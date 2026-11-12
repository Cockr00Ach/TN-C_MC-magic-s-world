"""风球预览：按 WindBallLines 的几何复算，画 飞行 / 压缩 / 炸开 三个状态。

参数和 src/main/java/com/tnc/tnc/client/WindBallLines.java 一致。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

SEGMENTS = 16
SHELL_LAT = 3
SHELL_LON = 2
SHELL_SEG = 14
SHOCK_RINGS = 3
STREAKS = 10

W, H = 1200, 460
GLOW = (26, 184, 133)
MID = (158, 255, 219)
WHITE = (255, 255, 255)


def strands(t):
    return 4 + max(1, min(5, t))


def turns(t):
    return 1.15 + 0.22 * max(1, min(5, t))


def swirl(radius, count, turns_, spin, seed):
    rnd = random.Random((seed * 0x2545F4914F6CDD1D) & 0xFFFFFFFF)
    out = []
    for s in range(count):
        offset = s * (2 * math.pi / max(1, count)) + (rnd.random() - 0.5) * 0.25
        wobble = 0.06 + rnd.random() * 0.10
        pts = []
        for i in range(SEGMENTS + 1):
            u = i / SEGMENTS
            phi = math.pi * (1 - u)
            theta = spin + offset + turns_ * phi
            ring = math.sin(phi) * (1 + wobble * math.sin(phi * 5 + offset))
            pts.append((math.cos(theta) * ring * radius, math.cos(phi) * radius,
                        math.sin(theta) * ring * radius))
        out.append(pts)
    return out


def shell(radius):
    out = []
    for i in range(1, SHELL_LAT + 1):
        phi = math.pi * i / (SHELL_LAT + 1)
        y = radius * math.cos(phi)
        ring = radius * math.sin(phi)
        out.append([(math.cos(2 * math.pi * k / SHELL_SEG) * ring, y,
                     math.sin(2 * math.pi * k / SHELL_SEG) * ring) for k in range(SHELL_SEG + 1)])
    for j in range(SHELL_LON):
        a = math.pi * j / SHELL_LON
        pts = []
        for k in range(SHELL_SEG + 1):
            b = 2 * math.pi * k / SHELL_SEG
            h = radius * math.sin(b)
            pts.append((math.cos(a) * h, radius * math.cos(b), math.sin(a) * h))
        out.append(pts)
    return out


def shock_rings(radius, progress):
    p = max(0.0, min(1.0, progress))
    out = []
    for i in range(SHOCK_RINGS):
        local = max(0.0, min(1.0, p * 1.6 - i * 0.18))
        r = radius * (0.35 + 1.85 * local)
        out.append([(math.cos(2 * math.pi * k / SEGMENTS) * r,
                     math.sin(2 * math.pi * k / SEGMENTS) * r * 0.06,
                     math.sin(2 * math.pi * k / SEGMENTS) * r) for k in range(SEGMENTS + 1)])
    return out


def streaks(radius, progress, seed):
    p = max(0.0, min(1.0, progress))
    rnd = random.Random((seed * 0x9E3779B97F4A7C15) & 0xFFFFFFFF)
    out = []
    for _ in range(STREAKS):
        u = rnd.random() * 2 - 1
        th = rnd.random() * 2 * math.pi
        s = math.sqrt(max(0.0, 1 - u * u))
        d = (s * math.cos(th), u, s * math.sin(th))
        inner = radius * (0.35 + 0.5 * p)
        outer = radius * (0.9 + 1.7 * p)
        out.append([(d[0] * inner, d[1] * inner, d[2] * inner),
                    (d[0] * outer, d[1] * outer, d[2] * outer)])
    return out


YAW = math.radians(22)
PITCH = math.radians(-12)


def project(p, cx, cy, scale):
    x, y, z = p[0] * scale, p[1] * scale, p[2] * scale
    xr = x * math.cos(YAW) + z * math.sin(YAW)
    zr = -x * math.sin(YAW) + z * math.cos(YAW)
    yr = y * math.cos(PITCH) - zr * math.sin(PITCH)
    return (cx + xr, cy - yr)


def layer(img, lines, colour, width, alpha, cx, cy, scale):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for pts in lines:
        d.line([project(p, cx, cy, scale) for p in pts],
               fill=colour + (int(255 * alpha),), width=max(1, int(width)), joint="curve")
    lay = lay.filter(ImageFilter.GaussianBlur(max(0.6, width * 0.4)))
    return Image.alpha_composite(img, lay)


def panel(img, cx, scale, radius, tier, spin, blast=None, label=""):
    if blast is None:
        lines = swirl(radius, strands(tier), turns(tier), spin, 99) + shell(radius)
        img = layer(img, lines, GLOW, 8, 0.30, cx, H / 2, scale)
        img = layer(img, swirl(radius, strands(tier), turns(tier), spin, 99), MID, 5, 0.55, cx, H / 2, scale)
        img = layer(img, swirl(radius, strands(tier), turns(tier), spin, 99), WHITE, 2, 0.9, cx, H / 2, scale)
    else:
        img = layer(img, shock_rings(radius, blast), GLOW, 8, 0.5, cx, H / 2, scale)
        img = layer(img, streaks(radius, blast, 7), MID, 4, 0.55, cx, H / 2, scale)
        img = layer(img, shock_rings(radius, blast), WHITE, 2, 0.85, cx, H / 2, scale)
    d = ImageDraw.Draw(img)
    d.text((cx - 40, H - 40), label, fill=(200, 220, 230, 255))
    return img


def main():
    img = Image.new("RGBA", (W, H), (5, 7, 10, 255))
    img = panel(img, 200, 1.0, 120.0, 1, 0.6, None, "t1 flight (5 lines)")
    img = panel(img, 590, 1.0, 150.0, 5, 1.4, None, "t5 flight (9 lines)")
    img = panel(img, 980, 1.0, 90.0, 5, 0.0, 0.65, "t5 blast")
    img.convert("RGB").save("work/windball-preview.png")
    print("saved work/windball-preview.png")


if __name__ == "__main__":
    main()
