#!/usr/bin/env python3
"""Firmament Ages - offline check of the FTB Quests book in config/ftbquests/quests/.

Parses the SNBT subset FTB Quests 2101.1.36 writes (and the comment lines its reader skips) and checks:
  ids        every object id is 16 hex digits, a positive signed long other than 0/1, and unique
  refs       quest dependencies, chapter groups and reward-table ids resolve; no dependency cycle
  items      every item id (tasks, rewards, reward tables, icons) is in dev/data/registry.json
  stages     every stage id (gamestage tasks/rewards, progressivestages_required_stage) has a stage file in
             config/progressivestages/stages/
  ages       no reward item and no task item belongs to a later Age than its chapter (age_items tags in
             kubejs/data/firmages/tags/item/age_items/); disabled items are never allowed
  structure  every Age chapter: required stage set, one entry quest (gamestage task = chapter stage), one goal
             quest that depends on every keystone, 3-4 keystones, 2-4 optional side quests, explanations
             optional. Goal of a shrine Age (an entry in the shrine offerings, see below): one gamestage
             task on the next Age, the offering as icon, no stage reward (the shrine grants it). Goal of any other
             Age (Dawn): its reward grants exactly the next Age stage, with auto "invisible"
  shrine     the offerings firmages-core reads (data/firmages/firmages_shrine/offerings.json from the mod
             sources, or its kubejs/data override): flat {"age_N": item}, the item in the Age tag of its key
             (not yet registered: warning, that ring takes no offering); each ring_N tier grants age_(N+1)
  text       every chapter and quest has an English title in lang/en_us.snbt, no lang key points to a
             missing object, no German letters in player text
  data       data.snbt: book never dropped on death, no loot crates, no emergency items
  portals    a dimension task needs its entry item: the KubeJS item tag that opens the portal (PORTAL_TAGS, read
             from kubejs/server_scripts/tags/*.js) is overridden and holds only items of the chapter's Age or earlier
With --mods <dir> (default test-server/mods if it exists) it also looks inside the mod jars for advancement,
entity type, entity/block tag and dimension ids used by tasks. Exit code 1 on any error.
"""
import argparse
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QDIR = os.path.join(ROOT, 'config', 'ftbquests', 'quests')
AGES = ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9']
AGE_CHAPTER_STAGES = {'dawn', 'age_0', 'age_1', 'age_2'}  # chapters authored so far (rule set applies to these)
# Dimension -> (item tag that activates its portal, the jar default). The default stays in force unless a KubeJS tag
# script calls removeAll on the tag.
PORTAL_TAGS = {'twilightforest:twilight_forest': ('twilightforest:portal/activator', '#c:gems/diamond (age_4)')}
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
        return 'data/%s/%s/%s.json' % (ns, folder, path) in self.files


VANILLA_DIMS = {'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'}


# ------------------------------------------------------------------------------------------------ checks
def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--mods', help='mods folder with the pack jars (default: test-server/mods if present)')
    ap.add_argument('--no-jars', action='store_true', help='skip the jar lookups')
    ap.add_argument('--dir', default=QDIR, help='quest folder to check (default: config/ftbquests/quests)')
    args = ap.parse_args()
    qdir = args.dir

    errors, warnings = [], []
    E = errors.append
    W = warnings.append

    registry = json.load(open(os.path.join(ROOT, 'dev', 'data', 'registry.json'), encoding='utf-8'))
    items = set(registry['items'])
    blocks = set(registry.get('blocks', [])) | items  # block items cover placeable blocks
    item_age = load_age_tags()
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
        if a == 'disabled':
            E('%s: %s item %s is disabled in this pack' % (where, what, iid))
        elif max_age is not None and age_index(a) is not None and age_index(a) > age_index(max_age):
            E('%s: %s item %s belongs to %s, later than %s' % (where, what, iid, a, max_age))

    # ---- shrine offerings: the file firmages-core reads (SPEC section 2.6). The mod jar ships
    # data/firmages/firmages_shrine/offerings.json; a pack copy at the same path in kubejs/data replaces it.
    pending_used = set()
    offerings = {}
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
    else:
        W('%s is missing: every goal is checked as a quest grant' % rel)

    table_users = {}
    interim = []
    counts = {}
    for cname, ch in chapters.items():
        cage = chapter_age[cname]
        is_age_chapter = ch.get('progressivestages_required_stage') in AGE_CHAPTER_STAGES
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
                    elif jars and t.get('entity') not in jars.entities:
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
            if not 3 <= len(keys) <= 4:
                E('chapters/%s: %d keystones (required strands), expected 3-4' % (cname, len(keys)))
            if not 2 <= len(sides) <= 4:
                E('chapters/%s: %d side missions, expected 2-4' % (cname, len(sides)))
            for s in sides + kinds.get('explain', []):
                if not s.get('optional'):
                    E('chapters/%s: side/explanation quest %s is not optional' % (cname, s['id']))
            for qd in ch['quests']:
                tags = qd.get('tags', [])
                if not {'side', 'explain'} & set(tags) and qd.get('optional'):
                    E('chapters/%s: required quest %s is marked optional' % (cname, qd['id']))
            if len(goals) == 1:
                g = goals[0]
                gdeps = {d.upper() for d in g.get('dependencies', [])}
                missing = [k['id'] for k in keys if k['id'].upper() not in gdeps]
                if missing:
                    E('chapters/%s: goal does not depend on keystone(s) %s' % (cname, ', '.join(missing)))
                grants = [r.get('stage') for r in g.get('rewards', []) if r.get('type') == 'gamestage']
                nxt = AGES[AGES.index(stage) + 1]
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
                    if not {'side', 'explain'} & set(qd.get('tags', [])) and qd['id'].upper() not in reach:
                        E('chapters/%s: required quest %s does not lead to the goal' % (cname, qd['id']))

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

    # ---- reward tables: items no later than the Age of every chapter using them
    for name, t in tables.items():
        users = table_users.get(name, [])
        if not users:
            W('reward_tables/%s: not used by any quest' % name)
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
    for oid, (typ, where) in objects.items():
        if typ in ('chapter', 'quest', 'chapter_group', 'reward_table') and \
                '%s.%s.title' % (typ, oid) not in lang:
            E('lang: %s %s (%s) has no title' % (typ, oid, where))

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
        print('%-14s %3d quests  (entry %d, keystones %d, goal %d, side %d, explanations %d)' % (
            cname, c['total'], c.get('entry', 0), c.get('keystone', 0), c.get('goal', 0), c.get('side', 0),
            c.get('explain', 0)))
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
