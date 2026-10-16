#!/usr/bin/env python3
"""Firmament Ages - recipe overrides for mod recipe files that KubeJS cannot read.

KubeJS 2101.7.2 reads every recipe file itself (RecipesKubeEvent.discoverRecipes, checked with javap) and warns:

  "Skipping recipe X, not a json object"      the file has no "type". TFC, TFCAstikorCarts, FirmaSupplementaries,
                                              Firmalife, Beneath and TFCreate remove recipes with files that hold only
                                              {"neoforge:conditions": [{"type": "neoforge:false"}]} (462 per data load).
  "Failed to parse recipe X! Falling back"    the recipe is older than its KubeJS schema (Create 0.5 fluid ingredients,
                                              {"item": {...}} results) or names a tag or item that does not exist.

Each entry of dev/data/recipe_overrides.json is written to kubejs/data/<ns>/recipe/<path>.json (the KubeJS data pack
sits above every mod), built from the mod's own file:

  remove    {"type": <type of the shadowed recipe>, "neoforge:conditions": [{"type": "neoforge:false"}]}: with a type
            KubeJS reads the conditions first and drops the file at DEBUG; the recipe stays removed
  modernise the mod's recipe in Create 6.0.10's own format: {"type": "neoforge:single"|"neoforge:tag", "amount", "fluid"|
            "tag"} fluid ingredients, flat {"id", "count", "chance"} results, "heat_requirement"
  retag     the mod's recipe with the tag ids of the entry's "tags" map replaced
  recount   the mod's recipe with the result count set to the entry's "count" (a result above the item's stack size
            loads at boot but fails every later data load with "larger than maximum")

With a log, every "not a json object" id becomes "remove" (only when one of the jars holds exactly the removal file),
and every "Falling back" id becomes "remove" when it matches PACK_REMOVES (the pack removes it anyway), "retag" when
it is in RETAG, "recount" when it is in RECOUNT, else "modernise" when that changes it; anything else is listed for
a manual look. New entries are merged into the manifest, so a later log (where the fixed ids no longer warn) keeps the existing ones.

Usage:
  python dev/gen_recipe_overrides.py --log test-server/logs/latest.log   add the log's ids, then write every file
  python dev/gen_recipe_overrides.py                                     write every file of the manifest
  python dev/gen_recipe_overrides.py --check                             files match the manifest (and the jars)
  --mods DIR      mod jars (default: the CurseForge instance, else test-server/mods)
  --vanilla JAR   Minecraft 1.21.1 client jar, for the type of a removed vanilla recipe (default: CurseForge install)
Exit code: 0 = ok, 1 = --check found a difference or an id needs a manual look, 2 = bad input.
"""
import argparse
import gzip
import io
import json
import os
import re
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(REPO, "dev", "data", "recipe_overrides.json")
DATA = os.path.join(REPO, "kubejs", "data")
MOD_DIRS = ["C:/Users/Felix/curseforge/minecraft/Instances/Firmament Ages/mods", os.path.join(REPO, "test-server", "mods")]
VANILLA = "C:/Users/Felix/curseforge/minecraft/Install/versions/1.21.1/1.21.1.jar"
FALSE = [{"type": "neoforge:false"}]
REMOVAL = {"neoforge:conditions": FALSE}
DEFAULT_TYPE = "minecraft:crafting_shaped"
RECIPE_FILE = re.compile(r"^data/([a-z0-9_.-]+)/recipes?/([a-z0-9_./-]+)\.json$")
SKIP = re.compile(r"\[KubeJS Server/\]: .*Skipping recipe ([a-z0-9_.-]+:[a-z0-9_./-]+), not a json object|"
                  r"\[net\.minecraft\.world\.item\.crafting\.RecipeManager/\]: Skipping recipe "
                  r"([a-z0-9_.-]+:[a-z0-9_./-]+), not a json object")
FALLBACK = re.compile(r"\[KubeJS Server/\]: \S+: Failed to parse recipe '([a-z0-9_.-]+:[a-z0-9_./-]+)\[")

WHY = {
    "typeless-removal": "The mod removes this recipe with a file that has no type; KubeJS warned 'not a json object' "
                        "at every data load. Same removal with a type: KubeJS skips it at DEBUG.",
    "pack-removes": "KubeJS could not read the mod's old format ('Falling back to vanilla'), and the pack removes the "
                    "recipe anyway (see the entry's note); removed before KubeJS reads it.",
    "missing-item": "The recipe names an item that does not exist in this pack (see the entry's note); it failed to "
                    "load on every data load and is removed before KubeJS reads it.",
    "old-format": "Create 0.5 format that Create 6 still loads but KubeJS cannot read ('Falling back to vanilla'); "
                  "rewritten in Create 6.0.10's own format, same recipe.",
    "over-stack": "The recipe makes more than the item's stack size; it loaded at boot but every later data load "
                  "(/reload, /firmages ages sync) rejected it with 'larger than maximum'. Same recipe, count at the "
                  "stack size.",
    "wrong-tag": "The recipe names a tag that does not exist, so its ingredient was empty and the recipe could not be "
                 "crafted; KubeJS warned 'not allowed to be empty'. Points to the tag the mod meant.",
}
# Fallback ids the pack removes anyway: (regex, why, note).
PACK_REMOVES = [
    (re.compile(r"^createdieselgenerators:(?!crafting/(diesel_engine|large_diesel_engine|huge_diesel_engine|"
                r"engine_piston|engine_silencer|engine_turbocharger)$)"), "pack-removes",
     "recipes/age_5/electric_age.js keeps only the Diesel Generators engines and their upgrades"),
    (re.compile(r"^woodencog:cutting/(slab|stair)/(loam|sandy_loam|silt|silty_loam)_brick_(slab|stairs)$"),
     "missing-item", "Wooden Cog 1.2.19 cuts mud bricks of the pre-4.2 TFC soils; TFC 4.2.11 has no "
                     "tfc:mud_bricks/<loam|sandy_loam|silt|silty_loam>"),
    (re.compile(r"^storagedrawers:personal_key_cofh$"), "missing-item",
     "Storage Drawers' CoFH personal key names an item of CoFH Core, which is not in the pack (empty ingredient)"),
]
# Fallback ids with a wrong tag: id -> {old tag: new tag}. Tags checked in the fa_dump item_tags.json of 2026-10-14.
RETAG = {
    "firmasupps:crafting/soap/clean_tfc_candle": {"tfc:candles": "tfc:colored_candles"},
    "firmasupps:crafting/soap/clean_tfc_bricks_alabaster": {"tfc:colored_bricks_alabaster": "tfc:colored_alabaster_bricks"},
}
# Fallback ids whose result count is above the item's stack size: id -> the stack size the reload error names.
RECOUNT = {
    "adpoles:redstone_pole": 32,   # AdPoles 10.1.0.2 makes 48 (ModRecipes); the item stacks to 32 (F1 server pack log)
}
DOC = ("Recipe overrides written by dev/gen_recipe_overrides.py into kubejs/data/<ns>/recipe/. 'why' names the reason "
       "in 'reasons'; 'from' is the jar whose file the override replaces. Regenerate with python "
       "dev/gen_recipe_overrides.py, check with --check.")


def rel(p):
    return os.path.relpath(p, REPO).replace(os.sep, "/")


def override_path(rid):
    ns, path = rid.split(":", 1)
    return os.path.join(DATA, ns, "recipe", *path.split("/")) + ".json"


# ---------------------------------------------------------------------------------------------------- jar index
def index_jars(mods_dir, vanilla):
    """recipe id -> list of (jar name, parsed json or None)."""
    idx = {}

    def scan(zf, name):
        for info in zf.infolist():
            n = info.filename
            if n.endswith(".jar"):
                try:
                    with zipfile.ZipFile(io.BytesIO(zf.read(n))) as inner:
                        scan(inner, n.rsplit("/", 1)[-1])
                except zipfile.BadZipFile:
                    pass
                continue
            m = RECIPE_FILE.match(n)
            if m:
                try:
                    obj = json.loads(zf.read(n).decode("utf-8"))
                except ValueError:
                    obj = None
                idx.setdefault("%s:%s" % m.groups(), []).append((name, obj))

    jars = sorted(f for f in os.listdir(mods_dir) if f.endswith(".jar"))
    if vanilla and os.path.isfile(vanilla):
        jars.append(vanilla)
    for f in jars:
        path = f if os.path.isabs(f) else os.path.join(mods_dir, f)
        try:
            with zipfile.ZipFile(path) as zf:
                scan(zf, os.path.basename(f))
        except (OSError, zipfile.BadZipFile) as e:
            print("gen_recipe_overrides: cannot read %s: %s" % (f, e))
    return idx


def find_mods(arg):
    for d in ([arg] if arg else MOD_DIRS):
        if d and os.path.isdir(d):
            return d
    return None


# ---------------------------------------------------------------------------------------------------- transforms
def modernise_fluid(ing):
    if not isinstance(ing, dict):
        return ing
    if ing.get("type") == "fluid_stack" and "fluid" in ing and "components" in ing:
        # a fluid with data components (Create's potion fluid): NeoForge's components ingredient, as in Create 6.0.10's
        # own filling/glowstone.json
        out = {"type": "neoforge:components", "amount": ing.get("amount", 1000), "components": ing["components"],
               "fluids": ing["fluid"]}
        ing = {k: v for k, v in ing.items() if k != "components"}
    elif ing.get("type") == "fluid_stack" and "fluid" in ing:
        out = {"type": "neoforge:single", "amount": ing.get("amount", 1000), "fluid": ing["fluid"]}
    elif ing.get("type") == "fluid_tag" and "fluid_tag" in ing:
        out = {"type": "neoforge:tag", "amount": ing.get("amount", 1000), "tag": ing["fluid_tag"]}
    else:
        return ing
    extra = set(ing) - {"type", "fluid", "fluid_tag", "amount"}
    if extra:
        raise ValueError("fluid ingredient with unknown keys %s" % sorted(extra))
    return out


def modernise_result(res):
    if isinstance(res, dict) and isinstance(res.get("item"), dict) and "id" not in res:
        out = dict(res["item"])
        for k, v in res.items():
            if k != "item":
                out[k] = v
        return out
    return res


def modernise(obj):
    if not str(obj.get("type", "")).startswith("create:"):
        return obj
    out = {}
    for k, v in obj.items():
        if k == "heatRequirement":
            out["heat_requirement"] = v
        elif k == "ingredients" and isinstance(v, list):
            out[k] = [modernise_fluid(i) for i in v]
        elif k == "results" and isinstance(v, list):
            out[k] = [modernise_result(r) for r in v]
        elif k == "transitional_item":  # sequenced assembly: one result in the same format
            out[k] = modernise_result(v)
        elif k == "sequence" and isinstance(v, list):  # sequenced assembly steps are Create recipes themselves
            out[k] = [modernise(step) if isinstance(step, dict) else step for step in v]
        else:
            out[k] = v
    return out


def retag(obj, tags):
    if isinstance(obj, dict):
        return {k: (tags.get(v, v) if k == "tag" and isinstance(v, str) else retag(v, tags)) for k, v in obj.items()}
    if isinstance(obj, list):
        return [retag(v, tags) for v in obj]
    return obj


def build(entry, idx):
    """The override's json for one manifest entry, or raise ValueError."""
    rid = entry["id"]
    providers = idx.get(rid, [])
    if not providers:
        raise ValueError("no jar has %s" % rid)
    if entry["action"] == "remove":
        if entry["why"] == "typeless-removal" and not any(o == REMOVAL for _, o in providers):
            raise ValueError("no jar holds the typeless removal file any more")
        typed = [o["type"] for _, o in providers if isinstance(o, dict) and isinstance(o.get("type"), str)]
        return {"type": typed[0] if typed else DEFAULT_TYPE, "neoforge:conditions": FALSE}
    real = [(j, o) for j, o in providers if isinstance(o, dict) and "type" in o]
    if len(real) != 1:
        raise ValueError("%d jars hold a typed recipe, expected 1" % len(real))
    src = real[0][1]
    if entry["action"] == "modernise":
        out = modernise(src)
    elif entry["action"] == "retag":
        out = retag(src, entry["tags"])
    elif entry["action"] == "recount":
        out = json.loads(json.dumps(src))
        out["result"]["count"] = entry["count"]
    else:
        raise ValueError("unknown action %r" % entry["action"])
    if out == src:
        raise ValueError("the %s rule changes nothing" % entry["action"])
    return out


# ---------------------------------------------------------------------------------------------------- manifest
def read_manifest():
    if not os.path.isfile(MANIFEST):
        return {}
    with open(MANIFEST, encoding="utf-8") as f:
        return {e["id"]: e for e in json.load(f)["recipes"]}


def write_manifest(entries):
    rows = [entries[k] for k in sorted(entries)]
    counts = {}
    for e in rows:
        key = "%s %s" % (e["action"], e["why"])
        counts[key] = counts.get(key, 0) + 1
    data = {"_doc": DOC, "reasons": WHY, "count": len(rows), "by_reason": dict(sorted(counts.items())), "recipes": rows}
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def read_log(path):
    opener = gzip.open if path.endswith(".gz") else open
    skipped, fallback = set(), set()
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            m = SKIP.search(line)
            if m:
                skipped.add(m.group(1) or m.group(2))
                continue
            m = FALLBACK.search(line)
            if m:
                fallback.add(m.group(1))
    return skipped, fallback


def classify(rid, idx, skipped):
    providers = idx.get(rid, [])
    jars = sorted({j for j, _ in providers})
    if rid in skipped:
        hit = [j for j, o in providers if o == REMOVAL]
        if not hit:
            return None, "no jar holds the plain removal file (%s)" % ", ".join(jars)
        return {"id": rid, "action": "remove", "why": "typeless-removal", "from": hit[0]}, None
    for rx, why, note in PACK_REMOVES:
        if rx.search(rid):
            return {"id": rid, "action": "remove", "why": why, "note": note, "from": ", ".join(jars)}, None
    if rid in RETAG:
        return {"id": rid, "action": "retag", "why": "wrong-tag", "tags": RETAG[rid], "from": ", ".join(jars)}, None
    if rid in RECOUNT:
        return {"id": rid, "action": "recount", "why": "over-stack", "count": RECOUNT[rid], "from": ", ".join(jars)}, None
    e = {"id": rid, "action": "modernise", "why": "old-format", "from": ", ".join(jars)}
    try:
        build(e, idx)
    except ValueError as err:
        return None, str(err)
    return e, None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--log", help="server log with the KubeJS warnings (.log or .log.gz)")
    ap.add_argument("--mods")
    ap.add_argument("--vanilla", default=VANILLA)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    entries = read_manifest()
    mods = find_mods(a.mods)

    if a.check:
        bad = []
        idx = index_jars(mods, a.vanilla) if mods else None
        for rid, e in sorted(entries.items()):
            p = override_path(rid)
            try:
                with open(p, encoding="utf-8") as f:
                    have = json.load(f)
            except (OSError, ValueError):
                bad.append("%s: %s missing or not json" % (rid, rel(p)))
                continue
            if idx is None:
                if e["action"] == "remove" and have.get("neoforge:conditions") != FALSE:
                    bad.append("%s: not a removal" % rel(p))
                continue
            try:
                if build(e, idx) != have:
                    bad.append("%s: differs from what the jars give (run the generator)" % rel(p))
            except ValueError as err:
                bad.append("%s: %s" % (rid, err))
        print("gen_recipe_overrides --check: %d overrides%s, %d problems"
              % (len(entries), "" if idx is not None else " (no mod jars: structure only)", len(bad)))
        for b in bad:
            print("  " + b)
        return 1 if bad else 0

    if not mods:
        print("gen_recipe_overrides: no mod folder (--mods)")
        return 2
    idx = index_jars(mods, a.vanilla)
    manual = []
    added = 0
    if a.log:
        if not os.path.isfile(a.log):
            print("gen_recipe_overrides: no such log: %s" % a.log)
            return 2
        skipped, fallback = read_log(a.log)
        for rid in sorted(skipped | fallback):
            e, why = classify(rid, idx, skipped)
            if e is None:
                manual.append((rid, why))
                continue
            if rid not in entries:
                added += 1
            entries[rid] = e
        print("gen_recipe_overrides: %s: %d 'not a json object', %d 'Falling back'" % (a.log, len(skipped), len(fallback)))
    written = 0
    for rid, e in sorted(entries.items()):
        try:
            obj = build(e, idx)
        except ValueError as err:
            manual.append((rid, str(err)))
            continue
        write_json(override_path(rid), obj)
        written += 1
    write_manifest(entries)
    print("gen_recipe_overrides: %d new entries, %d in the manifest, %d files written" % (added, len(entries), written))
    for rid, why in manual:
        print("  needs a manual look: %s: %s" % (rid, why))
    return 1 if manual else 0


if __name__ == "__main__":
    sys.exit(main())
