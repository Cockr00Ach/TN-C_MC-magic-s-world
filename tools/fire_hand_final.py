"""火手链：巨人手掌 —— 高精度几何 + 程序化火焰（等离子层）预览。

    python tools/fire_hand_final.py      # → docs/spells/火手-model.png

三层结构：
    1) 实体：掌 + 四指(每指 3 节，节间收腰) + 拇指 + 前臂，24 边截面，关节鼓包、掌背腱线、
       指端烧白硬壳；表面带噪声起伏（烧硬的壳）。
    2) 裂纹：分叉网络，越靠中心越白，从缝里往外发光。
    3) 火焰：沿表面窜起的火舌，用可平铺噪声做逐像素 alpha 调制（白芯→黄→橙→暗红），
       叠在实体上（加色），越握紧越旺。这一步就是"离子态"的来源。
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


CRUST = (0.050, 0.037, 0.034)
CRUST2 = (0.150, 0.086, 0.066)
PLATE = (0.088, 0.058, 0.050)
SEAM = (1.00, 0.26, 0.04)
HOT = (1.00, 0.50, 0.09)
WHITE = (1.00, 0.93, 0.74)
DEEP = (0.42, 0.06, 0.02)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


# ---------------------------------------------------------------- 噪声（可平铺）
def make_noise(n=96, cells=8, seed=4):
    rng = np.random.default_rng(seed)
    g = rng.random((cells, cells))
    g = np.tile(g, (3, 3))
    out = np.zeros((n, n))
    xs = np.linspace(0, cells, n, endpoint=False)
    ys = np.linspace(0, cells, n, endpoint=False)
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    fx = xs - x0
    fy = ys - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    for j in range(n):
        for i in range(n):
            a = g[y0[j] + cells, x0[i] + cells]
            b = g[y0[j] + cells, x0[i] + 1 + cells]
            c = g[y0[j] + 1 + cells, x0[i] + cells]
            d = g[y0[j] + 1 + cells, x0[i] + 1 + cells]
            out[j, i] = (a * (1 - fx[i]) + b * fx[i]) * (1 - fy[j]) + (c * (1 - fx[i]) + d * fx[i]) * fy[j]
    out = (out - out.min()) / (out.max() - out.min() + 1e-9)
    return out


NOISE = make_noise()


def sample_noise(u, vv, t=0.0, scale=1.0):
    n = NOISE.shape[0]
    x = int((u * scale + t * 0.13) % 1.0 * (n - 1))
    y = int((vv * scale + t * 0.07) % 1.0 * (n - 1))
    return NOISE[y, x]


# ---------------------------------------------------------------- 网格
class Mesh:
    def __init__(self):
        self.quads = []      # 实体
        self.flames = []     # (spine points, widths, intensity, seed) —— 渲染时做朝向相机的公告板

    def add(self, a, b, c, d, col, emit=0.0, normal=None):
        n = normal if normal is not None else nrm(np.cross(b - a, c - a) + np.cross(c - a, d - a))
        self.quads.append((a, b, c, d, nrm(n), col, emit))

    def flame(self, pts, widths, intensity=1.0, seed=0.0):
        self.flames.append((list(pts), list(widths), intensity, seed))

    def tube(self, pts, radii, sides=20, c0=CRUST, c1=CRUST2, emit=0.0,
             seam_from=0.30, seam_str=0.42, rough=0.035, fade_tip=0.0):
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
                col = mix(c0, c1, min(1.0, t * 0.9 + 0.1 * abs(math.sin(s * 2.0 + i * 1.3))))
                e = 0.0
                if fade_tip > 0 and t > 1.0 - fade_tip:
                    col = mix(col, DEEP, (t - (1 - fade_tip)) / fade_tip)
                if t > seam_from and (s * 5 + i * 7) % 17 == 0:
                    col = mix(col, SEAM, seam_str)
                    e = 0.55
                self.add(q0, q1, q2, q3, col, e, nrad)

    def ball(self, c, r, scale=(1, 1, 1), col=CRUST2, emit=0.0, seg=20, rings=13):
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

    def slab(self, y0, y1, layers, half_w, half_t, col0, col1, n=26):
        rows = []
        for i in range(layers + 1):
            t = i / layers
            y = y0 + (y1 - y0) * t
            w = half_w * (0.70 + 0.34 * math.sin(math.pi * min(1.0, t * 1.35)) + 0.17 * t)
            th = half_t * (0.88 + 0.28 * math.sin(math.pi * t))
            ring = []
            for s in range(n):
                a = 2 * math.pi * s / n
                ca, sa = math.cos(a), math.sin(a)
                px = math.copysign(abs(ca) ** (2 / 3.2), ca) * w
                pz = math.copysign(abs(sa) ** (2 / 3.2), sa) * th
                if sa > 0:
                    pz *= 1.0 + 0.16 * math.sin(math.pi * t)
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


FINGERS = [
    ("食指", -0.520, 0.70, 0.126, 9, 0.02),
    ("中指", -0.175, 0.74, 0.133, 0, 0.07),
    ("无名指", 0.175, 0.70, 0.126, -9, 0.13),
    ("小指", 0.490, 0.60, 0.109, -16, 0.20),
]
SEG = (0.44, 0.30, 0.20)


def catmull(pts, per=9):
    """过点的平滑曲线（Catmull-Rom）—— 指节之间要顺，不能有折角。"""
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(len(P) - 3):
        p0, p1, p2, p3 = P[i], P[i + 1], P[i + 2], P[i + 3]
        for s in range(per):
            u = s / per
            u2, u3 = u * u, u * u * u
            out.append(0.5 * (2 * p1 + (-p0 + p2) * u
                              + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * u3))
    out.append(pts[-1])
    return out


def radius_profile(n, rad, knuckles=(0.33, 0.66, 1.0), bulge=0.16, tip=0.60):
    """指管半径：整根管子自己鼓出关节（不是在外面套球），指根略粗接掌。"""
    xs = np.linspace(0.0, 1.0, n)
    r = np.full(n, rad) * (1.06 - 0.40 * xs ** 2.2)
    for k in knuckles:
        r = r * (1.0 + bulge * np.exp(-((xs - k) ** 2) / (2 * 0.055 ** 2)))
    r = r * (1.0 - (1.0 - tip) * np.clip((xs - 0.86) / 0.14, 0, 1) ** 1.5)
    return r


def digit(base, length, radius, spread, curl):
    d = nrm(v(math.sin(math.radians(spread)), 1.0, 0.05))
    pts = [base]
    radii = [radius]
    bends = (math.radians(90) * curl, math.radians(102) * curl, math.radians(56) * curl)
    for i in range(3):
        d = nrm(rz(-bends[i] * 0.92) @ d)
        pts.append(pts[-1] + d * (length * SEG[i]))
        radii.append(radius * (0.93, 0.80, 0.66)[i])
    return pts, radii


def digit_smooth(base, length, radius, spread, curl):
    """一根手指：过 4 个关节点的平滑样条 + 沿整根管子变化的半径。"""
    pts, _ = digit(base, length, radius, spread, curl)
    spline = catmull(pts, per=10)
    prof = radius_profile(len(spline), radius)
    return pts, spline, prof


def build_hand(curl, rise, spread=0.0, tremble=0.0, time=0.0):
    m = Mesh()
    wrist_y = -0.70 + 0.95 * rise
    rng = np.random.default_rng(5)

    # ---- 前臂
    arm = catmull([v(0, wrist_y - 1.75, 0), v(0, wrist_y - 1.35, 0), v(0, wrist_y - 0.95, 0),
                   v(0, wrist_y - 0.55, 0), v(0, wrist_y - 0.22, 0),
                   v(0, wrist_y - 0.04, 0)], per=8)
    arm_prof = np.interp(np.linspace(0, 1, len(arm)),
                         [0, 0.18, 0.42, 0.72, 0.9, 1.0],
                         [0.52, 0.55, 0.50, 0.40, 0.355, 0.345])
    m.tube(arm, list(arm_prof), sides=26, seam_from=0.16, seam_str=0.5, rough=0.045)

    # ---- 手掌（掌背拱起 + 指根脊）
    m.slab(wrist_y - 0.06, wrist_y + 1.02, 20, 0.470, 0.235, CRUST, CRUST2)
    m.ball(v(-0.34, wrist_y + 0.34, 0.13), 0.200, (1.0, 1.32, 0.78), mix(CRUST, CRUST2, 0.50))
    m.ball(v(0.31, wrist_y + 0.22, 0.07), 0.155, (1.0, 1.42, 0.74), mix(CRUST, CRUST2, 0.44))
    # 指根不是四颗球：掌顶做成一排连续的低隆（下面的 tube 出来时会自己接上去）
    m.tube([v(-0.50, wrist_y + 0.98, -0.01), v(0, wrist_y + 1.06, -0.01), v(0.50, wrist_y + 0.98, -0.01)],
           [0.075, 0.095, 0.075], sides=16, c0=mix(CRUST, CRUST2, 0.55),
           c1=mix(CRUST, CRUST2, 0.72), seam_from=2.0)
    # 掌背腱线：四条从腕到指根的隆起
    for x in (-0.36, -0.12, 0.12, 0.36):
        m.tube([v(x * 0.55, wrist_y + 0.10, 0.185), v(x * 0.8, wrist_y + 0.45, 0.20),
                v(x, wrist_y + 0.80, 0.18)],
               [0.035, 0.030, 0.024], sides=10, c0=mix(CRUST, CRUST2, 0.55),
               c1=mix(CRUST, CRUST2, 0.35), seam_from=2.0)

    # ---- 四指
    knuckle_y = wrist_y + 1.02
    for name, x, length, rad, spread_deg, delay in FINGERS:
        c = max(0.10, min(1.0, (curl - delay) / max(0.05, 1.0 - delay)))
        base = v(x * (1.0 + 0.10 * spread), knuckle_y, 0.0)
        pts, spline, prof = digit_smooth(base, length * (1.0 + 0.10 * spread),
                                         rad, spread_deg * (1.0 + spread), c)
        # 一根连续的指管：关节是管子自己鼓出来的，没有外挂的球
        m.tube(spline, list(prof), sides=26, seam_from=0.26, seam_str=0.44, rough=0.025,
               fade_tip=0.12)
        # 指端只有一小片烧白的硬壳（贴着指尖，不是球）
        tip, tdir = pts[3], nrm(pts[3] - pts[2])
        m.ball(tip + tdir * 0.008, prof[-1] * 0.86, (1.2, 0.55, 1.0),
               mix(CRUST2, WHITE, 0.22), 0.10, seg=14, rings=9)

    # ---- 拇指
    tc = max(0.16, min(1.0, curl * 1.12))
    tb = v(-0.36, wrist_y + 0.38, 0.12)
    d1 = nrm(v(-0.36, 0.66, 0.66))
    p1 = tb + d1 * 0.42
    d2 = nrm(rz(-math.radians(48) * tc) @ nrm(d1 + v(0.32, 0.08, -0.18)))
    p2 = p1 + d2 * 0.30
    d3 = nrm(rz(-math.radians(56) * tc) @ d2)
    p3 = p2 + d3 * 0.21
    thumb_spline = catmull([tb, p1, p2, p3], per=11)
    thumb_prof = radius_profile(len(thumb_spline), 0.23, knuckles=(0.42, 0.78),
                                bulge=0.20, tip=0.58)
    m.tube(thumb_spline, list(thumb_prof), sides=26,
           seam_from=0.26, seam_str=0.46, rough=0.03, fade_tip=0.12)
    # 大鱼际（拇指根那团肌肉）：不是球，是顺着掌根长出来的一段粗管
    m.tube([v(-0.30, wrist_y + 0.08, 0.10), v(-0.36, wrist_y + 0.22, 0.13),
            v(-0.36, wrist_y + 0.36, 0.12)],
           [0.145, 0.185, 0.150], sides=20, c0=mix(CRUST, CRUST2, 0.40),
           c1=mix(CRUST, CRUST2, 0.52), seam_from=2.0)

    # ---- 裂纹网络
    def crack(start, direction, length, depth):
        p = start
        d = nrm(direction)
        for i in range(5):
            nxt = p + d * (length / 5)
            s = nrm(np.cross(d, v(0, 1, 0)))
            wdt = 0.016 * (1.0 - i / 5) + 0.005
            heat = mix(SEAM, WHITE, 0.12 + 0.65 * (i / 5))
            m.add(p - s * wdt, p + s * wdt, nxt + s * wdt, nxt - s * wdt, heat, 1.0)
            if depth < 2 and rng.random() < 0.6:
                crack(nxt, nrm(d + v(rng.uniform(-.9, .9), rng.uniform(-.4, .4), rng.uniform(.4, 1.2))),
                      length * 0.55, depth + 1)
            p = nxt

    for _ in range(int(18 + 20 * curl)):
        a = rng.uniform(0, 2 * math.pi)
        y = wrist_y + rng.uniform(-0.15, 1.05)
        r = rng.uniform(0.14, 0.42)
        crack(v(math.cos(a) * r * 0.9, y, math.sin(a) * r * 0.42 + 0.04),
              v(rng.uniform(-.4, .4), rng.uniform(0.8, 1.5), rng.uniform(0.2, 1.0)),
              rng.uniform(0.10, 0.28), 0)

    # ---- 火焰（等离子层）：从表面窜出的火舌，握紧时更多更长
    tongues = int(16 + 22 * curl)
    for i in range(tongues):
        a = rng.uniform(0, 2 * math.pi)
        y = wrist_y + rng.uniform(-0.25, 1.10)
        rr = rng.uniform(0.18, 0.46)
        base = v(math.cos(a) * rr, y, math.sin(a) * rr * 0.55)
        out = nrm(v(math.cos(a) * 0.8, rng.uniform(0.1, 0.5), math.sin(a) * 0.6))
        h = rng.uniform(0.15, 0.42) * (0.7 + 0.7 * curl)
        p0 = base
        p1 = base + out * h * 0.35 + v(0, h * 0.55, 0)
        p2 = base + out * h * 0.55 + v(rng.uniform(-.10, .10), h, rng.uniform(-.10, .10))
        spine = bez(p0, p1, p2, 7)
        widths = [rng.uniform(0.055, 0.10) * (1.0 - 0.85 * (k / 7)) for k in range(8)]
        m.flame(spine, widths, rng.uniform(0.75, 1.35), rng.uniform(0, 6.28))
    # 腕部的大火舌（从地里冒出来那一圈）
    for i in range(int(8 + 6 * curl)):
        a = 2 * math.pi * i / max(1, int(8 + 6 * curl)) + rng.uniform(-.2, .2)
        base = v(math.cos(a) * 0.44, wrist_y - 0.05, math.sin(a) * 0.44)
        h = rng.uniform(0.35, 0.85) * (0.8 + 0.5 * rise)
        spine = bez(base, base + v(math.cos(a) * .18, h * .5, math.sin(a) * .18),
                    base + v(math.cos(a) * .10, h, math.sin(a) * .10), 8)
        widths = [rng.uniform(0.09, 0.16) * (1.0 - 0.9 * (k / 8)) for k in range(9)]
        m.flame(spine, widths, rng.uniform(0.9, 1.6), rng.uniform(0, 6.28))
    return m


# ---------------------------------------------------------------- 渲染
class Camera:
    def __init__(self, pos, target, fov=38.0):
        self.pos = v(*pos)
        fwd = nrm(v(*target) - self.pos)
        self.right = nrm(np.cross(fwd, v(0, 1, 0)))
        self.up = np.cross(self.right, fwd)
        self.b = np.stack([self.right, self.up, fwd])
        self.f = 1.0 / math.tan(math.radians(fov) * 0.5)

    def project(self, p, w, h):
        d = p - self.pos
        x, y, z = float(d @ self.b[0]), float(d @ self.b[1]), float(d @ self.b[2])
        if z <= 0.05:
            return None
        return (x / z) * self.f * h * 0.5 + w * 0.5, h * 0.5 - (y / z) * self.f * h * 0.5, z


def render(mesh, cam, w, h, exposure=1.0, bg=(0.028, 0.022, 0.026), time=0.37):
    ss = 2
    W, H = w * ss, h * ss
    color = np.zeros((H, W, 3))
    depth = np.full((H, W), 1e9)
    key = nrm(v(-0.30, 0.90, 0.50))
    fill = nrm(v(0.80, 0.05, 0.55))
    for a, b, c, d, n, col, emit in mesh.quads:
        pts = [cam.project(p, W, H) for p in (a, b, c, d)]
        if any(p is None for p in pts):
            continue
        mid = (a + b + c + d) * 0.25
        view = nrm(cam.pos - mid)
        rim = max(0.0, 1.0 - abs(float(n @ view))) ** 2.4
        shade = 0.09 + 0.70 * max(0.0, float(n @ key)) + 0.16 * max(0.0, float(n @ fill))
        rgb = np.array(col) * shade
        rgb += np.array(HOT) * rim * 0.18
        rgb += np.array(col) * emit * 2.8
        rgb += np.array(WHITE) * emit * emit * 0.9
        rgb = np.clip(rgb, 0, 3.2)
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

    # ---- 火焰层：朝相机的公告板 + 噪声 alpha，加色叠加
    if mesh.flames:
        for spine, widths, intensity, seed in mesh.flames:
            mids = [(spine[i] + spine[i + 1]) * 0.5 for i in range(len(spine) - 1)]
            for i in range(len(spine) - 1):
                p0, p1 = spine[i], spine[i + 1]
                w0, w1 = widths[i], widths[i + 1]
                uv0, uv1 = i / (len(spine) - 1), (i + 1) / (len(spine) - 1)
                # 每个截面朝向相机
                q = []
                for p, wd, uv in ((p0, w0, uv0), (p1, w1, uv1)):
                    q.append((p + cam.right * wd, uv))
                    q.append((p - cam.right * wd, uv))
                quad = [q[0][0], q[1][0], q[3][0], q[2][0]]
                uvs = [q[0][1], q[1][1], q[3][1], q[2][1]]
                pts = [cam.project(p, W, H) for p in quad]
                if any(p is None for p in pts):
                    continue
                xs = [p[0] for p in pts]
                ys = [p[1] for p in pts]
                x0, x1 = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
                y0, y1 = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
                if x1 <= x0 or y1 <= y0:
                    continue
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        px, py = x + 0.5, y + 0.5
                        # 四边形 → 双线性 uv
                        sa = pts[0]
                        sb = pts[1]
                        sc = pts[2]
                        sd = pts[3]
                        dx1, dy1 = sb[0] - sa[0], sb[1] - sa[1]
                        dx2, dy2 = sd[0] - sa[0], sd[1] - sa[1]
                        det = dx1 * dy2 - dx2 * dy1
                        if abs(det) < 1e-7:
                            continue
                        u = ((px - sa[0]) * dy2 - (py - sa[1]) * dx2) / det
                        vv = ((py - sa[1]) * dx1 - (px - sa[0]) * dy1) / det
                        if not (0 <= u <= 1 and 0 <= vv <= 1):
                            continue
                        uu = uvs[0] + (uvs[1] - uvs[0]) * u
                        vvv = uvs[0] + (uvs[2] - uvs[0]) * vv
                        n = sample_noise(uu * 0.9 + seed, vvv * 1.4, t=time, scale=1.0)
                        a = n ** 1.6 * intensity * (1.0 - vvv) ** 0.8
                        if a <= 0.02:
                            continue
                        # 颜色沿高度：白芯 → 黄 → 橙 → 暗红
                        if vvv < 0.25:
                            col = mix(WHITE, HOT, vvv / 0.25)
                        elif vvv < 0.65:
                            col = mix(HOT, SEAM, (vvv - 0.25) / 0.4)
                        else:
                            col = mix(SEAM, DEEP, (vvv - 0.65) / 0.35)
                        color[y, x] += np.array(col) * a * 1.9

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
    color = color + np.repeat(np.repeat(small, 4, 0), 4, 1) * 1.25
    out = 1.0 - np.exp(-color * exposure)
    out = np.where(mask[..., None], np.array(bg), out)
    return Image.fromarray((np.clip(out, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8))


def main():
    W, H = 468, 560
    poses = [(0.10, 0.34, 0.15, 0.0), (0.34, 0.82, 0.85, 0.0), (1.00, 1.00, 0.05, 0.9)]
    cams = [(1.0, 0.16, 1.0), (-0.92, 0.38, 0.9)]
    sheet = Image.new('RGB', (W * 2, H * 3), (8, 7, 8))
    for pi, (curl, rise, spread, trem) in enumerate(poses):
        mesh = build_hand(curl, rise, spread, trem, time=0.37)
        pts = np.array([q[0] for q in mesh.quads])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        ctr = (lo + hi) * 0.5
        radius = float(np.linalg.norm(hi - lo)) * 0.5 + 0.05
        for ci, d in enumerate(cams):
            dirv = nrm(v(*d))
            dist = radius / math.tan(math.radians(38) * 0.5) * 0.95
            cam = Camera(tuple(ctr + dirv * dist + v(0, 0.06, 0)), tuple(ctr), 38)
            sheet.paste(render(mesh, cam, W, H), (ci * W, pi * H))
    out = pathlib.Path('docs/spells/火手-model.png')
    sheet.save(out)
    print('saved', out, sheet.size, 'quads', len(build_hand(1.0, 1.0, 0.05, 0.7).quads))


if __name__ == '__main__':
    main()
