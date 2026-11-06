"""Render the water dragon from WaterDragonFx.dragonGeometry -- the same spine, the same four tube
layers, the same fins / head / horns / whiskers / legs / tail / rings / droplets.

Two render modes, because they answer two different questions:
  * 结构（shaded）  -- depth-sorted alpha blending + lambert shading, so the *shape* is readable;
  * 游戏内观感      -- additive accumulation exactly like RenderType.lightning(), so you see what
                        the game shows (a glowing water body, long whiskers, floating beads).

This is a preview render, not a screenshot. The geometry numbers are copied from the Java.

Usage: python tools/water_dragon_model.py [tier]
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

DEEP = (.04, .22, .55)
CYAN = (.26, .80, 1.0)
LIGHT = (.55, .92, 1.0)
FOAM = (.86, .98, 1.0)
SPINE = 12


def font(size):
    for path in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


# ---------------------------------------------------------------- vector helpers
def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    n = math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
    return (a[0] / n, a[1] / n, a[2] / n) if n > 1e-9 else (0.0, 0.0, 1.0)


def right_of(forward):
    flat = (forward[0], 0.0, forward[2])
    if flat[0] * flat[0] + flat[2] * flat[2] < 1e-9:
        return (1.0, 0.0, 0.0)
    flat = norm(flat)
    return (-flat[2], 0.0, flat[0])


class Scene:
    def __init__(self):
        self.quads = []

    def quad(self, a, b, c, d, color, alpha):
        self.quads.append((a, b, c, d, color[0], color[1], color[2], alpha))

    def tube(self, a, b, r, color, alpha, sides):
        axis = sub(b, a)
        if axis[0] ** 2 + axis[1] ** 2 + axis[2] ** 2 < 1e-9:
            return
        axis = norm(axis)
        rt = right_of(axis)
        up = norm(cross(rt, axis))
        for i in range(sides):
            a0 = i * math.tau / sides
            a1 = (i + 1) * math.tau / sides
            v = add(mul(rt, math.cos(a0) * r), mul(up, math.sin(a0) * r))
            w = add(mul(rt, math.cos(a1) * r), mul(up, math.sin(a1) * r))
            self.quad(add(a, v), add(b, v), add(b, w), add(a, w), color, alpha)

    def disk(self, center, normal, radius, color, alpha, segments=16):
        rt = right_of(normal)
        up = norm(cross(rt, normal))
        for i in range(segments):
            a0 = i * math.tau / segments
            a1 = (i + 1) * math.tau / segments
            p = add(center, add(mul(rt, math.cos(a0) * radius), mul(up, math.sin(a0) * radius)))
            q = add(center, add(mul(rt, math.cos(a1) * radius), mul(up, math.sin(a1) * radius)))
            self.quad(center, p, q, center, color, alpha)

    def ring(self, center, axis, radius, width, color, alpha, segments=18):
        axis = norm(axis)
        rt = right_of(axis)
        up = norm(cross(rt, axis))
        k = max(0.0, 1 - width / max(1e-6, radius))
        for i in range(segments):
            a0 = i * math.tau / segments
            a1 = (i + 1) * math.tau / segments
            o0 = add(mul(rt, math.cos(a0) * radius), mul(up, math.sin(a0) * radius))
            o1 = add(mul(rt, math.cos(a1) * radius), mul(up, math.sin(a1) * radius))
            self.quad(add(center, o0), add(center, o1), add(center, mul(o1, k)),
                      add(center, mul(o0, k)), color, alpha)

    def box(self, center, right, up, forward, hx, hy, hz, color, alpha):
        x, y, z = mul(right, hx), mul(up, hy), mul(forward, hz)
        c = center

        def q(p0, p1, p2, p3, shade):
            self.quad(p0, p1, p2, p3, (color[0] * shade, color[1] * shade, color[2] * shade), alpha)

        q(add(add(add(c, x), y), z), add(add(sub(c, x), y), z),
          add(sub(sub(c, x), y), z), add(sub(add(c, x), y), z), 1.00)
        q(add(sub(add(c, x), y), mul(z, -1)), add(sub(sub(c, x), y), mul(z, -1)),
          add(sub(sub(c, x), mul(y, -1)), mul(z, -1)),
          add(sub(add(c, x), mul(y, -1)), mul(z, -1)), .82)
        q(add(add(add(c, z), y), mul(x, -1)), add(add(sub(c, z), y), mul(x, -1)),
          add(sub(sub(c, z), y), mul(x, -1)), add(sub(add(c, z), y), mul(x, -1)), .90)
        q(add(add(sub(c, z), y), x), add(add(sub(c, z), mul(y, -1)), x),
          add(add(add(c, z), mul(y, -1)), x), add(add(add(c, z), y), x), .90)
        q(add(add(add(c, x), z), y), add(add(sub(c, x), z), y),
          add(add(sub(c, x), mul(z, -1)), y), add(add(add(c, x), mul(z, -1)), y), 1.06)
        q(add(add(sub(c, x), z), mul(y, -1)), add(add(add(c, x), z), mul(y, -1)),
          add(add(add(c, x), mul(z, -1)), mul(y, -1)),
          add(add(sub(c, x), mul(z, -1)), mul(y, -1)), .74)


    def sweep(self, pts, rad, color, alpha, sides):
        n = len(pts)
        t0 = norm(sub(pts[1], pts[0]))
        ref = (1.0, 0.0, 0.0) if abs(t0[1]) > .9 else (0.0, 1.0, 0.0)
        nrm = norm(cross(t0, ref))
        ring = []
        for i in range(n):
            if i == 0:
                t = t0
            elif i == n - 1:
                t = norm(sub(pts[n - 1], pts[n - 2]))
            else:
                t = norm(sub(pts[i + 1], pts[i - 1]))
            flat = sub(nrm, mul(t, sum(nrm[q] * t[q] for q in range(3))))
            ln = math.sqrt(sum(v * v for v in flat))
            nrm = norm(cross(t0, ref)) if ln < 1e-9 else norm(flat)
            bin = norm(cross(t, nrm))
            ring.append([add(pts[i], add(mul(nrm, math.cos(k * math.tau / sides) * rad[i]),
                                        mul(bin, math.sin(k * math.tau / sides) * rad[i])))
                         for k in range(sides)])
        for i in range(n - 1):
            for k in range(sides):
                k2 = (k + 1) % sides
                self.quad(ring[i][k], ring[i + 1][k], ring[i + 1][k2], ring[i][k2], color, alpha)

    def droplet(self, at, size, color, alpha):
        self.disk(at, (0.0, 0.0, 1.0), size, color, alpha, 10)
        self.disk(at, (1.0, 0.0, 0.0), size * .9, color, alpha * .8, 10)


# ---------------------------------------------------------------- the dragon (port of the Java)
def build_dragon(scene, scale, age):
    s = scale
    length = 2.8 * scale
    dir_v = (0.0, 0.0, 1.0)
    right = right_of(dir_v)
    up = norm(cross(right, dir_v))

    spine, radii = [], []
    for i in range(SPINE):
        t = i / (SPINE - 1.0)
        z = (.5 - t) * length
        sway = math.sin(age * .55 - t * 4.6) * .055 * length * (.25 + .75 * t)
        rise = math.sin(age * .42 - t * 5.4) * .028 * length * t
        spine.append(add(add(mul(dir_v, z), mul(right, sway)), mul(up, rise)))
        radii.append(s * (.108 - .052 * t))

    body_pts = 26
    path = [spine_at(spine, i / (body_pts - 1)) for i in range(body_pts)]
    rad = [s * (.108 - .052 * i / (body_pts - 1)) for i in range(body_pts)]
    scene.sweep(path, [x * 2.00 for x in rad], DEEP, .05, 8)
    scene.sweep(path, [x * 1.55 for x in rad], (CYAN[0] * .75, CYAN[1] * .85, CYAN[2]), .13, 6)
    scene.sweep(path, rad, LIGHT, .20, 5)
    scene.sweep(path, [x * .42 for x in rad], FOAM, .40, 4)
    scene.droplet(spine[-1], .10 * s, FOAM, .55)

    previous_tip = None
    for k in range(7):
        t = .12 + k * .10
        base = spine_at(spine, t)
        nxt = spine_at(spine, min(1.0, t + .09))
        h = s * (.30 - .022 * k)
        tip = add(base, mul(up, h))
        tip_next = add(nxt, mul(up, h * .88))
        scene.quad(base, nxt, tip_next, tip, CYAN, .30)
        scene.tube(base, tip, .012 * s, LIGHT, .28, 4)
        if previous_tip is not None:
            scene.tube(previous_tip, tip, .008 * s, FOAM, .34, 4)
        previous_tip = tip

    head(scene, spine, radii, dir_v, right, up, s, age)

    for side in (-1, 1):
        for pair in (0, 1):
            t = .26 if pair == 0 else .58
            idx = int(round(t * (SPINE - 1)))
            root = add(spine_at(spine, t), mul(right, side * radii[idx] * .55))
            knee = add(add(add(root, mul(right, side * .13 * s)), (0.0, -.19 * s, 0.0)),
                       mul(dir_v, -.06 * s))
            foot = add(add(add(knee, mul(right, side * .04 * s)), mul(dir_v, .07 * s)),
                       (0.0, -.09 * s, 0.0))
            scene.tube(root, knee, .045 * s, CYAN, .30, 5)
            scene.tube(knee, foot, .034 * s, CYAN, .26, 5)
            for claw in (-1, 0, 1):
                tip = add(add(add(foot, mul(dir_v, .10 * s)), mul(right, claw * .045 * s)),
                          (0.0, -.02 * s, 0.0))
                scene.tube(foot, tip, .010 * s, FOAM, .40, 4)

    tail = spine[-1]
    for i in range(5):
        a = (i - 2) * .34
        tip = add(add(add(tail, mul(right, math.sin(a) * .24 * s)),
                      mul(up, math.cos(a) * .17 * s)), mul(dir_v, -.12 * s))
        scene.tube(tail, tip, .012 * s, LIGHT, .30, 4)
    scene.quad(add(add(tail, mul(right, -.17 * s)), (0, -.07 * s, 0)),
               add(add(tail, mul(right, .17 * s)), (0, -.07 * s, 0)),
               add(add(add(tail, mul(right, .11 * s)), mul(up, .10 * s)), mul(dir_v, -.10 * s)),
               add(add(add(tail, mul(right, -.11 * s)), mul(up, .10 * s)), mul(dir_v, -.10 * s)),
               CYAN, .10)

    for k in (1, 2, 3):
        center = spine_at(spine, k * .24)
        axis = sub(spine_at(spine, k * .24 - .03), spine_at(spine, k * .24 + .03))
        idx = int(min(SPINE - 1, round(k * .24 * (SPINE - 1))))
        scene.ring(center, axis, radii[idx] * 1.8, .012 * s, LIGHT, .16, 18)
    for k in range(4):
        base = add(spine_at(spine, .18 + k * .20), mul(up, .10 * s))
        bob = math.sin(age * .8 + k * 1.7) * .05 * s
        scene.droplet(add(base, (0.0, bob, 0.0)), .045 * s, FOAM, .48)


def spine_at(spine, t):
    x = max(0.0, min(1.0, t)) * (len(spine) - 1)
    i = int(math.floor(x))
    j = min(len(spine) - 1, i + 1)
    return lerp(spine[i], spine[j], x - i)


def head(scene, spine, radii, dir_v, right, up, s, age):
    hd = spine[0]
    jaw_open = .10 + .07 * math.sin(age * .5)
    scene.sweep([add(hd, mul(dir_v, z * s)) for z in (-.34, -.16, -.03, .09, .19, .30, .345)],
                [x * s for x in (.092, .118, .130, .126, .104, .075, .048)], CYAN, .24, 12)
    hinge = add(add(hd, mul(dir_v, .02 * s)), mul(up, -.055 * s - jaw_open * .05 * s))
    jaw_mid = add(add(hd, mul(dir_v, .17 * s)), mul(up, -.075 * s - jaw_open * .10 * s))
    jaw_tip = add(add(hd, mul(dir_v, .30 * s)), mul(up, -.070 * s - jaw_open * .13 * s))
    scene.sweep([hinge, jaw_mid, jaw_tip], [x * s for x in (.070, .058, .040)],
                (CYAN[0] * .85, CYAN[1], CYAN[2]), .22, 8)
    for side in (-1, 1):
        a = add(add(add(hd, mul(dir_v, .05 * s)), mul(right, side * .055 * s)), mul(up, .095 * s))
        b = add(add(add(hd, mul(dir_v, .21 * s)), mul(right, side * .070 * s)), mul(up, .072 * s))
        scene.tube(a, b, .024 * s, CYAN, .26, 5)
    for side in (-1, 1):
        eye = add(add(add(hd, mul(right, side * .105 * s)), mul(up, .055 * s)), mul(dir_v, .135 * s))
        n = mul(right, side)
        scene.disk(eye, n, .058 * s, CYAN, .30, 12)
        scene.disk(add(eye, mul(n, .002)), n, .027 * s, FOAM, .85, 10)
    for side in (-1, 1):
        for pair in (0, 1):
            start = add(add(add(hd, mul(right, side * (.080 + pair * .035) * s)),
                            mul(up, (.100 - pair * .030) * s)), mul(dir_v, -(.02 + pair * .05) * s))
            tip = add(add(add(start, mul(right, side * (.14 + pair * .05) * s)),
                          mul(up, (.20 - pair * .06) * s)), mul(dir_v, -(.24 + pair * .10) * s))
            horn(scene, start, tip, s * (1 if pair == 0 else .8), age + side * .7)
    for side in (-1, 1):
        root = add(add(add(hd, mul(dir_v, .27 * s)), mul(right, side * .050 * s)),
                   mul(up, -.010 * s))
        whisker(scene, root, dir_v, right, up, side, s * 1.05, age + side * 1.3)
    for side in (-1, 1):
        for i in range(4):
            z = (.265 - i * .052) * s
            at = add(add(add(hd, mul(dir_v, z)), mul(right, side * (.016 + i * .014) * s)),
                     mul(up, -.050 * s))
            scene.tube(at, add(at, (0, -.028 * s, 0)), .009 * s, FOAM, .45, 4)
            low = add(add(add(hd, mul(dir_v, z - .01 * s)),
                          mul(right, side * (.014 + i * .013) * s)),
                      mul(up, -.060 * s - jaw_open * .10 * s))
            scene.tube(low, add(low, (0, .024 * s, 0)), .008 * s, FOAM, .40, 4)
    for side in (-1, 1):
        for i in range(3):
            root = add(add(add(hd, mul(right, side * (.115 + i * .012) * s)),
                           mul(up, (.020 - i * .035) * s)), mul(dir_v, -.07 * s))
            tip = add(add(add(root, mul(right, side * .20 * s)), mul(up, .04 * s)),
                      mul(dir_v, -.20 * s))
            scene.quad(root, add(root, mul(dir_v, -.09 * s)), tip, tip, CYAN, .12)
            scene.tube(root, tip, .010 * s, LIGHT, .24, 4)


def horn(scene, start, tip, s, phase):
    prev = start
    for i in range(1, 6):
        t = i / 5.0
        p = add(lerp(start, tip, t), (0.0, math.sin(phase + t * 2.2) * .012 * s, 0.0))
        scene.tube(prev, p, s * (.030 - .022 * t), LIGHT, .30, 5)
        prev = p


def whisker(scene, root, dir_v, right, up, side, s, phase):
    prev = root
    for i in range(1, 15):
        t = i / 14.0
        wave = math.sin(phase + t * 4.4) * .10 * s * t
        p = add(add(add(root, mul(right, side * (.10 + .34 * t) * s + wave)),
                    mul(dir_v, -1.15 * t * s)),
                mul(up, -.16 * t * s + math.cos(phase + t * 3.6) * .05 * s * t))
        scene.tube(prev, p, s * (.012 - .007 * t), FOAM, .40, 4)
        prev = p


# ---------------------------------------------------------------- rasteriser
class Camera:
    def __init__(self, pos, target, fov_deg, w, h):
        self.pos = pos
        f = norm(sub(target, pos))
        r = norm(cross(f, (0.0, 1.0, 0.0)))
        u = cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.w, self.h = w, h
        self.fl = (h * .5) / math.tan(math.radians(fov_deg) * .5)

    def project(self, p):
        d = sub(p, self.pos)
        x = d[0] * self.r[0] + d[1] * self.r[1] + d[2] * self.r[2]
        y = d[0] * self.u[0] + d[1] * self.u[1] + d[2] * self.u[2]
        z = d[0] * self.f[0] + d[1] * self.f[1] + d[2] * self.f[2]
        if z <= .05:
            return None
        return (self.w * .5 + self.fl * x / z, self.h * .5 - self.fl * y / z, z)


def rasterize(quads, cam):
    out = []
    for (a, b, c, d, r, g, bl, al) in quads:
        pts, depth, ok = [], 0.0, True
        for p in (a, b, c, d):
            q = cam.project(p)
            if q is None:
                ok = False
                break
            pts.append(q)
            depth += q[2]
        if not ok:
            continue
        n = norm(cross(sub(b, a), sub(d, a)))
        out.append((pts, depth / 4.0, n, r, g, bl, al))
    return out


def poly_mask(pts, w, h):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    x0 = max(0, int(math.floor(min(xs))))
    y0 = max(0, int(math.floor(min(ys))))
    x1 = min(w, int(math.ceil(max(xs))) + 1)
    y1 = min(h, int(math.ceil(max(ys))) + 1)
    if x1 <= x0 or y1 <= y0:
        return None
    m = Image.new("L", (x1 - x0, y1 - y0), 0)
    ImageDraw.Draw(m).polygon([(p[0] - x0, p[1] - y0) for p in pts], fill=255)
    return (x0, y0, x1, y1, np.asarray(m, dtype=np.float32) / 255.0)


def render_additive(scene, cam, exposure=.90, bloom=.28):
    w, h = cam.w, cam.h
    buf = np.zeros((h, w, 3), dtype=np.float32)
    for pts, depth, n, r, g, bl, al in rasterize(scene.quads, cam):
        m = poly_mask(pts, w, h)
        if m is None:
            continue
        x0, y0, x1, y1, mm = m
        buf[y0:y1, x0:x1, 0] += mm * (r * al)
        buf[y0:y1, x0:x1, 1] += mm * (g * al)
        buf[y0:y1, x0:x1, 2] += mm * (bl * al)
    img = Image.fromarray(np.clip(buf * 255, 0, 255).astype(np.uint8))
    glow = np.asarray(img.filter(ImageFilter.GaussianBlur(7)), dtype=np.float32)
    hot = np.asarray(img, dtype=np.float32) + glow * bloom
    out = 1.0 - np.exp(-hot / 255.0 * exposure)
    return (np.clip(out, 0, 1) * 255).astype(np.uint8)


def render_shaded(scene, cam, bg=(10, 22, 36), light=(.35, .78, -.52), boost=2.4):
    w, h = cam.w, cam.h
    buf = np.zeros((h, w, 3), dtype=np.float32)
    buf[:] = np.array(bg, dtype=np.float32) / 255.0
    l = norm(light)
    for pts, depth, n, r, g, bl, al in sorted(rasterize(scene.quads, cam), key=lambda q: -q[1]):
        lam = .42 + .72 * abs(n[0] * l[0] + n[1] * l[1] + n[2] * l[2])
        a = min(.96, al * boost)
        m = poly_mask(pts, w, h)
        if m is None:
            continue
        x0, y0, x1, y1, mm = m
        sl = buf[y0:y1, x0:x1]
        aa = (mm * a)[..., None]
        col = np.array([min(1.0, r * lam), min(1.0, g * lam), min(1.0, bl * lam)])
        sl[:] = sl * (1 - aa) + col * aa
    img = Image.fromarray((np.clip(buf, 0, 1) * 255).astype(np.uint8))
    glow = np.asarray(img.filter(ImageFilter.GaussianBlur(6)), dtype=np.float32)
    base = np.asarray(img, dtype=np.float32)
    out = np.clip(base * .82 + glow * .28, 0, 255)
    return out.astype(np.uint8)


# ---------------------------------------------------------------- compose
PANEL_W, PANEL_H = 700, 470
TIER = int(sys.argv[1]) if len(sys.argv) > 1 else 5
SCALE = 1.08 + .08 * (TIER - 1)
AGE = 12.0
LEN = 2.8 * SCALE

panels = [
    ("① 侧视 · 结构", (5.55, .28, 0.0), (0.0, -.05, 0.0), "shaded"),
    ("② 四分之三视 · 结构", (3.1, 1.55, -3.5), (0.0, -.02, 0.0), "shaded"),
    ("③ 四分之三视 · 游戏内观感（加色）", (3.1, 1.55, -3.5), (0.0, -.02, 0.0), "add"),
    ("④ 龙头特写 · 结构", (1.75, .72, 3.05), (0.0, .05, 1.55), "shaded"),
]

header = 62
W = PANEL_W * 2 + 48
H = header + PANEL_H * 2 + 20
canvas = Image.new("RGB", (W, H), (7, 13, 21))
draw = ImageDraw.Draw(canvas)
draw.text((20, 12), "水龙 · 实机几何预览（t%d，全长 %.2f 格，按 1:1 比例渲染）" % (TIER, LEN),
          fill=(228, 244, 255), font=font(23))
draw.text((20, 40), "① ② ④ 是同一份几何的着色渲染（好判断形体）；③ 是加色混合下的实际观感 —— "
                    "游戏里就是这种发光的水。", fill=(122, 168, 196), font=font(14))

for idx, (title, pos, target, mode) in enumerate(panels):
    cam = Camera(pos, target, 34.0, PANEL_W, PANEL_H)
    scene = Scene()
    build_dragon(scene, SCALE, AGE)
    arr = render_shaded(scene, cam) if mode == "shaded" else render_additive(scene, cam)
    x0 = 20 + (idx % 2) * (PANEL_W + 8)
    y0 = header + (idx // 2) * (PANEL_H + 8)
    canvas.paste(Image.fromarray(arr), (x0, y0))
    d2 = ImageDraw.Draw(canvas)
    d2.rectangle([x0, y0, x0 + PANEL_W - 1, y0 + PANEL_H - 1], outline=(32, 60, 88))
    d2.text((x0 + 14, y0 + 10), title, fill=(206, 232, 246), font=font(16))
    if idx == 0:
        p0 = cam.project((0.0, -.52, -1.55))
        p1 = cam.project((0.0, -.52, -.55))
        if p0 and p1:
            d2.line([(x0 + p0[0], y0 + p0[1]), (x0 + p1[0], y0 + p1[1])],
                    fill=(120, 200, 230), width=2)
            d2.text((x0 + p0[0] + 6, y0 + p0[1] - 16), "1 格", fill=(140, 210, 235), font=font(14))

canvas.save(r"D:\ModTest\docs\spells\水法读条水龙-model.png")
print("saved", canvas.size, "quads:", len(scene.quads))
