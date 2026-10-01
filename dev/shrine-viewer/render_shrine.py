#!/usr/bin/env python3
"""Render the Firmament Ages shrine multiblock (Modonomicon "dense" rings).

Reads, read-only, from the firmages-core data folder:
  modonomicon/multiblocks/shrine_ring_N.json   ring patterns (top layer first, "_" = any)
  firmages_shrine/tier/ring_N.json             grants, plinth_key, rites, response, blessing
  tags/block/shrine/<tag>.json                 what each tag letter means in materials
  ../../assets/firmages/lang/en_us.json        Age names, multiblock names, blessing and rite text (optional)

Writes to <this folder>/out/:
  ring_N.png               isometric voxel view of ring N alone
  shrine_after_age_N.png   isometric view of the cumulative shrine (rings 0..N)
  plan_after_age_N.png     top-down plan, one panel per layer, for builders
  overview_all_rings.png   the cumulative shrine at every tier, 3 x 3 panels, shared legend
and <this folder>/shrine_data.json (voxel data for viewer.html).

Usage:
  python render_shrine.py                       render PNGs + shrine_data.json
  python render_shrine.py --build-html          ... and inline the JSON into viewer.html
  python render_shrine.py --no-png --build-html only JSON + HTML (fast)
  python render_shrine.py --data-root <path>    other data/firmages folder

Only matplotlib and numpy are required (Python 3.13).

Rendering: every panel is ONE Poly3DCollection of precomputed quads (only faces that are exposed and turned
towards the camera), so a 21 x 21 x 5 shrine with ground slab and beam is a few thousand polygons instead of
one collection per voxel (ax.voxels), which crashed the interpreter on the 9-panel overview.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import math
import re
import sys
import textwrap
import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.colors import to_rgb  # noqa: E402
from matplotlib.patches import Patch, Rectangle  # noqa: E402
from mpl_toolkits.mplot3d.art3d import Poly3DCollection  # noqa: E402

HERE = Path(__file__).resolve().parent
DEFAULT_DATA_ROOT = Path(
    r"D:\Minecraft\MinecraftModServer\FirmamentAges\mod\firmages-core\src\main\resources\data\firmages"
)
HEART_BLOCK = "firmages:shrine_heart"
PLINTH_BLOCK = "firmages:offering_plinth"
DEITY = "Caelum, the Firmament"

# Readable names for the shrine tags / blocks. Unknown keys fall back to a humanised tag name.
MATERIAL_NAMES = {
    "hearth_stones": "Cobblestone (TFC)",
    "hearth_posts": "Logs",
    "thatch": "Thatch",
    "sanctum_bricks": "Stone bricks",
    "bronze_blocks": "Bronze blocks",
    "bells": "Bell",
    "smooth_stones": "Smooth stone",
    "iron_bars": "Iron bars",
    "iron_lamps": "Iron lamps",
    HEART_BLOCK: "Shrine Heart",
    PLINTH_BLOCK: "Offering Plinth",
}
MATERIAL_COLORS = {
    "hearth_stones": "#7f7f7a",
    "hearth_posts": "#6e4a2a",
    "thatch": "#d2b04e",
    "sanctum_bricks": "#a39f93",
    "bronze_blocks": "#b9742f",
    "bells": "#e2b94b",
    "smooth_stones": "#b8bab5",
    "iron_bars": "#596069",
    "iron_lamps": "#f4d679",
    HEART_BLOCK: "#ff7a1a",
    PLINTH_BLOCK: "#f2dc9c",
}
# Materials that glow (emissive in the 3D viewer, drawn without shading darkening in PNGs).
GLOW_KEYS = {HEART_BLOCK}
# Keyword heuristics for materials that appear in later rings (ring 3..8) without a hand-picked color.
COLOR_HINTS = [
    ("gold", "#e6c04a"), ("copper", "#c47a45"), ("bronze", "#b9742f"), ("brass", "#d4a94a"),
    ("steel", "#7d8690"), ("iron", "#6a6f78"), ("silver", "#c9ced6"), ("tin", "#d9dde0"),
    ("obsidian", "#2c2238"), ("quartz", "#eeeae2"), ("marble", "#e4e0d6"), ("glass", "#9fd3e6"),
    ("crystal", "#9ad1ff"), ("amethyst", "#9a6fd6"), ("lapis", "#2d55b5"), ("emerald", "#3fbf6f"),
    ("redstone", "#c0261f"), ("netherite", "#4a3f45"), ("diamond", "#6fd8e8"), ("concrete", "#c9c5bd"),
    ("brick", "#a39f93"), ("smooth", "#b8bab5"), ("stone", "#8d8d88"), ("cobble", "#7f7f7a"),
    ("log", "#6e4a2a"), ("wood", "#8a5a30"), ("plank", "#b98a52"), ("thatch", "#d2b04e"), ("hay", "#d2b04e"),
    ("lamp", "#f4d679"), ("lantern", "#f4c86a"), ("glow", "#f2e68a"), ("torch", "#f0b050"),
    ("bell", "#e2b94b"), ("beacon", "#8ff0ff"), ("machine", "#8a9aa8"), ("circuit", "#3f7f5f"),
    ("solar", "#304a7a"), ("coil", "#b06a3a"), ("wire", "#b06a3a"), ("plate", "#a9b2bb"),
]
FALLBACK_PALETTE = ["#8e6bbf", "#4f9d8c", "#c76b6b", "#5b8fd6", "#b58d3c", "#6fa84f", "#c35fa0", "#5fb3c3"]
GROUND_COLORS = ("#6f8a4c", "#66814a")

# Camera and light for every isometric panel (orthographic). Light comes from the upper left of the camera.
VIEW_ELEV, VIEW_AZIM = 28.0, -50.0
LIGHT_AZDEG, LIGHT_ALTDEG = 200.0, 48.0

# English fallbacks for rite hints when the lang file has no text for the hint key.
RITE_FALLBACKS = {
    "blockstate": "Set the marked block of the shrine to {property} = {value}.",
    "interact": "Use the marked block of the shrine.",
    "players_praying": "At least {min} of you must pray together.",
    "sky": "Pray at night, under an open sky, without rain.",
}

RING_RE = re.compile(r"shrine_ring_(\d+)\.json$")
TIER_RE = re.compile(r"^ring_(\d+)\.json$")


# --------------------------------------------------------------------------- data model
@dataclass
class Material:
    key: str                    # tag name ("hearth_stones") or block id ("firmages:shrine_heart")
    name: str
    color: str
    glow: bool = False
    examples: list[str] = field(default_factory=list)
    ring_letters: dict[int, str] = field(default_factory=dict)
    plan_letter: str = ""       # unique 1-2 character code, the same in every plan, render and the viewer
    index: int = -1


@dataclass
class Ring:
    n: int
    mb_id: str
    display_name: str
    age_name: str               # Age during which the ring is built (ring N -> age_N)
    grants: str | None
    grants_name: str | None
    blessing: str | None
    blessing_name: str | None
    blessing_desc: str | None
    beam_color: str
    sky_tint: str
    plinth_key: str | None
    rites: list[str]
    voice: str | None
    letters: dict[str, str]     # letter -> material key
    blocks: list[tuple[int, int, int, str]]  # (x east, y up, z south, material key), heart at origin
    size: tuple[int, int, int]  # (width x, height y, depth z)
    notes: list[str] = field(default_factory=list)


def humanize(key: str) -> str:
    tail = key.split("/")[-1].split(":")[-1]
    return tail.replace("_", " ").strip().capitalize()


def color_for(key: str, taken: list[str]) -> str:
    if key in MATERIAL_COLORS:
        return MATERIAL_COLORS[key]
    low = key.lower()
    for word, col in COLOR_HINTS:
        if word in low:
            return col
    for col in FALLBACK_PALETTE:
        if col not in taken:
            return col
    return FALLBACK_PALETTE[len(taken) % len(FALLBACK_PALETTE)]


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def load_lang(data_root: Path) -> dict[str, str]:
    lang = data_root.parent.parent / "assets" / "firmages" / "lang" / "en_us.json"
    if lang.is_file():
        try:
            return load_json(lang)
        except Exception as exc:  # pragma: no cover - diagnostics only
            print(f"warning: could not read {lang}: {exc}")
    return {}


def tag_examples(data_root: Path, tag_key: str) -> list[str]:
    path = data_root / "tags" / "block" / "shrine" / f"{tag_key}.json"
    if not path.is_file():
        return []
    try:
        values = load_json(path).get("values", [])
    except Exception:
        return []
    out = []
    for v in values:
        vid = v.get("id") if isinstance(v, dict) else v
        if isinstance(vid, str):
            out.append(vid)
    return out


def material_key(entry: dict) -> str:
    t = entry.get("type", "")
    if t.endswith("block"):
        return entry.get("block", "?")
    if t.endswith("tag"):
        tag = entry.get("tag", "?").lstrip("#")
        if tag.startswith("firmages:shrine/"):
            return tag[len("firmages:shrine/"):]
        return tag
    return json.dumps(entry, sort_keys=True)


def format_rite(rite: dict, lang: dict) -> str:
    """Human sentence for one rite: lang text for its hint (or type), with %s / {param} filled in."""
    rtype = rite.get("type", "rite")
    hint = rite.get("hint")
    text = (lang.get(hint) if hint else None) or lang.get(f"firmages.shrine.rite.{rtype}") \
        or RITE_FALLBACKS.get(rtype) or (hint.split(".")[-1].replace("_", " ").capitalize() + "." if hint
                                         else f"Rite: {rtype}.")
    params = {k: v for k, v in rite.items() if k not in ("type", "hint")}
    # Minecraft lang strings use %s / %1$s; the parameter is the rite's own value (players_praying: "min")
    ordered = [params[k] for k in ("min", "count", "value", "key", "tag", "property") if k in params]
    ordered += [v for k, v in params.items() if k not in ("min", "count", "value", "key", "tag", "property")]
    if "%" in text:
        def sub(m):
            idx = int(m.group(1)) - 1 if m.group(1) else sub.pos
            sub.pos += 1
            return str(ordered[idx]) if 0 <= idx < len(ordered) else "enough"
        sub.pos = 0
        text = re.sub(r"%(?:(\d+)\$)?[sd]", sub, text)
    if "{" in text:
        try:
            text = text.format(**{k: str(v) for k, v in params.items()})
        except (KeyError, IndexError):
            pass
    return text.strip()


# --------------------------------------------------------------------------- parsing
def discover_ring_numbers(data_root: Path) -> list[int]:
    nums: set[int] = set()
    for p in (data_root / "modonomicon" / "multiblocks").glob("shrine_ring_*.json"):
        m = RING_RE.search(p.name)
        if m:
            nums.add(int(m.group(1)))
    for p in (data_root / "firmages_shrine" / "tier").glob("ring_*.json"):
        m = TIER_RE.search(p.name)
        if m:
            nums.add(int(m.group(1)))
    return sorted(nums)


def parse_ring(n: int, data_root: Path, lang: dict, materials: dict[str, Material], notes: list[str]) -> Ring | None:
    tier_path = data_root / "firmages_shrine" / "tier" / f"ring_{n}.json"
    tier = load_json(tier_path) if tier_path.is_file() else {}
    if not tier:
        notes.append(f"ring {n}: no tier file {tier_path.name} (grants/beam/blessing unknown)")
    mb_id = tier.get("multiblock", f"firmages:shrine_ring_{n}")
    mb_name = mb_id.split(":", 1)[-1]
    mb_path = data_root / "modonomicon" / "multiblocks" / f"{mb_name}.json"
    if not mb_path.is_file():
        notes.append(f"ring {n}: multiblock file {mb_path.name} missing, ring skipped")
        return None
    mb = load_json(mb_path)
    if mb.get("type") != "modonomicon:dense":
        notes.append(f"ring {n}: multiblock type is {mb.get('type')!r}, expected modonomicon:dense")

    mapping = mb.get("mapping", {})
    letters: dict[str, str] = {}
    for letter, entry in mapping.items():
        key = material_key(entry)
        letters[letter] = key
        if key not in materials:
            mat = Material(
                key=key,
                name=MATERIAL_NAMES.get(key, humanize(key)),
                color=color_for(key, [m.color for m in materials.values()]),
                glow=key in GLOW_KEYS,
                examples=tag_examples(data_root, key) if entry.get("type", "").endswith("tag") else [key],
            )
            mat.index = len(materials)
            materials[key] = mat
        materials[key].ring_letters[n] = letter

    layers = mb.get("pattern", [])
    height = len(layers)
    if height == 0:
        notes.append(f"ring {n}: empty pattern")
        return None
    rows = len(layers[0])
    cols = max((len(r) for r in layers[0]), default=0)
    raw: list[tuple[int, int, int, str]] = []   # (col, y, row, letter)
    hearts: list[tuple[int, int, int]] = []
    unknown: set[str] = set()
    for li, layer in enumerate(layers):
        y = height - 1 - li  # pattern lists the TOP layer first
        if len(layer) != rows:
            notes.append(f"ring {n}: layer {li} has {len(layer)} rows, layer 0 has {rows}")
        for r, row in enumerate(layer):
            if len(row) != cols:
                notes.append(f"ring {n}: layer {li} row {r} has {len(row)} columns, expected {cols}")
            for c, ch in enumerate(row):
                if ch in ("_", " "):
                    continue
                if ch not in letters:
                    unknown.add(ch)
                    continue
                raw.append((c, y, r, ch))
                if letters[ch] == HEART_BLOCK:
                    hearts.append((c, y, r))
    for ch in sorted(unknown):
        notes.append(f"ring {n}: letter {ch!r} appears in the pattern but is not in 'mapping' (ignored)")
    if not hearts and "0" in letters:
        hearts = [(c, y, r) for (c, y, r, ch) in raw if ch == "0"]
    if len(hearts) != 1:
        notes.append(f"ring {n}: expected exactly one Shrine Heart, found {len(hearts)}; "
                     f"{'using the footprint centre' if not hearts else 'using the first'}")
    hc, hy, hr = hearts[0] if hearts else ((cols - 1) // 2, 0, (rows - 1) // 2)
    if hy != 0:
        notes.append(f"ring {n}: the heart is in layer y={hy}, not the bottom layer")
    if (cols - 1) / 2 != hc or (rows - 1) / 2 != hr:
        notes.append(f"ring {n}: heart at column {hc}, row {hr} is not the centre of the {cols}x{rows} footprint")

    blocks = [(c - hc, y - hy, r - hr, letters[ch]) for (c, y, r, ch) in raw]

    # 4-fold symmetry about the heart (rotation by 90 degrees: (x, z) -> (-z, x)). The plinth marks one
    # side by design, so its cell and the three cells it would occupy when rotated are ignored for the
    # wall material it replaces.
    by_key: dict[str, set[tuple[int, int, int]]] = {}
    for x, y, z, key in blocks:
        by_key.setdefault(key, set()).add((x, y, z))
    plinth_key = tier.get("plinth_key")

    def rot(cells):
        return {(-z, y, x) for (x, y, z) in cells}

    plinth_orbit: set[tuple[int, int, int]] = set()
    cells = set(by_key.get(PLINTH_BLOCK, set()))
    for _ in range(4):
        plinth_orbit |= cells
        cells = rot(cells)
    for key, cells in by_key.items():
        if key == PLINTH_BLOCK:
            continue
        probe = cells - plinth_orbit
        if rot(probe) != probe:
            letter = next(l for l, k in letters.items() if k == key)
            if len(cells) < 4:
                notes.append(f"ring {n}: letter {letter!r} ({materials[key].name}) is a single-sided feature "
                             f"({len(cells)} block{'s' if len(cells) != 1 else ''}, not repeated on all four sides)")
            else:
                notes.append(f"ring {n}: letter {letter!r} ({materials[key].name}) breaks 4-fold symmetry")
    if plinth_key and plinth_key not in letters:
        notes.append(f"ring {n}: plinth_key {plinth_key!r} is not a letter of the multiblock")
    plinths = [b for b in blocks if b[3] == PLINTH_BLOCK]
    if len(plinths) != 1:
        notes.append(f"ring {n}: {len(plinths)} Offering Plinths in the pattern (expected 1)")

    grants = tier.get("grants")
    grants_name = lang.get(f"firmages.age.{grants}.name") if grants else None
    age_name = lang.get(f"firmages.age.age_{n}.name", f"Age {n}")
    if grants and re.fullmatch(r"age_\d+", grants) and int(grants[4:]) != n + 1:
        notes.append(f"ring {n}: grants {grants}, expected age_{n + 1} (ring N built in age_N grants age_N+1)")
    blessing = tier.get("blessing")
    bkey = blessing.split(":", 1)[-1] if blessing else None
    response = tier.get("response", {})
    rites = [format_rite(rite, lang) for rite in tier.get("rites", [])]
    return Ring(
        n=n,
        mb_id=mb_id,
        display_name=lang.get(f"multiblock.{mb_id.replace(':', '.')}", f"ring {n}"),
        age_name=age_name,
        grants=grants,
        grants_name=grants_name,
        blessing=blessing,
        blessing_name=lang.get(f"firmages.blessing.{bkey}", humanize(bkey)) if bkey else None,
        blessing_desc=lang.get(f"firmages.blessing.{bkey}.description") if bkey else None,
        beam_color=response.get("beam_color", "#FFFFFF"),
        sky_tint=response.get("sky_tint", "#8FB8E8"),
        plinth_key=plinth_key,
        rites=rites,
        voice=lang.get(f"firmages.shrine.voice.{grants}") if grants else None,
        letters=letters,
        blocks=blocks,
        size=(cols, height, rows),
    )


def assign_plan_codes(materials: dict[str, Material]) -> None:
    """One unique 1-2 character code per material, in order of first appearance.

    Preference: the material's own JSON letter, the initial of a word of its name, then two-letter codes
    (initials of two words, first two letters, first letter + consonant), then letter + digit.
    Rings reuse JSON letters (e.g. 'L' is Logs in ring 0 and Iron lamps in ring 2), hence the codes.
    """
    used: set[str] = set()
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    for mat in sorted(materials.values(), key=lambda m: m.index):
        words = [w for w in re.split(r"[\s_/:()\-]+", mat.name) if w]
        first = (words[0] if words else "X").upper()
        cands: list[str] = [l for _, l in sorted(mat.ring_letters.items())]
        cands += [w[0] for w in words]
        if len(words) >= 2:
            cands.append(words[0][0] + words[1][0])
        cands.append(first[:2])
        cands += [first[0] + c for c in first[1:] if c not in "AEIOU"]
        cands += [first[0] + str(d) for d in range(1, 10)]
        cands += list(letters)
        cands += [a + str(d) for a in letters for d in range(1, 10)]
        for cand in cands:
            cand = cand.upper()
            if cand and cand.isalnum() and cand not in used:
                mat.plan_letter = cand
                used.add(cand)
                break


def cumulative_blocks(rings: list[Ring], upto: int, notes: list[str]) -> list[tuple[int, int, int, str, int]]:
    """Rings 0..upto stacked; a later ring only fills cells the earlier rings left as '_'."""
    cells: dict[tuple[int, int, int], tuple[str, int]] = {}
    for ring in rings:
        if ring.n > upto:
            break
        for x, y, z, key in ring.blocks:
            pos = (x, y, z)
            if pos in cells:
                prev_key, prev_ring = cells[pos]
                if prev_key != key and upto == ring.n:
                    notes.append(f"ring {ring.n}: {key} at x={x} y={y} z={z} overlaps ring {prev_ring}'s {prev_key} "
                                 f"(earlier ring kept)")
                continue
            cells[pos] = (key, ring.n)
    return [(x, y, z, key, rn) for (x, y, z), (key, rn) in cells.items()]


# --------------------------------------------------------------------------- rendering helpers
def rgb(c: str) -> np.ndarray:
    return np.array(to_rgb(c))


def blend(c1: str, c2: str, t: float) -> tuple[float, float, float]:
    a, b = rgb(c1), rgb(c2)
    return tuple((a * (1 - t) + b * t).tolist())


def text_color_for(bg: str) -> str:
    r, g, b = rgb(bg)
    return "#111111" if (0.299 * r + 0.587 * g + 0.114 * b) > 0.6 else "#ffffff"


def bounds_of(blocks) -> tuple[int, int, int, int, int]:
    xs = [b[0] for b in blocks]
    zs = [b[2] for b in blocks]
    ys = [b[1] for b in blocks]
    return min(xs), max(xs), min(zs), max(zs), max(ys)


# Unit-cube faces: outward normal -> the four corner offsets of that face.
CUBE_FACES: dict[tuple[int, int, int], tuple[tuple[int, int, int], ...]] = {
    (0, 0, 1): ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
    (0, 0, -1): ((0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)),
    (1, 0, 0): ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
    (-1, 0, 0): ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)),
    (0, 1, 0): ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    (0, -1, 0): ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
}


def view_direction(elev: float, azim: float) -> np.ndarray:
    """Unit vector from the scene towards the camera (matplotlib's elev/azim convention)."""
    e, a = math.radians(elev), math.radians(azim)
    return np.array([math.cos(e) * math.cos(a), math.cos(e) * math.sin(a), math.sin(e)])


def light_direction(azdeg: float, altdeg: float) -> np.ndarray:
    """Same convention as matplotlib.colors.LightSource.direction."""
    az, alt = math.radians(90 - azdeg), math.radians(altdeg)
    return np.array([math.cos(az) * math.cos(alt), math.sin(az) * math.cos(alt), math.sin(alt)])


def face_shade(normal: tuple[int, int, int], light: np.ndarray) -> float:
    """Brightness factor 0.3..1 like mplot3d's shading (Normalize(-1, 1) -> Normalize(0.3, 1))."""
    return 0.65 + 0.35 * float(np.dot(normal, light))


def build_quads(blocks, materials: dict[str, Material], bounds, beam_color: str | None, beam_headroom: int,
                edge_alpha: float):
    """Exposed, camera-facing quads of the ground slab, the blocks and the beam.

    Returns (verts [N,4,3], facecolors [N,4], edgecolors [N,4], nx, ny, nz, heart_ix, heart_iy).
    Axes: X = east, Y = south (north is the near side for azim -50), Z = up. Cell (ix, iy, iz) spans
    [ix, ix+1] x [iy, iy+1] x [iz, iz+1]; the ground slab is Z index 0, blocks start at Z index 1.
    """
    minx, maxx, minz, maxz, maxy = bounds
    margin = 1
    nx = maxx - minx + 1 + 2 * margin
    ny = maxz - minz + 1 + 2 * margin
    nz = maxy + 2 + beam_headroom  # ground slab + blocks + headroom for the beam
    view = view_direction(VIEW_ELEV, VIEW_AZIM)
    light = light_direction(LIGHT_AZDEG, LIGHT_ALTDEG)
    visible = [n for n in CUBE_FACES if np.dot(n, view) > 1e-9]   # back faces can never be seen (ortho camera)
    shade = {n: face_shade(n, light) for n in visible}

    # cell -> (rgb, edge rgba, glow)
    cells: dict[tuple[int, int, int], tuple[tuple[float, float, float], tuple[float, float, float, float], bool]] = {}
    for ix in range(nx):
        for iy in range(ny):
            cells[(ix, iy, 0)] = (to_rgb(GROUND_COLORS[(ix + iy) % 2]), (0.0, 0.0, 0.0, 0.06), False)
    for x, y, z, key, *_ in blocks:
        mat = materials[key]
        cells[(x - minx + margin, z - minz + margin, y + 1)] = (to_rgb(mat.color), (0.0, 0.0, 0.0, edge_alpha), mat.glow)

    verts: list[list[tuple[float, float, float]]] = []
    fcs: list[tuple[float, float, float, float]] = []
    ecs: list[tuple[float, float, float, float]] = []
    for (ix, iy, iz), (col, edge, glow) in cells.items():
        for n in visible:
            if (ix + n[0], iy + n[1], iz + n[2]) in cells:
                continue  # face between two solid cells, never visible
            if glow:
                c = blend(matplotlib.colors.to_hex(col), "#ffffff", 0.45) if n == (0, 0, 1) else col
                f = 1.0
            else:
                c, f = col, shade[n]
            verts.append([(ix + dx, iy + dy, iz + dz) for dx, dy, dz in CUBE_FACES[n]])
            fcs.append((c[0] * f, c[1] * f, c[2] * f, 1.0))
            ecs.append(edge)

    heart_ix, heart_iy = -minx + margin + 0.5, -minz + margin + 0.5
    if beam_color:
        # translucent column above the heart (its top face is at Z=2), split into 1-block rings so the
        # painter's sort interleaves it correctly with blocks standing in front of its lower part
        z0, z1 = 2.0, nz - 0.2
        zs = np.arange(z0, z1, 1.0).tolist() + [z1]
        core = blend(beam_color, "#ffffff", 0.5)
        beam_rgb = to_rgb(beam_color)
        for radius, alpha, col, segs in ((0.46, 0.16, beam_rgb, 28), (0.30, 0.26, beam_rgb, 28), (0.09, 0.75, core, 12)):
            theta = np.linspace(0, 2 * np.pi, segs + 1)
            xs = heart_ix + radius * np.cos(theta)
            ys = heart_iy + radius * np.sin(theta)
            rgba = (col[0], col[1], col[2], alpha)
            for a, b in zip(zs[:-1], zs[1:]):
                for i in range(segs):
                    verts.append([(xs[i], ys[i], a), (xs[i + 1], ys[i + 1], a), (xs[i + 1], ys[i + 1], b), (xs[i], ys[i], b)])
                    fcs.append(rgba)
                    ecs.append(rgba)  # edge in the face colour hides anti-aliasing seams between segments
    return (np.asarray(verts, dtype=float), np.asarray(fcs, dtype=float), np.asarray(ecs, dtype=float),
            nx, ny, nz, heart_ix, heart_iy)


def draw_voxels(ax, blocks, materials: dict[str, Material], bounds, beam_color: str | None,
                beam_headroom: int = 4, zoom: float = 1.0, edge_alpha: float = 0.22, north_label: bool = True) -> int:
    """Draw blocks (x east, y up, z south) on a ground slab with the beam above the heart. Returns the quad count."""
    verts, fcs, ecs, nx, ny, nz, heart_ix, heart_iy = build_quads(blocks, materials, bounds, beam_color,
                                                                 beam_headroom, edge_alpha)
    coll = Poly3DCollection(verts, facecolors=fcs, edgecolors=ecs, linewidths=0.35, zsort="average")
    ax.add_collection3d(coll)
    if north_label:
        # compass letters painted on the ground slab's margin row (north is the near side)
        ax.text(heart_ix, 0.5, 1.02, "N", color="#1d2a14", fontsize=11, fontweight="bold", ha="center", va="center",
                zorder=10 ** 6)
        ax.text(nx - 0.5, heart_iy, 1.02, "E", color="#1d2a14", fontsize=10, ha="center", va="center",
                zorder=10 ** 6)
    ax.set_xlim(0, nx)
    ax.set_ylim(0, ny)
    ax.set_zlim(0, nz)
    ax.set_box_aspect((nx, ny, nz), zoom=zoom)
    ax.set_proj_type("ortho")
    ax.view_init(elev=VIEW_ELEV, azim=VIEW_AZIM)
    ax.set_axis_off()
    return len(verts)


def legend_handles(materials: dict[str, Material], keys: list[str], counts: dict[str, int] | None = None,
                   ring: Ring | None = None):
    """Legend entries 'CODE  Name  (count)'; for a ring-alone picture the ring's JSON letter is added when it differs."""
    handles = []
    for key in sorted(keys, key=lambda k: materials[k].index):
        mat = materials[key]
        label = f"{mat.plan_letter:<2}  {mat.name}"
        if counts:
            label += f"  ({counts.get(key, 0)})"
        if ring is not None:
            json_letter = ring.letters and next((l for l, k in ring.letters.items() if k == key), None)
            if json_letter and json_letter != mat.plan_letter:
                label += f"  [JSON: {json_letter}]"
        handles.append(Patch(facecolor=mat.color, edgecolor="#333333", linewidth=0.6, label=label))
    return handles


def legend_columns(n_entries: int, max_rows: int) -> int:
    return max(1, math.ceil(n_entries / max_rows))


def footer_lines(ring: Ring, width: int) -> list[str]:
    grants = f"Grants {ring.grants}" + (f" ({ring.grants_name})" if ring.grants_name else "") if ring.grants \
        else "Grants nothing"
    if ring.blessing_name:
        blessing = f"Blessing: {ring.blessing_name}" + (f" - {ring.blessing_desc}" if ring.blessing_desc else "")
    else:
        blessing = "Blessing: none"
    lines = textwrap.wrap(f"{grants}.  {blessing}", width)
    if ring.rites:
        rites = "Rite: " + "  ".join(ring.rites) if len(ring.rites) == 1 else "Rites: " + "  |  ".join(ring.rites)
    else:
        rites = "No rite: offering and prayer are enough."
    lines += textwrap.wrap(rites, width)
    return lines


def render_3d(blocks, materials, ring: Ring, title: str, subtitle: str, out: Path, ring_legend: Ring | None) -> int:
    counts: dict[str, int] = {}
    for b in blocks:
        counts[b[3]] = counts.get(b[3], 0) + 1
    handles = legend_handles(materials, list(counts), counts, ring_legend)
    # layout in inches: title band, 3D view (left), legend column(s) (right), footer band with wrapped text
    legend_fs, legend_rows = 9.0, 18
    ncol = legend_columns(len(handles), legend_rows)
    longest = max((len(h.get_label()) for h in handles), default=20)
    col_w = min(3.2, 0.066 * longest + 0.55)       # inches per legend column at 9 pt
    view_w, legend_w = 8.0, ncol * col_w + 0.35
    fig_w = view_w + legend_w
    foot = footer_lines(ring, width=int((view_w - 0.4) / 0.072))
    top_band, bottom_band = 1.0, 0.45 + 0.19 * len(foot)
    fig_h = 6.3 + top_band + bottom_band
    fig = plt.figure(figsize=(fig_w, fig_h), dpi=150)
    fig.patch.set_facecolor(blend("#ffffff", ring.sky_tint, 0.08))
    ax = fig.add_axes([0.0, bottom_band / fig_h, view_w / fig_w, 1 - (top_band + bottom_band) / fig_h], projection="3d")
    ax.set_facecolor((0, 0, 0, 0))
    quads = draw_voxels(ax, blocks, materials, bounds_of(blocks), ring.beam_color, zoom=1.45)

    leg = fig.legend(handles=handles, loc="upper left", bbox_to_anchor=(view_w / fig_w, 1 - (top_band + 0.05) / fig_h),
                     ncol=ncol, frameon=False, fontsize=legend_fs, title="Materials  (code, count)",
                     title_fontsize=legend_fs + 0.5, handlelength=1.5, labelspacing=0.55, columnspacing=1.2,
                     borderaxespad=0.0, alignment="left")
    leg.get_title().set_color("#444444")
    # beam / sky swatches in the footer band, under the legend column (never shared with the legend's area)
    sx = (view_w + 0.2) / fig_w
    sw_w, sw_h = 0.18 / fig_w, 0.17 / fig_h
    y_beam, y_sky = (bottom_band - 0.22) / fig_h, (bottom_band - 0.52) / fig_h
    fig.patches.extend([
        Rectangle((sx, y_beam), sw_w, sw_h, transform=fig.transFigure, facecolor=ring.beam_color, edgecolor="#333"),
        Rectangle((sx, y_sky), sw_w, sw_h, transform=fig.transFigure, facecolor=ring.sky_tint, edgecolor="#333"),
    ])
    fig.text(sx + sw_w + 0.08 / fig_w, y_beam + sw_h / 2, f"Beam {ring.beam_color}", fontsize=9.5, va="center", color="#333333")
    fig.text(sx + sw_w + 0.08 / fig_w, y_sky + sw_h / 2, f"Sky tint {ring.sky_tint}", fontsize=9.5, va="center", color="#333333")
    fig.text(0.3 / fig_w, 1 - 0.38 / fig_h, title, fontsize=16, fontweight="bold", color="#222222", va="center")
    fig.text(0.3 / fig_w, 1 - 0.72 / fig_h, subtitle, fontsize=11, color="#555555", va="center")
    fig.text(0.3 / fig_w, (bottom_band - 0.2) / fig_h, "\n".join(foot), fontsize=9, color="#555555", va="top",
             linespacing=1.55)
    fig.savefig(out, facecolor=fig.get_facecolor())
    plt.close(fig)
    return quads


def render_plan(blocks, materials: dict[str, Material], ring: Ring, out: Path) -> None:
    minx, maxx, minz, maxz, maxy = bounds_of(blocks)
    layers = maxy + 1
    per_row = 4 if layers > 4 else layers
    nrows = (layers + per_row - 1) // per_row
    w = maxx - minx + 1
    d = maxz - minz + 1
    present: dict[str, int] = {}
    by_layer: dict[int, list] = {}
    for b in blocks:
        by_layer.setdefault(b[1], []).append(b)
        present[b[3]] = present.get(b[3], 0) + 1
    # layout in inches: square panels, title band, legend band (up to 4 columns, 9 rows each)
    panel_in = max(3.6, min(5.4, 0.45 * max(w, d) + 1.3))
    gap_in, side_in = 0.55, 0.6
    fig_w = max(10.67, per_row * panel_in + (per_row - 1) * gap_in + 2 * side_in)
    ncol = min(4, legend_columns(len(present), 9))
    legend_rows = (len(present) + ncol - 1) // ncol
    legend_in = 0.22 * legend_rows + 0.3
    title_in, panel_title_in = 1.05, 0.5
    fig_h = title_in + nrows * (panel_in + panel_title_in) + (nrows - 1) * 0.3 + legend_in + 0.35
    fig = plt.figure(figsize=(fig_w, fig_h), dpi=150)
    fig.patch.set_facecolor("#ffffff")
    axes = fig.subplots(nrows, per_row, squeeze=False)
    fs = 11 if max(w, d) <= 11 else (9 if max(w, d) <= 17 else 7.5)
    for i in range(nrows * per_row):
        ax = axes[i // per_row][i % per_row]
        if i >= layers:
            ax.set_visible(False)
            continue
        y = i
        ax.set_xlim(minx - 0.5, maxx + 0.5)
        ax.set_ylim(maxz + 0.5, minz - 0.5)  # north (negative z) at the top
        ax.set_aspect("equal")
        for xg in range(minx, maxx + 2):
            ax.axvline(xg - 0.5, color="#e3e3e3", linewidth=0.6, zorder=0)
        for zg in range(minz, maxz + 2):
            ax.axhline(zg - 0.5, color="#e3e3e3", linewidth=0.6, zorder=0)
        # faint heart marker on every layer so builders can line layers up
        ax.add_patch(Rectangle((-0.5, -0.5), 1, 1, facecolor="none", edgecolor=MATERIAL_COLORS[HEART_BLOCK],
                               linewidth=1.2, linestyle=(0, (2, 2)), zorder=1))
        for x, yy, z, key, *_ in by_layer.get(y, []):
            mat = materials[key]
            ax.add_patch(Rectangle((x - 0.5, z - 0.5), 1, 1, facecolor=mat.color, edgecolor="#333333",
                                   linewidth=0.7, zorder=2))
            ax.text(x, z, mat.plan_letter, ha="center", va="center", fontsize=fs if len(mat.plan_letter) == 1 else fs - 1,
                    fontweight="bold", color=text_color_for(mat.color), zorder=3)
        ticks_x = list(range(minx, maxx + 1))
        ticks_z = list(range(minz, maxz + 1))
        step = 1 if max(w, d) <= 13 else 2
        ax.set_xticks(ticks_x[::step])
        ax.set_yticks(ticks_z[::step])
        ax.set_xticklabels([f"{t:+d}" if t else "0" for t in ticks_x[::step]], fontsize=7.5, color="#666666")
        ax.set_yticklabels([f"{t:+d}" if t else "0" for t in ticks_z[::step]], fontsize=7.5, color="#666666")
        ax.tick_params(length=0, pad=2)
        for sp in ax.spines.values():
            sp.set_color("#999999")
        count = len(by_layer.get(y, []))
        where = "ground, heart level" if y == 0 else f"{y} above the heart"
        ax.set_title(f"Layer y={y}  ({where})  -  {count} blocks", fontsize=10.5, color="#333333", pad=16)
        ax.text(0.5, 1.004, "N", transform=ax.transAxes, ha="center", va="bottom", fontsize=8, color="#999999")
        ax.text(1.012, 0.5, "E", transform=ax.transAxes, ha="left", va="center", fontsize=8, color="#999999")
    handles = []
    for key in sorted(present, key=lambda k: materials[k].index):
        mat = materials[key]
        ring_letters = {rl for rn, rl in mat.ring_letters.items() if rn <= ring.n}
        extra = ""
        if ring_letters and ring_letters != {mat.plan_letter}:
            extra = "  (in JSON: " + ", ".join(f"ring {rn}: {rl}" for rn, rl in sorted(mat.ring_letters.items())
                                               if rn <= ring.n) + ")"
        handles.append(Patch(facecolor=mat.color, edgecolor="#333333", linewidth=0.6,
                             label=f"{mat.plan_letter:<2}  {mat.name}  x{present[key]}{extra}"))
    fig.legend(handles=handles, loc="lower center", bbox_to_anchor=(0.5, 0.12 / fig_h), ncol=ncol, frameon=False,
               fontsize=9.5, handlelength=1.4, columnspacing=2.0, labelspacing=0.45, alignment="left")
    total = len(blocks)
    fig.text(0.03, 1 - 0.32 / fig_h, f"Build plan: Firmament Ages Shrine after the {ring.age_name} (rings 0-{ring.n})",
             fontsize=15, fontweight="bold", ha="left", va="center", color="#222222")
    fig.text(0.03, 1 - 0.68 / fig_h,
             f"{w} x {d} footprint, {layers} layer{'s' if layers != 1 else ''}, {total} blocks.  Coordinates are "
             f"offsets from the Shrine Heart (x east, z south; north is up).  Dashed square = heart column.  "
             f"Cell codes are the material codes of the legend.",
             fontsize=10, color="#555555", va="center")
    fig.subplots_adjust(left=side_in / fig_w, right=1 - side_in / fig_w, top=1 - (title_in + panel_title_in) / fig_h,
                        bottom=(legend_in + 0.35) / fig_h, wspace=gap_in / panel_in, hspace=(panel_title_in + 0.3) / panel_in)
    fig.savefig(out, facecolor="#ffffff")
    plt.close(fig)


def render_overview(rings: list[Ring], cumul: dict[int, list], materials: dict[str, Material], out: Path,
                    progress=print) -> int:
    """All tiers in a 3-column grid at the same scale, one shared legend below."""
    n = len(rings)
    per_row = min(n, 3)
    nrows = (n + per_row - 1) // per_row
    panel_in, panel_h = 4.4, 4.0                 # one 3D panel, inches
    title_in, panel_title_in = 1.05, 0.62
    all_blocks = cumul[rings[-1].n]
    present = sorted({b[3] for b in all_blocks}, key=lambda k: materials[k].index)
    handles = legend_handles(materials, present)
    fig_w = max(10.67, panel_in * per_row + 0.5)
    ncol = min(4, legend_columns(len(handles), 9))
    legend_rows = (len(handles) + ncol - 1) // ncol
    legend_in = 0.21 * legend_rows + 0.45
    fig_h = title_in + nrows * (panel_h + panel_title_in) + legend_in + 0.25
    fig = plt.figure(figsize=(fig_w, fig_h), dpi=150)
    fig.patch.set_facecolor("#ffffff")
    bounds = bounds_of(all_blocks)
    x_off = (fig_w - per_row * panel_in) / 2
    quads = 0
    for i, ring in enumerate(rings):
        blocks = cumul[ring.n]
        r, c = divmod(i, per_row)
        left = (x_off + c * panel_in) / fig_w
        bottom = (legend_in + 0.25 + (nrows - 1 - r) * (panel_h + panel_title_in)) / fig_h
        ax = fig.add_axes([left, bottom, panel_in / fig_w, panel_h / fig_h], projection="3d")
        ax.set_facecolor((0, 0, 0, 0))
        quads += draw_voxels(ax, blocks, materials, bounds, ring.beam_color, zoom=1.4, edge_alpha=0.18, north_label=False)
        fig.text(left + panel_in / fig_w / 2, bottom + (panel_h + 0.08) / fig_h,
                 f"after the {ring.age_name} (ring {ring.n})", fontsize=11.5, color="#222222", ha="center", va="bottom")
        fig.text(left + panel_in / fig_w / 2, bottom + (panel_h + 0.08) / fig_h - 0.21 / fig_h,
                 f"+ {ring.display_name}: {len(blocks)} blocks, beam {ring.beam_color}",
                 fontsize=9.5, color="#666666", ha="center", va="top")
        progress(f"  overview panel {i + 1}/{n} (ring {ring.n}, {len(blocks)} blocks)")
    fig.legend(handles=handles, loc="lower center", bbox_to_anchor=(0.5, 0.12 / fig_h), ncol=ncol,
               frameon=False, fontsize=9.5, handlelength=1.4, columnspacing=1.6, labelspacing=0.45,
               title="Materials (code)", title_fontsize=10, alignment="left")
    fig.text(0.03, 1 - 0.34 / fig_h, "Firmament Ages Shrine: growth of the sacred site of Caelum, the Firmament",
             fontsize=15, fontweight="bold", ha="left", va="center", color="#222222")
    fig.text(0.03, 1 - 0.70 / fig_h, "Cumulative shrine after each Age. All rings share the Shrine Heart; each Age "
             "adds a larger ring outward. Same scale in every panel; the beam has the ring's beam color.",
             fontsize=10, color="#555555", va="center")
    fig.savefig(out, facecolor="#ffffff")
    plt.close(fig)
    return quads


# --------------------------------------------------------------------------- export
def export_json(rings: list[Ring], cumul: dict[int, list], materials: dict[str, Material], notes: list[str],
                data_root: Path, out: Path) -> dict:
    mats = sorted(materials.values(), key=lambda m: m.index)
    data = {
        "meta": {
            "generated": _dt.datetime.now().isoformat(timespec="seconds"),
            "data_root": str(data_root),
            "deity": DEITY,
            "coordinates": "x east, y up, z south; the Shrine Heart is at (0,0,0) in every ring",
        },
        "materials": [
            {
                "key": m.key, "name": m.name, "color": m.color, "glow": m.glow, "plan_letter": m.plan_letter,
                "ring_letters": {str(k): v for k, v in sorted(m.ring_letters.items())},
                "examples": m.examples[:6], "example_count": len(m.examples),
            } for m in mats
        ],
        "rings": [
            {
                "n": r.n, "id": r.mb_id, "display_name": r.display_name, "age_name": r.age_name,
                "grants": r.grants, "grants_name": r.grants_name,
                "blessing": r.blessing, "blessing_name": r.blessing_name, "blessing_desc": r.blessing_desc,
                "beam_color": r.beam_color, "sky_tint": r.sky_tint, "plinth_key": r.plinth_key,
                "rites": r.rites, "voice": r.voice, "size": list(r.size),
                "letters": {l: materials[k].index for l, k in r.letters.items()},
                "blocks": [[x, y, z, materials[k].index] for (x, y, z, k) in r.blocks],
                "count": len(r.blocks),
            } for r in rings
        ],
        "cumulative": [
            {
                "n": r.n,
                "blocks": [[x, y, z, materials[k].index, rn] for (x, y, z, k, rn) in cumul[r.n]],
                "count": len(cumul[r.n]),
                "bounds": {"min": [min(b[0] for b in cumul[r.n]), 0, min(b[2] for b in cumul[r.n])],
                           "max": [max(b[0] for b in cumul[r.n]), max(b[1] for b in cumul[r.n]),
                                   max(b[2] for b in cumul[r.n])]},
            } for r in rings
        ],
        "notes": notes,
    }
    out.write_text(json.dumps(data, indent=1), encoding="utf-8")
    return data


def build_html(data: dict, template: Path, out: Path) -> None:
    html = template.read_text(encoding="utf-8")
    marker = "/*__SHRINE_DATA__*/null"
    if marker not in html:
        raise SystemExit(f"{template} has no {marker} marker")
    payload = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    # keep the inline <script> intact whatever the data contains
    payload = payload.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    out.write_text(html.replace(marker, payload, 1), encoding="utf-8")


# --------------------------------------------------------------------------- main
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data-root", type=Path, default=DEFAULT_DATA_ROOT, help="data/firmages folder")
    ap.add_argument("--out", type=Path, default=HERE / "out", help="PNG output folder")
    ap.add_argument("--build-html", action="store_true", help="inline shrine_data.json into viewer.html")
    ap.add_argument("--no-png", action="store_true", help="skip the PNG renders")
    args = ap.parse_args(argv)
    t_start = time.perf_counter()

    def progress(msg: str) -> None:
        print(f"[{time.perf_counter() - t_start:6.1f}s] {msg}", flush=True)

    data_root: Path = args.data_root
    if not data_root.is_dir():
        print(f"data root not found: {data_root}")
        return 2
    lang = load_lang(data_root)
    notes: list[str] = []
    materials: dict[str, Material] = {}
    rings: list[Ring] = []
    for n in discover_ring_numbers(data_root):
        ring = parse_ring(n, data_root, lang, materials, notes)
        if ring:
            rings.append(ring)
    if not rings:
        print("no rings found")
        return 1
    numbers = [r.n for r in rings]
    if numbers != list(range(numbers[0], numbers[-1] + 1)) or numbers[0] != 0:
        notes.append(f"ring numbers are not contiguous from 0: {numbers}")
    assign_plan_codes(materials)
    cumul = {r.n: cumulative_blocks(rings, r.n, notes) for r in rings}
    # heart position sanity across rings: every ring must contain the heart at the origin
    for r in rings:
        if not any((x, y, z) == (0, 0, 0) and k == HEART_BLOCK for x, y, z, k in r.blocks):
            notes.append(f"ring {r.n}: no Shrine Heart at the shared heart position")
    progress(f"parsed {len(rings)} rings, {len(materials)} materials, {len(cumul[rings[-1].n])} blocks in the final shrine")

    args.out.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    if not args.no_png:
        for r in rings:
            title = f"Firmament Ages Shrine, ring {r.n} alone: {r.display_name}"
            sub = (f"Built in the {r.age_name}; {r.size[0]} x {r.size[2]} footprint, {r.size[1]} layer"
                   f"{'s' if r.size[1] != 1 else ''}, {len(r.blocks)} blocks. Heart and plinth shown for alignment.")
            p = args.out / f"ring_{r.n}.png"
            quads = render_3d(r.blocks, materials, r, title, sub, p, ring_legend=r)
            written.append(p)
            progress(f"wrote {p.name} ({quads} quads)")

            blocks = cumul[r.n]
            minx, maxx, minz, maxz, maxy = bounds_of(blocks)
            title = f"Firmament Ages Shrine, after the {r.age_name} (ring {r.n})"
            sub = (f"Rings 0-{r.n} stacked; {maxx - minx + 1} x {maxz - minz + 1} footprint, {maxy + 1} layers, "
                   f"{len(blocks)} blocks. Newest ring: {r.display_name}.")
            p = args.out / f"shrine_after_age_{r.n}.png"
            quads = render_3d(blocks, materials, r, title, sub, p, ring_legend=None)
            written.append(p)
            progress(f"wrote {p.name} ({quads} quads)")

            p = args.out / f"plan_after_age_{r.n}.png"
            render_plan(blocks, materials, r, p)
            written.append(p)
            progress(f"wrote {p.name}")
        p = args.out / "overview_all_rings.png"
        quads = render_overview(rings, cumul, materials, p, progress)
        written.append(p)
        progress(f"wrote {p.name} ({quads} quads in {len(rings)} panels)")

    json_path = HERE / "shrine_data.json"
    data = export_json(rings, cumul, materials, notes, data_root, json_path)
    written.append(json_path)
    progress(f"wrote {json_path.name}")
    if args.build_html:
        template = HERE / "viewer_template.html"
        html_out = HERE / "viewer.html"
        build_html(data, template, html_out)
        written.append(html_out)
        progress(f"wrote {html_out.name} ({html_out.stat().st_size / 1024:.0f} KB)")

    print("\nRings:", ", ".join(f"{r.n} ({r.age_name}, {len(r.blocks)} blocks, grants {r.grants})" for r in rings))
    print("Materials:", ", ".join(f"{m.plan_letter}={m.name}" for m in sorted(materials.values(), key=lambda m: m.index)))
    if notes:
        print("\nData notes:")
        for note in notes:
            print(f"  - {note}")
    print("\nOutputs:")
    for p in written:
        print(f"  {p}")
    print(f"\nTotal time: {time.perf_counter() - t_start:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
