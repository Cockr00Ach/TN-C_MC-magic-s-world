# -*- coding: utf-8 -*-
"""
fix_multiaxis_rotations.py -- repair Blockbench exports that Minecraft cannot load.

WHY
    Newer Blockbench writes an element rotation as a MULTI-AXIS object:

        "rotation": { "x": 0, "y": -90, "z": 0, "origin": [18, 0, 13] }

    Vanilla's BlockElement.Deserializer only understands the SINGLE-AXIS form:

        "rotation": { "angle": 90, "axis": "y", "origin": [18, 0, 13] }

    and it throws
        com.google.gson.JsonSyntaxException: Missing axis, expected to find a string
    while loading the model. Result: "Failed to load model ..." and the item renders as the
    purple/black missing model. This is a SILENT failure in the sense that the only clue is
    an ERROR line during the client resource reload.

    Found in this repo on 2026-10-04: copper/silver/gold_coin, thunder_ball,
    dragon_display_light/dark, dragon_block.

WHAT IT DOES
    For every model JSON under src/main/resources/assets/tnc/models:
      * if an element rotation has exactly ONE non-zero axis and that angle is a legal
        single-axis angle (a multiple of 22.5), rewrite it to {angle, axis, origin}
      * if it has MORE THAN ONE non-zero axis, it CANNOT be represented -> the file is
        reported as needing a manual rebuild (do not guess a conversion)
      * a zero-angle rotation is dropped entirely (vanilla treats a missing rotation as none)

    Every value is preserved exactly; the angle sign follows vanilla's convention
    (a negative angle about an axis == the positive angle about that same axis, because the
    deserializer negates it and the 22.5 check uses the absolute value).

ASCII only.
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc", "models")

# vanilla: BlockElement.Deserializer checks Math.abs(angle) % 22.5 == 0 (or 45-degree steps)
LEGAL_STEP = 22.5


def legal(angle):
    return abs(abs(angle) % LEGAL_STEP) < 1e-6 or abs(abs(angle) % LEGAL_STEP - LEGAL_STEP) < 1e-6


def fix_element(el, report):
    rot = el.get("rotation")
    if not isinstance(rot, dict):
        return False
    if "axis" in rot and "angle" in rot:
        return False                       # already vanilla form
    axes = [a for a in ("x", "y", "z") if a in rot]
    if not axes:
        return False

    values = {a: rot.get(a, 0) for a in axes}
    nonzero = [a for a in axes if abs(values[a]) > 1e-6]
    origin = rot.get("origin")

    if not nonzero:
        el.pop("rotation", None)           # zero rotation == no rotation
        report.append("dropped zero rotation")
        return True

    if len(nonzero) > 1:
        report.append("MANUAL: multi-axis non-zero %r -- cannot convert" % values)
        return False

    axis = nonzero[0]
    angle = values[axis]
    if not legal(angle):
        report.append("MANUAL: angle %r on %s is not a multiple of 22.5" % (angle, axis))
        return False

    fixed = {"angle": angle, "axis": axis}
    if origin is not None:
        fixed["origin"] = origin
    el["rotation"] = fixed
    report.append("converted %s=%s -> {angle:%s, axis:%s}" % (axis, angle, angle, axis))
    return True


def walk(node, report):
    """Elements live under a top-level 'elements' array; recurse anyway in case of nesting."""
    changed = False
    if isinstance(node, dict):
        if "from" in node and "to" in node and "faces" in node:
            changed |= fix_element(node, report)
        for v in node.values():
            changed |= walk(v, report)
    elif isinstance(node, list):
        for v in node:
            changed |= walk(v, report)
    return changed


def main():
    if not os.path.isdir(MODELS):
        print("no models dir: %s" % MODELS)
        return 1

    touched = 0
    manual = 0
    for root, _dirs, files in os.walk(MODELS):
        for name in sorted(files):
            if not name.endswith(".json"):
                continue
            path = os.path.join(root, name)
            with open(path, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            report = []
            if not walk(data, report):
                continue
            rel = os.path.relpath(path, REPO)
            bad = [r for r in report if r.startswith("MANUAL")]
            if bad:
                manual += 1
                print("[manual] %s" % rel)
                for r in bad:
                    print("         %s" % r)
                continue
            with open(path, "w", encoding="utf-8", newline="\n") as fh:
                json.dump(data, fh, indent="\t")
                fh.write("\n")
            touched += 1
            print("[fixed]  %s  (%d rotation(s))" % (rel, len(report)))

    print("")
    print("rewritten: %d file(s)" % touched)
    if manual:
        print("NEEDS MANUAL WORK: %d file(s) -- see [manual] above" % manual)
    return 0


if __name__ == "__main__":
    sys.exit(main())
