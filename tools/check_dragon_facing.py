# -*- coding: utf-8 -*-
"""check_dragon_facing.py -- which way does the model point, and is that the way it flies?

The author reported (2026-10-04) that both the light and the dark dragon fly BACKWARDS.
Everything in the code says the head is at -Z and GeckoLib maps the model's -Z onto the
entity's facing, so this script settles it with a picture instead of an argument:

  * a thick arrow on the ground at y=0: GREEN points to +Z, MAGENTA points to -Z;
  * the value of Mth.atan2(dz, dx) * 180/PI - 90 for the direction the entity reports
    (that is exactly TNDragonEntity.faceDir), printed as a text row;
  * the same model seen from the left, the right, the front and the back.

If the head sits on the MAGENTA side, the model faces -Z, which is what GeckoLib maps
onto "the way the entity looks" -- i.e. it flies head first and the bug is elsewhere
(the renderer's own pose, the spawn position, or the movement code).

ASCII only.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402  (reuse the renderer)

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "dragon.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "dragon.animation.json")


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)

    P.FRONT = "-Z"                      # the dragon geo puts its head at -Z (verified below)
    P._GEO, order = P.load_geo(GEO)
    anim = P.load_anim(ANIM, "dash")
    assert isinstance(anim, dict), anim
    try:
        P._TEX = Image.open("src/main/resources/assets/tnc/textures/entity/dragon_bedrock.png").convert("RGBA")
    except Exception:
        P._TEX = None

    # --- where do the bones actually sit? (head vs tail, in model units) ---
    tfs = P.world_transforms(P._GEO, anim, 0.0)
    head_z = []
    tail_z = []
    for name in ("head", "jaw"):
        for cube in P._GEO[name].get("cubes", []):
            o, s = cube["origin"], cube["size"]
            for k in (0, 1):
                head_z.append(P.apply(tfs[name], [o[0], o[1], o[2] + s[2] * k])[2])
    for name in ("tailFin", "tail5"):
        for cube in P._GEO[name].get("cubes", []):
            o, s = cube["origin"], cube["size"]
            for k in (0, 1):
                tail_z.append(P.apply(tfs[name], [o[0], o[1], o[2] + s[2] * k])[2])

    lo, hi = P.model_bounds(P._GEO, order, anim, 0.0)
    center = [(lo[i] + hi[i]) / 2.0 for i in range(3)]

    size = 320
    views = [("left(-X looking +X)", -90, 6), ("right(+X)", 90, 6),
             ("front", 180, 6), ("back", 0, 6)]
    sheet = Image.new("RGB", (size * len(views), size + 44), (26, 26, 34))

    for c, (tag, az, el) in enumerate(views):
        img = P.render(P._GEO, order, anim, anim["animation_length"] * 0.0, size, az, el,
                       size * 0.86 / max(hi[2] - lo[2], hi[1] - lo[1], 1.0), tag,
                       center=tuple(center))
        d = ImageDraw.Draw(img)
        f, right, up = P.camera(az, el)

        def project(p):
            v = [p[i] - center[i] for i in range(3)]
            return (size / 2 + sum(v[i] * right[i] for i in range(3)) * (size * 0.86 / max(hi[2] - lo[2], hi[1] - lo[1], 1.0)),
                    size / 2 - sum(v[i] * up[i] for i in range(3)) * (size * 0.86 / max(hi[2] - lo[2], hi[1] - lo[1], 1.0)))

        y = lo[1] - 6.0
        for sign, col, name in ((1, (60, 220, 90), "+Z"), (-1, (235, 60, 200), "-Z")):
            a = project([0.0, y, 0.0])
            b = project([0.0, y, sign * 60.0])
            d.line([a, b], fill=col, width=4)
            d.text((b[0] + 3, b[1] - 6), name, fill=col)
        sheet.paste(img, (c * size, 0))

    # --- what yaw does the entity report for a given travel direction? ---
    rows = []
    for dz in (-1.0, 1.0):
        for dx in (0.0,):
            yaw = math.degrees(math.atan2(dz, dx)) - 90.0 if dx else (180.0 if dz < 0 else 0.0)
            rows.append("dir=(%.0f,%.0f) -> yaw=%.1f" % (dx, dz, yaw))
    d = ImageDraw.Draw(sheet)
    d.text((6, size + 4),
           "head cubes z=%.1f..%.1f   tail cubes z=%.1f..%.1f   (model units, 16/block)"
           % (min(head_z), max(head_z), min(tail_z), max(tail_z)), fill=(230, 230, 240))
    d.text((6, size + 18),
           "GeckoLib maps the model's -Z onto the entity's facing.   " + "   ".join(rows),
           fill=(230, 230, 240))
    out = os.path.join("docs", "previews", "dragon_facing.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("head z", round(min(head_z), 1), round(max(head_z), 1),
          "| tail z", round(min(tail_z), 1), round(max(tail_z), 1))
    print("preview ->", out)


if __name__ == "__main__":
    main()
