#!/usr/bin/env python3
"""Firmament Ages - Creeping Darkness: checks and normalises the night table (S1f, Felix 2026-10-18).

Reads kubejs/data/firmages/balance/nights.json (one row per Age, see its _doc) and writes it back in the one canonical
layout (one row per line plus its 'why'). Rules (N-1 to N-8), the same as NightsTableTest and /firmages selftest nights:
  N-1  one row per Age, dawn .. age_9, in order, each with a 'why' of its own
  N-2  ranges: gear_chance, blood_moon_keep, cave_dweller_hunt in [0, 1]; health, damage in [1, 2.5]; night_spawn in [1, 2]
  N-3  never easier: gear_chance, health, damage, night_spawn, blood_moon_keep and the Apotheosis tier never fall with
       the Age; once super_blood_moon is true it stays true
  N-4  anchors: Dawn 1.0 / 1.0 / 1.0 without gear, the Singularity Age 2.5 / 2.5 / 2.0
  N-5  no blood moon before mob_2 (age_2, kubejs/server_scripts/firmages/blood_moon.js)
  N-6  cave_dweller_hunt is 0 from [hardship] caveDwellerUntilAge (config/firmages-server.toml) on
  N-7  gear one Age behind: the gear's Age (dev/age_map.toml [tfc_metals], vanilla armour from [derived]) is below the
       row's Age; gear_chance 0 exactly when there is no gear
  N-8  apoth_tier is an Apotheosis 8.9.0 WorldTier (haven, frontier, ascent, summit, pinnacle)

Usage: python dev/gen_nights.py [--check]   (--check: write nothing, exit 1 on a broken rule or a layout change)
"""
import argparse
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "kubejs" / "data" / "firmages" / "balance" / "nights.json"
AGE_MAP = ROOT / "dev" / "age_map.toml"
SERVER_TOML = ROOT / "config" / "firmages-server.toml"
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]
TIERS = ["haven", "frontier", "ascent", "summit", "pinnacle"]
KEYS = ["age", "gear", "gear_chance", "health", "damage", "night_spawn", "blood_moon_keep", "super_blood_moon",
        "cave_dweller_hunt", "apoth_tier", "why"]
MONOTONE = ["gear_chance", "health", "damage", "night_spawn", "blood_moon_keep"]
VANILLA_GEAR = {"diamond", "netherite"}


def gear_age(gear, age_map):
    if gear in VANILLA_GEAR:
        item = f"minecraft:{gear}_helmet"
        for section in ("derived", "items"):
            if item in age_map.get(section, {}):
                return age_map[section][item]
        return age_map.get("materials", {}).get(gear)
    return age_map.get("tfc_metals", {}).get(gear)


def cave_dweller_until():
    m = re.search(r'^\s*caveDwellerUntilAge\s*=\s*"([^"]+)"', SERVER_TOML.read_text(encoding="utf-8"), re.M)
    return m.group(1).replace("progressivestages:", "") if m else "age_2"


def check(table, age_map):
    errors = []
    rows = table.get("ages", [])
    if [r.get("age") for r in rows] != AGES:
        return [f"N-1 rows {[r.get('age') for r in rows]} != {AGES}"]
    whys = set()
    for r in rows:
        a = r["age"]
        missing = [k for k in KEYS if k not in r]
        if missing:
            errors.append(f"N-1 {a}: missing {missing}")
            continue
        if not r["why"].strip() or r["why"] in whys:
            errors.append(f"N-1 {a}: 'why' empty or copied")
        whys.add(r["why"])
        for k in ("gear_chance", "blood_moon_keep", "cave_dweller_hunt"):
            if not 0 <= r[k] <= 1:
                errors.append(f"N-2 {a}: {k} {r[k]} outside [0, 1]")
        for k, hi in (("health", 2.5), ("damage", 2.5), ("night_spawn", 2.0)):
            if not 1 <= r[k] <= hi:
                errors.append(f"N-2 {a}: {k} {r[k]} outside [1, {hi}]")
        if r["apoth_tier"] not in TIERS:
            errors.append(f"N-8 {a}: apoth_tier {r['apoth_tier']!r} not in {TIERS}")
        if (r["gear"] == "") != (r["gear_chance"] == 0):
            errors.append(f"N-7 {a}: gear {r['gear']!r} with gear_chance {r['gear_chance']}")
        if r["gear"]:
            g = gear_age(r["gear"], age_map)
            if g is None:
                errors.append(f"N-7 {a}: gear {r['gear']!r} has no Age in dev/age_map.toml")
            elif AGES.index(g) >= AGES.index(a):
                errors.append(f"N-7 {a}: gear {r['gear']} is of {g}, not one Age behind")
    for prev, cur in zip(rows, rows[1:]):
        for k in MONOTONE:
            if cur[k] < prev[k]:
                errors.append(f"N-3 {cur['age']}: {k} {cur[k]} below {prev['age']} {prev[k]}")
        if prev["apoth_tier"] in TIERS and cur["apoth_tier"] in TIERS and TIERS.index(cur["apoth_tier"]) < TIERS.index(prev["apoth_tier"]):
            errors.append(f"N-3 {cur['age']}: apoth_tier falls")
        if prev["super_blood_moon"] and not cur["super_blood_moon"]:
            errors.append(f"N-3 {cur['age']}: super_blood_moon switches off again")
    d, s = rows[0], rows[-1]
    if (d["health"], d["damage"], d["night_spawn"], d["gear"]) != (1.0, 1.0, 1.0, ""):
        errors.append("N-4 dawn is not 1.0 / 1.0 / 1.0 without gear")
    if (s["health"], s["damage"], s["night_spawn"]) != (2.5, 2.5, 2.0):
        errors.append("N-4 age_9 is not 2.5 / 2.5 / 2.0")
    for r in rows[:AGES.index("age_2")]:
        if r["blood_moon_keep"] != 0 or r["super_blood_moon"]:
            errors.append(f"N-5 {r['age']}: blood moon before mob_2")
    until = cave_dweller_until()
    if until in AGES:
        for r in rows[AGES.index(until):]:
            if r["cave_dweller_hunt"] != 0:
                errors.append(f"N-6 {r['age']}: cave_dweller_hunt {r['cave_dweller_hunt']} after caveDwellerUntilAge {until}")
    return errors


def render(table):
    out = ["{", '  "_doc": [']
    out.append(",\n".join("    " + json.dumps(line, ensure_ascii=False) for line in table["_doc"]))
    out.append("  ],")
    out.append('  "ages": [')
    rows = []
    for r in table["ages"]:
        head = ", ".join(f'"{k}": {json.dumps(r[k])}' for k in KEYS[:-1])
        rows.append("    {" + head + ",\n     \"why\": " + json.dumps(r["why"], ensure_ascii=False) + "}")
    out.append(",\n".join(rows))
    out.append("  ]")
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    table = json.loads(TABLE.read_text(encoding="utf-8"))
    age_map = tomllib.loads(AGE_MAP.read_text(encoding="utf-8"))
    errors = check(table, age_map)
    for e in errors:
        print("FAIL", e)
    text = render(table) if not errors else None
    current = TABLE.read_bytes().decode("utf-8")
    changed = text is not None and text != current
    if args.check:
        if changed:
            print("FAIL layout: run python dev/gen_nights.py")
        if not errors and not changed:
            print(f"PASS nights: {len(table['ages'])} rows, N-1..N-8")
        return 1 if errors or changed else 0
    if errors:
        return 1
    if changed:
        TABLE.write_bytes(text.encode("utf-8"))
        print(f"wrote {TABLE.relative_to(ROOT)}")
    print(f"PASS nights: {len(table['ages'])} rows, N-1..N-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
