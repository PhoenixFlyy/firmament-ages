"""CurseForge images from the shrine render: logo.png (512x512), avatar-400.png (400x400, CF's avatar size)
and banner.png (1920x1080).

Input: dev/shrine-viewer/out/shrine_cutout_age_8.png, the final shrine in its consecrated look (sky-marble with the
Age accents) on a transparent background; python dev/shrine-viewer/render_shrine.py writes it. Fonts: Windows
Georgia Bold / Constantia.
Usage: python dev/cf/make_logo.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "dev" / "shrine-viewer" / "out" / "shrine_cutout_age_8.png"
OUT = ROOT / "dev" / "cf"
FONT_TITLE = "C:/Windows/Fonts/georgiab.ttf"
FONT_SUB = "C:/Windows/Fonts/constan.ttf"
SKY_TOP, SKY_BOTTOM = (14, 10, 34), (52, 20, 70)       # night sky into the shrine's Quantum sky tint #301040
GOLD = (244, 214, 140)


def shrine_cutout():
    """The transparent render trimmed to the shrine; its alpha is the mask (no flood fill: the marble is as pale as
    any light background would be)."""
    im = Image.open(SRC).convert("RGBA")
    if im.getextrema()[3][0] == 255:
        raise SystemExit(f"{SRC} has no transparent background; re-run dev/shrine-viewer/render_shrine.py")
    return im.crop(im.getbbox())


def sky(size, seed=7, stars=140):
    w, h = size
    bg = Image.new("RGB", size)
    px = bg.load()
    for y in range(h):
        t = y / (h - 1)
        row = tuple(round(a + (b - a) * t) for a, b in zip(SKY_TOP, SKY_BOTTOM))
        for x in range(w):
            px[x, y] = row
    glow = Image.new("L", size, 0)
    gd = ImageDraw.Draw(glow)
    cx, cy, r = w * 0.5, h * 0.55, min(w, h) * 0.42
    gd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=110)
    glow = glow.filter(ImageFilter.GaussianBlur(min(w, h) * 0.12))
    bg = Image.composite(Image.new("RGB", size, (150, 100, 200)), bg, glow.point(lambda v: v * 0.45))
    rnd = random.Random(seed)
    d = ImageDraw.Draw(bg)
    for _ in range(stars):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h * 0.75)
        s = rnd.choice([0.6, 0.8, 1.0, 1.0, 1.4, 2.0]) * math.sqrt(min(w, h) / 512)
        c = rnd.randint(170, 255)
        d.ellipse((x - s, y - s, x + s, y + s), fill=(c, c, min(255, c + 20)))
    return bg.convert("RGBA")


def text_with_glow(canvas, xy, text, font, fill, anchor, glow_radius):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=(255, 200, 255, 200), anchor=anchor)
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(glow_radius)))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).text((xy[0], xy[1] + max(2, glow_radius // 3)), text, font=font, fill=(0, 0, 0, 170),
                                anchor=anchor)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(1.5)))
    ImageDraw.Draw(canvas).text(xy, text, font=font, fill=fill, anchor=anchor)


def place(canvas, shrine, box):
    """Fit the shrine into box=(x0, y0, x1, y1), centred."""
    x0, y0, x1, y1 = box
    scale = min((x1 - x0) / shrine.width, (y1 - y0) / shrine.height)
    s = shrine.resize((round(shrine.width * scale), round(shrine.height * scale)), Image.LANCZOS)
    pos = (x0 + (x1 - x0 - s.width) // 2, y0 + (y1 - y0 - s.height) // 2)
    halo = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    halo.paste((255, 220, 255, 90), pos, s.getchannel("A"))
    canvas.alpha_composite(halo.filter(ImageFilter.GaussianBlur(max(6, s.width // 40))))
    canvas.alpha_composite(s, pos)


def square(size, shrine):
    c = sky((size, size))
    place(c, shrine, (round(size * 0.07), round(size * 0.05), round(size * 0.93), round(size * 0.70)))
    f1 = ImageFont.truetype(FONT_TITLE, round(size * 0.135))
    f2 = ImageFont.truetype(FONT_TITLE, round(size * 0.105))
    text_with_glow(c, (size / 2, size * 0.795), "Firmament", f1, GOLD, "mm", round(size * 0.02))
    text_with_glow(c, (size / 2, size * 0.915), "AGES", f2, (236, 228, 255), "mm", round(size * 0.02))
    return c.convert("RGB")


def banner(shrine):
    w, h = 1920, 1080
    c = sky((w, h), seed=11, stars=320)
    place(c, shrine, (70, 120, 1000, 1000))
    f1 = ImageFont.truetype(FONT_TITLE, 124)
    f2 = ImageFont.truetype(FONT_TITLE, 96)
    f3 = ImageFont.truetype(FONT_SUB, 44)
    cx = 1480
    text_with_glow(c, (cx, 430), "Firmament", f1, GOLD, "mm", 16)
    text_with_glow(c, (cx, 555), "AGES", f2, (236, 228, 255), "mm", 16)
    d = ImageDraw.Draw(c)
    d.line((cx - 230, 640, cx + 230, 640), fill=(244, 214, 140, 200), width=2)
    d.text((cx, 700), "Ten Ages. One shrine.", font=f3, fill=(222, 210, 240), anchor="mm")
    d.text((cx, 760), "Minecraft 1.21.1 · NeoForge", font=f3, fill=(190, 176, 214), anchor="mm")
    return c.convert("RGB")


def main():
    shrine = shrine_cutout()
    square(512, shrine).save(OUT / "logo.png")
    square(400, shrine).save(OUT / "avatar-400.png")
    banner(shrine).save(OUT / "banner.png")
    print("wrote logo.png, avatar-400.png, banner.png in", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
