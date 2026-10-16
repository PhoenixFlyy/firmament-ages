#!/usr/bin/env python3
"""Firmament Ages - miners against the ore ladder (harmony round 2, Felix: "4x+ must stay worth it next to the
unlimited miners").

Computes, from the shipped files, what one Digital Miner and one IE Excavator deliver (ore pieces, mB and ingots per
hour) and the FE per ingot of every ladder step when a miner feeds it (mining + processing). With unlimited miners
energy is what limits a base, so a ladder step is worth it when it lowers the FE per ingot. The rules checked here:
  - every step (2.5x -> 3x -> 4x -> 5x) lowers the FE per ingot by at least --min-gain (default 5 %) for the Digital
    Miner and for the average excavator vein, with 8 energy upgrades in every Mekanism machine (the Chronicle teaches
    them in the Information Age; the miner on any equal number of speed and energy upgrades: FE per block as without);
    the rows without energy upgrades are printed, not checked (there only 3x pays off: the trap the quest names);
  - the Digital Miner (Space Age) costs 1 to 2 times the average excavator vein's FE per ore mB (review fix 0.12.0:
    the later miner may cost more per mB, never be useless);
  - a fully upgraded Digital Miner (8 speed, 8 energy) draws at most --plant FE/t, one Space Age plant (fission at
    1 mB/t through a turbine: 100,000 FE/t measured after the A8 tuning, dev/harmonie-messung.md).

Inputs: kubejs/data/firmages/balance/metals.json (factors, grades, IE Crusher energy), config/Mekanism/machine-usage.toml
and general.toml (usage J/t, ticksPerMine), config/immersiveengineering-server.toml (excavator consumption, speed),
the mineral mixes (recipes_full.json of a /fa_dump_full with --dump-dir, else kubejs/data/immersiveengineering).
Machine times are Mekanism 10.7.19's (javap: 200 ticks per operation, dissolution 100, washer 1 mB per tick); the
excavator digs once per 45 / speed ticks (IE 12.4.2 ExcavatorLogic: 360 degrees, 8 buckets). Chemicals (oxygen,
hydrogen chloride, sulfuric acid, water) are not counted; they only make the higher steps dearer.

Usage: python dev/miner_ladder.py [--dump-dir <fa_dump folder>] [--vein-mb 24] [--min-gain 0.05] [--check]
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J_PER_FE = 2.5
TICKS = {"enrichmentChamber": 200, "crusher": 200, "purificationChamber": 200, "chemicalInjectionChamber": 200,
         "chemicalDissolutionChamber": 100, "chemicalCrystallizer": 200}
SLURRY_PER_CRYSTAL = 200


def toml_values(path):
    out, sec = {}, ""
    for line in open(path, encoding="utf-8"):
        m = re.match(r"^\s*\[([^\]]+)\]\s*$", line)
        if m:
            sec = m.group(1).strip()
            continue
        m = re.match(r"^\s*([A-Za-z_0-9]+)\s*=\s*(-?[0-9][0-9.eE+-]*)\s*$", line)
        if m:
            out[(sec, m.group(1))] = float(m.group(2))
    return out


def ratio(target, max_n, tol):
    """n pieces -> m items like startup_scripts/metal_table.js: smallest exact n, else smallest within tol, else closest."""
    def err(n):
        m = round(target * n)
        return abs(m / n - target) / target if m > 0 else 1e9
    best, first = 1, 0
    for n in range(1, max_n + 1):
        if err(n) <= 0.005:
            return n, round(target * n)
        if not first and err(n) <= tol:
            first = n
        if err(n) < err(best):
            best = n
    n = first or best
    return n, max(1, round(target * n))


def mixes(dump_dir):
    rows = []
    if dump_dir:
        full = json.load(open(os.path.join(dump_dir, "recipes_full.json"), encoding="utf-8"))
        items = [(k, json.loads(v) if isinstance(v, str) else v) for k, v in full.items()]
    else:
        items = [(os.path.basename(p), json.load(open(p, encoding="utf-8")))
                 for p in glob.glob(os.path.join(ROOT, "kubejs", "data", "immersiveengineering", "recipe", "mineral", "*.json"))]
    return [(k, j) for k, j in items if j.get("type") == "immersiveengineering:mineral_mix"]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump-dir")
    ap.add_argument("--vein-mb", type=float, default=24.0, help="mean mB per ore block of a TFC vein (TFC 4.2.11: "
                    "18.5 poor-heavy, 24.0 most iron/tin/gold veins, 29.5 rich veins)")
    ap.add_argument("--min-gain", type=float, default=0.05)
    ap.add_argument("--plant", type=float, default=100000.0, help="FE/t of one Space Age plant (dev/harmonie-messung.md: fission 1 mB/t)")
    ap.add_argument("--check", action="store_true", help="exit 1 when a step fails the rule")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    mt = json.load(open(os.path.join(ROOT, "kubejs", "data", "firmages", "balance", "metals.json"), encoding="utf-8"))
    gmb, st = mt["grade_mb"], mt["stations"]
    use = toml_values(os.path.join(ROOT, "config", "Mekanism", "machine-usage.toml"))
    gen = toml_values(os.path.join(ROOT, "config", "Mekanism", "general.toml"))
    ie = toml_values(os.path.join(ROOT, "config", "immersiveengineering-server.toml"))
    op = {k: use[("", k)] / J_PER_FE * t for k, t in TICKS.items()}            # FE per operation, no upgrades
    wash = use[("", "chemicalWasher")] / J_PER_FE * SLURRY_PER_CRYSTAL          # FE per crystal's slurry
    mb = gmb["normal"]
    f = {k: float(st[k]["factor"]) for k in ("ie_crusher", "mek_purifying", "mek_injecting", "mek_dissolution")}

    def proc(div):
        """FE per ingot of processing for each factor (normal pieces), Mekanism FE divided by div (energy upgrades)."""
        iec = st["ie_crusher"]
        e_ie = (float(iec["energy_base"]) + float(iec["energy_per_mb"]) * mb) / (mb * f["ie_crusher"] / 100)
        pur = op["purificationChamber"] / div
        cr, en = op["crusher"] / div, op["enrichmentChamber"] / div
        inj = op["chemicalInjectionChamber"] / div
        dis = op["chemicalDissolutionChamber"] / div / (mb * f["mek_dissolution"] / 100)
        # one purifying / injecting operation takes n pieces and gives m clumps / shards (the metal table's ratio)
        m_pur = ratio(mb * f["mek_purifying"] / 100, mt["max_pieces"], mt["tolerance"])[1]
        m_inj = ratio(mb * f["mek_injecting"] / 100, mt["max_pieces"], mt["tolerance"])[1]
        return {f["ie_crusher"]: e_ie,
                f["mek_purifying"]: pur / m_pur + cr + en,
                f["mek_injecting"]: inj / m_inj + pur + cr + en,
                f["mek_dissolution"]: dis + wash / div + op["chemicalCrystallizer"] / div + inj + pur + cr + en}

    # ---- miners -------------------------------------------------------------------------------------------------
    dm_use = use[("", "digitalMiner")] / J_PER_FE
    dm_ticks = gen[("digital_miner", "ticksPerMine")]
    dm_block = dm_use * dm_ticks                                               # FE per block, equal upgrades
    ex_fe, ex_speed = ie[("machines.excavator", "consumption")], ie[("machines.excavator", "speed")]
    ex_ticks = 45.0 / ex_speed
    print(f"Digital Miner: {dm_use:,.0f} FE/t, {dm_ticks:.0f} ticks per block without upgrades -> {dm_block:,.0f} FE "
          f"per block (any equal speed/energy upgrades), {72000 / dm_ticks:,.0f} blocks/h without, "
          f"{72000 * 10 / dm_ticks:,.0f} with 8 speed upgrades ({dm_use * 10:,.0f} FE/t with 8 energy, "
          f"{dm_use * 100:,.0f} without); vein mean {a.vein_mb} mB per block")
    print(f"IE Excavator: {ex_fe:,.0f} FE/t, speed {ex_speed:g} -> one dig per {ex_ticks:.0f} ticks, "
          f"{72000 / ex_ticks:,.0f} digs/h, {ex_fe * ex_ticks:,.0f} FE per dig")
    mx = []
    for rid, j in mixes(a.dump_dir):
        tot = sum(o.get("chance", 0) for o in j.get("ores", []))
        ore_mb, ore_share = 0.0, 0.0
        for o in j.get("ores", []):
            oid = (o.get("output") or {}).get("id") or (o.get("output") or {}).get("item") or ""
            m = re.search(r":ore/(small|poor|normal|rich)_", oid)
            if m and tot:
                ore_mb += o["chance"] / tot * gmb[m.group(1)]
                ore_share += o["chance"] / tot
        keep = 1 - float(j.get("fail_chance", 0))
        if ore_mb > 0:
            mx.append((rid, float(j.get("weight", 1)), ore_mb * keep, ore_share * keep))
    mx.sort(key=lambda r: -r[2])
    for rid, w, mbd, share in mx:
        print(f"  mix {rid:55s} weight {w:>4.0f}  {mbd:5.2f} mB per dig, {share * 72000 / ex_ticks:6,.0f} ore pieces/h, "
              f"{mbd * 72000 / ex_ticks / 100:5.0f} ingots/h at 1x, {ex_fe * ex_ticks / mbd:>8,.0f} FE per mB")
    wsum = sum(r[1] for r in mx) or 1
    ex_mean = sum(r[1] * r[2] for r in mx) / wsum

    # ---- FE per ingot -------------------------------------------------------------------------------------------
    routes = [("Digital Miner, Mekanism with 8 energy upgrades", dm_block / a.vein_mb, 10),
              ("Digital Miner, Mekanism without upgrades", dm_block / a.vein_mb, 1),
              (f"Excavator, average vein ({ex_mean:.2f} mB/dig), Mekanism without upgrades", ex_fe * ex_ticks / ex_mean, 1),
              (f"Excavator, average vein, Mekanism with 8 energy upgrades", ex_fe * ex_ticks / ex_mean, 10)]
    if mx:
        routes.append((f"Excavator, richest vein ({mx[0][2]:.2f} mB/dig), Mekanism with 8 energy upgrades",
                       ex_fe * ex_ticks / mx[0][2], 10))
    bad = []
    for name, m_per_mb, div in routes:
        p = proc(div)
        print(f"\n{name}: mining {m_per_mb:,.0f} FE per mB")
        prev = None
        for fac in sorted(p):
            fe = m_per_mb * 100 / fac + p[fac]
            gain = "" if prev is None else f"  {fe / prev - 1:+.1%}"
            print(f"  {fac:g}x: {m_per_mb * 100 / fac:>12,.0f} mining + {p[fac]:>10,.0f} processing = {fe:>12,.0f} FE per ingot{gain}")
            rule = div == 10 and (name.startswith("Digital") or "average" in name)
            if prev is not None and rule and fe > prev * (1 - a.min_gain):
                bad.append(f"{name}: {fac:g}x saves less than {a.min_gain:.0%}")
            prev = fe
    ratio_dm = (dm_block / a.vein_mb) / (ex_fe * ex_ticks / ex_mean)
    full = dm_use * 10
    print(f"\nDigital Miner against the excavator: {dm_block / a.vein_mb:,.0f} / {ex_fe * ex_ticks / ex_mean:,.0f} FE per "
          f"ore mB = {ratio_dm:.2f}x; fully upgraded {full:,.0f} FE/t against one Space Age plant of {a.plant:,.0f} FE/t")
    if not 1.0 <= ratio_dm <= 2.0:
        bad.append(f"Digital Miner costs {ratio_dm:.2f}x the average excavator vein per ore mB, want 1 to 2")
    if full > a.plant:
        bad.append(f"a fully upgraded Digital Miner draws {full:,.0f} FE/t, more than one Space Age plant ({a.plant:,.0f})")
    for b in bad:
        print(f"FAIL {b}")
    print("miner_ladder: " + ("every step lowers the FE per ingot, the miners fit their Ages" if not bad else f"{len(bad)} failed"))
    sys.exit(1 if bad and a.check else 0)


if __name__ == "__main__":
    main()
