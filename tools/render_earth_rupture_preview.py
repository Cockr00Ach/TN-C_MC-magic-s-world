"""Storyboard of the earth rupture layout, drawn from the real baked model boxes.

Approximate material colours, no particles and no lighting from the game. This is
a geometry/readability preview, not a screenshot. Run from the repo root:
    python tools/render_earth_rupture_preview.py
"""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / 'src/main/resources/assets/tnc/models/projectile'
OUT = ROOT / 'work/earth-rupture-preview.png'

FACES = {'north': [0, 1, 3, 2], 'south': [4, 6, 7, 5], 'west': [0, 2, 6, 4],
         'east': [1, 5, 7, 3], 'down': [0, 4, 5, 1], 'up': [2, 3, 7, 6]}
SHADE = {'up': 1.14, 'south': .84, 'east': .96, 'north': .70, 'west': .78, 'down': .60}
COLORS = {'stone': (150, 92, 45), 'dark': (94, 57, 28), 'vein': (199, 162, 103),
          'soil': (120, 85, 55), 'mud': (129, 98, 74), 'moss': (86, 131, 66),
          'wood': (102, 76, 47), 'leaf': (84, 131, 64)}

_cache = {}


def load(name):
    """Faces in model space, centred on x/z=8 with y=0 at the base (1 unit = 1 block)."""
    if name in _cache:
        return _cache[name]
    data = json.loads((MODEL_DIR / f'{name}.json').read_text(encoding='utf-8'))
    polys = []
    for box in data['elements']:
        a, b = box['from'], box['to']
        pts = [[x, y, z] for z in (a[2], b[2]) for y in (a[1], b[1]) for x in (a[0], b[0])]
        if 'rotation' in box:
            rot = box['rotation']; axis = rot['axis']; ang = math.radians(rot['angle']); o = rot['origin']
            c, s = math.cos(ang), math.sin(ang)
            for p in pts:
                x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
                if axis == 'y': x, z = x * c + z * s, -x * s + z * c
                elif axis == 'z': x, y = x * c - y * s, x * s + y * c
                else: y, z = y * c - z * s, y * s + z * c
                p[:] = [x + o[0], y + o[1], z + o[2]]
        for face, ids in FACES.items():
            key = box['faces'][face]['texture'].lstrip('#')
            color = COLORS.get(key, (142, 142, 133))
            polys.append(([((pts[i][0] - 8) / 16, pts[i][1] / 16, (pts[i][2] - 8) / 16) for i in ids],
                          color, SHADE[face]))
    _cache[name] = polys
    return polys


def i32(v):
    v &= 0xffffffff
    return v - 0x100000000 if v >= 0x80000000 else v


def jitter(index, salt):
    x = i32(index * 374761393 + salt * 668265263)
    ux = x & 0xffffffff
    x = i32((ux ^ (ux >> 13)) * 1274126177)
    ux = x & 0xffffffff
    return ((ux ^ (ux >> 16)) & 0x7fffffff) / 2147483647.0


def rise(t):
    if t <= 0: return 0.0
    if t >= 1: return 1.0
    u = t - 1; c1 = 2.2; c3 = c1 + 1
    return min(1.2, 1 + c3 * u ** 3 + c1 * u ** 2)


def impact(t):
    if t <= 0: return 1.0
    if t >= 1: return 0.0
    return (1 - t) ** 2


def ejecta(tier, columns, field_radius, aura=False):
    count = min(18, 6 + max(1, min(5, tier)) * 2)
    out = []
    for i in range(count):
        column = i % max(1, columns)
        angle = column * math.pi * 2 / max(1, columns) + (jitter(i, 1) - .5) * .42
        base = field_radius * .78 if aura else field_radius * (.42 + .22 * (column % 3) / 2)
        out.append((angle, base + (jitter(i, 2) - .5) * .34, (column % 5) * 2.0 + jitter(i, 3) * 1.1,
                    .13 + jitter(i, 4) * .17, jitter(i, 5) * 360.0, .5 + jitter(i, 6) * .95))
    return out


def burst_columns(spread, giant):
    reach = max(.6, min(12, spread)); count = 14 if giant else 9
    out = []
    for i in range(count):
        angle = i * math.pi * 2 / count + (jitter(i, 21) - .5) * .55
        radius = reach * ((.22 + .75 * jitter(i, 22)) if giant else (.35 + .6 * jitter(i, 23)))
        out.append((angle, radius, i * .42 + jitter(i, 24) * .9, .2 + jitter(i, 25) * .22,
                    jitter(i, 26) * 360.0, .55 + jitter(i, 27) * .95))
    return out


def rot_y(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_z(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def place(poly, pos, scale, yaw=0.0, lean=0.0, spin=0.0, tumble=0.0):
    """Mirrors PoseStack: Y(-yaw) * Z(-lean) * Y(spin) * X(tumble) * S."""
    out = []
    for p in poly:
        v = (p[0] * scale[0], p[1] * scale[1], p[2] * scale[2])
        v = rot_y(v, math.radians(spin))
        v = rot_z(v, math.radians(-lean))
        v = rot_y(v, math.radians(-yaw))
        if tumble: v = rot_x(v, math.radians(tumble))
        out.append((v[0] + pos[0], v[1] + pos[1], v[2] + pos[2]))
    return out


CAM_YAW = math.radians(38)
CAM_PITCH = math.radians(34)


def project(p):
    x = p[0] * math.cos(CAM_YAW) - p[2] * math.sin(CAM_YAW)
    z = p[0] * math.sin(CAM_YAW) + p[2] * math.cos(CAM_YAW)
    return x, -p[1] * math.cos(CAM_PITCH) + z * math.sin(CAM_PITCH), z * math.cos(CAM_PITCH) + p[1] * math.sin(CAM_PITCH)


def scene_objects(kind, age):
    objs = []
    if kind in ('rupture', 'rupture_settled'):
        tier, radius = 4, 5.5
        columns = 5 + tier * 2
        for i in range(columns):
            angle = i * math.pi * 2 / columns + .13
            r = radius * (.42 + .22 * (i % 3) / 2)
            t = max(0.0, min(1.0, (age - (i % 5) * 2) / 10.0))
            h = (.45 + tier * .27) * (1 + .13 * (i % 3))
            w = .35 + tier * .11
            objs.append(('earth_pillar', (math.cos(angle) * r, 0, math.sin(angle) * r),
                         (w, h * rise(t), w), math.degrees(angle), 7.5 * impact(t), i * 29))
        if kind == 'rupture':
            for angle, r, delay, size, spin, lift in ejecta(tier, columns, radius):
                local = age - delay
                if local < 0 or local > 13: continue
                fade = 1 - local / 13
                rr = r + local * .055
                y = .16 + lift * local * .05 - .012 * local * local
                s = size * (.35 + .65 * fade)
                objs.append(('earth_clod', (math.cos(angle) * rr, y, math.sin(angle) * rr),
                             (s, s, s), 0, 0, spin + local * 24, local * 33))
    else:
        giant = kind == 'burst_giant'
        elapsed = age
        spread = 9 if giant else 5
        for angle, r, delay, size, spin, lift in burst_columns(spread, giant):
            local = elapsed - delay
            if local < 0: continue
            t = min(1.0, local / 5.0)
            h = (1.7 if giant else .95) + lift * (1.5 if giant else .7)
            w = size * (1.35 if giant else 1)
            lean = 7 + 9 * impact(t)
            objs.append(('earth_spike_sharp' if (giant or int(spin) % 3) else 'earth_pillar',
                         (math.cos(angle) * r, 0, math.sin(angle) * r),
                         (w, h * rise(t), w), math.degrees(angle), lean, spin))
        for i in range(14):
            a = i * math.pi * 2 / 14 + i * .71
            rr = elapsed * ((.6 if giant else .32) + (i % 5) * .055)
            y = .2 + elapsed * ((.30 if giant else .12) + (i % 3) * .065) - elapsed * elapsed * .012
            s = (.6 if giant else .28) * max(0, 1 - elapsed / (24 if giant else 14))
            objs.append(('earth_clod' if i % 3 else 'earth_shell',
                         (math.cos(a) * rr, y, math.sin(a) * rr), (s, s, s), 0, 0, i * 37 + elapsed * 13))
    return objs


def draw_scene(draw, box, kind, age, title, subtitle, scale_px, radius_hint):
    ox, oy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2 + 40
    # Ground disc and range rings.
    ground = []
    for i in range(72):
        a = i * math.pi * 2 / 72
        x, y, d = project((math.cos(a) * radius_hint, 0, math.sin(a) * radius_hint))
        ground.append((ox + x * scale_px, oy + y * scale_px))
    draw.polygon(ground, fill=(27, 38, 45), outline=(48, 65, 72))
    for ring in (radius_hint * .33, radius_hint * .66):
        pts = []
        for i in range(72):
            a = i * math.pi * 2 / 72
            x, y, _ = project((math.cos(a) * ring, 0, math.sin(a) * ring))
            pts.append((ox + x * scale_px, oy + y * scale_px))
        draw.line(pts + pts[:1], fill=(43, 58, 66), width=1)
    faces = []
    for name, pos, scale, yaw, lean, spin, *rest in scene_objects(kind, age):
        tumble = rest[0] if rest else 0
        for poly, color, shade in load(name):
            pts = place(poly, pos, scale, yaw, lean, spin, tumble)
            proj = [project(p) for p in pts]
            depth = sum(p[2] for p in proj) / len(proj)
            shaded = tuple(min(255, int(c * shade)) for c in color)
            faces.append((depth, [(ox + p[0] * scale_px, oy + p[1] * scale_px) for p in proj], shaded))
    faces.sort(key=lambda f: -f[0])
    for _, pts, color in faces:
        draw.polygon(pts, fill=color, outline=tuple(int(c * .82) for c in color))
    draw.text((box[0] + 14, box[1] + 12), title, fill='#eef4ef', font=FONT)
    draw.text((box[0] + 14, box[1] + 38), subtitle, fill='#a9c0cc', font=SMALL)


FONT = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 20)
SMALL = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 14)
image = Image.new('RGB', (1560, 1080), '#0f171d')
draw = ImageDraw.Draw(image)
draw.text((22, 16), 'EARTH — rupture and detonation storyboard / actual baked model boxes / approximate materials',
          fill='#dfe9e2', font=FONT)
draw.text((22, 44), 'not a game screenshot: dust particles, block debris and camera shake are not drawn',
          fill='#9db4c0', font=SMALL)
draw_scene(draw, (0, 76, 780, 560), 'rupture', 7.0,
           'T4 rupture · 0.35s', 'columns punching through, 14% overshoot, base splinters, outward lean', 40, 6.4)
draw_scene(draw, (780, 76, 1560, 560), 'rupture', 10.5,
           'T4 rupture · 0.53s', 'columns lock in, splinters arc outward and fall back to the ground', 40, 6.4)
draw_scene(draw, (0, 560, 780, 1044), 'burst', 4.0,
           'earth detonation · 0.20s', 'a ring of nine small stone columns erupts around the blast', 52, 6.2)
draw_scene(draw, (780, 560, 1560, 1044), 'burst_giant', 5.5,
           'giant earth core · 0.28s', 'fourteen larger columns, scattered up to nine blocks', 34, 10.5)
OUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUT)
print(f'Saved {OUT.relative_to(ROOT)}')
