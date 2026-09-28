#!/usr/bin/env python
"""
gen_zuowang_skin.py -- derive the 64x64 fallback skin for the NPC "zuowang".

Why this exists
---------------
The NPC has two rendering paths, exactly like "self" and "zhuangquerang":

  * GeckoLib present  -> GeckoLib draws the Bedrock model and samples the
                         128x128 sheet (zuowang_bedrock.png);
  * GeckoLib missing  -> the vanilla humanoid model draws it and samples a
                         64x64 vanilla-layout skin (zuowang_humanoid.png).

tools/gen_zuowang_model.py only writes the Bedrock sheet.  For zuowang the
humanoid parts are painted at the *vanilla* UVs, inside x 0..64 / y 0..64:
the generator states this explicitly and every body cube's uv proves it
(head 0,0 / body 16,16 / armR 40,16 / armL 32,48 / legR 0,16 / legL 16,48).
All hair cubes are packed into the free area (x 64..128 and y 64..128), so a
plain crop of the top-left quadrant is a complete, correct vanilla skin.

That is NOT true for "self": its Bedrock sheet was painted at a different
scale and had to be reduced by hand.  Do not assume this crop works for other
NPCs without checking where their body cubes' UVs land.

ASCII only on purpose: this console is cp936 and CJK would come out as mojibake
in the pwsh/PowerShell 5.1 pipeline (project iron rule 7).

Usage:
    python tools/gen_zuowang_skin.py
"""

import os
import sys

from PIL import Image

MODID = "tnc"
NAME = "zuowang"

# Everything the vanilla humanoid model samples lives here.
HUMANOID_BOX = (0, 0, 64, 64)


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    repo = repo_root()
    tex_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID, "textures", "entity")
    bedrock_path = os.path.join(tex_dir, "%s_bedrock.png" % NAME)
    humanoid_path = os.path.join(tex_dir, "%s_humanoid.png" % NAME)

    if not os.path.exists(bedrock_path):
        print("ERROR: missing bedrock sheet: %s" % bedrock_path)
        return 1

    sheet = Image.open(bedrock_path).convert("RGBA")
    if sheet.size != (128, 128):
        print("ERROR: expected a 128x128 sheet, got %s" % (sheet.size,))
        return 1

    humanoid = sheet.crop(HUMANOID_BOX)

    opaque = sum(1 for p in humanoid.getdata() if p[3] > 0)
    if opaque == 0:
        print("ERROR: the humanoid quadrant is fully transparent - the body was "
              "painted somewhere else; do NOT ship this as a fallback skin")
        return 1

    os.makedirs(os.path.dirname(humanoid_path), exist_ok=True)
    humanoid.save(humanoid_path)
    print("wrote %s (%dx%d, %d opaque pixel(s))"
          % (humanoid_path, humanoid.width, humanoid.height, opaque))

    # 4x preview so the result can be eyeballed without launching the game.
    prev_dir = os.path.join(repo, "docs", "previews")
    os.makedirs(prev_dir, exist_ok=True)
    prev_path = os.path.join(prev_dir, "%s_humanoid_preview.png" % NAME)
    humanoid.resize((256, 256), Image.NEAREST).save(prev_path)
    print("wrote %s (4x preview)" % prev_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
