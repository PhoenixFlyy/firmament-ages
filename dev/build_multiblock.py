#!/usr/bin/env python3
"""Build an Immersive Engineering template multiblock on the test server with /setblock (RCON, dev/rcon.py).

Reads the structure template (data/<ns>/structure/multiblocks/<name>.nbt) from a mod jar in test-server/mods and
places every non-air block unrotated (template x/y/z = world x/y/z from --origin), block states included. An
unrotated build forms when the hammer hits the trigger block from the top face while the player looks north
(yaw 180): IE turns a top-face click into the player's facing (HammerItem), and rotation NONE means "formed from
the south". --sub swaps a template block for a pack block, e.g. the disabled IE steel block for the TFC one.
Prints the trigger position (template offset from --trigger) for `firmages debug use`.

  python dev/build_multiblock.py --jar AdvancedTFCTech --template advancedtfctech:grist_mill --origin 0 200 0 \
      --trigger 2 1 1 --sub immersiveengineering:storage_steel=tfc:metal/block/steel
  --clear   fill the template box with air first (and a stone floor below it)
  --dry-run print the commands instead of sending them
"""
import argparse
import glob
import os
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from derive_ages import _nbt  # noqa: E402
import rcon  # noqa: E402


def template(jar_part, rl):
    ns, name = rl.split(":", 1)
    jars = glob.glob(os.path.join(REPO, "test-server", "mods", f"*{jar_part}*.jar"))
    if len(jars) != 1:
        sys.exit(f"jar matching {jar_part!r}: {jars}")
    with zipfile.ZipFile(jars[0]) as z:
        return _nbt(z.read(f"data/{ns}/structure/multiblocks/{name}.nbt"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", required=True, help="part of the mod jar file name")
    ap.add_argument("--template", required=True, help="<namespace>:<name> under structure/multiblocks")
    ap.add_argument("--origin", type=int, nargs=3, required=True)
    ap.add_argument("--trigger", type=int, nargs=3, required=True, help="triggerFromOrigin of the multiblock class")
    ap.add_argument("--sub", action="append", default=[], help="old=new block id swap (repeatable)")
    ap.add_argument("--clear", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    subs = dict(s.split("=", 1) for s in a.sub)
    t = template(a.jar, a.template)
    ox, oy, oz = a.origin
    sx, sy, sz = t["size"]
    cmds = []
    if a.clear:
        cmds.append(f"fill {ox - 1} {oy} {oz - 1} {ox + sx} {oy + sy} {oz + sz} air")
        cmds.append(f"fill {ox - 1} {oy - 1} {oz - 1} {ox + sx} {oy - 1} {oz + sz} minecraft:stone")
    pal = t["palette"]
    for b in sorted(t["blocks"], key=lambda b: b["pos"][1]):
        p = pal[b["state"]]
        name = subs.get(p["Name"], p["Name"])
        if name == "minecraft:air":
            continue
        props = p.get("Properties", {}) if name == p["Name"] else {}
        state = name + ("[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]" if props else "")
        x, y, z = b["pos"]
        cmds.append(f"setblock {ox + x} {oy + y} {oz + z} {state}")
    tx, ty, tz = a.trigger
    print(f"trigger {ox + tx} {oy + ty} {oz + tz}")
    if a.dry_run:
        print("\n".join(cmds))
        return 0
    props = rcon.props(rcon.PROPS)
    r = rcon.Rcon("127.0.0.1", int(props["rcon.port"]), props["rcon.password"], 30)
    bad = 0
    for c in cmds:
        out = r.command(c)
        if "Changed the block" not in out and "Successfully filled" not in out:
            bad += 1
            print(f"{c} -> {out.strip()}")
    print(f"{len(cmds)} commands, {bad} without the expected reply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
