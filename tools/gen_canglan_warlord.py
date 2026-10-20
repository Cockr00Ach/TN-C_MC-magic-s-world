"""苍岚战将 / Canglan Warlord -- the wind school's heavy boss rig.

The rig follows the same production contract as 翠岚武尊 (Wind Warden) and the
stone lancer: a Bedrock ``.geo.json`` built from boxes on a standard humanoid
skeleton, one 256x256 x 4-material atlas, and a GeckoLib animation file whose
clip lengths are *exactly* the tick budgets in ``CanglanWarlordAttackRules``.

What it is meant to read as, at a glance:

  * six and a half blocks of cold relic-steel plate in the wind arsenal palette
    (slate steel body, cyan-white edge, mist-blue cloth, gale glow) -- the same
    colours as ``tnc:item/wind_relic_metal``, the metal the player's wind
    weapons are cut from;
  * a 鬼面 wind mask under a horned helm, with a long swept cloth crest;
  * **no weapon geometry at all**: the right hand carries an empty
    ``weapon_1`` anchor, and the renderer draws the *wind arsenal* blade there --
    sword (form 1) in phase one, spear (form 2) in phase two, the exact weapons
    the wind spell chain conjures;
  * a round gale buckler on the left forearm, a four-bone tattered cape and a
    three-bone war banner behind the shoulders, so the silhouette still says
    "warlord" when it is standing still.

Scale: 1 Minecraft pixel = 2.75 model units (1 unit = 1/16 block), so the
32-pixel template is 88 units = 5.5 blocks of body, the helmet tops out at
~6.3 blocks and the crest at ~6.7 blocks.  Hitbox is 2.8 x 6.4 in
``CanglanWarlordAttackRules``.

Deterministic: ``random.seed`` is fixed, so re-running produces byte-identical
geo, animation and texture files.

    python tools/gen_canglan_warlord.py
    python tools/preview_canglan_warlord.py      # offline previews in docs/previews/
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
GEO = ASSETS / "geo/entity/canglan_warlord.geo.json"
ANIM = ASSETS / "animations/entity/canglan_warlord.animation.json"
TEX = ASSETS / "textures/entity/canglan_warlord.png"

random.seed(20261020)

P = 2.75          # model units per Minecraft pixel
DENS = 7.0        # texture pixels per model unit
Q = {"steel": (0, 0), "mist": (128, 0), "edge": (0, 128), "gale": (128, 128)}


def px(v):
    return v * P


def t(a=2.0):
    return (random.uniform(-a, a), random.uniform(-a, a), random.uniform(-a, a))


# ---------------------------------------------------------------------------
#  bone skeleton
# ---------------------------------------------------------------------------
PIVOT = {
    "root": [0, 0, 0],
    "pelvis": [0, px(11.8), 0],
    "waist": [0, px(14.6), 0],
    "torso": [0, px(16.0), 0],
    "chest": [0, px(20.6), 0],
    "neck": [0, px(25.2), 0],
    "head": [0, px(26.4), 0],
    "jaw": [0, px(28.6), -px(1.6)],
    "eyes": [0, px(30.8), -px(3.8)],
    "helmet": [0, px(29.2), 0],
    "crest_a": [0, px(34.8), px(0.4)],
    "crest_b": [0, px(35.2), px(2.2)],
    "crest_c": [0, px(34.2), px(3.9)],
    "banner_pole": [px(3.4), px(20.2), px(2.6)],
    "banner_a": [px(3.5), px(30.6), px(3.4)],
    "banner_b": [px(3.5), px(26.5), px(3.4)],
    "banner_c": [px(3.5), px(22.5), px(3.4)],
    "cape_a": [0, px(23.4), px(3.2)],
    "cape_b": [0, px(19.4), px(3.8)],
    "cape_c": [0, px(15.4), px(4.2)],
    "cape_d": [0, px(11.4), px(4.4)],
    "skirt_f": [0, px(11.6), -px(2.2)],
    "skirt_b": [0, px(11.6), px(2.2)],
    "skirt_-1": [-px(4.4), px(11.6), 0],
    "skirt_1": [px(4.4), px(11.6), 0],
}
PARENT = {
    "pelvis": "root", "waist": "pelvis", "torso": "waist", "chest": "torso",
    "neck": "chest", "head": "neck", "jaw": "head", "eyes": "head",
    "helmet": "head", "crest_a": "helmet", "crest_b": "crest_a", "crest_c": "crest_b",
    "banner_pole": "chest", "banner_a": "banner_pole", "banner_b": "banner_a",
    "banner_c": "banner_b",
    "cape_a": "chest", "cape_b": "cape_a", "cape_c": "cape_b", "cape_d": "cape_c",
    "skirt_f": "waist", "skirt_b": "waist", "skirt_-1": "waist", "skirt_1": "waist",
}
for _s in (-1, 1):
    _k = str(_s)
    PIVOT["horn_a_" + _k] = [_s * px(3.2), px(33.6), 0]
    PIVOT["horn_b_" + _k] = [_s * px(4.8), px(35.8), 0]
    PIVOT["pauldron_" + _k] = [_s * px(5.4), px(24.2), 0]
    PIVOT["arm_" + _k] = [_s * px(6.1), px(23.4), 0]
    PIVOT["fore_" + _k] = [_s * px(6.1), px(16.6), 0]
    PIVOT["hand_" + _k] = [_s * px(6.1), px(11.2), 0]
    PIVOT["finger_" + _k] = [_s * px(6.1), px(9.6), -px(1.4)]
    PIVOT["thigh_" + _k] = [_s * px(2.7), px(13.4), 0]
    PIVOT["shin_" + _k] = [_s * px(2.7), px(7.0), 0]
    PIVOT["foot_" + _k] = [_s * px(2.7), px(1.5), 0]
    PIVOT["toe_" + _k] = [_s * px(2.7), px(0.9), -px(2.6)]
    PARENT["horn_a_" + _k] = "helmet"
    PARENT["horn_b_" + _k] = "horn_a_" + _k
    PARENT["pauldron_" + _k] = "chest"
    PARENT["arm_" + _k] = "chest"
    PARENT["fore_" + _k] = "arm_" + _k
    PARENT["hand_" + _k] = "fore_" + _k
    PARENT["finger_" + _k] = "hand_" + _k
    PARENT["thigh_" + _k] = "pelvis"
    PARENT["shin_" + _k] = "thigh_" + _k
    PARENT["foot_" + _k] = "shin_" + _k
    PARENT["toe_" + _k] = "foot_" + _k

# The wind arsenal sword/spear is *drawn* at this empty anchor, not modelled:
# the same baked weapon the player's wind spells use, so phase one carries the
# sword (form 1) and phase two the spear (form 2) with the relic-metal palette.
PIVOT["weapon_1"] = [px(6.1), px(10.6), px(-0.8)]
PIVOT["weapon_tip_1"] = [px(6.1), px(10.6), px(-0.8)]
PARENT["weapon_1"] = "hand_1"
PARENT["weapon_tip_1"] = "weapon_1"
PIVOT["shield"] = [-px(6.1), px(14.6), -px(2.4)]
PARENT["shield"] = "fore_-1"

CUBES = {}


# ---------------------------------------------------------------------------
#  primitive helpers (all coordinates are in Minecraft pixels)
# ---------------------------------------------------------------------------
def b(bone, c, s, mat, rot=None):
    """One box: centre ``c``, size ``s`` (px), optional Euler rotation (deg)."""
    CUBES.setdefault(bone, []).append({
        "o": [round(px(c[i]) - px(s[i]) / 2, 3) for i in range(3)],
        "s": [round(max(0.3, px(s[i])), 3) for i in range(3)],
        "r": None if rot is None else [round(v, 2) for v in rot],
        "m": mat,
    })


def mb(bone, c, s, mat, rot=None):
    """Mirror a box pair across the model's X=0 plane."""
    b(bone, c, s, mat, rot)
    rr = None if rot is None else [rot[0], -rot[1], -rot[2]]
    b(bone, (-c[0], c[1], c[2]), s, mat, rr)


def scales(bone, c, s, mat, rows, cols, gap=0.88, tilt=5.0):
    """Overlapping lamellar plates -- the core visual of the armour."""
    for r in range(rows):
        for k in range(cols):
            x = c[0] + (k - (cols - 1) / 2) * s[0] * gap
            y = c[1] + (r - (rows - 1) / 2) * s[1] * gap
            b(bone, (x, y, c[2]), (s[0] * 0.95, s[1] * 0.9, s[2]), mat,
              ((r - (rows - 1) / 2) * tilt * 0.6, (k - (cols - 1) / 2) * tilt, 0))


def ring(bone, y, rx, rz, thick, mat, seg=12, h=1.0):
    """A closed band of small boxes -- helmet rims, gold collars, weapon rings."""
    w = 2 * rx * math.sin(math.pi / seg) * 1.25
    for i in range(seg):
        a = 2 * math.pi * i / seg
        b(bone, (rx * math.cos(a), y, rz * math.sin(a)), (w, h, thick), mat,
          (0, 90 - math.degrees(a), 0))


def rivets(bone, positions, mat="edge", size=0.55):
    for (x, y, z) in positions:
        b(bone, (x, y, z), (size, size, size), mat, (0, 0, 45))


# ---------------------------------------------------------------------------
#  sculpt
# ---------------------------------------------------------------------------
def sculpt():
    CUBES.clear()

    # ---------------- torso: iron lamellar under a war cuirass ----------------
    b("pelvis", (0, 11.9, 0), (9.6, 4.6, 5.4), "steel")
    b("pelvis", (0, 12.2, 2.9), (8.4, 3.2, 0.6), "mist")
    b("pelvis", (0, 10.0, -2.6), (8.8, 2.4, 0.7), "mist", (6, 0, 0))
    scales("pelvis", (0, 11.6, -2.7), (2.6, 2.0, 0.7), "steel", 2, 3, tilt=3)

    b("waist", (0, 14.8, 0), (9.0, 2.4, 4.9), "steel")
    b("waist", (0, 14.9, 0), (9.4, 1.2, 5.3), "mist")
    # 兽面 belt buckle
    b("waist", (0, 14.9, -2.85), (3.8, 2.6, 0.9), "edge")
    b("waist", (0, 14.6, -3.35), (2.2, 1.5, 0.6), "edge")
    mb("waist", (0.9, 15.4, -3.45), (0.6, 0.55, 0.4), "gale")
    mb("waist", (1.8, 15.9, -3.25), (0.7, 1.6, 0.45), "edge", (0, 0, -26))
    mb("waist", (0.95, 13.9, -3.3), (0.5, 1.1, 0.4), "edge")
    rivets("waist", [(3.2, 14.9, -3.05), (-3.2, 14.9, -3.05)])

    b("torso", (0, 18.4, 0), (9.0, 8.4, 4.6), "steel")
    # abdomen lamellae, two bands, crimson lacing under each plate
    scales("torso", (0, 15.9, -2.45), (2.55, 1.75, 0.7), "steel", 2, 3, gap=0.92, tilt=4)
    b("torso", (0, 15.9, -2.7), (8.0, 0.5, 0.5), "mist")
    b("torso", (0, 17.4, -2.7), (8.2, 0.5, 0.5), "mist")
    b("torso", (0, 19.9, 0), (9.8, 1.0, 5.0), "edge")

    # ---------------- chest cuirass + 兽面 emblem ----------------
    b("chest", (0, 22.0, 0), (11.0, 6.2, 5.8), "steel")
    b("chest", (0, 22.0, -2.95), (9.8, 5.6, 0.9), "steel")
    b("chest", (0, 22.3, -3.5), (8.8, 4.6, 0.5), "steel")
    mb("chest", (4.5, 22.0, -3.35), (0.9, 5.4, 0.7), "edge")
    b("chest", (0, 24.6, -3.2), (9.4, 1.0, 0.8), "edge", (-8, 0, 0))
    b("chest", (0, 19.6, -3.2), (8.6, 0.9, 0.8), "edge", (6, 0, 0))
    # golden taotie face
    b("chest", (0, 22.2, -3.95), (4.2, 3.4, 0.8), "edge")
    b("chest", (0, 21.5, -4.35), (2.6, 1.7, 0.6), "edge")
    mb("chest", (1.05, 22.9, -4.2), (0.75, 0.6, 0.45), "gale")
    mb("chest", (2.0, 23.5, -3.95), (1.0, 2.1, 0.55), "edge", (0, 0, -32))
    mb("chest", (2.6, 24.4, -3.9), (0.7, 1.2, 0.5), "edge", (0, 0, -48))
    mb("chest", (1.05, 20.8, -4.15), (0.5, 1.2, 0.45), "edge")
    b("chest", (0, 21.0, -4.45), (1.4, 0.8, 0.4), "edge")
    rivets("chest", [(3.7, 23.8, -3.45), (-3.7, 23.8, -3.45),
                     (3.7, 20.1, -3.45), (-3.7, 20.1, -3.45)])
    # shoulder straps, collar and backplate
    mb("chest", (3.4, 23.7, -0.9), (2.6, 1.5, 4.6), "mist", (0, 0, -12))
    ring("chest", 25.0, 4.7, 3.7, 0.85, "edge", 12, 1.3)
    b("chest", (0, 22.0, 3.05), (10.0, 5.6, 0.9), "steel")
    for i in range(3):
        b("chest", (0, 23.8 - i * 1.5, 3.55), (1.6, 0.9, 0.6), "edge")
    b("chest", (0, 23.9, 3.6), (5.2, 1.2, 0.8), "edge")
    # cape mount rings
    mb("chest", (1.8, 23.8, 3.7), (0.9, 0.9, 0.55), "edge", (0, 0, 45))

    b("neck", (0, 25.6, 0), (3.4, 2.8, 3.4), "steel")
    b("neck", (0, 25.2, 0), (3.9, 0.9, 3.9), "mist")

    # ---------------- head: 鬼面 mask under a horned helm ----------------
    b("head", (0, 30.2, 0), (8.4, 8.4, 8.4), "steel")
    b("head", (0, 30.0, -4.2), (7.8, 7.2, 0.9), "steel")
    b("head", (0, 30.6, -4.7), (6.6, 4.8, 0.5), "steel")
    b("head", (0, 32.3, -4.75), (7.2, 1.0, 0.7), "edge", (-8, 0, 0))
    b("head", (0, 30.2, -4.95), (1.0, 2.6, 0.6), "steel")
    mb("head", (3.9, 29.2, -1.4), (1.0, 5.2, 4.8), "steel", (0, 0, -6))
    mb("head", (4.35, 29.6, -2.6), (0.7, 3.4, 2.6), "edge", (0, 0, -6))
    mb("head", (2.5, 33.0, -4.2), (1.4, 0.6, 0.6), "edge", (0, 0, 8))
    b("jaw", (0, 27.6, -3.2), (6.4, 2.3, 2.8), "steel", (4, 0, 0))
    b("jaw", (0, 27.0, -4.35), (4.4, 1.0, 0.9), "steel", (4, 0, 0))
    mb("jaw", (1.9, 26.4, -4.5), (0.7, 1.5, 0.6), "edge")
    mb("jaw", (2.85, 26.6, -4.45), (0.5, 1.1, 0.5), "edge")
    b("jaw", (0, 26.8, -4.4), (1.4, 1.0, 0.5), "edge")
    mb("jaw", (3.2, 28.2, -3.9), (1.6, 1.2, 0.8), "edge", (0, 0, -18))
    # burning eyes sit on their own bone so the renderer can force full bright
    mb("eyes", (1.9, 31.0, -4.85), (1.6, 0.75, 0.45), "gale", (0, 0, 5))
    mb("eyes", (1.9, 31.35, -4.75), (1.9, 0.3, 0.3), "edge", (0, 0, 5))

    # helmet dome: three plate rows + gold brow band + cheek and neck guards
    for r in range(3):
        for c in range(5):
            b("helmet", ((c - 2) * 1.74, 33.5 + r * 1.0, -0.3 + r * 0.35),
              (1.9, 1.25, 8.7 - r * 1.15), "steel",
              (-10 + r * 7, (c - 2) * 6, 0))
    ring("helmet", 32.6, 4.95, 4.95, 0.75, "edge", 14, 1.15)
    b("helmet", (0, 32.7, -4.65), (7.8, 1.2, 0.85), "edge", (-6, 0, 0))
    b("helmet", (0, 33.2, -5.0), (1.7, 1.7, 0.7), "gale", (0, 0, 45))
    mb("helmet", (4.65, 29.6, -1.2), (1.1, 5.0, 4.4), "steel", (0, 0, -8))
    mb("helmet", (5.25, 29.0, -1.0), (0.65, 2.5, 2.8), "edge", (0, 0, -8))
    mb("helmet", (4.6, 27.4, -2.0), (0.8, 1.6, 2.2), "edge", (0, 0, -8))
    for i in range(3):
        b("helmet", (0, 30.8 - i * 1.25, 4.45 + i * 0.25), (7.8 - i * 0.7, 1.0, 1.7),
          "steel", (-8 - i * 4, 0, 0))
        b("helmet", (0, 30.2 - i * 1.25, 5.3 + i * 0.25), (7.4 - i * 0.7, 0.4, 0.5),
          "edge", (-8 - i * 4, 0, 0))
    b("helmet", (0, 36.1, 0.2), (1.9, 1.7, 3.3), "edge")
    b("helmet", (0, 36.9, 0.3), (1.2, 0.8, 2.4), "edge")

    # horns: two segments per side, curving out and back
    for s in (-1, 1):
        k = str(s)
        for i, (x, y, z, w, h, d, rx, rz) in enumerate((
                (3.1, 33.9, 0.0, 1.7, 2.3, 1.7, -10, -12),
                (3.8, 35.2, 0.35, 1.5, 2.5, 1.5, -16, -24))):
            b("horn_a_" + k, (s * x, y, z), (w, h, d), "steel", (rx, 0, s * rz))
        for i, (x, y, z, w, h, d, rx, rz) in enumerate((
                (4.5, 36.6, 0.75, 1.3, 2.4, 1.3, -22, -36),
                (5.2, 37.8, 1.15, 1.0, 1.8, 1.0, -30, -50))):
            b("horn_b_" + k, (s * x, y, z), (w, h, d), "steel", (rx, 0, s * rz))
        b("horn_b_" + k, (s * 5.6, 38.6, 1.5), (0.85, 1.4, 0.85), "edge", (-36, 0, s * -58))

    # swept cloth crest: three ribbon bones trailing back over the helm
    for bone, y, z, pitch, count in (("crest_a", 36.3, -0.6, -18, 4),
                                     ("crest_b", 35.9, 1.4, -38, 4),
                                     ("crest_c", 34.9, 3.2, -58, 3)):
        for i in range(count):
            b(bone, (0, y + i * 0.3, z + i * 0.95), (0.5, 2.6 - i * 0.2, 0.8),
              "mist", (pitch - i * 5, (i - count / 2) * 4, 0))
            b(bone, (0, y + i * 0.3 - 0.85, z + i * 0.95 + 0.1), (0.55, 0.7, 0.85),
              "gale", (pitch - i * 5, 0, 0))
        b(bone, (0, y - 0.55, z - 0.2), (1.2, 0.6, 1.5), "edge")

    # ---------------- pauldrons ----------------
    for s in (-1, 1):
        k = str(s)
        bone = "pauldron_" + k
        for layer in range(3):
            b(bone, (s * 6.6, 25.6 - layer * 1.35, 0),
              (5.9, 1.5, 6.7 - layer * 0.5), "steel", (0, 0, s * (-8 - layer * 4)))
            b(bone, (s * 9.3, 25.5 - layer * 1.35, 0),
              (0.85, 1.25, 6.5 - layer * 0.5), "edge", (0, 0, s * (-10 - layer * 4)))
            b(bone, (s * 6.6, 26.3 - layer * 1.35, -0.4),
              (5.7, 0.5, 6.2 - layer * 0.5), "mist", (0, 0, s * (-8 - layer * 4)))
        b(bone, (s * 6.4, 26.9, 0), (5.3, 0.9, 6.1), "steel", (0, 0, s * -6))
        b(bone, (s * 9.0, 26.8, 0), (0.9, 0.8, 6.0), "edge", (0, 0, s * -6))
        # small beast head riveted to the top plate
        b(bone, (s * 7.0, 27.8, -1.1), (2.9, 2.1, 3.1), "steel")
        b(bone, (s * 7.2, 27.6, -2.9), (2.1, 1.5, 1.3), "edge")
        b(bone, (s * 6.6, 28.2, -3.3), (0.65, 0.55, 0.45), "gale")
        b(bone, (s * 7.8, 28.2, -3.3), (0.65, 0.55, 0.45), "gale")
        b(bone, (s * 7.0, 29.2, -0.5), (0.95, 2.3, 1.05), "edge", (0, 0, s * 12))
        b(bone, (s * 7.3, 30.2, -0.2), (0.7, 1.2, 0.75), "edge", (0, 0, s * 20))
        b(bone, (s * 10.1, 27.0, 0.3), (1.0, 3.2, 1.0), "steel", (0, 0, s * 30))
        b(bone, (s * 10.5, 28.4, 0.4), (0.7, 1.3, 0.7), "edge", (0, 0, s * 36))
        b(bone, (s * 10.8, 29.3, 0.5), (0.5, 0.8, 0.5), "edge", (0, 0, s * 44))
        # gale vane: a thin swept fin on the outer edge, so the shoulders carry wind
        b(bone, (s * 9.9, 25.2, 2.0), (0.5, 1.7, 2.6), "edge", (0, 0, s * -20))
        b(bone, (s * 10.6, 26.3, 2.6), (0.4, 1.0, 1.7), "gale", (0, 0, s * -28))

    # ---------------- arms ----------------
    for s in (-1, 1):
        k = str(s)
        b("arm_" + k, (s * 6.1, 20.0, 0), (4.7, 7.8, 4.5), "steel")
        b("arm_" + k, (s * 6.1, 23.2, 0), (5.1, 2.1, 4.9), "mist")
        b("arm_" + k, (s * 6.1, 21.2, 0), (4.9, 0.7, 4.7), "edge")
        b("arm_" + k, (s * 6.1, 17.6, -2.25), (3.7, 2.6, 1.0), "steel", (0, 0, s * 6))
        b("arm_" + k, (s * 8.05, 19.6, 0), (0.9, 4.2, 3.7), "steel", (0, 0, s * -8))
        b("arm_" + k, (s * 6.1, 16.7, 0), (4.5, 0.95, 4.3), "edge")
        rivets("arm_" + k, [(s * 8.0, 21.8, -1.5), (s * 8.0, 18.2, -1.5)])

        b("fore_" + k, (s * 6.1, 14.2, 0), (4.3, 6.5, 4.3), "steel")
        b("fore_" + k, (s * 6.1, 14.0, -2.0), (3.5, 3.4, 1.0), "steel", (0, 0, s * 5))
        b("fore_" + k, (s * 6.1, 15.7, -2.3), (3.7, 0.95, 0.85), "edge")
        b("fore_" + k, (s * 6.1, 12.1, -2.3), (3.5, 0.95, 0.85), "edge")
        b("fore_" + k, (s * 8.1, 13.8, 0), (0.8, 3.3, 2.9), "mist")
        b("fore_" + k, (s * 4.1, 13.8, 0), (0.8, 3.3, 2.9), "mist")
        rivets("fore_" + k, [(s * 7.9, 15.0, -2.0), (s * 7.9, 12.8, -2.0)])

        b("hand_" + k, (s * 6.1, 10.4, 0), (4.1, 3.5, 4.5), "steel")
        b("hand_" + k, (s * 6.1, 11.7, -1.7), (3.7, 1.0, 1.7), "edge")
        b("hand_" + k, (s * 4.5, 10.3, -1.6), (1.3, 1.7, 1.9), "steel", (0, 0, s * 8))
        b("hand_" + k, (s * 7.7, 10.3, -1.6), (1.1, 1.5, 1.7), "steel", (0, 0, s * -8))
        for f in range(3):
            b("finger_" + k, (s * 6.1 + (f - 1) * 1.2, 9.0, -1.6), (1.0, 2.3, 1.5),
              "steel", (0, 0, s * 6))
            b("finger_" + k, (s * 6.1 + (f - 1) * 1.2, 8.0, -2.1), (0.9, 0.9, 1.1),
              "edge", (0, 0, s * 6))
        if s == 1:
            # the drawn arsenal haft passes through this fist, so the back of the
            # hand carries a talisman boss instead of a modelled grip
            b("hand_" + k, (6.1, 10.7, -2.35), (1.7, 1.7, 0.45), "edge", (0, 0, 45))
            b("hand_" + k, (6.1, 10.7, -2.6), (0.7, 0.7, 0.35), "gale")

    # ---------------- round gale buckler on the left forearm ----------------
    def shield_box(x, y, z, w, h, d, mat, rot=None):
        b("shield", (x - 6.1, y, z), (w, h, d), mat, rot)

    # eight convex slats, widest in the middle: a round face out of boxes
    for i, width in enumerate((3.4, 5.6, 6.9, 7.5, 7.5, 6.9, 5.6, 3.4)):
        shield_box(0, 10.55 + i * 1.05, -3.55, width, 1.15, 0.9, "steel")
    shield_box(0, 19.9, -3.7, 8.4, 1.0, 1.0, "edge")
    shield_box(0, 9.7, -3.7, 8.4, 1.0, 1.0, "edge")
    shield_box(-4.4, 14.7, -3.7, 1.0, 9.2, 1.0, "edge")
    shield_box(4.4, 14.7, -3.7, 1.0, 9.2, 1.0, "edge")
    for (dx, dy, rot) in ((-3.4, 18.2, -45), (3.4, 18.2, 45),
                          (-3.4, 11.2, 45), (3.4, 11.2, -45)):
        shield_box(dx, dy, -3.75, 1.6, 1.6, 0.9, "edge", (0, 0, rot))
    # gale spiral on the boss: three offset crescents read as a swirl at a glance
    shield_box(0, 14.7, -4.05, 3.0, 0.8, 0.7, "gale", (0, 0, 18))
    shield_box(0.55, 15.5, -4.05, 2.2, 0.7, 0.7, "gale", (0, 0, -52))
    shield_box(-0.6, 13.9, -4.05, 2.0, 0.7, 0.7, "gale", (0, 0, -14))
    shield_box(0, 14.7, -4.2, 0.9, 0.9, 0.5, "edge", (0, 0, 45))
    # vane on the crown so a still shield still looks windy
    shield_box(-3.1, 20.4, -3.6, 1.3, 2.6, 1.1, "edge", (0, 0, 22))
    shield_box(-3.6, 21.5, -3.5, 0.5, 1.2, 0.9, "gale", (0, 0, 34))
    # straps back to the forearm (shield bone pivots on fore_-1)
    shield_box(0, 15.2, -2.7, 2.6, 1.1, 1.6, "mist")
    shield_box(0, 13.4, -2.7, 2.6, 1.1, 1.6, "mist")

    # ---------------- legs ----------------
    for s in (-1, 1):
        k = str(s)
        b("thigh_" + k, (s * 2.7, 9.6, 0), (5.3, 7.6, 5.1), "steel")
        b("thigh_" + k, (s * 2.7, 10.7, -2.35), (4.5, 3.3, 1.0), "steel", (0, 0, s * 4))
        b("thigh_" + k, (s * 2.7, 8.3, -2.45), (4.7, 2.1, 0.8), "mist")
        b("thigh_" + k, (s * 5.15, 10.5, 0), (0.85, 3.7, 3.7), "edge", (0, 0, s * -6))
        b("thigh_" + k, (s * 2.7, 12.9, -2.4), (4.9, 0.8, 0.7), "edge")
        rivets("thigh_" + k, [(s * 4.5, 11.4, -2.2), (s * 4.5, 9.6, -2.2)])

        b("shin_" + k, (s * 2.7, 4.1, 0), (4.8, 6.2, 4.8), "steel")
        b("shin_" + k, (s * 2.7, 5.0, -2.45), (4.1, 2.7, 1.0), "steel", (0, 0, s * 4))
        b("shin_" + k, (s * 2.7, 2.9, -2.55), (1.7, 2.5, 0.85), "edge")
        b("shin_" + k, (s * 2.7, 6.9, -0.5), (4.7, 1.2, 4.7), "edge")
        b("shin_" + k, (s * 2.7, 7.4, -2.45), (1.3, 2.3, 1.3), "edge", (-26, 0, 0))
        b("shin_" + k, (s * 2.7, 7.9, -3.5), (0.9, 1.5, 0.9), "edge", (-40, 0, 0))
        b("shin_" + k, (s * 2.7, 3.2, 2.1), (3.6, 1.8, 0.9), "mist")

        b("foot_" + k, (s * 2.7, 1.35, -1.2), (5.1, 2.6, 7.1), "steel")
        b("foot_" + k, (s * 2.7, 1.0, -4.1), (5.0, 1.5, 2.2), "edge", (0, 0, 0))
        b("foot_" + k, (s * 2.7, 2.6, 1.6), (4.6, 1.0, 1.4), "edge")
        b("toe_" + k, (s * 2.7, 0.95, -4.6), (4.4, 1.3, 1.5), "steel")
        b("toe_" + k, (s * 2.7, 0.6, -5.4), (4.0, 0.8, 0.7), "steel")
        b("toe_" + k, (s * 2.7, 1.45, -4.6), (4.5, 0.45, 1.6), "edge")

    # ---------------- belt tassets ----------------
    for name, cx, cz, rot in (("skirt_f", 0, -2.7, 0), ("skirt_b", 0, 2.7, 0),
                              ("skirt_-1", -4.5, 0, -90), ("skirt_1", 4.5, 0, 90)):
        for i in range(3):
            y = 11.2 - i * 1.35
            if rot == 0:
                b(name, (cx, y, cz), (4.6 - i * 0.35, 1.55, 0.8), "steel", (i * 6, 0, 0))
                b(name, (cx, y - 0.75, cz - 0.06), (4.4 - i * 0.35, 0.45, 0.6), "edge",
                  (i * 6, 0, 0))
            else:
                b(name, (cx, y, cz), (0.8, 1.55, 4.6 - i * 0.35), "steel", (0, 0, 0))
                b(name, (cx - 0.06 * (1 if cx > 0 else -1), y - 0.75, cz),
                  (0.6, 0.45, 4.4 - i * 0.35), "edge")
        if rot == 0:
            b(name, (cx, 10.1, cz), (2.0, 1.0, 0.7), "edge")
        rivets(name, [(cx if rot == 0 else cx - 0.4, 11.3, cz if rot == 0 else cz)])

    # ---------------- cape ----------------
    for name, y, z, w in (("cape_a", 21.8, 3.55, 9.3), ("cape_b", 18.0, 3.95, 8.7),
                          ("cape_c", 14.2, 4.2, 8.0), ("cape_d", 10.6, 4.35, 7.2)):
        b(name, (0, y, z), (w, 4.5, 0.6), "mist", (0, 0, 0))
        b(name, (0, y + 1.6, z + 0.3), (w * 0.94, 0.55, 0.55), "edge")
        for xx in (-w * 0.32, 0.0, w * 0.32):
            b(name, (xx, y - 0.1, z - 0.38), (0.55, 4.0, 0.35), "mist")
        b(name, (0, y, z + 0.38), (w * 0.7, 4.1, 0.3), "steel")
    # tattered tips on the last cape segment
    for i, (xx, ln) in enumerate(((-3.0, 1.9), (-1.2, 1.2), (0.6, 2.4), (2.4, 1.4))):
        b("cape_d", (xx, 7.9 - ln * 0.2, 4.5), (1.1, ln, 0.5), "mist", (0, 0, (i - 1.5) * 4))

    # ---------------- war banner on the back ----------------
    b("banner_pole", (3.4, 25.9, 3.2), (0.95, 13.6, 0.95), "steel", (-6, 0, 0))
    b("banner_pole", (3.4, 32.5, 3.9), (1.35, 1.0, 1.35), "edge", (-6, 0, 0))
    b("banner_pole", (3.4, 31.7, 3.7), (7.2, 0.75, 0.75), "edge", (-6, 0, 0))
    b("banner_pole", (6.9, 31.5, 3.7), (0.9, 1.1, 0.9), "edge", (-6, 0, 0))
    b("banner_pole", (0.1, 31.9, 3.7), (0.9, 1.1, 0.9), "edge", (-6, 0, 0))
    b("banner_pole", (3.4, 27.0, 3.45), (1.5, 1.2, 1.5), "mist", (-6, 0, 0))
    rivets("banner_pole", [(3.4, 20.5, 3.0), (3.4, 30.0, 3.7)])

    for name, y, h, w in (("banner_a", 28.4, 4.4, 5.9), ("banner_b", 24.4, 4.2, 5.5),
                          ("banner_c", 20.6, 3.8, 5.0)):
        b(name, (3.5, y, 3.55), (w, h, 0.45), "mist", (0, 0, 0))
        b(name, (3.5, y + h / 2 - 0.15, 3.35), (w, 0.4, 0.35), "edge")
        b(name, (3.5, y, 3.3), (w * 0.62, h * 0.55, 0.3), "steel")
    # simple gold war glyph on the banner
    b("banner_a", (3.5, 28.9, 3.32), (2.6, 0.45, 0.35), "edge")
    b("banner_a", (3.5, 27.6, 3.32), (0.45, 2.8, 0.35), "edge")
    b("banner_a", (2.4, 27.1, 3.32), (1.5, 0.45, 0.35), "edge", (0, 0, 30))
    for i, (xx, ln) in enumerate(((-1.9, 1.6), (-0.6, 2.2), (0.8, 1.3), (2.0, 1.8))):
        b("banner_c", (3.5 + xx, 18.9 - ln * 0.2, 3.6), (1.0, ln, 0.4), "mist",
          (0, 0, (i - 1.5) * 5))


# ---------------------------------------------------------------------------
#  geometry export
# ---------------------------------------------------------------------------
def build_geo():
    bones = []
    for name, pivot in PIVOT.items():
        entry = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": []}
        if name in PARENT:
            entry["parent"] = PARENT[name]
        for c in CUBES.get(name, []):
            w, h, d = c["s"]
            faces = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                if face in ("north", "south"):
                    across, vert = w, h
                elif face in ("east", "west"):
                    across, vert = d, h
                else:
                    across, vert = w, d
                su = min(118.0, max(6.0, across * DENS))
                sv = min(118.0, max(6.0, vert * DENS))
                u0, v0 = Q[c["m"]]
                faces[face] = {
                    "uv": [round(u0 + random.uniform(2.0, 126.0 - su), 2),
                           round(v0 + random.uniform(2.0, 126.0 - sv), 2)],
                    "uv_size": [round(su, 2), round(sv, 2)],
                }
            out = {"origin": c["o"], "size": c["s"], "uv": faces}
            if c["r"] and any(abs(a) > 0.01 for a in c["r"]):
                out["rotation"] = c["r"]
                out["pivot"] = [round(c["o"][i] + c["s"][i] / 2, 3) for i in range(3)]
            entry["cubes"].append(out)
        bones.append(entry)
    return bones


def write_geo(bones):
    GEO.parent.mkdir(parents=True, exist_ok=True)
    GEO.write_text(json.dumps({
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tnc.canglan_warlord",
                "texture_width": 256, "texture_height": 256,
                "visible_bounds_width": 5, "visible_bounds_height": 7,
                "visible_bounds_offset": [0, 3, 0],
            },
            "bones": bones,
        }],
    }, separators=(",", ":")), encoding="utf-8", newline="\n")


# ---------------------------------------------------------------------------
#  the drawn blade's wrist keys
#
#  The sword and the spear are render-only: they hang off the empty ``weapon_1``
#  anchor, so every clip's wrist rotation decides where a two-and-a-half block
#  blade ends up. Authoring those keys inside each clip made it impossible to
#  check them as a set, so they live in one table here and are written over the
#  per-clip tracks at the end. ``tools/solve_wrist.py`` owns this block: it
#  searches wrist angles that keep the drawn blade out of the boss's own head,
#  chest and pauldron, then rewrites these rows.
#  Entry format: clip -> [(seconds, x degrees, z degrees), ...]
# <<< WEAPON_KEYS >>>
WEAPON_KEYS = {
    # every clip starts and ends on the resting wrist so the action controller
    # blends back into idle without snapping the blade through the shoulder
    "idle": [(0.0, -26, -34), (2.0, -30, -38), (4.0, -26, -34)],
    "walk": [(0.0, -22, -30), (1.0, -26, -34), (2.0, -22, -30)],
    "cleave_left": [(0.0, -26, -34), (0.35, -90, 0), (0.5, -170, 10), (0.65, -120, 30),
                    (1.0, -26, -34)],
    "cleave_right": [(0.0, -26, -34), (0.35, 60, 30), (0.5, 160, 30), (0.65, 110, 15),
                     (1.0, -26, -34)],
    "overhead": [(0.0, -26, -34), (0.55, -40, 20), (0.8, -160, 60), (0.95, -120, 45),
                 (1.4, -26, -34)],
    "thrust": [(0.0, -26, -34), (0.45, -60, 20), (0.6, -170, 50), (0.8, -156, 42),
               (1.2, -26, -34)],
    "sweep": [(0.0, -26, -34), (0.3, 160, 30), (0.6, 170, 30), (1.0, 100, 0),
              (1.25, 90, 0), (1.7, -26, -34)],
    "spear_throw": [(0.0, -26, -34), (0.35, -60, 0), (0.6, -180, -30), (0.95, -166, -24),
                    (1.5, -26, -34)],
    "shield_bash": [(0.0, -26, -34), (0.4, 10, -20), (0.5, -30, -60), (1.1, -26, -34)],
    "war_cry": [(0.0, -26, -34), (0.5, -30, -20), (1.3, -60, -60), (2.2, -26, -34)],
    "phase_shift": [(0.0, -26, -34), (0.9, 48, 0), (1.5, 52, 0), (1.65, 39, 0),
                    (1.7, 40, 10), (1.75, 53, 5), (1.8, -14, 0), (2.6, -26, -34)],
    "death": [(0.0, -26, -34), (1.4, 18, -14), (1.7, 19, -7), (1.75, 18, -7),
              (1.8, 17, -17), (1.85, 16, -16), (1.9, 15, -15), (1.95, 14, -14),
              (2.0, 13, -13), (2.05, 21, -12), (2.1, 29, -10), (2.15, 25, -18),
              (2.2, 20, -20), (2.25, 13, -24), (2.3, 0, -20), (2.35, -22, -11),
              (2.4, -34, 18)],
    "spawn": [(0.0, 40, 0), (1.8, 30, -10), (2.6, -26, -34)],
}
# <<< END WEAPON_KEYS >>>


# ---------------------------------------------------------------------------
#  animations -- clip lengths are the tick budgets in CanglanWarlordAttackRules
# ---------------------------------------------------------------------------
A = {}


def clip(name, seconds, loop=False):
    A[name] = {"animation_length": round(seconds, 3), "bones": {}}
    if loop:
        A[name]["loop"] = True
    return A[name]["bones"]


def rot(bones, bone, keys):
    bones.setdefault(bone, {})["rotation"] = {f"{t:.3f}": [round(v, 2) for v in vec]
                                              for t, vec in keys}


def pos(bones, bone, keys):
    bones.setdefault(bone, {})["position"] = {f"{t:.3f}": [round(v, 2) for v in vec]
                                              for t, vec in keys}


def secondary(bones, length, sway=6.0, delay=0.0):
    """Cape, banner, tassel and crest all follow the body with a phase delay."""
    chains = (("cape_a", 1.0), ("cape_b", 1.35), ("cape_c", 1.7), ("cape_d", 2.0),
              ("banner_a", 1.1), ("banner_b", 1.4), ("banner_c", 1.7),
              ("crest_a", 0.55), ("crest_b", 0.8), ("crest_c", 1.0))
    for bone, weight in chains:
        amp = sway * weight
        keys = [(0.0, [0, 0, 0]),
                (length * 0.33, [-amp, amp * 0.18, 0]),
                (length * 0.66, [-amp * 0.55, -amp * 0.12, 0]),
                (length, [0, 0, 0])]
        rot(bones, bone, keys)


def write_animations():
    A.clear()

    # ---------------- idle: breathing, scanning, everything settling ----------------
    b = clip("idle", 4.0, True)
    rot(b, "torso", [(0, [0.6, 0, 0]), (2, [1.8, 0, 0]), (4, [0.6, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (2, [-1.6, 0, 0]), (4, [0, 0, 0])])
    rot(b, "head", [(0, [1, 0, 0]), (1.2, [0.5, 4, 0]), (2.2, [-1.5, 1, 0]),
                    (3.2, [0.5, -4, 0]), (4, [1, 0, 0])])
    rot(b, "jaw", [(0, [0, 0, 0]), (2, [2.5, 0, 0]), (4, [0, 0, 0])])
    pos(b, "root", [(0, [0, 0, 0]), (2, [0, 0.7, 0]), (4, [0, 0, 0])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "arm_" + k, [(0, [0, 0, s * 2]), (2, [1.6, 0, s * 3]), (4, [0, 0, s * 2])])
        rot(b, "fore_" + k, [(0, [0, 0, 0]), (2, [-3.0, 0, 0]), (4, [0, 0, 0])])
        rot(b, "pauldron_" + k, [(0, [0, 0, 0]), (2, [0, 0, s * 1.4]), (4, [0, 0, 0])])
    rot(b, "weapon_1", [(0, [-26, 0, -34]), (2, [-30, 0, -38]), (4, [-26, 0, -34])])
    rot(b, "cape_a", [(0, [0, 0, 0]), (1.3, [-3.5, 1.2, 0]), (2.7, [-2.0, -1.0, 0]),
                      (4, [0, 0, 0])])
    rot(b, "cape_b", [(0, [-1, 0, 0]), (1.5, [-5, 1.6, 0]), (2.9, [-2.4, -1.4, 0]),
                      (4, [-1, 0, 0])])
    rot(b, "cape_c", [(0, [-2, 0, 0]), (1.7, [-6.5, 2.0, 0]), (3.1, [-3.0, -1.8, 0]),
                      (4, [-2, 0, 0])])
    rot(b, "cape_d", [(0, [-2, 0, 0]), (1.9, [-8, 2.4, 0]), (3.3, [-3.6, -2.2, 0]),
                      (4, [-2, 0, 0])])
    rot(b, "crest_a", [(0, [0, 0, 0]), (2, [-3, 1.5, 0]), (4, [0, 0, 0])])
    rot(b, "crest_b", [(0, [-1, 0, 0]), (2.2, [-5, 2.0, 0]), (4, [-1, 0, 0])])
    rot(b, "crest_c", [(0, [-1, 0, 0]), (2.4, [-7, 2.5, 0]), (4, [-1, 0, 0])])
    rot(b, "banner_a", [(0, [-2, 0, 0]), (1.6, [-5, 1.8, 0]), (3.0, [-2.5, -1.5, 0]),
                        (4, [-2, 0, 0])])
    rot(b, "banner_b", [(0, [-2, 0, 0]), (1.9, [-7, 2.4, 0]), (3.4, [-3.4, -2.0, 0]),
                        (4, [-2, 0, 0])])
    rot(b, "banner_c", [(0, [-2, 0, 0]), (2.1, [-9, 3.0, 0]), (3.6, [-4.2, -2.5, 0]),
                        (4, [-2, 0, 0])])

    # ---------------- walk: a heavy 2s stomp cycle ----------------
    def cycle(table, shift, length=2.0, step=0.25):
        """Phase-shifted key list for one leg/arm, wrapping around the cycle."""
        def value(phase):
            for (t0, v0), (t1, v1) in zip(table, table[1:]):
                if t0 <= phase <= t1:
                    f = (phase - t0) / (t1 - t0)
                    return v0 + (v1 - v0) * f
            return table[-1][1]

        keys = []
        t = 0.0
        while t <= length + 1e-9:
            keys.append((round(t, 3), [round(value((t + shift) % length), 2), 0, 0]))
            t += step
        return keys

    b = clip("walk", 2.0, True)
    pos(b, "root", [(0, [0, 1.5, 0]), (0.25, [0, 3.5, 0]), (0.5, [0, 1.9, 0]),
                    (0.75, [0, 3.5, 0]), (1.0, [0, 1.5, 0]), (1.25, [0, 3.5, 0]),
                    (1.5, [0, 1.9, 0]), (1.75, [0, 3.5, 0]), (2.0, [0, 1.5, 0])])
    rot(b, "pelvis", [(0, [0, 0, 2.6]), (1.0, [0, 0, -2.6]), (2.0, [0, 0, 2.6])])
    rot(b, "torso", [(0, [2.0, 3.0, -2.0]), (1.0, [2.0, -3.0, 2.0]), (2.0, [2.0, 3.0, -2.0])])
    rot(b, "chest", [(0, [0, 1.5, 0]), (1.0, [0, -1.5, 0]), (2.0, [0, 1.5, 0])])
    rot(b, "head", [(0, [0, -3, 0]), (1.0, [0, 3, 0]), (2.0, [0, -3, 0])])
    thigh_gait = [(0.0, 16), (0.5, 0), (1.0, -16), (1.5, 0), (2.0, 16)]
    shin_gait = [(0.0, -2), (0.25, -8), (0.5, 0), (0.75, 22), (1.0, 2),
                 (1.25, 4), (1.5, -2), (1.75, -2), (2.0, -2)]
    foot_gait = [(0.0, 6), (0.2, 2), (0.5, 0), (0.75, -4), (1.0, 0),
                 (1.4, 3), (1.7, 6), (2.0, 6)]
    for s in (-1, 1):
        k = str(s)
        # +1 side starts with the leg forward; -1 side is half a cycle behind
        shift = 0.0 if s == 1 else 1.0
        rot(b, "thigh_" + k, cycle(thigh_gait, shift))
        rot(b, "shin_" + k, cycle(shin_gait, shift))
        rot(b, "foot_" + k, cycle(foot_gait, shift))
        rot(b, "arm_" + k, cycle([(0.0, -13), (1.0, 13), (2.0, -13)],
                                 shift, step=0.5))
        rot(b, "fore_" + k, cycle([(0.0, -6), (1.0, -16), (2.0, -6)],
                                  shift, step=0.5))
        rot(b, "pauldron_" + k, [(0, [0, 0, s * -2]), (1.0, [0, 0, s * 2]),
                                 (2.0, [0, 0, s * -2])])
    rot(b, "weapon_1", [(0, [-22, 0, -30]), (1.0, [-26, 0, -34]), (2.0, [-22, 0, -30])])
    secondary(b, 2.0, sway=7.0)

    # ---------------- cleave_left: 20 ticks, connects at tick 10 ----------------
    b = clip("cleave_left", 1.0)
    rot(b, "root", [(0, [0, 0, 0]), (0.35, [0, 12, 0]), (0.5, [0, -20, 0]),
                    (0.65, [0, -10, 0]), (1.0, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.35, [4, 30, 0]), (0.5, [-8, -32, 0]),
                     (0.65, [-5, -20, 0]), (1.0, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.35, [2, 14, 0]), (0.5, [-4, -16, 0]),
                     (0.65, [-2, -8, 0]), (1.0, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.35, [0, -20, 0]), (0.5, [2, 22, 0]),
                    (0.65, [0, 10, 0]), (1.0, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.35, [-38, 0, 30]), (0.5, [58, 0, 60]),
                     (0.65, [46, 0, 46]), (1.0, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.35, [-18, 0, 0]), (0.5, [-4, 0, 0]),
                      (0.65, [-10, 0, 0]), (1.0, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.35, [-14, 0, -18]), (0.5, [8, 0, -12]),
                      (1.0, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.35, [-90, 0, 0]), (0.5, [-170, 0, 10]),
                      (0.65, [-120, 0, 30]), (1.0, [0, 0, 0])])
    rot(b, "thigh_1", [(0, [0, 0, 0]), (0.35, [-6, 0, 0]), (0.5, [8, 0, 0]),
                       (1.0, [0, 0, 0])])
    rot(b, "thigh_-1", [(0, [0, 0, 0]), (0.5, [-6, 0, 0]), (1.0, [0, 0, 0])])
    secondary(b, 1.0, sway=14.0)

    # ---------------- cleave_right: mirrored 20-tick reverse cut ----------------
    b = clip("cleave_right", 1.0)
    rot(b, "root", [(0, [0, 0, 0]), (0.35, [0, -14, 0]), (0.5, [0, 22, 0]),
                    (0.65, [0, 10, 0]), (1.0, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.35, [4, -28, 0]), (0.5, [-9, 30, 0]),
                     (0.65, [-5, 18, 0]), (1.0, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.35, [2, -14, 0]), (0.5, [-5, 15, 0]),
                     (0.65, [-2, 8, 0]), (1.0, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.35, [0, 18, 0]), (0.5, [2, -20, 0]),
                    (1.0, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.35, [-40, 0, 52]), (0.5, [20, 0, 50]),
                     (0.65, [12, 0, 36]), (1.0, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.35, [-24, 0, 0]), (0.5, [-4, 0, 0]),
                      (1.0, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.35, [6, 0, -10]), (0.5, [-16, 0, -22]),
                      (1.0, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.35, [60, 0, 30]), (0.5, [160, 0, 30]),
                      (0.65, [110, 0, 15]), (1.0, [0, 0, 0])])
    rot(b, "thigh_1", [(0, [0, 0, 0]), (0.5, [-8, 0, 0]), (1.0, [0, 0, 0])])
    rot(b, "thigh_-1", [(0, [0, 0, 0]), (0.5, [6, 0, 0]), (1.0, [0, 0, 0])])
    secondary(b, 1.0, sway=13.0)

    # ---------------- overhead: 28 ticks, connects at tick 16 ----------------
    b = clip("overhead", 1.4)
    pos(b, "root", [(0, [0, 0, 0]), (0.55, [0, 2.0, 0]), (0.8, [0, -6.0, 0]),
                    (0.95, [0, -3.0, 0]), (1.4, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.55, [14, 0, 0]), (0.8, [-24, 0, 0]),
                     (0.95, [-14, 0, 0]), (1.4, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.55, [8, 0, 0]), (0.8, [-12, 0, 0]),
                     (1.4, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.55, [-10, 0, 0]), (0.8, [18, 0, 0]),
                    (1.4, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.55, [-152, 0, 12]), (0.8, [48, 0, 6]),
                     (0.95, [38, 0, 4]), (1.4, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.55, [-28, 0, 0]), (0.8, [-4, 0, 0]),
                      (1.4, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.55, [-56, 0, -24]), (0.8, [22, 0, -8]),
                      (1.4, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.55, [30, 0, 10]), (0.8, [-160, 0, 60]),
                      (0.95, [-120, 0, 45]), (1.4, [0, 0, 0])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "thigh_" + k, [(0, [0, 0, 0]), (0.55, [-4, 0, 0]), (0.8, [16, 0, 0]),
                              (1.4, [0, 0, 0])])
        rot(b, "shin_" + k, [(0, [0, 0, 0]), (0.8, [-22, 0, 0]), (1.4, [0, 0, 0])])
    secondary(b, 1.4, sway=16.0)

    # ---------------- thrust: 24 ticks, connects at tick 12 ----------------
    b = clip("thrust", 1.2)
    pos(b, "root", [(0, [0, 0, 0]), (0.45, [0, 0, 4.0]), (0.6, [0, 0, -14.0]),
                    (0.8, [0, 0, -12.0]), (1.2, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.45, [4, -24, 0]), (0.6, [-10, -6, 0]),
                     (0.8, [-8, -4, 0]), (1.2, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.45, [2, -12, 0]), (0.6, [-6, -2, 0]),
                     (1.2, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.45, [0, 16, 0]), (0.6, [4, 4, 0]),
                    (1.2, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.45, [-24, 0, 30]), (0.6, [72, 0, 10]),
                     (0.8, [66, 0, 8]), (1.2, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.45, [-14, 0, 0]), (0.6, [-2, 0, 0]),
                      (1.2, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.45, [-18, 0, -16]), (0.6, [-30, 0, -26]),
                      (1.2, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.45, [-60, 0, 20]), (0.6, [-170, 0, 50]),
                      (0.8, [-156, 0, 42]), (1.2, [0, 0, 0])])
    rot(b, "thigh_1", [(0, [0, 0, 0]), (0.45, [-8, 0, 0]), (0.6, [14, 0, 0]),
                       (1.2, [0, 0, 0])])
    rot(b, "thigh_-1", [(0, [0, 0, 0]), (0.6, [-12, 0, 0]), (1.2, [0, 0, 0])])
    rot(b, "shin_-1", [(0, [0, 0, 0]), (0.6, [10, 0, 0]), (1.2, [0, 0, 0])])
    secondary(b, 1.2, sway=17.0)

    # ---------------- sweep: 34 ticks, 360 spin, hits at 12 and 20 ticks ----------------
    b = clip("sweep", 1.7)
    rot(b, "root", [(0, [0, 0, 0]), (0.3, [0, -40, 0]), (0.9, [0, 230, 0]),
                    (1.5, [0, 360, 0]), (1.7, [0, 360, 0])])
    pos(b, "root", [(0, [0, 0, 0]), (0.6, [0, 2.2, 0]), (1.2, [0, 0.6, 0]),
                    (1.7, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.35, [-6, -18, -6]), (1.0, [-8, 10, -10]),
                     (1.6, [0, 4, 0]), (1.7, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.35, [2, -10, -4]), (1.0, [2, 8, 4]),
                     (1.7, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.35, [0, 22, 0]), (1.0, [0, -16, 0]),
                    (1.7, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.3, [-20, 0, 66]), (1.25, [-16, 0, 72]),
                     (1.7, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.3, [-12, 0, 0]), (1.25, [-8, 0, 0]),
                      (1.7, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.4, [-10, 0, -58]), (1.3, [-8, 0, -62]),
                      (1.7, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.3, [160, 0, 30]), (0.6, [170, 0, 30]),
                      (1.0, [100, 0, 0]), (1.25, [90, 0, 0]), (1.7, [0, 0, 0])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "thigh_" + k, [(0, [0, 0, 0]), (0.3, [-6, 0, s * 8]), (1.2, [6, 0, s * 10]),
                              (1.7, [0, 0, 0])])
        rot(b, "shin_" + k, [(0, [0, 0, 0]), (0.6, [-12, 0, 0]), (1.7, [0, 0, 0])])
    secondary(b, 1.7, sway=22.0)

    # ---------------- spear_throw: 30 ticks, the blade leaves the hand at 12 ----------------
    b = clip("spear_throw", 1.5)
    pos(b, "root", [(0, [0, 0, 0]), (0.35, [0, 0, 6.0]), (0.6, [0, 0, -8.0]),
                    (0.95, [0, 0, -6.0]), (1.5, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.35, [2, 34, 0]), (0.6, [-12, -16, 0]),
                     (0.95, [-8, -10, 0]), (1.5, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.35, [0, 18, 0]), (0.6, [-8, -8, 0]),
                     (1.5, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.35, [0, -20, 0]), (0.6, [6, 0, 0]),
                    (1.5, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.35, [-46, 0, 22]), (0.6, [84, 0, 6]),
                     (0.95, [76, 0, 4]), (1.5, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.35, [-32, 0, 0]), (0.6, [10, 0, 0]),
                      (0.95, [6, 0, 0]), (1.5, [0, 0, 0])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.35, [-60, 0, 0]), (0.6, [-180, 0, -30]),
                        (0.95, [-166, 0, -24]), (1.5, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.35, [18, 0, -24]), (0.6, [-34, 0, -18]),
                      (1.5, [0, 0, -2])])
    rot(b, "thigh_1", [(0, [0, 0, 0]), (0.35, [-10, 0, 0]), (0.6, [16, 0, 0]),
                       (1.5, [0, 0, 0])])
    rot(b, "thigh_-1", [(0, [0, 0, 0]), (0.6, [-14, 0, 0]), (1.5, [0, 0, 0])])
    rot(b, "shin_-1", [(0, [0, 0, 0]), (0.6, [12, 0, 0]), (1.5, [0, 0, 0])])
    secondary(b, 1.5, sway=18.0)

    # ---------------- shield_bash: 22 ticks, connects at tick 10 ----------------
    b = clip("shield_bash", 1.1)
    pos(b, "root", [(0, [0, 0, 0]), (0.4, [0, 0, 3.0]), (0.5, [0, 0, -11.0]),
                    (0.75, [0, 0, -9.0]), (1.1, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.4, [0, 18, 0]), (0.5, [-8, 10, 0]),
                     (1.1, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.4, [0, 10, 0]), (0.5, [-4, 4, 0]),
                     (1.1, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.4, [-4, -10, 0]), (0.5, [6, -4, 0]),
                    (1.1, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.4, [-30, 0, -34]), (0.5, [82, 0, -12]),
                      (0.75, [74, 0, -14]), (1.1, [0, 0, -2])])
    rot(b, "fore_-1", [(0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.5, [-8, 0, 0]),
                       (1.1, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.4, [-8, 0, 14]), (0.5, [-24, 0, 26]),
                     (1.1, [0, 0, 2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.4, [10, 0, -20]), (0.5, [-30, 0, -60]),
                      (1.1, [0, 0, 0])])
    rot(b, "thigh_1", [(0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.1, [0, 0, 0])])
    rot(b, "thigh_-1", [(0, [0, 0, 0]), (0.4, [-6, 0, 0]), (0.5, [-14, 0, 0]),
                        (1.1, [0, 0, 0])])
    secondary(b, 1.1, sway=12.0)

    # ---------------- war_cry: 44 ticks ----------------
    b = clip("war_cry", 2.2)
    pos(b, "root", [(0, [0, 0, 0]), (0.5, [0, 1.5, 0]), (1.4, [0, 2.2, 0]), (2.2, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (1.3, [14, 0, 0]), (2.2, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.5, [8, 0, 0]), (1.3, [12, 0, 0]), (2.2, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.5, [18, 0, 0]), (1.0, [24, 0, 0]), (1.6, [22, 0, 0]),
                    (2.2, [0, 0, 0])])
    rot(b, "jaw", [(0, [0, 0, 0]), (0.5, [-22, 0, 0]), (1.6, [-18, 0, 0]), (2.2, [0, 0, 0])])
    secondary(b, 2.2, sway=10.0)
    for s in (-1, 1):
        k = str(s)
        rot(b, "arm_" + k, [(0, [0, 0, s * 2]), (0.5, [-26, 0, s * 72]),
                            (1.3, [-30, 0, s * 78]), (2.2, [0, 0, s * 2])])
        rot(b, "fore_" + k, [(0, [0, 0, 0]), (0.5, [-34, 0, 0]), (1.3, [-40, 0, 0]),
                             (2.2, [0, 0, 0])])
        rot(b, "pauldron_" + k, [(0, [0, 0, 0]), (0.5, [0, 0, s * 6]), (1.3, [0, 0, s * 8]),
                                 (2.2, [0, 0, 0])])
        rot(b, "thigh_" + k, [(0, [0, 0, 0]), (0.6, [4, 0, 0]), (1.4, [2, 0, 0]),
                              (2.2, [0, 0, 0])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.5, [-30, 0, -20]), (1.3, [-60, 0, -60]),
                      (2.2, [0, 0, 0])])

    # ---------------- phase_shift: 52 ticks, the half-health roar ----------------
    b = clip("phase_shift", 2.6)
    pos(b, "root", [(0, [0, 0, 0]), (0.9, [0, -7, 0]), (1.5, [0, -7, 0]),
                    (1.8, [0, 1, 0]), (2.6, [0, 0, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.9, [-20, 0, 0]), (1.5, [-22, 0, 0]),
                     (1.8, [18, 0, 0]), (2.2, [12, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.8, [14, 0, 0]),
                     (2.6, [0, 0, 0])])
    rot(b, "head", [(0, [0, 0, 0]), (0.9, [-22, 0, 0]), (1.5, [-18, 0, 0]),
                    (1.8, [28, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "jaw", [(0, [0, 0, 0]), (1.6, [-26, 0, 0]), (2.4, [-10, 0, 0]),
                   (2.6, [0, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.9, [26, 0, 10]), (1.5, [30, 0, 8]),
                     (1.8, [-34, 0, 64]), (2.3, [-10, 0, 30]), (2.6, [0, 0, 2])])
    rot(b, "fore_1", [(0, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.8, [-30, 0, 0]),
                      (2.6, [0, 0, 0])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.9, [10, 0, -6]), (1.8, [-34, 0, -70]),
                      (2.3, [-10, 0, -32]), (2.6, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (0.9, [48, 0, 0]), (1.5, [52, 0, 0]),
                      (1.8, [6, 0, 0]), (2.6, [0, 0, 0])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "thigh_" + k, [(0, [0, 0, 0]), (0.9, [26, 0, s * 4]), (1.5, [26, 0, s * 4]),
                              (1.8, [-4, 0, 0]), (2.6, [0, 0, 0])])
        rot(b, "shin_" + k, [(0, [0, 0, 0]), (0.9, [-34, 0, 0]), (1.5, [-34, 0, 0]),
                             (1.8, [0, 0, 0]), (2.6, [0, 0, 0])])
        rot(b, "pauldron_" + k, [(0, [0, 0, 0]), (1.8, [0, 0, s * 8]), (2.4, [0, 0, s * 2]),
                                 (2.6, [0, 0, 0])])
    secondary(b, 2.6, sway=9.0)
    # the cape tears open and the banner snaps out as the second phase begins
    for name, amp in (("cape_a", -26), ("cape_b", -34), ("cape_c", -42), ("cape_d", -50),
                      ("banner_a", -22), ("banner_b", -30), ("banner_c", -38)):
        rot(b, name, [(0, [0, 0, 0]), (1.0, [amp * 0.35, 0, 0]), (1.7, [amp, 0, 0]),
                      (2.1, [amp * 0.8, 0, 0]), (2.6, [0, 0, 0])])

    # ---------------- death: 48 ticks, falls backwards ----------------
    b = clip("death", 2.4)
    rot(b, "root", [(0, [0, 0, 0]), (0.5, [6, 0, -4]), (1.2, [18, 0, 6]),
                    (2.0, [62, 0, 10]), (2.4, [80, 0, 12])])
    pos(b, "root", [(0, [0, 0, 0]), (0.5, [0, 1, 0]), (1.2, [0, 3, 0]),
                    (2.0, [0, 2, 0]), (2.4, [0, 1, 0])])
    rot(b, "torso", [(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (1.0, [-6, 0, 0]),
                     (2.0, [10, 0, 6]), (2.4, [6, 0, 8])])
    rot(b, "chest", [(0, [0, 0, 0]), (0.4, [-8, 0, 0]), (2.4, [6, 0, 4])])
    rot(b, "head", [(0, [0, 0, 0]), (0.4, [-26, 0, 0]), (1.2, [22, 0, 0]),
                    (2.4, [34, 0, 6])])
    rot(b, "jaw", [(0, [0, 0, 0]), (0.6, [-24, 0, 0]), (1.2, [6, 0, 0]), (2.4, [10, 0, 0])])
    rot(b, "arm_1", [(0, [0, 0, 2]), (0.6, [-20, 0, 18]), (1.4, [10, 0, 26]),
                     (2.4, [-30, 0, 40])])
    rot(b, "arm_-1", [(0, [0, 0, -2]), (0.6, [-16, 0, -20]), (1.4, [8, 0, -30]),
                      (2.4, [-26, 0, -48])])
    rot(b, "weapon_1", [(0, [0, 0, 0]), (1.4, [18, 0, -14]), (2.4, [-24, 0, 18])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "thigh_" + k, [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (1.4, [-6, 0, 0]),
                              (2.4, [12, 0, 0])])
        rot(b, "shin_" + k, [(0, [0, 0, 0]), (0.5, [-24, 0, 0]), (1.4, [-10, 0, 0]),
                             (2.4, [-36, 0, 0])])
    secondary(b, 2.4, sway=8.0)

    # ---------------- spawn: 52 ticks, rises out of a war-crouch ----------------
    b = clip("spawn", 2.6)
    pos(b, "root", [(0, [0, -10, 0]), (1.0, [0, -8, 0]), (1.8, [0, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "torso", [(0, [-30, 0, 0]), (0.9, [-20, 0, 0]), (1.8, [4, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "chest", [(0, [-12, 0, 0]), (1.8, [4, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "head", [(0, [-28, 0, 0]), (0.9, [-20, 0, 0]), (1.8, [6, 0, 0]), (2.6, [0, 0, 0])])
    rot(b, "arm_1", [(0, [-20, 0, 6]), (1.8, [-6, 0, 10]), (2.6, [0, 0, 2])])
    rot(b, "arm_-1", [(0, [-20, 0, -6]), (1.8, [-6, 0, -10]), (2.6, [0, 0, -2])])
    rot(b, "weapon_1", [(0, [40, 0, 0]), (1.8, [10, 0, 0]), (2.6, [0, 0, 0])])
    for s in (-1, 1):
        k = str(s)
        rot(b, "thigh_" + k, [(0, [46, 0, s * 6]), (1.0, [34, 0, s * 4]), (1.8, [6, 0, 0]),
                              (2.6, [0, 0, 0])])
        rot(b, "shin_" + k, [(0, [-56, 0, 0]), (1.0, [-44, 0, 0]), (1.8, [-6, 0, 0]),
                             (2.6, [0, 0, 0])])
    secondary(b, 2.6, sway=9.0)

    # the drawn blade's wrist table wins over whatever each clip authored
    for name, keys in WEAPON_KEYS.items():
        if name in A:
            A[name]["bones"].setdefault("weapon_1", {})["rotation"] = {
                f"{t:.3f}": [round(x, 2), 0.0, round(z, 2)] for t, x, z in keys}

    ANIM.parent.mkdir(parents=True, exist_ok=True)
    ANIM.write_text(json.dumps({"format_version": "1.8.0", "animations": A}, indent=2),
                    encoding="utf-8", newline="\n")


# ---------------------------------------------------------------------------
#  texture atlas: four 128x128 material quadrants
# ---------------------------------------------------------------------------
def paint_texture():
    def tone(c, delta):
        return tuple(max(0, min(255, int(v + delta))) for v in c)

    canvas = Image.new("RGBA", (256, 256), (19, 17, 20, 255))
    canvas_draw = ImageDraw.Draw(canvas)

    def quadrant(origin, base, bright, dark, style):
        tile = Image.new("RGBA", (128, 128), base + (255,))
        draw = ImageDraw.Draw(tile)
        for gy in range(4):
            for gx in range(4):
                x0, y0 = gx * 32, gy * 32
                cell = tone(base, random.randint(-9, 9))
                draw.rectangle([x0, y0, x0 + 31, y0 + 31], fill=cell + (255,))
                draw.line([x0 + 1, y0 + 1, x0 + 30, y0 + 1], fill=bright + (255,))
                draw.line([x0 + 1, y0 + 1, x0 + 1, y0 + 30], fill=tone(bright, -10) + (255,))
                draw.line([x0 + 1, y0 + 30, x0 + 30, y0 + 30], fill=dark + (255,))
                draw.line([x0 + 30, y0 + 1, x0 + 30, y0 + 30], fill=tone(dark, 8) + (255,))
                if style == "steel":
                    for (rx, ry) in ((4, 4), (27, 4), (4, 27), (27, 27)):
                        draw.ellipse([x0 + rx - 1, y0 + ry - 1, x0 + rx + 1, y0 + ry + 1],
                                     fill=bright + (255,))
                    # etched gust lines: the relic metal is cut with wind channels
                    for _ in range(3):
                        sx = x0 + random.randint(3, 18)
                        sy = y0 + random.randint(5, 26)
                        draw.line([sx, sy, sx + random.randint(6, 13), sy + random.randint(-3, 3)],
                                  fill=tone(bright, -14) + (255,))
                        draw.line([sx, sy + 1, sx + random.randint(6, 13), sy + random.randint(-2, 4)],
                                  fill=tone(dark, 10) + (255,))
                    for _ in range(2):
                        sx = x0 + random.randint(4, 26)
                        sy = y0 + random.randint(4, 26)
                        draw.line([sx, sy, sx + random.randint(2, 5), sy + random.randint(-1, 1)],
                                  fill=tone(dark, 12) + (255,))
                elif style == "mist":
                    for yy in range(y0 + 3, y0 + 30, 4):
                        jitter = random.randint(-1, 1)
                        draw.line([x0 + 2, yy, x0 + 29, yy + jitter],
                                  fill=tone(base, random.randint(-22, 16)) + (255,))
                    for _ in range(3):
                        sx = x0 + random.randint(3, 24)
                        draw.line([sx, y0 + 2, sx + random.randint(2, 7), y0 + 30],
                                  fill=tone(dark, 2) + (255,))
                    for _ in range(2):
                        sx = x0 + random.randint(4, 22)
                        draw.line([sx, y0 + 3, sx + random.randint(5, 12), y0 + 29],
                                  fill=tone(bright, 4) + (255,))
                elif style == "edge":
                    for _ in range(4):
                        sx = x0 + random.randint(3, 18)
                        sy = y0 + random.randint(4, 27)
                        draw.line([sx, sy, sx + random.randint(6, 13), sy - random.randint(1, 3)],
                                  fill=bright + (255,))
                    draw.arc([x0 + 5, y0 + 5, x0 + 27, y0 + 27], 190, 350,
                             fill=tone(bright, 8) + (255,))
                    draw.line([x0 + 2, y0 + 26, x0 + 29, y0 + 22],
                              fill=tone(dark, 6) + (255,))
                else:  # gale
                    draw.rectangle([x0, y0, x0 + 31, y0 + 31], fill=dark + (255,))
                    for step in range(4):
                        inset = 5 + step * 3
                        draw.ellipse([x0 + inset, y0 + inset, x0 + 31 - inset, y0 + 31 - inset],
                                     fill=tone(base, step * 22) + (255,))
                    draw.ellipse([x0 + 12, y0 + 12, x0 + 20, y0 + 20], fill=bright + (255,))
                    # swirl arms so the glow reads as moving air, not a lamp
                    for arm in range(3):
                        draw.arc([x0 + 4 + arm * 3, y0 + 4 + arm * 3,
                                  x0 + 28 - arm * 3, y0 + 28 - arm * 3],
                                 arm * 120 + 20, arm * 120 + 150,
                                 fill=tone(bright, -6) + (255,))
        # film grain, same seed family as the geometry
        noise = Image.effect_noise((128, 128), 18).convert("L")
        tile = Image.composite(tile, tile, noise.point(lambda v: 255 if v > 150 else 0))
        grain = Image.blend(tile, Image.new("RGBA", (128, 128), tone(base, 4) + (255,)), 0.16)
        canvas.paste(grain, origin)

    # palette read off tnc:item/wind_relic_metal: slate steel, cyan-white edge,
    # mist cloth, gale glow -- the boss is cast from the same metal as the swords
    quadrant(Q["steel"], (52, 74, 86), (108, 148, 160), (26, 38, 48), "steel")
    quadrant(Q["mist"], (140, 186, 196), (188, 224, 232), (84, 122, 134), "mist")
    quadrant(Q["edge"], (86, 178, 178), (214, 244, 244), (44, 104, 112), "edge")
    quadrant(Q["gale"], (128, 224, 224), (232, 252, 255), (24, 66, 78), "gale")

    # pale rivet clusters along quadrant seams so plate borders read in game
    for y in (12, 44, 76, 108):
        for x in (12, 44, 76, 108):
            canvas_draw.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(206, 236, 240, 255))

    TEX.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(TEX)


# ---------------------------------------------------------------------------
#  entry point + self-check
# ---------------------------------------------------------------------------
def main():
    sculpt()
    bones = build_geo()
    write_geo(bones)
    write_animations()
    paint_texture()

    cubes = [c for bone in bones for c in bone["cubes"]]
    verts = len(cubes) * 24
    rot_count = sum(1 for c in cubes if "rotation" in c)
    top = max(c["origin"][1] + c["size"][1] for c in cubes)
    width = max(c["origin"][0] + c["size"][0] for c in cubes) - min(c["origin"][0] for c in cubes)

    names = set(PIVOT)
    for clip_name, clip_data in A.items():
        for bone in clip_data["bones"]:
            assert bone in names, f"{clip_name} animates missing bone {bone}"
        assert clip_data["bones"], f"{clip_name} is empty"

    print(f"{len(bones)} bones; {len(cubes)} cubes ({verts} verts); "
          f"{100 * rot_count // max(1, len(cubes))}% rotated")
    print(f"top {top:.1f}u = {top / 16:.2f} blocks; shoulder span {width:.1f}u = "
          f"{width / 16:.2f} blocks")
    print("clips: " + ", ".join(f"{k}={v['animation_length']:.2f}s" for k, v in A.items()))
    print(f"geo {GEO.stat().st_size / 1024:.0f} KB; anim {ANIM.stat().st_size / 1024:.0f} KB; "
          f"tex {TEX.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()