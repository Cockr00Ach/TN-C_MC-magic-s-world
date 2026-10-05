# -*- coding: utf-8 -*-
"""
gen_dark_fog_icons.py -- 32x32 icons for the BLACK FOG chain (dark chain 4).

Author 2026-10-09 (black fog redesign). The magic stone draws one icon per spell
from assets/tnc/textures/spell/<spell id>.png; these five were missing, so the
whole chain showed as the magenta "missing texture" square in the GUI.

Ids (the pack already defines these five spells):
    black_mist                 (t1) a low pool of mist
    night_grace                (t2) the pool + drifting wisps and embers
    dark_city                  (t3) towers drowning in the fog
    where_light_cannot_reach   (t4) a chasm of unlight
    devour_light               (t5) a black sun eating a bright ring

Art: near-black violet body, one magenta ember accent per tier, drawn with the
same supersampled PIL approach as gen_dark_summon_icons.py. ASCII only.
Usage: python tools/gen_dark_fog_icons.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
SS = 8
W = SIZE * SS

VOID = (12, 8, 20, 255)          # the black body of the fog
DEEP = (36, 22, 58, 255)         # violet shadow
MID = (74, 44, 110, 255)         # violet body
RIM = (150, 84, 190, 255)        # rim light
EMBER = (236, 108, 236, 255)     # accent ember
GLOW = (206, 160, 240, 255)      # wisp


def layer():
    return Image.new("RGBA", (W, W), (0, 0, 0, 0))


def blob(d, cx, cy, r, fill, squash=0.55, n=7):
    """A lumpy cloud puff (a plain ellipse reads as a ball, not as mist)."""
    for i in range(n):
        a = 2 * math.pi * i / n
        rr = r * (0.55 + 0.30 * ((i * 5) % 4) / 3.0)
        x = cx + math.cos(a) * r * 0.62
        y = cy + math.sin(a) * r * squash
        d.ellipse([x - rr, y - rr * squash, x + rr, y + rr * squash], fill=fill)


def pool(d, cy, w, h, fill):
    """The flat ground pool: wider than tall, with a ragged top."""
    d.ellipse([W * 0.5 - w, cy - h, W * 0.5 + w, cy + h], fill=fill)
    for i in range(9):
        t = (i + 0.5) / 9.0
        x = W * 0.5 - w + 2 * w * t
        bump = h * (0.4 + 0.6 * ((i * 3) % 4) / 3.0)
        d.ellipse([x - w * 0.16, cy - bump - h * 0.3, x + w * 0.16, cy - h * 0.3], fill=fill)


def wisp(d, x, y, r, fill):
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill)
    d.ellipse([x - r * 0.55, y - r * 2.1, x + r * 0.55, y - r * 0.2], fill=fill)


def icon(tier):
    c = layer()
    d = ImageDraw.Draw(c)

    # every tier has the same base: a pool of black fog lying on the ground
    pool(d, W * 0.70, W * (0.30 + 0.015 * tier), W * (0.13 + 0.008 * tier), VOID)
    blob(d, W * 0.5, W * 0.62, W * (0.20 + 0.012 * tier), DEEP, n=6 + tier)

    if tier == 1:
        # just the pool, a couple of slow curls
        blob(d, W * 0.38, W * 0.52, W * 0.09, MID, n=5)
        blob(d, W * 0.66, W * 0.50, W * 0.07, MID, n=5)

    elif tier == 2:
        # wisps drift up out of it
        blob(d, W * 0.40, W * 0.54, W * 0.10, MID, n=6)
        blob(d, W * 0.64, W * 0.50, W * 0.09, MID, n=6)
        wisp(d, W * 0.44, W * 0.34, W * 0.045, GLOW)
        wisp(d, W * 0.60, W * 0.28, W * 0.035, GLOW)
        d.ellipse([W * 0.30, W * 0.72, W * 0.34, W * 0.76], fill=EMBER)

    elif tier == 3:
        # towers drowning in the fog
        for (tx, th) in ((0.30, 0.30), (0.47, 0.42), (0.66, 0.34)):
            x = W * tx
            top = W * (0.66 - th)
            d.rectangle([x - W * 0.045, top, x + W * 0.045, W * 0.72], fill=DEEP)
            d.polygon([(x - W * 0.06, top), (x + W * 0.06, top), (x, top - W * 0.06)], fill=MID)
            d.rectangle([x - W * 0.012, top + W * 0.05, x + W * 0.012, top + W * 0.08], fill=EMBER)
        blob(d, W * 0.5, W * 0.74, W * 0.34, VOID, n=8)

    elif tier == 4:
        # a chasm of unlight: a black maw with a rim of violet
        d.ellipse([W * 0.16, W * 0.20, W * 0.84, W * 0.82], fill=DEEP)
        d.ellipse([W * 0.24, W * 0.28, W * 0.76, W * 0.74], fill=VOID)
        for i in range(16):
            a = 2 * math.pi * i / 16
            x = W * 0.5 + math.cos(a) * W * 0.30
            y = W * 0.51 + math.sin(a) * W * 0.27
            d.ellipse([x - W * 0.022, y - W * 0.022, x + W * 0.022, y + W * 0.022], fill=RIM)
        blob(d, W * 0.5, W * 0.60, W * 0.22, VOID, n=7)

    else:
        # a black sun eating a bright ring
        d.ellipse([W * 0.10, W * 0.12, W * 0.90, W * 0.88], fill=VOID)
        rr = W * 0.33
        for i in range(48):
            a = 2 * math.pi * i / 48
            x = W * 0.5 + math.cos(a) * rr
            y = W * 0.5 + math.sin(a) * rr
            col = EMBER if i % 4 == 0 else RIM
            d.ellipse([x - W * 0.020, y - W * 0.020, x + W * 0.020, y + W * 0.020], fill=col)
        d.ellipse([W * 0.34, W * 0.35, W * 0.66, W * 0.67], fill=(4, 2, 8, 255))
        for i in range(7):
            a = 2 * math.pi * i / 7 + 0.4
            x = W * 0.5 + math.cos(a) * W * 0.13
            y = W * 0.5 + math.sin(a) * W * 0.13
            d.ellipse([x - W * 0.016, y - W * 0.016, x + W * 0.016, y + W * 0.016], fill=EMBER)

    return compose(c)


def compose(core, glow=0.035):
    blur = core.filter(ImageFilter.GaussianBlur(W * glow))
    return Image.alpha_composite(blur, core).resize((SIZE, SIZE), Image.LANCZOS)


ICONS = {
    "black_mist": icon(1),
    "night_grace": icon(2),
    "dark_city": icon(3),
    "where_light_cannot_reach": icon(4),
    "devour_light": icon(5),
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
    sheet.save(os.path.join(preview, "dark_fog_icons.png"))
    print("preview -> docs/previews/dark_fog_icons.png")


if __name__ == "__main__":
    main()
