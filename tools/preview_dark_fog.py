# -*- coding: utf-8 -*-
"""
preview_dark_fog.py -- a "what it is supposed to look like" board for the black fog.

This is NOT a game screenshot and it cannot be one: the author's machine is the only
place the game runs. It is a CPU mock-up built from the SAME files that ship:

    * the dome geometry  : assets/tnc/models/projectile/dark_fog.json
    * the dome atlas     : assets/tnc/textures/spell_projectile/dark_fog.png
    * the boundary circle: assets/tnc/textures/entity/dark_circle.png
    * the particle puffs : the actual particle textures pulled out of the pack's
                           block_factorys_bosses jar (ink_fog / fog_1)

Layout: for each tier (radius 4.5 .. 9) ...
    left  = BEFORE: one batch of grey vanilla `smoke` from the centre (what shipped)
    right = AFTER : ground pool + body haze + rising soul wisps + embers + dome + circle

So it answers exactly two questions: "is it dark and low and layered now?" and
"is the area readable?" -- geometry and density, not shader accuracy.

ASCII only. Usage: python tools/preview_dark_fog.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc")
CACHE = os.path.join(REPO, ".dsh-tmp", "fogtex")
OUT = os.path.join(REPO, "docs", "previews", "dark_fog_preview_board.png")

CELL = 360
LABEL = 26
SEED = 20261009

# pack particle textures, extracted from the mod jars (see the sibling script
# docs/previews/dark_fog_particle_candidates.png for the full candidate list)
INK_TEX = os.path.join(CACHE, "bff_ink_fog.png")
SOFT_TEX = os.path.join(CACHE, "bff_soft_smoke.png")


def load(path, fallback=None):
    try:
        return Image.open(path).convert("RGBA")
    except Exception:
        return fallback


def tint(img, rgba):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (rgba[0], rgba[1], rgba[2], int(a * rgba[3] / 255.0))
    return out


def puff(canvas, tex, cx, cy, size, alpha_mul, tint_rgb, rng):
    t = tint(tex, (tint_rgb[0], tint_rgb[1], tint_rgb[2], int(255 * alpha_mul)))
    t = t.rotate(rng.uniform(0, 360), resample=Image.BICUBIC, expand=True)
    s = max(4, int(size))
    t = t.resize((s, s), Image.BILINEAR)
    canvas.alpha_composite(t, (int(cx - s / 2), int(cy - s / 2)))


# ---------------------------------------------------------------------------
#  the dome, drawn from the shipped model with a simple isometric painter
# ---------------------------------------------------------------------------
FACES = {
    "south": ([(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)], (0, 0, 1)),
    "east": ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
    "up": ([(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], (0, 1, 0)),
    "north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
    "west": ([(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)], (-1, 0, 0)),
}
LIGHT = {"north": 0.75, "south": 0.95, "west": 0.80, "east": 0.55, "up": 1.15}


def draw_dome_fast(canvas, model, texture, cx, base_y, scale, yaw=42.0, pitch=20.0):
    """Isometric painter over the shipped model, tinted from the real atlas.

    `scale` = pixels per MODEL UNIT (16 units = 1 block in the engine, where the
    spell's `scale` multiplies the block size -- so pixels_per_unit =
    spell_scale * px_per_block).

    Each cube samples the 4x4 patch its faces point at (that is what the model does),
    averaged to one representative colour -- enough to show the shape, the ember
    cubes and the lumpy silhouette.
    """
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    tex_px = texture.load()

    def project(x, y, z):
        x, y, z = (x - 8.0) * scale, (y - 8.0) * scale, (z - 8.0) * scale
        x, z = x * cy_ - z * sy_, x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return (cx + x, base_y - y)

    def patch_colour(el):
        u0, v0, u1, v1 = el["faces"]["up"]["uv"]
        r = g = b = n = 0
        for yy in range(int(v0), int(v1)):
            for xx in range(int(u0), int(u1)):
                if 0 <= xx < texture.width and 0 <= yy < texture.height:
                    pr, pg, pb, pa = tex_px[xx, yy]
                    if pa:
                        r += pr
                        g += pg
                        b += pb
                        n += 1
        if not n:
            return (30, 22, 44, 235)
        return (r // n, g // n, b // n, 235)

    quads = []
    for el in model["elements"]:
        f, t = el["from"], el["to"]
        col = patch_colour(el)
        for key, (idx, normal) in FACES.items():
            nz = normal[0] * sy_ + normal[2] * cy_
            if nz <= 0.0001:
                continue
            pts = [project(f[0] + (t[0] - f[0]) * ax,
                           f[1] + (t[1] - f[1]) * ay,
                           f[2] + (t[2] - f[2]) * az) for (ax, ay, az) in idx]
            quads.append((sum(p[1] for p in pts) / 4.0, pts, key, col))
    quads.sort(key=lambda q: q[0])

    d = ImageDraw.Draw(canvas, "RGBA")
    for depth, pts, key, col in quads:
        k = LIGHT.get(key, 0.8)
        d.polygon(pts, fill=(int(col[0] * k), int(col[1] * k), int(col[2] * k), col[3]))
    return canvas


def circle_overlay(canvas, texture, cx, cy, radius_px, squash=0.42):
    t = texture.resize((int(radius_px * 2), int(radius_px * 2)), Image.BILINEAR)
    t = t.resize((int(radius_px * 2), max(6, int(radius_px * 2 * squash))), Image.BILINEAR)
    canvas.alpha_composite(t, (int(cx - radius_px), int(cy - radius_px * squash)))


def scene(radius, before, model, dome_tex, ink, soft, circle_tex):
    """One cell: the same field, drawn the OLD way (top row) and the NEW way (bottom)."""
    rng = random.Random(SEED + int(radius * 10))
    cell = Image.new("RGBA", (CELL, CELL), (16, 14, 22, 255))
    d = ImageDraw.Draw(cell)
    # ground
    d.rectangle([0, int(CELL * 0.62), CELL, CELL], fill=(22, 20, 28, 255))
    cx = CELL / 2.0
    # ONE fixed view scale for every tier, so the rows are comparable at a glance.
    # 11 px/block keeps the whole t5 field (18 blocks across) inside a 360 px cell.
    px_per_block = 7.0
    squash = 0.42                      # ground plane foreshortening
    horizon = CELL * 0.56

    def ground(r_blocks, rng_):
        """A point on the ground disc, in pixels."""
        a = rng_.uniform(0, math.tau)
        r = math.sqrt(rng_.random()) * r_blocks * px_per_block
        return cx + math.cos(a) * r, horizon + math.sin(a) * r * squash

    if before:
        # ---- BEFORE: vanilla smoke, one sphere batch from the centre ----
        # (drawn as flat grey blobs -- the change is the point, not the exact particle)
        for _ in range(26):
            x, y = ground(radius * 0.9, rng)
            puff(cell, soft, x, y - rng.uniform(0, 26), rng.uniform(26, 54), 0.34,
                 (176, 176, 180), rng)
        d.text((8, 8), "BEFORE  smoke x35, one SPHERE batch", fill=(214, 194, 194, 255))
    else:
        # ---- AFTER: boundary circle, ground pool, dome, haze, wisps, embers ----
        circle_overlay(cell, circle_tex, cx, horizon, radius * px_per_block)
        # 1) ground pool: wide, low, dark
        for _ in range(int(80 + 16 * radius)):
            x, y = ground(radius, rng)
            puff(cell, ink, x, y - rng.uniform(0, 10), rng.uniform(24, 52), 0.55,
                 (58, 42, 84), rng)
        # 2) the dome. The engine applies `scale` to the block model, and the model
        #    is 2.25 blocks across at scale 1, so the shipped scale (0.4 * radius)
        #    gives a dome 0.9 * radius blocks wide; its vertical MIDDLE sits on the
        #    cloud position, which is why the base is lifted by half its height here.
        dome_px_per_unit = (0.4 * radius) * px_per_block
        dome_half_height = (0.73 / 2.0) * (0.4 * radius) * px_per_block
        draw_dome_fast(cell, model, dome_tex, cx, horizon + dome_half_height, dome_px_per_unit)
        # 3) body haze that softens the cube edges
        for _ in range(int(26 + 6 * radius)):
            x, y = ground(radius, rng)
            puff(cell, soft, x, y - rng.uniform(0, radius * px_per_block * 0.3),
                 rng.uniform(30, 64), 0.22, (96, 76, 128), rng)
        # 4) rising wisps
        for _ in range(int(4 + radius)):
            x, y = ground(radius * 0.8, rng)
            puff(cell, soft, x, y - rng.uniform(6, radius * px_per_block * 0.34),
                 rng.uniform(16, 30), 0.5, (150, 220, 235), rng)
        # 5) embers
        for _ in range(int(3 + radius * 0.6)):
            x, y = ground(radius * 0.9, rng)
            puff(cell, ink, x, y - rng.uniform(2, 24), rng.uniform(6, 12), 0.95,
                 (236, 120, 240), rng)
        d.text((8, 8), "AFTER   circle+pool+dome+haze+wisp+embers", fill=(226, 206, 255, 255))

    d.text((8, CELL - 18), "radius %.1f blocks" % radius, fill=(180, 176, 196, 255))
    return cell


def main():
    model = json.loads(open(os.path.join(ASSETS, "models", "projectile", "dark_fog.json"),
                            encoding="utf-8").read())
    dome_tex = load(os.path.join(ASSETS, "textures", "spell_projectile", "dark_fog.png"))
    circle_tex = load(os.path.join(ASSETS, "textures", "entity", "dark_circle.png"))
    ink = load(INK_TEX)
    soft = load(SOFT_TEX)
    if ink is None or soft is None:
        print("WARN: pack particle textures not in .dsh-tmp/fogtex -- using flat discs")
        ink = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        ImageDraw.Draw(ink).ellipse([2, 6, 30, 26], fill=(40, 28, 60, 210))
        soft = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        ImageDraw.Draw(soft).ellipse([2, 8, 30, 24], fill=(120, 120, 128, 190))

    radii = [4.5, 5.0, 6.0, 7.5, 9.0]
    board = Image.new("RGBA", (CELL * len(radii), CELL * 2 + 8), (12, 10, 16, 255))
    for i, r in enumerate(radii):
        board.paste(scene(r, True, model, dome_tex, ink, soft, circle_tex), (i * CELL, 0))
        board.paste(scene(r, False, model, dome_tex, ink, soft, circle_tex), (i * CELL, CELL + 8))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    board.save(OUT)
    print("wrote %s (%dx%d)" % (os.path.relpath(OUT, REPO), board.width, board.height))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
