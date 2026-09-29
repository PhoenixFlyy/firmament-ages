#!/usr/bin/env python
"""Generate the TFC-derived lock lists inside the ProgressiveStages stage files.

ProgressiveStages 3.0.5 only accepts EXACT block ids in [[ores.overrides]] (StageFileParser.parseOreOverrides
rejects tags and mod: selectors; tag targets arrive with [[blocks.overrides]] in the unreleased 3.1.0).
TFC 4.2.11 has 21 rocks x 53 ore kinds = 1113 ore blocks, so the override rows are generated here.

The script rewrites only the text between marker comments in config/progressivestages/stages/*.toml:
    # >>> generated:items >>>          ... # <<< generated:items <<<          (inside [items].locked)
    # >>> generated:dawn_crafting >>>  ... # <<< generated:dawn_crafting <<<  (inside [recipes].locked_ids)
    # >>> generated:ore_overrides >>>  ... # <<< generated:ore_overrides <<<  (end of file)

Input: dev/data/tfc-4.2.11-ids.json (ids derived from the TFC git tree, tag v4.2.11).
Usage:  python dev/gen_stage_locks.py          rewrite the stage files
        python dev/gen_stage_locks.py --check  exit 1 if a stage file is out of date
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STAGES = ROOT / "config" / "progressivestages" / "stages"
DATA = json.loads((ROOT / "dev" / "data" / "tfc-4.2.11-ids.json").read_text(encoding="utf-8"))

# ---------------------------------------------------------------- design tables (Doc 08 v3 section 3.2, 4.1)
# Stage in which an ore becomes visible/usable. Copper ores are visible from Dawn (no override) but their
# ore pieces are items made of metal and therefore locked until age_0 ("nothing metal in Dawn", Doc 08 9.2).
ORE_AGE = {
    "native_copper": "age_0", "malachite": "age_0", "tetrahedrite": "age_0",
    "cassiterite": "age_1", "bismuthinite": "age_1", "sphalerite": "age_1", "native_silver": "age_1",
    "native_gold": "age_1", "graphite": "age_1", "borax": "age_1", "halite": "age_1", "gypsum": "age_1",
    "saltpeter": "age_1", "sylvite": "age_1",
    "hematite": "age_2", "limonite": "age_2", "magnetite": "age_2", "bituminous_coal": "age_2",
    "lignite": "age_2", "cinnabar": "age_2", "cryolite": "age_2", "sulfur": "age_2",
    "lapis_lazuli": "age_3", "amethyst": "age_3", "opal": "age_3", "pyrite": "age_3", "ruby": "age_3",
    "sapphire": "age_3", "topaz": "age_3", "emerald": "age_3",
    "garnierite": "age_4", "diamond": "age_4",
}
# Visible from the start: no block override needed for these stages.
NO_SPOOF = {"dawn", "age_0"}

METAL_AGE = {
    "copper": "age_0",
    "bismuth": "age_1", "bismuth_bronze": "age_1", "black_bronze": "age_1", "bronze": "age_1", "brass": "age_1",
    "rose_gold": "age_1", "sterling_silver": "age_1", "tin": "age_1", "zinc": "age_1", "gold": "age_1",
    "silver": "age_1",
    "wrought_iron": "age_2", "cast_iron": "age_2", "pig_iron": "age_2", "steel": "age_2",
    "high_carbon_steel": "age_2", "weak_steel": "age_2",
    "nickel": "age_4", "black_steel": "age_4", "high_carbon_black_steel": "age_4",
    "red_steel": "age_5", "weak_red_steel": "age_5", "high_carbon_red_steel": "age_5",
    "blue_steel": "age_6", "weak_blue_steel": "age_6", "high_carbon_blue_steel": "age_6",
    # "unknown" (failed alloy) is never locked.
}
# Non-ore powders: flux and salt belong to the Bronze Age, the rest stays free.
POWDER_AGE = {"flux": "age_1", "salt": "age_1"}

# Dawn crafting whitelist (Doc 08 section 9.2: "TFC workbench recipes except shafting and string").
DAWN_GROUP_WHITELIST = {"stone"}  # tfc:crafting/stone/<tool>/<rock category> = stone tool assembly
DAWN_SINGLE_WHITELIST = {
    "firestarter", "thatch", "straw", "straw_from_alfalfa", "straw_from_canola", "straw_from_hay",
    "stick_bunch", "stick_bundle", "stick_from_bunch", "stick_from_bundle", "stick_from_tfc_bamboo",
    "stick_from_twigs", "rope",
    "obsidian_axe", "obsidian_hammer", "obsidian_hoe", "obsidian_javelin", "obsidian_knife",
    "obsidian_macuahuitl", "obsidian_shovel",
}

# POC: surface small ores have no rock; one neutral loose rock is used for all of them.
SMALL_ORE_DISPLAY = "tfc:rock/loose/granite"

# ---------------------------------------------------------------- derivation
GRADES = ("poor_", "normal_", "rich_")


def base_ore(kind: str) -> str:
    for g in GRADES:
        if kind.startswith(g):
            return kind[len(g):]
    if kind.startswith("small_"):
        return kind[len("small_"):]
    return kind


def build():
    items = {}      # stage -> list of item ids
    overrides = {}  # stage -> list of (target, display, drop)
    add_item = lambda st, i: items.setdefault(st, []).append(i)
    add_ovr = lambda st, row: overrides.setdefault(st, []).append(row)

    for m, ids in DATA["metal_items"].items():
        st = METAL_AGE.get(m)
        if st:
            for i in ids:
                add_item(st, i)

    for i in DATA["ore_items"]:
        st = ORE_AGE.get(base_ore(i.split("/", 1)[1]))
        if st:
            add_item(st, i)
    for i in DATA["gem_items"]:
        st = ORE_AGE.get(i.split("/", 1)[1])
        if st:
            add_item(st, i)
    for i in DATA["powder_items"]:
        name = i.split("/", 1)[1]
        st = ORE_AGE.get(name) or POWDER_AGE.get(name)
        if st:
            add_item(st, i)

    for kind in DATA["ore_block_kinds"]:
        st = ORE_AGE.get(base_ore(kind))
        if not st or st in NO_SPOOF:
            continue
        for rock in DATA["rocks"]:
            add_ovr(st, (f"tfc:ore/{kind}/{rock}", f"tfc:rock/raw/{rock}", f"tfc:rock/loose/{rock}"))
    for ore in DATA["deposit_ores"]:
        st = ORE_AGE.get(ore)
        if not st or st in NO_SPOOF:
            continue
        for rock in DATA["rocks"]:
            add_ovr(st, (f"tfc:deposit/{ore}/{rock}", f"tfc:rock/gravel/{rock}", f"tfc:rock/gravel/{rock}"))
    for small in DATA["small_ore_blocks"]:
        st = ORE_AGE.get(base_ore(small))
        if not st or st in NO_SPOOF:
            continue
        add_ovr(st, (f"tfc:ore/{small}", SMALL_ORE_DISPLAY, SMALL_ORE_DISPLAY))

    dawn = [f"name:tfc:crafting/{g}/" for g in sorted(DATA["crafting_recipe_groups"]) if g not in DAWN_GROUP_WHITELIST]
    dawn += [f"id:tfc:crafting/{s}" for s in DATA["crafting_recipe_singles"] if s not in DAWN_SINGLE_WHITELIST]
    return items, overrides, dawn


# ---------------------------------------------------------------- rendering
def render_list(entries, indent="    "):
    return "".join(f'{indent}"{e}",\n' for e in entries)


def render_overrides(rows):
    if not rows:
        return "# (no TFC ore becomes visible in this stage)\n"
    out = [f"# {len(rows)} rows: TFC ore/deposit blocks stay disguised as rock until this stage is owned.\n"]
    for t, d, r in rows:
        out.append(f'[[ores.overrides]]\ntarget = "id:{t}"\ndisplay_as = "id:{d}"\ndrop_as = "id:{r}"\n')
    return "\n".join(out)


def replace_block(text, name, body, path):
    pat = re.compile(r"(?P<head>[ \t]*# >>> generated:%s >>>\n)(?P<body>.*?)(?P<tail>[ \t]*# <<< generated:%s <<<)" % (name, name), re.S)
    m = pat.search(text)
    if not m:
        return text, False
    return text[: m.start("body")] + body + text[m.end("body"):], True


def main():
    check = "--check" in sys.argv
    items, overrides, dawn = build()
    dirty = []
    for path in sorted(STAGES.glob("*.toml")):
        stage = path.stem
        text = path.read_text(encoding="utf-8")
        new = text
        new, _ = replace_block(new, "items", render_list(sorted(set(f"id:{i}" for i in items.get(stage, [])))), path)
        new, _ = replace_block(new, "dawn_crafting", render_list(dawn) if stage == "age_0" else "", path)
        new, _ = replace_block(new, "ore_overrides", render_overrides(overrides.get(stage, [])), path)
        if new != text:
            dirty.append(path.name)
            if not check:
                path.write_text(new, encoding="utf-8", newline="\n")
    summary = {st: (len(set(items.get(st, []))), len(overrides.get(st, []))) for st in sorted(set(items) | set(overrides))}
    print("items/overrides per stage:", summary, "| dawn recipe selectors:", len(dawn))
    if check and dirty:
        print("out of date:", ", ".join(dirty))
        sys.exit(1)
    print(("checked" if check else "rewrote"), len(dirty), "file(s)")


if __name__ == "__main__":
    main()
