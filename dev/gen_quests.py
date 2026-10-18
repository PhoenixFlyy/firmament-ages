#!/usr/bin/env python3
"""Firmament Ages - generate the FTB Quests book (FTB Quests 2101.1.36, NeoForge 1.21.1).

Writes config/ftbquests/quests/: data.snbt, chapter_groups.snbt, chapters/*.snbt and lang/en_us.snbt (no reward
tables since B2: every quest pays fixed items). All player-facing text lives in lang/en_us.snbt (FTB Quests 2101 keeps titles, subtitles and
descriptions in per-locale translation tables keyed "<type>.<16-hex id>.<field>", see TranslationManager).

IDs are derived from stable keys (sha1), so re-running the generator keeps every quest id and therefore every
team's saved progress. This script is the source of the book (round G4, 2026-10-18: the freeze of 1.0.1 is lifted,
Felix's in-game edits of 1.2.0 are folded in below; he sends wishes, Claude edits here, dev/quests-editing.md).
Check the book with dev/validate_quests.py, which reads the SNBT files and does not need this script.

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
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quest_layout  # noqa: E402  (dev/quest_layout.py)
from quest_image_refs import image_line  # noqa: E402  (dev/quest_image_refs.py: pictures of dev/gen_quest_images.py)

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


def _knap_cells(rows, outside):
    return ''.join((rows[r] if r < len(rows) else '').ljust(5, outside)[:5] for r in range(5))


def _knap_pictures():
    """25-cell grid -> picture name, from the KNAPPING table of dev/gen_quest_images.py (read with ast, so the book
    generator needs no PIL)."""
    import ast
    src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_quest_images.py'), encoding='utf-8').read()
    for node in ast.parse(src).body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], 'id', None) == 'KNAPPING':
            return {_knap_cells(rows, outside): name for name, _, rows, outside in ast.literal_eval(node.value)}
    raise SystemExit('dev/gen_quest_images.py has no KNAPPING table')


KNAP_PICTURES = _knap_pictures()


def knap_grid(*rows, outside=' '):
    """TFC knapping pattern rows ('#' = stays, ' ' = knocked away) -> the picture line of that pattern
    (dev/gen_quest_images.py draws it: light squares stay, dark squares are knocked away). A pattern smaller than 5x5
    sits in the top-left corner: TFC 4.2.11 KnappingPattern.matches tries every offset and the mirror image, and every
    square outside the shape must equal the recipe's "default_on": knocked away for false (the stone heads,
    outside=' '), left standing for true (the clay ingot and pickaxe head molds, outside='#'). The rows stay here as
    the source the picture is checked against (gen_quest_images.py --check reads every knap_grid call); a pattern
    without a picture stops the generator."""
    cells = _knap_cells(rows, outside)
    if cells not in KNAP_PICTURES:
        raise SystemExit('knap_grid%r: no knapping picture in dev/gen_quest_images.py KNAPPING' % (rows,))
    return [Verbatim(image_line('knapping/' + KNAP_PICTURES[cells]))]


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


def t_structure(structure, title=None, optional=False):
    d = {'type': 'structure', 'structure': structure}
    if optional:
        d['optional_task'] = True
    return d, title


def t_stat(stat, value, title=None):
    return {'type': 'stat', 'stat': stat, 'value': value}, title


def r_xp(n):
    return {'type': 'xp', 'xp': n}


# Who receives a reward (decision log 2026-10-14, "Belohnungen: Material fürs Team, Ausrüstung pro Spieler", and B2
# 2026-10-16: fixed items in place of the random and choice tables): T = material for the shared base, claimed once
# for the fellowship (team_reward); P = gear or a consumable, each player claims their own. Every quest that asks for
# work pays items of its own Age (dev/validate_quests.py checks the Age).
# XP rule (Felix, G1 2026-10-17): a quest that pays items pays no XP; XP stays alone on the quests without items (entry,
# notes, build guides). quest_rewards() applies it, dev/validate_quests.py fails on "xp beside item rewards".
# Rules of the B2 reward audit (scratchpad b2/rewards): never a task item of an open quest of the chapter (item tasks
# and the items of the Chronicle's own advancements, CHRONICLE_ADVANCEMENTS), never a sky material, token, offering,
# Wayshrine stone or blessing shard, never the tally metal from the Industrial Age on, no second Wyvern or Draconic
# piece, nothing that fires a TFC story advancement before its quest.
def r_item(item, count=1, team=True):
    d = {'type': 'item', 'item': {'count': 1, 'id': item}, 'team_reward': bool(team)}
    if count > 1:
        d['count'] = count
    return d


def T(item, count=1):
    """Team reward: material for the shared base, one claim for the fellowship."""
    return r_item(item, count, True)


def P(item, count=1):
    """Per-player reward: gear, food or a consumable, each player claims their own."""
    return r_item(item, count, False)


def r_stage(stage):
    return {'type': 'gamestage', 'stage': stage, 'auto': 'invisible'}


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


# Keepsakes of the proof of work (B2 reward audit): one per player, and for the Industrial and Space Age a team item.
PROOF_REWARDS = {'industrial': [P('immersiveengineering:skyhook'), T('tfc:metal/sheet/steel', 8)],
                 'electric': [P('immersivepetroleum:speedboat')], 'information': [P('ae2:charged_staff')],
                 'space': [T('ad_astra:space_painting'), P('ad_astra:radio')],
                 'quantum': [P('mysticalagradditions:insanium_apple')]}


def proof_quest(chapter, age):
    """The Age's proof of work as a side quest from the entry: done when the fellowship's Tally Chutes took the count."""
    return Q('%s/side/proof' % chapter, 'Proof of Work', t_adv('firmages:proof/' + age, 'Feed %s into Tally Chutes' % tally(age)),
             kind='side', icon='firmages:tally_chute', rewards=PROOF_REWARDS[chapter],
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
                ring=None, bridge=None, routes=None, trunk=None):
    """strands: list of (name, [quests], keystone-quest). sides: list of (attach_key, [quests]).
    trunk: required steps between the entry and every strand (G4, rule C: what every strand needs first, e.g. the
    pit kiln of the Stone Age), drawn on the axis west of the orbits.
    ring: ring_spec(...) of the Age's shrine ring, folded into the goal (shrine_goal).
    bridge: a required step between one keystone (its deps) and the goal, drawn on the main route; the goal depends
    on it as well as on every keystone.
    routes: quests with their own keystone deps between the keystones and the goal; the goal then depends on the
    routes alone and needs any one of them (FTB Quests dependency_requirement one_completed): magic stays optional."""
    return dict(key=key, filename=filename, title=title, subtitle=subtitle, icon=icon, stage=stage, order=order,
                group='ages', entry=entry, strands=strands, goal=goal, sides=sides, explains=explains,
                ring=ring, bridge=bridge, routes=routes, trunk=list(trunk or []))


def ring_spec(age_index, ring_name, desc):
    """The shrine ring of an Age: age_chapter(ring=...) folds it into the goal (G4, Felix: one quest per Age for ring,
    offering and rite instead of a ring quest, a goal, a road marker and a build guide). The goal gets a block_state
    observation of the heart with ready=true and awakened=N (SPEC section 7.8: ready while the current tier's rings
    stand, awakened=N while age_N is the highest Age, so it only ticks in its own Age), an item task on the offering
    and the gamestage task on the next Age; desc is the ring's summary page (what to build, in a few lines)."""
    return dict(n=age_index, name=ring_name, desc=list(desc))


# ---------------------------------------------------------------- Dawn
# G1 (Felix 2026-10-17: Dawn was done in ten minutes): five strands for two or three players, about three to four times
# as long. Every task is reachable in Dawn (TFC 4.2.11 data, dev/age_map.toml Dawn items). New worlds start on 1 June
# (config/tfc-common.toml), and no TFC berry bush fruits before August (field guide the_world/wild_fruits), so Forage
# gathers wild crops: "mature from June to October" (field guide the_world/wild_crops), each drops one to three of its
# food and two to three seeds (melon and pumpkin one seed, no food), loot_table/blocks/wild_crop/*.json; all 29 grow in
# the wild (tags/worldgen/placed_feature/feature/crops.json).
WILD_CROPS = ['alfalfa', 'barley', 'beet', 'cabbage', 'canola', 'carrot', 'cassava', 'garlic', 'green_bean', 'jute',
              'lentil', 'maize', 'melon', 'oat', 'onion', 'papyrus', 'peanut', 'potato', 'pumpkin', 'radish',
              'red_bell_pepper', 'rice', 'rye', 'soybean', 'squash', 'sugarcane', 'tomato', 'wheat', 'yellow_bell_pepper']
WILD_CROP_FOOD = ['tfc:food/' + c for c in WILD_CROPS
                  if c not in ('alfalfa', 'canola', 'jute', 'melon', 'papyrus', 'pumpkin')]   # these drop no food
ROCK_GROUPS = ['igneous_extrusive', 'igneous_intrusive', 'metamorphic', 'sedimentary']


def _inventory(items):
    return {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': items}]}}


def _at_least_two(names):
    """Advancement requirements for "at least two of these criteria": every clause leaves one criterion out, so a single
    one never satisfies the clause that omits it (CNF of "two of n")."""
    return [[n for n in names if n != skip] for skip in names]


# The Chronicle's own advancements: a quest that needs "this and that" or "two different" of a family, which FTB
# Quests item tasks cannot ask (a quest whose tasks are all optional completes with any one of them). Hidden (no
# display), written by main() to kubejs/data/firmages/advancement/chronicle/.
CHRONICLE_ADV_DIR = os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'advancement', 'chronicle')
CHRONICLE_ADVANCEMENTS = {
    # "Knife and Hammer": a stone knife blade and a stone hammer head, any rock group each
    os.path.join(CHRONICLE_ADV_DIR, 'dawn_knife_hammer.json'): {
        'criteria': {'knife': _inventory(['tfc:stone/knife_head/' + g for g in ROCK_GROUPS]),
                     'hammer': _inventory(['tfc:stone/hammer_head/' + g for g in ROCK_GROUPS])},
        'requirements': [['knife'], ['hammer']]},
    # "Seeds of the Wild": seeds of two different wild crops
    os.path.join(CHRONICLE_ADV_DIR, 'dawn_wild_seeds.json'): {
        'criteria': {c: _inventory('tfc:seeds/' + c) for c in WILD_CROPS},
        'requirements': _at_least_two(WILD_CROPS)},
    # "Loose and Found": a loose rock of each rock family (TFC 4.2.11 tags tfc:stones/loose/<group>)
    os.path.join(CHRONICLE_ADV_DIR, 'dawn_rock_families.json'): {
        'criteria': {g: _inventory('#tfc:stones/loose/' + g) for g in ROCK_GROUPS},
        'requirements': [[g] for g in ROCK_GROUPS]},
}


def t_chronicle(name, title):
    """Task on one of CHRONICLE_ADVANCEMENTS."""
    return t_adv('firmages:chronicle/' + name, title)


DAWN = age_chapter(
    'dawn', 'dawn', 'Dawn', ['Prologue: stone, straw and the first spark.'], 'tfc:firestarter', 'dawn', 0,
    entry=Q('dawn/entry', 'Welcome to the Wild', t_stage('dawn'), kind='entry', icon='tfc:rock/loose/granite',
            sub='The prologue before the first Age',
            desc=['Begin with stone, straw and the hunt.',
                  '',
                  'Four strands lead out of Dawn: Stone, Shelter, Hunt and Sky. Share them out.',
                  'Read "Hands and Keys" in The Firmament first.',
                  '',
                  'Nights are dark. Light a fire and make torches before the first one (see "Before Dark").',
                  'When all four strands are done, the first fire opens the Stone Age for all of you.']),
    strands=[
        ('Stone', [
            Q('dawn/stone/sticks', 'A Bundle of Sticks', t_item('minecraft:stick', 8),
              desc=['Pick up twigs under trees with an empty hand; each makes a stick on the ground.',
                    'Leaves cut with a stone knife drop sticks too.'],
              rewards=[T('tfc:jute_fiber', 4)]),
            # G4 (rule C): the knife comes before the axe head, so "Thatch" (straw needs the knife) can wait for it.
            Q('dawn/stone/kit', 'Knife and Hammer',
              t_chronicle('dawn_knife_hammer', 'A stone knife blade and a stone hammer head'),
              icon='tfc:stone/knife_head/igneous_extrusive',
              desc=['Knap a knife blade and a hammer head (see "Knapping Patterns") and craft each with a stick.',
                    'The knife cuts straw; the hammer splits logs.'],
              rewards=[T('minecraft:stick', 8), P('tfc:food/cooked_mutton', 2)]),
            # Patterns: TFC 4.2.11 data/tfc/recipe/knapping/stone/<tool>_head/<rock group>[_n].json, identical for all
            # four rock groups (igneous_extrusive, igneous_intrusive, metamorphic, sedimentary); checked against the
            # jar on 2026-10-14. Axe: tfc:knapping/stone/axe_head/<group>. Knapping type tfc:rock: input 2 of
            # #tfc:rock_knapping, amount_to_consume 1, consume_after_complete false (the first click uses the rock).
            # Shaft: tfc:crafting/stone/axe/<group>, head above #c:rods/wooden (sticks and twigs) or a bone.
            # G4 (Felix): the quest checks the head, not the axe (the axe comes with "Stone Kit"), so the task is the
            # knapped head of any rock group (either/or: no required task beside it) and the icon is a head.
            Q('dawn/stone/tool', 'Stone Tools',
              t_any_item(['tfc:stone/axe_head/' + g for g in ROCK_GROUPS], 'Knap a stone axe head'),
              desc=['Right-click loose rocks with an empty hand; gather about ten of one kind.',
                    '',
                    'Hold two or more and right-click into the air. A 5x5 grid of stone opens.',
                    '- Click a square to knock it away. It does not come back.',
                    '- The first click uses one rock. Closing the grid early wastes it.',
                    '- When only the shape is left, take the head from the slot on the right.',
                    '',
                    'The axe head (light squares stay, dark squares are knocked away):',
                    *knap_grid(' #   ', '#### ', '#####', '#### ', ' #   '),
                    '',
                    'Craft the head with a stick, a twig or a bone on the ground (see "Hands and Keys"): a stone axe.',
                    'The knife and the other heads: "Knapping Patterns".'],
              icon='tfc:stone/axe_head/sedimentary',
              rewards=[P('tfc:food/cooked_beef', 4), P('tfc:stone/shovel/sedimentary')]),
            # Knife: tfc:knapping/stone/knife_head/<group>_1, hammer: hammer_head/<group>; assembled like the axe
            # (tfc:crafting/stone/knife/<group>, hammer/<group>). CHRONICLE_ADVANCEMENTS dawn_knife_hammer.
        ], Q('dawn/stone/key', 'Stone Kit', t_adv('tfc:story/logging', 'Fell a tree with a stone axe'),
             kind='keystone', icon='tfc:stone/axe/igneous_extrusive',
             desc=['Fell a tree with your stone axe. With axe, knife, hammer and shovel you have a full stone kit.'],
             rewards=[T('minecraft:stick', 16), P('tfc:food/cooked_rabbit', 3)])),
        ('Shelter', [
            # tfc:crafting/thatch: four straw in a 2x2 (ground craft: four straw on one block). Straw: one per grass
            # block cut with #tfc:tools/sharp (loot_table/blocks/plant/<grass>.json). 32 thatch = 128 straw: about ten
            # minutes of cutting, two stone knives and the ground crafts for one player.
            # G4 (Felix, rule C): straw needs the knife, so the strand starts after "Knife and Hammer".
            Q('dawn/shelter/thatch', 'Thatch', [t_adv('tfc:story/get_straw', 'Cut straw'), t_item('tfc:thatch', 8)],
              icon='tfc:thatch', deps=['dawn/stone/kit'],
              desc=['Cut tall grass with a stone knife for straw. Lay four on one block, sneak and right-click the',
                    "pile: thatch, Dawn's one building block."],
              rewards=[T('tfc:thatch', 28)]),
            Q('dawn/shelter/walls', 'Walls of Straw', t_item('tfc:thatch', 32), icon='tfc:thatch',
              desc=['Make 32 thatch, enough for walls and a roof. A hut of straw keeps the night out.'],
              rewards=[T('tfc:straw', 16), P('tfc:food/cooked_pork', 2)]),
            # Felix 2026-10-18: the hunt for the bed's large hide sits between the walls and the bed.
            Q('dawn/hunt/kill', 'Hunter',
              [t_kill(tag='tfc:animals', value=3, title='Kill three wild animals'),
               t_item('tfc:large_raw_hide', title='A large raw hide')],
              icon='tfc:large_raw_hide',
              desc=['Hunt three animals. Large prey drops the large hide that makes the thatch bed.'],
              rewards=[P('tfc:food/cooked_pork', 4), T('tfc:rope', 2)]),
        ], Q('dawn/shelter/key', 'Shelter', t_observe('tfc:thatch_bed', title='Look at a finished thatch bed'),
             kind='keystone', icon='tfc:thatch_bed',
             desc=['Right-click two thatch blocks side by side with a large raw hide: a thatch bed.',
                   'It sets your spawn but does not skip the night.'],
             rewards=[T('tfc:thatch', 4), T('tfc:rope', 2)])),
        ('Hunt', [], Q('dawn/hunt/javelin', 'A Stone Javelin',
              t_any_item(['tfc:stone/javelin/' + g for g in ROCK_GROUPS], 'Any stone javelin'),
              desc=['Knap a javelin head (see "Knapping Patterns") and craft it with a stick on the ground.',
                    'Throw it or stab with it.'],
              kind='keystone', icon='tfc:stone/javelin/sedimentary',
              rewards=[P('tfc:food/cooked_venison', 2)])),
        ('Sky', [
            # Vanilla stat walk_one_cm: walking on the ground, not sprinting, crouching, swimming or riding
            # (Player.checkMovementStatistics). FTB Quests StatTask checks it every three ticks.
            Q('dawn/sky/walk', 'And I would walk 500 miles', t_stat('minecraft:walk_one_cm', 50000, 'Walk 500 blocks'),
              icon='minecraft:leather_boots',
              desc=['Walk 500 blocks in all; sprinting does not count. Keep your eyes on the land as you go.'],
              rewards=[P('tfc:food/cooked_beef', 3)]),
        ], # The tutorial Wayshrine (firmages-core SPEC 28.4a): a forest_ring ruin placed by code 300 to 800 blocks from
           # the world spawn on a new world, every block a Dawn block, the ruin's faint beam drawn from the start (28.8).
           # The core (firmages:wayshrine_core) has no item, so the task observes the block.
           Q('dawn/sky/key', 'A Shrine on the Road', t_observe('firmages:wayshrine_core', title='Look at the core of a ruined shrine'),
             kind='keystone', icon='firmages:wayshrine_core',
             desc=['Find the ruined shrine of Caelum that stands 300 to 800 blocks from where you woke. A faint beam',
                   'rises from it. Look at its core.'],
             rewards=[P('tfc:food/cooked_mutton', 4), T('tfc:rope', 4)])),
    ],
    goal=Q('dawn/goal', 'The First Spark',
           [t_item('tfc:firestarter', title='A firestarter'), t_adv('tfc:story/firepit', 'Light a firepit')],
           kind='goal', icon='tfc:firestarter', sub='Goal of Dawn: grants the Stone Age',
           desc=['Lay two sticks on one block and sneak and right-click the pile: a firestarter. Throw a log, three',
                 'sticks and some straw on one block and hold right-click on it with the firestarter until the',
                 'firepit lights.',
                 '',
                 'To relight a burnt-out firepit, put a log in its top fuel slot and use the firestarter on it.',
                 '',
                 'Fire opens the Stone Age for all of you.'],
           rewards=[T('minecraft:oak_log', 8), T('tfc:straw', 16), T('tfc:rope', 4), P('tfc:food/cooked_venison', 4)]),
    sides=[
        # Forage (Felix 2026-10-18): an optional chain from the entry; nothing required waits for it.
        ('dawn/entry', [
            Q('dawn/forage/harvest', 'Wild Harvest', t_any_item(WILD_CROP_FOOD, 'Food from any wild crop'),
              kind='side', icon='tfc:food/carrot',
              desc=['Gather the food of a wild crop. Wild grain and roots ripen in summer, hidden in the tall grass.'],
              rewards=[P('tfc:food/cooked_chicken', 2)]),
            Q('dawn/forage/seeds', 'Seeds of the Wild', t_chronicle('dawn_wild_seeds', 'Seeds of two different wild crops'),
              kind='side', icon='tfc:seeds/barley',
              desc=['Break wild crops for their seeds. Seeds of two different crops are the start of a field.'],
              rewards=[P('tfc:food/cooked_pork', 3)]),
            Q('dawn/forage/key', 'The Larder', t_any_item(WILD_CROP_FOOD, 'Twelve of one wild crop', count=12),
             kind='side', icon='tfc:food/potato',
             desc=["Gather twelve of one wild crop's food. A full larder keeps the fellowship fed."],
             rewards=[P('tfc:food/cooked_venison', 4), T('tfc:jute_fiber', 8)]),
        ]),
        # The first night (More Darkness, no skipping bed): the firepit advancement also counts for the goal, which
        # still waits for the four keystones, so lighting it early changes nothing about the Age grant.
        ('dawn/stone/key', [
            Q('dawn/side/torch', 'Before Dark', t_item('tfc:torch', title='A torch'), kind='side', icon='tfc:torch',
              desc=[Verbatim(image_line('device/firepit')), '',
                    'Drop one log, three sticks and some straw on one block. Hold right-click on it with a firestarter',
                    '(two sticks) until the firepit lights.',
                    '',
                    'Heat a stick in the firepit\'s right slot: it becomes two torches. A torch lights your way in hand;',
                    'a placed torch burns about 36 minutes. Zombies and spiders walk at night.'],
              rewards=[P('tfc:food/cooked_chicken', 4)]),
        ]),
        # Hides: rabbits drop a small hide (half the time), deer a medium one, the large beasts a large one
        # (loot_table/entities/*.json). Bones: deer one to six, rabbits one, skeletons at night.
        ('dawn/hunt/kill', [
            Q('dawn/side/hides', 'Hides',
              t_any_item(['tfc:small_raw_hide', 'tfc:medium_raw_hide', 'tfc:large_raw_hide'],
                         'Three raw hides of one size', count=3),
              kind='side', icon='tfc:medium_raw_hide',
              desc=['Bring home three raw hides of one size. Small, medium and large prey each give their own.'],
              rewards=[T('tfc:jute_fiber', 6)]),
        ]),
        ('dawn/hunt/kill', [
            Q('dawn/side/bones', 'Bones', t_item('minecraft:bone', 8), kind='side', icon='minecraft:bone',
              desc=['Gather eight bones from your kills. A bone serves as a tool shaft where sticks run short.'],
              rewards=[T('tfc:rope', 2)]),
        ]),
        ('dawn/sky/walk', [
            Q('dawn/side/rocks', 'Loose and Found',
              t_chronicle('dawn_rock_families', 'A loose rock of each family'), kind='side',
              icon='tfc:rock/loose/basalt',
              desc=['Gather a loose rock of each family: igneous extrusive, igneous intrusive, metamorphic and',
                    'sedimentary. Hover over a rock to read its family.'],
              rewards=[P('tfc:food/cooked_beef', 2)]),
        ]),
    ],
    explains=[
        # Patterns: tfc:knapping/stone/knife_head/<group>_1 (two blades from one rock), hammer_head/<group>,
        # shovel_head/<group>, hoe_head/<group>_1 (two heads), javelin_head/<group> (TFC 4.2.11, all four rock
        # groups alike). Hammer, shovel and the single hoe are "default_on": false: the rest of the grid must be
        # knocked away; any offset and the mirror image match.
        Q('dawn/info/knapping', 'Knapping Patterns', t_check(), kind='explain',
          icon='tfc:stone/knife_head/sedimentary',
          desc=['Every head is laid with a stick on one block and crafted on the ground (see "Hands and Keys").',
                'Light squares stay, dark squares are knocked away.',
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
    )

# ---------------------------------------------------------------- Stone Age
STONE = age_chapter(
    'stone', 'stone_age', 'Stone Age', ['Clay, pit kilns and the first cast copper.'], 'firmages:hearthstone',
    'age_0', 1,
    entry=Q('stone/entry', 'The Stone Age Dawns', t_stage('age_0'), kind='entry', icon='tfc:ceramic/vessel',
            sub='The first Age',
            desc=['Fire is tamed. Clay follows, and with it pottery, molds and copper.',
                  '',
                  'Four strands lead to the Hearthstone: Fire & Clay, First Metal, Camp, and Hearth & Shrine.',
                  'The last one raises the shrine where the Hearthstone is offered to Caelum.']),
    # G4 (rule C): clay, the pit kiln and the molds gate every strand (the vessel melts the copper, the jug and the
    # idol are fired), so they form the trunk; the copper kit (with the saw) gates the shrine's 3x3 recipes.
    trunk=[
            Q('stone/clay/find', 'Locating Clay', t_adv('tfc:story/find_clay'),
              desc=['Clay lies under grass near water. Athyrium fern, canna, goldenrod, pampas grass, perovskia and',
                    'water canna grow above it.'],
              icon='minecraft:clay_ball',
              rewards=[T('minecraft:clay_ball', 16)]),
            # TFC 4.2.11 pit kiln (field guide getting_started/pit_kiln, ServerConfig pitKilnTicks 7200 = 6 min,
            # pitKilnTemperature 1400): up to four items set down with the place-block key (tfc.key.place_block = V in
            # config/defaultoptions/keybindings.txt), eight straw (tfc:pit_kiln_straw) or two thatch, eight logs.
            Q('stone/clay/kiln', 'Potter', [t_adv('tfc:story/knap_clay', 'Knap a clay piece'),
                                            t_adv('tfc:story/pit_kiln', 'Fire it in a pit kiln')],
              icon='tfc:ceramic/unfired_vessel',
              desc=[Verbatim(image_line('device/pit_kiln')), '',
                    'Knap five clay into a vessel, a jug, a bowl or a mold (see "Clay Patterns"). Fire it in a pit kiln:',
                    '- Dig a hole one block deep. Hold an unfired piece, look at the floor of the hole and press V.',
                    'Up to four pieces fit.',
                    '- Right-click the pile with eight straw, then with eight logs.',
                    '- Light the top with a firestarter.',
                    '',
                    'After about six minutes the fire is out and the pottery is fired.'],
              rewards=[T('minecraft:clay_ball', 16), T('tfc:straw', 16)]),
            Q('stone/clay/key', 'Pottery for Metal',
              [t_item('tfc:ceramic/vessel', title='Fired small vessel'),
               t_item('tfc:ceramic/ingot_mold', title='Fired ingot mold'),
               t_item('tfc:ceramic/pickaxe_head_mold', title='Fired pickaxe head mold')],
              icon='tfc:ceramic/ingot_mold',
              desc=['Fire a small vessel, an ingot mold and a pickaxe head mold in one burn. The vessel melts ore,',
                    'the molds take the liquid copper.'],
              rewards=[T('tfc:ceramic/axe_head_mold'), T('tfc:ceramic/hammer_head_mold'), T('tfc:ceramic/knife_blade_mold'), T('tfc:ceramic/shovel_head_mold'), T('tfc:straw', 8)]),
    ],
    strands=[
        ('First Metal', [
            Q('stone/metal/ore', 'Native Copper',
              t_any_item(['tfc:ore/small_native_copper', 'tfc:ore/small_malachite', 'tfc:ore/small_tetrahedrite'],
                         'Any small copper ore piece'),
              desc=['Copper ore pieces lie on the surface above copper veins.'], icon='tfc:ore/small_native_copper',
              rewards=[P('tfc:torch', 4)]),
            # TFC 4.2.11 charcoal pit (field guide metalworking/charcoal_pit): sneak + right-click with a log places a
            # log pile (InteractionManager checks isShiftKeyDown), light it, then close it with non-flammable blocks.
            Q('stone/metal/charcoal', 'A Better Fuel',
              [t_adv('tfc:story/charcoal', 'Dig out charcoal'), t_item('minecraft:charcoal', 32, title='Keep 32 charcoal')],
              desc=[Verbatim(image_line('device/log_pile')), Verbatim(image_line('device/charcoal_pit')), '',
                    'Make 32 charcoal in a charcoal pit; a bigger pile burns in the same time:',
                    '- Sneak and right-click the ground with logs: a log pile. Right-click it with more logs.',
                    '- Cover every side with dirt, leave the top open and light it with a firestarter.',
                    '- Close the gap with dirt.',
                    '',
                    'When the smoke stops, dig the charcoal out with a shovel. The forge, the casting and the Hearthstone',
                    'burn it.'],
              icon='minecraft:charcoal',
              rewards=[T('minecraft:charcoal', 8)]),
            # Casting: field guide getting_started/finding_ores (casting interface, mold in the slot, right-click the
            # cooled mold to take the item out); small ore 10 mB, ingot 100 mB.
            Q('stone/metal/ingot', 'Copper Ingot', t_item('tfc:metal/ingot/copper', 2),
              desc=['Take the vessel out while it glows and right-click it. Put a fired mold in the slot; the copper',
                    'pours in. Ten small pieces make one ingot.',
                    '',
                    'When the mold has cooled, right-click it to take the ingot out.'],
              rewards=[T('tfc:ore/small_native_copper', 5)]),
            Q('stone/metal/pickaxe', 'Copper Pickaxe', t_item('tfc:metal/pickaxe/copper'),
              desc=['Right-click the glowing vessel, the pickaxe head mold in the slot. The cooled head on a stick is',
                    'your first pickaxe.'],
              rewards=[T('tfc:metal/ingot/copper', 2)]),
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
                    'Four loose rocks in the 2x2 grid make a cobblestone. The shrine takes many.'],
              rewards=[T('tfc:straw', 8), P('tfc:torch', 4)]),
            # Patterns: TFC 4.2.11 data/tfc/recipe/knapping/ceramic/unfired_axe_head_mold.json and
            # unfired_hammer_head_mold.json (no "default_on": the rest of the grid is knocked away, like the stone heads).
        ], Q('stone/metal/key', 'Copper Kit',
             [t_item('tfc:metal/axe/copper', title='Copper axe'), t_item('tfc:metal/hammer/copper', title='Copper hammer'),
              t_item('tfc:metal/knife/copper', title='Copper knife'), t_item('tfc:metal/shovel/copper', title='Copper shovel'),
              t_item('tfc:metal/saw/copper', title='Copper saw')],
             kind='keystone', icon='tfc:metal/axe/copper',
             desc=['Cast axe, hammer, knife, shovel and saw heads in their molds and haft them: about 50 small ore',
                   'pieces. The Bronze Age smiths with this kit.',
                   '',
                   'Axe, hammer, knife and shovel molds:',
                   *knap_grid('# ###', '    #', '     ', '    #', '# ###'),
                   *knap_grid('#####', '     ', '     ', '## ##', '#####'),
                   *knap_grid('## #', '#  #', '#  #', '#  #', '#  #', outside='#'),
                   *knap_grid('#   #', '#   #', '#   #', '#   #', '## ##')],
             rewards=[T('tfc:ore/small_native_copper', 16), P('tfc:metal/javelin/copper', 2)])),
        ('Camp', [
            Q('stone/camp/seeds', 'Gatherer', t_adv('tfc:world/seeds'),
              desc=['Break a wild crop to get its seeds.'], icon='tfc:seeds/wheat',
              rewards=[T('tfc:seeds/wheat', 4), T('tfc:seeds/carrot', 4)]),
            Q('stone/camp/field', 'First Field', t_observe('tfc:farmlands', kind='block_tag',
                                                           title='Look at your farmland'),
              desc=['Till soil with a hoe and plant the seeds. Crops need water and time.'],
              icon='tfc:seeds/wheat',
              rewards=[P('tfc:metal/hoe/copper')]),
            Q('stone/camp/pot', 'A Pot of Soup', [t_item('tfc:ceramic/jug', title='A fired jug'), t_adv('tfc:story/pot')],
              desc=['Fire a jug and fill it at a river. Put a fired ceramic pot on the firepit, pour the water in and',
                    'cook a soup.'], icon='tfc:ceramic/jug',
              rewards=[P('tfc:food/wheat_bread', 4), T('tfc:ceramic/bowl', 4)]),
        ], Q('stone/camp/key', 'Stocked Camp', t_item('tfc:ceramic/large_vessel'), kind='keystone',
             desc=['Fill a large vessel with food and seal it. Sealed, food rots far slower.'],
             rewards=[T('tfc:wool_cloth', 4), P('tfc:food/cooked_beef', 4)])),
        ('Hearth & Shrine', [
            # Pattern: kubejs/server_scripts/recipes/age_0/stone_age.js, firmages:knapping/unfired_hearth_idol.
            Q('stone/hearth/idol', 'Hearth Idol', t_item('firmages:hearth_idol', 2),
              desc=['Knap two, five clay each, and fire them in a pit kiln. One becomes the heart of the shrine, the',
                    'other the Hearthstone.',
                    '',
                    *knap_grid(' ### ', '#####', ' ### ', ' ### ', '## ##')],
              rewards=[T('tfc:thatch', 4), T('minecraft:clay_ball', 10)]),
            # TFC 4.2.11: saw = blade over a stick (tfc:crafting/metal/saw/copper). Wood classes (HW1A,
            # recipes/age_0/wood_classes.js): saw + vanilla log = 2 planks, 4 vanilla planks = crafting table (in the
            # block tag tfc:workbenches, dev/gen_wood_classes.py), saw + planks = 4 lumber of the class's lead species.
            # 4 loose rocks = cobblestone (rock/cobble/<rock>). The shrine recipes below are 3x3
            # (kubejs/server_scripts/recipes/age_0/stone_age.js).
        ], # G4 (Felix): "Raise the Shrine" went into the goal; the heart is the strand's keystone. Its 3x3 recipe
           # needs the workbench, which the saw of the copper kit builds (rule C): it waits for "Copper Kit" too.
           Q('stone/shrine/heart', 'The Shrine Heart',
             t_observe('firmages:shrine_heart', title='Look at your placed Shrine Heart'), kind='keystone',
             icon='firmages:shrine_heart', deps=['stone/hearth/idol', 'stone/metal/key'],
             desc=['Craft it on the workbench from a Hearth Idol (H), cobblestone (S) and charcoal (C):',
                   *craft_grid(' C ', 'SHS', ' S '),
                   '',
                   'Leave 21 by 21 blocks free: each Age adds a ring.'],
             rewards=[T('firmages:lantern/stone', 4), P('tfc:torch', 4)])),
    ],
    goal=Q('stone/goal', 'Offer the Hearthstone at the Shrine',
           t_stage('age_1', 'Offer the Hearthstone at the shrine'), kind='goal', icon='firmages:hearthstone',
           sub='Goal of the Stone Age: Caelum opens the Bronze Age',
           desc=['Craft the Hearthstone from the fired Hearth Idol (H), copper ingots (K) and charcoal (C):',
                 *craft_grid(' K ', 'CHC', ' K ')],
           rewards=[P('tfc:metal/sword/copper'), P('tfc:metal/shield/copper'), T('tfc:metal/ingot/copper', 4), T('firmages:lantern/stone', 2)]),
    sides=[
        ('stone/clay/kiln', [
            Q('stone/side/candle', 'Tallow and Candles', t_item('tfc:candle', 4), kind='side',
              desc=['Render fat into tallow and make candles. Light without fire hazards.'],
              rewards=[P('firmages:lantern/stone', 2)]),
        ]),
        ('stone/camp/seeds', [
            Q('stone/side/friend', 'A New Friend', t_adv('tfc:world/familiarity'), kind='side',
              icon='minecraft:lead',
              desc=['Feed a wild animal to familiarize it. Tame animals are the start of a farm.'],
              rewards=[P('minecraft:lead', 2), P('minecraft:name_tag')]),
        ]),
    ],
    explains=[
        Q('stone/info/food', 'Food Decay and Nutrition', t_check(), kind='explain', icon='tfc:ceramic/large_vessel',
          desc=['Food rots. Sealed large vessels, salting, drying and cooking slow it down.',
                'Eat from every food group: grain, fruit, vegetables, protein and dairy.']),
        Q('stone/info/heat', 'Heat Colors', t_check(), kind='explain', icon='minecraft:charcoal',
          desc=['Hot items glow. Hover over one to read its heat; each metal melts at its own.']),
        Q('stone/info/cavein', 'Cave-ins', t_check(), kind='explain', icon='tfc:rock/loose/granite',
          desc=['Mining raw stone can collapse the ceiling.']),
        # Patterns: TFC 4.2.11 data/tfc/recipe/knapping/ceramic/unfired_<name>.json (knapping type tfc:clay, five clay,
        # used up when the shape is taken). Ingot and pickaxe head molds are "default_on": true: the squares outside
        # their 4x5 and 5x4 shapes stay standing. The saw blade mold is a full 5x5 shape.
        Q('stone/info/clay', 'Clay Patterns', t_check(), kind='explain', icon='tfc:ceramic/unfired_vessel',
          desc=['Hold five clay and right-click into the air. Knock squares away as with stone.',
                'Light squares stay, dark squares are knocked away. Mirrored shapes work too.',
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
    ring=ring_spec(0, 'the Hearth Circle',
                   ['Build the Hearth Circle around the heart: eight cobblestone, four log posts two high with thatch',
                    'on top, and the Offering Plinth two blocks from the heart. Craft the plinth from four',
                    'cobblestone (S):',
                    *craft_grid(' S ', 'SSS'),
                    '',
                    'When the circle is whole, the heart glows. Right-click it again to hear what Caelum asks for.']),
    )

# ---------------------------------------------------------------- Bronze Age
BRONZE = age_chapter(
    'bronze', 'bronze_age', 'Bronze Age', ['The first alloys. New veins wake.'], 'firmages:sky_disc',
    'age_1', 2,
    entry=Q('bronze/entry', 'The Bronze Age Dawns', t_stage('age_1'), kind='entry',
            icon='tfc:metal/ingot/bronze', sub='The second Age',
            desc=['Tin and zinc veins wake. Anvils, alloys and the first kinetic machines follow.',
                  '',
                  'Four strands lead to the Sky Disc: Smithing, Prospecting, Alloys and Kinetics.']),
    strands=[
        ('Smithing', [
            # TFC 4.2.11 charcoal forge (field guide metalworking/charcoal_forge): a charcoal pile of 7 or 8 layers,
            # five stone blocks around it (four sides and below), air above, sky in view; charcoal places a pile layer
            # on right-click (no sneak).
            Q('bronze/smith/forge', 'Forging', [t_adv('tfc:story/forge', 'Light a charcoal forge'),
                                                t_adv('tfc:story/stone_anvil', 'Make a stone anvil')],
              desc=[Verbatim(image_line('device/forge_anvil')), '',
                    'Build a charcoal forge:',
                    '- Make a hole one block deep, lined with stone on its four sides and below.',
                    '- Right-click into it with charcoal until the pile is seven or eight layers high.',
                    '- Keep the sky open above it and light it with a firestarter.',
                    '',
                    'Right-click the forge: fuel goes below, the metal to heat above. Right-click raw igneous rock with',
                    'a stone hammer: a stone anvil.'], icon='minecraft:charcoal',
              rewards=[T('minecraft:charcoal', 16), P('tfc:metal/hammer/copper')]),
            Q('bronze/smith/flux', 'In Flux', [t_adv('tfc:story/quern', 'Craft a quern'), t_item('tfc:powder/flux', 4)],
              icon='tfc:powder/flux',
              desc=[Verbatim(image_line('device/quern')), '',
                    'Craft a quern and a handstone. Grind borax, lime or other flux stones in it. Welding needs flux.'],
              rewards=[T('tfc:metal/ingot/copper'), T('tfc:ore/small_native_copper', 10)]),
            Q('bronze/smith/weld', 'First Weld', t_adv('tfc:story/welding'),
              desc=['Weld two hot ingots into a double ingot on the stone anvil.'],
              icon='tfc:metal/double_ingot/copper',
              rewards=[T('tfc:metal/ingot/copper', 2)]),
        ], Q('bronze/smith/key', 'Copper Anvil', t_item('tfc:metal/anvil/copper'), kind='keystone',
             desc=['Weld copper double ingots into a copper anvil. Tools and sheets are smithed on it.'],
             rewards=[T('tfc:metal/ingot/copper', 6), T('minecraft:charcoal', 16)])),
        # G4 (rule C): the surface ore quests moved to the strands that need them (tin: Alloys, zinc: Kinetics), so
        # no strand waits on another. The propick is cast in its mold (TFC 4.2.11 ceramic/propick_head_mold), any
        # copper or bronze; the mineral prospector of the Side Roads is mentioned, not asked.
        ('Prospecting', [
            Q('bronze/prosp/propick', 'Prospector',
              t_any_item(['tfc:metal/propick/copper', 'tfc:metal/propick/bronze', 'tfc:metal/propick/bismuth_bronze',
                          'tfc:metal/propick/black_bronze'], 'A prospector\'s pick'),
              desc=['Cast a propick head in its mold and haft it. Right-click stone with it: it tells what ore lies',
                    'within a few blocks.'],
              icon='tfc:metal/propick/copper',
              rewards=[T('precisionprospecting:ceramic/mineral_prospector_head_mold'), T('tfc:ore/small_malachite', 8)]),
            Q('bronze/prosp/support', 'Hold the Ceiling',
              t_observe('tfc:support_beams', kind='block_tag', title='Look at a placed support beam'),
              icon='tfc:wood/support/oak',
              desc=['Saw support beams and place them in your mine. They stop cave-ins nearby.'],
              rewards=[P('tfc:torch', 8)]),
        ], Q('bronze/prosp/key', 'First Mine',
             [t_item('tfc:metal/ingot/tin', 16, title='Tin ingots'), t_item('tfc:metal/ingot/zinc', 8, title='Zinc ingots')],
             kind='keystone', icon='tfc:metal/ingot/tin',
             desc=['Mine the tin and zinc veins you found and cast 16 tin and eight zinc: enough for the bronze',
                   'anvil, the Sanctum and the brass of the Iron Age.'],
             rewards=[T('tfc:metal/ingot/copper', 4), T('minecraft:charcoal', 16)])),
        ('Alloys', [
            Q('bronze/alloy/fireclay', 'Fireproof', t_adv('tfc:story/fire_clay'),
              desc=['Mix clay with kaolinite and graphite powder into fire clay.'], icon='tfc:fire_clay',
              rewards=[T('tfc:powder/kaolinite', 4), T('tfc:powder/graphite', 4)]),
            Q('bronze/prosp/tin', 'Tin on the Surface', t_item('tfc:ore/small_cassiterite'),
              desc=['Cassiterite pieces on the ground point to a tin vein below. Ten pieces make an ingot in the vessel.'],
              rewards=[T('tfc:metal/ingot/copper', 2)]),
            Q('bronze/alloy/ingot', 'Bronze',
              [t_item('tfc:crucible', title='A fired crucible'), t_item('tfc:metal/ingot/bronze', title='A bronze ingot')],
              icon='tfc:metal/ingot/bronze',
              desc=['Knap and fire a crucible. Melt copper with tin in it, nine parts to one: bronze. Bismuth and',
                    'black bronze serve for tools too.'],
              rewards=[T('tfc:metal/ingot/bronze', 2), T('tfc:metal/ingot/copper', 4)]),
            Q('bronze/alloy/tool', 'First Bronze Tool', t_adv('tfc:story/bronze_age'),
              desc=['Smith any bronze tool.'], icon='tfc:metal/axe/bronze',
              rewards=[T('tfc:metal/ingot/bronze', 2)]),
            Q('bronze/alloy/kit', 'Bronze Kit',
              [t_item('tfc:metal/pickaxe/bronze', title='Bronze pickaxe'), t_item('tfc:metal/axe/bronze', title='Bronze axe'),
               t_item('tfc:metal/saw/bronze', title='Bronze saw'), t_item('tfc:metal/hammer/bronze', title='Bronze hammer'),
               t_item('tfc:metal/chisel/bronze', title='Bronze chisel')],
              icon='tfc:metal/pickaxe/bronze',
              desc=['Smith a bronze pickaxe, axe, saw, hammer and chisel. Bronze is a working metal, not a trophy;',
                    'every one of you needs the kit.'],
              rewards=[P('tfc:metal/javelin/bronze')]),
        ], Q('bronze/alloy/key', 'Bronze Anvil',
             t_any_item(['tfc:metal/anvil/bronze', 'tfc:metal/anvil/bismuth_bronze', 'tfc:metal/anvil/black_bronze'],
                        'Any bronze anvil'),
             kind='keystone', icon='tfc:metal/anvil/bronze',
             desc=['A bronze anvil forges bronze double sheets: the Sky Disc needs one.'],
             rewards=[T('tfc:metal/ingot/bronze', 6), P('tfc:metal/hammer/bronze')])),
        ('Kinetics', [
            Q('bronze/prosp/zinc', 'Zinc on the Surface', t_item('tfc:ore/small_sphalerite'),
              desc=['Sphalerite pieces point to a zinc vein. Kinetic machines need zinc.'],
              rewards=[P('tfc:torch', 8)]),
            Q('bronze/kin/alloy', 'Andesite Alloy', t_item('create:andesite_alloy', 16),
              desc=['Make 16 andesite alloy from andesite cobble and zinc nuggets, nine from an ingot. None nearby?',
                    'Travel: the rock changes every few hundred blocks.'],
              rewards=[T('create:andesite_casing', 2)]),
            Q('bronze/kin/wheel', 'Water Wheel', t_item('create:water_wheel'),
              desc=['A water wheel in flowing water gives rotational force.'],
              rewards=[T('create:andesite_alloy', 4)]),
            Q('bronze/kin/saw', 'Mechanical Saw',
              [t_item('create:shaft', 8, title='Shafts'), t_item('create:cogwheel', 4, title='Cogwheels'),
               t_item('create:mechanical_saw', title='A mechanical saw')],
              icon='create:mechanical_saw',
              desc=['Carry the rotation on shafts and cogwheels to a mechanical saw. It cuts logs into planks and',
                    'lumber without a hand saw.'],
              rewards=[T('create:andesite_alloy', 8), T('create:large_cogwheel', 2)]),
        ], Q('bronze/kin/key', 'Flux Mill',
             [t_observe('create:millstone', title='Look at a placed millstone'),
              t_observe('create:water_wheel', title='Look at a placed water wheel'),
              t_item('tfc:powder/flux', 32, title='Grind 32 flux'), t_item('create:chute', title='A chute')],
             kind='keystone', icon='create:millstone',
             desc=['Power a millstone from a water wheel and grind 32 flux in it. A chute under the mill drops the',
                   'flux into a vessel: your first running line.'],
             rewards=[T('create:encased_fan', 2), P('create:wrench'), P('create:goggles')])),
    ],
    goal=Q('bronze/goal', 'Offer the Sky Disc at the Shrine', t_stage('age_2', 'Offer the Sky Disc at the shrine'),
           kind='goal', icon='firmages:sky_disc', sub='Goal of the Bronze Age: Caelum opens the Iron Age',
           desc=['Craft the Sky Disc from a bronze double sheet and gold sheets.'],
           rewards=[P('tfc:metal/shield/bronze'), P('tfc:metal/sword/bronze'), T('firmages:lantern/bronze', 4), T('tfc:metal/ingot/bronze', 8)]),
    sides=[
        ('bronze/smith/weld', [
            Q('bronze/side/backpack', 'Pack Mule', t_item('sophisticatedbackpacks:backpack'), kind='side',
              desc=['A backpack carries a second inventory.'],
              rewards=[P('sophisticatedbackpacks:stack_upgrade_starter_tier'), T('minecraft:leather', 4)]),
        ]),
        ('bronze/alloy/ingot', [
            Q('bronze/side/kitchen', 'Kitchen', t_item('firmalife:drying_mat'), kind='side',
              desc=['Build a drying mat, the first piece of a real kitchen. Ovens, cheese and dried food follow.'],
              rewards=[T('firmalife:beeswax', 4), P('firmalife:food/bacon', 3)]),
        ]),
        ('bronze/prosp/tin', [
            Q('bronze/side/totem', 'A Flute', t_item('totemic:flute'), kind='side',
              desc=['Carve a flute. Its music starts the ceremonies at a totem pole.'],
              rewards=[P('totemic:cooked_buffalo_meat', 4)]),
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
          desc=['Hover over a machine in your inventory and hold W to watch it work.']),
    ],
    ring=ring_spec(1, 'the Bronze Sanctum',
                    ['Raise the Bronze Sanctum around the Hearth Circle: rock bricks, a bronze block on each corner',
                     'and a bronze or brass bell opposite the plinth. The heart shows every missing block.']))

# ---------------------------------------------------------------- Iron Age
IRON = age_chapter(
    'iron', 'iron_age', 'Iron Age', ['Iron veins wake.'], 'firmages:steel_heart',
    'age_2', 3,
    entry=Q('iron/entry', 'The Iron Age Dawns', t_stage('age_2'), kind='entry',
            icon='tfc:metal/ingot/wrought_iron', sub='The third Age',
            desc=['Iron veins wake.',
                  '',
                  'Five strands lead to the Steel Heart: Metallurgy, Power & Motion, Survival, Frontier and Sky. The',
                  'Sky strand readies the Lens Crown of the next Age.']),
    strands=[
        ('Metallurgy', [
            Q('iron/metal/bloomery', 'Ironworks', t_item('tfc:bloomery'),
              desc=[Verbatim(image_line('device/bloomery')), '', 'Build a bloomery from bronze double sheets and stone bricks.'],
              rewards=[T('minecraft:charcoal', 16)]),
            Q('iron/metal/bloom', 'In Bloom', t_item('tfc:raw_iron_bloom'),
              desc=['Fill the bloomery with iron ore and charcoal and let it burn.'],
              rewards=[T('tfc:powder/flux', 8)]),
            Q('iron/metal/wrought', 'Wrought Iron', t_item('tfc:metal/ingot/wrought_iron', 4),
              desc=['Hammer the bloom on an anvil until it becomes wrought iron.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/metal/anvil', 'Iron Anvil', t_item('tfc:metal/anvil/wrought_iron'),
              desc=['A wrought iron anvil can work steel.'],
              rewards=[P('tfc:metal/hammer/wrought_iron')]),
            Q('iron/metal/blast', 'Blast Furnace', t_item('tfc:blast_furnace'),
              desc=[Verbatim(image_line('device/blast_furnace')), '', 'The blast furnace makes pig iron from iron ore, charcoal and flux.'],
              rewards=[T('minecraft:charcoal', 16), T('tfc:powder/flux', 8)]),
            Q('iron/metal/pig', 'Pig Iron', t_item('tfc:metal/ingot/pig_iron', 2),
              desc=['Cast the pig iron. Hammer it into high carbon steel, then into steel.'],
              rewards=[T('tfc:metal/ingot/pig_iron', 2)]),
        ], Q('iron/metal/key', 'Steel Ingots', t_item('tfc:metal/ingot/steel', 16), kind='keystone',
             desc=['Sixteen steel ingots. From here on, steel is a material, not a trophy.'],
             rewards=[T('tfc:metal/ingot/steel', 4), T('tfc:powder/flux', 16), P('tfc:metal/hammer/steel')])),
        ('Power & Motion', [
            Q('iron/power/brass', 'Brass', [t_item('tfc:metal/ingot/brass', 4), t_item('tfcreate:primitive_heater',
                                                                                     title='A fuel heater')],
              desc=['Alloy copper and zinc into brass, by hand in the crucible or in a basin over a fuel heater.'],
              rewards=[T('create:brass_casing', 4), T('create:fluid_tank', 2)]),
            Q('iron/power/precision', 'Precision Mechanism', t_item('create:precision_mechanism'),
              desc=['Sequenced assembly on a brass base. Many Iron Age machines need it.'],
              rewards=[T('create:electron_tube', 2)]),
            Q('iron/power/crush', 'Crushing Wheels', t_item('create:crushing_wheel', 2),
              desc=['Crushing wheels turn ore pieces into dust for more metal per ore.'],
              rewards=[T('create:fluid_pipe', 8)]),
        ], Q('iron/power/steam', 'Steam Engine', t_item('create:steam_engine'), kind='keystone',
             desc=['Steam engines on a boiler give far more force than water wheels.'],
             rewards=[T('create:mechanical_pump', 2), T('create:steam_whistle')])),
        ('Survival', [
            Q('iron/surv/leather', 'Genuine Leather', [t_adv('tfc:story/barrel', 'Build a barrel'),
                                                       t_adv('tfc:story/leather', 'Tan a hide')],
              desc=[Verbatim(image_line('device/barrel')), '',
                    'Build a barrel with a saw and lumber; barrels brine, pickle and tan. Soak a hide in limewater,',
                    'scrape it, then soak it in tannin.'],
              icon='minecraft:leather',
              rewards=[T('minecraft:leather', 8), T('tfc:powder/lime', 8)]),
            Q('iron/surv/clothes', 'Winter Gear',
              [t_item('minecraft:leather_helmet', title='Leather cap'), t_item('minecraft:leather_chestplate', title='Leather tunic'),
               t_item('minecraft:leather_leggings', title='Leather pants'), t_item('minecraft:leather_boots', title='Leather boots')],
              icon='minecraft:leather_chestplate',
              desc=['Make all four pieces of leather armor from your own tanned hides. Wear them before the first',
                    'winter trip.'],
              rewards=[T('tfc:wool_cloth', 6)]),
            Q('iron/surv/vessels', 'Cellar Vessels', t_item('tfc:ceramic/large_vessel', 4),
              desc=['Dig a cellar below ground: it stays cool. Fill large vessels and seal them.'],
              rewards=[T('tfc:powder/salt', 16)]),
        ], Q('iron/surv/key', 'Winter Stores', t_check('Our cellar is stocked for the winter'), kind='keystone',
             icon='tfc:ceramic/large_vessel',
             desc=['Tick this when your cellar holds sealed vessels with enough food for all of you.',
                   'No food goes into any recipe or turn-in.'],
             rewards=[P('sophisticatedbackpacks:iron_backpack')])),
        ('Frontier', [
            Q('iron/front/compass', 'Frontier Compass', t_item('firmages:frontier_compass'),
              desc=['Set magnetite in wrought iron and right-click with the compass. It names the way to the nearest',
                    'cold forge.'],
              rewards=[P('artifacts:running_shoes')]),
            Q('iron/front/forge', 'The Cold Forge', t_item('firmages:frontier_shard', consume=False),
              desc=['An older smith worked these hills. Find the cold forge and bring back what its chest kept.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
        ], Q('iron/front/key', 'Frontier Relic', t_item('firmages:frontier_relic', consume=False), kind='keystone',
             desc=['Finish the shard on the deployer and the press. The Steel Heart needs the relic.'],
             rewards=[P('firmages:frontier_compass'), T('tfc:metal/sheet/wrought_iron', 4)])),
        ('Sky', [
            Q('iron/sky/star', 'A Falling Star', t_item('firmages:meteoric_iron_chunk'), icon='firmages:meteoric_iron_chunk',
              desc=['Break a meteorite in a crater. On some clear nights a star falls a few thousand blocks away;',
                    'old cold craters lie in new land.'],
              rewards=[T('minecraft:charcoal', 16)]),
            Q('iron/sky/ingot', 'Meteoric Iron', t_item('firmages:meteoric_iron_ingot', 2),
              desc=['Heat the chunks and work them on the anvil. Meteoric iron falls only from the sky.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/sky/rod', 'Storm Rod', t_item('firmages:storm_rod'),
              desc=['Build a Storm Rod with a meteoric iron tip and fill it with sand. In any rain, lightning strikes',
                    'it and fuses the sand.'],
              rewards=[T('tfc:metal/rod/wrought_iron', 4)]),
        ], Q('iron/sky/key', 'Storm Glass', t_item('firmages:storm_glass', 8), kind='keystone',
             desc=['Gather eight storm glass from your Storm Rods; they are its only source. With meteoric iron it',
                   'becomes Starlit Iron under the Lens Crown, the first celestial alloy.'],
             rewards=[T('tfc:metal/sheet/steel', 2), P('tfc:metal/helmet/steel')])),
    ],
    goal=Q('iron/goal', 'Offer the Steel Heart at the Shrine',
           t_stage('age_3', 'Offer the Steel Heart at the shrine'), kind='goal', icon='firmages:steel_heart',
           sub='Goal of the Iron Age: Caelum opens the Arcane Age',
           desc=['Craft the Steel Heart from the Frontier Relic, a precision mechanism, a steel sheet and wrought iron',
                 'double sheets.'],
           rewards=[P('artifacts:cloud_in_a_bottle'), P('tfc:metal/sword/steel'), T('tfc:metal/ingot/steel', 8), T('firmages:lantern/iron', 4)]),
    # G4 (rule C): the automated line needs the bloomery of Metallurgy and the engine of Power & Motion: a bridge
    # between those two keystones and the goal.
    bridge=Q('iron/power/key', 'Automated Bloomery Line', t_item('tfc:metal/ingot/wrought_iron', 32),
             icon='tfc:metal/ingot/wrought_iron', deps=['iron/power/steam', 'iron/metal/key'],
             desc=['Feed the bloomery by machine and collect 32 wrought iron ingots from the line.'],
             rewards=[T('tfc:metal/sheet/wrought_iron', 4), P('tfc:metal/chestplate/wrought_iron')]),
    sides=[
        ('iron/power/brass', [
            Q('iron/side/rails', 'Trains', t_item('create:track', 16), kind='side',
              desc=['Lay 16 tracks. Trains bring ore home from distant mines.'],
              rewards=[T('railways:buffer', 2), T('tfc:metal/rod/wrought_iron', 4)]),
        ]),
        ('iron/surv/leather', [
            Q('iron/side/storage', 'Storage Terminal', t_item('toms_storage:storage_terminal'), kind='side',
              desc=['Build a storage terminal and connect your chests. Every chest shows in one screen.'],
              rewards=[T('toms_storage:inventory_cable', 16), T('create:electron_tube', 2)]),
        ]),
    ],
    explains=[
        Q('iron/info/winter', 'The First Winter', t_check(), kind='explain', icon='tfc:ceramic/large_vessel',
          desc=['The year has seasons. Crops stop growing in winter and it gets cold. Plan ahead.']),
    ],
    ring=ring_spec(2, 'the Iron Sanctum',
                    ['Raise the Iron Sanctum around the Bronze Sanctum: smooth rock pillars with wrought iron bars and',
                     'four wrought iron lamps. The heart shows every missing block.']))

# ---------------------------------------------------------------- Arcane Age
ARCANE = age_chapter(
    'arcane', 'arcane_age', 'Arcane Age', ['Spirits, source, alchemy and the Nether.'],
    'firmages:arcane_keystone', 'age_3', 4,
    entry=Q('arcane/entry', 'The Arcane Age Dawns', t_stage('age_3'), kind='entry', icon='ars_nouveau:source_gem',
            sub='The fourth Age',
            desc=['Magic wakes: spirits, source and alchemy. It gives the best ore yield of this Age.',
                  '',
                  'The Arcane Keystone takes one of two routes. The Spirit Route binds it from the Spirits, Source and',
                  'Alchemy strands. The Machine Route fills a Hollow Keystone on your machine line. Both need Starlit',
                  'Iron from the Lens Crown: the Sky strand.']),
    strands=[
        ('Spirits', [
            Q('arcane/spirit/datura', 'Datura', t_item('occultism:datura_seeds'),
              desc=['Datura seeds drop from grass. Plant them on Arcane Soil, dirt charged with amethyst;',
                    'plain farmland will not hold them.'],
              rewards=[T('tfc:gem/amethyst', 4)]),
            Q('arcane/spirit/dream', 'Demon\'s Dream', t_item('occultism:demons_dream_essence'),
              desc=['Datura fruit becomes Demon\'s Dream essence. It opens your third eye to the spirit world.'],
              rewards=[T('occultism:otherworld_essence', 4)]),
            Q('arcane/spirit/chalk', 'White Chalk', [t_item('occultism:dictionary_of_spirits', title='Dictionary of Spirits'),
                                                     t_item('occultism:chalk_white', title='White chalk')],
              icon='occultism:chalk_white',
              desc=['Craft the Dictionary of Spirits and white chalk. The book explains every pentacle and ritual;',
                    'white chalk is enough for Foliot rituals.'],
              rewards=[T('occultism:chalk_white', 4), P('occultism:otherworld_goggles')]),
            Q('arcane/spirit/nether', 'The Way Below', t_dim('minecraft:the_nether'), icon='beneath:ancient_altar',
              # Beneath 2.0.4 PortalUtil (javap): a goat, pig or sheep killed with a TFC scythe, at least 7 offerings
              # within 5 blocks (y -2 to +3): skulls, anvils, gold or black steel blocks, ingot piles of gold, pig iron
              # or black steel. Blindness and nausea, an explosion at the player, a lightning bolt, a portal.
              desc=['Reach the Nether by sacrifice:',
                    '- Set at least seven offerings within five blocks of one spot: anvils, mob heads, gold or black',
                    'steel blocks, or piles of gold, pig iron or black steel ingots.',
                    '- Kill a goat, pig or sheep there with a scythe.',
                    '',
                    'The ground bursts and a portal opens where you stand. Step through and look for iesnium.'],
              rewards=[P('artifacts:obsidian_skull')]),
            Q('arcane/spirit/iesnium', 'Iesnium', t_item('occultism:iesnium_ingot', 4),
              desc=['Iesnium ore lies in the Nether. Smelt it into ingots; the Djinni rituals need it.'],
              rewards=[T('occultism:golden_sacrificial_bowl', 4)]),
            Q('arcane/spirit/djinni', 'Bind a Djinni', t_item('occultism:book_of_binding_bound_djinni'),
              desc=['Bind a Djinni into a book of binding. A Djinni runs crafting rituals and the Mineshaft.'],
              rewards=[T('occultism:chalk_gold', 2)]),
        ], Q('arcane/spirit/key', 'Spirit Proof',
             [t_item('occultism:spirit_attuned_crystal', title='Spirit attuned crystal'),
              t_item('occultism:iesnium_ingot', 2, title='Iesnium ingots')],
             kind='keystone', icon='occultism:spirit_attuned_crystal',
             desc=['A spirit attuned crystal and iesnium: the proof of the Spirits strand.',
                   'Next, Dimensional Storage and the Mineshaft with a Djinni miner, which gives rich ore pieces',
                   'of every metal your Age allows.'],
             rewards=[T('occultism:otherworld_essence', 8), P('occultism:familiar_ring')])),
        ('Source', [
            Q('arcane/source/book', 'Novice Spellbook', t_item('ars_nouveau:novice_spell_book'),
              desc=['The novice spellbook casts the first spells and opens the worn notebook.'],
              rewards=[P('ars_nouveau:blank_parchment', 8)]),
            Q('arcane/source/link', 'Sourcelink',
              t_any_item(['ars_nouveau:volcanic_sourcelink', 'ars_nouveau:agronomic_sourcelink',
                          'ars_nouveau:mycelial_sourcelink', 'ars_nouveau:vitalic_sourcelink',
                          'ars_nouveau:alchemical_sourcelink'], 'Any sourcelink'),
              icon='ars_nouveau:volcanic_sourcelink',
              desc=['Sourcelinks turn fuel, growth or life around them into source. Store it in source jars.'],
              rewards=[T('ars_nouveau:source_jar', 2)]),
            Q('arcane/source/chamber', 'Imbuement Chamber', t_item('ars_nouveau:imbuement_chamber'),
              desc=['The imbuement chamber charges items with source.'],
              rewards=[T('tfc:gem/lapis_lazuli', 4)]),
            Q('arcane/source/gems', 'Source Gems', t_item('ars_nouveau:source_gem', 16),
              desc=['Imbue amethyst or lapis lazuli. Source gems are the base of all source magic.'],
              rewards=[T('ars_nouveau:source_gem', 8)]),
            Q('arcane/source/apparatus', 'Enchanting Apparatus', t_item('ars_nouveau:enchanting_apparatus'),
              desc=['The apparatus on an arcane core with pedestals crafts the source items. It is the one magic',
                    'pedestal crafting of this Age.'],
              rewards=[T('ars_nouveau:arcane_pedestal', 4)]),
            Q('arcane/source/starbuncle', 'Starbuncle', t_item('ars_nouveau:starbuncle_charm'),
              desc=['A Starbuncle carries items between chests: the item transport of the Arcane Age.'],
              rewards=[T('ars_nouveau:whirlisprig_charm')]),
            Q('arcane/source/apprentice', 'Apprentice Spellbook', t_item('ars_nouveau:apprentice_spell_book'),
              desc=['The apprentice spellbook holds the stronger glyphs you need against the Wilden Chimera.'],
              rewards=[P('ars_nouveau:amulet_of_mana_boost')]),
        ], Q('arcane/source/key', 'Wilden Tribute',
             [t_item('ars_nouveau:wilden_tribute', consume=False),
              t_kill('ars_nouveau:wilden_boss', optional=True, title='Slay the Wilden Chimera (optional)')],
             kind='keystone', icon='ars_nouveau:wilden_tribute',
             desc=['Bind horn, wing and spike of the Wilden with source in the enchanting apparatus, or take the',
                   'tribute from the Wilden Chimera. Keep it: the Djinni ritual of the Arcane Keystone needs it.'],
             rewards=[T('ars_nouveau:source_gem', 8), P('ars_nouveau:amulet_of_mana_regen')])),
        ('Alchemy', [
            Q('arcane/alch/brazier', 'Pyromantic Brazier', t_item('theurgy:pyromantic_brazier'),
              desc=['Build a pyromantic brazier. It heats every alchemical apparatus; read the Hermetica as you go.'],
              rewards=[T('minecraft:charcoal', 16)]),
            Q('arcane/alch/calcination', 'Calcination Oven', [t_item('theurgy:calcination_oven'),
                                                              t_item('theurgy:sal_ammoniac_accumulator')],
              desc=['Build the calcination oven and a sal ammoniac accumulator. Calcination turns minerals into',
                    'alchemical salt; sal ammoniac is the solvent of every liquefaction.'],
              rewards=[T('tfc:ore/normal_hematite', 8), T('theurgy:sal_ammoniac_crystal', 4)]),
            Q('arcane/alch/liquefaction', 'Liquefaction Cauldron', t_item('theurgy:liquefaction_cauldron'),
              desc=['Build the liquefaction cauldron and a digestion vat. Ore pieces of every grade digest into',
                    'alchemical sulfur, where spagyrics begin.'],
              rewards=[T('tfc:ore/normal_limonite', 8)]),
            Q('arcane/alch/distiller', 'The Three Principles', [t_item('theurgy:distiller'), t_item('theurgy:incubator')],
              desc=['Build the distiller and the incubator. Mercury shards, salt and sulfur incubate into a metal',
                    'dust. Melt it in a vessel.'],
              rewards=[T('theurgy:mercury_shard', 4), T('tfc:ore/normal_magnetite', 8)]),
        ], Q('arcane/alch/key', 'Mercury Catalyst',
             [t_item('theurgy:mercury_catalyst'), t_item('theurgy:divination_rod_t1', title='A divination rod, if you like',
                                                         optional=True)],
             kind='keystone', icon='theurgy:mercury_catalyst',
             desc=['Make the mercury catalyst, the proof of the Alchemy strand. The Arcane Keystone needs it. A',
                   'divination rod points to the ore veins your Age has woken.'],
             rewards=[T('theurgy:mercury_shard', 8), T('theurgy:sal_ammoniac_crystal', 8), P('theurgy:mercurial_wand'), P('theurgy:divination_rod_t2')])),
        ('Sky', [
            Q('arcane/sky/crown', 'The Lens Crown',
              t_observe('firmages:star_anvil[formed=true]', kind='block_state', title='Look at a formed Star Anvil'),
              icon='firmages:star_anvil',
              desc=[Verbatim(image_line('ponder/lens_crown')), '',
                    'Set a Star Anvil in a 9 by 9 stone ring with three lens mounts under open sky. The Lens Crown',
                    'forges the celestial alloys.', '',
                    Verbatim(image_line('ponder/chords'))],
              rewards=[T('tfc:gem/amethyst', 4)]),
        ], Q('arcane/sky/key', 'Starlit Iron', t_item('firmages:starlit_iron_ingot', 3), kind='keystone',
             desc=['Forge Starlit Iron under the crown from meteoric iron, storm glass and an Arcane gem. Both routes to',
                   'the Arcane Keystone need it, and the Wayshrines want it as their offering.'],
             rewards=[T('tfc:metal/sheet/steel', 4), P('artifacts:cross_necklace')])),
        # Machine Route (pacing B2): the magic-free way carries the blaze burner and the blaze enchanter as steps,
        # so a fellowship without spirits, source or alchemy still builds a line of its Age.
        ('Machine Line', [
            Q('arcane/machine/blaze', 'Blaze Rods', t_item('minecraft:blaze_rod', 3), deps=['arcane/spirit/nether'],
              desc=['Blazes live in the Nether fortresses. A deployer sets one rod per pass.'],
              rewards=[T('create:deployer', 2)]),
            Q('arcane/machine/burner', 'Blaze Burner', t_item('create:blaze_burner'),
              desc=['Catch a blaze in the Nether with an empty blaze burner. It fires the mixers of your line',
                    'superheated.'],
              rewards=[T('minecraft:blaze_rod', 4)]),
            Q('arcane/machine/enchant', 'Enchantment Industry', t_item('create_enchantment_industry:blaze_enchanter'),
              desc=['Smith a blaze burner and an enchanting table with a blaze upgrade template from Nether chests.',
                    'The enchanter and the Hollow Keystone drink liquid experience.'],
              rewards=[T('create:experience_nugget', 32)]),
        ], Q('arcane/machine/key', 'Hollow Keystone', t_item('firmages:hollow_keystone', consume=False), kind='keystone',
             desc=['Find a Hollow Keystone in a chest of a Nether fortress. A spout, two deployers and a press fill it in',
                   'three passes: liquid experience, a blaze rod, Starlit Iron, the press.'],
             rewards=[T('create:mechanical_arm', 2), P('create:potato_cannon')])),
    ],
    routes=[
        Q('arcane/route/spirit', 'The Spirit Route',
          [t_item('occultism:book_of_binding_bound_djinni', consume=False, title='The bound Djinni'),
           t_item('firmages:arcane_keystone', consume=False, title='The Arcane Keystone')],
          deps=['arcane/spirit/key', 'arcane/source/key', 'arcane/alch/key', 'arcane/sky/key'],
          icon='occultism:book_of_binding_bound_djinni',
          desc=['Bind the Arcane Keystone in the Djinni\'s crafting pentacle from the proofs of the magic strands.'],
          rewards=[T('ars_nouveau:source_gem', 8)]),
        Q('arcane/route/machine', 'The Machine Route',
          [t_item('create:mechanical_press', consume=False, title='A mechanical press'),
           t_item('firmages:arcane_keystone', consume=False, title='The Arcane Keystone')],
          deps=['arcane/machine/key', 'arcane/sky/key'], icon='create:mechanical_press',
          desc=['Fill the Hollow Keystone on your machine line. No spirit, source or alchemy is needed.'],
          rewards=[T('create:experience_nugget', 16)]),
    ],
    goal=Q('arcane/goal', 'Offer the Arcane Keystone at the Shrine',
           t_stage('age_4', 'Offer the Arcane Keystone at the shrine'), kind='goal', icon='firmages:arcane_keystone',
           sub='Goal of the Arcane Age: Caelum opens the Industrial Age',
           desc=['Bind the Arcane Keystone in the Djinni\'s crafting pentacle (a spirit attuned crystal, an iesnium',
                 'ingot, the Wilden Tribute, the mercury catalyst, Starlit Iron and a source gem block), or fill a',
                 'Hollow Keystone on the machine line.'],
           rewards=[P('artifacts:strider_shoes'), T('ars_nouveau:source_jar', 2), T('firmages:lantern/arcane', 4)]),
    sides=[
        ('arcane/machine/burner', [
            Q('arcane/side/cake', 'Blaze Cake', t_item('create:blaze_cake'), kind='side',
              desc=['Bake a blaze cake. A burner fed one runs superheated for a while without a fresh blaze.'],
              rewards=[P('create:experience_nugget', 8)]),
        ]),
        ('arcane/source/starbuncle', [
            Q('arcane/side/creo', 'Starbuncle Wheel', t_item('ars_creo:starbuncle_wheel'), kind='side',
              desc=['Set a Starbuncle in a wheel. It turns shafts without water or wind.'],
              rewards=[T('ars_nouveau:starbuncle_charm', 2)]),
        ]),
    ],
    explains=[
        Q('arcane/info/ore', 'Ore in the Arcane Age', t_check(), kind='explain', icon='tfc:ore/rich_hematite',
          desc=['Three ways give more metal per ore:',
                '- Crushing wheels: about 1.5 times.',
                '- Spagyrics: twice the metal, every grade.',
                '- The Dimensional Mineshaft: rich pieces without end.',
                '',
                'The spirit crusher grinds no ore.']),
        Q('arcane/info/one', 'One Carrier per Function', t_check(), kind='explain', icon='ars_nouveau:starbuncle_charm',
          desc=['Each magic function has one carrier: Starbuncles move items, Dimensional Storage stores them,',
                'spagyrics multiply ore, the Djinni crafts in pentacles.']),
    ],
    ring=ring_spec(3, 'the Spirit Circle',
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
              desc=['Soak planks in creosote. Treated wood is the frame of every engineering machine.'],
              rewards=[P('immersiveengineering:manual'), P('immersiveengineering:hammer')]),
            Q('industrial/steel/coke', 'Coke Oven', t_observe('immersiveengineering:coke_oven',
                                                               title='Look at a formed coke oven'),
              icon='immersiveengineering:coke_oven',
              desc=['Build the 3x3x3 coke oven from coke bricks and form it with the Engineer\'s Hammer.',
                    'It cokes bituminous coal and lignite and gives creosote.'],
              rewards=[T('immersiveengineering:coal_coke', 8)]),
            Q('industrial/steel/fuel', 'Coal Coke', t_item('immersiveengineering:coal_coke', 32),
              desc=['Coke burns hot and clean. The blast furnace needs it.'],
              rewards=[T('tfc:metal/ingot/wrought_iron', 8)]),
            Q('industrial/steel/blast', 'Engineer\'s Blast Furnace', t_observe('immersiveengineering:blast_furnace',
                                                                    title='Look at a formed blast furnace'),
              icon='immersiveengineering:blast_furnace',
              desc=['Build the blast furnace. Coke and wrought iron go in, steel comes out, faster than any anvil.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
            Q('industrial/steel/gearbox', 'Arcane Gearbox', t_item('firmages:arcane_gearbox'),
              desc=['A gearbox with steel and precision mechanisms from the mechanical crafter, or with source gems',
                    'by magic. Reinforced blast bricks and Pressure Cores need it.'],
              rewards=[T('immersiveengineering:blastbrick_reinforced', 4)]),
            Q('industrial/steel/improved', 'Improved Blast Furnace',
              t_observe('immersiveengineering:advanced_blast_furnace', title='Look at a formed improved blast furnace'),
              icon='immersiveengineering:advanced_blast_furnace',
              desc=['Reinforced blast bricks and preheaters make steel faster.'],
              rewards=[T('tfc:metal/ingot/nickel', 2), T('tfc:metal/ingot/black_bronze', 2)]),
            Q('industrial/steel/black', 'Black Steel', t_item('tfc:metal/ingot/black_steel', 8),
              desc=['Work weak steel with nickel and black bronze on the anvil into black steel, the metal of the',
                    'Industrial Age.'],
              rewards=[T('tfc:metal/ingot/black_steel', 2)]),
            Q('industrial/steel/anvil', 'Black Steel Anvil', t_item('tfc:metal/anvil/black_steel'),
              desc=['A black steel anvil works black steel double sheets.'],
              rewards=[P('tfc:metal/hammer/black_steel')]),
            Q('industrial/steel/chamber', 'Built-Up Chamber',
              t_item('createbigcannons:built_up_steel_cannon_chamber', consume=False),
              desc=['Cast steel layers, bore them, build them up and heat them into one chamber. It goes into the',
                    'Pressure Core.'],
              rewards=[P('tfc:metal/pickaxe/black_steel')]),
        ], Q('industrial/steel/key', 'Black Steel Double Sheets', t_item('tfc:metal/double_sheet/black_steel', 2),
             kind='keystone', desc=['Two black steel double sheets for the Pressure Core.'],
             rewards=[T('immersiveengineering:component_steel', 4), P('tfc:metal/chestplate/black_steel')])),
        ('Power', [
            Q('industrial/power/alternator', 'Alternator', t_item('createaddition:alternator'),
              desc=['The alternator turns rotation into FE, the only bridge from SU to FE. Drive it with a steam engine',
                    'on a fired boiler.'],
              rewards=[T('createaddition:electric_motor')]),
            Q('industrial/power/lead', 'Lead', t_item('immersiveengineering:ingot_lead', 4),
              desc=['Galena veins wake. Lead goes into capacitors.'],
              rewards=[T('immersiveengineering:plate_lead', 2)]),
            Q('industrial/power/capacitor', 'LV Capacitor', t_item('immersiveengineering:capacitor_lv'),
              desc=['The LV capacitor stores FE and evens out the grid.'],
              rewards=[T('immersiveengineering:wirecoil_copper', 2)]),
        ], Q('industrial/power/key', 'Grid Online (LV)',
             [t_observe('createaddition:alternator', title='Look at your placed alternator'),
              t_observe('immersiveengineering:capacitor_lv', title='Look at your placed LV capacitor'),
              t_item('immersiveengineering:wirecoil_copper', 8, title='Copper wire'),
              t_item('immersiveengineering:connector_lv', 4, title='LV connectors')],
             kind='keystone', icon='immersiveengineering:capacitor_lv',
             desc=['Hang copper wire on LV connectors from the alternator to the capacitor and run your machines',
                   'from it.'],
             rewards=[T('immersiveengineering:capacitor_lv', 2), T('immersiveengineering:electric_lantern', 4), P('immersiveengineering:wirecutter'), T('immersiveengineering:connector_lv_relay', 4)])),
        ('Ore Line', [
            Q('industrial/ore/crusher', 'Crusher', t_observe('immersiveengineering:crusher',
                                                             title='Look at a formed crusher'),
              icon='immersiveengineering:crusher',
              desc=['Build a crusher and feed it ore pieces. It returns two and a half times the metal as powder and',
                    'dust, plus a by-product.'],
              rewards=[T('tfc:ore/normal_hematite', 16)]),
            Q('industrial/ore/press', 'Metal Press', t_observe('immersiveengineering:metal_press',
                                                               title='Look at a formed metal press'),
              icon='immersiveengineering:mold_plate',
              desc=['The metal press makes sheets from two ingots, rods and wire.'],
              rewards=[T('immersiveengineering:mold_plate'), T('immersiveengineering:mold_rod'), T('immersiveengineering:mold_wire')]),
            Q('industrial/ore/kiln', 'Alloy Kiln', t_observe('immersiveengineering:alloy_smelter',
                                                             title='Look at a formed alloy kiln'),
              icon='immersiveengineering:alloy_smelter',
              desc=['The alloy kiln alloys two metals in the right ratios.'],
              rewards=[T('tfc:metal/ingot/nickel', 2)]),
            Q('industrial/ore/constantan', 'Constantan', t_item('immersiveengineering:ingot_constantan', 8),
              desc=['Copper and nickel in the alloy kiln.'],
              rewards=[T('immersiveengineering:plate_constantan', 2)]),
        ], Q('industrial/ore/key', 'Automated Ore Line', t_item('mekanism:dust_iron', 64), kind='keystone',
             desc=['Feed the crusher by machine and collect 64 iron dust from the line.'],
             rewards=[T('immersiveengineering:ingot_constantan', 4), T('immersiveengineering:component_iron', 4)])),
        ('Logistics & Backfill', [
            Q('industrial/logi/packager', 'Packager', t_item('create:packager'),
              desc=['The packager boxes items for the package network.'],
              rewards=[T('create:chain_conveyor', 4)]),
            Q('industrial/logi/frogport', 'Frogport', t_item('create:package_frogport'),
              desc=['Frogports send packages along chain conveyors.'],
              rewards=[T('create:package_frogport', 2)]),
            Q('industrial/logi/ticker', 'Stock Ticker', t_item('create:stock_ticker'),
              desc=['A stock link and a stock ticker show and request your stock from anywhere on the network.'],
              rewards=[T('create:stock_link', 2)]),
            Q('industrial/logi/gauge', 'Factory Gauge', t_item('create:factory_gauge'),
              desc=['Factory gauges keep a production line stocked by themselves.'],
              rewards=[T('create:factory_gauge', 2)]),
            Q('industrial/logi/atfc', 'The Thresher',
              t_observe('advancedtfctech:thresher', title='Look at a formed thresher'),
              icon='advancedtfctech:thresher',
              desc=['Build a thresher from steel sheet metal, light engineering blocks, a redstone engineering block',
                    'and a hopper, and form it with the Engineer\'s Hammer like the crusher. It ends threshing by hand.',
                    '',
                    'The grist mill, beamhouse, power loom and fleshing machine follow and end the rest of the hand',
                    'work.'],
              rewards=[T('immersiveengineering:hemp_fiber', 16)]),
        ], Q('industrial/logi/key', 'Backfill Line', t_item('tfc:metal/sheet/steel', 64), kind='keystone',
             icon='tfc:metal/sheet/steel',
             desc=['Bring 64 steel sheets from your press line through the package network.'],
             rewards=[T('create:redstone_requester', 2), T('create:stock_link', 2)])),
        ('Sky', [
            Q('industrial/sky/isles', 'Above the Clouds', t_item('firmages:firmament_crystal', 4),
              icon='firmages:firmament_crystal',
              desc=['Fly to the Drifting Isles at height 228 to 272 by airship or jetpack. Firmament crystal grows only',
                    'there.'],
              rewards=[T('immersiveengineering:hemp_fabric', 8)]),
            Q('industrial/sky/net', 'Aurora Net', t_item('firmages:aurora_net'),
              desc=['Set an Aurora Net at height 140 or more under open sky in the cold north. Every clear night it',
                    'catches aurora light.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
        ], Q('industrial/sky/key', 'Cloudsteel', t_item('firmages:cloudsteel_ingot', 2), kind='keystone',
             desc=['Add the Arcane lens to the crown and forge Cloudsteel from meteoric iron, black steel and',
                   'firmament crystal. Two ingots go into the Pressure Core.'],
             rewards=[T('tfc:metal/sheet/black_steel', 2)])),
    ],
    goal=Q('industrial/goal', 'Offer the Pressure Core at the Shrine',
           t_stage('age_5', 'Offer the Pressure Core at the shrine'), kind='goal', icon='firmages:pressure_core',
           sub='Goal of the Industrial Age: Caelum opens the Electric Age',
           desc=['Craft the Pressure Core from two black steel double sheets, a heavy engineering block, two',
                 'Cloudsteel ingots, the Arcane Gearbox and a built-up steel chamber.'],
           rewards=[P('artifacts:crystal_heart'), P('tfc:metal/sword/black_steel'), T('immersiveengineering:component_steel', 4), T('firmages:lantern/industrial', 4)]),
    sides=[
        ('industrial/steel/wood', [
            Q('industrial/side/airship', 'Airship', t_item('aeronautics:andesite_propeller'), kind='side',
              desc=['Craft a propeller, the first part of a ship that flies.'],
              rewards=[P('aeronautics:aviators_goggles'), T('immersiveengineering:wirecoil_structure_rope', 8)]),
        ]),
        ('industrial/entry', [
            Q('industrial/side/chute', 'Tally Chute', t_item('firmages:tally_chute'), kind='side',
              desc=['Craft a Tally Chute. It takes only the metal Caelum asks as proof of work, counts it and keeps it.'],
              rewards=[T('immersiveengineering:chute_steel', 4)]),
            proof_quest('industrial', 'age_4'),
        ]),
        ('industrial/power/lead', [
            Q('industrial/side/jetpack', 'Air Jetpack', t_item('create_jetpack:jetpack'), kind='side',
              desc=['Craft a jetpack. It flies on compressed air from its tank.'],
              rewards=[P('create:copper_backtank')]),
        ]),
        ('industrial/ore/crusher', [
            Q('industrial/side/spawner', 'Mechanical Spawner', t_item('create_mechanical_spawner:mechanical_spawner'),
              kind='side', desc=['Spawn fluid turns into mobs, only those your Age allows.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
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
          desc=['Keep the chunks your lines run in loaded, or they stop when you leave.']),
    ],
    ring=ring_spec(4, 'the Foundry Nave',
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
              desc=['Electrum wire carries medium voltage.'],
              rewards=[T('immersiveengineering:connector_mv', 4)]),
            Q('electric/grid/hv', 'HV Wire', t_item('immersiveengineering:wirecoil_steel', 8),
              desc=['Steel wire carries high voltage over long distances.'],
              rewards=[T('immersiveengineering:connector_hv', 4)]),
            Q('electric/grid/transformer', 'Transformers',
              [t_item('immersiveengineering:transformer', title='Transformer'),
               t_item('immersiveengineering:transformer_hv', title='HV transformer')],
              icon='immersiveengineering:transformer_hv', desc=['Transformers step the voltage between wire tiers.'],
              rewards=[T('immersiveengineering:connector_hv_relay', 4)]),
            Q('electric/grid/diesel', 'Diesel Generator', t_observe('immersiveengineering:diesel_generator',
                                                                     title='Look at a formed diesel generator'),
              icon='immersiveengineering:diesel_generator',
              desc=['The diesel generator is the one FE generator of this Age. Feed it diesel from the Oil strand.'],
              rewards=[T('immersiveengineering:capacitor_mv', 2)]),
            Q('electric/grid/af', 'Alternating Flux', t_item('alternatingflux:wirecoil_af', 4),
              desc=['Alternating Flux wire carries the strongest current with little loss.'],
              rewards=[T('immersiveengineering:connector_hv', 4)]),
        ], Q('electric/grid/key', 'Grid Online', t_item('immersiveengineering:capacitor_hv', 2), kind='keystone',
             desc=['Two HV capacitors: one buffers your grid, one goes into the Humming Core.'],
             rewards=[T('immersiveengineering:wirecoil_electrum', 8), T('immersiveengineering:wirecoil_steel', 8)])),
        ('Oil', [
            Q('electric/oil/sample', 'Core Sample', t_item('immersiveengineering:coresample'),
              desc=['A core sample drill shows what lies under a chunk, oil included.'],
              rewards=[T('immersivepetroleum:seismic_survey', 2)]),
            Q('electric/oil/pumpjack', 'Pumpjack',
              [t_observe('immersivepetroleum:pumpjack', title='Look at a formed pumpjack'),
               t_item('immersivepetroleum:crudeoil_bucket', title='A bucket of crude oil')],
              icon='immersivepetroleum:pumpjack',
              desc=['Build a pumpjack over an oil reservoir and pump the first bucket of crude oil into a tank.'],
              rewards=[T('create:fluid_tank', 4), P('immersiveengineering:jerrycan')]),
            Q('electric/oil/tower', 'Distillation Tower',
              [t_observe('immersivepetroleum:distillation_tower', title='Look at a formed distillation tower'),
               t_item('immersivepetroleum:lubricant_bucket', title='A bucket of lubricant')],
              icon='immersivepetroleum:distillation_tower',
              desc=['The distillation tower splits crude oil into diesel, lubricant and more. Lubricant through an',
                    'automatic lubricator speeds up your machines.'],
              rewards=[T('immersivepetroleum:petcoke', 8), T('immersivepetroleum:auto_lubricator', 2)]),
        ], Q('electric/oil/key', 'Refined Diesel', t_item('immersivepetroleum:diesel_bucket', 16), kind='keystone',
             desc=['Sixteen buckets of diesel from your own refinery.'],
             rewards=[T('immersivepetroleum:gasoline_bucket', 2), P('immersivepetroleum:oil_can')])),
        ('Aluminium & Excavator', [
            Q('electric/alu/bauxite', 'Bauxite',
              t_any_item(['tfc_ie_addon:ore/small_bauxite', 'tfc_ie_addon:ore/poor_bauxite',
                          'tfc_ie_addon:ore/normal_bauxite', 'tfc_ie_addon:ore/rich_bauxite'], 'Any bauxite ore piece'),
              icon='tfc_ie_addon:ore/normal_bauxite', desc=['Mine bauxite. Its ore is the only source of aluminium.'],
              rewards=[T('immersiveengineering:graphite_electrode', 3)]),
            Q('electric/alu/arc', 'Arc Furnace', t_observe('immersiveengineering:arc_furnace',
                                                           title='Look at a formed arc furnace'),
              icon='immersiveengineering:graphite_electrode',
              desc=['The arc furnace melts, alloys and makes steel with graphite electrodes.'],
              rewards=[T('immersiveengineering:graphite_electrode', 3)]),
            Q('electric/alu/ingot', 'Aluminium', t_item('immersiveengineering:ingot_aluminum', 16),
              desc=['Aluminium comes only from the arc furnace.'],
              rewards=[T('immersiveengineering:plate_electrum', 4)]),
            Q('electric/alu/red', 'Red Steel', t_item('tfc:metal/sheet/red_steel', 2),
              desc=['Work weak red steel on the anvil, then into a sheet. Red steel is the metal of the Electric Age.'],
              rewards=[P('tfc:metal/hammer/red_steel')]),
            Q('electric/alu/excavator', 'Excavator', t_observe('immersiveengineering:excavator',
                                                               title='Look at a formed excavator'),
              icon='immersiveengineering:excavator',
              desc=['The excavator digs the mineral mix under a chunk. A core sample shows which one lies there.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
        ], Q('electric/alu/key', 'Aluminium Plates', t_item('immersiveengineering:plate_aluminum', 32),
             kind='keystone', desc=['Press 32 aluminium plates. Four go into the Humming Core, the rest into the machines of this Age.'],
             rewards=[T('immersiveengineering:component_electronic', 4), P('tfc:metal/pickaxe/red_steel')])),
        ('Circuits', [
            Q('electric/circ/tube', 'Electronics', [t_item('immersiveengineering:electron_tube', 8, title='Vacuum tubes'),
                                                    t_item('immersiveengineering:circuit_board', 4, title='Circuit boards')],
              icon='immersiveengineering:electron_tube',
              desc=['Make vacuum tubes, the first electronics, and the circuit boards that carry them, both at the',
                    'engineer\'s workbench.'],
              rewards=[T('immersiveengineering:wire_electrum', 8), T('immersiveengineering:component_electronic', 2)]),
            Q('electric/circ/assembler', 'Assembler', t_observe('immersiveengineering:assembler',
                                                                title='Look at a formed assembler'),
              icon='immersiveengineering:assembler',
              desc=['The assembler autocrafts grid recipes: the autocrafting of this Age.'],
              rewards=[T('immersiveengineering:component_electronic_adv', 2)]),
            Q('electric/circ/wither', 'Nether Star', t_boss('minecraft:wither', 'minecraft:nether_star'),
              icon='minecraft:nether_star',
              desc=['Fuse a Nether Star in the arc furnace. The Humming Core needs it; the Wither drops one too.'],
              rewards=[P('immersiveengineering:toolbox')]),
        ], Q('electric/circ/key', 'Attuned Circuits', t_item('firmages:attuned_circuit', 16), kind='keystone',
             desc=['A circuit board tuned by quartz, with two advanced electronic components and electrum wire.',
                   'With magic, a spirit attuned crystal tunes it alone. Let the assembler make them.'],
             rewards=[T('immersiveengineering:component_electronic_adv', 4), T('immersiveengineering:plate_electrum', 4)])),
        ('Sky', [
            Q('electric/sky/starsteel', 'Starsteel', t_item('firmages:starsteel_ingot', 2),
              desc=['Melt starsteel meteorite chunks in the arc furnace. The sky is its only source.'],
              rewards=[T('immersiveengineering:graphite_electrode', 3)]),
            Q('electric/sky/light', 'Aurora Light', t_item('firmages:aurora_light_bucket'),
              desc=['Pipe or bucket the aurora light out of your Aurora Nets. Nothing else makes it.'],
              rewards=[T('immersiveengineering:floodlight', 2)]),
        ], Q('electric/sky/key', 'Aurora Steel', t_item('firmages:aurora_steel_ingot', consume=False), kind='keystone',
             desc=['Add the Industrial lens and forge Aurora Steel from starsteel, red steel, storm glass and aurora',
                   'light. The Humming Core needs it.'],
             rewards=[P('tfc:metal/helmet/red_steel')])),
    ],
    goal=Q('electric/goal', 'Offer the Humming Core at the Shrine',
           t_stage('age_6', 'Offer the Humming Core at the shrine'), kind='goal', icon='firmages:humming_core',
           sub='Goal of the Electric Age: Caelum opens the Information Age',
           desc=['Fuse the Humming Core in the arc furnace: an HV capacitor, an Attuned Circuit, four aluminium plates,',
                 'an Aurora Steel ingot and a Nether Star.'],
           rewards=[P('immersiveengineering:glider'), P('tfc:metal/sword/red_steel'), T('firmages:lantern/electric', 4), T('immersiveengineering:capacitor_mv', 2)]),
    sides=[
        ('electric/entry', [proof_quest('electric', 'age_5')]),
        ('electric/circ/tube', [
            Q('electric/side/afrit', 'Bind an Afrit', t_item('occultism:book_of_binding_bound_afrit'), kind='side',
              desc=['Afrits need an HV coil block for their book. They run the Dimensional Battlefield.'],
              rewards=[T('occultism:chalk_red', 4)]),
        ]),
        ('electric/oil/sample', [
            Q('electric/side/cloche', 'Garden Cloche', t_item('immersiveengineering:cloche'), kind='side',
              desc=['A garden cloche grows crops under glass with power and water.'],
              rewards=[T('immersiveengineering:fertilizer', 16)]),
        ]),
        ('electric/alu/bauxite', [
            Q('electric/side/hypertube', 'Hypertubes', t_item('create_hypertube:hypertube', 16), kind='side',
              desc=['Hypertubes shoot players across the base.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
        ]),
    ],
    explains=[
        Q('electric/info/voltage', 'Voltage', t_check(), kind='explain', icon='immersiveengineering:connector_hv',
          desc=['LV, MV and HV wire each take their own connectors. Transformers join the tiers.',
                'Wire that carries too much power burns.']),
    ],
    ring=ring_spec(5, 'the Tesla Crown',
                    ['Raise the Tesla Crown around the Foundry Nave: aluminium, red steel bases with HV capacitors,',
                     'aluminium scaffolding and four floodlights. The heart shows every missing block.']))

# ---------------------------------------------------------------- Information Age
INFORMATION = age_chapter(
    'information', 'information_age', 'Information Age', ['Ore tripled, one network, the End.'],
    'firmages:data_matrix', 'age_6', 7,
    entry=Q('information/entry', 'The Information Age Dawns', t_stage('age_6'), kind='entry',
            icon='mekanism:advanced_control_circuit', sub='The seventh Age',
            desc=['Ore triples, the ME network stores and crafts, and the End waits. Veins of certus quartz,',
                  'inferium, prosperity and soulstone wake.',
                  '',
                  'Four strands lead to the Data Matrix: Digital, Factory, Mob Data and Sky. The End is a side path.',
                  '',
                  proof_line('age_6')]),
    strands=[
        ('Digital', [
            Q('information/digital/certus', 'Certus Quartz', t_item('ae2:certus_quartz_crystal', 16),
              desc=['Certus quartz comes from its own vein.'],
              rewards=[T('ae2:charger')]),
            Q('information/digital/silicon', 'Silicon', t_item('ae2:silicon', 8),
              desc=['Silicon is printed into every processor.'],
              rewards=[T('ae2:inscriber')]),
            Q('information/digital/processors', 'Processors',
              [t_item('ae2:logic_processor', 16, title='Logic processors'),
               t_item('ae2:calculation_processor', 16, title='Calculation processors'),
               t_item('ae2:engineering_processor', 16, title='Engineering processors')],
              icon='ae2:logic_processor',
              desc=['Print 16 of each processor on an inscriber line. Each press is crafted from four blue steel sheets',
                    'around what it prints.'],
              rewards=[T('ae2:fluix_crystal', 8)]),
            Q('information/digital/controller', 'ME Controller', t_item('ae2:controller'),
              desc=['The controller powers an ME network.'],
              rewards=[T('ae2:energy_cell', 2)]),
            Q('information/digital/drive', 'ME Drive', [t_item('ae2:drive', title='ME drive'),
                                                         t_item('ae2:item_storage_cell_1k', title='1k storage cell')],
              icon='ae2:drive', desc=['Build an ME drive and a 1k storage cell. The drive holds the cells; the network',
                                      'reads them all.'],
              rewards=[T('ae2:item_cell_housing', 4)]),
            Q('information/digital/assembler', 'Molecular Assembler',
              [t_item('ae2:molecular_assembler', title='Molecular assembler'),
               t_item('ae2:pattern_provider', title='Pattern provider')],
              icon='ae2:molecular_assembler', desc=['Patterns and assemblers craft on request.'],
              rewards=[T('ae2:pattern_encoding_terminal'), T('ae2:blank_pattern', 16)]),
        ], Q('information/digital/key', 'Autocrafting Online',
             [t_item('ae2:crafting_unit', title='Crafting unit'),
              t_item('ae2:cell_component_64k', 4, title='64k storage components')],
             kind='keystone', icon='ae2:crafting_unit',
             desc=['Build a crafting CPU and let the network craft four 64k storage components: your first',
                   'autocrafting order.'],
             rewards=[T('ae2:16k_crafting_storage', 2), T('ae2:crafting_accelerator', 2), P('ae2:network_tool')])),
        ('Factory', [
            Q('information/mek/osmium', 'Osmium', t_item('mekanism:ingot_osmium', 8),
              desc=['Smelt eight osmium ingots from the vein that woke in the Electric Age. The steel casing needs them.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/mek/crops', 'Resource Crops',
              [t_item('mysticalagriculture:inferium_essence', 32, title='Inferium essence'),
               t_item('mysticalagriculture:prosperity_shard', 8, title='Prosperity shards'),
               t_observe('mysticalagriculture:soulstone', title='Find a body of soulstone')],
              icon='mysticalagriculture:inferium_essence',
              desc=['Prospect for inferium in any rock, prosperity deep in igneous and metamorphic rock and soulstone',
                    'in basalt. Then grow the seeds your Age allows.'],
              rewards=[T('mysticalagriculture:mystical_fertilizer', 16)]),
            Q('information/mek/casing', 'Steel Casing', [t_item('mekanism:steel_casing'), t_item('mekanism:metallurgic_infuser')],
              desc=['Make a steel casing around a heavy engineering block and the metallurgic infuser on it. Every',
                    'machine stands on a casing; the infuser makes alloys.'],
              rewards=[P('mekanism:configurator'), T('mekanism:basic_universal_cable', 16), T('mekanism:alloy_infused', 8)]),
            Q('information/mek/purify', 'Purification Chamber', t_item('mekanism:purification_chamber'),
              desc=['Purification gives three times the metal from every ore piece.'],
              rewards=[T('mekanism:upgrade_speed', 2)]),
            Q('information/mek/tripling', 'Ore Tripling',
              [t_observe('mekanism:purification_chamber[active=true]', kind='block_state', timer=60,
                         title='Watch the chamber run for three seconds'),
               t_item('mekanism:clump_iron', 64, title='Iron clumps')],
              icon='mekanism:clump_iron',
              desc=['Run 64 ore pieces through the chamber and keep the clumps. Three clumps from a piece: this is the',
                    'ore step of your Age.'],
              rewards=[T('mekanism:basic_universal_cable', 8)]),
            Q('information/mek/energy', 'Energy Upgrades', t_item('mekanism:upgrade_energy', 8),
              desc=['Make eight energy upgrades for your ore machines. Without them the fourfold and fivefold ore lines',
                    'cost more power than they gain.'],
              rewards=[T('mekanism:basic_energy_cube')]),
            Q('information/mek/separator', 'Electrolytic Separator', t_item('mekanism:electrolytic_separator'),
              desc=['Split water into hydrogen and oxygen. Hydrogen burns in the gas-burning generator.'],
              rewards=[T('mekanism:basic_chemical_tank', 2)]),
            Q('information/mek/hdpe', 'HDPE', t_item('mekanism:hdpe_sheet', 64),
              desc=['The pressurized reaction chamber makes HDPE from ethene. Lay up 64 sheets: the SPS casings of the',
                    'Quantum Age take them.'],
              rewards=[T('mekanism:basic_pressurized_tube', 8)]),
            Q('information/mek/gas', 'Gas-Burning Generator', t_item('mekanismgenerators:gas_burning_generator'),
              desc=['The FE generator of this Age.'],
              rewards=[T('mekanism:basic_logistical_transporter', 8)]),
            Q('information/mek/blue', 'Blue Steel', t_item('tfc:metal/sheet/blue_steel', 4),
              desc=['Make blue steel, the metal of the Information Age. Two sheets go into the Data Matrix, the rest',
                    'into the inscriber presses.'],
              rewards=[P('tfc:metal/hammer/blue_steel')]),
        ], Q('information/mek/key', 'Factory Line',
             [t_item('mekanism:advanced_smelting_factory', title='Advanced smelting factory'),
              t_item('mekanism:advanced_enriching_factory', title='Advanced enriching factory')],
             kind='keystone', icon='mekanism:advanced_smelting_factory',
             desc=['Set an advanced smelting factory and an advanced enriching factory on the osmium line, energy',
                   'upgrades in both. Five stacks at once; the tally runs through them.'],
             rewards=[T('mekanism:alloy_reinforced', 8)])),
        ('Mob Data', [
            Q('information/mob/learner', 'Deep Learner', [t_item('hostilenetworks:deep_learner'),
                                                          t_item('hostilenetworks:blank_data_model')],
              desc=['Craft the deep learner and a blank data model. The learner trains the model while you fight; a',
                    'blank model learns one mob.'],
              rewards=[P('mekanismtools:refined_glowstone_sword'), T('hostilenetworks:blank_data_model', 2)]),
            Q('information/mob/sim', 'Simulation', [t_item('hostilenetworks:sim_chamber'), t_item('hostilenetworks:loot_fabricator')],
              icon='hostilenetworks:sim_chamber',
              desc=['Set the simulation chamber and the loot fabricator. The chamber turns a trained model and FE into',
                    'matrices, the fabricator matrices into drops.'],
              rewards=[T('ae2:fluix_crystal', 8), T('mekanism:basic_universal_cable', 16)]),
            Q('information/mob/matrix', 'Prediction Matrices', t_item('hostilenetworks:prediction_matrix', 64),
              desc=['Let the simulation run: 64 matrices are machine time, not hand work.'],
              rewards=[T('ae2:energy_cell', 2)]),
            Q('information/mob/wither', 'Wither on Demand', t_item('minecraft:nether_star', 4),
              icon='minecraft:nether_star',
              desc=['Train a Wither data model and print four Nether Stars in the loot fabricator. Awakened draconium',
                    'wants them later.'],
              rewards=[T('hostilenetworks:prediction_matrix', 32)]),
        ], Q('information/mob/key', 'Data Model: Tier 3', t_item('hostilenetworks:data_model',
                                                                 title='A data model trained to tier 3'),
             kind='keystone', icon='hostilenetworks:data_model',
             desc=['Train a data model to tier 3 (Superior) and keep it running in a simulation chamber.'],
             rewards=[P('ae2:wireless_terminal'), T('ae2:wireless_access_point')])),
        ('Sky', [], Q('information/sky/key', 'Halo Steel', t_item('firmages:halo_steel_ingot', 2), kind='keystone',
                      desc=['Add the Electric lens and forge Halo Steel from starsteel, blue steel, firmament crystal and',
                            'aurora light. Two ingots go into the Data Matrix.'],
                      rewards=[P('tfc:metal/helmet/blue_steel')])),
    ],
    goal=Q('information/goal', 'Offer the Data Matrix at the Shrine',
           t_stage('age_7', 'Offer the Data Matrix at the shrine'), kind='goal', icon='firmages:data_matrix',
           sub='Goal of the Information Age: Caelum opens the Space Age',
           desc=['Craft the Data Matrix from two blue steel sheets, two Halo Steel ingots, three advanced control',
                 'circuits, a 64k storage component and a singularity from the matter condenser.'],
           rewards=[P('tfc:metal/sword/blue_steel'), T('ae2:fluix_crystal', 16), T('firmages:lantern/information', 4)]),
    sides=[
        ('information/entry', [proof_quest('information', 'age_6')]),
        # The End is optional (no Age needs it, docs/WALKTHROUGH.md): a side path off the first autocrafting order, not
        # a strand the goal waits for (review B2). The dragon is an optional task beside the breath.
        ('information/digital/key', [
            Q('information/end/enter', 'The End',
              [t_item('minecraft:end_portal_frame', 12, title='End portal frames'),
               t_item('minecraft:ender_eye', 12, title='Eyes of ender'),
               t_dim('minecraft:the_end', 'Step into the End')],
              kind='side', icon='minecraft:end_portal_frame',
              desc=['No stronghold stands here. Ring a 3 by 3 hole with twelve frames facing inwards, fill them with',
                    'twelve eyes of ender and step through.'],
              rewards=[P('mekanism:free_runners'), T('ae2:ender_dust', 8)]),
            Q('information/end/key', "Dragon's Breath",
              [t_item('minecraft:dragon_breath', 4), t_kill('minecraft:ender_dragon', optional=True,
                                                         title='Slay the Ender Dragon (optional)')],
              kind='side', icon='minecraft:dragon_breath',
              desc=['Destroy the crystals, then the dragon, and bottle four breaths of it. Brewers and enchanters want',
                    'them.'],
              rewards=[P('mekanismtools:refined_obsidian_chestplate'), T('ae2:ender_dust', 8)]),
        ]),
        ('information/digital/silicon', [
            Q('information/side/archmage', 'Archmage', t_item('ars_nouveau:archmage_spell_book'), kind='side',
              desc=['The archmage spellbook needs an advanced control circuit.'],
              rewards=[T('arseng:me_source_jar')]),
        ]),
        ('information/mob/learner', [
            Q('information/side/jetpack', 'Hydrogen Jetpack', t_item('mekanism:jetpack'), kind='side',
              desc=['Craft a jetpack and fill it with hydrogen from the separator.'],
              rewards=[P('mekanism:scuba_mask')]),
        ]),
        # firmages-core SPEC 41, recipes/age_6/arcane_catalyst.js (Halo Steel, Arcane Core, steel casing): magic as a
        # bonus, never asked for.
        ('information/sky/key', [
            Q('information/side/catalyst', 'Arcane Catalyst', t_item('firmages:arcane_catalyst'), kind='side',
              desc=['Set an Arcane Catalyst against a processing machine, a factory or an electric multiblock. It needs a',
                    'Source jar within six blocks and a turning shaft at 64 RPM or more beside it.',
                    '',
                    'The machine works 50 % faster and pays the power of each extra tick once more. Generators and',
                    'miners do not take it.'],
              rewards=[T('ars_nouveau:source_jar', 2)]),
        ]),
    ],
    explains=[
    ],
    ring=ring_spec(6, 'the Data Nave',
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
              desc=['Make four elite control circuits. The reactor parts run on them.'],
              rewards=[T('mekanism:upgrade_energy', 4)]),
            Q('space/atom/uranium', 'Yellow Cake', t_item('mekanism:yellow_cake_uranium', 8),
              desc=['Process uraninite ore into yellow cake, the start of fission fuel.'],
              rewards=[P('mekanism:hazmat_mask'), P('mekanism:hazmat_gown')]),
            Q('space/atom/fuel', 'Fuel Assemblies', t_item('mekanismgenerators:fission_fuel_assembly', 4),
              desc=['Build four fuel assemblies. They hold the fissile fuel inside the reactor.'],
              rewards=[P('mekanism:hazmat_pants'), P('mekanism:hazmat_boots')]),
            Q('space/atom/reactor', 'Fission Reactor', t_item('mekanismgenerators:fission_reactor_casing', 32),
              desc=['Build the fission reactor from 32 casings. Cool it well: a hot reactor melts down.'],
              rewards=[T('mekanismgenerators:fission_reactor_port', 2), T('mekanismgenerators:fission_reactor_logic_adapter', 2)]),
            Q('space/atom/turbine', 'Industrial Turbine',
              [t_item('mekanismgenerators:turbine_rotor', 4, title='Turbine rotors'),
               t_item('mekanismgenerators:turbine_blade', 8, title='Turbine blades')],
              icon='mekanismgenerators:turbine_rotor',
              desc=['Build a thermoelectric boiler and an industrial turbine. Together they turn reactor heat into FE.'],
              rewards=[T('mekanismgenerators:turbine_valve', 2), T('mekanismgenerators:turbine_vent', 2)]),
        ], Q('space/atom/key', 'Reactor Online', t_item('mekanism:pellet_polonium', 24), kind='keystone',
             desc=['Draw 24 polonium pellets from your reactor waste. One is the catalyst of the Star Chart; the fusion',
                   'frames and SPS casings of the next Age take the rest.'],
             rewards=[T('mekanismgenerators:electromagnetic_coil', 4)])),
        ('Rocketry', [
            Q('space/moon/workbench', 'NASA Workbench', t_item('ad_astra:nasa_workbench'),
              desc=['Build a NASA workbench. Every rocket is assembled on it.'],
              rewards=[P('ad_astra:oxygen_gear')]),
            Q('space/moon/suit', 'Space Suit',
              [t_item('ad_astra:space_helmet', title='Space helmet'), t_item('ad_astra:space_suit', title='Space suit'),
               t_item('ad_astra:space_pants', title='Space pants'), t_item('ad_astra:space_boots', title='Space boots')],
              icon='ad_astra:space_helmet', desc=['Make a full space suit. Off-world it carries your air.'],
              rewards=[T('ad_astra:oxygen_loader')]),
            # Link 16 (recipes/age_7/links.js): tier 1 and 2 rockets burn #c:diesel or kerosene, tier 3 and 4 hydrogen
            # or cryo fuel; ad_astra:fuel has no recipe in the pack (refinery disabled) and only fuels the rover.
            Q('space/moon/fuel', 'Rocket Fuel',
              t_any_item(['immersivepetroleum:diesel_bucket', 'immersivepetroleum:kerosene_bucket'],
                         'A bucket of diesel or kerosene'), icon='immersivepetroleum:kerosene_bucket',
              desc=['Fill a bucket with diesel or kerosene from your distillation tower. Tier 1 and 2 rockets burn',
                    'either.'],
              rewards=[T('ad_astra:gas_tank', 2)]),
            Q('space/moon/pad', 'Launch Pad', t_item('ad_astra:launch_pad'),
              desc=['Craft a launch pad. Rockets start only from one.'],
              rewards=[T('ad_astra:airlock', 2)]),
            Q('space/moon/rocket', 'Tier 1 Rocket', t_item('ad_astra:tier_1_rocket'),
              desc=['Build the tier 1 rocket. It reaches the Moon and no further.'],
              rewards=[T('ad_astra:oxygen_distributor')]),
            Q('space/moon/land', 'The Moon', t_dim('ad_astra:moon'), icon='ad_astra:moon_sand',
              desc=['Land on the Moon. Desh lies in its rock.'],
              rewards=[T('ad_astra:gravity_normalizer')]),
            Q('space/moon/key', 'Desh Plates', t_item('ad_astra:desh_plate', 32),
              desc=['Mine raw desh on the Moon and press 32 desh plates. The engine and the tanks of the rocket to',
                    'Mars are made from them.'],
              rewards=[T('ad_astra:cable_duct', 8), P('ad_astra:radio')]),
            Q('space/mars/rocket', 'Tier 2 Rocket', t_item('ad_astra:tier_2_rocket'),
              desc=['Build the tier 2 rocket from desh. It reaches Mars.'],
              rewards=[T('ad_astra:engine_frame', 2)]),
            Q('space/mars/land', 'Mars', t_dim('ad_astra:mars'), icon='ad_astra:raw_ostrum', desc=['Land on Mars. Ostrum lies in its red rock.'],
              rewards=[T('ad_astra:oxygen_distributor')]),
            Q('space/mars/key', 'Ostrum Plates', t_item('ad_astra:ostrum_plate', 32),
              desc=['Mine raw ostrum on Mars and press 32 ostrum plates: two for the Star Chart, the rest for the',
                    'rocket to Venus.'],
              rewards=[T('ad_astra:gas_tank', 2), T('ad_astra:cable_duct', 8)]),
        ], Q('space/mars/temple', 'The Mars Temple', t_item('firmages:flight_recorder', consume=False), kind='keystone',
             desc=['Something landed on Mars before you. Its temple keeps the record. The Star Chart needs it.'],
             rewards=[T('ad_astra:space_painting')])),
        ('Draconium', [
            Q('space/drac/ingot', 'Draconium', t_item('draconicevolution:draconium_ingot', 16),
              desc=['Make 16 draconium ingots. The ore lies deep in granite, diorite and gabbro. Every fusion part is',
                    'made of it.'],
              rewards=[T('draconicevolution:draconium_dust', 8)]),
            Q('space/drac/heart', 'Dragon Heart', t_item('draconicevolution:dragon_heart', consume=False),
              desc=['Fuse a dragon heart at wyvern level from a draconium block and core, pellets and ostrum. The',
                    'Ender Dragon drops one too.'],
              rewards=[P('draconicevolution:magnet')]),
            Q('space/drac/core', 'Fusion Crafting Core', t_item('draconicevolution:crafting_core'),
              desc=['Build a fusion crafting core. It is the one pedestal crafting of the tech Ages.'],
              rewards=[T('draconicevolution:infused_obsidian', 4)]),
            Q('space/drac/injectors', 'Injectors',
              [t_item('draconicevolution:basic_crafting_injector', 8, title='Basic injectors'),
               t_item('draconicevolution:wyvern_crafting_injector', 8, title='Wyvern injectors')],
              icon='draconicevolution:wyvern_crafting_injector',
              desc=['Set eight basic and eight wyvern injectors around the core. The wyvern injector fills eight basic',
                    'ones, the awakened core eight wyvern ones.'],
              rewards=[T('draconicevolution:draconium_dust', 8)]),
        ], Q('space/drac/key', 'Wyvern Core', t_item('draconicevolution:wyvern_core'), kind='keystone',
             desc=['Fuse a wyvern core. It is the heart of wyvern gear and of the Star Chart.'],
             rewards=[T('draconicevolution:draconium_dust', 16)])),
        ('Sky', [
            Q('space/sky/complex', 'Launch Complex', t_item('firmages:launch_control'),
              desc=['Build the launch control, a probe cradle and a gantry with a flame trench. Probes fly from it.'],
              rewards=[T('ad_astra:oxygen_gear', 2)]),
            Q('space/sky/voidglass', 'Voidglass', t_item('firmages:voidglass', 2),
              desc=['Launch a probe with a sampler module. It returns with voidglass, found nowhere else.'],
              rewards=[T('draconicevolution:infused_obsidian', 4)]),
        ], Q('space/sky/key', 'Void Steel', t_item('firmages:void_steel_ingot', consume=False), kind='keystone',
             desc=['Add the Information lens and forge Void Steel from voidglass, desh, starsteel and aurora light.',
                   'The Star Chart needs it.'],
             rewards=[T('ad_astra:cryo_freezer')])),
    ],
    goal=Q('space/goal', 'Offer the Star Chart at the Shrine',
           t_stage('age_8', 'Offer the Star Chart at the shrine'), kind='goal', icon='firmages:star_chart',
           sub='Goal of the Space Age: Caelum opens the Quantum Age',
           desc=['Fuse the Star Chart at wyvern level: a polonium pellet in the middle, a desh plate, a Void Steel',
                 'ingot, two ostrum plates, a wyvern core and the flight recorder from Mars.'],
           rewards=[T('ad_astra:tier_1_rover'), T('firmages:lantern/space', 4), T('ad_astra:space_painting')]),
    sides=[
        ('space/entry', [proof_quest('space', 'age_7')]),
        ('space/atom/circuit', [
            Q('space/side/miner', 'Digital Miner', t_item('mekanism:digital_miner'), kind='side',
              desc=['The digital miner clears a whole area by filter. Keep it: a later Age needs it.'],
              rewards=[T('mekanism:basic_logistical_transporter', 16)]),
        ]),
        ('space/atom/uranium', [
            Q('space/side/injection', 'Ore Times Four',
              [t_item('mekanism:chemical_injection_chamber', title='Chemical injection chamber'),
               t_item('moremekanismprocessing:shard_zinc', 8, title='Zinc shards')],
              kind='side', icon='mekanism:chemical_injection_chamber',
              desc=['Hydrogen chloride in the injection chamber breaks ore into shards: four times the metal. Without',
                    'energy upgrades it costs more power per ingot than purification.'],
              rewards=[T('mekanism:basic_chemical_tank', 2)]),
        ]),
        ('space/sky/key', [
            Q('space/side/anchor', 'Sky Anchor', t_item('firmages:sky_anchor'), kind='side',
              desc=['A Sky Anchor carries the shrine\'s blessings within 24 blocks, on the Moon and Mars too. Set',
                    'blessing shards into it.'],
              rewards=[T('firmages:lantern/space', 4)]),
        ]),
        ('space/atom/uranium', [
            Q('space/side/induction', 'Induction Matrix', t_item('mekanism:basic_induction_cell', 4), kind='side',
              desc=['The induction matrix stores FE in its cells and gives it out through its ports.'],
              rewards=[T('mekanism:basic_induction_provider', 2)]),
        ]),
        ('space/moon/workbench', [
            Q('space/side/mega', 'MEGA Cells', t_item('megacells:cell_component_1m'), kind='side',
              desc=['Make a 1M cell component. MEGA cells hold far more than the first cells.'],
              rewards=[T('megacells:mega_item_cell_housing', 2)]),
        ]),
    ],
    explains=[
        Q('space/info/oxygen', 'Oxygen and Gravity', t_check(), kind='explain', icon='ad_astra:oxygen_distributor',
          desc=['Off-world you need a space suit and oxygen. An oxygen distributor fills a sealed base.']),
        Q('space/info/fusion', 'Fusion Crafting', t_check(), kind='explain', icon='draconicevolution:crafting_core',
          desc=['Fusion crafting needs a core, injectors and FE. Every ingredient needs its own injector of the',
                "recipe's tier or better; the core shows what is missing. Tools and armor have direct recipes: no",
                'gear goes into better gear.']),
    ],
    ring=ring_spec(7, 'the Star Spire',
                    ['Raise the Star Spire around the Data Nave: steel plating, desh block pillars and ostrum block',
                     'caps. The heart shows every missing block.']))

# ---------------------------------------------------------------- Quantum Age
QUANTUM = age_chapter(
    'quantum', 'quantum_age', 'Quantum Age', ['Fusion, antimatter and the inner planets.'],
    'firmages:quantum_core', 'age_8', 9,
    entry=Q('quantum/entry', 'The Quantum Age Dawns', t_stage('age_8'), kind='entry',
            icon='mekanism:pellet_antimatter', sub='The ninth Age',
            desc=['Fusion power, the SPS, rockets to Venus and Glacio, awakened draconium and the Marid.',
                  '',
                  'Five strands lead to the Quantum Core: Fusion, Inner Planets, Awakened, Keystone and Sky.',
                  'The Quantum Core is the last offering at the shrine.',
                  '',
                  proof_line('age_8')]),
    strands=[
        ('Fusion', [
            Q('quantum/fusion/frame', 'Fusion Reactor', t_item('mekanismgenerators:fusion_reactor_frame', 32),
              desc=['Build the fusion reactor from 32 frames. Cool it with water and run the steam through an',
                    'industrial turbine: about 840,000 FE/t, against fewer than 100,000 air-cooled.',
                    '',
                    'Keep injection at 20 or more, or it goes out.'],
              rewards=[T('mekanismgenerators:fusion_reactor_port', 2)]),
            Q('quantum/fusion/controller', 'Reactor Controller', t_item('mekanismgenerators:fusion_reactor_controller'),
              desc=['Craft the reactor controller. It sits in the top of the reactor.'],
              rewards=[T('mekanismgenerators:laser_focus_matrix', 2)]),
            Q('quantum/fusion/fuel', 'Fusion Fuel', [t_item('mekanismgenerators:fusion_fuel_bucket'),
                                                     t_item('mekanismgenerators:hohlraum')],
              desc=['Mix deuterium and tritium into fusion fuel and fill a hohlraum with it. A laser fires the',
                    'hohlraum to ignite the reactor.'],
              rewards=[T('mekanismgenerators:fusion_reactor_logic_adapter'), T('mekanismgenerators:turbine_blade', 8)]),
            Q('quantum/fusion/sps', 'SPS', [t_item('mekanism:sps_casing', 16, title='SPS casings'),
                                            t_item('mekanism:supercharged_coil', 2, title='Supercharged coils')],
              icon='mekanism:sps_casing', desc=['Build the SPS from 16 casings and two supercharged coils. It turns polonium into antimatter.'],
              rewards=[T('mekanism:sps_port', 2)]),
            Q('quantum/fusion/key', 'Antimatter Pellets', t_item('mekanism:pellet_antimatter', 2),
              desc=['Make two antimatter pellets in the SPS for the Quantum Core. Each takes 100,000 mB polonium and',
                    '40,000,000,000 FE, about 40 minutes of fusion power.'],
              rewards=[T('evolvedmekanism:overclocked_energy_cube'), T('mekanism:upgrade_energy', 8)]),
        ], Q('quantum/awake/subatomic', 'Subatomic Alloy', t_item('evolvedmekanism:alloy_subatomic', 2, consume=False),
             kind='keystone',
             desc=['Make hypercharged alloy and fuse it with antimatter into two subatomic alloys. The Quantum Core',
                   'and the Awakened Keystone each want one.'],
             rewards=[T('mekanism:upgrade_speed', 4), T('mekanism:ultimate_control_circuit', 2)])),
        ('Inner Planets', [
            Q('quantum/planets/t3', 'Tier 3 Rocket', t_item('ad_astra:tier_3_rocket'),
              desc=['Build the tier 3 rocket from ostrum. It reaches Venus and Mercury and burns liquid hydrogen from a',
                    'rotary condensentrator or cryo fuel, not diesel.'],
              rewards=[T('ad_astra:cryo_fuel_bucket', 2)]),
            Q('quantum/planets/venus', 'Venus', t_dim('ad_astra:venus'), icon='ad_astra:raw_calorite',
              desc=['Land on Venus. Calorite lies under its clouds.'],
              rewards=[T('ad_astra:oxygen_distributor')]),
            Q('quantum/planets/calorite', 'Calorite', t_item('ad_astra:raw_calorite', 16),
              desc=['Mine 16 raw calorite on Venus. It builds the rocket to Glacio.'],
              rewards=[T('ad_astra:oxygen_loader')]),
            Q('quantum/planets/t4', 'Tier 4 Rocket', t_item('ad_astra:tier_4_rocket'),
              desc=['Build the tier 4 rocket from calorite. It reaches Glacio.'],
              rewards=[T('ad_astra:cryo_fuel_bucket', 2)]),
            Q('quantum/planets/glacio', 'Glacio', t_dim('ad_astra:glacio'), icon='ad_astra:ice_shard',
              desc=['Land on Glacio, the frozen world. Ice shards are mined there.'],
              rewards=[T('ad_astra:oxygen_distributor')]),
            Q('quantum/planets/ice', 'Ice Shards', t_item('ad_astra:ice_shard', 8),
              desc=['Mine eight ice shards on Glacio. Two go into the Quantum Core.'],
              rewards=[T('ad_astra:gas_tank', 2)]),
        ], Q('quantum/planets/key', 'Calorite Plates', t_item('ad_astra:calorite_plate', 32), kind='keystone',
             desc=['Keep a store of 32 calorite plates. One goes into the Quantum Core; engines and tanks take the rest.'],
             rewards=[T('ad_astra:large_gas_tank')])),
        ('Awakened', [
            Q('quantum/awake/ingot', 'Awakened Draconium', t_item('draconicevolution:awakened_draconium_ingot', 16),
              desc=['Fuse 16 awakened draconium from draconium blocks, a dragon heart and the four Nether Stars the',
                    'fabricator printed. Draconic gear and the reactor take it.'],
              rewards=[T('draconicevolution:infused_obsidian', 4), T('draconicevolution:draconium_dust', 16)]),
            Q('quantum/awake/injectors', 'Awakened Injectors',
              t_item('draconicevolution:awakened_crafting_injector', 12),
              desc=['Craft twelve awakened injectors. Fusion at draconic level needs them: the chaotic core and the',
                    'reactor parts fill all twelve.'],
              rewards=[T('draconicevolution:draconium_dust', 16)]),
        ], Q('quantum/awake/key', 'Awakened Core', t_item('draconicevolution:awakened_core'), kind='keystone',
             desc=['Fuse the awakened core. It is the catalyst of the Quantum Core.'],
             rewards=[P('draconicevolution:advanced_magnet')])),
        ('Sky', [
            Q('quantum/sky/sample', 'Deep-Space Sample', t_item('firmages:deep_space_sample'),
              desc=['Launch a probe with a deep-space module. It brings back deep-space samples, found nowhere else.'],
              rewards=[T('ad_astra:cryo_fuel_bucket', 2)]),
        ], Q('quantum/sky/key', 'Quasar Alloy', t_item('firmages:quasar_alloy_ingot', consume=False), kind='keystone',
             desc=['Add the Space lens and forge Quasar Alloy from voidglass, calorite, a deep-space sample and aurora',
                   'light. The Quantum Core needs it.'],
             rewards=[T('mekanism:upgrade_speed', 4)])),
    ],
    bridge=Q('quantum/marid/key', 'Awakened Keystone', t_item('firmages:awakened_keystone', consume=False),
             deps=['quantum/awake/subatomic', 'quantum/awake/key'],
             desc=['Take the Arcane Keystone from its plinth (sneak and right-click the plinth with an empty hand), or make',
                   'a second one. Awaken it by fusion at draconic level: the Keystone on the fusion core, four atomic',
                   'alloys, a wyvern core and a subatomic alloy in the injectors. With magic, the Marid ritual does it',
                   'with two atomic alloys.',
                   '',
                   'Lay the Awakened Keystone back on its plinth. It stays the relic of the Arcane Age and goes into',
                   'the Ultimate Singularity.'],
             rewards=[T('evolvedmekanism:overclocked_control_circuit', 2), T('mekanism:alloy_reinforced', 8)]),
    goal=Q('quantum/goal', 'Offer the Quantum Core at the Shrine',
           t_stage('age_9', 'Offer the Quantum Core at the shrine'), kind='goal', icon='firmages:quantum_core',
           sub='Goal of the Quantum Age: Caelum opens the Singularity Age',
           desc=['Fuse the Quantum Core at draconic level: two antimatter pellets, a calorite plate, Quasar Alloy, two',
                 'ice shards and a subatomic alloy.'],
           rewards=[P('mysticalagradditions:imperium_apple'), T('ad_astra:space_painting'), T('firmages:lantern/quantum', 4)]),
    sides=[
        ('quantum/entry', [proof_quest('quantum', 'age_8')]),
        ('quantum/fusion/frame', [
            Q('quantum/side/draconic', 'Draconic Tools', t_item('draconicevolution:draconic_pickaxe'), kind='side',
              desc=['Craft a draconic pickaxe straight from the awakened core. No lesser tool goes into it.'],
              rewards=[T('draconicevolution:draconium_dust', 16)]),
        ]),
        ('quantum/awake/injectors', [
            Q('quantum/marid/book', 'Book of Binding: Marid', t_item('occultism:book_of_binding_marid'), kind='side',
              desc=['Write the Marid\'s name into a book of binding. Without it the Marid will not come.'],
              rewards=[T('occultism:otherworld_essence', 8)]),
            Q('quantum/marid/bind', 'Bind a Marid', t_item('occultism:book_of_binding_bound_marid'), kind='side',
              desc=['Bind the Marid, the strongest of the spirits. Its ritual awakens the Keystone for half the alloy.'],
              rewards=[T('occultism:chalk_red', 4)]),
        ]),
        ('quantum/planets/t3', [
            Q('quantum/side/crystallizer', 'Ore Times Five',
              [t_item('mekanism:chemical_crystallizer', title='Chemical crystallizer'),
               t_item('moremekanismprocessing:crystal_zinc', 8, title='Zinc crystals')],
              kind='side', icon='mekanism:chemical_crystallizer',
              desc=['Dissolution, washing and crystallizing grow ore into crystals: five times the metal. Without energy',
                    'upgrades it costs more power per ingot than injection.'],
              rewards=[T('evolvedmekanism:overclocked_chemical_tank')]),
        ]),
    ],
    explains=[
        Q('quantum/info/last', 'The Last Offering', t_check(), kind='explain', icon='firmages:offering_plinth',
          desc=['The Quantum Core is the ninth relic. The Singularity Age has no offering: it ends in battle.']),
        Q('quantum/info/reactor', 'Power for the End', t_check(), kind='explain',
          icon='mekanismgenerators:fusion_reactor_controller',
          desc=['Fusion through a turbine powers this Age. The next Age asks for more than fusion can give.']),
    ],
    ring=ring_spec(8, 'the Quantum Ring',
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
                  'Three strands lead to the final battle: Chaos, Stargate and Sky.']),
    strands=[
        # Chaos comes from the team's own reactor (recipes/age_9/chaos_bootstrap.js: reactor parts at draconic level,
        # no chaos), so the reactor comes first and the fragments after it, in one strand up to the Ultimate Singularity
        # (review B2). The Chaos Guardian is a side path off "Chaos Fragments". The Gathering and the singular alloy need
        # no chaos and stand in the Sky strand.
        ('Chaos', [
            Q('singularity/chaos/parts', 'Reactor Parts',
              [t_item('draconicevolution:reactor_stabilizer', 4, title='Reactor stabilizers'),
               t_item('draconicevolution:reactor_injector', title='Reactor injector')],
              icon='draconicevolution:reactor_stabilizer',
              desc=['Build four stabilizers and an injector at draconic level. They hold the reactor core in its field.'],
              rewards=[T('draconicevolution:draconium_dust', 16)]),
            Q('singularity/chaos/fuel', 'Reactor Fuel', t_item('draconicevolution:awakened_draconium_block', 4),
              desc=["Make four awakened draconium blocks. They are the reactor's first fuel."],
              rewards=[T('evolvedmekanism:overclocked_energy_cube')]),
            Q('singularity/chaos/key', 'Reactor Online',
              t_observe('draconicevolution:reactor_core', title='Look at your placed reactor core'),
              icon='draconicevolution:reactor_core',
              desc=['Build the draconic reactor and bring it online. Its spent fuel comes out as chaos fragments, and',
                    'only it can power the Stargate.'],
              rewards=[T('evolvedmekanism:overclocked_energy_cube'), T('draconicevolution:draconium_dust', 16)]),
            Q('singularity/chaos/shards', 'Chaos Fragments', t_item('draconicevolution:large_chaos_frag', 20),
              desc=['Keep 20 large and 36 medium fragments of spent fuel: twelve chaotic injectors, the chaotic core,',
                    'the Singularity and the Coordinates.'],
              rewards=[T('draconicevolution:flux_gate')]),
            Q('singularity/chaos/injectors', 'Chaotic Injectors',
              t_item('draconicevolution:chaotic_crafting_injector', 12),
              desc=['Craft twelve chaotic injectors from fragments and antimatter. Chaotic fusion, the highest tier,',
                    'needs them: the Ultimate Singularity fills all twelve.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('singularity/sing/core', 'Chaotic Core', t_item('draconicevolution:chaotic_core'),
              desc=['Fuse the chaotic core. It is the catalyst of the Ultimate Singularity.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
        ], Q('singularity/sing/key', 'The Ultimate Singularity', t_item('firmages:ultimate_singularity'),
             kind='keystone', icon='firmages:ultimate_singularity', deps=['singularity/sing/core', 'singularity/sky/key'],
             desc=['Forge the Ultimate Singularity from the nine relics, chaos fragments, the Firmament Alloy and the',
                   'chaotic core in a chaotic fusion.'],
             rewards=[P('minecraft:enchanted_golden_apple', 2)])),
        ('Stargate', [
            Q('singularity/gate/naquadah', 'Naquadah', [t_dim('ad_astra:mercury', 'Land on Mercury'),
                                                       t_item('sgjourney:raw_naquadah', 16, title='Raw naquadah')],
              icon='sgjourney:raw_naquadah',
              desc=['Fly the tier 3 rocket to Mercury and mine 16 raw naquadah there with a digital miner. No other',
                    'tool reaches it.'],
              rewards=[P('sgjourney:transceiver'), T('ad_astra:space_painting')]),
            Q('singularity/gate/crystallizer', 'Crystallizer',
              [t_item('sgjourney:crystallizer', title='Crystallizer'),
               t_item('sgjourney:naquadah_liquidizer', title='Naquadah liquidizer')],
              icon='sgjourney:crystallizer',
              desc=["Build a crystallizer and a naquadah liquidizer. The gate's 32 crystals grow in the crystallizer",
                    'from gems, a crystal base and liquid naquadah.'],
              rewards=[T('sgjourney:raw_naquadah', 8)]),
            Q('singularity/gate/blocks', 'Stargate Blocks',
              [t_item('sgjourney:classic_stargate_ring_block', 14, title='Ring blocks'),
               t_item('sgjourney:classic_stargate_chevron_block', 9, title='Chevron blocks'),
               t_item('sgjourney:classic_stargate_base_block', title='Base block')],
              icon='sgjourney:classic_stargate_chevron_block',
              deps=['singularity/gate/crystallizer', 'singularity/sing/key'],
              desc=['Infuse naquadah with refined obsidian, cast a base block, nine chevrons and 14 ring blocks.',
                    'Chevrons take three crystals, the base four and the Singularity.'],
              rewards=[T('sgjourney:diamond_graver'), T('sgjourney:steel_iris')]),
            Q('singularity/gate/dhd', 'Dial Home Device', t_item('sgjourney:classic_dhd'),
              desc=['Build the Dial Home Device around a communication crystal. It dials the gate.'],
              rewards=[P('sgjourney:gdo')]),
        ], Q('singularity/gate/key', 'Gate Online',
             t_observe('sgjourney:classic_stargate', title='Look at your assembled Stargate'),
             kind='keystone', icon='sgjourney:classic_stargate',
             desc=['Assemble the Stargate from its base, chevron and ring blocks; the draconic reactor powers it',
                   'through a flux gate.',
                   '',
                   'Its address, on the Coordinates of The Origin: 9, 16, 21, 33, 2, 37, then the point of origin.',
                   '',
                   'Dialing The Origin takes 1,000,000,000 FE stored in the gate and 50,000 FE/t while the wormhole',
                   'stays open; a gate in your own system takes 10,000,000 FE. Fill the gate from the reactor before you',
                   'dial. The gate in The Origin always keeps one trip home charged.'],
             rewards=[T('sgjourney:naquadah_iris'), T('evolvedmekanism:overclocked_energy_cube')])),
        ('Sky', [
            # The Gathering (firmages-core 0.9): one prayer at the complete shrine in this Age gives the nine relics
            # back and sets the heart to awakened=10 (ShrineHeartBlock allows 0..10, ShrineService keeps it while
            # ShrineSavedData.gathered).
            Q('singularity/sing/relics', 'The Gathering',
              t_observe('firmages:shrine_heart[awakened=10]', kind='block_state',
                        title='Look at the heart after the Gathering'),
              icon='firmages:shrine_heart',
              desc=['Feed 1,000,000,000 FE into the heart, then pray at the complete shrine with your fellowship.',
                    'Caelum gives back the nine relics for the chaotic fusion.'],
              rewards=[T('firmages:lantern/singularity', 4)]),
            Q('singularity/sing/alloy', 'Singular Alloy', t_item('evolvedmekanism:alloy_singular'),
              desc=['Make the singular alloy. The full Lens Crown forges it into the Firmament Alloy, which binds the nine relics into one.'],
              rewards=[T('mekanism:alloy_reinforced', 8)]),
        ], Q('singularity/sky/key', 'Firmament Alloy', t_item('firmages:firmament_alloy_ingot', consume=False),
                      kind='keystone',
                      desc=['Set the Quantum lens and forge the Firmament Alloy under the full crown from the singular',
                            'alloy and the sky materials of every Age. The Ultimate Singularity needs it.'],
                      rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)])),
    ],
    goal=Q('singularity/goal', 'Beyond the Firmament', t_stage('finale_won', 'Defeat the final boss in The Origin'),
           kind='goal', icon='sgjourney:classic_stargate', sub='Goal of the Singularity Age: the end of the road',
           desc=['Dial The Origin and step through the gate. Gather at the altar, all of you, and The Primordial',
                 'wakes. Its veil opens only when you answer its attacks.',
                 '',
                 'Its fall completes the Firmament. The MekaSuit and the Meka-Tool can then be made.'],
           rewards=[T('minecraft:dragon_egg'), T('firmages:lantern/singularity', 8), P('mysticalagradditions:insanium_apple')]),
    # Into The Origin: the step from the gate to the battle, on the main route (third player's-eye review).
    bridge=Q('singularity/side/origin', 'Into The Origin',
             t_dim('firmages:origin', 'Step through the gate into The Origin'), deps=['singularity/gate/key'],
             icon='firmages:origin_coordinates',
             desc=['Step through the gate. Gather, all of you, at the central altar to call the final battle.',
                   'A gate on the arena dials home.'],
             rewards=[P('minecraft:totem_of_undying')]),
    sides=[
        # The Chaos Guardian stays optional (no Age needs a boss): its kill, or a chaos shard, which nine large
        # fragments press into, completes it (review B2: it stood first in the Chaos strand).
        ('singularity/chaos/shards', [
            Q('singularity/chaos/guardian', 'Chaos Guardian',
              t_boss('draconicevolution:draconic_guardian', 'draconicevolution:chaos_shard'), kind='side',
              icon='draconicevolution:chaos_shard',
              desc=['The Chaos Guardian waits on a Chaos Island in the End; its crystals drop chaos shards. Nine',
                    'large fragments press into a shard too.'],
              rewards=[P('mysticalagradditions:insanium_apple')]),
        ]),
        ('singularity/chaos/shards', [
            Q('singularity/side/armor', 'Chaotic Armor', t_item('draconicevolution:chaotic_chestpiece'), kind='side',
              desc=['The chaotic chestpiece has a direct recipe. Wear it into the final battle.'],
              rewards=[P('sgjourney:naquadah_chestplate')]),
        ]),
        ('singularity/sing/core', [
            Q('singularity/side/staff', 'Chaotic Staff', t_item('draconicevolution:chaotic_staff'), kind='side',
              desc=['A tool for everything, built directly from the chaotic core.'],
              rewards=[T('draconicevolution:draconium_dust', 16)]),
        ]),
        # firmages-core SPEC 40: the last step of the ore ladder (recipes/age_9/ore_processor.js, Firmament Alloy shell).
        ('singularity/sky/key', [
            Q('singularity/side/processor', 'Ore Times Six', t_item('firmages:ore_processor'), kind='side',
              desc=['Feed the Ore Processor ore pieces, hydrofluoric acid and kerosene: six times the metal as dust,',
                    '150,000 FE each. Lubricant doubles its speed.'],
              rewards=[T('immersivepetroleum:kerosene_bucket', 4), T('immersivepetroleum:lubricant_bucket', 2)]),
        ]),
        # The shards exist only while The Primordial lives (firmages-core boss, docs/primordial.md); after the gate.
        ('singularity/gate/key', [
            Q('singularity/side/shard', 'A Piece of the Sky', t_item('firmages:consecrated_shard'), kind='side',
              desc=["Keep a shard from The Primordial's last phase: they lie a minute and never come again.",
                    'Four glass and a shard make four Firmament Glass.'],
              rewards=[T('firmages:lantern/singularity', 4)]),
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
    ],
    )

AGE_CHAPTERS = [DAWN, STONE, BRONZE, IRON, ARCANE, INDUSTRIAL, ELECTRIC, INFORMATION, SPACE, QUANTUM, SINGULARITY]

# Goal quests that grant the next Age as a quest reward. Only Dawn's First Spark: the shrine is built in the Stone
# Age. From the Stone Age on the shrine grants the Age (firmages-core reads data/firmages/firmages_shrine/
# offerings.json) and the goal is a gamestage task on the next Age without a stage reward (a second grant path
# would double the ceremony).
GOAL_GRANTS = {'dawn': 'age_0'}

# ---------------------------------------------------------------- The Firmament
# The first chapter of the book (Felix 2026-10-15: Welcome goes, its read-me-first content moves here). Three parts:
#  - read-me-first notes (left): waking, keys, the fellowship, the shrine, crafting by hand. Written in-world (Felix
#    2026-10-13: full immersion): no mod names, no recipe viewer, no talk of locks or stages, and no word on what an
#    Age opens or takes away (Felix G1, 2026-10-17: players find out). Keys of the welcome and Dawn notes are kept, so
#    their ids and any ticked progress stay. G1 dropped "Reading the Chronicle", "The Sign in the Corner" and "What
#    Dawn Withholds".
#  - the road: every Age's offering on one spiral that winds in to "Beyond the Firmament"; each offering appears when
#    its Age dawns (decision A, 2026-10-14).
#  - the build guide of each shrine ring in its offering's marker (G4, Felix: no separate guide diamonds): the ring
#    rising as an animation, one page per layer with its blocks, the offering and the rite, with the finished ring as
#    a picture beside the marker. The pictures and the block counts come from dev/gen_quest_pictures.py
#    (dev/data/quest_pictures.json).
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
    # G4 (Felix, in game on 1.2.0): "By Hand" merged in here, the sneak-key sentence dropped.
    Q('firmament/note/keys', 'Hands and Keys', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:stick',
      desc=['Press K to open the Chronicle. Hold H to see the sign of your Age again.',
            '',
            'Hold an item and press V to lay it on a block; up to four items fit on one block, one in each corner.',
            '',
            'Lay the parts together, then sneak and right-click the pile with an empty hand. If they make',
            'something, you get it. Where on the block each part lies does not matter.']),
    Q('welcome/team', 'One Fellowship', t_check(), deps=['welcome/start'], kind='info', icon='minecraft:player_head',
      desc=['Everybody on this server is one fellowship. The Ages are shared by all of you.',
            'Split the strands between you: one friend smiths, one prospects, one builds.',
            '',
            'A keystone\'s supplies go to whoever claims them first. The gift of a side path and all experience come to each of you.']),
    # The finished shrine on top (G1): dev/gen_quest_pictures.py draws shrine/complete.png; until it is drawn the line
    # takes the ring pictures' aspect (396 x 292) and dev/validate_quests.py reports the missing picture.
    Q('firmament/note/shrine', 'The Shrine of Caelum', t_check(), deps=['welcome/start'], kind='info',
      icon='firmages:wayshrine_stone',
      # G4 (Felix): the paragraph on the build guides went; the guides now sit in the road markers themselves.
      desc=[Verbatim(image_line('firmages:textures/gui/quests/shrine/complete.png', size=(396, 292))), '',
            'From the Stone Age on, every Age ends at the shrine. Raise the ring of the Age round the heart, lay the '
            'offering on the plinth and pray.']),
    # "By Hand" (ground crafting, HW2A) was deleted by Felix in game (G4); its text lives in "Hands and Keys".
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


# The road's first quest waits for "You Wake" (Felix G4, in game: "By Hand" is gone).
FIRST_SPARK_DEPS = ['welcome/start']

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
    """The road marker of ring n's Age in The Firmament: it carries the build guide (G4)."""
    return 'firmament/' + FIRMAMENT[n + 1][0]


def shrine_texture(n, kind='map'):
    return PIC + ('shrine/ring_%d.png' % n if kind == 'map' else 'shrine/ring_%d_%s.png' % (n, kind))


def ring_picture(n, width=200):
    """The finished ring, clickable: opens its Age's road marker in The Firmament, which carries the build guide."""
    return picture('shrine/ring_%d.png' % n, RINGS[n]['pictures']['ring'], width,
                   click='open_quest:' + qid(guide_key(n)), hover='Open the build guide')


def block_list(blocks):
    out = []
    for name, c in blocks:
        out.append('the ' + name if name in ('Offering Plinth', 'Shrine Heart') else '%s %s' % (amount(c), name))
    return out


def layer_place(y):
    return {0: 'level with the heart', 1: 'one block up'}.get(y, '%s blocks up' % amount(y))


def guide_desc(n):
    """Build guide of ring n, the description of its road marker (G4): page 1 the ring rising and its blocks, one page
    per layer, last page offering and rite."""
    info = RINGS[n]
    name = info['name']
    offering = FIRMAMENT[n + 1][1]
    layers = info['layers']
    first = ('Build %s round the Shrine Heart.' % name if n == 0 else
             'Raise %s round %s.' % (name, RING_NAMES[n - 1]))
    count = amount(info['count'])
    desc = [picture('shrine/ring_%d_build.apng.png' % n, info['pictures']['build'], 220)]
    if n == 0:   # where the heart and the plinth come from (Stone Age, Hearth & Shrine), so ring 0 builds from here
        desc += ['Craft the Shrine Heart from a fired Hearth Idol, cobblestone and charcoal (Stone Age, Hearth & Shrine).',
                 'Set it where 21 by 21 blocks stay free: each Age adds a ring. The Offering Plinth is four cobblestone.',
                 '']
    desc += ['%s %s new blocks in %s layers:' % (first, count[0].upper() + count[1:], amount(len(layers)))]
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
    return desc


# ------------------------------------------------------------------------------------------------ layout hints
# dev/quest_layout.py: related {(strand, strand): 0..1} (shared stations, items or craft; default 0.3), style per
# strand (arc, meander, circle), axis (the strand that ends next to the shrine picture), shrine (ring shown on the
# axis; its click opens the build guide), order (slot order from the top-west round to the bottom-west, in place of
# the search by relatedness). Strand names must match the chapter definitions; an unknown name falls back to the
# defaults. Review 0.13.0: Electric, Information, Space, Quantum and Singularity Age carry an order, picked so that no
# two lines cross more than validate_quests.py --style allows (LINE_LIMITS) and no line runs over a quest.
LAYOUT_HINTS = {
    'dawn': dict(related={('Stone', 'Hunt'): 0.7, ('Stone', 'Shelter'): 0.5, ('Shelter', 'Hunt'): 0.6,
                          ('Stone', 'Sky'): 0.2, ('Hunt', 'Sky'): 0.3},
                 style={'Stone': 'meander', 'Shelter': 'arc', 'Hunt': 'arc', 'Sky': 'arc'},
                 order=['Stone', 'Shelter', 'Sky', 'Hunt']),
    'stone': dict(related={('First Metal', 'Hearth & Shrine'): 0.9, ('Camp', 'First Metal'): 0.1,
                           ('Camp', 'Hearth & Shrine'): 0.2},
                  style={'First Metal': 'arc', 'Camp': 'circle', 'Hearth & Shrine': 'arc'},
                  axis='Hearth & Shrine', shrine=0, order=['Camp', 'First Metal', 'Hearth & Shrine']),
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
                            'Sky': 'arc'}, shrine=5,
                     order=['Power Grid', 'Sky', 'Circuits', 'Oil', 'Aluminium & Excavator']),
    'information': dict(related={('Digital', 'Factory'): 0.8, ('Digital', 'Mob Data'): 0.6, ('Digital', 'Sky'): 0.5},
                        style={'Digital': 'arc', 'Factory': 'meander', 'Mob Data': 'circle', 'Sky': 'arc'}, shrine=6,
                        order=['Sky', 'Digital', 'Factory', 'Mob Data']),
    'space': dict(related={('Atomic', 'Rocketry'): 0.5, ('Atomic', 'Draconium'): 0.5, ('Sky', 'Rocketry'): 0.7},
                  style={'Atomic': 'circle', 'Rocketry': 'meander', 'Draconium': 'arc', 'Sky': 'arc'}, shrine=7,
                  order=['Draconium', 'Atomic', 'Rocketry', 'Sky']),
    'quantum': dict(related={('Fusion', 'Awakened'): 0.9, ('Inner Planets', 'Fusion'): 0.4, ('Sky', 'Inner Planets'): 0.5},
                    style={'Fusion': 'circle', 'Inner Planets': 'meander', 'Awakened': 'arc', 'Sky': 'arc'}, shrine=8,
                    order=['Fusion', 'Sky', 'Inner Planets', 'Awakened']),
    'singularity': dict(related={('Chaos', 'Stargate'): 0.6, ('Stargate', 'Sky'): 0.5},
                        style={'Chaos': 'circle', 'Stargate': 'arc', 'Sky': 'arc'},
                        order=['Sky', 'Chaos', 'Stargate']),
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
        if r is None:   # XP_SLOT: the reward ids of the other rewards keep their number
            continue
        d = dict(r)
        d['id'] = qid('%s/reward/%d' % (quest['key'], n))
        out.append(d)
    return out


# Shapes gilded, rune and ember are the pack's own (dev/gen_quest_art.py writes them to
# kubejs/assets/ftbquests/textures/shapes/<name>/; FTB Quests only looks in its own namespace, javap QuestShape).
# Steps keep the chapter default (hexagon); explanations stay gears.
SHAPES = {'entry': ('rune', 2.0), 'step': ('', 0.0), 'keystone': ('rune', 1.5), 'goal': ('gilded', 3.0),
          'side': ('ember', 0.85), 'explain': ('gear', 0.0), 'info': ('', 0.0), 'note': ('rune', 1.6)}

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
    if quest['kind'] in ('side', 'explain'):
        d['optional'] = True
    if quest['icon']:
        d['icon'] = {'id': quest['icon']}
    if rewards:
        d['rewards'] = build_rewards(quest, rewards)
    if quest['kind'] in ('entry', 'keystone', 'goal', 'side', 'explain', 'info', 'note'):
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
    dev/quest_layout.py. Strand steps follow each other from the entry (a step with its own deps keeps them: a
    prerequisite from another strand, rules B and C of G4), a keystone follows its last step, the goal needs every
    keystone (and the bridge), routes replace the keystones as the goal's dependencies; side paths start at their
    attach quest, notes at the entry (their lines hidden)."""
    entry = ch['entry']
    kinds, deps, hide = {entry['key']: 'entry'}, {}, set()
    origin = entry['key']
    for t in ch['trunk']:
        t['deps'] = t['deps'] or [origin]
        kinds[t['key']] = t['kind']
        origin = t['key']
    strands = []
    for name, steps, keystone in ch['strands']:
        prev, keys = origin, []
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
        # the keystones the bridge already waits for reach the goal through it (G4: no second line over the bridge)
        goal['deps'] = [k for k in goal['deps'] if k not in bridge['deps']] + [bridge['key']]
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
    titles = {}
    for q_ in chapter_quests(ch):
        deps[q_['key']] = list(q_['deps'])
        titles[q_['key']] = q_['title']
    satellites = []
    for w in WORKS:   # the links to the Great Works plans sit beside their hook quest (quest_link)
        if WORK_HOOKS[w['key']][0] == ch['key']:
            satellites.append((WORK_HOOKS[w['key']][1], 'link/' + w['key'], LINK_SIZE))
            titles['link/' + w['key']] = w['title']
    return dict(key=ch['key'], entry=entry['key'], goal=goal['key'], strands=strands, sides=sides,
                trunk=[t['key'] for t in ch['trunk']],
                explains=[e['key'] for e in ch['explains']], ring=None, links=links,
                kinds=kinds, deps=deps, hide_lines=hide, titles=titles, satellites=satellites)


def chapter_quests(ch):
    """Every quest of an Age chapter, in book order."""
    out = [ch['entry']] + list(ch['trunk'])
    for _name, steps, keystone in ch['strands']:
        out += steps + [keystone]
    if ch.get('bridge'):
        out.append(ch['bridge'])
    out += list(ch.get('routes') or [])
    out.append(ch['goal'])
    for _attach, line in ch['sides']:
        out += line
    out += ch['explains']
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


# Quest kinds that pay only XP: they ask for reading, not work (B2 reward audit). Every other quest of an Age, a Side
# Road or a Great Work carries its own item rewards (Q(rewards=[T(...), P(...)])); the generator stops otherwise.
INFO_KINDS = {'entry', 'explain', 'info', 'note'}
XP_MULT = {'keystone': 3, 'goal': 10}


# Until G1 the XP stood first and every item reward after it; its slot stays empty, so the reward ids
# (qid('<key>/reward/<n>')) and every claim on a running world stay as they were.
XP_SLOT = None


def quest_rewards(quest, xp, mirror=False):
    """The quest's own items, or the XP of the Age (times the kind's multiplier) on a quest without items: never both
    (XP rule above r_item). mirror: a quest that only mirrors another one's task (the road of offerings in The
    Firmament) pays nothing; the memorable reward sits on the goal."""
    if mirror:
        return []
    if quest['kind'] in INFO_KINDS:
        if quest['rewards']:
            raise SystemExit('%s: an info quest pays XP only, no items' % quest['key'])
        return [r_xp(xp * XP_MULT.get(quest['kind'], 1))] if xp else []
    if not quest['rewards']:
        raise SystemExit('%s: no item reward (every quest that asks for work pays items of its Age)' % quest['key'])
    return [XP_SLOT] + list(quest['rewards'])


def shrine_goal(ch):
    """The goal of a shrine Age with its ring folded in (G4): tasks ring, offering, next Age; three pages: the
    offering's recipe with the finished ring (a click opens the layer-by-layer guide in The Firmament), the ring, then
    the offering, the proof of work and the rite."""
    ring = ch['ring']
    n = ring['n']
    goal = dict(ch['goal'])
    offering = next(f for f in FIRMAMENT if f[2] == 'age_%d' % n)
    goal['tasks'] = [t_observe('firmages:shrine_heart[awakened=%d,ready=true]' % n, kind='block_state',
                               title='Look at the heart with %s complete' % ring['name']),
                     t_item(offering[4], consume=False, title='The %s in hand' % offering[1])] + list(goal['tasks'])
    desc = list(goal['desc']) + ['', ring_picture(n, 180), PAGE] + ring['desc']
    desc += [PAGE, 'Lay the %s on the new plinth.' % offering[1]]
    if 'age_%d' % n in TALLIES:
        desc += ['Your Tally Chutes must have taken %s in this Age.' % tally('age_%d' % n)]
    desc += ['', 'The rite:'] + ['- ' + r for r in RINGS[n]['rites']]
    desc += ['', 'Then sneak and hold right-click on the heart with an empty hand. Caelum keeps the %s and opens'
             ' the %s.' % (offering[1], AGE_NAME['age_%d' % (n + 1)])]
    goal['desc'] = desc
    return goal


def build_age_chapter(ch):
    age = ch['stage']
    xp = XP[age]
    if ch.get('ring'):
        ch = dict(ch, goal=shrine_goal(ch))
    spec = wire_age_chapter(ch)
    lay = quest_layout.age_chapter(spec, LAYOUT_HINTS.get(ch['key']))
    quests = [laid_out(lay, ch['entry'], quest_rewards(ch['entry'], xp))]
    for t in ch['trunk']:
        quests.append(laid_out(lay, t, quest_rewards(t, xp)))
    for _name, steps, keystone in ch['strands']:
        for s in steps:
            quests.append(laid_out(lay, s, quest_rewards(s, xp)))
        quests.append(laid_out(lay, keystone, quest_rewards(keystone, xp)))
    if ch.get('bridge'):
        quests.append(laid_out(lay, ch['bridge'], quest_rewards(ch['bridge'], xp)))
    for r in ch.get('routes') or []:
        quests.append(laid_out(lay, r, quest_rewards(r, xp)))
    goal_rewards = quest_rewards(ch['goal'], xp)
    if ch['key'] in GOAL_GRANTS:
        goal_rewards.insert(1, r_stage(GOAL_GRANTS[ch['key']]))
    goal_extra = {'dependency_requirement': 'one_completed'} if ch.get('routes') else None
    quests.append(laid_out(lay, ch['goal'], goal_rewards, extra=goal_extra))
    for _attach, line in ch['sides']:
        for s in line:
            quests.append(laid_out(lay, s, quest_rewards(s, xp)))
    for e in ch['explains']:
        quests.append(laid_out(lay, e, [], extra={'hide_dependency_lines': True}))
    images = [shrine_image(ch['key'], lay, im) for im in lay.images if im['kind'] == 'shrine']
    images.insert(0, top_banner(ch['key'], age, lay))
    LAYOUTS[ch['filename']] = lay
    links = [quest_link(ch['key'], lay, w) for w in WORKS if WORK_HOOKS[w['key']][0] == ch['key']]
    return chapter_nbt(ch['key'], ch['filename'], ch['title'], ch['subtitle'], ch['icon'], ch['group'], ch['order'],
                       quests, required_stage=age, tags=[age], images=images,
                       extra={'autofocus_id': qid(ch['goal']['key']), 'quest_links': links})


def build_firmament():
    """Read-me-first notes on the left, the road of offerings as a spiral that winds in to "Beyond the Firmament";
    every offering's marker carries the build guide of its Age's ring, the finished ring as a picture beside it."""
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
                       deps=['firmament/' + FIRMAMENT[i - 1][0]] if i else FIRST_SPARK_DEPS)
        node.stiff = True
        lay.tang[node.key] = ((x2 - x) / d, (y2 - y) / d)
        if i:
            flow.append(('firmament/' + FIRMAMENT[i - 1][0], node.key))
    lay.nodes['firmament/beyond'].fixed = True
    # the finished rings outside the spiral, each beside its marker
    for n in sorted(RINGS):
        x, y = road_pos[n + 1]
        d = math.hypot(x, y) or 1.0
        ox, oy = x / d, y / d
        w = 3.2
        size = RINGS[n]['pictures']['ring']
        lay.images.append(dict(kind='shrine', ring=n, x=x + ox * 3.4, y=y + oy * 2.6, w=w, h=w * size[1] / size[0],
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
    for nq in FIRMAMENT_NOTES:
        lay.labels[nq['key']] = nq['title']
    for key, title, _age, _stage, _icon in FIRMAMENT:
        lay.labels['firmament/' + key] = title
    lay.relax()
    lay.curves(flow)

    for nq in FIRMAMENT_NOTES:
        quests.append(laid_out(lay, nq, quest_rewards(nq, 5)))
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
            desc = ['The first fire. It opens the Stone Age.'] if dawn else guide_desc(i - 1)
        q_ = Q('firmament/' + key, title, t_stage(stage, task), deps=[prev] if prev else FIRST_SPARK_DEPS,
               kind='goal' if final else 'keystone', icon=icon, sub=sub, desc=desc)
        # Decision A (2026-10-14): each offering appears when its Age dawns (its predecessor's Age is reached). The
        # first spark stays in view from the start; "Beyond the Firmament" waits for the Singularity Age (Felix, B2).
        extra = {'hide_until_deps_complete': True} if prev else None
        quests.append(laid_out(lay, q_, quest_rewards(q_, 0, mirror=True), extra=extra))
        prev = q_['key']
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


# ---------------------------------------------------------------- the Side Roads
# One optional chapter per Age in the group "Side Roads" (B2, 2026-10-16; content lock: every mod that is not
# decoration gets a strand of the Chronicle). A road is a row of two to seven side quests that branches off one
# quest of the Age chapter: its first quest depends on that quest (FTB Quests draws no line between chapters, so the
# first quest's subtitle names the road and the branch). Every quest pays an item of its Age. The chapter is hidden
# until its Age dawns, like the Age chapter. Rows: strands in the order written, one row each, the first quest at
# the left; dev/preview_quests.py draws them. Texts: dev/style-guide.md (side quest: 25 words, no mod names).
# Source of the roads: scratchpad b2/story/strands.json (mod inventory of 264 mods: 106 content mods, 59 roads).
# The twenty TFC saplings (TFC Regrowing Forests regrows the TFC forests; every climate has one of them), from the
# registry dump: the task list stays short enough for the quest window.
SAPLINGS = sorted(i for i in json.load(open(os.path.join(ROOT, 'dev', 'data', 'registry.json'), encoding='utf-8'))['items']
                  if re.fullmatch(r'tfc:wood/sapling/[a-z_]+', i))
TFC_RUINS = ['tfc_ruins:ruin_small', 'tfc_ruins:ruin_beach', 'tfc_ruins:ruin_rich', 'tfc_ruins:ruin_basement']
LAMPS_IRON = ['tfc:metal/lamp/wrought_iron', 'tfc:metal/lamp/steel']

ROADS = [
    dict(key='dawn', filename='roads_dawn', stage='dawn', title='Dawn Side Roads', strands=[
        # Strange Beasts (Alex's Mobs Continued)
        ('Strange Beasts', 'dawn/hunt/kill', [
            Q('dawn/beast/hide', 'Kangaroo Hide', t_item('alexsmobs:kangaroo_hide'), kind='side',
              desc=['Hunt a kangaroo on open, dry ground. Its hide is tough and warm.'],
              rewards=[T('tfc:rope', 4)]),
            Q('dawn/beast/egg', 'Emu Egg', t_item('alexsmobs:emu_egg'), kind='side',
              desc=['Take an egg from an emu. Boiled over the fire it makes a meal.'],
              rewards=[P('tfc:food/cooked_venison', 3)]),
            Q('dawn/beast/feather', 'Roadrunner Feather', t_item('alexsmobs:roadrunner_feather'), kind='side',
              desc=['Chase a roadrunner across the flats and take its feather. It is quicker than you.'],
              rewards=[T('tfc:jute_fiber', 8)]),
            Q('dawn/beast/rattle', 'Rattle in the Sand', t_item('alexsmobs:rattlesnake_rattle'), kind='side',
              desc=['Kill a rattlesnake and take its rattle. It warns only once.'],
              rewards=[P('tfc:food/cooked_rabbit', 3), T('tfc:straw', 8)]),   # G1: no hide (Dawn side "Hides")
        ]),
    ]),
    dict(key='stone', filename='roads_stone_age', stage='age_0', title='Stone Age Side Roads', strands=[
        # Goblins Below (Goblin Traders)
        ('Goblins Below', 'stone/camp/seeds', [
            Q('stone/gob/fruit', 'Fruit for Seed',
              t_any_item(['tfc:food/red_apple', 'tfc:food/green_apple'], 'Six apples, red or green', count=6), kind='side',
              desc=['Go deep and find a goblin trader below the surface. It walks the dark with a pack',
                    'of goods and pays seed for fruit.'],
              rewards=[T('minecraft:clay_ball', 8)], icon='tfc:food/red_apple'),
            Q('stone/gob/seed', 'Strange Seed',
              t_any_item(['tfc:seeds/rice', 'tfc:seeds/sugarcane', 'tfc:seeds/papyrus'], 'Rice, sugarcane or papyrus seed'), kind='side',
              desc=['Trade for rice, sugarcane or papyrus seed. The stock is random; ask again when it refills.'],
              rewards=[T('tfc:burlap_cloth', 4)], icon='tfc:seeds/rice'),
            Q('stone/gob/restock', 'Rare Goods', t_item('minecraft:saddle', title='A saddle'), kind='side',
              desc=['Now and then a goblin carries rarer goods for silk or a large hide. Trade it for a saddle.'],
              rewards=[T('tfc:wool_cloth', 4)], icon='minecraft:saddle'),
        ]),
        # Fire Mountain (TFC Volcanoes)
        ('Fire Mountain', 'stone/metal/ore', [
            Q('stone/vol/salt', 'Crust of Salt', t_item('tfcvolcanoes:mineral/salt', 4), kind='side',
              desc=['Find pale crusts on the stones of a hot spring. Each spring field grows its own',
                    'mineral; break salt from one.'],
              rewards=[P('tfc:metal/hoe/copper')]),
            Q('stone/vol/peter', 'Saltpeter', t_item('tfcvolcanoes:mineral/saltpeter', 4), kind='side',
              desc=['White needles crust the rim of some hot springs. Gather saltpeter where they grow.'],
              rewards=[T('minecraft:clay_ball', 8)]),
            Q('stone/vol/brim', 'Brimstone', t_item('tfcvolcanoes:mineral/brimstone', 4), kind='side',
              desc=['Yellow brimstone crusts the rock beside open lava. Take it before the ground',
                    'shakes again.'],
              rewards=[P('tfc:food/cooked_pork', 4)]),
            Q('stone/vol/jet', 'Black Jet', t_item('tfcvolcanoes:mineral/jet', 2), kind='side',
              desc=['Take a lump of jet from the rock beside lava. Each lava field grows its own',
                    'mineral, so seek one that gives jet.'],
              rewards=[T('tfc:metal/lamp/copper')]),
            Q('stone/vol/greig', 'Greigite', t_item('tfcvolcanoes:mineral/greigite', 4), kind='side',
              desc=['Gather greigite. It holds iron.'],
              rewards=[P('tfc:metal/chisel/copper')]),
        ]),
        # Old Stones (TFC Ruins, Cave Dweller ReEvolved, TFC Regrowing Forests)
        ('Old Stones', 'stone/entry', [
            Q('stone/ruin/house', 'A Fallen House',
              ([t_structure(r, optional=True) for r in TFC_RUINS], 'Stand in a ruin'), kind='side',
              desc=['Find a ruin: toppled walls and chests, in the open and on the beaches. Step inside.'],
              rewards=[T('tfc:ceramic/pot', 2)], icon='tfc:rock/cobble/granite'),
            Q('stone/ruin/chest', 'What the Ruin Kept',
              t_any_item(['minecraft:flint', 'tfc:glue', 'tfc:wool_yarn', 'tfc:powder/charcoal'],
                         'Something from a ruin chest'), kind='side',
              desc=['Open a ruin chest. Its goods follow your Age: flint, glue, yarn and charcoal at first.'],
              rewards=[T('tfc:silk_cloth', 2)], icon='minecraft:flint'),
            Q('stone/ruin/watcher', 'The Pale Watcher',
              t_observe('cave_dweller:cave_dweller', kind='entity_type', title='Look at the pale watcher'), kind='side',
              desc=['Go below Y 0 into the deep caves. A pale thing hunts there until iron is yours.',
                    'See it once.'],
              rewards=[P('tfc:torch', 4)], icon='tfc:torch'),
            Q('stone/ruin/regrow', 'Forests Close',
              t_any_item(SAPLINGS, 'A sapling from the cleared patch'), kind='side',
              desc=['Leave a stand of trees beside a clearing. Their leaves seed saplings around them',
                    'and the forest closes in. Dig one up.'],
              rewards=[P('tfc:food/barley_bread', 2)], icon='tfc:wood/sapling/oak'),
        ]),
    ]),
    dict(key='bronze', filename='roads_bronze_age', stage='age_1', title='Bronze Age Side Roads', strands=[
        # Totem Ceremonies (Totemic)
        ('Totem Ceremonies', 'bronze/side/totem', [
            Q('bronze/totem/knife', 'Whittling Knife', t_item('totemic:totem_whittling_knife'), kind='side',
              desc=['Carve a log with the totem knife. Sneak and right-click to change the carving; the pole is',
                      'built from them.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
            Q('bronze/totem/drum', 'Totemist Drum', t_item('totemic:drum'), kind='side',
              desc=['Craft a drum. Its beat is another voice that the ceremonies listen for.'],
              rewards=[T('minecraft:leather', 4)]),
            Q('bronze/totem/rattle', 'Rattle', t_item('totemic:rattle'), kind='side',
              desc=['Shake a rattle at the totem. Sneak while you play to choose the ceremony.'],
              rewards=[T('minecraft:leather', 4)]),
            Q('bronze/totem/whistle', 'Eagle Whistle', t_item('totemic:eagle_bone_whistle'), kind='side',
              desc=['Make a whistle from an eagle bone. Its call is the oldest signal in the wild.'],
              rewards=[P('totemic:cooked_buffalo_meat', 4)]),
            Q('bronze/totem/tipi', 'Tipi', t_item('totemic:tipi'), kind='side',
              desc=['Raise a tipi of hide or wool and poles. You can sleep in it out in the open field.'],
              rewards=[T('tfc:wool_cloth', 6)]),
        ]),
        # Torque and Connect (Torque Link Create To TFC, WoodenCog, Create: Connected)
        ('Torque and Connect', 'bronze/kin/saw', [
            Q('bronze/torque/torque', 'Torque',
              
              t_any_item(['c2tfc:stress_converter/acacia', 'c2tfc:stress_converter/ash',
                          'c2tfc:stress_converter/aspen', 'c2tfc:stress_converter/birch',
                          'c2tfc:stress_converter/blackwood', 'c2tfc:stress_converter/chestnut',
                          'c2tfc:stress_converter/douglas_fir', 'c2tfc:stress_converter/hickory',
                          'c2tfc:stress_converter/kapok', 'c2tfc:stress_converter/mangrove',
                          'c2tfc:stress_converter/maple', 'c2tfc:stress_converter/oak',
                          'c2tfc:stress_converter/palm', 'c2tfc:stress_converter/pine',
                          'c2tfc:stress_converter/rosewood', 'c2tfc:stress_converter/sequoia',
                          'c2tfc:stress_converter/spruce', 'c2tfc:stress_converter/sycamore',
                          'c2tfc:stress_converter/white_cedar', 'c2tfc:stress_converter/willow'], 'A stress converter, any wood'), kind='side',
              desc=['Set a stress converter on a shaft. Its turning drives axles of the old kind, and',
                    'the quern turns without a hand.'],
              rewards=[T('create:shaft', 8)], icon='c2tfc:stress_converter/oak'),
            Q('bronze/torque/transformer', 'Wooden Transformer', t_item('woodencog:ct_transformer'), kind='side',
              desc=['Craft a wooden transformer. It hands the rotation of your shafts to wooden axles',
                    'at the same speed.'],
              rewards=[T('create:cogwheel', 4)]),
            Q('bronze/torque/generator', 'Wooden Generator', t_item('woodencog:wooden_generator'), kind='side',
              desc=['Set a wooden generator beside a wooden water wheel or windmill. Its turning becomes SU for',
                      'your machines.'],
              rewards=[T('create:andesite_alloy', 8)]),
            Q('bronze/torque/pin', 'Shear Pin', t_item('create_connected:shear_pin'), kind='side',
              desc=['Put a shear pin in the line. It breaks before the machines do when the network is',
                      'overstressed.'],
              rewards=[T('create:large_cogwheel', 2)]),
            Q('bronze/torque/chute', 'Brass Chute', t_item('create_connected:brass_chute'), kind='side',
              desc=['Build a brass chute. It moves up to 64 items at a time, for lines that must not clog.'],
              rewards=[T('create:andesite_funnel', 4)]),
            Q('bronze/torque/silo', 'Item Silo', t_item('create_connected:item_silo'), kind='side',
              desc=['Raise an item silo, a vault standing upright. The line drops into it from above.'],
              rewards=[T('create:gearbox', 2), T('create:clutch', 2)]),
        ]),
        # Pantry (FirmaLife)
        ('Pantry', 'bronze/side/kitchen', [
            Q('bronze/pantry/oven', 'Clay Oven', t_item('firmalife:clay_oven_top'), kind='side',
              desc=['Build a clay oven. Bread and pies bake in its heat, set in and taken out with a peel.'],
              rewards=[T('minecraft:leather', 4)], icon='firmalife:clay_oven_top'),
            Q('bronze/pantry/bowl', 'Mixing Bowl', t_item('firmalife:mixing_bowl'), kind='side',
              desc=['Craft a mixing bowl. Dough and batter are mixed in it before they go into the oven.'],
              rewards=[P('firmalife:food/bacon', 3)]),
            Q('bronze/pantry/cheese', 'A Wheel of Cheese',
              t_any_item(['firmalife:cheddar_wheel', 'firmalife:chevre_wheel', 'firmalife:blue_wheel'], 'A wheel of cheese'), kind='side',
              desc=['Seal curdled milk with cheesecloth in a barrel for curds, then age the wheel in a',
                    'cellar. It keeps for seasons.'],
              rewards=[T('firmalife:beeswax', 4)], icon='firmalife:cheddar_wheel'),
            Q('bronze/pantry/hive', 'Beehive', t_item('firmalife:beehive'), kind='side',
              desc=['Set a beehive among flowers and bring a queen in a skep. Its frames fill with',
                    'honey; a full beekeeper suit stops the stings.'],
              rewards=[T('firmalife:beeswax', 8)]),
            Q('bronze/pantry/glass', 'Under Glass', t_item('firmalife:copper_greenhouse_wall', 4), kind='side',
              desc=['Raise a copper greenhouse. Frost stays outside and crops grow through the cold months.'],
              rewards=[T('tfc:metal/sheet/copper', 4)]),
        ]),
        # Nuts of the Wood (A handful of acorns)
        ('Nuts of the Wood', 'bronze/side/kitchen', [
            Q('bronze/nuts/secateurs', 'Long Secateurs', t_item('ahandfulofacorns:long_secateurs'), kind='side',
              desc=['Make long secateurs with a brass mechanism. They reach the high leaves where the nuts hide.'],
              rewards=[T('create:gearbox', 2)]),
            Q('bronze/nuts/acorn', 'Acorns', t_item('ahandfulofacorns:acorn', 8), kind='side',
              desc=['Cut acorns from oak leaves with the long secateurs. Out of their season the leaves',
                    'give nothing.'],
              rewards=[T('minecraft:leather', 2)]),
            Q('bronze/nuts/roast', 'Roasted Nuts',
              
              t_any_item(['ahandfulofacorns:roast_acorn', 'ahandfulofacorns:roast_chestnut',
                          'ahandfulofacorns:roast_hickory_nut'], 'Any roasted nut', count=4), kind='side',
              desc=['Roast acorns, chestnuts or hickory nuts at the fire. Raw, they are bitter.'],
              rewards=[P('firmalife:food/toast', 3)], icon='ahandfulofacorns:roast_acorn'),
            Q('bronze/nuts/coconut', 'Coconut',
              t_any_item(['ahandfulofacorns:opened_coconut', 'ahandfulofacorns:coconut_meat'], 'An opened coconut'), kind='side',
              desc=['Cut a coconut from palm leaves and crack it with a stone. Sneak and right-click a',
                    'barrel with it for juice and meat.'],
              rewards=[T('tfc:metal/ingot/bismuth', 2)], icon='ahandfulofacorns:opened_coconut'),
        ]),
        # Hearth Odds (Supplementaries, FirmaSupplementaries)
        ('Hearth Odds', 'bronze/entry', [
            Q('bronze/rope/rope', 'A Hanging Rope', t_item('supplementaries:rope', 8), kind='side',
              desc=['Hang a rope from a beam or a bridge post. Climb it, lower a lamp on it, tie it to',
                    'a post.'],
              rewards=[T('tfc:metal/chain/bronze', 4)]),
            Q('bronze/rope/sack', 'Sack', t_item('firmasupps:sack'), kind='side',
              desc=['Sew a sack and fill it. It keeps a small hoard in the space of a single item.'],
              rewards=[T('minecraft:leather', 4)]),
            Q('bronze/rope/clock', 'Clock Block', t_item('supplementaries:clock_block'), kind='side',
              desc=['Set a clock block on a wall. It tells the hour of the sun to the whole house.'],
              rewards=[T('tfc:metal/lamp/bronze')]),
            Q('bronze/rope/altimeter', 'Altimeter', t_item('supplementaries:altimeter'), kind='side',
              desc=['Carry an altimeter and read your height. Below Y 0 the pale watcher walks.'],
              rewards=[P('create:goggles')]),
            Q('bronze/rope/board', 'Blackboard', t_item('supplementaries:blackboard'), kind='side',
              desc=['Hang a blackboard by the door. Chalk a note for those who come home after you.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
        ]),
        # Wheels of the Field (Astikor Carts TFC)
        ('Wheels of the Field', 'bronze/kin/wheel', [
            Q('bronze/carts/wheel', 'Cart Wheel',
              
              t_any_item(['tfcastikorcarts:wheel/acacia', 'tfcastikorcarts:wheel/araucaria',
                          'tfcastikorcarts:wheel/ash', 'tfcastikorcarts:wheel/aspen',
                          'tfcastikorcarts:wheel/baobab', 'tfcastikorcarts:wheel/beech',
                          'tfcastikorcarts:wheel/birch', 'tfcastikorcarts:wheel/blackwood',
                          'tfcastikorcarts:wheel/chestnut', 'tfcastikorcarts:wheel/cypress',
                          'tfcastikorcarts:wheel/douglas_fir', 'tfcastikorcarts:wheel/eucalyptus',
                          'tfcastikorcarts:wheel/fig', 'tfcastikorcarts:wheel/ginkgo',
                          'tfcastikorcarts:wheel/hevea', 'tfcastikorcarts:wheel/hickory',
                          'tfcastikorcarts:wheel/ipe', 'tfcastikorcarts:wheel/ironwood',
                          'tfcastikorcarts:wheel/kapok', 'tfcastikorcarts:wheel/mahoe',
                          'tfcastikorcarts:wheel/mahogany', 'tfcastikorcarts:wheel/mangrove',
                          'tfcastikorcarts:wheel/maple', 'tfcastikorcarts:wheel/oak', 'tfcastikorcarts:wheel/palm',
                          'tfcastikorcarts:wheel/pine', 'tfcastikorcarts:wheel/rosewood',
                          'tfcastikorcarts:wheel/sequoia', 'tfcastikorcarts:wheel/spruce',
                          'tfcastikorcarts:wheel/sycamore', 'tfcastikorcarts:wheel/teak',
                          'tfcastikorcarts:wheel/tualang', 'tfcastikorcarts:wheel/white_cedar',
                          'tfcastikorcarts:wheel/willow'], 'A cart wheel, any wood', count=2), kind='side',
              desc=['Make cart wheels. Every cart and contraption of the field rolls on them.'],
              rewards=[T('minecraft:leather', 2)], icon='tfcastikorcarts:wheel/acacia'),
            Q('bronze/carts/hand', 'Hand Cart',
              
              t_any_item(['tfcastikorcarts:hand_cart/acacia', 'tfcastikorcarts:hand_cart/araucaria',
                          'tfcastikorcarts:hand_cart/ash', 'tfcastikorcarts:hand_cart/aspen',
                          'tfcastikorcarts:hand_cart/baobab', 'tfcastikorcarts:hand_cart/beech',
                          'tfcastikorcarts:hand_cart/birch', 'tfcastikorcarts:hand_cart/blackwood',
                          'tfcastikorcarts:hand_cart/chestnut', 'tfcastikorcarts:hand_cart/cypress',
                          'tfcastikorcarts:hand_cart/douglas_fir', 'tfcastikorcarts:hand_cart/eucalyptus',
                          'tfcastikorcarts:hand_cart/fig', 'tfcastikorcarts:hand_cart/ginkgo',
                          'tfcastikorcarts:hand_cart/hevea', 'tfcastikorcarts:hand_cart/hickory',
                          'tfcastikorcarts:hand_cart/ipe', 'tfcastikorcarts:hand_cart/ironwood',
                          'tfcastikorcarts:hand_cart/kapok', 'tfcastikorcarts:hand_cart/mahoe',
                          'tfcastikorcarts:hand_cart/mahogany', 'tfcastikorcarts:hand_cart/mangrove',
                          'tfcastikorcarts:hand_cart/maple', 'tfcastikorcarts:hand_cart/oak',
                          'tfcastikorcarts:hand_cart/palm', 'tfcastikorcarts:hand_cart/pine',
                          'tfcastikorcarts:hand_cart/rosewood', 'tfcastikorcarts:hand_cart/sequoia',
                          'tfcastikorcarts:hand_cart/spruce', 'tfcastikorcarts:hand_cart/sycamore',
                          'tfcastikorcarts:hand_cart/teak', 'tfcastikorcarts:hand_cart/tualang',
                          'tfcastikorcarts:hand_cart/white_cedar', 'tfcastikorcarts:hand_cart/willow'], 'A hand cart, any wood'), kind='side',
              desc=['Build a hand cart. It carries stacks of goods, and you pull it yourself, no beast',
                    'needed.'],
              rewards=[T('minecraft:leather', 4)], icon='tfcastikorcarts:hand_cart/acacia'),
            Q('bronze/carts/plow', 'Plow',
              
              t_any_item(['tfcastikorcarts:plow/acacia', 'tfcastikorcarts:plow/araucaria',
                          'tfcastikorcarts:plow/ash', 'tfcastikorcarts:plow/aspen', 'tfcastikorcarts:plow/baobab',
                          'tfcastikorcarts:plow/beech', 'tfcastikorcarts:plow/birch',
                          'tfcastikorcarts:plow/blackwood', 'tfcastikorcarts:plow/chestnut',
                          'tfcastikorcarts:plow/cypress', 'tfcastikorcarts:plow/douglas_fir',
                          'tfcastikorcarts:plow/eucalyptus', 'tfcastikorcarts:plow/fig',
                          'tfcastikorcarts:plow/ginkgo', 'tfcastikorcarts:plow/hevea',
                          'tfcastikorcarts:plow/hickory', 'tfcastikorcarts:plow/ipe',
                          'tfcastikorcarts:plow/ironwood', 'tfcastikorcarts:plow/kapok',
                          'tfcastikorcarts:plow/mahoe', 'tfcastikorcarts:plow/mahogany',
                          'tfcastikorcarts:plow/mangrove', 'tfcastikorcarts:plow/maple', 'tfcastikorcarts:plow/oak',
                          'tfcastikorcarts:plow/palm', 'tfcastikorcarts:plow/pine', 'tfcastikorcarts:plow/rosewood',
                          'tfcastikorcarts:plow/sequoia', 'tfcastikorcarts:plow/spruce',
                          'tfcastikorcarts:plow/sycamore', 'tfcastikorcarts:plow/teak',
                          'tfcastikorcarts:plow/tualang', 'tfcastikorcarts:plow/white_cedar',
                          'tfcastikorcarts:plow/willow'], 'A plow, any wood'), kind='side',
              desc=['Build a plow, fit a hoe in it and hitch it to a beast. It tills the ground in rows',
                    'as it walks.'],
              rewards=[T('tfc:seeds/barley', 8)], icon='tfcastikorcarts:plow/acacia'),
            Q('bronze/carts/drill', 'Seed Drill',
              
              t_any_item(['tfcastikorcarts:seed_drill/acacia', 'tfcastikorcarts:seed_drill/araucaria',
                          'tfcastikorcarts:seed_drill/ash', 'tfcastikorcarts:seed_drill/aspen',
                          'tfcastikorcarts:seed_drill/baobab', 'tfcastikorcarts:seed_drill/beech',
                          'tfcastikorcarts:seed_drill/birch', 'tfcastikorcarts:seed_drill/blackwood',
                          'tfcastikorcarts:seed_drill/chestnut', 'tfcastikorcarts:seed_drill/cypress',
                          'tfcastikorcarts:seed_drill/douglas_fir', 'tfcastikorcarts:seed_drill/eucalyptus',
                          'tfcastikorcarts:seed_drill/fig', 'tfcastikorcarts:seed_drill/ginkgo',
                          'tfcastikorcarts:seed_drill/hevea', 'tfcastikorcarts:seed_drill/hickory',
                          'tfcastikorcarts:seed_drill/ipe', 'tfcastikorcarts:seed_drill/ironwood',
                          'tfcastikorcarts:seed_drill/kapok', 'tfcastikorcarts:seed_drill/mahoe',
                          'tfcastikorcarts:seed_drill/mahogany', 'tfcastikorcarts:seed_drill/mangrove',
                          'tfcastikorcarts:seed_drill/maple', 'tfcastikorcarts:seed_drill/oak',
                          'tfcastikorcarts:seed_drill/palm', 'tfcastikorcarts:seed_drill/pine',
                          'tfcastikorcarts:seed_drill/rosewood', 'tfcastikorcarts:seed_drill/sequoia',
                          'tfcastikorcarts:seed_drill/spruce', 'tfcastikorcarts:seed_drill/sycamore',
                          'tfcastikorcarts:seed_drill/teak', 'tfcastikorcarts:seed_drill/tualang',
                          'tfcastikorcarts:seed_drill/white_cedar', 'tfcastikorcarts:seed_drill/willow'], 'A seed drill, any wood'), kind='side',
              desc=['Build a seed drill. It plants seed on tilled ground in a line, as far as the beast walks.'],
              rewards=[T('tfc:seeds/wheat', 8)], icon='tfcastikorcarts:seed_drill/acacia'),
            Q('bronze/carts/reaper', 'Reaper',
              
              t_any_item(['tfcastikorcarts:reaper/acacia', 'tfcastikorcarts:reaper/araucaria',
                          'tfcastikorcarts:reaper/ash', 'tfcastikorcarts:reaper/aspen',
                          'tfcastikorcarts:reaper/baobab', 'tfcastikorcarts:reaper/beech',
                          'tfcastikorcarts:reaper/birch', 'tfcastikorcarts:reaper/blackwood',
                          'tfcastikorcarts:reaper/chestnut', 'tfcastikorcarts:reaper/cypress',
                          'tfcastikorcarts:reaper/douglas_fir', 'tfcastikorcarts:reaper/eucalyptus',
                          'tfcastikorcarts:reaper/fig', 'tfcastikorcarts:reaper/ginkgo',
                          'tfcastikorcarts:reaper/hevea', 'tfcastikorcarts:reaper/hickory',
                          'tfcastikorcarts:reaper/ipe', 'tfcastikorcarts:reaper/ironwood',
                          'tfcastikorcarts:reaper/kapok', 'tfcastikorcarts:reaper/mahoe',
                          'tfcastikorcarts:reaper/mahogany', 'tfcastikorcarts:reaper/mangrove',
                          'tfcastikorcarts:reaper/maple', 'tfcastikorcarts:reaper/oak',
                          'tfcastikorcarts:reaper/palm', 'tfcastikorcarts:reaper/pine',
                          'tfcastikorcarts:reaper/rosewood', 'tfcastikorcarts:reaper/sequoia',
                          'tfcastikorcarts:reaper/spruce', 'tfcastikorcarts:reaper/sycamore',
                          'tfcastikorcarts:reaper/teak', 'tfcastikorcarts:reaper/tualang',
                          'tfcastikorcarts:reaper/white_cedar', 'tfcastikorcarts:reaper/willow'], 'A reaper, any wood'), kind='side',
              desc=['Build a reaper and sit on it. It cuts the standing crop where the plow cut the rows.'],
              rewards=[T('tfc:metal/sheet/copper', 2)], icon='tfcastikorcarts:reaper/acacia'),
        ]),
        # Smoke and Poles (Advanced Chimneys, "To the Bat Poles!")
        ('Smoke and Poles', 'bronze/smith/forge', [
            Q('bronze/smoke/chimney', 'A Chimney', t_item('adchimneys:stone_chimney', 4), kind='side',
              desc=['Build a stone chimney over the forge. The smoke rises clear of the roof and harms nobody.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
            Q('bronze/smoke/brick', 'Brick Chimney', t_item('adchimneys:small_brick_chimney', 4), kind='side',
              desc=['Build a small brick chimney for the house fire. It fits a narrow roof and a small hearth.'],
              rewards=[T('tfc:metal/ingot/bismuth', 2)]),
            Q('bronze/smoke/vent', 'Metal Vent', t_item('adchimneys:metal_vent'), kind='side',
              desc=['Cap a chimney with a metal vent. The smoke leaves through its slots.'],
              rewards=[T('create:andesite_casing', 2)]),
            Q('bronze/smoke/pole', 'Slide Pole', t_item('adpoles:wooden_pole', 4), kind='side',
              desc=['Stand a wooden pole between floors. Hold on and slide down; there is no ladder faster.'],
              rewards=[T('tfc:metal/chain/bronze', 4)]),
            Q('bronze/smoke/glass', 'Glass Pole', t_item('adpoles:glass_pole', 4), kind='side',
              desc=['Stand a glass pole in a bright hall. It slides like the wooden one and shows the light',
                      'through.'],
              rewards=[T('tfc:metal/ingot/tin', 2)]),
        ]),
        # Packs and Drawers (Sophisticated Backpacks, Storage Drawers, GraveStone Mod, Trash Cans)
        ('Packs and Drawers', 'bronze/side/backpack', [
            Q('bronze/packs/copper', 'Copper Pack', t_item('sophisticatedbackpacks:copper_backpack'), kind='side',
              desc=['Upgrade the pack with copper. It holds more, and it takes more upgrades.'],
              rewards=[T('minecraft:leather', 4)]),
            Q('bronze/packs/pickup', 'Pickup Upgrade', t_item('sophisticatedbackpacks:pickup_upgrade'), kind='side',
              desc=['Fit a pickup upgrade. The pack gathers what lies at your feet and keeps your hands free.'],
              rewards=[P('create:wrench')]),
            Q('bronze/packs/drawer', 'Drawer',
              
              t_any_item(['storagedrawers:acacia_full_drawers_2', 'storagedrawers:bamboo_full_drawers_2',
                          'storagedrawers:birch_full_drawers_2', 'storagedrawers:cherry_full_drawers_2',
                          'storagedrawers:dark_oak_full_drawers_2', 'storagedrawers:jungle_full_drawers_2',
                          'storagedrawers:mangrove_full_drawers_2', 'storagedrawers:oak_full_drawers_2',
                          'storagedrawers:spruce_full_drawers_2'], 'A full drawer, any wood'), kind='side',
              desc=['Build a drawer. Each front stores one kind of item in great number and gives a',
                    'stack when you punch it.'],
              rewards=[T('storagedrawers:upgrade_template', 2)], icon='storagedrawers:acacia_full_drawers_2'),
            Q('bronze/packs/upgrade', 'Copper Upgrade', t_item('storagedrawers:copper_storage_upgrade'), kind='side',
              desc=['Slot a copper storage upgrade into a drawer. Its capacity grows many times over.'],
              rewards=[T('sophisticatedbackpacks:upgrade_base', 2)]),
            Q('bronze/packs/trash', 'Trash Can', t_item('trashcans:item_trash_can'), kind='side',
              desc=['Craft a trash can. It destroys what you drop in, and its filter can limit it to',
                    'the junk you name.'],
              rewards=[T('tfc:metal/ingot/bismuth', 2)]),
            Q('bronze/packs/grave', 'Grave', t_item('gravestone:gravestone'), kind='side',
              desc=['Craft a grave. Should you fall, one rises where you died and keeps your goods;',
                    'break it to take them back.'],
              rewards=[P('create:goggles')]),
        ]),
        # Prospector's Kit (Precision Prospecting)
        ("Prospector's Kit", 'bronze/prosp/propick', [
            Q('bronze/prosp/mold', 'Drill Head Mold',
              t_item('precisionprospecting:ceramic/prospector_drill_head_mold'), kind='side',
              desc=['Knap a drill head mold from clay and fire it. Cast the drill head in it.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
            Q('bronze/prosp/drill', 'Prospector Drill',
              t_item('precisionprospecting:metal/prospector_drill/bronze'), kind='side',
              desc=['Fit a bronze drill head to two sticks. Strike a rock face and it reads a long,',
                    'narrow column behind it.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
            Q('bronze/prosp/hammer', 'Prospector Hammer',
              t_item('precisionprospecting:metal/prospector_hammer/bronze'), kind='side',
              desc=['Fit a bronze hammer head to a stick and strike the rock. It searches a small cube',
                    'around the blow.'],
              rewards=[T('tfc:metal/sheet/copper', 2)]),
            Q('bronze/prosp/head', 'Mineral Prospector',
              
              t_any_item(['precisionprospecting:metal/mineral_prospector/copper',
                          'precisionprospecting:metal/mineral_prospector/bronze',
                          'precisionprospecting:metal/mineral_prospector/bismuth_bronze',
                          'precisionprospecting:metal/mineral_prospector/black_bronze'], 'A mineral prospector'), kind='side',
              desc=['Make a mineral prospector. It searches a great cube around the blow, but only for',
                    'ores that are not metal.'],
              rewards=[T('tfc:metal/ingot/black_bronze', 2)], icon='precisionprospecting:metal/mineral_prospector/bronze'),
        ]),
        # Ruin Trinkets (Artifacts)
        ('Ruin Trinkets', 'bronze/entry', [
            Q('bronze/trinket/first', 'A Trinket',
              
              t_any_item(['artifacts:anglers_hat', 'artifacts:cowboy_hat', 'artifacts:novelty_drinking_hat',
                          'artifacts:plastic_drinking_hat', 'artifacts:superstitious_hat', 'artifacts:villager_hat',
                          'artifacts:lucky_scarf', 'artifacts:whoopee_cushion', 'artifacts:umbrella',
                          'artifacts:snorkel', 'artifacts:flippers', 'artifacts:kitty_slippers',
                          'artifacts:snowshoes', 'artifacts:golden_hook', 'artifacts:onion_ring',
                          'artifacts:bunny_hoppers'], 'Any trinket from a ruin'), kind='side',
              desc=['Open ruin chests until a trinket lies among the flint and yarn. Now and then a',
                    'chest holds one.'],
              rewards=[T('minecraft:leather', 4)], icon='artifacts:anglers_hat'),
            Q('bronze/trinket/water', 'Snorkel and Flippers',
              t_any_item(['artifacts:snorkel', 'artifacts:flippers'], 'A snorkel or flippers'), kind='side',
              desc=['A snorkel lengthens your breath and flippers quicken your stroke. Wear both before you cross',
                      'deep water.'],
              rewards=[T('create:copper_backtank')], icon='artifacts:snorkel'),
            Q('bronze/trinket/rain', 'Umbrella', t_item('artifacts:umbrella'), kind='side',
              desc=['An umbrella slows a fall while you hold it, and it turns a blow like a shield.',
                    'Hold it up when the sky opens.'],
              rewards=[T('minecraft:leather', 4)]),
            Q('bronze/trinket/cold', 'Snowshoes', t_item('artifacts:snowshoes'), kind='side',
              desc=['Snowshoes carry you over powder snow and quicken your step on snow. Winter stops',
                    'being a wall.'],
              rewards=[T('tfc:wool_cloth', 4)]),
        ]),
    ]),
    dict(key='iron', filename='roads_iron_age', stage='age_2', title='Iron Age Side Roads', strands=[
        # Sap, Syrup and Latex (ArborFirmaCraft)
        ('Sap, Syrup and Latex', 'iron/surv/leather', [
            Q('iron/sap/tap', 'Tree Tap', t_item('afc:tree_tap'), kind='side',
              desc=['Drive a tree tap into a living trunk. It fills with sap as the seasons turn.'],
              rewards=[T('tfc:metal/sheet/bronze', 2)]),
            Q('iron/sap/syrup', 'Syrup and Sugar',
              t_any_item(['afc:maple_sugar', 'afc:birch_sugar'], 'Maple or birch sugar'), kind='side',
              desc=['Boil maple or birch sap to syrup in a pot, then craft the syrup into sugar. Sweet',
                    'food keeps a winter camp cheerful.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)], icon='afc:maple_sugar'),
            Q('iron/sap/rubber', 'Rubber', t_item('afc:rubber_bar', 3), kind='side',
              desc=['Tap a hevea or a rubber fig for latex and boil it with sulfur into rubber bars.',
                    'Three bars make four belts.'],
              rewards=[T('tfc:powder/sulfur', 8)]),
            Q('iron/sap/belts', 'Rubber Belts', t_item('create:belt_connector', 4), kind='side',
              desc=['Cut belts from rubber bars. A belt carries items and turns shafts where gears cannot reach.'],
              rewards=[T('create:electron_tube', 2), T('create:brass_funnel', 2)]),
        ]),
        # Terminal Habits (Tom's Simple Storage Mod)
        ('Terminal Habits', 'iron/side/storage', [
            Q('iron/store/hopper', 'Inventory Hopper', t_item('toms_storage:basic_inventory_hopper'), kind='side',
              desc=['Set an inventory hopper on the network. Right-click an item on it to choose what it takes or',
                      'gives.'],
              rewards=[T('tfc:metal/sheet/bronze', 2)]),
            Q('iron/store/crate', 'Open Crate', t_item('toms_storage:open_crate'), kind='side',
              desc=['Place an open crate. The block in front of it becomes an inventory, and what falls in lies',
                      'on the ground.'],
              rewards=[T('create:electron_tube', 2)]),
            Q('iron/store/proxy', 'Inventory Proxy', t_item('toms_storage:inventory_proxy'), kind='side',
              desc=['Place an inventory proxy. It lends its face to the chest it points at, so one terminal',
                      'reaches farther.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/store/level', 'Level Emitter', t_item('toms_storage:level_emitter'), kind='side',
              desc=['Set a level emitter on the network. It gives a redstone signal that follows the count of an',
                      'item.'],
              rewards=[T('create:fluid_pipe', 8)]),
            Q('iron/store/cabinet', 'Filing Cabinet', t_item('toms_storage:filing_cabinet'), kind='side',
              desc=['Build a filing cabinet. It shelves goods in a small space, and the terminal reads it like a',
                      'chest.'],
              rewards=[P('sophisticatedbackpacks:iron_backpack')]),
        ]),
        # The Line (Steam 'n' Rails Neoforge, Create Railways Navigator, Create: Bells & Whistles)
        ('The Line', 'iron/side/rails', [
            Q('iron/line/handcar', 'Handcar', t_item('railways:handcar'), kind='side',
              desc=['Build a handcar and pump it along your track. It is a train for one while the locomotive',
                      'waits.'],
              rewards=[T('tfc:metal/rod/wrought_iron', 4)]),
            Q('iron/line/switch', 'Track Switch', t_item('railways:track_switch_andesite'), kind='side',
              desc=['Set a track switch where the line forks. A signal chooses the branch the train takes.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/line/semaphore', 'Semaphore', t_item('railways:semaphore'), kind='side',
              desc=['Raise a semaphore by the line. Its arm shows stop or go to every driver.'],
              rewards=[T('create:electron_tube', 2)]),
            Q('iron/line/platform', 'Station Platform', t_item('bellsandwhistles:station_platform', 4), kind='side',
              desc=['Lay a platform at your station. Passengers and crates have somewhere to stand.'],
              rewards=[T('tfc:metal/rod/steel', 4)]),
            Q('iron/line/navigator', 'Navigator', t_item('createrailwaysnavigator:navigator'), kind='side',
              desc=['Carry a navigator. Choose a station and it shows the way, line by line.'],
              rewards=[T('create:electron_tube', 2)]),
            Q('iron/line/clock', 'Station Clock', t_item('createrailwaysnavigator:train_station_clock'), kind='side',
              desc=['Hang a station clock on the platform wall. The schedule means little without a time to read.'],
              rewards=[T('tfc:metal/sheet/brass', 2)]),
        ]),
        # Mechanical Odds (Create Mechanical Extruder, Create: Pattern Schematics, Create: Bits 'n' Bobs, TFCreate Compat)
        ('Mechanical Odds', 'iron/power/crush', [
            Q('iron/odds/powder', 'Andesite Powder', t_item('tfcreate:andesite_powder', 4), kind='side',
              desc=['Hammer loose dacite or basalt into powder and wash it in a barrel of water. Welded',
                    'to iron or zinc, it makes andesite alloy.'],
              rewards=[T('tfc:metal/sheet/bronze', 2)]),
            Q('iron/odds/extruder', 'Extruder', t_item('create_mechanical_extruder:mechanical_extruder'), kind='side',
              desc=['Set a mechanical extruder on a spinning shaft. What lies at its sides and below decides the',
                      'block it makes.'],
              rewards=[T('create:fluid_pipe', 8)]),
            Q('iron/odds/pulley', 'Chain Pulley', t_item('bits_n_bobs:chain_pulley'), kind='side',
              desc=['Hang a contraption on a chain pulley. It rides a chain where the rope pulley rides a rope.'],
              rewards=[T('create:electron_tube', 2)]),
            Q('iron/odds/pattern', 'Pattern Schematic',
              t_item('create_pattern_schematics:empty_pattern_schematic'), kind='side',
              desc=['Craft an empty pattern schematic and write a schematic to it at the table. The',
                    'cannon then repeats the design side by side.'],
              rewards=[T('create:brass_tunnel', 2)]),
            Q('iron/odds/strut', 'Girder Strut', t_item('bits_n_bobs:girder_strut', 8), kind='side',
              desc=['Span a gap with girder struts. They run straight or at an angle between two anchor',
                    'points.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
        ]),
        # Smith's Aids (TFC Better Blast Furnace, MmmMmmMmmMmm (Target Dummy), TFC Weld Button)
        ("Smith's Aids", 'iron/metal/blast', [
            Q('iron/aids/insulation', 'Insulation', t_item('tfcbetterbf:insulation'), kind='side',
              desc=['Right-click fire bricks with blast furnace insulation. The chimney then needs no',
                    'iron sheets around it.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/aids/dummy', 'Target Dummy', t_item('dummmmmmy:target_dummy'), kind='side',
              desc=['Set a target dummy in the yard. Strike it with your new blade and read the damage it shows.'],
              rewards=[T('minecraft:leather', 6)]),
            Q('iron/aids/weld', 'Weld Button',
              t_item('tfc:metal/double_ingot/wrought_iron', title='A double ingot of wrought iron'), kind='side',
              desc=['Open the anvil with two hot ingots and flux and press the weld button. It joins them in one press.'],
              rewards=[T('tfc:powder/flux', 8)], icon='tfc:metal/double_ingot/wrought_iron'),
            Q('iron/aids/shield', 'A Shield', t_item('tfc:metal/shield/wrought_iron'), kind='side',
              desc=['Forge a shield of wrought iron and carry it against the dummy. A blow you catch is a blow',
                      'not taken.'],
              rewards=[T('minecraft:leather', 4)]),
        ]),
        # Cellar and Glass (FirmaLife)
        ('Cellar and Glass', 'iron/surv/key', [
            Q('iron/cellar/house', 'Iron Greenhouse', t_item('firmalife:iron_greenhouse_wall', 4), kind='side',
              desc=['Raise an iron greenhouse. Bonsai fruit trees, bananas and berry trellises grow',
                    'only under iron or better.'],
              rewards=[T('tfc:metal/sheet/bronze', 4)]),
            Q('iron/cellar/hydro', 'Hydroponic Planter', t_item('firmalife:hydroponic_planter'), kind='side',
              desc=['Set a hydroponic planter over a sprinkler pipe. Rice and cranberries grow in it',
                    'with no watering can.'],
              rewards=[T('create:fluid_pipe', 8)]),
            Q('iron/cellar/tumbler', 'Compost Tumbler', t_item('firmalife:compost_tumbler'), kind='side',
              desc=['Build a compost tumbler and turn it by axle. It takes bones, fish and sherds too,',
                    'and asks for no exact mix.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/cellar/keg', 'Keg',
              
              t_any_item(['firmalife:wood/keg/acacia', 'firmalife:wood/keg/ash', 'firmalife:wood/keg/aspen',
                          'firmalife:wood/keg/birch', 'firmalife:wood/keg/blackwood', 'firmalife:wood/keg/chestnut',
                          'firmalife:wood/keg/douglas_fir', 'firmalife:wood/keg/hickory',
                          'firmalife:wood/keg/kapok', 'firmalife:wood/keg/mangrove', 'firmalife:wood/keg/maple',
                          'firmalife:wood/keg/oak', 'firmalife:wood/keg/palm', 'firmalife:wood/keg/pine',
                          'firmalife:wood/keg/rosewood', 'firmalife:wood/keg/sequoia', 'firmalife:wood/keg/spruce',
                          'firmalife:wood/keg/sycamore', 'firmalife:wood/keg/white_cedar',
                          'firmalife:wood/keg/willow'], 'A keg, any wood'), kind='side',
              desc=['Craft a keg, a barrel two blocks high and wide. It holds far more goods or fluid',
                    'than a plain barrel.'],
              rewards=[T('tfc:metal/ingot/cast_iron', 2)], icon='firmalife:wood/keg/acacia'),
            Q('iron/cellar/press', 'Barrel Press',
              
              t_any_item(['firmalife:wood/barrel_press/acacia', 'firmalife:wood/barrel_press/ash',
                          'firmalife:wood/barrel_press/aspen', 'firmalife:wood/barrel_press/birch',
                          'firmalife:wood/barrel_press/blackwood', 'firmalife:wood/barrel_press/chestnut',
                          'firmalife:wood/barrel_press/douglas_fir', 'firmalife:wood/barrel_press/hickory',
                          'firmalife:wood/barrel_press/kapok', 'firmalife:wood/barrel_press/mangrove',
                          'firmalife:wood/barrel_press/maple', 'firmalife:wood/barrel_press/oak',
                          'firmalife:wood/barrel_press/palm', 'firmalife:wood/barrel_press/pine',
                          'firmalife:wood/barrel_press/rosewood', 'firmalife:wood/barrel_press/sequoia',
                          'firmalife:wood/barrel_press/spruce', 'firmalife:wood/barrel_press/sycamore',
                          'firmalife:wood/barrel_press/white_cedar', 'firmalife:wood/barrel_press/willow'], 'A barrel press, any wood'), kind='side',
              desc=['Build a barrel press. Fermented grapes go in, and red, white or sparkling wine',
                    'comes out.'],
              rewards=[T('firmalife:beeswax', 8)], icon='firmalife:wood/barrel_press/acacia'),
        ]),
        # Flue and Lift (Advanced Chimneys, "To the Bat Poles!")
        ('Flue and Lift', 'iron/metal/bloomery', [
            Q('iron/flue/pipe', 'Flue Pipe', t_item('adchimneys:pipe', 8), kind='side',
              desc=['Raise a pump pipe, a narrow flue. Right-click it with more pipe to stack up to',
                    'eight at once.'],
              rewards=[T('tfc:metal/sheet/bronze', 2)]),
            Q('iron/flue/duct', 'Smoke Duct', t_item('adchimneys:duct', 4), kind='side',
              desc=['Lay ducts to lead smoke sideways. They join chimneys round corners, so many fires',
                    'share one stack.'],
              rewards=[T('create:fluid_pipe', 8)]),
            Q('iron/flue/pump', 'Stone Pump', t_item('adchimneys:stone_pump'), kind='side',
              desc=['Set a stone pump by a fire and give it a redstone signal. It draws the smoke away',
                    'into the flue.'],
              rewards=[T('tfc:metal/sheet/wrought_iron', 2)]),
            Q('iron/flue/lift', 'Lift Pole', t_item('adpoles:redstone_pole', 4), kind='side',
              desc=['Stand a redstone pole and power it. Grab it and you slide up the shaft as the',
                    'wooden pole let you down.'],
              rewards=[T('create:electron_tube', 2)]),
        ]),
        # Red Moons (Enhanced Celestials 2: Core, Enhanced Celestials 2: Default Lunar Events)
        ('Red Moons', 'iron/entry', [
            Q('iron/moon/armed', 'Armed Dead',
              t_kill('minecraft:zombie', value=16, title='Put down 16 of the dead'), kind='side',
              desc=['On a red moon the dead rise armed. On a Firmament Night, after an Age dawns, fewer walk and',
                    'fights pay more.'],
              rewards=[P('tfc:metal/javelin/wrought_iron', 2)], icon='tfc:metal/helmet/wrought_iron'),
            Q('iron/moon/keep', 'Keeping the Door',
              t_any_item(LAMPS_IRON, 'Four lamps of wrought iron or steel', count=4), kind='side',
              desc=['Hang lamps at the yard and the walls before dusk. A lit house is quiet on the reddest night.'],
              rewards=[T('tfc:candle', 6)], icon='tfc:metal/lamp/wrought_iron'),
        ]),
    ]),
    dict(key='arcane', filename='roads_arcane_age', stage='age_3', title='Arcane Age Side Roads', strands=[
        # Shelves of Power (Apothic Enchanting)
        ('Shelves of Power', 'arcane/machine/enchant', [
            Q('arcane/shelves/stone', 'Stoneshelf', t_item('apothic_enchanting:stoneshelf', 8), kind='side',
              desc=['Set stoneshelves round the enchanting table. They lower Eterna and Arcana, for a',
                    'weaker but surer enchantment.'],
              rewards=[T('tfc:gem/lapis_lazuli', 4)]),
            Q('arcane/shelves/deep', 'Hell and Sea',
              t_any_item(['apothic_enchanting:hellshelf', 'apothic_enchanting:seashelf'], 'A hellshelf or a seashelf', count=4), kind='side',
              desc=['A hellshelf or a seashelf lifts the table past level 30. Quanta and Arcana grow',
                    'with them.'],
              rewards=[T('tfc:gem/amethyst', 2)], icon='apothic_enchanting:hellshelf'),
            Q('arcane/shelves/library', 'Library', t_item('apothic_enchanting:library'), kind='side',
              desc=['Build a library. Enchanted books dropped in become stored levels, and you draw out',
                    'any enchantment you saved.'],
              rewards=[T('tfc:gem/emerald', 2)]),
            Q('arcane/shelves/extract', 'Extraction Tome', t_item('apothic_enchanting:extraction_tome'), kind='side',
              desc=['Craft an extraction tome. It strips the enchantments from a worn blade and writes them into',
                      'books.'],
              rewards=[T('theurgy:mercury_shard', 4)]),
        ]),
        # Gems and Sigils (Apotheosis)
        ('Gems and Sigils', 'arcane/source/apparatus', [
            Q('arcane/gems/table', 'Gem Cutting Table', t_item('apotheosis:gem_cutting_table'), kind='side',
              desc=['Build a gem cutting table. A gem, gem dust and a rarity material go in, and the',
                    'gem comes out of higher purity.'],
              rewards=[T('tfc:gem/ruby', 2)]),
            Q('arcane/gems/gem', 'A Gem', t_item('apotheosis:gem'), kind='side',
              desc=['Slay monsters until one drops a gem. Each carries one power for the gear it is set',
                    'in.'],
              rewards=[T('apotheosis:gem_dust', 8)]),
            Q('arcane/gems/socket', 'Sigil of Socketing', t_item('apotheosis:sigil_of_socketing'), kind='side',
              desc=['Craft a sigil of socketing. At the smithing table it opens a socket in a blade or',
                    'a helm.'],
              rewards=[T('tfc:gem/emerald', 2)]),
            Q('arcane/gems/reforge', 'Reforging', t_item('apotheosis:simple_reforging_table'), kind='side',
              desc=['Build a simple reforging table. It rolls the affixes of a piece anew for rarity',
                    'materials and sigils of rebirth.'],
              rewards=[T('tfc:gem/sapphire', 2)]),
        ]),
        # Spirits at Work (Create: Occult Engineering, Ars Ocultas)
        ('Spirits at Work', 'arcane/spirit/djinni', [
            Q('arcane/spirits/book', 'Encyclopedia of Souls',
              t_item('occultengineering:encyclopedia_of_souls'), kind='side',
              desc=['Craft the encyclopedia. It shows what spirits do for a machine and where chalk and gears',
                      'meet.'],
              rewards=[T('occultism:otherworld_essence', 4)]),
            Q('arcane/spirits/gears', 'Bound by Gears',
              t_any_item(['occultism:book_of_binding_foliot', 'occultism:book_of_binding_djinni'], 'A binding book of a Foliot or Djinni'), kind='side',
              desc=['The Foliot and Djinni binding books each take a precision mechanism. The machine line builds',
                      'the miner of the spirits.'],
              rewards=[T('create:sturdy_sheet', 4)], icon='occultism:book_of_binding_foliot'),
            Q('arcane/spirits/chamber', 'Mechanical Chamber',
              t_item('occultengineering:mechanical_chamber'), kind='side',
              desc=['Build a mechanical chamber. It stands in for the sacrificial bowl at any ritual that needs',
                      'no offering.'],
              rewards=[T('theurgy:mercury_shard', 4)]),
            Q('arcane/spirits/pulverizer', 'Mechanical Pulverizer',
              t_item('occultengineering:mechanical_pulverizer'), kind='side',
              desc=['Turn a mechanical pulverizer by shaft. It crushes whatever a crusher spirit can,',
                    'with no spirit bound to it.'],
              rewards=[T('tfc:gem/topaz', 2)]),
            Q('arcane/spirits/altar', 'The Joined Altar', t_item('ars_ocultas:altar'), kind='side',
              desc=['Craft the sacrificial altar. It holds a ritual in place of the golden bowl and',
                    'pays its cost in source from nearby jars.'],
              rewards=[T('occultism:otherworld_essence', 4)]),
        ]),
        # The Hot Dark (Beneath)
        ('The Hot Dark', 'arcane/spirit/nether', [
            Q('arcane/beneath/tome', 'Book of the Deep', t_item('beneath:tome'), kind='side',
              desc=['Find a blank tome in a fortress or bastion chest. Inscribed on an ancient altar',
                    'with a cut gem, it carries one enchantment.'],
              rewards=[T('tfc:gem/amethyst', 2)]),
            Q('arcane/beneath/coal', 'Cursecoal', t_item('beneath:cursecoal', 8), kind='side',
              desc=['Mine cursecoal from the netherrack above Y 80 in the deep. It is a fuel far',
                    'stronger than charcoal.'],
              rewards=[T('tfc:gem/topaz', 2)]),
            Q('arcane/beneath/gold', 'Gold Chunks', t_item('beneath:gold_chunk', 8), kind='side',
              desc=['Break gilded blackstone in the bastions of the deep. One block in four gives up a',
                    'chunk of gold.'],
              rewards=[T('tfc:gem/lapis_lazuli', 4)]),
            Q('arcane/beneath/fungus', 'Fungus Supper',
              
              t_any_item(['beneath:food/shiitake', 'beneath:food/chantrelle', 'beneath:food/parasol',
                          'beneath:food/portobello', 'beneath:food/oyster'], 'Any safe mushroom', count=4), kind='side',
              desc=['Pick shiitake, chanterelle or parasol and eat only what you can name. The death cap kills',
                      'quietly.'],
              rewards=[T('occultism:otherworld_essence', 2)], icon='beneath:food/shiitake'),
            Q('arcane/beneath/pepper', 'Ghost Pepper', t_item('beneath:ghost_pepper', 4), kind='side',
              desc=['Grow ghost pepper in the Nether. It is hot enough to be called a fruit of the dark.'],
              rewards=[T('theurgy:mercury_shard', 2)]),
            Q('arcane/beneath/goblin', 'The Vein Goblin',
              t_any_item(['minecraft:shroomlight', 'beneath:seeds/gleamflower'], 'A shroomlight or a gleamflower seed'),
              kind='side',
              desc=['A goblin of the veins trades in the Nether for silk, hides and cloth. Trade it for',
                    'a shroomlight or a gleamflower seed.'],
              rewards=[T('tfc:gem/ruby', 2)], icon='minecraft:shroomlight'),
        ]),
        # Elemental Arts (Ars Elemental, Ars Additions, Akashic Tome; the Wixie of Ars Nouveau)
        ('Elemental Arts', 'arcane/source/apprentice', [
            Q('arcane/elements/tome', 'One Book', t_item('akashictome:tome'), kind='side',
              desc=['Craft an Akashic Tome and craft your handbooks into it. Sneak and right-click to',
                    'turn it to the one you need.'],
              rewards=[T('tfc:gem/amethyst', 2)]),
            Q('arcane/elements/caster', 'An Element Tome',
              
              t_any_item(['ars_elemental:fire_caster_tome', 'ars_elemental:air_caster_tome',
                          'ars_elemental:earth_caster_tome', 'ars_elemental:water_caster_tome'], 'A spell book of an element'), kind='side',
              desc=['Search fortress and bastion chests for a caster tome of one element. It casts its',
                    'written spell with no glyphs of your own.'],
              rewards=[T('ars_nouveau:magebloom_fiber', 8)], icon='ars_elemental:fire_caster_tome'),
            Q('arcane/elements/focus', 'Lesser Focus',
              t_any_item(['ars_elemental:lesser_air_focus', 'ars_elemental:lesser_earth_focus'], 'A lesser air or earth focus'), kind='side',
              desc=['Imbue a lesser focus and wear it. Spells of its element strike harder.'],
              rewards=[T('theurgy:mercury_shard', 4)], icon='ars_elemental:lesser_air_focus'),
            Q('arcane/elements/prism', 'Prism', t_item('ars_elemental:advanced_prism'), kind='side',
              desc=['Set an advanced prism in the path of a spell. It turns the cast and gives it a new course.'],
              rewards=[T('tfc:gem/topaz', 2)]),
            Q('arcane/elements/haversack', 'Handy Haversack', t_item('ars_additions:handy_haversack'), kind='side',
              desc=['Bind a handy haversack to a chest. Right-click items onto it and they land in that',
                    'chest from anywhere.'],
              rewards=[T('tfc:gem/opal', 2)]),
            Q('arcane/elements/charm', 'Second Wind', t_item('ars_additions:undying_charm'), kind='side',
              desc=['Carry a charm of second wind. When the last blow lands, it keeps death at the door',
                    'while its charge lasts.'],
              rewards=[T('tfc:gem/sapphire', 2)]),
            Q('arcane/elements/wixie', 'Wixie Charm', t_item('ars_nouveau:wixie_charm'), kind='side',
              desc=['Make a Wixie charm at the enchanting apparatus with conjuration essence. Use it on a cauldron:',
                    'the Wixie crafts or brews what you show it.'],
              rewards=[T('ars_nouveau:source_gem', 4)]),
        ]),
        # Steam on Rails (Steam 'n' Rails Neoforge, Create Train Utilities (Create Train Doors), Create Railways Navigator)
        ('Steam on Rails', 'arcane/machine/burner', [
            Q('arcane/steam/station', 'Train Station', t_item('create:track_station'), kind='side',
              desc=['Set a station on your track. A train that reaches it stops, loads and leaves by its',
                      'schedule.'],
              rewards=[T('create:sturdy_sheet', 4)]),
            Q('arcane/steam/boiler', 'Boiler', t_item('railways:locometal_boiler'), kind='side',
              desc=['Build a locometal boiler for the engine. It gives the locomotive its body; the',
                    'train itself runs without fuel.'],
              rewards=[T('create:sturdy_sheet', 4)]),
            Q('arcane/steam/stack', 'Smokestack',
              
              t_any_item(['railways:smokestack_woodburner', 'railways:smokestack_coalburner',
                          'railways:smokestack_oilburner'], 'A smokestack'), kind='side',
              desc=['Fit a smokestack for wood, coal or oil. It puffs the smoke of its kind while the',
                    'train runs.'],
              rewards=[T('tfc:gem/lapis_lazuli', 4)], icon='railways:smokestack_woodburner'),
            Q('arcane/steam/schedule', 'Schedule', t_item('create:schedule'), kind='side',
              desc=['Write a schedule and hand it to the driver. Stops, waits and signals follow in order.'],
              rewards=[T('tfc:gem/lapis_lazuli', 4)]),
            Q('arcane/steam/door', 'Train Door', t_item('trainutilities:door_andesite_alloy'), kind='side',
              desc=['Hang a train door on the coach. It opens at the platform and shuts before the line moves.'],
              rewards=[T('tfc:gem/amethyst', 2)]),
            Q('arcane/steam/display', 'Advanced Display',
              t_item('createrailwaysnavigator:advanced_display'), kind='side',
              desc=['Mount an advanced display on the platform. It lists every train, its stop and its delay.'],
              rewards=[T('theurgy:mercury_shard', 4)]),
        ]),
        # Industry of Enchantment (Create: Enchantment Industry, Create: Dragons Plus)
        ('Industry of Enchantment', 'arcane/machine/enchant', [
            Q('arcane/industry/hatch', 'Experience Hatch',
              t_item('create_enchantment_industry:experience_hatch'), kind='side',
              desc=['Set an experience hatch on a tank. Right-click to pour your own experience in;',
                    'sneak and right-click to draw it back.'],
              rewards=[T('tfc:gem/amethyst', 2)]),
            Q('arcane/industry/printer', 'Printer', t_item('create_enchantment_industry:printer'), kind='side',
              desc=['Build a printer. It copies a written book as often as the ink and the experience last.'],
              rewards=[T('ars_nouveau:blank_parchment', 8)]),
            Q('arcane/industry/grind', 'Mechanical Grindstone',
              t_item('create_enchantment_industry:mechanical_grindstone'), kind='side',
              desc=['Build a mechanical grindstone. It wears the enchantments off old gear and gives their',
                      'experience back.'],
              rewards=[T('create:sturdy_sheet', 4)]),
            Q('arcane/industry/cutter', 'Gem Cutter', t_item('create_enchantment_industry:gem_cutter'), kind='side',
              desc=['Set a gem cutter on the line. It cuts gems by machine where the table needed your hands.'],
              rewards=[T('tfc:gem/ruby', 2)]),
            Q('arcane/industry/fluid', 'Fluid Hatch', t_item('create_dragons_plus:fluid_hatch'), kind='side',
              desc=['Set a fluid hatch on a tank. Right-click it with a bucket to pour in or fill up,',
                    'with no pipe needed.'],
              rewards=[T('theurgy:mercury_shard', 4)]),
        ]),
    ]),
    dict(key='industrial', filename='roads_industrial_age', stage='age_4', title='Industrial Age Side Roads', strands=[
        # Cannon Works (Create Big Cannons)
        ('Cannon Works', 'industrial/steel/chamber', [
            Q('industrial/cannon/lid', 'Foundry Lid', t_item('createbigcannons:basin_foundry_lid'), kind='side',
              desc=['Cover a basin with a foundry lid and melt metal in it. Cannon parts are cast in sand moulds.'],
              rewards=[T('createbigcannons:packed_gunpowder', 4)]),
            Q('industrial/cannon/drill', 'Cannon Drill', t_item('createbigcannons:cannon_drill'), kind='side',
              desc=['Set a cannon drill on the cast tube. It bores the barrel true, one block of steel at a time.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
            Q('industrial/cannon/mount', 'Cannon Mount', t_item('createbigcannons:cannon_mount'), kind='side',
              desc=['Raise a cannon mount and fit the tube. It aims by turning shafts and holds against the',
                      'recoil.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/cannon/loader', 'Cannon Loader', t_item('createbigcannons:cannon_loader'), kind='side',
              desc=['Fit a cannon loader behind the breech. It pushes powder and shell home without a hand in the',
                      'way.'],
              rewards=[T('createbigcannons:powder_charge', 4)]),
            Q('industrial/cannon/charge', 'A Big Cartridge', t_item('createbigcannons:big_cartridge'), kind='side',
              desc=['Fill a big cartridge with powder. The charge decides how far the shell flies.'],
              rewards=[T('createbigcannons:solid_shot', 4)]),
            Q('industrial/cannon/shell', 'Armor-Piercing Shell', t_item('createbigcannons:ap_shell'), kind='side',
              desc=['Build an armor-piercing shell in the mechanical crafter. It pierces blocks and',
                    'bursts inside the walls behind them.'],
              rewards=[T('tfc:metal/ingot/black_steel')]),
        ]),
        # Ships in the Sky (Create Aeronautics)
        ('Ships in the Sky', 'industrial/side/airship', [
            Q('industrial/ship/assembler', 'Physics Assembler', t_item('simulated:physics_assembler'), kind='side',
              desc=['Glue a hull together and pull the lever of a physics assembler. The whole frame',
                    'becomes one body with weight.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/ship/burner', 'Adjustable Burner', t_item('aeronautics:adjustable_burner'), kind='side',
              desc=['Set an adjustable burner under the envelope. Its heat fills the balloon.'],
              rewards=[T('immersiveengineering:hemp_fabric', 8)]),
            Q('industrial/ship/envelope', 'Envelope', t_item('aeronautics:white_envelope', 16), kind='side',
              desc=['Stitch envelopes into a balloon. The hot air inside lifts the hull above the clouds.'],
              rewards=[T('immersiveengineering:wirecoil_structure_rope', 8)]),
            Q('industrial/ship/bearing', 'Propeller Bearing', t_item('aeronautics:propeller_bearing'), kind='side',
              desc=['Set a propeller on a bearing at the stern. Turned by a shaft, it drives the ship forward.'],
              rewards=[T('immersiveengineering:sheetmetal_steel', 4)]),
            Q('industrial/ship/wheel', 'Steering Wheel', t_item('simulated:steering_wheel'), kind='side',
              desc=['Fit a steering wheel on the deck. Hold right-click and turn it; its rotation',
                    'steers the bearings it drives.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
            Q('industrial/ship/table', 'Navigation Table', t_item('simulated:navigation_table'), kind='side',
              desc=['Lay a navigation table and give it a compass or a map. Its redstone signal points',
                    'toward the goal.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)]),
            Q('industrial/ship/wheels', 'Wheel Mount', t_item('offroad:wheel_mount', 4), kind='side',
              desc=['Bolt wheel mounts and tires to a frame. A buggy goes where a ship cannot.'],
              rewards=[T('immersiveengineering:sheetmetal_steel', 4)]),
        ]),
        # Mobs and Wings (Create Mechanical Spawner, Create Jetpack)
        ('Mobs and Wings', 'industrial/side/spawner', [
            Q('industrial/mobs/casing', 'Reinforced Casing',
              t_item('create_mechanical_spawner:reinforced_brass_casing', 8), kind='side',
              desc=['Apply an iron block to brass casing for reinforced casing. It is the wall stone of',
                    'a spawner hall.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/mobs/glass', 'Dark Glass',
              t_item('create_mechanical_spawner:framed_dark_glass', 4), kind='side',
              desc=['Darken framed glass with coal for the walls of the spawner hall. You watch the',
                    'stock without opening a door.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)]),
            Q('industrial/mobs/collector', 'Loot Collector',
              t_item('create_mechanical_spawner:loot_collector'), kind='side',
              desc=['Set a loot collector at the spawn point of a spawner. The drops come straight into',
                    'it, and no beast walks out.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/mobs/wings', 'Netherite Jetpack', t_item('create_jetpack:netherite_jetpack'), kind='side',
              desc=['Upgrade the jetpack with netherite. It is the strongest the maker of air-wings gives.'],
              rewards=[P('immersiveengineering:hammer')]),
        ]),
        # Hands Retired (Advanced TFC Tech Unofficial)
        ('Hands Retired', 'industrial/logi/atfc', [
            Q('industrial/retired/pirn', 'Pirn', t_item('advancedtfctech:pirn', 4), kind='side',
              desc=['Craft a pirn. Wound with wool, fiber or silk, it feeds the power loom.'],
              rewards=[T('immersiveengineering:hemp_fiber', 16)]),
            Q('industrial/retired/wound', 'Wound Pirn',
              
              t_any_item(['advancedtfctech:wool_winded_pirn', 'advancedtfctech:fiber_winded_pirn',
                          'advancedtfctech:silk_winded_pirn'], 'A wound pirn', count=4), kind='side',
              desc=['Wind a pirn with wool, fiber or silk. The loom weaves what the pirn carries.'],
              rewards=[T('immersiveengineering:hemp_fabric', 4)], icon='advancedtfctech:wool_winded_pirn'),
            Q('industrial/retired/loom', 'Power Loom',
              t_observe('advancedtfctech:power_loom', kind='block', title='Look at a formed power loom'), kind='side',
              desc=["Build a power loom from engineering blocks and form it with the Engineer's Hammer. It weaves",
                      'cloth without a hand.'],
              rewards=[T('immersiveengineering:component_iron', 2)], icon='advancedtfctech:power_loom'),
            Q('industrial/retired/mill', 'Grist Mill',
              t_observe('advancedtfctech:grist_mill', kind='block', title='Look at a formed grist mill'), kind='side',
              desc=['Build a grist mill. Grain goes in and flour comes out, with no quern and no hand to turn it.'],
              rewards=[T('immersiveengineering:conveyor_basic', 4)], icon='advancedtfctech:grist_mill'),
            Q('industrial/retired/beam', 'Beamhouse',
              t_observe('advancedtfctech:beamhouse', kind='block', title='Look at a formed beamhouse'), kind='side',
              desc=['Build a beamhouse. Hides are limed, washed and tanned in it by machine; the',
                    'scraping waits for the fleshing machine.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)], icon='advancedtfctech:beamhouse'),
            Q('industrial/retired/blades', 'Fleshing Blades', t_item('advancedtfctech:fleshing_blades'), kind='side',
              desc=['Craft fleshing blades now for the fleshing machine of the next Age. Fitted to it,',
                    'they scrape hides with no hand on the knife.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
        ]),
        # Gauges and Ledgers (Create: Extra Gauges, Create Factory Logistics, Engineered Schematics)
        ('Gauges and Ledgers', 'industrial/logi/gauge', [
            Q('industrial/gauge/link', 'Network Link', t_item('create_factory_logistics:network_link'), kind='side',
              desc=['Attach a network link to a threshold switch. A blaze burner is then fed only when it needs',
                      'fuel.'],
              rewards=[T('immersiveengineering:conveyor_basic', 4)]),
            Q('industrial/gauge/fluid', 'Fluid Gauge',
              t_item('create_factory_logistics:factory_fluid_gauge'), kind='side',
              desc=['Set a fluid gauge on a tank. It orders more when the level falls, as the item gauge does for',
                      'crates.'],
              rewards=[T('immersiveengineering:wooden_barrel', 2)]),
            Q('industrial/gauge/barrel', 'Barrel Packager',
              t_item('create_factory_logistics:barrel_packager'), kind='side',
              desc=['Build a barrel packager. Liquid travels the package lines sealed in barrels.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/gauge/logic', 'Logic Gauge', t_item('extra_gauges:logic_gauge'), kind='side',
              desc=['Craft a logic gauge. It joins signals as AND or OR, and it can hold a factory',
                    'gauge back until the rule is met.'],
              rewards=[T('immersiveengineering:rs_engineering', 2)]),
            Q('industrial/gauge/count', 'Counter Gauge', t_item('extra_gauges:counter_gauge'), kind='side',
              desc=['Craft a counter gauge. It counts redstone pulses and gives a signal when the count',
                    'is reached.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/gauge/table', 'Schematic Table',
              t_item('engineered_schematics:schematic_table_block'), kind='side',
              desc=['Build a schematic table. A schematic of an engineering machine lists its blocks, and the',
                      'Hammer forms it.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)]),
        ]),
        # Terminals and Packs (Tom's Simple Storage Mod, Sophisticated Backpacks)
        ('Terminals and Packs', 'industrial/logi/ticker', [
            Q('industrial/terminal/connector', 'Inventory Connector',
              t_item('toms_storage:inventory_connector'), kind='side',
              desc=['Place an inventory connector. Every chest that touches it joins one list for the terminal.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/terminal/cable', 'Inventory Cable',
              t_item('toms_storage:inventory_cable_connector', 4), kind='side',
              desc=['Run inventory cable to a far chest and attach it with a cable connector. Distance stops',
                      'mattering.'],
              rewards=[T('immersiveengineering:conveyor_basic', 4)]),
            Q('industrial/terminal/craft', 'Crafting Terminal', t_item('toms_storage:crafting_terminal'), kind='side',
              desc=['Craft a crafting terminal. A crafting table is built in, and it takes from the whole',
                      'network.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/terminal/wireless', 'Wireless Terminal',
              t_item('toms_storage:adv_wireless_terminal'), kind='side',
              desc=['Craft an advanced wireless terminal. Open your storage from the field, within its reach.'],
              rewards=[T('immersiveengineering:component_steel', 2)]),
            Q('industrial/terminal/diamond', 'Diamond Pack',
              t_item('sophisticatedbackpacks:diamond_backpack'), kind='side',
              desc=['Upgrade the pack to diamond. It carries far more, and the upgrade slots grow with it.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)]),
            Q('industrial/terminal/magnet', 'Advanced Magnet',
              t_item('sophisticatedbackpacks:advanced_magnet_upgrade'), kind='side',
              desc=['Fit an advanced magnet upgrade. Loose goods fly to the pack from far off.'],
              rewards=[P('immersiveengineering:hammer')]),
        ]),
        # Jars and Stainless (FirmaLife)
        ('Jars and Stainless', 'industrial/steel/black', [
            Q('industrial/jar/ore', 'Chromite',
              
              t_any_item(['firmalife:ore/poor_chromite', 'firmalife:ore/normal_chromite',
                          'firmalife:ore/rich_chromite'], 'Any chromite ore piece'), kind='side',
              desc=['Find chromite in the rock. Its metal is the last ingredient of stainless steel.'],
              rewards=[T('immersiveengineering:graphite_electrode')], icon='firmalife:ore/poor_chromite'),
            Q('industrial/jar/chrome', 'Chromium', t_item('firmalife:metal/ingot/chromium', 4), kind='side',
              desc=['Smelt chromium from the ore. It is hard and bright and rusts no more than glass.'],
              rewards=[T('immersiveengineering:component_iron', 2)]),
            Q('industrial/jar/stainless', 'Stainless Steel',
              t_item('firmalife:metal/ingot/stainless_steel', 4), kind='side',
              desc=['Alloy steel, nickel and chromium into stainless steel. It never takes the taste of what it',
                      'holds.'],
              rewards=[T('immersiveengineering:sheetmetal_steel', 4)]),
            Q('industrial/jar/vat', 'Vat', t_item('firmalife:vat'), kind='side',
              desc=['Set a stainless steel vat on an oven. It boils sugar water, jam and sauces in',
                    'great measure.'],
              rewards=[T('immersiveengineering:electric_lantern', 4)]),
            Q('industrial/jar/jarring', 'Jarring Station', t_item('firmalife:jarring_station'), kind='side',
              desc=['Set a jarring station by the vat. It fills lidded jars with jam and seals them by',
                    'itself.'],
              rewards=[T('tfc:metal/ingot/black_steel')]),
        ]),
        # Veins of the Crossing (TFC + IE Crossover)
        ('Veins of the Crossing', 'industrial/ore/constantan', [
            Q('industrial/cross/galena', 'Galena',
              
              t_any_item(['tfc_ie_addon:ore/poor_galena', 'tfc_ie_addon:ore/normal_galena',
                          'tfc_ie_addon:ore/rich_galena', 'tfc_ie_addon:ore/small_galena'], 'Any galena ore piece'), kind='side',
              desc=['Mine galena, a grey vein with a heavy shine. It is the only ore of lead.'],
              rewards=[T('immersiveengineering:plate_lead', 2)], icon='tfc_ie_addon:ore/poor_galena'),
            Q('industrial/cross/lead', 'Lead Sheet', t_item('tfc_ie_addon:metal/sheet/lead', 2), kind='side',
              desc=['Hammer lead into a sheet on the anvil. It lines the tanks and shields the cables.'],
              rewards=[T('immersiveengineering:wirecoil_structure_rope', 4)]),
            Q('industrial/cross/constantan', 'Constantan Sheet',
              t_item('tfc_ie_addon:metal/sheet/constantan', 2), kind='side',
              desc=['Hammer constantan into a sheet. Its resistance stays the same whatever the heat.'],
              rewards=[T('immersiveengineering:plate_constantan', 2)]),
            Q('industrial/cross/electrum', 'Electrum Double Ingot',
              t_item('tfc_ie_addon:metal/double_ingot/electrum', 2), kind='side',
              desc=['Weld electrum into a double ingot. Gold and silver, tamed for the wire of the next Age.'],
              rewards=[T('immersiveengineering:plate_electrum', 2)]),
            Q('industrial/cross/drill', 'Black Steel Drillhead',
              t_item('tfc_ie_addon:drillhead_black_steel'), kind='side',
              desc=['Forge a black steel drillhead. It bites rock that wrought iron cannot scratch.'],
              rewards=[T('immersiveengineering:toolupgrade_drill_damage')]),
        ]),
    ]),
    dict(key='electric', filename='roads_electric_age', stage='age_5', title='Electric Age Side Roads', strands=[
        # Diesel Engines (Create: Diesel Generators, Create Crafts & Additions)
        ('Diesel Engines', 'electric/grid/diesel', [
            Q('electric/diesel/piston', 'Engine Piston',
              t_item('createdieselgenerators:engine_piston', 4), kind='side',
              desc=['Make engine pistons from andesite alloy, a shaft and zinc. Every diesel engine is',
                    'built around them.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/diesel/engine', 'Diesel Engine', t_item('createdieselgenerators:diesel_engine'), kind='side',
              desc=['Build a diesel engine. It turns a pipe of diesel into rotation in a small frame.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)]),
            Q('electric/diesel/silencer', 'Silencer', t_item('createdieselgenerators:engine_silencer'), kind='side',
              desc=['Fit a silencer to the exhaust. The engine stops roaring at your sleeping friends.'],
              rewards=[T('immersivepetroleum:petcoke', 8)]),
            Q('electric/diesel/turbo', 'Turbocharger',
              t_item('createdieselgenerators:engine_turbocharger'), kind='side',
              desc=['Fit a turbocharger. The engine runs twice as fast and burns twice as much.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/diesel/large', 'Large Engine',
              t_item('createdieselgenerators:large_diesel_engine'), kind='side',
              desc=['Stack modular diesel engines in a row. They work like the small one, and each',
                    'module adds its share of stress.'],
              rewards=[T('tfc:metal/ingot/red_steel')]),
            Q('electric/diesel/huge', 'Huge Engine', t_item('createdieselgenerators:huge_diesel_engine'), kind='side',
              desc=['Build the huge engine around a steam engine of the old Age. The old engine lives',
                    'inside the new one.'],
              rewards=[T('immersivepetroleum:gasoline_bucket')]),
        ]),
        # Jet Works (AeroEngine)
        ('Jet Works', 'electric/oil/key', [
            Q('electric/jet/alloy', 'Aviation Alloy', t_item('aeroengineering:aviation_alloy_ingot', 4), kind='side',
              desc=['Mix iron with netherite powder over a superheated burner. The aviation alloy ingot',
                    'stands the heat of a turbine.'],
              rewards=[T('tfc:metal/sheet/black_steel', 2)]),
            Q('electric/jet/casing', 'Engine Casing', t_item('aeroengineering:engine_casing', 4), kind='side',
              desc=['Build an engine casing. Every part of a jet sits inside one shell.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)]),
            Q('electric/jet/compressor', 'Compressor', t_item('aeroengineering:engine_compressor'), kind='side',
              desc=['Fit an engine compressor. It squeezes the air before it meets the fire.'],
              rewards=[T('immersiveengineering:wirecoil_redstone', 4)]),
            Q('electric/jet/combustor', 'Combustor', t_item('aeroengineering:engine_combustor'), kind='side',
              desc=['Fit a combustor behind it. Kerosene burns here, and the burning is the thrust.'],
              rewards=[T('immersivepetroleum:petcoke', 8)]),
            Q('electric/jet/turbine', 'Turbine', t_item('aeroengineering:engine_turbine'), kind='side',
              desc=['Fit a turbine. It takes some of the burning back to drive the compressor.'],
              rewards=[T('tfc:metal/sheet/black_steel', 2)]),
            Q('electric/jet/fuel', 'Aviation Kerosene',
              t_item('aeroengineering:aviation_kerosene_bucket'), kind='side',
              desc=['Heat refinery kerosene in a mixer into aviation kerosene and fill a bucket. The',
                    'engines burn it and nothing else.'],
              rewards=[T('tfc:metal/ingot/red_steel')]),
        ]),
        # Elemental Robes (Ars Elemancy)
        ('Elemental Robes', 'electric/side/afrit', [
            Q('electric/robes/essence', 'Elemental Essence',
              
              t_any_item(['ars_elemancy:cinder_essence', 'ars_elemancy:lava_essence',
                          'ars_elemancy:elemancer_essence'], 'Any fused essence'), kind='side',
              desc=['Imbue an essence of two elements fused, such as cinder from fire and air. It is',
                    'the thread of every dual robe.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)], icon='ars_elemancy:cinder_essence'),
            Q('electric/robes/robe', 'Robes of One',
              
              t_any_item(['ars_elemental:air_robes', 'ars_elemental:earth_robes', 'ars_elemental:fire_robes',
                          'ars_elemental:aqua_robes'], 'Robes of one element'), kind='side',
              desc=['Wear the robes of a single element. They suit casters of fire, air, earth or water.'],
              rewards=[T('ars_nouveau:source_gem', 8)], icon='ars_elemental:air_robes'),
            Q('electric/robes/cinder', 'Cinder Focus', t_item('ars_elemancy:cinder_focus'), kind='side',
              desc=['Carry a cinder focus. Two elements fused burn hotter than either alone.'],
              rewards=[T('ars_nouveau:source_jar', 2)]),
            Q('electric/robes/lava', 'Lava Robes', t_item('ars_elemancy:lava_robes'), kind='side',
              desc=['Wear lava robes. Two elements are woven into one cloth, and the caster draws on both.'],
              rewards=[T('ars_nouveau:magebloom_fiber', 8)]),
            Q('electric/robes/elemancer', 'Elemancer Robes', t_item('ars_elemancy:elemancer_robes'), kind='side',
              desc=['Wear the robes of the elemancer. All four elements answer to the one who wears them.'],
              rewards=[T('tfc:metal/ingot/red_steel')]),
        ]),
        # Builder's Hand (Building Gadgets)
        ("Builder's Hand", 'electric/circ/assembler', [
            Q('electric/hand/build', 'Building Gadget', t_item('buildinggadgets2:gadget_building'), kind='side',
              desc=['Craft a building gadget. Mark a wall or a floor and it lays the blocks from your pack.'],
              rewards=[T('immersiveengineering:capacitor_mv')]),
            Q('electric/hand/exchange', 'Exchanging Gadget',
              t_item('buildinggadgets2:gadget_exchanging'), kind='side',
              desc=['Craft an exchanging gadget. It swaps a wall of blocks in one sweep, but never ore or the raw stone',
                    'of the wild.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/hand/destroy', 'Destruction Gadget',
              t_item('buildinggadgets2:gadget_destruction'), kind='side',
              desc=['Craft a destruction gadget. It clears a marked cube of blocks at the cost of',
                    'power.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)]),
        ]),
        # Flux Without Loss (Alternating Flux NeoForge)
        ('Flux Without Loss', 'electric/grid/af', [
            Q('electric/flux/relay', 'Flux Relay', t_item('alternatingflux:connector_af_relay', 2), kind='side',
              desc=['Hang a flux relay between poles. It carries the current across a valley with hardly a loss.'],
              rewards=[T('immersiveengineering:connector_mv', 4)]),
            Q('electric/flux/trans', 'Flux Transformer',
              t_item('alternatingflux:connector_af_transformer'), kind='side',
              desc=['Set a flux transformer where flux wire meets the old grid. It joins both without a waste of',
                      'power.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/flux/wire', 'Constantan Wire', t_item('alternatingflux:wire_constantan', 8), kind='side',
              desc=['Draw constantan wire for the flux coils. Its resistance stays steady when the load changes.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)]),
            Q('electric/flux/haul', 'Long Haul', t_item('alternatingflux:wirecoil_af', 16), kind='side',
              desc=['Lay 16 flux coils end to end. The grid reaches the far shaft and the far mill.'],
              rewards=[T('immersiveengineering:connector_hv', 4)]),
        ]),
        # Parcel Roads (Create: Hypertubes, Create: Mobile Packages)
        ('Parcel Roads', 'electric/side/hypertube', [
            Q('electric/parcel/entrance', 'Tube Entrance', t_item('create_hypertube:hypertube_entrance'), kind='side',
              desc=['Set an entrance at the end of a hypertube. Step in and you are carried off along the line.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)]),
            Q('electric/parcel/junction', 'Tube Junction', t_item('create_hypertube:hypertube_junction'), kind='side',
              desc=['Set a junction where the tubes fork. You choose the branch as you pass through.'],
              rewards=[T('tfc:metal/sheet/black_steel', 2)]),
            Q('electric/parcel/accel', 'Tube Accelerator',
              t_item('create_hypertube:hypertube_accelerator'), kind='side',
              desc=['Set an accelerator on a long run. It gives the traveller speed on the way.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/parcel/mobile', 'Mobile Packager',
              t_item('create_mobile_packages:mobile_packager'), kind='side',
              desc=['Carry a mobile packager. It wraps and addresses parcels in your hands, wherever',
                    'you stand.'],
              rewards=[T('immersivepetroleum:petcoke', 8)]),
            Q('electric/parcel/bee', 'Bee Port', t_item('create_mobile_packages:bee_port'), kind='side',
              desc=['Set a bee port and load a robo bee. It flies a parcel addressed to a friend',
                    'straight to them.'],
              rewards=[T('immersiveengineering:wirecoil_redstone', 4)]),
            Q('electric/parcel/ticker', 'Portable Stock Ticker',
              t_item('create_mobile_packages:portable_stock_ticker'), kind='side',
              desc=['Carry a portable stock ticker. Order from your stores wherever you walk.'],
              rewards=[T('tfc:metal/ingot/red_steel')]),
        ]),
        # Liquid Beasts (Create Mechanical Spawner, Create: Dragons Plus, Create: Enchantment Industry)
        ('Liquid Beasts', 'electric/circ/wither', [
            Q('electric/liquid/pig', 'Spawn Fluid',
              
              t_any_item(['create_mechanical_spawner:spawn_fluid_pig_bucket',
                          'create_mechanical_spawner:spawn_fluid_cow_bucket'], 'Pig or cow spawn fluid'), kind='side',
              desc=['Fill a bucket with spawn fluid of the pig or the cow. The spawner turns it into living',
                      'stock.'],
              rewards=[T('immersiveengineering:ingot_lead', 4)], icon='create_mechanical_spawner:spawn_fluid_pig_bucket'),
            Q('electric/liquid/blaze', 'Blaze Fluid',
              t_item('create_mechanical_spawner:spawn_fluid_blaze_bucket'), kind='side',
              desc=['Fill a bucket with blaze spawn fluid. The spawner turns it into blazes and their rods.'],
              rewards=[T('immersivepetroleum:paraffin_wax', 8)]),
            Q('electric/liquid/wither', 'Wither Fluid',
              t_item('create_mechanical_spawner:spawn_fluid_wither_bucket'), kind='side',
              desc=['Brew wither spawn fluid if you dare. The star it bears is the one the Humming Core asks for.'],
              rewards=[T('immersiveengineering:component_electronic', 2)]),
            Q('electric/liquid/xp', 'Liquid Experience',
              t_item('create_enchantment_industry:experience_bucket'), kind='side',
              desc=['Fill a bucket with liquid experience. It feeds the enchanting machines and heals no wound.'],
              rewards=[T('create:experience_nugget', 16)]),
            Q('electric/liquid/breath', 'Dragon Breath',
              t_item('create_dragons_plus:dragon_breath_bucket'), kind='side',
              desc=['Fill a bucket with dragon breath. Fans and mixers use it where fire and water will not do.'],
              rewards=[T('tfc:metal/ingot/red_steel')]),
        ]),
    ]),
    dict(key='information', filename='roads_information_age', stage='age_6', title='Information Age Side Roads', strands=[
        # Essence Farms (Mystical Agriculture)
        ('Essence Farms', 'information/mek/crops', [
            Q('information/essence/farmland', 'Inferium Farmland',
              t_item('mysticalagriculture:inferium_farmland', 8), kind='side',
              desc=['Lay inferium farmland. Crops planted on it often drop a second seed, more so crops',
                    'of its own tier.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/essence/seeds', 'Metal Seeds',
              t_any_item(['mysticalagriculture:copper_seeds', 'mysticalagriculture:iron_seeds'], 'Copper or iron seeds', count=4), kind='side',
              desc=['Plant copper or iron seeds on inferium farmland and let them ripen. The crop is a bundle of',
                      'essence.'],
              rewards=[T('mysticalagriculture:mystical_fertilizer', 8)], icon='mysticalagriculture:copper_seeds'),
            Q('information/essence/essence', 'Metal Essence',
              t_any_item(['mysticalagriculture:copper_essence', 'mysticalagriculture:iron_essence'], 'Copper or iron essence', count=16), kind='side',
              desc=['Metal essence gives ore pieces, not ingots. Run them through the ore lines like any mined',
                      'piece.'],
              rewards=[T('mekanism:alloy_infused', 4)], icon='mysticalagriculture:copper_essence'),
            Q('information/essence/altar', 'Infusion Altar',
              t_item('mysticalagriculture:infusion_altar'), kind='side',
              desc=['Build an infusion altar with eight pedestals. Seeds of higher tiers are woven',
                    'there from lesser ones.'],
              rewards=[T('ae2:fluix_crystal', 4)]),
            Q('information/essence/can', 'Watering Can',
              t_item('mysticalagriculture:inferium_watering_can'), kind='side',
              desc=['Craft an inferium watering can. A splash on the field speeds every seed in its reach.'],
              rewards=[T('mekanism:hdpe_sheet', 4)]),
            Q('information/essence/accel', 'Growth Accelerator',
              t_item('mysticalagriculture:inferium_growth_accelerator'), kind='side',
              desc=['Set a growth accelerator under the farmland. It hastens the crop above it with no',
                    'one to tend it.'],
              rewards=[T('tfc:metal/ingot/blue_steel')]),
        ]),
        # Pattern Halls (ExtendedAE, Applied Energistics 2 Wireless Terminals)
        ('Pattern Halls', 'information/digital/assembler', [
            Q('information/halls/provider', 'Extended Pattern Provider',
              t_item('extendedae:ex_pattern_provider'), kind='side',
              desc=['Build an extended pattern provider. It holds many more patterns than the plain one.'],
              rewards=[T('ae2:silicon', 8)]),
            Q('information/halls/interface', 'Extended Interface', t_item('extendedae:ex_interface'), kind='side',
              desc=['Build an extended interface. It exposes more slots to the machines around it.'],
              rewards=[T('ae2:fluix_crystal', 4)]),
            Q('information/halls/matrix', 'Assembler Matrix',
              t_item('extendedae:assembler_matrix_frame', 8), kind='side',
              desc=['Raise a frame of assembler matrix walls and fill it with crafters, speed blocks and',
                      'patterns. One cube replaces a hall.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/halls/pattern', 'Wireless Patterns',
              t_item('ae2wtlib:wireless_pattern_access_terminal'), kind='side',
              desc=['Craft a wireless pattern access terminal. Encode patterns anywhere, without a walk back to',
                      'the base.'],
              rewards=[T('ae2:certus_quartz_crystal', 8)]),
            Q('information/halls/universal', 'Universal Terminal',
              t_item('ae2wtlib:wireless_universal_terminal'), kind='side',
              desc=['Craft a universal terminal. One slot of your pack opens the network, the crafting grid and',
                      'patterns.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
        ]),
        # Bridges of the Network (Applied Mekanistics, Ars nergistique, Create: Applied Kinetics, Create Stock Bridge, More Immersive Wires)
        ('Bridges of the Network', 'information/digital/key', [
            Q('information/bridges/gas', 'Chemical Cell', t_item('appmek:chemical_storage_cell_1k'), kind='side',
              desc=['Build a chemical storage cell. Gases and slurries enter the network like items.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/bridges/source', 'Source Cell', t_item('arseng:source_storage_cell_1k'), kind='side',
              desc=['Move Source through a Source jar network or through the ME network. Machines',
                      'carry magic, magic never carries machines.'],
              rewards=[T('ae2:fluix_crystal', 4)]),
            Q('information/bridges/proxy', 'ME Proxy', t_item('createappliedkinetics:me_proxy'), kind='side',
              desc=['Place an ME proxy. Belts and funnels reach into the network through it as into a chest.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/bridges/stock', 'Stock Bridge', t_item('createstockbridge:ae_bridge'), kind='side',
              desc=['Bind an AE bridge to a frogport address. The network can then order from your stores, and',
                      'your stores from the network.'],
              rewards=[T('ae2:silicon', 8)]),
            Q('information/bridges/wire', 'Network Wire', t_item('more_immersive_wires:ae_coil', 4), kind='side',
              desc=['Wire the network with an ME coil. Cable crosses a valley like power does.'],
              rewards=[T('mekanism:alloy_reinforced', 4)]),
        ]),
        # Refined Gear (Mekanism Tools, Mekanism Additions)
        ('Refined Gear', 'information/mek/hdpe', [
            Q('information/gear/plastic', 'Plastic Blocks',
              
              t_any_item(['mekanismadditions:red_plastic', 'mekanismadditions:blue_plastic',
                          'mekanismadditions:white_plastic'], 'Plastic in any color', count=8), kind='side',
              desc=['Craft plastic blocks from HDPE and dye. They come in all 16 colors.'],
              rewards=[T('mekanism:hdpe_sheet', 8)], icon='mekanismadditions:red_plastic'),
            Q('information/gear/obsidian', 'Refined Obsidian Pickaxe',
              t_item('mekanismtools:refined_obsidian_pickaxe'), kind='side',
              desc=['Forge a pickaxe of refined obsidian. It is a tool of the factory age, tough and quick.'],
              rewards=[T('mekanism:ingot_refined_obsidian', 2)]),
            Q('information/gear/shield', 'Refined Shield',
              t_item('mekanismtools:refined_obsidian_shield'), kind='side',
              desc=['Forge a shield of refined obsidian. It turns blows that would split a wooden one.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/gear/glow', 'Glowstone Armor',
              t_item('mekanismtools:refined_glowstone_chestplate'), kind='side',
              desc=['Forge a chestplate of refined glowstone. Armor from the factory itself frees you from the',
                      'old forge.'],
              rewards=[P('mekanism:configurator')]),
        ]),
        # Laser Lines (Mekanism Lasers)
        ('Laser Lines', 'information/mek/energy', [
            Q('information/lasers/laser', 'Basic Laser', t_item('mekanism_lasers:basic_laser'), kind='side',
              desc=['Set a basic laser and power it. The beam crosses a hall in a straight line.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/lasers/split', 'Laser Splitter', t_item('mekanism_lasers:laser_splitter'), kind='side',
              desc=['Set a splitter in the beam. It divides the light into up to four lines.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/lasers/trans', 'Energy Transformer',
              t_item('mekanism_lasers:energy_transformer'), kind='side',
              desc=['Set an energy transformer at the end of a beam. The light becomes FE again.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/lasers/remote', 'Remote Control', t_item('mekanism_lasers:remote_control'), kind='side',
              desc=['Link a toggleable laser to a remote control. A switch across the base turns a whole line on',
                      'and off.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/lasers/cell', 'Energy Storage Cell',
              t_item('mekanism_lasers:energy_storage_cell'), kind='side',
              desc=['Build an energy store of casing and ports with cells inside. Each cell deepens the',
                    'store, and its ports take and give power.'],
              rewards=[T('mekanism:energy_tablet')]),
        ]),
        # Bigger Machines (Mekanism:More Machine)
        ('Bigger Machines', 'information/mek/key', [
            Q('information/machines/wash', 'Washing Factory', t_item('mekmm:basic_washing_factory'), kind='side',
              desc=['Build a washing factory. It runs the washing line many times over for the cost of one',
                      'machine.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/machines/tank', 'Chemical Tank', t_item('mekmm:basic_mid_chemical_tank'), kind='side',
              desc=['Build a mid-size chemical tank. A factory line drinks gas faster than a basic tank can fill.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/machines/air', 'Ambient Gas Collector', t_item('mekmm:ambient_gas_collector'), kind='side',
              desc=['Set an ambient gas collector. It draws gas from the air itself and wants nothing but power.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/machines/gen', 'Large Gas Generator',
              t_item('mekmm:large_gas_burning_generator'), kind='side',
              desc=['Build a large gas-burning generator. It burns the gas of your lines in a single, bigger',
                      'flame.'],
              rewards=[T('mekanism:alloy_reinforced', 4)]),
            Q('information/machines/core', 'Electrolysis Core',
              t_item('mekmm:advanced_electrolysis_core'), kind='side',
              desc=['Craft an advanced electrolysis core, the heart of the large gas generator. Each fission port',
                      'holds one later.'],
              rewards=[T('tfc:metal/ingot/blue_steel')]),
        ]),
        # More Metals (More Mekanism Processing)
        ('More Metals', 'information/mek/purify', [
            Q('information/metals/clump', 'Clumps',
              t_any_item(['moremekanismprocessing:clump_nickel', 'moremekanismprocessing:clump_zinc'], 'Nickel or zinc clumps', count=8), kind='side',
              desc=['Purify ore pieces in oxygen into clumps of nickel or zinc. Shards and crystals of the old metals',
                    'follow in the Space and Quantum Ages.'],
              rewards=[T('mekanism:alloy_infused', 4)], icon='moremekanismprocessing:clump_nickel'),
            Q('information/metals/dirty', 'Dirty Dust',
              t_item('moremekanismprocessing:dirty_dust_zinc', 8), kind='side',
              desc=['Crush clumps into dirty dust and clean it in the enrichment chamber. Dust is the last rung.'],
              rewards=[T('mekanism:alloy_reinforced', 4)]),
            Q('information/metals/lithium', 'Lithium', t_item('moremekanismprocessing:lithium_ingot', 4), kind='side',
              desc=['Crystallize lithium from brine and smelt the dust into an ingot. The fusion fuel',
                    'of the last Ages is bred from lithium.'],
              rewards=[T('mekanism:alloy_reinforced', 4)]),
            Q('information/metals/gem', 'Cinnabar',
              t_any_item(['moremekanismprocessing:gem_cinnabar', 'moremekanismprocessing:dust_ruby'], 'Cinnabar or ruby dust', count=4), kind='side',
              desc=['Crush a ruby into dust, or draw cinnabar from its ore. Gems pass through the',
                    'machines like metal here.'],
              rewards=[T('ae2:fluix_crystal', 4)], icon='moremekanismprocessing:gem_cinnabar'),
        ]),
        # Programming Magic (Psi)
        ('Programming Magic', 'information/side/archmage', [
            Q('information/psi/assembler', 'CAD Assembler', t_item('psi:cad_assembler'), kind='side',
              desc=['Build a CAD assembler. Its frame takes two control circuits from the factory line.'],
              rewards=[T('ae2:fluix_crystal', 4)]),
            Q('information/psi/dust', 'Psidust', t_item('psi:psidust', 8), kind='side',
              desc=['Assemble a bare CAD from an iron assembly and cast it at redstone dust on the',
                    'ground. The burst makes psidust.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/psi/cad', 'CAD', t_item('psi:cad'), kind='side',
              desc=['Assemble a CAD from an assembly, a core, a socket and a battery. Hold it and the',
                    'spells answer.'],
              rewards=[T('mekanism:alloy_infused', 4)]),
            Q('information/psi/programmer', 'Programmer', t_item('psi:programmer'), kind='side',
              desc=['Build a programmer. A spell is a small program of pieces you set in a grid.'],
              rewards=[T('mekanism:basic_control_circuit', 4)]),
            Q('information/psi/bullet', 'Spell Bullet', t_item('psi:spell_bullet', 4), kind='side',
              desc=['Load a program into a spell bullet and slot it in the CAD. Other bullets cast it',
                    'as a shot, a mine or a loop.'],
              rewards=[T('ae2:silicon', 8)]),
            Q('information/psi/psimetal', 'Psimetal', t_item('psi:psimetal'), kind='side',
              desc=['Cast an infusion spell at a gold ingot to turn it into psimetal. Its tools and',
                    'exosuit hold spells in the metal itself.'],
              rewards=[T('mekanism:alloy_reinforced', 4)]),
            Q('information/psi/exosuit', 'Exosuit', t_item('psi:psimetal_exosuit_chestplate'), kind='side',
              desc=['Wear a psimetal exosuit chestplate. Add a controller and sensors, and the suit casts for',
                      'you.'],
              rewards=[T('tfc:metal/ingot/blue_steel')]),
        ]),
    ]),
    dict(key='space', filename='roads_space_age', stage='age_7', title='Space Age Side Roads', strands=[
        # Mega Storage (MEGA Cells, AE2 Draconic Fusion Autocrafter)
        ('Mega Storage', 'space/side/mega', [
            Q('space/mega/bulk', 'Bulk Cell', t_item('megacells:bulk_item_cell'), kind='side',
              desc=['Build a bulk cell. It stores a single kind of item in numbers no ordinary cell can hold.'],
              rewards=[T('ad_astra:desh_plate', 2)]),
            Q('space/mega/compress', 'Compression Card', t_item('megacells:compression_card'), kind='side',
              desc=['Fit a compression card in a cell. Stone and ore are stored as blocks, and the cell holds',
                      'many more.'],
              rewards=[T('megacells:accumulation_processor', 4)]),
            Q('space/mega/crafting', 'MEGA Crafting Unit', t_item('megacells:mega_crafting_unit'), kind='side',
              desc=['Raise a MEGA crafting unit, the frame of a greater crafting computer. Its storage',
                    'blocks hold jobs too big for the old one.'],
              rewards=[T('ad_astra:ostrum_ingot', 2)]),
            Q('space/mega/energy', 'MEGA Energy Cell', t_item('megacells:mega_energy_cell'), kind='side',
              desc=['Set a MEGA energy cell. The network keeps a deep reserve for the long nights of the launch.'],
              rewards=[T('draconicevolution:draconium_ingot', 4)]),
            Q('space/mega/dock', 'Cell Dock', t_item('megacells:cell_dock'), kind='side',
              desc=['Place a cell dock. A cell can be slid in and out without a drive or a wall.'],
              rewards=[T('ad_astra:ostrum_plate', 4)]),
            Q('space/mega/fusion', 'Draconic Patterns',
              t_item('ae2_draconic_fusion_autocrafter:me_draconic_pattern_provider'), kind='side',
              desc=['Set a draconic pattern provider by the fusion core. The network then sends fusion',
                    'crafting jobs to it like any pattern.'],
              rewards=[P('draconicevolution:wyvern_capacitor')]),
        ]),
        # Light Bridges (Laser Bridges & Doors [Fabric/Forge/NeoForge])
        ('Light Bridges', 'space/moon/pad', [
            Q('space/light/source', 'Laser Source', t_item('laserbridges:laser_source_block'), kind='side',
              desc=['Power a laser source on a ledge. A bridge of hard light crosses the gap, and a',
                    'stronger signal reaches farther.'],
              rewards=[T('ad_astra:desh_nugget', 16)]),
            Q('space/light/full', 'Full Source', t_item('laserbridges:laser_source_fullblock'), kind='side',
              desc=['Set a full-block laser source in a wall. The bridge starts from stone with no machine in',
                      'sight.'],
              rewards=[T('ad_astra:desh_plate', 2)]),
            Q('space/light/fence', 'Laser Fence', t_item('laserbridges:laser_fence_source_block'), kind='side',
              desc=['Set a laser fence source in a doorway. Light stands in the gap and holds back what is not',
                      'welcome.'],
              rewards=[T('ad_astra:ostrum_plate', 4)]),
            Q('space/light/door', 'Hidden Door', t_item('laserbridges:laser_fence_source_fullblock'), kind='side',
              desc=['Set a full-block fence source in a wall. The door shows only when it is shut.'],
              rewards=[T('ad_astra:ostrum_ingot', 2)]),
        ]),
    ]),
    dict(key='quantum', filename='roads_quantum_age', stage='age_8', title='Quantum Age Side Roads', strands=[
        # Quantum Network (AdvancedAE)
        ('Quantum Network', 'quantum/awake/injectors', [
            Q('quantum/qnet/alloy', 'Quantum Alloy', t_item('advanced_ae:quantum_alloy', 4), kind='side',
              desc=['Make a quantum alloy ingot. Every part of the next network is cast from it.'],
              rewards=[T('ad_astra:calorite_plate', 2)]),
            Q('quantum/qnet/proc', 'Quantum Processor', t_item('advanced_ae:quantum_processor', 4), kind='side',
              desc=['Press a quantum processor. It is the brain of the next crafting computer.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('quantum/qnet/pattern', 'Advanced Pattern Provider',
              t_item('advanced_ae:adv_pattern_provider'), kind='side',
              desc=['Build an advanced extended pattern provider. It holds many more patterns and sends',
                    'each ingredient to the side you choose.'],
              rewards=[T('draconicevolution:awakened_draconium_ingot', 2)]),
            Q('quantum/qnet/accel', 'Quantum Accelerator', t_item('advanced_ae:quantum_accelerator', 4), kind='side',
              desc=['Raise quantum accelerators on a crafting computer. Jobs finish in a fraction of the time.'],
              rewards=[T('evolvedmekanism:alloy_hypercharged', 2)]),
            Q('quantum/qnet/thread', 'Multi-Threader', t_item('advanced_ae:quantum_multi_threader'), kind='side',
              desc=['Add a quantum multi-threader. The computer then runs many jobs side by side.'],
              rewards=[T('evolvedmekanism:alloy_hypercharged', 2)]),
            Q('quantum/qnet/store', 'Quantum Storage',
              t_any_item(['advanced_ae:quantum_storage_128', 'advanced_ae:quantum_storage_256'], 'A quantum storage block'), kind='side',
              desc=['Fit a 128M quantum computer storage block. One job can grow as large as the plan of a whole',
                      'factory.'],
              rewards=[P('draconicevolution:draconic_capacitor')], icon='advanced_ae:quantum_storage_128'),
        ]),
        # Evolved Factories (Evolved Mekanism, Mekanism: Neutron Activator)
        ('Evolved Factories', 'quantum/awake/injectors', [
            Q('quantum/evolved/chemixer', 'Chemixer', t_item('evolvedmekanism:chemixer'), kind='side',
              desc=['Build a chemixer. It mixes chemicals and items in one stroke.'],
              rewards=[T('ad_astra:calorite_plate', 2)]),
            Q('quantum/evolved/cube', 'Quantum Energy Cube',
              t_item('evolvedmekanism:quantum_energy_cube'), kind='side',
              desc=['Raise an overclocked cube to a quantum energy cube. The reactor and the gate drink from a store',
                    'this deep.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('quantum/evolved/wash', 'Quantum Washing Factory', t_item('mekmm:quantum_washing_factory'), kind='side',
              desc=['Build a quantum washing factory. Ore of every Age comes out clean at a pace no basic factory',
                      'reaches.'],
              rewards=[T('draconicevolution:awakened_draconium_ingot', 2)]),
            Q('quantum/evolved/nuclear', 'Nucleosynthesizer',
              t_item('mekmm:large_antiprotonic_nucleosynthesizer'), kind='side',
              desc=['Raise a large antiprotonic nucleosynthesizer. It spends antimatter on matter at a',
                    'pace the plain machine cannot match.'],
              rewards=[P('draconicevolution:draconic_capacitor')]),
            Q('quantum/evolved/neutron', 'Fusion Neutron Activator',
              t_item('mekanismneutronactivator:fusion_neutron_activator'), kind='side',
              desc=['Set it under the centre of a burning fusion reactor. Its neutrons turn nuclear waste into',
                    'polonium and lithium into tritium, day and night.'],
              rewards=[T('evolvedmekanism:alloy_hypercharged', 2)]),
        ]),
        # Insanium Fields (Mystical Agradditions)
        ('Insanium Fields', 'quantum/side/crystallizer', [
            Q('quantum/insanium/farm', 'Insanium Farmland',
              t_item('mysticalagradditions:insanium_farmland', 8), kind='side',
              desc=['Lay insanium farmland. The highest tier of crop grows only on it.'],
              rewards=[T('ad_astra:calorite_plate', 2)]),
            Q('quantum/insanium/essence', 'Insanium Essence',
              t_item('mysticalagradditions:insanium_essence', 8), kind='side',
              desc=['Harvest insanium essence. Rare metals and the gems of the last tier are woven from it.'],
              rewards=[T('evolvedmekanism:alloy_hypercharged', 2)]),
            Q('quantum/insanium/shard', 'Nether Star Shard',
              t_item('mysticalagradditions:nether_star_shard', 4), kind='side',
              desc=['Craft a nether star shard from the essence of its crop. Three shards make a star,',
                    'and the Wither can rest.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('quantum/insanium/apple', 'Supremium Apple',
              t_item('mysticalagradditions:supremium_apple'), kind='side',
              desc=['Craft a supremium apple from a golden apple and essence. It heals and shields you',
                    'for a time.'],
              rewards=[T('draconicevolution:awakened_draconium_ingot', 2)]),
            Q('quantum/insanium/ingot', 'Insanium Ingot',
              t_item('mysticalagradditions:insanium_ingot', 2), kind='side',
              desc=['Craft an insanium ingot from a prosperity ingot and insanium essence. It is the',
                    'hardest metal the fields give.'],
              rewards=[P('draconicevolution:draconic_capacitor')]),
        ]),
    ]),
    dict(key='singularity', filename='roads_singularity_age', stage='age_9', title='Singularity Age Side Roads', strands=[
        # Chain of Necklaces (Draconic Additions)
        ('Chain of Necklaces', 'singularity/chaos/shards', [
            Q('singularity/necks/wyvern', 'Wyvern Necklace', t_item('draconicadditions:wyvern_necklace'), kind='side',
              desc=['Craft a wyvern necklace. Worn at the throat, it carries armor modules as a',
                    'chestplate does.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('singularity/necks/draconic', 'Draconic Necklace',
              t_item('draconicadditions:draconic_necklace'), kind='side',
              desc=['Craft a draconic necklace. It holds more modules and more power than the wyvern',
                    'one.'],
              rewards=[T('sgjourney:naquadah_ingot', 4)]),
            Q('singularity/necks/chaotic', 'Chaotic Necklace',
              t_item('draconicadditions:chaotic_necklace'), kind='side',
              desc=['Craft a chaotic necklace, the last link of the chain, and fill it with your',
                    'strongest modules.'],
              rewards=[T('sgjourney:naquadah_ingot', 4)]),
        ]),
        # Crystals and Rings (Stargate Journey)
        ('Crystals and Rings', 'singularity/gate/blocks', [
            Q('singularity/crystal/energy', 'Energy Crystal', t_item('sgjourney:energy_crystal'), kind='side',
              desc=['Grow an energy crystal. Set in a device of the gate, it stores power and raises',
                    'its energy target.'],
              rewards=[T('sgjourney:naquadah_ingot', 4)]),
            Q('singularity/crystal/control', 'Control Crystal', t_item('sgjourney:control_crystal'), kind='side',
              desc=['Grow a control crystal. Devices of the gate answer to it.'],
              rewards=[T('evolvedmekanism:overclocked_control_circuit', 2)]),
            Q('singularity/crystal/rings', 'Transport Rings',
              t_item('sgjourney:ancient_transport_rings'), kind='side',
              desc=['Raise ancient transport rings. Step inside, and they carry you to another set of',
                    'rings nearby.'],
              rewards=[T('sgjourney:naquadah_ingot', 4)]),
            Q('singularity/crystal/iris', 'Iris', t_item('sgjourney:bronze_iris'), kind='side',
              desc=['Fit an iris over the gate. It closes the horizon to what should not come through.'],
              rewards=[T('sgjourney:naquadah_iris')]),
        ]),
    ]),
]


ROAD_ORDER = 40   # order_index of the first Side Roads chapter (Ages 0..10, Great Works 20..22)


def build_roads(rd, i):
    """A Side Roads chapter: one row per road, read left to right; the first quest hangs on its hook in the Age
    chapter (no line across chapters), the rest chain along the row."""
    lay = quest_layout.Layout('roads_' + rd['key'])
    flow = []
    titles = {q_['key']: q_['title'] for q_ in all_age_quests()}
    for n, (name, hook, line) in enumerate(rd['strands']):
        y = -n * 2.9
        prev = hook
        for k, q_ in enumerate(line):
            q_['deps'] = [prev]
            x = k * 2.15 + quest_layout.jit(q_['key'], 'rx', 0.12)
            lay.add(q_['key'], 'side', x, y + (0.42 if k % 2 else -0.08) + quest_layout.jit(q_['key'], 'ry', 0.1),
                    deps=q_['deps'])
            lay.labels[q_['key']] = q_['title']
            lay.tang[q_['key']] = (1.0, 0.0)
            if k:
                flow.append((prev, q_['key']))
            prev = q_['key']
        first = line[0]
        if first['sub']:
            raise SystemExit('%s: the first quest of a road gets its subtitle from the road' % first['key'])
        if hook not in titles:
            raise SystemExit('%s: road hook %s is no Age quest' % (name, hook))
        first['sub'] = '%s, off "%s"' % (name, titles[hook])
    lay.focus = rd['strands'][0][2][0]['key']
    lay.relax()
    lay.curves(flow)
    xp = XP[rd['stage']]
    quests = [laid_out(lay, q_, quest_rewards(q_, xp)) for _n, _h, line in rd['strands'] for q_ in line]
    LAYOUTS[rd['filename']] = lay
    icon = rd['strands'][0][2][0]['icon'] or rd['strands'][0][2][0]['tasks'][0][0]['item']['id']
    n_roads = amount(len(rd['strands']))
    return chapter_nbt('roads/' + rd['key'], rd['filename'], rd['title'],
                       ['Side roads of the %s: %s crafts to try, each off a step of its Age.'
                        % (rd['title'][:-len(' Side Roads')], n_roads)], icon, 'roads', ROAD_ORDER + i, quests,
                       required_stage=rd['stage'], tags=[rd['stage'], 'roads'],
                       extra={'autofocus_id': qid(lay.focus), 'default_quest_shape': 'ember'})


def all_age_quests():
    out = []
    for ch in AGE_CHAPTERS:
        out += chapter_quests(ch)
    return out


# ---------------------------------------------------------------- the Great Works
# Felix 2026-10-15: moving multiblocks as build strands with pictures (research n7-build section 2). One small chapter
# per work in the group "Great Works", hidden until its Age like an Age chapter, laid out in the shape of the build.
# Steps give real items for the shared base (team rewards, like the keystone supplies); the entry is the plan.
# Pacing and the links into the Age strands come in round B2.
AGE_ITEMS_DIR = os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'tags', 'item', 'age_items')


def live_variants(pattern, max_age):
    """Every item of the Age tags up to max_age (dev/gen_stage_locks.py) whose last path segment matches pattern: all
    live woods of a wood block, so an 'any wood' task takes TFC and AFC woods too (dev/validate_quests.py checks it)."""
    rx, out = re.compile(pattern), []
    for st in AGE_ORDER[:AGE_ORDER.index(max_age) + 1]:
        for v in json.load(open(os.path.join(AGE_ITEMS_DIR, st + '.json'), encoding='utf-8'))['values']:
            i = v['id'] if isinstance(v, dict) else v
            if rx.fullmatch(i.split(':', 1)[1].rsplit('/', 1)[-1]):
                out.append(i)
    return sorted(out)


# The Gatehouse's bridges (review 0.13.0: the task took only the eight vanilla woods, a TFC world builds ash or kapok).
# FTB Quests 2101.1.36 has no tag task without FTB Filter System, so a hidden advancement on the pack tag
# firmages:rope_bridges counts eight of one wood (inventory_changed counts per stack, like an item task per wood).
ROPE_BRIDGES = live_variants(r'rope_[a-z_]+_bridge', 'age_1')
WORK_TAGS = {os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'tags', 'item', 'rope_bridges.json'):
             {'replace': False, 'values': [{'id': i, 'required': False} for i in ROPE_BRIDGES]}}
WORK_ADVANCEMENTS = {
    os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'advancement', 'works', 'rope_bridges.json'):
        {'criteria': {'bridges': {'trigger': 'minecraft:inventory_changed',
                                  'conditions': {'items': [{'items': '#firmages:rope_bridges', 'count': {'min': 8}}]}}}}}

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
                      'Click the picture to watch the grate built, step by step.',
                      PAGE, Verbatim(image_line('build/gatehouse')), '',
                      'The plan: towers, ditch and bridge, the grate on its pulley, the drawbridge on its bearing.']),
              (-4.6, -3.0), None),
             (Q('works/gate/bridge', 'A Bridge Over the Ditch',
                t_adv('firmages:works/rope_bridges', 'Eight rope bridges of one wood'),
                icon='mcwbridges:rope_oak_bridge',
                desc=['Make eight rope bridges of one wood, any tree. They span the ditch before your gate.']),
              (-4.7, 0.3), [T('tfc:metal/ingot/copper', 4)]),
             (Q('works/gate/portcullis', 'Portcullis',
                [t_observe('create:rope_pulley', title='Look at the rope pulley over your gate'),
                 t_item('tfc:metal/bars/bronze', 9, title='Nine bronze bars for the grate')], icon='tfc:metal/bars/bronze',
                desc=[Verbatim(image_line('ponder/portcullis')),
                      'Glue a bronze grate together and hang it in a wall slot under a rope pulley. Turn the pulley '
                      'and the gate shuts.']),
              (-2.6, 3.1), [T('create:andesite_casing', 2)]),
             (Q('works/gate/drawbridge', 'Drawbridge',
                [t_observe('create:mechanical_bearing', title='Look at the bearing of your drawbridge'),
                 t_item('create:super_glue', title='Super glue')], icon='create:mechanical_bearing',
                desc=[Verbatim(image_line('ponder/mechanical_bearing')),
                      'Glue the bridge deck to a mechanical bearing at the gate. Turn it up at night and the ditch is '
                      'open again.']),
              (2.6, 3.1), [T('create:large_cogwheel', 2)]),
             (Q('works/gate/signal', 'One Signal', t_item('create:redstone_link', 2), icon='create:redstone_link',
                desc=['Pair two redstone links. The gate obeys a lever from across the yard.']),
              (4.7, 0.3), [T('create:analog_lever', 2)]),
             (Q('works/gate/goal', 'Shut the Gate',
                t_observe('create:gearshift[powered=true]', kind='block_state',
                          title='Look at the gearshift while the lever holds it'),
                kind='goal', icon='create:gearshift', sub='The gate holds',
                desc=[picture('works/gatehouse_shut.png', PICTURES['works']['gatehouse_shut'], 180,
                              click=ponder('create:rope_pulley'), hover='Watch it built'),
                      'Throw the lever on the gearshift of the pulley. The grate drops, the bridge rises, and the '
                      'night stays outside.']),
              (0.0, -0.9), [T('create:andesite_casing', 8), P('tfc:metal/shield/bronze'), T('firmages:lantern/bronze', 2)]),
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
                      'board that speaks and a clock in the gable. The hearth stays its heart.',
                      PAGE, Verbatim(image_line('build/living_house')), '',
                      'The plan: doors, lamps, board, lift and clock.']),
              (0.0, -4.6), None),
             (Q('works/house/doors', 'Doors That Slide', t_item('create:brass_door', 2), icon='create:brass_door',
                desc=['Hang two brass doors in your entrance. Sliding doors open by hand or on a redstone signal.']),
              (-3.8, -2.0), [T('tfc:metal/sheet/brass', 2)]),
             (Q('works/house/lights', 'Lights on a Link',
                [t_item('minecraft:redstone_lamp', 4, title='Four redstone lamps'),
                 t_item('create:redstone_link', 2, title='Two redstone links')], icon='minecraft:redstone_lamp',
                desc=['Wire four lamps to a redstone link. One lever by the door lights the whole house.']),
              (-3.8, 2.0), [T('create:electron_tube', 2)]),
             (Q('works/house/display', 'The House Speaks',
                [t_observe('create:display_link', title='Look at your display link'),
                 t_item('create:display_board', 4, title='Four display boards')], icon='create:display_link',
                desc=[Verbatim(image_line('ponder/display_link')),
                      'Show the time and your stores on a display board by the door.']),
              (0.0, 4.0), [T('create:brass_casing', 8)]),
             (Q('works/house/lift', 'A Lift',
                t_observe('create:elevator_contact', title='Look at a working elevator contact'),
                icon='create:elevator_contact',
                desc=[Verbatim(image_line('ponder/elevator')),
                      'Run an elevator pulley through the floors. A redstone contact on each floor turns into an '
                      'elevator contact once the lift works.']),
              (3.8, 2.0), [T('create:brass_funnel', 4)]),
             (Q('works/house/goal', 'The House Keeps Time',
                t_observe('create:clockwork_bearing', title='Look at the clockwork bearing in your gable'),
                kind='goal', icon='create:clockwork_bearing', sub='The house is awake',
                desc=[Verbatim(image_line('ponder/clockwork_bearing')),
                      'Set a clockwork bearing in the gable and give it hands. The house keeps the hours of the sun.']),
              (0.0, 0.0), [T('create:cuckoo_clock'), T('tfc:metal/sheet/wrought_iron', 2)]),
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
                      'Click the picture to watch the hatch built.',
                      PAGE, Verbatim(image_line('build/silo_closed')), '',
                      'The plan: the shaft, the pad at its foot and the two leaves over the rim.']),
              (0.0, -6.6), None),
             (Q('works/silo/shaft', 'The Shaft',
                t_observe('ad_astra:launch_pad', title='Look at the launch pad at the bottom of your shaft'),
                icon='ad_astra:launch_pad',
                desc=[Verbatim(image_line('build/silo_open')), '',
                      'Sink a shaft 15 by 15 and nine deep. Shore its walls; the rock remembers every cut. Set the pad '
                      'at the bottom.']),
              (0.0, -3.8), [T('ad_astra:fluid_pipe_duct', 8)]),
             (Q('works/silo/hatch', 'The Hatch',
                [t_item('create:gantry_carriage', 2, title='Two gantry carriages'),
                 t_item('create:sequenced_gearshift', title='A sequenced gearshift'),
                 t_item('create:super_glue', title='Super glue')], icon='create:gantry_carriage',
                desc=[Verbatim(image_line('ponder/silo_hatch')),
                      'Glue two leaves over the shaft. Each rides a gantry carriage and slides into a pocket under '
                      'the rim.']),
              (0.0, -1.1), [T('create:gantry_shaft', 8)]),
             (Q('works/silo/signal', 'One Signal',
                [t_item('create:redstone_link', 2, title='Two redstone links'),
                 t_item('create:pulse_repeater', title='A pulse repeater')], icon='create:redstone_link',
                desc=['One link opens the hatch. A delayed link wakes the pad once the leaves are clear.']),
              (-2.5, 1.5), [T('create:pulse_extender', 2)]),
             (Q('works/silo/fuel', 'Fuel Line',
                t_any_item(['immersivepetroleum:diesel_bucket', 'immersivepetroleum:kerosene_bucket'],
                           'Four buckets of diesel or kerosene', count=4), icon='immersivepetroleum:kerosene_bucket',
                desc=['Lay up four buckets of diesel or kerosene by the pad. A tier 1 or 2 rocket in the silo waits '
                      'full.']),
              (2.5, 1.5), [T('ad_astra:gas_tank')]),
             (Q('works/silo/goal', 'Launch from the Silo',
                t_observe('ad_astra:launch_pad[powered=true]', kind='block_state',
                          title='Look at the pad while the signal wakes it'),
                kind='goal', icon='ad_astra:launch_pad', sub='The sky opens from below',
                desc=['Open the hatch and wake the pad with one signal. Ten heartbeats later the rocket rises through '
                      'your own sky.']),
              (0.0, 4.3), [T('ad_astra:large_gas_tank'), T('firmages:lantern/space', 4)]),
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


# Where each Great Work branches off its Age (B2): the plan quest depends on this quest of the Age chapter, and the
# Age map carries a link to the plan beside it (FTB Quests QuestLink: linked_quest, x, y, shape, size; javap
# 2101.1.36). The work's chapter stays in the group Great Works, hidden until its Age like before.
WORK_HOOKS = {'gatehouse': ('bronze', 'bronze/kin/key'), 'house': ('iron', 'iron/power/brass'),
              'silo': ('space', 'space/moon/pad')}


LINK_SIZE = 1.25


def quest_link(chapter_key, lay, w):
    """A link to the Great Work's plan on the Age map, a satellite of its hook quest (placed by the layout)."""
    n = lay.nodes['link/' + w['key']]
    lid = qid('link/%s/%s' % (chapter_key, w['key']))
    entry = w['quests'][0][0]
    lang('quest_link', lid, 'title', entry['title'])
    return {'id': lid, 'linked_quest': qid(entry['key']), 'x': D(round(n.x, 2)), 'y': D(round(-n.y, 2)), 'shape': 'rune',
            'size': D(LINK_SIZE)}


def build_work(w):
    lay = quest_layout.Layout(w['key'])
    for q_, (x, y), rewards in w['quests']:
        q_['deps'] = WORK_DEPS.get(q_['key'], [])
        q_['rewards'] = rewards
        lay.add(q_['key'], q_['kind'], x, y, deps=q_['deps']).stiff = True
    hook = WORK_HOOKS[w['key']][1]
    w['quests'][0][0]['deps'] = [hook]   # the plan hangs on its Age strand (no line: another chapter)
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
    quests = [laid_out(lay, q_, quest_rewards(q_, XP[w['stage']])) for q_, _p, _rewards in w['quests']]
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


# The freeze of 1.0.1 (Felix Q1A) ended in round G4 (2026-10-18): this script is the source again, a run rewrites every
# chapter and lang/en_us.snbt. In-game edits are folded in here before a run (dev/quests-editing.md).
def main():
    LANG.clear()
    lang('file', '0000000000000001', 'title', 'The Chronicle')  # the quest book's one name (welcome screen, HUD, docs)
    gid = qid('group/ages')
    lang('chapter_group', gid, 'title', 'Ages')
    wid = qid('group/works')
    lang('chapter_group', wid, 'title', 'Great Works')
    rid = qid('group/roads')
    lang('chapter_group', rid, 'title', 'Side Roads')
    LAYOUTS.clear()
    chapters = [('the_firmament', build_firmament())]
    chapters += [(c['filename'], build_age_chapter(c)) for c in AGE_CHAPTERS]
    chapters += [(w['filename'], build_work(w)) for w in WORKS]
    chapters += [(rd['filename'], build_roads(rd, i)) for i, rd in enumerate(ROADS)]

    # wipe generated folders so renamed files do not linger
    for sub in ('chapters', 'reward_tables'):
        d = os.path.join(OUT, sub)
        if os.path.isdir(d):
            for f in os.listdir(d):
                if f.endswith('.snbt'):
                    os.remove(os.path.join(d, f))

    write(os.path.join(OUT, 'data.snbt'), data_nbt())
    write(os.path.join(OUT, 'chapter_groups.snbt'), {'chapter_groups': [{'id': gid}, {'id': wid}, {'id': rid}]})
    for name, ch in chapters:
        write(os.path.join(OUT, 'chapters', name + '.snbt'), ch)
    write(os.path.join(OUT, 'lang', 'en_us.snbt'), LANG)
    write_chapter_shaders(chapters)
    for path, adv in (list(proof_advancements().items()) + list(WORK_ADVANCEMENTS.items()) + list(WORK_TAGS.items())
                      + list(CHRONICLE_ADVANCEMENTS.items())):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(adv, indent=2) + '\n')
    print('wrote %d chapters, %d quests, %d lang keys to %s' % (len(chapters), sum(len(c['quests']) for _n, c in chapters),
                                                               len(LANG), OUT))


def write_chapter_shaders(chapters):
    """Certain Questing Additions 1.2.0.4: assets/certain_questing_additions/chapter_shaders.json maps
    Chapter.getCodeString() (16 hex digits, matched lower-case) to a core shader id; "default" covers the rest.
    The shaders are written by dev/gen_quest_art.py (kubejs/assets/firmages/shaders/core/sky_<key>.*)."""
    art_key = {'the_firmament': 'firmament'}
    art_key.update({c['filename']: c['stage'] for c in AGE_CHAPTERS})
    art_key.update({w['filename']: w['stage'] for w in WORKS})
    art_key.update({rd['filename']: rd['stage'] for rd in ROADS})
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
