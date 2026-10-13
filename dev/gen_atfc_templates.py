#!/usr/bin/env python3
"""Write pack copies of the Advanced TFC Tech multiblock templates with the disabled IE steel block swapped out.

Grist mill and power loom are built around one immersiveengineering:storage_steel, which is disabled in the pack
(the TFC steel block is the canonical one). IE's matcher accepts the TFC block through c:storage_blocks/steel, but
IE puts the TEMPLATE block back when the machine is taken apart and drops it when that part is broken, so every
teardown handed out the disabled block. The pack copies (kubejs/data/advancedtfctech/structure/multiblocks/) name
tfc:metal/block/steel instead; build, teardown and drops then all use the TFC block.

The swap is done on the decompressed NBT: a palette name is a length-prefixed string and no enclosing tag stores a
byte length, so replacing the prefixed string is a valid edit. The result is parsed again as a check.

Usage:  python dev/gen_atfc_templates.py [--check]   (--check: exit 1 if the pack copies are missing or stale)
"""
import argparse
import glob
import gzip
import os
import struct
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from derive_ages import _nbt  # noqa: E402

SWAP = {"immersiveengineering:storage_steel": "tfc:metal/block/steel"}
SRC = "data/advancedtfctech/structure/multiblocks/"
OUT = os.path.join(REPO, "kubejs", "data", "advancedtfctech", "structure", "multiblocks")


def nbt_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    jars = glob.glob(os.path.join(REPO, "test-server", "mods", "AdvancedTFCTech-*.jar"))
    if len(jars) != 1:
        sys.exit(f"AdvancedTFCTech jar in test-server/mods: {jars}")
    stale = []
    with zipfile.ZipFile(jars[0]) as z:
        for n in sorted(x for x in z.namelist() if x.startswith(SRC) and x.endswith(".nbt")):
            raw = gzip.decompress(z.read(n))
            new = raw
            for old, rep in SWAP.items():
                new = new.replace(b"\x08" + b"\x00\x04Name" + nbt_str(old), b"\x08" + b"\x00\x04Name" + nbt_str(rep))
            if new == raw:
                continue
            names = {p["Name"] for p in _nbt(gzip.compress(new))["palette"]}
            assert not names & SWAP.keys(), (n, names)
            path = os.path.join(OUT, n[len(SRC):])
            current = None
            if os.path.isfile(path):
                with open(path, "rb") as f:
                    current = gzip.decompress(f.read())
            if current == new:
                print(f"up to date: {os.path.relpath(path, REPO)}")
                continue
            stale.append(path)
            if not a.check:
                os.makedirs(OUT, exist_ok=True)
                with open(path, "wb") as f:
                    f.write(gzip.compress(new, mtime=0))
                print(f"wrote {os.path.relpath(path, REPO)} ({', '.join(f'{k} -> {v}' for k, v in SWAP.items())})")
    if a.check and stale:
        print(f"stale or missing: {[os.path.relpath(p, REPO) for p in stale]}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
