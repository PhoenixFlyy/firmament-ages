#!/usr/bin/env python3
"""Firmament Ages - write the Origin Archipelago of firmages-core (SPEC section 31.8, milestone M17).

After the finale the nine islands of The Origin take living samples of their Age at their plinth and grow in three
stages into small dioramas. This script writes, reproducibly (fixed seeds, sorted output), into
mod/firmages-core/src/main/resources/data/firmages/:
  firmages_ark/island/age_<N>.json        the sample rules of island N (flora, fauna, work, crown, stage templates)
  structure/ark/age_<N>/stage_<K>.nbt      the 27 stage templates (vanilla blocks, and TFC blocks where the pack disables
                                           the vanilla one: wrought iron block, anvil and lamp, firepit, rooted dirt,
                                           hanging vines, allium; without TFC those cells load as air)
  tags/item/ark/*.json                     the flora tags the rules name (entries "required": false)
  tags/block/ark/*.json                    the work tags the rules name
and dev/data/origin_arena.json: the block counts of The Origin's grand arena (rounds G3 and G4, SPEC section 16.5) from
dev/origin_arena_plan.py, the mirror of GrandArenaPlan.java (integer geometry, the same keys), and the length of the walk
from the arrival point up to the rim; GrandArenaPlanTest and the GameTests ArenaGameTests.grandArenaMatchesThePlan and
ascentIsWalkable compare the Java plan and the built arena with this file.

Template frame (shared with ArkGrowth.java): 25 by 22 by 25 blocks; the island's top centre is template (12, 6, 12),
so dy = y - 6 is the height above the island's surface. A stage writes only into air or into blocks of the island's
earlier stages, so the island's own surface (dy = 0) and the plinth are never touched; the plaza around the plinth and
the floating echo (radius 2.6) stays clear. Stage 1 is the ground (soil and plants), stage 2 the Age's work in the
north-east, stage 3 its landmark in the south-west plus what hangs under the rim.

Every id the rules name is checked against dev/data/registry.json (items, blocks), and no flora item may be hidden
(dev/age_map.toml [hidden] and [derived_hidden]). No template block may be one whose item the pack disables or hides
(gen_stage_locks.AgeMap.age_of "disabled"; review fix 0.12.0: the stages stand in the world and can be broken). The
crown entries carry neoforge:item_exists guards.

Usage
  python dev/gen_ark.py           write every file
  python dev/gen_ark.py --check   exit 1 if a file on disk differs, an id is unknown or hidden, or a template block is
                                  disabled
"""
import json
import math
import os
import random
import sys
import tomllib
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_wayshrines import nbt_bytes, same  # noqa: E402
import origin_arena_plan as arena_plan  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "data", "firmages")
SIZE = (25, 22, 25)
HALF, BELOW = 12, 6
PLAZA = 2.6
AGES = ["stone", "bronze", "iron", "arcane", "industrial", "electric", "information", "space", "quantum"]
ALLOYS = {3: "starlit_iron", 4: "cloudsteel", 5: "aurora_steel", 6: "halo_steel", 7: "void_steel", 8: "quasar_alloy"}


def radius(i):
    """OriginArena.arkRadius: 9 to 11."""
    return 9 + i % 3


def depth(d, r):
    """OriginArena.depth: rock layers under a column at distance d of an island of radius r (Java Math.round)."""
    return int(math.floor((r + 2) * (1 - d / (r + 1)) + 0.5)) + 1


def opt(i):
    return {"id": i, "required": False}


# ---------------------------------------------------------------------------------------------------------------------
# Rules (SPEC 31.8 table). Ids are checked in main().
# ---------------------------------------------------------------------------------------------------------------------
TFC_SAPLINGS = [f"tfc:wood/sapling/{w}" for w in ("acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir",
                                                     "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood",
                                                     "sequoia", "spruce", "sycamore", "white_cedar", "willow")]
TFC_SEEDS = [f"tfc:seeds/{c}" for c in ("barley", "beet", "cabbage", "carrot", "garlic", "green_bean", "jute", "maize", "oat",
                                         "onion", "papyrus", "potato", "pumpkin", "melon", "red_bell_pepper", "rice", "rye",
                                         "soybean", "squash", "sugarcane", "tomato", "wheat", "yellow_bell_pepper")]
# Vanilla trees grow in the world and vanilla wood is equal in every recipe (Felix's wood decision HW1A), so their
# saplings count on the Stone island like the TFC kinds.
VANILLA_SAPLINGS = [f"minecraft:{w}_sapling" for w in ("acacia", "birch", "cherry", "dark_oak", "jungle", "oak", "spruce")] + [
    "minecraft:mangrove_propagule"]
FRUIT_SAPLINGS = [f"tfc:plant/{f}_sapling" for f in ("banana", "cherry", "green_apple", "lemon", "olive", "orange", "peach",
                                                      "plum", "red_apple")]
ARCHWOOD = ["ars_nouveau:blue_archwood_sapling", "ars_nouveau:green_archwood_sapling", "ars_nouveau:purple_archwood_sapling",
            "ars_nouveau:red_archwood_sapling", "ars_elemental:yellow_archwood_sapling"]
FLOWERS = [f"tfc:plant/{f}" for f in ("allium", "anthurium", "black_orchid", "blue_orchid", "butterfly_milkweed", "calendula",
                                       "canna", "dandelion", "foxglove", "goldenrod", "grape_hyacinth", "heliconia", "houstonia",
                                       "kangaroo_paw", "lilac", "lotus", "marigold", "meads_milkweed", "nasturtium",
                                       "oxeye_daisy", "poppy", "primrose", "pulsatilla", "rose", "sapphire_tower",
                                       "snapdragon_pink", "snapdragon_red", "snapdragon_white", "snapdragon_yellow",
                                       "strelitzia", "trillium", "tulip_orange", "tulip_pink", "tulip_red", "tulip_white",
                                       "yucca")]
GREENHOUSE = ["firmalife:seeds/red_grape", "firmalife:seeds/white_grape", "firmalife:plant/cocoa_sapling",
              "firmalife:plant/fig_sapling", "tfc:seeds/rice", "tfc:seeds/sugarcane", "tfc:seeds/papyrus", "tfc:seeds/jute"]
MYSTICAL = [f"mysticalagriculture:{s}_seeds" for s in ("inferium", "air", "earth", "fire", "water", "coal", "copper", "iron",
                                                        "gold", "redstone", "glowstone", "nether_quartz", "obsidian",
                                                        "certus_quartz", "fluix")]
SPACE_FLORA = ["ad_astra:aeronos_mushroom", "ad_astra:strophar_mushroom", "ad_astra:aeronos_cap", "ad_astra:strophar_cap",
               "ad_astra:glacian_leaves"]
QUANTUM_FLORA = ["minecraft:chorus_flower", "minecraft:chorus_fruit", "minecraft:crimson_fungus", "minecraft:warped_fungus",
                 "minecraft:wither_rose", "minecraft:twisting_vines", "minecraft:weeping_vines", "minecraft:shroomlight"]

ITEM_TAGS = {
    "saplings": TFC_SAPLINGS + VANILLA_SAPLINGS,
    "seeds": TFC_SEEDS,
    "fruit_saplings": FRUIT_SAPLINGS,
    "archwood_saplings": ARCHWOOD,
    "flowers": FLOWERS,
    "greenhouse_crops": GREENHOUSE,
    "mystical_seeds": MYSTICAL,
    "space_flora": SPACE_FLORA,
    "quantum_flora": QUANTUM_FLORA,
}
BLOCK_TAGS = {
    "bronze_work": ["tfc:bloomery", "tfc:charcoal_forge"],
    "arcane_work": ["theurgy:pyromantic_brazier", "theurgy:calcination_oven"],
}


def lit(tag_or_block, prop="lit", value="true"):
    key = "tag" if tag_or_block.startswith("#") else "block"
    return {"type": "blockstate", key: tag_or_block.lstrip("#"), "property": prop, "value": value}


def energy(block):
    return {"type": "energy", "block": block, "amount": 1}


RULES = {
    0: dict(flora={"tag": "firmages:ark/saplings", "distinct": 4}, fauna=["tfc:rabbit"],
            work=lit("tfc:firepit")),
    1: dict(flora={"tag": "firmages:ark/seeds", "distinct": 4}, fauna=["tfc:chicken", "tfc:duck"],
            work=[lit("#firmages:ark/bronze_work"),
                  {"type": "blockstate", "tag": "firmages:ark/bronze_work", "property": "heat_level", "min": 1}]),
    2: dict(flora={"tag": "firmages:ark/fruit_saplings", "distinct": 4}, fauna=["tfc:cow", "tfc:goat"],
            work={"type": "kinetic", "block": "create:water_wheel", "rpm": 16}),
    # Magic stays optional: TFC flowers beside the archwood, a cat beside the starbuncle, a kindled blaze burner beside
    # the alchemist's fires (enchanted books are hidden in this pack, so they are no choice).
    3: dict(flora=[{"tag": "firmages:ark/archwood_saplings", "distinct": 4}, {"tag": "firmages:ark/flowers", "distinct": 4}],
            fauna=["tfc:cat", "ars_nouveau:starbuncle"],
            work=[lit("#firmages:ark/arcane_work"),
                  {"type": "blockstate", "block": "create:blaze_burner", "property": "blaze", "values": ["kindled", "seething"]}]),
    4: dict(flora={"items": ["immersiveengineering:seed"], "count": 8}, fauna=["tfc:horse"],
            work=energy("immersiveengineering:crusher")),
    5: dict(flora={"tag": "firmages:ark/greenhouse_crops", "distinct": 4}, fauna=["tfc:sheep"],
            work=energy("immersiveengineering:capacitor_hv")),
    6: dict(flora={"tag": "firmages:ark/mystical_seeds", "distinct": 4}, fauna=["tfc:pig"],
            work=lit("ae2:controller", "state", "online")),
    7: dict(flora={"tag": "firmages:ark/space_flora", "distinct": 4}, fauna=[],
            work=energy("mekanism:teleporter")),
    8: dict(flora={"tag": "firmages:ark/quantum_flora", "distinct": 4}, fauna=[],
            work=lit("mekanismgenerators:fusion_reactor_controller", "active", "true")),
}


def crown(i):
    if i < 3:
        return []
    alloy = f"firmages:{ALLOYS[i]}_ingot"
    out = [{"neoforge:conditions": [{"type": "neoforge:item_exists", "item": alloy}],
            "tag": f"firmages:celestial_alloy/age_{i}", "count": 4}]
    if i == 7:
        out.append({"neoforge:conditions": [{"type": "neoforge:item_exists", "item": "firmages:deep_space_sample"}],
                    "item": "firmages:deep_space_sample", "count": 2})
    return out


def island_file(i):
    r = RULES[i]
    return {"island": i, "flora": r["flora"], "fauna": {"entities": r["fauna"]}, "work": r["work"], "crown": crown(i),
            "stages": [f"firmages:ark/age_{i}/stage_{k}" for k in (1, 2, 3)]}


# ---------------------------------------------------------------------------------------------------------------------
# Stage templates
# ---------------------------------------------------------------------------------------------------------------------
def parse(b):
    """'minecraft:wheat[age=7]' -> ('minecraft:wheat', {'age': '7'})"""
    if "[" not in b:
        return b, {}
    name, rest = b[:-1].split("[", 1)
    return name, dict(kv.split("=", 1) for kv in rest.split(","))


def seeded(*key):
    return random.Random(zlib.crc32(("ark/" + "/".join(map(str, key))).encode()))


class Stage:
    """Cells (x, dy, z) relative to the island's top centre -> block string."""

    def __init__(self, i):
        self.i = i
        self.r = radius(i)
        self.cells = {}

    def put(self, x, dy, z, block, force=True):
        if math.hypot(x, z) <= PLAZA and 0 < dy <= 5:
            return
        if not force and (x, dy, z) in self.cells:
            return
        self.cells[(x, dy, z)] = block

    def column(self, x, z, dy0, h, block):
        for dy in range(dy0, dy0 + h):
            self.put(x, dy, z, block)

    def disc(self, cx, cz, rad, dy, block, fill=1.0, rng=None):
        for x in range(math.floor(cx - rad), math.ceil(cx + rad) + 1):
            for z in range(math.floor(cz - rad), math.ceil(cz + rad) + 1):
                if math.hypot(x - cx, z - cz) <= rad + 0.2 and (rng is None or rng.random() < fill):
                    self.put(x, dy, z, block)

    def blob(self, cx, dy, cz, rad, block):
        for x in range(math.floor(cx - rad), math.ceil(cx + rad) + 1):
            for y in range(math.floor(dy - rad), math.ceil(dy + rad) + 1):
                for z in range(math.floor(cz - rad), math.ceil(cz + rad) + 1):
                    if math.sqrt((x - cx) ** 2 + (y - dy) ** 2 + (z - cz) ** 2) <= rad + 0.25:
                        self.put(x, y, z, block, force=False)

    def blocks(self):
        """Template blocks: {(tx, ty, tz): (name, props, None)}; checks the frame and the island's reach."""
        out = {}
        for (x, dy, z), b in self.cells.items():
            tx, ty, tz = x + HALF, dy + BELOW, z + HALF
            assert 0 <= tx < SIZE[0] and 0 <= ty < SIZE[1] and 0 <= tz < SIZE[2], (self.i, x, dy, z)
            assert math.hypot(x, z) <= self.r + 1, (self.i, x, dy, z, "beyond the island")
            name, props = parse(b)
            out[(tx, ty, tz)] = (name, props, None)
        return out


def ground_cells(i):
    """Island cells for soil (dy 1): inside the island, outside the plaza."""
    r = radius(i)
    return [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1) if PLAZA < math.hypot(x, z) <= r - 1]


def feature_centre(i, angle):
    d = radius(i) / 2 + 1.5
    a = math.radians(angle)
    return round(math.cos(a) * d), round(-math.sin(a) * d)  # angle counter-clockwise from east, north = -z


THEMES = {
    0: dict(soil=[("minecraft:coarse_dirt", 3), ("minecraft:gravel", 2), ("minecraft:moss_block", 2), ("minecraft:grass_block", 4)],
            plants={"minecraft:grass_block": ["minecraft:short_grass", "minecraft:fern"], "minecraft:moss_block": ["minecraft:moss_carpet"],
                    "minecraft:coarse_dirt": ["minecraft:dead_bush"]},
            hang="tfc:plant/hanging_vines"),
    1: dict(soil=[("minecraft:grass_block", 4), ("minecraft:farmland[moisture=7]", 5)],
            plants={"minecraft:farmland[moisture=7]": ["minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]"],
                    "minecraft:grass_block": ["minecraft:short_grass", "minecraft:poppy"]},
            hang="tfc:plant/hanging_vines"),
    2: dict(soil=[("minecraft:grass_block", 6), ("tfc:rooted_dirt/alfisol", 2), ("minecraft:moss_block", 1)],
            plants={"minecraft:grass_block": ["minecraft:short_grass", "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:dandelion"],
                    "minecraft:moss_block": ["minecraft:moss_carpet"]},
            hang="minecraft:cave_vines[berries=true]"),
    3: dict(soil=[("minecraft:moss_block", 4), ("minecraft:grass_block", 3), ("minecraft:calcite", 2)],
            plants={"minecraft:moss_block": ["minecraft:small_amethyst_bud[facing=up]", "minecraft:moss_carpet"],
                    "minecraft:grass_block": ["tfc:plant/allium", "minecraft:lilac[half=lower]"],
                    "minecraft:calcite": ["minecraft:amethyst_cluster[facing=up]"]},
            hang="minecraft:amethyst_cluster[facing=down]"),
    4: dict(soil=[("minecraft:coarse_dirt", 3), ("minecraft:gravel", 3), ("minecraft:packed_mud", 2), ("minecraft:stone_bricks", 2)],
            plants={"minecraft:coarse_dirt": ["minecraft:dead_bush", "minecraft:short_grass"]},
            hang="minecraft:chain[axis=y]"),
    5: dict(soil=[("minecraft:grass_block", 4), ("minecraft:smooth_stone", 3), ("minecraft:waxed_cut_copper", 1)],
            plants={"minecraft:grass_block": ["minecraft:short_grass", "minecraft:oxeye_daisy"]},
            hang="minecraft:chain[axis=y]"),
    6: dict(soil=[("minecraft:polished_deepslate", 3), ("minecraft:tuff", 2), ("minecraft:moss_block", 3)],
            plants={"minecraft:moss_block": ["minecraft:moss_carpet", "minecraft:short_grass"]},
            hang="minecraft:chain[axis=y]"),
    7: dict(soil=[("minecraft:end_stone", 6), ("minecraft:end_stone_bricks", 2)],
            plants={"minecraft:end_stone": ["minecraft:end_rod[facing=up]"]},
            hang="minecraft:end_rod[facing=down]"),
    8: dict(soil=[("minecraft:obsidian", 4), ("minecraft:crying_obsidian", 2), ("minecraft:black_concrete", 2)],
            plants={"minecraft:obsidian": ["minecraft:amethyst_cluster[facing=up]"], "minecraft:black_concrete": ["minecraft:end_rod[facing=up]"]},
            hang="minecraft:end_rod[facing=down]"),
}


def weighted(rng, table):
    total = sum(w for _, w in table)
    x = rng.random() * total
    for b, w in table:
        x -= w
        if x < 0:
            return b
    return table[-1][0]


def stage_1(i):
    """The ground: soil patches and plants (about 60 % of the island, a third of the soil planted)."""
    s = Stage(i)
    t = THEMES[i]
    rng = seeded(i, 1)
    for x, z in ground_cells(i):
        if rng.random() > 0.6:
            continue
        soil = weighted(rng, t["soil"])
        s.put(x, 1, z, soil)
        plants = t["plants"].get(soil)
        if plants and rng.random() < 0.35:
            p = rng.choice(plants)
            s.put(x, 2, z, p)
            if p.endswith("[half=lower]"):
                s.put(x, 3, z, p.replace("half=lower", "half=upper"))
    return s


def tree(s, x, z, dy, log, leaves, h=4, rad=2.0):
    s.column(x, z, dy, h, log)
    s.blob(x, dy + h, z, rad, leaves)


def stage_2(i):
    """The Age's work, north-east of the plinth."""
    s = Stage(i)
    cx, cz = feature_centre(i, 45)
    rng = seeded(i, 2)
    if i == 0:  # standing stones around a cold hearth
        for k in range(6):
            a = math.radians(k * 60)
            x, z = cx + round(math.cos(a) * 2.5), cz + round(math.sin(a) * 2.5)
            s.column(x, z, 1, 2 + k % 2, "minecraft:mossy_cobblestone" if k % 2 else "minecraft:cobblestone")
        s.put(cx, 1, cz, "tfc:firepit[axis=x,lit=false]")
    elif i == 1:  # a mud-brick hut with a copper roof
        for x in range(cx - 2, cx + 2):
            for z in range(cz - 2, cz + 2):
                edge = x in (cx - 2, cx + 1) or z in (cz - 2, cz + 1)
                if edge and not (z == cz + 1 and x == cx - 1):
                    s.column(x, z, 1, 3, "minecraft:mud_bricks")
                s.put(x, 4, z, "minecraft:waxed_cut_copper_slab[type=bottom,waterlogged=false]")
        s.put(cx - 1, 1, cz, "minecraft:hay_block[axis=y]")
    elif i == 2:  # an orchard
        for dx, dz in ((0, 0), (-2, 2), (2, -1)):
            tree(s, cx + dx, cz + dz, 1, "minecraft:oak_log[axis=y]", "minecraft:oak_leaves[persistent=true,distance=1,waterlogged=false]", h=3, rad=1.6)
    elif i == 3:  # an amethyst mound in a calcite ring
        s.blob(cx, 1, cz, 1.6, "minecraft:amethyst_block")
        for k in range(8):
            a = math.radians(k * 45)
            s.put(cx + round(math.cos(a) * 2.8), 1, cz + round(math.sin(a) * 2.8), "minecraft:calcite")
        s.put(cx, 3, cz, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    elif i == 4:  # a brick chimney with smoke
        for x in (cx, cx + 1):
            for z in (cz, cz + 1):
                s.column(x, z, 1, 6, "minecraft:bricks")
        s.put(cx, 7, cz, "tfc:firepit[axis=x,lit=false]")
        s.put(cx - 1, 1, cz + 1, "tfc:metal/anvil/wrought_iron[facing=north]")
        s.put(cx + 2, 1, cz, "minecraft:blast_furnace[facing=west,lit=false]")
    elif i == 5:  # a glass greenhouse
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                wall = abs(x - cx) == 2 or abs(z - cz) == 2
                if wall:
                    if not (z == cz + 2 and x == cx):
                        s.column(x, z, 1, 3, "minecraft:glass")
                else:
                    s.put(x, 1, z, "minecraft:farmland[moisture=7]")
                    s.put(x, 2, z, "minecraft:carrots[age=7]" if (x + z) % 2 else "minecraft:beetroots[age=3]")
                s.put(x, 4, z, "minecraft:glass")
    elif i == 6:  # data pillars
        for k, (dx, dz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1), (0, 0))):
            h = 2 + (k * 7) % 3
            s.column(cx + dx * 2, cz + dz * 2, 1, h, "minecraft:polished_deepslate")
            s.put(cx + dx * 2, 1 + h, cz + dz * 2, "minecraft:sea_lantern")
    elif i == 7:  # a crater wall with a purpur mast
        for k in range(12):
            a = math.radians(k * 30)
            s.column(cx + round(math.cos(a) * 2.6), cz + round(math.sin(a) * 2.6), 1, 1 + k % 2, "minecraft:end_stone_bricks")
        s.column(cx, cz, 1, 4, "minecraft:purpur_pillar[axis=y]")
        s.put(cx, 5, cz, "minecraft:end_rod[facing=up]")
    elif i == 8:  # two arcs of amethyst and violet glass
        for k in range(7):
            a = math.radians(k * 30)
            s.put(cx + round(math.cos(a) * 2.5), 1 + round(math.sin(a) * 2.5), cz, "minecraft:purple_stained_glass")
            s.put(cx, 1 + round(math.sin(a) * 2.5), cz + round(math.cos(a) * 2.5), "minecraft:amethyst_block")
    # a path from the plaza to the work
    steps = max(abs(cx), abs(cz))
    path = "minecraft:dirt_path" if i < 4 else "minecraft:polished_andesite" if i < 7 else "minecraft:polished_blackstone"
    for k in range(3, steps):
        s.put(round(cx * k / steps), 1, round(cz * k / steps), path, force=False)
    del rng
    return s


def hanging(s, i):
    """Under the rim: a few hanging blocks below the rock cone's bottom."""
    r = radius(i)
    rng = seeded(i, 3, "hang")
    hang = THEMES[i]["hang"]
    for k in range(10):
        a = math.radians(k * 36 + rng.randint(0, 20))
        x, z = round(math.cos(a) * (r - 1)), round(math.sin(a) * (r - 1))
        bottom = -depth(math.hypot(x, z), r)
        n = 1 + rng.randint(0, 2)
        for j in range(n):
            if bottom - 1 - j < -BELOW:
                break
            b = hang
            if hang.startswith("minecraft:cave_vines"):
                last = j == n - 1 or bottom - 2 - j < -BELOW
                b = "minecraft:cave_vines[age=25,berries=true]" if last else "minecraft:cave_vines_plant[berries=true]"
            s.put(x, bottom - 1 - j, z, b)
            if hang.startswith("minecraft:amethyst") or hang.startswith("minecraft:end_rod") or hang.startswith("tfc:plant/hanging_vines"):
                break


def stage_3(i):
    """The landmark, south-west of the plinth, and the hanging rim."""
    s = Stage(i)
    cx, cz = feature_centre(i, 225)
    if i == 0:  # an old spruce
        tree(s, cx, cz, 1, "minecraft:spruce_log[axis=y]", "minecraft:spruce_leaves[persistent=true,distance=1,waterlogged=false]", h=6, rad=2.2)
    elif i == 1:  # a copper bell tower
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            s.column(cx + dx, cz + dz, 1, 4, "minecraft:waxed_copper_block")
        s.disc(cx, cz, 1.5, 5, "minecraft:waxed_cut_copper_slab[type=bottom,waterlogged=false]")
        s.put(cx, 4, cz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    elif i == 2:  # an iron forge yard
        s.column(cx, cz, 1, 4, "tfc:metal/block/wrought_iron")
        s.put(cx, 5, cz, "tfc:metal/lamp/wrought_iron[hanging=false,lit=false]")
        s.put(cx + 1, 1, cz, "tfc:metal/anvil/wrought_iron[facing=east]")
        for dz in (-1, 1):
            s.column(cx, cz + dz, 1, 2, "minecraft:iron_bars[east=false,north=true,south=true,west=false,waterlogged=false]")
    elif i == 3:  # a reading circle with candles and rods
        for k in range(8):
            a = math.radians(k * 45)
            x, z = cx + round(math.cos(a) * 2), cz + round(math.sin(a) * 2)
            s.column(x, z, 1, 2, "minecraft:bookshelf")
            s.put(x, 3, z, "minecraft:purple_candle[candles=3,lit=true,waterlogged=false]")
        s.put(cx, 1, cz, "minecraft:end_rod[facing=up]")
        s.put(cx, 2, cz, "minecraft:end_rod[facing=up]")
    elif i == 4:  # a rail loop with lanterns on chains
        for k in range(16):
            a = math.radians(k * 22.5)
            x, z = cx + round(math.cos(a) * 2.5), cz + round(math.sin(a) * 2.5)
            s.put(x, 1, z, "minecraft:stone_bricks")
        s.column(cx, cz, 1, 4, "minecraft:stone_brick_wall[east=none,north=none,south=none,west=none,up=true,waterlogged=false]")
        s.put(cx, 5, cz, "tfc:metal/lamp/steel[hanging=false,lit=false]")
    elif i == 5:  # a lightning tower
        s.column(cx, cz, 1, 5, "minecraft:waxed_copper_block")
        s.put(cx, 6, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s.put(cx + dx, 1, cz + dz, "minecraft:sea_lantern")
    elif i == 6:  # an obelisk of tinted glass
        for dy in range(1, 7):
            for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
                s.put(cx + dx, dy, cz + dz, "minecraft:tinted_glass" if dy % 3 else "minecraft:polished_blackstone")
        s.put(cx, 7, cz, "minecraft:end_rod[facing=up]")
    elif i == 7:  # a glass dome on purpur
        s.disc(cx, cz, 2.6, 1, "minecraft:purpur_block")
        for x in range(cx - 3, cx + 4):
            for dy in range(2, 6):
                for z in range(cz - 3, cz + 4):
                    d = math.sqrt((x - cx) ** 2 + (dy - 1.5) ** 2 + (z - cz) ** 2)
                    if 2.4 <= d <= 3.0 and math.hypot(x, z) <= radius(i) - 1:
                        s.put(x, dy, z, "minecraft:glass")
        s.put(cx, 2, cz, "minecraft:end_rod[facing=up]")
    elif i == 8:  # a standing ring of crying obsidian
        for k in range(16):
            a = math.radians(k * 22.5)
            s.put(cx + round(math.cos(a) * 3), 4 + round(math.sin(a) * 3), cz, "minecraft:crying_obsidian")
        s.put(cx, 1, cz, "minecraft:obsidian")
        s.put(cx, 2, cz, "minecraft:end_rod[facing=up]")
    hanging(s, i)
    return s


STAGES = {1: stage_1, 2: stage_2, 3: stage_3}


# ---------------------------------------------------------------------------------------------------------------------
# Files and checks
# ---------------------------------------------------------------------------------------------------------------------
# ---- the grand arena of The Origin: dev/origin_arena_plan.py mirrors mod/.../origin/GrandArenaPlan.java -------------
def arena_counts():
    counts = arena_plan.counts()
    walk = arena_plan.ascent_ok()
    assert walk > 0, "the ascent of the grand arena is not walkable from the arrival point"
    return {"radius": arena_plan.RADIUS, "classic_radius": 20, "cells": sum(counts.values()), "ascent_steps": walk,
            "counts": counts}


def all_files():
    files = {}
    files[os.path.join(REPO, "dev", "data", "origin_arena.json")] = (json.dumps(arena_counts(), indent=2) + "\n").encode("utf-8")

    def js(path, data):
        files[path] = (json.dumps(data, indent=2) + "\n").encode("utf-8")

    for i in range(9):
        js(os.path.join(DATA, "firmages_ark", "island", f"age_{i}.json"), island_file(i))
        for k, fn in STAGES.items():
            files[os.path.join(DATA, "structure", "ark", f"age_{i}", f"stage_{k}.nbt")] = nbt_bytes(SIZE, fn(i).blocks())
    for name, values in ITEM_TAGS.items():
        js(os.path.join(DATA, "tags", "item", "ark", name + ".json"), {"values": [opt(v) for v in values]})
    for name, values in BLOCK_TAGS.items():
        js(os.path.join(DATA, "tags", "block", "ark", name + ".json"), {"values": [opt(v) for v in values]})
    return files


TFC_BLOCKS_WITHOUT_ITEM = {"tfc:charcoal_forge"}  # TFC 4.2.11: the forge is made from a charcoal pile, it has no item


def id_errors():
    """Unknown ids (registry dump of the pack) and hidden flora items."""
    reg = json.load(open(os.path.join(REPO, "dev", "data", "registry.json"), encoding="utf-8"))
    items, blocks = set(reg["items"]), set(reg["blocks"]) | set(reg["items"])
    age_map = tomllib.load(open(os.path.join(REPO, "dev", "age_map.toml"), "rb"))
    hidden = set(age_map.get("hidden", {}).get("items", [])) | set(age_map.get("derived_hidden", {}).get("items", []))
    errors = []
    for name, values in ITEM_TAGS.items():
        for v in values:
            if v not in items:
                errors.append(f"item tag ark/{name}: {v} is not registered")
            elif v in hidden:
                errors.append(f"item tag ark/{name}: {v} is hidden")
    for name, values in BLOCK_TAGS.items():
        # tfc:charcoal_forge has no item, so the dump's block list (blocks without an item) is checked as well
        errors += [f"block tag ark/{name}: {v} is not registered" for v in values if v not in blocks and v not in TFC_BLOCKS_WITHOUT_ITEM]
    for i, r in RULES.items():
        flora = r["flora"] if isinstance(r["flora"], list) else [r["flora"]]
        for f in flora:
            for v in f.get("items", []):
                if v not in items or v in hidden:
                    errors.append(f"age_{i}: flora item {v} unknown or hidden")
        work = r["work"] if isinstance(r["work"], list) else [r["work"]]
        for w in work:
            if "block" in w and w["block"] not in blocks:
                errors.append(f"age_{i}: work block {w['block']} is not registered")
    return errors


def template_errors():
    """Template blocks the pack disables or hides (their item is "disabled" in dev/age_map.toml)."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_stage_locks as gsl
    am = gsl.AgeMap(tomllib.loads(open(gsl.AGE_MAP, encoding="utf-8").read()), json.load(open(gsl.REGISTRY, encoding="utf-8")),
                    json.load(open(gsl.TFC_IDS, encoding="utf-8")))
    errors = []
    for i in range(9):
        for k, fn in STAGES.items():
            for name in sorted({b[0] for b in fn(i).blocks().values()}):
                # blocks without an item of their own (crops, vine plants) follow their item's rule by mod
                if am.age_of(name)[0] == "disabled":
                    errors.append(f"age_{i}/stage_{k}: template block {name} is disabled or hidden ({am.age_of(name)[1]})")
    return errors


def main():
    errors = id_errors() + template_errors()
    files = all_files()
    if "--check" in sys.argv:
        stale = [p for p, d in files.items() if not same(p, d)]
        for p in stale:
            print(f"{os.path.relpath(p, REPO)} is out of date")
        for e in errors:
            print("ERROR " + e)
        if stale or errors:
            sys.exit(1)
        print(f"gen_ark: {len(files)} files up to date, ids checked")
        return
    for e in errors:
        print("ERROR " + e)
    if errors:
        sys.exit(1)
    for p, d in files.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as f:
            f.write(d)
    counts = []
    for i in range(9):
        counts.append(f"age_{i} " + "/".join(str(len(fn(i).cells)) for fn in STAGES.values()))
    print(f"gen_ark: wrote {len(files)} files; blocks per stage: " + ", ".join(counts))


if __name__ == "__main__":
    main()
