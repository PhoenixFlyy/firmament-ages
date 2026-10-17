#!/usr/bin/env python3
"""Firmament Ages - the Dawn positive list #firmages:dawn_breakable (firmages-core SPEC 19.6, round G2).

Felix 2026-10-17: in Dawn a player breaks "no stone, really only grass and dirt and such blocks". firmages-core's
hand-breaking rule (SPEC 19.2, hardship.toolRuleUntilAge, Dawn only) stops every block outside this tag: no crack, no
progress, no text. Logs stay in #firmages:needs_tool_early, so a listed log still needs an axe.

The tag is written from the block registry of a dump (default: the newest dev/data/baseline-*/registries.json.gz) by
the rules in RULES, plus the vanilla block tags in TAGS and the blocks of [dawn_whitelist] in dev/age_map.toml.
On the list: TerraFirmaCraft soil (grass, dirt, rooted dirt, mud, duff, farmland, paths, sand, peat), gravel, snow,
plants and grasses (tfc:plant/*, Firmalife plants, wild crops, crops), coral, logs, wood, leaves, fallen leaves,
saplings and twigs of TFC and AFC (and every #minecraft:logs/leaves/saplings, so the vanilla groves of W-9 too),
loose rocks and groundcover (sticks, flint, bones), the blocks a Dawn player places (thatch, thatch bed, placed items,
log pile, firepit, scraping, rope), the vanilla counterparts (soil, small plants, mushrooms, hay) and fire.
Never on the list (FORBIDDEN, checked): rock other than gravel and loose rocks, ores, clay (Dawn withholds clay),
ice; no tag but the fixed TAGS.

Usage:
  python dev/gen_dawn_breakable.py [--registry FILE]      write kubejs/data/firmages/tags/block/dawn_breakable.json
  python dev/gen_dawn_breakable.py --check [--registry F]  the file is up to date and lists nothing forbidden
  python dev/gen_dawn_breakable.py --explain tfc:dirt/loam which rule lists a block (or that none does)
FILE is a registries.json(.gz) of fa_dump (<server>/local/firmages/registries.json) or a baseline.
Exit code: 0 = ok, 1 = --check found a difference or a forbidden entry, 2 = bad input.
"""
import argparse
import glob
import gzip
import json
import os
import re
import sys
import tomllib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "block", "dawn_breakable.json")
AGE_MAP = os.path.join(REPO, "dev", "age_map.toml")

# Vanilla block tags (each entry optional): trees of every mod, flowers, crops, snow, fire (a Dawn player can punch
# out a fire).
TAGS = ["#minecraft:logs", "#minecraft:leaves", "#minecraft:saplings", "#minecraft:flowers", "#minecraft:crops",
        "#minecraft:snow", "#minecraft:fire"]

VANILLA = ("grass_block dirt coarse_dirt rooted_dirt podzol mycelium mud muddy_mangrove_roots mangrove_roots moss_block "
           "moss_carpet dirt_path farmland sand red_sand gravel short_grass tall_grass fern large_fern dead_bush vine "
           "glow_lichen hanging_roots sugar_cane bamboo bamboo_sapling cactus sweet_berry_bush cave_vines "
           "cave_vines_plant lily_pad seagrass tall_seagrass kelp kelp_plant azalea flowering_azalea big_dripleaf "
           "big_dripleaf_stem small_dripleaf pitcher_plant brown_mushroom red_mushroom brown_mushroom_block "
           "red_mushroom_block mushroom_stem cocoa melon pumpkin hay_block").split()

# (label, regex over the block id). Order is only for --explain.
RULES = [
    ("TFC soil", r"^tfc:(grass|dirt|coarse_dirt|rooted_dirt|mud|muddy_roots|duff|farmland|grass_path|sand)/"),
    ("TFC peat", r"^tfc:(peat|peat_grass)$"),
    ("TFC gravel", r"^tfc:rock/gravel/"),
    ("TFC loose rocks", r"^tfc:rock/(loose|mossy_loose)/"),
    ("TFC groundcover (sticks, flint, bones, feathers)", r"^tfc:groundcover/"),
    ("TFC snow", r"^tfc:snow_pile$"),
    ("plants (TFC, Firmalife; not potted)", r"^(tfc|firmalife):plant/(?!potted/)"),
    ("wild crops and crops", r"^[a-z0-9_]+:(wild_crop|crop|dead_crop)/"),
    ("TFC coral and sea pickle", r"^tfc:(coral/|sea_pickle$)"),
    ("TFC melons and pumpkins", r"^tfc:(rotten_)?(melon|pumpkin)$"),
    ("TFC and AFC trees (logs and wood need an axe)",
     r"^(tfc|afc):wood/(log|wood|stripped_log|stripped_wood|leaves|fallen_leaves|sapling|twig)/"),
    ("TFC tree roots", r"^tfc:tree_roots$"),
    ("blocks a Dawn player places", r"^tfc:(thatch|thatch_bed|placed_item|log_pile|firepit|scraping|rope|hanging_rope)$"),
    ("vanilla soil, plants, mushrooms and hay", r"^minecraft:(" + "|".join(VANILLA) + r")$"),
]
RULES_RX = [(label, re.compile(rx)) for label, rx in RULES]

# Felix: no stone, no ore; Dawn withholds clay (claystone is a rock type, its gravel and loose rocks stay); ice is not soft.
FORBIDDEN = re.compile(r"^tfc:rock/(?!gravel/|loose/|mossy_loose/)|^tfc:ore/|clay(?!stone)|kaolin|_ore$|:ore/|deepslate|"
                       r"^minecraft:(.*stone.*|.*terracotta|.*ice|calcite|tuff|dripstone_block|obsidian)$|^tfc:(sea_ice|ice_pile|icicle|calcite)$")


def default_registry():
    found = sorted(glob.glob(os.path.join(REPO, "dev", "data", "baseline-*", "registries.json.gz")),
                   key=lambda p: [int(x) for x in re.findall(r"\d+", os.path.basename(os.path.dirname(p)))])
    return found[-1] if found else None


def load_blocks(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        data = json.load(f)
    blocks = data.get("block") if isinstance(data, dict) else None
    if not isinstance(blocks, list) or not blocks:
        raise ValueError(f"{path}: no 'block' registry list")
    return set(blocks)


def whitelist_blocks(blocks):
    """The blocks of [dawn_whitelist] singles (tfc:<name>), e.g. thatch and rope."""
    with open(AGE_MAP, "rb") as f:
        singles = tomllib.load(f).get("dawn_whitelist", {}).get("singles", [])
    return {f"tfc:{s}" for s in singles if f"tfc:{s}" in blocks}


def rule_of(block):
    for label, rx in RULES_RX:
        if rx.search(block):
            return label
    return None


def generate(blocks):
    ids = {b for b in blocks if rule_of(b)} | whitelist_blocks(blocks)
    return TAGS + sorted(ids)


def render(values):
    lines = ['{', '  "replace": false,', '  "values": [']
    lines += [f'    {{"id": "{v}", "required": false}}' + ("," if i < len(values) - 1 else "") for i, v in enumerate(values)]
    lines += ['  ]', '}']
    return "\n".join(lines) + "\n"


def forbidden(values):
    bad = [v for v in values if not v.startswith("#") and FORBIDDEN.search(v)]
    bad += [v for v in values if v.startswith("#") and v not in TAGS]
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--registry", help="registries.json(.gz) of a dump (default: newest dev/data/baseline-*)")
    ap.add_argument("--check", action="store_true", help="compare with the file, do not write")
    ap.add_argument("--explain", metavar="ID", help="which rule lists a block")
    a = ap.parse_args()
    reg = a.registry or default_registry()
    if not reg or not os.path.isfile(reg):
        print(f"no registry dump: {reg}", file=sys.stderr)
        return 2
    try:
        blocks = load_blocks(reg)
    except (OSError, ValueError) as e:
        print(e, file=sys.stderr)
        return 2
    if a.explain:
        b = a.explain
        label = rule_of(b) or ("[dawn_whitelist]" if b in whitelist_blocks(blocks) else None)
        print(f"{b}: {'registered' if b in blocks else 'NOT registered'}; "
              f"{'listed by rule: ' + label if label else 'not listed (it does not break in Dawn)'}"
              f"{'; FORBIDDEN' if FORBIDDEN.search(b) else ''}")
        return 0
    values = generate(blocks)
    bad = forbidden(values)
    text = render(values)
    counts = {}
    for v in values:
        label = "tags" if v.startswith("#") else (rule_of(v) or "[dawn_whitelist]")
        counts[label] = counts.get(label, 0) + 1
    rel = os.path.relpath(OUT, REPO).replace(os.sep, "/")
    print(f"{rel}: {len(values)} entries from {os.path.relpath(reg, REPO).replace(os.sep, '/')} ({len(blocks)} blocks)")
    for label, n in counts.items():
        print(f"  {n:5d}  {label}")
    if bad:
        print(f"FORBIDDEN entries ({len(bad)}): {bad[:20]}")
        return 1
    if a.check:
        have = open(OUT, encoding="utf-8").read() if os.path.isfile(OUT) else None
        if have != text:
            print(f"{rel} is out of date: run python dev/gen_dawn_breakable.py")
            return 1
        print("up to date")
        return 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
