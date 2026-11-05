# -*- coding: utf-8 -*-
"""Rebuild the two angel models: proper tapered robe, layered feather wings, ring halo,
hair/face, gold armour, six arms on the statue, and a white-gold spear.

Bone names, parents and the bones used by `fightingangel.animation.json` are kept.
Pivots are moved to the anatomical joints (the old fighting-angel arm pivots sat 20
units away from the arm, which is why the attack swing looked wrong); rotations are
kept modest so the animation values still read as arm swings.

The atlas is 256x256, every cube gets its own box-UV rect, and each rect is painted
per material (skin / hair / robe / gold / feather / steel / gem) with eyes on the
head's +Z face. The renderer still turns the model 180 degrees, so +Z is the face.

Usage: python tools/gen_angel_models.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "tnc")
GEO_DIR = os.path.join(ASSETS, "geo", "entity")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")

PALETTE = {
    "skin":    ((242, 201, 166), (208, 154, 120), (255, 228, 204)),
    "hair":    ((243, 226, 174), (188, 160, 96), (255, 250, 220)),
    "robe":    ((240, 234, 219), (190, 180, 158), (255, 253, 244)),
    "gold":    ((218, 168, 62), (132, 88, 18), (255, 234, 152)),
    "feather": ((249, 246, 236), (192, 184, 164), (255, 255, 252)),
    "steel":   ((222, 233, 244), (146, 166, 188), (255, 255, 255)),
    "gem":     ((108, 226, 255), (30, 112, 164), (232, 255, 255)),
    "eye":     ((70, 205, 245), (18, 66, 104), (238, 255, 255)),
    "wood":    ((146, 104, 60), (92, 62, 34), (192, 148, 96)),
}

SEGMENTS = 16


def pack(cubes, size=256, pad=1):
    x = y = row = 0
    for c in cubes:
        w, h, d = [int(math.ceil(v)) for v in c["size"]]
        rw, rh = 2 * (w + d), h + d
        if x + rw + pad > size:
            x = 0
            y += row + pad
            row = 0
        if y + rh + pad > size:
            raise RuntimeError("atlas overflow at %s" % c)
        c["uv"] = [x, y]
        x += rw + pad
        row = max(row, rh)


def rects_for(c):
    u, v = c["uv"]
    w, h, d = [int(math.ceil(x)) for x in c["size"]]
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def paint_rect(dr, r, col):
    x, y, w, h = r
    if w > 0 and h > 0:
        dr.rectangle([x, y, x + w - 1, y + h - 1], fill=col)


def paint_cube(img, c, mat, rng):
    dr = ImageDraw.Draw(img)
    u, v = c["uv"]
    w, h, d = [int(math.ceil(x)) for x in c["size"]]
    f = rects_for(c)
    b, sh, li = PALETTE[mat]
    for key, r in f.items():
        x, y, rw, rh = r
        if rw <= 0 or rh <= 0:
            continue
        dr.rectangle([x, y, x + rw - 1, y + rh - 1], fill=b)
        if key == "up":
            dr.rectangle([x, y, x + rw - 1, y + rh - 1], fill=li)
        elif key == "down":
            dr.rectangle([x, y, x + rw - 1, y + rh - 1], fill=sh)
        elif rw > 0 and rh > 0:
            dr.line([x, y + rh - 1, x + rw - 1, y + rh - 1], fill=sh)

    if mat in ("robe", "feather"):
        for key in ("north", "south", "east", "west"):
            x, y, rw, rh = f[key]
            step = 3 if mat == "robe" else 2
            for i in range(step, rw, step):
                dr.line([x + i, y, x + i, y + rh - 1], fill=sh)
        if mat == "feather":
            for key in ("north", "south"):
                x, y, rw, rh = f[key]
                for j in range(1, rh, 2):
                    dr.line([x, y + j, x + rw - 1, y + j], fill=li)
    elif mat == "gold":
        for key in ("north", "south", "east", "west"):
            x, y, rw, rh = f[key]
            if rh >= 3:
                dr.line([x, y + 1, x + rw - 1, y + 1], fill=li)
                dr.line([x, y + rh - 2, x + rw - 1, y + rh - 2], fill=sh)
    elif mat == "hair":
        for key in ("north", "south", "east", "west"):
            x, y, rw, rh = f[key]
            for i in range(0, rw, 2):
                dr.line([x + i, y, x + i, y + rh - 1], fill=sh if i % 4 == 0 else li)
    elif mat == "skin":
        x, y, rw, rh = f["south"]
        if rw >= 6 and rh >= 6:
            dr.rectangle([x + 1, y + rh - 3, x + rw - 2, y + rh - 2], fill=(236, 176, 156))
    elif mat == "steel":
        x, y, rw, rh = f["north"]
        dr.line([x, y, x + rw - 1, y + rh - 1], fill=li)
    elif mat == "gem":
        x, y, rw, rh = f["south"]
        if rw >= 4 and rh >= 4:
            dr.rectangle([x + 1, y + 1, x + rw - 2, y + rh - 2], fill=li)

    # face: eyes + mouth on the head cube's +Z face
    if mat == "skin" and c["size"] == [8, 8, 8]:
        x, y, rw, rh = f["south"]
        mx = x + int(rw * 0.42)
        dr.line([mx, y + int(rh * 0.62), mx + 2, y + int(rh * 0.62)], fill=sh)

    for _ in range(max(2, rw * rh // 24)):
        px = rng.randrange(u, u + max(1, 2 * (w + d)))
        py = rng.randrange(v, v + max(1, h + d))
        if img.getpixel((px, py))[3]:
            r, g, bl, a = img.getpixel((px, py))
            k = rng.choice((-1, 1)) * rng.randrange(2, 6)
            img.putpixel((px, py), (max(0, min(255, r + k)),
                                    max(0, min(255, g + k)),
                                    max(0, min(255, bl + k)), a))


# ------------------------------------------------------------------ model


class Angel:
    def __init__(self, data, statue):
        self.data = data
        self.statue = statue
        self.desc = data["minecraft:geometry"][0]["description"]
        self.bones = {b["name"]: b for b in data["minecraft:geometry"][0]["bones"]}
        self.mats = {}
        for b in self.bones.values():
            b["cubes"] = []

    def set_pivot(self, name, p, rot=None):
        b = self.bones[name]
        b["pivot"] = list(p)
        if rot is not None:
            b["rotation"] = list(rot)
        elif "rotation" in b:
            del b["rotation"]

    def add(self, bone, origin, size, mat, rot=None, piv=None):
        c = {"origin": [round(v, 2) for v in origin],
             "size": [round(v, 2) for v in size]}
        if rot:
            c["rotation"] = [round(v, 2) for v in rot]
        if piv:
            c["pivot"] = [round(v, 2) for v in piv]
        self.bones[bone].setdefault("cubes", []).append(c)
        self.mats[id(c)] = mat
        return c

    def box(self, bone, cx, cy, cz, sx, sy, sz, mat, rot=None, piv=None):
        return self.add(bone, [cx - sx / 2.0, cy - sy / 2.0, cz - sz / 2.0],
                        [sx, sy, sz], mat, rot, piv)

    # -- body ---------------------------------------------------------
    def body(self):
        self.set_pivot("body", (0, 14, 0))
        self.box("body", 0, 15.5, 0, 8, 4, 5, "robe")          # waist
        self.box("body", 0, 19.5, 0, 9, 5, 5.5, "robe")        # chest
        self.box("body", 0, 22.0, 0, 10.5, 1.6, 5.5, "gold")   # collar
        self.box("body", 0, 18.6, 2.9, 7.5, 4.6, 0.8, "gold")  # chest plate
        self.box("body", 0, 19.2, 3.35, 1.8, 1.8, 0.4, "gem")  # heart gem
        self.box("body", -5.4, 21.4, 0, 2.6, 1.4, 4.4, "gold") # pauldron R
        self.box("body", 5.4, 21.4, 0, 2.6, 1.4, 4.4, "gold")  # pauldron L
        self.box("body", 0, 14.2, 0, 8.6, 1.4, 5.4, "gold")    # belt

    def robe(self):
        self.set_pivot("robe", (0, 14, 0))
        self.box("robe", 0, 11.0, 0, 9.0, 4, 5.5, "robe")
        self.box("robe", 0, 7.0, 0, 11.0, 5, 7.0, "robe")
        self.box("robe", 0, 3.0, 0, 13.0, 4.5, 8.0, "robe")
        self.box("robe", 0, 1.0, 0, 14.0, 2.2, 9.0, "robe")
        self.box("robe", 0, 0.6, 0, 14.4, 1.2, 9.4, "gold")    # hem
        self.box("robe", 0, 6.0, 4.1, 4.2, 10, 0.8, "robe")    # front apron
        self.box("robe", -2.1, 6.0, 4.5, 0.6, 10, 0.3, "gold")
        self.box("robe", 2.1, 6.0, 4.5, 0.6, 10, 0.3, "gold")
        self.box("robe", 0, 7.0, -4.1, 9, 12, 0.8, "robe")     # back cape
        self.box("robe", -6.8, 5.0, 0, 0.8, 8, 6.4, "robe")    # side drape R
        self.box("robe", 6.8, 5.0, 0, 0.8, 8, 6.4, "robe")     # side drape L
        # feet peeking out
        self.box("robe", -3.2, 1.0, 3.6, 3.0, 2.0, 3.4, "skin")
        self.box("robe", 3.2, 1.0, 3.6, 3.0, 2.0, 3.4, "skin")
        self.box("robe", -3.2, 0.4, 3.8, 3.4, 1.0, 3.8, "gold")
        self.box("robe", 3.2, 0.4, 3.8, 3.4, 1.0, 3.8, "gold")

    def head(self):
        self.set_pivot("head", (0, 24, 0))
        self.box("head", 0, 23.0, 0, 3.0, 2.0, 3.0, "skin")     # neck
        self.box("head", 0, 27.8, 0, 8, 8, 8, "skin")           # head 24..32
        self.box("head", 0, 27.6, -4.5, 8.6, 8.4, 1.0, "hair")  # back hair
        self.box("head", 0, 32.0, 0, 8.8, 1.4, 8.8, "hair")     # hair cap
        self.box("head", 0, 30.9, 4.2, 8, 2.0, 0.8, "hair")     # bangs
        self.box("head", -4.5, 26.8, 3.6, 1.0, 6.0, 1.0, "hair")
        self.box("head", 4.5, 26.8, 3.6, 1.0, 6.0, 1.0, "hair")
        self.box("head", 0, 33.0, -1.0, 4.0, 2.0, 4.0, "hair")  # bun
        self.box("head", 0, 30.3, 4.35, 9.0, 0.9, 0.4, "gold")  # circlet
        self.box("head", 0, 30.3, 4.4, 1.2, 1.6, 0.3, "gem")
        # geometry eyes so the face still reads at 10 blocks tall
        self.box("head", -1.7, 27.6, 4.05, 1.5, 1.0, 0.2, "eye")
        self.box("head", 1.7, 27.6, 4.05, 1.5, 1.0, 0.2, "eye")
        self.box("head", 0, 28.9, 4.02, 3.0, 0.35, 0.15, "hair")  # brow line

    def halo(self):
        self.set_pivot("halo", (0, 34, 0))
        r, y = 5.0, 37.0
        seg_w, seg_h, seg_d = 1.0, 0.7, 2.2
        for i in range(SEGMENTS):
            a = 2.0 * math.pi * i / SEGMENTS
            cx, cz = r * math.cos(a), r * math.sin(a)
            self.add("halo", [cx - seg_w / 2, y, cz - seg_d / 2],
                     [seg_w, seg_h, seg_d], "gold",
                     rot=[0, -math.degrees(a), 0], piv=[cx, y + seg_h / 2, cz])
        for i in range(4):
            a = math.pi / 2 * i + math.pi / 4
            self.box("halo", r * math.cos(a), 36.8, r * math.sin(a),
                     1.0, 1.4, 1.0, "gem")

    def arms(self):
        for side, sx in ((-1, "armRight"), (1, "armLeft")):
            self.set_pivot(sx, (side * 5, 22, 0), (0, 0, side * 4))
            self.box(sx, side * 5.4, 20.2, 0, 3.2, 3.2, 3.4, "gold")   # shoulder
            self.box(sx, side * 5.6, 16.2, 0, 3.0, 7.0, 3.0, "robe")   # sleeve
            self.box(sx, side * 5.8, 12.0, 0, 2.6, 5.2, 2.6, "skin")   # forearm
            self.box(sx, side * 5.8, 13.0, 0, 3.2, 2.6, 3.2, "gold")   # bracer
            self.box(sx, side * 5.9, 9.2, 0.4, 2.4, 2.2, 3.0, "skin")  # hand
        # The old file hung six/eight arms off the torso; they read as stubs in game and
        # the animation channels that drive them (armLeft2/3/4, armRight4) still evaluate
        # harmlessly against the empty bones. Keep the bones, drop the clutter.
        for name in ("armRight2", "armRight3", "armRight4",
                     "armLeft2", "armLeft3", "armLeft4"):
            if name in self.bones:
                self.set_pivot(name, self.bones[name]["pivot"])
                self.bones[name]["cubes"] = []

    # -- wings --------------------------------------------------------
    def feather(self, bone, side, ax, ay, az, length, width, yaw, droop, mat="feather"):
        if side > 0:
            origin = [ax, ay - width / 2, az]
            rot = [0, yaw, -droop]
        else:
            origin = [ax - length, ay - width / 2, az]
            rot = [0, -yaw, droop]
        self.add(bone, origin, [length, width, 1.0], mat, rot=rot, piv=[ax, ay, az])

    def wing(self, side, root, mid, tip):
        rn = "wingRight" if side < 0 else "wingLeft"
        mn = "wingRightMid" if side < 0 else "wingLeftMid"
        tn = "wingRightTip" if side < 0 else "wingLeftTip"
        self.set_pivot(rn, root)
        self.set_pivot(mn, mid)
        self.set_pivot(tn, tip)
        s = -1 if side < 0 else 1

        # shoulder feathers
        for i in range(4):
            self.feather(rn, s, s * (1.2 + i * 0.9), 24.6 - i * 0.5, -1.4 - i * 0.4,
                         4.8 + i * 0.6, 2.6 - i * 0.1, 12 + i * 4, 18 + i * 4)
        # mid feathers
        for i in range(5):
            self.feather(mn, s, s * (3.4 + i * 1.1), 26.6 - i * 0.7, -2.4 - i * 0.5,
                         5.8 + i * 0.7, 2.8 - i * 0.1, 14 + i * 5, 26 + i * 5)
        # primaries
        for i in range(5):
            self.feather(tn, s, s * (6.4 + i * 1.3), 29.0 - i * 0.9, -3.6 - i * 0.7,
                         6.6 + i * 0.6, 2.6 - i * 0.1, 18 + i * 6, 34 + i * 5)
        # covert row (shorter, in front)
        for i in range(3):
            self.feather(mn, s, s * (3.2 + i * 1.5), 28.0 - i * 1.0, -1.6 - i * 0.3,
                         4.2 + i * 0.5, 2.2, 6 + i * 3, 8 + i * 3)

    def wings(self):
        self.wing(-1, (-1.2, 22, -1.4), (-4.0, 24, -2.2), (-7.0, 27, -3.2))
        self.wing(1, (1.2, 22, -1.4), (4.0, 24, -2.2), (7.0, 27, -3.2))

    # -- spear --------------------------------------------------------
    def weapon(self):
        geo = self.data["minecraft:geometry"][0]
        geo["bones"] = [b for b in geo["bones"] if b["name"] != "weapon"]
        self.bones.pop("weapon", None)
        geo["bones"].append(
            {"name": "weapon", "parent": "armRight", "pivot": [-5.9, 9.5, 0.4]})
        self.bones["weapon"] = self.data["minecraft:geometry"][0]["bones"][-1]
        self.bones["weapon"]["cubes"] = []
        self.box("weapon", -6.4, 17.0, 0.4, 1.2, 34.0, 1.2, "wood")   # shaft 0..34
        self.box("weapon", -6.4, 35.4, 0.4, 2.6, 5.0, 1.6, "steel")   # blade
        self.box("weapon", -6.4, 38.8, 0.4, 1.4, 2.2, 1.0, "gold")    # tip
        self.box("weapon", -6.4, 33.2, 0.4, 3.8, 1.0, 2.2, "gold")    # guard
        self.box("weapon", -6.4, 1.2, 0.4, 2.0, 2.0, 2.0, "gold")     # pommel
        self.box("weapon", -6.4, 30.0, 0.4, 1.8, 1.8, 1.8, "gem")     # focus gem

    def build(self):
        self.body()
        self.robe()
        self.head()
        self.halo()
        self.arms()
        self.wings()
        self.weapon()


def build(name, geo_file, tex_file, statue):
    path = os.path.join(GEO_DIR, geo_file)
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    a = Angel(data, statue)
    a.build()

    cubes = []
    for b in data["minecraft:geometry"][0]["bones"]:
        cubes.extend(b.get("cubes", []))
    pack(cubes)
    a.desc["texture_width"] = 256
    a.desc["texture_height"] = 256

    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(20261105)
    used = set()
    for c in cubes:
        paint_cube(img, c, a.mats[id(c)], rng)
        for x, y, w, h in rects_for(c).values():
            for px in range(x, x + w):
                for py in range(y, y + h):
                    if (px, py) in used:
                        raise RuntimeError("UV overlap at %s" % ((px, py),))
                    used.add((px, py))

    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent="\t")
        f.write("\n")
    img.save(os.path.join(TEX_DIR, tex_file))
    print("%s: %d cubes, %d uv pixels" % (name, len(cubes), len(used)))


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    build("angel", "angel.geo.json", "angel_bedrock.png", True)
    build("fightingangel", "fightingangel.geo.json", "fightingangel_bedrock.png", False)
