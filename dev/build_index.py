"""Rebuild index.toml and the [index] hash in pack.toml, like `packwiz refresh`.

Walks the repo root, skips paths matched by packwiz's built-in ignore list and by
.packwizignore (gitignore syntax: '!' negation, trailing '/' = directory only,
leading or inner '/' = anchored to the root, '*', '?', '**'), hashes every file
with sha256 and marks *.pw.toml as metafile.
Usage:  python dev/build_index.py
"""
import hashlib, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "pack.toml")
INDEX_NAME = "index.toml"
# packwiz core/index.go defaults, plus the pack/index files and the ignore file itself
DEFAULT_IGNORE = [".git/**", ".gitattributes", ".gitignore", ".DS_Store", "/*.zip",
                  "*.mrpack", "packwiz.exe", "packwiz"]
ALWAYS_SKIP = {"pack.toml", INDEX_NAME, ".packwizignore"}


def compile_pattern(line):
    neg = line.startswith("!")
    if neg:
        line = line[1:]
    dir_only = line.endswith("/")
    line = line.rstrip("/")
    anchored = line.startswith("/") or "/" in line
    line = line.lstrip("/")
    rx, i = "", 0
    while i < len(line):
        if line.startswith("**/", i):
            rx += "(?:.*/)?"; i += 3
        elif line.startswith("/**", i) and i + 3 == len(line):
            rx += "/.*"; i += 3
        elif line.startswith("**", i):
            rx += ".*"; i += 2
        elif line[i] == "*":
            rx += "[^/]*"; i += 1
        elif line[i] == "?":
            rx += "[^/]"; i += 1
        else:
            rx += re.escape(line[i]); i += 1
    rx = ("^" if anchored else "^(?:.*/)?") + rx + "$"
    return neg, dir_only, re.compile(rx)


def load_patterns():
    lines = list(DEFAULT_IGNORE)
    p = os.path.join(ROOT, ".packwizignore")
    if os.path.exists(p):
        for l in open(p, encoding="utf-8").read().splitlines():
            l = l.strip()
            if l and not l.startswith("#"):
                lines.append(l)
    return [compile_pattern(l) for l in lines]


def ignored(rel, is_dir, pats):
    """gitignore semantics: last matching pattern wins; a file inside an ignored dir is ignored."""
    parts = rel.split("/")
    for n in range(1, len(parts) + 1):
        sub = "/".join(parts[:n])
        sub_is_dir = is_dir or n < len(parts)
        state = False
        for neg, dir_only, rx in pats:
            if dir_only and not sub_is_dir:
                continue
            if rx.match(sub):
                state = not neg
        if state:
            return True
    return False


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def tstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    pats = load_patterns()
    files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel_dir = os.path.relpath(dirpath, ROOT).replace("\\", "/")
        rel_dir = "" if rel_dir == "." else rel_dir + "/"
        dirnames[:] = sorted(d for d in dirnames if not ignored(rel_dir + d, True, pats))
        for fn in filenames:
            rel = rel_dir + fn
            if rel in ALWAYS_SKIP or ignored(rel, False, pats):
                continue
            files.append(rel)
    files.sort()
    out = ['hash-format = "sha256"', ""]
    for rel in files:
        out.append("[[files]]")
        out.append(f"file = {tstr(rel)}")
        out.append(f"hash = {tstr(sha256(os.path.join(ROOT, rel)))}")
        if rel.endswith(".pw.toml"):
            out.append("metafile = true")
        out.append("")
    with open(os.path.join(ROOT, INDEX_NAME), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))
    index_hash = sha256(os.path.join(ROOT, INDEX_NAME))

    pack = open(PACK, encoding="utf-8").read()
    m = re.search(r"(\[index\][^\[]*?^hash = )\"[0-9a-fA-F]*\"", pack, re.S | re.M)
    if not m:
        raise SystemExit("pack.toml has no [index] hash line")
    pack = pack[:m.start()] + m.group(1) + f'"{index_hash}"' + pack[m.end():]
    with open(PACK, "w", encoding="utf-8", newline="\n") as f:
        f.write(pack)
    meta = sum(1 for r in files if r.endswith(".pw.toml"))
    print(f"index.toml: {len(files)} files ({meta} metafiles), sha256 {index_hash}")


if __name__ == "__main__":
    main()
