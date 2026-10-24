"""Authored cube geometry and material UVs. Inventory artwork is deliberately read-only."""
from pathlib import Path
from PIL import Image, ImageDraw
import base64, hashlib, json, math, uuid

R = Path(__file__).resolve().parents[1]
A = R / 'src/main/resources/assets/tnc'
S = R / 'model-source/equipment'
ELEMENTS = ['water', 'fire', 'lightning', 'wind', 'earth', 'light', 'dark']
PALETTES = {
    'bastion': ('#344552', '#19242e', '#ad8651', '#82949c', '#66d7d3'),
    'astral': ('#304b7a', '#172840', '#bdc7d2', '#647c96', '#76dedc'),
    'runic': ('#694350', '#302936', '#b6975c', '#92989e', '#e1ac83'),
    'wanderer': ('#4c8179', '#263f42', '#c2a375', '#788980', '#a9e3cb'),
    'divine': ('#e3dfcb', '#263a58', '#d6b062', '#a4bdc8', '#8ee5ed'),
}
WAND_COLORS = {
    'water': ('#243a60', '#8eaebd', '#76e5f6'),
    'fire': ('#392c2d', '#bd8a4a', '#ffad36'),
    'lightning': ('#303747', '#aab9cf', '#8dcfff'),
    'wind': ('#385750', '#d0c8a0', '#b9f1de'),
    'earth': ('#524431', '#bb9654', '#edbd60'),
    'light': ('#e0d9ba', '#cba558', '#fff0af'),
    'dark': ('#282837', '#68738b', '#be86dc'),
}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', 'utf8')

def shade(color, amount):
    return tuple(max(0, min(255, round(int(color[i:i+2], 16)*amount))) for i in (1, 3, 5))

class Atlas:
    def __init__(self, palette):
        self.palette = palette
        self.image = Image.new('RGBA', (256, 512), palette['cloth'])
        self.draw = ImageDraw.Draw(self.image)
        self.x = self.y = self.row = self.used = 0
        self.cache = {}

    def cube(self, size, material):
        w, h, d = [max(1, math.ceil(x)) for x in size]
        key = (w, h, d, material)
        if key in self.cache:
            return self.cache[key]
        tw, th = 2*(w+d)+2, h+d+2
        if self.x+tw > 256:
            self.x = 0; self.y += self.row; self.row = 0
        assert self.y+th <= 512, (key, self.y)
        u, v = self.x+1, self.y+1
        self.x += tw; self.row = max(self.row, th); self.used = max(self.used, self.y+th)
        # Material backing covers the fractional-cube UV border as well.
        color = self.palette[material]
        self.draw.rectangle((u-1, v-1, u+tw-2, v+th-2), fill=color)
        for fx, fy, fw, fh in [(u, v+d, d, h), (u+d, v+d, w, h),
                               (u+d+w, v+d, d, h), (u+2*d+w, v+d, w, h),
                               (u+d, v, w, d), (u+d+w, v, w, d)]:
            for yy in range(fh):
                for xx in range(fw):
                    edge = min(xx, yy, fw-1-xx, fh-1-yy)
                    if material in ('metal', 'trim'):
                        f = .77 if edge == 0 else (1.2 if xx == 1 or yy == 1 else 1.0)
                    elif material == 'gem':
                        f = .69 + .52*(1-abs((xx+.5)/fw-.35)) + .12*(1-yy/max(1, fh))
                    else:
                        # Long, quiet folds and a woven seam, not a random-noise atlas.
                        f = .91 + .07*math.cos((xx+.5)/max(1, fw)*math.pi*3)
                        if edge == 0: f *= .9
                        if yy == fh-2 and material == 'cloth': f *= 1.12
                        if (xx+yy)%7 == 0: f *= .98
                    self.draw.point((fx+xx, fy+yy), fill=shade(color, f))
        self.cache[key] = [u, v]
        return [u, v]

    def save(self, path):
        height = max(32, 2**math.ceil(math.log2(max(1, self.used))))
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image.crop((0, 0, 256, height)).save(path)
        return 256, height

def palette(style):
    cloth, lining, trim, metal, gem = PALETTES[style]
    return dict(cloth=cloth, lining=lining, trim=trim, metal=metal, gem=gem,
                leather='#503d32', seam=trim)

class Garment:
    def __init__(self, style, tier, hat):
        self.style, self.tier, self.hat = style, tier, hat
        self.id = f'{style}_{"hat" if hat else "outfit"}_{tier}'
        self.atlas = Atlas(palette(style))
        self.parts = []
        for n in ['head', 'hat', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg']:
            self.part(n)

    def part(self, name, parent='root', pivot=(0, 0, 0), rot=(0, 0, 0)):
        p = next((p for p in self.parts if p['name'] == name), None)
        if p is None:
            p = dict(name=name, parent=parent, pivot=list(pivot), rotation=list(rot), boxes=[])
            self.parts.append(p)
        else:
            p.update(parent=parent, pivot=list(pivot), rotation=list(rot))
        return name

    def box(self, part, pos, size, material='cloth', inflate=0):
        p = next(p for p in self.parts if p['name'] == part)
        p['boxes'].append(dict(uv=self.atlas.cube(size, material), position=list(pos),
                               size=list(size), inflate=inflate))

    def child(self, name, parent, pos, size, mat='cloth', pivot=(0, 0, 0), rot=(0, 0, 0)):
        self.part(name, parent, pivot, rot); self.box(name, pos, size, mat)
        return name

    def hem(self, side, front, length):
        # Each draped quarter is a cloth shell. Trousers remain separate inside.
        n = ('front_' if front else 'back_') + ('left' if side > 0 else 'right')
        self.part(n, 'body', (0, 11.1, 0))
        for j in range(4):
            h = length/4
            outer = 4.45+j*.29
            inner = .46 if front else .06
            x = inner if side > 0 else -outer
            z = -2.95-j*.2 if front else 2.25+j*.19
            self.box(n, (x, j*h, z), (outer-inner, h+.04, .72), 'cloth')
            self.box(n, (x, j*h, z-.11 if front else z+.7), (.28, h+.04, .14), 'trim')
            if j == 3:
                self.box(n, (x, length-.55, z-.08 if front else z+.7), (outer-inner, .5, .16), 'trim')
                if self.tier >= 3:
                    self.box(n, (x+1.2, length-2.1, z-.13 if front else z+.74), (.4, 1.35, .18), 'trim')
        # Flared side seam closes the coat without a solid box around each leg.
        n = ('side_left' if side > 0 else 'side_right')
        if not any(p['name'] == n for p in self.parts):
            self.part(n, 'body', (0, 11.1, 0))
            for j in range(4):
                x = side*(4.15+j*.3) - (.55 if side < 0 else 0)
                self.box(n, (x, j*length/4, -2.6-j*.15), (.6, length/4+.04, 5.3+j*.3), 'cloth')

    def outfit(self):
        heavy, mail = self.style == 'bastion', self.style == 'runic'
        travel, divine = self.style == 'wanderer', self.style == 'divine'
        self.box('body', (-4.25, -.2, -2.35), (8.5, 11.9, 4.7))
        # Sculpted shoulder yoke, an open neckline and separate crossed lapels.
        self.child('collar_back', 'body', (-3.6, -1.1, 1.5), (7.2, 2.8, 1.25), 'lining')
        for s in [-1, 1]:
            self.child('collar_'+str(s), 'body', (-1.7, -.7, -2.75), (2.7, 4.5, .65),
                       'lining' if not heavy else 'metal', pivot=(s*2.1, 0, 0), rot=(0, 0, s*-.25))
            self.child('lapel_trim_'+str(s), 'body', (-1.7, -.7, -3.0), (.32, 4.5, .35),
                       'trim', pivot=(s*2.1, 0, 0), rot=(0, 0, s*-.25))
        self.child('belt', 'body', (-4.5, 9.7, -2.75), (9, 1.6, 5.5), 'leather')
        self.child('buckle', 'body', (-.95, 9.65, -3.25), (1.9, 1.7, .65), 'trim')
        self.child('buckle_core', 'body', (-.42, 10, -3.6), (.84, .95, .4), 'gem')
        if heavy:
            for j, (w, h) in enumerate([(7.6, 3.1), (7.0, 3.1), (6.5, 2.4)]):
                self.child('chest_plate_'+str(j), 'body', (-w/2, 1.2+j*2.8, -3.05-j*.1), (w, h, .85), 'metal')
                self.child('chest_ridge_'+str(j), 'body', (-.25, 1.2+j*2.8, -4.0-j*.1), (.5, h, .3), 'trim')
            for j in range(3):
                self.child('fauld_'+str(j), 'body', (-4.6-j*.2, 11+j*1.2, -3-j*.2),
                           (9.2+j*.4, 1.3, 6+j*.4), 'metal')
        elif mail:
            # Visible rows of rings at the sides of a plum-colored tabard.
            for s in [-1, 1]:
                for y in range(2, 10, 2):
                    self.child(f'mail_{s}_{y}', 'body', (s*3.25-.6, y, -2.65), (1.2, 1.3, .55), 'metal')
            self.child('tabard', 'body', (-2.5, 2, -2.65), (5, 7.4, .6), 'cloth')
        elif travel:
            self.child('cross_strap', 'body', (-.5, .2, -2.9), (1.2, 10.2, .5), 'leather',
                       rot=(0, 0, -.53))
            self.child('satchel', 'body', (4.4, 8.4, -.1), (2.6, 3.9, 3), 'leather')
            self.child('satchel_flap', 'body', (4.25, 8.2, -.35), (2.9, 1, 3.5), 'trim')
        else:
            self.child('front_placket', 'body', (-.5, 3, -2.7), (1, 6.6, .55), 'lining')
            for y in [3.6, 5.8, 8]:
                self.child('clasp_'+str(y), 'body', (-.7, y, -3.05), (1.4, .38, .32), 'trim')
        if divine or self.tier >= 3:
            self.child('breast_jewel', 'body', (-.7, 2.2, -3.5), (1.4, 1.9, .8), 'gem')
        if self.tier >= 2:
            for s in [-1, 1]:
                self.child('embroidered_seam_'+str(s), 'body', (s*3.15-.13, 4, -2.78), (.26, 5.3, .22), 'trim')
                for j in range(self.tier-1):
                    self.child(f'embroidered_leaf_{s}_{j}', 'body', (s*3.1-.48, 4.5+j*1.3, -2.85), (.96, .3, .22), 'trim', rot=(0, 0, s*.16))
        for s in [-1, 1]:
            left = s > 0
            arm, leg = ('left_arm' if left else 'right_arm'), ('left_leg' if left else 'right_leg')
            self.part(arm, pivot=(s*5, 2, 0)); self.part(leg, pivot=(s*1.9, 12, 0))
            ax = -1.25 if left else -3.25
            self.box(arm, (ax, -1.9, -2.3), (4.5, 10.8, 4.6), 'metal' if heavy else 'cloth')
            cuff = 5.1 if heavy or travel or mail else 5.9
            self.child(arm+'_cuff', arm, (ax-(cuff-4.5)/2, 6.7, -cuff/2), (cuff, 2.8, cuff),
                       'metal' if heavy else 'lining')
            self.child(arm+'_cuff_trim', arm, (ax-(cuff-4.5)/2-.08, 9, -cuff/2-.08),
                       (cuff+.16, .5, cuff+.16), 'trim')
            if heavy or divine or self.tier >= 3:
                for j in range(2 if heavy else 1):
                    self.child(arm+'_shoulder_'+str(j), arm, (ax-.65-j*.2, -2.8+j*1.9, -2.9-j*.1),
                               (5.8+j*.2, 2.2, 5.8+j*.2), 'metal' if heavy or mail else 'lining')
                    self.child(arm+'_rim_'+str(j), arm, (ax-.7-j*.2, -1+j*1.9, -3-j*.1),
                               (5.9+j*.2, .45, 6+j*.2), 'trim')
            self.box(leg, (-2.12, -.2, -2.1), (4.24, 11.9, 4.2), 'lining')
            self.child(leg+'_boot', leg, (-2.3, 8, -2.6), (4.6, 4, 5.2), 'leather' if not heavy else 'metal')
            self.child(leg+'_toe', leg, (-2.3, 10.4, -3.4), (4.6, 1.6, 2.5), 'leather' if not heavy else 'metal')
            self.child(leg+'_sole', leg, (-2.4, 11.5, -3.5), (4.8, .5, 6), 'lining')
            self.child(leg+'_boot_cuff', leg, (-2.35, 7.9, -2.65), (4.7, .65, 5.3), 'trim')
            if heavy:
                self.child(leg+'_tasset', leg, (-2.5, -.6, -2.85), (5, 5.3, .95), 'metal')
                self.child(leg+'_knee', leg, (-2.35, 4.8, -2.8), (4.7, 2.9, 1.15), 'metal')
                self.child(leg+'_greave_ridge', leg, (-.35, 7, -3), (.7, 3.6, .65), 'trim')
            else:
                self.hem(s, True, 6.4 if travel else 8 if mail else 12)
                self.hem(s, False, 7.4 if travel else 8.4 if mail else 12)
        if travel or divine or self.tier >= 4:
            self.part('cape', 'body', (0, .2, 2.9))
            for j in range(3):
                w = 8.9+j*.55
                self.box('cape', (-w/2, j*3.2, .2+j*.22), (w, 3.3, .65), 'lining')
                self.box('cape', (-w/2, j*3.2, .85+j*.22), (.35, 3.3, .15), 'trim')
        return self

    def brim(self, width, depth, y, mat='cloth'):
        # Three crossing slabs make an octagonal, layered brim rather than a square tile.
        self.child('brim_core', 'head', (-width/2+1, y, -depth/2), (width-2, .65, depth), mat)
        self.child('brim_sides', 'head', (-width/2, y+.1, -depth/2+1), (width, .5, depth-2), mat)
        self.child('brim_under', 'head', (-width/2+.8, y+.55, -depth/2+.7), (width-1.6, .3, depth-1.4), 'lining')
        self.child('brim_front_edge', 'head', (-width/2+1, y+.15, -depth/2-.1), (width-2, .3, .3), 'trim')

    def headwear(self):
        s = self.style
        if s in ['astral', 'divine']:
            self.brim(13.8, 12.5, -8.9)
            # A leaning, jointed cloth cone and a deliberately off-center folded tip.
            sizes = [(8.3, 3, 8.3), (6.6, 3, 6.6), (4.8, 2.7, 4.8), (3.1, 2.5, 3.1), (1.6, 2.1, 1.8)]
            last = 'head'
            for i, size in enumerate(sizes):
                n = 'crown_'+str(i)
                self.part(n, last, (0 if i == 0 else .15, -8.6 if i == 0 else -sizes[i-1][1]+.15, 0), (0, 0, -.08-i*.025))
                self.box(n, (-size[0]/2, -size[1], -size[2]/2), size)
                last = n
            self.child('crown_band', 'head', (-4.32, -10.9, -4.32), (8.64, 1.5, 8.64), 'lining')
            self.child('brooch_mount', 'head', (-1.3, -11.2, -4.65), (2.6, 2, .7), 'trim')
            self.child('brooch', 'head', (-.72, -10.95, -5), (1.44, 1.5, .8), 'gem')
            if self.tier >= 3:
                self.child('ribbon_tail', 'head', (3.4, -9.4, 3), (1.3, 6, .5), 'trim', rot=(.15, 0, -.18))
            if s == 'divine':
                for i in range(8):
                    a = i*math.pi/4
                    self.child('halo_'+str(i), 'head', (-.38, -1.5, -.4), (.76, 3, .8), 'trim',
                               pivot=(math.cos(a)*6, -13+math.sin(a)*6, 4.6), rot=(0, 0, a))
        elif s == 'bastion':
            self.brim(14, 12, -8.9, 'lining')
            for i, (w, d, y, h) in enumerate([(8.8, 8, -12.6, 3.6), (7.4, 6.9, -13.3, 1.1)]):
                self.child('crown_'+str(i), 'head', (-w/2, y, -d/2), (w, h, d))
            self.child('hatband', 'head', (-4.5, -10.6, -4.1), (9, 1.3, 8.2), 'leather')
            self.child('badge', 'head', (-1, -10.8, -4.6), (2, 1.8, .65), 'trim')
            if self.tier >= 2:
                self.child('feather_stem', 'head', (3.5, -16.5, 0), (.5, 6, .7), 'trim', rot=(0, 0, .2))
                for i in range(3):
                    self.child('feather_'+str(i), 'head', (3.5, -16+i*1.3, -.7), (2.4-i*.3, .65, 1.4), 'lining', rot=(0, 0, -.35))
        elif s == 'runic':
            for i in range(8):
                a = i*math.pi/4
                self.child('circlet_'+str(i), 'head', (-1.9, -1.1, -.5), (3.8, 2.2, 1), 'metal',
                           pivot=(math.sin(a)*4.3, -8.7, math.cos(a)*4.3), rot=(0, a, 0))
            for i, x in enumerate([-3, 0, 3]):
                self.child('point_'+str(i), 'head', (x-.6, -13+(1 if x else 0), -4.55), (1.2, 4, .9), 'trim', rot=(0, 0, x*.025))
            self.child('forehead_crystal', 'head', (-.8, -12.7, -5.05), (1.6, 2.4, 1.25), 'gem')
            if self.tier >= 3:
                for x in [-4.9, 4.9]:
                    self.child('temple_'+str(x), 'head', (x-.45, -8.6, -.7), (.9, 3, 1.4), 'trim')
        else:
            # Hollow hood with a front aperture: no face-covering solid cube.
            self.child('hood_back', 'head', (-4.65, -8.9, 3.5), (9.3, 9.1, 1.5))
            self.child('hood_top', 'head', (-4.7, -9.7, -4.5), (9.4, 1.7, 8.7))
            for side in [-1, 1]:
                self.child('hood_side_'+str(side), 'head', (side*4.1-(1 if side < 0 else 0), -8.4, -4.25), (1, 8.8, 8.3))
                self.child('hood_face_trim_'+str(side), 'head', (side*4.05-.35, -8.4, -4.6), (.7, 8.6, .6), 'trim')
            self.child('hood_lip', 'head', (-4.6, -8.9, -4.8), (9.2, .5, .7), 'trim')
            self.child('hood_cowl_back', 'head', (-4.4, .1, 2.1), (8.8, 2.7, 3.5), 'lining')
            for x in [-3.4, 3.4]:
                self.child('hood_tie_'+str(x), 'head', (x-.2, -.1, -3.6), (.4, 3.4, .5), 'leather')
        return self

    def save(self):
        texture = A/'textures/models/armor'/f'{self.id}.png'
        w, h = self.atlas.save(texture)
        write(A/'models/armor'/f'{self.id}.json', dict(parts=self.parts, texture_width=w, texture_height=h))
        # The common exporter retains animated humanoid bones and exact box UVs.
        import equipment_blockbench as exporter
        exporter.export_armor(self.id, self.parts)
        return dict(id=self.id, parts=len(self.parts), cubes=sum(len(p['boxes']) for p in self.parts), texture=[w, h])

class Wand:
    def __init__(self, element, tier, base=False):
        self.element, self.tier = element, tier
        self.id = 'magic_wand' if base else f'{element}_wand_{tier}'
        wood, metal, gem = WAND_COLORS[element]
        if tier == 1 and element == 'lightning': metal, gem = '#a87b41', '#ffcf79'
        self.atlas = Atlas(dict(cloth=wood, shaft=wood, metal=metal, trim='#caa268' if element not in ['water', 'dark', 'lightning'] else metal,
                                gem=gem, lining='#1d2832', leather='#47372f', bone='#dbd6bb'))
        self.elements = []
        self.top = 22+min(tier, 4)*1.6

    def box(self, name, pos, size, mat='shaft', axis=None, angle=0, origin=None):
        u, v = self.atlas.cube(size, mat)
        w, h, d = size
        # Vanilla box UV convention, converted to 0..16 coordinates after atlas completion.
        faces = dict(west=[u, v+d, u+d, v+d+h], north=[u+d, v+d, u+d+w, v+d+h],
                     east=[u+d+w, v+d, u+2*d+w, v+d+h], south=[u+2*d+w, v+d, u+2*(d+w), v+d+h],
                     up=[u+d, v, u+d+w, v+d], down=[u+d+w, v, u+d+2*w, v+d])
        e = dict(name=name, **{'from':list(pos), 'to':[a+b for a,b in zip(pos, size)]},
                 faces={f:dict(uv=uv, texture='#body') for f, uv in faces.items()})
        if axis and angle:
            e['rotation'] = dict(axis=axis, angle=angle, origin=list(origin or [pos[i]+size[i]/2 for i in range(3)]), rescale=False)
        self.elements.append(e)

    def crystal(self, x, y, z, size=1, mat='gem'):
        for i, (w, h, yy) in enumerate([(2.4, 3.5, -1.7), (1.6, 1.1, 1.8), (.8, .8, 2.9), (1.4, 1.1, -2.8)]):
            self.box('crystal_'+str(len(self.elements)), (x-w*size/2, y+yy*size, z-w*size/2),
                     (w*size, h*size, w*size), mat, 'y', 45)

    def arc(self, cx, cy, radius, start, end, mat='metal', depth=1.1):
        for i, deg in enumerate(range(start, end+1, 45)):
            a = math.radians(deg); x, y = cx+radius*math.cos(a), cy+radius*math.sin(a)
            rot = deg+90
            while rot > 45: rot -= 90
            while rot < -45: rot += 90
            horizontal = abs(math.cos(a+math.pi/2)) > abs(math.sin(a+math.pi/2))
            length = radius*.86
            size = (length, .7, depth) if horizontal else (.7, length, depth)
            self.box('arc_'+str(len(self.elements)), (x-size[0]/2, y-size[1]/2, 8-depth/2), size, mat, 'z', rot)

    def build(self):
        t, e = self.tier, self.element
        c = 17.1 + t*1.7
        self.box('grip', (7.1, 1.2, 7.1), (1.8, 7.6, 1.8), 'leather')
        self.box('shaft', (7.35, 8.5, 7.35), (1.3, c-9.5, 1.3), 'shaft', 'y', 45)
        self.box('shaft_front', (7.58, 8.6, 7.12), (.84, c-9.4, .55), 'shaft')
        for y in [1.1, 2.8, 4.6, 6.4, 8.2, c-1.6]:
            self.box('binding_'+str(y), (6.96, y, 6.96), (2.08, .46, 2.08), 'trim')
        self.box('ferrule', (6.98, .3, 6.98), (2.04, 1, 2.04), 'metal')
        self.box('neck', (6.7, c-1.7, 6.7), (2.6, 1.25, 2.6), 'metal')
        for j, y in enumerate([c-8.1, c-5.6]):
            self.box('shaft_inlay_'+str(j), (7.1, y, 7.1), (1.8, .28, 1.8), 'trim')
        self.box('shaft_rune', (7.71, c-7.1, 6.98), (.58, 1.05, .34), 'gem')
        if t == 1:
            self.crystal(8, c+1.3, 8, .8 if e != 'earth' else 1.0)
            for side in [-1, 1]:
                self.box('claw_'+str(side), (8+side*1.55-.32, c-.4, 7.1), (.64, 2.2, 1.8), 'metal', 'z', -side*22.5)
            if e == 'wind':
                self.box('novice_feather', (9, c+.3, 7.4), (.6, 4.5, 1.2), 'bone', 'z', -22.5)
            return self
        if e == 'water':
            self.crystal(8, c+1.6, 8, 1.0)
            for s in [-1, 1]:
                self.box('dragon_jaw_'+str(s), (8+s*2.5-.5, c-.5, 6.9), (1, 3.4, 2.2), 'metal', 'z', -s*22.5)
                self.box('dragon_horn_'+str(s), (8+s*3.1-.35, c+2, 7.2), (.7, 2.5, 1.6), 'gem', 'z', s*22.5)
                if t >= 4:
                    self.box('frost_spine_'+str(s), (8+s*2.5-.35, c+3.5, 7.4), (.7, 2, 1.2), 'metal', 'z', -s*45)
        elif e == 'fire':
            self.arc(8, c+1.8, 3.2 if t < 4 else 4.0, 0, 315, 'metal', 1.35)
            self.crystal(8, c+1.1, 8, .95)
            self.box('flame_tip', (7.6, c+3.3, 7.3), (.8, 2, 1.4), 'gem', 'z', -22.5)
            if t >= 3:
                for s in [-1, 1]:
                    self.box('sun_flare_'+str(s), (8+s*4.3-.35, c+1.7, 7.5), (.7, 2.3, 1), 'trim', 'z', s*45)
        elif e == 'lightning':
            self.crystal(8, c+1.4, 8, .88)
            for s in [-1, 1]:
                for j, x in enumerate([1.7, 3.2, 3.5]):
                    self.box(f'conductor_{s}_{j}', (8+s*x-.4, c-1+j*1.9, 7.25), (.8, 2.6, 1.5), 'metal', 'z', s*(45 if j%2 == 0 else -45))
                self.box('arc_point_'+str(s), (8+s*3.9-.32, c+4.3, 7.5), (.64, 1.4, 1), 'gem', 'z', -s*22.5)
        elif e == 'wind':
            self.arc(8.2, c+1.8, 3.2, 45, 315, 'bone', 1.3)
            self.crystal(8.5, c+1.8, 8, .75)
            for s in [-1, 1]:
                for j in range(3 if t >= 4 else 2):
                    self.box(f'feather_blade_{s}_{j}', (8+s*(3.2+j*.65)-.35, c-.2+j*1.4, 7.3), (.7, 3.8-j*.5, 1.4),
                             'bone' if j%2 == 0 else 'gem', 'z', -s*45)
        elif e == 'earth':
            self.crystal(8, c+1.4, 8, 1.1)
            for s in [-1, 1]:
                self.box('stone_cradle_'+str(s), (8+s*2.8-1, c-1, 6.7), (2, 3.9, 2.6), 'shaft', 'z', -s*22.5)
                self.box('stone_cap_'+str(s), (8+s*2.6-1, c+2.1, 6.8), (2, 1.3, 2.4), 'metal')
            self.box('arch', (5.5, c+4, 7), (5, .8, 2), 'metal')
        elif e == 'light':
            self.crystal(8, c+1.6, 8, 1.0)
            for i in range(4):
                a = i*math.pi/2
                x, y = 8+math.cos(a)*2.5, c+1.6+math.sin(a)*2.5
                size = (3.4, .7, 1.4) if i%2 == 0 else (.7, 3.4, 1.4)
                self.box('star_ray_'+str(i), (x-size[0]/2, y-size[1]/2, 7.3), size, 'trim')
            if t >= 3:
                for s in [-1, 1]:
                    self.box('star_diagonal_'+str(s), (7.7, c-.9, 7.65), (.6, 5, .7), 'metal', 'z', s*45)
        else:
            self.arc(8.4, c+1.5, 3.5, 45, 270, 'metal', 1.6)
            self.crystal(8.4, c+1.4, 8, .85)
            for j in range(2 if t < 4 else 3):
                self.box('crescent_hook_'+str(j), (10.2+j*.45, c+3.2-j*1.5, 7.1), (.65, 2, 1.8), 'shaft', 'z', -45)
        if t >= 4:
            self.box('relic_knot', (6.5, c-3.3, 6.5), (3, .7, 3), 'metal', 'y', 45)
            for s in [-1, 1]:
                self.box('hanging_seal_'+str(s), (8+s*1.8-.23, c-5, 7.65), (.46, 2.5, .7), 'trim')
        if t == 5:
            # God-tier pieces have a second structure in depth, not a larger flat icon.
            if e == 'water':
                for s in [-1, 1]:
                    self.box('dragon_fin_'+str(s), (8+s*4.1-.45, c-.6, 6.7), (.9, 4.2, 2.6), 'gem', 'z', -s*45)
                self.box('dragon_spinal_crest', (7.45, c-.4, 10.15), (1.1, 4.5, 1.3), 'metal')
            elif e == 'fire':
                for z in [5.3, 10.7]:
                    self.box('solar_axis_'+str(z), (7.65, c-.3, z-.4), (.7, 3.8, .8), 'trim')
                    self.box('solar_orb_'+str(z), (7.25, c+1.3, z-.75), (1.5, 1.5, 1.5), 'gem', 'y', 45)
            elif e == 'lightning':
                for s in [-1, 1]:
                    self.box('sky_conductor_'+str(s), (7.55, c-.4, 8+s*3-.45), (.9, 4.4, .9), 'metal', 'x', s*22.5)
                    self.box('sky_pole_'+str(s), (7.35, c+3, 8+s*3.7-.65), (1.3, 1.3, 1.3), 'gem')
            elif e == 'wind':
                for s in [-1, 1]:
                    self.box('spiral_feather_'+str(s), (6.4, c-.1, 8+s*2.2-.5), (3.2, .75, 1), 'bone', 'y', s*45)
                    self.box('spiral_tip_'+str(s), (8+s*2.3-.4, c+1.9, 8-s*2.1-.4), (.8, 2.5, .8), 'gem', 'z', -s*22.5)
            elif e == 'earth':
                for x, z in [(5, 5), (11, 5), (5, 11), (11, 11)]:
                    self.box('orbiting_stone_'+str(x)+str(z), (x-.8, c+1, z-.8), (1.6, 2, 1.6), 'metal', 'y', 45)
            elif e == 'light':
                for z in [5.1, 10.9]:
                    self.box('star_satellite_'+str(z), (7.3, c+.8, z-.7), (1.4, 1.4, 1.4), 'gem', 'y', 45)
                    self.box('star_halo_axis_'+str(z), (7.8, c-.8, z-.2), (.4, 4.5, .4), 'trim')
            else:
                for s in [-1, 1]:
                    self.box('void_talon_'+str(s), (7.5, c+.2, 8+s*2.8-.5), (1, 4.2, 1), 'metal', 'x', -s*22.5)
                    self.box('void_eye_'+str(s), (7.4, c+.8, 8+s*3.1-.6), (1.2, 1.2, 1.2), 'gem')
        return self

    def save(self):
        texture = A/'textures/item/wands/held'/f'{self.id}.png'
        w, h = self.atlas.save(texture)
        for e in self.elements:
            for face in e['faces'].values():
                face['uv'] = [round(n*16/(w if i%2 == 0 else h), 5) for i, n in enumerate(face['uv'])]
        tip = max(e['to'][1] for e in self.elements)
        grip = [0, -3, 0]  # [8,5,8] in model coordinates, relative to item center.
        fp_scale = min(.58, 10.8/(tip-5))
        display = {}
        for first in [False, True]:
            rotation = [0, -25, -5] if first else [90, 0, 0]
            scale = fp_scale if first else (.85 if self.tier < 3 else .94)
            # Model item transform is T * Rxyz * S around the model's [8,8,8] center.
            rz, ry, rx = [math.radians(rotation[i]) for i in [2, 1, 0]]
            x, y, z = grip[0]*scale, grip[1]*scale, 0
            x, y = x*math.cos(rz)-y*math.sin(rz), x*math.sin(rz)+y*math.cos(rz)
            x, z = x*math.cos(ry)+z*math.sin(ry), -x*math.sin(ry)+z*math.cos(ry)
            y, z = y*math.cos(rx)-z*math.sin(rx), y*math.sin(rx)+z*math.cos(rx)
            translation = [-x-(1.8 if first else 0), -y-(.8 if first else 0), -z]
            for hand in ['righthand', 'lefthand']:
                display[('firstperson_' if first else 'thirdperson_')+hand] = dict(rotation=rotation, translation=translation, scale=[scale]*3)
        display['ground'] = dict(rotation=[0, 0, 15], translation=[0, 3, 0], scale=[.35]*3)
        held = dict(ambientocclusion=False, textures=dict(body=f'tnc:item/wands/held/{self.id}', particle=f'tnc:item/wands/held/{self.id}'),
                    display=display, elements=self.elements, tnc_grip=[8, 5, 8], tnc_tip=[8, tip, 8])
        write(A/'models/item/wands/held'/f'{self.id}.json', held)
        icon = 'tnc:item/magic_wand' if self.id == 'magic_wand' else f'tnc:item/wands/{self.id}'
        write(A/'models/item/wands/icons'/f'{self.id}.json', dict(parent='minecraft:item/generated', textures=dict(layer0=icon)))
        write(A/'models/item'/f'{self.id}.json', dict(loader='forge:separate_transforms',
              textures=dict(particle=f'tnc:item/wands/held/{self.id}'),
              base=dict(parent=f'tnc:item/wands/held/{self.id}'),
              perspectives={key:dict(parent=f'tnc:item/wands/icons/{self.id}') for key in ['gui', 'fixed']}))
        import equipment_blockbench as exporter
        exporter.export_wand(A/'models/item'/f'{self.id}.json')
        return dict(id=self.id, cubes=len(self.elements), grip=[8, 5, 8], tip=tip, texture=[w, h])

def main():
    icons = list((A/'textures/item/wands').glob('*.png'))+list((A/'textures/item/gear').glob('*.png'))+[A/'textures/item/magic_wand.png']
    before = {p.relative_to(A).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in icons}
    armor = [Garment(style, t, hat).headwear().save() if hat else Garment(style, t, hat).outfit().save()
             for style in PALETTES for t in ([5] if style == 'divine' else range(1, 5)) for hat in [False, True]]
    wands = [Wand(e, t).build().save() for e in ELEMENTS for t in range(1, 6)]
    wands.append(Wand('water', 1, True).build().save())
    assert all(hashlib.sha256((A/p).read_bytes()).hexdigest() == digest for p, digest in before.items())
    write(S/'volume-redesign-manifest.json', dict(armor=armor, wands=wands, preserved_icon_sha256=before))
    write(A/'equipment/icon-integrity.json', before)
    print('Built', len(wands), 'volumetric wands and', len(armor), 'tailored wearables; preserved', len(before), 'icon PNGs.')

if __name__ == '__main__':
    main()
