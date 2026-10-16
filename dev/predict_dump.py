#!/usr/bin/env python3
"""Firmament Ages - predict the fa_dump of a KubeJS recipe change without booting a server.

Takes a dump made with every Age unlocked (fa_dump + fa_dump_full: recipes.json, recipes_full.json, item_tags.json,
registries.json), applies the recipe edits listed below the way KubeJS applies them, and writes a new dump directory
that dev/derive_ages.py --dump-dir and dev/poc_analyze.py --dump-dir read like a real one. The edit list mirrors the
KubeJS scripts it names; it is a stand-in until the next boot with fa_dump, after which `derive_ages.py --check` on
the real dump must say "up to date".

Current edit set: the sky road with V4A (firmages-core SPEC 24 to 31, M15), on a dump of a branch that has the Lens
Crown (M12) but not yet the Sky Harvest (M11), the Drifting Isles (M13) and the Launch Complex (M14). It adds their
items, blocks and aurora light as the merged jar registers them, their pack recipes (the meteoric rod of
recipes/age_2/sky_metals.js and recipes/age_4/sky_harvest.js of M11; recipes/age_7/launch_complex.js), the 22 starforge
recipes of the mod jar (their neoforge:conditions hold once every sky material exists), the Lens Crown parts
(recipes/age_3/lens_crown.js), the Sky Anchor (recipes/age_7/space_age.js) and the eight V4A changes of SPEC 27.8
(recipes/age_3..age_9; startup_scripts/sky.js switches them on). The edit sets of earlier changes (the adventure round,
deco in TFC woods) are in the git history.

Usage
  python dev/predict_dump.py SRC_DUMP OUT_DIR           write the predicted dump
  python dev/predict_dump.py --sync-locks OUT_DIR       after derive_ages.py and gen_stage_locks.py: set the item_lock
        column and the age_items / hidden tags of the predicted dump from the regenerated Age tags, as the next boot
        would (only rows and members of items whose Age changed are touched)
"""
import copy
import json
import os
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]
PS = "progressivestages:"

GRID_TYPES = ("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "kubejs:shaped", "kubejs:shapeless")
I = lambda i: {"item": i}  # noqa: E731
T = lambda t: {"tag": t}   # noqa: E731
F = "firmages:"
MOD_DATA = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "data", "firmages")
# Items of the sibling milestones as the merged jar registers them (M11 Sky Harvest, M13 Drifting Isles, M14 Launch
# Complex) with their blocks; the aurora light is a fluid.
SKY_ITEMS = [F + i for i in ("storm_rod", "storm_glass", "aurora_net", "aurora_light_bucket",              # M11
                             "skystone", "firmament_crystal",                                             # M13
                             "launch_control", "probe_cradle", "launch_port", "probe", "sampler_module",  # M14
                             "deep_space_module", "voidglass", "deep_space_sample")]
SKY_BLOCKS = [F + b for b in ("storm_rod", "storm_glass", "aurora_net", "aurora_light", "skystone",
                              "firmament_crystal_cluster", "firmament_lode", "launch_control", "probe_cradle", "launch_port")]
SKY_FLUIDS = [F + "aurora_light", F + "flowing_aurora_light"]
ALLOYS = {"age_3": F + "starlit_iron_ingot", "age_4": F + "cloudsteel_ingot", "age_5": F + "aurora_steel_ingot",
          "age_6": F + "halo_steel_ingot", "age_7": F + "void_steel_ingot", "age_8": F + "quasar_alloy_ingot",
          "age_9": F + "firmament_alloy_ingot"}


def shaped(result, pattern, key, count=1):
    return {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: (T(v[1:]) if v.startswith("#") else I(v)) for k, v in key.items()},
            "result": {"id": result, "count": count}}


# recipe id -> JSON as the KubeJS scripts add it
NEW_RECIPES = {
    # M11: recipes/age_2/sky_metals.js (the meteoric rod back from [hidden]) and recipes/age_4/sky_harvest.js
    "firmages:anvil/meteoric_iron_rod": {"type": "tfc:anvil", "input": T("c:ingots/meteoric_iron"), "tier": 3,
                                         "rules": ["bend_last", "draw_second_last", "draw_third_last"],
                                         "result": {"id": F + "meteoric_iron_rod", "count": 2}},
    "firmages:crafting/storm_rod": shaped(F + "storm_rod", [" M ", "R R", "CCC"],
                                          {"M": "#c:rods/meteoric_iron", "R": "#c:rods/wrought_iron", "C": "#c:sheets/copper"}),
    "firmages:crafting/aurora_net": shaped(F + "aurora_net", ["SWS", "MGM", "SWS"],
                                           {"S": "#c:rods/steel", "W": "immersiveengineering:wirecoil_copper",
                                            "M": "#c:sheets/meteoric_iron", "G": F + "storm_glass"}),
    # recipes/age_3/lens_crown.js
    "firmages:sandpaper_polishing/lens_blank": {"type": "create:sandpaper_polishing", "ingredients": [I(F + "storm_glass")],
                                                "results": [{"id": F + "lens_blank"}]},
    **{f"firmages:crafting/lens/{lens}": shaped(F + "lens/" + lens, [" S ", "SBS", " S "], {"S": "#" + sheet, "B": F + "lens_blank"})
       for lens, sheet in (("stone", "c:sheets/copper"), ("bronze", "c:sheets/bronze"), ("iron", "c:sheets/wrought_iron"))},
    "firmages:crafting/lens_mount": shaped(F + "lens_mount", [" M ", "MPM", " M "],
                                           {"M": "#c:ingots/meteoric_iron", "P": "create:precision_mechanism"}),
    "firmages:crafting/star_anvil": shaped(F + "star_anvil", ["MGM", "GAG", "MGM"],
                                           {"M": "#c:ingots/meteoric_iron", "G": F + "storm_glass", "A": "tfc:metal/anvil/steel"}),
    # recipes/age_7/space_age.js
    "firmages:crafting/sky_anchor": shaped(F + "sky_anchor", ["PVP", "SWT", "PBP"],
                                           {"P": "#c:plates/desh", "V": F + "voidglass", "S": "#c:plates/steel",
                                            "W": F + "wayshrine_stone", "T": ALLOYS["age_7"], "B": "#c:storage_blocks/desh"}),
    # recipes/age_7/launch_complex.js
    "firmages:crafting/launch_control": shaped(F + "launch_control", ["DCD", "SKS", "DED"],
                                               {"D": "#c:plates/desh", "C": "mekanism:elite_control_circuit", "S": "#c:plates/steel",
                                                "K": "mekanism:steel_casing", "E": "immersiveengineering:component_electronic_adv"}),
    "firmages:crafting/probe_cradle": shaped(F + "probe_cradle", ["DSD", "SHS", "DSD"],
                                             {"D": "#c:plates/desh", "S": "#c:plates/steel", "H": "immersiveengineering:heavy_engineering"}),
    "firmages:crafting/launch_port": shaped(F + "launch_port", ["DPD", "PTP", "DPD"],
                                            {"D": "#c:plates/desh", "P": "immersiveengineering:fluid_pipe", "T": "mekanism:basic_fluid_tank"}),
    "firmages:crafting/probe": shaped(F + "probe", [" N ", "DTD", "FEF"],
                                      {"N": "ad_astra:rocket_nose_cone", "D": "#c:plates/desh", "T": "ad_astra:desh_tank",
                                       "F": "ad_astra:rocket_fin", "E": "ad_astra:desh_engine"}),
    "firmages:crafting/sampler_module": shaped(F + "sampler_module", [" C ", "DAD", " H "],
                                               {"C": "mekanism:advanced_control_circuit", "D": "#c:plates/desh",
                                                "A": "#firmages:celestial_alloy/age_6", "H": "minecraft:hopper"}),
    "firmages:crafting/deep_space_module": shaped(F + "deep_space_module", [" C ", "PAP", " H "],
                                                  {"C": "mekanism:elite_control_circuit", "P": "#c:plates/calorite",
                                                   "A": "#firmages:celestial_alloy/age_7", "H": "minecraft:hopper"}),
}


def replace_first(o, old, new):
    """o with the first value equal to `old` replaced by `new` (depth first); returns (o, replaced)."""
    if o == old:
        return copy.deepcopy(new), True
    if isinstance(o, dict):
        for k in o:
            o[k], done = replace_first(o[k], old, new)
            if done:
                return o, True
    elif isinstance(o, list):
        for i, v in enumerate(o):
            o[i], done = replace_first(v, old, new)
            if done:
                return o, True
    return o, False


def v4a(full):
    """The eight V4A changes of SPEC 27.8 on the dumped JSON (recipes/age_3..age_9). Returns the ids changed."""
    done = []

    def edit(rid, fn):
        j = json.loads(full[rid])
        fn(j)
        full[rid] = json.dumps(j, separators=(",", ":"))
        done.append(rid)

    def swap(old, new):
        def fn(j):
            if not replace_first(j, old, new)[1]:
                sys.exit(f"V4A: {old} not found")
        return fn

    def keystone(j):   # a Starlit Iron deployer before the press, in each of the three loops
        seq = j["sequence"]
        press = next(i for i, st in enumerate(seq) if st.get("type") == "create:pressing")
        seq.insert(press, {"type": "create:deploying",
                           "ingredients": [I("firmages:incomplete_arcane_keystone"), I(ALLOYS["age_3"])],
                           "results": [{"id": "firmages:incomplete_arcane_keystone"}]})

    def grid(pattern, key_add):
        def fn(j):
            j["pattern"] = pattern
            j["key"].update(key_add)
        return fn

    def humming(j):
        adds = j["additives"]
        i = next(i for i, a in enumerate(adds) if "red_steel" in json.dumps(a))
        adds[i] = I(ALLOYS["age_5"])

    edit("firmages:sequenced_assembly/arcane_keystone", keystone)
    edit("firmages:ritual/arcane_keystone", swap(I("tfc:metal/ingot/steel"), I(ALLOYS["age_3"])))
    edit("firmages:crafting/pressure_core", grid(["AHA", "DGD", " T "], {"A": I(ALLOYS["age_4"])}))
    edit("firmages:arc_furnace/humming_core", humming)
    edit("firmages:crafting/data_matrix", grid(["ACA", "CSC", "BTB"], {"A": I(ALLOYS["age_6"])}))
    edit("firmages:fusion/star_chart", swap(T("c:plates/desh"), I(ALLOYS["age_7"])))
    edit("firmages:fusion/quantum_core", swap(T("c:plates/calorite"), I(ALLOYS["age_8"])))
    edit("firmages:fusion/ultimate_singularity", swap(I("evolvedmekanism:alloy_singular"), I(ALLOYS["age_9"])))
    return done


def load(d, n):
    return json.load(open(os.path.join(d, n), encoding="utf-8"))


def dump_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def item_tag_age(item):
    tag_dir = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items")
    for st in AGES + ["disabled"]:
        for v in json.load(open(os.path.join(tag_dir, f"{st}.json"), encoding="utf-8"))["values"]:
            if (v["id"] if isinstance(v, dict) else v) == item:
                return st
    return "dawn"


def row_for(rid, j, rows):
    res = j.get("result") or (j.get("results") or [{}])[0]
    out_id = res.get("id")
    rtype = "minecraft:crafting" if j["type"] in GRID_TYPES else j["type"]   # the dump lists the recipe type
    row = rows.get(rid) or {"id": rid, "type": rtype, "result": out_id, "count": res.get("count", 1),
                            "recipe_lock": "", "output_lock": "", "item_lock": ""}
    st = item_tag_age(out_id)
    row["item_lock"] = "" if st == "dawn" else PS + st
    return row


def predict(src, out):
    os.makedirs(out, exist_ok=True)
    full = load(src, "recipes_full.json")
    rj = load(src, "recipes.json")
    cols = rj["columns"]
    rows = {r[0]: dict(zip(cols, r)) for r in rj["recipes"]}
    tags = load(src, "item_tags.json")
    reg = load(src, "registries.json")
    log = []
    # ---- registry: the items, blocks and fluid of M11, M13 and M14
    reg["item"] = sorted(set(reg["item"]) | set(SKY_ITEMS))
    if isinstance(reg.get("block"), list):
        reg["block"] = sorted(set(reg["block"]) | set(SKY_BLOCKS))
    if isinstance(reg.get("fluid"), list):
        reg["fluid"] = sorted(set(reg["fluid"]) | set(SKY_FLUIDS))
    log.append(f"registry: {len(SKY_ITEMS)} items, {len(SKY_BLOCKS)} blocks, {len(SKY_FLUIDS)} fluids in")
    # ---- the 22 starforge recipes of the jar (their conditions hold once every sky material exists)
    sdir = os.path.join(MOD_DATA, "recipe", "starforge")
    n = 0
    for f in sorted(os.listdir(sdir)):
        j = json.load(open(os.path.join(sdir, f), encoding="utf-8"))
        j.pop("neoforge:conditions", None)
        rid = "firmages:starforge/" + f[:-5]
        full[rid] = json.dumps(j, separators=(",", ":"))
        rows[rid] = row_for(rid, j, rows)
        n += 1
    log.append(f"starforge recipes: {n}")
    # ---- new pack recipes and the V4A changes
    for rid, j in NEW_RECIPES.items():
        full[rid] = json.dumps(j, separators=(",", ":"))
        rows[rid] = row_for(rid, j, rows)
    log.append(f"pack recipes added: {len(NEW_RECIPES)}")
    log.append("V4A: " + ", ".join(v4a(full)))
    # ---- tags the sibling milestones fill
    rods = tags.setdefault("c:rods/meteoric_iron", [])
    if F + "meteoric_iron_rod" not in rods:
        rods.append(F + "meteoric_iron_rod")
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
