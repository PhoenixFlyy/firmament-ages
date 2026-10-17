#!/usr/bin/env python3
"""Firmament Ages - static check of every mixin's class references against the pack's jars.

Why: a mod's mixin that names a class another mod renamed or removed crashes the game while it loads (1.0.1: WoodenCog
1.2.19's JEI mixin MixinBasinCategory named net.dries007.tfc.config.TemperatureDisplayStyle, which TerraFirmaCraft
4.2.12 split into ItemTemperatureDisplayStyle and ClimateTemperatureDisplayStyle; the client crashed while JEI was
constructed). Client-only mixins never run on the dedicated server, so a server boot does not show it. This check
reads the jars and needs neither a boot nor javap.

What it does, for every jar in the given mod folders (and every jar-in-jar, recursively: the paths
META-INF/jarjar/metadata.json lists, some under META-INF/jars/, and every jar under META-INF/jarjar/):
  1. collects the classes of all jars (multi-release entries under META-INF/versions/<n>/ count as their class);
  2. finds the jar's mixin configs: the manifest's MixinConfigs, the [[mixins]] config entries of
     META-INF/neoforge.mods.toml (a wrapper jar may name configs of its nested mod jar) and every *.mixins.json / mixins.*.json at the jar root; configs that neither the
     manifest nor neoforge.mods.toml names are never loaded by NeoForge and are only counted (--all-configs checks
     them too);
  3. loads every mixin class of each config (mixins, client and server lists, under the config's package) and reads
     its constant pool with its own classfile parser: every CONSTANT_Class and every class in the descriptors of the
     field and method references, NameAndType entries (invokedynamic included) and MethodType entries;
  4. reports every referenced class that is in no jar of the set and not under a platform prefix (JDK, Minecraft,
     NeoForge and the libraries NeoForge ships, see PLATFORM_PREFIXES), and every mixin class the config lists that
     the jar lacks.

Optional mixins (a config plugin that skips them without their mod, @Pseudo targets, a soft dependency) can show
up although they never load; dev/data/mixin_refs_allow.json lists them per jar (exact file name), each with its
reason (the proof that the mixin is only applied with its target mod). An entry that matched nothing is printed as
stale when its jar was part of the run; allowlisted jars that are not in the run are listed once (after a mod update
the old name no longer matches and its findings come back until the reason is checked on the new jar).

Usage:
  python dev/check_mixin_refs.py --mods <folder> [--mods <folder2>] [--allow dev/data/mixin_refs_allow.json]
         [--all-configs] [--verbose]
  Server jars plus the client-only jars, e.g. a synced server copy's mods/ and a CurseForge instance's mods/.
Exit code: 0 = no missing class, 1 = missing classes or missing mixin classes, 2 = bad input.
"""
import argparse
import fnmatch
import io
import json
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_ALLOW = os.path.join(ROOT, "dev", "data", "mixin_refs_allow.json")

# Classes that are not in the mods folder: the JDK, Minecraft, NeoForge and the libraries the NeoForge 21.1.252
# installer puts into libraries/ (com/electronwill night-config, Guava and Gson, Netty, fastutil, ASM, Mixin, slf4j,
# JOML, LWJGL on the client, Apache Commons and Log4j, modlauncher and securejarhandler under cpw/mods) plus
# MixinExtras, which neoforge-21.1.252-universal.jar carries as META-INF/jarjar/mixinextras-neoforge-0.5.3.jar.
PLATFORM_PREFIXES = (
    "java/", "javax/", "jdk/", "sun/", "com/sun/",
    "net/minecraft/", "com/mojang/", "net/neoforged/",
    "org/spongepowered/", "org/objectweb/", "com/llamalad7/mixinextras/",
    "io/netty/", "com/google/", "org/slf4j/", "org/apache/", "org/joml/", "it/unimi/", "org/lwjgl/",
    "com/electronwill/", "org/jetbrains/", "cpw/mods/",
)

DESC_CLASS_RE = re.compile(r"L([^;<>]+);")


class ClassFileError(Exception):
    pass


def class_refs(data):
    """All class names (internal form) the constant pool of one class file names: CONSTANT_Class entries and the
    classes in the descriptors of NameAndType (field, method and invokedynamic references) and MethodType entries.
    Returns (this_class, set of names)."""
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ClassFileError("no class file magic")
    count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    utf8 = {}
    classes = []      # utf8 index of each CONSTANT_Class
    descs = []        # utf8 index of each descriptor (NameAndType, MethodType)
    class_index = {}  # cp index -> utf8 index, for this_class
    i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:  # Utf8 (modified UTF-8; class names in the pool are plain enough for a lenient decode)
            n = struct.unpack_from(">H", data, pos + 1)[0]
            utf8[i] = data[pos + 3:pos + 3 + n].decode("utf-8", "replace")
            pos += 3 + n
        elif tag in (3, 4):  # Integer, Float
            pos += 5
        elif tag in (5, 6):  # Long, Double take two slots
            pos += 9
            i += 1
        elif tag == 7:  # Class
            idx = struct.unpack_from(">H", data, pos + 1)[0]
            classes.append(idx)
            class_index[i] = idx
            pos += 3
        elif tag in (8, 16, 19, 20):  # String, MethodType, Module, Package
            if tag == 16:
                descs.append(struct.unpack_from(">H", data, pos + 1)[0])
            pos += 3
        elif tag in (9, 10, 11, 17, 18):  # Fieldref, Methodref, InterfaceMethodref, Dynamic, InvokeDynamic
            pos += 5
        elif tag == 12:  # NameAndType: name, descriptor
            descs.append(struct.unpack_from(">H", data, pos + 3)[0])
            pos += 5
        elif tag == 15:  # MethodHandle
            pos += 4
        else:
            raise ClassFileError(f"unknown constant pool tag {tag} at entry {i}")
        i += 1
    # access_flags u2, this_class u2
    this_cp = struct.unpack_from(">H", data, pos + 2)[0]
    this_name = utf8.get(class_index.get(this_cp), "?")
    names = set()
    for idx in classes:
        name = utf8.get(idx, "")
        if name.startswith("["):  # array class: its element type
            names.update(DESC_CLASS_RE.findall(name))
        elif name:
            names.add(name)
    for idx in descs:
        names.update(DESC_CLASS_RE.findall(utf8.get(idx, "")))
    return this_name, names


class Jar:
    """One jar of the set: its display name, classes, mixin configs and the bytes of its entries on demand."""

    def __init__(self, label, data_or_path, outer=None):
        self.label = label          # file name, or outer!/META-INF/jarjar/inner.jar
        self.outer = outer          # file name of the top-level jar
        self.zf = zipfile.ZipFile(data_or_path if isinstance(data_or_path, str) else io.BytesIO(data_or_path))
        self.names = set(self.zf.namelist())
        self.classes = set()
        for n in self.names:
            if not n.endswith(".class"):
                continue
            if n.startswith("META-INF/versions/"):
                parts = n.split("/", 3)
                if len(parts) == 4:
                    n = parts[3]
                else:
                    continue
            self.classes.add(n[:-6])

    def read(self, name):
        return self.zf.read(name)

    def file_name(self):
        return self.label.rsplit("/", 1)[-1]

    def manifest_configs(self):
        if "META-INF/MANIFEST.MF" not in self.names:
            return []
        text = self.read("META-INF/MANIFEST.MF").decode("utf-8", "replace").replace("\r\n", "\n")
        text = text.replace("\n ", "")  # manifest continuation lines
        for line in text.split("\n"):
            if line.startswith("MixinConfigs:"):
                return [c.strip() for c in line.split(":", 1)[1].split(",") if c.strip()]
        return []

    def toml_configs(self):
        name = "META-INF/neoforge.mods.toml"
        if name not in self.names:
            return []
        text = self.read(name).decode("utf-8", "replace")
        out = []
        # [[mixins]] blocks: config = "x.mixins.json" (no TOML library needed for this one key)
        for block in re.split(r"^\s*\[\[", text, flags=re.M)[1:]:
            if not block.startswith("mixins]]"):
                continue
            m = re.search(r'^\s*config\s*=\s*["\']([^"\']+)["\']', block, flags=re.M)
            if m:
                out.append(m.group(1))
        return out

    def root_configs(self):
        out = []
        for n in self.names:
            if "/" in n or not n.endswith(".json"):
                continue
            if n.endswith(".mixins.json") or n.startswith("mixins.") or ".mixins." in n or n.endswith("-mixins.json"):
                out.append(n)
        return sorted(out)

    def nested(self):
        """Jar-in-jar: every path META-INF/jarjar/metadata.json lists (NeoForge's JarJar; some mods keep the jars
        under META-INF/jars/) and every jar directly under META-INF/jarjar/."""
        out = {n for n in self.names if n.startswith("META-INF/jarjar/") and n.endswith(".jar")}
        if "META-INF/jarjar/metadata.json" in self.names:
            try:
                meta = json.loads(self.read("META-INF/jarjar/metadata.json").decode("utf-8-sig"))
                for entry in meta.get("jars") or []:
                    path = entry.get("path")
                    if path in self.names:
                        out.add(path)
            except (ValueError, UnicodeDecodeError, AttributeError):
                pass
        return sorted(out)


def load_jars(folders):
    jars = []
    errors = []

    def add(label, src, outer):
        try:
            jar = Jar(label, src, outer)
        except (zipfile.BadZipFile, OSError) as e:
            errors.append(f"{label}: not a readable jar ({e})")
            return
        jars.append(jar)
        for inner in jar.nested():
            add(f"{label}!/{inner}", jar.read(inner), outer)

    for folder in folders:
        if not os.path.isdir(folder):
            raise SystemExit(f"error: {folder} is not a folder")
        for name in sorted(os.listdir(folder)):
            if name.endswith(".jar"):
                add(name, os.path.join(folder, name), name)
    return jars, errors


def mixin_list(cfg):
    pkg = cfg.get("package") or ""
    out = []
    for side in ("mixins", "client", "server"):
        for name in cfg.get(side) or []:
            if isinstance(name, str):
                out.append((side, f"{pkg}.{name}" if pkg else name))
    return out


def load_allow(path):
    if not path or not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    entries = []
    for pattern, rows in (data.get("jars") or {}).items():
        for row in rows:
            entries.append({"jar": pattern, "mixin": row["mixin"], "missing": row["missing"],
                            "reason": row["reason"], "hits": 0})
    return entries


def matches(pattern, value):
    """Exact name, a prefix ending in '.' or '/', or a glob."""
    if pattern.endswith((".", "/")):
        return value.startswith(pattern)
    return fnmatch.fnmatchcase(value, pattern)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--mods", action="append", required=True, help="mod folder (repeatable)")
    ap.add_argument("--allow", default=DEFAULT_ALLOW, help="allowlist json (default dev/data/mixin_refs_allow.json)")
    ap.add_argument("--all-configs", action="store_true",
                    help="also check mixin configs that neither the manifest nor neoforge.mods.toml names")
    ap.add_argument("--verbose", action="store_true", help="list allowed findings too")
    args = ap.parse_args()

    jars, load_errors = load_jars(args.mods)
    known = set()
    for jar in jars:
        known |= jar.classes
    allow = load_allow(args.allow)

    def present(name):
        return name in known or name.startswith(PLATFORM_PREFIXES)

    findings = []   # (jar label, config, mixin, missing)
    allowed = []
    bad_classes = []
    stats = {"jars": len(jars), "jars_with_mixins": 0, "configs": 0, "configs_unreferenced": 0, "mixins": 0,
             "refs": 0}
    top_level_in_run = {j.outer for j in jars}

    for jar in jars:
        referenced = []
        for c in jar.manifest_configs() + jar.toml_configs():
            if c not in referenced:
                referenced.append(c)
        unreferenced = [c for c in jar.root_configs() if c not in referenced]
        stats["configs_unreferenced"] += len(unreferenced)
        configs = referenced + (unreferenced if args.all_configs else [])
        checked_any = False
        for cname in configs:
            if cname not in jar.names:
                # a wrapper jar (e.g. Sodium's FMLModType LIBRARY jar) names configs its nested mod jar carries;
                # that nested jar is checked on its own
                inner = [j for j in jars if j.label.startswith(jar.label + "!/") and cname in j.names]
                if cname in referenced and not inner:
                    findings.append((jar.label, cname, "-", f"(config file {cname} missing from the jar)"))
                continue
            try:
                cfg = json.loads(jar.read(cname).decode("utf-8-sig"))
            except (ValueError, UnicodeDecodeError) as e:
                bad_classes.append(f"{jar.label}: {cname} is not valid JSON ({e})")
                continue
            if not isinstance(cfg, dict):
                continue
            stats["configs"] += 1
            checked_any = True
            for _side, mixin in mixin_list(cfg):
                stats["mixins"] += 1
                entry = mixin.replace(".", "/") + ".class"
                if entry not in jar.names:
                    findings.append((jar.label, cname, mixin, "(mixin class missing from the jar)"))
                    continue
                try:
                    _this, refs = class_refs(jar.read(entry))
                except (ClassFileError, struct.error, IndexError) as e:
                    bad_classes.append(f"{jar.label}: {entry}: {e}")
                    continue
                stats["refs"] += len(refs)
                for ref in sorted(refs):
                    if present(ref):
                        continue
                    missing = ref.replace("/", ".")
                    hit = None
                    for a in allow:
                        if (fnmatch.fnmatchcase(jar.file_name(), a["jar"]) or fnmatch.fnmatchcase(jar.label, a["jar"])) \
                                and matches(a["mixin"], mixin) and matches(a["missing"], missing):
                            hit = a
                            break
                    if hit:
                        hit["hits"] += 1
                        allowed.append((jar.label, cname, mixin, missing, hit["reason"]))
                    else:
                        findings.append((jar.label, cname, mixin, missing))
        if checked_any:
            stats["jars_with_mixins"] += 1

    folders = ", ".join(args.mods)
    print(f"check_mixin_refs: {folders}")
    print(f"  {stats['jars']} jars (jar-in-jar included), {len(known)} classes; {stats['jars_with_mixins']} jars with "
          f"mixins, {stats['configs']} configs, {stats['mixins']} mixin classes, {stats['refs']} class references "
          f"checked; {stats['configs_unreferenced']} configs named by neither manifest nor neoforge.mods.toml "
          f"{'checked' if args.all_configs else 'skipped'}")
    for e in load_errors + bad_classes:
        print(f"  WARN {e}")
    print(f"  allowed: {len(allowed)} references by {sum(1 for a in allow if a['hits'])} of {len(allow)} allowlist "
          f"entries")
    if args.verbose:
        for jar, cname, mixin, missing, reason in allowed:
            print(f"    {jar} | {cname} | {mixin} -> {missing}  ({reason})")
    stale = [a for a in allow if not a["hits"]
             and any(fnmatch.fnmatchcase(j, a["jar"]) for j in top_level_in_run)]
    for a in stale:
        print(f"  STALE allowlist entry {a['jar']} | {a['mixin']} -> {a['missing']}: matched nothing")
    absent = sorted({a["jar"] for a in allow if not any(fnmatch.fnmatchcase(j, a["jar"]) for j in top_level_in_run)})
    if absent:
        print(f"  allowlist jars not in this run (client-only jars missing, or the mod was updated: check the new "
              f"jar and move its entries): {', '.join(absent)}")
    if findings:
        print(f"  MISSING: {len(findings)}")
        for jar, cname, mixin, missing in findings:
            print(f"    {jar} | {cname} | {mixin} -> {missing}")
        return 1
    print("  MISSING: 0, all mixin class references resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
