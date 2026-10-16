#!/usr/bin/env python3
"""Firmament Ages - description lines for the quest pictures of dev/gen_quest_images.py.

    from quest_image_refs import image_line
    Verbatim(image_line('knapping/axe_head'))   # {image:firmages:textures/quests/knapping/axe_head.png width:64 ...}

Reads dev/data/quest_images.json (the manifest the picture generator writes). The line uses FTB Library 2101.1.37's
image component (ClientTextComponentUtils: properties split at spaces, key:value at the first colon): image, width,
height, align, fit, click_action and the hover text `text` (spaces as %20). Ponder pictures carry their
click_action custom_event:firmages:ponder/<ns>/<path> (firmages-core QuestPonderLink). A key that is not in the
manifest raises KeyError, so a typo fails when the book is generated. Conventions: dev/quest-images.md.
"""
import json
import os

MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'quest_images.json')
_CACHE = {}


def images():
    if 'images' not in _CACHE:
        with open(MANIFEST, encoding='utf-8') as f:
            _CACHE['images'] = json.load(f)['images']
    return _CACHE['images']


def image_line(key, fit=False, align='center', hover=None):
    """One description line that shows the picture `key` ('<group>/<name>') at its display size."""
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
