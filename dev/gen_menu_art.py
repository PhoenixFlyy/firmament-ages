#!/usr/bin/env python
"""Menu and loading-screen art for FancyMenu / Drippy Loading Screen.

Writes (all shipped with the pack, referenced from config/fancymenu/customization/*.txt and
config/drippyloadingscreen/options.txt):
  config/fancymenu/assets/firmament/loading_bg.png     1920x1080 night-blue -> violet gradient with stars (loading)
  config/fancymenu/assets/firmament/wordmark.png       1200x300 transparent "Firmament AGES" (title + loading logo)
  config/fancymenu/assets/firmament/btn_normal.png, btn_hover.png   800x80 button faces (dark glass, gold border)
  config/fancymenu/assets/firmament/bar_bg.png, bar_fill.png        400x12 early-window progress bar
  config/fancymenu/assets/firmament/icon16.png, icon32.png         window icons from dev/cf/logo.png

The title background is a GLSL shader: dev/title-shader/ (build_shader.py, README.md).

Inputs: dev/cf/make_logo.py (sky, fonts, glow text), dev/cf/logo.png. Fonts: Windows Georgia Bold / Constantia.
Usage: python dev/gen_menu_art.py
       python dev/gen_menu_art.py --check   only verify that every output exists with the right size
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "dev" / "cf"))
from make_logo import FONT_TITLE, GOLD, sky, text_with_glow  # noqa: E402

FM = ROOT / "config" / "fancymenu"
ASSETS = FM / "assets" / "firmament"
W, H = 1920, 1080
NIGHT_TOP, NIGHT_BOTTOM = (8, 10, 32), (46, 18, 66)   # deep night-blue into violet (banner palette)

EXPECTED = {
    ASSETS / "loading_bg.png": (W, H), ASSETS / "wordmark.png": (1200, 300),
    ASSETS / "btn_normal.png": (800, 80), ASSETS / "btn_hover.png": (800, 80),
    ASSETS / "bar_bg.png": (400, 12), ASSETS / "bar_fill.png": (400, 12),
    ASSETS / "icon16.png": (16, 16), ASSETS / "icon32.png": (32, 32),
}


def gradient(size, top, bottom) -> Image.Image:
    w, h = size
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / (h - 1)
        px[0, y] = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
    return strip.resize((w, h), Image.NEAREST)


def vignette(canvas: Image.Image, strength: float = 0.72) -> None:
    """Darken the edges so GUI text stays readable on any slide."""
    w, h = canvas.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((-w * 0.15, -h * 0.25, w * 1.15, h * 1.25), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(w * 0.16))
    dark = Image.new("RGBA", (w, h), (4, 3, 14, round(255 * strength)))
    dark.putalpha(mask.point(lambda v: round((255 - v) * strength)))
    canvas.alpha_composite(dark)


def loading_bg() -> None:
    c = gradient((W, H), NIGHT_TOP, NIGHT_BOTTOM).convert("RGBA")
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((W * 0.25, H * 0.30, W * 0.75, H * 0.95), fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(H * 0.18))
    c = Image.composite(Image.new("RGBA", (W, H), (120, 70, 170, 255)), c, glow.point(lambda v: v * 0.5))
    stars = sky((W, H), seed=23, stars=220)
    # keep only the stars of make_logo.sky: paste its bright pixels over the gradient
    lum = stars.convert("L").point(lambda v: 255 if v > 120 else 0)
    c.paste(stars, (0, 0), lum)
    vignette(c, 0.5)
    c.convert("RGB").save(ASSETS / "loading_bg.png", optimize=True)
    print("wrote", (ASSETS / "loading_bg.png").relative_to(ROOT))


def wordmark() -> None:
    c = Image.new("RGBA", (1200, 300), (0, 0, 0, 0))
    f1 = ImageFont.truetype(FONT_TITLE, 150)
    f2 = ImageFont.truetype(FONT_TITLE, 92)
    text_with_glow(c, (600, 112), "Firmament", f1, GOLD, "mm", 14)
    text_with_glow(c, (600, 238), "AGES", f2, (236, 228, 255), "mm", 12)
    c.save(ASSETS / "wordmark.png", optimize=True)
    print("wrote", (ASSETS / "wordmark.png").relative_to(ROOT))


def button(fill, border, name: str) -> None:
    w, h = 800, 80
    c = Image.new("RGBA", (w, h), fill)
    d = ImageDraw.Draw(c)
    d.rectangle((0, 0, w - 1, h - 1), outline=border, width=6)
    d.rectangle((10, 10, w - 11, h - 11), outline=border[:3] + (70,), width=2)
    c.save(ASSETS / name, optimize=True)


def bars() -> None:
    w, h = 400, 12
    bg = Image.new("RGBA", (w, h), (12, 9, 30, 230))
    ImageDraw.Draw(bg).rectangle((0, 0, w - 1, h - 1), outline=GOLD + (200,), width=1)
    bg.save(ASSETS / "bar_bg.png", optimize=True)
    fill = gradient((w, h), (255, 224, 150), (201, 162, 39)).convert("RGBA")
    fill.save(ASSETS / "bar_fill.png", optimize=True)


def icons() -> None:
    logo = Image.open(ROOT / "dev" / "cf" / "logo.png").convert("RGBA")
    for n in (16, 32):
        logo.resize((n, n), Image.LANCZOS).save(ASSETS / f"icon{n}.png", optimize=True)


def check() -> int:
    bad = 0
    for p, size in EXPECTED.items():
        if not p.exists():
            print("MISSING", p.relative_to(ROOT))
            bad += 1
            continue
        got = Image.open(p).size
        if got != size:
            print("SIZE", p.relative_to(ROOT), got, "expected", size)
            bad += 1
    print(f"{len(EXPECTED) - bad}/{len(EXPECTED)} menu art files ok")
    return 1 if bad else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if args.check:
        return check()
    ASSETS.mkdir(parents=True, exist_ok=True)
    loading_bg()
    wordmark()
    button((12, 9, 30, 190), (244, 214, 140, 230), "btn_normal.png")
    button((44, 28, 80, 215), (255, 236, 180, 255), "btn_hover.png")
    bars()
    icons()
    return check()


if __name__ == "__main__":
    raise SystemExit(main())
