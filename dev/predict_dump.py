#!/usr/bin/env python3
"""Firmament Ages - predict the fa_dump of a KubeJS recipe change without booting a server.

Takes a dump made with every Age unlocked (fa_dump + fa_dump_full: recipes.json, recipes_full.json, item_tags.json,
registries.json), applies the recipe edits listed below the way KubeJS applies them, and writes a new dump directory
that dev/derive_ages.py --dump-dir and dev/poc_analyze.py --dump-dir read like a real one. The edit list mirrors the
KubeJS scripts it names; it is a stand-in until the next boot with fa_dump, after which `derive_ages.py --check` on
the real dump must say "up to date".

Current edit set: the adventure round (dev/decisions-while-away.md, 2026-10-14). Removed mods: The Twilight Forest,
L_Ender's Cataclysm with Lionfish API, Bosses of Mass Destruction with CERBON's API, Mowzie's Mobs, Summoning Rituals,
Gateways to Eternity: their registry entries (and the compat items other mods register only with them: Railways
tracks, Supplementaries boats and way signs), their tags and every recipe that names one of their ids. The boss tokens
become Age tokens (tags/*.js): new pack items (startup_scripts/items.js, draconium.js), the sigils and the
Monstrosity altar go, the new recipes of recipes/age_2..age_9 and age_9/chaos_bootstrap.js, the draconium ore ladder.
The edit set of a change already in the live dump (deco in TFC woods, commit 719e8a4) is in the git history.

Usage
  python dev/predict_dump.py SRC_DUMP OUT_DIR           write the predicted dump
  python dev/predict_dump.py --sync-locks OUT_DIR       after derive_ages.py and gen_stage_locks.py: set the item_lock
        column and the age_items / hidden tags of the predicted dump from the regenerated Age tags, as the next boot
        would (only rows and members of items whose Age changed are touched)
"""
import copy
import json
import os
import re
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]
PS = "progressivestages:"

REMOVED_NS = ("twilightforest", "cataclysm", "lionfishapi", "mowziesmobs", "bosses_of_mass_destruction", "cerbons_api",
              "summoningrituals", "gateways")
# ids other mods register only while a removed mod is loaded (Railways compat tracks, Supplementaries compat items)
COMPAT_ID = re.compile(r"^(railways:(track_incomplete_|track_)twilightforest_|supplementaries:(twilightforest|cataclysm)/)")
REMOVED_ID = re.compile(r'"(?:%s):[a-z0-9_/.-]+"' % "|".join(REMOVED_NS))
DROP_RECIPES = {f"firmages:crafting/{s}_sigil" for s in ("frontier", "wild", "forge", "wither", "end", "space", "abyss", "chaos")} \
    | {"firmages:altar/netherite_monstrosity", "firmages:crafting/summoning_altar"}
NEW_ITEMS = ["firmages:frontier_compass", "firmages:frontier_shard", "firmages:incomplete_frontier_relic",
             "firmages:frontier_relic", "firmages:hollow_keystone", "firmages:incomplete_arcane_keystone",
             "firmages:flight_recorder", "firmages:ore/poor_draconium", "firmages:ore/normal_draconium",
             "firmages:ore/rich_draconium", "firmages:ore/small_draconium"]
DRACONIUM_BLOCKS = [f"firmages:ore/{g}_draconium/{r}" for g in ("poor", "normal", "rich") for r in ("granite", "diorite", "gabbro")]
GONE_ITEMS = [f"firmages:{s}_sigil" for s in ("frontier", "wild", "forge", "wither", "end", "space", "abyss", "chaos")]
AGE_TOKENS = {"age_2": ["firmages:frontier_relic"], "age_3": ["firmages:hollow_keystone"],
              "age_4": ["createbigcannons:built_up_steel_cannon_chamber"], "age_5": ["minecraft:nether_star"],
              "age_6": ["ae2:singularity"], "age_7": ["firmages:flight_recorder"],
              "age_8": ["evolvedmekanism:alloy_subatomic"], "age_9": ["draconicevolution:large_chaos_frag"]}

GRID_TYPES = ("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "kubejs:shaped", "kubejs:shapeless")
I = lambda i: {"item": i}  # noqa: E731
T = lambda t: {"tag": t}   # noqa: E731
C = lambda ing: {"ingredient": ing, "consume": True}  # noqa: E731
RELIC, KEY = "firmages:incomplete_frontier_relic", "firmages:incomplete_arcane_keystone"
AW, DR = T("c:ingots/draconium_awakened"), T("c:ingots/draconium")
ROTOR, DIA, MED = I("draconicevolution:reactor_prt_in_rotor"), T("c:gems/diamond"), I("draconicevolution:medium_chaos_frag")


def fusion(result, catalyst, energy, level, ings):
    return {"result": {"id": result, "count": 1}, "catalyst": catalyst, "totalEnergy": energy, "techLevel": level,
            "ingredients": [C(x) for x in ings], "type": "draconicevolution:fusion_crafting"}


# recipe id -> JSON as the KubeJS scripts add or replace it (recipes/age_2..age_9, age_9/chaos_bootstrap.js; the empty
# bucket of recipes/age_5/electric_age.js, whose only source was Twilight Forest loot)
NEW_RECIPES = {
    "firmages:crafting/frontier_compass": {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" W ", "WMW", " W "],
        "key": {"W": T("c:ingots/wrought_iron"),
                "M": [I("tfc:ore/poor_magnetite"), I("tfc:ore/normal_magnetite"), I("tfc:ore/rich_magnetite")]},
        "result": {"id": "firmages:frontier_compass", "count": 1}},
    "firmages:crafting/bucket": {
        "type": "kubejs:shaped", "category": "misc", "pattern": ["S S", " S "], "key": {"S": T("c:sheets/steel")},
        "result": {"id": "minecraft:bucket", "count": 1}},
    "firmages:sequenced_assembly/frontier_relic": {
        "type": "create:sequenced_assembly", "ingredient": I("firmages:frontier_shard"), "transitional_item": {"id": RELIC},
        "sequence": [{"type": "create:deploying", "ingredients": [I(RELIC), T("c:sheets/steel")], "results": [{"id": RELIC}]},
                     {"type": "create:pressing", "ingredients": [I(RELIC)], "results": [{"id": RELIC}]},
                     {"type": "create:deploying", "ingredients": [I(RELIC), T("c:double_sheets/wrought_iron")], "results": [{"id": RELIC}]},
                     {"type": "create:pressing", "ingredients": [I(RELIC)], "results": [{"id": RELIC}]}],
        "results": [{"id": "firmages:frontier_relic"}], "loops": 3},
    "firmages:sequenced_assembly/arcane_keystone": {
        "type": "create:sequenced_assembly", "ingredient": T("firmages:age_token/age_3"), "transitional_item": {"id": KEY},
        "sequence": [{"type": "create:filling", "ingredients": [I(KEY), {"type": "neoforge:single", "fluid": "create_enchantment_industry:experience", "amount": 250}], "results": [{"id": KEY}]},
                     {"type": "create:deploying", "ingredients": [I(KEY), T("c:rods/blaze")], "results": [{"id": KEY}]},
                     {"type": "create:pressing", "ingredients": [I(KEY)], "results": [{"id": KEY}]}],
        "results": [{"id": "firmages:arcane_keystone"}], "loops": 3},
    "firmages:apparatus/wilden_tribute": {
        "type": "ars_nouveau:enchanting_apparatus", "reagent": I("ars_nouveau:source_gem_block"),
        "pedestalItems": [I(x) for x in ("ars_nouveau:wilden_horn", "ars_nouveau:wilden_horn", "ars_nouveau:wilden_wing",
                                         "ars_nouveau:wilden_wing", "ars_nouveau:wilden_spike", "ars_nouveau:wilden_spike",
                                         "occultism:spirit_attuned_crystal", "theurgy:mercury_shard")],
        "result": {"id": "ars_nouveau:wilden_tribute", "count": 1}, "sourceCost": 5000, "keepNbtOfReagent": False},
    "firmages:arc_furnace/nether_star": {
        "type": "immersiveengineering:arc_furnace", "input": I("minecraft:wither_skeleton_skull"),
        "additives": [{"basePredicate": I("minecraft:soul_sand"), "count": 8}, T("c:storage_blocks/gold"),
                      {"basePredicate": T("c:plates/electrum"), "count": 2}],
        "results": [{"id": "minecraft:nether_star", "count": 1}], "time": 1200, "energy": 3276800},
    "firmages:ritual/nether_star": {
        "type": "occultism:ritual", "ritual_type": "occultism:craft", "pentacle_id": "occultism:craft_afrit",
        "activation_item": I("occultism:book_of_binding_bound_afrit"), "duration": 300,
        "ritual_dummy": {"id": "occultism:ritual_dummy/custom_ritual_craft", "count": 1},
        "ingredients": [I("minecraft:wither_skeleton_skull")] * 3 + [I("minecraft:soul_sand")] * 2
        + [I("firmages:attuned_circuit"), I("occultism:afrit_essence"), T("c:storage_blocks/gold")],
        "result": {"id": "minecraft:nether_star", "count": 1}},
    "firmages:fusion/dragon_heart": fusion(
        "draconicevolution:dragon_heart", I("draconicevolution:draconium_block"), 16000000, "wyvern",
        [I("mekanism:pellet_polonium")] * 2 + [I("mekanism:pellet_plutonium")] * 2 + [T("c:plates/ostrum")] * 2
        + [I("draconicevolution:draconium_core")]),
    "draconicevolution:machines/reactor_core": fusion(
        "draconicevolution:reactor_core", I("mekanism:pellet_antimatter"), 64000000, "draconic",
        [AW, DR, AW, DR, AW, DR, AW, I("evolvedmekanism:alloy_subatomic"), I("evolvedmekanism:alloy_subatomic")]),
    "draconicevolution:machines/reactor_stabilizer": fusion(
        "draconicevolution:reactor_stabilizer", I("draconicevolution:reactor_prt_stab_frame"), 16000000, "draconic",
        [I("draconicevolution:draconic_energy_core"), I("draconicevolution:reactor_prt_rotor_full"),
         I("draconicevolution:reactor_prt_focus_ring"), AW, AW, I("draconicevolution:awakened_core"), AW,
         I("mekanism:pellet_antimatter")]),
    "draconicevolution:machines/reactor_injector": fusion(
        "draconicevolution:reactor_injector", I("draconicevolution:wyvern_core"), 16000000, "draconic",
        [DR, ROTOR, ROTOR, ROTOR, DR, DR, T("c:ingots/iron"), ROTOR, T("c:ingots/iron"), DR]),
    "draconicevolution:machines/chaotic_crafting_injector": fusion(
        "draconicevolution:chaotic_crafting_injector", I("draconicevolution:awakened_crafting_injector"), 8000000, "draconic",
        [DIA, DIA, I("draconicevolution:large_chaos_frag"), MED, MED, MED, I("mekanism:pellet_antimatter"), DIA, DIA]),
}


def load(d, n):
    return json.load(open(os.path.join(d, n), encoding="utf-8"))


def dump_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def gone_id(i):
    return isinstance(i, str) and (i.split(":")[0] in REMOVED_NS or COMPAT_ID.match(i) is not None)


def item_tag_age(item):
    tag_dir = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items")
    for st in AGES + ["disabled"]:
        for v in json.load(open(os.path.join(tag_dir, f"{st}.json"), encoding="utf-8"))["values"]:
            if (v["id"] if isinstance(v, dict) else v) == item:
                return st
    return "dawn"


def predict(src, out):
    os.makedirs(out, exist_ok=True)
    full = load(src, "recipes_full.json")
    rj = load(src, "recipes.json")
    cols = rj["columns"]
    rows = {r[0]: dict(zip(cols, r)) for r in rj["recipes"]}
    tags = load(src, "item_tags.json")
    reg = load(src, "registries.json")
    log = []
    # ---- registry: removed mods and their compat ids out, the new pack items and blocks in
    gone_items = set()
    for k, v in reg.items():
        if isinstance(v, list):
            keep = [i for i in v if not gone_id(i)]
            if k == "item":
                gone_items = set(v) - set(keep) | set(GONE_ITEMS)
                keep = [i for i in keep if i not in GONE_ITEMS] + NEW_ITEMS + DRACONIUM_BLOCKS
            if k == "block":
                keep += DRACONIUM_BLOCKS + ["firmages:ore/small_draconium"]
            reg[k] = sorted(set(keep)) if k in ("item", "block") else keep
    log.append(f"registry: {len(gone_items)} items out, {len(NEW_ITEMS) + len(DRACONIUM_BLOCKS)} in")
    # ---- recipes: every recipe that names a removed id, the sigils and the altar out
    drop = {rid for rid, j in full.items() if gone_id(rid) or REMOVED_ID.search(j)
            or any(f'"{i}"' in j for i in gone_items if not i.split(":")[0] in REMOVED_NS)} | (DROP_RECIPES & set(full))
    for rid in drop:
        full.pop(rid, None)
        rows.pop(rid, None)
    log.append(f"recipes dropped: {len(drop)}")
    # ---- boss tokens -> Age tokens; the Djinni ritual takes the Wilden Tribute itself
    n = 0
    for rid, j in list(full.items()):
        if "firmages:boss_token/" in j:
            if rid == "firmages:ritual/arcane_keystone":
                j = j.replace('{"tag":"firmages:boss_token/age_3"}', '{"item":"ars_nouveau:wilden_tribute"}')
            full[rid] = j.replace("firmages:boss_token/", "firmages:age_token/")
            n += 1
    log.append(f"recipes moved to the Age tokens: {n}")
    # ---- new and replaced recipes
    for rid, j in NEW_RECIPES.items():
        full[rid] = json.dumps(j, separators=(",", ":"))
        res = j.get("result") or (j.get("results") or [{}])[0]
        out_id = res.get("id")
        rtype = "minecraft:crafting" if j["type"] in GRID_TYPES else j["type"]   # the dump lists the recipe type
        row = rows.get(rid) or {"id": rid, "type": rtype, "result": out_id, "count": res.get("count", 1),
                                "recipe_lock": PS + "age_0" if rid.startswith("firmages:") else "", "output_lock": "",
                                "item_lock": ""}
        st = item_tag_age(out_id)
        row["item_lock"] = "" if st == "dawn" else PS + st
        rows[rid] = row
    # Almost Unified dropped Create's copper ingot -> nuggets recipe as a duplicate of the Twilight Forest's; without the
    # Twilight Forest Create's own recipe loads again (same shape, read from the Create 6.0.10 jar)
    rid = "create:crafting/materials/copper_nugget"
    full[rid] = json.dumps({"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [T("c:ingots/copper")],
                            "result": {"id": "create:copper_nugget", "count": 9}}, separators=(",", ":"))
    rows[rid] = dict(rows["create:crafting/materials/copper_ingot"], id=rid, result="create:copper_nugget", count=9)
    st = item_tag_age("create:copper_nugget")
    rows[rid]["item_lock"] = "" if st == "dawn" else PS + st
    # the draconium ore ladder (recipes/age_6/ore_ladder.js): the osmium ladder with the draconium pieces and More
    # Mekanism Processing's draconium clumps, shards and slurry
    swap = {"native_osmium": "draconium", "mekanism:clump_osmium": "moremekanismprocessing:clump_draconium",
            "mekanism:shard_osmium": "moremekanismprocessing:shard_draconium",
            "mekanism:dirty_osmium": "moremekanismprocessing:dirty_draconium"}
    for step in ("purifying", "injecting", "dissolution"):
        for g in ("small", "poor", "normal", "rich"):
            src_id = f"firmages:{step}/native_osmium_{g}"
            j = full[src_id]
            for a, b in swap.items():
                j = j.replace(a, b)
            rid = f"firmages:{step}/draconium_{g}"
            full[rid] = j
            row = dict(rows[src_id], id=rid)
            out_id = json.loads(j)["output"].get("id")
            row["result"] = out_id
            if out_id:
                st = item_tag_age(out_id)
                row["item_lock"] = "" if st == "dawn" else PS + st
            rows[rid] = row
    log.append(f"recipes added or replaced: {len(NEW_RECIPES) + 13}")
    # ---- tags
    for t in list(tags):
        if gone_id(t) or t.startswith("firmages:boss_token/"):
            del tags[t]
            continue
        tags[t] = [i for i in tags[t] if i not in gone_items]
    for st, members in AGE_TOKENS.items():
        tags[f"firmages:age_token/{st}"] = list(members)
    tags["tfc:ore_pieces"] = sorted(set(tags.get("tfc:ore_pieces", [])) | {f"firmages:ore/{g}_draconium" for g in ("poor", "normal", "rich")})
    tags["tfc:small_ore_pieces"] = sorted(set(tags.get("tfc:small_ore_pieces", [])) | {"firmages:ore/small_draconium"})
    rj["recipes"] = [[r[c] for c in cols] for r in rows.values()]
    dump_json(os.path.join(out, "recipes_full.json"), full)
    dump_json(os.path.join(out, "recipes.json"), rj)
    dump_json(os.path.join(out, "item_tags.json"), tags)
    dump_json(os.path.join(out, "registries.json"), reg)
    print("\n".join(log))
    print(f"recipes {len(full)} ({len(rj['recipes'])} rows); written to {out}")

def sync_locks(out):
    """The item locks and Age tags the next boot would dump after the regeneration (items whose Age changed only)."""
    tag_dir = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items")
    age = {}
    for st in AGES + ["disabled"]:
        for v in json.load(open(os.path.join(tag_dir, f"{st}.json"), encoding="utf-8"))["values"]:
            age[v["id"] if isinstance(v, dict) else v] = st
    tags = load(out, "item_tags.json")
    old = {i: st for st in AGES + ["disabled"] for i in tags.get(f"firmages:age_items/{st}", [])}
    changed = {i: (old.get(i), st) for i, st in age.items() if old.get(i) != st}
    for i, (a, b) in changed.items():
        if a:
            tags[f"firmages:age_items/{a}"].remove(i)
        tags.setdefault(f"firmages:age_items/{b}", []).append(i)
    hidden_src = os.path.join(REPO, "kubejs", "data", "c", "tags", "item", "hidden_from_recipe_viewers.json")
    hid_repo = {v["id"] if isinstance(v, dict) else v for v in json.load(open(hidden_src, encoding="utf-8"))["values"]}
    hid = set(tags.get("c:hidden_from_recipe_viewers", []))
    for i, (a, b) in changed.items():
        if b == "disabled" and i in hid_repo:
            hid.add(i)
        elif a == "disabled" and i not in hid_repo:
            hid.discard(i)
    tags["c:hidden_from_recipe_viewers"] = sorted(hid)
    rj = load(out, "recipes.json")
    cols = rj["columns"]
    ri, li = cols.index("result"), cols.index("item_lock")
    n, gone = 0, set()
    for r in rj["recipes"]:
        if r[ri] in changed:
            st = changed[r[ri]][1]
            if st == "disabled":   # the firmages-core gate drops a recipe whose result is disabled
                gone.add(r[0])
                continue
            r[li] = "" if st == "dawn" else PS + st
            n += 1
    rj["recipes"] = [r for r in rj["recipes"] if r[0] not in gone]
    full = load(out, "recipes_full.json")
    for rid in gone:
        full.pop(rid, None)
    dump_json(os.path.join(out, "item_tags.json"), tags)
    dump_json(os.path.join(out, "recipes.json"), rj)
    dump_json(os.path.join(out, "recipes_full.json"), full)
    print(f"{len(changed)} items with a new Age; {n} recipe rows got a new item_lock, {len(gone)} dropped (disabled result)")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--sync-locks":
        sync_locks(sys.argv[2])
    elif len(sys.argv) == 3:
        predict(sys.argv[1], sys.argv[2])
    else:
        sys.exit(__doc__)
