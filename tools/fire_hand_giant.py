"""火手链：**巨人手掌**（带岩壳/裂纹/火舌细节）姿势预览。

    python tools/fire_hand_giant.py      # → docs/spells/火手-model.png

尺寸单位 = 掌长；游戏里整体再乘约 2.2 格（t1 小一点，t5 更大）。

    掌长 : 掌宽 : 掌厚 ≈ 1.0 : 1.05 : 0.42      人手是 1 : 0.88 : 0.30 —— 巨人的掌更方更厚
    前臂半径 ≈ 0.44 掌长（人手约 0.22），从地里顶出来
    指根半径 ≈ 0.14 掌长（人手 0.10），关节鼓成骨节，指缝几乎并拢

表面细节（游戏里用顶点色做同样的层次，不需要贴图文件）：
    焦黑底 → 暗红壳面 → 岩浆缝；岩壳板边缘亮一线；裂纹分叉、越靠中心越白；火舌从缝里窜出
"""

import math
import pathlib

import numpy as np
from PIL import Image

# ---------------------------------------------------------------- 数学
def v(*a):
    return np.array(a, dtype=np.float64)


def nrm(a):
    n = np.linalg.norm(a)
    return a / (n if n > 1e-9 else 1.0)


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], float)


def bez(p0, p1, p2, n):
    return [((1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2)
            for t in (i / n for i in range(n + 1))]


# ---------------------------------------------------------------- 材质
CRUST = (0.062, 0.046, 0.042)
CRUST2 = (0.175, 0.100, 0.076)
PLATE = (0.105, 0.070, 0.058)
SEAM = (1.00, 0.28, 0.045)
HOT = (1.00, 0.52, 0.10)
WHITE = (1.00, 0.93, 0.72)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


class Mesh:
    def __init__(self):
        self.quads = []

    def add(self, a, b, c, d, col, emit=0.0, normal=None):
        n = normal if normal is not None else nrm(np.cross(b - a, c - a) + np.cross(c - a, d - a))
        self.quads.append((a, b, c, d, nrm(n), col, emit))

    def tube(self, pts, radii, sides=16, c0=CRUST, c1=CRUST2, emit=0.0,
             seam_from=0.30, seam_str=0.40, rough=0.05):
        frames = []
        for i, p in enumerate(pts):
            d = (pts[1] - p) if i == 0 else (p - pts[-2]) if i == len(pts) - 1 else (pts[i + 1] - pts[i - 1])
            d = nrm(d)
            ref = v(0, 1, 0) if abs(d[1]) < 0.85 else v(1, 0, 0)
            right = nrm(np.cross(ref, d))
            up = np.cross(d, right)
            frames.append((p, right, up))
        n = len(pts)
        for i in range(n - 1):
            p0, r0, u0 = frames[i]
            p1, r1, u1 = frames[i + 1]
            for s in range(sides):
                a0 = 2 * math.pi * s / sides
                a1 = 2 * math.pi * (s + 1) / sides
                j0 = 1.0 + rough * math.sin(s * 3.1 + i * 1.7) + 0.6 * rough * math.sin(s * 7.3 - i)
                j1 = 1.0 + rough * math.cos(s * 2.7 + i * 2.3) + 0.6 * rough * math.cos(s * 6.1 + i)
                q0 = p0 + r0 * math.cos(a0) * radii[i] * j0 + u0 * math.sin(a0) * radii[i] * j0
                q1 = p0 + r0 * math.cos(a1) * radii[i] * j0 + u0 * math.sin(a1) * radii[i] * j0
                q2 = p1 + r1 * math.cos(a1) * radii[i + 1] * j1 + u1 * math.sin(a1) * radii[i + 1] * j1
                q3 = p1 + r1 * math.cos(a0) * radii[i + 1] * j1 + u1 * math.sin(a0) * radii[i + 1] * j1
                nrad = nrm(r0 * math.cos((a0 + a1) * 0.5) + u0 * math.sin((a0 + a1) * 0.5))
                t = i / max(1, n - 2)
                col = mix(c0, c1, min(1.0, t * 0.85 + 0.15 * abs(math.sin(s * 2.0 + i * 1.3))))
                e = 0.0
                if t > seam_from and (s * 3 + i * 5) % 11 == 0:
                    col = mix(col, SEAM, seam_str)
                    e = 0.5
                self.add(q0, q1, q2, q3, col, e, nrad)

    def ball(self, c, r, scale=(1, 1, 1), col=CRUST2, emit=0.0, seg=16, rings=11):
        sx, sy, sz = scale
        for i in range(rings):
            t0 = math.pi * i / rings - math.pi / 2
            t1 = math.pi * (i + 1) / rings - math.pi / 2
            for s in range(seg):
                a0 = 2 * math.pi * s / seg
                a1 = 2 * math.pi * (s + 1) / seg

                def pt(t, a):
                    n = v(math.cos(t) * math.cos(a), math.sin(t), math.cos(t) * math.sin(a))
                    return c + v(n[0] * r * sx, n[1] * r * sy, n[2] * r * sz), n
                p0, n0 = pt(t0, a0)
                p1, _ = pt(t0, a1)
                p2, _ = pt(t1, a1)
                p3, _ = pt(t1, a0)
                self.add(p0, p1, p2, p3, col, emit, n0)

    def slab(self, y0, y1, layers, half_w, half_t, col0, col1, n=20, ridge=0.6):
        rows = []
        for i in range(layers + 1):
            t = i / layers
            y = y0 + (y1 - y0) * t
            w = half_w * (0.72 + 0.32 * math.sin(math.pi * min(1.0, t * 1.35)) + 0.16 * t)
            th = half_t * (0.90 + 0.26 * math.sin(math.pi * t))
            ring = []
            for s in range(n):
                a = 2 * math.pi * s / n
                ca, sa = math.cos(a), math.sin(a)
                px = math.copysign(abs(ca) ** (2 / 3.2), ca) * w
                pz = math.copysign(abs(sa) ** (2 / 3.2), sa) * th
                if sa > 0:
                    pz *= 1.0 + ridge * 0.18 * math.sin(math.pi * t)
                ring.append(v(px, y, pz))
            rows.append(ring)
        for i in range(layers):
            t = i / layers
            col = mix(col0, col1, t)
            for s in range(n):
                s2 = (s + 1) % n
                p = rows[i][s]
                self.add(rows[i][s], rows[i][s2], rows[i + 1][s2], rows[i + 1][s], col, 0.0,
                         nrm(v(p[0] * 1.5, 0.0, p[2])))

    def plate(self, c, fwd, side, w, h, col=PLATE):
        f = nrm(fwd) * h * 0.5
        s = nrm(side) * w * 0.5
        self.add(c - s - f, c + s - f, c + s + f, c - s + f, col, 0.0)
        edge = mix(SEAM, HOT, 0.35)
        for p0, p1 in ((-s - f, s - f), (s - f, s + f), (s + f, -s + f), (-s + f, -s - f)):
            self.add(c + p0, c + p1, c + p1 * 0.94, c + p0 * 0.94, edge, 0.7)


FINGERS = [
    ("食指", -0.455, 0.64, 0.112, 9, 0.02),
    ("中指", -0.155, 0.68, 0.118, 0, 0.07),
    ("无名指", 0.155, 0.64, 0.112, -9, 0.13),
    ("小指", 0.430, 0.54, 0.096, -16, 0.20),
]
SEG = (0.44, 0.30, 0.20)


def digit(base, length, radius, spread, curl):
    d = nrm(v(math.sin(math.radians(spread)), 1.0, 0.05))
    pts = [base]
    radii = [radius]
    bends = (math.radians(92) * curl, math.radians(104) * curl, math.radians(58) * curl)
    for i in range(3):
        d = nrm(rz(-bends[i] * 0.92) @ d)
        pts.append(pts[-1] + d * (length * SEG[i]))
        radii.append(radius * (0.90, 0.78, 0.63)[i])
    return pts, radii


def build_hand(curl, rise, spread=0.0, tremble=0.0, time=0.0):
    m = Mesh()
    wrist_y = -0.70 + 0.95 * rise
    rng = np.random.default_rng(5)

    arm = [v(0, wrist_y - 1.45, 0), v(0, wrist_y - 1.05, 0), v(0, wrist_y - 0.60, 0),
           v(0, wrist_y - 0.24, 0)]
    m.tube(arm, [0.46, 0.42, 0.375, 0.34], sides=20, seam_from=0.2, seam_str=0.45, rough=0.06)
    m.ball(v(0, wrist_y - 0.02, 0), 0.35, (1.02, 0.58, 0.95), mix(CRUST, CRUST2, 0.45))

    m.slab(wrist_y - 0.04, wrist_y + 0.98, 16, 0.385, 0.205, CRUST, CRUST2)
    m.ball(v(-0.35, wrist_y + 0.34, 0.13), 0.205, (1.0, 1.35, 0.78), mix(CRUST, CRUST2, 0.52))
    m.ball(v(0.32, wrist_y + 0.22, 0.07), 0.16, (1.0, 1.45, 0.74), mix(CRUST, CRUST2, 0.46))
    for x in (-0.365, -0.12, 0.12, 0.36):
        m.ball(v(x, wrist_y + 1.00, -0.01), 0.158, (1.06, 1.0, 1.06), mix(CRUST, CRUST2, 0.72))

    for row in range(5):
        for col in range(4):
            x = -0.30 + col * 0.20 + rng.uniform(-0.02, 0.02)
            y = wrist_y + 0.24 + row * 0.17
            z = 0.205 * (1.0 - 0.55 * (x / 0.42) ** 2) + 0.012
            m.plate(v(x, y, z), v(0, 0, 1), v(1, 0, 0),
                    rng.uniform(0.10, 0.15), rng.uniform(0.09, 0.13),
                    mix(PLATE, CRUST2, rng.uniform(0.0, 0.5)))

    knuckle_y = wrist_y + 1.00
    for name, x, length, rad, spread_deg, delay in FINGERS:
        c = max(0.12, min(1.0, (curl - delay) / max(0.05, 1.0 - delay)))
        base = v(x * (1.0 + 0.08 * spread), knuckle_y, 0.0)
        pts, radii = digit(base, length * (1.0 + 0.08 * spread), rad, spread_deg * (1.0 + spread), c)
        m.ball(pts[0], rad * 1.06, (1.05, 0.92, 1.05), mix(CRUST, CRUST2, 0.66))
        dense, dr = [], []
        for i in range(len(pts) - 1):
            seg = bez(pts[i], (pts[i] + pts[i + 1]) * 0.5, pts[i + 1], 6)
            dense.extend(seg if i == 0 else seg[1:])
            dr.extend([radii[i]] * (len(seg) if i == 0 else len(seg) - 1))
        dr = list(np.interp(np.linspace(0, len(dr) - 1, len(dense)), range(len(dr)), dr))
        m.tube(dense, dr, sides=16, seam_from=0.28, seam_str=0.42, rough=0.045)
        for i in (1, 2):
            m.ball(pts[i], radii[i] * 1.03, (1.10, 0.86, 1.10), mix(CRUST, CRUST2, 0.70))
        fwd = nrm(pts[3] - pts[0])
        side = nrm(np.cross(fwd, v(0, 1, 0)))
        for k in range(4):
            c0 = pts[0] + (pts[3] - pts[0]) * (0.12 + k * 0.26)
            m.plate(c0 + v(0, 0, radii[0] * 0.92), fwd, side, radii[0] * 1.05, length * 0.16,
                    mix(PLATE, CRUST2, 0.3 + 0.1 * k))
        tip, tdir = pts[3], nrm(pts[3] - pts[2])
        m.ball(tip + tdir * 0.01, radii[3] * 0.95, (1.1, 0.75, 1.0), mix(CRUST2, WHITE, 0.28), 0.12)

    tc = max(0.18, min(1.0, curl * 1.15))
    tb = v(-0.37, wrist_y + 0.40, 0.11)
    d1 = nrm(v(-0.40, 0.64, 0.66))
    p1 = tb + d1 * 0.40
    d2 = nrm(rz(-math.radians(46) * tc) @ nrm(d1 + v(0.34, 0.10, -0.16)))
    p2 = p1 + d2 * 0.28
    d3 = nrm(rz(-math.radians(58) * tc) @ d2)
    p3 = p2 + d3 * 0.20
    m.ball(tb, 0.22, (1.0, 0.95, 1.0), mix(CRUST, CRUST2, 0.5))
    m.tube([tb, p1, p2, p3], [0.21, 0.18, 0.155, 0.122], sides=16,
           seam_from=0.30, seam_str=0.42, rough=0.05)
    m.ball(p1, 0.172, (1.06, 0.94, 1.06), mix(CRUST, CRUST2, 0.74))
    m.ball(p2, 0.144, (1.06, 0.94, 1.06), mix(CRUST, CRUST2, 0.8))

    def crack(start, direction, length, depth):
        p = start
        d = nrm(direction)
        segs = 4
        for i in range(segs):
            nxt = p + d * (length / segs)
            s = nrm(np.cross(d, v(0, 1, 0)))
            w = 0.017 * (1.0 - i / segs) + 0.006
            heat = mix(SEAM, WHITE, 0.15 + 0.6 * (i / segs))
            m.add(p - s * w, p + s * w, nxt + s * w, nxt - s * w, heat, 1.0)
            if depth < 2 and rng.random() < 0.55:
                nd = nrm(d + v(rng.uniform(-.9, .9), rng.uniform(-.3, .3), rng.uniform(.5, 1.2)))
                crack(nxt, nd, length * 0.55, depth + 1)
            p = nxt

    for _ in range(int(22 + 22 * curl)):
        a = rng.uniform(0, 2 * math.pi)
        y = wrist_y + rng.uniform(-0.10, 1.05)
        r = rng.uniform(0.16, 0.44)
        crack(v(math.cos(a) * r * 0.9, y, math.sin(a) * r * 0.42 + 0.05),
              v(rng.uniform(-.4, .4), rng.uniform(0.8, 1.4), rng.uniform(0.2, .9)),
              rng.uniform(0.10, 0.26), 0)

    for _ in range(int(5 + 9 * curl)):
        a = rng.uniform(0, 2 * math.pi)
        base = v(math.cos(a) * rng.uniform(0.26, 0.46), wrist_y + rng.uniform(0.1, 1.05),
                 math.sin(a) * rng.uniform(0.08, 0.22))
        tip = base + nrm(v(math.cos(a) * 0.35, 1.0, math.sin(a) * 0.2)) * rng.uniform(0.18, 0.40)
        mid = (base + tip) * 0.5 + v(rng.uniform(-.05, .05), 0, rng.uniform(-.05, .05))
        m.tube([base, mid, tip], [0.060, 0.036, 0.005], sides=8,
               c0=mix(SEAM, HOT, 0.5), c1=WHITE, emit=1.5, seam_from=2.0)
    return m


def ground(m, radius=1.05, n=9, rng=None):
    """腕子周围的几块半埋碎石（小、暗、贴着地），别做漂浮的碎片。"""
    rng = rng or np.random.default_rng(3)
    for i in range(n):
        a = 2 * math.pi * i / n + rng.uniform(-0.25, 0.25)
        r = radius * rng.uniform(0.62, 1.0)
        c = v(math.cos(a) * r, -0.06, math.sin(a) * r)
        s = rng.uniform(0.07, 0.13)
        col = mix(CRUST, CRUST2, rng.uniform(0.05, 0.3))
        m.add(c + v(-s, 0, -s), c + v(s, 0, -s), c + v(s * 0.7, s * 0.55, s * 0.5),
              c + v(-s * 0.6, s * 0.4, s * 0.8), col, 0.0)


class Camera:
    def __init__(self, pos, target, fov=38.0):
        self.pos = v(*pos)
        fwd = nrm(v(*target) - self.pos)
        right = nrm(np.cross(fwd, v(0, 1, 0)))
        up = np.cross(right, fwd)
        self.b = np.stack([right, up, fwd])
        self.f = 1.0 / math.tan(math.radians(fov) * 0.5)

    def project(self, p, w, h):
        d = p - self.pos
        x, y, z = float(d @ self.b[0]), float(d @ self.b[1]), float(d @ self.b[2])
        if z <= 0.05:
            return None
        return (x / z) * self.f * h * 0.5 + w * 0.5, h * 0.5 - (y / z) * self.f * h * 0.5, z


def render(mesh, cam, w, h, exposure=1.0, bg=(0.030, 0.024, 0.028)):
    ss = 2
    W, H = w * ss, h * ss
    color = np.zeros((H, W, 3))
    depth = np.full((H, W), 1e9)
    key = nrm(v(-0.35, 0.85, 0.55))
    fill = nrm(v(0.75, 0.10, 0.55))
    for a, b, c, d, n, col, emit in mesh.quads:
        pts = [cam.project(p, W, H) for p in (a, b, c, d)]
        if any(p is None for p in pts):
            continue
        mid = (a + b + c + d) * 0.25
        view = nrm(cam.pos - mid)
        rim = max(0.0, 1.0 - abs(float(n @ view))) ** 2.6
        shade = 0.08 + 0.66 * max(0.0, float(n @ key)) + 0.14 * max(0.0, float(n @ fill))
        rgb = np.array(col) * shade
        rgb += np.array(HOT) * rim * 0.14
        rgb += np.array(col) * emit * 2.6
        rgb += np.array(WHITE) * emit * emit * 0.9
        rgb = np.clip(rgb, 0, 3.0)
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        x0, x1 = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
        y0, y1 = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
        if x1 <= x0 or y1 <= y0:
            continue
        for tri in ((0, 1, 2), (0, 2, 3)):
            p0, p1, p2 = pts[tri[0]], pts[tri[1]], pts[tri[2]]
            area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
            if abs(area) < 1e-7:
                continue
            for y in range(y0, y1):
                for x in range(x0, x1):
                    px, py = x + 0.5, y + 0.5
                    w0 = ((p1[0] - px) * (p2[1] - py) - (p2[0] - px) * (p1[1] - py)) / area
                    w1 = ((p2[0] - px) * (p0[1] - py) - (p0[0] - px) * (p2[1] - py)) / area
                    w2 = 1.0 - w0 - w1
                    if w0 < -0.01 or w1 < -0.01 or w2 < -0.01:
                        continue
                    z = 1.0 / (w0 / p0[2] + w1 / p1[2] + w2 / p2[2])
                    if z < depth[y, x]:
                        depth[y, x] = z
                        color[y, x] = rgb
    color = color.reshape(h, ss, w, ss, 3).mean(axis=(1, 3))
    mask = depth.reshape(h, ss, w, ss).min(axis=(1, 3)) > 1e8
    glow = np.clip(color - 0.5, 0, None)
    small = glow.reshape(h // 4, 4, w // 4, 4, 3).mean(axis=(1, 3))
    for _ in range(2):
        acc = small.copy()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                acc += np.roll(np.roll(small, dy, 0), dx, 1)
        small = acc / 10.0
    color = color + np.repeat(np.repeat(small, 4, 0), 4, 1) * 1.2
    out = 1.0 - np.exp(-color * exposure)
    out = np.where(mask[..., None], np.array(bg), out)
    return Image.fromarray((np.clip(out, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8))


def main():
    W, H = 468, 560
    poses = [(0.08, 0.32, 0.10, 0.0), (0.30, 0.80, 0.85, 0.0), (1.00, 1.00, 0.05, 0.9)]
    cams = [(1.0, 0.16, 1.0), (-0.92, 0.38, 0.9)]
    sheet = Image.new('RGB', (W * 2, H * 3), (10, 8, 9))
    for pi, (curl, rise, spread, trem) in enumerate(poses):
        mesh = build_hand(curl, rise, spread, trem, time=0.37)
        ground(mesh, radius=1.15, rng=np.random.default_rng(3))
        pts = np.array([q[0] for q in mesh.quads])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        ctr = (lo + hi) * 0.5
        radius = float(np.linalg.norm(hi - lo)) * 0.5 + 0.05
        for ci, d in enumerate(cams):
            dirv = nrm(v(*d))
            dist = radius / math.tan(math.radians(38) * 0.5) * 0.94
            cam = Camera(tuple(ctr + dirv * dist + v(0, 0.06, 0)), tuple(ctr), 38)
            sheet.paste(render(mesh, cam, W, H), (ci * W, pi * H))
    out = pathlib.Path('docs/spells/火手-model.png')
    sheet.save(out)
    print('saved', out, sheet.size, 'quads', len(build_hand(1.0, 1.0, 0.05, 0.7).quads))


if __name__ == '__main__':
    main()
