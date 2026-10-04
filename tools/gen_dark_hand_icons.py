# -*- coding: utf-8 -*-
"""gen_dark_hand_icons.py -- 16x16 spell icons for the "night hand" chain.

The chain's five spells (night_hand / night_raid / night_embrace / black_ruin / slay_light)
had no icons at all, so the magic-stone UI logged

    Failed to load texture: tnc:textures/spell/night_hand.png

and drew a missing-texture square. The other 86 spells in the mod all have a 16x16 icon under
assets/tnc/textures/spell/<spell_id>.png, so this script makes the five missing ones from the
chain's own projectile art (assets/tnc/textures/spell_projectile/dark_hand.png) -- same
silhouette the spell actually throws, tinted per tier so they are distinguishable.

Placeholders by design: the author can repaint them, the pipeline (path + 16x16 + alpha) is
what matters here.
"""
import io
import os

from PIL import Image

SRC = os.path.join("src", "main", "resources", "assets", "tnc", "textures",
                   "spell_projectile", "dark_hand.png")
OUT_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "spell")

# spell id -> (tint RGB, brightness)
ICONS = {
    "night_hand": ((150, 130, 235), 1.00),
    "night_raid": ((178, 150, 255), 1.12),
    "night_embrace": ((120, 95, 215), 0.95),
    "black_ruin": ((95, 70, 190), 0.88),
    "slay_light": ((205, 175, 255), 1.25),
}


def load_source():
    """The hand art, cropped to its opaque bounds so the icon is not mostly empty."""
    im = Image.open(SRC).convert("RGBA")
    bbox = im.split()[3].getbbox()
    if bbox:
        im = im.crop(bbox)
    return im


def make_icon(hand, tint, gain):
    # fit into 16x16 with a 1px margin, then tint the RGB and keep the alpha
    box = 14
    w, h = hand.size
    scale = min(box / w, box / h)
    small = hand.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    icon.paste(small, ((16 - small.width) // 2, (16 - small.height) // 2))

    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    src = icon.load()
    dst = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            # keep the original shading, push it towards the tier tint
            lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0 * gain
            rr = min(255, int(tint[0] * lum * 1.15))
            gg = min(255, int(tint[1] * lum * 1.15))
            bb = min(255, int(tint[2] * lum * 1.15))
            dst[x, y] = (rr, gg, bb, a)
    return out


def main():
    hand = load_source()
    os.makedirs(OUT_DIR, exist_ok=True)
    for spell, (tint, gain) in ICONS.items():
        icon = make_icon(hand, tint, gain)
        path = os.path.join(OUT_DIR, spell + ".png")
        icon.save(path, "PNG")
        print("wrote %s (%dx%d)" % (path, icon.width, icon.height))


if __name__ == "__main__":
    main()
