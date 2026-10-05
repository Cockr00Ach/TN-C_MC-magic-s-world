# -*- coding: utf-8 -*-
"""gen_dragon_block_model.py -- Bedrock geo  ->  vanilla block model (drawn by a Display entity).

Why: the dragon was drawn by GeckoLib, which (per the disassembly of geckolib-4.8.4)
hardcodes yaw = 0 for every non-LivingEntity; our dragon is a plain Entity, so its yaw/pitch
never reached the model. Every workaround (renderer-side re-rotation, culling overrides,
head anchoring) still left the author with "the dragon does not appear at all".

The author's suggestion (2026-10-04: "你把他当成block来使用好不好") is the reliable route:
turn the geo into a **vanilla model json** and let a **Display entity** draw it -- vanilla
bakes that model itself, so no third-party renderer sits in the path any more.

How a rigged Bedrock model becomes a static vanilla one:
  * a bone pose (default: the `dash` clip's most-bent frame) is evaluated with the same
    matrix code tools/preview_geo_anim.py uses (that file is how the model was verified);
  * each cube keeps its true size and its own rotation inside the bone, and the whole chain
    from the cube up to `root` is folded into ONE vanilla element rotation.
    Vanilla composes element rotation as rx(-X) @ ry(-Y) @ rz(Z) (the X/Y signs are flipped
    relative to Bedrock), so the extracted angles are solved against that exact product and
    round-trip checked here;
  * translations come out of the same matrix, so the pose is exact to floating point;
  * per-face uv is mapped through the box-uv layout validated by tools/face_uv_check.py.

Output: src/main/resources/assets/tnc/models/entity/dragon_block.json
Run:    python tools/gen_dragon_block_model.py [--clip dash] [--time 0.3] [--out ...]
ASCII only.
"""
import json
import math
import os
import sys

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "dragon.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "dragon.animation.json")
OUT = os.path.join("src", "main", "resources", "assets", "tnc", "models", "entity",
                   "dragon_block.json")
TEXTURE = "tnc:entity/dragon_bedrock"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402  (the validated matrix + uv maths)


# ---- plain right-handed rotation matrices (deg). Do NOT reuse preview's rz/ry helpers here:
#      those are clockwise-positive to match Bedrock's axis semantics. ------------------
def _rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]]


def _ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]]


def _rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]]


def mmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(tf, p):
    r, t = tf
    return [sum(r[i][k] * p[k] for k in range(3)) + t[i] for i in range(3)]


def vanilla_matrix(x, y, z):
    """What vanilla builds for element rotation {x, y, z}."""
    return mmul(_rx(-x), mmul(_ry(-y), _rz(z)))


def extract_vanilla_angles(m):
    """Solve m == rx(-X) @ ry(-Y) @ rz(Z) numerically.

    Done by search, not by an asin/atan2 derivation: an earlier hand-derived version of this
    function passed review and was still wrong by ~180 degrees on some bones (the same class
    of mistake that made the dragon fly backwards). A tiny coordinate-descent over three
    angles is exact to <0.01 degrees here and cannot silently pick a mirrored branch.
    """
    m00, m01, m02 = m[0]
    m10, m11, m12 = m[1]
    m20, m21, m22 = m[2]
    # closed-form seed (vanilla applies rx(-X) @ ry(-Y) @ rz(Z) to a ZYX product):
    y = math.degrees(math.asin(max(-1.0, min(1.0, m02))))
    x = math.degrees(math.atan2(-m12, m22))
    z = math.degrees(math.atan2(-m01, m00))
    x, y, z = -x, -y, z                      # because the chain negates X and Y

    def err(a, b, c):
        r = vanilla_matrix(a, b, c)
        return max(abs(r[i][j] - m[i][j]) for i in range(3) for j in range(3))

    best = err(x, y, z)
    step = 45.0
    while step > 0.02:
        improved = False
        for i in range(3):
            for delta in (step, -step):
                trial = [x, y, z]
                trial[i] += delta
                e = err(*trial)
                if e < best - 1.0e-12:
                    best, x, y, z = e, trial[0], trial[1], trial[2]
                    improved = True
        if not improved:
            step *= 0.5
    return x, y, z


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)

    opts = {}
    for a in sys.argv[1:]:
        if a.startswith("--") and "=" in a:
            k, v = a.split("=", 1)
            opts[k] = v
    clip = opts.get("--clip", "dash")
    t = float(opts.get("--time", "0.3"))
    out = opts.get("--out", OUT)
    texture = opts.get("--texture", "tnc:entity/dragon_bedrock")
    part = opts.get("--particle", texture)

    P.FRONT = "-Z"
    geo, order = P.load_geo(GEO)
    anim = P.load_anim(ANIM, clip)
    tfs = P.world_transforms(geo, anim, t)

    elements = []
    worst = 0.0
    for bone in order:
        tf = tfs[bone]
        for cube in geo[bone].get("cubes", []):
            o = [float(v) for v in cube["origin"]]
            s = [float(v) for v in cube["size"]]
            uv = cube.get("uv")
            if not isinstance(uv, list) or min(s) <= 0.0:
                continue

            # cube-local rotation about its own centre, then the whole bone chain
            rot = cube.get("rotation") or [0, 0, 0]
            centre = [o[i] + s[i] / 2.0 for i in range(3)]
            local = P.mmul(P.translate(centre),
                           P.mmul(P.rxyz(*rot), P.translate([-c for c in centre])))
            full = P.mmul(tf, local)

            c_world = apply(full, centre)
            lo = [c_world[i] - s[i] / 2.0 for i in range(3)]
            hi = [c_world[i] + s[i] / 2.0 for i in range(3)]

            x, y, z = extract_vanilla_angles(full[0])
            rebuild = vanilla_matrix(x, y, z)
            worst = max(worst, max(abs(rebuild[i][j] - full[0][i][j])
                                   for i in range(3) for j in range(3)))

            u, v = float(uv[0]), float(uv[1])
            w, h, d = s
            rects = {
                "up":    (u + d, v, w, d),
                "down":  (u + d + w, v, w, d),
                "west":  (u, v + d, d, h),                 # -X
                "north": (u + d, v + d, w, h),             # -Z
                "east":  (u + d + w, v + d, d, h),         # +X
                "south": (u + d + w + d, v + d, w, h),     # +Z
            }
            faces = {name: {"uv": [fx, fy, fx + fw, fy + fh], "texture": "#0"}
                     for name, (fx, fy, fw, fh) in rects.items()}

            element = {
                "from": [round(v2, 4) for v2 in lo],
                "to": [round(v2, 4) for v2 in hi],
                "faces": faces,
            }
            if max(abs(x), abs(y), abs(z)) > 1.0e-4:
                element["rotation"] = {
                    "origin": [round(v2, 4) for v2 in c_world],
                    "x": round(x, 3), "y": round(y, 3), "z": round(z, 3),
                    "rescale": False,
                }
            elements.append(element)

    model = {
        "__comment": ("TN-C dragon, generated by tools/gen_dragon_block_model.py from "
                      "dragon.geo.json clip=%s t=%.2f (%d cubes). Drawn by a "
                      "Display.BlockDisplay (see light/TNDragonDisplayEntity)."
                      % (clip, t, len(elements))),
        "parent": "block/block",
        "ambientocclusion": False,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.2, 0.2, 0.2]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        },
        "texture_size": [512, 512],
        "textures": {"0": texture, "particle": part},
        "elements": elements,
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=1)

    xs = [e["from"][0] for e in elements] + [e["to"][0] for e in elements]
    ys = [e["from"][1] for e in elements] + [e["to"][1] for e in elements]
    zs = [e["from"][2] for e in elements] + [e["to"][2] for e in elements]
    print("wrote %s" % out)
    print("  elements=%d  rotation round-trip worst err=%.2e" % (len(elements), worst))
    print("  bounds x=%.1f..%.1f y=%.1f..%.1f z=%.1f..%.1f  (model units, 16/block)"
          % (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
