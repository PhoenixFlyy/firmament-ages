#!/usr/bin/env python3
"""Firmament Ages - draw the grand arena of The Origin (dev/origin_arena_plan.py) as an isometric picture.

Writes dev/quest-preview/origin_arena.png: the plan from the south-west (x mirrored, so the ascent stands in front of
the arena), with the block textures of firmages-core and the vanilla jar (dev/qart_textures.py; flat colours without the
jars). Hidden cubes (covered on top, east and south) are left out.

Usage
  python dev/gen_arena_preview.py [--scale N]   (default 6 px per block edge)
"""
import argparse
import os
import sys

from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import origin_arena_plan as P  # noqa: E402
import qart_iso as ISO  # noqa: E402
import qart_textures as T  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "assets", "firmages", "textures", "block")
OUT = os.path.join(REPO, "dev", "quest-preview", "origin_arena.png")

VANILLA = {"rock_0": "minecraft:deepslate", "rock_1": "minecraft:tuff", "rock_2": "minecraft:blackstone",
           "rock_3": "minecraft:stone", "rock_4": "minecraft:mossy_cobblestone", "dais": "minecraft:crying_obsidian",
           "lantern": "minecraft:pearlescent_froglight"}
FALLBACK = {"rock_0": (70, 70, 74), "rock_1": (108, 110, 102), "rock_2": (40, 36, 44), "rock_3": (125, 125, 125),
            "rock_4": (100, 118, 84), "dais": (40, 10, 70), "lantern": (232, 210, 220)}
# OriginArena.ARENA_PALETTE: the unbreakable arena stone of a role per accent group (accent % 3: dark, moss, purple)
PALETTE = {"stone": ("polished_deepslate", "mossy_stone_bricks", "deepslate_tiles"),
           "brick": ("deepslate_bricks", "stone_bricks", "polished_blackstone_bricks"),
           "trim": ("purpur_block", "moss_block", "amethyst_block"),
           "pillar": ("purpur_pillar",) * 3, "metal": ("chiseled_deepslate", "mossy_cobblestone", "chiseled_deepslate"),
           "lamp": ("pearlescent_froglight",) * 3, "glass": ("tinted_glass",) * 3,
           "scaffold": ("polished_deepslate", "mossy_stone_bricks", "deepslate_tiles")}
STONE_RGB = {"polished_deepslate": (72, 72, 73), "mossy_stone_bricks": (115, 121, 105), "deepslate_tiles": (54, 54, 55),
             "deepslate_bricks": (70, 70, 71), "stone_bricks": (122, 121, 122), "polished_blackstone_bricks": (48, 42, 49),
             "purpur_block": (170, 125, 170), "moss_block": (89, 110, 45), "amethyst_block": (133, 97, 191),
             "purpur_pillar": (171, 129, 171), "chiseled_deepslate": (54, 54, 54), "mossy_cobblestone": (110, 118, 94),
             "pearlescent_froglight": (232, 210, 220), "tinted_glass": (44, 38, 46)}
_faces = {}


def own(name):
    p = os.path.join(TEX, name + ".png")
    if not os.path.isfile(p):
        return None
    im = Image.open(p).convert("RGBA")
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    return im


def faces(key):
    if key in _faces:
        return _faces[key]
    f = None
    if key in VANILLA:
        f = T.block_faces(VANILLA[key]) if T.available() else None
        if f is None:
            t = ISO.solid_tex(FALLBACK[key])
            f = (t, t)
    elif key == "arena_altar":
        top, side = own("arena_altar_top") or own("origin_altar_top"), own("arena_altar_side") or own("origin_altar_side")
        f = (top, side)
    else:
        role, accent = key.rsplit("_", 1)
        name = PALETTE[role][int(accent) % 3]
        f = T.block_faces("minecraft:" + name) if T.available() else None
        if f is None:
            t = ISO.solid_tex(STONE_RGB[name])
            f = (t, t)
    _faces[key] = f
    return f


_hazed = {}


def hazed(key, y):
    """The faces of key darkened by depth: the lower a block, the darker (so the height steps read in the picture)."""
    bucket = max(0, min(10, (72 - y) // 4))
    if (key, bucket) not in _hazed:
        top, side = faces(key)
        f = 1.0 - 0.06 * bucket
        _hazed[(key, bucket)] = tuple(ImageEnhance.Brightness(t).enhance(f) for t in (top, side))
    return _hazed[(key, bucket)]


def blocks(m):
    out = []
    for (x, y, z), key in m.items():
        # mirrored x: the camera's east face is the world's west face
        if (x - 1, y, z) in m and (x, y + 1, z) in m and (x, y, z + 1) in m:
            continue
        top, side = hazed(key, y)
        b = dict(x=-x, y=y, z=z, top=top, east=side, south=side)
        if key == "lantern" or key.startswith("lamp_"):
            b["outline"] = (255, 240, 180, 160)
        out.append(b)
    return out


def ghost_gate():
    gx, gy, gz = P.gate_origin()
    cx, cy, cz = gx + 3, gy + 2, gz + 9
    out = []
    for dx in range(-3, 4):
        for dy in range(0, 7):
            d = (dx * dx + (dy - 3) ** 2)
            if 6 <= d <= 11:
                out.append((cx + dx, cy + dy, cz))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--scale", type=int, default=6)
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    m = P.cells()
    for b in ghost_gate():
        m.setdefault(b, "metal_8")  # preview only: Stargate Journey's ring stands here in the world
    bl = blocks(m)
    im = ISO.render(bl, a=args.scale, pad=24)
    bg = Image.new("RGBA", im.size, (6, 6, 20, 255))
    bg.alpha_composite(im)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    bg.convert("RGB").save(args.out, optimize=True)
    print("wrote", os.path.relpath(args.out, REPO), bg.size, len(bl), "visible of", len(m), "cells")


if __name__ == "__main__":
    main()
