#!/usr/bin/env python3
"""Firmament Ages - machine energy and miner configs (harmony plan 2026-10-15, H2A; package A4 "miner vs ladder").

Sets the values of these shipped server configs (full files as the mods write them, so every instance gets them on
an update, like config/createdieselgenerators-server.toml; NeoForge 21.1 reads server configs from config/):
  config/Mekanism/machine-usage.toml             Mekanism machines x10 (Felix H2A), Digital Miner x12
  config/Mekanism/machine-storage.toml           the buffers grow with the usage (upgrades behave as in Mekanism)
  config/Mekanism/general.toml                   Digital Miner ticksPerMine 80 -> 200
  config/MekanismMoreMachine/machine-usage.toml  Mekanism: More Machine x10 (H2A, "More Machine equivalents")
  config/MekanismMoreMachine/machine-storage.toml
  config/immersiveengineering-server.toml        Excavator speed 1.0 -> 0.5 (and the [ores] of defaultconfigs/)
Evolved Mekanism has no usage config of its own: its Alloyer, Chemixer, Melter and Solidifier read Mekanism's
combiner, pressurizedReactionBase and chemicalOxidizer (javap EMBlockTypes, Evolved Mekanism 1.2.1-fix3), so they
follow the x10 here; its APT keeps 100,000 J/t (40,000 FE/t, the Quantum Age band).

Every value is the mod's default (the "# Default:" comment above the key in the same file) times the factor of the
table below; keys not in the table must equal their default (except IE's [ores], set by defaultconfigs/). The numbers
and the reasoning are in dev/harmonie-r2.md and dev/decisions-while-away.md (2026-10-16).

--check writes nothing and exits 1 when a value would change. --server-dir <instance> also reads the instance's
config/ (what the server loaded and wrote back) and fails on any value that differs from the shipped file.

Usage: python dev/gen_machine_usage.py [--check] [--server-dir <server folder>]
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
H2A = 10          # Felix H2A: Mekanism machine usage x10 (default 20-160 FE/t -> 200-1,600 FE/t)
MINER = 12        # Digital Miner usage 1,000 -> 12,000 J/t (4,800 FE/t; 0.96 M FE per block at 200 ticks, about 1.5x the
                  # excavator's FE per ore mB; 48,000 FE/t with 8 speed and 8 energy upgrades, inside one Space Age
                  # fission plant). Review fix 0.12.0: at x90 the Space Age miner cost 9x the excavator per mB and
                  # 360,000 FE/t upgraded, more than a whole Age-7 plant.
J_PER_FE = 2.5    # Mekanism feConversionRate

MEK_USAGE_KEEP = {"chargepad", "antiprotonicNucleosynthesizer", "dimensionalStabilizer", "randomizedConsumption"}
MM_KEEP = {"largeAntiprotonicNucleosynthesizer", "itemReplicator", "fluidReplicator", "chemicalReplicator"}


def mek_usage(sec, key):
    if key == "digitalMiner":
        return MINER
    return 1 if key in MEK_USAGE_KEEP else H2A        # teleporter base/distance/dimensionPenalty x10 too


def mek_storage(sec, key):
    # the buffer of every machine whose usage changed grows by the same factor
    if key == "digitalMiner":
        return MINER
    usage_keys = {k for _, k in _keys(ROOT / "config" / "Mekanism" / "machine-usage.toml")}
    return H2A if key in usage_keys and key not in MEK_USAGE_KEEP and sec == "" else 1


def mek_general(sec, key):
    return 2.5 if (sec, key) == ("digital_miner", "ticksPerMine") else 1


def mm(sec, key):
    return 1 if key in MM_KEEP or key not in _mm_usage_keys() else H2A


def _mm_usage_keys():
    return {k for _, k in _keys(ROOT / "config" / "MekanismMoreMachine" / "machine-usage.toml")}


def ie(sec, key):
    if (sec, key) == ("machines.excavator", "speed"):
        return 0.5
    if sec.startswith("ores.") and key == "vein_size":
        return 0                                       # defaultconfigs/immersiveengineering-server.toml (C1)
    return 1


FILES = {
    "config/Mekanism/machine-usage.toml": mek_usage,
    "config/Mekanism/machine-storage.toml": mek_storage,
    "config/Mekanism/general.toml": mek_general,
    "config/MekanismMoreMachine/machine-usage.toml": mm,
    "config/MekanismMoreMachine/machine-storage.toml": mm,
    "config/immersiveengineering-server.toml": ie,
}
VALUE = re.compile(r"^(\s*)([A-Za-z_0-9]+)(\s*=\s*)(-?[0-9][0-9.eE+-]*)(\s*)$")


def _keys(path):
    out, sec = [], ""
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*\[([^\]]+)\]\s*$", line)
        if m:
            sec = m.group(1).strip()
            continue
        m = VALUE.match(line)
        if m:
            out.append((sec, m.group(2)))
    return out


def fmt(v, like):
    if "." in like or "e" in like.lower():
        return repr(float(v))
    return str(int(round(v)))


def rewrite(path, factor_of):
    """(new text, [(section, key, default, factor, value)]) with every numeric key = default x factor."""
    lines = path.read_text(encoding="utf-8").split("\n")
    sec, default, rows = "", None, []
    for i, line in enumerate(lines):
        m = re.match(r"^\s*\[([^\]]+)\]\s*$", line)
        if m:
            sec, default = m.group(1).strip(), None
            continue
        m = re.match(r"^\s*#\s*Default:\s*(-?[0-9][0-9.eE+-]*)\s*$", line)
        if m:
            default = m.group(1)
            continue
        m = VALUE.match(line)
        if m:
            f = factor_of(sec, m.group(2))
            if default is None:
                if f != 1:
                    sys.exit(f"gen_machine_usage: {path} [{sec}] {m.group(2)} has no '# Default:' comment")
            else:
                new = fmt(float(default) * f, default)
                lines[i] = f"{m.group(1)}{m.group(2)}{m.group(3)}{new}{m.group(5)}"
                rows.append((sec, m.group(2), default, f, new))
            default = None
        elif not line.strip().startswith("#"):
            default = None
    return "\n".join(lines), rows


def values(path):
    out, sec = {}, ""
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*\[([^\]]+)\]\s*$", line)
        if m:
            sec = m.group(1).strip()
            continue
        m = VALUE.match(line)
        if m:
            out[(sec, m.group(2))] = float(m.group(4))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--server-dir")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    new, stale, bad, changed = {}, [], [], 0
    for rel, fn in FILES.items():
        p = ROOT / rel
        text, rows = rewrite(p, fn)
        new[p] = text
        if text != p.read_text(encoding="utf-8"):
            stale.append(p)
        for sec, key, d, f, v in rows:
            if f != 1:
                changed += 1
                if "usage" in rel and "Mekanism/" in rel:
                    print(f"  {rel.split('/')[1]:24s} {(sec + '.' if sec else '') + key:34s} "
                          f"{float(d) / J_PER_FE:>10,.0f} -> {float(v) / J_PER_FE:>10,.0f} FE/t (x{f:g})")
                elif "Mekanism" not in rel or "general" in rel:
                    print(f"  {rel.split('/')[-1]:24s} {(sec + '.' if sec else '') + key:34s} {d} -> {v}")
    if a.server_dir:
        n = 0
        for rel in FILES:
            sp = Path(a.server_dir) / rel
            if not sp.is_file():
                bad.append(f"server: {sp} missing")
                continue
            want, got = values(ROOT / rel), values(sp)
            for k, v in want.items():
                n += 1
                if got.get(k) != v:
                    bad.append(f"server {rel} [{k[0]}] {k[1]} = {got.get(k)}, shipped {v:g}")
        print(f"server {a.server_dir}: {n} values read back")
    if a.check:
        for p in stale:
            print(f"STALE {p.relative_to(ROOT)}")
        for b in bad:
            print(f"FAIL {b}")
        print(f"gen_machine_usage --check: {changed} values off their default; " +
              ("up to date" if not stale and not bad else f"{len(stale)} stale, {len(bad)} failed"))
        sys.exit(1 if stale or bad else 0)
    for p, t in new.items():
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(t)
    for b in bad:
        print(f"FAIL {b}")
    print(f"gen_machine_usage: {len(new)} files, {len(stale)} changed, {changed} values off their default")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
