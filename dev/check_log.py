#!/usr/bin/env python3
"""Firmament Ages - triage of a server log against the documented noise list.

Reads a NeoForge latest.log or debug.log (plain or .gz), keeps the problem lines (level ERROR or FATAL, and the
head line of every STDERR trace; with --warn also WARN), and checks each one against dev/data/log_noise.json:

  noise  harmless signatures with a reason each (fb3 server log triage, section 3): dropped; "warn": true marks an
         entry for WARN lines, which only --warn checks
  open   known problems whose fix lives elsewhere (e.g. a firmages-core mixin): kept, but folded into one group per
         entry so the rest stays readable; they still count as remaining

Every other problem line is grouped by signature (level, logger and the message with resource ids, numbers and
hex collapsed) and printed with its count and up to --examples distinct messages. Noise entries that matched
nothing are listed at the end (stale, or the cause is gone).

Each regex is searched in the line without its timestamp, e.g.
  [Server thread/ERROR] [dev.ryanhcode.sable.Sable/]: Failed to apply tag physics properties. Unknown block: x:y

Usage:  python dev/check_log.py test-server/logs/latest.log [--warn] [--examples 3] [--noise dev/data/log_noise.json]
Exit code: 0 = nothing remains, 1 = problem lines remain (open entries included), 2 = bad input.
"""
import argparse
import gzip
import json
import os
import re
import sys
from collections import OrderedDict

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_NOISE = os.path.join(REPO, "dev", "data", "log_noise.json")

LINE = re.compile(r"^\[[^\]]*\] (\[(?P<thread>[^\]]*)/(?P<level>[A-Z]+)\] \[(?P<logger>[^\]]*)\]: (?P<msg>.*))$")
STDERR_CONT = re.compile(r"^(\[[^\]]*\]: )?(\s+at |\s*\.\.\. \d+ more|\s*Caused by: |\s*Suppressed: )")
STDERR_PREFIX = re.compile(r"^\[[^\]]*\]: ")
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


def problem_lines(path, warn):
    levels = {"ERROR", "FATAL"} | ({"WARN"} if warn else set())
    for raw in read_lines(path):
        m = LINE.match(raw)
        if not m:
            continue  # stack frames and other continuation lines
        level, logger, msg = m.group("level"), m.group("logger"), m.group("msg")
        if logger.startswith("STDERR"):
            if STDERR_CONT.match(msg):
                continue  # frame of a trace whose head line is already counted
            yield "STDERR", m.group(1), logger, STDERR_PREFIX.sub("", msg)
        elif level in levels:
            yield level, m.group(1), logger, msg


def signature(level, logger, msg):
    s = msg
    for rx, rep in NORMALISE:
        s = rx.sub(rep, s)
    return "%s [%s] %s" % (level, logger, s[:180])


def load_noise(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    entries = []
    for kind in ("open", "noise"):  # open first: a line of a known open problem is attributed to it
        for e in data.get(kind, []):
            for key in ("id", "regex", "reason"):
                if not e.get(key):
                    raise ValueError("%s entry without %s: %r" % (kind, key, e))
            entries.append((kind, e["id"], re.compile(e["regex"]), e))
    return entries


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("log", help="latest.log, debug.log or a rotated .log.gz")
    ap.add_argument("--noise", default=DEFAULT_NOISE)
    ap.add_argument("--warn", action="store_true", help="also check WARN lines")
    ap.add_argument("--examples", type=int, default=2, help="distinct example messages per group (default 2)")
    a = ap.parse_args()
    if not os.path.isfile(a.log):
        print("check_log: no such file: %s" % a.log)
        return 2
    try:
        entries = load_noise(a.noise)
    except (OSError, ValueError, re.error) as e:
        print("check_log: cannot read %s: %s" % (a.noise, e))
        return 2

    hits = OrderedDict((eid, 0) for _, eid, _, _ in entries)
    total = {"ERROR": 0, "FATAL": 0, "STDERR": 0, "WARN": 0}
    open_groups = OrderedDict()
    groups = {}
    for level, line, logger, msg in problem_lines(a.log, a.warn):
        total[level] += 1
        matched = None
        for kind, eid, rx, e in entries:
            if rx.search(line):
                matched = (kind, eid, e)
                break
        if matched:
            hits[matched[1]] += 1
            if matched[0] == "open":
                g = open_groups.setdefault(matched[1], {"count": 0, "entry": matched[2]})
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
    rest = sum(g["count"] for g in groups.values())
    counted = ", ".join("%s %d" % (k, v) for k, v in total.items() if k != "WARN" or a.warn)
    print("check_log: %s" % a.log)
    print("problem lines: %s; documented noise %d; open %d; remaining %d in %d signatures"
          % (counted, dropped, open_n, rest, len(groups)))
    if open_groups:
        print("\n== open (known, fix pending) ==")
        for eid, g in open_groups.items():
            print("%7d  %s: %s" % (g["count"], eid, g["entry"]["reason"]))
    if groups:
        print("\n== remaining ==")
        for sig, g in sorted(groups.items(), key=lambda kv: (-kv[1]["count"], kv[0])):
            print("%7d  %s" % (g["count"], sig))
            for ex in g["examples"]:
                print("           e.g. %s" % ex[:300])
    stale = [eid for kind, eid, _, e in entries if hits[eid] == 0 and (a.warn or not e.get("warn"))]
    if stale:
        print("\nentries without a match in this log: %s" % ", ".join(stale))
    return 1 if (rest or open_n) else 0


if __name__ == "__main__":
    sys.exit(main())
