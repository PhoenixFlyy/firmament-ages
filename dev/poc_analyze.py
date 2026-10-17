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
D-1 (any dump with recipes_full.json) checks Dawn's ground crafting (firmages-core SPEC 19.3): the inventory grid is
closed in Dawn, so every crafting recipe open in Dawn must fit a TerraFirmaCraft pile of at most four placed items
(a 2x2 shape or at most four shapeless ingredients, no log beside other parts: a log lies alone on a block).
C-LB (any dump with recipes_full.json and laserbridges recipes) checks that no Laser Bridges recipe takes an End
crystal: recipes/age_7/laser_bridges.js replaces the two laser-source recipes with desh-plate ones.
With --baseline <dir> (the fa_dump files of a world with every Age unlocked) they also check that every baseline
recipe whose outputs are all unlocked is present, so the gate drops nothing more. G-3 (dump with every Age unlocked)
finds recipes whose chance byproduct is later than their main output and station and checks that
kubejs/server_scripts/recipes/byproducts.js strips it; G-4 compares the recipe types without a detected output in
<server>/logs/firmages-recipe-audit.txt with dev/data/accepted_undetected.json.
The ProgressiveStages recipe locks stay in the pack as a second layer; the A- and C- checks read them and need the
whole recipe set, so they run only on a dump where dawn to age_9 are unlocked (after "firmages ages simulate grant").
E-1 (same dump) rebuilds what EMI shows at each Age from the Bronze Age on: the gate mirror of G-1 and G-2, the
ProgressiveStages recipe locks and the firmages-core EMI predicate. It lists recipe types shown before their station's
Age and shown recipes whose input lists a locked item.

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
FLUID_AGE_TAGS = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "fluid", "age_fluids")


def load_fluid_ages():
    """fluid id -> Age (or "disabled") from the generated age_fluids tags; empty when they are not generated."""
    out = {}
    for st in AGES + ["disabled"]:
        path = os.path.join(FLUID_AGE_TAGS, f"{st}.json")
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as f:
                for v in json.load(f)["values"]:
                    out[v["id"] if isinstance(v, dict) else v] = st
    return out
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
    unify_check(tags, item_age, check)
    ground_craft_check(base, open_in("dawn", [r for r in rows if r["type"] == "minecraft:crafting"]), check)
    laser_crystal_check(base, rows, check)
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
                    if r["result"] and item_age.get(r["result"]) in AGES
                    and AGES.index(item_age.get(r["result"], "dawn")) > AGES.index(st)]
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
    # Canonical non-TFC results by design (Doc 10 v3 8.1): Mekanism osmium, dusts (sulfur), Mekanism steel casing and
    # the pack's osmium double ingot (recipes/age_6/osmium.js).
    by_design = re.compile(r"^(mekanism:(ingot_osmium|dust_sulfur|steel_casing)|firmages:metal/double_ingot/osmium)$")
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

    emi_checks(base, rows, tags, item_age, check)
    wood_checks(base, rows, tags, item_age, check)
    sapling_checks(item_age, check)
    world_checks(base, item_age, check)
    content_checks(base, a.server_dir, rows, by_id, tags, item_age, check)

    print(f"\n{len(failed)} FAIL: {failed}" if failed else "\nall checks PASS")
    sys.exit(1 if failed else 0)


# ================================================================================================ ground crafting (D-1)
# Dawn crafting recipes that need a 3x3 grid on purpose: TFC's stick bunch and bundle (nine sticks) are on the Dawn
# whitelist (Doc 08 section 9.2) but only the Stone Age workbench can make them; nothing in Dawn needs them.
GROUND_CRAFT_ALLOWLIST = {
    "tfc:crafting/stick_bunch": "nine sticks, 3x3: Stone Age workbench",
    "tfc:crafting/stick_bundle": "nine stick bunches, 3x3: Stone Age workbench",
}
# Items TFC sizes very_large or huge (data/tfc/tfc/item_size): one lies alone on a block (maxPlacedItemSize LARGE).
GROUND_CRAFT_ALONE = re.compile(r"(^|[:/_])(logs?|wood)($|[/_])")


def _shape(o):
    """The (pattern, ingredients) of a crafting recipe JSON, looking through TFC's wrapping recipe types."""
    if not isinstance(o, dict):
        return None, None
    if isinstance(o.get("pattern"), list):
        return o["pattern"], [v for v in (o.get("key") or {}).values()]
    if isinstance(o.get("ingredients"), list):
        return None, o["ingredients"]
    for k in ("recipe", "base"):
        if isinstance(o.get(k), dict):
            return _shape(o[k])
    return None, None


def _ingredient_names(ing):
    out = []
    if isinstance(ing, list):
        for x in ing:
            out += _ingredient_names(x)
    elif isinstance(ing, dict):
        for k in ("item", "tag"):
            if isinstance(ing.get(k), str):
                out.append(ing[k])
        for k in ("ingredient", "children", "base"):
            if k in ing:
                out += _ingredient_names(ing[k])
    return out


LASER_SOURCES = ("laserbridges:laser_source_block", "laserbridges:laser_fence_source_block")


def laser_crystal_check(base, rows, check):
    """C-LB (round G1 review): Laser Bridges & Doors 6.2 builds its two laser sources from iron ingots and an End
    crystal (its library Deimos adds them as deimos:deimosgeneratedcrafting<n>); kubejs/server_scripts/recipes/age_7/
    laser_bridges.js removes both and adds desh-plate recipes under firmages:laserbridges/*. No recipe that makes a
    laserbridges item may take minecraft:end_crystal. Needs recipes_full.json; a dump without laserbridges recipes
    (the gate drops them before the Space Age) has nothing to check."""
    made = sorted((r for r in rows if (r["result"] or "").startswith("laserbridges:")), key=lambda r: r["id"])
    path = os.path.join(base, "recipes_full.json")
    if not made or not os.path.isfile(path):
        why = "no recipe makes a laserbridges item in this dump" if not made else f"{path} missing (run fa_dump_full)"
        print(f"[SKIP] C-LB Laser Bridges without End crystal: {why}")
        return
    full = json.load(open(path, encoding="utf-8"))
    crystal = []
    for r in made:
        raw = full.get(r["id"])
        text = raw if isinstance(raw, str) else json.dumps(raw)
        if "minecraft:end_crystal" in (text or ""):
            crystal.append(f"{r['id']} -> {r['result']} takes minecraft:end_crystal")
    pack = {r["result"] for r in made if r["id"].startswith("firmages:laserbridges/")}
    missing = [f"no firmages:laserbridges/* recipe makes {s}" for s in LASER_SOURCES if s not in pack]
    check("C-LB no Laser Bridges recipe takes an End crystal; both laser sources have their desh-plate pack recipe "
          "(recipes/age_7/laser_bridges.js)", not crystal and not missing,
          crystal + missing or [f"{len(made)} laserbridges recipes, none with an End crystal: "
                                + ", ".join(r["id"] for r in made[:8])])


def ground_craft_check(base, dawn_open, check):
    """D-1: every crafting recipe open in Dawn fits a placed-item pile of at most four (SPEC 19.3)."""
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        print(f"[SKIP] D-1 Dawn ground crafting: {path} missing (run fa_dump_full)")
        return
    full = json.load(open(path, encoding="utf-8"))
    bad, fits, allowed = [], 0, []
    for r in sorted(dawn_open, key=lambda r: r["id"]):
        if r["id"] in GROUND_CRAFT_ALLOWLIST:
            allowed.append(f"{r['id']}: {GROUND_CRAFT_ALLOWLIST[r['id']]}")
            continue
        raw = full.get(r["id"])
        pattern, ings = _shape(json.loads(raw) if isinstance(raw, str) else raw)
        if ings is None:
            bad.append(f"{r['id']}: no pattern or ingredients in its JSON (special recipe?)")
            continue
        if pattern is not None:
            cells = [c for row in pattern for c in row if c != " "]
            if len(pattern) > 2 or max((len(row) for row in pattern), default=0) > 2:
                bad.append(f"{r['id']}: shaped {len(pattern)} rows x {max(len(row) for row in pattern)} wide")
                continue
            n = len(cells)
        else:
            n = len(ings)
            if n > 4:
                bad.append(f"{r['id']}: shapeless with {n} ingredients")
                continue
        names = [x for ing in ings for x in _ingredient_names(ing)]
        if n > 1 and any(GROUND_CRAFT_ALONE.search(x.split(":", 1)[-1]) for x in names):
            bad.append(f"{r['id']}: a log lies alone, but the recipe has {n} parts ({', '.join(names)})")
            continue
        fits += 1
    check(f"D-1 every crafting recipe open in Dawn fits a ground pile of at most four (SPEC 19.3): {fits} fit, "
          f"{len(allowed)} allowlisted", not bad and fits > 0, bad + allowed)


# ================================================================================================ unification (U-1)
UNIFY_ALLOWLIST = {}  # tag -> reason; tags where two or more visible members are intended (empty: umbrellas go by rule)
AU_DEFAULT_MATERIALS = [
    "aeternium", "aluminum", "amber", "apatite", "bitumen", "brass", "bronze", "chrome", "cinnabar", "cobalt",
    "constantan", "copper", "diamond", "electrum", "elementium", "emerald", "enderium", "fluorite", "gold", "graphite",
    "invar", "iridium", "iron", "lapis", "lead", "lumium", "mithril", "netherite", "nickel", "obsidian", "osmium",
    "peridot", "platinum", "potassium_nitrate", "ruby", "sapphire", "signalum", "silver", "steel", "sulfur", "tin",
    "tungsten", "uranium", "zinc"]
# A last id segment that one mod uses for this many item forms names a material of that mod
# (tfc:wood/log/acacia, tfc:wood/planks/acacia, tfc:wood/twig/acacia, ...; tfc:powder/saltpeter has only 2 forms).
ID_MATERIAL_FORMS = 3


def umbrella_materials(t, vis, rx, tags, materials, id_forms):
    """The material of each visible member when tag `t` is an umbrella tag, else None.

    An umbrella groups several materials under one name of the family (c:rods/all_metal, c:storage_blocks/alloys,
    c:rods/wooden). `t` is one when its last path segment is not an AU material and its visible members are of
    pairwise different materials. A member's materials, first rule that gives any:
      1. per-material tags of the same family that hold it: the other tags of the family pattern (c:rods/steel for
         c:rods/all_metal) and the tags nested under `t` (c:storage_blocks/alloys/infused);
      2. the material in its id: the last path segment when the mod uses it for at least ID_MATERIAL_FORMS item forms
         (the twig of each wood species: tfc:wood/twig/acacia);
      3. none of these: the plain item of the umbrella (minecraft:stick in c:rods/wooden) takes the umbrella's own
         segment, so a second plain member, or a member whose id material is that segment, makes it no umbrella.
    A real one-material tag outside the AU placeholders (c:dusts/saltpeter with IE dust and TFC powder) is no umbrella:
    both members end up with the same material by rule 2 or 3."""
    prefix, seg = t.rsplit("/", 1)
    if seg in materials:
        return None
    per_mat = {}  # per-material tag -> its material
    for o in tags:
        if o == t:
            continue
        m = rx.match(o)
        if m:
            per_mat[o] = m.group(1)
        elif o.startswith(t + "/"):
            per_mat[o] = o[len(t) + 1:]
    held = collections.defaultdict(set)
    for o, mat in per_mat.items():
        for i in tags[o]:
            held[i].add(mat)
    out = {}
    for i in vis:
        mats = held.get(i)
        if not mats:
            ns, path = i.split(":", 1)
            last = path.rsplit("/", 1)[-1] if "/" in path else None
            mats = {last} if last and id_forms.get((ns, last), 0) >= ID_MATERIAL_FORMS else {seg}
        out[i] = mats
    seen = set()
    for mats in out.values():
        if mats & seen:
            return None
        seen |= mats
    return out


def unify_check(tags, item_age, check):
    """U-1: every tag of a unify family (materials.json "tags", e.g. c:ingots/{material}) shows exactly one item in EMI.

    Visible = not in age_items/disabled, not in c:hidden_from_recipe_viewers, not hidden by Almost Unified. AU
    (recipe_viewer_hiding) hides every member but its target in each tag whose material is in AU's placeholders.json
    and that is not in ignored_tags; ignored_items take no part. The pack ships config/almostunified/placeholders.json
    (AU's default list plus the pack's materials); without it AU uses its default list (AU_DEFAULT_MATERIALS). Tags
    with no visible member are fine when nothing was hidden by AU (the material is disabled as a whole); a tag whose AU
    target is disabled or hidden while AU hides the usable members is a violation.
    Umbrella tags (umbrella_materials: last segment not a material, one visible member per material) are skipped by
    rule and printed as "umbrella" notes; UNIFY_ALLOWLIST stays for intended exceptions and is empty."""
    au = load_au()
    try:
        ph = json.load(open(AU_PLACEHOLDERS, encoding="utf-8"))
        materials = set(ph["material"])
    except (OSError, ValueError, KeyError):
        materials = set(AU_DEFAULT_MATERIALS)
    ignored_items = set(au.get("ignored_items", []))
    ignored_tags = set(au.get("ignored_tags", []))
    off = {i for i, st in item_age.items() if st == "disabled"} | set(tags.get("c:hidden_from_recipe_viewers", []))
    fams = [re.compile("^" + re.escape(p).replace(re.escape("{material}"), "([a-z0-9_]+)") + "$")
            for p in au.get("tags", [])]
    family, family_rx = {}, {}  # tag -> material, tag -> its family pattern
    for t in tags:
        for rx in fams:
            m = rx.match(t)
            if m:
                family[t] = m.group(1)
                family_rx[t] = rx
    id_forms = collections.Counter()  # (namespace, last id segment) -> item forms using it
    for i in set(item_age) | {m for ms in tags.values() for m in ms}:
        ns, path = i.split(":", 1)
        if "/" in path:
            id_forms[(ns, path.rsplit("/", 1)[1])] += 1
    au_hidden, targets = set(), {}
    for t, mat in family.items():
        if mat not in materials or t in ignored_tags:
            continue
        mem = [m for m in tags[t] if m not in ignored_items]
        tgt = au_target(t, mem, au, fallback_first=False)
        if len(mem) > 1 and tgt:
            targets[t] = tgt[0]
            au_hidden |= set(mem) - set(tgt)
    bad, umbrellas = [], []
    for t in sorted(family):
        vis = [m for m in tags[t] if m not in off and m not in au_hidden]
        lost = [m for m in tags[t] if m in au_hidden and m not in off]
        if t in UNIFY_ALLOWLIST or len(vis) == 1 or (not vis and not lost):
            continue
        um = umbrella_materials(t, vis, family_rx[t], tags, materials, id_forms) if len(vis) > 1 else None
        if um:
            mats = sorted({x for ms in um.values() for x in ms})
            umbrellas.append(f"umbrella {t}: {len(vis)} visible, one per material ({', '.join(mats)})")
            continue
        mat = family[t]
        how = (f"AU target {targets[t]}" if t in targets else
               "not unified: material not in AU placeholders" if mat not in materials else
               "not unified: ignored tag" if t in ignored_tags else "not unified: no member of a priority mod")
        bad.append(f"{t} ({how}): visible {vis}" + (f", AU hid {lost}" if not vis else ""))
    check(f"U-1 one visible item per unify tag ({len(family)} tags, {len(umbrellas)} umbrella, "
          f"{len(UNIFY_ALLOWLIST)} allowlisted)", not bad, bad + umbrellas)


# ================================================================================================ gate checks (M2)
# Mirrors the rules of firmages-core gate/GateRules independently: deny/allow lists are empty in the pack, exempt types
# are world data; an output item locks when its Age tag is locked or it is in age_items/disabled; a tag output locks
# only when it has members and all are locked. Fluid outputs ("fluid" values and *_fluid keys) lock by the age_fluids
# tags; an "id" of unknown kind is checked as item and as fluid, the later lock wins (GateRules anyIds). Fluid tag
# outputs are not mirrored (the dump has no fluid tags).
GATE_EXEMPT = {"immersiveengineering:mineral_mix", "tfc:collapse", "tfc:landslide"}
_GATE_OUT_KEY = re.compile(r"^(results?|outputs?|.*_outputs?|.*_results?|outputs?_.*|results?_.*|.*_fluid)$")
# IE keys that the walker regex misses but the gate's IE extractor reads by its field scan (sawmill, arc furnace).
_GATE_IE_KEYS = ("secondaryOutputs", "strippingSecondaries", "slag")
# Mekanism-family serializers resolve a tag output to one stack the JSON does not name (the gate reads it through
# getOutputDefinition); the mirror cannot tell which member, so G-2 lists these instead of failing.
_GATE_MEK_TYPES = re.compile(r"^(mekanism|mekanismgenerators|evolvedmekanism|mekmm|moremekanismprocessing):")


def _gate_outs(o, items, tags_, inres=False, fluids=None):
    """Item ids (and ids of unknown kind), item tags and, with a `fluids` set, fluid ids in the output subtrees of a
    recipe JSON. Without `fluids`, "fluid" values count as items (the old mirror, used by the byproduct checks)."""
    if isinstance(o, dict):
        for k, v in o.items():
            kl = k.lower()
            if any(w in kl for w in ("input", "ingredient", "catalyst", "reagent", "condition")) or kl in ("key", "pattern", "components"):
                continue
            r = inres or bool(_GATE_OUT_KEY.match(kl))
            if r and kl in ("id", "item") and isinstance(v, str):
                items.add(v)
            elif r and kl == "fluid" and isinstance(v, str) and fluids is not None:
                fluids.add(v)
            elif r and kl == "tag" and isinstance(v, str):
                tags_.add(v)
            elif r and kl.endswith("_fluid") and isinstance(v, str) and fluids is not None:
                fluids.add(v)
            else:
                _gate_outs(v, items, tags_, r, fluids)
    elif isinstance(o, list):
        for v in o:
            _gate_outs(v, items, tags_, inres, fluids)
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
    fluid_age = load_fluid_ages()

    def locked(st):
        return st if st == "disabled" or (st in AGES and st not in unlocked) else None

    def lock_of(item):
        return locked(item_age.get(item))

    def any_lock(i):  # an id of unknown kind: item and fluid, the later lock (GateRules anyIds)
        ls = [x for x in (lock_of(i), locked(fluid_age.get(i))) if x]
        return max(ls, key=order.index) if ls else None

    def locks(rid, result, j, tagmap, special=None):
        """(output, stage) for every output that keeps the recipe out at the unlocked set.
        A special recipe (its JSON names no output, e.g. IE powerpack attach) is only judged by getResultItem;
        with a `special` list it goes there instead, because its real output depends on its inputs."""
        items, tg, fl = set(), set(), set()
        if j is not None:
            _gate_outs(j, items, tg, fluids=fl)
            for k in _GATE_IE_KEYS:
                if j.get(k) is not None:
                    _gate_outs({"results": j[k]}, items, tg, fluids=fl)
        if special is not None and not items and not tg and not fl:
            if result and lock_of(result):
                special.append(f"{rid} -> {result} ({lock_of(result)})")
            return []
        if result:
            items.add(result)
        out = [(i, any_lock(i)) for i in sorted(items) if any_lock(i)]
        out += [(f"fluid {f}", locked(fluid_age.get(f))) for f in sorted(fl) if locked(fluid_age.get(f))]
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
    check(f"G-1 no loaded recipe makes an item or fluid of a locked Age or of age_items/disabled ({len(rows)} recipes, "
          f"{len(fluid_age)} fluids with an Age)", not bad, bad)
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
        """Age that keeps a recipe out by its station (recipes/station_ages.js, the Create heat levels of the
        firmages-core gate, the 4x/5x ore ladder and the blue steel infuser of the pack scripts), or None."""
        for t in (r["type"], (j or {}).get("type", "")):
            if station_age.get(t) and station_age[t] not in unlocked:
                return station_age[t]
        if r["type"] in HEAT_TYPES or (j or {}).get("type", "") in HEAT_TYPES:
            # GateRules: the heat level is met by any of its sources; a source without an Age is always there.
            srcs = [i for i in HEAT_STATION.get((j or {}).get("heat_requirement"), ()) if item_age.get(i) != "disabled"]
            ages = [item_age.get(i) for i in srcs]
            if srcs and all(a in AGES for a in ages) and not any(a in unlocked for a in ages):
                return min(ages, key=AGES.index)
        for rx, st in LATE_LOADED:
            if re.match(rx, r["id"]) and st not in unlocked:
                return st
        return None
    superseded = 0
    for r in brows:
        ls = [] if r["type"] in GATE_EXEMPT else locks(r["id"], r["result"], bfull.get(r["id"]), btags)
        sl = None if ls else station_lock(r, bfull.get(r["id"]))
        until = re.match(r"^firmages:byproducts/.+/before_(age_\d)$", r["id"])
        until_age = until.group(1) if until else next((st for rx, st in UNTIL_LOADED if re.match(rx, r["id"])), None)
        if until_age and until_age in unlocked and r["id"] not in have:
            # recipes/byproducts.js: a copy without locked byproducts lives only until its Age (FirmAges.untilAge);
            # a baseline taken with gate.enabled = false still holds it, the gate drops it as superseded
            superseded += 1
        elif ls:
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
    print(f"    byproduct copies and stand-ins superseded by their unlocked Age (FirmAges.untilAge): {superseded}")
    check("G-2 every baseline recipe whose outputs are all unlocked is loaded (the gate drops nothing more)",
          not missing, missing)
    print(f"    Mekanism-family tag outputs the mirror cannot resolve (dropped by the gate's resolved stack): "
          f"{len(unresolved)} {unresolved[:5]}")
    print(f"    recipes here that the baseline lacks: {len(extra)} {extra[:10]}")


STATION_AGES_JS = os.path.join(REPO, "kubejs", "server_scripts", "recipes", "station_ages.js")
# Pack recipes that the gate holds until a later Age than their outputs (FirmAges.requireAge in the recipe scripts).
LATE_LOADED = [(r"^firmages:injecting/", "age_7"), (r"^firmages:dissolution/", "age_8"),
               (r"^firmages:metallurgic_infusing/blue_steel$", "age_7"),
               (r"^create:sequenced_assembly/black_and_white_film_developing$", "age_2"),  # recipes/exposure.js
               (r"^ae2:network/(upgrade_)?wireless_crafting_terminal$", "age_6"),  # recipes/age_4/wireless_terminal.js
               (r"^firmages:crafting/belt_connector_rubber$", "age_2")]  # recipes/age_2/links.js
# Pack recipes that exist only while an Age is locked (FirmAges.untilAge; the byproduct copies of byproducts.js are
# matched by their id pattern): from that Age on a later recipe replaces them.
UNTIL_LOADED = [(r"^firmages:mechanical_crafting/wireless_crafting_terminal$", "age_6")]  # recipes/age_4/wireless_terminal.js


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
            # the recipe cannot run before its ingredients exist either (each slot: its earliest member), so a
            # byproduct no later than the last ingredient costs nothing (items moved by dev/derive_ages.py)
            ins = [min((age_of(i) for k, x in sl for i in ([x] if k == "item" else tags.get(x, []) if k == "tag" else [])),
                       default=0) for sl in _slots(j, [])]
            need = max([need] + ins)
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
        # before the byproduct Ages the copy firmages:byproducts/<ns>/<path>/before_<age> stands in for the original
        copy = "firmages:byproducts/" + rid.replace(":", "/", 1) + "/before_"
        if need <= top and rid not in have and not any(h.startswith(copy) for h in have):
            absent.append(f"{rid}: main output and station are open at {AGES[top]} but neither the recipe nor a copy is loaded")
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


def world_bucket(i):
    """A vanilla-bucket item of a world namespace ("tfc:bucket/olive_oil", "afc:bucket/maple_syrup"): it comes with the
    bucket (Electric Age), not from the world (dev/derive_ages.py WORLD_BUCKETS)."""
    return i.split(":")[0] in WORLD_NS and i.split(":", 1)[1].startswith("bucket/")
VANILLA_WORLD = ("feather egg bone string spider_eye rotten_flesh gunpowder slime_ball ink_sac glow_ink_sac ender_pearl "
                 "blaze_rod ghast_tear magma_cream nether_wart glowstone_dust glowstone quartz netherrack soul_sand soul_soil "
                 "basalt blackstone obsidian gold_nugget clay_ball flint snowball ice snow_block charcoal stick cobweb "
                 "rabbit_hide rabbit_foot wither_skeleton_skull skeleton_skull phantom_membrane leather arrow bow magma_block "
                 "gilded_blackstone crimson_fungus warped_fungus crimson_stem warped_stem shroomlight ancient_debris bone_meal "
                 "redstone clay sugar_cane sugar paper glass white_wool brown_mushroom red_mushroom cactus dead_bush vine "
                 "lily_pad kelp crying_obsidian " + " ".join(f"{w}_log {w}_sapling" for w in (
                     "oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry")) + " mangrove_log mangrove_propagule").split()
MOB_DROPS = ["ars_nouveau:wilden_horn", "ars_nouveau:wilden_spike", "ars_nouveau:wilden_wing", "ars_nouveau:starbuncle_shards",
             "ars_nouveau:wilden_tribute",   # the optional Wilden Chimera; the Enchanting Apparatus makes it too
             "occultism:datura_seeds",   # pack loot modifier on TFC grass (kubejs/data/firmages/loot_modifiers)
             "occultism:tallow"]         # butcher knife on TFC animals (entity tags in tags/arcane_industrial.js)
# World processes the recipe graph does not see: saplings grow into trees, crops ripen, ore blocks drop, a vanilla
# bucket fills at a water source or a lava pool (the bucket has a recipe from the Electric Age since 0.10.0).
GROWN = {"occultism:otherworld_sapling_natural": ["occultism:otherworld_log"], "occultism:datura_seeds": ["occultism:datura"],
         "ars_nouveau:magebloom_crop": ["ars_nouveau:magebloom"],
         "theurgy:sal_ammoniac_ore": ["theurgy:sal_ammoniac_crystal"],
         "minecraft:bucket": ["minecraft:water_bucket", "minecraft:lava_bucket"]}
# Datura and Magebloom break on TFC farmland (setblock test); they need vanilla farmland (the pack's Arcane Soil).
GROWN_NEEDS = {"occultism:datura_seeds": "minecraft:farmland", "ars_nouveau:magebloom_crop": "minecraft:farmland"}
for _c in ("blue", "red", "green", "purple"):
    GROWN[f"ars_nouveau:{_c}_archwood_sapling"] = [f"ars_nouveau:{_c}_archwood_log"]
GROWN["ars_elemental:yellow_archwood_sapling"] = ["ars_elemental:yellow_archwood_log"]   # Ars Elemental's flashing archwood
# Industrial Age station parts (multiblocks, power, logistics) that must be makeable.
INDUSTRIAL_CHAIN = ("immersiveengineering:cokebrick immersiveengineering:blastbrick immersiveengineering:blastbrick_reinforced "
                    "immersiveengineering:alloybrick immersiveengineering:treated_wood_horizontal immersiveengineering:heavy_engineering "
                    "immersiveengineering:light_engineering immersiveengineering:steel_scaffolding_standard immersiveengineering:radiator "
                    "immersiveengineering:conveyor_basic immersiveengineering:coil_lv immersiveengineering:wirecoil_copper "
                    "immersiveengineering:capacitor_lv immersiveengineering:connector_lv immersiveengineering:craftingtable "
                    "immersiveengineering:workbench immersiveengineering:component_iron immersiveengineering:component_steel "
                    "immersiveengineering:hammer createaddition:alternator createaddition:electric_motor create:packager "
                    "create:package_frogport create:stock_link create:stock_ticker create:chain_conveyor create_jetpack:jetpack "
                    "firmages:arcane_gearbox firmages:pressure_core immersiveengineering:sheetmetal_steel "
                    "immersiveengineering:rs_engineering minecraft:hopper advancedtfctech:pirn").split()
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
                  "sgjourney:naquadah "   # pure naquadah in the arc furnace (f1: it was a quest reward only)
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
    "theurgy:liquefaction_cauldron theurgy:digestion_vat theurgy:distiller theurgy:incubator theurgy:mercury_catalyst theurgy:divination_rod_t1 "
    "theurgy:sal_ammoniac_accumulator firmages:arcane_keystone").split()


_REGISTRY_ITEMS = None


def _registry_items():
    """Item ids of dev/data/registry.json (the snapshot of the last /fa_dump), read once."""
    global _REGISTRY_ITEMS
    if _REGISTRY_ITEMS is None:
        try:
            _REGISTRY_ITEMS = set(json.load(open(os.path.join(REPO, "dev", "data", "registry.json"), encoding="utf-8"))["items"])
        except (OSError, ValueError, KeyError):
            _REGISTRY_ITEMS = set()
    return _REGISTRY_ITEMS


_EVERYCOMP = None


def _everycomp_index():
    """(target namespace, path) -> Every Compat item id, from ids like everycomp:mcb/tfc/oak_log_bridge_middle."""
    global _EVERYCOMP
    if _EVERYCOMP is None:
        _EVERYCOMP = {}
        for i in _registry_items():
            parts = i.split(":", 1)[1].split("/")
            if i.startswith("everycomp:") and len(parts) == 3:
                _EVERYCOMP.setdefault((parts[1], parts[2]), i)
    return _EVERYCOMP


def _wood_swap(o):
    """The item a Moonlight block_type_swap ingredient stands for. Every Compat writes its recipes with the ingredient
    of the source wood ("minecraft:oak_log", "mcwbridges:oak_log_bridge_middle") and from/to wood types
    ("minecraft:oak" -> "tfc:oak"); in game Moonlight swaps the item for the same part of the target wood. Vanilla
    parts map to the first id of the item registry among the usual layouts (TFC/AFC "<ns>:wood/<part>/<wood>" and
    "<ns>:wood/planks/<wood>_<part>", else "<ns>:<wood>_<part>"); a part of another mod maps to Every Compat's own
    variant (everycomp:<module>/<ns>/<path with the target wood>). An ingredient without the source wood in its id
    (a stick, calcite) or without a match stays as written."""
    inner = o.get("ingredient")
    item = inner.get("item") if isinstance(inner, dict) else None
    src, dst = str(o.get("from", "")), str(o.get("to", ""))
    if not (isinstance(item, str) and ":" in src and ":" in dst):
        return item
    sns, swood = src.split(":", 1)
    dns, dwood = dst.split(":", 1)
    ins, ipath = item.split(":", 1)
    reg = _registry_items()
    if ins == sns:
        part = None
        if ipath.startswith(swood + "_"):
            part = ipath[len(swood) + 1:]
        elif ipath.startswith("stripped_" + swood + "_"):
            part = "stripped_" + ipath[len("stripped_" + swood) + 1:]
        if part:
            for cand in (f"{dns}:wood/{part}/{dwood}", f"{dns}:wood/planks/{dwood}_{part}", f"{dns}:{dwood}_{part}",
                         f"{dns}:wood/{dwood}_{part}"):
                if cand in reg:
                    return cand
    token = r"(^|_)" + re.escape(swood) + r"(_|$)"
    if ins != "everycomp" and re.search(token, ipath):
        swapped = re.sub(token, lambda m: m.group(1) + dwood + m.group(2), ipath)
        hit = _everycomp_index().get((dns, swapped))
        if hit:
            return hit
    return item


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
        if o.get("type") == "moonlight:block_type_swap":
            swapped = _wood_swap(o)
            if swapped:
                out.append([("item", swapped)])
                return out
        if isinstance(o.get("item"), str):
            out.append([("item", o["item"])])
            return out
        if isinstance(o.get("tag"), str) and len(o) <= 3:
            out.append([("tag", o["tag"])])
            return out
        # a fluid ingredient is a dict whose "fluid" is the fluid id; a recipe with a "fluid" block (Theurgy digestion
        # and fermentation, IE bottling) still has item ingredients next to it
        if o.get("type") == "neoforge:single" or isinstance(o.get("fluid"), str) or "fluid_stack" in str(o.get("type", "")):
            return out
        if o.get("type") == "theurgy:reformation" and isinstance(o.get("target"), dict):
            # the target sulfur sits on the reformation's target pedestal: not consumed, but needed (a catalyst)
            _slots(o["target"], out, "ingredient")
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
                "ingredient", "input", "base", "template", "addition", "reagent", "key", "sulfur", "salt", "mercury", "solute",
                "basePredicate"):  # IE: a list under basePredicate is one slot with alternatives (paper or bamboo)
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


AU_MATERIALS = os.path.join(REPO, "config", "almostunified", "unification", "materials.json")
AU_PLACEHOLDERS = os.path.join(REPO, "config", "almostunified", "placeholders.json")


def load_au():
    try:
        return json.load(open(AU_MATERIALS, encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def au_target(t, mem, au, fallback_first=True):
    """The member of tag `t` Almost Unified keeps: the priority override of the tag, else the first member of a
    namespace in mod_priorities (config/almostunified/unification/materials.json). Without such a member AU leaves the
    tag alone; `fallback_first` then guesses the first member (recipe outputs), else the result is empty."""
    if not mem:
        return []
    over = au.get("priority_overrides", {})
    for ns in ([over[t]] if t in over else []) + au.get("mod_priorities", []):
        hit = [m for m in mem if m.split(":")[0] == ns]
        if hit:
            return hit[:1]
    return mem[:1] if fallback_first else []


def recipe_records(full, tags, disabled):
    """(recipe id, ingredient slots, output items) of every recipe JSON; `disabled` items never stand for a tag output."""
    # Tag outputs (More Mekanism Processing, IE, Occultism) resolve to the member Almost Unified keeps (au_target).
    au = load_au()

    def tag_target(t):
        return au_target(t, [m for m in tags.get(t, []) if m not in disabled], au)

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
    return [(rid, slots_of(j), outs_with_tags(j)) for rid, j in full.items()]


# ================================================================================================ recipe viewer (E-1)
EMI_FROM_AGE = "age_1"  # firmages-core ServerConfig ui.emiFromAge default: EMI is off before it (ImmersionClient)
AGE_NAMESPACES = os.path.join(REPO, "kubejs", "assets", "firmages", "age_namespaces.json")
CONSTANTS_JS = os.path.join(REPO, "kubejs", "startup_scripts", "00_constants.js")
CORE_MIXINS = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "firmages.mixins.json")
# Workstations of recipe types that station_ages.js leaves to the Age of their mod: one item of each group has to
# exist. A sequenced assembly needs the station of each of its steps.
EMI_STATIONS = {
    "create:pressing": [["create:mechanical_press"]], "create:cutting": [["create:mechanical_saw"]],
    "create:milling": [["create:millstone"]], "create:splashing": [["create:encased_fan"]],
    "create:mixing": [["create:mechanical_mixer"], ["create:basin"]],
    "create:compacting": [["create:mechanical_press"], ["create:basin"]],
    "create:mechanical_crafting": [["create:mechanical_crafter"]],
    "create:sandpaper_polishing": [["create:sand_paper", "create:red_sand_paper"]],
    # a fan blowing through soul fire: soul sand or soul soil (the Beneath) under fire, or a soul campfire
    "create:haunting": [["create:encased_fan"], ["minecraft:soul_sand", "minecraft:soul_soil", "minecraft:soul_campfire"]],
}


def _fluid_ins(o, out):
    """Fluid ids a recipe JSON consumes ({"fluid": id, "amount": n} outside the output keys)."""
    if isinstance(o, dict):
        if isinstance(o.get("fluid"), str) and "amount" in o:
            out.add(o["fluid"])
            return out
        for k, v in o.items():
            if k in RESULT_KEYS or _GATE_OUT_KEY.match(k.lower()):
                continue
            _fluid_ins(v, out)
    elif isinstance(o, list):
        for v in o:
            _fluid_ins(v, out)
    return out


def emi_checks(base, rows, tags, item_age, check):
    """E-1: what EMI shows at each Age from EMI_FROM_AGE on, rebuilt from the all-Ages dump. A recipe is shown when
    (1) the firmages-core gate keeps it: no output of a later Age (G-1), its type's station Age (station_ages.js),
    Create heat and the late pack recipes unlocked (G-2); (2) ProgressiveStages does not hide it: every stage of its
    recipe, output and item locks is owned (Age stages; tool_* helper stages inside their HELPER_WINDOWS; finale_won
    never); (3) the firmages-core EMI predicate keeps it: the mod of its category is unlocked (age_namespaces.json) and
    no input is fully locked (an item or fluid of a later Age or disabled, a tag whose members all are; EMI drops a
    recipe with a fully disabled ingredient). Offenders:
      types   a shown recipe whose station (station_ages.js, EMI_STATIONS, STATION_ITEMS, the steps of a sequenced
              assembly) belongs to a later Age
      inputs  a shown recipe with an input that lists locked items next to unlocked ones as an item list (EMI drops
              disabled members from tags, but an item list keeps them and shows the locked stack). Since firmages-core
              0.9.1 `mixin/emi/ListEmiIngredientMixin` shows only the unlocked members of an item list (SPEC 21.11); while
              firmages.mixins.json lists it, each list is cut here by the rule of `compat/emi/ShownMembers`: the
              unlocked members, or the whole list when none is unlocked (an offender if the recipe is still shown)."""
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        check("E-0 recipes_full.json present (run fa_dump_full)", False, [path])
        return
    full = {k: json.loads(v) for k, v in json.load(open(path, encoding="utf-8")).items()}
    fluid_age = load_fluid_ages()
    ns_age = json.load(open(AGE_NAMESPACES, encoding="utf-8"))["namespaces"]
    station_age = load_station_ages()
    windows = {h: (a_, b_) for h, a_, b_ in re.findall(r"(tool_\w+): \['(age_\d)', (?:'(age_\d)'|null)\]",
                                                       open(CONSTANTS_JS, encoding="utf-8").read())}
    never = len(AGES)

    def ix(st):
        return AGES.index(st) if st in AGES else never

    def item_ix(i):
        st = item_age.get(i)
        return ix(st) if st else ix(ns_age.get(i.split(":")[0], "dawn"))

    def fluid_ix(f):
        st = fluid_age.get(f)
        return ix(st) if st else ix(ns_age.get(f.split(":")[0], "dawn"))

    def heat_ix(t, j):
        if t in HEAT_TYPES and isinstance(j.get("heat_requirement"), str):
            srcs = [i for i in HEAT_STATION.get(j["heat_requirement"], ()) if item_age.get(i) != "disabled"]
            if srcs:
                return min(item_ix(i) for i in srcs)
        return 0

    def station_ix(t, j):
        n = ix(station_age[t]) if t in station_age else 0
        groups = EMI_STATIONS.get(t) or STATION_ITEMS.get(t) or []
        n = max([n, heat_ix(t, j)] + [min(item_ix(i) for i in g) for g in groups])
        if t == "create:sequenced_assembly":
            n = max([n] + [station_ix(s.get("type", ""), s) for s in j.get("sequence", []) if isinstance(s, dict)])
        return n

    def stage_span(s):  # (first, last) Age index in which a lock stage is owned
        s = s[len(PS):] if s.startswith(PS) else s
        if s in AGES:
            return ix(s), never
        if s in windows:
            a_, b_ = windows[s]
            return ix(a_), (ix(b_) - 1 if b_ else never)
        return never, never

    recs = []
    for r in rows:
        rid, j = r["id"], full.get(r["id"]) or {}
        jt = j.get("type", r["type"])
        if r["type"] in GATE_EXEMPT:
            continue
        # (1) the gate: outputs, station types, heat, the late pack recipes
        items, tg, fl = set(), set(), set()
        _gate_outs(j, items, tg, fluids=fl)
        for k in _GATE_IE_KEYS:
            if j.get(k) is not None:
                _gate_outs({"results": j[k]}, items, tg, fluids=fl)
        if r["result"]:
            items.add(r["result"])
        first = max([item_ix(i) if i in item_age else max(item_ix(i), fluid_ix(i)) for i in items]
                    + [fluid_ix(f) for f in fl]
                    + [min(item_ix(m) for m in tags[t]) for t in tg if tags.get(t)] + [0])
        gate = [ix(station_age[t]) for t in {r["type"], jt} if t in station_age]
        gate += [heat_ix(r["type"], j)] + [ix(st) for rx, st in LATE_LOADED if re.match(rx, rid)]
        first = max([first] + gate)
        # (2) ProgressiveStages; a FirmAges.untilAge stand-in ends before its Age
        last = min([never] + [ix(st) - 1 for rx, st in UNTIL_LOADED if re.match(rx, rid)])
        for s in r["locks"]:
            a_, b_ = stage_span(s)
            first, last = max(first, a_), min(last, b_)
        # (3) the EMI predicate: the category's mod, fully locked inputs
        first = max(first, ix(ns_age.get(r["type"].split(":")[0], "dawn")))
        lists = []
        for slot in _slots(j, []):
            alts = [(x, [item_ix(m) for m in tags.get(x, [])]) if k == "tag" else (x, [item_ix(x)])
                    for k, x in slot if k in ("item", "tag")]
            if not alts or any(not m for _, m in alts):
                continue  # chemicals, empty or unknown tags: not judged
            first = max(first, min(min(m) for _, m in alts))  # fully locked until its earliest member
            if len(alts) > 1 and len(alts) == len(slot):
                # every member EMI lists in the slot (tags flattened), each with its Age and whether it is a plain item
                lists.append([(y, m, k == "item") for (k, _), (x, mm) in zip(slot, alts)
                              for y, m in zip(tags[x] if k == "tag" else [x], mm)])
        for f in _fluid_ins(j, set()):
            first = max(first, fluid_ix(f))
        recs.append((rid, r["type"], first, last, station_ix(jt if jt in EMI_STATIONS or jt in STATION_ITEMS
                                                              or jt in station_age else r["type"], j), lists))
    lists_filtered = "emi.ListEmiIngredientMixin" in open(CORE_MIXINS, encoding="utf-8").read()
    early, mixed, shown, trimmed = {}, {}, collections.Counter(), set()
    for n in range(ix(EMI_FROM_AGE), len(AGES)):
        for rid, t, first, last, st, lists in recs:
            if not first <= n <= last:
                continue
            shown[AGES[n]] += 1
            if st > n:
                early.setdefault((t, AGES[st] if st < never else "never"), {}).setdefault(AGES[n], []).append(rid)
            bad = set()
            for lst in lists:
                if lists_filtered:
                    # ShownMembers.of: the unlocked members, or the whole list when none (or all) are unlocked
                    kept = [(x, m) for x, m, _ in lst if m <= n]
                    if kept and len(kept) < len(lst):
                        trimmed.add(rid)
                    bad |= {(x, m) for x, m in (kept or [(x, m) for x, m, _ in lst]) if m > n}
                else:
                    # without the mixin EMI keeps every stack of an item list (it drops disabled tag members only)
                    bad |= {(x, m) for x, m, is_item in lst if is_item and m > n}
            bad = sorted((x, AGES[m] if m < never else "disabled") for x, m in bad)
            if bad:
                mixed.setdefault(rid, [t, AGES[n], AGES[n], bad])[2] = AGES[n]
    print("    E-1 shown recipes per Age: " + ", ".join(f"{k}={shown[k]}" for k in AGES if shown[k]))
    ev = [f"{t} (station {st}) shown at " + ", ".join(f"{a_}: {len(i_)} e.g. {i_[0]}" for a_, i_ in per.items())
          for (t, st), per in sorted(early.items())]
    ev += [f"{rid} [{t}] {a_}..{b_}: lists " + ", ".join(f"{x} ({st})" for x, st in bad[:3])
           for rid, (t, a_, b_, bad) in sorted(mixed.items(), key=lambda kv: (AGES.index(kv[1][1]), kv[0]))]
    print(f"    E-1 offenders: {len(early)} recipe types shown before their station, {len(mixed)} recipes with a locked "
          f"item in an input list" + (f" ({len(trimmed)} recipes with an item list trimmed to the player's Ages by "
                                       f"firmages-core ShownMembers)" if lists_filtered else ""))
    check(f"E-1 recipe viewer leaks per Age ({EMI_FROM_AGE} to age_9): no recipe type shown before its station's Age, "
          f"no shown input that lists a locked item", not ev, ev)


def content_checks(base, server_dir, rows, by_id, tags, item_age, check):
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        check("C-0 recipes_full.json present (run fa_dump_full)", False, [path])
        return
    full = {k: json.loads(v) for k, v in json.load(open(path, encoding="utf-8")).items()}
    items = set(json.load(open(os.path.join(base, "registries.json"), encoding="utf-8"))["item"])
    reg_blocks = set(json.load(open(os.path.join(base, "registries.json"), encoding="utf-8"))["block"])
    fluid_ids = json.load(open(os.path.join(base, "registries.json"), encoding="utf-8")).get("fluid", [])
    disabled = {i for i, st in item_age.items() if st == "disabled"} | set(tags.get("c:hidden_from_recipe_viewers", []))

    def age_ix(i):
        return AGES.index(item_age[i]) if item_age.get(i) in AGES else -1

    def members(slot):
        return {x for k, x in slot if k == "item"} | {i for k, x in slot if k == "tag" for i in tags.get(x, [])}

    # ---- obtainability closure: which items a TFC world can make at all --------------------------------------
    recs = recipe_records(full, tags, disabled)

    def closure(max_age=None):
        """Items obtainable from the world set; with max_age only items of that Age or earlier may enter."""
        def fits(i):
            return max_age is None or age_ix(i) <= AGES.index(max_age)
        have = ({i for i in items if i.split(":")[0] in WORLD_NS and not world_bucket(i)}
                | {"minecraft:" + x for x in VANILLA_WORLD} | set(MOB_DROPS))
        have |= {i for drops in SKY_WORLD.values() for i in drops if i in items}   # Starfall, Drifting Isles
        have = {i for i in have if fits(i)}

        def sat_(slot):
            return any((k in ("item", "chem") and x in have) or (k == "tag" and any(i in have for i in tags.get(x, [])))
                       for k, x in slot)
        grown = dict(GROWN)
        grown["minecraft:bucket"] = GROWN["minecraft:bucket"] + sorted(i for i in items if world_bucket(i))
        grew = True
        while grew:
            grew = False
            for rid, sl, os_ in recs:
                new = {o for o in os_ if o not in have and fits(o)}
                if new and all(sat_(x) for x in sl):
                    have |= new
                    grew = True
            for seed, got in grown.items():
                new = {g for g in got if seed in have and GROWN_NEEDS.get(seed, seed) in have and g not in have and fits(g)}
                if new:
                    have |= new
                    grew = True
            for made, needs in MACHINE_MADE.items():
                if made not in have and fits(made) and all(x in have for x in needs):
                    have.add(made)
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
    # V4A (SPEC 27.8): with the sky road complete the steel ingot is a Starlit Iron ingot, and the tech route deploys one
    sky_road = "firmages:starlit_iron_ingot" in json.dumps(ks)
    want_ings = sorted(["occultism:book_of_binding_bound_djinni", "occultism:spirit_attuned_crystal", "occultism:iesnium_ingot",
                        "ars_nouveau:wilden_tribute", "theurgy:mercury_catalyst",
                        "firmages:starlit_iron_ingot" if sky_road else "tfc:metal/ingot/steel", "ars_nouveau:source_gem_block"])
    ks_row = by_id.get("firmages:ritual/arcane_keystone", {})
    ks_tech = full.get("firmages:sequenced_assembly/arcane_keystone", {})
    tribute = full.get("firmages:apparatus/wilden_tribute", {})
    check("C-3a Arcane Keystone: tech route on the Create line from the age_3 token (Hollow Keystone), magic route in the "
          "Djinni pentacle from the three proofs (Wilden Tribute from the Enchanting Apparatus), TFC steel (Starlit Iron on both "
          "routes with the sky road, V4A) and a source gem block; item age_3",
          ks.get("type") == "occultism:ritual" and ks.get("pentacle_id") == "occultism:craft_djinni" and ks_ings == want_ings
          and item_age.get("firmages:arcane_keystone") == "age_3" and ks_row.get("item_lock") == PS + "age_3"
          and ks_tech.get("type") == "create:sequenced_assembly"
          and (ks_tech.get("ingredient") or {}).get("tag") == "firmages:age_token/age_3"
          and sky_road == ("firmages:starlit_iron_ingot" in json.dumps(ks_tech))
          and tags.get("firmages:age_token/age_3") == ["firmages:hollow_keystone"]
          and tribute.get("type") == "ars_nouveau:enchanting_apparatus",
          [f"type {ks.get('type')}, pentacle {ks.get('pentacle_id')}, ingredients {ks_ings}",
           f"item age {item_age.get('firmages:arcane_keystone')}, PS item lock {ks_row.get('item_lock')}",
           f"tech route {ks_tech.get('type')} from {ks_tech.get('ingredient')}, V4A Starlit Iron on both routes: {sky_road}",
           f"age_token/age_3 {tags.get('firmages:age_token/age_3')}, apparatus tribute {tribute.get('type')}"])
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
    spag = sorted(r for r in full if r.startswith("firmages:digestion/"))
    old_spag = sorted(r for r in full if r.startswith("firmages:liquefaction/"))
    gone = [i for i in ("occultism:ritual/summon_foliot_transport_items", "ars_additions:apparatus/warp_index",
                        "ars_nouveau:scry_ritual/diamond_ores") if i in full]
    check("C-3e one magic carrier per function: no Occultism crusher ore recipes, Theurgy spagyrics in the Digestion Vat "
          "on every grade of 15 TFC ores (60 recipes, 2.0x, harmony H3A), no ore liquefaction, no Foliot transporter, no "
          "Warp Index, no diamond scrying",
          not ore_crush and not theurgy_std and len(spag) == 60 and not old_spag and not gone,
          [f"occultism ore crushing: {ore_crush[:5]}", f"theurgy standard ore liquefaction: {theurgy_std[:5]}",
           f"firmages digestion recipes: {len(spag)}", f"old liquefaction recipes: {old_spag[:3]}", f"still present: {gone}"])
    miner_bad = [f"{rid} -> {o}" for rid, j in full.items() if j.get("type") == "occultism:miner"
                 for o in _outs(j, []) if age_ix(o) > AGES.index("age_3")
                 and not rid.startswith(("occultism:miner/deeps", "occultism:miner/master", "occultism:miner/eldritch"))]
    # the ancient eldritch miner (miner/eldritch) has no source in the pack (dev/derive_ages.py: never reachable)
    check("C-3f miners of the Arcane Age give nothing of a later Age", not miner_bad, miner_bad)

    # ---- C-4: Industrial Age -----------------------------------------------------------------------------------
    pc = full.get("firmages:crafting/pressure_core", {})
    pc_ings = sorted(x for s in _slots(pc, []) for _, x in s)
    pc_want = sorted(["immersiveengineering:heavy_engineering", "c:double_sheets/black_steel", "firmages:arcane_gearbox",
                      "firmages:age_token/age_4"] + ["firmages:cloudsteel_ingot"] * ("firmages:cloudsteel_ingot" in pc_ings))  # V4A
    pc_row = by_id.get("firmages:crafting/pressure_core", {})
    check("C-4a Pressure Core: black steel double sheet, Heavy Engineering Block, Arcane Gearbox, built-up steel cannon "
          "chamber (age_4 token), with the sky road two Cloudsteel ingots (V4A); age_4",
          pc_ings == pc_want and item_age.get("firmages:pressure_core") == "age_4"
          and pc_row.get("item_lock") == PS + "age_4" and item_age.get("firmages:arcane_gearbox") == "age_4"
          and tags.get("firmages:age_token/age_4") == ["createbigcannons:built_up_steel_cannon_chamber"]
          and "firmages:arcane_gearbox" in json.dumps(full.get("firmages:crafting/blastbrick_reinforced", {})),
          [f"ingredients {pc_ings}", f"item age {item_age.get('firmages:pressure_core')}, lock {pc_row.get('item_lock')}",
           f"age_token/age_4 {tags.get('firmages:age_token/age_4')}"])
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
    pieces = {i for i in items if re.match(r"^(tfc|tfc_ie_addon):ore/(small|poor|normal|rich)_", i)
              or re.match(r"^firmages:ore/(small|poor|normal|rich)_native_osmium$", i)}
    # Allowed consumers: TFC hand stations, WoodenCog heated melting (Schmelzen, 1-2), Create crushing/milling (2),
    # Theurgy (3), the IE Crusher (4), grid recipes that use a piece as a part. Stations of later Ages are not checked;
    # the firmages-core Ore Processor (9) is a pack namespace, so it is named here.
    allowed = re.compile(r"^(tfc:|woodencog:heated_mixing$|create:(crushing|milling)$|theurgy:|immersiveengineering:crusher$"
                         r"|minecraft:crafting|kubejs:shape|firmages:ore_processing$)")
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
    for k in ("c:tools", "c:armors", "minecraft:enchantable/armor", "minecraft:enchantable/bow",
              "minecraft:enchantable/crossbow"):
        gear |= set(tags.get(k, []))
    # enchantable/mining and /weapon stay out too (Occultism miners, crushing wheels, annihilation planes). Draconic
    # Evolution armour and Evolved Mekanism shields carry none of these tags (DE chestpieces only curios:body).
    late_gear = re.compile(r"^(draconicevolution:(wyvern|draconic|chaotic)_(chestpiece|bow|staff|sword|axe|pickaxe|shovel|hoe)"
                           r"|evolvedmekanism:shield_)")
    gear |= {o for _, _, os_ in recs for o in os_ if late_gear.match(o)}
    gear |= {"minecraft:elytra", "mekanism:hdpe_elytra"}  # wings carry no gear tag either

    def gear_chains(age):
        """Recipes that make gear of `age` from a finished tool or armour piece. A slot that holds the output
        only (Ars dyeing, repair-style recipes) recolours the same item and is not an upgrade."""
        chains = []
        for rid, sl, os_ in recs:
            outs = [o for o in os_ if o in gear and item_age.get(o) == age]
            if not outs:
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
    late_ns = ("mekanism", "moremekanismprocessing", "mekmm", "evolvedmekanism")
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
    # draconium is no planet metal any more (plan fix 9): its End ore drops pack pieces, MMP's ore recipes are removed
    planet = re.compile(r'"c:(ores|raw_materials)/(desh|ostrum|calorite)"|"c:storage_blocks/raw_(desh|ostrum|calorite)"')
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
            "mekmm:lathe", "mekmm:rolling_mill", "mekmm:presser", "mekmm:pressing", "mekmm:planting", "mekmm:recycler",
            "evolvedmekanism:melting", "evolvedmekanism:solidifying"}
    present = sorted({j.get("type") for j in full.values()} & gone)
    ingot_crush = sorted(r for r, j in full.items() if j.get("type") in ("mekanism:crushing", "moremekanismprocessing:tag_crushing")
                         and '"c:ingots/' in json.dumps(j.get("input", {})))
    steel_dust = [r for r in ("mekanism:processing/steel/enriched_iron_to_dust", "mekanism:processing/bronze/ingot/from_infusing") if r in full]
    check("C-6b Mekanism: no Precision Sawmill, planting, recycling, molten-metal, stamping/lathe/rolling/presser recipes; "
          "no ingot -> dust crushing; no Mekanism steel or bronze route", not present and not ingot_crush and not steel_dust,
          ["types %s" % present, "ingot crushing %s" % ingot_crush[:8], "steel/bronze %s" % steel_dust])
    # C-6e: the rest of the fixed ORE_LADDER_REMOVALS list (recipes/age_6/ore_ladder.js): no Mekanism-family combiner
    # recipe makes an ore block, no Mekanism crusher crushes stone, gravel or sand, no More Machine silver chain
    mek_ns = ("mekanism:", "moremekanismprocessing:", "mekmm:", "evolvedmekanism:", "extendedae:", "advanced_ae:")
    comb_ore = sorted(r for r, j in full.items() if r.startswith(mek_ns) and j.get("type") == "mekanism:combining"
                      and re.search(r"(_ore|ancient_debris)$", str(j.get("output", {}).get("id", ""))))
    stone_crush = sorted(r for r, j in full.items() if r.startswith(mek_ns)
                         and j.get("type") in ("mekanism:crushing", "moremekanismprocessing:tag_crushing")
                         and re.search(r'"minecraft:(cobblestone|gravel|sand|red_sand|.*_tiles?|.*bricks?.*|.*stone.*|deepslate.*|tuff.*)"',
                                       json.dumps(j.get("output", {}))))
    mm_silver = sorted(r for r in full if r.startswith("mekmm:processing/silver/"))
    check("C-6e Mekanism ore ladder removals: no combiner ore blocks, no stone/gravel/sand crushing, no More Machine silver chain",
          not comb_ore and not stone_crush and not mm_silver,
          ["combiner %s" % comb_ore[:8], "stone crushing %s" % stone_crush[:8], "silver %s" % mm_silver[:8]])
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
    # C-1a / C-3s: the fixed recipe-id lists that replaced reload-time scans (dev/poc-results.md, "Content fixes after
    # the late Ages"). C-1a: no Create, Create Connected or TFCreate recipe of an item designed for Dawn to the Bronze
    # Age asks for iron in any form (recipes/age_1/bronze_age.js andesiteTier and the TFCreate copies); C-3s: no recipe
    # asks for Occultism silver (recipes/global_removals.js OCCULT_SILVER). A FAIL names a recipe id to add.
    # The intended Age is the age_map Age (column 0 of dev/data/derived_ages.json where present, else the tag): the
    # derived Age follows the iron and would hide the problem (Modpack-Planung/research-raw/fb3-create-tier-sweep.md).
    der_path = os.path.join(REPO, "dev", "data", "derived_ages.json")
    der = json.load(open(der_path, encoding="utf-8"))["items"] if os.path.isfile(der_path) else {}

    def map_age(i):
        return der[i][0] if i in der else item_age.get(i)

    def main_out(j):
        r = j.get("result") or (j.get("results") or [None])[0]
        return (r.get("id") or r.get("item")) if isinstance(r, dict) else r if isinstance(r, str) else None
    out_keys = ("result", "results", "output", "outputs", "secondaries")

    def inputs_text(j):
        return json.dumps({k: v for k, v in j.items() if k not in out_keys})
    iron_in = re.compile(r'"(c:(plates|ingots|storage_blocks|nuggets|rods)/iron|minecraft:iron_(bars|trapdoor|nugget|ingot|block)'
                         r'|minecraft:compass|tfc:metal/[a-z_]+/(wrought_iron|cast_iron|steel)|c:[a-z_]+/(wrought_iron|cast_iron|steel))"')
    # Iron by design although the mod is Bronze Age: the fluid stage, electron-tube parts, Industrial logistics (sweep 2.3)
    accept_iron = {"create:item_drain", "create_connected:overstress_clutch", "create_connected:kinetic_battery",
                   "create_connected:empty_fan_catalyst", "create:redstone_requester"}
    iron_tier = sorted(f"{r} -> {main_out(j)}" for r, j in full.items()
                       if r.startswith(("create:", "create_connected:", "tfcreate:")) and main_out(j)
                       and map_age(main_out(j)) in ("dawn", "age_0", "age_1") and main_out(j) not in accept_iron
                       and iron_in.search(inputs_text(j)))
    check("C-1a Create andesite tier: no Create/Connected/TFCreate recipe of a Dawn to Bronze Age item asks for iron in any form",
          not iron_tier, iron_tier)
    occ_silver = sorted(r for r, j in full.items() if re.search(r'"occultism:silver_(ingot|nugget)"', inputs_text(j)))
    check("C-3s no recipe asks for Occultism silver ingots or nuggets (global_removals.js OCCULT_SILVER)", not occ_silver, occ_silver)
    # C-Di: items no recipe makes (the disabled Create sheets, vanilla copper and gold ingots, iron block, lapis) must not
    # be named as an ingredient by item id (recipes/dead_inputs.js DEAD, byproducts.js). Recipes that only make disabled
    # items are dead anyway and left out.
    src_dead = open(os.path.join(REPO, "kubejs", "server_scripts", "recipes", "dead_inputs.js"), encoding="utf-8").read()
    dead_block = src_dead[src_dead.index("// BEGIN DEAD"):src_dead.index("// END DEAD")]
    dead = set(re.findall(r"'([a-z0-9_]+:[a-z0-9_/]+)':", dead_block))
    outs_of = {r: os_ for r, _, os_ in recs}
    dead_rx = re.compile(r'"item": "(%s)"' % "|".join(re.escape(d) for d in sorted(dead)))
    dead_in = sorted(f"{r}: {sorted(set(dead_rx.findall(inputs_text(j))))}" for r, j in full.items()
                     if dead_rx.search(inputs_text(j))
                     and not (outs_of.get(r) and all(item_age.get(o) == "disabled" for o in outs_of[r])))
    check(f"C-Di no recipe with a live output names one of the {len(dead)} dead items of dead_inputs.js as an ingredient",
          dead and not dead_in, dead_in)
    # C-3m: the Arcane MAP (recipes/age_3/arcane_tfc_inputs.js) runs on a fixed id list; no recipe of the magic mods
    # may still name one of its vanilla items as an ingredient.
    src3 = open(os.path.join(REPO, "kubejs", "server_scripts", "recipes", "age_3", "arcane_tfc_inputs.js"), encoding="utf-8").read()
    map_start = src3.index("const MAP = {")
    map_block = src3[map_start:src3.index(chr(10) + "  }" + chr(10), map_start)]
    map_keys = set(re.findall(r"^\s*'([a-z0-9_]+:[a-z0-9_/]+)':", map_block, re.M))
    magic_left = sorted(f"{r}: {sorted(set(m) & map_keys)}" for r, m in
                        ((r, re.findall(r'"item": "([a-z0-9_]+:[a-z0-9_/]+)"', inputs_text(j))) for r, j in full.items()
                         if r.startswith(MAGIC_NS)) if set(m) & map_keys)
    check(f"C-3m no magic-mod recipe names one of the {len(map_keys)} vanilla items of the Arcane MAP (REWRITE_IDS)",
          map_keys and not magic_left, magic_left)
    # C-Gv: no recipe consumes a vanilla tool or armour piece that no recipe of the pack makes (a TFC world has no
    # vanilla wooden, stone, iron or diamond tools); Apotheosis salvaging takes mob drops by design (decision log).
    vanilla_gear = re.compile(r'"(minecraft:(?:wooden|stone|iron|golden|diamond|netherite)_(?:sword|pickaxe|axe|shovel|hoe)'
                              r'|minecraft:(?:leather|chainmail|iron|golden|diamond|netherite)_(?:helmet|chestplate|leggings|boots)'
                              r'|minecraft:(?:bow|crossbow|shield|trident|fishing_rod|shears|flint_and_steel|elytra|mace|brush))"')
    made_items = {o for _, _, os_ in recs for o in os_}
    dead_gear = sorted(f"{r}: {sorted(set(m) - made_items)}" for r, m in
                       ((r, vanilla_gear.findall(inputs_text(j))) for r, j in full.items()
                        if not r.startswith(("minecraft:", "apotheosis:salvaging/")))
                       if set(m) - made_items)
    check("C-Gv no recipe needs a vanilla tool or armour piece that no recipe makes", not dead_gear, dead_gear)
    # C-G: the gear rule for the Ages without their own C- gear check (age_3: C-3g, age_4: C-4j). Exceptions (Doc 08 section 8): MekaSuit and Meka-Tool (trophies after
    # finale_won), MekaSuit and Draconic modules, backpack upgrades.
    exempt = re.compile(r"^mekanism:(mekasuit_|meka_tool)|module|^sophisticatedbackpacks:")
    for st in ("age_0", "age_1", "age_2", "age_5", "age_6", "age_7", "age_8", "age_9"):
        ch = [c for c in gear_chains(st) if not exempt.search(c.split(" eats ")[0])]
        check("C-G %s gear rule: no tool or armour of %s is made from another tool or armour (exceptions: MekaSuit, "
              "Meka-Tool, modules, backpack upgrades)" % (st, st), not ch, ch)

    # ---- C-M: the pack's osmium vein (replaces MekaTFC, 2026-10-13; startup_scripts/osmium.js, recipes/age_6/osmium.js) --
    osm_pieces = [f"firmages:ore/{g}_native_osmium" for g in ("small", "poor", "normal", "rich")]
    osm_blocks = [i for i in items if re.match(r"^firmages:ore/(poor|normal|rich)_native_osmium/[a-z]+$", i)]
    gone_ns = sorted({i for i in items if i.startswith(("mekatfc:", "tfcoretooltips:", "tfc_ore_tooltips:"))})
    ladder = sorted(r for r in full if re.match(r"^firmages:(purifying|injecting|dissolution)/native_osmium_", r))
    ladder_in = sorted({json.dumps(full[r].get("item_input", {}).get("item")) for r in ladder})
    # every recipe that eats an osmium piece: the 12 ladder recipes, TFC heating, the Theurgy digestion, WoodenCog
    # heated melting (generated from TFC heating), the Ore Processor (age_9) and nothing else (no furnace, enrichment
    # or crusher 2x)
    eaters = collections.defaultdict(set)
    for r, sl, os_ in recs:
        if any(members(x) & set(osm_pieces) for x in sl):
            eaters[full[r].get("type", "")].add(r)
    ok_types = {"mekanism:purifying", "mekanism:injecting", "mekanism:dissolution", "tfc:heating",
                "theurgy:digestion", "woodencog:heated_mixing", "firmages:ore_processing"}
    bad_eaters = sorted(f"{t}: {sorted(rs)[:3]}" for t, rs in eaters.items() if t not in ok_types)
    heat = sorted(r for r in full if full[r].get("type") == "tfc:heating"
                  and full[r].get("result_fluid", {}).get("id") == "evolvedmekanism:molten_osmium")
    cast = sorted(r for r in full if full[r].get("type") == "tfc:casting"
                  and full[r].get("result", {}).get("id") == "mekanism:ingot_osmium")
    # the TFC weld of the steel casing and its osmium double ingot went with harmony link 9 (recipes/age_6/links.js)
    weld = sorted(r for r in ("firmages:welding/osmium_double_ingot", "firmages:welding/steel_casing") if r in full)
    raw_tag = [i for i in tags.get("c:raw_materials/osmium", []) + tags.get("c:ores/osmium", []) if i.startswith("firmages:")]
    ages = {i: item_age.get(i) for i in osm_pieces + osm_blocks + ["firmages:metal/double_ingot/osmium"]}
    bad_age = [f"{i}: {a_}" for i, a_ in ages.items()
               if a_ != ("disabled" if i == "firmages:metal/double_ingot/osmium" else "age_5")]
    check("C-M osmium vein: no MekaTFC or TFC Ore Tooltips id left; 30 ore blocks; 12 ladder recipes on the pack "
          "pieces; pieces eaten only by the ladder, TFC heating and the spagyric; 6 heating recipes to molten osmium, "
          "2 casting recipes to the Mekanism ingot, no double ingot or steel casing weld (link 9); pieces and blocks out "
          "of c:raw_materials/osmium and c:ores/osmium; pieces and blocks age_5, double ingot hidden",
          not gone_ns and len(osm_blocks) == 30 and len(ladder) == 12 and len(ladder_in) == 4
          and all("firmages:ore/" in x for x in ladder_in) and not bad_eaters and len(heat) == 6 and len(cast) == 2
          and not weld and not raw_tag and not bad_age,
          [f"leftover ids {gone_ns[:5]}", f"ore blocks {len(osm_blocks)}", f"ladder {len(ladder)} on {ladder_in}",
           f"other consumers {bad_eaters}", f"heating {len(heat)}", f"casting {cast}", f"welding {weld}",
           f"raw/ore tag {raw_tag}", f"wrong Age {bad_age[:5]}"])

    # ---- C-MA: the Mystical Agriculture veins (decision 8A, 2026-10-13; startup_scripts/mystical_veins.js) -------
    # Three TFC veins replace MA's own ore generation (off in config/mysticalagriculture-common.toml): each block they
    # place is registered, sits in its host rock, is in age_blocks/age_6 and nowhere else, and has an age_6 ore
    # override that shows (and drops) exactly its host rock; the blocks' items and MA's mined products are age_6.
    kdata = os.path.join(REPO, "kubejs", "data")
    ma_veins = {"inferium": "mysticalagriculture:inferium_essence", "prosperity": "mysticalagriculture:prosperity_shard",
                "soulstone": None}
    vein_tag = json.load(open(os.path.join(kdata, "tfc", "tags", "worldgen", "placed_feature", "in_biome", "veins.json"),
                              encoding="utf-8"))["values"]
    stage_text = open(os.path.join(REPO, "config", "progressivestages", "stages", "age_6.toml"), encoding="utf-8").read()
    ovr = {m.group(1): (m.group(2), m.group(3)) for m in re.finditer(
        r'target = "id:([^"]+)"\ndisplay_as = "id:([^"]+)"\ndrop_as = "id:([^"]+)"', stage_text)}
    block_age = {}
    for p in sorted(os.listdir(os.path.join(kdata, "firmages", "tags", "block", "age_blocks"))):
        for v in json.load(open(os.path.join(kdata, "firmages", "tags", "block", "age_blocks", p), encoding="utf-8"))["values"]:
            block_age.setdefault(v["id"] if isinstance(v, dict) else v, []).append(p[:-5])
    ma_bad, placed = [], set()
    for vein, product in ma_veins.items():
        cf = os.path.join(kdata, "firmages", "worldgen", "configured_feature", "vein", vein + ".json")
        pf = os.path.join(kdata, "firmages", "worldgen", "placed_feature", "vein", vein + ".json")
        if not (os.path.isfile(cf) and os.path.isfile(pf)) or f"firmages:vein/{vein}" not in vein_tag:
            ma_bad.append(f"{vein}: configured/placed feature or the tfc:in_biome/veins entry missing")
            continue
        for b in json.load(open(cf, encoding="utf-8"))["config"]["blocks"]:
            rocks = [r.rsplit("/", 1)[1] for r in b["replace"] if r.startswith("tfc:rock/raw/")]
            if len(rocks) != 1 or len(b["replace"]) != 1:
                ma_bad.append(f"{vein}: replace {b['replace']} is not one TFC raw rock")
                continue
            for w in b["with"]:
                blk = w["block"]
                placed.add(blk)
                if blk not in reg_blocks:
                    ma_bad.append(f"{vein}: {blk} is not a registered block")
                if blk.startswith("firmages:") and not blk.endswith("/" + rocks[0]):
                    ma_bad.append(f"{vein}: {blk} placed in {rocks[0]}")
                if block_age.get(blk) != ["age_6"]:
                    ma_bad.append(f"{vein}: {blk} in age_blocks {block_age.get(blk)}, want only age_6")
                if ovr.get(blk) != (f"tfc:rock/raw/{rocks[0]}", f"tfc:rock/loose/{rocks[0]}"):
                    ma_bad.append(f"{vein}: {blk} age_6 override {ovr.get(blk)}, want its host rock {rocks[0]}")
                if blk in items and item_age.get(blk) != "age_6":
                    ma_bad.append(f"{vein}: item {blk} is {item_age.get(blk)}")
                lt = os.path.join(kdata, "firmages", "loot_table", "blocks", *blk.split(":", 1)[1].split("/")) + ".json"
                if product and (not os.path.isfile(lt) or product not in open(lt, encoding="utf-8").read()):
                    ma_bad.append(f"{vein}: {blk} has no loot table dropping {product}")
    for i in ("mysticalagriculture:inferium_essence", "mysticalagriculture:prosperity_shard",
              "mysticalagriculture:soulium_dust", "mysticalagriculture:soulstone", "mysticalagriculture:soulstone_cobble"):
        if item_age.get(i) != "age_6":
            ma_bad.append(f"{i} is {item_age.get(i)}, want age_6")
    ma_cfg = open(os.path.join(REPO, "config", "mysticalagriculture-common.toml"), encoding="utf-8").read()
    ma_gen_on = [k for k in ("generateProsperityOre", "generateInferiumOre", "generateSoulstone")
                 if not re.search(r"\b%s\s*=\s*false" % k, ma_cfg)]
    check("C-MA Mystical Agriculture veins: inferium (21 rocks), prosperity (13) and soulstone (basalt) as TFC veins; "
          "every placed block registered, in its host rock, only in age_blocks/age_6, disguised as that rock until "
          "age_6; block items, essence, shards, soulium dust and soulstone age_6; MA's own ore generation off",
          not ma_bad and not ma_gen_on and len([b for b in placed if b.startswith("firmages:ore/inferium/")]) == 21
          and len([b for b in placed if b.startswith("firmages:ore/prosperity/")]) == 13,
          ma_bad + [f"MA worldgen on: {ma_gen_on}"] * bool(ma_gen_on) + [f"placed blocks {len(placed)}"])

    ladder_checks(full, recs, items, tags, check)
    cycle_checks(full, tags, fluid_ids, {i for i, st in item_age.items() if st == "disabled"}, check)
    reach_checks(full, recs, items, tags, item_age, check, server_dir)
    sky_checks(full, recs, items, tags, check)
    link_checks(full, check)


# ================================================================================================ sky road (S-)
# S- checks (firmages-core SPEC 24.3 and 24.7 criterion 1, the "validate_recipes" hand-off of M15): every sky material
# has exactly one source. A recipe may turn a sky metal into another form of the same metal (meteoric iron chunk to
# ingot, sheet, rod, block), but nothing else makes a sky material; the celestial alloys, the later lenses and the
# blessing shards come only from the Lens Crown (firmages:starforge); the first three lenses are grid recipes from a
# lens blank (SPEC 27.9). No sky item is an ore or raw material (no ore doubling), and no loot table outside the
# firmages blocks and no global loot modifier hands one out (dev/data/world_sources.json of derive_ages.py --scan).
SKY_FAMILIES = {"meteoric_iron": r"^firmages:meteoric_iron_(chunk|ingot|double_ingot|sheet|rod|block)$",
                "starsteel": r"^firmages:starsteel_(chunk|ingot|block)$"}
SKY_SINGLE = ["firmages:storm_glass", "firmages:aurora_light_bucket", "firmages:firmament_crystal", "firmages:voidglass",
              "firmages:deep_space_sample"]
CROWN_ONLY = [f"firmages:{a}_ingot" for a in ("starlit_iron", "cloudsteel", "aurora_steel", "halo_steel", "void_steel",
                                              "quasar_alloy", "firmament_alloy")] \
    + [f"firmages:lens/{n}" for n in ("arcane", "industrial", "electric", "information", "space", "quantum")] \
    + ["firmages:blessing_shard"]
GRID_LENSES = {f"firmages:crafting/lens/{n}" for n in ("stone", "bronze", "iron")}


# ================================================================================================ cross-mod links (K-)
# Harmony plan section 4, package A9 (kubejs/server_scripts/recipes/age_N/links.js, dev/quest-hints-links.md): each
# link recipe holds its part of the earlier Age, and the route it replaced is gone. (recipe id, [ids or tags its JSON
# must name]); "-" in front of a recipe id: that recipe must be absent.
LINK_RECIPES = [
    ("age_2", 2, "firmages:heated_pressing/rod_steel", ["c:ingots/steel", "tfc:metal/rod/steel"]),
    ("age_2", 2, "-create:pressing/steel_ingot", []),
    ("age_2", 6, "firmages:crafting/belt_connector_rubber", ["afc:rubber_bar"]),
    ("age_3", 3, "occultism:crafting/book_of_binding_foliot", ["create:precision_mechanism"]),
    ("age_3", 3, "occultism:crafting/book_of_binding_djinni", ["create:precision_mechanism"]),
    ("age_3", 3, "-occultengineering:mixing/book_of_binding_foliot", []),
    ("age_4", 6, "immersiveengineering:crafting/wirecoil_copper_ins", ["afc:rubber_bar"]),
    ("age_4", 6, "immersiveengineering:crafting/conveyor_basic", ["afc:rubber_bar"]),
    ("age_5", 7, "immersiveengineering:crafting/generator", ["createaddition:alternator"]),
    ("age_5", 8, "firmages:mineral/native_osmium", ["firmages:ore/normal_native_osmium"]),
    ("age_5", 25, "createdieselgenerators:crafting/huge_diesel_engine", ["firmages:attuned_circuit"]),
    ("age_6", 9, "mekanism:steel_casing", ["immersiveengineering:heavy_engineering"]),
    ("age_6", 9, "-firmages:welding/steel_casing", []),
    ("age_6", 10, "firmages:crafting/basic_control_circuit", ["immersiveengineering:circuit_board"]),
    ("age_6", 10, "-mekanism:control_circuit/basic", []),
    ("age_6", 11, "immersivepetroleum:hydrotreater/naphtha_cracking", ["c:naphtha", "mekanism:ethene"]),
    ("age_6", 14, "ae2:network/blocks/controller", ["mekanism:steel_casing"]),
    ("age_6", 14, "ae2:network/blocks/storage_drive", ["mekanism:advanced_control_circuit"]),
    ("age_6", 25, "mekanism:control_circuit/advanced", ["firmages:attuned_circuit"]),
    ("age_6", 25, "-mekanism:control_circuit/infused_advanced", []),
    ("age_7", 15, "firmages:mechanical_crafting/digital_miner", ["mekanism:digital_miner"]),
    ("age_7", 15, "-mekanism:digital_miner", []),
    ("age_7", 18, "mekanismgenerators:fission_reactor/port", ["mekmm:advanced_electrolysis_core"]),
    ("age_7", 18, "mekanismgenerators:turbine/casing", ["immersiveengineering:sheetmetal_steel"]),
    ("age_7", 19, "draconicevolution:machines/wyvern_crafting_injector", ["ae2:engineering_processor"]),
    ("age_7", 25, "draconicevolution:components/wyvern_core", ["mekanism:advanced_control_circuit"]),
    ("age_8", 15, "firmages:mechanical_crafting/fusion_reactor_frame", ["mekanismgenerators:fusion_reactor_frame"]),
    ("age_8", 15, "-mekanismgenerators:reactor/frame", []),
    ("age_8", 20, "draconicevolution:components/awakened_core", ["ae2:cell_component_256k"]),
    ("age_8", 20, "mekanismgenerators:reactor/controller", ["mekanismgenerators:fission_reactor_port"]),
]


def link_checks(full, check):
    bad = []
    for st, n, rid, parts in LINK_RECIPES:
        if rid.startswith("-"):
            if rid[1:] in full:
                bad.append(f"link {n} ({st}): {rid[1:]} should be gone")
            continue
        j = full.get(rid)
        if j is None:
            bad.append(f"link {n} ({st}): {rid} missing")
            continue
        text = json.dumps(j)
        miss = [p_ for p_ in parts if f'"{p_}"' not in text]
        if miss:
            bad.append(f"link {n} ({st}): {rid} lacks {miss}")
    check(f"K-1 cross-mod links: {sum(1 for r in LINK_RECIPES if not r[2].startswith('-'))} link recipes hold their part "
          f"of the earlier Age, {sum(1 for r in LINK_RECIPES if r[2].startswith('-'))} replaced routes are gone", not bad, bad)


def sky_checks(full, recs, items, tags, check):
    fam = {f: re.compile(rx) for f, rx in SKY_FAMILIES.items()}

    def family(i):
        return next((f for f, rx in fam.items() if rx.match(i)), None)

    def slot_items(sl):
        out = set()
        for alts in sl:
            for k, x in alts:
                out |= set(tags.get(x, [])) if k == "tag" else {x}
        return out
    sky = {i for i in items if family(i)} | set(SKY_SINGLE) | set(CROWN_ONLY)
    bad = []
    for rid, sl, os_ in recs:
        if full[rid].get("type") in _WORLD_TYPES:
            continue   # world data, not processing: the IE veins under Starfall craters (SPEC 25.6) are part of the source
        for o in os_:
            if o not in sky:
                continue
            t = full[rid].get("type", "")
            if o in CROWN_ONLY:
                if t != "firmages:starforge":
                    bad.append(f"{rid} ({t}) makes {o} outside the Lens Crown")
            elif family(o):
                if not any(family(i) == family(o) for i in slot_items(sl)):
                    bad.append(f"{rid} ({t}) makes {o} without {family(o)}")
            else:
                bad.append(f"{rid} ({t}) makes {o}, whose one source is not a recipe")
    lens_bad = [f"{rid} makes {o}" for rid, sl, os_ in recs for o in os_
                if o.startswith("firmages:lens/") and o.split("/")[-1] in ("stone", "bronze", "iron")
                and rid not in GRID_LENSES and full[rid].get("type") != "firmages:starforge"]
    check(f"S-1 one source: no recipe makes a sky material ({len(sky)} items) outside its source; alloys, later lenses "
          "and shards only in the Lens Crown", not bad and not lens_bad, bad + lens_bad)
    ore = sorted(i for t in tags if t.startswith(("c:ores", "c:raw_materials")) for i in tags[t] if i in sky)
    check("S-2 no sky material is in #c:ores or #c:raw_materials (no ore doubling)", not ore, ore)
    try:
        ws = json.load(open(os.path.join(REPO, "dev", "data", "world_sources.json"), encoding="utf-8"))
    except (OSError, ValueError):
        ws = {}
    loot = sorted(f"{t}: {i}" for t, d in ws.get("loot", {}).items() for i in d.get("items", [])
                  if i in sky and not t.startswith("firmages:blocks/"))
    glm = sorted(f"{g['id']}: {s}" for g in ws.get("glm", []) for s in g.get("strings", []) if s in sky)
    check("S-3 no loot table outside the firmages blocks and no global loot modifier hands out a sky material",
          not loot and not glm, loot + glm)
    starforge = [rid for rid, _, _ in recs if full[rid].get("type") == "firmages:starforge"]
    per_alloy = {a: [rid for rid, _, os_ in recs if a in os_] for a in CROWN_ONLY[:7]}
    multi = [f"{a}: {r}" for a, r in per_alloy.items() if len(r) > 1]
    print(f"    S-4 starforge recipes in the dump: {len(starforge)}; alloys with a recipe: "
          f"{sum(1 for r in per_alloy.values() if r)} of 7")
    check("S-4 every celestial alloy has at most one recipe (the Lens Crown's; SPEC 27.7)", not multi, multi)


# ================================================================================================ ore ladder (L-)
# L- checks (harmony plan 2026-10-15, packages A3 and A4): kubejs/data/firmages/balance/metals.json is the one metal table
# (the recipe scripts read the same file through startup_scripts/metal_table.js) (ore pieces per metal,
# the Age a metal is first processed, the target factor of every ore station). The checks measure every station on
# every ore piece of the recipe dump and compare: L-1 the table (coverage, factor within the tolerance, station Age
# as in station_ages.js), L-2 the tech ladder never steps back, L-3 magic never beats the next tech step, L-4 no
# unlisted recipe type multiplies ore, L-5 no metal is reachable from the world without ore.
METALS_JSON = os.path.join(REPO, "kubejs", "data", "firmages", "balance", "metals.json")
_LADDER_TRACE = {}   # the last L-5 closure (made_by, tainted, have), for debugging an entrance
_METAL_FORMS = ("ingot|nugget|dust|dirty_dust|clump|shard|crystal|sheet|double_ingot|double_sheet|rod|plate|block|"
                "storage_block|raw|alchemical_sulfur")
_YIELD_KEYS = {"result", "results", "output", "outputs", "secondaries", "result_fluid", "output_fluid", "output_item",
               "result_item", "chemical_output"}
# world data, not processing: their ore outputs are ore, never "made from nothing"
_WORLD_TYPES = {"immersiveengineering:mineral_mix", "occultism:miner", "tfc:collapse", "tfc:landslide"}


# ================================================================================================ metal cycles (L-8)
# L-8 (round F1): no recipe cycle over the forms of one metal returns more than went in. Nodes are the members of the
# c:<form>/<metal> tags (ingots, nuggets, blocks, sheets, plates, double ingots and sheets, rods, dusts, gears, wires)
# and the molten metals (tfc:metal/<m>, *:molten_<m>, in mB); an edge is a recipe whose metal inputs are one kind
# (n of one item or tag, or one fluid amount) and whose output is a form of the same metal (expected count x chance),
# weighted out / in. Other inputs (planks, flux, a hammer, a mold) do not count. A cycle whose weights multiply to more
# than 1 creates metal (the 0.14.0 loop: 4 TFC sheets -> 8 TFC blocks -> 72 ingots through c:storage_blocks/<m>).
CYCLE_FORMS = ("ingots", "nuggets", "storage_blocks", "sheets", "plates", "double_ingots", "double_sheets", "rods",
               "dusts", "gears", "wires")
CYCLE_EPS = 1e-6


def _cycle_alts(o):
    """The alternatives of one ingredient JSON as ('item'|'tag', id) pairs, or None."""
    if isinstance(o, str) and ":" in o:
        return [("tag", o[1:]) if o.startswith("#") else ("item", o)]
    if isinstance(o, dict):
        if isinstance(o.get("basePredicate"), (dict, list)):
            return _cycle_alts(o["basePredicate"])
        if isinstance(o.get("item"), str):
            return [("item", o["item"])]
        if isinstance(o.get("tag"), str) and "amount" not in o:
            return [("tag", o["tag"])]
        for k in ("ingredient", "children", "values"):
            if isinstance(o.get(k), list):
                return _cycle_alts(o[k])
    if isinstance(o, list) and o and all(isinstance(v, (dict, str)) for v in o):
        alts = [a for v in o for a in (_cycle_alts(v) or [])]
        return alts or None
    return None


def _cycle_inputs(j):
    """(item slots [(alternatives, count)], fluid inputs [(('fluid'|'ftag', id), mB)]) of a recipe JSON."""
    items, fluids = [], []

    def walk(o, key=None):
        if isinstance(o, dict):
            amt = o.get("amount")
            if isinstance(amt, (int, float)) and "count" not in o:
                if isinstance(o.get("fluid"), str):
                    fluids.append((("fluid", o["fluid"]), amt))
                elif isinstance(o.get("ingredient"), dict) and isinstance(o["ingredient"].get("fluid"), str):
                    fluids.append((("fluid", o["ingredient"]["fluid"]), amt))
                elif isinstance(o.get("tag"), str) and "chemical" not in str(key).lower():
                    fluids.append((("ftag", o["tag"]), amt))
                return
            if "chemical" in o or str(o.get("type", "")).startswith("neoforge:components"):
                return
            alts = _cycle_alts(o) if ("item" in o or "tag" in o or "basePredicate" in o) else None
            if alts:
                n = o.get("count", 1)
                items.append((alts, n if isinstance(n, int) and n > 0 else 1))
                return
            for k, v in o.items():
                if k in _YIELD_KEYS or k in RESULT_KEYS or k in ("pattern", "key", "mold", "transitional_item"):
                    continue
                walk(v, k)
        elif isinstance(o, list):
            if o and all(isinstance(v, dict) and ("item" in v or "tag" in v) and "amount" not in v for v in o) and key in (
                    "ingredient", "input", "basePredicate"):
                items.append((_cycle_alts(o), 1))
                return
            for v in o:
                if isinstance(v, list):
                    alts = _cycle_alts(v)
                    if alts:
                        items.append((alts, 1))
                        continue
                walk(v, key)
        elif isinstance(o, str) and key in ("input", "ingredient", "ingredients", "inputs") and ":" in o:
            items.append((_cycle_alts(o), 1))

    if isinstance(j.get("pattern"), list) and isinstance(j.get("key"), dict):
        occ = collections.Counter(c for row in j["pattern"] for c in row if c != " ")
        for ch, n in occ.items():
            alts = _cycle_alts(j["key"].get(ch))
            if alts:
                items.append((alts, n))
    walk(j)
    return items, fluids


def metal_conversions(full, tags, fluid_ids, disabled=frozenset()):
    """(recipe id, metal inputs as a set of alternative nodes, their amount, {output node: amount}, node -> group) of
    every recipe whose metal inputs are one kind (see L-8); amounts are item counts or mB. A recipe with a disabled
    result (the firmages-core gate drops it) or with a slot only disabled items fill (nobody has one) is left out."""
    au = load_au()
    metals = sorted(t.split("/", 1)[1] for t in tags if t.startswith("c:ingots/") and tags[t])
    parent = {}

    def find(x):
        while parent.setdefault(x, x) != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    node_groups = collections.defaultdict(set)
    for m in metals:
        for f in CYCLE_FORMS:
            for i in tags.get(f"c:{f}/{m}", []):
                if i not in disabled:
                    node_groups[i].add(m)
    for i, ms in node_groups.items():   # aliases share items (c:ingots/iron and c:ingots/wrought_iron)
        ms = sorted(ms)
        for m in ms[1:]:
            parent[find(m)] = find(ms[0])
    group = {i: find(sorted(ms)[0]) for i, ms in node_groups.items()}
    mrx = re.compile(r"^[a-z0-9_]+:(?:metal/|molten_)(%s)$" % "|".join(sorted(map(re.escape, metals), key=len, reverse=True)))
    for fl in fluid_ids:
        mm = mrx.match(fl)
        if mm:
            group["fluid:" + fl] = find(mm.group(1))

    def members(alts):
        return {i for kind, v in alts for i in (tags.get(v, []) if kind == "tag" else [v]) if i not in disabled}

    def fluid_nodes(kind, v):
        if kind == "fluid":
            return {"fluid:" + v} & group.keys()
        mm = re.match(r"^c:molten_([a-z0-9_]+)$", v)
        return {"fluid:" + f for f in fluid_ids if mm and f.endswith(":molten_" + mm.group(1))} & group.keys()

    def tag_outs(o, out, inres=False, mult=1.0):
        if isinstance(o, dict):
            if inres and isinstance(o.get("tag"), str) and "amount" not in o:
                for t in au_target(o["tag"], tags.get(o["tag"], []), au):
                    out.append((t, mult))
                return out
            if inres and isinstance(o.get("count"), (int, float)) and isinstance(o.get("basePredicate"), dict):
                mult = mult * o["count"]
            for k, v in o.items():
                tag_outs(v, out, inres or k in _YIELD_KEYS, mult)
        elif isinstance(o, list):
            for v in o:
                tag_outs(v, out, inres, mult)
        return out

    for rid, j in full.items():
        if not isinstance(j, dict) or j.get("type") in _WORLD_TYPES:
            continue
        if any(o in disabled for o in _outs(j, [])):
            continue
        items, fluids = _cycle_inputs(j)
        ins, dead = [], False   # (frozenset of nodes, amount)
        for alts, n in items:
            mem = members(alts or [])
            dead = dead or not mem
            ns = mem & group.keys()
            if ns:
                ins.append((frozenset(ns), n))
        if dead:
            continue
        for (kind, v), amt in fluids:
            ns = fluid_nodes(kind, v)
            if ns:
                ins.append((frozenset(ns), amt))
        kinds = {k for k, _ in ins}
        if len(kinds) != 1:
            continue
        src = next(iter(kinds))
        total = sum(n for _, n in ins)
        outs = collections.Counter()
        for oid, amt in _yields(j, []) + tag_outs(j, []):
            key = oid if oid in group else ("fluid:" + oid if "fluid:" + oid in group else None)
            if key:
                outs[key] += amt
        if outs:
            yield rid, src, total, outs, group


def metal_cycle_edges(full, tags, fluid_ids, disabled=frozenset()):
    """group -> {(a, b): (ratio, recipe id)}: the best edge a -> b within every metal group (see L-8)."""
    edges = collections.defaultdict(dict)
    for rid, src, total, outs, group in metal_conversions(full, tags, fluid_ids, disabled):
        for a in src:
            for b, amt in outs.items():
                if group[a] != group[b] or amt <= 0:
                    continue
                r = amt / total
                g = group[a]
                if r > edges[g].get((a, b), (0, ""))[0]:
                    edges[g][(a, b)] = (r, rid)
    return edges


def value_cycles(edges, max_len=6):
    """[(group, product, [(a, b, ratio, recipe id), ...])]: the simple cycles of every metal group whose product exceeds
    1, the shortest first; a cycle that only adds detours to a reported one (a superset of its nodes) is left out."""
    found = []
    for g, es in sorted(edges.items()):
        out = collections.defaultdict(list)
        for (a, b), (r, rid) in es.items():
            out[a].append((b, r, rid))
        nodes = sorted(out)
        rank = {n: k for k, n in enumerate(nodes)}
        cycles = []

        def dfs(start, cur, prod, path, steps):
            for b, r, rid in out.get(cur, ()):
                if b == start:
                    if prod * r > 1 + CYCLE_EPS:
                        cycles.append((prod * r, steps + [(cur, b, r, rid)]))
                elif b not in path and rank.get(b, -1) > rank[start] and len(steps) + 1 < max_len:
                    dfs(start, b, prod * r, path | {b}, steps + [(cur, b, r, rid)])
        for s in nodes:   # each cycle once, from its smallest node
            dfs(s, s, 1.0, {s}, [])
        kept = []
        for prod, steps in sorted(cycles, key=lambda c: (len(c[1]), -c[0])):
            ns = {a for a, _, _, _ in steps}
            if any(k <= ns for k in kept):
                continue
            kept.append(ns)
            found.append((g, prod, steps))
    return found


def cycle_checks(full, tags, fluid_ids, disabled, check):
    edges = metal_cycle_edges(full, tags, fluid_ids, disabled)
    cyc = value_cycles(edges)
    ev = []
    for g, prod, steps in cyc:
        ev.append(f"{g} x{prod:.3g}: " + " -> ".join(f"{a} [{rid} x{r:.3g}]" for a, b, r, rid in steps))
    n_edges = sum(len(e) for e in edges.values())
    check("L-8 no value-creating metal cycle: no chain of recipes over the forms of one metal (ingot, nugget, block, "
          "sheet, plate, rod, dust, molten metal) gives back more than went in",
          not cyc, ev + [f"{len(edges)} metal groups, {n_edges} conversion edges"])


# ================================================================================================ wood classes (W-)
# dev/wood_classes.toml, dev/gen_wood_classes.py, kubejs/server_scripts/recipes/age_0/wood_classes.js (HW1A).
# Recipes whose result names no wood but that take one wood only, kept on purpose (W-6): rituals and processing with a
# thematic wood, prints that copy a vanilla door's or trapdoor's picture, a card from an oak boat, the Beneath's crimson
# unposter, Dusty Decorations' cutting board from stripped oak.
WOOD_W6_ALLOW = [
    r"^occultism:ritual/", r"^mekanism:reaction/wood_gasification/", r"^twilightforest:", r"^advanced_ae:swim_speed_card$",
    r"^mcwdoors:print_", r"^mcwtrpdoors:print_", r"^beneath:crafting/unposter$", r"^dustydecorations:cutting_board$"]
WOOD_GRID_TYPES = ("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "tfc:advanced_shaped_crafting",
                   "tfc:advanced_shapeless_crafting", "tfc:no_remainder_shapeless_crafting",
                   "tfc:no_remainder_shaped_crafting", "kubejs:shaped", "kubejs:shapeless")


def _ing_accepts(ing, item, tags):
    """True when a grid ingredient JSON accepts the item."""
    if isinstance(ing, list):
        return any(_ing_accepts(x, item, tags) for x in ing)
    if isinstance(ing, str):
        return item in tags.get(ing[1:], []) if ing.startswith("#") else ing == item
    if not isinstance(ing, dict):
        return False
    if "item" in ing:
        return ing["item"] == item
    if "tag" in ing:
        return item in tags.get(ing["tag"], [])
    if "items" in ing:
        return _ing_accepts(ing["items"], item, tags)
    for k in ("ingredient", "children", "values"):
        if k in ing:
            return _ing_accepts(ing[k], item, tags)
    return False


def _first_member(ing, tags):
    if isinstance(ing, list):
        return _first_member(ing[0], tags) if ing else None
    if isinstance(ing, str):
        return (sorted(tags.get(ing[1:], [])) or [None])[0] if ing.startswith("#") else ing
    if isinstance(ing, dict):
        if "item" in ing:
            return ing["item"]
        if "tag" in ing:
            return (sorted(tags.get(ing["tag"], [])) or [None])[0]
    return None


def _grid(j):
    """('shaped', rows of ingredient JSON or None) or ('shapeless', [ingredient JSON]) of a grid recipe, else None."""
    if isinstance(j.get("pattern"), list) and isinstance(j.get("key"), dict):
        return "shaped", [[(j["key"].get(ch) if ch != " " else None) for ch in row] for row in j["pattern"]]
    if isinstance(j.get("ingredients"), list):
        return "shapeless", j["ingredients"]
    return None


def _grid_matches(g, cells, tags):
    """Does grid recipe g accept the concrete grid `cells` (shaped: rows of item or None; shapeless: item list)?"""
    kind, body = g
    flat = [c for row in cells for c in row if c] if cells and isinstance(cells[0], list) else list(cells)
    if kind == "shapeless":
        if len(flat) != len(body):
            return False
        used = [False] * len(body)

        def place(k):
            if k == len(flat):
                return True
            for n, ing in enumerate(body):
                if not used[n] and _ing_accepts(ing, flat[k], tags):
                    used[n] = True
                    if place(k + 1):
                        return True
                    used[n] = False
            return False
        return place(0)
    if not cells or not isinstance(cells[0], list):
        return False
    for variant in (body, [list(reversed(r)) for r in body]):
        if len(variant) == len(cells) and all(len(a_) == len(b_) for a_, b_ in zip(variant, cells)):
            if all((ing is None) == (it is None) and (ing is None or _ing_accepts(ing, it, tags))
                   for r1, r2 in zip(variant, cells) for ing, it in zip(r1, r2)):
                return True
    return False


def wood_checks(base, rows, tags, item_age, check):
    import tomllib
    path = os.path.join(base, "recipes_full.json")
    if not os.path.isfile(path):
        return
    full = {k: json.loads(v) for k, v in json.load(open(path, encoding="utf-8")).items()}
    with open(os.path.join(REPO, "dev", "wood_classes.toml"), "rb") as f:
        table = tomllib.load(f)
    classes = table["classes"]
    kdata = os.path.join(REPO, "kubejs", "data")

    def vlog(k, c):
        return f"minecraft:{k}_stem" if c.get("nether") else f"minecraft:{k}_log"

    # W-1 drops: every natural trunk drops its class log, a placed one itself, a hammer sticks
    bad = []
    n = 0
    for k, c in classes.items():
        for s in c["species"] + c.get("variants", []):
            ns, sp = s.split(":")
            for kind in ("log", "wood"):
                p = os.path.join(kdata, ns, "loot_table", "blocks", "wood", kind, f"{sp}.json")
                try:
                    alts = json.load(open(p, encoding="utf-8"))["pools"][0]["entries"][0]["children"]
                except (OSError, ValueError, KeyError, IndexError):
                    bad.append(f"{ns}:blocks/wood/{kind}/{sp}: no pack loot table")
                    continue
                n += 1
                names = [x.get("name") for x in alts]
                cond = json.dumps(alts[1].get("conditions", []))
                if names != ["minecraft:stick", f"{ns}:wood/{kind}/{sp}", vlog(k, c)] or '"branch_direction": "none"' not in cond:
                    bad.append(f"{ns}:blocks/wood/{kind}/{sp}: drops {names}")
    check(f"W-1 natural trunks drop their class log, placed trunks themselves, hammers sticks ({n} loot tables)",
          not bad, bad)

    # W-2 fallen trunks and ruins (placed state, species log) turn back into the class log
    miss = []
    for k, c in classes.items():
        for s in c["species"] + c.get("variants", []):
            rid = f"firmages:wood/class_log/{s.replace(':', '_')}"
            j = full.get(rid)
            if not j or vlog(k, c) not in json.dumps(j.get("result", {})):
                miss.append(f"{rid}: missing or wrong result")
    check("W-2 every species log and wood has its one-step way back to the class log (R7)", not miss, miss)

    # W-3 fuel, tannin, firepit
    bad = []
    for k, c in classes.items():
        if not os.path.isfile(os.path.join(kdata, "firmages", "tfc", "fuel", f"{k}_logs.json")):
            bad.append(f"no fuel for {k} logs")
        # Nether stems do not burn, the Beneath's own stems are no firepit logs either
        if not c.get("nether") and vlog(k, c) not in tags.get("tfc:firepit_logs", []):
            bad.append(f"{vlog(k, c)} not in tfc:firepit_logs")
    for w in ("oak", "birch", "spruce", "dark_oak", "jungle", "acacia"):
        if f"minecraft:{w}_log" not in tags.get("tfc:tannin_logs", []):
            bad.append(f"minecraft:{w}_log not in tfc:tannin_logs")
    if "minecraft:crafting_table" not in tags.get("tfc:workbenches", []):
        bad.append("minecraft:crafting_table not in the item tag tfc:workbenches")
    check("W-3 vanilla logs burn (TFC fuel), tan (tfc:tannin_logs) and light the firepit; the crafting table is a workbench",
          not bad, bad)

    # W-4 one recipe per grid: each wood-class recipe's own grid matches no other grid recipe
    grids = {}
    for rid, j in full.items():
        t = str(j.get("type", ""))
        if t in WOOD_GRID_TYPES or t.endswith(("crafting_shaped", "crafting_shapeless")):
            g = _grid(j)
            if g:
                grids[rid] = g
    clash, tested = [], 0
    for rid, g in sorted(grids.items()):
        if not rid.startswith("firmages:wood/"):
            continue
        kind, body = g
        if kind == "shaped":
            cells = [[_first_member(ing, tags) if ing is not None else None for ing in row] for row in body]
            if any(c is None for row, crow in zip(body, cells) for ing, c in zip(row, crow) if ing is not None):
                continue
        else:
            cells = [_first_member(ing, tags) for ing in body]
            if None in cells:
                continue
        tested += 1
        for other, g2 in grids.items():
            if other != rid and _grid_matches(g2, cells, tags):
                clash.append(f"{rid} and {other} take the same grid {cells}")
    check(f"W-4 every wood-class grid makes exactly one recipe ({tested} grids against {len(grids)} grid recipes)",
          tested > 0 and not clash, clash)

    # W-5 Ages
    want = {}
    for k, c in classes.items():
        want[vlog(k, c)] = "age_3" if c.get("nether") else "dawn"
        want[f"minecraft:{k}_planks"] = "age_3" if c.get("nether") else "age_0"
        if not c.get("nether"):
            want["minecraft:mangrove_propagule" if k == "mangrove" else f"minecraft:{k}_sapling"] = "dawn"
    want.update({"minecraft:crafting_table": "age_0", "minecraft:bamboo": "age_0", "minecraft:bamboo_block": "age_0",
                 "minecraft:bamboo_planks": "age_0"})
    off = [f"{i}: {item_age.get(i)} (want {st})" for i, st in sorted(want.items()) if item_age.get(i) != st]
    check("W-5 class logs and saplings Dawn, planks, crafting table and bamboo Stone Age, Nether stems Arcane Age",
          not off, off)

    # W-6 no recipe with a wood-neutral result that takes one wood only (one class or one species in a slot)
    # chip_wood: Create Diesel Generators' wood type; Every Compat builds its furniture from oak planks
    woods = set(classes) | {s.split(":")[1] for c in classes.values() for s in c["species"] + c.get("variants", [])}         | {"chip_wood"}
    tok = re.compile(r"(^|[/_:])(" + "|".join(sorted(woods, key=len, reverse=True)) + r")([/_]|$)")
    base_rx = re.compile(r"^(minecraft:(stripped_)?\w+?_(log|wood|planks|stem|hyphae)|(tfc|afc|beneath):wood/"
                         r"(log|wood|stripped_log|stripped_wood|planks|lumber)/\w+)$")
    allow = [re.compile(x) for x in WOOD_W6_ALLOW]
    bound = []
    for rid, j in full.items():
        if any(x.search(rid) for x in allow):
            continue
        outs = _outs(j, [])
        if not outs or any(tok.search(o.split(":", 1)[1]) for o in outs if ":" in o):
            continue
        for slot in _slots(j, []):
            mem = {x for k_, x in slot if k_ == "item"} | {m for k_, x in slot if k_ == "tag" for m in tags.get(x, [])}
            wood = {m for m in mem if base_rx.match(m)}
            kinds = {tok.search(m.split(":", 1)[1]).group(2) for m in wood if tok.search(m.split(":", 1)[1])}
            if wood and wood == mem and len(kinds) == 1:
                bound.append(f"{rid} -> {outs[0]} takes only {sorted(wood)[:3]}")
                break
    check(f"W-6 no recipe with a wood-neutral result takes one wood only (allowlist {len(WOOD_W6_ALLOW)} patterns)",
          not bound, sorted(bound))

    # W-7 the lead species' function blocks take the class tags
    miss = []
    for k, c in classes.items():
        ns, sp = c["lead"].split(":")
        for kind in ("support", "axle", "crate", "clutch", "encased_axle", "sewing_table", "water_wheel",
                     "scribing_table", "shelf"):
            rid = f"{ns}:crafting/wood/{kind}/{sp}"
            if rid in full and f"firmages:wood_class/{k}/" not in json.dumps(full[rid]).replace(" ", ""):
                miss.append(f"{rid}: no class tag")
    check("W-7 the lead species' TFC function blocks take the class tags (R9)", not miss, miss)

    # W-8 style parity in the recipe viewer: no pixel twin of a vanilla original and no style TFC wood lacks
    sys.path.insert(0, os.path.join(REPO, "dev"))
    import gen_stage_locks as gsl
    am = gsl.AgeMap(gsl.tomllib.loads(open(gsl.AGE_MAP, encoding="utf-8").read()),
                    json.load(open(gsl.REGISTRY, encoding="utf-8")), json.load(open(gsl.TFC_IDS, encoding="utf-8")))
    twin = re.compile(r"^everycomp:[a-z_]+/tfc/(.+_)?(oak|mangrove)(_.+)?$")
    seen = sorted(i for i, st in item_age.items() if st != "disabled" and (twin.match(i) or am.parity_hidden(i)))
    vis = collections.Counter(i.split(":")[0] for i, st in item_age.items()
                              if st != "disabled" and i.split(":")[0] in am.parity_mods
                              and am.parity_wood.search(i.split(":")[1]))
    check("W-8 no Every Compat TFC oak/mangrove twin and no vanilla-wood style without a TFC-wood copy is visible",
          not seen, seen)
    print("    visible vanilla-wood deco by mod: " + ", ".join(f"{k}={v}" for k, v in vis.most_common()))


def sapling_checks(item_age, check):
    """W-9 (review fix 0.12.0): no tree a vanilla sapling grows places a block of a later Age than the sapling (a bee
    nest, moss carpet, podzol, azalea leaves). The sapling -> feature table and the vanilla features' blocks are in
    dev/gen_wood_classes.py; a feature the pack overrides is read from kubejs/data (its "Name" states and the features it
    places, recursively)."""
    sys.path.insert(0, os.path.join(REPO, "dev"))
    import gen_wood_classes as gwc

    def blocks_of(feature, seen):
        if feature in seen:
            return set()
        seen.add(feature)
        ns, path = feature.split(":", 1)
        f = os.path.join(REPO, "kubejs", "data", ns, "worldgen", "configured_feature", *path.split("/")) + ".json"
        if os.path.isfile(f):
            text = open(f, encoding="utf-8").read()
            out = set(re.findall(r'"Name": "([a-z0-9_:/.-]+)"', text))
            for ref in re.findall(r'"feature": "([a-z0-9_:/.-]+)"', text):
                out |= blocks_of(ref, seen)
            return out
        if feature in gwc.VANILLA_FEATURE_BLOCKS:
            return set(gwc.VANILLA_FEATURE_BLOCKS[feature])
        return {"?unknown feature " + feature}

    bad, n = [], 0
    for sap, feats in sorted(gwc.SAPLING_GROWTH.items()):
        sa = item_age.get(sap)
        if sa is None or sa == "disabled":
            continue
        for feat in feats:
            n += 1
            for b in sorted(blocks_of(feat, set())):
                ba = item_age.get(b, "?")
                if b.startswith("?") or ba == "?" or ba == "disabled" or AGES.index(ba) > AGES.index(sa):
                    bad.append(f"{sap} ({sa}) grows {feat}: {b} ({ba})")
    check(f"W-9 a vanilla sapling grows only blocks of its own Age or earlier ({n} sapling features)", not bad, bad)


# ================================================================================================ world (X-1 to X-4)
# f1 gating audit (2026-10-16): what the world shows or hands out before its Age. Read from dev/data/world_sources.json
# (derive_ages.py --scan) with the world model of dev/derive_ages.py (biome, feature, template and spawn Ages).
# X-2/X-3: blocks a place shows before their Age on purpose: (block, place prefix or "*") -> reason.
WORLD_BLOCK_ALLOW = {
    ("minecraft:bedrock", "*"): "the floor and roof of the Nether; nothing breaks it",
    ("minecraft:spawner", "*"): "a dungeon spawner works as a spawner; it drops nothing in any Age",
    ("minecraft:jigsaw", "*"): "replaced while the structure generates, never in the world",
    ("sgjourney:universe_stargate", "sgjourney:stargate/universe/destiny/"): "the Destiny's own gate in a Stargate "
        "world (age_9); Universe gates are not part of the pack, the block stays locked",
    ("minecraft:structure_block", "*"): "replaced while the structure generates, never in the world",
}
# X-4: items named after a mob that spawns, never reachable on purpose: item -> reason.
MOB_PRODUCT_ALLOW = {
    "ars_nouveau:whirlisprig_shards": "Ars Nouveau 5.13.3 never sets Whirlisprig.droppingShards (javap)",
}
MOB_PRODUCT_SUFFIX = ("_bucket", "_shard", "_shards", "_charm")


def world_checks(base, item_age, check):
    """X-1: no structure of a later mod's Age generates in an earlier Age's world (Apotheosis towers in Dawn; Stargate
    Journey's buried gate in the overworld bypassed the crafted gate of the Singularity Age).
    X-2: no block that world generation places is of a later Age than its place and gives nothing of that Age when
    mined, unless a ProgressiveStages ore override disguises it (age_blocks tag) (Evolved Mekanism's Nether and End
    ores, ice shard ore on the Moon).
    X-3: the same for structure templates, on the blocks of the template's own namespace and every block of the pack's
    own templates (firmages); the furnishing a mod's structure borrows (a barrel in a Moon village) is the mod's look,
    not a source (decision 2026-10-13 "Erreichbarkeit: Strukturen").
    X-4: no item named after a spawning mob (bucket, shard, charm) is never reachable (Drygmy shards, the Alex's Mobs
    fish buckets).
    The world model is dev/derive_ages.py World on dev/data/world_sources.json."""
    sys.path.insert(0, os.path.join(REPO, "dev"))
    import tomllib
    import derive_ages as da
    reg = json.load(open(os.path.join(base, "registries.json"), encoding="utf-8"))
    tags = json.load(open(os.path.join(base, "item_tags.json"), encoding="utf-8"))
    amap = tomllib.load(open(os.path.join(REPO, "dev", "age_map.toml"), "rb"))
    ws = da.load_world_sources()
    items, blocks = set(reg["item"]), set(reg["block"])
    w = da.World(ws, amap["mods"], items, blocks)
    disguised = set()
    bdir = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "block", "age_blocks")
    for f in os.listdir(bdir):
        disguised |= {v["id"] if isinstance(v, dict) else v for v in json.load(open(os.path.join(bdir, f)))["values"]}

    def ix(st):
        return AGES.index(st) if st in AGES else 99

    def later(a, st):
        return a == "disabled" or (a in AGES and ix(a) > ix(st))

    def drops_now(b, st):
        """Items of the block's loot table that are unlocked at st."""
        ns, path = b.split(":", 1)
        t = ws["loot"].get(f"{ns}:blocks/{path}")
        if t is None:
            return set()
        got = set(t["items"]) | {m for tg in t["tags"] for m in tags.get(tg, [])}
        return {i for i in got if item_age.get(i) in AGES and ix(item_age[i]) <= ix(st)}

    def allowed(b, place):
        return any(bb == b and (pre == "*" or place.startswith(pre)) for (bb, pre) in WORLD_BLOCK_ALLOW)

    def shows_early(b, st, place):
        return (b in items and b not in disguised and not allowed(b, place)
                and later(item_age.get(b, "dawn"), st) and not drops_now(b, st))

    # X-1: structure Age = earliest Age of its biomes; mod Age = [mods] Age of its template pool's namespace; structures
    # a mod config switches off (derive_ages.structures_off: Stargate Journey's common stargates) do not count
    off = da.structures_off()
    bad, n = [], 0
    for sid, s in sorted(ws["structures"].items()):
        st = w.age_of_biomes(s["biomes"])
        if st is None or sid.startswith(off):
            continue
        n += 1
        ns = (s["pool"] or sid).split(":")[0]
        mod = amap["mods"].get(ns) or amap["mods"].get(sid.split(":")[0])
        if mod and later(mod, st):
            bad.append(f"{sid} generates from {st} ({s['biomes'] if isinstance(s['biomes'], str) else 'biome set'}), "
                       f"its mod {ns} is {mod}")
    check(f"X-1 no structure of a later mod's Age generates in an earlier Age ({n} structures with a place)", not bad, bad)

    # X-2: blocks of world generation and terrain (World.world_blocks keeps each block's earliest place)
    bad, n = [], 0
    for b, (st, why) in sorted(w.world_blocks.items()):
        if not why.startswith(("world generation", "terrain")):
            continue
        n += 1
        if shows_early(b, st, why.split()[-1]):
            bad.append(f"{b} ({item_age.get(b)}) placed from {st} by {why}; mined there it gives nothing of {st}")
    check(f"X-2 world generation shows no block of a later Age that gives nothing at its place ({n} placed blocks)",
          not bad, bad)

    # X-3: the own blocks of every structure template that has a place
    bad, n = [], 0
    for tid, st in sorted(w.template_age.items()):
        n += 1
        ns = tid.split(":")[0]
        for b in ws["templates"][tid]["blocks"]:
            if (ns == "firmages" or b.split(":")[0] == ns) and shows_early(b, st, tid):
                bad.append(f"{tid} ({st}): {b} ({item_age.get(b)})")
    check(f"X-3 structure templates show no own block of a later Age that gives nothing at their Age ({n} templates)",
          not bad, bad)

    # X-4: never reachable items named after a mob that spawns
    hidden = set(amap.get("derived_hidden", {}).get("items", []))
    spawning = set(w.entity_src)
    bad = []
    for i in sorted(hidden):
        ns, path = i.split(":", 1)
        if not path.endswith(MOB_PRODUCT_SUFFIX) or i in MOB_PRODUCT_ALLOW:
            continue
        mob = next((e for e in sorted(spawning) if e.split(":")[0] == ns and path.startswith(e.split(":", 1)[1] + "_")),
                   None)
        if mob:
            bad.append(f"{i}: never reachable, but {mob} spawns ({sorted(w.entity_src[mob])[0]}); model how the mob "
                       f"yields it (derive_ages.py INTERACTIONS) or list it in MOB_PRODUCT_ALLOW")
    stale = [f"MOB_PRODUCT_ALLOW {i} is no longer hidden; drop the row" for i in MOB_PRODUCT_ALLOW if i not in hidden]
    check(f"X-4 every bucket, shard or charm of a spawning mob is reachable ({len(spawning)} spawning mobs)",
          not bad and not stale, bad + stale)



def load_metals():
    return json.load(open(METALS_JSON, encoding="utf-8"))


def _metal_res(metals):
    """metal -> (item-form regex, fluid/chemical regex); ingots, nuggets, dusts, clumps, shards, crystals, sheets, rods,
    blocks, raw ore items and Theurgy sulfurs of the metal or one of its aliases (cast_iron, wrought_iron, ...)."""
    out = {}
    for m, d in metals.items():
        al = "|".join(sorted(d.get("aliases", [m]), key=len, reverse=True))
        item = re.compile(rf"^[a-z0-9_]+:(?:.*/)?(?:{_METAL_FORMS})[/_](?:{al})$"
                          rf"|^[a-z0-9_]+:(?:.*/)?(?:raw_)?(?:{al})_(?:ingot|nugget|dust|block|sheet|plate|rod)$")
        fluid = re.compile(rf"^[a-z0-9_]+:(?:metal/(?:{al})|molten_(?:{al})|(?:dirty|clean)_(?:{al}))$")
        out[m] = (item, fluid)
    return out


def _yields(o, out, inres=False, mult=1.0):
    """(id, expected amount) of every output of a recipe JSON: count x chance for items, mB for fluids and chemicals;
    IE's {basePredicate, count} and {output, chance} wrappers multiply into their item."""
    num = (int, float)
    if isinstance(o, dict):
        idv = o.get("id") if isinstance(o.get("id"), str) else o.get("item") if isinstance(o.get("item"), str) else None
        if inres and idv:
            n = o.get("count", o.get("amount", 1))
            ch = o.get("chance", 1.0)
            out.append((idv, (n if isinstance(n, num) else 1) * (ch if isinstance(ch, num) else 1.0) * mult))
            return out
        if inres:
            if isinstance(o.get("chance"), num):
                mult *= o["chance"]
            if isinstance(o.get("count"), num) and any(isinstance(o.get(k), dict) for k in ("basePredicate", "output", "stack")):
                mult *= o["count"]
        for k, v in o.items():
            _yields(v, out, inres or k in _YIELD_KEYS, mult)
    elif isinstance(o, list):
        for v in o:
            _yields(v, out, inres, mult)
    return out


def _input_count(o, item, parent=None):
    """How many of `item` a recipe JSON consumes (Mekanism item_input count, Theurgy and IE sized ingredients), or None."""
    if isinstance(o, dict):
        if o.get("item") == item:
            c = o.get("count", parent)
            return c if isinstance(c, int) and c > 0 else 1
        own = o.get("count") if isinstance(o.get("count"), int) else None
        for k, v in o.items():
            if k in _YIELD_KEYS or k in RESULT_KEYS:
                continue
            r = _input_count(v, item, own if isinstance(v, dict) and k in ("ingredient", "basePredicate") else None)
            if r:
                return r
    elif isinstance(o, list):
        for v in o:
            r = _input_count(v, item, parent)
            if r:
                return r
    return None


def ladder_checks(full, recs, items, tags, check):
    """L-1 .. L-5 on the all-Ages recipe dump (see the comment above METALS_JSON)."""
    if not os.path.isfile(METALS_JSON):
        check("L-0 kubejs/data/firmages/balance/metals.json present", False, [METALS_JSON])
        return
    mt = load_metals()
    grades, tol, metals, stations = mt["grade_mb"], mt["tolerance"], mt["metals"], mt["stations"]
    item_mb, nug_mb, slurry = mt["units"]["item_mb"], mt["units"]["nugget_mb"], mt["units"]["slurry_mb_per_ingot"]
    neutral = set(mt["neutral_types"]["types"])
    res = _metal_res(metals)
    by_type = {s["type"]: k for k, s in stations.items()}
    st_js = load_station_ages()
    aix = AGES.index

    def metal_mb(metal, oid, amount):
        item_re, fluid_re = res[metal]
        if fluid_re.match(oid):
            return amount * item_mb / slurry if re.search(r":(dirty|clean)_", oid) else amount
        if item_re.match(oid):
            if "nugget" in oid:
                return amount * nug_mb
            return amount * item_mb * (9 if re.search(r"(^|[:/_])(storage_)?block([/_]|$)", oid) else 1)
        return 0.0
    # powder: mB per powder item from its recipe at the powder station (Enrichment Chamber: 64 powder -> 1 dust)
    powder_mb, powder_age = {}, {}
    pst = mt["powder"]["station_type"]
    for rid, j in full.items():
        if j.get("type") != pst:
            continue
        ins = re.findall(r'"item": "([a-z0-9_]+:powder/[a-z_]+)"', json.dumps({k: v for k, v in j.items() if k not in _YIELD_KEYS}))
        if len(ins) != 1:
            continue
        n = _input_count(j, ins[0]) or 1
        val = sum(metal_mb(m, oid, a) for oid, a in _yields(j, []) for m in metals)
        if val > 0:
            powder_mb[ins[0]] = max(powder_mb.get(ins[0], 0), val / n)
            powder_age[ins[0]] = st_js.get(pst, "age_5")
    # every piece -> metal; every recipe that consumes a piece
    piece_metal = {}
    for m, d in metals.items():
        for pre in d["ores"]:
            for g in grades:
                piece_metal[pre.format(g=g)] = (m, g, pre)
    piece_rx = re.compile(r'"item": "([a-z0-9_]+:ore/(?:small|poor|normal|rich)_[a-z_]+)"')
    measured = collections.defaultdict(list)   # (piece, type) -> [(rid, direct factor, powder factor, powder Age)]
    for rid, j in full.items():
        t = j.get("type", "")
        if t in _WORLD_TYPES:
            continue
        ins = set(piece_rx.findall(json.dumps({k: v for k, v in j.items() if k not in _YIELD_KEYS and k not in RESULT_KEYS})))
        for p in ins & set(piece_metal):
            m, g, _ = piece_metal[p]
            n = _input_count(j, p) or 1
            outs = _yields(j, [])
            direct = sum(metal_mb(m, oid, a) for oid, a in outs)
            pw = sum(a * powder_mb.get(oid, 0) for oid, a in outs)
            pa = max((powder_age.get(oid, "age_0") for oid, a in outs if oid in powder_mb), key=aix, default=None)
            measured[(p, t)].append((rid, direct / (n * grades[g]), pw / (n * grades[g]), pa))

    # ---- L-1: the table --------------------------------------------------------------------------------------------
    bad1 = []
    for k, s in stations.items():
        # the station may come earlier than its ladder step (FirmAges.requireAge holds Injection and Dissolution back)
        if s["type"] in st_js and aix(st_js[s["type"]]) > aix(s["age"]):
            bad1.append(f"station {k}: {s['type']} is {st_js[s['type']]} in station_ages.js, after {s['age']} in metals.json")
    entries = collections.defaultdict(list)   # piece -> [(effective Age, kind, station, factor at that Age)]
    for p, (m, g, pre) in sorted(piece_metal.items()):
        if p not in items:
            bad1.append(f"{p}: no such item")
            continue
        first = metals[m]["first_age"]
        for k in metals[m]["stations"]:
            s = stations[k]
            got = measured.get((p, s["type"]))
            if not got:
                bad1.append(f"{p}: no {k} recipe ({s['type']})")
                continue
            rid, f, pw, pa = max(got, key=lambda x: x[1])
            if abs(f - s["factor"]) > tol * s["factor"]:
                bad1.append(f"{p}: {k} {f:.3f} ({rid}), table {s['factor']}")
            eff = max(s["age"], first, key=aix)
            entries[p].append((eff, s["kind"], k, f))
            if pw > 0 and s["kind"] == "tech":
                entries[p].append((max(eff, pa, key=aix), "tech", f"{k}+powder", f + pw))
    check(f"L-1 metal table (kubejs/data/firmages/balance/metals.json): {len(metals)} metals, {len(piece_metal)} ore pieces; every listed station "
          f"has its recipe and yields its factor within {tol:.0%}; station Ages match station_ages.js", not bad1, bad1)

    # ---- L-2 / L-3: monotonic tech ladder, magic below the next tech step --------------------------------------------
    bad2, bad3 = [], []
    best_by_age = collections.defaultdict(list)   # Age -> [best tech factor of each piece at that Age]
    for p, es in sorted(entries.items()):
        tech = sorted((e for e in es if e[1] == "tech"), key=lambda e: aix(e[0]))
        for e in tech:
            before = [x[3] for x in tech if aix(x[0]) < aix(e[0])]
            if before and e[3] < max(before) * (1 - tol):
                bad2.append(f"{p}: {e[2]} at {e[0]} gives {e[3]:.3f}, an earlier Age already {max(before):.3f}")
        for e in es:
            if e[1] != "magic":
                continue
            nxt = [x for x in tech if aix(x[0]) > aix(e[0])]
            if nxt:
                step = min(nxt, key=lambda x: aix(x[0]))
                top = max(x[3] for x in nxt if x[0] == step[0])
                if e[3] > top * (1 + tol):
                    bad3.append(f"{p}: {e[2]} at {e[0]} gives {e[3]:.3f}, the next tech step ({step[0]}) only {top:.3f}")
        for a in AGES[1:]:
            have_ = [x[3] for x in tech if aix(x[0]) <= aix(a)]
            if have_:
                best_by_age[a].append(max(have_))
    summary = [f"{a}: best tech factor {min(v):.2f} .. {max(v):.2f} over {len(v)} pieces"
               for a, v in sorted(best_by_age.items(), key=lambda x: aix(x[0]))]
    check("L-2 ore ladder is monotonic: no tech station of a later Age yields less than the best one before it",
          not bad2, bad2 + summary)
    check("L-3 magic never yields more than the next tech step", not bad3, bad3)

    # ---- L-4: no unlisted multiplier ----------------------------------------------------------------------------------
    bad4 = []
    for (p, t), got in sorted(measured.items()):
        if t in by_type:
            continue
        for rid, f, pw, pa in got:
            if f + pw > 1 + tol:
                bad4.append(f"{rid} ({t}{', neutral type' if t in neutral else ''}): {p} gives {f + pw:.3f}x")
    check("L-4 no recipe type outside the metal table multiplies ore (quern, millstone, heated mixing and grid recipes "
          "stay at or below 1.0x)", not bad4, bad4)

    # ---- L-5: no metal without ore ------------------------------------------------------------------------------------
    ore_names = sorted({pre.split("{g}_", 1)[1] for d in metals.values() for pre in d["ores"]}, key=len, reverse=True)
    ore_rx = re.compile(r"(^|[:/_])(" + "|".join(ore_names) + r")([/_]|$)|(^|[:/_])ores?([/_]|$)|:deposit/|_ore$")
    ore_tagged = {i for t, mem in tags.items() if t.startswith(("c:ores/", "c:raw_materials/", "c:ores_in_ground/")) for i in mem}

    extra_ores = set(mt.get("extra_ores", {}).get("items", []))

    def is_ore(i):
        return i in ore_tagged or i in extra_ores or bool(ore_rx.search(i))

    metal_items = {i for lst in mt.get("metal_items", {}).values() if isinstance(lst, list) for i in lst}
    any_metal_rx = re.compile(r"^tfc:metal/|^[a-z0-9_]+:(?:.*/)?(?:ingot|nugget|plate|sheet|double_ingot|double_sheet)[/_]"
                              r"|_(?:ingot|nugget)$")

    def is_metal(i):   # a metal form of the table (what L-5 reports)
        return i in metal_items or any(res[m][0].match(i) for m in metals)

    def any_metal(i):  # any metal or alloy form (bronze, steel, electrum ...): it taints what is made from it
        return is_metal(i) or bool(any_metal_rx.search(i))

    def metal_fluid(f):  # any molten metal, alloys included (TFC tfc:metal/<m>, molten_<m>, fluid tags c:molten_<m>)
        return bool(re.search(r":(?:metal/|molten_)", f)) or any(res[m][1].match(f) for m in metals)
    start = ({i for i in items if i.split(":")[0] in WORLD_NS and not world_bucket(i)}
             | {"minecraft:" + x for x in VANILLA_WORLD} | set(MOB_DROPS))
    have = {i for i in start if not is_ore(i) and not any_metal(i)}
    def fluid_ins(o, out):   # fluid ids and fluid tags (neoforge:tag with an amount) outside the outputs
        if isinstance(o, dict):
            if "amount" in o and isinstance(o.get("fluid") or o.get("tag"), str):
                out.add(o.get("fluid") or o.get("tag"))
            for k, v in o.items():
                if k not in _YIELD_KEYS and k not in RESULT_KEYS:
                    fluid_ins(v, out)
        elif isinstance(o, list):
            for v in o:
                fluid_ins(v, out)
        return out
    usable = [(rid, sl, os_) for rid, sl, os_ in recs if full[rid].get("type") not in _WORLD_TYPES
              and not any(metal_fluid(f) for f in fluid_ins(full[rid], set()))]
    made_by, tainted = {}, set()   # tainted: made from a metal form (directly or through other tainted items)

    def sat(slot):
        return any((k in ("item", "chem") and x in have) or (k == "tag" and any(i in have for i in tags.get(x, [])))
                   for k, x in slot)

    def used(sl):
        return {x for slot in sl for k, x in slot if k in ("item", "chem") and x in have} | {
            m for slot in sl for k, x in slot if k == "tag" for m in tags.get(x, []) if m in have}
    grew = True
    while grew:
        grew = False
        for rid, sl, os_ in usable:
            new = {o for o in os_ if o not in have and not is_ore(o)}
            if new and all(sat(x) for x in sl):
                dirty = any(any_metal(u) or u in tainted for u in used(sl))
                for o in new:
                    made_by[o] = rid
                    if dirty:
                        tainted.add(o)
                have |= new
                grew = True
        for seed, got in GROWN.items():
            new = {g for g in got if seed in have and GROWN_NEEDS.get(seed, seed) in have and g not in have}
            if new:
                have |= new
                grew = True
        for made, needs in MACHINE_MADE.items():
            if made not in have and all(x in have for x in needs):
                have.add(made)
                grew = True
    free_metal = sorted(i for i in have if is_metal(i))

    _LADDER_TRACE.update(made_by=made_by, tainted=tainted, have=have)
    # an entrance: a metal form made from inputs that hold no metal (nothing tainted went in)
    roots = [i for i in free_metal if i not in tainted]
    free_metal = roots + [i for i in free_metal if i not in roots]
    check("L-5 no metal without ore: no ingot, nugget, dust, clump, shard, sulfur or other metal form of the table is "
          "reachable from the world when every ore piece, ore block, deposit and raw ore is left out",
          not free_metal, [f"{'ENTRANCE ' if i in roots else ''}{i} <- {made_by.get(i, 'start set')}" for i in free_metal])

    # ---- L-6: the Mineshaft rolls mixed grades (metal table "mineshaft") ----------------------------------------------
    gw = mt.get("mineshaft", {}).get("grade_weight", {})
    want = sum(grades[g] * w for g, w in gw.items()) / max(1, sum(gw.values()))
    tot, mbw, bad6 = 0, 0.0, []
    for rid, j in full.items():
        if j.get("type") != "occultism:miner" or not rid.startswith("firmages:miner/ores/"):
            continue
        res_ = j.get("result", {})
        sid = (res_.get("stack") or {}).get("id", "")
        m_ = re.match(r"^[a-z0-9_]+:ore/(small|poor|normal|rich)_", sid)
        if not m_:
            continue
        w = res_.get("weight", 0)
        tot += w
        mbw += w * grades[m_.group(1)]
    got6 = mbw / tot if tot else 0.0
    if not tot or abs(got6 - want) > tol * want:
        bad6.append(f"mean {got6:.2f} mB per ore piece over weight {tot}, table {want:.2f}")
    check(f"L-6 Dimensional Mineshaft: ore pieces of every grade, mean {got6:.2f} mB (table {want:.2f})", not bad6, bad6)

    # ---- L-7: the Arc Furnace melts a dust at the table's energy (metal table "arc_furnace") --------------------------
    de = mt.get("arc_furnace", {}).get("dust_energy")
    bad7, n7 = [], 0
    for rid, j in full.items():
        if j.get("type") == "immersiveengineering:arc_furnace" and re.match(r"^immersiveengineering:arcfurnace/dust_", rid):
            n7 += 1
            if j.get("energy") != de:
                bad7.append(f"{rid}: {j.get('energy')} FE")
    check(f"L-7 Arc Furnace dust -> ingot at {de} FE ({n7} recipes)", n7 > 0 and not bad7, bad7)

    # ---- U-2: one iron (harmony fix 10, package A5) -------------------------------------------------------------------
    iron = "minecraft:iron_ingot"
    bad_u2 = sorted(f"{rid} ({'input' if any(('item', iron) in slot for slot in sl) else 'output'})"
                    for rid, sl, os_ in recs if any(("item", iron) in slot for slot in sl) or iron in os_)
    check(f"U-2 one iron: no live recipe takes or makes {iron} (wrought iron is c:ingots/iron)", not bad_u2, bad_u2)


# ================================================================================================ reachability per Age
# R- checks: the goal chains close at their own Age. The walk starts from what a team has at Dawn without a recipe
# (the TFC-world mods, vanilla world items and drops by the mob ladder of Doc 08 section 5, boss drops of the Age's
# checkpoint) and fires a recipe only when every ingredient is reached, every output belongs to the Age or earlier
# (the firmages-core gate) and its station exists by then (mod Age of the recipe type in dev/age_map.toml, Create
# heat levels). Runs on a dump with every Age unlocked, like the A- and C- checks.
# Wood classes (dev/wood_classes.toml): every natural TFC/AFC trunk drops the vanilla log of its class, and vanilla trees
# grow in TFC forests (logs, leaves, saplings).
VANILLA_TREES = " ".join(f"{w}_log {w}_leaves {w}_sapling" for w in (
    "oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry")) + " mangrove_log mangrove_leaves mangrove_propagule"
VANILLA_BY_AGE = {
    "dawn": "feather egg bone string spider_eye rotten_flesh ink_sac glow_ink_sac clay_ball flint snowball ice snow_block "
            "charcoal stick cobweb rabbit_hide rabbit_foot leather bone_meal redstone clay sugar_cane sugar paper glass "
            "white_wool brown_mushroom red_mushroom cactus dead_bush vine lily_pad kelp obsidian " + VANILLA_TREES,
    "age_1": "gunpowder arrow bow skeleton_skull",                       # mob_1: skeletons, creepers
    "age_2": "slime_ball phantom_membrane glowstone_dust",               # mob_2: slimes, phantoms, witches
    "age_3": "blaze_rod ghast_tear magma_cream nether_wart glowstone quartz netherrack soul_sand soul_soil basalt "
             "blackstone gold_nugget gilded_blackstone crimson_fungus warped_fungus crimson_stem warped_stem shroomlight "
             "ancient_debris magma_block crying_obsidian ender_pearl",  # mob_3 and the Beneath Nether
    "age_5": "wither_skeleton_skull",                                    # mob_5
    "age_6": "end_stone chorus_fruit chorus_flower shulker_shell purpur_block elytra dragon_head",  # the End (age_6 boss)
}
# What a team brings home without a recipe besides the world and the mob ladder: the drop of the TFCreate quartz vein
# (ore family, age_2) and the expedition items of the Age tokens (dev/decisions-while-away.md, 2026-10-14): the Frontier
# Shard of the Cold Forge chest (Dawn on, the Iron Age token is made from it), the Hollow Keystone of the Nether
# fortress chests and the flight recorder of the Mars temple (loot modifiers). No boss drop: the Wither, the Ender
# Dragon, the Wilden Chimera and the Chaos Guardian are optional, so every goal closes without them.
EXPEDITION_DROPS = {"dawn": ["firmages:frontier_shard"],
                    "age_2": ["tfcreate:unpolished_quartz"],
                    "age_3": ["firmages:hollow_keystone"],
                    "age_7": ["firmages:flight_recorder"]}
# The sky road (firmages-core SPEC 24.2): sky materials that the world hands out without a recipe. Starfall craters
# drop meteoric iron chunks from the Iron Age and starsteel chunks from the Electric Age; the Drifting Isles grow
# firmament crystal, reached by airship or jetpack in the Industrial Age. Storm glass, voidglass and the deep-space
# samples come from machines (MACHINE_MADE), aurora light is a fluid (fluids count as available).
SKY_WORLD = {"age_2": ["firmages:meteoric_iron_chunk"],
             "age_4": ["firmages:firmament_crystal"],
             "age_5": ["firmages:starsteel_chunk"]}
# What the rockets of the Space and Quantum Age reach without a recipe (Ad Astra planets: stone, sand, ore drops,
# Moon cheese) and the draconium ore of the End (pack pieces since 2026-10-15). Regexes over the registry, per Age.
LATE_WORLD = {"age_7": [r"^ad_astra:(moon|mars)_(stone|cobblestone|sand|deepslate)$", r"^ad_astra:(raw_desh|raw_ostrum|cheese)$",
                        r"^firmages:ore/rich_draconium$"],  # End draconium ore (kubejs/data/draconicevolution/loot_table)
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
    # the Lens Crown (firmages-core SPEC 27): a starforge recipe needs the Star Anvil and a Lens Mount
    "firmages:starforge": [["firmages:star_anvil"], ["firmages:lens_mount"]],
    # the Ore Processor (firmages-core SPEC 40), the 6x step of the Singularity Age
    "firmages:ore_processing": [["firmages:ore_processor"]],
})
# The Lens Crown (SPEC 27): the Star Anvil and a lens mount; station_groups adds the lenses of the recipe's chord.
STATION_ITEMS["firmages:starforge"] = [["firmages:star_anvil"], ["firmages:lens_mount"]]
LENSES = ["stone", "bronze", "iron", "arcane", "industrial", "electric", "information", "space", "quantum"]
CHORDS_JSON = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "data", "firmages", "firmages_crown",
                           "chords.json")


def chord_lenses(chord):
    """Lens items of a chord of the shipped library (firmages:<name>), [] for an unknown chord."""
    try:
        lib = json.load(open(CHORDS_JSON, encoding="utf-8"))
    except (OSError, ValueError):
        return []
    c = lib.get(str(chord).split(":")[-1], {})
    return [f"firmages:lens/{LENSES[i]}" for i in c.get("lenses", []) if 0 <= i < len(LENSES)]
FUSION_INJECTOR = {"draconium": "basic", "wyvern": "wyvern", "draconic": "awakened", "chaotic": "chaotic"}
# What a machine or multiblock makes without a recipe JSON: the fission reactor burns fissile fuel into nuclear waste,
# the SPS turns polonium into antimatter, the AE2 Matter Condenser compresses anything into matter balls and the
# singularity (age_6 token), Create Big Cannons casts and bores the four steel layers and stacks and heats them into the
# built-up steel chamber (block recipes; age_4 token), the Draconic reactor hands its converted fuel out as chaos fragments (DE ReactorMenu; age_9 token).
# Each needs all listed items or chemicals.
CBC_CHAMBER = ["tfc:metal/ingot/steel", "create:basin", "createbigcannons:very_small_cast_mould",
               "createbigcannons:small_cast_mould", "createbigcannons:medium_cast_mould", "createbigcannons:large_cast_mould",
               "createbigcannons:cannon_drill", "createbigcannons:cannon_builder", "create:encased_fan"]
LAUNCH_COMPLEX = ["firmages:launch_control", "firmages:probe_cradle", "firmages:launch_port", "firmages:probe"]
DE_REACTOR = ["draconicevolution:reactor_core", "draconicevolution:reactor_stabilizer", "draconicevolution:reactor_injector",
              "firmages:reactor_controller", "draconicevolution:awakened_draconium_block"]
MACHINE_MADE = {"ae2:matter_ball": ["ae2:condenser"], "ae2:singularity": ["ae2:condenser"],
                **{f"createbigcannons:{b}{size}_steel_cannon_layer": CBC_CHAMBER
                   for size in ("very_small", "small", "medium", "large") for b in ("unbored_", "")},
                "createbigcannons:built_up_steel_cannon_chamber": CBC_CHAMBER,
                "draconicevolution:large_chaos_frag": DE_REACTOR, "draconicevolution:medium_chaos_frag": DE_REACTOR,
                "draconicevolution:small_chaos_frag": DE_REACTOR,
                "mekanism:nuclear_waste": ["mekanism:fissile_fuel", "mekanismgenerators:fission_reactor_casing",
                                           "mekanismgenerators:fission_fuel_assembly", "mekanismgenerators:control_rod_assembly",
                                           "mekanismgenerators:fission_reactor_port"],
                "mekanism:antimatter": ["mekanism:polonium", "mekanism:sps_casing", "mekanism:sps_port",
                                        "mekanism:supercharged_coil"],
                # the sky road (SPEC 26.5, 31.4): the Storm Rod fuses sand into storm glass in rain; the Launch Complex
                # flies a probe with a sampler module (voidglass) or a deep-space module (samples and voidglass)
                "firmages:storm_glass": ["firmages:storm_rod"],
                "firmages:voidglass": LAUNCH_COMPLEX + ["firmages:sampler_module"],
                "firmages:deep_space_sample": LAUNCH_COMPLEX + ["firmages:deep_space_module"]}
# Vanilla station blocks without a recipe in a TFC world, and the recipes that only transform such a block itself.
STATION_VANILLA = {"minecraft:bucket", "minecraft:crafting_table", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                   "minecraft:campfire", "minecraft:anvil",
                   # content fixes after the late Ages: further vanilla blocks without a recipe in a TFC world
                   "minecraft:enchanting_table", "minecraft:barrel", "minecraft:trapped_chest", "minecraft:lectern",
                   "minecraft:fletching_table", "minecraft:soul_campfire", "minecraft:milk_bucket"}
STATION_TRANSFORMS = {"mekanism:sawing/crafting_table", "create:haunting/soul_campfire"}
# Create heat levels: a heated recipe needs a heater, a superheated one a Blaze Burner (with a blaze cake). The
# firmages-core gate locks them the same way (GateRules.HEAT_TYPES, HEAT_SOURCES).
HEAT_STATION = {"heated": ("tfcreate:primitive_heater", "create:blaze_burner"), "superheated": ("create:blaze_burner",)}
HEAT_TYPES = {"create:mixing", "create:compacting"}
# Recipe and item namespaces of the magic mods (C-3m, R-X).
MAGIC_NS = ("occultism:", "ars_nouveau:", "ars_additions:", "ars_creo:", "theurgy:", "occultengineering:")
REACH_GOALS = [  # (Age, goal recipe id, chain items that must be reachable too)
    ("age_2", "firmages:crafting/steel_heart", ["create:precision_mechanism", "create:mechanical_crafter", "create:deployer",
                                                 "create:mechanical_press", "tfcreate:primitive_heater", "tfc:metal/ingot/steel",
                                                 "firmages:frontier_compass", "firmages:frontier_relic"]),
    ("age_3", "firmages:sequenced_assembly/arcane_keystone", ["create:spout", "create:deployer", "create:mechanical_press",
                                                               "firmages:hollow_keystone", "minecraft:blaze_rod"]),  # tech
    ("age_3", "firmages:ritual/arcane_keystone", ARCANE_CHAIN),                                                    # magic
    ("age_4", "firmages:crafting/pressure_core", INDUSTRIAL_CHAIN),
    ("age_5", "firmages:arc_furnace/humming_core", ELECTRIC_CHAIN),
    ("age_6", "firmages:crafting/data_matrix", INFORMATION_CHAIN),
    ("age_7", "firmages:fusion/star_chart", SPACE_CHAIN),
    ("age_8", "firmages:fusion/quantum_core", QUANTUM_CHAIN),
    ("age_9", "firmages:fusion/ultimate_singularity", FINALE_CHAIN),
    ("age_9", "firmages:crafting/classic_stargate_base_block", STARGATE_CHAIN),
]

# Multiblock Age where it is later than the [mods] Age of the template namespace (dev/age_map.toml): the IE MV/HV
# machines of the Electric Age (Doc 10 v3 matrix 6.1; recipe types in kubejs/server_scripts/recipes/station_ages.js),
# the excavator with its bucket wheel (quest chapter Electric Age) and the IE power and HV structures built from
# age_5 parts (generator block, HV coils and capacitors, aluminium, resonanz engineering).
MULTIBLOCK_AGE = {f"immersiveengineering:{m}": "age_5" for m in (
    "arcfurnace", "sawmill", "mixer", "bottling_machine", "excavator", "excavator_full", "bucket_wheel",
    "diesel_generator", "lightning_rod", "radio_tower")}
# Multiblocks that stay unbuildable in every Age, with the reason. R-M checks that one of their blocks is still out of
# reach at age_9, so a later change that makes them buildable shows up. IE's chunk loader needs a calibrated sculk
# sensor (no deep dark in a TFC world); chunk loading is FTB Chunks' job (one station per function).
MULTIBLOCK_UNBUILT = {"immersiveengineering:chunk_loader": "chunk loading is FTB Chunks (force-load quota from age_4)"}

# Outputs of the cross-mod links (kubejs/server_scripts/recipes/age_N/links.js, package A9), by the Age they belong to.
LINK_REACH = [("age_2", ["tfc:metal/rod/steel", "tfc:metal/rod/wrought_iron", "create:belt_connector"]),
              ("age_3", ["occultism:book_of_binding_foliot", "occultism:book_of_binding_djinni"]),
              ("age_4", ["immersiveengineering:wirecoil_copper_ins", "immersiveengineering:conveyor_basic"]),
              ("age_5", ["immersiveengineering:generator", "createdieselgenerators:huge_diesel_engine"]),
              ("age_6", ["mekanism:steel_casing", "mekanism:basic_control_circuit", "mekanism:advanced_control_circuit",
                         "ae2:controller", "ae2:drive", "mekanism:hdpe_sheet"]),
              ("age_7", ["mekanism:digital_miner", "mekanismgenerators:fission_reactor_port",
                         "mekanismgenerators:turbine_casing", "draconicevolution:wyvern_crafting_injector",
                         "draconicevolution:wyvern_core"]),
              ("age_8", ["mekanismgenerators:fusion_reactor_frame", "mekanismgenerators:fusion_reactor_controller",
                         "draconicevolution:awakened_core"])]

# Items whose only recipe needed a dead part (Create sheets, barrel, a disabled rod, the brass nugget, the bronze nugget). Not here because
# the walk does not model their chain: the MA soul extractor (Mystical Agriculture ores) and the AdvancedAE strength
# card (quantum alloy from AE2 singularities); their recipes are checked by C-Di and C-Gv.
LEFTOVERS = [("age_1", ["create:brass_nugget"]),
             ("age_2", ["create_connected:control_chip", "createdeco:red_shipping_container", "createdeco:decal_fire",
                        "railways:track_monorail", "trainutilities:sound_unit"]),
             ("age_4", ["simulated:gyroscopic_mechanism", "simulated:engine_assembly", "create_factory_logistics:fluid_mechanism",
                        "aeronautics:gyroscopic_propeller_bearing", "simulated:red_portable_engine"]),
             ("age_5", ["create_hypertube:hypertube"]),
             ("age_6", ["mekanism:module_base"]),  # c:nuggets/bronze had no source; TFC bronze ingots (age_6/information_age.js)
             ("age_7", ["ad_astra:steel_plateblock", "ad_astra:iron_plateblock"]),
             ("age_8", ["ad_astra:calorite_plateblock"])]


def make_walk(full, recs, items, tags, item_age, amap, extra=None):
    """The recipe-graph walk of the R- checks: (age_ix, start, walk).

    start(n, without) is the start set of Age index n, walk(n, without, via) returns (have, live, sat) with every item
    reached at n. dev/derive_ages.py adds what the R- checks leave out through `extra`:
      "start":       function n -> further start items (loot, world generation, quest rewards); filtered like the rest
      "transforms":  [(n0, needs, outs)]: from Age index n0, once every item of `needs` is reached, each item of `outs`
                     that fits the Age is reached (block and entity loot, interactions); each output on its own
      "recipe_open": function (recipe id, n) -> False when a ProgressiveStages recipe lock keeps the recipe shut at n
      "stations":    {recipe type: [[items]]} like STATION_ITEMS for types the R- checks leave to the mod-Age model
      "world_ns_late": items of WORLD_NS that are NOT taken as given by the world (dev/derive_ages.py: made only by
                     recipes whose ingredients come later); they come from a recipe, "start" or "transforms"
    `via` (a dict) receives item -> recipe id or source of the step that first reached it."""
    extra = extra or {}
    world_late = extra.get("world_ns_late", set())
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

    def fits(i, n):
        return age_ix(i) <= n and item_age.get(i) != "disabled"

    def station_ix(rid):
        st = mod_age.get(full[rid].get("type", "minecraft:x").split(":")[0], "dawn")
        return AGES.index(st) if st in AGES else 0

    def station_groups(rid):
        t = full[rid].get("type", "")
        groups = list(STATION_ITEMS.get(t, extra.get("stations", {}).get(t, [])))
        if t == "draconicevolution:fusion_crafting":
            lvl = FUSION_INJECTOR.get(str(full[rid].get("techLevel", "draconium")).lower(), "basic")
            groups.append([f"draconicevolution:{lvl}_crafting_injector"])
        if t == "firmages:starforge":
            groups += [[lens] for lens in chord_lenses(full[rid].get("chord"))]
        return groups

    def start(n, without=(), via=None):
        src = {}
        for i in items:
            if i.split(":")[0] in WORLD_NS and i not in world_late:
                src[i] = "world namespace"
        for st, words in VANILLA_BY_AGE.items():
            if AGES.index(st) <= n:
                src.update({"minecraft:" + w: f"vanilla world {st}" for w in words.split()})
        for st, drops in EXPEDITION_DROPS.items():
            if AGES.index(st) <= n:
                src.update({d: f"expedition or vein drop {st}" for d in drops})
        for st, drops in SKY_WORLD.items():
            if AGES.index(st) <= n:
                src.update({d: f"sky road world source {st}" for d in drops})
        for st, pats in LATE_WORLD.items():
            if AGES.index(st) <= n:
                src.update({i: f"planet/End world {st}" for i in items if any(re.search(p_, i) for p_ in pats)})
        for st, got in ore_pieces.items():
            if AGES.index(st) <= n:
                src.update({i: f"ore vein {st}" for i in got})
        src.update({i: "mob drop (MOB_DROPS)" for i in MOB_DROPS})
        if "start" in extra:
            for i, why_ in extra["start"](n).items():
                src.setdefault(i, why_)
        have = {i for i in src if fits(i, n) and i not in without}
        if via is not None:
            for i in have:
                via.setdefault(i, src[i])
        return have

    def walk(n, without=(), via=None):
        have = start(n, without, via)
        heat = {rid: HEAT_STATION.get(full[rid].get("heat_requirement")) for rid, _, _ in recs}
        stations = {rid: station_groups(rid) for rid, _, _ in recs}
        is_open = extra.get("recipe_open", lambda rid, n_: True)
        # LATE_LOADED: the gate holds these pack recipes until a later Age than their outputs (FirmAges.requireAge);
        # release review 1.0.0: without it the walk made Mekanism crystals and shards from slurry and HCl at age_6
        late = {rid: max((AGES.index(st) for rx, st in LATE_LOADED if re.match(rx, rid)), default=0) for rid, _, _ in recs}
        live = [(rid, sl, set(os_)) for rid, sl, os_ in recs
                if station_ix(rid) <= n and late[rid] <= n and os_ and all(fits(o, n) for o in os_) and is_open(rid, n)]
        transforms = [(set(needs), [o for o in outs if fits(o, n) and o not in without], why_)
                      for n0, needs, outs, why_ in extra.get("transforms", ()) if n0 <= n]

        def sat(slot):
            return any((k in ("item", "chem") and x in have) or (k == "tag" and any(i in have for i in tags.get(x, [])))
                       for k, x in slot)

        def add(new, why_):
            have.update(new)
            if via is not None:
                for i in new:
                    via.setdefault(i, why_)
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
                add(new, rid)
                grew = True
            for chem, needs in MACHINE_MADE.items():
                if chem not in have and all(x in have for x in needs):
                    add({chem}, "machine " + "+".join(needs))
                    grew = True
            for seed, got in GROWN.items():
                new = {g for g in got if seed in have and GROWN_NEEDS.get(seed, seed) in have and g not in have and age_ix(g) <= n}
                if new:
                    add(new, "grown from " + seed)
                    grew = True
            for needs, outs, why_ in transforms:
                new = {o for o in outs if o not in have}
                if new and needs <= have:
                    add(new, why_)
                    grew = True
        return have, live, sat

    return age_ix, start, walk


def reach_checks(full, recs, items, tags, item_age, check, server_dir=None):
    try:
        import tomllib
        with open(os.path.join(REPO, "dev", "age_map.toml"), "rb") as f:
            amap = tomllib.load(f)
    except (ImportError, OSError):
        amap = {}
    age_ix, start, walk = make_walk(full, recs, items, tags, item_age, amap)

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
    # Vanilla's own recipes are locked anyway; a mod recipe under the minecraft: namespace (createdeco's shipping
    # containers) is judged by its output.
    blocked = sorted({f"{rid}: {v}" for rid, sl, os_ in recs
                      if not (rid.startswith("minecraft:") and all(o.startswith("minecraft:") for o in os_))
                      and rid not in STATION_TRANSFORMS
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

    # R-T: the Age tokens (dev/decisions-while-away.md, 2026-10-14). Every signature recipe reads firmages:age_token/<Age>;
    # each tag holds at least one member, and one of them is reachable at its Age from the start set, which holds no
    # boss drop (the known bosses are optional shortcuts).
    for st in AGES[AGES.index("age_2"):]:
        n = AGES.index(st)
        token = tags.get(f"firmages:age_token/{st}", [])
        have, live, sat = walk(n)
        got = [t for t in token if t in have]
        lines = [] if got or not token else why(token[0], have, live, sat, n)
        check(f"R-T {st}: the Age token tag holds a member reachable at {st} without a boss",
              bool(got), lines + [f"age_token/{st} {token}, reached {got}"])
    gone = sorted(t for t in tags if t.startswith("firmages:boss_token/"))
    check("R-T no firmages:boss_token tag is left", not gone, gone)

    # R-X: tech is required, magic a bonus (dev/decisions-while-away.md, 2026-10-15). The walk again without the
    # recipes of the magic mods (by recipe id or recipe type) and without the pack's rituals (firmages:ritual/*): every
    # Age token, every tech goal and its chain items outside the magic namespaces stay reachable at their Age.
    def magic(rid):
        return rid.startswith(MAGIC_NS) or full[rid].get("type", "").startswith(MAGIC_NS) or rid.startswith("firmages:ritual/")
    tech_recs = [r for r in recs if not magic(r[0])]
    _, _, tech_walk = make_walk(full, tech_recs, items, tags, item_age, amap)
    for st in AGES[AGES.index("age_2"):]:
        n = AGES.index(st)
        token = tags.get(f"firmages:age_token/{st}", [])
        have, live, sat = tech_walk(n)
        got = [t for t in token if t in have]
        lines = [] if got or not token else why(token[0], have, live, sat, n)
        goals = [(rid, chain) for st_, rid, chain in REACH_GOALS if st_ == st and rid in full and not magic(rid)]
        goals_ok = True
        for rid, chain in goals:
            j = full[rid]
            goal = _outs(j, [])
            miss = [s_ for s_ in _slots(j, []) if not sat(s_)]
            chain_bad = [c for c in chain if not c.startswith(MAGIC_NS) and c not in have]
            goal_ok = bool(goal) and all(g in have for g in goal)
            if goal_ok and not miss and not chain_bad:
                continue
            goals_ok = False
            lines.append(f"{rid}: goal reached {goal_ok}, unreached slots {[' | '.join(x for _, x in s_)[:80] for s_ in miss]}, "
                         f"chain items not reached {chain_bad}")
            for s_ in miss:
                lines += why(first_item(s_), have, live, sat, n)
            for c in chain_bad:
                lines += why(c, have, live, sat, n)
        check(f"R-X {st} without magic ({len(recs) - len(tech_recs)} magic recipes left out): the Age token and the tech goal "
              f"{', '.join(r for r, _ in goals) or '-'} with its chain are reachable at {st}",
              (bool(got) or not token) and goals_ok, lines + [f"age_token/{st} {token}, reached {got}"])

    # R-K: the outputs of the cross-mod links (LINK_REACH, package A9) are reachable at their Age: the tech parts by the
    # walk without magic, the magic ones by the full walk.
    for st, its in LINK_REACH:
        n = AGES.index(st)
        lines = []
        for magic_ok, w in ((False, tech_walk), (True, walk)):
            have, live, sat = w(n)
            miss = [i for i in its if i.startswith(MAGIC_NS) == magic_ok and i not in have]
            lines += [f"not reached ({'full' if magic_ok else 'tech'} walk): {miss}"] if miss else []
            lines += [l for i in miss for l in why(i, have, live, sat, n)]
        check(f"R-K {st}: {len(its)} link outputs reachable ({', '.join(i.split(':')[1] for i in its)})", not lines, lines)

    # R-M: every block of every IE-style multiblock template the server loads (IE, Immersive Petroleum, Advanced TFC
    # Tech, TFC + IE Crossover's arc furnace over IE's) is obtainable at the multiblock's Age, and none is disabled or
    # hidden. The pack copy under kubejs/data/<ns>/structure/multiblocks/ (dev/gen_multiblock_templates.py) replaces the
    # jar's template. A disabled block fails even where IE's matcher would take a tag member: IE puts the template block
    # back on teardown and drops it when that part is broken (world tests, poc-results "ATFC multiblocks", "IE templates").
    # Multiblock Age: [mods] Age of the template namespace, MULTIBLOCK_AGE for the Electric Age machines of IE.
    if server_dir:
        import tomllib
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        from derive_ages import _nbt
        from gen_multiblock_templates import effective_templates, pack_path
        with open(os.path.join(REPO, "dev", "age_map.toml"), "rb") as f:
            mb_mod_age = tomllib.load(f).get("mods", {})
        off = {i for i, st in item_age.items() if st == "disabled"} | set(tags.get("c:hidden_from_recipe_viewers", []))
        templates = effective_templates(os.path.join(server_dir, "mods"))
        by_age = collections.defaultdict(list)
        unbuilt = []
        for (ns, name), (jar, data, _) in sorted(templates.items()):
            if f"{ns}:{name}" in MULTIBLOCK_UNBUILT:
                unbuilt.append((ns, name, jar, data))
                continue
            st = MULTIBLOCK_AGE.get(f"{ns}:{name}", mb_mod_age.get(ns, "age_9"))
            by_age[st].append((ns, name, jar, data))
        for st in sorted(by_age, key=AGES.index):
            n = AGES.index(st)
            have, live, sat = walk(n)
            lines, bad, ok = [], [], True
            for ns, name, jar, data in by_age[st]:
                pack = pack_path(ns, name)
                if os.path.isfile(pack):
                    with open(pack, "rb") as f:
                        t = _nbt(f.read())
                    src = "pack copy"
                else:
                    t = _nbt(data)
                    src = jar.split("-")[0]
                blocks = sorted({p["Name"] for p in t["palette"]} - {"minecraft:air"})
                miss = [b for b in blocks if b not in have]
                dis = [b for b in blocks if b in off]
                ok = ok and not miss and not dis
                lines.append(f"{ns}:{name} ({src}): {len(blocks)} blocks, not obtainable {miss}, disabled or hidden {dis}")
                for b in miss:
                    bad += why(b, have, live, sat, n)
            check(f"R-M {st} multiblocks ({len(by_age[st])} templates): every template block is obtainable at {st}, none "
                  f"disabled or hidden", ok, lines + bad)
        if unbuilt or MULTIBLOCK_UNBUILT:
            have = walk(AGES.index("age_9"))[0]
            lines = []
            for ns, name, jar, data in unbuilt:
                out = sorted({p["Name"] for p in _nbt(data)["palette"]} - have - {"minecraft:air"})
                lines.append(f"{ns}:{name}: out of reach at age_9 {out} ({MULTIBLOCK_UNBUILT[f'{ns}:{name}']})")
            gone = sorted(set(MULTIBLOCK_UNBUILT) - {f"{ns}:{name}" for ns, name, _, _ in unbuilt})
            check(f"R-M unbuildable multiblocks ({len(MULTIBLOCK_UNBUILT)}): still unbuildable at age_9",
                  not gone and all(not l.split(" at age_9 ", 1)[1].startswith("[]") for l in lines),
                  lines + [f"template not found: {gone}"])
        if not templates:
            check("R-M multiblock templates", False, [f"no data/*/structure/multiblocks/*.nbt in {server_dir}/mods"])

    # R-L: items whose only recipe was broken by a disabled part (leftovers run, 2026-10-01) are reachable at their Age.
    for st, its in LEFTOVERS:
        n = AGES.index(st)
        have, live, sat = walk(n)
        miss = [i for i in its if i not in have]
        bad = [l for i in miss for l in why(i, have, live, sat, n)]
        check(f"R-L {st}: {len(its)} leftover items reachable ({', '.join(i.split(':')[1] for i in its)})", not miss,
              [f"not reached: {miss}"] + bad)


if __name__ == "__main__":
    main()
