"""生成火手链 5 个图标（16x16 像素：攥紧的拳头 + 缝里的火）。

独立于 tools/spell_icons.py（那个是几何管线），这里直接按 ASCII 点阵画，
因为拳头这种"块状剪影"用点阵更准。

    python build/make_hand_icons.py
"""

import pathlib

from PIL import Image

DARK = (72, 16, 0)
BODY = (224, 88, 26)
LIGHT = (248, 208, 128)

FIST = """
................
................
...a.a.a.a......
..ababababa.....
..aaaaaaaaaa....
.abbbbbbbbbba...
.abbbbbbbbbba...
.abbbbbbbbbbba..
..abbbbbbbbba...
...aaaaaaaaa....
....aaa1aaa.....
................
2...............
...3............
.....4..........
.......5........
"""

OUT = pathlib.Path('src/main/resources/assets/tnc/textures/spell')
NAMES = ['flame_grip', 'blaze_clutch', 'molten_crush', 'inferno_clench', 'pyre_god_hand']


def render(tier):
    rows = FIST.strip('\n').split('\n')
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            if ch in '12345':
                if tier < int(ch):
                    continue
                ch = 'b'
            r, g, b = {'a': DARK, 'b': BODY, 'c': LIGHT}[ch]
            img.putpixel((x, y), (r, g, b, 255))
    return img


for i, name in enumerate(NAMES, start=1):
    render(i).save(OUT / (name + '.png'))
    print('wrote', name)
