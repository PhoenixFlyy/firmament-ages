#!/usr/bin/env python3
"""Firmament Ages - generator data from the fuel table (harmony plan 2026-10-15, package A6).

Reads kubejs/data/firmages/balance/fuels.json (one FE value per bucket per fluid) and writes
  kubejs/data/immersiveengineering/recipe/generator_fuel/<fuel>.json     IE Diesel Generator (burnTime = FE / 4,096)
  kubejs/data/immersivepetroleum/recipe/fuels/<fuel>.json                 the same for the Immersive Petroleum fuels
  kubejs/data/createdieselgenerators/createdieselgenerators/fuel_type/<file>.json
                                                                          Diesel Generators engines: burn_rate (mB/t)
                                                                          so that engine + C&A Alternator gives the
                                                                          table value (FE per SU from
                                                                          config/createaddition-common.toml)
  kubejs/data/mekanism/data_maps/mekanism/chemical/chemical_attribute_fuel.json
                                                                          Gas-Burning Generator: energy (J/t) per fuel
  config/createdieselgenerators-server.toml                               the turbocharger burn rate multiplier = its
                                                                          speed multiplier (same FE per bucket)

--check writes nothing and exits 1 when a file would change, when a generator route leaves its table value by more
than the table's tolerance, or when the ladder (creosote < diesel < ethene < deuterium_tritium) is broken. With
--dump-dir it also reads a /fa_dump_full recipes_full.json and fails on an IE generator fuel whose fluid tag is
neither in the table nor in its "dead" list.

Usage: python dev/gen_fuels.py [--check] [--dump-dir <fa_dump folder>]
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "kubejs" / "data" / "firmages" / "balance" / "fuels.json"
CA_CONFIG = ROOT / "config" / "createaddition-common.toml"
CDG_CONFIG = ROOT / "config" / "createdieselgenerators-server.toml"
IE_DIR = {"immersiveengineering": ROOT / "kubejs" / "data" / "immersiveengineering" / "recipe",
          "immersivepetroleum": ROOT / "kubejs" / "data" / "immersivepetroleum" / "recipe"}
CDG_DIR = ROOT / "kubejs" / "data" / "createdieselgenerators" / "createdieselgenerators" / "fuel_type"
MEK_MAP = ROOT / "kubejs" / "data" / "mekanism" / "data_maps" / "mekanism" / "chemical" / "chemical_attribute_fuel.json"
TURBO_SPEED = '"Turbocharged Diesel Engine Speed Multiplier"'
TURBO_BURN = '"Turbocharged Diesel Engine Burn Rate Multiplier"'


def toml_value(text, key):
    m = re.search(rf"^\s*{re.escape(key)}\s*=\s*([0-9.]+)\s*$", text, re.M)
    if not m:
        sys.exit(f"gen_fuels: {key} not found")
    return float(m.group(1))


def fe_per_su():
    t = CA_CONFIG.read_text(encoding="utf-8")
    return toml_value(t, "fe_at_max_rpm") * toml_value(t, "generator_efficiency") / toml_value(t, "max_stress")


def dumps(obj):
    return json.dumps(obj, indent=2) + "\n"


def build(table):
    """{path: text} of every generated file, and [(route, fuel, FE per bucket)] of every generator route."""
    out, routes = {}, []
    gens = table["generators"]
    ie_fe_t = gens["ie_diesel_generator"]["fe_per_tick"]
    jpf = gens["mek_gas_burning"]["joules_per_fe"]
    k = fe_per_su()
    mek = {}
    for name, f in table["fuels"].items():
        fe = f["fe_per_bucket"]
        if "ie" in f:
            ns, path = f["ie"]["recipe"].split(":", 1)
            burn = round(fe / ie_fe_t)
            out[IE_DIR[ns] / f"{path}.json"] = dumps(
                {"type": "immersiveengineering:generator_fuel", "fluidTag": f["ie"]["fluid_tag"], "burnTime": burn})
            routes.append(("IE Diesel Generator", name, burn * ie_fe_t))
        if "cdg" in f:
            c = f["cdg"]
            doc = {"fluid": c["fluid"]}
            for eng, p in c["engines"].items():
                fe_t = p["strength"] * k                      # engine stress capacity through alternators
                rate = round(fe_t * 1000 / fe, 3)             # mB per tick
                doc[eng] = {"speed": p["speed"], "strength": p["strength"], "burn_rate": rate}
                routes.append((f"CDG {eng} engine + alternator", name, fe_t * 1000 / rate))
            doc["sound_pitch"] = c["sound_pitch"]
            doc["burner_multiplier"] = c["burner_multiplier"]
            out[CDG_DIR / f"{c['file']}.json"] = dumps(doc)
        if "mekanism" in f:
            m = f["mekanism"]
            energy = round(fe / 1000 * jpf / m["burn_time"])  # J per tick while one mB burns for burn_time ticks
            mek[m["chemical"]] = {"burn_time": m["burn_time"], "energy": energy}
            routes.append(("Mekanism Gas-Burning Generator", name, energy * m["burn_time"] / jpf * 1000))
    out[MEK_MAP] = dumps({"values": mek})
    # turbocharger: burn rate multiplier = speed multiplier
    cfg = CDG_CONFIG.read_text(encoding="utf-8")
    speed = toml_value(cfg, TURBO_SPEED)
    cfg2 = re.sub(rf"^(\s*{re.escape(TURBO_BURN)}\s*=\s*)[0-9.]+(\s*)$", lambda mm: f"{mm.group(1)}{speed}{mm.group(2)}",
                  cfg, flags=re.M)
    out[CDG_CONFIG] = cfg2
    return out, routes


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="write nothing, exit 1 on a stale file or a broken rule")
    ap.add_argument("--dump-dir", help="a /fa_dump folder with recipes_full.json: every IE fuel must be in the table")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    table = json.loads(TABLE.read_text(encoding="utf-8"))
    files, routes = build(table)
    bad = []
    stale = [p for p, text in files.items() if not p.is_file() or p.read_text(encoding="utf-8") != text]
    tol = table["tolerance"]
    print(f"FE per SU through the alternator: {fe_per_su():.6f}")
    for route, fuel, got in routes:
        want = table["fuels"][fuel]["fe_per_bucket"]
        dev = got / want - 1
        print(f"  {fuel:18s} {route:38s} {got:>14,.0f} FE/bucket  table {want:>14,} ({dev:+.1%})")
        if abs(dev) > tol:
            bad.append(f"{fuel}: {route} {got:,.0f} FE per bucket, table {want:,} (more than {tol:.0%} off)")
    lad = table["ladder"]
    for lo, hi in zip(lad, lad[1:]):
        if table["fuels"][lo]["fe_per_bucket"] >= table["fuels"][hi]["fe_per_bucket"]:
            bad.append(f"ladder: {lo} >= {hi}")
    print("ladder: " + " < ".join(f"{x} {table['fuels'][x]['fe_per_bucket']:,}" for x in lad))
    if a.dump_dir:
        full = json.loads((Path(a.dump_dir) / "recipes_full.json").read_text(encoding="utf-8"))
        known = {f["ie"]["fluid_tag"] for f in table["fuels"].values() if "ie" in f} | set(table["dead"]["fluid_tags"])
        n = 0
        for rid, v in sorted(full.items()):
            j = json.loads(v) if isinstance(v, str) else v
            if j.get("type") == "immersiveengineering:generator_fuel":
                n += 1
                if j.get("fluidTag") not in known:
                    bad.append(f"{rid}: fluid tag {j.get('fluidTag')} is not in fuels.json")
        print(f"dump: {n} IE generator fuels")
    if a.check:
        for p in stale:
            print(f"STALE {p.relative_to(ROOT)}")
        for b in bad:
            print(f"FAIL {b}")
        print("gen_fuels --check: " + ("up to date" if not stale and not bad else f"{len(stale)} stale, {len(bad)} failed"))
        sys.exit(1 if stale or bad else 0)
    for p, text in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
    for b in bad:
        print(f"FAIL {b}")
    print(f"gen_fuels: {len(files)} files written, {len(stale)} changed")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
