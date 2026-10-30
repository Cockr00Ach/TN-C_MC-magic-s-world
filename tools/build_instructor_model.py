#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the GeckoLib assets of the "Warrior Training Grounds instructor" (tnc).

Single source of truth for:

    src/main/resources/assets/tnc/geo/entity/instructor.geo.json
    src/main/resources/assets/tnc/animations/entity/instructor.animation.json
    src/main/resources/assets/tnc/textures/entity/instructor_counter.png
    src/main/resources/assets/tnc/textures/entity/instructor_guard.png
    src/main/resources/assets/tnc/textures/entity/instructor_blade.png
    src/main/resources/assets/tnc/textures/entity/instructor_wind.png

plus human readable contact sheets under ``work/instructor-preview/``.

Run with::

    python tools/build_instructor_model.py

Only the standard library (json/math/os) and Pillow are used, and every byte
written is deterministic: the same source always yields the same JSON text and
the same PNG bytes (no timestamps, no unseeded randomness).
"""

from __future__ import annotations

import json
import math
import os

from PIL import Image, ImageDraw

# --------------------------------------------------------------------------- #
# paths / constants
# --------------------------------------------------------------------------- #

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc")
GEO_PATH = os.path.join(ASSETS, "geo", "entity", "instructor.geo.json")
ANIM_PATH = os.path.join(ASSETS, "animations", "entity", "instructor.animation.json")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")
PREVIEW_DIR = os.path.join(ROOT, "work", "instructor-preview")

GEOMETRY_ID = "geometry.instructor"
TEX_W, TEX_H = 128, 64
FPS = 20

VARIANTS = ("counter", "guard", "blade", "wind")
TEX_FILES = {v: os.path.join(TEX_DIR, "instructor_%s.png" % v) for v in VARIANTS}

# --------------------------------------------------------------------------- #
# model description
# --------------------------------------------------------------------------- #
#
# GeckoLib / Bedrock 1.12 coordinates: +Y up, feet on y=0, model faces -Z,
# so "north" (the face at -Z) is the front of the entity and "south" the back.
#
# Silhouette anchors are copied from assets/tnc/geo/entity/self.geo.json
# (feet 0 / hip 12 / shoulder 24 / crown 32) and then split into two joints
# per limb instead of one.

BONES = [
    {
        "name": "root",
        "parent": None,
        "pivot": [0, 24, 0],
        "cubes": [],
    },
    {
        "name": "body",
        "parent": "root",
        "pivot": [0, 12, 0],
        "cubes": [
            {"part": "body", "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]},
        ],
    },
    {
        "name": "head",
        "parent": "body",
        "pivot": [0, 24, 0],
        "cubes": [
            {"part": "head", "origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]},
        ],
    },
    {
        # cloth head-wrap shell drawn 0.25 outward on every side (8.5 cube) while
        # the box-UV block stays the vanilla 8x8x8 hat block at uv[32,0]
        "name": "hat",
        "parent": "head",
        "pivot": [0, 24, 0],
        "cubes": [
            {
                "part": "hat",
                "origin": [-4, 24, -4],
                "size": [8, 8, 8],
                "uv": [32, 0],
                "inflate": 0.25,
            },
        ],
    },
    {
        "name": "cape",
        "parent": "body",
        "pivot": [0, 24, 2.75],
        "cubes": [
            {
                "part": "cape",
                "origin": [-5, 10, 2.25],
                "size": [10, 14, 1],
                "uv": [64, 0],
                "rotation": [-5, 0, 0],
                "pivot": [0, 24, 2.75],
            },
        ],
    },
    {
        "name": "shoulder_r",
        "parent": "body",
        "pivot": [-4, 24, 0],
        "cubes": [
            {"part": "shoulder", "origin": [-8, 21, -2.5], "size": [4, 3, 5], "uv": [88, 0]},
        ],
    },
    {
        "name": "upper_arm_r",
        "parent": "shoulder_r",
        "pivot": [-6, 24, 0],
        "cubes": [
            {"part": "upper_arm", "origin": [-8, 18, -2], "size": [4, 6, 4], "uv": [40, 16]},
        ],
    },
    {
        # 1 px of the forearm sits *inside* the sleeve (y 12..19 around an elbow at
        # y=18) so a bent elbow never shows a wedge-shaped gap
        "name": "forearm_r",
        "parent": "upper_arm_r",
        "pivot": [-6, 18, 0],
        "cubes": [
            {
                "part": "forearm",
                "origin": [-8, 12, -2],
                "size": [4, 7, 4],
                "uv": [64, 26],
                "inflate": 0.05,
            },
        ],
    },
    {
        "name": "shoulder_l",
        "parent": "body",
        "pivot": [4, 24, 0],
        "cubes": [
            {"part": "shoulder", "origin": [4, 21, -2.5], "size": [4, 3, 5], "uv": [108, 0]},
        ],
    },
    {
        "name": "upper_arm_l",
        "parent": "shoulder_l",
        "pivot": [6, 24, 0],
        "cubes": [
            {"part": "upper_arm", "origin": [4, 18, -2], "size": [4, 6, 4], "uv": [32, 48]},
        ],
    },
    {
        "name": "forearm_l",
        "parent": "upper_arm_l",
        "pivot": [6, 18, 0],
        "cubes": [
            {
                "part": "forearm",
                "origin": [4, 12, -2],
                "size": [4, 7, 4],
                "uv": [82, 26],
                "inflate": 0.05,
            },
        ],
    },
    {
        "name": "thigh_r",
        "parent": "root",
        "pivot": [-2, 12, 0],
        "cubes": [
            {"part": "thigh", "origin": [-4, 6, -2], "size": [4, 6, 4], "uv": [0, 16]},
        ],
    },
    {
        # shin starts at y=7 while the thigh ends at y=6: one pixel of overlap
        # around the knee so a bent knee never opens a gap
        "name": "shin_r",
        "parent": "thigh_r",
        "pivot": [-2, 7, 0],
        "cubes": [
            {
                "part": "shin",
                "origin": [-4, 0, -2],
                "size": [4, 7, 4],
                "uv": [64, 38],
                "inflate": 0.05,
            },
        ],
    },
    {
        "name": "thigh_l",
        "parent": "root",
        "pivot": [2, 12, 0],
        "cubes": [
            {"part": "thigh", "origin": [0, 6, -2], "size": [4, 6, 4], "uv": [16, 48]},
        ],
    },
    {
        "name": "shin_l",
        "parent": "thigh_l",
        "pivot": [2, 7, 0],
        "cubes": [
            {
                "part": "shin",
                "origin": [0, 0, -2],
                "size": [4, 7, 4],
                "uv": [82, 38],
                "inflate": 0.05,
            },
        ],
    },
    {
        "name": "skirt_f",
        "parent": "body",
        "pivot": [0, 12, -2.5],
        "cubes": [
            {
                "part": "skirt",
                "origin": [-4, 5, -3],
                "size": [8, 7, 1],
                "uv": [64, 16],
                "rotation": [7, 0, 0],  # flares away from the legs
                "pivot": [0, 12, -2.5],
            },
        ],
    },
    {
        "name": "skirt_b",
        "parent": "body",
        "pivot": [0, 12, 2.5],
        "cubes": [
            {
                "part": "skirt",
                "origin": [-4, 5, 2],
                "size": [8, 7, 1],
                "uv": [84, 16],
                "rotation": [-7, 0, 0],
                "pivot": [0, 12, 2.5],
            },
        ],
    },
]

BONE_NAMES = [b["name"] for b in BONES]

# --------------------------------------------------------------------------- #
# box UV maths (Bedrock / Blockbench layout, one uv origin per cube)
# --------------------------------------------------------------------------- #


def box_uv_rects(uv, size):
    """Return {face: (x0, y0, x1, y1)} half open pixel rects for a box-UV cube."""
    u, v = uv[0], uv[1]
    w, h, d = size[0], size[1], size[2]
    return {
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + 2 * w, v + d),
    }


def box_uv_footprint(uv, size):
    w, h, d = size[0], size[1], size[2]
    return (uv[0], uv[1], uv[0] + 2 * (w + d), uv[1] + d + h)


# --------------------------------------------------------------------------- #
# palette
# --------------------------------------------------------------------------- #

PALETTES = {
    "counter": {
        "label": "instructor_counter (陪练傀儡)",
        "base": "#7f8a94",
        "dark": "#3a3f45",
        "accent": "#6ea08a",
        "trim": "#9aa6ae",
        "skin": "#9aa39b",
        "hair": "#4b5157",
        "wear": True,
        "emblem": "rivet",
        "blurb": "石灰布身 + 深灰束带 + 铜锈绿点缀，带磨损斑点",
    },
    "guard": {
        "label": "instructor_guard (洛克·盾卫)",
        "base": "#4f6f96",
        "dark": "#2b3d54",
        "accent": "#c9a44c",
        "trim": "#e8eef5",
        "skin": "#c08a63",
        "hair": "#3a3f45",
        "wear": False,
        "emblem": "chevron",
        "blurb": "钢蓝甲衣 + 月白镶边 + 金线扣饰",
    },
    "blade": {
        "label": "instructor_blade (莉娅·剑术)",
        "base": "#b8863f",
        "dark": "#4a3226",
        "accent": "#8f6a34",
        "trim": "#efe3c8",
        "skin": "#c08a63",
        "hair": "#3b2a20",
        "wear": False,
        "emblem": "sash",
        "blurb": "赭金劲装 + 深棕皮革 + 米白交领束带",
    },
    "wind": {
        "label": "instructor_wind (岚·风刃)",
        "base": "#4fb3a4",
        "dark": "#235f5a",
        "accent": "#8fd8cb",
        "trim": "#e6f4f1",
        "skin": "#c08a63",
        "hair": "#26443f",
        "wear": False,
        "emblem": "wave",
        "blurb": "青碧外袍 + 月白内衬 + 深青风纹",
    },
}


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def _clamp(v):
    if v < 0:
        return 0
    if v > 255:
        return 255
    return int(round(v))


def mul(c, f):
    return (_clamp(c[0] * f), _clamp(c[1] * f), _clamp(c[2] * f), 255)


def mix(a, b, t):
    return (
        _clamp(a[0] + (b[0] - a[0]) * t),
        _clamp(a[1] + (b[1] - a[1]) * t),
        _clamp(a[2] + (b[2] - a[2]) * t),
        255,
    )


def _noise(x, y, seed):
    """Deterministic 0..1 hash noise (no RNG state, so output never drifts)."""
    n = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


class Sheet:
    """A 128x64 RGBA texture sheet with deterministic painting helpers."""

    def __init__(self):
        self.img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x, y, c):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            self.px[x, y] = (c[0], c[1], c[2], 255)

    def face(self, rect, color, seed, wear=False, lift=1.12, drop=0.34):
        """Fill a whole face with a top-bright / bottom-dark cloth gradient."""
        x0, y0, x1, y1 = rect
        fh = max(1, y1 - y0)
        for y in range(y0, y1):
            t = (y - y0 + 0.5) / fh
            f = lift - drop * t + 0.02 * math.sin((y * 2.3 + x0 * 1.7) + seed)
            for x in range(x0, x1):
                n = _noise(x, y, seed)
                ff = f
                if n < 0.10:
                    ff -= 0.05
                elif n > 0.93:
                    ff += 0.04
                c = mul(color, ff)
                if wear and _noise(x + 53, y + 29, seed + 7) > 0.88:
                    c = mix(c, (58, 62, 60), 0.45)
                self.put(x, y, c)

    def box(self, rect, lx0, ly0, lx1, ly1, color, shade=1.0, seed=0, wear=False):
        """Paint a sub rectangle of a face (local coordinates, half open)."""
        x0, y0, x1, y1 = rect
        sx0, sy0 = x0 + lx0, y0 + ly0
        sx1, sy1 = min(x0 + lx1, x1), min(y0 + ly1, y1)
        h = max(1, sy1 - sy0)
        for y in range(sy0, sy1):
            f = shade * (1.06 - 0.16 * ((y - sy0) / h))
            for x in range(max(sx0, x0), sx1):
                c = mul(color, f)
                if wear and _noise(x + 11, y + 71, seed + 3) > 0.90:
                    c = mix(c, (52, 56, 54), 0.40)
                self.put(x, y, c)

    def dot(self, rect, lx, ly, color):
        self.put(rect[0] + lx, rect[1] + ly, color)


# --------------------------------------------------------------------------- #
# per part painting
# --------------------------------------------------------------------------- #


def paint_head(sh, r, pal):
    skin = hx(pal["skin"])
    hair = hx(pal["hair"])
    dark = hx(pal["dark"])
    seed = 101

    sh.face(r["up"], hair, seed)
    sh.face(r["down"], mul(skin, 0.76), seed)
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], skin, seed, pal["wear"], lift=1.08, drop=0.30)

    # hair cap and ears on both sides
    for f in ("east", "west"):
        fw = r[f][2] - r[f][0]
        sh.box(r[f], 0, 0, fw, 2, hair)
        sh.box(r[f], 0, 2, fw, 3, mul(hair, 0.86))
        sh.box(r[f], 2, 3, 4, 6, mul(skin, 0.72))       # ear shadow
        sh.box(r[f], 2, 4, 4, 5, mul(skin, 0.56))       # ear canal

    # back of the head: hair, with a short nape
    sh.box(r["south"], 0, 0, 8, 5, hair)
    sh.box(r["south"], 3, 5, 5, 6, mul(hair, 0.82))

    # top: strands
    for i in range(0, 8, 2):
        sh.box(r["up"], i, 0, i + 1, 8, mul(hair, 0.84))

    fr = r["north"]
    sh.box(fr, 0, 0, 8, 2, hair)                        # fringe
    sh.box(fr, 0, 1, 8, 2, mul(hair, 0.80))
    sh.box(fr, 1, 2, 3, 3, mul(hair, 0.72))             # brows
    sh.box(fr, 5, 2, 7, 3, mul(hair, 0.72))

    eye = mix(dark, (16, 18, 22, 255), 0.65)
    sh.box(fr, 1, 3, 3, 5, eye)                          # left eye
    sh.box(fr, 5, 3, 7, 5, eye)                          # right eye
    glow = mix(hx(pal["trim"]), (255, 255, 255, 255), 0.5)
    sh.dot(fr, 1, 3, glow)
    sh.dot(fr, 5, 3, glow)

    sh.box(fr, 3, 5, 5, 6, mul(skin, 0.86))             # nose
    sh.box(fr, 3, 4, 5, 5, mul(skin, 1.04))             # nose bridge highlight
    sh.box(fr, 3, 6, 5, 7, mul(skin, 0.60))             # mouth
    sh.box(fr, 0, 3, 1, 7, mul(skin, 0.90))             # cheek shading
    sh.box(fr, 7, 3, 8, 7, mul(skin, 0.90))


def paint_hat(sh, r, pal):
    band = hx(pal["trim"])
    accent = hx(pal["accent"])
    for f in ("east", "west", "north", "south"):
        fw = r[f][2] - r[f][0]
        sh.box(r[f], 0, 0, fw, 2, band)
        sh.box(r[f], 0, 1, fw, 2, mul(band, 0.90))
        sh.box(r[f], 0, 0, fw, 1, mul(band, 1.06))
    # knot and tail at the back of the head
    sh.box(r["south"], 3, 1, 5, 5, accent)
    sh.box(r["south"], 3, 4, 5, 5, mul(accent, 0.82))
    sh.dot(r["south"], 3, 2, mul(accent, 1.18))
    # 1px ring on the crown so the wrap reads from above
    up = r["up"]
    fw = up[2] - up[0]
    for i in range(fw):
        sh.box(up, i, 0, i + 1, 1, band, 1.04)
        sh.box(up, i, fw - 1, i + 1, fw, band, 0.92)
        sh.box(up, 0, i, 1, i + 1, band, 0.98)
        sh.box(up, fw - 1, i, fw, i + 1, band, 0.98)


def paint_body(sh, r, pal):
    base = hx(pal["base"])
    dark = hx(pal["dark"])
    trim = hx(pal["trim"])
    accent = hx(pal["accent"])
    seed = 202

    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"])
    sh.face(r["up"], mul(base, 0.70), seed)
    sh.face(r["down"], dark, seed)

    # waist sash / belt
    for f in ("east", "west", "north", "south"):
        fw = r[f][2] - r[f][0]
        sh.box(r[f], 0, 9, fw, 11, dark)
        sh.box(r[f], 0, 9, fw, 10, mul(dark, 1.20))
        sh.box(r[f], 0, 11, fw, 12, mul(base, 0.62))
    sh.box(r["north"], 3, 9, 5, 11, accent)
    sh.box(r["north"], 3, 10, 5, 11, mul(accent, 0.78))

    # crossed collar (交领) with a light trim
    fr = r["north"]
    sh.box(fr, 0, 0, 8, 2, trim)
    sh.box(fr, 0, 0, 8, 1, mul(trim, 1.05))
    sh.box(fr, 0, 2, 3, 3, trim)                         # crossed lapels
    sh.box(fr, 5, 2, 8, 3, trim)
    sh.box(fr, 0, 3, 1, 6, trim)                         # lapel edges
    sh.box(fr, 7, 3, 8, 6, trim)
    sh.box(fr, 3, 0, 5, 5, mul(base, 0.82))              # V opening
    sh.box(fr, 3, 1, 5, 2, mul(base, 0.68))              # inner shadow
    sh.box(fr, 3, 5, 4, 9, mul(base, 0.88))              # centre fold
    sh.box(fr, 1, 6, 2, 9, mul(base, 0.93))
    sh.box(fr, 6, 6, 7, 9, mul(base, 0.93))

    # back: shoulder yoke + seam
    bk = r["south"]
    sh.box(bk, 0, 0, 8, 2, mul(base, 0.86))
    sh.box(bk, 3, 2, 4, 9, mul(base, 0.90))
    for f in ("east", "west"):
        fw = r[f][2] - r[f][0]
        sh.box(r[f], fw // 2, 2, fw // 2 + 1, 9, mul(base, 0.90))

    emblem = pal["emblem"]
    if emblem == "sash":
        for i in range(5):
            sh.box(fr, 1 + i, 4 + i, 3 + i, 5 + i, trim)
        sh.box(fr, 5, 9, 7, 10, accent)
    elif emblem == "chevron":
        for i in range(3):
            sh.box(fr, 2 + i, 5 + i, 4 + i, 6 + i, accent)
            sh.box(fr, 6 - i, 5 + i, 8 - i, 6 + i, accent)
    elif emblem == "wave":
        for i in range(3):
            sh.box(fr, 2, 5 + i * 2, 6, 6 + i * 2, accent, 0.88 + 0.06 * i)
    else:  # rivet / patina specks
        for i, xy in enumerate(((2, 5), (5, 6), (3, 8), (6, 9))):
            sh.dot(fr, xy[0], xy[1], accent if i % 2 == 0 else mul(accent, 0.75))


def paint_cape(sh, r, pal):
    base = mul(hx(pal["base"]), 0.88)
    seed = 303

    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"], lift=1.06, drop=0.28)
    sh.face(r["up"], mul(base, 0.72), seed)

    for f in ("north", "south"):
        for i in (1, 4, 7):
            sh.box(r[f], i, 1, i + 1, 12, mul(base, 0.74))
        sh.box(r[f], 0, 0, 10, 2, mul(base, 1.12))
        sh.box(r[f], 4, 0, 6, 2, mul(base, 1.22))         # shoulder pleat
        sh.box(r[f], 0, 12, 10, 13, hx(pal["accent"]))     # hem band
        sh.box(r[f], 0, 13, 10, 14, mul(base, 0.68))       # dark hem edge


def paint_shoulder(sh, r, pal):
    base = hx(pal["base"])
    accent = hx(pal["accent"])
    seed = 404
    for f in ("east", "west", "north", "south", "up"):
        sh.face(r[f], base, seed, pal["wear"], lift=1.16, drop=0.26)
    for f in ("east", "west", "north", "south"):
        fw = r[f][2] - r[f][0]
        sh.box(r[f], 0, 2, fw, 3, accent)
        sh.box(r[f], 1, 1, 2, 2, mul(accent, 1.10))
    sh.face(r["down"], mul(base, 0.58), seed)


def paint_upper_arm(sh, r, pal):
    base = hx(pal["base"])
    accent = hx(pal["accent"])
    seed = 505
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"])
    sh.face(r["up"], mul(base, 0.74), seed)
    sh.face(r["down"], mul(base, 0.68), seed)
    for f in ("east", "west", "north", "south"):
        sh.box(r[f], 0, 4, 4, 6, mul(base, 0.80))        # sleeve cuff
        sh.box(r[f], 0, 4, 4, 5, accent)


def paint_forearm(sh, r, pal):
    skin = hx(pal["skin"])
    dark = hx(pal["dark"])
    accent = hx(pal["accent"])
    seed = 606
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], skin, seed, pal["wear"], lift=1.06, drop=0.32)
    sh.face(r["up"], mul(skin, 0.90), seed)
    sh.face(r["down"], mul(skin, 0.80), seed)
    for f in ("east", "west", "north", "south"):
        sh.box(r[f], 0, 0, 4, 1, mul(skin, 0.72))       # hidden inside the sleeve
        sh.box(r[f], 0, 4, 4, 5, accent)                # wrap trim
        sh.box(r[f], 0, 5, 4, 6, dark)                  # wrist wrap
        sh.box(r[f], 0, 6, 4, 7, mul(skin, 0.92))       # hand
    sh.box(r["north"], 1, 6, 3, 7, mul(skin, 0.76))     # knuckles


def paint_thigh(sh, r, pal):
    base = hx(pal["base"])
    seed = 707
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"], lift=1.05, drop=0.26)
    sh.face(r["up"], mul(base, 0.68), seed)
    sh.face(r["down"], mul(base, 0.64), seed)
    for f in ("east", "west", "north", "south"):
        sh.box(r[f], 0, 0, 4, 1, mul(base, 0.82))
        sh.box(r[f], 1, 1, 2, 6, mul(base, 0.90))


def paint_shin(sh, r, pal):
    base = hx(pal["base"])
    dark = hx(pal["dark"])
    accent = hx(pal["accent"])
    seed = 808
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"], lift=1.02, drop=0.30)
    sh.face(r["up"], mul(base, 0.70), seed)
    sh.face(r["down"], mul(dark, 0.72), seed)           # sole
    for f in ("east", "west", "north", "south"):
        sh.box(r[f], 0, 3, 4, 5, dark)                  # boot cuff
        sh.box(r[f], 0, 3, 4, 4, accent)
        sh.box(r[f], 0, 5, 4, 7, mul(dark, 0.86))       # boot body
        sh.box(r[f], 0, 6, 4, 7, mul(dark, 1.12))       # boot toe / lace edge


def paint_skirt(sh, r, pal):
    base = mul(hx(pal["base"]), 0.94)
    trim = hx(pal["trim"])
    seed = 909
    for f in ("east", "west", "north", "south"):
        sh.face(r[f], base, seed, pal["wear"], lift=1.06, drop=0.26)
    sh.face(r["up"], mul(base, 0.72), seed)
    sh.face(r["down"], mul(base, 0.66), seed)
    for f in ("north", "south"):
        sh.box(r[f], 0, 5, 8, 6, trim)
        sh.box(r[f], 0, 6, 8, 7, mul(hx(pal["dark"]), 0.92))
        for i in (2, 5):
            sh.box(r[f], i, 0, i + 1, 5, mul(base, 0.85))


PAINTERS = {
    "head": paint_head,
    "hat": paint_hat,
    "body": paint_body,
    "cape": paint_cape,
    "shoulder": paint_shoulder,
    "upper_arm": paint_upper_arm,
    "forearm": paint_forearm,
    "thigh": paint_thigh,
    "shin": paint_shin,
    "skirt": paint_skirt,
}


def build_texture(variant):
    pal = PALETTES[variant]
    sh = Sheet()
    for bone in BONES:
        for cube in bone.get("cubes", []):
            rects = box_uv_rects(cube["uv"], cube["size"])
            PAINTERS[cube["part"]](sh, rects, pal)
    return sh.img


# --------------------------------------------------------------------------- #
# animations (20 fps; constant keyframes only, no molang)
# --------------------------------------------------------------------------- #


def kf(*pairs):
    """Build a constant keyframe dict from (time, [x, y, z]) pairs."""
    out = {}
    for t, v in pairs:
        out["%.2f" % t] = [round(float(v[0]), 2), round(float(v[1]), 2), round(float(v[2]), 2)]
    return out


def build_animations():
    Z = [0, 0, 0]
    anims = {}

    # ---- idle: breathing, head sway, cape drift, forearm lag --------------- #
    anims["idle"] = {
        "loop": True,
        "animation_length": 3.0,
        "bones": {
            "body": {
                "rotation": kf(
                    (0.00, [1.0, 0.0, 0.4]),
                    (0.75, [0.0, 1.2, 0.0]),
                    (1.50, [-1.0, 0.0, -0.4]),
                    (2.25, [0.0, -1.2, 0.0]),
                    (3.00, [1.0, 0.0, 0.4]),
                )
            },
            "head": {
                "rotation": kf(
                    (0.00, [-1.0, 2.0, 0.0]),
                    (0.75, [1.2, 0.0, -0.8]),
                    (1.50, [-1.0, -2.0, 0.0]),
                    (2.25, [1.2, 0.0, 0.8]),
                    (3.00, [-1.0, 2.0, 0.0]),
                )
            },
            "cape": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.60, [-3.0, 0.0, 1.2]),
                    (1.50, [0.5, 0.0, 0.0]),
                    (2.20, [2.0, 0.0, -1.2]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            "skirt_f": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.75, [-1.5, 0.0, 0.0]),
                    (1.50, [0.0, 0.0, 0.0]),
                    (2.25, [1.5, 0.0, 0.0]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            "skirt_b": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.75, [1.5, 0.0, 0.0]),
                    (1.50, [0.0, 0.0, 0.0]),
                    (2.25, [-1.5, 0.0, 0.0]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            "upper_arm_r": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.75, [1.2, 0.0, 0.6]),
                    (1.50, [0.0, 0.0, 0.0]),
                    (2.25, [-1.2, 0.0, -0.6]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            "upper_arm_l": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.75, [-1.2, 0.0, 0.6]),
                    (1.50, [0.0, 0.0, 0.0]),
                    (2.25, [1.2, 0.0, -0.6]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            # forearms drift behind the upper arms: the keyframes are shifted
            "forearm_r": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.85, [3.2, 0.0, 0.0]),
                    (1.60, [0.0, 0.0, 0.0]),
                    (2.35, [-3.2, 0.0, 0.0]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
            "forearm_l": {
                "rotation": kf(
                    (0.00, [0.0, 0.0, 0.0]),
                    (0.80, [3.2, 0.0, 0.0]),
                    (1.55, [0.0, 0.0, 0.0]),
                    (2.30, [-3.2, 0.0, 0.0]),
                    (3.00, [0.0, 0.0, 0.0]),
                )
            },
        },
    }

    # ---- attack: 14 ticks, right arm winds up then whips forward ---------- #
    anims["attack"] = {
        "loop": False,
        "animation_length": 0.7,
        "bones": {
            "body": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [5.0, -9.0, 0.0]),
                    (0.35, [-8.0, 7.0, 0.0]),
                    (0.55, [-4.0, 3.0, 0.0]),
                    (0.70, Z),
                )
            },
            "head": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [3.0, -11.0, 0.0]),
                    (0.35, [-4.0, 8.0, 0.0]),
                    (0.70, Z),
                )
            },
            "cape": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [2.0, 0.0, 0.0]),
                    (0.35, [-7.0, 0.0, 1.5]),
                    (0.55, [-2.0, 0.0, 0.0]),
                    (0.70, Z),
                )
            },
            "shoulder_r": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [-10.0, 0.0, 7.0]),
                    (0.35, [9.0, 0.0, -4.0]),
                    (0.70, Z),
                )
            },
            "upper_arm_r": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [-58.0, 0.0, 12.0]),
                    (0.35, [84.0, 0.0, -6.0]),
                    (0.50, [63.0, 0.0, -2.0]),
                    (0.70, Z),
                )
            },
            "forearm_r": {
                "rotation": kf(
                    (0.00, Z),
                    (0.18, [-46.0, 0.0, 0.0]),
                    (0.38, [12.0, 0.0, 0.0]),
                    (0.55, [-8.0, 0.0, 0.0]),
                    (0.70, Z),
                )
            },
            "upper_arm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.15, [-8.0, 0.0, -4.0]),
                    (0.35, [15.0, 0.0, 4.0]),
                    (0.70, Z),
                )
            },
            "forearm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.18, [-6.0, 0.0, 0.0]),
                    (0.40, [10.0, 0.0, 0.0]),
                    (0.70, Z),
                )
            },
            "thigh_r": {
                "rotation": kf((0.00, Z), (0.35, [-6.0, 0.0, 0.0]), (0.70, Z))
            },
            "shin_r": {
                "rotation": kf((0.00, Z), (0.35, [7.0, 0.0, 0.0]), (0.70, Z))
            },
            "thigh_l": {
                "rotation": kf((0.00, Z), (0.35, [4.0, 0.0, 0.0]), (0.70, Z))
            },
            "shin_l": {
                "rotation": kf((0.00, Z), (0.35, [-5.0, 0.0, 0.0]), (0.70, Z))
            },
            "skirt_f": {
                "rotation": kf((0.00, Z), (0.35, [-4.0, 0.0, 0.0]), (0.70, Z))
            },
            "skirt_b": {
                "rotation": kf((0.00, Z), (0.35, [4.0, 0.0, 0.0]), (0.70, Z))
            },
        },
    }

    # ---- block: 20 ticks, shield-side guard, held ------------------------- #
    anims["block"] = {
        "loop": False,
        "animation_length": 1.0,
        "bones": {
            "body": {"rotation": kf((0.00, Z), (0.18, [0.0, -18.0, 0.0]), (1.00, [0.0, -18.0, 0.0]))},
            "head": {"rotation": kf((0.00, Z), (0.18, [2.0, -14.0, 0.0]), (1.00, [2.0, -14.0, 0.0]))},
            "cape": {"rotation": kf((0.00, Z), (0.18, [3.0, -4.0, 0.0]), (1.00, [3.0, -4.0, 0.0]))},
            "shoulder_l": {"rotation": kf((0.00, Z), (0.18, [-4.0, 0.0, 10.0]), (1.00, [-4.0, 0.0, 10.0]))},
            "upper_arm_l": {"rotation": kf((0.00, Z), (0.18, [-6.0, 0.0, 74.0]), (1.00, [-6.0, 0.0, 74.0]))},
            "forearm_l": {"rotation": kf((0.00, Z), (0.18, [-10.0, 86.0, 0.0]), (1.00, [-10.0, 86.0, 0.0]))},
            "shoulder_r": {"rotation": kf((0.00, Z), (0.18, [-4.0, 0.0, -5.0]), (1.00, [-4.0, 0.0, -5.0]))},
            "upper_arm_r": {"rotation": kf((0.00, Z), (0.18, [-14.0, 0.0, -8.0]), (1.00, [-14.0, 0.0, -8.0]))},
            "forearm_r": {"rotation": kf((0.00, Z), (0.18, [-44.0, 0.0, 0.0]), (1.00, [-44.0, 0.0, 0.0]))},
            "thigh_r": {"rotation": kf((0.00, Z), (0.18, [4.0, 0.0, 0.0]), (1.00, [4.0, 0.0, 0.0]))},
            "shin_r": {"rotation": kf((0.00, Z), (0.18, [-6.0, 0.0, 0.0]), (1.00, [-6.0, 0.0, 0.0]))},
            "thigh_l": {"rotation": kf((0.00, Z), (0.18, [-3.0, 0.0, 0.0]), (1.00, [-3.0, 0.0, 0.0]))},
            "shin_l": {"rotation": kf((0.00, Z), (0.18, [5.0, 0.0, 0.0]), (1.00, [5.0, 0.0, 0.0]))},
            "skirt_f": {"rotation": kf((0.00, Z), (0.18, [3.0, 0.0, 0.0]), (1.00, [3.0, 0.0, 0.0]))},
            "skirt_b": {"rotation": kf((0.00, Z), (0.18, [-3.0, 0.0, 0.0]), (1.00, [-3.0, 0.0, 0.0]))},
        },
    }

    # ---- cast: 18 ticks, both arms push out, chin lifts ------------------- #
    anims["cast"] = {
        "loop": False,
        "animation_length": 0.9,
        "bones": {
            "body": {
                "rotation": kf(
                    (0.00, Z),
                    (0.22, [4.0, 7.0, 0.0]),
                    (0.45, [-7.0, 0.0, 0.0]),
                    (0.65, [-5.0, 0.0, 0.0]),
                    (0.90, Z),
                )
            },
            "head": {
                "rotation": kf(
                    (0.00, Z),
                    (0.22, [3.0, 0.0, 0.0]),
                    (0.45, [11.0, 0.0, 0.0]),
                    (0.65, [9.0, 0.0, 0.0]),
                    (0.90, Z),
                )
            },
            "cape": {"rotation": kf((0.00, Z), (0.45, [-8.0, 0.0, 2.0]), (0.90, Z))},
            "shoulder_r": {"rotation": kf((0.00, Z), (0.45, [6.0, 0.0, 4.0]), (0.90, Z))},
            "shoulder_l": {"rotation": kf((0.00, Z), (0.45, [6.0, 0.0, -4.0]), (0.90, Z))},
            "upper_arm_r": {
                "rotation": kf(
                    (0.00, Z),
                    (0.22, [-30.0, 0.0, -7.0]),
                    (0.45, [76.0, 0.0, -9.0]),
                    (0.65, [70.0, 0.0, -9.0]),
                    (0.90, Z),
                )
            },
            "upper_arm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.22, [-30.0, 0.0, 7.0]),
                    (0.45, [76.0, 0.0, 9.0]),
                    (0.65, [70.0, 0.0, 9.0]),
                    (0.90, Z),
                )
            },
            "forearm_r": {
                "rotation": kf(
                    (0.00, Z),
                    (0.25, [-24.0, 0.0, 0.0]),
                    (0.45, [-6.0, 0.0, -14.0]),
                    (0.65, [-9.0, 0.0, -14.0]),
                    (0.90, Z),
                )
            },
            "forearm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.25, [-24.0, 0.0, 0.0]),
                    (0.45, [-6.0, 0.0, 14.0]),
                    (0.65, [-9.0, 0.0, 14.0]),
                    (0.90, Z),
                )
            },
            "thigh_r": {"rotation": kf((0.00, Z), (0.45, [-5.0, 0.0, 0.0]), (0.90, Z))},
            "shin_r": {"rotation": kf((0.00, Z), (0.45, [7.0, 0.0, 0.0]), (0.90, Z))},
            "thigh_l": {"rotation": kf((0.00, Z), (0.45, [5.0, 0.0, 0.0]), (0.90, Z))},
            "shin_l": {"rotation": kf((0.00, Z), (0.45, [-6.0, 0.0, 0.0]), (0.90, Z))},
            "skirt_f": {"rotation": kf((0.00, Z), (0.45, [-5.0, 0.0, 0.0]), (0.90, Z))},
            "skirt_b": {"rotation": kf((0.00, Z), (0.45, [5.0, 0.0, 0.0]), (0.90, Z))},
        },
    }

    # ---- greet: 20 ticks, bow with the eyes coming back up --------------- #
    anims["greet"] = {
        "loop": False,
        "animation_length": 1.0,
        "bones": {
            "body": {
                "rotation": kf(
                    (0.00, Z),
                    (0.25, [-12.0, 0.0, 0.0]),
                    (0.50, [-22.0, 0.0, 0.0]),
                    (0.75, [-10.0, 0.0, 0.0]),
                    (1.00, Z),
                )
            },
            "head": {
                "rotation": kf(
                    (0.00, Z),
                    (0.25, [10.0, 0.0, 0.0]),
                    (0.50, [22.0, 3.0, 0.0]),
                    (0.75, [8.0, 0.0, 0.0]),
                    (1.00, Z),
                )
            },
            "hat": {"rotation": kf((0.00, Z), (0.50, [-4.0, 0.0, 0.0]), (1.00, Z))},
            "cape": {"rotation": kf((0.00, Z), (0.50, [5.0, 0.0, 0.0]), (1.00, Z))},
            "upper_arm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.30, [-18.0, 0.0, 14.0]),
                    (0.60, [-22.0, 0.0, 16.0]),
                    (1.00, Z),
                )
            },
            "forearm_l": {
                "rotation": kf(
                    (0.00, Z),
                    (0.32, [-30.0, 12.0, 0.0]),
                    (0.62, [-34.0, 14.0, 0.0]),
                    (1.00, Z),
                )
            },
            "upper_arm_r": {
                "rotation": kf((0.00, Z), (0.35, [6.0, 0.0, -6.0]), (0.60, [8.0, 0.0, -8.0]), (1.00, Z))
            },
            "forearm_r": {"rotation": kf((0.00, Z), (0.35, [-12.0, 0.0, 0.0]), (1.00, Z))},
            "skirt_f": {"rotation": kf((0.00, Z), (0.50, [-3.0, 0.0, 0.0]), (1.00, Z))},
            "skirt_b": {"rotation": kf((0.00, Z), (0.50, [3.0, 0.0, 0.0]), (1.00, Z))},
        },
    }

    # ---- death: 20 ticks, pitch forward, thighs flex, shins fold --------- #
    anims["death"] = {
        "loop": False,
        "animation_length": 1.0,
        "bones": {
            "root": {"rotation": kf((0.00, Z), (0.55, [-6.0, 0.0, 0.0]), (1.00, [-14.0, 0.0, 3.0]))},
            "body": {
                "rotation": kf(
                    (0.00, Z),
                    (0.25, [8.0, 0.0, 0.0]),
                    (0.55, [-30.0, 0.0, 4.0]),
                    (1.00, [-82.0, 0.0, 6.0]),
                )
            },
            "head": {"rotation": kf((0.00, Z), (0.25, [-6.0, 0.0, 0.0]), (1.00, [16.0, 6.0, -4.0]))},
            "hat": {"rotation": kf((0.00, Z), (1.00, [-10.0, 0.0, 4.0]))},
            "cape": {"rotation": kf((0.00, Z), (0.55, [8.0, 0.0, 0.0]), (1.00, [16.0, 0.0, -4.0]))},
            "shoulder_r": {"rotation": kf((0.00, Z), (1.00, [-8.0, 0.0, 8.0]))},
            "shoulder_l": {"rotation": kf((0.00, Z), (1.00, [-8.0, 0.0, -8.0]))},
            "upper_arm_r": {
                "rotation": kf((0.00, Z), (0.25, [-22.0, 0.0, 6.0]), (0.55, [-40.0, 0.0, 16.0]), (1.00, [-52.0, 0.0, 24.0]))
            },
            "forearm_r": {"rotation": kf((0.00, Z), (0.55, [-24.0, 0.0, 0.0]), (1.00, [-40.0, 0.0, 0.0]))},
            "upper_arm_l": {
                "rotation": kf((0.00, Z), (0.25, [-22.0, 0.0, -6.0]), (0.55, [-38.0, 0.0, -14.0]), (1.00, [-48.0, 0.0, -20.0]))
            },
            "forearm_l": {"rotation": kf((0.00, Z), (0.55, [-24.0, 0.0, 0.0]), (1.00, [-38.0, 0.0, 0.0]))},
            "thigh_r": {"rotation": kf((0.00, Z), (0.55, [20.0, 0.0, 0.0]), (1.00, [48.0, 0.0, 0.0]))},
            "shin_r": {"rotation": kf((0.00, Z), (0.55, [-30.0, 0.0, 0.0]), (1.00, [-72.0, 0.0, 0.0]))},
            "thigh_l": {"rotation": kf((0.00, Z), (0.55, [16.0, 0.0, 0.0]), (1.00, [36.0, 0.0, 0.0]))},
            "shin_l": {"rotation": kf((0.00, Z), (0.55, [-24.0, 0.0, 0.0]), (1.00, [-60.0, 0.0, 0.0]))},
            "skirt_f": {"rotation": kf((0.00, Z), (1.00, [-14.0, 0.0, 0.0]))},
            "skirt_b": {"rotation": kf((0.00, Z), (1.00, [10.0, 0.0, 0.0]))},
        },
    }

    return {"format_version": "1.8.0", "animations": anims}


# --------------------------------------------------------------------------- #
# writers
# --------------------------------------------------------------------------- #


def build_geometry():
    bones = []
    for spec in BONES:
        bone = {"name": spec["name"]}
        if spec["parent"]:
            bone["parent"] = spec["parent"]
        bone["pivot"] = list(spec["pivot"])
        cubes = []
        for c in spec.get("cubes", []):
            cube = {
                "origin": list(c["origin"]),
                "size": list(c["size"]),
                "uv": list(c["uv"]),
                "box_uv": True,
            }
            if "inflate" in c:
                cube["inflate"] = c["inflate"]
            if "rotation" in c:
                cube["rotation"] = list(c["rotation"])
                cube["pivot"] = list(c["pivot"])
            cubes.append(cube)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": GEOMETRY_ID,
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 3,
                    "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": bones,
            }
        ],
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


# --------------------------------------------------------------------------- #
# verification
# --------------------------------------------------------------------------- #


def _rect_key(rect):
    return "(%g,%g)-(%g,%g)" % rect


def verify(geo, anims, textures):
    ok = True
    report = []

    def check(cond, msg):
        nonlocal ok
        if not cond:
            ok = False
            report.append("FAIL  " + msg)
        else:
            report.append("ok    " + msg)

    # ---- geometry -------------------------------------------------------- #
    check(geo.get("format_version") == "1.12.0", 'format_version == "1.12.0"')
    geoms = geo.get("minecraft:geometry")
    check(isinstance(geoms, list) and len(geoms) == 1, "minecraft:geometry holds exactly one entry")
    desc = geoms[0]["description"]
    check(desc["identifier"] == GEOMETRY_ID, "identifier == %s" % GEOMETRY_ID)
    check(desc["texture_width"] == TEX_W and desc["texture_height"] == TEX_H,
          "description texture size == %dx%d" % (TEX_W, TEX_H))

    bones = geoms[0]["bones"]
    names = [b["name"] for b in bones]
    check(names == BONE_NAMES, "bone list matches the required skeleton (order included)")
    check(len(names) == len(set(names)), "bone names are unique")

    parents = {b["name"]: b.get("parent") for b in bones}
    check(parents["root"] is None, "root has no parent")
    expected_tree = {
        "body": "root",
        "head": "body",
        "hat": "head",
        "cape": "body",
        "shoulder_r": "body",
        "upper_arm_r": "shoulder_r",
        "forearm_r": "upper_arm_r",
        "shoulder_l": "body",
        "upper_arm_l": "shoulder_l",
        "forearm_l": "upper_arm_l",
        "thigh_r": "root",
        "shin_r": "thigh_r",
        "thigh_l": "root",
        "shin_l": "thigh_l",
        "skirt_f": "body",
        "skirt_b": "body",
    }
    bad_parent = [n for n, p in expected_tree.items() if parents.get(n) != p]
    check(not bad_parent, "bone hierarchy matches root->body->{...}/root->{legs}")

    # ---- uv occupancy ---------------------------------------------------- #
    occupies = []  # (rect, label)
    faces_all = ("up", "down", "north", "south", "east", "west")
    report.append("")
    report.append("UV occupancy (texture %dx%d, box UV):" % (TEX_W, TEX_H))
    for bone in bones:
        for i, cube in enumerate(bone.get("cubes", [])):
            foot = box_uv_footprint(cube["uv"], cube["size"])
            occupies.append((foot, "%s#%d" % (bone["name"], i)))
    in_bounds = all(r[0] >= 0 and r[1] >= 0 and r[2] <= TEX_W and r[3] <= TEX_H for r, _ in occupies)
    check(in_bounds, "every box-UV footprint stays inside %dx%d" % (TEX_W, TEX_H))

    for rect, label in occupies:
        report.append("      %-16s footprint %-18s %5.0fx%-5.0f" % (
            label, _rect_key(rect), rect[2] - rect[0], rect[3] - rect[1]))

    overlaps = []
    for i in range(len(occupies)):
        for j in range(i + 1, len(occupies)):
            a, b = occupies[i][0], occupies[j][0]
            if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                overlaps.append((occupies[i][1], occupies[j][1]))
    check(not overlaps, "box-UV footprints do not overlap%s" % (
        "" if not overlaps else ": " + repr(overlaps)))

    per_face_ok = True
    for bone in bones:
        for cube in bone.get("cubes", []):
            for f, r in box_uv_rects(cube["uv"], cube["size"]).items():
                if not (0 <= r[0] and 0 <= r[1] and r[2] <= TEX_W and r[3] <= TEX_H):
                    per_face_ok = False
    check(per_face_ok, "every individual face rect stays inside the texture")

    # ---- animations ------------------------------------------------------ #
    anims_data = anims["animations"]
    check(sorted(anims_data) == sorted(["idle", "attack", "block", "cast", "greet", "death"]),
          "six animations present: idle/attack/block/cast/greet/death")

    animated = set()
    frames_ok = True
    for name, anim in anims_data.items():
        if "loop" not in anim or not isinstance(anim["loop"], bool):
            frames_ok = False
        for bone, chans in anim["bones"].items():
            animated.add(bone)
            for chan, keys in chans.items():
                if chan not in ("rotation", "position", "scale"):
                    frames_ok = False
                for t, val in keys.items():
                    if len(val) != 3 or any(isinstance(v, str) for v in val):
                        frames_ok = False
                    if float(t) > anim["animation_length"] + 1e-6:
                        frames_ok = False
    check(frames_ok, "all keyframes are constant triples inside animation_length, loop is a bool")
    check(animated == set(names),
          "animated bone names exactly match the geometry bones (%d bones)" % len(names))

    # t-pose sanity: head above body, arms outside the torso, feet on y=0
    byname = {b["name"]: b for b in bones}
    head_cube = byname["head"]["cubes"][0]
    body_cube = byname["body"]["cubes"][0]
    check(head_cube["origin"][1] + head_cube["size"][1] == 32, "crown height == 32 px (~2.0 blocks)")
    check(body_cube["origin"][1] == 12, "hip line at y=12")
    feet = [byname["shin_r"]["cubes"][0], byname["shin_l"]["cubes"][0]]
    check(all(c["origin"][1] == 0 for c in feet), "both shins reach y=0")
    check(byname["upper_arm_r"]["cubes"][0]["origin"][0] == -8 and
          byname["upper_arm_l"]["cubes"][0]["origin"][0] + 4 == 8,
          "shoulder span matches self.geo (x -8..8)")

    # joints must overlap by 1 px so a bent knee/elbow cannot open a gap
    thigh = byname["thigh_r"]["cubes"][0]
    shin = byname["shin_r"]["cubes"][0]
    check(thigh["origin"][1] < byname["shin_r"]["pivot"][1] <= thigh["origin"][1] + thigh["size"][1],
          "knee: thigh covers the shin top by 1 px (knee at y=%g)" % byname["shin_r"]["pivot"][1])
    fore = byname["forearm_r"]["cubes"][0]
    check(fore["origin"][1] + fore["size"][1] > byname["forearm_r"]["pivot"][1],
          "elbow: forearm reaches 1 px into the sleeve (elbow at y=%g)" % byname["forearm_r"]["pivot"][1])

    # tick counts at 20 fps
    expected_ticks = {"idle": 60, "attack": 14, "block": 20, "cast": 18, "greet": 20, "death": 20}
    tick_ok = all(round(anims_data[k]["animation_length"] * FPS) == v
                  for k, v in expected_ticks.items())
    check(tick_ok, "animation lengths equal 60/14/20/18/20/20 ticks at %d fps" % FPS)

    # ---- textures -------------------------------------------------------- #
    for v in VARIANTS:
        img = textures[v]
        check(img.size == (TEX_W, TEX_H) and img.mode == "RGBA",
              "%s is %dx%d RGBA" % (os.path.basename(TEX_FILES[v]), img.size[0], img.size[1]))

    return ok, report


# --------------------------------------------------------------------------- #
# previews
# --------------------------------------------------------------------------- #


def head_front_rect():
    head = next(b for b in BONES if b["name"] == "head")
    cube = head["cubes"][0]
    return box_uv_rects(cube["uv"], cube["size"])["north"]


def build_previews(textures):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    rect = head_front_rect()
    zoom = 10
    fw, fh = rect[2] - rect[0], rect[3] - rect[1]
    tiles = [textures[v].crop(rect).resize((fw * zoom, fh * zoom), Image.NEAREST) for v in VARIANTS]

    gap, pad, label_h = 12, 12, 18
    width = pad * 2 + len(tiles) * fw * zoom + gap * (len(tiles) - 1)
    height = pad * 2 + 18 + fh * zoom + label_h
    canvas = Image.new("RGBA", (width, height), (26, 28, 32, 255))
    draw = ImageDraw.Draw(canvas)
    draw.text((pad, 6), "instructor - head front (uv %s, x%d)" % (
        _rect_key(rect), zoom), fill=(228, 232, 238, 255))
    for i, (v, tile) in enumerate(zip(VARIANTS, tiles)):
        x = pad + i * (fw * zoom + gap)
        y = pad + 18
        canvas.alpha_composite(tile, (x, y))
        draw.rectangle([x - 1, y - 1, x + tile.width, y + tile.height], outline=(70, 76, 84, 255))
        draw.text((x, y + tile.height + 3), v, fill=(190, 198, 208, 255))
    face_path = os.path.join(PREVIEW_DIR, "face-compare.png")
    canvas.save(face_path, format="PNG")

    # full 128x64 sheets, 3x, stacked
    scale = 3
    sheet_w = TEX_W * scale
    sheet_h = TEX_H * scale
    stack = Image.new("RGBA", (pad * 2 + sheet_w, pad * 2 + len(VARIANTS) * (sheet_h + 14)), (26, 28, 32, 255))
    sdraw = ImageDraw.Draw(stack)
    for i, v in enumerate(VARIANTS):
        y = pad + i * (sheet_h + 14)
        up = textures[v].resize((sheet_w, sheet_h), Image.NEAREST)
        stack.alpha_composite(up, (pad, y))
        sdraw.rectangle([pad - 1, y - 1, pad + sheet_w, y + sheet_h], outline=(70, 76, 84, 255))
        sdraw.text((pad, y - 12), "%s  (%s)" % (v, PALETTES[v]["blurb"]), fill=(200, 208, 216, 255))
    sheet_path = os.path.join(PREVIEW_DIR, "texture-sheets.png")
    stack.save(sheet_path, format="PNG")
    return face_path, sheet_path


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #


def main():
    geo = build_geometry()
    anims = build_animations()
    write_json(GEO_PATH, geo)
    write_json(ANIM_PATH, anims)

    textures = {}
    os.makedirs(TEX_DIR, exist_ok=True)
    for v in VARIANTS:
        img = build_texture(v)
        textures[v] = img
        img.save(TEX_FILES[v], format="PNG")

    # reload from disk so the checks validate the files that were really written
    with open(GEO_PATH, encoding="utf-8") as fh:
        geo_disk = json.load(fh)
    with open(ANIM_PATH, encoding="utf-8") as fh:
        anim_disk = json.load(fh)
    tex_disk = {v: Image.open(TEX_FILES[v]).convert("RGBA") for v in VARIANTS}

    ok, report = verify(geo_disk, anim_disk, tex_disk)
    face_path, sheet_path = build_previews(tex_disk)

    print("== generated ==")
    for path in (GEO_PATH, ANIM_PATH) + tuple(TEX_FILES[v] for v in VARIANTS):
        print("   %6d B  %s" % (os.path.getsize(path), os.path.relpath(path, ROOT)))
    print()
    for line in report:
        print(line)
    print()
    print("previews: %s" % os.path.relpath(face_path, ROOT))
    print("          %s" % os.path.relpath(sheet_path, ROOT))
    print()
    print("RESULT: %s" % ("ALL CHECKS PASSED" if ok else "CHECKS FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
