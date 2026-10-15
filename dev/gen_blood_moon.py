#!/usr/bin/env python3
"""Firmament Ages - Age blood moons: per-Age variants of the Enhanced Celestials 2 blood moons (data + English lang).

Writes
  kubejs/data/firmages/enhancedcelestials2core/lunar/event/blood_moon_age_<N>.json             N = 2..9
  kubejs/data/firmages/enhancedcelestials2core/lunar/event/super_blood_moon_age_<N>.json
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/blood_moon_age_<N>_equipment.json
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/blood_moon_age_<N>_effects.json   N >= 5
  kubejs/data/firmages/enhancedcelestials2core/lunar/equipment_set/<metal>_{armor_full,armor_half,sword,mace}.json
  kubejs/data/firmages/tags/entity_type/blood_moon_{armored,melee}.json
  kubejs/assets/firmages/lang/en_us.json   the event names (other keys in that file are kept)

Each Age event keeps every modifier of the EC2 default (sky, moon colour, sound, chat lines, monster x2.25 or x4.5,
surface spawns, no sleep, village siege, spawn distance) and adds the Age: monsters that spawn during the moon wear
the armour and carry the weapons of the metal one Age behind the players (bronze in the Iron Age ... blue steel in
the Quantum and Singularity Ages), and from the Electric Age on every monster gets speed, then resistance, then
strength. The metal is always one the players already make, so the 8.5 % vanilla equipment drop is no shortcut.
No event_probability file is written: EC2 keeps rolling its own blood moons (none before mob_2,
kubejs/server_scripts/firmages/blood_moon.js), and that script swaps each one for the event of the team's Age.

Formats verified against Enhanced Celestials 2 Core 2.0.3.3 and Corgilib 5.0.0.10 (javap):
  modifier types  LunarEventModifierTypes: mob_spawn_equipment {equipment_combinations: [{filter, chance, equipment}]},
                  mob_effects {effects: [{filter, mob_effect}]}; equipment = weighted list [{data, weight}] of
                  equipment_set holders (registry enhancedcelestials2core:lunar/equipment_set, RegistryFileCodec);
                  equipment_set = {<slot>: {item: ItemStack, count?}}; mob_effect as in the default blue_moon_mob_effects
  filters         corgilib:is_type_tag {tag: "#ns:path"} (TagKey.hashedCodec), corgilib:is_mob_category {mob_category}
  hooks           LunarEvent.equipMobOnSpawn (spawn equipment), LunarEvent.livingEntityTick (effects)

Usage: python dev/gen_blood_moon.py [--check]   (--check: exit 1 when a written file would change)
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "kubejs" / "data" / "firmages" / "enhancedcelestials2core" / "lunar"
TAGS = ROOT / "kubejs" / "data" / "firmages" / "tags" / "entity_type"
LANG = ROOT / "kubejs" / "assets" / "firmages" / "lang" / "en_us.json"
EC2 = "enhancedcelestials2defaultlunarevents"

# The default events' modifier lists (Default Lunar Events 2.0.1.3, data/.../lunar/event/*.json), kept whole.
DEFAULT_MODIFIERS = {
    "blood_moon": ["sky_light_color", "moon_texture_color", "sound_track", "text_components", "name_color",
                   "spawn_category_multiplier", "force_surface_spawning", "block_sleeping",
                   "village_siege_probability", "mob_spawn_distances"],
    "super_blood_moon": ["sky_light_color", "moon_texture_color", "moon_size", "sound_track", "text_components",
                         "name_color", "spawn_category_multiplier", "force_surface_spawning", "block_sleeping",
                         "village_siege_probability", "mob_spawn_distances"],
}
NAMES = {"blood_moon": "Blood Moon", "super_blood_moon": "Super Blood Moon"}

# Undead that can wear armour, and the ones that fight in melee (skeletons keep their bows).
ARMORED = ["minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager", "minecraft:drowned",
           "minecraft:skeleton", "minecraft:stray"]
MELEE = ["minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager"]

# Age N: metal one Age behind (age tags: bronze age_1, wrought_iron and steel age_2, black_steel age_4,
# red_steel age_5, blue_steel age_6), chance for armour, chance for a weapon, effects on every monster.
AGES = {
    2: ("bronze", 0.25, 0.20, []),
    3: ("wrought_iron", 0.30, 0.25, []),
    4: ("steel", 0.35, 0.30, []),
    5: ("black_steel", 0.40, 0.35, ["speed"]),
    6: ("black_steel", 0.50, 0.40, ["speed"]),
    7: ("red_steel", 0.50, 0.40, ["speed", "resistance"]),
    8: ("blue_steel", 0.55, 0.45, ["speed", "resistance"]),
    9: ("blue_steel", 0.60, 0.50, ["speed", "resistance", "strength"]),
}
EFFECT_TICKS = 1210  # EC2's own value: refreshed while the moon is up, gone a minute after it sets


def modifier(kind, settings):
    return {"type": f"enhancedcelestials2core:{kind}", "settings": settings}


def entry(item):
    return {"item": {"id": item, "count": 1}}


def equipment_sets(metal):
    part = lambda p: f"tfc:metal/{p}/{metal}"
    return {
        f"{metal}_armor_full": {"head": entry(part("helmet")), "chest": entry(part("chestplate")),
                                "legs": entry(part("greaves")), "feet": entry(part("boots"))},
        f"{metal}_armor_half": {"head": entry(part("helmet")), "chest": entry(part("chestplate"))},
        f"{metal}_sword": {"mainhand": entry(part("sword"))},
        f"{metal}_mace": {"mainhand": entry(part("mace"))},
    }


def files():
    out = {}
    lang = {}
    out[TAGS / "blood_moon_armored.json"] = {"values": ARMORED}
    out[TAGS / "blood_moon_melee.json"] = {"values": MELEE}
    for n, (metal, armor, weapon, effects) in AGES.items():
        for name, obj in equipment_sets(metal).items():
            out[DATA / "equipment_set" / f"{name}.json"] = obj
        base = f"blood_moon_age_{n}"
        own = [f"firmages:{base}_equipment"]
        out[DATA / "event_modifier" / f"{base}_equipment.json"] = modifier("mob_spawn_equipment", {
            "equipment_combinations": [
                {"filter": {"type": "corgilib:is_type_tag", "tag": "#firmages:blood_moon_armored"}, "chance": armor,
                 "equipment": [{"data": f"firmages:{metal}_armor_half", "weight": 2},
                               {"data": f"firmages:{metal}_armor_full", "weight": 1}]},
                {"filter": {"type": "corgilib:is_type_tag", "tag": "#firmages:blood_moon_melee"}, "chance": weapon,
                 "equipment": [{"data": f"firmages:{metal}_sword", "weight": 1},
                               {"data": f"firmages:{metal}_mace", "weight": 1}]},
            ]})
        if effects:
            own.append(f"firmages:{base}_effects")
            out[DATA / "event_modifier" / f"{base}_effects.json"] = modifier("mob_effects", {"effects": [
                {"filter": {"type": "corgilib:is_mob_category", "mob_category": "monster"},
                 "mob_effect": {"ambient": True, "amplifier": 0, "duration_in_ticks": EFFECT_TICKS,
                                "effect": f"minecraft:{e}", "show_icon": False, "visible": False}}
                for e in effects]})
        for kind, mods in DEFAULT_MODIFIERS.items():
            event = f"{kind}_age_{n}"
            out[DATA / "event" / f"{event}.json"] = {"modifiers": [f"{EC2}:{kind}_{m}" for m in mods] + own}
            lang[f"lunar.event.firmages.{event}"] = NAMES[kind]
    return out, lang


def dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="write nothing, exit 1 if any file is out of date")
    a = ap.parse_args()
    out, lang = files()
    merged = json.loads(LANG.read_text(encoding="utf-8")) if LANG.exists() else {}
    merged.update(lang)
    out[LANG] = dict(sorted(merged.items()))
    stale = []
    for path, obj in out.items():
        text = dump(obj)
        if path.exists() and path.read_text(encoding="utf-8") == text:
            continue
        stale.append(path)
        if not a.check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(text.encode("utf-8"))
    rel = [p.relative_to(ROOT).as_posix() for p in stale]
    if a.check:
        print(f"check: {len(out)} files, {len(stale)} out of date" + "".join(f"\n  {r}" for r in rel))
        sys.exit(1 if stale else 0)
    print(f"wrote {len(stale)} of {len(out)} files")


if __name__ == "__main__":
    main()
