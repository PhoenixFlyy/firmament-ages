#!/usr/bin/env python3
"""Firmament Ages - reference builds of the harmony measurement (package A8): one structure template and one run file
per generator and big consumer, for /firmages energy reference run (firmages-core balance/ReferenceRun, SPEC §38.1).

Writes dev/reference/<name>.nbt (structure template, gzip NBT, DataVersion 3955 = 1.21.1) and dev/reference/<name>.json
(the run: audit centre and radius, warm-up, run time, steps, what to read, the band of harmony plan table 2.1). The pack
ships none of it: --install copies them as a world datapack into a server copy, where the runner finds them
(data/firmages_reference/structure/reference/<name>.nbt, data/firmages_reference/firmages_energy_reference/<name>.json).

Every build stands in the air at y 200 of its own site (x 30,000 + 160 per run, z 30,000) and feeds an energy trash can
(Trash Cans, takes any FE per tick), so "sinks" counts what the generator gives. Steps fill fuel and coolant through the
capabilities, form IE multiblocks with a hammer, start Mekanism and Draconic reactors the way a player's GUI or laser
would; the results go to <server>/logs/firmages-energy-reference.json and into dev/harmonie-messung.md.

Usage
  python dev/gen_reference_builds.py                      write dev/reference/
  python dev/gen_reference_builds.py --check              exit 1 if a file differs (content, not gzip bytes)
  python dev/gen_reference_builds.py --install <server>   also write <server>/world/datapacks/firmages_reference/
  --ie-jar <path>   the Immersive Engineering jar for its multiblock templates (default: the copy's or test-server mods/)
"""
import argparse
import glob
import gzip
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from gen_ponder_schematics import Plan, Int, nbt_bytes  # noqa: E402
from derive_ages import _nbt  # noqa: E402

OUT = os.path.join(HERE, "reference")
NS = "firmages_reference"
TRASH = "trashcans:energy_trash_can"
FLOOR = "minecraft:smooth_stone"

# Harmony plan table 2.1 (scratchpad harmonie-plan.md), FE per tick of the Age's plant
BAND = {
    "age_4": (1000, 3024),
    "age_5": (8000, 16000),
    "age_6": (10000, 20000),
    "age_7": (50000, 120000),
    "age_8": (200000, 1000000),
    "age_9": (1000000, 3000000),
}


def run(**kw):
    kw.setdefault("sinks", [TRASH])
    kw.setdefault("seconds", 60)
    return kw


# ---------------------------------------------------------------------------------------------------- Create (age_4)
def create_unfuelled_alternator():
    """A creative motor at 256 rpm turning a C&A Alternator: rotation that burns no fuel, like a water wheel or a
    windmill (a single water wheel cannot even turn an alternator: 256 SU against 512 at 8 rpm). Felix H1A (SPEC §34):
    the alternator gives FE only for the fuelled share of its network, here 0."""
    p = Plan((7, 5, 5))
    p.set(1, 2, 2, "create:creative_motor", {"facing": "east"}, {"ScrollValue": Int(256)})
    p.set(2, 2, 2, "create:shaft", {"axis": "x"})
    p.set(3, 2, 2, "createaddition:alternator", {"facing": "east"})
    p.set(3, 3, 2, TRASH)
    p.set(4, 2, 2, TRASH)
    return p, run(age="age_4", kind="passive", band=[0, BAND["age_4"][1] * 0.1], centre=[2, 2, 2], radius=5, warmup=100,
                  probe=[[3, 2, 2]])


def create_steam_boiler18():
    """Boiler level 18 (3x3x8 tank, nine seething blaze burners, 180 mB/t of water), 18 steam engines on three shaft
    columns, two 2:1 gear-ups to 256 rpm and 18 C&A Alternators in a line (Create 6.0.10 / C&A 1.7.2 [javap, measured]:
    an engine at full efficiency turns at 64 rpm with 256 SU per rpm, 16,384 SU; an alternator takes 64 SU per rpm and
    gives 480 x rpm/256 x 0.35 FE/t; 18 x 16,384 SU at 256 rpm, so the line should give 18 x 168 = 3,024 FE/t)."""
    p = Plan((30, 15, 10))
    tank_x, tank_z = range(2, 5), range(6, 9)
    for x in tank_x:
        for z in tank_z:
            p.set(x, 0, z, "create:blaze_burner", {"blaze": "seething", "facing": "north"},
                  {"fuelLevel": Int(2), "burnTimeRemaining": Int(30000), "isCreative": False})
            for y in range(1, 9):
                p.set(x, y, z, "create:fluid_tank", {"bottom": "false", "shape": "window", "top": "false"})
    for x in tank_x:
        for y in range(2, 8):
            p.set(x, y, 3, "create:shaft", {"axis": "y"})          # placed before the engine (lower z first)
            p.set(x, y, 5, "create:steam_engine", {"face": "wall", "facing": "north", "waterlogged": "false"})
        for y in (8, 9):
            p.set(x, y, 3, "create:shaft", {"axis": "y"})
        p.set(x, 10, 3, "create:gearbox", {"axis": "z"})
    p.set(5, 10, 3, "create:shaft", {"axis": "x"})
    p.set(6, 10, 3, "create:large_cogwheel", {"axis": "x"})
    p.set(6, 11, 4, "create:cogwheel", {"axis": "x"})
    p.set(7, 11, 4, "create:shaft", {"axis": "x"})
    p.set(8, 11, 4, "create:large_cogwheel", {"axis": "x"})
    p.set(8, 12, 5, "create:cogwheel", {"axis": "x"})
    p.set(9, 12, 5, "create:shaft", {"axis": "x"})
    for x in range(10, 28):
        p.set(x, 12, 5, "createaddition:alternator", {"facing": "east"})
        p.set(x, 13, 5, TRASH)
    return p, run(age="age_4", kind="generator", band=list(BAND["age_4"]), centre=[15, 12, 5], radius=18, warmup=300,
                  steps=[{"op": "wait", "ticks": 40},
                         # the boiler's water, as pumps would feed it: 250 mB per tick through the tank's handler
                         {"op": "fluid", "at": [3, 4, 8], "id": "minecraft:water", "amount": 250, "every": 1, "quiet": True}],
                  probe=[[3, 1, 7], [2, 2, 3], [10, 12, 5], [27, 12, 5]])


# ----------------------------------------------------------------------------------------- Immersive Engineering
IE_SUBS = {"immersiveengineering:storage_steel": "tfc:metal/block/steel"}


def ie_template(jar, name):
    import zipfile
    with zipfile.ZipFile(jar) as z:
        return _nbt(z.read(f"data/immersiveengineering/structure/multiblocks/{name}.nbt"))


def put_ie(p, t, ox, oy, oz):
    pal = t["palette"]
    for b in t["blocks"]:
        e = pal[b["state"]]
        name = IE_SUBS.get(e["Name"], e["Name"])
        if name == "minecraft:air":
            continue
        props = e.get("Properties", {}) if name == e["Name"] else {}
        x, y, z = b["pos"]
        p.set(ox + x, oy + y, oz + z, name, props)


def ie_diesel_generator(jar):
    """One IE Diesel Generator (template unrotated, formed from the south by a hammer looking north) on diesel; its three
    energy outputs face up from (0..2, 2, 4) [javap DieselGeneratorLogic]; trash cans cover the roof."""
    t = ie_template(jar, "diesel_generator")
    p = Plan((5, 5, 7))
    put_ie(p, t, 1, 0, 1)
    for x in range(1, 4):            # the whole roof: the output row's side depends on the formed orientation
        for z in range(1, 6):
            p.set(x, 3, z, TRASH)
    return p, run(age="age_5", kind="generator", band=[3900, 4200], centre=[2, 1, 3], radius=6, warmup=100,
                  steps=[{"op": "ie_form", "at": [2, 1, 5], "name": "immersiveengineering:multiblocks/diesel_generator"},
                         {"op": "wait", "ticks": 10},
                         {"op": "fluid", "at": [1, 0, 5], "id": "immersivepetroleum:diesel", "amount": 24000},
                         {"op": "fluid", "at": [3, 0, 5], "id": "immersivepetroleum:diesel", "amount": 24000},
                         # its control panel's redstone mode, as a player sets it (no signal reached the formed panel)
                         {"op": "ie_rs", "at": [1, 0, 5], "enabled_without_signal": True}],
                  probe=[[1, 0, 5]])


# ------------------------------------------------------------------------------------ Create: Diesel Generators
def cdg_huge_engine_alternator():
    """A huge diesel engine on its engine shaft (two blocks out, CDG 1.3.15 [javap]), one C&A Alternator on the shaft
    (14,336 SU at 224 rpm of the engine's 16,384), fuelled with diesel (fuel table: 1,318,912 FE per bucket)."""
    p = Plan((8, 5, 5))
    p.set(1, 2, 2, "createdieselgenerators:huge_diesel_engine", {"facing": "east", "powered": "false"})
    p.set(3, 2, 2, "createdieselgenerators:powered_engine_shaft", {"axis": "z"})
    p.set(3, 2, 3, "createaddition:alternator", {"facing": "south"})
    p.set(3, 3, 3, TRASH)
    p.set(3, 2, 4, TRASH)
    return p, run(age="age_5", kind="side source", band=[0, 336], centre=[2, 2, 2], radius=5, warmup=100,
                  steps=[{"op": "wait", "ticks": 20},
                         {"op": "fluid", "at": [1, 2, 2], "id": "immersivepetroleum:diesel", "amount": 4000, "every": 600}],
                  probe=[[1, 2, 2], [3, 2, 2], [3, 2, 3]])


# ------------------------------------------------------------------------------------------------- Mekanism
ETHENE_LINE = 1   # mB/t of ethene from one Pressurized Reaction Chamber without upgrades (100 mB per 100-tick op)


def mek_gbg():
    """One Mekanism Gas-Burning Generator fed one ethene line (1 mB/t). Mekanism 10.7.19 [javap, measured]: per tick it
    burns ceil(256 x tank fill) burn ticks of 705 J (pack data map, 40 burn ticks per mB), so its output follows the
    feed: 1 mB/t gives 705 x 40 / 2.5 = 11,280 FE/t; a full 18,000 mB tank gives 72,192 FE/t while it lasts."""
    p = Plan((5, 4, 5))
    p.set(2, 1, 2, "mekanismgenerators:gas_burning_generator", {"facing": "north"})
    for x, y, z in ((1, 1, 2), (3, 1, 2), (2, 1, 1), (2, 1, 3), (2, 2, 2)):
        p.set(x, y, z, TRASH)
    return p, run(age="age_6", kind="generator", band=list(BAND["age_6"]), centre=[2, 1, 2], radius=4, warmup=600,
                  # prefilled to the steady fill (256 x fill / 40 = 1 mB/t: 2,812 of 18,000 mB), then the line's feed
                  steps=[{"op": "chemical", "at": [2, 1, 2], "id": "mekanism:ethene", "amount": 2812},
                         {"op": "chemical", "at": [2, 1, 2], "id": "mekanism:ethene", "amount": 20 * ETHENE_LINE, "every": 20,
                          "quiet": True}],
                  probe=[[2, 1, 2]])


def mek_gbg_cube():
    """The same generator with 100 mB (about 560 FE/t, falling) pushing out of its front into a Mekanism basic energy
    cube (Mekanism's strict energy, not FE): the path that balance.feCap has to cap through
    compat/mekanism/MekEnergyMeter (SPEC §37). The cube is placed by a fake player looking south (so it faces north and
    takes energy from the south): a cube from a structure template gets no side configuration and no sided energy
    capability (measured, capcheck)."""
    p = Plan((5, 4, 5))
    p.set(2, 1, 2, "mekanismgenerators:gas_burning_generator", {"facing": "north"})
    p.set(2, 0, 1, FLOOR)
    return p, run(age="age_6", kind="cap test", sinks=["mekanism:basic_energy_cube"], centre=[2, 1, 2], radius=4, warmup=20,
                  seconds=20, steps=[{"op": "use", "at": [2, 0, 1], "item": "mekanism:basic_energy_cube", "face": "up", "yaw": 0},
                                     {"op": "wait", "ticks": 5},
                                     {"op": "chemical", "at": [2, 1, 2], "id": "mekanism:ethene", "amount": 100},
                                     {"op": "capcheck", "at": [2, 1, 1]}],
                  probe=[[2, 1, 2], [2, 1, 1]])


def mekmm_large_gbg():
    """Mekanism: More Machine's Large Gas-Burning Generator, placed by a fake player (its bounding blocks), fed 1 mB/t
    of ethene, the output of one ethene line. Mekmm 1.4.1 [javap]: per tick it burns ceil(256 x fill) x (1 + 63 x fill^3)
    burn ticks, so its output follows the feed like the single generator's (up to 64 x 72,192 FE/t with a full tank);
    energy leaves at the top."""
    p = Plan((7, 6, 7))
    p.fill(0, 0, 0, 6, 0, 6, FLOOR)
    p.set(3, 4, 3, TRASH)
    for x in range(2, 5):
        for z in range(2, 5):
            if (x, z) != (3, 3):
                p.set(x, 4, z, TRASH)
    return p, run(age="age_6", kind="generator", band=list(BAND["age_6"]), centre=[3, 2, 3], radius=5, warmup=600,
                  steps=[{"op": "use", "at": [3, 0, 3], "item": "mekmm:large_gas_burning_generator", "face": "up", "yaw": 180},
                         {"op": "wait", "ticks": 20},
                         # steady fill: ceil(256 f) (1 + 63 f^3) / 40 = 1 mB/t at f = 0.1352 of the 180,000 mB tank
                         {"op": "chemical", "at": [3, 1, 3], "id": "mekanism:ethene", "amount": 24336},
                         {"op": "chemical", "at": [3, 1, 3], "id": "mekanism:ethene", "amount": 20 * ETHENE_LINE, "every": 20,
                          "quiet": True}],
                  probe=[[3, 1, 3]])


TURBINE_W = 11   # Mekanism [javap TurbineValidator]: (width - 3) / 2 >= (complex height + 1) / 4, so 14 rotors need 11


def turbine(p, x0, z0, vents):
    """An 11x11x18 Mekanism turbine at (x0, 0, z0), the smallest that holds 14 rotors: 28 blades (2 per rotor) for the
    full 10 J per mB of steam, the rotational complex and 80 dispersers at y 15, 7 coils at y 16 (7 x 4 blades) touching
    the complex, vents on the y 16 walls (32,000 mB/t each). Steam and energy pass through valves the caller sets."""
    w = TURBINE_W
    for x in range(x0, x0 + w):
        for y in range(0, 18):
            for z in range(z0, z0 + w):
                edge = (x in (x0, x0 + w - 1)) + (y in (0, 17)) + (z in (z0, z0 + w - 1))
                if edge >= 1:
                    p.set(x, y, z, "mekanismgenerators:turbine_casing" if edge >= 2 or y in (0, 17) else "mekanism:structural_glass")
    cx, cz = x0 + w // 2, z0 + w // 2
    for y in range(1, 15):
        p.set(cx, y, cz, "mekanismgenerators:turbine_rotor", None, {"blades": Int(2)})
    for x in range(x0 + 1, x0 + w - 1):
        for z in range(z0 + 1, z0 + w - 1):
            p.set(x, 15, z, "mekanismgenerators:rotational_complex" if (x, z) == (cx, cz) else "mekanism:pressure_disperser")
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
        p.set(cx + dx, 16, cz + dz, "mekanismgenerators:electromagnetic_coil")
    walls = [(x0 + w - 1, 16, z) for z in range(z0 + 1, z0 + w - 1)] + [(x, 16, z0 + w - 1) for x in range(x0 + 1, x0 + w - 1)] +             [(x0, 16, z) for z in range(z0 + 1, z0 + w - 1)] + [(x, 16, z0) for x in range(x0 + 1, x0 + w - 1)]
    for x, y, z in walls[:vents]:
        p.set(x, y, z, "mekanismgenerators:turbine_vent")


def fission_reactor(p):
    """A 5x5x5 fission reactor at the origin: two fuel assemblies under a control rod in the centre column (max burn
    2 mB/t), an input port north (fuel, water), an output-coolant port east (steam into the turbine valve next to it)."""
    for x in range(5):
        for y in range(5):
            for z in range(5):
                edge = (x in (0, 4)) + (y in (0, 4)) + (z in (0, 4))
                if edge >= 2:
                    p.set(x, y, z, "mekanismgenerators:fission_reactor_casing")
                elif edge == 1:
                    p.set(x, y, z, "mekanismgenerators:reactor_glass")
    p.set(2, 1, 2, "mekanismgenerators:fission_fuel_assembly")
    p.set(2, 2, 2, "mekanismgenerators:fission_fuel_assembly")
    p.set(2, 3, 2, "mekanismgenerators:control_rod_assembly")
    p.set(2, 2, 0, "mekanismgenerators:fission_reactor_port", {"mode": "input"})
    p.set(4, 2, 2, "mekanismgenerators:fission_reactor_port", {"mode": "output_coolant"})


def mek_fission_turbine():
    """The Space Age plant: the fission reactor at 1 mB/t and one full-blade turbine (4 vents, 128,000 mB/t) next to it,
    the turbine's steam valve against the reactor's coolant port, an energy valve with a trash can on the far side.
    The turbine is the 11x11x18 one (28 blades); a 9x9x17 turbine holds 13 rotors (26 blades, 9.29 J per mB).
    Mekanism [javap]: 1 mB of fuel = energyPerFissionFuel J of heat, water boils it into heat x 0.2 / 10 mB of steam, the
    turbine gives up to 10 J per mB (28 blades)."""
    p = Plan((17, 18, 11))
    fission_reactor(p)
    turbine(p, 5, 0, 4)
    p.set(5, 2, 2, "mekanismgenerators:turbine_valve")
    p.set(15, 2, 2, "mekanismgenerators:turbine_valve")
    p.set(16, 2, 2, TRASH)
    return p, run(age="age_7", kind="generator", band=list(BAND["age_7"]), centre=[8, 2, 5], radius=12, warmup=3000,
                  steps=[{"op": "wait", "ticks": 60},
                         {"op": "chemical", "at": [2, 2, 0], "id": "mekanism:fissile_fuel", "amount": 16000},
                         {"op": "fluid", "at": [2, 2, 0], "id": "minecraft:water", "amount": 800000, "every": 20, "quiet": True},
                         {"op": "mek_fission", "at": [2, 2, 0], "rate": 1.0, "active": True}],
                  probe=[[2, 2, 0], [5, 2, 2]])


def fusion_reactor(p, out_port):
    """Mekanism's fusion reactor, 5x5x5 at the origin [javap FusionReactorValidator.ALLOWED_GRID]: the frame ring on
    every face, glass in the face centres, the controller on top, an input port north and an output port east."""
    ring = {(2, 0), (1, 1), (3, 1), (0, 2), (4, 2), (1, 3), (3, 3), (2, 4)}
    plus = {(2, 1), (1, 2), (2, 2), (3, 2), (2, 3)}
    for x in range(5):
        for y in range(5):
            for z in range(5):
                if 0 < x < 4 and 0 < y < 4 and 0 < z < 4:
                    continue
                if x in (0, 4):
                    c = (z, y)
                elif z in (0, 4):
                    c = (x, y)
                else:
                    c = (x, z)
                if c in ring:
                    p.set(x, y, z, "mekanismgenerators:fusion_reactor_frame")
                elif c in plus:
                    p.set(x, y, z, "mekanismgenerators:reactor_glass")
    p.set(2, 4, 2, "mekanismgenerators:fusion_reactor_controller")
    p.set(2, 2, 0, "mekanismgenerators:fusion_reactor_port", {"active": "false"})
    p.set(4, 2, 2, "mekanismgenerators:fusion_reactor_port", {"active": "true" if out_port else "false"})


FUSION_INJECTION = 20   # the Quantum Age reference plant: 10 mB/t deuterium + 10 mB/t tritium


def fusion_steps(water):
    s = [{"op": "wait", "ticks": 60},
         {"op": "mek_tank", "at": [2, 2, 0], "tank": "deuterium", "id": "mekanismgenerators:deuterium", "amount": 4000},
         {"op": "mek_tank", "at": [2, 2, 0], "tank": "tritium", "id": "mekanismgenerators:tritium", "amount": 4000},
         {"op": "mek_tank", "at": [2, 2, 0], "tank": "deuterium", "id": "mekanismgenerators:deuterium",
          "amount": FUSION_INJECTION * 10, "every": 20, "quiet": True},
         {"op": "mek_tank", "at": [2, 2, 0], "tank": "tritium", "id": "mekanismgenerators:tritium",
          "amount": FUSION_INJECTION * 10, "every": 20, "quiet": True}]
    if water:
        s.append({"op": "fluid", "at": [2, 2, 0], "id": "minecraft:water", "amount": 2000000, "every": 5, "quiet": True})
    s.append({"op": "mek_port", "at": [4, 2, 2], "output": True})
    s.append({"op": "mek_fusion", "at": [2, 2, 0], "injection": FUSION_INJECTION, "plasma": 5.0e8, "burning": True})
    return s


def mek_fusion_passive():
    """Air-cooled fusion at injection 20: Mekanism's passive generation (thermocouples) leaves through the output port."""
    p = Plan((6, 5, 5))
    fusion_reactor(p, True)
    p.set(5, 2, 2, TRASH)
    return p, run(age="age_8", kind="passive", band=[0, 120000], centre=[2, 2, 2], radius=6, warmup=6000,
                  steps=fusion_steps(False), probe=[[2, 2, 0]])


def mek_fusion_active():
    """Water-cooled fusion at injection 20: the output port pushes steam into a full-blade turbine (12 vents, 384,000
    mB/t) and the thermocouple share as energy into the valve; the turbine's energy valve feeds a trash can."""
    p = Plan((17, 18, 11))
    fusion_reactor(p, True)
    turbine(p, 5, 0, 12)
    p.set(5, 2, 2, "mekanismgenerators:turbine_valve")
    p.set(15, 2, 2, "mekanismgenerators:turbine_valve")
    p.set(16, 2, 2, TRASH)
    return p, run(age="age_8", kind="generator", band=list(BAND["age_8"]), centre=[8, 2, 5], radius=12, warmup=6000,
                  steps=fusion_steps(True), probe=[[2, 2, 0], [5, 2, 2]])


# ------------------------------------------------------------------------------------------- Draconic Evolution
DE_FUEL = 5184          # four blocks of awakened draconium (1,296 each): half a reactor, "small"
DE_FLOW = 2_000_000     # extraction through the four flux gates (RF/t); measured: 5,250 C and steady, net 1.09 M FE/t
                        # (two blocks at 1 M: 0.55 M net; four at 3 M: 8,800 to 9,100 C and a net loss)


def de_reactor_small():
    """A Draconic reactor with four blocks of fuel: the core is built and formed by the runner (DE keeps the component
    facing in managed data), four flux gates behind the stabilizers limit the extraction, trash cans behind them; the
    field is fed each tick what it drains, through the core as the injector would (counted against the output)."""
    p = Plan((15, 13, 15))
    c = 7
    for d, (dx, dz) in {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}.items():
        p.set(c + 6 * dx, 4, c + 6 * dz, "draconicevolution:flux_gate", {"facing": d})
        p.set(c + 7 * dx, 4, c + 7 * dz, TRASH)
    steps = [{"op": "de_build", "at": [c, 4, c]},
             {"op": "wait", "ticks": 20},
             {"op": "de_reactor", "at": [c, 4, c], "fuel": DE_FUEL, "temperature": 5000, "saturation": 0.5, "shield": 0.6,
              "start": True}]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        steps.append({"op": "de_gate", "at": [c + 6 * dx, 4, c + 6 * dz], "flow": DE_FLOW // 4})
    steps.append({"op": "de_feed", "at": [c, 4, c], "every": 1, "quiet": True})
    return p, run(age="age_9", kind="generator", band=list(BAND["age_9"]),
                  centre=[c, 4, c], radius=9, warmup=2400, steps=steps, probe=[[c, 4, c]])


# ------------------------------------------------------------------------------------------- consumers (harmony H2A)
ORE = "tfc:ore/rich_hematite"


def mek_machine(item, chemical, upgrades=None):
    """One Mekanism ore-ladder machine (placed by a fake player: a templated Mekanism block gets no side configuration),
    TFC ore pieces, its chemical and energy offered every tick through its energy capability; the audit's net use of the
    machine is what it draws (machine-usage.toml x10, H2A)."""
    p = Plan((5, 3, 5))
    p.set(2, 0, 2, FLOOR)
    steps = [{"op": "use", "at": [2, 0, 2], "item": item, "face": "up", "yaw": 180},
             {"op": "wait", "ticks": 5}]
    if upgrades:
        steps.append(dict({"op": "mek_upgrades", "at": [2, 1, 2]}, **upgrades))
    steps += [{"op": "item", "at": [2, 1, 2], "id": ORE, "count": 64},
              {"op": "chemical", "at": [2, 1, 2], "id": chemical, "amount": 10000},
              {"op": "energy", "at": [2, 1, 2], "amount": 1000000, "every": 1, "quiet": True}]
    return p, run(age="age_6", kind="consumer", sinks=[], users=[item], centre=[2, 1, 2], radius=4, warmup=100,
                  steps=steps, probe=[[2, 1, 2]])


# ------------------------------------------------------------------------------------------- Sky Harvest (SPEC §26)
STORM_RODS = 8


def storm_rods():
    """Eight Storm Rods on a floor under open sky, 256 sand each; the chunks stay loaded ("keep") so the rods keep rolling
    while the server runs on (SPEC §26.10 acceptance 3: the strike rate over five real hours of TFC weather, no
    override). Not an energy build: the audit only proves no rod gives FE (§26.10 acceptance 4)."""
    p = Plan((25, 3, 4))
    p.fill(0, 0, 0, 24, 0, 3, FLOOR)
    steps = []
    for i in range(STORM_RODS):
        x = 1 + 3 * i
        p.set(x, 1, 1, "firmages:storm_rod", {"half": "lower"})
        p.set(x, 2, 1, "firmages:storm_rod", {"half": "upper"})
        steps.append({"op": "item", "at": [x, 1, 1], "id": "tfc:sand/yellow", "count": 256, "side": "up"})
    return p, run(age="age_2", kind="other", band=[0, 0], sinks=[], centre=[12, 1, 1], radius=14, warmup=20, seconds=10, keep=True,
                  steps=steps, probe=[[1 + 3 * i, 1, 1] for i in range(STORM_RODS)])


# ----------------------------------------------------------------------------------------------------- the list
def builds(ie_jar):
    out = {
        "create_unfuelled_alternator": create_unfuelled_alternator(),
        "create_steam_boiler18": create_steam_boiler18(),
        "cdg_huge_engine_alternator": cdg_huge_engine_alternator(),
        "mek_gbg": mek_gbg(),
        "mek_gbg_cube": mek_gbg_cube(),
        "mekmm_large_gbg": mekmm_large_gbg(),
        "mek_fission_turbine": mek_fission_turbine(),
        "mek_fusion_passive": mek_fusion_passive(),
        "mek_fusion_active": mek_fusion_active(),
        "de_reactor_small": de_reactor_small(),
        "storm_rods": storm_rods(),
        "mek_purification": mek_machine("mekanism:purification_chamber", "mekanism:oxygen"),
        "mek_purification_upgraded": mek_machine("mekanism:purification_chamber", "mekanism:oxygen", {"speed": 8, "energy": 8}),
        "mek_injection": mek_machine("mekanism:chemical_injection_chamber", "mekanism:hydrogen_chloride"),
        "mek_dissolution": mek_machine("mekanism:chemical_dissolution_chamber", "mekanism:sulfuric_acid"),
    }
    if ie_jar:
        out["ie_diesel_generator"] = ie_diesel_generator(ie_jar)
    return out


# The GameTest smoke build (BalanceGameTests.referenceRunMetersASmokeBuild): an IE MV capacitor fed 100 FE a tick through
# its top face; the run must read 100 FE/t "in" at the sink. Written into the GameTest resources, not dev/reference/.
GT_DATA = os.path.join(REPO, "mod", "firmages-core", "src", "gametest", "resources", "data", "firmages")


def smoke_capacitor():
    p = Plan((3, 3, 3))
    p.set(1, 1, 1, "immersiveengineering:capacitor_mv")
    return p, run(age="test", kind="smoke", sinks=["immersiveengineering:capacitor_mv"], centre=[1, 1, 1], radius=3, settle=2,
                  warmup=5, seconds=2, steps=[{"op": "energy", "at": [1, 1, 1], "side": "up", "amount": 100, "every": 1, "quiet": True}])


def find_ie_jar():
    # a worktree's sibling copies (.wt/test-server-*), the main repo's copies, then test-server itself (read only)
    for base in (os.path.dirname(REPO), os.path.join(os.path.dirname(REPO), ".wt"), REPO):
        hits = glob.glob(os.path.join(base, "test-server*", "mods", "ImmersiveEngineering-*.jar"))
        if hits:
            return sorted(hits)[0]
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--install", metavar="SERVER")
    ap.add_argument("--ie-jar")
    a = ap.parse_args()
    ie_jar = a.ie_jar or find_ie_jar()
    if not ie_jar:
        sys.exit("gen_reference_builds: no Immersive Engineering jar found (--ie-jar)")
    os.makedirs(OUT, exist_ok=True)
    bad = 0
    files = {}
    for name, (plan, recipe) in builds(ie_jar).items():
        files[f"{name}.nbt"] = ("nbt", plan.nbt())
        files[f"{name}.json"] = ("json", json.dumps(recipe, indent=2) + "\n")
    plan, recipe = smoke_capacitor()
    files["gametest:structure/reference/smoke_capacitor.nbt"] = ("nbt", plan.nbt())
    files["gametest:firmages_energy_reference/smoke_capacitor.json"] = ("json", json.dumps(recipe, indent=2) + "\n")
    for fn, (kind, data) in files.items():
        path = os.path.join(GT_DATA, fn.split(":", 1)[1]) if fn.startswith("gametest:") else os.path.join(OUT, fn)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if a.check:
            if not os.path.isfile(path):
                print(f"MISSING {fn}")
                bad += 1
                continue
            if kind == "nbt":
                with gzip.open(path) as g:
                    same = g.read() == gzip.decompress(nbt_bytes(data))
            else:
                same = open(path, encoding="utf-8").read() == data
            if not same:
                print(f"STALE {fn}")
                bad += 1
            continue
        if kind == "nbt":
            with open(path, "wb") as f:
                f.write(nbt_bytes(data))
        else:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(data)
    if a.check:
        print(f"gen_reference_builds --check: {len(files)} files, " + ("up to date" if not bad else f"{bad} stale or missing"))
        sys.exit(1 if bad else 0)
    print(f"gen_reference_builds: {len(files) // 2 - 1} builds written to {OUT}, the GameTest smoke build to {GT_DATA}")
    if a.install:
        dp = os.path.join(a.install, "world", "datapacks", NS)
        shutil.rmtree(dp, ignore_errors=True)
        sdir = os.path.join(dp, "data", NS, "structure", "reference")
        rdir = os.path.join(dp, "data", NS, "firmages_energy_reference")
        os.makedirs(sdir)
        os.makedirs(rdir)
        with open(os.path.join(dp, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"pack": {"pack_format": 48, "description": "Firmament Ages reference builds (dev only)"}}, f)
        for fn in files:
            if fn.startswith("gametest:"):
                continue
            shutil.copy(os.path.join(OUT, fn), os.path.join(sdir if fn.endswith(".nbt") else rdir, fn))
        print(f"installed into {dp}")


if __name__ == "__main__":
    main()
