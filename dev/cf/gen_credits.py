"""Write the credits section of dev/cf/modpack-description.md from the pack's metafiles.

Every mods/*.pw.toml (with its dev/cf/swap/ twin where one exists) becomes one line: mod name, authors and project link. CurseForge metafiles are looked up on
cfwidget (https://api.cfwidget.com/<project-id>), Modrinth metafiles on the Modrinth API (project + members).
Jars tracked directly in index.toml (firmages-core) are listed from OWN_JARS.
The section between the markers <!-- credits:start --> and <!-- credits:end --> is replaced; everything else in the
description stays as written. Answers are cached in dev/exports/cf-work/api/ (gitignored).

Usage: python dev/cf/gen_credits.py
"""
import json
import re
import time
import tomllib
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODS = ROOT / "mods"
DESC = ROOT / "dev" / "cf" / "modpack-description.md"
CACHE = ROOT / "dev" / "exports" / "cf-work" / "api"
UA = {"User-Agent": "PhoenixFlyy/FirmamentAges gen_credits (private modpack tooling)"}
START, END = "<!-- credits:start -->", "<!-- credits:end -->"
OWN_JARS = {"firmages-core": ("Firmament Ages Core (firmages-core)", "PhoenixFlyy", "part of this pack")}


def get_json(url, cache_name):
    p = CACHE / cache_name
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    data = None
    for attempt in range(8):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202:  # cfwidget is still building the entry
                    time.sleep(8)
                    continue
                data = json.loads(r.read().decode("utf-8"))
                break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                break
            time.sleep(8)
    if data is not None:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(data, indent=1), encoding="utf-8")
    return data


def entries():
    """One row per mod as the CurseForge upload ships it: a Modrinth metafile that has a CurseForge twin in
    dev/cf/swap/ (download-blocked mods, see dev/cf/export_cf.py) is credited with its CurseForge project."""
    swap = {}
    for f in (ROOT / "dev" / "cf" / "swap").glob("*.pw.toml"):
        d = tomllib.loads(f.read_text(encoding="utf-8"))
        swap[d["filename"]] = d
    out = []
    for f in sorted(MODS.glob("*.pw.toml")):
        d = tomllib.loads(f.read_text(encoding="utf-8"))
        d = swap.get(d["filename"], d)
        up = d.get("update", {})
        if "curseforge" in up:
            pid = up["curseforge"]["project-id"]
            w = get_json(f"https://api.cfwidget.com/{pid}", f"cfwidget/{pid}.json")
            if not w:
                raise SystemExit(f"cfwidget has no entry for {f.name} ({pid})")
            authors = [m["username"] for m in w.get("members", []) if m.get("username")]
            url = (w.get("urls") or {}).get("curseforge") or f"https://www.curseforge.com/projects/{pid}"
            out.append((w.get("title") or d["name"], authors, "CurseForge", url))
        elif "modrinth" in up:
            mid = up["modrinth"]["mod-id"]
            p = get_json(f"https://api.modrinth.com/v2/project/{mid}", f"mr/{mid}.json")
            members = get_json(f"https://api.modrinth.com/v2/project/{mid}/members", f"mr/{mid}.members.json") or []
            owners = sorted(members, key=lambda m: (m.get("role") != "Owner", m.get("ordering", 0)))
            out.append((p["title"], [m["user"]["username"] for m in owners], "Modrinth",
                        f"https://modrinth.com/mod/{p['slug']}"))
    index = tomllib.loads((ROOT / "index.toml").read_text(encoding="utf-8"))
    for f in index["files"]:
        if f["file"].startswith("mods/") and f["file"].endswith(".jar"):
            key = re.sub(r"-\d.*$", "", Path(f["file"]).stem)
            title, author, link = OWN_JARS[key]
            out.append((title, [author], None, link))
    return out


def render(rows):
    rows = sorted(rows, key=lambda r: r[0].lower())
    lines = [START, "", f"Firmament Ages {pack_version()} contains {len(rows)} mods. Thank you to every author.", ""]
    for title, authors, site, url in rows:
        by = ", ".join(authors) if authors else "unknown"
        link = f"[{site}]({url})" if site else url
        lines.append(f"- **{title}** by {by} ({link})")
    lines += ["", END]
    return "\n".join(lines)


def pack_version():
    return tomllib.loads((ROOT / "pack.toml").read_text(encoding="utf-8"))["version"]


def main():
    rows = entries()
    text = DESC.read_text(encoding="utf-8")
    if START not in text or END not in text:
        raise SystemExit(f"markers {START} / {END} missing in {DESC}")
    head, rest = text.split(START, 1)
    _, tail = rest.split(END, 1)
    DESC.write_bytes((head + render(rows) + tail).encode("utf-8"))
    sites = {s: sum(1 for r in rows if r[2] == s) for s in ("CurseForge", "Modrinth", None)}
    print(f"{len(rows)} mods written to {DESC.relative_to(ROOT)}: {sites}")


if __name__ == "__main__":
    main()
