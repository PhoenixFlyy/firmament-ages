"""Firmament Ages - organic layout of the Chronicle's quest maps (FTB Quests 2101.1.36), used by dev/gen_quests.py.

Age chapters: the goal is the focal point in the middle (the chapter's autofocus_id). The entry sits to the west.
Every strand leaves the entry, swings round the goal on its own orbit (over the top or under the bottom) and ends in
its keystone on a ring round the goal, like the arms of a spiral round a star. Related strands get neighbouring
slots and orbits, unrelated ones a wide gap or the other side. A strand is drawn as an arc, a meander or a circle
(its steps on a small ring hanging off the orbit). Side paths hang outward as satellites, notes form a small
constellation behind the entry, and the shrine picture with the ring quest sits on the axis between entry and goal.
Every position gets a hand-drawn jitter seeded by the quest key, then a relaxation pushes overlapping quests apart.
Dependency lines become cubic Beziers ("dep_control_pts") that follow the orbits.

Everything is deterministic: the same chapter gives the same map, quest ids never change (they come from the keys).
Other chapters (The Firmament, the Great Works) place their quests by hand through Layout and reuse the relaxation
and the curves.

FTB Quests facts used here (javap, ftb-quests-neoforge-2101.1.36 and ftb-library-neoforge-2101.1.37):
  - x, y are the centre of a quest in grid units, y grows downward (QuestPanel.alignWidgets). One grid unit is
    button size + spacing = 1.5 zoom + zoom * quest_spacing / 4 (QuestScreen); with quest_spacing 1.0 from
    kubejs/assets/ftbquests/ftb_quests_theme.txt a quest of size s is drawn 6/7 s units wide.
  - dep_control_pts: {"<dependency hex id>": [x1, y1, x2, y2]} on the dependent quest (Quest.readDepControlPoints,
    four doubles). The first point pulls the curve near the dependency, the second near the dependent
    (QuestButton.getConnectionPoints: dependent at t = 0, Pair.second weighted 3(1-t)^2 t). A point is mapped like the
    top-left corner of a button of the dependent's size (positionControlPoints), so a control point meant for grid
    position P is stored as P + 3/7 * size of the dependent, on both axes (CP_SHIFT).
"""
import hashlib
import itertools
import math

CELL = 6.0 / 7.0          # drawn diameter of a size-1 quest in grid units
CP_SHIFT = 3.0 / 7.0      # control point offset per unit of the dependent's size (see above)
GAP = 0.36                # free space kept between two drawn quests, grid units
MIN_GAP = 0.2             # what dev/validate_quests.py accepts
# Titles are drawn under the quests (dev/preview_quests.py, 13 px at 46 px per unit; in game they show on hover, but
# the preview is what the layout is judged by): a label is about 0.15 units per character, wrapped at 3.3 units, and
# 0.5 units high (0.85 for two lines). The relaxation keeps labels off other labels and discs (B2 layout polish).
LABEL_CHAR, LABEL_MAX, LABEL_H, LABEL_H2 = 0.15, 3.3, 0.5, 0.85

# size per quest kind; must match SHAPES in dev/gen_quests.py (0 there = the chapter default 1.0)
SIZE = {'entry': 2.0, 'step': 1.0, 'keystone': 1.5, 'goal': 3.0, 'side': 0.85, 'explain': 1.0, 'info': 1.0,
        'ring': 1.25, 'guide': 1.25, 'note': 1.6, 'work': 1.25}


def h01(key, salt=''):
    """Deterministic 0..1 from a key."""
    return int(hashlib.sha1((salt + '|' + key).encode('utf-8')).hexdigest()[:8], 16) / 0xFFFFFFFF


def jit(key, salt, amp):
    return (h01(key, salt) * 2 - 1) * amp


class Node:
    def __init__(self, key, kind, deps=(), hide_lines=False, size=None):
        self.key = key
        self.kind = kind
        self.size = size or SIZE[kind]
        self.deps = list(deps)
        self.hide_lines = hide_lines
        self.x = self.y = 0.0      # math coordinates, y up
        self.fixed = False         # never moved by the relaxation
        self.stiff = kind in ('keystone', 'entry', 'note')

    @property
    def r(self):
        return self.size * CELL / 2.0


class Layout:
    """Quests (math coordinates, y up), pictures and curves of one chapter."""

    def __init__(self, key):
        self.key = key
        self.nodes = {}
        self.images = []           # dict(x, y, w, h, ...) in math coordinates, centre
        self.tang = {}             # quest key -> unit tangent the lines leave and enter it along
        self.edges = []            # (dep, quest, c0, c1) in math coordinates
        self.edge_tang = {}        # (dep, quest) -> tangent the line leaves dep along (overrides tang[dep])
        self.focus = None
        self.labels = {}           # quest key -> title, for the label room in relax()

    def label_box(self, n):
        """(x0, y0, x1, y1) of the title under quest n in math coordinates (y up), or None without a title."""
        t = self.labels.get(n.key)
        if not t:
            return None
        w = min(LABEL_MAX, LABEL_CHAR * len(t) + 0.3)
        h = LABEL_H2 if LABEL_CHAR * len(t) + 0.3 > LABEL_MAX else LABEL_H
        return n.x - w / 2, n.y - n.r - h, n.x + w / 2, n.y - n.r

    def add(self, key, kind, x=0.0, y=0.0, deps=(), hide_lines=False, size=None, fixed=False):
        n = Node(key, kind, deps, hide_lines, size)
        n.x, n.y, n.fixed = x, y, fixed
        self.nodes[key] = n
        return n

    # -------------------------------------------------------------------------------------------- relaxation
    def relax(self, it=120):
        nodes = sorted(self.nodes.values(), key=lambda n: n.key)
        for _ in range(it):
            moved = False
            for a, b in itertools.combinations(nodes, 2):
                dx, dy = b.x - a.x, b.y - a.y
                d = math.hypot(dx, dy)
                need = a.r + b.r + GAP
                if d >= need:
                    continue
                if d < 1e-6:
                    dx, dy, d = jit(a.key + b.key, 'rx', 1), jit(a.key + b.key, 'ry', 1), 1.0
                    d = math.hypot(dx, dy) or 1.0
                wa = 0.0 if a.fixed else (0.35 if a.stiff else 1.0)
                wb = 0.0 if b.fixed else (0.35 if b.stiff else 1.0)
                if wa + wb == 0:
                    continue
                push = need - d
                ux, uy = dx / d, dy / d
                a.x -= ux * push * wa / (wa + wb)
                a.y -= uy * push * wa / (wa + wb)
                b.x += ux * push * wb / (wa + wb)
                b.y += uy * push * wb / (wa + wb)
                moved = True
            for a, b in itertools.combinations(nodes, 2):   # label room: no title under another title or disc
                wa = 0.0 if a.fixed else (0.35 if a.stiff else 1.0)
                wb = 0.0 if b.fixed else (0.35 if b.stiff else 1.0)
                if wa + wb == 0:
                    continue
                boxes = []
                la, lb = self.label_box(a), self.label_box(b)
                if la and lb:
                    boxes.append((la, lb))
                if la:
                    boxes.append((la, (b.x - b.r, b.y - b.r, b.x + b.r, b.y + b.r)))
                if lb:
                    boxes.append(((a.x - a.r, a.y - a.r, a.x + a.r, a.y + a.r), lb))
                for ra, rb in boxes:
                    ox = min(ra[2], rb[2]) - max(ra[0], rb[0])
                    oy = min(ra[3], rb[3]) - max(ra[1], rb[1])
                    if ox <= 0.02 or oy <= 0.02:
                        continue
                    if ox < oy * 1.6:   # push sideways (labels are wide and flat), else apart vertically
                        push, ux, uy = ox + 0.05, (1.0 if b.x >= a.x else -1.0), 0.0
                    else:
                        push, ux, uy = oy + 0.05, 0.0, (1.0 if b.y >= a.y else -1.0)
                    a.x -= ux * push * wa / (wa + wb)
                    a.y -= uy * push * wa / (wa + wb)
                    b.x += ux * push * wb / (wa + wb)
                    b.y += uy * push * wb / (wa + wb)
                    moved = True
                    break
            for im in self.images:
                for n in nodes:
                    if n.fixed or n.key == im.get('anchor'):
                        continue
                    hx, hy = im['w'] / 2 + n.r + 0.2, im['h'] / 2 + n.r + 0.2
                    dx, dy = n.x - im['x'], n.y - im['y']
                    if abs(dx) < hx and abs(dy) < hy:
                        if hx - abs(dx) < hy - abs(dy):
                            n.x = im['x'] + math.copysign(hx, dx or 1)
                        else:
                            n.y = im['y'] + math.copysign(hy, dy or 1)
                        moved = True
            if not moved:
                break
        for n in nodes:
            n.x, n.y = round(n.x * 20) / 20.0, round(n.y * 20) / 20.0

    def overlaps(self):
        """Pairs of quests drawn closer than MIN_GAP (should be none after relax)."""
        out = []
        for a, b in itertools.combinations(self.nodes.values(), 2):
            if math.hypot(a.x - b.x, a.y - b.y) < a.r + b.r + MIN_GAP:
                out.append((a.key, b.key))
        return out

    # -------------------------------------------------------------------------------------------- curves
    def curves(self, flow=()):
        """flow: (dep, quest) pairs drawn as one flowing line: they leave and enter along the stored tangents
        (cubic Hermite, handles a third of the chord). Every other visible line gets a slight seeded bend."""
        flow = set(flow)
        self.edges = []
        for n in sorted(self.nodes.values(), key=lambda n: n.key):
            if n.hide_lines:
                continue
            for d in n.deps:
                if d not in self.nodes:
                    continue   # dependency in another chapter: FTB draws no line
                a = self.nodes[d]
                pa, pb = (a.x, a.y), (n.x, n.y)
                ch = math.hypot(pb[0] - pa[0], pb[1] - pa[1])
                if ch < 1e-6:
                    continue
                hl = ch / 3.0
                ta, tb = self.edge_tang.get((d, n.key), self.tang.get(d)), self.tang.get(n.key)
                if (d, n.key) in flow and (ta or tb):
                    c0 = (pa[0] + ta[0] * hl, pa[1] + ta[1] * hl) if ta else \
                        (pa[0] + (pb[0] - pa[0]) / 3, pa[1] + (pb[1] - pa[1]) / 3)
                    c1 = (pb[0] - tb[0] * hl, pb[1] - tb[1] * hl) if tb else \
                        (pa[0] + 2 * (pb[0] - pa[0]) / 3, pa[1] + 2 * (pb[1] - pa[1]) / 3)
                else:
                    mx, my = pb[0] - pa[0], pb[1] - pa[1]
                    bend = jit(d + n.key, 'bend', 0.2)
                    nx, ny = -my * bend, mx * bend
                    c0 = (pa[0] + mx / 3 + nx, pa[1] + my / 3 + ny)
                    c1 = (pa[0] + 2 * mx / 3 + nx, pa[1] + 2 * my / 3 + ny)
                self.edges.append((d, n.key, c0, c1))

    # -------------------------------------------------------------------------------------------- export
    def pos(self, key):
        """FTB grid position (y down) of a quest."""
        n = self.nodes[key]
        return n.x, -n.y + 0.0

    def control_points(self, key, qid):
        """dep_control_pts of a quest: {dependency hex id: [x1, y1, x2, y2]} in the stored frame (CP_SHIFT)."""
        n = self.nodes[key]
        s = CP_SHIFT * n.size
        out = {}
        for d, q, c0, c1 in self.edges:
            if q != key:
                continue
            out[qid(d)] = [round(c0[0] + s, 3), round(-c0[1] + s, 3), round(c1[0] + s, 3), round(-c1[1] + s, 3)]
        return out

    def bbox(self):
        """(min x, min y, max x, max y) of everything drawn, FTB frame (y down)."""
        xs, ys = [], []
        for n in self.nodes.values():
            xs += [n.x - n.r, n.x + n.r]
            ys += [-n.y - n.r, -n.y + n.r]
        for im in self.images:
            xs += [im['x'] - im['w'] / 2, im['x'] + im['w'] / 2]
            ys += [-im['y'] - im['h'] / 2, -im['y'] + im['h'] / 2]
        return min(xs), min(ys), max(xs), max(ys)

    def image_pos(self, im):
        return im['x'], -im['y'] + 0.0


# ------------------------------------------------------------------------------------------------ Age chapters
R_KEY = 3.6               # keystone ring round the goal
A0, A1 = 52.0, 308.0      # keystone slots: degrees from the west, clockwise over the top (0 = west)
ASPECT = (1.22, 0.84)     # orbits are ellipses: screens are wide


def polar(alpha_deg, r):
    """alpha from the west, clockwise over the top -> math coordinates (y up)."""
    th = math.radians(180.0 - alpha_deg)
    return r * math.cos(th) * ASPECT[0], r * math.sin(th) * ASPECT[1]


def _rel(hints, a, b):
    rel = hints.get('related', {})
    return rel.get((a, b), rel.get((b, a), 0.3))


def _slots(order, names, hints):
    """Keystone slots on the ring. Gaps grow with unrelatedness; the first half of the order goes over the top, the
    rest under the bottom (balanced sides keep the trunk at the entry from bundling)."""
    n = len(order)
    if n == 1:
        return [180.0]
    w = [0.55 + 1.6 * (1.0 - _rel(hints, names[order[j]], names[order[j + 1]])) for j in range(n - 1)]
    p = [0.0]
    for x in w:
        p.append(p[-1] + x)
    k = (n + 1) // 2
    mid = (p[k - 1] + p[k]) / 2.0 if k < n else p[-1]
    out = []
    for v in p:
        if v <= mid:
            out.append(A0 + (180.0 - A0) * (v / mid if mid else 0))
        else:
            out.append(180.0 + (A1 - 180.0) * ((v - mid) / ((p[-1] - mid) or 1)))
    return out


def _choose_order(key, names, lens, hints):
    if hints.get('order'):   # fixed slot order, over the top first (review 0.13.0: lanes that would braid)
        perm = tuple(names.index(n) for n in hints['order'])
        return perm, _slots(perm, names, hints)
    best = None
    for perm in itertools.permutations(range(len(names))):
        sl = _slots(perm, names, hints)
        score = sum(_rel(hints, names[perm[j]], names[perm[j + 1]]) for j in range(len(perm) - 1))
        for j, si in enumerate(perm):   # long strands take the long way round
            travel = min(sl[j], 360 - sl[j]) / 180.0
            score += 0.35 * (lens[si] / max(lens)) * travel
        if hints.get('axis') in names:   # this strand ends next to the shrine picture
            a = names.index(hints['axis'])
            score += 0.8 if perm[0] == a or perm[-1] == a else 0.0
        score += 1e-6 * h01(str(perm), key)
        if best is None or score > best[0]:
            best = (score, perm, sl)
    return best[1], best[2]


def _strand_path(alpha_end, r_entry, r_key, n=480):
    """Polyline from the entry (alpha 0) to the keystone slot. The radius follows one profile over the fraction of
    the turn, so a strand that travels farther is always farther out: strands on one side nest and never cross."""
    pts = []
    for i in range(n + 1):
        x = i / n
        r = r_key + (r_entry - r_key) * (1 - x) ** 0.9
        pts.append(polar(alpha_end * x, r))
    return pts


def _arclen(pts):
    out = [0.0]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        out.append(out[-1] + math.hypot(x1 - x0, y1 - y0))
    return out


def _at(pts, L, s):
    for i in range(1, len(L)):
        if L[i] >= s:
            t = (s - L[i - 1]) / max(1e-9, L[i] - L[i - 1])
            return (pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t), i
    return pts[-1], len(pts) - 1


def _frame(pts, i):
    a, b = pts[max(0, i - 2)], pts[min(len(pts) - 1, i + 2)]
    dx, dy = b[0] - a[0], b[1] - a[1]
    d = math.hypot(dx, dy) or 1.0
    return (-dy / d, dx / d), (dx / d, dy / d)


def age_chapter(spec, hints=None):
    """spec: key, entry, goal, strands [(name, [quest keys, keystone last])], sides [(attach, [keys])], explains
    [keys], ring (key or None), links [(key, [deps])] for quests between keystones and goal (bridge, routes), kinds
    {key: kind}, deps {key: [deps]}, hide_lines {keys}. hints: related {(a, b): 0..1}, style {name: arc|meander|
    circle}, axis (strand name), shrine (ring number for the picture, or None), r_key, r_entry, order (strand names
    in slot order from the top-west round to the bottom-west: replaces the search by relatedness). spec may carry
    titles {key: title} (label room) and satellites [(attach key, node key, size)]: extra nodes such as the links to
    the Great Works, hung off their quest like a one-quest side path."""
    h = dict(hints or {})
    lay = Layout(spec['key'])
    lay.labels = dict(spec.get('titles') or {})
    kinds, deps = spec['kinds'], spec['deps']

    def add(k, kind=None):
        return lay.add(k, kind or kinds[k], deps=deps.get(k, ()), hide_lines=k in spec['hide_lines'])

    names = [n for n, _k in spec['strands']]
    lens = [len(k) for _n, k in spec['strands']]
    order, sl = _choose_order(spec['key'], names, lens, h)
    extra = max(0, len(names) - 4)   # five strands need wider orbits
    r_key = h.get('r_key', R_KEY + (0.9 if spec['links'] else 0.0) + 0.3 * extra)
    r_entry = h.get('r_entry', max(9.5, min(14.0, 6.0 + 0.62 * max(lens) + 1.0 * extra)))

    goal = add(spec['goal'])
    goal.fixed = True
    lay.focus = spec['goal']
    entry = add(spec['entry'])
    entry.x, entry.y = -r_entry * ASPECT[0], 0.0
    entry.fixed = True
    flow = []
    for j, si in enumerate(order):
        a_end = sl[j]
        up = a_end <= 180
        pts = _strand_path(a_end if up else -(360 - a_end), r_entry, r_key)
        L = _arclen(pts)
        keys = spec['strands'][si][1]
        style = h.get('style', {}).get(names[si], 'arc')
        n_q = len(keys)
        # spread the strand over its whole orbit: the first quest 2.9 units from the entry, the keystone at the end
        # (a long empty stretch after the entry would draw one long line straight across the inner orbits)
        # strands on one side leave the entry staggered (inner orbit first), so their first quests do not bunch
        side = sorted(sl[jj] if up else 360 - sl[jj] for jj in range(len(order)) if (sl[jj] <= 180) == up)
        d0 = 2.9 + 1.1 * side.index(a_end if up else 360 - a_end)
        sp = max(1.45, min(4.0, (L[-1] - d0) / (n_q - 1))) if n_q > 1 else 0.0
        pos = []
        for k in range(n_q):
            p, i = _at(pts, L, max(0.0, L[-1] - sp * (n_q - 1 - k)))
            pos.append((p, i, _frame(pts, i)[1]))
        same = [sl[jj] for jj in range(len(order)) if (sl[jj] <= 180) == up]
        outer = (a_end == max(same)) if up else (a_end == min(same))
        if style == 'circle' and not outer:
            style = 'meander'   # a loop on an inner orbit would cross the outer strand's line
        if style == 'meander' and n_q >= 3:
            out = []
            for k, (p, i, t) in enumerate(pos[:-1]):
                nrm, _t = _frame(pts, i)
                amp = 0.7 * math.sin(math.pi * (k + 0.5) / max(1, n_q - 1)) * (1 if k % 2 == 0 else -1)
                out.append(((p[0] + nrm[0] * amp, p[1] + nrm[1] * amp), i, t))
            pos = out + [pos[-1]]
        elif style == 'circle' and n_q >= 5:
            # the last steps (three to five) sit on a small loop that hangs outward off the orbit near the keystone;
            # the first step or more follow the orbit, so the line into the loop stays short and crosses nothing
            steps = n_q - 1
            n_loop = min(5, steps - 1)
            n_arc = steps - n_loop
            rc = max(1.25, n_loop * 1.7 / (2 * math.pi))
            cidx = pos[n_arc + n_loop // 2][1]
            c = pts[cidx]
            gd = math.hypot(*c) or 1.0
            c = (c[0] + c[0] / gd * (rc + 0.5), c[1] + c[1] / gd * (rc + 0.5))
            prev_pt = pos[n_arc - 1][0] if n_arc else (entry.x, entry.y)
            a0 = math.atan2(prev_pt[1] - c[1], prev_pt[0] - c[0])
            sgn = -1 if up else 1
            loop = []
            for k in range(n_loop):
                ang = a0 + sgn * (2 * math.pi * (k + 0.5) / (n_loop + 1.0))
                loop.append(((c[0] + rc * math.cos(ang), c[1] + rc * math.sin(ang)), cidx,
                             (-math.sin(ang) * sgn, math.cos(ang) * sgn)))
            pos = pos[:n_arc] + loop + [pos[-1]]
        prev = spec['entry']
        for k, key in enumerate(keys):
            nd = add(key)
            (x, y), _i, t = pos[k]
            if nd.kind != 'keystone':
                x += jit(key, 'x', 0.16)
                y += jit(key, 'y', 0.16)
            nd.x, nd.y = x, y
            lay.tang[key] = t
            flow.append((prev, key))
            if prev == spec['entry']:   # leave the entry along this strand's own orbit, not along a shared trunk
                i0 = min(len(pts) - 1, max(3, pos[0][1] // 4))
                dx, dy = pts[i0][0] - pts[0][0], pts[i0][1] - pts[0][1]
                dl = math.hypot(dx, dy) or 1.0
                lay.edge_tang[(prev, key)] = (dx / dl, dy / dl)
            prev = key
        if lay.tang.get(spec['entry']) is None:
            lay.tang[spec['entry']] = (1.0, 0.0)
        flow.append((keys[-1], spec['goal']))

    # quests between keystones and goal (bridge, routes): on the mean bearing of their keystones, half way in
    for k, kdeps in spec['links']:
        nd = add(k)
        xs = [lay.nodes[d].x for d in kdeps if d in lay.nodes]
        ys = [lay.nodes[d].y for d in kdeps if d in lay.nodes]
        mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
        d = math.hypot(mx, my) or 1.0
        rr = (goal.r + nd.r + GAP + 0.25)
        nd.x, nd.y = mx / d * rr * 1.25, my / d * rr

    # side paths: satellites on the outer side of their strand, several lines on one quest fan out
    by_attach = {}
    for attach, keys in spec['sides']:
        by_attach.setdefault(attach, []).append(keys)
    for attach, key, size in spec.get('satellites') or []:
        by_attach.setdefault(attach, []).append([key])
        lay.add(key, 'guide', size=size)
    for attach in sorted(by_attach):
        lines = by_attach[attach]
        a = lay.nodes[attach]
        if attach == spec['entry']:
            base = math.radians(128.0)   # up and back from the entry; the notes sit down and back
            ox, oy = math.cos(base), math.sin(base)
        else:
            t = lay.tang.get(attach)
            if t:
                ox, oy = -t[1], t[0]
                if ox * a.x + oy * a.y < 0:
                    ox, oy = -ox, -oy
            else:
                d = math.hypot(a.x, a.y) or 1.0
                ox, oy = a.x / d, a.y / d
        for li, keys in enumerate(lines):
            spread = (li - (len(lines) - 1) / 2.0) * 38.0
            turn = math.radians(spread + (16 if h01(attach, 'side') > 0.5 else -16))
            vx, vy = ox * math.cos(turn) - oy * math.sin(turn), ox * math.sin(turn) + oy * math.cos(turn)
            px, py = a.x, a.y
            for k, key in enumerate(keys):
                nd = lay.nodes[key] if key in lay.nodes else add(key)
                dist = (a.r + nd.r + 0.9) if k == 0 else 1.35
                px, py = px + vx * dist + jit(key, 'sx', 0.12), py + vy * dist + jit(key, 'sy', 0.12)
                nd.x, nd.y = px, py

    # notes: a small constellation behind the entry, on an arc below the west
    ex = spec['explains']
    rr = 3.2
    step = math.degrees(1.95 / rr)
    for k, key in enumerate(ex):
        ang = math.radians(212 + (k - (len(ex) - 1) / 2.0) * step)
        r2 = rr + 0.5 * (k % 2)
        nd = add(key)
        nd.x = entry.x + r2 * math.cos(ang) + jit(key, 'ex', 0.12)
        nd.y = entry.y + r2 * math.sin(ang) + jit(key, 'ey', 0.12)

    # shrine picture on the axis between entry and goal, ring quest between picture and entry
    ring_n = h.get('shrine')
    if ring_n is not None:
        w = 4.0 if ring_n < 3 else 4.8
        hgt = w * 0.7
        cx = -(goal.r + 0.7 + w / 2.0)
        lay.images.append(dict(kind='shrine', ring=ring_n, x=cx, y=0.15, w=w, h=hgt))
        if spec['ring']:
            rq = add(spec['ring'])
            rq.x, rq.y = cx - w / 2.0 - 1.0, -0.2
            rq.stiff = True
    elif spec['ring']:
        rq = add(spec['ring'])
        rq.x, rq.y = 0.0, -(goal.r + 1.6)

    lay.relax()
    lay.curves(flow)
    return lay
