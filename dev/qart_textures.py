"""Firmament Ages - read-only texture lookup for the offline quest pictures (dev/gen_quest_pictures.py) and the map
previews (dev/preview_quests.py).

    icon(item_id)          -> RGBA PIL image of an item (flat item texture, or a small isometric cube for a block item)
    block_faces(block_id)  -> (top, side) RGBA textures of a full block, or None
    texture(texture_id)    -> RGBA texture (first frame of an animated strip), or None

Sources, in this order of precedence: the pack's own assets (kubejs/assets, firmages-core resources), then the mod
jars, then the vanilla client jar. The jars are read from the CurseForge instance of the pack unless
FIRMAGES_MODS_DIR / FIRMAGES_VANILLA_JAR point elsewhere. The jar index is cached in the system temp folder and
rebuilt when the mods folder changes. Nothing in the repository is written.

Resolution for an id ns:path: models/item/path.json (layer0, or the parent block model's textures),
textures/item/path.png, models/block/path.json (all | top/side | end/side | particle), textures/block/path.png.
"""
import io
import json
import os
import tempfile
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS = os.environ.get('FIRMAGES_MODS_DIR', r'C:\Users\Felix\curseforge\minecraft\Instances\Firmament Ages\mods')
VANILLA = os.environ.get('FIRMAGES_VANILLA_JAR', r'C:\Users\Felix\curseforge\minecraft\Install\versions\1.21.1\1.21.1.jar')
PACK_ASSETS = [os.path.join(ROOT, 'kubejs', 'assets'),
               os.path.join(ROOT, 'mod', 'firmages-core', 'src', 'main', 'resources', 'assets')]

_index = None   # asset path -> jar path, or 'DIR:<root>' for the pack's own folders
_zips = {}


def available():
    """True if the jars needed for real textures are there (pictures fall back to flat colours otherwise)."""
    return os.path.isdir(MODS) and os.path.isfile(VANILLA)


def _jar_list():
    jars = [VANILLA] if os.path.isfile(VANILLA) else []
    if os.path.isdir(MODS):
        jars += [os.path.join(MODS, f) for f in sorted(os.listdir(MODS)) if f.endswith('.jar')]
    return jars


def _cache_path(jars):
    stamp = '|'.join('%s:%d' % (os.path.basename(j), os.path.getsize(j)) for j in jars)
    import hashlib
    return os.path.join(tempfile.gettempdir(), 'firmages-texindex-%s.json' % hashlib.sha1(stamp.encode()).hexdigest()[:12])


def index():
    global _index
    if _index is not None:
        return _index
    jars = _jar_list()
    cache = _cache_path(jars)
    idx = None
    if os.path.isfile(cache):
        try:
            with open(cache, encoding='utf-8') as f:
                idx = json.load(f)
        except (OSError, ValueError):
            idx = None
    if idx is None:
        idx = {}
        for j in jars:
            try:
                with zipfile.ZipFile(j) as z:
                    for n in z.namelist():
                        if n.startswith('assets/') and n.endswith(('.png', '.json')) and \
                                ('/textures/' in n or '/models/' in n):
                            idx.setdefault(n, j)
            except (zipfile.BadZipFile, OSError):
                pass
        try:
            with open(cache, 'w', encoding='utf-8') as f:
                json.dump(idx, f)
        except OSError:
            pass
    for root in PACK_ASSETS:   # pack assets override the jars (never cached: they change while we work)
        for dp, _dn, fns in os.walk(root):
            for fn in fns:
                if fn.endswith(('.png', '.json')):
                    rel = 'assets/' + os.path.relpath(os.path.join(dp, fn), root).replace('\\', '/')
                    idx[rel] = 'DIR:' + root
    _index = idx
    return _index


def read(asset):
    src = index().get(asset)
    if src is None:
        return None
    if src.startswith('DIR:'):
        with open(os.path.join(src[4:], asset[len('assets/'):]), 'rb') as f:
            return f.read()
    z = _zips.get(src)
    if z is None:
        z = _zips[src] = zipfile.ZipFile(src)
    return z.read(asset)


def _split(rid, default_ns='minecraft'):
    if ':' in rid:
        return tuple(rid.split(':', 1))
    return default_ns, rid


def texture(tex_id):
    ns, p = _split(tex_id)
    data = read('assets/%s/textures/%s.png' % (ns, p))
    if data is None:
        return None
    im = Image.open(io.BytesIO(data)).convert('RGBA')
    if im.height > im.width:   # animated strip: first frame
        im = im.crop((0, 0, im.width, im.width))
    return im


def model(model_id):
    ns, p = _split(model_id)
    data = read('assets/%s/models/%s.json' % (ns, p))
    if data is None:
        return None
    try:
        return json.loads(data.decode('utf-8'))
    except (ValueError, UnicodeDecodeError):
        return None


def _resolve_textures(m):
    """Textures merged along the parent chain, #references resolved; also the chain's parent ids."""
    tex, chain, depth = {}, [], 0
    while m is not None and depth < 8:
        chain.append(m)
        parent = m.get('parent')
        m = model(parent) if parent and not parent.startswith('builtin') else None
        depth += 1
    for mm in reversed(chain):
        tex.update(mm.get('textures', {}))
    for _ in range(4):
        for k, v in list(tex.items()):
            if isinstance(v, str) and v.startswith('#'):
                tex[k] = tex.get(v[1:], v)
    return tex, [c.get('parent', '') for c in chain]


def block_faces(block_id):
    ns, p = _split(block_id)
    m = model('%s:block/%s' % (ns, p))
    top = side = None
    if m is not None:
        tex, _parents = _resolve_textures(m)

        def first(*keys):
            for k in keys:
                v = tex.get(k)
                if isinstance(v, str) and not v.startswith('#'):
                    t = texture(v)
                    if t is not None:
                        return t
            return None
        top = first('top', 'end', 'up', 'all', 'texture', 'pane', 'particle', 'side')
        side = first('side', 'all', 'texture', 'pane', 'north', 'front', 'particle', 'top')
    if top is None:
        top = texture('%s:block/%s' % (ns, p))
    if side is None:
        side = top
    if top is None:
        return None
    return top, side


def icon(item_id, size=32):
    from qart_iso import cube_icon
    ns, p = _split(item_id)
    m = model('%s:item/%s' % (ns, p))
    if m is not None:
        tex, parents = _resolve_textures(m)
        l0 = tex.get('layer0')
        if isinstance(l0, str) and not l0.startswith('#'):
            t = texture(l0)
            if t is not None:
                return t.resize((size, size), Image.NEAREST)
        if any('block/' in (pp or '') for pp in parents):
            faces = block_faces(item_id)
            if faces:
                return cube_icon(faces[0], faces[1], size)
    t = texture('%s:item/%s' % (ns, p))
    if t is not None:
        return t.resize((size, size), Image.NEAREST)
    faces = block_faces(item_id)
    if faces:
        return cube_icon(faces[0], faces[1], size)
    return None
