#!/usr/bin/env python3
"""Firmament Ages - empty loot tables for compat blocks of mods that are not installed.

Several mods ship block loot tables for blocks they register only when another mod is present (Design n' Decor for
Dye Depot, Steam 'n' Rails for BYG, Nature's Spirit, Biomes O' Plenty and others, Create Connected, Create Casing,
More Immersive Wires, ExtendedAE for ProjectE). Without that mod the loot table names an unknown item, and every data
load (boot, warm-up reload, each Age grant) logs one LootDataType ERROR per table, 480 in all
(fb3 server log triage, finding I).

This script reads those errors from a server log and writes an override with no pools for each table:
  kubejs/data/<ns>/loot_table/<path>.json = {"type": "minecraft:block", "pools": []}
A table is only silenced when its path is blocks/<x>, the missing key is an item or block, and (with a registry
dump, default test-server/local/firmages/registries.json from fa_dump) neither the block <ns>:<x> nor the missing
key is registered. Every override is listed in dev/data/loot_silence.json. New entries are merged into the
manifest, so running it on a later log (where the silenced tables no longer error) keeps the existing ones.

If one of those optional mods is added to the pack later, its blocks would drop nothing while the override is in
place: remove them with --remove <namespace> (all overrides of that namespace) or --remove <ns>:<path prefix>
(e.g. railways:blocks/track_biomesoplenty_ when Biomes O' Plenty is added; the "missing" column names the blocks).

Usage:
  python dev/gen_loot_silence.py test-server/logs/latest.log [--registries FILE]
  python dev/gen_loot_silence.py --check               every manifest entry has its override file, and no stray file
  python dev/gen_loot_silence.py --remove railways:blocks/track_byg_   delete matching overrides and manifest rows
Exit code: 0 = ok, 1 = --check found a difference or nothing usable, 2 = bad input.
"""
import argparse
import gzip
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(REPO, "dev", "data", "loot_silence.json")
DATA = os.path.join(REPO, "kubejs", "data")
DEFAULT_REGISTRIES = os.path.join(REPO, "test-server", "local", "firmages", "registries.json")
EMPTY = {"type": "minecraft:block", "pools": []}
ERROR = re.compile(r"\.LootDataType/\]: Couldn't parse element ResourceKey\[minecraft:root / minecraft:loot_table\]:"
                   r"(?P<table>[a-z0-9_.-]+:[a-z0-9_./-]+) - Unknown registry key in "
                   r"ResourceKey\[minecraft:root / minecraft:(?P<reg>item|block)\]: (?P<key>[a-z0-9_.-]+:[a-z0-9_./-]+)")
DOC = ("Empty loot tables written by dev/gen_loot_silence.py for blocks that exist only when an absent mod is "
       "installed. Remove a namespace's overrides with: python dev/gen_loot_silence.py --remove <namespace>.")


def override_path(table):
    ns, path = table.split(":", 1)
    return os.path.join(DATA, ns, "loot_table", *path.split("/")) + ".json"


def rel(p):
    return os.path.relpath(p, REPO).replace(os.sep, "/")


def read_manifest():
    if not os.path.isfile(MANIFEST):
        return {}
    with open(MANIFEST, encoding="utf-8") as f:
        return {e["table"]: e for e in json.load(f)["tables"]}


def write_manifest(entries, source):
    rows = [entries[k] for k in sorted(entries)]
    counts = {}
    for e in rows:
        ns = e["table"].split(":", 1)[0]
        counts[ns] = counts.get(ns, 0) + 1
    data = {"_doc": DOC, "source_logs": source, "count": len(rows), "by_namespace": dict(sorted(counts.items())),
            "tables": rows}
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f)
        f.write("\n")


def is_ours(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f) == EMPTY
    except (OSError, ValueError):
        return False


def read_log(path):
    opener = gzip.open if path.endswith(".gz") else open
    found = {}
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            m = ERROR.search(line)
            if m:
                found[m.group("table")] = (m.group("reg"), m.group("key"))
    return found


def generate(a):
    if not os.path.isfile(a.log):
        print("gen_loot_silence: no such log: %s" % a.log)
        return 2
    registered = set()
    reg_file = a.registries or (DEFAULT_REGISTRIES if os.path.isfile(DEFAULT_REGISTRIES) else None)
    if reg_file:
        with open(reg_file, encoding="utf-8") as f:
            reg = json.load(f)
        registered = set(reg.get("item", [])) | set(reg.get("block", []))
    found = read_log(a.log)
    entries = read_manifest()
    added, skipped = 0, []
    for table, (kind, key) in sorted(found.items()):
        ns, path = table.split(":", 1)
        block = "%s:%s" % (ns, path[len("blocks/"):]) if path.startswith("blocks/") else None
        target = override_path(table)
        if block is None:
            skipped.append((table, "not a block loot table"))
        elif block in registered or key in registered:
            skipped.append((table, "%s is registered, the error has another cause" % (block if block in registered else key)))
        elif os.path.exists(target) and table not in entries and not is_ours(target):
            skipped.append((table, "%s already exists and is not an empty override" % rel(target)))
        else:
            if table not in entries:
                added += 1
            entries[table] = {"table": table, "missing": "%s %s" % (kind, key), "file": rel(target)}
            write_json(target, EMPTY)
    sources = []
    if os.path.isfile(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as f:
            sources = json.load(f).get("source_logs", [])
    name = os.path.basename(a.log)
    if found and name not in sources:
        sources.append(name)
    write_manifest(entries, sources)
    print("gen_loot_silence: %d errors in %s, %d new overrides, %d in the manifest%s"
          % (len(found), a.log, added, len(entries), "" if reg_file else " (no registry dump: not cross-checked)"))
    for table, why in skipped:
        print("  skipped %s: %s" % (table, why))
    return 0 if entries else 1


def check():
    entries = read_manifest()
    bad = []
    for table, e in entries.items():
        p = override_path(table)
        if rel(p) != e["file"]:
            bad.append("%s: manifest file %s, expected %s" % (table, e["file"], rel(p)))
        elif not is_ours(p):
            bad.append("%s: %s missing or not an empty override" % (table, e["file"]))
    known = {e["file"] for e in entries.values()}
    namespaces = {t.split(":", 1)[0] for t in entries}
    for ns in sorted(namespaces):
        root = os.path.join(DATA, ns, "loot_table")
        for dirpath, _, files in os.walk(root):
            for fn in files:
                p = rel(os.path.join(dirpath, fn))
                if p not in known and is_ours(os.path.join(dirpath, fn)):
                    bad.append("%s: empty override not in the manifest" % p)
    print("gen_loot_silence --check: %d overrides, %d problems" % (len(entries), len(bad)))
    for b in bad:
        print("  " + b)
    return 1 if bad else 0


def remove(what):
    entries = read_manifest()
    hit = [t for t in entries if (t.startswith(what) if ":" in what else t.split(":", 1)[0] == what)]
    for t in hit:
        p = override_path(t)
        if os.path.exists(p):
            os.remove(p)
        d = os.path.dirname(p)
        while d.startswith(DATA) and d != DATA and not os.listdir(d):
            os.rmdir(d)
            d = os.path.dirname(d)
        del entries[t]
    sources = []
    if os.path.isfile(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as f:
            sources = json.load(f).get("source_logs", [])
    write_manifest(entries, sources)
    print("gen_loot_silence --remove %s: %d overrides removed, %d left" % (what, len(hit), len(entries)))
    return 0 if hit else 1


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("log", nargs="?", help="server log with the LootDataType errors (.log or .log.gz)")
    ap.add_argument("--registries", help="fa_dump registries.json (default test-server/local/firmages/registries.json)")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--remove", metavar="NS_OR_TABLE")
    a = ap.parse_args()
    if a.check:
        return check()
    if a.remove:
        return remove(a.remove)
    if not a.log:
        ap.error("a log path, --check or --remove is required")
    return generate(a)


if __name__ == "__main__":
    sys.exit(main())
