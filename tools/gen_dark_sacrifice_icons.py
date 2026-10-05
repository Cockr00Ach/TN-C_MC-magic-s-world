# -*- coding: utf-8 -*-
"""
gen_dark_sacrifice_icons.py -- 32x32 icons for the DARK SACRIFICE chain ("以伤换伤").

Author 2026-10-09 (chain rework). The magic stone draws one icon per spell from
assets/tnc/textures/spell/<spell id>.png; these five were missing, so the whole
chain showed as the magenta "missing texture" square in the GUI.
(They are missing in the LIVE instance too -- config/ is not covered by sync.cmd.)

Ids:
    trade_wounds (t1) a cut palm, drops falling
    blood_burn   (t2) the same cut, the blood on FIRE
    sacrifice    (t3) a kneeling figure inside a blood ring
    possess      (t4) a hand gripping a soul-shape, taking it over
    i_am_god     (t5) a figure of blood with a crown, halo of dark fire

Art: dark red-violet blood + one violet-white highlight, same supersampled PIL
approach as gen_dark_fog_icons.py / gen_dark_summon_icons.py. ASCII only.
Usage: python tools/gen_dark_sacrifice_icons.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
SS = 8
W = SIZE * SS

BLOOD = (138, 22, 52, 255)        # the body of the blood
BLOOD_DK = (74, 10, 30, 255)      # shadowed blood
BLOOD_LT = (206, 48, 88, 255)     # lit blood
FLAME = (238, 96, 168, 255)       # soul-fire pink
VOID = (18, 8, 20, 255)           # the dark
PALE = (236, 214, 238, 255)       # the stolen soul / highlight


def layer():
    return Image.new("RGBA", (W, W), (0, 0, 0, 0))


def drop(d, x, y, r, fill=BLOOD):
    """A blood droplet: round bottom, point on top."""
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill)
    d.polygon([(x - r * 0.75, y - r * 0.2), (x + r * 0.75, y - r * 0.2),
               (x, y - r * 2.6)], fill=fill)


def flame(d, x, y, r, fill=FLAME):
    """A licking flame."""
    d.polygon([(x - r, y + r), (x + r, y + r), (x + r * 0.5, y - r * 0.4),
               (x, y - r * 2.0), (x - r * 0.5, y - r * 0.4)], fill=fill)


def ring(d, cx, cy, r, width, fill, dotted=0):
    for i in range(240):
        a = 2 * math.pi * i / 240.0
        if dotted and int((i / 240.0) * dotted) % 2:
            continue
        x = cx + math.cos(a) * r
        y = cy + math.sin(a) * r * 0.42          # ground ellipse
        d.ellipse([x - width, y - width, x + width, y + width], fill=fill)


def figure(d, cx, cy, h, fill, crown=False, arms=False):
    d.polygon([(cx - 0.20 * h, cy - 0.10 * h), (cx + 0.20 * h, cy - 0.10 * h),
               (cx + 0.32 * h, cy + 0.42 * h), (cx - 0.32 * h, cy + 0.42 * h)], fill=fill)
    hr = 0.17 * h
    d.ellipse([cx - hr, cy - 0.42 * h - hr, cx + hr, cy - 0.42 * h + hr], fill=fill)
    if arms:
        for s in (-1, 1):
            d.line([(cx + s * 0.18 * h, cy - 0.02 * h),
                    (cx + s * 0.52 * h, cy - 0.26 * h)], fill=fill, width=int(0.08 * h))
    if crown:
        for k in (-1, 0, 1):
            x = cx + k * hr * 0.66
            d.polygon([(x - hr * 0.22, cy - 0.56 * h), (x + hr * 0.22, cy - 0.56 * h),
                       (x, cy - 0.86 * h)], fill=PALE)


def icon(tier):
    c = layer()
    d = ImageDraw.Draw(c)

    if tier == 1:
        # a cut across an open palm, three drops falling
        d.rounded_rectangle([W * 0.24, W * 0.26, W * 0.76, W * 0.56], radius=W * 0.10,
                            fill=BLOOD_DK, outline=BLOOD_LT, width=int(W * 0.012))
        d.rounded_rectangle([W * 0.30, W * 0.30, W * 0.70, W * 0.52], radius=W * 0.06, fill=BLOOD)
        # the cut itself
        d.line([(W * 0.30, W * 0.38), (W * 0.70, W * 0.46)], fill=VOID, width=int(W * 0.030))
        for (fx, fy, fr) in ((0.40, 0.72, 0.055), (0.52, 0.82, 0.045), (0.63, 0.70, 0.038)):
            drop(d, W * fx, W * fy, W * fr)

    elif tier == 2:
        # the cut is on FIRE: blood drops turning into flame
        d.rounded_rectangle([W * 0.24, W * 0.30, W * 0.76, W * 0.58], radius=W * 0.10,
                            fill=BLOOD_DK, outline=BLOOD_LT, width=int(W * 0.012))
        d.rounded_rectangle([W * 0.30, W * 0.34, W * 0.70, W * 0.54], radius=W * 0.06, fill=BLOOD)
        d.line([(W * 0.30, W * 0.42), (W * 0.70, W * 0.50)], fill=VOID, width=int(W * 0.026))
        flame(d, W * 0.38, W * 0.78, W * 0.11)
        flame(d, W * 0.60, W * 0.80, W * 0.09, BLOOD_LT)
        flame(d, W * 0.50, W * 0.90, W * 0.06)
        drop(d, W * 0.30, W * 0.92, W * 0.035)

    elif tier == 3:
        # a kneeling figure inside a blood ring
        ring(d, W * 0.5, W * 0.76, W * 0.36, W * 0.030, BLOOD_LT, dotted=8)
        ring(d, W * 0.5, W * 0.76, W * 0.28, W * 0.018, BLOOD)
        figure(d, W * 0.5, W * 0.62, W * 0.56, BLOOD, arms=True)
        d.polygon([(W * 0.50 - W * 0.30, W * 0.80), (W * 0.50 + W * 0.30, W * 0.80),
                   (W * 0.50 + W * 0.16, W * 0.64), (W * 0.50 - W * 0.16, W * 0.64)], fill=BLOOD_DK)
        drop(d, W * 0.5, W * 0.88, W * 0.045)

    elif tier == 4:
        # an open hand gripping a pale soul -- the soul is a small figure, not a blob
        d.rounded_rectangle([W * 0.22, W * 0.58, W * 0.78, W * 0.80], radius=W * 0.08,
                            fill=BLOOD_DK, outline=BLOOD_LT, width=int(W * 0.012))
        for i in range(4):
            x = W * (0.28 + 0.145 * i)
            d.rounded_rectangle([x - W * 0.045, W * 0.34, x + W * 0.045, W * 0.62],
                                radius=W * 0.035, fill=BLOOD)
        # the stolen soul: a tiny pale figure with two dark eyes
        d.ellipse([W * 0.38, W * 0.12, W * 0.62, W * 0.36], fill=PALE)
        d.polygon([(W * 0.40, W * 0.34), (W * 0.60, W * 0.34),
                   (W * 0.56, W * 0.48), (W * 0.44, W * 0.48)], fill=PALE)
        d.ellipse([W * 0.43, W * 0.20, W * 0.47, W * 0.26], fill=VOID)
        d.ellipse([W * 0.53, W * 0.20, W * 0.57, W * 0.26], fill=VOID)
        ring(d, W * 0.5, W * 0.84, W * 0.22, W * 0.024, FLAME, dotted=8)

    else:
        # a figure of blood, crowned, inside a halo of dark fire
        ring(d, W * 0.5, W * 0.54, W * 0.42, W * 0.032, FLAME, dotted=10)
        figure(d, W * 0.5, W * 0.64, W * 0.62, BLOOD, crown=True, arms=True)
        # a dark core with a lit rim, so it reads as "god" and not as a red blob
        d.ellipse([W * 0.36, W * 0.40, W * 0.64, W * 0.64], fill=VOID)
        d.ellipse([W * 0.40, W * 0.44, W * 0.60, W * 0.60], fill=BLOOD_LT)
        d.ellipse([W * 0.44, W * 0.48, W * 0.56, W * 0.56], fill=VOID)
        for i in range(6):
            a = 2 * math.pi * i / 6.0 - math.pi / 2.0
            x = W * 0.5 + math.cos(a) * W * 0.34
            y = W * 0.54 + math.sin(a) * W * 0.26
            flame(d, x, y, W * 0.040, FLAME)

    return compose(c)


def compose(core, glow=0.035):
    blur = core.filter(ImageFilter.GaussianBlur(W * glow))
    return Image.alpha_composite(blur, core).resize((SIZE, SIZE), Image.LANCZOS)


ICONS = {
    "trade_wounds": icon(1),
    "blood_burn": icon(2),
    "sacrifice": icon(3),
    "possess": icon(4),
    "i_am_god": icon(5),
}

OUT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "spell")


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(repo, OUT)
    os.makedirs(out, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 4 * len(ICONS), SIZE * 4), (24, 24, 32, 255))
    for i, (name, im) in enumerate(ICONS.items()):
        p = os.path.join(out, name + ".png")
        im.save(p)
        sheet.paste(im.resize((SIZE * 4, SIZE * 4), Image.NEAREST), (i * SIZE * 4, 0))
        print("icon -> %s.png (%d bytes)" % (name, os.path.getsize(p)))
    preview = os.path.join(repo, "docs", "previews")
    os.makedirs(preview, exist_ok=True)
    sheet.save(os.path.join(preview, "dark_sacrifice_icons.png"))
    print("preview -> docs/previews/dark_sacrifice_icons.png")


if __name__ == "__main__":
    main()
