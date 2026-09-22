# -*- coding: utf-8 -*-
"""
gen_npc_animation.py  --  pose-table animation generator (animation system MMM)

WHY
===
Hand-dragging rotation rings in Blockbench is slow and hard to review. Since the
animation file is just data, this script keeps every pose in ONE readable table
and regenerates the whole animation JSON in a second. Tweak a number, re-run,
look in game. No Blockbench required.

Output: src/main/resources/assets/tnc/animations/entity/self.animation.json

AXIS CONVENTIONS for this rig (Bedrock / GeckoLib, degrees)
==========================================================
    X  : arm/leg swings forward(-) / backward(+)
         arm raised in front  -> X around -80
    Y  : turn left(+) / right(-)
         head turned right    -> Y around -24
    Z  : arm opens outward(+) / tucks inward(-)
         arms tucked to chest -> Z around -18 ... -22

Helper letters used in the table below:
    P = body pitch forward (positive = lean forward)
    F = arm swing forward   (positive number in the table = arm raised in front)
    I = arm tucked inward   (positive number in the table = arm pulled to the chest)
    HY = head yaw, HY>0 = turn left, HY<0 = turn right
    HP = head pitch, positive = look down a little

Every bone rotation is relative to the rig's rest pose, which IS the natural
standing pose (see tools/gen_npc_bedrock.py). So 0 = standing normally.

USAGE
=====
    python tools/gen_npc_animation.py

The file is pure ASCII on purpose (same rule as tools/*.ps1).
"""

import io
import json
import os
import sys

MODID = "tnc"
NPC = "self"

# ---------------------------------------------------------------------------
# helpers: the "P / F / I" language -> actual [x, y, z] degrees
# ---------------------------------------------------------------------------


def arm(forward=0.0, inward=0.0):
    """Arm pose. forward>0 = raised in front, inward>0 = tucked toward the chest."""
    return [-float(forward), 0.0, -float(inward)]


def body(pitch=0.0, yaw=0.0):
    """Torso. pitch>0 = lean forward, yaw>0 = turn left."""
    return [float(pitch), float(yaw), 0.0]


def head(pitch=0.0, yaw=0.0):
    """Head. pitch>0 = look down, yaw>0 = turn left."""
    return [float(pitch), float(yaw), 0.0]


def seconds(frames):
    """20 frames = 1 second (the game runs at 20 ticks/s)."""
    return round(frames / 20.0, 3)


# ---------------------------------------------------------------------------
# THE POSE TABLE -- this is the part you edit
# ---------------------------------------------------------------------------
# Each animation: loop flag, and a list of keyframes.
# A keyframe = (frame_number, {bone: rotation})
# Bones: head / body / armRight / armLeft / hat (deprecated: hat follows head)

ANIMATIONS = [
    {
        # scene 1: wiping-hands idle (plays on loop all the time)
        "name": "idle",
        "loop": True,
        "keys": [
            (0,  {"armRight": arm(forward=84, inward=18),
                  "armLeft":  arm(forward=84, inward=18),
                  "head": head(pitch=4)}),
            (12, {"armRight": arm(forward=92, inward=20),
                  "armLeft":  arm(forward=76, inward=16),
                  "head": head(pitch=5, yaw=-3)}),
            (24, {"armRight": arm(forward=76, inward=16),
                  "armLeft":  arm(forward=92, inward=20),
                  "head": head(pitch=5, yaw=3)}),
            (36, {"armRight": arm(forward=88, inward=19),
                  "armLeft":  arm(forward=80, inward=17),
                  "head": head(pitch=4)}),
            (48, {"armRight": arm(forward=84, inward=18),
                  "armLeft":  arm(forward=84, inward=18),
                  "head": head(pitch=4)}),
        ],
    },
    {
        # stage direction: "(Self wipes his hands, pauses.)"
        "name": "stop_wipe",
        "loop": False,
        "keys": [
            (0,  {"armRight": arm(forward=88, inward=18),
                  "armLeft":  arm(forward=80, inward=18),
                  "head": head(pitch=4)}),
            (15, {"armRight": arm(forward=70, inward=14),
                  "armLeft":  arm(forward=70, inward=14),
                  "head": head(pitch=7)}),
            (30, {"armRight": arm(forward=76, inward=14),
                  "armLeft":  arm(forward=76, inward=14),
                  "head": head(pitch=6)}),
        ],
    },
    {
        # stage direction: "(Self puts the cloth down and glances at the untouched drink.)"
        "name": "put_cloth",
        "loop": False,
        "keys": [
            (0,  {"armRight": arm(forward=80, inward=16),
                  "armLeft":  arm(forward=80, inward=16),
                  "body": body(pitch=0),
                  "head": head(pitch=4)}),
            (12, {"armRight": arm(forward=30, inward=8),
                  "armLeft":  arm(forward=30, inward=8),
                  "body": body(pitch=6),
                  "head": head(pitch=8)}),
            (24, {"armRight": arm(forward=6, inward=2),
                  "armLeft":  arm(forward=6, inward=2),
                  "body": body(pitch=3),
                  "head": head(pitch=4)}),
        ],
    },
    {
        # right after put_cloth: head turns to the drink (on Self's right -> yaw negative)
        "name": "glance_cup",
        "loop": False,
        "keys": [
            (0,  {"head": head(pitch=2), "body": body()}),
            (9,  {"head": head(pitch=6, yaw=-24), "body": body(yaw=-5)}),
            (27, {"head": head(pitch=6, yaw=-24), "body": body(yaw=-5)}),
            (36, {"head": head(pitch=2), "body": body()}),
        ],
    },
    {
        # ending: watching the friend walk out the door (on Self's left -> yaw positive)
        "name": "look_door",
        "loop": False,
        "keys": [
            (0,  {"head": head(pitch=2), "body": body()}),
            (12, {"head": head(pitch=3, yaw=26), "body": body(yaw=6)}),
            (27, {"head": head(pitch=3, yaw=26), "body": body(yaw=6)}),
            (36, {"head": head(pitch=2), "body": body()}),
        ],
    },
]


# ---------------------------------------------------------------------------
# build the Bedrock / GeckoLib animation JSON
# ---------------------------------------------------------------------------


def build_animation(spec):
    """Turn [(frame, {bone: [x,y,z]})] into Bedrock's bones/rotation map."""
    bones = {}
    length = 0
    for frame, poses in spec["keys"]:
        length = max(length, frame)
        for bone, rot in poses.items():
            entry = bones.setdefault(bone, {"rotation": {}})
            entry["rotation"][str(seconds(frame))] = [
                round(float(v), 4) for v in rot
            ]
    return {
        "loop": bool(spec["loop"]),
        "animation_length": seconds(length),
        "bones": bones,
    }


def build_file():
    return {
        "format_version": "1.8.0",
        "animations": {spec["name"]: build_animation(spec) for spec in ANIMATIONS},
    }


def main(_argv):
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                           "animations", "entity")
    if not os.path.isdir(out_dir):
        print("ERROR: output folder not found: %s" % out_dir)
        return 1

    out_path = os.path.join(out_dir, NPC + ".animation.json")
    data = build_file()
    with io.open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent="\t")
        f.write("\n")

    print("wrote %s" % os.path.relpath(out_path, repo))
    for spec in ANIMATIONS:
        anim = data["animations"][spec["name"]]
        n_keys = len(anim["bones"]) and max(
            len(b["rotation"]) for b in anim["bones"].values()) or 0
        print("  %-12s loop=%-5s length=%4.2fs  bones=%d  keyframes/bone<=%d"
              % (spec["name"], anim["loop"], anim["animation_length"],
                 len(anim["bones"]), n_keys))
    print("")
    print("Axis reminder: X = swing forward(-)/back(+), Y = turn left(+)/right(-),")
    print("                Z = arm opens outward(+)/tucks inward(-)")
    print("Edit the ANIMATIONS table in this file, re-run, then look in game.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
