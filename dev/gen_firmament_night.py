#!/usr/bin/env python3
"""Firmament Ages - the Firmament Night: one Enhanced Celestials 2 lunar event per Age (data + English lang).

Writes
  kubejs/data/firmages/enhancedcelestials2core/lunar/event/firmament_age_<N>.json             N = 0..9
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/firmament_age_<N>_sky.json   sky light colour
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/firmament_age_<N>_moon.json  moon colour
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/firmament_age_<N>_text.json  hot-bar line at dusk
  kubejs/data/firmages/enhancedcelestials2core/lunar/event_modifier/firmament_{moon_size,calm,experience}.json
  kubejs/assets/firmages/lang/en_us.json   the keys below (other keys in that file are kept)

No event_probability file is written: EC2 only rolls events that have one, so these nights never come by chance.
kubejs/server_scripts/firmages/firmament_night.js starts the night of a new Age once per world.

Colours: Age N takes the beam_color of shrine ring N (mod tier/ring_N.json), the same accent as the quest chapter and
banner of that Age (dev/gen_quest_art.py, AGES). Age 9 has no ring of its own and takes the Singularity white of
gen_quest_art (#F4F0FF, also the finale gold-white of the ceremony).
Formats verified against Enhanced Celestials 2 Core 2.0.3.3 / Default Lunar Events 2.0.1.3 (jar data and javap):
colour = hex RGB string without '#', notification type CHAT | HOT_BAR | NONE, end_notification optional.

Usage: python dev/gen_firmament_night.py [--check]   (--check: exit 1 when a written file would change)
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIERS = ROOT / "mod" / "firmages-core" / "src" / "main" / "resources" / "data" / "firmages" / "firmages_shrine" / "tier"
DATA = ROOT / "kubejs" / "data" / "firmages" / "enhancedcelestials2core" / "lunar"
LANG = ROOT / "kubejs" / "assets" / "firmages" / "lang" / "en_us.json"
AGE_9_COLOR = "#F4F0FF"

MOON_SIZE = 30.0       # vanilla-sized moon is 20, EC2 super moons use 40
MONSTERS = 0.5         # half the monster spawns: a calm night, not a safe one (early darkness stays, H4)
EXPERIENCE = 1.5

# Age name, hot-bar line. Caelum speaks only at the shrine; out here the sky itself answers.
AGES = [
    ("Stone Age", "Tonight the sky glows like embers."),
    ("Bronze Age", "Tonight the sky turns to bronze."),
    ("Iron Age", "Tonight the sky burns like a forge."),
    ("Arcane Age", "Tonight the sky shimmers violet."),
    ("Industrial Age", "Tonight the sky is pale as steam."),
    ("Electric Age", "Tonight the sky crackles blue."),
    ("Information Age", "Tonight the sky flickers green."),
    ("Space Age", "Tonight the moon hangs close enough to reach."),
    ("Quantum Age", "Tonight the stars shimmer, here and not here."),
    ("Singularity Age", "Tonight the whole sky is light."),
]


def age_colors():
    colors = []
    for n in range(9):
        tier = json.loads((TIERS / f"ring_{n}.json").read_text(encoding="utf-8"))
        colors.append(tier["response"]["beam_color"])
    colors.append(AGE_9_COLOR)
    return [c.lstrip("#").lower() for c in colors]


def modifier(kind, settings):
    return {"type": f"enhancedcelestials2core:{kind}", "settings": settings}


def files():
    out = {}
    colors = age_colors()
    out[DATA / "event_modifier" / "firmament_moon_size.json"] = modifier("moon_size", {"moon_size": MOON_SIZE})
    out[DATA / "event_modifier" / "firmament_calm.json"] = modifier(
        "spawn_category_multiplier", {"multipliers": {"monster": MONSTERS}})
    out[DATA / "event_modifier" / "firmament_experience.json"] = modifier("experience", {"amplifier": EXPERIENCE})
    lang = {}
    for n, ((name, line), color) in enumerate(zip(AGES, colors)):
        base = f"firmament_age_{n}"
        out[DATA / "event_modifier" / f"{base}_sky.json"] = modifier("sky_light_color", {"color": color})
        out[DATA / "event_modifier" / f"{base}_moon.json"] = modifier("moon_texture_color", {"color": color})
        out[DATA / "event_modifier" / f"{base}_text.json"] = modifier("text_components", {"components": {
            "start_notification": {
                "type": "HOT_BAR",
                "component": {"key": f"firmages.night.age_{n}", "style": {"color": f"#{color}"}},
            },
        }})
        out[DATA / "event" / f"{base}.json"] = {"modifiers": [
            f"firmages:{base}_sky",
            f"firmages:{base}_moon",
            "firmages:firmament_moon_size",
            f"firmages:{base}_text",
            "firmages:firmament_calm",
            "firmages:firmament_experience",
        ]}
        lang[f"lunar.event.firmages.{base}"] = f"Firmament Night ({name})"
        lang[f"firmages.night.age_{n}"] = line
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
