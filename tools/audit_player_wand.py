# -*- coding: utf-8 -*-
"""
audit_player_wand.py  --  offline audit of the "player holding the shipped staff" model.

WHY THIS EXISTS
===============
tools/gen_player_wand.py paints a texture *and* writes UVs that must agree with each other,
and it reduces a free-form staff mesh (about 1100 quads) to axis-aligned boxes that must not
pass through the body. All of that is checkable without Minecraft:

  * every wand face samples a painted pixel (a transparent sample = wrong UV)
  * the wand never passes through the head, hat, body or legs (the right arm may be
    touched: that is the grip itself)
  * the player half of the sheet is byte-identical to self_bedrock.png
  * the staff covers the grip height, its bottom hangs above the ground and its crown
    clears the head, so the rest pose reads as "holding a staff", not "staff through me"
  * the animation contract: the seven humanoid bones keep their names; the two props are
    wand (child of armRight, pivot at the grip) and wandJewel (child of wand)

USAGE
=====
    python tools/audit_player_wand.py            # exit 0 = PASS
    python tools/audit_player_wand.py --map      # also print a labelled silhouette map

Pure ASCII on purpose (repo rule; Chinese docs live in docs/).
"""

import json
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import preview_geo_anim as preview  # noqa: E402

NAME = "self_wand"
SOURCE = "self"
GEO = ROOT / ("src/main/resources/assets/tnc/geo/entity/%s.geo.json" % NAME)
TEX = ROOT / ("src/main/resources/assets/tnc/textures/entity/%s_bedrock.png" % NAME)
SRC_TEX = ROOT / ("src/main/resources/assets/tnc/textures/entity/%s_bedrock.png" % SOURCE)
PLAYER_BONES = ("head", "hat", "body", "armRight", "armLeft", "legRight", "legLeft")
PROP_BONES = ("wand", "wandJewel")


def boxes(bone):
    return [(c["origin"], [c["origin"][i] + c["size"][i] for i in range(3)])
            for c in bone.get("cubes", [])]


def overlaps(a, b):
    return all(a[0][i] < b[1][i] - 1e-6 and b[0][i] < a[1][i] - 1e-6 for i in range(3))


def silhouette(bones, order, azimuth, cols=46, rows=30):
    """One letter per bone, nearest surface wins: a readable map for a text-only session."""
    letter = {"head": "H", "hat": "T", "body": "B", "armRight": "R", "armLeft": "L",
              "legRight": "r", "legLeft": "l", "wand": "W", "wandJewel": "J"}
    f, right, up = preview.camera(azimuth, 0)
    polys = []
    for name in order:
        tf = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0.0, 0.0, 0.0])
        for quad, _n, _rect in preview.cube_faces_cached(name):
            polys.append((name, [preview.apply(tf, p) for p in quad]))
    xs = [[sum(p[i] * right[i] for i in range(3)) for p in pts] for _n, pts in polys]
    ys = [[sum(p[i] * up[i] for i in range(3)) for p in pts] for _n, pts in polys]
    depth = [sum(sum(p[i] * f[i] for i in range(3)) for p in pts) / 4.0 for _n, pts in polys]
    x0, x1 = min(min(v) for v in xs), max(max(v) for v in xs)
    y0, y1 = min(min(v) for v in ys), max(max(v) for v in ys)
    span = max(x1 - x0, y1 - y0)
    grid = [["." for _ in range(cols)] for _ in range(rows)]

    def inside(px, py, poly_x, poly_y):
        state = False
        for i in range(len(poly_x)):
            j = (i - 1) % len(poly_x)
            if (poly_y[i] > py) != (poly_y[j] > py):
                xin = (poly_x[j] - poly_x[i]) * (py - poly_y[i]) / (poly_y[j] - poly_y[i]) \
                      + poly_x[i]
                if px < xin:
                    state = not state
        return state

    for k in sorted(range(len(polys)), key=lambda i: depth[i]):
        name = polys[k][0]
        for cy in range(rows):
            py = y1 - (cy + 0.5) / (rows - 1) * span
            for cx in range(cols):
                px = x0 + (cx + 0.5) / (cols - 1) * span
                if inside(px, py, xs[k], ys[k]):
                    grid[cy][cx] = letter[name]
    return "\n".join("".join(row).rstrip(".") for row in grid)


def main():
    errors = []
    geometry = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    bones = {b["name"]: b for b in geometry["bones"]}
    order = [b["name"] for b in geometry["bones"]]
    texture = Image.open(TEX).convert("RGBA")

    preview._GEO = bones
    preview._CACHE.clear()

    # ---- 1. the animation contract ----
    for name in PLAYER_BONES + PROP_BONES:
        if name not in bones:
            errors.append("missing bone %s" % name)
    if bones.get("wand", {}).get("parent") != "armRight":
        errors.append("wand is not a child of armRight: it would not follow the arm")
    if bones.get("wandJewel", {}).get("parent") != "wand":
        errors.append("wandJewel is not a child of wand")
    grip = bones.get("wand", {}).get("pivot")
    fist = bones.get("armRight", {}).get("cubes", [{}])[0]
    if fist and grip:
        low = fist["origin"]
        high = [fist["origin"][i] + fist["size"][i] for i in range(3)]
        gap = max(max(0.0, low[i] - grip[i], grip[i] - high[i]) for i in range(3))
        if gap > 0.8:
            errors.append("grip %s is %.2f units away from the fist %s..%s"
                          % (grip, gap, low, high))

    # ---- 2. UVs land on painted pixels ----
    for name in PROP_BONES:
        for cube in bones[name].get("cubes", []):
            for _quad, normal, rect in preview.cube_faces(cube):
                if preview.face_color(texture, rect, None) is None:
                    errors.append("%s cube %s face %s samples a transparent pixel"
                                  % (name, cube["origin"], normal))

    # ---- 3. the wand clears the body; only the grip may pierce the right arm ----
    prop_boxes = [(n, b) for n in PROP_BONES for b in boxes(bones[n])]
    for name in PLAYER_BONES:
        if name == "armRight":
            continue
        for body_box in boxes(bones[name]):
            for prop_name, prop_box in prop_boxes:
                if overlaps(prop_box, body_box):
                    errors.append("%s cube %s passes through %s %s"
                                  % (prop_name, prop_box[0], name, body_box[0]))

    # ---- 4. the staff reaches the hand and hangs like a held staff ----
    # The fist sits where the ITEM model is held (tnc_grip y=5 in item space), so ~9.6
    # units of shaft hang below the hand and the butt ends at ankle height. 2026-10-31 the
    # user confirmed that placement is the correct one ("以前版本的法杖握法是正确的啊");
    # an earlier attempt to shove the fist down to the butt ("like a sword hilt") was
    # reverted. What was actually wrong was the SHAFT TURNING WITH THE FOREARM during the
    # cast clips - that is checked by tools/lock_staff_upright.py, not here.
    if grip:
        spans = [(b[0][1], b[1][1]) for _n, b in prop_boxes]
        if not any(low <= grip[1] <= high for low, high in spans):
            errors.append("no wand geometry covers the grip height %.1f" % grip[1])
    low_y = min(b[0][1] for _n, b in prop_boxes)
    high_y = max(b[1][1] for _n, b in prop_boxes)
    head_top = max(b[1][1] for b in boxes(bones["head"]))
    if low_y > 8.0:
        errors.append("staff bottom is %.1f units up: it no longer hangs below the fist the "
                      "way the item model does (grip y=%.1f)" % (low_y, grip[1] if grip else -1))
    if low_y < 1.5:
        errors.append("staff bottom is %.1f: it would drag on / z-fight the ground" % low_y)
    if high_y <= head_top:
        errors.append("staff top (%.1f) does not clear the head (%.1f)" % (high_y, head_top))

    # ---- 5. the player half of the sheet is untouched ----
    if SRC_TEX.exists():
        source = Image.open(SRC_TEX).convert("RGBA")
        if texture.size != source.size:
            errors.append("sheet size changed: %s vs %s" % (texture.size, source.size))
        else:
            half = ImageChops.difference(source.crop((0, 0, 64, texture.size[1])),
                                         texture.crop((0, 0, 64, texture.size[1])))
            if half.getbbox() is not None:
                errors.append("the player skin pixels were modified (left half differs)")
            if texture.crop((64, 0, texture.size[0], texture.size[1])).getbbox() is None:
                errors.append("the staff half of the sheet is empty")

    print("%s: %d bones, %d wand cubes, texture=%dx%d" % (
        NAME, len(bones), len(prop_boxes), texture.size[0], texture.size[1]))
    print("staff: y %.2f..%.2f  x %.2f..%.2f  grip y=%.1f  (head top %.1f)" % (
        low_y, high_y, min(b[0][0] for _n, b in prop_boxes),
        max(b[1][0] for _n, b in prop_boxes), grip[1] if grip else -1, head_top))
    if errors:
        print("FAIL (%d)" % len(errors))
        for error in errors:
            print("  - " + error)
        return 1
    print("PASS player wand model audit")
    if "--map" in sys.argv:
        print("")
        print("== front: camera on -Z, the way the entity looks ==")
        print(silhouette(bones, order, 180))
        print("")
        print("== side: camera on -X, the staff side ==")
        print(silhouette(bones, order, -90))
    return 0


if __name__ == "__main__":
    sys.exit(main())
