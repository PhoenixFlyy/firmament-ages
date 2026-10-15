#!/usr/bin/env python3
"""Write pack copies of the IE-style multiblock templates whose palette names a disabled or hidden block.

Every Immersive Engineering template multiblock (IE itself, Immersive Petroleum, Advanced TFC Tech, and the
templates TFC + IE Crossover ships over IE's own) is a structure file data/<ns>/structure/multiblocks/<name>.nbt.
IE's matcher accepts the TFC steel block where the template names immersiveengineering:storage_steel (both are in
c:storage_blocks/steel), but IE puts the TEMPLATE block back when the machine is taken apart and drops it when that
part is broken, so every teardown handed out the disabled block (world tests, poc-results "ATFC multiblocks" and
"IE templates"). The pack copies under kubejs/data/<ns>/structure/multiblocks/ name the canonical pack block instead;
build, teardown and drops then all use it. KubeJS data sits above every mod data pack, so a pack copy also wins over
an add-on's override (TFC + IE Crossover's arcfurnace.nbt).

Which template a path resolves to without the pack copy: when several jars ship one path, the jar of a mod other
than the namespace owner is the override (NeoForge orders a dependent mod's pack after its dependency's).

"Disabled or hidden": item tag firmages:age_items/disabled and c:hidden_from_recipe_viewers. A palette block in either
needs a CANON entry (or the generic IE storage-block rule); the script fails on one without, so a new template or a
newly disabled block cannot slip through.

The swap is done on the decompressed NBT: a palette name is a length-prefixed string and no enclosing tag stores a
byte length, so replacing the prefixed string is a valid edit. The result is parsed again as a check.

Usage:  python dev/gen_multiblock_templates.py [--check] [--list] [--mods <server>/mods]
  --check  exit 1 if a pack copy is missing, stale or no longer needed
  --list   print every template with its source jar and palette
"""
import argparse
import glob
import gzip
import json
import os
import re
import struct
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from derive_ages import _nbt  # noqa: E402

MODS = os.path.join(REPO, "test-server", "mods")
PACK_DATA = os.path.join(REPO, "kubejs", "data")
TEMPLATE_RE = re.compile(r"^data/([a-z0-9_.-]+)/structure/multiblocks/([a-z0-9_./-]+)\.nbt$")
DISABLED_TAG = os.path.join(REPO, "kubejs", "data", "firmages", "tags", "item", "age_items", "disabled.json")
HIDDEN_TAG = os.path.join(REPO, "kubejs", "data", "c", "tags", "item", "hidden_from_recipe_viewers.json")
REGISTRY = os.path.join(REPO, "dev", "data", "registry.json")

# Material canon (dev/age_map.toml [materials], Doc 10 v3 section 8.1): TFC metal blocks are the canonical ones.
CANON = {"immersiveengineering:storage_steel": "tfc:metal/block/steel"}
# Generic rule for further IE storage blocks and their slabs, used only when the TFC block exists.
GENERIC = [(re.compile(r"^immersiveengineering:storage_([a-z_]+)$"), r"tfc:metal/block/\1"),
           (re.compile(r"^immersiveengineering:slab_storage_([a-z_]+)$"), r"tfc:metal/block/\1_slab")]


def nbt_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_ids(path):
    with open(path, encoding="utf-8") as f:
        return {v["id"] if isinstance(v, dict) else v for v in json.load(f)["values"]}


def mod_ids(z):
    try:
        toml = z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
    except KeyError:
        return set()
    toml = re.split(r"^\s*\[\[?dependencies", toml, maxsplit=1, flags=re.M)[0]  # the mod's own ids, not its dependencies
    return set(re.findall(r'^\s*modId\s*=\s*"([^"]+)"', toml, re.M))


def effective_templates(mods_dir=MODS):
    """{(ns, name): (jar basename, raw gzip bytes, [all jar basenames shipping the path])} as the server resolves it
    without the pack copies."""
    found = {}
    for jar in sorted(glob.glob(os.path.join(mods_dir, "*.jar"))):
        with zipfile.ZipFile(jar) as z:
            ids = None
            for n in z.namelist():
                m = TEMPLATE_RE.match(n)
                if not m:
                    continue
                if ids is None:
                    ids = mod_ids(z)
                found.setdefault(m.groups(), []).append((os.path.basename(jar), ids, z.read(n)))
    out = {}
    for (ns, name), providers in found.items():
        if len(providers) == 1:
            win = providers[0]
        else:
            overrides = [p for p in providers if ns not in p[1]]
            if len(overrides) != 1:
                sys.exit(f"{ns}:{name}: cannot tell which of {[p[0] for p in providers]} the server loads")
            win = overrides[0]
        out[(ns, name)] = (win[0], win[2], [p[0] for p in providers])
    return out


def pack_path(ns, name):
    return os.path.join(PACK_DATA, ns, "structure", "multiblocks", *name.split("/")) + ".nbt"


def canon_for(block, known):
    if block in CANON:
        return CANON[block]
    for rx, rep in GENERIC:
        if rx.match(block):
            cand = rx.sub(rep, block)
            if cand in known:
                return cand
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--mods", default=MODS, help="mods folder of the server whose jars hold the templates (a copy)")
    a = ap.parse_args()
    off = tag_ids(DISABLED_TAG) | tag_ids(HIDDEN_TAG)
    with open(REGISTRY, encoding="utf-8") as f:
        reg = json.load(f)
    known = set(reg["blocks"]) | set(reg["items"])
    templates = effective_templates(a.mods)
    if not templates:
        sys.exit(f"no multiblock templates in {a.mods}")
    stale, unmapped, wanted, replaced = [], [], set(), 0
    for (ns, name), (jar, data, providers) in sorted(templates.items()):
        raw = gzip.decompress(data)
        palette = sorted({p["Name"] for p in _nbt(data)["palette"]})
        if a.list:
            print(f"{ns}:{name} from {jar}{' (over ' + ', '.join(p for p in providers if p != jar) + ')' if len(providers) > 1 else ''}: "
                  f"{', '.join(palette)}")
        swap = {}
        for b in palette:
            if b in off:
                rep = canon_for(b, known)
                if rep is None:
                    unmapped.append(f"{ns}:{name}: {b}")
                else:
                    swap[b] = rep
        if not swap:
            continue
        new = raw
        for old, rep in swap.items():
            new = new.replace(b"\x08\x00\x04Name" + nbt_str(old), b"\x08\x00\x04Name" + nbt_str(rep))
        names = {p["Name"] for p in _nbt(gzip.compress(new))["palette"]}
        assert new != raw and not names & swap.keys(), (ns, name, names)
        replaced += 1
        path = pack_path(ns, name)
        wanted.add(os.path.normcase(path))
        current = None
        if os.path.isfile(path):
            with open(path, "rb") as f:
                current = gzip.decompress(f.read())
        rel = os.path.relpath(path, REPO).replace(os.sep, "/")
        if current == new:
            print(f"up to date: {rel} ({jar})")
            continue
        stale.append(rel)
        if not a.check:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(gzip.compress(new, mtime=0))
            print(f"wrote {rel} from {jar} ({', '.join(f'{k} -> {v}' for k, v in swap.items())})")
    extra = [os.path.relpath(p, REPO).replace(os.sep, "/") for p in glob.glob(os.path.join(PACK_DATA, "*", "structure", "multiblocks", "**", "*.nbt"),
                                                          recursive=True) if os.path.normcase(p) not in wanted]
    print(f"{len(templates)} templates scanned, {replaced} need a pack copy")
    if unmapped:
        print("disabled or hidden blocks without a canonical replacement (add to CANON):\n  " + "\n  ".join(unmapped))
        return 1
    if extra:
        print(f"pack copies no longer needed: {extra}")
    if a.check and (stale or extra):
        print(f"stale or missing: {stale}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
