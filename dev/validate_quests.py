#!/usr/bin/env python3
"""Firmament Ages - offline check of the FTB Quests book in config/ftbquests/quests/.

Parses the SNBT subset FTB Quests 2101.1.36 writes (and the comment lines its reader skips) and checks:
  ids        every object id is 16 hex digits, a positive signed long other than 0/1, and unique
  refs       quest dependencies, chapter groups and reward-table ids resolve; no dependency cycle
  items      every item id (tasks, rewards, reward tables, icons) is in dev/data/registry.json
  stages     every stage id (gamestage tasks/rewards, progressivestages_required_stage) has a stage file in
             config/progressivestages/stages/
  ages       no reward item and no task item belongs to a later Age than its chapter (age_items tags in
             kubejs/data/firmages/tags/item/age_items/); disabled items are never allowed, except as an icon when
             dev/derive_ages.py found them never reachable ([derived_hidden]: hidden in EMI, not stage-locked)
  structure  every Age chapter: required stage set, one entry quest (gamestage task = chapter stage), one goal
             quest that depends on every keystone, 3-4 keystones, 2-4 optional side quests, explanations
             optional. Goal of a shrine Age (an entry in the shrine offerings, see below): one gamestage
             task on the next Age, the offering as icon, no stage reward (the shrine grants it). Goal of the
             Singularity Age (age_9, no shrine tier): one gamestage task on finale_won (the end boss script grants
             it), no stage reward. Goal of any other Age (Dawn): its reward grants exactly the next Age stage, with
             auto "invisible". Optional "Raise the ring" quests (tag ring): one block_state observation of
             firmages:shrine_heart with ready=true and awakened=N in the chapter of age_N, only where ring_N exists
  grants     one grant path per Age, book-wide: age_0 by one quest reward, age_1..age_9 by their shrine tier and
             no quest reward, finale_won by no quest reward (warning while no KubeJS script grants it)
  tables     a reward table carries tag age:<stage> and is used only by chapters of that Age
  shrine     the offerings firmages-core reads (data/firmages/firmages_shrine/offerings.json from the mod
             sources, or its kubejs/data override): flat {"age_N": item}, the item in the Age tag of its key
             (not yet registered: warning, that ring takes no offering); each ring_N tier grants age_(N+1)
  text       every chapter and quest has an English title in lang/en_us.snbt, no lang key points to a
             missing object, no German letters in player text
  data       data.snbt: book never dropped on death, no loot crates, no emergency items
  pictures   every chapter image and every {image:...} description line points to a texture that exists (kubejs/assets
             or the firmages-core resources for the pack's namespaces), an open_quest click to a quest of the book, a
             Ponder click to a registered item; a chapter image's dependency is a quest of the book; a description
             picture is a line of its own, width/height are numbers, its click type is FTB Quests' (dev/quest-images.md)
  layout     no two quests of a chapter are drawn closer than dev/quest_layout.MIN_GAP grid units (their drawn size is
             6/7 of their size), dep_control_pts only name the quest's own dependencies, four numbers each; with
             --style at most LINE_LIMITS line crossings and lines over a quest they do not join per chapter
  wood       a task titled "any wood" or "one wood" accepts every live wood of its block up to the chapter's Age
             (its item tasks, or the item tag of its advancement)
  works      a Great Works chapter (tag works): its required stage, one entry, one goal, every quest leads to the goal
  portals    a dimension task needs its entry item: the KubeJS item tag that opens the portal (PORTAL_TAGS, read
             from kubejs/server_scripts/tags/*.js) is overridden and holds only items of the chapter's Age or earlier
With --mods <dir> (default test-server/mods if it exists) it also looks inside the mod jars for advancement,
entity type, entity/block tag and dimension ids used by tasks. With --style it also lints the player text against
dev/style-guide.md (warnings only): words per description by quest kind (knapping and crafting grid lines and
pictures excluded), more than
three paragraphs, banned and meta words, mod names, "Age <n>" anywhere (chapter and quest titles included), "!" anywhere, dashes and ellipses.
Exit code 1 on any error.
"""
import argparse
import glob
import json
import os
import re
import sys
import tomllib
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quest_layout  # noqa: E402  (dev/quest_layout.py: drawn sizes and the minimum gap)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KUBEJS_DATA = os.path.join(ROOT, 'kubejs', 'data')
QDIR = os.path.join(ROOT, 'config', 'ftbquests', 'quests')
AGES = ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9']
AGE_CHAPTER_STAGES = set(AGES)  # every Age has its chapter (rule set applies to all of them)
NEXT_STAGE = dict(zip(AGES, AGES[1:] + ['finale_won']))
# Dimension -> (item tag that activates its portal, the jar default). The default stays in force unless a KubeJS tag
# script calls removeAll on the tag. Empty since the Twilight Forest left the pack (2026-10-14); the Beneath opens by a
# sacrifice, the End and the planets by crafted blocks and rockets, none of them through an item tag.
PORTAL_TAGS = {}
MOD_DATA = os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'resources', 'data', 'firmages', 'firmages_shrine')
OFFERINGS_MOD = os.path.join(MOD_DATA, 'offerings.json')
OFFERINGS_PACK = os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'firmages_shrine', 'offerings.json')
SHRINE_TIERS = os.path.join(MOD_DATA, 'tier')
# Ids a new firmages-core registers before dev/data/registry.json is dumped again (gen_stage_locks.py --registry).
# Accepted with a warning. Empty since the 0.3.0 dump.
PENDING_MOD_IDS = set()


# ------------------------------------------------------------------------------------------------ SNBT parser
class Num:
    def __init__(self, value, kind):
        self.value, self.kind = value, kind

    def __repr__(self):
        return '%s%s' % (self.value, self.kind)


class SNBTError(Exception):
    pass


def parse_snbt(text, path):
    # FTB's reader drops whole lines whose trimmed text starts with // or #
    lines = [ln for ln in text.splitlines() if not ln.strip().startswith(('//', '#'))]
    s = '\n'.join(lines)
    pos = [0]

    def err(msg):
        line = s.count('\n', 0, pos[0]) + 1
        raise SNBTError('%s: line %d: %s' % (path, line, msg))

    def ws():
        while pos[0] < len(s) and s[pos[0]] in ' \t\r\n,':
            pos[0] += 1

    def peek():
        ws()
        return s[pos[0]] if pos[0] < len(s) else ''

    def quoted():
        qc = s[pos[0]]
        pos[0] += 1
        out = []
        while pos[0] < len(s):
            c = s[pos[0]]
            if c == '\\':
                out.append(s[pos[0] + 1])
                pos[0] += 2
                continue
            if c == qc:
                pos[0] += 1
                return ''.join(out)
            out.append(c)
            pos[0] += 1
        err('unterminated string')

    def word():
        start = pos[0]
        while pos[0] < len(s) and (s[pos[0]].isalnum() or s[pos[0]] in '._-+'):
            pos[0] += 1
        if start == pos[0]:
            err('unexpected character %r' % s[pos[0]:pos[0] + 1])
        return s[start:pos[0]]

    def scalar(w):
        if w == 'true':
            return True
        if w == 'false':
            return False
        m = re.fullmatch(r'([-+]?\d+)([bBsSlL]?)', w)
        if m:
            return Num(int(m.group(1)), m.group(2).lower() or 'i')
        m = re.fullmatch(r'([-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?)([fFdD]?)', w)
        if m:
            return Num(float(m.group(1)), m.group(4).lower() or 'd')
        return w

    def value():
        c = peek()
        if c == '{':
            pos[0] += 1
            d = {}
            while True:
                c = peek()
                if c == '}':
                    pos[0] += 1
                    return d
                if c == '':
                    err('unterminated compound')
                k = quoted() if c in '"\'' else word()
                if peek() not in ':=':
                    err('expected ":" after key %r' % k)
                pos[0] += 1
                if k in d:
                    err('duplicate key %r' % k)
                d[k] = value()
        if c == '[':
            pos[0] += 1
            if re.match(r'[BIL];', s[pos[0]:pos[0] + 2]):
                pos[0] += 2
            out = []
            while True:
                c = peek()
                if c == ']':
                    pos[0] += 1
                    return out
                if c == '':
                    err('unterminated list')
                out.append(value())
        if c in '"\'':
            return quoted()
        if c == '':
            err('unexpected end of file')
        return scalar(word())

    v = value()
    if peek() != '':
        err('trailing data')
    return v


def load(path):
    with open(path, encoding='utf-8') as f:
        try:
            shown = os.path.relpath(path, ROOT)
        except ValueError:  # other drive (e.g. a scratch copy)
            shown = path
        return parse_snbt(f.read(), shown)


# ------------------------------------------------------------------------------------------------ helpers
def age_index(stage):
    return AGES.index(stage) if stage in AGES else None


def load_age_tags():
    ages = {}
    for f in glob.glob(os.path.join(ROOT, 'kubejs', 'data', 'firmages', 'tags', 'item', 'age_items', '*.json')):
        st = os.path.basename(f)[:-5]
        for v in json.load(open(f, encoding='utf-8'))['values']:
            ages[v['id'] if isinstance(v, dict) else v] = st
    return ages


def load_stage_ids():
    ids = set()
    for f in glob.glob(os.path.join(ROOT, 'config', 'progressivestages', 'stages', '*.toml')):
        m = re.search(r'^\s*id\s*=\s*"([^"]+)"', open(f, encoding='utf-8').read(), re.M)
        if m:
            ids.add(m.group(1))
    return ids


def load_portal_tags():
    """tag -> (removeAll seen, [item ids added]) from the KubeJS tag scripts, for the PORTAL_TAGS tags."""
    out = {}
    for f in glob.glob(os.path.join(ROOT, 'kubejs', 'server_scripts', 'tags', '*.js')):
        src = open(f, encoding='utf-8').read()
        for tag, _ in PORTAL_TAGS.values():
            q = re.escape(tag)
            removed = re.search(r"removeAll\(\s*'%s'\s*\)" % q, src) is not None
            added = re.findall(r"\.add\(\s*'%s'\s*,\s*'([^']+)'\s*\)" % q, src)
            if removed or added:
                r0, a0 = out.get(tag, (False, []))
                out[tag] = (r0 or removed, a0 + added)
    return out


def strval(x):
    return x if isinstance(x, str) else None


class JarIndex:
    """Names inside the mod jars: data/<ns>/advancement/..., tags, dimensions, entity lang keys."""

    def __init__(self, mods_dir):
        self.files = set()
        self.entities = set()
        for jar in glob.glob(os.path.join(mods_dir, '*.jar')):
            try:
                with zipfile.ZipFile(jar) as z:
                    for n in z.namelist():
                        if n.startswith('data/'):
                            self.files.add(n)
                        elif re.fullmatch(r'assets/[^/]+/lang/en_us\.json', n):
                            try:
                                data = json.loads(z.read(n).decode('utf-8', 'replace'))
                            except ValueError:
                                continue
                            for k in data:
                                if k.startswith('entity.') and k.count('.') == 2:
                                    _, ns, path = k.split('.')
                                    self.entities.add('%s:%s' % (ns, path))
            except zipfile.BadZipFile:
                pass

    def has(self, kind, rid):
        ns, _, path = rid.lstrip('#').partition(':')
        folder = {'advancement': 'advancement', 'entity_tag': 'tags/entity_type', 'block_tag': 'tags/block',
                  'dimension': 'dimension'}[kind]
        # the pack's own datapack (kubejs/data) counts like a jar: the Chronicle's proof advancements live there
        return ('data/%s/%s/%s.json' % (ns, folder, path) in self.files
                or os.path.isfile(os.path.join(KUBEJS_DATA, ns, *folder.split('/'), *path.split('/')) + '.json'))


VANILLA_DIMS = {'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'}
# Vanilla entities have no lang file in the mod jars; kill tasks on them are accepted as they are.
VANILLA_ENTITIES = {'minecraft:wither', 'minecraft:ender_dragon', 'minecraft:zombie', 'minecraft:blaze',
                    'minecraft:wither_skeleton', 'minecraft:enderman'}


OPTIONAL_TAGS = {'side', 'explain', 'ring'}


def finale_script_grant():
    """True if firmages-core (0.4.0+: OriginService grants finale_won when the tagged final boss dies) or a KubeJS
    server script grants finale_won (stage grant command or a grant call naming it)."""
    svc = os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'java', 'dev', 'firmages', 'core', 'origin',
                       'OriginService.java')
    if os.path.isfile(svc) and 'FINALE_STAGE = "finale_won"' in open(svc, encoding='utf-8').read():
        return True
    pat = re.compile(r"(stage\s+(grant|add)\b[^\n]*finale_won|grant\w*\([^)\n]*finale_won)")
    for f in glob.glob(os.path.join(ROOT, 'kubejs', 'server_scripts', '**', '*.js'), recursive=True):
        if pat.search(open(f, encoding='utf-8').read()):
            return True
    return False


# ------------------------------------------------------------------------------------------------ style lint
# dev/style-guide.md. Word limits per quest kind (the quest's tag; untagged = step, Welcome chapter = info).
STYLE_LIMITS = {'step': 25, 'side': 25, 'keystone': 40, 'explain': 40, 'ring': 40, 'entry': 70, 'goal': 70,
                'info': 70, 'note': 70, 'guide': 90}
# Build guides (tag guide: the shrine rings, a build no viewer shows, so the teaching limit) are counted per page
# ({@pagebreak}); every other description as a whole. Picture lines
# ({image:...}) and page breaks are never counted and never linted as words.
PER_PAGE = {'guide'}
# Teaching quests: the one place a mechanic, or a build no recipe viewer shows, is explained (style guide rules 3
# and 4). They may run to STYLE_TEACH words (knapping and crafting grid lines are never counted).
STYLE_TEACH = 110
STYLE_TEACHING = {
    '6522D484BFB67425',  # dawn/stone/tool: knapping, step by step (Dawn has no recipe viewer)
    '7E2D0099177BFBE8',  # dawn/info/knapping: the other knapping patterns
    '30A13F8701E9EF80',  # stone/shrine/key: the Hearth Circle and the ghost preview
    '485FE6EFB6565F6E',  # quantum/marid/key: lending the Arcane Keystone and laying it back
    '124DDC9853158E78',  # industrial/logi/atfc: the thresher's blocks (no manual page, no viewer)
    '001477EB265CFAF8',  # dawn/side/torch: firepit and torches before the first night (Dawn has no recipe viewer)
    '6A272A1F4193488A',  # stone/info/clay: the clay knapping patterns (no viewer in the Stone Age)
    '484BD83C5FBABCE1',  # stone/info/home: the Stone Age home blocks (no viewer in the Stone Age)
    '5032B9C3899A4044',  # stone/hearth/bench: saw, lumber, planks and workbench for the 3x3 shrine recipes (no viewer)
    '33BEA94E7673D21D',  # arcane/spirit/nether: the Nether sacrifice (no recipe, no viewer, Field Guide hidden)
    '16708A1C39158F9C',  # dawn/info/rules: what Dawn withholds, the hand-break rule, no viewer before the Bronze Age
    '1CC29E163A94FCE6',  # stone/clay/kiln: building a pit kiln (a structure, no viewer in the Stone Age)
    '7997DC1DFF73EA36',  # stone/metal/charcoal: building a charcoal pit (a structure, no viewer in the Stone Age)
    '759550A45990B3C8',  # stone/metal/ingot: casting from the vessel into a mold (a screen, no viewer shows it)
    '71B6B8FAE5353DAB',  # bronze/smith/forge: building a charcoal forge (a structure no viewer shows)
    '54432F5A30AE7695',  # stone/info/woods: the wood class table and the sapling recipes (no viewer in the Stone Age)
    '64181B7811C2814E',  # dawn/info/hand: ground crafting, Dawn's only crafting (no grid, no viewer; SPEC 19.3)
    '65E696893D25C27D',  # quantum/fusion/frame: water cooling through a turbine and the injection floor (config, no viewer)
    '5C0C8D3E5C081AA2',  # singularity/gate/key: what a dial costs (config values; SGJ only says NOT_ENOUGH_POWER)
    '07D0E5C2A9206383',  # information/side/catalyst: the catalyst's three conditions (a placement rule, no viewer)
}
# knapping grid rows (5 squares of X and -) and crafting grid rows (3 squares, a letter per item: gen_quests.craft_grid)
GRID_LINE = re.compile(r'[X-]( [X-]){4}|[A-Z-]( [A-Z-]){2}')
# a picture line: FTB Library 2101.1.37 image component, the whole description line (dev/quest-images.md)
IMAGE_LINE = re.compile(r'\{image:[^ {}]+( [^{}]*)?\}')
# FTB Quests 2101.1.36 ImageClickAction.ActionType (javap): <type>:<data>
IMAGE_CLICK_TYPES = ('none', 'open_uri', 'open_quest', 'run_command', 'custom_event', 'show_recipe', 'show_docs')
ASSET_ROOTS = [os.path.join(ROOT, 'kubejs', 'assets'),
               os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'resources', 'assets')]


def wood_task_items(qd):
    """Items a wood task accepts: its item tasks, or the item tag of its advancement (kubejs/data, one criterion with
    an items predicate on a tag)."""
    out = set()
    for t in qd.get('tasks', []):
        if t.get('type') == 'item':
            out.add(t.get('item', {}).get('id'))
        elif t.get('type') == 'advancement':
            ns, path = strval(t.get('advancement')).split(':', 1)
            f = os.path.join(ROOT, 'kubejs', 'data', ns, 'advancement', path + '.json')
            if not os.path.isfile(f):
                continue
            for c in json.load(open(f, encoding='utf-8')).get('criteria', {}).values():
                for pred in c.get('conditions', {}).get('items', []):
                    it = pred.get('items')
                    if isinstance(it, str) and it.startswith('#'):
                        tns, tpath = it[1:].split(':', 1)
                        tf = os.path.join(ROOT, 'kubejs', 'data', tns, 'tags', 'item', tpath + '.json')
                        if os.path.isfile(tf):
                            out |= {v['id'] if isinstance(v, dict) else v
                                    for v in json.load(open(tf, encoding='utf-8'))['values']}
                    elif isinstance(it, str):
                        out.add(it)
                    elif isinstance(it, list):
                        out |= set(it)
    return {i for i in out if isinstance(i, str)}


def check_any_wood(qd, where, cage, item_age, E):
    """The family is the shared prefix and suffix of the accepted ids' last path segment (rope_ ... _bridge); every
    item of the Age tags up to the chapter's Age with a segment of that form must be accepted."""
    got = wood_task_items(qd)
    segs = [i.split(':', 1)[1].rsplit('/', 1)[-1] for i in got]
    if len(segs) < 2:
        E('%s: an "any wood" task accepts %d item, no wood family to check' % (where, len(segs)))
        return
    pre = os.path.commonprefix(segs)
    suf = os.path.commonprefix([x[::-1] for x in segs])[::-1]
    pre = pre[:pre.rfind('_') + 1]
    suf = suf[suf.find('_'):] if '_' in suf else ''
    rx = re.compile(re.escape(pre) + r'[a-z_]+' + re.escape(suf))
    live = {i for i, a in item_age.items() if a != 'disabled' and age_index(a) is not None and
            age_index(a) <= age_index(cage) and rx.fullmatch(i.split(':', 1)[1].rsplit('/', 1)[-1])}
    missing = sorted(live - got)
    if missing:
        E('%s: an "any wood" task misses %d live woods of %s*%s, e.g. %s' % (where, len(missing), pre, suf,
                                                                           ', '.join(missing[:4])))


def check_image_line(tx, where, E, items):
    """A description line with an {image:...} component: it is the whole line, the texture exists in the pack's
    resource pack (kubejs/assets) or the firmages-core jar sources, sizes are numbers, the click action is known and a
    Ponder link names a registered item."""
    if not IMAGE_LINE.fullmatch(tx.strip()):
        E('%s: a picture must be a line of its own: %r' % (where, tx[:80]))
        return
    props = {}
    for part in tx.strip()[1:-1].split(' '):
        if part:
            k, _, v = part.partition(':')
            props[k] = v.replace('%20', ' ')
    loc = props.get('image', '')
    ns, _, path = loc.partition(':')
    if not ns or not path or not re.fullmatch(r'[a-z0-9_.-]+', ns) or not re.fullmatch(r'[a-z0-9_./-]+', path):
        E('%s: picture %r is not a resource location' % (where, loc))
    elif not any(os.path.isfile(os.path.join(r, ns, *path.split('/'))) for r in ASSET_ROOTS):
        E('%s: picture %s has no file in kubejs/assets or the firmages-core assets' % (where, loc))
    for k in ('width', 'height'):
        if k in props and not re.fullmatch(r'[1-9][0-9]{0,3}', props[k]):
            E('%s: picture %s %s=%r is not a number' % (where, loc, k, props[k]))
    if props.get('align', 'center') not in ('left', 'center', 'right'):
        E('%s: picture %s align=%r' % (where, loc, props['align']))
    if props.get('fit', 'false') not in ('true', 'false'):
        E('%s: picture %s fit=%r' % (where, loc, props['fit']))
    click = props.get('click_action')
    if click:
        typ, _, data = click.partition(':')
        if typ not in IMAGE_CLICK_TYPES or not data:
            E('%s: picture %s click_action %r: type must be one of %s' % (where, loc, click, ', '.join(IMAGE_CLICK_TYPES)))
        elif typ == 'custom_event' and data.startswith('firmages:ponder/'):
            m = re.fullmatch(r'firmages:ponder/([a-z0-9_.-]+)/([a-z0-9_./-]+)', data)
            if not m:
                E('%s: picture %s Ponder link %r is not firmages:ponder/<ns>/<path>' % (where, loc, data))
            elif '%s:%s' % m.groups() not in items:
                E('%s: picture %s Ponder link names %s:%s, not a registered item' % ((where, loc) + m.groups()))


# (pattern, label); case-insensitive unless the label says "mod name" (those are matched as written).
STYLE_BANNED = [r'unlock the power', r'tapestry', r'testament', r'journey', r'embark', r'dive into',
                r"it'?s important to", r'unleash', r'incredible', r'amazing', r'\bepic\b', r'seamless', r'streamline',
                r'elevate', r'next level', r'powerful', r'mighty', r'\bvast\b', r'\bawaits?\b', r'\brealms?\b',
                r'harness', r'a variety of', r'allows? you', r'keep in mind', r'note that', r'basically',
                r'essentially', r'crucial', r'essential', r'\bvital\b', r'whole new', r'mysterious', r'make sure',
                r"don'?t forget", r'\bteams?\b', r'fallback:', r'signature item']
STYLE_META = [r'\bquests?\b', r'\bstages?\b', r'\bunlock\w*', r'\block(s|ed)?\b', r'\bEMI\b', r'\bJEI\b',
              r'\btooltips?\b', r'in this pack', r'\bmods?\b']
STYLE_MODS = ['TFC', r"Create(?! (a|an|the) )", 'Mekanism', 'AE2', 'IE', 'Draconic Evolution', 'Occultism', 'Ars', 'FTB',
              'Beneath', 'Firmalife', 'Mystical Agriculture', "Tom's", 'Summoning Rituals', 'Theurgy',
              'Immersive', 'Precision Prospecting', 'TFCreate', 'Aeronautics', "Steam 'n' Rails", 'Totemic',
              'Hostile Networks', 'Ad Astra', 'Applied Kinetics', 'Gateways?', 'Cataclysm', "Mowzie",
              'Apotheosis', 'Stargate Journey', 'Sophisticated', 'Supplementaries', 'Every Compat', 'Handcrafted',
              "Macaw's", 'Evolved Mekanism', 'ProgressiveStages', 'KubeJS', 'Jade']


# Dependency lines of a chapter map as FTB Quests draws them (dev/quest_layout.py, dev/preview_quests.py): a cubic
# Bezier from the quest (t = 0) to its dependency through dep_control_pts. A map reads as paths when few lines cross
# and no line runs over a quest it does not join (review 0.13.0: Electric, Space and Quantum Age had 5 and more).
LINE_LIMITS = {'crossings': 2, 'over_quests': 2}


def _bez(p0, c0, c1, p1, n=40):
    out = []
    for i in range(n + 1):
        t = i / n
        m = 1 - t
        out.append((m ** 3 * p0[0] + 3 * m * m * t * c0[0] + 3 * m * t * t * c1[0] + t ** 3 * p1[0],
                    m ** 3 * p0[1] + 3 * m * m * t * c0[1] + 3 * m * t * t * c1[1] + t ** 3 * p1[1]))
    return out


def _seg_cross(a, b, c, d):
    def orient(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    o1, o2, o3, o4 = orient(a, b, c), orient(a, b, d), orient(c, d, a), orient(c, d, b)
    return (o1 > 0) != (o2 > 0) and (o3 > 0) != (o4 > 0) and o1 * o2 != 0 and o3 * o4 != 0


def _seg_dist(p, a, b):
    vx, vy = b[0] - a[0], b[1] - a[1]
    L = vx * vx + vy * vy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / L))
    return ((p[0] - a[0] - t * vx) ** 2 + (p[1] - a[1] - t * vy) ** 2) ** 0.5


def map_lines(ch):
    """-> (crossings [(line, line)], over [(line, quest id)]) of one chapter; a line is 'dep>quest' by ids."""
    def num(v, d=0.0):
        return v.value if isinstance(v, Num) else d
    dsize = num(ch.get('default_quest_size'), 1.0) or 1.0
    qs = {str(q['id']).upper(): q for q in ch.get('quests', [])}
    size = {k: (num(q.get('size')) or dsize) for k, q in qs.items()}
    pos = {k: (num(q.get('x')), num(q.get('y'))) for k, q in qs.items()}
    lines = []
    for k, q in qs.items():
        if q.get('hide_dependency_lines'):
            continue
        cps = q.get('dep_control_pts') or {}
        sh = quest_layout.CP_SHIFT * size[k]
        for dep in q.get('dependencies', []):
            d = str(dep).upper()
            if d not in qs:
                continue
            pts = cps.get(d) or cps.get(str(dep))
            if pts:
                c_d, c_q = (num(pts[0]) - sh, num(pts[1]) - sh), (num(pts[2]) - sh, num(pts[3]) - sh)
                curve = _bez(pos[k], c_q, c_d, pos[d])
            else:
                curve = [pos[k], pos[d]]
            lines.append((d, k, curve))
    def box(pts):
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        return min(xs), min(ys), max(xs), max(ys)

    def meet(b1, b2, m=0.0):
        return b1[0] - m <= b2[2] and b2[0] - m <= b1[2] and b1[1] - m <= b2[3] and b2[1] - m <= b1[3]
    segs = [[((p, q), box((p, q))) for p, q in zip(c, c[1:])] for _d, _k, c in lines]
    boxes = [box(c) for _d, _k, c in lines]
    cross = []
    for i in range(len(lines)):
        for j in range(i + 1, len(lines)):
            a, b = lines[i], lines[j]
            if {a[0], a[1]} & {b[0], b[1]} or not meet(boxes[i], boxes[j]):
                continue
            if any(meet(ba, bb) and _seg_cross(sa[0], sa[1], sb[0], sb[1])
                   for sa, ba in segs[i] if meet(ba, boxes[j]) for sb, bb in segs[j]):
                cross.append(('%s>%s' % a[:2], '%s>%s' % b[:2]))
    over = []
    for i, (d, k, curve) in enumerate(lines):
        for n, c in pos.items():
            r = size[n] * quest_layout.CELL / 2 * 0.9
            if n in (d, k) or not meet(boxes[i], (c[0], c[1], c[0], c[1]), r):
                continue
            if any(_seg_dist(c, sg[0], sg[1]) < r for sg, _b in segs[i]):
                over.append(('%s>%s' % (d, k), n))
    return cross, over


def figure(t):
    """A knapping or crafting grid row or a picture line: neither counts as words nor makes a paragraph."""
    return bool(GRID_LINE.fullmatch(t) or IMAGE_LINE.fullmatch(t.strip()))


def style_lint(lang, chapters, W):
    """Warnings for player text that breaks dev/style-guide.md, and for chapter maps that stop reading as paths."""
    for cname, ch in sorted(chapters.items()):
        cross, over = map_lines(ch)
        if len(cross) > LINE_LIMITS['crossings']:
            W('chapters/%s: %d line crossings, at most %d (%s)' % (cname, len(cross), LINE_LIMITS['crossings'],
                                                                 '; '.join('%s x %s' % c for c in cross[:4])))
        if len(over) > LINE_LIMITS['over_quests']:
            W('chapters/%s: %d lines run over a quest they do not join, at most %d (%s)'
              % (cname, len(over), LINE_LIMITS['over_quests'], '; '.join('%s over %s' % o for o in over[:4])))
    kinds = {}
    for cname, ch in chapters.items():
        for qd in ch.get('quests', []):
            tags = [t for t in qd.get('tags', []) if t in STYLE_LIMITS]
            kinds[str(qd.get('id')).upper()] = tags[0] if tags else ('info' if cname == 'welcome' else 'step')
    banned = [(re.compile(p, re.I), 'banned word') for p in STYLE_BANNED]
    banned += [(re.compile(p, 0 if p in (r'\bEMI\b', r'\bJEI\b') else re.I), 'meta word') for p in STYLE_META]
    banned += [(re.compile(r'\b%s\b' % p), 'mod name') for p in STYLE_MODS]
    for key, val in lang.items():
        m = re.fullmatch(r'(\w+)\.([0-9A-Fa-f]{16})\.(\w+)', key)
        if not m:
            continue
        typ, oid, field = m.group(1), m.group(2).upper(), m.group(3)
        texts = val if isinstance(val, list) else [val]
        pages = [[]]
        for t in texts:
            if isinstance(t, str) and t.strip() == '{@pagebreak}':
                pages.append([])
            elif isinstance(t, str):  # pictures stay: figure() keeps them out of words and paragraphs
                pages[-1].append(t)
        texts = [t for page in pages for t in page]
        where = 'style: %s' % key
        for tx in texts:
            if IMAGE_LINE.fullmatch(tx.strip()):  # a picture: its path is no player text
                continue
            for rx, label in banned:
                hit = rx.search(tx)
                if hit:
                    W('%s: %s "%s" in %r' % (where, label, hit.group(0), tx[:70]))
            for bad in ('!', '—', '–', '...', '…'):
                if bad in tx:
                    W('%s: %r in %r' % (where, bad, tx[:70]))
            if re.search(r'\bAge \d', tx):
                W('%s: "Age <n>", write the Age name: %r' % (where, tx[:70]))
        if typ == 'quest' and field == 'quest_desc':
            kind = kinds.get(oid, 'step')
            limit = STYLE_TEACH if oid in STYLE_TEACHING else STYLE_LIMITS[kind]
            for n_page, page in enumerate(pages if kind in PER_PAGE else [texts]):
                at = '%s page %d' % (where, n_page + 1) if kind in PER_PAGE else where
                words = sum(len(t.split()) for t in page if not figure(t))
                if words > limit:
                    W('%s: %d words, the limit for a %s is %d' % (at, words, kind, limit))
                blocks, cur = [], []
                for t in page + ['']:
                    if t:
                        cur.append(t)
                    elif cur:
                        blocks.append(cur)
                        cur = []
                # a block with a knapping grid or a picture is the figure and its caption, not a paragraph
                paras = [b for b in blocks if not any(figure(t) for t in b)]
                if len(paras) > 3:
                    W('%s: %d paragraphs, at most 3' % (at, len(paras)))


# ------------------------------------------------------------------------------------------------ checks
def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--mods', help='mods folder with the pack jars (default: test-server/mods if present)')
    ap.add_argument('--no-jars', action='store_true', help='skip the jar lookups')
    ap.add_argument('--dir', default=QDIR, help='quest folder to check (default: config/ftbquests/quests)')
    ap.add_argument('--style', action='store_true', help='also lint the player text against dev/style-guide.md')
    args = ap.parse_args()
    qdir = args.dir

    errors, warnings = [], []
    E = errors.append
    W = warnings.append

    registry = json.load(open(os.path.join(ROOT, 'dev', 'data', 'registry.json'), encoding='utf-8'))
    items = set(registry['items'])
    blocks = set(registry.get('blocks', [])) | items  # block items cover placeable blocks
    item_age = load_age_tags()
    derived_hidden = set(tomllib.load(open(os.path.join(ROOT, 'dev', 'age_map.toml'), 'rb'))
                         .get('derived_hidden', {}).get('items', []))
    stage_ids = load_stage_ids()
    portal_tags = load_portal_tags()

    jars = None
    mods_dir = args.mods or os.path.join(ROOT, 'test-server', 'mods')
    if not args.no_jars and os.path.isdir(mods_dir):
        jars = JarIndex(mods_dir)

    # ---- load files
    data = load(os.path.join(qdir, 'data.snbt'))
    groups_file = load(os.path.join(qdir, 'chapter_groups.snbt'))
    lang = load(os.path.join(qdir, 'lang', 'en_us.snbt'))
    chapters = {os.path.basename(p)[:-5]: load(p) for p in sorted(glob.glob(os.path.join(qdir, 'chapters', '*.snbt')))}
    tables = {os.path.basename(p)[:-5]: load(p)
              for p in sorted(glob.glob(os.path.join(qdir, 'reward_tables', '*.snbt')))}

    # ---- ids
    objects = {}  # id -> (type, where)

    def reg_id(oid, typ, where):
        if not isinstance(oid, str) or not re.fullmatch(r'[0-9A-Fa-f]{16}', oid):
            E('%s: id %r is not 16 hex digits' % (where, oid))
            return
        n = int(oid, 16)
        if n >= 2 ** 63 or n < 2:
            E('%s: id %s is not a positive signed long > 1' % (where, oid))
        key = oid.upper()
        if key in objects:
            E('%s: id %s already used by %s' % (where, oid, objects[key][1]))
        objects[key] = (typ, where)

    group_ids = set()
    for g in groups_file.get('chapter_groups', []):
        reg_id(g.get('id'), 'chapter_group', 'chapter_groups.snbt')
        group_ids.add(str(g.get('id')).upper())

    table_ids = {}
    for name, t in tables.items():
        reg_id(t.get('id'), 'reward_table', 'reward_tables/%s' % name)
        table_ids[int(t['id'], 16)] = name
        for r in t.get('rewards', []):
            reg_id(r.get('id'), 'reward', 'reward_tables/%s reward' % name)

    quests = {}  # id -> (chapter file, quest dict)
    for cname, ch in chapters.items():
        reg_id(ch.get('id'), 'chapter', 'chapters/%s' % cname)
        for qd in ch.get('quests', []):
            reg_id(qd.get('id'), 'quest', 'chapters/%s quest' % cname)
            quests[str(qd.get('id')).upper()] = (cname, qd)
            for t in qd.get('tasks', []):
                reg_id(t.get('id'), 'task', 'chapters/%s task' % cname)
            for r in qd.get('rewards', []):
                reg_id(r.get('id'), 'reward', 'chapters/%s reward' % cname)
        for im in ch.get('images', []):
            reg_id(im.get('id'), 'image', 'chapters/%s image' % cname)

    # ---- chapter-level refs and ages
    chapter_age = {}
    for cname, ch in chapters.items():
        g = ch.get('group', '')
        if g and g.upper() not in group_ids:
            E('chapters/%s: group %s is not in chapter_groups.snbt' % (cname, g))
        if ch.get('filename') != cname:
            E('chapters/%s: filename field %r differs from the file name' % (cname, ch.get('filename')))
        rs = ch.get('progressivestages_required_stage')
        if rs is not None and rs not in stage_ids:
            E('chapters/%s: required stage %r has no stage file' % (cname, rs))
        chapter_age[cname] = rs if rs in AGES else 'dawn'

    def check_item(iid, where, max_age, what):
        if not isinstance(iid, str):
            E('%s: %s has no item id' % (where, what))
            return
        if iid in PENDING_MOD_IDS and iid not in items:
            pending_used.add(iid)
            return
        if iid not in items:
            E('%s: %s item %s is not in dev/data/registry.json' % (where, what, iid))
            return
        a = item_age.get(iid)
        if a is None:
            W('%s: %s item %s has no Age tag' % (where, what, iid))
            return
        if a == 'disabled' and what in ('icon', 'chapter icon') and iid in derived_hidden:
            pass  # never reachable (dev/derive_ages.py): hidden from EMI but not ProgressiveStages-locked, so it renders
        elif a == 'disabled':
            E('%s: %s item %s is disabled in this pack' % (where, what, iid))
        elif max_age is not None and age_index(a) is not None and age_index(a) > age_index(max_age):
            E('%s: %s item %s belongs to %s, later than %s' % (where, what, iid, a, max_age))

    # ---- shrine offerings: the file firmages-core reads (SPEC section 2.6). The mod jar ships
    # data/firmages/firmages_shrine/offerings.json; a pack copy at the same path in kubejs/data replaces it.
    pending_used = set()
    offerings = {}
    ring_tiers = {}  # ring N -> stage it grants
    quest_grants = {}  # stage -> [where] for every gamestage quest reward
    src = OFFERINGS_PACK if os.path.isfile(OFFERINGS_PACK) else OFFERINGS_MOD
    rel = os.path.relpath(src, ROOT).replace(os.sep, '/')
    stray = glob.glob(os.path.join(ROOT, 'kubejs', 'data', '*', 'shrine', 'offerings.json'))
    for f in stray:
        E('%s: firmages-core does not read this path; offerings live in data/<ns>/firmages_shrine/offerings.json'
          % os.path.relpath(f, ROOT).replace(os.sep, '/'))
    if os.path.isfile(src):
        raw = json.load(open(src, encoding='utf-8'))
        for age, iid in raw.items():
            where = '%s %s' % (rel, age)
            if not re.fullmatch(r'age_[0-8]', age):
                E('%s: key is not age_0..age_8 (the mod rejects the whole file)' % where)
                continue
            if not isinstance(iid, str):
                E('%s: value must be an item id string (the mod rejects the whole file)' % where)
                continue
            if iid not in items and iid not in PENDING_MOD_IDS:
                W('%s: %s is not registered yet, that ring cannot take its offering' % (where, iid))
                continue
            check_item(iid, where, age, 'offering')
            if item_age.get(iid) not in (None, age):
                E('%s: offering %s belongs to %s, not to the Age of its ring' % (where, iid, item_age.get(iid)))
            offerings[age] = iid
        for tf in sorted(glob.glob(os.path.join(SHRINE_TIERS, 'ring_*.json'))):
            n = int(re.search(r'ring_(\d+)', tf).group(1))
            tier = json.load(open(tf, encoding='utf-8'))
            if tier.get('grants', 'age_%d' % (n + 1)) != 'age_%d' % (n + 1):
                E('tier ring_%d: grants %r, expected age_%d' % (n, tier.get('grants'), n + 1))
            ring_tiers[n] = tier.get('grants', 'age_%d' % (n + 1))
    else:
        W('%s is missing: every goal is checked as a quest grant' % rel)

    table_users = {}
    interim = []
    counts = {}
    for cname, ch in chapters.items():
        cage = chapter_age[cname]
        is_age_chapter = ch.get('progressivestages_required_stage') in AGE_CHAPTER_STAGES and \
            'works' not in ch.get('tags', [])
        if 'icon' in ch:
            check_item(ch['icon'].get('id'), 'chapters/%s' % cname, None, 'chapter icon')
        kinds = {}
        for qd in ch.get('quests', []):
            where = 'chapters/%s quest %s' % (cname, qd.get('id'))
            tags = qd.get('tags', [])
            for k in tags:
                kinds.setdefault(k, []).append(qd)
            if 'icon' in qd:
                check_item(qd['icon'].get('id'), where, None, 'icon')
            for dep in qd.get('dependencies', []):
                if str(dep).upper() not in quests and str(dep).upper() not in {c['id'].upper() for c in chapters.values()}:
                    E('%s: dependency %s does not resolve' % (where, dep))
            if not qd.get('tasks'):
                E('%s: quest has no task' % where)
            for t in qd.get('tasks', []):
                typ = t.get('type')
                tw = '%s task %s' % (where, t.get('id'))
                if typ == 'item':
                    check_item(t.get('item', {}).get('id'), tw, cage, 'task')
                elif typ == 'gamestage':
                    if t.get('stage') not in stage_ids:
                        E('%s: stage %r has no stage file' % (tw, t.get('stage')))
                    if t.get('team_stage'):
                        W('%s: team_stage uses FTB Teams storage unless ProgressiveStages redirects it' % tw)
                elif typ == 'advancement':
                    adv = strval(t.get('advancement'))
                    if not adv or ':' not in adv:
                        E('%s: bad advancement id %r' % (tw, adv))
                    elif jars and not jars.has('advancement', adv):
                        E('%s: advancement %s not found in the mod jars' % (tw, adv))
                elif typ == 'kill':
                    tag = t.get('entityTypeTag')
                    if tag:
                        if jars and not jars.has('entity_tag', tag):
                            E('%s: entity type tag %s not found in the mod jars' % (tw, tag))
                    elif jars and t.get('entity') not in jars.entities and t.get('entity') not in VANILLA_ENTITIES:
                        E('%s: entity %s not found in the mod jars' % (tw, t.get('entity')))
                elif typ == 'observation':
                    ot, target = t.get('observation_type'), strval(t.get('to_observe'))
                    bid = (target or '').split('[')[0]
                    if ot in ('block', 'block_state') and bid in PENDING_MOD_IDS and bid not in blocks:
                        pending_used.add(bid)
                    elif ot in ('block', 'block_state') and bid not in blocks:
                        E('%s: observed block %s is not in the registry' % (tw, bid))
                    elif ot == 'block_tag' and jars and not jars.has('block_tag', target):
                        E('%s: block tag %s not found in the mod jars' % (tw, target))
                    elif ot == 'entity_type' and jars and target not in jars.entities:
                        E('%s: observed entity %s not found in the mod jars' % (tw, target))
                    elif ot not in ('block', 'block_tag', 'entity_type', 'entity_type_tag', 'block_state',
                                    'block_entity', 'block_entity_type'):
                        E('%s: unknown observation_type %r' % (tw, ot))
                elif typ == 'dimension':
                    dim = strval(t.get('dimension'))
                    if jars and dim not in VANILLA_DIMS and not jars.has('dimension', dim):
                        E('%s: dimension %s not found in the mod jars' % (tw, dim))
                    if dim in PORTAL_TAGS:
                        ptag, default = PORTAL_TAGS[dim]
                        removed, added = portal_tags.get(ptag, (False, []))
                        if not removed or not added:
                            E('%s: portal tag %s is not overridden in kubejs/server_scripts/tags/, the jar default %s '
                              'applies' % (tw, ptag, default))
                        for iid in added:
                            if iid.startswith('#'):
                                E('%s: portal tag %s adds the tag %s; list items so their Age can be checked' % (tw, ptag, iid))
                            else:
                                check_item(iid, tw, cage, 'portal activator (%s)' % ptag)
                elif typ in ('checkmark', 'stat', 'xp', 'location', 'biome', 'structure', 'fluid', 'custom'):
                    pass
                else:
                    E('%s: unknown task type %r' % (tw, typ))
            # an "any wood" task covers every live wood of its block (review 0.13.0: the Gatehouse took only vanilla woods)
            titles = ' '.join(str(lang.get('task.%s.title' % t.get('id'), '')) for t in qd.get('tasks', []))
            if re.search(r'(any|one) wood', titles, re.I):
                check_any_wood(qd, where, cage, item_age, E)
            for r in qd.get('rewards', []):
                typ = r.get('type', 'item')
                rw = '%s reward %s' % (where, r.get('id'))
                if typ == 'item':
                    check_item(r.get('item', {}).get('id'), rw, cage, 'reward')
                elif typ in ('random', 'choice', 'loot', 'all_table'):
                    tid = r.get('table_id')
                    if not isinstance(tid, Num) or tid.kind != 'l':
                        E('%s: table_id must be a long (…L), got %r' % (rw, tid))
                    elif tid.value not in table_ids:
                        E('%s: reward table %s does not exist' % (rw, tid))
                    else:
                        table_users.setdefault(table_ids[tid.value], []).append((cname, cage))
                elif typ == 'gamestage':
                    st = r.get('stage')
                    if st not in stage_ids:
                        E('%s: stage %r has no stage file' % (rw, st))
                    quest_grants.setdefault(st, []).append(rw)
                    if r.get('auto') != 'invisible':
                        E('%s: stage reward must auto-claim invisibly (auto: "invisible")' % rw)
                    if 'interim_shrine' in r.get('tags', []):
                        interim.append((cname, qd.get('id'), st))
                        if ch.get('progressivestages_required_stage') in offerings:
                            E('%s: interim stage reward left on a shrine Age, the shrine grants %s' % (rw, st))
                elif typ in ('xp', 'xp_levels', 'toast', 'command', 'advancement'):
                    if typ == 'command':
                        W('%s: command reward, check by hand' % rw)
                else:
                    E('%s: unknown reward type %r' % (rw, typ))

        # ---- Age chapter structure (Doc 08 section 9.1)
        n_q = len(ch.get('quests', []))
        counts[cname] = {k: len(v) for k, v in kinds.items()}
        counts[cname]['total'] = n_q
        if is_age_chapter:
            stage = ch['progressivestages_required_stage']
            entries, goals = kinds.get('entry', []), kinds.get('goal', [])
            keys, sides = kinds.get('keystone', []), kinds.get('side', [])
            if len(entries) != 1:
                E('chapters/%s: %d entry quests, expected 1' % (cname, len(entries)))
            elif not any(t.get('type') == 'gamestage' and t.get('stage') == stage for t in entries[0]['tasks']):
                E('chapters/%s: entry quest has no gamestage task for %s' % (cname, stage))
            if len(goals) != 1:
                E('chapters/%s: %d goal quests, expected exactly 1' % (cname, len(goals)))
            # 3-5 strands: from the Iron Age a Sky strand teaches the celestial alloys (V4A, review fix 0.12.0); up to
            # six side missions: the Proof of Work quests of the Industrial to the Quantum Age join them
            if not 3 <= len(keys) <= 5:
                E('chapters/%s: %d keystones (required strands), expected 3-5' % (cname, len(keys)))
            if not 2 <= len(sides) <= 6:
                E('chapters/%s: %d side missions, expected 2-6' % (cname, len(sides)))
            rings = kinds.get('ring', [])
            for s in sides + kinds.get('explain', []) + rings:
                if not s.get('optional'):
                    E('chapters/%s: side/explanation/ring quest %s is not optional' % (cname, s['id']))
            for qd in ch['quests']:
                tags = qd.get('tags', [])
                if not OPTIONAL_TAGS & set(tags) and qd.get('optional'):
                    E('chapters/%s: required quest %s is marked optional' % (cname, qd['id']))
            # "Raise the ring": only where the Age has its own ring, one block_state observation of the heart
            n_age = int(stage[4:]) if stage.startswith('age_') else None
            if len(rings) > 1:
                E('chapters/%s: %d ring quests, expected at most 1' % (cname, len(rings)))
            for rq in rings:
                rqw = 'chapters/%s ring quest %s' % (cname, rq['id'])
                if n_age is None or n_age not in ring_tiers:
                    E('%s: %s has no shrine ring (no tier ring_N file)' % (rqw, stage))
                ts = rq.get('tasks', [])
                target = strval(ts[0].get('to_observe')) if len(ts) == 1 else None
                if len(ts) != 1 or ts[0].get('type') != 'observation' or ts[0].get('observation_type') != 'block_state':
                    E('%s: needs exactly one block_state observation task' % rqw)
                elif not target or not target.startswith('firmages:shrine_heart['):
                    E('%s: observes %r, expected firmages:shrine_heart[...]' % (rqw, target))
                else:
                    props = dict(kv.split('=', 1) for kv in target[target.index('[') + 1:-1].split(',') if '=' in kv)
                    if props.get('ready') != 'true' or props.get('awakened') != str(n_age):
                        E('%s: %s must set ready=true and awakened=%s' % (rqw, target, n_age))
            if len(goals) == 1:
                g = goals[0]
                gdeps = {d.upper() for d in g.get('dependencies', [])}
                if g.get('dependency_requirement') == 'one_completed':
                    # routes (the Arcane Age): the goal waits for any one route; every keystone lies on some route
                    by_id = {qd['id'].upper(): qd for qd in ch.get('quests', [])}
                    gdeps = {d.upper() for r in gdeps if r in by_id for d in by_id[r].get('dependencies', [])}
                missing = [k['id'] for k in keys if k['id'].upper() not in gdeps]
                if missing:
                    E('chapters/%s: goal does not depend on keystone(s) %s' % (cname, ', '.join(missing)))
                grants = [r.get('stage') for r in g.get('rewards', []) if r.get('type') == 'gamestage']
                nxt = NEXT_STAGE[stage]
                waits = [t.get('stage') for t in g.get('tasks', []) if t.get('type') == 'gamestage']
                # Dawn: the goal grants age_0. From the Stone Age the shrine grants the next Age: the goal waits for
                # it with a gamestage task and must not grant it too (one grant path, firmages-core 0.3.0).
                if stage in offerings:
                    if grants:
                        E('chapters/%s: goal of a shrine Age grants %r; the shrine grants %s, drop the reward'
                          % (cname, grants, nxt))
                    if waits != [nxt]:
                        E('chapters/%s: goal of a shrine Age must wait for %s with one gamestage task; waits for %r'
                          % (cname, nxt, waits))
                    if g.get('icon', {}).get('id') != offerings[stage]:
                        E('chapters/%s: goal icon %r is not the offering %s' % (cname, g.get('icon', {}).get('id'),
                                                                                offerings[stage]))
                elif nxt == 'finale_won':
                    # Singularity Age: the end boss script grants finale_won; the goal only waits for it
                    if grants:
                        E('chapters/%s: goal grants %r; finale_won comes from the end boss, drop the reward'
                          % (cname, grants))
                    if waits != [nxt]:
                        E('chapters/%s: goal must wait for finale_won with one gamestage task; waits for %r'
                          % (cname, waits))
                elif grants != [nxt]:
                    E('chapters/%s: goal grants %r, expected exactly [%r]' % (cname, grants, nxt))
            # every required quest must lead to the goal (no dead-end required quests)
            dependants = {}
            for qd in ch['quests']:
                for d in qd.get('dependencies', []):
                    dependants.setdefault(d.upper(), set()).add(qd['id'].upper())
            if len(goals) == 1:
                reach = set()
                stack = [goals[0]['id'].upper()]
                byid = {qd['id'].upper(): qd for qd in ch['quests']}
                while stack:
                    cur = stack.pop()
                    if cur in reach:
                        continue
                    reach.add(cur)
                    stack += [d.upper() for d in byid.get(cur, {}).get('dependencies', [])]
                for qd in ch['quests']:
                    if not OPTIONAL_TAGS & set(qd.get('tags', [])) and qd['id'].upper() not in reach:
                        E('chapters/%s: required quest %s does not lead to the goal' % (cname, qd['id']))

    # ---- pictures: chapter images and {image:...} description lines
    def texture_exists(rl):
        if ':' not in rl or rl.startswith(('item:', 'http', 'file:')):
            return True
        ns, path = rl.split(':', 1)
        if ns not in ('firmages', 'ftbquests', 'certain_questing_additions'):
            return True   # a mod's own texture: not checked here
        roots = [os.path.join(ROOT, 'kubejs', 'assets'),
                 os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'resources', 'assets')]
        return any(os.path.isfile(os.path.join(r, ns, path)) for r in roots)

    def check_click(action, where):
        if not action:
            return
        kind, _sep, arg = action.partition(':')
        if kind == 'open_quest':
            if arg.split('/')[0].upper() not in quests:
                E('%s: click opens quest %s, which is not in the book' % (where, arg))
        elif kind == 'custom_event':
            m = re.fullmatch(r'firmages:ponder/([a-z0-9_.-]+)/([a-z0-9_./-]+)', arg)
            if not m:
                E('%s: unknown custom event %r' % (where, arg))
            elif '%s:%s' % m.groups() not in items:
                E('%s: Ponder click for %s:%s, not a registered item' % ((where,) + m.groups()))
        elif kind not in ('open_uri', 'run_command', 'show_recipe', 'none'):
            E('%s: unknown click action %r' % (where, action))

    for cname, ch in chapters.items():
        for im in ch.get('images', []):
            where = 'chapters/%s image %s' % (cname, im.get('id'))
            if not texture_exists(strval(im.get('image')) or ''):
                E('%s: texture %s does not exist' % (where, im.get('image')))
            check_click(strval(im.get('click_action')), where)
            dep = im.get('dependency')
            if dep and str(dep).upper() not in quests:
                E('%s: dependency %s is not a quest of the book' % (where, dep))
    for key, val in lang.items():
        if not key.endswith('.quest_desc') or not isinstance(val, list):
            continue
        for line in val:
            m = re.fullmatch(r'\{image:(\S+)([^}]*)\}', line.strip()) if isinstance(line, str) else None
            if not m:
                continue
            props = dict(p_.split(':', 1) for p_ in m.group(2).split() if ':' in p_)
            if not texture_exists(m.group(1)):
                E('lang: %s: picture %s does not exist' % (key, m.group(1)))
            for k_ in ('width', 'height'):
                if not props.get(k_, '').isdigit():
                    E('lang: %s: picture %s needs a numeric %s' % (key, m.group(1), k_))
            check_click(props.get('click_action'), 'lang: %s' % key)

    # ---- layout: drawn quests never touch; curves only for real dependencies
    for cname, ch in chapters.items():
        dsize = ch.get('default_quest_size')
        dsize = dsize.value if isinstance(dsize, Num) else 1.0
        placed = []
        for qd in ch.get('quests', []):
            size = qd.get('size').value if isinstance(qd.get('size'), Num) else 0.0
            x, y = qd.get('x'), qd.get('y')
            if not isinstance(x, Num) or not isinstance(y, Num):
                E('chapters/%s quest %s: no position' % (cname, qd.get('id')))
                continue
            placed.append((qd['id'], x.value, y.value, size or dsize or 1.0))
            deps = {str(d).upper() for d in qd.get('dependencies', [])}
            for dep, pts in (qd.get('dep_control_pts') or {}).items():
                if dep.upper() not in deps:
                    E('chapters/%s quest %s: dep_control_pts for %s, which is not a dependency' % (cname, qd['id'], dep))
                if not isinstance(pts, list) or len(pts) != 4 or \
                        not all(isinstance(v, Num) and v.kind == 'd' for v in pts):
                    E('chapters/%s quest %s: dep_control_pts %s needs four doubles' % (cname, qd['id'], dep))
        for i in range(len(placed)):
            for j in range(i + 1, len(placed)):
                a, b = placed[i], placed[j]
                gap = ((a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5 - (a[3] + b[3]) * quest_layout.CELL / 2
                if gap < quest_layout.MIN_GAP:
                    E('chapters/%s: quests %s and %s are drawn %.2f units apart (at least %.2f)'
                      % (cname, a[0], b[0], gap, quest_layout.MIN_GAP))

    # ---- Great Works chapters: a stage, one entry, one goal, every quest leads to the goal
    for cname, ch in chapters.items():
        if 'works' not in ch.get('tags', []):
            continue
        if ch.get('progressivestages_required_stage') not in AGE_CHAPTER_STAGES:
            E('chapters/%s: a Great Work needs the required stage of its Age' % cname)
        entries = [qd for qd in ch['quests'] if 'entry' in qd.get('tags', [])]
        goals = [qd for qd in ch['quests'] if 'goal' in qd.get('tags', [])]
        if len(entries) != 1 or len(goals) != 1:
            E('chapters/%s: %d entries and %d goals, expected one each' % (cname, len(entries), len(goals)))
            continue
        byid = {qd['id'].upper(): qd for qd in ch['quests']}
        reach, stack = set(), [goals[0]['id'].upper()]
        while stack:
            cur = stack.pop()
            if cur not in reach:
                reach.add(cur)
                stack += [d.upper() for d in byid.get(cur, {}).get('dependencies', [])]
        for qd in ch['quests']:
            if qd['id'].upper() not in reach:
                E('chapters/%s: quest %s does not lead to the goal' % (cname, qd['id']))

    # ---- dependency cycles
    state = {}

    def visit(qid_, path):
        st = state.get(qid_)
        if st == 1:
            E('dependency cycle: %s' % ' -> '.join(path + [qid_]))
            return
        if st == 2:
            return
        state[qid_] = 1
        for d in quests.get(qid_, (None, {}))[1].get('dependencies', []):
            visit(d.upper(), path + [qid_])
        state[qid_] = 2

    for k in quests:
        visit(k, [])

    # ---- one grant path per Age (book-wide)
    for st in AGES[1:] + ['finale_won']:
        by_quest = quest_grants.get(st, [])
        by_shrine = [n for n, g in ring_tiers.items() if g == st]
        if st == 'age_0':
            if len(by_quest) != 1:
                E('grants: age_0 must come from exactly one quest reward (The First Spark), found %d' % len(by_quest))
        elif st == 'finale_won':
            if by_quest:
                E('grants: finale_won is granted by quest reward(s) %s; the end boss script grants it'
                  % ', '.join(by_quest))
            elif not finale_script_grant():
                W('grants: neither firmages-core nor a KubeJS script grants finale_won yet (end boss, Doc 08 section 10.4); the Singularity '
                  'goal waits for it')
        else:
            if len(by_shrine) != 1:
                E('grants: %s must come from exactly one shrine tier, found %d' % (st, len(by_shrine)))
            if by_quest:
                E('grants: %s is also granted by quest reward(s) %s (second grant path)' % (st, ', '.join(by_quest)))
    if quest_grants.get('dawn'):
        E('grants: dawn is the start stage, no quest may grant it')

    # ---- reward tables: items no later than the Age of every chapter using them; one table, one Age
    for name, t in tables.items():
        users = table_users.get(name, [])
        if not users:
            W('reward_tables/%s: not used by any quest' % name)
        declared = [tg[4:] for tg in t.get('tags', []) if isinstance(tg, str) and tg.startswith('age:')]
        if len(declared) != 1 or declared[0] not in AGES:
            E('reward_tables/%s: needs exactly one tag age:<stage>, has %r' % (name, t.get('tags')))
        else:
            for cname_, cage_ in users:
                if cage_ != declared[0]:
                    E('reward_tables/%s: table of %s used by chapters/%s (%s)' % (name, declared[0], cname_, cage_))
        max_age = min((u[1] for u in users), key=age_index) if users else 'dawn'
        for r in t.get('rewards', []):
            typ = r.get('type', 'item')
            if typ != 'item':
                E('reward_tables/%s: reward type %s in a loot table, only items allowed' % (name, typ))
                continue
            check_item(r.get('item', {}).get('id'), 'reward_tables/%s' % name, max_age, 'table')

    # ---- lang
    for key, val in lang.items():
        m = re.fullmatch(r'(file|chapter|chapter_group|quest|task|reward|reward_table|quest_link|image)\.'
                         r'([0-9A-Fa-f]{16})\.(title|quest_subtitle|quest_desc|chapter_subtitle)', key)
        if not m:
            E('lang: malformed key %s' % key)
            continue
        if m.group(1) != 'file' and m.group(2).upper() not in objects:
            E('lang: %s points to a missing object' % key)
        list_field = m.group(3) in ('quest_desc', 'chapter_subtitle')
        if list_field != isinstance(val, list):
            E('lang: %s must be a %s' % (key, 'list of strings' if list_field else 'string'))
        texts = val if isinstance(val, list) else [val]
        for tx in texts:
            if not isinstance(tx, str):
                E('lang: %s has a non-string value' % key)
            elif re.search('[äöüÄÖÜß]', tx):
                E('lang: %s contains German letters: %r' % (key, tx))
            elif '{image:' in tx:
                check_image_line(tx, 'lang: %s' % key, E, items)
    for oid, (typ, where) in objects.items():
        if typ in ('chapter', 'quest', 'chapter_group', 'reward_table') and \
                '%s.%s.title' % (typ, oid) not in lang:
            E('lang: %s %s (%s) has no title' % (typ, oid, where))
    if args.style:
        style_lint(lang, chapters, W)

    # ---- data.snbt
    if data.get('drop_book_on_death') is not False:
        E('data.snbt: drop_book_on_death must be false')
    if data.get('drop_loot_crates') is not False:
        E('data.snbt: drop_loot_crates must be false')
    if data.get('emergency_items'):
        E('data.snbt: emergency_items must be empty')

    # ---- report
    for cname in chapters:
        c = counts[cname]
        print('%-16s %3d quests  (entry %d, keystones %d, goal %d, side %d, explanations %d, ring %d)' % (
            cname, c['total'], c.get('entry', 0), c.get('keystone', 0), c.get('goal', 0), c.get('side', 0),
            c.get('explain', 0), c.get('ring', 0)))
    for cname, qid_, st in interim:
        print('interim stage reward: chapters/%s quest %s grants %s (shrine will grant this)' % (cname, qid_, st))
    print('shrine offerings: %s' % ', '.join('%s %s' % kv for kv in sorted(offerings.items())))
    for iid in sorted(pending_used):
        W('%s is not in dev/data/registry.json yet (firmages-core 0.3.0, PENDING_MOD_IDS)' % iid)
    print('jar lookups: %s' % ('on (%s)' % mods_dir if jars else 'off'))
    for w in warnings:
        print('WARN  ' + w)
    for e in errors:
        print('ERROR ' + e)
    print('%d object ids, %d errors, %d warnings' % (len(objects), len(errors), len(warnings)))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
