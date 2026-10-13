"""CurseForge images for the firmages-core mod project, distinct from the modpack's shrine render:
the Shrine Heart block (its own textures: lit top, side) as an isometric cube on a sky-marble floor
(consecrated_stone tiled), with the mod's short name. Writes dev/cf/firmages-core-logo.png (512x512) and
dev/cf/firmages-core-avatar-400.png (400x400, CurseForge's avatar size).

Usage: python dev/cf/make_mod_logo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "assets" / "firmages" / "textures" / "block"
OUT = ROOT / "dev" / "cf"
FONT_TITLE = "C:/Windows/Fonts/georgiab.ttf"
GOLD = (244, 214, 140)
SKY_TOP, SKY_BOTTOM = (14, 10, 34), (52, 20, 70)


def frame0(name: str) -> Image.Image:
    im = Image.open(TEX / name).convert("RGBA")
    return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im


def shade(im: Image.Image, factor: float) -> Image.Image:
    r, g, b, a = im.split()
    rgb = Image.merge("RGB", (r, g, b)).point(lambda v: round(v * factor))
    rgb.putalpha(a)
    return rgb


def face(img: Image.Image, size: tuple[int, int], ul, ur, ll) -> Image.Image:
    """Draw the square texture `img` as the parallelogram whose top-left, top-right and bottom-left corners are
    the canvas points ul, ur, ll (PIL AFFINE maps canvas pixels back to texture pixels, so the forward map is
    inverted here)."""
    import numpy as np
    s = img.width
    fwd = np.array([[(ur[0] - ul[0]) / s, (ll[0] - ul[0]) / s], [(ur[1] - ul[1]) / s, (ll[1] - ul[1]) / s]])
    inv = np.linalg.inv(fwd)
    t = -inv @ np.array([ul[0], ul[1]])
    data = (inv[0, 0], inv[0, 1], t[0], inv[1, 0], inv[1, 1], t[1])
    return img.transform(size, Image.AFFINE, data, Image.NEAREST)


def iso_cube(top: Image.Image, side: Image.Image, edge: int) -> Image.Image:
    """Isometric cube of edge length `edge` px (face textures upscaled nearest): top, left and right faces."""
    s = edge
    h = round(s * 0.5)             # half height of the top rhombus
    w = round(s * 0.866)           # half width
    size = (2 * w + 2, 2 * h + s + 2)
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    big_top = top.resize((s, s), Image.NEAREST)
    big_side = side.resize((s, s), Image.NEAREST)
    cx = w + 1
    n, e, so, we = (cx, 0), (cx + w, h), (cx, 2 * h), (cx - w, h)
    canvas.alpha_composite(face(big_top, size, we, n, so))                            # top: W -> N across, W -> S down
    canvas.alpha_composite(face(shade(big_side, 0.78), size, we, so, (we[0], we[1] + s)))   # left: W -> S, down s
    canvas.alpha_composite(face(shade(big_side, 0.6), size, so, e, (so[0], so[1] + s)))     # right: S -> E, down s
    return canvas


def background(size: int) -> Image.Image:
    bg = Image.new("RGBA", (size, size))
    px = bg.load()
    for y in range(size):
        t = y / (size - 1)
        row = tuple(round(a + (b - a) * t) for a, b in zip(SKY_TOP, SKY_BOTTOM)) + (255,)
        for x in range(size):
            px[x, y] = row
    # sky-marble floor: consecrated_stone tiled, pressed flat into perspective by a vertical squash
    tile = Image.open(TEX / "consecrated_stone.png").convert("RGBA")
    cell = size // 4
    floor = Image.new("RGBA", (size * 2, size * 2))
    for ix in range(8):
        for iy in range(8):
            floor.paste(tile.resize((cell, cell), Image.NEAREST), (ix * cell, iy * cell))
    floor = floor.rotate(45, resample=Image.NEAREST, expand=False)
    floor = floor.crop((size // 2, size // 2, size // 2 + size, size // 2 + size))
    floor = floor.resize((size, size // 2), Image.LANCZOS)
    mask = Image.new("L", (size, size // 2), 0)
    ImageDraw.Draw(mask).ellipse((-size * 0.1, -size * 0.05, size * 1.1, size * 0.55), fill=170)
    mask = mask.filter(ImageFilter.GaussianBlur(size * 0.05))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    floor.putalpha(mask)
    layer.alpha_composite(floor, (0, round(size * 0.46)))
    bg.alpha_composite(layer)
    return bg


def text_with_glow(canvas, xy, text, font, fill, glow_radius):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=(255, 200, 255, 200), anchor="mm")
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(glow_radius)))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).text((xy[0], xy[1] + 3), text, font=font, fill=(0, 0, 0, 170), anchor="mm")
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(1.5)))
    ImageDraw.Draw(canvas).text(xy, text, font=font, fill=fill, anchor="mm")


def square(size: int) -> Image.Image:
    c = background(size)
    cube = iso_cube(frame0("shrine_heart_top_lit.png"), frame0("shrine_heart_side.png"), round(size * 0.30))
    pos = ((size - cube.width) // 2, round(size * 0.14))
    halo = Image.new("RGBA", c.size, (0, 0, 0, 0))
    halo.paste((255, 170, 90, 140), pos, cube.getchannel("A"))
    c.alpha_composite(halo.filter(ImageFilter.GaussianBlur(size * 0.05)))
    c.alpha_composite(cube, pos)
    f1 = ImageFont.truetype(FONT_TITLE, round(size * 0.105))
    f2 = ImageFont.truetype(FONT_TITLE, round(size * 0.165))
    text_with_glow(c, (size / 2, size * 0.745), "Firmament Ages", f1, GOLD, round(size * 0.02))
    text_with_glow(c, (size / 2, size * 0.885), "CORE", f2, (236, 228, 255), round(size * 0.02))
    return c.convert("RGB")


def main():
    square(512).save(OUT / "firmages-core-logo.png")
    square(400).save(OUT / "firmages-core-avatar-400.png")
    print("wrote firmages-core-logo.png (512), firmages-core-avatar-400.png (400) in", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
