#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_wind_blade_model.py -- the WIND "blade" projectile model (wind chain 3, the gale /
range-wind chain: gale -> great_gale -> vast_gale -> giant_gale -> wind_god_gale).

Author 2026-10-10: "the wind blade could copy the water chain's wave slash" -- the
wind blade should read as a crescent / moon-blade cutting wave, not the tornado column
that was imported earlier the same day (see HISTORY below).

HISTORY (why the model id stays "windblade")
    The five gale spells used to borrow tnc:projectile/lightingball (a yellow ball). On
    2026-10-10 the author's tornado model was imported as projectile/windblade and wired
    in. The author then asked for a crescent instead, so THIS script replaces the model
    body at the same id -- the spell JSONs and TNProjectileModels do NOT change.

WHAT IT WRITES
    1. src/main/resources/assets/tnc/models/projectile/windblade.json
       vanilla Java block model (same format as lightingball.json / dark_hand.json)
    2. src/main/resources/assets/tnc/textures/spell_projectile/windblade.png   (32x16)
    3. docs/previews/windblade_preview.png    a 6x orthographic board (front + side)

SHAPE
    A crescent built from SEGMENTS overlapping axis-aligned boxes placed along an arc of
    ARC degrees at radius R, thin in Z (the flight axis is +Z, so the crescent is seen
    face-on as it flies at you), tapering to points at both horns:
      * radial half-thickness  -> thick at the crest, ~0 at the horns
      * half-depth in Z        -> same taper, which is what makes it read as a blade
    No element rotations at all (vanilla only accepts +-22.5/+-45) and every UV is on a
    WHOLE 16th, which is what keeps a curved run of boxes from looking striped.

ORIENTATION
    +Z is forward (see the spell JSON: "orientation": "TOWARDS_MOTION"). The engine's
    SpellProjectileRenderer applies ONLY yaw+pitch for that value, so a crescent lying in
    the XY plane stays "vertical, edge forward" instead of tumbling.

SIZE
    Printed by main(). The model is recentred so its bounding box centre sits at
    (8,8,8) units = the origin the engine translates to the entity position.
    Per-tier size lives in the spell JSON `scale` -- do NOT resize the model for that.

ASCII only (project tooling rule).
"""
import json
import math
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "tnc"
NAME = "windblade"

MODEL_PATH = os.path.join(REPO, "src", "main", "resources", "assets", NS,
                          "models", "projectile", NAME + ".json")
TEX_DIR = os.path.join(REPO, "src", "main", "resources", "assets", NS,
                       "textures", "spell_projectile")
TEX_PATH = os.path.join(TEX_DIR, NAME + ".png")
PREVIEW_PATH = os.path.join(REPO, "docs", "previews", NAME + "_preview.png")

TEX_W, TEX_H = 32, 16
SEGMENTS = 16          # 16 -> u step of exactly 1.0 in 0..16 UV space (no fractional UV)
R = 9.0                # arc radius, model units (16 units = 1 block)
ARC = 170.0            # total sweep of the crescent, degrees (wide + shallow = a slash)
CENTER = 8.0           # model centre, units (the engine translates this onto the entity)
LOADER_LO, LOADER_HI = -16.0, 32.0

# arc runs across the LOWER half so the horns point up: 185deg .. 355deg
A0 = 185.0
A1 = 355.0


# ---------------------------------------------------------------- geometry ----
def t_ratio(theta_deg):
    """0 at the horns, 1 at the crest of the arc."""
    mid = (A0 + A1) / 2.0
    half = (A1 - A0) / 2.0
    return max(0.0, 1.0 - abs(theta_deg - mid) / half)


def build_boxes():
    boxes = []
    for i in range(SEGMENTS):
        t0 = A0 + (A1 - A0) * (i / float(SEGMENTS))
        t1 = A0 + (A1 - A0) * ((i + 1) / float(SEGMENTS))
        tm = (t0 + t1) / 2.0
        k = t_ratio(tm)

        half_thick = 0.30 + 1.35 * (k ** 0.75)      # radial half-thickness
        half_deep = 0.28 + 0.95 * (k ** 0.85)       # half-depth along Z

        xs, ys = [], []
        for th in (t0, t1):
            rad = math.radians(th)
            for r in (R - half_thick, R + half_thick):
                xs.append(CENTER + r * math.cos(rad))
                ys.append(CENTER + r * math.sin(rad))

        frm = [min(xs), min(ys), CENTER - half_deep]
        to = [max(xs), max(ys), CENTER + half_deep]

        u0 = 16.0 * i / float(SEGMENTS)
        u1 = 16.0 * (i + 1) / float(SEGMENTS)
        boxes.append((frm, to, u0, u1))
    return boxes


def recenter(boxes):
    lo = [min(b[0][a] for b in boxes) for a in range(3)]
    hi = [max(b[1][a] for b in boxes) for a in range(3)]
    shift = [CENTER - (lo[a] + hi[a]) / 2.0 for a in range(3)]
    out = []
    for frm, to, u0, u1 in boxes:
        out.append(([frm[a] + shift[a] for a in range(3)],
                    [to[a] + shift[a] for a in range(3)], u0, u1))
    return out


def to_model(boxes):
    # v bands: 0..4 is the bright cutting rim, 4..16 the body (see build_texture)
    elements = []
    for frm, to, u0, u1 in boxes:
        faces = {}
        for name in ("north", "south"):
            faces[name] = {"uv": [u0, 0.0, u1, 16.0], "texture": "#0"}
        for name in ("east", "west", "up", "down"):
            faces[name] = {"uv": [u0, 0.0, u1, 4.0], "texture": "#0"}
        elements.append({
            "from": [round(v, 4) for v in frm],
            "to": [round(v, 4) for v in to],
            "faces": faces,
        })
    return {
        "credit": "generated by tools/gen_wind_blade_model.py",
        "textures": {
            "0": "%s:spell_projectile/%s" % (NS, NAME),
            "particle": "%s:spell_projectile/%s" % (NS, NAME),
        },
        "elements": elements,
    }


# ----------------------------------------------------------------- texture ----
def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


WHITE = (245, 252, 255)
PALE = (200, 236, 248)
MID = (108, 186, 214)
DEEP = (46, 110, 138)
EDGE = (255, 255, 255)


def build_texture():
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    for y in range(TEX_H):
        for x in range(TEX_W):
            # across the blade: y=0 outer cutting edge (bright) -> y=15 inner body (deep)
            if y < 4:
                c = lerp(EDGE, PALE, y / 3.0)
            elif y < 9:
                c = lerp(PALE, MID, (y - 4) / 4.0)
            else:
                c = lerp(MID, DEEP, (y - 9) / 6.0)
            # along the blade: brightest at the crest (x = TEX_W/2), dimmer at the horns
            crest = 1.0 - abs(x - TEX_W / 2.0) / (TEX_W / 2.0)
            gain = 0.80 + 0.30 * crest
            if x in (0, TEX_W - 1):
                gain *= 0.80            # the very tips fade out
            c = tuple(min(255, int(v * gain)) for v in c)
            px[x, y] = (c[0], c[1], c[2], 255)
    # a few wind streaks through the body so it does not read as flat plastic
    for k in range(5):
        y0 = 6 + k * 2
        for x in range(TEX_W):
            y = y0 + int(1.5 * math.sin(x * 0.55 + k))
            if 0 <= y < TEX_H:
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, r + 26), min(255, g + 22), min(255, b + 18), a)
    return img


# ----------------------------------------------------------------- preview ----
def build_preview(elements, tex):
    scale = 6
    pad = 6
    views = [("front (XY, flight axis into the screen)", 0),
             ("side (ZY)", 1)]
    cell_w = 16 * scale + pad * 2
    board = Image.new("RGBA", (cell_w * len(views), 16 * scale + pad * 2 + 14), (24, 24, 30, 255))
    d = ImageDraw.Draw(board)
    for vi, (label, mode) in enumerate(views):
        ox = vi * cell_w + pad
        oy = pad + 12
        # unit box (0..16) as a 1-block reference grid
        d.rectangle([ox, oy, ox + 16 * scale, oy + 16 * scale], outline=(70, 70, 80, 255))
        for el in elements:
            frm, to = el["from"], el["to"]
            if mode == 0:
                a = (frm[0], frm[1]); b = (to[0], to[1])
            else:
                a = (frm[2], frm[1]); b = (to[2], to[1])
            # sample the texture where this box's front face samples it
            uv = el["faces"]["north"]["uv"]
            sx = int((uv[0] + uv[2]) / 2.0 / 16.0 * (TEX_W - 1))
            sy = int((uv[1] + uv[3]) / 2.0 / 16.0 * (TEX_H - 1))
            col = tex.getpixel((sx, sy))
            d.rectangle([ox + a[0] * scale, oy + (16 - b[1]) * scale,
                         ox + b[0] * scale, oy + (16 - a[1]) * scale],
                        fill=(col[0], col[1], col[2], 190))
        d.text((ox, oy - 11), label, fill=(200, 200, 210, 255))
    return board


# -------------------------------------------------------------------- main ----
def main():
    boxes = recenter(build_boxes())
    model = to_model(boxes)

    lo = [min(e["from"][a] for e in model["elements"]) for a in range(3)]
    hi = [max(e["to"][a] for e in model["elements"]) for a in range(3)]
    print("model      : %s (%d elements, 0 rotated)" % (NAME, len(model["elements"])))
    print("bbox units : x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f" %
          (lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
    print("bbox blocks: %.3f x %.3f x %.3f" %
          ((hi[0] - lo[0]) / 16.0, (hi[1] - lo[1]) / 16.0, (hi[2] - lo[2]) / 16.0))
    worst = min(lo) - LOADER_LO, LOADER_HI - max(hi)
    print("loader headroom: %.2f below / %.2f above (must both be >= 0)" % worst)
    if min(lo) < LOADER_LO or max(hi) > LOADER_HI:
        raise SystemExit("REFUSING: model leaves the loader window -16..32 (the WHOLE "
                         "model would be refused and the spell would render purple/black)")

    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    with open(MODEL_PATH, "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")

    tex = build_texture()
    tex.save(TEX_PATH)

    os.makedirs(os.path.dirname(PREVIEW_PATH), exist_ok=True)
    build_preview(model["elements"], tex).save(PREVIEW_PATH)

    print("wrote %s" % MODEL_PATH)
    print("wrote %s (%dx%d)" % (TEX_PATH, TEX_W, TEX_H))
    print("wrote %s" % PREVIEW_PATH)


if __name__ == "__main__":
    main()
