"""Pack AI-painted, individually assigned plates into non-overlapping per-face UV islands.

Only crops/resamples authored art for the model's UV layout; does not paint materials.
Run through gen_stone_lancer.py so neither the head nor atlas can be applied twice.
"""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / f'art/materials/stone_lancer_individual_plates_20261019_{half}.png'
           for half in ('upper', 'lower')]
TEXTURE = ROOT / 'src/main/resources/assets/tnc/textures/entity/stone_lancer.png'
MANIFEST = ROOT / 'model-source/stone_lancer/individual_plates.json'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
ATLAS = 2048
DENSITY = 2.25
PAD = 2


def taller_head(bones):
    """Give the stone mask a real cranium and jaw without changing its animation rig."""
    for bone in bones:
        if bone['name'] != 'head' and bone.get('parent') != 'head':
            continue
        def position(v):
            return [round(v[0] * .94, 4), round(73 + (v[1] - 73) * 1.62, 4),
                    round(-18 + (v[2] + 18) * 1.1, 4)]
        bone['pivot'] = position(bone['pivot'])
        for cube in bone.get('cubes', []):
            cube['origin'] = position(cube['origin'])
            cube['size'] = [round(v*s, 4) for v, s in zip(cube['size'], (.94, 1.62, 1.1))]
            if 'pivot' in cube:
                cube['pivot'] = position(cube['pivot'])
    head = next(b for b in bones if b['name'] == 'head')
    # A lower jaw and tapered chin stop the eyes reading as a thin horizontal slab.
    for center, size, turn in [((-10, 77, -41), (13, 8, 9), -8),
                               ((10, 77, -41), (13, 8, 9), 8),
                               ((0, 74, -40), (10, 10, 10), 0)]:
        head['cubes'].append({'origin': [center[i]-size[i]/2 for i in range(3)],
                              'size': list(size), 'pivot': list(center), 'rotation': [4, 0, turn],
                              'uv': {f: {'uv': [0, 0], 'uv_size': [1, 1]} for f in FACES}})


def pack(bones):
    halves = [Image.open(p).convert('RGB') for p in SOURCES]
    source = Image.new('RGB', (halves[0].width, sum(p.height for p in halves)))
    offset = 0
    for half in halves:
        source.paste(half, (0, offset))
        offset += half.height
    atlas = Image.new('RGB', (ATLAS, ATLAS), (44, 38, 31))
    # The generated sheet has nine greige rows, three basalt rows, two rune rows,
    # and a final double-height row of amber panels. Boundaries verified visually.
    families = {0: list(range(0, 9*16)), 1: list(range(9*16, 12*16)),
                2: list(range(12*16, 14*16)), 3: list(range(14*16, 15*16))}
    counts = {i: 0 for i in families}
    records, rectangles = [], []
    for bone in bones:
        for i, cube in enumerate(bone.get('cubes', [])):
            old = cube['uv']['north']['uv']
            family = int(old[0] >= 128) + 2 * int(old[1] >= 128)
            index = counts[family]
            if index >= len(families[family]):
                raise ValueError(f'Not enough individually painted plates for material {family}')
            cell = families[family][index]
            counts[family] += 1
            col, row = cell % 16, cell // 16
            box = (round(col*source.width/16)+2, round(row*source.height/16)+2,
                   round((col+1)*source.width/16)-2,
                   round((row+(2 if family == 3 else 1))*source.height/16)-2)
            plate = source.crop(box)
            record = {'bone': bone['name'], 'cube': i, 'source_cell': cell,
                      'material': ('greige', 'basalt', 'inlaid', 'amber')[family], 'faces': {}}
            records.append(record)
            sx, sy, sz = cube['size']
            for face in FACES:
                w, h = (sx, sy) if face in ('north', 'south') else (sz, sy) if face in ('east', 'west') else (sx, sz)
                # Exact fractional UV spans preserve physical aspect ratio; integer
                # crop bounds merely add a safety pixel outside each UV island.
                uw, vh = w*DENSITY, h*DENSITY
                pw, ph = math.ceil(uw)+2*PAD, math.ceil(vh)+2*PAD
                rectangles.append((pw, ph, cube, face, uw, vh, plate, record))
    # Deterministic descending-height shelf packing avoids texture overlap.
    rectangles.sort(key=lambda r: (-r[1], -r[0]))
    x = y = shelf = 0
    for pw, ph, cube, face, uw, vh, plate, record in rectangles:
        if x+pw > ATLAS:
            x = 0
            y += shelf
            shelf = 0
        if y+ph > ATLAS:
            raise ValueError('Per-face atlas exceeds texture budget')
        iw, ih = pw-2*PAD, ph-2*PAD
        # Cover-crop instead of stretching square rock details across long edges.
        patch = ImageOps.fit(plate, (iw, ih), method=Image.Resampling.LANCZOS)
        atlas.paste(patch, (x+PAD, y+PAD))
        # Duplicate the outer pixels into gutters for filtered/mipped sampling.
        atlas.paste(patch.resize((iw+2*PAD, ih+2*PAD)), (x, y))
        atlas.paste(patch, (x+PAD, y+PAD))
        cube['uv'][face] = {'uv': [x+PAD, y+PAD], 'uv_size': [round(uw, 5), round(vh, 5)]}
        record['faces'][face] = [x+PAD, y+PAD, round(uw, 5), round(vh, 5)]
        x += pw
        shelf = max(shelf, ph)
    # Keep RGB: a global palette discards the small but important bright crystal
    # regions because ordinary stone occupies almost all of the texture.
    atlas.save(TEXTURE, optimize=True)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({'sources': [str(p.relative_to(ROOT)) for p in SOURCES],
        'source_sha256': [hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES],
        'atlas': [ATLAS, ATLAS], 'texels_per_model_unit': DENSITY,
        'pieces': len(records), 'faces': len(rectangles), 'used_height': y+shelf,
        'materials': counts, 'assignments': records}, indent=2)+'\n', encoding='utf-8')
    print(f'Individual armor: {len(records)} pieces, {len(rectangles)} unique UV faces, {y+shelf}/{ATLAS} rows')
