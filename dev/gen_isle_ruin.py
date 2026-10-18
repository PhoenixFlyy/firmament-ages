#!/usr/bin/env python3
"""Firmament Ages - write the structure template of the isle ruin (G3, Felix I1B; SPEC 30.13).

One small ruin per Drifting Isle cluster, placed by code (firmages-core `isles/IsleRuin`), so the template ships in the
mod jar: mod/firmages-core/src/main/resources/data/firmages/structure/isle_ruin.nbt, 7 x 7 x 7 blocks.
  y 0      skystone floor of the crystal chamber
  y 1..2   the chamber: skystone walls, three firmament crystal clusters on the floor (the bonus)
  y 3      the isle's top surface (IsleRuin.CORE_Y): skystone with the 3 x 3 sealed lid in the middle
  y 4..6   the ruin: four Wind Seals on the compass points (each faces outward, the north one is worn by the wind:
           first=true), broken skystone pillars in the corners, a spruce chest (firmages:chests/isle_ruin); the rest is
           air, so the top's one-block dome does not cut through the ruin
Only Dawn blocks and the ruin's own blocks (skystone Dawn; seal, lid and crystal cluster in age_blocks/age_4 like the
other isle crystal blocks). The rotation per isle comes from IsleRuin.rotation; FACING turns with it, so the order
"from the worn seal, clockwise round the compass" holds in every rotation.

Usage
  python dev/gen_isle_ruin.py           write the template
  python dev/gen_isle_ruin.py --check   exit 1 if the file on disk differs
"""
import gzip
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_cold_forge as nbt  # noqa: E402  (the NBT writer)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "data", "firmages", "structure", "isle_ruin.nbt")
SIZE = (7, 7, 7)
LOOT = "firmages:chests/isle_ruin"

SKY = "firmages:skystone"
LID = "firmages:wind_seal_lid"
SEAL = "firmages:wind_seal"
CLUSTER = "firmages:firmament_crystal_cluster"
CHEST = "tfc:wood/chest/spruce"
AIR = "minecraft:air"


def layout():
    b = {}
    sx, sy, sz = SIZE
    for x in range(sx):
        for z in range(sz):
            ring = x in (0, sx - 1) or z in (0, sz - 1)
            b[(x, 0, z)] = (SKY, {}, None)
            for y in (1, 2):
                b[(x, y, z)] = (SKY if ring else AIR, {}, None)
            b[(x, 3, z)] = (LID if 2 <= x <= 4 and 2 <= z <= 4 else SKY, {}, None)
            for y in (4, 5, 6):
                b[(x, y, z)] = (AIR, {}, None)
    for x, z in ((1, 1), (4, 2), (2, 4)):
        b[(x, 1, z)] = (CLUSTER, {"facing": "up", "waterlogged": "false"}, None)
    # the seals: north (z 0), east (x 6), south (z 6), west (x 0); the north one is worn by the wind
    for (x, z), facing in (((3, 0), "north"), ((6, 3), "east"), ((3, 6), "south"), ((0, 3), "west")):
        b[(x, 4, z)] = (SEAL, {"facing": facing, "first": "true" if facing == "north" else "false", "lit": "false"}, None)
    # broken pillars in the corners
    for (x, z), h in (((0, 0), 3), ((6, 0), 1), ((0, 6), 2), ((6, 6), 0)):
        for y in range(4, 4 + h):
            b[(x, y, z)] = (SKY, {}, None)
    b[(5, 4, 1)] = (CHEST, {"facing": "west", "type": "single", "waterlogged": "false"}, {"id": "tfc:chest", "LootTable": LOOT})
    return b


def template_bytes():
    nbt.SIZE = SIZE
    nbt.layout = layout
    return nbt.template_bytes()


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
