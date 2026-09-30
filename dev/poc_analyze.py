#!/usr/bin/env python3
"""Check recipe gating and unification from the /fa_dump files (kubejs/server_scripts/debug/dump.js).

Reads <server>/local/firmages/recipes.json and item_tags.json and prints one PASS/FAIL line per check,
followed by the evidence. Run "fa_dump" on the server first (console or: python dev/rcon.py fa_dump).

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

    print(f"\n{len(failed)} FAIL: {failed}" if failed else "\nall checks PASS")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
