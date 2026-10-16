#!/usr/bin/env python3
"""Firmament Ages - generate the FTB Quests book (FTB Quests 2101.1.36, NeoForge 1.21.1).

Writes config/ftbquests/quests/: data.snbt, chapter_groups.snbt, chapters/*.snbt, reward_tables/*.snbt and
lang/en_us.snbt. All player-facing text lives in lang/en_us.snbt (FTB Quests 2101 keeps titles, subtitles and
descriptions in per-locale translation tables keyed "<type>.<16-hex id>.<field>", see TranslationManager).

IDs are derived from stable keys (sha1), so re-running the generator keeps every quest id and therefore every
team's saved progress. The generator is the source of the book until someone edits it in game; after an
in-game edit, the SNBT files are the source and this script must not be re-run over them (see
dev/archive/quests-notes.md). Check the output with dev/validate_quests.py.

Format facts used here (read from the FTB Quests source at tag v2101.1.36 and the 2101.1.37 FTB Library jar):
  - ids: 16 hex digits, parsed with Long.parseLong(hex, 16): must be positive and not 0 or 1
  - quest keys: x, y, shape, size, dependencies (list of hex strings), optional, hide_dependency_lines,
    tasks, rewards, tags; task/reward type ids without the "ftbquests:" namespace
  - gamestage task/reward go through FTB Library's StageHelper provider = ProgressiveStages (PoC A10);
    ProgressiveStages 3.0.5 also adds "progressivestages_required_stage" to chapters and quests (mixin), which
    hides the object until the team owns the stage
  - a quest whose tasks are ALL optional_task completes when any one of them is done (QuestObject.isCompletedRaw)
  - the SNBT reader skips whole lines that start with "//" or "#"
"""
import hashlib
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quest_layout  # noqa: E402  (dev/quest_layout.py)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'config', 'ftbquests', 'quests')
FILE_VERSION = 13  # BaseQuestFile.VERSION in 2101.1.36

AGE_ORDER = ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9',
             'finale_won']


def qid(key):
    """Stable positive 63-bit id, never 0 or 1."""
    n = int(hashlib.sha1(('firmages-quests/' + key).encode('utf-8')).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF
    if n < 2:
        n += 2
    return '%016X' % n


# ------------------------------------------------------------------------------------------------ SNBT writer
class Raw:
    """Pre-formatted SNBT scalar (numbers with type suffix)."""

    def __init__(self, text):
        self.text = text


def D(x):
    return Raw(repr(float(x)) + 'd')


def L(x):
    return Raw('%dL' % x)


def q(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def snbt(value, indent=0, comments=None):
    tab = '\t'
    if isinstance(value, Raw):
        return value.text
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return repr(value) + 'd'
    if isinstance(value, str):
        return q(value)
    if isinstance(value, list):
        if not value:
            return '[ ]'
        if all(isinstance(v, str) for v in value) and sum(len(v) for v in value) < 60 and len(value) <= 3:
            return '[' + ', '.join(q(v) for v in value) + ']'
        inner = '\n'.join(tab * (indent + 1) + snbt(v, indent + 1) for v in value)
        return '[\n' + inner + '\n' + tab * indent + ']'
    if isinstance(value, dict):
        if not value:
            return '{ }'
        lines = []
        for k in sorted(value):
            if k.startswith('//'):
                continue
            key = k if all(c.isalnum() or c in '._-+' for c in k) else q(k)
            note = value.get('//' + k)
            if note:
                lines.append(tab * (indent + 1) + '// ' + note)
            lines.append(tab * (indent + 1) + key + ': ' + snbt(value[k], indent + 1))
        return '{\n' + '\n'.join(lines) + '\n' + tab * indent + '}'
    raise TypeError(value)


# ------------------------------------------------------------------------------------------------ builders
LANG = {}


def lang(obj_type, oid, field, text):
    LANG['%s.%s.%s' % (obj_type, oid, field)] = text


BULLETS = ('- ', '* ', '• ')


class Verbatim(str):
    """A description line that stays an entry of its own, exactly as written (knapping grids)."""


def knap_grid(*rows, outside=' '):
    """TFC knapping pattern rows ('#' = stays, ' ' = knocked away) -> five Verbatim lines of the 5x5 grid,
    'X' = leave standing, '-' = knock away. A pattern smaller than 5x5 is drawn in the top-left corner: TFC 4.2.11
    KnappingPattern.matches tries every offset and the mirror image, and every square outside the shape must equal the
    recipe's "default_on": knocked away for false (the stone heads, outside=' '), left standing for true (the clay
    ingot and pickaxe head molds, outside='#'). 'X' and '-' have the same advance (6 px) in the Minecraft font ('.'
    only 2 px), so the columns line up in the quest window."""
    out = []
    for r in range(5):
        row = rows[r] if r < len(rows) else ''
        out.append(Verbatim(' '.join('X' if c == '#' else '-' for c in row.ljust(5, outside))))
    return out


def craft_grid(*rows):
    """Crafting pattern rows (a letter = that item, ' ' = empty) -> Verbatim lines, '-' for an empty square. Use
    letters with the 6 px advance of 'X' and '-' (not 'I'), so the columns line up like the knapping grids."""
    return [Verbatim(' '.join('-' if c == ' ' else c for c in row)) for row in rows]


def paragraphs(lines):
    """Description lines -> one entry per paragraph. FTB Quests draws every entry of a description list as a line of
    its own and wraps it to the width of the quest window, so a source line broken by hand ends early and the window
    wraps it again: ragged text at GUI scale 3 and auto in the 75 % quest window. Consecutive source lines join with
    one space; an empty line stays as the paragraph break; a bullet line ('- ', '* ', '• ') starts an entry of
    its own and takes the lines that follow it up to the next empty or bullet line. A Verbatim line is always an
    entry of its own."""
    out, cur = [], []
    for line in lines:
        if isinstance(line, Verbatim):
            if cur:
                out.append(' '.join(cur))
                cur = []
            out.append(str(line))
            continue
        s = line.strip()
        if s and not s.startswith(BULLETS) and cur:
            cur.append(s)
            continue
        if cur:
            out.append(' '.join(cur))
            cur = []
        if s:
            cur = [s]
        else:
            out.append('')
    if cur:
        out.append(' '.join(cur))
    return out


# task constructors: each returns (dict without id, optional title)
def t_item(i, count=1, title=None, optional=False, consume=None):
    d = {'type': 'item', 'item': {'count': 1, 'id': i}}
    if count > 1:
        d['count'] = L(count)
    if consume is not None:
        d['consume_items'] = consume
    if optional:
        d['optional_task'] = True
    return d, title


def t_any_item(ids, title, count=1):
    """Either/or: every task optional -> the quest completes with any one of them."""
    return [t_item(i, count, optional=True) for i in ids], title


def t_adv(adv, title=None):
    return {'type': 'advancement', 'advancement': adv, 'criterion': ''}, title


def t_stage(stage, title=None):
    return {'type': 'gamestage', 'stage': stage}, title


def t_check(title=None):
    return {'type': 'checkmark'}, title


def t_kill(entity=None, tag=None, value=1, title=None, optional=False):
    d = {'type': 'kill', 'entity': entity or 'minecraft:zombie', 'value': L(value)}
    if tag:
        d['entityTypeTag'] = tag
    if optional:
        d['optional_task'] = True
    return d, title


def t_boss(entity, drop, count=1):
    """A known boss as an optional shortcut (dev/decisions-while-away.md, 2026-10-14): the kill OR the item, so the
    pack's own route to the item completes the quest too. Every task optional -> either one."""
    return [t_kill(entity, value=1, optional=True), t_item(drop, count, optional=True, consume=False)]


def t_observe(target, kind='block', timer=0, title=None):
    return {'type': 'observation', 'observation_type': kind, 'to_observe': target, 'timer': L(timer)}, title


def t_dim(dim, title=None):
    return {'type': 'dimension', 'dimension': dim}, title


def t_stat(stat, value, title=None):
    return {'type': 'stat', 'stat': stat, 'value': value}, title


def r_xp(n):
    return {'type': 'xp', 'xp': n}


# Who receives a table reward (decision log 2026-10-14, "Belohnungen: Material fürs Team, Ausrüstung pro Spieler"):
# a keystone's supplies are materials for the shared base, claimed once for the fellowship (team_reward); a side
# path's comfort is personal gear or a consumable, and each player claims and chooses their own. Shared
# infrastructure (storage, capacitors, teleporters, tubes, the iris) therefore sits in the supplies tables.
def r_random(table_key):
    return {'type': 'random', 'table': table_key, 'team_reward': True}


def r_choice(table_key):
    return {'type': 'choice', 'table': table_key, 'team_reward': False}


def r_stage(stage):
    return {'type': 'gamestage', 'stage': stage, 'auto': 'invisible'}


# ------------------------------------------------------------------------------------------------ reward tables
# Items only from the table's Age (checked by dev/validate_quests.py against the age_items tags). No fresh food
# in quantity (Doc 08 section 12), no creative items, never a later-Age item.
REWARD_TABLES = {
    'dawn_supplies': ('Dawn Supplies', 'dawn', [
        ('minecraft:stick', 16), ('tfc:straw', 16), ('tfc:thatch', 4), ('tfc:rope', 4)]),
    'stone_supplies': ('Stone Age Supplies', 'age_0', [
        ('tfc:metal/ingot/copper', 2), ('tfc:ore/small_native_copper', 8), ('minecraft:charcoal', 8),
        ('minecraft:clay_ball', 16), ('tfc:ceramic/vessel', 1), ('tfc:ceramic/ingot_mold', 1)]),
    'stone_comfort': ('Stone Age Comforts', 'age_0', [
        ('tfc:ceramic/large_vessel', 1), ('tfc:metal/knife/copper', 1), ('tfc:metal/axe/copper', 1),
        ('tfc:candle', 8)]),
    'bronze_supplies': ('Bronze Age Supplies', 'age_1', [
        ('tfc:metal/ingot/bronze', 2), ('tfc:metal/ingot/tin', 2), ('tfc:metal/ingot/zinc', 2),
        ('tfc:powder/flux', 8), ('create:andesite_alloy', 8), ('create:shaft', 8), ('create:cogwheel', 4),
        ('minecraft:leather', 4)]),
    'bronze_comfort': ('Bronze Age Comforts', 'age_1', [
        ('sophisticatedbackpacks:backpack', 1), ('create:wrench', 1), ('create:goggles', 1),
        ('tfc:metal/propick/bronze', 1)]),
    'iron_supplies': ('Iron Age Supplies', 'age_2', [
        ('tfc:metal/ingot/wrought_iron', 2), ('tfc:metal/ingot/steel', 1), ('tfc:metal/ingot/brass', 2),
        ('tfc:metal/sheet/wrought_iron', 2), ('create:electron_tube', 2), ('create:fluid_pipe', 8),
        ('toms_storage:storage_terminal', 1)]),
    'iron_comfort': ('Iron Age Comforts', 'age_2', [
        ('firmages:frontier_compass', 1), ('tfc:metal/shield/wrought_iron', 1),
        ('sophisticatedbackpacks:iron_backpack', 1)]),
    'arcane_supplies': ('Arcane Age Supplies', 'age_3', [
        ('ars_nouveau:source_gem', 8), ('occultism:iesnium_ingot', 2), ('theurgy:mercury_shard', 4),
        ('tfc:gem/amethyst', 2), ('tfc:gem/lapis_lazuli', 4), ('occultism:otherworld_essence', 4),
        ('create:blaze_cake', 2)]),
    'arcane_comfort': ('Arcane Age Comforts', 'age_3', [
        ('ars_nouveau:starbuncle_charm', 1), ('ars_nouveau:whirlisprig_charm', 1), ('ars_nouveau:wixie_charm', 1),
        ('occultism:otherworld_goggles', 1)]),
    'industrial_supplies': ('Industrial Age Supplies', 'age_4', [
        ('immersiveengineering:coal_coke', 16), ('immersiveengineering:treated_wood_horizontal', 16),
        ('immersiveengineering:ingot_lead', 4), ('immersiveengineering:wirecoil_copper', 4),
        ('immersiveengineering:component_iron', 2), ('immersiveengineering:ingot_constantan', 4),
        ('tfc:metal/ingot/black_steel', 1), ('immersiveengineering:electric_lantern', 4)]),
    'industrial_comfort': ('Industrial Age Comforts', 'age_4', [
        ('immersiveengineering:hammer', 1), ('immersiveengineering:wirecutter', 1), ('create_jetpack:jetpack', 1)]),
    'electric_supplies': ('Electric Age Supplies', 'age_5', [
        ('immersiveengineering:ingot_aluminum', 4), ('immersiveengineering:plate_aluminum', 4),
        ('immersiveengineering:wirecoil_electrum', 4), ('immersiveengineering:wirecoil_steel', 4),
        ('tfc:metal/ingot/red_steel', 1), ('immersivepetroleum:lubricant_bucket', 1),
        ('create_hypertube:hypertube', 16), ('immersiveengineering:capacitor_mv', 1)]),
    'electric_comfort': ('Electric Age Comforts', 'age_5', [
        ('immersivepetroleum:speedboat', 1), ('immersivepetroleum:oil_can', 1)]),
    'information_supplies': ('Information Age Supplies', 'age_6', [
        ('mekanism:ingot_osmium', 8), ('mekanism:basic_control_circuit', 4), ('ae2:certus_quartz_crystal', 8),
        ('ae2:fluix_crystal', 4), ('ae2:silicon', 4), ('mekanism:hdpe_sheet', 4), ('tfc:metal/ingot/blue_steel', 1)]),
    'information_comfort': ('Information Age Comforts', 'age_6', [
        ('mekanism:jetpack', 1), ('mekanism:free_runners', 1), ('ae2:wireless_terminal', 1),
        ('mekanism:configurator', 1)]),
    'space_supplies': ('Space Age Supplies', 'age_7', [
        ('ad_astra:desh_ingot', 4), ('ad_astra:ostrum_ingot', 2), ('ad_astra:desh_plate', 8),
        ('draconicevolution:draconium_ingot', 4), ('ad_astra:ostrum_plate', 4), ('ad_astra:fuel_bucket', 1),
        ('mekanism:teleporter', 1)]),
    'space_comfort': ('Space Age Comforts', 'age_7', [
        ('ad_astra:tier_1_rover', 1), ('ad_astra:zip_gun', 1), ('draconicevolution:wyvern_capacitor', 1)]),
    'quantum_supplies': ('Quantum Age Supplies', 'age_8', [
        ('ad_astra:calorite_ingot', 4), ('draconicevolution:awakened_draconium_ingot', 2), ('ad_astra:ice_shard', 2),
        ('evolvedmekanism:alloy_hypercharged', 2), ('mekanism:sps_casing', 4),
        ('evolvedmekanism:overclocked_energy_cube', 1), ('evolvedmekanism:overclocked_control_circuit', 4)]),
    'quantum_comfort': ('Quantum Age Comforts', 'age_8', [
        ('draconicevolution:draconic_capacitor', 1), ('draconicevolution:draconic_pickaxe', 1)]),
    'singularity_supplies': ('Singularity Age Supplies', 'age_9', [
        ('draconicevolution:small_chaos_frag', 4), ('sgjourney:raw_naquadah', 8), ('sgjourney:naquadah', 4),
        ('draconicevolution:chaos_shard', 1), ('sgjourney:naquadah_iris', 1)]),
    'singularity_comfort': ('Singularity Age Comforts', 'age_9', [
        ('draconicevolution:chaotic_capacitor', 1), ('sgjourney:gdo', 1)]),
}


def table_long_id(key):
    return int(qid('table/' + key), 16)


# ------------------------------------------------------------------------------------------------ content
# Quest spec: dict(key, title, sub, desc[list], tasks[list of (task, title) or ([tasks], title)], deps[keys],
#                  rewards[list], kind in entry|step|keystone|goal|side|explain|info, icon)
# desc: source lines; '' is a paragraph break. The lines of a paragraph become one entry (paragraphs()), so line
# breaks inside a paragraph are only source layout and the quest window wraps the text itself.
# Proof of work (firmages-core SPEC 35): the counts come from the mod's tallies.json, the one source; the generator
# writes one hidden advancement per Age (kubejs/data/firmages/advancement/proof/<age>.json, criterion firmages:tally)
# for the Chronicle's "Proof of Work" quests.
TALLIES_JSON = os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'resources', 'data', 'firmages', 'firmages_shrine',
                            'tallies.json')
TALLIES = json.load(open(TALLIES_JSON, encoding='utf-8'))
TALLY_NAME = {'c:ingots/steel': 'steel ingots', 'c:ingots/aluminum': 'aluminium ingots', 'c:ingots/osmium': 'osmium ingots',
              'c:ingots/desh': 'desh ingots', 'c:ingots/calorite': 'calorite ingots'}
PROOF_ADV_DIR = os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'advancement', 'proof')


def tally(age):
    """'384 steel ingots' for the offering made in `age`."""
    t = TALLIES[age]
    return '{:,} {}'.format(t['count'], TALLY_NAME[t['tag']])


def proof_advancements():
    out = {}
    for age, t in sorted(TALLIES.items()):
        cond = {'tag': t['tag'], 'count': t['count'], 'age': t.get('age', age)}
        out[os.path.join(PROOF_ADV_DIR, age + '.json')] = {'criteria': {'tally': {'trigger': 'firmages:tally', 'conditions': cond}}}
    return out


def proof_quest(chapter, age):
    """The Age's proof of work as a side quest from the entry: done when the fellowship's Tally Chutes took the count."""
    return Q('%s/side/proof' % chapter, 'Proof of Work', t_adv('firmages:proof/' + age, 'Feed %s into Tally Chutes' % tally(age)),
             kind='side', icon='firmages:tally_chute',
             desc=['Feed %s into Tally Chutes in this Age. They keep what they take; a chute or the heart shows the'
                   % tally(age), 'count.'])


def proof_line(age):
    """One entry line from the Industrial Age on: what the offering will ask, so the chutes can run from the start."""
    return 'Caelum asks for proof of work this time: %s fed into Tally Chutes in this Age.' % tally(age)


def Q(key, title, tasks, deps=(), desc=(), sub=None, kind='step', rewards=None, icon=None):
    return dict(key=key, title=title, tasks=tasks if isinstance(tasks, list) else [tasks], deps=list(deps),
                desc=list(desc), sub=sub, kind=kind, rewards=rewards, icon=icon)


XP = {'dawn': 10, 'age_0': 20, 'age_1': 35, 'age_2': 50, 'age_3': 65, 'age_4': 80, 'age_5': 100, 'age_6': 120,
      'age_7': 140, 'age_8': 160, 'age_9': 200}


def age_chapter(key, filename, title, subtitle, icon, stage, order, entry, strands, goal, sides, explains,
                supplies, comfort, ring=None, bridge=None, routes=None):
    """strands: list of (name, [quests], keystone-quest). sides: list of (attach_key, [quests]).
    ring: "Raise the ring" quest (kind 'ring') for the Age's shrine ring.
    bridge: a required step between one keystone (its deps) and the goal, drawn on the main route; the goal depends
    on it as well as on every keystone.
    routes: quests with their own keystone deps between the keystones and the goal; the goal then depends on the
    routes alone and needs any one of them (FTB Quests dependency_requirement one_completed): magic stays optional."""
    return dict(key=key, filename=filename, title=title, subtitle=subtitle, icon=icon, stage=stage, order=order,
                group='ages', entry=entry, strands=strands, goal=goal, sides=sides, explains=explains,
                supplies=supplies, comfort=comfort, ring=ring, bridge=bridge, routes=routes)


def ring_quest(chapter_key, age_index, ring_name, icon, desc):
    """Observation of the finished ring: the heart reports ready=true while the current tier's rings stand, and
    awakened=N while age_N is the highest Age (SPEC section 7.8), so the quest only ticks in its own Age. The ring
    itself is required (the shrine refuses the offering without it); the quest is optional in the book because the
    goal already waits for the Age the shrine grants."""
    return Q('%s/ring' % chapter_key, 'Raise %s' % ring_name,
             t_observe('firmages:shrine_heart[awakened=%d,ready=true]' % age_index, kind='block_state',
                       title='Look at the heart with %s complete' % ring_name),
             kind='ring', icon=icon, sub='The shrine ring of this Age', desc=desc)


# ---------------------------------------------------------------- Dawn
DAWN = age_chapter(
    'dawn', 'dawn', 'Dawn', ['Prologue: stone, straw and the first spark.'], 'tfc:firestarter', 'dawn', 0,
    entry=Q('dawn/entry', 'Welcome to the Wild', t_stage('dawn'), kind='entry', icon='tfc:rock/loose/granite',
            sub='The prologue before the first Age',
            desc=['Dawn gives you nothing: no pick, no clay, no fire.',
                  '',
                  'Three strands lead out of Dawn: Stone, Shelter and Hunt. Share them out.',
                  'Read "By Hand" and "Hands and Keys" in The Firmament first.',
                  '',
                  'Nights are dark. Light a fire and make torches before the first one (see "Before Dark").',
                  'When all three strands are done, the first fire opens the Stone Age for all of you.']),
    strands=[
        ('Stone', [
            Q('dawn/stone/rock', 'Just a Rock', t_adv('tfc:story/find_rock'),
              desc=['Right-click loose rocks with an empty hand; any kind works.',
                    '',
                    'Gather about ten. Every stone tool head is knapped from them (see "Stone Tools").'],
              icon='tfc:rock/loose/granite'),
            Q('dawn/stone/sticks', 'A Bundle of Sticks', t_item('minecraft:stick', 8),
              desc=['Pick up twigs under trees with an empty hand; each makes a stick on the ground.',
                    'Leaves cut with a stone knife drop sticks too.']),
            # Patterns: TFC 4.2.11 data/tfc/recipe/knapping/stone/<tool>_head/<rock group>[_n].json, identical for all
            # four rock groups (igneous_extrusive, igneous_intrusive, metamorphic, sedimentary); checked against the
            # jar on 2026-10-14. Axe: tfc:knapping/stone/axe_head/<group>. Knapping type tfc:rock: input 2 of
            # #tfc:rock_knapping, amount_to_consume 1, consume_after_complete false (the first click uses the rock).
            # Shaft: tfc:crafting/stone/axe/<group>, head above #c:rods/wooden (sticks and twigs) or a bone.
            Q('dawn/stone/tool', 'Stone Tools', t_adv('tfc:story/stone_age'),
              desc=['Hold two or more loose rocks of one kind and right-click into the air. A 5x5 grid of stone opens.',
                    '- Click a square to knock it away. It does not come back.',
                    '- The first click uses one rock. Closing the grid early wastes it.',
                    '- When only the shape is left, take the head from the slot on the right.',
                    '',
                    'The axe head (X = leave standing, - = knock away):',
                    *knap_grid(' #   ', '#### ', '#####', '#### ', ' #   '),
                    '',
                    'Craft the head with a stick, a twig or a bone on the ground (see "By Hand"): a stone axe.',
                    'The knife and the other heads are in "Knapping Patterns" at the bottom of this chapter.'],
              icon='tfc:stone/axe/sedimentary'),
        ], Q('dawn/stone/key', 'Stone Kit', t_adv('tfc:story/logging', 'Fell a tree with a stone axe'),
             kind='keystone', icon='tfc:stone/axe_head/igneous_extrusive',
             desc=['Fell a tree with your stone axe. With axe, knife, hammer and shovel you have a full stone kit.'])),
        ('Shelter', [
            Q('dawn/shelter/straw', 'Grasping at Straws', t_adv('tfc:story/get_straw'),
              desc=['Cut tall grass with a stone knife to get straw.'], icon='tfc:straw'),
            Q('dawn/shelter/thatch', 'Thatch', t_item('tfc:thatch', 8),
              desc=['Lay four straw on one block, then sneak and right-click the pile: thatch, the only building',
                    'block of Dawn.']),
        ], Q('dawn/shelter/key', 'Shelter', t_observe('tfc:thatch_bed', title='Look at a finished thatch bed'),
             kind='keystone', icon='tfc:thatch_bed',
             desc=['Right-click two thatch blocks side by side with a large raw hide: a thatch bed.',
                   'It sets your spawn but does not skip the night. Beds that do come with the Iron Age.'])),
        ('Hunt', [
            Q('dawn/hunt/javelin', 'A Stone Javelin',
              t_any_item(['tfc:stone/javelin/igneous_extrusive', 'tfc:stone/javelin/igneous_intrusive',
                          'tfc:stone/javelin/metamorphic', 'tfc:stone/javelin/sedimentary'], 'Any stone javelin'),
              desc=['Knap a javelin head (see "Knapping Patterns") and craft it with a stick on the ground.',
                    'Throw it or stab with it.'],
              icon='tfc:stone/javelin/sedimentary'),
            Q('dawn/hunt/kill', 'Hunter', t_kill(tag='tfc:animals', value=1, title='Kill any wild animal'),
              desc=['Hunt an animal. Large prey drops large hides; carry a knife to butcher it.']),
        ], Q('dawn/hunt/key', 'Raw Hide',
             t_any_item(['tfc:small_raw_hide', 'tfc:medium_raw_hide', 'tfc:large_raw_hide'], 'Any raw hide'),
             kind='keystone', icon='tfc:large_raw_hide',
             desc=['Bring home a raw hide. A large one makes the thatch bed.'])),
    ],
    goal=Q('dawn/goal', 'The First Spark',
           [t_item('tfc:firestarter', title='A firestarter'), t_adv('tfc:story/firepit', 'Light a firepit')],
           kind='goal', icon='tfc:firestarter', sub='Goal of Dawn: grants the Stone Age',
           desc=['Lay two sticks on one block and sneak and right-click the pile: a firestarter. Throw a log, three',
                 'sticks and some straw on one block and hold right-click on it with the firestarter until the',
                 'firepit lights.',
                 '',
                 'Fire opens the Stone Age for all of you: clay, pit kilns, the first copper and the crafting grid (E).']),
    sides=[
        # The first night (More Darkness, no skipping bed): the firepit advancement also counts for the goal, which
        # still waits for the three keystones, so lighting it early changes nothing about the Age grant.
        ('dawn/stone/key', [
            Q('dawn/side/torch', 'Before Dark', t_item('tfc:torch', title='A torch'), kind='side', icon='tfc:torch',
              desc=['Drop one log, three sticks and some straw on one block. Hold right-click on it with a firestarter',
                    '(two sticks) until the firepit lights.',
                    '',
                    'Heat a stick in the firepit\'s right slot: it becomes two torches. A torch lights your way in hand;',
                    'a placed torch burns about 36 minutes. Zombies and spiders walk at night.']),
        ]),
        ('dawn/hunt/javelin', [
            Q('dawn/side/berries', 'Berries and Herbs',
              t_any_item(['tfc:food/blackberry', 'tfc:food/raspberry', 'tfc:food/blueberry',
                          'tfc:food/elderberry', 'tfc:food/gooseberry', 'tfc:food/strawberry',
                          'tfc:food/cloudberry', 'tfc:food/snowberry', 'tfc:food/bunchberry',
                          'tfc:food/cranberry', 'tfc:food/wintergreen_berry'], 'Any wild berry'),
              kind='side', icon='tfc:food/blueberry',
              desc=['Gather wild berries. You never thirst here, and a varied diet makes you hardier.']),
        ]),
    ],
    explains=[
        # Felix 2026-10-15 (HW2A): the inventory grid is closed in Dawn (firmages-core SPEC 19.3); ground crafting on a
        # TFC placed-item pile is the Dawn replacement. Teaching quest (dev/validate_quests.py STYLE_TEACHING).
        Q('dawn/info/hand', 'By Hand', t_check(), kind='explain', icon='tfc:stone/axe/sedimentary',
          desc=['You have no crafting grid yet. Hold an item and press V to lay it on a block; up to four items fit',
                'on one block, one in each corner.',
                '',
                'Lay the parts together, then sneak and right-click the pile with an empty hand. If they make',
                'something, you get it. Where on the block each part lies does not matter.',
                '',
                'Four straw make thatch, two sticks a firestarter, a stone head and a stick a stone tool.',
                'The crafting grid (E) opens with the Stone Age.']),
        Q('dawn/info/rules', 'What Dawn Withholds', t_check(), kind='explain', icon='minecraft:barrier',
          desc=['In Dawn you cannot dig clay, build pit kilns or use metal. There is no crafting grid: you craft on',
                'the ground (see "By Hand"), and only stone and obsidian tools, the firestarter, straw, thatch, sticks',
                'and rope. The grid opens with the Stone Age.',
                '',
                'Logs and stone do not break by hand: the stone axe fells trees, stone waits for the copper pickaxe.',
                'Every tree drops the log of its wood class: oak, birch, spruce, jungle, acacia, dark oak, mangrove or',
                'cherry. Logs of one class stack, whatever tree they came from.',
                '',
                'No recipe viewer until the Bronze Age; this book draws every pattern.']),
        # Patterns: tfc:knapping/stone/knife_head/<group>_1 (two blades from one rock), hammer_head/<group>,
        # shovel_head/<group>, hoe_head/<group>_1 (two heads), javelin_head/<group> (TFC 4.2.11, all four rock
        # groups alike). Hammer, shovel and the single hoe are "default_on": false: the rest of the grid must be
        # knocked away; any offset and the mirror image match.
        Q('dawn/info/knapping', 'Knapping Patterns', t_check(), kind='explain',
          icon='tfc:stone/knife_head/sedimentary',
          desc=['Every head is laid with a stick on one block and crafted on the ground (see "By Hand").',
                'X = leave standing, - = knock away.',
                'Mirrored shapes work too.',
                '',
                'Knife blades, two from one rock:',
                *knap_grid('#  # ', '## ##', '## ##', '## ##', '## ##'),
                '',
                'Hammer head, breaks logs into sticks:',
                *knap_grid('#####', '#####', '  #  '),
                '',
                'Shovel head, digs faster:',
                *knap_grid('###', '###', '###', '###', ' # '),
                '',
                'Hoe heads, two from one rock:',
                *knap_grid('#####', '##   ', '     ', '#####', '##   '),
                '',
                'Javelin head, for the hunt:',
                *knap_grid('###  ', '#### ', '#####', ' ### ', '  #  '),
                '',
                'There is no stone pickaxe. The first pickaxe is cast in copper in the Stone Age.']),
    ],
    supplies='dawn_supplies', comfort='dawn_supplies')

# ---------------------------------------------------------------- Stone Age
STONE = age_chapter(
    'stone', 'stone_age', 'Stone Age', ['Clay, pit kilns and the first cast copper.'], 'firmages:hearthstone',
    'age_0', 1,
    entry=Q('stone/entry', 'The Stone Age Dawns', t_stage('age_0'), kind='entry', icon='tfc:ceramic/vessel',
            sub='The first Age',
            desc=['Fire is tamed. Clay is yours now, and with it pottery, molds and copper. Your hands have learned:',
                  'the crafting grid (E) is open.',
                  '',
                  'Four strands lead to the Hearthstone: Fire & Clay, First Metal, Camp, and Hearth & Shrine.',
                  'The last one raises the shrine where the Hearthstone is offered to Caelum.']),
    strands=[
        ('Fire & Clay', [
            Q('stone/clay/find', 'Locating Clay', t_adv('tfc:story/find_clay'),
              desc=['Clay lies under grass near water. Athyrium fern, canna, goldenrod, pampas grass, perovskia and',
                    'water canna grow above it.'],
              icon='minecraft:clay_ball'),
            Q('stone/clay/knap', 'Clay Forming', t_adv('tfc:story/knap_clay'),
              desc=['Knap five clay into a vessel, a jug, a bowl or a mold (see "Clay Patterns").']),
            # TFC 4.2.11 pit kiln (field guide getting_started/pit_kiln, ServerConfig pitKilnTicks 7200 = 6 min,
            # pitKilnTemperature 1400): up to four items set down with the place-block key (tfc.key.place_block = V in
            # config/defaultoptions/keybindings.txt), eight straw (tfc:pit_kiln_straw) or two thatch, eight logs.
            Q('stone/clay/kiln', 'Potter', t_adv('tfc:story/pit_kiln'),
              desc=['Fire clay in a pit kiln:',
                    '- Dig a hole one block deep. Hold an unfired piece, look at the floor of the hole and press V.',
                    'Up to four pieces fit.',
                    '- Right-click the pile with eight straw, then with eight logs.',
                    '- Light the top with a firestarter.',
                    '',
                    'After about six minutes the fire is out and the pottery is fired.']),
            Q('stone/clay/vessel', 'Small Vessel', t_item('tfc:ceramic/vessel'),
              desc=['Fill it with ore and fire it in a pit kiln: the copper melts. A firepit never gets hot enough.']),
        ], Q('stone/clay/key', 'Mold Set',
             [t_item('tfc:ceramic/ingot_mold', title='Fired ingot mold'),
              t_item('tfc:ceramic/pickaxe_head_mold', title='Fired pickaxe head mold')],
             kind='keystone', icon='tfc:ceramic/ingot_mold',
             desc=['Fire an ingot mold and a pickaxe head mold. They take liquid copper.'])),
        ('First Metal', [
            Q('stone/metal/ore', 'Native Copper',
              t_any_item(['tfc:ore/small_native_copper', 'tfc:ore/small_malachite', 'tfc:ore/small_tetrahedrite'],
                         'Any small copper ore piece'),
              desc=['Copper ore pieces lie on the surface above copper veins.'], icon='tfc:ore/small_native_copper'),
            # TFC 4.2.11 charcoal pit (field guide metalworking/charcoal_pit): sneak + right-click with a log places a
            # log pile (InteractionManager checks isShiftKeyDown), light it, then close it with non-flammable blocks.
            Q('stone/metal/charcoal', 'A Better Fuel', t_adv('tfc:story/charcoal'),
              desc=['Make charcoal in a charcoal pit:',
                    '- Sneak and right-click the ground with logs: a log pile. Right-click it with more logs.',
                    '- Cover every side with dirt, leave the top open and light it with a firestarter.',
                    '- Close the gap with dirt.',
                    '',
                    'When the smoke stops, dig the charcoal out with a shovel. The Shrine Heart and the Hearthstone need it.'],
              icon='minecraft:charcoal'),
            # Casting: field guide getting_started/finding_ores (casting interface, mold in the slot, right-click the
            # cooled mold to take the item out); small ore 10 mB, ingot 100 mB.
            Q('stone/metal/ingot', 'Copper Ingot', t_item('tfc:metal/ingot/copper', 2),
              desc=['Take the vessel out while it glows and right-click it. Put a fired mold in the slot; the copper',
                    'pours in. Ten small pieces make one ingot.',
                    '',
                    'When the mold has cooled, right-click it to take the ingot out.']),
            Q('stone/metal/head', 'Pickaxe Head', t_item('tfc:metal/pickaxe_head/copper'),
              desc=['Right-click the glowing vessel and put the fired pickaxe head mold in the slot.']),
        ], Q('stone/metal/key', 'Copper Pickaxe', t_item('tfc:metal/pickaxe/copper'), kind='keystone',
             desc=['Put the head on a stick. Your first pickaxe: now you can mine.'])),
        ('Camp', [
            Q('stone/camp/seeds', 'Gatherer', t_adv('tfc:world/seeds'),
              desc=['Break a wild crop to get its seeds.'], icon='tfc:seeds/wheat'),
            Q('stone/camp/field', 'First Field', t_observe('tfc:farmlands', kind='block_tag',
                                                           title='Look at your farmland'),
              desc=['Till soil with a hoe and plant the seeds. Crops need water and time.'],
              icon='tfc:seeds/wheat'),
            Q('stone/camp/jug', 'A Jug of Water', t_item('tfc:ceramic/jug'),
              desc=['Fire a jug and fill it at a river. A pot needs water before it cooks.']),
            Q('stone/camp/pot', 'A Pot of Soup', t_adv('tfc:story/pot'),
              desc=['Put a fired ceramic pot on the firepit and cook a soup.'], icon='tfc:ceramic/pot'),
        ], Q('stone/camp/key', 'Stocked Camp', t_item('tfc:ceramic/large_vessel'), kind='keystone',
             desc=['Fill a large vessel with food and seal it. Sealed, food rots far slower.'])),
        ('Hearth & Shrine', [
            # Pattern: kubejs/server_scripts/recipes/age_0/stone_age.js, firmages:knapping/unfired_hearth_idol.
            Q('stone/hearth/unfired', 'Unfired Hearth Idol', t_item('firmages:unfired_hearth_idol', 2),
              desc=['Knap two, five clay each: one for the Shrine Heart, one for the Hearthstone.',
                    '',
                    'X = leave standing, - = knock away:',
                    *knap_grid(' ### ', '#####', ' ### ', ' ### ', '## ##')]),
            Q('stone/hearth/idol', 'Hearth Idol', t_item('firmages:hearth_idol', 2),
              desc=['Fire both in a pit kiln, like any other pottery. One becomes the heart of the shrine,',
                    'the other the Hearthstone.']),
            # TFC 4.2.11: saw = blade over a stick (tfc:crafting/metal/saw/copper). Wood classes (HW1A,
            # recipes/age_0/wood_classes.js): saw + vanilla log = 2 planks, 4 vanilla planks = crafting table (in the
            # block tag tfc:workbenches, dev/gen_wood_classes.py), saw + planks = 4 lumber of the class's lead species.
            # 4 loose rocks = cobblestone (rock/cobble/<rock>). The shrine recipes below are 3x3
            # (kubejs/server_scripts/recipes/age_0/stone_age.js).
            Q('stone/hearth/bench', 'Workbench',
              t_observe('tfc:workbenches', kind='block_tag', title='Look at your placed crafting table'),
              icon='minecraft:crafting_table',
              desc=['Build a crafting table. The shrine is crafted in its 3x3 grid; the inventory grid is 2x2.',
                    '',
                    '- Knap a saw blade mold (see "Clay Patterns") and fire it. Right-click the glowing vessel and',
                    'put the mold in the slot.',
                    '- Put the blade above a stick: a saw.',
                    '- A saw and a log make two planks. Four planks make a crafting table.',
                    '- A saw and planks make four lumber: barrels, chests and tool racks take lumber.',
                    '',
                    'Four loose rocks in the 2x2 grid make a cobblestone. The shrine takes many.']),
            Q('stone/shrine/heart', 'The Shrine Heart',
              t_observe('firmages:shrine_heart', title='Look at your placed Shrine Heart'), icon='firmages:shrine_heart',
              desc=['Craft it from a Hearth Idol (H), cobblestone (S) and charcoal (C):',
                    *craft_grid(' C ', 'SHS', ' S '),
                    '',
                    'Leave 21 by 21 blocks free: each Age adds a ring.']),
        ], Q('stone/shrine/key', 'Raise the Shrine',
             t_observe('firmages:shrine_heart[ready=true]', kind='block_state',
                       title='Look at the heart of the finished Hearth Circle'),
             kind='keystone', icon='firmages:offering_plinth',
             desc=['Build the Hearth Circle around the heart. Right-click the heart with an empty hand: it shows every',
                   'missing block as a ghost.',
                   '',
                   'You need eight cobblestone, four log posts two high with thatch on top, and the Offering Plinth',
                   'two blocks from the heart. Craft the plinth from four cobblestone (S):',
                   *craft_grid(' S ', 'SSS'),
                   '',
                   'When the circle is whole, the heart glows. Right-click it again to hear what Caelum asks for.'])),
    ],
    goal=Q('stone/goal', 'Offer the Hearthstone at the Shrine',
           t_stage('age_1', 'Offer the Hearthstone at the shrine'), kind='goal', icon='firmages:hearthstone',
           sub='Goal of the Stone Age: Caelum opens the Bronze Age',
           desc=['Craft the Hearthstone from the fired Hearth Idol (H), copper ingots (K) and charcoal (C):',
                 *craft_grid(' K ', 'CHC', ' K '),
                 '',
                 'Lay it on the plinth and kindle the heart with a firestarter. Then sneak and hold right-click on the heart',
                 'with an empty hand. Friends who pray with you make it faster.',
                 '',
                 'Caelum keeps the Hearthstone and opens the Bronze Age. From then on a thin beam over the heart',
                 'shows the way home.']),
    sides=[
        ('stone/clay/kiln', [
            Q('stone/side/candle', 'Tallow and Candles', t_item('tfc:candle', 4), kind='side',
              desc=['Render fat into tallow and make candles. Light without fire hazards.']),
        ]),
        ('stone/camp/seeds', [
            Q('stone/side/friend', 'A New Friend', t_adv('tfc:world/familiarity'), kind='side',
              icon='tfc:seeds/wheat',
              desc=['Feed a wild animal to familiarize it. Tame animals are the start of a farm.']),
        ]),
    ],
    explains=[
        Q('stone/info/food', 'Food Decay and Nutrition', t_check(), kind='explain', icon='tfc:ceramic/large_vessel',
          desc=['Food rots. Sealed large vessels, salting, drying and cooking slow it down.',
                'Eat from every food group: grain, fruit, vegetables, protein and dairy.']),
        Q('stone/info/heat', 'Heat Colors', t_check(), kind='explain', icon='minecraft:charcoal',
          desc=['Hot items glow. Hover over one to read its heat; each metal melts at its own.']),
        Q('stone/info/cavein', 'Cave-ins', t_check(), kind='explain', icon='tfc:rock/loose/granite',
          desc=['Mining raw stone can collapse the ceiling. Support beams come with the Bronze Age.']),
        # Patterns: TFC 4.2.11 data/tfc/recipe/knapping/ceramic/unfired_<name>.json (knapping type tfc:clay, five clay,
        # used up when the shape is taken). Ingot and pickaxe head molds are "default_on": true: the squares outside
        # their 4x5 and 5x4 shapes stay standing. The saw blade mold is a full 5x5 shape.
        Q('stone/info/clay', 'Clay Patterns', t_check(), kind='explain', icon='tfc:ceramic/unfired_vessel',
          desc=['Hold five clay and right-click into the air. Knock squares away as with stone.',
                'X = leave standing, - = knock away. Mirrored shapes work too.',
                '',
                'Small vessel, melts ore:',
                *knap_grid(' ### ', '#####', '#####', '#####', ' ### '),
                '',
                'Ingot molds, two at once:',
                *knap_grid('####', '#  #', '#  #', '#  #', '####', outside='#'),
                '',
                'Pickaxe head mold:',
                *knap_grid('#####', '#   #', ' ### ', '#####', outside='#'),
                '',
                'Saw blade mold, for the workbench:',
                *knap_grid('  ###', '   ##', '#   #', '#    ', '###  '),
                '',
                'Jug, carries water:',
                *knap_grid(' #   ', '#### ', '### #', '#### ', '###  '),
                '',
                'Pot, cooks soup on the firepit:',
                *knap_grid('#   #', '#   #', '#   #', '#####', ' ### '),
                '',
                'Bowls, four at once:',
                *knap_grid('#   #', ' ### ', '     ', '#   #', ' ### '),
                '',
                'Large vessel, keeps food when sealed:',
                *knap_grid('#   #', '#   #', '#   #', '#   #', '#####')]),
        # Every home block of the Stone Age (age_items/age_0: Every Compat db/ and mcb/ in TFC woods, Supplementaries timber
        # frames, daub and rope). Recipes from the 0.9.1 dump at age_1 (/fa_dump_full): db/ palisade, beam, support, seat;
        # mcb/ rope, log and rail bridge, pier, stairs; FirmaSupplementaries daub and jute rope. Sconces are Iron Age.
        Q('stone/info/home', 'A Home', t_check(), kind='explain', icon='supplementaries:timber_frame',
          desc=['Any wood works:',
                '- Palisade: two logs side by side. Beam: two stripped logs stacked.',
                '- Support: three planks in an L. Seat: a slab above a fence.',
                '- Timber frame: four sticks in a diamond. Cross brace: a stick in each corner. Brace: sticks in two',
                'opposite corners and both sides. Daub in the middle: the daub version.',
                '- Daub: two clay and two straw, crossed. Rope: three jute fiber stacked.',
                '- Rope bridge: string in the top corners, three slabs below. Log bridge: the same with fences.',
                'Rail bridge: fences in the corners over fence, slab, fence. Pier: log, fence, log.',
                'Six bridges as a staircase: bridge stairs.']),
        # Wood classes (HW1A, dev/wood_classes.toml): natural trunks drop the class log (loot from
        # dev/gen_wood_classes.py), recipes/age_0/wood_classes.js R5 to R8 and the sapling swap. No recipe viewer
        # before the Bronze Age, so the class table lives here.
        Q('stone/info/woods', 'Wood Classes', t_check(), kind='explain', icon='minecraft:oak_log',
          desc=["For a tree's own wood, put eight class logs or planks around its sapling. Its own log turns back",
                'into the class log, its sapling into the class sapling.',
                '',
                '- Oak: oak, ash, maple, sycamore, beech, black oak',
                '- Birch: birch, aspen, eucalyptus, ginkgo, poplar, rainbow eucalyptus',
                '- Spruce: spruce, pine, douglas fir, white cedar, sequoia, cypress, araucaria, red cedar, kauri',
                '- Jungle: kapok, palm, teak, hevea, tualang, mahoe',
                '- Acacia: acacia, baobab, gum arabic',
                '- Dark oak: blackwood, hickory, chestnut, ironwood, ipe',
                '- Mangrove: mangrove, willow, mahogany',
                '- Cherry: rosewood, fig, rubber fig',
                '',
                'Golden bamboo and bamboo turn into each other. Class saplings grow on any soil.']),
    ],
    supplies='stone_supplies', comfort='stone_comfort')

# ---------------------------------------------------------------- Bronze Age
BRONZE = age_chapter(
    'bronze', 'bronze_age', 'Bronze Age', ['The first alloys. New veins can be found.'], 'firmages:sky_disc',
    'age_1', 2,
    entry=Q('bronze/entry', 'The Bronze Age Dawns', t_stage('age_1'), kind='entry',
            icon='tfc:metal/ingot/bronze', sub='The second Age',
            desc=['Tin and zinc veins wake. Anvils, alloys and the first kinetic machines follow.',
                  '',
                  'The recipe viewer opens: hover over an item and press R to see how it is made, U to see what it',
                  'is used for. F3 now shows coordinates.',
                  '',
                  'Four strands lead to the Sky Disc: Smithing, Prospecting, Alloys and Kinetics.']),
    strands=[
        ('Smithing', [
            # TFC 4.2.11 charcoal forge (field guide metalworking/charcoal_forge): a charcoal pile of 7 or 8 layers,
            # five stone blocks around it (four sides and below), air above, sky in view; charcoal places a pile layer
            # on right-click (no sneak).
            Q('bronze/smith/forge', 'Forging', t_adv('tfc:story/forge'),
              desc=['Build a charcoal forge:',
                    '- Make a hole one block deep, lined with stone on its four sides and below.',
                    '- Right-click into it with charcoal until the pile is seven or eight layers high.',
                    '- Keep the sky open above it and light it with a firestarter.',
                    '',
                    'Right-click the forge: fuel goes below, the metal to heat above.'], icon='minecraft:charcoal'),
            Q('bronze/smith/stone_anvil', 'Stone Anvil', t_adv('tfc:story/stone_anvil'),
              desc=['Right-click raw igneous rock with a stone hammer to make a stone anvil.']),
            Q('bronze/smith/quern', 'The Grind', t_adv('tfc:story/quern'),
              desc=['Craft a quern and a handstone.'], icon='tfc:quern'),
            Q('bronze/smith/flux', 'In Flux', t_item('tfc:powder/flux', 4),
              desc=['Grind borax, lime or other flux stones in the quern. Welding needs flux.']),
            Q('bronze/smith/weld', 'First Weld', t_adv('tfc:story/welding'),
              desc=['Weld two hot ingots into a double ingot on the stone anvil.'],
              icon='tfc:metal/double_ingot/copper'),
        ], Q('bronze/smith/key', 'Copper Anvil', t_item('tfc:metal/anvil/copper'), kind='keystone',
             desc=['Weld copper double ingots into a copper anvil. Tools and sheets are smithed on it.'])),
        ('Prospecting', [
            Q('bronze/prosp/tin', 'Tin on the Surface', t_item('tfc:ore/small_cassiterite'),
              desc=['Cassiterite pieces on the ground point to a tin vein below.']),
            Q('bronze/prosp/zinc', 'Zinc on the Surface', t_item('tfc:ore/small_sphalerite'),
              desc=['Sphalerite pieces point to a zinc vein. Kinetic machines need zinc.']),
            Q('bronze/prosp/propick', 'Prospector', t_adv('tfc:story/propick'),
              desc=['Smith a prospector\'s pick on an anvil (Smithing strand). It finds ore through stone.'],
              icon='tfc:metal/propick/copper'),
            Q('bronze/prosp/mineral', 'Mineral Prospector',
              t_any_item(['precisionprospecting:metal/mineral_prospector/copper',
                          'precisionprospecting:metal/mineral_prospector/bronze',
                          'precisionprospecting:metal/mineral_prospector/bismuth_bronze',
                          'precisionprospecting:metal/mineral_prospector/black_bronze'], 'Any mineral prospector'),
              icon='precisionprospecting:metal/mineral_prospector/copper',
              desc=['A mineral prospector shows the direction and size of a vein.']),
            Q('bronze/prosp/support', 'Hold the Ceiling',
              t_observe('tfc:support_beams', kind='block_tag', title='Look at a placed support beam'),
              icon='tfc:wood/support/oak',
              desc=['Saw support beams and place them in your mine. They stop cave-ins nearby.']),
        ], Q('bronze/prosp/key', 'First Mine',
             [t_item('tfc:metal/ingot/tin', 4, title='Tin ingots'), t_item('tfc:metal/ingot/zinc', 2, title='Zinc ingots')],
             kind='keystone', icon='tfc:ore/normal_cassiterite',
             desc=['Mine the tin and zinc veins you found and cast their ingots.'])),
        ('Alloys', [
            Q('bronze/alloy/fireclay', 'Fireproof', t_adv('tfc:story/fire_clay'),
              desc=['Mix clay with kaolinite and graphite powder into fire clay.'], icon='tfc:fire_clay'),
            Q('bronze/alloy/crucible', 'The Crucible', t_item('tfc:crucible'),
              desc=['Knap and fire a crucible. It mixes metals into alloys.']),
            Q('bronze/alloy/ingot', 'Bronze',
              t_any_item(['tfc:metal/ingot/bronze', 'tfc:metal/ingot/bismuth_bronze', 'tfc:metal/ingot/black_bronze'],
                         'Any bronze ingot'),
              icon='tfc:metal/ingot/bronze',
              desc=['Melt copper with tin, bismuth or gold and silver in the right ratio.']),
            Q('bronze/alloy/tool', 'First Bronze Tool', t_adv('tfc:story/bronze_age'),
              desc=['Smith any bronze tool.'], icon='tfc:metal/axe/bronze'),
        ], Q('bronze/alloy/key', 'Bronze Anvil',
             t_any_item(['tfc:metal/anvil/bronze', 'tfc:metal/anvil/bismuth_bronze', 'tfc:metal/anvil/black_bronze'],
                        'Any bronze anvil'),
             kind='keystone', icon='tfc:metal/anvil/bronze',
             desc=['A bronze anvil forges bronze double sheets: the Sky Disc needs one.'])),
        ('Kinetics', [
            Q('bronze/kin/nugget', 'Zinc Nuggets', t_item('create:zinc_nugget', 9),
              desc=['Split a zinc ingot into nine nuggets in the crafting grid.']),
            Q('bronze/kin/alloy', 'Andesite Alloy', t_item('create:andesite_alloy', 4),
              desc=['Make four andesite alloy from andesite cobble and zinc nuggets. Andesite is grey volcanic rock.',
                    'None nearby? Travel: the rock changes every few hundred blocks.']),
            Q('bronze/kin/wheel', 'Water Wheel', t_item('create:water_wheel'),
              desc=['A water wheel in flowing water gives rotational force.']),
            Q('bronze/kin/shafts', 'Shafts and Cogs',
              [t_item('create:shaft', 8, title='Shafts'), t_item('create:cogwheel', 4, title='Cogwheels')],
              icon='create:cogwheel', desc=['Carry the rotation to your machines.']),
            Q('bronze/kin/saw', 'Mechanical Saw', t_item('create:mechanical_saw'),
              desc=['Build a mechanical saw. It cuts logs into planks and lumber without a hand saw.']),
        ], Q('bronze/kin/key', 'First Mill',
             [t_observe('create:millstone', title='Look at a placed millstone'),
              t_observe('create:water_wheel', title='Look at a placed water wheel')],
             kind='keystone', icon='create:millstone',
             desc=['Power a millstone from a water wheel. It grinds what the quern grinds by hand.'])),
    ],
    goal=Q('bronze/goal', 'Offer the Sky Disc at the Shrine', t_stage('age_2', 'Offer the Sky Disc at the shrine'),
           kind='goal', icon='firmages:sky_disc', sub='Goal of the Bronze Age: Caelum opens the Iron Age',
           desc=['Craft the Sky Disc from a bronze double sheet and gold sheets.',
                 '',
                 'Raise the Bronze Sanctum: rock bricks, four bronze blocks and a bronze bell. The heart shows what',
                 'is missing. Lay the Disc on the new plinth, ring the bell and pray.',
                 '',
                 'Caelum keeps the Disc and opens the Iron Age.']),
    sides=[
        ('bronze/smith/weld', [
            Q('bronze/side/backpack', 'Pack Mule', t_item('sophisticatedbackpacks:backpack'), kind='side',
              desc=['A backpack carries a second inventory.']),
        ]),
        ('bronze/alloy/crucible', [
            Q('bronze/side/kitchen', 'Kitchen', t_item('firmalife:drying_mat'), kind='side',
              desc=['Build a drying mat, the first piece of a real kitchen. Ovens, cheese and dried food follow.']),
        ]),
        ('bronze/prosp/tin', [
            Q('bronze/side/totem', 'A Flute', t_item('totemic:flute'), kind='side',
              desc=['Carve a flute. Its music starts the ceremonies at a totem pole.']),
        ]),
    ],
    explains=[
        Q('bronze/info/heat', 'Working Heat', t_check(), kind='explain', icon='tfc:metal/double_ingot/copper',
          desc=['Metal can be worked or welded only while it is hot enough. Hover over it to read its heat.']),
        # TFC 4.2.11 field guide metalworking/anvils: plan from the scroll, green and red actions move the progress
        # marker to the target, the rules (two or three icons) bind the last three actions.
        Q('bronze/info/anvil', 'Anvil Work', t_check(), kind='explain', icon='tfc:metal/anvil/copper',
          desc=['Put hot metal in the anvil and hold a hammer. Pick the plan from the scroll.',
                'Strike until the green marker meets the red one; your last three strikes must match the rules',
                'shown on top.']),
        Q('bronze/info/create', 'Kinetics in the Bronze Age', t_check(), kind='explain', icon='create:andesite_alloy',
          desc=['Only andesite machines exist now: water wheels, saws, presses, fans and mills.',
                'Crushing wheels, deployers and brass come with the Iron Age.',
                '',
                'Hover over a machine in your inventory and hold W to watch it work.']),
        Q('bronze/info/home', 'A Home', t_check(), kind='explain', icon='handcrafted:oak_chair',
          desc=['Furniture opens: chairs, tables, shelves, doors and windows in every wood, vanilla wood and',
                "every tree's own wood alike. The recipe viewer shows how each is made."]),
    ],
    supplies='bronze_supplies', comfort='bronze_comfort',
    ring=ring_quest('bronze', 1, 'the Bronze Sanctum', 'tfc:bronze_bell',
                    ['Raise the Bronze Sanctum around the Hearth Circle: rock bricks, a bronze block on each corner',
                     'and a bronze or brass bell opposite the plinth. The heart shows every missing block.']))

# ---------------------------------------------------------------- Iron Age
IRON = age_chapter(
    'iron', 'iron_age', 'Iron Age', ['The map opens. Iron veins become visible.'], 'firmages:steel_heart',
    'age_2', 3,
    entry=Q('iron/entry', 'The Iron Age Dawns', t_stage('age_2'), kind='entry',
            icon='tfc:metal/ingot/wrought_iron', sub='The third Age',
            desc=['Iron veins are visible now and the map opens.',
                  '',
                  'Five strands lead to the Steel Heart: Metallurgy, Power & Motion, Survival, Frontier and Sky. The',
                  'Sky strand readies the Lens Crown of the next Age.']),
    strands=[
        ('Metallurgy', [
            Q('iron/metal/bloomery', 'Ironworks', t_item('tfc:bloomery'),
              desc=['Build a bloomery from bronze double sheets and stone bricks.']),
            Q('iron/metal/bloom', 'In Bloom', t_item('tfc:raw_iron_bloom'),
              desc=['Fill the bloomery with iron ore and charcoal and let it burn.']),
            Q('iron/metal/wrought', 'Wrought Iron', t_item('tfc:metal/ingot/wrought_iron', 4),
              desc=['Hammer the bloom on an anvil until it becomes wrought iron.']),
            Q('iron/metal/anvil', 'Iron Anvil', t_item('tfc:metal/anvil/wrought_iron'),
              desc=['A wrought iron anvil can work steel.']),
            Q('iron/metal/blast', 'Blast Furnace', t_item('tfc:blast_furnace'),
              desc=['The blast furnace makes pig iron from iron ore, charcoal and flux.']),
            Q('iron/metal/pig', 'Pig Iron', t_item('tfc:metal/ingot/pig_iron', 2),
              desc=['Cast the pig iron. Hammer it into high carbon steel, then into steel.']),
        ], Q('iron/metal/key', 'Steel Ingots', t_item('tfc:metal/ingot/steel', 16), kind='keystone',
             desc=['Sixteen steel ingots. From here on, steel is a material, not a trophy.'])),
        ('Power & Motion', [
            Q('iron/power/brass', 'Brass', t_item('tfc:metal/ingot/brass', 4),
              desc=['Alloy copper and zinc into brass, by hand in the crucible or in a heated basin.']),
            Q('iron/power/precision', 'Precision Mechanism', t_item('create:precision_mechanism'),
              desc=['Sequenced assembly on a brass base. Many Iron Age machines need it.']),
            Q('iron/power/crush', 'Crushing Wheels', t_item('create:crushing_wheel', 2),
              desc=['Crushing wheels turn ore pieces into dust for more metal per ore.']),
            Q('iron/power/heater', 'Fuel Heater', t_item('tfcreate:primitive_heater'),
              desc=['A fuel heater heats a basin for heated mixing and compacting.']),
            Q('iron/power/steam', 'Steam Engine', t_item('create:steam_engine'),
              desc=['Steam engines on a boiler give far more force than water wheels.']),
        ], Q('iron/power/key', 'Automated Bloomery Line', t_item('tfc:metal/ingot/wrought_iron', 32),
             kind='keystone', icon='tfc:raw_iron_bloom',
             desc=['Feed the bloomery by machine and collect 32 wrought iron ingots from the line.'])),
        ('Survival', [
            Q('iron/surv/barrel', 'Barrel', t_adv('tfc:story/barrel'),
              desc=['Build a barrel with a saw and lumber. Barrels brine, pickle and tan.'],
              icon='tfc:wood/barrel/oak'),
            Q('iron/surv/leather', 'Genuine Leather', t_adv('tfc:story/leather'),
              desc=['Soak a hide in limewater, scrape it, then soak it in tannin.'], icon='minecraft:leather'),
            Q('iron/surv/clothes', 'Leather Armor',
              t_any_item(['minecraft:leather_helmet', 'minecraft:leather_chestplate', 'minecraft:leather_leggings',
                          'minecraft:leather_boots'], 'Any leather armor piece'),
              icon='minecraft:leather_chestplate',
              desc=['Your own tanned leather makes the first armor. Wear it before the first winter trip.']),
            Q('iron/surv/vessels', 'Cellar Vessels', t_item('tfc:ceramic/large_vessel', 4),
              desc=['Dig a cellar below ground: it stays cool. Fill large vessels and seal them.']),
        ], Q('iron/surv/key', 'Winter Stores', t_check('Our cellar is stocked for the winter'), kind='keystone',
             icon='tfc:ceramic/large_vessel',
             desc=['Tick this when your cellar holds sealed vessels with enough food for all of you.',
                   'No food goes into any recipe or turn-in.'])),
        ('Frontier', [
            Q('iron/front/compass', 'Frontier Compass', t_item('firmages:frontier_compass'),
              desc=['Set magnetite in wrought iron and right-click with the compass. It names the way to the nearest',
                    'cold forge.']),
            Q('iron/front/forge', 'The Cold Forge', t_item('firmages:frontier_shard', consume=False),
              desc=['An older smith worked these hills. Find the cold forge and bring back what its chest kept.']),
        ], Q('iron/front/key', 'Frontier Relic', t_item('firmages:frontier_relic', consume=False), kind='keystone',
             desc=['Finish the shard on the deployer and the press. The Steel Heart needs the relic.'])),
        ('Sky', [
            Q('iron/sky/star', 'A Falling Star', t_item('firmages:meteoric_iron_chunk'), icon='firmages:meteorite',
              desc=['Break a meteorite in a crater. On some clear nights a star falls a few thousand blocks away;',
                    'old cold craters lie in new land.']),
            Q('iron/sky/ingot', 'Meteoric Iron', t_item('firmages:meteoric_iron_ingot', 2),
              desc=['Heat the chunks and work them on the anvil. Meteoric iron falls only from the sky.']),
            Q('iron/sky/rod', 'Storm Rod', t_item('firmages:storm_rod'),
              desc=['Build a Storm Rod with a meteoric iron tip and fill it with sand. In any rain, lightning strikes',
                    'it and fuses the sand.']),
        ], Q('iron/sky/key', 'Storm Glass', t_item('firmages:storm_glass', 8), kind='keystone',
             desc=['Gather eight storm glass from your Storm Rods; they are its only source. With meteoric iron it',
                   'becomes Starlit Iron under the Lens Crown, the first celestial alloy.'])),
    ],
    goal=Q('iron/goal', 'Offer the Steel Heart at the Shrine',
           t_stage('age_3', 'Offer the Steel Heart at the shrine'), kind='goal', icon='firmages:steel_heart',
           sub='Goal of the Iron Age: Caelum opens the Arcane Age',
           desc=['Craft the Steel Heart from the Frontier Relic, a precision mechanism, a steel sheet and wrought iron',
                 'double sheets.',
                 '',
                 'Raise the Iron Sanctum: four smooth rock pillars with wrought iron bars and four wrought iron lamps.',
                 'Lay the Heart on the new plinth, light the lamps and pray.',
                 '',
                 'Caelum keeps the Heart and opens the Arcane Age.']),
    sides=[
        ('iron/power/brass', [
            Q('iron/side/rails', 'Trains', t_item('create:track', 16), kind='side',
              desc=['Lay sixteen tracks. Trains bring ore home from distant mines.']),
        ]),
        ('iron/surv/barrel', [
            Q('iron/side/storage', 'Storage Terminal', t_item('toms_storage:storage_terminal'), kind='side',
              desc=['Build a storage terminal and connect your chests. Every chest shows in one screen.']),
        ]),
    ],
    explains=[
        Q('iron/info/winter', 'The First Winter', t_check(), kind='explain', icon='tfc:ceramic/large_vessel',
          desc=['The year has seasons. Crops stop growing in winter and it gets cold. Plan ahead.']),
        Q('iron/info/home', 'A Home', t_check(), kind='explain', icon='minecraft:white_bed',
          desc=['Real beds open: wool beds and fancy beds with cushions and sheets. Sleeping in one skips the night.']),
    ],
    supplies='iron_supplies', comfort='iron_comfort',
    ring=ring_quest('iron', 2, 'the Iron Sanctum', 'tfc:metal/lamp/wrought_iron',
                    ['Raise the Iron Sanctum around the Bronze Sanctum: smooth rock pillars with wrought iron bars and',
                     'four wrought iron lamps. The heart shows every missing block.']))

# ---------------------------------------------------------------- Arcane Age
ARCANE = age_chapter(
    'arcane', 'arcane_age', 'Arcane Age', ['Spirits, source and alchemy. The Nether opens.'],
    'firmages:arcane_keystone', 'age_3', 4,
    entry=Q('arcane/entry', 'The Arcane Age Dawns', t_stage('age_3'), kind='entry', icon='ars_nouveau:source_gem',
            sub='The fourth Age',
            desc=['Magic wakes: spirits, source and alchemy. It gives the best ore yield until the Information Age.',
                  '',
                  'The Arcane Keystone takes one of two routes. The Spirit Route binds it from the Spirits, Source and',
                  'Alchemy strands. The Machine Route fills a Hollow Keystone on your machine line. Both need Starlit',
                  'Iron from the Lens Crown: the Sky strand.']),
    strands=[
        ('Spirits', [
            Q('arcane/spirit/datura', 'Datura', t_item('occultism:datura_seeds'),
              desc=['Datura seeds now drop from grass. Plant them on Arcane Soil, dirt charged with amethyst;',
                    'plain farmland will not hold them.']),
            Q('arcane/spirit/dream', 'Demon\'s Dream', t_item('occultism:demons_dream_essence'),
              desc=['Datura fruit becomes Demon\'s Dream essence. It opens your third eye to the spirit world.']),
            Q('arcane/spirit/dictionary', 'Dictionary of Spirits', t_item('occultism:dictionary_of_spirits'),
              desc=['The Dictionary of Spirits explains every pentacle and ritual. Keep it on hand.']),
            Q('arcane/spirit/chalk', 'White Chalk', t_item('occultism:chalk_white'),
              desc=['Chalk draws pentacles. White chalk is enough for Foliot rituals.']),
            Q('arcane/spirit/nether', 'The Way Below', t_dim('minecraft:the_nether'), icon='beneath:ancient_altar',
              # Beneath 2.0.4 PortalUtil (javap): a goat, pig or sheep killed with a TFC scythe, at least 7 offerings
              # within 5 blocks (y -2 to +3): skulls, anvils, gold or black steel blocks, ingot piles of gold, pig iron
              # or black steel. Blindness and nausea, an explosion at the player, a lightning bolt, a portal.
              desc=['No portal leads to the Nether in this land. It opens to a sacrifice:',
                    '- Set at least seven offerings within five blocks of one spot: anvils, mob heads, gold or black',
                    'steel blocks, or piles of gold, pig iron or black steel ingots.',
                    '- Kill a goat, pig or sheep there with a scythe.',
                    '',
                    'The ground bursts and a portal opens where you stand. Step through and look for iesnium.']),
            Q('arcane/spirit/iesnium', 'Iesnium', t_item('occultism:iesnium_ingot', 4),
              desc=['Iesnium ore lies in the Nether. Smelt it into ingots; the Djinni rituals need it.']),
            Q('arcane/spirit/djinni', 'Bind a Djinni', t_item('occultism:book_of_binding_bound_djinni'),
              desc=['Bind a Djinni into a book of binding. A Djinni runs crafting rituals and the Mineshaft.']),
        ], Q('arcane/spirit/key', 'Spirit Proof',
             [t_item('occultism:spirit_attuned_crystal', title='Spirit attuned crystal'),
              t_item('occultism:iesnium_ingot', 2, title='Iesnium ingots')],
             kind='keystone', icon='occultism:spirit_attuned_crystal',
             desc=['A spirit attuned crystal and iesnium: the proof of the Spirits strand.',
                   'Next, Dimensional Storage and the Mineshaft with a Djinni miner, which gives rich ore pieces',
                   'of every metal your Age allows.'])),
        ('Source', [
            Q('arcane/source/book', 'Novice Spellbook', t_item('ars_nouveau:novice_spell_book'),
              desc=['The novice spellbook casts the first spells and opens the worn notebook.']),
            Q('arcane/source/link', 'Sourcelink',
              t_any_item(['ars_nouveau:volcanic_sourcelink', 'ars_nouveau:agronomic_sourcelink',
                          'ars_nouveau:mycelial_sourcelink', 'ars_nouveau:vitalic_sourcelink',
                          'ars_nouveau:alchemical_sourcelink'], 'Any sourcelink'),
              icon='ars_nouveau:volcanic_sourcelink',
              desc=['Sourcelinks turn fuel, growth or life around them into source. Store it in source jars.']),
            Q('arcane/source/chamber', 'Imbuement Chamber', t_item('ars_nouveau:imbuement_chamber'),
              desc=['The imbuement chamber charges items with source.']),
            Q('arcane/source/gems', 'Source Gems', t_item('ars_nouveau:source_gem', 16),
              desc=['Imbue amethyst or lapis lazuli. Source gems are the base of all source magic.']),
            Q('arcane/source/apparatus', 'Enchanting Apparatus', t_item('ars_nouveau:enchanting_apparatus'),
              desc=['The apparatus on an arcane core with pedestals crafts the source items. It is the one magic',
                    'pedestal crafting of this Age.']),
            Q('arcane/source/starbuncle', 'Starbuncle', t_item('ars_nouveau:starbuncle_charm'),
              desc=['A Starbuncle carries items between chests: the item transport of the Arcane Age.']),
            Q('arcane/source/apprentice', 'Apprentice Spellbook', t_item('ars_nouveau:apprentice_spell_book'),
              desc=['The apprentice spellbook holds the stronger glyphs you need against the Wilden Chimera.']),
            Q('arcane/source/chimera', 'Wilden Chimera', t_boss('ars_nouveau:wilden_boss', 'ars_nouveau:wilden_tribute'),
              icon='ars_nouveau:ritual_wilden_summon',
              desc=['Bind horn, wing and spike of the Wilden with source in the enchanting apparatus. The Wilden',
                    'Chimera drops a tribute too.']),
        ], Q('arcane/source/key', 'Wilden Tribute', t_item('ars_nouveau:wilden_tribute', consume=False),
             kind='keystone',
             desc=['Keep a Wilden Tribute. The Djinni ritual of the Arcane Keystone needs it.'])),
        ('Alchemy', [
            Q('arcane/alch/brazier', 'Pyromantic Brazier', t_item('theurgy:pyromantic_brazier'),
              desc=['Build a pyromantic brazier. It heats every alchemical apparatus; read the Hermetica as you go.']),
            Q('arcane/alch/calcination', 'Calcination Oven', t_item('theurgy:calcination_oven'),
              desc=['Calcination turns minerals into alchemical salt.']),
            Q('arcane/alch/sal', 'Sal Ammoniac', t_item('theurgy:sal_ammoniac_accumulator'),
              desc=['The accumulator gathers sal ammoniac, the solvent of every liquefaction.']),
            Q('arcane/alch/liquefaction', 'Liquefaction Cauldron', t_item('theurgy:liquefaction_cauldron'),
              desc=['Build the liquefaction cauldron and a digestion vat. Ore pieces of every grade digest into',
                    'alchemical sulfur, where spagyrics begin.']),
            Q('arcane/alch/distiller', 'Distiller', t_item('theurgy:distiller'),
              desc=['The distiller makes mercury shards, the third principle.']),
            Q('arcane/alch/incubator', 'Incubator', t_item('theurgy:incubator'),
              desc=['Mercury, salt and sulfur incubate into a metal dust. Melt the dust in a vessel.']),
            Q('arcane/alch/rod', 'Divination Rod', t_item('theurgy:divination_rod_t1'),
              desc=['A divination rod points to the ore veins your Age has woken.']),
        ], Q('arcane/alch/key', 'Mercury Catalyst', t_item('theurgy:mercury_catalyst'), kind='keystone',
             desc=['Make the mercury catalyst, the proof of the Alchemy strand. The Arcane Keystone needs it.'])),
        ('Sky', [
            Q('arcane/sky/crown', 'The Lens Crown',
              t_observe('firmages:star_anvil[formed=true]', kind='block_state', title='Look at a formed Star Anvil'),
              icon='firmages:star_anvil',
              desc=['Set a Star Anvil in a 9 by 9 stone ring with three lens mounts under open sky. The Lens Crown',
                    'forges the celestial alloys.']),
        ], Q('arcane/sky/key', 'Starlit Iron', t_item('firmages:starlit_iron_ingot', 3), kind='keystone',
             desc=['Forge Starlit Iron under the crown from meteoric iron, storm glass and an Arcane gem. Both routes to',
                   'the Arcane Keystone need it, and the Wayshrines now want it as their offering.'])),
        ('Machine Line', [
            Q('arcane/machine/blaze', 'Blaze Rods', t_item('minecraft:blaze_rod', 3),
              desc=['Blazes live in the Nether fortresses. A deployer sets one rod per pass.']),
        ], Q('arcane/machine/key', 'Hollow Keystone', t_item('firmages:hollow_keystone', consume=False), kind='keystone',
             desc=['Find a Hollow Keystone in a chest of a Nether fortress. A spout, two deployers and a press fill it in',
                   'three passes: liquid experience, a blaze rod, Starlit Iron, the press.'])),
    ],
    routes=[
        Q('arcane/route/spirit', 'The Spirit Route', t_item('firmages:arcane_keystone', consume=False),
          deps=['arcane/spirit/key', 'arcane/source/key', 'arcane/alch/key', 'arcane/sky/key'],
          icon='occultism:book_of_binding_bound_djinni',
          desc=['Bind the Arcane Keystone in the Djinni\'s crafting pentacle from the proofs of the magic strands.']),
        Q('arcane/route/machine', 'The Machine Route', t_item('firmages:arcane_keystone', consume=False),
          deps=['arcane/machine/key', 'arcane/sky/key'], icon='create:mechanical_press',
          desc=['Fill the Hollow Keystone on your machine line. No spirit, source or alchemy is needed.']),
    ],
    goal=Q('arcane/goal', 'Offer the Arcane Keystone at the Shrine',
           t_stage('age_4', 'Offer the Arcane Keystone at the shrine'), kind='goal', icon='firmages:arcane_keystone',
           sub='Goal of the Arcane Age: Caelum opens the Industrial Age',
           desc=['Bind the Arcane Keystone in the Djinni\'s crafting pentacle (a spirit attuned crystal, an iesnium',
                 'ingot, the Wilden Tribute, the mercury catalyst, Starlit Iron and a source gem block), or fill a',
                 'Hollow Keystone on the machine line.',
                 '',
                 'Raise the Spirit Circle; the heart shows what is missing. Lay the Keystone on the new plinth,',
                 'light the eight candles and pray.',
                 '',
                 'Caelum keeps the Keystone and opens the Industrial Age.']),
    sides=[
        ('arcane/spirit/nether', [
            Q('arcane/side/blaze', 'Blaze Burner', t_item('create:blaze_burner'), kind='side',
              desc=['Catch a blaze in the Nether with an empty blaze burner. Superheated mixing follows.']),
            Q('arcane/side/enchant', 'Enchantment Industry', t_item('create_enchantment_industry:blaze_enchanter'),
              kind='side', desc=['A blaze enchanter enchants with liquid experience. No more enchanting table luck.']),
        ]),
        ('arcane/source/starbuncle', [
            Q('arcane/side/creo', 'Starbuncle Wheel', t_item('ars_creo:starbuncle_wheel'), kind='side',
              desc=['Set a Starbuncle in a wheel. It turns shafts without water or wind.']),
        ]),
    ],
    explains=[
        Q('arcane/info/ore', 'Ore in the Arcane Age', t_check(), kind='explain', icon='tfc:ore/rich_hematite',
          desc=['Three ways now give more metal per ore:',
                '- Crushing wheels: about 1.7 times.',
                '- Spagyrics: twice the metal, every grade.',
                '- The Dimensional Mineshaft: rich pieces without end.',
                '',
                'The spirit crusher grinds no ore.']),
        Q('arcane/info/one', 'One Carrier per Function', t_check(), kind='explain', icon='ars_nouveau:starbuncle_charm',
          desc=['Each magic function has one carrier: Starbuncles move items, Dimensional Storage stores them,',
                'spagyrics multiply ore, the Djinni crafts in pentacles. Duplicates are switched off.']),
    ],
    supplies='arcane_supplies', comfort='arcane_comfort',
    ring=ring_quest('arcane', 3, 'the Spirit Circle', 'ars_nouveau:sourcestone',
                    ['Raise the Spirit Circle around the Iron Sanctum: sourcestone, otherstone pillars capped with',
                     'source gem blocks, and eight candles. The heart shows every missing block. When the ring',
                     'stands, the heart waits for the Keystone.']))

# ---------------------------------------------------------------- Industrial Age
INDUSTRIAL = age_chapter(
    'industrial', 'industrial_age', 'Industrial Age', ['Coke, black steel and the first FE.'],
    'firmages:pressure_core', 'age_4', 5,
    entry=Q('industrial/entry', 'The Industrial Age Dawns', t_stage('age_4'), kind='entry',
            icon='immersiveengineering:hammer', sub='The fifth Age',
            desc=['Coke ovens, blast furnaces, crushers, presses and the first FE.',
                  '',
                  'Five strands lead to the Pressure Core: Coke & Steel, Power, Ore Line, Logistics & Backfill and Sky.',
                  '',
                  proof_line('age_4')]),
    strands=[
        ('Coke & Steel', [
            Q('industrial/steel/wood', 'Treated Wood', t_item('immersiveengineering:treated_wood_horizontal', 16),
              desc=['Soak planks in creosote. Treated wood is the frame of every engineering machine.']),
            Q('industrial/steel/coke', 'Coke Oven', t_observe('immersiveengineering:coke_oven',
                                                               title='Look at a formed coke oven'),
              icon='immersiveengineering:cokebrick',
              desc=['Build the 3x3x3 coke oven from coke bricks and form it with the Engineer\'s Hammer.',
                    'It cokes bituminous coal and lignite and gives creosote.']),
            Q('industrial/steel/fuel', 'Coal Coke', t_item('immersiveengineering:coal_coke', 32),
              desc=['Coke burns hot and clean. The blast furnace needs it.']),
            Q('industrial/steel/blast', 'Engineer\'s Blast Furnace', t_observe('immersiveengineering:blast_furnace',
                                                                    title='Look at a formed blast furnace'),
              icon='immersiveengineering:blastbrick',
              desc=['Build the blast furnace. Coke and wrought iron go in, steel comes out, faster than any anvil.']),
            Q('industrial/steel/gearbox', 'Arcane Gearbox', t_item('firmages:arcane_gearbox'),
              desc=['A gearbox with steel and precision mechanisms from the mechanical crafter, or with source gems',
                    'by magic. Reinforced blast bricks and Pressure Cores need it.']),
            Q('industrial/steel/improved', 'Improved Blast Furnace',
              t_observe('immersiveengineering:advanced_blast_furnace', title='Look at a formed improved blast furnace'),
              icon='immersiveengineering:blastbrick_reinforced',
              desc=['Reinforced blast bricks and preheaters make steel faster.']),
            Q('industrial/steel/black', 'Black Steel', t_item('tfc:metal/ingot/black_steel', 8),
              desc=['Work weak steel with nickel and black bronze on the anvil into black steel, the metal of the',
                    'Industrial Age.']),
            Q('industrial/steel/anvil', 'Black Steel Anvil', t_item('tfc:metal/anvil/black_steel'),
              desc=['A black steel anvil works black steel double sheets.']),
            Q('industrial/steel/chamber', 'Built-Up Chamber',
              t_item('createbigcannons:built_up_steel_cannon_chamber', consume=False),
              desc=['Cast steel layers, bore them, build them up and heat them into one chamber. It goes into the',
                    'Pressure Core.']),
        ], Q('industrial/steel/key', 'Black Steel Double Sheets', t_item('tfc:metal/double_sheet/black_steel', 2),
             kind='keystone', desc=['Two black steel double sheets for the Pressure Core.'])),
        ('Power', [
            Q('industrial/power/alternator', 'Alternator', t_item('createaddition:alternator'),
              desc=['The alternator turns rotation into FE, the only bridge from SU to FE. Drive it with a steam engine',
                    'on a fired boiler.']),
            Q('industrial/power/coil', 'Copper Wire', t_item('immersiveengineering:wirecoil_copper', 8),
              desc=['LV wire carries FE between connectors.']),
            Q('industrial/power/connector', 'LV Connectors', t_item('immersiveengineering:connector_lv', 4),
              desc=['Connectors hang the wire on machines and posts.']),
            Q('industrial/power/lead', 'Lead', t_item('immersiveengineering:ingot_lead', 4),
              desc=['Galena veins are visible now. Lead goes into capacitors.']),
            Q('industrial/power/capacitor', 'LV Capacitor', t_item('immersiveengineering:capacitor_lv'),
              desc=['The LV capacitor stores FE and evens out the grid.']),
        ], Q('industrial/power/key', 'Grid Online (LV)',
             [t_observe('createaddition:alternator', title='Look at your placed alternator'),
              t_observe('immersiveengineering:capacitor_lv', title='Look at your placed LV capacitor')],
             kind='keystone', icon='immersiveengineering:capacitor_lv',
             desc=['Wire an alternator through LV connectors to a capacitor and run your machines from it.'])),
        ('Ore Line', [
            Q('industrial/ore/crusher', 'Crusher', t_observe('immersiveengineering:crusher',
                                                             title='Look at a formed crusher'),
              icon='immersiveengineering:heavy_engineering',
              desc=['Build a crusher and feed it ore pieces. It returns twice the metal as powder and dust, plus a',
                    'by-product.']),
            Q('industrial/ore/press', 'Metal Press', t_observe('immersiveengineering:metal_press',
                                                               title='Look at a formed metal press'),
              icon='immersiveengineering:mold_plate',
              desc=['The metal press makes sheets from two ingots, rods and wire.']),
            Q('industrial/ore/kiln', 'Alloy Kiln', t_observe('immersiveengineering:alloy_smelter',
                                                             title='Look at a formed alloy kiln'),
              icon='immersiveengineering:alloybrick',
              desc=['The alloy kiln alloys two metals in the right ratios.']),
            Q('industrial/ore/constantan', 'Constantan', t_item('immersiveengineering:ingot_constantan', 8),
              desc=['Copper and nickel in the alloy kiln.']),
        ], Q('industrial/ore/key', 'Automated Ore Line', t_item('mekanism:dust_iron', 64), kind='keystone',
             desc=['Feed the crusher by machine and collect 64 iron dust from the line.'])),
        ('Logistics & Backfill', [
            Q('industrial/logi/packager', 'Packager', t_item('create:packager'),
              desc=['The packager boxes items for the package network.']),
            Q('industrial/logi/frogport', 'Frogport', t_item('create:package_frogport'),
              desc=['Frogports send packages along chain conveyors.']),
            Q('industrial/logi/ticker', 'Stock Ticker', t_item('create:stock_ticker'),
              desc=['A stock link and a stock ticker show and request your stock from anywhere on the network.']),
            Q('industrial/logi/gauge', 'Factory Gauge', t_item('create:factory_gauge'),
              desc=['Factory gauges keep a production line stocked by themselves.']),
            Q('industrial/logi/atfc', 'The Thresher',
              t_observe('advancedtfctech:thresher', title='Look at a formed thresher'),
              icon='advancedtfctech:pirn',
              desc=['Build a thresher from steel sheet metal, light engineering blocks, a redstone engineering block',
                    'and a hopper, and form it with the Engineer\'s Hammer like the crusher. It ends threshing by hand.',
                    '',
                    'The grist mill, beamhouse, power loom and fleshing machine follow and end the rest of the hand',
                    'work.']),
        ], Q('industrial/logi/key', 'Backfill Line', t_item('tfc:metal/sheet/steel', 64), kind='keystone',
             icon='create:package_frogport',
             desc=['Bring 64 steel sheets from your press line through the package network.'])),
        ('Sky', [
            Q('industrial/sky/isles', 'Above the Clouds', t_item('firmages:firmament_crystal', 4),
              icon='firmages:firmament_crystal',
              desc=['Fly to the Drifting Isles at height 228 to 272 by airship or jetpack. Firmament crystal grows only',
                    'there.']),
            Q('industrial/sky/net', 'Aurora Net', t_item('firmages:aurora_net'),
              desc=['Set an Aurora Net at height 140 or more under open sky in the cold north. Every clear night it',
                    'catches aurora light.']),
        ], Q('industrial/sky/key', 'Cloudsteel', t_item('firmages:cloudsteel_ingot', 2), kind='keystone',
             desc=['Add the Arcane lens to the crown and forge Cloudsteel from meteoric iron, black steel and',
                   'firmament crystal. Two ingots go into the Pressure Core.'])),
    ],
    goal=Q('industrial/goal', 'Offer the Pressure Core at the Shrine',
           t_stage('age_5', 'Offer the Pressure Core at the shrine'), kind='goal', icon='firmages:pressure_core',
           sub='Goal of the Industrial Age: Caelum opens the Electric Age',
           desc=['Craft the Pressure Core from two black steel double sheets, a heavy engineering block, two',
                 'Cloudsteel ingots, the Arcane Gearbox and a built-up steel chamber.',
                 '',
                 'Raise the Foundry Nave; the heart shows what is missing. Once your Tally Chutes have taken %s in'
                 % tally('age_4'),
                 'this Age, lay the Core on the new plinth, power the four electric lanterns and pray.',
                 '',
                 'Caelum keeps the Core and opens the Electric Age.']),
    sides=[
        ('industrial/steel/wood', [
            Q('industrial/side/airship', 'Airship', t_item('aeronautics:andesite_propeller'), kind='side',
              desc=['Craft a propeller, the first part of a ship that flies.']),
        ]),
        ('industrial/entry', [
            Q('industrial/side/chute', 'Tally Chute', t_item('firmages:tally_chute'), kind='side',
              desc=['Craft a Tally Chute. It takes only the metal Caelum asks as proof of work, counts it and keeps it.']),
            proof_quest('industrial', 'age_4'),
        ]),
        ('industrial/power/coil', [
            Q('industrial/side/jetpack', 'Air Jetpack', t_item('create_jetpack:jetpack'), kind='side',
              desc=['Craft a jetpack. It flies on compressed air from its tank.']),
        ]),
        ('industrial/ore/crusher', [
            Q('industrial/side/spawner', 'Mechanical Spawner', t_item('create_mechanical_spawner:mechanical_spawner'),
              kind='side', desc=['Spawn fluid turns into mobs, only those your Age allows.']),
        ]),
    ],
    explains=[
        Q('industrial/info/fe', 'FE and SU', t_check(), kind='explain', icon='createaddition:alternator',
          desc=['FE is the one unit of energy. The alternator turns SU into FE only from steam engines on a fired',
                'boiler or diesel engines; wind and water give none. The electric motor turns FE back into SU.']),
        Q('industrial/info/multiblock', 'Multiblocks', t_check(), kind='explain', icon='immersiveengineering:manual',
          desc=['The engineering machines are multiblocks: build the shape from the Engineer\'s Manual, then hit the marked',
                'block with the Engineer\'s Hammer. Projector blueprints show the shape in the world.']),
        Q('industrial/info/claims', 'Chunk Claims', t_check(), kind='explain', icon='minecraft:map',
          desc=['Your fellowship can now claim land and keep chunks loaded. Keep the chunks your lines run in',
                'loaded, or they stop when you leave.']),
    ],
    supplies='industrial_supplies', comfort='industrial_comfort',
    ring=ring_quest('industrial', 4, 'the Foundry Nave', 'immersiveengineering:cokebrick',
                    ['Raise the Foundry Nave around the Spirit Circle: coke bricks, heavy engineering block bases,',
                     'steel scaffolding and four electric lanterns. The heart shows every missing block.']))

# ---------------------------------------------------------------- Electric Age
ELECTRIC = age_chapter(
    'electric', 'electric_age', 'Electric Age', ['High voltage, oil and aluminium.'],
    'firmages:humming_core', 'age_5', 6,
    entry=Q('electric/entry', 'The Electric Age Dawns', t_stage('age_5'), kind='entry',
            icon='immersiveengineering:capacitor_hv', sub='The sixth Age',
            desc=['MV and HV power, oil, the arc furnace and the excavator. Bauxite and osmium veins wake: the Information Age needs osmium at once.',
                  '',
                  'Five strands lead to the Humming Core: Power Grid, Oil, Aluminium & Excavator, Circuits and Sky.',
                  '',
                  proof_line('age_5')]),
    strands=[
        ('Power Grid', [
            Q('electric/grid/mv', 'MV Wire', t_item('immersiveengineering:wirecoil_electrum', 8),
              desc=['Electrum wire carries medium voltage.']),
            Q('electric/grid/hv', 'HV Wire', t_item('immersiveengineering:wirecoil_steel', 8),
              desc=['Steel wire carries high voltage over long distances.']),
            Q('electric/grid/transformer', 'Transformers',
              [t_item('immersiveengineering:transformer', title='Transformer'),
               t_item('immersiveengineering:transformer_hv', title='HV transformer')],
              icon='immersiveengineering:transformer_hv', desc=['Transformers step the voltage between wire tiers.']),
            Q('electric/grid/diesel', 'Diesel Generator', t_observe('immersiveengineering:diesel_generator',
                                                                     title='Look at a formed diesel generator'),
              icon='immersiveengineering:diesel_generator',
              desc=['The diesel generator is the one FE generator of this Age. Feed it diesel from the Oil strand.']),
            Q('electric/grid/af', 'Alternating Flux', t_item('alternatingflux:wirecoil_af', 4),
              desc=['Alternating Flux wire carries the strongest current with little loss.']),
        ], Q('electric/grid/key', 'Grid Online', t_item('immersiveengineering:capacitor_hv', 2), kind='keystone',
             desc=['Two HV capacitors: one buffers your grid, one goes into the Humming Core.'])),
        ('Oil', [
            Q('electric/oil/sample', 'Core Sample', t_item('immersiveengineering:coresample'),
              desc=['A core sample drill shows what lies under a chunk, oil included.']),
            Q('electric/oil/pumpjack', 'Pumpjack', t_observe('immersivepetroleum:pumpjack',
                                                              title='Look at a formed pumpjack'),
              icon='immersivepetroleum:pumpjack', desc=['Build a pumpjack over an oil reservoir.']),
            Q('electric/oil/crude', 'Crude Oil', t_item('immersivepetroleum:crudeoil_bucket'),
              desc=['Pump crude oil into tanks.']),
            Q('electric/oil/tower', 'Distillation Tower', t_observe('immersivepetroleum:distillation_tower',
                                                                     title='Look at a formed distillation tower'),
              icon='immersivepetroleum:distillation_tower',
              desc=['The distillation tower splits crude oil into diesel, lubricant and more.']),
            Q('electric/oil/lube', 'Lubricant', t_item('immersivepetroleum:lubricant_bucket'),
              desc=['Fill a bucket with lubricant. Fed through an automatic lubricator, it speeds up your machines.']),
        ], Q('electric/oil/key', 'Refined Diesel', t_item('immersivepetroleum:diesel_bucket', 16), kind='keystone',
             desc=['Sixteen buckets of diesel from your own refinery.'])),
        ('Aluminium & Excavator', [
            Q('electric/alu/bauxite', 'Bauxite',
              t_any_item(['tfc_ie_addon:ore/small_bauxite', 'tfc_ie_addon:ore/poor_bauxite',
                          'tfc_ie_addon:ore/normal_bauxite', 'tfc_ie_addon:ore/rich_bauxite'], 'Any bauxite ore piece'),
              icon='tfc_ie_addon:ore/normal_bauxite', desc=['Mine bauxite. Its ore is the only source of aluminium.']),
            Q('electric/alu/arc', 'Arc Furnace', t_observe('immersiveengineering:arc_furnace',
                                                           title='Look at a formed arc furnace'),
              icon='immersiveengineering:graphite_electrode',
              desc=['The arc furnace melts, alloys and makes steel with graphite electrodes.']),
            Q('electric/alu/ingot', 'Aluminium', t_item('immersiveengineering:ingot_aluminum', 16),
              desc=['Aluminium comes only from the arc furnace.']),
            Q('electric/alu/red', 'Red Steel', t_item('tfc:metal/sheet/red_steel', 2),
              desc=['Work weak red steel on the anvil, then into a sheet. Red steel is the metal of the Electric Age.']),
            Q('electric/alu/excavator', 'Excavator', t_observe('immersiveengineering:excavator',
                                                               title='Look at a formed excavator'),
              icon='immersiveengineering:bucket_wheel',
              desc=['The excavator digs the mineral mix under a chunk. A core sample shows which one lies there.']),
        ], Q('electric/alu/key', 'Aluminium Plates', t_item('immersiveengineering:plate_aluminum', 32),
             kind='keystone', desc=['Press 32 aluminium plates. Four go into the Humming Core, the rest into the machines of this Age.'])),
        ('Circuits', [
            Q('electric/circ/tube', 'Vacuum Tubes', t_item('immersiveengineering:electron_tube', 8),
              desc=['Vacuum tubes are the first electronics.']),
            Q('electric/circ/board', 'Circuit Board', t_item('immersiveengineering:circuit_board', 4),
              desc=['Circuit boards carry the tubes.']),
            Q('electric/circ/assembler', 'Assembler', t_observe('immersiveengineering:assembler',
                                                                title='Look at a formed assembler'),
              icon='immersiveengineering:assembler',
              desc=['The assembler autocrafts grid recipes: the autocrafting of this Age.']),
            Q('electric/circ/wither', 'Nether Star', t_boss('minecraft:wither', 'minecraft:nether_star'),
              icon='minecraft:nether_star',
              desc=['Fuse a Nether Star in the arc furnace. The Humming Core needs it; the Wither drops one too.']),
        ], Q('electric/circ/key', 'Attuned Circuits', t_item('firmages:attuned_circuit', 16), kind='keystone',
             desc=['A circuit board tuned by quartz, with two advanced electronic components and electrum wire.',
                   'With magic, a spirit attuned crystal tunes it alone. Let the assembler make them.'])),
        ('Sky', [
            Q('electric/sky/starsteel', 'Starsteel', t_item('firmages:starsteel_ingot', 2),
              desc=['Starsteel meteorites fall now. Melt their chunks in the arc furnace; the sky is its only source.']),
            Q('electric/sky/light', 'Aurora Light', t_item('firmages:aurora_light_bucket'),
              desc=['Pipe or bucket the aurora light out of your Aurora Nets. Nothing else makes it.']),
        ], Q('electric/sky/key', 'Aurora Steel', t_item('firmages:aurora_steel_ingot', consume=False), kind='keystone',
             desc=['Add the Industrial lens and forge Aurora Steel from starsteel, red steel, storm glass and aurora',
                   'light. The Humming Core needs it.'])),
    ],
    goal=Q('electric/goal', 'Offer the Humming Core at the Shrine',
           t_stage('age_6', 'Offer the Humming Core at the shrine'), kind='goal', icon='firmages:humming_core',
           sub='Goal of the Electric Age: Caelum opens the Information Age',
           desc=['Fuse the Humming Core in the arc furnace: an HV capacitor, an Attuned Circuit, four aluminium plates,',
                 'an Aurora Steel ingot and a Nether Star.',
                 '',
                 'Raise the Tesla Crown. Once your Tally Chutes have taken %s in this Age, lay the Core on' % tally('age_5'),
                 'the plinth, give the floodlights power and a signal, charge the capacitors with 1,000,000 FE and',
                 'pray.',
                 '',
                 'Caelum opens the Information Age.']),
    sides=[
        ('electric/entry', [proof_quest('electric', 'age_5')]),
        ('electric/circ/tube', [
            Q('electric/side/afrit', 'Bind an Afrit', t_item('occultism:book_of_binding_bound_afrit'), kind='side',
              desc=['Afrits need an HV coil block for their book. They run the Dimensional Battlefield.']),
        ]),
        ('electric/oil/sample', [
            Q('electric/side/cloche', 'Garden Cloche', t_item('immersiveengineering:cloche'), kind='side',
              desc=['A garden cloche grows crops under glass with power and water.']),
        ]),
        ('electric/alu/bauxite', [
            Q('electric/side/hypertube', 'Hypertubes', t_item('create_hypertube:hypertube', 16), kind='side',
              desc=['Hypertubes shoot players across the base.']),
        ]),
    ],
    explains=[
        Q('electric/info/voltage', 'Voltage', t_check(), kind='explain', icon='immersiveengineering:connector_hv',
          desc=['LV, MV and HV wire each take their own connectors. Transformers join the tiers.',
                'Wire that carries too much power burns.']),
        Q('electric/info/retire', 'Stations Retire', t_check(), kind='explain', icon='immersiveengineering:alloybrick',
          desc=['The arc furnace takes over alloying from the alloy kiln. Old stations stay in the world,',
                'but their recipes move to the new one.']),
    ],
    supplies='electric_supplies', comfort='electric_comfort',
    ring=ring_quest('electric', 5, 'the Tesla Crown', 'immersiveengineering:floodlight',
                    ['Raise the Tesla Crown around the Foundry Nave: aluminium, red steel bases with HV capacitors,',
                     'aluminium scaffolding and four floodlights. The heart shows every missing block.']))

# ---------------------------------------------------------------- Information Age
INFORMATION = age_chapter(
    'information', 'information_age', 'Information Age', ['Ore tripled, one network, the End.'],
    'firmages:data_matrix', 'age_6', 7,
    entry=Q('information/entry', 'The Information Age Dawns', t_stage('age_6'), kind='entry',
            icon='mekanism:advanced_control_circuit', sub='The seventh Age',
            desc=['Ore triples, the ME network stores and crafts, and the End can be reached. Veins of certus quartz,',
                  'inferium, prosperity and soulstone wake.',
                  '',
                  'Five strands lead to the Data Matrix: Digital, Factory, Mob Data, The End and Sky.',
                  '',
                  proof_line('age_6')]),
    strands=[
        ('Digital', [
            Q('information/digital/certus', 'Certus Quartz', t_item('ae2:certus_quartz_crystal', 16),
              desc=['Certus quartz comes from its own vein.']),
            Q('information/digital/silicon', 'Silicon', t_item('ae2:silicon', 8),
              desc=['Silicon is printed into every processor.']),
            Q('information/digital/processors', 'Processors',
              [t_item('ae2:logic_processor', title='Logic processor'),
               t_item('ae2:calculation_processor', title='Calculation processor'),
               t_item('ae2:engineering_processor', title='Engineering processor')],
              icon='ae2:logic_processor',
              desc=['Print the three processors in an inscriber. Each press is crafted from four blue steel sheets around what it prints.']),
            Q('information/digital/controller', 'ME Controller', t_item('ae2:controller'),
              desc=['The controller powers an ME network.']),
            Q('information/digital/drive', 'ME Drive', [t_item('ae2:drive', title='ME drive'),
                                                         t_item('ae2:item_storage_cell_1k', title='1k storage cell')],
              icon='ae2:drive', desc=['Build an ME drive and a 1k storage cell. The drive holds the cells; the network',
                                      'reads them all.']),
            Q('information/digital/assembler', 'Molecular Assembler',
              [t_item('ae2:molecular_assembler', title='Molecular assembler'),
               t_item('ae2:pattern_provider', title='Pattern provider')],
              icon='ae2:molecular_assembler', desc=['Patterns and assemblers craft on request.']),
        ], Q('information/digital/key', 'Autocrafting Online',
             [t_item('ae2:crafting_unit', title='Crafting unit'),
              t_item('ae2:cell_component_64k', title='64k storage component')],
             kind='keystone', icon='ae2:crafting_unit',
             desc=['Build a crafting CPU and let the network craft a 64k storage component.'])),
        ('Factory', [
            Q('information/mek/osmium', 'Osmium', t_item('mekanism:ingot_osmium', 8),
              desc=['Smelt eight osmium ingots from the vein that woke in the Electric Age. The steel casing needs them.']),
            Q('information/mek/casing', 'Steel Casing', t_item('mekanism:steel_casing'),
              desc=['Make a steel casing. Every machine of the Factory strand is built on one.']),
            Q('information/mek/infuser', 'Metallurgic Infuser', t_item('mekanism:metallurgic_infuser'),
              desc=['The infuser makes the alloys and the control circuits.']),
            Q('information/mek/purify', 'Purification Chamber', t_item('mekanism:purification_chamber'),
              desc=['Purification gives three times the metal from every ore piece.']),
            Q('information/mek/energy', 'Energy Upgrades', t_item('mekanism:upgrade_energy', 8),
              desc=['Make eight energy upgrades for your ore machines. Without them the fourfold and fivefold ore lines',
                    'cost more power than they gain.']),
            Q('information/mek/separator', 'Electrolytic Separator', t_item('mekanism:electrolytic_separator'),
              desc=['Split water into hydrogen and oxygen. Hydrogen burns in the gas-burning generator.']),
            Q('information/mek/hdpe', 'HDPE', t_item('mekanism:hdpe_sheet', 8),
              desc=['The pressurized reaction chamber makes HDPE from ethene.']),
            Q('information/mek/gas', 'Gas-Burning Generator', t_item('mekanismgenerators:gas_burning_generator'),
              desc=['The FE generator of this Age.']),
            Q('information/mek/blue', 'Blue Steel', t_item('tfc:metal/sheet/blue_steel', 4),
              desc=['Make blue steel, the metal of the Information Age. Four sheets go into the Data Matrix, more into the inscriber presses.']),
        ], Q('information/mek/key', 'Advanced Factory', t_item('mekanism:advanced_smelting_factory'),
             kind='keystone', desc=['An advanced smelting factory smelts five stacks at once.'])),
        ('Mob Data', [
            Q('information/mob/learner', 'Deep Learner', t_item('hostilenetworks:deep_learner'),
              desc=['The deep learner trains data models while you fight.']),
            Q('information/mob/model', 'Data Model', t_item('hostilenetworks:blank_data_model'),
              desc=['A blank model learns one mob.']),
            Q('information/mob/sim', 'Simulation Chamber', t_item('hostilenetworks:sim_chamber'),
              desc=['The simulation chamber turns a trained model and FE into prediction matrices.']),
            Q('information/mob/matrix', 'Prediction Matrices', t_item('hostilenetworks:prediction_matrix', 16),
              desc=['Matrices are the raw output of the simulation.']),
            Q('information/mob/fabricator', 'Loot Fabricator', t_item('hostilenetworks:loot_fabricator'),
              desc=['The fabricator turns matrices into mob drops, boss drops included.']),
        ], Q('information/mob/key', 'Data Model: Tier 3', t_item('hostilenetworks:data_model',
                                                                 title='A data model trained to tier 3'),
             kind='keystone', icon='hostilenetworks:data_model',
             desc=['Train a data model to tier 3 (Superior) and keep it running in a simulation chamber.'])),
        ('The End', [
            Q('information/end/eyes', 'Your Own End Portal',
              [t_item('minecraft:end_portal_frame', 12, title='End portal frames'),
               t_item('minecraft:ender_eye', 12, title='Eyes of ender')],
              icon='minecraft:end_portal_frame',
              desc=['No stronghold stands in this land, so build your own End portal: twelve frames and twelve eyes',
                    'of ender.']),
            Q('information/end/enter', 'The End', t_dim('minecraft:the_end'), icon='minecraft:end_stone',
              desc=['Set the twelve frames as a ring around a 3 by 3 hole, facing inwards, fill them with the eyes',
                    'and step through.']),
            Q('information/end/dragon', 'The Ender Dragon', t_boss('minecraft:ender_dragon', 'minecraft:dragon_breath'),
              icon='minecraft:dragon_head', desc=['Destroy the crystals, then the dragon.']),
        ], Q('information/end/key', 'Dragon\'s Breath', t_item('minecraft:dragon_breath', 4), kind='keystone',
             desc=['Bottle four breaths of the dragon. Brewers and enchanters want them.'])),
        ('Sky', [], Q('information/sky/key', 'Halo Steel', t_item('firmages:halo_steel_ingot', 2), kind='keystone',
                      desc=['Add the Electric lens and forge Halo Steel from starsteel, blue steel, firmament crystal and',
                            'aurora light. Two ingots go into the Data Matrix.'])),
    ],
    goal=Q('information/goal', 'Offer the Data Matrix at the Shrine',
           t_stage('age_7', 'Offer the Data Matrix at the shrine'), kind='goal', icon='firmages:data_matrix',
           sub='Goal of the Information Age: Caelum opens the Space Age',
           desc=['Craft the Data Matrix from two blue steel sheets, two Halo Steel ingots, three advanced control',
                 'circuits, a 64k storage component and a singularity from the matter condenser.',
                 '',
                 'Raise the Data Nave. Once your Tally Chutes have taken %s in this Age, lay the Matrix on' % tally('age_6'),
                 'the plinth, feed the heart 10,000,000 FE and pray, together if two of you are online.',
                 '',
                 'Caelum opens the Space Age.']),
    sides=[
        ('information/entry', [proof_quest('information', 'age_6')]),
        ('information/mek/osmium', [
            Q('information/side/mystical', 'Resource Crops',
              [t_item('mysticalagriculture:inferium_essence', 32, title='Inferium essence'),
               t_item('mysticalagriculture:prosperity_shard', 8, title='Prosperity shards'),
               t_observe('mysticalagriculture:soulstone', title='Find a body of soulstone')],
              kind='side', icon='mysticalagriculture:inferium_essence',
              desc=['Prospect for inferium in any rock, prosperity deep in igneous and metamorphic rock and soulstone',
                    'in basalt. Then grow the seeds your Age allows.']),
        ]),
        ('information/digital/silicon', [
            Q('information/side/archmage', 'Archmage', t_item('ars_nouveau:archmage_spell_book'), kind='side',
              desc=['The archmage spellbook needs an advanced control circuit.']),
        ]),
        ('information/mob/learner', [
            Q('information/side/jetpack', 'Hydrogen Jetpack', t_item('mekanism:jetpack'), kind='side',
              desc=['Craft a jetpack and fill it with hydrogen from the separator.']),
        ]),
    ],
    explains=[
        Q('information/info/one', 'One Network', t_check(), kind='explain', icon='ae2:controller',
          desc=['From now on the ME network is the one store and crafter. The storage terminal retires, and energy',
                'cubes take over from the LV and MV capacitors.']),
    ],
    supplies='information_supplies', comfort='information_comfort',
    ring=ring_quest('information', 6, 'the Data Nave', 'ae2:quartz_glass',
                    ['Raise the Data Nave around the Tesla Crown: certus quartz blocks, steel casing bases, quartz',
                     'glass and fluix block caps. The heart shows every missing block.']))

# ---------------------------------------------------------------- Space Age
SPACE = age_chapter(
    'space', 'space_age', 'Space Age', ['Fission, rockets and draconium.'], 'firmages:star_chart', 'age_7', 8,
    entry=Q('space/entry', 'The Space Age Dawns', t_stage('age_7'), kind='entry', icon='ad_astra:tier_1_rocket',
            sub='The eighth Age',
            desc=['The sky opens upward. Uranium and fission, rockets to the Moon and Mars, and draconium from the End.',
                  '',
                  'Five strands lead to the Star Chart: Atomic, Rocketry & Moon, Mars, Draconium and Sky.',
                  '',
                  proof_line('age_7')]),
    strands=[
        ('Atomic', [
            Q('space/atom/circuit', 'Elite Circuits', t_item('mekanism:elite_control_circuit', 4),
              desc=['Make four elite control circuits. The reactor parts run on them.']),
            Q('space/atom/uranium', 'Yellow Cake', t_item('mekanism:yellow_cake_uranium', 8),
              desc=['Uraninite veins wake now. Process the ore into yellow cake, the start of fission fuel.']),
            Q('space/atom/fuel', 'Fuel Assemblies', t_item('mekanismgenerators:fission_fuel_assembly', 4),
              desc=['Build four fuel assemblies. They hold the fissile fuel inside the reactor.']),
            Q('space/atom/reactor', 'Fission Reactor', t_item('mekanismgenerators:fission_reactor_casing', 32),
              desc=['Build the fission reactor from 32 casings. Cool it well: a hot reactor melts down.']),
            Q('space/atom/turbine', 'Industrial Turbine',
              [t_item('mekanismgenerators:turbine_rotor', 4, title='Turbine rotors'),
               t_item('mekanismgenerators:turbine_blade', 8, title='Turbine blades')],
              icon='mekanismgenerators:turbine_rotor',
              desc=['Build a thermoelectric boiler and an industrial turbine. Together they turn reactor heat into FE.']),
        ], Q('space/atom/key', 'Reactor Online', t_item('mekanism:pellet_polonium', 2), kind='keystone',
             desc=['Draw two polonium pellets from your reactor waste. One is the catalyst of the Star Chart.'])),
        ('Rocketry & Moon', [
            Q('space/moon/workbench', 'NASA Workbench', t_item('ad_astra:nasa_workbench'),
              desc=['Build a NASA workbench. Every rocket is assembled on it.']),
            Q('space/moon/suit', 'Space Suit',
              [t_item('ad_astra:space_helmet', title='Space helmet'), t_item('ad_astra:space_suit', title='Space suit'),
               t_item('ad_astra:space_pants', title='Space pants'), t_item('ad_astra:space_boots', title='Space boots')],
              icon='ad_astra:space_helmet', desc=['Make a full space suit. Off-world it carries your air.']),
            Q('space/moon/fuel', 'Rocket Fuel', t_item('ad_astra:fuel_bucket'),
              desc=['Refine rocket fuel from your oil. One bucket proves the line runs.']),
            Q('space/moon/pad', 'Launch Pad', t_item('ad_astra:launch_pad'),
              desc=['Craft a launch pad. Rockets start only from one.']),
            Q('space/moon/rocket', 'Tier 1 Rocket', t_item('ad_astra:tier_1_rocket'),
              desc=['Build the tier 1 rocket. It reaches the Moon and no further.']),
            Q('space/moon/land', 'The Moon', t_dim('ad_astra:moon'), icon='ad_astra:moon_sand',
              desc=['Land on the Moon. Desh lies in its rock.']),
            Q('space/moon/desh', 'Desh', t_item('ad_astra:raw_desh', 16), desc=['Mine 16 raw desh on the Moon. It is the metal of the next rocket.']),
        ], Q('space/moon/key', 'Desh Plates', t_item('ad_astra:desh_plate', 32), kind='keystone',
             desc=['Press 32 desh plates. The engine and the tanks of the rocket to Mars are made from them.'])),
        ('Mars', [
            Q('space/mars/rocket', 'Tier 2 Rocket', t_item('ad_astra:tier_2_rocket'),
              desc=['Build the tier 2 rocket from desh. It reaches Mars.']),
            Q('space/mars/land', 'Mars', t_dim('ad_astra:mars'), icon='ad_astra:raw_ostrum', desc=['Land on Mars. Ostrum lies in its red rock.']),
            Q('space/mars/ostrum', 'Ostrum', t_item('ad_astra:raw_ostrum', 16), desc=['Mine 16 raw ostrum on Mars. It builds the rocket to Venus.']),
            Q('space/mars/temple', 'The Mars Temple', t_item('firmages:flight_recorder', consume=False),
              desc=['Something landed on Mars before you. Its temple keeps the record. The Star Chart needs it.']),
        ], Q('space/mars/key', 'Ostrum Plates', t_item('ad_astra:ostrum_plate', 32), kind='keystone',
             desc=['Lay up 32 ostrum plates. Two go into the Star Chart; the rest become the engine and tanks of the rocket to Venus.'])),
        ('Draconium', [
            Q('space/drac/ingot', 'Draconium', t_item('draconicevolution:draconium_ingot', 16),
              desc=['Make 16 draconium ingots. The ore lies deep in granite, diorite and gabbro. Every fusion part is',
                    'made of it.']),
            Q('space/drac/heart', 'Dragon Heart', t_item('draconicevolution:dragon_heart', consume=False),
              desc=['Fuse a dragon heart at wyvern level from a draconium block and core, pellets and ostrum. The',
                    'Ender Dragon drops one too.']),
            Q('space/drac/core', 'Fusion Crafting Core', t_item('draconicevolution:crafting_core'),
              desc=['Build a fusion crafting core. It is the one pedestal crafting of the tech Ages.']),
            Q('space/drac/injectors', 'Injectors',
              [t_item('draconicevolution:basic_crafting_injector', 4, title='Basic injectors'),
               t_item('draconicevolution:wyvern_crafting_injector', 4, title='Wyvern injectors')],
              icon='draconicevolution:wyvern_crafting_injector',
              desc=['Set four basic and four wyvern injectors around the core. They hold the ingredients.']),
        ], Q('space/drac/key', 'Wyvern Core', t_item('draconicevolution:wyvern_core'), kind='keystone',
             desc=['Fuse a wyvern core. It is the heart of wyvern gear and of the Star Chart.'])),
        ('Sky', [
            Q('space/sky/complex', 'Launch Complex', t_item('firmages:launch_control'),
              desc=['Build the launch control, a probe cradle and a gantry with a flame trench. Probes fly from it.']),
            Q('space/sky/voidglass', 'Voidglass', t_item('firmages:voidglass', 2),
              desc=['Launch a probe with a sampler module. It returns with voidglass, found nowhere else.']),
        ], Q('space/sky/key', 'Void Steel', t_item('firmages:void_steel_ingot', consume=False), kind='keystone',
             desc=['Add the Information lens and forge Void Steel from voidglass, desh, starsteel and aurora light.',
                   'The Star Chart needs it.'])),
    ],
    goal=Q('space/goal', 'Offer the Star Chart at the Shrine',
           t_stage('age_8', 'Offer the Star Chart at the shrine'), kind='goal', icon='firmages:star_chart',
           sub='Goal of the Space Age: Caelum opens the Quantum Age',
           desc=['Fuse the Star Chart at wyvern level: a polonium pellet in the middle, a desh plate, a Void Steel',
                 'ingot, two ostrum plates, a wyvern core and the flight recorder from Mars.',
                 '',
                 'Raise the Star Spire. Once your Tally Chutes have taken %s in this Age, lay the Chart on' % tally('age_7'),
                 'the plinth and pray at night, under an open sky, without rain.',
                 '',
                 'Caelum opens the Quantum Age.']),
    sides=[
        ('space/entry', [proof_quest('space', 'age_7')]),
        ('space/atom/circuit', [
            Q('space/side/miner', 'Digital Miner', t_item('mekanism:digital_miner'), kind='side',
              desc=['The digital miner clears a whole area by filter. Keep it: a later Age needs it.']),
        ]),
        ('space/atom/uranium', [
            Q('space/side/injection', 'Ore Times Four', t_item('mekanism:chemical_injection_chamber'), kind='side',
              desc=['Injection with hydrogen chloride gives four times the metal. Without energy upgrades it costs more',
                    'power per ingot than purification.']),
        ]),
        ('space/sky/key', [
            Q('space/side/anchor', 'Sky Anchor', t_item('firmages:sky_anchor'), kind='side',
              desc=['A Sky Anchor carries the shrine\'s blessings within 24 blocks, on the Moon and Mars too. Set',
                    'blessing shards into it.']),
        ]),
        ('space/atom/uranium', [
            Q('space/side/induction', 'Induction Matrix', t_item('mekanism:basic_induction_cell', 4), kind='side',
              desc=['The induction matrix stores FE in its cells and gives it out through its ports.']),
        ]),
        ('space/moon/workbench', [
            Q('space/side/mega', 'MEGA Cells', t_item('megacells:cell_component_1m'), kind='side',
              desc=['Make a 1M cell component. MEGA cells hold far more than the first cells.']),
        ]),
    ],
    explains=[
        Q('space/info/oxygen', 'Oxygen and Gravity', t_check(), kind='explain', icon='ad_astra:oxygen_distributor',
          desc=['Off-world you need a space suit and oxygen. An oxygen distributor fills a sealed base.']),
        Q('space/info/fusion', 'Fusion Crafting', t_check(), kind='explain', icon='draconicevolution:crafting_core',
          desc=['Fusion crafting needs a core, injectors of the right tier and FE. Wyvern items need wyvern',
                'injectors. Every tool and armor piece has a direct recipe: no gear goes into better gear.']),
    ],
    supplies='space_supplies', comfort='space_comfort',
    ring=ring_quest('space', 7, 'the Star Spire', 'ad_astra:desh_block',
                    ['Raise the Star Spire around the Data Nave: steel plating, desh block pillars and ostrum block',
                     'caps. The heart shows every missing block.']))

# ---------------------------------------------------------------- Quantum Age
QUANTUM = age_chapter(
    'quantum', 'quantum_age', 'Quantum Age', ['Fusion, antimatter and the inner planets.'],
    'firmages:quantum_core', 'age_8', 9,
    entry=Q('quantum/entry', 'The Quantum Age Dawns', t_stage('age_8'), kind='entry',
            icon='mekanism:pellet_antimatter', sub='The ninth Age',
            desc=['Fusion power, the SPS, rockets to Venus, Mercury and Glacio, awakened draconium and the Marid.',
                  '',
                  'Five strands lead to the Quantum Core: Fusion, Inner Planets, Awakened, Keystone and Sky.',
                  'The Quantum Core is the last offering at the shrine.',
                  '',
                  proof_line('age_8')]),
    strands=[
        ('Fusion', [
            Q('quantum/fusion/frame', 'Fusion Reactor', t_item('mekanismgenerators:fusion_reactor_frame', 32),
              desc=['Build the fusion reactor from 32 frames. It powers this Age.']),
            Q('quantum/fusion/controller', 'Reactor Controller', t_item('mekanismgenerators:fusion_reactor_controller'),
              desc=['Craft the reactor controller. It sits in the top of the reactor.']),
            Q('quantum/fusion/hohlraum', 'Hohlraum', t_item('mekanismgenerators:hohlraum'),
              desc=['Fill a hohlraum with fuel. A laser fires it to ignite the reactor.']),
            Q('quantum/fusion/fuel', 'Fusion Fuel', t_item('mekanismgenerators:fusion_fuel_bucket'),
              desc=['Mix deuterium and tritium into fusion fuel. It keeps the reactor burning.']),
            Q('quantum/fusion/sps', 'SPS', [t_item('mekanism:sps_casing', 16, title='SPS casings'),
                                            t_item('mekanism:supercharged_coil', 2, title='Supercharged coils')],
              icon='mekanism:sps_casing', desc=['Build the SPS from 16 casings and two supercharged coils. It turns polonium into antimatter.']),
        ], Q('quantum/fusion/key', 'Antimatter Pellets', t_item('mekanism:pellet_antimatter', 2), kind='keystone',
             desc=['Make two antimatter pellets in the SPS. Both go into the Quantum Core.'])),
        ('Inner Planets', [
            Q('quantum/planets/t3', 'Tier 3 Rocket', t_item('ad_astra:tier_3_rocket'),
              desc=['Build the tier 3 rocket from ostrum. It reaches Venus and Mercury.']),
            Q('quantum/planets/venus', 'Venus', t_dim('ad_astra:venus'), icon='ad_astra:raw_calorite',
              desc=['Land on Venus. Calorite lies under its clouds.']),
            Q('quantum/planets/calorite', 'Calorite', t_item('ad_astra:raw_calorite', 16),
              desc=['Mine 16 raw calorite on Venus. It builds the rocket to Glacio.']),
            Q('quantum/planets/mercury', 'Mercury', t_dim('ad_astra:mercury'), icon='ad_astra:tier_3_rocket',
              desc=['Land on Mercury. It holds nothing for this Age; remember the way.']),
            Q('quantum/planets/t4', 'Tier 4 Rocket', t_item('ad_astra:tier_4_rocket'),
              desc=['Build the tier 4 rocket from calorite. It reaches Glacio.']),
            Q('quantum/planets/glacio', 'Glacio', t_dim('ad_astra:glacio'), icon='ad_astra:ice_shard',
              desc=['Land on Glacio, the frozen world. Ice shards are mined there.']),
            Q('quantum/planets/ice', 'Ice Shards', t_item('ad_astra:ice_shard', 8),
              desc=['Mine eight ice shards on Glacio. Two go into the Quantum Core.']),
        ], Q('quantum/planets/key', 'Calorite Plates', t_item('ad_astra:calorite_plate', 32), kind='keystone',
             desc=['Keep a store of 32 calorite plates. One goes into the Quantum Core; engines and tanks take the rest.'])),
        ('Awakened', [
            Q('quantum/awake/stars', 'Nether Stars', t_item('minecraft:nether_star', 4),
              desc=['Gather four Nether Stars. Awakened draconium needs them with a dragon heart.']),
            Q('quantum/awake/ingot', 'Awakened Draconium', t_item('draconicevolution:awakened_draconium_ingot', 16),
              desc=['Fuse 16 awakened draconium from draconium blocks, a dragon heart and Nether Stars. Draconic gear and the reactor are made of it.']),
            Q('quantum/awake/injectors', 'Awakened Injectors',
              t_item('draconicevolution:awakened_crafting_injector', 4),
              desc=['Craft four awakened injectors. Fusion at draconic level needs them.']),
            Q('quantum/awake/evolved', 'Hypercharged Alloy', t_item('evolvedmekanism:alloy_hypercharged', 4),
              desc=['Make four hypercharged alloys. Machines above the ultimate tier are built from them.']),
            Q('quantum/awake/subatomic', 'Subatomic Alloy', t_item('evolvedmekanism:alloy_subatomic', 2, consume=False),
              desc=['Fuse hypercharged alloy with antimatter into two subatomic alloys. The Quantum Core and the',
                    'Awakened Keystone each want one.']),
        ], Q('quantum/awake/key', 'Awakened Core', t_item('draconicevolution:awakened_core'), kind='keystone',
             desc=['Fuse the awakened core. It is the catalyst of the Quantum Core.'])),
        ('Keystone', [
            Q('quantum/marid/alloy', 'Atomic Alloy', t_item('mekanism:alloy_atomic', 4),
              desc=['Make four atomic alloys. The Awakened Keystone needs them.']),
        ], Q('quantum/marid/key', 'Awakened Keystone', t_item('firmages:awakened_keystone', consume=False),
             kind='keystone',
             desc=['Take the Arcane Keystone from its plinth (sneak and right-click the plinth with an empty hand), or make',
                   'a second one. Awaken it by fusion at draconic level: the Keystone on the fusion core, four atomic',
                   'alloys, a wyvern core and a subatomic alloy in the injectors. With magic, the Marid ritual does it',
                   'with two atomic alloys.',
                   '',
                   'Lay the Awakened Keystone back on its plinth. It stays the relic of the Arcane Age and goes into',
                   'the Ultimate Singularity.'])),
        ('Sky', [
            Q('quantum/sky/sample', 'Deep-Space Sample', t_item('firmages:deep_space_sample'),
              desc=['Launch a probe with a deep-space module. It brings back deep-space samples, found nowhere else.']),
        ], Q('quantum/sky/key', 'Quasar Alloy', t_item('firmages:quasar_alloy_ingot', consume=False), kind='keystone',
             desc=['Add the Space lens and forge Quasar Alloy from voidglass, calorite, a deep-space sample and aurora',
                   'light. The Quantum Core needs it.'])),
    ],
    goal=Q('quantum/goal', 'Offer the Quantum Core at the Shrine',
           t_stage('age_9', 'Offer the Quantum Core at the shrine'), kind='goal', icon='firmages:quantum_core',
           sub='Goal of the Quantum Age: Caelum opens the Singularity Age',
           desc=['Fuse the Quantum Core at draconic level: two antimatter pellets, a calorite plate, Quasar Alloy, two',
                 'ice shards and a subatomic alloy.',
                 '',
                 'Raise the Quantum Ring and set the Awakened Keystone back. Once your Tally Chutes have taken'
                 ' %s, lay the Core down,' % tally('age_8'),
                 'feed the heart 100,000,000 FE and pray at night under open sky, together if two are online.',
                 '',
                 'Caelum opens the Singularity Age.']),
    sides=[
        ('quantum/entry', [proof_quest('quantum', 'age_8')]),
        ('quantum/fusion/frame', [
            Q('quantum/side/draconic', 'Draconic Tools', t_item('draconicevolution:draconic_pickaxe'), kind='side',
              desc=['Craft a draconic pickaxe straight from the awakened core. No lesser tool goes into it.']),
        ]),
        ('quantum/marid/alloy', [
            Q('quantum/marid/book', 'Book of Binding: Marid', t_item('occultism:book_of_binding_marid'), kind='side',
              desc=['Write the Marid\'s name into a book of binding. Without it the Marid will not come.']),
            Q('quantum/marid/bind', 'Bind a Marid', t_item('occultism:book_of_binding_bound_marid'), kind='side',
              desc=['Bind the Marid, the strongest of the spirits. Its ritual awakens the Keystone for half the alloy.']),
        ]),
        ('quantum/planets/t3', [
            Q('quantum/side/crystallizer', 'Ore Times Five', t_item('mekanism:chemical_crystallizer'), kind='side',
              desc=['Dissolution, washing and crystallizing give five times the metal. Without energy upgrades it costs',
                    'more power per ingot than injection.']),
        ]),
    ],
    explains=[
        Q('quantum/info/last', 'The Last Offering', t_check(), kind='explain', icon='firmages:offering_plinth',
          desc=['The Quantum Core is the ninth relic. The Singularity Age has no offering: it ends in battle.']),
        Q('quantum/info/reactor', 'Power for the End', t_check(), kind='explain',
          icon='mekanismgenerators:fusion_reactor_controller',
          desc=['Fusion powers this Age. The next Age asks for more than fusion can give.']),
    ],
    supplies='quantum_supplies', comfort='quantum_comfort',
    ring=ring_quest('quantum', 8, 'the Quantum Ring', 'ad_astra:calorite_block',
                    ['Raise the Quantum Ring around the Star Spire: calorite blocks, SPS casings or fusion reactor',
                     'frames, and awakened draconium pylons. The heart shows every missing block.']))

# ---------------------------------------------------------------- Singularity Age
# No shrine tier: the goal waits for finale_won, which firmages-core grants when the tagged final boss dies
# (OriginService, SPEC section 16; Doc 08 section 10.4).
SINGULARITY = age_chapter(
    'singularity', 'singularity_age', 'Singularity Age', ['Chaos, the Singularity and the gate.'],
    'sgjourney:classic_stargate', 'age_9', 10,
    entry=Q('singularity/entry', 'The Singularity Age Dawns', t_stage('age_9'), kind='entry',
            icon='draconicevolution:chaos_shard', sub='The tenth and last Age',
            desc=['The last Age. Chaos, the Ultimate Singularity and a Stargate to The Origin.',
                  '',
                  'Four strands lead to the final battle: Chaos, Singularity, Stargate and Sky.']),
    strands=[
        ('Chaos', [
            Q('singularity/chaos/guardian', 'Chaos Guardian',
              t_boss('draconicevolution:draconic_guardian', 'draconicevolution:chaos_shard'),
              icon='draconicevolution:chaos_shard',
              desc=['The Chaos Guardian waits on a Chaos Island in the End; its crystals drop chaos shards. The',
                    'reactor makes chaos without it.']),
            Q('singularity/chaos/shards', 'Chaos Fragments', t_item('draconicevolution:large_chaos_frag', 8),
              desc=['Keep eight large chaos fragments from the reactor. Two go into the Ultimate Singularity, one into the Coordinates of The Origin.']),
            Q('singularity/chaos/injectors', 'Chaotic Injectors',
              t_item('draconicevolution:chaotic_crafting_injector', 4),
              desc=['Craft four chaotic injectors. Chaotic fusion, the highest tier, needs them.']),
            Q('singularity/chaos/parts', 'Reactor Parts',
              [t_item('draconicevolution:reactor_stabilizer', 4, title='Reactor stabilizers'),
               t_item('draconicevolution:reactor_injector', title='Reactor injector')],
              icon='draconicevolution:reactor_stabilizer',
              desc=['Build four stabilizers and an injector. They hold the reactor core in its field.']),
            Q('singularity/chaos/fuel', 'Reactor Fuel', t_item('draconicevolution:awakened_draconium_block', 4),
              desc=['Make four awakened draconium blocks. They are the reactor\'s first fuel.']),
        ], Q('singularity/chaos/key', 'Reactor Online',
             t_observe('draconicevolution:reactor_core', title='Look at your placed reactor core'),
             kind='keystone', icon='draconicevolution:reactor_core',
             desc=['Build the draconic reactor and bring it online. Only it can power the Stargate.'])),
        ('Singularity', [
            # The Gathering (firmages-core 0.9): one prayer at the complete shrine in this Age gives the nine relics
            # back and sets the heart to awakened=10 (ShrineHeartBlock allows 0..10, ShrineService keeps it while
            # ShrineSavedData.gathered).
            Q('singularity/sing/relics', 'The Gathering',
              t_observe('firmages:shrine_heart[awakened=10]', kind='block_state',
                        title='Look at the heart after the Gathering'),
              icon='firmages:shrine_heart',
              desc=['Feed 1,000,000,000 FE into the heart, then pray at the complete shrine with your fellowship.',
                    'Caelum gives back the nine relics for the chaotic fusion.']),
            Q('singularity/sing/core', 'Chaotic Core', t_item('draconicevolution:chaotic_core'),
              desc=['Fuse the chaotic core. It is the catalyst of the Ultimate Singularity.']),
            Q('singularity/sing/alloy', 'Singular Alloy', t_item('evolvedmekanism:alloy_singular'),
              desc=['Make the singular alloy. The full Lens Crown forges it into the Firmament Alloy, which binds the nine relics into one.']),
        ], Q('singularity/sing/key', 'The Ultimate Singularity', t_item('firmages:ultimate_singularity'),
             kind='keystone', icon='firmages:ultimate_singularity',
             desc=['Forge the Ultimate Singularity from the nine relics, chaos fragments, the Firmament Alloy and the',
                   'chaotic core in a chaotic fusion.'])),
        ('Stargate', [
            Q('singularity/gate/naquadah', 'Naquadah', t_item('sgjourney:raw_naquadah', 16),
              desc=['Mine 16 raw naquadah on Mercury with a digital miner. No other tool reaches it.']),
            Q('singularity/gate/refined', 'Refined Naquadah', t_item('sgjourney:refined_naquadah', 8),
              desc=['Refine naquadah in the metallurgic infuser with refined obsidian. The base block of the gate needs it.']),
            Q('singularity/gate/blocks', 'Stargate Blocks',
              [t_item('sgjourney:classic_stargate_ring_block', 14, title='Ring blocks'),
               t_item('sgjourney:classic_stargate_chevron_block', 9, title='Chevron blocks'),
               t_item('sgjourney:classic_stargate_base_block', title='Base block')],
              icon='sgjourney:classic_stargate_chevron_block',
              desc=['A classic Stargate takes one base block, nine chevron blocks and fourteen ring blocks.',
                    'The Ultimate Singularity goes into the base block.']),
            Q('singularity/gate/dhd', 'Dial Home Device', t_item('sgjourney:classic_dhd'),
              desc=['Build the Dial Home Device. It dials the gate.']),
        ], Q('singularity/gate/key', 'Gate Online',
             t_observe('sgjourney:classic_stargate', title='Look at your assembled Stargate'),
             kind='keystone', icon='sgjourney:classic_stargate',
             desc=['Assemble the Stargate from its base, chevron and ring blocks; the draconic reactor powers it',
                   'through a flux gate.',
                   '',
                   'Its address, on the Coordinates of The Origin: 9, 16, 21, 33, 2, 37, then the point of origin.'])),
        ('Sky', [], Q('singularity/sky/key', 'Firmament Alloy', t_item('firmages:firmament_alloy_ingot', consume=False),
                      kind='keystone',
                      desc=['Set the Quantum lens and forge the Firmament Alloy under the full crown from the singular',
                            'alloy and the sky materials of every Age. The Ultimate Singularity needs it.'])),
    ],
    goal=Q('singularity/goal', 'Beyond the Firmament', t_stage('finale_won', 'Defeat the final boss in The Origin'),
           kind='goal', icon='sgjourney:classic_stargate', sub='Goal of the Singularity Age: the end of the road',
           desc=['Dial The Origin and step through the gate. Gather at the altar, all of you, and The Primordial',
                 'wakes. Its veil opens only when you answer its attacks.',
                 '',
                 'Its fall completes the Firmament. The MekaSuit and the Meka-Tool can then be made.']),
    # Into The Origin: the step from the gate to the battle, on the main route (third player's-eye review).
    bridge=Q('singularity/side/origin', 'Into The Origin',
             t_dim('firmages:origin', 'Step through the gate into The Origin'), deps=['singularity/gate/key'],
             icon='firmages:origin_coordinates',
             desc=['Step through the gate. Gather, all of you, at the central altar to call the final battle.',
                   'A gate on the arena dials home.']),
    sides=[
        ('singularity/chaos/shards', [
            Q('singularity/side/armor', 'Chaotic Armor', t_item('draconicevolution:chaotic_chestpiece'), kind='side',
              desc=['The chaotic chestpiece has a direct recipe. Wear it into the final battle.']),
        ]),
        ('singularity/sing/core', [
            Q('singularity/side/staff', 'Chaotic Staff', t_item('draconicevolution:chaotic_staff'), kind='side',
              desc=['A tool for everything, built directly from the chaotic core.']),
        ]),
        # The shards exist only while The Primordial lives (firmages-core boss, docs/primordial.md); after the gate.
        ('singularity/gate/key', [
            Q('singularity/side/shard', 'A Piece of the Sky', t_item('firmages:consecrated_shard'), kind='side',
              desc=["Keep a shard from The Primordial's last phase: they lie a minute and never come again.",
                    'Four glass and a shard make four Firmament Glass.']),
        ]),
    ],
    explains=[
        Q('singularity/info/reactor', 'The Reactor', t_check(), kind='explain', icon='draconicevolution:reactor_core',
          desc=['The draconic reactor can explode. Watch temperature and field strength and keep the shields',
                'fed. Refuel it only while it is cold.']),
        # How to open the veil of The Primordial (dev/finale-text-c1.md section 1, docs/primordial.md).
        Q('singularity/info/primordial', 'The Primordial', t_check(), kind='explain', icon='firmages:consecrated_shard',
          desc=['Answer its attacks and the veil opens.',
                '- First: hit the grounded keel or shoot the white node.',
                '- Nine Lights: shoot the burning node, or strike as it pulls.',
                '- Shatter: strike with an off-hand shard, or throw one.']),
        Q('singularity/info/finale', 'After the Battle', t_check(), kind='explain', icon='mekanism:ultimate_control_circuit',
          desc=['The MekaSuit and the Meka-Tool are trophies. They can be made only after the final boss falls.']),
    ],
    supplies='singularity_supplies', comfort='singularity_comfort')

AGE_CHAPTERS = [DAWN, STONE, BRONZE, IRON, ARCANE, INDUSTRIAL, ELECTRIC, INFORMATION, SPACE, QUANTUM, SINGULARITY]

# Goal quests that grant the next Age as a quest reward. Only Dawn's First Spark: the shrine is built in the Stone
# Age. From the Stone Age on the shrine grants the Age (firmages-core reads data/firmages/firmages_shrine/
# offerings.json) and the goal is a gamestage task on the next Age without a stage reward (a second grant path
# would double the ceremony).
GOAL_GRANTS = {'dawn': 'age_0'}

# ---------------------------------------------------------------- The Firmament
# The first chapter of the book (Felix 2026-10-15: Welcome goes, its read-me-first content moves here). Three parts:
#  - read-me-first notes (left): waking, reading the Chronicle, keys, the sign, the fellowship, what Dawn withholds,
#    crafting by hand, the shrine. Written in-world (Felix 2026-10-13: full immersion): no mod names, no recipe
#    viewer, no talk of locks or stages. Keys of the welcome and Dawn notes are kept, so their ids and any ticked
#    progress stay.
#  - the road: every Age's offering on one spiral that winds in to "Beyond the Firmament"; each offering appears when
#    its Age dawns (decision A, 2026-10-14).
#  - a build guide per shrine ring beside its offering (diamond, hidden until its Age dawns): the ring rising as an
#    animation, one page per layer with its blocks, the offering and the rite. The pictures and the block counts come
#    from dev/gen_quest_pictures.py (dev/data/quest_pictures.json).
FIRMAMENT_NOTES = [
    Q('welcome/start', 'You Wake', t_check(), kind='note', icon='tfc:firestarter',
      sub='Read me first',
      desc=['You wake beneath a sky that watches. Your hands are empty.',
            '',
            'This is the Chronicle, the one record of your fellowship.',
            '',
            'The world is lived in Ages, from Dawn to the Singularity Age. What a later Age holds does not exist yet. '
            'Each Age ends with an offering at the shrine of Caelum, the sky itself, and its chapter appears here '
            'when it dawns.']),
    Q('welcome/book', 'Reading the Chronicle', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:compass',
      desc=['Each Age has one chapter. Its strands wind from the entry round the goal, the gilded star.',
            '- A strand ends in a keystone, the large engraved hexagon.',
            '- The goal needs every keystone.',
            '- Round ember ones are side paths with a small gift.',
            '- The diamond is the shrine ring of the Age. Its picture opens the build guide.',
            '- Gears are notes. Tick them once read.']),
    Q('firmament/note/keys', 'Hands and Keys', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:stick',
      desc=['Press K to open the Chronicle. Sneak is Left Ctrl here; Left Shift sprints.',
            '',
            'Press V to lay the item in your hand on a block. Hold H to see the sign of your Age again.']),
    Q('welcome/sign', 'The Sign in the Corner', t_check(), deps=['welcome/start'], kind='info',
      icon='tfc:firestarter',  # the sign shows the Age's offering; in Dawn the firestarter (oak sign is Iron Age)
      desc=['Top left, a small sign names your Age and its offering. It fades after a few breaths. Hold H to see it again.']),
    Q('welcome/team', 'One Fellowship', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:player_head',
      desc=['Everybody on this server is one fellowship. The Ages are shared by all of you.',
            'Split the strands between you: one friend smiths, one prospects, one builds.',
            '',
            'A keystone\'s supplies go to whoever claims them first. The gift of a side path and all experience come to each of you.']),
    # 'dawn/info/rules' and 'dawn/info/hand' move here from Dawn (see DAWN_MOVED below)
    Q('firmament/note/shrine', 'The Shrine of Caelum', t_check(), deps=['welcome/start'], kind='info',
      icon='firmages:shrine_heart',
      desc=['From the Stone Age on, every Age ends at the shrine. Raise the ring of the Age round the heart, lay the '
            'offering on the plinth and pray.',
            '',
            'The diamonds along the road are the build guides: the ring rising, every layer with its blocks, and the '
            'rite. Each appears when its Age dawns.']),
]

# Every Age's goal, visible from minute one; details stay hidden until the previous goal is done (linear).
FIRMAMENT = [
    ('first_spark', 'The First Spark', 'dawn', 'age_0', 'tfc:firestarter'),
    ('hearthstone', 'Hearthstone', 'age_0', 'age_1', 'firmages:hearthstone'),
    ('sky_disc', 'Sky Disc', 'age_1', 'age_2', 'firmages:sky_disc'),
    ('steel_heart', 'Steel Heart', 'age_2', 'age_3', 'firmages:steel_heart'),
    ('arcane_keystone', 'Arcane Keystone', 'age_3', 'age_4', 'firmages:arcane_keystone'),
    ('pressure_core', 'Pressure Core', 'age_4', 'age_5', 'firmages:pressure_core'),
    ('humming_core', 'Humming Core', 'age_5', 'age_6', 'firmages:humming_core'),
    ('data_matrix', 'Data Matrix', 'age_6', 'age_7', 'firmages:data_matrix'),
    ('star_chart', 'Star Chart', 'age_7', 'age_8', 'firmages:star_chart'),
    ('quantum_core', 'Quantum Core', 'age_8', 'age_9', 'firmages:quantum_core'),
    # A neutral icon: the gate and The Origin stay unnamed until the Singularity chapter (the Stargate model gave the
    # finale away from minute one).
    ('beyond', 'Beyond the Firmament', 'age_9', 'finale_won', 'minecraft:nether_star'),
]


# Plain Age names for prose ("the Stone Age") and chapter titles ("Ten Ages": no numbers in player text).
AGE_NAME = {'dawn': 'Dawn', 'age_0': 'Stone Age', 'age_1': 'Bronze Age', 'age_2': 'Iron Age', 'age_3': 'Arcane Age',
            'age_4': 'Industrial Age', 'age_5': 'Electric Age', 'age_6': 'Information Age', 'age_7': 'Space Age',
            'age_8': 'Quantum Age', 'age_9': 'Singularity Age'}



# Felix 2026-10-15: what Dawn withholds and crafting by hand are read-me-first, so they move from Dawn's notes to The
# Firmament (keys kept: ids, ticks and the STYLE_TEACHING entries of dev/validate_quests.py stay).
DAWN_MOVED = [q_ for q_ in DAWN['explains'] if q_['key'] in ('dawn/info/rules', 'dawn/info/hand')]
DAWN['explains'] = [q_ for q_ in DAWN['explains'] if q_ not in DAWN_MOVED]
for q_ in DAWN_MOVED:
    q_['deps'] = ['welcome/start']
FIRMAMENT_NOTES += DAWN_MOVED

# ------------------------------------------------------------------------------------------------ pictures
# dev/gen_quest_pictures.py renders them into kubejs/assets/firmages/textures/gui/quests/ and writes their sizes and
# the shrine build data (rings, layers, block counts, rites) to dev/data/quest_pictures.json.
PICTURES = json.load(open(os.path.join(ROOT, 'dev', 'data', 'quest_pictures.json'), encoding='utf-8'))
RINGS = {int(n): info for n, info in PICTURES['shrine'].items()}
RING_NAMES = {n: info['name'] for n, info in RINGS.items()}   # 'the Bronze Sanctum'
PIC = 'firmages:textures/gui/quests/'
PAGE = Verbatim('{@pagebreak}')   # FTB Quests ViewQuestPanel splits the description into pages here
NUMBER = ['no', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve']


def amount(n):
    """Quantities up to twelve as a word, from 13 on as digits (dev/style-guide.md)."""
    return NUMBER[n] if n < len(NUMBER) else '{:,}'.format(n)


def picture(path, size, width=200, click=None, hover=None):
    """One description line that holds only a picture (FTB Library ClientTextComponentUtils: properties split on
    spaces, each key:value on the first colon, %20 for a space). size: the picture's pixel size; the height keeps
    its aspect."""
    w, h = size
    line = '{image:%s%s width:%d height:%d align:center' % (PIC, path, width, round(width * h / float(w)))
    if click:
        line += ' click_action:' + click
    if hover:
        line += ' text:' + hover.replace(' ', '%20')
    return Verbatim(line + '}')


def ponder(item):
    """Click action that opens the Ponder scene of an item (firmages-core QuestPonderLink, client side)."""
    ns, path = item.split(':', 1)
    return 'custom_event:firmages:ponder/%s/%s' % (ns, path)


def guide_key(n):
    return 'firmament/guide/ring_%d' % n


def shrine_texture(n, kind='map'):
    return PIC + ('shrine/ring_%d.png' % n if kind == 'map' else 'shrine/ring_%d_%s.png' % (n, kind))


def ring_picture(n, width=200):
    """The finished ring, clickable: opens its build guide in The Firmament."""
    return picture('shrine/ring_%d.png' % n, RINGS[n]['pictures']['ring'], width,
                   click='open_quest:' + qid(guide_key(n)), hover='Open the build guide')


RING_ICONS = {0: 'firmages:offering_plinth', 1: 'tfc:bronze_bell', 2: 'tfc:metal/lamp/wrought_iron',
              3: 'ars_nouveau:sourcestone', 4: 'immersiveengineering:cokebrick', 5: 'immersiveengineering:floodlight',
              6: 'ae2:quartz_glass', 7: 'ad_astra:desh_block', 8: 'ad_astra:calorite_block'}


def block_list(blocks):
    out = []
    for name, c in blocks:
        out.append('the ' + name if name in ('Offering Plinth', 'Shrine Heart') else '%s %s' % (amount(c), name))
    return out


def layer_place(y):
    return {0: 'level with the heart', 1: 'one block up'}.get(y, '%s blocks up' % amount(y))


def guide_quest(n):
    """Build guide of ring n: page 1 the ring rising and its blocks, one page per layer, last page offering and rite."""
    info = RINGS[n]
    name = info['name']
    title = name[4:] if name.startswith('the ') else name
    offering = next(f[1] for f in FIRMAMENT if f[2] == 'age_%d' % n)
    layers = info['layers']
    first = ('Build %s round the Shrine Heart.' % name if n == 0 else
             'Raise %s round %s.' % (name, RING_NAMES[n - 1]))
    count = amount(info['count'])
    desc = [picture('shrine/ring_%d_build.apng.png' % n, info['pictures']['build'], 220),
            '%s %s new blocks in %s layers:' % (first, count[0].upper() + count[1:], amount(len(layers)))]
    desc += ['- ' + b for b in block_list(info['blocks'])]
    desc += ['', 'Right-click the heart with an empty hand: it shows every missing block as a ghost.']
    for k, layer in enumerate(layers):
        desc += [PAGE, picture('shrine/ring_%d_layer_%d.png' % (n, k + 1), info['pictures']['layer_%d' % (k + 1)], 220),
                 'Layer %s of %s, %s: %s.' % (amount(k + 1), amount(len(layers)), layer_place(layer['y']),
                                              ', '.join(block_list(layer['blocks'])))]
    desc += [PAGE, 'Lay the %s on the plinth.' % offering]
    if 'age_%d' % n in TALLIES:
        desc += ['Your Tally Chutes must have taken %s in this Age.' % tally('age_%d' % n)]
    desc += ['', 'The rite:'] + ['- ' + r for r in info['rites']]
    desc += ['', 'Then sneak and hold right-click on the heart with an empty hand. Caelum opens the %s.'
             % AGE_NAME['age_%d' % (n + 1)]]
    return Q(guide_key(n), title, t_check('Read the build guide'), deps=['firmament/' + FIRMAMENT[n][0]],
             kind='guide', icon=RING_ICONS.get(n), sub='The shrine ring of the %s' % AGE_NAME['age_%d' % n], desc=desc)


# ------------------------------------------------------------------------------------------------ layout hints
# dev/quest_layout.py: related {(strand, strand): 0..1} (shared stations, items or craft; default 0.3), style per
# strand (arc, meander, circle), axis (the strand that ends next to the shrine picture), shrine (ring shown on the
# axis; its click opens the build guide). Strand names must match the chapter definitions; an unknown name falls
# back to the defaults.
LAYOUT_HINTS = {
    'dawn': dict(related={('Stone', 'Hunt'): 0.7, ('Stone', 'Shelter'): 0.5, ('Shelter', 'Hunt'): 0.6},
                 style={'Stone': 'meander', 'Shelter': 'arc', 'Hunt': 'arc'}),
    'stone': dict(related={('Fire & Clay', 'First Metal'): 0.9, ('Fire & Clay', 'Hearth & Shrine'): 0.8,
                           ('First Metal', 'Hearth & Shrine'): 0.6, ('Camp', 'Fire & Clay'): 0.45,
                           ('Camp', 'First Metal'): 0.05, ('Camp', 'Hearth & Shrine'): 0.2},
                  style={'Fire & Clay': 'arc', 'First Metal': 'meander', 'Camp': 'circle', 'Hearth & Shrine': 'arc'},
                  axis='Hearth & Shrine', shrine=0),
    'bronze': dict(related={('Smithing', 'Alloys'): 0.9, ('Prospecting', 'Alloys'): 0.7, ('Kinetics', 'Alloys'): 0.35,
                            ('Smithing', 'Kinetics'): 0.25, ('Smithing', 'Prospecting'): 0.4,
                            ('Prospecting', 'Kinetics'): 0.1},
                   style={'Smithing': 'arc', 'Prospecting': 'meander', 'Alloys': 'arc', 'Kinetics': 'circle'},
                   shrine=1),
    'iron': dict(related={('Metallurgy', 'Power & Motion'): 0.6, ('Metallurgy', 'Sky'): 0.7, ('Power & Motion', 'Sky'): 0.4,
                          ('Survival', 'Frontier'): 0.8, ('Metallurgy', 'Survival'): 0.2, ('Frontier', 'Sky'): 0.3},
                 style={'Metallurgy': 'meander', 'Power & Motion': 'circle', 'Survival': 'arc', 'Frontier': 'arc',
                        'Sky': 'arc'}, shrine=2),
    'arcane': dict(related={('Spirits', 'Alchemy'): 0.7, ('Source', 'Alchemy'): 0.6, ('Spirits', 'Source'): 0.5,
                            ('Sky', 'Machine Line'): 0.8, ('Sky', 'Source'): 0.4, ('Machine Line', 'Spirits'): 0.05},
                   style={'Spirits': 'meander', 'Source': 'arc', 'Alchemy': 'circle', 'Sky': 'arc', 'Machine Line': 'arc'},
                   shrine=3),
    'industrial': dict(related={('Coke & Steel', 'Ore Line'): 0.7, ('Coke & Steel', 'Power'): 0.45,
                                ('Ore Line', 'Logistics & Backfill'): 0.65, ('Power', 'Logistics & Backfill'): 0.4,
                                ('Power', 'Ore Line'): 0.5, ('Coke & Steel', 'Logistics & Backfill'): 0.15,
                                ('Sky', 'Power'): 0.6, ('Sky', 'Coke & Steel'): 0.5},
                       style={'Coke & Steel': 'meander', 'Power': 'arc', 'Ore Line': 'circle',
                              'Logistics & Backfill': 'arc', 'Sky': 'arc'}, shrine=4),
    'electric': dict(related={('Power Grid', 'Circuits'): 0.7, ('Oil', 'Aluminium & Excavator'): 0.4,
                              ('Power Grid', 'Oil'): 0.5, ('Circuits', 'Sky'): 0.6,
                              ('Aluminium & Excavator', 'Circuits'): 0.5},
                     style={'Power Grid': 'arc', 'Oil': 'meander', 'Aluminium & Excavator': 'circle', 'Circuits': 'arc',
                            'Sky': 'arc'}, shrine=5),
    'information': dict(related={('Digital', 'Factory'): 0.8, ('Digital', 'Mob Data'): 0.6, ('Factory', 'The End'): 0.3,
                                 ('Mob Data', 'The End'): 0.6, ('Digital', 'Sky'): 0.5},
                        style={'Digital': 'arc', 'Factory': 'meander', 'Mob Data': 'circle', 'The End': 'arc',
                               'Sky': 'arc'}, shrine=6),
    'space': dict(related={('Atomic', 'Rocketry & Moon'): 0.5, ('Rocketry & Moon', 'Mars'): 0.9,
                           ('Mars', 'Draconium'): 0.4, ('Atomic', 'Draconium'): 0.5, ('Sky', 'Rocketry & Moon'): 0.7},
                  style={'Atomic': 'circle', 'Rocketry & Moon': 'meander', 'Mars': 'arc', 'Draconium': 'arc',
                         'Sky': 'arc'}, shrine=7),
    'quantum': dict(related={('Fusion', 'Awakened'): 0.6, ('Inner Planets', 'Fusion'): 0.4,
                             ('Awakened', 'Keystone'): 0.9, ('Sky', 'Inner Planets'): 0.5},
                    style={'Fusion': 'circle', 'Inner Planets': 'meander', 'Awakened': 'arc', 'Keystone': 'arc',
                           'Sky': 'arc'}, shrine=8),
    'singularity': dict(related={('Chaos', 'Singularity'): 0.8, ('Singularity', 'Stargate'): 0.6,
                                 ('Stargate', 'Sky'): 0.5, ('Chaos', 'Stargate'): 0.3},
                        style={'Chaos': 'meander', 'Singularity': 'circle', 'Stargate': 'arc', 'Sky': 'arc'}),
}
LAYOUTS = {}   # chapter file name -> dev/quest_layout.Layout of the last build (dev/preview_quests.py reads them)

# ------------------------------------------------------------------------------------------------ assembly
def build_tasks(quest):
    out = []
    for n, entry in enumerate(quest['tasks']):
        spec, title = entry
        specs = spec if isinstance(spec, list) else [spec]
        for m, s in enumerate(specs):
            task_def, task_title = s if isinstance(s, tuple) else (s, None)
            tid = qid('%s/task/%d/%d' % (quest['key'], n, m))
            d = dict(task_def)
            d['id'] = tid
            t = task_title or (title if len(specs) == 1 else None)
            if t:
                lang('task', tid, 'title', t)
            out.append(d)
    return out


def build_rewards(quest, rewards):
    out = []
    for n, r in enumerate(rewards):
        d = dict(r)
        d['id'] = qid('%s/reward/%d' % (quest['key'], n))
        if 'table' in d:
            d['table_id'] = L(table_long_id(d.pop('table')))
        out.append(d)
    return out


# Shapes gilded, rune and ember are the pack's own (dev/gen_quest_art.py writes them to
# kubejs/assets/ftbquests/textures/shapes/<name>/; FTB Quests only looks in its own namespace, javap QuestShape).
# Steps keep the chapter default (hexagon); explanations stay gears, ring quests diamonds.
SHAPES = {'entry': ('rune', 2.0), 'step': ('', 0.0), 'keystone': ('rune', 1.5), 'goal': ('gilded', 3.0),
          'side': ('ember', 0.85), 'explain': ('gear', 0.0), 'info': ('', 0.0), 'ring': ('diamond', 1.25),
          'guide': ('diamond', 1.25), 'note': ('rune', 1.6)}

# Chapter art keys (dev/gen_quest_art.py): banner_<key>.png, bg_<key>.png, shader firmages:sky_<key>
BANNER_W, BANNER_H = 8.0, 2.0


def banner_image(chapter_key, art_key, x, y):
    """Chapter image (ChapterImage NBT, javap 2101.1.36: x, y, width, height in quest grid units, rotation, image,
    color, alpha, order, position_locked) with the Age banner centred above the quests."""
    return {'id': qid('image/%s/banner' % chapter_key), 'x': D(x), 'y': D(y), 'width': D(BANNER_W),
            'height': D(BANNER_H), 'rotation': D(0), 'image': 'firmages:textures/gui/quests/banner_%s.png' % art_key,
            'color': 16777215, 'alpha': 255, 'order': 0, 'position_locked': True}


def quest_nbt(quest, x, y, rewards, extra=None):
    oid = qid(quest['key'])
    shape, size = SHAPES[quest['kind']]
    d = {'id': oid, 'x': D(x), 'y': D(y), 'tasks': build_tasks(quest)}
    if shape:
        d['shape'] = shape
    if size:
        d['size'] = D(size)
    if quest['deps']:
        d['dependencies'] = [qid(k) for k in quest['deps']]
    if quest['kind'] in ('side', 'explain', 'ring', 'guide'):
        d['optional'] = True
    if quest['icon']:
        d['icon'] = {'id': quest['icon']}
    if rewards:
        d['rewards'] = build_rewards(quest, rewards)
    if quest['kind'] in ('entry', 'keystone', 'goal', 'side', 'explain', 'ring', 'guide', 'info', 'note'):
        d['tags'] = [quest['kind']]
    if extra:
        d.update(extra)
    lang('quest', oid, 'title', quest['title'])
    if quest['sub']:
        lang('quest', oid, 'quest_subtitle', quest['sub'])
    if quest['desc']:
        lang('quest', oid, 'quest_desc', paragraphs(quest['desc']))
    return d


def chapter_nbt(key, filename, title, subtitle, icon, group, order, quests, required_stage=None, extra=None,
                tags=(), images=()):
    """tags: theme selectors ([#age_3] in ftb_quests_theme.txt picks background and colours per chapter; QuestScreen
    resolves theme properties against the selected chapter). images: ChapterImage list (banner_image)."""
    cid = qid('chapter/' + key)
    d = {'id': cid, 'filename': filename, 'group': qid('group/' + group) if group else '', 'order_index': order,
         'icon': {'id': icon}, 'default_quest_shape': 'hexagon', 'default_hide_dependency_lines': False,
         'progression_mode': 'flexible', 'quests': quests, 'quest_links': [], 'images': list(images)}
    if tags:
        d['tags'] = list(tags)
    if required_stage:
        # ProgressiveStages 3.0.6 ChapterMixin injects Chapter.isVisible(TeamData) at HEAD (cancellable) and returns
        # false until the team owns the stage; ChapterPanel lists DefaultChapterGroup.getVisibleChapters(TeamData),
        # so the chapter is absent from the sidebar, not merely locked (javap, 2026-10-13).
        d['progressivestages_required_stage'] = required_stage
    if extra:
        d.update(extra)
    lang('chapter', cid, 'title', title)
    if subtitle:
        lang('chapter', cid, 'chapter_subtitle', subtitle)
    return d


def wire_age_chapter(ch):
    """Dependencies of an Age chapter (fills the quests' empty deps, as the book always did) and the layout spec for
    dev/quest_layout.py. Strand steps follow each other from the entry, a keystone follows its last step, the goal
    needs every keystone (and the bridge), routes replace the keystones as the goal's dependencies; side paths start
    at their attach quest, notes and the ring quest at the entry (their lines hidden)."""
    entry = ch['entry']
    kinds, deps, hide = {entry['key']: 'entry'}, {}, set()
    strands = []
    for name, steps, keystone in ch['strands']:
        prev, keys = entry['key'], []
        for s in steps:
            s['deps'] = s['deps'] or [prev]
            prev = s['key']
            keys.append(s['key'])
            kinds[s['key']] = s['kind']
        keystone['deps'] = keystone['deps'] or [prev]
        keys.append(keystone['key'])
        kinds[keystone['key']] = 'keystone'
        strands.append((name, keys))
    goal = ch['goal']
    goal['deps'] = [k[2]['key'] for k in ch['strands']]
    links = []
    bridge = ch.get('bridge')
    if bridge:
        goal['deps'].append(bridge['key'])
        links.append((bridge['key'], list(bridge['deps'])))
        kinds[bridge['key']] = bridge['kind']
    for r in ch.get('routes') or []:
        links.append((r['key'], list(r['deps'])))
        kinds[r['key']] = r['kind']
    if ch.get('routes'):
        goal['deps'] = [r['key'] for r in ch['routes']]
    kinds[goal['key']] = 'goal'
    sides = []
    for attach, line in ch['sides']:
        prev, keys = attach, []
        for s in line:
            s['deps'] = s['deps'] or [prev]
            prev = s['key']
            keys.append(s['key'])
            kinds[s['key']] = s['kind']
        sides.append((attach, keys))
    for e in ch['explains']:
        e['deps'] = e['deps'] or [entry['key']]
        kinds[e['key']] = e['kind']
        hide.add(e['key'])
    ring = ch.get('ring')
    if ring:
        ring['deps'] = ring['deps'] or [entry['key']]
        kinds[ring['key']] = 'ring'
        hide.add(ring['key'])
    for q_ in chapter_quests(ch):
        deps[q_['key']] = list(q_['deps'])
    return dict(key=ch['key'], entry=entry['key'], goal=goal['key'], strands=strands, sides=sides,
                explains=[e['key'] for e in ch['explains']], ring=ring['key'] if ring else None, links=links,
                kinds=kinds, deps=deps, hide_lines=hide)


def chapter_quests(ch):
    """Every quest of an Age chapter, in book order."""
    out = [ch['entry']]
    for _name, steps, keystone in ch['strands']:
        out += steps + [keystone]
    if ch.get('bridge'):
        out.append(ch['bridge'])
    out += list(ch.get('routes') or [])
    out.append(ch['goal'])
    for _attach, line in ch['sides']:
        out += line
    out += ch['explains']
    if ch.get('ring'):
        out.append(ch['ring'])
    return out


def laid_out(lay, quest, rewards, extra=None):
    """quest_nbt at the quest's place in a dev/quest_layout.Layout, with its curved lines."""
    x, y = lay.pos(quest['key'])
    extra = dict(extra or {})
    cps = lay.control_points(quest['key'], qid)
    if cps:
        extra['dep_control_pts'] = {k: [D(v) for v in pts] for k, pts in cps.items()}
    return quest_nbt(quest, x, y, rewards, extra=extra or None)


def shrine_image(chapter_key, lay, im, key=None):
    """The finished ring as a chapter image on the map; a click opens its build guide in The Firmament."""
    x, y = lay.image_pos(im)
    d = {'id': qid(key or 'image/%s/shrine' % chapter_key), 'x': D(x), 'y': D(y), 'width': D(im['w']), 'height': D(im['h']),
         'rotation': D(0), 'image': shrine_texture(im['ring'], 'map'), 'color': 16777215, 'alpha': 255, 'order': 1,
         'position_locked': True, 'click_action': 'open_quest:' + qid(guide_key(im['ring']))}
    lang('image', d['id'], 'title', 'Raise %s: the build guide' % RING_NAMES[im['ring']])
    return d


def top_banner(chapter_key, art_key, lay, middle=False):
    """The chapter banner above everything else on the map, centred on the focal quest (or the map's middle)."""
    _x0, y0, _x1, _y1 = lay.bbox()
    fx = lay.pos(lay.focus)[0] if lay.focus and not middle else (_x0 + _x1) / 2.0
    return banner_image(chapter_key, art_key, fx, y0 - BANNER_H / 2.0 - 0.6)


def build_age_chapter(ch):
    age = ch['stage']
    xp = XP[age]
    spec = wire_age_chapter(ch)
    lay = quest_layout.age_chapter(spec, LAYOUT_HINTS.get(ch['key']))
    quests = [laid_out(lay, ch['entry'], [r_xp(xp)])]
    for _name, steps, keystone in ch['strands']:
        for s in steps:
            quests.append(laid_out(lay, s, [r_xp(xp)]))
        quests.append(laid_out(lay, keystone, [r_xp(xp * 3), r_random(ch['supplies'])]))
    if ch.get('bridge'):
        quests.append(laid_out(lay, ch['bridge'], [r_xp(xp)]))
    for r in ch.get('routes') or []:
        quests.append(laid_out(lay, r, [r_xp(xp)]))
    goal_rewards = [r_xp(xp * 10)]
    if ch['key'] in GOAL_GRANTS:
        goal_rewards.append(r_stage(GOAL_GRANTS[ch['key']]))
    goal_extra = {'dependency_requirement': 'one_completed'} if ch.get('routes') else None
    quests.append(laid_out(lay, ch['goal'], goal_rewards, extra=goal_extra))
    for _attach, line in ch['sides']:
        for j, s in enumerate(line):
            last = j == len(line) - 1
            quests.append(laid_out(lay, s, [r_xp(xp), r_choice(ch['comfort'])] if last else [r_xp(xp)]))
    for e in ch['explains']:
        quests.append(laid_out(lay, e, [], extra={'hide_dependency_lines': True}))
    if ch.get('ring'):
        ring = dict(ch['ring'])   # the finished ring on top; a click opens the build guide in The Firmament
        ring['desc'] = [ring_picture(int(age[4:]), 180)] + ring['desc']
        quests.append(laid_out(lay, ring, [r_xp(xp * 2)], extra={'hide_dependency_lines': True}))
    images = [shrine_image(ch['key'], lay, im) for im in lay.images if im['kind'] == 'shrine']
    images.insert(0, top_banner(ch['key'], age, lay))
    LAYOUTS[ch['filename']] = lay
    return chapter_nbt(ch['key'], ch['filename'], ch['title'], ch['subtitle'], ch['icon'], ch['group'], ch['order'],
                       quests, required_stage=age, tags=[age], images=images,
                       extra={'autofocus_id': qid(ch['goal']['key'])})


def build_firmament():
    """Read-me-first notes on the left, the road of offerings as a spiral that winds in to "Beyond the Firmament",
    and beside every offering the build guide of its Age's ring with the finished ring as a picture."""
    lay = quest_layout.Layout('firmament')
    quests = []
    n_road = len(FIRMAMENT)
    flow, road_pos = [], []
    for i, (key, _title, _age, _stage, _icon) in enumerate(FIRMAMENT):
        t = i / (n_road - 1.0)
        a = 10.0 + 318.0 * t + quest_layout.jit(key, 'a', 3.0)      # degrees from the west, clockwise over the top
        r = 9.4 - 6.1 * t
        th = math.radians(180.0 - a)
        x, y = r * math.cos(th) * 1.42, r * math.sin(th) * 0.92
        th2 = math.radians(180.0 - a - 1.0)
        r2 = r - 6.1 / 318.0
        x2, y2 = r2 * math.cos(th2) * 1.42, r2 * math.sin(th2) * 0.92
        d = math.hypot(x2 - x, y2 - y)
        road_pos.append((x, y))
        node = lay.add('firmament/' + key, 'goal' if key == 'beyond' else 'keystone', x, y,
                       deps=['firmament/' + FIRMAMENT[i - 1][0]] if i else [])
        node.stiff = True
        lay.tang[node.key] = ((x2 - x) / d, (y2 - y) / d)
        if i:
            flow.append(('firmament/' + FIRMAMENT[i - 1][0], node.key))
    lay.nodes['firmament/beyond'].fixed = True
    # build guides outside the spiral, the finished ring beyond each guide
    for n in sorted(RINGS):
        x, y = road_pos[n + 1]
        d = math.hypot(x, y) or 1.0
        ox, oy = x / d, y / d
        g = lay.add(guide_key(n), 'guide', x + ox * 2.1, y + oy * 1.7, deps=['firmament/' + FIRMAMENT[n][0]],
                    hide_lines=True)
        g.stiff = True
        w = 3.2
        size = RINGS[n]['pictures']['ring']
        lay.images.append(dict(kind='shrine', ring=n, x=x + ox * 4.6, y=y + oy * 3.5, w=w, h=w * size[1] / size[0],
                               anchor=guide_key(n)))
    # read-me-first notes: a constellation west of the start of the road, "You Wake" in its middle
    sx, sy = road_pos[0]
    cx, cy = sx - 6.2, sy - 0.4
    lay.add('welcome/start', 'note', cx, cy).fixed = True
    others = FIRMAMENT_NOTES[1:]
    for j, nq in enumerate(others):
        ang = math.radians(100 + j * (330.0 / len(others)))
        rr = 2.7 + 0.35 * (j % 2)
        kind = 'explain' if nq['kind'] == 'explain' else 'info'
        lay.add(nq['key'], kind, cx + rr * math.cos(ang) * 1.2 + quest_layout.jit(nq['key'], 'nx', 0.15),
                cy + rr * math.sin(ang) + quest_layout.jit(nq['key'], 'ny', 0.15), deps=nq['deps'])
    lay.focus = 'welcome/start'
    lay.relax()
    lay.curves(flow)

    for nq in FIRMAMENT_NOTES:
        quests.append(laid_out(lay, nq, [r_xp(5)]))
    prev = None
    for i, (key, title, age, stage, icon) in enumerate(FIRMAMENT):
        final = key == 'beyond'
        dawn = age == 'dawn'
        name = AGE_NAME[age] if dawn else 'the ' + AGE_NAME[age]
        if final:
            task, sub = 'Defeat the final boss', 'Goal of the Singularity Age: the last battle'
            desc = ['Nine offerings, one gate. Beyond it waits the last battle, and its end is the end of the road.']
        else:
            task, sub = 'Reach the ' + AGE_NAME[stage], 'Goal of ' + name
            desc = (['The first fire. It opens the Stone Age.'] if dawn else
                    ['The offering of %s. Its build guide stands beside it.' % name])
        q_ = Q('firmament/' + key, title, t_stage(stage, task), deps=[prev] if prev else [],
               kind='goal' if final else 'keystone', icon=icon, sub=sub, desc=desc)
        # Decision A (2026-10-14): each offering appears when its Age dawns (its predecessor's Age is reached). The
        # first spark and the end of the road stay in view from the start.
        extra = {'hide_until_deps_complete': True} if prev and not final else None
        quests.append(laid_out(lay, q_, [], extra=extra))
        prev = q_['key']
    for n in sorted(RINGS):
        quests.append(laid_out(lay, guide_quest(n), [r_xp(XP['age_%d' % n])],
                               extra={'hide_until_deps_complete': True, 'hide_dependency_lines': True}))
    images = [top_banner('firmament', 'firmament', lay, middle=True)]
    for im in lay.images:
        d = shrine_image('firmament', lay, im, key='image/firmament/shrine/%d' % im['ring'])
        d['dependency'] = qid('firmament/' + FIRMAMENT[im['ring']][0])   # shown once its Age has dawned
        images.append(d)
    LAYOUTS['the_firmament'] = lay
    return chapter_nbt('firmament', 'the_firmament', 'The Firmament',
                       ['Read me first. Then the whole road, from the first spark to the last.'],
                       'tfc:firestarter', None, 0, quests,
                       extra={'progression_mode': 'linear', 'hide_quest_details_until_startable': True,
                              'default_quest_shape': 'gear', 'autofocus_id': qid('welcome/start')},
                       tags=['firmament'], images=images)


# ---------------------------------------------------------------- the Great Works
# Felix 2026-10-15: moving multiblocks as build strands with pictures (research n7-build section 2). One small chapter
# per work in the group "Great Works", hidden until its Age like an Age chapter, laid out in the shape of the build.
# Steps give real items for the shared base (team rewards, like the keystone supplies); the entry is the plan.
# Pacing and the links into the Age strands come in round B2.
def r_item(item, count=1):
    d = {'type': 'item', 'item': {'count': 1, 'id': item}, 'team_reward': True}
    if count > 1:
        d['count'] = count
    return d


ROPE_BRIDGES = ['mcwbridges:rope_%s_bridge' % w for w in
                ('oak', 'birch', 'spruce', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry')]

WORKS = [
    dict(key='gatehouse', filename='work_gatehouse', title='The Gatehouse', stage='age_1', order=20,
         icon='create:rope_pulley', subtitle=['A Great Work of the Bronze Age: a gate that shuts on its own.'],
         pictures=[('gatehouse_open', (6.6, -3.4), 4.4, ponder('create:rope_pulley'))],
         quests=[
             (Q('works/gate/entry', 'The Gatehouse', t_check('Read the plan'), kind='entry', icon='create:rope_pulley',
                sub='A Great Work of the Bronze Age',
                desc=[picture('works/gatehouse_open.png', PICTURES['works']['gatehouse_open'], 200,
                              click=ponder('create:rope_pulley'), hover='Watch it built'),
                      'Build a gate that shuts on its own: towers of rock, a bridge over the ditch, a grate that drops '
                      'and a bridge that lifts.',
                      '',
                      'Click the picture to watch the grate built, step by step.']),
              (-4.6, -3.0), [r_xp(XP['age_1'])]),
             (Q('works/gate/bridge', 'A Bridge Over the Ditch',
                t_any_item(ROPE_BRIDGES, 'Eight rope bridges of any wood', count=8), icon='mcwbridges:rope_oak_bridge',
                desc=['Make eight rope bridges of any wood. They span the ditch before your gate.']),
              (-4.7, 0.3), [r_item('create:shaft', 8)]),
             (Q('works/gate/portcullis', 'Portcullis',
                [t_observe('create:rope_pulley', title='Look at the rope pulley over your gate'),
                 t_item('tfc:metal/bars/bronze', 9, title='Nine bronze bars for the grate')], icon='tfc:metal/bars/bronze',
                desc=[picture('works/gatehouse_shut.png', PICTURES['works']['gatehouse_shut'], 180,
                              click=ponder('create:rope_pulley'), hover='Watch it built'),
                      'Glue a bronze grate together and hang it in a wall slot under a rope pulley. Turn the pulley '
                      'and the gate shuts.']),
              (-2.6, 3.1), [r_item('tfc:metal/ingot/bronze', 2)]),
             (Q('works/gate/drawbridge', 'Drawbridge',
                [t_observe('create:mechanical_bearing', title='Look at the bearing of your drawbridge'),
                 t_item('create:super_glue', title='Super glue')], icon='create:mechanical_bearing',
                desc=['Glue the bridge deck to a mechanical bearing at the gate. Turn it up at night and the ditch is '
                      'open again.',
                      picture('works/gatehouse_open.png', PICTURES['works']['gatehouse_open'], 120,
                              click=ponder('create:mechanical_bearing'), hover='Watch a bearing turn')]),
              (2.6, 3.1), [r_item('create:cogwheel', 4)]),
             (Q('works/gate/signal', 'One Signal', t_item('create:redstone_link', 2), icon='create:redstone_link',
                desc=['Pair two redstone links. The gate obeys a lever from across the yard.']),
              (4.7, 0.3), [r_item('create:andesite_alloy', 8)]),
             (Q('works/gate/goal', 'Shut the Gate',
                t_observe('create:gearshift[powered=true]', kind='block_state',
                          title='Look at the gearshift while the lever holds it'),
                kind='goal', icon='create:gearshift', sub='The gate holds',
                desc=['Throw the lever on the gearshift of the pulley. The grate drops, the bridge rises, and the '
                      'night stays outside.']),
              (0.0, -0.9), [r_item('tfc:metal/ingot/bronze', 4), r_item('create:andesite_casing', 8)]),
         ]),
    dict(key='house', filename='work_living_house', title='The Living House', stage='age_2', order=21,
         icon='create:clockwork_bearing', subtitle=['A Great Work of the Iron Age: a house that moves for you.'],
         pictures=[('living_house', (7.4, -3.0), 4.6, ponder('create:elevator_pulley'))],
         quests=[
             (Q('works/house/entry', 'The Living House', t_check('Read the plan'), kind='entry',
                icon='create:brass_door', sub='A Great Work of the Iron Age',
                desc=[picture('works/living_house.png', PICTURES['works']['living_house'], 200,
                              click=ponder('create:elevator_pulley'), hover='Watch a lift built'),
                      'Build a house that moves for you: doors that slide, a lift between floors, lamps on a link, a '
                      'board that speaks and a clock in the gable. The hearth stays its heart.']),
              (0.0, -4.6), [r_xp(XP['age_2'])]),
             (Q('works/house/doors', 'Doors That Slide', t_item('create:brass_door', 2), icon='create:brass_door',
                desc=['Hang two brass doors in your entrance. Sliding doors open by hand or on a redstone signal.']),
              (-3.8, -2.0), [r_item('tfc:metal/ingot/brass', 2)]),
             (Q('works/house/lights', 'Lights on a Link',
                [t_item('minecraft:redstone_lamp', 4, title='Four redstone lamps'),
                 t_item('create:redstone_link', 2, title='Two redstone links')], icon='minecraft:redstone_lamp',
                desc=['Wire four lamps to a redstone link. One lever by the door lights the whole house.']),
              (-3.8, 2.0), [r_item('create:electron_tube', 2)]),
             (Q('works/house/display', 'The House Speaks',
                [t_observe('create:display_link', title='Look at your display link'),
                 t_item('create:display_board', 4, title='Four display boards')], icon='create:display_link',
                desc=[picture('works/living_house.png', PICTURES['works']['living_house'], 120,
                              click=ponder('create:display_link'), hover='Watch a display link'),
                      'Show the time and your stores on a display board by the door.']),
              (0.0, 4.0), [r_item('create:brass_casing', 8)]),
             (Q('works/house/lift', 'A Lift',
                t_observe('create:elevator_contact', title='Look at a working elevator contact'),
                icon='create:elevator_pulley',
                desc=['Run an elevator pulley through the floors. A redstone contact on each floor turns into an '
                      'elevator contact once the lift works.',
                      picture('works/living_house.png', PICTURES['works']['living_house'], 120,
                              click=ponder('create:elevator_pulley'), hover='Watch a lift built')]),
              (3.8, 2.0), [r_item('create:precision_mechanism')]),
             (Q('works/house/goal', 'The House Keeps Time',
                t_observe('create:clockwork_bearing', title='Look at the clockwork bearing in your gable'),
                kind='goal', icon='create:clockwork_bearing', sub='The house is awake',
                desc=['Set a clockwork bearing in the gable and give it hands. The house keeps the hours of the sun.',
                      picture('works/living_house.png', PICTURES['works']['living_house'], 120,
                              click=ponder('create:clockwork_bearing'), hover='Watch a clock built')]),
              (0.0, 0.0), [r_item('tfc:metal/ingot/wrought_iron', 4), r_item('tfc:metal/ingot/steel', 1)]),
         ]),
    dict(key='silo', filename='work_silo', title='The Silo', stage='age_7', order=22,
         icon='ad_astra:launch_pad', subtitle=['A Great Work of the Space Age: a rocket that rises from the ground.'],
         pictures=[('silo_shut', (-5.4, -3.0), 4.4, ponder('ad_astra:launch_pad')),
                   ('silo_open', (5.4, -3.0), 4.4, ponder('ad_astra:launch_pad'))],
         quests=[
             (Q('works/silo/entry', 'The Silo', t_check('Read the plan'), kind='entry', icon='ad_astra:launch_pad',
                sub='A Great Work of the Space Age',
                desc=[picture('works/silo_open.png', PICTURES['works']['silo_open'], 200,
                              click=ponder('ad_astra:launch_pad'), hover='Watch the hatch built'),
                      'Sink a silo for your rocket: a shaft 15 by 15, a hatch of two leaves that slide apart, and one '
                      'signal that opens the sky.',
                      '',
                      'Click the picture to watch the hatch built.']),
              (0.0, -6.6), [r_xp(XP['age_7'])]),
             (Q('works/silo/shaft', 'The Shaft',
                t_observe('ad_astra:launch_pad', title='Look at the launch pad at the bottom of your shaft'),
                icon='ad_astra:launch_pad',
                desc=['Sink a shaft 15 by 15 and nine deep. Shore its walls; the rock remembers every cut. Set the pad '
                      'at the bottom.']),
              (0.0, -3.8), [r_item('ad_astra:desh_plate', 4)]),
             (Q('works/silo/hatch', 'The Hatch',
                [t_item('create:gantry_carriage', 2, title='Two gantry carriages'),
                 t_item('create:sequenced_gearshift', title='A sequenced gearshift'),
                 t_item('create:super_glue', title='Super glue')], icon='create:gantry_carriage',
                desc=[picture('works/silo_shut.png', PICTURES['works']['silo_shut'], 180,
                              click=ponder('ad_astra:launch_pad'), hover='Watch the hatch built'),
                      'Glue two leaves over the shaft. Each rides a gantry carriage and slides into a pocket under '
                      'the rim.']),
              (0.0, -1.1), [r_item('ad_astra:desh_ingot', 4)]),
             (Q('works/silo/signal', 'One Signal',
                [t_item('create:redstone_link', 2, title='Two redstone links'),
                 t_item('create:pulse_repeater', title='A pulse repeater')], icon='create:redstone_link',
                desc=['One link opens the hatch. A delayed link wakes the pad once the leaves are clear.']),
              (-2.5, 1.5), [r_item('ad_astra:fuel_bucket')]),
             (Q('works/silo/fuel', 'Fuel Line', t_item('ad_astra:fuel_bucket', 4), icon='ad_astra:fuel_bucket',
                desc=['Pipe fuel down to the pad. A rocket in the silo waits full.']),
              (2.5, 1.5), [r_item('ad_astra:gas_tank')]),
             (Q('works/silo/goal', 'Launch from the Silo',
                t_observe('ad_astra:launch_pad[powered=true]', kind='block_state',
                          title='Look at the pad while the signal wakes it'),
                kind='goal', icon='ad_astra:tier_1_rocket', sub='The sky opens from below',
                desc=['Open the hatch and wake the pad with one signal. Ten heartbeats later the rocket rises through '
                      'your own sky.']),
              (0.0, 4.3), [r_item('ad_astra:ostrum_plate', 4), r_item('ad_astra:large_gas_tank')]),
         ]),
]
WORK_DEPS = {   # dependency wiring of each work, by quest key (the drawing follows these lines)
    'works/gate/bridge': ['works/gate/entry'], 'works/gate/portcullis': ['works/gate/bridge'],
    'works/gate/drawbridge': ['works/gate/portcullis'], 'works/gate/signal': ['works/gate/drawbridge'],
    'works/gate/goal': ['works/gate/signal'],
    'works/house/doors': ['works/house/entry'], 'works/house/lights': ['works/house/doors'],
    'works/house/display': ['works/house/lights'], 'works/house/lift': ['works/house/display'],
    'works/house/goal': ['works/house/lift'],
    'works/silo/shaft': ['works/silo/entry'], 'works/silo/hatch': ['works/silo/shaft'],
    'works/silo/signal': ['works/silo/hatch'], 'works/silo/fuel': ['works/silo/hatch'],
    'works/silo/goal': ['works/silo/signal', 'works/silo/fuel'],
}
WORK_FLOW = {   # tangents of the drawn path: the gate's arch, the walk round the house, the shaft going up
    'gatehouse': {'works/gate/entry': (0, 1), 'works/gate/bridge': (0.3, 1), 'works/gate/portcullis': (1, 0.35),
                  'works/gate/drawbridge': (1, -0.35), 'works/gate/signal': (-0.3, -1)},
    'house': {'works/house/entry': (-1, 0.6), 'works/house/doors': (0, 1), 'works/house/lights': (1, 0.6),
              'works/house/display': (1, -0.6), 'works/house/lift': (-0.5, -1)},
    'silo': {'works/silo/entry': (0, 1), 'works/silo/shaft': (0, 1), 'works/silo/hatch': (0, 1)},
}


def build_work(w):
    lay = quest_layout.Layout(w['key'])
    for q_, (x, y), _rewards in w['quests']:
        q_['deps'] = WORK_DEPS.get(q_['key'], [])
        lay.add(q_['key'], q_['kind'], x, y, deps=q_['deps']).stiff = True
    for name, (x, y), wd, _click in w['pictures']:
        size = PICTURES['works'][name]
        lay.images.append(dict(kind='work', name=name, x=x, y=y, w=wd, h=wd * size[1] / size[0]))
    for k, t in WORK_FLOW.get(w['key'], {}).items():
        d = math.hypot(*t)
        lay.tang[k] = (t[0] / d, t[1] / d)
    goal = next(q_['key'] for q_, _p, _r in w['quests'] if q_['kind'] == 'goal')
    lay.nodes[goal].fixed = True
    lay.focus = goal
    lay.relax()
    prefix = w['quests'][0][0]['key'].rsplit('/', 1)[0] + '/'
    lay.curves([(d, k) for k, deps in WORK_DEPS.items() for d in deps if k.startswith(prefix)])
    quests = [laid_out(lay, q_, rewards) for q_, _p, rewards in w['quests']]
    images = [top_banner('work_' + w['key'], 'work_' + w['key'], lay)]
    for i, im in enumerate(lay.images):
        x, y = lay.image_pos(im)
        d = {'id': qid('image/work_%s/%s' % (w['key'], im['name'])), 'x': D(x), 'y': D(y), 'width': D(im['w']),
             'height': D(im['h']), 'rotation': D(0), 'image': PIC + 'works/%s.png' % im['name'], 'color': 16777215,
             'alpha': 255, 'order': 1 + i, 'position_locked': True, 'click_action': w['pictures'][i][3]}
        lang('image', d['id'], 'title', 'Watch it built')
        images.append(d)
    LAYOUTS[w['filename']] = lay
    return chapter_nbt('work_' + w['key'], w['filename'], w['title'], w['subtitle'], w['icon'], 'works', w['order'],
                       quests, required_stage=w['stage'], tags=[w['stage'], 'works'], images=images,
                       extra={'autofocus_id': qid(goal)})


def build_tables():
    out = {}
    for order, (key, (title, _age, items)) in enumerate(REWARD_TABLES.items()):
        tid = qid('table/' + key)
        rewards = []
        for n, (i, c) in enumerate(items):
            r = {'id': qid('table/%s/%d' % (key, n)), 'item': {'count': 1, 'id': i}}
            if c > 1:
                r['count'] = c
            rewards.append(r)
        out[key] = {'id': tid, 'order_index': order, 'loot_size': 1, 'rewards': rewards, 'tags': ['age:' + _age]}
        lang('reward_table', tid, 'title', title)
    return out


def data_nbt():
    return {
        'version': FILE_VERSION,
        'default_reward_team': False,
        'default_consume_items': False,
        'default_autoclaim_rewards': 'disabled',
        'default_quest_shape': 'circle',
        # true: no "Quests" category in the recipe viewer (FTB XMod Compat JEI plugin, also reaches EMI); the
        # Chronicle on K is the only quest surface (Felix 2026-10-13, immersion)
        'default_quest_disable_jei': True,
        'emergency_items_cooldown': 300,
        'drop_loot_crates': False,
        'loot_crate_no_drop': {'passive': 4000, 'monster': 600, 'boss': 0},
        'disable_gui': False,
        'grid_scale': D(0.5),
        'pause_game': False,
        'lock_message': '',
        'progression_mode': 'flexible',
        'detection_delay': 20,
        'show_lock_icons': True,
        'drop_book_on_death': False,
        'hide_excluded_quests': False,
        'fallback_locale': 'en_us',
        'verify_on_load': False,
    }


HEADER = ('// Firmament Ages quest book. Generated by dev/gen_quests.py; check with dev/validate_quests.py.\n'
          '// Player text is in lang/en_us.snbt. Comment lines are dropped when FTB Quests rewrites this file.\n')


def write(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(HEADER + snbt(value) + '\n')


def main():
    LANG.clear()
    lang('file', '0000000000000001', 'title', 'The Chronicle')  # the quest book's one name (welcome screen, HUD, docs)
    gid = qid('group/ages')
    lang('chapter_group', gid, 'title', 'Ages')
    wid = qid('group/works')
    lang('chapter_group', wid, 'title', 'Great Works')
    tables = build_tables()
    LAYOUTS.clear()
    chapters = [('the_firmament', build_firmament())]
    chapters += [(c['filename'], build_age_chapter(c)) for c in AGE_CHAPTERS]
    chapters += [(w['filename'], build_work(w)) for w in WORKS]

    # wipe generated folders so renamed files do not linger
    for sub in ('chapters', 'reward_tables'):
        d = os.path.join(OUT, sub)
        if os.path.isdir(d):
            for f in os.listdir(d):
                if f.endswith('.snbt'):
                    os.remove(os.path.join(d, f))

    write(os.path.join(OUT, 'data.snbt'), data_nbt())
    write(os.path.join(OUT, 'chapter_groups.snbt'), {'chapter_groups': [{'id': gid}, {'id': wid}]})
    for name, ch in chapters:
        write(os.path.join(OUT, 'chapters', name + '.snbt'), ch)
    for key, t in tables.items():
        write(os.path.join(OUT, 'reward_tables', key + '.snbt'), t)
    write(os.path.join(OUT, 'lang', 'en_us.snbt'), LANG)
    write_chapter_shaders(chapters)
    for path, adv in proof_advancements().items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(adv, indent=2) + '\n')
    print('wrote %d chapters, %d reward tables, %d lang keys to %s' % (len(chapters), len(tables), len(LANG), OUT))


def write_chapter_shaders(chapters):
    """Certain Questing Additions 1.2.0.4: assets/certain_questing_additions/chapter_shaders.json maps
    Chapter.getCodeString() (16 hex digits, matched lower-case) to a core shader id; "default" covers the rest.
    The shaders are written by dev/gen_quest_art.py (kubejs/assets/firmages/shaders/core/sky_<key>.*)."""
    art_key = {'the_firmament': 'firmament'}
    art_key.update({c['filename']: c['stage'] for c in AGE_CHAPTERS})
    art_key.update({w['filename']: w['stage'] for w in WORKS})
    mapping = {}
    for name, ch in chapters:
        mapping[ch['id'].lower()] = 'firmages:sky_%s' % art_key[name]
    path = os.path.join(ROOT, 'kubejs', 'assets', 'certain_questing_additions', 'chapter_shaders.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'default': 'firmages:sky_firmament', 'chapters': mapping}, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    sys.exit(main())
