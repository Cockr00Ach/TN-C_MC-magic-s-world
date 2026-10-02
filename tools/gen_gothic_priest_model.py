# -*- coding: utf-8 -*-
"""
gen_gothic_priest_model.py -- a rigged GOTHIC HIGH PRIEST (Bedrock geo + 128x128 texture).

1 unit = 1/16 block, +Y up, feet at y=0.  Height ~2.15 blocks (hood peak a bit higher).

BONES
    root
      robe        flared black skirt + crimson hem + belt
      body        torso with a gold front panel
        stole     two dangling front strips (pivot at the TOP so they sway)
        cross     gold pendant on the chest (child of stole)
        cape      wide crimson-lined cape hanging down the back
        armRight -> staff      (shaft + a cross head, held in the hand)
        armLeft
        head      pale face inside a deep hood
          hoodTail   small cloth tail at the back of the hood

TEXTURE 128x128 regions (uv = pixel, Bedrock box unwrap):
    skin (0,0) | robe black (16,0) | crimson (32,0) | gold (48,0) | hood lining (64,0) | staff wood (80,0)
ASCII only.
"""
import json, os, random
from PIL import Image

MODID, NAME = "tnc", "gothic_priest"
SKIN, ROBE, CRIM, GOLD, LINING, WOOD = (0, 0), (16, 0), (32, 0), (48, 0), (64, 0), (80, 0)

def cube(f, t, uv):
    return {"origin": [f[0], f[1], f[2]], "size": [t[0]-f[0], t[1]-f[1], t[2]-f[2]], "uv": [uv[0], uv[1]]}

def box(cx, cy, cz, w, h, d, uv):
    return cube((cx - w // 2, cy, cz - d // 2), (cx - w // 2 + w, cy + h, cz - d // 2 + d), uv)

def build():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "robe", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            box(0, 0, 0, 13, 3, 9, CRIM),          # crimson hem
            box(0, 3, 0, 12, 9, 8, ROBE),          # skirt
            box(0, 12, 0, 9, 3, 6, ROBE),          # waist
            box(0, 14, 0, 10, 2, 7, CRIM),         # belt
        ]},
        {"name": "body", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            box(0, 14, 0, 9, 9, 5, ROBE),          # torso
            box(0, 14, -2, 5, 8, 1, GOLD),         # gold front panel
            box(0, 22, 0, 10, 3, 6, LINING),       # high collar
        ]},
        {"name": "stole", "parent": "body", "pivot": [0, 21, -2], "cubes": [
            box(-2, 12, -3, 2, 9, 1, CRIM),        # left strip
            box(2, 12, -3, 2, 9, 1, CRIM),         # right strip
        ]},
        {"name": "cross", "parent": "body", "pivot": [0, 21, -3], "cubes": [
            box(0, 13, -3, 1, 5, 1, GOLD),         # cross vertical
            box(0, 15, -3, 4, 1, 1, GOLD),         # cross horizontal
        ]},
        {"name": "cape", "parent": "body", "pivot": [0, 23, 2], "cubes": [
            box(0, 3, 3, 15, 20, 1, ROBE),         # cape (back)
            box(0, 3, 4, 15, 2, 1, CRIM),          # cape hem lining
        ]},
        {"name": "armRight", "parent": "body", "pivot": [-5, 22, 0], "cubes": [
            box(-7, 14, 0, 4, 8, 4, ROBE),         # sleeve
            box(-7, 9, 0, 5, 5, 5, CRIM),          # wide gothic cuff
            box(-7, 7, 0, 3, 3, 3, SKIN),          # hand
        ]},
        {"name": "staff", "parent": "armRight", "pivot": [-7, 8, 0], "cubes": [
            cube((-8, -10, -1), (-7, 24, 0), WOOD),      # tall shaft
            box(-7, 24, 0, 3, 2, 1, GOLD),               # cross head (horizontal)
            box(-7, 22, 0, 1, 4, 1, GOLD),               # cross head (vertical)
        ]},
        {"name": "armLeft", "parent": "body", "pivot": [5, 22, 0], "cubes": [
            box(7, 14, 0, 4, 8, 4, ROBE),
            box(7, 9, 0, 5, 5, 5, CRIM),
            box(7, 7, 0, 3, 3, 3, SKIN),
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 23, 0], "cubes": [
            box(0, 23, 0, 8, 8, 8, SKIN),          # pale face
            box(0, 26, 0, 10, 8, 10, ROBE),        # hood over the head
            box(0, 33, 0, 6, 3, 6, ROBE),          # hood peak
            box(0, 30, -4, 9, 3, 1, LINING),       # hood brim (front)
        ]},
        {"name": "hoodTail", "parent": "head", "pivot": [0, 30, 4], "cubes": [
            box(0, 24, 5, 6, 7, 1, ROBE),
        ]},
    ]
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

    fill(0, 0, 16, 16, lambda x, y, w, h: (232, 214, 204, 255) if not (x in (0, w-1) or y in (0, h-1)) else (198, 178, 170, 255))
    def cloth(base, seam):
        def f(x, y, w, h):
            if y % 5 == 0:
                return seam
            j = rng.randint(-5, 5)
            return (max(0, base[0]+j), max(0, base[1]+j), max(0, base[2]+j), 255)
        return f
    fill(16, 0, 16, 16, cloth((38, 34, 44), (22, 20, 28)))       # robe black
    fill(32, 0, 16, 16, cloth((108, 28, 40), (76, 18, 28)))      # crimson
    def gold(x, y, w, h):
        if (x + y) % 4 == 0:
            return (120, 96, 44, 255)
        return (196, 162, 82, 255)
    fill(48, 0, 16, 16, gold)                                     # gold
    fill(64, 0, 16, 16, cloth((72, 52, 62), (48, 34, 42)))        # hood lining
    def wood(x, y, w, h):
        if x % 3 == 0:
            return (52, 36, 26, 255)
        j = rng.randint(-6, 6)
        return (78 + j, 54 + j, 36 + j, 255)
    fill(80, 0, 16, 16, wood)                                     # staff wood
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

if __name__ == "__main__":
    main()