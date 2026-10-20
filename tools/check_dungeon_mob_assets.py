# -*- coding: utf-8 -*-
"""check_dungeon_mob_assets.py -- gate for the 12 dungeon minions before they ever reach Java.

WHY THIS EXISTS
===============
A GeckoLib clip that animates a bone the geo does not have does not crash: it silently does
nothing, and the mob ships with a frozen limb. A cube whose UV unwrap runs past the sheet edge
renders as a smeared stripe. Neither shows up in gradle, so this checks the three things that
fail quietly:

  1. every bone named by every animation clip exists in the geo
  2. every cube's unwrap rectangle (2*(w+d) x (h+d)) fits inside the sheet at its uv origin
  3. every cube has non-zero size, and each texture region is actually painted (not flat)

Exit code is 1 on any violation, so it can sit in front of a build.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mob_ids import ids as _mob_ids  # noqa: E402  (single source of truth: DungeonSpecies.java)

IDS = _mob_ids()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc")


def bones_of(geo):
    return {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}


def unwrap_fits(cube, tw, th):
    (ox, oy, oz) = cube["origin"]
    (sx, sy, sz) = cube["size"]
    w, h, d = int(round(sx)), int(round(sy)), int(round(sz))
    if w <= 0 or h <= 0 or d <= 0:
        return False, "zero-size cube at %s" % (cube["origin"],)
    u, v = cube["uv"][0], cube["uv"][1]
    if u + 2 * (w + d) > tw or v + (h + d) > th:
        return False, "uv %s + unwrap %dx%d overflows sheet %dx%d" % (
            (u, v), 2 * (w + d), h + d, tw, th)
    return True, ""


def check_one(mob):
    problems = []
    gp = os.path.join(ASSETS, "geo", "entity", mob + ".geo.json")
    ap = os.path.join(ASSETS, "animations", "entity", mob + ".animation.json")
    tp = os.path.join(ASSETS, "textures", "entity", mob + "_bedrock.png")
    for p in (gp, ap, tp):
        if not os.path.exists(p):
            return ["missing %s" % os.path.relpath(p, ROOT)], {}

    with open(gp, encoding="utf-8") as f:
        geo = json.load(f)
    with open(ap, encoding="utf-8") as f:
        anim = json.load(f)

    desc = geo["minecraft:geometry"][0]["description"]
    tw, th = desc["texture_width"], desc["texture_height"]
    bones = bones_of(geo)

    for cube_owner in geo["minecraft:geometry"][0]["bones"]:
        for cube in cube_owner.get("cubes", []):
            ok, why = unwrap_fits(cube, tw, th)
            if not ok:
                problems.append("bone %s: %s" % (cube_owner["name"], why))

    clips = anim["animations"]
    for clip_name, clip in clips.items():
        for bone in clip.get("bones", {}):
            if bone not in bones:
                problems.append("clip %s animates missing bone %s" % (clip_name, bone))
        if "animation_length" not in clip:
            problems.append("clip %s has no animation_length" % clip_name)

    img = np.asarray(Image.open(tp).convert("RGBA")).astype(np.float32)
    if img.shape[0] != tw or img.shape[1] != th:
        problems.append("texture is %dx%d, geo declares %dx%d" % (img.shape[1], img.shape[0], tw, th))
    half = tw // 2
    for name, (ox, oy) in {"main": (0, 0), "steel": (half, 0),
                           "accent": (0, half), "dark": (half, half)}.items():
        patch = img[oy:oy + half, ox:ox + half, :3]
        if float(patch.std()) < 3.0:
            problems.append("texture region %s looks flat (std %.2f)" % (name, float(patch.std())))

    return problems, {"bones": len(bones), "cubes": sum(len(b.get("cubes", []))
                                                       for b in geo["minecraft:geometry"][0]["bones"]),
                      "clips": len(clips)}


def main():
    total_bad = 0
    for mob in IDS:
        problems, info = check_one(mob)
        if problems:
            total_bad += 1
            print("FAIL %-22s" % mob)
            for p in problems:
                print("       - %s" % p)
        else:
            print("ok   %-22s %2d bones %2d cubes %d clips" % (
                mob, info["bones"], info["cubes"], info["clips"]))
    print("\n%d/%d mobs clean" % (len(IDS) - total_bad, len(IDS)))
    return 1 if total_bad else 0


if __name__ == "__main__":
    sys.exit(main())
