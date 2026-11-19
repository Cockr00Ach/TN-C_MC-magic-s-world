"""Build the five lightning staves, their material atlases and Blockbench sources."""

import hashlib
import json
import math
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from equipment_blockbench import OUT, export_wand
from rebuild_sculpted_wands import A, Sculpt, add, cross, mul, rgb, unit
from staff_hold_pose import item_display


PALETTES = (
    ('#88604a', '#ac7049', '#8bdcff', '#44363e', '#e3aa70', '#e4d0b0', '#302330', '#4e878c'),
    ('#615569', '#9cafc8', '#75caff', '#343850', '#e2d8ba', '#dce8f2', '#292c42', '#477eaa'),
    ('#464052', '#685f85', '#ae85ff', '#352a44', '#caaa82', '#c1afdf', '#2c2543', '#64439c'),
    ('#46536c', '#899ebf', '#78dffa', '#30384f', '#c9d6e7', '#ebf6f9', '#313c65', '#447da9'),
    ('#5b4a65', '#c5914e', '#dcaaff', '#40304e', '#ffe0a0', '#fff0cd', '#422d52', '#8c60bc'),
)
CORE_Y = (27.0, 28.1, 28.2, 29.0, 29.7)
TIPS = (32.6, 34.7, 35.5, 37.0, 39.5)
TAU = math.tau


def radial(angle, distance, y, side=0):
    return (8 + math.cos(angle) * distance - math.sin(angle) * side,
            y, 8 + math.sin(angle) * distance + math.cos(angle) * side)


class ForgedStaff(Sculpt):
    def __init__(self, tier):
        super().__init__('lightning', tier)
        self.colors = PALETTES[tier - 1]
        self.core = (8, CORE_Y[tier - 1], 8)

    def collar(self, y, radius, height, material, part):
        bevel = min(.15, height * .22)
        rings = (y - height / 2, y - height / 2 + bevel, y + height / 2 - bevel, y + height / 2)
        self.tube([(8, level, 8) for level in rings],
                  [radius * .83, radius, radius, radius * .83], material, 8, part)

    def motion(self, start, kind, pivot, phase=0):
        for face in self.surfaces[start:]:
            face.update(motion=kind, pivot=list(pivot), phase=phase)

    def fork(self, angle, route, widths, material, name):
        points = [radial(angle, distance, y, side) for distance, y, side in route]
        self.tube(points, widths, material, 6, name)

    def profile(self, angle, route, widths, depth, material, name):
        points = [(distance, y, side) for distance, y, side in route]
        start = len(self.surfaces)
        self.blade(points, widths, depth, material, name)
        for face in self.surfaces[start:]:
            face['vertices'] = [[round(value, 5) for value in radial(angle, *point)]
                                for point in face['vertices']]

    def ellipse(self, center, rx, ry, tube, normal, start, end, material, part, speed=0):
        normal = unit(normal)
        across = unit(cross((0, 1, 0), normal))
        up = cross(normal, across)
        count = max(5, math.ceil(abs(end - start) * 4.0))
        points = []
        for i in range(count + 1):
            angle = start + (end - start) * i / count
            points.append(add(center, add(mul(across, rx * math.cos(angle)), mul(up, ry * math.sin(angle)))))
        index = len(self.surfaces)
        self.tube(points, [tube] * len(points), material, 5, part)
        if speed:
            self.motion(index, 'armillary', center, speed)

    def shaft(self):
        t = self.tier
        core_material = 0 if t <= 2 else 6
        self.tube([(8, -4.8, 8), (8, -2.9, 8), (8, 2.2, 8), (8, 8.3, 8),
                   (8, 14.3, 8), (8, 21.9, 8), (8, 24.3, 8)],
                  [.34, .48, .57, .54, .43, .55, .86], core_material, 8, 'shaft')
        self.gem((8, -4.55, 8), (.58 + .035 * t, 1.0 + .08 * t, .58), 1, 'pommeled_end', 6)
        for y, radius, height in [(1.7, .69, .34), (8.5, .68, .44), (21.0, .72, .53), (23.1, .99, .69)]:
            self.collar(y, radius, height, 1, 'forged_collar')
            self.collar(y + height * .42, radius * .88, .13, 4, 'bright_collar_edge')
        # One continuous helical wrap with a stitched edge.
        for width, radius, material, name in [(.64, .606, 3, 'grip_wrap'), (.085, .637, 1, 'grip_stitch')]:
            for i in range(42):
                a, b = i * TAU / 8, (i + 1) * TAU / 8
                y0, y1 = 2.03 + i * .14, 2.03 + (i + 1) * .14
                self.quad([radial(a, radius, y0), radial(b, radius, y1),
                           radial(b, radius, y1 + width), radial(a, radius, y0 + width)], material, name)
        for sign in (-1, 1):
            angle = sign * math.pi / 2
            self.fork(angle, [(.50, 9.3, 0), (.49, 13.4, .06), (.46, 16.5, -.12), (.57, 21.1, 0)],
                      [.036, .072, .080, .048], 4 if t >= 2 else 1, 'shaft_beading')
        for i in range(t):
            y = 10.2 + i * 2.15
            for angle in (math.pi / 2, -math.pi / 2):
                self.gem(radial(angle, .53, y), (.18, .36, .12), 7, 'socketed_inlay', 4)
        if t >= 3:
            for a in (0, math.pi):
                self.profile(a, [(.32, 15.3, 0), (.65, 17.0, 0), (.81, 20.1, .06), (1.11, 22.4, .10)],
                             [.05, .28, .48, .18], .14, 1, 'chased_throat_rail')

    def apprentice(self):
        for i, angle in enumerate((.31, 2.62, 4.6)):
            self.fork(angle, [(.42, 22.7, 0), (1.16, 25.2, -.24), (1.64, 28.0, .06),
                              (1.34, 30.1 + i * .52, .33), (.60, 31.0 + i * .85, .30)],
                      [.57, .47, .33, .18, .035], 0, 'split_ash_prong')
            self.fork(angle, [(1.40, 26.1, -.17), (1.82, 28.2, .08), (1.51, 29.5 + i * .48, .28)],
                      [.16, .15, .07], 1, 'copper_prong_cap')
        self.cut_stone(self.core, (.82, 1.57, .74), part='first_spark', sides=5, twist=.31)

    def silver(self):
        for angle in (.23, .23 + math.pi):
            self.profile(angle, [(.46, 22.3, 0), (1.72, 25.6, -.07), (2.15, 28.8, 0),
                                 (1.22, 32.1, .30), (.16, 34.7, .16)],
                         [.22, .72, .63, .33, .007], .22, 1, 'silver_lancet')
            self.fork(angle, [(.78, 23.1, -.11), (2.15, 26.8, -.12), (2.22, 29.2, .07), (1.00, 32.55, .30)],
                      [.05, .087, .062, .01], 5, 'lancet_edge_weld')
            self.fork(angle + .84, [(.6, 22.8, 0), (1.52, 25.8, .05), (1.40, 29.8, -.31), (.55, 31.0, -.44)],
                      [.26, .24, .16, .015], 6, 'rear_silver_spline')
        self.cut_stone(self.core, (1.07, 2.53, .86), part='silver_eye_prism', sides=6, twist=.26)
        self.gem((8, 23.6, 8), (.8, 1.08, .64), 1, 'four_facet_socket', 6)

    def obsidian(self):
        for i in range(4):
            angle = i * TAU / 4 + .15
            self.profile(angle, [(.55, 22.2, 0), (1.85, 24.4, -.32), (3.05, 27.4, .06),
                                 (2.91, 29.3, .30), (2.20, 31.5, .56), (1.36, 35.2, .34)],
                         [.32, .89, 1.02, .75, .55, .015], .32, 6, 'obsidian_fulgurite_spire')
            self.profile(angle, [(1.21, 23.9, -.10), (2.53, 25.9, 0), (3.43, 28.0, .11),
                                 (3.13, 29.65, .33), (2.32, 31.7, .52)],
                         [.03, .32, .50, .31, .016], .16, 1, 'lapidary_outer_bevel')
            self.fork(angle, [(1.5, 24.65, -.32), (2.83, 27.03, -.18), (3.19, 28.2, .16), (2.70, 30.15, .36)],
                      [.033, .065, .084, .013], 7, 'violet_fissure_channel')
            self.profile(angle, [(2.02, 25.0, -.32), (3.69, 25.96, -.22), (4.04, 27.64, .12),
                                 (3.37, 28.70, .44)], [.065, .47, .60, .015], .20, 1, 'forged_cage_spur')
        self.collar(23.7, 1.31, .82, 4, 'bronze_cage_joint')
        self.cut_stone(self.core, (1.34, 3.88, 1.23), part='contained_violet_storm', sides=7, twist=.33)

    def armillary(self):
        self.cut_stone(self.core, (.90, 2.83, .9), part='polar_crystal', sides=6, twist=.22)
        for angle in (-.25, math.pi - .25):
            self.profile(angle, [(.44, 22.1, 0), (1.38, 23.7, .12), (2.50, 25.5, .03),
                                 (3.42, 26.4, -.18), (3.67, 27.75, -.35)],
                         [.21, .60, .54, .31, .015], .27, 1, 'armillary_cradle')
        self.ellipse(self.core, 3.49, 5.83, .142, (.21, .20, 1), .36, 6.17, 1, 'fixed_meridian')
        self.ellipse(self.core, 3.49, 5.83, .054, (.21, .20, 1), .42, 6.09, 5, 'meridian_edge')
        self.ellipse(self.core, 4.6, 3.36, .105, (.0, .71, .69), .22, 5.92, 4, 'polar_orbit', .017)
        self.ellipse(self.core, 4.23, 3.46, .075, (.27, -.63, .74), .97, 6.75, 7, 'counter_orbit', -.011)
        for i in range(4):
            angle = i * TAU / 4 + .40
            at = radial(angle, 4.16, 28.85 + .92 * math.sin(angle))
            self.gem(at, (.38, .83, .36), part='floating_ion_' + str(i), sides=5)
        self.gem((8, 36.4, 8), (.38, .57, .40), 4, 'pole_finale', 6)

    def regalia(self):
        self.cut_stone(self.core, (1.41, 4.04, 1.17), part='royal_amethyst', sides=7, twist=.40)
        self.gem((8, 38.6, 8), (.49, .82, .44), part='hanging_throne_star', sides=5)
        for i, angle in enumerate((.03, math.pi + .03, math.pi * .52, -math.pi * .48)):
            large = i < 2
            reach = 1 if large else .70
            peak = 35.7 if large else 34.1
            y = 24.45
            start = len(self.surfaces)
            self.profile(angle, [(.53, y, 0), (1.86 * reach, 25.65, -.12), (3.61 * reach, 27.80, -.16),
                                 (5.20 * reach, 29.85, .06), (4.86 * reach, 32.05, .32),
                                 (3.63 * reach, 34.10, .42), (2.69 * reach, peak, .62)],
                         [.38, .88, .98, .73, .59, .33, .016], .25, 1, 'hinged_gold_banner')
            self.profile(angle, [(1.54 * reach, 25.55, -.19), (3.05 * reach, 28.05, -.22),
                                 (4.08 * reach, 30.38, .09), (3.15 * reach, 32.70, .38),
                                 (1.93 * reach, 34.20, .55)],
                         [.18, .65, .76, .42, .014], .16, 6, 'ebon_inner_scale')
            self.fork(angle, [(1.73 * reach, 25.65, -.35), (3.65 * reach, 27.76, -.38), (5.17 * reach, 29.88, -.09),
                              (4.88 * reach, 32.10, .09), (3.66 * reach, 34.05, .22), (2.74 * reach, peak - .1, .48)],
                      [.044, .10, .132, .095, .055, .013], 4, 'engraved_crown_rim')
            self.motion(start, 'crown', radial(angle, .53, y), angle)
        for i in range(4):
            angle = i * TAU / 4 + .44
            self.gem(radial(angle, 4.04, 35.48 - (i % 2) * 1.21), (.40, .88, .38),
                     part='floating_crown_shard_' + str(i), sides=5)
        self.ellipse((8, 26.8, 8), 3.25, 3.82, .092, (.14, .86, 1), .23, 5.95, 4, 'royal_equator', -.013)
        self.collar(24.2, 1.58, .90, 1, 'crown_hinge')

    def cut_stone(self, center, size, part, sides, twist):
        # Narrow keel, broad shoulder and a small off-axis table catch light on different planes.
        cx, cy, cz = center
        width, height, depth = size
        segments = sides + 2
        rings = []
        for y, radius, drift, turn in [(-1, .08, -.32, -.18), (-.28, .94, -.12, 0),
                                      (.19, 1.18, .06, .34), (.66, .77, .28, .08),
                                      (.91, .35, .46, -.12)]:
            ring = []
            for index in range(segments):
                angle = twist + TAU * index / segments + turn
                cut = radius * (1 + .11 * math.cos(3 * angle + self.tier))
                ring.append((cx + width * (drift + cut * math.cos(angle)),
                             cy + height * (y + .055 * math.sin(angle) * radius),
                             cz + depth * cut * math.sin(angle)))
            rings.append(ring)
        start = len(self.surfaces)
        for level, (lower, upper) in enumerate(zip(rings, rings[1:])):
            for i in range(segments):
                j = (i + 1) % segments
                self.quad([lower[i], upper[i], upper[j], lower[i]], 2, part)
                self.quad([lower[i], upper[j], lower[j], lower[i]],
                          7 if (i + 2 * level) % 5 == 0 else 2, part)
        for ring in (rings[0][::-1], rings[-1]):
            for i in range(1, segments - 1):
                self.quad([ring[0], ring[i], ring[i + 1], ring[0]], 2, part)
        for i in range(1 + self.tier // 2):
            side = -1 if i % 2 == 0 else 1
            self.gem((cx + side * width * (.88 + .17 * i), cy - height * (.15 + .15 * i),
                      cz + depth * (.27 - .34 * i)), (.31 * width, height * (.49 - .06 * i), .31 * depth),
                     2, part + '_intergrowth', 5, twist + i)
        self.motion(start, 'spin', center)

    def sweep_setting(self, start):
        # A high swept spine carries the weight on one side. The opposite jaw stops at the girdle.
        for face in self.surfaces[start:]:
            if self.tier == 4 and face['part'] != 'armillary_cradle':
                continue
            if face.get('motion') not in (None, 'crown') or face['material'] == 2:
                continue
            for point in face['vertices']:
                x, y, z = point
                rise = max(0, y - 24.7)
                side = math.tanh((x - 8) * 1.4)
                spread = (1 - side) * .5
                point[:] = [round(x - .022 * rise ** 1.7 * spread + .07 * rise * max(0, side), 5),
                            round(y - .43 * rise * max(0, side) + .07 * rise * spread, 5),
                            round(z + .085 * rise * spread, 5)]

    def conductor_lines(self):
        # Follow the taper of the shaft, then lift free of the metal into the crown.
        for strand in range(1 + self.tier // 2):
            angle = -1.05 + strand * 2.25
            route = []
            for step in range(17):
                t = step / 16
                radius = .54 + .12 * t + .22 * t ** 4
                route.append(radial(angle + (1 - t) * (2.8 + .4 * self.tier),
                                    radius, 8.8 + 14.5 * t))
            self.tube(route, [.059] * len(route), 4, 5, 'spiral_wire')
            if self.tier >= 2:
                companion = [radial(angle + (1 - j / 16) * (2.8 + .4 * self.tier) + .26,
                                    .57 + .30 * (j / 16) ** 4, 8.8 + 14.5 * j / 16)
                             for j in range(17)]
                self.tube(companion, [.038] * len(companion), 5, 4, 'silver_return_wire')
            reach = 1.02 + self.tier * .34 + .20 * strand
            y = self.core[1] + (1.28 if strand % 2 else -.80)
            for lane in range(1 + self.tier // 3):
                bend = 1 + .32 * lane
                points = [radial(angle, .71, 23.3),
                          radial(angle, reach * .78, 24.2, -.72 * bend),
                          radial(angle, reach * 1.18, y - 2.4, -.82 * bend),
                          radial(angle, reach * 1.09, y - 1.55, -.49 * bend),
                          radial(angle, reach * .83, y, .31)]
                self.tube(points, [.055, .081, .073, .052, .023], 4 if lane == 0 else 5,
                          5, 'openwork_crown_wire')

    def luminous_inlays(self):
        # Broken enamel runs leave the underlying wire visible between pools of light.
        for strand in range(1 + self.tier // 2):
            angle = -1.05 + strand * 2.25
            for section in range(2 + self.tier):
                start = section / (2 + self.tier)
                length = .74 / (2 + self.tier)
                points = []
                for step in range(5):
                    t = start + length * step / 4
                    points.append(radial(angle + (1 - t) * (2.8 + .4 * self.tier),
                                         .62 + .12 * t + .22 * t ** 4, 8.8 + 14.5 * t))
                self.tube(points, [.021, .055, .062, .049, .017], 2, 4, 'lumen_vein')
                self.gem(points[-1], (.093, .16, .093), 2, 'lumen_pin', 4)
        for i in range(min(4, self.tier + 1)):
            angle = -1.05 + i * 2.25
            reach = 1.02 + self.tier * .34 + .20 * i
            y = self.core[1] + (1.28 if i % 2 else -.80)
            points = [radial(angle, reach + .10, y - 2.1, -.22),
                      radial(angle, reach + .42, y - 1.1, .02),
                      radial(angle, reach * .96 + .27, y - .54, .19),
                      radial(angle, reach * .83, y, .31)]
            self.tube(points, [.032, .069, .051, .014], 2, 4, 'lumen_bezel')
            for j in range(1 + self.tier // 2):
                at = radial(angle + .17 * j, 1.0 + .09 * j, 23.1 + j * .24)
                self.gem(at, (.13, .19, .13), 2, 'lumen_pin', 5)

    def electrodes(self):
        # Split metal tongues form a small open eye below each discharge point.
        for i in range(min(self.tier + 1, 4)):
            angle = -1.05 + i * 2.25
            reach = 1.02 + self.tier * .34 + .20 * i
            y = self.core[1] + (1.28 if i % 2 else -.80)
            root = radial(angle, .71, 23.3)
            shoulder = radial(angle, reach, y - 2.1, -.22)
            tip = radial(angle, reach * .83, y, .31)
            self.tube([root, radial(angle, reach * .75, y - 3.4), shoulder],
                      [.20, .15, .11], 1, 6, 'chased_conductor')
            for sign in (-1, 1):
                self.tube([shoulder, radial(angle, reach + sign * .34, y - 1.1, .02),
                           radial(angle, reach * .96 + sign * .20, y - .54, .19), tip],
                          [.105, .080, .059, .024], 4 if sign > 0 else 1, 5, 'split_wire_bezel')
            start = len(self.surfaces)
            self.gem(tip, (.14, .26, .14), 2, 'electrode_' + str(i), sides=5)
            self.motion(start, 'electrode', tip, i)

    def build(self):
        self.shaft()
        head_start = len(self.surfaces)
        (self.apprentice, self.silver, self.obsidian, self.armillary, self.regalia)[self.tier - 1]()
        self.sweep_setting(head_start)
        self.electrodes()
        self.conductor_lines()
        self.luminous_inlays()
        atlas, emission = paint_atlas(self.colors)
        path = A / 'textures/item/wands/held' / (self.name + '.png')
        atlas.save(path)
        emission.save(path.with_name(self.name + '_emission.png'))
        for i, face in enumerate(self.surfaces):
            if face.get('motion') == 'orbit':
                face['motion'] = 'storm_orbit'
            material = face['material']
            variant = (zlib.crc32(face['part'].encode()) + (0 if material == 2 else i % 5)) % 2
            tile_u, tile_v = (material % 4) * 4, (material // 4 + variant * 2) * 4
            old_u, old_v = tile_u, (material // 4) * 4
            face['uv'] = [[round(u - old_u + tile_u, 4), round(v - old_v + tile_v, 4)]
                          for u, v in face['uv']]
            if face['part'].startswith('lumen_'):
                # Sample the narrow luminous enamel strip beside the main crystal panel.
                v = 8 if face['part'] == 'lumen_pin' else 0
                face['uv'] = [[8.03, v + 1], [8.10, v + 1], [8.10, v + 3], [8.03, v + 3]]
        target = A / 'models/item/wands/held' / (self.name + '.json')
        model = json.loads(target.read_text(encoding='utf-8'))
        model.pop('tnc_surface_keys', None)
        model.pop('tnc_surface_metadata', None)
        grip = [8, 5, 8]
        tip = [8, TIPS[self.tier - 1], 8]
        model.update(surfaces=self.surfaces, tnc_design='forged_lightning_stages', tnc_parts=self.parts,
                     tnc_color=list(rgb(self.colors[2])), tnc_core=self.core, tnc_tier=self.tier,
                     tnc_grip=grip, tnc_tip=tip, display=item_display(grip, tip))
        write_geometry(target, model)
        from preview_textured_lightning_staves import render
        icon = A / 'textures/item/wands' / (self.name + '.png')
        painted = render(self.tier, 9, 192, 440, 371, transparent=True)
        rotated = painted.rotate(-38, resample=Image.Resampling.BICUBIC, expand=True)
        shape = rotated.crop(rotated.getbbox())
        shape.thumbnail((59, 59), Image.Resampling.LANCZOS)
        sprite = Image.new('RGBA', (64, 64))
        sprite.paste(shape, ((64 - shape.width) // 2, (64 - shape.height) // 2))
        outline = Image.new('RGBA', sprite.size, (31, 29, 48, 0))
        outline.putalpha(sprite.getchannel('A').filter(ImageFilter.MaxFilter(3)))
        outline.alpha_composite(sprite)
        outline.save(icon)
        model['tnc_reference_sha256'] = hashlib.sha256(icon.read_bytes()).hexdigest()
        write_geometry(target, model)
        export_wand(A / 'models/item' / (self.name + '.json'))
        blockbench = OUT / 'wands' / (self.name + '.bbmodel')
        document = json.loads(blockbench.read_text(encoding='utf-8'))
        blockbench.write_text(json.dumps(document, ensure_ascii=False, separators=(',', ':')) + '\n',
                              encoding='utf-8', newline='\n')
        print(f'{self.name}: {len(self.surfaces)} faces in {len(self.parts)} authored parts')


def write_geometry(path, model):
    fields = {key: value for key, value in model.items() if key != 'surfaces'}
    header = json.dumps(fields, ensure_ascii=False, indent=2)[:-2]
    surfaces = ',\n'.join('    ' + json.dumps(face, ensure_ascii=False) for face in model['surfaces'])
    path.write_text(header + ',\n  "surfaces": [\n' + surfaces + '\n  ]\n}\n', encoding='utf-8', newline='\n')


def paint_atlas(colors):
    y, x = np.indices((128, 128))
    border = np.minimum(np.minimum(x, y), np.minimum(127 - x, 127 - y))
    atlas = Image.new('RGB', (512, 512))
    emission = Image.new('RGBA', (512, 512), (0, 0, 0, 255))
    for material, hex_color in enumerate(colors):
        for variant in range(2):
            grain = ((x * 89 + y * 113 + x * y * 17 + variant * 61) % 71) / 71
            light = .64 + .19 * np.cos((x - 27) * .038) + .12 * grain
            if material == 0:
                light = .63 + .19 * np.sin(x * .38 + np.sin(y * .048) * 1.42) + .13 * grain
            elif material == 3:
                light = .60 + .11 * ((x + y * 3) % 8 < 3) + .14 * grain
            elif material == 2:
                light = .52 + .18 * np.cos((x + variant * 29) * .06) + .12 * np.sin((x * .45 + y) * .044) + .13 * grain
            else:
                light += .08 * (y % 4 == 0)
            light = np.where(border < 2, light * .62, light)
            light = np.where((border >= 3) & (border <= 5), light * 1.23, light)
            color = np.asarray(rgb(hex_color))
            tile = Image.fromarray(np.clip(light[..., None] * color, 0, 255).astype('uint8'))
            line = ImageDraw.Draw(tile)
            px, py = (material % 4) * 128, (material // 4 + variant * 2) * 128
            if material == 2:
                xshift = 9 * variant
                split = [(44 + xshift, 2), (64 + xshift, 31), (56 + xshift, 51), (82 + xshift, 71),
                         (64 + xshift, 95), (78 + xshift, 112), (73 + xshift, 126)]
                line.line(split, fill=tuple(int(.85 * c) for c in color), width=8)
                line.line(split, fill=tuple(int(c * .62 + 96) for c in color), width=3)
                line.line([(23, 3), (21, 74), (36, 125)], fill=tuple(int(.79 * c) for c in color), width=2)
                glow = ImageDraw.Draw(emission)
                split = [(xx + px, yy + py) for xx, yy in split]
                glow.line(split, fill=tuple(int(c * .28) for c in color) + (255,), width=9)
                glow.line(split, fill=tuple(int(c * .75 + 50) for c in color) + (255,), width=3)
            elif material in (1, 4, 5, 6, 7):
                inset = [(104, 17), (78, 17), (46, 53), (70, 53), (40, 92), (30, 110)]
                if variant:
                    inset = [(127 - xx, yy) for xx, yy in inset]
                line.line(inset, fill=tuple(int(.61 * c) for c in color), width=3)
                line.line([(xx + 2, yy) for xx, yy in inset], fill=tuple(min(255, int(c * .91 + 23)) for c in color))
                for xx in (16, 111):
                    for yy in (15, 112):
                        line.polygon([(xx, yy - 3), (xx + 3, yy), (xx, yy + 3), (xx - 3, yy)],
                                     fill=tuple(min(255, int(c * .9 + 27)) for c in color))
            atlas.paste(tile, (px, py))
    for variant in range(2):
        y = variant * 256
        hue = np.asarray(rgb(colors[4] if variant else colors[2]))
        surface = tuple(int(channel) for channel in hue * .76 + 44)
        light = tuple(int(channel) for channel in hue * .65 + 28)
        ImageDraw.Draw(atlas).rectangle((256, y + 29, 260, y + 99), fill=surface)
        ImageDraw.Draw(emission).rectangle((256, y + 29, 260, y + 99), fill=light + (255,))
    return atlas, emission


if __name__ == '__main__':
    for tier in range(1, 6):
        ForgedStaff(tier).build()
