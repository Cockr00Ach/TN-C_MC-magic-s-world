"""墨翼飞虎 / Ink Winged Tiger -- built with the same idiom as the ink crane.

Measured recipe from the ink crane (the reference model):
    259 bones, 687 cubes, 93% rotated, mean |tilt| 22.7 deg, 2048 texture.

So this generator uses a *deep* bone hierarchy and many **strongly rotated** boxes:
the tilt is what makes ink-wash brushwork read instead of crates. Four-legged
predator: spine chain, three-segment neck, jaw, ears, four five-segment legs,
two four-segment wings with feather bones, eight-segment tail, plus emitter bones
for brush trails.

Ink-wash palette: charcoal body, near-black stripes, pale paper muzzle/chest,
pale glowing eyes. No saturated colours.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
GEO = ASSETS / "geo/entity/ink_winged_tiger.geo.json"
ANIM = ASSETS / "animations/entity/ink_winged_tiger.animation.json"
TEX = ASSETS / "textures/entity/ink_winged_tiger.png"

random.seed(9090)
TILT = 21.0

# Four 256x256 quadrants of a 512 atlas: ink body, ink dark, paper, glow.
Q = {0: (0, 0), 1: (256, 0), 2: (0, 256), 3: (256, 256)}
MAT = {"ink": 0, "dark": 1, "paper": 2, "glow": 3}

PIVOT = {"root": [0, 0, 0], "spine_0": [0, 26, 18]}
PARENT = {"spine_0": "root"}

# ---- spine / neck / head ----
SPINE = [("spine_1", 0, 27, 6), ("spine_2", 0, 28, -8)]
NECK = [("neck_0", 0, 30, -17), ("neck_1", 0, 32, -23), ("neck_2", 0, 34, -28)]
HEAD = [("head", 0, 35, -34), ("jaw", 0, 33, -38)]
for i, (n, x, y, z) in enumerate(SPINE):
    PIVOT[n] = [x, y, z]
    PARENT[n] = "spine_0" if i == 0 else SPINE[i - 1][0]
for i, (n, x, y, z) in enumerate(NECK):
    PIVOT[n] = [x, y, z]
    PARENT[n] = "spine_2" if i == 0 else NECK[i - 1][0]
for i, (n, x, y, z) in enumerate(HEAD):
    PIVOT[n] = [x, y, z]
    PARENT[n] = "neck_2" if i == 0 else "head"
PIVOT["eyes"] = [0, 36, -38]
PARENT["eyes"] = "head"
for s in (-1, 1):
    k = "l" if s < 0 else "r"
    PIVOT["ear_" + k] = [s * 5, 40, -32]
    PARENT["ear_" + k] = "head"
    PIVOT["brow_" + k] = [s * 4, 37, -37]
    PARENT["brow_" + k] = "head"

# ---- tail: eight segments sweeping back and up ----
TAIL = []
for i in range(8):
    t = i / 7.0
    n = f"tail_{i}"
    PIVOT[n] = [math.sin(t * 2.4) * 6, 26 + math.sin(t * 3.0) * 7 + t * 6, 24 + i * 5]
    PARENT[n] = "spine_0" if i == 0 else f"tail_{i - 1}"
    TAIL.append(n)

# ---- legs: shoulder/hip -> elbow/knee -> wrist/ankle -> paw -> toes ----
LEGS = {}
for s in (-1, 1):
    k = "l" if s < 0 else "r"
    front = (s * 7, 26, -10)
    back = (s * 8, 25, 15)
    chain_front = [("shoulder_" + k, front, "spine_2"),
                   ("upper_" + k, (s * 7, 19, -9), "shoulder_" + k),
                   ("lower_" + k, (s * 7, 12, -8), "upper_" + k),
                   ("paw_f_" + k, (s * 7, 5, -10), "lower_" + k)]
    chain_back = [("hip_" + k, back, "spine_0"),
                  ("thigh_" + k, (s * 8, 18, 16), "hip_" + k),
                  ("shin_" + k, (s * 8, 11, 14), "thigh_" + k),
                  ("paw_b_" + k, (s * 8, 5, 12), "shin_" + k)]
    LEGS[k] = chain_front + chain_back
    for n, p, parent in LEGS[k]:
        PIVOT[n] = list(p)
        PARENT[n] = parent
    for n, p, parent in (("toes_f_" + k, (s * 7, 2, -13), "paw_f_" + k),
                         ("toes_b_" + k, (s * 8, 2, 9), "paw_b_" + k)):
        PIVOT[n] = list(p)
        PARENT[n] = parent

# ---- wings: base -> mid -> tip, plus primary feathers ----
WINGS = {}
for s in (-1, 1):
    k = "l" if s < 0 else "r"
    chain = [("wing_base_" + k, (s * 6, 31, -6), "spine_2"),
             ("wing_mid_" + k, (s * 14, 34, -4), "wing_base_" + k),
             ("wing_tip_" + k, (s * 23, 36, 0), "wing_mid_" + k)]
    WINGS[k] = chain
    for n, p, parent in chain:
        PIVOT[n] = list(p)
        PARENT[n] = parent
    for f in range(4):
        n = f"feather_{k}_{f}"
        PIVOT[n] = [s * (24 + f * 3), 33 - f * 4, 2 + f * 3]
        PARENT[n] = "wing_tip_" + k

# ---- brush emitters (drive the ink trails, like the crane's emitter bones) ----
for name, pivot, parent in (("emitter_wing_l", (-26, 34, 4), "wing_tip_l"),
                            ("emitter_wing_r", (26, 34, 4), "wing_tip_r"),
                            ("emitter_tail", PIVOT["tail_7"], "tail_7"),
                            ("emitter_mouth", (0, 32, -40), "jaw"),
                            ("emitter_claw_l", (-7, 2, -14), "toes_f_l"),
                            ("emitter_claw_r", (7, 2, -14), "toes_f_r")):
    PIVOT[name] = list(pivot)
    PARENT[name] = parent

CUBES = {}


def box(bone, centre, size, mat="ink", rot=None):
    CUBES.setdefault(bone, []).append(
        {"o": [round(centre[i] - size[i] / 2, 3) for i in range(3)],
         "s": [round(max(0.4, v), 3) for v in size],
         "r": [round(a, 2) for a in rot] if rot else None, "m": mat})


def brush(a=TILT):
    return (random.uniform(-a, a), random.uniform(-a, a), random.uniform(-a, a))


def tube(bone, start, end, r0, r1, mat="ink", rings=4, per_ring=6, roll=0.0):
    """A tapered tube of strongly rotated boxes: limbs, neck, tail."""
    ax = [end[i] - start[i] for i in range(3)]
    length = math.sqrt(sum(a * a for a in ax)) or 1.0
    for i in range(rings):
        t = (i + 0.5) / rings
        c = [start[j] + ax[j] * t for j in range(3)]
        r = r0 + (r1 - r0) * t
        for s in range(per_ring):
            a = 2 * math.pi * s / per_ring + i * 0.6 + roll
            x = c[0] + math.cos(a) * r * 0.92
            z = c[2] + math.sin(a) * r * 0.92
            box(bone, (x, c[1], z), (r * 0.78, length / rings * 1.2, r * 0.78), mat,
                (random.uniform(-14, 14), math.degrees(a), random.uniform(-14, 14)))


def mass(bone, centre, radii, mat="ink", count=18, spread=1.0):
    """An organic mass: rotated boxes scattered over an ellipsoid shell."""
    for _ in range(count):
        u, v = random.random() * 2 * math.pi, math.acos(2 * random.random() - 1)
        nx, ny, nz = math.sin(v) * math.cos(u), math.cos(v), math.sin(v) * math.sin(u)
        x = centre[0] + nx * radii[0] * 0.92 * spread
        y = centre[1] + ny * radii[1] * 0.92 * spread
        z = centre[2] + nz * radii[2] * 0.92 * spread
        box(bone, (x, y, z),
            (radii[0] * 0.5, radii[1] * 0.42, radii[2] * 0.5), mat,
            (math.degrees(v) * 0.5, math.degrees(u), random.uniform(-18, 18)))


def strip(bone, centre, size, mat, rot=None):
    """A single brush stroke: long, thin, tilted."""
    box(bone, centre, size, mat, rot or brush(26))


def paint():
    CUBES.clear()

    # ================= body =================
    mass("spine_0", (0, 26, 17), (11, 9, 12), "ink", 26)
    mass("spine_1", (0, 27.5, 5), (12, 10, 13), "ink", 30)
    mass("spine_2", (0, 28.5, -9), (13, 11, 13), "ink", 32)     # chest / ribcage
    strip("spine_2", (0, 24, -10), (14, 3, 12), "paper", (0, 0, 0))   # pale belly
    strip("spine_1", (0, 23, 6), (11, 3, 12), "paper", (2, 0, 0))
    # Ink stripes: long thin strokes over the flanks and shoulders.
    for s in (-1, 1):
        for i in range(5):
            z = -12 + i * 6
            strip("spine_1" if i < 3 else "spine_2",
                  (s * (10 - i * 0.4), 28 + (i % 2) * 2, z), (1.6, 5.0, 1.6), "dark",
                  (0, 0, s * (18 + i * 4)))
    # Shoulder blades and hips.
    for s in (-1, 1):
        mass("spine_0", (s * 7, 28, 16), (5, 5, 6), "ink", 8)
        mass("spine_2", (s * 7, 30, -10), (5.5, 5, 6), "ink", 8)

    # ================= neck + head =================
    tube("neck_0", (0, 29, -15), (0, 31, -22), 6.0, 5.2, "ink", 3, 7)
    tube("neck_1", (0, 31, -22), (0, 33, -28), 5.2, 4.6, "ink", 3, 7)
    tube("neck_2", (0, 33, -28), (0, 34.5, -33), 4.6, 4.2, "ink", 3, 7)
    mass("head", (0, 35.5, -35), (6.0, 5.2, 6.4), "ink", 20)
    strip("head", (0, 34.2, -36), (5.0, 2.6, 5.0), "paper", (4, 0, 0))   # muzzle
    box("head", (0, 34.6, -40), (3.0, 2.0, 1.6), "paper", brush(8))      # nose bridge
    box("head", (0, 33.9, -40.4), (2.2, 1.2, 1.2), "dark", brush(6))     # nose pad
    mass("jaw", (0, 32.4, -37), (4.2, 2.2, 4.6), "paper", 10)
    # Fangs.
    for s in (-1, 1):
        box("jaw", (s * 2.0, 31.4, -39.5), (1.2, 2.4, 1.2), "paper", (0, 0, s * 8))
        box("head", (s * 2.0, 32.9, -39.5), (1.0, 2.0, 1.0), "paper", (0, 0, s * 6))
    # Eyes: pale glowing slits under a heavy brow.
    for s in (-1, 1):
        strip("brow_" + ("l" if s < 0 else "r"), (s * 3.4, 36.6, -38.4),
              (4.6, 1.5, 3.0), "ink", (0, 0, s * 16))
        box("eyes", (s * 3.6, 35.6, -38.2), (3.2, 1.5, 1.2), "glow", (0, 0, s * 14))
    # Ears.
    for s in (-1, 1):
        k = "l" if s < 0 else "r"
        box("ear_" + k, (s * 4.6, 39.6, -33), (3.0, 3.6, 1.6), "ink", (s * -8, 0, s * 16))
        box("ear_" + k, (s * 4.6, 39.4, -33.6), (2.0, 2.6, 1.0), "paper", (s * -8, 0, s * 16))
    # Whisker strokes.
    for s in (-1, 1):
        strip("head", (s * 4.0, 33.6, -40), (3.4, 0.5, 0.5), "paper", (0, 0, s * 28))

    # ================= legs =================
    for k, chain in LEGS.items():
        s = -1 if k == "l" else 1
        for name, pivot, parent in chain:
            if name.startswith(("shoulder_", "hip_")):
                mass(name, (pivot[0], pivot[1], pivot[2]), (4.4, 4.6, 5.0), "ink", 10)
            elif name.startswith(("upper_", "thigh_")):
                tube(name, (pivot[0], pivot[1] + 3, pivot[2]),
                     (pivot[0], pivot[1] - 4, pivot[2]), 3.8, 2.9, "ink", 3, 6)
            elif name.startswith(("lower_", "shin_")):
                tube(name, (pivot[0], pivot[1] + 3, pivot[2]),
                     (pivot[0], pivot[1] - 3, pivot[2]), 2.9, 2.2, "ink", 3, 6)
            else:
                mass(name, (pivot[0], pivot[1], pivot[2] + 1), (3.4, 2.4, 4.0), "ink", 8)
        for toes in ("toes_f_" + k, "toes_b_" + k):
            base = PIVOT[toes]
            for t in range(4):
                box(toes, (base[0] + (t - 1.5) * 1.7, base[1] + 0.4, base[2] - 1.2),
                    (1.2, 1.6, 3.4), "paper", (random.uniform(-8, 8), random.uniform(-14, 14), 0))

    # ================= wings =================
    for k, chain in WINGS.items():
        s = -1 if k == "l" else 1
        for index, (name, pivot, parent) in enumerate(chain):
            if index == 0:
                tube(name, (pivot[0], pivot[1], pivot[2]),
                     (pivot[0] + s * 7, pivot[1] + 3, pivot[2] + 1), 4.2, 3.2, "ink", 3, 6)
            else:
                tube(name, (pivot[0] - s * 3, pivot[1] - 1, pivot[2]),
                     (pivot[0] + s * 6, pivot[1] + 2, pivot[2] + 3), 3.0, 2.0, "ink", 3, 5)
            # Membrane between the arm bones: broad ink slabs.
            strip(name, (pivot[0] + s * 3, pivot[1] - 2, pivot[2] + 4),
                  (9.0, 1.2, 9.0), "dark", (10, s * 12, s * -14))
        for f in range(4):
            name = f"feather_{k}_{f}"
            base = PIVOT[name]
            for seg in range(3):
                box(name, (base[0] + s * seg * 2.6, base[1] - seg * 1.9, base[2] + seg * 1.6),
                    (1.8, 1.0, 5.4 - seg * 0.8), "ink",
                    (random.uniform(-12, 12), s * (12 + seg * 8), s * -18))

    # ================= tail =================
    for i, name in enumerate(TAIL):
        p = PIVOT[name]
        r = 3.4 - i * 0.22
        tube(name, (p[0], p[1], p[2] - 2), (p[0], p[1] + 0.6, p[2] + 3.6),
             r, r * 0.82, "ink", 2, 5)
        if i % 2 == 0:
            strip(name, (p[0], p[1] + 1.4, p[2] + 1), (r * 2.2, 1.6, 2.4), "dark",
                  (0, 0, random.uniform(-24, 24)))
    tip = PIVOT[TAIL[-1]]
    for f in range(4):
        strip(TAIL[-1], (tip[0] + (f - 1.5) * 1.8, tip[1] + f * 1.2, tip[2] + 5),
              (1.4, 1.0, 5.0), "dark", (random.uniform(-16, 16), random.uniform(-20, 20),
                                        random.uniform(-20, 20)))
    return CUBES


def build_geo():
    bones = []
    for name, pivot in PIVOT.items():
        entry = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": []}
        if name in PARENT:
            entry["parent"] = PARENT[name]
        for c in CUBES.get(name, []):
            u, v = Q[MAT[c["m"]]]
            w, h, d = c["s"]
            sy = min(240.0, max(8.0, h * 12.0))
            faces = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                across = d if face in ("north", "south") else w
                su = min(240.0, max(8.0, across * 12.0))
                faces[face] = {"uv": [round(u + random.uniform(2, 254 - su), 2),
                                      round(v + random.uniform(2, 254 - sy), 2)],
                               "uv_size": [round(su, 2), round(sy, 2)]}
            out = {"origin": c["o"], "size": c["s"], "uv": faces}
            if c["r"] and any(abs(a) > 0.01 for a in c["r"]):
                out["rotation"] = c["r"]
                out["pivot"] = [round(c["o"][i] + c["s"][i] / 2, 3) for i in range(3)]
            entry["cubes"].append(out)
        bones.append(entry)
    return bones


def write_geo(bones):
    GEO.parent.mkdir(parents=True, exist_ok=True)
    GEO.write_text(json.dumps({"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.tnc.ink_winged_tiger",
                        "texture_width": 512, "texture_height": 512,
                        "visible_bounds_width": 5, "visible_bounds_height": 3,
                        "visible_bounds_offset": [0, 1, 0]},
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

    b = clip("idle", 3.2, True)
    for n, a in (("spine_1", 1.4), ("spine_2", 1.8)):
        ch(b, n, "rotation", [(0, [a, 0, 0]), (1.6, [-a, 0, 0]), (3.2, [a, 0, 0])])
    ch(b, "neck_1", "rotation", [(0, [-2, 3, 0]), (1.6, [1, -3, 0]), (3.2, [-2, 3, 0])])
    ch(b, "head", "rotation", [(0, [3, -5, 0]), (1.6, [-2, 5, 0]), (3.2, [3, -5, 0])])
    for i, n in enumerate(TAIL):
        a = 3 + i * 2.2
        ch(b, n, "rotation", [(0, [0, 0, a]), (1.6, [0, 0, -a]), (3.2, [0, 0, a])])
    for k in ("l", "r"):
        s = -1 if k == "l" else 1
        for n, a in ((f"wing_base_{k}", 3), (f"wing_mid_{k}", 5), (f"wing_tip_{k}", 7)):
            ch(b, n, "rotation", [(0, [0, 0, s * a]), (1.6, [0, 0, s * -a]), (3.2, [0, 0, s * a])])
        for f in range(4):
            ch(b, f"feather_{k}_{f}", "rotation",
               [(0, [0, 0, s * (2 + f)]), (1.6, [0, 0, s * -(2 + f)]), (3.2, [0, 0, s * (2 + f)])])

    b = clip("prowl", 1.2, True)
    for k in ("l", "r"):
        s = -1 if k == "l" else 1
        ch(b, f"upper_{k}", "rotation", [(0, [s * 22, 0, 0]), (0.6, [-s * 22, 0, 0]), (1.2, [s * 22, 0, 0])])
        ch(b, f"lower_{k}", "rotation", [(0, [-s * 14, 0, 0]), (0.6, [s * 18, 0, 0]), (1.2, [-s * 14, 0, 0])])
        ch(b, f"thigh_{k}", "rotation", [(0, [-s * 22, 0, 0]), (0.6, [s * 22, 0, 0]), (1.2, [-s * 22, 0, 0])])
        ch(b, f"shin_{k}", "rotation", [(0, [s * 16, 0, 0]), (0.6, [-s * 18, 0, 0]), (1.2, [s * 16, 0, 0])])
    ch(b, "spine_1", "rotation", [(0, [2, 0, 3]), (0.6, [2, 0, -3]), (1.2, [2, 0, 3])])
    ch(b, "root", "position", [(0, [0, 0, 0]), (0.3, [0, 1.6, 0]), (0.6, [0, 0, 0]),
                               (0.9, [0, 1.6, 0]), (1.2, [0, 0, 0])])

    b = clip("roar", 1.6)
    ch(b, "neck_1", "rotation", [(0, [0, 0, 0]), (0.4, [-16, 0, 0]), (1.2, [-20, 0, 0]), (1.6, [0, 0, 0])])
    ch(b, "head", "rotation", [(0, [0, 0, 0]), (0.4, [-24, 0, 0]), (1.2, [-28, 0, 0]), (1.6, [0, 0, 0])])
    ch(b, "jaw", "rotation", [(0, [0, 0, 0]), (0.4, [34, 0, 0]), (1.2, [38, 0, 0]), (1.6, [0, 0, 0])])
    ch(b, "spine_2", "rotation", [(0, [0, 0, 0]), (0.4, [-8, 0, 0]), (1.6, [0, 0, 0])])

    b = clip("wing_gust", 1.5)
    for k in ("l", "r"):
        s = -1 if k == "l" else 1
        ch(b, f"wing_base_{k}", "rotation", [(0, [0, 0, 0]), (0.35, [0, 0, s * 62]),
                                             (0.7, [0, 0, s * -34]), (1.5, [0, 0, 0])])
        ch(b, f"wing_mid_{k}", "rotation", [(0, [0, 0, 0]), (0.35, [0, 0, s * 40]),
                                            (0.7, [0, 0, s * -30]), (1.5, [0, 0, 0])])
        ch(b, f"wing_tip_{k}", "rotation", [(0, [0, 0, 0]), (0.35, [0, 0, s * 30]),
                                            (0.7, [0, 0, s * -26]), (1.5, [0, 0, 0])])

    b = clip("wing_slash_l", 1.1)
    ch(b, "wing_base_l", "rotation", [(0, [0, 0, 0]), (0.3, [0, -40, -70]), (0.55, [0, 30, 40]), (1.1, [0, 0, 0])])
    ch(b, "wing_mid_l", "rotation", [(0, [0, 0, 0]), (0.3, [0, 0, -44]), (0.55, [0, 0, 26]), (1.1, [0, 0, 0])])
    ch(b, "wing_tip_l", "rotation", [(0, [0, 0, 0]), (0.3, [0, 0, -32]), (0.55, [0, 0, 18]), (1.1, [0, 0, 0])])
    ch(b, "spine_2", "rotation", [(0, [0, 0, 0]), (0.3, [0, -22, 0]), (0.55, [0, 20, 0]), (1.1, [0, 0, 0])])

    b = clip("wing_slash_r", 1.1)
    ch(b, "wing_base_r", "rotation", [(0, [0, 0, 0]), (0.3, [0, 40, 70]), (0.55, [0, -30, -40]), (1.1, [0, 0, 0])])
    ch(b, "wing_mid_r", "rotation", [(0, [0, 0, 0]), (0.3, [0, 0, 44]), (0.55, [0, 0, -26]), (1.1, [0, 0, 0])])
    ch(b, "wing_tip_r", "rotation", [(0, [0, 0, 0]), (0.3, [0, 0, 32]), (0.55, [0, 0, -18]), (1.1, [0, 0, 0])])
    ch(b, "spine_2", "rotation", [(0, [0, 0, 0]), (0.3, [0, 22, 0]), (0.55, [0, -20, 0]), (1.1, [0, 0, 0])])

    b = clip("leap", 1.4)
    ch(b, "root", "position", [(0, [0, 0, 0]), (0.5, [0, 10, 0]), (0.85, [0, 16, 0]), (1.4, [0, 0, 0])])
    for k in ("l", "r"):
        s = -1 if k == "l" else 1
        ch(b, f"upper_{k}", "rotation", [(0, [0, 0, 0]), (0.4, [34, 0, 0]), (0.7, [-40, 0, 0]), (1.4, [0, 0, 0])])
        ch(b, f"lower_{k}", "rotation", [(0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.7, [20, 0, 0]), (1.4, [0, 0, 0])])
        ch(b, f"thigh_{k}", "rotation", [(0, [0, 0, 0]), (0.4, [-34, 0, 0]), (0.7, [40, 0, 0]), (1.4, [0, 0, 0])])
        ch(b, f"shin_{k}", "rotation", [(0, [0, 0, 0]), (0.4, [30, 0, 0]), (0.7, [-20, 0, 0]), (1.4, [0, 0, 0])])
    ch(b, "spine_1", "rotation", [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (0.85, [-8, 0, 0]), (1.4, [0, 0, 0])])
    for i, n in enumerate(TAIL):
        a = 6 + i * 3
        ch(b, n, "rotation", [(0, [0, 0, 0]), (0.7, [0, 0, a]), (1.4, [0, 0, 0])])

    b = clip("death", 2.0)
    ch(b, "root", "position", [(0, [0, 0, 0]), (1.4, [0, -6, 0]), (2.0, [0, -8, 0])])
    ch(b, "root", "rotation", [(0, [0, 0, 0]), (2.0, [0, 0, -78])])
    ch(b, "neck_1", "rotation", [(0, [0, 0, 0]), (2.0, [22, 0, 10])])
    ch(b, "jaw", "rotation", [(0, [0, 0, 0]), (1.4, [26, 0, 0]), (2.0, [10, 0, 0])])

    ANIM.parent.mkdir(parents=True, exist_ok=True)
    ANIM.write_text(json.dumps({"format_version": "1.8.0", "animations": A}, indent=2),
                    encoding="utf-8", newline="\n")


def paint_texture():
    """Ink-wash atlas: charcoal washes with dry-brush edges, paper highlights."""
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def wash(box, base, deep, streaks):
        x0, y0, w, h = box
        tile = Image.new("RGBA", (w, h), base + (255,))
        d = ImageDraw.Draw(tile)
        # Wet ink pooling: overlapping soft blotches.
        for _ in range(90):
            cx, cy = random.randrange(w), random.randrange(h)
            r = random.randint(6, 34)
            shade = tuple(max(0, min(255, c + random.randint(-26, 18))) for c in deep)
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=shade + (200,))
        tile = tile.filter(ImageFilter.GaussianBlur(1.6))
        d = ImageDraw.Draw(tile)
        # Dry-brush strokes.
        for _ in range(streaks):
            x, y = random.randrange(w), random.randrange(h)
            ln = random.randint(10, 60)
            shade = tuple(max(0, min(255, c + random.randint(-20, 30))) for c in base)
            d.line([(x, y), (x + ln, y + random.randint(-4, 4))], fill=shade + (255,),
                   width=random.choice((1, 1, 2)))
        img.paste(tile, (x0, y0))

    wash((0, 0, 256, 256), (58, 58, 64), (26, 26, 30), 260)      # ink body
    wash((256, 0, 256, 256), (28, 28, 32), (10, 10, 13), 200)    # near-black stripes
    wash((0, 256, 256, 256), (232, 228, 218), (198, 194, 184), 150)  # paper
    wash((256, 256, 256, 256), (214, 238, 232), (168, 214, 204), 120)  # pale glow

    # Scattered ink spatter for brush texture.
    for _ in range(320):
        x, y = random.randrange(size), random.randrange(size)
        r = random.randint(1, 3)
        a = random.randint(30, 120)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(14, 14, 18, a))

    TEX.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEX)


def main():
    CUBES.clear()
    paint()
    bones = build_geo()
    write_geo(bones)
    write_animations()
    paint_texture()
    cubes = [c for b in bones for c in b["cubes"]]
    rot = [abs(v) for c in cubes if "rotation" in c for v in c["rotation"]]
    sizes = sorted(max(c["size"]) for c in cubes)
    print(f"{len(bones)} bones; {len(cubes)} cubes; "
          f"{100 * sum(1 for c in cubes if 'rotation' in c) // max(1, len(cubes))}% rotated; "
          f"mean tilt {sum(rot) / len(rot):.1f} deg; median cube {sizes[len(sizes) // 2]:.1f}u")
    print(f"geo  -> {GEO.relative_to(ROOT)}")
    print(f"anim -> {ANIM.relative_to(ROOT)}")
    print(f"tex  -> {TEX.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
