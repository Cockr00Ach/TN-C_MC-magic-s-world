# -*- coding: utf-8 -*-
"""
gen_angel_model.py -- a rigged ANGEL model (Bedrock geo + 128x128 texture).

1 unit = 1/16 block, +Y up, feet at y=0.  Height ~2.2 blocks.

BONES (so it can be animated / worn):
    root
      robe            (flared skirt)
      body            -> armRight, armLeft, head, wingRight, wingLeft
      head            -> halo (a floating golden ring above the head)
      wingRight -> wingRightMid -> wingRightTip
      wingLeft  -> wingLeftMid  -> wingLeftTip      (true mirror: x negated, z-rot negated, NO mirror flag)

TEXTURE 128x128 regions (uv = pixel, Bedrock box unwrap):
    skin (0,0) | robe white (0,16) | robe gold trim (16,16) | feathers (32,0) | halo gold (48,0) | hair (64,0)
ASCII only.
"""
import json, os, random
from PIL import Image

MODID, NAME = "tnc", "angel"
SKIN, ROBE, TRIM, FEATH, HALO, HAIR = (0, 0), (0, 16), (16, 16), (32, 0), (48, 0), (64, 0)

def cube(f, t, uv):
    return {"origin": [f[0], f[1], f[2]], "size": [t[0]-f[0], t[1]-f[1], t[2]-f[2]], "uv": [uv[0], uv[1]]}

def box(cx, cy, cz, w, h, d, uv):
    return cube((cx - w//2, cy, cz - d//2), (cx - w//2 + w, cy + h, cz - d//2 + d), uv)

def wing(side):
    s = side
    tag = "Right" if s > 0 else "Left"
    def c(x0, y0, x1, y1, uv):
        if s > 0:
            return cube((x0, y0, 0), (x1, y1, 1), uv)
        return cube((-x1, y0, 0), (-x0, y1, 1), uv)
    def rot(deg):
        return [0.0, 0.0, deg if s > 0 else -deg]
    def rc(x0, y0, x1, y1, uv, deg):
        d = c(x0, y0, x1, y1, uv)
        d["rotation"] = rot(deg)
        return d
    return [
        {"name": "wing" + tag, "parent": "body", "pivot": [s * 2, 22, 2], "cubes": [
            rc(2, 20, 9, 24, FEATH, -18), rc(3, 24, 10, 28, FEATH, -8),
        ]},
        {"name": "wing" + tag + "Mid", "parent": "wing" + tag, "pivot": [s * 9, 24, 1], "cubes": [
            rc(9, 24, 15, 30, FEATH, 14), rc(9, 20, 14, 24, FEATH, 6),
        ]},
        {"name": "wing" + tag + "Tip", "parent": "wing" + tag + "Mid", "pivot": [s * 14, 28, 1], "cubes": [
            rc(14, 28, 19, 36, FEATH, 26), rc(14, 24, 18, 28, FEATH, 18),
        ]},
    ]

def build():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "robe", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            box(0, 12, 0, 8, 2, 5, ROBE),                 # belt
            box(0, 0, 0, 12, 12, 8, ROBE),                # flared skirt
            box(0, 0, 0, 13, 2, 9, TRIM),                 # hem trim
        ]},
        {"name": "body", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            box(0, 14, 0, 9, 9, 5, ROBE),                 # torso
            box(0, 22, 0, 8, 2, 5, TRIM),                 # collar
        ]},
        {"name": "armRight", "parent": "body", "pivot": [-5, 22, 0], "cubes": [
            box(-7, 14, 0, 4, 9, 4, ROBE), box(-7, 10, 0, 3, 4, 3, SKIN),
        ]},
        {"name": "armLeft", "parent": "body", "pivot": [5, 22, 0], "cubes": [
            box(7, 14, 0, 4, 9, 4, ROBE), box(7, 10, 0, 3, 4, 3, SKIN),
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 23, 0], "cubes": [
            box(0, 23, 0, 8, 8, 8, SKIN),                 # head
            box(0, 29, 0, 9, 3, 9, HAIR),                 # hair cap
            box(0, 23, -4, 9, 2, 2, HAIR),                # bangs
        ]},
        {"name": "halo", "parent": "head", "pivot": [0, 33, 0], "cubes": [
            box(0, 33, -3, 8, 1, 1, HALO), box(0, 33, 3, 8, 1, 1, HALO),
            box(-4, 33, 0, 1, 1, 6, HALO), box(4, 33, 0, 1, 1, 6, HALO),
        ]},
    ] + wing(+1) + wing(-1)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (MODID, NAME),
                "texture_width": 128, "texture_height": 128,
                "visible_bounds_width": 4, "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }

def texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    rng = random.Random(20261001)
    def fill(x0, y0, w, h, fn):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), fn(x - x0, y - y0, w, h))
    fill(0, 0, 16, 16, lambda x, y, w, h: (238, 210, 186, 255) if not (x in (0, w-1) or y in (0, h-1)) else (214, 184, 158, 255))
    fill(0, 16, 16, 16, lambda x, y, w, h: (246, 244, 238, 255) if y % 4 else (226, 224, 216, 255))
    fill(16, 16, 16, 16, lambda x, y, w, h: (226, 188, 96, 255) if y % 3 else (250, 226, 150, 255))
    fill(32, 0, 16, 16, lambda x, y, w, h: (int(250 - 26 * (y / 15.0)) + rng.randint(-4, 4),) * 3 + (255,))
    fill(48, 0, 16, 16, lambda x, y, w, h: (255, 236, 168, 255) if y % 2 else (246, 206, 104, 255))
    fill(64, 0, 16, 16, lambda x, y, w, h: (250, 244, 226, 255) if (x + y) % 3 else (232, 222, 198, 255))
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
    t = texture(); t.save(tp); t.resize((256, 256), Image.NEAREST).save(pp)
    print("geo     -> %s" % os.path.relpath(gp, repo))
    print("texture -> %s" % os.path.relpath(tp, repo))
    print("preview -> %s" % os.path.relpath(pp, repo))
    print("bones   : root, robe, body(->armRight/armLeft/head->halo, wingRight/Left->Mid->Tip)")

if __name__ == "__main__":
    main()