#!/usr/bin/env python3
"""Firmament Ages - description lines for the quest pictures of dev/gen_quest_images.py.

    from quest_image_refs import image_line
    Verbatim(image_line('knapping/axe_head'))   # {image:firmages:textures/quests/knapping/axe_head.png width:64 ...}

Reads dev/data/quest_images.json (the manifest the picture generator writes). The line uses FTB Library 2101.1.37's
image component (ClientTextComponentUtils: properties split at spaces, key:value at the first colon): image, width,
height, align, fit, click_action and the hover text `text` (spaces as %20). Ponder pictures carry their
click_action custom_event:firmages:ponder/<ns>/<path> (firmages-core QuestPonderLink). A key that is not in the
manifest raises KeyError, so a typo fails when the book is generated. Conventions: dev/quest-images.md.

A full resource location ('firmages:textures/gui/quests/shrine/complete.png') names a picture another generator draws
(dev/gen_quest_pictures.py): it is shown `width` GUI px wide, the height keeps the aspect of the PNG in kubejs/assets or
the firmages-core resources. While that PNG is not drawn yet, `size` gives the aspect and the generator says so; run the
book generator again once the picture exists (dev/validate_quests.py reports a missing picture as an error).
"""
import json
import os
import struct
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(DEV, 'data', 'quest_images.json')
ASSET_ROOTS = [os.path.join(os.path.dirname(DEV), 'kubejs', 'assets'),
               os.path.join(os.path.dirname(DEV), 'mod', 'firmages-core', 'src', 'main', 'resources', 'assets')]
_CACHE = {}
PNG_SIGNATURE = bytes([0x89]) + b'PNG' + bytes([13, 10, 26, 10])


def images():
    if 'images' not in _CACHE:
        with open(MANIFEST, encoding='utf-8') as f:
            _CACHE['images'] = json.load(f)['images']
    return _CACHE['images']


def png_size(rl):
    """Pixel size of the PNG behind a resource location, or None while no asset root has it."""
    ns, path = rl.split(':', 1)
    for root in ASSET_ROOTS:
        f = os.path.join(root, ns, *path.split('/'))
        if os.path.isfile(f):
            with open(f, 'rb') as fh:
                head = fh.read(24)
            if head[:8] == PNG_SIGNATURE:
                return struct.unpack('>II', head[16:24])
    return None


def image_line(key, fit=False, align='center', hover=None, width=200, size=None):
    """One description line that shows the picture `key` ('<group>/<name>') at its display size, or the picture at the
    full resource location `key` ('<ns>:textures/....png') `width` GUI px wide (see the module notes for `size`)."""
    if ':' in key:
        px = png_size(key)
        if px is None:
            if size is None:
                raise KeyError('%s: no such picture and no size to stand in for it' % key)
            print('quest_image_refs: %s is not drawn yet; height from the stand-in size %dx%d' % ((key,) + tuple(size)),
                  file=sys.stderr)
            px = size
        e = {'path': key, 'display': (width, round(width * px[1] / float(px[0])))}
    else:
        e = images()[key]
    w, h = e['display']
    parts = ['image:' + e['path'], 'width:%d' % w, 'height:%d' % h, 'align:' + align]
    if fit:
        parts.append('fit:true')
    if e.get('click_action'):
        parts.append('click_action:' + e['click_action'])
    text = hover if hover is not None else e.get('hover')
    if text:
        parts.append('text:' + text.replace(' ', '%20'))
    return '{' + ' '.join(parts) + '}'


if __name__ == '__main__':
    for k in sorted(images()):
        print('%-26s %s' % (k, image_line(k)))
