#!/usr/bin/env python3
"""Firmament Ages - art for the FTB Quests theme (kubejs/assets, read by the client only).

Writes
  kubejs/assets/firmages/textures/gui/quests/bg_<key>.png        chapter backgrounds (1280 x 720, stretched by the
                                                                  theme's `background` icon when CQA's shader is off)
  kubejs/assets/firmages/textures/gui/quests/banner_<key>.png    Age banner (1024 x 256) placed as a chapter image
  kubejs/assets/firmages/textures/gui/quests/dependency.png      dependency line texture (64 x 64)
  kubejs/assets/firmages/textures/gui/quests/panel.png           chapter panel backdrop (64 x 64)
  kubejs/assets/firmages/textures/gui/quests/firmament.png       modpack icon of the chapter panel (64 x 64)
  kubejs/assets/ftbquests/textures/shapes/<shape>/*.png          quest shapes gilded (goal), rune (keystone, entry),
                                                                  ember (side mission); FTB Quests only loads shapes
                                                                  from the ftbquests namespace (QuestShape uses
                                                                  FTBQuestsAPI.rl, javap 2101.1.36)
  kubejs/assets/firmages/shaders/core/sky_<key>.{json,fsh}        Certain Questing Additions chapter shaders
  dev/shrine-viewer/out/quest_theme_preview.png                   mock of the themed quest screen (untracked)

The shrine in every background is the cumulative shrine of the chapter's Age, rendered by
dev/shrine-viewer/render_shrine.py (consecrated look, transparent, ground slab removed) and cached as
dev/shrine-viewer/out/quest_cutout_age_N.png. Colours: deep night blue and violet, gold accents, one accent per Age
from the tier files (beam_color, sky_tint). No parchment.

Usage: python dev/gen_quest_art.py [--skip-shrine] [--force-shrine] [--preview-only]
Needs Pillow, numpy, matplotlib (render_shrine).
"""
from __future__ import annotations

import argparse
import math
import os
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "kubejs" / "assets" / "firmages" / "textures" / "gui" / "quests"
SHAPES = ROOT / "kubejs" / "assets" / "ftbquests" / "textures" / "shapes"
SHADERS = ROOT / "kubejs" / "assets" / "firmages" / "shaders" / "core"
VIEWER = ROOT / "dev" / "shrine-viewer"
CACHE = VIEWER / "out"
PREVIEW = CACHE / "quest_theme_preview.png"
DATA_ROOT = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "data" / "firmages"

BG_W, BG_H = 1280, 720
BANNER_W, BANNER_H = 1024, 256

# Firmament palette
NIGHT = "#070A1C"       # deepest sky
NIGHT_BLUE = "#101A3E"
VIOLET = "#2A1B4E"
GOLD = "#E8C56A"
GOLD_DARK = "#8A6A24"
PARCHMENT_TEXT = "#F2E6C9"  # text only, no parchment surfaces

# key, chapter title, poetic title, ring index of the shrine shown (None: none), accent, sky top, sky bottom, mood
# Accent/sky defaults are replaced by beam_color/sky_tint of the ring's tier file when the shrine data is readable.
AGES = [
    ("welcome", "Welcome", "Under the Firmament", None, "#FFB347", "#070A1C", "#2A1B4E", "dawn"),
    ("firmament", "The Firmament", "The Whole Road, Spark to Gate", 8, "#E8C56A", "#070A1C", "#1A1540", "night"),
    ("dawn", "Dawn", "Stone, Straw and the First Spark", None, "#FFB347", "#0A0C24", "#3A2244", "dawn"),
    ("age_0", "Age 0: Stone Age", "Clay, Fire and the Hearth Circle", 0, "#FFB347", "#070A1C", "#2A1B4E", "night"),
    ("age_1", "Age 1: Bronze Age", "The First Alloys", 1, "#FFD54A", "#070A1C", "#261B48", "night"),
    ("age_2", "Age 2: Iron Age", "Steel and the Open Map", 2, "#E06030", "#070A1C", "#2C1A3E", "night"),
    ("age_3", "Age 3: Arcane Age", "Spirits, Source and Alchemy", 3, "#9B59FF", "#08071E", "#301A58", "night"),
    ("age_4", "Age 4: Industrial Age", "Coke, Black Steel and Steam", 4, "#F0F0F0", "#06091A", "#22243E", "night"),
    ("age_5", "Age 5: Electric Age", "High Voltage and Oil", 5, "#40E0FF", "#060A20", "#182A4E", "night"),
    ("age_6", "Age 6: Information Age", "Circuits, Data and the End", 6, "#40FF80", "#05091C", "#162C3C", "night"),
    ("age_7", "Age 7: Space Age", "Rockets, Moons and Draconium", 7, "#E0E8FF", "#03040F", "#121A3A", "space"),
    ("age_8", "Age 8: Quantum Age", "Fusion and the Inner Planets", 8, "#E0B0FF", "#06041A", "#2C1850", "space"),
    ("age_9", "Age 9: Singularity Age", "Chaos, Singularity and the Gate", 8, "#F4F0FF", "#000000", "#1A0A2A", "void"),
]


# ------------------------------------------------------------------------------------------------ helpers
def rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def rgbf(h: str) -> np.ndarray:
    return np.array(rgb(h), dtype=np.float32) / 255.0


def font(size: int, bold: bool = False, italic: bool = False) -> ImageFont.FreeTypeFont:
    """Palatino Linotype when present (Windows), else matplotlib's DejaVu Serif."""
    win = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
    names = {(False, False): "pala.ttf", (True, False): "palab.ttf", (False, True): "palai.ttf", (True, True): "palabi.ttf"}
    p = win / names[(bold, italic)]
    if p.is_file():
        return ImageFont.truetype(str(p), size)
    try:
        import matplotlib
        base = Path(matplotlib.get_data_path()) / "fonts" / "ttf"
        dj = {(False, False): "DejaVuSerif.ttf", (True, False): "DejaVuSerif-Bold.ttf",
              (False, True): "DejaVuSerif-Italic.ttf", (True, True): "DejaVuSerif-BoldItalic.ttf"}
        return ImageFont.truetype(str(base / dj[(bold, italic)]), size)
    except Exception:
        return ImageFont.load_default()


def value_noise(h: int, w: int, cells: int, rng: np.random.Generator) -> np.ndarray:
    """Smooth value noise in [0,1] from a coarse random grid, bilinear (via PIL resize) upsampled."""
    grid = rng.random((cells, cells), dtype=np.float32)
    im = Image.fromarray((grid * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)
    return np.asarray(im, dtype=np.float32) / 255.0


def fbm(h: int, w: int, rng: np.random.Generator, base_cells: int = 4, octaves: int = 5) -> np.ndarray:
    out = np.zeros((h, w), dtype=np.float32)
    amp, total, cells = 1.0, 0.0, base_cells
    for _ in range(octaves):
        out += amp * value_noise(h, w, cells, rng)
        total += amp
        amp *= 0.5
        cells *= 2
    return out / total


def save_png(im: Image.Image, path: Path, quantize: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if quantize:
        # 256-colour palette with dithering keeps the pack small; the sky is dark and smooth enough for it
        im = im.convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    im.save(path, optimize=True)


# ------------------------------------------------------------------------------------------------ shrine cutouts
def render_shrine_cutouts(force: bool, log) -> dict[int, dict]:
    """Render quest_cutout_age_N.png for every ring with render_shrine.py; returns ring n -> {beam, sky}."""
    sys.path.insert(0, str(VIEWER))
    import render_shrine as rs  # noqa: E402

    notes: list[str] = []
    lang = rs.load_lang(DATA_ROOT)
    materials: dict = {}
    rings = []
    for n in rs.discover_ring_numbers(DATA_ROOT):
        ring = rs.parse_ring(n, DATA_ROOT, lang, materials, notes)
        if ring:
            rings.append(ring)
    if not rings:
        raise SystemExit("no shrine rings found under %s" % DATA_ROOT)
    rs.assign_plan_codes(materials)
    rs.assign_roles(rings, rs.load_consecration(DATA_ROOT, notes), notes)
    cumul = {r.n: rs.cumulative_blocks(rings, r.n, notes) for r in rings}
    look = rs.Look(consecrated=True, roles={r.n: r.roles for r in rings}, accents={r.n: r.beam_color for r in rings},
                   age_names={r.n: r.age_name for r in rings})
    info = {r.n: {"beam": r.beam_color, "sky": r.sky_tint} for r in rings}

    # the ground slab becomes fully transparent: build_quads styles it with GROUND_COLORS through CellStyle
    ground = {rs.to_rgb(c) for c in rs.GROUND_COLORS}
    orig = rs.CellStyle

    def cell_style(rgb_, alpha, edge, glow, lw):
        if tuple(rgb_) in ground:
            return orig(rgb_, 0.0, (0.0, 0.0, 0.0, 0.0), glow, 0.0)
        return orig(rgb_, alpha, edge, glow, lw)

    rs.CellStyle = cell_style
    CACHE.mkdir(parents=True, exist_ok=True)
    for r in rings:
        p = CACHE / f"quest_cutout_age_{r.n}.png"
        if p.is_file() and not force:
            continue
        quads = rs.render_cutout(cumul[r.n], materials, r, p, look)
        log(f"rendered {p.name} ({quads} quads)")
    rs.CellStyle = orig
    return info


def load_cutout(n: int) -> Image.Image | None:
    p = CACHE / f"quest_cutout_age_{n}.png"
    if not p.is_file():
        return None
    im = Image.open(p).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


# ------------------------------------------------------------------------------------------------ sky
def sky(w: int, h: int, top: str, bottom: str, accent: str, mood: str, seed: int) -> Image.Image:
    rng = np.random.default_rng(seed)
    yy = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    xx = np.linspace(0.0, 1.0, w, dtype=np.float32)[None, :]
    t, b = rgbf(top), rgbf(bottom)
    img = t[None, None, :] * (1 - yy[..., None]) + b[None, None, :] * yy[..., None]
    img = np.repeat(img, w, axis=1) if img.shape[1] == 1 else img

    # nebula: two fbm layers, one in the accent colour, one in violet, strongest in the lower middle
    acc = rgbf(accent)
    neb1 = fbm(h, w, rng, 3, 5)
    neb2 = fbm(h, w, rng, 5, 4)
    band = np.exp(-((yy - 0.62) ** 2) / 0.09) * np.exp(-((xx - 0.5) ** 2) / 0.35)
    n1 = np.clip(neb1 - 0.42, 0, 1) * 1.8 * band
    n2 = np.clip(neb2 - 0.48, 0, 1) * 1.4 * (0.5 + 0.5 * band)
    strength = {"dawn": 0.45, "night": 0.55, "space": 0.5, "void": 0.35}[mood]
    img += n1[..., None] * acc[None, None, :] * strength * 0.5
    img += n2[..., None] * rgbf("#5B3FA0")[None, None, :] * strength * 0.45

    if mood == "dawn":
        # pre-dawn: a warm band low on the horizon, the night still above it
        glow = np.exp(-((yy - 1.0) ** 2) / 0.022)
        img += glow[..., None] * rgbf("#C05A28")[None, None, :] * 0.45
        img += np.exp(-((yy - 1.0) ** 2) / 0.006)[..., None] * rgbf("#FFB347")[None, None, :] * 0.25
    if mood == "void":
        # the Singularity: a faint white-violet ring of dust round the centre
        rr = np.sqrt(((xx - 0.5) * (w / h)) ** 2 + (yy - 0.55) ** 2)
        ring = np.exp(-((rr - 0.42) ** 2) / 0.004)
        img += ring[..., None] * rgbf("#B8B0FF")[None, None, :] * 0.18

    # stars: many faint, some bright with a soft halo; fewer near the bottom (dawn) or everywhere (space)
    density = {"dawn": 900, "night": 1400, "space": 2400, "void": 1600}[mood]
    stars = np.zeros((h, w), dtype=np.float32)
    n = density
    sx = rng.integers(0, w, n)
    sy = rng.integers(0, h, n)
    mag = rng.random(n) ** 3.0
    for x, y, m in zip(sx, sy, mag):
        if mood == "dawn" and rng.random() < (y / h) ** 2:
            continue
        stars[y, x] = max(stars[y, x], 0.25 + 0.75 * m)
    halo = Image.fromarray((np.clip(stars, 0, 1) * 255).astype(np.uint8), "L")
    core = np.asarray(halo.filter(ImageFilter.GaussianBlur(0.6)), dtype=np.float32) / 255.0
    glow_ = np.asarray(halo.filter(ImageFilter.GaussianBlur(3.0)), dtype=np.float32) / 255.0
    star_col = rgbf("#F6F2FF")
    img += (core * 1.0 + glow_ * 0.9)[..., None] * star_col[None, None, :]
    # a handful of bright stars with four-point glints
    for _ in range(14):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h * 0.8))
        r = int(rng.integers(6, 14))
        for d in range(-r, r + 1):
            f = (1 - abs(d) / r) ** 2 * 0.8
            if 0 <= x + d < w:
                img[y, x + d] += f * star_col
            if 0 <= y + d < h:
                img[y + d, x] += f * star_col

    # vignette, then keep it dark: quest nodes and text must stay readable on top
    vig = 1.0 - 0.45 * (((xx - 0.5) * 2) ** 2 + ((yy - 0.5) * 2) ** 2) / 2.0
    img *= vig[..., None]
    img = np.clip(img, 0, 1)
    return Image.fromarray((img * 255).astype(np.uint8), "RGB")


def place_shrine(bg: Image.Image, cut: Image.Image | None, accent: str, mood: str) -> Image.Image:
    """Shrine cutout low in the picture, fading at the bottom, with a glow of the Age colour behind it."""
    if cut is None:
        return bg
    out = bg.convert("RGBA")
    w, h = out.size
    target_h = int(h * 0.42)
    scale = target_h / cut.height
    cut = cut.resize((int(cut.width * scale), target_h), Image.LANCZOS)
    # glow behind the shrine
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    cx, cy = w // 2, int(h * 0.84)
    gd.ellipse((cx - w * 0.22, cy - h * 0.16, cx + w * 0.22, cy + h * 0.16), fill=(*rgb(accent), 60))
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    out.alpha_composite(glow)
    # ghostly: darker, translucent, fading into the lower edge, so it reads as a distant memory behind the quests
    cut = cut.copy()
    a = np.asarray(cut, dtype=np.float32)
    fade = np.ones((cut.height, 1), dtype=np.float32)
    fade_start = int(cut.height * 0.70)
    fade[fade_start:, 0] = np.linspace(1.0, 0.0, cut.height - fade_start)
    a[..., 3] *= fade * 0.62
    a[..., :3] *= 0.62
    cut = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
    out.alpha_composite(cut, (cx - cut.width // 2, h - cut.height + int(h * 0.03)))
    return out.convert("RGB")


# ------------------------------------------------------------------------------------------------ banner
def banner(title: str, poetic: str, accent: str) -> Image.Image:
    w, h = BANNER_W, BANNER_H
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # dark band, slightly translucent, pointed ends
    band = [(40, 30), (w - 40, 30), (w - 8, h // 2), (w - 40, h - 30), (40, h - 30), (8, h // 2)]
    d.polygon(band, fill=(*rgb(NIGHT_BLUE), 215))
    d.line(band + [band[0]], fill=(*rgb(GOLD), 255), width=4)
    inner = [(58, 44), (w - 58, 44), (w - 30, h // 2), (w - 58, h - 44), (58, h - 44), (30, h // 2)]
    d.line(inner + [inner[0]], fill=(*rgb(accent), 170), width=2)
    # ornaments: a diamond on each side of the title
    f1 = font(64, bold=True)
    f2 = font(34, italic=True)
    tw = d.textlength(title, font=f1)
    ty = 54
    d.text(((w - tw) / 2 + 3, ty + 3), title, font=f1, fill=(0, 0, 0, 200))
    d.text(((w - tw) / 2, ty), title, font=f1, fill=(*rgb(GOLD), 255))
    for sx in ((w - tw) / 2 - 42, (w + tw) / 2 + 42):
        cy = ty + 38
        d.polygon([(sx, cy - 12), (sx + 12, cy), (sx, cy + 12), (sx - 12, cy)], fill=(*rgb(accent), 255))
    # thin rule
    d.line((w * 0.28, 140, w * 0.72, 140), fill=(*rgb(GOLD_DARK), 255), width=2)
    pw = d.textlength(poetic, font=f2)
    d.text(((w - pw) / 2 + 2, 158), poetic, font=f2, fill=(0, 0, 0, 180))
    d.text(((w - pw) / 2, 156), poetic, font=f2, fill=(*rgb(PARCHMENT_TEXT), 255))
    return im


# ------------------------------------------------------------------------------------------------ shapes
def _ss_canvas(ss: int = 4):
    s = 128 * ss
    return Image.new("RGBA", (s, s), (0, 0, 0, 0)), s


def _down(im: Image.Image) -> Image.Image:
    return im.resize((128, 128), Image.LANCZOS)


def polygon_points(cx, cy, r, n, rot=0.0):
    return [(cx + r * math.cos(rot + 2 * math.pi * i / n), cy + r * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]


def star_points(cx, cy, r_out, r_in, n, rot=-math.pi / 2):
    pts = []
    for i in range(2 * n):
        r = r_out if i % 2 == 0 else r_in
        a = rot + math.pi * i / n
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def radial_background(mask: Image.Image) -> Image.Image:
    """White radial gradient (centre opaque, rim transparent) clipped to the mask, like the built-in shapes."""
    s = mask.size[0]
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    rr = np.sqrt((xx - s / 2) ** 2 + (yy - s / 2) ** 2) / (s / 2)
    a = np.clip(1.0 - rr * 1.15, 0, 1) ** 1.4
    m = np.asarray(mask.split()[3], dtype=np.float32) / 255.0
    out = np.zeros((s, s, 4), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = (a * m * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def shape_set(kind: str) -> dict[str, Image.Image]:
    """shape = filled silhouette (tinted by quest state), background = radial highlight, outline = border."""
    ss = 4
    base, s = _ss_canvas(ss)
    cx = cy = s / 2
    shape = base.copy()
    outline = base.copy()
    ds, do = ImageDraw.Draw(shape), ImageDraw.Draw(outline)
    white = (255, 255, 255, 255)
    faint = (255, 255, 255, 110)
    lw = 3 * ss
    if kind == "gilded":
        # goal: eight-point rosette on a disc, with an inner ring (engraving) and a double outline
        pts = star_points(cx, cy, s * 0.49, s * 0.37, 8)
        ds.polygon(pts, fill=white)
        ds.ellipse((cx - s * 0.30, cy - s * 0.30, cx + s * 0.30, cy + s * 0.30), outline=(220, 220, 220, 255), width=lw)
        ds.ellipse((cx - s * 0.235, cy - s * 0.235, cx + s * 0.235, cy + s * 0.235), outline=(235, 235, 235, 255), width=ss)
        do.polygon(pts, outline=white, width=lw)
        do.ellipse((cx - s * 0.30, cy - s * 0.30, cx + s * 0.30, cy + s * 0.30), outline=faint, width=ss)
    elif kind == "rune":
        # keystone / entry: pointed hexagon with an engraved inner hexagon
        pts = polygon_points(cx, cy, s * 0.48, 6, rot=-math.pi / 2)
        ds.polygon(pts, fill=white)
        ds.polygon(polygon_points(cx, cy, s * 0.36, 6, rot=-math.pi / 2), outline=(225, 225, 225, 255), width=ss * 2)
        do.polygon(pts, outline=white, width=lw)
        do.polygon(polygon_points(cx, cy, s * 0.36, 6, rot=-math.pi / 2), outline=faint, width=ss)
    elif kind == "ember":
        # side mission: round, a thin inner ring, a small flame notch at the top
        r = s * 0.44
        ds.ellipse((cx - r, cy - r, cx + r, cy + r), fill=white)
        ds.ellipse((cx - r * 0.78, cy - r * 0.78, cx + r * 0.78, cy + r * 0.78), outline=(225, 225, 225, 255), width=ss * 2)
        ds.polygon([(cx, cy - r * 0.62), (cx + r * 0.16, cy - r * 0.30), (cx, cy - r * 0.40), (cx - r * 0.16, cy - r * 0.30)],
                   fill=(215, 215, 215, 255))
        do.ellipse((cx - r, cy - r, cx + r, cy + r), outline=white, width=lw)
    else:
        raise ValueError(kind)
    shape, outline = _down(shape), _down(outline)
    return {"shape": shape, "background": radial_background(shape), "outline": outline}


# ------------------------------------------------------------------------------------------------ small textures
def dependency_texture() -> Image.Image:
    """64 x 64 line texture: a gold core with a soft edge, a brighter bead every half tile (animated by the theme)."""
    s = 64
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    core = np.exp(-((yy - s / 2) ** 2) / 60.0)
    bead = 0.65 + 0.35 * np.cos(xx / s * 2 * math.pi * 2)
    v = np.clip(core * bead, 0, 1)
    col = rgbf(GOLD)
    img = np.ones((s, s, 3), dtype=np.float32) * (0.35 + 0.65 * v[..., None]) * col[None, None, :]
    img[..., :] += 0.25 * v[..., None]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")


def panel_texture() -> Image.Image:
    """64 x 64 tile for the chapter panel: night blue with a faint weave."""
    rng = np.random.default_rng(7)
    s = 64
    n = value_noise(s, s, 8, rng)
    base = rgbf(NIGHT_BLUE) * 0.9
    img = base[None, None, :] * (0.85 + 0.3 * n[..., None])
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")


def firmament_icon() -> Image.Image:
    """64 x 64 gold eight-point star on a night disc: the chapter panel's modpack button."""
    ss = 4
    s = 64 * ss
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((4 * ss, 4 * ss, s - 4 * ss, s - 4 * ss), fill=(*rgb(NIGHT_BLUE), 255), outline=(*rgb(GOLD), 255), width=2 * ss)
    d.polygon(star_points(s / 2, s / 2, s * 0.36, s * 0.14, 8), fill=(*rgb(GOLD), 255))
    d.ellipse((s / 2 - 5 * ss, s / 2 - 5 * ss, s / 2 + 5 * ss, s / 2 + 5 * ss), fill=(*rgb(PARCHMENT_TEXT), 255))
    return im.resize((64, 64), Image.LANCZOS)


# ------------------------------------------------------------------------------------------------ shaders
SHADER_JSON = """{
  "blend": {
    "func": "add",
    "srcrgb": "one",
    "dstrgb": "zero"
  },
  "vertex": "minecraft:position_tex",
  "fragment": "firmages:%(name)s",
  "samplers": [],
  "attributes": ["Position", "UV0"],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
    { "name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
    { "name": "size", "type": "float", "count": 2, "values": [1920, 1080]},
    { "name": "scrollOffset", "type": "float", "count": 2, "values": [0, 0]},
    { "name": "scrollSize", "type": "float", "count": 2, "values": [0, 0]},
    { "name": "time", "type": "float", "count": 1, "values": [0]},
    { "name": "zoom", "type": "float", "count": 1, "values": [16.0]}
  ]
}
"""

SHADER_FSH = """#version 150

// Firmament Ages: %(title)s. Generated by dev/gen_quest_art.py for Certain Questing Additions
// (assets/certain_questing_additions/chapter_shaders.json). Uniforms as in its custom_background shader.
// Vertex shader minecraft:position_tex gives texCoord0.

in vec2 texCoord0;
out vec4 fragColor;

uniform vec2 size;
uniform vec2 scrollOffset;
uniform vec2 scrollSize;
uniform float time;
uniform float zoom;

const vec3 SKY_TOP = vec3(%(top)s);
const vec3 SKY_BOTTOM = vec3(%(bottom)s);
const vec3 ACCENT = vec3(%(accent)s);
const vec3 VIOLET = vec3(0.36, 0.25, 0.63);
const float NEBULA = %(nebula)s;
const float STARS = %(stars)s;
const float HORIZON = %(horizon)s;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 5; k++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return v;
}

// one star layer: cells of `cell` pixels, one star per cell at a random offset, twinkling
float starLayer(vec2 px, float cell, float seed, float t) {
    vec2 cellId = floor(px / cell);
    vec2 local = fract(px / cell);
    float r1 = hash12(cellId + seed);
    float r2 = hash12(cellId * 1.7 + seed + 3.1);
    if (r1 < 0.55) return 0.0;
    vec2 pos = vec2(r2, hash12(cellId + seed * 2.0 + 7.3)) * 0.8 + 0.1;
    float d = length((local - pos) * cell);
    float tw = 0.75 + 0.25 * sin(t * (1.0 + 2.0 * r1) + r2 * 6.28);
    float core = exp(-d * d * 1.4) * tw;
    float glow = 0.12 / (1.0 + d * d * 0.5) * tw;
    return (core + glow) * (0.4 + 0.6 * r1);
}

void main() {
    vec2 uv = texCoord0;
    float aspect = size.x / max(size.y, 1.0);
    float scale = max(zoom, 1.0) / 16.0;
    vec2 scroll = vec2(0.0);
    if (scrollSize.x > 0.0) scroll.x = scrollOffset.x / scrollSize.x;
    if (scrollSize.y > 0.0) scroll.y = scrollOffset.y / scrollSize.y;

    // base gradient
    vec3 col = mix(SKY_TOP, SKY_BOTTOM, uv.y);

    // nebula, slow drift, parallax with the map
    vec2 nuv = vec2(uv.x * aspect, uv.y) * 2.2 + scroll * 0.35 + vec2(time * 0.004, -time * 0.002);
    float n1 = clamp(fbm(nuv) - 0.42, 0.0, 1.0) * 1.8;
    float n2 = clamp(fbm(nuv * 1.9 + 41.0) - 0.47, 0.0, 1.0) * 1.4;
    float band = exp(-pow(uv.y - 0.62, 2.0) / 0.09) * exp(-pow(uv.x - 0.5, 2.0) / 0.35);
    col += n1 * band * ACCENT * NEBULA * 0.5;
    col += n2 * (0.5 + 0.5 * band) * VIOLET * NEBULA * 0.45;

    // horizon glow (Dawn)
    col += exp(-pow(uv.y - 1.0, 2.0) / 0.022) * vec3(0.75, 0.35, 0.16) * HORIZON;
    col += exp(-pow(uv.y - 1.0, 2.0) / 0.006) * vec3(1.0, 0.70, 0.28) * HORIZON * 0.55;

    // stars in three parallax layers; pixels of the panel, so they keep their size at every zoom
    vec2 px = uv * size;
    float s = 0.0;
    s += starLayer(px + scroll * size * 0.06 / scale, 90.0, 1.0, time) * 1.0;
    s += starLayer(px + scroll * size * 0.12 / scale, 55.0, 2.0, time) * 0.7;
    s += starLayer(px + scroll * size * 0.20 / scale, 34.0, 3.0, time) * 0.45;
    col += s * STARS * vec3(0.96, 0.95, 1.0);

    // vignette and a dark cap so quests and text stay readable
    vec2 c = (uv - 0.5) * 2.0;
    col *= 1.0 - 0.45 * dot(c, c) / 2.0;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""


def glsl_vec(h: str) -> str:
    r, g, b = rgbf(h)
    return "%.3f, %.3f, %.3f" % (r, g, b)


def write_shaders(ages_resolved) -> list[Path]:
    SHADERS.mkdir(parents=True, exist_ok=True)
    written = []
    for key, title, poetic, ring, accent, top, bottom, mood in ages_resolved:
        name = "sky_" + key
        nebula = {"dawn": 0.45, "night": 0.55, "space": 0.5, "void": 0.35}[mood]
        stars = {"dawn": 0.6, "night": 0.85, "space": 1.1, "void": 0.9}[mood]
        horizon = 0.75 if mood == "dawn" else 0.0
        (SHADERS / (name + ".json")).write_text(SHADER_JSON % {"name": name}, encoding="utf-8", newline="\n")
        (SHADERS / (name + ".fsh")).write_text(SHADER_FSH % {
            "title": title, "top": glsl_vec(top), "bottom": glsl_vec(bottom), "accent": glsl_vec(accent),
            "nebula": "%.2f" % nebula, "stars": "%.2f" % stars, "horizon": "%.2f" % horizon},
            encoding="utf-8", newline="\n")
        written += [SHADERS / (name + ".json"), SHADERS / (name + ".fsh")]
    return written


# ------------------------------------------------------------------------------------------------ preview
def tint(im: Image.Image, color: tuple[int, int, int], alpha: float = 1.0) -> Image.Image:
    a = np.asarray(im.convert("RGBA"), dtype=np.float32)
    a[..., 0] *= color[0] / 255.0
    a[..., 1] *= color[1] / 255.0
    a[..., 2] *= color[2] / 255.0
    a[..., 3] *= alpha
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def preview(bg_path: Path, banner_path: Path, shapes: dict[str, dict[str, Image.Image]], theme_colors: dict) -> Image.Image:
    """A mock of the themed quest screen: chapter panel left, banner on top, a strand of quests, dependency lines."""
    W, H = 1600, 900
    im = Image.open(bg_path).convert("RGBA").resize((W, H), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    # chapter panel
    pw = 280
    panel = Image.new("RGBA", (pw, H), (*rgb(NIGHT_BLUE), 225))
    im.alpha_composite(panel, (0, 0))
    d = ImageDraw.Draw(im)
    d.line((pw, 0, pw, H), fill=(*rgb(GOLD_DARK), 255), width=2)
    f = font(22)
    fb = font(22, bold=True)
    im.alpha_composite(firmament_icon().resize((28, 28), Image.LANCZOS), (14, 12))
    d.text((52, 14), "Firmament Ages", font=fb, fill=rgb(GOLD))
    y = 64
    for name, state in [("Welcome", "done"), ("The Firmament", "open"), ("Ages", "group"), ("Dawn", "done"),
                        ("Age 0: Stone Age", "done"), ("Age 1: Bronze Age", "done"), ("Age 2: Iron Age", "done"),
                        ("Age 3: Arcane Age", "selected")]:
        if state == "selected":
            hl = Image.new("RGBA", (pw - 12, 30), (*rgb(GOLD), 45))
            ImageDraw.Draw(hl).rectangle((0, 0, pw - 13, 29), outline=(*rgb(GOLD), 160))
            im.alpha_composite(hl, (6, y - 4))
            d = ImageDraw.Draw(im)
        col = rgb(GOLD) if state in ("selected", "open") else rgb(PARCHMENT_TEXT)
        if state == "group":
            d.text((12, y), name, font=fb, fill=rgb(GOLD_DARK))
        else:
            d.text((26, y), name, font=f, fill=col)
            if state == "done":  # a drawn check mark (the serif font has no U+2713)
                d.line((pw - 30, y + 12, pw - 24, y + 19), fill=rgb("#9BD37A"), width=3)
                d.line((pw - 24, y + 19, pw - 12, y + 5), fill=rgb("#9BD37A"), width=3)
        y += 34
    d.text((26, y + 8), "(later Ages are hidden)", font=font(17, italic=True), fill=(*rgb(PARCHMENT_TEXT), 130))
    # banner
    bn = Image.open(banner_path).convert("RGBA")
    bw = 760
    bn = bn.resize((bw, bw // 4), Image.LANCZOS)
    im.alpha_composite(bn, (pw + (W - pw - bw) // 2, 28))

    # quest nodes: entry (rune, big) -> steps (hexagon) -> keystone (rune) -> goal (gilded); a side (ember) above
    def node(kind, x, y, size, state, label):
        px = int(size)
        if kind == "hexagon":
            sh = shapes["rune"]  # the built-in hexagon is not in the repo; the rune hexagon stands in for it
        else:
            sh = shapes[kind]
        colr = theme_colors[state]
        a = colr[0]
        layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        layer.alpha_composite(tint(sh["shape"].resize((px, px), Image.LANCZOS), colr[1], a))
        layer.alpha_composite(tint(sh["background"].resize((px, px), Image.LANCZOS), (255, 255, 255), 0.55))
        layer.alpha_composite(tint(sh["outline"].resize((px, px), Image.LANCZOS), colr[1], 1.0))
        im.alpha_composite(layer, (int(x - px / 2), int(y - px / 2)))
        if label:
            lf = font(18)
            tw = d.textlength(label, font=lf)
            d.text((x - tw / 2 + 1, y + px / 2 + 7), label, font=lf, fill=(0, 0, 0, 180))
            d.text((x - tw / 2, y + px / 2 + 6), label, font=lf, fill=rgb(PARCHMENT_TEXT))

    base = 64
    cx0, cy = pw + 140, 540
    chain = [("rune", cx0, cy, base * 2.0, "completed", "Welcome to the Arcane Age"),
             ("hexagon", cx0 + 190, cy - 110, base, "completed", "Blaze Burner"),
             ("hexagon", cx0 + 310, cy - 110, base, "started", "Source Jar"),
             ("rune", cx0 + 450, cy - 110, base * 1.5, "not_started", "Keystone: Wilden Chimera"),
             ("hexagon", cx0 + 190, cy + 110, base, "completed", "Beneath Ritual"),
             ("hexagon", cx0 + 310, cy + 110, base, "not_started", "The Nether"),
             ("rune", cx0 + 450, cy + 110, base * 1.5, "locked", "Keystone: Djinni"),
             ("gilded", cx0 + 680, cy, base * 3.0, "locked", "Offer the Arcane Keystone at the Shrine"),
             ("ember", cx0 + 310, cy - 260, base * 0.85, "not_started", "Side: Umvuthi")]
    lines = [(0, 1, "completed"), (1, 2, "completed"), (2, 3, "uncompleted"), (0, 4, "completed"), (4, 5, "uncompleted"),
             (5, 6, "unavailable"), (3, 7, "unavailable"), (6, 7, "unavailable"), (2, 8, "uncompleted")]
    line_col = {"completed": (*rgb(GOLD), 230), "uncompleted": (*rgb("#B8A890"), 150), "unavailable": (*rgb("#6A6080"), 110)}
    for a_, b_, st in lines:
        d.line((chain[a_][1], chain[a_][2], chain[b_][1], chain[b_][2]), fill=line_col[st], width=5)
    for kind, x, y, size, state, label in chain:
        node(kind, x, y, size, state, label)
    # footer hint
    d.text((pw + 24, H - 34), "Mock composite of the Firmament theme: background, chapter banner, shapes gilded / rune / ember."
           " Real zoom, fonts and panel chrome come from FTB Quests.", font=font(17, italic=True),
           fill=(*rgb(PARCHMENT_TEXT), 170))
    return im.convert("RGB")


# ------------------------------------------------------------------------------------------------ main
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--skip-shrine", action="store_true", help="do not render shrine cutouts (use the cache or none)")
    ap.add_argument("--force-shrine", action="store_true", help="re-render the shrine cutouts")
    ap.add_argument("--preview-only", action="store_true", help="only rebuild the preview from the existing textures")
    args = ap.parse_args(argv)
    t0 = time.perf_counter()

    def log(msg):
        print("[%5.1fs] %s" % (time.perf_counter() - t0, msg), flush=True)

    info: dict[int, dict] = {}
    if not args.skip_shrine and not args.preview_only:
        try:
            info = render_shrine_cutouts(args.force_shrine, log)
        except Exception as e:  # art must not depend on the renderer being importable
            log("shrine cutouts skipped: %s" % e)
    ages = []
    for key, title, poetic, ring, accent, top, bottom, mood in AGES:
        if ring is not None and ring in info and key not in ("firmament", "age_9"):
            accent = info[ring]["beam"] or accent
        ages.append((key, title, poetic, ring, accent, top, bottom, mood))

    shapes = {k: shape_set(k) for k in ("gilded", "rune", "ember")}
    if not args.preview_only:
        TEX.mkdir(parents=True, exist_ok=True)
        for i, (key, title, poetic, ring, accent, top, bottom, mood) in enumerate(ages):
            bg = sky(BG_W, BG_H, top, bottom, accent, mood, seed=100 + i)
            bg = place_shrine(bg, load_cutout(ring) if ring is not None else None, accent, mood)
            save_png(bg, TEX / f"bg_{key}.png", quantize=True)
            save_png(banner(title, poetic, accent), TEX / f"banner_{key}.png")
            log(f"wrote bg_{key}.png, banner_{key}.png")
        save_png(dependency_texture(), TEX / "dependency.png")
        save_png(panel_texture(), TEX / "panel.png")
        save_png(firmament_icon(), TEX / "firmament.png")
        for k, parts in shapes.items():
            for part, im in parts.items():
                p = SHAPES / k / f"{part}.png"
                save_png(im, p)
                (SHAPES / k / f"{part}.png.mcmeta").write_text('{\n  "texture": {\n    "blur": true\n  }\n}\n',
                                                               encoding="utf-8", newline="\n")
        log("wrote shapes gilded, rune, ember (ftbquests namespace)")
        for p in write_shaders(ages):
            pass
        log("wrote %d shader files" % (2 * len(ages)))

    theme_colors = {  # (alpha, rgb) of the quest state colours in ftb_quests_theme.txt
        "completed": (0.85, rgb("#E8C56A")), "started": (0.85, rgb("#F2E6C9")),
        "not_started": (0.60, rgb("#A9B4D8")), "locked": (0.85, rgb("#3C3650"))}
    pv = preview(TEX / "bg_age_3.png", TEX / "banner_age_3.png", shapes, theme_colors)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    pv.save(PREVIEW, optimize=True)
    log("wrote %s" % PREVIEW)
    return 0


if __name__ == "__main__":
    sys.exit(main())
