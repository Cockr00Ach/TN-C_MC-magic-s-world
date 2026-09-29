# -*- coding: utf-8 -*-
"""
gen_tavern_set.py -- tavern interior props (Java block models + 64x64 textures each).

SIZE CONTRACT (all props share the same rules, do not guess):
    model space : 1 unit = 1/16 block, +Y up, origin at the block corner
    texture     : 64x64 px;  UV space is 0..16  =>  uv = pixel / 4
    *** every table/counter TOP SURFACE is at y = 16 units (= exactly 1 block) ***
        so a bottle / lamp dropped into the cell above lands FLUSH on it

    1) tavern_table_2x1   32 x 16 units  = 2.0 x 1.0 blocks, top y 13..16
    2) tavern_table_round 16 x 16 units  = 1.0 x 1.0 blocks (octagonal top), top y 13..16
    3) tavern_counter     48 x 16 units  = 3.0 x 1.0 blocks, bar top y 14..16
    4) tavern_bottle       4 x 12 x 4 units (body 4x6x4 + neck + cork) -> sits on y=0
    5) tavern_lamp         8 x 10 x 8 units (base + stem + shade)      -> sits on y=0

ASCII only.
"""
import json, os, random
from PIL import Image

UV = 16.0
WOOD = [0, 0, 4, 4]          # planks
WOOD_D = [4, 0, 8, 4]        # dark planks
METAL = [8, 0, 10, 2]        # brass
SHADE = [10, 0, 14, 4]       # lamp shade (warm)
GLASS = [0, 4, 2, 8]         # bottle glass
LABEL = [2, 4, 4, 6]         # bottle label
CORK = [4, 4, 6, 5]          # cork

def cube(f, t, top=None, side=None, side2=None):
    """one model element; top/side are UV rects in 0..16 space"""
    sides = side or WOOD
    faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        uv = top if face == "up" else (side2 if (face in ("east", "west") and side2) else sides)
        faces[face] = {"uv": uv, "texture": "#0"}
    return {"from": list(f), "to": list(t), "faces": faces}

PROPS = {
  "tavern_table_2x1": [
    cube((0,13,0),(32,16,16), top=WOOD,   side=WOOD),
    cube((2,11,2),(30,13,14), top=WOOD_D, side=WOOD_D),
    cube((2,0,2),(5,11,5),    side=WOOD_D),
    cube((27,0,2),(30,11,5),  side=WOOD_D),
    cube((2,0,11),(5,11,14),  side=WOOD_D),
    cube((27,0,11),(30,11,14),side=WOOD_D),
  ],
  "tavern_table_round": [
    cube((2,13,2),(14,16,14), top=WOOD, side=WOOD),      # centre of the octagon
    cube((1,13,3),(15,16,13), top=WOOD, side=WOOD),      # N-S strips
    cube((3,13,1),(13,16,15), top=WOOD, side=WOOD),      # E-W strips
    cube((6,4,6),(10,13,10),  side=WOOD_D),              # pedestal
    cube((4,0,4),(12,4,12),   top=WOOD_D, side=WOOD_D),  # foot
  ],
  "tavern_counter": [
    cube((0,0,3),(48,14,16),  side=WOOD_D),              # body (dark)
    cube((0,14,2),(48,16,16), top=WOOD,  side=WOOD),     # bar top (overhang)
    cube((0,0,0),(48,3,3),    side=WOOD_D),              # foot rail
    cube((0,14,2),(48,15,4),  top=WOOD,  side=WOOD),     # front lip
  ],
  "tavern_bottle": [
    cube((6,0,6),(10,6,10),   top=GLASS, side=GLASS),    # body
    cube((7,6,7),(9,8,9),     top=GLASS, side=GLASS),    # shoulder
    cube((7,8,7),(9,11,9),    top=GLASS, side=GLASS),    # neck
    cube((7,11,7),(9,12,9),   top=CORK,  side=CORK),     # cork
  ],
  "tavern_lamp": [
    cube((5,0,5),(11,1,11),   top=METAL, side=METAL),    # base
    cube((7,1,7),(9,5,9),     top=METAL, side=METAL),    # stem
    cube((4,5,4),(12,10,12),  top=SHADE, side=SHADE),    # shade
    cube((6,10,6),(10,11,10), top=METAL, side=METAL),    # cap
  ],
}

def paint_wood(img, rng, x0=0):
    for y in range(16):
        for x in range(x0, x0+16):
            c = (118, 84, 50, 255) if y % 4 == 0 else (146, 106, 64, 255)
            if (x-x0) % 8 == 0 and (y//4) % 2 == 1:
                c = (118, 84, 50, 255)
            j = rng.randint(-8, 8)
            img.putpixel((x, y), (max(0,c[0]+j), max(0,c[1]+j), max(0,c[2]+j), 255))

def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(20260930)
    paint_wood(img, rng, 0)                       # WOOD   px 0..16
    for y in range(16):                           # WOOD_D px 16..32 (darker)
        for x in range(16, 32):
            c = (74, 52, 32, 255) if y % 4 == 0 else (96, 68, 42, 255)
            j = rng.randint(-6, 6)
            img.putpixel((x, y), (max(0,c[0]+j), max(0,c[1]+j), max(0,c[2]+j), 255))
    for y in range(0, 8):                         # METAL  px 32..40 (brass)
        for x in range(32, 40):
            c = (168, 132, 62, 255) if (x+y) % 3 else (198, 164, 88, 255)
            img.putpixel((x, y), c)
    for y in range(0, 16):                        # SHADE  px 40..56 (warm glow)
        for x in range(40, 56):
            band = abs(y - 8)
            c = (252, 226, 158, 255) if band < 5 else (232, 196, 120, 255)
            if y in (0, 15):
                c = (176, 140, 74, 255)
            img.putpixel((x, y), c)
    for y in range(16, 32):                       # GLASS px 0..8 (bottle green)
        for x in range(0, 8):
            c = (46, 92, 60, 255) if x < 2 else (64, 118, 78, 255)
            img.putpixel((x, y), c)
    for y in range(16, 24):                       # LABEL px 8..16 (cream)
        for x in range(8, 16):
            c = (226, 214, 186, 255)
            if y in (16, 23) or x in (8, 15):
                c = (188, 168, 132, 255)
            img.putpixel((x, y), c)
    for y in range(16, 20):                       # CORK px 16..24
        for x in range(16, 24):
            c = (176, 138, 92, 255)
            j = rng.randint(-10, 10)
            img.putpixel((x, y), (max(0,c[0]+j), max(0,c[1]+j), max(0,c[2]+j), 255))
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tex = texture()
    for name, elements in PROPS.items():
        mp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "models", "block", name + ".json")
        tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "block", name + ".png")
        os.makedirs(os.path.dirname(mp), exist_ok=True); os.makedirs(os.path.dirname(tp), exist_ok=True)
        model = {"credit": "TN-C tavern prop",
                 "textures": {"0": "tnc:block/%s" % name, "particle": "tnc:block/%s" % name},
                 "elements": elements}
        with open(mp, "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f, indent=2, ensure_ascii=False); f.write("\n")
        tex.save(tp)
        print("%-20s %d elements -> %s" % (name, len(elements), os.path.relpath(mp, repo)))
    pp = os.path.join(repo, "docs", "previews", "tavern_set_texture.png")
    tex.resize((256, 256), Image.NEAREST).save(pp)
    print("preview -> %s" % os.path.relpath(pp, repo))

if __name__ == "__main__":
    main()