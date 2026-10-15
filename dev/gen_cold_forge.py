#!/usr/bin/env python3
"""Firmament Ages - write the structure template of the Cold Forge (kubejs/data/firmages/structure/cold_forge.nbt).

The Cold Forge is the Iron Age expedition (dev/decisions-while-away.md, 2026-10-14): the ruin of an older smithy in
the TFC hills, 11 x 6 x 9 blocks of granite. Collapsed brick walls, a hearth with a chimney stump, a stone anvil and
one chest with the loot table firmages:chests/cold_forge (the Frontier Shard). The worldgen files next to it:
  kubejs/data/firmages/worldgen/structure/cold_forge.json         jigsaw, surface, beard_thin, sunk one block
  kubejs/data/firmages/worldgen/structure_set/cold_forge.json     random spread, spacing 32 chunks, separation 10
  kubejs/data/firmages/worldgen/template_pool/cold_forge.json     this template with the processor list below
  kubejs/data/firmages/worldgen/processor_list/cold_forge.json    cracked and mossy bricks, mossy cobble, fewer loose rocks
  kubejs/data/firmages/tags/worldgen/biome/has_structure/cold_forge.json   TFC hills and highlands (as TFC Ruins)
  kubejs/data/firmages/tags/worldgen/structure/cold_forge.json    the tag the Frontier Compass searches
The weathering comes from the processor list, so this template stays plain and the ruin differs at every site.

Usage
  python dev/gen_cold_forge.py           write the template
  python dev/gen_cold_forge.py --check   exit 1 if the file on disk differs
"""
import gzip
import io
import os
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "kubejs", "data", "firmages", "structure", "cold_forge.nbt")
DATA_VERSION = 3955   # Minecraft 1.21.1
SIZE = (11, 6, 9)     # x, y, z; y 0 is the floor, one block below the surface (start_height -1)
LOOT = "firmages:chests/cold_forge"

ROCK = "granite"
BRICKS = f"tfc:rock/bricks/{ROCK}"
COBBLE = f"tfc:rock/cobble/{ROCK}"
SMOOTH = f"tfc:rock/smooth/{ROCK}"
GRAVEL = f"tfc:rock/gravel/{ROCK}"
LOOSE = f"tfc:rock/loose/{ROCK}"
ANVIL = f"tfc:rock/anvil/{ROCK}"
SLAB = f"tfc:rock/bricks/{ROCK}_slab"
CHEST = "tfc:wood/chest/oak"
CHARCOAL = "tfc:charcoal_pile"
BONE = "tfc:groundcover/bone"
AIR = "minecraft:air"

# Wall height per perimeter cell (x, z) -> top y of the wall; the ruin is higher at the back (the hearth wall, z = 8)
# and falls away towards the open front (z = 0, door gap at x 4..6).
WALL_TOP = {}
for x in range(SIZE[0]):
    WALL_TOP[(x, 8)] = 4 if 2 <= x <= 8 else 3
    WALL_TOP[(x, 0)] = 0 if 4 <= x <= 6 else (2 if x in (0, 1, 9, 10) else 1)
for z in range(1, 8):
    WALL_TOP[(0, z)] = 3 if z >= 5 else (2 if z >= 3 else 1)
    WALL_TOP[(10, z)] = 2 if z >= 6 else (1 if z in (2, 3, 5) else 0)


def layout():
    """{(x, y, z): (block, properties, nbt or None)}; positions not listed keep the terrain."""
    b = {}
    sx, sy, sz = SIZE
    for x in range(sx):
        for z in range(sz):
            # floor: smooth slabs worn into cobble and gravel
            pick = (x * 7 + z * 3) % 5
            b[(x, 0, z)] = ((SMOOTH, COBBLE, SMOOTH, GRAVEL, COBBLE)[pick], {}, None)
            # clear the inside of the ruin up to the top
            for y in range(1, sy):
                b[(x, y, z)] = (AIR, {}, None)
    for (x, z), top in WALL_TOP.items():
        for y in range(1, top + 1):
            b[(x, y, z)] = (BRICKS if (x + y + z) % 4 else COBBLE, {}, None)
        if top == 0 and not (z == 0 and 4 <= x <= 6):
            b[(x, 1, z)] = (LOOSE, {}, None)       # rubble where the wall has fallen
    # the hearth against the back wall: a brick ring with old charcoal, a chimney stump above it
    for x in (4, 6):
        for y in (1, 2):
            b[(x, y, 7)] = (BRICKS, {}, None)
    b[(5, 1, 7)] = (CHARCOAL, {"layers": "2"}, None)
    b[(5, 2, 7)] = (AIR, {}, None)
    for x in (4, 5, 6):
        b[(x, 3, 7)] = (BRICKS, {}, None)
    for y in (4, 5):
        b[(5, y, 7)] = (BRICKS, {}, None)
        b[(5, y, 8)] = (BRICKS, {}, None)
    # the anvil in the middle, a slab bench along the left wall, the chest in the back corner
    b[(5, 1, 4)] = (ANVIL, {}, None)
    for z in (3, 4, 5):
        b[(1, 1, z)] = (SLAB, {"type": "bottom"}, None)
    b[(8, 1, 6)] = (CHEST, {"facing": "west", "type": "single"}, {"id": "tfc:chest", "LootTable": LOOT})
    # loose rocks and old bones on the floor
    for x, z in ((2, 2), (7, 2), (3, 6), (8, 3), (6, 5), (2, 6)):
        b[(x, 1, z)] = (LOOSE, {}, None)
    for x, z in ((7, 4), (3, 2)):
        b[(x, 1, z)] = (BONE, {}, None)
    return b


# ---------------------------------------------------------------------------------------------------- NBT writer
TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _str(out, s):
    data = s.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _payload(out, tag, v):
    if tag == TAG_INT:
        out.write(struct.pack(">i", v))
    elif tag == TAG_STRING:
        _str(out, v)
    elif tag == TAG_LIST:
        etag, items = v
        out.write(struct.pack(">bi", etag if items else TAG_END, len(items)))
        for it in items:
            _payload(out, etag, it)
    elif tag == TAG_COMPOUND:
        for k, (t, val) in v.items():
            out.write(struct.pack(">b", t))
            _str(out, k)
            _payload(out, t, val)
        out.write(struct.pack(">b", TAG_END))


def compound(d):
    """Plain dict -> typed compound: int -> TAG_Int, str -> TAG_String, dict -> TAG_Compound."""
    out = {}
    for k, v in d.items():
        if isinstance(v, bool) or not isinstance(v, (int, str, dict)):
            raise TypeError(f"{k}: unsupported value {v!r}")
        out[k] = (TAG_INT, v) if isinstance(v, int) else (TAG_STRING, v) if isinstance(v, str) else (TAG_COMPOUND, compound(v))
    return out


def template_bytes():
    blocks = layout()
    palette, index = [], {}
    rows = []
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
        "size": (TAG_LIST, (TAG_INT, list(SIZE))),
        "palette": (TAG_LIST, (TAG_COMPOUND, palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, rows)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _str(raw, "")
    _payload(raw, TAG_COMPOUND, root)
    return gzip.compress(raw.getvalue(), mtime=0), len(rows), len(palette)


def main():
    data, n, p = template_bytes()
    if "--check" in sys.argv:
        cur = open(OUT, "rb").read() if os.path.exists(OUT) else b""
        if not cur or gzip.decompress(cur) != gzip.decompress(data):
            print(f"{os.path.relpath(OUT, REPO)} is out of date")
            sys.exit(1)
        print(f"{os.path.relpath(OUT, REPO)} up to date ({n} blocks, {p} states)")
        return
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)
    print(f"wrote {os.path.relpath(OUT, REPO)}: {n} blocks, {p} states, size {SIZE}")


if __name__ == "__main__":
    main()
