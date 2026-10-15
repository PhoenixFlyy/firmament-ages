#!/usr/bin/env python
"""Assets of The Primordial, the end boss of firmages-core (mod/firmages-core/docs/primordial.md).

    python dev/gen_primordial.py                    # write geo, animations, textures (entity, item, block, effect)
    python dev/gen_primordial.py --check            # only verify: outputs exist and match what this script would write
    python dev/gen_primordial.py --preview OUT.png  # also render the model (three phases) with the projector below

Writes into mod/firmages-core/src/main/resources/assets/firmages/:
  geo/entity/primordial.geo.json               GeckoLib geometry, format 1.12.0, per-face UVs on a 128x128 atlas
  animations/entity/primordial.animation.json  the eleven animations (format 1.8.0, keyframes only, no molang)
  textures/entity/primordial.png               128x128 atlas: dark glass with gold edges, void, eye, nine Age nodes
  textures/entity/primordial_glowmask.png      emissive layer (PrimordialGlowLayer): nodes, void specks, faint gold
  textures/entity/primordial_glowmask_eye.png  emissive eye, drawn only from phase 3 on (the eye wakes when the core splits)
  textures/entity/primordial_flare.png         16x16 soft dot for the node flares (PrimordialRenderer)
  textures/item/consecrated_shard.png          16x16 shard item
  textures/block/firmament_glass.png           16x16 constellation glass (cosmetic block)
  textures/mob_effect/firmament_ward.png       18x18 effect icon

The model is the refined concept of n7-boss (scratchpad boss-concept/primordial.geo.json): the core is split into two
halves (core_a, core_b) so phase 3 can open it, each veil plate hangs on its own hinge bone (veil_N_plate) so it folds
outward around its top edge, and the halo has a tilt bone (halo) and a spin bone (halo_spin) so the ring turns in its
own plane. The entity origin is the bottom of the hitbox (6 x 7 blocks); the keel tip touches it, and the entity hovers
2.5 blocks above the floor, so melee reaches the keel and the underside.

The node geometry here is mirrored in Java by dev.firmages.core.boss.PrimordialGeometry (server hit tests and the
client beam): NODE_CENTRE, NODE_PIVOT and the shard step. --check recomputes the node positions with the full GeckoLib
transform chain and compares them with the closed form PrimordialGeometry uses.

Conventions (GeckoLib 4.9.3, verified with javap): bedrock pixels, y up, front = -z. Baking negates x of origins and
pivots and the x and y rotations; bones rotate Z * Y * X (the X rotation applies to a vertex first); animation
rotation keyframes negate x and y the same way and add to the baked rotation; position keyframes negate x.
"""
import argparse
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "dev"))
from gen_textures import BEAM_FALLBACK, PAL, rgb, scale_rgb  # noqa: E402

ASSETS = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "assets" / "firmages"
GEO = ASSETS / "geo" / "entity" / "primordial.geo.json"
ANIM = ASSETS / "animations" / "entity" / "primordial.animation.json"
TEX = ASSETS / "textures" / "entity" / "primordial.png"
GLOW = ASSETS / "textures" / "entity" / "primordial_glowmask.png"
EYE_GLOW = ASSETS / "textures" / "entity" / "primordial_glowmask_eye.png"
FLARE = ASSETS / "textures" / "entity" / "primordial_flare.png"
SHARD = ASSETS / "textures" / "item" / "consecrated_shard.png"
FGLASS = ASSETS / "textures" / "block" / "firmament_glass.png"
WARD = ASSETS / "textures" / "mob_effect" / "firmament_ward.png"
ATLAS = 128

# ---- palette (dev/gen_textures.py) ----------------------------------------------------------------------------------
GOLD = rgb(PAL["gold"][2])          # edge lines
GOLD_DARK = rgb(PAL["gold"][1])
GOLD_LIGHT = rgb(PAL["gold"][3])
GLASS = rgb(PAL["void"][2])         # #1a0a2a dark glass
GLASS_HI = rgb(PAL["violet"][0])    # #4a2a80 highlight streaks
VOID = rgb(PAL["void"][1])          # #0c0614
WHITE_VIOLET = rgb(PAL["violet"][4])  # #f4f0ff, the Age 9 colour
VIOLET_LIGHT = rgb(PAL["violet"][3])
NODES = [rgb(c) for c in BEAM_FALLBACK] + [WHITE_VIOLET]  # node_0..8 Age beams, node_9 the spire tip

# ---- model ------------------------------------------------------------------------------------------------------------
BODY_Y = 56              # body pivot: the core's centre, 3.5 blocks above the entity origin
HALO_R = 44              # shard bar inner radius
NODE_CENTRE = (44.0, 55.5, 17.0)  # node cube centre of shard_0 (bedrock px), see PrimordialGeometry
SHARD_STEP = 40          # degrees between shards (nine Ages)
HITBOX_W, HITBOX_H = 6.0, 7.0
HOVER = 2.5


def cube(origin, size, mat, rot=None, pivot=None):
    c = {"origin": list(origin), "size": list(size), "mat": mat}
    if rot:
        c["rotation"] = list(rot)
        c["pivot"] = list(pivot) if pivot else [origin[i] + size[i] / 2 for i in range(3)]
    return c


def bones():
    b = []

    def bone(name, parent, pivot, rot=None, cubes=()):
        e = {"name": name, "pivot": list(pivot), "cubes": list(cubes)}
        if parent:
            e["parent"] = parent
        if rot:
            e["rotation"] = list(rot)
        b.append(e)

    core_rot, core_piv = (45, 0, 45), (0, BODY_Y, 0)
    bone("root", None, (0, 0, 0))
    bone("body", "root", (0, BODY_Y, 0))
    bone("core", "body", (0, BODY_Y, 0))
    bone("core_a", "core", (0, BODY_Y, 0), cubes=[cube((-14, BODY_Y - 14, -14), (14, 28, 28), "core", core_rot, core_piv)])
    bone("core_b", "core", (0, BODY_Y, 0), cubes=[cube((0, BODY_Y - 14, -14), (14, 28, 28), "core", core_rot, core_piv)])
    bone("core_inner", "body", (0, BODY_Y, 0), cubes=[cube((-9, BODY_Y - 9, -9), (18, 18, 18), "void")])
    # the eye: a diamond plate in front of the core (front = -z); unlit until phase 3 (glow layer)
    bone("eye", "body", (0, BODY_Y, -21), cubes=[cube((-3, BODY_Y - 3, -22), (6, 6, 2), "eye", (0, 0, 45), (0, BODY_Y, -21))])
    # the spire above: a dark needle with the white-violet node at the tip (the shrine's beam, inverted)
    bone("spire", "body", (0, 76, 0), cubes=[
        cube((-2, 76, -2), (4, 42, 4), "spire", (0, 45, 0)),
        cube((-3, 118, -3), (6, 6, 6), "node_9", (45, 0, 45)),
    ])
    # the keel below: the slam weapon, its tip at the entity origin
    bone("keel", "body", (0, 40, 0), cubes=[
        cube((-5, 8, -5), (10, 32, 10), "keel", (0, 45, 0)),
        cube((-3, 0, -3), (6, 10, 6), "keel", (0, 45, 0)),
    ])
    # the veil: four plates around the keel, each on a hinge at its top edge (rest flare 6 degrees outward)
    for k in range(4):
        bone(f"veil_{k}", "body", (0, 44, 0), rot=(0, 45 + 90 * k, 0))
        bone(f"veil_{k}_plate", f"veil_{k}", (12, 44, 0), rot=(0, 0, -6), cubes=[cube((12, 8, -7), (2, 34, 14), "veil")])
    # the halo: tilt (code), spin (code), nine tangential shards with their Age node at the leading end
    bone("halo", "body", (0, BODY_Y, 0))
    bone("halo_spin", "halo", (0, BODY_Y, 0))
    nx, ny, nz = NODE_CENTRE
    for i in range(9):
        bone(f"shard_{i}", "halo_spin", (0, BODY_Y, 0), rot=(0, SHARD_STEP * i, 0) if i else None, cubes=[
            cube((HALO_R - 3, BODY_Y - 3, -14), (6, 6, 28), "shard"),
            cube((nx - 3, ny - 3.5, nz - 3), (6, 7, 6), f"node_{i}", (0, 45, 0), (nx, ny, nz)),
        ])
    return b


# ---- per-face UV atlas ------------------------------------------------------------------------------------------------
FACES = ("north", "south", "east", "west", "up", "down")


def face_size(size, face):
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


def pack(model):
    """Shelf-packs one tile per distinct (material, w, h); returns {(mat, w, h): (u, v)}."""
    tiles = set()
    for bn in model:
        for c in bn["cubes"]:
            for f in FACES:
                w, h = face_size(c["size"], f)
                tiles.add((c["mat"], int(math.ceil(w)), int(math.ceil(h))))
    order = sorted(tiles, key=lambda t: (-t[2], -t[1], t[0]))
    out, x, y, row = {}, 0, 0, 0
    for t in order:
        w, h = t[1], t[2]
        if x + w > ATLAS:
            x, y, row = 0, y + row + 1, 0
        if y + h > ATLAS:
            raise SystemExit(f"atlas full at {t}")
        out[t] = (x, y)
        x += w + 1
        row = max(row, h)
    return out


def geo_json(model, atlas):
    out_bones = []
    for bn in model:
        e = {"name": bn["name"]}
        if "parent" in bn:
            e["parent"] = bn["parent"]
        e["pivot"] = bn["pivot"]
        if "rotation" in bn:
            e["rotation"] = bn["rotation"]
        cubes = []
        for c in bn["cubes"]:
            cc = {"origin": c["origin"], "size": c["size"]}
            if "rotation" in c:
                cc["pivot"] = c["pivot"]
                cc["rotation"] = c["rotation"]
            uv = {}
            for f in FACES:
                w, h = face_size(c["size"], f)
                u, v = atlas[(c["mat"], int(math.ceil(w)), int(math.ceil(h)))]
                uv[f] = {"uv": [u, v], "uv_size": [w, h]}
            cc["uv"] = uv
            cubes.append(cc)
        if cubes:
            e["cubes"] = cubes
        out_bones.append(e)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.primordial", "texture_width": ATLAS, "texture_height": ATLAS,
                        "visible_bounds_width": 10, "visible_bounds_height": 10, "visible_bounds_offset": [0, 4, 0]},
        "bones": out_bones}]}


# ---- textures -----------------------------------------------------------------------------------------------------------
def paint_tile(base, glow, eye_glow, u, v, w, h, mat, rnd):
    px, gx = base.load(), (eye_glow if mat == "eye" else glow).load()

    def put(x, y, c, g=None):
        if 0 <= x < w and 0 <= y < h:
            px[u + x, v + y] = c
            if g is not None:
                gx[u + x, v + y] = g

    border = lambda x, y: x == 0 or y == 0 or x == w - 1 or y == h - 1
    if mat in ("core", "shard", "spire", "keel", "veil"):
        alpha = {"veil": 150, "core": 222}.get(mat, 236)
        for y in range(h):
            for x in range(w):
                if border(x, y) and min(w, h) > 2:
                    put(x, y, GOLD if (x + y) % 5 else GOLD_LIGHT, GOLD_DARK[:3] + (110,))
                    continue
                streak = (x + y * 2 + rnd.randint(0, 1)) % 11 in (0, 1)
                c = GLASS_HI if streak else GLASS
                n = rnd.uniform(0.85, 1.12)
                put(x, y, scale_rgb(c, n, alpha))
        if mat == "keel" and w <= h:  # the keel ridge: a gold line down its length, glowing faintly
            for y in range(1, h - 1):
                put(w // 2, y, GOLD_LIGHT, GOLD[:3] + (150,))
        if mat == "core":  # faint constellation specks inside the dark glass
            for _ in range(w * h // 60):
                put(rnd.randint(2, w - 3), rnd.randint(2, h - 3), VIOLET_LIGHT[:3] + (alpha,), VIOLET_LIGHT[:3] + (90,))
    elif mat == "void":
        for y in range(h):
            for x in range(w):
                put(x, y, scale_rgb(VOID, rnd.uniform(0.7, 1.3)))
        for _ in range(w * h // 25):
            put(rnd.randint(0, w - 1), rnd.randint(0, h - 1), WHITE_VIOLET, WHITE_VIOLET[:3] + (160,))
    elif mat == "eye":
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = max(abs(x - cx), abs(y - cy)) / max(cx, cy, 0.5)
                lit = WHITE_VIOLET if d < 0.5 else VIOLET_LIGHT
                put(x, y, rgb(PAL["void"][4]) if d < 0.75 else rgb(PAL["violet"][0]), lit)
    elif mat.startswith("node_"):
        col = NODES[int(mat.split("_")[1])]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = max(abs(x - cx) / max(cx, 0.5), abs(y - cy) / max(cy, 0.5))
                c = scale_rgb(col, 1.45) if d < 0.4 else col if d < 0.85 else scale_rgb(col, 0.55)
                put(x, y, c, c[:3] + (255,))


def textures(atlas, seed=11):
    base = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    glow = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    eye_glow = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    rnd = random.Random(seed)
    for (mat, w, h), (u, v) in sorted(atlas.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        paint_tile(base, glow, eye_glow, u, v, w, h, mat, rnd)
    return base, glow, eye_glow


def flare():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            a = max(0.0, 1 - d) ** 1.8
            p[x, y] = (255, 255, 255, int(255 * a))
    return img


def shard_item():
    rows = [
        "................",
        "..........hh....",
        ".........hlg....",
        "........hllo....",
        ".......hlmmo....",
        "......hlmmdo....",
        ".....hlmmdo.....",
        "....hlmmdo......",
        "...glmmdo.......",
        "...lmmdo........",
        "..lmmdo.........",
        "..lmdo..........",
        ".gmdo...........",
        ".ldo............",
        ".oo.............",
        "................",
    ]
    leg = {"h": WHITE_VIOLET, "l": rgb(PAL["violet"][3]), "m": rgb(PAL["violet"][2]), "d": rgb(PAL["violet"][1]),
           "o": rgb(PAL["violet"][0]), "g": GOLD_LIGHT}
    return grid(rows, leg)


def ward_icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(9, 1), (11, 7), (17, 9), (11, 11), (9, 17), (7, 11), (1, 9), (7, 7)], fill=VIOLET_LIGHT, outline=GOLD)
    d.point([(9, 9), (9, 8), (8, 9), (10, 9), (9, 10)], fill=WHITE_VIOLET)
    return img


def firmament_glass(seed=5):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                p[x, y] = GOLD_DARK if (x + y) % 3 else GOLD
            else:
                p[x, y] = scale_rgb(GLASS_HI if (x + 2 * y) % 13 == 0 else GLASS, rnd.uniform(0.9, 1.15), 150)
    stars = [(4, 4), (11, 3), (7, 8), (12, 11), (3, 12)]
    d = ImageDraw.Draw(img)
    for a, b in zip(stars, stars[1:]):
        d.line([a, b], fill=GOLD_DARK[:3] + (200,))
    for s in stars:
        p[s] = WHITE_VIOLET
    return img


def grid(rows, leg):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p[x, y] = leg[ch]
    return img


# ---- animations ---------------------------------------------------------------------------------------------------------
def kf(*pairs, easing="linear"):
    """kf((t, [x, y, z]), ...) -> keyframe map; a pair may carry its own easing as a third element."""
    out = {}
    for p in pairs:
        t, vec = p[0], p[1]
        e = p[2] if len(p) > 2 else easing
        out[f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0"] = {"vector": vec, "easing": e}
    return out


def radial(i, d):
    """Bedrock-space offset of d px outward from the centre for shard i (bone y rotation 40 i)."""
    a = math.radians(SHARD_STEP * i)
    return [round(d * math.cos(a), 3), 0, round(-d * math.sin(a), 3)]


def animations():
    rnd = random.Random(3)
    A = {}
    veil = lambda f: {f"veil_{k}_plate": {"rotation": f} for k in range(4)}
    A["idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"position": kf((0, [0, 0, 0]), (2, [0, 1.5, 0]), (4, [0, 0, 0]), easing="easeInOutSine")},
        "core": {"scale": kf((0, [1, 1, 1]), (2, [1.03, 1.03, 1.03]), (4, [1, 1, 1]), easing="easeInOutSine")},
        "core_inner": {"rotation": kf((0, [0, 0, 0]), (4, [0, 360, 0]))},
        "keel": {"rotation": kf((0, [0, 0, 0]), (4, [0, 90, 0]))},
        "spire": {"position": kf((0, [0, 0, 0]), (2, [0, -0.5, 0]), (4, [0, 0, 0]), easing="easeInOutSine")},
    }}
    A["hurt"] = {"loop": False, "animation_length": 0.5, "bones": {
        "core": {"rotation": kf((0, [0, 0, 0]), (0.1, [4, 0, -5]), (0.25, [-3, 0, 3]), (0.5, [0, 0, 0]), easing="easeOutSine")},
    }}
    A["veil_open"] = {"loop": "hold_on_last_frame", "animation_length": 0.6,
                      "bones": veil(kf((0, [0, 0, 0]), (0.6, [0, 0, -55], "easeOutBack")))}
    A["veil_close"] = {"loop": "hold_on_last_frame", "animation_length": 0.6,
                       "bones": veil(kf((0, [0, 0, -55]), (0.6, [0, 0, 0], "easeInOutCubic")))}
    # keel slam, 5 s = PrimordialEntity: telegraph 30 t, impact at 1.5 s, keel rests 40 t, rises 30 t
    A["keel_slam"] = {"loop": False, "animation_length": 5.0, "bones": {
        "keel": {"position": kf((0, [0, 0, 0]), (1.2, [0, 5, 0], "easeOutCubic"), (1.5, [0, -2, 0], "easeInCubic"),
                                (3.5, [0, -2, 0]), (5.0, [0, 0, 0], "easeInOutSine")),
                 "rotation": kf((0, [0, 0, 0]), (1.5, [0, 180, 0], "easeInCubic"), (5.0, [0, 180, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (1.2, [-6, 0, 0]), (1.5, [4, 0, 0], "easeInCubic"), (2.0, [0, 0, 0]))},
        "core": {"scale": kf((0, [1, 1, 1]), (1.5, [0.95, 0.95, 0.95]), (1.7, [1.06, 1.06, 1.06]), (2.0, [1, 1, 1]))},
    }}
    # node volley, 1.5 s: the charge (20 t) and the release
    A["volley_charge"] = {"loop": False, "animation_length": 1.5, "bones": {
        "core": {"scale": kf((0, [1, 1, 1]), (1.0, [1.12, 1.12, 1.12], "easeInQuad"), (1.1, [0.92, 0.92, 0.92]), (1.5, [1, 1, 1]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (1.0, [-5, 0, 0]), (1.15, [3, 0, 0]), (1.5, [0, 0, 0]))},
    }}
    # beam sweep, 7 s: 1 s telegraph, 6 s sweep
    A["beam_sweep"] = {"loop": False, "animation_length": 7.0, "bones": {
        "core_inner": {"rotation": kf((0, [0, 0, 0]), (7.0, [0, 1080, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (1.0, [8, 0, 0], "easeOutSine"), (6.0, [8, 0, 0]), (7.0, [0, 0, 0]))},
        "spire": {"position": kf((0, [0, 0, 0]), (1.0, [0, 2, 0]), (6.0, [0, 2, 0]), (7.0, [0, 0, 0]))},
    }}
    # the pull, 5.5 s: the veil opens as a maw for 2 s, then the slam (impact at 3.25 s)
    pull_veil = kf((0, [0, 0, 0]), (0.5, [0, 0, -75], "easeOutBack"), (2.5, [0, 0, -75]), (3.25, [0, 0, -10], "easeInCubic"),
                   (5.5, [0, 0, 0]))
    A["pull"] = {"loop": False, "animation_length": 5.5, "bones": {
        **veil(pull_veil),
        "keel": {"position": kf((0, [0, 0, 0]), (2.5, [0, 0, 0]), (3.0, [0, 4, 0], "easeOutCubic"), (3.25, [0, -2, 0], "easeInCubic"),
                                (4.25, [0, -2, 0]), (5.5, [0, 0, 0])),
                 "rotation": kf((0, [0, 0, 0]), (2.5, [0, 270, 0]), (3.25, [0, 360, 0]), (5.5, [0, 360, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (2.5, [-6, 0, 0]), (3.25, [4, 0, 0], "easeInCubic"), (3.75, [0, 0, 0]))},
    }}
    # phase 3 loop: the core stays split, the eye burns, the halo is scattered and tumbles
    split = {
        "core_a": {"position": kf((0, [-4, 0, 0]), (4, [-4, 0, 0]))},
        "core_b": {"position": kf((0, [4, 0, 0]), (4, [4, 0, 0]))},
        "core_inner": {"rotation": kf((0, [0, 0, 0]), (4, [0, 360, 0]))},
        "eye": {"scale": kf((0, [1.3, 1.3, 1.3]), (2, [1.55, 1.55, 1.55]), (4, [1.3, 1.3, 1.3]), easing="easeInOutSine")},
        "body": {"position": kf((0, [0, 0, 0]), (2, [0, 2, 0]), (4, [0, 0, 0]), easing="easeInOutSine")},
    }
    scatter = []
    for i in range(9):
        off = radial(i, 22 + rnd.uniform(0, 10))
        off[1] = round(rnd.uniform(-26, 26), 2)
        r0 = [round(rnd.uniform(-60, 60), 1), 0, round(rnd.uniform(-50, 50), 1)]
        spin = rnd.choice([-1, 1])
        scatter.append((off, r0))
        split[f"shard_{i}"] = {
            "position": kf((0, off), (4, off)),
            "rotation": kf((0, r0), (4, [r0[0] + 360 * spin, 0, r0[2]])),
        }
    A["split"] = {"loop": True, "animation_length": 4.0, "bones": split}
    # firmament shatter, 3 s: the body rises (server), the core bursts at 1.5 s when the stars fall
    A["shatter"] = {"loop": False, "animation_length": 3.0, "bones": {
        "core": {"rotation": kf((0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.5, [6, 0, -6]), (1.6, [-6, 0, 6]), (1.8, [0, 0, 0]))},
        "core_a": {"position": kf((0, [-4, 0, 0]), (1.4, [-4, 0, 0]), (1.5, [-10, 0, 0], "easeOutCubic"), (2.2, [-4, 0, 0]), (3.0, [-4, 0, 0]))},
        "core_b": {"position": kf((0, [4, 0, 0]), (1.4, [4, 0, 0]), (1.5, [10, 0, 0], "easeOutCubic"), (2.2, [4, 0, 0]), (3.0, [4, 0, 0]))},
        "eye": {"scale": kf((0, [1.3, 1.3, 1.3]), (1.5, [2.2, 2.2, 2.2]), (2.2, [1.4, 1.4, 1.4]), (3.0, [1.3, 1.3, 1.3]))},
        "spire": {"position": kf((0, [0, 0, 0]), (1.5, [0, 6, 0], "easeOutCubic"), (3.0, [0, 0, 0]))},
    }}
    # death, 3 s: the halo collapses into the core, the eye goes out, the spire tip rises as a single star
    die = {
        "core_a": {"position": kf((0, [-4, 0, 0]), (2.0, [0, 0, 0], "easeInCubic"))},
        "core_b": {"position": kf((0, [4, 0, 0]), (2.0, [0, 0, 0], "easeInCubic"))},
        "core": {"scale": kf((0, [1, 1, 1]), (2.2, [1, 1, 1]), (3.0, [0.2, 0.2, 0.2], "easeInCubic"))},
        "eye": {"scale": kf((0, [1.5, 1.5, 1.5]), (2.5, [0, 0, 0], "easeInCubic"))},
        "spire": {"position": kf((0, [0, 0, 0]), (3.0, [0, 40, 0], "easeInQuad")),
                  "scale": kf((0, [1, 1, 1]), (2.4, [1, 1, 1]), (3.0, [0, 0, 0]))},
        "keel": {"scale": kf((0, [1, 1, 1]), (2.0, [1, 1, 1]), (3.0, [0, 0, 0]))},
        "body": {"position": kf((0, [0, 0, 0]), (3.0, [0, -8, 0]))},
    }
    for i, (off, r0) in enumerate(scatter):
        inward = radial(i, -HALO_R + 6)
        die[f"shard_{i}"] = {
            "position": kf((0, off), (2.0, inward, "easeInCubic")),
            "rotation": kf((0, r0), (2.0, [0, 0, 0])),
            "scale": kf((0, [1, 1, 1]), (1.6, [1, 1, 1]), (2.2, [0, 0, 0])),
        }
    A["die"] = {"loop": "hold_on_last_frame", "animation_length": 3.0, "bones": die}
    return {"format_version": "1.8.0", "animations": A}


# ---- GeckoLib transform chain (Java space) ----------------------------------------------------------------------------
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def m_translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def m_scale(x, y, z):
    return [[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]]


def m_rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def m_ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def m_rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def m_apply(m, p):
    return tuple(sum(m[i][k] * (p[k] if k < 3 else 1) for k in range(4)) for i in range(3))


IDENT = m_translate(0, 0, 0)


def bone_matrix(bn, rot_extra=(0, 0, 0), pos=(0, 0, 0), scale=(1, 1, 1), code_rot=None):
    """prepMatrixForBone in Java space (blocks). Bedrock rotation (baked + animation), converted like GeckoLib."""
    px, py, pz = bn["pivot"]
    rx, ry, rz = [a + b for a, b in zip(bn.get("rotation", (0, 0, 0)), rot_extra)]
    jr = (math.radians(-rx), math.radians(-ry), math.radians(rz))
    if code_rot:  # GeoBone.setRotX/Y/Z from PrimordialModel (Java radians, replaces the value)
        jr = tuple(code_rot[i] if code_rot[i] is not None else jr[i] for i in range(3))
    m = m_translate(-pos[0] / 16, pos[1] / 16, pos[2] / 16)
    m = mat_mul(m, m_translate(-px / 16, py / 16, pz / 16))
    m = mat_mul(m, mat_mul(m_rz(jr[2]), mat_mul(m_ry(jr[1]), m_rx(jr[0]))))
    m = mat_mul(m, m_scale(*scale))
    m = mat_mul(m, m_translate(px / 16, -py / 16, -pz / 16))
    return m


def cube_vertices(c):
    ox, oy, oz = c["origin"]
    sx, sy, sz = c["size"]
    jo = (-(ox + sx) / 16, oy / 16, oz / 16)
    corners = [(jo[0] + dx * sx / 16, jo[1] + dy * sy / 16, jo[2] + dz * sz / 16)
               for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    if "rotation" in c:
        px, py, pz = c["pivot"]
        jp = (-px / 16, py / 16, pz / 16)
        rx, ry, rz = c["rotation"]
        r = mat_mul(m_rz(math.radians(rz)), mat_mul(m_ry(math.radians(-ry)), m_rx(math.radians(-rx))))
        m = mat_mul(m_translate(*jp), mat_mul(r, m_translate(-jp[0], -jp[1], -jp[2])))
        corners = [m_apply(m, p) for p in corners]
    return corners


def sample(track, t):
    """Value of a keyframe map at time t (eases approximated as smoothstep; linear stays linear)."""
    keys = sorted((float(k), v) for k, v in track.items())
    if t <= keys[0][0]:
        return keys[0][1]["vector"]
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1
            if b.get("easing", "linear") != "linear":
                f = f * f * (3 - 2 * f)
            return [x + (y - x) * f for x, y in zip(a["vector"], b["vector"])]
    return keys[-1][1]["vector"]


def pose(model, anims, playing, code):
    """World matrices (Java space, model origin) of every bone for the animations `playing` [(name, t)]."""
    by = {bn["name"]: bn for bn in model}
    mats = {}

    def get(name):
        if name in mats:
            return mats[name]
        bn = by[name]
        rot, pos, scl = [0, 0, 0], [0, 0, 0], [1, 1, 1]
        for an, t in playing:  # later animations win per channel, like later GeckoLib controllers
            tr = anims["animations"][an]["bones"].get(name, {})
            if "rotation" in tr:
                rot = sample(tr["rotation"], t)
            if "position" in tr:
                pos = sample(tr["position"], t)
            if "scale" in tr:
                scl = sample(tr["scale"], t)
        m = bone_matrix(bn, rot, pos, scl, code.get(name))
        parent = bn.get("parent")
        mats[name] = mat_mul(get(parent), m) if parent else m
        return mats[name]

    for bn in model:
        get(bn["name"])
    return mats


def node_closed_form(i, spin, tilt):
    """PrimordialGeometry.nodeModel: node i centre in Java model space (blocks), as the Java class computes it."""
    x, y, z = -NODE_CENTRE[0], NODE_CENTRE[1] - BODY_Y, NODE_CENTRE[2]
    a = math.radians(-SHARD_STEP * i)
    x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    x, z = x * math.cos(spin) + z * math.sin(spin), -x * math.sin(spin) + z * math.cos(spin)
    y, z = y * math.cos(tilt) - z * math.sin(tilt), y * math.sin(tilt) + z * math.cos(tilt)
    return (x / 16, (y + BODY_Y) / 16, z / 16)


def node_chain(model, i, spin, tilt):
    mats = pose(model, {"animations": {}}, [], {"halo": (tilt, None, None), "halo_spin": (None, spin, None)})
    c = model[[bn["name"] for bn in model].index(f"shard_{i}")]["cubes"][1]
    v = cube_vertices(c)
    centre = tuple(sum(p[k] for p in v) / 8 for k in range(3))
    return m_apply(mats[f"shard_{i}"], centre)


def check_nodes(model):
    worst = 0.0
    for spin in (0.0, 0.7, 2.9):
        for tilt in (0.0, math.radians(18), math.radians(35)):
            for i in range(9):
                a, b = node_chain(model, i, spin, tilt), node_closed_form(i, spin, tilt)
                worst = max(worst, max(abs(x - y) for x, y in zip(a, b)))
    return worst


# ---- preview projector --------------------------------------------------------------------------------------------------
MAT_COL = {"core": GLASS, "shard": GLASS, "spire": GLASS, "keel": GLASS, "veil": rgb(PAL["violet"][0]), "void": VOID,
           "eye": rgb(PAL["void"][4])}


def render_panel(model, anims, playing, code, title, beam_node=None, eye_lit=False, w=640, h=760):
    img = Image.new("RGB", (w, h), (10, 8, 24))
    glow = Image.new("RGB", (w, h), (0, 0, 0))
    d, g = ImageDraw.Draw(img), ImageDraw.Draw(glow)
    mats = pose(model, anims, playing, code)
    yaw = math.radians(28)
    pitch = math.radians(14)
    scale = 46.0
    cx, cy = w / 2, h * 0.62

    def proj(p):
        x, y, z = p
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
        return (cx + x * scale, cy - y * scale), z

    # floor (2.5 blocks under the origin) and the hitbox
    for r in (4, 10):
        pts = [proj((r * math.cos(a / 30 * math.pi), -HOVER, r * math.sin(a / 30 * math.pi)))[0] for a in range(61)]
        d.line(pts, fill=(60, 46, 24), width=1)
    hw = HITBOX_W / 2
    box = [(x, y, z) for x in (-hw, hw) for y in (0, HITBOX_H) for z in (-hw, hw)]
    for a in box:
        for b in box:
            if sum(1 for k in range(3) if a[k] != b[k]) == 1 and a < b:
                d.line([proj(a)[0], proj(b)[0]], fill=(52, 60, 90), width=1)
    faces = []
    for bn in model:
        for c in bn["cubes"]:
            v = [m_apply(mats[bn["name"]], p) for p in cube_vertices(c)]
            for quad in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
                pts = [v[k] for k in quad]
                depth = sum(proj(p)[1] for p in pts) / 4
                nx = [pts[1][k] - pts[0][k] for k in range(3)]
                ny = [pts[3][k] - pts[0][k] for k in range(3)]
                n = (nx[1] * ny[2] - nx[2] * ny[1], nx[2] * ny[0] - nx[0] * ny[2], nx[0] * ny[1] - nx[1] * ny[0])
                ln = math.sqrt(sum(x * x for x in n)) or 1
                light = 0.55 + 0.45 * abs((n[0] * 0.3 + n[1] * 0.8 + n[2] * -0.5) / ln)
                faces.append((depth, pts, c["mat"], light))
    faces.sort(key=lambda f: -f[0])
    for depth, pts, mat, light in faces:
        poly = [proj(p)[0] for p in pts]
        if mat.startswith("node_"):
            col = NODES[int(mat[5:])]
            d.polygon(poly, fill=col[:3])
            g.polygon(poly, fill=col[:3])
            continue
        if mat == "eye" and eye_lit:
            d.polygon(poly, fill=WHITE_VIOLET[:3])
            g.polygon(poly, fill=WHITE_VIOLET[:3])
            continue
        base = MAT_COL.get(mat, GLASS)
        col = tuple(int(min(255, x * light * 1.6)) for x in base[:3])
        d.polygon(poly, fill=col, outline=GOLD[:3] if mat in ("core", "shard", "spire", "keel", "veil") else None)
    if beam_node is not None:
        tilt, spin = code["halo"][0], code["halo_spin"][1]
        nd = node_closed_form(beam_node, spin, tilt)
        tgt = (nd[0] * 3.2, -HOVER, nd[2] * 3.2)
        col = NODES[beam_node][:3]
        g.line([proj(nd)[0], proj(tgt)[0]], fill=col, width=7)
        d.line([proj(nd)[0], proj(tgt)[0]], fill=col, width=3)
    img = Image.fromarray(np.clip(np.asarray(img, dtype=np.int32)
                                  + np.asarray(glow.filter(ImageFilter.GaussianBlur(6)), dtype=np.int32) // 2, 0, 255).astype(np.uint8))
    ImageDraw.Draw(img).text((14, 12), title, fill=(255, 224, 150))
    return img


def preview(model, anims, out):
    t18, t35, t10 = math.radians(18), math.radians(35), math.radians(10)
    panels = [
        render_panel(model, anims, [("idle", 0.0), ("veil_close", 0.6)], {"halo": (t18, None, None), "halo_spin": (None, 0.3, None)},
                     "phase 1, The Dark Before: veil closed, halo tilt 18"),
        render_panel(model, anims, [("idle", 1.0), ("veil_open", 0.6), ("beam_sweep", 3.0)],
                     {"halo": (t35, None, None), "halo_spin": (None, 1.1, None)}, "phase 2, Nine Lights: veil open, beam from node 5",
                     beam_node=5),
        render_panel(model, anims, [("split", 1.0), ("veil_open", 0.6)], {"halo": (t10, None, None), "halo_spin": (None, 2.0, None)},
                     "phase 3, Firmament Shatter: core split, eye lit", eye_lit=True),
    ]
    sheet = Image.new("RGB", (sum(p.width for p in panels), panels[0].height + 40), (10, 8, 24))
    x = 0
    for p in panels:
        sheet.paste(p, (x, 0))
        x += p.width
    ImageDraw.Draw(sheet).text((14, panels[0].height + 12),
                               "The Primordial: generated geo (dev/gen_primordial.py), GeckoLib transform chain; blue box = hitbox 6 x 7, "
                               "gold rings = floor d 4 / d 10, 2.5 blocks below the entity origin", fill=(180, 170, 210))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


# ---- main ---------------------------------------------------------------------------------------------------------------
def dumps(obj):
    return json.dumps(obj, indent=2) + "\n"


def outputs():
    model = bones()
    atlas = pack(model)
    base, glow, eye_glow = textures(atlas)
    return model, atlas, {
        GEO: dumps(geo_json(model, atlas)),
        ANIM: dumps(animations()),
        TEX: base, GLOW: glow, EYE_GLOW: eye_glow, FLARE: flare(), SHARD: shard_item(), FGLASS: firmament_glass(), WARD: ward_icon(),
    }


def same(path, value):
    if not path.exists():
        return False
    if isinstance(value, str):
        return path.read_bytes() == value.encode("utf-8")
    old = Image.open(path).convert("RGBA")
    return old.size == value.size and old.tobytes() == value.convert("RGBA").tobytes()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview", type=Path)
    a = ap.parse_args()
    model, atlas, files = outputs()
    worst = check_nodes(model)
    names = [bn["name"] for bn in model]
    cubes = sum(len(bn["cubes"]) for bn in model)
    print(f"{len(names)} bones, {cubes} cubes, {len(atlas)} atlas tiles, node closed form vs chain: {worst:.2e} blocks")
    ok = worst < 1e-6 and all(bn.get("parent", "root") in names for bn in model)
    anims = json.loads(files[ANIM])
    for an, data in anims["animations"].items():
        for bone in data["bones"]:
            if bone not in names:
                print(f"ERROR: animation {an} names unknown bone {bone}")
                ok = False
    if a.check:
        for p, v in files.items():
            if not same(p, v):
                print(f"ERROR: {p.relative_to(ROOT)} differs from the generator (run python dev/gen_primordial.py)")
                ok = False
    else:
        for p, v in files.items():
            p.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(v, str):
                p.write_bytes(v.encode("utf-8"))
            else:
                v.save(p)
        print(f"wrote {len(files)} files")
    if a.preview:
        print("preview:", preview(model, anims, a.preview))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
