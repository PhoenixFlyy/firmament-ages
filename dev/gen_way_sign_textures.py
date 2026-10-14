#!/usr/bin/env python
"""Item icons for Supplementaries' way signs in the 34 TFC and ArborFirmaCraft woods (pack-drawn, 16x16).

Why: Supplementaries 3.9.9 builds the way-sign item texture of every wood at runtime with Moonlight, by recoloring the
wood's sign item texture. TFC 4.2.11 has no per-wood sign item texture (it tints one grey sign_head.png), so for ten
TFC woods the generation fails ("Failed to generate texture supplementaries:item/way_signs/tfc/way_sign_<wood>",
"Missing textures in model supplementaries:tfc/way_sign_<wood>#inventory") and the item shows the purple-black
missing texture; for the other ten, and for six of the 14 ArborFirmaCraft woods, the scribble overlay fails ("Failed to
apply sign scribbles overlay for tfc:<wood>" / "afc:<wood>"). Moonlight only generates a texture when no resource pack
has one (ResourceSink.addTextureUnlessPresent), so these files in kubejs/assets replace the generation for all 34 home
woods of the pack (the woods Every Compat builds for, config/everycomp-hazardous.toml): one look, no errors.

The art is the pack's own (no Supplementaries or TFC pixels): a post with two arrow boards, in a 5-tone wood palette.
The palettes are the 5/25/50/75/95 % luminance quantiles of the wood's planks texture (TFC 4.2.11, AFC 2.1.3); they
are stored below so the build needs no mod jar. To recompute them:
    python dev/gen_way_sign_textures.py --sample <TFC jar> <AFC jar>

    python dev/gen_way_sign_textures.py            # write the 34 PNGs (way_signs/tfc/, way_signs/afc/)
    python dev/gen_way_sign_textures.py --check    # only check that all 34 exist and are 16x16 RGBA
"""
import argparse
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "kubejs" / "assets" / "supplementaries" / "textures" / "item" / "way_signs"

WOODS = {
    "tfc": ["acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
            "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow"],
    "afc": ["araucaria", "baobab", "beech", "cypress", "eucalyptus", "fig", "ginkgo", "hevea", "ipe", "ironwood",
            "mahoe", "mahogany", "teak", "tualang"],
}

# 5 tones per wood (dark .. highlight) from assets/<ns>/textures/block/wood/planks/<wood>.png (--sample).
PAL = {
    "tfc:acacia": ["#692c1f", "#763123", "#883928", "#913d2b", "#9d422f"],
    "tfc:ash": ["#6d3a2e", "#894a3d", "#9e5647", "#a55a4a", "#ae604e"],
    "tfc:aspen": ["#26261b", "#343425", "#40402d", "#474732", "#515139"],
    "tfc:birch": ["#4e452f", "#64593f", "#756748", "#796b4b", "#80704e"],
    "tfc:blackwood": ["#11100f", "#1d1b19", "#252525", "#2f2b27", "#2f2b27"],
    "tfc:chestnut": ["#432e25", "#55341f", "#6d3d2c", "#714528", "#7e5330"],
    "tfc:douglas_fir": ["#9e7f61", "#ae9476", "#c8a77a", "#d7b085", "#d7bc8d"],
    "tfc:hickory": ["#291a0c", "#3a2411", "#492f17", "#4f3218", "#53381a"],
    "tfc:kapok": ["#704c63", "#906380", "#a27892", "#a67e98", "#ad879f"],
    "tfc:mangrove": ["#5d1c1e", "#6f2a2d", "#773934", "#7f4234", "#8b4d3a"],
    "tfc:maple": ["#63401d", "#7e552d", "#946637", "#9d6c39", "#aa743d"],
    "tfc:oak": ["#67502c", "#967441", "#af8f55", "#b8945f", "#c29d62"],
    "tfc:palm": ["#764622", "#9c5e2e", "#a56430", "#b66e35", "#c07438"],
    "tfc:pine": ["#887355", "#a68f6f", "#c0ae8b", "#c9b595", "#d1bd9a"],
    "tfc:rosewood": ["#4b1111", "#6c1919", "#8f2228", "#8f2821", "#933434"],
    "tfc:sequoia": ["#513024", "#643a2c", "#754434", "#804a38", "#8e533f"],
    "tfc:spruce": ["#834a3b", "#a35d4a", "#b6705d", "#ba7764", "#bf806f"],
    "tfc:sycamore": ["#816344", "#9b6e3b", "#bf8a3a", "#b99546", "#d2a040"],
    "tfc:white_cedar": ["#7a736e", "#998f8a", "#afa49d", "#b9ada6", "#c2b5ad"],
    "tfc:willow": ["#151e02", "#252c07", "#313a09", "#353f09", "#3a430b"],
    "afc:araucaria": ["#c0b2b2", "#d2c6c7", "#e7e3e1", "#faefee", "#fffbf8"],
    "afc:baobab": ["#884728", "#99502b", "#ad5d32", "#ba6337", "#c26d3f"],
    "afc:beech": ["#34261b", "#443324", "#5e4731", "#6d523a", "#6d523a"],
    "afc:cypress": ["#553a1f", "#614b2e", "#7a5a34", "#82613a", "#886539"],
    "afc:eucalyptus": ["#9e8b61", "#ae9f76", "#c8b77a", "#d7c185", "#d7cb8d"],
    "afc:fig": ["#cd8580", "#e1a8a1", "#e7bab4", "#e7c2bb", "#e7cac5"],
    "afc:ginkgo": ["#2f5f58", "#376f68", "#448981", "#4c988f", "#4c988f"],
    "afc:hevea": ["#5e613b", "#666b41", "#8b9458", "#aab06e", "#aab06e"],
    "afc:ipe": ["#9e833b", "#ae9142", "#c8a64b", "#d7b351", "#d7b351"],
    "afc:ironwood": ["#373737", "#444141", "#494848", "#525050", "#585555"],
    "afc:mahoe": ["#0e2545", "#122d55", "#173a6f", "#1b427e", "#1b427e"],
    "afc:mahogany": ["#5d1c2f", "#6f2a42", "#77344e", "#7e3d59", "#7e3d59"],
    "afc:teak": ["#68462f", "#976a44", "#aa7954", "#b88764", "#bf8e6b"],
    "afc:tualang": ["#646687", "#707297", "#8385b1", "#8f91c0", "#8f91c0"],
}

# O outline; 2..5 board tones (dark .. light); 1 and 2 are the post (darker than the boards). Upper board points
# right, lower board points left, the post shows above, between and below them.
POST = ".....O21O......."
GRAIN = "33" + "22" + "333" + "22" + "33"
GRID = [
    "......OO........",
    POST,
    "O" * 14 + "..",
    "O" + "4" + "5" * 11 + "3" + "O" + ".",
    "O" + "3" * 14 + "O",
    "O" + "2" + GRAIN + "2" + "O" + ".",
    "O" * 14 + "..",
    POST,
    ".." + "O" * 14,
    "." + "O" + "3" + "5" * 11 + "4" + "O",
    "O" + "3" * 14 + "O",
    "." + "O" + "2" + GRAIN + "2" + "O",
    ".." + "O" * 14,
    POST,
    POST,
    ".....OOOO.......",
]


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in rgb)


def draw(wood):
    raw = [hex_rgb(h) for h in PAL[wood]]
    # The planks quantiles are close together and spreading them per channel shifts the hue, so the board tones are
    # a brightness ramp of the median tone (hue kept); the highlight is the lightest quantile, brightened.
    tones = [shade(raw[2], f) for f in (0.62, 0.80, 1.0, 1.16)] + [shade(raw[4], 1.25)]
    colors = {
        "O": shade(tones[0], 0.55),
        "1": shade(tones[0], 0.80),
        "2": tones[0],
        "3": tones[1],
        "4": tones[3],
        "5": tones[2],
    }
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        if len(row) != 16:
            raise ValueError("grid row %d has %d columns" % (y, len(row)))
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch] + (255,))
    # one highlight pixel on the top edge of each board
    img.putpixel((2, 3), tones[4] + (255,))
    img.putpixel((12, 9), tones[4] + (255,))
    return img


def sample(jars):
    out = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            for ns, woods in WOODS.items():
                for wood in woods:
                    entry = "assets/%s/textures/block/wood/planks/%s.png" % (ns, wood)
                    if entry not in names:
                        continue
                    im = Image.open(io.BytesIO(z.read(entry))).convert("RGBA")
                    data = im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata()
                    px = [p[:3] for p in data if p[3] > 0]
                    px.sort(key=lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])
                    q = [px[min(len(px) - 1, int(len(px) * f))] for f in (0.05, 0.25, 0.5, 0.75, 0.95)]
                    out["%s:%s" % (ns, wood)] = ["#%02x%02x%02x" % c for c in q]
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--sample", nargs="+", metavar="JAR", help="TFC and AFC jars: print the palettes")
    a = ap.parse_args()
    if a.sample:
        for wood, tones in sample(a.sample).items():
            print('    "%s": [%s],' % (wood, ", ".join('"%s"' % t for t in tones)))
        return 0
    keys = ["%s:%s" % (ns, w) for ns, woods in WOODS.items() for w in woods]
    missing = [k for k in keys if k not in PAL]
    if missing:
        print("gen_way_sign_textures: no palette for %s" % ", ".join(missing))
        return 1
    bad = []
    for key in keys:
        ns, wood = key.split(":")
        path = OUT / ns / ("way_sign_%s.png" % wood)
        if a.check:
            if not path.is_file():
                bad.append("%s missing" % path.name)
            else:
                im = Image.open(path)
                if im.size != (16, 16) or im.mode != "RGBA":
                    bad.append("%s is %s %s" % (path.name, im.size, im.mode))
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        draw(key).save(path)
    if bad:
        print("gen_way_sign_textures: " + "; ".join(bad))
        return 1
    print("gen_way_sign_textures: %s %d way sign icons in %s" % ("checked" if a.check else "wrote", len(keys),
                                                                   OUT.relative_to(ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
