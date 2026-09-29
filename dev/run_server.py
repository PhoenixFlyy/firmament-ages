#!/usr/bin/env python3
"""Boot the local Firmament Ages test server once and summarise the result.

Starts NeoForge in <repo>/test-server with the portable Temurin 21 from tools/jdk-21,
streams the console to test-server/logs/run_server-console.log, waits for "Done (",
optionally sends console commands (with a wait after each), then sends "stop".
A hard timeout kills the JVM. The summary lists boot time, loaded mod count, ERROR lines,
KubeJS errors (logs/kubejs/*.log) and ProgressiveStages errors.

Usage:
  python dev/run_server.py
  python dev/run_server.py --cmd "progressivestages validate" --cmd "kubejs errors server" --wait 5
  python dev/run_server.py --wipe-world --timeout 900
Exit code: 0 = reached Done and stopped cleanly, 1 = crash/exit before Done, 2 = timeout.
"""
import argparse
import glob
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SERVER = os.path.join(REPO, "test-server")
JAVA = os.path.join(REPO, "tools", "jdk-21", "bin", "java.exe")
NEOFORGE = "21.1.252"

CRASH_MARKERS = (
    "---- Minecraft Crash Report ----",
    "Exception in server tick loop",
    "Failed to start the minecraft server",
    "Encountered an unexpected exception",
    "Crash report saved to",
    "Mod loading has failed",
    "Missing or unsupported mandatory dependencies",
)
LEVEL_RE = re.compile(r"^\[[^\]]*\] \[[^\]]*/(ERROR|WARN|FATAL)\]")
DONE_RE = re.compile(r"Done \((\d+(?:\.\d+)?)s\)!")
MODCOUNT_RE = re.compile(r"Loading (\d+) mods")


def reader(proc, q, logf):
    for raw in iter(proc.stdout.readline, b""):
        line = raw.decode("utf-8", "replace").rstrip("\r\n")
        logf.write(line + "\n")
        logf.flush()
        q.put(line)
    q.put(None)


def send(proc, cmd):
    try:
        proc.stdin.write((cmd + "\n").encode("utf-8"))
        proc.stdin.flush()
        return True
    except OSError:
        return False


def tail_errors(path, pattern, limit):
    out = []
    if not os.path.isfile(path):
        return out
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if pattern.search(line):
                out.append(line.rstrip())
    return out[:limit]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server-dir", default=DEFAULT_SERVER)
    ap.add_argument("--xms", default="6G")
    ap.add_argument("--xmx", default="8G")
    ap.add_argument("--timeout", type=int, default=560, help="hard timeout in seconds for the whole run")
    ap.add_argument("--cmd", action="append", default=[], help="console command to send after Done (repeatable)")
    ap.add_argument("--wait", type=float, default=5.0, help="seconds to wait after each command")
    ap.add_argument("--settle", type=float, default=5.0, help="seconds to wait after Done before the first command")
    ap.add_argument("--wipe-world", action="store_true", help="delete <server>/world before the boot")
    ap.add_argument("--max-lines", type=int, default=25, help="max lines per summary section")
    a = ap.parse_args()

    sd = os.path.abspath(a.server_dir)
    args_file = os.path.join("libraries", "net", "neoforged", "neoforge", NEOFORGE, "win_args.txt")
    if not os.path.isfile(os.path.join(sd, args_file)):
        sys.exit(f"NeoForge args file missing: {os.path.join(sd, args_file)} (run the installer first)")
    if not os.path.isfile(JAVA):
        sys.exit(f"Java 21 missing: {JAVA}")
    if a.wipe_world:
        shutil.rmtree(os.path.join(sd, "world"), ignore_errors=True)

    logs = os.path.join(sd, "logs")
    os.makedirs(logs, exist_ok=True)
    crash_before = set(glob.glob(os.path.join(sd, "crash-reports", "*.txt")))
    console_path = os.path.join(logs, "run_server-console.log")

    cmd = [JAVA, f"-Xms{a.xms}", f"-Xmx{a.xmx}", "-XX:+UseG1GC", f"@{args_file}", "nogui"]
    t0 = time.time()
    logf = open(console_path, "w", encoding="utf-8")
    proc = subprocess.Popen(cmd, cwd=sd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT,
                            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0))
    q = queue.Queue()
    threading.Thread(target=reader, args=(proc, q, logf), daemon=True).start()

    state = {"done": None, "crash": [], "levels": [], "mods": None, "eof": False}
    cmd_output = []  # (command, [lines])
    current = None

    def pump(until):
        """Consume console lines until time `until` or EOF. Returns False on EOF."""
        nonlocal current
        while True:
            left = until - time.time()
            if left <= 0:
                return True
            try:
                line = q.get(timeout=min(left, 0.5))
            except queue.Empty:
                continue
            if line is None:
                state["eof"] = True
                return False
            if current is not None:
                current[1].append(line)
            m = DONE_RE.search(line)
            if m and state["done"] is None:
                state["done"] = (time.time() - t0, m.group(1))
            m = MODCOUNT_RE.search(line)
            if m and state["mods"] is None:
                state["mods"] = m.group(1)
            if any(k in line for k in CRASH_MARKERS):
                state["crash"].append(line)
            if LEVEL_RE.match(line):
                state["levels"].append(line)
            if state["done"] and until == float("inf"):
                return True

    deadline = t0 + a.timeout
    result = "timeout"
    # phase 1: boot
    while state["done"] is None and not state["eof"] and time.time() < deadline:
        if not pump(min(deadline, time.time() + 1)):
            break
        if proc.poll() is not None and q.empty():
            break
    if state["done"]:
        pump(min(deadline, time.time() + a.settle))
        for c in a.cmd:
            if state["eof"]:
                break
            current = (c, [])
            cmd_output.append(current)
            send(proc, c)
            pump(min(deadline, time.time() + a.wait))
            current = None
        send(proc, "stop")
        stop_deadline = min(deadline + 60, time.time() + 120)
        while not state["eof"] and time.time() < stop_deadline:
            pump(min(stop_deadline, time.time() + 1))
        result = "ok" if not state["crash"] else "crash"
    elif state["eof"] or proc.poll() is not None:
        result = "crash"
    if result in ("timeout", "crash") and proc.poll() is None:
        proc.kill()
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
        if result == "ok":
            result = "stop-timeout"
    logf.close()

    # ---- summary
    total = time.time() - t0
    n = a.max_lines
    print(f"=== run_server summary: {result} (exit code {proc.returncode}, {total:.0f}s total) ===")
    if state["done"]:
        print(f"Done after {state['done'][0]:.1f}s wall clock (server reported {state['done'][1]}s)")
    mods = state["mods"]
    if mods is None:
        dbg = os.path.join(logs, "debug.log")
        for line in tail_errors(dbg, MODCOUNT_RE, 1):
            mods = MODCOUNT_RE.search(line).group(1)
    print(f"Mods loaded: {mods or '?'}")
    errs = [l for l in state["levels"] if "/ERROR]" in l or "/FATAL]" in l]
    warns = [l for l in state["levels"] if "/WARN]" in l]
    print(f"Console: {len(errs)} ERROR/FATAL, {len(warns)} WARN lines (full log: {console_path})")
    for l in errs[:n]:
        print("  E " + l[:400])
    if state["crash"]:
        print("Crash markers:")
        for l in state["crash"][:n]:
            print("  ! " + l[:400])
    new_crash = sorted(set(glob.glob(os.path.join(sd, "crash-reports", "*.txt"))) - crash_before)
    for c in new_crash:
        print(f"New crash report: {c}")
    kjs_re = re.compile(r"\bERROR\b|Error in|Error:|\] \[ERR", re.I)
    for name in ("startup.log", "server.log"):
        p = os.path.join(logs, "kubejs", name)
        e = tail_errors(p, kjs_re, n)
        print(f"KubeJS {name}: {len(e)} error lines" + ("" if os.path.isfile(p) else " (file missing)"))
        for l in e:
            print("  K " + l[:400])
    ps_re = re.compile(r"progressivestages|ProgressiveStages|PStages", re.I)
    ps = [l for l in state["levels"] if ps_re.search(l)]
    print(f"ProgressiveStages WARN/ERROR lines: {len(ps)}")
    for l in ps[:n]:
        print("  P " + l[:400])
    for c, lines in cmd_output:
        print(f"--- > {c}")
        for l in lines[:60]:
            print("    " + l[:300])
    sys.exit({"ok": 0, "crash": 1, "timeout": 2}.get(result, 1))


if __name__ == "__main__":
    main()
