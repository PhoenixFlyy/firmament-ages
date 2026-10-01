#!/usr/bin/env python3
"""Firmament Ages - generate the FTB Quests book (FTB Quests 2101.1.36, NeoForge 1.21.1).

Writes config/ftbquests/quests/: data.snbt, chapter_groups.snbt, chapters/*.snbt, reward_tables/*.snbt and
lang/en_us.snbt. All player-facing text lives in lang/en_us.snbt (FTB Quests 2101 keeps titles, subtitles and
descriptions in per-locale translation tables keyed "<type>.<16-hex id>.<field>", see TranslationManager).

IDs are derived from stable keys (sha1), so re-running the generator keeps every quest id and therefore every
team's saved progress. The generator is the source of the book until someone edits it in game; after an
in-game edit, the SNBT files are the source and this script must not be re-run over them (see
dev/quests-notes.md). Check the output with dev/validate_quests.py.

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


def t_kill(entity=None, tag=None, value=1, title=None):
    d = {'type': 'kill', 'entity': entity or 'minecraft:zombie', 'value': L(value)}
    if tag:
        d['entityTypeTag'] = tag
    return d, title


def t_observe(target, kind='block', timer=0, title=None):
    return {'type': 'observation', 'observation_type': kind, 'to_observe': target, 'timer': L(timer)}, title


def t_dim(dim, title=None):
    return {'type': 'dimension', 'dimension': dim}, title


def t_stat(stat, value, title=None):
    return {'type': 'stat', 'stat': stat, 'value': value}, title


def r_xp(n):
    return {'type': 'xp', 'xp': n}


def r_random(table_key):
    return {'type': 'random', 'table': table_key, 'team_reward': True}


def r_choice(table_key):
    return {'type': 'choice', 'table': table_key, 'team_reward': True}


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
        ('tfc:metal/sheet/wrought_iron', 2), ('create:electron_tube', 2), ('create:fluid_pipe', 8)]),
    'iron_comfort': ('Iron Age Comforts', 'age_2', [
        ('twilightforest:charm_of_life_1', 1), ('tfc:metal/shield/wrought_iron', 1),
        ('toms_storage:storage_terminal', 1), ('twilightforest:magic_map_focus', 1)]),
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
        ('tfc:metal/ingot/black_steel', 1)]),
    'industrial_comfort': ('Industrial Age Comforts', 'age_4', [
        ('immersiveengineering:hammer', 1), ('immersiveengineering:wirecutter', 1), ('create_jetpack:jetpack', 1),
        ('immersiveengineering:electric_lantern', 4)]),
    'electric_supplies': ('Electric Age Supplies', 'age_5', [
        ('immersiveengineering:ingot_aluminum', 4), ('immersiveengineering:plate_aluminum', 4),
        ('immersiveengineering:wirecoil_electrum', 4), ('immersiveengineering:wirecoil_steel', 4),
        ('tfc:metal/ingot/red_steel', 1), ('immersivepetroleum:lubricant_bucket', 1)]),
    'electric_comfort': ('Electric Age Comforts', 'age_5', [
        ('immersivepetroleum:speedboat', 1), ('immersivepetroleum:oil_can', 1), ('create_hypertube:hypertube', 16),
        ('immersiveengineering:capacitor_mv', 1)]),
    'information_supplies': ('Information Age Supplies', 'age_6', [
        ('mekanism:ingot_osmium', 8), ('mekanism:basic_control_circuit', 4), ('ae2:certus_quartz_crystal', 8),
        ('ae2:fluix_crystal', 4), ('ae2:silicon', 4), ('mekanism:hdpe_sheet', 4), ('tfc:metal/ingot/blue_steel', 1)]),
    'information_comfort': ('Information Age Comforts', 'age_6', [
        ('mekanism:jetpack', 1), ('mekanism:free_runners', 1), ('ae2:wireless_terminal', 1),
        ('mekanism:configurator', 1)]),
    'space_supplies': ('Space Age Supplies', 'age_7', [
        ('ad_astra:desh_ingot', 4), ('ad_astra:ostrum_ingot', 2), ('ad_astra:desh_plate', 8),
        ('draconicevolution:draconium_ingot', 4), ('ad_astra:ostrum_plate', 4), ('ad_astra:fuel_bucket', 1)]),
    'space_comfort': ('Space Age Comforts', 'age_7', [
        ('ad_astra:tier_1_rover', 1), ('ad_astra:zip_gun', 1), ('mekanism:teleporter', 1),
        ('draconicevolution:wyvern_capacitor', 1)]),
    'quantum_supplies': ('Quantum Age Supplies', 'age_8', [
        ('ad_astra:calorite_ingot', 4), ('draconicevolution:awakened_draconium_ingot', 2), ('ad_astra:ice_shard', 2),
        ('evolvedmekanism:alloy_hypercharged', 2), ('mekanism:sps_casing', 4)]),
    'quantum_comfort': ('Quantum Age Comforts', 'age_8', [
        ('draconicevolution:draconic_capacitor', 1), ('evolvedmekanism:overclocked_energy_cube', 1),
        ('evolvedmekanism:overclocked_control_circuit', 4), ('draconicevolution:draconic_pickaxe', 1)]),
    'singularity_supplies': ('Singularity Age Supplies', 'age_9', [
        ('draconicevolution:small_chaos_frag', 4), ('sgjourney:raw_naquadah', 8), ('sgjourney:naquadah', 4),
        ('draconicevolution:chaos_shard', 1)]),
    'singularity_comfort': ('Singularity Age Comforts', 'age_9', [
        ('draconicevolution:chaotic_capacitor', 1), ('sgjourney:gdo', 1), ('sgjourney:naquadah_iris', 1)]),
}


def table_long_id(key):
    return int(qid('table/' + key), 16)


# ------------------------------------------------------------------------------------------------ content
# Quest spec: dict(key, title, sub, desc[list], tasks[list of (task, title) or ([tasks], title)], deps[keys],
#                  rewards[list], kind in entry|step|keystone|goal|side|explain|info, icon)
def Q(key, title, tasks, deps=(), desc=(), sub=None, kind='step', rewards=None, icon=None):
    return dict(key=key, title=title, tasks=tasks if isinstance(tasks, list) else [tasks], deps=list(deps),
                desc=list(desc), sub=sub, kind=kind, rewards=rewards, icon=icon)


XP = {'dawn': 10, 'age_0': 20, 'age_1': 35, 'age_2': 50, 'age_3': 65, 'age_4': 80, 'age_5': 100, 'age_6': 120,
      'age_7': 140, 'age_8': 160, 'age_9': 200}


def age_chapter(key, filename, title, subtitle, icon, stage, order, entry, strands, goal, sides, explains,
                supplies, comfort, ring=None):
    """strands: list of (name, [quests], keystone-quest). sides: list of (attach_key, [quests]).
    ring: optional "Raise the ring" quest (kind 'ring') for the Age's shrine ring."""
    return dict(key=key, filename=filename, title=title, subtitle=subtitle, icon=icon, stage=stage, order=order,
                group='ages', entry=entry, strands=strands, goal=goal, sides=sides, explains=explains,
                supplies=supplies, comfort=comfort, ring=ring)


def ring_quest(chapter_key, age_index, ring_name, icon, desc):
    """Optional observation of the finished ring: the heart reports ready=true while the current tier's rings stand,
    and awakened=N while age_N is the highest Age (SPEC section 7.8), so the quest only ticks in its own Age."""
    return Q('%s/ring' % chapter_key, 'Raise %s' % ring_name,
             t_observe('firmages:shrine_heart[awakened=%d,ready=true]' % age_index, kind='block_state',
                       title='Look at the heart with %s complete' % ring_name),
             kind='ring', icon=icon, sub='Optional: the shrine ring of this Age', desc=desc)


# ---------------------------------------------------------------- Dawn
DAWN = age_chapter(
    'dawn', 'dawn', 'Dawn', ['Prologue: stone, straw and the first spark.'], 'tfc:firestarter', 'dawn', 0,
    entry=Q('dawn/entry', 'Welcome to the Wild', t_stage('dawn'), kind='entry', icon='tfc:rock/loose/granite',
            sub='The prologue before the first Age',
            desc=['You wake up with nothing. No pickaxe, no clay, no fire.',
                  '',
                  'Dawn has three strands: Stone, Shelter and Hunt. Split them between you.',
                  'When all three keystones are done, light the first fire. That is the goal of Dawn,',
                  'and it opens the Stone Age for the whole team.']),
    strands=[
        ('Stone', [
            Q('dawn/stone/rock', 'Just a Rock', t_adv('tfc:story/find_rock'),
              desc=['Pick up a loose rock from the ground. Rocks are knapped by using them in hand.'],
              icon='tfc:rock/loose/granite'),
            Q('dawn/stone/sticks', 'A Bundle of Sticks', t_item('minecraft:stick', 8),
              desc=['Sticks lie on the forest floor and drop from leaves. You need them for every tool shaft.']),
            Q('dawn/stone/tool', 'Paleolithic!', t_adv('tfc:story/stone_age'),
              desc=['Knap a tool head from two rocks and put it on a stick in the crafting grid.',
                    'An axe comes first. A knife, a hammer and a shovel follow the same way.'],
              icon='tfc:stone/axe/sedimentary'),
        ], Q('dawn/stone/key', 'Stone Kit', t_adv('tfc:story/logging', 'Fell a tree with a stone axe'),
             kind='keystone', icon='tfc:stone/axe_head/igneous_extrusive',
             desc=['Fell a tree with your stone axe. With axe, knife, hammer and shovel you have a full stone kit.'])),
        ('Shelter', [
            Q('dawn/shelter/straw', 'Grasping at Straws', t_adv('tfc:story/get_straw'),
              desc=['Cut tall grass with a stone knife to get straw.'], icon='tfc:straw'),
            Q('dawn/shelter/thatch', 'Thatch', t_item('tfc:thatch', 8),
              desc=['Four straw make one block of thatch. It is the only building block of Dawn.']),
        ], Q('dawn/shelter/key', 'Shelter', t_observe('tfc:thatch_bed', title='Look at a finished thatch bed'),
             kind='keystone', icon='tfc:thatch_bed',
             desc=['Place two thatch blocks side by side and use a large raw hide on them.',
                   'The thatch bed sets your spawn point. You cannot sleep through the night in it.',
                   'Large hides come from large animals: the Hunt strand gets them.'])),
        ('Hunt', [
            Q('dawn/hunt/javelin', 'A Stone Javelin',
              t_any_item(['tfc:stone/javelin/igneous_extrusive', 'tfc:stone/javelin/igneous_intrusive',
                          'tfc:stone/javelin/metamorphic', 'tfc:stone/javelin/sedimentary'], 'Any stone javelin'),
              desc=['Knap a javelin head and shaft it. Throw it or stab with it.'],
              icon='tfc:stone/javelin/sedimentary'),
            Q('dawn/hunt/kill', 'Hunter', t_kill(tag='tfc:animals', value=1, title='Kill any TFC animal'),
              desc=['Hunt an animal. Large prey drops large hides; carry a knife to butcher it.']),
        ], Q('dawn/hunt/key', 'Raw Hide',
             t_any_item(['tfc:small_raw_hide', 'tfc:medium_raw_hide', 'tfc:large_raw_hide'], 'Any raw hide'),
             kind='keystone', icon='tfc:large_raw_hide',
             desc=['Bring home a raw hide. A large one makes the thatch bed.'])),
    ],
    goal=Q('dawn/goal', 'The First Spark',
           [t_item('tfc:firestarter', title='A firestarter'), t_adv('tfc:story/firepit', 'Light a firepit')],
           kind='goal', icon='tfc:firestarter', sub='Goal of Dawn: grants the Stone Age',
           desc=['Craft a firestarter from two sticks. Throw a log, three sticks and some straw on one block',
                 'and hold use on it with the firestarter until the firepit lights.',
                 '',
                 'Fire opens the Stone Age for the whole team: clay, pit kilns and the first copper.']),
    sides=[
        ('dawn/stone/rock', [
            Q('dawn/side/nugget', 'A Weird Rock', t_adv('tfc:world/nugget'), kind='side',
              desc=['Some rocks on the surface are ore. Remember where you found them.'],
              icon='tfc:ore/small_native_copper'),
        ]),
        ('dawn/hunt/javelin', [
            Q('dawn/side/berries', 'Berries and Herbs',
              t_any_item(['tfc:food/blackberry', 'tfc:food/raspberry', 'tfc:food/blueberry',
                          'tfc:food/elderberry', 'tfc:food/gooseberry', 'tfc:food/strawberry',
                          'tfc:food/cloudberry', 'tfc:food/snowberry', 'tfc:food/bunchberry',
                          'tfc:food/cranberry', 'tfc:food/wintergreen_berry'], 'Any wild berry'),
              kind='side', icon='tfc:food/blueberry',
              desc=['Gather wild berries. Fruit is a nutrition group of its own.']),
        ]),
    ],
    explains=[
        Q('dawn/info/rules', 'What Dawn Locks', t_check(), kind='explain', icon='minecraft:barrier',
          desc=['In Dawn you cannot dig clay, build pit kilns or use metal.',
                'The crafting grid only makes stone tool shafts, the firestarter, straw, thatch, sticks and rope.',
                'Everything else opens with the Stone Age.']),
    ],
    supplies='dawn_supplies', comfort='dawn_supplies')

# ---------------------------------------------------------------- Stone Age
STONE = age_chapter(
    'stone', 'stone_age', 'Age 0: Stone Age', ['Clay, pit kilns and the first cast copper.'], 'firmages:hearthstone',
    'age_0', 1,
    entry=Q('stone/entry', 'Welcome to the Stone Age', t_stage('age_0'), kind='entry', icon='tfc:ceramic/vessel',
            sub='Age 0',
            desc=['Fire is tamed. Clay is yours now, and with it pottery, molds and copper.',
                  '',
                  'Four strands lead to the Hearthstone: Fire & Clay, First Metal, Camp, and Hearth & Shrine.',
                  'The last one raises the shrine where the Hearthstone is offered to Caelum.']),
    strands=[
        ('Fire & Clay', [
            Q('stone/clay/find', 'Locating Clay', t_adv('tfc:story/find_clay'),
              desc=['Clay grows under grass near water. Look for the clay indicator plants.'],
              icon='minecraft:clay_ball'),
            Q('stone/clay/knap', 'Clay Forming', t_adv('tfc:story/knap_clay'),
              desc=['Knap five clay into a vessel, a jug, a bowl or a mold.']),
            Q('stone/clay/kiln', 'Potter', t_adv('tfc:story/pit_kiln'),
              desc=['Place unfired pottery, cover it with straw and logs and set it on fire: a pit kiln.']),
            Q('stone/clay/vessel', 'Small Vessel', t_item('tfc:ceramic/vessel'),
              desc=['The fired small vessel melts ore when it sits in a fire or a pit kiln.']),
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
            Q('stone/metal/charcoal', 'A Better Fuel', t_adv('tfc:story/charcoal'),
              desc=['Stack logs in a log pile, cover it with earth and light it: a charcoal pit.',
                    'Charcoal burns hot enough for copper.'], icon='minecraft:charcoal'),
            Q('stone/metal/ingot', 'Copper Ingot', t_item('tfc:metal/ingot/copper', 2),
              desc=['Fill the small vessel with copper ore, heat it until the copper melts,',
                    'then pour it into the ingot mold.']),
            Q('stone/metal/head', 'Pickaxe Head', t_item('tfc:metal/pickaxe_head/copper'),
              desc=['Pour copper into the pickaxe head mold.']),
        ], Q('stone/metal/key', 'Copper Pickaxe', t_item('tfc:metal/pickaxe/copper'), kind='keystone',
             desc=['Put the head on a stick. Your first pickaxe: now you can mine.'])),
        ('Camp', [
            Q('stone/camp/seeds', 'Gatherer', t_adv('tfc:world/seeds'),
              desc=['Break a wild crop to get its seeds.'], icon='tfc:seeds/wheat'),
            Q('stone/camp/field', 'First Field', t_observe('tfc:farmlands', kind='block_tag',
                                                           title='Look at your farmland'),
              desc=['Till soil with a hoe and plant the seeds. Crops need water and time.'],
              icon='tfc:seeds/wheat'),
            Q('stone/camp/pot', 'Pot Head', t_adv('tfc:story/pot'),
              desc=['Put a fired ceramic pot on the firepit and cook a soup.'], icon='tfc:ceramic/pot'),
            Q('stone/camp/jug', 'Fresh Water', t_item('tfc:ceramic/jug'),
              desc=['A fired jug carries fresh water. Drink from it when you are away from rivers.']),
        ], Q('stone/camp/key', 'Stocked Camp', t_item('tfc:ceramic/large_vessel'), kind='keystone',
             desc=['A large vessel stores food. Sealed, it slows decay. Food decay is real in this pack.'])),
        ('Hearth & Shrine', [
            Q('stone/hearth/unfired', 'Unfired Hearth Idol', t_item('firmages:unfired_hearth_idol'),
              desc=['Knap clay into the shape of the hearth idol.']),
            Q('stone/hearth/idol', 'Hearth Idol', t_item('firmages:hearth_idol'),
              desc=['Fire the idol in a pit kiln, like any other pottery. It becomes the Hearthstone.']),
            Q('stone/shrine/heart', 'The Shrine Heart',
              t_observe('firmages:shrine_heart', title='Look at your placed Shrine Heart'), icon='firmages:shrine_heart',
              desc=['Fire a second Hearth Idol and set it in three cobblestone under a charcoal:',
                    'that is the Shrine Heart.',
                    'Place it where your team wants its shrine. There is only one shrine in the world,',
                    'and every later Age adds a ring around it, so leave room: about 21 by 21 blocks.']),
        ], Q('stone/shrine/key', 'Raise the Shrine',
             t_observe('firmages:shrine_heart[ready=true]', kind='block_state',
                       title='Look at the heart of the finished Hearth Circle'),
             kind='keystone', icon='firmages:offering_plinth',
             desc=['Build the Hearth Circle around the heart: eight cobblestone of any rock around it, four log',
                   'posts two blocks high on the corners of a 5x5 square with thatch on top, and an Offering Plinth',
                   '(four cobblestone) two blocks from the heart.',
                   'Use the heart with an empty hand to see the missing blocks in place.',
                   '',
                   'When the circle is complete, the heart glows. Use it again: it names the offering Caelum asks for.'])),
    ],
    goal=Q('stone/goal', 'Offer the Hearthstone at the Shrine',
           t_stage('age_1', 'Offer the Hearthstone at the shrine'), kind='goal', icon='firmages:hearthstone',
           sub='Goal of the Stone Age: Caelum opens the Bronze Age',
           desc=['Craft the Hearthstone: the fired hearth idol, copper ingots and charcoal.',
                 '',
                 'Lay it on the plinth of the Hearth Circle (use it on the plinth or on the heart).',
                 'Kindle the heart with a firestarter. Then sneak and hold use on the heart with an empty hand',
                 'and pray until Caelum answers. Friends who pray with you make it faster.',
                 '',
                 'Caelum keeps the Hearthstone as a relic and opens the Bronze Age for the whole team.']),
    sides=[
        ('stone/clay/kiln', [
            Q('stone/side/candle', 'Tallow and Candles', t_item('tfc:candle', 4), kind='side',
              desc=['Render fat into tallow and make candles. Light without fire hazards.']),
        ]),
        ('stone/metal/ore', [
            Q('stone/side/lantern', 'Lights in the Swamp',
              t_observe('mowziesmobs:lantern', kind='entity_type', title='Look at a Lantern'), kind='side',
              icon='mowziesmobs:glowing_jelly',
              desc=['Lanterns float over swamps at night. Look, then decide whether to hunt them.']),
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
          desc=['Hot items glow. The tooltip shows the temperature; metal melts at its own heat.']),
        Q('stone/info/cavein', 'Cave-ins', t_check(), kind='explain', icon='tfc:rock/loose/granite',
          desc=['Mining raw stone can collapse the ceiling. Support beams come with the Bronze Age.']),
    ],
    supplies='stone_supplies', comfort='stone_comfort')

# ---------------------------------------------------------------- Bronze Age
BRONZE = age_chapter(
    'bronze', 'bronze_age', 'Age 1: Bronze Age', ['The first alloys. New veins can be found.'], 'firmages:sky_disc',
    'age_1', 2,
    entry=Q('bronze/entry', 'Welcome to the Bronze Age', t_stage('age_1'), kind='entry',
            icon='tfc:metal/ingot/bronze', sub='Age 1',
            desc=['Tin and zinc veins are visible now. Anvils, alloys and the first Create machines follow.',
                  '',
                  'Four strands lead to the Sky Disc: Smithing, Prospecting, Alloys and Kinetics.']),
    strands=[
        ('Smithing', [
            Q('bronze/smith/forge', 'Forging', t_adv('tfc:story/forge'),
              desc=['Build a charcoal forge: charcoal on the ground, surrounded by stone.'], icon='minecraft:charcoal'),
            Q('bronze/smith/stone_anvil', 'Hammer Time', t_adv('tfc:story/stone_anvil'),
              desc=['Use a stone hammer on raw igneous rock to make a stone anvil.']),
            Q('bronze/smith/quern', 'The Grind', t_adv('tfc:story/quern'),
              desc=['Craft a quern and a handstone.'], icon='tfc:quern'),
            Q('bronze/smith/flux', 'In Flux', t_item('tfc:powder/flux', 4),
              desc=['Grind borax, lime or other flux stones in the quern. Welding needs flux.']),
            Q('bronze/smith/weld', 'Double Trouble', t_adv('tfc:story/welding'),
              desc=['Weld two hot ingots into a double ingot on the stone anvil.'],
              icon='tfc:metal/double_ingot/copper'),
        ], Q('bronze/smith/key', 'Copper Anvil', t_item('tfc:metal/anvil/copper'), kind='keystone',
             desc=['Weld copper double ingots into a copper anvil. Tools and sheets are smithed on it.'])),
        ('Prospecting', [
            Q('bronze/prosp/tin', 'Tin on the Surface', t_item('tfc:ore/small_cassiterite'),
              desc=['Cassiterite pieces on the ground point to a tin vein below.']),
            Q('bronze/prosp/zinc', 'Zinc on the Surface', t_item('tfc:ore/small_sphalerite'),
              desc=['Sphalerite pieces point to a zinc vein. Create needs zinc.']),
            Q('bronze/prosp/propick', 'Prospector', t_adv('tfc:story/propick'),
              desc=['Smith a prospector\'s pick on an anvil (Smithing strand). It finds ore through stone.'],
              icon='tfc:metal/propick/copper'),
            Q('bronze/prosp/mineral', 'Mineral Prospector',
              t_any_item(['precisionprospecting:metal/mineral_prospector/copper',
                          'precisionprospecting:metal/mineral_prospector/bronze',
                          'precisionprospecting:metal/mineral_prospector/bismuth_bronze',
                          'precisionprospecting:metal/mineral_prospector/black_bronze'], 'Any mineral prospector'),
              icon='precisionprospecting:metal/mineral_prospector/copper',
              desc=['Precision Prospecting shows the direction and size of a vein.']),
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
            Q('bronze/alloy/tool', 'The Bronze Age', t_adv('tfc:story/bronze_age'),
              desc=['Smith any bronze tool.'], icon='tfc:metal/axe/bronze'),
        ], Q('bronze/alloy/key', 'Bronze Anvil',
             t_any_item(['tfc:metal/anvil/bronze', 'tfc:metal/anvil/bismuth_bronze', 'tfc:metal/anvil/black_bronze'],
                        'Any bronze anvil'),
             kind='keystone', icon='tfc:metal/anvil/bronze',
             desc=['A bronze anvil forges bronze double sheets: the Sky Disc needs one.'])),
        ('Kinetics', [
            Q('bronze/kin/nugget', 'Zinc Nuggets', t_item('create:zinc_nugget', 9),
              desc=['Split a TFC zinc ingot into nine zinc nuggets in the crafting grid.']),
            Q('bronze/kin/alloy', 'Andesite Alloy', t_item('create:andesite_alloy', 4),
              desc=['Andesite alloy is TFC andesite cobble and zinc nuggets. It is the base of Create.']),
            Q('bronze/kin/wheel', 'Water Wheel', t_item('create:water_wheel'),
              desc=['A water wheel in flowing water gives rotational force.']),
            Q('bronze/kin/shafts', 'Shafts and Cogs',
              [t_item('create:shaft', 8, title='Shafts'), t_item('create:cogwheel', 4, title='Cogwheels')],
              icon='create:cogwheel', desc=['Carry the rotation to your machines.']),
            Q('bronze/kin/saw', 'Mechanical Saw', t_item('create:mechanical_saw'),
              desc=['The saw cuts TFC logs into lumber.']),
        ], Q('bronze/kin/key', 'First Mill',
             [t_observe('create:millstone', title='Look at a placed millstone'),
              t_observe('create:water_wheel', title='Look at a placed water wheel')],
             kind='keystone', icon='create:millstone',
             desc=['Power a millstone from a water wheel. It grinds what the quern grinds by hand.'])),
    ],
    goal=Q('bronze/goal', 'Offer the Sky Disc at the Shrine', t_stage('age_2', 'Offer the Sky Disc at the shrine'),
           kind='goal', icon='firmages:sky_disc', sub='Goal of the Bronze Age: Caelum opens the Iron Age',
           desc=['Craft the Sky Disc: a bronze double sheet from the bronze anvil and gold sheets.',
                 '',
                 'Grow the shrine by its second ring, the Bronze Sanctum: rock bricks, four bronze blocks and',
                 'a bronze bell. Use the heart with an empty hand to see the missing blocks.',
                 'Lay the Sky Disc on the new plinth, ring the bell and pray at the heart.',
                 '',
                 'Caelum keeps the Sky Disc as a relic and opens the Iron Age for the whole team.']),
    sides=[
        ('bronze/smith/weld', [
            Q('bronze/side/backpack', 'Pack Mule', t_item('sophisticatedbackpacks:backpack'), kind='side',
              desc=['A backpack carries a second inventory.']),
        ]),
        ('bronze/alloy/crucible', [
            Q('bronze/side/kitchen', 'Kitchen', t_item('firmalife:drying_mat'), kind='side',
              desc=['Firmalife adds ovens, cheese, drying and more. A drying mat is the first step.']),
        ]),
        ('bronze/kin/alloy', [
            Q('bronze/side/grottol', 'Grottol Hunt', t_kill('mowziesmobs:grottol', value=1), kind='side',
              icon='mowziesmobs:captured_grottol',
              desc=['Grottols hide in caves and flee. Catch one if you can.']),
        ]),
        ('bronze/prosp/tin', [
            Q('bronze/side/totem', 'Totemic', t_item('totemic:flute'), kind='side',
              desc=['Carve a flute. Totemic ceremonies and totem poles give the camp some flair.']),
        ]),
    ],
    explains=[
        Q('bronze/info/heat', 'Working Heat', t_check(), kind='explain', icon='tfc:metal/double_ingot/copper',
          desc=['Metal can only be worked or welded while it is hot enough. Check the tooltip color.']),
        Q('bronze/info/create', 'Create in the Bronze Age', t_check(), kind='explain', icon='create:andesite_alloy',
          desc=['Only the andesite tier of Create exists now: water wheels, saws, presses, fans and mills.',
                'Crushing wheels, deployers and brass machines come with the Iron Age.']),
    ],
    supplies='bronze_supplies', comfort='bronze_comfort')

# ---------------------------------------------------------------- Iron Age
IRON = age_chapter(
    'iron', 'iron_age', 'Age 2: Iron Age', ['The map opens. Iron veins become visible.'], 'firmages:steel_heart',
    'age_2', 3,
    entry=Q('iron/entry', 'Welcome to the Iron Age', t_stage('age_2'), kind='entry',
            icon='tfc:metal/ingot/wrought_iron', sub='Age 2',
            desc=['Iron veins are visible now, the map opens and the Twilight Forest can be reached.',
                  '',
                  'Four strands lead to the Steel Heart: Metallurgy, Power & Motion, Survival and Frontier.']),
    strands=[
        ('Metallurgy', [
            Q('iron/metal/bloomery', 'Ironworks', t_item('tfc:bloomery'),
              desc=['Build a bloomery from bronze double sheets and stone bricks.']),
            Q('iron/metal/bloom', 'In Bloom', t_item('tfc:raw_iron_bloom'),
              desc=['Fill the bloomery with iron ore and charcoal and let it burn.']),
            Q('iron/metal/wrought', 'The Iron Age', t_item('tfc:metal/ingot/wrought_iron', 4),
              desc=['Hammer the bloom on an anvil until it becomes wrought iron.']),
            Q('iron/metal/anvil', 'Iron Anvil', t_item('tfc:metal/anvil/wrought_iron'),
              desc=['A wrought iron anvil can work steel.']),
            Q('iron/metal/blast', 'Blast Off!', t_item('tfc:blast_furnace'),
              desc=['The blast furnace makes pig iron from iron ore, charcoal and flux.']),
            Q('iron/metal/pig', 'Pig Iron', t_item('tfc:metal/ingot/pig_iron', 2),
              desc=['Cast the pig iron. Hammer it into high carbon steel, then into steel.']),
        ], Q('iron/metal/key', 'Steel Ingots', t_item('tfc:metal/ingot/steel', 16), kind='keystone',
             desc=['Sixteen steel ingots. From here on, steel is a material, not a trophy.'])),
        ('Power & Motion', [
            Q('iron/power/brass', 'Brass', t_item('tfc:metal/ingot/brass', 4),
              desc=['Alloy copper and zinc into brass, by hand in the crucible or heated in a Create basin.']),
            Q('iron/power/precision', 'Precision Mechanism', t_item('create:precision_mechanism'),
              desc=['Sequenced assembly on a brass base. Many Iron Age machines need it.']),
            Q('iron/power/crush', 'Crushing Wheels', t_item('create:crushing_wheel', 2),
              desc=['Crushing wheels turn ore pieces into dust for more metal per ore.']),
            Q('iron/power/heater', 'Fuel Heater', t_item('tfcreate:primitive_heater'),
              desc=['The TFCreate fuel heater heats a basin: heated mixing and compacting.']),
            Q('iron/power/steam', 'Steam Engine', t_item('create:steam_engine'),
              desc=['Steam engines on a boiler give far more force than water wheels.']),
        ], Q('iron/power/key', 'Automated Bloomery Line', t_item('tfc:metal/ingot/wrought_iron', 32),
             kind='keystone', icon='tfc:raw_iron_bloom',
             desc=['Feed the bloomery by machine and collect 32 wrought iron ingots from the line.'])),
        ('Survival', [
            Q('iron/surv/barrel', 'Do a Barrel Roll!', t_adv('tfc:story/barrel'),
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
             desc=['Confirm when your cellar holds sealed vessels with enough food for the whole team.',
                   'No food goes into any recipe or quest turn-in.'])),
        ('Frontier', [
            Q('iron/front/portal', 'Into the Twilight', t_dim('twilightforest:twilight_forest'),
              icon='twilightforest:twilight_portal_miniature_structure',
              desc=['Dig a 2x2 pool of water ringed by grass or dirt, put flowers around it and throw polished quartz into',
                    'the water. Polished quartz comes from the quartz veins of the Iron Age, polished with sandpaper.',
                    'Step through.']),
            Q('iron/front/naga', 'The Naga', t_kill('twilightforest:naga', value=1), icon='twilightforest:naga_trophy',
              desc=['Defeat the Naga in its courtyard.']),
            Q('iron/front/lich', 'The Lich', t_kill('twilightforest:lich', value=1), icon='twilightforest:lich_trophy',
              desc=['Climb the Lich tower and defeat the Lich.']),
        ], Q('iron/front/key', 'Lich Trophy', t_item('twilightforest:lich_trophy', consume=False), kind='keystone',
             desc=['The Lich drops its trophy. The Steel Heart needs it.'])),
    ],
    goal=Q('iron/goal', 'Offer the Steel Heart at the Shrine',
           t_stage('age_3', 'Offer the Steel Heart at the shrine'), kind='goal', icon='firmages:steel_heart',
           sub='Goal of the Iron Age: Caelum opens the Arcane Age',
           desc=['Craft the Steel Heart: a steel sheet, a precision mechanism, wrought iron double sheets',
                 'and the Lich trophy.',
                 '',
                 'Grow the shrine by its third ring, the Iron Sanctum: four pillars of smooth rock with',
                 'wrought iron bars and four wrought iron lamps. Use the heart to see the missing blocks.',
                 'Lay the Steel Heart on the new plinth, light the four lamps and pray at the heart.',
                 '',
                 'Caelum keeps the Steel Heart as a relic and opens the Arcane Age for the whole team.']),
    sides=[
        ('iron/power/brass', [
            Q('iron/side/rails', 'Trains', t_item('create:track', 16), kind='side',
              desc=['Create trains and Steam \'n\' Rails connect distant mines.']),
        ]),
        ('iron/metal/wrought', [
            Q('iron/side/wroughtnaut', 'Ferrous Wroughtnaut', t_kill('mowziesmobs:ferrous_wroughtnaut', value=1),
              kind='side', icon='mowziesmobs:wrought_helmet',
              desc=['A metal giant waits in a deep chamber. Strike its back.']),
        ]),
        ('iron/surv/barrel', [
            Q('iron/side/storage', 'Tom\'s Storage', t_item('toms_storage:storage_terminal'), kind='side',
              desc=['A storage terminal shows every connected chest in one screen.']),
        ]),
    ],
    explains=[
        Q('iron/info/map', 'The Map Opens', t_check(), kind='explain', icon='tfc:metal/ingot/wrought_iron',
          desc=['From the Iron Age the FTB Chunks map and minimap work. Claims and chunk loading come later.']),
        Q('iron/info/winter', 'The First Winter', t_check(), kind='explain', icon='tfc:ceramic/large_vessel',
          desc=['A TFC year has seasons. Crops stop growing in winter and it gets cold. Plan ahead.']),
    ],
    supplies='iron_supplies', comfort='iron_comfort')

# ---------------------------------------------------------------- Arcane Age
ARCANE = age_chapter(
    'arcane', 'arcane_age', 'Age 3: Arcane Age', ['Spirits, source and alchemy. The Nether opens.'],
    'firmages:arcane_keystone', 'age_3', 4,
    entry=Q('arcane/entry', 'Welcome to the Arcane Age', t_stage('age_3'), kind='entry', icon='ars_nouveau:source_gem',
            sub='Age 3',
            desc=['Magic comes to the Firmament: Occultism spirits, Ars Nouveau source and Theurgy alchemy.',
                  'Until the Information Age magic gives the best ore yield, the first endless ore and the',
                  'first item logistics.',
                  '',
                  'Three strands lead to the Arcane Keystone: Spirits, Source and Alchemy.']),
    strands=[
        ('Spirits', [
            Q('arcane/spirit/datura', 'Datura', t_item('occultism:datura_seeds'),
              desc=['Datura seeds now drop from TFC grass. Plant them on Arcane Soil (amethyst-charged TFC dirt);',
                    'TFC farmland does not hold them.']),
            Q('arcane/spirit/dream', 'Demon\'s Dream', t_item('occultism:demons_dream_essence'),
              desc=['Datura fruit becomes Demon\'s Dream essence. It opens your third eye to the spirit world.']),
            Q('arcane/spirit/dictionary', 'Dictionary of Spirits', t_item('occultism:dictionary_of_spirits'),
              desc=['The Dictionary of Spirits explains every pentacle and ritual. Keep it on hand.']),
            Q('arcane/spirit/chalk', 'White Chalk', t_item('occultism:chalk_white'),
              desc=['Chalk draws pentacles. White chalk is enough for Foliot rituals.']),
            Q('arcane/spirit/nether', 'Beneath', t_dim('minecraft:the_nether'), icon='beneath:ancient_altar',
              desc=['TFC has no nether portals. Beneath opens the Nether through a sacrifice ritual:',
                    'its field guide shows the altar and the offering. Step through and look for iesnium.']),
            Q('arcane/spirit/iesnium', 'Iesnium', t_item('occultism:iesnium_ingot', 4),
              desc=['Iesnium ore lies in the Nether. Smelt it into ingots; the Djinni rituals need it.']),
            Q('arcane/spirit/djinni', 'Bind a Djinni', t_item('occultism:book_of_binding_bound_djinni'),
              desc=['Bind a Djinni into a book of binding. A Djinni runs crafting rituals and the Mineshaft.']),
        ], Q('arcane/spirit/key', 'Spirit Proof',
             [t_item('occultism:spirit_attuned_crystal', title='Spirit attuned crystal'),
              t_item('occultism:iesnium_ingot', 2, title='Iesnium ingots')],
             kind='keystone', icon='occultism:spirit_attuned_crystal',
             desc=['A spirit attuned crystal and iesnium: the proof of the Spirits strand.',
                   'Next, Dimensional Storage and the Mineshaft with a Djinni miner, which gives rich TFC ore pieces',
                   'of every unlocked metal.'])),
        ('Source', [
            Q('arcane/source/book', 'Novice Spellbook', t_item('ars_nouveau:novice_spell_book'),
              desc=['The novice spellbook casts the first spells and opens the Ars Nouveau worn notebook.']),
            Q('arcane/source/link', 'Sourcelink',
              t_any_item(['ars_nouveau:volcanic_sourcelink', 'ars_nouveau:agronomic_sourcelink',
                          'ars_nouveau:mycelial_sourcelink', 'ars_nouveau:vitalic_sourcelink',
                          'ars_nouveau:alchemical_sourcelink'], 'Any sourcelink'),
              icon='ars_nouveau:volcanic_sourcelink',
              desc=['Sourcelinks turn fuel, growth or life around them into source. Store it in source jars.']),
            Q('arcane/source/chamber', 'Imbuement Chamber', t_item('ars_nouveau:imbuement_chamber'),
              desc=['The imbuement chamber charges items with source.']),
            Q('arcane/source/gems', 'Source Gems', t_item('ars_nouveau:source_gem', 16),
              desc=['Imbue TFC amethyst or lapis lazuli. Source gems are the base of every Ars recipe.']),
            Q('arcane/source/apparatus', 'Enchanting Apparatus', t_item('ars_nouveau:enchanting_apparatus'),
              desc=['The apparatus on an arcane core with pedestals crafts the Ars items. It is the one magic',
                    'pedestal crafting of this Age.']),
            Q('arcane/source/starbuncle', 'Starbuncle', t_item('ars_nouveau:starbuncle_charm'),
              desc=['A Starbuncle carries items between chests: the item transport of the Arcane Age.']),
            Q('arcane/source/apprentice', 'Apprentice Spellbook', t_item('ars_nouveau:apprentice_spell_book'),
              desc=['The apprentice spellbook holds the stronger glyphs you need against the Wilden Chimera.']),
            Q('arcane/source/chimera', 'Wilden Chimera', t_kill('ars_nouveau:wilden_boss', value=1),
              icon='ars_nouveau:ritual_wilden_summon',
              desc=['Summon the Wilden Chimera with its ritual and defeat it.']),
        ], Q('arcane/source/key', 'Wilden Tribute', t_item('ars_nouveau:wilden_tribute', consume=False),
             kind='keystone',
             desc=['The Chimera drops the Wilden Tribute. The Arcane Keystone needs it.'])),
        ('Alchemy', [
            Q('arcane/alch/brazier', 'Pyromantic Brazier', t_item('theurgy:pyromantic_brazier'),
              desc=['Theurgy heats its apparatus with a pyromantic brazier. Read the Hermetica as you go.']),
            Q('arcane/alch/calcination', 'Calcination Oven', t_item('theurgy:calcination_oven'),
              desc=['Calcination turns minerals into alchemical salt.']),
            Q('arcane/alch/sal', 'Sal Ammoniac', t_item('theurgy:sal_ammoniac_accumulator'),
              desc=['The accumulator gathers sal ammoniac, the solvent of every liquefaction.']),
            Q('arcane/alch/liquefaction', 'Liquefaction Cauldron', t_item('theurgy:liquefaction_cauldron'),
              desc=['The cauldron dissolves RICH TFC ore pieces into alchemical sulfur.']),
            Q('arcane/alch/distiller', 'Distiller', t_item('theurgy:distiller'),
              desc=['The distiller makes mercury shards, the third principle.']),
            Q('arcane/alch/incubator', 'Incubator', t_item('theurgy:incubator'),
              desc=['Mercury, salt and sulfur incubate into a canonical metal dust. Melt the dust in a TFC vessel.']),
            Q('arcane/alch/rod', 'Divination Rod', t_item('theurgy:divination_rod_t1'),
              desc=['A divination rod points to the ore veins your team has unlocked.']),
        ], Q('arcane/alch/key', 'Mercury Catalyst', t_item('theurgy:mercury_catalyst'), kind='keystone',
             desc=['The mercury catalyst is the proof of the Alchemy strand.',
                   'Spagyrics give about 2.5 times the metal of a hand-smelted rich piece, the best yield until',
                   'the Information Age.'])),
    ],
    goal=Q('arcane/goal', 'Offer the Arcane Keystone at the Shrine',
           t_stage('age_4', 'Offer the Arcane Keystone at the shrine'), kind='goal', icon='firmages:arcane_keystone',
           sub='Goal of the Arcane Age: Caelum opens the Industrial Age',
           desc=['Craft the Arcane Keystone in the Djinni crafting pentacle (bound Djinni book):',
                 'a spirit attuned crystal, an iesnium ingot, the Wilden Tribute, the mercury catalyst,',
                 'a TFC steel ingot and a source gem block.',
                 '',
                 'Grow the shrine by its fourth ring, the Spirit Circle: an 11 by 11 border of sourcestone,',
                 'otherstone corner pillars with source gem block caps and eight candles on the border.',
                 'Use the heart with an empty hand to see the missing blocks.',
                 'Lay the Keystone on the new plinth, light the eight candles and pray at the heart.',
                 '',
                 'Caelum keeps the Arcane Keystone as a relic and opens the Industrial Age for the whole team.']),
    sides=[
        ('arcane/spirit/nether', [
            Q('arcane/side/blaze', 'Blaze Burner', t_item('create:blaze_burner'), kind='side',
              desc=['Catch a blaze in the Nether with an empty blaze burner. Create gets superheated mixing.']),
            Q('arcane/side/enchant', 'Enchantment Industry', t_item('create_enchantment_industry:blaze_enchanter'),
              kind='side', desc=['A blaze enchanter enchants with liquid experience. No more enchanting table luck.']),
        ]),
        ('arcane/source/starbuncle', [
            Q('arcane/side/creo', 'Ars Creo', t_item('ars_creo:starbuncle_wheel'), kind='side',
              desc=['A Starbuncle on a wheel turns Create shafts. Magic and kinetics meet.']),
        ]),
        ('arcane/alch/calcination', [
            Q('arcane/side/umvuthi', 'Umvuthi', t_kill('mowziesmobs:umvuthi', value=1), kind='side',
              icon='mowziesmobs:sol_visage',
              desc=['The Umvuthana chief rules a grove in hot biomes. Defeat Umvuthi for the Sol Visage.']),
        ]),
    ],
    explains=[
        Q('arcane/info/ore', 'Ore in the Arcane Age', t_check(), kind='explain', icon='tfc:ore/rich_hematite',
          desc=['Three ore routes now: Create crushing wheels (about 1.5 to 1.9 times), Theurgy spagyrics',
                '(only rich pieces, about 2.5 times) and the Dimensional Mineshaft (endless rich pieces).',
                'The Occultism crusher grinds only Occultism\'s own materials, no ore.']),
        Q('arcane/info/one', 'One Carrier per Function', t_check(), kind='explain', icon='ars_nouveau:starbuncle_charm',
          desc=['Each magic function has one carrier: Starbuncles move items, Dimensional Storage stores them,',
                'Theurgy multiplies ore, the Djinni crafts in pentacles. Duplicates are switched off.']),
    ],
    supplies='arcane_supplies', comfort='arcane_comfort',
    ring=ring_quest('arcane', 3, 'the Spirit Circle', 'ars_nouveau:sourcestone',
                    ['Build the Spirit Circle around the Iron Sanctum: an 11 by 11 border of sourcestone, four',
                     'otherstone corner pillars three high with source gem block caps, eight candles on the border',
                     'and the fourth plinth in the middle of the north side.',
                     'When the ring stands, the heart is ready for the Arcane Keystone.']))

# ---------------------------------------------------------------- Industrial Age
INDUSTRIAL = age_chapter(
    'industrial', 'industrial_age', 'Age 4: Industrial Age', ['Coke, black steel and the first FE.'],
    'firmages:pressure_core', 'age_4', 5,
    entry=Q('industrial/entry', 'Welcome to the Industrial Age', t_stage('age_4'), kind='entry',
            icon='immersiveengineering:hammer', sub='Age 4',
            desc=['Immersive Engineering arrives: coke ovens, blast furnaces, crushers, presses and the first FE.',
                  'Your team also gets a chunk claim and force-load quota with this Age.',
                  '',
                  'Four strands lead to the Pressure Core: Coke & Steel, Power, Ore Line and Logistics & Backfill.']),
    strands=[
        ('Coke & Steel', [
            Q('industrial/steel/wood', 'Treated Wood', t_item('immersiveengineering:treated_wood_horizontal', 16),
              desc=['Soak planks in creosote. Treated wood is the frame of every IE machine.']),
            Q('industrial/steel/coke', 'Coke Oven', t_observe('immersiveengineering:coke_oven',
                                                               title='Look at a formed coke oven'),
              icon='immersiveengineering:cokebrick',
              desc=['Build the 3x3x3 coke oven from coke bricks and form it with the Engineer\'s Hammer.',
                    'It cokes TFC bituminous coal and lignite and gives creosote.']),
            Q('industrial/steel/fuel', 'Coal Coke', t_item('immersiveengineering:coal_coke', 32),
              desc=['Coke burns hot and clean. The blast furnace needs it.']),
            Q('industrial/steel/blast', 'Blast Furnace', t_observe('immersiveengineering:blast_furnace',
                                                                    title='Look at a formed blast furnace'),
              icon='immersiveengineering:blastbrick',
              desc=['The IE blast furnace makes TFC steel from wrought iron and coke.']),
            Q('industrial/steel/gearbox', 'Arcane Gearbox', t_item('firmages:arcane_gearbox'),
              desc=['Source gems in a steel gearbox. A Wixie can automate it. It goes into the reinforced',
                    'blast bricks and into the Pressure Core.']),
            Q('industrial/steel/improved', 'Improved Blast Furnace',
              t_observe('immersiveengineering:advanced_blast_furnace', title='Look at a formed improved blast furnace'),
              icon='immersiveengineering:blastbrick_reinforced',
              desc=['Reinforced blast bricks and preheaters make steel faster.']),
            Q('industrial/steel/black', 'Black Steel', t_item('tfc:metal/ingot/black_steel', 8),
              desc=['Weak steel with nickel and black bronze, worked on the anvil: black steel, the Age 4 metal.']),
            Q('industrial/steel/anvil', 'Black Steel Anvil', t_item('tfc:metal/anvil/black_steel'),
              desc=['A black steel anvil works black steel double sheets.']),
            Q('industrial/steel/monstrosity', 'Netherite Monstrosity',
              t_kill('cataclysm:netherite_monstrosity', value=1), icon='cataclysm:monstrous_horn',
              desc=['A Gateway in the Nether calls the Netherite Monstrosity. Its horn goes into the Pressure Core.',
                    'If the Gateway fails, the Summoning Rituals altar can call it too.']),
        ], Q('industrial/steel/key', 'Black Steel Double Sheets', t_item('tfc:metal/double_sheet/black_steel', 2),
             kind='keystone', desc=['Two black steel double sheets for the Pressure Core.'])),
        ('Power', [
            Q('industrial/power/alternator', 'Alternator', t_item('createaddition:alternator'),
              desc=['The alternator turns Create rotation into FE. It is the only bridge from SU to FE.']),
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
              desc=['The IE crusher grinds TFC ore pieces into mineral powder and canonical dust: twice the metal,',
                    'plus a by-product.']),
            Q('industrial/ore/press', 'Metal Press', t_observe('immersiveengineering:metal_press',
                                                               title='Look at a formed metal press'),
              icon='immersiveengineering:mold_plate',
              desc=['The metal press makes TFC sheets from two ingots, rods and wire.']),
            Q('industrial/ore/kiln', 'Alloy Kiln', t_observe('immersiveengineering:alloy_smelter',
                                                             title='Look at a formed alloy kiln'),
              icon='immersiveengineering:alloybrick',
              desc=['The alloy kiln alloys two metals in TFC ratios. It retires with the Electric Age.']),
            Q('industrial/ore/constantan', 'Constantan', t_item('immersiveengineering:ingot_constantan', 8),
              desc=['Copper and nickel in the alloy kiln.']),
        ], Q('industrial/ore/key', 'Automated Ore Line', t_item('mekanism:dust_iron', 64), kind='keystone',
             desc=['Feed the crusher by machine and collect 64 iron dust from the line.'])),
        ('Logistics & Backfill', [
            Q('industrial/logi/packager', 'Packager', t_item('create:packager'),
              desc=['Create 6 packages: the packager boxes items for the network.']),
            Q('industrial/logi/frogport', 'Frogport', t_item('create:package_frogport'),
              desc=['Frogports send packages along chain conveyors.']),
            Q('industrial/logi/ticker', 'Stock Ticker', t_item('create:stock_ticker'),
              desc=['A stock link and a stock ticker show and request your stock from anywhere on the network.']),
            Q('industrial/logi/gauge', 'Factory Gauge', t_item('create:factory_gauge'),
              desc=['Factory gauges keep a production line stocked by themselves.']),
            Q('industrial/logi/atfc', 'Advanced TFC Tech',
              t_any_item(['advancedtfctech:thresher', 'advancedtfctech:grist_mill', 'advancedtfctech:beamhouse',
                          'advancedtfctech:fleshing_machine', 'advancedtfctech:power_loom'], 'Any Advanced TFC Tech machine'),
              icon='advancedtfctech:power_loom',
              desc=['Thresher, grist mill, beamhouse, fleshing machine and power loom take over the TFC hand work.']),
        ], Q('industrial/logi/key', 'Backfill Line', t_item('tfc:metal/sheet/steel', 64), kind='keystone',
             icon='create:package_frogport',
             desc=['Bring 64 steel sheets from your press line through the package network.'])),
    ],
    goal=Q('industrial/goal', 'Offer the Pressure Core at the Shrine',
           t_stage('age_5', 'Offer the Pressure Core at the shrine'), kind='goal', icon='firmages:pressure_core',
           sub='Goal of the Industrial Age: Caelum opens the Electric Age',
           desc=['Craft the Pressure Core: two black steel double sheets, a heavy engineering block,',
                 'the Arcane Gearbox and the horn of the Netherite Monstrosity.',
                 '',
                 'Grow the shrine by its fifth ring, the Foundry Nave: a 13 by 13 border of coke bricks,',
                 'heavy engineering block bases, steel scaffolding three high and electric lanterns on top.',
                 'Lay the Pressure Core on the new plinth, power the four electric lanterns and pray at the heart.',
                 '',
                 'Caelum keeps the Pressure Core as a relic and opens the Electric Age for the whole team.']),
    sides=[
        ('industrial/steel/wood', [
            Q('industrial/side/airship', 'Airship', t_item('aeronautics:andesite_propeller'), kind='side',
              desc=['Create Aeronautics builds flying machines. A propeller is the first part.']),
        ]),
        ('industrial/power/coil', [
            Q('industrial/side/jetpack', 'Jetpack', t_item('create_jetpack:jetpack'), kind='side',
              desc=['A Create jetpack flies on compressed air from its tank.']),
        ]),
        ('industrial/ore/crusher', [
            Q('industrial/side/spawner', 'Mechanical Spawner', t_item('create_mechanical_spawner:mechanical_spawner'),
              kind='side', desc=['Spawn fluids turn into mobs: only those of the current mob stage.']),
        ]),
        ('industrial/logi/packager', [
            Q('industrial/side/frostmaw', 'Frostmaw', t_kill('mowziesmobs:frostmaw', value=1), kind='side',
              icon='mowziesmobs:ice_crystal',
              desc=['A giant sleeps in the cold lands. Wake it and take its ice crystal.']),
        ]),
    ],
    explains=[
        Q('industrial/info/fe', 'FE and SU', t_check(), kind='explain', icon='createaddition:alternator',
          desc=['FE is the one energy unit of the pack. Create rotation (SU) has one bridge: the alternator',
                'turns SU into FE and the electric motor turns FE back into SU.']),
        Q('industrial/info/multiblock', 'Multiblocks', t_check(), kind='explain', icon='immersiveengineering:manual',
          desc=['IE machines are multiblocks: build the shape from the Engineer\'s Manual, then hit the marked',
                'block with the Engineer\'s Hammer. Projector blueprints show the shape in the world.']),
        Q('industrial/info/claims', 'Chunk Claims', t_check(), kind='explain', icon='minecraft:map',
          desc=['From this Age your team can claim and force-load chunks with the FTB Chunks map.',
                'Force-load the chunks your lines run in.']),
    ],
    supplies='industrial_supplies', comfort='industrial_comfort',
    ring=ring_quest('industrial', 4, 'the Foundry Nave', 'immersiveengineering:cokebrick',
                    ['Build the Foundry Nave around the Spirit Circle: a 13 by 13 border of coke bricks, heavy',
                     'engineering block bases, steel scaffolding three high and four electric lanterns on top,',
                     'with the fifth plinth in the middle of the north side.']))

# ---------------------------------------------------------------- Electric Age
ELECTRIC = age_chapter(
    'electric', 'electric_age', 'Age 5: Electric Age', ['High voltage, oil and aluminium.'],
    'firmages:humming_core', 'age_5', 6,
    entry=Q('electric/entry', 'Welcome to the Electric Age', t_stage('age_5'), kind='entry',
            icon='immersiveengineering:capacitor_hv', sub='Age 5',
            desc=['MV and HV power, oil, the arc furnace and the excavator. Bauxite veins are visible now.',
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
              desc=['Alternating Flux wire carries very high power with little loss.']),
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
              desc=['Lubricant speeds up IE machines through the automatic lubricator.']),
        ], Q('electric/oil/key', 'Refined Diesel', t_item('immersivepetroleum:diesel_bucket', 16), kind='keystone',
             desc=['Sixteen buckets of diesel from your own refinery.'])),
        ('Aluminium & Excavator', [
            Q('electric/alu/bauxite', 'Bauxite',
              t_any_item(['tfc_ie_addon:ore/small_bauxite', 'tfc_ie_addon:ore/poor_bauxite',
                          'tfc_ie_addon:ore/normal_bauxite', 'tfc_ie_addon:ore/rich_bauxite'], 'Any bauxite ore piece'),
              icon='tfc_ie_addon:ore/normal_bauxite', desc=['Bauxite veins are visible from this Age.']),
            Q('electric/alu/arc', 'Arc Furnace', t_observe('immersiveengineering:arc_furnace',
                                                           title='Look at a formed arc furnace'),
              icon='immersiveengineering:graphite_electrode',
              desc=['The arc furnace melts, alloys and makes steel with graphite electrodes.',
                    'It takes over from the alloy kiln.']),
            Q('electric/alu/ingot', 'Aluminium', t_item('immersiveengineering:ingot_aluminum', 16),
              desc=['Aluminium comes only from the arc furnace.']),
            Q('electric/alu/red', 'Red Steel', t_item('tfc:metal/sheet/red_steel', 2),
              desc=['Red steel is the Age 5 metal: weak red steel worked on the anvil, then a sheet.']),
            Q('electric/alu/excavator', 'Excavator', t_observe('immersiveengineering:excavator',
                                                               title='Look at a formed excavator'),
              icon='immersiveengineering:bucket_wheel',
              desc=['The excavator digs the mineral mixes of a chunk forever: an endless ore source.']),
        ], Q('electric/alu/key', 'Aluminium Plates', t_item('immersiveengineering:plate_aluminum', 32),
             kind='keystone', desc=['Thirty-two aluminium plates from the metal press.'])),
        ('Circuits', [
            Q('electric/circ/tube', 'Vacuum Tubes', t_item('immersiveengineering:electron_tube', 8),
              desc=['Vacuum tubes are the first electronics.']),
            Q('electric/circ/board', 'Circuit Board', t_item('immersiveengineering:circuit_board', 4),
              desc=['Circuit boards carry the tubes.']),
            Q('electric/circ/assembler', 'Assembler', t_observe('immersiveengineering:assembler',
                                                                title='Look at a formed assembler'),
              icon='immersiveengineering:assembler',
              desc=['The assembler autocrafts grid recipes: the autocrafting of this Age.']),
            Q('electric/circ/wither', 'The Wither', t_kill('minecraft:wither', value=1), icon='minecraft:nether_star',
              desc=['Summon the Wither and defeat it. The Humming Core needs its Nether Star.']),
        ], Q('electric/circ/key', 'Attuned Circuits', t_item('firmages:attuned_circuit', 16), kind='keystone',
             desc=['A circuit board with a spirit attuned crystal: the magic tail of the Electric Age.',
                   'Let the assembler make them.'])),
    ],
    goal=Q('electric/goal', 'Offer the Humming Core at the Shrine',
           t_stage('age_6', 'Offer the Humming Core at the shrine'), kind='goal', icon='firmages:humming_core',
           sub='Goal of the Electric Age: Caelum opens the Information Age',
           desc=['Fuse the Humming Core in the arc furnace: an HV capacitor with an Attuned Circuit,',
                 'four aluminium plates, two red steel sheets and a Nether Star.',
                 '',
                 'Grow the shrine by its sixth ring, the Tesla Crown: a 15 by 15 border of aluminium,',
                 'red steel block bases, HV capacitors, aluminium scaffolding and four floodlights on top.',
                 'Lay the Humming Core on the new plinth, light the floodlights (power and a redstone signal)',
                 'and pray at the heart.',
                 '',
                 'Caelum keeps the Humming Core as a relic and opens the Information Age for the whole team.']),
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
        ('electric/grid/mv', [
            Q('electric/side/sculptor', 'The Sculptor', t_kill('mowziesmobs:sculptor', value=1), kind='side',
              icon='mowziesmobs:sculptor_staff',
              desc=['A sculptor waits on a mountain top. Pass the climb to earn the geomancer gear.']),
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
                    ['Build the Tesla Crown around the Foundry Nave: a 15 by 15 border of aluminium sheetmetal or',
                     'aluminium blocks, red steel block bases with HV capacitors, aluminium scaffolding two high and',
                     'four floodlights on top, with the sixth plinth in the middle of the north side.']))

# ---------------------------------------------------------------- Information Age
INFORMATION = age_chapter(
    'information', 'information_age', 'Age 6: Information Age', ['Mekanism, AE2 and the End.'],
    'firmages:data_matrix', 'age_6', 7,
    entry=Q('information/entry', 'Welcome to the Information Age', t_stage('age_6'), kind='entry',
            icon='mekanism:advanced_control_circuit', sub='Age 6',
            desc=['Mekanism triples ore, AE2 stores and crafts everything, and the End can be reached.',
                  'Osmium and certus quartz veins are visible now.',
                  '',
                  'Four strands lead to the Data Matrix: Digital, Mekanism, Mob Data and The End.']),
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
              desc=['Processors are made by Create sequenced assembly (Create: Applied Kinetics).',
                    'The inscriber has no processor recipes in this pack.']),
            Q('information/digital/controller', 'ME Controller', t_item('ae2:controller'),
              desc=['The controller powers an ME network.']),
            Q('information/digital/drive', 'ME Drive', [t_item('ae2:drive', title='ME drive'),
                                                         t_item('ae2:item_storage_cell_1k', title='1k storage cell')],
              icon='ae2:drive', desc=['Drives hold the storage cells. AE2 is now the one storage network.']),
            Q('information/digital/assembler', 'Molecular Assembler',
              [t_item('ae2:molecular_assembler', title='Molecular assembler'),
               t_item('ae2:pattern_provider', title='Pattern provider')],
              icon='ae2:molecular_assembler', desc=['Patterns and assemblers craft on request.']),
        ], Q('information/digital/key', 'Autocrafting Online',
             [t_item('ae2:crafting_unit', title='Crafting unit'),
              t_item('ae2:cell_component_64k', title='64k storage component')],
             kind='keystone', icon='ae2:crafting_unit',
             desc=['Build a crafting CPU and let the network craft a 64k storage component.'])),
        ('Mekanism', [
            Q('information/mek/osmium', 'Osmium', t_item('mekanism:ingot_osmium', 8),
              desc=['Native osmium is a TFC-style vein from this Age.']),
            Q('information/mek/casing', 'Steel Casing', t_item('mekanism:steel_casing'),
              desc=['Every Mekanism machine starts with a steel casing.']),
            Q('information/mek/infuser', 'Metallurgic Infuser', t_item('mekanism:metallurgic_infuser'),
              desc=['The infuser makes the Mekanism alloys and the control circuits.']),
            Q('information/mek/purify', 'Purification Chamber', t_item('mekanism:purification_chamber'),
              desc=['Purification gives three times the metal from every ore piece.']),
            Q('information/mek/separator', 'Electrolytic Separator', t_item('mekanism:electrolytic_separator'),
              desc=['Split water into hydrogen and oxygen. Hydrogen burns in the gas-burning generator.']),
            Q('information/mek/hdpe', 'HDPE', t_item('mekanism:hdpe_sheet', 8),
              desc=['The pressurized reaction chamber makes HDPE from ethene.']),
            Q('information/mek/gas', 'Gas-Burning Generator', t_item('mekanismgenerators:gas_burning_generator'),
              desc=['The FE generator of this Age.']),
            Q('information/mek/blue', 'Blue Steel', t_item('tfc:metal/sheet/blue_steel', 4),
              desc=['Blue steel is the Age 6 metal. The Data Matrix needs four sheets.']),
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
            Q('information/end/eyes', 'Eyes of Ender', t_item('minecraft:ender_eye', 12),
              desc=['TFC has no strongholds. Your team builds its own End portal.']),
            Q('information/end/enter', 'The End', t_dim('minecraft:the_end'), icon='minecraft:end_stone',
              desc=['Build the End portal frame, fill it with eyes of ender and step through.']),
            Q('information/end/dragon', 'The Ender Dragon', t_kill('minecraft:ender_dragon', value=1),
              icon='minecraft:dragon_head', desc=['Destroy the crystals, then the dragon.']),
        ], Q('information/end/key', 'Dragon\'s Breath', t_item('minecraft:dragon_breath', 4), kind='keystone',
             desc=['Bottle the dragon\'s breath. The Data Matrix needs it.'])),
    ],
    goal=Q('information/goal', 'Offer the Data Matrix at the Shrine',
           t_stage('age_7', 'Offer the Data Matrix at the shrine'), kind='goal', icon='firmages:data_matrix',
           sub='Goal of the Information Age: Caelum opens the Space Age',
           desc=['Craft the Data Matrix: four blue steel sheets, four advanced control circuits,',
                 'a 64k storage component and a bottle of dragon\'s breath.',
                 '',
                 'Grow the shrine by its seventh ring, the Data Nave: a 17 by 17 border of certus quartz blocks,',
                 'steel casing bases, quartz glass two high and fluix block caps.',
                 'Lay the Data Matrix on the new plinth and pray at the heart together: Caelum wants a chorus',
                 'of at least two of you (alone, one is enough).',
                 '',
                 'Caelum keeps the Data Matrix as a relic and opens the Space Age for the whole team.']),
    sides=[
        ('information/mek/osmium', [
            Q('information/side/mystical', 'Mystical Agriculture', t_item('mysticalagriculture:inferium_essence', 32),
              kind='side', desc=['Grow inferium and the resource seeds of unlocked materials.']),
        ]),
        ('information/digital/silicon', [
            Q('information/side/archmage', 'Archmage', t_item('ars_nouveau:archmage_spell_book'), kind='side',
              desc=['The archmage spellbook needs a Mekanism advanced control circuit.']),
        ]),
        ('information/mob/learner', [
            Q('information/side/jetpack', 'Mekanism Jetpack', t_item('mekanism:jetpack'), kind='side',
              desc=['A jetpack on hydrogen.']),
        ]),
        ('information/end/eyes', [
            Q('information/side/guardian', 'Ender Guardian', t_kill('cataclysm:ender_guardian', value=1), kind='side',
              icon='minecraft:end_stone', desc=['A guardian of the End waits in its ruins.']),
        ]),
    ],
    explains=[
        Q('information/info/one', 'One Network', t_check(), kind='explain', icon='ae2:controller',
          desc=['AE2 is the one storage and autocrafting network from this Age. Tom\'s Simple Storage retires,',
                'and the IE LV and MV capacitors give way to Mekanism energy cubes.']),
        Q('information/info/ore', 'Ore Tripling', t_check(), kind='explain', icon='mekanism:purification_chamber',
          desc=['Mekanism purification gives three times the metal. Theurgy and the IE crusher stay usable,',
                'but Mekanism is the yield route from now on.']),
    ],
    supplies='information_supplies', comfort='information_comfort',
    ring=ring_quest('information', 6, 'the Data Nave', 'ae2:quartz_glass',
                    ['Build the Data Nave around the Tesla Crown: a 17 by 17 border of certus quartz blocks, steel',
                     'casing bases, quartz glass two high and fluix block caps, with the seventh plinth in the',
                     'middle of the north side.']))

# ---------------------------------------------------------------- Space Age
SPACE = age_chapter(
    'space', 'space_age', 'Age 7: Space Age', ['Fission, rockets and draconium.'], 'firmages:star_chart', 'age_7', 8,
    entry=Q('space/entry', 'Welcome to the Space Age', t_stage('age_7'), kind='entry', icon='ad_astra:tier_1_rocket',
            sub='Age 7',
            desc=['Uranium, fission, the first rockets to the Moon and Mars, and Draconic Evolution.',
                  '',
                  'Four strands lead to the Star Chart: Atomic, Rocketry & Moon, Mars and Draconium.']),
    strands=[
        ('Atomic', [
            Q('space/atom/circuit', 'Elite Circuits', t_item('mekanism:elite_control_circuit', 4),
              desc=['Elite control circuits run the reactor parts.']),
            Q('space/atom/uranium', 'Yellow Cake', t_item('mekanism:yellow_cake_uranium', 8),
              desc=['Uraninite veins are visible now. Process the ore into yellow cake.']),
            Q('space/atom/fuel', 'Fuel Assemblies', t_item('mekanismgenerators:fission_fuel_assembly', 4),
              desc=['Fuel assemblies hold the fissile fuel in the fission reactor.']),
            Q('space/atom/reactor', 'Fission Reactor', t_item('mekanismgenerators:fission_reactor_casing', 32),
              desc=['Build the fission reactor. Cool it well: a hot reactor melts down.']),
            Q('space/atom/turbine', 'Industrial Turbine',
              [t_item('mekanismgenerators:turbine_rotor', 4, title='Turbine rotors'),
               t_item('mekanismgenerators:turbine_blade', 8, title='Turbine blades')],
              icon='mekanismgenerators:turbine_rotor',
              desc=['The thermoelectric boiler and the industrial turbine turn reactor heat into FE.']),
        ], Q('space/atom/key', 'Reactor Online', t_item('mekanism:pellet_polonium', 2), kind='keystone',
             desc=['Polonium from your reactor waste: two pellets. One is the catalyst of the Star Chart.'])),
        ('Rocketry & Moon', [
            Q('space/moon/workbench', 'NASA Workbench', t_item('ad_astra:nasa_workbench'),
              desc=['Rockets are built at the NASA workbench.']),
            Q('space/moon/suit', 'Space Suit',
              [t_item('ad_astra:space_helmet', title='Space helmet'), t_item('ad_astra:space_suit', title='Space suit'),
               t_item('ad_astra:space_pants', title='Space pants'), t_item('ad_astra:space_boots', title='Space boots')],
              icon='ad_astra:space_helmet', desc=['A full space suit with oxygen keeps you alive off-world.']),
            Q('space/moon/fuel', 'Rocket Fuel', t_item('ad_astra:fuel_bucket'),
              desc=['Rocket fuel is refined from your oil. EMI shows the route.']),
            Q('space/moon/pad', 'Launch Pad', t_item('ad_astra:launch_pad'),
              desc=['Rockets start from a launch pad.']),
            Q('space/moon/rocket', 'Tier 1 Rocket', t_item('ad_astra:tier_1_rocket'),
              desc=['The tier 1 rocket reaches the Moon.']),
            Q('space/moon/land', 'The Moon', t_dim('ad_astra:moon'), icon='ad_astra:moon_sand',
              desc=['Land on the Moon.']),
            Q('space/moon/desh', 'Desh', t_item('ad_astra:raw_desh', 16), desc=['Mine desh on the Moon.']),
        ], Q('space/moon/key', 'Desh Plates', t_item('ad_astra:desh_plate', 32), kind='keystone',
             desc=['Thirty-two desh plates from the metal press.'])),
        ('Mars', [
            Q('space/mars/rocket', 'Tier 2 Rocket', t_item('ad_astra:tier_2_rocket'),
              desc=['Desh builds the tier 2 rocket.']),
            Q('space/mars/land', 'Mars', t_dim('ad_astra:mars'), icon='ad_astra:raw_ostrum', desc=['Land on Mars.']),
            Q('space/mars/ostrum', 'Ostrum', t_item('ad_astra:raw_ostrum', 16), desc=['Mine ostrum on Mars.']),
            Q('space/mars/harbinger', 'The Harbinger', t_kill('cataclysm:the_harbinger', value=1),
              icon='cataclysm:witherite_block',
              desc=['Defeat the Harbinger. Its witherite block goes into the Star Chart.']),
        ], Q('space/mars/key', 'Ostrum Plates', t_item('ad_astra:ostrum_plate', 32), kind='keystone',
             desc=['Thirty-two ostrum plates.'])),
        ('Draconium', [
            Q('space/drac/ingot', 'Draconium', t_item('draconicevolution:draconium_ingot', 16),
              desc=['Draconium ore lies in the End.']),
            Q('space/drac/heart', 'Dragon Heart', t_item('draconicevolution:dragon_heart', consume=False),
              desc=['The Ender Dragon now drops its heart.']),
            Q('space/drac/core', 'Fusion Crafting Core', t_item('draconicevolution:crafting_core'),
              desc=['Fusion crafting is the one pedestal crafting of the tech Ages.']),
            Q('space/drac/injectors', 'Injectors',
              [t_item('draconicevolution:basic_crafting_injector', 4, title='Basic injectors'),
               t_item('draconicevolution:wyvern_crafting_injector', 4, title='Wyvern injectors')],
              icon='draconicevolution:wyvern_crafting_injector',
              desc=['Injectors around the core hold the ingredients.']),
        ], Q('space/drac/key', 'Wyvern Core', t_item('draconicevolution:wyvern_core'), kind='keystone',
             desc=['The wyvern core is the heart of wyvern gear and of the Star Chart.'])),
    ],
    goal=Q('space/goal', 'Offer the Star Chart at the Shrine',
           t_stage('age_8', 'Offer the Star Chart at the shrine'), kind='goal', icon='firmages:star_chart',
           sub='Goal of the Space Age: Caelum opens the Quantum Age',
           desc=['Fuse the Star Chart at wyvern level: a polonium pellet as catalyst, two desh plates,',
                 'two ostrum plates, a wyvern core and the Harbinger\'s witherite block.',
                 '',
                 'Grow the shrine by its eighth ring, the Star Spire: a 19 by 19 border of steel plating,',
                 'desh block pillars four high and ostrum block caps.',
                 'Lay the Star Chart on the new plinth and pray at night, under an open sky, without rain:',
                 'Caelum wants to see the stars.',
                 '',
                 'Caelum keeps the Star Chart as a relic and opens the Quantum Age for the whole team.']),
    sides=[
        ('space/atom/circuit', [
            Q('space/side/miner', 'Digital Miner', t_item('mekanism:digital_miner'), kind='side',
              desc=['The digital miner mines a whole area by filter. It is the way to the Naquadah of Age 9.']),
        ]),
        ('space/atom/uranium', [
            Q('space/side/induction', 'Induction Matrix', t_item('mekanism:basic_induction_cell', 4), kind='side',
              desc=['The induction matrix stores huge amounts of FE.']),
        ]),
        ('space/moon/workbench', [
            Q('space/side/mega', 'MEGA Cells', t_item('megacells:cell_component_1m'), kind='side',
              desc=['MEGA cells hold far more than the AE2 cells.']),
        ]),
        ('space/drac/ingot', [
            Q('space/side/remnant', 'Ancient Remnant', t_kill('cataclysm:ancient_remnant', value=1), kind='side',
              icon='cataclysm:ancient_metal_ingot', desc=['A desert tomb hides the Ancient Remnant.']),
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
                    ['Build the Star Spire around the Data Nave: a 19 by 19 border of steel plating, desh block',
                     'pillars four high and ostrum block caps, with the eighth plinth in the middle of the',
                     'north side.']))

# ---------------------------------------------------------------- Quantum Age
QUANTUM = age_chapter(
    'quantum', 'quantum_age', 'Age 8: Quantum Age', ['Fusion, antimatter and the inner planets.'],
    'firmages:quantum_core', 'age_8', 9,
    entry=Q('quantum/entry', 'Welcome to the Quantum Age', t_stage('age_8'), kind='entry',
            icon='mekanism:pellet_antimatter', sub='Age 8',
            desc=['Fusion power, the SPS, rockets to Venus, Mercury and Glacio, awakened draconium and the Marid.',
                  '',
                  'Four strands lead to the Quantum Core: Fusion, Inner Planets, Awakened and Marid.',
                  'The Quantum Core is the last offering at the shrine.']),
    strands=[
        ('Fusion', [
            Q('quantum/fusion/frame', 'Fusion Reactor', t_item('mekanismgenerators:fusion_reactor_frame', 32),
              desc=['The fusion reactor is the FE source of this Age.']),
            Q('quantum/fusion/controller', 'Reactor Controller', t_item('mekanismgenerators:fusion_reactor_controller'),
              desc=['The controller sits in the top of the reactor.']),
            Q('quantum/fusion/hohlraum', 'Hohlraum', t_item('mekanismgenerators:hohlraum'),
              desc=['A filled hohlraum and a laser ignite the reactor.']),
            Q('quantum/fusion/fuel', 'Fusion Fuel', t_item('mekanismgenerators:fusion_fuel_bucket'),
              desc=['Deuterium and tritium make the fuel.']),
            Q('quantum/fusion/sps', 'SPS', [t_item('mekanism:sps_casing', 16, title='SPS casings'),
                                            t_item('mekanism:supercharged_coil', 2, title='Supercharged coils')],
              icon='mekanism:sps_casing', desc=['The supercritical phase shifter turns polonium into antimatter.']),
        ], Q('quantum/fusion/key', 'Antimatter Pellets', t_item('mekanism:pellet_antimatter', 8), kind='keystone',
             desc=['Eight antimatter pellets. Two go into the Quantum Core.'])),
        ('Inner Planets', [
            Q('quantum/planets/t3', 'Tier 3 Rocket', t_item('ad_astra:tier_3_rocket'),
              desc=['Ostrum builds the tier 3 rocket for Venus and Mercury.']),
            Q('quantum/planets/venus', 'Venus', t_dim('ad_astra:venus'), icon='ad_astra:raw_calorite',
              desc=['Land on Venus.']),
            Q('quantum/planets/calorite', 'Calorite', t_item('ad_astra:raw_calorite', 16),
              desc=['Mine calorite on Venus.']),
            Q('quantum/planets/mercury', 'Mercury', t_dim('ad_astra:mercury'), icon='ad_astra:tier_3_rocket',
              desc=['Visit Mercury. Its Naquadah is locked until the Singularity Age.']),
            Q('quantum/planets/t4', 'Tier 4 Rocket', t_item('ad_astra:tier_4_rocket'),
              desc=['Calorite builds the tier 4 rocket for Glacio.']),
            Q('quantum/planets/glacio', 'Glacio', t_dim('ad_astra:glacio'), icon='ad_astra:ice_shard',
              desc=['Land on Glacio.']),
            Q('quantum/planets/ice', 'Ice Shards', t_item('ad_astra:ice_shard', 8),
              desc=['Ice shards from Glacio go into the Quantum Core.']),
        ], Q('quantum/planets/key', 'Calorite Plates', t_item('ad_astra:calorite_plate', 32), kind='keystone',
             desc=['Thirty-two calorite plates.'])),
        ('Awakened', [
            Q('quantum/awake/stars', 'Nether Stars', t_item('minecraft:nether_star', 4),
              desc=['Awakened draconium needs a dragon heart and Nether Stars.']),
            Q('quantum/awake/ingot', 'Awakened Draconium', t_item('draconicevolution:awakened_draconium_ingot', 16),
              desc=['Fuse draconium blocks with a dragon heart and Nether Stars.']),
            Q('quantum/awake/injectors', 'Awakened Injectors',
              t_item('draconicevolution:awakened_crafting_injector', 4),
              desc=['Draconic-level fusion needs awakened injectors.']),
            Q('quantum/awake/evolved', 'Evolved Mekanism', t_item('evolvedmekanism:alloy_hypercharged', 4),
              desc=['Evolved Mekanism adds the tiers above ultimate.']),
            Q('quantum/awake/leviathan', 'The Leviathan', t_kill('cataclysm:the_leviathan', value=1),
              icon='cataclysm:abyssal_egg',
              desc=['Defeat the Leviathan in the deep. Its abyssal egg goes into the Quantum Core.']),
        ], Q('quantum/awake/key', 'Awakened Core', t_item('draconicevolution:awakened_core'), kind='keystone',
             desc=['The awakened core is the catalyst of the Quantum Core.'])),
        ('Marid', [
            Q('quantum/marid/alloy', 'Atomic Alloy', t_item('mekanism:alloy_atomic', 4),
              desc=['The Marid book needs atomic alloy and a wyvern core.']),
            Q('quantum/marid/book', 'Book of Binding: Marid', t_item('occultism:book_of_binding_marid'),
              desc=['Write the Marid\'s name into a book of binding.']),
            Q('quantum/marid/bind', 'Bind a Marid', t_item('occultism:book_of_binding_bound_marid'),
              desc=['Bind the Marid, the strongest spirit of Occultism.']),
        ], Q('quantum/marid/key', 'Awakened Keystone', t_item('firmages:awakened_keystone', consume=False),
             kind='keystone',
             desc=['The Marid ritual awakens the Arcane Keystone. The Awakened Keystone goes into the',
                   'Ultimate Singularity.'])),
    ],
    goal=Q('quantum/goal', 'Offer the Quantum Core at the Shrine',
           t_stage('age_9', 'Offer the Quantum Core at the shrine'), kind='goal', icon='firmages:quantum_core',
           sub='Goal of the Quantum Age: Caelum opens the Singularity Age',
           desc=['Fuse the Quantum Core at draconic level: the awakened core as catalyst, two antimatter',
                 'pellets, two calorite plates, two ice shards and the Leviathan\'s abyssal egg.',
                 '',
                 'Grow the shrine by its ninth ring, the Quantum Ring: a 21 by 21 border of calorite blocks,',
                 'SPS casings or fusion reactor frames two high and awakened draconium pylons.',
                 'Lay the Quantum Core on the new plinth and pray together at night under an open sky:',
                 'Caelum wants a chorus of at least two of you and the stars.',
                 '',
                 'Caelum keeps the Quantum Core as its last relic and opens the Singularity Age.']),
    sides=[
        ('quantum/fusion/frame', [
            Q('quantum/side/draconic', 'Draconic Tools', t_item('draconicevolution:draconic_pickaxe'), kind='side',
              desc=['Draconic tools have direct recipes from the awakened core.']),
        ]),
        ('quantum/planets/t3', [
            Q('quantum/side/crystallizer', 'Ore Times Five', t_item('mekanism:chemical_crystallizer'), kind='side',
              desc=['Dissolution, washing and crystallizing give five times the metal.']),
        ]),
        ('quantum/awake/stars', [
            Q('quantum/side/scylla', 'Scylla', t_kill('cataclysm:scylla', value=1), kind='side',
              icon='minecraft:trident', desc=['Scylla rules a storm-wracked sea. A hard optional fight.']),
        ]),
    ],
    explains=[
        Q('quantum/info/last', 'The Last Offering', t_check(), kind='explain', icon='firmages:offering_plinth',
          desc=['The Quantum Core is the ninth relic. The Singularity Age has no offering: it ends in battle.']),
        Q('quantum/info/reactor', 'Power for the End', t_check(), kind='explain',
          icon='mekanismgenerators:fusion_reactor_controller',
          desc=['Fusion powers the Quantum Age. The Draconic reactor of the next Age powers the Stargate.']),
    ],
    supplies='quantum_supplies', comfort='quantum_comfort',
    ring=ring_quest('quantum', 8, 'the Quantum Ring', 'ad_astra:calorite_block',
                    ['Build the Quantum Ring around the Star Spire: a 21 by 21 border of calorite blocks, SPS',
                     'casings or fusion reactor frames two high and awakened draconium pylons, with the ninth plinth',
                     'in the middle of the north side.']))

# ---------------------------------------------------------------- Singularity Age
# No shrine tier: the goal waits for finale_won, which firmages-core grants when the tagged final boss dies
# (OriginService, SPEC section 16; Doc 08 section 10.4).
SINGULARITY = age_chapter(
    'singularity', 'singularity_age', 'Age 9: Singularity Age', ['Chaos, the Singularity and the gate.'],
    'sgjourney:classic_stargate', 'age_9', 10,
    entry=Q('singularity/entry', 'Welcome to the Singularity Age', t_stage('age_9'), kind='entry',
            icon='draconicevolution:chaos_shard', sub='Age 9',
            desc=['The last Age. Chaos, the Ultimate Singularity and a Stargate to The Origin.',
                  '',
                  'Three strands lead to the final battle: Chaos, Singularity and Stargate.']),
    strands=[
        ('Chaos', [
            Q('singularity/chaos/guardian', 'Chaos Guardian', t_kill('draconicevolution:draconic_guardian', value=1),
              icon='draconicevolution:chaos_shard',
              desc=['The Chaos Guardian waits on a Chaos Island in the End.']),
            Q('singularity/chaos/shards', 'Chaos Shards', t_item('draconicevolution:chaos_shard', 8),
              desc=['Chaos shards drop from the guardian\'s crystals.']),
            Q('singularity/chaos/injectors', 'Chaotic Injectors',
              t_item('draconicevolution:chaotic_crafting_injector', 4),
              desc=['Chaotic injectors fuse at the highest tier.']),
            Q('singularity/chaos/parts', 'Reactor Parts',
              [t_item('draconicevolution:reactor_stabilizer', 4, title='Reactor stabilizers'),
               t_item('draconicevolution:reactor_injector', title='Reactor injector')],
              icon='draconicevolution:reactor_stabilizer',
              desc=['Four stabilizers and an injector hold the reactor core in its field.']),
            Q('singularity/chaos/fuel', 'Reactor Fuel', t_item('draconicevolution:awakened_draconium_block', 4),
              desc=['Awakened draconium is the reactor\'s fuel.']),
        ], Q('singularity/chaos/key', 'Reactor Online',
             t_observe('draconicevolution:reactor_core', title='Look at your placed reactor core'),
             kind='keystone', icon='draconicevolution:reactor_core',
             desc=['Build the Draconic reactor and bring it online. It is the only power source for the Stargate.'])),
        ('Singularity', [
            Q('singularity/sing/relics', 'Nine Relics',
              t_observe('firmages:shrine_heart[awakened=9]', kind='block_state',
                        title='Look at the heart of the fully awakened shrine'),
              icon='firmages:shrine_heart',
              desc=['Nine relics rest on the shrine\'s plinths. Together they become the Ultimate Singularity.']),
            Q('singularity/sing/core', 'Chaotic Core', t_item('draconicevolution:chaotic_core'),
              desc=['The chaotic core is the catalyst of the Ultimate Singularity.']),
            Q('singularity/sing/alloy', 'Singular Alloy', t_item('evolvedmekanism:alloy_singular'),
              desc=['One Evolved Mekanism alloy binds the relics together.']),
        ], Q('singularity/sing/key', 'The Ultimate Singularity', t_item('firmages:ultimate_singularity'),
             kind='keystone', icon='firmages:ultimate_singularity',
             desc=['Forge the Ultimate Singularity from the nine relics, chaos shards, the singular alloy and the',
                   'chaotic core in a chaotic fusion.'])),
        ('Stargate', [
            Q('singularity/gate/naquadah', 'Naquadah', t_item('sgjourney:raw_naquadah', 16),
              desc=['Naquadah lies on Mercury. Mine it with a digital miner.']),
            Q('singularity/gate/refined', 'Refined Naquadah', t_item('sgjourney:refined_naquadah', 8),
              desc=['Refine the Naquadah in your arc furnace or Mekanism lines. EMI shows the route.']),
            Q('singularity/gate/blocks', 'Stargate Blocks',
              [t_item('sgjourney:classic_stargate_ring_block', 14, title='Ring blocks'),
               t_item('sgjourney:classic_stargate_chevron_block', 9, title='Chevron blocks'),
               t_item('sgjourney:classic_stargate_base_block', title='Base block')],
              icon='sgjourney:classic_stargate_chevron_block',
              desc=['A classic Stargate takes one base block, nine chevron blocks and fourteen ring blocks.',
                    'The Ultimate Singularity goes into the base block.']),
            Q('singularity/gate/dhd', 'Dial Home Device', t_item('sgjourney:classic_dhd'),
              desc=['The DHD dials the gate.']),
        ], Q('singularity/gate/key', 'Gate Online',
             t_observe('sgjourney:classic_stargate', title='Look at your assembled Stargate'),
             kind='keystone', icon='sgjourney:classic_stargate',
             desc=['Assemble the Stargate and power it from the Draconic reactor through a flux gate.',
                   '',
                   'The Coordinates of The Origin hold its address: 9, 16, 21, 33, 2, 37, then the point of origin.'])),
    ],
    goal=Q('singularity/goal', 'Beyond the Firmament', t_stage('finale_won', 'Defeat the final boss in The Origin'),
           kind='goal', icon='sgjourney:classic_stargate', sub='Goal of the Singularity Age: the end of the road',
           desc=['Dial The Origin and step through the gate. Hold out against the waves of the Gateway,',
                 'Maledictus and Ignis among them, then face the final boss.',
                 '',
                 'Its fall completes the Firmament and unlocks the trophies: the MekaSuit and the Meka-Tool.']),
    sides=[
        ('singularity/gate/key', [
            Q('singularity/side/origin', 'Into The Origin', t_dim('firmages:origin', 'Step through the gate into The Origin'),
              kind='side', icon='firmages:origin_coordinates',
              desc=['Dial 9, 16, 21, 33, 2, 37 and the point of origin, then step through.',
                    'Everyone gathers at the altar in the middle of The Origin; that calls the final battle.',
                    'A gate on the arena dials you home.']),
        ]),
        ('singularity/chaos/shards', [
            Q('singularity/side/armor', 'Chaotic Armor', t_item('draconicevolution:chaotic_chestpiece'), kind='side',
              desc=['The chaotic chestpiece has a direct recipe. Wear it into the final battle.']),
        ]),
        ('singularity/sing/core', [
            Q('singularity/side/staff', 'Chaotic Staff', t_item('draconicevolution:chaotic_staff'), kind='side',
              desc=['A tool for everything, built directly from the chaotic core.']),
        ]),
        ('singularity/gate/naquadah', [
            Q('singularity/side/ignis', 'Ignis', t_kill('cataclysm:ignis', value=1), kind='side',
              icon='minecraft:blaze_powder', desc=['Test yourself against Ignis before the final battle.']),
        ]),
    ],
    explains=[
        Q('singularity/info/reactor', 'The Reactor', t_check(), kind='explain', icon='draconicevolution:reactor_core',
          desc=['The Draconic reactor can explode. Watch temperature and field strength and keep the shields',
                'fed. Refuel it only while it is cold.']),
        Q('singularity/info/finale', 'After the Battle', t_check(), kind='explain', icon='mekanism:ultimate_control_circuit',
          desc=['The MekaSuit and the Meka-Tool are trophies: they unlock only after the final boss falls.']),
    ],
    supplies='singularity_supplies', comfort='singularity_comfort')

AGE_CHAPTERS = [DAWN, STONE, BRONZE, IRON, ARCANE, INDUSTRIAL, ELECTRIC, INFORMATION, SPACE, QUANTUM, SINGULARITY]

# Goal quests that grant the next Age as a quest reward. Only Dawn's First Spark: the shrine is built in the Stone
# Age. From the Stone Age on the shrine grants the Age (firmages-core reads data/firmages/firmages_shrine/
# offerings.json) and the goal is a gamestage task on the next Age without a stage reward (a second grant path
# would double the ceremony).
GOAL_GRANTS = {'dawn': 'age_0'}

# ---------------------------------------------------------------- Welcome
WELCOME = [
    Q('welcome/start', 'Welcome to Firmament Ages', t_check(), kind='info', icon='tfc:firestarter',
      sub='Read me first',
      desc=['Firmament Ages is a TerraFirmaCraft pack in Ages: Dawn, then Age 0 to Age 9.',
            'The whole server is one team with one shared progress.',
            '',
            'Press K to open this quest screen at any time. There is no quest book item.']),
    Q('welcome/ages', 'The Ages', t_check(), deps=['welcome/start'], kind='info', icon='firmages:hearthstone',
      desc=['Each Age has its own chapter. A chapter appears when your team reaches its Age.',
            'Recipes of a later Age do not exist yet, anywhere: not in the crafting grid, not in machines.',
            'EMI shows them once their Age is unlocked.',
            '',
            'Ores of later Ages look like plain stone until their Age.']),
    Q('welcome/book', 'Reading a Chapter', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:compass',
      desc=['Every Age chapter has one entry quest, three or four required strands and one goal.',
            'Each strand ends in a keystone (the larger quests). The goal needs every keystone.',
            'Round quests are side missions: optional, with a comfort reward.',
            'Diamonds mark the optional ring quest: look at the shrine heart once the Age\'s ring stands.',
            'Gears are explanations: tick them once you have read them.']),
    Q('welcome/team', 'One Team', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:player_head',
      desc=['Everybody on the server is in the same FTB team. Quest progress, Ages and rewards are shared.',
            'Split the strands between you: one friend smiths, one prospects, one builds.']),
    Q('welcome/shrine', 'The Shrine and Caelum', t_check(), deps=['welcome/ages'], kind='info',
      icon='firmages:hearthstone',
      desc=['In the Stone Age your team raises one shrine: a heart of fired clay in a circle of stone,',
            'logs and thatch. It is the hearth of the whole server and the altar of Caelum, the Firmament:',
            'the sky that watches every fire lit beneath it, and opens one layer for every gift.',
            '',
            'Every Age from the Stone Age on ends at the shrine. Add the Age\'s ring, lay its signature item',
            'on the new plinth and pray. Caelum answers with light and a word, keeps the gift as a relic',
            'and opens the next Age for everyone.']),
    Q('welcome/tfc', 'Survival Notes', t_check(), deps=['welcome/team'], kind='info', icon='tfc:ceramic/large_vessel',
      desc=['Food decays and tools wear out as in plain TerraFirmaCraft. Nothing is softened.',
            'Everything you need in quantity can be automated later. Hand work comes first.']),
]

# ---------------------------------------------------------------- The Firmament
# Every Age's goal, visible from minute one; details stay hidden until the previous goal is done (linear).
FIRMAMENT = [
    ('first_spark', 'The First Spark', 'Dawn', 'age_0', 'tfc:firestarter'),
    ('hearthstone', 'Hearthstone', 'Age 0: Stone Age', 'age_1', 'firmages:hearthstone'),
    ('sky_disc', 'Sky Disc', 'Age 1: Bronze Age', 'age_2', 'firmages:sky_disc'),
    ('steel_heart', 'Steel Heart', 'Age 2: Iron Age', 'age_3', 'firmages:steel_heart'),
    ('arcane_keystone', 'Arcane Keystone', 'Age 3: Arcane Age', 'age_4', 'firmages:arcane_keystone'),
    ('pressure_core', 'Pressure Core', 'Age 4: Industrial Age', 'age_5', 'firmages:pressure_core'),
    ('humming_core', 'Humming Core', 'Age 5: Electric Age', 'age_6', 'firmages:humming_core'),
    ('data_matrix', 'Data Matrix', 'Age 6: Information Age', 'age_7', 'firmages:data_matrix'),
    ('star_chart', 'Star Chart', 'Age 7: Space Age', 'age_8', 'firmages:star_chart'),
    ('quantum_core', 'Quantum Core', 'Age 8: Quantum Age', 'age_9', 'firmages:quantum_core'),
    ('beyond', 'Beyond the Firmament', 'Age 9: Singularity Age', 'finale_won', 'sgjourney:classic_stargate'),
]


STAGE_NAMES = {'age_0': 'Age 0: Stone Age', 'age_1': 'Age 1: Bronze Age', 'age_2': 'Age 2: Iron Age',
               'age_3': 'Age 3: Arcane Age', 'age_4': 'Age 4: Industrial Age', 'age_5': 'Age 5: Electric Age',
               'age_6': 'Age 6: Information Age', 'age_7': 'Age 7: Space Age', 'age_8': 'Age 8: Quantum Age',
               'age_9': 'Age 9: Singularity Age', 'finale_won': 'the end: defeat the final boss'}


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


SHAPES = {'entry': ('hexagon', 2.0), 'step': ('', 0.0), 'keystone': ('hexagon', 1.5), 'goal': ('gear', 3.0),
          'side': ('circle', 0.0), 'explain': ('gear', 0.0), 'info': ('', 0.0), 'ring': ('diamond', 1.25)}


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
        lang('quest', oid, 'quest_desc', quest['desc'])
    return d


def chapter_nbt(key, filename, title, subtitle, icon, group, order, quests, required_stage=None, extra=None):
    cid = qid('chapter/' + key)
    d = {'id': cid, 'filename': filename, 'group': qid('group/' + group) if group else '', 'order_index': order,
         'icon': {'id': icon}, 'default_quest_shape': 'hexagon', 'default_hide_dependency_lines': False,
         'progression_mode': 'flexible', 'quests': quests, 'quest_links': [], 'images': []}
    if required_stage:
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
    return chapter_nbt(ch['key'], ch['filename'], ch['title'], ch['subtitle'], ch['icon'], ch['group'], ch['order'],
                       quests, required_stage=age)


def build_welcome():
    quests = []
    layout = {'welcome/start': (0, 0), 'welcome/ages': (2, -1.5), 'welcome/book': (2, 0), 'welcome/team': (2, 1.5),
              'welcome/shrine': (4, -1.5), 'welcome/tfc': (4, 1.5)}
    for w in WELCOME:
        x, y = layout[w['key']]
        quests.append(quest_nbt(w, x, y, [r_xp(5)]))
    return chapter_nbt('welcome', 'welcome', 'Welcome', ['How Firmament Ages works. Press K to open this screen.'],
                       'tfc:firestarter', None, 0, quests)


def build_firmament():
    quests = []
    prev = None
    for i, (key, title, age_name, stage, icon) in enumerate(FIRMAMENT):
        final = key == 'beyond'
        q_ = Q('firmament/' + key, title, t_stage(stage, 'Unlock ' + STAGE_NAMES[stage]),
               deps=[prev] if prev else [], kind='goal' if final else 'keystone', icon=icon,
               sub=('Goal of ' + age_name) if not final else 'Goal of Age 9: defeat the final boss in The Origin',
               desc=['The signature item of %s. Details appear when the Age before it is done.' % age_name]
               if not final else ['Nine signature items, one Singularity, one gate. Beyond it waits the final boss',
                                  'of The Origin. Its fall is the end of the road.'])
        quests.append(quest_nbt(q_, i * 2.0, 0 if i % 2 == 0 else 1.0, []))
        prev = q_['key']
    return chapter_nbt('firmament', 'the_firmament', 'The Firmament',
                       ['Every Age ends with one signature item. This is the whole road, from the first spark',
                        'to the final gate.'],
                       'firmages:sky_disc', None, 1, quests,
                       extra={'progression_mode': 'linear', 'hide_quest_details_until_startable': True})


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
        'default_quest_disable_jei': False,
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
    lang('file', '0000000000000001', 'title', 'Firmament Ages')
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
    print('wrote %d chapters, %d reward tables, %d lang keys to %s' % (len(chapters), len(tables), len(LANG), OUT))


if __name__ == '__main__':
    sys.exit(main())
