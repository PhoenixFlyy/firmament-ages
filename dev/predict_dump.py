#!/usr/bin/env python3
"""Firmament Ages - predict the fa_dump of a KubeJS recipe change without booting a server.

Takes a dump made with every Age unlocked (fa_dump + fa_dump_full: recipes.json, recipes_full.json, item_tags.json,
registries.json), applies the recipe edits listed below the way KubeJS applies them, and writes a new dump directory
that dev/derive_ages.py --dump-dir and dev/poc_analyze.py --dump-dir read like a real one. The edit list mirrors the
KubeJS scripts it names; it is a stand-in until the next boot with fa_dump, after which `derive_ages.py --check` on
the real dump must say "up to date".

Current edit set: the f1 gating audit (2026-10-16): naquadah from pure naquadah in the IE arc furnace
(recipes/age_9/singularity_age.js, firmages:arc_furnace/naquadah). Stargate Journey's own blasting recipe is removed
by the same script, so naquadah had no recipe. The edit sets of earlier changes (the sky road with V4A, the adventure
round, deco in TFC woods) are in the git history.

Usage
  python dev/predict_dump.py SRC_DUMP OUT_DIR           write the predicted dump
  python dev/predict_dump.py --sync-locks OUT_DIR [BASELINE_DIR]   after derive_ages.py and gen_stage_locks.py: set
        the item_lock column and the age_items / hidden tags of the predicted dump from the regenerated Age tags, as
        the next boot would (only rows and members of items whose Age changed are touched); with BASELINE_DIR (the
        predicted gate-off dump) a gate-on dump also gets back the baseline recipes whose result left age_items/disabled
"""
import json
import os
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]
PS = "progressivestages:"

GRID_TYPES = ("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "kubejs:shaped", "kubejs:shapeless")
I = lambda i: {"item": i}  # noqa: E731
# recipe id -> JSON as the KubeJS scripts add it
NEW_RECIPES = {
    # recipes/age_9/singularity_age.js
    "firmages:arc_furnace/naquadah": {"type": "immersiveengineering:arc_furnace", "input": I("sgjourney:pure_naquadah"),
                                      "additives": [], "results": [{"id": "sgjourney:naquadah", "count": 1}],
                                      "time": 200, "energy": 102400},
}


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
    # ---- new pack recipes
    for rid, j in NEW_RECIPES.items():
        full[rid] = json.dumps(j, separators=(",", ":"))
        rows[rid] = row_for(rid, j, rows)
    log.append(f"pack recipes added: {len(NEW_RECIPES)}")
    rj["recipes"] = [[r[c] for c in cols] for r in rows.values()]
    dump_json(os.path.join(out, "recipes_full.json"), full)
    dump_json(os.path.join(out, "recipes.json"), rj)
    dump_json(os.path.join(out, "item_tags.json"), tags)
    dump_json(os.path.join(out, "registries.json"), reg)
    print("\n".join(log))
    print(f"recipes {len(full)} ({len(rj['recipes'])} rows); written to {out}")


def sync_locks(out, baseline=None):
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
    back = []
    if baseline:   # the gate drops a recipe while its result is disabled; it returns when the result gets an Age
        brj = load(baseline, "recipes.json")
        bfull = load(baseline, "recipes_full.json")
        have = {r[0] for r in rj["recipes"]}
        for r in brj["recipes"]:
            row = dict(zip(brj["columns"], r))
            if row["id"] not in have and changed.get(row["result"], ("", ""))[0] == "disabled":
                rj["recipes"].append([row.get(c, "") for c in cols])
                full[row["id"]] = bfull[row["id"]]
                back.append(row["id"])
    dump_json(os.path.join(out, "item_tags.json"), tags)
    dump_json(os.path.join(out, "recipes.json"), rj)
    dump_json(os.path.join(out, "recipes_full.json"), full)
    print(f"{len(changed)} items with a new Age; {n} recipe rows got a new item_lock, {len(gone)} dropped (disabled result), "
          f"{len(back)} back from the baseline")


if __name__ == "__main__":
    if len(sys.argv) in (3, 4) and sys.argv[1] == "--sync-locks":
        sync_locks(sys.argv[2], sys.argv[3] if len(sys.argv) == 4 else None)
    elif len(sys.argv) == 3:
        predict(sys.argv[1], sys.argv[2])
    else:
        sys.exit(__doc__)
