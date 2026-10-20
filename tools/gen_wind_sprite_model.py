#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_wind_sprite_model.py -- the WIND SPIRIT model for wind chain 2 (the summon chain:
wind_orb -> wind_orb_swarm -> wind_spirit -> triple_wind_spirit -> wind_spirit_lord).

Author 2026-10-10: "the wind spirit chain: why are all five just one little wind ball
orbiting the player? Where is the wind spirit? I think you could copy the sprite model."
=> the chain no longer draws particles; it spawns real creatures (magic/TNWindSpiritEntity),
and this is what they look like: a small winged wisp.

WHY NOT COPY THE PIXIE
    The obvious "sprite" in this pack is Ice and Fire's Pixie, but this project's rule is
    NOT to ship another mod's art (see docs/spells/water-magic-feedback-20260928.md:
    "no textures, models or code were copied from those mods"). So this is an original
    wisp built procedurally, in the same spirit as gen_dark_hand_model.py / the dark_imp.

WHAT IT WRITES
    1. src/main/resources/assets/tnc/geo/entity/wind_sprite.geo.json
       Bedrock geometry 1.12.0 (same shape as geo/entity/dark_imp.geo.json)
    2. src/main/resources/assets/tnc/animations/entity/wind_sprite.animation.json
       "idle": hover bob + wing flap + crest/tail sway (sampled keyframes, 1.8.0 format)
    3. src/main/resources/assets/tnc/textures/entity/wind_sprite_bedrock.png   (64x64)

BONES (the renderer/animation must agree on these names)
    root, body, crest, wing_left, wing_right, tail

ASCII only (project tooling rule).
"""
import json
import math
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "tnc"
NAME = "wind_sprite"

GEO_PATH = os.path.join(REPO, "src", "main", "resources", "assets", NS, "geo", "entity", NAME + ".geo.json")
ANIM_PATH = os.path.join(REPO, "src", "main", "resources", "assets", NS, "animations", "entity", NAME + ".animation.json")
TEX_PATH = os.path.join(REPO, "src", "main", "resources", "assets", NS, "textures", "entity", NAME + "_bedrock.png")
PREVIEW_PATH = os.path.join(REPO, "docs", "previews", NAME + "_preview.png")

TEX = 64          # atlas size (also the texture_width/height in the geo description)

# cube atlas slots: (name, uv, size)
BODY = ((0, 0), (6, 7, 6))
CREST = ((0, 20), (2, 3, 2))
WING = ((16, 0), (7, 4, 2))
TAIL = ((32, 0), (2, 2, 6))
HEAD = ((0, 32), (5, 5, 5))
ARM = ((20, 32), (2, 5, 2))
LOWER_WING = ((32, 20), (5, 5, 1))
HALO = ((48, 20), (2, 2, 8))

BODY_COL = (186, 232, 246)
BODY_TOP = (226, 248, 255)
BODY_LOW = (108, 176, 202)
EYE = (28, 92, 124)
WING_COL = (232, 250, 255, 205)
TAIL_COL = (198, 238, 250, 220)
CREST_COL = (255, 255, 255)
GOLD = (224, 188, 92, 235)


def build_geo():
    def cube(name, uv, size, origin, pivot, parent="root", inflate=0.0):
        return {
            "name": name,
            "parent": parent,
            "pivot": list(pivot),
            "cubes": [{
                "origin": list(origin),
                "size": list(size),
                "uv": list(uv),
                "inflate": inflate,
            }],
        }

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        # body sits on the origin: a small egg, 6 x 7 x 6
        cube("body", BODY[0], BODY[1], (-3, 2, -3), (0, 6, 0)),
        cube("head", HEAD[0], HEAD[1], (-2.5, 8.5, -2.8), (0, 10, 0), parent="body"),
        # crest: a little tuft on top (the "wind" flick)
        cube("crest", CREST[0], CREST[1], (-1, 9, -1), (0, 9, 0)),
        # wings: pivot at the shoulder so a Z rotation flaps them
        cube("wing_left", WING[0], WING[1], (3, 4, -1), (3, 6, 0)),
        cube("wing_right", WING[0], WING[1], (-10, 4, -1), (-3, 6, 0)),
        cube("wing_left_lower", LOWER_WING[0], LOWER_WING[1], (7, 1, 0), (7, 5, 0), parent="wing_left"),
        cube("wing_right_lower", LOWER_WING[0], LOWER_WING[1], (-12, 1, 0), (-7, 5, 0), parent="wing_right"),
        cube("arm_left", ARM[0], ARM[1], (3, 2, -3), (3, 7, -1), parent="body"),
        cube("arm_right", ARM[0], ARM[1], (-5, 2, -3), (-3, 7, -1), parent="body"),
        # tail: a short streamer trailing behind (-Z)
        cube("tail", TAIL[0], TAIL[1], (-1, 3, -9), (0, 4, -3)),
        cube("tail_left", TAIL[0], TAIL[1], (1, 1, -10), (2, 4, -3), parent="body"),
        cube("tail_right", TAIL[0], TAIL[1], (-3, 1, -10), (-2, 4, -3), parent="body"),
        # A slim gilded halo gives the higher-tier sprite a deliberate, crafted silhouette.
        cube("halo_left", HALO[0], HALO[1], (-6, 8, 2), (-5, 10, 1), parent="body"),
        cube("halo_right", HALO[0], HALO[1], (4, 8, 2), (5, 10, 1), parent="body"),
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (NS, NAME),
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }


def build_animation():
    """Sampled keyframes (the project's dark_imp.animation.json does the same)."""
    steps = 18
    length = 1.8
    body_pos, body_rot, wl, wr, crest, tail, tail_l, tail_r = {}, {}, {}, {}, {}, {}, {}, {}
    for i in range(steps + 1):
        t = length * i / float(steps)
        k = "%.2f" % t
        ph = 2.0 * math.pi * (t / length)
        # hover bob (two cycles) + a gentle lean
        body_pos[k] = [0.0, round(0.35 * math.sin(ph * 2.0), 3), 0.0]
        body_rot[k] = [round(2.0 * math.sin(ph * 2.0 + 0.6), 3), 0.0, 0.0]
        # wing flap: fast (six cycles) and mirrored
        flap = 34.0 * math.sin(ph * 6.0)
        wl[k] = [0.0, 0.0, round(flap, 3)]
        wr[k] = [0.0, 0.0, round(-flap, 3)]
        # crest + tail sway a little slower, tail opposite the crest
        crest[k] = [0.0, 0.0, round(9.0 * math.sin(ph * 2.0 + 1.2), 3)]
        tail[k] = [round(7.0 * math.sin(ph * 2.0), 3), 0.0, round(-6.0 * math.sin(ph * 2.0 + 0.9), 3)]
        tail_l[k] = [round(11.0 * math.sin(ph * 2.0 + .5), 3), 0.0, round(8.0 * math.sin(ph + .4), 3)]
        tail_r[k] = [round(11.0 * math.sin(ph * 2.0 - .5), 3), 0.0, round(-8.0 * math.sin(ph - .4), 3)]
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": length,
                "bones": {
                    "body": {"position": body_pos, "rotation": body_rot},
                    "wing_left": {"rotation": wl},
                    "wing_right": {"rotation": wr},
                    "crest": {"rotation": crest},
                    "tail": {"rotation": tail},
                    "tail_left": {"rotation": tail_l},
                    "tail_right": {"rotation": tail_r},
                },
            },
        },
    }


def paint_box(d, uv, size, base, top=None, low=None):
    """Fill one Bedrock cube unwrap (u,v,w,h,d) with base/top/low bands."""
    u, v = uv
    w, h, dp = size
    top = top or base
    low = low or base
    # top / bottom strips
    d.rectangle([u + dp, v, u + dp + w - 1, v + dp - 1], fill=top)
    d.rectangle([u + dp + w, v, u + dp + w + w - 1, v + dp - 1], fill=low)
    # four sides
    d.rectangle([u, v + dp, u + dp - 1, v + dp + h - 1], fill=base)                 # right
    d.rectangle([u + dp, v + dp, u + dp + w - 1, v + dp + h - 1], fill=base)        # front
    d.rectangle([u + dp + w, v + dp, u + dp + 2 * w - 1, v + dp + h - 1], fill=base)  # left
    d.rectangle([u + dp + w + dp, v + dp, u + 2 * dp + 2 * w - 1, v + dp + h - 1], fill=low)  # back


def build_texture():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # body: bright top, deeper underside
    paint_box(d, BODY[0], BODY[1], BODY_COL, BODY_TOP, BODY_LOW)
    # a face on the front face of the body (see the unwrap above)
    u, v = BODY[0]
    dp, w, h = BODY[1][2], BODY[1][0], BODY[1][1]
    fx, fy = u + dp, v + dp
    d.rectangle([fx + 1, fy + 2, fx + 1, fy + 3], fill=EYE)
    d.rectangle([fx + w - 2, fy + 2, fx + w - 2, fy + 3], fill=EYE)
    d.rectangle([fx + 2, fy + 5, fx + w - 3, fy + 5], fill=(150, 208, 228))

    # crest: white tuft
    paint_box(d, CREST[0], CREST[1], CREST_COL)
    paint_box(d, HEAD[0], HEAD[1], BODY_COL, BODY_TOP, BODY_LOW)
    # sharper face with a central wind-gem
    hu,hv=HEAD[0]
    d.rectangle([hu+5,hv+6,hu+6,hv+7],fill=EYE)
    d.rectangle([hu+8,hv+6,hu+9,hv+7],fill=EYE)
    d.rectangle([hu+7,hv+4,hu+7,hv+5],fill=GOLD)
    paint_box(d, ARM[0], ARM[1], BODY_LOW, BODY_COL, BODY_LOW)
    # wings: near white, translucent
    paint_box(d, WING[0], WING[1], WING_COL, (255, 255, 255, 225), (206, 238, 250, 180))
    paint_box(d, LOWER_WING[0], LOWER_WING[1], (205, 244, 255, 180), (255, 255, 255, 215), (128, 204, 230, 145))
    paint_box(d, HALO[0], HALO[1], GOLD, (255, 230, 132, 245), (170, 126, 48, 210))
    # tail: fading streamer (alpha drops towards the tip)
    u, v = TAIL[0]
    paint_box(d, TAIL[0], TAIL[1], TAIL_COL)
    d.rectangle([u, v, u + 13, v + 3], fill=(198, 238, 250, 160))
    return img


def build_preview(tex):
    scale = 5
    pad = 8
    board = Image.new("RGBA", (16 * scale + pad * 2, 16 * scale + pad * 2 + 14), (24, 24, 30, 255))
    d = ImageDraw.Draw(board)
    d.text((pad, 4), "wind_sprite (front, 16x16 units = 1 block)", fill=(210, 210, 220, 255))
    ox, oy = pad, pad + 12
    d.rectangle([ox, oy, ox + 16 * scale, oy + 16 * scale], outline=(70, 70, 80, 255))
    # draw the whole atlas scaled down inside the box so the palette is checkable
    small = tex.resize((14 * scale, 14 * scale), Image.NEAREST)
    board.alpha_composite(small, (ox + scale, oy + scale))
    return board


def main():
    geo = build_geo()
    anim = build_animation()
    tex = build_texture()

    for path, data in ((GEO_PATH, geo), (ANIM_PATH, anim)):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
            fh.write("\n")
    os.makedirs(os.path.dirname(TEX_PATH), exist_ok=True)
    tex.save(TEX_PATH)
    os.makedirs(os.path.dirname(PREVIEW_PATH), exist_ok=True)
    build_preview(tex).save(PREVIEW_PATH)

    bones = geo["minecraft:geometry"][0]["bones"]
    print("bones      : %s" % ", ".join(b["name"] for b in bones))
    print("cubes      : %d" % sum(len(b.get("cubes", [])) for b in bones))
    print("anim bones : %s" % ", ".join(sorted(anim["animations"]["idle"]["bones"])))
    print("wrote %s" % GEO_PATH)
    print("wrote %s" % ANIM_PATH)
    print("wrote %s (%dx%d)" % (TEX_PATH, TEX, TEX))
    print("wrote %s" % PREVIEW_PATH)


if __name__ == "__main__":
    main()
