#!/usr/bin/env python
"""Generate the Age registry tags and the ProgressiveStages locks from ONE source, dev/age_map.toml.

Inputs
  dev/age_map.toml               Age per mod namespace, material words, ore families, TFC tables, explicit rules
  dev/data/registry.json         snapshot of every item id, the ore/rock block ids, the item tags the rules use
                                 and the grid-recipe namespaces (refresh with --registry, see below)
  dev/data/tfc-4.2.11-ids.json   TFC ids by kind (rocks, ore kinds, metal items; from the TFC git tree v4.2.11)
  config/progressivestages/stages/disabled.toml   items locked forever (they go to age_items/disabled)

Outputs
  kubejs/data/firmages/tags/item/age_items/<stage>.json    every item in exactly one tag (dawn, age_0..age_9, disabled)
  kubejs/data/firmages/tags/block/age_blocks/<stage>.json  ore and deposit blocks per Age (all grades and rocks)
  the text between marker comments in config/progressivestages/stages/<stage>.toml:
    # >>> generated:items >>>          "tag:firmages:age_items/<stage>"            (inside [items].locked)
    # >>> generated:recipes >>>        "mod:<ns>" grid-recipe locks                 (inside [recipes].locked_ids)
    # >>> generated:dawn_crafting >>>  TFC grid recipes outside the Dawn whitelist  (age_0 [recipes].locked_ids)
    # >>> generated:ore_overrides >>>  [[ores.overrides]] rows                      (end of file)

ProgressiveStages 3.0.5 takes tag selectors for item locks (one hash lookup per stage) but only EXACT block ids
in [[ores.overrides]] (StageFileParser.parseOreOverrides rejects tags), so the ore rows are listed one by one.
Its lock lookup scans every selector of a category linearly, which is why items are locked by one tag per stage
instead of thousands of id: entries.

Usage
  python dev/gen_stage_locks.py                      rewrite tags and stage files
  python dev/gen_stage_locks.py --check              exit 1 if anything is out of date
  python dev/gen_stage_locks.py --registry test-server/local/firmages
                                                     refresh dev/data/registry.json from a /fa_dump, then rewrite
  python dev/gen_stage_locks.py --explain <item id>  print the rule that decides the Age of one item
Exit code 2: a namespace in the registry has no [mods] entry, or a rule names an unknown stage.
"""
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STAGES_DIR = ROOT / "config" / "progressivestages" / "stages"
AGE_MAP = ROOT / "dev" / "age_map.toml"
REGISTRY = ROOT / "dev" / "data" / "registry.json"
TFC_IDS = ROOT / "dev" / "data" / "tfc-4.2.11-ids.json"
TAG_DIR = ROOT / "kubejs" / "data" / "firmages" / "tags"

STAGES = ["dawn"] + [f"age_{i}" for i in range(10)]
RANK = {s: i for i, s in enumerate(STAGES)}
GRADES = ("poor_", "normal_", "rich_")
BLOCK_KEEP = re.compile(r"[:/](ore|deposit)/|^tfc:rock/(raw|loose|gravel)/|^tfcreate:quartz_")


def die(msg):
    print("ERROR:", msg)
    sys.exit(2)


def later(a, b):
    return a if RANK[a] >= RANK[b] else b


# ---------------------------------------------------------------- selectors
def selector_matches(sel, item, tags):
    kind, _, value = sel.partition(":")
    if kind == "id":
        return item == value
    if kind == "mod":
        return item.split(":", 1)[0] == value
    if kind == "name":
        return value in item
    if kind == "tag":
        return item in tags.get(value, ())
    die(f"unknown selector {sel!r}")


def selector_tags(selectors):
    return sorted({s[4:] for s in selectors if s.startswith("tag:")})


def disabled_selectors():
    data = tomllib.loads((STAGES_DIR / "disabled.toml").read_text(encoding="utf-8"))
    return list(data.get("items", {}).get("locked", []))


# ---------------------------------------------------------------- registry snapshot
def refresh_registry(dump_dir, amap):
    dump_dir = Path(dump_dir)
    reg = json.loads((dump_dir / "registries.json").read_text(encoding="utf-8"))
    item_tags = json.loads((dump_dir / "item_tags.json").read_text(encoding="utf-8"))
    recipes = json.loads((dump_dir / "recipes.json").read_text(encoding="utf-8"))
    col = recipes["columns"].index("id"), recipes["columns"].index("type")
    grid_ns = sorted({r[col[0]].split(":", 1)[0] for r in recipes["recipes"] if r[col[1]] == "minecraft:crafting"})
    wanted = selector_tags(list(amap.get("items", {})) + disabled_selectors())
    snap = {
        "source": f"/fa_dump in {dump_dir.as_posix()} (kubejs/server_scripts/debug/dump.js)",
        "items": sorted(i for i in reg["item"] if i != "minecraft:air"),
        "blocks": sorted(b for b in reg["block"] if BLOCK_KEEP.search(b)),
        "item_tags": {t: sorted(item_tags.get(t, [])) for t in wanted},
        "grid_recipe_namespaces": grid_ns,
    }
    missing = [t for t in wanted if t not in item_tags]
    if missing:
        die(f"item tags used by rules are not in the dump: {missing}")
    REGISTRY.write_text(json.dumps(snap, indent=0) + "\n", encoding="utf-8", newline="\n")
    print(f"registry snapshot: {len(snap['items'])} items, {len(snap['blocks'])} ore/rock blocks, "
          f"{len(grid_ns)} grid-recipe namespaces -> {REGISTRY.relative_to(ROOT).as_posix()}")


# ---------------------------------------------------------------- derivation
def base_ore(kind):
    for g in GRADES:
        if kind.startswith(g):
            return kind[len(g):]
    return kind[len("small_"):] if kind.startswith("small_") else kind


def check_stages(table, name):
    bad = {k: v for k, v in table.items() if v not in RANK and v != "generated"}
    if bad:
        die(f"[{name}] names unknown stages: {bad}")


class AgeMap:
    def __init__(self, amap, reg, tfc):
        self.amap, self.reg, self.tfc = amap, reg, tfc
        for key in ("mods", "recipes", "materials", "ore_families", "tfc_ores", "tfc_metals", "tfc_powders", "items"):
            check_stages(amap.get(key, {}), key)
        self.mods = amap["mods"]
        self.materials = amap["materials"]
        self.mod_materials = amap.get("mod_materials", {})
        for ns, words in self.mod_materials.items():
            check_stages(words, f"mod_materials.{ns}")
        self.items_set = set(reg["items"])
        self.blocks_set = set(reg["blocks"])
        self.tags = reg["item_tags"]
        rules = amap.get("items", {})
        self.id_rules = {k[3:]: v for k, v in rules.items() if k.startswith("id:")}
        self.other_rules = [(k, v) for k, v in rules.items() if not k.startswith("id:")]
        self.disabled = disabled_selectors()
        self.table_items, self.blocks, self.overrides = self._tables()

    # TFC tables and ore families: item id -> Age, block id -> Age, override rows per stage.
    def _tables(self):
        a, tfc = self.amap, self.tfc
        ore_age, visible = a["tfc_ores"], set(a["tfc_ores_visible"]["stages"])
        small_display = a["small_ore_display"]["block"]
        items, blocks, overrides = {}, {}, {}

        def ovr(stage, row):
            if stage not in visible:
                overrides.setdefault(stage, []).append(row)

        for metal, ids in tfc["metal_items"].items():
            if metal in a["tfc_metals"]:
                for i in ids:
                    items[i] = a["tfc_metals"][metal]
        for i in tfc["ore_items"]:
            st = ore_age.get(base_ore(i.split("/", 1)[1]))
            if st:
                items[i] = st
        for i in tfc["gem_items"]:
            st = ore_age.get(i.split("/", 1)[1])
            if st:
                items[i] = st
        for i in tfc["powder_items"]:
            name = i.split("/", 1)[1]
            st = ore_age.get(name) or a["tfc_powders"].get(name)
            if st:
                items[i] = st

        rocks = tfc["rocks"]
        for kind in tfc["ore_block_kinds"]:
            st = ore_age.get(base_ore(kind))
            if st:
                for rock in rocks:
                    b = f"tfc:ore/{kind}/{rock}"
                    blocks[b] = st
                    ovr(st, (b, f"tfc:rock/raw/{rock}", f"tfc:rock/loose/{rock}"))
        for ore in tfc["deposit_ores"]:
            st = ore_age.get(ore)
            if st:
                for rock in rocks:
                    b = f"tfc:deposit/{ore}/{rock}"
                    blocks[b] = st
                    ovr(st, (b, f"tfc:rock/gravel/{rock}", f"tfc:rock/gravel/{rock}"))
        for small in tfc["small_ore_blocks"]:
            st = ore_age.get(base_ore(small))
            if st:
                blocks[f"tfc:ore/{small}"] = st
                ovr(st, (f"tfc:ore/{small}", small_display, small_display))

        for fam, st in a["ore_families"].items():
            ns, ore = fam.split(":", 1)
            found = 0
            if "{rock}" in ore:  # single-grade block per rock
                for rock in rocks:
                    b = f"{ns}:{ore.replace('{rock}', rock)}"
                    if b in self.blocks_set:
                        blocks[b] = st
                        found += 1
                        ovr(st, (b, f"tfc:rock/raw/{rock}", f"tfc:rock/loose/{rock}"))
                if not found:
                    die(f"[ore_families] {fam}: no ore blocks in the registry snapshot")
                continue
            for g in GRADES:
                for rock in rocks:
                    b = f"{ns}:ore/{g}{ore}/{rock}"
                    if b in self.blocks_set:
                        blocks[b] = st
                        found += 1
                        ovr(st, (b, f"tfc:rock/raw/{rock}", f"tfc:rock/loose/{rock}"))
                    if b in self.items_set:
                        items[b] = st
                piece = f"{ns}:ore/{g}{ore}"
                if piece in self.items_set:
                    items[piece] = st
            small = f"{ns}:ore/small_{ore}"
            if small in self.blocks_set:
                blocks[small] = st
                found += 1
                ovr(st, (small, small_display, small_display))
            for extra in (small, f"{ns}:powder/{ore}"):
                if extra in self.items_set:
                    items[extra] = st
            if not found:
                die(f"[ore_families] {fam}: no ore blocks in the registry snapshot")

        # The item form of an ore or deposit block follows the block (silk touch, creative, loot).
        for b, st in blocks.items():
            if b in self.items_set:
                items.setdefault(b, st)
        for rows in overrides.values():
            for _, d, r in rows:
                if d not in self.blocks_set or r not in self.blocks_set:
                    die(f"override display/drop block missing from the registry: {d} / {r}")
        return items, blocks, overrides

    def material_age(self, ns, path):
        words = re.split(r"[/_]", path)
        tables = (self.materials, self.mod_materials.get(ns, {}))
        best, hit = None, []
        for n in (3, 2, 1):
            for i in range(len(words) - n + 1):
                w = "_".join(words[i:i + n])
                for table in tables:
                    if w in table:
                        hit.append(w)
                        best = table[w] if best is None else later(best, table[w])
        return best, hit

    def age_of(self, item):
        """(stage, reason) of one item."""
        for sel in self.disabled:
            if selector_matches(sel, item, self.tags):
                return "disabled", f"disabled.toml {sel}"
        if item in self.id_rules:
            return self.id_rules[item], f"[items] id:{item}"
        for sel, st in self.other_rules:
            if selector_matches(sel, item, self.tags):
                return st, f"[items] {sel}"
        if item in self.table_items:
            return self.table_items[item], "TFC table / [ore_families]"
        ns, path = item.split(":", 1)
        base = self.mods[ns]
        mat, words = self.material_age(ns, path)
        if mat and RANK[mat] > RANK[base]:
            return mat, f"[materials] {'+'.join(words)} (mod {ns} = {base})"
        return base, f"[mods] {ns}"

    def build(self):
        unmapped = sorted({i.split(":", 1)[0] for i in self.reg["items"]} - set(self.mods))
        unmapped_r = sorted(set(self.reg["grid_recipe_namespaces"]) - set(self.mods) - set(self.amap.get("recipes", {})))
        if unmapped or unmapped_r:
            die(f"namespaces without a [mods] entry: {sorted(set(unmapped) | set(unmapped_r))}")
        tags = {s: [] for s in STAGES + ["disabled"]}
        for item in self.reg["items"]:
            tags[self.age_of(item)[0]].append(item)
        block_tags = {s: [] for s in STAGES}
        for b, st in self.blocks.items():
            if b not in self.blocks_set:
                die(f"TFC table block missing from the registry snapshot: {b}")
            block_tags[st].append(b)
        recipe_locks = {}
        for ns in self.reg["grid_recipe_namespaces"]:
            st = self.amap.get("recipes", {}).get(ns) or later(self.mods[ns], "age_0")
            if st != "generated":
                recipe_locks.setdefault(st, []).append(f"mod:{ns}")
        wl = self.amap["dawn_whitelist"]
        dawn = [f"name:tfc:crafting/{g}/" for g in sorted(self.tfc["crafting_recipe_groups"]) if g not in wl["groups"]]
        dawn += [f"id:tfc:crafting/{s}" for s in self.tfc["crafting_recipe_singles"] if s not in wl["singles"]]
        return tags, block_tags, recipe_locks, dawn


# ---------------------------------------------------------------- rendering
def render_list(entries, indent="    "):
    return "".join(f'{indent}"{e}",\n' for e in entries)


def render_overrides(rows):
    if not rows:
        return "# (no ore becomes visible in this stage)\n"
    out = [f"# {len(rows)} rows: ore and deposit blocks stay disguised as rock until this stage is owned.\n"]
    for t, d, r in rows:
        out.append(f'[[ores.overrides]]\ntarget = "id:{t}"\ndisplay_as = "id:{d}"\ndrop_as = "id:{r}"\n')
    return "\n".join(out)


def render_tag(values):
    body = ",\n".join(f'    {{"id": "{v}", "required": false}}' for v in sorted(values))
    return '{\n  "replace": false,\n  "values": [\n' + body + ("\n" if body else "") + "  ]\n}\n"


def replace_block(text, name, body):
    pat = re.compile(r"(?P<head>[ \t]*# >>> generated:%s >>>\n)(?P<body>.*?)(?P<tail>[ \t]*# <<< generated:%s <<<)" % (name, name), re.S)
    m = pat.search(text)
    if not m:
        return text, False
    return text[: m.start("body")] + body + text[m.end("body"):], True


def main():
    args = sys.argv[1:]
    check = "--check" in args
    amap = tomllib.loads(AGE_MAP.read_text(encoding="utf-8"))
    if "--registry" in args:
        refresh_registry(args[args.index("--registry") + 1], amap)
    reg = json.loads(REGISTRY.read_text(encoding="utf-8"))
    tfc = json.loads(TFC_IDS.read_text(encoding="utf-8"))
    am = AgeMap(amap, reg, tfc)
    if "--explain" in args:
        item = args[args.index("--explain") + 1]
        print(item, "->", *am.age_of(item))
        return
    tags, block_tags, recipe_locks, dawn = am.build()

    files = {}  # path -> new text
    for st, values in tags.items():
        files[TAG_DIR / "item" / "age_items" / f"{st}.json"] = render_tag(values)
    for st, values in block_tags.items():
        files[TAG_DIR / "block" / "age_blocks" / f"{st}.json"] = render_tag(values)
    missing_markers = []
    for path in sorted(STAGES_DIR.glob("*.toml")):
        stage = path.stem
        text = path.read_text(encoding="utf-8")
        new = text
        item_body = render_list([f"tag:firmages:age_items/{stage}"]) if stage in RANK and stage != "dawn" and tags[stage] else ""
        for name, body in (("items", item_body),
                           ("recipes", render_list(sorted(recipe_locks.get(stage, [])))),
                           ("dawn_crafting", render_list(dawn) if stage == "age_0" else ""),
                           ("ore_overrides", render_overrides(am.overrides.get(stage, [])))):
            new, found = replace_block(new, name, body)
            if not found and body and not body.startswith("# (no ore"):
                missing_markers.append(f"{path.name}: generated:{name}")
        files[path] = new
    if missing_markers:
        die("stage files lack marker blocks for generated content: " + ", ".join(missing_markers))

    dirty = []
    for path, text in files.items():
        old = path.read_text(encoding="utf-8") if path.exists() else None
        if old != text:
            dirty.append(path.relative_to(ROOT).as_posix())
            if not check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")

    print("age_items per stage:", {s: len(v) for s, v in tags.items()})
    print("age_blocks per stage:", {s: len(v) for s, v in block_tags.items() if v})
    print("ore overrides per stage:", {s: len(v) for s, v in sorted(am.overrides.items())},
          "total", sum(len(v) for v in am.overrides.values()))
    print("grid-recipe namespace locks per stage:", {s: len(v) for s, v in sorted(recipe_locks.items(), key=lambda x: RANK[x[0]])},
          "| dawn recipe selectors:", len(dawn))
    if check and dirty:
        print("out of date:", ", ".join(dirty))
        sys.exit(1)
    print(("checked" if check else "rewrote"), len(dirty), "file(s)")


if __name__ == "__main__":
    main()
