#!/usr/bin/env python
"""Builds the "Constellation" title-screen shader for FancyMenu 3.9.14 and puts it into the title layout.

Reads:
  dev/shrine-viewer/shrine_data.json     shrine rings 0..8 (blocks, size, beam_color); not in Git, written by
                                         python dev/shrine-viewer/render_shrine.py --data-root mod/firmages-core/src/main/resources/data/firmages
  dev/title-shader/shader_template.glsl  the hand-written shader with @@...@@ data slots
Writes:
  dev/title-shader/constellation.glsl    the shader with the reduced shrine graph baked in as const arrays
  config/fancymenu/customization/title_screen_layout.txt
                                         only the value of `inline_shader_source` in the glsl menu_background block:
                                         constellation.glsl on one line, every line break replaced by FancyMenu's
                                         token %%!serialized_property_newline!%% (PropertyContainer.serializePropertyValue)

Node reduction per ring N (each ring = only the blocks it adds):
  * every raised column (top block y >= 1) -> node at (x, top+1, z)
  * the ring's footprint square (half size r): 4 corners, rings 0..3 also the 4 edge midpoints
  * the heart (0,0,0) is node 0 of ring 0
Edges per ring: the footprint loop, a minimum spanning tree over the raised columns (rings 0..3), the two shortest
column<->footprint links. Rings 4..8 keep their four corner pillars unconnected (posts on a platform, not a box).
Edges never cross rings; the rings light up one after another in the shader.

Usage: python dev/title-shader/build_shader.py            build both outputs
       python dev/title-shader/build_shader.py --layout   only re-inline the existing constellation.glsl
Then: python dev/check_menus.py
"""
import argparse
import json
import re
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
DATA_PATH = REPO / "dev/shrine-viewer/shrine_data.json"
TEMPLATE = HERE / "shader_template.glsl"
SHADER = HERE / "constellation.glsl"
LAYOUT = REPO / "config/fancymenu/customization/title_screen_layout.txt"
TOKEN = "%%!serialized_property_newline!%%"
Y_SCALE = 1.9        # vertical exaggeration in the shader (the shrine is 21 wide, 5 high)


def hex_rgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def ring_graph(rings, n):
    r = rings[n]
    cols = {}
    for x, y, z, _m in r["blocks"]:
        cols[(x, z)] = max(cols.get((x, z), -9), y)
    half = (r["size"][0] - 1) // 2
    nodes = []                      # (x, y, z)
    if n == 0:
        nodes.append((0, 1, 0))     # the heart, node 0
    raised = [(x, top + 1, z) for (x, z), top in cols.items() if top >= 1 and (x, z) != (0, 0)]
    raised.sort(key=lambda p: (-p[1], p[0], p[2]))
    nodes += raised
    first_foot = len(nodes)
    steps = (-half, 0, half) if n < 4 else (-half, half)
    foot = [(x, 0, z) for x in steps for z in steps if abs(x) == half or abs(z) == half]
    foot.sort(key=lambda p: np.arctan2(p[2], p[0]))     # the footprint as a loop (by angle)
    nodes += foot
    nf = len(foot)
    edges = [(first_foot + i, first_foot + (i + 1) % nf) for i in range(nf)]
    core_idx = list(range(0, first_foot))
    if len(core_idx) >= 5:          # MST over the raised columns (+ heart for ring 0)
        pts = np.array([nodes[i] for i in core_idx], float)
        d = np.linalg.norm(pts[:, None] - pts[None], axis=2)
        inn = [0]
        while len(inn) < len(core_idx):
            best = None
            for i in inn:
                for j in range(len(core_idx)):
                    if j in inn:
                        continue
                    if best is None or d[i, j] < best[0]:
                        best = (d[i, j], i, j)
            edges.append((core_idx[best[1]], core_idx[best[2]]))
            inn.append(best[2])
    pairs = []                      # the two closest column<->footprint pairs link the columns to the outline
    for i in core_idx:
        p = np.array(nodes[i], float)
        dist = np.linalg.norm(np.array(foot, float) - p, axis=1)
        j = int(np.argmin(dist))
        pairs.append((dist[j], i, first_foot + j))
    pairs.sort()
    for _d, i, j in (pairs[:2] if len(core_idx) >= 5 else pairs):
        if (i, j) not in edges:
            edges.append((i, j))
    return nodes, edges


def build_shader():
    data = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    rings = {r["n"]: r for r in data["rings"]}
    nodes, node_ring, edges = [], [], []
    for n in range(9):
        ns, es = ring_graph(rings, n)
        base = len(nodes)
        nodes += ns
        node_ring += [n] * len(ns)
        edges += [(a + base, b + base) for a, b in es]
    node_src = ",\n".join("    vec4(%d.,%d.,%d.,%d.)" % (p[0], p[1], p[2], r) for p, r in zip(nodes, node_ring))
    edge_src = ",\n".join("    ivec2(%d,%d)" % e for e in edges)
    col_src = ",\n".join("    vec3(%.3f,%.3f,%.3f)" % hex_rgb(rings[n]["beam_color"]) for n in range(9))
    # per-ring node/edge ranges (ring-major) and a bounding sphere per ring in shader units (y times Y_SCALE), so the
    # shader skips a ring's loops for pixels outside its circle
    nstart = [node_ring.index(n) for n in range(9)] + [len(nodes)]
    estart = [next(i for i, (a, _b) in enumerate(edges) if node_ring[a] == n) for n in range(9)] + [len(edges)]
    bounds = []
    for n in range(9):
        pts = np.array([nodes[i] for i in range(nstart[n], nstart[n + 1])], float) * [1.0, Y_SCALE, 1.0]
        ymid = (pts[:, 1].min() + pts[:, 1].max()) / 2
        rad = np.linalg.norm(pts - [0.0, ymid, 0.0], axis=1).max() + 0.6
        bounds.append("vec2(%.2f,%.2f)" % (ymid, rad))
    src = (TEMPLATE.read_text(encoding="utf-8").replace("@@NN@@", str(len(nodes))).replace("@@NE@@", str(len(edges)))
           .replace("@@NODES@@", node_src).replace("@@EDGES@@", edge_src).replace("@@RING_COLORS@@", col_src)
           .replace("@@Y_SCALE@@", "%.1f" % Y_SCALE)
           .replace("@@RING_NODE_START@@", ",".join(str(s) for s in nstart))
           .replace("@@RING_EDGE_START@@", ",".join(str(s) for s in estart))
           .replace("@@RING_BOUND@@", ",".join(bounds)))
    assert "@@" not in src.replace("@@CONST_DATA_BEGIN@@", "").replace("@@CONST_DATA_END@@", "")
    SHADER.write_text(src, encoding="utf-8", newline="\n")
    print(f"{SHADER.relative_to(REPO).as_posix()}: {len(nodes)} nodes, {len(edges)} edges, {len(src.encode())} bytes")


def inline_into_layout():
    src = SHADER.read_text(encoding="utf-8").replace("\r\n", "\n").rstrip("\n")
    assert TOKEN not in src
    one_line = src.replace("\n", TOKEN)
    # FancyMenu's PropertiesParser strips all whitespace from a line and opens a section when the result ends with
    # "{"; the value sits behind the key, so only its end can trip that rule
    assert not re.sub(r"\s+", "", one_line).endswith("{")
    text = LAYOUT.read_text(encoding="utf-8")
    new, count = re.subn(r"(?m)^(\s*inline_shader_source = ).*$", lambda m: m[1] + one_line, text)
    if count != 1:
        raise SystemExit(f"{LAYOUT.name}: expected exactly one inline_shader_source line, found {count}")
    LAYOUT.write_text(new, encoding="utf-8", newline="\n")
    print(f"{LAYOUT.relative_to(REPO).as_posix()}: inline_shader_source = {len(one_line)} chars")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--layout", action="store_true", help="only re-inline constellation.glsl into the layout")
    args = ap.parse_args()
    if not args.layout:
        build_shader()
    inline_into_layout()


if __name__ == "__main__":
    main()
