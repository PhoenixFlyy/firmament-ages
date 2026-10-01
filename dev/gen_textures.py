#!/usr/bin/env python
"""Generate every firmages texture (KubeJS items and firmages-core blocks) as 16x16 pixel art from palettes + shapes.

    python dev/gen_textures.py            # write all PNGs (+ .mcmeta), patch the block model texture maps, build the sheet
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

The contact sheet (8x upscaled, labelled) lands in dev/shrine-viewer/out/textures_sheet.png (not tracked).
"""
import argparse
import json
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
ITEMS_JS = ROOT / "kubejs" / "startup_scripts" / "items.js"
SHEET = ROOT / "dev" / "shrine-viewer" / "out" / "textures_sheet.png"

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
}


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def tones(name):
    return [rgb(c) for c in PAL[name]]


def legend(**kw):
    """legend(o=("stone",0), m=("stone",2)) -> {char: RGBA}. Values may also be hex strings."""
    out = {}
    for ch, spec in kw.items():
        out[ch] = rgb(spec) if isinstance(spec, str) else tones(spec[0])[spec[1]]
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


def noise_fill(img, pal, rng, weights=(1, 3, 6, 3, 1), cluster=2, oy=0):
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
            if rng.random() < 0.25:  # single-pixel grain
                idx = max(0, min(4, idx + rng.choice((-1, 1))))
            px[x, y + oy] = t[idx]
    return img


def bricks_fill(img, pal, rng, bw=8, bh=4, weights=(0, 2, 6, 3, 1), oy=0):
    """Stone-brick tile: staggered bricks bw x bh, 1 px mortar in tone 0, light top/left edge, dark bottom/right."""
    t = tones(pal)
    px = img.load()
    for y in range(16):
        row = y // bh
        off = (row % 2) * (bw // 2)
        for x in range(16):
            bx = (x + off) % bw
            by = y % bh
            if by == bh - 1 or bx == bw - 1:
                px[x, y + oy] = t[0]
                continue
            idx = rng.choices(range(5), weights=weights)[0]
            if by == 0 or bx == 0:
                idx = min(4, max(idx, 3))
            elif by == bh - 2 or bx == bw - 2:
                idx = min(idx, 1)
            px[x, y + oy] = t[idx]
    return img


def bevel(img, pal, inset=0, oy=0):
    """1 px light top/left and dark bottom/right frame (tone 4 / tone 1), outline tone 0 outside if inset > 0."""
    t = tones(pal)
    px = img.load()
    lo, hi = inset, 15 - inset
    for i in range(lo, hi + 1):
        px[i, lo + oy] = t[4]
        px[lo, i + oy] = t[4]
        px[i, hi + oy] = t[1]
        px[hi, i + oy] = t[1]
    px[hi, lo + oy] = t[2]
    px[lo, hi + oy] = t[2]
    return img


def overlay(img, rows, leg, oy=0):
    """Paint only the non-'.' cells of an ASCII grid on top of an existing tile."""
    return draw_grid(img, rows, leg, oy)


# ---------------------------------------------------------------------------------------------------------------------
# Item shapes. Light comes from the top-left: left/top edges "l"/"h", right/bottom edges "d".
# ---------------------------------------------------------------------------------------------------------------------
IDOL = [
    "................",
    ".....ooooo......",
    "....olmmmdo.....",
    "....olemedo.....",
    "....olmmmdo.....",
    ".....olmdo......",
    "...oooolmoooo...",
    "..ohlmmmmmmmddo.",
    "..olmmmmgmmmddo.",
    "...ooolmmmdooo..",
    ".....olmmdo.....",
    ".....olmmdo.....",
    "....oolmmdoo....",
    "...olmmdolmmdo..",
    "...olmmdolmmdo..",
    "...ooooo.ooooo..",
]

ITEMS = {}  # id path -> (age label, rows, legend)


def item(path, age, rows, leg):
    ITEMS[path] = (age, rows, leg)


item("unfired_hearth_idol", "Age 0", IDOL, {**shade("clay"), **legend(e=("clay", 0), g=("clay", 1))})
item("hearth_idol", "Age 0", IDOL, {**shade("terracotta"), **legend(e=("terracotta", 0), g=("ember", 2))})

item("hearthstone", "Age 0", [
    "................",
    ".....oooooo.....",
    "....ohlllmmdo...",
    "...ohlmmmmmmdo..",
    "..ohlmmAAmmmddo.",
    "..olmmABBAmmmdo.",
    "..olmABCCBAmmdo.",
    "..olmABCDCBAmdo.",
    "..olmABCCBAmddo.",
    "..olmmABBAmmddo.",
    "..oldmmAAmmdddo.",
    "...odmmmmmmddo..",
    "....oddmmdddo...",
    ".....oooooo.....",
    "................",
    "................",
], {**shade("stone"), **legend(A=("ember", 0), B=("ember", 1), C=("ember", 2), D=("ember", 4))})

item("sky_disc", "Age 1", [
    "................",
    ".....oooooo.....",
    "....ohlmmmmdo...",
    "...ohlmBCmmmdo..",
    "..ohlmBCCmmmmdo.",
    "..olmBCCmmmmmdo.",
    "..olmBCCmmBCCmdo",
    "..olmBCCmmCCDmdo",
    "..olmBCCmmBCCmdo",
    "..olmmBCmmmmmdo.",
    "..oldmmBmmmBddo.",
    "...odmmmmmmddo..",
    "....oddmBmddo...",
    ".....oooooo.....",
    "................",
    "................",
], {**shade("patina"), **legend(B=("gold", 1), C=("gold", 3), D=("gold", 4))})

item("steel_heart", "Age 2", [
    "................",
    "...oooo..oooo...",
    "..ohllmooollmdo.",
    ".ohlmmmmddmmmddo",
    ".olmmmmmmmmmmmdo",
    ".olmmoABBAommmdo",
    ".olmmABCCBAmmmdo",
    ".olmmoABBAommddo",
    "..olmmmoommmddo.",
    "..olmmmmmmmdddo.",
    "...olmmmmmmddo..",
    "....olmmmmddo...",
    ".....olmmddo....",
    "......olddo.....",
    ".......odo......",
    "........o.......",
], {**shade("steel"), **legend(A=("heat", 0), B=("heat", 2), C=("heat", 4))})

KEYSTONE = [
    "................",
    "..oooooooooooo..",
    "..ohlllllmmmddo.",
    "..ohlmmmmmmmmdo.",
    "..ohlmRRRRRmmdo.",
    "...olRmmRmmRdo..",
    "...olmRRRRRmdo..",
    "...olmmmRmmmdo..",
    "...olmmmRmmmdo..",
    "....olmmRmmdo...",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    ".....olmmmdo....",
    ".....olmmddo....",
    ".....ooooooo....",
    "................",
]
item("arcane_keystone", "Age 3", KEYSTONE, {**shade("arcane"), **legend(R=("rune", 3))})

item("awakened_keystone", "Age 8", [
    "s...............",
    "..oooooooooooos.",
    "..ohhhhhhlllmdo.",
    "..ohllllllllldo.",
    "..ohlRRRRRlllds.",
    "s..oRllRllRlmo..",
    "...olRRRRRllmo..",
    "...ollllRlllmos.",
    "...ollllRlllmo..",
    "....olllRllmo...",
    "s...olllllllmo..",
    "....ollllllmmo..",
    ".....olllmmo..s.",
    ".....ollmmmo....",
    ".....ooooooo....",
    "..s.............",
], {**legend(o=("arcane", 0), d=("arcane", 1), m=("arcane", 2), l=("arcane", 3), h=("arcane", 4)),
    **legend(R=("rune", 4), s=("violet", 4))})

item("arcane_gearbox", "Age 4", [
    "................",
    "..o..oooooo..o..",
    "..ooohllmmdooo..",
    "..ohlmmmmmmmdo..",
    ".oohlmAAAAmmdoo.",
    ".oolmABBBBAmdoo.",
    ".oolmABCCBAmdoo.",
    ".oolmABCCBAmdoo.",
    ".oolmABBBBAmdoo.",
    ".oolmmAAAAmddoo.",
    "..olmmmmmmmddo..",
    "..olmmmmmmdddo..",
    "..ooolmmdddooo..",
    "..o..oooooo..o..",
    "................",
    "................",
], {**shade("steel"), **legend(A=("arcane", 1), B=("arcane", 2), C=("arcane", 4))})

item("pressure_core", "Age 4", [
    "................",
    "....oooooooo....",
    "...ohhllllmmdo..",
    "..ohlmmmmmmmddo.",
    "..olmooooooomdo.",
    "..olmoWWWWWomdo.",
    "..olmoWwNwWomdo.",
    "..olmoWWNWWomdo.",
    "..olmoWWoWWomdo.",
    "..olmooooooomdo.",
    "..olmmmmmmmmddo.",
    "..olRomommoRddo.",
    "..oolmmmmmmddoo.",
    "...oolmmmdddoo..",
    "....oooooooo....",
    "................",
], {**shade("iron"), **legend(W=("steam", 3), w=("steam", 1), N="#d03020", R=("rust", 2))})

item("attuned_circuit", "Age 5", [
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhlllo..",
    "..ohmTTmmmmTmdo.",
    "..ohmTmmmmmTmdo.",
    "..olmTmmCmmTmdo.",
    "..olmTmBCDmTmdo.",
    "..olmTmBCDmTmdo.",
    "..olmTmABCmTmdo.",
    "..olmTmmAmmTmdo.",
    "..olmTmmmmmTmdo.",
    "..olmTTTTTTTmdo.",
    "..odddddddddddo.",
    "..oooooooooooo..",
    "..P.P.P.P.P.P...",
    "................",
], {**shade("pcb"), **legend(T=("copper", 3), A=("cyan", 0), B=("cyan", 2), C=("cyan", 3), D=("cyan", 4),
                             P=("alu", 3))})

item("humming_core", "Age 5", [
    "................",
    "....oooooooo....",
    "...ohhllllmmdo..",
    "..ohlmmmmmmmddo.",
    "..oABBBBBBBBBAo.",
    "..olmmmmmCCmddo.",
    "..olmmmmCDCmddo.",
    "..olmmmCDCmmddo.",
    "..olmmCDDDCCddo.",
    "..olmmmmCDCmddo.",
    "..olmmmmCDmmddo.",
    "..olmmmmCmmmddo.",
    "..oABBBBBBBBBAo.",
    "..oolmmmmmmddoo.",
    "....oooooooo....",
    "................",
], {**shade("alu"), **legend(A=("cyan", 0), B=("cyan", 2), C=("cyan", 3), D=("cyan", 4))})

item("data_matrix", "Age 6", [
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhldo.",
    "..ohAAAAAAAAldo.",
    "..ohACBCBCBAldo.",
    "..ohABABABAAldo.",
    "..ohACBCBCBAldo.",
    "..ohABABABAAldo.",
    "..ohACBCBCBAldo.",
    "..ohABABDBAAldo.",
    "..ohAAAAAAAAldo.",
    "..ohllllllllddo.",
    "..odddddddddddo.",
    "..oooooooooooo..",
    "................",
    "................",
], {**shade("quartz"), **legend(A=("data", 0), B=("data", 1), C=("data", 3), D=("data", 4))})

item("star_chart", "Age 7", [
    "................",
    "..oooooooooooo..",
    "..ohhhllllmmddo.",
    "..olNNNNNNNNNdo.",
    "..olNSnNNNNNNdo.",
    "..olNnsnNNSNNdo.",
    "..olNNNsnsNNNdo.",
    "..olNNNNSNNNNdo.",
    "..olNNNNnsNNNdo.",
    "..olNSNNNNsNNdo.",
    "..olNnsNNNNSNdo.",
    "..olNNNsNSNNNdo.",
    "..olNNNNNNNNNdo.",
    "..olmmmmmmddddo.",
    "..oooooooooooo..",
    "................",
], {**shade("desh"), **legend(N=("navy", 1), n=("navy", 3), s=("star", 0), S=("star", 4))})

item("quantum_core", "Age 8", [
    "................",
    ".....oooooo.....",
    "....ohhllmmdo...",
    "...ohlmmmmmmdo..",
    "..ohlmCCCCmmddo.",
    "..ohlCmmmmCmddo.",
    "..olmCmmWmmCmdo.",
    "..olmCmWWWmCmdo.",
    "..olmCmmWmmCddo.",
    "..olmmCmmmmCddo.",
    "..oldmmCCCCdddo.",
    "...odmmmmmmddo..",
    "....oddmmdddo...",
    ".....oooooo.....",
    "................",
    "................",
], {**shade("draconium"), **legend(C=("cyan", 3), W="#ffffff")})

item("ultimate_singularity", "Age 9", [
    "................",
    ".......h........",
    "....dmlllmd.....",
    "...dlhhhhhld....",
    "..dlhkkkkkhld...",
    "..mlhkKKKKkhlm..",
    ".dlhkKKKKKKkhld.",
    ".mlhkKKKKKKkhlm.",
    ".mlhkKKKKKKkhlm.",
    ".dlhkKKKKKKkhld.",
    "..mlhkKKKKkhlm..",
    "..dlhkkkkkhld...",
    "...dlhhhhhld....",
    "....dmlllmd.....",
    "........h.......",
    "................",
], legend(K=("void", 0), k=("void", 2), d=("violet", 0), m=("violet", 2), l=("violet", 3), h=("violet", 4)))

item("origin_coordinates", "Age 9", [
    "................",
    "...oooooooooo...",
    "...ohhhhhhhldo..",
    "...ohIIlIlIldo..",
    "...ohlllllllmo..",
    "...ohIlIIlIlmo..",
    "...ohlllllllmo..",
    "...ohIIlIlIlmo..",
    "...ohlllllllmo..",
    "...ohlllllBlmo..",
    "...ohllllBCBmo..",
    "...ohlllABCBmo..",
    "...ohlllAABAmo..",
    "...odmmmmmmmmo..",
    "...oooooooooo...",
    "................",
], {**shade("paper"), **legend(I=("arcane", 2), A=("draconium", 1), B=("draconium", 3), C=("violet", 4))})

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


@block("shrine_heart_top_lit", "heart top (lit, 4 frames)", {"animation": {"frametime": 6, "interpolate": True}})
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


@block("origin_altar_top", "altar top")
def _():
    img = new_tex()
    noise_fill(img, "void", seeded("altar_top"), weights=(1, 4, 6, 2, 0), cluster=2)
    bevel(img, "void")
    overlay(img, [
        "................",
        ".G............G.",
        "................",
        ".....rrrrrr.....",
        "....r......r....",
        "...r..RRRR..r...",
        "...r.R....R.r...",
        "...r.R.WW.R.r...",
        "...r.R.WW.R.r...",
        "...r.R....R.r...",
        "...r..RRRR..r...",
        "....r......r....",
        ".....rrrrrr.....",
        "................",
        ".G............G.",
        "................",
    ], legend(r=("violet", 0), R=("violet", 2), W=("violet", 4), G=("violet", 3)))
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


def generate():
    written = []
    for path, (age, rows, leg) in ITEMS.items():
        out = KJS_TEX / "item" / (path + ".png")
        write_png(grid_tex(rows, leg), out)
        written.append(out)
    for name, (label, builder, mcmeta) in BLOCKS.items():
        out = MOD_TEX / "block" / (name + ".png")
        write_png(builder(), out)
        written.append(out)
        meta = out.with_suffix(".png.mcmeta")
        if mcmeta:
            meta.write_text(json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")
        elif meta.exists():
            meta.unlink()
    for model, textures in MODEL_TEXTURES.items():
        p = MOD_ASSETS / "models" / "block" / (model + ".json")
        data = json.loads(p.read_text(encoding="utf-8"))
        data["textures"] = textures
        p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return written


# ---------------------------------------------------------------------------------------------------------------------
# Contact sheet
# ---------------------------------------------------------------------------------------------------------------------
def sheet(scale=8, cols=5):
    entries = []
    for path, (age, rows, leg) in ITEMS.items():
        entries.append((path, age, KJS_TEX / "item" / (path + ".png")))
    for name, (label, builder, mcmeta) in BLOCKS.items():
        entries.append((name, label, MOD_TEX / "block" / (name + ".png")))
    font = ImageFont.load_default(size=11)
    cell = 16 * scale
    pad = 10
    label_h = 40
    cw, ch = max(cell + pad * 2, 184), cell + pad + label_h
    rows_n = (len(entries) + cols - 1) // cols
    out = Image.new("RGBA", (cw * cols, ch * rows_n), (40, 40, 44, 255))
    d = ImageDraw.Draw(out)
    for i, (name, sub, p) in enumerate(entries):
        cx, cy = (i % cols) * cw + pad, (i // cols) * ch + pad
        # checkerboard so transparency is visible
        for yy in range(0, cell, 16):
            for xx in range(0, cell, 16):
                c = (70, 70, 76, 255) if ((xx + yy) // 16) % 2 == 0 else (58, 58, 64, 255)
                d.rectangle([cx + xx, cy + yy, cx + xx + 15, cy + yy + 15], fill=c)
        img = Image.open(p).convert("RGBA")
        frame = img.crop((0, 0, 16, 16)) if img.height > 16 else img
        big = frame.resize((cell, cell), Image.NEAREST)
        out.alpha_composite(big, (cx, cy))
        if img.height > 16:  # the other frames, small, right of the tile
            n = img.height // 16
            for f in range(1, n):
                fr = img.crop((0, 16 * f, 16, 16 * (f + 1))).resize((32, 32), Image.NEAREST)
                out.alpha_composite(fr, (cx + cell + 4, cy + 36 * (f - 1)))
        d.text((cx, cy + cell + 4), name, fill=(235, 235, 235, 255), font=font)
        d.text((cx, cy + cell + 20), sub, fill=(160, 160, 170, 255), font=font)
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    out.save(SHEET)
    return SHEET


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
    # 5. every PNG: 16 wide, height 16 or n*16 with .mcmeta; orphans
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
            if p.resolve() not in referenced:
                warnings.append(f"{p.relative_to(ROOT)}: not referenced by any model or items.js")
        for m in sorted(root.rglob("*.mcmeta")):
            if not m.with_suffix("").exists():
                errors.append(f"{m.relative_to(ROOT)}: mcmeta without png")
    for w in warnings:
        print("warn:", w)
    for e in errors:
        print("ERROR:", e)
    print(f"check: {len(referenced)} referenced textures, {len(errors)} errors, {len(warnings)} warnings")
    return not errors


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="validate only, write nothing")
    ap.add_argument("--sheet-only", action="store_true", help="only rebuild the contact sheet")
    a = ap.parse_args()
    if a.check:
        sys.exit(0 if check() else 1)
    if not a.sheet_only:
        written = generate()
        print(f"wrote {len(written)} textures")
    print("sheet:", sheet().relative_to(ROOT))
    sys.exit(0 if check() else 1)


if __name__ == "__main__":
    main()
