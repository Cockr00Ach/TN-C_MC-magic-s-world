# -*- coding: utf-8 -*-
"""
gen_light_dragon_icons.py -- 32x32 icons for the light chain #5 (光龙).

Same art direction as the other light icons (pure white core + pale gold), but the shapes
are the Eastern dragon: a serpentine body drawn as a sine chain of dots, a head block with
horns, and a trailing flame tail.

  light_dragon_breath    small dragon head + a bright beam
  light_dragon_scales    a shield made of overlapping scales
  summon_light_dragon    one coiled dragon
  light_dragon_dive      a dragon diving diagonally with speed lines
  light_dragon_descend   a big coiled dragon + a halo of rays

ASCII only.  Usage:  python tools/gen_light_dragon_icons.py
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
SS = 8
W = SIZE * SS
WHITE = (255, 255, 255, 255)
GOLD = (255, 214, 120, 255)

# ★ --dark：同一批形状、暗龙配色 ✓（作者 2026-10-02："复制一下光龙，生成一个暗龙" ✓）
#   核心换成幽紫白、镶边换成紫红 ⇒ 16x16 下一眼分得出是暗系 ✓
DARK = "--dark" in sys.argv
if DARK:
    WHITE = (234, 222, 250, 255)
    GOLD = (168, 60, 200, 255)
PREFIX = "dark_dragon_" if DARK else "light_dragon_"


def layer():
    return Image.new("RGBA", (W, W), (0, 0, 0, 0))


def serpent(d, cx, cy, span, amp, waves, thick, col, phase=0.0, head=True):
    """the dragon body: a chain of dots along a sine, plus a head block at the start"""
    n = 26
    pts = []
    for i in range(n):
        t = i / float(n - 1)
        x = cx - span / 2.0 + span * t
        y = cy + amp * math.sin(2 * math.pi * waves * t + phase)
        pts.append((x, y))
    r = thick
    for i, (x, y) in enumerate(pts):
        k = 1.0 - 0.45 * (i / float(n - 1))
        rr = r * k
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=col)
    if head:
        hx, hy = pts[0]
        hr = thick * 1.5
        d.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=col)
        # horns
        for sgn in (-1, 1):
            d.line([(hx, hy - hr * 0.4), (hx - hr * 1.1, hy - hr * (1.6 if sgn < 0 else 1.1))],
                   fill=GOLD, width=int(round(thick * 0.35)))
            d.line([(hx - hr * 0.3, hy - hr * 0.9), (hx - hr * 1.5, hy - hr * 1.5)],
                   fill=GOLD, width=int(round(thick * 0.3)))
        # snout
        d.ellipse([hx - hr * 1.7, hy - hr * 0.35, hx - hr * 0.5, hy + hr * 0.55], fill=col)
    return pts


def scales_shield(d, cx, cy, r):
    """a shield of overlapping scales"""
    d.polygon([(cx, cy - r), (cx + r * 0.92, cy - r * 0.5), (cx + r * 0.72, cy + r * 0.72),
               (cx, cy + r), (cx - r * 0.72, cy + r * 0.72), (cx - r * 0.92, cy - r * 0.5)],
              fill=WHITE)
    for row in range(3):
        yy = cy - r * 0.55 + row * r * 0.55
        for col in range(3 - (row % 2)):
            xx = cx - r * 0.5 + col * r * 0.55 + (r * 0.27 if row % 2 else 0.0)
            rr = r * 0.22
            d.ellipse([xx - rr, yy - rr * 1.1, xx + rr, yy + rr * 1.1], fill=GOLD)


def compose(core, glow_radius):
    glow = core.filter(ImageFilter.GaussianBlur(glow_radius))
    return Image.alpha_composite(glow, core).resize((SIZE, SIZE), Image.LANCZOS)


def icon_breath():
    c = layer()
    d = ImageDraw.Draw(c)
    d.polygon([(W * 0.10, W * 0.44), (W * 0.78, W * 0.30), (W * 0.78, W * 0.58),
               (W * 0.10, W * 0.52)], fill=WHITE)                 # the beam
    serpent(d, W * 0.36, W * 0.72, W * 0.5, W * 0.05, 1.0, W * 0.045, GOLD)
    return compose(c, W * 0.02)


def icon_scales():
    c = layer()
    scales_shield(ImageDraw.Draw(c), W * 0.5, W * 0.5, W * 0.34)
    return compose(c, W * 0.025)


def icon_summon():
    c = layer()
    serpent(ImageDraw.Draw(c), W * 0.5, W * 0.5, W * 0.78, W * 0.16, 1.5, W * 0.05, WHITE)
    return compose(c, W * 0.028)


def icon_dive():
    c = layer()
    d = ImageDraw.Draw(c)
    for i in range(3):                                            # speed lines
        d.line([(W * (0.10 + i * 0.06), W * 0.20), (W * (0.34 + i * 0.06), W * 0.34)],
               fill=GOLD, width=int(round(W * 0.018)))
    pts = serpent(d, W * 0.52, W * 0.45, W * 0.42, W * 0.14, 1.0, W * 0.045, WHITE)
    body = [(x + W * 0.16, y + W * 0.16) for x, y in pts]          # tilt it down-forward
    for x, y in body:
        d.ellipse([x - W * 0.03, y - W * 0.03, x + W * 0.03, y + W * 0.03], fill=WHITE)
    return compose(c, W * 0.026)


def icon_descend():
    c = layer()
    d = ImageDraw.Draw(c)
    for i in range(12):                                            # halo rays
        a = 2 * math.pi * i / 12.0
        d.line([(W * 0.5 + math.cos(a) * W * 0.30, W * 0.5 + math.sin(a) * W * 0.30),
                (W * 0.5 + math.cos(a) * W * 0.44, W * 0.5 + math.sin(a) * W * 0.44)],
               fill=GOLD, width=int(round(W * 0.016)))
    serpent(d, W * 0.5, W * 0.5, W * 0.62, W * 0.13, 1.25, W * 0.048, WHITE)
    return compose(c, W * 0.03)


ICONS = {}
for _short, _fn in (("breath", icon_breath), ("scales", icon_scales), ("charge", icon_summon),
                    ("dive", icon_dive), ("descend", icon_descend)):
    ICONS[PREFIX + _short] = _fn
if not DARK:
    # 光龙那条链的 t3 法术 id 是历史遗留的 summon_light_dragon ✓（显示名早已是"光龙出击"✓）
    # 暗龙那条链用的是干净名字 dark_dragon_charge ✓
    ICONS["summon_light_dragon"] = ICONS.pop("light_dragon_charge")

OUT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "spell")


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(repo, OUT)
    os.makedirs(out, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 4 * len(ICONS), SIZE * 4), (24, 24, 32, 255))
    for i, (name, fn) in enumerate(ICONS.items()):
        im = fn()
        p = os.path.join(out, name + ".png")
        im.save(p)
        sheet.paste(im.resize((SIZE * 4, SIZE * 4), Image.NEAREST), (i * SIZE * 4, 0))
        print("icon -> %s.png (%d bytes)" % (name, os.path.getsize(p)))
    preview = os.path.join(repo, "docs", "previews")
    os.makedirs(preview, exist_ok=True)
    sheet_name = "dark_dragon_icons.png" if DARK else "light_dragon_icons.png"
    sheet.save(os.path.join(preview, sheet_name))
    print("preview -> docs/previews/%s" % sheet_name)


if __name__ == "__main__":
    main()
