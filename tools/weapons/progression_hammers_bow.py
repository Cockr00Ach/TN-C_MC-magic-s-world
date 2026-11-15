"""Generate the three progression hammers and the laminated horn recurve.

Run with Python, NumPy and Pillow. --check validates the checked-in assets.
Solid models, simplified ghosts and previews use the same quarter-unit forge
and material atlas as the existing warrior weapons.
"""

import argparse
import copy
import json
import math
from pathlib import Path

from PIL import Image

import pixel_forge
import weapon_gen as g


MODELS = Path(g.MODELS)
TEXTURES = Path(g.TEX)
PREVIEWS = Path(g.PREVIEW)
HAMMERS = {
    'ironwood_mallet': ('包铁木槌', 'Ironbound wood mallet', 1),
    'ring_flange_hammer': ('透环棱锤', 'Open-ring flanged hammer', 4),
    'thunder_bronze_hammer': ('雷纹铜锤', 'Thunder-scroll bronze hammer', 5),
}
BOW = 'laminated_horn_bow'
BOW_IDS = (BOW, *(f'{BOW}_pulling_{stage}' for stage in range(3)))
STEP = .25


def palette(blade, blade_hi, edge, guard, guard_hi, grip, wrap, gem, gem_hi):
    shades = (blade, blade_hi, edge, guard, guard_hi, grip, wrap, gem, gem_hi)
    names = ('blade', 'blade_hi', 'edge', 'guard', 'guard_hi', 'grip', 'wrap', 'gem', 'gem_hi')
    return {key: g.hx(color) for key, color in zip(names, shades)}


PALETTES = {
    'ironwood_mallet': palette('825035', 'ca9461', 'c0c9c9', '4f6068', 'a4b2b5', '422c25', '796149', '503125', '986945'),
    'ring_flange_hammer': palette('485460', '94a2b5', 'd7e2e2', '887046', 'd4b67d', '31252f', '654956', '457873', '86c5b5'),
    'thunder_bronze_hammer': palette('805632', 'cc9052', 'f2d297', '9a6e42', 'e6bc71', '2e2d29', '585e4c', '45756c', '87bcab'),
    BOW: palette('514139', '8c7363', 'ddd0a0', '59463c', 'a17c51', '312a27', '654b3d', '8b372b', 'cc6550'),
}


class Forge:
    def __init__(self, weapon_id):
        self.id = weapon_id
        self.parts = []

    def block(self, name, box, material):
        bounds = tuple(round(value, 3) for value in box)
        if not all(round(bounds[i + 3], 2) > round(bounds[i], 2) for i in range(3)):
            raise ValueError(f'{self.id} {name} collapsed: {bounds}')
        self.parts.append((f'{name}_{len(self.parts)}', bounds, material, 'block'))

    def relief(self, name, x, z, low, high, material, width=.25, length=.25, depth=.10):
        self.block(name, (x, low - depth, z, x + width, low, z + length), material)
        self.block(name, (x, high, z, x + width, high + depth, z + length), material)

    def rows(self, name, profile, y_min, y_max, material, step):
        start, end = profile[0][0], profile[-1][0]
        for row in range(round((end - start) / step)):
            z = start + row * step
            left, right = (g._lerp_profile(profile, z + step / 2, side) for side in range(2))
            left, right = round(left / step) * step, round(right / step) * step
            self.block(name, (left, y_min, z, right, y_max, z + step), material)

    def grid_face(self, name, x_size, z_size, x0, z0, y_min, y_max, mask, role, step):
        # Tile only the beveled surfaces and the carved rim. The broad interior is
        # made from merged rows, keeping the 3D detail affordable.
        for iz in range(round(z_size / step)):
            z = z0 + iz * step
            for ix in range(round(x_size / step)):
                x = x0 + ix * step
                xx, zz = x + step / 2, z + step / 2
                if mask(xx, zz):
                    material, bevel = role(xx, zz)
                    self.block(name, (x, y_min + bevel, z, x + step, y_max - bevel, z + step), material)


def haft(f, end, width, grip_length, detail, band='guard'):
    x = 7.5
    f.block('wood_core', (x - width / 2, 0, -3.25, x + width / 2, 1, end), 'grip')
    f.block('butt_cap', (x - width / 2 - .16, -.16, -3.55, x + width / 2 + .16, 1.16, -2.7), band)
    f.block('head_socket', (x - width / 2 - .27, -.32, end - 1.65, x + width / 2 + .27, 1.32, end + .35), band)
    if not detail:
        f.block('wrap', (x - width / 2 - .06, -.08, -.5, x + width / 2 + .06, 1.08, grip_length), 'grip')
        return
    for i in range(round((grip_length + .5) / .25)):
        z = -.5 + i * .25
        for row in range(4):
            fx = x - width / 2 + row * width / 4
            stagger = (row % 2) * .0625
            f.relief('woven_grip', fx, z + stagger, 0, 1, 'grip' if i % 5 else 'blade', width / 4, .16, .085)
    for z in (-2.5, -.6, grip_length + .1, end - 2.0):
        f.block('rolled_collars', (x - width / 2 - .125, -.10, z, x + width / 2 + .125, 1.10, z + .22), band)
    for run in range(2):
        direction = 1 if run == 0 else -1
        for i in range(round((end - grip_length - 2.1) / STEP)):
            z = grip_length + .25 + i * STEP
            x_line = x + direction * (.11 + (i // 7 % 3) * .085)
            if i % 11 not in (9, 10):
                f.relief('haft_woodgrain', x_line, z, 0, 1, 'blade', .11, .22, .0325)


def ironwood(detail):
    f = Forge('ironwood_mallet')
    haft(f, 10.5, 1.0, 4.0, detail)
    if not detail:
        f.block('oak_head', (3.5, -.65, 9.75, 11.5, 1.65, 13.25), 'blade')
        for x in (3.3, 10.75):
            f.block('metal_shoe', (x, -.88, 9.95, x + .95, 1.88, 13.05), 'guard')
        return f

    # Octagonal wooden cross-section at either striking end. Iron straps visibly
    # clamp a wooden head, with the wooden end grain peeking between them.
    for iz in range(16):
        z = 9.5 + iz * STEP
        chamfer = (.50 if iz in (0, 15) else .25 if iz in (1, 14) else 0)
        f.block('oak_head', (3.5, -.72 + chamfer, z, 11.5, 1.72 - chamfer, z + STEP), 'blade')
    for x in (4.25, 10.0):
        for iz in range(16):
            z = 9.5 + iz * STEP
            chamfer = (.50 if iz in (0, 15) else .25 if iz in (1, 14) else 0)
            f.relief('iron_strap', x, z, -.72 + chamfer, 1.72 - chamfer, 'guard', .75, STEP, .16)
        for z in (10, 11.375, 12.75):
            f.relief('strap_rivet', x + .24, z, -.89, 1.89, 'edge', .24, .24, .09)
        for z in (9.32, 13.49):
            f.block('strap_bridge', (x, -.3, z, x + .75, 1.3, z + .19), 'guard')
    for x in (3.25, 11.5):
        f.block('striking_shoe', (x, -.63, 9.85, x + .25, 1.63, 13.15), 'guard')
        f.block('rolled_shoe_edge', (x - .06, -.36, 10.03, x + .31, 1.36, 10.28), 'edge')
        f.block('rolled_shoe_edge', (x - .06, -.36, 12.72, x + .31, 1.36, 12.97), 'edge')
    for iz in range(4):
        for ix in range(18):
            if (ix + iz * 3) % 9 in (0, 8):
                continue
            x = 5.2 + ix * STEP
            z = 10.25 + iz * .73 + .11 * math.sin(ix * .48 + iz)
            material = 'gem' if iz % 2 == 0 else 'blade'
            f.relief('cut_oak_grain', x, z, -.72, 1.72, material, .22, .11, .035)
    f.block('wood_wedge', (7.06, 1.72, 10.95, 7.94, 1.95, 12.35), 'grip')
    f.block('wedge_pin', (7.36, 1.94, 11.35, 7.64, 2.06, 11.95), 'edge')
    return f


def ring_flange(detail):
    f = Forge('ring_flange_hammer')
    haft(f, 11.0, 1.18, 4.75, detail)
    # Octagonal ring evaluated in the X/Z plane. Both solid and ghost preserve
    # the actual hole, rather than painting it onto the hammer face.
    def ring_radius(x, z):
        dx, dz = abs(x - 7.5), abs(z - 14.0)
        return max(dx, dz, (dx + dz) * .77)

    f.grid_face('ring_head', 8, 8, 3.5, 10.0, -.83, 1.83,
                lambda x, z: 1.68 <= ring_radius(x, z) < 3.27,
                lambda x, z: ('edge', .14) if ring_radius(x, z) > 2.98 else ('blade', 0),
                STEP if detail else .75)
    for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        # Short swept flanges are solid striking faces, outside the empty ring.
        x, z = 7.5 + sx * 3.42, 14 + sz * 3.42
        for tier in range(3 if detail else 1):
            r = 1.0 - tier * .19
            dx, dz = sx * tier * .28, sz * tier * .28
            wx, wz = (.54 if sx else r), (.54 if sz else r)
            f.block('flanged_striker', (x + dx - wx, -.91 + tier * .18, z + dz - wz,
                                        x + dx + wx, 1.91 - tier * .18, z + dz + wz),
                    'edge' if tier == 2 else 'blade')
    if detail:
        # Main bronze bands follow both ring rims, with raised teal bosses at the four corners.
        f.grid_face('ring_face_bead', 7, 7, 4.0, 10.5, 1.83, 1.94,
                    lambda x, z: 1.72 < ring_radius(x, z) < 1.96 or 2.60 < ring_radius(x, z) < 2.82,
                    lambda x, z: ('guard', 0), STEP)
        f.grid_face('ring_reverse_bead', 7, 7, 4.0, 10.5, -.94, -.83,
                    lambda x, z: 1.72 < ring_radius(x, z) < 1.96 or 2.60 < ring_radius(x, z) < 2.82,
                    lambda x, z: ('guard', 0), STEP)
        for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            x, z = 7.5 + sx * 1.57, 14 + sz * 1.57
            f.relief('bronze_boss', x - .34, z - .34, -.94, 1.94, 'guard', .68, .68, .17)
            f.relief('jade_pin', x - .18, z - .18, -1.11, 2.11, 'gem', .36, .36, .10)
    return f


THUNDER_MEANDER = (
    '11111110',
    '10000010',
    '10111010',
    '10101010',
    '10100010',
    '10111110',
    '10000000',
    '11111111',
)


def thunder_bronze(detail):
    f = Forge('thunder_bronze_hammer')
    haft(f, 11.25, 1.38, 5.1, detail)
    profile = [(10.5, 4.25, 10.75), (11, 3.25, 11.75), (12, 2.75, 12.25),
               (14.75, 2.75, 12.25), (15.75, 3.25, 11.75), (16.25, 4.25, 10.75)]
    if detail:
        f.rows('bronze_hammer_head', profile, -1.18, 2.18, 'blade', STEP)
    else:
        for low, high, left, right in ((10.5, 11.25, 4.0, 11.0), (11.25, 15.5, 2.9, 12.1), (15.5, 16.25, 4.0, 11.0)):
            f.block('bronze_hammer_head', (left, -1.18, low, right, 2.18, high), 'blade')
    for sign in (-1, 1):
        for layer in range(5 if detail else 2):
            # Squared striking ends swell outward in stacked octagonal courses.
            x = 7.5 + sign * (4.25 + layer * .23)
            inset = layer * .12
            f.block('hammer_face', (x - .35, -1.33 + inset, 11.30 + inset,
                                    x + .35, 2.33 - inset, 15.42 - inset),
                    'edge' if layer % 2 else 'guard')
    if not detail:
        return f

    # Two bronze plates and square thunder-scroll engravings, modelled on both
    # sides in separate thicknesses. Long continuous lines read at inventory size.
    for center in (5.65, 9.35):
        f.relief('raised_panel_backplate', center - 1.10, 12.12, -1.18, 2.18, 'guard', 2.2, 2.40, .075)
        for iz, row in enumerate(THUNDER_MEANDER):
            for ix, filled in enumerate(row):
                if filled == '1':
                    f.relief('thunder_scroll', center - 1.0 + ix * STEP, 12.27 + iz * STEP,
                             -1.255, 2.255, 'edge', STEP, STEP, .10)
        for x in (center - 1.31, center + 1.05):
            for z in (11.68, 14.88):
                f.relief('bronze_rivet', x, z, -1.18, 2.18, 'edge', .26, .26, .17)
    for z in (11.26, 15.15):
        f.relief('raised_rail', 4.0, z, -1.18, 2.18, 'guard', 7, .26, .16)
        f.relief('rail_highlight', 4.25, z + .06, -1.34, 2.34, 'edge', 6.5, .12, .055)
    for width, depth, role in ((.78, .15, 'guard'), (.52, .22, 'gem'), (.22, .12, 'edge')):
        f.relief('central_boss', 7.5 - width / 2, 13.3 - width / 2, -1.18, 2.18, role, width, width, depth)
    return f


BOW_DISPLAY = {
    'gui': {'rotation': [0, 0, -12], 'scale': [.55] * 3},
    'thirdperson_righthand': {'rotation': [0, -90, 0], 'scale': [1.23] * 3},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'scale': [1.23] * 3},
    'firstperson_righthand': {'rotation': [0, -90, 12], 'translation': [1, 1, 0], 'scale': [1.10] * 3},
    'firstperson_lefthand': {'rotation': [0, 90, -12], 'translation': [-1, 1, 0], 'scale': [1.10] * 3},
    'ground': {'scale': [.5] * 3, 'translation': [0, 3, 0]},
    'fixed': {'scale': [.55] * 3},
}
BOW_CURVE = [(0, 8.0), (.15, 6.5), (.35, 5.5), (.56, 5.35),
             (.72, 6.0), (.85, 7.4), (.94, 8.7), (1, 9.0)]


def bow_curve(t):
    for (a, x), (b, xx) in zip(BOW_CURVE, BOW_CURVE[1:]):
        if a <= t <= b:
            return x + (xx - x) * ((t - a) / (b - a))
    return BOW_CURVE[-1][1]


def build_bow(name, pull):
    f = Forge(name)
    # The wrapped riser remains stationary through all four models. Horn, wood
    # and sinew laminations are separate lengths across every recurve limb.
    f.block('wrapped_riser', (7.45, 5.8, 7.35, 8.55, 10.2, 8.65), 'grip')
    for row in range(17):
        y = 5.875 + row * STEP
        for side in (-1, 1):
            z = 8 + side * .67
            x = 7.38 + (row % 4) * .07
            f.block('lacquered_wrap', (x, y, z - .045, x + .9, y + .14, z + .045), 'gem' if row % 6 in (0, 1) else 'grip')
    f.block('bronze_arrow_shelf', (7.25, 7.72, 7.04, 8.72, 8.0, 8.96), 'guard')
    nock_x = 9.3 + pull * 4.1
    for sign in (-1, 1):
        span = 9.75 - .70 * pull
        for index in range(39):
            a, b = index / 39, (index + 1) / 39
            t = (a + b) / 2
            y0, y1 = sorted((8 + sign * (1.62 + span * a), 8 + sign * (1.62 + span * b)))
            x = round((bow_curve(t) + pull * t ** 2 * 1.08) / STEP) * STEP
            width = .92 - t * .39
            thickness = .37 - t * .16
            f.block('dark_horn_back', (x - width * .55, y0, 8 - thickness, x + width * .29, y1, 8 + thickness), 'blade')
            f.block('bamboo_laminate', (x + width * .29, y0, 8 - thickness * .94, x + width * .44, y1, 8 + thickness * .94), 'guard')
            f.block('sinew_backing', (x + width * .44, y0, 8 - thickness * .73, x + width * .56, y1, 8 + thickness * .73), 'edge')
            if index % 11 in (0, 1) or index in (35, 36, 37):
                f.block('limb_binding', (x - width * .60, y0 + .03, 8 - thickness - .07,
                                         x + width * .60, y1 - .025, 8 + thickness + .07), 'gem')
            if index % 5 in (1, 2) and 2 <= index <= 28:
                for z in (8 - thickness - .033, 8 + thickness):
                    f.block('lacquer_seam', (x - .21, y0 + .035, z, x + .11, y1 - .025, z + .033), 'guard')
        tip_x = round((bow_curve(1) + pull * 1.08) / STEP) * STEP
        tip_y = 8 + sign * (1.62 + span)
        f.block('horn_nock', (tip_x - .28, tip_y - .28, 7.79,
                              tip_x + .32, tip_y + .21, 8.21), 'guard')
        for index in range(45):
            a, b = index / 45, (index + 1) / 45
            xa, xb = sorted((tip_x + (nock_x - tip_x) * a, tip_x + (nock_x - tip_x) * b))
            ya, yb = sorted((tip_y + (8 - tip_y) * a, tip_y + (8 - tip_y) * b))
            f.block('twisted_string', (xa - .048, ya - .03, 7.952, xb + .048, yb + .03, 8.048), 'edge')
    if pull > 0:
        # The nock meets the same string point as both string halves.
        f.block('bamboo_arrow', (nock_x - 11.6, 7.93, 7.93, nock_x, 8.07, 8.07), 'guard')
        for segment in range(5):
            x = nock_x - 12.55 + segment * .19
            half = .08 + segment * .048
            f.block('arrowhead', (x, 8 - half, 8 - half * .55,
                                  x + .19, 8 + half, 8 + half * .55), 'edge')
        for sign in (-1, 1):
            for index in range(7):
                x = nock_x - 2.2 + index * STEP
                height = min(index + 1, 7 - index) * .10
                ya, yb = sorted((8, 8 + sign * height))
                f.block('red_arrow_fletching', (x, ya, 7.94, x + STEP, yb, 8.06), 'gem')
        f.block('arrow_nock', (nock_x - .22, 7.88, 7.88, nock_x + .05, 8.12, 8.12), 'edge')
    return f


def model_for(forge, material, grip, display=None):
    definition = {'id': forge.id, 'palette': material, 'parts': [], '_pixel_parts': forge.parts}
    texture, rects = g.build_texture(definition)
    definition['_rects'] = rects
    model = g.build_model(definition, rects)
    model['credit'] = 'TN-C quarter-unit pixel forge · tools/weapons/progression_hammers_bow.py'
    model['tnc_grip'] = list(grip)
    model['display'] = copy.deepcopy(display) if display is not None else g.calibrated_display(grip)
    return definition, texture, model


def slot_hammer(model):
    grip = model['tnc_grip']
    scale = min(.63, 13.6 / (max(e['to'][2] for e in model['elements']) - min(e['from'][2] for e in model['elements'])))
    center_z = (max(e['to'][2] for e in model['elements']) + min(e['from'][2] for e in model['elements'])) / 2
    model['display']['gui'].update(scale=[scale] * 3, translation=[(8 - grip[0]) * scale, (8 - center_z) * scale, 0])


def validate_model(model, image, name):
    assert isinstance(model['tnc_grip'], list) and len(model['tnc_grip']) == 3, name
    assert image.size == (128, 128) and model['texture_size'] == [128, 128], name
    for element in model['elements']:
        assert 1 <= len(element['faces']) <= 6, f'{name} empty faces'
        assert all(-16 <= v <= 32 for v in (*element['from'], *element['to'])), f'{name} bounds'
        assert all(a < b for a, b in zip(element['from'], element['to'])), f'{name} collapsed element'
        for face in element['faces'].values():
            u0, v0, u1, v1 = face['uv']
            assert face['texture'] == '#0' and all(0 <= uv <= 16 for uv in face['uv']), f'{name} UV'
            assert u0 != u1 and v0 != v1, f'{name} zero-area UV'
            assert image.getpixel((int((u0 + u1) * 4), int((v0 + v1) * 4)))[3] == 255, f'{name} transparent UV'


def generate():
    g.PALETTES.update(PALETTES)
    (MODELS / 'warrior_ghost').mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    rows = []
    for build in (ironwood, ring_flange, thunder_bronze):
        forge = build(True)
        name, english, level = HAMMERS[forge.id]
        grip = (7.5, .5, 2.05)
        w, image, model = model_for(forge, forge.id, grip)
        slot_hammer(model)
        g.write_model(forge.id, model)
        image.save(TEXTURES / f'{forge.id}.png', optimize=True)
        _, _, ghost = model_for(build(False), forge.id, grip, model['display'])
        g.write_model(f'warrior_ghost/{forge.id}', ghost)
        preview = g.render_preview(w, image, size=460, yaw_deg=23, pitch_deg=-59)
        preview.save(PREVIEWS / f'weapon-{forge.id}.png', optimize=True)
        rows.append((preview, f'{name} · {level} 阶', english))
    for stage, (model_id, pull) in enumerate(zip(BOW_IDS, (0, .31, .68, 1.0))):
        f = build_bow(model_id, pull)
        w, image, model = model_for(f, BOW, (8, 8, 8), BOW_DISPLAY)
        if stage == 0:
            model['overrides'] = [{'predicate': {'pulling': 1, 'pull': threshold}, 'model': 'tnc:item/' + name}
                                  for name, threshold in zip(BOW_IDS[1:], (0, .65, .9))]
        g.write_model(model_id, model)
        image.save(TEXTURES / f'{model_id}.png', optimize=True)
        preview = g.render_preview(w, image, size=460, yaw_deg=-18, pitch_deg=-7)
        if stage in (0, 3):
            preview.save(PREVIEWS / f'weapon-{model_id}.png', optimize=True)
        rows.append((preview, f'乌角复合弓 · {("松弦", "起弦", "半弦", "满弦")[stage]}', 'L4 · Laminated horn recurve'))
    g.contact_sheet(rows, str(PREVIEWS / 'weapon-progression-hammers-bow.png'), cols=4, cell=340,
                    title='TN-C · 战士进阶武器 · 锤与弓')


def check():
    models = {}
    for name in (*HAMMERS, *BOW_IDS):
        model = models[name] = json.loads((MODELS / f'{name}.json').read_text(encoding='utf-8'))
        assert model['textures']['0'] == f'tnc:item/{name}', name
        with Image.open(TEXTURES / f'{name}.png') as source:
            image = source.convert('RGBA')
        validate_model(model, image, name)
        ghost_size = 0
        if name in HAMMERS:
            ghost = json.loads((MODELS / 'warrior_ghost' / f'{name}.json').read_text(encoding='utf-8'))
            validate_model(ghost, image, name + ' ghost')
            assert ghost['display'] == model['display'] and ghost['tnc_grip'] == model['tnc_grip'], name
            ghost_size = len(ghost['elements'])
            assert 0 < ghost_size < 128, f'{name} ghost budget'
            if name == 'ring_flange_hammer':
                for candidate in (model, ghost):
                    assert not any(e['from'][0] <= 7.5 <= e['to'][0] and e['from'][2] <= 14 <= e['to'][2]
                                   for e in candidate['elements']), 'Ring hole is filled'
        print(f'{name}: {len(model["elements"])} elements; {ghost_size} simplified ghost elements')
    bow = models[BOW]
    assert [entry['model'] for entry in bow['overrides']] == ['tnc:item/' + name for name in BOW_IDS[1:]]
    assert all(models[name]['tnc_grip'] == [8, 8, 8] and models[name]['display'] == bow['display'] for name in BOW_IDS)
    assert len({json.dumps(models[name]['elements']) for name in BOW_IDS}) == 4, 'Bow frames must each contain distinct geometry'
    print('Pass: model bounds, opaque UVs, fixed grips, ring opening and all three bow overrides.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate the existing assets without regenerating them')
    args = parser.parse_args()
    if not args.check:
        generate()
    check()
