#!/usr/bin/env python3
"""Firmament Ages - triage of a server or client log against the documented noise list.

Reads a NeoForge latest.log or debug.log (plain or .gz) and keeps the problem lines: every WARN, ERROR and FATAL
line, the head line of every STDERR trace, and every INFO line that announces a skipped, missing, failed or
fallback thing (INFO_PROBLEM). Each one is checked against dev/data/log_noise.json:

  noise  lines the pack cannot fix (a mod's own log, a mod bug without a config or data fix), each with a one-line
         reason: dropped
  open   known problems whose fix lives outside the pack data (e.g. a firmages-core change): kept, folded into one
         group per entry, and still counted as remaining
  fixed  problems fixed in the pack data after the build that wrote the log (the reason says where): folded into
         one group per entry and still counted, so an older log reads the same and a newer log that still shows
         them points at a fix that did not work

Every other problem line is grouped by signature (level, logger and the message with resource ids, numbers and
hex collapsed) and printed with its count and up to --examples distinct messages. Noise entries that matched
nothing are listed at the end (stale, or the cause did not occur in this log). The boot time (first line to
vanilla's "Done (...)") is printed too.

--client reads a client log (CurseForge instance logs/latest.log or debug.log) against dev/data/client_log_noise.json.
The client latest.log starts in the short format "[13:36:21] [main/WARN]: message" (no logger) before NeoForge's
logging takes over; STDERR arrives as INFO lines of net.minecraft.server.LoggedPrintStream with a "[STDERR]: " prefix.
Instead of the boot time it prints the start phases: launcher to game start, mod construction, registries and
configs, the first resource reload up to the title screen (FancyMenu's title_screen layer), and every join from
"Connecting to" to the first "Loaded N advancements", with JEI's start and EMI's reload of that join.

Each regex is searched in the line without its timestamp, e.g.
  [Server thread/WARN] [net.minecraft.core.MappedRegistry/]: Not all defined tags for registry ...

Usage:  python dev/check_log.py test-server/logs/latest.log [--examples 3] [--noise dev/data/log_noise.json]
        python dev/check_log.py --client "<CurseForge instance>/logs/latest.log"
Exit code: 0 = nothing remains, 1 = problem lines remain (open entries included), 2 = bad input.
"""
import argparse
import gzip
import json
import os
import re
import sys
from collections import OrderedDict
from datetime import datetime

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_NOISE = os.path.join(REPO, "dev", "data", "log_noise.json")
CLIENT_NOISE = os.path.join(REPO, "dev", "data", "client_log_noise.json")

# The logger part is missing in the client's early lines ("[13:36:21] [main/WARN]: ...").
LINE = re.compile(r"^\[(?P<ts>[^\]]*)\] (\[(?P<thread>[^\]]*)/(?P<level>[A-Z]+)\](?: \[(?P<logger>[^\]]*)\])?: (?P<msg>.*))$")
STDERR_PRINT = "[STDERR]: "  # client: System.err as INFO lines of net.minecraft.server.LoggedPrintStream
STDERR_CONT = re.compile(r"^(\[[^\]]*\]: )?(\s+at |\s*\.\.\. \d+ more|\s*Caused by: |\s*Suppressed: )")
STDERR_PREFIX = re.compile(r"^\[[^\]]*\]: ")
LEVELS = {"WARN", "ERROR", "FATAL"}
# INFO lines that report a problem in words. Count summaries ("with 0 errors", "0 failed recipes", "(skipped N)")
# do not match: none of them uses these words in this form.
INFO_PROBLEM = re.compile(r"\b(Skipping recipe|Failed|Falling back|[Nn]ot found|Unable|[Mm]issing|Could ?n[o']t|"
                          r"Cannot|Can't|Ignoring|Invalid|Unknown)\b")
DONE = re.compile(r"\[net\.minecraft\.server\.dedicated\.DedicatedServer/\]: Done \(")
NORMALISE = [
    (re.compile(r"\b[a-z0-9_.-]+:[a-z0-9_./-]+"), "<id>"),
    (re.compile(r"\b0x[0-9a-fA-F]+\b|\b[0-9a-f]{8,}\b"), "<hex>"),
    (re.compile(r"\d+"), "N"),
]


def read_lines(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        for raw in f:
            yield raw.rstrip("\r\n")


def stamp(ts):
    """Seconds of a log timestamp like 14Okt.2026 13:19:16.787 or 13:36:21 (the date part is ignored)."""
    m = re.search(r"(\d+):(\d+):(\d+)(?:\.(\d+))?$", ts)
    if not m:
        return None
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)) + int(m.group(4) or 0) / 1000


# Client start phases: (key, regex searched in the line without timestamp). The first match of each key counts,
# except the per-join keys (connect, joined, jei, emi), which are collected per join.
CLIENT_MARKS = [
    ("target", re.compile(r"\[main/INFO\]: Launching target ")),
    ("construct", re.compile(r"NeoForge mod loading, version ")),
    ("reload", re.compile(r"\[net\.minecraft\.server\.packs\.resources\.ReloadableResourceManager/\]: Reloading ResourceManager: ")),
    ("title", re.compile(r"ScreenCustomizationLayer registered: title_screen$")),
    ("connect", re.compile(r"\[net\.minecraft\.client\.gui\.screens\.ConnectScreen/\]: Connecting to ")),
    ("joined", re.compile(r"\[net\.minecraft\.advancements\.AdvancementTree/\]: Loaded \d+ advancements")),
    ("jei", re.compile(r"Starting JEI took (?P<s>[\d.]+) seconds")),
    ("emi", re.compile(r"\[EMI\] Reloaded EMI in (?P<ms>\d+)ms")),
]


def client_phases(path):
    first, marks, joins = None, {}, []
    for raw in read_lines(path):
        m = LINE.match(raw)
        if not m:
            continue
        t = stamp(m.group("ts"))
        if first is None:
            first = t
        line = m.group(2)
        for key, rx in CLIENT_MARKS:
            mm = rx.search(line)
            if not mm:
                continue
            if key == "connect":
                joins.append({"connect": t})
            elif key in ("joined", "jei", "emi"):
                if joins and key not in joins[-1]:
                    if key == "joined":
                        joins[-1][key] = t
                    elif key == "jei":
                        joins[-1][key] = float(mm.group("s"))
                    else:
                        joins[-1][key] = int(mm.group("ms")) / 1000
            else:
                marks.setdefault(key, t)

    def span(a, b):
        return "%.1f s" % ((b - a) % 86400)

    out = []
    steps = [("launcher, mod discovery, mixin setup", first, marks.get("target")),
             ("game bootstrap up to NeoForge mod loading", marks.get("target"), marks.get("construct")),
             ("mod construction, registries, configs", marks.get("construct"), marks.get("reload")),
             ("first resource reload up to the title screen", marks.get("reload"), marks.get("title")),
             ("total up to the title screen", first, marks.get("title"))]
    for name, a, b in steps:
        if a is not None and b is not None:
            out.append("  %-46s %s" % (name, span(a, b)))
    for i, j in enumerate(joins, 1):
        parts = []
        if "joined" in j:
            parts.append("in the world after %s" % span(j["connect"], j["joined"]))
        if "jei" in j:
            parts.append("JEI start %.1f s (render thread)" % j["jei"])
        if "emi" in j:
            parts.append("EMI reload %.1f s (background)" % j["emi"])
        out.append("  join %d: %s" % (i, ", ".join(parts) or "no marker after 'Connecting to'"))
    return out


def problem_lines(path, boot):
    for raw in read_lines(path):
        m = LINE.match(raw)
        if not m:
            continue  # stack frames and other continuation lines
        if boot.get("first") is None:
            boot["first"] = stamp(m.group("ts"))
        level, logger, msg = m.group("level"), m.group("logger") or "", m.group("msg")
        if boot.get("done") is None and DONE.search(raw):
            boot["done"] = stamp(m.group("ts"))
        if logger.startswith("net.minecraft.server.LoggedPrintStream") and msg.startswith(STDERR_PRINT):
            if STDERR_CONT.match(msg[len(STDERR_PRINT):]):
                continue  # frame of a trace whose head line is already counted
            yield "STDERR", m.group(2), logger, msg[len(STDERR_PRINT):]
        elif logger.startswith("STDERR"):
            if STDERR_CONT.match(msg):
                continue  # frame of a trace whose head line is already counted
            yield "STDERR", m.group(2), logger, STDERR_PREFIX.sub("", msg)
        elif level in LEVELS or (level == "INFO" and INFO_PROBLEM.search(msg)):
            yield level, m.group(2), logger, msg


def signature(level, logger, msg):
    s = msg
    for rx, rep in NORMALISE:
        s = rx.sub(rep, s)
    return "%s [%s] %s" % (level, logger, s[:180])


def load_noise(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    entries = []
    for kind in ("open", "fixed", "noise"):  # open and fixed first: a line of a known problem is attributed to it
        for e in data.get(kind, []):
            for key in ("id", "regex", "reason"):
                if not e.get(key):
                    raise ValueError("%s entry without %s: %r" % (kind, key, e))
            entries.append((kind, e["id"], re.compile(e["regex"]), e))
    return entries


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("log", help="latest.log, debug.log or a rotated .log.gz")
    ap.add_argument("--client", action="store_true", help="client log: client noise list and start phases")
    ap.add_argument("--noise", default=None, help="noise list (default dev/data/log_noise.json, "
                                                  "with --client dev/data/client_log_noise.json)")
    ap.add_argument("--examples", type=int, default=2, help="distinct example messages per group (default 2)")
    a = ap.parse_args()
    if a.noise is None:
        a.noise = CLIENT_NOISE if a.client else DEFAULT_NOISE
    if not os.path.isfile(a.log):
        print("check_log: no such file: %s" % a.log)
        return 2
    try:
        entries = load_noise(a.noise)
    except (OSError, ValueError, re.error) as e:
        print("check_log: cannot read %s: %s" % (a.noise, e))
        return 2

    hits = OrderedDict((eid, 0) for _, eid, _, _ in entries)
    total = OrderedDict((k, 0) for k in ("FATAL", "ERROR", "STDERR", "WARN", "INFO"))
    open_groups = OrderedDict()
    fixed_groups = OrderedDict()
    groups = {}
    boot = {}
    for level, line, logger, msg in problem_lines(a.log, boot):
        total[level] = total.get(level, 0) + 1
        matched = None
        for kind, eid, rx, e in entries:
            if rx.search(line):
                matched = (kind, eid, e)
                break
        if matched:
            hits[matched[1]] += 1
            if matched[0] in ("open", "fixed"):
                target = open_groups if matched[0] == "open" else fixed_groups
                g = target.setdefault(matched[1], {"count": 0, "entry": matched[2]})
                g["count"] += 1
            continue
        sig = signature(level, logger, msg)
        g = groups.setdefault(sig, {"count": 0, "examples": []})
        g["count"] += 1
        if len(g["examples"]) < a.examples and msg not in g["examples"]:
            g["examples"].append(msg)

    kinds = {eid: kind for kind, eid, _, _ in entries}
    dropped = sum(n for eid, n in hits.items() if kinds[eid] == "noise")
    open_n = sum(g["count"] for g in open_groups.values())
    fixed_n = sum(g["count"] for g in fixed_groups.values())
    rest = sum(g["count"] for g in groups.values())
    counted = ", ".join("%s %d" % (k, v) for k, v in total.items())
    print("check_log: %s" % a.log)
    if a.client:
        phases = client_phases(a.log)
        if phases:
            print("client start phases:")
            print("\n".join(phases))
    elif boot.get("first") is not None and boot.get("done") is not None:
        print("boot: %.1f s from the first log line to 'Done'" % ((boot["done"] - boot["first"]) % 86400))
    print("problem lines: %s (INFO: only lines that report a problem); documented noise %d; open %d; "
          "fixed since %d; remaining %d in %d signatures" % (counted, dropped, open_n, fixed_n, rest, len(groups)))
    if open_groups:
        print("\n== open (known, fix pending outside the pack data) ==")
        for eid, g in open_groups.items():
            print("%7d  %s: %s" % (g["count"], eid, g["entry"]["reason"]))
    if fixed_groups:
        print("\n== fixed in the pack after this log's build (must be gone in a log of a newer build) ==")
        for eid, g in fixed_groups.items():
            print("%7d  %s: %s" % (g["count"], eid, g["entry"]["reason"]))
    if groups:
        print("\n== remaining ==")
        for sig, g in sorted(groups.items(), key=lambda kv: (-kv[1]["count"], kv[0])):
            print("%7d  %s" % (g["count"], sig))
            for ex in g["examples"]:
                print("           e.g. %s" % ex[:300])
    stale = [eid for kind, eid, _, e in entries if hits[eid] == 0]
    if stale:
        print("\nentries without a match in this log: %s" % ", ".join(stale))
    return 1 if (rest or open_n or fixed_n) else 0


if __name__ == "__main__":
    sys.exit(main())
