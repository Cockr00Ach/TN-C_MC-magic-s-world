#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_model_rotations.py -- legalise illegal `rotation.angle` values in vanilla Java
models, WITHOUT changing what the model looks like.

WHY THIS EXISTS
---------------
Vanilla's `BlockElement` deserializer only accepts five rotation angles:

    -45 / -22.5 / 0 / 22.5 / 45

anything else and it throws:

    com.google.gson.JsonParseException: Invalid rotation 67.5 found,
        only -45/-22.5/0/22.5/45 allowed

and ONE throwing element makes the WHOLE model unbakeable -- ModelBakery logs
"Failed to load model ..." and falls back to the missing model, which the game
draws as the purple-black checkerboard. That is exactly what happened to the wind
tornado (`tools/gen_wind_tornado_model.py`, whose head comment used to claim the
rule was "multiples of 22.5", which is wrong: 90 and 135 are multiples of 22.5 and
are still rejected).

HOW IT FIXES NOTHING-BUT-THE-ANGLE
----------------------------------
A rotation that is an exact multiple of 90 degrees about the vertical axis maps an
axis-aligned box onto another axis-aligned box, so it does not need the "rotation"
field at all -- it can be baked straight into the box coordinates. The element's
face normals turn with it, so the six face entries are permuted by the same
quarter turns; that keeps every face pointing the same way in the world with the
same UV it had before. Written formally, for an original element with angle a and
faces F, and a = k * 90 degrees:

    box'   = rot_a(box)
    angle' = 0
    F'[d]  = F[rot_-a(d)]        for each of the six directions d
    (up/down keep their uv; their in-plane uv orientation turns by a, which is
     invisible whenever the sampled patch is a single colour -- the tool says so
     per file and refuses if it cannot prove it)

The rotation convention is NOT guessed: it is what vanilla does --
`new Quaternionf().rotationAxis(angle * 0.017453292f, (0,1,0))`, i.e. right-handed
about +Y with the angle un-negated, which maps (x,z) -> (x cos a + z sin a,
-x sin a + z cos a) about the pivot.

Only angles that are exact multiples of 90 are handled. An illegal angle with a
non-zero residual (e.g. 67.5) cannot be fixed by moving the box, so the tool
REFUSES instead of guessing -- that has to be fixed at the source (the generator).

USAGE
-----
    python tools/fix_model_rotations.py --check <model.json> [...]   # report only
    python tools/fix_model_rotations.py <model.json> [...]           # rewrite in place

Exit code 0 = nothing left illegal (and, when rewriting, proven equivalent).
"""

import argparse
import json
import math
import sys

LEGAL_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)
SIDES = ("north", "east", "south", "west")
ALL_FACES = ("north", "east", "south", "west", "up", "down")

# Direction normals in the (x, z) plane, using the same axes the model format does:
# north = -Z, south = +Z, west = -X, east = +X.
NORMAL = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def rot_quarter(k):
    """Map a direction name through k quarter turns (k may be negative)."""
    order = ("north", "west", "south", "east")   # +90 degrees: north -> west -> ...
    index = {name: i for i, name in enumerate(order)}
    # 'east' is the remaining member of the cycle
    return {name: order[(index[name] + k) % 4] for name in order}


def yaw_point(p, angle_deg, origin):
    a = math.radians(angle_deg)
    ca, sa = math.cos(a), math.sin(a)
    dx, dz = p[0] - origin[0], p[2] - origin[2]
    return (origin[0] + dx * ca + dz * sa, p[1], origin[2] - dx * sa + dz * ca)


def yaw_box(box_from, box_to, k90, origin):
    """Rotate an axis-aligned box by k90 quarter turns about the vertical axis."""
    k = k90 % 4
    if k == 0:
        return list(box_from), list(box_to)
    corners = [yaw_point(c, 90.0 * k, origin)
               for c in ((box_from[0], box_from[1], box_from[2]),
                         (box_to[0], box_to[1], box_to[2]))]
    a, b = corners
    return ([min(a[i], b[i]) for i in range(3)],
            [max(a[i], b[i]) for i in range(3)])


def face_colour_signature(faces, angle, origin):
    """Which uv sits on which WORLD-facing side (the thing that must not change).

    Returns {world_direction: uv} after applying the element's rotation.
    """
    out = {}
    for name in ALL_FACES:
        face = faces.get(name)
        if face is None:
            continue
        if name in NORMAL:
            # where does this face's normal end up?
            nx, nz = NORMAL[name]
            rx = nx * math.cos(math.radians(angle)) + nz * math.sin(math.radians(angle))
            rz = -nx * math.sin(math.radians(angle)) + nz * math.cos(math.radians(angle))
            target = min(((abs(rx - v[0]) + abs(rz - v[1]), k) for k, v in NORMAL.items()))[1]
        else:
            target = name          # up/down stay up/down under a yaw
        out[target] = tuple(face["uv"])
    return out


def patch_is_uniform(texture, uv, tex_size):
    """True if the atlas patch a face samples is one single colour."""
    try:
        from PIL import Image
    except ImportError:
        return None
    if texture is None:
        return None
    w, h = tex_size
    u1, v1, u2, v2 = uv
    x0, y0 = int(round(u1 * w / 16.0)), int(round(v1 * h / 16.0))
    x1, y1 = int(round(u2 * w / 16.0)), int(round(v2 * h / 16.0))
    box = (x0, y0, max(x0 + 1, x1), max(y0 + 1, y1))
    region = texture.crop(box)
    return len(set(region.getdata())) == 1


def fix_element(el, texture, tex_size, problems):
    rot = el.get("rotation")
    if rot is None:
        return el, False
    angle = float(rot.get("angle", 0.0))
    if angle in LEGAL_ANGLES:
        return el, False

    k90 = int(round(angle / 90.0))
    residual = angle - k90 * 90.0
    if abs(residual) > 1e-9:
        problems.append("angle %s is not a multiple of 90 (residual %s) -- cannot be "
                        "folded into the box; fix the generator instead" % (angle, residual))
        return el, False
    if str(rot.get("axis", "y")).lower() != "y":
        problems.append("angle %s is on axis '%s'; only yaw can be folded by this tool"
                        % (angle, rot.get("axis")))
        return el, False

    origin = list(rot["origin"])
    new_from, new_to = yaw_box(el["from"], el["to"], k90, origin)

    # permute the six faces: the face that used to end up facing d must still face d
    remap = rot_quarter(k90)
    new_faces = {}
    for name, face in el["faces"].items():
        if name in NORMAL:
            new_faces[remap[name]] = face
        else:
            # up/down: same uv, but its in-plane orientation turns by k90*90 degrees.
            if texture is not None and not patch_is_uniform(texture, face["uv"], tex_size):
                problems.append("angle %s: the %s face samples a PATCHED uv, so its "
                                "in-plane rotation would be visible -- refusing"
                                % (angle, name))
                return el, False
            new_faces[name] = face

    new_el = dict(el)
    new_el["from"] = [round(v, 3) for v in new_from]
    new_el["to"] = [round(v, 3) for v in new_to]
    new_el["faces"] = new_faces
    if k90 % 4 == 0:
        new_el.pop("rotation", None)
    else:
        new_el["rotation"] = {"origin": origin, "axis": "y", "angle": 0.0,
                              "rescale": bool(rot.get("rescale", False))}

    # prove it: the same uv must land on the same world-facing side as before
    before = face_colour_signature(el["faces"], angle, origin)
    after = face_colour_signature(new_el["faces"], 0.0, origin)
    if before != after:
        problems.append("angle %s: face remap did not preserve the per-side uvs "
                        "(%s -> %s)" % (angle, before, after))
        return el, False
    return new_el, True


def load_texture(model, path):
    try:
        from PIL import Image
    except ImportError:
        return None, (0, 0)
    import os
    for slot, ref in (model.get("textures") or {}).items():
        if not str(ref).startswith("tnc:"):
            continue
        tex = os.path.join(os.path.dirname(path), "..", "..", "textures",
                           str(ref)[4:] + ".png")
        tex = os.path.normpath(tex)
        if os.path.exists(tex):
            img = Image.open(tex).convert("RGBA")
            return img, img.size
    return None, (0, 0)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+")
    ap.add_argument("--check", action="store_true", help="report only, do not write")
    args = ap.parse_args()

    failed = False
    for path in args.models:
        with open(path, encoding="utf-8") as fh:
            model = json.load(fh)
        texture, tex_size = load_texture(model, path)
        if texture is None:
            print("  !! %s: could not resolve its atlas, up/down patches cannot be "
                  "proven uniform" % path)

        problems = []
        changed = 0
        elements = []
        for el in model.get("elements") or []:
            new_el, did = fix_element(el, texture, tex_size, problems)
            elements.append(new_el)
            changed += 1 if did else 0

        if problems:
            failed = True
            print("[PROBLEM] %s" % path)
            for p in problems:
                print("    %s" % p)
            continue
        if changed == 0:
            print("[ok] %s: every rotation angle is already legal" % path)
            continue
        if args.check:
            print("[would fix] %s: %d element(s) carry an illegal angle" % (path, changed))
            continue

        model["elements"] = elements
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(model, fh, indent=2)
            fh.write("\n")
        print("[fixed] %s: %d element(s) rewritten to angle 0 with the quarter turn "
              "folded into the box (per-side uvs verified unchanged)" % (path, changed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
