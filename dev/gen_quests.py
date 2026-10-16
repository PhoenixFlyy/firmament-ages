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
import os
import sys

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


def t_any_item(ids, title):
    """Either/or: every task optional -> the quest completes with any one of them."""
    return [t_item(i, optional=True) for i in ids], title


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
def Q(key, title, tasks, deps=(), desc=(), sub=None, kind='step', rewards=None, icon=None):
    return dict(key=key, title=title, tasks=tasks if isinstance(tasks, list) else [tasks], deps=list(deps),
                desc=list(desc), sub=sub, kind=kind, rewards=rewards, icon=icon)


XP = {'dawn': 10, 'age_0': 20, 'age_1': 35, 'age_2': 50, 'age_3': 65, 'age_4': 80, 'age_5': 100, 'age_6': 120,
      'age_7': 140, 'age_8': 160, 'age_9': 200}


def age_chapter(key, filename, title, subtitle, icon, stage, order, entry, strands, goal, sides, explains,
                supplies, comfort, ring=None, bridge=None):
    """strands: list of (name, [quests], keystone-quest). sides: list of (attach_key, [quests]).
    ring: "Raise the ring" quest (kind 'ring') for the Age's shrine ring.
    bridge: a required step between one keystone (its deps) and the goal, drawn on the main route; the goal depends
    on it as well as on every keystone."""
    return dict(key=key, filename=filename, title=title, subtitle=subtitle, icon=icon, stage=stage, order=order,
                group='ages', entry=entry, strands=strands, goal=goal, sides=sides, explains=explains,
                supplies=supplies, comfort=comfort, ring=ring, bridge=bridge)


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
                  'Sneak is Left Ctrl here; Left Shift sprints. Read "By Hand" first.',
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
                  'Four strands lead to the Steel Heart: Metallurgy, Power & Motion, Survival and Frontier.']),
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
            desc=['Magic wakes: spirits, source and alchemy. Until the Information Age it gives the best ore yield,',
                  'the first endless ore and the first item transport.',
                  '',
                  'Three strands lead to the Arcane Keystone: Spirits, Source and Alchemy.']),
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
    ],
    goal=Q('arcane/goal', 'Offer the Arcane Keystone at the Shrine',
           t_stage('age_4', 'Offer the Arcane Keystone at the shrine'), kind='goal', icon='firmages:arcane_keystone',
           sub='Goal of the Arcane Age: Caelum opens the Industrial Age',
           desc=['Bind the Arcane Keystone in the Djinni\'s crafting pentacle from every strand\'s proof: the',
                 'spirit attuned crystal, an iesnium ingot, the Wilden Tribute and the mercury catalyst, with a',
                 'Starlit Iron ingot and a source gem block.',
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
                  'Four strands lead to the Pressure Core: Coke & Steel, Power, Ore Line and Logistics & Backfill.']),
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
    ],
    goal=Q('industrial/goal', 'Offer the Pressure Core at the Shrine',
           t_stage('age_5', 'Offer the Pressure Core at the shrine'), kind='goal', icon='firmages:pressure_core',
           sub='Goal of the Industrial Age: Caelum opens the Electric Age',
           desc=['Craft the Pressure Core from two black steel double sheets, a heavy engineering block, two',
                 'Cloudsteel ingots, the Arcane Gearbox and a built-up steel chamber.',
                 '',
                 'Raise the Foundry Nave; the heart shows what is missing. Once 512 steel ingots have passed your Tally',
                 'Chutes in this Age, lay the Core on the new plinth, power the four electric lanterns and pray.',
                 '',
                 'Caelum keeps the Core and opens the Electric Age.']),
    sides=[
        ('industrial/steel/wood', [
            Q('industrial/side/airship', 'Airship', t_item('aeronautics:andesite_propeller'), kind='side',
              desc=['Craft a propeller, the first part of a ship that flies.']),
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
                  'Four strands lead to the Humming Core: Power Grid, Oil, Aluminium & Excavator and Circuits.']),
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
    ],
    goal=Q('electric/goal', 'Offer the Humming Core at the Shrine',
           t_stage('age_6', 'Offer the Humming Core at the shrine'), kind='goal', icon='firmages:humming_core',
           sub='Goal of the Electric Age: Caelum opens the Information Age',
           desc=['Fuse the Humming Core in the arc furnace: an HV capacitor, an Attuned Circuit, four aluminium plates,',
                 'an Aurora Steel ingot and a Nether Star.',
                 '',
                 'Raise the Tesla Crown. Once 2,048 aluminium ingots have passed your Tally Chutes in this Age, lay the',
                 'Core on the plinth, give the floodlights power and a signal, charge the capacitors with 1,000,000 FE',
                 'and pray.',
                 '',
                 'Caelum opens the Information Age.']),
    sides=[
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
                  'Four strands lead to the Data Matrix: Digital, Factory, Mob Data and The End.']),
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
    ],
    goal=Q('information/goal', 'Offer the Data Matrix at the Shrine',
           t_stage('age_7', 'Offer the Data Matrix at the shrine'), kind='goal', icon='firmages:data_matrix',
           sub='Goal of the Information Age: Caelum opens the Space Age',
           desc=['Craft the Data Matrix from two blue steel sheets, two Halo Steel ingots, three advanced control',
                 'circuits, a 64k storage component and a singularity from the matter condenser.',
                 '',
                 'Raise the Data Nave. Once 4,096 osmium ingots have passed your Tally Chutes in this Age, lay the',
                 'Matrix on the plinth, feed the heart 10,000,000 FE and pray, together if two of you are online.',
                 '',
                 'Caelum opens the Space Age.']),
    sides=[
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
                  'Four strands lead to the Star Chart: Atomic, Rocketry & Moon, Mars and Draconium.']),
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
    ],
    goal=Q('space/goal', 'Offer the Star Chart at the Shrine',
           t_stage('age_8', 'Offer the Star Chart at the shrine'), kind='goal', icon='firmages:star_chart',
           sub='Goal of the Space Age: Caelum opens the Quantum Age',
           desc=['Fuse the Star Chart at wyvern level: a polonium pellet in the middle, a desh plate, a Void Steel',
                 'ingot, two ostrum plates, a wyvern core and the flight recorder from Mars.',
                 '',
                 'Raise the Star Spire. Once 4,096 desh ingots have passed your Tally Chutes in this Age, lay the Chart',
                 'on the plinth and pray at night, under an open sky, without rain.',
                 '',
                 'Caelum opens the Quantum Age.']),
    sides=[
        ('space/atom/circuit', [
            Q('space/side/miner', 'Digital Miner', t_item('mekanism:digital_miner'), kind='side',
              desc=['The digital miner clears a whole area by filter. Keep it: a later Age needs it.']),
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
                  'Four strands lead to the Quantum Core: Fusion, Inner Planets, Awakened and Keystone.',
                  'The Quantum Core is the last offering at the shrine.']),
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
    ],
    goal=Q('quantum/goal', 'Offer the Quantum Core at the Shrine',
           t_stage('age_9', 'Offer the Quantum Core at the shrine'), kind='goal', icon='firmages:quantum_core',
           sub='Goal of the Quantum Age: Caelum opens the Singularity Age',
           desc=['Fuse the Quantum Core at draconic level: two antimatter pellets, a calorite plate, Quasar Alloy, two',
                 'ice shards and a subatomic alloy around the awakened core.',
                 '',
                 'Raise the Quantum Ring and set the Awakened Keystone back. Once 8,192 calorite ingots have passed',
                 'your Tally Chutes, lay the Core down, feed the heart 100,000,000 FE and pray at night under open sky,',
                 'together if two are online.',
                 '',
                 'Caelum opens the Singularity Age.']),
    sides=[
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
              desc=['Dissolution, washing and crystallizing give five times the metal.']),
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
                  'Three strands lead to the final battle: Chaos, Singularity and Stargate.']),
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

# ---------------------------------------------------------------- Welcome
# Written in-world (Felix, 2026-10-13: full immersion). No mod names, no recipe viewer, no talk of locks or stages;
# the Ages, the shrine and Caelum, the Chronicle on K, the sign in the corner.
WELCOME = [
    Q('welcome/start', 'Welcome to Firmament Ages', t_check(), kind='info', icon='tfc:firestarter',
      sub='Read me first',
      desc=['You wake beneath a sky that watches. Your hands are empty.',
            '',
            'This is the Chronicle, the one record of your fellowship.',
            '',
            'The world is lived in Ages, from Dawn to the Singularity Age. What a later Age holds does not exist yet. Each Age ends with an offering at the shrine of Caelum, the sky itself, and its chapter appears here when it dawns.']),
    Q('welcome/book', 'Reading a Chapter', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:compass',
      desc=['Each chapter has one entry, three or four strands and one goal.',
            '- A strand ends in a keystone, the large engraved hexagon.',
            '- The goal, the gilded star, needs every keystone.',
            '- Round ones are side paths with a small gift.',
            '- The diamond marks the Age\'s shrine ring.',
            '- Gears are notes. Tick them once read.']),
    Q('welcome/team', 'One Fellowship', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:player_head',
      desc=['Everybody on this server is one fellowship. The Ages are shared by all of you.',
            'Split the strands between you: one friend smiths, one prospects, one builds.',
            '',
            'A keystone\'s supplies go to whoever claims them first. The gift of a side path and all experience come to each of you.']),
    Q('welcome/sign', 'The Sign in the Corner', t_check(), deps=['welcome/book'], kind='info',
      icon='tfc:firestarter',  # the sign shows the Age's offering; in Dawn the firestarter (oak sign is Iron Age)
      desc=['Top left, a small sign names your Age and its offering. It fades after a few breaths. Hold H to see it again.']),
]

# ---------------------------------------------------------------- The Firmament
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
          'side': ('ember', 0.85), 'explain': ('gear', 0.0), 'info': ('', 0.0), 'ring': ('diamond', 1.25)}

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
    if quest['kind'] in ('side', 'explain', 'ring'):
        d['optional'] = True
    if quest['icon']:
        d['icon'] = {'id': quest['icon']}
    if rewards:
        d['rewards'] = build_rewards(quest, rewards)
    if quest['kind'] in ('entry', 'keystone', 'goal', 'side', 'explain', 'ring'):
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


def build_age_chapter(ch):
    age = ch['stage']
    xp = XP[age]
    quests = []
    entry = ch['entry']
    quests.append(quest_nbt(entry, 0, 0, [r_xp(xp)]))
    n = len(ch['strands'])
    pos = {entry['key']: (0.0, 0.0)}
    key_x = 0
    rows = []
    for i, (name, steps, keystone) in enumerate(ch['strands']):
        y = (i - (n - 1) / 2.0) * 2.5
        rows.append(y)
        prev = entry['key']
        x = 2.5
        for s in steps:
            s['deps'] = s['deps'] or [prev]
            quests.append(quest_nbt(s, x, y, [r_xp(xp)]))
            pos[s['key']] = (x, y)
            prev = s['key']
            x += 1.75
        keystone['deps'] = keystone['deps'] or [prev]
        x += 0.25
        quests.append(quest_nbt(keystone, x, y, [r_xp(xp * 3), r_random(ch['supplies'])]))
        pos[keystone['key']] = (x, y)
        key_x = max(key_x, x)
    goal = ch['goal']
    goal['deps'] = [k[2]['key'] for k in ch['strands']]
    bridge = ch.get('bridge')
    if bridge:
        # between its keystone and the goal, halfway in x and y
        bx, by = pos[bridge['deps'][0]]
        quests.append(quest_nbt(bridge, (bx + key_x + 3.25) / 2.0, by / 2.0, [r_xp(xp)]))
        goal['deps'].append(bridge['key'])
    goal_rewards = [r_xp(xp * 10)]
    if ch['key'] in GOAL_GRANTS:
        goal_rewards.append(r_stage(GOAL_GRANTS[ch['key']]))
    quests.append(quest_nbt(goal, key_x + 3.25, 0, goal_rewards))
    # side missions above the top row, explanations below the bottom row
    top, bottom = min(rows), max(rows)
    cursor = -1e9
    for attach, line in sorted(ch['sides'], key=lambda sd: pos[sd[0]][0]):
        ax = pos[attach][0]
        x = max(ax, cursor)
        prev = attach
        for j, s in enumerate(line):
            s['deps'] = s['deps'] or [prev]
            last = j == len(line) - 1
            quests.append(quest_nbt(s, x, top - 2.5, [r_xp(xp), r_choice(ch['comfort'])] if last else [r_xp(xp)]))
            prev = s['key']
            x += 1.5
        cursor = x + 0.5
    for j, e in enumerate(ch['explains']):
        e['deps'] = e['deps'] or [entry['key']]
        quests.append(quest_nbt(e, j * 1.5, bottom + 2.5, [], extra={'hide_dependency_lines': True}))
    # optional "Raise the ring" observation below the goal (shrine Ages from the Arcane Age on)
    if ch.get('ring'):
        r = ch['ring']
        r['deps'] = r['deps'] or [entry['key']]
        quests.append(quest_nbt(r, key_x + 3.25, bottom + 2.5, [r_xp(xp * 2)], extra={'hide_dependency_lines': True}))
    # Age banner above everything (side missions sit at top - 2.5), centred on the chapter's width
    banner = banner_image(ch['key'], age, (key_x + 3.25) / 2.0, top - 2.5 - BANNER_H - 0.75)
    return chapter_nbt(ch['key'], ch['filename'], ch['title'], ch['subtitle'], ch['icon'], ch['group'], ch['order'],
                       quests, required_stage=age, tags=[age], images=[banner])


def build_welcome():
    quests = []
    layout = {'welcome/start': (0, 0), 'welcome/book': (2, -1.0), 'welcome/team': (2, 1.0), 'welcome/sign': (4, -1.0)}
    for w in WELCOME:
        x, y = layout[w['key']]
        quests.append(quest_nbt(w, x, y, [r_xp(5)]))
    return chapter_nbt('welcome', 'welcome', 'Welcome', ['Read this first.'],
                       'tfc:firestarter', None, 0, quests, tags=['welcome'],
                       images=[banner_image('welcome', 'welcome', 2.0, -1.0 - BANNER_H - 0.75)])


def build_firmament():
    quests = []
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
                    ['The offering of %s. Its details appear when the Age before it is done.' % name])
        q_ = Q('firmament/' + key, title, t_stage(stage, task), deps=[prev] if prev else [],
               kind='goal' if final else 'keystone', icon=icon, sub=sub, desc=desc)
        # Decision A (2026-10-14): each offering appears when its Age dawns (its predecessor's Age is reached). The
        # first spark and the end of the road stay in view from the start.
        extra = {'hide_until_deps_complete': True} if prev and not final else None
        quests.append(quest_nbt(q_, i * 2.0, 0 if i % 2 == 0 else 1.0, [], extra=extra))
        prev = q_['key']
    return chapter_nbt('firmament', 'the_firmament', 'The Firmament',
                       ['Nine offerings and a gate. The whole road, from the first spark to the last.'],
                       'tfc:firestarter', None, 1, quests,
                       extra={'progression_mode': 'linear', 'hide_quest_details_until_startable': True},
                       tags=['firmament'],
                       images=[banner_image('firmament', 'firmament', (len(FIRMAMENT) - 1), -BANNER_H - 1.0)])


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
    tables = build_tables()
    chapters = [('welcome', build_welcome()), ('the_firmament', build_firmament())]
    chapters += [(c['filename'], build_age_chapter(c)) for c in AGE_CHAPTERS]

    # wipe generated folders so renamed files do not linger
    for sub in ('chapters', 'reward_tables'):
        d = os.path.join(OUT, sub)
        if os.path.isdir(d):
            for f in os.listdir(d):
                if f.endswith('.snbt'):
                    os.remove(os.path.join(d, f))

    write(os.path.join(OUT, 'data.snbt'), data_nbt())
    write(os.path.join(OUT, 'chapter_groups.snbt'), {'chapter_groups': [{'id': gid}]})
    for name, ch in chapters:
        write(os.path.join(OUT, 'chapters', name + '.snbt'), ch)
    for key, t in tables.items():
        write(os.path.join(OUT, 'reward_tables', key + '.snbt'), t)
    write(os.path.join(OUT, 'lang', 'en_us.snbt'), LANG)
    write_chapter_shaders(chapters)
    print('wrote %d chapters, %d reward tables, %d lang keys to %s' % (len(chapters), len(tables), len(LANG), OUT))


def write_chapter_shaders(chapters):
    """Certain Questing Additions 1.2.0.4: assets/certain_questing_additions/chapter_shaders.json maps
    Chapter.getCodeString() (16 hex digits, matched lower-case) to a core shader id; "default" covers the rest.
    The shaders are written by dev/gen_quest_art.py (kubejs/assets/firmages/shaders/core/sky_<key>.*)."""
    art_key = {'welcome': 'welcome', 'the_firmament': 'firmament'}
    art_key.update({c['filename']: c['stage'] for c in AGE_CHAPTERS})
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
