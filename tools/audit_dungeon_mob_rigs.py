# -*- coding: utf-8 -*-
"""Structural audit of the dungeon-mob rigs: the things that make a model look broken
that no screenshot review can reliably catch at this resolution."""
import json
import os
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "geo", "entity")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mob_ids import ids as _mob_ids  # noqa: E402  (single source of truth: DungeonSpecies.java)

IDS = _mob_ids()


def boxes(geo):
    out = []
    for bone in geo["minecraft:geometry"][0]["bones"]:
        for c in bone.get("cubes", []):
            o = c["origin"]
            s = c["size"]
            out.append((bone["name"], (o[0], o[1], o[2]),
                        (o[0] + s[0], o[1] + s[1], o[2] + s[2])))
    return out


def touch(a, b, gap=0.30):
    """True when the two boxes are adjacent or overlapping (share a face / corner)."""
    lo_a, hi_a = a
    lo_b, hi_b = b
    for axis in range(3):
        if hi_a[axis] + gap < lo_b[axis] or hi_b[axis] + gap < lo_a[axis]:
            return False
    return True


def main():
    bad = 0
    for mob in IDS:
        geo = json.load(open(os.path.join(GEO, mob + ".geo.json"), encoding="utf-8"))
        bs = boxes(geo)
        lo = np.min([b[1] for b in bs], axis=0)
        hi = np.max([b[2] for b in bs], axis=0)
        size = (hi - lo) / 16.0
        # a cube is floating when nothing else touches it
        floaters = []
        for i, (bone, a_lo, a_hi) in enumerate(bs):
            if not any(touch((a_lo, a_hi), (b[1], b[2])) for j, b in enumerate(bs) if j != i):
                floaters.append((bone, a_lo))
        # how much of the model is one giant block?
        vols = np.array([(b[2][0] - b[1][0]) * (b[2][1] - b[1][1]) * (b[2][2] - b[1][2]) for b in bs])
        biggest = vols.max() / vols.sum()
        lowest = lo[1]
        centre_x = float(np.mean([(b[1][0] + b[2][0]) / 2.0 for b in bs]))
        flags = []
        if floaters:
            flags.append("%d 个悬空件 %s" % (len(floaters), floaters[:2]))
        if abs(lowest) > 0.5:
            flags.append("最低点 y=%.2f（脚没落地）" % lowest)
        if biggest > 0.16:
            flags.append("单个方块占体积 %.0f%%（一块独大，会显得像冰箱）" % (biggest * 100))
        if size[1] > 2.7:
            flags.append("高度 %.2f 格超高" % size[1])
        print("%-22s %3d 方块  %.2f x %.2f x %.2f 格  最大块占 %4.1f%%  %s"
              % (mob, len(bs), size[0], size[1], size[2], biggest * 100,
                 ("  <-- " + "; ".join(flags)) if flags else "ok"))
        bad += len(flags)
    print("\n问题合计: %d" % bad)
    return 0


if __name__ == "__main__":
    sys.exit(main())
