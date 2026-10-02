# -*- coding: utf-8 -*-
"""
preview_geo_anim.py -- offline preview of a Bedrock geo + one of its animations.

Why this exists: the attack/fly clips of the fighting angel were authored from the bone
list only ("the arm swings"), and the author reported the attack looking wrong
("attack 的面朝方向有点错"). Guessing the sign of a rotation is not acceptable here, so
this script reproduces what GeckoLib will do and draws it:

  * bone hierarchy, pivots, per-bone static rotation, cube rotation
  * the animation's rotation/position keyframes, linearly interpolated
  * orthographic camera, painter's algorithm, flat shading by face normal

Front of the model = -Z (both of the author's angel models put the face cubes at -Z, and
those models render correctly in game). The script paints a RED arrow on the ground on
the -Z side, so "which way is the front" is never a guess in the output image.

Usage:
  python tools/preview_geo_anim.py <geo> <animation> <clip> [--views side,front,three] [--times 0,0.2,..]
  python tools/preview_geo_anim.py                     (the fighting angel attack strip)

ASCII only.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "fightingangel.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "fightingangel.animation.json")

# ---------------------------------------------------------------- math


def mmul(a, b):
    """compose two (R, t) transforms: apply b first, then a."""
    (ar, at), (br, bt) = a, b
    r = [[sum(ar[i][k] * br[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    t = [sum(ar[i][k] * bt[k] for k in range(3)) + at[i] for i in range(3)]
    return (r, t)


def rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def rxyz(x, y, z):
    """GeckoLib order: rotateX * rotateY * rotateZ (so Z is applied first)."""
    return mmul((rx(x), [0, 0, 0]), mmul((ry(y), [0, 0, 0]), (rz(z), [0, 0, 0])))


def apply(tf, p):
    r, t = tf
    return [sum(r[i][k] * p[k] for k in range(3)) + t[i] for i in range(3)]


def translate(v):
    return ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], list(v))


def rot_about(pivot, angles):
    return mmul(translate(pivot), mmul(rxyz(*angles), translate([-c for c in pivot])))


# ---------------------------------------------------------------- model / animation


def load_geo(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    geo = data["minecraft:geometry"][0]
    bones = {}
    order = []
    for b in geo["bones"]:
        bones[b["name"]] = b
        order.append(b["name"])
    return bones, order


def load_anim(path, clip):
    if path == "-" or clip in (None, "-"):
        return {"animation_length": 0.0, "loop": False, "bones": {}}   # bind pose (no animation)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return data["animations"][clip]


def sample(channel, t, length, loop):
    """linear interpolation over a { "<time>": [x,y,z] } channel."""
    if channel is None:
        return [0.0, 0.0, 0.0]
    keys = sorted((float(k), v) for k, v in channel.items())
    if loop and length > 0:
        t = t % length
    if t <= keys[0][0]:
        return list(keys[0][1])
    if t >= keys[-1][0]:
        if loop and length > keys[-1][0]:
            a, b = keys[-1], (length, keys[0][1])
            f = (t - a[0]) / max(1e-6, b[0] - a[0])
            return [a[1][i] + (b[1][i] - a[1][i]) * f for i in range(3)]
        return list(keys[-1][1])
    for i in range(len(keys) - 1):
        a, b = keys[i], keys[i + 1]
        if a[0] <= t <= b[0]:
            f = (t - a[0]) / max(1e-6, b[0] - a[0])
            return [a[1][j] + (b[1][j] - a[1][j]) * f for j in range(3)]
    return list(keys[-1][1])


def world_transforms(bones, anim, t):
    """bone name -> (R, t) in model space (model space: +Y up, front = -Z)."""
    cache = {}

    def build(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        parent = b.get("parent")
        base = build(parent) if parent else ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0])
        pivot = b.get("pivot") or [0, 0, 0]
        stat = b.get("rotation") or [0, 0, 0]
        a = anim["bones"].get(name, {})
        length = anim.get("animation_length", 0.0)
        loop = bool(anim.get("loop"))
        rot = [stat[i] + sample(a.get("rotation"), t, length, loop)[i] for i in range(3)]
        # the author's static bone rotation is usually a list; a dict would be per-axis
        pos = sample(a.get("position"), t, length, loop)
        local = mmul(translate(pivot), mmul(rxyz(*rot), mmul(translate([-c for c in pivot]),
                                                              translate(pos))))
        tf = mmul(base, local)
        cache[name] = tf
        return tf

    for name in bones:
        build(name)
    return cache


def cube_faces(cube):
    o = cube["origin"]
    s = cube["size"]
    rot = cube.get("rotation") or [0, 0, 0]
    center = [o[i] + s[i] / 2.0 for i in range(3)]
    local = rot_about(center, rot)
    corners = []
    for i in (0, 1):
        for j in (0, 1):
            for k in (0, 1):
                corners.append(apply(local, [o[0] + s[0] * i, o[1] + s[1] * j, o[2] + s[2] * k]))
    # index of corner (i,j,k) = 4*i + 2*j + k
    quads = [
        ([0, 1, 3, 2], (0, 0, -1)),   # -X
        ([4, 5, 7, 6], (0, 0, 1)),    # +X
        ([0, 1, 5, 4], (0, -1, 0)),   # -Y
        ([2, 3, 7, 6], (0, 1, 0)),    # +Y
        ([0, 2, 6, 4], (0, 0, -1)),   # -Z
        ([1, 3, 7, 5], (0, 0, 1)),    # +Z
    ]
    return [( [corners[i] for i in idx], n) for idx, n in quads]


def norm(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / l for c in v]


def rot_dir(r, v):
    return [sum(r[i][k] * v[k] for k in range(3)) for i in range(3)]


# ---------------------------------------------------------------- camera / raster


def camera(azimuth, elevation):
    """returns (forward, right, up) unit vectors; forward points from camera to model."""
    a, e = math.radians(azimuth), math.radians(elevation)
    f = norm([math.sin(a) * math.cos(e), -math.sin(e), math.cos(a) * math.cos(e)])
    wup = [0, 1, 0]
    right = norm([f[1] * wup[2] - f[2] * wup[1], f[2] * wup[0] - f[0] * wup[2], f[0] * wup[1] - f[1] * wup[0]])
    up = norm([right[1] * f[2] - right[2] * f[1], right[2] * f[0] - right[0] * f[2], right[0] * f[1] - right[1] * f[0]])
    return f, right, up


def model_bounds(bones, order, anim, t):
    """(min, max) of every cube corner in model space - used to auto-fit big models."""
    lo = [1e9, 1e9, 1e9]
    hi = [-1e9, -1e9, -1e9]
    tfs = world_transforms(bones, anim, t)
    for name in order:
        tf = tfs[name]
        for cube in bones[name].get("cubes", []):
            o, s = cube["origin"], cube["size"]
            for i in (0, 1):
                for j in (0, 1):
                    for k in (0, 1):
                        p = apply(tf, [o[0] + s[0] * i, o[1] + s[1] * j, o[2] + s[2] * k])
                        if FRONT == "+Z":
                            p = flip_y180(p)
                        for c in range(3):
                            lo[c] = min(lo[c], p[c])
                            hi[c] = max(hi[c], p[c])
    return lo, hi


def render(bones, order, anim, t, size, azimuth, elevation, zoom, label,
           center=(0.0, 20.0, 0.0), show_bones=False):
    f, right, up = camera(azimuth, elevation)
    light = norm([-0.4, 0.85, -0.35])

    tris = []
    tfs = world_transforms(bones, anim, t)
    for name in order:
        tf = tfs[name]
        base = TINT.get(name, (235, 232, 222))
        for quad, n in cube_faces_cached(name):
            pts = [apply(tf, p) for p in quad]
            nrm = rot_dir(tf[0], n)
            if FRONT == "+Z":                     # same 180 deg the renderer adds
                pts = [flip_y180(p) for p in pts]
                nrm = flip_dir(nrm)
            tris.append((pts, nrm, base))

    def project(p):
        v = [p[i] - center[i] for i in range(3)]
        x = sum(v[i] * right[i] for i in range(3))
        y = sum(v[i] * up[i] for i in range(3))
        return (size / 2 + x * zoom, size / 2 - y * zoom)

    img = Image.new("RGB", (size, size), (26, 26, 34))
    d = ImageDraw.Draw(img)

    tris.sort(key=lambda e: -sum(sum(p[i] * f[i] for i in range(3)) for p in e[0]) / len(e[0]))
    for pts, nrm, base in tris:
        shade = 0.30 + 0.70 * abs(sum(nrm[i] * light[i] for i in range(3)))
        facing = sum(nrm[i] * (-f[i]) for i in range(3)) >= 0
        if not facing:
            shade *= 0.55
        col = tuple(min(255, int(c * shade)) for c in base)
        d.polygon([project(p) for p in pts], fill=col, outline=(55, 53, 60))

    # ---- skeleton overlay: one dot per bone pivot + a line to its parent ----
    if show_bones:
        ident = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0.0, 0.0, 0.0])
        for name in order:
            b = bones[name]
            parent = b.get("parent")
            piv = b.get("pivot") or [0, 0, 0]
            a = anim["bones"].get(name, {})
            pos = sample(a.get("position"), t, anim.get("animation_length", 0.0), bool(anim.get("loop")))
            here = apply(tfs[parent] if parent else ident, [piv[i] + pos[i] for i in range(3)])
            if FRONT == "+Z":
                here = flip_y180(here)
            if parent:
                there = apply(tfs[parent], bones[parent].get("pivot") or [0, 0, 0])
                if FRONT == "+Z":
                    there = flip_y180(there)
                d.line([project(here), project(there)], fill=(90, 230, 255), width=2)
            px, py = project(here)
            r = 3
            d.ellipse([px - r, py - r, px + r, py + r], fill=(255, 240, 120), outline=(20, 20, 20))

    d.rectangle([0, 0, size - 1, 12], fill=(12, 12, 16))
    d.text((3, 2), label, fill=(230, 230, 240))
    return img


_CACHE = {}

# Which way does THIS model face inside its own geometry?
#   "-Z" = the usual convention (GeckoLib/vanilla map the model's -Z onto the entity's facing)
#   "+Z" = the author's two angel models (the eyes are painted on the +Z face; see
#          tools/face_uv_check.py). Their renderers therefore add a 180 deg yaw, and this
#          preview does the same, so the red arrow always means "the way the entity looks".
FRONT = "+Z"


def flip_y180(p):
    return [-p[0], p[1], -p[2]]


def flip_dir(v):
    return [-v[0], v[1], -v[2]]

# per-bone colours so the limbs are never ambiguous in the output
TINT = {
    "body": (208, 206, 198),
    "robe": (150, 150, 162),
    "head": (243, 216, 130),
    "halo": (250, 240, 170),
    "armRight": (228, 96, 88),        # the striking arm  (red)
    "armRight4": (228, 150, 96),
    "armLeft": (104, 148, 232),       # the other arm     (blue)
    "armLeft2": (120, 168, 240),
    "armLeft3": (136, 184, 246),
    "armLeft4": (152, 196, 250),
    "wingRight": (128, 208, 150),     # wings             (green)
    "wingRightMid": (148, 218, 166),
    "wingRightTip": (168, 228, 182),
    "wingLeft": (86, 176, 128),
    "wingLeftMid": (104, 192, 142),
    "wingLeftTip": (122, 204, 156),
}


def cube_faces_cached(name):
    if name not in _CACHE:
        _CACHE[name] = []
        for cube in _GEO[name].get("cubes", []):
            _CACHE[name].extend(cube_faces(cube))
    return _CACHE[name]


_GEO = {}


def strip(geo_path, anim_path, clip, times, views, out, size=260, zoom=4.4,
          front=None, fit=False, show_bones=False):
    global _GEO, FRONT
    if front:
        FRONT = front
    _GEO, order = load_geo(geo_path)
    anim = load_anim(anim_path, clip)
    cols = len(times) * len(views)
    sheet = Image.new("RGB", (size * cols, size), (26, 26, 34))
    lo, hi = model_bounds(_GEO, order, anim, times[0])
    center = [(lo[i] + hi[i]) / 2.0 for i in range(3)]
    for c, view in enumerate(views):
        az, el, tag = {"side": (-90, 8, "side(-X)"), "side2": (90, 8, "side(+X)"),
                       "front": (180, 8, "front(-Z)"), "back": (0, 8, "back(+Z)"),
                       "top": (180, 78, "top"), "three": (-135, 18, "3/4")}[view]
        z = zoom
        if fit:
            # per-view auto-fit: project the 8 bounding-box corners and pick the zoom
            _, right, up = camera(az, el)
            xs, ys = [], []
            for i in (0, 1):
                for j in (0, 1):
                    for k in (0, 1):
                        p = [lo[0] if i == 0 else hi[0], lo[1] if j == 0 else hi[1],
                             lo[2] if k == 0 else hi[2]]
                        v = [p[m] - center[m] for m in range(3)]
                        xs.append(sum(v[m] * right[m] for m in range(3)))
                        ys.append(sum(v[m] * up[m] for m in range(3)))
            span = max(max(xs) - min(xs), max(ys) - min(ys), 1e-3)
            z = size * 0.88 / span
        for r, t in enumerate(times):
            img = render(_GEO, order, anim, t, size, az, el, z,
                         "%s %s t=%.2f" % (clip, tag, t), center=tuple(center),
                         show_bones=show_bones)
            sheet.paste(img, ((r * len(views) + c) * size, 0))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("preview -> %s (%dx%d) bounds=%s..%s" % (out, sheet.size[0], sheet.size[1],
                                                   [round(v) for v in lo], [round(v) for v in hi]))


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    args = sys.argv[1:]
    opts = {}
    flags = set()
    for a in args:
        if a.startswith("--"):
            if "=" in a:
                k, v = a.split("=", 1)
                opts[k] = v
            else:
                flags.add(a)
    pos = [a for a in args if not a.startswith("--")]
    geo = pos[0] if pos else GEO
    anim = pos[1] if len(pos) > 1 else ANIM
    clip = pos[2] if len(pos) > 2 else "attack"
    times = [float(x) for x in opts.get("--times", "0,0.15,0.3,0.45,0.6,0.8").split(",")]
    views = opts.get("--views", "side,front").split(",")
    out = opts.get("--out", os.path.join("docs", "previews", "anim_%s.png" % clip))
    strip(geo, anim, clip, times, views, out,
          size=int(opts.get("--size", 260)),
          zoom=float(opts.get("--zoom", 4.4)),
          front=opts.get("--front"),
          fit=("--fit" in flags),
          show_bones=("--bones" in flags))


if __name__ == "__main__":
    main()
