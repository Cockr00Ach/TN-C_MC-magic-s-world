"""龙卷风线条预览（两层版）：把 TornadoLineShape 的几何用 Python 复算一遍，
再按渲染器的三层加色光带画成图。

纯离线预览（不进游戏），用来判断"是不是螺旋缠绕 + 有没有锐角 + 两层分不分得开"。
参数必须和 src/main/java/com/tnc/tnc/magic/TornadoLineShape.java 保持一致。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

# ---- 和 TornadoLineShape 完全一致的常量 ----
STRANDS = 7
TURNS = 1.70
SEGMENTS = 17
KINK = 0.14
SPUR = 0.26
SPUR_CHANCE = 0.14
SPUR_LIFT = 0.060
TOP_CURL_FROM = 0.82
SLASHES = 6

# ---- 两层 ----
# (名字, 半径比例, 根数, 圈数比例, 相位偏移, 线宽比例, 透明度比例, 种子盐)
SHELLS = [
    ("outer", 1.00, STRANDS, 1.00, 0.0, 1.00, 1.00, 0, 1.00),
    ("inner", 0.55, 5, 0.92, math.pi * 0.4, 0.80, 0.95, 977, 0.30),
]

BOTTOM = 4.0 / 16.0
TOP = 13.5 / 16.0
HEIGHT = 31.0 / 16.0
SCALE = 8.0  # t5

CORE_WIDTH_RATIO = 0.028

CORE = (255, 255, 255)
MID = (158, 255, 219)
GLOW = (26, 184, 133)


def profile(u, bottom=BOTTOM, top=TOP):
    t = max(0.0, min(1.0, u))
    return bottom + (top - bottom) * (t ** 0.72)


def spur_chance(u):
    return SPUR_CHANCE * max(0.0, 1.0 - max(0.0, min(1.0, u)))


def top_taper(u, curl):
    if u <= TOP_CURL_FROM:
        return 1.0
    return 1.0 - curl * ((u - TOP_CURL_FROM) / (1.0 - TOP_CURL_FROM))


def angle_at(u, spin, strand, strands, turns, phase_shift):
    return phase_shift + strand * (2.0 * math.pi / strands) - (spin + turns * 2.0 * math.pi * u)


def strands(spin, seed, shell):
    name, radius_scale, count, turns_scale, phase_shift, width_scale, alpha_scale, salt, whiteness = shell
    rnd = random.Random(seed * 131 + salt)
    bottom = BOTTOM * radius_scale
    top = TOP * radius_scale
    out = []
    for s in range(count):
        pts = []
        phase = (rnd.random() - 0.5) * 0.55
        kink = 0.78 + rnd.random() * 0.5
        height_scale = 0.84 + rnd.random() * 0.16
        top_curl = 0.10 + rnd.random() * 0.12
        lift = 0.0
        for i in range(SEGMENTS + 1):
            u = i / SEGMENTS
            r = profile(u, bottom, top)
            spike = (i % 2 == 0)
            if spike:
                factor = 1.0 + KINK * kink
                if rnd.random() < spur_chance(u):
                    factor = 1.0 + SPUR * kink
                    lift = min(HEIGHT * 0.10, lift + HEIGHT * SPUR_LIFT)
                else:
                    lift *= 0.55
            else:
                factor = 1.0 - KINK * 0.55 * kink
                lift *= 0.55
            rr = max(1e-3, r * factor) * top_taper(u, top_curl)
            a = angle_at(u, spin, s, count, TURNS * turns_scale, phase_shift) + phase
            y = u * HEIGHT * height_scale + lift
            if 0 < i < SEGMENTS:
                y += (rnd.random() - 0.5) * HEIGHT * 0.008
            pts.append((math.cos(a) * rr, y, math.sin(a) * rr))
        out.append(pts)
    return out


def slashes(spin, seed):
    rnd = random.Random(seed * 31 + 7)
    out = []
    for k in range(SLASHES):
        u = 0.18 + 0.72 * ((k + rnd.random() * 0.6) / SLASHES)
        r = profile(u)
        a = angle_at(u, spin, k, SLASHES, TURNS, 0.0) + (rnd.random() - 0.5) * 0.5
        y = u * HEIGHT
        spread = 0.22 + rnd.random() * 0.16
        radial = (1.0, 1.16, 0.94, 1.28)
        pts = []
        for i in range(4):
            aa = a + (i - 0.5) * spread
            rr = r * radial[i]
            pts.append((math.cos(aa) * rr, y + (i - 1.5) * HEIGHT * 0.035, math.sin(aa) * rr))
        out.append(pts)
    return out


# ---- 投影 + 叠加光带 ----
W, H = 900, 1000
YAW = math.radians(28)
PITCH = math.radians(-8)
PIXELS_PER_BLOCK = 900.0 / 34.0


def project(p, size):
    x, y, z = p[0] * size, p[1] * size, p[2] * size
    xr = x * math.cos(YAW) + z * math.sin(YAW)
    zr = -x * math.sin(YAW) + z * math.cos(YAW)
    yr = y * math.cos(PITCH) - zr * math.sin(PITCH)
    zr2 = y * math.sin(PITCH) + zr * math.cos(PITCH)
    dist = 34.0
    f = 900.0 / max(1.0, dist + zr2)
    return (W / 2 + xr * f, H * 0.86 - yr * f)


def draw_layer(img, lines, colour, width, alpha):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for pts in lines:
        xy = [project(p, SCALE) for p in pts]
        d.line(xy, fill=colour + (int(255 * alpha),), width=max(1, int(round(width))), joint="curve")
    layer = layer.filter(ImageFilter.GaussianBlur(max(0.6, width * 0.32)))
    return Image.alpha_composite(img, layer)


def main():
    # 世界线宽（格）→ 像素 ✓
    core = max(1.5, HEIGHT * SCALE * CORE_WIDTH_RATIO * PIXELS_PER_BLOCK)

    img = Image.new("RGBA", (W, H), (8, 10, 14, 255))
    ground = ImageDraw.Draw(img)
    ground.ellipse([W / 2 - 150, H * 0.86 - 40, W / 2 + 150, H * 0.86 + 40],
                   outline=(40, 48, 56, 255), width=2)

    for shell in SHELLS:
        lines = strands(0.0, 99, shell)
        width_scale = shell[5]
        alpha_scale = shell[6]
        img = draw_layer(img, lines, GLOW, core * 3.4 * width_scale, 0.15 * alpha_scale)
        img = draw_layer(img, lines, MID, core * 1.5 * width_scale, 0.42 * alpha_scale)
        img = draw_layer(img, lines, CORE, core * 0.5 * width_scale, 0.95 * alpha_scale)

    marks = slashes(0.0, 99)
    img = draw_layer(img, marks, GLOW, core * 3.4 * 0.85, 0.14)
    img = draw_layer(img, marks, MID, core * 1.5 * 0.85, 0.38)
    img = draw_layer(img, marks, CORE, core * 0.5 * 0.85, 0.86)

    img.convert("RGB").save("work/tornado-preview.png")
    print("saved work/tornado-preview.png core=%.2fpx" % core)


if __name__ == "__main__":
    main()
