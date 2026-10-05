# -*- coding: utf-8 -*-
"""
gen_dark_fog_model.py -- the "BLACK FOG" cloud dome + the dark boundary circle.

Author 2026-10-09: the black fog chain (black_mist -> night_grace -> dark_city ->
where_light_cannot_reach -> devour_light) looked like campfire smoke, because all
five spells only spawned `minecraft:smoke` from the cloud's centre. This script
makes the two things that give the fog a BODY:

    1. src/main/resources/assets/tnc/models/projectile/dark_fog.json
       a lumpy dark dome, drawn as a solar-system of jittered cubes. It is attached
       to the cloud through the engine's `client_data.model` slot -- the same slot
       SpellCloudRenderer feeds into CustomModels.render, which is the exact path the
       projectiles already use, so it bakes through TNProjectileModels like the rest.

    2. src/main/resources/assets/tnc/textures/entity/dark_circle.png   (128x128)
       the boundary circle drawn flat on the ground at the fog's radius, so the
       player can SEE where the area ends. Picked up by TNMagicCircleEntity
       style 2 (STYLE_DARK).

    3. docs/previews/dark_fog_preview.png   what the dome looks like (rendered here)
       docs/previews/dark_circle_preview.png the boundary circle texture

GEOMETRY CONTRACT (the part that is easy to get wrong)
    CustomModels.render() translates the pose by (-0.5, -0.5, -0.5) before drawing,
    i.e. it maps model unit-space (0..16 per block) so that the model's (8,8,8) corner
    sits ON the entity position. The engine then applies `scale` from the spell JSON.

    So: keep the model's bounding box CENTRED on x = 8 and z = 8, with its BOTTOM at
    y = 8 units. Then `scale` = the fog radius in blocks (a 15-unit half-width times
    the scale gives a radius of 15/16 * scale = scale blocks).

    The dome is therefore built in x,z in 8 +- 15 and y in 8 .. 8 + 9.
    Do NOT recenter this model like the projectile models -- a ground dome must sit
    ON the ground, not float centred on the cloud's origin.

TEXTURE
    64x64 atlas, drawn procedurally: near-black violet soot with faint ember specks
    on the inner ring. Every face of every cube samples a 4x4 whole-pixel patch, so
    the fog reads as one material instead of a patchwork.

ASCII only (project rule: tooling stays ASCII, text data lives in json).
"""
import json
import math
import os
import random
import sys

from PIL import Image

MODID = "tnc"
MODEL_NAME = "dark_fog"

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                          "models", "projectile", MODEL_NAME + ".json")
CIRCLE_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                           "textures", "entity", "dark_circle.png")
PREVIEW_PATH = os.path.join(REPO, "docs", "previews", "dark_fog_preview.png")
CIRCLE_PREVIEW_PATH = os.path.join(REPO, "docs", "previews", "dark_circle_preview.png")

TEX = 64          # atlas size
PATCH = 4         # every face samples a 4x4 whole-pixel patch of this atlas
FLOOR = 8.0       # y of the model's bottom, in units (see GEOMETRY CONTRACT)
CENTER = 8.0      # x/z centre, in units (CustomModels shifts the pose by -0.5 blocks)
RING = 15.0       # horizontal half-extent in units (scale => radius in blocks)

SEED = 20261009

# name -> (x, y) top-left of a PATCH x PATCH patch in the atlas
PATCHES = {
    "fog_base": (0, 0),      # the bulk of the dome
    "fog_dark": (8, 0),      # shadow side / underside
    "fog_mid": (16, 0),      # mid ring
    "fog_edge": (24, 0),     # ragged outer wisps
    "fog_top": (32, 0),      # the cap
    "ember": (40, 0),        # inner ring specks
}


def patch_uv(mat, w=4, h=4):
    px, py = PATCHES[mat]
    assert px + w <= TEX and py + h <= TEX, "patch out of atlas: %s" % mat
    return px, py, w, h


def box(frm, to, mat):
    """One cube whose six faces all sample the same PATCH x PATCH whole-pixel patch.

    Whole pixels matter: a fractional UV on a 64x64 atlas that is scaled up to a
    9-block dome is what turns a solid cloud into a striped one.
    """
    x0, y0, z0 = [round(v, 2) for v in frm]
    x1, y1, z1 = [round(v, 2) for v in to]
    assert x1 > x0 and y1 > y0 and z1 > z0, "box needs positive size: %r -> %r" % (frm, to)
    ax, ay, w, h = patch_uv(mat)

    def face():
        return {"uv": [ax, ay, ax + w, ay + h], "texture": "#0"}

    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {"north": face(), "south": face(), "east": face(),
                  "west": face(), "up": face(), "down": face()},
    }


def build_boxes():
    """A squashed, ragged dome sitting on y = FLOOR.

    Three concentric rings of vertical pillars (outer / mid / inner) plus a cap. The
    pillar heights fall off with radius so the silhouette is a hill, and every pillar
    is jittered in position, thickness and height so the outline is lumpy -- a
    perfectly smooth hemisphere reads as a glass bowl, not as fog.
    """
    rng = random.Random(SEED)
    boxes = []

    #        radius_units, count, height_units, thickness, material
    rings = [
        (RING - 1.0, 26, 7.5, "fog_edge"),      # outer wall, tallest and thinnest
        (RING - 6.5, 20, 8.5, "fog_base"),      # body of the fog
        (RING - 11.0, 13, 7.0, "fog_mid"),      # inner mass
        (RING - 13.5, 7, 5.0, "fog_top"),       # crown
    ]
    for (radius, count, height, mat) in rings:
        for i in range(count):
            ang = (i / float(count)) * math.tau + rng.uniform(-0.09, 0.09)
            r = radius + rng.uniform(-1.5, 1.5)
            cx = CENTER + math.cos(ang) * r
            cz = CENTER + math.sin(ang) * r
            wide = rng.uniform(2.2, 3.8)                 # pillar footprint
            thick = rng.uniform(2.0, 3.4)
            tall = height * rng.uniform(0.70, 1.25)
            y0 = FLOOR - rng.uniform(0.0, 1.2)           # roots sink into the ground
            y1 = FLOOR + tall
            boxes.append(box([cx - wide, y0, cz - thick], [cx + wide, y1, cz + thick], mat))

    # the cap: a few flat slabs closing the top so you cannot see down into a hollow
    for i in range(9):
        ang = (i / 9.0) * math.tau + rng.uniform(-0.2, 0.2)
        r = rng.uniform(0.0, 3.0)
        cx = CENTER + math.cos(ang) * r
        cz = CENTER + math.sin(ang) * r
        wide = rng.uniform(2.5, 4.0)
        y0 = FLOOR + rng.uniform(6.0, 8.0)
        boxes.append(box([cx - wide, y0, cz - wide], [cx + wide, y0 + rng.uniform(1.5, 3.0), cz + wide],
                         "fog_top"))

    # embers: a handful of small bright cubes low and inside, so the fog is not a
    # featureless black lump when the player walks into it (drawn at full brightness
    # by the engine's emissive layer)
    for i in range(10):
        ang = (i / 10.0) * math.tau + rng.uniform(-0.3, 0.3)
        r = rng.uniform(2.0, 8.0)
        cx = CENTER + math.cos(ang) * r
        cz = CENTER + math.sin(ang) * r
        y0 = FLOOR + rng.uniform(0.6, 4.0)
        boxes.append(box([cx - 1.0, y0, cz - 1.0], [cx + 1.0, y0 + 1.0, cz + 1.0], "ember"))

    return boxes


def to_model(boxes):
    return {
        "format_version": "1.9.0",
        "credit": "Generated by tools/gen_dark_fog_model.py",
        "textures": {
            "0": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
            "particle": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
        },
        "elements": boxes,
        "display": {},
    }


# ----------------------------------------------------------------------------
#  texture: a 64x64 soot atlas (the dome) -- faint, so it multiplies into shadow
# ----------------------------------------------------------------------------
def build_texture():
    tex = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = tex.load()
    rng = random.Random(SEED + 7)

    shades = {
        "fog_base": (30, 22, 44),
        "fog_dark": (18, 13, 28),
        "fog_mid": (38, 27, 56),
        "fog_edge": (24, 17, 36),
        "fog_top": (34, 25, 50),
        "ember": (196, 74, 214),        # magenta ember, deliberately loud
    }

    for mat, (mx, my) in PATCHES.items():
        base = shades[mat]
        for y in range(my, my + PATCH):
            for x in range(mx, mx + PATCH):
                n = rng.randint(-6, 6)
                px[x, y] = (max(0, base[0] + n), max(0, base[1] + n),
                            max(0, base[2] + n), 255)
        # give embers a hot core so the 4x4 patch does not read as a flat square
        if mat == "ember":
            px[mx + 1, my + 1] = (255, 190, 255, 255)
            px[mx + 2, my + 1] = (255, 160, 240, 255)
            px[mx + 1, my + 2] = (255, 160, 240, 255)

    # unused area: leave transparent, so a mis-UVed face shows up as a hole
    return tex


# ----------------------------------------------------------------------------
#  texture: the boundary circle drawn flat on the ground (128x128, straight alpha)
# ----------------------------------------------------------------------------
def build_circle():
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(SEED + 11)
    c = (size - 1) / 2.0
    outer = size / 2.0 - 1.0

    def ring(r, half_width, colour, alpha=255, dotted=0):
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - c, y - c)
                if abs(d - r) <= half_width:
                    if dotted:
                        ang = math.atan2(y - c, x - c)
                        if int(((ang + math.pi) / math.tau) * dotted) % 2:
                            continue
                    px[x, y] = (colour[0], colour[1], colour[2], alpha)

    # the rim: the actual "you are leaving the fog" line -- bright, unbroken
    ring(outer - 1.5, 1.1, (198, 96, 226))
    # a thicker, dimmer band just inside it
    ring(outer - 5.0, 2.0, (96, 42, 124), 150)
    # inner sigil rings, sparser
    ring(outer * 0.62, 0.9, (120, 56, 150), 170)
    ring(outer * 0.34, 0.9, (86, 40, 112), 140)

    # rune ticks around the rim
    for i in range(24):
        ang = (i / 24.0) * math.tau
        r0 = outer - 9.0
        r1 = outer - 2.6 if i % 3 == 0 else outer - 5.5
        steps = int(abs(r1 - r0) * 2) + 1
        for s in range(steps):
            t = s / float(max(1, steps - 1))
            r = r0 + (r1 - r0) * t
            x = int(round(c + math.cos(ang) * r))
            y = int(round(c + math.sin(ang) * r))
            if 0 <= x < size and 0 <= y < size:
                px[x, y] = (214, 118, 240, 220)

    # a soft speckle so the disc is not perfectly clean
    for _ in range(420):
        ang = rng.uniform(0, math.tau)
        r = rng.uniform(0, outer - 3.0)
        x = int(round(c + math.cos(ang) * r))
        y = int(round(c + math.sin(ang) * r))
        if 0 <= x < size and 0 <= y < size:
            px[x, y] = (140, 70, 170, rng.randint(40, 120))
    return img


# ----------------------------------------------------------------------------
#  preview (an isometric painter; same idea as gen_dark_hand_model.py)
# ----------------------------------------------------------------------------
FACES = {
    "north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
    "south": ([(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)], (0, 0, 1)),
    "west": ([(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)], (-1, 0, 0)),
    "east": ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
    "up": ([(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], (0, 1, 0)),
    "down": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1, 0)),
}
LIGHT = {"north": 0.80, "south": 1.05, "west": 0.85, "east": 0.62, "up": 1.25, "down": 0.42}


def shade(col, k):
    return (min(255, int(col[0] * k)), min(255, int(col[1] * k)), min(255, int(col[2] * k)))


def project_pt(p, size):
    return (size / 2.0 + p[0], size / 2.0 - p[1])


def quad_uv(pts, sp):
    p0, p1, p2, p3 = pts
    u, v = 0.5, 0.5
    ex, ey = p1[0] - p0[0], p1[1] - p0[1]
    fx, fy = p3[0] - p0[0], p3[1] - p0[1]
    gx, gy = p0[0] - p1[0] + p2[0] - p3[0], p0[1] - p1[1] + p2[1] - p3[1]
    for _ in range(6):
        dx = p0[0] + ex * u + fx * v + gx * u * v - sp[0]
        dy = p0[1] + ey * u + fy * v + gy * u * v - sp[1]
        if abs(dx) < 0.05 and abs(dy) < 0.05:
            break
        a11, a12 = ex + gx * v, fx + gx * u
        a21, a22 = ey + gy * v, fy + gy * u
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


def render_view(elements, tex, yaw, pitch, size, zoom):
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
                continue
            pts = [project((f[0] + (t[0] - f[0]) * ix,
                            f[1] + (t[1] - f[1]) * iy,
                            f[2] + (t[2] - f[2]) * iz)) for (ix, iy, iz) in idx]
            depth = sum(p[2] for p in pts) / 4.0
            quads.append((depth, [project_pt(p, size) for p in pts], key, el["faces"][key]["uv"]))
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


def build_preview(elements, tex):
    size = 300
    views = [("3/4", 40, -26), ("front", 0, -14), ("top", 0, -80)]
    board = Image.new("RGBA", (size * len(views), size), (12, 10, 18, 255))
    from PIL import ImageDraw
    for i, (label, yaw, pitch) in enumerate(views):
        board.paste(render_view(elements, tex, yaw, pitch, size, 7.5), (i * size, 0))
        ImageDraw.Draw(board).text((i * size + 6, 6), label, fill=(190, 170, 220, 255))
    return board


def build_circle_preview(circle):
    """The circle texture over a checkerboard, so the alpha is visible."""
    size = 384
    board = Image.new("RGBA", (size, size), (26, 22, 32, 255))
    bp = board.load()
    s = 24
    for y in range(size):
        for x in range(size):
            if ((x // s) + (y // s)) % 2 == 0:
                bp[x, y] = (44, 38, 54, 255)
    big = circle.resize((size, size), Image.NEAREST)
    board.alpha_composite(big)
    return board


# ----------------------------------------------------------------------------
def main():
    boxes = build_boxes()
    model = to_model(boxes)
    tex = build_texture()
    circle = build_circle()

    # sanity 1: every UV inside the atlas and not degenerate
    for el in model["elements"]:
        for key, face in el["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert 0 <= u0 <= u1 <= TEX and 0 <= v0 <= v1 <= TEX, "UV out of atlas: %s" % face["uv"]
            assert u1 > u0 and v1 > v0, "degenerate UV: %s" % face["uv"]

    # sanity 2: every cube has three positive axes (the vanilla loader DROPS a
    # zero-size cube silently -- that bug already cost us a claw tip once)
    for el in model["elements"]:
        for i in range(3):
            assert el["to"][i] > el["from"][i], "zero-size cube: %r" % el

    xs0 = min(e["from"][0] for e in model["elements"])
    xs1 = max(e["to"][0] for e in model["elements"])
    ys0 = min(e["from"][1] for e in model["elements"])
    ys1 = max(e["to"][1] for e in model["elements"])
    zs0 = min(e["from"][2] for e in model["elements"])
    zs1 = max(e["to"][2] for e in model["elements"])

    # sanity 3: the geometry contract -- centred on x/z, bottom exactly at FLOOR
    assert abs((xs0 + xs1) / 2.0 - CENTER) < 1.6, "dome is not centred on x: %.2f" % ((xs0 + xs1) / 2.0)
    assert abs((zs0 + zs1) / 2.0 - CENTER) < 1.6, "dome is not centred on z: %.2f" % ((zs0 + zs1) / 2.0)
    assert abs(ys0 - FLOOR) < 1.4, "dome bottom must sit on y=%d: %.2f" % (FLOOR, ys0)

    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(CIRCLE_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW_PATH), exist_ok=True)

    with open(MODEL_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")
    tex.save(os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                          "textures", "spell_projectile", MODEL_NAME + ".png"))
    circle.save(CIRCLE_PATH)
    build_preview(model["elements"], tex).save(PREVIEW_PATH)
    build_circle_preview(circle).save(CIRCLE_PREVIEW_PATH)

    print("wrote %s" % os.path.relpath(MODEL_PATH, REPO))
    print("wrote %s" % os.path.relpath(CIRCLE_PATH, REPO))
    print("wrote %s" % os.path.relpath(PREVIEW_PATH, REPO))
    print("wrote %s" % os.path.relpath(CIRCLE_PREVIEW_PATH, REPO))
    print("elements : %d" % len(model["elements"]))
    print("bbox     : x %d..%d   y %d..%d   z %d..%d  (units, 16 = 1 block)"
          % (xs0, xs1, ys0, ys1, zs0, zs1))
    print("bbox blk : %.2f x %.2f x %.2f blocks"
          % ((xs1 - xs0) / 16.0, (ys1 - ys0) / 16.0, (zs1 - zs0) / 16.0))
    print("engine   : scale = fog radius in blocks  (model half-width is %.2f blocks)"
          % ((xs1 - xs0) / 32.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
