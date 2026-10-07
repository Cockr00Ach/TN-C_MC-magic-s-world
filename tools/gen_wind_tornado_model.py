#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_wind_tornado_model.py -- the TORNADO model for the wind gale chain (t3+).

Author 2026-10-10: "make a tornado model; from t3 on, casting the gale spell
summons a tornado that keeps spinning and keeps firing wind blades outward".

So the tornado is an ENTITY now (not a projectile): it stands where it was cast,
spins forever-ish, and spits wind blades outward. This script builds its look.

WHY WE GENERATE OUR OWN instead of re-using the author's exported tornado:
  that export came from `elemental_wizards_rpg` (the modpack's own RPG mod),
  i.e. a THIRD PARTY's art -- the project rule is "do not copy other mods'
textures/models/code". The author also said "you make one". So: ours.

SHAPE: a twisted funnel. 7 stacked layers, each layer is 4 thin panels sitting
on the 4 sides of a square whose radius SHRINKS with height (wide at the bottom,
narrow at the top = the classic tornado silhouette). Each layer is yaw-rotated
22.5 degrees more than the one below it, which reads as a spiral twist.

  * rotation angles stay on multiples of 22.5 degrees (the loader's baked-model
    rule recorded in docs/spells/projectile_model_recipe.md)
  * all panels SHARE one UV island on purpose: the twist already comes from the
    per-layer rotation, so a single vertical-streak texture is enough -- and it
    keeps the atlas tiny.
  * extents stay inside the loader window (-16..32) -- checked below, and the
    script REFUSES to write a model that would not load.

Outputs:
  assets/tnc/models/projectile/wind_tornado.json
  assets/tnc/textures/spell_projectile/wind_tornado.png
  docs/previews/wind_tornado_preview.png
"""

import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc",
                          "models", "projectile", "wind_tornado.json")
TEX_PATH = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc",
                        "textures", "spell_projectile", "wind_tornado.png")
PREVIEW_PATH = os.path.join(ROOT, "docs", "previews", "wind_tornado_preview.png")

# ---- shape knobs (units are 1/16 block, same as the model format) ----
# NOTE ON THE Y AXIS: the engine maps model y=0 onto the entity position (that is
# what the night-hand renderer's 13/16 lift proves), so the base sits at y=0 and
# the whole funnel grows UPWARD from the entity's feet -- no anchor fix needed.
# The loader window is -16..32, so the total height must stay <= 32 units.
LAYERS = 7              # how many stacked rings
PANELS = 4              # panels per ring (4 = a square tube)
BASE_RADIUS = 13.5      # bottom half-width
TOP_RADIUS = 4.0        # top half-width
LAYER_STEP = 4.25       # vertical distance between layers
PANEL_HEIGHT = 5.5      # panel height (> LAYER_STEP so the layers overlap)
PANEL_WIDTH = 11.0      # how wide each panel is
PANEL_THICK = 1.0
TWIST_DEG = 22.5        # yaw added per layer (must stay a multiple of 22.5)
DEBRIS = 4              # flat boxes lying at the base

# loader window: elements must stay inside -16..32 on every axis
LOADER_MIN, LOADER_MAX = -16.0, 32.0

# one shared UV island for every panel (atlas is 32x32)
UV_PANEL = [0, 0]
UV_SIZE = [PANEL_WIDTH + PANEL_THICK * 2, PANEL_HEIGHT + PANEL_THICK * 2]
TEX_W, TEX_H = 32, 32


def layer_radius(i):
    """Radius of layer i: linear taper from BASE_RADIUS down to TOP_RADIUS."""
    if LAYERS <= 1:
        return BASE_RADIUS
    t = i / float(LAYERS - 1)
    return BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * t


def panel(box_from, box_to, yaw, pivot_y):
    """One element: a box, yaw-rotated about the model's vertical centre line."""
    return {
        "from": [round(v, 3) for v in box_from],
        "to": [round(v, 3) for v in box_to],
        "rotation": {
            "origin": [round(pivot_y[0], 3), round(pivot_y[1], 3), round(pivot_y[2], 3)],
            "axis": "y",
            "angle": round(yaw, 3),
            "rescale": False,
        },
        "faces": {
            f: {"uv": list(UV_PANEL), "texture": "#0"}
            for f in ("north", "east", "south", "west", "up", "down")
        },
    }


def build_elements():
    elements = []
    half_w = PANEL_WIDTH / 2.0
    half_t = PANEL_THICK / 2.0

    for i in range(LAYERS):
        r = layer_radius(i)
        y0 = i * LAYER_STEP
        y1 = y0 + PANEL_HEIGHT
        yaw = i * TWIST_DEG
        pivot = (8.0, y0 + PANEL_HEIGHT / 2.0, 8.0)   # rotate about the centre line

        # four panels: +X, -X, +Z, -Z sides of the square
        elements.append(panel([8.0 + r - half_t, y0, 8.0 - half_w],
                              [8.0 + r + half_t, y1, 8.0 + half_w], yaw, pivot))
        elements.append(panel([8.0 - r - half_t, y0, 8.0 - half_w],
                              [8.0 - r + half_t, y1, 8.0 + half_w], yaw, pivot))
        elements.append(panel([8.0 - half_w, y0, 8.0 + r - half_t],
                              [8.0 + half_w, y1, 8.0 + r + half_t], yaw, pivot))
        elements.append(panel([8.0 - half_w, y0, 8.0 - r - half_t],
                              [8.0 + half_w, y1, 8.0 - r + half_t], yaw, pivot))

    # a flat debris ring lying on the ground, so it does not look like it floats
    r = BASE_RADIUS + 1.0
    for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if sx:
            elements.append(panel([8.0 + sx * r - half_t, 0.0, 8.0 - half_w],
                                  [8.0 + sx * r + half_t, 1.5, 8.0 + half_w],
                                  0.0, (8.0, 0.75, 8.0)))
        else:
            elements.append(panel([8.0 - half_w, 0.0, 8.0 + sz * r - half_t],
                                  [8.0 + half_w, 1.5, 8.0 + sz * r + half_t],
                                  0.0, (8.0, 0.75, 8.0)))
    return elements


def check_extents(elements):
    """Refuse to write a model the loader cannot bake (window is -16..32)."""
    lo = [1e9, 1e9, 1e9]
    hi = [-1e9, -1e9, -1e9]
    for e in elements:
        for i in range(3):
            lo[i] = min(lo[i], e["from"][i])
            hi[i] = max(hi[i], e["to"][i])
    bad = [a for a in range(3) if lo[a] < LOADER_MIN or hi[a] > LOADER_MAX]
    span = [round((hi[i] - lo[i]) / 16.0, 3) for i in range(3)]
    return lo, hi, span, bad


def paint_texture():
    """Vertical wind streaks: mostly translucent white-cyan, a few bright ones."""
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    u, v = UV_PANEL
    w, h = int(UV_SIZE[0]), int(UV_SIZE[1])
    for x in range(w):
        # a cheap deterministic "streak" pattern: some columns are brighter
        k = (x * 7) % 11
        base_alpha = 70 + k * 13
        for y in range(h):
            fade = 1.0 - (y / float(max(1, h - 1))) * 0.35     # a little darker at the top
            a = int(min(235, base_alpha * fade))
            # cyan-white wind
            r = int(198 + k * 4)
            g = int(232 + k * 2)
            b = 255
            px[u + x, v + y] = (min(r, 255), min(g, 255), b, a)
    # a brighter leading edge on the left/right of the island, so panels read as blades of wind
    for y in range(h):
        px[u, v + y] = (255, 255, 255, 245)
        px[u + w - 1, v + y] = (235, 250, 255, 225)
    img.save(TEX_PATH)
    return img


def paint_preview(elements, img):
    """Tiny front view of the silhouette, for eyeballing proportions."""
    scale = 3
    W, H = 120, 150
    canvas = Image.new("RGBA", (W, H), (18, 22, 28, 255))
    d = ImageDraw.Draw(canvas)
    for e in elements:
        x0 = W / 2 + (e["from"][0] - 8.0) * scale
        x1 = W / 2 + (e["to"][0] - 8.0) * scale
        y1 = H - 10 - e["from"][1] * scale
        y0 = H - 10 - e["to"][1] * scale
        d.rectangle([x0, y0, x1, y1], outline=(150, 220, 245, 255))
    canvas.save(PREVIEW_PATH)


def main():
    elements = build_elements()
    lo, hi, span, bad = check_extents(elements)

    model = {
        "credit": "TN-C wind tornado (generated by tools/gen_wind_tornado_model.py)",
        "texture_size": [TEX_W, TEX_H],
        "textures": {"0": "tnc:spell_projectile/wind_tornado", "particle": "tnc:spell_projectile/wind_tornado"},
        "elements": elements,
    }

    print("elements   : %d (%d layers x %d panels + %d debris)"
          % (len(elements), LAYERS, PANELS, DEBRIS))
    print("extents    : %s .. %s" % (lo, hi))
    print("size       : %.3f x %.3f x %.3f blocks" % tuple(span))
    if bad:
        raise SystemExit("REFUSING: axis %s is outside the loader window %s..%s"
                         % (bad, LOADER_MIN, LOADER_MAX))

    for d in (os.path.dirname(MODEL_PATH), os.path.dirname(TEX_PATH),
              os.path.dirname(PREVIEW_PATH)):
        os.makedirs(d, exist_ok=True)

    with open(MODEL_PATH, "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")
    img = paint_texture()
    paint_preview(elements, img)

    print("wrote %s" % MODEL_PATH)
    print("wrote %s (%dx%d)" % (TEX_PATH, TEX_W, TEX_H))
    print("wrote %s" % PREVIEW_PATH)


if __name__ == "__main__":
    main()