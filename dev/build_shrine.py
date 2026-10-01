#!/usr/bin/env python3
"""Build the shrine rings 0..N around a heart on the test server with /setblock (RCON, dev/rcon.py).

Reads the ring patterns from the firmages-core resources (data/firmages/modonomicon/multiblocks/shrine_ring_N.json),
places one real pack block per pattern character (PICK below, one concrete member of each firmages:shrine/* tag),
the heart in the centre and the plinth of every ring. Layers are placed bottom up so candles and lamps have support.
Prints each ring's plinth position (needed for /firmages debug use).

  --platform   fill a stone platform under the 21x21 footprint and clear the air above it first
  --rites N    also set the rite blockstates of tier N the way the game reaches them: tier 2 lights the four lamps
               (lit=true; a lamp without fuel goes out later, long enough for the prayer), tier 3 lights the candles
               (lit=true), tier 4 charges the IE electric lanterns, tier 5 charges the IE floodlights and puts a
               redstone block on each. "Charged" = IE's own stored energy (block entity NBT; the lantern reads
               energyStorage, the floodlight reads energy and writes energyStorage), so IE's tick switches
               active=true itself; wiring a generator by command is not possible.
  --dry-run    print the commands instead of sending them

Usage:  python dev/build_shrine.py --heart -16 200 -16 --rings 0-8 --platform
        python dev/build_shrine.py --heart -16 200 -16 --rings 3 --rites 3
"""
import argparse
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "mod", "firmages-core", "src", "main", "resources", "data", "firmages")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rcon  # noqa: E402

# One real block per shrine tag (all ids are members of the tag files in tags/block/shrine/).
PICK = {
    "hearth_stones": "tfc:rock/cobble/granite",
    "hearth_posts": "tfc:wood/log/oak",
    "thatch": "tfc:thatch",
    "sanctum_bricks": "tfc:rock/bricks/granite",
    "bronze_blocks": "tfc:metal/block/bronze",
    "bells": "tfc:bronze_bell",
    "smooth_stones": "tfc:rock/smooth/granite",
    "iron_bars": "tfc:metal/bars/wrought_iron",
    "iron_lamps": "tfc:metal/lamp/wrought_iron",
    "sourcestone": "ars_nouveau:gilded_sourcestone_large_bricks",
    "otherstone": "occultism:otherstone",
    "source_gem_blocks": "ars_nouveau:source_gem_block",
    "candles": "tfc:candle",
    "coke_bricks": "immersiveengineering:cokebrick",
    "heavy_engineering": "immersiveengineering:heavy_engineering",
    "steel_scaffolding": "immersiveengineering:steel_scaffolding_standard",
    "electric_lanterns": "immersiveengineering:electric_lantern",
    "aluminium_blocks": "immersiveengineering:sheetmetal_aluminum",
    "red_steel_blocks": "tfc:metal/block/red_steel",
    "hv_capacitors": "immersiveengineering:capacitor_hv",
    "alu_scaffolding": "immersiveengineering:alu_scaffolding_standard",
    "floodlights": "immersiveengineering:floodlight",
    "quartz_blocks": "ae2:quartz_block",
    "steel_casings": "mekanism:steel_casing",
    "quartz_glass": "ae2:quartz_glass",
    "fluix_blocks": "ae2:fluix_block",
    "steel_plating": "ad_astra:steel_plating",
    "desh_blocks": "ad_astra:desh_block",
    "ostrum_blocks": "ad_astra:ostrum_block",
    "calorite_blocks": "ad_astra:calorite_block",
    "quantum_casings": "mekanism:sps_casing",
    "awakened_draconium": "draconicevolution:awakened_draconium_block",
}
# tier -> (pattern key, block override, extra block placed above, or None)
RITES = {
    2: ("L", "tfc:metal/lamp/wrought_iron[lit=true]", None),
    3: ("K", "tfc:candle[candles=4,lit=true]", None),
    4: ("E", "immersiveengineering:electric_lantern{energyStorage:2000000000}", None),
    5: ("L", "immersiveengineering:floodlight{energy:2000000000}", "minecraft:redstone_block"),
}


def ring(n):
    with open(os.path.join(RES, "modonomicon", "multiblocks", f"shrine_ring_{n}.json"), encoding="utf-8") as f:
        return json.load(f)


def block_for(spec):
    if "block" in spec:
        return spec["block"]
    tag = spec["tag"].lstrip("#")
    if not tag.startswith("firmages:shrine/"):
        raise SystemExit(f"unexpected tag {tag}")
    return PICK[tag.split("/", 1)[1]]


def commands(heart, n, rites):
    """(setblock commands bottom up, plinth position) of ring n."""
    d = ring(n)
    layers = d["pattern"]
    h = len(layers)
    bottom = layers[-1]
    hr, hc = next((r, row.index("0")) for r, row in enumerate(bottom) if "0" in row)
    hx, hy, hz = heart
    out, plinth, extra = [], None, []
    for li in range(h - 1, -1, -1):
        y = hy + (h - 1 - li)
        for r, row in enumerate(layers[li]):
            for c, ch in enumerate(row):
                if ch in "_ ":
                    continue
                x, z = hx + c - hc, hz + r - hr
                if ch == "0":
                    continue  # the heart goes last (it validates on placement)
                b = block_for(d["mapping"][ch])
                if rites in RITES and RITES[rites][0] == ch and rites == n:
                    b = RITES[rites][1]
                    if "{" in b:  # an existing block keeps its block entity on setblock: merge the charge into it
                        extra.append(f"data merge block {x} {y} {z} {b[b.index('{'):]}")
                    if RITES[rites][2]:
                        extra.append(f"setblock {x} {y + 1} {z} {RITES[rites][2]}")
                if ch == "P":
                    plinth = (x, y, z)
                out.append(f"setblock {x} {y} {z} {b}")
    return out + extra, plinth


def parse_rings(s):
    if "-" in s:
        a, b = s.split("-")
        return list(range(int(a), int(b) + 1))
    return [int(x) for x in s.split(",")]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--heart", nargs=3, type=int, required=True, metavar=("X", "Y", "Z"))
    ap.add_argument("--rings", default="0-8")
    ap.add_argument("--rites", type=int, help="also set the rite states of this tier (2, 3, 4 or 5)")
    ap.add_argument("--platform", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    hx, hy, hz = a.heart
    cmds = []
    if a.platform:
        cmds += [f"forceload add {hx - 12} {hz - 12} {hx + 12} {hz + 12}",
                 f"fill {hx - 12} {hy - 1} {hz - 12} {hx + 12} {hy - 1} {hz + 12} minecraft:stone",
                 f"fill {hx - 12} {hy} {hz - 12} {hx + 12} {hy + 8} {hz + 12} minecraft:air"]
    plinths = {}
    for n in parse_rings(a.rings):
        c, p = commands(a.heart, n, a.rites)
        cmds += c
        plinths[n] = p
    cmds.append(f"setblock {hx} {hy} {hz} firmages:shrine_heart")
    for n, p in plinths.items():
        print(f"ring {n}: plinth {n + 1} at {p[0]} {p[1]} {p[2]}")
    if a.dry_run:
        print("\n".join(cmds))
        return
    p = rcon.props(rcon.PROPS)
    r = rcon.Rcon("127.0.0.1", int(p.get("rcon.port", "25575")), p.get("rcon.password", ""), 60)
    bad = 0
    for cmd in cmds:
        out = rcon.COLOR.sub("", r.command(cmd))
        if not (out.startswith("Changed the block") or out.startswith("Could not set the block") or out.startswith("Successfully filled") or "force loaded" in out
                or out.startswith("Marked") or out.startswith("Modified block data")):
            bad += 1
            print(f"{cmd} -> {out.strip()}")
    print(f"{len(cmds)} commands sent, {bad} with an unexpected reply (an unchanged block counts as expected)")


if __name__ == "__main__":
    main()
