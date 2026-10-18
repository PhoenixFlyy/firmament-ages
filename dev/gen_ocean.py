#!/usr/bin/env python3
"""Firmament Ages - write the ocean structure templates (kubejs/data/firmages/structure/ocean/*.nbt), Felix O1B.

Three shipwrecks of TFC wood and two sunken ruins of TFC rock on the ocean floor; every block is a Dawn block (TFC
wood and rock, no metal, no chain, no glass), checked by dev/poc_analyze.py X-6. The worldgen files next to them:
  kubejs/data/firmages/worldgen/structure/ocean_wreck.json        jigsaw, OCEAN_FLOOR_WG, sunk one block, no beard
  kubejs/data/firmages/worldgen/structure/ocean_ruin.json         jigsaw, OCEAN_FLOOR_WG, sunk one block, beard_thin
  kubejs/data/firmages/worldgen/structure_set/ocean_wreck.json    random spread, spacing 28, separation 10
  kubejs/data/firmages/worldgen/structure_set/ocean_ruin.json     random spread, spacing 36, separation 12
  kubejs/data/firmages/worldgen/template_pool/ocean_wreck.json    the three wrecks
  kubejs/data/firmages/worldgen/template_pool/ocean_ruin.json     the two ruins with the processor list below
  kubejs/data/firmages/worldgen/processor_list/ocean_ruin.json    mossy and cracked bricks, mossy cobble
  kubejs/data/firmages/tags/worldgen/biome/has_structure/ocean_{wreck,ruin}.json   TFC ocean biomes
  kubejs/data/firmages/loot_table/chests/ocean_{wreck,ruin}.json  Age pools, the loot gate keeps the opener's Ages
Water: the templates hold no air and no water block; every position not listed keeps the sea (and the floor), so the
inside of a hull or a ruin stays water. Stairs, slabs, fences, walls and chests carry waterlogged=true.

Usage
  python dev/gen_ocean.py           write the templates
  python dev/gen_ocean.py --check   exit 1 if a file on disk differs or a block is not in the baseline registry
"""
import glob
import gzip
import io
import json
import os
import struct
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
sys.path.insert(0, DEV)
from gen_cold_forge import DATA_VERSION, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_STRING, _payload, _str, compound  # noqa: E402

OUT_DIR = os.path.join(REPO, "kubejs", "data", "firmages", "structure", "ocean")
LOOT_WRECK = "firmages:chests/ocean_wreck"
LOOT_RUIN = "firmages:chests/ocean_ruin"
CHEST_BE = "tfc:chest"   # block entity of tfc:wood/chest/<wood>, as in gen_cold_forge.py
WET = {"waterlogged": "true"}


def holed(x, y, z, every):
    """Deterministic rot: True for roughly one cell in `every`."""
    return every and (x * 31 + y * 17 + z * 7) % every == 0


class T:
    def __init__(self):
        self.b = {}

    def put(self, pos, name, props=None, nbt=None):
        self.b[pos] = (name, dict(props or {}), nbt)

    def drop(self, pos):
        self.b.pop(pos, None)

    def chest(self, pos, wood, facing, loot):
        self.put(pos, f"tfc:wood/chest/{wood}", {"facing": facing, "type": "single", **WET},
                 {"id": CHEST_BE, "LootTable": loot})


# ---------------------------------------------------------------------------------------------------------- wrecks
def hull(t, wood, length, half, height, lift, rot, x0=0, open_end=False, low_side=None):
    """A hull along z: keel of logs, plank floor and sides, stair stems at both ends. lift(z) is the extra height of
    row z (the wreck lies tilted), low_side (-1 or 1) loses its top row (rolled over), rot leaves holes."""
    planks, log = f"tfc:wood/planks/{wood}", f"tfc:wood/log/{wood}"
    stairs, slab = f"tfc:wood/planks/{wood}_stairs", f"tfc:wood/planks/{wood}_slab"
    cx = x0 + half
    for z in range(length):
        w = min(half, 1 + z, half if open_end else 1 + (length - 1 - z))
        dy = lift(z)
        t.put((cx, dy, z), log, {"axis": "z"})
        for x in range(cx - w + 1, cx + w):
            if x != cx and not holed(x, dy, z, rot * 2):
                t.put((x, dy, z), planks)
        for side in (-1, 1):
            top = height - (1 if side == low_side else 0)
            for y in range(1, top + 1):
                if not holed(cx + side * w, dy + y, z, rot):
                    t.put((cx + side * w, dy + y, z), planks)
            if z % 3 == 1 and side != low_side:
                t.put((cx + side * w, dy + top + 1, z), slab, {"type": "bottom", **WET})
    ends = [(0, "north")] if open_end else [(0, "north"), (length - 1, "south")]
    for z, facing in ends:
        dy = lift(z)
        for y in range(1, height + 1):
            t.put((cx, dy + y, z), stairs, {"facing": facing, "half": "bottom", "shape": "straight", **WET})
    return cx


def wreck_skiff():
    """A small fishing boat of birch, 5 x 4 x 9, bow lifted on a rock: two thwarts, oars as fences, one chest."""
    t, wood = T(), "birch"
    lift = lambda z: 1 if z >= 6 else 0  # noqa: E731
    cx = hull(t, wood, 9, 2, 2, lift, 7)
    for z in (3, 6):
        t.put((cx, lift(z) + 1, z), f"tfc:wood/planks/{wood}_slab", {"type": "bottom", **WET})
    t.put((cx - 1, 1, 4), f"tfc:wood/fence/{wood}", WET)
    t.put((cx + 1, 1, 2), f"tfc:wood/fence/{wood}", WET)
    t.chest((cx, 1, 1), wood, "south", LOOT_WRECK)
    return t


def wreck_cog():
    """A trading cog of Douglas fir, 9 x 9 x 17, stern sunk, rolled to port: half a deck, a stern castle, a broken
    mast with the top lying beside the hull, two chests in the hold."""
    t, wood = T(), "douglas_fir"
    planks, log = f"tfc:wood/planks/{wood}", f"tfc:wood/log/{wood}"
    lift = lambda z: z // 5  # noqa: E731
    cx = hull(t, wood, 17, 3, 4, lift, 6, low_side=-1)
    # deck at the top of the hull, a third of it gone
    for z in range(2, 15):
        dy = lift(z)
        for x in range(cx - 2, cx + 3):
            if not holed(x, 9, z, 3) and not (5 <= z <= 9 and x <= cx):
                t.put((x, dy + 4, z), planks)
    # stern castle
    for z in (1, 2, 3):
        for x in range(cx - 1, cx + 2):
            t.put((x, 5, z), planks)
        t.put((cx - 2, 5, z), f"tfc:wood/fence/{wood}", WET)
        t.put((cx + 2, 5, z), f"tfc:wood/fence/{wood}", WET)
    # mast stump through the deck, the fallen top beside the hull on the floor
    for y in range(1, 7):
        t.put((cx, lift(8) + y, 8), log, {"axis": "y"})
    for z in range(6, 13):
        t.put((cx + 5, 0, z), log, {"axis": "z"})
    t.put((cx + 5, 1, 9), f"tfc:wood/fence/{wood}", WET)
    # cargo: two chests and a barrel in the hold
    t.chest((cx - 1, 1, 4), wood, "east", LOOT_WRECK)
    t.chest((cx + 1, lift(11) + 1, 11), wood, "west", LOOT_WRECK)
    t.put((cx + 2, lift(12) + 1, 12), f"tfc:wood/barrel/{wood}")
    return t


def wreck_mast():
    """The broken bow half of a pine ship, 12 x 6 x 10: the hull ends ragged at the break, the mast lies across the
    floor and leans on the hull in steps, one chest."""
    t, wood = T(), "pine"
    log = f"tfc:wood/log/{wood}"
    lift = lambda z: 2 - z // 4  # noqa: E731  (the bow points up)
    cx = hull(t, wood, 10, 3, 3, lift, 5, x0=5, open_end=True, low_side=1)
    # ragged break at z = 9 (open end): drop the upper planks of the last two rows
    for z in (8, 9):
        for side in (-1, 1):
            for y in range(2, 5):
                t.drop((cx + side * 3, lift(z) + y, z))
    # the mast: lying on the floor from the bow side outward, stepping up onto the hull wall
    for x in range(0, cx - 3):
        t.put((x, 0, 5), log, {"axis": "x"})
    for x in range(cx - 3, cx + 1):
        t.put((x, lift(5) + (2 if x >= cx - 1 else 1), 5), log, {"axis": "x"})
    t.put((cx - 4, 1, 5), f"tfc:wood/fence/{wood}", WET)
    t.put((cx - 4, 2, 5), f"tfc:wood/fence/{wood}", WET)
    t.chest((cx + 1, lift(3) + 1, 3), wood, "north", LOOT_WRECK)
    return t


# ----------------------------------------------------------------------------------------------------------- ruins
def rock_set(rock):
    r = f"tfc:rock/%s/{rock}"
    return {"bricks": r % "bricks", "cobble": r % "cobble", "smooth": r % "smooth", "raw": r % "raw",
            "stairs": f"tfc:rock/bricks/{rock}_stairs", "slab": f"tfc:rock/bricks/{rock}_slab",
            "wall": f"tfc:rock/bricks/{rock}_wall", "smooth_slab": f"tfc:rock/smooth/{rock}_slab"}


def ruin_colonnade():
    """A sunken colonnade of granite, 9 x 7 x 13: a cracked platform, two rows of columns of different heights, two
    toppled columns lying across the floor, a slab lintel on the tallest pair, one chest at the far end."""
    t, R = T(), rock_set("granite")
    for x in range(9):
        for z in range(13):
            if holed(x, 0, z, 6) or (x in (0, 8) and z % 4 == 3):
                continue
            t.put((x, 0, z), (R["smooth"], R["cobble"], R["smooth"], R["bricks"])[(x * 5 + z * 3) % 4])
    heights = {(1, 1): 6, (1, 4): 3, (1, 7): 5, (1, 10): 1, (7, 1): 6, (7, 4): 5, (7, 7): 2, (7, 10): 4}
    for (x, z), h in heights.items():
        for y in range(1, h + 1):
            t.put((x, y, z), R["bricks"] if y < h or h < 4 else R["wall"], WET if y == h and h >= 4 else None)
    for x in range(1, 8):  # lintel over the first pair
        t.put((x, 6, 1), R["slab"], {"type": "bottom", **WET})
    t.drop((4, 6, 1))
    for z in range(5, 10):  # a column fallen across the floor
        t.put((4, 1, z), R["bricks"])
    for x in (2, 3):
        t.put((x, 1, 11), R["cobble"])
    t.put((4, 1, 11), R["stairs"], {"facing": "west", "half": "bottom", "shape": "straight", **WET})
    t.chest((4, 1, 12), "oak", "north", LOOT_RUIN)
    return t


def ruin_shrine():
    """A drowned shrine of basalt, 9 x 6 x 9: walls broken to stumps, the sea side fallen in, steps up to an altar of
    smooth stone, roof slabs lying on the floor, two chests."""
    t, R = T(), rock_set("basalt")
    for x in range(9):
        for z in range(9):
            t.put((x, 0, z), R["smooth"] if 2 <= x <= 6 and 2 <= z <= 6 else R["cobble"])
    tops = {}
    for i in range(9):
        tops[(i, 8)] = 5 if 2 <= i <= 6 else 4
        tops[(0, i)] = 4 - i // 3
        tops[(8, i)] = 3 if i >= 4 else 1
        tops[(i, 0)] = 0 if 3 <= i <= 5 else (2 if i in (0, 8) else 1)
    for (x, z), top in tops.items():
        for y in range(1, top + 1):
            if not holed(x, y, z, 9):
                t.put((x, y, z), R["bricks"])
    for x in range(3, 6):  # steps and altar
        t.put((x, 1, 5), R["stairs"], {"facing": "north", "half": "bottom", "shape": "straight", **WET})
        t.put((x, 1, 6), R["smooth"])
        t.put((x, 2, 6), R["smooth_slab"], {"type": "bottom", **WET})
    for x, z in ((2, 2), (6, 3), (5, 2), (1, 6)):  # fallen roof
        t.put((x, 1, z), R["slab"], {"type": "bottom", **WET})
    t.put((7, 1, 2), R["cobble"])
    t.put((7, 2, 2), R["wall"], WET)
    t.chest((4, 3, 6), "oak", "north", LOOT_RUIN)
    t.chest((1, 1, 7), "oak", "east", LOOT_RUIN)
    return t


TEMPLATES = {"wreck_skiff": wreck_skiff, "wreck_cog": wreck_cog, "wreck_mast": wreck_mast,
             "ruin_colonnade": ruin_colonnade, "ruin_shrine": ruin_shrine}


# ------------------------------------------------------------------------------------------------------------ NBT
def template_bytes(t):
    blocks = t.b
    assert min(min(p) for p in blocks) >= 0, "negative position"
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]
    palette, index, rows = [], {}, []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        name, props, nbt = blocks[pos]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": (TAG_STRING, name)}
            if props:
                entry["Properties"] = (TAG_COMPOUND, compound(props))
            palette.append(entry)
        row = {"pos": (TAG_LIST, (TAG_INT, list(pos))), "state": (TAG_INT, index[key])}
        if nbt:
            row["nbt"] = (TAG_COMPOUND, compound(nbt))
        rows.append(row)
    root = {
        "DataVersion": (TAG_INT, DATA_VERSION),
        "size": (TAG_LIST, (TAG_INT, size)),
        "palette": (TAG_LIST, (TAG_COMPOUND, palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, rows)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _str(raw, "")
    _payload(raw, TAG_COMPOUND, root)
    return gzip.compress(raw.getvalue(), mtime=0), len(rows), len(palette), size


def registry_blocks():
    """Block ids of the newest dev/data/baseline-*/registries.json.gz, or None."""
    found = sorted(glob.glob(os.path.join(DEV, "data", "baseline-*", "registries.json.gz")))
    return set(json.load(gzip.open(found[-1]))["block"]) if found else None


def main():
    check = "--check" in sys.argv
    reg, bad = registry_blocks(), []
    for name, fn in TEMPLATES.items():
        t = fn()
        if reg is not None:
            bad += [f"{name}: {b} not in the block registry" for b in sorted({v[0] for v in t.b.values()} - reg)]
        data, n, p, size = template_bytes(t)
        out = os.path.join(OUT_DIR, f"{name}.nbt")
        rel = os.path.relpath(out, REPO).replace(os.sep, "/")
        if check:
            cur = open(out, "rb").read() if os.path.exists(out) else b""
            if not cur or gzip.decompress(cur) != gzip.decompress(data):
                bad.append(f"{rel} is out of date")
            else:
                print(f"{rel} up to date ({n} blocks, {p} states, size {size})")
            continue
        os.makedirs(OUT_DIR, exist_ok=True)
        with open(out, "wb") as f:
            f.write(data)
        print(f"wrote {rel}: {n} blocks, {p} states, size {size}")
    for line in bad:
        print(line)
    if bad:
        sys.exit(1)
    if check:
        print("PASS")


if __name__ == "__main__":
    main()
