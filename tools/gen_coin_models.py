# -*- coding: utf-8 -*-
"""
gen_coin_models.py -- make the three coin models LOADABLE without changing their shape.

WHAT WAS WRONG (author 2026-10-09: "我的金银铜币怎么还是紫黑方块啊")
    The coin models carry four blocks with `rotation: {angle: -90, axis: y}`.
    Vanilla block models only accept 22.5 degree steps (-45/-22.5/0/22.5/45), and ONE
    illegal angle makes the WHOLE model fail to load:
        [ModelManager] Failed to load model tnc:models/item/copper_coin.json
        JsonParseException: Invalid rotation -90.0 found, only -45/-22.5/0/22.5/45 allowed
    The item then draws the purple-black missing-model cube.

    Because vanilla cannot express that angle, it has to be BAKED into the geometry.
    Baking must reproduce the SAME SHAPE.

★ THE MISTAKE THIS FILE NOW GUARDS AGAINST (2026-10-09, second attempt)
    The first version of the baker turned each blade the WRONG WAY: instead of swinging
    the four rim blades into the coin's plane it left them sticking perpendicular to it,
    so the coin visibly changed shape. The author's reaction: "我模型都被你们改坏了啊，
    怎么方块都变了".
    Two things now prevent a repeat:
      1. a self-test compares the baked geometry against the authored geometry and refuses
         to write anything that differs (see `authored_corners` / `shapes_equal`);
      2. the authored geometry is kept in-tree at
         `tools/coin_authored/<name>.authored.json` so the operation is reproducible and
         reviewable instead of depending on a scratch backup.
    The pivot is also clamped into the element's own extent (the authored origins sit
    slightly outside the blade, which is what made the hand-math error so easy).

HOW THE ROTATION MAPS (BlockElement / Rotation, 1.20.1)
    axis y, angle A:
        A < 0 :  (x, z) -> ( x*c + z*s, -x*s + z*c )      (clockwise seen from +Y)
        A > 0 :  (x, z) -> ( x*c - z*s,  x*s + z*c )
    with c = cos|A|, s = sin|A|.

FACE / UV BOOKKEEPING for a 90 degree turn about Y
    A face's texture travels with the geometry (for -90: the new `east` is drawn with the
    old `west` face, etc.), and the local u/v axes of the four side faces are unchanged,
    so the uv arrays are copied as-is from the source face.

ASCII only. Usage:
    python tools/gen_coin_models.py            # bake (writes only if shape matches)
    python tools/gen_coin_models.py --check    # report only, exit 1 if anything to do
"""
import json
import math
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "tnc", "models", "item")
AUTHORED_DIR = os.path.join(REPO, "tools", "coin_authored")

ALLOWED = {-45.0, -22.5, 0.0, 22.5, 45.0}
EPS = 1e-6

FACE_TURN = {"north": "west", "west": "south", "south": "east", "east": "north"}
FACE_TURN_INV = {v: k for k, v in FACE_TURN.items()}


def round_half(value):
    """Round .5 AWAY from zero, like Math.round in Java (Python rounds to even)."""
    return math.floor(value + 0.5) if value >= 0 else math.ceil(value - 0.5)


def rotate_y(x, z, degrees):
    r = math.radians(abs(degrees))
    c, s = math.cos(r), math.sin(r)
    if degrees < 0:
        return (x * c + z * s, -x * s + z * c)
    return (x * c - z * s, x * s + z * c)


def rotate_point(p, pivot, degrees):
    dx, dz = rotate_y(p[0] - pivot[0], p[2] - pivot[2], degrees)
    return (pivot[0] + dx, p[1], pivot[2] + dz)


def carried_uvs(faces, angle):
    out = {}
    for face, data in faces.items():
        if abs(angle) < EPS or face in ("up", "down"):
            out[face] = dict(data)
            continue
        turn = -90 if angle > 0 else 90
        src = FACE_TURN[face] if turn == -90 else FACE_TURN_INV[face]
        out[face] = dict(faces[src])
    return out


def clamp_pivot(pivot, frm, to):
    """Keep the pivot inside the element's own extent (0.5 grid, and inside if the
    element is thinner than that -- an out-of-extent pivot produced the earlier error)."""
    out = []
    for i in range(3):
        lo, hi = min(frm[i], to[i]), max(frm[i], to[i])
        p = pivot[i]
        if p >= lo and p <= hi:
            out.append(p)
        else:
            out.append(round_half(p))
    return out


def authored_corners(model):
    """Every element's corners AFTER its own rotation -- the shape we must reproduce."""
    out = []
    for el in model["elements"]:
        frm = [float(v) for v in el["from"]]
        to = [float(v) for v in el["to"]]
        rot = el.get("rotation") or {}
        angle = float(rot.get("angle", 0.0))
        axis = rot.get("axis", "y")
        pivot = [float(v) for v in (rot.get("origin") or [0.0, 0.0, 0.0])]
        corners = [(frm[0] if ax == 0 else to[0],
                    frm[1] if ay == 0 else to[1],
                    frm[2] if az == 0 else to[2])
                   for ax in (0, 1) for ay in (0, 1) for az in (0, 1)]
        if abs(angle) < EPS:
            out.append(corners)
        elif axis == "y":
            out.append([rotate_point(c, pivot, angle) for c in corners])
        else:
            raise AssertionError("self-test only handles Y rotations")
    return out


def boxes(corners_list):
    """The same corner sets, each normalised to (min, max) rounded to 0.01."""
    out = []
    for corners in corners_list:
        lo = tuple(round(min(p[i] for p in corners), 2) for i in range(3))
        hi = tuple(round(max(p[i] for p in corners), 2) for i in range(3))
        out.append((lo, hi))
    return sorted(out)


def bake_element(element):
    """One illegally-rotated element -> axis-aligned cube(s) + a report line."""
    rot = element.get("rotation") or {}
    angle = float(rot.get("angle", 0.0))
    axis = rot.get("axis", "y")
    if abs(angle) < EPS or round(angle, 4) in ALLOWED:
        return [element], None
    if axis != "y" or abs(abs(angle) - 90.0) > EPS:
        nearest = min(ALLOWED, key=lambda a: abs(a - angle))
        clone = json.loads(json.dumps(element))
        clone["rotation"] = dict(rot, angle=nearest)
        return [clone], "snapped %s/%s -> %s" % (angle, axis, nearest)

    frm = [float(v) for v in element["from"]]
    to = [float(v) for v in element["to"]]
    pivot = clamp_pivot([float(v) for v in (rot.get("origin") or [0.0, 0.0, 0.0])], frm, to)
    corners = [(frm[0] if ax == 0 else to[0],
                frm[1] if ay == 0 else to[1],
                frm[2] if az == 0 else to[2])
               for ax in (0, 1) for ay in (0, 1) for az in (0, 1)]
    moved = [rotate_point(c, pivot, angle) for c in corners]
    new_from = [round(min(p[i] for p in moved), 3) for i in range(3)]
    new_to = [round(max(p[i] for p in moved), 3) for i in range(3)]
    return [{"from": new_from, "to": new_to, "faces": carried_uvs(element["faces"], angle)}], \
        "baked %s/%s pivot=%s -> x %s..%s z %s..%s" % (
            angle, axis, pivot, new_from[0], new_to[0], new_from[2], new_to[2])


def bake_model(model):
    fixed = []
    reports = []
    for el in model.get("elements", []):
        replacement, report = bake_element(el)
        fixed.extend(replacement)
        if report:
            reports.append(report)
    baked = json.loads(json.dumps(model))
    baked["elements"] = fixed
    return baked, reports


def main():
    check_only = "--check" in sys.argv
    if not os.path.isdir(MODEL_DIR):
        print("ERROR: %s not found" % MODEL_DIR)
        return 1

    problems = 0
    touched = 0
    for name in sorted(f for f in os.listdir(MODEL_DIR) if f.endswith(".json")):
        path = os.path.join(MODEL_DIR, name)
        with open(path, "r", encoding="utf-8-sig") as fh:
            current = json.load(fh)

        illegal = [el for el in current.get("elements", [])
                   if abs(float((el.get("rotation") or {}).get("angle", 0.0))) > EPS
                   and round(float((el.get("rotation") or {}).get("angle", 0.0)), 4) not in ALLOWED]
        if not illegal:
            continue

        # where the authored geometry lives (checked in, so this is reproducible)
        authored_path = os.path.join(AUTHORED_DIR, name.replace(".json", ".authored.json"))
        source = current
        if os.path.isfile(authored_path):
            with open(authored_path, "r", encoding="utf-8-sig") as fh:
                source = json.load(fh)

        baked, reports = bake_model(source)

        # ★ self-test: the baked geometry must be the authored geometry, box for box
        if boxes(authored_corners(source)) != boxes(authored_corners(baked)):
            print("FAIL %s: baked geometry differs from the authored shape -- not writing" % name)
            for a, b in zip(boxes(authored_corners(source)), boxes(authored_corners(baked))):
                if a != b:
                    print("        authored %s  !=  baked %s" % (a, b))
            problems += 1
            continue

        touched += 1
        if check_only:
            print("WOULD FIX %-22s %d element(s)" % (name, len(reports)))
            continue

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(baked, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        angles = sorted({float((e.get("rotation") or {}).get("angle", 0.0)) for e in baked["elements"]})
        print("%-22s %d element(s) baked, angles now %s, shape VERIFIED"
              % (name, len(reports), angles))
        for line in reports:
            print("      %s" % line)

    if touched == 0:
        print("nothing to do: every item model already uses legal rotation angles")
    if check_only and touched:
        return 1
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
