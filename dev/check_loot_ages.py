#!/usr/bin/env python3
"""Firmament Ages - loot tables and the Ages of what they hold (round G2, firmages-core SPEC §19.5).

firmages-core's loot gate (gate.lootFilter) removes from every generated loot each item of an Age the roll's player
(without one: the server) does not hold. This overview shows where the gate works: every loot table with the Age of
each item, and the tables that hold items of a later Age than the place where they first open.

Inputs
  loot tables   dev/data/world_sources.json (derive_ages.py --scan), or with --mods DIR the jars of DIR, the vanilla
                jar (--vanilla, else derived from DIR like derive_ages.py, else the CurseForge launcher's 1.21.1 jar);
                then firmages-core's own tables (mod/firmages-core/src/main/resources) and kubejs/data last
  item Ages     the generated tags kubejs/data/firmages/tags/item/age_items/<stage>.json, the Age index the gate
                reads (= dev/age_map.toml with [derived]; disabled = disabled.toml, [hidden], [derived_hidden])
  tag entries   expanded with --dump-dir <fa_dump>/item_tags.json, else counted as unresolved
  place Age     where a table first opens: a structure chest the Age of its template (dev/derive_ages.py World,
                VANILLA_CHEST_AGE), a block table the Age of its block, an entity table the earliest spawn of the
                mob, else the [mods] Age of the namespace, else Dawn

Output: totals, the namespaces with the most tables of later items, the named examples (village smiths, dungeon,
Ad Astra temples, the TFC ruins), the tables LootJS changes at runtime (not in this scan); with --mods also every
generating structure template with prefilled containers (items placed with the block, no loot roll; nested contents
included) that holds items of a later Age than the template or disabled ones: firmages-core removes those stacks when
the template is placed (StructureTemplateMixin, with the server's Ages).

Usage
  python dev/check_loot_ages.py [--mods DIR] [--dump-dir DIR] [--table ID ...] [--list] [--show N] [--strict]
  Exit 0 (informational). --strict: exit 1 when a firmages:* table holds an item that never drops (disabled, or not
  registered with --dump-dir), or an item of a later Age than its place unless LATER_ON_PURPOSE names the table (the
  gate holds the item back on purpose), or LATER_ON_PURPOSE names a table that no longer holds later items.
"""
import argparse
import collections
import glob
import json
import os
import re
import sys
import zipfile

DEV = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DEV)
sys.path.insert(0, DEV)
import derive_ages as da  # noqa: E402

AGES = da.AGES
AGE_TAGS = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items")
KUBEJS = os.path.join(REPO, "kubejs")
CORE_RES = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources")
LAUNCHER_JAR = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")

# firmages:* tables whose later items are on purpose: the loot gate holds each back until its Age. table -> reason
LATER_ON_PURPOSE = {
    "firmages:chests/ruin_small": "TFC ruins, one pool per Age up to the Iron Age (decision 2026-10-15); the chest grows "
                                  "with the opener's Ages",
    "firmages:chests/ruin_rich": "TFC ruins, one pool per Age up to the Electric Age (decision 2026-10-15); the chest grows "
                                 "with the opener's Ages",
    "firmages:chests/cold_forge": "the Iron Age expedition's chest (decision 2026-10-14); a Dawn player who stumbles on it "
                                  "gets no Bronze Age flux",
    "firmages:blocks/starfall_core": "one block for both metals: meteoric iron cores fall from the Iron Age, starsteel "
                                     "cores (metal=starsteel) from the Electric Age (SPEC 25.4)",
}
# Named examples of the report (Felix's G2 finding): prefixes, each matching tables are shown
EXAMPLES = [
    ("village smiths", "minecraft:chests/village/village_weaponsmith"),
    ("village smiths", "minecraft:chests/village/village_toolsmith"),
    ("village smiths", "minecraft:chests/village/village_armorer"),
    ("dungeon", "minecraft:chests/simple_dungeon"),
    ("Ad Astra temples", "ad_astra:chests/temple/"),
    ("TFC ruins", "firmages:chests/ruin_"),
    ("Supplementaries", "supplementaries:"),
    ("Alex's Mobs", "alexsmobs:"),
]
LOOTJS_TABLE_RE = re.compile(r"""(?:getLootTable\(|table:\s*)['"]([a-z0-9_.-]+:[a-z0-9_/.-]+)['"]""")


def ix(st):
    return AGES.index(st) if st in AGES else -1


def load_item_ages():
    out = {}
    for f in sorted(glob.glob(os.path.join(AGE_TAGS, "*.json"))):
        st = os.path.basename(f)[:-5]
        for v in json.load(open(f, encoding="utf-8"))["values"]:
            i = v["id"] if isinstance(v, dict) else v
            if not i.startswith("#"):
                out.setdefault(i, st)
    return out


def table_of(d):
    items, tags, refs = set(), set(), set()
    da._loot_entries(d, items, tags, refs)
    return {"items": sorted(items), "tags": sorted(tags), "refs": sorted(refs)}


def read_dir_tables(root, tables, owner, label):
    """Loot tables under <root>/data/<ns>/loot_table/ (a later read replaces the same id)."""
    for p in glob.glob(os.path.join(root, "data", "*", "loot_table", "**", "*.json"), recursive=True):
        rel = os.path.relpath(p, os.path.join(root, "data")).replace(os.sep, "/")
        ns, rest = rel.split("/", 1)
        try:
            d = json.load(open(p, encoding="utf-8-sig"))
        except (OSError, ValueError):
            continue
        tid = f"{ns}:{rest[len('loot_table/'):-5]}"
        tables[tid], owner[tid] = table_of(d), label


def scan_jars(mods, vanilla):
    """Loot tables of the vanilla jar and every jar of mods (mods merge into a table of another mod, replace a vanilla
    one, as dev/derive_ages.py --scan does), and the prefilled containers of their structure templates."""
    tables, owner, prefilled = {}, {}, {}
    jars = ([vanilla] if vanilla else []) + sorted(glob.glob(os.path.join(mods, "*.jar")))
    for j in jars:
        try:
            z = zipfile.ZipFile(j)
        except (OSError, zipfile.BadZipFile):
            continue
        name = "minecraft" if j == vanilla else os.path.basename(j)
        for n in z.namelist():
            m = re.match(r"data/([^/]+)/(.+)$", n)
            if not m:
                continue
            ns, rest = m.groups()
            if rest.startswith("loot_table/") and n.endswith(".json"):
                d = da._json(z, n)
                if d is None:
                    continue
                tid = f"{ns}:{rest[len('loot_table/'):-5]}"
                t = table_of(d)
                if tid in tables and owner[tid] != "minecraft":
                    for k in ("items", "tags", "refs"):
                        t[k] = sorted(set(t[k]) | set(tables[tid][k]))
                tables[tid], owner[tid] = t, name
            elif re.match(r"structures?/.+\.nbt$", rest):
                try:
                    root = da._nbt(z.read(n))
                except Exception:  # noqa: BLE001 - a broken template only loses its contents
                    continue
                its = set()
                for b in root.get("blocks") or []:
                    nb = b.get("nbt") if isinstance(b, dict) else None
                    if isinstance(nb, dict):
                        _stack_ids({k: v for k, v in nb.items() if k != "id"}, its)
                if its:
                    prefilled[f"{ns}:{rest.split('/', 1)[1][:-4]}"] = sorted(its)
    return tables, owner, prefilled, len(jars)


def _stack_ids(o, out):
    """Item ids of stacks inside a block entity's NBT (Items lists, single item fields, TFC inventories)."""
    if isinstance(o, dict):
        if isinstance(o.get("id"), str) and ("Count" in o or "count" in o):
            out.add(o["id"])
        for k, v in o.items():
            if k != "id":
                _stack_ids(v, out)
    elif isinstance(o, list):
        for v in o:
            _stack_ids(v, out)


def lootjs_tables():
    """Tables a LootJS script of the pack names (pools added or changed at runtime, not in the data this reads)."""
    out = collections.defaultdict(list)
    for p in glob.glob(os.path.join(KUBEJS, "server_scripts", "**", "*.js"), recursive=True):
        text = open(p, encoding="utf-8").read()
        if "LootJS" not in text:
            continue
        for t in LOOTJS_TABLE_RE.findall(text):
            out[t].append(os.path.relpath(p, REPO).replace(os.sep, "/"))
    return out


def place_ages(world, amap, tables, item_age):
    """table -> (Age, why): where the table first opens."""
    entity_age = {}
    for e, srcs in world.entity_src.items():
        for why, st in srcs.items():
            if st in AGES and (e not in entity_age or ix(st) < ix(entity_age[e][0])):
                entity_age[e] = (st, why)
    out = {}
    for tid in tables:
        ns, path = tid.split(":", 1)
        srcs = {w: a for w, a in world.table_src.get(tid, {}).items() if a in AGES}
        if srcs:
            why = min(srcs, key=lambda w: ix(srcs[w]))
            out[tid] = (srcs[why], why)
        elif path.startswith("blocks/") and item_age.get(f"{ns}:{path[7:]}") in AGES:
            out[tid] = (item_age[f"{ns}:{path[7:]}"], f"block {ns}:{path[7:]}")
        elif path.startswith("entities/") and f"{ns}:{path.split('/')[1]}" in entity_age:
            st, why = entity_age[f"{ns}:{path.split('/')[1]}"]
            out[tid] = (st, why)
        elif amap["mods"].get(ns) in AGES:
            out[tid] = (amap["mods"][ns], f"mod {ns}")
        else:
            out[tid] = ("dawn", "no place known")
    return out


def analyse(tables, item_age, tags, place, registry):
    """Per table: [(item, Age)], the later items, the never-dropping items, the unresolved tags."""
    rows = {}
    for tid, t in sorted(tables.items()):
        items = set(t["items"])
        unresolved = []
        for tg in t["tags"]:
            if tags is not None and tg in tags:
                items |= set(tags[tg])
            else:
                unresolved.append("#" + tg)
        st = place[tid][0]
        aged = sorted((i, item_age.get(i)) for i in items)
        later = [(i, a) for i, a in aged if a in AGES and ix(a) > ix(st)]
        never = [(i, a) for i, a in aged if a == "disabled" or (registry is not None and i not in registry)]
        rows[tid] = {"place": place[tid], "items": aged, "later": later, "never": never, "unresolved": unresolved}
    return rows


def strict_failures(rows):
    bad = []
    for tid, r in sorted(rows.items()):
        if not tid.startswith("firmages:"):
            continue
        for i, a in r["never"]:
            bad.append(f"{tid}: {i} ({a or 'not registered'}) never drops; remove the entry")
        if r["later"] and tid not in LATER_ON_PURPOSE:
            items = ", ".join(f"{i} ({a})" for i, a in r["later"][:6])
            bad.append(f"{tid} (opens from {r['place'][0]}, {r['place'][1]}) holds later items {items}; "
                       "fix the table or list it in LATER_ON_PURPOSE with the reason")
    for tid in LATER_ON_PURPOSE:
        if tid not in rows:
            bad.append(f"LATER_ON_PURPOSE {tid}: no such table; drop the row")
        elif not rows[tid]["later"]:
            bad.append(f"LATER_ON_PURPOSE {tid}: holds no later item any more; drop the row")
    return bad


def firmages_tables():
    """Only the pack's own tables: firmages-core's source, then kubejs/data (enough for --strict)."""
    tables, owner = {}, {}
    read_dir_tables(CORE_RES, tables, owner, "firmages-core source")
    read_dir_tables(KUBEJS, tables, owner, "kubejs/data")
    return {k: v for k, v in tables.items() if k.startswith("firmages:")}


def strict_check(show=40):
    """For dev/poc_analyze.py X-5: (failures, number of firmages:* tables)."""
    import tomllib
    amap = tomllib.load(open(da.AGE_MAP, "rb"))
    item_age = load_item_ages()
    tables = firmages_tables()
    ws = da.load_world_sources()
    world = da.World(ws, amap["mods"], set(item_age), set(item_age))
    rows = analyse(tables, item_age, None, place_ages(world, amap, tables, item_age), None)
    return strict_failures(rows)[:show], len(tables)


def main():
    import tomllib
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mods", help="scan the jars of this folder instead of dev/data/world_sources.json")
    ap.add_argument("--vanilla", help="vanilla jar for --mods (default: from the server layout, else the launcher's)")
    ap.add_argument("--dump-dir", help="fa_dump folder: item_tags.json expands tag entries, registries.json checks ids")
    ap.add_argument("--table", action="append", default=[], help="print every item of this table (repeatable)")
    ap.add_argument("--list", action="store_true", help="print every table that holds later items")
    ap.add_argument("--show", type=int, default=8, help="lines per list")
    ap.add_argument("--strict", action="store_true", help="exit 1 on a finding in a firmages:* table")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    amap = tomllib.load(open(da.AGE_MAP, "rb"))
    item_age = load_item_ages()
    prefilled = None
    if a.mods:
        vanilla = a.vanilla or os.path.join(os.path.dirname(os.path.abspath(a.mods)), da.VANILLA_JAR)
        if not os.path.isfile(vanilla):
            vanilla = LAUNCHER_JAR if os.path.isfile(LAUNCHER_JAR) else None
        if vanilla is None:
            print("WARN no vanilla jar: the minecraft tables are missing (--vanilla)")
        tables, owner, prefilled, njars = scan_jars(a.mods, vanilla)
        source = f"{njars} jars of {a.mods}" + (f" (vanilla {os.path.basename(vanilla)})" if vanilla else "")
    else:
        ws_raw = json.load(open(da.WORLD_SOURCES, encoding="utf-8"))
        tables = {k: dict(v) for k, v in da.load_world_sources()["loot"].items()}
        owner = {k: "world_sources.json" for k in tables}
        source = ws_raw["source"]
    read_dir_tables(CORE_RES, tables, owner, "firmages-core source")
    read_dir_tables(KUBEJS, tables, owner, "kubejs/data")
    tags = registry = None
    if a.dump_dir:
        tp = os.path.join(a.dump_dir, "item_tags.json")
        rp = os.path.join(a.dump_dir, "registries.json")
        tags = json.load(open(tp, encoding="utf-8")) if os.path.isfile(tp) else None
        registry = set(json.load(open(rp, encoding="utf-8"))["item"]) if os.path.isfile(rp) else None
    ws = da.load_world_sources()
    world = da.World(ws, amap["mods"], set(item_age), set(item_age))
    rows = analyse(tables, item_age, tags, place_ages(world, amap, tables, item_age), registry)

    with_items = [t for t, r in rows.items() if r["items"]]
    later = {t: r for t, r in rows.items() if r["later"]}
    never = {t: r for t, r in rows.items() if r["never"]}
    unresolved = sum(len(r["unresolved"]) for r in rows.values())
    print(f"source: {source}, then firmages-core's source and kubejs/data")
    print(f"{len(rows)} loot tables, {len(with_items)} with items; {len(later)} hold items of a later Age than where they "
          f"open ({sum(len(r['later']) for r in later.values())} entries); {len(never)} hold items that never drop "
          f"(disabled{' or not registered' if registry is not None else ''}); {unresolved} tag entries unresolved"
          f"{'' if tags is not None else ' (no --dump-dir)'}")
    kinds = collections.Counter(t.split(":", 1)[1].split("/")[0] for t in later)
    print("later items by table kind: " + ", ".join(f"{k} {n}" for k, n in kinds.most_common()))
    by_ns = collections.Counter(t.split(":")[0] for t in later)
    print("later items by namespace: " + ", ".join(f"{k} {n}" for k, n in by_ns.most_common(15)))

    def show_table(tid, full=False):
        r = rows[tid]
        st, why = r["place"]
        items = r["items"] if full else r["later"]
        print(f"  {tid} [opens from {st}: {why}; {owner.get(tid, '?')}] {len(r['items'])} items, {len(r['later'])} later"
              + (f", {len(r['never'])} never drop" if r["never"] else "") + (f", unresolved {r['unresolved']}" if r["unresolved"] else ""))
        for i, ag in items[:a.show if not full else None]:
            print(f"      {i} ({ag or 'untagged'})")
        if not full and len(items) > a.show:
            print(f"      ... {len(items) - a.show} more")

    print("\nexamples:")
    for label, pre in EXAMPLES:
        hits = sorted(t for t in rows if t.startswith(pre) and rows[t]["items"])
        affected = [t for t in hits if rows[t]["later"]]
        if not hits:
            print(f"- {label} {pre}: no table")
            continue
        print(f"- {label} {pre}: {len(hits)} tables, {len(affected)} with later items")
        for t in (affected or hits)[:3]:
            show_table(t)
    js = lootjs_tables()
    if js:
        print("\nLootJS changes these tables at runtime (their pools are not in this scan; the gate filters them too):")
        for t, files in sorted(js.items()):
            print(f"  {t}: {', '.join(sorted(set(files)))}")
    if prefilled is not None:
        placed = {t: its for t, its in prefilled.items() if t in world.template_age}
        late = {}
        for tid, its in placed.items():
            bad = [(i, item_age.get(i)) for i in its if item_age.get(i) == "disabled" or
                   (item_age.get(i) in AGES and ix(item_age[i]) > ix(world.template_age[tid]))]
            if bad:
                late[tid] = bad
        print(f"\nstructure templates with prefilled containers (placed items, no loot roll; firmages-core removes the "
              f"stacks of a later Age when the template is placed, with the server's Ages): {len(prefilled)}, "
              f"{len(placed)} of them generate; {len(late)} of those hold items of a later Age than the template or disabled")
        for tid, bad in sorted(late.items()):
            print(f"  {tid} ({world.template_age[tid]}): {', '.join(f'{i} ({ag})' for i, ag in bad)}")
    for tid in a.table:
        print("\ntable:")
        if tid in rows:
            show_table(tid, full=True)
        else:
            print(f"  {tid}: no such table")
    if a.list:
        print("\nevery table with later items:")
        for t in sorted(later):
            show_table(t)

    bad = strict_failures(rows)
    print(f"\n--strict ({sum(1 for t in rows if t.startswith('firmages:'))} firmages:* tables): "
          + ("clean" if not bad else f"{len(bad)} finding(s)"))
    for line in bad[:40]:
        print(f"  {line}")
    sys.exit(1 if a.strict and bad else 0)


if __name__ == "__main__":
    main()
