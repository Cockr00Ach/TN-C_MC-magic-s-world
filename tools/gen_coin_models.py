# -*- coding: utf-8 -*-
"""
gen_coin_models.py -- re-import an authored coin model so 1.20.1 can actually load it.

WHY THIS FILE EXISTS (author: "我的金银铜币怎么还是紫黑方块啊" /
                     "游戏里金银铜三个币模型都有问题，只有金币的模型是正确的")
    BlockElement.Deserializer (1.20.1) reads an element rotation as
        getAxis(o)  -> GsonHelper.getAsString(o, "axis")   -- REQUIRED
        getAngle(o) -> GsonHelper.getAsFloat(o, "angle")   -- REQUIRED,
                       then |angle| must be 0 / 22.5 / 45
    so a coin model dies -- and the whole model never bakes, i.e. the purple-black
    missing-model cube -- for either of two reasons:
      1. LEGACY rotation block `{"x":0,"y":-90,"z":0,"origin":[...]}` (what Blockbench
         writes for a free rotation): it has neither "axis" nor "angle" -> JsonSyntaxException;
      2. `"angle": -90` (any +-90) -> JsonParseException "Invalid rotation -90.0 found".
    Copper/silver were hit by (2) first, gold is hit by (1) -- hence "all three broken".

HOW THE BAKE IS DONE (read out of the vanilla sources, not guessed)
    FaceBakery.applyElementRotation uses
        Quaternionf.rotationAxis(angle, axis)          -- the standard right-handed rotation
    so for axis Y and angle A (degrees):
        x' = x*cos A + z*sin A
        z' = -x*sin A + z*cos A        (A=-90 gives (x,z) -> (-z, x))
    Vertex/UV pairing comes from FaceInfo.fromFacing(dir).getVertexInfo(i) + BlockFaceUV:
    vertex i of a face gets uv corner 0, 1, 2, 3 -> (u0,v0) (u0,v1) (u1,v1) (u1,v0).
    The bake therefore rebuilds each rotated element as an axis-aligned cube and derives its
    uv array so that the (position, uv) pairs of every face are EXACTLY the rotated pairs of
    the source.  That is the self-test: nothing is written unless the pairs match.

    !! The first version of this file had the sign of the Y rotation backwards (it produced
    (z,-x) for A<0, i.e. it baked +90 instead of -90).  That is why the four rim blades
    landed on the wrong corner of the coin ("我模型都被你们改坏了啊，怎么方块都变了").
    With the correct sign the blades lie flat on the coin rim:
        e2 [6,0,13..20,1,15]  pivot 18,13 -> x 16..18, z  1..15   (east rim)
        e3 [-12,0,13..2,1,15] pivot  0,13 -> x -2..0,  z  1..15   (west rim)
        e4 [9,0,14..21,1,15]  pivot 20,13 -> x 18..19, z  2..14
        e5 [-12,0,14..0,1,15] pivot -1,13 -> x  0..1,  z  2..14
    (mirror-symmetric about x=8 -- the wrong bake gave 18..20 / 11..25 etc, i.e. blades
     sticking out past the coin's corner.)

USAGE
    python tools/gen_coin_models.py            # re-import + write (only if the self-test passes)
    python tools/gen_coin_models.py --check    # report only, exit 1 when something is to do

    The authored original of a model is read from `tools/coin_authored/<name>.authored.json`
    when present -- that is the file to re-import from, so the bake is reproducible even
    after the model in `assets/.../models/item/` has been rewritten.

    Two steps, in this order:
      1. every item model that vanilla cannot read is re-imported (gold: legacy rotation);
      2. `silver_coin.json` / `copper_coin.json` are re-imported FROM the gold model with
         their own texture, because the author's three coins are one shape -- that is what
         keeps gold's seven marks (see tools/coin_marks.py) on the same front/back pixels of
         all three coins.  The old copper/silver files were baked with the inverted Y
         rotation: their rim blades sat on the coin's corner instead of on its rim.

ASCII only.
"""
import json
import math
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "tnc", "models", "item")
AUTHORED_DIR = os.path.join(REPO, "tools", "coin_authored")

EPS = 1e-6
# BlockElement.getAngle: 0 or +-22.5 or +-45 -- anything else kills the whole model
LEGAL_ANGLES = (0.0, 22.5, -22.5, 45.0, -45.0)

# The three coins are one shape with three textures; gold is the model the author authored,
# the other two are re-imported from it (see derive_from_gold).
GOLD_MODEL = "gold_coin.json"
DERIVED_FROM_GOLD = {"silver_coin.json": "tnc:item/coin_silver",
                     "copper_coin.json": "tnc:item/coin_copper"}

# ---- net.minecraft.client.renderer.FaceInfo: vertex order + corner constant per direction
MIN_X, MIN_Y, MIN_Z = "MIN_X", "MIN_Y", "MIN_Z"
MAX_X, MAX_Y, MAX_Z = "MAX_X", "MAX_Y", "MAX_Z"
FACE_VERTICES = {
    "down":  ((MIN_X, MIN_Y, MAX_Z), (MIN_X, MIN_Y, MIN_Z), (MAX_X, MIN_Y, MIN_Z), (MAX_X, MIN_Y, MAX_Z)),
    "up":    ((MIN_X, MAX_Y, MIN_Z), (MIN_X, MAX_Y, MAX_Z), (MAX_X, MAX_Y, MAX_Z), (MAX_X, MAX_Y, MIN_Z)),
    "north": ((MAX_X, MAX_Y, MIN_Z), (MAX_X, MIN_Y, MIN_Z), (MIN_X, MIN_Y, MIN_Z), (MIN_X, MAX_Y, MIN_Z)),
    "south": ((MIN_X, MAX_Y, MAX_Z), (MIN_X, MIN_Y, MAX_Z), (MAX_X, MIN_Y, MAX_Z), (MAX_X, MAX_Y, MAX_Z)),
    "west":  ((MIN_X, MAX_Y, MIN_Z), (MIN_X, MIN_Y, MIN_Z), (MIN_X, MIN_Y, MAX_Z), (MIN_X, MAX_Y, MAX_Z)),
    "east":  ((MAX_X, MAX_Y, MAX_Z), (MAX_X, MIN_Y, MAX_Z), (MAX_X, MIN_Y, MIN_Z), (MAX_X, MAX_Y, MIN_Z)),
}


def shape_of(frm, to):
    """setupShape(): the FaceInfo corner constants -> real coordinates."""
    return {MIN_X: float(frm[0]), MIN_Y: float(frm[1]), MIN_Z: float(frm[2]),
            MAX_X: float(to[0]), MAX_Y: float(to[1]), MAX_Z: float(to[2])}


def vertex_pos(corner, shape):
    return (shape[corner[0]], shape[corner[1]], shape[corner[2]])


def uv_corner(uvs, index, rotation=0):
    """BlockFaceUV.getU/getV for vertex `index` of a face with a 0/90/180/270 uv rotation."""
    shifted = (index + rotation // 90) % 4
    u = uvs[2] if shifted not in (0, 1) else uvs[0]
    v = uvs[3] if shifted not in (0, 3) else uvs[1]
    return (u, v)


def rotate_y(point, pivot, angle):
    """FaceBakery.applyElementRotation for axis Y: right-handed rotation about +Y."""
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    dx, dz = point[0] - pivot[0], point[2] - pivot[2]
    return (pivot[0] + dx * c + dz * s, point[1], pivot[2] - dx * s + dz * c)


def uv_array_for_vertices(pairs):
    """Inverse of uv_corner: the 4 (position, uv) of a face in FaceInfo vertex order ->
    (the [u0,v0,u1,v1] array, the 0/90/180/270 uv rotation that reproduces them).
    getU/getV only ever hand out two u values and two v values, laid out per rotation, so a
    rotated element usually needs the face `rotation` field to stay pixel-identical."""
    for rotation in (0, 90, 180, 270):
        slots = []
        for index in range(4):
            shifted = (index + rotation // 90) % 4
            slots.append((0 if shifted in (0, 1) else 2, 1 if shifted in (0, 3) else 3))
        uvs = [None, None, None, None]
        usable = True
        for index, (_pos, uv) in enumerate(pairs):
            for slot, value in zip(slots[index], uv):
                if uvs[slot] is None:
                    uvs[slot] = value
                elif abs(uvs[slot] - value) > 1e-6:
                    usable = False
        if usable and all(value is not None for value in uvs):
            return uvs, rotation
    raise ValueError("this face's four texture corners cannot be written as a uv box: %s"
                     % [uv for _pos, uv in pairs])


def element_quads(element):
    """Every face of `element` as [(position x4, uv x4)] with the element rotation applied.
    Positions are the face's own corners (no model rotation), exactly like FaceBakery."""
    frm, to = element["from"], element["to"]
    shape = shape_of(frm, to)
    rot = element.get("rotation") or {}
    angle = read_angle(rot)
    pivot = [float(v) for v in (rot.get("origin") or [0.0, 0.0, 0.0])]
    out = {}
    for face, data in (element.get("faces") or {}).items():
        uvs = [float(v) for v in data["uv"]]
        rotation = int(data.get("rotation", 0) or 0)
        pairs = []
        for i, corner in enumerate(FACE_VERTICES[face]):
            pos = vertex_pos(corner, shape)
            if abs(angle) > EPS:
                pos = rotate_y(pos, pivot, angle)
            pairs.append((pos, uv_corner(uvs, i, rotation)))
        out[face] = (pairs, data.get("texture", "#0"))
    return out


def read_angle(rot):
    """The element rotation angle talked about by BlockElement.getAngle."""
    if "angle" in rot:
        return float(rot["angle"])
    # legacy Blockbench/1.12 block: the Y angle is the one vanilla would have used
    return float(rot.get("y", 0.0))


def read_rotation(element):
    """-> dict(axis, angle, origin, form) or None. Raises for shapes we must not guess."""
    rot = element.get("rotation")
    if not rot:
        return None
    origin = [float(v) for v in (rot.get("origin") or [0.0, 0.0, 0.0])]
    if "angle" in rot or "axis" in rot:
        axis = str(rot.get("axis", "y")).lower()
        return {"axis": axis, "angle": float(rot.get("angle", 0.0)), "origin": origin,
                "form": "angle/axis"}
    legacy = [float(rot.get(k, 0.0)) for k in ("x", "y", "z")]
    used = [k for k, v in zip(("x", "y", "z"), legacy) if abs(v) > EPS]
    if not used:
        return {"axis": "y", "angle": 0.0, "origin": origin, "form": "legacy xyz"}
    if len(used) > 1:
        raise ValueError("legacy rotation mixes axes %s -- vanilla has one axis per element, "
                         "get a fresh export from the author" % "+".join(used))
    axis = used[0]
    return {"axis": axis, "angle": legacy[("x", "y", "z").index(axis)], "origin": origin,
            "form": "legacy xyz"}


def face_direction_on_box(pairs, frm, to):
    """Which face of the axis-aligned box `frm..to` the rotated quad has landed on."""
    for axis, (low, high) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
        values = {round(p[axis], 4) for p, _ in pairs}
        if len(values) != 1:
            continue
        value = values.pop()
        if abs(value - frm[axis]) < 1e-4:
            return low
        if abs(value - to[axis]) < 1e-4:
            return high
    raise ValueError("rotated face %s is not axis-aligned on the baked box %s..%s"
                     % ([p for p, _ in pairs], frm, to))


def bake_element(element):
    """One element -> a list of elements vanilla can load, plus a one-line report."""
    rot = read_rotation(element)
    if rot is None:
        return [element], None
    if rot["form"] == "legacy xyz" and abs(rot["angle"]) < EPS:
        clone = json.loads(json.dumps(element))
        clone.pop("rotation", None)
        return [clone], "legacy zero rotation -> dropped (vanilla needs angle+axis)"

    legal = any(abs(rot["angle"] - a) < EPS for a in LEGAL_ANGLES)
    legacy = rot["form"] == "legacy xyz"
    if legal and not legacy:
        return [element], None                      # already written the way vanilla reads it
    if legal and legacy:
        # same angle, but vanilla wants the angle/axis spelling
        clone = json.loads(json.dumps(element))
        clone["rotation"] = {"angle": rot["angle"], "axis": rot["axis"], "origin": rot["origin"]}
        return [clone], "legacy rotation -> angle/axis form (was unloadable)"
    # an illegal angle: only a quarter turn about Y can become an axis-aligned cube
    if rot["axis"] != "y" or abs(abs(rot["angle"]) - 90.0) > EPS:
        raise ValueError("cannot express %s deg about %s as an axis-aligned cube; ask the "
                         "author for an export that uses 0/22.5/45" % (rot["angle"], rot["axis"]))

    source = element_quads(element)
    positions = [p for pairs, _ in source.values() for p, _ in pairs]
    new_from = [round(min(p[i] for p in positions), 4) for i in range(3)]
    new_to = [round(max(p[i] for p in positions), 4) for i in range(3)]
    baked_shape = shape_of(new_from, new_to)
    baked = {"from": new_from, "to": new_to, "faces": {}}
    for face, (pairs, texture) in source.items():
        target = face_direction_on_box(pairs, new_from, new_to)
        wanted = {tuple(round(v, 4) for v in pos): uv for pos, uv in pairs}
        own = []
        for corner in FACE_VERTICES[target]:
            pos = tuple(round(v, 4) for v in vertex_pos(corner, baked_shape))
            if pos not in wanted:
                raise ValueError("rotated %s face corner %s is not a corner of the baked cube"
                                 % (face, pos))
            own.append((pos, wanted[pos]))
        uvs, face_rotation = uv_array_for_vertices(own)
        data = {"uv": uvs, "texture": texture}
        for extra, value in element["faces"][face].items():
            if extra not in ("uv", "texture", "rotation"):
                data[extra] = value
        if face_rotation:
            data["rotation"] = face_rotation
        baked["faces"][target] = data
    return [baked], ("baked %s deg about %s, pivot %s -> x %s..%s z %s..%s"
                     % (rot["angle"], rot["axis"], rot["origin"],
                        new_from[0], new_to[0], new_from[2], new_to[2]))


def quads_match(source_element, baked_element):
    """Self-test: the baked cube must carry the same (position, uv) pairs, face for face
    (after the rotation has moved each face onto its new side)."""
    def canonical(element):
        out = []
        for face, (pairs, texture) in element_quads(element).items():
            out.append((texture, tuple(sorted((tuple(round(v, 2) for v in pos),
                                               (round(uv[0], 4), round(uv[1], 4)))
                                              for pos, uv in pairs))))
        return sorted(out)

    want, have = canonical(source_element), canonical(baked_element)
    if want == have:
        return None
    for a, b in zip(want, have):
        if a != b:
            return "%s != %s" % (a, b)
    return "different number of faces: %d != %d" % (len(want), len(have))


def bake_model(model):
    fixed = []
    reports = []
    for element in model.get("elements", []):
        try:
            replacement, report = bake_element(element)
        except ValueError as error:
            raise ValueError("%s (element %s -> %s)" % (error, element.get("from"), element.get("to")))
        fixed.extend(replacement)
        if report:
            reports.append(report)
    baked = json.loads(json.dumps(model))
    baked["elements"] = fixed
    return baked, reports


def needs_work(model):
    """Why vanilla would refuse this model (empty list = loadable)."""
    issues = []
    for index, element in enumerate(model.get("elements", [])):
        rot = element.get("rotation")
        if not rot:
            continue
        if "angle" not in rot and "axis" not in rot:
            issues.append("element %d has a legacy x/y/z rotation block (no angle/axis)"
                          % index)
            continue
        angle = float(rot.get("angle", 0.0))
        if not any(abs(angle - a) < EPS for a in LEGAL_ANGLES):
            issues.append("element %d has rotation angle %s" % (index, angle))
    return issues


def main():
    check_only = "--check" in sys.argv
    if not os.path.isdir(MODEL_DIR):
        print("ERROR: %s not found" % MODEL_DIR)
        return 1

    problems = 0
    touched = 0
    for name in sorted(f for f in os.listdir(MODEL_DIR) if f.endswith(".json")):
        path = os.path.join(MODEL_DIR, name)
        with open(path, "r", encoding="utf-8-sig") as handle:
            current = json.load(handle)

        issues = needs_work(current)
        if not issues:
            continue

        authored_path = os.path.join(AUTHORED_DIR, name.replace(".json", ".authored.json"))
        source = current
        origin = "the model file itself"
        if os.path.isfile(authored_path):
            with open(authored_path, "r", encoding="utf-8-sig") as handle:
                source = json.load(handle)
            origin = os.path.relpath(authored_path, REPO)

        print("%s: %s" % (name, "; ".join(issues)))
        print("      re-importing from %s" % origin)
        try:
            baked, reports = bake_model(source)
        except ValueError as error:
            print("FAIL %s: %s" % (name, error))
            problems += 1
            continue

        broken = []
        if len(baked["elements"]) != len(source.get("elements", [])):
            print("FAIL %s: element count changed" % name)
            problems += 1
            continue
        for before, after in zip(source.get("elements", []), baked["elements"]):
            reason = quads_match(before, after)
            if reason:
                broken.append(reason)
        if broken:
            print("FAIL %s: baked geometry differs from the authored shape -- not writing" % name)
            for line in broken[:4]:
                print("        %s" % line)
            problems += 1
            continue

        touched += 1
        if check_only:
            print("WOULD FIX %-22s %d element(s)" % (name, len(reports)))
            continue

        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(baked, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
        print("%-22s %d element(s) re-imported, shape+uv VERIFIED" % (name, len(reports)))
        for line in reports:
            print("      %s" % line)

    if touched == 0 and problems == 0:
        print("nothing to do: every item model already loads in vanilla")
    derived, derive_problems = derive_from_gold(check_only)
    problems += derive_problems
    if check_only and (touched or derived):
        return 1
    return 0 if problems == 0 else 1


def derive_from_gold(check_only):
    """Silver and copper are the SAME coin as gold with another texture (author's design), so
    they are re-imported from the gold model instead of being baked on their own -- that is
    what keeps the seven marks on the coin's front/back faces in the same place on all three
    coins.  Returns (files that need a write, problems)."""
    gold_path = os.path.join(MODEL_DIR, GOLD_MODEL)
    if not os.path.isfile(gold_path):
        return 0, 0
    with open(gold_path, "r", encoding="utf-8-sig") as handle:
        gold = json.load(handle)
    if needs_work(gold):
        print("FAIL %s still has an unloadable rotation, refusing to derive from it"
              % GOLD_MODEL)
        return 0, 1

    touched = 0
    problems = 0
    for name, texture in sorted(DERIVED_FROM_GOLD.items()):
        model = json.loads(json.dumps(gold))
        model["textures"] = {"0": texture, "particle": texture}
        want = json.dumps(model, indent=2, ensure_ascii=False) + "\n"
        path = os.path.join(MODEL_DIR, name)
        have = None
        if os.path.isfile(path):
            with open(path, "r", encoding="utf-8-sig") as handle:
                have = json.load(handle)
        if have == model:
            continue
        touched += 1
        if check_only:
            print("WOULD DERIVE %-20s from %s (texture %s)" % (name, GOLD_MODEL, texture))
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(want)
        print("%-22s re-imported from %s, texture %s, geometry+uv identical"
              % (name, GOLD_MODEL, texture))
    return touched, problems


if __name__ == "__main__":
    sys.exit(main())
