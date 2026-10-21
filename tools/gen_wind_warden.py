"""翠岚武尊 / Wind Warden -- Zhao Yun style armoured spear general.

Built on the *standard Minecraft humanoid template* (head 8x8x8, body 8x12x4,
arm/leg 4x12x4 px) scaled to 5.4 blocks, then clad in layered lamellar armour:

  * plumed helmet (dome + brow band + cheek guards + long sweeping plume)
  * lamellar pauldrons, chest cuirass, tasselled skirt
  * vambraces, greaves, armoured boots
  * long flowing scarf on its own bone chain
  * wind sword / wind spear drawn at the hand anchor by the renderer

Unit scale: 1 MC pixel = 2.7 model units, so 32 px = 86.4 units = 5.4 blocks and
the renderer needs no extra scale.
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
GEO = ASSETS / "geo/entity/wind_warden.geo.json"
ANIM = ASSETS / "animations/entity/wind_warden.animation.json"
TEX = ASSETS / "textures/entity/wind_warden.png"

random.seed(4242)
P = 2.7          # units per Minecraft pixel
Q = {0: (0, 0), 1: (128, 0), 2: (0, 128), 3: (128, 128)}
MAT = {"skin": 0, "plate": 1, "jade": 2, "gold": 3, "eye": 3}


def px(v):
    return v * P


PIVOT = {
    "root": [0, 0, 0],
    "pelvis": [0, px(12), 0],
    "torso": [0, px(16), 0],
    "chest": [0, px(20), 0],
    "neck": [0, px(24), 0],
    "head": [0, px(24), 0],
    "helmet": [0, px(27), 0],
    "plume_a": [0, px(31), px(1)],
    "plume_b": [0, px(30), px(3)],
    "plume_c": [0, px(28), px(5)],
    "eyes": [0, px(28), -px(4)],
    "scarf_a": [0, px(23), px(2)],
    "scarf_b": [-px(1), px(19), px(3)],
    "scarf_c": [-px(2), px(15), px(3.6)],
}
PARENT = {"pelvis": "root", "torso": "pelvis", "chest": "torso", "neck": "chest",
          "head": "neck", "helmet": "head", "eyes": "head",
          "plume_a": "helmet", "plume_b": "plume_a", "plume_c": "plume_b",
          "scarf_a": "chest", "scarf_b": "scarf_a", "scarf_c": "scarf_b"}
for _s in (-1, 1):
    _k = str(_s)
    PIVOT["pauldron_" + _k] = [_s * px(4), px(23), 0]
    PIVOT["arm_" + _k] = [_s * px(6), px(22), 0]
    PIVOT["fore_" + _k] = [_s * px(6), px(16), 0]
    PIVOT["hand_" + _k] = [_s * px(6), px(12), 0]
    PIVOT["weapon_" + _k] = [_s * px(6), px(11), -px(1)]
    PIVOT["weapon_tip_" + _k] = [_s * px(6), px(11), -px(1)]
    PIVOT["thigh_" + _k] = [_s * px(2), px(12), 0]
    PIVOT["shin_" + _k] = [_s * px(2), px(6), 0]
    PIVOT["foot_" + _k] = [_s * px(2), 0, 0]
    PARENT["pauldron_" + _k] = "chest"
    PARENT["arm_" + _k] = "chest"
    PARENT["fore_" + _k] = "arm_" + _k
    PARENT["hand_" + _k] = "fore_" + _k
    PARENT["weapon_" + _k] = "hand_" + _k
    PARENT["weapon_tip_" + _k] = "weapon_" + _k
    PARENT["thigh_" + _k] = "pelvis"
    PARENT["shin_" + _k] = "thigh_" + _k
    PARENT["foot_" + _k] = "shin_" + _k

CUBES = {}


def box(bone, centre, size, mat, rot=None):
    CUBES.setdefault(bone, []).append(
        {"o": [round(centre[i] - size[i] / 2, 3) for i in range(3)],
         "s": [round(max(0.5, v), 3) for v in size], "r": rot, "m": mat})


def t(a=3.0):
    return (random.uniform(-a, a), random.uniform(-a, a), random.uniform(-a, a))


def scales(bone, centre, size, mat, rows, cols, gap=0.86, tilt=5.0):
    """A block of overlapping lamellar plates: the core visual of this armour."""
    for r in range(rows):
        for c in range(cols):
            x = centre[0] + (c - (cols - 1) / 2) * size[0] * gap
            y = centre[1] + (r - (rows - 1) / 2) * size[1] * gap
            box(bone, (x, y, centre[2]), (size[0] * 0.94, size[1] * 0.92, size[2]), mat,
                ((r - rows / 2) * tilt * 0.5, (c - (cols - 1) / 2) * tilt, 0))


def mirror(bone, centre, size, mat, rot=None):
    box(bone, centre, size, mat, rot)
    box(bone, (-centre[0], centre[1], centre[2]), size, mat,
        None if rot is None else [-rot[0], -rot[1], rot[2]])


def sculpt():
    CUBES.clear()

    # ============ torso: base body + cuirass ============
    box("torso", (0, px(18), 0), (px(8), px(12), px(4)), "skin", t(1.5))
    box("chest", (0, px(21), 0), (px(8.2), px(6), px(4.4)), "plate", t(1.5))
    scales("chest", (0, px(22), -px(2.3)), (px(2.6), px(1.6), px(0.9)), "plate", 2, 3)
    scales("chest", (0, px(19.4), -px(2.3)), (px(2.6), px(1.6), px(0.9)), "plate", 2, 3)
    box("chest", (0, px(20.5), -px(2.9)), (px(2.4), px(2.4), px(0.8)), "gold", (0, 0, 45))
    box("chest", (0, px(20.5), -px(3.3)), (px(1.1), px(1.1), px(0.5)), "jade")
    mirror("chest", (px(3.4), px(21), -px(1.6)), (px(1.4), px(3.4), px(1.2)), "plate",
           (0, -22, 8))
    box("chest", (0, px(23.4), -px(2.2)), (px(5), px(0.9), px(0.9)), "gold", (-4, 0, 0))
    box("chest", (0, px(23.6), 0), (px(8.6), px(1.2), px(4.8)), "plate", (-3, 0, 0))
    box("chest", (0, px(21), px(2.4)), (px(7.6), px(5), px(0.9)), "plate")
    # Waist lamellae + belt + tasselled skirt.
    scales("torso", (0, px(15.4), -px(2.2)), (px(2.4), px(1.2), px(0.7)), "jade", 2, 3)
    box("pelvis", (0, px(12.6), 0), (px(8.6), px(1.6), px(4.6)), "plate", t(1.2))
    box("pelvis", (0, px(12.5), -px(2.5)), (px(3), px(1.2), px(0.9)), "gold")
    box("pelvis", (0, px(12.5), -px(2.9)), (px(1.2), px(1.2), px(0.5)), "jade", (0, 0, 45))
    for col in range(-2, 3):
        scales("pelvis", (col * px(1.6), px(9.6), -px(2.3)), (px(1.5), px(2.2), px(0.7)),
               "plate", 2, 1)
        box("pelvis", (col * px(1.6), px(6.6), -px(2.2)), (px(0.5), px(2.2), px(0.5)),
            "gold", (5, 0, 0))
    mirror("pelvis", (px(4.1), px(9.8), 0), (px(0.9), px(4.4), px(3.4)), "plate", (0, 0, -8))
    box("pelvis", (0, px(12.4), 0), (px(8), px(6), px(4.2)), "jade", t(1.5))

    # ============ head + plumed helmet ============
    box("head", (0, px(28), 0), (px(8), px(8), px(8)), "skin", t(1.0))
    box("head", (0, px(24.6), -px(2.4)), (px(6), px(1.6), px(2.2)), "skin", (6, 0, 0))
    box("head", (0, px(29.4), -px(4.1)), (px(6.4), px(0.9), px(0.7)), "skin", (-8, 0, 0))
    box("head", (0, px(28), -px(4.3)), (px(1.0), px(1.8), px(0.7)), "skin")
    mirror("head", (px(3.2), px(27.4), -px(3.4)), (px(1.6), px(1.4), px(1.6)), "skin",
           (0, 6, 6))
    mirror("eyes", (px(1.9), px(28.4), -px(4.1)), (px(1.4), px(0.8), px(0.5)), "eye", (0, 0, 5))
    mirror("head", (px(1.9), px(28.9), -px(4.15)), (px(1.9), px(0.4), px(0.4)), "jade", (0, 0, 5))
    box("head", (0, px(25.6), -px(4.2)), (px(1.8), px(0.5), px(0.5)), "jade")
    # Helmet: dome of plates, brow band, cheek guards, crest socket, horns.
    for r in range(3):
        for c in range(5):
            box("helmet", ((c - 2) * px(1.7), px(30.2 + r * 0.9), -px(0.4) + r * px(0.2)),
                (px(1.8), px(1.2), px(8.2 - r * 1.2)), "plate",
                (-8 + r * 6, (c - 2) * 7, 0))
    box("helmet", (0, px(29.2), -px(0.2)), (px(8.8), px(1.3), px(8.8)), "plate", (-5, 0, 0))
    box("helmet", (0, px(29.2), -px(4.6)), (px(6.4), px(1.1), px(0.8)), "gold", (-5, 0, 0))
    box("helmet", (0, px(29.2), -px(5.0)), (px(1.4), px(1.4), px(0.6)), "jade", (0, 0, 45))
    mirror("helmet", (px(4.4), px(26.6), -px(1.2)), (px(1.1), px(3.6), px(3.6)), "plate",
           (0, 0, -12))
    mirror("helmet", (px(3.4), px(32.2), 0), (px(1.2), px(2.6), px(3.6)), "plate", (0, 0, 20))
    mirror("helmet", (px(3.2), px(33.8), 0), (px(0.9), px(1.8), px(1.6)), "gold", (0, 0, 30))
    box("helmet", (0, px(33.4), -px(0.6)), (px(1.6), px(2.6), px(1.6)), "gold")
    # Long plume, swept back over three bones.
    for bone, cy, cz, w, pitch in (("plume_a", 33.6, 1.6, 4.4, -18),
                                   ("plume_b", 33.0, 3.6, 3.8, -36),
                                   ("plume_c", 31.6, 5.2, 3.0, -56)):
        scales(bone, (0, px(cy), px(cz)), (px(w / 2), px(1.6), px(1.4)), "jade", 3, 2,
               gap=0.9, tilt=7)
        box(bone, (0, px(cy), px(cz)), (px(w), px(1.6), px(0.7)), "jade", (pitch, 0, 0))

    # ============ shoulders + arms ============
    for s in (1, -1):
        k = str(s)
        for layer in range(3):
            scales("pauldron_" + k, (s * px(6.4), px(23.6 - layer * 1.1), 0),
                   (px(2.6), px(1.3), px(4.4 - layer * 0.4)), "plate" if layer != 1 else "jade",
                   1, 2)
            box("pauldron_" + k, (s * px(6.4), px(24.1 - layer * 1.1), 0),
                (px(5.6), px(0.7), px(4.8 - layer * 0.4)), "gold", (-layer * 8, 0, s * -6))
        box("pauldron_" + k, (s * px(5.4), px(22.4), 0), (px(3.6), px(3.0), px(4.6)),
            "plate", (0, 0, s * -12))
        box("arm_" + k, (s * px(6), px(19), 0), (px(4), px(12), px(4)), "skin", t(1.2))
        box("arm_" + k, (s * px(6), px(20.6), -px(1.6)), (px(3.4), px(3.4), px(1.1)),
            "plate", (0, 0, s * 7))
        box("fore_" + k, (s * px(6), px(15), 0), (px(4), px(10), px(4)), "plate", t(1.2))
        box("fore_" + k, (s * px(6), px(16.6), -px(1.9)), (px(3.6), px(1.0), px(0.9)),
            "gold", (0, 0, s * 5))
        box("fore_" + k, (s * px(8.1), px(15), px(1.0)), (px(0.9), px(3.6), px(2.0)),
            "jade", (0, 0, s * -16))
        box("hand_" + k, (s * px(6), px(11.4), -px(0.2)), (px(3.8), px(3.2), px(4.0)),
            "skin", (0, 0, s * 4))
        for f in range(2):
            box("hand_" + k, (s * px(6) + (f - 0.5) * px(1.6), px(9.8), -px(1.4)),
                (px(1.2), px(1.8), px(2.0)), "skin", (0, 0, s * 4))

        # ============ legs ============
        box("thigh_" + k, (s * px(2), px(8.4), 0), (px(4), px(7.2), px(4)), "skin", t(1.2))
        scales("thigh_" + k, (s * px(2), px(8.6), -px(1.9)), (px(1.7), px(1.8), px(0.8)),
               "plate", 2, 2)
        box("thigh_" + k, (s * px(3.7), px(8.4), px(0.2)), (px(0.8), px(4.4), px(3.2)),
            "plate", (0, 0, s * -8))
        box("shin_" + k, (s * px(2), px(3.4), 0), (px(4), px(6.4), px(4)), "plate", t(1.2))
        box("shin_" + k, (s * px(2), px(5.4), -px(1.9)), (px(3.4), px(1.0), px(0.9)),
            "gold", (0, 0, s * 4))
        box("shin_" + k, (s * px(2), px(2.6), -px(1.8)), (px(1.4), px(3.0), px(0.9)), "jade")
        box("foot_" + k, (s * px(2), px(1.0), -px(1.4)), (px(4.4), px(2.0), px(6.0)),
            "jade", (0, 0, s * 2))
        box("foot_" + k, (s * px(2), px(0.7), -px(3.8)), (px(4.0), px(1.4), px(1.6)),
            "plate", (-8, 0, 0))
        box("foot_" + k, (s * px(2), px(2.0), px(1.0)), (px(4.2), px(1.2), px(2.4)),
            "plate", (-8, 0, 0))

    # ============ flowing scarf ============
    for bone, cy, cz, w, pitch in (("scarf_a", 22.6, 3.0, 5.6, -12),
                                   ("scarf_b", 18.6, 3.8, 5.0, -28),
                                   ("scarf_c", 14.6, 4.2, 4.0, -46)):
        box(bone, (-px(0.4), px(cy), px(cz)), (px(w), px(4.2), px(0.7)), "jade", (pitch, 0, 6))
        box(bone, (-px(0.4), px(cy), px(cz) + px(0.5)), (px(w) * 0.7, px(3.6), px(0.4)),
            "plate", (pitch, 0, 10))


def build_geo():
    bones = []
    for name, pivot in PIVOT.items():
        entry = {"name": name, "pivot": pivot, "cubes": []}
        if name in PARENT:
            entry["parent"] = PARENT[name]
        for c in CUBES.get(name, []):
            u, v = Q[MAT[c["m"]]]
            w, h, d = c["s"]
            sy = min(120.0, max(10.0, h * 10.0))
            faces = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                across = d if face in ("north", "south") else w
                su = min(120.0, max(10.0, across * 10.0))
                faces[face] = {"uv": [round(u + random.uniform(2, 126 - su), 2),
                                      round(v + random.uniform(2, 126 - sy), 2)],
                               "uv_size": [round(su, 2), round(sy, 2)]}
            out = {"origin": c["o"], "size": c["s"], "uv": faces}
            if c["r"] and any(abs(a) > 0.01 for a in c["r"]):
                out["rotation"] = [round(a, 2) for a in c["r"]]
                out["pivot"] = [round(c["o"][i] + c["s"][i] / 2, 3) for i in range(3)]
            entry["cubes"].append(out)
        bones.append(entry)
    return bones


def write_geo(bones):
    GEO.parent.mkdir(parents=True, exist_ok=True)
    GEO.write_text(json.dumps({"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.tnc.wind_warden",
                        "texture_width": 256, "texture_height": 256,
                        "visible_bounds_width": 4, "visible_bounds_height": 5,
                        "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}, separators=(",", ":")), encoding="utf-8", newline="\n")


def write_animations():
    A = {}

    def clip(name, length, loop=False):
        A[name] = {"animation_length": length, "bones": {}}
        if loop:
            A[name]["loop"] = True
        return A[name]["bones"]

    def ch(b, bone, key, vals):
        b.setdefault(bone, {})[key] = {str(k): v for k, v in vals}

    b = clip("idle", 3.6, True)
    ch(b, "torso", "rotation", [(0, [1, 0, -0.6]), (1.8, [-1.2, 0, 0.6]), (3.6, [1, 0, -0.6])])
    ch(b, "chest", "rotation", [(0, [0, 0, 0]), (1.8, [-1.6, 0, 0]), (3.6, [0, 0, 0])])
    ch(b, "head", "rotation", [(0, [2, -4, 0]), (1.8, [-1, 4, 0]), (3.6, [2, -4, 0])])
    for n, a in (("plume_a", 5), ("plume_b", 9), ("plume_c", 13)):
        ch(b, n, "rotation", [(0, [0, 0, 0]), (1.8, [-a, a * 0.5, 0]), (3.6, [0, 0, 0])])
    for n, a in (("scarf_a", 5), ("scarf_b", 9), ("scarf_c", 13)):
        ch(b, n, "rotation", [(0, [0, 0, 0]), (1.8, [-a, 0, a * 0.4]), (3.6, [0, 0, 0])])
    for s in (-1, 1):
        n = str(s)
        ch(b, "arm_" + n, "rotation", [(0, [4, 0, s * 4]), (1.8, [1, 0, s * 6]), (3.6, [4, 0, s * 4])])
        ch(b, "fore_" + n, "rotation", [(0, [-12, 0, 0]), (1.8, [-16, 0, 0]), (3.6, [-12, 0, 0])])

    b = clip("walk", 1.6, True)
    ch(b, "root", "position", [(0, [0, 0, 0]), (0.4, [0, 3, 0]), (0.8, [0, 0, 0]),
                               (1.2, [0, 3, 0]), (1.6, [0, 0, 0])])
    ch(b, "torso", "rotation", [(0, [2, 0, 2]), (0.8, [2, 0, -2]), (1.6, [2, 0, 2])])
    ch(b, "head", "rotation", [(0, [0, 6, 0]), (0.8, [0, -6, 0]), (1.6, [0, 6, 0])])
    for n, a in (("scarf_a", 8), ("scarf_b", 14), ("scarf_c", 20)):
        ch(b, n, "rotation", [(0, [0, 0, 0]), (0.8, [-a, 0, a * 0.6]), (1.6, [0, 0, 0])])
    for s in (-1, 1):
        n = str(s)
        ch(b, "thigh_" + n, "rotation", [(0, [s * 16, 0, 0]), (0.8, [-s * 16, 0, 0]), (1.6, [s * 16, 0, 0])])
        ch(b, "shin_" + n, "rotation", [(0, [-s * 6, 0, 0]), (0.8, [s * 16, 0, 0]), (1.6, [-s * 6, 0, 0])])
        ch(b, "arm_" + n, "rotation", [(0, [-s * 10, 0, s * 5]), (0.8, [s * 10, 0, s * 5]), (1.6, [-s * 10, 0, s * 5])])
        ch(b, "fore_" + n, "rotation", [(0, [-10, 0, 0]), (0.8, [-18, 0, 0]), (1.6, [-10, 0, 0])])

    for name, length, body in (
            ("slash_left", 1.15, {"torso": [(0, [0, 0, 0]), (0.32, [0, -34, 0]), (0.55, [0, 34, 0]), (1.15, [0, 0, 0])],
                                  "arm_1": [(0, [4, 0, 4]), (0.28, [-72, 0, -58]), (0.52, [-84, 0, 42]), (1.15, [4, 0, 4])],
                                  "fore_1": [(0, [-12, 0, 0]), (0.28, [-46, 0, 0]), (0.52, [-6, 0, 0]), (1.15, [-12, 0, 0])]}),
            ("slash_right", 1.15, {"torso": [(0, [0, 0, 0]), (0.32, [0, 34, 0]), (0.55, [0, -34, 0]), (1.15, [0, 0, 0])],
                                   "arm_1": [(0, [4, 0, 4]), (0.28, [-72, 0, 62]), (0.52, [-84, 0, -48]), (1.15, [4, 0, 4])],
                                   "fore_1": [(0, [-12, 0, 0]), (0.28, [-30, 0, 0]), (0.52, [-4, 0, 0]), (1.15, [-12, 0, 0])]}),
            ("overhead", 1.5, {"root": [(0, [0, 0, 0]), (0.75, [0, 6, 0]), (1.0, [0, 0, 0]), (1.5, [0, 0, 0])],
                               "torso": [(0, [0, 0, 0]), (0.7, [-26, 0, 0]), (1.0, [26, 0, 0]), (1.5, [0, 0, 0])],
                               "arm_1": [(0, [4, 0, 4]), (0.7, [-168, 0, -14]), (1.0, [-34, 0, 10]), (1.5, [4, 0, 4])],
                               "fore_1": [(0, [-12, 0, 0]), (0.7, [-24, 0, 0]), (1.0, [-6, 0, 0]), (1.5, [-12, 0, 0])]}),
            ("spin_sweep", 1.6, {"root": [(0, [0, 0, 0]), (1.35, [0, 360, 0]), (1.6, [0, 360, 0])],
                                 "arm_1": [(0, [4, 0, 4]), (0.35, [-88, 0, 58]), (1.25, [-88, 0, 58]), (1.6, [4, 0, 4])],
                                 "fore_1": [(0, [-12, 0, 0]), (0.35, [-4, 0, 0]), (1.25, [-4, 0, 0]), (1.6, [-12, 0, 0])]}),
            ("spear_thrust", 1.5, {"root": [(0, [0, 0, 0]), (0.55, [0, 0, 6]), (0.8, [0, 0, -12]), (1.5, [0, 0, 0])],
                                   "torso": [(0, [0, 0, 0]), (0.55, [-4, 26, 0]), (0.8, [10, -18, 0]), (1.5, [0, 0, 0])],
                                   "arm_1": [(0, [4, 0, 4]), (0.55, [-36, 0, 26]), (0.8, [-92, 0, -6]), (1.5, [4, 0, 4])],
                                   "arm_-1": [(0, [4, 0, -4]), (0.55, [-40, 0, -30]), (0.8, [-86, 0, -12]), (1.5, [4, 0, -4])]}),
            ("spear_rip", 1.3, {"torso": [(0, [0, 0, 0]), (0.4, [18, 0, 0]), (0.62, [-22, 0, 0]), (1.3, [0, 0, 0])],
                                "arm_1": [(0, [4, 0, 4]), (0.4, [-24, 0, -8]), (0.62, [-150, 0, -18]), (1.3, [4, 0, 4])]}),
            ("spear_throw", 1.7, {"root": [(0, [0, 0, 0]), (0.6, [0, 0, 5]), (0.85, [0, 0, -8]), (1.7, [0, 0, 0])],
                                  "torso": [(0, [0, 0, 0]), (0.6, [-6, 40, 0]), (0.85, [8, -26, 0]), (1.7, [0, 0, 0])],
                                  "arm_1": [(0, [4, 0, 4]), (0.6, [-46, 0, 46]), (0.85, [-116, 0, -18]), (1.7, [4, 0, 4])]}),
            ("death", 2.0, {"root": [(0, [0, 0, 0]), (2.0, [-74, 0, 12])],
                            "torso": [(0, [0, 0, 0]), (0.7, [16, 0, 6]), (2.0, [8, 0, 14])],
                            "head": [(0, [0, 0, 0]), (1.2, [26, 0, 8]), (2.0, [34, 0, 10])]})):
        b = clip(name, length)
        for bone, vals in body.items():
            ch(b, bone, "rotation", vals)

    b = clip("phase_shift", 2.4)
    ch(b, "root", "position", [(0, [0, 0, 0]), (0.5, [0, -4, 0]), (1.8, [0, -4, 0]), (2.4, [0, 0, 0])])
    ch(b, "torso", "rotation", [(0, [0, 0, 0]), (0.5, [-18, 0, 0]), (1.1, [-30, 0, 0]), (2.4, [0, 0, 0])])
    ch(b, "head", "rotation", [(0, [0, 0, 0]), (0.5, [-26, 0, 0]), (1.1, [-34, 0, 0]), (2.4, [0, 0, 0])])
    for n, a in (("plume_b", 20), ("plume_c", 30), ("scarf_b", 22), ("scarf_c", 32)):
        ch(b, n, "rotation", [(0, [0, 0, 0]), (0.8, [-a, 0, 0]), (2.4, [0, 0, 0])])
    for s in (-1, 1):
        n = str(s)
        ch(b, "arm_" + n, "rotation", [(0, [4, 0, s * 4]), (0.5, [-24, 0, s * -56]),
                                       (1.1, [-34, 0, s * -70]), (2.4, [4, 0, s * 4])])

    ANIM.parent.mkdir(parents=True, exist_ok=True)
    ANIM.write_text(json.dumps({"format_version": "1.8.0", "animations": A}, indent=2),
                    encoding="utf-8", newline="\n")


def paint_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))

    def quad(origin, base, accent, grain, streaks):
        tile = Image.new("RGBA", (128, 128), base + (255,))
        noise = Image.effect_noise((128, 128), grain).convert("L")
        tile = Image.composite(Image.new("RGBA", (128, 128), accent + (255,)), tile,
                               noise.point(lambda v: 255 if v > 172 else 0))
        d = ImageDraw.Draw(tile)
        for _ in range(streaks):
            x, y = random.randrange(128), random.randrange(128)
            shade = tuple(max(0, min(255, c + random.randint(-15, 15))) for c in base)
            d.line([(x, y), (x + random.randint(6, 26), y + random.randint(-2, 2))],
                   fill=shade + (255,))
        img.paste(tile, origin)

    quad(Q[0], (210, 158, 118), (180, 126, 90), 26, 170)   # skin
    quad(Q[1], (36, 150, 106), (19, 110, 78), 22, 150)     # emerald plate
    quad(Q[2], (21, 94, 72), (12, 62, 48), 18, 120)        # dark jade
    quad(Q[3], (234, 198, 108), (178, 144, 66), 20, 130)   # gold

    d = ImageDraw.Draw(img)
    for _ in range(150):
        x, y = random.randrange(130, 252), random.randrange(2, 126)
        d.line([(x, y), (x + random.randint(2, 9), y)], fill=(150, 252, 200, 255))
    TEX.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEX)


def main():
    sculpt()
    bones = build_geo()
    write_geo(bones)
    write_animations()
    paint_texture()
    cubes = [c for b in bones for c in b["cubes"]]
    rot = sum(1 for c in cubes if "rotation" in c)
    sizes = sorted(max(c["size"]) for c in cubes)
    med = sizes[len(sizes) // 2]
    print(f"{len(bones)} bones; {len(cubes)} cubes; {100 * rot // max(1, len(cubes))}% rotated; "
          f"median {med:.1f}u ({100 * med / 86.4:.1f}% of height); "
          f"{GEO.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
