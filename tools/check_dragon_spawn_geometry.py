# -*- coding: utf-8 -*-
"""check_dragon_spawn_geometry.py -- where does the body actually end up when cast?

Mirrors TNLightDragonChain.release()/Dragon.delayFor() + TNDragonEntity.modelCenterOffset()
so the numbers can be eyeballed without entering the game. This is what showed that the old
"spawn at 8 + bodyLength*0.25" put t5's origin 76 blocks away (invisible / inside terrain)
and that the head-anchored version keeps every tier in front of the caster's eyes.

Prints, per tier: the spawn point, where the snout/tail land relative to the caster, how high
the body rides, and the launch delay of each dragon in the line.

ASCII only.
"""
import math
import os
import sys

# --- copied from the java (keep in sync) ---
MODEL_LENGTH_BLOCKS = 22.75          # TNDragonEntity.MODEL_LENGTH_BLOCKS
NOSE_BLOCKS = 165.0 / 16.0           # TNDragonEntity.MODEL_NOSE_BLOCKS
HEAD_CLEARANCE = 2.5                 # TNLightDragonChain.HEAD_CLEARANCE
LIFT = 0.22                          # TNLightDragonChain.LIFT
LIFT_MAX = 12.0                      # TNLightDragonChain.LIFT_MAX
RENDER_DISTANCE = 512.0              # TNDragonEntity.RENDER_DISTANCE

TIERS = [
    # name, tier, count, scale, damage, speed, ticks, formation step (x scale = blocks apart in height)
    ("t1 光龙吐息", 1, 1, 0.30, 14.0, 0.48, 450, 0.0),
    ("t3 光龙出击", 3, 1, 3.00, 24.0, 0.58, 620, 0.0),
    ("t4 光龙俯冲", 4, 2, 6.00, 34.0, 0.66, 740, 0.50),
    ("t5 光龙降世", 5, 3, 12.00, 44.0, 0.74, 840, 0.35),
]


def main():
    print("%-14s %8s %9s %9s %9s %9s %14s" % (
        "tier", "scale", "bodyLen", "lift", "originZ", "tailZ", "heights in formation"))
    worst = 0.0
    for name, tier, count, scale, _dmg, _speed, _ticks, spacing in TIERS:
        body = MODEL_LENGTH_BLOCKS * scale
        origin_ahead = body + HEAD_CLEARANCE
        lift = min(LIFT_MAX, origin_ahead * LIFT)
        # head anchored: the snout sits exactly at the origin, the body runs back the other way
        nose_ahead = origin_ahead
        tail_ahead = origin_ahead - body
        heights = [lift + (i - (count - 1) / 2.0) * spacing * scale for i in range(count)]
        worst = max(worst, origin_ahead)
        print("%-14s %8.2f %9.2f %9.2f %9.2f %9.2f %14s"
              % (name, scale, body, lift, origin_ahead, tail_ahead,
                 ",".join("%.1f" % h for h in heights)))
    print()
    print("originZ / tailZ = blocks ahead of the caster along the look vector (tail should be >= 0,")
    print("i.e. the body must NOT start inside the caster)")
    print("lift / heights = how far above the caster's feet each dragon of the flight rides")
    print("deepest origin: %.1f blocks  (must stay well inside RENDER_DISTANCE %.0f)" % (worst,
                                                                                         RENDER_DISTANCE))
    # the old formula, for contrast
    print()
    print("old formula (origin = body centre): 8 + bodyLen*0.25")
    for name, tier, count, scale, _dmg, _speed, _ticks, _sp in TIERS:
        body = MODEL_LENGTH_BLOCKS * scale
        old = 8.0 + body * 0.25
        print("   %-14s origin %.1f blocks ahead -> snout %.1f, tail %.1f"
              % (name, old, old + NOSE_BLOCKS * scale, old - (body - NOSE_BLOCKS * scale)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
