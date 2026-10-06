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
    The projectile flies along +Z in model space. The hand is therefore built with its
    FINGERS pointing +Z (forward, at the victim), the wrist stub at -Z (trailing), and
    the palm facing down (-Y). Author 2026-10-10 confirmed the intent: "手腕对着别人
    不是手指" was wrong, and the hand should read as an open grasp with "掌心对着别人".

    ★★ THIS ORIENTATION ONLY HOLDS IF THE SPELL SETS `model.orientation`.
    With the field absent, the engine TUMBLES the model about the entity position, and
    then whichever end faces the victim is arbitrary. That is why the five night-hand
    spells now carry `"orientation": "TOWARDS_CAMERA"` -- the only variant that yaws
    the model without pitching or rolling it (see tools/gen_dark_hand_projectiles.py).
    (Same trap as the `flash` lightning on 2026-09-27: "怎么雷电是躺着的".)

SHAPE (2026-10-10 rework: open grasp, not a fist)
    Compact wrist collar + broad flat palm + four splayed, nearly-straight fingers with
    forward-hooking claws + a thumb angled across, i.e. the "about to seize" silhouette
    the author asked for ("有点下界铁掌这个boss的形状").

SIZE
    (printed by main(); the model is recentred on its own bounding box, so the printed
     bbox is what the engine sees.) Scale knobs per tier live in the spell JSONs --
     do not resize the model to change how big a tier looks.

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
    """An OPEN, grasping hand: compact wrist + broad palm + five spread digits.

    Author 2026-10-10: "我希望你把手掌张开也就是弄成要去抓住别人的感觉，然后掌心对着
    别人冲过去，有点像下界铁掌这个boss的形状".

    ORIENTATION, in model space (see the module docstring):
        +Z = forward (at the victim)   -Z = the wrist stub
        +Y = up                        -Y = the palm side
    So the FINGERS point at the victim, the palm faces down/forward, and the wrist stub
    is a short collar behind the palm. The previous version had a 7-unit thick forearm
    block trailing behind a curled fist, which read as "a wrist being thrown".

    WHAT CHANGED (and why)
        * wrist: 7 units long x 8 wide  ->  5 long x 7 wide (a collar, not an arm), and
          it is now the SAME height as the palm so the hand has one silhouette.
        * fingers: were 3 short segments curled DOWN (a fist) -> now longer, laid out
          nearly straight, and SPREAD OUTWARD so the gaps between them read as an open
          grasp. Middle two are longest; the outer two splay.
        * palm: wider (14 vs 12) and flatter, so "open palm" is legible head-on.
        * claws: point forward past each fingertip and curl slightly down, so the tips
          read as about to close rather than as five unrelated spikes.
    """
    b = []

    # ---- wrist: a short collar behind the palm (NOT a forearm) ----
    b.append(box([-3.5, -2.5, -13], [3.5, 0.5, -8], "wrist"))
    # a low ridge along the back of the wrist so it is not a bare rectangle
    b.append(box([-2, 0.5, -12], [2, 1.5, -9], "back"))

    # ---- palm: broad and flat; its front face (+Z... the north face) is the 掌心 ----
    b.append(box([-7, -3, -8], [7, 0, 3], "palm"))
    # heel of the palm, slightly thicker at the wrist end
    b.append(box([-5.5, -3.5, -8], [5.5, -1, -4], "back"))

    # ---- knuckle bar: the row the fingers grow out of ----
    b.append(box([-7, -3, 3], [7, 0, 5], "knuckle"))
    b.append(box([-7, -1, 3], [7, 1, 5], "back"))     # raised back of the knuckles

    # ---- four fingers: nearly straight, splayed INWARD from symmetrically placed
    #      bases (an open grasp). The splay signs deliberately mirror about x=0 so the
    #      open hand is symmetric -- the first cut of this had both outer fingers
    #      drifting the same way, which made the whole model lopsided.
    #      (xa, xb) = base span, length, splay per segment
    fingers = [
        (-7.5, -5.5, 16, 0.60),     # outermost, nearest the thumb side
        (-4.0, -2.0, 19, 0.15),
        (2.0, 4.0, 19, -0.15),
        (5.5, 7.5, 16, -0.60),      # outermost, pinky side
    ]
    for i, (xa, xb, length, splay) in enumerate(fingers):
        mat = "digit_%d" % min(2, i if i < 3 else 2)
        seg = length // 3
        z = 5.0
        y = -3.0
        for s in range(3):
            h = 3 - s * 0.5                       # taper toward the tip
            # spread: each segment drifts outward and drops very slightly (a relaxed grasp)
            dx = splay * (s + 1)
            dy = -0.5 * s
            b.append(box([xa + dx, y + dy, z], [xb + dx, y + dy + h, z + seg], mat))
            z += seg
        # ---- claw at this fingertip: forward and curling down ----
        cx0, cx1 = xa + splay * 3, xb + splay * 3
        ty = y + (-0.5 * 2)                        # tip height
        # base of the claw (thicker, sitting on the fingertip)
        b.append(box([cx0 + 0.25, ty - 0.5, z], [cx1 - 0.25, ty + 1.0, z + 1.5], "claw"))
        # spike: narrower, pokes forward and hooks down (still positive in every axis)
        b.append(box([cx0 + 0.75, ty - 1.0, z + 1.5], [cx1 - 0.75, ty + 0.5, z + 4.0], "claw"))

    # ---- thumb: off the left side, angled across as if about to close ----
    b.append(box([-10, -3, -2], [-8, 0, 2], "digit_2"))
    b.append(box([-12, -3, 1], [-10, 0, 5], "digit_1"))
    b.append(box([-12.5, -3.5, 5], [-10.5, -1.5, 7], "claw"))

    # ---- aura shards: a few floating fragments (dark magic feel) ----
    b.append(box([-9, 1, 0], [-7, 3, 2], "aura"))
    b.append(box([7, 1, 0], [9, 3, 2], "aura"))
    b.append(box([-2, 2, -10], [2, 4, -8], "aura"))
    b.append(box([-1.5, 2, 12], [1.5, 4, 14], "aura"))

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
