#!/usr/bin/env python3
"""Compare an installed pack (or an export) against index.toml for one side.

Expected set for side S: every index.toml entry whose side is "both" or S (plain files count as "both").
A metafile (*.pw.toml) maps to <its folder>/<filename>, checked by the hash in its [download] table;
a plain file maps to its own path, checked by its sha256 in index.toml.

Usage:
  python dev/verify_install.py dir    <install-folder> --side client|server
  python dev/verify_install.py mrpack <file.mrpack>    --side client|server
  python dev/verify_install.py cfzip  <file.zip>                        (client side)
Exit code 0 = no mismatch, 1 = mismatch.
"""
import argparse
import hashlib
import json
import os
import sys
import tomllib
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Files packwiz-installer itself leaves in the install folder.
INSTALLER_FILES = {"packwiz-installer-bootstrap.jar", "packwiz-installer.jar", "packwiz.json"}


def expected(side):
    """{path: (hash_format, hash, cf_file_id or None)} for the side."""
    with open(os.path.join(REPO, "index.toml"), "rb") as f:
        index = tomllib.load(f)
    fmt_default = index.get("hash-format", "sha256")
    out = {}
    for e in index["files"]:
        path = e["file"]
        if e.get("metafile"):
            with open(os.path.join(REPO, path), "rb") as f:
                meta = tomllib.load(f)
            if meta.get("side", "both") not in ("both", side):
                continue
            dl = meta["download"]
            cf = meta.get("update", {}).get("curseforge", {}).get("file-id")
            out[os.path.dirname(path) + "/" + meta["filename"]] = (dl["hash-format"], dl["hash"], cf)
        else:
            out[path] = (e.get("hash-format", fmt_default), e["hash"], None)
    return out


def digest(fmt, data):
    if fmt == "murmur2":  # CurseForge fingerprint, not checked here
        return None
    return hashlib.new(fmt, data).hexdigest()


def compare(exp, actual, label):
    """actual: {path: bytes-reader callable or {fmt: hash}}"""
    missing = sorted(set(exp) - set(actual))
    extra = sorted(set(actual) - set(exp))
    bad = []
    for p in sorted(set(exp) & set(actual)):
        fmt, want, _ = exp[p]
        got = actual[p]
        if isinstance(got, dict):  # {fmt: hash} from an export manifest
            if fmt in got and got[fmt] != want:
                bad.append(p)
            continue
        h = digest(fmt, got())
        if h is not None and h != want:
            bad.append(p)
    print(f"{label}: expected {len(exp)}, found {len(actual)}, missing {len(missing)}, extra {len(extra)}, hash mismatch {len(bad)}")
    for tag, items in (("missing", missing), ("extra", extra), ("hash", bad)):
        for p in items:
            print(f"  {tag}: {p}")
    return len(missing) + len(extra) + len(bad)


def read_file(path):
    return lambda: open(path, "rb").read()


def scan_dir(root):
    actual = {}
    for d, _, files in os.walk(root):
        for n in files:
            rel = os.path.relpath(os.path.join(d, n), root).replace(os.sep, "/")
            if rel in INSTALLER_FILES:
                continue
            actual[rel] = read_file(os.path.join(d, n))
    return actual


def scan_mrpack(path, side):
    z = zipfile.ZipFile(path)
    idx = json.loads(z.read("modrinth.index.json"))
    actual = {}
    for f in idx["files"]:
        if f.get("env", {}).get(side, "required") == "unsupported":
            continue
        actual[f["path"]] = f["hashes"]
    for prefix in ("overrides/", f"{side}-overrides/"):
        for n in z.namelist():
            if n.startswith(prefix) and not n.endswith("/"):
                actual[n[len(prefix):]] = (lambda name=n: z.read(name))
    return actual


def scan_cfzip(path, exp):
    z = zipfile.ZipFile(path)
    manifest = json.loads(z.read("manifest.json"))
    by_cf = {v[2]: p for p, v in exp.items() if v[2] is not None}
    actual = {}
    for f in manifest["files"]:
        p = by_cf.get(f["fileID"], f"<cf file {f['projectID']}/{f['fileID']}>")
        actual[p] = {}  # no hash in the manifest
    for n in z.namelist():
        if n.startswith("overrides/") and not n.endswith("/"):
            actual[n[len("overrides/"):]] = (lambda name=n: z.read(name))
    return actual


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("kind", choices=("dir", "mrpack", "cfzip"))
    ap.add_argument("path")
    ap.add_argument("--side", choices=("client", "server"), default="client")
    a = ap.parse_args()
    exp = expected(a.side)
    if a.kind == "dir":
        actual = scan_dir(a.path)
    elif a.kind == "mrpack":
        actual = scan_mrpack(a.path, a.side)
    else:
        actual = scan_cfzip(a.path, exp)
    n = compare(exp, actual, f"{a.kind} {os.path.basename(a.path)} ({a.side})")
    sys.exit(1 if n else 0)


if __name__ == "__main__":
    main()
