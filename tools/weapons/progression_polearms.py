"""Build the six spear/halberd progression assets. Run with Python, Pillow and NumPy."""

from dataclasses import dataclass
from pathlib import Path
import json
import math

from PIL import Image
import weapon_gen as forge


@dataclass(frozen=True)
class Weapon:
    id: str
    name: str
    level: int
    colors: tuple[str, ...]  # wood, wrap, blade, edge, fittings, raised metal, inlay
    head: str
    point: tuple[tuple[float, float, float], ...]  # z, left x, right x
    butt: float
    socket: float


WEAPONS = (
    Weapon('militia_spear', '民兵铁枪', 1,
           ('76503a', '513624', '58666b', 'b1c5c3', '554b41', '8c7b64', '7c6950'),
           'plain', ((12.15, 6.63, 7.37), (13.28, 6.34, 7.66), (14.3, 6.43, 7.57),
                     (17.6, 6.93, 7.07)), -9.1, 12.35),
    Weapon('silver_hook_spear', '银钩枪', 4,
           ('29343e', '25324a', '8196a5', 'e1f2f3', '6e7d8c', 'c9d9df', '8dc8bd'),
           'hook', ((12.3, 6.57, 7.43), (14.1, 6.04, 7.96), (16.4, 6.33, 7.67),
                    (20.9, 6.92, 7.08)), -10.3, 12.6),
    Weapon('coiled_dragon_spear', '盘龙金枪', 5,
           ('511f25', '5c2831', 'aa7838', 'ffe4a2', 'a76c31', 'f2c47a', '559b8f'),
           'dragon', ((12.8, 6.48, 7.52), (14.5, 5.73, 8.27), (16.1, 5.97, 8.03),
                      (18.9, 6.47, 7.53), (22.75, 6.93, 7.07)), -10.5, 13.1),
    Weapon('bronze_ge_halberd', '青铜戈戟', 1,
           ('6c5036', '62452e', '756136', 'c6ac72', '68603e', 'bc9955', '668a78'),
           'ge', ((12.0, 6.57, 7.43), (13.6, 6.18, 7.82), (15.5, 6.35, 7.65),
                  (18.35, 6.93, 7.07)), -9.9, 12.3),
    Weapon('single_crescent_halberd', '单月戟', 2,
           ('513f37', '523637', '536a6b', 'cbddcf', '786744', 'bf9a60', '78a296'),
           'crescent', ((12.0, 6.52, 7.48), (14.2, 5.98, 8.02), (16.1, 6.37, 7.63),
                        (20.7, 6.94, 7.06)), -10.1, 12.4),
    Weapon('black_iron_halberd', '玄铁双月戟', 4,
           ('352a3a', '3d2a42', '394452', '9eabc2', '564c65', 'a894ae', 'aa79ba'),
           'double', ((11.9, 6.52, 7.48), (14.4, 5.69, 8.31), (17.1, 6.2, 7.8),
                      (22.2, 6.91, 7.09)), -10.5, 12.3),
)

ROLES = ('wood', 'grip', 'blade', 'edge', 'guard', 'relief', 'inlay')
PREVIEW_NAME = 'progression-polearms-20261115.png'
DETAIL_NAME = 'progression-polearms-detail-20261115.png'


class Sculpt:
    def __init__(self, detailed):
        self.detailed = detailed
        self.parts = []

    def box(self, name, bounds, role):
        bounds = tuple(round(v, 4) for v in bounds)
        if all(round(bounds[i + 3], 2) > round(bounds[i], 2) for i in range(3)):
            self.parts.append((f'{name}_{len(self.parts)}', bounds, role, 'block'))

    def blade(self, name, profile, thickness, outer=0):
        """Step tapered cutting edges in x/z and bevel each cross-section in depth."""
        step = .23 if self.detailed else .72
        rows = math.ceil((profile[-1][0] - profile[0][0]) / step)
        for row in range(rows):
            z0 = profile[0][0] + row * step
            z1 = min(profile[-1][0], z0 + step)
            z = (z0 + z1) * .5
            left, right = (forge._lerp_profile(profile, z, axis) for axis in (0, 1))
            columns = math.ceil((right - left) / .23) if self.detailed else 1
            for col in range(columns):
                u = (col + .5) / columns
                x0 = left + (right - left) * col / columns
                x1 = left + (right - left) * (col + 1) / columns
                edge = 2 * abs(u - .5) if outer == 0 else u if outer > 0 else 1 - u
                half = thickness * (.5 - .31 * edge ** 1.8) if self.detailed else thickness * .43
                role = 'edge' if edge > .68 or row == rows - 1 else 'blade'
                self.box(name, (x0, .5 - half, z0, x1, .5 + half, z1), role)
                if self.detailed and col == columns // 2 and columns >= 5 and row % 6 < 4:
                    self.relief('forged_ridge', (x0 + x1) / 2, z0, z1 - z0, .12, half, 'blade')

    def relief(self, name, x, z, length, width, half, role):
        """Shallow inlays and rivets on both faces, seated in the parent part."""
        for sign in (-1, 1):
            y = .5 + sign * half
            ya, yb = (y - .08, y + .07) if sign > 0 else (y - .07, y + .08)
            self.box(name, (x - width / 2, ya, z, x + width / 2, yb, z + length), role)

    def collar(self, name, z, length, radius, role='guard'):
        quarter = radius * .74
        self.box(name, (7-radius, .5-quarter, z, 7+radius, .5+quarter, z+length), role)
        if self.detailed:
            self.box(name, (7-quarter, .5-radius, z, 7+quarter, .5+radius, z+length), role)
            for end in (z, z + length - .09):
                self.relief(name + '_seam', 7, end, .09, 1.3 * radius, radius, 'relief')


def shaft(sculpt, weapon):
    detailed = sculpt.detailed
    # Solid octagonal wood core, leather grip and forged socket. The grip center is fixed across models.
    sculpt.box('wood_haft', (6.61, .14, weapon.butt, 7.39, .86, weapon.socket), 'wood')
    if detailed:
        for sign in (-1, 1):
            x = 7 + sign * .39
            x0, x1 = sorted((x, x + sign * .07))
            sculpt.box('haft_side', (x0, .28, weapon.butt, x1, .72, weapon.socket), 'wood')
        for i in range(35):
            z = weapon.butt + .45 + i * .58
            if .2 < z < 3.75 or z > weapon.socket - 1.2:
                continue
            x = 7 + .12 * math.sin(i * .83)
            sculpt.relief('wood_grain', x, z, .42, .052, .35, 'wood')
    sculpt.box('leather_grip', (6.51, .02, .55, 7.49, .98, 3.55), 'grip')
    if detailed:
        for i in range(12):
            z = .62 + .235 * i
            for strip in range(4):
                x = 6.63 + .24 * strip
                sculpt.relief('split_lacing', x, z + .039 * strip, .14, .19, .48,
                              'relief' if weapon.level > 3 and i % 4 == 0 else 'grip')
    sculpt.collar('butt_shoe', weapon.butt, .62 if weapon.level < 3 else 1.08, .51)
    sculpt.collar('socket', weapon.socket - 1.25, 1.50, .59)
    if detailed:
        locations = (.46, 3.50, weapon.socket - 1.45)
        if weapon.level > 3:
            locations += (-7.7, -4.0, 7.0, 9.3)
        for z in locations:
            sculpt.collar('forged_binding', z, .19 if weapon.level < 3 else .31, .57)
        for i in range(5 if weapon.level > 3 else 3):
            z = weapon.socket - 1.15 + i * .23
            sculpt.relief('socket_rivet', 7, z, .11, .19, .59, 'relief')


def wing(sculpt, name, shape, side=1, thickness=.72):
    profile = shape if side == 1 else tuple((z, 14 - right, 14 - left) for z, left, right in shape)
    sculpt.blade(name, profile, thickness, side)


def bridge(sculpt, name, z, end, role='guard'):
    left, right = sorted((7.0, end))
    sculpt.box(name, (left, .15, z, right, .85, z + .38), role)
    if sculpt.detailed:
        for x in (left + .28, right - .28):
            sculpt.relief(name + '_pin', x, z + .10, .16, .15, .35, 'relief')


def head(sculpt, weapon):
    sculpt.blade('spearhead', weapon.point, .88 if weapon.level < 3 else 1.13)
    if weapon.head == 'hook':
        shape = ((11.8, 7.63, 8.4), (12.5, 8.35, 9.6), (13.45, 9.14, 10.30),
                 (14.4, 9.25, 10.28), (15.5, 8.92, 9.68), (16.25, 8.32, 8.49))
        wing(sculpt, 'silver_hook', shape, thickness=.72)
        bridge(sculpt, 'hook_socket', 12.06, 9.18)
        if sculpt.detailed:
            sculpt.relief('seal', 7, 13.15, .49, .33, .49, 'inlay')
    elif weapon.head == 'dragon':
        # A connected, stepped dragon wraps the socket and opens its jaw under the head.
        count = 35 if sculpt.detailed else 8
        for i in range(count):
            t = i / (count - 1)
            angle = t * math.tau * 1.42
            x, y, z = 7 + .57 * math.cos(angle), .5 + .53 * math.sin(angle), 8.2 + 4.8 * t
            radius = .145 + .12 * t
            sculpt.box('dragon_body', (x-radius, y-radius, z-.11, x+radius, y+radius, z+(.13 if sculpt.detailed else .39)), 'relief')
            if sculpt.detailed and i % 3 == 0:
                sculpt.box('dragon_scale', (x-.24, y-.14, z-.11, x+.24, y+.14, z+.08), 'guard')
        sculpt.box('dragon_head', (6.07, .19, 12.43, 7.91, .99, 13.45), 'guard')
        sculpt.box('dragon_jaw', (6.42, .07, 13.17, 7.55, .34, 13.89), 'relief')
        sculpt.box('dragon_snout', (6.49, .63, 13.23, 7.49, .96, 13.98), 'relief')
        if sculpt.detailed:
            for side in (-1, 1):
                x = 7 + side * .67
                sculpt.relief('jade_eye', x, 13.07, .17, .23, .49, 'inlay')
                for horn in range(4):
                    hx = x + side * horn * .09
                    sculpt.box('antler', (hx-.10, .64+horn*.08, 12.64-horn*.19,
                                          hx+.10, .93+horn*.08, 12.9-horn*.19), 'relief')
                for claw in range(3):
                    sx = 7 + side * (.36 + claw * .18)
                    sculpt.box('claw', (sx-.09, .89, 11.13+claw*.16, sx+.10, 1.15, 11.40+claw*.16), 'edge')
            for i in range(8):
                sculpt.relief('gold_fuller_tick', 7, 14.38 + i * .50, .30, .16, .54, 'relief')
    elif weapon.head == 'ge':
        # Lateral dagger-axe blade has a broad heel and a long pointed third edge.
        shape = ((11.32, 7.85, 9.35), (11.87, 7.92, 10.79), (12.55, 8.07, 12.37),
                 (13.01, 8.40, 11.57), (13.51, 8.73, 9.89))
        wing(sculpt, 'bronze_dagger_axe', shape, -1, .78)
        bridge(sculpt, 'cross_socket', 11.85, 4.65)
        if sculpt.detailed:
            for i in range(9):
                sculpt.relief('cast_meander', 4.18 + .23 * i, 12.06 + (i % 3) * .13, .20, .10, .35,
                              'relief' if i % 3 else 'inlay')
    elif weapon.head == 'crescent':
        shape = ((11.82, 8.15, 8.40), (12.75, 8.89, 10.27), (13.95, 9.27, 11.26),
                 (15.3, 9.30, 11.45), (16.70, 9.05, 10.98), (18.02, 8.47, 9.64),
                 (18.68, 7.89, 8.10))
        wing(sculpt, 'single_crescent', shape, thickness=.83)
        for z, end in ((12.82, 9.91), (16.18, 10.0)):
            bridge(sculpt, 'single_crescent_shoulder', z, end)
        if sculpt.detailed:
            for i in range(13):
                z = 13.15 + .26 * i
                sculpt.relief('moon_inlay', 9.37 + .35 * math.sin(i / 12 * math.pi), z, .18, .15,
                              .36, 'inlay' if i % 3 else 'relief')
    elif weapon.head == 'double':
        shape = ((10.9, 8.25, 8.52), (12.1, 9.1, 10.55), (13.65, 9.58, 11.8),
                 (15.6, 9.66, 12.08), (17.52, 9.35, 11.75), (19.2, 8.70, 10.44),
                 (20.05, 7.89, 8.10))
        for side in (-1, 1):
            wing(sculpt, 'black_iron_crescent', shape, side, 1.06)
            for z, reach in ((12.22, 10.15), (17.30, 10.47)):
                bridge(sculpt, 'crescent_arch', z, 7 + side * (reach - 7))
            if sculpt.detailed:
                for i in range(21):
                    z = 13.32 + .23 * i
                    x = 7 + side * (2.79 + .42 * math.sin(math.pi * i / 20))
                    sculpt.relief('silver_inlaid_scroll', x, z, .17, .16, .46,
                                  'inlay' if i % 4 < 3 else 'relief')
        sculpt.collar('chiseled_lock', 11.65, .66, .77)


def material_atlas(weapon):
    """Four eight-pixel tiles for each material, with coherent grain and etched ornament."""
    image = Image.new('RGBA', (128, 128))
    pixels = image.load()
    for row, (role, base) in enumerate(zip(ROLES, weapon.colors)):
        base = forge.hx(base)
        shade = (forge.dark(base, .28), forge.dark(base, .12), base,
                 forge.light(base, .13), forge.light(base, .29))
        for variant in range(4):
            for y in range(8):
                for x in range(8):
                    if role == 'wood':
                        grain = (x + variant + int(math.sin(y * .58) * 1.4)) % 5
                        tone = {0: 0, 1: 1, 2: 2, 3: 3, 4: 2}[grain]
                    elif role == 'grip':
                        tone = (1, 3, 2, 0)[(y + x // 2 + variant) % 4]
                    elif role == 'blade':
                        tone = 2 + (1 if (x + variant + y // 4) % 6 == 0 else 0)
                        if x == 1 and y in (1, 2, 3):
                            tone = 1
                    elif role == 'edge':
                        tone = 4 if (x + variant) % 7 < 2 else 3 if x % 3 == 0 else 2
                    elif role in ('guard', 'relief'):
                        tone = 1 if x == 0 or y == 7 else 3 if x == 7 or y == 0 else 2
                        if (abs(2*x-7) + abs(2*y-7) + 2*variant) % 8 == 4:
                            tone = 3 if role == 'relief' else 0
                    else:
                        tone = 4 if 1 <= x+y <= 4 else 1 if x+y >= 10 else 3
                    pixels[variant * 8 + x, row * 8 + y] = shade[tone]
    return image


def texture_rects(parts):
    rects = {}
    for name, box, role, _ in parts:
        row = ROLES.index(role)
        variant = (math.floor(box[0] * 5) + math.floor(box[2] * 3)) % 4
        size = 8 if role in ('wood', 'grip') or (box[3] - box[0]) * (box[5] - box[2]) > .48 else 2
        offset = 0 if size == 8 else 2 + (variant % 2) * 2
        rects[name] = (variant * 8 + offset, row * 8 + offset, size, size)
    return rects


def make(weapon, detailed):
    sculpt = Sculpt(detailed)
    shaft(sculpt, weapon)
    head(sculpt, weapon)
    definition = {'id': weapon.id, 'parts': [('grip', (6.51, .02, .55, 7.49, .98, 3.55), 'grip', 'block')],
                  '_pixel_parts': sculpt.parts, '_rects': texture_rects(sculpt.parts)}
    model = forge.build_model(definition, definition['_rects'])
    lo, hi = weapon.butt, weapon.point[-1][0]
    scale = min(.68, 13.6 / (hi - lo))
    model['display']['gui'].update(scale=[scale] * 3, translation=[0, round((8 - (lo + hi) / 2) * scale, 4), 0])
    model['credit'] = 'TN-C · 长柄兵器进阶 · tools/weapons/progression_polearms.py'
    return definition, model


def verify(model, ghost, texture):
    assert model['display'] == ghost['display'] and model['tnc_grip'] == ghost['tnc_grip']
    assert 220 <= len(model['elements']) <= 2200
    assert len(ghost['elements']) < 85
    assert texture.size == (128, 128)
    for item in (model, ghost):
        for cube in item['elements']:
            assert all(-16 <= a < b <= 32 for a, b in zip(cube['from'], cube['to']))
            for face in cube['faces'].values():
                u1, v1, u2, v2 = face['uv']
                assert face['texture'] == '#0' and all(0 <= v <= 16 for v in face['uv'])
                assert u1 != u2 and v1 != v2
                assert texture.getpixel((int((u1 + u2) * 4), int((v1 + v2) * 4)))[3] == 255


def main():
    previews, details = [], []
    ghost_dir = Path(forge.MODELS) / 'warrior_ghost'
    ghost_dir.mkdir(exist_ok=True)
    for weapon in WEAPONS:
        definition, model = make(weapon, True)
        _, ghost = make(weapon, False)
        texture = material_atlas(weapon)
        verify(model, ghost, texture)
        forge.write_model(weapon.id, model)
        (ghost_dir / (weapon.id + '.json')).write_text(json.dumps(ghost, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        texture.save(Path(forge.TEX) / (weapon.id + '.png'), optimize=True)
        label = f'{weapon.name} · {weapon.level} 级'
        previews.append((forge.render_preview(definition, texture, size=530, yaw_deg=24, pitch_deg=-63), label, weapon.id))
        # Intersect long parts at the crop plane rather than dropping the haft.
        # The close-up must keep socket, collars and wood visibly connected.
        crop_at = 7.9
        clipped = []
        for name, bounds, role, mode in definition['_pixel_parts']:
            if bounds[5] > crop_at:
                x0, y0, z0, x1, y1, z1 = bounds
                clipped.append((name, (x0, y0, max(z0, crop_at), x1, y1, z1), role, mode))
        cropped = dict(definition, _pixel_parts=clipped)
        details.append((forge.render_preview(cropped, texture, size=530, yaw_deg=34, pitch_deg=-65), label, weapon.id))
        print(f'{weapon.id}: {len(model["elements"])} detailed boxes, {len(ghost["elements"])} ghost boxes')
    forge.contact_sheet(previews, str(Path(forge.PREVIEW) / PREVIEW_NAME), cols=3, cell=460,
                        title='TN-C · 长柄兵器进阶 / 枪与戟')
    forge.contact_sheet(details, str(Path(forge.PREVIEW) / DETAIL_NAME), cols=3, cell=460,
                        title='TN-C · 刃口、护钩、龙纹与月牙')


if __name__ == '__main__':
    main()
