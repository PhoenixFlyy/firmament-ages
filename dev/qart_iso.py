"""Firmament Ages - isometric voxel painter with real block textures (Pillow only), for dev/gen_quest_pictures.py.

Projection: screen x = (x - z) * a, screen y = (x + z) * a / 2 - y * a (camera south-east, looking down). Visible
faces: top, east (x+1, right) and south (z+1, left). Blocks are drawn in ascending x + y + z, so nearer blocks cover
farther ones. Every block is a full cube; thin blocks (bars, panes, rails) show their texture on the cube faces.
"""
from PIL import Image, ImageChops, ImageDraw, ImageEnhance

SHADE = {'top': 1.0, 'east': 0.80, 'south': 0.62}


def _shade(tex, f, alpha=1.0, tint=None):
    t = tex.convert('RGBA')
    if tint is not None:
        solid = Image.new('RGBA', t.size, tuple(tint) + (255,))
        t = Image.composite(Image.blend(t, solid, 0.55), t, t.split()[3])
    t = ImageEnhance.Brightness(t).enhance(f)
    if alpha < 1.0:
        r, g, b, a = t.split()
        t = Image.merge('RGBA', (r, g, b, a.point(lambda v: int(v * alpha))))
    return t


def _face(canvas, tex, o, e1, e2):
    """Map a texture onto the parallelogram o + s*e1 + t*e2 (s, t in 0..1)."""
    xs = [o[0], o[0] + e1[0], o[0] + e2[0], o[0] + e1[0] + e2[0]]
    ys = [o[1], o[1] + e1[1], o[1] + e2[1], o[1] + e1[1] + e2[1]]
    x0, y0 = int(min(xs)) - 1, int(min(ys)) - 1
    x1, y1 = int(max(xs)) + 2, int(max(ys)) + 2
    w, h = x1 - x0, y1 - y0
    det = e1[0] * e2[1] - e1[1] * e2[0]
    if abs(det) < 1e-9 or w <= 0 or h <= 0:
        return
    tw, th = tex.size
    ia, ib = e2[1] / det, -e2[0] / det
    ic, id_ = -e1[1] / det, e1[0] / det
    ox, oy = x0 - o[0], y0 - o[1]
    coeffs = (ia * tw, ib * tw, (ia * ox + ib * oy) * tw, ic * th, id_ * th, (ic * ox + id_ * oy) * th)
    patch = tex.transform((w, h), Image.AFFINE, coeffs, resample=Image.NEAREST)
    mask = Image.new('L', (w, h), 0)
    poly = [(o[0] - x0, o[1] - y0), (o[0] + e1[0] - x0, o[1] + e1[1] - y0),
            (o[0] + e1[0] + e2[0] - x0, o[1] + e1[1] + e2[1] - y0), (o[0] + e2[0] - x0, o[1] + e2[1] - y0)]
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    mask = ImageChops.multiply(mask, patch.split()[3])
    canvas.paste(patch, (x0, y0), mask)


def solid_tex(color):
    """A 16 px texture in one colour with a faint speckle (blocks without a texture in the jars)."""
    im = Image.new('RGBA', (16, 16), tuple(color) + (255,))
    d = ImageDraw.Draw(im)
    dark = tuple(max(0, int(c * 0.85)) for c in color)
    for i in range(0, 16, 4):
        d.point([(i, (i * 7) % 16), ((i * 5 + 3) % 16, i + 1)], fill=dark + (255,))
    return im


def draw_cube(canvas, top_vertex, a, top, east, south, alpha=1.0, tint=None, outline=None):
    """top_vertex: screen position of the top face's back corner (x, y + 1, z)."""
    X, Y = top_vertex
    ex, ez = (a, a / 2.0), (-a, a / 2.0)
    _face(canvas, _shade(top, SHADE['top'], alpha, tint), (X, Y), ex, ez)
    _face(canvas, _shade(east, SHADE['east'], alpha, tint), (X + ex[0], Y + ex[1]), ez, (0.0, a))
    _face(canvas, _shade(south, SHADE['south'], alpha, tint), (X + ez[0], Y + ez[1]), ex, (0.0, a))
    if outline:
        d = ImageDraw.Draw(canvas)
        wd = max(1, int(a / 9))
        pts = [(X, Y), (X + ex[0], Y + ex[1]), (X + ex[0], Y + ex[1] + a), (X + ex[0] + ez[0], Y + ex[1] + ez[1] + a),
               (X + ez[0], Y + ez[1] + a), (X + ez[0], Y + ez[1])]
        d.line(pts + [pts[0]], fill=outline, width=wd)
        d.line([(X + ex[0], Y + ex[1]), (X + ex[0] + ez[0], Y + ex[1] + ez[1]), (X + ez[0], Y + ez[1])],
               fill=outline, width=max(1, wd - 1))
        d.line([(X + ex[0] + ez[0], Y + ex[1] + ez[1]), (X + ex[0] + ez[0], Y + ex[1] + ez[1] + a)],
               fill=outline, width=max(1, wd - 1))


def cube_icon(top, side, size=32):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw_cube(im, (size / 2.0, 0.0), size / 2.0 - 0.5, top, side, side)
    return im


def bounds(blocks, a):
    xs, ys = [], []
    for b in blocks:
        for dx in (0, 1):
            for dz in (0, 1):
                for dy in (0, 1):
                    x, y, z = b['x'] + dx, b['y'] + dy, b['z'] + dz
                    xs.append((x - z) * a)
                    ys.append((x + z) * a / 2.0 - y * a)
    return min(xs), min(ys), max(xs), max(ys)


def render(blocks, a=24, pad=16, frame=None):
    """blocks: dicts x, y, z, top, east, south [, alpha, tint, outline]. frame: (minx, miny, maxx, maxy) screen bounds
    to share between pictures of one series (so layers do not jump); default: this picture's own bounds."""
    if not blocks and frame is None:
        return Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    minx, miny, maxx, maxy = frame or bounds(blocks, a)
    W, H = int(maxx - minx) + 2 * pad, int(maxy - miny) + 2 * pad
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for b in sorted(blocks, key=lambda b: (b['x'] + b['y'] + b['z'], b['y'])):
        if 'sprite' in b:   # an upright item picture standing on the block below (bells, lamps, candles)
            sp = b['sprite'].resize((int(a * 1.25), int(a * 1.25)), Image.NEAREST)
            if b.get('outline'):
                glow = Image.new('RGBA', sp.size, b['outline'])
                sp = Image.composite(Image.blend(sp, glow, 0.35), sp, sp.split()[3])
            cx = (b['x'] + 0.5 - (b['z'] + 0.5)) * a
            cy = (b['x'] + 0.5 + b['z'] + 0.5) * a / 2.0 - b['y'] * a
            im.alpha_composite(sp, (int(cx - minx + pad - sp.width / 2), int(cy - miny + pad - sp.height * 0.92)))
            continue
        sx = (b['x'] - b['z']) * a
        sy = (b['x'] + b['z']) * a / 2.0 - (b['y'] + 1) * a
        draw_cube(im, (sx - minx + pad, sy - miny + pad), a, b['top'], b['east'], b['south'],
                  alpha=b.get('alpha', 1.0), tint=b.get('tint'), outline=b.get('outline'))
    return im
