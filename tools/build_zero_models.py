"""Zero's original single-aperture geometry; never regenerates the crystal gun or armor."""
import base64
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATERIALS = ['metal', 'ceramic', 'gold', 'glow']
TEXTURES = {key: 'tnc:item/mech_' + key for key in MATERIALS}


def box(x, y, z, w, h, d, material, glow=False):
    return {'from': [x, y, z], 'to': [x+w, y+h, z+d],
            'faces': {face: {'texture': '#'+material, 'uv': [0, 0, 16, 16],
                             **({'tintindex': 0} if glow else {})}
                      for face in ['north', 'south', 'east', 'west', 'up', 'down']}}


def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', 'utf8')


parts = {
    'body': [box(4.5, 7, 0, 7, 6, 13, 'metal'), box(4, 8, 2, 1, 4, 9, 'ceramic'),
             box(11, 8, 2, 1, 4, 9, 'ceramic'), box(5, 12.6, 1, 6, .8, 10, 'gold'),
             box(5, 6.7, 1, 6, .8, 11, 'gold'), box(5.5, 8, 13, 5, 4, 6, 'ceramic'),
             box(4.8, 7.5, 17, 6.4, 5, 1.2, 'metal'), box(6.5, 2, 9, 3, 5, 3.5, 'metal'),
             box(6, 1.5, 8.5, 4, 1, 4.5, 'gold'), box(6.2, 4, -1, 3.6, 3, 4, 'ceramic'),
             box(4.5, 7, -3, 7, 1.5, 3, 'ceramic'), box(4.5, 11.5, -3, 7, 1.5, 3, 'ceramic')],
    # Two separated forks make an open aperture rather than six conventional barrels.
    'rail': [box(4.8, 8.5, -13, 1.3, 3, 13, 'ceramic'), box(9.9, 8.5, -13, 1.3, 3, 13, 'ceramic'),
             box(4.5, 8.2, -14, 2, 3.6, 1.3, 'gold'), box(9.5, 8.2, -14, 2, 3.6, 1.3, 'gold'),
             box(5.3, 12.8, -3, 1, 1.2, 12, 'metal'), box(9.7, 12.8, -3, 1, 1.2, 12, 'metal'),
             box(7.1, 13, 7, 1.8, 1.7, 1.5, 'gold'), box(7.5, 13.5, 6.8, 1, 1, .3, 'glow', True)],
    # The renderer orbits this one asymmetric vane five times around the barrel axis.
    'fin': [box(6.4, 14.1, -9.5, 3.2, 1.2, 7, 'ceramic'), box(7, 15.3, -8, 2, .8, 4, 'metal'),
            box(6.1, 13.6, -10, 3.8, .6, 1.1, 'gold'), box(7, 12.9, -3.4, 2, 1.3, 1, 'gold')],
    'glow': [box(7.4, 15.95, -7.6, 1.2, .18, 3, 'glow', True),
             box(6.8, 13.5, -10.2, 2.4, .5, .25, 'glow', True)],
    'core': [box(7.1, 8.7, -5.2, 1.8, 2.6, 1.8, 'glow', True),
             box(7.45, 8, -4.85, 1.1, 4, 1.1, 'glow', True),
             box(5, 9.65, 3, .2, .7, 7, 'glow', True),
             box(10.8, 9.65, 3, .2, .7, 7, 'glow', True)]
}
display = {
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.72]*3},
    'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.72]*3},
    'firstperson_righthand': {'rotation': [0, 7, 0], 'translation': [2, -1.7, -3.3], 'scale': [.72]*3},
    'firstperson_lefthand': {'rotation': [0, -7, 0], 'translation': [2, -1.7, -3.3], 'scale': [.72]*3},
    'gui': {'rotation': [22, 225, 0], 'translation': [0, .5, 0], 'scale': [.5]*3},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.35]*3},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [.55]*3}
}
for name, elements in parts.items():
    write('src/main/resources/assets/tnc/models/item/zero_spellgun_'+name+'.json',
          {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': elements, 'gui_light': 'front'})
write('src/main/resources/assets/tnc/models/item/zero_spellgun.json', {'parent': 'builtin/entity', 'display': display})

# A complete editable Blockbench model, including all five vanes, using the same geometry.
elements, groups = [], []
for name, boxes in parts.items():
    for instance in range(5 if name in ['fin', 'glow'] else 1):
        children = []
        for index, cube in enumerate(boxes):
            ident = str(uuid.uuid5(uuid.NAMESPACE_URL, f'tnc:zero/{name}/{instance}/{index}'))
            faces = {key: {**face, 'texture': MATERIALS.index(face['texture'][1:])} for key, face in cube['faces'].items()}
            elements.append({'name': f'{name}_{instance}_{index}', 'uuid': ident,
                             'from': cube['from'], 'to': cube['to'], 'origin': [8, 10, -6],
                             'rotation': [0, 0, instance*72 if name in ['fin', 'glow'] else 0],
                             'faces': faces, 'type': 'cube', 'rescale': False})
            children.append(ident)
        groups.append({'name': name+str(instance), 'origin': [8, 10, -6],
                       'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, f'tnc:zero/{name}/{instance}')), 'children': children})
textures = []
for material in MATERIALS:
    path = ROOT / ('src/main/resources/assets/tnc/textures/item/mech_'+material+'.png')
    textures.append({'name': path.name, 'id': str(len(textures)), 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, str(path.name))),
                     'source': 'data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode(), 'width': 16, 'height': 16})
write('model-source/zero_spellgun/zero.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
      'name': '零式 · 五相术式机关枪', 'resolution': {'width': 16, 'height': 16}, 'elements': elements,
      'outliner': groups, 'textures': textures, 'display': display})
print(f'Zero: {sum(map(len,parts.values()))} source cubes; {len(elements)} assembled cubes; editable Blockbench source exported')
