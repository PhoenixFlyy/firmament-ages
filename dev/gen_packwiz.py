"""Generate packwiz metafiles for Firmament Ages from the Modrinth API.

No jar is downloaded: hashes, URLs and sizes come from the Modrinth version JSON.
Usage:  python dev/gen_packwiz.py [--refresh]
Writes: mods/*.pw.toml, pack.toml (without index hash; run build_index.py after),
        dev/manifest.csv, dev/cf-pending.md, dev/gen_report.json
"""
import csv, json, os, re, sys, time, urllib.parse, urllib.request, urllib.error
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEV = os.path.join(ROOT, "dev")
MODS = os.path.join(ROOT, "mods")
PLAN_TMP = os.path.join(os.path.dirname(ROOT), "Modpack-Planung", "research-raw", "_tmp")
CACHE = os.path.join(PLAN_TMP, "packwiz_meta_cache.json")
API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "firmament-ages-build/0.1 (private pack)"}
MC, LOADER = "1.21.1", "neoforge"

# Core list: doc 10-Kernmodliste-v3 section 1.1-1.14 (Modrinth part, 135 slugs,
# same list as research-raw/_tmp/v3core_slugs.txt) plus the s2 B additions.
CORE_SLUGS_FILE = os.path.join(PLAN_TMP, "v3core_slugs.txt")
S2B_ADDITIONS = {  # slug -> version named in research-raw/r3-mek-ie.md
    "mekanism-lasers": "1.1.10.3-c",
    "mekanism-neutron-activator": "1.21.1-1.0.0",
    "mekatfc": "0.1.0",
    "mekanismmoremachine": "1.21.1-1.4.1",
}
# Doc section 9.2 libraries (40), slugs as resolved in v3core_out.json deps.
DOC_LIBS = [
    "patchouli", "guideme", "kotlin-for-forge", "placebo", "apothic-attributes",
    "apothic-enchanting", "apothic-spawners", "cucumber", "resourceful-lib",
    "resourceful-config", "common-storage-lib", "smartbrainlib", "curios",
    "modonomicon", "geckolib", "rhino", "better-advanced-tooltips", "balm",
    "sophisticated-core", "searchables", "fzzy-config", "lionfish-api", "corgilib",
    "data-anchor", "supermartijn642s-core-lib", "supermartijn642s-config-lib",
    "architectury-api", "glodium", "create-dragons-plus", "sable", "rpl",
    "mechanicals-lib", "dragonlib", "azimuth-api", "strut-your-stuff",
    "iglee-library", "brandons-core", "codechicken-lib", "cerbons-api", "cloth-config",
]
# CurseForge-only core mods (doc 10 section 9.1).
CF_ONLY = [
    # (name, CF slug, known CF project id or None, doc version); the FTB slug URLs 404 on cfwidget, ids work
    ("FTB Quests", "ftb-quests-forge", 289412, "2101.1.36"),
    ("FTB Library", "ftb-library-forge", 404465, "2101.1.37"),
    ("FTB Teams", "ftb-teams-forge", 404468, "2101.1.11"),
    ("FTB XMod Compat", "ftb-xmod-compat", None, "21.1.12"),
    ("FTB Chunks", "ftb-chunks-forge", 314906, "2101.1.22"),
    ("The Twilight Forest", "the-twilight-forest", 227639, "4.8.3345"),
    ("Torque Link Create To TFC", "torque-link-create-to-tfc", None, "1.1.0"),
    ("AE2 Draconic Fusion Autocrafter", "ae2-draconic-fusion-autocrafter", None, "0.1.6"),
]

_last = [0.0]
cache = {}


def get(url, allow_404=False):
    if url in cache:
        return cache[url]
    for attempt in range(5):
        wait = 0.3 - (time.time() - _last[0])
        if wait > 0:
            time.sleep(wait)
        _last[0] = time.time()
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                body = r.read().decode("utf-8")
                if r.status == 202:  # cfwidget: queued, retry
                    time.sleep(5)
                    continue
                data = json.loads(body) if not url.endswith(".xml") else body
                cache[url] = data
                return data
        except urllib.error.HTTPError as e:
            if e.code == 404 and allow_404:
                cache[url] = None
                return None
            if e.code in (429, 500, 502, 503, 504) and attempt < 4:
                time.sleep(3 * (attempt + 1))
                continue
            raise
    raise RuntimeError("gave up on " + url)


def q(obj):
    return urllib.parse.quote(json.dumps(obj, separators=(",", ":")))


def projects(ids_or_slugs):
    out = {}
    lst = list(ids_or_slugs)
    for i in range(0, len(lst), 80):
        chunk = lst[i:i + 80]
        for p in get(f"{API}/projects?ids={q(chunk)}"):
            out[p["id"]] = p
            out[p["slug"]] = p
    return out


def pick_version(pid):
    vs = get(f"{API}/project/{pid}/version?game_versions={q([MC])}&loaders={q([LOADER])}")
    vs = [v for v in vs if MC in v["game_versions"] and LOADER in v["loaders"]]
    if not vs:
        return None
    vs.sort(key=lambda v: v["date_published"], reverse=True)
    return vs[0]


def primary_file(v):
    files = v["files"]
    return next((f for f in files if f.get("primary")), files[0])


# Server tools that Modrinth flags client-optional; clients have no use for them.
SERVER_ONLY_SLUGS = {"chunky", "simple-backups"}


def side_of(p):
    if p.get("slug") in SERVER_ONLY_SLUGS:
        return "server"
    c, s = p.get("client_side"), p.get("server_side")
    if s == "unsupported" and c != "unsupported":
        return "client"
    if c == "unsupported" and s != "unsupported":
        return "server"
    return "both"


def meta_name(slug):
    return re.sub(r"[^a-z0-9_-]+", "-", slug.lower()).strip("-")


def tstr(s):
    return json.dumps(s, ensure_ascii=False)  # TOML basic string == JSON string for our content


def neoforge_latest():
    xml = get("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
    vers = [e.text for e in ET.fromstring(xml).iter("version")]
    stable = [v for v in vers if v.startswith("21.1.") and re.fullmatch(r"21\.1\.\d+", v)]
    return max(stable, key=lambda v: tuple(int(x) for x in v.split(".")))


def cf_lookup(slug, cfid):
    url = f"https://api.cfwidget.com/{cfid}" if cfid else f"https://api.cfwidget.com/minecraft/mc-mods/{slug}"
    d = get(url, allow_404=True)
    if not d or "files" not in d:
        return None, None
    cands = [f for f in d["files"] if MC in f.get("versions", []) and "NeoForge" in f.get("versions", [])]
    cands.sort(key=lambda f: f["uploaded_at"], reverse=True)
    return d, (cands[0] if cands else None)


def main():
    global cache
    if os.path.exists(CACHE) and "--refresh" not in sys.argv:
        cache = json.load(open(CACHE, encoding="utf-8"))
    try:
        run()
    finally:
        json.dump(cache, open(CACHE, "w", encoding="utf-8"))


def run():
    core = [l.strip() for l in open(CORE_SLUGS_FILE, encoding="utf-8") if l.strip() and not l.startswith("#")]
    doc_raw = json.load(open(os.path.join(PLAN_TMP, "v3core_out.json"), encoding="utf-8"))
    doc_ver = {s: m["chosen"] for s, m in doc_raw["mods"].items()}
    doc_ver.update({d["slug"]: d["chosen"] for d in doc_raw["deps"].values()})
    doc_ver.update(S2B_ADDITIONS)

    wanted = [(s, "mod") for s in core] + [(s, "mod") for s in S2B_ADDITIONS] + [(s, "library") for s in DOC_LIBS]
    pinfo = projects([s for s, _ in wanted])

    entries = {}  # project_id -> dict
    missing_project, missing_on_1211 = [], []
    queue = []
    for slug, kind in wanted:
        p = pinfo.get(slug)
        if not p:
            missing_project.append(slug)
            continue
        queue.append((p["id"], kind, False, "doc"))

    dead_deps, dep_edges, incompat = [], [], []
    while queue:
        pid, kind, auto, via = queue.pop(0)
        if pid in entries:
            continue
        p = pinfo.get(pid) or projects([pid]).get(pid)
        if p is None:
            dead_deps.append({"dep_id": pid, "required_by": via})
            continue
        pinfo[pid] = p
        v = pick_version(pid)
        if v is None:
            missing_on_1211.append({"slug": p["slug"], "title": p["title"], "kind": kind, "required_by": via})
            continue
        entries[pid] = {"p": p, "v": v, "kind": kind, "auto": auto, "via": via}
        for d in v.get("dependencies", []):
            dpid = d.get("project_id")
            if not dpid and d.get("version_id"):
                dv = get(f"{API}/version/{d['version_id']}", allow_404=True)
                dpid = dv["project_id"] if dv else None
            if not dpid:
                continue
            if d["dependency_type"] == "required":
                dep_edges.append((p["slug"], dpid))
                if dpid not in entries:
                    if dpid not in pinfo:
                        r = get(f"{API}/project/{dpid}", allow_404=True)
                        if r is None:
                            dead_deps.append({"dep_id": dpid, "required_by": p["slug"]})
                            continue
                        pinfo[dpid] = r
                    queue.append((dpid, "library", True, p["slug"]))
            elif d["dependency_type"] == "incompatible":
                incompat.append((p["slug"], dpid))

    present = set(entries)
    incompatible_pairs = []
    for a, bid in incompat:
        if bid in present:
            incompatible_pairs.append([a, entries[bid]["p"]["slug"]])

    # Side widening: a library required by a mod that runs on the client (or server)
    # must be present there too, whatever its own Modrinth side flags say.
    side = {pid: side_of(e["p"]) for pid, e in entries.items()}
    slug2pid = {e["p"]["slug"]: pid for pid, e in entries.items()}
    side_overrides = []
    changed = True
    while changed:
        changed = False
        for a_slug, bid in dep_edges:
            a = slug2pid.get(a_slug)
            if a is None or bid not in side:
                continue
            need_c = side[a] in ("both", "client")
            need_s = side[a] in ("both", "server")
            if (need_c and side[bid] == "server") or (need_s and side[bid] == "client"):
                side_overrides.append(f"{entries[bid]['p']['slug']}: {side[bid]} -> both (required by {a_slug}, side {side[a]})")
                side[bid] = "both"
                changed = True

    # write metafiles
    for f in os.listdir(MODS):
        if f.endswith(".pw.toml"):
            os.remove(os.path.join(MODS, f))
    rows = []
    for pid, e in sorted(entries.items(), key=lambda kv: kv[1]["p"]["slug"]):
        p, v = e["p"], e["v"]
        f = primary_file(v)
        side_ = side[pid]
        name = meta_name(p["slug"])
        toml = (
            f"name = {tstr(p['title'])}\n"
            f"filename = {tstr(f['filename'])}\n"
            f"side = {tstr(side_)}\n\n"
            f"[download]\n"
            f"url = {tstr(f['url'])}\n"
            f"hash-format = \"sha512\"\n"
            f"hash = {tstr(f['hashes']['sha512'])}\n\n"
            f"[update]\n"
            f"[update.modrinth]\n"
            f"mod-id = {tstr(pid)}\n"
            f"version = {tstr(v['id'])}\n"
        )
        with open(os.path.join(MODS, name + ".pw.toml"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(toml)
        dv = doc_ver.get(p["slug"])
        rows.append({
            "slug": p["slug"], "project_id": pid, "title": p["title"],
            "version_number": v["version_number"], "version_id": v["id"],
            "channel": v["version_type"], "date_published": v["date_published"][:10],
            "side": side_, "filename": f["filename"], "size_bytes": f["size"],
            "auto_dep": ("yes (via " + e["via"] + ")") if e["auto"] else "",
            "doc_version": "" if dv == v["version_number"] else (dv or "n/a"),
            "kind": e["kind"],
        })

    with open(os.path.join(DEV, "manifest.csv"), "w", encoding="utf-8", newline="") as fh:
        cols = ["slug", "project_id", "title", "version_number", "version_id", "channel",
                "date_published", "side", "filename", "size_bytes", "auto_dep", "doc_version", "kind"]
        w = csv.DictWriter(fh, fieldnames=cols, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)

    # CurseForge-only list
    cf_rows = []
    for title, slug, cfid, docv in CF_ONLY:
        d, f = cf_lookup(slug, cfid)
        cf_rows.append({
            "name": title, "slug": slug, "doc_version": docv,
            "cf_id": d.get("id") if d else None,
            "file": f["name"] if f else None, "file_id": f["id"] if f else None,
            "type": f["type"] if f else None, "uploaded": f["uploaded_at"][:10] if f else None,
            "size": f.get("filesize") if f else None,
            "versions": ", ".join(f["versions"]) if f else None,
        })
    write_cf_pending(cf_rows)

    nf = neoforge_latest()
    write_pack_toml(nf)

    mods = [r for r in rows if r["kind"] == "mod"]
    libs = [r for r in rows if r["kind"] == "library"]
    report = {
        "mods": len(mods), "libraries": len(libs),
        "auto_deps": sum(1 for r in rows if r["auto_dep"]),
        "auto_dep_slugs": [r["slug"] + " <- " + r["auto_dep"][9:-1] for r in rows if r["auto_dep"]],
        "cf_pending": len(cf_rows),
        "total_mb": round(sum(r["size_bytes"] for r in rows) / 1024 / 1024, 1),
        "neoforge_version": nf,
        "missing_project": missing_project,
        "missing_on_1211": missing_on_1211,
        "dead_deps": dead_deps,
        "incompatible_pairs": incompatible_pairs,
        "side_overrides": side_overrides,
        "sides": {k: sum(1 for r in rows if r["side"] == k) for k in ("both", "client", "server")},
        "channels": {c: sum(1 for r in rows if r["channel"] == c) for c in ("release", "beta", "alpha")},
        "doc_version_diffs": [f"{r['slug']}: {r['doc_version']} -> {r['version_number']} ({r['channel']})"
                              for r in rows if r["doc_version"] and r["doc_version"] != "n/a"],
        "cf": cf_rows,
    }
    json.dump(report, open(os.path.join(DEV, "gen_report.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print(json.dumps({k: v for k, v in report.items() if k != "cf"}, indent=1, ensure_ascii=False))


def write_cf_pending(cf_rows):
    L = ["# CurseForge-only mods (not in mods/)", "",
         "Diese Mods gibt es nicht auf Modrinth. Sie stehen noch nicht als `.pw.toml` im Repo, weil",
         "packwiz fuer CurseForge-Dateien die CF-Datei-ID und einen Hash braucht (`packwiz curseforge add`",
         "oder ein manuell geschriebenes `[update.curseforge]`), und die cfwidget-API liefert keinen Hash.",
         "Quelle der Datei-Angaben: https://api.cfwidget.com/minecraft/mc-mods/<slug>, Stand beim Generieren.",
         "",
         "| Mod | CF-Slug | CF-Projekt-ID | Datei fuer 1.21.1 NeoForge | Datei-ID | Kanal | Datum | Doc-Version | Grund |",
         "|---|---|---|---|---|---|---|---|---|"]
    for r in cf_rows:
        L.append(f"| {r['name']} | `{r['slug']}` | {r['cf_id'] or '?'} | `{r['file'] or 'keine 1.21.1-NeoForge-Datei gefunden'}` "
                 f"| {r['file_id'] or '?'} | {r['type'] or '?'} | {r['uploaded'] or '?'} | {r['doc_version']} | nur CurseForge |")
    L += ["", "Hinweise:", "",
          "- FTB-Mods brauchen Architectury API; die liegt als Modrinth-Library schon in `mods/`.",
          "- `.mrpack`-Export: CF-only-Jars duerfen nicht in die Modrinth-Datei; fuer den privaten Server",
          "  reicht packwiz-installer mit `[update.curseforge]`-Eintraegen (Dok. 06).",
          "- Eigene Mod `firmages-core` ist noch nicht gebaut und fehlt deshalb ebenfalls.", ""]
    with open(os.path.join(DEV, "cf-pending.md"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(L))


def write_pack_toml(nf):
    path = os.path.join(ROOT, "pack.toml")
    old_hash = ""
    if os.path.exists(path):
        m = re.search(r'^hash = "([0-9a-f]*)"', open(path, encoding="utf-8").read(), re.M)
        old_hash = m.group(1) if m else ""
    txt = (
        'name = "Firmament Ages"\n'
        'author = "PhoenixFlyy"\n'
        'version = "0.1.0"\n'
        'pack-format = "packwiz:1.1.0"\n\n'
        '[index]\n'
        'file = "index.toml"\n'
        'hash-format = "sha256"\n'
        f'hash = "{old_hash}"\n\n'
        '[versions]\n'
        f'minecraft = "{MC}"\n'
        f'neoforge = "{nf}"\n'
    )
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(txt)


if __name__ == "__main__":
    main()
