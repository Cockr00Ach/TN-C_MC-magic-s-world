# -*- coding: utf-8 -*-
"""gen_dungeon_mobs.py -- 12 hand-painted dungeon minions: Bedrock geo + animations + textures.

WHY A GENERATOR AND NOT BLOCKBENCH
==================================
Twelve rigs x four animations each = 48 authored animation clips. Hand-keying them in
Blockbench means the attack keyframes drift away from the tick timings the Java goals use,
and the next balance pass has to redo them by hand. Everything here is derived from one table
(SPECS) so a timing change is a one-line edit that regenerates geo + clip + texture together.

WHAT IT WRITES (per mob id)
===========================
  assets/tnc/geo/entity/<id>.geo.json                 format_version 1.12.0, 1 unit = 1/16 block
  assets/tnc/animations/entity/<id>.animation.json    idle / walk / attack / special / death
  assets/tnc/textures/entity/<id>_bedrock.png         1024x1024, 4 material regions of 512

TEXTURE LAYOUT (shared by every mob so one painter works for all)
=================================================================
  region 0 (  0,   0, 512) main hide / plate / cloth
  region 1 (512,   0, 512) bone / steel / chitin
  region 2 (  0, 512, 512) accent (ember, frost, rune, glass)
  region 3 (512, 512, 512) shadow / underside / vent

Cubes reference a region by its top-left pixel, exactly like the dark-summon pipeline.

ASCII only in code; Chinese only in the generated doc.
"""
import json
import math
import os
import random
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

MODID = "tnc"
TEX = 1024
R_MAIN = (0, 0)
R_STEEL = (512, 0)
R_ACCENT = (0, 512)
R_DARK = (512, 512)

# ---------------------------------------------------------------------------
# species table -- id, archetype, palette, material motifs, animation flavour
# ---------------------------------------------------------------------------
SPECS = {
    "lantern_boar": dict(
        arch="quadruped", height=1.9, kind="charge", bones_extra=("lantern", "tusk"),
        pal=dict(main=(74, 58, 46), steel=(96, 104, 112), accent=(255, 186, 74), dark=(38, 30, 24)),
        motif="hide", accent_motif="lantern"),
    "rust_hook_butcher": dict(
        arch="biped", height=2.0, kind="hook", bones_extra=("hook", "apron"),
        pal=dict(main=(88, 44, 40), steel=(128, 92, 70), accent=(196, 122, 52), dark=(44, 26, 24)),
        motif="rust", accent_motif="ember"),
    "moss_carapace_spider": dict(
        arch="insect", height=1.5, kind="pounce", bones_extra=("abdomen",),
        pal=dict(main=(46, 62, 44), steel=(104, 96, 78), accent=(150, 208, 120), dark=(24, 32, 24)),
        motif="moss", accent_motif="venom"),
    "bone_crossbowman": dict(
        arch="biped", height=1.9, kind="volley", bones_extra=("crossbow", "quiver"),
        pal=dict(main=(168, 160, 142), steel=(120, 118, 112), accent=(210, 196, 150), dark=(52, 48, 44)),
        motif="bone", accent_motif="ember"),
    "ash_flinger": dict(
        arch="biped", height=1.7, kind="pot", bones_extra=("pot",),
        pal=dict(main=(74, 66, 62), steel=(112, 96, 90), accent=(236, 132, 58), dark=(34, 30, 28)),
        motif="ash_cloth", accent_motif="fire"),
    "copper_fusilier": dict(
        arch="biped", height=1.8, kind="beam", bones_extra=("rifle", "coil"),
        pal=dict(main=(58, 54, 62), steel=(178, 118, 72), accent=(120, 226, 236), dark=(30, 28, 34)),
        motif="leather", accent_motif="arc"),
    "sewer_crawler": dict(
        arch="insect", height=0.9, kind="burrow", bones_extra=("abdomen", "mandible"),
        pal=dict(main=(52, 66, 54), steel=(86, 92, 74), accent=(140, 176, 108), dark=(22, 30, 24)),
        motif="slime", accent_motif="venom"),
    "umbral_binder": dict(
        arch="biped", height=2.1, kind="bind", bones_extra=("chain", "hood", "robe"),
        pal=dict(main=(44, 36, 64), steel=(86, 74, 112), accent=(168, 110, 236), dark=(20, 16, 30)),
        motif="cloth", accent_motif="shadow"),
    "frost_weaver": dict(
        arch="insect", height=1.6, kind="frost", bones_extra=("abdomen",),
        pal=dict(main=(96, 128, 152), steel=(180, 208, 226), accent=(150, 226, 255), dark=(34, 48, 62)),
        motif="frost", accent_motif="ice"),
    "ash_monk": dict(
        arch="biped", height=2.0, kind="summon", bones_extra=("staff", "hood", "robe"),
        pal=dict(main=(112, 96, 78), steel=(96, 80, 64), accent=(255, 138, 46), dark=(40, 32, 26)),
        motif="ash_cloth", accent_motif="ember"),
    "steam_sentinel": dict(
        arch="mech", height=2.4, kind="overheat", bones_extra=("pistonL", "pistonR", "vent"),
        pal=dict(main=(104, 96, 88), steel=(186, 148, 96), accent=(255, 158, 64), dark=(44, 40, 36)),
        motif="brass", accent_motif="fire"),
    "grave_grub": dict(
        arch="grub", height=1.1, kind="detonate", bones_extra=("seg2", "seg3", "maw"),
        pal=dict(main=(122, 110, 96), steel=(96, 84, 74), accent=(198, 236, 96), dark=(38, 34, 30)),
        motif="decay", accent_motif="bile"),
    # ---- 经典地城三巨头 + 触手：这一批是"玩家一看就认识"的怪 ----
    "goblin_skirmisher": dict(
        arch="small_biped", height=1.55, kind="pounce", bones_extra=("dagger", "cowl", "satchel"),
        pal=dict(main=(96, 138, 78), steel=(112, 96, 66), accent=(214, 255, 122), dark=(30, 44, 30)),
        motif="hide", accent_motif="venom"),
    "kobold_miner": dict(
        arch="small_biped", height=1.55, kind="pot", bones_extra=("pickaxe", "candle", "pouch", "rope"),
        pal=dict(main=(156, 116, 74), steel=(104, 96, 88), accent=(255, 196, 92), dark=(44, 32, 24)),
        motif="hide", accent_motif="fire"),
    "orc_brute": dict(
        arch="bulky_biped", height=2.15, kind="whirl", bones_extra=("cleaver", "topknot", "loincloth"),
        pal=dict(main=(104, 118, 70), steel=(126, 122, 112), accent=(236, 96, 52), dark=(34, 40, 28)),
        motif="hide", accent_motif="ember"),
    "minotaur_warden": dict(
        arch="bull", height=2.35, kind="charge", bones_extra=("greataxe", "tail", "nosering"),
        pal=dict(main=(92, 70, 50), steel=(158, 124, 78), accent=(255, 178, 74), dark=(38, 30, 24)),
        motif="hide", accent_motif="ember"),
    "tentacle_horror": dict(
        arch="tentacle", height=1.45, kind="grab", bones_extra=("maw",),
        pal=dict(main=(112, 74, 104), steel=(96, 110, 96), accent=(164, 255, 176), dark=(36, 26, 40)),
        motif="slime", accent_motif="venom"),
}

ORDER = [
    "lantern_boar", "rust_hook_butcher", "moss_carapace_spider", "bone_crossbowman",
    "ash_flinger", "copper_fusilier", "sewer_crawler", "umbral_binder",
    "frost_weaver", "ash_monk", "steam_sentinel", "grave_grub",
    "goblin_skirmisher", "kobold_miner", "orc_brute", "minotaur_warden", "tentacle_horror",
]


# ---------------------------------------------------------------------------
# geometry helpers
# ---------------------------------------------------------------------------
# Everything below builds the same thing: axis-aligned cubes with a *material tag*.
# The tag is resolved into a real uv pair by pack_uv() at the very end, so each cube
# gets its own rectangle on the sheet. That is the difference between "one flat colour
# per mob" (what the first pass did: every cube sampled the same 512 block) and actually
# painted parts.
AREA = TEX // 2  # one material region is 512 x 512
REGION = {"main": R_MAIN, "steel": R_STEEL, "accent": R_ACCENT, "dark": R_DARK}


def cube(a, b, mat="main"):
    return {"origin": [a[0], a[1], a[2]],
            "size": [b[0] - a[0], b[1] - a[1], b[2] - a[2]],
            "mat": mat}


def box(cx, cy, cz, w, h, d, mat="main"):
    """centre-x/z, bottom-y box -- the convention the other generators use."""
    x0 = int(round(cx - w / 2.0))
    z0 = int(round(cz - d / 2.0))
    return cube((x0, int(round(cy)), z0), (x0 + int(w), int(round(cy)) + int(h), z0 + int(d)), mat)


def bevel(out, x0, y0, z0, w, h, d, mat, cut=1, edge=None):
    """Box with chamfered corners: 7 cubes, octagonal cross-section.

    One fat cube reads as a fridge; the chamfer is what makes a shoulder look like a
    shoulder at Minecraft's resolution. Used on torsos / skulls / shoulder pads only --
    cheap boxes everywhere else keeps the cube count sane.
    """
    edge = edge or mat
    c = max(1, int(cut))
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    # A bevel smaller than 2*cut would collapse its core into a zero-size cube (bedrock geo
    # rejects that). Cheap authoring mistake, so the helper degrades instead of dying.
    if w > 2 * c and h > 2 * c and d > 2 * c:
        out.append(cube((x0 + c, y0 + c, z0 + c), (x1 - c, y1 - c, z1 - c), mat))
    if w > 2 * c and d > 2 * c:
        out.append(cube((x0 + c, y1 - c, z0 + c), (x1 - c, y1, z1 - c), mat))       # top cap
        out.append(cube((x0 + c, y0, z0 + c), (x1 - c, y0 + c, z1 - c), edge))      # bottom cap
    for px in (x0, x1 - c):
        for pz in (z0, z1 - c):
            out.append(cube((px, y0 + c, pz), (px + c, y1 - c, pz + c), edge))      # corner posts
    if h > 2 * c:
        out.append(cube((x0 + c, y0 + c, z0), (x1 - c, y1 - c, z0 + c), mat))       # front rail
        out.append(cube((x0 + c, y0 + c, z1 - c), (x1 - c, y1 - c, z1), mat))       # back rail
        out.append(cube((x0, y0 + c, z0 + c), (x0 + c, y1 - c, z1 - c), mat))       # left rail
        out.append(cube((x1 - c, y0 + c, z0 + c), (x1, y1 - c, z1 - c), mat))       # right rail


def chain(out, x, y0, z, w, d, h, n, mat, dx=0.0, dz=0.0, taper=0.74, alt=None):
    """Stack of tapered segments -- a limb that narrows toward the end.

    ``dx``/``dz`` drift each segment so a leg can bend without a second bone.
    """
    sh = h / float(n)
    for i in range(n):
        t = i / max(1.0, n - 1.0)
        ww = max(2, int(round(w * (1 - (1 - taper) * t))))
        dd = max(2, int(round(d * (1 - (1 - taper) * t))))
        out.append(box(x + dx * i, int(round(y0 + sh * i)), z + dz * i,
                       ww, max(1, int(round(sh)) + 1), dd,
                       mat if (alt is None or i % 2 == 0) else alt))


def plate(out, x0, y0, z0, w, h, d, mat):
    """Thin slab: armour plate, strap, belt, lens. Always 1 unit thick on its short axis."""
    out.append(cube((x0, y0, z0), (x0 + w, y0 + h, z0 + d), mat))


def studs(out, x0, y0, z0, w, h, d, count, mat, axis="z"):
    """A row of rivets/studs -- the cheapest way to say 'forged, not extruded'."""
    for i in range(count):
        t = (i + 0.5) / count
        if axis == "z":
            studs.__dict__  # noqa: B018  (keeps the intent obvious at call sites)
            out.append(box(x0 + w * t, y0, z0, 1, 1, d, mat))
        elif axis == "x":
            out.append(box(x0, y0, z0 + d * t, w, 1, 1, mat))
        else:
            out.append(box(x0 + w * t, y0, z0, 1, h, 1, mat))


def fingers(out, x, y, z, mat, n=3, spread=1.2, forward=-1):
    for i in range(n):
        out.append(box(x + (i - (n - 1) / 2.0) * spread, y, z + forward * 1.0, 1, 2, 2, mat))


def teeth(out, x, y, z, w, n, mat, forward=-1):
    step = w / float(n)
    for i in range(n):
        out.append(box(x - w / 2.0 + step * (i + 0.5), y, z + forward * 1.0,
                       max(1, int(step * 0.7)), 1, 1, mat))


def eyes(out, x, y, z, spread, mat, size=2):
    for sx in (-1, 1):
        out.append(box(x + sx * spread, y, z, size, size, 1, mat))


# ---------------------------------------------------------------------------
# rigs -- 5 archetypes, each ~50-90 cubes
# ---------------------------------------------------------------------------
def rig_biped(extra):
    """head / body / armL / armR / legL / legR + optional props."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    # ---- legs: thigh bone + knee bone so the knee can bend in the walk clip
    for side, sx in (("L", 3), ("R", -3)):
        thigh = []
        chain(thigh, sx, 6, 0, 5, 5, 6, 2, "main", taper=0.9)
        plate(thigh, sx - 2, 6, -2, 5, 1, 5, "steel")                     # knee cap
        b.append({"name": "leg" + side, "parent": "root", "pivot": [sx, 12, 0], "cubes": thigh})
        shin = []
        chain(shin, sx, 2, 0, 4, 4, 4, 2, "main", taper=0.92)
        plate(shin, sx - 2, 1, -3, 5, 2, 6, "steel")                      # boot
        plate(shin, sx - 2, 0, -4, 5, 1, 2, "dark")                       # sole
        for t in (-1, 1):
            shin.append(box(sx + t * 1.5, 0, -4, 1, 1, 2, "steel"))       # toe caps
        b.append({"name": "leg" + side + "2", "parent": "leg" + side,
                  "pivot": [sx, 6, 0], "cubes": shin})

    # ---- torso: chest bevel + waist + belt + back plate + spine + strap + pouch
    c = []
    bevel(c, -5, 13, -4, 11, 11, 8, "main", cut=1, edge="dark")
    bevel(c, -4, 11, -3, 9, 3, 7, "main", cut=1, edge="dark")          # waist
    plate(c, -6, 13, -4, 12, 2, 9, "steel")                             # belt
    plate(c, -1, 12, -5, 3, 3, 1, "accent")                             # buckle
    plate(c, -5, 14, 4, 10, 9, 1, "dark")                               # back plate
    for i in range(3):
        plate(c, -1, 15 + i * 3, 4, 2, 2, 1, "steel")                   # spine studs
    for t in (-1, 1):
        plate(c, t * 3, 15, -5, 2, 8, 1, "dark")                        # chest strap
    plate(c, 4, 12, -4, 3, 4, 2, "dark")                                # hip pouch
    plate(c, 4, 12, -5, 2, 1, 1, "accent")                              # pouch clasp
    b.append({"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": c})

    # ---- head: skull + brow + snout + jaw + eyes + teeth + ears
    c = []
    bevel(c, -4, 25, -4, 8, 8, 8, "main", cut=1, edge="dark")
    plate(c, -4, 31, -4, 8, 2, 2, "dark")                               # brow
    plate(c, -3, 26, -6, 6, 4, 2, "main")                               # snout
    plate(c, -3, 25, -6, 6, 1, 2, "steel")                              # jaw line
    eyes(c, 0, 29, -5, 2, "accent", size=2)
    teeth(c, 0, 25, -6, 4, 4, "steel")
    for t in (-1, 1):
        plate(c, t * 4, 30, 1, 1, 3, 2, "dark")                         # ear
    plate(c, -2, 22, -2, 4, 3, 4, "dark")                               # neck
    b.append({"name": "head", "parent": "body", "pivot": [0, 25, 0], "cubes": c})

    # ---- arms: shoulder pad + upper + elbow + forearm + hand + fingers
    for side, sx in (("L", 7), ("R", -7)):
        c = []
        bevel(c, int(sx - 3), 21, -3, 6, 5, 6, "steel", cut=1, edge="dark")   # pauldron
        chain(c, sx, 13, 0, 4, 4, 9, 3, "main", taper=0.88)
        plate(c, int(sx - 2), 11, -2, 4, 2, 4, "steel")                       # elbow cuff
        plate(c, int(sx - 2), 9, -2, 5, 3, 5, "main")                         # hand
        fingers(c, sx, 7, -2, "steel")
        b.append({"name": "arm" + side, "parent": "body", "pivot": [sx, 24, 0], "cubes": c})

    # ---- props
    if "hood" in extra:
        c = []
        bevel(c, -5, 30, -5, 10, 9, 10, "dark", cut=1, edge="dark")
        plate(c, -3, 32, -7, 6, 6, 2, "dark")                            # hood opening
        plate(c, -2, 34, -8, 4, 3, 1, "accent")                          # inner glow
        for t in (-1, 1):
            plate(c, t * 5, 24, 3, 1, 7, 6, "dark")                      # side drape
        b.append({"name": "hood", "parent": "head", "pivot": [0, 33, 0], "cubes": c})
    if "robe" in extra:
        c = []
        for i in range(5):
            wdt = 13 - i
            plate(c, -(wdt // 2), 1 + i * 3, -5 + i, wdt, 4, 10 - i * 2, "dark")
        plate(c, -7, 0, -6, 14, 1, 12, "dark")                           # hem
        b.append({"name": "robe", "parent": "body", "pivot": [0, 12, 0], "cubes": c})
    if "apron" in extra:
        c = [box(0, 3, 5, 11, 10, 2, "dark")]
        for i in range(4):
            plate(c, -5, 4 + i * 2, 5, 10, 1, 1, "accent")               # apron stains
        b.append({"name": "apron", "parent": "body", "pivot": [0, 12, 5], "cubes": c})
    if "crossbow" in extra:
        c = [box(-9, 11, -6, 4, 2, 10, "steel")]
        for t in (-1, 1):
            plate(c, -9 + t * 5, 11, -9, 2, 2, 12, "steel")              # limbs
            plate(c, -9 + t * 5, 12, -9, 1, 1, 3, "dark")                # tips
        plate(c, -10, 12, -8, 2, 1, 14, "accent")                        # string
        plate(c, -10, 10, 0, 2, 3, 2, "dark")                            # stock
        b.append({"name": "crossbow", "parent": "armR", "pivot": [-9, 12, -4], "cubes": c})
    if "quiver" in extra:
        c = [box(3, 14, 5, 4, 9, 3, "steel")]
        for i in range(3):
            plate(c, 2 + i, 23, 5, 1, 3, 1, "accent")                    # arrow tails
        plate(c, 2, 15, 4, 6, 1, 1, "dark")
        b.append({"name": "quiver", "parent": "body", "pivot": [0, 20, 6], "cubes": c})
    if "pot" in extra:
        c = []
        bevel(c, -13, 9, -8, 7, 7, 7, "dark", cut=1, edge="steel")
        plate(c, -13, 14, -8, 7, 1, 7, "accent")                         # lid ember
        plate(c, -11, 16, -6, 3, 2, 3, "dark")                           # handle
        b.append({"name": "pot", "parent": "armR", "pivot": [-10, 12, -5], "cubes": c})
    if "rifle" in extra:
        c = [box(-9, 11, -8, 3, 3, 12, "steel")]
        c.append(box(-9, 12, -16, 2, 2, 6, "steel"))                     # barrel
        plate(c, -10, 11, -6, 5, 3, 3, "dark")                           # receiver
        plate(c, -8, 14, -9, 1, 1, 3, "accent")                          # sight
        plate(c, -10, 9, -2, 4, 2, 4, "dark")                            # stock
        b.append({"name": "rifle", "parent": "armR", "pivot": [-9, 12, -6], "cubes": c})
    if "coil" in extra:
        c = []
        for i in range(3):
            plate(c, -4, 15 + i * 3, 5, 8, 1, 2, "steel")                # coil rings
        plate(c, -1, 15, 4, 2, 10, 2, "accent")                          # plasma core
        b.append({"name": "coil", "parent": "body", "pivot": [0, 22, 6], "cubes": c})
    if "chain" in extra:
        c = []
        for i in range(6):
            off = 1 if i % 2 else 0
            c.append(box(11 + off, 8 - i * 1.4, 0, 2, 2, 2, "steel"))
        plate(c, 11, 0, -1, 3, 3, 3, "accent")                           # weighted end
        b.append({"name": "chain", "parent": "armL", "pivot": [10, 13, 0], "cubes": c})
    if "staff" in extra:
        c = [box(-9, 2, -4, 2, 22, 2, "steel")]
        for i in range(4):
            plate(c, -10, 4 + i * 5, -5, 4, 1, 4, "dark")                # binding rings
        bevel(c, -11, 24, -6, 5, 5, 5, "accent", cut=1, edge="steel")    # head crystal
        b.append({"name": "staff", "parent": "armR", "pivot": [-9, 20, -3], "cubes": c})
    if "hook" in extra:
        c = [box(-10, 3, -9, 2, 10, 2, "steel")]
        plate(c, -11, 0, -11, 4, 2, 5, "steel")                          # hook blade
        plate(c, -11, 2, -12, 2, 2, 3, "dark")                           # barb
        b.append({"name": "hook", "parent": "armR", "pivot": [-10, 12, -8], "cubes": c})
    return b


def rig_quadruped(extra):
    """boar: barrel body, four stocky legs, tusked head, hanging lantern."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    for name, sx, sz in (("FL", 5, -6), ("FR", -5, -6), ("BL", 5, 6), ("BR", -5, 6)):
        c = []
        chain(c, sx, 1, sz, 5, 5, 8, 3, "main", taper=0.8)
        plate(c, sx - 2, 0, sz - 3, 5, 2, 6, "steel")                     # hoof
        for t in (-1, 1):
            c.append(box(sx + t * 1.5, 0, sz - 4, 1, 1, 2, "dark"))       # toes
        b.append({"name": "leg" + name, "parent": "root", "pivot": [sx, 9, sz], "cubes": c})

    c = []
    # barrel in three tapering masses: a single 12x10x20 block reads as a fridge
    bevel(c, -7, 15, -9, 14, 5, 9, "main", cut=1, edge="dark")            # upper chest
    bevel(c, -7, 9, -9, 14, 6, 9, "main", cut=1, edge="dark")             # lower chest
    bevel(c, -6, 9, 0, 13, 10, 5, "main", cut=1, edge="dark")             # ribs
    bevel(c, -5, 9, 5, 10, 9, 4, "main", cut=1, edge="dark")              # rump
    bevel(c, -4, 16, -5, 9, 3, 9, "main", cut=1, edge="steel")            # shoulder hump
    plate(c, -5, 7, -7, 10, 2, 14, "dark")                                # belly
    for i in range(3):
        plate(c, -1, 19, -4 + i * 3, 2, 2, 2, "steel")                    # bristles on the hump
    for t in (-1, 1):
        plate(c, t * 6, 10, -6, 1, 7, 12, "dark")                         # flank shading
    plate(c, -3, 10, 9, 6, 6, 2, "steel")                                 # rump plate
    b.append({"name": "body", "parent": "root", "pivot": [0, 9, 0], "cubes": c})

    c = []
    bevel(c, -4, 10, -16, 9, 8, 8, "main", cut=1, edge="dark")            # skull
    plate(c, -3, 11, -19, 6, 5, 3, "main")                                # snout
    plate(c, -3, 13, -20, 6, 1, 1, "steel")                               # nose ring
    plate(c, -2, 9, -18, 4, 2, 2, "dark")                                 # jowl
    eyes(c, 0, 15, -16, 2, "accent", size=2)
    for t in (-1, 1):
        plate(c, t * 4, 17, -13, 1, 3, 3, "dark")                         # ear
        teeth(c, t * 2, 9, -19, 2, 2, "steel")
    b.append({"name": "head", "parent": "body", "pivot": [0, 14, -10], "cubes": c})

    c = [box(0, 8, -16, 6, 2, 4, "steel")]                                # jaw
    for t in (-1, 1):
        plate(c, t * 3, 8, -19, 1, 1, 3, "accent")                        # drool glow
    b.append({"name": "jaw", "parent": "head", "pivot": [0, 10, -15], "cubes": c})

    c = []
    chain(c, 0, 11, 10, 4, 4, 4, 2, "dark", dz=1, taper=0.8)
    plate(c, -1, 12, 13, 3, 3, 3, "steel")                                # tuft
    b.append({"name": "tail", "parent": "body", "pivot": [0, 14, 10], "cubes": c})

    if "tusk" in extra:
        c = []
        for t in (-1, 1):
            c.append(box(t * 3, 6, -18, 2, 4, 2, "steel"))
            c.append(box(t * 3, 5, -19, 1, 2, 2, "steel"))                # curved tip
        b.append({"name": "tusk", "parent": "head", "pivot": [0, 10, -16], "cubes": c})
    if "lantern" in extra:
        c = []
        plate(c, -1, 18, -14, 2, 3, 2, "steel")                           # hanger
        bevel(c, -2, 14, -15, 5, 5, 5, "accent", cut=1, edge="steel")     # glass box
        plate(c, -3, 19, -14, 7, 1, 3, "steel")                           # cap
        plate(c, -1, 15, -16, 2, 3, 1, "accent")                          # glowing pane
        b.append({"name": "lantern", "parent": "head", "pivot": [0, 20, -12], "cubes": c})
    return b


def rig_insect(extra):
    """spider / crawler: plated thorax, ringed abdomen, six jointed legs."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    c = []
    bevel(c, -5, 4, -6, 10, 6, 13, "main", cut=1, edge="dark")            # thorax
    plate(c, -4, 9, -5, 8, 1, 10, "steel")                                # carapace top
    for t in (-1, 1):
        plate(c, t * 4, 5, -5, 1, 4, 11, "dark")                          # side plates
    for i in range(3):
        plate(c, -1, 10, -5 + i * 4, 2, 1, 3, "steel")                    # dorsal studs
    for t in (-1, 1):
        plate(c, t * 3, 9, -6, 2, 1, 2, "accent")                         # shoulder sigil
    b.append({"name": "body", "parent": "root", "pivot": [0, 5, 0], "cubes": c})

    c = []
    bevel(c, -4, 3, -13, 9, 7, 6, "main", cut=1, edge="dark")             # head shell
    for t in (-1, 1):
        eyes(c, t * 1, 7, -14, 1, "accent", size=2)                       # twin eye rows
        plate(c, t * 3, 4, -13, 1, 3, 2, "dark")
    plate(c, -3, 8, -11, 6, 1, 3, "steel")                                # brow ridge
    b.append({"name": "head", "parent": "body", "pivot": [0, 6, -8], "cubes": c})

    c = []
    for i in range(4):
        wdt = 13 - i * 2
        bevel(c, -(wdt // 2), 3 + i, 7 + i * 3, wdt, 9 - i, 4, "dark", cut=1, edge="main")
        plate(c, -(wdt // 2) + 1, 10 - i, 8 + i * 3, wdt - 2, 1, 2, "steel")   # dorsal ridge
        plate(c, -(wdt // 2), 4 + i, 7 + i * 3, 1, 3, 3, "accent")             # flank bristle
        plate(c, (wdt // 2) - 1, 4 + i, 7 + i * 3, 1, 3, 3, "accent")
    plate(c, -1, 4, 19, 3, 3, 3, "steel")                                 # spinneret
    b.append({"name": "abdomen", "parent": "body", "pivot": [0, 5, 7], "cubes": c})

    for i, zz in enumerate([-6, -1, 4]):
        for side, sx in (("L", 6), ("R", -6)):
            c = []
            s = 1 if sx > 0 else -1
            c.append(box(sx + s * 1, 5, zz, 3, 2, 2, "steel"))       # coxa
            c.append(box(sx + s * 4, 5, zz, 4, 2, 2, "main"))        # femur, outwards
            c.append(box(sx + s * 7, 2, zz, 2, 4, 2, "steel"))       # tibia, down to the knee
            c.append(box(sx + s * 8, 0, zz, 2, 2, 3, "dark"))        # foot on the floor
            plate(c, sx + s * 5, 7, zz, 2, 1, 2, "accent")           # joint spike
            c.append(box(sx + s * 6, 3, zz, 1, 2, 1, "dark"))        # tibia barb
            c.append(box(sx + s * 4, 3, zz, 1, 2, 1, "dark"))        # femur barb
            b.append({"name": "leg%s%d" % (side, i + 1), "parent": "body",
                      "pivot": [sx, 5, zz], "cubes": c})

    if "mandible" in extra:
        c = []
        for t in (-1, 1):
            c.append(box(t * 2, 2, -14, 2, 2, 4, "steel"))
            c.append(box(t * 2, 1, -16, 1, 1, 2, "accent"))               # fang tip
        b.append({"name": "mandible", "parent": "head", "pivot": [0, 4, -11], "cubes": c})
    return b


def rig_mech(extra):
    """armoured shell: plated torso, piston arms, louvred vent, glowing core."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    for side, sx in (("L", 4), ("R", -4)):
        thigh = []
        chain(thigh, sx, 7, 0, 6, 6, 6, 2, "main", taper=0.9)
        plate(thigh, sx - 3, 6, -3, 7, 1, 7, "steel")                      # knee ring
        b.append({"name": "leg" + side, "parent": "root", "pivot": [sx, 13, 0], "cubes": thigh})
        shin = []
        chain(shin, sx, 2, 0, 6, 6, 5, 2, "main", taper=0.9)
        plate(shin, sx - 3, 0, -4, 8, 3, 9, "steel")                       # foot plate
        for t in (-1, 1):
            plate(shin, sx + t * 3, 1, -2, 1, 3, 5, "dark")                # ankle brace
        b.append({"name": "leg" + side + "2", "parent": "leg" + side,
                  "pivot": [sx, 7, 0], "cubes": shin})

    c = []
    bevel(c, -7, 19, -5, 15, 7, 11, "main", cut=1, edge="dark")            # upper chest
    bevel(c, -6, 13, -4, 13, 6, 9, "main", cut=1, edge="dark")             # abdomen block
    plate(c, -6, 18, -6, 13, 6, 1, "steel")                                # front plate
    plate(c, -2, 19, -7, 5, 4, 1, "accent")                                # core window
    plate(c, -7, 15, 6, 14, 7, 1, "dark")                                  # back plate
    for i in range(4):
        plate(c, -4, 16 + i * 2, 7, 9, 1, 1, "steel")                      # back louvres
    for t in (-1, 1):
        plate(c, t * 7, 14, -4, 1, 10, 8, "dark")                          # side skirt
        plate(c, t * 5, 25, -4, 2, 2, 8, "steel")                          # shoulder rail
    for k in range(4):
        plate(c, -5 + k * 3, 19, -7, 1, 1, 1, "steel")                     # chest rivets
    for t in (-1, 1):
        plate(c, t * 5, 22, 6, 2, 8, 1, "steel")                           # back pipes
    b.append({"name": "body", "parent": "root", "pivot": [0, 13, 0], "cubes": c})

    c = []
    bevel(c, -4, 26, -4, 9, 7, 9, "steel", cut=1, edge="dark")             # helmet
    plate(c, -3, 29, -6, 7, 2, 2, "accent")                                # visor
    plate(c, -2, 26, -5, 5, 1, 1, "dark")                                  # chin vent
    for t in (-1, 1):
        plate(c, t * 4, 30, -2, 1, 3, 4, "dark")                           # cheek guard
    b.append({"name": "head", "parent": "body", "pivot": [0, 27, 0], "cubes": c})

    for side, sx in (("L", 9), ("R", -9)):
        c = []
        bevel(c, int(sx - 3), 21, -3, 7, 6, 7, "steel", cut=1, edge="dark")  # pauldron
        for k in range(2):
            plate(c, int(sx - 2 + k * 3), 22, -4, 1, 3, 1, "accent")         # pauldron rivets
        chain(c, sx, 12, 0, 5, 5, 8, 2, "main", taper=0.9)
        plate(c, int(sx - 2), 9, -3, 5, 4, 6, "steel")                        # gauntlet
        for t in (-1, 1):
            plate(c, int(sx + t * 2), 9, -4, 1, 3, 2, "dark")                 # knuckle
        b.append({"name": "arm" + side, "parent": "body", "pivot": [sx, 26, 0], "cubes": c})

    c = []
    bevel(c, -3, 20, 6, 7, 7, 4, "dark", cut=1, edge="steel")              # vent housing
    for i in range(3):
        plate(c, -2, 21 + i * 2, 9, 5, 1, 1, "accent")                     # glow slats
    b.append({"name": "vent", "parent": "body", "pivot": [0, 22, 7], "cubes": c})

    for side, sx in (("L", 11), ("R", -11)):
        c = []
        chain(c, sx, 4, 0, 4, 4, 6, 2, "steel", taper=0.9)                 # piston rod
        plate(c, int(sx - 1), 9, -2, 4, 3, 4, "dark")                      # coupling
        b.append({"name": "piston" + side, "parent": "arm" + side,
                  "pivot": [sx, 10, 0], "cubes": c})
    return b


def rig_grub(extra):
    """segmented worm: plated head, ring segments, stubby legs, toothy maw."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    c = []
    bevel(c, -5, 3, -14, 11, 9, 7, "main", cut=1, edge="dark")             # head shell
    plate(c, -4, 11, -13, 8, 1, 5, "steel")                                # cranial plate
    for t in (-1, 1):
        eyes(c, t * 1, 8, -15, 1, "accent", size=2)
        plate(c, t * 4, 4, -13, 1, 5, 2, "dark")
    b.append({"name": "head", "parent": "root", "pivot": [0, 6, -9], "cubes": c})

    c = []
    plate(c, -3, 2, -17, 7, 5, 3, "dark")                                  # maw
    teeth(c, 0, 2, -18, 6, 5, "steel")
    plate(c, -2, 5, -18, 4, 2, 1, "accent")                                # bile glow
    b.append({"name": "maw", "parent": "head", "pivot": [0, 4, -14], "cubes": c})

    c = []
    for i in range(3):
        wdt = 13 - i
        bevel(c, -(wdt // 2), 3 + i, -6 + i * 4, wdt, 10 - i, 5, "main", cut=1, edge="dark")
        for k in range(3):
            plate(c, -(wdt // 2) + 2 + k * 3, 12 - i, -5 + i * 4, 1, 1, 3, "steel")  # rib spines
    plate(c, -6, 6, -6, 1, 6, 10, "dark")
    plate(c, 6, 6, -6, 1, 6, 10, "dark")
    for i in range(4):
        plate(c, -1, 13, -7 + i * 3, 2, 1, 2, "accent")                   # dorsal glow plates
    b.append({"name": "body", "parent": "root", "pivot": [0, 6, -4], "cubes": c})

    c = []
    for i in range(2):
        bevel(c, -5 + i, 3 + i, 2 + i * 3, 11 - i * 2, 9 - i, 4, "steel", cut=1, edge="main")
    b.append({"name": "seg2", "parent": "body", "pivot": [0, 6, 3], "cubes": c})

    c = []
    bevel(c, -4, 3, 8, 9, 8, 5, "dark", cut=1, edge="steel")
    plate(c, -1, 4, 13, 3, 3, 2, "accent")                                 # tail glow
    b.append({"name": "seg3", "parent": "seg2", "pivot": [0, 6, 9], "cubes": c})

    for i in range(3):
        for side, sx in (("L", 6), ("R", -6)):
            s = 1 if sx > 0 else -1
            c = [box(sx + s * 1, 2, -5 + i * 4, 4, 2, 2, "steel"),
                 box(sx + s * 3, 0, -5 + i * 4, 2, 2, 2, "dark")]
            b.append({"name": "leg%s%d" % (side, i + 1), "parent": "body",
                      "pivot": [sx, 4, -5 + i * 4], "cubes": c})
    return b


def rig_small_biped(extra):
    """goblin / kobold: stumpy legs, long arms, oversized head. 25 units tall."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    for side, sx in (("L", 2), ("R", -2)):
        thigh = []
        chain(thigh, sx, 4, 0, 4, 4, 4, 2, "main", taper=0.9)
        plate(thigh, sx - 2, 4, -2, 4, 1, 4, "steel")                       # knee wrap
        b.append({"name": "leg" + side, "parent": "root", "pivot": [sx, 8, 0], "cubes": thigh})
        shin = []
        chain(shin, sx, 0, 0, 3, 3, 4, 2, "main", taper=0.95)
        plate(shin, sx - 2, 0, -3, 4, 2, 5, "steel")                        # boot
        plate(shin, sx - 2, 0, -4, 4, 1, 2, "dark")                         # toe
        b.append({"name": "leg" + side + "2", "parent": "leg" + side,
                  "pivot": [sx, 4, 0], "cubes": shin})

    c = []
    bevel(c, -4, 8, -3, 9, 8, 7, "main", cut=1, edge="dark")                # chest
    bevel(c, -3, 6, -2, 7, 3, 5, "main", cut=1, edge="dark")                # pelvis
    plate(c, -5, 8, -3, 10, 2, 7, "steel")                                  # belt
    plate(c, -1, 9, -4, 3, 2, 1, "accent")                                  # buckle
    plate(c, -4, 9, 4, 8, 6, 1, "dark")                                     # back
    for i in range(3):
        plate(c, -1, 10 + i * 2, 4, 2, 1, 1, "steel")                       # spine studs
    for t in (-1, 1):
        plate(c, t * 2, 9, -4, 1, 6, 1, "dark")                             # chest strap
    plate(c, 3, 7, -3, 2, 3, 2, "dark")                                     # shoulder knot
    for t in (-1, 1):
        plate(c, t * 4, 7, -2, 1, 3, 3, "steel")                            # belt pouch
        plate(c, t * 4, 8, -3, 1, 1, 1, "accent")                           # pouch clasp
        plate(c, t * 3, 12, -5, 2, 1, 1, "steel")                           # strap buckle
    for k in range(3):
        plate(c, -1 + k, 14, -4, 1, 1, 1, "steel")                          # tooth necklace
    b.append({"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": c})

    c = []
    bevel(c, -4, 16, -5, 9, 9, 10, "main", cut=1, edge="dark")              # skull
    plate(c, -4, 22, -6, 9, 2, 2, "dark")                                   # brow
    plate(c, -3, 17, -7, 7, 4, 3, "main")                                   # snout
    plate(c, -3, 16, -7, 7, 1, 3, "steel")                                  # jaw
    eyes(c, 0, 20, -6, 2, "accent", size=2)
    teeth(c, 0, 16, -7, 5, 5, "steel")
    for t in (-1, 1):
        plate(c, t * 5, 21, -2, 1, 4, 3, "dark")                            # long ears
        plate(c, t * 3, 24, 0, 1, 2, 2, "dark")
        plate(c, t * 5, 19, -3, 1, 1, 1, "accent")                          # ear ring
        plate(c, t * 2, 23, -6, 1, 1, 1, "steel")                           # brow stud
    plate(c, -2, 25, -3, 4, 1, 4, "dark")                                   # scalp patch
    b.append({"name": "head", "parent": "body", "pivot": [0, 16, 0], "cubes": c})

    for side, sx in (("L", 6), ("R", -6)):
        c = []
        plate(c, int(sx - 2), 14, -2, 4, 2, 4, "steel")                     # shoulder pad
        chain(c, sx, 7, 0, 3, 3, 8, 3, "main", taper=0.85)                  # knuckle-length arm
        plate(c, int(sx - 2), 6, -2, 4, 2, 4, "main")                       # hand
        fingers(c, sx, 4, -2, "steel")
        b.append({"name": "arm" + side, "parent": "body", "pivot": [sx, 15, 0], "cubes": c})

    if "dagger" in extra:
        c = [box(-8, 2, -6, 2, 2, 3, "dark")]
        plate(c, -8, 2, -11, 2, 2, 5, "steel")                              # blade
        plate(c, -9, 2, -6, 4, 1, 1, "steel")                               # guard
        plate(c, -8, 3, -4, 2, 1, 1, "accent")                              # grip wrap
        b.append({"name": "dagger", "parent": "armR", "pivot": [-8, 5, -5], "cubes": c})
    if "cowl" in extra:
        c = []
        bevel(c, -5, 20, -6, 11, 6, 12, "dark", cut=1, edge="dark")
        plate(c, -3, 21, -8, 6, 4, 2, "dark")                               # face opening
        plate(c, -2, 22, -9, 4, 2, 1, "accent")                             # eye shine
        b.append({"name": "cowl", "parent": "head", "pivot": [0, 24, 0], "cubes": c})
    if "satchel" in extra:
        c = [box(4, 5, 2, 4, 4, 3, "dark")]
        plate(c, 3, 5, 1, 5, 1, 1, "steel")                                 # strap
        plate(c, 5, 7, 1, 2, 1, 1, "accent")                                # clasp
        b.append({"name": "satchel", "parent": "body", "pivot": [4, 8, 2], "cubes": c})
    if "pickaxe" in extra:
        c = [box(-8, 2, -6, 2, 2, 14, "dark")]
        for t in (-1, 1):
            plate(c, -8, 2, -6 + t * 12, 2, 3, 3, "steel")                  # pick ends
            plate(c, -9, 3, -6 + t * 13, 4, 1, 1, "accent")
        plate(c, -9, 2, -2, 4, 2, 2, "steel")                               # binding
        b.append({"name": "pickaxe", "parent": "armR", "pivot": [-8, 5, -6], "cubes": c})
    if "candle" in extra:
        c = []
        plate(c, -4, 25, -4, 9, 1, 9, "steel")                              # helmet brim
        plate(c, -3, 26, -3, 7, 2, 7, "dark")                               # cap
        plate(c, -1, 28, -1, 3, 3, 3, "steel")                              # candle
        plate(c, -1, 31, -1, 3, 2, 3, "accent")                             # flame
        b.append({"name": "candle", "parent": "head", "pivot": [0, 27, 0], "cubes": c})
    if "pouch" in extra:
        c = [box(4, 6, 3, 5, 4, 3, "steel")]
        plate(c, 4, 6, 1, 5, 1, 1, "dark")
        plate(c, 5, 8, 1, 3, 1, 1, "accent")
        b.append({"name": "pouch", "parent": "body", "pivot": [4, 9, 3], "cubes": c})
    if "rope" in extra:
        c = []
        for i in range(4):
            plate(c, 3, 8 + i * 2, 4, 5, 1, 3, "steel")                     # coiled rope
        plate(c, 3, 8, 6, 5, 8, 1, "dark")                                  # coil shadow
        b.append({"name": "rope", "parent": "body", "pivot": [5, 10, 5], "cubes": c})
    return b


def rig_bulky_biped(extra):
    """orc: wide chest, thick limbs, tusks, permanent hunch. 34 units tall."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    for side, sx in (("L", 5), ("R", -5)):
        thigh = []
        chain(thigh, sx, 7, 0, 6, 6, 6, 2, "main", taper=0.92)
        plate(thigh, sx - 3, 7, -3, 6, 1, 6, "steel")                       # knee iron
        b.append({"name": "leg" + side, "parent": "root", "pivot": [sx, 13, 0], "cubes": thigh})
        shin = []
        chain(shin, sx, 0, 0, 6, 6, 7, 2, "main", taper=0.95)
        plate(shin, sx - 3, 0, -4, 6, 3, 7, "steel")                        # boot
        plate(shin, sx - 3, 0, -5, 6, 1, 3, "dark")                         # sole
        b.append({"name": "leg" + side + "2", "parent": "leg" + side,
                  "pivot": [sx, 7, 0], "cubes": shin})

    c = []
    bevel(c, -7, 13, -5, 15, 12, 10, "main", cut=1, edge="dark")            # chest
    bevel(c, -5, 11, -4, 11, 3, 8, "main", cut=1, edge="dark")              # waist
    plate(c, -8, 13, -5, 16, 2, 10, "steel")                                # belt
    plate(c, -2, 13, -6, 4, 3, 1, "accent")                                 # buckle
    plate(c, -6, 14, 5, 12, 9, 1, "dark")                                   # back plate
    for i in range(3):
        plate(c, -1, 16 + i * 3, 5, 2, 2, 1, "steel")
    for t in (-1, 1):
        plate(c, t * 4, 15, -6, 2, 8, 1, "dark")                            # harness
    plate(c, -7, 14, -6, 14, 3, 1, "steel")                                 # gut plate
    b.append({"name": "body", "parent": "root", "pivot": [0, 13, 0], "cubes": c})

    c = []
    bevel(c, -5, 25, -5, 10, 9, 10, "main", cut=1, edge="dark")             # skull
    plate(c, -5, 31, -6, 10, 3, 2, "dark")                                  # brow ridge
    plate(c, -4, 26, -7, 8, 5, 2, "main")                                   # muzzle
    eyes(c, 0, 30, -6, 2, "accent", size=2)
    teeth(c, 0, 26, -8, 6, 5, "steel")
    for t in (-1, 1):
        plate(c, t * 2, 26, -8, 1, 3, 1, "accent")                          # lower tusks
        plate(c, t * 5, 29, 2, 1, 3, 3, "dark")                             # ears
        plate(c, t * 3, 25, -5, 1, 1, 4, "steel")                           # jaw plate
    b.append({"name": "head", "parent": "body", "pivot": [0, 25, 0], "cubes": c})

    for side, sx in (("L", 9), ("R", -9)):
        c = []
        bevel(c, int(sx - 4), 22, -4, 8, 6, 8, "steel", cut=1, edge="dark")  # pauldron
        plate(c, int(sx - 3), 26, -3, 6, 1, 6, "dark")
        chain(c, sx, 12, 0, 5, 5, 10, 3, "main", taper=0.9)
        plate(c, int(sx - 3), 10, -2, 6, 3, 6, "main")                       # fist
        plate(c, int(sx - 3), 11, -3, 6, 2, 1, "steel")                      # knuckle iron
        fingers(c, sx, 8, -2, "steel")
        b.append({"name": "arm" + side, "parent": "body", "pivot": [sx, 24, 0], "cubes": c})

    if "cleaver" in extra:
        c = [box(-11, 4, -6, 2, 2, 16, "dark")]                             # haft
        plate(c, -13, 6, -14, 6, 9, 3, "steel")                             # blade
        plate(c, -13, 14, -14, 6, 2, 3, "accent")                           # edge glow
        plate(c, -13, 4, -14, 6, 2, 3, "dark")                              # spine
        plate(c, -12, 3, -3, 4, 2, 3, "steel")                              # collar
        b.append({"name": "cleaver", "parent": "armR", "pivot": [-11, 6, -6], "cubes": c})
    if "topknot" in extra:
        c = [box(0, 34, 0, 3, 3, 3, "dark")]
        for i in range(3):
            plate(c, -1 - i, 36 + i, -1, 3 + i * 2, 1, 3, "dark")           # swept hair
        plate(c, -2, 34, -2, 4, 1, 4, "steel")                              # tie
        b.append({"name": "topknot", "parent": "head", "pivot": [0, 34, 0], "cubes": c})
    if "loincloth" in extra:
        c = []
        for i in range(4):
            plate(c, -4 + i, 4 + i * 2, -5, 8 - i * 2, 2, 10, "dark")       # hanging cloth
        plate(c, -1, 4, -6, 2, 10, 1, "accent")                             # tassel
        b.append({"name": "loincloth", "parent": "body", "pivot": [0, 13, 0], "cubes": c})
    return b


def rig_bull(extra):
    """minotaur: digitigrade legs, hunched shoulders, horned bovine skull, tail."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    for side, sx in (("L", 5), ("R", -5)):
        thigh = []
        chain(thigh, sx, 7, 0, 7, 7, 7, 2, "main", taper=0.9)
        plate(thigh, sx - 3, 7, -3, 7, 1, 7, "dark")                        # hock
        b.append({"name": "leg" + side, "parent": "root", "pivot": [sx, 14, 0], "cubes": thigh})
        shin = []
        chain(shin, sx, 2, 1, 6, 6, 5, 2, "main", taper=0.9)
        plate(shin, sx - 3, 0, -3, 7, 2, 8, "steel")                        # hoof
        plate(shin, sx - 3, 0, -4, 7, 1, 2, "dark")
        for t in (-1, 1):
            plate(shin, sx + t * 2, 0, -4, 1, 1, 1, "steel")                # cloven
        b.append({"name": "leg" + side + "2", "parent": "leg" + side,
                  "pivot": [sx, 7, 1], "cubes": shin})

    c = []
    bevel(c, -7, 14, -5, 14, 12, 11, "main", cut=1, edge="dark")            # barrel chest
    bevel(c, -5, 25, -4, 10, 4, 9, "main", cut=1, edge="dark")              # shoulder hump
    bevel(c, -5, 11, -4, 11, 3, 8, "main", cut=1, edge="dark")              # waist
    plate(c, -8, 14, -5, 15, 2, 11, "steel")                                # belt
    plate(c, -7, 16, -6, 14, 5, 1, "steel")                                 # chest plate
    for t in (-1, 1):
        plate(c, t * 4, 16, -6, 2, 9, 1, "dark")                            # harness
    for i in range(3):
        plate(c, -1, 17 + i * 3, 6, 2, 2, 1, "steel")                       # spine studs
    b.append({"name": "body", "parent": "root", "pivot": [0, 14, 0], "cubes": c})

    c = []
    bevel(c, -6, 26, -7, 12, 9, 11, "main", cut=1, edge="dark")             # bovine skull
    plate(c, -5, 27, -10, 10, 5, 3, "main")                                 # muzzle
    plate(c, -4, 27, -11, 8, 1, 1, "dark")                                  # nose
    eyes(c, 0, 32, -8, 3, "accent", size=2)
    teeth(c, 0, 27, -11, 6, 5, "steel")
    for t in (-1, 1):
        plate(c, t * 3, 28, -11, 1, 1, 1, "accent")                         # nostrils
        plate(c, t * 7, 29, 0, 1, 3, 4, "dark")                             # ears
        # horns: staircase out then up, each step its own cube so it reads as curved
        c.append(box(t * 7, 28, -5, 4, 3, 3, "steel"))
        c.append(box(t * 9, 30, -4, 3, 3, 3, "steel"))
        c.append(box(t * 10, 33, -3, 2, 3, 2, "accent"))
        c.append(box(t * 10, 36, -3, 2, 2, 2, "steel"))
    b.append({"name": "head", "parent": "body", "pivot": [0, 26, 0], "cubes": c})

    for side, sx in (("L", 8), ("R", -8)):
        c = []
        bevel(c, int(sx - 4), 23, -4, 8, 6, 8, "steel", cut=1, edge="dark")  # pauldron
        chain(c, sx, 11, 0, 5, 5, 12, 3, "main", taper=0.88)
        plate(c, int(sx - 3), 9, -2, 6, 3, 6, "main")                        # fist
        fingers(c, sx, 7, -2, "steel")
        b.append({"name": "arm" + side, "parent": "body", "pivot": [sx, 25, 0], "cubes": c})

    if "greataxe" in extra:
        c = [box(-11, 1, -6, 2, 22, 2, "dark")]                             # upright haft
        for i in range(4):
            plate(c, -12, 4 + i * 5, -7, 4, 1, 1, "steel")                   # binding rings
        plate(c, -14, 16, -12, 7, 10, 2, "steel")                            # blade
        plate(c, -14, 16, -13, 7, 10, 1, "accent")                           # edge
        plate(c, -14, 26, -12, 7, 2, 2, "dark")                              # top spike base
        c.append(box(-11, 23, -6, 3, 5, 3, "steel"))                         # top spike
        b.append({"name": "greataxe", "parent": "armR", "pivot": [-11, 5, -6], "cubes": c})
    if "tail" in extra:
        c = []
        for i in range(5):
            plate(c, -1, 12 - i * 2, 6 + i, 3 - (i // 3), 3, 2, "main")     # hanging tail
        plate(c, -2, 2, 10, 4, 3, 3, "dark")                                # tuft
        b.append({"name": "tail", "parent": "body", "pivot": [0, 12, 6], "cubes": c})
    if "nosering" in extra:
        c = [box(-2, 26, -12, 4, 1, 1, "accent")]
        plate(c, -3, 24, -12, 1, 3, 1, "accent")                            # ring sides
        plate(c, 2, 24, -12, 1, 3, 1, "accent")
        plate(c, -2, 23, -12, 4, 1, 1, "accent")
        b.append({"name": "nosering", "parent": "head", "pivot": [0, 26, -11], "cubes": c})
    return b


def rig_tentacle(extra):
    """tentacle horror: floating maw-dome on six writhing tentacles."""
    b = [{"name": "root", "pivot": [0, 0, 0]}]

    # ---- six tentacles: 5 tapering segments each, drifting outward as they reach the floor
    for i, zz in enumerate((-5, 0, 5)):
        for side, sx in (("L", 4), ("R", -4)):
            s = 1 if sx > 0 else -1
            c = []
            c.append(box(sx + s * 0.5, 6, zz, 4, 4, 4, "main"))             # root
            c.append(box(sx + s * 2.0, 3, zz, 4, 4, 4, "main"))             # knuckle
            c.append(box(sx + s * 3.5, 1, zz, 3, 3, 3, "main"))             # low
            c.append(box(sx + s * 4.5, 0, zz, 3, 2, 3, "dark"))             # tip
            c.append(box(sx + s * 5.5, 0, zz, 2, 1, 2, "dark"))             # curled end
            plate(c, int(sx + s * 0.5) - 1, 8, zz - 3, 1, 1, 1, "accent")   # front sucker
            plate(c, int(sx + s * 2.0) - 1, 5, zz - 3, 1, 1, 1, "accent")   # knuckle sucker
            plate(c, int(sx + s * 3.5) - 1, 2, zz - 3, 1, 1, 1, "accent")   # low sucker
            plate(c, int(sx + s * 2), 7, zz, 2, 1, 2, "dark")               # ring
            b.append({"name": "tent%s%d" % (side, i + 1), "parent": "body",
                      "pivot": [sx, 8, zz], "cubes": c})

    c = []
    # dome in two slabs so no single cube owns the whole silhouette
    bevel(c, -6, 8, -6, 12, 6, 12, "main", cut=1, edge="dark")             # lower dome
    bevel(c, -6, 14, -6, 12, 6, 12, "main", cut=1, edge="dark")            # upper dome
    bevel(c, -5, 18, -5, 10, 4, 10, "main", cut=1, edge="dark")             # crown
    plate(c, -3, 22, -3, 6, 2, 6, "dark")                                   # crown plate
    for t in (-1, 1):
        plate(c, t * 1, 23, -2, 1, 2, 4, "accent")                          # crown ridge
    for k in range(6):                                                      # barnacles
        bx = -5 + (k % 3) * 4
        bz = -5 + (k // 3) * 8
        c.append(box(bx, 7, bz, 2, 2, 2, "steel"))
        plate(c, bx, 6, bz, 3, 1, 3, "dark")
    for k in range(4):                                                      # mantle frill
        plate(c, -6 + k * 3, 12, 5, 2, 5, 1, "dark")
    b.append({"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": c})

    c = []
    bevel(c, -4, 16, -9, 8, 7, 8, "main", cut=1, edge="dark")               # brow mass
    for t in (-1, 1):
        plate(c, t * 3, 20, -10, 2, 3, 1, "dark")                           # eye sockets
        plate(c, t * 3, 20, -11, 2, 2, 1, "accent")                         # eyes
    plate(c, -1, 23, -9, 2, 2, 1, "accent")                                 # third eye
    b.append({"name": "head", "parent": "body", "pivot": [0, 18, -2], "cubes": c})

    if "maw" in extra:
        c = []
        for t in (-1, 1):
            plate(c, t * 3, 9, -8, 3, 6, 3, "dark")                         # jaw halves
            for k in range(4):                                              # fang rows
                plate(c, t * (1 + k), 9 + k * 2, -9, 1, 1, 1, "steel")
            plate(c, t * 4, 8, -9, 2, 2, 2, "steel")                        # corner fangs
        plate(c, -2, 8, -8, 4, 1, 3, "dark")                                # lower lip
        plate(c, 0, 11, -9, 1, 4, 1, "accent")                              # gullet glow
        plate(c, -3, 15, -8, 6, 1, 2, "dark")                               # upper lip
        b.append({"name": "maw", "parent": "body", "pivot": [0, 12, -7], "cubes": c})
    return b


RIGS = {"biped": rig_biped, "quadruped": rig_quadruped, "insect": rig_insect,
        "mech": rig_mech, "grub": rig_grub, "small_biped": rig_small_biped,
        "bulky_biped": rig_bulky_biped, "bull": rig_bull, "tentacle": rig_tentacle}


# ---------------------------------------------------------------------------
# uv atlas: one rectangle per cube, packed into the cube's material region
# ---------------------------------------------------------------------------
FACE_ORDER = ("up", "down", "west", "north", "east", "south")


def face_rects(w, h, d, u, v):
    """Bedrock box unwrap: 2*(d+w) wide, (d+h) tall."""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def pack_uv(bones):
    """Shelf-pack every cube into its own region; returns the face plan for the painter."""
    shelf = {k: [0, 0, 0] for k in REGION}  # cursor x, cursor y, current row height
    plan = []
    for bone in bones:
        for c in bone.get("cubes", []):
            sx, sy, sz = [int(round(v)) for v in c["size"]]
            if min(sx, sy, sz) < 1:
                raise SystemExit("bad cube size %s in %s" % (c["size"], bone["name"]))
            fw, fh = 2 * (sx + sz), sy + sz
            st = shelf[c["mat"]]
            if st[0] + fw > AREA:
                st[0] = 0
                st[1] += st[2] + 2
                st[2] = 0
            if st[1] + fh > AREA:
                raise SystemExit("atlas overflow in region %s (%s)" % (c["mat"], bone["name"]))
            ox, oy = REGION[c["mat"]]
            u, v = st[0], st[1]
            c["uv"] = [ox + u, oy + v]
            st[0] += fw + 2
            st[2] = max(st[2], fh)
            for face, (rx, ry, rw, rh) in face_rects(sx, sy, sz, ox + u, oy + v).items():
                if rw > 0 and rh > 0:
                    plan.append({"rect": (rx, ry, rw, rh), "mat": c["mat"],
                                 "face": face, "part": bone["name"]})
    return plan


def resolve_flush_faces(bones, eps=0.02, tries=6):
    """Push detail slabs off coplanar faces.

    A plate whose front face sits exactly on the body's front face (the natural thing to
    author) z-fights in game: two quads, identical depth, flickering pixels. Vanilla models
    dodge this by hand; here it is cheaper to detect the condition and nudge the later cube
    outward by a fraction of a unit than to hand-check 140 boxes per mob.
    """
    accepted = []

    def faces(b):
        (x0, y0, z0), (sx, sy, sz) = b["origin"], b["size"]
        return [(x0, y0, z0), (x0 + sx, y0 + sy, z0 + sz)]

    def overlap(a0, a1, b0, b1):
        return min(a1, b1) - max(a0, b0) > 1e-6

    for bone in bones:
        for c in bone.get("cubes", []):
            for _ in range(tries):
                clash = None
                mine = faces(c)
                for other in accepted:
                    theirs = faces(other)
                    for axis in range(3):
                        o0, o1 = mine[0][axis], mine[1][axis]
                        t0, t1 = theirs[0][axis], theirs[1][axis]
                        if abs(o0 - t0) > eps and abs(o0 - t1) > eps and \
                                abs(o1 - t0) > eps and abs(o1 - t1) > eps:
                            continue  # not touching on this axis
                        a1, a2 = [i for i in range(3) if i != axis]
                        if not overlap(mine[0][a1], mine[1][a1], theirs[0][a1], theirs[1][a1]):
                            continue
                        if not overlap(mine[0][a2], mine[1][a2], theirs[0][a2], theirs[1][a2]):
                            continue
                        centre_mine = (o0 + o1) / 2.0
                        centre_theirs = (t0 + t1) / 2.0
                        clash = (axis, 1.0 if centre_mine >= centre_theirs else -1.0)
                        break
                    if clash:
                        break
                if clash is None:
                    break
                axis, direction = clash
                c["origin"][axis] = round(c["origin"][axis] + direction * eps, 4)
            accepted.append(c)
    return bones


def build_geo(name, spec, plan_out=None):
    bones = RIGS[spec["arch"]](spec["bones_extra"])
    resolve_flush_faces(bones)
    plan = pack_uv(bones)
    if plan_out is not None:
        plan_out.extend(plan)
    for bone in bones:
        for c in bone.get("cubes", []):
            c.pop("mat", None)
    bounds = spec["height"]
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.%s.%s" % (MODID, name),
                        "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 3.0,
                        "visible_bounds_height": round(bounds + 0.5, 2),
                        "visible_bounds_offset": [0, round(bounds / 2, 2), 0]},
        "bones": bones}]}


# ---------------------------------------------------------------------------
# animation helpers -- everything is a sampled sine/ramp so clips always match the ticks
# ---------------------------------------------------------------------------
def keys(fn, length, samples):
    """fn(t) -> (rotation, position, scale) triples; emit only the channels that move."""
    rot, pos, scl = {}, {}, {}
    for i in range(samples + 1):
        t = length * i / float(samples)
        r, p, s = fn(t)
        k = "%.2f" % t
        if r is not None:
            rot[k] = [round(v, 3) for v in r]
        if p is not None:
            pos[k] = [round(v, 3) for v in p]
        if s is not None:
            scl[k] = [round(v, 3) for v in s]
    out = {}
    if rot:
        out["rotation"] = rot
    if pos:
        out["position"] = pos
    if scl:
        out["scale"] = scl
    return out


def bob_bone(length, amp_y=0.6, amp_rot=2.0, lag=0.0):
    def f(t):
        p = 2 * math.pi * (t / length) + lag
        return (None, (0.0, round(amp_y * math.sin(p), 3), 0.0),
                None)
    return f


def walk_leg(length, amp=32.0, lag=0.0, axis=0):
    def f(t):
        a = math.radians(amp) * math.sin(2 * math.pi * (t / length) + lag)
        r = [0.0, 0.0, 0.0]
        r[axis] = math.degrees(a)
        return (tuple(round(v, 3) for v in r), None, None)
    return f


def clip(length, bones_clips, loop=True):
    return {"loop": loop, "animation_length": round(length, 3), "bones": bones_clips}


BIPED_LIKE = ("biped", "mech", "small_biped", "bulky_biped", "bull")


def anim_for(name, spec, bones_ok):
    """Five clips, shaped by the attack kind so the strike lands on the java timing.

    ``bones_ok`` is the rig's bone set: a clip may only touch bones this mob actually has
    (a spider has no armL/armR -- animating them is a silent no-op in GeckoLib, so the limbs
    are chosen per archetype here and the result is filtered once more before returning).
    """
    kind = spec["kind"]
    arch = spec["arch"]
    out = {}

    # limbs that this archetype actually owns
    if arch in BIPED_LIKE:
        arms = ["armL", "armR"]
        walkers = ["legL", "legR"]
    elif arch == "tentacle":
        arms = []
        walkers = ["tentL1", "tentL2", "tentL3", "tentR1", "tentR2", "tentR3"]
    elif arch == "insect":
        arms = []
        walkers = ["legL1", "legL2", "legL3", "legR1", "legR2", "legR3"]
    elif arch == "grub":
        arms = []
        walkers = ["legL1", "legL2", "legL3", "legR1", "legR2", "legR3"]
    else:
        arms = []
        walkers = ["legFL", "legFR", "legBL", "legBR"]

    # ---- idle: slow breathing, weapon sway ----
    idle_len = 3.0
    idle = {"body": keys(lambda t: (None, (0.0, round(0.35 * math.sin(2 * math.pi * t / idle_len), 3), 0.0), None), idle_len, 12),
            "head": keys(lambda t: ((round(2.2 * math.sin(2 * math.pi * t / idle_len + 1.1), 3), 0.0, 0.0), None, None), idle_len, 12)}
    if arch in BIPED_LIKE:
        idle["armL"] = keys(lambda t: ((round(3.0 * math.sin(2 * math.pi * t / idle_len), 3), 0.0, 0.0), None, None), idle_len, 12)
        idle["armR"] = keys(lambda t: ((round(-3.0 * math.sin(2 * math.pi * t / idle_len), 3), 0.0, 0.0), None, None), idle_len, 12)
    if arch == "insect":
        idle["abdomen"] = keys(lambda t: ((round(2.5 * math.sin(2 * math.pi * t / idle_len + 2.0), 3), 0.0, 0.0), None, None), idle_len, 12)
    if "lantern" in spec["bones_extra"]:
        idle["lantern"] = keys(lambda t: (None, (0.0, round(0.4 * math.sin(2 * math.pi * t / 2.0), 3), 0.0), None), idle_len, 12)
    out["idle"] = clip(idle_len, idle, loop=True)

    # ---- walk ----
    wl = 1.0
    walk = {"body": keys(lambda t: ((0.0, 0.0, 0.0), (0.0, round(0.5 * abs(math.sin(2 * math.pi * t / wl)), 3), 0.0), None), wl, 8)}
    if arch in BIPED_LIKE:
        walk["legL"] = keys(walk_leg(wl, 34.0), wl, 8)
        walk["legR"] = keys(walk_leg(wl, 34.0, math.pi), wl, 8)
        # 膝盖：小腿相位滞后半个步，膝盖才像"弯"而不是"整条腿荡秋千"
        walk["legL2"] = keys(walk_leg(wl, 20.0, math.pi * 0.55), wl, 8)
        walk["legR2"] = keys(walk_leg(wl, 20.0, math.pi * 1.55), wl, 8)
        walk["armL"] = keys(walk_leg(wl, 22.0, math.pi), wl, 8)
        walk["armR"] = keys(walk_leg(wl, 22.0), wl, 8)
    elif arch == "quadruped":
        for b, lag in (("legFL", 0.0), ("legBR", 0.0), ("legFR", math.pi), ("legBL", math.pi)):
            walk[b] = keys(walk_leg(wl, 30.0, lag), wl, 8)
        walk["tail"] = keys(lambda t: (None, None, None), wl, 1)
    elif arch == "insect":
        for i in range(3):
            walk["legL%d" % (i + 1)] = keys(walk_leg(wl, 18.0, i * 0.7), wl, 8)
            walk["legR%d" % (i + 1)] = keys(walk_leg(wl, 18.0, i * 0.7 + math.pi), wl, 8)
    elif arch == "tentacle":
        # 触手不是腿，是"原地扭动 + 轮流撑地"：整体起伏 + 每条各自相位
        walk["body"] = keys(lambda t: (None, (0.0, round(1.2 * abs(math.sin(2 * math.pi * t / wl)), 3), 0.0), None), wl, 8)
        for i, w in enumerate(walkers):
            walk[w] = keys(lambda t, i=i: ((round(14.0 * math.sin(2 * math.pi * t / wl + i * 1.05), 3),
                                            round(10.0 * math.cos(2 * math.pi * t / wl + i * 1.05), 3), 0.0), None, None), wl, 8)
    elif arch == "grub":
        for i in range(3):
            walk["legL%d" % (i + 1)] = keys(walk_leg(wl, 20.0, i * 0.8), wl, 8)
            walk["legR%d" % (i + 1)] = keys(walk_leg(wl, 20.0, i * 0.8 + math.pi), wl, 8)
        walk["seg2"] = keys(lambda t: ((0.0, round(6.0 * math.sin(4 * math.pi * t / wl), 3), 0.0), None, None), wl, 8)
    out["walk"] = clip(wl, walk, loop=True)

    # ---- attack: one signature clip per kind, tuned so impact is at the java tick ----
    atk = {}
    if kind == "charge":            # windup 0-0.45, launch 0.45-0.75, recover
        atk["body"] = keys(lambda t: (
            (round(-18 * min(t / 0.45, 1.0) if t < 0.45 else 8.0 * (1 - (t - 0.45) / 0.55), 3), 0, 0),
            (0.0, 0.0, round(3.0 * (t / 0.45) if t < 0.45 else 3.0 * (1 - (t - 0.45) / 0.55), 3)), None), 1.0, 10)
        atk["head"] = keys(lambda t: ((round(14 if t < 0.45 else -6 * (1 - t), 3), 0, 0), None, None), 1.0, 10)
        atk["legFL"] = keys(walk_leg(1.0, 42.0), 1.0, 10)
        atk["legFR"] = keys(walk_leg(1.0, 42.0), 1.0, 10)
        if arms:  # 双足/牛头人没有 legFL/FR，改用腿蹬地，不然冲撞只有上半身在动
            atk["legL"] = keys(walk_leg(1.0, 30.0), 1.0, 10)
            atk["legR"] = keys(walk_leg(1.0, 30.0, math.pi), 1.0, 10)
            atk["legL2"] = keys(walk_leg(1.0, 18.0, math.pi * 0.5), 1.0, 10)
            atk["legR2"] = keys(walk_leg(1.0, 18.0, math.pi * 1.5), 1.0, 10)
    elif kind in ("hook", "pounce"):  # reach out then snap back
        atk["body"] = keys(lambda t: ((round(16 * math.sin(math.pi * min(t / 0.7, 1.0)), 3), 0, 0), None, None), 0.8, 10)
        atk["armR"] = keys(lambda t: ((round(-110 * math.sin(math.pi * min(t / 0.7, 1.0)), 3), 0, 0), None, None), 0.8, 10)
        atk["armL"] = keys(lambda t: ((round(-70 * math.sin(math.pi * min(t / 0.7, 1.0)), 3), 0, 0), None, None), 0.8, 10)
        if "hook" in spec["bones_extra"]:
            atk["hook"] = keys(lambda t: ((round(30 * math.sin(math.pi * min(t / 0.7, 1.0)), 3), 0, 0), None, None), 0.8, 10)
    elif kind in ("volley", "pot", "beam"):   # load, aim, release
        atk["armR"] = keys(lambda t: ((round(-80 if t < 0.5 else -20 * (1 - (t - 0.5) / 0.5), 3), 0, 0), None, None), 1.0, 10)
        atk["head"] = keys(lambda t: ((round(-6 if t < 0.5 else 4, 3), 0, 0), None, None), 1.0, 10)
        atk["body"] = keys(lambda t: ((round(-4 if t < 0.5 else 6, 3), 0, 0), None, None), 1.0, 10)
        for prop in ("crossbow", "pot", "rifle"):
            if prop in spec["bones_extra"]:
                atk[prop] = keys(lambda t: ((round(-14 if t < 0.5 else 10 * (1 - (t - 0.5) / 0.5), 3), 0, 0), None, None), 1.0, 10)
    elif kind == "burrow":          # sink, rise, bite
        atk["body"] = keys(lambda t: (None, (0.0, round(-10 * min(t / 0.3, 1.0) if t < 0.3 else (-10 + 14 * min((t - 0.3) / 0.3, 1.0)), 3), 0.0), None), 0.9, 12)
        for i in range(3):
            atk["legL%d" % (i + 1)] = keys(walk_leg(0.9, 40.0, i * 0.6), 0.9, 12)
            atk["legR%d" % (i + 1)] = keys(walk_leg(0.9, 40.0, i * 0.6 + math.pi), 0.9, 12)
    elif kind == "bind":            # arms out, hold, release
        atk["armL"] = keys(lambda t: ((round(-95 * min(t / 0.35, 1.0) if t < 0.75 else -95 * (1 - (t - 0.75) / 0.25), 3), 0, 0), None, None), 1.0, 10)
        atk["armR"] = keys(lambda t: ((round(-95 * min(t / 0.35, 1.0) if t < 0.75 else -95 * (1 - (t - 0.75) / 0.25), 3), 0, 0), None, None), 1.0, 10)
        atk["body"] = keys(lambda t: ((round(-8 * min(t / 0.35, 1.0), 3), 0, 0), None, None), 1.0, 10)
    elif kind == "frost":           # rear up, spray, settle
        atk["body"] = keys(lambda t: ((round(-22 * math.sin(math.pi * min(t / 0.8, 1.0)), 3), 0, 0), None, None), 0.9, 10)
        atk["head"] = keys(lambda t: ((round(-16 * math.sin(math.pi * min(t / 0.8, 1.0)), 3), 0, 0), None, None), 0.9, 10)
        atk["abdomen"] = keys(lambda t: ((round(18 * math.sin(math.pi * min(t / 0.8, 1.0)), 3), 0, 0), None, None), 0.9, 10)
    elif kind == "summon":          # chant, raise staff, slam
        atk["armR"] = keys(lambda t: ((round(-150 * min(t / 0.6, 1.0) if t < 0.6 else -150 + 170 * ((t - 0.6) / 0.4), 3), 0, 0), None, None), 1.4, 12)
        atk["body"] = keys(lambda t: ((round(-10 * min(t / 0.6, 1.0), 3), 0, 0), None, None), 1.4, 12)
        if "staff" in spec["bones_extra"]:
            atk["staff"] = keys(lambda t: ((round(-40 * min(t / 0.6, 1.0), 3), 0, 0), None, None), 1.4, 12)
    elif kind == "overheat":        # piston punch
        atk["armR"] = keys(lambda t: ((round(-120 * min(t / 0.3, 1.0) if t < 0.55 else -120 * (1 - (t - 0.55) / 0.45), 3), 0, 0), None, None), 1.0, 10)
        atk["body"] = keys(lambda t: ((round(10 * min(t / 0.3, 1.0), 3), 0, 0), None, None), 1.0, 10)
        if "pistonR" in spec["bones_extra"]:
            atk["pistonR"] = keys(lambda t: (None, (0.0, 0.0, round(-6 * min(t / 0.3, 1.0), 3)), None), 1.0, 10)
    elif kind == "whirl":           # 沉身蓄力 → 360° 横扫 → 收势
        atk["body"] = keys(lambda t: ((round(-14 * min(t / 0.3, 1.0), 3),
                                       round(360.0 * min(max(t - 0.3, 0.0) / 0.45, 1.0), 3), 0.0), None, None), 1.0, 12)
        atk["armR"] = keys(lambda t: ((round(-92 * min(t / 0.3, 1.0) if t < 0.75 else -92 * (1 - (t - 0.75) / 0.25), 3), 0, 0), None, None), 1.0, 12)
        atk["armL"] = keys(lambda t: ((round(-70 * min(t / 0.3, 1.0) if t < 0.75 else -70 * (1 - (t - 0.75) / 0.25), 3), 0, 0), None, None), 1.0, 12)
        if "cleaver" in spec["bones_extra"]:
            atk["cleaver"] = keys(lambda t: ((round(20 * min(t / 0.3, 1.0), 3), 0, 0), None, None), 1.0, 12)
    elif kind == "grab":            # 六条触手依次弹出 → 抓 → 收回
        atk["body"] = keys(lambda t: ((round(-16 * math.sin(math.pi * min(t / 0.9, 1.0)), 3), 0, 0), None, None), 1.0, 12)
        atk["head"] = keys(lambda t: ((round(-10 * math.sin(math.pi * min(t / 0.9, 1.0)), 3), 0, 0), None, None), 1.0, 12)
        for i, w in enumerate(walkers):
            lag = i * 0.045          # 依次抽出：六条同时动会像一片裙摆
            def lunge(t, lag=lag):
                p = math.sin(math.pi * min(max(t - lag, 0.0) / 0.62, 1.0))
                return ((round(30 * p, 3), 0.0, 0.0),               # 向前压
                        (0.0, 0.0, round(-36 * p, 3)),               # 前伸 36 像素 = 2.25 格
                        tuple(round(1 + 0.2 * p, 3) for _ in range(3)))  # 顺带拉长
            atk[w] = keys(lunge, 1.0, 14)
        if "maw" in spec["bones_extra"]:
            atk["maw"] = keys(lambda t: (None, None, tuple(round(1 + 0.35 * math.sin(math.pi * min(t / 0.8, 1.0)), 3) for _ in range(3))), 1.0, 12)
    elif kind == "detonate":        # swell, tremor, blow
        atk["body"] = keys(lambda t: (None, None, tuple(round(1 + 0.5 * min(t / 0.7, 1.0), 3) for _ in range(3))), 0.8, 10)
        atk["head"] = keys(lambda t: ((round(6 * math.sin(14 * t), 3), 0, 0), None, None), 0.8, 16)
    out["attack"] = clip(0.8 if kind in ("hook", "pounce", "detonate") else (0.9 if kind in ("burrow", "frost") else (1.4 if kind == "summon" else 1.0)), atk, loop=False)

    # ---- special: the mob's own state clip (overheat / chant / submerge) ----
    sp = {}
    if kind == "overheat":
        sp["body"] = keys(lambda t: ((round(8 * math.sin(18 * t), 2), 0, 0), None, None), 1.2, 20)
        if "vent" in spec["bones_extra"]:
            sp["vent"] = keys(lambda t: (None, None, tuple(round(1 + 0.3 * abs(math.sin(9 * t)), 3) for _ in range(3))), 1.2, 20)
    elif kind == "summon":
        sp["armL"] = keys(lambda t: ((round(-40 - 30 * math.sin(3 * t), 2), 0, 0), None, None), 1.5, 12)
        sp["armR"] = keys(lambda t: ((round(-40 - 30 * math.sin(3 * t + 1.0), 2), 0, 0), None, None), 1.5, 12)
    elif kind == "burrow":
        sp["body"] = keys(lambda t: (None, (0.0, round(-12 * abs(math.sin(math.pi * t / 0.8)), 2), 0.0), None), 1.2, 12)
    elif kind == "bind":
        sp["armL"] = keys(lambda t: ((round(-70, 2), round(10 * math.sin(6 * t), 2), 0), None, None), 1.2, 12)
        sp["armR"] = keys(lambda t: ((round(-70, 2), round(-10 * math.sin(6 * t), 2), 0), None, None), 1.2, 12)
    elif kind == "whirl":           # 举斧过头，慢慢转 —— "要来横扫了"的预警姿势
        sp["body"] = keys(lambda t: ((round(-6, 2), round(28 * math.sin(2 * math.pi * t / 1.2), 2), 0), None, None), 1.2, 12)
        sp["armR"] = keys(lambda t: ((round(-120, 2), 0, 0), None, None), 1.2, 12)
        sp["armL"] = keys(lambda t: ((round(-100, 2), 0, 0), None, None), 1.2, 12)
    elif kind == "grab":            # 触手在水里一样慢慢摆
        for i, w in enumerate(walkers):
            sp[w] = keys(lambda t, i=i: ((round(18 * math.sin(2.4 * t + i * 1.1), 2),
                                          round(8 * math.cos(2.4 * t + i * 1.1), 2), 0), None, None), 1.2, 12)
    else:
        sp["body"] = keys(lambda t: (None, None, tuple(round(1 + 0.06 * math.sin(2 * math.pi * t), 3) for _ in range(3))), 1.2, 12)
    out["special"] = clip(1.2 if kind != "summon" else 1.5, sp, loop=True)

    # ---- death ----
    death = {"body": keys(lambda t: ((round(80 * (t / 1.0), 2), 0, 0), (0.0, round(-6 * (t / 1.0), 2), 0.0),
                                   tuple(round(max(0.05, 1 - 0.55 * (t / 1.0)), 3) for _ in range(3))), 1.0, 10)}
    if arch in BIPED_LIKE:
        death["armL"] = keys(lambda t: ((round(50 * (t / 1.0), 2), 0, 0), None, None), 1.0, 10)
        death["armR"] = keys(lambda t: ((round(-50 * (t / 1.0), 2), 0, 0), None, None), 1.0, 10)
    out["death"] = clip(1.0, death, loop=False)

    # ---- final gate: a clip may only touch bones this rig owns ----
    for clip_name, c in list(out.items()):
        c["bones"] = {b: k for b, k in c["bones"].items() if b in bones_ok}

    # pounce/hook written above is an arm swing; on a legless-armed rig turn it into a real
    # leap (crouch -> spring -> land) instead of leaving the clip with a lone bobbing body.
    if kind in ("hook", "pounce") and not arms:
        leap = {"body": keys(lambda t: (None, (0.0, round(9.0 * math.sin(math.pi * min(t / 0.55, 1.0)), 3), 0.0), None), 0.8, 12)}
        for i, w in enumerate(walkers):
            leap[w] = keys(walk_leg(0.8, 40.0, i * 0.6), 0.8, 12)
        out["attack"] = clip(0.8, {b: k for b, k in leap.items() if b in bones_ok}, loop=False)

    return {"format_version": "1.8.0", "animations": out}


# ---------------------------------------------------------------------------
# hand-painted texture: base wash + brush strokes + material motifs + grain
# ---------------------------------------------------------------------------
def _rng(name):
    # crc32, not hash(): hash() is salted per process, and a texture generator that paints a
    # different sheet on every run would make "did the art change?" impossible to diff.
    return np.random.default_rng(zlib.crc32(name.encode("utf-8")))


def _wash(rng, size, base, spread=10):
    """base colour with a soft vertical gradient and per-pixel jitter."""
    base = np.array(base, dtype=np.float32)
    yy = np.linspace(-1.0, 1.0, size, dtype=np.float32)[:, None]
    grad = (yy * spread * 0.6)
    noise = rng.normal(0, spread * 0.45, (size, size)).astype(np.float32)
    rgb = base[None, None, :3] + grad[:, :, None] + noise[:, :, None]
    a = np.full((size, size, 1), 255, dtype=np.float32)
    return np.clip(np.concatenate([rgb, a], axis=2), 0, 255)


def _strokes(rng, arr, count, colour, alpha, width=2, curved=True):
    """hand-brush look: short tapered strokes, not straight lines."""
    size = arr.shape[0]
    img = Image.fromarray(arr.astype(np.uint8), "RGBA")
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        x = rng.uniform(0, size)
        y = rng.uniform(0, size)
        if curved:
            pts = [(x, y)]
            ang = rng.uniform(0, math.tau)
            for _step in range(rng.integers(3, 7)):
                ang += rng.normal(0, 0.5)
                x += math.cos(ang) * rng.uniform(6, 22)
                y += math.sin(ang) * rng.uniform(6, 22)
                pts.append((x, y))
            d.line(pts, fill=(colour[0], colour[1], colour[2], int(alpha)), width=int(width),
                   joint="curve")
        else:
            w = rng.uniform(8, 40)
            h = rng.uniform(4, 12)
            d.rounded_rectangle([x, y, x + w, y + h], radius=3,
                                fill=(colour[0], colour[1], colour[2], int(alpha)))
    layer = layer.filter(ImageFilter.GaussianBlur(0.6))
    img = Image.alpha_composite(img, layer)
    return np.asarray(img).astype(np.float32)


MOTIFS = {}


def motif(fn):
    MOTIFS[fn.__name__.replace("m_", "", 1)] = fn
    return fn


@motif
def m_hide(rng, arr, pal, size):
    arr = _strokes(rng, arr, 220, pal["dark"], 60, width=3)
    arr = _strokes(rng, arr, 160, pal["main"], 90, width=2)
    return arr


@motif
def m_rust(rng, arr, pal, size):
    arr = _strokes(rng, arr, 260, pal["accent"], 80, width=2)
    arr = _strokes(rng, arr, 140, pal["dark"], 110, width=4)
    return arr


@motif
def m_moss(rng, arr, pal, size):
    arr = _strokes(rng, arr, 300, pal["accent"], 70, width=3)
    arr = _strokes(rng, arr, 180, pal["dark"], 90, width=2)
    return arr


@motif
def m_bone(rng, arr, pal, size):
    arr = _strokes(rng, arr, 200, (250, 244, 226), 80, width=2, curved=False)
    arr = _strokes(rng, arr, 160, pal["dark"], 70, width=3)
    return arr


@motif
def m_ash_cloth(rng, arr, pal, size):
    arr = _strokes(rng, arr, 240, pal["dark"], 90, width=4)
    arr = _strokes(rng, arr, 120, (240, 232, 220), 40, width=2)
    return arr


@motif
def m_leather(rng, arr, pal, size):
    arr = _strokes(rng, arr, 200, pal["dark"], 80, width=3, curved=False)
    arr = _strokes(rng, arr, 120, pal["steel"], 60, width=2)
    return arr


@motif
def m_slime(rng, arr, pal, size):
    arr = _strokes(rng, arr, 260, pal["accent"], 60, width=6)
    arr = _strokes(rng, arr, 120, (255, 255, 255), 30, width=2)
    return arr


@motif
def m_cloth(rng, arr, pal, size):
    arr = _strokes(rng, arr, 260, pal["dark"], 100, width=5)
    arr = _strokes(rng, arr, 100, pal["accent"], 45, width=2)
    return arr


@motif
def m_frost(rng, arr, pal, size):
    arr = _strokes(rng, arr, 240, (240, 252, 255), 90, width=2, curved=False)
    arr = _strokes(rng, arr, 140, pal["dark"], 70, width=3)
    return arr


@motif
def m_brass(rng, arr, pal, size):
    arr = _strokes(rng, arr, 220, pal["steel"], 90, width=3, curved=False)
    arr = _strokes(rng, arr, 140, pal["dark"], 90, width=2)
    return arr


@motif
def m_decay(rng, arr, pal, size):
    arr = _strokes(rng, arr, 260, pal["dark"], 100, width=4)
    arr = _strokes(rng, arr, 150, pal["accent"], 55, width=3)
    return arr


# accent motifs -- the glowing bits, painted brighter so they read at a distance
def m_lantern(rng, arr, pal, size):
    return _strokes(rng, arr, 200, (255, 236, 170), 140, width=4)


def m_ember(rng, arr, pal, size):
    return _strokes(rng, arr, 200, (255, 170, 90), 130, width=3)


def m_fire(rng, arr, pal, size):
    return _strokes(rng, arr, 220, (255, 200, 110), 150, width=5)


def m_venom(rng, arr, pal, size):
    return _strokes(rng, arr, 200, (200, 255, 160), 120, width=3)


def m_ice(rng, arr, pal, size):
    return _strokes(rng, arr, 220, (215, 248, 255), 140, width=3, curved=False)


def m_arc(rng, arr, pal, size):
    return _strokes(rng, arr, 180, (190, 250, 255), 140, width=2, curved=False)


def m_shadow(rng, arr, pal, size):
    return _strokes(rng, arr, 220, (170, 110, 240), 120, width=4)


def m_bile(rng, arr, pal, size):
    return _strokes(rng, arr, 200, (210, 245, 120), 130, width=4)


# the accent painters above are plain functions (they are read from SPECS by motif name),
# so fold every m_* into the table the painter looks up -- decorator order stopped mattering.
for _n, _f in list(globals().items()):
    if _n.startswith("m_") and callable(_f):
        MOTIFS.setdefault(_n[2:], _f)


# ---------------------------------------------------------------------------
# per-face painting: each cube owns its own rectangle, so every part can be detailed
# ---------------------------------------------------------------------------
# The first pass painted four 512 blocks of generic material and let every cube sample
# the same corner of them -- which is why the mobs read as flat coloured blobs. Now the
# packer hands each cube a unique rectangle, and the painter fills each *face* of that
# rectangle separately: form shading (light on top, shadow underneath), a dark seam and
# an inner highlight so parts separate visually, then material-specific micro detail.
ORIENT_LIGHT = {"up": 1.16, "south": 1.02, "north": 0.99, "east": 0.90, "west": 0.86, "down": 0.72}


def _to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def _from_img(img):
    return np.asarray(img).astype(np.float32)


def _blobs(arr, rng, colour, n, alpha, rmin=1.0, rmax=3.0):
    h, w, _ = arr.shape
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        cx, cy = rng.uniform(0, w), rng.uniform(0, h)
        rw, rh = rng.uniform(rmin, rmax), rng.uniform(rmin, rmax)
        d.ellipse([cx - rw, cy - rh, cx + rw, cy + rh],
                  fill=(int(colour[0]), int(colour[1]), int(colour[2]), int(alpha)))
    return _from_img(Image.alpha_composite(_to_img(arr).convert("RGBA"), layer).convert("RGB"))


def _lines(arr, rng, colour, n, alpha, vertical=False, width=1):
    h, w, _ = arr.shape
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        if vertical:
            x = rng.uniform(0, w)
            d.line([(x, 0), (x + rng.normal(0, 1.2), h)],
                   fill=(int(colour[0]), int(colour[1]), int(colour[2]), int(alpha)), width=width)
        else:
            y = rng.uniform(0, h)
            d.line([(0, y), (w, y + rng.normal(0, 1.2))],
                   fill=(int(colour[0]), int(colour[1]), int(colour[2]), int(alpha)), width=width)
    return _from_img(Image.alpha_composite(_to_img(arr).convert("RGBA"), layer).convert("RGB"))


def _speck(arr, rng, colour, amount=0.10, alpha=200):
    """Random pixels of a second colour -- cheap grain that survives at 16px texels."""
    h, w, _ = arr.shape
    mask = rng.random((h, w)) < amount
    out = arr.copy()
    out[mask] = out[mask] * (1 - alpha / 255.0) + np.array(colour, dtype=np.float32) * (alpha / 255.0)
    return out


def _mix(arr, colour, k):
    return arr * (1 - k) + np.array(colour, dtype=np.float32)[None, None, :] * k


# ---- main-material styles -------------------------------------------------
def st_hide(img, rng, base):
    img = _lines(img, rng, tuple(int(c * 0.55) for c in base), max(3, img.shape[0] // 2), 95)
    img = _lines(img, rng, tuple(min(255, int(c * 1.35)) for c in base), max(2, img.shape[0] // 3), 80)
    img = _mix(img, (base[0] * 1.22, base[1] * 1.18, base[2] * 1.12), 0.10)
    return _speck(img, rng, (int(base[0] * 0.5), int(base[1] * 0.45), int(base[2] * 0.4)), 0.12)


def st_rust(img, rng, base):
    img = _blobs(img, rng, (base[0] * 1.5, base[1] * 0.9, base[2] * 0.5), 4, 90, 1, 2.5)
    img = _blobs(img, rng, (base[0] * 0.45, base[1] * 0.4, base[2] * 0.4), 5, 120, 0.8, 2.0)
    return _speck(img, rng, (int(base[0] * 1.6), int(base[1] * 1.0), int(base[2] * 0.6)), 0.10)


def st_moss(img, rng, base):
    img = _blobs(img, rng, (90, 130, 70), 5, 110, 1, 3)
    img = _blobs(img, rng, (140, 180, 90), 3, 90, 0.8, 2)
    return _speck(img, rng, (60, 90, 55), 0.12)


def st_bone(img, rng, base):
    img = _mix(img, (245, 240, 225), 0.16)
    img = _lines(img, rng, (110, 100, 88), 3, 90)
    return _speck(img, rng, (150, 140, 124), 0.07)


def st_ash_cloth(img, rng, base):
    img = _lines(img, rng, (40, 38, 36), max(3, img.shape[0] // 2), 80, vertical=True)
    img = _blobs(img, rng, (30, 28, 26), 4, 90, 1, 3)
    return _speck(img, rng, (200, 120, 50), 0.05)


def st_leather(img, rng, base):
    img = _blobs(img, rng, tuple(int(c * 1.25) for c in base), 5, 60, 1, 2.5)
    img = _lines(img, rng, tuple(int(c * 0.6) for c in base), 3, 70)
    return _speck(img, rng, (60, 40, 30), 0.08)


def st_slime(img, rng, base):
    img = _mix(img, (120, 150, 110), 0.14)
    img = _blobs(img, rng, (170, 200, 150), 4, 80, 1, 2.5)
    return _speck(img, rng, (40, 60, 40), 0.10)


def st_cloth(img, rng, base):
    img = _lines(img, rng, tuple(int(c * 0.55) for c in base), 4, 80)
    img = _lines(img, rng, tuple(min(255, int(c * 1.5)) for c in base), 4, 85, vertical=True)
    img = _blobs(img, rng, tuple(min(255, int(c * 1.55)) for c in base), 5, 80, 1, 2.5)
    return _blobs(img, rng, tuple(int(c * 0.5) for c in base), 4, 90, 1, 2)


def st_frost(img, rng, base):
    img = _mix(img, (225, 245, 255), 0.20)
    img = _lines(img, rng, (170, 215, 240), 4, 90)
    return _speck(img, rng, (250, 255, 255), 0.10)


def st_brass(img, rng, base):
    img = _lines(img, rng, tuple(min(255, int(c * 1.35)) for c in base), 3, 110)
    img = _lines(img, rng, tuple(int(c * 0.55) for c in base), 2, 90)
    img = _mix(img, (255, 220, 150), 0.10)
    return _blobs(img, rng, (60, 45, 30), 2, 70, 0.8, 1.6)


def st_decay(img, rng, base):
    img = _blobs(img, rng, (60, 50, 40), 5, 130, 1, 3)
    img = _blobs(img, rng, (110, 90, 60), 4, 80, 0.8, 2)
    return _speck(img, rng, (40, 35, 28), 0.14)


def st_steel(img, rng, base):
    img = _lines(img, rng, tuple(min(255, int(c * 1.45)) for c in base), 2, 120)
    img = _lines(img, rng, tuple(int(c * 0.5) for c in base), 2, 90)
    return _mix(img, (210, 215, 225), 0.08)


# ---- accent styles (emissive parts; these read as glow) -------------------
def _glow_core(img, rng, colour, strength=0.55):
    h, w, _ = img.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = w * 0.5, h * 0.45
    d = np.sqrt(((xx - cx) / max(1.0, w * 0.6)) ** 2 + ((yy - cy) / max(1.0, h * 0.7)) ** 2)
    fall = np.clip(1.0 - d, 0, 1)[:, :, None] ** 1.6
    return img + (np.array(colour, dtype=np.float32)[None, None, :] - img) * (fall * strength)


def st_lantern(img, rng, base):
    img = _glow_core(img, rng, (255, 226, 150), 0.62)
    return _lines(img, rng, (255, 245, 200), 2, 140)


def st_ember(img, rng, base):
    img = _glow_core(img, rng, (255, 170, 60), 0.55)
    return _speck(img, rng, (255, 230, 160), 0.16)


def st_fire(img, rng, base):
    img = _glow_core(img, rng, (255, 140, 40), 0.60)
    img = _blobs(img, rng, (255, 225, 150), 3, 120, 0.8, 2)
    return _speck(img, rng, (255, 255, 210), 0.12)


def st_venom(img, rng, base):
    img = _glow_core(img, rng, (150, 235, 110), 0.50)
    return _blobs(img, rng, (200, 255, 150), 3, 120, 0.8, 2)


def st_ice(img, rng, base):
    img = _glow_core(img, rng, (210, 245, 255), 0.55)
    img = _lines(img, rng, (255, 255, 255), 3, 130)
    return _speck(img, rng, (140, 210, 250), 0.10)


def st_arc(img, rng, base):
    img = _glow_core(img, rng, (150, 245, 255), 0.55)
    return _lines(img, rng, (235, 255, 255), 2, 160, vertical=True)


def st_shadow(img, rng, base):
    img = _glow_core(img, rng, (200, 140, 255), 0.58)
    return _blobs(img, rng, (120, 70, 190), 4, 90, 0.8, 2)


def st_bile(img, rng, base):
    img = _glow_core(img, rng, (200, 245, 120), 0.50)
    return _blobs(img, rng, (150, 200, 80), 3, 110, 0.8, 2)


STYLES = {
    "hide": st_hide, "rust": st_rust, "moss": st_moss, "bone": st_bone,
    "ash_cloth": st_ash_cloth, "leather": st_leather, "slime": st_slime,
    "cloth": st_cloth, "frost": st_frost, "brass": st_brass, "decay": st_decay,
    "steel": st_steel, "lantern": st_lantern, "ember": st_ember, "fire": st_fire,
    "venom": st_venom, "ice": st_ice, "arc": st_arc, "shadow": st_shadow, "bile": st_bile,
}


def paint_face(w, h, base, style, orient, rng):
    """One cube face: form shading -> material detail -> seam -> grain."""
    h, w = max(1, int(h)), max(1, int(w))
    yy = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None, None]
    form = 1.0 + 0.16 * (0.5 - yy)                     # lit from above
    # Mild per-part tone shift only: separation comes from the seam and corner occlusion,
    # a big random tint would read as a patchwork quilt rather than one creature.
    jitter = rng.uniform(0.93, 1.07)
    img = np.broadcast_to(np.array(base, dtype=np.float32)[None, None, :],
                          (h, w, 3)).copy() * form * ORIENT_LIGHT[orient] * jitter
    img = np.clip(img, 0, 255)
    try:
        img = STYLES[style](img, rng, base)
    except KeyError:
        img = _mix(img, (200, 200, 200), 0.05)
    # seam + inner highlight: the "inked panel" look
    img[:1, :, :] *= 1.12
    img[:, :1, :] *= 1.09
    img[-1:, :, :] *= 0.80
    img[:, -1:, :] *= 0.84
    # soft corner occlusion -- two texels in each bottom corner. At 16 texels per face this
    # is the difference between "flat sticker" and "bevelled piece of kit".
    for dy in range(min(2, h)):
        for dx in range(min(2, w)):
            k = 0.88 + 0.05 * (dy + dx)
            img[h - 1 - dy, dx, :] *= k
            img[h - 1 - dy, w - 1 - dx, :] *= k
    img = np.clip(img, 0, 255)
    img += rng.normal(0, 2.6, (h, w, 1)).astype(np.float32)
    return np.clip(img, 0, 255)


def paint(name, spec, plan):
    """Fill the 1024 sheet face by face, then finish with grain and a soft vignette."""
    rng = _rng(name)
    pal = spec["pal"]
    style_of = {
        "main": spec["motif"],
        "steel": "brass" if spec["arch"] == "mech" else "steel",
        "accent": spec["accent_motif"],
        "dark": "cloth",
    }
    img = np.full((TEX, TEX, 3), 58.0, dtype=np.float32)
    # neutral bed per region so unused space still reads as material, not as holes
    for key, (ox, oy) in REGION.items():
        base = np.array(pal[{"main": "main", "steel": "steel",
                             "accent": "accent", "dark": "dark"}[key]], dtype=np.float32)
        img[oy:oy + AREA, ox:ox + AREA] = base * 0.78
    tex = Image.fromarray(img.astype(np.uint8), "RGB")

    for face in plan:
        rx, ry, rw, rh = face["rect"]
        if rw < 1 or rh < 1:
            continue
        tile = paint_face(rw, rh, pal[face["mat"]], style_of[face["mat"]], face["face"], rng)
        tex.paste(Image.fromarray(tile.astype(np.uint8), "RGB"), (rx, ry))

    arr = np.asarray(tex).astype(np.float32)
    # organic pass: a few large soft blotches so the sheet reads as painted canvas
    # instead of a grid of identical tiles (and dark materials stop looking flat).
    layer = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(26):
        cx, cy = rng.uniform(0, TEX), rng.uniform(0, TEX)
        rx = rng.uniform(50, 190)
        tint = (255, 240, 205) if rng.random() < 0.5 else (16, 12, 10)
        d.ellipse([cx - rx, cy - rx * 0.7, cx + rx, cy + rx * 0.7], fill=tint + (11,))
    layer = layer.filter(ImageFilter.GaussianBlur(26))
    arr = _from_img(Image.alpha_composite(_to_img(arr).convert("RGBA"), layer).convert("RGB"))
    arr += rng.normal(0, 3.0, (TEX, TEX, 1)).astype(np.float32)
    yy, xx = np.mgrid[0:TEX, 0:TEX].astype(np.float32)
    vig = 1.0 - 0.09 * (((xx - TEX / 2) ** 2 + (yy - TEX / 2) ** 2) ** 0.5 / (TEX * 0.72))
    arr = np.clip(arr * vig[:, :, None], 0, 255)
    out = Image.fromarray(arr.astype(np.uint8), "RGB").convert("RGBA")
    return out


# ---------------------------------------------------------------------------
def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    assets = os.path.join(repo, "src", "main", "resources", "assets", "tnc")
    geo_dir = os.path.join(assets, "geo", "entity")
    anim_dir = os.path.join(assets, "animations", "entity")
    tex_dir = os.path.join(assets, "textures", "entity")
    for d in (geo_dir, anim_dir, tex_dir):
        os.makedirs(d, exist_ok=True)

    sheet = Image.new("RGBA", (TEX, TEX * len(ORDER)), (0, 0, 0, 0))
    for i, name in enumerate(ORDER):
        spec = SPECS[name]
        plan = []
        geo = build_geo(name, spec, plan)
        anim = anim_for(name, spec, {b["name"] for b in geo["minecraft:geometry"][0]["bones"]})
        tex = paint(name, spec, plan)

        with open(os.path.join(geo_dir, name + ".geo.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(geo, f, indent=2, ensure_ascii=False)
            f.write("\n")
        with open(os.path.join(anim_dir, name + ".animation.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(anim, f, separators=(",", ":"), ensure_ascii=False)
            f.write("\n")
        tex.save(os.path.join(tex_dir, name + "_bedrock.png"))
        sheet.paste(tex.resize((256, 256), Image.LANCZOS), (0, i * 256))
        bones = len(geo["minecraft:geometry"][0]["bones"])
        cubes = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
        clips = len(anim["animations"])
        print("%-22s %-10s %2d bones  %3d cubes  %2d faces  %d clips  %s"
              % (name, spec["arch"], bones, cubes, len(plan), clips, spec["kind"]))

    prev = os.path.join(repo, "docs", "previews", "dungeon_mobs_texture.png")
    os.makedirs(os.path.dirname(prev), exist_ok=True)
    sheet.save(prev)
    print("\ntexture sheet -> %s" % os.path.relpath(prev, repo))


if __name__ == "__main__":
    main()
