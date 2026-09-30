#!/usr/bin/env python3
"""Check recipe gating and unification from the /fa_dump files (kubejs/server_scripts/debug/dump.js).

Reads <server>/local/firmages/recipes.json and item_tags.json and prints one PASS/FAIL line per check,
followed by the evidence. Run "fa_dump" on the server first (console or: python dev/rcon.py fa_dump).
The content checks of the Arcane and Industrial Age ("C-" lines) also need recipes_full.json from "fa_dump_full"
(every recipe as its serializer JSON) and read <server>/logs/latest.log for recipe parse errors.

Usage:  python dev/poc_analyze.py [--server-dir test-server] [--show 40]
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
    ap.add_argument("--show", type=int, default=40, help="max evidence lines per check")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    base = os.path.join(a.server_dir, "local", "firmages")
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
                               "output_item", "result_item")
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
    def outs_with_tags(j):
        found = set(_outs(j, []))
        res = j.get("result")
        if isinstance(res, dict) and isinstance(res.get("tag"), str) and j.get("type") == "occultism:miner":
            found |= set(tags.get(res["tag"], [])[:1])
        return found
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
            return any((k == "item" and x in have) or (k == "tag" and any(i in have for i in tags.get(x, []))) for k, x in slot)
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
    gear = set()
    for k in ("c:tools", "c:armors", "minecraft:enchantable/durability", "minecraft:enchantable/armor"):
        gear |= set(tags.get(k, []))
    chains = []
    for rid, sl, os_ in recs:
        outs4 = [o for o in os_ if o in gear and item_age.get(o) == "age_4"]
        if not outs4 or rid.startswith("cataclysm:weapon_infusion/"):
            continue
        for s in sl:
            alts = members(s)
            if alts and alts <= gear:
                chains.append(f"{rid} -> {outs4[0]} eats {sorted(alts)[:2]}")
    check("C-4j gear rule for age_4 gear: no tool or armour as ingredient (Cataclysm boss-weapon fusion is an open point)",
          not chains, chains)

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


if __name__ == "__main__":
    main()
