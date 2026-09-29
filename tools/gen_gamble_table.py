# -*- coding: utf-8 -*-
"""
gen_gamble_table.py -- 4x2 block GAMBLING table (cards / dice / chips / card shoe).

SIZE CONTRACT (fixed, do not guess):
    model space : 1 unit = 1/16 block, +Y up, origin at the block corner
    footprint   : 4.0 x 2.0 blocks  (x 0..64, z 0..32)
    felt top    : y 12..13   (the playing surface; everything sits ON y=13)
    rails       : y 13..15   (wooden rim, 1 unit above the felt)
    total height: 15/16 block (0.9375)
    texture     : 64x64 px;  UV space is 0..16, so  uv = pixel / 4

TEXTURE REGIONS (pixels -> uv in 0..16 space):
    wood      (0,0)-(16,16)    -> 0,0 .. 4,4
    felt      (16,0)-(48,16)   -> 4,0 .. 12,4      (2:1, matches the 4x2 top)
    card face (0,16)-(8,24)    -> 0,4 .. 2,6
    card back (8,16)-(16,24)   -> 2,4 .. 4,6
    die       (16,16)-(24,24)  -> 4,4 .. 6,6
    chip red  (24,16)-(32,24)  -> 6,4 .. 8,6
    chip blue (32,16)-(40,24)  -> 8,4 .. 10,6
    chip white(40,16)-(48,24)  -> 10,4 .. 12,6

ASCII only (PowerShell/Python both fine with it).
"""
import json, os, random
from PIL import Image

NAME = "gamble_table"
WOOD = [0, 0, 4, 4]
FELT = [4, 0, 12, 4]
CARD_F = [0, 4, 2, 6]
CARD_B = [2, 4, 4, 6]
DIE = [4, 4, 6, 6]
CHIP_R = [6, 4, 8, 6]
CHIP_B = [8, 4, 10, 6]
CHIP_W = [10, 4, 12, 6]

# (name, from, to, top_uv, side_uv)
PARTS = [
    ("felt",      (0, 12, 0),  (64, 13, 32), FELT,   WOOD),
    ("railFront", (0, 13, 0),  (64, 15, 2),  WOOD,   WOOD),
    ("railBack",  (0, 13, 30), (64, 15, 32), WOOD,   WOOD),
    ("railLeft",  (0, 13, 2),  (2, 15, 30),  WOOD,   WOOD),
    ("railRight", (62, 13, 2), (64, 15, 30), WOOD,   WOOD),
    ("apron",     (3, 10, 3),  (61, 12, 29), WOOD,   WOOD),
    ("legA",      (2, 0, 2),   (5, 10, 5),   WOOD,   WOOD),
    ("legB",      (2, 0, 27),  (5, 10, 30),  WOOD,   WOOD),
    ("legC",      (59, 0, 2),  (62, 10, 5),  WOOD,   WOOD),
    ("legD",      (59, 0, 27), (62, 10, 30), WOOD,   WOOD),
    ("cardFace",  (20, 13, 10), (30, 14, 18), CARD_F, CARD_F),
    ("cardBack",  (28, 13, 14), (38, 14, 22), CARD_B, CARD_B),
    ("dieA",      (44, 13, 20), (47, 16, 23), DIE,   DIE),
    ("dieB",      (49, 13, 24), (52, 16, 27), DIE,   DIE),
    ("chipRed",   (34, 13, 6),  (38, 14, 10), CHIP_R, CHIP_R),
    ("chipBlue",  (39, 13, 7),  (43, 14, 11), CHIP_B, CHIP_B),
    ("chipWhite", (44, 13, 6),  (48, 14, 10), CHIP_W, CHIP_W),
    ("shoe",      (54, 13, 6),  (62, 14, 26), CARD_B, CARD_B),
]

def build():
    elements = []
    for (name, f, t, top, side) in PARTS:
        faces = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            uv = top if face == "up" else side
            faces[face] = {"uv": uv, "texture": "#0"}
        elements.append({"from": list(f), "to": list(t), "faces": faces})
    return {
        "credit": "TN-C scene prop (gambling table)",
        "textures": {"0": "tnc:block/%s" % NAME, "particle": "tnc:block/%s" % NAME},
        "elements": elements,
    }

def rect(img, x0, y0, x1, y1, fn):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), fn(x - x0, y - y0, x1 - x0, y1 - y0))

def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(20260929)
    # wood
    def wood(px, py, w, h):
        c = (118, 84, 50, 255) if py % 4 == 0 else (146, 106, 64, 255)
        if px % 8 == 0 and (py // 4) % 2 == 1:
            c = (118, 84, 50, 255)
        j = rng.randint(-8, 8)
        return (max(0, c[0] + j), max(0, c[1] + j), max(0, c[2] + j), 255)
    rect(img, 0, 0, 16, 16, wood)
    # felt: green with a darker border and a faint centre line
    def felt(px, py, w, h):
        c = (32, 118, 58, 255)
        if px < 2 or py < 2 or px >= w - 2 or py >= h - 2:
            c = (22, 88, 44, 255)
        if px == w // 2:
            c = (44, 136, 70, 255)
        j = rng.randint(-6, 6)
        return (max(0, c[0] + j), max(0, c[1] + j), max(0, c[2] + j), 255)
    rect(img, 16, 0, 48, 16, felt)
    # card face: white with a red corner mark
    def cardf(px, py, w, h):
        if px in (0, w - 1) or py in (0, h - 1):
            return (206, 206, 212, 255)
        if (px < 2 and py < 3) or (px >= w - 2 and py >= h - 3):
            return (188, 46, 46, 255)
        return (242, 240, 236, 255)
    rect(img, 0, 16, 8, 24, cardf)
    # card back: red diagonal lattice
    def cardb(px, py, w, h):
        if px in (0, w - 1) or py in (0, h - 1):
            return (232, 228, 224, 255)
        return (150, 34, 46, 255) if (px + py) % 4 in (0, 1) else (196, 60, 70, 255)
    rect(img, 8, 16, 16, 24, cardb)
    # die: white with pips
    def die(px, py, w, h):
        pips = [(1, 1), (5, 5), (5, 1)] if (px + py) % 2 == 0 else [(2, 2), (4, 2), (2, 4), (4, 4)]
        for (a, b) in pips:
            if abs(px - a) < 1 and abs(py - b) < 1:
                return (36, 36, 40, 255)
        return (246, 246, 244, 255)
    rect(img, 16, 16, 24, 24, die)
    # chips: coloured disc with white dashes
    def chip(base):
        def f(px, py, w, h):
            if px in (0, w - 1) or py in (0, h - 1):
                return (232, 232, 228, 255)
            if (px + py) % 4 == 0:
                return (238, 238, 234, 255)
            return base
        return f
    rect(img, 24, 16, 32, 24, chip((176, 40, 44, 255)))
    rect(img, 32, 16, 40, 24, chip((44, 74, 168, 255)))
    rect(img, 40, 16, 48, 24, chip((236, 234, 228, 255)))
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    mp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "models", "block", NAME + ".json")
    tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "block", NAME + ".png")
    pp = os.path.join(repo, "docs", "previews", NAME + "_texture.png")
    for p in (mp, tp, pp):
        os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(mp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(build(), f, indent=2, ensure_ascii=False); f.write("\n")
    tex = texture(); tex.save(tp); tex.resize((256, 256), Image.NEAREST).save(pp)
    print("model   -> %s (%d elements)" % (os.path.relpath(mp, repo), len(PARTS)))
    print("texture -> %s" % os.path.relpath(tp, repo))
    print("preview -> %s" % os.path.relpath(pp, repo))
    print("size    : 4.0 x 0.9375 x 2.0 blocks   felt y=12..13, rails y=13..15, legs y=0..10")

if __name__ == "__main__":
    main()