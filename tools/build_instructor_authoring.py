# -*- coding: utf-8 -*-
"""build_instructor_authoring.py -- Blockbench-ready copy of the instructor WITH the weapon.

The shipped model deliberately has no sword or shield geometry: in game the weapon is a real
ItemStack drawn by ``InstructorHeldItemLayer`` on the ``forearm_r`` / ``forearm_l`` bone. That
is great for the game and useless for animating, because Blockbench would show empty hands.

This tool writes ``model-source/instructor/instructor-blockbench.geo.json``: the same 17 bones,
the same pivots and the same animation targets, plus

  * a sword (grip / guard / blade) welded into ``forearm_r``,
  * a shield plate welded into ``forearm_l``,

positioned and rotated to match what the game's ``ItemDisplayContext.THIRD_PERSON_*_HAND``
transform produces (grip at the fist, i.e. 6 px below the elbow pivot, tilted 55 deg).

Because the props are *cubes inside existing bones* -- not new bones -- the animation channels
you author are still exactly the shipped 17 bones, so an animation exported from this file drops
straight into ``assets/tnc/animations/entity/instructor.animation.json``.

    python tools/build_instructor_authoring.py          # writes the geo + a render preview
"""
import copy
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_instructor_model as G          # noqa: E402  (the shipped generator)
import preview_geo_anim as P                # noqa: E402  (the validated renderer)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "model-source", "instructor")
OUT_GEO = os.path.join(OUT_DIR, "instructor-blockbench.geo.json")
PREVIEW_DIR = os.path.join(ROOT, "work", "instructor-preview")

# Grip sits 6 px below the elbow pivot (elbow y=18, fist y=12) -- the same GRIP_DROP the
# renderer uses, so the authoring pose and the in-game pose agree.
GRIP = {"r": (-6.0, 12.0, 0.0), "l": (6.0, 12.0, 0.0)}
# Same number as InstructorHeldItemLayer.GRIP_ROT_X: blade tipped 55 deg forward out of the arm's
# line. Single axis on purpose -- Blockbench (three.js) composes cubes as Rx*Ry*Rz while Bedrock
# and GeckoLib compose ZYX, so any multi-axis triple would pose differently in the two places.
# With only X non-zero both conventions agree, and the crossguard still spans left-right.
SWORD_ROT = [-55.0, 0.0, 0.0]
HANDLE = (8.0, 1.0)              # where the hand grabs the sprite, in sprite px
SWORD_SCALE = 0.85               # InstructorHeldItemLayer.SCALE
# Shield: a flat plate hanging on the left forearm, face forward. Reference only -- the real
# item is vanilla's shield model, drawn with the same offset/scale (see the layer).
SHIELD_OFFSET = (-2.0, 1.0, -2.0)
SHIELD_SCALE = 0.8 * 0.85

# Boxes in item-model space (16x16 sprite, +Y = blade, y measured up from the sprite bottom).
SWORD_BOXES = (
    # (name, x, y, z, w, h, d)
    ("blade", 7.2, 4.0, 0.0, 1.6, 11.5, 1.0),
    ("guard", 5.0, 3.0, 0.0, 6.0, 1.0, 1.0),
    ("grip", 7.0, 0.2, 0.0, 2.0, 2.8, 1.0),
)
SHIELD_PLATE = (10.0, 14.0, 1.5)


def flat_patch(tex, w, d, h, target, avoid):
    """Find a low-variance box-UV footprint whose average brightness is near `target`."""
    arr = np.asarray(tex.convert("RGB")).astype(float)
    bw, bh = 2 * (w + d), d + h
    best = None
    for y in range(0, arr.shape[0] - bh + 1):
        for x in range(0, arr.shape[1] - bw + 1):
            if any(x < a[2] and x + bw > a[0] and y < a[3] and y + bh > a[1] for a in avoid):
                continue
            patch = arr[y:y + bh, x:x + bw]
            if patch.shape[2] != 3:
                continue
            mean = patch.mean()
            score = abs(mean - target) + patch.std() * 2.0
            if best is None or score < best[0]:
                best = (score, x, y, mean, patch.std())
    if best is None:
        return 0, 0
    return best[1], best[2]


def prop_cubes(tex):
    """Sword cubes for the right forearm and one shield plate for the left."""
    fist_r = GRIP["r"]
    fist_l = GRIP["l"]
    sword = []
    for name, x, y, z, w, h, d in SWORD_BOXES:
        size = [SWORD_SCALE * w, SWORD_SCALE * h, SWORD_SCALE * d]
        origin = [fist_r[0] + SWORD_SCALE * (x - HANDLE[0]),
                  fist_r[1] + SWORD_SCALE * (y - HANDLE[1]),
                  fist_r[2] + SWORD_SCALE * z]
        target = {"blade": 150.0, "guard": 110.0, "grip": 70.0}[name]
        uv = flat_patch(tex, max(1, int(round(size[0]))), max(1, int(round(size[2]))),
                        max(1, int(round(size[1]))), target, [])
        sword.append({"origin": [round(v, 3) for v in origin],
                      "size": [round(v, 3) for v in size],
                      "uv": list(uv), "rotation": SWORD_ROT,
                      "pivot": [round(v, 3) for v in fist_r], "inflate": 0.0})
    plate = [SHIELD_SCALE * v for v in SHIELD_PLATE]
    shield_uv = flat_patch(tex, max(1, int(round(plate[0]))), max(1, int(round(plate[2]))),
                           max(1, int(round(plate[1]))), 95.0, [])
    shield = [
        {"origin": [round(fist_l[0] + SHIELD_OFFSET[0] - plate[0] / 2.0, 3),
                    round(fist_l[1] + SHIELD_OFFSET[1] - plate[1] / 2.0, 3),
                    round(fist_l[2] + SHIELD_OFFSET[2] - plate[2], 3)],
         "size": [round(v, 3) for v in plate], "uv": list(shield_uv), "rotation": [0.0, 0.0, 0.0],
         "pivot": [round(v, 3) for v in fist_l], "inflate": 0.0},
    ]
    return sword, shield


def build(tex):
    geo = copy.deepcopy(G.build_geometry())
    bones = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones}
    sword, shield = prop_cubes(tex)
    by_name["forearm_r"]["cubes"].extend(sword)
    by_name["forearm_l"]["cubes"].extend(shield)
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.instructor_blockbench"
    return geo


def preview(geo, tex):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    P.FRONT = "-Z"
    P._GEO = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    order = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
    P._CACHE.clear()
    P._TEX = tex
    idle = {"bones": {}, "animation_length": 0.0, "loop": False}
    attack = P.load_anim(G.ANIM_PATH, "attack")
    # four turntable views at rest + two swing frames, so the grip can be checked from front,
    # side and back without opening Blockbench.
    shots = ((0.0, idle, 0, "front"),
             (0.0, idle, 35, "front-left 35"),
             (0.0, idle, 90, "side"),
             (0.0, idle, 180, "back"),
             (0.35, attack, 35, "attack mid-swing"),
             (0.6, attack, 35, "attack follow-through"))
    panels = [P.render(P._GEO, order, anim, t, 520, azimuth, 10, 14.0, label,
                       center=(0.0, 16.0, 0.0)) for t, anim, azimuth, label in shots]
    sheet = Image.new("RGB", (520 * len(panels), 520), (14, 18, 26))
    for i, panel in enumerate(panels):
        sheet.paste(panel, (i * 520, 0))
    out = os.path.join(PREVIEW_DIR, "authoring-weapon.png")
    sheet.save(out)
    return out


def main():
    os.chdir(ROOT)
    os.makedirs(OUT_DIR, exist_ok=True)
    tex = Image.open(G.TEX_FILES["counter"]).convert("RGBA")
    geo = build(tex)
    with open(OUT_GEO, "w", encoding="utf-8") as fh:
        json.dump(geo, fh, indent="\t", ensure_ascii=False)
        fh.write("\n")
    bones = geo["minecraft:geometry"][0]["bones"]
    cubes = sum(len(b.get("cubes", [])) for b in bones)
    print("wrote %s  (%d bones, %d cubes)" % (os.path.relpath(OUT_GEO, ROOT), len(bones), cubes))
    print("preview %s" % os.path.relpath(preview(geo, tex), ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
