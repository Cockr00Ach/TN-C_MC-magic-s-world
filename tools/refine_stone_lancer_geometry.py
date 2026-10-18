"""Sculpt a giant-scale rock relief over the existing Stone Lancer animation rig."""
import random


def refine(bones):
    """Run once on the generator's 66-cube base; preserve all bind pivots and bone names."""
    assert sum(len(b.get('cubes', [])) for b in bones) == 66, 'Refine the base rig exactly once'
    by_name = {b['name']: b for b in bones}
    rng = random.Random(20261018)

    def sample(cube, material=None):
        for face, uv in cube['uv'].items():
            if material is None:
                u = 128 if uv['uv'][0] >= 128 else 0
                v = 128 if uv['uv'][1] >= 128 else 0
            else:
                u, v = [(0, 0), (128, 0), (0, 128), (128, 128)][material]
            x, y, z = cube['size']
            w, h = (x, y) if face in ('north', 'south') else (z, y) if face in ('east', 'west') else (x, z)
            density = min(.95, 118 / max(w, h))
            w, h = max(1, w * density), max(1, h * density)
            uv.update(uv=[round(u + rng.uniform(2, 126 - w), 3), round(v + rng.uniform(2, 126 - h), 3)],
                      uv_size=[round(w, 3), round(h, 3)])

    def reshape(name, factors):
        for c in by_name[name]['cubes']:
            center = [c['origin'][i] + c['size'][i] / 2 for i in range(3)]
            c['size'] = [round(v * f, 4) for v, f in zip(c['size'], factors)]
            c['origin'] = [round(center[i] - c['size'][i] / 2, 4) for i in range(3)]
            sample(c)

    def plate(name, center, size, material=0, rotation=(0, 0, 0)):
        c = {'origin': [round(center[i] - size[i] / 2, 4) for i in range(3)],
             'size': list(size), 'uv': {f: {} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
        if any(rotation):
            c.update(rotation=list(rotation), pivot=list(center))
        sample(c, material)
        by_name[name]['cubes'].append(c)

    # Recess the oversized base masses so the overlapping armor has real relief and gaps.
    reshape('torso', (.94, 1, .9))
    reshape('pelvis', (.96, 1, .94))
    reshape('head', (.94, 1.08, .94))
    for sign in (-1, 1):
        reshape('arm_' + str(sign), (.88, 1, .88))
        reshape('fore_' + str(sign), (.9, 1, .88))
        reshape('lance_point_down_' + str(sign), (.8, 1, .8))
        reshape('leg_' + str(sign), (.94, 1, .94))

    # Distinct mask, recessed cheeks, brow and a central stone keystone.
    plate('head', (0, 79, -43), (6, 11, 5), 1, (5, 0, 0))
    plate('head', (0, 88, -29), (12, 6, 17), 0, (-8, 0, 0))
    for sign in (-1, 1):
        plate('head', (sign * 11, 85, -39), (18, 4.5, 8), 0, (0, sign * 6, sign * -9))
        plate('head', (sign * 17, 77, -39), (7, 8, 8), 0, (4, sign * -8, sign * 13))
        plate('head', (sign * 22, 83, -19), (7, 11, 17), 1, (0, 0, sign * -10))
        plate('head', (sign * 13, 92, -11), (11, 4, 13), 0, (5, sign * 8, sign * 12))
    for c in by_name['glow_eyes']['cubes']:
        c['origin'][2] -= 2.3

    # Layered chest ribs leave the central crystal unobstructed.
    for sign in (-1, 1):
        for row in range(3):
            plate('torso', (sign * (26 + row % 2), 45 + row * 10, -29 - row % 2),
                  (15, 9.5, 6), 0, (row * 3 - 3, sign * -10, sign * (9 - row * 6)))
            plate('torso', (sign * 35, 46 + row * 10, 2), (7, 8.5, 28), 1 if row == 1 else 0,
                  (0, 0, sign * -8))
        plate('torso', (sign * 20, 71, -9), (25, 5, 22), 0, (5, sign * 8, sign * -9))
    for row in range(2):
        for col in range(3):
            plate('torso', ((col - 1) * 22, 47 + row * 14, 32), (21, 12, 6),
                  0, (6, (col - 1) * 8, (col - 1) * (6 - row * 3)))
    for x, y in ((-17, 57), (17, 57), (0, 73), (0, 41)):
        plate('core_frame', (x, y, -34), (7, 7, 4), 0, (0, 0, 45))

    # Every shoulder and forearm is a layered assembly, rather than one broad slab.
    for sign in (-1, 1):
        arm, fore = 'arm_' + str(sign), 'fore_' + str(sign)
        for col in range(3):
            plate(arm, (sign * (35 + col * 15), 64 + (col % 2) * 2, -26), (18, 13, 7),
                  0, (4, sign * (col - 1) * 9, sign * (col - 1) * 8))
        for col in range(2):
            for depth in range(2):
                plate(arm, (sign * (42 + col * 19), 73 + col, -10 + depth * 24), (22, 5, 24),
                      0, ((depth * 2 - 1) * 9, sign * 6, sign * (col * 2 - 1) * 9))
            plate(arm, (sign * 76, 65, -11 + col * 24), (6, 14, 21), 1 if col else 0,
                  (col * 5, 0, sign * -8))
            plate(arm, (sign * (42 + col * 20), 64, 32), (20, 13, 6), 0, (4, sign * 5, sign * 8))
        for row in range(3):
            for col in range(2):
                plate(fore, (sign * (51 + col * 17 + row % 2), 29 + row * 9, -26.5), (15.5, 8.5, 5.5),
                      0, (row * 3 - 3, sign * 5, sign * (col * 2 - 1) * (4 + row)))
                plate(fore, (sign * 80, 29 + row * 9, -13 + col * 22), (5, 8, 18),
                      1 if row == 1 else 0, (3, 0, sign * -5))
        for col in range(2):
            plate(fore, (sign * (51 + col * 17), 50, -2), (15, 4, 37), 0, (0, sign * 4, sign * 5))
        # The rune sits on the new relief, instead of disappearing inside the enlarged forearm.
        for c in by_name['glow_arm_' + str(sign)]['cubes']:
            c['origin'][2] = -30.5
        for y in (24, 27):
            plate(fore, (sign * 59.4, y, -23), (28, 2, 4), 2, (0, 0, sign * 4))

    # Broken belt plates, shin shields and articulated stone toes break up the lower-body blocks.
    for col in range(3):
        plate('pelvis', ((col - 1) * 20, 32, -26), (18, 9, 6), 0, (5, (col - 1) * 6, (col - 1) * -5))
        plate('pelvis', ((col - 1) * 20, 37, 28), (18, 7, 5), 0, (0, (col - 1) * 6, (col - 1) * 7))
    for sign in (-1, 1):
        leg, foot = 'leg_' + str(sign), 'foot_' + str(sign)
        plate('pelvis', (sign * 31, 33, 3), (6, 11, 27), 1, (0, 0, sign * 5))
        for row in range(2):
            for col in range(2):
                plate(leg, (sign * (13 + col * 14), 18 + row * 10, -22), (12.5, 8.5, 5),
                      0, (5, sign * 5, sign * (col * 2 - 1) * 7))
            plate(leg, (sign * 20, 19 + row * 9, 23), (21, 7, 5), 1, (4, sign * 4, sign * -5))
        for col in range(3):
            plate(foot, (sign * (10 + col * 8), 7, -44), (7, 5, 7), 1, (-5, 0, sign * 3))
        for col in range(2):
            for depth in range(2):
                plate(foot, (sign * (12 + col * 15), 14.5, -24 + depth * 20), (14, 4, 18),
                      0, (depth * 6 - 3, sign * 5, sign * (col * 2 - 1) * 5))
    assert sum(len(b.get('cubes', [])) for b in bones) <= 224
