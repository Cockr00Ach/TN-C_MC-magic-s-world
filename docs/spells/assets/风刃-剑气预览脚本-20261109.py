"""风刃（战士剑气式弯月）预览：按 WindBladeQi 的几何复算，画四层 + 两道残影 + 刃口。

参数必须和 src/main/java/com/tnc/tnc/client/WindBladeQi.java 保持一致。
"""
import math
from PIL import Image, ImageDraw, ImageFilter

SEGMENTS = 40
BOW = 0.34
WIDTHS = (0.34, 0.22, 0.11, 0.035)
GLOW = (26, 184, 133)
MID = (158, 255, 219)
CORE = (255, 255, 255)
WHITE = (250, 255, 255)
ALPHAS = (0.16, 0.45, 0.85, 0.95)
COLORS = (GLOW, MID, MID, CORE)

BASE_SPAN = 1.179
SIZE = 2.4
HALF = BASE_SPAN * SIZE * 0.5

W, H = 900, 620
PIXELS_PER_BLOCK = 150.0


def outer(span, t, z=0.0):
    return (t * span, -BOW * span * (1.0 - t * t), z)


def inner(span, t, thickness):
    x, y, z = outer(span, t)
    k = max(0.0, 1.0 - t * t) ** 0.35
    return (x, y + thickness * span * k, z)


def taper(t):
    return max(0.0, 1.0 - t * t) ** 0.35


def project(p, yaw, pitch, cx, cy):
    x, y, z = p
    # yaw
    xr = x * math.cos(yaw) + z * math.sin(yaw)
    zr = -x * math.sin(yaw) + z * math.cos(yaw)
    # pitch
    yr = y * math.cos(pitch) - zr * math.sin(pitch)
    zr2 = y * math.sin(pitch) + zr * math.cos(pitch)
    depth = 1.0 + zr2 * 0.02
    return (cx + xr * PIXELS_PER_BLOCK * depth, cy - yr * PIXELS_PER_BLOCK * depth)


def draw_band(img, span, thickness, colour, alpha, yaw, pitch, cx, cy, z_shift=0.0):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(SEGMENTS):
        a = -1 + i * 2 / SEGMENTS
        b = -1 + (i + 1) * 2 / SEGMENTS
        oa = outer(span, a, z_shift)
        ob = outer(span, b, z_shift)
        ia = inner(span, a, thickness)
        ib = inner(span, b, thickness)
        ia = (ia[0], ia[1], ia[2] + z_shift)
        ib = (ib[0], ib[1], ib[2] + z_shift)
        quad = [project(oa, yaw, pitch, cx, cy), project(ob, yaw, pitch, cx, cy),
                project(ib, yaw, pitch, cx, cy), project(ia, yaw, pitch, cx, cy)]
        fa = alpha * taper(a)
        fb = alpha * taper(b)
        avg = (fa + fb) * 0.5
        d.polygon(quad, fill=colour + (int(255 * max(0.0, min(1.0, avg))),))
    return Image.alpha_composite(img, layer)


def main():
    img = Image.new("RGBA", (W, H), (6, 8, 11, 255))
    cx, cy = W / 2, H / 2
    # 三个视角：正面 / 斜 35° / 侧面（几乎只看得到刃口）
    for idx, (yaw, pitch, ox, oy) in enumerate((
            (0.0, 0.0, -260, 0),
            (math.radians(35), math.radians(-12), 0, 0),
            (math.radians(78), 0.0, 270, 0))):
        ccx, ccy = cx + ox, cy + oy
        # 残影
        for echo in (2, 1):
            z = -echo * HALF * 0.10
            img = draw_band(img, HALF, WIDTHS[0] * 1.15, GLOW, 0.12 if echo == 2 else 0.26,
                            yaw, pitch, ccx, ccy, z)
            img = draw_band(img, HALF, WIDTHS[2], MID, 0.10 if echo == 2 else 0.22,
                            yaw, pitch, ccx, ccy, z)
        # 四层
        for w, col, a in zip(WIDTHS, COLORS, ALPHAS):
            img = draw_band(img, HALF, w, col, a, yaw, pitch, ccx, ccy)
        # 刃口（外缘沿 Z 的薄带）
        img = draw_band(img, HALF, WIDTHS[0] * 0.16, WHITE, 0.55, yaw, pitch, ccx, ccy, HALF * 0.055)

    img.convert("RGB").save("work/windblade-preview.png")
    print("saved work/windblade-preview.png  half=%.2f blocks span=%.2f" % (HALF, HALF * 2))


if __name__ == "__main__":
    main()
