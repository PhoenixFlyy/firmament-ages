#!/usr/bin/env python
"""Generate every firmages texture (KubeJS items and firmages-core blocks) as 16x16 pixel art from palettes + shapes.

    python dev/gen_textures.py            # write all PNGs (+ .mcmeta), patch the block model texture maps, build the sheets
    python dev/gen_textures.py --check    # only validate: every referenced texture exists, sizes, orphans, KubeJS items
    python dev/gen_textures.py --sheet-only

Item textures go to kubejs/assets/firmages/textures/item/<path>.png (KubeJS 2101 builds the item/generated model
itself when no model file exists, texture firmages:item/<path>). Block textures go to the mod,
mod/firmages-core/src/main/resources/assets/firmages/textures/block/<name>.png, and the block models in
models/block/*.json get their "textures" map rewritten from MODEL_TEXTURES below (the element geometry is untouched).

Tuning: AGE palettes are 5 tones (outline, dark, mid, light, highlight) built from the shrine beam colors
(data/firmages/firmages_shrine/tier/ring_N.json) and the Age themes of Doc 08. Item shapes are 16-row ASCII grids,
"." is transparent, every other character is looked up in the item's legend. Block tiles are procedural (seeded noise,
bricks, bevels) with ASCII overlays for carvings. Change a palette or a grid and rerun.

Animated textures (second pass, 2026-10-01) are vertical strips of 4..8 frames plus a .mcmeta next to the PNG; the
builders draw frame by frame. The consecrated block family (firmages:consecrated_<role>, see dev/textures-notes.md)
is one pale "sky-marble" material with faint gold veining; the nine accent overlays consecrated_accent_<N>.png carry a
thin rune/edge pattern in ring N's beam_color and are transparent elsewhere, so they composite over any base.

The contact sheets (8x upscaled, labelled) land in dev/shrine-viewer/out/textures_sheet.png and consecrated_sheet.png
(not tracked).
"""
import argparse
import json
import math
import random
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
KJS_ASSETS = ROOT / "kubejs" / "assets" / "firmages"
KJS_TEX = KJS_ASSETS / "textures"
MOD_ASSETS = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "assets" / "firmages"
MOD_TEX = MOD_ASSETS / "textures"
TIERS = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "data" / "firmages" / "firmages_shrine" / "tier"
ITEMS_JS = ROOT / "kubejs" / "startup_scripts" / "items.js"
OUT = ROOT / "dev" / "shrine-viewer" / "out"
SHEET = OUT / "textures_sheet.png"
CONSECRATED_SHEET = OUT / "consecrated_sheet.png"

# ---------------------------------------------------------------------------------------------------------------------
# Palettes: 5 tones each, index 0 = outline (darkest) .. 4 = highlight. Hex without alpha.
# ---------------------------------------------------------------------------------------------------------------------
PAL = {
    # Age 0, Stone Age: hearth stone, clay, terracotta, ember (beam #FFB347)
    "stone": ["#26241f", "#4a4742", "#6e6b64", "#8f8c84", "#aaa79e"],
    "clay": ["#3f4350", "#646a78", "#8e97a6", "#aeb7c4", "#cdd4de"],
    "terracotta": ["#3b1c0e", "#7c3b1c", "#b0582c", "#cf7a44", "#e8a068"],
    "ember": ["#5a1a04", "#9c2e0a", "#e05a1a", "#ffb347", "#ffe8b0"],
    "coal": ["#0c0c0e", "#1c1c20", "#2c2d32", "#3d3f46", "#53565e"],
    # Age 1, Bronze Age: bronze, patina, gold (beam #FFD54A)
    "bronze": ["#3f2a10", "#7a4f1c", "#b0762e", "#d49a4a", "#f0c270"],
    "patina": ["#1a3630", "#2d5a50", "#3f7a6e", "#5a9a8a", "#7fb8a8"],
    "gold": ["#6a4a08", "#b08018", "#e2b42e", "#ffd54a", "#fff0a8"],
    # Age 2, Iron Age: steel, heat (beam #E06030)
    "steel": ["#1e2026", "#4b4f5a", "#7b808c", "#a9aeb8", "#d6dae2"],
    "heat": ["#6a1a08", "#a83418", "#e06030", "#ff9a60", "#ffd0a8"],
    # Age 3, Arcane Age: arcane purple (beam #9B59FF)
    "arcane": ["#1c0a30", "#3f2370", "#6b3fb0", "#9b59ff", "#c9a0ff"],
    "rune": ["#8a60d0", "#b48cff", "#d4b8ff", "#ebd8ff", "#ffffff"],
    # Age 4, Industrial Age: coke-black iron, steam white (beam #F0F0F0), rust
    "iron": ["#141416", "#33363a", "#5a5e64", "#80858c", "#a4a9b0"],
    "steam": ["#8a8a8a", "#b4b4b4", "#d8d8d8", "#f0f0f0", "#ffffff"],
    "rust": ["#4a1e0e", "#7a3418", "#a0522d", "#c87040", "#e09a68"],
    # Age 5, Electric Age: aluminium, electric cyan (beam #40E0FF)
    "alu": ["#3a4650", "#6a7a84", "#9aa8b0", "#c8d4da", "#e8f0f4"],
    "cyan": ["#0a4a60", "#1480a0", "#1ab4d8", "#40e0ff", "#b0f4ff"],
    "pcb": ["#0f2a14", "#1f5028", "#2f7038", "#3f8c48", "#5aa860"],
    "copper": ["#5a3a10", "#8a5a20", "#c8903c", "#e0b060", "#f4d890"],
    # Age 6, Information Age: quartz white, data green (beam #40FF80)
    "quartz": ["#5a5a60", "#a8a4a0", "#d8d4d0", "#ece8e4", "#faf8f6"],
    "data": ["#0a3c1c", "#157a3a", "#22c060", "#40ff80", "#b0ffd0"],
    # Age 7, Space Age: desh (Ad Astra), steel, deep navy, starlight (beam #E0E8FF, sky #05051A)
    "desh": ["#4a2018", "#8a4a38", "#c87a5a", "#e8a080", "#f8c8a8"],
    "navy": ["#05051a", "#0c1030", "#1a2050", "#2c3470", "#4a5490"],
    "star": ["#5a6080", "#8a90a0", "#b8c0d8", "#e0e8ff", "#ffffff"],
    # Age 8, Quantum Age: draconium violet, calorite (beam #E0B0FF)
    "draconium": ["#1a0a2a", "#4a1a6a", "#8a3ac0", "#c070ff", "#e0b0ff"],
    "calorite": ["#3a0a14", "#6a1a2a", "#9a2a3a", "#c84a50", "#e88080"],
    # Age 9, Singularity: void black, white-violet (beam fallback #F4F0FF, sky #B8B0FF)
    "void": ["#000000", "#0c0614", "#1a0a2a", "#2c1448", "#3e2060"],
    "violet": ["#4a2a80", "#7a4ac0", "#9b59ff", "#d8b8ff", "#f4f0ff"],
    "paper": ["#5a4a3a", "#b8a888", "#e0d4b4", "#f0e8cc", "#faf4e0"],
    # dusts
    "zinc": ["#3a4248", "#6a7680", "#9aa6b0", "#c0cad2", "#dde4ea"],
    "bismuth": ["#2e2432", "#5e5066", "#8e8096", "#b4a8bc", "#d4ccd8"],
    "bismuth_iris": ["#d47fa8", "#7fc4c0", "#e0b870", "#90a0e0", "#ffffff"],
    # consecrated family: pale sky-marble, faint gold veins, lamp glass
    "marble": ["#8e8ba0", "#bdb9cc", "#dcd9e6", "#edebf4", "#fbfafe"],
    "vein": ["#c3a86c", "#d9c48c", "#eadcb2", "#f3e8c8", "#faf3dc"],
    "lampglass": ["#3a3650", "#55506e", "#6e6888", "#8c86a4", "#a8a2bc"],
    "lampglow": ["#b8702a", "#e8a848", "#ffd070", "#ffe8a8", "#fff6d8"],
}

# Beam colour per ring (accent index) from the tier files; the fallback is the Doc 08 ladder.
BEAM_FALLBACK = ["#FFB347", "#FFD54A", "#E06030", "#9B59FF", "#F0F0F0", "#40E0FF", "#40FF80", "#E0E8FF", "#E0B0FF"]


def beam_colors():
    out = []
    for n in range(9):
        p = TIERS / f"ring_{n}.json"
        color = BEAM_FALLBACK[n]
        if p.exists():
            color = json.loads(p.read_text(encoding="utf-8")).get("response", {}).get("beam_color", color)
        out.append(color)
    return out


def rgb(hex_color, a=255):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def tones(name):
    return [rgb(c) for c in PAL[name]]


def scale_rgb(c, f, a=None):
    """Darken (f < 1) or lighten towards white (f > 1) an RGBA tuple."""
    if f <= 1:
        out = tuple(int(v * f) for v in c[:3])
    else:
        out = tuple(int(v + (255 - v) * (f - 1)) for v in c[:3])
    return out + ((c[3] if a is None else a),)


def legend(**kw):
    """legend(o=("stone",0), m=("stone",2)) -> {char: RGBA}. Values may also be hex strings or RGBA tuples."""
    out = {}
    for ch, spec in kw.items():
        if isinstance(spec, str):
            out[ch] = rgb(spec)
        elif isinstance(spec, tuple) and len(spec) == 2 and isinstance(spec[0], str):
            out[ch] = tones(spec[0])[spec[1]]
        else:
            out[ch] = spec
    return out


def shade(name):
    """The standard 5-letter legend for one palette: o outline, d dark, m mid, l light, h highlight."""
    return legend(o=(name, 0), d=(name, 1), m=(name, 2), l=(name, 3), h=(name, 4))


# ---------------------------------------------------------------------------------------------------------------------
# Drawing helpers
# ---------------------------------------------------------------------------------------------------------------------
def new_tex(h=16):
    return Image.new("RGBA", (16, h), (0, 0, 0, 0))


def draw_grid(img, rows, leg, oy=0):
    """Paint a 16-row ASCII grid. Rows shorter than 16 are padded with '.', longer rows are an error."""
    if len(rows) != 16:
        raise ValueError(f"grid needs 16 rows, got {len(rows)}")
    px = img.load()
    for y, row in enumerate(rows):
        if len(row) > 16:
            raise ValueError(f"row {y} longer than 16: {row!r}")
        row = row.ljust(16, ".")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in leg:
                raise KeyError(f"row {y} col {x}: char {ch!r} not in legend")
            px[x, y + oy] = leg[ch]
    return img


def grid_tex(rows, leg):
    return draw_grid(new_tex(), rows, leg)


def noise_fill(img, pal, rng, weights=(1, 3, 6, 3, 1), cluster=2, oy=0, grain=0.25):
    """Fill 16x16 with clustered noise over the palette tones (weights per tone, cluster = blob size)."""
    t = tones(pal)
    px = img.load()
    cells = {}
    for y in range(16):
        for x in range(16):
            key = (x // cluster, y // cluster)
            if key not in cells:
                cells[key] = rng.choices(range(5), weights=weights)[0]
            idx = cells[key]
            if rng.random() < grain:  # single-pixel grain
                idx = max(0, min(4, idx + rng.choice((-1, 1))))
            px[x, y + oy] = t[idx]
    return img


def bricks_fill(img, pal, rng, bw=8, bh=4, weights=(0, 2, 6, 3, 1), oy=0, mortar=0):
    """Stone-brick tile: staggered bricks bw x bh, 1 px mortar in tone `mortar`, light top/left edge, dark bottom/right."""
    t = tones(pal)
    px = img.load()
    for y in range(16):
        row = y // bh
        off = (row % 2) * (bw // 2)
        for x in range(16):
            bx = (x + off) % bw
            by = y % bh
            if by == bh - 1 or bx == bw - 1:
                px[x, y + oy] = t[mortar]
                continue
            idx = rng.choices(range(5), weights=weights)[0]
            if by == 0 or bx == 0:
                idx = min(4, max(idx, 3))
            elif by == bh - 2 or bx == bw - 2:
                idx = min(idx, 1)
            px[x, y + oy] = t[idx]
    return img


def bevel(img, pal, inset=0, oy=0, light=4, dark=1, corner=2):
    """1 px light top/left and dark bottom/right frame (tone 4 / tone 1), outline tone 0 outside if inset > 0."""
    t = tones(pal)
    px = img.load()
    lo, hi = inset, 15 - inset
    for i in range(lo, hi + 1):
        px[i, lo + oy] = t[light]
        px[lo, i + oy] = t[light]
        px[i, hi + oy] = t[dark]
        px[hi, i + oy] = t[dark]
    px[hi, lo + oy] = t[corner]
    px[lo, hi + oy] = t[corner]
    return img


def overlay(img, rows, leg, oy=0):
    """Paint only the non-'.' cells of an ASCII grid on top of an existing tile."""
    return draw_grid(img, rows, leg, oy)


def ring_pixels(r0, r1, cx=7.5, cy=7.5):
    """Pixels whose centre lies in the annulus r0 <= d < r1 around (cx, cy), sorted clockwise from the left."""
    pts = []
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if r0 <= d < r1:
                pts.append((math.atan2(y - cy, x - cx), x, y))
    pts.sort()
    return [(x, y) for _, x, y in pts]


def strip(frames):
    """Stack 16x16 frames into one vertical strip."""
    out = new_tex(16 * len(frames))
    for i, fr in enumerate(frames):
        out.alpha_composite(fr, (0, 16 * i))
    return out


def anim(frametime, interpolate=False):
    return {"animation": {"frametime": frametime, "interpolate": interpolate}}


# ---------------------------------------------------------------------------------------------------------------------
# Item shapes. Light comes from the top-left: left/top edges "l"/"h", right/bottom edges "d".
# ---------------------------------------------------------------------------------------------------------------------
ITEMS = {}  # id path -> (age label, builder() -> RGBA strip, mcmeta or None)


def item(path, age, rows, leg):
    ITEMS[path] = (age, lambda: grid_tex(rows, leg), None)


def anim_item(path, age, mcmeta):
    """Decorator: the function returns a list of 16x16 frames."""
    def deco(fn):
        ITEMS[path] = (age, lambda: strip(fn()), mcmeta)
        return fn
    return deco


IDOL = [
    "................",
    ".....ooooo......",
    "....ohlllmo.....",
    "....ohemedo.....",
    "....olmmmdo.....",
    ".....odmdo......",
    "...oooohlooo....",
    "..ohlllmmmmmddo.",
    "..olmmmgmgmmmdo.",
    "..oolmmmgmmmdoo.",
    "...ooolmmmdooo..",
    ".....ohmmdo.....",
    ".....olmmdo.....",
    "....oolmmdoo....",
    "...ohlmdolmmdo..",
    "...ooooo.ooooo..",
]
item("unfired_hearth_idol", "Age 0", IDOL, {**shade("clay"), **legend(e=("clay", 0), g=("clay", 1))})
item("hearth_idol", "Age 0", IDOL, {**shade("terracotta"), **legend(e=("terracotta", 0), g=("ember", 3))})

item("hearthstone", "Age 0", [
    "................",
    ".....oooooo.....",
    "....ohhllmmo....",
    "...ohlllAmmmo...",
    "..ohllmAAmmmdo..",
    "..ohlmABBAmmdo..",
    "..olmmABCCBAmdo.",
    "..olmABCDDCBAdo.",
    "..olmABCDDCBAdo.",
    "..olmmABCCBAddo.",
    "..oldmmABBAmddo.",
    "..oddmmmAAmAddo.",
    "...odmmmmmdddo..",
    "....oddddddoo...",
    ".....oooooo.....",
    "................",
], {**shade("stone"), **legend(A=("ember", 0), B=("ember", 1), C=("ember", 2), D=("ember", 4))})

item("sky_disc", "Age 1", [
    "................",
    ".....oooooo.....",
    "....ohhllmmdo...",
    "...ohlmmmmmmdo..",
    "..ohlmBCmmmBmdo.",
    "..olmBCCmmBCCBdo",
    "..olmBCCmmCDDCdo",
    "..olBCCmmmCDDCdo",
    "..olmBCCmmBCCBdo",
    "..olmmBCmmmBmddo",
    "..oldmmBmmmmmddo",
    "...odmmmmmmdddo.",
    "....oddmmmdddo..",
    ".....oooooo.....",
    "................",
    "................",
], {**shade("patina"), **legend(B=("gold", 1), C=("gold", 3), D=("gold", 4))})

item("steel_heart", "Age 2", [
    "................",
    "...oooo..oooo...",
    "..ohhllooollmdo.",
    ".ohlmhmmddmhmmdo",
    ".olmmmmmmmmmmmdo",
    ".olmmoABBAommmdo",
    ".olmmABCDCBAmddo",
    ".olmmABCDCBAmddo",
    ".olmmoABBAommddo",
    "..olmmmoommmddo.",
    "..olhmmmmmmhddo.",
    "...olmmmmmmddo..",
    "....olmmmmddo...",
    ".....olmmddo....",
    "......olddo.....",
    ".......ooo......",
], {**shade("steel"), **legend(A=("heat", 0), B=("heat", 2), C=("heat", 3), D=("heat", 4))})

# Arcane Keystone: a solid wedge (wide top, narrow foot) with one carved rune. Awakened: the wedge split by a white-hot
# fissure with sparks around it, so the two read apart at a glance.
KEYSTONE = [
    "................",
    ".oooooooooooooo.",
    ".ohhhhllllmmmddo",
    ".ohlllllllmmmmdo",
    ".ohllmRRRRRmmmdo",
    "..olmRmmmmmRmdo.",
    "..olmRmRRRmRmdo.",
    "..olmmmmRmmmmdo.",
    "...olmmmRmmmdo..",
    "...olmmmRmmmdo..",
    "....olmmRmmdo...",
    "....olmmmmmdo...",
    ".....olmmmdo....",
    ".....olmmddo....",
    "......ooooo.....",
    "................",
]


@anim_item("arcane_keystone", "Age 3", anim(12, True))
def _():
    frames = []
    for R in (("rune", 2), ("rune", 3), ("rune", 2), ("rune", 1)):
        frames.append(grid_tex(KEYSTONE, {**shade("arcane"), **legend(R=R)}))
    return frames


AWAKENED = [
    "....s.......s...",
    ".oooooooooooooo.",
    ".ohhhhhllllmmddo",
    ".ohlllRRRRRlmmdo",
    "..ohlRlllllRmdo.",
    "..oolllRRRlllos.",
    "...WWWHHHHHWWW..",
    "s.WHHHHHHHHHHHW.",
    "...WWWHHHHHWWW..",
    "...olllRRlllmo..",
    "....ollRlRllos..",
    "....ollRRRlmo...",
    ".s...ollllmo....",
    ".....ollmmo.....",
    "......ooooo.....",
    "................",
]
AWAKENED_SPARKS = [
    [(4, 0), (12, 0), (0, 7), (1, 12)],
    [(14, 5), (13, 10), (4, 0), (0, 7)],
    [(12, 0), (14, 5), (1, 12), (13, 10)],
    [(0, 7), (12, 0), (4, 0), (14, 5)],
]


@anim_item("awakened_keystone", "Age 8", anim(8, True))
def _():
    frames = []
    base_leg = {**legend(o=("arcane", 0), d=("arcane", 2), m=("arcane", 3), l=("arcane", 4), h=("violet", 3)),
                **legend(R=("rune", 4), s=(0, 0, 0, 0))}
    core = [("violet", 4), ("rune", 3), ("violet", 3), ("rune", 3)]
    glow = [("violet", 3), ("violet", 2), ("violet", 1), ("violet", 2)]
    for f in range(4):
        leg = {**base_leg, **legend(H=core[f], W=glow[f])}
        img = grid_tex(AWAKENED, leg)
        px = img.load()
        for (x, y) in AWAKENED_SPARKS[f]:
            px[x, y] = tones("violet")[4]
        frames.append(img)
    return frames


item("arcane_gearbox", "Age 4", [
    "................",
    "...oo.oooo.oo...",
    "..ohhoohhloomo..",
    "..ohhlllllllmo..",
    ".oohlmmmmmmmdoo.",
    ".ohlmmAAAAmmmdo.",
    ".olmmABBBBAmmdo.",
    ".olmmABCCBAmmdo.",
    ".olmmABCCBAmddo.",
    ".olmmABBBBAmddo.",
    ".ohlmmAAAAmmddo.",
    ".oodmmmmmmmddoo.",
    "..odmmmmmmmddo..",
    "..oddoodddooddo.",
    "...oo.oooo.oo...",
    "................",
], {**shade("steel"), **legend(A=("arcane", 1), B=("arcane", 2), C=("arcane", 4))})

item("pressure_core", "Age 4", [
    "................",
    "....oooooooo....",
    "...ohhllllmmdo..",
    "..ohlmmmmmmmmdo.",
    "..olhmooooommdo.",
    "..olmoWWWWWomdo.",
    "..olmoWwwwWomdo.",
    "..olmoWwNwWomdo.",
    "..olmoWWNWWomdo.",
    "..olmooooooomdo.",
    "..olhmmmmmmhmdo.",
    "..olRRoddddRRdo.",
    "..oolmmmmmmmdoo.",
    "...oolmmmdddoo..",
    "....oooooooo....",
    "................",
], {**shade("iron"), **legend(W=("steam", 3), w=("steam", 1), N="#d03020", R=("rust", 2))})

item("attuned_circuit", "Age 5", [
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhhdo.",
    "..ohTTTmmmTTTdo.",
    "..ohTmmmmmmmTdo.",
    "..olTmmCCmmmTdo.",
    "..olTmmBCDmmTdo.",
    "..olTmBCDDCmTdo.",
    "..olTmABCDBmTdo.",
    "..olTmmABCmmTdo.",
    "..olTmmmAmmmTdo.",
    "..olTTTTTTTTTdo.",
    "..oddddddddddoo.",
    "..oooooooooooo..",
    "..P.P.P.P.P.P...",
    "..p.p.p.p.p.p...",
], {**shade("pcb"), **legend(T=("copper", 3), A=("cyan", 0), B=("cyan", 2), C=("cyan", 3), D=("cyan", 4),
                             P=("alu", 3), p=("alu", 1))})

HUMMING = [
    "................",
    "....oooooooo....",
    "...ohhllllmmdo..",
    "..ohlmmmmmmmmdo.",
    "..oAbbbbbbbbbAo.",
    "..olmmmmmmmmddo.",
    "..olmmmmmCCmddo.",
    "..olmmmmCDCmddo.",
    "..olmmmCDCCmddo.",
    "..olmmCDDDDCddo.",
    "..olmmmmCDCmddo.",
    "..olmmmmCDmmddo.",
    "..olmmmmCmmmddo.",
    "..oAbbbbbbbbbAo.",
    "..oolmmmmmmddoo.",
    "....oooooooo....",
]


@anim_item("humming_core", "Age 5", anim(3))
def _():
    frames = []
    cyan = tones("cyan")
    bolt_c = [3, 4, 3, 2]
    bolt_d = [4, 4, 4, 3]
    coil = [1, 2, 4, 2]  # the current runs along the coil band
    for f in range(4):
        leg = {**shade("alu"), **legend(A=("cyan", 0), C=cyan[bolt_c[f]], D=cyan[bolt_d[f]], b=cyan[1])}
        img = grid_tex(HUMMING, leg)
        px = img.load()
        for y in (4, 13):
            for x in range(4, 13):
                px[x, y] = cyan[coil[(x + f) % 4]]
        frames.append(img)
    return frames


DATA_MATRIX = [
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohllllllllddo.",
    "..odddmmmmdddoo.",
    "..oooooooooooo..",
    "................",
    "................",
]
# 8 x 8 glyph pattern, scrolls up one row per frame (period 8)
DATA_SCROLL = [
    "CB.C.BC.",
    ".C.CB.C.",
    "B.CB.C.B",
    "........",
    "C.BC..CB",
    ".CB.C.B.",
    "..C.BC.C",
    "........",
]


@anim_item("data_matrix", "Age 6", anim(3))
def _():
    frames = []
    data = tones("data")
    for f in range(8):
        img = grid_tex(DATA_MATRIX, {**shade("quartz"), **legend(A=("data", 0))})
        px = img.load()
        for r in range(8):
            row = DATA_SCROLL[(r + f) % 8]
            for c, ch in enumerate(row):
                if ch == "C":
                    px[4 + c, 3 + r] = data[3] if r != 6 else data[4]
                elif ch == "B":
                    px[4 + c, 3 + r] = data[1]
        frames.append(img)
    return frames


STAR_CHART = [
    "................",
    "..oooooooooooo..",
    "..ohhhlllmmmddo.",
    "..olNNNNNNNNNdo.",
    "..olN1NNNN2NNdo.",
    "..olNn3nNNnNNdo.",
    "..olNNNn4nNNNdo.",
    "..olNNNNNn5NNdo.",
    "..olN6NNNNn7Ndo.",
    "..olNn8nNNNNNdo.",
    "..olNNNn1NN2Ndo.",
    "..olNNNNNNNNNdo.",
    "..olNNN3NNNNNdo.",
    "..olmmmmmmmdddo.",
    "..oooooooooooo..",
    "................",
]


@anim_item("star_chart", "Age 7", anim(8, True))
def _():
    frames = []
    star = tones("star")
    cycle = [4, 3, 1, 2]
    for f in range(4):
        leg = {**shade("desh"), **legend(N=("navy", 1), n=("navy", 3))}
        for digit in "12345678":
            leg[digit] = star[cycle[(f + int(digit)) % 4]]
        frames.append(grid_tex(STAR_CHART, leg))
    return frames


SPHERE = [
    "................",
    ".....oooooo.....",
    "....ohhllmmdo...",
    "...ohllmmmmmdo..",
    "..ohlmmmmmmmmdo.",
    "..ohlmmmmmmmddo.",
    "..olmmmmmmmmmdo.",
    "..olmmmmmmmmmdo.",
    "..olmmmmmmmmddo.",
    "..olmmmmmmmmddo.",
    "..oldmmmmmmdddo.",
    "...odmmmmmmddo..",
    "....oddmmdddo...",
    ".....oooooo.....",
    "................",
    "................",
]
CORE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......W........",
    "......WWW.......",
    ".......W........",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]


@anim_item("quantum_core", "Age 8", anim(2))
def _():
    frames = []
    cyan = tones("cyan")
    ring = ring_pixels(3.6, 4.6, 7.5, 7.5)
    n = len(ring)
    for f in range(8):
        img = grid_tex(SPHERE, shade("draconium"))
        overlay(img, CORE, legend(W="#ffffff"))
        px = img.load()
        head = (f * n) // 8
        for k, (x, y) in enumerate(ring):
            back = (head - k) % n
            if back == 0:
                px[x, y] = (255, 255, 255, 255)
            elif back <= 2:
                px[x, y] = cyan[4]
            elif back <= 4:
                px[x, y] = cyan[3]
            elif back <= 6:
                px[x, y] = cyan[2]
            else:
                px[x, y] = cyan[1]
        frames.append(img)
    return frames


@anim_item("ultimate_singularity", "Age 9", anim(3, True))
def _():
    frames = []
    void, violet = tones("void"), tones("violet")
    rays = [(7, 0), (8, 0), (0, 7), (0, 8), (15, 7), (15, 8), (7, 15), (8, 15)]
    for f in range(8):
        img = new_tex()
        px = img.load()
        phase = 2 * math.pi * f / 8
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 3.2:
                    px[x, y] = void[0]
                elif d < 4.2:
                    px[x, y] = void[1] if d < 3.7 else void[2]
                elif d < 6.4:
                    ang = math.atan2(y - 7.5, x - 7.5)
                    b = 0.5 + 0.5 * math.cos(2 * ang - phase)
                    idx = 1 + int(round(b * 3))
                    if d >= 5.8:
                        idx = max(0, idx - 2)
                    px[x, y] = violet[idx]
                elif d < 7.1:
                    px[x, y] = violet[0]
        for (x, y) in rays:
            px[x, y] = violet[3] if (f // 2) % 2 == 0 else violet[1]
        frames.append(img)
    return frames


item("origin_coordinates", "Age 9", [
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhldo.",
    "..ohIIlIIIlIldo.",
    "..ohlllllllllmo.",
    "..ohIlIIlIlIlmo.",
    "..ohlllllllllmo.",
    "..ohIIIlIlIIlmo.",
    "..ohlllllllllmo.",
    "..ohIlIlllBBlmo.",
    "..ohlllllBCCBmo.",
    "..ohlllllBCDBmo.",
    "..ohllllABBBAmo.",
    "..oddmmmmmmmmmo.",
    "..oooooooooooo..",
    "................",
], {**shade("paper"), **legend(I=("arcane", 2), A=("draconium", 1), B=("draconium", 3), C=("violet", 3),
                               D=("violet", 4))})

DUST = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "........o.......",
    "......ohlmo.....",
    ".....ohlhmmo....",
    "....ohlmmhmdo...",
    "...ohllmmmmddo..",
    "..ohlmhmmmmdddo.",
    "..olmmmmhmddiddo",
    ".oolllmmmmmddddo",
    ".ommmmmdddiddoo.",
    "..oooooooooooo..",
    "................",
]
item("dust/zinc", "dust", DUST, {**shade("zinc"), **legend(i=("zinc", 4))})
item("dust/bismuth", "dust", DUST, {**shade("bismuth"), **legend(i=("bismuth_iris", 0), h=("bismuth_iris", 1))})

# ---------------------------------------------------------------------------------------------------------------------
# Block tiles. Each entry: name -> builder() returning a 16x16 (or 16 x 16*frames) RGBA image, plus optional mcmeta.
# ---------------------------------------------------------------------------------------------------------------------
BLOCKS = {}  # name -> (label, builder, mcmeta or None)


def block(name, label, mcmeta=None):
    def deco(fn):
        BLOCKS[name] = (label, fn, mcmeta)
        return fn
    return deco


def seeded(name):
    return random.Random(name)


@block("shrine_heart_base", "heart base")
def _():
    img = new_tex()
    noise_fill(img, "stone", seeded("heart_base"), weights=(1, 4, 6, 3, 0), cluster=2)
    bevel(img, "stone")
    return img


@block("shrine_heart_side", "heart side")
def _():
    img = new_tex()
    noise_fill(img, "terracotta", seeded("heart_side"), weights=(0, 1, 7, 3, 0), cluster=3)
    bevel(img, "terracotta")
    # carved flame in a ring (the hearth sign of Caelum)
    overlay(img, [
        "................",
        "................",
        ".....dddddd.....",
        "....d......d....",
        "...d...dd...d...",
        "...d..dhd...d...",
        "...d..d.dd..d...",
        "...d.dh..dd.d...",
        "...d.d.ee.d.d...",
        "...d.d.ee.d.d...",
        "...d..d..d..d...",
        "...d...dd...d...",
        "....d......d....",
        ".....dddddd.....",
        "................",
        "................",
    ], legend(d=("terracotta", 0), h=("terracotta", 4), e=("ember", 2)))
    return img


@block("shrine_heart_band", "heart band")
def _():
    img = new_tex()
    noise_fill(img, "gold", seeded("heart_band"), weights=(0, 0, 4, 6, 1), cluster=3)
    bevel(img, "gold")
    overlay(img, [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......d.....d...",
        ".....dhd...dhd..",
        "..d.dhhhd.dhhhd.",
        ".dhd.dhd...dhd..",
        "..d...d.....d...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], legend(d=("gold", 0), h=("gold", 4)))
    return img


COLD_TOP = [
    "................",
    "................",
    "...cc...c.......",
    "..cccc.ccc..c...",
    "...cc..ccc.ccc..",
    "......c.c...c...",
    "..c..cccc.......",
    ".ccc.cccc..cc...",
    "..c...cc..cccc..",
    ".......c..cccc..",
    "...cc......cc...",
    "..cccc..c.......",
    "..cccc.ccc......",
    "...cc..ccc......",
    "........c.......",
    "................",
]
# where the lit cracks glow (between the coals)
CRACKS = [
    "................",
    "................",
    "................",
    "......g.........",
    "......g....g....",
    ".....g.g.g......",
    ".........g......",
    "....g....g......",
    "....g.g.........",
    "....g.g..g......",
    ".......g..g.....",
    "......g.........",
    "......g.........",
    "......g.........",
    "................",
    "................",
]


@block("shrine_heart_top", "heart top (cold)")
def _():
    img = new_tex()
    noise_fill(img, "stone", seeded("heart_top"), weights=(2, 5, 4, 1, 0), cluster=2)
    bevel(img, "stone")
    overlay(img, COLD_TOP, legend(c=("coal", 1)))
    overlay(img, CRACKS, legend(g=("coal", 0)))
    return img


@block("shrine_heart_top_lit", "heart top (lit, 4 frames)", anim(6, True))
def _():
    frames = 4
    img = new_tex(16 * frames)
    glow_levels = [("ember", 2), ("ember", 3), ("ember", 4), ("ember", 3)]
    coal_levels = [("ember", 0), ("heat", 0), ("ember", 1), ("heat", 0)]
    for f in range(frames):
        oy = 16 * f
        noise_fill(img, "stone", seeded("heart_top"), weights=(2, 5, 4, 1, 0), cluster=2, oy=oy)
        bevel(img, "stone", oy=oy)
        overlay(img, COLD_TOP, legend(c=coal_levels[f]), oy=oy)
        overlay(img, CRACKS, legend(g=glow_levels[f]), oy=oy)
    return img


@block("offering_plinth_side", "plinth side")
def _():
    img = new_tex()
    bricks_fill(img, "stone", seeded("plinth_side"))
    return img


PLINTH_RING = [
    "................",
    "................",
    "................",
    "......rrrr......",
    "....rr....rr....",
    "....r......r....",
    "...r........r...",
    "...r...cc...r...",
    "...r...cc...r...",
    "...r........r...",
    "....r......r....",
    "....rr....rr....",
    "......rrrr......",
    "................",
    "................",
    "................",
]


@block("offering_plinth_top", "plinth top")
def _():
    img = new_tex()
    noise_fill(img, "stone", seeded("plinth_top"), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, "stone")
    overlay(img, PLINTH_RING, legend(r=("stone", 0), c=("stone", 0)))
    return img


@block("offering_plinth_top_awakened", "plinth top (awakened)")
def _():
    img = new_tex()
    noise_fill(img, "stone", seeded("plinth_top"), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, "stone")
    overlay(img, PLINTH_RING, legend(r=("gold", 3), c=("gold", 4)))
    return img


ORIGIN_GLYPHS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "gggggggggggggggg",
    ".G..G.GG..G..GG.",
    ".GG.G.G..GG.G.G.",
    "gggggggggggggggg",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]


@block("origin_altar_side", "altar side")
def _():
    img = new_tex()
    noise_fill(img, "void", seeded("altar_side"), weights=(0, 3, 6, 3, 1), cluster=3)
    bevel(img, "void")
    overlay(img, ORIGIN_GLYPHS, legend(g=("violet", 0), G=("violet", 4)))
    return img


ALTAR_TOP = [
    "................",
    ".G............G.",
    "................",
    ".....rrrrrr.....",
    "....r......r....",
    "...r........r...",
    "...r........r...",
    "...r...WW...r...",
    "...r...WW...r...",
    "...r........r...",
    "...r........r...",
    "....r......r....",
    ".....rrrrrr.....",
    "................",
    ".G............G.",
    "................",
]


@block("origin_altar_top", "altar top (6 frames)", anim(4, True))
def _():
    frames = 6
    img = new_tex(16 * frames)
    violet = tones("violet")
    inner = ring_pixels(2.2, 3.3, 7.5, 7.5)  # the inner ring turns
    n = len(inner)
    eye = [violet[4], violet[3], violet[2], violet[3], violet[4], (255, 255, 255, 255)]
    corner = [violet[3], violet[2], violet[1], violet[2], violet[3], violet[4]]
    for f in range(frames):
        oy = 16 * f
        noise_fill(img, "void", seeded("altar_top"), weights=(1, 4, 6, 2, 0), cluster=2, oy=oy)
        bevel(img, "void", oy=oy)
        overlay(img, ALTAR_TOP, legend(r=("violet", 0), W=eye[f], G=corner[f]), oy=oy)
        px = img.load()
        for k, (x, y) in enumerate(inner):
            lit = (k + f * 2) % 6
            px[x, y + oy] = violet[3] if lit == 0 else (violet[2] if lit == 1 else violet[1])
    return img


@block("origin_altar_bottom", "altar bottom")
def _():
    img = new_tex()
    noise_fill(img, "void", seeded("altar_bottom"), weights=(1, 4, 6, 2, 0), cluster=2)
    bevel(img, "void")
    return img


REACTOR_SIDE = [
    "o..............o",
    "................",
    "..vvvvvvvvvvvv..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..vvvvvvvvvvvv..",
    "................",
    "o..............o",
]


@block("reactor_controller_side", "reactor side")
def _():
    img = new_tex()
    noise_fill(img, "iron", seeded("reactor_side"), weights=(1, 5, 5, 1, 0), cluster=4)
    bevel(img, "iron")
    overlay(img, REACTOR_SIDE, legend(o=("iron", 4), v=("violet", 1)))
    return img


@block("reactor_controller_bottom", "reactor bottom")
def _():
    img = new_tex()
    noise_fill(img, "iron", seeded("reactor_bottom"), weights=(1, 5, 5, 1, 0), cluster=4)
    bevel(img, "iron")
    overlay(img, REACTOR_SIDE, legend(o=("iron", 4), v=("iron", 0)))
    return img


SCREEN = [
    "................",
    ".oooooooooooooo.",
    ".oSSSSSSSSSSSSo.",
    ".oSbbbbSSbbbSSo.",
    ".oSSSSSSSSSSSSo.",
    ".oSbbSSSSbbbbSo.",
    ".oSSSSSSSSSSSSo.",
    ".oSbbbbbbSSbbSo.",
    ".oSSSSSSSSSSSSo.",
    ".oSbbbSSbbbbSSo.",
    ".oSSSSSSSSSSSSo.",
    ".oSSSSSSSSSLLSo.",
    ".oSSSSSSSSSLLSo.",
    ".oSSSSSSSSSSSSo.",
    ".oooooooooooooo.",
    "................",
]


@block("reactor_controller_top", "reactor top (monitor)")
def _():
    img = new_tex()
    noise_fill(img, "iron", seeded("reactor_top"), weights=(1, 5, 5, 1, 0), cluster=4)
    bevel(img, "iron")
    overlay(img, SCREEN, legend(o=("iron", 0), S=("coal", 2), b=("steel", 1), L=("heat", 2)))
    return img


@block("reactor_controller_top_ready", "reactor top (ready)")
def _():
    img = new_tex()
    noise_fill(img, "iron", seeded("reactor_top"), weights=(1, 5, 5, 1, 0), cluster=4)
    bevel(img, "iron")
    overlay(img, SCREEN, legend(o=("iron", 0), S=("violet", 3), b=("violet", 4), L=("data", 3)))
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Consecrated family (firmages:consecrated_<role>, dev/textures-notes.md). One material: pale sky-marble with faint
# gold veins. The mod registers the models; these textures have no model in this repo yet (the checker knows).
# ---------------------------------------------------------------------------------------------------------------------
CONSECRATED = {}  # texture name -> (label, builder, mcmeta or None); written like BLOCKS


def consecrated(name, label, mcmeta=None):
    def deco(fn):
        CONSECRATED[name] = (label, fn, mcmeta)
        return fn
    return deco


def marble_fill(img, rng, oy=0, veins=2, mask=None):
    """Smooth pale marble (tones 2..4) plus `veins` thin gold veins as short random walks. mask(x, y) -> bool limits
    the veins to the stone (not the mortar or a frame)."""
    noise_fill(img, "marble", rng, weights=(0, 0, 3, 8, 3), cluster=3, oy=oy, grain=0.08)
    vein = tones("vein")
    px = img.load()
    for _ in range(veins):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        dx, dy = rng.choice(((1, 0), (1, 1), (1, -1), (0, 1))), None
        for step in range(rng.randrange(6, 11)):
            if 0 <= x < 16 and 0 <= y < 16 and (mask is None or mask(x, y)):
                px[x, y + oy] = vein[1] if step % 3 else vein[0]
            # mostly along dx, sometimes wobble
            if rng.random() < 0.3:
                dx = rng.choice(((1, 0), (1, 1), (1, -1), (0, 1), (-1, 1)))
            x, y = x + dx[0], y + dx[1]
    return img


def soft_bevel(img, oy=0):
    return bevel(img, "marble", oy=oy, light=4, dark=1, corner=2)


@consecrated("consecrated_stone", "stone")
def _():
    img = new_tex()
    marble_fill(img, seeded("consecrated_stone"), veins=2)
    soft_bevel(img)
    return img


@consecrated("consecrated_brick", "brick")
def _():
    img = new_tex()
    rng = seeded("consecrated_brick")
    bricks_fill(img, "marble", rng, weights=(0, 0, 3, 7, 2), mortar=1)
    px = img.load()

    def in_brick(x, y):
        return px[x, y] != tones("marble")[1]
    # faint veins on two bricks only
    vein = tones("vein")
    for (x0, y0, w) in ((1, 0, 5), (8, 8, 6)):
        for i in range(w):
            x, y = x0 + i, y0 + (i % 3 == 2) + (i // 4)
            if 0 <= x < 16 and 0 <= y < 16 and in_brick(x, y):
                px[x, y] = vein[1] if i % 3 else vein[0]
    return img


@consecrated("consecrated_pillar_top", "pillar top")
def _():
    img = new_tex()
    marble_fill(img, seeded("consecrated_pillar_top"), veins=1)
    soft_bevel(img)
    vein = tones("vein")
    px = img.load()
    for (x, y) in ring_pixels(4.6, 5.6):
        px[x, y] = vein[1]
    for (x, y) in ring_pixels(5.6, 6.3):
        px[x, y] = vein[0]
    for (x, y) in ring_pixels(0, 1.2):
        px[x, y] = vein[2]
    return img


@consecrated("consecrated_pillar_side", "pillar side")
def _():
    img = new_tex()
    rng = seeded("consecrated_pillar_side")
    marble_fill(img, rng, veins=0)
    marble, vein = tones("marble"), tones("vein")
    px = img.load()
    flute = [1, 4, 3, 2]  # groove edge, lit flank, flank, shade
    for y in range(16):
        for x in range(16):
            idx = flute[x % 4]
            if rng.random() < 0.12:
                idx = max(1, min(4, idx + rng.choice((-1, 1))))
            px[x, y] = marble[idx]
    for x in range(16):  # gold fillets at both ends: stacked pillars show a 2 px band
        px[x, 0] = vein[1] if x % 4 else vein[0]
        px[x, 15] = vein[0] if x % 4 else vein[1]
    return img


LAMP_FRAME = [
    "FFFFFFFFFFFFFFFF",
    "FFFFFFFFFFFFFFFF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FF............FF",
    "FFFFFFFFFFFFFFFF",
    "FFFFFFFFFFFFFFFF",
]
LAMP_LATTICE = [
    "................",
    "................",
    "..g..........g..",
    "...g...gg...g...",
    "....g..gg..g....",
    ".....g.gg.g.....",
    "......g..g......",
    "...gggg..gggg...",
    "...gggg..gggg...",
    "......g..g......",
    ".....g.gg.g.....",
    "....g..gg..g....",
    "...g...gg...g...",
    "..g..........g..",
    "................",
    "................",
]


def lamp(lit):
    img = new_tex()
    marble_fill(img, seeded("consecrated_lamp"), veins=1)
    soft_bevel(img)
    px = img.load()
    glow = tones("lampglow") if lit else tones("lampglass")
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            idx = 4 if d < 2.2 else 3 if d < 4.0 else 2 if d < 6.0 else 1
            px[x, y] = glow[idx]
    overlay(img, LAMP_LATTICE, legend(g=("vein", 0) if lit else ("vein", 1)))
    px[2, 2] = tones("marble")[1]
    px[13, 13] = tones("marble")[1]
    return img


@consecrated("consecrated_lamp", "lamp (unlit)")
def _():
    return lamp(False)


@consecrated("consecrated_lamp_lit", "lamp (lit)")
def _():
    return lamp(True)


@consecrated("consecrated_metal", "metal")
def _():
    img = new_tex()
    rng = seeded("consecrated_metal")
    noise_fill(img, "marble", rng, weights=(0, 0, 6, 6, 1), cluster=3, grain=0.08)
    marble, vein = tones("marble"), tones("vein")
    px = img.load()
    for i in range(16):  # outer soft edge
        px[i, 0] = marble[4]
        px[0, i] = marble[4]
        px[i, 15] = marble[1]
        px[15, i] = marble[1]
    for i in range(2, 14):  # gold plate frame at inset 2
        px[i, 2] = vein[2]
        px[2, i] = vein[2]
        px[i, 13] = vein[0]
        px[13, i] = vein[0]
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):  # rivets
        px[x, y] = vein[3]
        px[x + 1, y + 1] = vein[0]
    for x in range(6, 10):  # a short centre bar
        px[x, 7] = vein[1]
        px[x, 8] = vein[0]
    return img


@consecrated("consecrated_glass", "glass (translucent)")
def _():
    img = new_tex()
    marble, vein = tones("marble"), tones("vein")
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = scale_rgb(marble[4], 1.0, a=56)
    for i in range(16):
        px[i, 0] = marble[3]
        px[0, i] = marble[3]
        px[i, 15] = marble[1]
        px[15, i] = marble[1]
    px[15, 0] = marble[2]
    px[0, 15] = marble[2]
    for i in range(2, 8):  # one faint vein and a corner gleam
        px[i, 2 + i] = scale_rgb(vein[2], 1.0, a=150)
    for (x, y) in ((2, 1), (1, 2), (3, 1)):
        px[x, y] = scale_rgb(marble[4], 1.0, a=190)
    for i in range(9, 13):
        px[i, i] = scale_rgb(marble[4], 1.0, a=140)
    return img


@consecrated("consecrated_trim", "trim")
def _():
    img = new_tex()
    marble_fill(img, seeded("consecrated_trim"), veins=1, mask=lambda x, y: y < 5 or y > 10)
    soft_bevel(img)
    vein = tones("vein")
    px = img.load()
    for x in range(16):
        px[x, 5] = vein[0]
        px[x, 6] = vein[3]
        px[x, 9] = vein[1]
        px[x, 10] = vein[0]
        px[x, 7] = vein[2] if x % 4 in (1, 2) else vein[1]
        px[x, 8] = vein[1] if x % 4 in (1, 2) else vein[0]
    return img


@consecrated("consecrated_scaffold", "scaffold (cutout)")
def _():
    img = new_tex()
    marble_fill(img, seeded("consecrated_scaffold"), veins=0)
    marble, vein = tones("marble"), tones("vein")
    px = img.load()
    for y in range(2, 14):
        for x in range(2, 14):
            px[x, y] = (0, 0, 0, 0)
    for i in range(16):
        px[i, 0] = marble[4]
        px[0, i] = marble[4]
        px[i, 15] = marble[1]
        px[15, i] = marble[1]
        px[i, 1] = marble[3] if i not in (0, 15) else px[i, 1]
        px[1, i] = marble[3] if i not in (0, 15) else px[1, i]
        px[i, 14] = marble[2] if i not in (0, 15) else px[i, 14]
        px[14, i] = marble[2] if i not in (0, 15) else px[14, i]
    for i in range(2, 14):  # the X brace
        px[i, i] = marble[3] if i < 8 else marble[2]
        px[15 - i, i] = marble[3] if i < 8 else marble[2]
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):  # gold pins
        px[x, y] = vein[1]
    px[7, 7] = vein[2]
    px[8, 8] = vein[0]
    px[8, 7] = vein[1]
    px[7, 8] = vein[1]
    return img


# Accent overlays: corner brackets, mid-edge ticks and one 5x5 rune per Age, in the beam colour, with a thin dark halo
# so the pale Ages (Industrial white, Space starlight) still read on the pale marble. Transparent elsewhere.
RUNES = [
    [".....", "..#..", ".###.", "#####", ".#.#."],  # 0 Stone: hearth flame
    ["..#..", ".###.", "#####", ".###.", "..#.."],  # 1 Bronze: sun disc
    ["#####", "..#..", "..#..", ".###.", "#####"],  # 2 Iron: anvil
    [".###.", "#.#.#", "#####", "#.#.#", ".###."],  # 3 Arcane: eye
    ["#.#.#", ".###.", "#####", ".###.", "#.#.#"],  # 4 Industrial: gear
    ["..##.", ".##..", "#####", "..##.", ".##.."],  # 5 Electric: bolt
    ["##.##", "#...#", "..#..", "#...#", "##.##"],  # 6 Information: matrix
    ["..#..", "..#..", "#####", ".###.", "#...#"],  # 7 Space: star
    ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."],  # 8 Quantum: lattice
]
BRACKETS = [
    "................",
    ".###...##...###.",
    ".#............#.",
    ".#............#.",
    "................",
    "................",
    "................",
    ".#............#.",
    ".#............#.",
    "................",
    "................",
    "................",
    ".#............#.",
    ".#............#.",
    ".###...##...###.",
    "................",
]


def accent_overlay(color_hex, rune):
    img = new_tex()
    px = img.load()
    core = rgb(color_hex)
    halo = scale_rgb(core, 0.35, a=120)
    cells = set()
    for y, row in enumerate(BRACKETS):
        for x, ch in enumerate(row):
            if ch == "#":
                cells.add((x, y))
    for ry, row in enumerate(rune):
        for rx, ch in enumerate(row):
            if ch == "#":
                cells.add((6 + rx, 6 + ry))
    for (x, y) in cells:
        for (nx, ny) in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in cells:
                px[nx, ny] = halo
    for (x, y) in cells:
        px[x, y] = core
    return img


def register_accents():
    for n, color in enumerate(beam_colors()):
        CONSECRATED[f"consecrated_accent_{n}"] = (f"accent {n} {color}", (lambda c=color, r=RUNES[n]: accent_overlay(c, r)), None)


register_accents()

# Block model texture maps (models/block/<file>.json -> "textures"). Geometry stays as it is.
B = "firmages:block/"
MODEL_TEXTURES = {
    "shrine_heart": {"particle": B + "shrine_heart_base", "side": B + "shrine_heart_side",
                     "base": B + "shrine_heart_base", "top": B + "shrine_heart_top", "band": B + "shrine_heart_band"},
    "shrine_heart_lit": {"particle": B + "shrine_heart_base", "side": B + "shrine_heart_side",
                         "base": B + "shrine_heart_base", "top": B + "shrine_heart_top_lit",
                         "band": B + "shrine_heart_band"},
    "offering_plinth": {"particle": B + "offering_plinth_side", "side": B + "offering_plinth_side",
                        "top": B + "offering_plinth_top"},
    "offering_plinth_awakened": {"particle": B + "offering_plinth_side", "side": B + "offering_plinth_side",
                                 "top": B + "offering_plinth_top_awakened"},
    "origin_altar": {"side": B + "origin_altar_side", "top": B + "origin_altar_top",
                     "bottom": B + "origin_altar_bottom"},
    "reactor_controller": {"side": B + "reactor_controller_side", "top": B + "reactor_controller_top",
                           "bottom": B + "reactor_controller_bottom"},
    "reactor_controller_ready": {"side": B + "reactor_controller_side", "top": B + "reactor_controller_top_ready",
                                 "bottom": B + "reactor_controller_bottom"},
}


# ---------------------------------------------------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------------------------------------------------
def write_png(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def write_json(path, data):
    """LF line endings on every platform (the repo and the jar carry LF)."""
    with path.open("w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2) + "\n")


def write_with_meta(img, out, mcmeta):
    write_png(img, out)
    meta = out.with_suffix(".png.mcmeta")
    if mcmeta:
        write_json(meta, mcmeta)
    elif meta.exists():
        meta.unlink()


def generate():
    written = []
    for path, (age, builder, mcmeta) in ITEMS.items():
        out = KJS_TEX / "item" / (path + ".png")
        write_with_meta(builder(), out, mcmeta)
        written.append(out)
    for table in (BLOCKS, CONSECRATED):
        for name, (label, builder, mcmeta) in table.items():
            out = MOD_TEX / "block" / (name + ".png")
            write_with_meta(builder(), out, mcmeta)
            written.append(out)
    for model, textures in MODEL_TEXTURES.items():
        p = MOD_ASSETS / "models" / "block" / (model + ".json")
        data = json.loads(p.read_text(encoding="utf-8"))
        data["textures"] = textures
        write_json(p, data)
    return written


# ---------------------------------------------------------------------------------------------------------------------
# Contact sheets
# ---------------------------------------------------------------------------------------------------------------------
def checker(d, x, y, w, h):
    for yy in range(0, h, 16):
        for xx in range(0, w, 16):
            c = (70, 70, 76, 255) if ((xx + yy) // 16) % 2 == 0 else (58, 58, 64, 255)
            d.rectangle([x + xx, y + yy, x + xx + min(15, w - xx - 1), y + yy + min(15, h - yy - 1)], fill=c)


def frames_of(p):
    img = Image.open(p).convert("RGBA")
    return [img.crop((0, 16 * f, 16, 16 * (f + 1))) for f in range(img.height // 16)]


def sheet(scale=8, cols=5):
    entries = []
    for path, (age, builder, mcmeta) in ITEMS.items():
        entries.append((path, age, KJS_TEX / "item" / (path + ".png"), mcmeta))
    for name, (label, builder, mcmeta) in BLOCKS.items():
        entries.append((name, label, MOD_TEX / "block" / (name + ".png"), mcmeta))
    font = ImageFont.load_default(size=11)
    title_font = ImageFont.load_default(size=14)
    cell = 16 * scale
    pad = 10
    label_h = 40
    cw, ch = max(cell + pad * 2, 184), cell + pad + label_h
    rows_n = (len(entries) + cols - 1) // cols
    animated = [e for e in entries if e[3]]
    fscale = 6
    fcell = 16 * fscale
    anim_h = 40 + len(animated) * (fcell + 30)
    max_frames = max(len(frames_of(e[2])) for e in animated) if animated else 0
    width = max(cw * cols, 200 + max_frames * (fcell + 6) + pad)
    out = Image.new("RGBA", (width, ch * rows_n + anim_h), (40, 40, 44, 255))
    d = ImageDraw.Draw(out)
    for i, (name, sub, p, mcmeta) in enumerate(entries):
        cx, cy = (i % cols) * cw + pad, (i // cols) * ch + pad
        checker(d, cx, cy, cell, cell)
        big = frames_of(p)[0].resize((cell, cell), Image.NEAREST)
        out.alpha_composite(big, (cx, cy))
        d.text((cx, cy + cell + 4), name, fill=(235, 235, 235, 255), font=font)
        d.text((cx, cy + cell + 20), sub, fill=(160, 160, 170, 255), font=font)
    y0 = ch * rows_n + 10
    d.text((pad, y0), "Animated textures, frames left to right (frametime in ticks, i = interpolate)",
           fill=(235, 235, 235, 255), font=title_font)
    y = y0 + 30
    for name, sub, p, mcmeta in animated:
        frames = frames_of(p)
        a = mcmeta["animation"]
        d.text((pad, y + fcell // 2 - 14), name, fill=(235, 235, 235, 255), font=font)
        d.text((pad, y + fcell // 2 + 2), f"{len(frames)} frames, {a['frametime']}t{' i' if a.get('interpolate') else ''}",
               fill=(160, 160, 170, 255), font=font)
        for f, fr in enumerate(frames):
            fx = 200 + f * (fcell + 6)
            checker(d, fx, y, fcell, fcell)
            out.alpha_composite(fr.resize((fcell, fcell), Image.NEAREST), (fx, y))
        y += fcell + 30
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(SHEET)
    return SHEET


def consecrated_sheet(scale=8):
    bases = [n for n in CONSECRATED if not n.startswith("consecrated_accent_")]
    accents = [n for n in CONSECRATED if n.startswith("consecrated_accent_")]
    font = ImageFont.load_default(size=11)
    cell = 16 * scale
    gap = 8
    left = 150
    top = 30
    cols = 1 + len(accents)
    out = Image.new("RGBA", (left + cols * (cell + gap), top + len(bases) * (cell + gap) + 10), (40, 40, 44, 255))
    d = ImageDraw.Draw(out)
    d.text((left, 8), "base", fill=(235, 235, 235, 255), font=font)
    for j, a in enumerate(accents):
        d.text((left + (j + 1) * (cell + gap), 8), CONSECRATED[a][0], fill=(235, 235, 235, 255), font=font)
    for i, name in enumerate(bases):
        y = top + i * (cell + gap)
        d.text((8, y + cell // 2 - 14), name.replace("consecrated_", ""), fill=(235, 235, 235, 255), font=font)
        d.text((8, y + cell // 2 + 2), CONSECRATED[name][0], fill=(160, 160, 170, 255), font=font)
        base = frames_of(MOD_TEX / "block" / (name + ".png"))[0]
        for j in range(cols):
            x = left + j * (cell + gap)
            checker(d, x, y, cell, cell)
            tile = base.copy()
            if j > 0:
                tile.alpha_composite(frames_of(MOD_TEX / "block" / (accents[j - 1] + ".png"))[0])
            out.alpha_composite(tile.resize((cell, cell), Image.NEAREST), (x, y))
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(CONSECRATED_SHEET)
    return CONSECRATED_SHEET


# ---------------------------------------------------------------------------------------------------------------------
# Checker
# ---------------------------------------------------------------------------------------------------------------------
def tex_file(ref):
    """'firmages:block/x' -> Path in the mod; 'firmages:item/x' -> Path under kubejs (items) unless the mod has it."""
    ns, _, path = ref.partition(":")
    if ns != "firmages":
        return None
    mod = MOD_TEX / (path + ".png")
    kjs = KJS_TEX / (path + ".png")
    return mod if mod.exists() or not kjs.exists() else kjs


def check():
    errors, warnings = [], []
    referenced = set()
    # 1. models in both asset roots
    for root in (MOD_ASSETS, KJS_ASSETS):
        for p in sorted((root / "models").rglob("*.json")) if (root / "models").exists() else []:
            data = json.loads(p.read_text(encoding="utf-8"))
            for key, ref in data.get("textures", {}).items():
                if ref.startswith("#"):
                    continue
                if not ref.startswith("firmages:"):
                    warnings.append(f"{p.relative_to(ROOT)}: texture {key} = {ref} is not a pack texture")
                    continue
                f = tex_file(ref)
                referenced.add(f.resolve())
                if not f.exists():
                    errors.append(f"{p.relative_to(ROOT)}: texture {key} = {ref} missing ({f.relative_to(ROOT)})")
            parent = data.get("parent", "")
            if parent.startswith("firmages:"):
                mp = root / "models" / (parent.split(":", 1)[1] + ".json")
                if not mp.exists():
                    errors.append(f"{p.relative_to(ROOT)}: parent model {parent} missing")
    # 1b. the consecrated family (dev/textures-notes.md) must be used by the mod's models
    for name in CONSECRATED:
        f = MOD_TEX / "block" / (name + ".png")
        if f.resolve() not in referenced:
            errors.append(f"{f.relative_to(ROOT)}: consecrated texture not referenced by any mod model")
    # 2. blockstates -> models
    for p in sorted((MOD_ASSETS / "blockstates").glob("*.json")):
        data = json.loads(p.read_text(encoding="utf-8"))
        models = []
        for v in data.get("variants", {}).values():
            models += [m["model"] for m in (v if isinstance(v, list) else [v])]
        for part in data.get("multipart", []):
            a = part["apply"]
            models += [m["model"] for m in (a if isinstance(a, list) else [a])]
        for m in models:
            ns, _, path = m.partition(":")
            mp = MOD_ASSETS / "models" / (path + ".json")
            if ns == "firmages" and not mp.exists():
                errors.append(f"{p.relative_to(ROOT)}: model {m} missing")
    # 3. mod block items need a model
    for p in sorted((MOD_ASSETS / "blockstates").glob("*.json")):
        im = MOD_ASSETS / "models" / "item" / p.name
        if not im.exists():
            errors.append(f"block {p.stem}: no item model models/item/{p.name}")
    # 4. KubeJS items: every registered id needs textures/item/<path>.png (KubeJS generates the model)
    ids = re.findall(r"event\.create\('firmages:([^']+)'", ITEMS_JS.read_text(encoding="utf-8"))
    for path in ids:
        model = KJS_ASSETS / "models" / "item" / (path + ".json")
        f = KJS_TEX / "item" / (path + ".png")
        if model.exists():
            continue  # its textures were checked in step 1
        referenced.add(f.resolve())
        if not f.exists():
            errors.append(f"items.js: firmages:{path} has no texture {f.relative_to(ROOT)}")
    for path in ITEMS:
        if path not in ids:
            warnings.append(f"gen_textures.py draws firmages:{path} but items.js does not register it")
    # 5. every PNG: 16 wide, height 16 or n*16 with .mcmeta; orphans; mcmeta shape and LF
    for root in (MOD_TEX, KJS_TEX):
        if not root.exists():
            continue
        for p in sorted(root.rglob("*.png")):
            img = Image.open(p)
            if img.width != 16 or img.height % 16 != 0:
                errors.append(f"{p.relative_to(ROOT)}: size {img.width}x{img.height}, want 16 x n*16")
            meta = p.with_suffix(".png.mcmeta")
            if img.height > 16 and not meta.exists():
                errors.append(f"{p.relative_to(ROOT)}: {img.height // 16} frames but no .mcmeta")
            if img.height == 16 and meta.exists():
                warnings.append(f"{p.relative_to(ROOT)}: single frame but has .mcmeta")
            if img.height > 16 * 8:
                warnings.append(f"{p.relative_to(ROOT)}: {img.height // 16} frames, keep animations at 4..8")
            if p.resolve() not in referenced:
                warnings.append(f"{p.relative_to(ROOT)}: not referenced by any model or items.js")
        for m in sorted(root.rglob("*.mcmeta")):
            if not m.with_suffix("").exists():
                errors.append(f"{m.relative_to(ROOT)}: mcmeta without png")
            raw = m.read_bytes()
            if b"\r\n" in raw:
                errors.append(f"{m.relative_to(ROOT)}: CRLF line endings")
            data = json.loads(raw.decode("utf-8"))
            if "animation" not in data or not isinstance(data["animation"].get("frametime", 1), int):
                errors.append(f"{m.relative_to(ROOT)}: no animation.frametime")
    for p in sorted((MOD_ASSETS / "models").rglob("*.json")):
        if b"\r\n" in p.read_bytes():
            errors.append(f"{p.relative_to(ROOT)}: CRLF line endings")
    for w in warnings:
        print("warn:", w)
    for e in errors:
        print("ERROR:", e)
    print(f"check: {len(referenced)} referenced textures, {len(errors)} errors, {len(warnings)} warnings")
    return not errors


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="validate only, write nothing")
    ap.add_argument("--sheet-only", action="store_true", help="only rebuild the contact sheets")
    a = ap.parse_args()
    if a.check:
        sys.exit(0 if check() else 1)
    if not a.sheet_only:
        written = generate()
        print(f"wrote {len(written)} textures")
    print("sheet:", sheet().relative_to(ROOT))
    print("sheet:", consecrated_sheet().relative_to(ROOT))
    sys.exit(0 if check() else 1)


if __name__ == "__main__":
    main()
