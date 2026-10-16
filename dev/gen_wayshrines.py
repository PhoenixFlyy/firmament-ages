#!/usr/bin/env python3
"""Firmament Ages - write the Wayshrines of firmages-core (SPEC-himmel §28) from one layout per type.

Each type has one layout: a map of cells (x, y, z) -> pattern key. The restored form (the Modonomicon multiblock the
fellowship rebuilds) and the ruin (the structure template that generates in the world) both come from that layout, so
they cannot drift: the ruin is the layout with a fixed list of cells taken away (fallen, stolen, broken) and some
rubble on the floor. The light ("L") is always missing in a ruin, so a ruin never validates.

Coordinates: x west to east, y bottom to top, z north to south. The core ("0") sits in the centre of the bottom layer,
facing north in the template; the bottom layer is the floor, one block below the surface (jigsaw start_height -1).

Written into mod/firmages-core/src/main/resources/data/firmages/ (the mod jar, overridable from kubejs/data/firmages/):
  structure/wayshrine/<type>.nbt                         the ruin (all eight types, the Wind Shrine included)
  worldgen/structure/wayshrine/<type>.json               jigsaw, size 1, surface (seven types, not the Wind Shrine)
  worldgen/structure_set/wayshrine/<type>.json           TFC climate placement (tfc:climate), spacing 72, separation 27
  worldgen/template_pool/wayshrine/<type>.json           the ruin with the processor list (all eight)
  worldgen/processor_list/wayshrine.json                 cracked and mossy bricks, block_rot on #firmages:wayshrine/rottable
  tags/worldgen/structure/wayshrine.json                 the seven structures (/locate, the pointer)
  tags/worldgen/biome/has_structure/wayshrine/<type>.json  TFC biomes per type
  tags/block/wayshrine/...                               role tags, lights, any_rock_bricks, rottable
  modonomicon/multiblocks/wayshrine_<type>.json          the restored form (modonomicon:dense, "0" core, "L" light)
  firmages_wayshrine/type/<type>.json                    type data (structure, multiblock, min_age, light_key, ruin_beam)
The worldgen files carry the NeoForge condition "tfc is loaded": they name TFC blocks and the tfc:climate placement,
and the GameTest run has no TFC. The Wind Shrine (S6B, Felix 2026-10-15: it replaces the Glacier Cairn) has no
structure of its own: the Drifting Isles (M13) place its template through WayshrinePlacement.placeIsleShrine.

GameTest stand-in (vanilla blocks) into mod/firmages-core/src/gametest/resources/:
  data/firmages/structure/wayshrine/stand_in.nbt, stand_in_restored.nbt (restored cells without the core)
  data/firmages/modonomicon/multiblocks/wayshrine_stand_in.json
  data/firmages_test/firmages_wayshrine/type/stand_in.json

Usage
  python dev/gen_wayshrines.py           write every file
  python dev/gen_wayshrines.py --check   exit 1 if a file on disk differs or a layout breaks a rule (among them: every
                                         block of a ruin template is a Dawn block in dev/age_map.toml)
"""
import gzip
import json
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_cold_forge import TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_STRING, _payload, _str, compound  # noqa: E402

import io  # noqa: E402
import struct  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(REPO, "mod", "firmages-core", "src")
DATA = os.path.join(MOD, "main", "resources", "data", "firmages")
TEST_DATA = os.path.join(MOD, "gametest", "resources", "data")
DATA_VERSION = 3955  # Minecraft 1.21.1
MAX_SIZE = (11, 8, 11)
TFC_LOADED = [{"type": "neoforge:mod_loaded", "modid": "tfc"}]

ROCKS = ["granite", "diorite", "gabbro", "shale", "claystone", "limestone", "conglomerate", "dolomite", "chert", "chalk",
         "rhyolite", "basalt", "andesite", "dacite", "quartzite", "slate", "phyllite", "schist", "gneiss", "marble", "tuff"]
SOILS = ["entisol", "mollisol", "alfisol", "andisol", "podzol", "oxisol", "fluvisol", "aridisol"]
SANDS = ["black", "brown", "yellow", "white", "red", "pink", "green"]
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
          "blue", "brown", "green", "red", "black"]
LAMP_METALS = ["copper", "bronze", "bismuth_bronze", "black_bronze", "wrought_iron", "steel", "black_steel", "red_steel",
               "blue_steel"]


def opt(i):
    """A tag entry that may be absent (TFC, IE): never fails a tag load without that mod."""
    return i if i.startswith("#minecraft:") or i.startswith("minecraft:") else {"id": i, "required": False}


# ---------------------------------------------------------------------------------------------------- shared tags
SHARED_TAGS = {
    "lights": ["#minecraft:candles", "tfc:candle"] + [f"tfc:candle/{c}" for c in COLORS]
              + [f"tfc:metal/lamp/{m}" for m in LAMP_METALS] + ["immersiveengineering:electric_lantern"],
    "any_rock_bricks": [f"tfc:rock/{k}/{r}" for r in ROCKS for k in ("bricks", "cracked_bricks", "mossy_bricks")]
                       + ["minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks"],
    "rottable": [f"tfc:rock/loose/{r}" for r in ROCKS] + [f"tfc:rock/gravel/{r}" for r in ROCKS],
}
SMOOTH_ROCK = [f"tfc:rock/smooth/{r}" for r in ROCKS]
RAW_ROCK = [f"tfc:rock/raw/{r}" for r in ROCKS]
GLASS = ["#c:glass_blocks", "tfc:poured_glass"] + [f"tfc:{c}_poured_glass" for c in COLORS]
LOGS = ["#minecraft:logs"]
ROCK_BRICKS = ["#firmages:wayshrine/any_rock_bricks"]


# Biomes per type: the SPEC-himmel 28.4 base biomes first, then their TFC 4.2.11 variants (the dolines, cenotes, burren,
# shilin and tower karst families, glacial and volcanic variants). With the base biomes alone the 0.10.0 copy boot found
# no Wayshrine within 4,096 blocks of spawn: TFC 4.2 splits most landscapes into variants, and the variants cover most
# of the land. The tfc:climate placement stays the real filter. No type uses min_elevation: ClimateStructurePlacement tests
# the climate at y 0 of the chunk (javap TFC 4.2.11), so an elevation bound never passes; the mountain biomes carry the
# height of the Mountain Spire instead. All ids are in data/tfc/worldgen/biome of TFC 4.2.11.
# Density (SPEC-himmel 28.4 says spacing 128, separation 48; acceptance 2 wants 7 to 11 Wayshrines within 4,000 blocks
# of spawn [PoC]). Three fresh seeds of the 0.10.0 copy boot at spacing 128 had 1, 1 and 4 within 4,096 blocks: about
# 2.8 % of the candidate chunks pass climate and biome, and the hot types (Desert Arch, Reef Altar) lie beyond the
# spawn band by design. Spacing 64 gave 5, 1 and 4 on three more seeds; the climate bands of the near types are wider
# since: Forest Ring from edge forest (min_forest 2, was 3), Steppe Stones up to 220 mm groundwater and edge forest
# (was 150 and sparse), Swamp Altar from 300 mm (was 350) and also in wet plains and river valleys (TFC has no swamp
# biome; its swamps are wet plains and lowlands). With those bands spacing 64 gave 11, 11 and 20 on three seeds, so
# the spacing was 80: (64 / 80)^2 of that is about 9 per seed. Round C2 measured acceptance 2 with
# `/firmages wayshrine census 4000 [spacing separation]` on 20 fresh seeds (1001 to 1020; dev/poc-results.md):
# 80/30 gave 7 of 20 seeds in 7 to 11 (mean 7.0, three below 4), 64/24 8 (mean 10.9), 72/27 11 (mean 10.0, none
# below 5, at most 16), 96/36 6, 112/42 2. No spacing reaches 16 of 20: the count swings with the spawn's climate.
# Round H3 measured every type at ten spacings (48/18 to 128/48) on the same 20 seeds and on 10 more (1021 to 1030):
# with one spacing for all, 72/27 had 14 of 30 seeds in 7 to 11 (3 below 5, 9 above 11); per type, the best on all 30
# is below (21 of 30, 1 below 5, 4 above 11; on the 10 new seeds alone 5 of 10, none below 5). A shared 16 of 20 is
# out of reach for independent random-spread sets: the count of each type is about Poisson-distributed and swings with
# the spawn's climate (dev/poc-results.md, round H3). Desert Arch and Reef Altar never fell within 4,000 blocks and
# keep 72/27.
SPACING, SEPARATION = 72, 27
TYPE_SPACING = {"forest_ring": (72, 27), "mountain_spire": (80, 30), "steppe_stones": (80, 30), "swamp_altar": (80, 30),
                "volcanic_ring": (48, 18)}
FOREST_RING_BIOMES = ["plains", "hills", "rolling_hills", "lowlands",
                      "doline_plains", "doline_hills", "doline_rolling_hills", "cenote_plains", "cenote_hills",
                      "cenote_rolling_hills", "burren_plains", "shilin_plains", "shilin_hills", "tower_karst_plains",
                      "tower_karst_hills", "drumlins", "knob_and_kettle"]
STEPPE_STONES_BIOMES = ["plains", "rolling_hills", "plateau",
                        "plateau_wide", "rocky_plateau", "doline_plains", "doline_rolling_hills", "doline_plateau",
                        "cenote_plains", "cenote_rolling_hills", "cenote_plateau", "burren_plains", "burren_plateau",
                        "shilin_plains", "shilin_plateau", "patterned_ground", "inverted_patterned_ground",
                        "stone_circles", "terrace_lower", "terrace_upper"]
SWAMP_ALTAR_BIOMES = ["lowlands", "salt_marsh", "mud_flats", "tidal_flats", "knob_and_kettle", "plains", "river_valley"]
DESERT_ARCH_BIOMES = ["dune_sea", "grassy_dunes", "badlands", "mesas", "buttes",
                      "coastal_dunes", "burren_badlands", "burren_badlands_tall", "hoodoos", "salt_flats",
                      "stair_step_canyons", "whorled_canyons"]
MOUNTAIN_SPIRE_BIOMES = ["mountains", "old_mountains", "highlands",
                         "collisional_mountains", "oceanic_mountains", "extreme_doline_mountains", "doline_highlands",
                         "cenote_highlands", "shilin_highlands", "tower_karst_highlands", "glacially_carved_mountains",
                         "glaciated_mountains"]
REEF_ALTAR_BIOMES = ["ocean_reef", "ocean_atolls", "deep_ocean_atolls"]
VOLCANIC_RING_BIOMES = ["volcanic_mountains", "volcanic_island", "volcanic_oceanic_mountains",
                        "volcanic_mountain_islands", "oceanic_volcanic_arc", "tuyas", "active_shield_volcano",
                        "dormant_shield_volcano", "extinct_shield_volcano", "ancient_shield_volcano",
                        "glacially_carved_volcanic_mountains", "glaciated_volcanic_mountains",
                        "glacially_carved_volcanic_oceanic_mountains", "glaciated_volcanic_oceanic_mountains"]


class Role:
    """One pattern key: its tag (role name inside the type) and the block the templates use. `ruin` is a Dawn stand-in
    the ruin template keeps in this role's cells instead of `block` (the restored form still asks for the role)."""

    def __init__(self, name, values, block, props=None, bars=False, ruin=None):
        self.name, self.values, self.block, self.props, self.bars = name, values, block, props or {}, bars
        self.ruin = ruin


class Kind:
    def __init__(self, tid, name, min_age, beam, size, rock, roles, cells, missing, rubble, biomes=None, climate=None,
                 terrain="beard_thin", heightmap="WORLD_SURFACE_WG", water=False, worldgen=True, test=False):
        self.tid, self.name, self.min_age, self.beam, self.size, self.rock = tid, name, min_age, beam, size, rock
        self.roles, self.cells, self.missing, self.rubble = roles, cells, missing, rubble
        self.biomes, self.climate, self.terrain, self.heightmap = biomes or [], climate or {}, terrain, heightmap
        self.water, self.worldgen, self.test = water, worldgen, test

    @property
    def core(self):
        return (self.size[0] // 2, 0, self.size[2] // 2)


def ring(cx, cz, r):
    """The eight points of a ring of radius r: the four sides and the four diagonals."""
    d = max(1, round(r * 0.7))
    return [(cx + r, cz), (cx - r, cz), (cx, cz + r), (cx, cz - r), (cx + d, cz + d), (cx + d, cz - d), (cx - d, cz + d),
            (cx - d, cz - d)]


def square(cx, cz, r):
    return [(cx + dx, cz + dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1)]


def column(cells, x, z, y0, y1, key):
    for y in range(y0, y1 + 1):
        cells[(x, y, z)] = key


def base(size):
    c = (size[0] // 2, 0, size[2] // 2)
    return {c: "0"}, c[0], c[2]


# ---------------------------------------------------------------------------------------------------- the eight types
def forest_ring():
    size = (9, 4, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 2):
        if (x - cx) ** 2 + (z - cz) ** 2 <= 5 and (x, 0, z) not in cells:
            cells[(x, 0, z)] = "F"
    for (x, z) in ring(cx, cz, 3):
        column(cells, x, z, 0, 2, "W")
        cells[(x, 3, z)] = "T"
    cells[(cx, 1, cz - 1)] = "A"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx + 2, 2, cz + 2): None, (cx + 2, 3, cz + 2): None, (cx - 3, 2, cz): "loose", (cx - 3, 3, cz): None,
               (cx + 3, 3, cz): None, (cx + 1, 0, cz + 1): "gravel", (cx - 1, 0, cz - 2): "gravel"}
    rubble = [(cx + 1, 1, cz + 3, "loose"), (cx - 2, 1, cz + 1, "loose"), (cx + 3, 1, cz - 2, "log_x")]
    roles = {"F": Role("floor", ROCK_BRICKS, "tfc:rock/bricks/granite"), "A": Role("altar", ROCK_BRICKS, "tfc:rock/bricks/granite"),
             "W": Role("post", LOGS, "tfc:wood/log/oak", {"axis": "y"}),
             "T": Role("thatch", ["tfc:thatch", "minecraft:hay_block"], "tfc:thatch")}
    return Kind("forest_ring", "Forest Ring", "age_1", "#9FD38A", size, "granite", roles, cells, missing, rubble,
                biomes=FOREST_RING_BIOMES,
                climate={"min_temperature": 4.0, "max_temperature": 22.0, "min_forest": 2})


def steppe_stones():
    size = (9, 5, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1) + [(cx + 2, cz), (cx - 2, cz), (cx, cz + 2), (cx, cz - 2)]:
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "F"
    for dx, dz in ((3, 3), (3, -3), (-3, 3), (-3, -3)):
        column(cells, cx + dx, cz + dz, 0, 3, "P")
    cells[(cx, 1, cz - 1)] = "Z"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx, 1, cz - 1): None, (cx + 3, 3, cz - 3): None, (cx + 3, 2, cz - 3): "loose", (cx - 3, 3, cz + 3): None,
               (cx + 2, 0, cz): "gravel", (cx - 1, 0, cz + 1): "gravel"}
    rubble = [(cx + 3, 1, cz - 2, "loose"), (cx - 2, 1, cz + 3, "loose"), (cx + 1, 1, cz + 2, "loose")]
    roles = {"F": Role("floor", ROCK_BRICKS, "tfc:rock/bricks/andesite"),
             "P": Role("stone", RAW_ROCK + ["minecraft:stone"], "tfc:rock/raw/andesite"),
             "Z": Role("bronze", ["#c:storage_blocks/bronze", "tfc:metal/block/bronze", "tfc:metal/block/bismuth_bronze",
                                  "tfc:metal/block/black_bronze"], "tfc:metal/block/bronze")}
    return Kind("steppe_stones", "Steppe Stones", "age_1", "#E8D7A0", size, "andesite", roles, cells, missing, rubble,
                biomes=STEPPE_STONES_BIOMES, climate={"max_groundwater": 220.0, "max_forest": 2})


def swamp_altar():
    size = (9, 4, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 2):
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "M"
    for dx, dz in ((2, 2), (2, -2), (-2, 2), (-2, -2)):
        column(cells, cx + dx, cz + dz, 1, 3, "W")
    for dx in (2, -2):
        for dz in (-1, 0, 1):
            cells[(cx + dx, 1, cz + dz)] = "I"
    cells[(cx, 1, cz - 1)] = "M"
    cells[(cx, 2, cz - 1)] = "L"
    # every bars cell is gone from the ruin (stolen): iron bars are an Iron Age block and the ruin stands from Dawn
    missing = {(cx + 2, 1, cz - 1): None, (cx + 2, 1, cz): None, (cx + 2, 1, cz + 1): None, (cx - 2, 1, cz + 1): None,
               (cx - 2, 1, cz): None, (cx - 2, 1, cz - 1): None, (cx - 2, 3, cz - 2): None, (cx - 2, 2, cz - 2): None,
               (cx + 1, 0, cz + 2): None}
    rubble = [(cx + 3, 0, cz + 1, "log_z"), (cx - 3, 0, cz - 1, "log_z")]
    roles = {"M": Role("mud", [f"tfc:mud_bricks/{s}" for s in SOILS], "tfc:mud_bricks/alfisol"),
             "W": Role("post", LOGS, "tfc:wood/log/oak", {"axis": "y"}),
             "I": Role("bars", ["tfc:metal/bars/wrought_iron", "tfc:metal/bars/steel", "tfc:metal/bars/black_steel",
                                "tfc:metal/bars/red_steel", "tfc:metal/bars/blue_steel", "minecraft:iron_bars"],
                       "tfc:metal/bars/wrought_iron", bars=True)}
    return Kind("swamp_altar", "Swamp Altar", "age_2", "#7FA88A", size, "shale", roles, cells, missing, rubble,
                biomes=SWAMP_ALTAR_BIOMES, climate={"min_groundwater": 300.0}, terrain="beard_box")


def desert_arch():
    size = (9, 6, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1) + [(cx + 2, cz), (cx - 2, cz), (cx, cz + 2), (cx, cz - 2)]:
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "S"
    for dx in (3, -3):
        column(cells, cx + dx, cz, 0, 4, "P")
    for dx in range(-3, 4):
        cells[(cx + dx, 5, cz)] = "S"
    cells[(cx, 1, cz - 1)] = "C"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx, 1, cz - 1): None, (cx - 1, 5, cz): None, (cx, 5, cz): None, (cx + 1, 5, cz): None,
               (cx - 3, 4, cz): None, (cx + 1, 0, cz - 1): "sand"}
    rubble = [(cx - 1, 1, cz + 1, "sandstone"), (cx + 1, 1, cz + 2, "sandstone"), (cx + 2, 1, cz - 2, "sandstone")]
    roles = {"S": Role("sandstone", [f"tfc:{k}/{s}" for k in ("raw_sandstone", "smooth_sandstone", "cut_sandstone") for s in SANDS]
                       + ["#c:sandstone/blocks"], "tfc:raw_sandstone/yellow"),
             "P": Role("pillar", [f"tfc:{k}/{s}" for k in ("smooth_sandstone", "cut_sandstone") for s in SANDS],
                       "tfc:smooth_sandstone/yellow"),
             "C": Role("copper", ["#c:storage_blocks/copper", "tfc:metal/block/copper"], "tfc:metal/block/copper")}
    return Kind("desert_arch", "Desert Arch", "age_2", "#F2C46D", size, "sandstone", roles, cells, missing, rubble,
                biomes=DESERT_ARCH_BIOMES,
                climate={"max_groundwater": 100.0, "min_temperature": 18.0})


def mountain_spire():
    size = (7, 7, 7)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1) + [(cx + 2, cz), (cx - 2, cz), (cx, cz + 2)]:
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "S"
    column(cells, cx, cz - 2, 0, 6, "B")
    for dx in (1, -1):
        for y in (2, 3):
            cells[(cx + dx, y, cz - 2)] = "I"
    for dx, dz in ((2, 2), (-2, 2), (2, -1), (-2, -1)):
        cells[(cx + dx, 1, cz + dz)] = "S"
    cells[(cx, 1, cz - 1)] = "B"
    cells[(cx, 2, cz - 1)] = "L"
    # every bars cell is gone from the ruin (stolen): iron bars are an Iron Age block and the ruin stands from Dawn
    missing = {(cx, 6, cz - 2): None, (cx, 5, cz - 2): None, (cx, 4, cz - 2): "loose", (cx + 1, 3, cz - 2): None,
               (cx + 1, 2, cz - 2): None, (cx - 1, 3, cz - 2): None, (cx - 1, 2, cz - 2): None, (cx - 2, 1, cz + 2): None}
    rubble = [(cx + 2, 1, cz + 1, "loose"), (cx - 1, 1, cz + 2, "loose"), (cx + 1, 1, cz - 1, "loose")]
    roles = {"S": Role("smooth", SMOOTH_ROCK, "tfc:rock/smooth/slate"), "B": Role("bricks", ROCK_BRICKS, "tfc:rock/bricks/slate"),
             "I": Role("bars", ["tfc:metal/bars/wrought_iron", "tfc:metal/bars/steel", "tfc:metal/bars/black_steel",
                                "tfc:metal/bars/red_steel", "tfc:metal/bars/blue_steel", "minecraft:iron_bars"],
                       "tfc:metal/bars/wrought_iron", bars=True)}
    return Kind("mountain_spire", "Mountain Spire", "age_3", "#C8D6E8", size, "slate", roles, cells, missing, rubble,
                biomes=MOUNTAIN_SPIRE_BIOMES, terrain="beard_box")


def wind_shrine():
    size = (9, 5, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 2):
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "B"
    for dx, dz in ((2, 2), (2, -2), (-2, 2), (-2, -2)):
        column(cells, cx + dx, cz + dz, 1, 3, "P")
        cells[(cx + dx, 4, cz + dz)] = "G"
    for dx in (-1, 0, 1):
        for y in (1, 2):
            cells[(cx + dx, y, cz + 2)] = "I"
    cells[(cx, 1, cz - 1)] = "P"
    cells[(cx, 2, cz - 1)] = "L"
    # every bars cell is gone from the ruin (stolen): the isles can be seen and climbed to from Dawn, steel bars are Iron Age
    missing = {(cx + 2, 4, cz - 2): None, (cx - 2, 4, cz + 2): None, (cx - 1, 2, cz + 2): None, (cx, 2, cz + 2): None,
               (cx + 1, 1, cz + 2): None, (cx - 1, 1, cz + 2): None, (cx, 1, cz + 2): None, (cx + 1, 2, cz + 2): None,
               (cx - 2, 3, cz - 2): None}
    rubble = [(cx + 3, 1, cz + 3, "loose"), (cx - 3, 1, cz, "loose")]
    body = ["firmages:skystone"] + SMOOTH_ROCK + ROCK_BRICKS
    roles = {"B": Role("body", body, "tfc:rock/smooth/marble"), "P": Role("pillar", ["firmages:skystone"] + SMOOTH_ROCK, "tfc:rock/smooth/marble"),
             "G": Role("glass", GLASS, "tfc:poured_glass"),
             "I": Role("bars", ["tfc:metal/bars/steel", "tfc:metal/bars/black_steel", "tfc:metal/bars/red_steel",
                                "tfc:metal/bars/blue_steel"], "tfc:metal/bars/steel", bars=True)}
    return Kind("wind_shrine", "Wind Shrine", "age_4", "#D8E6F0", size, "marble", roles, cells, missing, rubble, worldgen=False)


def reef_altar():
    size = (9, 3, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1) + [(cx + 2, cz), (cx - 2, cz), (cx, cz + 2), (cx, cz - 2)]:
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "F"
    for (x, z) in ring(cx, cz, 3):
        column(cells, x, z, 0, 1, "K")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        cells[(cx + dx, 1, cz + dz)] = "G"
    cells[(cx, 1, cz - 1)] = "F"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx + 3, 1, cz): None, (cx - 2, 1, cz - 2): None, (cx, 1, cz + 3): None, (cx + 1, 1, cz + 1): None,
               (cx - 1, 1, cz - 1): None, (cx - 2, 0, cz): "gravel"}
    rubble = [(cx + 1, 1, cz + 2, "loose"), (cx - 2, 1, cz + 1, "loose")]
    roles = {"F": Role("floor", ROCK_BRICKS, "tfc:rock/bricks/limestone"),
             # the ruin's corals died and turned to stone: coral blocks are an Arcane Age block, the ruin stands from Dawn
             "K": Role("coral", ["#minecraft:coral_blocks"], "minecraft:brain_coral_block", ruin="tfc:rock/raw/limestone"),
             "G": Role("glass", GLASS, "tfc:poured_glass")}
    return Kind("reef_altar", "Reef Altar", "age_4", "#6FD6D0", size, "limestone", roles, cells, missing, rubble,
                biomes=REEF_ALTAR_BIOMES, climate={"min_temperature": 20.0}, heightmap="OCEAN_FLOOR_WG", water=True)


def volcanic_ring():
    size = (9, 3, 9)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1):
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "S"
    for (x, z) in square(cx, cz, 3):
        if max(abs(x - cx), abs(z - cz)) == 3:
            cells[(x, 0, z)] = "B"
    for dx, dz in ((3, 3), (3, -3), (-3, 3), (-3, -3)):
        column(cells, cx + dx, cz + dz, 1, 2, "B")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        cells[(cx + dx, 0, cz + dz)] = "M"
    cells[(cx, 1, cz - 1)] = "Z"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx, 1, cz - 1): None, (cx + 1, 0, cz + 3): "loose_bed", (cx - 3, 0, cz): "loose_bed", (cx + 3, 0, cz - 1): None,
               (cx, 0, cz + 2): None, (cx - 3, 2, cz + 3): None}
    rubble = [(cx + 1, 1, cz + 1, "loose"), (cx - 1, 1, cz - 2, "loose")]
    roles = {"S": Role("smooth", ["tfc:rock/smooth/basalt", "minecraft:smooth_basalt"], "tfc:rock/smooth/basalt"),
             "B": Role("bricks", [f"tfc:rock/{k}/basalt" for k in ("bricks", "cracked_bricks", "mossy_bricks")], "tfc:rock/bricks/basalt"),
             "M": Role("magma", [f"tfc:rock/magma/{r}" for r in ("granite", "diorite", "gabbro", "rhyolite", "basalt", "andesite", "dacite")]
                       + ["minecraft:magma_block"], "tfc:rock/magma/basalt"),
             "Z": Role("steel", ["#c:storage_blocks/steel", "tfc:metal/block/steel"], "tfc:metal/block/steel")}
    return Kind("volcanic_ring", "Volcanic Ring", "age_5", "#FF7A3D", size, "basalt", roles, cells, missing, rubble,
                biomes=VOLCANIC_RING_BIOMES, terrain="beard_box")


def stand_in():
    """GameTest stand-in: vanilla blocks only, so the Wayshrine flow runs without TFC."""
    size = (5, 3, 5)
    cells, cx, cz = base(size)
    for (x, z) in square(cx, cz, 1):
        if (x, 0, z) not in cells:
            cells[(x, 0, z)] = "F"
    for dx, dz in ((2, 2), (2, -2), (-2, 2), (-2, -2)):
        cells[(cx + dx, 1, cz + dz)] = "W"
    cells[(cx, 1, cz - 1)] = "F"
    cells[(cx, 2, cz - 1)] = "L"
    missing = {(cx + 2, 1, cz + 2): None, (cx - 2, 1, cz - 2): None, (cx + 1, 0, cz + 1): "vanilla_gravel"}
    roles = {"F": Role("floor", ["minecraft:stone_bricks"], "minecraft:stone_bricks"),
             "W": Role("post", ["minecraft:oak_log"], "minecraft:oak_log", {"axis": "y"})}
    return Kind("stand_in", "Stand-in", "age_1", "#D8E6F0", size, "granite", roles, cells, missing, [], worldgen=False, test=True)


KINDS = [forest_ring(), steppe_stones(), swamp_altar(), desert_arch(), mountain_spire(), wind_shrine(), reef_altar(), volcanic_ring()]
STAND_IN = stand_in()
# The light as the restored templates show it (unlit: lighting it is the rite).
LIGHT_BLOCK = ("tfc:candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
TEST_LIGHT_BLOCK = ("minecraft:candle", {"candles": "1", "lit": "false", "waterlogged": "false"})


def rubble_block(kind, what):
    r = kind.rock
    return {
        "loose": (f"tfc:rock/loose/{r}", {}),
        "loose_bed": (f"tfc:rock/gravel/{r}", {}),
        "gravel": (f"tfc:rock/gravel/{r}", {}),
        "sand": ("tfc:sand/yellow", {}),
        "sandstone": ("tfc:raw_sandstone/yellow_slab", {"type": "bottom", "waterlogged": "false"}),
        "log_x": ("tfc:wood/log/oak", {"axis": "x"}),
        "log_z": ("tfc:wood/log/oak", {"axis": "z"}),
        "vanilla_gravel": ("minecraft:gravel", {}),
    }[what]


# ---------------------------------------------------------------------------------------------------- checks
def check_kind(k):
    errors = []
    sx, sy, sz = k.size
    if sx > MAX_SIZE[0] or sy > MAX_SIZE[1] or sz > MAX_SIZE[2]:
        errors.append(f"{k.tid}: size {k.size} exceeds {MAX_SIZE}")
    cores = [p for p, c in k.cells.items() if c == "0"]
    if cores != [k.core]:
        errors.append(f"{k.tid}: one core in the bottom-layer centre, found {cores}")
    if sy % 2 == 1 and sy // 2 == 0:
        errors.append(f"{k.tid}: the core sits in the exact middle layer")
    if sum(1 for c in k.cells.values() if c == "L") != 1:
        errors.append(f"{k.tid}: exactly one light L")
    for (x, y, z), c in k.cells.items():
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            errors.append(f"{k.tid}: cell {(x, y, z)} outside {k.size}")
        if c not in ("0", "L") and c not in k.roles:
            errors.append(f"{k.tid}: key {c} has no role")
        if c == "L" and y > 0 and (x, y - 1, z) not in k.cells:
            errors.append(f"{k.tid}: the light has nothing below it")
    light = next(p for p, c in k.cells.items() if c == "L")
    if light not in k.missing:
        k.missing[light] = None  # a ruin never keeps its light
    for p in k.missing:
        if p not in k.cells or k.cells[p] == "0":
            errors.append(f"{k.tid}: missing cell {p} is not a pattern cell (or the core)")
    for (x, y, z, what) in k.rubble:
        if (x, y, z) in k.cells:
            errors.append(f"{k.tid}: rubble at pattern cell {(x, y, z)}")
    n = len(k.missing)
    if not k.test and not 6 <= n <= 12:
        errors.append(f"{k.tid}: a ruin misses {n} blocks, want 6 to 12")
    return errors


# ---------------------------------------------------------------------------------------------------- outputs
def bars_props(k, cells, pos):
    """Bars connect to the solid neighbours of the layout (the light and air excepted)."""
    x, y, z = pos
    sides = {"north": (x, y, z - 1), "south": (x, y, z + 1), "west": (x - 1, y, z), "east": (x + 1, y, z)}
    out = {s: ("true" if (cells.get(n) not in (None, "L")) else "false") for s, n in sides.items()}
    out["waterlogged"] = "false"
    return out


def template_block(k, cells, pos, key, ruin=False):
    if key == "0":
        return ("firmages:wayshrine_core", {"facing": "north", "intact": "false", "phase": "ruin"},
                {"id": "firmages:wayshrine_core", "type": k.tid})
    if key == "L":
        return TEST_LIGHT_BLOCK + (None,) if k.test else LIGHT_BLOCK + (None,)
    role = k.roles[key]
    if ruin and role.ruin:
        return (role.ruin, {}, None)
    props = bars_props(k, cells, pos) if role.bars else dict(role.props)
    return (role.block, props, None)


def ruin_blocks(k):
    """The ruin template: the layout minus the missing cells, rubble, air above the floor (not under water)."""
    sx, sy, sz = k.size
    blocks = {}
    if not k.water:
        for x in range(sx):
            for z in range(sz):
                for y in range(1, sy):
                    blocks[(x, y, z)] = ("minecraft:air", {}, None)
    kept = {p: c for p, c in k.cells.items() if p not in k.missing}
    for p, c in kept.items():
        blocks[p] = template_block(k, kept, p, c, ruin=True)
    for p, what in k.missing.items():
        if what is not None:
            name, props = rubble_block(k, what)
            blocks[p] = (name, props, None)
        elif p in blocks and p[1] == 0:
            del blocks[p]  # a missing floor cell keeps the terrain
    for (x, y, z, what) in k.rubble:
        name, props = rubble_block(k, what)
        blocks[(x, y, z)] = (name, props, None)
    return blocks


def restored_blocks(k, with_core=False):
    out = {}
    for p, c in k.cells.items():
        if c == "0" and not with_core:
            continue
        out[p] = template_block(k, k.cells, p, c)
    return out


def nbt_bytes(size, blocks):
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
        "size": (TAG_LIST, (TAG_INT, list(size))),
        "palette": (TAG_LIST, (TAG_COMPOUND, palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, rows)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _str(raw, "")
    _payload(raw, TAG_COMPOUND, root)
    return gzip.compress(raw.getvalue(), mtime=0)


def multiblock(k, ns_tags=True):
    """modonomicon:dense, top layer first; rows are x (west first), characters z (north first): Modonomicon reads
    pattern[layer][x].charAt(z) as Patchouli does, so the multiblock's rotation NONE is the template's (the GameTest
    WayshrineGameTests.restoreAndLight checks every rotation). "_" = any block."""
    sx, sy, sz = k.size
    mapping = {"0": {"type": "modonomicon:block", "block": "firmages:wayshrine_core"},
               "L": {"type": "modonomicon:tag", "tag": "#firmages:wayshrine/lights"}}
    for key, role in sorted(k.roles.items()):
        if k.test:
            mapping[key] = {"type": "modonomicon:block", "block": role.block}
        else:
            mapping[key] = {"type": "modonomicon:tag", "tag": f"#firmages:wayshrine/{k.tid}/{role.name}"}
    pattern = []
    for y in range(sy - 1, -1, -1):
        layer = []
        for x in range(sx):
            layer.append("".join(k.cells.get((x, y, z), "_") for z in range(sz)))
        pattern.append(layer)
    return {"type": "modonomicon:dense", "mapping": mapping, "pattern": pattern}


def salt(tid):
    return zlib.crc32(("firmages:wayshrine/" + tid).encode()) & 0x7FFFFFFF


def worldgen_files(k):
    out = {}
    out[f"worldgen/template_pool/wayshrine/{k.tid}.json"] = {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                               "location": f"firmages:wayshrine/{k.tid}", "processors": "firmages:wayshrine",
                                               "projection": "rigid"}}],
        "neoforge:conditions": TFC_LOADED,
    }
    if not k.worldgen:
        return out
    out[f"worldgen/structure/wayshrine/{k.tid}.json"] = {
        "type": "minecraft:jigsaw",
        "biomes": f"#firmages:has_structure/wayshrine/{k.tid}",
        "step": "surface_structures",
        "spawn_overrides": {},
        "start_pool": f"firmages:wayshrine/{k.tid}",
        "size": 1,
        "terrain_adaptation": k.terrain,
        "start_height": {"absolute": -1},
        "project_start_to_heightmap": k.heightmap,
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
        "liquid_settings": "apply_waterlogging" if k.water else "ignore_waterlogging",
        "neoforge:conditions": TFC_LOADED,
    }
    out[f"worldgen/structure_set/wayshrine/{k.tid}.json"] = {
        "structures": [{"structure": f"firmages:wayshrine/{k.tid}", "weight": 1}],
        "placement": {"type": "tfc:climate", "salt": salt(k.tid), "spacing": TYPE_SPACING.get(k.tid, (SPACING, SEPARATION))[0],
                      "separation": TYPE_SPACING.get(k.tid, (SPACING, SEPARATION))[1], "climate": k.climate},
        "neoforge:conditions": TFC_LOADED,
    }
    out[f"tags/worldgen/biome/has_structure/wayshrine/{k.tid}.json"] = {
        "replace": False, "values": [{"id": f"tfc:{b}", "required": False} for b in k.biomes]}
    return out


def processor_list():
    rules = []
    for r in sorted({k.rock for k in KINDS if k.rock in ROCKS}):
        rules.append(rule(f"tfc:rock/bricks/{r}", 0.25, f"tfc:rock/cracked_bricks/{r}"))
        rules.append(rule(f"tfc:rock/bricks/{r}", 0.12, f"tfc:rock/mossy_bricks/{r}"))
    return {"processors": [
        {"processor_type": "minecraft:rule", "rules": rules},
        {"processor_type": "minecraft:block_rot", "rottable_blocks": "#firmages:wayshrine/rottable", "integrity": 0.6},
    ], "neoforge:conditions": TFC_LOADED}


def rule(block, p, out):
    return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": p},
            "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"Name": out}}


def type_file(k):
    return {"structure": f"firmages:wayshrine/{k.tid}", "multiblock": f"firmages:wayshrine_{k.tid}", "min_age": k.min_age,
            "light_key": "L", "ruin_beam": k.beam}


def all_files():
    """{absolute path: bytes}"""
    files = {}

    def js(path, data):
        files[path] = (json.dumps(data, indent=2) + "\n").encode("utf-8")

    for k in KINDS:
        files[os.path.join(DATA, "structure", "wayshrine", k.tid + ".nbt")] = nbt_bytes(k.size, ruin_blocks(k))
        js(os.path.join(DATA, "modonomicon", "multiblocks", f"wayshrine_{k.tid}.json"), multiblock(k))
        js(os.path.join(DATA, "firmages_wayshrine", "type", k.tid + ".json"), type_file(k))
        for rel, data in worldgen_files(k).items():
            js(os.path.join(DATA, *rel.split("/")), data)
        for role in k.roles.values():
            js(os.path.join(DATA, "tags", "block", "wayshrine", k.tid, role.name + ".json"),
               {"values": [opt(v) for v in role.values]})
    for name, values in SHARED_TAGS.items():
        js(os.path.join(DATA, "tags", "block", "wayshrine", name + ".json"), {"values": [opt(v) for v in values]})
    js(os.path.join(DATA, "worldgen", "processor_list", "wayshrine.json"), processor_list())
    js(os.path.join(DATA, "tags", "worldgen", "structure", "wayshrine.json"),
       {"values": [{"id": f"firmages:wayshrine/{k.tid}", "required": False} for k in KINDS if k.worldgen]})
    s = STAND_IN
    files[os.path.join(TEST_DATA, "firmages", "structure", "wayshrine", "stand_in.nbt")] = nbt_bytes(s.size, ruin_blocks(s))
    files[os.path.join(TEST_DATA, "firmages", "structure", "wayshrine", "stand_in_restored.nbt")] = nbt_bytes(s.size, restored_blocks(s))
    js(os.path.join(TEST_DATA, "firmages", "modonomicon", "multiblocks", "wayshrine_stand_in.json"), multiblock(s))
    js(os.path.join(TEST_DATA, "firmages_test", "firmages_wayshrine", "type", "stand_in.json"), type_file(s))
    return files


def age_errors():
    """Every block of a ruin template must be a Dawn block (review fix 0.12.0): ground ruins stand from Dawn and the
    isle Wind Shrine can be climbed to before the Industrial Age; ProgressiveStages has no structure locks."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_stage_locks as gsl
    am = gsl.AgeMap(gsl.tomllib.loads(open(gsl.AGE_MAP, encoding="utf-8").read()),
                    json.load(open(gsl.REGISTRY, encoding="utf-8")), json.load(open(gsl.TFC_IDS, encoding="utf-8")))
    errors = []
    for k in KINDS:
        for p, (name, _, _) in sorted(ruin_blocks(k).items()):
            if name == "minecraft:air":
                continue
            st, why = am.age_of(name)
            if st != "dawn":
                errors.append(f"{k.tid}: ruin block {name} at {p} is {st} ({why}), want dawn")
    return errors


def same(path, data):
    if not os.path.exists(path):
        return False
    cur = open(path, "rb").read()
    if path.endswith(".nbt"):
        return gzip.decompress(cur) == gzip.decompress(data)
    return cur == data


def main():
    errors = []
    for k in KINDS + [STAND_IN]:
        errors += check_kind(k)
    if len({salt(k.tid) for k in KINDS}) != len(KINDS):
        errors.append("two structure sets share a salt")
    if not errors:
        errors += age_errors()
    if errors:
        for e in errors:
            print("ERROR " + e)
        sys.exit(1)
    files = all_files()
    if "--check" in sys.argv:
        stale = [p for p, d in files.items() if not same(p, d)]
        for p in stale:
            print(f"{os.path.relpath(p, REPO)} is out of date")
        if stale:
            sys.exit(1)
        print(f"gen_wayshrines: {len(files)} files up to date; ruins miss "
              + ", ".join(f"{k.tid} {len(k.missing)}" for k in KINDS))
        return
    for p, d in files.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as f:
            f.write(d)
    print(f"gen_wayshrines: wrote {len(files)} files; ruins miss " + ", ".join(f"{k.tid} {len(k.missing)}" for k in KINDS))


if __name__ == "__main__":
    main()
