# -*- coding: utf-8 -*-
"""
gen_light_wings_model.py -- a rigged LIGHT WING model (Bedrock geo, 1 unit = 1/16 block).

RIG CONTRACT (so it can be worn by the player AND animated in Blockbench):
    root            pivot [0,0,0]        <- the SHOULDER. Parent this to the player's back.
    wingRight       pivot [0,0,0]        leading edge (arm), extends +X
      wingRightMid  pivot [9,3,0]        mid feather panel  (rotate for the flap/trail)
        wingRightTip pivot [13,7,0]      tip feather panel
    wingLeft        pivot [0,0,0]        mirrored (-X)
      wingLeftMid   pivot [-9,3,0]
        wingLeftTip  pivot [-13,7,0]

    Flapping = rotate wingRight / wingLeft around Z; the mid/tip bones add the trailing flex,
    so "flying looks alive" works without any keyframes, but you can also K them directly.

    bbox: x -18..18  y -1..14  z 0..1  =>  2.25 blocks wide, ~0.94 blocks tall, 1/16 thick

TEXTURE: 64x64, four horizontal bands (one per feather layer, tip -> brightest):
    y 0..8 leading edge | 8..16 inner | 16..24 mid | 24..32 tip
ASCII only.
"""
import json, os, random
from PIL import Image

MODID, NAME = "tnc", "light_wings"

def cube(f, t, uv):
    return {"origin": list(f), "size": [t[0]-f[0], t[1]-f[1], t[2]-f[2]], "uv": list(uv)}

def wing(side):
    """side = +1 right, -1 left"""
    s = side
    tag = "Right" if s > 0 else "Left"
    def c(x0, y0, x1, y1, uv):
        # keep x0 < x1 regardless of side
        if s > 0:
            return cube((x0, y0, 0), (x1, y1, 1), uv)
        return cube((-x1, y0, 0), (-x0, y1, 1), uv)
    return [
        {"name": "wing" + tag, "pivot": [0, 0, 0], "cubes": [
            c(0, 0, 6, 1, (0, 0)),          # arm root
            c(6, 1, 10, 2, (0, 0)),         # arm outer
        ]},
        {"name": "wing" + tag + "Mid", "parent": "wing" + tag, "pivot": [s * 9, 3, 0], "cubes": [
            c(9, 3, 15, 9, (0, 16)),        # mid feather panel
        ]},
        {"name": "wing" + tag + "Tip", "parent": "wing" + tag + "Mid", "pivot": [s * 13, 7, 0], "cubes": [
            c(13, 7, 18, 14, (0, 24)),      # tip feather panel
        ]},
        {"name": "wing" + tag + "Inner", "parent": "wing" + tag, "pivot": [s * 5, 0, 0], "cubes": [
            c(5, -1, 11, 4, (0, 8)),        # inner short feathers
        ]},
    ]

def build():
    bones = [{"name": "root", "pivot": [0, 0, 0]}] + wing(+1) + wing(-1)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (MODID, NAME),
                "texture_width": 64, "texture_height": 64,
                "visible_bounds_width": 3, "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": bones,
        }],
    }

def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(20260930)
    bands = [(0, 8, (255, 252, 232)), (8, 16, (255, 240, 196)),
             (16, 24, (252, 220, 150)), (24, 32, (238, 198, 118))]
    for (y0, y1, base) in bands:
        for y in range(y0, y1):
            fade = 1.0 - (y - y0) / float(max(1, y1 - y0)) * 0.25
            for x in range(0, 64):
                n = rng.randint(-10, 10)
                a = 255
                if x < 2 or x > 60:
                    a = 120                      # soft edges
                r = max(0, min(255, int(base[0] * fade) + n))
                g = max(0, min(255, int(base[1] * fade) + n))
                b = max(0, min(255, int(base[2] * fade) + n))
                img.putpixel((x, y), (r, g, b, a))
        # feather separations
        for x in range(0, 64, 7):
            for y in range(y0, y1):
                p = img.getpixel((x, y))
                img.putpixel((x, y), (int(p[0]*0.85), int(p[1]*0.85), int(p[2]*0.85), p[3]))
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "geo", "entity", NAME + ".geo.json")
    tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "entity", NAME + "_bedrock.png")
    pp = os.path.join(repo, "docs", "previews", NAME + "_model_texture.png")
    for p in (gp, tp, pp):
        os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(gp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(build(), f, indent=2, ensure_ascii=False); f.write("\n")
    tex = texture(); tex.save(tp); tex.resize((256, 256), Image.NEAREST).save(pp)
    print("geo     -> %s" % os.path.relpath(gp, repo))
    print("texture -> %s" % os.path.relpath(tp, repo))
    print("bones   : root, wingRight(->Mid->Tip, Inner), wingLeft(->Mid->Tip, Inner)")
    print("bbox    : x -18..18 (2.25 格宽)  y -1..14 (0.94 格高)  z 0..1 (1/16 厚)")

if __name__ == "__main__":
    main()