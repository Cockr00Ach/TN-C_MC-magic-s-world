# -*- coding: utf-8 -*-
"""
gen_dark_summons.py -- the five DIFFERENT dark summons (t1..t5), Bedrock geo + 128x128 textures.

1 unit = 1/16 block, +Y up, feet at y=0.  One model per tier, each a different creature:
    t1 dark_imp      small legless shadow blob + two eyes + a wisp tail   (~0.9 block)
    t2 dark_guard    hooded humanoid with one dark blade                  (~1.6)
    t3 dark_lord     bulkier, twin blades, cape                           (~1.8)
    t4 dark_king     crowned, long cape, one great blade                  (~2.1)
    t5 evil_god      huge, three eyes, six arms, floating ring            (~2.6)

TEXTURE regions (uv = pixel, box unwrap): cloth (0,0) tattered (16,0) eye (32,0) wisp (48,0)
                                          blade (64,0) crown (80,0)
ASCII only.
"""
import json, os, random
from PIL import Image

MODID = "tnc"
CLOTH, TATT, EYE, WISP, BLADE, CROWN = (0, 0), (16, 0), (32, 0), (48, 0), (64, 0), (80, 0)

def cube(f, t, uv):
    return {"origin": [f[0], f[1], f[2]], "size": [t[0]-f[0], t[1]-f[1], t[2]-f[2]], "uv": [uv[0], uv[1]]}

def box(cx, cy, cz, w, h, d, uv):
    return cube((cx - w // 2, cy, cz - d // 2), (cx - w // 2 + w, cy + h, cz - d // 2 + d), uv)

def blades(bones, arms, name):
    pass

def spec(tier):
    """returns bones for the given tier"""
    if tier == 1:
        return [
            {"name": "root", "pivot": [0, 0, 0]},
            {"name": "blob", "parent": "root", "pivot": [0, 6, 0], "cubes": [
                box(0, 2, 0, 9, 6, 8, CLOTH), box(0, 7, 0, 7, 4, 6, CLOTH), box(0, 0, 0, 6, 3, 5, TATT)]},
            {"name": "eyeL", "parent": "blob", "pivot": [-2, 8, -4], "cubes": [box(-2, 8, -4, 2, 1, 1, EYE)]},
            {"name": "eyeR", "parent": "blob", "pivot": [2, 8, -4], "cubes": [box(2, 8, -4, 2, 1, 1, EYE)]},
            {"name": "wisp", "parent": "blob", "pivot": [0, 4, 4], "cubes": [box(0, 0, 5, 3, 5, 2, WISP)]},
        ]
    if tier == 2:
        return [
            {"name": "root", "pivot": [0, 0, 0]},
            {"name": "cloak", "parent": "root", "pivot": [0, 12, 0], "cubes": [
                box(0, 0, 0, 11, 3, 7, TATT), box(0, 3, 0, 10, 9, 6, CLOTH)]},
            {"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": [box(0, 12, 0, 8, 7, 5, CLOTH)]},
            {"name": "armRight", "parent": "body", "pivot": [-4, 18, 0], "cubes": [box(-6, 11, 0, 3, 7, 3, CLOTH)]},
            {"name": "blade", "parent": "armRight", "pivot": [-6, 11, 0], "cubes": [
                cube((-7, 2, -1), (-6, 12, 0), BLADE)]},
            {"name": "armLeft", "parent": "body", "pivot": [4, 18, 0], "cubes": [box(6, 11, 0, 3, 7, 3, CLOTH)]},
            {"name": "head", "parent": "body", "pivot": [0, 19, 0], "cubes": [
                box(0, 22, 0, 9, 7, 9, CLOTH), box(0, 28, 0, 5, 2, 5, TATT),
                box(-2, 22, -5, 2, 1, 1, EYE), box(2, 22, -5, 2, 1, 1, EYE)]},
            {"name": "wisp", "parent": "body", "pivot": [0, 12, 3], "cubes": [box(0, 6, 4, 3, 7, 2, WISP)]},
        ]
    if tier == 3:
        b = spec(2)
        for bone in b:
            if bone["name"] in ("cloak", "body", "armRight", "armLeft", "head"):
                bone["cubes"] = [dict(c, size=[c["size"][0] + (2 if c["size"][0] > 2 else 0),
                                               c["size"][1], c["size"][2]]) for c in bone["cubes"]]
        b.append({"name": "cape", "parent": "body", "pivot": [0, 19, 3], "cubes": [box(0, 4, 4, 13, 15, 1, CLOTH)]})
        b.append({"name": "blade2", "parent": "armLeft", "pivot": [6, 11, 0], "cubes": [cube((6, 2, -1), (7, 12, 0), BLADE)]})
        return b
    if tier == 4:
        b = spec(3)
        b.append({"name": "crown", "parent": "head", "pivot": [0, 29, 0], "cubes": [
            box(0, 29, 0, 10, 1, 10, CROWN),
            box(0, 30, -4, 2, 2, 2, CROWN), box(0, 30, 4, 2, 2, 2, CROWN),
            box(-4, 30, 0, 2, 2, 2, CROWN), box(4, 30, 0, 2, 2, 2, CROWN)]})
        return b
    # t5: huge, three eyes, six arms, a ring
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "robe", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            box(0, 0, 0, 18, 5, 12, TATT), box(0, 5, 0, 16, 9, 10, CLOTH)]},
        {"name": "body", "parent": "root", "pivot": [0, 14, 0], "cubes": [box(0, 14, 0, 14, 12, 8, CLOTH)]},
        {"name": "head", "parent": "body", "pivot": [0, 26, 0], "cubes": [
            box(0, 26, 0, 12, 10, 12, CLOTH), box(0, 35, 0, 8, 3, 8, TATT),
            box(-3, 30, -7, 3, 2, 1, EYE), box(3, 30, -7, 3, 2, 1, EYE), box(0, 33, -7, 3, 2, 1, EYE)]},
        {"name": "ring", "parent": "head", "pivot": [0, 37, 0], "cubes": [
            box(0, 37, -6, 14, 1, 1, CROWN), box(0, 37, 6, 14, 1, 1, CROWN),
            box(-7, 37, 0, 1, 1, 12, CROWN), box(7, 37, 0, 1, 1, 12, CROWN)]},
        {"name": "wisp", "parent": "body", "pivot": [0, 14, 5], "cubes": [
            box(0, 2, 6, 6, 12, 3, WISP), box(0, -3, 7, 4, 6, 2, WISP)]},
    ]
    for i, (sx, yy) in enumerate([(-9, 22), (-9, 18), (-9, 14)]):
        bones.append({"name": "armR%d" % (i + 1), "parent": "body", "pivot": [sx, yy, 0],
                      "cubes": [box(sx, yy - 7, 0, 4, 8, 4, CLOTH)]})
        bones.append({"name": "bladeR%d" % (i + 1), "parent": "armR%d" % (i + 1), "pivot": [sx - 1, yy - 7, 0],
                      "cubes": [cube((sx - 3, yy - 16, -1), (sx - 2, yy - 6, 0), BLADE)]})
    for i, (sx, yy) in enumerate([(9, 22), (9, 18), (9, 14)]):
        bones.append({"name": "armL%d" % (i + 1), "parent": "body", "pivot": [sx, yy, 0],
                      "cubes": [box(sx, yy - 7, 0, 4, 8, 4, CLOTH)]})
        bones.append({"name": "bladeL%d" % (i + 1), "parent": "armL%d" % (i + 1), "pivot": [sx + 1, yy - 7, 0],
                      "cubes": [cube((sx + 2, yy - 16, -1), (sx + 3, yy - 6, 0), BLADE)]})
    return bones

NAMES = {1: "dark_imp", 2: "dark_guard", 3: "dark_lord", 4: "dark_king", 5: "evil_god"}

def geo(name, bones):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.%s.%s" % (MODID, name),
                        "texture_width": 128, "texture_height": 128,
                        "visible_bounds_width": 4, "visible_bounds_height": 4,
                        "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}

def texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    rng = random.Random(20261002)
    def fill(x0, w, h, fn):
        for y in range(h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), fn(x - x0, y, w, h))
    def cloth(x, y, w, h):
        base = (34, 24, 46) if y % 4 else (24, 16, 34)
        if x % 7 == 0:
            base = (20, 14, 30)
        j = rng.randint(-4, 4)
        return (max(0, base[0]+j), max(0, base[1]+j), max(0, base[2]+j), 255)
    fill(0, 16, 16, cloth)
    def tatt(x, y, w, h):
        if y > h - 4 and (x + y) % 3 == 0:
            return (0, 0, 0, 0)
        return cloth(x, y, w, h)
    fill(16, 16, 16, tatt)
    fill(32, 16, 16, lambda x, y, w, h: (90, 30, 140, 255) if x in (0, w-1) or y in (0, h-1) else (214, 150, 255, 255))
    fill(48, 16, 16, lambda x, y, w, h: (150, 90, 220, max(0, int(200 * (1.0 - y / float(h))))))
    fill(64, 16, 16, lambda x, y, w, h: (206, 210, 226, 255) if x % 3 else (120, 124, 140, 255))
    fill(80, 16, 16, lambda x, y, w, h: (208, 172, 92, 255) if (x + y) % 3 else (150, 120, 60, 255))
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tex = texture()
    for tier, name in NAMES.items():
        gp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "geo", "entity", name + ".geo.json")
        tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "entity", name + "_bedrock.png")
        os.makedirs(os.path.dirname(gp), exist_ok=True)
        with open(gp, "w", encoding="utf-8", newline="\n") as f:
            json.dump(geo(name, spec(tier)), f, indent=2, ensure_ascii=False); f.write("\n")
        tex.save(tp)
        print("t%d %-12s -> %s (%d bones)" % (tier, name, os.path.relpath(gp, repo), len(spec(tier))))
    pp = os.path.join(repo, "docs", "previews", "dark_summons_texture.png")
    tex.resize((256, 256), Image.NEAREST).save(pp)
    print("preview -> %s" % os.path.relpath(pp, repo))

if __name__ == "__main__":
    main()