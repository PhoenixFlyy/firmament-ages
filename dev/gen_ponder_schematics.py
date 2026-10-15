#!/usr/bin/env python3
"""Ponder schematics of firmages-core (SPEC 22.5) and the empty GameTest template of the proof-of-work tests.

Writes structure templates (gzip NBT, DataVersion 3955 = 1.21.1) from Python build plans, so the scenes can be rebuilt
without the game:

    mod/firmages-core/src/main/resources/assets/firmages/ponder/silo_hatch.nbt    scene "Silo Hatch" (ad_astra:launch_pad)
    mod/firmages-core/src/main/resources/assets/firmages/ponder/portcullis.nbt    scene "Portcullis" (create:rope_pulley)
    mod/firmages-core/src/gametest/resources/data/firmages/structure/silo_area.nbt  empty 11x11x11 template (GameTests)

The scene coordinates are repeated in compat/ponder/FirmagesScenes.java; move a block here and fix the selections
there. Ponder looks from the north-west (yRotation 145: north and west faces are visible), so the silo's earth is cut
away on those sides and the pad, the rocket and the hatch stay in view.

Usage:  python dev/gen_ponder_schematics.py [--check]
  --check  exit 1 if a file is missing or differs from its plan (content, not bytes: the gzip header does not count)
"""
import argparse
import gzip
import io
import os
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORE = os.path.join(REPO, "mod", "firmages-core", "src")
PONDER = os.path.join(CORE, "main", "resources", "assets", "firmages", "ponder")
GT_STRUCT = os.path.join(CORE, "gametest", "resources", "data", "firmages", "structure")
DATA_VERSION = 3955

# ---- NBT (only the tags structure templates need) ------------------------------------------------------------------
END, BYTE, INT, FLOAT, STRING, LIST, COMPOUND = 0, 1, 3, 5, 8, 9, 10


class Int(int):
    pass


class Float(float):
    pass


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _tag_type(v):
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, Float) or isinstance(v, float):
        return FLOAT
    if isinstance(v, int):
        return INT
    if isinstance(v, str):
        return STRING
    if isinstance(v, list):
        return LIST
    if isinstance(v, dict):
        return COMPOUND
    raise TypeError(type(v))


def _payload(v):
    t = _tag_type(v)
    if t == BYTE:
        return struct.pack(">b", 1 if v else 0)
    if t == INT:
        return struct.pack(">i", v)
    if t == FLOAT:
        return struct.pack(">f", v)
    if t == STRING:
        return _str(v)
    if t == LIST:
        et = _tag_type(v[0]) if v else END
        return struct.pack(">bi", et, len(v)) + b"".join(_payload(x) for x in v)
    out = b""
    for k, x in v.items():
        out += struct.pack(">b", _tag_type(x)) + _str(k) + _payload(x)
    return out + struct.pack(">b", END)


def nbt_bytes(root):
    raw = struct.pack(">b", COMPOUND) + _str("") + _payload(root)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as g:
        g.write(raw)
    return buf.getvalue()


# ---- build plans ---------------------------------------------------------------------------------------------------------
class Plan:
    def __init__(self, size):
        self.size = size
        self.blocks = {}  # (x, y, z) -> (name, props, nbt)

    def set(self, x, y, z, name, props=None, nbt=None):
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise ValueError(f"{name} at {(x, y, z)} outside {self.size}")
        self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def clear(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks.pop((x, y, z), None)

    def nbt(self):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[pos]
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(props)
                palette.append(entry)
            b = {"pos": [Int(c) for c in pos], "state": Int(index[key])}
            if nbt:
                b["nbt"] = nbt
            blocks.append(b)
        if not palette:
            palette.append({"Name": "minecraft:air"})
        return {"DataVersion": Int(DATA_VERSION), "size": [Int(c) for c in self.size], "palette": palette,
                "blocks": blocks, "entities": []}


def silo_hatch():
    """14 x 9 x 14: floor y0, shaft x/z 3..9 (7 wide), pad y1 centred (6,1,6), rocket (6,2..6,6), deck y7.

    Hatch: west leaf x3..5, east leaf x6..9 on y7; pockets in the deck: west x0..2, east x10..13 (z3..9)."""
    p = Plan((14, 9, 14))
    p.fill(0, 0, 0, 13, 0, 13, "minecraft:stone")
    for y in range(1, 7):  # earth; the shaft wall is lined with stone bricks
        for x in range(14):
            for z in range(14):
                if 3 <= x <= 9 and 3 <= z <= 9:
                    continue
                lining = 2 <= x <= 10 and 2 <= z <= 10
                p.set(x, y, z, "minecraft:stone_bricks" if lining else ("minecraft:andesite" if (x * 7 + y * 3 + z * 5) % 5 == 0 else "minecraft:stone"))
    for x in range(14):  # deck: polished andesite rim, grass outside, pockets open
        for z in range(14):
            if 3 <= x <= 9 and 3 <= z <= 9:
                continue
            if 3 <= z <= 9 and (x <= 2 or x >= 10):
                continue
            rim = 2 <= x <= 10 and 2 <= z <= 10
            p.set(x, 7, z, "minecraft:polished_andesite" if rim else "minecraft:grass_block", None if rim else {"snowy": "false"})
    parts = {(-1, -1): "top_left", (0, -1): "top", (1, -1): "top_right", (-1, 0): "left", (0, 0): "center",
             (1, 0): "right", (-1, 1): "bottom_left", (0, 1): "bottom", (1, 1): "bottom_right"}
    for (dx, dz), part in parts.items():
        p.set(6 + dx, 1, 6 + dz, "ad_astra:launch_pad", {"part": part, "powered": "false", "waterlogged": "false"})
    p.set(6, 2, 6, "minecraft:iron_block")
    p.set(6, 3, 6, "minecraft:white_concrete")
    p.set(6, 4, 6, "minecraft:white_concrete")
    p.set(6, 5, 6, "minecraft:red_concrete")
    p.set(6, 6, 6, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    p.fill(3, 7, 3, 9, 7, 9, "minecraft:smooth_stone")
    return p


def portcullis():
    """9 x 8 x 9: wall x0..8, y1..6, z3..5; gate x3..5, y1..3; slot x3..5, y4..6, z4 (the grate starts up in it).

    Drive on y7, z4: rope pulley x4 (axis x), shafts x5..6, gearshift x7, input shaft x8; lever (7,7,3)."""
    p = Plan((9, 8, 9))
    p.fill(0, 0, 0, 8, 0, 8, "minecraft:stone_bricks")
    p.fill(0, 1, 3, 8, 6, 5, "minecraft:stone_bricks")
    p.clear(3, 1, 3, 5, 3, 5)
    p.clear(3, 4, 4, 5, 6, 4)
    for x in range(3, 6):
        for y in range(4, 7):
            p.set(x, y, 4, "minecraft:iron_bars", {"east": "true" if x < 5 else "false", "west": "true" if x > 3 else "false",
                                                    "north": "false", "south": "false", "waterlogged": "false"})
    p.set(4, 7, 4, "create:rope_pulley", {"axis": "x"})
    p.set(5, 7, 4, "create:shaft", {"axis": "x", "waterlogged": "false"})
    p.set(6, 7, 4, "create:shaft", {"axis": "x", "waterlogged": "false"})
    p.set(7, 7, 4, "create:gearshift", {"axis": "x", "powered": "false"})
    p.set(8, 7, 4, "create:shaft", {"axis": "x", "waterlogged": "false"})
    p.set(7, 7, 3, "minecraft:lever", {"face": "wall", "facing": "north", "powered": "false"})
    return p


def empty(size):
    return Plan(size)


OUTPUTS = [
    (os.path.join(PONDER, "silo_hatch.nbt"), silo_hatch),
    (os.path.join(PONDER, "portcullis.nbt"), portcullis),
    (os.path.join(GT_STRUCT, "silo_area.nbt"), lambda: empty((11, 11, 11))),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    bad = 0
    for path, build in OUTPUTS:
        data = nbt_bytes(build().nbt())
        rel = os.path.relpath(path, REPO)
        if args.check:
            cur = open(path, "rb").read() if os.path.exists(path) else None
            if cur is None or gzip.decompress(cur) != gzip.decompress(data):
                print(f"stale: {rel}")
                bad += 1
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        print(f"wrote {rel} ({len(data)} bytes)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
