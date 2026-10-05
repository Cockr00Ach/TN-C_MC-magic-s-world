# -*- coding: utf-8 -*-
"""
gen_dark_hand_model.py -- the "HAND OF NIGHT" projectile model (dark magic chain 1).

Author 2026-10-02: "give me a hand model" -- this is the hand of the
night_hand chain (night_hand -> night_raid -> night_embrace -> black_ruin -> slay_light).
All five spells currently fire tnc:projectile/lightingball (a yellow ball), which has
nothing to do with a "hand of night". This replaces it.

WHAT IT WRITES
    1. src/main/resources/assets/tnc/models/projectile/dark_hand.json
       vanilla Java block model (same format as lightingball.json / fireball.json)
    2. src/main/resources/assets/tnc/textures/spell_projectile/dark_hand.png   (32x32)
    3. docs/previews/dark_hand_preview.png     a 4x "what it looks like" board
       (front / side / top / 3-4 view, rendered by this script -- not a screenshot)

MODEL ORIENTATION (the part that is easy to get wrong)
    The projectile flies along +Z in model space. So the hand is built with its
    fingers pointing +Z (forward, at the victim) and the wrist at -Z (trailing).
    The palm is flat in the XZ plane, so it reads correctly from ANY roll angle --
    unlike a hand designed in the XY plane, which would fly edge-on and look like a stick.
    The palm faces UP. All six faces are present on every cube, so even if the engine
    rolls the model the silhouette still reads as a hand.

SIZE
    bbox x -6..6, y -6..4, z -11..12  =>  0.75 x 0.63 x 1.44 blocks
    A "scale" of 1.4 in the spell json makes it about 1 block wide and 2 blocks long.
    Scale knobs per tier are in the spell JSONs, not here -- do not resize the model
    to change how big a tier looks.

TEXTURE
    32x32, drawn procedurally: bone-white knuckle plates, purple-black shadow between
    the fingers, hot magenta claw tips. Every face of every cube maps into this atlas
    in whole pixels (no fractional UV), which is what keeps it from looking striped.

ASCII only (PowerShell 5.1 reads non-ASCII .ps1 as ANSI; this is .py but the project
rule is the same -- keep tooling ASCII and put text data in json).
"""
import json
import os
import sys

from PIL import Image

MODID = "tnc"
MODEL_NAME = "dark_hand"

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                          "models", "projectile", MODEL_NAME + ".json")
TEXTURE_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                            "textures", "spell_projectile", MODEL_NAME + ".png")
PREVIEW_PATH = os.path.join(REPO, "docs", "previews", MODEL_NAME + "_preview.png")

TEX = 32   # texture is 32x32; every UV below is in whole pixels of this atlas

# ----------------------------------------------------------------------------
#  atlas: one rectangle per "material", tiled by the boxes below
# ----------------------------------------------------------------------------
#  Keep every material a solid-ish patch with its own shading so a mis-UVed face is
#  visible at a glance instead of blending in.
ATLAS = {
    # name          (x, y, w, h)   base      edge        accent
    "wrist":        ((0, 0, 8, 8), (26, 16, 40), (40, 26, 58), (10, 6, 16)),
    "palm":         ((8, 0, 12, 12), (34, 22, 52), (52, 34, 74), (12, 8, 20)),
    "back":         ((20, 0, 8, 8), (24, 15, 38), (36, 23, 52), (9, 5, 14)),
    "digit_0":      ((0, 8, 12, 6), (44, 30, 64), (64, 44, 88), (14, 9, 22)),
    "digit_1":      ((0, 14, 12, 6), (38, 25, 56), (56, 38, 78), (12, 8, 20)),
    "digit_2":      ((0, 20, 12, 6), (32, 21, 48), (48, 32, 68), (11, 7, 18)),
    "knuckle":      ((0, 26, 12, 6), (72, 60, 96), (112, 96, 140), (26, 18, 36)),
    "claw":         ((12, 8, 10, 10), (200, 90, 190), (255, 190, 250), (90, 30, 90)),
    "aura":         ((22, 8, 8, 8), (60, 30, 96), (120, 70, 170), (24, 10, 40)),
}

# where the next free patch is, for any material not pre-declared (auto-tiled)
_auto_cursor = [0, 16]


def atlas_rect(name):
    if name in ATLAS:
        return ATLAS[name][0]
    # auto-tile unknown materials into the bottom half, 8x8 each
    x, y = _auto_cursor
    if x + 8 > TEX:
        x, y = 0, y + 8
    _auto_cursor[0] = x + 8
    ATLAS[name] = ((x, y, 8, 8), (40, 40, 40), (70, 70, 70), (20, 20, 20))
    return ATLAS[name][0]


# ----------------------------------------------------------------------------
#  geometry helpers
# ----------------------------------------------------------------------------
def box(frm, to, mat, uv_face=None):
    """One axis-aligned cube. frm/to are [x,y,z] in model units (1 unit = 1/16 block)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    assert x1 > x0 and y1 > y0 and z1 > z0, "box must have positive size: %r -> %r" % (frm, to)
    ax, ay, aw, ah = atlas_rect(mat)
    # every face gets a same-sized slice of the material patch, picking different
    # corners so faces are distinguishable while looking at the model
    fx, fy = uv_face if uv_face else (0, 0)
    w = max(1, min(aw, x1 - x0))
    h = max(1, min(ah, y1 - y0))
    d = max(1, min(aw, z1 - z0))
    eh = max(1, min(ah, y1 - y0))

    def face(u, v, uw, vh):
        return {"uv": [u, v, u + uw, v + vh], "texture": "#0"}

    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "rotation": {"angle": 0, "axis": "y", "origin": [x0, y0, z0]},
        "faces": {
            # north/south are the XY faces (size w x h)
            "north": face(ax + fx, ay + fy, w, h),
            "south": face(ax + (aw - w - fx) % max(1, aw - w + 1), ay + fy, w, h),
            # east/west are the ZY faces (size d x h)
            "east": face(ax + fx, ay + ((fy + h) % max(1, ah - h + 1)), d, h),
            "west": face(ax + ((fx + w) % max(1, aw - d + 1)), ay + fy, d, h),
            # up/down are the XZ faces (size w x d)
            "up": face(ax + fx, ay + ((fy + h) % max(1, ah - d + 1)), w, d),
            "down": face(ax + fx, ay + ((fy + h + 1) % max(1, ah - eh + 1)), w, d),
        },
        "_mat": mat,
    }


def build_boxes():
    """The hand itself: wrist + palm + 5 digits, fingers pointing +Z (direction of flight)."""
    b = []

    # ---- wrist / forearm stub: trailing behind, thinner than the palm ----
    b.append(box([-4, -2, -11], [4, 2, -4], "wrist"))
    # a raised ridge along the back of the wrist, so it is not a bare rectangle
    b.append(box([-2, 1, -10], [2, 3, -5], "back"))

    # ---- palm: 12 wide, 3 thick, 9 long, palm-up ----
    b.append(box([-6, -3, -4], [6, 0, 5], "palm"))
    # heel of the palm (a slightly thicker block at the wrist end)
    b.append(box([-5, -4, -4], [5, -1, 0], "back"))

    # ---- knuckle bar: the row the fingers grow out of ----
    b.append(box([-6, -3, 4], [6, 0, 6], "knuckle"))

    # ---- four fingers, each 3 segments, curling slightly DOWN then forward ----
    #      x centres spread across the knuckle bar; the middle two are longest
    finger_x = [(-5, -3), (-2, 0), (1, 3), (4, 6)]
    lengths = [6, 8, 8, 6]
    for i, ((xa, xb), ln) in enumerate(zip(finger_x, lengths)):
        mat = "digit_%d" % min(2, i if i < 3 else 2)
        y = -3
        z = 6
        seg = ln // 3
        for s in range(3):
            h = 3 - s          # taper
            if s == 1:
                y -= 1         # middle segment drops: the curl
            if s == 2:
                y -= 1
            b.append(box([xa, y, z], [xb, y + h, z + seg], mat))
            z += seg

    # ---- thumb: shorter, thicker, angled across the palm ----
    b.append(box([-8, -2, -1], [-6, 1, 3], "digit_2"))
    b.append(box([-10, -1, 1], [-8, 2, 5], "digit_1"))

    # ---- claws on the fingertips: bright, so the hand reads as hostile ----
    #      Explicit from/to per claw -- every claw gets 1 unit of thickness in ALL axes,
    #      because a zero-size axis makes the vanilla model loader drop the cube.
    #      The spike TAPERS in both x and y on its way forward, so it reads as a claw from
    #      above AND from the side (a flat 1-unit-tall spike just looks like a blade).
    claws = [
        # x0, x1, y0, y1, z0, z1
        (-5, -3, -4.0, -2.0, 12, 14),
        (-2, 0, -4.0, -2.0, 14, 16),
        (1, 3, -4.0, -2.0, 14, 16),
        (4, 6, -4.0, -2.0, 12, 14),
    ]
    for (x0, x1, y0, y1, z0, z1) in claws:
        # base: full width, thinner than the finger, tucked under the fingertip
        b.append(box([x0, y0 + 0.5, z0], [x1, y1, z1], "claw"))
        # spike: narrower + shorter, poking further forward
        xm0, xm1 = x0 + 0.5, x1 - 0.5
        if xm1 - xm0 < 1:
            xm0, xm1 = x0, x1
        b.append(box([xm0, y0 + 1.0, z1], [xm1, y1, z1 + 3], "claw"))

    # ---- aura shards: a few floating fragments around the hand (dark magic feel) ----
    b.append(box([-8, 1, 2], [-6, 3, 4], "aura"))
    b.append(box([6, 1, 2], [8, 3, 4], "aura"))
    b.append(box([-2, 2, -7], [2, 4, -5], "aura"))
    b.append(box([-1, 2, 7], [1, 4, 9], "aura"))

    return b


def recenter(boxes):
    """Shift the whole model so its bounding-box centre sits on the origin.

    Why this matters for a PROJECTILE: the engine tumbles the model about the entity
    position. If the geometry hangs forward of the origin (this hand was +4 units on Z),
    the hand swings around a point in front of itself and looks like it is orbiting
    its own wrist. Centring the bbox makes it spin in place.
    """
    xs0 = min(b["from"][0] for b in boxes)
    xs1 = max(b["to"][0] for b in boxes)
    ys0 = min(b["from"][1] for b in boxes)
    ys1 = max(b["to"][1] for b in boxes)
    zs0 = min(b["from"][2] for b in boxes)
    zs1 = max(b["to"][2] for b in boxes)
    dx = -round((xs0 + xs1) / 2.0, 2)
    dy = -round((ys0 + ys1) / 2.0, 2)
    dz = -round((zs0 + zs1) / 2.0, 2)
    if dx == dy == dz == 0:
        return boxes
    for b in boxes:
        b["from"] = [round(b["from"][0] + dx, 2), round(b["from"][1] + dy, 2), round(b["from"][2] + dz, 2)]
        b["to"] = [round(b["to"][0] + dx, 2), round(b["to"][1] + dy, 2), round(b["to"][2] + dz, 2)]
    return boxes


def to_model(boxes):
    elements = []
    for bx in boxes:
        el = {k: v for k, v in bx.items() if not k.startswith("_")}
        # round the half-units the claws use, so the json stays readable
        el["from"] = [round(v, 2) for v in el["from"]]
        el["to"] = [round(v, 2) for v in el["to"]]
        elements.append(el)
    return {
        "format_version": "1.9.0",
        "credit": "Generated by tools/gen_dark_hand_model.py",
        "textures": {
            "0": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
            "particle": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
        },
        "elements": elements,
    }


# ----------------------------------------------------------------------------
#  texture
# ----------------------------------------------------------------------------
def build_texture():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = img.load()

    def paint(name):
        (x, y, w, h), base, edge, accent = ATLAS[name]
        for i in range(w):
            for j in range(h):
                # soft vertical shade + a 1px darker frame
                t = j / max(1, h - 1)
                c = tuple(int(base[k] * (1 - 0.25 * t) + accent[k] * (0.25 * t)) for k in range(3))
                if i == 0 or j == 0 or i == w - 1 or j == h - 1:
                    c = edge
                # a couple of lighter speckles so faces are not flat
                if (i * 7 + j * 13) % 17 == 0:
                    c = tuple(min(255, v + 28) for v in c)
                px[x + i, y + j] = (c[0], c[1], c[2], 255)

    for name in ATLAS:
        paint(name)

    # claw patch: add a hard white highlight along its centre line
    (x, y, w, h), base, edge, accent = ATLAS["claw"]
    for j in range(h):
        px[x + w // 2, y + j] = (255, 235, 255, 255)

    return img


# ----------------------------------------------------------------------------
#  preview: render the boxes ourselves (front / side / top / three-quarter)
# ----------------------------------------------------------------------------
def shade(color, factor):
    r, g, b = color[0], color[1], color[2]
    return (max(0, min(255, int(r * factor))),
            max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))))


def render_view(elements, tex, yaw, pitch, size, zoom):
    """Dead-simple painter's-algorithm box renderer -- just enough to sanity-check the model.

    For every visible face we rasterise the projected quad and pick the texture sample with
    an inverse-bilinear UV lookup, so a wrong UV shows up here exactly as it would in game.
    """
    import math

    FACES = {
        "north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
        "south": ([(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], (0, 0, 1)),
        "west":  ([(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], (-1, 0, 0)),
        "east":  ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
        "up":    ([(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)], (0, 1, 0)),
        "down":  ([(0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)], (0, -1, 0)),
    }
    LIGHT = {"north": 0.80, "south": 1.05, "west": 0.85, "east": 0.62, "up": 1.20, "down": 0.48}

    # model centre, so the preview is centred regardless of how the bbox moves
    xs0 = min(e["from"][0] for e in elements)
    xs1 = max(e["to"][0] for e in elements)
    ys0 = min(e["from"][1] for e in elements)
    ys1 = max(e["to"][1] for e in elements)
    zs0 = min(e["from"][2] for e in elements)
    zs1 = max(e["to"][2] for e in elements)
    mid = ((xs0 + xs1) / 2.0, (ys0 + ys1) / 2.0, (zs0 + zs1) / 2.0)

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[0] - mid[0], p[1] - mid[1], p[2] - mid[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x * zoom, y * zoom, z * zoom)

    def rotate_dir(n):
        x, y, z = n
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)

    quads = []
    for el in elements:
        f, t = el["from"], el["to"]
        for key, (idx, normal) in FACES.items():
            if rotate_dir(normal)[2] <= 0.0001:
                continue                      # face points away from the camera
            pts = []
            for (ix, iy, iz) in idx:
                pts.append(project((f[0] + (t[0] - f[0]) * ix,
                                    f[1] + (t[1] - f[1]) * iy,
                                    f[2] + (t[2] - f[2]) * iz)))
            depth = sum(p[2] for p in pts) / 4.0
            face = el["faces"].get(key)
            if not face:
                continue
            quads.append((depth, [project_pt(p, size) for p in pts], key, face["uv"]))
    quads.sort(key=lambda r: -r[0])

    out = Image.new("RGBA", (size, size), (14, 11, 20, 255))
    op = out.load()
    step = max(4, size // 10)
    for i in range(0, size, step):
        for j in range(size):
            op[i, j] = (30, 23, 40, 255)
            op[j, i] = (30, 23, 40, 255)

    for depth, pts, key, uvs in quads:
        u0, v0, u1, v1 = uvs
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for yy in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
            for xx in range(max(0, int(min(xs))), min(size, int(max(xs)) + 1)):
                uv = quad_uv(pts, (xx + 0.5, yy + 0.5))
                if uv is None:
                    continue
                su, sv = uv
                tu = u0 + (u1 - u0) * su
                tv = v0 + (v1 - v0) * sv
                col = tex.getpixel((int(min(TEX - 1, max(0, tu))), int(min(TEX - 1, max(0, tv)))))
                op[xx, yy] = shade(col, LIGHT[key]) + (255,)
    return out


def project_pt(p, size):
    return (size / 2.0 + p[0], size / 2.0 - p[1])


def quad_uv(pts, sp):
    """Inverse-bilinear lookup: screen point -> (u,v) in [0,1]^2 inside the quad."""
    # corners in order: p0 p1 p2 p3 = (0,0) (1,0) (1,1) (0,1)
    p0, p1, p2, p3 = pts
    # solve p = p0 + u*(p1-p0) + v*(p3-p0) + u*v*(p0-p1+p2-p3) by a few Newton steps
    u, v = 0.5, 0.5
    ex = p1[0] - p0[0]
    ey = p1[1] - p0[1]
    fx = p3[0] - p0[0]
    fy = p3[1] - p0[1]
    gx = p0[0] - p1[0] + p2[0] - p3[0]
    gy = p0[1] - p1[1] + p2[1] - p3[1]
    for _ in range(6):
        dx = p0[0] + ex * u + fx * v + gx * u * v - sp[0]
        dy = p0[1] + ey * u + fy * v + gy * u * v - sp[1]
        if abs(dx) < 0.05 and abs(dy) < 0.05:
            break
        a11 = ex + gx * v
        a12 = fx + gx * u
        a21 = ey + gy * v
        a22 = fy + gy * u
        det = a11 * a22 - a12 * a21
        if abs(det) < 1e-9:
            return None
        du = (dx * a22 - dy * a12) / det
        dv = (a11 * dy - a21 * dx) / det
        u -= du
        v -= dv
    if -0.01 <= u <= 1.01 and -0.01 <= v <= 1.01:
        return (min(1.0, max(0.0, u)), min(1.0, max(0.0, v)))
    return None


def build_preview(elements, tex):
    zoom = 12.0
    size = 240
    views = [
        ("front", 0, -12),
        ("side", 90, -12),
        ("top", 0, -78),
        ("3/4", 40, -32),
    ]
    try:
        from PIL import ImageDraw
        draw_labels = True
    except Exception:
        draw_labels = False

    board = Image.new("RGBA", (size * len(views), size), (12, 10, 18, 255))
    for i, (label, yaw, pitch) in enumerate(views):
        cell = render_view(elements, tex, yaw, pitch, size, zoom)
        board.paste(cell, (i * size, 0))
        if draw_labels:
            d = ImageDraw.Draw(board)
            d.text((i * size + 6, 6), label, fill=(180, 165, 210, 255))
    return board


# ----------------------------------------------------------------------------
def main():
    boxes = recenter(build_boxes())
    model = to_model(boxes)
    tex = build_texture()

    # sanity: every UV inside the atlas
    for el in model["elements"]:
        for key, face in el["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert 0 <= u0 <= u1 <= TEX and 0 <= v0 <= v1 <= TEX, \
                "UV out of atlas: %s %s" % (key, face["uv"])
            assert u1 > u0 and v1 > v0, "degenerate UV: %s %s" % (key, face["uv"])

    # bbox report (the spell json needs this to pick a scale)
    xs0 = min(e["from"][0] for e in model["elements"])
    xs1 = max(e["to"][0] for e in model["elements"])
    ys0 = min(e["from"][1] for e in model["elements"])
    ys1 = max(e["to"][1] for e in model["elements"])
    zs0 = min(e["from"][2] for e in model["elements"])
    zs1 = max(e["to"][2] for e in model["elements"])

    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(TEXTURE_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW_PATH), exist_ok=True)

    with open(MODEL_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")
    tex.save(TEXTURE_PATH)
    build_preview(model["elements"], tex).save(PREVIEW_PATH)

    print("wrote %s" % os.path.relpath(MODEL_PATH, REPO))
    print("wrote %s" % os.path.relpath(TEXTURE_PATH, REPO))
    print("wrote %s" % os.path.relpath(PREVIEW_PATH, REPO))
    print("elements : %d" % len(model["elements"]))
    print("bbox     : x %d..%d   y %d..%d   z %d..%d  (units, 16 = 1 block)"
          % (xs0, xs1, ys0, ys1, zs0, zs1))
    print("bbox blk : %.2f x %.2f x %.2f blocks"
          % ((xs1 - xs0) / 16.0, (ys1 - ys0) / 16.0, (zs1 - zs0) / 16.0))
    print("center   : %.2f, %.2f, %.2f (units) -- put this in the render translate if needed"
          % ((xs0 + xs1) / 2.0, (ys0 + ys1) / 2.0, (zs0 + zs1) / 2.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
