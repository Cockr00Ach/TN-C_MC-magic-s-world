# -*- coding: utf-8 -*-
"""check_dragon_charge_facing.py -- does the snout point the way the dragon travels?

The author reported (2026-10-04) that BOTH dragons fly backwards
("黑龙和光龙都是倒着飞"). This script is the referee for that, and it must be run before
touching TNDragonEntity.renderYaw / TNDragonPose.fixFacing, because the sign of that one
angle is invisible in review (a wrong one got shipped twice while writing this file).

Two independent checks:

  A. GEOMETRY -- walk the geo with the dash clip applied and print the model-space z of the
     snout cubes (head/jaw) and of the tail cubes (tailFin/tail5) relative to the origin.
     No renderer involved: it just says which local axis the face is on.

  B. RENDER -- rebuild the exact client matrix chain and check the snout ends up along the
     travel direction for north/south/east/west and for a range of pitches:

         GeckoLib  : mulPose(Y, 180 - yaw)      yaw == 0 for every plain Entity
                                               (GeoEntityRenderer.actuallyRender ignores
                                                non-LivingEntity yaw -- see TNDragonPose)
         fixFacing : mulPose(Y, MODEL_YAW_OFFSET)
                     mulPose(Y, renderYaw())    TNDragonEntity.renderYaw()
                     mulPose(X, -renderPitch())

     The travel direction is taken from Entity.calculateViewVector, i.e. the authoritative
     MC convention: (sin(-yRot)cos(xRot), -sin(xRot), cos(-yRot)cos(xRot)).

  C. REGRESSION -- brute-forces every "plausible" formula (constant offsets AND the linear
     180 - yRot) and prints which ones pass, so a future edit that looks equivalent but is
     180 deg off on east/west fails loudly here instead of in game.

Run:  python tools/check_dragon_charge_facing.py
ASCII only.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "dragon.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "dragon.animation.json")

MODEL_YAW_OFFSET = 180.0      # TNDragonPose.MODEL_YAW_OFFSET
GECKOLIB_YAW = 180.0          # what GeckoLib adds for a plain (non-Living) Entity


def render_yaw(y_rot):
    """TNDragonEntity.updateRenderOffsets() -- keep in sync with the java source."""
    return 180.0 - y_rot


def ry(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]]


def rx(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]]


def mmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot(r, v):
    return [sum(r[i][k] * v[k] for k in range(3)) for i in range(3)]


def face_dir_yaw(dx, dz):
    """TNDragonEntity.faceDir's yaw, verbatim (Mth.atan2 == Math.atan2)."""
    return math.degrees(math.atan2(dz, dx)) - 90.0


def travel_dir(y_rot, x_rot):
    """Entity.calculateViewVector(xRot, yRot) -- the authoritative MC facing."""
    f = math.radians(x_rot)
    f1 = -math.radians(y_rot)
    return (math.sin(f1) * math.cos(f), -math.sin(f), math.cos(f1) * math.cos(f))


def client_matrix(y_rot, x_rot, yaw_fn=render_yaw):
    m = ry(GECKOLIB_YAW)                    # GeckoLib, plain Entity
    m = mmul(m, ry(MODEL_YAW_OFFSET))       # fixFacing step 1
    m = mmul(m, ry(yaw_fn(y_rot)))          # fixFacing step 2
    m = mmul(m, rx(-x_rot))                 # fixFacing step 3
    return m


def head_first_dot(y_rot, x_rot, yaw_fn=render_yaw):
    s = rot(client_matrix(y_rot, x_rot, yaw_fn), [0.0, 0.0, -1.0])
    t = travel_dir(y_rot, x_rot)
    return sum(s[i] * t[i] for i in range(3)), s, t


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)

    P.FRONT = "-Z"
    P._GEO, order = P.load_geo(GEO)
    anim = P.load_anim(ANIM, "dash")
    try:
        P._TEX = Image.open(
            "src/main/resources/assets/tnc/textures/entity/dragon_bedrock.png").convert("RGBA")
    except Exception:
        P._TEX = None

    # ---------- A. geometry ----------
    tfs = P.world_transforms(P._GEO, anim, 0.0)
    snout, tail = [], []
    for bone in ("jaw", "head"):
        for cube in P._GEO[bone].get("cubes", []):
            o, s = cube["origin"], cube["size"]
            for k in (0, 1):
                snout.append(P.apply(tfs[bone], [o[0], o[1], o[2] + s[2] * k]))
    for bone in ("tailFin", "tail5"):
        for cube in P._GEO[bone].get("cubes", []):
            o, s = cube["origin"], cube["size"]
            for k in (0, 1):
                tail.append(P.apply(tfs[bone], [o[0], o[1], o[2] + s[2] * k]))
    print("A. geometry (model units, 16/block; the geo's own front is -Z)")
    print("   head/jaw cubes z: %8.1f .. %8.1f" % (min(p[2] for p in snout),
                                                   max(p[2] for p in snout)))
    print("   tailFin/tail5 z:  %8.1f .. %8.1f" % (min(p[2] for p in tail),
                                                   max(p[2] for p in tail)))
    print("   => the snout sits at local %s"
          % ("-Z" if min(p[2] for p in snout) < 0 else "+Z"))

    # ---------- B. render chain ----------
    print("B. snout vs travel direction (renderYaw = 180 - yRot)")
    dirs = [("north", 0.0, -1.0), ("south", 0.0, 1.0), ("east", 1.0, 0.0), ("west", -1.0, 0.0)]
    rows, fails = [], 0
    for name, dx, dz in dirs:
        y_rot = face_dir_yaw(dx, dz)
        dot, s, t = head_first_dot(y_rot, 0.0)
        fails += 0 if dot > 0.99 else 1
        print("   %-6s yRot=%7.1f  snout=(%5.2f,%5.2f,%5.2f)  travel=(%5.2f,%5.2f,%5.2f)"
              "  dot=%+.3f  %s" % (name, y_rot, s[0], s[1], s[2], t[0], t[1], t[2], dot,
                                   "head-first" if dot > 0.99 else "BACKWARDS"))
        rows.append((name, y_rot, s, dot))
    for x_rot in (-45.0, -20.0, 0.0, 20.0, 45.0):
        dot, _, _ = head_first_dot(face_dir_yaw(0.0, -1.0), x_rot)
        fails += 0 if dot > 0.99 else 1
        print("   pitch %+5.1f (facing north)                        dot=%+.3f" % (x_rot, dot))
    print("   => %s" % ("all head-first" if fails == 0 else "%d FAILING cases" % fails))

    # ---------- C. regression: which formulas would pass? ----------
    print("C. candidate formulas (a wrong-but-plausible one must show up as 3/4 or 2/4)")
    candidates = [
        ("renderYaw = 180 - yRot          (current)", render_yaw),
        ("renderYaw = yRot + 180", lambda y: y + 180.0),
        ("renderYaw = yRot - 180", lambda y: y - 180.0),
        ("renderYaw = yRot                (no fix)", lambda y: y),
        ("renderYaw = 0                   (geckolib only)", lambda y: 0.0),
        ("renderYaw = -yRot", lambda y: -y),
    ]
    for label, fn in candidates:
        passed = 0
        for _name, dx, dz in dirs:
            if head_first_dot(face_dir_yaw(dx, dz), 0.0, fn)[0] > 0.99:
                passed += 1
        print("   %-46s %d/%d  %s" % (label, passed, len(dirs),
                                       "OK" if passed == len(dirs) else "rejected"))
    if fails:
        print("!! the configured formula is WRONG -- fix TNDragonEntity.renderYaw first")

    # ---------- picture ----------
    size = 300
    sheet = Image.new("RGB", (size * len(rows), size + 30), (26, 26, 34))
    for c, (name, y_rot, snout_dir, dot) in enumerate(rows):
        img = Image.new("RGB", (size, size), (26, 26, 34))
        d = ImageDraw.Draw(img)
        m = client_matrix(y_rot, 0.0)
        tris = []
        for bone in order:
            bm = mmul(m, tfs[bone][0])
            for quad, n, rect in P.cube_faces_cached(bone):
                pts = [[p[0], p[2]] for p in (P.apply((bm, [0, 0, 0]), q) for q in quad)]
                tris.append((pts, P.face_color(P._TEX, rect, (235, 232, 222))))
        sc = size * 1.55 / 400.0
        cx = cy = size / 2.0

        def pr(p):
            return (cx + p[0] * sc, cy + p[1] * sc)

        for pts, col in tris:
            d.polygon([pr(p) for p in pts], fill=col, outline=(40, 38, 44))
        a = pr([0.0, 0.0])
        b = pr([snout_dir[0] * 70.0, snout_dir[2] * 70.0])
        d.line([a, b], fill=(60, 230, 90), width=4)
        d.text((b[0] + 3, b[1] - 6), "face", fill=(60, 230, 90))
        d.rectangle([0, 0, size - 1, 14], fill=(12, 12, 16))
        d.text((3, 2), "%s yRot=%.0f dot=%+.2f" % (name, y_rot, dot), fill=(230, 230, 240))
        sheet.paste(img, (c * size, 0))
    d = ImageDraw.Draw(sheet)
    d.text((6, size + 4),
           "top view, dragon drawn through the real client matrix chain; "
           "green arrow = where the snout points", fill=(230, 230, 240))
    out = os.path.join("docs", "previews", "dragon_charge_facing.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("preview ->", out)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
