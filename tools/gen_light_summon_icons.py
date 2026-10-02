# -*- coding: utf-8 -*-
"""
gen_light_summon_icons.py -- 32x32 icons for the light chain #4 (summon_angel).

Author 2026-10-02 asked for the 4th light chain (summon angels). The magic stone draws
`assets/tnc/textures/spell/<spell id>.png` (see MagicStoneScreen.iconFor), so every new
spell needs one or the button shows the missing-texture checkerboard.

Art direction follows tools/gen_light_textures.ps1 (the light chain icons): pure white
core + a very faint pale yellow (255,250,205) accent, everything anti-aliased into alpha 0
(4x supersampling + a blurred glow layer underneath), no hard borders.

  summon_angel    one angel
  angel_twins     two angels
  angel_legion    three angels
  seraph_descent  one big angel under a falling beam
  archangel       one big angel + halo ring + raised blade

ASCII only (the project rule for tools/*).
Usage:  python tools/gen_light_summon_icons.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
SS = 8                      # supersampling factor
W = SIZE * SS
WHITE = (255, 255, 255, 255)
PALE = (255, 250, 205, 255)


def layer():
    return Image.new("RGBA", (W, W), (0, 0, 0, 0))


def angel(d, cx, cy, h, blade=False, halo=True):
    """One winged figure. (cx, cy) = centre of the body, h = total figure height."""
    # ---- wings (two swept shapes behind the body) ----
    for sign in (-1.0, 1.0):
        d.polygon([
            (cx + sign * 0.10 * h, cy - 0.26 * h),
            (cx + sign * 0.52 * h, cy - 0.52 * h),
            (cx + sign * 0.72 * h, cy - 0.22 * h),
            (cx + sign * 0.60 * h, cy + 0.10 * h),
            (cx + sign * 0.26 * h, cy + 0.04 * h),
        ], fill=PALE)
    # ---- robe / body ----
    d.polygon([
        (cx - 0.13 * h, cy - 0.30 * h),
        (cx + 0.13 * h, cy - 0.30 * h),
        (cx + 0.22 * h, cy + 0.50 * h),
        (cx - 0.22 * h, cy + 0.50 * h),
    ], fill=WHITE)
    # ---- head ----
    r = 0.13 * h
    d.ellipse([cx - r, cy - 0.50 * h - r, cx + r, cy - 0.50 * h + r], fill=WHITE)
    # ---- halo ----
    if halo:
        rr = 0.21 * h
        hw = max(1.0, 0.035 * h)
        d.ellipse([cx - rr, cy - 0.60 * h - rr * 0.42, cx + rr, cy - 0.60 * h + rr * 0.42],
                  outline=PALE, width=int(round(hw)))
    # ---- raised blade (archangel only) ----
    if blade:
        d.polygon([
            (cx + 0.30 * h, cy - 0.62 * h),
            (cx + 0.38 * h, cy - 0.60 * h),
            (cx + 0.34 * h, cy + 0.16 * h),
            (cx + 0.26 * h, cy + 0.16 * h),
        ], fill=WHITE)


def beam(d, cx, w, y0, y1):
    """A vertical light column, fading with height (drawn as stacked trapezia)."""
    steps = 24
    for i in range(steps):
        t = i / float(steps)
        y = y0 + (y1 - y0) * t
        half = w * (0.35 + 0.65 * t)
        d.polygon([(cx - half, y), (cx + half, y),
                   (cx + half * 1.04, y + (y1 - y0) / steps + 1.0),
                   (cx - half * 1.04, y + (y1 - y0) / steps + 1.0)],
                  fill=(255, 255, 255, int(210 * (1.0 - 0.55 * t))))


def compose(core, glow_radius):
    """Blur a copy into a soft glow, then lay the crisp core on top."""
    glow = core.filter(ImageFilter.GaussianBlur(glow_radius))
    out = Image.alpha_composite(glow, core)
    return out.resize((SIZE, SIZE), Image.LANCZOS)


def icon_summon_angel():
    c = layer()
    angel(ImageDraw.Draw(c), W * 0.5, W * 0.55, W * 0.86)
    return compose(c, W * 0.035)


def icon_angel_twins():
    c = layer()
    d = ImageDraw.Draw(c)
    angel(d, W * 0.33, W * 0.58, W * 0.62)
    angel(d, W * 0.67, W * 0.52, W * 0.72)
    return compose(c, W * 0.032)


def icon_angel_legion():
    c = layer()
    d = ImageDraw.Draw(c)
    angel(d, W * 0.24, W * 0.60, W * 0.52)
    angel(d, W * 0.50, W * 0.46, W * 0.58)
    angel(d, W * 0.76, W * 0.60, W * 0.52)
    return compose(c, W * 0.030)


def icon_seraph_descent():
    c = layer()
    d = ImageDraw.Draw(c)
    beam(d, W * 0.5, W * 0.15, 0.0, W * 0.34)
    angel(d, W * 0.5, W * 0.66, W * 0.72)
    return compose(c, W * 0.035)


def icon_archangel():
    c = layer()
    d = ImageDraw.Draw(c)
    # a wide halo ring behind everything (only the top half is visible above the wings)
    rr = W * 0.34
    ring = layer()
    ImageDraw.Draw(ring).ellipse([W * 0.5 - rr, W * 0.30 - rr * 0.40,
                                  W * 0.5 + rr, W * 0.30 + rr * 0.40],
                                 outline=(255, 250, 205, 190), width=int(round(W * 0.028)))
    angel(d, W * 0.5, W * 0.62, W * 0.82, blade=True, halo=False)
    c = Image.alpha_composite(ring, c)
    return compose(c, W * 0.035)


ICONS = {
    "summon_angel": icon_summon_angel,
    "angel_twins": icon_angel_twins,
    "angel_legion": icon_angel_legion,
    "seraph_descent": icon_seraph_descent,
    "archangel": icon_archangel,
}

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
    sheet.save(os.path.join(preview, "light_summon_icons.png"))
    print("preview -> docs/previews/light_summon_icons.png")


if __name__ == "__main__":
    main()
