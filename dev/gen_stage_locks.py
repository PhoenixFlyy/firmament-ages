#!/usr/bin/env python
"""Generate the Age registry tags and the ProgressiveStages locks from ONE source, dev/age_map.toml.

Inputs
  dev/age_map.toml               Age per mod namespace, material words, ore families and ore blocks, TFC tables, explicit
                                 rules, and
                                 the section dev/derive_ages.py generates ([derived] later first reachable Ages,
                                 [derived_hidden] never reachable items; an item takes the later of rule and derived Age)
  dev/data/registry.json         snapshot of every item id, the ore/rock block ids, the item tags the rules use
                                 and the grid-recipe namespaces (refresh with --registry, see below)
  dev/data/tfc-4.2.11-ids.json   TFC ids by kind (rocks, ore kinds, metal items; from the TFC git tree v4.2.11)
  config/progressivestages/stages/disabled.toml   items locked forever (they go to age_items/disabled)

Outputs
  kubejs/data/firmages/tags/item/age_items/<stage>.json    every item in exactly one tag (dawn, age_0..age_9, disabled)
  kubejs/data/firmages/tags/block/age_blocks/<stage>.json  ore and deposit blocks per Age (all grades and rocks)
  kubejs/data/firmages/tags/fluid/age_fluids/<stage>.json  every fluid in exactly one tag (dawn, age_0..age_9; disabled
                                                           stays empty). A fluid's Age: the rule Age of its bucket item
                                                           (registry dump field fluid_buckets; derived Ages and disabled
                                                           buckets do not count), else for "<ns>:metal/<metal>" the
                                                           metal's [tfc_metals] or [materials] Age, else the later of its
                                                           [mods] Age and its [materials] words (as items, rule 5).
                                                           The recipe gate drops recipes with a locked fluid output.
  kubejs/assets/firmages/age_namespaces.json               the [mods] table for the client: the firmages-core EMI plugin
                                                           disables stacks, components and categories of locked mods
  kubejs/data/c/tags/item/hidden_from_recipe_viewers.json  the never reachable items of [derived_hidden] (EMI/JEI hide them)
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
                                                     refresh dev/data/registry.json from a /fa_dump taken with
                                                     every Age unlocked (age_9), then rewrite
  python dev/gen_stage_locks.py --explain <item id>  print the rule that decides the Age of one item (or fluid)
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
NAMESPACES_ASSET = ROOT / "kubejs" / "assets" / "firmages" / "age_namespaces.json"
HIDDEN_TAG = ROOT / "kubejs" / "data" / "c" / "tags" / "item" / "hidden_from_recipe_viewers.json"

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
    # The recipe gate drops every recipe of a locked Age, so a dump taken before age_9 lacks whole namespaces and
    # the grid-recipe locks below would silently shrink. Dump with every Age unlocked
    # (firmages ages simulate grant age_9, then /fa_dump).
    if "age_9" not in recipes.get("unlocked", []):
        die(f"{dump_dir}/recipes.json was dumped with Ages {recipes.get('unlocked')}; dump with age_9 unlocked")
    col = recipes["columns"].index("id"), recipes["columns"].index("type")
    grid_ns = sorted({r[col[0]].split(":", 1)[0] for r in recipes["recipes"] if r[col[1]] == "minecraft:crafting"})
    wanted = selector_tags(list(amap.get("items", {})) + disabled_selectors())
    # fluid -> bucket item (Fluid#getBucket; dump.js and /firmages dump registry write it since firmages-core 0.9).
    # An older dump has no such key; then the buckets of the current snapshot stay.
    buckets = reg.get("fluid_buckets")
    if buckets is None:
        old = json.loads(REGISTRY.read_text(encoding="utf-8")) if REGISTRY.exists() else {}
        buckets = old.get("fluid_buckets", {})
        print(f"note: {dump_dir.as_posix()}/registries.json has no fluid_buckets; kept {len(buckets)} from the snapshot")
    source = dump_dir.as_posix()
    source = source[source.index("test-server/"):] if "test-server/" in source else source
    snap = {
        "source": f"/fa_dump in {source} (kubejs/server_scripts/debug/dump.js)",
        "items": sorted(i for i in reg["item"] if i != "minecraft:air"),
        "blocks": sorted(b for b in reg["block"] if BLOCK_KEEP.search(b) or b in amap.get("ore_blocks", {})),
        "fluids": sorted(f for f in reg["fluid"] if f != "minecraft:empty"),
        "fluid_buckets": {f: b for f, b in sorted(buckets.items()) if b != "minecraft:air"},
        "item_tags": {t: sorted(item_tags.get(t, [])) for t in wanted},
        "grid_recipe_namespaces": grid_ns,
    }
    missing = [t for t in wanted if t not in item_tags]
    if missing:
        die(f"item tags used by rules are not in the dump: {missing}")
    REGISTRY.write_text(json.dumps(snap, indent=0) + "\n", encoding="utf-8", newline="\n")
    print(f"registry snapshot: {len(snap['items'])} items, {len(snap['blocks'])} ore/rock blocks, "
          f"{len(snap['fluids'])} fluids ({len(snap['fluid_buckets'])} with a bucket), "
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
        check_stages({b: v["stage"] for b, v in amap.get("ore_blocks", {}).items()}, "ore_blocks")
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
        # disabled.toml (ProgressiveStages stage "disabled") plus [hidden] (tag only, no PS lock)
        self.disabled = disabled_selectors() + [f"id:{i}" for i in amap.get("hidden", {}).get("items", [])]
        # generated by dev/derive_ages.py: first reachable Age when later than the rules, and never reachable items
        self.derived = amap.get("derived", {})
        check_stages(self.derived, "derived")
        self.derived_hidden = set(amap.get("derived_hidden", {}).get("items", []))
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
        # [ore_blocks]: a mod's own block that a pack vein places in one host rock, disguised as that rock
        for b, v in a.get("ore_blocks", {}).items():
            if b not in self.blocks_set:
                die(f"[ore_blocks] {b}: not in the registry snapshot")
            if v["rock"] not in rocks:
                die(f"[ore_blocks] {b}: unknown TFC rock {v['rock']!r}")
            blocks[b] = v["stage"]
            ovr(v["stage"], (b, f"tfc:rock/raw/{v['rock']}", f"tfc:rock/loose/{v['rock']}"))

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
        """(stage, reason) of one item: the rules, then the later first reachable Age of dev/derive_ages.py."""
        st, why = self.rule_age(item)
        if st == "disabled":
            return st, why
        if item in self.derived_hidden:
            return "disabled", f"[derived_hidden] never reachable (rules give {st}: {why})"
        d = self.derived.get(item)
        if d and RANK[d] > RANK[st]:
            return d, f"[derived] first reachable at {d} (rules give {st}: {why})"
        return st, why

    def rule_age(self, item):
        """(stage, reason) of one item from the hand-written rules alone."""
        for sel in self.disabled:
            if selector_matches(sel, item, self.tags):
                return "disabled", f"disabled.toml or [hidden] {sel}"
        if item in self.id_rules:
            return self.id_rules[item], f"[items] id:{item}"
        for sel, st in self.other_rules:
            if selector_matches(sel, item, self.tags):
                return st, f"[items] {sel}"
        if item in self.table_items:
            return self.table_items[item], "TFC table / [ore_families] / [ore_blocks]"
        ns, path = item.split(":", 1)
        base = self.mods[ns]
        mat, words = self.material_age(ns, path)
        if mat and RANK[mat] > RANK[base]:
            return mat, f"[materials] {'+'.join(words)} (mod {ns} = {base})"
        return base, f"[mods] {ns}"

    def fluid_age(self, fluid):
        """(stage, reason) of one fluid: its bucket's rule Age, a TFC-style metal fluid's metal, else its namespace."""
        bucket = self.reg.get("fluid_buckets", {}).get(fluid)
        if bucket in self.items_set:
            # The hand-written rules only: a bucket's derived Age says when a bucket can be filled, not when the fluid
            # exists, and a never reachable bucket item does not make the fluid unreachable.
            st, why = self.rule_age(bucket)
            if st != "disabled":
                return st, f"bucket {bucket}: {why}"
        ns, path = fluid.split(":", 1)
        parts = path.split("/")
        if len(parts) == 2 and parts[0] == "metal":
            metal = parts[1][len("flowing_"):] if parts[1].startswith("flowing_") else parts[1]
            st = self.amap["tfc_metals"].get(metal) or self.materials.get(metal)
            if st:
                return st, f"metal {metal}"
        # like rule 5 for items: the later of the namespace and the material words in the path (uranium, fusion)
        base = self.mods[ns]
        mat, words = self.material_age(ns, path)
        if mat and RANK[mat] > RANK[base]:
            return mat, f"[materials] {'+'.join(words)} (mod {ns} = {base})"
        return base, f"[mods] {ns}"

    def build(self):
        unmapped = sorted({i.split(":", 1)[0] for i in self.reg["items"]} - set(self.mods))
        unmapped_r = sorted(set(self.reg["grid_recipe_namespaces"]) - set(self.mods) - set(self.amap.get("recipes", {})))
        unmapped_f = sorted({f.split(":", 1)[0] for f in self.reg.get("fluids", [])} - set(self.mods))
        if unmapped or unmapped_r or unmapped_f:
            die(f"namespaces without a [mods] entry: {sorted(set(unmapped) | set(unmapped_r) | set(unmapped_f))}")
        tags = {s: [] for s in STAGES + ["disabled"]}
        for item in self.reg["items"]:
            tags[self.age_of(item)[0]].append(item)
        fluid_tags = {s: [] for s in STAGES + ["disabled"]}
        for fluid in self.reg.get("fluids", []):
            fluid_tags[self.fluid_age(fluid)[0]].append(fluid)
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
        return tags, block_tags, fluid_tags, recipe_locks, dawn


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


def render_namespaces(mods):
    body = ",\n".join(f'    "{ns}": "{st}"' for ns, st in sorted(mods.items()))
    return ('{\n  "_comment": "Generated by dev/gen_stage_locks.py from dev/age_map.toml [mods]. Read by the firmages-core '
            'EMI plugin (SPEC 21): stacks, components and recipe categories of a namespace whose Age is locked are '
            'hidden.",\n  "namespaces": {\n' + body + "\n  }\n}\n")


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
        if item in set(reg.get("fluids", [])) and item not in am.items_set:
            print(item, "(fluid) ->", *am.fluid_age(item))
        else:
            print(item, "->", *am.age_of(item))
        return
    tags, block_tags, fluid_tags, recipe_locks, dawn = am.build()

    files = {}  # path -> new text
    for st, values in tags.items():
        files[TAG_DIR / "item" / "age_items" / f"{st}.json"] = render_tag(values)
    # Never reachable items (dev/derive_ages.py) are not ProgressiveStages-locked, so EMI and JEI hide them through the
    # NeoForge convention tag, like the [hidden] items (server_scripts/tags/hidden_from_viewers.js adds those).
    files[HIDDEN_TAG] = render_tag(sorted(i for i in am.derived_hidden if i in am.items_set))
    for st, values in block_tags.items():
        files[TAG_DIR / "block" / "age_blocks" / f"{st}.json"] = render_tag(values)
    if reg.get("fluids"):
        for st, values in fluid_tags.items():
            files[TAG_DIR / "fluid" / "age_fluids" / f"{st}.json"] = render_tag(values)
    files[NAMESPACES_ASSET] = render_namespaces(am.mods)
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
    print("age_fluids per stage:", {s: len(v) for s, v in fluid_tags.items() if v},
          f"({len(reg.get('fluid_buckets', {}))} fluids with a known bucket)")
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
