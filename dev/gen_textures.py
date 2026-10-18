#!/usr/bin/env python
"""Generate every firmages texture (KubeJS items and firmages-core blocks) as 16x16 pixel art from palettes + shapes.

    python dev/gen_textures.py            # write all PNGs (+ .mcmeta), patch the block model texture maps, build the sheets
    python dev/gen_textures.py --check    # only validate: every referenced texture exists, sizes, orphans, KubeJS items
    python dev/gen_textures.py --sheet-only

Item textures go to kubejs/assets/firmages/textures/item/<path>.png (KubeJS 2101 builds the item/generated model
itself when no model file exists, texture firmages:item/<path>); items the mod registers itself (MOD_ITEMS: the sky
metals of SPEC 25 and the Lens Crown's items of SPEC 27) go to
mod/firmages-core/src/main/resources/assets/firmages/textures/item/<path>.png. Block textures go to the mod,
mod/firmages-core/src/main/resources/assets/firmages/textures/block/<name>.png, and the block models in
models/block/*.json get their "textures" map rewritten from MODEL_TEXTURES below (the element geometry is untouched).
GUI icons of the mod (MOD_GUI, 16x16 like the items) go to mod/.../assets/firmages/textures/gui/<name>.png.

Tuning: AGE palettes are 5 tones (outline, dark, mid, light, highlight) built from the shrine beam colors
(data/firmages/firmages_shrine/tier/ring_N.json) and the Age themes of Doc 08. Item shapes are 16-row ASCII grids,
"." is transparent, every other character is looked up in the item's legend. Block tiles are procedural (seeded noise,
bricks, bevels) with ASCII overlays for carvings. Change a palette or a grid and rerun.

Animated textures (second pass, 2026-10-01) are vertical strips of 4..8 frames plus a .mcmeta next to the PNG; the
builders draw frame by frame. The consecrated block family (firmages:consecrated_<role>, see dev/archive/textures-notes.md)
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
    # Tally Chute (SPEC 22): lumber frame with bronze bands
    "lumber": ["#2e1f12", "#5a3d22", "#7f5a36", "#a07a4e", "#c29e6e"],
    # dusts
    "zinc": ["#3a4248", "#6a7680", "#9aa6b0", "#c0cad2", "#dde4ea"],
    "bismuth": ["#2e2432", "#5e5066", "#8e8096", "#b4a8bc", "#d4ccd8"],
    "bismuth_iris": ["#d47fa8", "#7fc4c0", "#e0b870", "#90a0e0", "#ffffff"],
    # osmium (the pack vein): Mekanism's blue-grey metal; gangue in the ore pieces uses "stone"
    "osmium": ["#1c2633", "#435a70", "#6f8aa3", "#9fbad1", "#d8e8f6"],
    # consecrated family: pale sky-marble, faint gold veins, lamp glass
    "marble": ["#8e8ba0", "#bdb9cc", "#dcd9e6", "#edebf4", "#fbfafe"],
    "vein": ["#c3a86c", "#d9c48c", "#eadcb2", "#f3e8c8", "#faf3dc"],
    "lampglass": ["#3a3650", "#55506e", "#6e6888", "#8c86a4", "#a8a2bc"],
    "lampglow": ["#b8702a", "#e8a848", "#ffd070", "#ffe8a8", "#fff6d8"],
    # Lens Crown (SPEC 27): storm glass and the seven celestial alloys; its meteoric iron frames take the sky metal's
    # "meteoric" palette of the Starfall section (SPEC 25)
    "stormglass": ["#3a4a5c", "#5f7890", "#8aa6be", "#b8d0e2", "#e4f0f8"],
    "starlit_iron": ["#26243a", "#4f4c6e", "#8582a8", "#b8b4dc", "#ece8ff"],
    "cloudsteel": ["#3c4652", "#6e7c8c", "#a4b2c2", "#d2dce6", "#f6faff"],
    "aurora_steel": ["#0c3a34", "#16705e", "#2cb08c", "#7ae8c0", "#d8fff0"],
    "halo_steel": ["#4a3a14", "#8a6e2c", "#c8a85a", "#ecd894", "#fff8dc"],
    "void_steel": ["#04040c", "#141428", "#2a2450", "#4a3c80", "#8070c0"],
    "quasar_alloy": ["#3a0a34", "#741a68", "#b03aa0", "#e080d4", "#ffd0f8"],
    "firmament_alloy": ["#5a4a30", "#a08ca0", "#d4c8e8", "#f0e8ff", "#ffffff"],
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

HEARTHSTONE = [
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
]
HEARTHSTONE_EMBER = [(0, 1, 2, 4), (1, 2, 3, 4), (0, 1, 3, 4), (0, 0, 1, 3)]  # A, B, C, D tones per frame


@anim_item("hearthstone", "Age 0", anim(6, True))
def _():
    """The embers in the stone breathe: brighter, settle, dim, settle."""
    frames = []
    for a, b, c, d in HEARTHSTONE_EMBER:
        leg = {**shade("stone"), **legend(A=("ember", a), B=("ember", b), C=("ember", c), D=("ember", d))}
        frames.append(grid_tex(HEARTHSTONE, leg))
    return frames

SKY_DISC = [
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
]
SKY_DISC_GLINT = [7, 12, 17, 22]  # x + y of the glint band per frame, top-left to bottom-right


@anim_item("sky_disc", "Age 1", anim(8, True))
def _():
    """A glint sweeps diagonally across the bronze disc and lights the gold sun and moon as it passes."""
    frames = []
    leg = {**shade("patina"), **legend(B=("gold", 1), C=("gold", 3), D=("gold", 4))}
    lit = {"m": tones("patina")[3], "l": tones("patina")[4], "B": tones("gold")[3], "C": tones("gold")[4]}
    for band in SKY_DISC_GLINT:
        img = grid_tex(SKY_DISC, leg)
        px = img.load()
        for y, row in enumerate(SKY_DISC):
            for x, ch in enumerate(row):
                if ch in lit and band <= x + y <= band + 1:
                    px[x, y] = lit[ch]
        frames.append(img)
    return frames

STEEL_HEART = [
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
]
STEEL_HEART_HEAT = [(0, 2, 3, 4), (1, 3, 4, 4), (0, 2, 3, 4), (0, 1, 2, 3)]  # A, B, C, D tones per frame


@anim_item("steel_heart", "Age 2", anim(5, True))
def _():
    """Forge heat pulses in the core like a slow heartbeat."""
    frames = []
    for a, b, c, d in STEEL_HEART_HEAT:
        leg = {**shade("steel"), **legend(A=("heat", a), B=("heat", b), C=("heat", c), D=("heat", d))}
        frames.append(grid_tex(STEEL_HEART, leg))
    return frames

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

PRESSURE_CORE = [
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
]
PRESSURE_NEEDLE_TIP = [8, 9, 8, 7]  # x of the needle tip in row 7 per frame; the pivot (8, 8) stays


@anim_item("pressure_core", "Age 4", anim(3))
def _():
    """The gauge needle trembles around the red line."""
    frames = []
    leg = {**shade("iron"), **legend(W=("steam", 3), w=("steam", 1), N="#d03020", R=("rust", 2))}
    for tip in PRESSURE_NEEDLE_TIP:
        img = grid_tex(PRESSURE_CORE, leg)
        px = img.load()
        for x in (7, 8, 9):
            px[x, 7] = leg["N"] if x == tip else leg["w"]
        frames.append(img)
    return frames

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

# Expedition items of the Age tokens (dev/decisions-while-away.md, 2026-10-14): the Frontier Compass, the shard of the
# Cold Forge and the relic the Create line finishes from it (Iron Age), the Hollow Keystone of the Nether fortresses and
# its Create-line stage (Arcane Age), the flight recorder of the Mars temple (Space Age).
item("frontier_compass", "Age 2", [
    "................",
    ".....oooooo.....",
    "...oohhlllmoo...",
    "..ohlppppppqmo..",
    "..olppppppNqdo..",
    ".ohpppppppNnqdo.",
    ".olppppppNnqqdo.",
    ".olpppppcnqqqdo.",
    ".olppppscqqqqdo.",
    ".olpppssqqqqqdo.",
    ".ompppsqqqqqqdo.",
    "..omqqqqqqqqdo..",
    "..odmqqqqqqddo..",
    "...ooddmmddoo...",
    ".....oooooo.....",
    "................",
], {**shade("steel"), **legend(p=("paper", 3), q=("paper", 2), N=("heat", 3), n=("heat", 1), s=("steel", 1),
                               c=("steel", 4))})
FRONTIER_SHARD = [
    "................",
    "................",
    ".........oo.....",
    "........ohlo....",
    ".......ohlmdo...",
    "......ohlmmdo...",
    ".....ohlmrmddo..",
    "....ohlmmmmdo...",
    "...ohlmgmmddo...",
    "..ohlmmmmrddo...",
    "..olmmrmmddo....",
    "...odmmmddo.....",
    "....oddddo......",
    ".....oooo.......",
    "................",
    "................",
]
item("frontier_shard", "Age 2", FRONTIER_SHARD, {**shade("iron"), **legend(r=("rust", 2), g=("steel", 4))})
item("incomplete_frontier_relic", "Age 2", [
    "................",
    "................",
    ".........oo.....",
    "........ohlo....",
    ".......ohlmdo...",
    "......ohlmmdo...",
    ".....oSSSSSSo...",
    "....oShhllmSo...",
    "...ohSlmmmdSo...",
    "..ohlSSSSSSSo...",
    "..olmmrmmddo....",
    "...odmmmddo.....",
    "....oddddo......",
    ".....oooo.......",
    "................",
    "................",
], {**shade("iron"), **legend(r=("rust", 2), S=("steel", 2))})
item("frontier_relic", "Age 2", [
    "................",
    "...oooooooooo...",
    "..ohhhhllllmdo..",
    "..ohllllmmmmdo..",
    "..ohlNNNNNNmdo..",
    "..ohlNmmmmNmdo..",
    "..ohlNmhhmNmdo..",
    "..olmNmhhmNmdo..",
    "..olmNmmmmNddo..",
    "..olmNNNNNNddo..",
    "...olmmmmmddo...",
    "....olmmmddo....",
    ".....olmddo.....",
    "......oddo......",
    ".......oo.......",
    "................",
], {**shade("steel"), **legend(N=("heat", 3))})
KEYSTONE_SHELL = [
    "................",
    "................",
    "..oooooooooooo..",
    "..ohhllllllmdo..",
    "..ohlmooooomdo..",
    "..ohlmovvvomdo..",
    "...olmovvvomo...",
    "...olmovavomo...",
    "...olmovvvomo...",
    "...odmooooomo...",
    "....odmmmmdo....",
    "....oddddddo....",
    "....oooooooo....",
    "................",
    "................",
    "................",
]
item("hollow_keystone", "Age 3", KEYSTONE_SHELL, {**shade("stone"), **legend(v=("void", 1), a=("arcane", 3))})
item("incomplete_arcane_keystone", "Age 3", [r.replace("vvv", "AvA").replace("vav", "ArA") for r in KEYSTONE_SHELL],
     {**shade("stone"), **legend(v=("void", 1), A=("arcane", 2), r=("rune", 2))})
item("flight_recorder", "Age 7", [
    "................",
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhmo..",
    "..ohlllllllmdo..",
    "..ohlNNNNNNmdo..",
    "..ohlNSnnnNmdo..",
    "..ohlNnnSnNmdo..",
    "..ohlNNNNNNmdo..",
    "..olmmmmmmmmdo..",
    "..olmRmmmmGmdo..",
    "..odmmmmmmmddo..",
    "..oddddddddddo..",
    "..oooooooooooo..",
    "................",
    "................",
], {**shade("desh"), **legend(N=("navy", 2), n=("navy", 1), S=("star", 4), R=("heat", 3), G=("data", 3))})

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
# Osmium (the pack vein, kubejs/startup_scripts/osmium.js): TFC-style ore pieces per grade, the small surface piece, the
# double ingot, and one ore overlay per grade (KubeJS blocks, model tfc:block/ore = raw rock + overlay). Poor pieces
# carry more grey gangue (s, g), rich pieces are bigger and brighter.
# ---------------------------------------------------------------------------------------------------------------------
OSMIUM_LEG = {**shade("osmium"), **legend(s=("stone", 1), g=("stone", 2))}
ORE_PIECE = {}  # grade -> grid, shared by the osmium and draconium pieces
ORE_PIECE["small"] = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......oo.......",
    "......ohlo......",
    ".....ohmgdo.....",
    ".....olmmdo.....",
    "......oddo......",
    ".......oo.......",
    "................",
    "................",
    "................",
    "................",
]
ORE_PIECE["poor"] = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......ooo.......",
    "....oogslo......",
    "...osgmlhlo.....",
    "...ogsmmlgso....",
    "..osmmgsmmdo....",
    "..ogsddmgsdo....",
    "...oodsgddo.....",
    ".....ooooo......",
    "................",
    "................",
    "................",
]
ORE_PIECE["normal"] = [
    "................",
    "................",
    "................",
    ".....oooo.......",
    "...oohlmgoo.....",
    "..ohlmmsgmdo....",
    "..olmgsmmmmdo...",
    ".ohlmmmlhmgdo...",
    ".olsgmmmmmmddo..",
    ".olmmmdgsmmddo..",
    "..odmmmmmmdddo..",
    "..oddgsmmddoo...",
    "...oodddddo.....",
    ".....ooooo......",
    "................",
    "................",
]
ORE_PIECE["rich"] = [
    "................",
    "................",
    "....ooooo.......",
    "..oohhllmoo.....",
    ".ohllmmmmmdoo...",
    ".ohlmmhlmmmmdo..",
    "ohlmmlhmmmmmddo.",
    "olmmmmmmlhmmddo.",
    "olmmgsmmmlmmddo.",
    "olmmmmmmmmmdddo.",
    ".odmmmmmmmddddo.",
    ".oddmmmmddddoo..",
    "..ooddddddoo....",
    "....oooooo......",
    "................",
    "................",
]

for _g in ("small", "poor", "normal", "rich"):
    item(f"ore/{_g}_native_osmium", "osmium", ORE_PIECE[_g], OSMIUM_LEG)
# Draconium (the pack vein, kubejs/startup_scripts/draconium.js, 2026-10-14): the same piece shapes in draconium violet.
DRACONIUM_LEG = {**shade("draconium"), **legend(s=("stone", 1), g=("stone", 2))}
for _g in ("small", "poor", "normal", "rich"):
    item(f"ore/{_g}_draconium", "draconium", ORE_PIECE[_g], DRACONIUM_LEG)

INGOT = [  # 7 rows, light from the top-left
    "....oooooooooo..",
    "..oohhhhhhhhhlo.",
    "oohllllllllllldo",
    "olmmmmmmmmmmmddo",
    "odmmmmmmmmmdddo.",
    "oddddddddddddo..",
    ".ooooooooooooo..",
]


def paint(img, rows, leg, ox, oy):
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and 0 <= x + ox < 16 and 0 <= y + oy < img.height:
                px[x + ox, y + oy] = leg[ch]


def _double_ingot():
    img = new_tex()
    leg = shade("osmium")
    paint(img, INGOT, leg, 0, 3)   # back ingot
    paint(img, INGOT, leg, 0, 8)   # front ingot, drawn over the back one
    return img


ITEMS["metal/double_ingot/osmium"] = ("osmium", _double_ingot, None)

KJS_BLOCKS = {}  # texture path under kubejs/assets/firmages/textures/block -> (label, builder, mcmeta or None)
OSMIUM_SPECKS = {"poor": 5, "normal": 8, "rich": 12}  # ore clusters per overlay


def ore_overlay(grade, metal="osmium"):
    """Transparent 16x16: metal clusters (light top pixel, mid body) with a dark rim that sits on the rock texture."""
    rng = random.Random(f"{metal}_overlay_" + grade)
    t = tones(metal)
    img = new_tex()
    px = img.load()
    taken = set()
    for _ in range(OSMIUM_SPECKS[grade]):
        for _try in range(40):
            x, y = rng.randrange(1, 15), rng.randrange(1, 15)
            if all(abs(x - a) > 2 or abs(y - b) > 2 for a, b in taken):
                break
        cluster = [(x, y)]
        for _step in range(rng.randint(2, 4 if grade == "poor" else 5)):
            cx, cy = rng.choice(cluster)
            nx, ny = cx + rng.choice((-1, 0, 1)), cy + rng.choice((-1, 0, 1))
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in cluster:
                cluster.append((nx, ny))
        taken |= set(cluster)
        top = min(cluster, key=lambda p: (p[1], p[0]))
        for c in cluster:
            px[c] = t[2]
        px[top] = t[4] if grade == "rich" else t[3]
        if len(cluster) > 3:
            px[cluster[1]] = t[3]
        for cx, cy in cluster:  # dark rim below and right of the cluster
            for nx, ny in ((cx + 1, cy), (cx, cy + 1)):
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in cluster and px[nx, ny][3] == 0:
                    px[nx, ny] = t[0] if rng.random() < 0.6 else t[1]
    return img


for _g in ("poor", "normal", "rich"):
    KJS_BLOCKS[f"ore/{_g}_native_osmium"] = (f"{_g} overlay", (lambda g=_g: ore_overlay(g)), None)
    KJS_BLOCKS[f"ore/{_g}_draconium"] = (f"{_g} draconium overlay", (lambda g=_g: ore_overlay(g, "draconium")), None)


# ---------------------------------------------------------------------------------------------------------------------
# Signature blocks (kubejs/startup_scripts/signature_blocks.js, research n2-deco-mods section 5).
# Age Lanterns: one per Age, drawn on the UV layout of minecraft:block/template_lantern (the model the blocks use):
# cap sides x1-4 rows 0-1, body sides x0-5 rows 2-8, body top/bottom x0-5 rows 9-14 (cap top = its inner 4x4),
# handle planes x11-13 rows 1-2 and 10-11. Frame in the Age's material palette, glass in its accent; four frames of a
# slow flicker. Engraved rock bricks: one overlay per TFC rock on model tfc:block/ore (the TFC brick texture plus this
# overlay): a star diamond between two small stars, a sun and a crescent moon carved into the three bricks of the TFC brick tile, the groove in the rock's
# own dark tone and its lit lower edge in the rock's light tone.
# ---------------------------------------------------------------------------------------------------------------------
LANTERN_PALETTES = {  # lantern key -> (frame palette, glass palette), Age order
    "stone": ("terracotta", "ember"),
    "bronze": ("bronze", "gold"),
    "iron": ("steel", "heat"),
    "arcane": ("arcane", "rune"),
    "industrial": ("iron", "steam"),
    "electric": ("alu", "cyan"),
    "information": ("quartz", "data"),
    "space": ("desh", "star"),
    "quantum": ("calorite", "draconium"),
    "singularity": ("void", "violet"),
}
LANTERN = [  # o d m l h = frame tones, 1 2 3 = glass tones (3 brightest), "." transparent
    ".dmmd...........",
    ".oddo......odd..",
    "omllmo.....d.d..",
    "d1221o..........",
    "l2332d..........",
    "m2332d..........",
    "m2332d..........",
    "d1221o..........",
    "oddddo..........",
    "oooooo..........",
    "odmmdo.....odd..",
    "dmhlmo.....d.d..",
    "dmllmo..........",
    "odmmdo..........",
    "oooooo..........",
    "................",
]
LANTERN_FLICKER = (1.0, 0.9, 1.0, 0.82)  # glass brightness per frame


def lantern_frames(key):
    frame, glass = LANTERN_PALETTES[key]
    f, g = tones(frame), tones(glass)
    out = []
    for k in LANTERN_FLICKER:
        leg = {"o": f[0], "d": f[1], "m": f[2], "l": f[3], "h": f[4],
               "1": scale_rgb(g[2], k), "2": scale_rgb(g[3], k), "3": g[4] if k == 1.0 else scale_rgb(g[4], k)}
        out.append(grid_tex(LANTERN, leg))
    return out


for _k in LANTERN_PALETTES:
    KJS_BLOCKS[f"lantern/{_k}"] = (f"{_k} lantern", (lambda k=_k: strip(lantern_frames(k))), anim(8, True))

ROCK_TONES = {  # TFC rock -> (dark, mid, light): darkest, middle and lightest eighth of the pixels of the TFC 4.2.11
    # brick texture (assets/tfc/textures/block/rock/bricks/<rock>.png), sampled once; the PNG is not copied
    "andesite": ("#4d5153", "#666b6b", "#818181"),
    "basalt": ("#26292b", "#303537", "#4f5455"),
    "chalk": ("#b0b5af", "#bfbeb7", "#d8d6d2"),
    "chert": ("#6f443b", "#85544b", "#9f675d"),
    "claystone": ("#735d4a", "#916d4e", "#b8855a"),
    "conglomerate": ("#615b4a", "#847a64", "#9f9177"),
    "dacite": ("#5e6060", "#808181", "#949595"),
    "diorite": ("#6c6c6c", "#a0a0a2", "#bebebf"),
    "dolomite": ("#2f2f35", "#494953", "#595963"),
    "gabbro": ("#454543", "#686868", "#7d7d7c"),
    "gneiss": ("#615c53", "#817a6f", "#9d948a"),
    "granite": ("#65595c", "#9c8189", "#beacb2"),
    "limestone": ("#93876f", "#b3a891", "#c6bba6"),
    "marble": ("#a7a7a7", "#dadcde", "#f4f6f6"),
    "phyllite": ("#52484b", "#635e5c", "#7b7678"),
    "quartzite": ("#8d8581", "#b2a7a3", "#cfc7c2"),
    "rhyolite": ("#716965", "#8e8480", "#a29a96"),
    "schist": ("#45493a", "#626751", "#7c8070"),
    "shale": ("#3a373c", "#504e52", "#6f6a6f"),
    "slate": ("#67625b", "#8b8478", "#a9a398"),
    "tuff": ("#3f453a", "#616967", "#87857b"),
}
# TFC brick tile: one long brick in rows 0-6 (mortar row 7, column 15), two short bricks in rows 8-14 (mortar column 7,
# row 15). g = groove; its lit lower-right edge is added by engraved_overlay().
ENGRAVED = [
    "................",
    ".......g........",
    "......g.g.......",
    "..g..g.g.g...g..",
    "......g.g.......",
    ".......g........",
    "................",
    "................",
    "................",
    "..ggg......gg...",
    ".g...g....g.....",
    ".g...g....g.....",
    ".g...g....g.....",
    "..ggg......gg...",
    "................",
    "................",
]


def engraved_overlay(rock):
    dark, mid, light = (rgb(c) for c in ROCK_TONES[rock])
    groove = scale_rgb(dark, 0.72)
    img = new_tex()
    px = img.load()
    cut = {(x, y) for y, row in enumerate(ENGRAVED) for x, ch in enumerate(row) if ch == "g"}
    for x, y in cut:
        px[x, y] = groove
    for x, y in cut:  # light falls from the top left: the lower and right walls of the cut catch it
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if (nx, ny) not in cut and nx < 16 and ny < 16 and ENGRAVED[ny][nx] == "." and px[nx, ny][3] == 0:
                px[nx, ny] = scale_rgb(light, 1.08)
    return img


for _r in ROCK_TONES:
    KJS_BLOCKS[f"rock/engraved/{_r}"] = (f"engraved {_r}", (lambda r=_r: engraved_overlay(r)), None)

# Rock patterns of the TFC chisel cycle (signature_blocks.js ROCK_PATTERNS, research n7-build 3.4 option A): one overlay
# per pattern and rock on model tfc:block/ore over the TFC smooth rock texture (bevelled rim in rows/columns 0 and 15,
# so the patterns stay inside 1..14). g = groove in the rock's dark tone (lit lower-right edge added like the engraved
# bricks), d / l = a tile in the rock's dark / light tone, e = an explicit lit edge (a pattern with e gets no automatic
# edge: chevron, whose diagonals would double). Panel and column connect across blocks (Fusion, below).
ROCK_PATTERNS = {
    "chevron": [
        "................",
        "..eg...ee...ge..",
        "...eg......ge...",
        "....eg....ge....",
        ".g...eg..ge...g.",
        ".eg...egge...ge.",
        "..eg...ee...ge..",
        "...eg......ge...",
        "....eg....ge....",
        ".g...eg..ge...g.",
        ".eg...egge...ge.",
        "..eg...ee...ge..",
        "...eg......ge...",
        "....eg....ge....",
        ".g...eg..ge...g.",
        "................",
    ],
    "ashlar": [
        "................",
        ".........g......",
        ".........g......",
        ".........g......",
        ".........g......",
        ".gggggggggggggg.",
        "....g.......g...",
        "....g.......g...",
        "....g.......g...",
        "....g.......g...",
        ".gggggggggggggg.",
        ".......g........",
        ".......g........",
        ".......g........",
        ".......g........",
        "................",
    ],
    "runes": [
        "................",
        "................",
        "................",
        "................",
        ".gggggggggggggg.",
        "................",
        "..gg..g..g.g.g..",
        "..g.g..gg...g...",
        "..gg...gg...g...",
        "..g...g..g..g...",
        "................",
        ".gggggggggggggg.",
        "................",
        "................",
        "................",
        "................",
    ],
    "mosaic": [
        "................",
        ".llllg....gllll.",
        ".llllg....gllll.",
        ".llllg....gllll.",
        ".llllg....gllll.",
        ".gggggggggggggg.",
        ".....gddddg.....",
        ".....gddddg.....",
        ".....gddddg.....",
        ".....gddddg.....",
        ".gggggggggggggg.",
        ".llllg....gllll.",
        ".llllg....gllll.",
        ".llllg....gllll.",
        ".llllg....gllll.",
        "................",
    ],
}


def pattern_overlay(rock, pattern):
    rows = ROCK_PATTERNS[pattern]
    dark, mid, light = (rgb(c) for c in ROCK_TONES[rock])
    lit = scale_rgb(light, 1.08)
    fill = {"g": scale_rgb(dark, 0.72), "d": dark, "l": light, "e": lit}
    img = new_tex()
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in fill:
                px[x, y] = fill[ch]
    if not any("e" in row for row in rows):
        cut = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "g"}
        for x, y in cut:
            for nx, ny in ((x + 1, y), (x, y + 1)):
                if nx < 16 and ny < 16 and rows[ny][nx] == ".":
                    px[nx, ny] = lit
    return img


for _p in ROCK_PATTERNS:
    for _r in ROCK_TONES:
        KJS_BLOCKS[f"rock/pattern/{_p}/{_r}"] = (f"{_p} {_r}", (lambda p=_p, r=_r: pattern_overlay(r, p)), None)


# Connected rock patterns (Fusion, the library of Rechiseled; model fusion:connecting in signature_blocks.js). A
# connected face cannot sit on the TFC smooth texture (its bevelled rim would cut every joint), so these are whole
# textures: a stone surface of clustered noise in the rock's three tones (drawn, not copied), then the grooves.
# Panel, layout "pieced" (5 tiles of 16, side by side): 0 alone, 1 joined on all sides, 2 joined above and below,
# 3 joined left and right, 4 joined on all sides but not diagonally (inner corners). One frame runs around the whole
# wall of panels. Column, layout "vertical" (4 tiles stacked): 0 alone, 1 top, 2 middle, 3 bottom: a fluted shaft
# with a capital on top and a base below, however tall.
def rock_fill(rock):
    dark, mid, light = (rgb(c) for c in ROCK_TONES[rock])
    mix = lambda a, b, f: tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3)) + (255,)  # noqa: E731
    t = [mix(mid, dark, 0.6), mix(mid, dark, 0.3), mid, mix(mid, light, 0.35), mix(mid, light, 0.6)]
    rng = random.Random("rock_fill_" + rock)
    img = new_tex()
    px = img.load()
    cells = {}
    for y in range(16):
        for x in range(16):
            key = (x // 2, y // 2)
            if key not in cells:
                cells[key] = rng.choices(range(5), weights=(1, 3, 8, 3, 1))[0]
            idx = cells[key]
            if rng.random() < 0.25:
                idx = max(0, min(4, idx + rng.choice((-1, 1))))
            px[x, y] = t[idx]
    return img


def carve(img, rows, rock):
    """Cut the grooves of a 16-row grid (g) into a filled tile; lit lower-right edge as in pattern_overlay; d = dark."""
    dark, mid, light = (rgb(c) for c in ROCK_TONES[rock])
    px = img.load()
    cut = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "g"}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "g":
                px[x, y] = scale_rgb(dark, 0.72)
            elif ch == "d":
                px[x, y] = dark
    for x, y in cut:
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if nx < 16 and ny < 16 and rows[ny][nx] == ".":
                px[nx, ny] = scale_rgb(light, 1.08)
    return img


def ct_grid(fn):
    return ["".join(fn(x, y) for x in range(16)) for y in range(16)]


PANEL_TILES = [
    ct_grid(lambda x, y: "g" if (x in (1, 14) and 1 <= y <= 14) or (y in (1, 14) and 1 <= x <= 14) else "."),
    ct_grid(lambda x, y: "."),
    ct_grid(lambda x, y: "g" if x in (1, 14) else "."),
    ct_grid(lambda x, y: "g" if y in (1, 14) else "."),
    ct_grid(lambda x, y: "g" if (x in (1, 14) and y in (0, 1, 14, 15)) or (y in (1, 14) and x in (0, 1, 14, 15)) else "."),
]
FLUTES = (3, 6, 9, 12)


def column_tile(top, bottom):
    def cell(x, y):
        if x in (0, 15):
            return "d"
        if top and y in (1, 3) or bottom and y in (12, 14):
            return "g"
        if x in FLUTES and (5 if top else 0) <= y <= (10 if bottom else 15):
            return "g"
        return "."
    return ct_grid(cell)


COLUMN_TILES = [column_tile(True, True), column_tile(True, False), column_tile(False, False), column_tile(False, True)]
CONNECTED_PATTERNS = {"panel": ("pieced", PANEL_TILES), "column": ("vertical", COLUMN_TILES)}


def connected_texture(rock, pattern):
    layout, tiles = CONNECTED_PATTERNS[pattern]
    wide = layout == "pieced"
    out = Image.new("RGBA", (16 * len(tiles), 16) if wide else (16, 16 * len(tiles)), (0, 0, 0, 0))
    for i, rows in enumerate(tiles):
        out.paste(carve(rock_fill(rock), rows, rock), (16 * i, 0) if wide else (0, 16 * i))
    return out


for _p, (_layout, _tiles) in CONNECTED_PATTERNS.items():
    for _r in ROCK_TONES:
        KJS_BLOCKS[f"rock/pattern/{_p}/{_r}"] = (f"{_p} {_r} (connected)", (lambda p=_p, r=_r: connected_texture(r, p)),
                                                  {"fusion": {"type": "connecting", "layout": _layout}})


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


# The island plinth of the Origin Archipelago (SPEC 31.8): the altar's void stone with a sprout on top (an island
# that grows) and nine notches on the sides (the nine islands).
ARK_SPROUT = [
    "................",
    "................",
    "................",
    ".......l........",
    "......lLl..ll...",
    "......lLLllLl...",
    ".....lLl.LLl....",
    "..lll.lL.ll.....",
    ".lLLLl.L........",
    "..llLLlL........",
    "....lllL........",
    ".......L........",
    "......sss.......",
    ".....s...s......",
    "................",
    "................",
]


@block("ark_plinth_side", "island plinth side")
def _():
    img = new_tex()
    bricks_fill(img, "void", seeded("ark_plinth_side"), bw=8, bh=4, weights=(0, 3, 6, 3, 1))
    bevel(img, "void")
    d = ImageDraw.Draw(img)
    for k in range(3):
        d.point((4 + 4 * k, 3), fill=rgb(PAL["violet"][3]))
        d.point((4 + 4 * k, 12), fill=rgb(PAL["violet"][3]))
    d.point((2, 7), fill=rgb(PAL["violet"][3]))
    d.point((13, 7), fill=rgb(PAL["violet"][3]))
    d.point((7, 7), fill=rgb(PAL["violet"][4]))
    return img


@block("ark_plinth_top", "island plinth top")
def _():
    img = new_tex()
    noise_fill(img, "void", seeded("ark_plinth_top"), weights=(1, 4, 6, 2, 0), cluster=2)
    bevel(img, "void")
    overlay(img, ARK_SPROUT, legend(l=("patina", 3), L=("patina", 1), s=("violet", 3)))
    return img


@block("ark_plinth_bottom", "island plinth bottom")
def _():
    img = new_tex()
    noise_fill(img, "void", seeded("ark_plinth_bottom"), weights=(1, 4, 6, 2, 0), cluster=2)
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
# Consecrated family (firmages:consecrated_<role>, dev/archive/textures-notes.md). One material: pale sky-marble with faint
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

TALLY_SIDE = [
    "oooooooooooooooo",
    "bBBBBBBBBBBBBBBb",
    "bbbbbbbbbbbbbbbb",
    "................",
    "................",
    "...t.t.t.t......",
    "...t.t.t.tx.....",
    "...t.t.txt......",
    "...t.txt.t......",
    "...txt.t.t......",
    "..xt.t.t.t......",
    "...t.t.t.t......",
    "................",
    "bBBBBBBBBBBBBBBb",
    "bbbbbbbbbbbbbbbb",
    "oooooooooooooooo",
]


@block("tally_chute_side", "tally chute side")
def _():
    img = new_tex()
    rng = seeded("tally_side")
    noise_fill(img, "lumber", rng, weights=(0, 2, 6, 3, 0), cluster=1, grain=0.15)
    px = img.load()
    for x in (3, 7, 11):  # plank seams
        for y in range(16):
            px[x, y] = tones("lumber")[1]
    overlay(img, TALLY_SIDE, legend(o=("bronze", 0), b=("bronze", 2), B=("bronze", 3), t=("lumber", 0), x=("lumber", 0)))
    return img


TALLY_OPENING = [
    "oooooooooooooooo",
    "obbbbbbbbbbbbbbo",
    "obBBBBBBBBBBBBbo",
    "obB..........Bbo",
    "obB.g..g..g..Bbo",
    "obB..........Bbo",
    "obB.g..g..g..Bbo",
    "obB..........Bbo",
    "obB..........Bbo",
    "obB.g..g..g..Bbo",
    "obB..........Bbo",
    "obB.g..g..g..Bbo",
    "obB..........Bbo",
    "obBBBBBBBBBBBBbo",
    "obbbbbbbbbbbbbbo",
    "oooooooooooooooo",
]


@block("tally_chute_top", "tally chute top")
def _():
    img = new_tex()
    noise_fill(img, "coal", seeded("tally_top"), weights=(3, 5, 2, 0, 0), cluster=2)
    overlay(img, TALLY_OPENING, legend(o=("bronze", 0), b=("bronze", 2), B=("bronze", 3), g=("bronze", 1)))
    return img


@block("tally_chute_bottom", "tally chute bottom")
def _():
    img = new_tex()
    noise_fill(img, "lumber", seeded("tally_bottom"), weights=(0, 3, 6, 2, 0), cluster=2)
    bevel(img, "bronze", light=3, dark=1)
    px = img.load()
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = tones("coal")[1]
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Starfall (SPEC 25, firmages-core M10): the crater shell, the meteorites, the core and the two sky metals. Sky rock is
# dark fused stone with glassy streaks; meteoric iron is a dark brown-grey iron with the crossed etch lines of a real
# iron meteorite (Widmanstaetten pattern); starsteel is pale blue steel with star sparkles. The core is drawn cold (a
# cracked lump of its metal) and gets an emissive overlay while it glows (starfall_core_hot, model neoforge_data
# block_light 15). Mod items (registered in firmages-core, not KubeJS) go to the mod's textures/item through MOD_ITEMS.
# ---------------------------------------------------------------------------------------------------------------------
PAL.update({
    "skyrock": ["#0b0a10", "#1b1922", "#2a2733", "#3b3846", "#555066"],
    "meteoric": ["#1c1714", "#3d342d", "#5e534a", "#857a6e", "#b6ab9a"],
    "starsteel": ["#1d2a3a", "#3f5a78", "#7092b8", "#a9c8e8", "#e6f4ff"],
})
MOD_ITEMS = {}  # mod item texture path -> (label, builder() -> RGBA strip, mcmeta or None); written to MOD_TEX/item


def mod_item(path, label, rows, leg, post=None):
    def build():
        img = grid_tex(rows, leg)
        if post:
            post(img)
        return img
    MOD_ITEMS[path] = (label, build, None)


MOD_GUI = {}  # mod gui texture name -> (label, builder() -> RGBA, mcmeta or None); written to MOD_TEX/gui


def mod_gui(name, label, rows, leg):
    MOD_GUI[name] = (label, lambda: grid_tex(rows, leg), None)


# Jade's "unknown" sigil (SPEC 21: a block before its Age shows this instead of its name): a sky rune, a lozenge with a
# spark in it on a short staff, gold with a one-pixel night outline so it reads on the light and the dark tooltip.
UNKNOWN_SIGIL = [
    ".......o........",
    "......ogo.......",
    ".....ogggo......",
    "....oggoggo.....",
    "...oggoooggo....",
    "..oggoowooggo...",
    ".oggoowgwooggo..",
    "..oggoowooggo...",
    "...oggoooggo....",
    "....oggoggo.....",
    ".....ogggo......",
    "......ogo.......",
    ".....oogoo......",
    "....ogggggo.....",
    ".....ooooo......",
    "................",
]
mod_gui("unknown_sigil", "Jade: unknown block", UNKNOWN_SIGIL, legend(g="#e8c56a", w="#fff0a8", o="#14102c"))


def etch(img, pal, rng, n=5, mask_alpha=True):
    """Widmanstaetten etch: short crossing diagonal lines in the metal's light tone over the opaque pixels."""
    t = tones(pal)
    px = img.load()
    for _ in range(n):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        dx = rng.choice((1, -1))
        for i in range(rng.randint(2, 4)):
            nx, ny = x + dx * i, y + i
            if 0 <= nx < 16 and 0 <= ny < 16 and (not mask_alpha or px[nx, ny][3] == 255) and px[nx, ny] != t[0]:
                px[nx, ny] = t[3] if i % 2 == 0 else t[4]


def sparkle(img, rng, n=3, color="#ffffff"):
    """Star points: a white pixel with four faint neighbours, only on opaque pixels."""
    px = img.load()
    c = rgb(color)
    for _ in range(n):
        for _try in range(30):
            x, y = rng.randrange(2, 14), rng.randrange(2, 14)
            if px[x, y][3] == 255 and all(px[x + a, y + b][3] == 255 for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                break
        else:
            continue
        px[x, y] = c
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            o = px[x + a, y + b]
            px[x + a, y + b] = tuple((o[i] + c[i]) // 2 for i in range(3)) + (255,)


SHEET_SHAPE = [
    "................",
    "................",
    "................",
    "......oooooooo..",
    "....oohhhhhhhlo.",
    "..oohlllllllldo.",
    ".ohllmmmmmmmmdo.",
    ".olmmmmmmmmmddo.",
    ".olmmmmmmmmmddo.",
    ".olmmmmmmmmddo..",
    ".odmmmmmmmddo...",
    ".odddddddddo....",
    "..oooooooooo....",
    "................",
    "................",
    "................",
]
ROD_SHAPE = [
    "................",
    "................",
    "............ooo.",
    "...........ohlo.",
    "..........ohmdo.",
    ".........ohmdo..",
    "........ohmdo...",
    ".......ohmdo....",
    "......ohmdo.....",
    ".....ohmdo......",
    "....ohmdo.......",
    "...ohmdo........",
    "..olmdo.........",
    "..oddo..........",
    "..ooo...........",
    "................",
]
INGOT_ONE = ["................"] * 5 + [r.ljust(16, ".") for r in INGOT] + ["................"] * 4


def _metal_items():
    mleg = shade("meteoric")
    sleg = shade("starsteel")
    mod_item("meteoric_iron_chunk", "meteoric chunk", ORE_PIECE["normal"], {**mleg, **legend(s=("meteoric", 1), g=("meteoric", 3))},
             lambda img: etch(img, "meteoric", seeded("met_chunk"), 3))
    mod_item("meteoric_iron_ingot", "meteoric ingot", INGOT_ONE, mleg, lambda img: etch(img, "meteoric", seeded("met_ingot"), 3))
    MOD_ITEMS["meteoric_iron_double_ingot"] = ("meteoric double ingot", lambda: _double(mleg, "met_double"), None)
    mod_item("meteoric_iron_sheet", "meteoric sheet", SHEET_SHAPE, mleg, lambda img: etch(img, "meteoric", seeded("met_sheet"), 4))
    mod_item("meteoric_iron_rod", "meteoric rod", ROD_SHAPE, mleg)
    mod_item("starsteel_chunk", "starsteel chunk", ORE_PIECE["rich"], {**sleg, **legend(s=("skyrock", 2), g=("skyrock", 3))},
             lambda img: sparkle(img, seeded("ss_chunk"), 2))
    mod_item("starsteel_ingot", "starsteel ingot", INGOT_ONE, sleg, lambda img: sparkle(img, seeded("ss_ingot"), 1))


def _double(leg, seed):
    img = new_tex()
    paint(img, INGOT, leg, 0, 3)
    paint(img, INGOT, leg, 0, 8)
    etch(img, "meteoric", seeded(seed), 3)
    return img


_metal_items()


@block("sky_rock", "sky rock")
def _():
    img = new_tex()
    noise_fill(img, "skyrock", seeded("sky_rock"), weights=(1, 4, 6, 3, 1), cluster=2, grain=0.3)
    rng = seeded("sky_rock_glass")
    px = img.load()
    t = tones("skyrock")
    for _ in range(4):  # glassy fused streaks
        x, y = rng.randrange(0, 16), rng.randrange(0, 16)
        for i in range(rng.randint(2, 4)):
            px[(x + i) % 16, y] = t[4] if i == 0 else t[3]
    return img


def meteorite_tile(seed, base, metal, sparkles=0):
    img = new_tex()
    noise_fill(img, base, seeded(seed), weights=(1, 3, 6, 3, 1), cluster=2)
    rng = seeded(seed + "_metal")
    t = tones(metal)
    px = img.load()
    for _ in range(7):  # metal grains in the fused stone
        x, y = rng.randrange(0, 16), rng.randrange(0, 16)
        cl = [(x, y)]
        for _s in range(rng.randint(2, 5)):
            cx, cy = rng.choice(cl)
            nx, ny = (cx + rng.choice((-1, 0, 1))) % 16, (cy + rng.choice((-1, 0, 1))) % 16
            if (nx, ny) not in cl:
                cl.append((nx, ny))
        for c in cl:
            px[c] = t[2]
        px[min(cl, key=lambda p: (p[1], p[0]))] = t[3]
    for _ in range(5):  # thumbprint pits of the fall (regmaglypts)
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        px[x, y] = tones(base)[0]
        px[x + 1, y] = tones(base)[1]
    etch(img, metal, rng, 4, mask_alpha=False)
    if sparkles:
        sparkle(img, rng, sparkles, "#e6f4ff")
    return img


@block("meteorite", "meteorite")
def _():
    return meteorite_tile("meteorite", "meteoric", "meteoric")


@block("starsteel_meteorite", "starsteel meteorite")
def _():
    return meteorite_tile("starsteel_meteorite", "skyrock", "starsteel", sparkles=3)


CORE_CRACKS = [
    "................",
    "..c.......c.....",
    "...c.....c......",
    "....c...c...c...",
    ".....c.c...c....",
    "......c...c.....",
    "...cc.c..c......",
    "..c...cccc......",
    ".c......cc..cc..",
    ".......c..cc..c.",
    "......c....c....",
    ".....c......c...",
    "....c........c..",
    "...c..........c.",
    "................",
    "................",
]


def core_tile(metal, seed):
    img = meteorite_tile(seed, "skyrock" if metal == "starsteel" else "meteoric", metal, sparkles=2 if metal == "starsteel" else 0)
    overlay(img, CORE_CRACKS, legend(c=("coal", 0)))
    bevel(img, metal, light=3, dark=0, corner=1)
    return img


@block("starfall_core_meteoric_iron", "core (meteoric iron, cold)")
def _():
    return core_tile("meteoric", "core_met")


@block("starfall_core_starsteel", "core (starsteel, cold)")
def _():
    return core_tile("starsteel", "core_ss")


@block("starfall_core_hot", "core glow overlay", anim(5, True))
def _():
    """The cracks glow and breathe: four frames from ember orange to white-hot."""
    frames = []
    for f, (a, b) in enumerate([(2, 3), (3, 4), (3, 4), (2, 3)]):
        fr = new_tex()
        overlay(fr, CORE_CRACKS, legend(c=("ember", a)))
        px = fr.load()
        rng = seeded(f"core_hot_{f}")
        cracks = [(x, y) for y in range(16) for x in range(16) if px[x, y][3]]
        for x, y in rng.sample(cracks, 6):
            px[x, y] = tones("ember")[b]
        frames.append(fr)
    return strip(frames)


@block("meteoric_iron_block", "meteoric iron block")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("met_block"), weights=(0, 2, 6, 4, 1), cluster=4, grain=0.15)
    etch(img, "meteoric", seeded("met_block_etch"), 9, mask_alpha=False)
    bevel(img, "meteoric")
    return img


@block("starsteel_block", "starsteel block")
def _():
    img = new_tex()
    noise_fill(img, "starsteel", seeded("ss_block"), weights=(0, 1, 6, 4, 1), cluster=4, grain=0.15)
    bevel(img, "starsteel")
    sparkle(img, seeded("ss_block_spark"), 3)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Drifting Isles (SPEC 30.4): skystone, the firmament crystal cluster and lode, the crystal item
# ---------------------------------------------------------------------------------------------------------------------
PAL.update({
    "skystone": ["#7d8798", "#a3adbd", "#c3cbd8", "#dce2ea", "#f2f5f9"],
    "firmament": ["#1c2f66", "#3460b8", "#58a0e6", "#98d6ff", "#eafcff"],
})


@block("skystone", "skystone")
def _():
    img = new_tex()
    noise_fill(img, "skystone", seeded("skystone"), weights=(0, 2, 6, 4, 2), cluster=3, grain=0.2)
    rng = seeded("skystone_veins")
    px = img.load()
    f = tones("firmament")
    for _ in range(2):  # faint blue veins: the stone remembers the crystal
        x, y = rng.randrange(0, 16), rng.randrange(0, 16)
        for i in range(rng.randint(3, 5)):
            c = px[(x + i) % 16, (y + i // 2) % 16]
            px[(x + i) % 16, (y + i // 2) % 16] = tuple((c[k] * 2 + f[3][k]) // 3 for k in range(3)) + (255,)
    return img


# Isle ruin (G3, Felix I1B): the four Wind Seals and the sealed lid over the crystal chamber.
WIND_GLYPH = [
    "................",
    "................",
    "....hhhhhh......",
    "...h......h.....",
    "..........h.....",
    ".hhhhhhhhh......",
    "................",
    "...hhhhhhhhhhh..",
    "..............h.",
    "..............h.",
    "...hhhhhhhhhhh..",
    "................",
    ".hhhhhhh........",
    "........h.......",
    ".......h........",
    "................",
]
SEAL_RING = [
    "................",
    "................",
    "......hhhh......",
    "....hh....hh....",
    "...h........h...",
    "...h........h...",
    "..h..........h..",
    "..h..........h..",
    "..h..........h..",
    "..h..........h..",
    "...h........h...",
    "...h........h...",
    "....hh....hh....",
    "......hhhh......",
    "................",
    "................",
]


def _seal_base(name):
    img = new_tex()
    noise_fill(img, "skystone", seeded(name), weights=(1, 3, 6, 3, 1), cluster=2, grain=0.2)
    bevel(img, "skystone")
    return img


@block("wind_seal_side", "wind seal side")
def _():
    return _seal_base("wind_seal_side")


@block("wind_seal_front", "wind seal front (wind glyph)")
def _():
    img = _seal_base("wind_seal_front")
    t = tones("skystone")
    overlay(img, WIND_GLYPH, legend(h=t[0]))
    return img


@block("wind_seal_top", "wind seal top")
def _():
    img = _seal_base("wind_seal_top")
    overlay(img, SEAL_RING, legend(h=tones("skystone")[0]))
    return img


@block("wind_seal_top_first", "wind seal top, the first seal (worn by the wind)")
def _():
    img = _seal_base("wind_seal_top_first")
    t = tones("skystone")
    overlay(img, SEAL_RING, legend(h=t[0]))
    overlay(img, WIND_GLYPH[4:12] + ["." * 16] * 8, legend(h=t[1]), oy=4)
    return img


@block("wind_seal_top_lit", "wind seal top, awake")
def _():
    img = _seal_base("wind_seal_top_lit")
    overlay(img, SEAL_RING, legend(h=tones("firmament")[3]))
    return img


@block("wind_seal_lid", "sealed skystone over the crystal chamber")
def _():
    img = new_tex()
    noise_fill(img, "skystone", seeded("wind_seal_lid"), weights=(2, 4, 6, 2, 0), cluster=2, grain=0.2)
    t = tones("firmament")
    overlay(img, ["h..............h"] + ["." * 16] * 14 + ["h..............h"], legend(h=t[1]))
    overlay(img, SEAL_RING, legend(h=tones("skystone")[0]))
    return img


CRYSTAL_CLUSTER = [
    "................",
    "................",
    ".......h........",
    "......hl........",
    "......lm...h....",
    "..h...lm..hl....",
    "..lh..lmd.lm....",
    "..lm..lmd.lmd...",
    "..lmd.lmd.lmd.h.",
    "...md.lmd.lmdhl.",
    "...md.lmd.lmdlm.",
    "...mdlmmd.lmdmd.",
    "...mdlmmdlmmdmd.",
    "...mmlmmdlmmmd..",
    "..oooooooooooo..",
    "................",
]

LODE_FACETS = [
    "................",
    "..h.........h...",
    "..lm.......lm...",
    "...md.....lmd...",
    "...ldd...lmd....",
    "....md..lmd.....",
    ".........d......",
    "......hl........",
    ".....hlmd.......",
    "....hlmmd.......",
    "....lmmdd...hl..",
    ".....mdd...hlmd.",
    "......d....lmmd.",
    "............md..",
    ".h..............",
    ".ld.............",
]


@block("firmament_crystal_cluster", "firmament crystal cluster (cross)")
def _():
    return grid_tex(CRYSTAL_CLUSTER, {**shade("firmament"), "o": tones("firmament")[1]})


@block("firmament_lode", "firmament lode")
def _():
    img = new_tex()
    noise_fill(img, "skystone", seeded("firmament_lode"), weights=(1, 4, 6, 3, 0), cluster=2, grain=0.25)
    overlay(img, LODE_FACETS, shade("firmament"))
    return img


@block("firmament_lode_glow", "firmament lode glow overlay")
def _():
    img = new_tex()
    t = tones("firmament")
    overlay(img, LODE_FACETS, legend(h=t[4], l=t[3], m=t[3], d=t[2]))
    return img


def _crystal_item():
    mod_item("firmament_crystal", "firmament crystal", [
        "................",
        ".........oo.....",
        "........ohho....",
        ".......ohllo....",
        "......ohllmo....",
        ".....ohllmdo....",
        "....ohllmmdo....",
        "...ohllmmdo.....",
        "...olllmmdo.....",
        "...olmmmddo.....",
        "....omdddo......",
        "....oddoo.......",
        ".....oo.........",
        "................",
        "................",
        "................",
    ], shade("firmament"), lambda img: sparkle(img, seeded("firmament_crystal"), 1))


_crystal_item()


# ---------------------------------------------------------------------------------------------------------------------
# The Lens Crown (SPEC 27): Star Anvil, Lens Mount, lens glass; mod items (lens blank, nine lenses, seven alloys)
# ---------------------------------------------------------------------------------------------------------------------
def crown_item(path, label, mcmeta=None):
    def deco(fn):
        MOD_ITEMS[path] = (label, fn, mcmeta)
        return fn
    return deco


def disc(img, r0, r1, colour_at):
    """Paint the pixels whose centre lies in r0 <= d < r1 around (7.5, 7.5); colour_at(x, y, d) -> RGBA or None."""
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if r0 <= d < r1:
                c = colour_at(x, y, d)
                if c is not None:
                    px[x, y] = c
    return img


def lit(x, y):
    """Light from the top-left: -1 (shadow) .. 1 (highlight)."""
    return max(-1.0, min(1.0, ((7.5 - x) + (7.5 - y)) / 10.5))


@block("star_anvil_side", "star anvil side")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("anvil_side"), weights=(0, 3, 6, 2, 0), cluster=2)
    overlay(img, [
        "hhhhhhhhhhhhhhhh",
        "llllllllllllllll",
        "................",
        "................",
        "................",
        "dddddddddddddddd",
        "oooooooooooooooo",
        "....o......o....",
        "....o......o....",
        "....o......o....",
        "oooooooooooooooo",
        "llllllllllllllll",
        "................",
        "................",
        "dddddddddddddddd",
        "oooooooooooooooo",
    ], shade("meteoric"))
    return img


@block("star_anvil_base", "star anvil base")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("anvil_base"), weights=(1, 4, 6, 2, 0), cluster=2)
    bevel(img, "meteoric", light=3, dark=0)
    return img


STAR = [
    "................",
    "................",
    "................",
    "................",
    ".......s........",
    ".......s........",
    "......sSs.......",
    "....ssSSSss.....",
    "......sSs.......",
    ".......s........",
    ".......s........",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def anvil_top(seed, star):
    img = new_tex()
    noise_fill(img, "meteoric", seeded(seed), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, "meteoric", light=4, dark=0)
    if star:
        overlay(img, STAR, star)
    return img


@block("star_anvil_top", "star anvil top")
def _():
    return anvil_top("anvil_top", legend(s=("meteoric", 1), S=("meteoric", 0)))


@block("star_anvil_top_formed", "star anvil top, crown formed")
def _():
    return anvil_top("anvil_top", legend(s=("stormglass", 2), S=("stormglass", 3)))


@block("star_anvil_top_active", "star anvil top, forging", anim(3, True))
def _():
    frames = []
    for f in range(6):
        k = 0.5 + 0.5 * math.sin(f / 6 * 2 * math.pi)
        s = scale_rgb(tones("stormglass")[3], 1 + 0.4 * k)
        S = scale_rgb(tones("stormglass")[4], 1 + 0.6 * k)
        frames.append(anvil_top("anvil_top", {"s": s, "S": S}))
    return strip(frames)


@block("lens_mount_frame", "lens mount frame")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("mount_frame"), weights=(0, 3, 6, 3, 0), cluster=1, grain=0.15)
    bevel(img, "meteoric", light=4, dark=0)
    return img


def mount_top(glow):
    img = new_tex()
    noise_fill(img, "meteoric", seeded("mount_top"), weights=(0, 3, 6, 2, 0), cluster=2)
    bevel(img, "meteoric", light=3, dark=0)
    groove = tones("stormglass")[4] if glow else tones("meteoric")[0]
    px = img.load()
    for i in range(3, 13):  # the slot the lens swings over
        px[i, 7] = groove
        px[i, 8] = groove if glow else tones("meteoric")[1]
    return img


@block("lens_mount_top", "lens mount top")
def _():
    return mount_top(False)


@block("lens_mount_top_in", "lens mount top, lens in")
def _():
    return mount_top(True)


def glass_tone(x, y, d, edge):
    """Greyscale lens glass (tinted in game): bright top-left, darker soft edge, a three-pixel glint."""
    v = 205 + int(40 * lit(x, y))
    if d >= edge - 1:
        v -= 35
    if (x, y) in ((5, 4), (4, 5), (5, 5)):
        v = 255
    v = max(0, min(255, v))
    return (v, v, v, 225)


@block("lens_glass", "lens glass (greyscale, tinted)")
def _():
    return disc(new_tex(), 0, 8, lambda x, y, d: glass_tone(x, y, d, 8))


LENS_RIM = {"stone": "stone", "bronze": "bronze", "iron": "steel", "arcane": "arcane", "industrial": "iron",
            "electric": "alu", "information": "quartz", "space": "desh", "quantum": "draconium"}


def rim(pal):
    t = tones(pal)

    def colour(x, y, d):
        if d >= 6.6:
            return t[0]
        k = lit(x, y)
        return t[4] if k > 0.5 else t[3] if k > 0 else t[2] if k > -0.5 else t[1]
    return disc(new_tex(), 4.6, 7.3, colour)


for _name, _pal in LENS_RIM.items():
    MOD_ITEMS[f"lens/{_name}"] = (f"{_name} lens rim", (lambda p=_pal: rim(p)), None)


@crown_item("lens_glass", "lens glass (greyscale, tinted)")
def _():
    return disc(new_tex(), 0, 4.6, lambda x, y, d: glass_tone(x, y, d, 4.6))


@crown_item("lens_blank", "lens blank")
def _():
    t = tones("stormglass")

    def colour(x, y, d):
        if d >= 6.6:
            return t[0]
        k = lit(x, y)
        c = t[4] if k > 0.6 else t[3] if k > 0.1 else t[2] if k > -0.5 else t[1]
        return c[:3] + (235,)
    return disc(new_tex(), 0, 7.3, colour)


def alloy_ingot(pal, spark):
    img = new_tex()
    paint(img, INGOT, shade(pal), 0, 5)
    px = img.load()
    for x, y in spark:  # a glint of the sky in the metal
        px[x, y] = (255, 255, 255, 255)
    return img


ALLOY_SPARK = [(5, 7), (6, 6), (10, 8)]
for _alloy in ("starlit_iron", "cloudsteel", "aurora_steel", "halo_steel", "void_steel", "quasar_alloy", "firmament_alloy"):
    MOD_ITEMS[f"{_alloy}_ingot"] = (_alloy.replace("_", " ") + " ingot", (lambda p=_alloy: alloy_ingot(p, ALLOY_SPARK)), None)


# ---------------------------------------------------------------------------------------------------------------------
# Wayshrines (SPEC-himmel 28): a weathered core stone with a carved star; its top shows the phase (ruin grey, restored
# pale marble, lit glowing). The Wayshrine Stone carries the same star in the colour of its kind.
WAYSHRINE_STAR = [
    "................",
    "................",
    ".......ss.......",
    ".......ss.......",
    "......shhs......",
    "..s...shhs...s..",
    "...ssshhhhsss...",
    "....shhhhhhs....",
    "....shhhhhhs....",
    "...ssshhhhsss...",
    "..s...shhs...s..",
    "......shhs......",
    ".......ss.......",
    ".......ss.......",
    "................",
    "................",
]
WAYSHRINE_PHASES = {"ruin": ("stone", "stone"), "restored": ("marble", "vein"), "lit": ("marble", "lampglow")}
WAYSHRINE_KINDS = {"forest_ring": "patina", "steppe_stones": "clay", "swamp_altar": "pcb", "desert_arch": "gold",
                   "mountain_spire": "steel", "wind_shrine": "star", "reef_altar": "cyan", "volcanic_ring": "heat"}


@block("wayshrine_core_side", "wayshrine core side")
def _():
    img = new_tex()
    bricks_fill(img, "stone", seeded("wayshrine_side"), bw=8, bh=4, weights=(0, 2, 6, 3, 1))
    bevel(img, "stone")
    return img


@block("wayshrine_core_bottom", "wayshrine core bottom")
def _():
    img = new_tex()
    noise_fill(img, "stone", seeded("wayshrine_bottom"), weights=(1, 4, 6, 2, 0), cluster=2)
    return img


def _wayshrine_top(phase):
    base, star = WAYSHRINE_PHASES[phase]
    img = new_tex()
    noise_fill(img, base, seeded("wayshrine_top_" + phase), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, base)
    overlay(img, WAYSHRINE_STAR, legend(s=(star, 1), h=(star, 3 if phase != "ruin" else 2)))
    return img


for _ph in WAYSHRINE_PHASES:
    block("wayshrine_core_top_" + _ph, "wayshrine core top, " + _ph)(lambda ph=_ph: _wayshrine_top(ph))


def _wayshrine_stone(kind):
    img = new_tex()
    noise_fill(img, "marble", seeded("wayshrine_stone_" + kind), weights=(0, 1, 5, 4, 2), cluster=2)
    bevel(img, "marble")
    overlay(img, WAYSHRINE_STAR, legend(s=(WAYSHRINE_KINDS[kind], 1), h=(WAYSHRINE_KINDS[kind], 3)))
    return img


for _k in WAYSHRINE_KINDS:
    block("wayshrine_stone_" + _k, "wayshrine stone, " + _k)(lambda k=_k: _wayshrine_stone(k))


# ---------------------------------------------------------------------------------------------------------------------
# Sky Anchor and blessing shards (SPEC-himmel 29): a desh plinth around a void-dark core; its top carries the Wayshrine
# star (the anchor is built around a Wayshrine Stone), starlit while it carries a blessing. The shard is greyscale and
# tinted in game with its tier's beam colour (FirmagesClient), like the lens glass.
@block("sky_anchor_base", "sky anchor base")
def _():
    img = new_tex()
    noise_fill(img, "desh", seeded("anchor_base"), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, "desh")
    return img


@block("sky_anchor_side", "sky anchor core side")
def _():
    img = new_tex()
    noise_fill(img, "navy", seeded("anchor_side"), weights=(1, 3, 6, 2, 0), cluster=2)
    overlay(img, [
        "................",
        "................",
        ".......h........",
        ".......s........",
        "................",
        "...h........s...",
        "................",
        "..........h.....",
        "....s...........",
        "................",
        ".......h........",
        "................",
        "..s.........h...",
        "................",
        "......s.........",
        "................",
    ], legend(s=("star", 2), h=("star", 4)))
    bevel(img, "navy")
    return img


def _anchor_top(active):
    img = new_tex()
    noise_fill(img, "desh", seeded("anchor_top"), weights=(0, 2, 6, 3, 1), cluster=2)
    bevel(img, "desh")
    overlay(img, WAYSHRINE_STAR, legend(s=("star" if active else "navy", 2 if active else 3), h=("star", 4 if active else 1)))
    return img


block("sky_anchor_top", "sky anchor top")(lambda: _anchor_top(False))
block("sky_anchor_top_active", "sky anchor top, active")(lambda: _anchor_top(True))

SHARD = [
    "................",
    "................",
    ".........oo.....",
    "........ohho....",
    ".......ohhmo....",
    "......ohhmmo....",
    ".....ohhmmdo....",
    "....ohhmmmdo....",
    "....ohmmmdo.....",
    "...ohmmmddo.....",
    "...ommmddo......",
    "...ommddo.......",
    "....oddo........",
    ".....oo.........",
    "................",
    "................",
]


@crown_item("blessing_shard", "blessing shard (greyscale, tinted)")
def _():
    img = new_tex()
    tone = {"o": (70, 70, 70, 255), "d": (150, 150, 150, 255), "m": (205, 205, 205, 255), "h": (250, 250, 250, 255)}
    px = img.load()
    for y, row in enumerate(SHARD):
        for x, ch in enumerate(row):
            if ch in tone:
                px[x, y] = tone[ch]
    return img


# ---------------------------------------------------------------------------------------------------------------------
# The Launch Complex (SPEC 31): launch control (front with three throttle lamps), launch port, probe cradle; the probe
# (its custom item model is also what the launch renderer draws at scale 3), the two payload modules, voidglass and
# the deep-space sample.
RIVETS = [
    "................",
    ".r............r.",
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
    "................",
    "................",
    ".r............r.",
    "................",
]


def steel_plate(seed, rivets=True):
    img = new_tex()
    noise_fill(img, "steel", seeded(seed), weights=(0, 2, 6, 3, 0), cluster=2, grain=0.15)
    bevel(img, "steel", light=4, dark=0)
    if rivets:
        overlay(img, RIVETS, legend(r=("steel", 4)))
    return img


@block("launch_control_side", "launch control side")
def _():
    img = steel_plate("lc_side")
    overlay(img, [
        "................",
        "................",
        "................",
        "....oooooooo....",
        "....oddddddo....",
        "....oddddddo....",
        "....oooooooo....",
        "................",
        "................",
        "................",
        "...yy..yy..yy...",
        "..yy..yy..yy..y.",
        ".yy..yy..yy..yy.",
        "................",
        "................",
        "................",
    ], legend(o=("steel", 0), d=("steel", 1), y=("ember", 3)))
    return img


@block("launch_control_top", "launch control top")
def _():
    return steel_plate("lc_top")


@block("launch_control_bottom", "launch control bottom")
def _():
    return steel_plate("lc_bottom", rivets=False)


def control_front(step):
    img = steel_plate("lc_front")
    lamps = {"1": ("ember", 3), "2": ("ember", 3) if step >= 2 else ("coal", 2), "3": ("heat", 4) if step >= 3 else ("coal", 2)}
    overlay(img, [
        "................",
        "................",
        "..oooooooooooo..",
        "..occcccccccco..",
        "..occcccccccco..",
        "..occcccccccco..",
        "..occcccccccco..",
        "..oooooooooooo..",
        "................",
        "...ooo.ooo.ooo..",
        "...o1o.o2o.o3o..",
        "...ooo.ooo.ooo..",
        "................",
        "..ssssssssssss..",
        "................",
        "................",
    ], legend(o=("steel", 0), c=("cyan", 1), s=("steel", 1), **lamps))
    px = img.load()
    for x in range(4, 4 + 2 * step + 2):  # the screen shows the throttle as a bar
        px[x, 5] = tones("cyan")[3]
        px[x, 4] = tones("cyan")[4]
    return img


for _st in (1, 2, 3):
    block(f"launch_control_front_{_st}", f"launch control front, throttle {_st}")(lambda st=_st: control_front(st))


@block("launch_port_side", "launch port side")
def _():
    img = steel_plate("lp_side")
    overlay(img, [
        "................",
        "................",
        "................",
        "................",
        ".....oooooo.....",
        "....oddddddo....",
        "....odlllldo....",
        "....odlaaldo....",
        "....odlaaldo....",
        "....odlllldo....",
        "....oddddddo....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
        "................",
    ], legend(o=("steel", 0), d=("iron", 1), l=("iron", 2), a=("ember", 3)))
    return img


@block("launch_port_top", "launch port top")
def _():
    img = steel_plate("lp_top")
    t = tones("iron")
    return disc(img, 3, 6, lambda x, y, d: t[0] if d >= 5 else t[2] if lit(x, y) > 0 else t[1])


def hazard(img, y0, y1):
    px = img.load()
    for y in range(y0, y1):
        for x in range(16):
            px[x, y] = tones("halo_steel")[3] if ((x + y) // 2) % 2 == 0 else tones("coal")[1]


@block("probe_cradle_side", "probe cradle side")
def _():
    img = steel_plate("pc_side", rivets=False)
    hazard(img, 11, 15)
    return img


@block("probe_cradle_top", "probe cradle top")
def _():
    img = steel_plate("pc_top")
    t = tones("heat")
    return disc(img, 4.5, 6, lambda x, y, d: t[1])


@block("probe_cradle_bottom", "probe cradle bottom")
def _():
    return steel_plate("pc_bottom", rivets=False)


@block("probe_cradle_clamp", "probe cradle clamp")
def _():
    img = new_tex()
    noise_fill(img, "iron", seeded("pc_clamp"), weights=(0, 3, 6, 2, 0), cluster=1, grain=0.1)
    bevel(img, "iron", light=3, dark=0)
    return img


# ---- The Ore Processor (SPEC 40): void steel housing with a draconium window; the Arcane Catalyst (SPEC 41) ----------
PROCESSOR_WINDOW = [
    "................",
    "................",
    "................",
    "...oooooooooo...",
    "...owwwwwwwwo...",
    "...owggggggwo...",
    "...owg....gwo...",
    "...owg....gwo...",
    "...owg....gwo...",
    "...owg....gwo...",
    "...owggggggwo...",
    "...owwwwwwwwo...",
    "...oooooooooo...",
    "................",
    "................",
    "................",
]


def processor_side(glow):
    img = new_tex()
    noise_fill(img, "void_steel", seeded("proc_side"), weights=(1, 4, 6, 2, 0), cluster=2, grain=0.1)
    bevel(img, "void_steel", light=4, dark=0)
    overlay(img, PROCESSOR_WINDOW, legend(o=("void_steel", 0), w=("void_steel", 3), g=("draconium", 1)))
    px = img.load()
    for y in range(6, 10):
        for x in range(7, 9):
            px[x, y] = glow[1] if (x + y) % 2 else glow[0]
    for y in range(6, 10):
        for x in (6, 9):
            px[x, y] = glow[0]
    return img


@block("ore_processor_side", "ore processor side")
def _():
    return processor_side((tones("draconium")[1], tones("draconium")[2]))


@block("ore_processor_side_active", "ore processor side, working", anim(2, True))
def _():
    frames = []
    for f in range(6):
        k = 0.5 + 0.5 * math.sin(f / 6 * 2 * math.pi)
        frames.append(processor_side((scale_rgb(tones("draconium")[3], 1 + 0.3 * k), scale_rgb(tones("draconium")[4], 1 + 0.5 * k))))
    return strip(frames)


@block("ore_processor_top", "ore processor top")
def _():
    img = new_tex()
    noise_fill(img, "void_steel", seeded("proc_top"), weights=(1, 3, 6, 3, 0), cluster=2, grain=0.1)
    bevel(img, "void_steel", light=4, dark=0)
    px = img.load()
    for y in range(4, 12):
        for x in range(4, 12):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = tones("osmium")[3] if ring > 3 else tones("draconium")[0] if ring > 2 else tones("draconium")[2]
    return img


@block("ore_processor_bottom", "ore processor bottom")
def _():
    img = new_tex()
    noise_fill(img, "void_steel", seeded("proc_bottom"), weights=(2, 5, 5, 1, 0), cluster=2)
    bevel(img, "void_steel", light=2, dark=0)
    return img


CATALYST_RUNE = [
    "................",
    "................",
    "......rrrr......",
    ".....r....r.....",
    "....r..RR..r....",
    "...r..R..R..r...",
    "...r.R.cc.R.r...",
    "...r.R.cc.R.r...",
    "...r..R..R..r...",
    "....r..RR..r....",
    ".....r....r.....",
    "......rrrr......",
    "................",
    "................",
    "................",
    "................",
]


def catalyst_front(lit):
    img = new_tex()
    noise_fill(img, "arcane", seeded("cat_front"), weights=(1, 4, 6, 1, 0), cluster=2, grain=0.15)
    bevel(img, "bronze", light=3, dark=1)
    leg = legend(r=("rune", 1 if lit else 0), R=("rune", 3 if lit else 1), c=("rune", 4 if lit else 2))
    overlay(img, CATALYST_RUNE, leg)
    return img


@block("arcane_catalyst_front", "arcane catalyst front")
def _():
    return catalyst_front(False)


@block("arcane_catalyst_front_lit", "arcane catalyst front, quickening")
def _():
    return catalyst_front(True)


@block("arcane_catalyst_side", "arcane catalyst side")
def _():
    img = new_tex()
    noise_fill(img, "arcane", seeded("cat_side"), weights=(1, 4, 6, 1, 0), cluster=2, grain=0.15)
    bevel(img, "bronze", light=3, dark=1)
    px = img.load()
    for x in range(2, 14):
        px[x, 7] = tones("bronze")[2]
        px[x, 8] = tones("bronze")[1]
    return img


@block("arcane_catalyst_back", "arcane catalyst back (shaft side)")
def _():
    img = new_tex()
    noise_fill(img, "arcane", seeded("cat_back"), weights=(1, 4, 6, 1, 0), cluster=2, grain=0.15)
    bevel(img, "bronze", light=3, dark=1)
    px = img.load()
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = tones("iron")[1] if 6 <= x <= 9 and 6 <= y <= 9 else tones("bronze")[2]
    return img


def probe_part(path, label, pal, seed, weights=(0, 2, 6, 3, 1), band=None):
    def build():
        img = new_tex()
        noise_fill(img, pal, seeded(seed), weights=weights, cluster=2, grain=0.1)
        if band:
            px = img.load()
            for y in band:
                for x in range(16):
                    px[x, y] = tones("heat")[2]
        return img
    MOD_ITEMS[path] = (label, build, None)


probe_part("probe_hull", "probe hull", "alu", "probe_hull", band=(9, 10))
probe_part("probe_cap", "probe cap", "quartz", "probe_cap")
probe_part("probe_fin", "probe fin", "heat", "probe_fin", weights=(0, 3, 6, 2, 0))
probe_part("probe_nozzle", "probe nozzle", "iron", "probe_nozzle", weights=(2, 4, 4, 1, 0))

PAYLOAD_MODULE = [
    "................",
    "................",
    "....oooooooo....",
    "...ommmmmmmmo...",
    "..omllllllllmo..",
    "..omlhhhhhhlmo..",
    "..omlwwwwwwlmo..",
    "..omlwWWWWwlmo..",
    "..omlwWWWWwlmo..",
    "..omlwwwwwwlmo..",
    "..omllllllllmo..",
    "..odddddddddddo.",
    "...oddddddddo...",
    "....oooooooo....",
    "................",
    "................",
]


def module_item(path, label, pal, window):
    leg = shade(pal)
    leg.update(legend(w=(window, 2), W=(window, 4)))
    mod_item(path, label, PAYLOAD_MODULE, leg)


module_item("sampler_module", "sampler module (halo steel)", "halo_steel", "cyan")
module_item("deep_space_module", "deep-space module (void steel)", "void_steel", "draconium")


@crown_item("voidglass", "voidglass")
def _():
    rows = [
        "................",
        ".......oo.......",
        "......omho......",
        ".....ommlho.....",
        ".....odmmlo.....",
        "....oddmmmlo....",
        "....odddmmlo....",
        "...oddddmmmlo...",
        "...odddddmmlo...",
        "...odddddmmmo...",
        "....odddddmo....",
        "....oddddddo....",
        ".....oddddo.....",
        "......oddo......",
        ".......oo.......",
        "................",
    ]
    img = grid_tex(rows, shade("void"))
    px = img.load()
    for x, y in ((6, 6), (8, 9), (5, 10), (9, 5)):  # stars inside the glass
        px[x, y] = rgb("#e0e8ff")
    return img


@crown_item("deep_space_sample", "deep-space sample")
def _():
    rows = [
        "................",
        "......oooo......",
        "......ossso.....",
        ".....oooooo.....",
        ".....ogGggo.....",
        ".....ogGggo.....",
        ".....ogggGo.....",
        ".....oggGgo.....",
        ".....oGgggo.....",
        ".....ogggGo.....",
        ".....ogGggo.....",
        ".....ogggGo.....",
        ".....oggggo.....",
        "......oooo......",
        "................",
        "................",
    ]
    return grid_tex(rows, legend(o=("void_steel", 0), s=("steel", 3), g=("quasar_alloy", 2), G=("quasar_alloy", 4)))


# Block model texture maps (models/block/<file>.json -> "textures"). Geometry stays as it is.
# ---------------------------------------------------------------------------------------------------------------------
# The Sky Harvest (SPEC 26, firmages-core M11): storm glass (a Fusion connected glass, pieced layout like the rock panel
# pattern: frame, open, vertical edges, horizontal edges, corners), the Storm Rod (a copper basin with a meteoric iron
# rod and a storm glass tip), the Aurora Net (a meteoric frame with a fine mesh and a veil that shimmers green and
# violet while it catches) and aurora light (still and flowing, animated in the two aurora colours of sky_harvest.json).
# The fluid textures are named by the mod's code (client/SkyHarvestClient), see CODE_TEXTURES.
# ---------------------------------------------------------------------------------------------------------------------
PAL.update({
    "aurora": ["#0a3a2a", "#16805a", "#3cff9e", "#9effd0", "#e8fff4"],
    "auroraviolet": ["#1c1050", "#3c2a9a", "#7a5cff", "#b0a0ff", "#ece6ff"],
})
CODE_TEXTURES = {"block/aurora_light_still", "block/aurora_light_flow"}


def storm_glass_tile(edges):
    """One 16 px glass tile: the frame pixels named by edges (subset of 'nsewc'), transparent glass with two streaks."""
    img = new_tex()
    px = img.load()
    t = tones("stormglass")
    for y in range(16):
        for x in range(16):
            on = (("w" in edges and x == 0) or ("e" in edges and x == 15) or ("n" in edges and y == 0) or ("s" in edges and y == 15)
                  or ("c" in edges and x in (0, 15) and y in (0, 15)))
            if on:
                px[x, y] = t[1] if x == 15 or y == 15 else t[3]
    for i in range(4):  # two diagonal storm streaks, the same on every tile so neighbours line up
        for sx, sy in ((3, 9), (8, 6)):
            x, y = sx + i, sy - i
            if 1 <= x <= 14 and 1 <= y <= 14:
                px[x, y] = t[4] if i == 0 else t[2]
    return img


def storm_glass_strip():
    tiles = [storm_glass_tile("nsew"), storm_glass_tile(""), storm_glass_tile("we"), storm_glass_tile("ns"), storm_glass_tile("c")]
    out = Image.new("RGBA", (16 * len(tiles), 16), (0, 0, 0, 0))
    for i, tile in enumerate(tiles):
        out.paste(tile, (16 * i, 0))
    return out


BLOCKS["storm_glass"] = ("storm glass (connected)", storm_glass_strip, {"fusion": {"type": "connecting", "layout": "pieced"}})


@block("storm_glass_particle", "storm glass (single)")
def _():
    return storm_glass_tile("nsew")


@block("storm_rod_side", "storm rod basin side")
def _():
    img = new_tex()
    noise_fill(img, "copper", seeded("storm_rod_side"), weights=(0, 2, 6, 3, 1), cluster=3, grain=0.15)
    px = img.load()
    t = tones("copper")
    for x in range(16):  # rim of the basin (rows 8..9) and the foot (rows 14..15)
        px[x, 8] = t[4]
        px[x, 9] = t[1]
        px[x, 14] = t[3]
        px[x, 15] = t[0]
    for x in (4, 8, 12):  # rivets
        px[x, 11] = t[4]
    return img


@block("storm_rod_top", "storm rod basin top")
def _():
    img = new_tex()
    noise_fill(img, "copper", seeded("storm_rod_top"), weights=(0, 3, 6, 2, 0), cluster=2, grain=0.2)
    bevel(img, "copper", light=4, dark=0, corner=1)
    px = img.load()
    for (x, y) in ring_pixels(1.5, 2.6):  # the socket of the rod
        px[x, y] = tones("copper")[0]
    return img


@block("storm_rod_bottom", "storm rod basin bottom")
def _():
    img = new_tex()
    noise_fill(img, "copper", seeded("storm_rod_bottom"), weights=(1, 4, 5, 1, 0), cluster=3, grain=0.15)
    return img


@block("storm_rod_rod", "storm rod (meteoric iron)")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("storm_rod_rod"), weights=(0, 2, 6, 4, 1), cluster=2, grain=0.2)
    etch(img, "meteoric", seeded("storm_rod_rod_etch"), 6, mask_alpha=False)
    return img


@block("storm_rod_tip", "storm rod tip (storm glass)")
def _():
    img = new_tex()
    noise_fill(img, "stormglass", seeded("storm_rod_tip"), weights=(0, 1, 4, 5, 2), cluster=2, grain=0.2)
    px = img.load()
    for x, y in ((6, 6), (7, 6), (6, 7)):
        px[x, y] = tones("stormglass")[4]
    return img


@block("aurora_net_frame", "aurora net frame")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("aurora_net_frame"), weights=(0, 2, 6, 4, 1), cluster=2, grain=0.2)
    etch(img, "meteoric", seeded("aurora_net_frame_etch"), 5, mask_alpha=False)
    bevel(img, "meteoric", light=3, dark=0, corner=1)
    return img


@block("aurora_net_base", "aurora net base")
def _():
    img = new_tex()
    noise_fill(img, "meteoric", seeded("aurora_net_base"), weights=(0, 3, 6, 3, 0), cluster=3, grain=0.15)
    bevel(img, "meteoric", light=3, dark=0, corner=1)
    px = img.load()
    for (x, y) in ring_pixels(3.5, 4.6):  # a storm glass eye in the base
        px[x, y] = tones("stormglass")[3]
    return img


@block("aurora_net_net", "aurora net mesh")
def _():
    """A fine copper mesh on a meteoric rim; the holes are open (translucent model)."""
    img = new_tex()
    px = img.load()
    m = tones("meteoric")
    c = tones("copper")
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = m[2] if x == 0 or y == 0 else m[1]
            elif (x + y) % 4 == 0 or (x - y) % 4 == 0:
                px[x, y] = c[3] if (x + y) % 8 == 0 else c[2]
    return img


@block("aurora_net_veil", "aurora net veil (resting)")
def _():
    img = new_tex()
    px = img.load()
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 7 == 0:
                px[x, y] = rgb(PAL["stormglass"][3], 90)
    return img


def aurora_band(f, y, x, phase_mul=1.0):
    """The aurora colour at (x, y) of frame f: green below, violet above, a slow wave."""
    wave = math.sin((x / 16.0 + f / 8.0) * 2 * math.pi * phase_mul) * 2.0
    k = (y + wave) / 16.0
    g = tones("aurora")
    v = tones("auroraviolet")
    a, b = (g[3], v[2]) if k > 0.5 else (g[2], g[3])
    t = max(0.0, min(1.0, abs(k - 0.5) * 2))
    return tuple(int(a[i] * t + b[i] * (1 - t)) for i in range(3))


@block("aurora_net_veil_catching", "aurora net veil (catching)", anim(4, True))
def _():
    frames = []
    for f in range(8):
        fr = new_tex()
        px = fr.load()
        for y in range(16):
            for x in range(16):
                c = aurora_band(f, y, x)
                alpha = 150 + int(70 * math.sin((y / 16.0 + f / 8.0) * 2 * math.pi) ** 2)
                px[x, y] = c + (alpha,)
        frames.append(fr)
    return strip(frames)


def aurora_light_frames(flow):
    frames = []
    rng = seeded("aurora_light_flow" if flow else "aurora_light_still")
    sparks = [(rng.randrange(16), rng.randrange(16)) for _ in range(6)]
    for f in range(8):
        fr = new_tex()
        px = fr.load()
        for y in range(16):
            for x in range(16):
                yy = (y - 2 * f) % 16 if flow else y
                c = aurora_band(f, yy, x, 2.0 if flow else 1.0)
                px[x, y] = c + (235,)
        for i, (sx, sy) in enumerate(sparks):
            if (i + f) % 3 == 0:
                px[sx, (sy + (2 * f if flow else 0)) % 16] = tones("aurora")[4]
        frames.append(fr)
    return strip(frames)


@block("aurora_light_still", "aurora light (still)", anim(3, True))
def _():
    return aurora_light_frames(False)


@block("aurora_light_flow", "aurora light (flowing)", anim(2, True))
def _():
    return aurora_light_frames(True)


STORM_ROD_ITEM = [
    "................",
    ".......hh.......",
    "......hlld......",
    ".......dd.......",
    ".......rr.......",
    ".......rR.......",
    ".......rR.......",
    ".......rR.......",
    ".......rR.......",
    ".......rR.......",
    "...cccccccccc...",
    "...CccccccccC...",
    "....CccccccC....",
    "....CccccccC....",
    "...kkkkkkkkkk...",
    "................",
]


def _storm_rod_item():
    leg = legend(h=("stormglass", 4), l=("stormglass", 3), d=("stormglass", 1), r=("meteoric", 3), R=("meteoric", 1),
                 c=("copper", 3), C=("copper", 1), k=("copper", 0))
    MOD_ITEMS["storm_rod"] = ("storm rod", lambda: grid_tex(STORM_ROD_ITEM, leg), None)


_storm_rod_item()

BUCKET = [
    "................",
    "................",
    "................",
    "...oooooooooo...",
    "..oaaaaaaaaaao..",
    "..oAaaaaAaaaao..",
    "..olaaaaaaaaao..",
    "...ollmmmmmmo...",
    "...olmmmmmmdo...",
    "...olmmmmmmdo...",
    "....olmmmmdo....",
    "....olmmmmdo....",
    "....odddddd.....",
    ".....oooooo.....",
    "................",
    "................",
]


def _aurora_bucket():
    leg = {**shade("steel"), **legend(a=("aurora", 2), A=("auroraviolet", 3))}
    MOD_ITEMS["aurora_light_bucket"] = ("aurora light bucket", lambda: grid_tex(BUCKET, leg), None)


_aurora_bucket()


B = "firmages:block/"
BLOCKS_WITHOUT_ITEM: set[str] = {"arena_altar"}  # round G2: every mod block has an item (pick-block), SPEC 28.5 and 30.4; G3: the fight altar picks the Gathering altar's item (SPEC 39.1)
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
    "ark_plinth": {"particle": B + "ark_plinth_side", "side": B + "ark_plinth_side", "top": B + "ark_plinth_top",
                   "bottom": B + "ark_plinth_bottom"},
    "reactor_controller": {"side": B + "reactor_controller_side", "top": B + "reactor_controller_top",
                           "bottom": B + "reactor_controller_bottom"},
    "reactor_controller_ready": {"side": B + "reactor_controller_side", "top": B + "reactor_controller_top_ready",
                                 "bottom": B + "reactor_controller_bottom"},
    "tally_chute": {"particle": B + "tally_chute_side", "side": B + "tally_chute_side", "top": B + "tally_chute_top",
                    "bottom": B + "tally_chute_bottom"},
    "sky_rock": {"all": B + "sky_rock"},
    "meteorite": {"all": B + "meteorite"},
    "starsteel_meteorite": {"all": B + "starsteel_meteorite"},
    "starfall_core_meteoric_iron": {"all": B + "starfall_core_meteoric_iron"},
    "starfall_core_starsteel": {"all": B + "starfall_core_starsteel"},
    "starfall_core_hot": {"glow": B + "starfall_core_hot", "particle": B + "starfall_core_hot"},
    "meteoric_iron_block": {"all": B + "meteoric_iron_block"},
    "starsteel_block": {"all": B + "starsteel_block"},
    "skystone": {"all": B + "skystone"},
    "firmament_crystal_cluster": {"cross": B + "firmament_crystal_cluster", "particle": B + "firmament_crystal_cluster"},
    "firmament_lode": {"all": B + "firmament_lode", "glow": B + "firmament_lode_glow", "particle": B + "firmament_lode"},
    "star_anvil": {"particle": B + "star_anvil_side", "side": B + "star_anvil_side", "base": B + "star_anvil_base",
                   "top": B + "star_anvil_top"},
    "star_anvil_formed": {"particle": B + "star_anvil_side", "side": B + "star_anvil_side", "base": B + "star_anvil_base",
                          "top": B + "star_anvil_top_formed"},
    "star_anvil_active": {"particle": B + "star_anvil_side", "side": B + "star_anvil_side", "base": B + "star_anvil_base",
                          "top": B + "star_anvil_top_active"},
    "lens_mount": {"particle": B + "lens_mount_frame", "frame": B + "lens_mount_frame", "top": B + "lens_mount_top",
                   "glass": B + "lens_glass"},
    "lens_mount_in": {"particle": B + "lens_mount_frame", "frame": B + "lens_mount_frame", "top": B + "lens_mount_top_in",
                      "glass": B + "lens_glass"},
    "storm_glass": {"all": B + "storm_glass", "particle": B + "storm_glass_particle"},
    "storm_glass_item": {"all": B + "storm_glass_particle"},
    "storm_rod_lower": {"particle": B + "storm_rod_side", "side": B + "storm_rod_side", "top": B + "storm_rod_top",
                        "bottom": B + "storm_rod_bottom", "rod": B + "storm_rod_rod"},
    "storm_rod_upper": {"particle": B + "storm_rod_rod", "rod": B + "storm_rod_rod", "tip": B + "storm_rod_tip"},
    "aurora_net": {"particle": B + "aurora_net_frame", "frame": B + "aurora_net_frame", "base": B + "aurora_net_base",
                   "net": B + "aurora_net_net", "veil": B + "aurora_net_veil"},
    "aurora_net_catching": {"particle": B + "aurora_net_frame", "frame": B + "aurora_net_frame", "base": B + "aurora_net_base",
                            "net": B + "aurora_net_net", "veil": B + "aurora_net_veil_catching"},
}
for _ph in WAYSHRINE_PHASES:
    MODEL_TEXTURES["wayshrine_core_" + _ph] = {"particle": B + "wayshrine_core_side", "side": B + "wayshrine_core_side",
                                                "top": B + "wayshrine_core_top_" + _ph, "bottom": B + "wayshrine_core_bottom"}
for _k in WAYSHRINE_KINDS:
    MODEL_TEXTURES["wayshrine_stone_" + _k] = {"particle": B + "wayshrine_stone_" + _k, "all": B + "wayshrine_stone_" + _k}
for _top, _model in (("sky_anchor_top", "sky_anchor"), ("sky_anchor_top_active", "sky_anchor_active")):
    MODEL_TEXTURES[_model] = {"particle": B + "sky_anchor_side", "side": B + "sky_anchor_side", "base": B + "sky_anchor_base",
                              "top": B + _top}
for _st in (1, 2, 3):
    MODEL_TEXTURES[f"launch_control_{_st}"] = {"particle": B + "launch_control_side", "front": B + f"launch_control_front_{_st}",
                                              "side": B + "launch_control_side", "top": B + "launch_control_top",
                                              "bottom": B + "launch_control_bottom"}
MODEL_TEXTURES["launch_port"] = {"particle": B + "launch_port_side", "side": B + "launch_port_side", "top": B + "launch_port_top",
                                 "bottom": B + "launch_port_top"}
MODEL_TEXTURES["ore_processor"] = {"particle": B + "ore_processor_side", "side": B + "ore_processor_side", "top": B + "ore_processor_top",
                                   "bottom": B + "ore_processor_bottom"}
MODEL_TEXTURES["ore_processor_active"] = {"particle": B + "ore_processor_side", "side": B + "ore_processor_side_active",
                                          "top": B + "ore_processor_top", "bottom": B + "ore_processor_bottom"}
for _lit in ("", "_lit"):
    MODEL_TEXTURES["arcane_catalyst" + _lit] = {"particle": B + "arcane_catalyst_side", "front": B + "arcane_catalyst_front" + _lit,
                                                "side": B + "arcane_catalyst_side", "top": B + "arcane_catalyst_side",
                                                "bottom": B + "arcane_catalyst_back"}
MODEL_TEXTURES["probe_cradle"] = {"particle": B + "probe_cradle_side", "side": B + "probe_cradle_side", "top": B + "probe_cradle_top",
                                  "bottom": B + "probe_cradle_bottom", "clamp": B + "probe_cradle_clamp"}


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
    for name, (label, builder, mcmeta) in KJS_BLOCKS.items():
        out = KJS_TEX / "block" / (name + ".png")
        write_with_meta(builder(), out, mcmeta)
        written.append(out)
    for path, (label, builder, mcmeta) in MOD_ITEMS.items():
        out = MOD_TEX / "item" / (path + ".png")
        write_with_meta(builder(), out, mcmeta)
        written.append(out)
    for table in (BLOCKS, CONSECRATED):
        for name, (label, builder, mcmeta) in table.items():
            out = MOD_TEX / "block" / (name + ".png")
            write_with_meta(builder(), out, mcmeta)
            written.append(out)
    for name, (label, builder, mcmeta) in MOD_GUI.items():
        out = MOD_TEX / "gui" / (name + ".png")
        write_with_meta(builder(), out, mcmeta)
        written.append(out)
    for path, (label, builder, mcmeta) in MOD_ITEMS.items():
        out = MOD_TEX / "item" / (path + ".png")
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
    for name, (label, builder, mcmeta) in KJS_BLOCKS.items():
        entries.append((name, label, KJS_TEX / "block" / (name + ".png"), mcmeta))
    for path, (label, builder, mcmeta) in MOD_ITEMS.items():
        entries.append((path, label, MOD_TEX / "item" / (path + ".png"), mcmeta))
    font = ImageFont.load_default(size=11)
    title_font = ImageFont.load_default(size=14)
    cell = 16 * scale
    pad = 10
    label_h = 40
    cw, ch = max(cell + pad * 2, 184), cell + pad + label_h
    rows_n = (len(entries) + cols - 1) // cols
    animated = [e for e in entries if e[3] and "animation" in e[3]]
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
    # 1a. textures the mod's code names (the fluid textures of client/SkyHarvestClient)
    for ref in CODE_TEXTURES:
        f = MOD_TEX / (ref + ".png")
        referenced.add(f.resolve())
        if not f.exists():
            errors.append(f"{f.relative_to(ROOT)}: named by the mod's code but missing")
    # 1b. the consecrated family (dev/archive/textures-notes.md) must be used by the mod's models
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
    # 3. mod block items need a model (since round G2 every mod block has an item, SPEC 24.5)
    for p in sorted((MOD_ASSETS / "blockstates").glob("*.json")):
        im = MOD_ASSETS / "models" / "item" / p.name
        if not im.exists() and p.stem not in BLOCKS_WITHOUT_ITEM:
            errors.append(f"block {p.stem}: no item model models/item/{p.name}")
    # 4. KubeJS items: every id registered with a literal create('firmages:...') in a startup script (items.js, and
    # the small osmium piece in osmium.js) needs textures/item/<path>.png (KubeJS generates the model)
    ids = [i for js in sorted(ITEMS_JS.parent.glob("*.js"))
           for i in re.findall(r"event\.create\('firmages:([^']+)'", js.read_text(encoding="utf-8"))]
    for path in ids:
        model = KJS_ASSETS / "models" / "item" / (path + ".json")
        f = KJS_TEX / "item" / (path + ".png")
        if model.exists():
            continue  # its textures were checked in step 1
        referenced.add(f.resolve())
        if not f.exists():
            errors.append(f"startup_scripts: firmages:{path} has no texture {f.relative_to(ROOT)}")
    # 4b. texture ids named in the startup scripts (KubeJS block builders); "${...}" in a template matches any part
    patterns = []
    for p in sorted((ROOT / "kubejs" / "startup_scripts").glob("*.js")):
        for m in re.finditer(r"[`'](firmages:(?:block|item)/[^`'\s]+)[`']", p.read_text(encoding="utf-8")):
            ref = m.group(1)
            if "${" in ref:
                parts = re.split(r"\$\{[^}]*\}", ref)
                patterns.append(re.compile("^" + "[a-z0-9_]+".join(re.escape(x) for x in parts) + "$"))
                continue
            f = tex_file(ref)
            referenced.add(f.resolve())
            if not f.exists():
                errors.append(f"{p.relative_to(ROOT)}: texture {ref} missing ({f.relative_to(ROOT)})")
    for name in KJS_BLOCKS:
        f = KJS_TEX / "block" / (name + ".png")
        if any(pt.match("firmages:block/" + name) for pt in patterns):
            referenced.add(f.resolve())
        if not f.exists():
            errors.append(f"{f.relative_to(ROOT)}: drawn by gen_textures.py but not written (run without --check)")
    for path in MOD_ITEMS:
        f = MOD_TEX / "item" / (path + ".png")
        if not f.exists():
            errors.append(f"{f.relative_to(ROOT)}: drawn by gen_textures.py but not written (run without --check)")
    for name in MOD_GUI:
        f = MOD_TEX / "gui" / (name + ".png")
        if not f.exists():
            errors.append(f"{f.relative_to(ROOT)}: drawn by gen_textures.py but not written (run without --check)")
    for path in ITEMS:
        if path not in ids:
            warnings.append(f"gen_textures.py draws firmages:{path} but no startup script registers it")
    # 5. every block/item PNG: 16 wide, height 16 or n*16 with .mcmeta; orphans; mcmeta shape and LF
    #    (textures/gui/ holds the quest theme art of dev/gen_quest_art.py: full-size images, not 16 px textures)
    def blockitem(f, root):
        # 16 px block and item tiles only: gui art, the boss's GeckoLib skins (entity/, dev/gen_primordial.py) and
        # the 18 px mob effect icon follow other size rules
        return f.relative_to(root).parts[0] in ("block", "item")

    for root in (MOD_TEX, KJS_TEX):
        if not root.exists():
            continue
        for p in sorted(x for x in root.rglob("*.png") if blockitem(x, root)):
            img = Image.open(p)
            meta = p.with_suffix(".png.mcmeta")
            if meta.exists() and "fusion" in json.loads(meta.read_text(encoding="utf-8")):
                # Fusion connected texture: tiles of 16 side by side (pieced) or stacked (vertical), no animation
                if img.width % 16 or img.height % 16 or (img.width > 16 and img.height > 16):
                    errors.append(f"{p.relative_to(ROOT)}: size {img.width}x{img.height}, want a row or column of 16 px tiles")
                if p.resolve() not in referenced:
                    warnings.append(f"{p.relative_to(ROOT)}: not referenced by any model or items.js")
                continue
            if img.width != 16 or img.height % 16 != 0:
                errors.append(f"{p.relative_to(ROOT)}: size {img.width}x{img.height}, want 16 x n*16")
            if img.height > 16 and not meta.exists():
                errors.append(f"{p.relative_to(ROOT)}: {img.height // 16} frames but no .mcmeta")
            if img.height == 16 and meta.exists():
                warnings.append(f"{p.relative_to(ROOT)}: single frame but has .mcmeta")
            if img.height > 16 * 8:
                warnings.append(f"{p.relative_to(ROOT)}: {img.height // 16} frames, keep animations at 4..8")
            if p.resolve() not in referenced:
                warnings.append(f"{p.relative_to(ROOT)}: not referenced by any model or items.js")
        for m in sorted(x for x in root.rglob("*.mcmeta") if blockitem(x, root)):
            if not m.with_suffix("").exists():
                errors.append(f"{m.relative_to(ROOT)}: mcmeta without png")
            raw = m.read_bytes()
            if b"\r\n" in raw:
                errors.append(f"{m.relative_to(ROOT)}: CRLF line endings")
            data = json.loads(raw.decode("utf-8"))
            if "fusion" in data:
                continue
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
