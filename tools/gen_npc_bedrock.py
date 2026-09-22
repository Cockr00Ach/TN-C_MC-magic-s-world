# -*- coding: utf-8 -*-
"""
gen_npc_bedrock.py  --  NPC Bedrock model + 128x128 skin generator (animation system MMM)

WHY THIS EXISTS
===============
TN-C's self / cava / huai NPCs use the *vanilla humanoid* route:
a Java-baked model (npc/client/TnHumanoidNpcModel.java) with a 64x64 classic skin.
They have **no .geo.json at all** -- so in Blockbench there is no model to open
and no bones to record keyframes on. Only zhuangquerang has a .geo.json
(copied from the TLM maid pack).

This script generates a standard Bedrock model from the *exact* vanilla humanoid
geometry, and converts a 64x64 classic skin into the 128x128 modern layout that
Bedrock / Blockbench expect. The result is a rig you can animate in Blockbench.

It NEVER modifies existing files: it only ADDS <name>.geo.json and <name>_bedrock.png.

USAGE
=====
    python tools/gen_npc_bedrock.py self cava huai

OUTPUT (relative to repo root)
==============================
    src/main/resources/assets/tnc/geo/entity/<name>.geo.json
    src/main/resources/assets/tnc/textures/entity/<name>_bedrock.png      (128x128)

BONE NAMES (use these when wiring the Java renderer)
====================================================
    head / hat / body / armRight / armLeft / legRight / legLeft

Geometry comes from TnHumanoidNpcModel.createBodyLayer() (1 pixel = 1/16 block):
    head  -4,-8,-4  8x8x8      (texOffs 0,0)
      hat -4,-8,-4  8x8x8 +0.5 (texOffs 32,0)   child bone of head
    body  -4, 0,-2  8x12x4     (texOffs 16,16)
    armR  -7,-2,-2  4x12x4     (texOffs 40,16)  pivot(-5,2,0)
    armL   3,-2,-2  4x12x4     (texOffs 40,16 mirrored) pivot(5,2,0)
    legR  -4, 0,-2  4x12x4     (texOffs 0,16)   pivot(-1.9,12,0)
    legL   0, 0,-2  4x12x4     (texOffs 0,16 mirrored) pivot(1.9,12,0)

Model space: feet at y=0, top of head at y=32  ( = 2 blocks tall in game ).

NOTE ON ASCII
=============
This file is deliberately pure ASCII, same rule as tools/*.ps1 (see docs/current
status "iron rules" #7): this machine's PowerShell is 5.1 and Chinese literals in
scripts get mangled. Chinese documentation lives in docs/ instead.
"""

import io
import json
import os
import sys

from PIL import Image

MODID = "tnc"

# --------------------------------------------------------------------------
# Standard humanoid rig, copied value-by-value from TnHumanoidNpcModel.
# --------------------------------------------------------------------------
# UVs use the 128x128 modern skin layout:
#   head (0,0) / hat layer (32,0) / body (16,16) / armRight (40,16) / legRight (0,16)
#   armLeft (32,48) / legLeft (16,48)    <- modern layout stores left limbs separately
# The skin converter below pre-flips the classic left-limb texture into those slots,
# so the geometry itself needs no "mirror" flag.

BONES = [
    {
        "name": "head",
        "pivot": [0, 24, 0],
        "cubes": [
            {"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]},
        ],
    },
    {
        "name": "hat",
        "parent": "head",
        "pivot": [0, 24, 0],
        "cubes": [
            {"origin": [-4.5, 23.5, -4.5], "size": [9, 9, 9], "uv": [32, 0]},
        ],
    },
    {
        "name": "body",
        "pivot": [0, 12, 0],
        "cubes": [
            {"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]},
        ],
    },
    {
        "name": "armRight",
        "pivot": [-5, 22, 0],
        "cubes": [
            {"origin": [-7, 8, -2], "size": [4, 12, 4], "uv": [40, 16]},
        ],
    },
    {
        "name": "armLeft",
        "pivot": [5, 22, 0],
        "cubes": [
            {"origin": [3, 8, -2], "size": [4, 12, 4], "uv": [32, 48]},
        ],
    },
    {
        "name": "legRight",
        "pivot": [-1.9, 12, 0],
        "cubes": [
            {"origin": [-4, 0, -2], "size": [4, 12, 4], "uv": [0, 16]},
        ],
    },
    {
        "name": "legLeft",
        "pivot": [1.9, 12, 0],
        "cubes": [
            {"origin": [0, 0, -2], "size": [4, 12, 4], "uv": [16, 48]},
        ],
    },
]


def build_geo(name):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.%s.%s" % (MODID, name),
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1, 0],
                },
                "bones": [json.loads(json.dumps(b)) for b in BONES],
            }
        ],
    }


# --------------------------------------------------------------------------
# Skin: 64x64 classic layout -> 128x128 modern layout
# --------------------------------------------------------------------------
# A classic 64x64 skin has NO texture area for the left limbs: the game renders
# them by mirroring the right-limb region. A modern 128x128 skin DOES have slots
# for the left limbs, and the image stored there must already be the flipped
# appearance. So the conversion = scale every part 2x into its modern slot, with
# one extra horizontal flip for the two left limbs.

# (label, source box (classic), dest box (modern), flip horizontally)
SKIN_PARTS = [
    ("head ", (0, 0, 32, 16), (0, 0, 32, 16), False),
    ("hat  ", (32, 0, 64, 16), (32, 0, 64, 16), False),
    ("body ", (16, 16, 40, 32), (16, 16, 40, 32), False),
    ("armR ", (40, 16, 56, 32), (40, 16, 56, 32), False),
    ("legR ", (0, 16, 16, 32), (0, 16, 16, 32), False),
    ("armL ", (40, 16, 56, 32), (32, 48, 48, 64), True),
    ("legL ", (0, 16, 16, 32), (16, 48, 32, 64), True),
]


def convert_skin(src_path, dst_path):
    """Classic 64x64 -> modern 128x128. Returns (converted, note)."""
    im = Image.open(src_path).convert("RGBA")
    w, h = im.size
    if (w, h) == (128, 128):
        im.save(dst_path)  # already modern layout, just copy
        return False, "already 128x128, copied as-is"

    if (w, h) != (64, 64):
        raise SystemExit(
            "ERROR: %s is %dx%d -- only 64x64 (classic) or 128x128 (modern) accepted"
            % (src_path, w, h)
        )

    out = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for _label, sbox, dbox, flip in SKIN_PARTS:
        region = im.crop(sbox).resize(
            (dbox[2] - dbox[0], dbox[3] - dbox[1]), Image.NEAREST
        )
        if flip:
            region = region.transpose(Image.FLIP_LEFT_RIGHT)
        out.paste(region, (dbox[0], dbox[1]))
    out.save(dst_path)
    return True, "64x64 classic -> 128x128 modern (left limbs pre-flipped)"


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tex_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                           "textures", "entity")
    geo_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                           "geo", "entity")

    if not os.path.isdir(tex_dir):
        print("ERROR: skin folder not found: %s" % tex_dir)
        return 1
    os.makedirs(geo_dir, exist_ok=True)

    rc = 0
    for name in argv[1:]:
        src = os.path.join(tex_dir, name + ".png")
        if not os.path.isfile(src):
            print("[%s] skipped: no such skin %s" % (name, src))
            rc = 1
            continue

        # 1) model
        geo_path = os.path.join(geo_dir, name + ".geo.json")
        with io.open(geo_path, "w", encoding="utf-8") as f:
            json.dump(build_geo(name), f, ensure_ascii=False, indent="\t")
            f.write("\n")

        # 2) skin
        dst = os.path.join(tex_dir, name + "_bedrock.png")
        converted, note = convert_skin(src, dst)

        print("[%s] model -> %s (%d bones)" % (
            name, os.path.relpath(geo_path, repo), len(BONES)))
        print("[%s] skin  -> %s (%s)" % (
            name, os.path.relpath(dst, repo), note))
        if not converted:
            print("[%s] note: source skin was already 128x128" % name)

    print("")
    print("next (Blockbench): new Bedrock Entity model -> import the geo.json ->")
    print("  texture = <name>_bedrock.png -> record keyframes -> export animation JSON")
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv))
