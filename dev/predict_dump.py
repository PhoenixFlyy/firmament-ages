#!/usr/bin/env python3
"""Firmament Ages - predict the fa_dump of a KubeJS recipe change without booting a server.

Takes a dump made with every Age unlocked (fa_dump + fa_dump_full: recipes.json, recipes_full.json, item_tags.json,
registries.json), applies the recipe edits listed below the way KubeJS applies them, and writes a new dump directory
that dev/derive_ages.py --dump-dir and dev/poc_analyze.py --dump-dir read like a real one. The edit list mirrors the
KubeJS scripts it names; it is a stand-in until the next boot with fa_dump, after which `derive_ages.py --check` on
the real dump must say "up to date".

Current edit set: the Create andesite tier sweep of 2026-10-14 (kubejs/server_scripts/recipes/age_1/bronze_age.js,
kubejs/server_scripts/tags/unification.js; Modpack-Planung/research-raw/fb3-create-tier-sweep.md).

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
T = lambda t: {"tag": t}   # noqa: E731
I = lambda i: {"item": i}  # noqa: E731

# ---- tags/unification.js: tags added by the change ---------------------------------------------------------------
NEW_TAGS = {
    "firmages:rods/any_bronze": ["tfc:metal/rod/bronze", "tfc:metal/rod/bismuth_bronze", "tfc:metal/rod/black_bronze"],
    "firmages:trapdoors/any_bronze": ["tfc:metal/trapdoor/bronze", "tfc:metal/trapdoor/bismuth_bronze",
                                      "tfc:metal/trapdoor/black_bronze"],
}
# ---- bronze_age.js andesiteTier: replaceInput on fixed ids ----------------------------------------------------------
TIER_REPL = {
    ("tag", "c:plates/iron"): T("firmages:plates/any_bronze"),
    ("tag", "c:ingots/iron"): T("firmages:ingots/any_bronze"),
    ("tag", "c:storage_blocks/iron"): T("firmages:storage_blocks/any_bronze"),
    ("tag", "c:nuggets/iron"): T("firmages:rods/any_bronze"),
    ("item", "minecraft:iron_trapdoor"): T("firmages:trapdoors/any_bronze"),
    ("item", "minecraft:compass"): T("firmages:plates/any_bronze"),
    ("tag", "c:slimeballs"): I("tfc:glue"),
    ("tag", "c:barrels/wooden"): T("tfc:barrels"),
}
TIER_IDS = [
    "create:crafting/kinetics/empty_blaze_burner", "create:crafting/kinetics/item_vault",
    "create:crafting/kinetics/mechanical_drill", "create:crafting/kinetics/mechanical_press",
    "create:crafting/kinetics/mechanical_saw", "create:crafting/kinetics/metal_bracket",
    "create:crafting/kinetics/metal_girder", "create:crafting/kinetics/propeller", "create:crafting/kinetics/super_glue",
    "create:crafting/kinetics/whisk", "create:crafting/logistics/packager", "create:crafting/logistics/redstone_contact",
    "create:industrial_iron_block_from_ingots_iron_stonecutting", "create:weathered_iron_block_from_ingots_iron_stonecutting",
    "create:crafting/kinetics/mechanical_harvester", "create:crafting/kinetics/mechanical_plough",
    "create:crafting/curiosities/minecart_coupling", "create:crafting/kinetics/filter",
    "create:crafting/kinetics/encased_chain_drive", "create:crafting/logistics/item_hatch",
    "create:crafting/kinetics/sticker", "create:crafting/kinetics/sticky_mechanical_piston",
    "create:crafting/kinetics/speedometer",
    "create_connected:crafting/kinetics/freewheel_clutch", "create_connected:crafting/kinetics/centrifugal_clutch",
    "create_connected:crafting/kinetics/item_silo",
]
# ---- bronze_age.js: TFCreate-only recipes copied under firmages: ids (iron as bronze), then the tfcreate ids removed
COPY_TFCREATE = ["chute", "rope_pulley", "goggles", "copper_valve_handle", "brown_toolbox", "desk_bell", "weighted_ejector"]
REMOVE = [f"tfcreate:{x}" for x in COPY_TFCREATE + [
    "item_vault", "metal_bracket", "super_glue", "filter", "item_hatch", "sticker", "sticky_mechanical_piston",
    "mechanical_press", "mechanical_drill", "mechanical_saw", "encased_chain_drive", "encased_chain_drive_from_steel",
    "redstone_contact", "anvil/propeller", "anvil/whisk", "anvil/drill_head", "anvil/saw_blade"]]
FIRMAGES_RECIPE_LOCK = PS + "age_0"   # [recipes] default of the firmages namespace (fa_dump of firmages:crafting/*)
# ---- bronze_age.js: other early mods -----------------------------------------------------------------------------
SEED_DRILL_WOODS = ("acacia araucaria ash aspen baobab beech birch blackwood chestnut cypress douglas_fir eucalyptus fig "
                    "ginkgo hevea hickory ipe ironwood kapok mahoe mahogany mangrove maple oak palm pine rosewood sequoia "
                    "spruce sycamore teak tualang white_cedar willow").split()
OTHER = {
    "totemic:totem_whittling_knife": {("tag", "c:ingots/iron"): T("firmages:ingots/any_bronze")},
    "totemic:totem_torch": {("item", "minecraft:torch"): I("tfc:torch")},
    "trashcans:liquid_trash_can": {("tag", "c:buckets/empty"): I("tfc:wooden_bucket")},
    "framedblocks:framing_saw": {("tag", "c:ingots/iron"): T("c:ingots/copper")},
    "sophisticatedbackpacks:upgrade_base": {("tag", "c:ingots/iron"): T("firmages:ingots/any_bronze")},
    **{f"tfcastikorcarts:crafting/seed_drill/{w}": {("item", "minecraft:hopper"): T("firmages:plates/any_bronze")}
       for w in SEED_DRILL_WOODS},
}
OUT_KEYS = ("result", "results")


def rewrite(o, m):
    """replaceInput: swap every ingredient that is exactly one of the matched items or tags; outputs stay."""
    if isinstance(o, dict):
        for k in ("tag", "item"):
            if set(o) == {k} and (k, o[k]) in m:
                return copy.deepcopy(m[(k, o[k])])
        return {k: (v if k in OUT_KEYS else rewrite(v, m)) for k, v in o.items()}
    if isinstance(o, list):
        return [rewrite(v, m) for v in o]
    return o


def load(d, n):
    return json.load(open(os.path.join(d, n), encoding="utf-8"))


def dump_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def predict(src, out):
    os.makedirs(out, exist_ok=True)
    full = load(src, "recipes_full.json")
    rj = load(src, "recipes.json")
    cols = rj["columns"]
    rows = {r[0]: dict(zip(cols, r)) for r in rj["recipes"]}
    tags = load(src, "item_tags.json")
    log = []
    for t, mem in NEW_TAGS.items():
        tags[t] = list(mem)
    edits = [(rid, TIER_REPL) for rid in TIER_IDS] + list(OTHER.items())
    for rid, m in edits:
        if rid not in full:
            log.append(f"missing recipe {rid}")
            continue
        old = json.loads(full[rid])
        new = rewrite(old, m)
        if new != old:
            full[rid] = json.dumps(new, separators=(",", ":"))
            log.append(f"rewrote {rid}")
    for x in COPY_TFCREATE:
        src_id, new_id = f"tfcreate:{x}", f"firmages:crafting/create/{x}"
        j = rewrite(json.loads(full[src_id]), TIER_REPL)
        full[new_id] = json.dumps(j, separators=(",", ":"))
        row = dict(rows[src_id], id=new_id, recipe_lock=FIRMAGES_RECIPE_LOCK)
        rows[new_id] = row
        log.append(f"added {new_id} (copy of {src_id})")
    for rid in REMOVE:
        if full.pop(rid, None) is None:
            log.append(f"missing recipe {rid} (remove)")
        rows.pop(rid, None)
    rj["recipes"] = [[r[c] for c in cols] for r in rows.values()]
    dump_json(os.path.join(out, "recipes_full.json"), full)
    dump_json(os.path.join(out, "recipes.json"), rj)
    dump_json(os.path.join(out, "item_tags.json"), tags)
    dump_json(os.path.join(out, "registries.json"), load(src, "registries.json"))
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
