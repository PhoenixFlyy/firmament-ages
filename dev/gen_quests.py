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


def r_stage(stage, interim):
    d = {'type': 'gamestage', 'stage': stage, 'auto': 'invisible'}
    if interim:
        d['tags'] = ['interim_shrine']
        d['//tags'] = 'INTERIM: shrine will grant this (firmages-core M4); dev note only, see dev/quests-notes.md'
    return d


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
}


def table_long_id(key):
    return int(qid('table/' + key), 16)


# ------------------------------------------------------------------------------------------------ content
# Quest spec: dict(key, title, sub, desc[list], tasks[list of (task, title) or ([tasks], title)], deps[keys],
#                  rewards[list], kind in entry|step|keystone|goal|side|explain|info, icon)
def Q(key, title, tasks, deps=(), desc=(), sub=None, kind='step', rewards=None, icon=None):
    return dict(key=key, title=title, tasks=tasks if isinstance(tasks, list) else [tasks], deps=list(deps),
                desc=list(desc), sub=sub, kind=kind, rewards=rewards, icon=icon)


XP = {'dawn': 10, 'age_0': 20, 'age_1': 35, 'age_2': 50}


def age_chapter(key, filename, title, subtitle, icon, stage, order, entry, strands, goal, sides, explains,
                supplies, comfort):
    """strands: list of (name, [quests], keystone-quest). sides: list of (attach_key, [quests])."""
    return dict(key=key, filename=filename, title=title, subtitle=subtitle, icon=icon, stage=stage, order=order,
                group='ages', entry=entry, strands=strands, goal=goal, sides=sides, explains=explains,
                supplies=supplies, comfort=comfort)


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
                  'Four strands lead to the Hearthstone: Fire & Clay, First Metal, Camp and the Hearth Idol.']),
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
        ('Hearth', [
            Q('stone/hearth/unfired', 'Unfired Hearth Idol', t_item('firmages:unfired_hearth_idol'),
              desc=['Knap clay into the shape of the hearth idol.']),
        ], Q('stone/hearth/key', 'Hearth Idol', t_item('firmages:hearth_idol'), kind='keystone',
             desc=['Fire the idol in a pit kiln, like any other pottery.'])),
    ],
    goal=Q('stone/goal', 'Hearthstone', t_item('firmages:hearthstone', consume=False), kind='goal',
           sub='Goal of the Stone Age: grants the Bronze Age',
           desc=['Craft the Hearthstone: the fired hearth idol, copper ingots and charcoal.',
                 '',
                 'For now this quest opens the Bronze Age. Later the Hearthstone is offered at the shrine.']),
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
    goal=Q('bronze/goal', 'Sky Disc', t_item('firmages:sky_disc', consume=False), kind='goal',
           sub='Goal of the Bronze Age: grants the Iron Age',
           desc=['Craft the Sky Disc: a bronze double sheet from the bronze anvil and gold sheets.',
                 '',
                 'For now this quest opens the Iron Age. Later the Sky Disc is offered at the shrine.']),
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
              desc=['Ring a small pool of water with flowers and throw the portal gem into it. Step through.']),
            Q('iron/front/naga', 'The Naga', t_kill('twilightforest:naga', value=1), icon='twilightforest:naga_trophy',
              desc=['Defeat the Naga in its courtyard.']),
            Q('iron/front/lich', 'The Lich', t_kill('twilightforest:lich', value=1), icon='twilightforest:lich_trophy',
              desc=['Climb the Lich tower and defeat the Lich.']),
        ], Q('iron/front/key', 'Lich Trophy', t_item('twilightforest:lich_trophy', consume=False), kind='keystone',
             desc=['The Lich drops its trophy. The Steel Heart needs it.'])),
    ],
    goal=Q('iron/goal', 'Steel Heart', t_item('firmages:steel_heart', consume=False), kind='goal',
           sub='Goal of the Iron Age: grants the Arcane Age',
           desc=['Craft the Steel Heart: a steel sheet, a precision mechanism, wrought iron double sheets',
                 'and the Lich trophy.',
                 '',
                 'For now this quest opens the Arcane Age. Later the Steel Heart is offered at the shrine.']),
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

AGE_CHAPTERS = [DAWN, STONE, BRONZE, IRON]

# Goal quests and the stage each grants. Dawn's First Spark stays a quest reward for good (the shrine is built
# in the Stone Age); the others are interim until the shrine (firmages-core M4) grants the Ages.
GOAL_GRANTS = {'dawn': ('age_0', False), 'stone': ('age_1', True), 'bronze': ('age_2', True), 'iron': ('age_3', True)}

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
            'Gears are explanations: tick them once you have read them.']),
    Q('welcome/team', 'One Team', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:player_head',
      desc=['Everybody on the server is in the same FTB team. Quest progress, Ages and rewards are shared.',
            'Split the strands between you: one friend smiths, one prospects, one builds.']),
    Q('welcome/shrine', 'The Shrine (coming)', t_check(), deps=['welcome/ages'], kind='info',
      icon='firmages:hearth_idol',
      desc=['In a later update your team builds a shrine in the Stone Age.',
            'Each Age then ends with an offering: the Age\'s signature item on the shrine.',
            'Until then, the goal quest of each Age opens the next Age directly.']),
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
    ('arcane_keystone', 'Arcane Keystone', 'Age 3: Arcane Age', 'age_4', None),
    ('pressure_core', 'Pressure Core', 'Age 4: Industrial Age', 'age_5', None),
    ('humming_core', 'Humming Core', 'Age 5: Electric Age', 'age_6', None),
    ('data_matrix', 'Data Matrix', 'Age 6: Information Age', 'age_7', None),
    ('star_chart', 'Star Chart', 'Age 7: Space Age', 'age_8', None),
    ('quantum_core', 'Quantum Core', 'Age 8: Quantum Age', 'age_9', None),
    ('beyond', 'Beyond the Firmament', 'Age 9: Singularity Age', 'finale_won', None),
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
          'side': ('circle', 0.0), 'explain': ('gear', 0.0), 'info': ('', 0.0)}


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
    if quest['kind'] in ('side', 'explain'):
        d['optional'] = True
    if quest['icon']:
        d['icon'] = {'id': quest['icon']}
    if rewards:
        d['rewards'] = build_rewards(quest, rewards)
    if quest['kind'] in ('entry', 'keystone', 'goal', 'side', 'explain'):
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
    stage, interim = GOAL_GRANTS[ch['key']]
    quests.append(quest_nbt(goal, key_x + 3.25, 0, [r_xp(xp * 10), r_stage(stage, interim)]))
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
               sub=('Goal of ' + age_name) if not final else 'Defeat the final boss beyond the Stargate',
               desc=['The signature item of %s. Details appear when the Age before it is done.' % age_name]
               if not final else ['Nine signature items, one Singularity, one gate. The end of the road.'])
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
