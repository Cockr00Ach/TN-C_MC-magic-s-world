# -*- coding: utf-8 -*-
"""
gen_table_model.py -- a 4x2 block scene table (Java block model + 64x64 wood texture).

Model space: 1 unit = 1/16 block, +Y up, origin at the block corner.
4 blocks long (x 0..64) x 2 blocks wide (z 0..32), top at y 12..14, four 3x3 legs.
The model is much bigger than one block on purpose: it is meant to be drawn by an
entity/prop renderer (or with a matching VoxelShape), not tiled as 1x1 blockstates.

ASCII only.
"""
import json, os, random
from PIL import Image

NAME = "scene_table"
TOP_Y0, TOP_Y1 = 12, 14
LEG = 3

def boxes():
    b = []
    # tabletop
    b.append(((0, TOP_Y0, 0), (64, TOP_Y1 - TOP_Y0, 32)))
    # apron just under the top
    b.append(((3, TOP_Y0 - 2, 3), (58, 2, 26)))
    # four legs
    for x in (2, 64 - 2 - LEG):
        for z in (2, 32 - 2 - LEG):
            b.append(((x, 0, z), (LEG, TOP_Y0 - 2, LEG)))
    return b

def uv(i):
    # every face samples the same 16x16 patch of the texture (uniform wood look)
    return [0, 0]

def build():
    elements = []
    for (o, s) in boxes():
        f = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            f[face] = {"uv": [0, 0, 16, 16], "texture": "#0"}
        elements.append({
            "from": [o[0], o[1], o[2]],
            "to": [o[0] + s[0], o[1] + s[1], o[2] + s[2]],
            "faces": f,
        })
    model = {
        "credit": "TN-C scene prop",
        "textures": {"0": "tnc:block/%s" % NAME, "particle": "tnc:block/%s" % NAME},
        "elements": elements,
    }
    return model

def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(20260929)
    # oak-ish planks inside the 0..16 UV patch (the rest is unused/padding)
    base = (146, 106, 64)
    dark = (118, 84, 50)
    lite = (168, 126, 80)
    for y in range(16):
        plank = y // 4
        for x in range(16):
            c = base
            if y % 4 == 0:
                c = dark                      # plank seam
            elif x % 8 == 0 and plank % 2 == 1:
                c = dark                      # staggered end seam
            j = rng.randint(-8, 8)
            c = (max(0, c[0] + j), max(0, c[1] + j), max(0, c[2] + j), 255)
            if rng.random() < 0.06:
                c = (lite[0], lite[1], lite[2], 255)
            img.putpixel((x, y), c)
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    mp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "models", "block", NAME + ".json")
    tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "block", NAME + ".png")
    pp = os.path.join(repo, "docs", "previews", NAME + "_texture.png")
    for p in (mp, tp, pp):
        os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(mp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(build(), f, indent=2, ensure_ascii=False)
        f.write("\n")
    tex = texture()
    tex.save(tp)
    tex.resize((256, 256), Image.NEAREST).save(pp)
    print("model   -> %s" % os.path.relpath(mp, repo))
    print("texture -> %s" % os.path.relpath(tp, repo))
    print("preview -> %s" % os.path.relpath(pp, repo))
    print("size    : 4.0 x 0.875 x 2.0 blocks (top at y=12..14 units)")

if __name__ == "__main__":
    main()