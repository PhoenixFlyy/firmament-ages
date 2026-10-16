#!/usr/bin/env python3
"""Parity checks for the TFCRegistryAPI scan scope (firmages-core SPEC 20, config perf.limitTfcRegistryApiScan).

TFCRegistryAPI 1.2 loads every class of every mod seven times at start to find enums of seven registry interfaces.
firmages-core limits that scan to mod files that are TFC or TFCRegistryAPI or declare a dependency on one of them.
Two checks show that nothing the API finds is lost:

  jars <mods-dir>
      Static: reads every mod jar (and its jar-in-jar files) of a pack install, applies the same rule as
      dev.firmages.core.perf.RegistryScanScope (neoforge.mods.toml: [[mods]] modId, [[dependencies.<mod>]] modId) and
      walks all class files. Every class that implements one of the seven interfaces, directly or through other
      interfaces, is listed with its jar; for a mixin that adds such an interface, also the enums it names. FAIL when
      one of them lies in a jar outside the scope. With --dump <fa_dump dir> also: the TFCAstikorCarts items of that
      dump (one per part and wood) must match the constants of the wood enums found, one to one.

  dump <before> <after>
      Diffs two /fa_dump outputs (registries.json; a directory or the file itself; recipes.json next to it when
      present). Every key both files have is compared: item, block, fluid and, with the dump.js after pack 0.8.0,
      every other built-in registry (entity_type, recipe_type, ...) and "tfcregistryapi_enums" (the enum values the
      API registered, "<category> <mod> <class>.<VALUE>"). Keys only one dump has are listed, not compared. Then the
      focus checks: TFCAstikorCarts ids per part and wood (none lost; when "after" has tfcregistryapi_enums, every
      wood enum value has every part), TFCRegrowingForests (tfcrf) registry ids and regrowth recipes.
      For a like-for-like dump, boot the same build twice at the same Ages with perf.limitTfcRegistryApiScan = false
      (before) and true (after); the 0.8.0 dump (local/firmages/ in test-server) has only item, block and fluid.

Usage:  python dev/check_registry_parity.py jars "C:/Users/Felix/curseforge/minecraft/Instances/Firmament Ages/mods"
            [--dump test-server/local/firmages]
        python dev/check_registry_parity.py dump before/local/firmages after/local/firmages [--show 30]
Exit code: 0 = parity, 1 = a difference or an enum outside the scope, 2 = bad input.
"""
import argparse
import collections
import gzip
import io
import json
import os
import re
import struct
import sys
import tomllib
import zipfile

ROOTS = {"tfc", "tfcregistryapi"}
INTERFACES = {
    "net/dries007/tfc/util/registry/RegistryMetal": "metal",
    "tfcregistryapi/util/registry/RegistryOre": "ore",
    "net/dries007/tfc/util/registry/RegistryRock": "rock",
    "tfcregistryapi/util/registry/RegistrySand": "sand",
    "tfcregistryapi/util/registry/RegistrySoil": "soil",
    "net/dries007/tfc/util/registry/RegistrySoilVariant": "soil_variant",
    "net/dries007/tfc/util/registry/RegistryWood": "wood",
}
ACC_INTERFACE = 0x0200
ACC_ENUM = 0x4000


# ---- jars ------------------------------------------------------------------------------------------------------------

def parse_class(data):
    """(name, super, interfaces, access, class-descriptor strings, enum constant names) of a class file, or None."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    count = struct.unpack_from(">H", data, 8)[0]
    pos, i = 10, 1
    utf8, classes, descs = {}, {}, set()
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack_from(">H", data, pos + 1)[0]
            s = data[pos + 3:pos + 3 + n].decode("utf-8", "replace")
            utf8[i] = s
            pos += 3 + n
        elif tag == 7:
            classes[i] = struct.unpack_from(">H", data, pos + 1)[0]
            pos += 3
        elif tag in (8, 16, 19, 20):
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        else:
            return None
        i += 1
    access, this_i, super_i, n_if = struct.unpack_from(">HHHH", data, pos)
    pos += 8
    ifaces = [utf8[classes[struct.unpack_from(">H", data, pos + 2 * k)[0]]] for k in range(n_if)]
    pos += 2 * n_if
    constants = []
    n_fields = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    for _ in range(n_fields):
        f_access, f_name, _, n_attr = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        for _ in range(n_attr):
            pos += 6 + struct.unpack_from(">I", data, pos + 2)[0]
        if f_access & ACC_ENUM:
            constants.append(utf8[f_name])
    for s in utf8.values():
        if s.startswith("L") and s.endswith(";") and "/" in s and ";" not in s[:-1]:
            descs.add(s[1:-1])
    return utf8[classes[this_i]], (utf8[classes[super_i]] if super_i else None), ifaces, access, descs, constants


def read_mods_toml(zf):
    """[(modId, [dependency modIds])] of a jar, [] for a library jar, None when the file cannot be parsed."""
    try:
        raw = zf.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
    except KeyError:
        return []
    try:
        cfg = tomllib.loads(raw)
    except tomllib.TOMLDecodeError:
        return None
    deps = cfg.get("dependencies", {}) or {}
    out = []
    for mod in cfg.get("mods", []) or []:
        mod_id = str(mod.get("modId", ""))
        entries = deps.get(mod_id, []) or []
        if isinstance(entries, dict):
            entries = [entries]
        out.append((mod_id, [str(d.get("modId", "")) for d in entries if isinstance(d, dict)]))
    return out


def walk_jar(label, zf, jars):
    """Collects mods and classes of a jar and of every jar-in-jar file into jars[label]."""
    mods = read_mods_toml(zf)
    classes = {}
    for name in zf.namelist():
        if name.endswith(".class"):
            parsed = parse_class(zf.read(name))
            if parsed:
                classes[parsed[0]] = (name,) + parsed[1:]
        elif name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(zf.read(name))) as inner:
                walk_jar(label + "!/" + name, inner, jars)
    jars[label] = {"mods": mods, "classes": classes}


def in_scope(mods):
    if mods is None:
        return True  # unreadable mods.toml: counted as scanned, listed as a warning
    return any(mod_id in ROOTS or ROOTS.intersection(deps) for mod_id, deps in mods)


def cmd_jars(args):
    if not os.path.isdir(args.mods_dir):
        print(f"not a directory: {args.mods_dir}")
        return 2
    jars = {}
    for fn in sorted(os.listdir(args.mods_dir)):
        if fn.endswith(".jar"):
            with zipfile.ZipFile(os.path.join(args.mods_dir, fn)) as zf:
                walk_jar(fn, zf, jars)
    index = {}  # class -> (jar, path, super, interfaces, access, descs, enum constants)
    for jar, info in jars.items():
        for cls, (path, sup, ifaces, access, descs, constants) in info["classes"].items():
            index.setdefault(cls, (jar, path, sup, ifaces, access, descs, constants))

    memo = {}

    def reaches(cls, seen=()):
        """The target interfaces a class or interface implements through its interfaces and superclasses."""
        if cls in INTERFACES:
            return {cls}
        if cls in memo:
            return memo[cls]
        if cls in seen or cls not in index:
            return set()
        _, _, sup, ifaces, _, _, _ = index[cls]
        out = set()
        for parent in ifaces + ([sup] if sup else []):
            out |= reaches(parent, seen + (cls,))
        memo[cls] = out
        return out

    mod_jars = {j: i for j, i in jars.items() if i["mods"]}
    selected = {j for j, i in mod_jars.items() if in_scope(i["mods"])}
    unreadable = [j for j, i in jars.items() if i["mods"] is None]
    print(f"{len(jars)} jars ({len(mod_jars) + len(unreadable)} mod jars, {len(jars) - len(mod_jars) - len(unreadable)} libraries "
          f"that are not in ModList and were never scanned), {len(index)} classes")
    print(f"scope: {len(selected)} mod jars -> " + ", ".join(sorted(
        "+".join(m for m, _ in mod_jars[j]["mods"]) if j in mod_jars else j for j in selected)))
    for j in unreadable:
        print(f"WARN  {j}: neoforge.mods.toml does not parse; counted as in scope")

    hits = []  # (category, jar, class, kind, note)
    for cls, (jar, path, sup, ifaces, access, descs, _) in sorted(index.items()):
        if access & ACC_INTERFACE:
            continue
        targets = reaches(cls)
        if not targets:
            continue
        cats = ",".join(sorted(INTERFACES[t] for t in targets))
        lower = path.lower()
        if "mixin" in lower:
            named = sorted(d for d in descs if d in index and index[d][4] & ACC_ENUM and index[d][2] == "java/lang/Enum")
            hits.append((cats, jar, cls, "mixin", named))
            for enum in named:
                hits.append((cats, index[enum][0], enum, "mixin target", [cls]))
        elif access & ACC_ENUM and sup == "java/lang/Enum":
            hits.append((cats, jar, cls, "enum, skipped" if "client" in lower else "enum", []))  # the API skips "client" paths
        else:
            hits.append((cats, jar, cls, "not an enum", []))  # the API keeps only enums

    outside = 0
    print()
    print(f"{'interfaces':<22} {'kind':<14} {'in scope':<8} class (jar)")
    for cats, jar, cls, kind, extra in hits:
        top = jar.split("!/")[0]
        scoped = jar in selected or (jar not in mod_jars and top in selected)
        # A jar-in-jar library has no ModList entry of its own: the API only reaches it through the outer jar's file
        # system, which does not open nested jars, so it is never scanned either way.
        bad = kind in ("enum", "mixin target") and not scoped and jar in mod_jars
        outside += bad
        note = f" -> {', '.join(extra)}" if extra else ""
        print(f"{cats:<22} {kind:<14} {'yes' if scoped else 'NO':<8} {cls.replace('/', '.')} ({jar}){note}"
              f"{'  <-- OUTSIDE' if bad else ''}")
    per_cat = collections.Counter()
    for cats, jar, cls, kind, extra in hits:
        if kind in ("enum", "mixin target"):
            for c in cats.split(","):
                per_cat[c] += 1
    print()
    print("enums per interface: " + ", ".join(f"{c} {per_cat[c]}" for c in sorted(set(INTERFACES.values()))))
    woods = {c.lower() for cats, jar, cls, kind, _ in hits if kind == "enum" and "wood" in cats.split(",") for c in index[cls][6]}
    print(f"wood enum constants: {len(woods)}")
    if args.dump:
        try:
            _, dump, _ = load_dump(args.dump)
        except FileNotFoundError as e:
            print(f"missing: {e}")
            return 2
        cart_woods = carts(dump.get("item", []))
        for part in sorted(cart_woods):
            same = cart_woods[part] == woods
            outside += not same
            print(f"{'PASS' if same else 'FAIL'}  tfcastikorcarts:{part}/<wood> in {args.dump}: {len(cart_woods[part])} woods"
                  + ("" if same else f"; no enum {sorted(cart_woods[part] - woods)}; no item {sorted(woods - cart_woods[part])}"))
    if outside:
        print(f"FAIL  {outside} problem(s): an enum outside the scope or a cart part that does not match the woods")
        return 1
    print("PASS  every enum the full scan can find lies in a jar of the scope")
    return 0


# ---- dump ------------------------------------------------------------------------------------------------------------

def _open_json(path):
    """A dump file, plain or gzip'd (the baseline in dev/data/baseline-<version>/ is kept as .json.gz)."""
    if path.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8")
    return open(path, encoding="utf-8")


def _dump_file(folder, name):
    plain = os.path.join(folder, name)
    return plain if os.path.isfile(plain) or not os.path.isfile(plain + ".gz") else plain + ".gz"


def load_dump(path):
    reg = path if path.endswith((".json", ".json.gz")) else _dump_file(path, "registries.json")
    if not os.path.isfile(reg):
        raise FileNotFoundError(reg)
    with _open_json(reg) as f:
        data = json.load(f)
    recipes = None
    rec = _dump_file(os.path.dirname(reg), "recipes.json")
    if os.path.isfile(rec):
        with _open_json(rec) as f:
            r = json.load(f)
        cols = r["columns"]
        recipes = {row[cols.index("id")]: row[cols.index("type")] for row in r["recipes"]}
    return reg, data, recipes


def flatten(value):
    if isinstance(value, dict):
        return {f"{k} {v}" for k, vs in value.items() for v in (vs if isinstance(vs, list) else [vs])}
    return set(map(str, value))


def show(label, ids, limit):
    if not ids:
        return
    by_ns = collections.Counter(i.split(":")[0] if ":" in i else i.split(" ")[0] for i in ids)
    print(f"  {label} {len(ids)}: " + ", ".join(f"{ns} {n}" for ns, n in by_ns.most_common(12)))
    for i in sorted(ids)[:limit]:
        print(f"    {i}")
    if len(ids) > limit:
        print(f"    ... {len(ids) - limit} more")


CART_RE = re.compile(r"^tfcastikorcarts:([a-z_]+)/([a-z_]+)$")


def carts(items):
    """{part: {wood}} of the TFCAstikorCarts items that end in a wood name."""
    out = collections.defaultdict(set)
    for i in items:
        m = CART_RE.match(i)
        if m:
            out[m.group(1)].add(m.group(2))
    return out


def cmd_dump(args):
    try:
        a_path, a, a_rec = load_dump(args.before)
        b_path, b, b_rec = load_dump(args.after)
    except FileNotFoundError as e:
        print(f"missing: {e}")
        return 2
    print(f"before: {a_path}")
    print(f"after:  {b_path}")
    diffs = 0
    only_a = sorted(set(a) - set(b))
    only_b = sorted(set(b) - set(a))
    if only_a:
        print(f"keys only before (not compared): {', '.join(only_a)}")
    if only_b:
        print(f"keys only after (not compared): {', '.join(only_b)}")
    for key in sorted(set(a) & set(b)):
        sa, sb = flatten(a[key]), flatten(b[key])
        added, removed = sb - sa, sa - sb
        status = "same" if not added and not removed else "DIFF"
        diffs += bool(added or removed)
        print(f"{status:<5} {key}: {len(sa)} -> {len(sb)}")
        show("removed", removed, args.show)
        show("added", added, args.show)

    print()
    items_a, items_b = set(a.get("item", [])), set(b.get("item", []))
    enums = b.get("tfcregistryapi_enums", {}).get("wood")
    woods = {v.rsplit(".", 1)[-1].lower() for v in enums} if enums else set()
    ca, cb = carts(items_a), carts(items_b)
    print(f"TFCAstikorCarts: {sum(map(len, ca.values()))} -> {sum(map(len, cb.values()))} items, "
          + (f"{len(woods)} wood enum values in 'after'" if enums else "no tfcregistryapi_enums in 'after': only losses are checked"))
    for part in sorted(set(ca) | set(cb)):
        lost, missing = ca.get(part, set()) - cb.get(part, set()), woods - cb.get(part, set())
        ok = not lost and not missing
        diffs += not ok
        print(f"  {'ok' if ok else 'FAIL':<4} {part}: {len(ca.get(part, set()))} -> {len(cb.get(part, set()))} woods"
              + (f"; lost {sorted(lost)}" if lost else "") + (f"; woods without it {sorted(missing)}" if missing else ""))

    # only registries both dumps have (a dump.js before pack 0.8.1 wrote item, block and fluid only)
    tfcrf = lambda d: {f"{k} {v}" for k, vs in d.items() if k in a and k in b and isinstance(vs, list)
                       for v in vs if str(v).startswith("tfcrf:")}
    ra, rb = tfcrf(a), tfcrf(b)
    print(f"TFCRegrowingForests registry ids: {len(ra)} -> {len(rb)}" + ("" if ra == rb else
          f"  DIFF; removed {sorted(ra - rb)}; added {sorted(rb - ra)}"))
    diffs += ra != rb
    if a_rec is not None and b_rec is not None:
        ga = {i for i, t in a_rec.items() if t.startswith("tfcrf:")}
        gb = {i for i, t in b_rec.items() if t.startswith("tfcrf:")}
        print(f"TFCRegrowingForests recipes: {len(ga)} -> {len(gb)}" + ("" if ga == gb else "  DIFF"))
        show("removed", ga - gb, args.show)
        show("added", gb - ga, args.show)
        diffs += ga != gb
    print()
    if diffs:
        print(f"FAIL  {diffs} difference(s)")
        return 1
    print("PASS  no difference in the compared keys")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    j = sub.add_parser("jars", help="static scan of a mods folder")
    j.add_argument("mods_dir")
    j.add_argument("--dump", help="also compare the TFCAstikorCarts woods of this fa_dump with the wood enum constants")
    d = sub.add_parser("dump", help="diff two fa_dump outputs")
    d.add_argument("before")
    d.add_argument("after")
    d.add_argument("--show", type=int, default=30)
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    return cmd_jars(args) if args.cmd == "jars" else cmd_dump(args)


if __name__ == "__main__":
    sys.exit(main())
