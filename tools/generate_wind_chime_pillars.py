"""Build Java 1.20.1 block meshes and the painted pixel atlas for the three aeolian instruments."""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc"
MODEL_DIR = ASSETS / "models/block/wind_chimes"
TEXTURE = "tnc:block/wind_chimes/carving"
TILE = 32
TILES = {"gneiss": (0, 0), "cut": (1, 0), "copper": (2, 0), "gold": (3, 0),
         "flute": (0, 1), "glass": (1, 1), "bell": (2, 1), "iron": (3, 1),
         "harp": (0, 2), "iris": (1, 2), "chorus": (2, 2), "plinth": (3, 2)}
PALETTES = {
    "gneiss": [(43, 58, 66), (55, 71, 77), (67, 83, 87), (84, 98, 96)],
    "cut": [(53, 69, 74), (67, 83, 88), (84, 99, 101), (104, 116, 111)],
    "copper": [(41, 73, 71), (54, 94, 85), (70, 118, 101), (117, 132, 102)],
    "gold": [(91, 72, 50), (129, 99, 64), (164, 128, 77), (201, 166, 108)],
    "flute": [(37, 53, 60), (50, 68, 74), (64, 83, 87), (79, 96, 96)],
    "glass": [(49, 106, 105), (84, 152, 137), (124, 190, 162), (185, 222, 187)],
    "bell": [(85, 65, 47), (132, 96, 58), (181, 139, 85), (223, 188, 119)],
    "iron": [(37, 47, 53), (46, 58, 62), (67, 77, 77), (90, 96, 87)]
}

# Lines of the three inlaid seals, each cut into two opposing faces of the instrument.
SIGILS = {
    "harp": [[(8, 22), (13, 21), (21, 11), (23, 6), (20, 7), (11, 16), (8, 22)],
             [(10, 21), (20, 11)], [(13, 16), (13, 13)], [(16, 14), (20, 14)],
             [(8, 25), (15, 23), (19, 19)]],
    "iris": [[(6, 16), (11, 11), (17, 9), (23, 12), (26, 16), (21, 19),
              (15, 21), (10, 19), (6, 16)], [(16, 12), (19, 15), (16, 19), (13, 16), (16, 12)],
             [(7, 12), (12, 8), (19, 7)], [(12, 24), (18, 23), (24, 20)]],
    "chorus": [[(6, 17), (10, 13), (13, 15), (11, 18), (8, 17)],
               [(12, 12), (16, 7), (20, 12), (16, 16), (12, 12)],
               [(19, 15), (22, 13), (26, 17), (22, 18), (19, 15)],
               [(9, 21), (13, 22), (16, 25), (19, 22), (23, 21)], [(16, 16), (16, 21)]]
}

def paint_atlas():
    atlas = Image.new("RGB", (128, 128))
    for idx, (name, (col, row)) in enumerate(TILES.items()):
        rng = random.Random(3401 + idx)
        palette = PALETTES.get(name, PALETTES["flute"])
        tile = Image.new("RGB", (TILE, TILE), palette[1])
        draw = ImageDraw.Draw(tile)
        metal = name in ("gold", "copper", "bell", "iron", "glass")
        for _ in range(70 if metal else 120):
            x, y = rng.randrange(32), rng.randrange(32)
            swatch = rng.choice([0, 1, 1, 2, 2, 2, 3])
            length = rng.randint(1, 4) if metal else rng.randint(2, 7)
            dx, dy = (0, length) if metal else (length, rng.choice([0, 0, -1]))
            draw.line((x, y, x + dx, y + dy), fill=palette[swatch])
        if name in ("gneiss", "cut"):
            for k in [4, 18, 27]:
                draw.line([(0, k), (10, k-2), (15, k-2), (23, k+1), (31, k)], fill=palette[0])
            for x, y in [(2, 4), (3, 5), (17, 17), (22, 24), (24, 23), (28, 12)]:
                draw.line((x, y, x+2, y), fill=(106, 123, 91))
        if name == "flute":
            for x in [4, 11, 20, 27]:
                draw.line((x, 0, x, 31), fill=palette[0])
                draw.line((x+1, 1, x+1, 27), fill=palette[2])
        if name in ("bell", "gold"):
            for x in [3, 9, 14, 25]:
                draw.line((x, 0, x, 31), fill=palette[2])
                draw.line((x+1, 5, x+1, 12), fill=palette[3])
                draw.line((x+1, 19, x+1, 23), fill=palette[3])
            for y in [2, 26]:
                draw.line((0, y, 31, y), fill=palette[0])
                draw.line((0, y+1, 31, y+1), fill=palette[3])
        if name == "copper":
            for x, y in [(6, 9), (15, 5), (18, 22), (27, 15), (3, 25)]:
                draw.line((x, y, x+3, y-1), fill=(138, 130, 91))
                draw.point((x+3, y-1), fill=(169, 148, 103))
        if name in SIGILS or name == "plinth":
            draw.polygon([(2, 0), (29, 0), (30, 2), (30, 27), (26, 31), (5, 31), (1, 27), (1, 2)],
                         outline=(121, 126, 97))
            for x in (4, 26):
                draw.line((x, 2, x, 6), fill=(164, 146, 96))
                draw.line((x, 25, x, 28), fill=(78, 131, 116))
            if name == "plinth":
                for x in (2, 10, 18, 26):
                    draw.line([(x-1, 13), (x+2, 10), (x+5, 13), (x+2, 16), (x-1, 13)], fill=(97, 148, 129))
                draw.line([(0, 21), (10, 21), (14, 17), (19, 17), (24, 21), (31, 21)], fill=(119, 114, 86))
            else:
                for seq in SIGILS[name]:
                    draw.line([(x, y+1) for x,y in seq], fill=(25, 42, 46), width=3)
                    draw.line(seq, fill=(122, 185, 165), width=1)
                    draw.point(seq[0], fill=(209, 214, 162))
                draw.point((16, 3), fill=(202, 173, 104))
                draw.point((16, 28), fill=(202, 173, 104))
        atlas.paste(tile, (col*TILE, row*TILE))
    out = ASSETS / "textures/block/wind_chimes/carving.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(out, optimize=True)

class Mesh:
    def __init__(self):
        self.elements = []

    def box(self, lo, hi, material, *, seals=None, tilt=None, pivot=None):
        dims = [b-a for a,b in zip(lo,hi)]
        faces = {}
        for side, axes in [("up",(0,2)), ("down",(0,2)), ("north",(0,1)), ("south",(0,1)),
                           ("west",(2,1)), ("east",(2,1))]:
            panel = seals if side in ("north", "south") else None
            col, row = TILES[panel or material]
            w,h = (32,32) if panel else tuple(max(2, min(32, round(dims[n]*2))) for n in axes)
            u = col*32 + (32-w)//2
            v = row*32 + (32-h)//2
            faces[side] = {"uv": [u/8, v/8, (u+w)/8, (v+h)/8], "texture": "#skin"}
        el = {"from": list(lo), "to": list(hi), "faces": faces}
        if tilt:
            el["rotation"] = {"origin": list(pivot), "axis": "z", "angle": tilt}
        self.elements.append(el)

    def save(self, name):
        # Adjacent, fully covered end faces are never visible from an opening.
        faces = [("west",0,0), ("east",0,1), ("down",1,0), ("up",1,1), ("north",2,0), ("south",2,1)]
        for e in self.elements:
            if "rotation" in e:
                continue
            for key, axis, upper in faces:
                edge = "to" if upper else "from"
                far = "from" if upper else "to"
                for other in self.elements:
                    if e is other or "rotation" in other or e[edge][axis] != other[far][axis]:
                        continue
                    if all(other["from"][a] <= e["from"][a] and other["to"][a] >= e["to"][a] for a in range(3) if a!=axis):
                        e["faces"].pop(key, None)
                        break
        model = {"parent": "minecraft:block/block", "textures": {"skin": TEXTURE, "particle": "minecraft:block/tuff"},
                 "elements": self.elements}
        write_json(MODEL_DIR / (name+".json"), model)

def pedestal():
    m = Mesh()
    m.box((0,0,0),(16,2,16),"gneiss")
    m.box((1,2,1),(15,4,15),"cut")
    m.box((2,4,2),(14,5,14),"copper")
    m.box((2.5,5,2.5),(13.5,8,13.5),"gneiss",seals="plinth")
    m.box((3.5,8,3.5),(12.5,9,12.5),"gold")
    m.box((4,9,4),(12,16,12),"flute")
    for x in (2,11):
        for z in (2,11):
            m.box((x,5,z),(x+3,7,z+3),"cut")
            m.box((x+.5,7,z+.5),(x+2.5,10,z+2.5),"copper")
    m.save("foot")

def shaft(name):
    m = Mesh()
    m.box((4,0,4),(12,12,12),"flute",seals=name)
    for x in (3,11.5):
        m.box((x,0,4.6),(x+1.5,8,11.4),"gneiss")
        m.box((x,8,5.3),(x+1.5,10,10.7),"copper")
    m.box((3,12,3),(13,13,13),"gold")
    m.box((2,13,3),(14,14,13),"cut")
    m.box((1,14,2),(15,15,14),"gneiss")
    m.box((2,15,3),(14,16,13),"copper")
    m.save("shaft_"+name)

def harp():
    m = Mesh()
    for x in (1,12.5):
        m.box((x,0,5.5),(x+2.5,10,10.5),"copper")
        m.box((x-.0,10,6),(x+2.5,13,10),"gold")
    m.box((1,13,5.5),(15,14.5,10.5),"copper")
    m.box((3,14.5,6),(13,15.5,10),"gold")
    for x, bottom in [(4.25,3.2),(8,1.5),(11.75,5.0)]:
        m.box((x-.32,11,7.68),(x+.32,13,8.32),"iron")
        m.box((x-.9,bottom,7.1),(x+.9,11,8.9),"bell")
        m.box((x-1.1,bottom+.6,6.9),(x+1.1,bottom+1.4,9.1),"copper")
    m.save("crown_harp")

def iris():
    m = Mesh()
    m.box((5,0,5.5),(11,2.5,10.5),"gold")
    # The aperture is an actual open octagon with a laurel-shaped wind vane suspended inside.
    m.box((5,2,6),(11,4,10),"copper")
    m.box((5,14,6),(11,16,10),"copper")
    m.box((.8,6.5,6),(2.8,11.5,10),"copper")
    m.box((13.2,6.5,6),(15.2,11.5,10),"copper")
    for cx,cy,angle in [(3.85,5.1,-45),(12.15,5.1,45),(3.85,12.9,45),(12.15,12.9,-45)]:
        m.box((cx-2.65,cy-1.05,6.05),(cx+2.65,cy+1.05,9.95),"gold",tilt=angle,pivot=(cx,cy,8))
    m.box((7.7,11,7.7),(8.3,14.0,8.3),"iron")
    m.box((6.55,6.35,7.1),(9.45,9.25,8.9),"glass",tilt=45,pivot=(8,7.8,8))
    m.box((7.6,9.1,7.55),(8.4,11.2,8.45),"gold")
    m.save("crown_iris")

def chorus():
    m=Mesh()
    for x in (1.0,13.0):
        m.box((x,0,6),(x+2,9,10),"copper")
        m.box((x-.4,0,5.6),(x+2.4,1.5,10.4),"gold")
    for cx,angle in [(4, -45),(12,45)]:
        m.box((cx-1,8,6.1),(cx+1,15,9.9),"copper",tilt=angle,pivot=(cx,11.5,8))
    m.box((5.75,13.75,6.1),(10.25,15.25,9.9),"gold")
    m.box((7.65,10,7.65),(8.35,14.0,8.35),"iron")
    m.box((6.5,9,6.5),(9.5,10.25,9.5),"gold")
    m.box((5.5,6.5,5.5),(10.5,9,10.5),"bell")
    m.box((5,5.25,5),(11,6.5,11),"copper")
    m.box((4.25,3.75,4.25),(11.75,5.25,11.75),"bell")
    m.box((7.55,2,7.55),(8.45,3.75,8.45),"iron")
    m.box((7.1,1,7.1),(8.9,2.6,8.9),"glass")
    m.save("crown_chorus")

def write_json(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

if __name__ == "__main__":
    paint_atlas()
    pedestal()
    harp()
    iris()
    chorus()
    variants={}
    for voice,name in enumerate(SIGILS):
        shaft(name)
        for section,model in enumerate(("foot","shaft_"+name,"crown_"+name)):
            variants[f"section={section},voice={voice}"]={"model":"tnc:block/wind_chimes/"+model}
    write_json(ASSETS/"blockstates/wind_chime_pillar.json", {"variants":variants})

