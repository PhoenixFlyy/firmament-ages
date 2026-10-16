#!/usr/bin/env python3
"""Firmament Ages - draw every chapter of the Chronicle as the quest map would show it, from the generated files.

Reads config/ftbquests/quests/ (chapters, lang) exactly as dev/gen_quests.py wrote them and draws per chapter: the
chapter background of its theme tag, the chapter images (banner, shrine and build pictures), the dependency lines
with their curves (dep_control_pts mapped back the way FTB Quests 2101.1.36 draws them, see dev/quest_layout.py), the
quests in their shapes and sizes with item icons, and each title below its quest (in game a title shows on hover).
Quests and pictures that stay hidden until their Age dawns are drawn faded. A dashed red ring marks two quests drawn
closer than dev/quest_layout.MIN_GAP.

Writes dev/quest-preview/<chapter>.jpg and dev/quest-preview/index.html (all chapters on one page). Icons and shapes
come from the mod jars (dev/qart_textures.py); without them quests are plain discs.

Usage:  python dev/preview_quests.py [chapter file names ...]
"""
import html
import math
import os
import sys

from PIL import Image, ImageDraw, ImageEnhance, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'dev'))
sys.dont_write_bytecode = True
import qart_textures as T  # noqa: E402
import quest_layout as QL  # noqa: E402
import validate_quests as V  # noqa: E402

QDIR = os.path.join(ROOT, 'config', 'ftbquests', 'quests')
OUT = os.path.join(ROOT, 'dev', 'quest-preview')
ASSETS = os.path.join(ROOT, 'kubejs', 'assets')
U = 46                    # pixels per grid unit
GOLD = (232, 197, 106)
CREAM = (242, 230, 201)
OUTLINE = {'entry': GOLD, 'keystone': GOLD, 'goal': (255, 222, 140), 'side': (232, 140, 70),
           'explain': (169, 196, 255), 'ring': (190, 160, 255), 'guide': (190, 160, 255), 'note': GOLD,
           'info': CREAM}


def num(v, default=0.0):
    return float(v.value) if isinstance(v, V.Num) else (default if v is None else float(v))


def font(size, bold=False):
    for f in (('C:/Windows/Fonts/segoeuib.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf'),
              '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def resource(rl):
    """firmages:textures/... -> RGBA image from kubejs/assets or the jars, or None."""
    ns, path = rl.split(':', 1)
    p = os.path.join(ASSETS, ns, path)
    if os.path.isfile(p):
        return Image.open(p).convert('RGBA')
    data = T.read('assets/%s/%s' % (ns, path))
    if data is None:
        return None
    import io
    return Image.open(io.BytesIO(data)).convert('RGBA')


_shapes = {}


def shape(name, part):
    k = (name, part)
    if k not in _shapes:
        im = resource('ftbquests:textures/shapes/%s/%s.png' % (name, part))
        if im is not None and im.height > im.width:
            im = im.crop((0, 0, im.width, im.width))
        _shapes[k] = im
    return _shapes[k]


def tint(im, rgb):
    r, g, b, a = im.split()
    lum = Image.merge('RGB', (r, g, b)).convert('L')
    col = Image.composite(Image.new('RGB', im.size, rgb), Image.new('RGB', im.size, (0, 0, 0)), lum)
    col.putalpha(a)
    return col


def bezier(p0, c0, c1, p1, n=32):
    out = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        out.append((mt ** 3 * p0[0] + 3 * mt * mt * t * c0[0] + 3 * mt * t * t * c1[0] + t ** 3 * p1[0],
                    mt ** 3 * p0[1] + 3 * mt * mt * t * c0[1] + 3 * mt * t * t * c1[1] + t ** 3 * p1[1]))
    return out


def wrap(text, f, width, d):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines[:2]


def render(name, ch, lang, all_quests=None):
    all_quests = all_quests or {}
    default_size = num(ch.get('default_quest_size'), 1.0) or 1.0
    default_shape = ch.get('default_quest_shape', 'circle')
    quests = {q['id'].upper(): q for q in ch.get('quests', [])}
    hidden = set()
    for qid_, q in quests.items():   # hidden until deps complete: drawn faded, so is what depends on them alone
        if q.get('hide_until_deps_complete'):
            hidden.add(qid_)
    for _ in range(3):
        for qid_, q in quests.items():
            deps = [str(d).upper() for d in q.get('dependencies', [])]
            if deps and all(d in hidden for d in deps if d in quests) and any(d in quests for d in deps):
                hidden.add(qid_)

    def size_of(q):
        return num(q.get('size'), 0.0) or default_size

    def kind_of(q):
        tags = q.get('tags', [])
        return tags[0] if tags else 'step'

    xs, ys = [], []
    for q in quests.values():
        r = size_of(q) * QL.CELL / 2
        x, y = num(q.get('x')), num(q.get('y'))
        xs += [x - r - 1.2, x + r + 1.2]
        ys += [y - r, y + r + 0.8]
    for im in ch.get('images', []):
        x, y, w, h = num(im['x']), num(im['y']), num(im['width']), num(im['height'])
        xs += [x - w / 2, x + w / 2]
        ys += [y - h / 2, y + h / 2]
    minx, maxx, miny, maxy = min(xs) - 1.0, max(xs) + 1.0, min(ys) - 1.0, max(ys) + 1.0
    W, H = int((maxx - minx) * U), int((maxy - miny) * U)

    def P(x, y):
        return ((x - minx) * U, (y - miny) * U)

    tags = ch.get('tags', [])
    bg = None
    for t in tags:
        bg = resource('firmages:textures/gui/quests/bg_%s.png' % t)
        if bg is not None:
            break
    if bg is not None:
        s = max(W / bg.width, H / bg.height)
        bg = bg.resize((int(bg.width * s) + 1, int(bg.height * s) + 1), Image.LANCZOS).crop((0, 0, W, H))
        canvas = ImageEnhance.Brightness(bg.convert('RGB')).enhance(0.8).convert('RGBA')
    else:
        canvas = Image.new('RGBA', (W, H), (16, 26, 62, 255))
    d = ImageDraw.Draw(canvas)
    for gx in range(int(math.floor(minx)), int(math.ceil(maxx)) + 1):   # grid dots every unit
        for gy in range(int(math.floor(miny)), int(math.ceil(maxy)) + 1):
            d.point([P(gx, gy)], fill=(255, 255, 255, 46))

    for im in sorted(ch.get('images', []), key=lambda i: num(i.get('order'), 0)):
        pic = resource(im['image'])
        if pic is None:
            continue
        w, h = int(num(im['width']) * U), int(num(im['height']) * U)
        pic = pic.resize((max(1, w), max(1, h)), Image.LANCZOS)
        if im.get('dependency'):
            pic.putalpha(pic.split()[3].point(lambda v: int(v * 0.35)))
        cx, cy = P(num(im['x']), num(im['y']))
        canvas.alpha_composite(pic, (int(cx - w / 2), int(cy - h / 2)))

    lines = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(lines)
    for qid_, q in quests.items():
        if q.get('hide_dependency_lines'):
            continue
        cps = q.get('dep_control_pts', {})
        s = QL.CP_SHIFT * size_of(q)
        for dep in q.get('dependencies', []):
            a = quests.get(str(dep).upper())
            if a is None:
                continue
            p_dep, p_q = (num(a['x']), num(a['y'])), (num(q['x']), num(q['y']))
            pts = cps.get(str(dep).upper()) or cps.get(str(dep))
            if pts:   # [first (near the dependency), second (near this quest)], stored with the size shift
                c_dep = (num(pts[0]) - s, num(pts[1]) - s)
                c_q = (num(pts[2]) - s, num(pts[3]) - s)
                curve = bezier(p_q, c_q, c_dep, p_dep)
            else:
                curve = [p_q, p_dep]
            faded = qid_ in hidden or str(dep).upper() in hidden
            px = [P(*c) for c in curve]
            ld.line(px, fill=(8, 10, 28, 90 if faded else 170), width=6, joint='curve')
            ld.line(px, fill=GOLD + ((80,) if faded else (215,)), width=3, joint='curve')
    canvas.alpha_composite(lines)

    small, bold = font(13), font(14, True)
    by_size = sorted(quests.items(), key=lambda kv: size_of(kv[1]))
    for qid_, q in by_size:
        layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        x, y = num(q['x']), num(q['y'])
        px, py = P(x, y)
        sz = int(size_of(q) * QL.CELL * U)
        kind = kind_of(q)
        shp = q.get('shape', default_shape)
        bgt, olt = shape(shp, 'background'), shape(shp, 'outline')
        if bgt is not None and shp != 'none':
            layer.alpha_composite(tint(bgt.resize((sz, sz), Image.LANCZOS), (40, 52, 104)), (int(px - sz / 2), int(py - sz / 2)))
        if olt is not None and shp != 'none':
            layer.alpha_composite(tint(olt.resize((sz, sz), Image.LANCZOS), OUTLINE.get(kind, CREAM)),
                                  (int(px - sz / 2), int(py - sz / 2)))
        if bgt is None and olt is None:
            ld.ellipse([px - sz / 2, py - sz / 2, px + sz / 2, py + sz / 2], fill=(40, 52, 104, 255),
                       outline=OUTLINE.get(kind, CREAM), width=2)
        icon_id = (q.get('icon') or {}).get('id')
        if not icon_id:
            for t in q.get('tasks', []):
                if t.get('type') == 'item':
                    icon_id = t['item']['id']
                    break
                if t.get('type') == 'observation':
                    icon_id = str(t.get('to_observe')).split('[')[0]
                    break
        ic = T.icon(icon_id, 32) if icon_id else None
        title = lang.get('quest.%s.title' % qid_, '?')
        isz = int(sz * (0.5 if kind in ('goal', 'entry', 'note') else 0.58))
        if ic is not None:
            layer.alpha_composite(ic.resize((isz, isz), Image.NEAREST), (int(px - isz / 2), int(py - isz / 2)))
        elif icon_id:   # a valid id the flat renderer cannot draw (entity models, custom loaders, a jar not in MODS)
            gf = font(int(isz * 0.8), True)
            gw = ld.textlength(title[0], font=gf)
            ld.text((px - gw / 2, py - isz * 0.5), title[0], font=gf, fill=(169, 196, 255, 200))
        else:
            ld.ellipse([px - 5, py - 5, px + 5, py + 5], fill=(255, 60, 60, 255))   # no icon at all: fix the quest
        f = bold if kind in ('goal', 'keystone', 'entry', 'ring', 'guide', 'note') else small
        ty = py + sz / 2 + 1
        for ln in wrap(title, f, 3.3 * U, ld):
            tw = ld.textlength(ln, font=f)
            ld.text((px - tw / 2 + 1, ty + 1), ln, font=f, fill=(0, 0, 0, 210))
            ld.text((px - tw / 2, ty), ln, font=f, fill=CREAM + (240,))
            ty += f.size + 2
        if qid_ in hidden:
            layer.putalpha(layer.split()[3].point(lambda v: int(v * 0.4)))
        canvas.alpha_composite(layer)

    for ql in ch.get('quest_links', []):   # links to quests of other chapters (the Great Works plans, B2)
        x, y = num(ql.get('x')), num(ql.get('y'))
        px, py = P(x, y)
        sz = int((num(ql.get('size')) or 1.0) * QL.CELL * U)
        shp = ql.get('shape') or default_shape
        bgt, olt = shape(shp, 'background'), shape(shp, 'outline')
        if bgt is not None:
            canvas.alpha_composite(tint(bgt.resize((sz, sz), Image.LANCZOS), (40, 52, 104)), (int(px - sz / 2), int(py - sz / 2)))
        if olt is not None:
            canvas.alpha_composite(tint(olt.resize((sz, sz), Image.LANCZOS), (169, 196, 255)), (int(px - sz / 2), int(py - sz / 2)))
        target = all_quests.get(str(ql.get('linked_quest')).upper())
        ic = T.icon(target['icon']['id'], 32) if target and target.get('icon') else None
        if ic is not None:
            isz = int(sz * 0.58)
            canvas.alpha_composite(ic.resize((isz, isz), Image.NEAREST), (int(px - isz / 2), int(py - isz / 2)))
        t = lang.get('quest_link.%s.title' % ql['id'].upper(), '?')
        ty = py + sz / 2 + 1
        for ln in wrap(t, small, 3.3 * U, d):
            tw = d.textlength(ln, font=small)
            d.text((px - tw / 2 + 1, ty + 1), ln, font=small, fill=(0, 0, 0, 210))
            d.text((px - tw / 2, ty), ln, font=small, fill=(169, 196, 255, 240))
            ty += small.size + 2

    # quests drawn too close (should never happen: the layout relaxes them apart)
    items = list(quests.values())
    close = 0
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            a, b = items[i], items[j]
            dist = math.hypot(num(a['x']) - num(b['x']), num(a['y']) - num(b['y']))
            if dist < (size_of(a) + size_of(b)) * QL.CELL / 2 + QL.MIN_GAP:
                close += 1
                for q in (a, b):
                    cx, cy = P(num(q['x']), num(q['y']))
                    r = size_of(q) * QL.CELL * U / 2 + 4
                    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 60, 60, 255), width=3)
    title = lang.get('chapter.%s.title' % ch['id'].upper(), name)
    stats = dict(quests=len(quests), width=round(maxx - minx - 2, 1), height=round(maxy - miny - 2, 1), close=close,
                 hidden=len(hidden), title=title)
    os.makedirs(OUT, exist_ok=True)
    canvas.convert('RGB').save(os.path.join(OUT, name + '.jpg'), quality=86, optimize=True)
    return stats


def chapter_order(chapters, groups):
    gorder = {g['id'].upper(): i for i, g in enumerate(groups)}

    def key(item):
        name, ch = item
        g = ch.get('group', '')
        return (-1 if not g else gorder.get(g.upper(), 99), num(ch.get('order_index'), 0), name)
    return sorted(chapters.items(), key=key)


def main(argv):
    lang = V.load(os.path.join(QDIR, 'lang', 'en_us.snbt'))
    lang = {k.split('.', 1)[0] + '.' + k.split('.', 1)[1].split('.', 1)[0].upper() + '.' + k.rsplit('.', 1)[1]
            if k.count('.') == 2 else k: v for k, v in lang.items()}
    groups = V.load(os.path.join(QDIR, 'chapter_groups.snbt')).get('chapter_groups', [])
    chapters = {}
    for fn in sorted(os.listdir(os.path.join(QDIR, 'chapters'))):
        if fn.endswith('.snbt') and (not argv or fn[:-5] in argv):
            chapters[fn[:-5]] = V.load(os.path.join(QDIR, 'chapters', fn))
    if not T.available():
        print('warning: mod jars not found (%s); quests without icons' % T.MODS)
    rows = []
    all_quests = {q['id'].upper(): q for ch in chapters.values() for q in ch.get('quests', [])}
    for name, ch in chapter_order(chapters, groups):
        st = render(name, ch, lang, all_quests)
        rows.append((name, st))
        print('%-22s %3d quests  %5.1f x %4.1f units  %2d hidden  %d too close' % (
            name, st['quests'], st['width'], st['height'], st['hidden'], st['close']))
    if not argv:
        parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" '
                 'content="width=device-width, initial-scale=1"><title>Chronicle Preview</title><style>'
                 'body{margin:0;background:#070a1c;color:#f2e6c9;font:15px/1.5 system-ui,sans-serif}'
                 'main{max-width:1800px;margin:0 auto;padding:16px}h1{color:#e8c56a;font-weight:600}'
                 'section{margin:28px 0}h2{color:#e8c56a;margin:0 0 4px}p{margin:0 0 8px;opacity:.85}'
                 'img{max-width:100%;height:auto;border:1px solid #3c3650;border-radius:6px}</style></head><body><main>'
                 '<h1>The Chronicle: chapter maps</h1><p>Drawn by dev/preview_quests.py from config/ftbquests/quests. '
                 'Faded: hidden until its Age dawns. Titles show on hover in game.</p>']
        for name, st in rows:
            parts.append('<section><h2>%s</h2><p>%s.snbt: %d quests, %.1f by %.1f grid units%s</p>'
                         '<img src="%s.jpg" alt="%s map" loading="lazy"></section>' % (
                             html.escape(st['title']), name, st['quests'], st['width'], st['height'],
                             ', %d drawn too close' % st['close'] if st['close'] else '', name, html.escape(st['title'])))
        parts.append('</main></body></html>\n')
        with open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(''.join(parts))
    return 1 if any(st['close'] for _n, st in rows) else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
