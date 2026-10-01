#!/usr/bin/env python3
"""Check recipe gating and unification from the /fa_dump files (kubejs/server_scripts/debug/dump.js).

Reads <server>/local/firmages/recipes.json and item_tags.json and prints one PASS/FAIL line per check,
followed by the evidence. Run "fa_dump" on the server first (console or: python dev/rcon.py fa_dump).
The content checks of the Arcane and Industrial Age ("C-" lines) also need recipes_full.json from "fa_dump_full"
(every recipe as its serializer JSON) and read <server>/logs/latest.log for recipe parse errors.
The "R-" checks walk the recipe graph once per goal Age from the Dawn start set (world items, mob-ladder drops, the
Age's boss drops, ore veins, the planets) and report every goal ingredient (Steel Heart, Arcane Keystone, Pressure
Core, Humming Core, Data Matrix, Star Chart, Quantum Core) not reached at its Age. R-0 reports recipes that still need
a vanilla station block (crafting table, furnace, ...) that a TFC world cannot make.

The "G-" checks test the firmages-core recipe gate (M2) against the Age tags: the dump records the Ages that were
unlocked ("unlocked", written by fa_dump), and no recipe may make an item of a locked Age or of age_items/disabled.
With --baseline <dir> (the fa_dump files of a world with every Age unlocked) they also check that every baseline
recipe whose outputs are all unlocked is present, so the gate drops nothing more. G-3 (dump with every Age unlocked)
finds recipes whose chance byproduct is later than their main output and station and checks that
kubejs/server_scripts/recipes/byproducts.js strips it; G-4 compares the recipe types without a detected output in
<server>/logs/firmages-recipe-audit.txt with dev/data/accepted_undetected.json.
The ProgressiveStages recipe locks stay in the pack as a second layer; the A- and C- checks read them and need the
whole recipe set, so they run only on a dump where dawn to age_9 are unlocked (after "firmages ages simulate grant").

Usage:  python dev/poc_analyze.py [--server-dir test-server] [--dump-dir DIR] [--baseline DIR] [--show 40]
Exit code: 0 = all checks pass, 1 = at least one FAIL.
"""
import argparse
import collections
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]
PS = "progressivestages:"
AGE_TAGS = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items")
HIDDEN_ITEMS_RE = re.compile(r"HIDDEN_ITEMS:\s*\[(.*?)\]", re.S)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server-dir", default=os.path.join(REPO, "test-server"))
    ap.add_argument("--dump-dir", help="fa_dump files to read (default <server>/local/firmages)")
    ap.add_argument("--baseline", help="fa_dump files of a world with every Age unlocked (G-2)")
    ap.add_argument("--show", type=int, default=40, help="max evidence lines per check")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    base = a.dump_dir or os.path.join(a.server_dir, "local", "firmages")
    dump = json.load(open(os.path.join(base, "recipes.json"), encoding="utf-8"))
    tags = json.load(open(os.path.join(base, "item_tags.json"), encoding="utf-8"))
    rows = [dict(zip(dump["columns"], r)) for r in dump["recipes"]]
    lock_cols = ("recipe_lock", "output_lock", "item_lock")
    for r in rows:  # /fa_dump joins several gating stages with "|"
        r["locks"] = {s for c in lock_cols for s in (r[c] or "").split("|") if s}
    item_age = {}
    for st in AGES + ["disabled"]:
        with open(os.path.join(AGE_TAGS, f"{st}.json"), encoding="utf-8") as f:
            for v in json.load(f)["values"]:
                item_age[v["id"] if isinstance(v, dict) else v] = st

    def owned(stage):  # the cumulative Age stages of a team that has just reached `stage`
        return {PS + s for s in AGES[: AGES.index(stage) + 1]}

    def open_in(stage, recipes):
        have = owned(stage)
        return [r for r in recipes if r["locks"] <= have]
    by_id = {r["id"]: r for r in rows}
    failed = []

    def check(name, ok, evidence=()):
        print(f"[{'PASS' if ok else 'FAIL'}] {name}")
        for line in list(evidence)[:a.show]:
            print(f"    {line}")
        if not ok:
            failed.append(name)

    def ids(pattern, field="id"):
        rx = re.compile(pattern)
        return sorted(r["id"] for r in rows if rx.search(r[field] or ""))

    print(f"{len(rows)} recipes, {len(tags)} item tags")

    unlocked = dump.get("unlocked")
    if unlocked is not None:
        gate_checks(base, a.baseline, rows, tags, item_age, set(unlocked), check)
        fullp = os.path.join(base, "recipes_full.json")
        if os.path.isfile(fullp):
            byproduct_checks(rows, {k: json.loads(v) for k, v in json.load(open(fullp, encoding="utf-8")).items()},
                             tags, item_age, set(unlocked), a.baseline, check)
        undetected_check(a.server_dir, check)
    if unlocked is not None and not set(AGES) <= set(unlocked):
        print(f"[SKIP] A- and C- checks: they need the whole recipe set, this dump was filtered at {sorted(unlocked, key=AGES.index)}")
        print(f"\n{len(failed)} FAIL: {failed}" if failed else "\nall checks PASS")
        sys.exit(1 if failed else 0)

    # ---- Dawn crafting whitelist: crafting recipes a Dawn-only team can still use -----------------------
    crafting = [r for r in rows if r["type"] == "minecraft:crafting"]
    open_ = open_in("dawn", crafting)
    by_ns = collections.Counter(r["id"].split(":")[0] for r in open_)
    tfc_open = sorted(r["id"] for r in open_ if r["id"].startswith("tfc:"))
    tfc_bad = [i for i in tfc_open if not re.match(
        r"tfc:crafting/(stone/|firestarter$|thatch$|straw|stick_|rope$|obsidian_|.*_from_twigs$)", i)]
    print(f"crafting recipes: {len(crafting)}; open in Dawn (no recipe, output or item lock): {len(open_)}")
    check("A-dawn-1 vanilla (minecraft:) crafting recipes all locked in Dawn",
          by_ns.get("minecraft", 0) == 0,
          [f"{r['id']} -> {r['result']}" for r in open_ if r["id"].startswith("minecraft:")])
    check("A-dawn-2 TFC grid recipes open in Dawn are only the whitelist (shafting, firestarter, straw/thatch, sticks, rope, obsidian)",
          not tfc_bad, [f"not whitelisted: {i}" for i in tfc_bad] or [f"open TFC: {len(tfc_open)} e.g. {tfc_open[:6]}"])
    print("    open in Dawn by namespace: " + ", ".join(f"{k}={v}" for k, v in by_ns.most_common()))
    other = [r for r in open_ if not r["id"].startswith(("tfc:", "minecraft:"))]
    check("A-dawn-3 no grid recipe of another mod is open in Dawn (Doc 08 section 9.2)",
          not other, [f"{r['id']} -> {r['result']}" for r in sorted(other, key=lambda r: r["id"])])

    # ---- Stone, Bronze, Iron: what opens with each Age ------------------------------------------------------
    # Every recipe open at a stage must make an item of that Age or earlier (age_items tags), the namespaces that
    # enter later must stay shut, and a few landmark recipes must open exactly there.
    landmarks = {
        "age_0": (["tfc:crafting/wood/workbench/oak", "afc:crafting/wood/lumber/ipe_from_planks", "firmages:crafting/hearthstone"],
                  ["create:", "firmalife:", "createdeco:", "immersiveengineering:"]),
        "age_1": (["create:crafting/kinetics/cogwheel", "create:crafting/kinetics/water_wheel", "firmages:crafting/sky_disc"],
                  ["createdeco:", "dndecor:", "railways:", "immersiveengineering:", "mekanism:"]),
        "age_2": (["firmages:crafting/steel_heart", "create:crafting/kinetics/brass_hand", "railways:"],
                  ["occultism:", "ars_nouveau:", "immersiveengineering:", "mekanism:", "mysticalagriculture:"]),
    }
    prev = {r["id"] for r in open_}
    for st, (must, never) in landmarks.items():
        now = open_in(st, crafting)
        ids_now = {r["id"] for r in now}
        new = [r for r in now if r["id"] not in prev]
        too_late = [f"{r['id']} -> {r['result']} ({item_age.get(r['result'], '?')})" for r in now
                    if r["result"] and AGES.index(item_age.get(r["result"], "dawn")) > AGES.index(st)
                    if item_age.get(r["result"]) in AGES]
        missing = [m for m in must if not any(i == m or (m.endswith(":") and i.startswith(m)) for i in ids_now)]
        leaked = [i for i in sorted(ids_now) if i.startswith(tuple(never))]
        byns = collections.Counter(r["id"].split(":")[0] for r in new)
        check(f"A-{st} grid recipes open at {st}: results of that Age or earlier, landmarks open, later mods shut",
              not too_late and not missing and not leaked,
              [f"result from a later Age: {x}" for x in too_late] + [f"landmark not open: {m}" for m in missing]
              + [f"later mod open: {x}" for x in leaked])
        print(f"    open at {st}: {len(now)} (+{len(new)}); new by namespace: " +
              ", ".join(f"{k}={v}" for k, v in byns.most_common(12)))
        prev = ids_now

    # ---- Create plates / zinc / brass ---------------------------------------------------------------------
    sheet_out = ids(r"^create:(iron|copper|golden|brass)_sheet$", "result")
    check("A8 Create plates removed (no recipe makes create:{iron,copper,golden,brass}_sheet; no create:pressing/*_ingot)",
          not sheet_out and not ids(r"^create:pressing/(iron|copper|gold|brass)_ingot$"), sheet_out)
    # A result locked by the "disabled" stage can never be held, so such a recipe is no source.
    zinc_out = [r["id"] for r in rows if re.match(r"^create:(zinc_ingot|zinc_block|raw_zinc|raw_zinc_block|brass_ingot)$", r["result"] or "")
                and PS + "disabled" not in r["locks"]]
    chain = ["firmages:crafting/zinc_nugget", "firmages:heating/create_zinc_nugget",
             "create:crafting/materials/andesite_alloy_from_zinc", "create:mixing/andesite_alloy_from_zinc"]
    gone = ["create:mixing/brass_ingot", "create:crafting/materials/andesite_alloy", "create:mixing/andesite_alloy",
            "woodencog:rock_knapping/andesite_alloy", "woodencog:rock_knapping/andesite_alloy_deploying"]
    check("A8 Create zinc/brass chain as designed (no Create zinc/brass ingot sources; TFC zinc -> Create nugget -> andesite alloy)",
          not zinc_out and all(c in by_id for c in chain) and not any(g in by_id for g in gone),
          [f"makes {i}" for i in zinc_out] + [f"missing {c}" for c in chain if c not in by_id]
          + [f"still present {g}" for g in gone if g in by_id])
    other_alloy = [r["id"] for r in rows if r["result"] == "create:andesite_alloy" and r["id"] not in chain
                   and not r["id"].startswith("create:crafting/materials/andesite_alloy_from_block")]
    print(f"    other recipes making create:andesite_alloy: {other_alloy}")

    # ---- IE hammer ------------------------------------------------------------------------------------------
    ham = ids(r"^immersiveengineering:crafting/(plate_.*_hammering|hammercrushing_.*|raw_hammercrushing_.*)$")
    check("IE hammer plate and hammer-crushing grid recipes removed", not ham, ham)

    # ---- Create crushing of ores ----------------------------------------------------------------------------
    ore_crush = [i for i in ids(r"^create:crushing/") if re.search(r"(^create:crushing/raw_|_ore$|compat/.*ore|/(crimsite|asurine|veridium|ochrum)(_recycling)?$)", i)]
    other_crush = [r["id"] for r in rows if r["type"] == "create:crushing" and not r["id"].startswith(("create:", "firmages:"))
                   and re.search(r"ore|raw", r["id"])]
    check("A8 Create crushing of ores / raw metals / Create stones removed", not ore_crush, ore_crush)
    print(f"    firmages crushing recipes: {len(ids(r'^firmages:crushing/'))}; other mods' ore-like crushing: {other_crush[:20]}")

    # ---- Custom recipes (A7) and removals (A8) -------------------------------------------------------------
    want = ["firmages:crafting/hearthstone", "firmages:crafting/sky_disc", "firmages:crafting/steel_heart",
            "firmages:pressing/tfc_sheet_bronze", "firmages:crushing/rich_hematite",
            "firmages:knapping/unfired_hearth_idol", "firmages:heating/hearth_idol"]
    check("A7 pack recipes present (signature items, press, crushing, TFC knapping + heating)",
          all(w in by_id for w in want), [f"{w}: {'ok ' + by_id[w]['type'] if w in by_id else 'MISSING'}" for w in want])
    heat = ids(r"^firmages:heating/")
    print(f"    firmages tfc:heating recipes: {len(heat)}: {heat}")
    must_gone = ["create:pressing/iron_ingot", "create:mixing/brass_ingot", "create:crafting/materials/andesite_alloy",
                 "create:crushing/raw_iron"]
    book = ids(r"^ftbquests:book$", "result")
    check("A8 removed recipes absent (+ no recipe for ftbquests:book)",
          not any(m in by_id for m in must_gone) and not book, [m for m in must_gone if m in by_id] + book)

    # ---- Tags (A9) -------------------------------------------------------------------------------------------
    plates_iron = tags.get("c:plates/iron", [])
    check("A9 c:plates/iron has tfc:metal/sheet/wrought_iron and not create:iron_sheet",
          "tfc:metal/sheet/wrought_iron" in plates_iron and "create:iron_sheet" not in plates_iron, [str(plates_iron)])
    src = open(os.path.join(REPO, "kubejs", "startup_scripts", "00_constants.js"), encoding="utf-8").read()
    hidden_want = re.findall(r"'([a-z0-9_]+:[a-z0-9_/]+)'", HIDDEN_ITEMS_RE.search(src).group(1))
    hidden = set(tags.get("c:hidden_from_recipe_viewers", []))
    miss = [h for h in hidden_want if h not in hidden]
    check(f"A9 c:hidden_from_recipe_viewers holds the {len(hidden_want)} HIDDEN_ITEMS", not miss, miss)

    # ---- Unification: TFC metals stay TFC ------------------------------------------------------------------
    # Canonical non-TFC results by design (Doc 10 v3 8.1): Mekanism osmium, dusts (sulfur), Mekanism/MekaTFC parts.
    by_design = re.compile(r"^(mekanism:(ingot_osmium|dust_sulfur|steel_casing)|mekatfc:)")
    tfc_foreign = [(r["id"], r["result"]) for r in rows if r["id"].startswith("tfc:")
                   and r["type"] in ("tfc:casting", "tfc:anvil", "tfc:welding", "tfc:quern")
                   and r["result"] and not r["result"].startswith(("tfc:", "minecraft:"))
                   and not by_design.match(r["result"])]
    tfc_vanilla_metal = [(r["id"], r["result"]) for r in rows if r["id"].startswith("tfc:")
                         and r["type"] in ("tfc:casting", "tfc:anvil", "tfc:welding")
                         and re.match(r"minecraft:(copper|gold|iron)_(ingot|block)$", r["result"] or "")]
    check("AU TFC casting/anvil/welding/quern results stay TFC items (Almost Unified priority tfc first)",
          not tfc_foreign and not tfc_vanilla_metal, [f"{i} -> {o}" for i, o in (tfc_foreign + tfc_vanilla_metal)])
    for tag in ("c:ingots/copper", "c:ingots/tin", "c:ingots/steel", "c:ingots/gold", "c:ingots/iron", "c:dusts/tin",
                "c:nuggets/copper"):
        print(f"    {tag}: {tags.get(tag)}")

    content_checks(base, a.server_dir, rows, by_id, tags, item_age, check)

    print(f"\n{len(failed)} FAIL: {failed}" if failed else "\nall checks PASS")
    sys.exit(1 if failed else 0)


# ================================================================================================ gate checks (M2)
# Mirrors the rules of firmages-core gate/GateRules independently: deny/allow lists are empty in the pack, exempt types
# are world data; an output item locks when its Age tag is locked or it is in age_items/disabled; a tag output locks
# only when it has members and all are locked. Fluids have no Age tags yet (age_fluids is not generated).
GATE_EXEMPT = {"immersiveengineering:mineral_mix", "tfc:collapse", "tfc:landslide"}
_GATE_OUT_KEY = re.compile(r"^(results?|outputs?|.*_outputs?|.*_results?|outputs?_.*|results?_.*)$")
# IE keys that the walker regex misses but the gate's IE extractor reads by its field scan (sawmill, arc furnace).
_GATE_IE_KEYS = ("secondaryOutputs", "strippingSecondaries", "slag")
# Mekanism-family serializers resolve a tag output to one stack the JSON does not name (the gate reads it through
# getOutputDefinition); the mirror cannot tell which member, so G-2 lists these instead of failing.
_GATE_MEK_TYPES = re.compile(r"^(mekanism|mekanismgenerators|evolvedmekanism|mekmm|moremekanismprocessing):")


def _gate_outs(o, items, tags_, inres=False):
    """Item ids and item tags in the output subtrees of a recipe JSON."""
    if isinstance(o, dict):
        for k, v in o.items():
            kl = k.lower()
            if any(w in kl for w in ("input", "ingredient", "catalyst", "reagent", "condition")) or kl in ("key", "pattern", "components"):
                continue
            r = inres or bool(_GATE_OUT_KEY.match(kl))
            if r and kl in ("id", "item") and isinstance(v, str):
                items.add(v)
            elif r and kl == "tag" and isinstance(v, str):
                tags_.add(v)
            else:
                _gate_outs(v, items, tags_, r)
    elif isinstance(o, list):
        for v in o:
            _gate_outs(v, items, tags_, inres)
    elif isinstance(o, str) and inres and ":" in o:
        (tags_.add(o[1:]) if o.startswith("#") else items.add(o))


def _load_rows(base):
    d = json.load(open(os.path.join(base, "recipes.json"), encoding="utf-8"))
    full = {k: json.loads(v) for k, v in json.load(open(os.path.join(base, "recipes_full.json"), encoding="utf-8")).items()}
    return [dict(zip(d["columns"], r)) for r in d["recipes"]], full


def gate_checks(base, baseline, rows, tags, item_age, unlocked, check):
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        check("G-0 recipes_full.json present (run fa_dump_full)", False, [path])
        return
    full = {k: json.loads(v) for k, v in json.load(open(path, encoding="utf-8")).items()}
    order = AGES + ["disabled"]

    def lock_of(item):
        st = item_age.get(item)
        return st if st == "disabled" or (st in AGES and st not in unlocked) else None

    def locks(rid, result, j, tagmap, special=None):
        """(output, stage) for every output that keeps the recipe out at the unlocked set.
        A special recipe (its JSON names no output, e.g. IE powerpack attach) is only judged by getResultItem;
        with a `special` list it goes there instead, because its real output depends on its inputs."""
        items, tg = set(), set()
        if j is not None:
            _gate_outs(j, items, tg)
            for k in _GATE_IE_KEYS:
                if j.get(k) is not None:
                    _gate_outs({"results": j[k]}, items, tg)
        if special is not None and not items and not tg:
            if result and lock_of(result):
                special.append(f"{rid} -> {result} ({lock_of(result)})")
            return []
        if result:
            items.add(result)
        out = [(i, lock_of(i)) for i in sorted(items) if lock_of(i)]
        for t in sorted(tg):
            mem = tagmap.get(t, [])
            ls = [lock_of(m) for m in mem]
            if mem and all(ls):
                out.append(("#" + t, min(ls, key=order.index)))
        return out
    print(f"gate: dump filtered at {sorted(unlocked, key=AGES.index)}")
    bad, special = [], []
    for r in rows:
        if r["type"] in GATE_EXEMPT:
            continue
        ls = locks(r["id"], r["result"], full.get(r["id"]), tags, special)
        if ls:
            bad.append(f"{r['id']} [{r['type']}] makes " + ", ".join(f"{o} ({st})" for o, st in ls))
    check(f"G-1 no loaded recipe makes an item of a locked Age or of age_items/disabled ({len(rows)} recipes)", not bad, bad)
    print(f"    special recipes (no output in their JSON) whose getResultItem is locked: {len(special)} {special[:10]}")
    if not baseline:
        return
    brows, bfull = _load_rows(baseline)
    btags = json.load(open(os.path.join(baseline, "item_tags.json"), encoding="utf-8"))
    have = {r["id"] for r in rows}
    missing, unresolved, per_age = [], [], collections.Counter()
    station_age = load_station_ages()
    by_station = collections.Counter()

    def station_lock(r, j):
        """Age that keeps a recipe out by its station (recipes/station_ages.js, the 4x/5x ore ladder and the blue
        steel infuser of the pack scripts), or None."""
        for t in (r["type"], (j or {}).get("type", "")):
            if station_age.get(t) and station_age[t] not in unlocked:
                return station_age[t]
        for rx, st in LATE_LOADED:
            if re.match(rx, r["id"]) and st not in unlocked:
                return st
        return None
    for r in brows:
        ls = [] if r["type"] in GATE_EXEMPT else locks(r["id"], r["result"], bfull.get(r["id"]), btags)
        sl = None if ls else station_lock(r, bfull.get(r["id"]))
        if ls:
            per_age[max((st for _, st in ls), key=order.index)] += 1
        elif sl:
            by_station[sl] += 1
        elif r["id"] not in have:
            j = bfull.get(r["id"]) or {}
            it, tg = set(), set()
            _gate_outs(j, it, tg)
            line = f"{r['id']} [{r['type']}] -> {r['result']}"
            (unresolved if tg and _GATE_MEK_TYPES.match(j.get("type", r["type"])) else missing).append(line)
    extra = sorted(have - {r["id"] for r in brows})
    print(f"    baseline {len(brows)} recipes; kept out here by Age of the latest locked output: "
          + ", ".join(f"{k}={per_age[k]}" for k in order if per_age[k]))
    print("    kept out by the Age of their station (station_ages.js and the late pack recipes): "
          + ", ".join(f"{k}={by_station[k]}" for k in order if by_station[k]))
    check("G-2 every baseline recipe whose outputs are all unlocked is loaded (the gate drops nothing more)",
          not missing, missing)
    print(f"    Mekanism-family tag outputs the mirror cannot resolve (dropped by the gate's resolved stack): "
          f"{len(unresolved)} {unresolved[:5]}")
    print(f"    recipes here that the baseline lacks: {len(extra)} {extra[:10]}")


STATION_AGES_JS = os.path.join(REPO, "kubejs", "server_scripts", "recipes", "station_ages.js")
# Pack recipes that a script adds only once a later Age than their outputs is unlocked (FirmAges.isUnlocked).
LATE_LOADED = [(r"^firmages:injecting/", "age_7"), (r"^firmages:dissolution/", "age_8"),
               (r"^firmages:metallurgic_infusing/blue_steel$", "age_7")]


def load_station_ages():
    """recipe type -> Age of its station, read from the put('age_N', [...]) calls of station_ages.js."""
    out = {}
    try:
        src = open(STATION_AGES_JS, encoding="utf-8").read()
    except OSError:
        return out
    for age, body in re.findall(r"put\('(age_\d)', \[(.*?)\]\)", src, re.S):
        for t in re.findall(r"'([a-z0-9_]+:[a-z0-9_/]+)'", body):
            out[t] = age
    return out


BYPRODUCTS_JS = os.path.join(REPO, "kubejs", "server_scripts", "recipes", "byproducts.js")
ACCEPTED_UNDETECTED = os.path.join(REPO, "dev", "data", "accepted_undetected.json")
_BYP_KEYS = ("secondaries", "secondaryOutputs", "strippingSecondaries", "slag")


def load_byproducts():
    src = open(BYPRODUCTS_JS, encoding="utf-8").read()
    m = re.search(r"// BEGIN BYPRODUCTS\s*const FA_BYPRODUCTS = (\{.*?\})\s*// END BYPRODUCTS", src, re.S)
    return json.loads(m.group(1))


def _entry_ids(e, tags):
    """(item ids, from a tag) of one result entry: {id|item|tag}, {output: ...}, {basePredicate: ...}, a string."""
    if isinstance(e, str):
        return (tags.get(e[1:], []), True) if e.startswith("#") else ([e], False)
    if not isinstance(e, dict):
        return [], False
    for k in ("output", "basePredicate", "stack"):
        if isinstance(e.get(k), dict):
            return _entry_ids(e[k], tags)
    if isinstance(e.get("tag"), str):
        return tags.get(e["tag"], []), True
    i = e.get("id") or e.get("item")
    return ([i], False) if isinstance(i, str) else ([], False)


def split_outputs(j, tags, age_of):
    """Main outputs and chance byproducts of a recipe JSON, as {label: Age index}. In a results list the entries
    with the highest chance (no chance = 1; sequenced-assembly weights alike) are the main output, the rest are
    byproducts; IE secondaries, sawmill secondaries and arc-furnace slag are byproducts. A tag counts with its
    earliest member (the gate locks a tag only when every member is locked)."""
    mains, byps = {}, {}

    def put(dst, e):
        ids, is_tag = _entry_ids(e, tags)
        if not ids:
            return
        a = min(age_of(i) for i in ids)
        lab = ("#" + (e.get("tag") if isinstance(e, dict) and "tag" in e else "?")) if is_tag else ids[0]
        dst[lab] = max(dst.get(lab, -1), a)
    res = j.get("results")
    if isinstance(res, list) and res:
        chance = [e.get("chance", 1) if isinstance(e, dict) and isinstance(e.get("chance", 1), (int, float)) else 1 for e in res]
        for e, ch in zip(res, chance):
            put(mains if ch == max(chance) else byps, e)
    for k in ("result", "output"):
        if isinstance(j.get(k), (dict, str)):
            put(mains, j[k])
    for k in _BYP_KEYS:
        v = j.get(k)
        for e in (v if isinstance(v, list) else [v] if v is not None else []):
            put(byps, e)
    return mains, byps


def byproduct_checks(rows, full, tags, item_age, unlocked, baseline, check):
    """G-3: no recipe waits for a chance byproduct (policy: byproducts.js strips it until its Age)."""
    table = load_byproducts()
    order = AGES + ["disabled"]
    try:
        import tomllib
        with open(os.path.join(REPO, "dev", "age_map.toml"), "rb") as f:
            mod_age = tomllib.load(f).get("mods", {})
    except (ImportError, OSError):
        mod_age = {}

    def age_of(i):
        st = item_age.get(i, "dawn")
        return order.index(st) if st in order else 0
    have = {r["id"]: r for r in rows}
    top = max(AGES.index(u) for u in unlocked)
    if set(AGES) <= set(unlocked):
        need_entry, wrong = [], []
        for r in rows:
            j = full.get(r["id"])
            if j is None or r["type"] in GATE_EXEMPT:
                continue
            mains, byps = split_outputs(j, tags, age_of)
            if not mains or not byps:
                continue
            st = mod_age.get(j.get("type", r["type"]).split(":")[0], "dawn")
            need = max(max(mains.values()), AGES.index(st) if st in AGES else 0)
            late = {b: order[a] for b, a in byps.items() if a > need}
            if not late:
                continue
            listed = table.get(r["id"], {})
            miss = [f"{b} ({a})" for b, a in late.items() if listed.get(b) != a]
            if miss:
                need_entry.append(f"{r['id']} [{r['type']}] main {sorted(mains)} at {order[need]}: byproduct {miss}")
        for rid, ent in table.items():
            if rid not in full:
                wrong.append(f"{rid}: table entry without a recipe")
            for item, st in ent.items():
                if item_age.get(item) != st:
                    wrong.append(f"{rid}: {item} is {item_age.get(item)} in the Age tags, the table says {st}")
        check(f"G-3 no recipe waits for a chance byproduct of a later Age (all listed in byproducts.js, {len(table)} entries)",
              not need_entry and not wrong, need_entry + wrong)
        return
    if baseline is None:
        print("    G-3 at a filtered dump needs --baseline (the main output Ages come from the all-Ages JSON)")
        return
    bfull = _load_rows(baseline)[1]
    absent = []
    for rid in table:
        j = bfull.get(rid)
        if j is None:
            continue
        mains, _ = split_outputs(j, tags, age_of)
        st = mod_age.get(j.get("type", "x:").split(":")[0], "dawn")
        need = max(max(mains.values(), default=0), AGES.index(st) if st in AGES else 0)
        if need <= top and rid not in have:
            absent.append(f"{rid}: main output and station are open at {AGES[top]} but the recipe is absent")
    check(f"G-3 recipes of byproducts.js are loaded once their main output and station are open ({len(table)} entries)",
          not absent, absent)


def undetected_check(server_dir, check):
    """G-4: every recipe type with no detected output (audit file) is accepted with a reason."""
    path = os.path.join(server_dir, "logs", "firmages-recipe-audit.txt")
    if not os.path.isfile(path):
        print(f"[SKIP] G-4: no audit file {path} (run /firmages recipes audit)")
        return
    lines = open(path, encoding="utf-8").read().splitlines()
    types, on = [], False
    for ln in lines:
        if ln.startswith("Types with no detected output"):
            on = True
            continue
        if on:
            if not ln.startswith("  "):
                break
            types.append(ln.strip().rsplit(" (", 1)[0])
    acc = json.load(open(ACCEPTED_UNDETECTED, encoding="utf-8"))["types"]
    new = [t for t in types if t not in acc]
    gone = [t for t in acc if t not in types]
    check(f"G-4 every recipe type without a detected output is accepted ({len(types)} types, {len(acc)} accepted)", not new,
          [f"not accepted: {t}" for t in new] + [f"accepted but no longer undetected: {gone}"])


# ================================================================================================ content checks
# Arcane Age (age_3) and Industrial Age (age_4), dev/poc-results.md section "Content: Arcane and Industrial Age".
RESULT_KEYS = {"result", "results", "output", "outputs", "ritual_dummy", "entity_to_summon", "secondaryOutputs",
               "secondaries", "slag", "byproducts", "spellData", "target", "trader_id", "table", "highlight",
               "entity_outputs", "item_outputs"}
# Items a TFC world yields without a recipe: every item of the TFC-world mods, mob drops of the mob ladder up to
# the Arcane Age (vanilla, Beneath Nether), Nether blocks, and the drops of the Ars Wilden (mob_3, ritual-summoned)
# and of wild Starbuncles (spawn modifier; the spawn itself needs a client to observe).
WORLD_NS = ("tfc", "afc", "firmalife", "beneath")
VANILLA_WORLD = ("feather egg bone string spider_eye rotten_flesh gunpowder slime_ball ink_sac glow_ink_sac ender_pearl "
                 "blaze_rod ghast_tear magma_cream nether_wart glowstone_dust glowstone quartz netherrack soul_sand soul_soil "
                 "basalt blackstone obsidian gold_nugget clay_ball flint snowball ice snow_block charcoal stick cobweb "
                 "rabbit_hide rabbit_foot wither_skeleton_skull skeleton_skull phantom_membrane leather arrow bow magma_block "
                 "gilded_blackstone crimson_fungus warped_fungus crimson_stem warped_stem shroomlight ancient_debris bone_meal "
                 "redstone clay sugar_cane sugar paper glass white_wool brown_mushroom red_mushroom cactus dead_bush vine "
                 "lily_pad kelp crying_obsidian").split()
MOB_DROPS = ["ars_nouveau:wilden_horn", "ars_nouveau:wilden_spike", "ars_nouveau:wilden_wing", "ars_nouveau:starbuncle_shards",
             "ars_nouveau:wilden_tribute", "cataclysm:monstrous_horn",
             "occultism:datura_seeds",   # pack loot modifier on TFC grass (kubejs/data/firmages/loot_modifiers)
             "occultism:tallow"]         # butcher knife on TFC animals (entity tags in tags/arcane_industrial.js)
# World processes the recipe graph does not see: saplings grow into trees, crops ripen, ore blocks drop.
GROWN = {"occultism:otherworld_sapling_natural": ["occultism:otherworld_log"], "occultism:datura_seeds": ["occultism:datura"],
         "ars_nouveau:magebloom_crop": ["ars_nouveau:magebloom"],
         "theurgy:sal_ammoniac_ore": ["theurgy:sal_ammoniac_crystal"]}
# Datura and Magebloom break on TFC farmland (setblock test); they need vanilla farmland (the pack's Arcane Soil).
GROWN_NEEDS = {"occultism:datura_seeds": "minecraft:farmland", "ars_nouveau:magebloom_crop": "minecraft:farmland"}
for _c in ("blue", "red", "green", "purple"):
    GROWN[f"ars_nouveau:{_c}_archwood_sapling"] = [f"ars_nouveau:{_c}_archwood_log"]
# Industrial Age station parts (multiblocks, power, logistics) that must be makeable.
INDUSTRIAL_CHAIN = ("immersiveengineering:cokebrick immersiveengineering:blastbrick immersiveengineering:blastbrick_reinforced "
                    "immersiveengineering:alloybrick immersiveengineering:treated_wood_horizontal immersiveengineering:heavy_engineering "
                    "immersiveengineering:light_engineering immersiveengineering:steel_scaffolding_standard immersiveengineering:radiator "
                    "immersiveengineering:conveyor_basic immersiveengineering:coil_lv immersiveengineering:wirecoil_copper "
                    "immersiveengineering:capacitor_lv immersiveengineering:connector_lv immersiveengineering:craftingtable "
                    "immersiveengineering:workbench immersiveengineering:component_iron immersiveengineering:component_steel "
                    "immersiveengineering:hammer createaddition:alternator createaddition:electric_motor create:packager "
                    "create:package_frogport create:stock_link create:stock_ticker create:chain_conveyor create_jetpack:jetpack "
                    "firmages:arcane_gearbox firmages:pressure_core").split()
# Station parts of the late goals (Doc 10 v3 section 6.1): the arc furnace and HV power (age_5), Mekanism's
# infuser and AE2 autocrafting (age_6), DE fusion crafting with wyvern and draconic injectors and the rockets that
# reach the ores of the goal (age_7, age_8).
ELECTRIC_CHAIN = ("firmages:attuned_circuit immersiveengineering:capacitor_hv immersiveengineering:coil_hv "
                  "immersiveengineering:wirecoil_steel immersiveengineering:connector_hv immersiveengineering:transformer_hv "
                  "immersiveengineering:graphite_electrode immersiveengineering:sheetmetal_steel "
                  "immersiveengineering:light_engineering immersiveengineering:heavy_engineering "
                  "immersiveengineering:plate_aluminum tfc:metal/sheet/red_steel").split()
INFORMATION_CHAIN = ("mekanism:advanced_control_circuit mekanism:metallurgic_infuser mekanism:alloy_infused "
                     "ae2:cell_component_64k ae2:controller ae2:molecular_assembler ae2:inscriber ae2:pattern_provider "
                     "tfc:metal/sheet/blue_steel").split()
SPACE_CHAIN = ("draconicevolution:crafting_core draconicevolution:basic_crafting_injector "
               "draconicevolution:wyvern_crafting_injector draconicevolution:wyvern_core mekanism:pellet_polonium "
               "ad_astra:tier_1_rocket ad_astra:tier_2_rocket ad_astra:desh_plate ad_astra:ostrum_plate "
               # Atomic strand: the fission fuel chain (fluorite from TFC cryolite, uranium from TFC + IE uraninite)
               "mekanism:fluorite_gem mekanism:hydrofluoric_acid mekanism:yellow_cake_uranium mekanism:uranium_oxide "
               "mekanism:uranium_hexafluoride mekanism:fissile_fuel mekanism:nuclear_waste mekanism:chemical_dissolution_chamber "
               "mekanism:chemical_oxidizer mekanism:solar_neutron_activator mekanism:pressurized_reaction_chamber").split()
QUANTUM_CHAIN = ("draconicevolution:awakened_crafting_injector draconicevolution:awakened_core mekanism:pellet_antimatter "
                 "mekanism:sps_casing ad_astra:tier_3_rocket ad_astra:tier_4_rocket ad_astra:calorite_plate "
                 "ad_astra:ice_shard occultism:book_of_binding_bound_marid firmages:awakened_keystone").split()
# Singularity Age: the Chaos strand (chaotic injector and core, the Draconic Reactor) and the Ultimate Singularity, then
# the Stargate strand (naquadah from Mercury through the Arc Furnace and Mekanism, Stargate Journey's crystals).
FINALE_CHAIN = ("draconicevolution:chaotic_crafting_injector draconicevolution:chaotic_core evolvedmekanism:alloy_singular "
                "draconicevolution:reactor_core draconicevolution:reactor_stabilizer draconicevolution:reactor_injector "
                "draconicevolution:flux_gate firmages:awakened_keystone").split()
STARGATE_CHAIN = ("sgjourney:naquadah_ingot sgjourney:naquadah_iron_alloy sgjourney:refined_naquadah sgjourney:pure_naquadah "
                  "sgjourney:naquadah_liquidizer sgjourney:crystallizer sgjourney:crystal_base sgjourney:control_crystal "
                  "sgjourney:communication_crystal sgjourney:transfer_crystal sgjourney:classic_stargate_ring_block "
                  "sgjourney:classic_stargate_chevron_block sgjourney:classic_dhd firmages:ultimate_singularity "
                  "firmages:origin_coordinates").split()
ARCANE_CHAIN = (
    "occultism:datura_seeds occultism:dictionary_of_spirits occultism:chalk_white occultism:chalk_gold "
    "occultism:chalk_purple occultism:golden_sacrificial_bowl occultism:spirit_attuned_gem occultism:spirit_attuned_crystal "
    "occultism:book_of_binding_djinni occultism:iesnium_ingot occultism:storage_controller occultism:storage_stabilizer_tier1 "
    "occultism:dimensional_mineshaft occultism:miner_djinni_ores occultism:spirit_campfire occultism:large_candle "
    "ars_nouveau:novice_spell_book ars_nouveau:source_gem ars_nouveau:source_gem_block ars_nouveau:imbuement_chamber "
    "ars_nouveau:source_jar ars_nouveau:enchanting_apparatus ars_nouveau:arcane_core ars_nouveau:scribes_table "
    "ars_nouveau:warp_scroll ars_nouveau:apprentice_spell_book ars_nouveau:ritual_brazier ars_nouveau:ritual_wilden_summon "
    "ars_nouveau:starbuncle_charm ars_nouveau:ritual_flight theurgy:pyromantic_brazier theurgy:calcination_oven "
    "theurgy:liquefaction_cauldron theurgy:distiller theurgy:incubator theurgy:mercury_catalyst theurgy:divination_rod_t1 "
    "theurgy:sal_ammoniac_accumulator summoningrituals:altar firmages:arcane_keystone").split()


def _slots(o, out, key=None):
    """Ingredient slots of a recipe JSON; each slot is a list of alternatives ('item'|'tag', id)."""
    if isinstance(o, dict):
        if "amount" in o and ("fluid" in o or "fluid" in str(o.get("type", ""))):
            return out  # fluid ingredient
        if "amount" in o and "count" not in o and isinstance(o.get("tag") or o.get("chemical"), str):
            # an amount without a count is a fluid or a Mekanism chemical. A chemical ("chemical": id, or a tag under
            # a chemical_* key; a chemical tag is named after its chemical, e.g. mekanism:redstone) is reached when a
            # recipe or a machine (MACHINE_MADE) has produced it; fluids are taken as available
            if "chemical" in o or (key and "chemical" in key.lower()):
                out.append([("chem", o.get("chemical") or o.get("tag"))])
            return out
        if isinstance(o.get("item"), str):
            out.append([("item", o["item"])])
            return out
        if isinstance(o.get("tag"), str) and len(o) <= 3:
            out.append([("tag", o["tag"])])
            return out
        if o.get("type") == "neoforge:single" or "fluid" in o or "fluid_stack" in str(o.get("type", "")):
            return out
        for k, v in o.items():
            if k in RESULT_KEYS or k == "pattern":
                continue
            if k == "key" and isinstance(v, dict):
                for vv in v.values():
                    _slots(vv, out, "key")
                continue
            _slots(v, out, k)
    elif isinstance(o, list):
        if o and all(isinstance(v, dict) and ("item" in v or "tag" in v) for v in o) and key in (
                "ingredient", "input", "base", "template", "addition", "reagent", "key", "sulfur", "salt", "mercury", "solute"):
            out.append([("item", v["item"]) if "item" in v else ("tag", v["tag"]) for v in o])
            return out
        if o and all(isinstance(v, str) and ":" in v for v in o) and key in ("ingredient", "ingredients", "input", "inputs", "key"):
            alts = [("tag", v[1:]) if v.startswith("#") else ("item", v) for v in o]
            if key in ("ingredients", "inputs"):
                out.extend([[x] for x in alts])
            else:
                out.append(alts)
            return out
        for v in o:
            if isinstance(v, list) and v and all(isinstance(x, dict) and ("item" in x or "tag" in x) for x in v):
                out.append([("item", x["item"]) if "item" in x else ("tag", x["tag"]) for x in v])
            else:
                _slots(v, out, key)
    elif isinstance(o, str) and key in ("input", "ingredient") and ":" in o:
        out.append([("tag", o[1:]) if o.startswith("#") else ("item", o)])
    return out


def _outs(o, out, inres=False):
    """Item ids a recipe JSON produces (results, outputs, secondaries, weighted stacks)."""
    if isinstance(o, dict):
        for k, v in o.items():
            r = inres or k in ("result", "results", "output", "outputs", "secondaries", "secondaryOutputs", "slag",
                               "output_item", "result_item", "item_output", "chemical_output")
            if r and k in ("id", "item") and isinstance(v, str):
                out.append(v)
            elif r and k == "stack" and isinstance(v, dict) and "id" in v:
                out.append(v["id"])
            else:
                _outs(v, out, r)
    elif isinstance(o, list):
        for v in o:
            _outs(v, out, inres)
    elif isinstance(o, str) and inres and ":" in o:
        out.append(o)
    return out


def content_checks(base, server_dir, rows, by_id, tags, item_age, check):
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        check("C-0 recipes_full.json present (run fa_dump_full)", False, [path])
        return
    full = {k: json.loads(v) for k, v in json.load(open(path, encoding="utf-8")).items()}
    items = set(json.load(open(os.path.join(base, "registries.json"), encoding="utf-8"))["item"])
    disabled = {i for i, st in item_age.items() if st == "disabled"} | set(tags.get("c:hidden_from_recipe_viewers", []))

    def age_ix(i):
        return AGES.index(item_age[i]) if item_age.get(i) in AGES else -1

    def members(slot):
        return {x for k, x in slot if k == "item"} | {i for k, x in slot if k == "tag" for i in tags.get(x, [])}

    # ---- obtainability closure: which items a TFC world can make at all --------------------------------------
    # Tag outputs (More Mekanism Processing, IE, Occultism) resolve to the member Almost Unified keeps: the
    # priority override of the tag, else the first member of a namespace in mod_priorities, else the first member
    # (config/almostunified/unification/materials.json).
    try:
        au = json.load(open(os.path.join(REPO, "config", "almostunified", "unification", "materials.json"), encoding="utf-8"))
    except (OSError, ValueError):
        au = {}
    au_prio = au.get("mod_priorities", [])
    au_over = au.get("priority_overrides", {})

    def tag_target(t):
        mem = [m for m in tags.get(t, []) if m not in disabled]
        if not mem:
            return []
        for ns in ([au_over[t]] if t in au_over else []) + au_prio:
            hit = [m for m in mem if m.split(":")[0] == ns]
            if hit:
                return hit[:1]
        return mem[:1]

    def tag_outs(o, out, inres=False):
        if isinstance(o, dict):
            for k, v in o.items():
                r = inres or k in ("result", "results", "output", "outputs", "secondaries", "slag", "item_output")
                if r and k == "tag" and isinstance(v, str):
                    out |= set(tag_target(v))
                else:
                    tag_outs(v, out, r)
        elif isinstance(o, list):
            for v in o:
                tag_outs(v, out, inres)
        return out

    def outs_with_tags(j):
        return set(_outs(j, [])) | tag_outs(j, set())
    def slots_of(j):
        sl = _slots(j, [])
        trans = j.get("transitional_item", {})
        tid = trans.get("id") if isinstance(trans, dict) else None
        return [x for x in sl if not (tid and ("item", tid) in x)]  # Create sequenced assembly loops on its own item
    recs = [(rid, slots_of(j), outs_with_tags(j)) for rid, j in full.items()]

    def closure(max_age=None):
        """Items obtainable from the world set; with max_age only items of that Age or earlier may enter."""
        def fits(i):
            return max_age is None or age_ix(i) <= AGES.index(max_age)
        have = {i for i in items if i.split(":")[0] in WORLD_NS} | {"minecraft:" + x for x in VANILLA_WORLD} | set(MOB_DROPS)
        have = {i for i in have if fits(i)}

        def sat_(slot):
            return any((k in ("item", "chem") and x in have) or (k == "tag" and any(i in have for i in tags.get(x, [])))
                       for k, x in slot)
        grew = True
        while grew:
            grew = False
            for rid, sl, os_ in recs:
                new = {o for o in os_ if o not in have and fits(o)}
                if new and all(sat_(x) for x in sl):
                    have |= new
                    grew = True
            for seed, got in GROWN.items():
                new = {g for g in got if seed in have and GROWN_NEEDS.get(seed, seed) in have and g not in have and fits(g)}
                if new:
                    have |= new
                    grew = True
        return have
    ok = closure()
    ok3 = closure("age_3")
    ok4 = closure("age_4")
    station_age = {}
    try:
        import tomllib
        with open(os.path.join(REPO, "dev", "age_map.toml"), "rb") as f:
            station_age = tomllib.load(f).get("mods", {})
    except (ImportError, OSError):
        pass

    def by_age4(rtype):
        """True for recipe types whose mod (the station) exists by the Industrial Age."""
        ns = rtype.split(":")[0]
        return ns in ("minecraft", "kubejs") or station_age.get(ns, "dawn") in AGES[: AGES.index("age_4") + 1]

    # ---- C-3: Arcane Age ---------------------------------------------------------------------------------------
    ks = full.get("firmages:ritual/arcane_keystone", {})
    ks_ings = sorted(x for s in _slots(ks, []) for _, x in s)
    want_ings = sorted(["occultism:book_of_binding_bound_djinni", "occultism:spirit_attuned_crystal", "occultism:iesnium_ingot", "firmages:boss_token/age_3",
                        "theurgy:mercury_catalyst", "tfc:metal/ingot/steel", "ars_nouveau:source_gem_block"])
    ks_row = by_id.get("firmages:ritual/arcane_keystone", {})
    check("C-3a Arcane Keystone: Djinni pentacle ritual from the three proofs, TFC steel and a source gem block; item age_3",
          ks.get("type") == "occultism:ritual" and ks.get("pentacle_id") == "occultism:craft_djinni" and ks_ings == want_ings
          and item_age.get("firmages:arcane_keystone") == "age_3" and ks_row.get("item_lock") == PS + "age_3"
          and tags.get("firmages:boss_token/age_3") == ["ars_nouveau:wilden_tribute"],
          [f"type {ks.get('type')}, pentacle {ks.get('pentacle_id')}, ingredients {ks_ings}",
           f"item age {item_age.get('firmages:arcane_keystone')}, PS item lock {ks_row.get('item_lock')}",
           f"boss_token/age_3 {tags.get('firmages:boss_token/age_3')}"])
    missing = [c for c in ARCANE_CHAIN if c not in ok]
    check("C-3b Arcane strands (Spirits, Source, Alchemy), their stations and the Keystone are obtainable from TFC-world inputs",
          not missing, [f"not obtainable: {m}" for m in missing]
          + [f"theurgy:sal_ammoniac_crystal obtainable: {'theurgy:sal_ammoniac_crystal' in ok} (Occultism miner)"])
    late = [f"{c} ({item_age.get(c)})" for c in ARCANE_CHAIN if age_ix(c) > AGES.index("age_3")]
    check("C-3c every item of the Arcane chain belongs to age_3 or earlier", not late, late)
    missing3 = [c for c in ARCANE_CHAIN if c in ok and c not in ok3]
    check("C-3d the Arcane chain is reachable with items of age_3 or earlier only (no diamond, End stone, ...)",
          not missing3, [f"needs a later Age: {m}" for m in missing3])
    ore_tag = re.compile(r'"tag": "c:(ores|raw_materials|clumps|storage_blocks/raw_)')
    ore_crush = [rid for rid, j in full.items() if j.get("type") == "occultism:crushing" and ore_tag.search(json.dumps(j.get("ingredient")))
                 and "iesnium" not in json.dumps(j.get("ingredient"))]  # Occultism's own Nether metal stays
    theurgy_std = [rid for rid, j in full.items() if j.get("type") == "theurgy:liquefaction" and ore_tag.search(json.dumps(j.get("ingredient")))]
    spag = sorted(r for r in full if r.startswith("firmages:liquefaction/"))
    gone = [i for i in ("occultism:ritual/summon_foliot_transport_items", "ars_additions:apparatus/warp_index",
                        "ars_nouveau:scry_ritual/diamond_ores") if i in full]
    check("C-3e one magic carrier per function: no Occultism crusher ore recipes, Theurgy spagyrics only on rich TFC pieces, "
          "no Foliot transporter, no Warp Index, no diamond scrying",
          not ore_crush and not theurgy_std and len(spag) == 15 and not gone,
          [f"occultism ore crushing: {ore_crush[:5]}", f"theurgy standard ore liquefaction: {theurgy_std[:5]}",
           f"firmages spagyric recipes: {len(spag)}", f"still present: {gone}"])
    miner_bad = [f"{rid} -> {o}" for rid, j in full.items() if j.get("type") == "occultism:miner"
                 for o in _outs(j, []) if age_ix(o) > AGES.index("age_3")
                 and not rid.startswith(("occultism:miner/deeps", "occultism:miner/master"))]
    check("C-3f miners of the Arcane Age give nothing of a later Age", not miner_bad, miner_bad)

    # ---- C-4: Industrial Age -----------------------------------------------------------------------------------
    pc = full.get("firmages:crafting/pressure_core", {})
    pc_ings = sorted(x for s in _slots(pc, []) for _, x in s)
    pc_want = sorted(["immersiveengineering:heavy_engineering", "c:double_sheets/black_steel", "firmages:arcane_gearbox",
                      "firmages:boss_token/age_4"])
    pc_row = by_id.get("firmages:crafting/pressure_core", {})
    check("C-4a Pressure Core: black steel double sheet, Heavy Engineering Block, Arcane Gearbox, Monstrosity token; age_4",
          pc_ings == pc_want and item_age.get("firmages:pressure_core") == "age_4"
          and pc_row.get("item_lock") == PS + "age_4" and item_age.get("firmages:arcane_gearbox") == "age_4"
          and tags.get("firmages:boss_token/age_4") == ["cataclysm:monstrous_horn"]
          and "firmages:altar/netherite_monstrosity" in full
          and "firmages:arcane_gearbox" in json.dumps(full.get("firmages:crafting/blastbrick_reinforced", {})),
          [f"ingredients {pc_ings}", f"item age {item_age.get('firmages:pressure_core')}, lock {pc_row.get('item_lock')}",
           f"boss_token/age_4 {tags.get('firmages:boss_token/age_4')}",
           f"altar ritual present: {'firmages:altar/netherite_monstrosity' in full}"])
    pc_missing = [i for i in INDUSTRIAL_CHAIN if i not in ok4]
    check("C-4b Industrial Age stations, power, package tier and the Pressure Core are reachable with items of age_4 or earlier",
          not pc_missing, [f"not reachable: {m}{' (only with later items)' if m in ok else ''}" for m in pc_missing])
    ie_like = re.compile(r"^(immersiveengineering|tfc_ie_addon|advancedtfctech):")
    bad_out = set()
    for rid, j in full.items():
        if not (ie_like.match(j.get("type", "")) or ie_like.match(rid) or rid.startswith(("firmages:crusher/", "firmages:alloy_kiln/"))):
            continue
        for o in _outs(j, []):
            if o in disabled or re.match(r"^immersiveengineering:(ingot|plate|storage)_(copper|iron|gold|silver|nickel|steel|tin|zinc|bronze|brass)$", o) \
                    or o == "immersiveengineering:nugget_copper" \
                    or re.match(r"^immersiveengineering:dust_(copper|iron|gold|tin|steel|lead|uranium)$", o) \
                    or re.match(r"^minecraft:(iron|copper|gold)_ingot$", o):
                bad_out.add(f"{rid} -> {o}")
    check("C-4c IE outputs are canonical (no disabled or hidden item, no IE/vanilla ingot, plate or dust of a TFC metal)",
          not bad_out, sorted(bad_out))

    # One station per function (Doc 10 v3 matrix 6.1), Ages 3-4.
    pieces = {i for i in items if re.match(r"^(tfc|tfc_ie_addon|mekatfc):ore/(small|poor|normal|rich)_", i)}
    # Allowed consumers: TFC hand stations, WoodenCog heated melting (Schmelzen, 1-2), Create crushing/milling (2),
    # Theurgy (3), the IE Crusher (4), grid recipes that use a piece as a part. Stations of later Ages are not checked.
    allowed = re.compile(r"^(tfc:|woodencog:heated_mixing$|create:(crushing|milling)$|theurgy:|immersiveengineering:crusher$"
                         r"|minecraft:crafting|kubejs:shape)")
    wrong = collections.Counter()
    ex = {}
    for rid, sl, os_ in recs:
        t = full[rid].get("type", "")
        if any(members(s) & pieces for s in sl) and by_age4(t) and not allowed.match(t):
            wrong[t] += 1
            ex.setdefault(t, rid)
    check("C-4d ore pieces: only TFC hand stations, Create crushing/milling (2), Theurgy (3), IE Crusher (4), Mekanism ladder (6-8)",
          not wrong, [f"{t}: {n} e.g. {ex[t]}" for t, n in wrong.most_common()])
    sheet_bad = []
    for rid, j in full.items():
        if j.get("type") != "immersiveengineering:metal_press":
            continue
        inp = j.get("input") if isinstance(j.get("input"), dict) else {}
        if any(x.startswith("tfc:metal/sheet/") for x in _outs(j, [])) and inp.get("count", 1) != 2:
            sheet_bad.append(f"{rid}: {inp.get('count', 1)} ingot(s)")
    sheet_types = sorted({full[r].get("type") for r, sl, os_ in recs if any(x.startswith("tfc:metal/sheet/") for x in os_)
                          and by_age4(full[r].get("type", ""))})
    check("C-4e TFC sheets from two ingots only (TFC anvil, Create/WoodenCog press, IE Metal Press mold_sheet); stations by age_4",
          not sheet_bad and set(sheet_types) <= {"tfc:anvil", "create:pressing", "woodencog:heated_pressing",
                                                  "immersiveengineering:metal_press", "tfc:heating", "create:sequenced_assembly"},
          sheet_bad + [f"types making TFC sheets: {sheet_types}"])
    kiln = {rid: j for rid, j in full.items() if j.get("type") == "immersiveengineering:alloy"}
    want_kiln = {"bronze": (9, 1, 10), "brass": (9, 1, 10), "rose_gold": (3, 1, 4), "sterling_silver": (2, 1, 3),
                 "constantan": (1, 1, 2), "electrum": (1, 1, 2)}
    kiln_bad = []
    for m, want in want_kiln.items():
        j = kiln.get(f"firmages:alloy_kiln/{m}")
        if not j:
            kiln_bad.append(f"missing firmages:alloy_kiln/{m}")
            continue
        got = (j["input0"].get("count", 1), j["input1"].get("count", 1), j["result"].get("count", 1))
        if got != want:
            kiln_bad.append(f"{m}: {got} != {want}")
    kiln_other = [r for r in kiln if not r.startswith("firmages:") and r != "immersiveengineering:alloysmelter/insulating_glass"]
    check("C-4f IE Alloy Kiln makes TFC alloys in TFC ratios, no IE ratio left", not kiln_bad and not kiln_other,
          kiln_bad + [f"other kiln recipe: {r}" for r in kiln_other])
    coke_items = set(tags.get("c:coal_coke", []))
    coke = sorted({full[r].get("type") for r, sl, os_ in recs if os_ & coke_items})
    coke_vanilla = [r for r, j in full.items() if j.get("type") == "immersiveengineering:coke_oven" and '"minecraft:coal' in json.dumps(j)]
    # sources only: recipes that recast steel (ingot, dust, nugget, block, molten steel) are no new steel
    steel = sorted({full[r].get("type") for r, sl, os_ in recs if "tfc:metal/ingot/steel" in os_ and by_age4(full[r].get("type", ""))
                    and "steel" not in json.dumps({k: v for k, v in full[r].items() if k not in RESULT_KEYS})
                    and not any(members(s_) and all(age_ix(i) > AGES.index("age_4") for i in members(s_)) for s_ in sl)})
    steel_ok = {"tfc:anvil", "tfc:casting", "tfc:welding", "tfc:heating", "immersiveengineering:blast_furnace",
                "immersiveengineering:arc_furnace", "minecraft:crafting_shapeless", "minecraft:crafting_shaped"}
    check("C-4g coke only from the IE Coke Oven on TFC coal; TFC steel only from TFC, the IE Blast Furnace (and Arc Furnace, 5)",
          not coke_vanilla and set(coke) <= {"immersiveengineering:coke_oven", "minecraft:crafting_shapeless", "minecraft:crafting_shaped"}
          and set(steel) <= steel_ok,
          [f"coke makers {coke}", f"coke from vanilla coal {coke_vanilla}", f"steel makers {steel}"])
    ca = sorted({o for r, sl, os_ in recs for o in os_ if o.startswith("createaddition:")})
    check("C-4h Crafts & Additions: only the Electric Motor and the Alternator have recipes",
          set(ca) == {"createaddition:alternator", "createaddition:electric_motor"}, ca)
    pkg_bad = []
    for o in ("create:packager", "create:package_frogport", "create:stock_link", "create:chain_conveyor"):
        makers = [r for r, sl, os_ in recs if o in os_ and re.match(r"^(minecraft:crafting|kubejs:shape)", full[r].get("type", ""))
                  and not r.endswith(("_clear", "_from_conversion"))]
        if not makers or any("immersiveengineering:component_" not in json.dumps(full[r]) for r in makers):
            pkg_bad.append(f"{o}: {makers}")
        if item_age.get(o) != "age_4":
            pkg_bad.append(f"{o} age {item_age.get(o)}")
    check("C-4i Create 6 package tier: age_4 items, every grid recipe carries an IE component", not pkg_bad, pkg_bad)
    # Gear = tools, weapons and armour. "minecraft:enchantable/durability" is left out: Occultism puts its chalks and
    # miner spirits there, which are consumables and machine parts, not gear.
    gear = set()
    for k in ("c:tools", "c:armors", "minecraft:enchantable/armor"):
        gear |= set(tags.get(k, []))

    def gear_chains(age):
        """Recipes that make gear of `age` from a finished tool or armour piece. A slot that holds the output
        only (Ars dyeing, repair-style recipes) recolours the same item and is not an upgrade."""
        chains = []
        for rid, sl, os_ in recs:
            outs = [o for o in os_ if o in gear and item_age.get(o) == age]
            if not outs or rid.startswith("cataclysm:weapon_infusion/"):
                continue
            for s in sl:
                alts = members(s)
                if alts and alts <= gear and not alts <= set(outs):
                    chains.append(f"{rid} -> {outs[0]} eats {sorted(alts)[:2]}")
        return chains
    chains3 = gear_chains("age_3")
    fixed = ("ars_nouveau:enchanters_fishing_rod", "ars_nouveau:spell_bow", "ars_nouveau:spell_crossbow")
    unreached = [o for o in fixed if o not in ok3]
    check("C-3g gear rule for age_3 gear: no tool or armour as ingredient; the three Ars apparatus upgrades "
          "(arcane_tfc_inputs.js GEAR_FIX) are made from materials reachable at age_3",
          not chains3 and not unreached,
          chains3 + [f"not reachable with age_3 items: {o}" for o in unreached])
    chains = gear_chains("age_4")
    check("C-4j gear rule for age_4 gear: no tool or armour as ingredient (Cataclysm boss-weapon fusion is an open point)",
          not chains, chains)

    # ---- C-5 .. C-9: Electric to Singularity Age (Doc 10 v3 sections 6.1, 7, 8; recipes/age_5 .. age_9) ---------------
    def types_making(pred):
        return sorted({full[r].get("type", "") for r, sl, os_ in recs if any(pred(o) for o in os_)})

    def makers(item):
        return sorted(r for r, sl, os_ in recs if item in os_)
    late_ns = ("mekanism", "moremekanismprocessing", "mekmm", "evolvedmekanism", "mekatfc")
    # C-5a: the arc furnace steels and the IE/IP/CDG disable list
    arc = {r for r in full if full[r].get("type") == "immersiveengineering:arc_furnace"}
    want_arc = ["firmages:arc_furnace/weak_steel", "firmages:arc_furnace/black_steel", "firmages:arc_furnace/weak_red_steel"]
    weak_in = json.dumps(full.get("firmages:arc_furnace/weak_steel", {}).get("input", {}))
    cdg = sorted({o for r, sl, os_ in recs for o in os_ if o.startswith("createdieselgenerators:")})
    cdg_keep = {"createdieselgenerators:" + p_ for p_ in ("diesel_engine", "large_diesel_engine", "huge_diesel_engine",
                                                           "engine_piston", "engine_silencer", "engine_turbocharger")}
    check("C-5a Electric Age: arc furnace weak steel from steel, black steel and weak red steel pack recipes; no TFC + IE "
          "weak steel from black steel; Diesel Generators only engines; no IP gas generator",
          all(w in arc for w in want_arc) and "c:ingots/steel" in weak_in and "tfc_ie_addon:arcfurnace/weak_steel" not in full
          and set(cdg) <= cdg_keep and not makers("immersivepetroleum:gas_generator"),
          ["missing " + w for w in want_arc if w not in arc] + ["weak steel input " + weak_in, "CDG outputs %s" % sorted(set(cdg) - cdg_keep)])
    # C-6a: the ore ladder, one recipe per ore family, grade and step; no Mekanism-family recipe on TFC ore blocks/raw ores
    fams = ("native_copper malachite tetrahedrite cassiterite native_gold hematite limonite magnetite galena uraninite "
            "native_osmium sphalerite bismuthinite native_silver garnierite bauxite").split()
    ladder_missing = ["firmages:%s/%s_%s" % (st, f, g) for st in ("purifying", "injecting", "dissolution") for f in fams
                      for g in ("small", "poor", "normal", "rich") if "firmages:%s/%s_%s" % (st, f, g) not in full]
    planet = re.compile(r'"c:(ores|raw_materials)/(desh|ostrum|calorite|draconium)"|"c:storage_blocks/raw_(desh|ostrum|calorite|draconium)"')
    ore_in = re.compile(r'"tag": "c:(ores|raw_materials)/|"tag": "c:storage_blocks/raw_')
    leftover = sorted(r for r, j in full.items() if r.split(":")[0] in late_ns and not j.get("type", "").startswith("minecraft:crafting")
                      and ore_in.search(json.dumps({k: v for k, v in j.items() if k not in RESULT_KEYS}))
                      and not planet.search(json.dumps(j)))
    piece_mek = sorted({full[r].get("type") for r, sl, os_ in recs if r.split(":")[0] != "firmages"
                        and full[r].get("type", "").startswith("mekanism:") and any(members(x) & pieces for x in sl)})
    check("C-6a Mekanism ore ladder: 3x/4x/5x for 16 TFC ore families x 4 grades; no Mekanism-family recipe eats an ore "
          "block, raw ore or raw block of a TFC metal or gem; ore pieces enter Mekanism only through the ladder",
          not ladder_missing and not leftover and not piece_mek,
          ["missing %d: %s" % (len(ladder_missing), ladder_missing[:5]), "leftover %s" % leftover[:10],
           "other Mekanism types on pieces %s" % piece_mek])
    # C-6b: one station per function for Mekanism (no sawmill, planting, recycling, molten metals, plate copies; the
    # crusher keeps clumps, gems and bio fuel but crushes no ingot)
    gone = {"mekanism:sawing", "mekanism:planting", "mekanism:recycling", "mekanism:melting", "mekanism:solidification",
            "mekanism:stamping", "mekanism:lathing", "mekanism:rolling_mill", "mekanism:pressing", "mekmm:stamper",
            "mekmm:lathe", "mekmm:rolling_mill", "mekmm:presser", "evolvedmekanism:melting", "evolvedmekanism:solidifying"}
    present = sorted({j.get("type") for j in full.values()} & gone)
    ingot_crush = sorted(r for r, j in full.items() if j.get("type") in ("mekanism:crushing", "moremekanismprocessing:tag_crushing")
                         and '"c:ingots/' in json.dumps(j.get("input", {})))
    steel_dust = [r for r in ("mekanism:processing/steel/enriched_iron_to_dust", "mekanism:processing/bronze/ingot/from_infusing") if r in full]
    check("C-6b Mekanism: no Precision Sawmill, planting, recycling, molten-metal, stamping/lathe/rolling/presser recipes; "
          "no ingot -> dust crushing; no Mekanism steel or bronze route", not present and not ingot_crush and not steel_dust,
          ["types %s" % present, "ingot crushing %s" % ingot_crush[:8], "steel/bronze %s" % steel_dust])
    # C-6c: AE2 processors and printed circuits only from the AE2 Inscriber
    proc = types_making(lambda o: re.match(r"^ae2:(printed_)?(logic|calculation|engineering)_processor$|^ae2:printed_silicon$", o))
    check("C-6c AE2 processors and printed circuits only from the AE2 Inscriber", proc == ["ae2:inscriber"], ["types %s" % proc])
    # C-6d: Mystical Agriculture: no seeds of the switched-off crops; no vanilla iron or coal from essences; End frame
    off = ("dirt stone wood ice coral prismarine sculk dye honey nature cow pig chicken sheep rabbit turtle squid armadillo fish "
           "steel bronze brass refined_obsidian refined_glowstone constantan electrum invar rose_gold pig_iron draconium "
           "awakened_draconium limestone marble platinum iridium apatite peridot").split()
    seeds = ["mysticalagriculture:%s_seeds" % c for c in off if makers("mysticalagriculture:%s_seeds" % c)]
    ma_vanilla = sorted(r for r, sl, os_ in recs if r.startswith("mysticalagriculture:") and os_ & {"minecraft:iron_ingot", "minecraft:coal"})
    frame = makers("minecraft:end_portal_frame")
    check("C-6d Mystical Agriculture: no seed of a switched-off crop, no vanilla iron or coal output; End portal frame "
          "crafted (age_6)", not seeds and not ma_vanilla and frame == ["firmages:crafting/end_portal_frame"]
          and item_age.get("minecraft:end_portal_frame") == "age_6",
          ["seeds %s" % seeds, "vanilla outputs %s" % ma_vanilla, "frame makers %s" % frame,
           "frame age %s" % item_age.get("minecraft:end_portal_frame")])
    # C-7a: Ad Astra processing on the pack stations; planet plates only from the IE Metal Press
    aa_types = sorted({j.get("type") for j in full.values() if j.get("type") in ("ad_astra:compressing", "ad_astra:alloying", "ad_astra:refining")})
    plates = types_making(lambda o: o in ("ad_astra:desh_plate", "ad_astra:ostrum_plate", "ad_astra:calorite_plate"))
    aa_smelt = sorted(r for r in full if re.match(r"^ad_astra:(smelting|blasting)/.+_from_(smelting|blasting)_(.+_ore|raw_.+)$", r))
    check("C-7a Ad Astra: no compressor, alloying or refinery recipes; desh, ostrum and calorite plates only from the IE "
          "Metal Press; no furnace smelting of planet ores", not aa_types and plates == ["immersiveengineering:metal_press"] and not aa_smelt,
          ["types %s" % aa_types, "plate makers %s" % plates, "furnace %s" % aa_smelt[:6]])
    # C-9a: the finale recipes
    us = full.get("firmages:fusion/ultimate_singularity", {})
    us_items = {i.get("ingredient", {}).get("item") for i in us.get("ingredients", [])}
    relics = {"firmages:" + x for x in ("hearthstone sky_disc steel_heart awakened_keystone pressure_core humming_core "
                                        "data_matrix star_chart quantum_core").split()}
    base = json.dumps(full.get("firmages:crafting/classic_stargate_base_block", {}))
    sgj_furnace = sorted(r for r in full if re.match(r"^sgjourney:(naquadah_ingot|refined_naquadah|naquadah_iron_alloy)_from_(smelting|blasting)", r))
    check("C-9a Singularity: Ultimate Singularity by chaotic fusion from the nine signature items; the Stargate base block "
          "takes it; no Stargate Journey furnace processing of naquadah",
          us.get("techLevel") == "chaotic" and relics <= us_items and us.get("catalyst", {}).get("item") == "draconicevolution:chaotic_core"
          and "firmages:ultimate_singularity" in base and not sgj_furnace,
          ["tech %s" % us.get("techLevel"), "missing relics %s" % sorted(relics - us_items),
           "base has singularity %s" % ("firmages:ultimate_singularity" in base), "SGJ furnace %s" % sgj_furnace])
    # C-G: the gear rule for every later Age. Exceptions (Doc 08 section 8): MekaSuit and Meka-Tool (trophies after
    # finale_won), MekaSuit and Draconic modules, backpack upgrades.
    exempt = re.compile(r"^mekanism:(mekasuit_|meka_tool)|module|^sophisticatedbackpacks:")
    for st in ("age_5", "age_6", "age_7", "age_8", "age_9"):
        ch = [c for c in gear_chains(st) if not exempt.search(c.split(" eats ")[0])]
        check("C-G %s gear rule: no tool or armour of %s is made from another tool or armour (exceptions: MekaSuit, "
              "Meka-Tool, modules, backpack upgrades)" % (st, st), not ch, ch)

    # ---- C-M: MekaTFC --------------------------------------------------------------------------------------------
    log = os.path.join(server_dir, "logs", "latest.log")
    parse_err = []
    if os.path.isfile(log):
        with open(log, encoding="utf-8", errors="replace") as f:
            parse_err = [l.strip()[:200] for l in f if "mekatfc" in l and re.search(r"Parsing error|Couldn't parse|Failed to parse", l)]
    ladder = sorted(r for r in full if re.match(r"^firmages:(purifying|injecting|dissolution)/native_osmium_", r))
    leftover = sorted(r for r in full if re.match(r"^mekatfc:(smelting|blasting|enriching|crushing)/ore/", r))
    raw_tag = [i for i in tags.get("c:raw_materials/osmium", []) if i.startswith("mekatfc:")]
    check("C-M MekaTFC: 0 parse errors, 12 grade recipes (3x/4x/5x), no furnace/enriching/crushing of ore pieces, "
          "pieces out of c:raw_materials/osmium",
          os.path.isfile(log) and not parse_err and len(ladder) == 12 and not leftover and not raw_tag,
          parse_err[:5] + [f"ladder recipes {len(ladder)}", f"leftover {leftover}", f"raw tag {raw_tag}"])

    # ---- C-W: Mowzie's Mobs boss drops -------------------------------------------------------------------------
    mow = {"mowziesmobs:ice_crystal": "age_4", "mowziesmobs:music_disc_petiole": "age_4", "mowziesmobs:sculptor_staff": "age_5",
           "mowziesmobs:earthrend_gauntlet": "age_5", "mowziesmobs:bluff_rod": "age_5", "mowziesmobs:geomancer_beads": "age_5",
           "mowziesmobs:geomancer_belt": "age_5", "mowziesmobs:geomancer_robe": "age_5", "mowziesmobs:geomancer_sandals": "age_5"}
    wrong_age = [f"{i}: {item_age.get(i)} != {st}" for i, st in mow.items() if item_age.get(i) != st]
    check("C-W Mowzie structure-boss drops follow the mob stage of their boss (Frostmaw age_4, Sculptor age_5)", not wrong_age, wrong_age)

    reach_checks(full, recs, items, tags, item_age, check)


# ================================================================================================ reachability per Age
# R- checks: the goal chains close at their own Age. The walk starts from what a team has at Dawn without a recipe
# (the TFC-world mods, vanilla world items and drops by the mob ladder of Doc 08 section 5, boss drops of the Age's
# checkpoint) and fires a recipe only when every ingredient is reached, every output belongs to the Age or earlier
# (the firmages-core gate) and its station exists by then (mod Age of the recipe type in dev/age_map.toml, Create
# heat levels). Runs on a dump with every Age unlocked, like the A- and C- checks.
VANILLA_BY_AGE = {
    "dawn": "feather egg bone string spider_eye rotten_flesh ink_sac glow_ink_sac clay_ball flint snowball ice snow_block "
            "charcoal stick cobweb rabbit_hide rabbit_foot leather bone_meal redstone clay sugar_cane sugar paper glass "
            "white_wool brown_mushroom red_mushroom cactus dead_bush vine lily_pad kelp obsidian",
    "age_1": "gunpowder arrow bow skeleton_skull",                       # mob_1: skeletons, creepers
    "age_2": "slime_ball phantom_membrane glowstone_dust",               # mob_2: slimes, phantoms, witches
    "age_3": "blaze_rod ghast_tear magma_cream nether_wart glowstone quartz netherrack soul_sand soul_soil basalt "
             "blackstone gold_nugget gilded_blackstone crimson_fungus warped_fungus crimson_stem warped_stem shroomlight "
             "ancient_debris magma_block crying_obsidian ender_pearl",  # mob_3 and the Beneath Nether
    "age_5": "wither_skeleton_skull",                                    # mob_5
    "age_6": "end_stone chorus_fruit chorus_flower shulker_shell purpur_block elytra dragon_head",  # the End (age_6 boss)
}
BOSS_DROPS = {"age_2": ["twilightforest:naga_scale", "twilightforest:naga_trophy", "twilightforest:lich_trophy",
                       "tfcreate:unpolished_quartz"],  # and the drop of the TFCreate quartz vein (ore family, age_2)
              # boss tokens of kubejs/server_scripts/tags/late_ages.js (Doc 08 section 5.1)
              "age_5": ["minecraft:nether_star"],                           # The Wither
              "age_6": ["minecraft:dragon_breath", "minecraft:dragon_egg",  # Ender Dragon; DE adds its heart to the dragon loot
                        "draconicevolution:dragon_heart"],
              "age_7": ["cataclysm:witherite_block"],                       # The Harbinger
              "age_8": ["cataclysm:abyssal_egg"],                           # The Leviathan
              "age_9": ["draconicevolution:chaos_shard"]}                   # Chaos Guardian
# What the rockets of the Space and Quantum Age reach without a recipe (Ad Astra planets: stone, sand, ore drops,
# Moon cheese) and the draconium ore of the End (DE, dust drop). Regexes over the registry, per Age.
LATE_WORLD = {"age_7": [r"^ad_astra:(moon|mars)_(stone|cobblestone|sand|deepslate)$", r"^ad_astra:(raw_desh|raw_ostrum|cheese)$",
                        r"^draconicevolution:draconium_dust$"],
              "age_8": [r"^ad_astra:(venus|mercury|glacio)_(stone|cobblestone|sand|deepslate)$",
                        r"^ad_astra:(raw_calorite|ice_shard)$"],
              "age_9": [r"^sgjourney:raw_naquadah$"]}  # the pack vein on Mercury (kubejs/data/firmages/worldgen)
# Stations of the late Ages as items: a recipe of these types fires only when every group has one reached item
# (the machine, or the parts of a multiblock). Earlier types keep the mod-Age model (station_ix).
_MEK = {"metallurgic_infusing": "metallurgic_infuser", "reaction": "pressurized_reaction_chamber",
        "activating": "solar_neutron_activator", "centrifuging": "isotopic_centrifuge", "chemical_infusing": "chemical_infuser",
        "compressing": "osmium_compressor", "combining": "combiner", "crushing": "crusher", "crystallizing": "chemical_crystallizer",
        "dissolution": "chemical_dissolution_chamber", "enriching": "enrichment_chamber",
        "evaporating": "thermal_evaporation_controller", "injecting": "chemical_injection_chamber",
        "nucleosynthesizing": "antiprotonic_nucleosynthesizer", "oxidizing": "chemical_oxidizer",
        "purifying": "purification_chamber", "rotary": "rotary_condensentrator", "sawing": "precision_sawmill",
        "separating": "electrolytic_separator", "washing": "chemical_washer"}
STATION_ITEMS = {f"mekanism:{t}": [[f"mekanism:{m}"]] for t, m in _MEK.items()}
STATION_ITEMS.update({
    "immersiveengineering:arc_furnace": [["immersiveengineering:graphite_electrode"], ["immersiveengineering:sheetmetal_steel"],
                                         ["immersiveengineering:heavy_engineering"], ["immersiveengineering:light_engineering"]],
    "ae2:inscriber": [["ae2:inscriber"]], "ae2:charger": [["ae2:charger"]],
    "ad_astra:compressing": [["ad_astra:compressor"]], "ad_astra:nasa_workbench": [["ad_astra:nasa_workbench"]],
    "ad_astra:refining": [["ad_astra:fuel_refinery"]], "ad_astra:cryo_freezing": [["ad_astra:cryo_freezer"]],
    "ad_astra:alloying": [["ad_astra:etrionic_blast_furnace"]], "ad_astra:oxygen_loading": [["ad_astra:oxygen_loader"]],
    "draconicevolution:fusion_crafting": [["draconicevolution:crafting_core"]],
    "sgjourney:crystallizing": [["sgjourney:crystallizer"]], "sgjourney:advanced_crystallizing": [["sgjourney:advanced_crystallizer"]],
    "sgjourney:naquadah_liquidizing": [["sgjourney:naquadah_liquidizer"]],
    "sgjourney:naquadah_heavy_liquidizing": [["sgjourney:heavy_naquadah_liquidizer"]],
})
FUSION_INJECTOR = {"draconium": "basic", "wyvern": "wyvern", "draconic": "awakened", "chaotic": "chaotic"}
# Chemicals a multiblock makes without a recipe: the fission reactor burns fissile fuel into nuclear waste, the SPS
# turns polonium into antimatter. Each needs all listed items or chemicals.
MACHINE_MADE = {"mekanism:nuclear_waste": ["mekanism:fissile_fuel", "mekanismgenerators:fission_reactor_casing",
                                           "mekanismgenerators:fission_fuel_assembly", "mekanismgenerators:control_rod_assembly",
                                           "mekanismgenerators:fission_reactor_port"],
                "mekanism:antimatter": ["mekanism:polonium", "mekanism:sps_casing", "mekanism:sps_port",
                                        "mekanism:supercharged_coil"]}
# Vanilla station blocks without a recipe in a TFC world, and the recipes that only transform such a block itself.
STATION_VANILLA = {"minecraft:bucket", "minecraft:crafting_table", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                   "minecraft:campfire", "minecraft:anvil"}
STATION_TRANSFORMS = {"mekanism:sawing/crafting_table", "create:haunting/soul_campfire"}
# Create heat levels: a heated recipe needs a heater, a superheated one a Blaze Burner (with a blaze cake).
HEAT_STATION = {"heated": ("tfcreate:primitive_heater", "create:blaze_burner"), "superheated": ("create:blaze_burner",)}
REACH_GOALS = [  # (Age, goal recipe id, chain items that must be reachable too)
    ("age_2", "firmages:crafting/steel_heart", ["create:precision_mechanism", "create:mechanical_crafter", "create:deployer",
                                                 "create:mechanical_press", "tfcreate:primitive_heater", "tfc:metal/ingot/steel",
                                                 "tfcreate:polished_quartz"]),  # Twilight portal activator (Frontier)
    ("age_3", "firmages:ritual/arcane_keystone", ARCANE_CHAIN),
    ("age_4", "firmages:crafting/pressure_core", INDUSTRIAL_CHAIN),
    ("age_5", "firmages:arc_furnace/humming_core", ELECTRIC_CHAIN),
    ("age_6", "firmages:crafting/data_matrix", INFORMATION_CHAIN),
    ("age_7", "firmages:fusion/star_chart", SPACE_CHAIN),
    ("age_8", "firmages:fusion/quantum_core", QUANTUM_CHAIN),
    ("age_9", "firmages:fusion/ultimate_singularity", FINALE_CHAIN),
    ("age_9", "firmages:crafting/classic_stargate_base_block", STARGATE_CHAIN),
]


def reach_checks(full, recs, items, tags, item_age, check):
    try:
        import tomllib
        with open(os.path.join(REPO, "dev", "age_map.toml"), "rb") as f:
            amap = tomllib.load(f)
    except (ImportError, OSError):
        amap = {}
    mod_age = amap.get("mods", {})
    # ore pieces of the non-TFC ore veins ([ore_families]: "ns:name" = Age), mined in the world from their Age
    ore_pieces = collections.defaultdict(set)
    for fam, st in amap.get("ore_families", {}).items():
        if "{rock}" in fam or st not in AGES:
            continue
        ns, name = fam.split(":", 1)
        ore_pieces[st] |= {i for i in items if i.startswith(ns + ":ore/") and name in i}

    def age_ix(i):
        return AGES.index(item_age[i]) if item_age.get(i) in AGES else -1

    def station_ix(rid):
        st = mod_age.get(full[rid].get("type", "minecraft:x").split(":")[0], "dawn")
        return AGES.index(st) if st in AGES else 0

    def station_groups(rid):
        t = full[rid].get("type", "")
        groups = list(STATION_ITEMS.get(t, []))
        if t == "draconicevolution:fusion_crafting":
            lvl = FUSION_INJECTOR.get(str(full[rid].get("techLevel", "draconium")).lower(), "basic")
            groups.append([f"draconicevolution:{lvl}_crafting_injector"])
        return groups

    def start(n):
        have = {i for i in items if i.split(":")[0] in WORLD_NS}
        for st, words in VANILLA_BY_AGE.items():
            if AGES.index(st) <= n:
                have |= {"minecraft:" + w for w in words.split()}
        for st, drops in BOSS_DROPS.items():
            if AGES.index(st) <= n:
                have |= set(drops)
        for st, pats in LATE_WORLD.items():
            if AGES.index(st) <= n:
                have |= {i for i in items if any(re.search(p_, i) for p_ in pats)}
        for st, got in ore_pieces.items():
            if AGES.index(st) <= n:
                have |= got
        have |= set(MOB_DROPS)
        return {i for i in have if age_ix(i) <= n and item_age.get(i) != "disabled"}

    def walk(n):
        have = start(n)
        heat = {rid: HEAT_STATION.get(full[rid].get("heat_requirement")) for rid, _, _ in recs}
        stations = {rid: station_groups(rid) for rid, _, _ in recs}
        live = [(rid, sl, set(os_)) for rid, sl, os_ in recs
                if station_ix(rid) <= n and os_ and all(age_ix(o) <= n and item_age.get(o) != "disabled" for o in os_)]

        def sat(slot):
            return any((k in ("item", "chem") and x in have) or (k == "tag" and any(i in have for i in tags.get(x, [])))
                       for k, x in slot)
        grew = True
        while grew:
            grew = False
            for rid, sl, os_ in live:
                new = os_ - have
                if not new or not all(sat(x) for x in sl):
                    continue
                if heat[rid] and not any(h in have for h in heat[rid]):
                    continue
                if not all(any(i in have for i in grp) for grp in stations[rid]):
                    continue
                have |= new
                grew = True
            for chem, needs in MACHINE_MADE.items():
                if chem not in have and all(x in have for x in needs):
                    have.add(chem)
                    grew = True
            for seed, got in GROWN.items():
                new = {g for g in got if seed in have and GROWN_NEEDS.get(seed, seed) in have and g not in have and age_ix(g) <= n}
                if new:
                    have |= new
                    grew = True
        return have, live, sat

    def first_item(slot):
        k, x = slot[0]
        return x if k == "item" else (tags.get(x) or [x])[0]

    def why(item, have, live, sat, n, depth=0, seen=None):
        """Lines that explain why `item` is not reached at Age n (the unreached ingredients of up to 3 makers)."""
        seen = set() if seen is None else seen
        if item in seen or depth > 3:
            return []
        seen.add(item)
        pad = "      " + "  " * depth
        if age_ix(item) > n or item_age.get(item) == "disabled":
            return [f"{pad}{item} belongs to {item_age.get(item)}"]
        makers = [(rid, sl) for rid, sl, os_ in live if item in os_]
        if not makers:
            return [f"{pad}{item}: no recipe at {AGES[n]} (station Age, output Age) and not in the start set"]
        out = []
        for rid, sl in makers[:3]:
            miss = [s for s in sl if not sat(s)]
            label = [" | ".join(x for _, x in s)[:80] for s in miss][:4] or ["heat source"]
            out.append(f"{pad}{item} <- {rid}: unreached {label}")
            for s in miss[:2]:
                out += why(first_item(s), have, live, sat, n, depth + 1, seen)
        return out

    # R-0: no recipe of a mod asks for a vanilla station block that a TFC world cannot make
    # (kubejs/server_scripts/recipes/tfc_station_inputs.js; the magic mods are in recipes/age_3/arcane_tfc_inputs.js)
    have_all = walk(AGES.index("age_9"))[0]
    blocked = sorted({f"{rid}: {v}" for rid, sl, _ in recs if not rid.startswith("minecraft:") and rid not in STATION_TRANSFORMS
                     for x in sl for k, v in x if len(x) == 1 and k == "item" and v in STATION_VANILLA and v not in have_all})
    check(f"R-0 no recipe needs a vanilla station block without a source in the pack ({len(STATION_VANILLA)} blocks)",
          not blocked, blocked)
    for st, rid, chain in REACH_GOALS:
        n = AGES.index(st)
        j = full.get(rid)
        if j is None:
            check(f"R-{st} goal recipe {rid} present", False, [rid])
            continue
        have, live, sat = walk(n)
        goal = _outs(j, [])
        lines, bad = [], []
        for s in _slots(j, []):
            ok_ = sat(s)
            lines.append(f"{'ok' if ok_ else 'NO'}  {' | '.join(x for _, x in s)[:100]}")
            if not ok_:
                bad += why(first_item(s), have, live, sat, n)
        chain_bad = [c for c in chain if c not in have]
        for c in chain_bad:
            bad += why(c, have, live, sat, n)
        goal_ok = bool(goal) and all(g in have for g in goal)
        print(f"    R-{st} {rid}: {len(have)} items reached at {st} from the Dawn start set ({len(start(n))} items)")
        check(f"R-{st} {goal[0] if goal else rid}: every goal ingredient, the goal and its chain ({len(chain)} items) are "
              f"reachable at {st} (outputs of {st} or earlier, stations by {st})",
              goal_ok and not chain_bad and all(x.startswith("ok") for x in lines),
              lines + [f"goal reached: {goal_ok}", f"chain items not reached: {chain_bad}"] + bad)


if __name__ == "__main__":
    main()
