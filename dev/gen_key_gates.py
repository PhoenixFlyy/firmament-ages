#!/usr/bin/env python3
"""Firmament Ages - key and screen gates per mod Age (SPEC 18.10), generated from the pack's client jars.

Why: Felix 2026-10-17 (round G2): "C opens the Psi menu although Psi is locked; every mod shortcut must be gated."
A key of a mod whose Age the world has not reached does nothing, and a screen of such a mod does not open. The
client half is firmages-core's immersion/KeyGate and client/immersion/KeyGateClient; this script writes the table they
read.

What it reads, for every jar in the given mod folders (and every jar-in-jar, recursively; dev/check_mixin_refs.py's
jar loader):
  1. the mod ids of the jar (META-INF/neoforge.mods.toml); a jar-in-jar library without one belongs to its outer jar;
  2. the key mappings it registers: the name a class passes to `new KeyMapping(...)` (or to a subclass of KeyMapping
     any jar defines), read from the bytecode with this script's own class-file parser (no javap needed), and the
     key-like entries of its assets/<ns>/lang/en_us.json (`key.<mod>.*`, `<mod>.key.*`, `<mod>.keyinfo.*`,
     `<mod>.keybind.*`, `keyinfo.<mod>.*`; every `key.*` entry for a jar family that builds key names at run time,
     e.g. "key.ae2." + id), which catches the names a helper builds;
  3. the Java packages of its classes, as prefixes (grouped by their first part, by the first two below a reverse
     domain such as com or net, each group cut to its longest common package); a screen belongs to the mod with the
     longest prefix its class name starts with.

The Age of a mod: its row in dev/age_map.toml [mods]; else the earliest Age tag (kubejs/data/firmages/tags/item/
age_items/<age>.json) that holds an item of its namespace; else, for a jar-in-jar mod that never ships as a jar of
its own, the earliest Age of the jars that carry it (flightlib in Create Jetpack: age_4), but none (never locked) as
soon as one carrier is never locked itself (no Age, Dawn or exempt: a library of an open mod must not shut it); else
none.
EXEMPT lists the mods that are never locked, each with its reason; a mod whose Age is Dawn is never locked either.
NEVER_OPEN lists the mods that no Age opens (Age "never" in the pack file), each with its reason; creative mode has
them all the same.
Round G2 looked for locked screens that stand in for a vanilla one: in 1.0.2 only Apothic Enchanting's enchanting
screens (the enchanting table is disabled), Create's Pojav warning and a Stargate Journey confirm dialog subclass a
vanilla screen other than Screen and AbstractContainerScreen.

Outputs
  dev/data/key_gates.json                 the inventory: every key found (mod, Age, jar, how it was found, exemption),
                                          every package prefix, the exemptions and the counts
  kubejs/assets/firmages/key_gates.json   what the client reads (pack file, so it changes without a core build):
                                          {"mods": {mod: age}, "keys": {key name: mod}, "packages": {prefix: mod},
                                          "open_packages": [prefix]}, only mods that can be locked (an Age after
                                          Dawn and not exempt); open_packages are the packages of never-locked mods
                                          below a locked prefix, where the class lookup stops

Usage
  python dev/gen_key_gates.py --mods <folder> [--mods <folder2>]     scan and write both files
  python dev/gen_key_gates.py --mods <folder> --check                 exit 1 if either file differs from a new scan
  python dev/gen_key_gates.py                                         without jars: both files again from the stored
                                                                      inventory and today's dev/age_map.toml (after an
                                                                      Age change); with --check, exit 1 if they differ
  python dev/gen_key_gates.py --mods <folder> --options <options.txt> also compare with the key names a real client
                                                                      saved (prints the differences, writes nothing new)
The client set: C:/Users/Felix/curseforge/minecraft/Instances/Firmament Ages/mods (the server pack lacks client mods).
Exit code: 0 ok, 1 out of date, 2 bad input.
"""
import argparse
import json
import os
import re
import struct
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_mixin_refs import PLATFORM_PREFIXES, load_jars  # noqa: E402  (same folder)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AGE_MAP = os.path.join(ROOT, "dev", "age_map.toml")
TAG_DIR = os.path.join(ROOT, "kubejs", "data", "firmages", "tags", "item", "age_items")
OUT_DEV = os.path.join(ROOT, "dev", "data", "key_gates.json")
OUT_PACK = os.path.join(ROOT, "kubejs", "assets", "firmages", "key_gates.json")
AGES = ["dawn"] + [f"age_{i}" for i in range(10)]

KEY_MAPPING = "net/minecraft/client/KeyMapping"

# Never locked (Felix's list, round G2), each with its reason. Mods without an Age are never locked anyway; they are
# listed so the table says why.
EXEMPT = {
    "minecraft": "vanilla keys",
    "neoforge": "platform",
    "controlling": "client tool: the key binding screen",
    "fancymenu": "client tool: menus",
    "sodium": "client tool: video settings",
    "iris": "client tool: shaders (O opens the shader menu)",
    "drippyloadingscreen": "client tool: loading screen",
    "chat_heads": "client tool",
    "skinlayers3d": "client tool",
    "dynamic_fps": "client tool",
    "mousetweaks": "client tool",
    "emotecraft": "cosmetic emotes (round G2, branch g2/anim): the emote wheel key.emotecraft.fastchoose on Y and "
                  "key.emotecraft.stop are there in every Age",
    "notenoughanimations": "cosmetic animations, client only (round G2, branch g2/anim)",
    "ok_zoomer": "client tool (Felix 2026-10-18): zoom key.ok_zoomer.zoom on C in every Age (replaced WI Zoom, no third-party download)",
    "jade": "own gate: Jade's overlay by Age (SPEC 18.3)",
    "emi": "own gate: the recipe viewer by Age (SPEC 18.3, 21.3)",
    "jei": "own gate: JEI's recipe screen shuts with EMI's (SPEC 18.3, 21.3)",
    "journeymap": "own gate: MapGate (SPEC 18.2)",
    "curios": "own gate: InventoryButtonGate (SPEC 18.2)",
    "ftbquests": "the Chronicle (K) is there from Dawn",
    "ftbchunks": "its stage ftbchunks_mapping gates the map: before age_2 the map key does nothing "
                 "(FTBChunksClient.keyPressed returns pass without playerHasMapStage)",
    "sophisticatedbackpacks": "the backpack key opens only a worn or carried backpack (an item from the Bronze Age)",
    "sophisticatedcore": "sort and transfer keys act only inside a Sophisticated storage screen (the backpack's)",
    "betterf3": "own veil: the F3 screen before the map's Age (SPEC 18.11)",
    "exposure": "the camera keys act only with the camera item",
    "immersiveengineering": "the magnet key acts only with the magnet item",
}

# Locked in every Age (Age "never" in the pack file), each with its reason; creative mode has them all the same.
NEVER_OPEN = {
    "progressivestages": "the stage tree (key.progressivestages.open_tree, unbound by default; StageTreeScreen, also "
                         "behind /stage gui) lists every stage and what it unlocks ahead of time (round G2 integration)",
}

# Lang entries that name no key mapping although they look like one (descriptions and tooltips of a key).
LANG_SKIP_SUFFIX = (".desc", ".tooltip", ".info", ".outline", ".hint", ".tip")
CATEGORY_RE = re.compile(r"^key\.categor(y|ies)\.|\.categor(y|ies)$|\.categor(y|ies)\.|^key\.category_")
PLATFORM_DOTTED = tuple(p.replace("/", ".") for p in PLATFORM_PREFIXES)
# A literal key name: two or more dot-separated parts (format strings such as "key.kubejs.%s" are no name).
KEY_NAME_RE = re.compile(r"^[A-Za-z0-9_\-]+(\.[A-Za-z0-9_\-]+)+$")

# ------------------------------------------------------------------------------------------------ class files

# Bytes per instruction, opcode included, for the fixed-size instructions (the rest are 1 byte).
_LEN = [1] * 256
for _op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
    _LEN[_op] = 2
for _op in (0x11, 0x13, 0x14, 0x84, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6,
            0xa7, 0xa8, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7):
    _LEN[_op] = 3
_LEN[0xc5] = 4
for _op in (0xb9, 0xba, 0xc8, 0xc9):
    _LEN[_op] = 5


class ClassFile:
    """The parts of a class file this script needs: name, super class, constant pool and the Code of each method."""

    def __init__(self, data):
        if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
            raise ValueError("no class file magic")
        count = struct.unpack_from(">H", data, 8)[0]
        pos = 10
        cp = [None] * count
        i = 1
        while i < count:
            tag = data[pos]
            if tag == 1:
                n = struct.unpack_from(">H", data, pos + 1)[0]
                cp[i] = (1, data[pos + 3:pos + 3 + n].decode("utf-8", "replace"))
                pos += 3 + n
            elif tag in (3, 4):
                cp[i] = (tag,)
                pos += 5
            elif tag in (5, 6):
                cp[i] = (tag,)
                pos += 9
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = (tag, struct.unpack_from(">H", data, pos + 1)[0])
                pos += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                cp[i] = (tag,) + struct.unpack_from(">HH", data, pos + 1)
                pos += 5
            elif tag == 15:
                cp[i] = (15,)
                pos += 4
            else:
                raise ValueError(f"unknown constant pool tag {tag}")
            i += 1
        self.cp = cp
        _access, this_c, super_c = struct.unpack_from(">HHH", data, pos)
        pos += 6
        self.name = self.cls(this_c)
        self.super = self.cls(super_c)
        n_if = struct.unpack_from(">H", data, pos)[0]
        pos += 2 + 2 * n_if
        pos, _ = self._members(data, pos, False)
        _, self.codes = self._members(data, pos, True)

    def utf8(self, ix):
        e = self.cp[ix] if ix else None
        return e[1] if e and e[0] == 1 else None

    def cls(self, ix):
        e = self.cp[ix] if ix else None
        return self.utf8(e[1]) if e and e[0] == 7 else None

    def string(self, ix):
        e = self.cp[ix] if ix else None
        return self.utf8(e[1]) if e and e[0] == 8 else None

    def _members(self, data, pos, want_code):
        n = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        codes = []
        for _ in range(n):
            _acc, _name, _desc, n_attr = struct.unpack_from(">HHHH", data, pos)
            pos += 8
            for _ in range(n_attr):
                a_name, a_len = struct.unpack_from(">HI", data, pos)
                pos += 6
                if want_code and self.utf8(a_name) == "Code":
                    code_len = struct.unpack_from(">I", data, pos + 4)[0]
                    codes.append(data[pos + 8:pos + 8 + code_len])
                pos += a_len
        return pos, codes


def instructions(code):
    pc = 0
    while pc < len(code):
        op = code[pc]
        if op == 0xaa:  # tableswitch
            p = (pc + 4) & ~3
            lo, hi = struct.unpack_from(">ii", code, p + 4)
            n = p + 12 + 4 * (hi - lo + 1) - pc
        elif op == 0xab:  # lookupswitch
            p = (pc + 4) & ~3
            n = p + 8 + 8 * struct.unpack_from(">i", code, p + 4)[0] - pc
        elif op == 0xc4:  # wide
            n = 6 if code[pc + 1] == 0x84 else 4
        else:
            n = _LEN[op]
        yield op, code[pc:pc + n]
        pc += n


def constructions(cf, wanted):
    """Every `new X(...)` with X in `wanted` (a predicate on internal class names): the literal String passed as the
    first argument (pushed right after `new X; dup`), or None when the first argument is computed."""
    out = []
    for code in cf.codes:
        pending = []  # [class, state]: "NEW" after new, "DUP" after its dup, then the first argument or None
        for op, ins in instructions(code):
            if pending and pending[-1][1] in ("NEW", "DUP"):
                top = pending[-1]
                if top[1] == "NEW":
                    top[1] = "DUP" if op == 0x59 else None
                    if op == 0x59:
                        continue
                elif op in (0x12, 0x13):  # ldc, ldc_w
                    top[1] = cf.string(ins[1] if op == 0x12 else struct.unpack_from(">H", ins, 1)[0])
                else:
                    top[1] = None
            if op == 0xbb:  # new
                pending.append([cf.cls(struct.unpack_from(">H", ins, 1)[0]), "NEW"])
            elif op == 0xb7:  # invokespecial
                e = cf.cp[struct.unpack_from(">H", ins, 1)[0]]
                if e and e[0] == 10 and pending:
                    owner = cf.cls(e[1])
                    if cf.utf8(cf.cp[e[2]][1]) == "<init>" and pending[-1][0] == owner:
                        cls, state = pending.pop()
                        if wanted(cls):
                            out.append(state if state not in ("NEW", "DUP") else None)
    return out

# ------------------------------------------------------------------------------------------------ scan


class JarInfo:
    def __init__(self, jar):
        self.label = jar.label
        self.jar_name = jar.file_name()
        self.outer = jar.label.split("!/")[0]
        self.parent = jar.label.rsplit("!/", 1)[0] if "!/" in jar.label else None
        self.mods = self._mods(jar)
        self.classes = {}  # internal name -> ClassFile (only classes that construct something are kept)
        self.supers = {}
        self.packages = set()
        self.langs = {}
        for c in jar.classes:
            path = c + ".class"
            if path not in jar.names:
                continue  # multi-release entry only
            try:
                cf = ClassFile(jar.read(path))
            except (ValueError, struct.error, IndexError):
                continue
            self.supers[cf.name] = cf.super
            if cf.codes:
                self.classes[cf.name] = cf
            if "/" in cf.name:
                self.packages.add(cf.name.rsplit("/", 1)[0].replace("/", "."))
        for n in jar.names:
            m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
            if m:
                try:
                    data = json.loads(jar.read(n).decode("utf-8-sig", "replace"))
                except ValueError:
                    continue
                if isinstance(data, dict):
                    self.langs[m.group(1)] = [k for k in data if isinstance(k, str)]

    @staticmethod
    def _mods(jar):
        for n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if n in jar.names:
                try:
                    t = tomllib.loads(jar.read(n).decode("utf-8", "replace"))
                except (tomllib.TOMLDecodeError, UnicodeDecodeError):
                    return []
                return [m["modId"] for m in t.get("mods", []) if isinstance(m, dict) and m.get("modId")]
        return []


# First package parts that name no author or mod on their own (reverse domains): a jar's classes are grouped by the
# first two parts below them, by the first part otherwise.
GENERIC_ROOTS = {"com", "net", "org", "io", "dev", "me", "de", "fr", "ru", "uk", "be", "nl", "it", "pl", "cz", "at", "ch",
                 "ca", "au", "gg", "xyz", "top", "tech", "plus", "systems", "earth", "info", "cc", "co", "app", "edu"}


def _common(members):
    common = members[0]
    for m in members[1:]:
        n = 0
        while n < min(len(common), len(m)) and common[n] == m[n]:
            n += 1
        common = common[:n]
    return common


def package_prefixes(packages):
    """The jar's packages as prefixes: grouped by their first part (by the first two below a reverse-domain part such
    as com or net), each group cut to its longest common package. Platform packages (Minecraft, NeoForge, the JDK and
    NeoForge's libraries) are left out."""
    groups = {}
    for p in packages:
        if (p + ".").startswith(PLATFORM_DOTTED) or p.startswith("architectury_inject_"):
            continue  # platform packages; Architectury's one-class build stubs
        parts = p.split(".")
        if parts[0] in GENERIC_ROOTS:
            if len(parts) < 2:
                continue
            key = tuple(parts[:2])
        else:
            key = (parts[0],)
        groups.setdefault(key, []).append(parts)
    return {".".join(_common(members)) for members in groups.values()}


def mod_of_jar(info, by_label):
    """The mod a jar's keys and packages belong to: its own (first) mod id, else that of the jar that carries it."""
    j = info
    while j is not None:
        if j.mods:
            return j.mods[0]
        j = by_label.get(j.parent) if j.parent else None
    return None


def family(info, by_label):
    """The jar, the jars it carries and the jar that carries it (key helpers live in nested libraries)."""
    root = info
    while root.parent and root.parent in by_label:
        root = by_label[root.parent]
    return [j for j in by_label.values() if j.label == root.label or j.label.startswith(root.label + "!/")]


def scan(folders):
    jars, errors = load_jars(folders)
    for e in errors:
        print(f"warning: {e}", file=sys.stderr)
    infos = [JarInfo(j) for j in jars]
    by_label = {i.label: i for i in infos}
    supers = {}
    for i in infos:
        supers.update(i.supers)

    km_cache = {}

    def is_key_mapping(name):
        if name in km_cache:
            return km_cache[name]
        seen, c, ok = set(), name, False
        while c and c not in seen:
            if c == KEY_MAPPING:
                ok = True
                break
            seen.add(c)
            c = supers.get(c)
        km_cache[name] = ok
        return ok

    all_mod_ids = {m for i in infos for m in i.mods}
    keys = {}      # name -> {mod, jar, source}
    dynamic_jars = set()
    for i in infos:
        for cf in i.classes.values():
            for name in constructions(cf, is_key_mapping):
                if name is None:
                    dynamic_jars.add(i.label)
                elif KEY_NAME_RE.match(name):
                    keys.setdefault(name, {"mod": mod_of_jar(i, by_label), "jar": i.jar_name, "source": "class"})

    for i in infos:
        fam = family(i, by_label)
        fam_ids = {m for j in fam for m in j.mods} | {ns for j in fam for ns in j.langs}
        fam_dynamic = any(j.label in dynamic_jars for j in fam)
        for ns, entries in i.langs.items():
            tokens = fam_ids | {ns}
            for k in entries:
                if k in keys or k.endswith(LANG_SKIP_SUFFIX) or CATEGORY_RE.search(k):
                    continue
                parts = k.split(".")
                if len(parts) < 3:
                    continue
                shaped = (parts[0] == "key" and (parts[1] in tokens or parts[1] in all_mod_ids)) \
                    or (parts[0] in tokens and parts[1] in ("key", "keyinfo", "keybind", "keybinds", "keys")) \
                    or (parts[0] == "keyinfo" and parts[1] in tokens)
                if shaped or (fam_dynamic and parts[0] == "key"):
                    mod = ns if ns in i.mods else mod_of_jar(i, by_label)
                    keys[k] = {"mod": mod, "jar": i.jar_name, "source": "lang"}

    packages = {}  # prefix -> set of mods
    for i in infos:
        mod = i.mods[0] if i.mods else None
        if mod is None:
            continue  # a library without a mod id: its classes are no mod's screens
        for p in package_prefixes(i.packages):
            packages.setdefault(p, set()).add(mod)

    # jar-in-jar mod -> mods of the jars that carry it; a mod that also ships as a jar of its own is a shared library
    # (cloth_config, architectury) and takes no carrier's Age
    top_level = {m for i in infos if not i.parent for m in i.mods}
    parents = {}
    for i in infos:
        if i.parent and i.mods and not set(i.mods) & top_level:
            outer = mod_of_jar(by_label[i.parent], by_label) if i.parent in by_label else None
            if outer:
                for m in i.mods:
                    parents.setdefault(m, set()).add(outer)
    return keys, packages, parents, all_mod_ids

# ------------------------------------------------------------------------------------------------ Ages


def load_ages():
    with open(AGE_MAP, "rb") as f:
        mods = tomllib.load(f).get("mods", {})
    first_item = {}
    for age in AGES:
        path = os.path.join(TAG_DIR, f"{age}.json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for v in data.get("values", []):
            item = v["id"] if isinstance(v, dict) else v
            first_item.setdefault(item.split(":", 1)[0], age)
    return mods, first_item


def age_of(mod, mods, first_item, parents, seen=()):
    """[mods] row, else the earliest Age tag with an item of the namespace, else the earliest Age of a carrying jar;
    none when a carrier is never locked (no Age, Dawn or exempt)."""
    if mod is None:
        return None, "no mod"
    if mod in NEVER_OPEN:
        return "never", "NEVER_OPEN"
    if mod in mods:
        return mods[mod], "[mods]"
    if mod in first_item:
        return first_item[mod], "items"
    best, open_by = None, []
    for p in sorted(parents.get(mod, ())):
        if p in seen:
            continue
        a, _ = age_of(p, mods, first_item, parents, seen + (mod,))
        if a is None or a == "dawn" or p in EXEMPT:
            open_by.append(p)
        elif a in AGES and (best is None or AGES.index(a) < AGES.index(best)):
            best = a
    if open_by:
        return None, "carried by " + ", ".join(open_by) + " (never locked)"
    return (best, "carried by " + ", ".join(sorted(parents[mod]))) if best else (None, "none")


def lockable(mod, age):
    return mod not in EXEMPT and age is not None and age != "dawn"

# ------------------------------------------------------------------------------------------------ outputs


def ancestors(prefix):
    """The shorter packages of a package on whole parts, longest first: a.b.c -> a.b, a."""
    parts = prefix.split(".")
    return [".".join(parts[:i]) for i in range(len(parts) - 1, 0, -1)]


def build(keys, packages, parents, mods_rows, first_item):
    mod_ages = {}
    for mod in sorted({v["mod"] for v in keys.values() if v["mod"]} | {m for ms in packages.values() for m in ms}):
        mod_ages[mod] = age_of(mod, mods_rows, first_item, parents)

    inv_keys = {}
    for name in sorted(keys):
        v = keys[name]
        age, why = mod_ages.get(v["mod"], (None, "no mod"))
        row = {"mod": v["mod"], "age": age, "age_from": why, "jar": v["jar"], "found_in": v["source"]}
        if v["mod"] in EXEMPT:
            row["exempt"] = EXEMPT[v["mod"]]
        inv_keys[name] = row

    inv_packages = {}
    conflicts = {}
    for prefix in sorted(packages):
        ms = sorted(packages[prefix])
        if len(ms) > 1:
            conflicts[prefix] = ms
        # several mods in one package: the earliest Age wins (a screen never shuts too early); an exempt one keeps it open
        def rank(m):
            a = mod_ages[m][0]
            return (0 if m in EXEMPT else 1, AGES.index(a) if a in AGES else (len(AGES) if a else -1))
        mod = min(ms, key=rank)
        inv_packages[prefix] = {"mod": mod, "age": mod_ages[mod][0]}

    gated_mods = {m: a for m, (a, _) in mod_ages.items() if lockable(m, a)}
    pack = {
        "_comment": "Generated by dev/gen_key_gates.py (inventory: dev/data/key_gates.json). Read by firmages-core "
                    "(SPEC 18.10): before a mod's Age a key it registers does nothing and a screen of its packages does "
                    "not open, except in creative mode; Age never: no Age opens it (NEVER_OPEN in the script). Edit an "
                    "Age here or in dev/age_map.toml [mods] and rerun.",
        "mods": dict(sorted(gated_mods.items())),
        "keys": {k: v["mod"] for k, v in inv_keys.items() if v["mod"] in gated_mods},
        "packages": {p: v["mod"] for p, v in inv_packages.items() if v["mod"] in gated_mods},
    }
    # a package of a never-locked mod below a locked prefix would shut with it (the lookup takes the longest listed
    # prefix): listed as open, the lookup stops there
    pack["open_packages"] = sorted(p for p, v in inv_packages.items() if v["mod"] not in gated_mods
                                   and any(a in pack["packages"] for a in ancestors(p)))
    counts = {
        "keys_found": len(inv_keys),
        "keys_locked": len(pack["keys"]),
        "keys_exempt": sum(1 for v in inv_keys.values() if "exempt" in v),
        "keys_never_locked": sum(1 for v in inv_keys.values() if "exempt" not in v and v["mod"] not in gated_mods),
        "mods_with_keys": len({v["mod"] for v in inv_keys.values()}),
        "mods_locked": len(gated_mods),
        "packages": len(inv_packages),
        "packages_locked": len(pack["packages"]),
        "packages_open_below_locked": len(pack["open_packages"]),
    }
    inventory = {
        "_comment": "Generated by dev/gen_key_gates.py from the client jars; do not edit (EXEMPT lives in the script). "
                    "age_from: [mods] row, item tags, or the jars that carry a jar-in-jar mod (carriers).",
        "counts": counts,
        "exempt": dict(sorted(EXEMPT.items())),
        "never_open": dict(sorted(NEVER_OPEN.items())),
        "mods": {m: {"age": a, "age_from": why} for m, (a, why) in mod_ages.items()},
        "carriers": {m: sorted(ps) for m, ps in sorted(parents.items())},
        "keys": inv_keys,
        "packages": inv_packages,
        "package_conflicts": conflicts,
    }
    return inventory, pack


def pack_from_inventory(inventory, mods_rows, first_item):
    """The pack file from a stored inventory and today's dev/age_map.toml (for --check without jars)."""
    keys = {k: {"mod": v["mod"], "jar": v["jar"], "source": v["found_in"]} for k, v in inventory["keys"].items()}
    packages = {p: {v["mod"]} for p, v in inventory["packages"].items()}
    for p, ms in inventory.get("package_conflicts", {}).items():
        packages[p] = set(ms)
    parents = {m: set(ps) for m, ps in inventory.get("carriers", {}).items()}
    return build(keys, packages, parents, mods_rows, first_item)


def dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def write_or_check(path, text, check):
    old = None
    if os.path.exists(path):
        with open(path, encoding="utf-8", newline="") as f:
            old = f.read()
    if old == text:
        return True
    if check:
        print(f"OUT OF DATE: {os.path.relpath(path, ROOT)}")
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"wrote {os.path.relpath(path, ROOT)}")
    return True


def read_options(path):
    names = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("key_"):
                names.add(line[4:].strip().split(":key.", 1)[0])
    return names


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    ap.add_argument("--mods", action="append", default=[], help="a folder of client mod jars (repeatable)")
    ap.add_argument("--check", action="store_true", help="exit 1 if a file is out of date, write nothing")
    ap.add_argument("--options", help="a client's options.txt: list the keys it saved that the scan missed")
    args = ap.parse_args()
    mods_rows, first_item = load_ages()

    if not args.mods:
        if args.options:
            print("error: --options needs --mods <folder>", file=sys.stderr)
            return 2
        if not os.path.exists(OUT_DEV):
            print(f"error: {OUT_DEV} is missing; scan with --mods <client mods>", file=sys.stderr)
            return 2
        with open(OUT_DEV, encoding="utf-8") as f:
            inventory = json.load(f)
        new_inventory, pack = pack_from_inventory(inventory, mods_rows, first_item)
        new_inventory["package_conflicts"] = inventory.get("package_conflicts", {})
        ok = write_or_check(OUT_DEV, dump(new_inventory), args.check)
        ok = write_or_check(OUT_PACK, dump(pack), args.check) and ok
        if args.check:
            print("key gates up to date" if ok else "rerun dev/gen_key_gates.py (Ages) or with --mods <client mods> (mod update)")
        return 0 if ok else 1

    keys, packages, parents, mod_ids = scan(args.mods)
    inventory, pack = build(keys, packages, parents, mods_rows, first_item)
    missing_exempt = sorted(m for m in EXEMPT if m not in mod_ids and m not in ("minecraft", "neoforge"))
    if missing_exempt:
        print("note: exempt mods not in the scanned jars: " + ", ".join(missing_exempt))
    c = inventory["counts"]
    print(f"keys found {c['keys_found']} ({c['mods_with_keys']} mods): locked {c['keys_locked']}, exempt {c['keys_exempt']}, "
          f"never locked (no Age after Dawn) {c['keys_never_locked']}; mods locked {c['mods_locked']}; "
          f"package prefixes {c['packages']} (locked {c['packages_locked']}, shared by several mods "
          f"{len(inventory['package_conflicts'])})")
    if args.options:
        saved = read_options(args.options)
        vanilla = {n for n in saved if n.startswith("key.") and (n.count(".") == 1 or n.startswith("key.hotbar."))}
        missed = sorted(saved - set(inventory["keys"]) - vanilla)
        unsaved = sorted(set(inventory["keys"]) - saved)
        print(f"options.txt: {len(saved)} keys ({len(vanilla)} vanilla); in the scan {len(saved & set(inventory['keys']))}; "
              f"missed by the scan {len(missed)}: {', '.join(missed) or '-'}")
        print(f"scanned names a client never saved (lang entries or keys a mod registers only sometimes): {len(unsaved)}")
        return 0
    ok = write_or_check(OUT_DEV, dump(inventory), args.check)
    ok = write_or_check(OUT_PACK, dump(pack), args.check) and ok
    if args.check:
        print("key gates up to date" if ok else "rerun dev/gen_key_gates.py --mods <client mods>")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
