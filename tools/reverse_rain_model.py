"""逆雨预览：把 WaterReverseRain.draw() 的同一份几何在 CPU 上重画。

用法： python tools/reverse_rain_model.py [tier]
输出： docs/spells/水法逆雨-model.png（三格：读条 25% / 60% / 95%）

这不是游戏截图。几何、颜色、alpha 全部照抄 src/main/java/com/tnc/tnc/client/WaterReverseRain.java，
只是换了个渲染器：左中右三格都是同一台相机、同一个时间点，差别只有读条进度。
"""

import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- 向量工具
def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    n = math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2)
    return (0.0, 0.0, 1.0) if n < 1e-9 else (a[0] / n, a[1] / n, a[2] / n)


def right_of(forward):
    r = cross(forward, (0.0, 1.0, 0.0))
    if r[0] ** 2 + r[1] ** 2 + r[2] ** 2 < 1e-6:
        return (1.0, 0.0, 0.0)
    return norm(r)


def font(size):
    for name in ("msyh.ttc", "msyhbd.ttc", "simhei.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


# ---------------------------------------------------------------- 场景
class Scene:
    def __init__(self):
        self.quads = []

    def quad(self, a, b, c, d, color, alpha):
        self.quads.append((a, b, c, d, color[0], color[1], color[2], alpha))

    def disk(self, center, normal, radius, color, alpha, segments=16, spin=0.0):
        rt = right_of(normal)
        up = norm(cross(rt, normal))
        for i in range(segments):
            a0 = i * math.tau / segments + spin
            a1 = (i + 1) * math.tau / segments + spin
            p = add(center, add(mul(rt, math.cos(a0) * radius), mul(up, math.sin(a0) * radius)))
            q = add(center, add(mul(rt, math.cos(a1) * radius), mul(up, math.sin(a1) * radius)))
            self.quad(center, p, q, center, color, alpha)

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

    def streak(self, a, b, half_width, color, alpha, color2=None):
        """两片交叉的窄四边形 —— 和 Java 里的 streak() 一样，任何角度看都不是一条缝。"""
        axis = sub(b, a)
        if axis[0] ** 2 + axis[1] ** 2 + axis[2] ** 2 < 1e-9:
            return
        axis = norm(axis)
        across = cross(axis, (1.0, 0.0, 0.0))
        if across[0] ** 2 + across[1] ** 2 + across[2] ** 2 < 1e-6:
            across = cross(axis, (0.0, 0.0, 1.0))
        across = mul(norm(across), half_width)
        across2 = mul(norm(cross(axis, across)), half_width)
        self.quad(sub(a, across), add(a, across), add(b, across), sub(b, across), color, alpha)
        c2 = color2 or color
        self.quad(sub(a, across2), add(a, across2), add(b, across2), sub(b, across2), c2, alpha * .70)


def droplet(scene, at, size, color, alpha):
    scene.disk(at, (0.0, 0.0, 1.0), size, color, alpha, 10)
    scene.disk(at, (1.0, 0.0, 0.0), size * .9, color, alpha * .8, 10)


def box(scene, center, hx, hy, hz, color, alpha):
    """给"施法者"用的一个方块人，纯参考尺度，游戏里是玩家模型。"""
    cx, cy, cz = center
    v = [(cx + sx * hx, cy + sy * hy, cz + sz * hz)
         for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    # 顶点编号： (sx,sy,sz) -> index
    def i(sx, sy, sz):
        return (0 if sx < 0 else 4) + (0 if sy < 0 else 2) + (0 if sz < 0 else 1)
    faces = [((0, 0, -1), [i(-1, -1, -1), i(1, -1, -1), i(1, 1, -1), i(-1, 1, -1)], .80),
             ((0, 0, 1), [i(-1, -1, 1), i(1, -1, 1), i(1, 1, 1), i(-1, 1, 1)], 1.00),
             ((-1, 0, 0), [i(-1, -1, -1), i(-1, -1, 1), i(-1, 1, 1), i(-1, 1, -1)], .86),
             ((1, 0, 0), [i(1, -1, -1), i(1, -1, 1), i(1, 1, 1), i(1, 1, -1)], .92),
             ((0, 1, 0), [i(-1, 1, -1), i(1, 1, -1), i(1, 1, 1), i(-1, 1, 1)], 1.10)]
    for _n, idxs, shade in faces:
        scene.quad(v[idxs[0]], v[idxs[1]], v[idxs[2]], v[idxs[3]],
                   (color[0] * shade, color[1] * shade, color[2] * shade), alpha)


# ---------------------------------------------------------------- 和 Java 一致的参数
W_C, C_C, D_C = (0.78, 0.96, 1.0), (0.26, 0.78, 1.0), (0.02, 0.10, 0.28)


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def smooth(x):
    x = clamp(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def frac(v):
    f = v - math.floor(v)
    return f + 1 if f < 0 else f


def hashv(index, salt):
    x = (index * 0x9E3779B97F4A7C15 + salt * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    x ^= x >> 30
    x = (x * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    x ^= x >> 27
    x = (x * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    x ^= x >> 31
    return (x >> 11) / float(1 << 53)


def column_height(tier):
    return 4.6 + 1.05 * clamp(tier, 1, 5)


def drop_count(tier):
    return 110 + 34 * clamp(tier, 1, 5)


def drop_alpha(cycle):
    return (1.0 - clamp(cycle, 0.0, 1.0)) ** .55


def drop_radius_factor(cycle):
    return 1.0 - .28 * clamp(cycle, 0.0, 1.0)


def charge_glow(ratio):
    return smooth(clamp(ratio / .82, 0.0, 1.0))


# ---------------------------------------------------------------- 逆雨本体（Java draw 的移植）
def build_rain(scene, tier, ratio, time, radius=3.0):
    p = clamp(ratio, 0.0, 1.0)
    charge = charge_glow(p)
    R = radius
    height = column_height(tier)

    wet = R * (1.15 + .15 * charge)
    scene.disk((0.0, .028, 0.0), (0.0, 1.0, 0.0), wet, D_C, .26 * (.45 + .55 * charge), 40)
    scene.ring((0.0, .045, 0.0), (0.0, 1.0, 0.0), wet, .020, C_C, .16 + .22 * charge, 48)

    for k in range(6):
        angle = k * math.tau / 6 + hashv(k, 11) * .9
        spot = R * (.30 + .62 * hashv(k, 17))
        cycle = frac(time * (.85 + .55 * hashv(k, 23)) + hashv(k, 29))
        shrink = spot * (.55 + .40 * cycle)
        at = (math.cos(angle) * spot, .05, math.sin(angle) * spot)
        a = (1 - cycle) * (.10 + .16 * charge)
        scene.ring(at, (0.0, 1.0, 0.0), shrink, .012 + .012 * (1 - cycle), C_C, a, 20)
        droplet(scene, add(at, (0.0, .06 + .30 * cycle, 0.0)), .028 + .020 * (1 - cycle),
                W_C, a * 1.4)
        # 离地那一下的小水冠：五根朝上的短刺
        for s in range(5):
            sa = s * math.tau / 5 + time * .6 + k
            lift = .10 + .34 * (1 - cycle)
            frm = add(at, (math.cos(sa) * .07, .02, math.sin(sa) * .07))
            scene.streak(frm, add(frm, (0.0, lift, 0.0)), .010, W_C, a * 1.5, C_C)

    for i in range(drop_count(tier)):
        seed = hashv(i, 7)
        speed = .55 + .45 * hashv(i, 13)
        cycle = frac(time * .045 * speed + hashv(i, 31) + seed * .65)
        y = cycle * height
        angle = hashv(i, 37) * math.tau + cycle * .55 * (1 if i % 2 == 0 else -1)
        reach = R * (.30 + .95 * hashv(i, 41)) * drop_radius_factor(cycle)
        foot = (math.cos(angle) * reach, .04 + y, math.sin(angle) * reach)
        length = (.34 + .55 * speed) * (1 - .38 * cycle)
        a = drop_alpha(cycle) * (.30 + .46 * charge)
        if a < .004:
            continue
        tip = add(foot, (0.0, length, 0.0))
        width = .011 + .007 * speed
        scene.streak(foot, tip, width, W_C, a * .55, C_C)
        scene.streak(add(tip, (0.0, -length * .30, 0.0)), tip, width * 1.35, W_C,
                     min(1.0, a * 1.85), C_C)

    cloud_y = height
    cloud_r = R * (.50 + .35 * charge)
    cloud_a = .09 + .23 * charge
    # 软水团堆出来的云（硬边圆盘看着像飞碟）
    for k in range(10):
        pa = k * math.tau / 10 + time * .25
        pr = cloud_r * (.30 + .42 * hashv(k, 61))
        puff = (math.cos(pa) * pr, cloud_y + (hashv(k, 67) - .5) * .40, math.sin(pa) * pr)
        scene.disk(puff, (0.0, 1.0, 0.0), cloud_r * (.26 + .16 * hashv(k, 71)), C_C,
                   cloud_a * .60, 18)
    scene.disk((0.0, cloud_y, 0.0), (0.0, 1.0, 0.0), cloud_r * .58, W_C, cloud_a * .85, 26)
    for arm in range(3):
        spin = time * .55 + arm * math.tau / 3
        a0 = (math.cos(spin) * cloud_r * .30, cloud_y - .02, math.sin(spin) * cloud_r * .30)
        a1 = (math.cos(spin + 1.25) * cloud_r * .92, cloud_y + .04,
              math.sin(spin + 1.25) * cloud_r * .92)
        scene.streak(a0, a1, .018, W_C, cloud_a * 1.9, C_C)
    # 云底下垂下来的几缕水：把"下面那些线"和"上面那团云"缝起来
    for k in range(6):
        ka = k * math.tau / 6 + time * .18
        kr = cloud_r * (.45 + .40 * hashv(k, 53))
        drop = .55 + .85 * (1 - charge)
        top = (math.cos(ka) * kr, cloud_y - .03, math.sin(ka) * kr)
        scene.streak(add(top, (0.0, -drop, 0.0)), top, .013, W_C, cloud_a * 1.5, C_C)

    if p > .72:
        tail = smooth((p - .72) / .28)
        bead = cloud_r * (.85 - .55 * tail)
        bead_y = cloud_y - .55 * tail
        droplet(scene, (0.0, bead_y, 0.0), .18 + .16 * tail, W_C, .35 + .45 * tail)
        scene.ring((0.0, bead_y - .12, 0.0), (0.0, 1.0, 0.0), bead * 1.5, .014, W_C,
                   .22 * tail, 28)


# ---------------------------------------------------------------- 渲染
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


def render_additive(scene, cam, exposure=.92, bloom=.30):
    w, h = cam.w, cam.h
    buf = np.zeros((h, w, 3), dtype=np.float32)
    for pts, _depth, _n, r, g, bl, al in rasterize(scene.quads, cam):
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


# ---------------------------------------------------------------- 拼图
PANEL_W, PANEL_H = 600, 660
TIER = int(sys.argv[1]) if len(sys.argv) > 1 else 5
RADIUS = 3.0
CAM_POS = (9.8, 4.6, -11.8)
CAM_TARGET = (0.0, 4.9, 0.0)

PHASES = [(.25, "① 读条 25%：地面刚被抽起来"),
          (.60, "② 读条 60%：水线一路往上"),
          (.95, "③ 读条 95%：头顶积成一片云，珠子已经压下来")]

header = 66
W = PANEL_W * len(PHASES) + 24 * (len(PHASES) + 1)
H = header + PANEL_H + 22
canvas = Image.new("RGB", (W, H), (7, 13, 21))
draw = ImageDraw.Draw(canvas)
draw.text((24, 12), "水法前戏 · 逆雨（t%d，水柱 %.1f 格，地面半径 %.1f 格，1:1 比例）"
          % (TIER, column_height(TIER), RADIUS), fill=(228, 244, 255), font=font(23))
draw.text((24, 42), "同一台相机、同一个时间点，只有读条进度不同；渲染器照抄 WaterReverseRain.draw() 的几何与 alpha，"
                    "加色混合 ⇒ 这就是游戏里那种发光的水。", fill=(122, 168, 196), font=font(14))

for idx, (ratio, title) in enumerate(PHASES):
    cam = Camera(CAM_POS, CAM_TARGET, 44.0, PANEL_W, PANEL_H)
    scene = Scene()
    # 地面（暗）+ 一格方块参照 + 施法者剪影
    box(scene, (0.0, -0.06, 0.0), 9.0, .06, 9.0, (0.05, 0.09, 0.14), 1.0)
    for k in range(-4, 5):
        scene.quad((k, .005, -9.0), (k, .005, 9.0), (k + .04, .005, 9.0), (k + .04, .005, -9.0),
                   (0.16, 0.30, 0.40), .30)
        scene.quad((-9.0, .005, k), (9.0, .005, k), (9.0, .005, k + .04), (-9.0, .005, k + .04),
                   (0.16, 0.30, 0.40), .30)
    box(scene, (0.0, .90, 0.0), .30, .90, .22, (0.30, 0.42, 0.52), .85)
    build_rain(scene, TIER, ratio, 12.0, RADIUS)

    arr = np.asarray(Image.fromarray(render_additive(scene, cam)))
    # 加一点冷色渐晕，别让纯黑边显得像贴纸
    bg = np.zeros_like(arr, dtype=np.float32)
    yy = np.linspace(0, 1, arr.shape[0])[:, None, None]
    bg[:] = np.array([9, 18, 28], dtype=np.float32) * (1 - .7 * yy)
    arr = np.clip(arr.astype(np.float32) * .92 + bg * .55, 0, 255).astype(np.uint8)

    x0 = 24 + idx * (PANEL_W + 24)
    y0 = header
    canvas.paste(Image.fromarray(arr), (x0, y0))
    d2 = ImageDraw.Draw(canvas)
    d2.rectangle([x0, y0, x0 + PANEL_W - 1, y0 + PANEL_H - 1], outline=(32, 60, 88))
    d2.text((x0 + 14, y0 + 10), title, fill=(206, 232, 246), font=font(16))

out_path = r"D:\ModTest\docs\spells\水法逆雨-model.png"
canvas.save(out_path)
print("saved", canvas.size, "quads:", len(scene.quads))
