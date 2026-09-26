# -*- coding: utf-8 -*-
"""
gen_yan_model.py -- Bedrock model + 128x128 skin for the NPC "yan" (Gongsun Yan).

WHY THIS EXISTS
===============
Yan has no entity and no model yet (the animation doc: he left the city before the
story starts, so he never appears on screen).  The author asked for a
"long flowing hair" character model, chose silver-white hair, and will record the
hair sway keyframes himself in Blockbench.

So this script only has to deliver a *rig that can sway*:
  * the standard TN-C humanoid bones (same layout as tools/gen_npc_bedrock.py,
    so the same Java renderer path can be reused later),
  * plus FOUR independently pivotable hair limbs:
        hairTop    - skull cap + fringe (parent: head)
        hairBack -> hairBack2 -> hairBack3     (3 segments down the back)
        hairLeft -> hairLeft2                  (side lock, 2 segments)
        hairRight -> hairRight2                (side lock, 2 segments)
    Each segment's pivot sits at its TOP edge, so rotating the bone swings the
    hair like a pendulum - that is what makes it read as "flowing".

Skin: painted from scratch (there is no source skin for Yan).  Silver-white hair,
dark robe, so the silhouette reads at a distance.

OUTPUT (relative to repo root)
==============================
    src/main/resources/assets/tnc/geo/entity/yan.geo.json
    src/main/resources/assets/tnc/textures/entity/yan_bedrock.png     (128x128)
    docs/previews/yan_skin_preview.png                                (4x, for eyeballing)

USAGE
=====
    python tools/gen_yan_model.py

ASCII only on purpose: the console here is cp936 and CJK would come out as mojibake.
"""

import json
import os
import random

from PIL import Image

MODID = "tnc"
NAME = "yan"

# --------------------------------------------------------------------------
# 1. geometry
# --------------------------------------------------------------------------
# Model space: 1 unit = 1/16 block, +Y up, feet at y=0 (same convention as
# gen_npc_bedrock.py and TnHumanoidNpcModel).
#
# Texture sheet: 128x128 modern layout.  The humanoid parts live inside
# x 0..64 / y 0..64 exactly like the vanilla skin; every hair cube points into
# the free area x 64..128, so nothing overlaps the body UVs.

_BODY = [
    ("head",     None,       [0, 24, 0],  [(-4, 24, -4, 8, 8, 8, 0, 0)]),
    ("hat",      "head",     [0, 24, 0],  [(-4.5, 23.5, -4.5, 9, 9, 9, 32, 0)]),
    ("body",     None,       [0, 12, 0],  [(-4, 12, -2, 8, 12, 4, 16, 16)]),
    ("armRight", None,       [-5, 22, 0], [(-7, 8, -2, 4, 12, 4, 40, 16)]),
    ("armLeft",  None,       [5, 22, 0],  [(3, 8, -2, 4, 12, 4, 32, 48)]),
    ("legRight", None,       [-1.9, 12, 0], [(-4, 0, -2, 4, 12, 4, 0, 16)]),
    ("legLeft",  None,       [1.9, 12, 0], [(0, 0, -2, 4, 12, 4, 16, 48)]),
]

# hair cubes: (bone, parent, pivot, [(ox, oy, oz, w, h, d)])
# uv is filled in below by layout_hair_uv(); the free sheet area starts at HAIR_UV_ORIGIN.
_HAIR = [
    # skull cap + fringe, children of head
    ("hairTop",   "head",       [0, 32, 0],    [(-4.6, 30.6, -4.6, 9.2, 1.4, 9.2),
                                                (-4.5, 28.6, -4.8, 9.0, 2.0, 1.0)]),
    # down the back: 3 segments, each pivot at its top edge
    ("hairBack",  "head",       [0, 30, 3.0],  [(-4.0, 24.0, 3.0, 8, 6, 2)]),
    ("hairBack2", "hairBack",   [0, 24, 4.0],  [(-3.5, 17.0, 3.6, 7, 7, 2)]),
    ("hairBack3", "hairBack2",  [0, 17, 4.5],  [(-3.0, 9.0, 3.8, 6, 8, 2)]),
    # side locks (temple -> chest, 2 segments)
    ("hairLeft",  "head",       [-4, 30, -2],  [(-5.0, 20.0, -2.5, 1, 10, 5)]),
    ("hairLeft2", "hairLeft",   [-4.5, 20, 0], [(-5.0, 13.0, -2.5, 1, 7, 5)]),
    ("hairRight", "head",       [4, 30, -2],   [(4.0, 20.0, -2.5, 1, 10, 5)]),
    ("hairRight2", "hairRight", [4.5, 20, 0],  [(4.0, 13.0, -2.5, 1, 7, 5)]),
]

HAIR_UV_ORIGIN = (64, 0)      # free area of the 128x128 sheet
HAIR_UV_WIDTH = 64            # x 64..128 is ours


def _cubes(spec):
    """(ox,oy,oz,w,h,d,uv_u,uv_v) tuples -> Bedrock cube dicts."""
    out = []
    for (ox, oy, oz, w, h, d, u, v) in spec:
        out.append({
            "origin": [ox, oy, oz],
            "size": [w, h, d],
            "uv": [u, v],
        })
    return out


def build_geo():
    bones = []
    for (name, parent, pivot, boxes) in _BODY:
        bone = {"name": name, "pivot": pivot, "cubes": _cubes(boxes)}
        if parent:
            bone["parent"] = parent
        bones.append(bone)

    # lay the hair cubes out in the free area, left to right, 2 columns
    cursor_x, cursor_y = HAIR_UV_ORIGIN
    row_height = 0
    for (name, parent, pivot, boxes) in _HAIR:
        placed = []
        for (ox, oy, oz, w, h, d) in boxes:
            need_w = 2 * (w + d)           # Bedrock auto unwrap: 2*(w+d) x (h+d)
            need_h = h + d
            if cursor_x + need_w > HAIR_UV_ORIGIN[0] + HAIR_UV_WIDTH:
                cursor_x = HAIR_UV_ORIGIN[0]
                cursor_y += row_height + 1
                row_height = 0
            placed.append((ox, oy, oz, w, h, d, cursor_x, cursor_y))
            cursor_x += need_w + 1
            row_height = max(row_height, need_h)
        bone = {"name": name, "parent": parent, "pivot": pivot, "cubes": _cubes(placed)}
        bones.append(bone)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.%s.%s" % (MODID, NAME),
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1, 0],
                },
                "bones": bones,
            }
        ],
    }, bones


# --------------------------------------------------------------------------
# 2. skin
# --------------------------------------------------------------------------
HAIR_BASE = (226, 228, 236, 255)     # silver white
HAIR_DARK = (176, 180, 196, 255)     # shading / roots
HAIR_LIGHT = (248, 249, 255, 255)    # highlights
SKIN = (240, 214, 190, 255)
SKIN_SHADE = (214, 184, 158, 255)
ROBE = (42, 45, 58, 255)             # dark robe
ROBE_DARK = (28, 30, 39, 255)
TRIM = (198, 204, 220, 255)          # silver trim (matches the hair)
EYE = (86, 104, 130, 255)
BOOT = (24, 25, 31, 255)


def _noise(base, amount, rng):
    r = max(0, min(255, base[0] + rng.randint(-amount, amount)))
    g = max(0, min(255, base[1] + rng.randint(-amount, amount)))
    b = max(0, min(255, base[2] + rng.randint(-amount, amount)))
    return (r, g, b, base[3])


def fill(img, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), color)


def fill_hair(img, x0, y0, x1, y1, rng, root_at_top=True):
    """Hair shading: brighter at the tips, darker at the roots + per-pixel streaks."""
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        t = (y - y0) / float(h)                     # 0 = top of the region
        if root_at_top:
            base = HAIR_DARK if t < 0.25 else (HAIR_LIGHT if t > 0.8 else HAIR_BASE)
        else:
            base = HAIR_LIGHT if t < 0.2 else (HAIR_DARK if t > 0.75 else HAIR_BASE)
        for x in range(x0, x1):
            c = _noise(base, 10, rng)
            if rng.random() < 0.10:                 # a lighter strand
                c = _noise(HAIR_LIGHT, 6, rng)
            elif rng.random() < 0.10:               # a darker strand
                c = _noise(HAIR_DARK, 6, rng)
            img.putpixel((x, y), c)


def paint_skin(bones):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    rng = random.Random(20260927)

    # ---- head: unwrap at (0,0), 32x16 --------------------------------------
    # faces: top(8,0,16,8) bottom(16,0,24,8) right(0,8,8,16)
    #        front(8,8,16,16) left(16,8,24,16) back(24,8,32,16)
    fill_hair(img, 8, 0, 16, 8, rng, root_at_top=False)      # top of the skull
    fill_hair(img, 16, 0, 24, 8, rng, root_at_top=False)     # bottom (hidden)
    fill_hair(img, 0, 8, 8, 16, rng)                         # right side
    fill_hair(img, 16, 8, 24, 16, rng)                       # left side
    fill_hair(img, 24, 8, 32, 16, rng)                       # back of the head

    # face
    fill(img, 8, 8, 16, 16, SKIN)
    fill(img, 8, 8, 16, 11, SKIN_SHADE)                      # forehead shadow
    # fringe drawn on the face region (so the fringe reads from the front)
    fill_hair(img, 8, 8, 16, 10, rng)
    # eyes + brows + mouth
    for ex in (10, 13):
        fill(img, ex, 12, ex + 2, 13, EYE)
    fill(img, 10, 11, 12, 12, HAIR_DARK)
    fill(img, 13, 11, 15, 12, HAIR_DARK)
    img.putpixel((12, 14), SKIN_SHADE)
    img.putpixel((13, 14), SKIN_SHADE)

    # ---- hat layer: leave transparent (we do not stack a second hair shell) --

    # ---- body: (16,16) 8x12x4 ---------------------------------------------
    # top(20,16,28,20) bottom(28,16,36,20) right(16,20,20,32)
    # front(20,20,28,32) left(28,20,32,32) back(32,20,40,32)
    fill(img, 16, 20, 20, 32, ROBE_DARK)     # right side
    fill(img, 28, 20, 32, 32, ROBE_DARK)     # left side
    fill(img, 32, 20, 40, 32, ROBE)          # back
    fill(img, 20, 20, 28, 32, ROBE)          # front
    fill(img, 20, 16, 28, 20, ROBE_DARK)     # top (hidden)
    fill(img, 28, 16, 36, 20, ROBE_DARK)     # bottom (hidden)
    fill(img, 20, 20, 28, 22, TRIM)          # collar
    fill(img, 24, 22, 26, 32, TRIM)          # front sash
    fill(img, 20, 30, 28, 32, ROBE_DARK)     # hem

    # ---- arms: (40,16) right / (32,48) left, 4x12x4 ------------------------
    for (u, v) in ((40, 16), (32, 48)):
        fill(img, u + 4, v + 4, u + 8, v + 16, ROBE)         # front
        fill(img, u, v + 4, u + 4, v + 16, ROBE_DARK)        # right side
        fill(img, u + 8, v + 4, u + 12, v + 16, ROBE_DARK)   # left side
        fill(img, u + 12, v + 4, u + 16, v + 16, ROBE)       # back
        fill(img, u + 4, v, u + 8, v + 4, ROBE_DARK)         # top
        fill(img, u + 4, v + 12, u + 8, v + 16, SKIN)        # hands
        fill(img, u, v + 12, u + 4, v + 16, SKIN_SHADE)
        fill(img, u + 8, v + 12, u + 12, v + 16, SKIN_SHADE)
        fill(img, u + 4, v + 11, u + 8, v + 12, TRIM)        # cuff

    # ---- legs: (0,16) right / (16,48) left, 4x12x4 -------------------------
    for (u, v) in ((0, 16), (16, 48)):
        fill(img, u + 4, v + 4, u + 8, v + 16, ROBE_DARK)    # front
        fill(img, u + 12, v + 4, u + 16, v + 16, ROBE_DARK)  # back
        fill(img, u, v + 4, u + 4, v + 16, ROBE_DARK)
        fill(img, u + 8, v + 4, u + 12, v + 16, ROBE_DARK)
        fill(img, u + 4, v, u + 8, v + 4, ROBE_DARK)
        fill(img, u, v + 10, u + 16, v + 16, BOOT)           # boots

    # ---- hair cubes: fill every region we allocated -------------------------
    for bone in bones:
        if not bone["name"].startswith("hair"):
            continue
        for cube in bone.get("cubes", []):
            w, h, d = cube["size"]
            u, v = cube["uv"]
            fill_hair(img, int(u), int(v), int(u + 2 * (w + d)), int(v + h + d), rng,
                      root_at_top=("Back" not in bone["name"]))
    return img


# --------------------------------------------------------------------------
# 3. main
# --------------------------------------------------------------------------
def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    geo, bones = build_geo()

    geo_path = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                            "geo", "entity", "%s.geo.json" % NAME)
    skin_path = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                             "textures", "entity", "%s_bedrock.png" % NAME)
    prev_path = os.path.join(repo, "docs", "previews", "%s_skin_preview.png" % NAME)

    for p in (geo_path, skin_path, prev_path):
        os.makedirs(os.path.dirname(p), exist_ok=True)

    with open(geo_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(geo, f, indent=2, ensure_ascii=False)
        f.write("\n")

    skin = paint_skin(bones)
    skin.save(skin_path)

    # 4x nearest-neighbour preview so the author can eyeball it without Blockbench
    skin.resize((512, 512), Image.NEAREST).save(prev_path)

    print("model  -> %s (%d bones)" % (os.path.relpath(geo_path, repo), len(bones)))
    print("skin   -> %s" % os.path.relpath(skin_path, repo))
    print("preview-> %s" % os.path.relpath(prev_path, repo))
    print("")
    print("hair bones to animate in Blockbench (pivot = top edge of each segment):")
    for b in bones:
        if b["name"].startswith("hair"):
            print("   %-11s parent=%-11s pivot=%s" % (b["name"], b.get("parent", "-"), b["pivot"]))


if __name__ == "__main__":
    main()
