#!/usr/bin/env python3
"""Firmament Ages - derive every item's Age from the first Age a team can get it (generated section of dev/age_map.toml).

Rule (Felix, 2026-10-13): final Age = the later of (the age_map Age, the first Age the item is reachable); an item
that is never reachable is disabled. The age_map Age is what dev/gen_stage_locks.py gives without the generated
section, so the derivation only ever moves an item LATER, never earlier, and running it twice changes nothing.

Inputs
  <dump>/registries.json, recipes.json, recipes_full.json, item_tags.json
        /fa_dump and /fa_dump_full taken with every Age unlocked (age_9) and gate.enabled = false in
        config/firmages-server.toml (on a copy): the gate drops every recipe whose output is in age_items/disabled,
        [derived_hidden] included, so on a gated dump an item hidden once stays hidden even after its recipe chain
        returns (0.10.0: Macaw's tables after the coffee tables came back)
  dev/age_map.toml, dev/data/registry.json, dev/data/tfc-4.2.11-ids.json   (via gen_stage_locks.AgeMap)
  config/progressivestages/stages/*.toml   mob spawn locks ([mobs]) and the mob ladder
  dev/data/world_sources.json   facts read from the pack's jars and kubejs/data by --scan: loot tables, global loot
        modifiers, blocks placed by world generation and by structure templates, mob spawns, structure chests
  config/ftbquests/quests   item rewards of quests and reward tables (at the Age of their chapter or table)

Walk (dev/poc_analyze.py make_walk, the walk of the R- checks), once per Age dawn..age_9:
  - a recipe fires when its station exists (mod Age of the recipe type, Create heat, late machines), every
    ingredient is reached, every output has an age_map Age of this Age or earlier (the firmages-core gate drops a
    recipe while one output is locked) and its ProgressiveStages recipe locks are owned (recipe_lock column of
    recipes.json: mod:minecraft, the Dawn crafting whitelist, ...). Outputs are the JSON outputs plus the result
    the server reports (recipes.json), so recipe types with unusual output keys count too.
  - start set: the start set of the R- checks plus WORLD_SOURCES below, and the block loot of every ore and deposit
    block of the Age map (TFC tables, [ore_families], [ore_blocks]) from the Age it becomes visible. An item of the
    TFC-world namespaces (poc_analyze.WORLD_NS) is not in it when every recipe that makes it needs an Iron Age or later
    ingredient (blast furnace, bladed axle, barrel press; see derive()).
  - transforms: the block loot of every reached block item, crop and wall blocks, INTERACTIONS (bottles, buckets,
    brewing, special crafting), fishing and piglin bartering.
  An item's first Age is the first walk that reaches it. Each loot or world output is reached on its own once its
  Age is owned (ProgressiveStages filters locked items out of loot), recipe outputs all together.

Output
  dev/data/derived_ages.json   {item: [base, first, final, via]} for every item (first = null: never reached)
  dev/age_map.toml between "# >>> generated:derived >>>" and "# <<< generated:derived <<<":
    [derived]         "item" = "<Age>" for every item whose first reachable Age is later than its age_map Age
    [derived_hidden]  items = [...] never reachable; gen_stage_locks.py puts them in age_items/disabled and in the
                      c:hidden_from_recipe_viewers tag (EMI and JEI hide them), like [hidden]
  Then run dev/gen_stage_locks.py to rewrite the tags and stage files.

Usage
  python dev/derive_ages.py --scan            read the jars of test-server/mods (+ vanilla) into world_sources.json, derive
  python dev/derive_ages.py                   derive from the dump and world_sources.json, rewrite age_map.toml
  python dev/derive_ages.py --check           exit 1 if age_map.toml would change
  python dev/derive_ages.py --explain ITEM    base Age, first reachable Age and the step that reached it
  options: --dump-dir DIR (default test-server/local/firmages), --mods DIR, --show N (report lines per list)
"""
import argparse
import collections
import glob
import gzip
import io
import json
import os
import re
import struct
import sys
import tomllib
import zipfile

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
sys.path.insert(0, DEV)
import gen_stage_locks as gsl  # noqa: E402
import poc_analyze as pa  # noqa: E402
import validate_quests as vq  # noqa: E402

AGES = pa.AGES
AGE_MAP = os.path.join(REPO, "dev", "age_map.toml")
WORLD_SOURCES = os.path.join(REPO, "dev", "data", "world_sources.json")
DERIVED_JSON = os.path.join(REPO, "dev", "data", "derived_ages.json")
STAGES_DIR = os.path.join(REPO, "config", "progressivestages", "stages")
TEST_SERVER = os.path.join(REPO, "test-server")
VANILLA_JAR = "libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-extra.jar"
MARK_BEGIN = "# >>> generated:derived >>>"
LOCKED_RECIPE_TYPES = {"minecraft:crafting", "minecraft:smithing", "minecraft:stonecutting"}
# Vanilla cooking recipes run only where a block runs them: a furnace, blast furnace, smoker or campfire (none has a
# recipe in a TFC world early on), Create's encased fan (bulk blasting and smoking) or Mekanism's energized smelter.
COOKING_STATIONS = {"minecraft:smelting": [["minecraft:furnace", "create:encased_fan", "mekanism:energized_smelter"]],
                    "minecraft:blasting": [["minecraft:blast_furnace", "create:encased_fan"]],
                    "minecraft:smoking": [["minecraft:smoker", "create:encased_fan"]],
                    "minecraft:campfire_cooking": [["minecraft:campfire", "minecraft:soul_campfire"]]}
# A block without an item form drops what placed it: crop seeds and pods, berries, a wall torch its torch.
PLACER = re.compile(r"(_seeds|_pod|berries|_beans|_spores)$")
MARK_END = "# <<< generated:derived <<<"

# ------------------------------------------------------------------------------------------------ world model
# Dimensions and their Age (Doc 08: the Beneath Nether age_3, the End age_6, Ad Astra Moon and Mars age_7, Venus,
# Mercury and Glacio age_8, the Stargate planets age_9). The overworld is TerraFirmaCraft's: its
# biomes are tfc:*, so vanilla overworld biomes, their features and their structures (villages, temples, mineshafts,
# ...) never generate. The Beneath keeps the vanilla Nether biomes and adds its own features to them.
BIOME_NS_AGE = {"tfc": "dawn", "beneath": "age_3", "sgjourney": "age_9"}
VANILLA_BIOME_AGE = {**{b: "age_3" for b in ("nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest",
                                             "basalt_deltas")},
                     **{b: "age_6" for b in ("the_end", "end_highlands", "end_midlands", "small_end_islands",
                                             "end_barrens")}}
AD_ASTRA_AGE = [(("lunar", "moon"), "age_7"), (("martian", "mars"), "age_7"),
                (("venus", "infernal"), "age_8"), (("mercury",), "age_8"), (("glacio",), "age_8")]
# Noise settings (default block, fluid, surface rules) of the dimensions above.
NOISE_AGE = {"minecraft:nether": "age_3", "minecraft:end": "age_6"}
# Structure templates: a template that a structure reaches through its template pools takes the structure's Age (the
# earliest Age of its biomes). Templates of code-placed structures, which no pool names, take the earliest Age of their
# namespace's pool-less structures; vanilla ones by path. A template yields the blocks of its OWN namespace only: a
# mod's castle yields its castle bricks, but the crafting table or furnace a mod's structure is furnished with
# is no source of that vanilla block (decision 2026-10-13, dev/decisions-while-away.md). Chests in templates name
# their loot table, which then opens with the template. TFC and AFC templates are trees (world namespaces anyway).
VANILLA_TEMPLATE_AGE = [("end_city/", "age_6"), ("nether_fossils/", "age_3"), ("ruined_portal/", "age_3"),
                        ("bastion/", "age_3")]
TEMPLATE_SKIP_NS = ("tfc", "afc")
# Dimensions built by code (terrain features and structures that no data file describes): every block of the namespace
# counts as placed in the world from that Age; its block loot decides what mining it yields.
WORLD_BLOCK_NS = {}
# Vanilla chest tables of code-placed structures that generate in the pack's dimensions (the Beneath keeps the
# fortress; End cities in the End). Every other vanilla chest table has no structure in the pack.
VANILLA_CHEST_AGE = {"minecraft:chests/nether_bridge": "age_3", "minecraft:chests/end_city_treasure": "age_6"}
# Mobs a player summons or builds: (entity, Age of the place, items the build needs).
BUILT_MOBS = [("minecraft:wither", "dawn", ["minecraft:soul_sand", "minecraft:wither_skeleton_skull"]),
              ("minecraft:ender_dragon", "age_6", []),
              ("minecraft:iron_golem", "dawn", ["minecraft:iron_block", "minecraft:carved_pumpkin"]),
              ("minecraft:snow_golem", "dawn", ["minecraft:snow_block", "minecraft:carved_pumpkin"])]
# Vanilla interactions and special crafting recipes (no JSON output): (first Age, items needed, items made).
# Special grid recipes are vanilla grid recipes, so they need age_0 (mod:minecraft lock).
INTERACTIONS = [
    ("dawn", ["minecraft:glass_bottle"], ["minecraft:potion"]),                       # bottle of water
    ("dawn", ["minecraft:bucket"], ["minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:powder_snow_bucket"]),
    ("dawn", ["minecraft:brewing_stand", "minecraft:potion", "minecraft:gunpowder"], ["minecraft:splash_potion"]),
    ("dawn", ["minecraft:brewing_stand", "minecraft:splash_potion", "minecraft:dragon_breath"], ["minecraft:lingering_potion"]),
    ("dawn", ["minecraft:writable_book"], ["minecraft:written_book"]),                # signing
    ("dawn", ["minecraft:map"], ["minecraft:filled_map"]),                            # using an empty map
    ("dawn", ["minecraft:book", "minecraft:enchanting_table"], ["minecraft:enchanted_book"]),
    ("dawn", ["minecraft:glass_bottle", "minecraft:dragon_egg"], ["minecraft:dragon_breath"]),
    ("age_0", ["minecraft:paper", "minecraft:gunpowder"], ["minecraft:firework_rocket"]),
    ("age_0", ["minecraft:gunpowder"], ["minecraft:firework_star"]),
    ("age_0", ["minecraft:arrow", "minecraft:lingering_potion"], ["minecraft:tipped_arrow"]),
    ("age_0", ["minecraft:bowl", "minecraft:brown_mushroom", "minecraft:red_mushroom"], ["minecraft:suspicious_stew"]),
    # Mod machines and in-world steps without a recipe JSON. "entity:<id>" needs that mob (its spawn Age).
    ("dawn", ["ae2:condenser"], ["ae2:matter_ball", "ae2:singularity"]),                 # Matter Condenser
    ("dawn", ["create:empty_blaze_burner", "entity:minecraft:blaze"], ["create:blaze_burner"]),
    ("dawn", ["create:empty_schematic", "create:schematic_table"], ["create:schematic"]),
    ("dawn", ["create:stock_ticker"], ["create:shopping_list"]),
    ("dawn", ["create:packager"], ["create:cardboard_package_10x12", "create:cardboard_package_10x8",
                                   "create:cardboard_package_12x10", "create:cardboard_package_12x12"]),
    ("dawn", ["hostilenetworks:blank_data_model"], ["hostilenetworks:data_model"]),       # used on a mob
    ("dawn", ["hostilenetworks:sim_chamber", "hostilenetworks:data_model", "hostilenetworks:prediction_matrix"],
     ["hostilenetworks:overworld_prediction", "hostilenetworks:nether_prediction", "hostilenetworks:end_prediction",
      "hostilenetworks:twilight_prediction", "hostilenetworks:prediction"]),
    ("dawn", ["occultengineering:empty_pentacle_schematic"], ["occultengineering:pentacle_schematic"]),
    ("dawn", ["ars_nouveau:blank_parchment", "ars_nouveau:scribes_table"], ["ars_nouveau:spell_parchment"]),
    ("dawn", ["immersiveengineering:sample_drill"], ["immersiveengineering:coresample"]),
    # Exposure 1.9.19: the lightroom prints a developed film on paper with dyes (lightroom.dyes_* in
    # defaultconfigs/exposure-server.toml), photographs stack by using one on another, an album is signed like a book.
    ("dawn", ["exposure:lightroom", "exposure:developed_black_and_white_film", "minecraft:paper", "minecraft:black_dye"],
     ["exposure:photograph"]),
    ("dawn", ["exposure:lightroom", "exposure:developed_color_film", "minecraft:paper", "minecraft:cyan_dye",
              "minecraft:magenta_dye", "minecraft:yellow_dye", "minecraft:black_dye"], ["exposure:photograph"]),
    ("dawn", ["exposure:lightroom", "exposure:developed_black_and_white_film", "minecraft:paper", "minecraft:cyan_dye",
              "minecraft:magenta_dye", "minecraft:yellow_dye"], ["exposure:chromatic_sheet"]),
    ("dawn", ["exposure:photograph"], ["exposure:stacked_photographs"]),
    ("dawn", ["exposure:album"], ["exposure:signed_album"]),
    # 1.0 round C: Psi's CAD Assembler builds the CAD from its parts (GUI, no recipe JSON); Ars Energistique's P2P
    # tunnels are AE2 P2P tunnels attuned with a Source gem or a spell book (arseng p2p_attunements tags); AeroEngine
    # encases a Create shaft with its engine casing (right click).
    ("dawn", ["psi:cad_assembler", "psi:cad_assembly_iron", "psi:cad_core_basic", "psi:cad_socket_basic"], ["psi:cad"]),
    ("dawn", ["ae2:me_p2p_tunnel", "ars_nouveau:source_gem"], ["arseng:source_p2p_tunnel"]),
    ("dawn", ["ae2:me_p2p_tunnel", "ars_nouveau:novice_spell_book"], ["arseng:spell_p2p_tunnel"]),
    ("dawn", ["create:shaft", "aeroengineering:engine_casing"], ["aeroengineering:engine_encased_shaft"]),
    ("dawn", ["create:brown_toolbox"], [f"create:{c}_toolbox" for c in (               # create:toolbox_dyeing (special)
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
        "blue", "green", "red", "black")]),
    # A handful of acorns 1.2: the long secateurs cut nuts from TFC oak, chestnut, hickory, pine and palm leaves in
    # autumn (LongSecateurs.useOn, javap; the pine gives TFC pinecones). No loot table, so the walk needs this step.
    ("dawn", ["ahandfulofacorns:long_secateurs"], ["ahandfulofacorns:acorn", "ahandfulofacorns:chestnut",
                                                    "ahandfulofacorns:hickory_nut", "ahandfulofacorns:coconut"]),
    # An opened coconut used on a TFC barrel pours its juice into the barrel and leaves the meat (OpenedCoconutItem).
    ("dawn", ["ahandfulofacorns:opened_coconut", "tfc:wood/barrel/oak"], ["ahandfulofacorns:coconut_meat"]),
    # firmages-core Sky Harvest (SPEC 26): the Storm Rod fuses sand into storm glass in rain (block entity, no recipe);
    # a bucket takes aurora light from an Aurora Net (pipes take the fluid itself).
    ("dawn", ["firmages:storm_rod", "tfc:sand/yellow"], ["firmages:storm_glass"]),
    ("dawn", ["firmages:aurora_net", "minecraft:bucket"], ["firmages:aurora_light_bucket"]),
]
# Roads and Roofs: poured concrete dries in the world, trodden roads crack: (wet or fresh block, the block it becomes).
WORLD_CHANGES = [("rnr:wet_concrete_road", "rnr:concrete_road"), ("rnr:wet_concrete_road_panel", "rnr:concrete_road_panel"),
                 ("rnr:wet_concrete_road_sett", "rnr:concrete_road_sett"),
                 ("rnr:wet_concrete_road_flagstones", "rnr:concrete_road_flagstones"),
                 ("rnr:wet_concrete_road_control_joint", "rnr:concrete_road_control_joint"),
                 ("rnr:trodden_wet_concrete_road", "rnr:trodden_concrete_road"),
                 ("rnr:concrete_road", "rnr:cracked_concrete_road"), ("rnr:trodden_concrete_road", "rnr:cracked_trodden_concrete_road")]
# Trees and giant mushrooms Ad Astra places by code on its planets: (items, Age of the planet).
CODE_WORLD = [(["ad_astra:glacian_log", "ad_astra:glacian_leaves", "ad_astra:glacian_fur"], "age_8"),   # Glacio
              (["ad_astra:aeronos_cap", "ad_astra:aeronos_stem", "ad_astra:aeronos_mushroom"], "age_7"),   # Mars
              (["ad_astra:strophar_cap", "ad_astra:strophar_stem", "ad_astra:strophar_mushroom"], "age_8"),  # Venus
              # TFC Volcanoes: random ticks of lava and hot-spring water add mineral sheets to the rock around them
              # (FluidBlockEventHelpers.tryPlaceMineralSheet, javap 2.3; shouldLavaDepositMinerals and
              # shouldHotSpringsDepositMinerals stay on in config/tfcvolcanoes-startup.toml). Overworld, from Dawn;
              # pitchblende keeps its age_map Age.
              ([f"tfcvolcanoes:mineral/{m}" for m in (
                  "alabandite", "apatite", "arcanite", "bastnasite", "brimstone", "calcite", "galena", "greigite", "jet",
                  "magnesite", "microcline", "olivine", "pitchblende", "salmiak", "salt", "saltpeter", "satinspar",
                  "selenite", "serpentine", "smithsonite", "spherocobaltite", "zabuyelite")], "dawn"),
              # firmages-core Starfall (SPEC 25.3): old craters in new chunks line their bowls and rims with sky rock;
              # their meteorites and cores are [ore_blocks] of dev/age_map.toml
              (["firmages:sky_rock"], "dawn")]
# Mods whose own spawning is switched off by config: their mobs spawn only where a scanned source puts them (the pack's
# biome modifiers in kubejs/data), not from the mod Age. Alex's Mobs Continued: config/amc.json sets every weight to 0,
# kubejs/data/firmages/neoforge/biome_modifier/alexsmobs_*_spawn_tfc.json spawns the 22 kept species (V1B).
OWN_SPAWNS_OFF = {"alexsmobs"}
# Drops a mod adds in code (no loot table or modifier file): (items, Age; None = the item's mod Age).
CODE_DROPS = [
    (["apotheosis:gem"], None),                         # gem drops from any mob (apotheosis:gem_entity_drops, code)
    # Artifacts in the TFC ruins, the Cold Forge and the Beneath fortresses: LootJS pools of
    # kubejs/server_scripts/firmages/artifacts_loot.js (same lists, same Ages).
    ([f"artifacts:{i}" for i in ("anglers_hat", "cowboy_hat", "novelty_drinking_hat", "plastic_drinking_hat",
                                 "superstitious_hat", "villager_hat", "lucky_scarf", "whoopee_cushion", "umbrella",
                                 "snorkel", "flippers", "kitty_slippers", "snowshoes", "golden_hook", "onion_ring",
                                 "bunny_hoppers")], "age_1"),
    ([f"artifacts:{i}" for i in ("running_shoes", "cloud_in_a_bottle", "aqua_dashers", "steadfast_spikes", "feral_claws",
                                 "power_glove", "pocket_piston", "charm_of_sinking", "charm_of_shrinking",
                                 "night_vision_goggles", "antidote_vessel", "panic_necklace")], "age_2"),
    ([f"artifacts:{i}" for i in ("fire_gauntlet", "flame_pendant", "shock_pendant", "thorn_pendant", "cross_necklace",
                                 "strider_shoes", "withered_bracelet", "obsidian_skull", "vampiric_glove",
                                 "scarf_of_invisibility")], "age_3"),
    (["artifacts:crystal_heart", "artifacts:warp_drive"], "age_4"),
    (["alexsmobs:emu_egg"], "dawn"),                    # emus lay their eggs (EntityEmu, code), Dawn wildlife
    (["firmages:wayshrine_stone"], "dawn"),             # a lit Wayshrine drops it (firmages-core, SPEC-himmel 28.7); Dawn by 24.5
    (["firmages:consecrated_shard"], "age_9"),          # The Primordial sheds six per Firmament Shatter (BossEvents, code)
]
# Pre-existing design gaps: items the walk cannot reach from any source although the design uses them. They stay at
# their age_map Age (visible from that Age) instead of being hidden, and each is an open point in dev/poc-results.md.
KNOWN_GAPS = {
    # Create Enchantment Industry's Blaze Enchanter (Arcane Age quest task) is smithed with Create Dragons Plus's blaze
    # upgrade template, whose only recipe copies a template; the first one comes from no loot table or modifier file.
    "create_dragons_plus:blaze_upgrade_smithing_template": "first template has no data-visible source",
    # The Blaze Enchanter (Arcane Age quest task) is smithed onto Apothic Enchanting's table, whose recipe needs
    # c:gems/diamond; diamonds are age_4 (TFC diamond ore, [tfc_ores]).
    "apothic_enchanting:apothic_enchanting_table": "recipe needs c:gems/diamond (age_4); Arcane Age quest needs it",
}


# ------------------------------------------------------------------------------------------------ NBT (templates)
def _nbt(buf):
    """Minimal reader for gzip'd structure templates: returns the root compound as dicts and lists."""
    data = gzip.decompress(buf)
    pos = 0

    def rd(fmt):
        nonlocal pos
        v = struct.unpack_from(">" + fmt, data, pos)
        pos += struct.calcsize(">" + fmt)
        return v[0]

    def string():
        nonlocal pos
        n = rd("H")
        s = data[pos:pos + n].decode("utf-8", "replace")
        pos += n
        return s

    def payload(t):
        nonlocal pos
        if t == 1:
            return rd("b")
        if t == 2:
            return rd("h")
        if t == 3:
            return rd("i")
        if t == 4:
            return rd("q")
        if t == 5:
            return rd("f")
        if t == 6:
            return rd("d")
        if t == 7:
            n = rd("i")
            pos += n
            return None
        if t == 8:
            return string()
        if t == 9:
            et, n = rd("b"), rd("i")
            return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                ct = rd("b")
                if ct == 0:
                    return out
                k = string()
                out[k] = payload(ct)
        if t == 11:
            n = rd("i")
            pos += 4 * n
            return None
        if t == 12:
            n = rd("i")
            pos += 8 * n
            return None
        raise ValueError(f"NBT tag {t}")
    t = rd("b")
    string()
    return payload(t)


# ------------------------------------------------------------------------------------------------ scan
def _json(z, n):
    try:
        return json.loads(z.read(n).decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        return None


def _strings(o, out):
    if isinstance(o, dict):
        for k, v in o.items():
            if k != "type":
                _strings(v, out)
    elif isinstance(o, list):
        for v in o:
            _strings(v, out)
    elif isinstance(o, str):
        out.add(o)
    return out


# Keys of feature configs that name blocks a feature tests, not blocks it places (block predicates, rule tests, the
# blocks roots or trunks may grow through): a sapling in a would_survive filter is no sapling in the world (0.10.0 put
# every vanilla sapling at age_3 through the replaceable blocks of minecraft:crimson_fungus).
FEATURE_TEST_KEYS = {"predicate", "allowed_placement", "replaceable_blocks", "replaceable", "can_grow_through",
                     "muddy_roots_in", "valid_base_block", "target", "can_place_on", "can_replace", "root_replaceable",
                     "allowed_tree_position", "cannot_replace", "invalid_blocks"}


def _feature_strings(o, out):
    if isinstance(o, dict):
        for k, v in o.items():
            if k != "type" and k not in FEATURE_TEST_KEYS:
                _feature_strings(v, out)
    elif isinstance(o, list):
        for v in o:
            _feature_strings(v, out)
    elif isinstance(o, str):
        out.add(o)
    return out


def _loot_entries(o, items, tags, refs):
    """Item ids, item tags and nested table ids of a loot table JSON."""
    if isinstance(o, dict):
        t = str(o.get("type", "")).replace("minecraft:", "")
        if t == "item" and isinstance(o.get("name"), str):
            items.add(o["name"])
        elif t == "tag" and isinstance(o.get("name"), str):
            tags.add(o["name"])
        elif t == "loot_table" and isinstance(o.get("value"), str):
            refs.add(o["value"])
        for k, v in o.items():
            if k not in ("conditions", "predicate"):
                _loot_entries(v, items, tags, refs)
    elif isinstance(o, list):
        for v in o:
            _loot_entries(v, items, tags, refs)


def scan(mods_dir, vanilla_jar):
    """Read loot tables, loot modifiers, world generation, templates and spawns of every jar (+ kubejs/data last)."""
    if not os.path.isfile(vanilla_jar):  # without it every vanilla loot table, structure and biome drops out
        sys.exit(f"scan: no vanilla jar at {vanilla_jar}")
    jars = [vanilla_jar] + sorted(glob.glob(os.path.join(mods_dir, "*.jar")))
    loot = {}            # table -> {"items", "tags", "refs"}; a mod's copy of a vanilla table replaces it, mods merge
    loot_owner = {}
    glm = []             # {"id", "items", "tables"}
    biome_tags = collections.defaultdict(set)
    biomes = {}          # biome -> {"features", "spawns"}
    modifiers = {}       # biome modifier id -> {"kind", "biomes", "values"}
    features = {}        # feature id -> strings
    noise = {}           # noise settings id -> strings
    structures = {}      # structure -> {"biomes", "pool", "spawns"}
    pools = {}           # pool -> template locations
    templates = {}       # template -> {"blocks", "pools", "loot", "spawners"}

    def put_loot(tid, d, owner):
        items, tags, refs = set(), set(), set()
        _loot_entries(d, items, tags, refs)
        if tid in loot and loot_owner[tid] != "minecraft" and owner != "kubejs":
            e = loot[tid]
            items |= set(e["items"])
            tags |= set(e["tags"])
            refs |= set(e["refs"])
        loot[tid] = {"items": sorted(items), "tags": sorted(tags), "refs": sorted(refs)}
        loot_owner[tid] = owner

    def visit(owner, names, read_json, read_bytes):
        for n in names:
            m = re.match(r"data/([^/]+)/(.+)$", n)
            if not m:
                continue
            ns, rest = m.groups()
            if rest.startswith("loot_table/") and n.endswith(".json"):
                d = read_json(n)
                if d is not None:
                    put_loot(f"{ns}:{rest[len('loot_table/'):-5]}", d, owner)
            elif rest.startswith("loot_modifiers/") and n.endswith(".json") and ns != "neoforge":
                d = read_json(n)
                if not isinstance(d, dict):
                    continue
                conds = json.dumps(d.get("conditions", []))
                tables = sorted(set(re.findall(r'"loot_table_id": "([a-z0-9_.-]+:[a-z0-9_/.-]+)"', conds)))
                strs = _strings({k: v for k, v in d.items() if k != "conditions"}, set())
                glm.append({"id": f"{ns}:{rest[len('loot_modifiers/'):-5]}", "strings": sorted(strs), "tables": tables})
            elif rest.startswith("tags/worldgen/biome/") and n.endswith(".json"):
                d = read_json(n)
                if isinstance(d, dict):
                    tag = f"{ns}:{rest[len('tags/worldgen/biome/'):-5]}"
                    for v in d.get("values", []):
                        biome_tags[tag].add(v["id"] if isinstance(v, dict) else v)
            elif rest.startswith("worldgen/biome/") and n.endswith(".json"):
                d = read_json(n)
                if isinstance(d, dict):
                    feats = _strings(d.get("features", []), set())
                    spawns = {e.get("type") for lst in (d.get("spawners") or {}).values() for e in lst if isinstance(e, dict)}
                    biomes[f"{ns}:{rest[len('worldgen/biome/'):-5]}"] = {"features": sorted(feats), "spawns": sorted(spawns - {None})}
            elif rest.startswith("neoforge/biome_modifier/") and n.endswith(".json"):
                # keyed by resource id: a later copy (kubejs/data, e.g. "neoforge:none") replaces the jar's modifier
                d = read_json(n)
                if not isinstance(d, dict):
                    continue
                mid = f"{ns}:{rest[len('neoforge/biome_modifier/'):-5]}"
                modifiers.pop(mid, None)
                t = d.get("type", "")
                if t == "neoforge:add_features":
                    modifiers[mid] = {"kind": "features", "biomes": d.get("biomes"), "values": sorted(_strings(d.get("features"), set()))}
                elif t == "neoforge:add_spawns":
                    sp = d.get("spawners")
                    sp = sp if isinstance(sp, list) else [sp]
                    modifiers[mid] = {"kind": "spawns", "biomes": d.get("biomes"),
                                      "values": sorted({s.get("type") for s in sp if isinstance(s, dict)} - {None})}
            elif re.match(r"worldgen/(configured_feature|placed_feature)/.+\.json$", rest):
                d = read_json(n)
                if d is not None:
                    fid = f"{ns}:{rest.split('/', 2)[2][:-5]}"
                    features.setdefault(fid, set()).update(_feature_strings(d, set()))
            elif rest.startswith("worldgen/noise_settings/") and n.endswith(".json"):
                d = read_json(n)
                if d is not None:
                    noise[f"{ns}:{rest[len('worldgen/noise_settings/'):-5]}"] = sorted(_strings(d, set()))
            elif rest.startswith("worldgen/structure/") and n.endswith(".json"):
                d = read_json(n)
                if isinstance(d, dict):
                    sp = {e.get("type") for c in (d.get("spawn_overrides") or {}).values() if isinstance(c, dict)
                          for e in c.get("spawns", []) if isinstance(e, dict)}
                    structures[f"{ns}:{rest[len('worldgen/structure/'):-5]}"] = {
                        "biomes": d.get("biomes"), "pool": d.get("start_pool") if isinstance(d.get("start_pool"), str) else None,
                        "spawns": sorted(sp - {None})}
            elif rest.startswith("worldgen/template_pool/") and n.endswith(".json"):
                d = read_json(n)
                if isinstance(d, dict):
                    locs = set()
                    for el in d.get("elements", []):
                        _template_locs(el, locs)
                    pid = f"{ns}:{rest[len('worldgen/template_pool/'):-5]}"
                    pools.setdefault(pid, set()).update(locs)
                    if isinstance(d.get("fallback"), str):
                        pools[pid].add("pool:" + d["fallback"])
            elif re.match(r"structures?/.+\.nbt$", rest) and ns not in TEMPLATE_SKIP_NS:
                try:
                    root = _nbt(read_bytes(n))
                except Exception:  # noqa: BLE001 - a broken template only loses its blocks
                    continue
                tid = f"{ns}:{rest.split('/', 1)[1][:-4]}"
                pal = root.get("palette") or [p for ps in (root.get("palettes") or []) for p in ps]
                info = {"blocks": set(), "pools": set(), "loot": set(), "spawners": set()}
                info["blocks"] = {p.get("Name") for p in pal if isinstance(p, dict) and p.get("Name")}
                for b in root.get("blocks") or []:
                    nb = b.get("nbt") if isinstance(b, dict) else None
                    if not isinstance(nb, dict):
                        continue
                    if isinstance(nb.get("pool"), str):
                        info["pools"].add(nb["pool"])
                    if isinstance(nb.get("LootTable"), str):
                        info["loot"].add(nb["LootTable"])
                    sd = nb.get("SpawnData") or nb.get("spawn_data")
                    if isinstance(sd, dict) and isinstance(sd.get("entity"), dict) and sd["entity"].get("id"):
                        info["spawners"].add(sd["entity"]["id"])
                templates[tid] = info

    for j in jars:
        try:
            z = zipfile.ZipFile(j)
        except (OSError, zipfile.BadZipFile):
            continue
        owner = "minecraft" if j == vanilla_jar else os.path.basename(j)
        visit(owner, z.namelist(), lambda n, z=z: _json(z, n), z.read)
    kdata = os.path.join(REPO, "kubejs")
    names = [os.path.relpath(p, kdata).replace(os.sep, "/") for p in glob.glob(os.path.join(kdata, "data", "**", "*"), recursive=True)
             if os.path.isfile(p)]

    def kjson(n):
        try:
            return json.load(open(os.path.join(kdata, n), encoding="utf-8-sig"))
        except (OSError, ValueError):
            return None
    visit("kubejs", names, kjson, lambda n: open(os.path.join(kdata, n), "rb").read())
    out = {
        "source": f"dev/derive_ages.py --scan: {len(jars) - 1} mod jars in {os.path.relpath(mods_dir, REPO)}, the vanilla "
                  f"server jar and kubejs/data",
        "loot": dict(sorted(loot.items())),
        "glm": sorted(glm, key=lambda g: g["id"]),
        "biome_tags": {k: sorted(v) for k, v in sorted(biome_tags.items())},
        "biomes": dict(sorted(biomes.items())),
        "biome_modifiers": list(modifiers.values()),
        "features": {k: sorted(v) for k, v in sorted(features.items())},
        "noise_settings": dict(sorted(noise.items())),
        "structures": dict(sorted(structures.items())),
        "pools": {k: sorted(v) for k, v in sorted(pools.items())},
        "templates": {k: {kk: sorted(vv) for kk, vv in v.items()} for k, v in sorted(templates.items())},
    }
    # A block table that drops only its own block is stored by name only (most of the loot tables).
    out["self_drop"] = sorted(t for t, v in out["loot"].items() if not v["tags"] and not v["refs"]
                              and ":blocks/" in t and v["items"] == [t.replace(":blocks/", ":", 1)])
    for t in out["self_drop"]:
        del out["loot"][t]
    write_lines(WORLD_SOURCES, out)
    print(f"scan: {len(loot)} loot tables, {len(glm)} loot modifiers, {len(biomes)} biomes, {len(features)} features, "
          f"{len(structures)} structures, {len(templates)} templates -> {os.path.relpath(WORLD_SOURCES, REPO)}")


def write_lines(path, data):
    """JSON with one entry of each top-level section per line (small and diffable)."""
    parts = []
    for k, v in data.items():
        if isinstance(v, dict):
            body = ",\n".join(f"  {json.dumps(kk)}: {json.dumps(vv, separators=(',', ':'))}" for kk, vv in v.items())
            parts.append(f"{json.dumps(k)}: {{\n{body}\n}}")
        elif isinstance(v, list) and v:
            body = ",\n".join("  " + json.dumps(x, separators=(",", ":")) for x in v)
            parts.append(f"{json.dumps(k)}: [\n{body}\n]")
        else:
            parts.append(f"{json.dumps(k)}: {json.dumps(v)}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n" + ",\n".join(parts) + "\n}\n")


def load_world_sources():
    ws = json.load(open(WORLD_SOURCES, encoding="utf-8"))
    for t in ws.pop("self_drop", []):
        ws["loot"][t] = {"items": [t.replace(":blocks/", ":", 1)], "tags": [], "refs": []}
    return ws


def _template_locs(el, out):
    if isinstance(el, dict):
        for k, v in el.items():
            if k == "location" and isinstance(v, str):
                out.add(v)
            else:
                _template_locs(v, out)
    elif isinstance(el, list):
        for v in el:
            _template_locs(v, out)


# ------------------------------------------------------------------------------------------------ world ages
def earlier(a, b):
    if a is None:
        return b
    if b is None:
        return a
    return a if AGES.index(a) <= AGES.index(b) else b


def later(a, b):
    if a is None or b is None:
        return a or b
    return a if AGES.index(a) >= AGES.index(b) else b


class World:
    """Ages of biomes, blocks placed in the world, mobs and loot tables, from world_sources.json."""

    def __init__(self, ws, mod_age, items, blocks):
        self.ws, self.mod_age, self.items, self.blocks = ws, mod_age, items, blocks
        self.biome_age = {b: self._biome_age(b) for b in ws["biomes"]}
        self._tag_cache = {}
        self.world_blocks = {}    # block -> (Age, why)
        self.entity_src = collections.defaultdict(dict)   # entity -> {why: Age}
        self.table_src = collections.defaultdict(dict)    # loot table -> {why: Age}
        self._features()
        self._structures()
        for b in sorted(blocks):
            ns = b.split(":")[0]
            if ns in WORLD_BLOCK_NS:
                self._place([b], WORLD_BLOCK_NS[ns], f"{ns} dimension")

    def _biome_age(self, b):
        ns, path = b.split(":", 1)
        if ns in BIOME_NS_AGE:
            return BIOME_NS_AGE[ns]
        if ns == "minecraft":
            return VANILLA_BIOME_AGE.get(path)
        if ns == "ad_astra":
            for words, st in AD_ASTRA_AGE:
                if any(w in path for w in words):
                    return st
        return None

    def biomes_of(self, spec, depth=0):
        """Biome ids of a biome holder set: an id, '#tag', a list, or a NeoForge and/or/not object."""
        out = set()
        if isinstance(spec, list):
            for s in spec:
                out |= self.biomes_of(s, depth)
        elif isinstance(spec, dict):
            t = spec.get("type", "")
            vals = spec.get("values", [])
            if t == "neoforge:not":
                return set()
            if t == "neoforge:and":
                pos = [self.biomes_of(v, depth) for v in vals if not (isinstance(v, dict) and v.get("type") == "neoforge:not")]
                neg = [self.biomes_of(v.get("value"), depth) for v in vals if isinstance(v, dict) and v.get("type") == "neoforge:not"]
                out = set.intersection(*pos) if pos else set()
                for n_ in neg:
                    out -= n_
                return out
            for k, v in spec.items():
                if k != "type":
                    out |= self.biomes_of(v, depth)
        elif isinstance(spec, str) and spec.startswith("#") and depth < 10:
            tag = spec[1:]
            if tag not in self._tag_cache:
                self._tag_cache[tag] = set()
                self._tag_cache[tag] = self.biomes_of(self.ws["biome_tags"].get(tag, []), depth + 1)
            out |= self._tag_cache[tag]
        elif isinstance(spec, str):
            ref = spec if ":" in spec else "minecraft:" + spec
            if ref in self.ws["biomes"]:
                out.add(ref)
        return out

    def age_of_biomes(self, spec):
        st = None
        for b in self.biomes_of(spec):
            st = earlier(st, self.biome_age.get(b))
        return st

    def _place(self, strings, st, why):
        for s in strings:
            if s in self.blocks:
                old = self.world_blocks.get(s)
                if old is None or AGES.index(st) < AGES.index(old[0]):
                    self.world_blocks[s] = (st, why)

    def _features(self):
        feats = self.ws["features"]
        feat_age = {}

        def spread(fid, st, seen):
            if fid in seen or fid not in feats:
                return
            seen.add(fid)
            if fid in feat_age and AGES.index(feat_age[fid]) <= AGES.index(st):
                return
            feat_age[fid] = st
            for s in feats[fid]:
                if s in feats:
                    spread(s, st, seen)
        for b, d in self.ws["biomes"].items():
            st = self.biome_age[b]
            if st is None:
                continue
            for f in d["features"]:
                spread(f, st, set())
            for e in d["spawns"]:
                self.entity_src[e][f"spawns in {b}"] = st
        for m in self.ws["biome_modifiers"]:
            st = self.age_of_biomes(m["biomes"])
            if st is None:
                continue
            for v in m["values"]:
                if m["kind"] == "features":
                    spread(v, st, set())
                else:
                    self.entity_src[v][f"spawn modifier ({st})"] = st
        for fid, st in feat_age.items():
            self._place(feats[fid], st, f"world generation {fid}")
        for nid, strings in self.ws["noise_settings"].items():
            st = NOISE_AGE.get(nid)
            ns, path = nid.split(":", 1)
            if st is None and ns in BIOME_NS_AGE:
                st = BIOME_NS_AGE[ns]
            if st is None and ns == "ad_astra":
                st = next((a for words, a in AD_ASTRA_AGE if any(w in path for w in words)), None)
            if st:
                self._place(strings, st, f"terrain {nid}")

    def _structures(self):
        tmpl = self.ws["templates"]
        pools = self.ws["pools"]
        tmpl_age = {}
        linked = set()

        def reach_pool(pid, st, why, seen):
            if pid in seen:
                return
            seen.add(pid)
            for loc in pools.get(pid, []):
                if loc.startswith("pool:"):
                    reach_pool(loc[5:], st, why, seen)
                elif loc in tmpl:
                    linked.add(loc)
                    if st and (loc not in tmpl_age or AGES.index(st) < AGES.index(tmpl_age[loc][0])):
                        tmpl_age[loc] = (st, why)
                    for p in tmpl[loc]["pools"]:
                        reach_pool(p, st, why, seen)
        ns_code_age = {}
        for sid, s in self.ws["structures"].items():
            st = self.age_of_biomes(s["biomes"])
            ns = sid.split(":")[0]
            if s["pool"]:
                reach_pool(s["pool"], st, f"structure {sid}", set())
                ns = s["pool"].split(":")[0]
            if st is None:
                continue
            if not s["pool"]:
                ns_code_age[ns] = earlier(ns_code_age.get(ns), st)
            for e in s["spawns"]:
                self.entity_src[e][f"spawns in structure {sid}"] = st
        for tid in tmpl:
            if tid in linked:
                continue
            ns, path = tid.split(":", 1)
            if ns == "minecraft":
                st = next((a for pre, a in VANILLA_TEMPLATE_AGE if path.startswith(pre)), None)
            else:
                st = ns_code_age.get(ns)
            if st:
                tmpl_age[tid] = (st, f"structure template {tid}")
        for tid, (st, why) in tmpl_age.items():
            t = tmpl[tid]
            ns = tid.split(":")[0]
            self._place([b for b in t["blocks"] if b.split(":")[0] == ns], st, why)
            for lt in t["loot"]:
                self.table_src[lt][why] = st
            for e in t["spawners"]:
                self.entity_src[e][f"spawner in {why}"] = st
        for tid, st in VANILLA_CHEST_AGE.items():
            self.table_src[tid]["structure chest"] = st
        # chest tables of a mod's code-placed structures that no template names: the mod's earliest such structure
        for tid in self.ws["loot"]:
            ns, path = tid.split(":", 1)
            if ns != "minecraft" and ns in ns_code_age and not path.startswith(("blocks/", "entities/")):
                self.table_src[tid][f"structure loot of {ns}"] = ns_code_age[ns]


def mob_locks():
    """Entity -> the Age whose stage lifts its spawn lock (Age stages and the mob ladder mob_N = age_N)."""
    out = {}
    for f in glob.glob(os.path.join(STAGES_DIR, "*.toml")):
        stage = os.path.basename(f)[:-5]
        m = re.fullmatch(r"(?:age|mob)_(\d)", stage)
        if not m:
            continue
        st = f"age_{m.group(1)}"
        mobs = tomllib.load(open(f, "rb")).get("mobs", {})
        ids = list(mobs.get("locked_spawns", [])) + [r.get("target") for r in mobs.get("replacements", [])]
        for sel in ids:
            if sel and sel.startswith("id:"):
                out[sel[3:]] = later(out.get(sel[3:]), st)
    return out


def quest_rewards():
    """Item -> (Age, why) of every quest item reward and reward-table entry (chapter Age; table tag age:<stage>)."""
    qdir = vq.QDIR
    out = {}

    def put(i, st, why):
        if isinstance(i, str) and (i not in out or AGES.index(st) < AGES.index(out[i][0])):
            out[i] = (st, why)
    for p in sorted(glob.glob(os.path.join(qdir, "reward_tables", "*.snbt"))):
        t = vq.load(p)
        st = next((tg.split(":", 1)[1] for tg in t.get("tags", []) if str(tg).startswith("age:")), None)
        if st not in AGES:
            continue
        for r in t.get("rewards", []):
            if isinstance(r.get("item"), dict):
                put(r["item"].get("id"), st, f"quest reward table {os.path.basename(p)[:-5]}")
    for p in sorted(glob.glob(os.path.join(qdir, "chapters", "*.snbt"))):
        ch = vq.load(p)
        st = ch.get("progressivestages_required_stage")
        if st not in AGES:
            continue
        for q in ch.get("quests", []):
            for r in q.get("rewards", []):
                if r.get("type") == "item" and isinstance(r.get("item"), dict):
                    put(r["item"].get("id"), st, f"quest reward in {os.path.basename(p)[:-5]}")
    return out


# ------------------------------------------------------------------------------------------------ derivation
def base_age_map():
    raw = open(AGE_MAP, encoding="utf-8").read()
    amap = tomllib.loads(raw)
    amap.pop("derived", None)
    amap.pop("derived_hidden", None)
    reg = json.load(open(gsl.REGISTRY, encoding="utf-8"))
    tfc = json.load(open(gsl.TFC_IDS, encoding="utf-8"))
    return raw, amap, reg, gsl.AgeMap(amap, reg, tfc)


def derive(dump_dir, show):
    raw, amap, reg, am = base_age_map()
    items = list(reg["items"])
    base = {i: am.age_of(i)[0] for i in items}
    base_why = {i: am.age_of(i)[1] for i in items}
    dump_items = set(json.load(open(os.path.join(dump_dir, "registries.json"), encoding="utf-8"))["item"]) - {"minecraft:air"}
    if dump_items != set(items):
        gsl.die(f"the dump in {dump_dir} and dev/data/registry.json list different items "
                f"({len(dump_items - set(items))} only in the dump, {len(set(items) - dump_items)} only in the snapshot); "
                f"refresh with gen_stage_locks.py --registry")
    blocks = set(json.load(open(os.path.join(dump_dir, "registries.json"), encoding="utf-8"))["block"])
    tags = json.load(open(os.path.join(dump_dir, "item_tags.json"), encoding="utf-8"))
    dump = json.load(open(os.path.join(dump_dir, "recipes.json"), encoding="utf-8"))
    if not set(AGES) <= set(dump.get("unlocked", [])):
        gsl.die(f"{dump_dir}/recipes.json was dumped with Ages {dump.get('unlocked')}; dump with age_9 unlocked")
    rows = [dict(zip(dump["columns"], r)) for r in dump["recipes"]]
    full = {k: json.loads(v) for k, v in json.load(open(os.path.join(dump_dir, "recipes_full.json"), encoding="utf-8")).items()}
    disabled = {i for i, st in base.items() if st == "disabled"}
    recs = pa.recipe_records(full, tags, disabled)
    result = {r["id"]: r["result"] for r in rows if r["result"]}
    recs = [(rid, sl, set(os_) | ({result[rid]} if rid in result and result[rid] in base else set())) for rid, sl, os_ in recs]
    # A slot that names no item (a block-only id or a tag that is no item tag: blocks a Roads and Roofs mattock works
    # in the world, mobs an Occultism ritual sacrifices) is not an item ingredient; the walk takes it as given.
    item_set = set(items)

    def item_slot(slot):
        return any((k == "item" and x in item_set) or (k == "tag" and x in tags) or k == "chem" for k, x in slot)
    recs = [(rid, [x for x in sl if item_slot(x)], os_) for rid, sl, os_ in recs]
    # The transitional item of a sequenced assembly is held while the recipe runs, so the recipe reaches it too.
    trans = {rid: j["transitional_item"].get("id") for rid, j in full.items() if isinstance(j.get("transitional_item"), dict)}
    recs = [(rid, sl, os_ | ({trans[rid]} if trans.get(rid) in base else set())) for rid, sl, os_ in recs]
    # ProgressiveStages enforces recipe locks only in crafting-style menus (crafting table and grid, crafter, mechanical
    # crafter, smithing table, stonecutter; research-raw/core-integration.md), so only those recipe types wait for them.
    lock = {}
    for r in rows:
        if r["type"] not in LOCKED_RECIPE_TYPES:
            continue
        st = [s.split(":", 1)[1] for s in (r["recipe_lock"] or "").split("|") if s]
        lock[r["id"]] = max((AGES.index(s) if s in AGES else (99 if s == "disabled" else 0) for s in st), default=0)

    ws = load_world_sources()
    world = World(ws, amap["mods"], set(items), blocks)
    locks = mob_locks()
    loot = ws["loot"]

    def table_items(tid, seen=None):
        seen = set() if seen is None else seen
        if tid in seen or tid not in loot:
            return set()
        seen.add(tid)
        t = loot[tid]
        out = set(t["items"]) | {m for tg in t["tags"] for m in tags.get(tg, [])}
        for ref in t["refs"]:
            out |= table_items(ref, seen)
        return out & set(items)

    # mob Ages: earliest spawn source (biomes, structures, spawners, built), mod Age for mod mobs, then the spawn lock
    entity_age = {}
    ents = set(world.entity_src) | {f"{t.split(':')[0]}:{t.split(':', 1)[1].split('/')[1]}" for t in loot
                                    if t.split(":", 1)[1].startswith("entities/") and t.count("/") >= 1}
    built = {e: (st, needs) for e, st, needs in BUILT_MOBS}
    for e in ents:
        st, why = None, None
        for w, a in world.entity_src.get(e, {}).items():
            if earlier(st, a) != st or st is None:
                st, why = a, w
        ns = e.split(":")[0]
        if ns != "minecraft" and ns in amap["mods"] and ns not in OWN_SPAWNS_OFF and earlier(st, amap["mods"][ns]) != st:
            st, why = amap["mods"][ns], f"mob of {ns} ({amap['mods'][ns]})"
        if e in built and not built[e][1]:
            st, why = earlier(st, built[e][0]), why or "summoned"
        if st is None:
            continue
        if e in locks and later(st, locks[e]) != st:
            st, why = locks[e], f"{why}, spawn lock until {locks[e]}"
        entity_age[e] = (st, why)

    start_src = collections.defaultdict(dict)   # Age -> {item: why}
    transforms = []
    # Byproducts named by a tag (IE sawmill sawdust, ...): every member that is not disabled, once the recipe's main
    # output is reached, from the station's Age (an approximation that can only reach more, never less).
    for rid, j in full.items():
        tags_out = set()
        for key in ("secondaryOutputs", "strippingSecondaries", "secondaries"):
            for e in j.get(key) or []:
                e = e.get("output", e) if isinstance(e, dict) else e
                if isinstance(e, dict) and isinstance(e.get("tag"), str):
                    tags_out.add(e["tag"])
        mains = [o for o in pa._outs({"result": j.get("result")}, []) if o in base]
        members = sorted({m for t in tags_out for m in tags.get(t, []) if base.get(m) not in (None, "disabled")})
        if mains and members:
            st = amap["mods"].get(j.get("type", "x:x").split(":")[0], "dawn")
            transforms.append((AGES.index(st) if st in AGES else 0, (mains[0],), members, f"byproduct of {rid}"))

    def give(st, its, why):
        n = AGES.index(st)
        for i in its:
            if i in base:
                start_src[n].setdefault(i, why)

    # loot tables with an Age: entity tables, structure chests, referenced tables follow their parent
    table_age = {}
    for tid, srcs in world.table_src.items():
        for w, a in srcs.items():
            if tid not in table_age or AGES.index(a) < AGES.index(table_age[tid][0]):
                table_age[tid] = (a, w)
    for tid in loot:
        ns, path = tid.split(":", 1)
        if path.startswith("entities/"):
            e = f"{ns}:{path.split('/')[1]}"
            if e in entity_age:
                table_age[tid] = (entity_age[e][0], f"drop of {e} ({entity_age[e][1]})")
    for tid, (st, why) in sorted(table_age.items()):
        give(st, table_items(tid), why)
    for e, st, needs in BUILT_MOBS:
        if needs:
            for tid in loot:
                if tid == f"{e.split(':')[0]}:entities/{e.split(':')[1]}":
                    transforms.append((AGES.index(st), tuple(needs), sorted(table_items(tid)), f"drop of built {e}"))
    # blocks placed by world generation and structures: their block loot
    for b, (st, why) in world.world_blocks.items():
        ns, path = b.split(":", 1)
        give(st, table_items(f"{ns}:blocks/{path}"), f"block {b} ({why})")
    # ore and deposit blocks of the Age map (TFC tables, [ore_families], [ore_blocks]): TFC places its veins through a
    # placed-feature tag the world scan does not follow, so each block's loot is reached from the Age it becomes visible
    for b, st in am.blocks.items():
        ns, path = b.split(":", 1)
        give(st, table_items(f"{ns}:blocks/{path}"), f"ore vein block {b} ({st})")
    # block loot of every block item; blocks without an item (crops, wall variants) drop what places them
    for tid in loot:
        ns, path = tid.split(":", 1)
        if not path.startswith("blocks/"):
            continue
        b = f"{ns}:{path[len('blocks/'):]}"
        outs = sorted(table_items(tid))
        if not outs:
            continue
        if b in base:
            transforms.append((0, (b,), outs, f"block loot {tid}"))
        else:
            bpath = path[len("blocks/"):]
            for o in outs:
                opath = o.split(":", 1)[1]
                if PLACER.search(opath) or (bpath.replace("wall_", "") == opath and "wall_" in bpath):
                    transforms.append((0, (o,), outs, f"block loot {tid} (placed by {o})"))
    # fishing and piglin bartering
    fish = sorted(table_items("minecraft:gameplay/fishing"))
    transforms.append((0, ("minecraft:fishing_rod",), fish, "fishing (minecraft:gameplay/fishing)"))
    if "minecraft:piglin" in entity_age:
        transforms.append((AGES.index(entity_age["minecraft:piglin"][0]), ("minecraft:gold_ingot",),
                           sorted(table_items("minecraft:gameplay/piglin_bartering")), "piglin bartering"))
    for st, needs, outs in INTERACTIONS:
        n0 = AGES.index(st)
        mobs = [x[7:] for x in needs if x.startswith("entity:")]
        if any(m not in entity_age for m in mobs):
            continue
        for m in mobs:
            n0 = max(n0, AGES.index(entity_age[m][0]))
        its = tuple(x for x in needs if not x.startswith("entity:"))
        transforms.append((n0, its, outs, "interaction " + "+".join(x.split(":")[-1] for x in needs)))
    for a_, b_ in WORLD_CHANGES:
        if a_ in base and b_ in base:
            transforms.append((0, (a_,), [b_], "changes in the world"))
    # an axe strips logs, wood, stems and hyphae in the world (Dawn has the stone axe): minecraft:<w>_log ->
    # minecraft:stripped_<w>_log, tfc:wood/log/<w> -> tfc:wood/stripped_log/<w> (wood classes: the vanilla beams of
    # Decorative Blocks take stripped vanilla logs like their Every Compat copies take stripped TFC logs)
    for i in items:
        ns, path = i.split(":", 1)
        head, _, leaf = path.rpartition("/")
        if head.endswith(("wood/log", "wood/wood")):
            to = f"{ns}:{head[:-3]}stripped_{head[-3:]}/{leaf}" if head.endswith("log") else                 f"{ns}:{head[:-4]}stripped_{head[-4:]}/{leaf}"
        elif leaf.endswith(("_log", "_wood", "_stem", "_hyphae")) and not leaf.startswith("stripped_"):
            to = f"{ns}:{head + '/' if head else ''}stripped_{leaf}"
        else:
            continue
        if i in base and to in base:
            transforms.append((0, (i,), [to], "stripped with an axe"))
    for its, st in CODE_WORLD:
        give(st, its, f"world generation by code ({st})")
    for its, st in CODE_DROPS:
        for i in its:
            give(st or amap["mods"][i.split(":")[0]], [i], "mod drop (code)")
    for i, why in KNOWN_GAPS.items():
        give(base[i], [i], "KNOWN GAP: " + why)
    # fluid buckets: any bucket of a registered fluid, once a bucket exists (fluids count as available, as in the R-
    # checks); filled maps from their empty map
    fluids = set(json.load(open(os.path.join(dump_dir, "registries.json"), encoding="utf-8"))["fluid"])
    buckets = [i for i in items if i.endswith("_bucket") and i[:-len("_bucket")] in fluids]
    transforms.append((0, ("minecraft:bucket",), buckets, "bucket filled with the fluid"))
    for i in items:
        ns, path = i.split(":", 1)
        if path.startswith("filled_") and f"{ns}:{path[7:]}" in base:
            transforms.append((0, (f"{ns}:{path[7:]}",), [i], "map used"))
    # copper blocks weather in the world (exposed, weathered, oxidized) and take wax from a honeycomb
    for i in items:
        ns, path = i.split(":", 1)
        if "copper" not in path or path.startswith(("exposed_", "weathered_", "oxidized_", "waxed_")):
            continue
        chain = [i] + [f"{ns}:{st}_{'copper' if path == 'copper_block' else path}" for st in ("exposed", "weathered", "oxidized")]
        chain = [c for c in chain if c in base]
        for a_, b_ in zip(chain, chain[1:]):
            transforms.append((0, (a_,), [b_], "copper weathering"))
        for c in chain:
            w = f"{ns}:waxed_{c.split(':', 1)[1]}"
            if c == f"{ns}:copper_block" and f"{ns}:waxed_copper_block" in base:
                w = f"{ns}:waxed_copper_block"
            if w in base:
                transforms.append((0, (c, "minecraft:honeycomb"), [w], "waxing"))
    # global loot modifiers: items they name, at the Age of the table they hook (or of their mod)
    for g in ws["glm"]:
        its = [s for s in g["strings"] if s in base]
        if not its:
            continue
        st, why = None, None
        for tid in g["tables"]:
            ns, path = tid.split(":", 1)
            if tid in table_age:
                a = table_age[tid][0]
            elif path.startswith("blocks/") and f"{ns}:{path[7:]}" in world.world_blocks:
                a = world.world_blocks[f"{ns}:{path[7:]}"][0]
            elif path.startswith("blocks/") and f"{ns}:{path[7:]}" in base:
                transforms.append((0, (f"{ns}:{path[7:]}",), its, f"loot modifier {g['id']}"))
                continue
            else:
                continue
            if st is None or AGES.index(a) < AGES.index(st):
                st, why = a, f"loot modifier {g['id']} on {tid}"
        if not g["tables"]:
            st, why = amap["mods"].get(g["id"].split(":")[0]), f"loot modifier {g['id']}"
        if st:
            give(st, its, why)
    for i, (st, why) in quest_rewards().items():
        give(st, [i], why)

    def extra_start(n):
        out = {}
        for k in range(n + 1):
            for i, why in start_src.get(k, {}).items():
                out.setdefault(i, why)
        return out

    extra = {"start": extra_start, "transforms": transforms, "recipe_open": lambda rid, n: lock.get(rid, 0) <= n,
             "stations": COOKING_STATIONS}

    def run_walk():
        _, _, walk_ = pa.make_walk(full, recs, set(items), tags, base, amap, extra)
        first_, via_, counts_ = {}, {}, []
        for n, st in enumerate(AGES):
            v = {}
            have = walk_(n, via=v)[0]
            for i in have:
                if i not in first_:
                    first_[i] = st
                    via_[i] = v.get(i, "?")
            counts_.append(f"  {st}: {len(have)} items reached")
        return walk_, first_, via_, counts_

    # The walk takes every item of the TFC-world namespaces (pa.WORLD_NS) as given by the world from its age_map Age, so
    # a TFC, AFC or Firmalife machine made only from Iron Age inputs (blast furnace, bladed axle, barrel press, tree tap)
    # kept an early Age (fb3-create-tier-sweep 3.3). Such an item loses the shortcut when a recipe makes it, every recipe
    # that makes it has an ingredient first reachable in the Iron Age or later, and that is after its age_map Age.
    # The world and the TFC mechanics the walk does not model (composter, groundcover, pit kiln, knapping, casting,
    # mossy rocks) give Dawn to Bronze Age items only, so an item whose recipes fire earlier keeps the shortcut, and so
    # does an item whose recipes never fire. A recipe that needs the item itself, directly or packed (Firmalife jars:
    # compost -> jar -> compost), is no way to make it. Repeated until no item is added (a late item makes others late).
    makes = collections.defaultdict(list)
    for rid, sl, os_ in recs:
        for o in os_:
            makes[o].append(sl)
    iron_ix = AGES.index("age_2")
    late = set()

    def slot_items(slot):
        return {m for k, x in slot for m in ([x] if k != "tag" else tags.get(x, []))}

    def needs_itself(i, sl):
        for x in sl:
            alts = slot_items(x)
            if i in alts or (alts and all(makes.get(m) and all(any(i in slot_items(y) for y in s2) for s2 in makes[m])
                                          for m in alts)):
                return True
        return False
    while True:
        walk, first, via, counts = run_walk()

        def slot_ix(slot):
            got = [AGES.index(first[m]) for m in slot_items(slot) if m in first]
            return min(got) if got else None
        add = set()
        for i in items:
            if i.split(":")[0] not in pa.WORLD_NS or i in late or base[i] not in AGES or i not in makes:
                continue
            ages = []
            for sl in makes[i]:
                ix = [slot_ix(x) for x in sl]
                if None not in ix and not needs_itself(i, sl):
                    ages.append(max(ix, default=0))
            if ages and min(ages) >= max(iron_ix, AGES.index(base[i]) + 1):
                add.add(i)
        if not add:
            break
        late |= add
        extra["world_ns_late"] = late
    print("\n".join(counts))
    print(f"  world-namespace items made only from Iron Age or later inputs: {len(late)}")
    derived, hidden = {}, []
    for i in items:
        if base[i] == "disabled":
            continue
        if i not in first:
            hidden.append(i)
        elif AGES.index(first[i]) > AGES.index(base[i]):
            derived[i] = first[i]
    return dict(raw=raw, items=items, base=base, base_why=base_why, first=first, via=via, derived=derived,
                hidden=sorted(hidden), entity_age=entity_age, walk=walk, recs=recs, tags=tags, lock=lock,
                transforms=transforms, world_ns_late=sorted(late))


# ------------------------------------------------------------------------------------------------ output
def render(res):
    lines = [MARK_BEGIN + "\n",
             "# Written by dev/derive_ages.py; do not edit by hand (rerun it after changing anything above).\n",
             "# [derived]: items whose first reachable Age is later than the Age the rules above give; the later Age wins.\n",
             "# [derived_hidden]: items no recipe, loot table, world generation, quest or gate reward yields at any Age.\n",
             "# The step that reaches each item is in dev/data/derived_ages.json.\n",
             "[derived]\n"]
    for i in sorted(res["derived"]):
        lines.append(f'"{i}" = "{res["derived"][i]}"\n')
    lines.append("\n[derived_hidden]\nitems = [\n")
    for i in res["hidden"]:
        lines.append(f'    "{i}",\n')
    lines.append("]\n" + MARK_END + "\n")
    return "".join(lines)


def splice(raw, block):
    if MARK_BEGIN in raw:
        a = raw.index(MARK_BEGIN)
        b = raw.index(MARK_END) + len(MARK_END)
        rest = raw[b:]
        return raw[:a] + block.rstrip("\n") + (rest if rest.strip() else "\n")
    return raw.rstrip("\n") + "\n\n# " + "-" * 96 + " derived\n" + block


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump-dir", default=os.path.join(TEST_SERVER, "local", "firmages"))
    ap.add_argument("--mods", default=os.path.join(TEST_SERVER, "mods"))
    ap.add_argument("--scan", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--explain")
    ap.add_argument("--show", type=int, default=0)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if a.scan or not os.path.isfile(WORLD_SOURCES):
        # The vanilla server jar of the server whose mods are scanned: without it every vanilla loot table, biome,
        # feature and structure is missing and vanilla items fall to "never reachable" (a copy outside test-server).
        vanilla = os.path.join(os.path.dirname(os.path.abspath(a.mods)), VANILLA_JAR)
        if not os.path.isfile(vanilla):
            sys.exit(f"vanilla server jar missing: {vanilla}")
        scan(a.mods, vanilla)
    res = derive(a.dump_dir, a.show)
    base, first, via = res["base"], res["first"], res["via"]
    if a.explain:
        i = a.explain
        print(f"{i}: age_map {base.get(i)} ({res['base_why'].get(i)}), first reachable {first.get(i)} via {via.get(i)}")
        return
    final = {i: ("disabled" if i in res["hidden"] else res["derived"].get(i, base[i])) for i in res["items"]}
    out = {i: [base[i], first.get(i), final[i], via.get(i)] for i in res["items"] if final[i] != base[i]}
    block = render(res)
    new = splice(res["raw"], block)
    by_ns = collections.Counter(i.split(":")[0] for i in res["derived"])
    hid_ns = collections.Counter(i.split(":")[0] for i in res["hidden"])
    print(f"items {len(res['items'])}; moved later {len(res['derived'])}; never reachable (hidden) {len(res['hidden'])}")
    print("  moved later by namespace:", dict(by_ns.most_common(25)))
    print("  hidden by namespace:", dict(hid_ns.most_common(25)))
    print("  final per Age:", dict(collections.Counter(final.values())))
    if a.show:
        for i in sorted(res["derived"])[:a.show]:
            print(f"    later {i}: {base[i]} -> {res['derived'][i]} via {via.get(i)}")
        for i in res["hidden"][:a.show]:
            print(f"    hidden {i} ({base[i]}, {res['base_why'][i]})")
    if a.check:
        if new != res["raw"]:
            print("dev/age_map.toml: generated:derived is out of date")
            sys.exit(1)
        print("dev/age_map.toml: generated:derived is up to date")
        return
    write_lines(DERIVED_JSON, {"columns": ["age_map", "first_reachable", "final", "via"],
                               "note": "items whose final Age differs from the age_map rules; --explain ITEM for any other",
                               "items": dict(sorted(out.items()))})
    if new != res["raw"]:
        with open(AGE_MAP, "w", encoding="utf-8", newline="\n") as f:
            f.write(new)
        print("rewrote dev/age_map.toml (generated:derived)")
    else:
        print("dev/age_map.toml unchanged")


if __name__ == "__main__":
    main()
