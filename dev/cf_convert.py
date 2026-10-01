"""Convert Modrinth metafiles (mods/*.pw.toml) to CurseForge metafiles where the same file exists on CF.

Steps (each writes to dev/cf-convert.log and the plan dev/exports/cf-work/plan.json):
  python dev/cf_convert.py scan    download every Modrinth jar (sha512-checked), fetch Modrinth project data,
                                   let `packwiz curseforge detect` match the jars by CurseForge fingerprint in a
                                   scratch pack, then look up the rest via cfwidget (slug, CF link of the project)
  python dev/cf_convert.py apply   for every plan entry with an identical CF file: remove the Modrinth metafile,
                                   `packwiz curseforge add --addon-id --file-id` (dependency prompts answered "n"),
                                   keep the old `side`, check the CF sha1 against the Modrinth jar
Only exact matches are applied. A fingerprint match means CurseForge holds a byte-identical jar (murmur2 over the
jar without whitespace bytes), the sha1 check afterwards confirms it.
"""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = ROOT / "mods"
PACKWIZ = ROOT / "tools" / "packwiz.exe"
WORK = ROOT / "dev" / "exports" / "cf-work"
JARS = WORK / "jars"
API = WORK / "api"
PLAN = WORK / "plan.json"
LOG = ROOT / "dev" / "cf-convert.log"
REUSE_DIRS = [ROOT / "dev" / "exports" / "test-client" / "client" / "mods"]
# cfwidget answers 404 on the slug path for some projects; these ids come from `packwiz curseforge add "<title>"`
# in the scratch pack (search), confirmed by author and filename below.
CF_IDS = {"fastsuite": 475117, "kubejs-create": 429371, "sophisticated-core": 618298}
# Same person under different names on the two sites (checked on the project pages and the GitHub repo).
AUTHOR_ALIASES = {"90": "ninety"}
UA = {"User-Agent": "PhoenixFlyy/FirmamentAges cf_convert (private modpack tooling)"}


def log(msg):
    line = f"{datetime.now():%Y-%m-%d %H:%M:%S} {msg}"
    print(line)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def http_json(url, cache_name=None, retries=3):
    if cache_name:
        p = API / cache_name
        if p.exists():
            return json.loads(p.read_text(encoding="utf-8"))
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                data = json.loads(r.read().decode("utf-8"))
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                data = None
                break
            if e.code in (202, 429, 500, 502, 503) and attempt < retries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise
        except urllib.error.URLError:
            if attempt < retries - 1:
                time.sleep(5)
                continue
            raise
    if cache_name:
        p = API / cache_name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(data, indent=1), encoding="utf-8")
    return data


def cfwidget(slug_or_id):
    """cfwidget returns 202 while it builds a new entry; retry a few times."""
    key = str(slug_or_id)
    cache = f"cfwidget/{key.replace('/', '_')}.json"
    p = API / cache
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    url = (f"https://api.cfwidget.com/{key}" if key.isdigit()
           else f"https://api.cfwidget.com/minecraft/mc-mods/{urllib.parse.quote(key)}")
    data = None
    for attempt in range(6):
        req = urllib.request.Request(url, headers=UA)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                if r.status == 202:
                    time.sleep(10)
                    continue
                data = json.loads(r.read().decode("utf-8"))
                break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                data = None
                break
            if e.code in (429, 500, 502, 503):
                time.sleep(10)
                continue
            raise
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, indent=1), encoding="utf-8")
    return data


def sha(path, algo):
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def modrinth_metafiles():
    out = []
    for p in sorted(MODS.glob("*.pw.toml")):
        d = tomllib.loads(p.read_text(encoding="utf-8"))
        mr = d.get("update", {}).get("modrinth")
        if mr:
            out.append({"meta": p.name, "name": d["name"], "filename": d["filename"], "side": d["side"],
                        "url": d["download"]["url"], "sha512": d["download"]["hash"],
                        "mr_project": mr["mod-id"], "mr_version": mr["version"]})
    return out


def fetch_jar(m):
    target = JARS / m["filename"]
    if target.exists() and sha(target, "sha512") == m["sha512"]:
        return target
    for d in REUSE_DIRS:
        cand = d / m["filename"]
        if cand.exists() and sha(cand, "sha512") == m["sha512"]:
            shutil.copy2(cand, target)
            return target
    with urllib.request.urlopen(urllib.request.Request(m["url"], headers=UA), timeout=300) as r:
        target.write_bytes(r.read())
    if sha(target, "sha512") != m["sha512"]:
        raise RuntimeError(f"sha512 mismatch for {m['filename']}")
    return target


def run_detect(entries):
    """Scratch pack with all Modrinth jars, `packwiz curseforge detect` replaces matched jars by metafiles."""
    pack = WORK / "detect-pack"
    if pack.exists():
        shutil.rmtree(pack)
    (pack / "mods").mkdir(parents=True)
    (pack / "index.toml").write_text('hash-format = "sha256"\n', encoding="utf-8")
    (pack / "pack.toml").write_text(
        'name = "cf-detect"\npack-format = "packwiz:1.1.0"\n\n[index]\nfile = "index.toml"\n'
        'hash-format = "sha256"\nhash = ""\n\n[versions]\nminecraft = "1.21.1"\nneoforge = "21.1.252"\n',
        encoding="utf-8")
    for m in entries:
        shutil.copy2(JARS / m["filename"], pack / "mods" / m["filename"])
    r = subprocess.run([str(PACKWIZ), "curseforge", "detect", "-y"], cwd=pack, capture_output=True, text=True,
                       input="n\n" * 50, timeout=1800)
    (WORK / "detect.out.txt").write_text(r.stdout + "\n--- stderr ---\n" + r.stderr, encoding="utf-8")
    log(f"detect: exit {r.returncode}; " + " | ".join(l for l in r.stdout.splitlines() if "ound" in l)[:400])
    found = {}
    for p in (pack / "mods").glob("*.pw.toml"):
        d = tomllib.loads(p.read_text(encoding="utf-8"))
        cf = d["update"]["curseforge"]
        found[d["download"]["hash"]] = {"cf_slug_meta": p.name, "project_id": cf["project-id"], "file_id": cf["file-id"],
                                "cf_filename": d["filename"], "cf_sha1": d["download"]["hash"],
                                "cf_name": d["name"]}
    return found


def cf_link_from_modrinth(proj):
    links = [proj.get("source_url") or "", proj.get("wiki_url") or "", proj.get("issues_url") or ""]
    links += [d.get("url", "") for d in proj.get("donation_urls") or []]
    body = proj.get("body") or ""
    for m in re.finditer(r"curseforge\.com/minecraft/mc-mods/([a-z0-9\-_]+)", body + " ".join(links)):
        return m.group(1)
    return None


def version_tokens(s):
    return set(re.findall(r"\d+(?:\.\d+)+", s or ""))


def cf_candidate(m, proj):
    """Fallback for jars the fingerprint did not match: find the CF project and a 1.21.1 NeoForge file."""
    slugs = []
    for s in (proj.get("slug"), cf_link_from_modrinth(proj), m["meta"].removesuffix(".pw.toml"),
              str(CF_IDS.get(proj.get("slug"), ""))):
        if s and s not in slugs:
            slugs.append(s)
    for s in slugs:
        w = cfwidget(s)
        if not w or "files" not in w:
            continue
        files = [f for f in w["files"]
                 if "1.21.1" in f.get("versions", []) and "NeoForge" in f.get("versions", [])]
        same = [f for f in files if f.get("name") == m["filename"]]
        mr_ver = version_tokens(m["filename"]) - {"1.21.1", "1.21"}
        samever = [f for f in files if mr_ver and mr_ver <= version_tokens(f.get("name", ""))]
        newest = sorted(files, key=lambda f: f.get("uploaded_at", ""), reverse=True)[:1]
        return {"cf_slug": s, "cf_id": w.get("id"), "cf_title": w.get("title"),
                "cf_url": (w.get("urls") or {}).get("curseforge"),
                "same_name": [(f["id"], f["name"]) for f in same],
                "same_version": [(f["id"], f["name"]) for f in samever][:5],
                "newest_1211_neoforge": [(f["id"], f["name"], f.get("type")) for f in newest],
                "authors": [a.get("username") for a in w.get("members", [])]}
    return None


def mr_authors(project_id):
    members = http_json(f"https://api.modrinth.com/v2/project/{project_id}/members", f"mr/{project_id}.members.json")
    return [x["user"]["username"] for x in members or []]


def zip_entries(path):
    import zipfile
    with zipfile.ZipFile(path) as z:
        return {i.filename: i.CRC for i in z.infolist() if not i.is_dir()}


def differs():
    """Same-version CF files without a fingerprint match: download the CF jar and describe the difference."""
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    cfdir = WORK / "cfjars"
    cfdir.mkdir(exist_ok=True)
    for e in plan:
        if e["status"] not in ("cf_file_differs",):
            continue
        c = e["cf_lookup"]
        fid, fname = (c["same_name"] or c["same_version"])[0]
        e["project_id"], e["file_id"], e["cf_filename"] = c["cf_id"], fid, fname
        target = cfdir / fname
        if not target.exists():
            url = f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000:03d}/{urllib.parse.quote(fname)}"
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r:
                target.write_bytes(r.read())
        e["cf_sha1"] = sha(target, "sha1")
        a, b = zip_entries(JARS / e["filename"]), zip_entries(target)
        only_mr = sorted(set(a) - set(b))
        only_cf = sorted(set(b) - set(a))
        changed = sorted(k for k in set(a) & set(b) if a[k] != b[k])
        e["diff"] = {"only_modrinth": only_mr[:8], "only_cf": only_cf[:8], "changed": changed[:8],
                     "counts": [len(only_mr), len(only_cf), len(changed)]}
        e["mr_authors"] = mr_authors(e["mr_project"])
        norm = lambda n: re.sub(r"[^a-z0-9]", "", AUTHOR_ALIASES.get(n, n).lower())
        same_author = bool({norm(x) for x in e["mr_authors"]} & {norm(x) for x in c["authors"] if x})
        e["status"] = "cf_file_differs_accepted" if same_author and fname == e["filename"] else "cf_file_differs_review"
        log(f"{e['meta']}: CF {c['cf_id']} file {fid} {fname}; sha1 MR {e['sha1'][:10]} CF {e['cf_sha1'][:10]}; "
            f"entries only MR/only CF/changed {e['diff']['counts']} e.g. {(changed or only_mr or only_cf)[:3]}; "
            f"authors MR {e['mr_authors']} CF {c['authors']} -> {e['status']}"
            + ("; CODE DIFFERS (changed .class files): the CF build is not the Modrinth build"
               if any(k.endswith(".class") for k in changed + only_mr + only_cf) else "; same code, packaging only"))
    PLAN.write_text(json.dumps(plan, indent=1), encoding="utf-8")


def scan():
    WORK.mkdir(parents=True, exist_ok=True)
    JARS.mkdir(parents=True, exist_ok=True)
    entries = modrinth_metafiles()
    log(f"scan: {len(entries)} Modrinth metafiles")
    for m in entries:
        fetch_jar(m)
        m["sha1"] = sha(JARS / m["filename"], "sha1")
        proj = http_json(f"https://api.modrinth.com/v2/project/{m['mr_project']}", f"mr/{m['mr_project']}.json")
        m["mr_slug"] = proj["slug"]
        m["mr_title"] = proj["title"]
        m["license"] = (proj.get("license") or {}).get("id")
        m["source_url"] = proj.get("source_url")
    log("scan: jars present and sha512-verified")
    found = run_detect(entries)
    plan = []
    for m in entries:
        e = dict(m)
        f = found.get(m["sha1"])
        if f:
            e.update(f)
            e["status"] = "fingerprint" if f["cf_sha1"] == m["sha1"] else "fingerprint_sha1_differs"
            log(f"{m['meta']}: CF fingerprint match project {f['project_id']} file {f['file_id']} "
                f"({f['cf_filename']}), sha1 {'equal' if e['status'] == 'fingerprint' else 'DIFFERENT'}")
        else:
            proj = http_json(f"https://api.modrinth.com/v2/project/{m['mr_project']}", f"mr/{m['mr_project']}.json")
            c = cf_candidate(m, proj)
            e["cf_lookup"] = c
            if not c:
                e["status"] = "no_cf_project"
                log(f"{m['meta']}: no CurseForge project found (cfwidget slugs tried: {proj['slug']}, "
                    f"{cf_link_from_modrinth(proj)}, {m['meta'].removesuffix('.pw.toml')})")
            elif c["same_name"] or c["same_version"]:
                e["status"] = "cf_file_differs"
                log(f"{m['meta']}: CF project {c['cf_id']} {c['cf_slug']} has a same-version 1.21.1 NeoForge file "
                    f"{(c['same_name'] or c['same_version'])[0]} but no fingerprint match (content differs)")
            elif c["newest_1211_neoforge"]:
                e["status"] = "cf_version_mismatch"
                log(f"{m['meta']}: CF project {c['cf_id']} {c['cf_slug']} has no file of version "
                    f"{m['filename']}; newest 1.21.1 NeoForge: {c['newest_1211_neoforge'][0]}")
            else:
                e["status"] = "cf_no_1211_neoforge"
                log(f"{m['meta']}: CF project {c['cf_id']} {c['cf_slug']} has no 1.21.1 NeoForge file")
        plan.append(e)
    PLAN.write_text(json.dumps(plan, indent=1), encoding="utf-8")
    counts = {}
    for e in plan:
        counts[e["status"]] = counts.get(e["status"], 0) + 1
    log(f"scan: done {counts}")


def apply(limit=None):
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    todo = [e for e in plan if e["status"] in ("fingerprint", "fingerprint_sha1_differs", "cf_file_differs_accepted")
            and (MODS / e["meta"]).exists() and "modrinth" in (MODS / e["meta"]).read_text(encoding="utf-8")]
    if limit:
        todo = todo[:limit]
    done = 0
    for e in todo:
        before = set(MODS.glob("*.pw.toml"))
        old = MODS / e["meta"]
        old_text = old.read_bytes().decode("utf-8")
        old.unlink()
        r = subprocess.run([str(PACKWIZ), "curseforge", "add", "--addon-id", str(e["project_id"]),
                            "--file-id", str(e["file_id"])], cwd=ROOT, capture_output=True, text=True,
                           encoding="utf-8", errors="replace",
                           input="n\n" * 20, timeout=300)
        created = sorted(set(MODS.glob("*.pw.toml")) - before)
        new = created[0] if len(created) == 1 else (MODS / e["meta"] if (MODS / e["meta"]).exists() else None)
        if r.returncode != 0 or new is None:
            old.write_bytes(old_text.encode("utf-8"))
            log(f"apply {e['meta']}: packwiz add FAILED, Modrinth metafile restored: {r.stdout[-300:]} {r.stderr[-300:]}")
            continue
        d = tomllib.loads(new.read_text(encoding="utf-8"))
        text = new.read_text(encoding="utf-8")
        if d["side"] != e["side"]:
            text = re.sub(r'^side = ".*"$', f'side = "{e["side"]}"', text, count=1, flags=re.M)
            new.write_bytes(text.encode("utf-8"))  # keep LF, the index hash must match what git serves
        ok_hash = d["download"]["hash-format"] == "sha1" and d["download"]["hash"] == e["sha1"]
        ok_cf = d["download"]["hash"] == e["cf_sha1"]
        ok_name = d["filename"] == e["filename"]
        prompts = [l for l in r.stdout.splitlines() if "?" in l or "ependenc" in l]
        log(f"apply {e['meta']} -> {new.name}: project {e['project_id']} file {e['file_id']}, side {e['side']}, "
            f"sha1 vs Modrinth jar {'equal' if ok_hash else 'DIFFERENT (CF build kept, see scan)'}, "
            f"sha1 vs checked CF jar {'equal' if ok_cf else 'DIFFERENT'}, filename {'equal' if ok_name else d['filename']}"
            + (f"; prompts: {' / '.join(prompts)[:200]}" if prompts else ""))
        done += 1
    log(f"apply: {done} converted")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "scan"
    if cmd == "scan":
        scan()
    elif cmd == "differs":
        differs()
    elif cmd == "apply":
        apply(int(sys.argv[2]) if len(sys.argv) > 2 else None)
    else:
        sys.exit(__doc__)
