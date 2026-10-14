#!/usr/bin/env python3
"""Firmament Ages - predict the fa_dump of a KubeJS recipe change without booting a server.

Takes a dump made with every Age unlocked (fa_dump + fa_dump_full: recipes.json, recipes_full.json, item_tags.json,
registries.json), applies the recipe edits listed below the way KubeJS applies them, and writes a new dump directory
that dev/derive_ages.py --dump-dir and dev/poc_analyze.py --dump-dir read like a real one. The edit list mirrors the
KubeJS scripts it names; it is a stand-in until the next boot with fa_dump, after which `derive_ages.py --check` on
the real dump must say "up to date".

Current edit set: deco in TFC woods in the Bronze Age (kubejs/server_scripts/recipes/age_1/bronze_age.js, section
"Deco in TFC woods"): any wooden door or trapdoor makes Macaw's Classic and Cottage Door Print, any smooth rock the
counter top of Handcrafted's and Every Compat's counters. The edit set of a change already in the live dump (the
Create andesite tier sweep, commit 166f2bc) is in the git history.

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
T = lambda t: {"tag": t}   # noqa: E731
I = lambda i: {"item": i}  # noqa: E731

# ---- bronze_age.js "Deco in TFC woods" ---------------------------------------------------------------------------
REPLACE = {
    "mcwdoors:print_oak": {("item", "minecraft:oak_door"): T("minecraft:wooden_doors")},
    "mcwdoors:print_spruce": {("item", "minecraft:spruce_door"): T("minecraft:wooden_trapdoors")},
}
COUNTER_ID = re.compile(r"^(handcrafted:[a-z_]+_counter|everycomp:hc/.+_counter)$")
SMOOTH_ROCK = T("c:stones/smooth")
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
    for rid, m in REPLACE.items():
        if rid not in full:
            log.append(f"missing recipe {rid}")
            continue
        old = json.loads(full[rid])
        new = rewrite(old, m)
        if new != old:
            full[rid] = json.dumps(new, separators=(",", ":"))
            log.append(f"rewrote {rid}")
    n = 0
    for rid in [r for r in full if COUNTER_ID.match(r)]:
        j = json.loads(full[rid])
        hit = False
        for k, v in (j.get("key") or {}).items():
            inner = v.get("ingredient") if v.get("type") == "moonlight:block_type_swap" else v
            if isinstance(inner, dict) and inner.get("item") == "minecraft:calcite":
                j["key"][k] = copy.deepcopy(SMOOTH_ROCK)
                hit = True
        if hit:
            full[rid] = json.dumps(j, separators=(",", ":"))
            n += 1
    log.append(f"counters on smooth rock: {n}")
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
