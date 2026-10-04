# -*- coding: utf-8 -*-
"""
gen_dark_minion_model.py -- a dark summon (hooded wraith) model + texture.

1 unit = 1/16 block, +Y up, feet at y=0.  About 1.7 blocks tall, floats a little.

BONES
    root
      cloak   lower robe, tattered hem
      body    torso
        armRight / armLeft   thin sleeves
        head  -> hood, and two glowing eyes
        wisp  -> a trailing shadow wisp (pivot at the top so it can sway)

The renderer scales it per tier (t1 small ... t5 big) - one model serves the whole chain.
TEXTURE 128x128: cloth (0,0) | tattered (16,0) | eye glow (32,0) | wisp (48,0)
ASCII only.
"""
import json, os, random
from PIL import Image

MODID, NAME = "tnc", "dark_minion"
CLOTH, TATT, EYE, WISP = (0, 0), (16, 0), (32, 0), (48, 0)

def cube(f, t, uv):
    return {"origin": [f[0], f[1], f[2]], "size": [t[0]-f[0], t[1]-f[1], t[2]-f[2]], "uv": [uv[0], uv[1]]}

def box(cx, cy, cz, w, h, d, uv):
    return cube((cx - w // 2, cy, cz - d // 2), (cx - w // 2 + w, cy + h, cz - d // 2 + d), uv)

def build():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "cloak", "parent": "root", "pivot": [0, 12, 0], "cubes": [
            box(0, 0, 0, 11, 3, 7, TATT),          # tattered hem
            box(0, 3, 0, 10, 9, 6, CLOTH),         # lower cloak
        ]},
        {"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": [
            box(0, 12, 0, 8, 7, 5, CLOTH),         # torso
        ]},
        {"name": "armRight", "parent": "body", "pivot": [-4, 18, 0], "cubes": [
            box(-6, 11, 0, 3, 7, 3, CLOTH),
        ]},
        {"name": "armLeft", "parent": "body", "pivot": [4, 18, 0], "cubes": [
            box(6, 11, 0, 3, 7, 3, CLOTH),
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 19, 0], "cubes": [
            box(0, 19, 0, 7, 7, 7, CLOTH),         # head under the hood
            box(0, 22, 0, 9, 7, 9, CLOTH),         # hood
            box(0, 28, 0, 5, 2, 5, TATT),          # hood peak
            box(-2, 22, -5, 2, 1, 1, EYE),         # glowing eyes
            box(2, 22, -5, 2, 1, 1, EYE),
        ]},
        {"name": "wisp", "parent": "body", "pivot": [0, 12, 3], "cubes": [
            box(0, 5, 4, 3, 8, 2, WISP),
            box(0, 1, 5, 2, 5, 1, WISP),
        ]},
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (MODID, NAME),
                "texture_width": 128, "texture_height": 128,
                "visible_bounds_width": 3, "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }

def texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    rng = random.Random(20261002)

    def fill(x0, y0, w, h, fn):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), fn(x - x0, y - y0, w, h))

    def cloth(x, y, w, h):
        base = (34, 24, 46) if y % 4 else (24, 16, 34)
        if x % 7 == 0:
            base = (20, 14, 30)
        j = rng.randint(-4, 4)
        return (max(0, base[0]+j), max(0, base[1]+j), max(0, base[2]+j), 255)
    fill(0, 0, 16, 16, cloth)
    def tatt(x, y, w, h):
        if y > h - 4 and (x + y) % 3 == 0:
            return (0, 0, 0, 0)                       # ragged holes
        return cloth(x, y, w, h)
    fill(16, 0, 16, 16, tatt)
    def eye(x, y, w, h):
        if x in (0, w-1) or y in (0, h-1):
            return (90, 30, 140, 255)
        return (214, 150, 255, 255)                   # glowing violet
    fill(32, 0, 16, 16, eye)
    def wisp(x, y, w, h):
        a = int(200 * (1.0 - y / float(max(1, h))))   # fades downward
        return (150, 90, 220, max(0, a))
    fill(48, 0, 16, 16, wisp)
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
    print("bones   : root, cloak, body(->armRight/armLeft, head->hood+eyes, wisp)")

if __name__ == "__main__":
    main()