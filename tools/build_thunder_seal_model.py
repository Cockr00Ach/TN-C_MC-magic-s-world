"""Build the native, atlas-textured electrum/crystal thunder conductor mesh."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def build():
    elements = []

    def box(a, b, material, rotation=None):
        element = {'from': a, 'to': b, 'shade': False,
                   'faces': {side: {'texture': '#' + material, 'uv': [0, 0, 16, 16]}
                             for side in ['up', 'down', 'north', 'south', 'east', 'west']}}
        if rotation:
            element['rotation'] = rotation
        elements.append(element)

    def square(y, height, width, material, turn=0):
        rotation = {'origin': [4, y, 4], 'axis': 'y', 'angle': turn} if turn else None
        box([4-width/2, y, 4-width/2], [4+width/2, y+height, 4+width/2], material, rotation)

    # Stepped octagonal footing, with inset luminous seams instead of a solid gold slab.
    for y, depth, width, mat, angle in [
        (0, .55, 7.2, 'dark', 0), (.12, .5, 6.4, 'dark', 45),
        (.55, .25, 6.8, 'gold', 0), (.56, .24, 6.1, 'gold', 45),
        (.8, .7, 5.8, 'dark', 0), (1.5, .18, 5.9, 'blue', 45),
        (1.68, .35, 5.3, 'gold', 45), (2.03, .8, 4.7, 'dark', 45),
        (2.83, .25, 4.8, 'gold', 45)]:
        square(y, depth, width, mat, angle)
    square(3, 18, 2.5, 'blue', 45)
    square(5, 13, 1.65, 'gem', 0)
    # Four segmented armoured rails leave the blue crystal exposed between them.
    for axis in ['x', 'z']:
        for direction in [-1, 1]:
            for y, top, width in [(3.1, 9.8, 1.0), (10.4, 16.4, .8), (17.0, 21.1, .6)]:
                a, b = [3.5, y, 3.5], [4.5, top, 4.5]
                k = 0 if axis == 'x' else 2
                middle = 4 + direction * 1.42
                a[k], b[k] = middle-width/2, middle+width/2
                box(a, b, 'dark')
                # Thin engraved electrum inlay along the outside of the rail.
                a, b = list(a), list(b)
                a[1] += .25; b[1] -= .25
                a[k] = middle+direction*(width/2+.035)-.065
                b[k] = a[k]+.13
                box(a, b, 'gold')
    for y in [3.2, 10, 16.6, 20.8]:
        square(y, .23, 3.6, 'gold', 45)
        square(y+.23, .18, 3.25, 'dark', 45)
    # Small violet circuit jewels punctuate the pale metal; never broad purple armour.
    for y in [5.6, 12.5, 18.8]:
        for side in [-1, 1]:
            box([3.65, y, 4+side*1.85-.22], [4.35, y+.7, 4+side*1.85+.22], 'gem',
                {'origin': [4, y+.35, 4+side*1.85], 'axis': 'z', 'angle': 45})
    # Four swept crown vanes, with gold rims and narrow cyan inset channels.
    for axis in ['x', 'z']:
        for side in [-1, 1]:
            k = 0 if axis == 'x' else 2
            a, b = [3.5, 16.8, 3.5], [4.5, 24, 4.5]
            a[k], b[k] = 4+side*1.8-.42, 4+side*1.8+.42
            rot_axis = 'z' if axis == 'x' else 'x'
            angle = -side*22.5 if axis == 'x' else side*22.5
            rotation = {'origin': [4, 16.8, 4], 'axis': rot_axis, 'angle': angle}
            box(a, b, 'gold', rotation)
            a, b = list(a), list(b); a[1]+=.4; b[1]-=.3
            a[k]+=.16; b[k]-=.16
            box(a, b, 'dark', rotation)
            other = 2 if k == 0 else 0
            a[other] = 3.45; b[other] = 4.55
            a[k]+=.13; b[k]-=.13
            box(a, b, 'blue', rotation)
    # Suspended faceted central thunder crystal with a fine electrum socket.
    square(21.1, .45, 2.9, 'gold', 45)
    square(21.55, 3.3, 1.85, 'blue', 45)
    square(24.85, 1.1, 1.3, 'blue', 45)
    square(25.95, .8, .65, 'gem', 45)
    square(26.75, .5, .28, 'gold', 45)
    assert all(-16 <= value <= 32 for e in elements for key in ['from', 'to'] for value in e[key])
    model = {'ambientocclusion': False,
             'textures': {**{key: 'tnc:block/thunder_seal/' + key for key in ['dark', 'gold', 'blue', 'gem']},
                          'particle': '#blue'}, 'elements': elements,
             'display': {'gui': {'rotation': [15, 45, 0], 'translation': [0, -3, 0], 'scale': [.5, .5, .5]}}}
    for location in ['item', 'spell']:
        path = ROOT / 'src/main/resources/assets/tnc/models' / location / 'thunder_seal_pylon.json'
        path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    print(f'{len(elements)} elements, {len(elements)*6} quads per pylon')


if __name__ == '__main__':
    build()
