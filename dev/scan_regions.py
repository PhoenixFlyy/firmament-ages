#!/usr/bin/env python3
"""Offline worldgen audit: count blocks in generated chunks by reading Anvil region files (no dependencies).

Reads <world>/<dim>/region/r.X.Z.mca, keeps chunks whose centre lies within --radius blocks of (--x, --z)
and whose Status is minecraft:full, and counts every block state name by decoding the section palettes.
Prints the chunk count, block totals per namespace, and the block ids that match each --match prefix.
Use it after a Chunky pregen (run "save-all flush" first, or stop the server).

Usage:
  python dev/scan_regions.py --x 1600 --z -2000 --radius 256
  python dev/scan_regions.py --dim DIM-1 --match "beneath:" --match "minecraft:nether_quartz_ore"
Exit code: 0 always (it reports, the caller judges).
"""
import argparse
import collections
import gzip
import os
import struct
import sys
import zlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MATCH = ["tfc:ore/", "create:zinc_ore", "create:deepslate_zinc_ore", "create:crimsite", "create:asurine",
                 "create:veridium", "create:ochrum", "immersiveengineering:", "mekanism:", "occultism:silver_ore",
                 "mekatfc:", "evolvedmekanism:", "tfc_ie_addon:ore/"]


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        v = self.d[self.i:self.i + n]
        self.i += n
        return v

    def u(self, fmt, n):
        return struct.unpack(fmt, self.take(n))[0]

    def string(self):
        n = self.u(">H", 2)
        return self.take(n).decode("utf-8", "replace")

    def payload(self, t):
        if t == 1:
            return self.u(">b", 1)
        if t == 2:
            return self.u(">h", 2)
        if t == 3:
            return self.u(">i", 4)
        if t == 4:
            return self.u(">q", 8)
        if t == 5:
            return self.u(">f", 4)
        if t == 6:
            return self.u(">d", 8)
        if t == 7:
            n = self.u(">i", 4)
            return self.take(n)
        if t == 8:
            return self.string()
        if t == 9:
            et = self.u(">b", 1)
            n = self.u(">i", 4)
            return [self.payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                ct = self.u(">b", 1)
                if ct == 0:
                    return out
                name = self.string()
                out[name] = self.payload(ct)
        if t == 11:
            n = self.u(">i", 4)
            return struct.unpack(f">{n}i", self.take(4 * n))
        if t == 12:
            n = self.u(">i", 4)
            return struct.unpack(f">{n}q", self.take(8 * n))
        raise ValueError(f"bad NBT tag {t}")


def read_nbt(data):
    r = Reader(data)
    t = r.u(">b", 1)
    r.string()
    return r.payload(t)


def chunks(path):
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 8192:
        return
    for idx in range(1024):
        loc = struct.unpack(">I", data[idx * 4:idx * 4 + 4])[0]
        off, cnt = loc >> 8, loc & 0xFF
        if off == 0 or cnt == 0:
            continue
        start = off * 4096
        length = struct.unpack(">I", data[start:start + 4])[0]
        comp = data[start + 4]
        raw = data[start + 5:start + 4 + length]
        if comp == 2:
            raw = zlib.decompress(raw)
        elif comp == 1:
            raw = gzip.decompress(raw)
        elif comp != 3:
            continue  # lz4 or external .mcc: not used by this server
        yield read_nbt(raw)


def section_counts(bs):
    palette = [p["Name"] for p in bs.get("palette", [])]
    data = bs.get("data")
    if len(palette) == 1 or not data:
        return {palette[0]: 4096} if palette else {}
    bits = max(4, (len(palette) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    counts = [0] * len(palette)
    n = 0
    for word in data:
        word &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            if n >= 4096:
                break
            counts[(word & mask)] += 1
            word >>= bits
            n += 1
    return {palette[i]: c for i, c in enumerate(counts) if c}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--world", default=os.path.join(REPO, "test-server", "world"))
    ap.add_argument("--dim", default="", help="sub folder of the world, e.g. DIM-1 or dimensions/twilightforest/twilight_forest")
    ap.add_argument("--x", type=int, default=0)
    ap.add_argument("--z", type=int, default=0)
    ap.add_argument("--radius", type=int, default=256)
    ap.add_argument("--match", action="append", default=None, help="block id prefix to report (repeatable)")
    ap.add_argument("--top", type=int, default=12)
    a = ap.parse_args()
    match = a.match or DEFAULT_MATCH
    region_dir = os.path.join(a.world, a.dim, "region")
    if not os.path.isdir(region_dir):
        sys.exit(f"no region folder: {region_dir}")

    totals = collections.Counter()
    status = collections.Counter()
    used = 0
    for name in sorted(os.listdir(region_dir)):
        if not name.endswith(".mca"):
            continue
        for c in chunks(os.path.join(region_dir, name)):
            cx, cz = c.get("xPos"), c.get("zPos")
            if cx is None:
                continue
            mx, mz = cx * 16 + 8, cz * 16 + 8
            if (mx - a.x) ** 2 + (mz - a.z) ** 2 > a.radius ** 2:
                continue
            st = c.get("Status", "?")
            status[st] += 1
            if st != "minecraft:full":
                continue
            used += 1
            for sec in c.get("sections", []):
                bs = sec.get("block_states")
                if bs:
                    totals.update(section_counts(bs))

    print(f"region folder: {region_dir}")
    print(f"centre ({a.x}, {a.z}) radius {a.radius}: chunk status {dict(status)}; scanned {used} full chunks")
    ns = collections.Counter()
    for bid, n in totals.items():
        ns[bid.split(":", 1)[0]] += n
    print("blocks per namespace: " + ", ".join(f"{k}={v}" for k, v in ns.most_common()))
    for prefix in match:
        hits = {b: n for b, n in totals.items() if b.startswith(prefix)}
        tot = sum(hits.values())
        print(f"match {prefix!r}: {tot} block(s) in {len(hits)} id(s)")
        for b, n in sorted(hits.items(), key=lambda kv: -kv[1])[:a.top]:
            print(f"    {n:>8} {b}")


if __name__ == "__main__":
    main()
