"""Build the CurseForge upload zip: dev/exports/FirmamentAges-<version>-curseforge-upload.zip

Why not plain `packwiz curseforge export` in the repo: five mods stay Modrinth metafiles in mods/ because their
CurseForge files are excluded from third-party downloads (packwiz-installer could not fetch them for the friends'
Prism instances and the server). Their CurseForge metafiles live in dev/cf/swap/. This script copies the served pack
into dev/exports/cf-work/upload-pack/, replaces each Modrinth metafile whose jar filename matches a swap file by
that CurseForge metafile, refreshes the copy and exports it with `-s both` (server-only mods such as LootJS and
TFC Ruins must be in a CurseForge install, which also runs single player). The manifest is packwiz's own output,
nothing is edited by hand.

Usage: python dev/cf/export_cf.py
Prints the manifest count and every jar that ends up in overrides/ (must be only approved or own jars).
"""
import json
import shutil
import subprocess
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKWIZ = ROOT / "tools" / "packwiz.exe"
SWAP = ROOT / "dev" / "cf" / "swap"
WORK = ROOT / "dev" / "exports" / "cf-work" / "upload-pack"


def main():
    pack = tomllib.loads((ROOT / "pack.toml").read_text(encoding="utf-8"))
    out = ROOT / "dev" / "exports" / f"FirmamentAges-{pack['version']}-curseforge-upload.zip"
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    index = tomllib.loads((ROOT / "index.toml").read_text(encoding="utf-8"))
    for name in ("pack.toml", "index.toml", ".packwizignore"):
        if (ROOT / name).exists():
            shutil.copy2(ROOT / name, WORK / name)
    for f in index["files"]:
        dst = WORK / f["file"]
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / f["file"], dst)
    swapped = []
    for cf in sorted(SWAP.glob("*.pw.toml")):
        cf_data = tomllib.loads(cf.read_text(encoding="utf-8"))
        for mr in (WORK / "mods").glob("*.pw.toml"):
            d = tomllib.loads(mr.read_text(encoding="utf-8"))
            if "modrinth" in d.get("update", {}) and d["filename"] == cf_data["filename"]:
                mr.unlink()
                shutil.copy2(cf, WORK / "mods" / cf.name)
                swapped.append(f"{mr.name} -> {cf.name}")
                break
        else:
            raise SystemExit(f"no Modrinth metafile with filename {cf_data['filename']} for {cf.name}")
    subprocess.run([str(PACKWIZ), "refresh"], cwd=WORK, check=True, capture_output=True)
    if out.exists():
        out.unlink()
    r = subprocess.run([str(PACKWIZ), "curseforge", "export", "-s", "both", "-o", str(out)], cwd=WORK,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(r.stdout + r.stderr)
    z = zipfile.ZipFile(out)
    manifest = json.loads(z.read("manifest.json"))
    jars = sorted(n for n in z.namelist() if n.endswith(".jar"))
    print(f"swapped {len(swapped)}: " + ", ".join(swapped))
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size / 1e6:.1f} MB, manifest files {len(manifest['files'])}, "
          f"jars in overrides {len(jars)}")
    for j in jars:
        print("  " + j)


if __name__ == "__main__":
    main()
