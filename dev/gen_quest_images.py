#!/usr/bin/env python3
"""Firmament Ages - pictures for the quest descriptions (the Chronicle).

Writes reproducible PNGs into the client resource pack the pack ships (KubeJS loads kubejs/assets on the client):

  kubejs/assets/firmages/textures/quests/knapping/<name>.png  knapping patterns, stone and clay (128 x 128)
  kubejs/assets/firmages/textures/quests/device/<name>.png    TFC devices with numbered parts and a legend (256 x 256)
  kubejs/assets/firmages/textures/quests/ponder/<name>.png    a still of each Ponder scene of firmages-core with a
                                                              "click to watch" frame (256 x 256)
  kubejs/assets/firmages/textures/quests/build/<name>.png     the build strands: gatehouse, living house, silo (256 x 256)
  dev/data/quest_images.json                                  manifest: resource location, display size, click action
                                                              (read by dev/quest_image_refs.py and dev/validate_quests.py)

Path, naming, sizes and the description syntax are agreed in dev/quest-images.md.

Every picture is drawn on a logical grid and scaled up 2x (nearest) into its texture; the quest shows it at the logical
size (width/height of the {image:...} line), so each logical pixel lands on whole screen pixels at every integer GUI
scale. Block scenes are drawn at texture resolution by a small isometric renderer (painter's order, flat shaded faces,
no anti-aliasing) with one colour pair per block from dev/data/quest_block_colors.json. Text is the pack's own 5x7
pixel font below (no system font, so every machine draws the same pixels).

Sources:
  knapping   the patterns are kept here with their TFC 4.2.11 recipe ids; the ASCII grids in dev/gen_quests.py are
             read with ast and must each match one of them (--check fails on a grid without a picture)
  device     TFC 4.2.11 field guide (assets/tfc/patchouli_books/field_guide/en_us/entries) and the multiblocks of
             net.dries007.tfc.compat.patchouli.PatchouliIntegration (bloomery, blast furnace, charcoal forge; javap)
  ponder     the build plans of dev/gen_ponder_schematics.py (the same plans the Ponder scenes load), seen from the
             north-west like Ponder; click action custom_event:firmages:ponder/<ns>/<path> (firmages-core
             QuestPonderLink, SPEC 22.5). Stills of Create's own scenes (elevator, display link, clockwork and
             mechanical bearing) come from the schematics in the Create jar, copied into
             dev/data/create_ponder_plans.json by --refresh-ponders (so --check needs no jar)
  build      plans in this file after Modpack-Planung/research-raw/n7-build.md 1.4 and 2.1 to 2.3

Usage:
  python dev/gen_quest_images.py                 write every picture and the manifest
  python dev/gen_quest_images.py --check         regenerate in memory and compare pixels with the files (exit 1 on a
                                                 difference, a missing or extra file, a size over budget, or a
                                                 knapping grid in dev/gen_quests.py without a picture)
  python dev/gen_quest_images.py --only device   one group (knapping, device, ponder, build)
  python dev/gen_quest_images.py --refresh-colors --mods <mods dir>
                                                 sample the mean colour of modded block textures from the jars into
                                                 dev/data/quest_block_colors.json (vanilla blocks keep the hand table)
  python dev/gen_quest_images.py --refresh-ponders --mods <mods dir>
                                                 copy the Create Ponder schematics the stills use into
                                                 dev/data/create_ponder_plans.json
  python dev/gen_quest_images.py --preview out.png
                                                 contact sheet of every picture (not tracked)
Needs Pillow.
"""
from __future__ import annotations

import argparse
import ast
import importlib.util
import io
import json
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "dev"
OUT = ROOT / "kubejs" / "assets" / "firmages" / "textures" / "quests"
MANIFEST = DEV / "data" / "quest_images.json"
COLORS_FILE = DEV / "data" / "quest_block_colors.json"
CREATE_PONDERS_FILE = DEV / "data" / "create_ponder_plans.json"
QUESTS_PY = DEV / "gen_quests.py"
NS = "firmages"
GROUPS = ("knapping", "device", "ponder", "build")
MAX_FILE = 256 * 1024
MAX_TOTAL = 10 * 1024 * 1024
UP = 2  # texture pixels per logical pixel

# Firmament palette (dev/gen_quest_art.py)
NIGHT = (7, 10, 28)
NIGHT_BLUE = (16, 26, 62)
PLATE = (13, 19, 46)
PLATE_EDGE = (34, 44, 92)
GOLD = (232, 197, 106)
GOLD_DARK = (138, 106, 36)
TEXT = (242, 230, 201)
TEXT_DIM = (170, 160, 190)
SHADOW = (4, 6, 16)


# ------------------------------------------------------------------------------------------------ pixel font
# 5x7, uppercase. Glyphs are trimmed to their used columns; advance = width + 1, space = 3.
_FONT_SRC = {
    "A": ".###.|#...#|#...#|#####|#...#|#...#|#...#", "B": "####.|#...#|#...#|####.|#...#|#...#|####.",
    "C": ".###.|#...#|#....|#....|#....|#...#|.###.", "D": "####.|#...#|#...#|#...#|#...#|#...#|####.",
    "E": "#####|#....|#....|####.|#....|#....|#####", "F": "#####|#....|#....|####.|#....|#....|#....",
    "G": ".###.|#...#|#....|#.###|#...#|#...#|.###.", "H": "#...#|#...#|#...#|#####|#...#|#...#|#...#",
    "I": "###|.#.|.#.|.#.|.#.|.#.|###", "J": "..###|...#.|...#.|...#.|...#.|#..#.|.##..",
    "K": "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#", "L": "#....|#....|#....|#....|#....|#....|#####",
    "M": "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#", "N": "#...#|#...#|##..#|#.#.#|#..##|#...#|#...#",
    "O": ".###.|#...#|#...#|#...#|#...#|#...#|.###.", "P": "####.|#...#|#...#|####.|#....|#....|#....",
    "Q": ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#", "R": "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
    "S": ".####|#....|#....|.###.|....#|....#|####.", "T": "#####|..#..|..#..|..#..|..#..|..#..|..#..",
    "U": "#...#|#...#|#...#|#...#|#...#|#...#|.###.", "V": "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
    "W": "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.", "X": "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
    "Y": "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..", "Z": "#####|....#|...#.|..#..|.#...|#....|#####",
    "0": ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.", "1": ".#.|##.|.#.|.#.|.#.|.#.|###",
    "2": ".###.|#...#|....#|...#.|..#..|.#...|#####", "3": "####.|....#|....#|.###.|....#|....#|####.",
    "4": "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.", "5": "#####|#....|####.|....#|....#|#...#|.###.",
    "6": "..##.|.#...|#....|####.|#...#|#...#|.###.", "7": "#####|....#|...#.|..#..|.#...|.#...|.#...",
    "8": ".###.|#...#|#...#|.###.|#...#|#...#|.###.", "9": ".###.|#...#|#...#|.####|....#|...#.|.##..",
    ".": ".|.|.|.|.|#|#", ",": "..|..|..|..|.#|.#|#.", ":": ".|#|#|.|#|#|.", "-": "...|...|...|###|...|...|...",
    "+": ".....|..#..|..#..|#####|..#..|..#..|.....", "/": "....#|....#|...#.|..#..|.#...|#....|#....",
    "(": "..#|.#.|#..|#..|#..|.#.|..#", ")": "#..|.#.|..#|..#|..#|.#.|#..", "'": "#|#|#|.|.|.|.",
    "?": ".###.|#...#|....#|...#.|..#..|.....|..#..", ">": "#...|.#..|..#.|...#|..#.|.#..|#...",
    "=": "....|....|####|....|####|....|....",
}
FONT = {c: [row for row in src.split("|")] for c, src in _FONT_SRC.items()}
FONT_H = 7


def text_width(s: str) -> int:
    w = 0
    for ch in s.upper():
        w += 3 if ch == " " else len(FONT[ch][0]) + 1
    return max(0, w - 1)


# ------------------------------------------------------------------------------------------------ canvas
def rgba(c, a=255):
    return (c[0], c[1], c[2], a) if len(c) == 3 else c


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


class Canvas:
    """Logical-pixel drawing on a texture UP times larger. `im` is the texture (RGBA)."""

    def __init__(self, w: int, h: int, bg=None):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w * UP, h * UP), (0, 0, 0, 0) if bg is None else rgba(bg))
        self.d = ImageDraw.Draw(self.im)

    def rect(self, x, y, w, h, c):
        if w <= 0 or h <= 0:
            return
        self.d.rectangle([x * UP, y * UP, (x + w) * UP - 1, (y + h) * UP - 1], fill=rgba(c))

    def px(self, x, y, c):
        self.rect(x, y, 1, 1, c)

    def frame(self, x, y, w, h, c):
        self.rect(x, y, w, 1, c)
        self.rect(x, y + h - 1, w, 1, c)
        self.rect(x, y, 1, h, c)
        self.rect(x + w - 1, y, 1, h, c)

    def text(self, x, y, s, c=TEXT, shadow=True):
        s = s.upper()
        if shadow:
            self._text(x + 1, y + 1, s, SHADOW)
        self._text(x, y, s, c)

    def _text(self, x, y, s, c):
        cx = x
        for ch in s:
            if ch == " ":
                cx += 3
                continue
            g = FONT[ch]
            for r, row in enumerate(g):
                for k, v in enumerate(row):
                    if v == "#":
                        self.px(cx + k, y + r, c)
            cx += len(g[0]) + 1

    def text_center(self, cx, y, s, c=TEXT, shadow=True):
        self.text(cx - text_width(s) // 2, y, s, c, shadow)

    def badge(self, x, y, n, c=GOLD):
        """9 x 9 number badge with its top-left at (x, y)."""
        s = str(n)
        w = max(9, text_width(s) + 4)
        self.rect(x + 1, y, w - 2, 9, c)
        self.rect(x, y + 1, w, 7, c)
        self.rect(x + 1, y + 8, w - 2, 1, shade(c, 0.6))
        self.rect(x + w - 1, y + 1, 1, 7, shade(c, 0.6))
        self._text(x + (w - text_width(s)) // 2, y + 1, s, NIGHT)
        return w

    def sprite(self, x, y, rows, palette):
        for r, row in enumerate(rows):
            for k, ch in enumerate(row):
                if ch != "." and ch != " ":
                    self.px(x + k, y + r, palette[ch])


def plate(c: Canvas, title: str | None = None, accent=GOLD):
    """Night-blue plate with a thin border, gold corners and an optional title strip."""
    c.rect(0, 0, c.w, c.h, PLATE)
    c.frame(0, 0, c.w, c.h, PLATE_EDGE)
    for (x, y) in ((0, 0), (c.w - 1, 0), (0, c.h - 1), (c.w - 1, c.h - 1)):
        c.px(x, y, (0, 0, 0, 0))
    for (x, y, dx, dy) in ((1, 1, 1, 1), (c.w - 2, 1, -1, 1), (1, c.h - 2, 1, -1), (c.w - 2, c.h - 2, -1, -1)):
        c.px(x, y, accent)
        c.px(x + dx, y, accent)
        c.px(x, y + dy, accent)
    if title:
        c.rect(3, 3, c.w - 6, 11, NIGHT)
        c.rect(3, 14, c.w - 6, 1, GOLD_DARK)
        c.text_center(c.w // 2, 5, title, GOLD)


LEGEND_ROW = 10


def wrap_legend(items, width=128):
    """(badge number, text) -> rows (number or None, text) that fit the plate; a long text wraps onto rows of its own
    under the first (same indent). Raises on a single word too wide."""
    room = width - 4 - 12 - 4
    rows = []
    for n, s in items:
        line = ""
        first = n is not None
        for word in s.split(" "):
            cand = (line + " " + word).strip()
            if text_width(cand) <= room:
                line = cand
                continue
            if not line:
                raise ValueError(f"legend word too wide: {word!r}")
            rows.append((n if first else None, line))
            first = False
            line = word
        rows.append((n if first else None, line))
    return rows


def legend(c: Canvas, rows, top: int, x: int = 4):
    """Rows from wrap_legend; returns the y below the last row."""
    y = top
    for n, s in rows:
        if n is not None:
            c.badge(x, y, n)
        c.text(x + 12, y + 1, s)
        y += LEGEND_ROW
    return y


# ------------------------------------------------------------------------------------------------ icons (logical px)
ICON_PAL = {
    "k": (40, 26, 14), "b": (122, 82, 44), "B": (160, 112, 64), "l": (196, 150, 92), "r": (92, 60, 30),
    "y": (226, 196, 92), "Y": (250, 230, 140), "o": (240, 120, 30), "O": (255, 190, 60), "w": (255, 244, 200),
    "g": (110, 112, 118), "G": (160, 162, 168), "d": (60, 62, 68), "c": (40, 40, 44), "C": (80, 80, 86),
    "e": (180, 100, 70), "q": (226, 206, 172), "Q": (150, 120, 90), "u": (70, 120, 220), "U": (130, 180, 250), "p": (232, 197, 106),
}
ICONS = {
    "log": ["..kkkk..", ".kbbbbk.", "kbBllBbk", "kblrrlbk", "kblrrlbk", "kbBllBbk", ".kbbbbk.", "..kkkk.."],
    "stick": ["......kb", ".....kbk", "....kbk.", "...kbk..", "..kbk...", ".kbk....", "kbk.....", "bk......"],
    "straw": ["y.Y..y.Y", ".y.Y.y.Y", ".yY.yY.y", "..yYy.Y.", "..yYyY..", ".yY.yY..", "y.Y..yY.", "..y...y."],
    "flame": ["...o....", "...oo...", "..oOo.o.", "..oOOoo.", ".oOwOOo.", ".oOwwOo.", "oOOwwOOo", ".oOOOOo."],
    "firestarter": ["......kb", ".....kbk", "....kbk.", "kb.kbk..", ".kbbk...", "..kbbk..", ".kbk.kb.", "kbk...kb"],
    "ore": ["........", "..dgGd..", ".dgeGgd.", "dgGgeGgd", "dgeGgGgd", ".dgGegd.", "..dggd..", "........"],
    "charcoal": ["........", "..cCCc..", ".cCcCCc.", "cCcCCcCc", "cCCcCCcc", ".cCcCcc.", "..cccc..", "........"],
    "pot": ["..QQQQ..", ".QqqqqQ.", "QqqqqqqQ", "QqqqqqqQ", "QqqqqqqQ", ".QqqqqQ.", "..QQQQ..", "........"],
    "water": ["...u....", "...uu...", "..uUu...", "..uUUu..", ".uUUUUu.", ".uUwUUu.", ".uUUUUu.", "..uuuu.."],
    "arrow": ["........", "....p...", "....pp..", "ppppppp.", "ppppppp.", "....pp..", "....p...", "........"],
}


# ------------------------------------------------------------------------------------------------ block colours
# (top, side) RGB per block id. Vanilla: hand values close to the textures' mean colour (no vanilla jar is read).
# Modded ids not listed here come from dev/data/quest_block_colors.json (--refresh-colors) or the fallback.
VANILLA = {
    "minecraft:stone": ((125, 125, 125), (116, 116, 116)),
    "minecraft:andesite": ((136, 136, 137), (130, 130, 131)),
    "minecraft:polished_andesite": ((132, 135, 134), (128, 131, 130)),
    "minecraft:stone_bricks": ((122, 121, 122), (112, 111, 112)),
    "minecraft:smooth_stone": ((160, 160, 160), (150, 150, 150)),
    "minecraft:grass_block": ((96, 150, 60), (134, 96, 67)),
    "minecraft:dirt": ((134, 96, 67), (134, 96, 67)),
    "minecraft:iron_block": ((220, 220, 220), (205, 205, 205)),
    "minecraft:white_concrete": ((207, 213, 214), (196, 202, 203)),
    "minecraft:red_concrete": ((142, 33, 33), (132, 30, 30)),
    "minecraft:gray_concrete": ((54, 57, 61), (50, 53, 57)),
    "minecraft:lightning_rod": ((198, 110, 80), (178, 98, 70)),
    "minecraft:iron_bars": ((136, 139, 135), (120, 122, 118)),
    "minecraft:lever": ((110, 90, 60), (110, 90, 60)),
    "minecraft:oak_slab": ((162, 131, 79), (150, 121, 72)),
    "minecraft:oak_planks": ((162, 131, 79), (150, 121, 72)),
    "minecraft:spruce_planks": ((115, 85, 49), (105, 78, 45)),
    "minecraft:spruce_log": ((108, 80, 46), (58, 37, 16)),
    "minecraft:oak_log": ((151, 122, 73), (109, 85, 50)),
    "minecraft:water": ((50, 90, 200), (44, 80, 180)),
    "minecraft:glass": ((190, 220, 230), (170, 200, 215)),
    "minecraft:bricks": ((150, 97, 83), (140, 90, 77)),
    "minecraft:hopper": ((70, 70, 72), (62, 62, 64)),
    "minecraft:chest": ((160, 115, 50), (150, 105, 45)),
    "minecraft:comparator": ((165, 160, 155), (150, 145, 140)),
    "minecraft:dark_oak_planks": ((66, 43, 20), (60, 39, 18)),
    "minecraft:deepslate_tiles": ((54, 54, 55), (48, 48, 50)),
    "minecraft:lantern": ((230, 170, 80), (90, 80, 70)),
    "minecraft:campfire": ((240, 140, 40), (90, 64, 40)),
    "minecraft:cobblestone": ((127, 127, 127), (118, 118, 118)),
    "minecraft:redstone_lamp": ((236, 196, 120), (214, 170, 96)),
    "minecraft:redstone_wire": ((170, 30, 20), (150, 26, 18)),
    "minecraft:snow_block": ((236, 240, 242), (226, 230, 232)),
    "minecraft:oak_fence": ((162, 131, 79), (150, 121, 72)),
    "minecraft:magenta_glazed_terracotta": ((208, 100, 191), (190, 90, 175)),
    "minecraft:stone_brick_wall": ((122, 121, 122), (112, 111, 112)),
    "minecraft:air": ((0, 0, 0), (0, 0, 0)),
}
FALLBACK = ((150, 120, 170), (130, 100, 150))
# Hand values for blocks whose texture mean says little (multi-part models, entities, fluids).
HAND = {
    "firmages:storm_glass": ((150, 200, 235), (120, 170, 215)),
    "firmages:star_anvil": ((60, 64, 92), (44, 48, 72)),
    "firmages:lens_mount": ((150, 120, 70), (110, 88, 50)),
    "ad_astra:launch_pad": ((95, 98, 104), (70, 72, 78)),
    "create:rope_pulley": ((176, 140, 90), (120, 110, 100)),
    "create:shaft": ((150, 150, 150), (120, 120, 120)),
    "create:gearshift": ((176, 140, 90), (120, 110, 100)),
    "create:gantry_shaft": ((190, 150, 80), (140, 110, 60)),
    "create:gantry_carriage": ((176, 140, 90), (120, 110, 100)),
    "create:mechanical_bearing": ((176, 140, 90), (120, 110, 100)),
    "create:clockwork_bearing": ((196, 160, 90), (120, 110, 100)),
    "firmages:quest/clock_hand": ((40, 34, 30), (30, 26, 22)),   # picture only: the hands of the gable clock
    "firmages:quest/rope_bridge": ((176, 140, 90), (150, 118, 72)),  # picture only: a rope bridge plank
    "create:elevator_pulley": ((196, 160, 90), (150, 120, 70)),
    "create:redstone_link": ((150, 60, 50), (120, 50, 40)),
    "create:display_link": ((90, 70, 50), (80, 60, 40)),
    "create:display_board": ((60, 60, 62), (230, 210, 140)),
    "create:brass_door": ((196, 160, 90), (176, 140, 80)),
    "create:andesite_casing": ((160, 160, 150), (140, 140, 130)),
    "create:andesite_encased_shaft": ((160, 160, 150), (140, 140, 130)),
    "create:andesite_scaffolding": ((160, 150, 120), (140, 130, 105)),
    "create:metal_girder": ((92, 92, 98), (72, 72, 78)),
    "create:redstone_contact": ((150, 60, 50), (120, 50, 40)),
    "simulated:optical_sensor": ((90, 130, 160), (70, 100, 130)),
    "ae2:pattern_provider": ((90, 90, 110), (70, 70, 90)),
    "tfc:log_pile": ((176, 136, 82), (110, 78, 40)),
    "tfc:charcoal_pile": ((44, 42, 40), (36, 34, 32)),
    "tfc:charcoal_forge": ((240, 120, 40), (44, 42, 40)),
    "tfc:pit_kiln/straw": ((216, 186, 92), (196, 166, 80)),
    "tfc:pit_kiln/logs": ((176, 136, 82), (110, 78, 40)),
    "tfc:firepit": ((240, 130, 40), (80, 60, 40)),
    "tfc:bloomery": ((150, 112, 70), (120, 86, 54)),
    "tfc:blast_furnace": ((130, 120, 116), (106, 96, 92)),
    "tfc:bellows": ((150, 110, 70), (120, 86, 54)),
    "tfc:crucible": ((170, 120, 90), (150, 100, 76)),
    "tfc:metal/anvil/copper": ((184, 110, 70), (150, 90, 56)),
    "tfc:quern": ((158, 158, 158), (140, 140, 140)),
    "tfc:quern/handstone": ((120, 120, 122), (100, 100, 104)),
    "tfc:wood/barrel/oak": ((60, 110, 200), (150, 118, 72)),
    "tfc:thatch": ((200, 170, 80), (180, 150, 70)),
    "tfc:fire_bricks": ((150, 72, 48), (130, 62, 42)),
    "tfc:reinforced_fire_bricks": ((156, 140, 136), (140, 70, 50)),
    "firmages:quest/hatch_leaf": ((186, 192, 204), (150, 156, 170)),  # picture only: the silo's hatch leaves
}


def load_colors():
    table = dict(VANILLA)
    if COLORS_FILE.exists():
        for k, v in json.loads(COLORS_FILE.read_text(encoding="utf-8")).get("blocks", {}).items():
            table[k] = (tuple(v["top"]), tuple(v["side"]))
    table.update(HAND)
    return table


COLORS: dict = {}


def color_of(block_id: str):
    base = block_id.split("[")[0]
    if base in COLORS:
        return COLORS[base]
    return FALLBACK


# ------------------------------------------------------------------------------------------------ isometric renderer
class Scene:
    """Blocks on an integer grid. kind: cube (default), slab (height), bars (open grate), pane (thin glass)."""

    def __init__(self):
        self.blocks = {}   # (x, y, z) -> dict(id, h, kind, top, side)
        self.sprites = []  # (x, y, z, icon name) drawn on top of the block cell (x, y, z)

    def put(self, x, y, z, bid, h=1.0, kind="cube", top=None, side=None):
        t, s = color_of(bid)
        self.blocks[(x, y, z)] = {"id": bid, "h": h, "kind": kind, "top": top or t, "side": side or s}

    def fill(self, x0, y0, z0, x1, y1, z1, bid, **kw):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, bid, **kw)

    def clear(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks.pop((x, y, z), None)

    def sprite(self, x, y, z, name):
        self.sprites.append((x, y, z, name))

    def rotated(self, turns: int):
        """Turn the scene by 90 degrees `turns` times about the vertical axis (turns=2: seen from the north-west)."""
        if not self.blocks:
            return self
        out = Scene()
        blocks, sprites = dict(self.blocks), list(self.sprites)
        for _ in range(turns % 4):  # (x, z) -> (max z - z, x), one quarter turn at a time
            bz = max(p[2] for p in blocks)
            blocks = {(bz - z, y, x): v for (x, y, z), v in blocks.items()}
            sprites = [(bz - z, y, x, n) for (x, y, z, n) in sprites]
        out.blocks, out.sprites = blocks, sprites
        return out

    @staticmethod
    def from_plan(plan, rename=None):
        """A gen_ponder_schematics.Plan -> Scene (block states mapped to a kind)."""
        s = Scene()
        for (x, y, z), (name, props, _nbt) in plan.blocks.items():
            p = dict(props)
            if rename and name in rename:
                name = rename[name]
            if name == "minecraft:iron_bars":
                s.put(x, y, z, name, kind="bars")
            elif name.endswith("_slab"):
                s.put(x, y, z, name, h=0.5)
            elif name in ("minecraft:lever", "minecraft:comparator", "create:redstone_link", "minecraft:redstone_wire",
                          "create:redstone_contact", "create:belt", "create:depot"):
                s.put(x, y, z, name, h=0.25 if name != "create:depot" else 0.5)
            elif name in THIN_SKIP or name.endswith("_button") or name.endswith("_sign") or name.startswith("minecraft:potted_"):
                continue
            elif name == "ad_astra:launch_pad":
                s.put(x, y, z, name, h=0.25)
            elif name == "create:shaft":
                s.put(x, y, z, name, kind="shaft", h=1.0)
            else:
                s.put(x, y, z, name)
        return s


# blocks a still leaves out: too small to read at this size, a cube would mislead
THIN_SKIP = {"minecraft:air", "minecraft:lectern", "create:cuckoo_clock", "create:nixie_tube", "create:stressometer",
             "create:content_observer", "create:contraption_controls", "create:train_door"}


class CreatePlan:
    """A Create Ponder schematic from dev/data/create_ponder_plans.json, shaped like gen_ponder_schematics.Plan."""

    def __init__(self, key):
        data = json.loads(CREATE_PONDERS_FILE.read_text(encoding="utf-8"))["plans"][key]
        self.size = tuple(data["size"])
        self.blocks = {(x, y, z): (name, (), None) for x, y, z, name in data["blocks"]}


def create_plan(key):
    return lambda: CreatePlan(key)


def _opaque(b):
    return b is not None and b["kind"] == "cube" and b["h"] >= 1.0


def render_scene(cv: Canvas, scene: Scene, box, a=None, max_a=16):
    """Draw `scene` into the logical box (x, y, w, h) of the canvas. Returns project(x, y, z) -> logical (x, y)."""
    bx, by, bw, bh = box
    if not scene.blocks:
        return lambda x, y, z: (bx, by)
    pts = []
    for (x, y, z), b in scene.blocks.items():
        for dx in (0, 1):
            for dz in (0, 1):
                for dy in (0, b["h"]):
                    pts.append((x + dx, y + dy, z + dz))
    for (x, y, z, _n) in scene.sprites:
        pts.append((x + 0.5, y + 1.6, z + 0.5))

    def raw(x, y, z, a_):
        return (x - z) * a_, (x + z) * a_ / 2 - y * a_

    W, H = bw * UP, bh * UP
    if a is None:
        a = 2
        for cand in range(max_a, 1, -1):
            xs = [raw(*p, cand)[0] for p in pts]
            ys = [raw(*p, cand)[1] for p in pts]
            if max(xs) - min(xs) <= W - 2 and max(ys) - min(ys) <= H - 2:
                a = cand
                break
        a -= a % 2
        a = max(a, 2)
    xs = [raw(*p, a)[0] for p in pts]
    ys = [raw(*p, a)[1] for p in pts]
    ox = bx * UP + (W - (max(xs) - min(xs))) // 2 - min(xs)
    oy = by * UP + (H - (max(ys) - min(ys))) // 2 - min(ys)
    ox, oy = int(round(ox)), int(round(oy))

    def P(x, y, z):
        sx, sy = raw(x, y, z, a)
        return (int(round(ox + sx)), int(round(oy + sy)))

    d = cv.d
    items = [(x + y + z, y, 0, (x, y, z)) for (x, y, z) in scene.blocks]
    items += [(x + y + z + 0.6, y, 1, (x, y, z, n)) for (x, y, z, n) in scene.sprites]
    items.sort(key=lambda t: (t[0], t[1], t[2], t[3][:3]))
    for _k, _y, typ, ref in items:
        if typ == 1:
            x, y, z, n = ref
            cx, cy = P(x + 0.5, y + 1, z + 0.5)
            icon = ICONS[n]
            lx = cx // UP - len(icon[0]) // 2
            ly = cy // UP - len(icon) + 1
            cv.sprite(lx, ly, icon, ICON_PAL)
            continue
        x, y, z = ref
        b = scene.blocks[ref]
        h = b["h"]
        top, side = b["top"], b["side"]
        south = scene.blocks.get((x, y, z + 1))
        east = scene.blocks.get((x + 1, y, z))
        above = scene.blocks.get((x, y + 1, z))
        kind = b["kind"]
        if kind == "bars":
            line = shade(side, 0.75)
            for f in (0.15, 0.5, 0.85):
                d.line([P(x + f, y, z + 1), P(x + f, y + 1, z + 1)], fill=rgba(line))
                d.line([P(x + 1, y, z + f), P(x + 1, y + 1, z + f)], fill=rgba(shade(line, 0.85)))
            d.line([P(x, y + 1, z + 1), P(x + 1, y + 1, z + 1)], fill=rgba(line))
            d.line([P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1)], fill=rgba(line))
            continue
        if kind == "shaft":  # a thin bar along x is enough at this size
            _box(d, P, x, y + 0.35, z + 0.35, x + 1, y + 0.65, z + 0.65, top, side)
            continue
        if not (_opaque(above) and h >= 1.0):
            poly = [P(x, y + h, z), P(x + 1, y + h, z), P(x + 1, y + h, z + 1), P(x, y + h, z + 1)]
            d.polygon(poly, fill=rgba(top), outline=rgba(shade(top, 0.72)))
        if not _opaque(south):
            c = shade(side, 0.84)
            poly = [P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + h, z + 1), P(x, y + h, z + 1)]
            d.polygon(poly, fill=rgba(c), outline=rgba(shade(c, 0.72)))
        if not _opaque(east):
            c = shade(side, 0.66)
            poly = [P(x + 1, y, z), P(x + 1, y, z + 1), P(x + 1, y + h, z + 1), P(x + 1, y + h, z)]
            d.polygon(poly, fill=rgba(c), outline=rgba(shade(c, 0.72)))

    def project(x, y, z):
        sx, sy = P(x, y, z)
        return sx // UP, sy // UP

    return project


def _box(d, P, x0, y0, z0, x1, y1, z1, top, side):
    d.polygon([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], fill=rgba(top), outline=rgba(shade(top, 0.7)))
    c = shade(side, 0.84)
    d.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=rgba(c), outline=rgba(shade(c, 0.7)))
    c = shade(side, 0.66)
    d.polygon([P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)], fill=rgba(c), outline=rgba(shade(c, 0.7)))


def badge_at(cv: Canvas, project, pos, n, dx=0, dy=0):
    x, y = project(*pos)
    cv.badge(x - 4 + dx, y - 4 + dy, n)


# ------------------------------------------------------------------------------------------------ knapping
# 5x5 rows, '#' = leave standing, ' ' = knock away; outside = the "default_on" fill of a smaller shape.
# Recipes: TFC 4.2.11 data/tfc/recipe/knapping/stone/<tool>_head/<rock group>[_n].json and
# data/tfc/recipe/knapping/ceramic/unfired_<name>.json; the Hearth Idol: kubejs/server_scripts/recipes/age_0/
# stone_age.js (firmages:knapping/unfired_hearth_idol).
KNAPPING = [
    # name, material, rows, outside
    ("axe_head", "stone", (" #   ", "#### ", "#####", "#### ", " #   "), " "),
    ("knife_blades", "stone", ("#  # ", "## ##", "## ##", "## ##", "## ##"), " "),
    ("hammer_head", "stone", ("#####", "#####", "  #  "), " "),
    ("shovel_head", "stone", ("###", "###", "###", "###", " # "), " "),
    ("hoe_heads", "stone", ("#####", "##   ", "     ", "#####", "##   "), " "),
    ("javelin_head", "stone", ("###  ", "#### ", "#####", " ### ", "  #  "), " "),
    ("small_vessel", "clay", (" ### ", "#####", "#####", "#####", " ### "), " "),
    ("ingot_molds", "clay", ("####", "#  #", "#  #", "#  #", "####"), "#"),
    ("pickaxe_head_mold", "clay", ("#####", "#   #", " ### ", "#####"), "#"),
    ("saw_blade_mold", "clay", ("  ###", "   ##", "#   #", "#    ", "###  "), " "),
    ("jug", "clay", (" #   ", "#### ", "### #", "#### ", "###  "), " "),
    ("pot", "clay", ("#   #", "#   #", "#   #", "#####", " ### "), " "),
    ("bowls", "clay", ("#   #", " ### ", "     ", "#   #", " ### "), " "),
    ("large_vessel", "clay", ("#   #", "#   #", "#   #", "#   #", "#####"), " "),
    ("hearth_idol", "clay", (" ### ", "#####", " ### ", " ### ", "## ##"), " "),
    # tfc:knapping/ceramic/unfired_axe_head_mold and unfired_hammer_head_mold (no default_on: the rest is knocked away)
    ("axe_head_mold", "clay", ("# ###", "    #", "     ", "    #", "# ###"), " "),
    ("hammer_head_mold", "clay", ("#####", "     ", "     ", "## ##", "#####"), " "),
    # unfired_knife_blade_mold ("default_on": true) and unfired_shovel_head_mold (no default_on)
    ("knife_blade_mold", "clay", ("## #", "#  #", "#  #", "#  #", "#  #"), "#"),
    ("shovel_head_mold", "clay", ("#   #", "#   #", "#   #", "#   #", "## ##"), " "),
]
MATERIAL = {
    # stand base, stand light, stand dark, pit, pit dark
    "stone": ((132, 136, 144), (178, 182, 190), (88, 92, 100), (22, 26, 48), (12, 15, 32)),
    "clay": ((176, 104, 76), (210, 140, 108), (122, 68, 48), (30, 22, 44), (16, 12, 30)),
}


def norm_grid(rows, outside):
    """The 25-cell string knap_grid() draws: rows padded to 5 with `outside`, missing rows all `outside`."""
    out = ""
    for r in range(5):
        row = rows[r] if r < len(rows) else ""
        out += row.ljust(5, outside)[:5]
    return out


def draw_knapping(name, material, rows, outside):
    cv = Canvas(64, 64)
    stand, light, dark, pit, pit_dark = MATERIAL[material]
    cells = norm_grid(rows, outside)
    cv.rect(0, 0, 64, 64, PLATE)
    cv.frame(0, 0, 64, 64, PLATE_EDGE)
    for (x, y) in ((0, 0), (63, 0), (0, 63), (63, 63)):
        cv.px(x, y, (0, 0, 0, 0))
    for (x, y) in ((1, 1), (62, 1), (1, 62), (62, 62)):
        cv.px(x, y, GOLD)
    # 5 cells of 10 px with 1 px gaps = 54 px, centred in 64 at 5
    ox = oy = 5
    for i, ch in enumerate(cells):
        r, k = divmod(i, 5)
        x, y = ox + k * 12 - k, oy + r * 12 - r  # 10 px cells, 1 px gaps
        if ch == "#":
            cv.rect(x, y, 10, 10, stand)
            cv.rect(x, y, 10, 1, light)
            cv.rect(x, y, 1, 10, light)
            cv.rect(x, y + 9, 10, 1, dark)
            cv.rect(x + 9, y, 1, 10, dark)
            # a few chips so it reads as stone or clay, the same in every picture
            for (px, py) in ((3, 3), (6, 5), (4, 7)):
                cv.px(x + px, y + py, shade(stand, 0.86))
        else:
            cv.rect(x, y, 10, 10, pit)
            cv.rect(x, y, 10, 1, pit_dark)
            cv.rect(x, y, 1, 10, pit_dark)
    return cv


def quest_grids():
    """Every knap_grid(...) call in dev/gen_quests.py -> list of (line, normalised 25-cell grid)."""
    tree = ast.parse(QUESTS_PY.read_text(encoding="utf-8"))
    found = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and getattr(node.func, "id", None) == "knap_grid":
            rows = [a.value for a in node.args if isinstance(a, ast.Constant)]
            if len(rows) != len(node.args):
                continue
            outside = " "
            for kw in node.keywords:
                if kw.arg == "outside" and isinstance(kw.value, ast.Constant):
                    outside = kw.value.value
            found.append((node.lineno, norm_grid(rows, outside)))
    return found


# ------------------------------------------------------------------------------------------------ devices
def ground(s: Scene, x0, z0, x1, z1, y=0, bid="minecraft:grass_block"):
    s.fill(x0, y, z0, x1, y, z1, bid)


def diagram(title, scene, items, badges=(), a=None, extras=None, max_a=24):
    """Plate with one block scene, numbered badges and a wrapped legend at the bottom (`title` names the picture in
    the code only: the quest title stands above it in the Chronicle).
    badges: (number, (x, y, z) in block coordinates, dx, dy) in logical px."""
    cv = Canvas(128, 128)
    plate(cv)
    rows = wrap_legend(items)
    top = 128 - 4 - LEGEND_ROW * len(rows)
    p = render_scene(cv, scene, (4, 4, 120, top - 7), a=a, max_a=max_a)
    if extras:
        extras(cv, p)
    for n, pos, dx, dy in badges:
        badge_at(cv, p, pos, n, dx, dy)
    legend(cv, rows, top)
    return cv


def panel_label(cv, x, y, w, s, n=None):
    tw = text_width(s) + (12 if n is not None else 0)
    x0 = x + (w - tw) // 2
    if n is not None:
        cv.badge(x0, y, n)
        x0 += 12
    cv.text(x0, y + 1, s)


def dev_firepit():
    cv = Canvas(128, 128)
    plate(cv)
    # left: the parts lying on one block; right: the lit firepit
    a = Scene()
    ground(a, 0, 0, 1, 1)
    a.sprite(0, 0, 1, "log")
    a.sprite(1, 0, 1, "stick")
    a.sprite(0, 0, 0, "straw")
    a.sprite(1, 0, 0, "stick")
    rows = wrap_legend([(1, "LOG AND THREE STICKS"), (2, "STRAW HELPS IT CATCH"), (3, "HOLD RIGHT-CLICK")])
    top = 128 - 4 - LEGEND_ROW * len(rows)
    pa = render_scene(cv, a, (4, 4, 54, top - 7), max_a=24)
    b = Scene()
    ground(b, 0, 0, 1, 1)
    b.put(0, 1, 0, "tfc:firepit", h=0.25)
    b.sprite(0, 1, 0, "flame")
    pb = render_scene(cv, b, (70, 4, 54, top - 7), max_a=24)
    cv.sprite(60, 4 + (top - 7) // 2 - 4, ICONS["arrow"], ICON_PAL)
    badge_at(cv, pa, (0.5, 1, 1.5), 1, dx=-16, dy=-2)
    badge_at(cv, pa, (0.5, 1, 0.5), 2, dx=-6, dy=-14)
    fx, fy = pb(0.5, 1.25, 0.5)
    cv.sprite(fx + 8, fy - 18, ICONS["firestarter"], ICON_PAL)
    cv.badge(fx + 18, fy - 18, 3)
    legend(cv, rows, top)
    return cv


def _kiln_pit(s: Scene):
    """3 x 2 x 3 ground with a one-block hole in the middle of the top layer."""
    s.fill(0, 0, 0, 2, 0, 2, "minecraft:dirt")
    s.fill(0, 1, 0, 2, 1, 2, "minecraft:grass_block")
    s.blocks.pop((1, 1, 1))


def dev_pit_kiln():
    cv = Canvas(128, 128)
    plate(cv)
    s1 = Scene(); _kiln_pit(s1)
    s2 = Scene(); _kiln_pit(s2); s2.put(1, 1, 1, "tfc:pit_kiln/straw", h=0.35)
    s3 = Scene(); _kiln_pit(s3); s3.put(1, 1, 1, "tfc:pit_kiln/logs", h=1.0)
    s4 = Scene(); _kiln_pit(s4); s4.put(1, 1, 1, "tfc:pit_kiln/logs", h=1.0); s4.sprite(1, 1, 1, "flame")
    labels = ["POTTERY", "STRAW", "LOGS", "FIRE"]
    for i, (sc, lab) in enumerate(zip((s1, s2, s3, s4), labels)):
        col, row = i % 2, i // 2
        x, y = 4 + col * 61, 4 + row * 61
        cv.rect(x, y, 59, 59, NIGHT_BLUE)
        pr = render_scene(cv, sc, (x + 2, y + 2, 55, 44), max_a=24)
        if i == 0:  # the pottery sits in the hole; drawn over the rim so it shows
            px_, py_ = pr(1.5, 1.6, 1.5)
            cv.sprite(px_ - 4, py_ - 7, ICONS["pot"], ICON_PAL)
        panel_label(cv, x, y + 48, 59, lab, i + 1)
    return cv


def dev_charcoal_pit():
    s = Scene()
    s.fill(0, 0, 0, 4, 0, 4, "minecraft:dirt")
    s.fill(0, 1, 0, 4, 2, 4, "minecraft:dirt")
    s.fill(1, 1, 1, 3, 2, 3, "tfc:log_pile")
    s.fill(1, 3, 1, 3, 3, 3, "minecraft:dirt")
    # cut the front corner away so the piles show
    s.clear(3, 1, 3, 4, 3, 4)
    s.clear(4, 1, 0, 4, 3, 4)
    s.clear(0, 1, 4, 4, 3, 4)
    s.blocks.pop((2, 3, 2), None)
    s.sprite(2, 2, 2, "flame")
    return diagram("CHARCOAL PIT", s,
                   [(1, "LOG PILES"), (2, "DIRT ON EVERY SIDE"), (3, "LIGHT, THEN CLOSE"),
                    (4, "NO SMOKE: DIG IT OUT")],
                   badges=[(1, (3.5, 2, 3.5), 6, 0), (2, (0.5, 3, 0.5), -8, -4), (3, (2.5, 4.5, 2.5), 10, -8)])


def dev_log_pile():
    s = Scene()
    ground(s, 0, 0, 2, 2)
    s.put(1, 1, 1, "tfc:log_pile")
    s.put(2, 1, 1, "tfc:log_pile", h=0.5)

    def extras(cv, p):
        x, y = p(2.5, 2.2, 1.5)
        cv.sprite(x + 6, y - 14, ICONS["log"], ICON_PAL)

    return diagram("LOG PILE", s,
                   [(1, "SNEAK + RIGHT-CLICK"), (2, "MORE LOGS INTO IT")],
                   badges=[(1, (1.5, 2, 1.5), -10, -8), (2, (2.5, 1.5, 1.5), 12, -2)], extras=extras)


def dev_bloomery():
    s = Scene()
    ground(s, 0, 0, 4, 4, bid="minecraft:stone")
    brick = "minecraft:stone_bricks"
    # TFC multiblock tfc:bloomery (PatchouliIntegration): bloomery block in front of a one-block shaft, stone on
    # the other three sides, one more ring of stone on top. Shaft (2, 1..2, 2), bloomery facing south (2, 1, 3).
    for y in (1, 2):
        for (x, z) in ((1, 2), (3, 2), (2, 1)):
            s.put(x, y, z, brick)
    s.put(1, 1, 3, brick)
    s.put(3, 1, 3, brick)
    s.put(2, 1, 3, "tfc:bloomery")
    s.put(2, 2, 3, brick)
    s.sprite(2, 2, 2, "ore")
    return diagram("BLOOMERY", s,
                   [(1, "BLOOMERY FACES OUT"), (2, "STONE AROUND SHAFT"), (3, "ORE AND CHARCOAL")],
                   badges=[(1, (2.5, 0.6, 4.0), -4, 6), (2, (3.5, 2.6, 2.5), 12, 0), (3, (2.5, 3.7, 2.5), -14, -6)])


def dev_blast_furnace():
    s = Scene()
    ground(s, 0, 0, 4, 4, bid="minecraft:stone")
    # tfc:full_blast_furnace: crucible under the furnace, bellows beside it, rings of eight reinforced fire bricks
    # (up to five) above; two rings drawn.
    s.put(2, 1, 2, "tfc:crucible")
    s.put(2, 2, 2, "tfc:blast_furnace")
    s.put(3, 2, 2, "tfc:bellows")
    for y in (3, 4):
        for x in (1, 2, 3):
            for z in (1, 2, 3):
                if (x, z) != (2, 2):
                    s.put(x, y, z, "tfc:reinforced_fire_bricks")
    s.sprite(2, 4, 2, "ore")
    return diagram("BLAST FURNACE", s,
                   [(1, "FURNACE, TUYERE IN"), (2, "FIRE BRICK RINGS"), (3, "BELLOWS"), (4, "CRUCIBLE BELOW")],
                   badges=[(1, (2.5, 2.5, 3.0), -14, 0), (2, (3.5, 4.5, 4.0), 10, -2), (3, (4.0, 2.5, 2.5), 10, 2),
                           (4, (2.5, 1.5, 3.0), -14, 4)])


def dev_forge_anvil():
    s = Scene()
    s.fill(0, 0, 0, 5, 0, 3, "minecraft:stone")
    s.fill(0, 1, 0, 5, 1, 3, "minecraft:grass_block")
    # tfc:charcoal_forge: pile of 7 or 8 layers in a hole, stone on four sides and below, air above
    for (x, z) in ((1, 1), (3, 1), (2, 0), (2, 2)):
        s.put(x, 1, z, "minecraft:cobblestone")
    s.put(2, 1, 1, "tfc:charcoal_forge", h=0.9)
    s.put(2, 2, 0, "tfc:bellows")
    s.put(4, 2, 2, "tfc:metal/anvil/copper", h=0.7)
    return diagram("FORGE AND ANVIL", s,
                   [(1, "CHARCOAL, 7-8 LAYERS"), (2, "STONE ON FIVE SIDES"), (3, "BELLOWS, ONE UP"), (4, "ANVIL")],
                   badges=[(1, (2.5, 2.0, 1.5), 0, -6), (2, (1.5, 2.0, 1.5), -14, 2), (3, (2.5, 3.0, 0.5), 8, -6),
                           (4, (4.5, 2.7, 2.5), 10, -4)])


def dev_quern():
    s = Scene()
    ground(s, 0, 0, 2, 2, bid="minecraft:stone")
    s.put(1, 1, 1, "tfc:quern", h=0.5)

    def extras(cv, p):
        # handstone, handle and the item to grind, drawn flat on top of the base
        hx, hy = p(1.5, 1.5, 1.5)
        cv.rect(hx - 9, hy - 6, 18, 5, (122, 122, 126))
        cv.rect(hx - 9, hy - 1, 18, 2, (92, 92, 96))
        cv.rect(hx - 9, hy - 6, 18, 1, (150, 150, 154))
        cv.rect(hx + 5, hy - 13, 2, 8, (122, 82, 44))
        cv.sprite(hx - 4, hy - 16, ICONS["ore"], ICON_PAL)
        cv.badge(hx - 22, hy - 8, 2)
        cv.badge(hx + 10, hy - 16, 3)

    return diagram("QUERN", s,
                   [(1, "QUERN BASE"), (2, "HANDSTONE, ITEM IN"), (3, "TURN THE HANDLE"), (4, "POWDER ON THE BASE")],
                   badges=[(1, (1.5, 1.0, 2.0), -16, 4), (4, (2.0, 0.6, 1.5), 12, 2)], extras=extras)


def dev_barrel():
    s = Scene()
    ground(s, 0, 0, 2, 2, bid="minecraft:stone")
    s.put(1, 1, 1, "tfc:wood/barrel/oak")

    def extras(cv, p):
        hx, hy = p(1.5, 2, 1.5)
        cv.sprite(hx - 14, hy - 18, ICONS["water"], ICON_PAL)
        cv.sprite(hx + 6, hy - 18, ICONS["ore"], ICON_PAL)
        cv.badge(hx - 26, hy - 17, 1)
        cv.badge(hx + 16, hy - 17, 2)

    return diagram("BARREL", s,
                   [(1, "WATER, BRINE, TANNIN"), (2, "ITEM IN THE MIDDLE"), (3, "SNEAK + RIGHT-CLICK"),
                    (None, "SEALED, IT WORKS")],
                   badges=[(3, (2.0, 1.5, 1.5), 12, 2)], extras=extras)


DEVICES = [
    ("firepit", dev_firepit, "Firepit: one log, three sticks and straw on one block, lit with a firestarter"),
    ("pit_kiln", dev_pit_kiln, "Pit kiln in four steps: pottery, straw, logs, fire"),
    ("charcoal_pit", dev_charcoal_pit, "Charcoal pit: log piles closed in by dirt"),
    ("log_pile", dev_log_pile, "Log pile: sneak and right-click the ground with a log"),
    ("bloomery", dev_bloomery, "Bloomery: the block in front of a stone shaft, filled from the top"),
    ("blast_furnace", dev_blast_furnace, "Blast furnace with fire brick rings, bellows and crucible"),
    ("forge_anvil", dev_forge_anvil, "Charcoal forge in a stone hole, bellows and an anvil"),
    ("quern", dev_quern, "Quern with handstone and handle"),
    ("barrel", dev_barrel, "Barrel: fluid, item in the middle, sealed"),
]


# ------------------------------------------------------------------------------------------------ ponder stills
def _ponder_module():
    spec = importlib.util.spec_from_file_location("gen_ponder_schematics", DEV / "gen_ponder_schematics.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def ponder_frame(cv: Canvas, title: str):
    plate(cv, title, accent=GOLD)
    # play button and the banner at the bottom
    cv.rect(3, cv.h - 17, cv.w - 6, 14, NIGHT)
    cv.rect(3, cv.h - 18, cv.w - 6, 1, GOLD_DARK)
    label = "CLICK TO WATCH"
    tw = text_width(label) + 12
    x0 = (cv.w - tw) // 2
    tri = ["#....", "##...", "###..", "####.", "#####", "####.", "###..", "##...", "#...."]
    for r, row in enumerate(tri):
        for k, ch in enumerate(row):
            if ch == "#":
                cv.px(x0 + k, cv.h - 15 + r, GOLD)
    cv.text(x0 + 9, cv.h - 14, label, TEXT)


def ponder_still(title, plan, cut=None, turns=2, a=None, extra=None):
    cv = Canvas(128, 128)
    ponder_frame(cv, title)
    s = Scene.from_plan(plan)
    if cut:
        cut(s)
    if extra:
        extra(s)
    s = s.rotated(turns)
    render_scene(cv, s, (5, 17, 118, 92), a=a)
    return cv


def _silo_cut(s: Scene):
    # Ponder hides the earth on the north and west sides (gen_ponder_schematics: yRotation 145); do the same
    for (x, y, z) in list(s.blocks):
        if 1 <= y <= 7 and (x < 3 or z < 3) and not (3 <= x <= 9 and 3 <= z <= 9):
            s.blocks.pop((x, y, z))


def _grate_down(s: Scene):
    # the still shows the grate let down into the gate (the scene starts with it up in the slot)
    for (x, y, z) in list(s.blocks):
        if s.blocks[(x, y, z)]["kind"] == "bars":
            s.blocks.pop((x, y, z))
    for x in range(3, 6):
        for y in range(1, 4):
            s.put(x, y, 4, "minecraft:iron_bars", kind="bars")


def _silo_open(s: Scene):
    # the still shows the hatch sliding open: west leaf half way into its pocket, east leaf too
    for x in range(3, 10):
        for z in range(3, 10):
            s.blocks.pop((x, 7, z), None)
    for z in range(3, 10):
        for x in range(0, 3):
            s.put(x, 7, z, "minecraft:smooth_stone")
        for x in range(10, 14):
            s.put(x, 7, z, "minecraft:smooth_stone")


PONDERS = [
    # name, title, item for the click action, plan function name, cut, extra, caption
    ("silo_hatch", "SILO HATCH", "ad_astra:launch_pad", "silo_hatch", _silo_cut, _silo_open,
     "Ponder scene Silo Hatch: two glued leaves slide open on gantries above the launch pad"),
    ("portcullis", "PORTCULLIS", "create:rope_pulley", "portcullis", None, _grate_down,
     "Ponder scene Portcullis: an iron grate on a rope pulley over a gate"),
    ("lens_crown", "THE LENS CROWN", "firmages:star_anvil", "lens_crown", None, None,
     "Ponder scene The Lens Crown on the Star Anvil"),
    ("chords", "CHORDS", "firmages:lens_mount", "chords", None, None,
     "Ponder scene Chords: levers swing the lenses of the Lens Crown"),
    # Create's own scenes, one still per Great Works step (review 0.13.0: the steps showed the finished house)
    ("elevator", "ELEVATOR", "create:elevator_pulley", create_plan("elevator_pulley/elevator"), None, None,
     "Create Ponder scene: an elevator pulley lifts a cabin between floors with contacts"),
    ("display_link", "DISPLAY LINK", "create:display_link", create_plan("display_link"), None, None,
     "Create Ponder scene: display links write onto display boards"),
    ("clockwork_bearing", "CLOCKWORK BEARING", "create:clockwork_bearing", create_plan("clockwork_bearing"), None,
     None, "Create Ponder scene: a clockwork bearing turns the hands of a clock"),
    ("mechanical_bearing", "MECHANICAL BEARING", "create:mechanical_bearing", create_plan("mechanical_bearing/anchor"),
     None, None, "Create Ponder scene: a mechanical bearing turns the blocks glued to it"),
]
# the Create schematics copied by --refresh-ponders (assets/create/ponder/<key>.nbt in the Create jar)
CREATE_PONDER_KEYS = ("elevator_pulley/elevator", "display_link", "clockwork_bearing", "mechanical_bearing/anchor")


# ------------------------------------------------------------------------------------------------ build strands
def build_gatehouse():
    """The plan in quest order: rope bridges over the ditch, the grate on its rope pulley, the drawbridge on its bearing,
    one signal (dev/gen_quests.py WORKS 'gatehouse')."""
    s = Scene()
    W = 13
    s.fill(0, 0, 0, W - 1, 0, 9, "minecraft:grass_block")
    s.fill(0, 0, 6, W - 1, 0, 7, "minecraft:water", h=0.85)  # the ditch in front of the wall
    wall = "minecraft:stone_bricks"
    s.fill(0, 1, 2, W - 1, 6, 4, wall)
    for x in range(0, W, 2):  # crenellations
        s.put(x, 7, 4, wall)
    for x0 in (0, W - 3):  # towers
        s.fill(x0, 1, 1, x0 + 2, 9, 5, wall)
        for x in (x0, x0 + 2):
            for z in (1, 5):
                s.put(x, 10, z, wall)
    s.clear(5, 1, 2, 7, 4, 4)  # gate
    s.clear(5, 5, 3, 7, 6, 3)  # slot for the grate
    for x in range(5, 8):
        for y in range(3, 7):
            s.put(x, y, 3, "minecraft:iron_bars", kind="bars")
    s.put(6, 7, 3, "create:rope_pulley")
    s.put(7, 7, 3, "create:gearshift")
    # rope bridges: a footbridge over the ditch west of the gate
    for z in range(5, 9):
        s.put(1, 1, z, "firmages:quest/rope_bridge", h=0.25)
        s.put(2, 1, z, "firmages:quest/rope_bridge", h=0.25)
    # drawbridge lowered over the ditch, its bearing at the foot
    s.fill(5, 1, 5, 7, 1, 8, "minecraft:spruce_planks", h=0.5)
    s.put(4, 1, 5, "create:mechanical_bearing")
    s.put(9, 1, 5, "create:redstone_link", h=0.25)
    return s


def build_living_house():
    """The plan in quest order: sliding doors, lamps on a link, the display board, the lift shaft and the clock in the
    gable (dev/gen_quests.py WORKS 'house'). Cutaway: the back and the left wall stand, the front is open."""
    s = Scene()
    s.fill(0, 0, 0, 14, 0, 11, "minecraft:grass_block")
    floor = "minecraft:spruce_planks"
    wall = "minecraft:stone_bricks"
    s.fill(1, 1, 1, 11, 1, 9, floor, h=0.2)
    s.fill(1, 1, 1, 11, 8, 1, wall)
    s.fill(1, 1, 1, 1, 8, 9, wall)
    for k, y in enumerate(range(9, 13)):  # the gable over the back wall
        s.fill(1 + k + 1, y, 1, 11 - k - 1, y, 1, wall)
    s.fill(2, 5, 2, 11, 5, 9, floor, h=0.3)  # upper floor
    s.clear(9, 5, 6, 11, 5, 9)  # lift hole
    # hearth and chimney
    s.fill(3, 1, 3, 4, 1, 4, "minecraft:bricks")
    s.put(3, 2, 3, "minecraft:campfire", h=0.4)
    s.fill(3, 2, 2, 3, 8, 2, "minecraft:bricks")
    # 1 sliding doors in the south entrance
    s.fill(6, 1, 10, 7, 3, 10, "create:brass_door", h=1.0)
    # 2 lamps under the upper floor and on it, one redstone link by the door
    for x in (4, 8):
        s.put(x, 4, 5, "minecraft:redstone_lamp")
    s.put(5, 6, 3, "minecraft:redstone_lamp")
    s.put(5, 2, 10, "create:redstone_link", h=0.25)
    # 3 display board by the door
    s.fill(8, 2, 10, 9, 3, 10, "create:display_board")
    # 4 lift: elevator pulley over a glass shaft at the east
    for y in range(1, 9):
        s.put(12, y, 7, "minecraft:glass", kind="bars")
    s.put(12, 9, 7, "create:elevator_pulley")
    s.put(11, 1, 7, "create:andesite_casing", h=0.3)
    # 5 the clock in the gable: a clockwork bearing, hour hand east, minute hand up
    s.put(6, 10, 2, "create:clockwork_bearing")
    for pos in ((6, 11, 2), (6, 12, 2), (7, 10, 2)):
        s.put(*pos, "firmages:quest/clock_hand", h=1.0)
    return s


def _silo(open_: bool):
    """n7-build 1.4: a 15 x 15 shaft nine deep, the pad at the bottom; two leaves at deck level, west 7 wide and east
    8 wide, each sliding on a gantry into a pocket of its own width beside the shaft. Cut through on the south side
    of the rocket so the shaft shows."""
    s = Scene()
    N, WL, EL = 15, 7, 8           # shaft, west leaf, east leaf
    x0 = WL + 1                    # shaft starts after the west pocket and one rim block
    xs = x0 + N + EL + 1           # size along x
    zs = N + 2                     # one rim block north and south
    depth = 9
    deck = depth + 1
    s.fill(0, 0, 0, xs - 1, 0, zs - 1, "minecraft:stone")
    for y in range(1, deck + 1):
        for x in range(xs):
            for z in range(zs):
                in_shaft = x0 <= x < x0 + N and 1 <= z < 1 + N
                if in_shaft:
                    continue
                pocket = y == deck and 1 <= z < 1 + N and (1 <= x < x0 or x0 + N <= x < xs - 1)
                if pocket:
                    continue
                if y == deck:
                    s.put(x, y, z, "minecraft:grass_block")
                else:
                    lining = x0 - 1 <= x <= x0 + N and 0 <= z <= N + 1
                    s.put(x, y, z, "minecraft:stone_bricks" if lining else "minecraft:stone")
    # pad and rocket
    cx, cz = x0 + N // 2, 1 + N // 2
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            s.put(cx + dx, 1, cz + dz, "ad_astra:launch_pad", h=0.25)
    s.put(cx, 1, cz, "minecraft:iron_block")
    for y in range(2, 7):
        s.put(cx, y, cz, "minecraft:white_concrete")
    s.put(cx, 7, cz, "minecraft:red_concrete")
    s.put(cx, 8, cz, "minecraft:lightning_rod", h=0.6)
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        s.put(cx + dx, 2, cz + dz, "minecraft:red_concrete", h=0.6)
    # leaves (deck level) and the gantry shafts along the outer pocket walls
    leaf = "firmages:quest/hatch_leaf"
    for z in range(1, 1 + N):
        if open_:
            for x in range(1, x0):
                s.put(x, deck, z, leaf)
            for x in range(x0 + N, xs - 1):
                s.put(x, deck, z, leaf)
        else:
            for x in range(x0, x0 + N):
                s.put(x, deck, z, leaf)
        s.put(0, deck + 1, z, "create:gantry_shaft", h=0.4)
        s.put(xs - 1, deck + 1, z, "create:gantry_shaft", h=0.4)
    # cut away everything south of the rocket so the shaft and the pad show
    for (x, y, z) in list(s.blocks):
        if z > cz and y >= 1:
            s.blocks.pop((x, y, z))
    return s


def build_silo_closed():
    return _silo(False)


def build_silo_open():
    return _silo(True)


def bd_gatehouse():
    return diagram("THE GATEHOUSE", build_gatehouse(),
                   [(1, "ROPE BRIDGES, DITCH"), (2, "GRATE, ROPE PULLEY"), (3, "DRAWBRIDGE, BEARING"),
                    (4, "LINKS: ONE SIGNAL")],
                   badges=[(1, (1.5, 1.5, 8.0), -10, 0), (2, (6.5, 8, 3.5), 0, -8), (3, (4.5, 2, 6.0), -10, 2),
                           (4, (9.5, 1.5, 5.5), 10, 0)])


def bd_living_house():
    return diagram("THE LIVING HOUSE", build_living_house(),
                   [(1, "SLIDING DOORS"), (2, "LAMPS ON A LINK"), (3, "DISPLAY BOARD"), (4, "LIFT SHAFT"),
                    (5, "CLOCK IN THE GABLE")],
                   badges=[(1, (6.5, 3, 10.5), -12, 2), (2, (5.5, 7, 3.5), -10, -6), (3, (9.5, 3.5, 10.5), 4, 8),
                           (4, (12.5, 10, 7.5), 8, 0), (5, (6.5, 12.5, 2.5), 10, -2)])


def bd_silo_closed():
    return diagram("THE SILO: CLOSED", build_silo_closed(),
                   [(1, "15 BY 15 HATCH"), (2, "SHAFT NINE DEEP"), (3, "LAUNCH PAD")],
                   badges=[(1, (15, 11, 4), 0, -6), (2, (8, 5, 9), -12, 0), (3, (15.5, 1.3, 9), 12, 4)])


def bd_silo_open():
    return diagram("THE SILO: OPEN", build_silo_open(),
                   [(1, "LEAVES IN POCKETS"), (2, "GANTRY ON THE RIM"), (3, "ROCKET ON THE PAD")],
                   badges=[(1, (4, 11, 4), 0, -6), (2, (0.5, 11.4, 2), -8, -6), (3, (15.5, 6, 9), 12, 0)])


BUILDS = [
    ("gatehouse", bd_gatehouse, "The Gatehouse: rope bridges over the ditch, portcullis on a rope pulley, drawbridge "
                                "on a bearing, one signal"),
    ("living_house", bd_living_house, "The Living House: sliding doors, lamps on a link, display board, lift and the "
                                      "clock in the gable"),
    ("silo_closed", bd_silo_closed, "The Silo closed: a 15 by 15 hatch over a shaft nine deep"),
    ("silo_open", bd_silo_open, "The Silo open: both leaves in their pockets, the rocket on the pad"),
]


# ------------------------------------------------------------------------------------------------ build all
def build_all(only=None):
    """-> list of (key, Image, manifest entry)."""
    global COLORS
    COLORS = load_colors()
    out = []

    def add(group, name, cv, caption, click=None, hover=None):
        key = f"{group}/{name}"
        entry = {
            "path": f"{NS}:textures/quests/{group}/{name}.png",
            "file": f"kubejs/assets/{NS}/textures/quests/{group}/{name}.png",
            "texture": [cv.im.width, cv.im.height],
            "display": [cv.w, cv.h],
            "caption": caption,
        }
        if click:
            entry["click_action"] = click
        if hover:
            entry["hover"] = hover
        out.append((key, cv.im, entry))

    if only in (None, "knapping"):
        for name, material, rows, outside in KNAPPING:
            what = "stone" if material == "stone" else "clay"
            add("knapping", name, draw_knapping(name, material, rows, outside),
                f"Knapping pattern ({what}): light squares stay, dark squares are knocked away")
    if only in (None, "device"):
        for name, fn, caption in DEVICES:
            add("device", name, fn(), caption)
    if only in (None, "ponder"):
        pm = _ponder_module()
        for name, title, item, plan_fn, cut, extra, caption in PONDERS:
            plan = plan_fn() if callable(plan_fn) else getattr(pm, plan_fn)()
            cv = ponder_still(title, plan, cut=cut, extra=extra)
            ns, path = item.split(":")
            add("ponder", name, cv, caption, click=f"custom_event:{NS}:ponder/{ns}/{path}", hover="Click to watch")
    if only in (None, "build"):
        for name, fn, caption in BUILDS:
            add("build", name, fn(), caption)
    return out


def png_bytes(im: Image.Image) -> bytes:
    buf = io.BytesIO()
    im.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def manifest_of(items):
    return {"//": "Written by dev/gen_quest_images.py; path and sizes agreed in dev/quest-images.md.",
            "images": {k: e for k, _im, e in sorted(items, key=lambda t: t[0])}}


def pow2(n):
    return n > 0 and (n & (n - 1)) == 0


def check(items, only=None) -> int:
    bad = 0
    total = 0
    expected = set()
    for key, im, entry in items:
        path = ROOT / entry["file"]
        expected.add(path.resolve())
        w, h = im.size
        if not (pow2(w) and pow2(h)):
            print(f"not a power of two: {key} {w}x{h}")
            bad += 1
        if not path.exists():
            print(f"missing: {entry['file']}")
            bad += 1
            continue
        size = path.stat().st_size
        total += size
        if size > MAX_FILE:
            print(f"too large: {entry['file']} {size} bytes > {MAX_FILE}")
            bad += 1
        cur = Image.open(path).convert("RGBA")
        if cur.size != im.size or cur.tobytes() != im.convert("RGBA").tobytes():
            print(f"stale: {entry['file']}")
            bad += 1
    groups = [only] if only else list(GROUPS)
    for g in groups:
        d = OUT / g
        if d.exists():
            for f in sorted(d.glob("*.png")):
                if f.resolve() not in expected:
                    print(f"extra file (not generated): {f.relative_to(ROOT).as_posix()}")
                    bad += 1
    if total > MAX_TOTAL:
        print(f"over budget: {total} bytes > {MAX_TOTAL}")
        bad += 1
    if only is None:
        cur = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else None
        if cur != manifest_of(items):
            print(f"stale: {MANIFEST.relative_to(ROOT).as_posix()}")
            bad += 1
    known = {norm_grid(rows, outside): name for name, _m, rows, outside in KNAPPING}
    for line, grid in quest_grids():
        if grid not in known:
            print(f"dev/gen_quests.py:{line}: knapping grid without a picture (add it to KNAPPING)")
            bad += 1
    print(f"{len(items)} pictures, {total} bytes on disk, {bad} problem(s)")
    return bad


def write(items, only=None):
    total = 0
    for key, im, entry in items:
        path = ROOT / entry["file"]
        path.parent.mkdir(parents=True, exist_ok=True)
        data = png_bytes(im)
        path.write_bytes(data)
        total += len(data)
        if len(data) > MAX_FILE:
            print(f"WARNING too large: {entry['file']} {len(data)} bytes")
    if only is None:
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest_of(items), indent=2, sort_keys=True) + "\n", encoding="utf-8",
                            newline="\n")
    print(f"wrote {len(items)} pictures, {total} bytes")


def preview(items, path):
    cols = 6
    cell = 280
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (30, 30, 40, 255))
    for i, (key, im, _e) in enumerate(items):
        x, y = (i % cols) * cell + 12, (i // cols) * cell + 12
        tile = im if im.width == 256 else im.resize((256, 256), Image.NEAREST)
        sheet.alpha_composite(tile, (x, y))
    sheet.save(path)
    print(f"preview: {path}")


def refresh_ponders(mods_dir: Path):
    """Copy the Create Ponder schematics of CREATE_PONDER_KEYS (block names and positions) into
    dev/data/create_ponder_plans.json."""
    import gzip
    sys.path.insert(0, str(DEV))
    from scan_regions import read_nbt
    jar = next((j for j in sorted(mods_dir.glob("create-*.jar"))), None)
    if jar is None:
        raise SystemExit(f"no create-*.jar in {mods_dir}")
    z = zipfile.ZipFile(jar)
    plans = {}
    for key in CREATE_PONDER_KEYS:
        d = read_nbt(gzip.decompress(z.read(f"assets/create/ponder/{key}.nbt")))
        pal = [p["Name"] for p in d["palette"]]
        blocks = sorted([*b["pos"], pal[b["state"]]] for b in d["blocks"] if pal[b["state"]] != "minecraft:air")
        plans[key] = {"size": list(d["size"]), "blocks": blocks}
        print(f"{key}: {len(blocks)} blocks, size {d['size']}")
    CREATE_PONDERS_FILE.write_text(json.dumps({"//": f"Create Ponder schematics from {jar.name} "
                                                     "(gen_quest_images.py --refresh-ponders); Create is MIT-licensed.",
                                               "plans": plans}, indent=1, sort_keys=True) + "\n",
                                   encoding="utf-8", newline="\n")
    print(f"wrote {CREATE_PONDERS_FILE.relative_to(ROOT).as_posix()}")


def refresh_colors(mods_dir: Path):
    """Mean colour (opaque pixels) of block textures in the jars for every modded block id the scenes use."""
    global COLORS
    COLORS = dict(VANILLA)
    COLORS.update(HAND)
    used = set()
    old_color_of = globals()["color_of"]

    def spy(bid):
        used.add(bid.split("[")[0])
        return old_color_of(bid)

    globals()["color_of"] = spy
    try:
        build_all()
    finally:
        globals()["color_of"] = old_color_of
    want = sorted(b for b in used if not b.startswith("minecraft:") and b not in HAND)
    jars = sorted(mods_dir.glob("*.jar"))
    table = {}
    for bid in want:
        ns, path = bid.split(":")
        cands = {k: [f"assets/{ns}/textures/block/{path}{suf}.png" for suf in sufs]
                 for k, sufs in (("top", ("_top", "_end", "")), ("side", ("_side", "")))}
        got = {}
        for jar in jars:
            try:
                z = zipfile.ZipFile(jar)
            except zipfile.BadZipFile:
                continue
            names = set(z.namelist())
            for k, lst in cands.items():
                if k in got:
                    continue
                for n in lst:
                    if n in names:
                        im = Image.open(io.BytesIO(z.read(n))).convert("RGBA")
                        im = im.crop((0, 0, im.width, min(im.height, im.width)))
                        raw = im.tobytes()
                        px = [raw[i:i + 4] for i in range(0, len(raw), 4) if raw[i + 3] > 128]
                        if px:
                            got[k] = [round(sum(p[i] for p in px) / len(px)) for i in range(3)]
                        break
            if len(got) == 2:
                break
        if got:
            table[bid] = {"top": got.get("top", got.get("side")), "side": got.get("side", got.get("top"))}
            print(f"{bid}: {table[bid]}")
        else:
            print(f"{bid}: no texture found, fallback colour")
    COLORS_FILE.parent.mkdir(parents=True, exist_ok=True)
    COLORS_FILE.write_text(json.dumps({"//": "Mean colours of modded block textures (gen_quest_images.py "
                                             "--refresh-colors); vanilla and hand colours live in the script.",
                                       "blocks": table}, indent=2, sort_keys=True) + "\n", encoding="utf-8",
                           newline="\n")
    print(f"wrote {COLORS_FILE.relative_to(ROOT).as_posix()} ({len(table)} blocks)")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--only", choices=GROUPS)
    ap.add_argument("--preview")
    ap.add_argument("--refresh-colors", action="store_true")
    ap.add_argument("--refresh-ponders", action="store_true")
    ap.add_argument("--mods", type=Path)
    args = ap.parse_args()
    if args.refresh_ponders:
        if not args.mods or not args.mods.is_dir():
            ap.error("--refresh-ponders needs --mods <dir with the pack jars>")
        refresh_ponders(args.mods)
        return 0
    if args.refresh_colors:
        if not args.mods or not args.mods.is_dir():
            ap.error("--refresh-colors needs --mods <dir with the pack jars>")
        refresh_colors(args.mods)
        return 0
    items = build_all(args.only)
    if args.check:
        return 1 if check(items, args.only) else 0
    write(items, args.only)
    if args.preview:
        preview(items, args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
