"""Build the CurseForge upload zip: dev/exports/FirmamentAges-<version>-curseforge-upload.zip

Why not plain `packwiz curseforge export` in the repo: eight mods stay Modrinth metafiles in mods/ because their
CurseForge files are excluded from third-party downloads (packwiz-installer could not fetch them for the friends'
Prism instances and the server). The Complementary Reimagined shader pack stays a Modrinth metafile in shaderpacks/,
because its license forbids a direct file upload and the .mrpack would carry a CurseForge file in overrides/.
Their CurseForge metafiles live in dev/cf/swap/. This script copies the served pack into
dev/exports/cf-work/upload-pack/, replaces each Modrinth metafile in mods/ or shaderpacks/ whose filename matches a
swap file by that CurseForge metafile (same folder), refreshes the copy and exports it with `-s both` (server-only
mods such as LootJS and TFC Ruins must be in a CurseForge install, which also runs single player). The manifest is
packwiz's own output plus one field packwiz cannot write: minecraft.recommendedRam = 8192 (MB). The CurseForge app
reads it into the profile (MinecraftModpackManifestGameSettings.RecommendedRam in Curse.Agent.Host.exe; every
profile's minecraftinstance.json keeps it as manifest.minecraft.recommendedRam) and uses it as the profile's memory
when the profile or the app setting asks for the recommended amount. 8 GB: Modpack-Planung/research-raw/n2-perf.md
(heap 2.96 GB at the menu, up to 4.13 GB in a world).

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
RECOMMENDED_RAM_MB = 8192


def set_recommended_ram(zip_path: Path, mb: int) -> dict:
    """Rewrite the zip with manifest.minecraft.recommendedRam = mb; every other entry is copied unchanged."""
    tmp = zip_path.with_name(zip_path.name + ".tmp")
    with zipfile.ZipFile(zip_path) as src, zipfile.ZipFile(tmp, "w") as dst:
        for info in src.infolist():
            data = src.read(info)
            if info.filename == "manifest.json":
                manifest = json.loads(data)
                manifest["minecraft"]["recommendedRam"] = mb
                data = (json.dumps(manifest, indent=2) + "\n").encode("utf-8")
            dst.writestr(info, data)
    tmp.replace(zip_path)
    return manifest


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
        for mr in sorted((WORK / "mods").glob("*.pw.toml")) + sorted((WORK / "shaderpacks").glob("*.pw.toml")):
            d = tomllib.loads(mr.read_text(encoding="utf-8"))
            if "modrinth" in d.get("update", {}) and d["filename"] == cf_data["filename"]:
                mr.unlink()
                shutil.copy2(cf, mr.parent / cf.name)
                swapped.append(f"{mr.relative_to(WORK).as_posix()} -> {cf.name}")
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
    manifest = set_recommended_ram(out, RECOMMENDED_RAM_MB)
    with zipfile.ZipFile(out) as z:
        jars = sorted(n for n in z.namelist() if n.endswith(".jar"))
    print(f"swapped {len(swapped)}: " + ", ".join(swapped))
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size / 1e6:.1f} MB, manifest files {len(manifest['files'])}, "
          f"recommendedRam {manifest['minecraft']['recommendedRam']} MB, jars in overrides {len(jars)}")
    for j in jars:
        print("  " + j)


if __name__ == "__main__":
    main()
