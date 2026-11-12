"""雷球外观预览：按 ThunderOrbAura 的几何复算，画经纬线 + 游走闪电 + 亮晕。

参数和 src/main/java/com/tnc/tnc/client/ThunderOrbAura.java 一致。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

LATITUDES = 4
MERIDIANS = 3
RING_SEGMENTS = 18
ARC_SEGMENTS = 5
ARC_STEP = 0.55

W, H = 1100, 460
R_PX = 150


def shell(radius):
    out = []
    for i in range(1, LATITUDES + 1):
        phi = math.pi * i / (LATITUDES + 1.0)
        y = radius * math.cos(phi)
        ring = radius * math.sin(phi)
        pts = []
        for k in range(RING_SEGMENTS + 1):
            a = 2 * math.pi * k / RING_SEGMENTS
            pts.append((math.cos(a) * ring, y, math.sin(a) * ring))
        out.append(pts)
    for j in range(MERIDIANS):
        a = math.pi * j / MERIDIANS
        pts = []
        for k in range(RING_SEGMENTS + 1):
            b = 2 * math.pi * k / RING_SEGMENTS
            h = radius * math.sin(b)
            pts.append((math.cos(a) * h, radius * math.cos(b), math.sin(a) * h))
        out.append(pts)
    return out


def _add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _scale(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _norm(a):
    n = math.sqrt(_dot(a, a))
    return (0.0, 1.0, 0.0) if n < 1e-9 else (a[0] / n, a[1] / n, a[2] / n)


def _tangent(p, rnd):
    any_ = (rnd.random() - 0.5, rnd.random() - 0.5, rnd.random() - 0.5)
    return _norm(_add(any_, _scale(p, -_dot(any_, p))))


def _rot(v, axis, rad):
    c, s = math.cos(rad), math.sin(rad)
    cross = (axis[1] * v[2] - axis[2] * v[1],
             axis[2] * v[0] - axis[0] * v[2],
             axis[0] * v[1] - axis[1] * v[0])
    return _norm(tuple(v[i] * c + cross[i] * s + axis[i] * _dot(axis, v) * (1 - c) for i in range(3)))


def arcs(radius, count, seed):
    rnd = random.Random((seed * 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF)
    out = []
    for _ in range(count):
        u = rnd.random() * 2 - 1
        th = rnd.random() * 2 * math.pi
        s = math.sqrt(max(0.0, 1 - u * u))
        p = (s * math.cos(th), u, s * math.sin(th))
        t = _tangent(p, rnd)
        pts = [(p[0] * radius, p[1] * radius, p[2] * radius)]
        for _k in range(ARC_SEGMENTS):
            turn = (rnd.random() - 0.5) * 2 * ARC_STEP
            t = _rot(t, p, turn)
            t = _norm(_add(t, _scale(p, -_dot(t, p) * 0.35)))
            p = _norm(_add(p, _scale(t, 0.28 + rnd.random() * 0.22)))
            t = _norm(_add(t, _scale(p, -_dot(t, p))))
            pts.append((p[0] * radius, p[1] * radius, p[2] * radius))
        out.append(pts)
    return out


YAW = math.radians(25)
PITCH = math.radians(-10)


def project(p, cx, cy):
    x, y, z = p
    xr = x * math.cos(YAW) + z * math.sin(YAW)
    zr = -x * math.sin(YAW) + z * math.cos(YAW)
    yr = y * math.cos(PITCH) - zr * math.sin(PITCH)
    return (cx + xr, cy - yr)


def draw_lines(img, lines, colour, width, alpha):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for pts in lines:
        d.line([project(p, pts_cx, pts_cy) for p in pts],
               fill=colour + (int(255 * alpha),), width=max(1, int(width)), joint="curve")
    layer = layer.filter(ImageFilter.GaussianBlur(max(0.6, width * 0.4)))
    return Image.alpha_composite(img, layer)


TIERS = [(1, 4), (2, 6), (3, 8), (4, 10), (5, 12)]
GLOW = (26, 184, 133)
MID = (120, 220, 255)
WHITE = (255, 255, 255)

img = Image.new("RGBA", (W, H), (5, 7, 10, 255))
for idx, (tier, arc_count) in enumerate(TIERS):
    globals()["pts_cx"] = 110 + idx * 220
    globals()["pts_cy"] = H / 2
    radius = R_PX * 0.86
    lines = shell(radius)
    arcs_lines = arcs(radius * 1.03, arc_count, 1234 + tier)
    img = draw_lines(img, lines, GLOW, 9, 0.28)
    img = draw_lines(img, arcs_lines, MID, 6, 0.5)
    img = draw_lines(img, arcs_lines, WHITE, 2, 0.9)
    d = ImageDraw.Draw(img)
    d.text((globals()["pts_cx"] - 34, H - 46), f"t{tier} / {arc_count} arcs", fill=(200, 220, 230, 255))

img.convert("RGB").save("work/orb-preview.png")
print("saved work/orb-preview.png")
