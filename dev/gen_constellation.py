#!/usr/bin/env python
"""Title-screen art "Constellation" for FancyMenu 3.9.14 (config/fancymenu/customization/title_screen_layout.txt).

Reads dev/shrine-viewer/shrine_data.json (cumulative voxel data of rings 0..8, Age names, beam colours = Age accents;
written by `python dev/shrine-viewer/render_shrine.py --data-root mod/firmages-core/src/main/resources/data/firmages`)
and, for the previews only, the wordmark and button faces from config/fancymenu/assets/firmament/.

Writes (shipped with the pack):
  config/fancymenu/slideshows/firmament_constellation/images/constellation_age_N.png
                            N = 0..8, 2048x1152 RGBA, transparent except the chart: the shrine after Age N
                            (rings 0..N) reduced to a star chart (raised columns, footprint corners and the heart
                            as glowing nodes in the Age accent, gold edges, faint footprint diamond), the previous
                            Age's chart ghosted in cool white and drifted off-centre, the Age caption in small caps
                            gold at the bottom right. Drawn by the slideshow element fa_title_constellation.
  config/fancymenu/assets/firmament/sky_2304x1296.png
                            night blue to violet gradient, faint nebula, three star sizes, vignette; no chart.
                            The menu background (image, parallax 0.03).
Writes (not tracked, dev/shrine-viewer/out/ is ignored):
  dev/shrine-viewer/out/constellation_preview_age_N.png   1920x1080 check renders: sky + slide + title UI at GUI 3
  dev/shrine-viewer/out/constellation_sheet.png           3x3 contact sheet of the previews

Usage: python dev/gen_constellation.py [slides|sky|sheet|N ...]   (default: slides sky sheet)
Pillow + numpy only. The slideshow timing (duration 12, fadespeed 0.3) lives in
config/fancymenu/slideshows/firmament_constellation/properties.txt, not here.
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = Path(__file__).resolve().parents[1]
ASSETS = REPO / "config/fancymenu/assets/firmament"
SLIDES = REPO / "config/fancymenu/slideshows/firmament_constellation/images"
PREVIEWS = REPO / "dev/shrine-viewer/out"
DATA_PATH = REPO / "dev/shrine-viewer/shrine_data.json"
if not DATA_PATH.exists():
    sys.exit(f"{DATA_PATH} missing: run python dev/shrine-viewer/render_shrine.py "
             "--data-root mod/firmages-core/src/main/resources/data/firmages first")
DATA = json.load(open(DATA_PATH, encoding="utf-8"))
PACK_VERSION = re.search(r'^version = "([^"]+)"', (REPO / "pack.toml").read_text(encoding="utf-8"), re.M)[1]
RINGS = {r["n"]: r for r in DATA["rings"]}
CUMUL = {c["n"]: c["blocks"] for c in DATA["cumulative"]}

W, H = 2048, 1152                # slide size (stretched to the screen by the slideshow element, 16:9)
SKY_W, SKY_H = 2304, 1296
PW, PH = 1920, 1080              # preview / contact-sheet cell size
SAFE = 72                        # px the element parallax can shift the slide (0.6 intensity -> 58 px at 1080p)
FONT_SERIF = "C:/Windows/Fonts/constanb.ttf"
FONT_UI = "C:/Windows/Fonts/calibrib.ttf"
GOLD = (244, 214, 140)
GHOST = (224, 232, 255)          # cool white
CAPTIONS = {0: "STONE AGE", 1: "BRONZE AGE", 2: "IRON AGE", 3: "ARCANE AGE", 4: "INDUSTRIAL AGE",
            5: "ELECTRIC AGE", 6: "INFORMATION AGE", 7: "SPACE AGE", 8: "QUANTUM AGE"}


# ----------------------------------------------------------------------------- helpers
def hex_rgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def accent_of(n):
    """Age accent = the ring's beam colour. The two near-white beams (Industrial #F0F0F0, Space #E0E8FF) would
    vanish against the cool-white ghost, so they get a slight tint: Industrial warm steel, Space ice blue."""
    c = hex_rgb(RINGS[n]["beam_color"])
    lum = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
    if lum > 0.85:
        tint = (255, 214, 170) if n == 4 else (150, 190, 255)
        c = tuple(int(a * 0.55 + b * 0.45) for a, b in zip(c, tint))
    return c


def vnoise(w, h, seed, base=4, octaves=6, persist=0.55):
    rng = np.random.default_rng(seed)
    acc = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        g = rng.random((max(2, base * 2 ** o), max(2, base * 2 ** o)), dtype=np.float32)
        acc += np.asarray(Image.fromarray(g, mode="F").resize((w, h), Image.BICUBIC)) * amp
        tot += amp
        amp *= persist
    acc /= tot
    return (acc - acc.min()) / (acc.max() - acc.min() + 1e-6)


def starfield(w, h, n, seed, rmin, rmax, bright, glint=0.5):
    rng = np.random.default_rng(seed)
    im = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(im)
    for _ in range(n):
        x, y = rng.random() * w, rng.random() * h
        r = rng.uniform(rmin, rmax)
        b = int(255 * bright * rng.uniform(0.35, 1.0))
        d.ellipse((x - r, y - r, x + r, y + r), fill=b)
        if r > 1.7 and rng.random() < glint:
            d.line((x - r * 3.5, y, x + r * 3.5, y), fill=b // 2)
            d.line((x, y - r * 3.5, x, y + r * 3.5), fill=b // 2)
    return np.asarray(im.filter(ImageFilter.GaussianBlur(0.7)), np.float32) / 255


def blend_layer(img, layer, times=1):
    for _ in range(times):
        img.alpha_composite(layer)


def scaled(layer, k):
    """Multiply a RGBA layer's alpha by k."""
    a = np.asarray(layer, np.float32)
    a[..., 3] *= k
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


# ----------------------------------------------------------------------------- chart geometry
def columns(n):
    cols = {}
    for b in CUMUL[n]:
        x, y, z = b[0], b[1], b[2]
        cols[(x, z)] = max(cols.get((x, z), -9), y)
    return cols


def iso(x, y, z):
    return np.array([(x - z) * 0.866, (x + z) * 0.5 - y * 1.05], np.float32)


def chart_nodes(n):
    """World-space nodes of the shrine after Age n: (iso xy, height). Raised columns (top y >= 1), the heart,
    footprint corners and edge midpoints. Returns proj [k,2], heights [k], corner quad [4,2], footprint radius."""
    cols = columns(n)
    r = max(abs(k[0]) for k in cols)
    pts = []
    for (x, z), y in cols.items():
        if y >= 1 or (x, z) == (0, 0):
            pts.append((x, y + 1, z, y))
    for x, z in ((r, r), (r, -r), (-r, r), (-r, -r), (r, 0), (-r, 0), (0, r), (0, -r)):
        pts.append((x, 1, z, 0))
    proj = np.array([iso(x, y, z) for x, y, z, _ in pts])
    hts = np.array([p[3] for p in pts], np.float32)
    corners = np.array([iso(x, 1, z) for x, z in ((-r, -r), (r, -r), (r, r), (-r, r))])
    return proj, hts, corners, r


def thin(proj, hts, spacing):
    """Keep the tallest nodes first, drop any closer than `spacing` px to a kept one."""
    order = np.argsort(-hts, kind="stable")
    keep = []
    for i in order:
        if all(np.hypot(*(proj[i] - proj[j])) > spacing for j in keep):
            keep.append(i)
    return proj[keep], hts[keep]


def mst_edges(p, extra_max):
    n = len(p)
    if n < 2:
        return []
    d = np.hypot(p[:, None, 0] - p[None, :, 0], p[:, None, 1] - p[None, :, 1])
    inn = [0]
    edges = []
    while len(inn) < n:
        best = None
        for i in inn:
            for j in range(n):
                if j in inn:
                    continue
                if best is None or d[i, j] < best[0]:
                    best = (d[i, j], i, j)
        edges.append((best[1], best[2]))
        inn.append(best[2])
    # a few extra short links so it reads as a star chart, not a tree
    for i in range(n):
        if n > 2:
            j = int(np.argsort(d[i])[2])
            if d[i, j] < extra_max and (i, j) not in edges and (j, i) not in edges:
                edges.append((i, j))
    return edges


def layout(n, box, fill):
    """Pixel positions for the chart of Age n inside box, filling `fill` of the box width (or height).
    The previous Age is laid out with the same world scale so the ghost sits inside the new footprint."""
    proj, hts, corners, r = chart_nodes(n)
    x0, y0, x1, y1 = box
    mn, mx = proj.min(axis=0), proj.max(axis=0)
    ext = mx - mn
    s = min((x1 - x0) * fill / ext[0], (y1 - y0) * fill / ext[1])
    centre = np.array([(x0 + x1) / 2, (y0 + y1) / 2])
    world_centre = (mn + mx) / 2

    def to_px(p):
        return (p - world_centre) * s + centre

    out = {"s": s, "to_px": to_px}
    spacing = max(36.0, 0.95 * s)
    p, h = thin(to_px(proj), hts, spacing)
    out["cur"] = (p, h, to_px(corners), mst_edges(p, 2.4 * s + 40))
    if n > 0:
        pp, hp, cp, _ = chart_nodes(n - 1)
        p2, h2 = thin(to_px(pp), hp, spacing)
        out["prev"] = (p2, h2, to_px(cp), mst_edges(p2, 2.4 * s + 40))
    else:
        heart = to_px(np.array([iso(0, 1, 0)]))
        out["prev"] = (heart, np.array([0.0], np.float32), None, [])
    return out


# ----------------------------------------------------------------------------- drawing
def draw_chart(size, p, hts, corners, edges, line_col, node_col, glow_col, s, ghost=False):
    """RGBA layer with the chart: edges, footprint diamond, node cores, accent glow, soft halo."""
    w, h = size
    lines = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lines)
    lw = 2 if not ghost else 2
    for i, j in edges:
        d.line((tuple(p[i]), tuple(p[j])), fill=line_col + (255,), width=lw)
    if corners is not None:
        d.polygon([tuple(c) for c in corners], outline=line_col + (90 if not ghost else 60,), width=2)
    nodes = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dn, dg = ImageDraw.Draw(nodes), ImageDraw.Draw(glow)
    base = 1.0 if not ghost else 0.85
    for (x, y), hh in zip(p, hts):
        r = base * (2.0 + 0.6 * hh) * (0.85 + 0.15 * min(s / 30, 1.5))
        dn.ellipse((x - r, y - r, x + r, y + r), fill=node_col + (255,))
        rg = r * (2.3 + 0.25 * hh)
        dg.ellipse((x - rg, y - rg, x + rg, y + rg), fill=glow_col + (255,))
        if hh >= 3:
            k = r * 5.0
            dn.line((x - k, y, x + k, y), fill=glow_col + (230,), width=2)
            dn.line((x, y - k, x, y + k), fill=glow_col + (230,), width=2)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if ghost:
        layer.alpha_composite(scaled(lines.filter(ImageFilter.GaussianBlur(4)), 0.45))
        layer.alpha_composite(lines.filter(ImageFilter.GaussianBlur(0.6)))
        layer.alpha_composite(scaled(glow.filter(ImageFilter.GaussianBlur(6)), 0.5))
        layer.alpha_composite(nodes.filter(ImageFilter.GaussianBlur(0.5)))
        return layer
    # halo (wide, faint), gold edge bloom, accent node glow (wide, mid, tight), crisp lines and cores
    merged = Image.alpha_composite(lines, glow)
    layer.alpha_composite(scaled(merged.filter(ImageFilter.GaussianBlur(26)), 1.0))
    layer.alpha_composite(scaled(lines.filter(ImageFilter.GaussianBlur(6)), 0.8))
    layer.alpha_composite(scaled(glow.filter(ImageFilter.GaussianBlur(18)), 0.75))
    layer.alpha_composite(scaled(glow.filter(ImageFilter.GaussianBlur(8)), 0.7))
    layer.alpha_composite(scaled(glow.filter(ImageFilter.GaussianBlur(3)), 0.9))
    layer.alpha_composite(lines.filter(ImageFilter.GaussianBlur(0.7)))
    layer.alpha_composite(nodes.filter(ImageFilter.GaussianBlur(0.6)))
    return layer


def small_caps(d, text, font_big, font_small, tracking):
    """Width of a tracked small-caps string and its glyph list [(char, font, advance)]."""
    out = []
    first = True
    for ch in text:
        if ch == " ":
            out.append((ch, font_small, d.textlength(" ", font=font_big) + tracking))
            first = True
            continue
        f = font_big if first else font_small
        out.append((ch, f, d.textlength(ch, font=f) + tracking))
        first = False
    return sum(a for _, _, a in out), out


def caption(img, text, color, right, baseline, size=38, tracking=11, glow=12):
    """Small-caps Age name, right-aligned at (right, baseline), gold with a soft glow."""
    big = ImageFont.truetype(FONT_SERIF, size)
    small = ImageFont.truetype(FONT_SERIF, int(size * 0.78))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    width, glyphs = small_caps(d, text, big, small, tracking)
    x = right - width + tracking
    for ch, f, adv in glyphs:
        if ch != " ":
            d.text((x, baseline), ch, font=f, fill=color + (255,), anchor="ls")
        x += adv
    halo = layer.filter(ImageFilter.GaussianBlur(glow))
    img.alpha_composite(scaled(halo, 0.9))
    img.alpha_composite(scaled(halo, 0.9))
    img.alpha_composite(layer)
    return img


def slide(n):
    accent = accent_of(n)
    box = (W * 0.455, 150, W - 150, H - 215)
    fill = 0.50 + 0.50 * (n / 8) ** 0.75
    lo = layout(n, box, fill)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # previous Age, cool white, drifted down-left (toward the heart of the old shrine's own chart)
    p, h, c, e = lo["prev"]
    drift = np.array([-1.3 * lo["s"] - 18, 0.75 * lo["s"] + 10])
    ghost = draw_chart((W, H), p + drift, h, None if c is None else c + drift, e, GHOST, GHOST, GHOST, lo["s"], ghost=True)
    img.alpha_composite(scaled(ghost, 0.45))
    # current Age
    p, h, c, e = lo["cur"]
    img.alpha_composite(draw_chart((W, H), p, h, c, e, GOLD, (255, 255, 255), accent, lo["s"]))
    img = caption(img, CAPTIONS[n], GOLD, W - 112, H - 92)
    SLIDES.mkdir(parents=True, exist_ok=True)
    out = SLIDES / f"constellation_age_{n}.png"
    img.save(out, optimize=True)
    print("wrote", out, f"nodes={len(p)} edges={len(e)} s={lo['s']:.1f}")
    return img


# ----------------------------------------------------------------------------- sky
def sky():
    w, h = SKY_W, SKY_H
    t = np.linspace(0, 1, h)[:, None, None]
    top, mid, bottom = np.array([4, 6, 22]) / 255, np.array([14, 10, 40]) / 255, np.array([44, 18, 70]) / 255
    c = np.where(t < 0.55, top + (mid - top) * (t / 0.55), mid + (bottom - mid) * ((t - 0.55) / 0.45)) + np.zeros((h, w, 3), np.float32)
    n1 = vnoise(w, h, 31, base=3, octaves=5, persist=0.55)
    n2 = vnoise(w, h, 34, base=5, octaves=4, persist=0.5)
    neb = np.clip(n1 - 0.45, 0, 1) * 0.42
    c = c + neb[..., None] * (np.array([0x3A, 0x2A, 0x7A]) / 255)[None, None]
    c = c + (np.clip(n2 - 0.6, 0, 1) * 0.35)[..., None] * (np.array([0x6A, 0x2C, 0x78]) / 255)[None, None]
    far = starfield(w, h, 2300, 32, 0.5, 1.1, 0.5, 0)
    mid_s = starfield(w, h, 430, 33, 0.9, 1.6, 0.7, 0.2)
    near = starfield(w, h, 60, 35, 1.6, 2.3, 0.9, 0.7)
    c = c + (far + mid_s + near)[..., None] * np.array([0.9, 0.92, 1.0])[None, None]
    ys, xs = np.mgrid[0:h, 0:w]
    dd = np.sqrt(((xs - w / 2) / (w * 0.72)) ** 2 + ((ys - h / 2) / (h * 0.78)) ** 2)
    m = np.clip(1 - 0.55 * np.clip(dd - 0.55, 0, 1) ** 1.4 * 2.2, 0.08, 1)
    c = c * m[..., None]
    img = Image.fromarray(np.clip(c * 255, 0, 255).astype(np.uint8), "RGB")
    out = ASSETS / "sky_2304x1296.png"
    img.save(out, optimize=True)
    print("wrote", out)
    return img


# ----------------------------------------------------------------------------- preview + contact sheet
def ui_mock(img):
    """The real title UI at GUI scale 3 (positions from title_screen_layout.txt): wordmark, four buttons, version."""
    S = 3
    wm = Image.open(ASSETS / "wordmark.png").convert("RGBA").resize((240 * S, 60 * S), Image.LANCZOS)
    img.alpha_composite(wm, (28 * S, 22 * S))
    face = Image.open(ASSETS / "btn_normal.png").convert("RGBA").resize((200 * S, 20 * S), Image.LANCZOS)
    font = ImageFont.truetype(FONT_UI, 11 * S)
    for y, label in ((110, "Singleplayer"), (136, "Multiplayer"), (172, "Options..."), (198, "Quit Game")):
        img.alpha_composite(face, (28 * S, y * S))
        d = ImageDraw.Draw(img)
        cx, cy = (28 + 100) * S, (y + 10) * S
        d.text((cx + 2, cy + 2), label, font=font, fill=(20, 15, 40, 220), anchor="mm")
        d.text((cx, cy), label, font=font, fill=(236, 228, 255, 255), anchor="mm")
    d = ImageDraw.Draw(img)
    vf = ImageFont.truetype(FONT_UI, 9 * S)
    vx, vy = 28 * S, PH - 20 * S
    d.text((vx + 2, vy + 2), f"Firmament Ages {PACK_VERSION}", font=vf, fill=(0, 0, 0, 200), anchor="ls")
    d.text((vx, vy), f"Firmament Ages {PACK_VERSION}", font=vf, fill=(169, 156, 200, 255), anchor="ls")
    return img


def preview(n, sky_img):
    """What the player sees: background image (scaled to the screen, keep aspect) + stretched slide + UI."""
    bg = sky_img.resize((PW, PH), Image.LANCZOS).convert("RGBA")
    sl = Image.open(SLIDES / f"constellation_age_{n}.png").convert("RGBA").resize((PW, PH), Image.LANCZOS)
    bg.alpha_composite(sl)
    bg = ui_mock(bg)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = PREVIEWS / f"constellation_preview_age_{n}.png"
    bg.convert("RGB").save(out, optimize=True)
    print("wrote", out)
    return bg


def sheet():
    sky_img = Image.open(ASSETS / "sky_2304x1296.png")
    tw, th, bar = 960, 540, 44
    out = Image.new("RGB", (tw * 3, (th + bar) * 3), (14, 12, 24))
    d = ImageDraw.Draw(out)
    f1 = ImageFont.truetype(FONT_UI, 22)
    for n in range(9):
        im = preview(n, sky_img).resize((tw, th), Image.LANCZOS)
        x, y = (n % 3) * tw, (n // 3) * (th + bar)
        out.paste(im, (x, y))
        d.text((x + 14, y + th + 10), f"constellation_age_{n}.png  -  {CAPTIONS[n]}  -  accent {RINGS[n]['beam_color']}",
               font=f1, fill=(200, 194, 220))
        d.rectangle((x, y, x + tw - 1, y + th - 1), outline=(60, 50, 90), width=1)
    out.save(PREVIEWS / "constellation_sheet.png", optimize=True)
    print("wrote", PREVIEWS / "constellation_sheet.png")


if __name__ == "__main__":
    which = sys.argv[1:] or ["slides", "sky", "sheet"]
    for k in which:
        if k == "slides":
            for n in range(9):
                slide(n)
        elif k.isdigit():
            slide(int(k))
        elif k == "sky":
            sky()
        elif k == "sheet":
            sheet()
