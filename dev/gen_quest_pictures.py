#!/usr/bin/env python3
"""Firmament Ages - pictures for the Chronicle, rendered offline with the real block textures of the pack.

Writes into kubejs/assets/firmages/textures/gui/quests/ (a client resource the pack ships through KubeJS; FTB Quests
reads description images and chapter images from any resource location):

  shrine/ring_N.png            the shrine after the Age of ring N: older rings pale (consecrated), ring N as built
  shrine/ring_N_layer_K.png    build step K of ring N: layers below K done, layer K outlined in gold, nothing above
  shrine/ring_N_build.png      animated PNG: the ring rising layer by layer (Certain Questing Additions plays APNG;
                               any other reader shows the default image, the finished ring)
  shrine/complete.png          the whole shrine as built: every ring in its own colours, heart and plinth
  works/<name>.png             the Great Works: gatehouse, living house, silo (from the Ponder build plans of
                               dev/gen_ponder_schematics.py where one exists)

and dev/data/quest_pictures.json: the shrine build data the guides in dev/gen_quests.py print (ring names, layers,
block counts per layer, rites, offering) and the pixel size of every picture (for width/height of {image:} lines);
the whole shrine under "complete" (its size and the block totals of all rings).

Sources (read only): the shrine rings and tiers in mod/firmages-core/src/main/resources/data/firmages (parsed with
dev/shrine-viewer/render_shrine.py), the textures from the pack's mod jars (dev/qart_textures.py). Without the jars
the blocks are drawn in flat colours.

Usage:  python dev/gen_quest_pictures.py [--check]
  --check  exit 1 if dev/data/quest_pictures.json would change (pictures are not compared byte for byte)
"""
import argparse
import json
import os
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'dev', 'shrine-viewer'))
sys.path.insert(0, os.path.join(ROOT, 'dev'))
sys.dont_write_bytecode = True
import render_shrine as RS  # noqa: E402
import qart_iso as ISO  # noqa: E402
import qart_textures as T  # noqa: E402

DATA_ROOT = Path(ROOT) / 'mod' / 'firmages-core' / 'src' / 'main' / 'resources' / 'data' / 'firmages'
LANG_FILE = Path(ROOT) / 'mod' / 'firmages-core' / 'src' / 'main' / 'resources' / 'assets' / 'firmages' / 'lang' / 'en_us.json'
PIC_DIR = os.path.join(ROOT, 'kubejs', 'assets', 'firmages', 'textures', 'gui', 'quests')
DATA_JSON = os.path.join(ROOT, 'dev', 'data', 'quest_pictures.json')
PIC_W = 400                 # target width of a shrine picture in pixels

GOLD = (232, 197, 106, 255)
PALE = (226, 230, 238)      # older rings: the consecrated sky marble, roughly

# material -> block whose textures stand for it (a tag has no single texture)
MAT_BLOCK = {
    'hearth_stones': 'tfc:rock/cobble/granite', 'hearth_posts': 'minecraft:oak_log', 'thatch': 'tfc:thatch',
    'sanctum_bricks': 'tfc:rock/bricks/granite', 'bronze_blocks': 'tfc:metal/block/bronze',
    'smooth_stones': 'tfc:rock/smooth/granite', 'iron_bars': 'tfc:metal/bars/wrought_iron',
    'sourcestone': 'ars_nouveau:gilded_sourcestone_large_bricks', 'otherstone': 'occultism:otherstone',
    'source_gem_blocks': 'ars_nouveau:source_gem_block', 'coke_bricks': 'immersiveengineering:cokebrick',
    'heavy_engineering': 'immersiveengineering:heavy_engineering',
    'steel_scaffolding': 'immersiveengineering:steel_scaffolding_standard',
    'aluminium_blocks': 'immersiveengineering:sheetmetal_aluminum', 'red_steel_blocks': 'tfc:metal/block/red_steel',
    'hv_capacitors': 'immersiveengineering:capacitor_hv', 'alu_scaffolding': 'immersiveengineering:alu_scaffolding_standard',
    'quartz_blocks': 'ae2:quartz_block', 'steel_casings': 'mekanism:steel_casing', 'quartz_glass': 'ae2:quartz_glass',
    'fluix_blocks': 'ae2:fluix_block', 'steel_plating': 'ad_astra:steel_plating', 'desh_blocks': 'ad_astra:desh_block',
    'ostrum_blocks': 'ad_astra:ostrum_block', 'calorite_blocks': 'ad_astra:calorite_block',
    'quantum_casings': 'mekanism:sps_casing', 'awakened_draconium': 'draconicevolution:awakened_draconium_block',
}
# materials that are not full blocks: drawn as their item standing upright
SPRITES = {'bells': 'tfc:bronze_bell', 'iron_lamps': 'tfc:metal/lamp/wrought_iron', 'candles': 'tfc:candle',
           'electric_lanterns': 'immersiveengineering:electric_lantern', 'floodlights': 'immersiveengineering:floodlight'}
# names the guides print (player text: no mod names)
MAT_NAME = {
    'hearth_stones': 'cobblestone of any rock', 'hearth_posts': 'logs of any tree', 'thatch': 'thatch', 'sanctum_bricks': 'rock bricks',
    'bronze_blocks': 'bronze blocks', 'bells': 'bell, bronze or brass', 'smooth_stones': 'smooth rock',
    'iron_bars': 'iron bars', 'iron_lamps': 'iron lamps', 'sourcestone': 'gilded sourcestone',
    'otherstone': 'otherstone', 'source_gem_blocks': 'source gem blocks', 'candles': 'candles',
    'coke_bricks': 'coke bricks', 'heavy_engineering': 'heavy engineering blocks',
    'steel_scaffolding': 'steel scaffolding', 'electric_lanterns': 'electric lanterns',
    'aluminium_blocks': 'aluminium blocks', 'red_steel_blocks': 'red steel blocks', 'hv_capacitors': 'HV capacitors',
    'alu_scaffolding': 'aluminium scaffolding', 'floodlights': 'floodlights', 'quartz_blocks': 'quartz blocks',
    'steel_casings': 'steel casings', 'quartz_glass': 'quartz glass', 'fluix_blocks': 'fluix blocks',
    'steel_plating': 'steel plating', 'desh_blocks': 'desh blocks', 'ostrum_blocks': 'ostrum blocks',
    'calorite_blocks': 'calorite blocks', 'quantum_casings': 'SPS casings or fusion reactor frames',
    'awakened_draconium': 'awakened draconium blocks', RS.PLINTH_BLOCK: 'Offering Plinth',
    RS.HEART_BLOCK: 'Shrine Heart',
}
NUM = ['no', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve']


def count_text(n):
    return NUM[n] if n < len(NUM) else '{:,}'.format(n)


# ------------------------------------------------------------------------------------------------ textures
_faces = {}


def hex_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def faces_for(mat):
    """(top, side) textures for a shrine material; a flat colour if the jars do not have it."""
    if mat.key not in _faces:
        f = None
        for bid in [MAT_BLOCK.get(mat.key)] + [e for e in mat.examples if not e.startswith('#')]:
            if bid:
                f = T.block_faces(bid)
                if f:
                    break
        if f is None:
            t = ISO.solid_tex(hex_rgb(mat.color))
            f = (t, t)
        _faces[mat.key] = f
    return _faces[mat.key]


def block_faces(bid, color=(140, 140, 140)):
    f = T.block_faces(bid)
    if f is None:
        t = ISO.solid_tex(color)
        f = (t, t)
    return f


def grass_faces():
    top = T.texture('minecraft:block/grass_block_top')
    side = T.texture('minecraft:block/dirt')
    if top is None or side is None:
        return ISO.solid_tex((96, 128, 64)), ISO.solid_tex((110, 80, 50))
    top = ImageChops.multiply(top.convert('RGBA'), Image.new('RGBA', top.size, (125, 170, 90, 255)))
    return top, side


def cube(x, y, z, faces, **kw):
    top, side = faces
    d = dict(x=x, y=y, z=z, top=top, east=side, south=side)
    d.update(kw)
    return d


def quantize(im):
    """256-colour palette image with transparency (a quarter of the bytes; Minecraft and ImageIO read it)."""
    return im.convert('RGBA').quantize(255, method=Image.Quantize.FASTOCTREE)


def save_png(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    quantize(im).save(path, optimize=True)
    return [im.width, im.height]


def save_apng(frames, durations, path):
    """Animated PNG for Certain Questing Additions, which plays only files named *.apng.png (javap ApngTextureManager
    .isAPngPath). Frame 0 is also the default image every other reader shows. One palette for all frames: APNG has
    one PLTE."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    w, h = frames[0].size
    sheet = Image.new('RGBA', (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * h))
    sheet = quantize(sheet)
    q = [sheet.crop((0, i * h, w, (i + 1) * h)) for i in range(len(frames))]
    q[0].save(path, save_all=True, append_images=q[1:], duration=durations, loop=0, disposal=0, blend=0)
    return [frames[0].width, frames[0].height]


def fit_width(im, w):
    if im.width <= w:
        return im
    return im.resize((w, int(im.height * w / im.width)), Image.LANCZOS)


# ------------------------------------------------------------------------------------------------ shrine
def load_rings():
    lang = RS.load_lang(DATA_ROOT)
    if not lang and LANG_FILE.is_file():
        lang = json.load(open(LANG_FILE, encoding='utf-8'))
    materials, notes, rings = {}, [], []
    for n in RS.discover_ring_numbers(DATA_ROOT):
        r = RS.parse_ring(n, DATA_ROOT, lang, materials, notes)
        if r is not None:
            rings.append(r)
    return rings, materials, lang


def rite_lines(n, lang):
    """The ring's rites as the guide prints them (from the tier file and the mod's lang hints)."""
    tier = json.load(open(DATA_ROOT / 'firmages_shrine' / 'tier' / ('ring_%d.json' % n), encoding='utf-8'))
    out = []
    for rite in tier.get('rites', []):
        t = rite.get('type')
        if t == 'players_praying':
            out.append('At least %s of you pray together.' % count_text(int(rite.get('min', 2))))
        elif t == 'energy':
            out.append('Charge the four capacitors of the crown with {:,} FE together.'.format(int(rite['amount'])))
        elif t == 'energy_consume':
            out.append('Feed {:,} FE into the heart; Caelum takes it.'.format(int(rite['amount'])))
        elif t == 'relic_returned':
            out.append('The lent Awakened Keystone rests on its plinth again.')
        else:
            text = lang.get(rite.get('hint', ''), '')
            if '%' in text or not text:
                text = RS.RITE_FALLBACKS.get(t, 'Complete the rite.')
            out.append(text)
    return out


def shrine_scene_tools(materials, cumul):
    """Ground slab and painter-block builder for a shrine scene; n is the ring drawn as built (older rings pale),
    None draws every ring as built."""
    grass = grass_faces()
    by_key = {m.key: m for m in materials.values()}
    xs = [b[0] for b in cumul]
    zs = [b[2] for b in cumul]
    gx, gz = (min(xs) - 1, max(xs) + 1), (min(zs) - 1, max(zs) + 1)
    a = max(7.0, min(26.0, PIC_W / float((gx[1] - gx[0] + 1) + (gz[1] - gz[0] + 1))))
    ground = [cube(x, -1, z, grass) for x in range(gx[0], gx[1] + 1) for z in range(gz[0], gz[1] + 1)]

    def block(x, y, z, key, rn, n, **kw):
        mat = by_key[key]
        if n is not None and rn < n and key not in (RS.HEART_BLOCK, RS.PLINTH_BLOCK):
            kw['tint'] = PALE
        if key in SPRITES:
            spr = T.icon(SPRITES[key], 32)
            if spr is not None:
                return dict(x=x, y=y, z=z, sprite=spr, outline=kw.get('outline'))
        return cube(x, y, z, faces_for(mat), **kw)
    return a, ground, block


def complete_picture(rings, materials, write=True):
    """The finished shrine: all rings as built (no pale tint), heart and plinth; same scale rule as the ring pictures."""
    cumul = RS.cumulative_blocks(rings, max(r.n for r in rings), [])
    a, ground, block = shrine_scene_tools(materials, cumul)
    scene = list(ground) + [block(x, y, z, key, rn, None) for x, y, z, key, rn in cumul]
    totals = {}
    for b in cumul:
        totals[b[3]] = totals.get(b[3], 0) + 1
    info = {'count': len(cumul), 'pictures': {},
            'blocks': [[MAT_NAME.get(key, RS.humanize(key).lower()), c]
                       for key, c in sorted(totals.items(), key=lambda kv: (-kv[1], kv[0]))]}
    if write:
        im = ISO.render(scene, a, frame=ISO.bounds(scene, a))
        info['pictures']['complete'] = save_png(im, os.path.join(PIC_DIR, 'shrine', 'complete.png'))
    return info


def shrine_pictures(rings, materials, lang, offerings, write=True):
    out = {}
    for ring in rings:
        n = ring.n
        cumul = RS.cumulative_blocks(rings, n, [])
        new = [b for b in cumul if b[4] == n and b[3] not in (RS.HEART_BLOCK,)]
        layers = sorted(set(b[1] for b in new))
        a, ground, block = shrine_scene_tools(materials, cumul)

        def scene(upto=None, highlight=None):
            bl = list(ground)
            for x, y, z, key, rn in cumul:
                if rn == n and key != RS.HEART_BLOCK and upto is not None and y > upto:
                    continue
                hl = rn == n and highlight is not None and y == highlight and key != RS.HEART_BLOCK
                bl.append(block(x, y, z, key, rn, n, outline=GOLD if hl else None))
            return bl

        frame = ISO.bounds(scene(), a)
        base = os.path.join(PIC_DIR, 'shrine')
        finished = ISO.render(scene(), a, frame=frame)
        info = {'n': n, 'name': ring.display_name, 'age': 'age_%d' % n, 'grants': ring.grants,
                'offering': offerings.get('age_%d' % n), 'rites': rite_lines(n, lang), 'layers': [],
                'count': len(new), 'pictures': {}}
        if write:
            info['pictures']['ring'] = save_png(finished, os.path.join(base, 'ring_%d.png' % n))
        frames = [ISO.render(scene(upto=layers[0] - 1), a, frame=frame)]
        for k, y in enumerate(layers):
            counts = {}
            for b in new:
                if b[1] == y:
                    counts[b[3]] = counts.get(b[3], 0) + 1
            info['layers'].append({'y': y, 'blocks': [[MAT_NAME.get(key, RS.humanize(key).lower()), c]
                                                      for key, c in sorted(counts.items(), key=lambda kv: -kv[1])]})
            step = ISO.render(scene(upto=y, highlight=y), a, frame=frame)
            frames.append(ISO.render(scene(upto=y), a, frame=frame))
            if write:
                info['pictures']['layer_%d' % (k + 1)] = save_png(step, os.path.join(base, 'ring_%d_layer_%d.png' % (n, k + 1)))
        totals = {}
        for b in new:
            totals[b[3]] = totals.get(b[3], 0) + 1
        info['blocks'] = [[MAT_NAME.get(key, RS.humanize(key).lower()), c]
                          for key, c in sorted(totals.items(), key=lambda kv: -kv[1])]
        if write:   # finished (held), then the ring rising from nothing; the last layer is the finished ring again
            seq = [finished] + frames[:-1]
            durations = [2600, 700] + [900] * (len(seq) - 2)
            info['pictures']['build'] = save_apng(seq, durations, os.path.join(base, 'ring_%d_build.apng.png' % n))
        out[str(n)] = info
    return out


# ------------------------------------------------------------------------------------------------ Great Works
def plan_blocks(plan, skip=(), moved=None, skip_pos=()):
    """Blocks of a dev/gen_ponder_schematics.Plan as painter cubes; moved: {(x, y, z): (x2, y2, z2)}."""
    out = []
    for (x, y, z), (name, _props, _nbt) in plan.blocks.items():
        if name in skip or (x, y, z) in skip_pos:
            continue
        x2, y2, z2 = (moved or {}).get((x, y, z), (x, y, z))
        if name == 'minecraft:grass_block':
            out.append(cube(x2, y2, z2, grass_faces()))
        else:
            out.append(cube(x2, y2, z2, block_faces(name)))
    return out


def works_pictures(write=True):
    import gen_ponder_schematics as GP
    out = {}
    base = os.path.join(PIC_DIR, 'works')
    # gatehouse: the portcullis plan with a ditch before the gate (review 0.13.0: the pictures showed no bridge).
    # Open: grate up in its slot, drawbridge deck down over the ditch. Shut: grate down in the gate, deck raised
    # against the gate. The rope pulley sits on the wall top, the bearing at the bridge foot.
    p = GP.portcullis()
    grate = {pos for pos, (name, _p, _n) in p.blocks.items() if name == 'minecraft:iron_bars'}
    ditch = {(x, 0, z) for x in range(9) for z in (6, 7)}
    open_ = gate_ditch(plan_blocks(p, skip_pos=ditch), deck_down=True)
    shut = gate_ditch(plan_blocks(p, skip_pos=ditch, moved={pos: (pos[0], pos[1] - 3, pos[2]) for pos in grate}),
                      deck_down=False)
    a = 22
    frame = ISO.bounds(open_ + shut, a)
    for name, bl in (('gatehouse_open', open_), ('gatehouse_shut', shut)):
        im = ISO.render(bl, a, frame=frame)
        if write:
            out[name] = save_png(fit_width(im, 360), os.path.join(base, name + '.png'))
    # silo: the hatch plan closed, and open with the leaves in their pockets (west 3..5 -> 0..2, east 6..9 -> 10..13)
    s = GP.silo_hatch()
    leaves = {pos for pos, (name, _p, _n) in s.blocks.items() if name == 'minecraft:smooth_stone' and pos[1] == 7}
    moved = {pos: ((pos[0] - 3) if pos[0] <= 5 else (pos[0] + 4), pos[1], pos[2]) for pos in leaves}
    shut = plan_blocks(s)
    opened = plan_blocks(s, moved=moved)
    a = 18
    frame = ISO.bounds(opened + shut, a)
    for name, bl in (('silo_shut', shut), ('silo_open', opened)):
        im = ISO.render(bl, a, frame=frame)
        if write:
            out[name] = save_png(fit_width(im, 360), os.path.join(base, name + '.png'))
    # living house: a two-storey hall, a lift shaft on the east side, sliding doors and lamps (drawn here: no Ponder plan)
    out.update(house_pictures(base, write))
    if write:
        work_banners()
    return out


def gate_ditch(bl, deck_down):
    """The ditch (z 6..7 in front of the wall, water one block down) and the drawbridge (x 3..5, the gate's width)
    with its mechanical bearing at the foot (x 6, z 6)."""
    water = ISO.solid_tex((52, 92, 196))
    bricks = block_faces('minecraft:stone_bricks', (120, 120, 120))
    deck = block_faces('minecraft:spruce_planks', (110, 80, 50))
    out = list(bl)
    for x in range(9):
        for z in (6, 7):
            out.append(cube(x, -2, z, bricks))
            out.append(cube(x, -1, z, (water, water), alpha=0.85))
    out.append(cube(6, -1, 6, bricks))
    out.append(cube(6, 0, 6, block_faces('create:mechanical_bearing', (176, 140, 90))))
    if deck_down:
        out += [cube(x, 0, z, deck) for x in range(3, 6) for z in (6, 7)]
    else:
        out += [cube(x, y, 6, deck) for x in range(3, 6) for y in (0, 1)]
    return out


# chapter banners of the Great Works, drawn like the Age banners (dev/gen_quest_art.py), in the accent of their Age
WORK_BANNERS = [('work_gatehouse', 'The Gatehouse', 'A Gate That Shuts on Its Own', 'age_1'),
                ('work_house', 'The Living House', 'Doors, Lifts and a Clock', 'age_2'),
                ('work_silo', 'The Silo', 'The Sky Opens from Below', 'age_7')]


def work_banners():
    import gen_quest_art as GA
    accent = {row[0]: row[4] for row in GA.AGES}
    for key, title, poetic, age in WORK_BANNERS:
        GA.save_png(GA.banner(title, poetic, accent[age]), Path(PIC_DIR) / ('banner_%s.png' % key))


def house_pictures(base, write):
    """The finished house of the quests (review 0.13.0): sliding doors, lamps on a link, a display board by the door,
    the lift shaft east of the hall and the clock in the south gable."""
    stone = block_faces('tfc:rock/smooth/granite', (150, 150, 150))
    bricks = block_faces('tfc:rock/bricks/granite', (140, 140, 140))
    planks = block_faces('minecraft:oak_planks', (160, 120, 70))
    roof = block_faces('minecraft:spruce_planks', (110, 80, 50))
    glass = block_faces('minecraft:glass', (200, 220, 230))
    lamp_on = (T.texture('minecraft:block/redstone_lamp_on') or ISO.solid_tex((250, 220, 140)),) * 2
    door = block_faces('create:brass_casing', (200, 160, 80))
    pulley = block_faces('create:elevator_pulley', (200, 160, 80))
    bearing = block_faces('create:clockwork_bearing', (196, 160, 90))
    hand = block_faces('minecraft:polished_blackstone', (40, 34, 30))
    grass = grass_faces()
    bl = [cube(x, -1, z, grass) for x in range(-1, 11) for z in range(-1, 10)]
    for y in range(0, 7):
        for x in range(0, 9):
            for z in range(0, 7):
                edge_x, edge_z = x in (0, 8), z in (0, 6)
                if y in (0, 3, 6) and not (y == 3 and 1 <= x <= 7 and 1 <= z <= 5 and not (x >= 6 and z >= 4)):
                    if y == 0 or (edge_x or edge_z) or y == 6:
                        bl.append(cube(x, y, z, stone if y == 0 else planks))
                    continue
                if edge_x or edge_z:
                    if z == 6 and x in (3, 4) and y in (1, 2):
                        continue   # the doors, slid open
                    if z == 6 and x in (2, 5, 6) and y in (1, 2, 4, 5):
                        bl.append(cube(x, y, z, glass, alpha=0.55))
                    else:
                        bl.append(cube(x, y, z, bricks))
    for k, y in enumerate(range(7, 11)):     # pitched roof, ridge north to south; the south gable in brick
        for x in range(k, 9 - k):
            for z in range(0, 7):
                bl.append(cube(x, y, z, bricks if z == 6 else roof))
    for y in (1, 2):
        bl.append(cube(2, y, 7, door))   # door leaves pushed aside
        bl.append(cube(5, y, 7, door))
    for y in range(1, 8):                # lift shaft east of the hall
        for x, z in ((9, 1), (9, 3), (10, 1), (10, 3)):
            bl.append(cube(x, y, z, bricks))
    bl.append(cube(9, 8, 2, pulley))
    bl.append(cube(9, 4, 2, planks))
    for pos in ((2, 5, 6), (5, 5, 6), (1, 2, 6)):   # lamps lit by one link
        bl.append(cube(*pos, lamp_on))
    board = (T.texture('create:block/flap_display_top') or ISO.solid_tex((60, 60, 62)),
             T.texture('create:block/flap_display_front') or ISO.solid_tex((230, 210, 140)))
    for x in (6, 7):                     # the display board by the door
        bl.append(cube(x, 2, 7, board))
    bl.append(cube(4, 8, 7, bearing))    # the clock: bearing on the gable, hour hand east, minute hand up
    for pos in ((4, 9, 7), (4, 10, 7), (5, 8, 7)):
        bl.append(cube(*pos, hand))
    a = 18
    im = ISO.render(bl, a)
    out = {}
    if write:
        out['living_house'] = save_png(fit_width(im, 360), os.path.join(base, 'living_house.png'))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='compare dev/data/quest_pictures.json, write nothing')
    args = ap.parse_args()
    if not T.available():
        print('warning: mod jars not found (%s); blocks are drawn in flat colours' % T.MODS)
    rings, materials, lang = load_rings()
    offerings = json.load(open(DATA_ROOT / 'firmages_shrine' / 'offerings.json', encoding='utf-8'))
    data = {'shrine': shrine_pictures(rings, materials, lang, offerings, write=not args.check),
            'complete': complete_picture(rings, materials, write=not args.check)}
    if args.check:
        old = json.load(open(DATA_JSON, encoding='utf-8'))
        for n, info in data['shrine'].items():
            info['pictures'] = old['shrine'].get(n, {}).get('pictures', {})
        data['complete']['pictures'] = old.get('complete', {}).get('pictures', {})
        data['works'] = old.get('works', {})
        if old != data:
            print('dev/data/quest_pictures.json is out of date: run python dev/gen_quest_pictures.py')
            return 1
        print('dev/data/quest_pictures.json is up to date')
        return 0
    data['works'] = works_pictures()
    with open(DATA_JSON, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1, sort_keys=True)
        f.write('\n')
    n_files = sum(len(i['pictures']) for i in data['shrine'].values()) + len(data['complete']['pictures']) + len(data['works'])
    print('wrote %d pictures to %s and %s' % (n_files, os.path.relpath(PIC_DIR, ROOT), os.path.relpath(DATA_JSON, ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
