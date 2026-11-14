"""风球漩涡线预览：按 WindBallLines.vortex 的算法复算，画三档 + 渐变线。

参数和 src/main/java/com/tnc/tnc/client/WindBallLines.java 一致。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

SEGMENTS = 24
FINE_SEGMENTS = 10
VORTEX_WIDE = 1.05
VORTEX_NARROW = 0.28
VORTEX_HEIGHT = 2.40
SHOCK_RINGS = 3
STREAKS = 10

GLOW = (26, 184, 133)
MID = (158, 255, 219)
CORE = (255, 255, 255)
PALETTE = {0: GLOW, 1: MID, 2: CORE}

W, H = 1200, 520


def count(t):
    return 3 * (4 + max(1, min(5, t)))


def bold_count(t):
    return max(1, count(t) // 3)


def turns(t):
    return 1.75 + 0.16 * max(1, min(5, t))


def vortex(radius, tier, spin, seed):
    total, bold, base_turns = count(tier), bold_count(tier), turns(tier)
    rnd = random.Random((seed * 0x2545F4914F6CDD1D) & 0xFFFFFFFFFFFFFFFF | tier)
    out = []
    for i in range(total):
        is_bold = i < bold
        seg = SEGMENTS if is_bold else FINE_SEGMENTS
        phase = i * (2 * math.pi / total) + (rnd.random() - 0.5) * 0.45
        turns_i = base_turns * (0.82 + rnd.random() * 0.5) * (1.0 if is_bold else 1.12)
        drift = (rnd.random() - 0.5) * 0.35
        wide = VORTEX_WIDE * (0.92 + rnd.random() * 0.20)
        narrow = VORTEX_NARROW * (0.85 + rnd.random() * 0.35)
        wobble = 0.03 + rnd.random() * 0.07
        frm = 0.0 if is_bold else rnd.random() * 0.55
        to = 1.0 if is_bold else min(1.0, frm + 0.30 + rnd.random() * 0.25)
        local_turns = turns_i * (to - frm)
        pts = []
        for k in range(seg + 1):
            local = k / seg
            u = frm + (to - frm) * local
            r = radius * (narrow + (wide - narrow) * u)
            theta = spin + phase + drift * u + local_turns * 2 * math.pi * local
            y = radius * VORTEX_HEIGHT * (u - 0.5)
            wob = 1 + wobble * math.sin(u * 7 + phase * 3)
            pts.append((math.cos(theta) * r * wob, y, math.sin(theta) * r * wob))
        ws = (1.15 + rnd.random() * 0.55) if is_bold else (0.45 + rnd.random() * 0.45)
        al = (0.80 + rnd.random() * 0.20) if is_bold else (0.30 + rnd.random() * 0.40)
        pal = (2 if rnd.random() < 0.55 else 1) if is_bold else (0 if rnd.random() < 0.55 else 1)
        out.append((pts, ws, al, pal))
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


YAW = math.radians(20)
PITCH = math.radians(-16)


def project(p, cx, cy):
    x, y, z = p
    xr = x * math.cos(YAW) + z * math.sin(YAW)
    zr = -x * math.sin(YAW) + z * math.cos(YAW)
    yr = y * math.cos(PITCH) - zr * math.sin(PITCH)
    return (cx + xr, cy - yr)


def draw_line(img, pts, colour, width, alpha, cx, cy):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.line([project(p, cx, cy) for p in pts], fill=colour + (int(255 * max(0.0, min(1.0, alpha))),),
           width=max(1, int(round(width))), joint="curve")
    lay = lay.filter(ImageFilter.GaussianBlur(max(0.5, width * 0.35)))
    return Image.alpha_composite(img, lay)


def main():
    img = Image.new("RGBA", (W, H), (5, 7, 10, 255))
    panels = [(1, 150.0, 210, 0.6), (3, 165.0, 600, 1.1), (5, 180.0, 990, 1.6)]
    for tier, radius, cx, spin in panels:
        cy = H / 2
        for pts, ws, al, pal in vortex(radius, tier, spin, 99):
            img = draw_line(img, pts, PALETTE[pal], max(1.0, radius * 0.018 * ws * 1.6), al * 0.5, cx, cy)
        for ring in shock_rings(radius * 0.0, 0.0):
            pass
        d = ImageDraw.Draw(img)
        d.text((cx - 46, H - 36), f"t{tier} / {count(tier)} lines", fill=(200, 220, 230, 255))

    # 右边再放一个炸开的样子
    cx, cy, radius = 1180, H / 2, 120.0
    img = img.convert("RGBA")
    for ring in shock_rings(radius, 0.55):
        img = draw_line(img, ring, MID, 5, 0.5, cx, cy)

    img.convert("RGB").save("work/windvortex-preview.png")
    print("saved work/windvortex-preview.png")


if __name__ == "__main__":
    main()
