# -*- coding: utf-8 -*-
"""
gen_dragon_model.py -- a RIGGED **Eastern dragon** (Chinese / dragon-dance style).

★★ 2026-10-02 起：作者自己动手改了模型（41 骨骼 / 100 方块、他自己的 box-UV 布局、
   腿和须暂时是空骨骼 ✓）⇒ **不要再直接跑本脚本** ✗（会覆盖他的改动；脚本里已经加了
   拒绝覆盖的闸门，只有 --force 才会写 ✓）。
   他的模型现在只需要"贴图 + 动画"：
     贴图  -> tools/retexture_dragon.py   （只写 png，绝不碰 geo ✓）
     动画  -> tools/gen_dragon_animation.py（dash 冲刺 / idle ✓）
   本脚本留着是为了：以后要重新生成一版模型时还能用 ✓（改色/改结构都在这里 ✓）。

Author 2026-10-02: "生成一个巨龙的模型，我要骨骼的" -> first pass was a western dragon
("太粗糙了") -> "我想要那种东方的，舞龙的那样长长的" (this file).

DESIGN (what makes it "Eastern"):
  * a LONG serpentine body: **12 body segments + 5 tail segments**, one bone each, so the
    whole thing can undulate like a dragon-dance dragon (head end at -Z, tail at +Z);
  * **no bat wings** (Eastern dragons fly by magic) -> a dorsal fin ridge runs down the spine;
  * deer-like **antlers** (2 bones per side, branching), two long **whiskers** (2 bones each),
    a **mane** fanning back off the head, a nose horn, fangs, a chin beard;
  * four short legs with claws, a **flame-shaped tail fin**.

SIZE: snout to flame tip ~21.6 blocks, body height ~2.2 blocks, standing on y=0.

RIG: root > neck1 > neck2 > head (+ jaw, antlers, whiskers, mane), body1..body12 (chained),
     tail1..tail5 > tailFin, and 4 legs (thigh > shin > paw) hanging off body2 / body9.

TEXTURE 512x512, Bedrock **box UV** (the only convention this project has proven in game):
    every cube owns a packed rectangle of (2d+2w) x (d+h) px, painted with its material.
    uv values in the json are plain texture pixels (same as gothic_priest / yan / ...).

NOTE ON ROTATIONS: cubes may carry a rotation (Blockbench allows it and the author's models
use it). Mirroring across x=0 negates the cube's x, and negates the y/z rotation angles -
NEVER add a `mirror` flag (it flips geometry AND texture, which broke the angel wings).
ASCII only.  Usage:  python tools/gen_dragon_model.py
"""
import json
import math
import os
import random

from PIL import Image

MODID, NAME = "tnc", "dragon"
TEX = 512                       # 12 body + 5 tail segments * (2d+2w)x(d+h) does not fit 256
MARGIN = 2

# ------------------------------------------------------------------ palette
# 想换配色：改这 7 行 ✓（朱红+金是舞龙的常见配色 ✓）
SCALE = (188, 36, 28)           # 朱红鳞甲
SCALE_HI = (255, 214, 120)      # 鳞片金边
SCALE2 = (150, 26, 22)          # 每隔一节换个深浅 => 舞龙那种"一节一节"的感觉 ✓
BELLY = (232, 194, 104)         # 金色腹甲 / 节与节之间的金箍
FIN = (246, 140, 44)            # 背鳍 / 鬃毛 / 尾焰
BONE = (238, 228, 204)          # 角 / 须 / 牙 / 爪
EYE = (255, 202, 64)            # 眼睛金


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255)


def _tone(img, box, rect, k, jitter=0, rng=None):
    x0, y0, w, h = box
    rx, ry, rw, rh = rect
    for y in range(ry, ry + rh):
        for x in range(rx, rx + rw):
            if 0 <= x < w and 0 <= y < h:
                p = img.getpixel((x0 + x, y0 + y))
                j = rng.randint(-jitter, jitter) if (rng and jitter) else 0
                img.putpixel((x0 + x, y0 + y), (clamp(p[0] * k + j), clamp(p[1] * k + j),
                                                clamp(p[2] * k + j), 255))


def paint_scales(img, box, size, base, hi, period=8, seed=1, dorsal=0.72, ventral=1.22):
    """red scales with a gold rim; box-UV aware: back darker, underside lighter"""
    x0, y0, w, h = box
    cw, ch, cd = size
    rng = random.Random(seed)
    for y in range(h):
        row = y // period
        off = (period // 2) if row % 2 else 0
        for x in range(w):
            phase = (x + off) % period
            c = hi if (phase < 2 and y % period < period - 2) else base
            if phase == 0 or y % period == 0:
                c = tuple(int(v * 0.66) for v in base)
            j = rng.randint(-6, 6)
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] + j), clamp(c[1] + j), clamp(c[2] + j), 255))
    _tone(img, box, (cd, 0, cw, cd), dorsal, 6, rng)              # up   -> 背脊压暗
    _tone(img, box, (cd + cw, 0, cw, cd), ventral, 6, rng)        # down -> 腹面提亮


def paint_belly(img, box, size, base, seed=2):
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        band = y % 7
        for x in range(w):
            k = 1.0 if band < 5 else (1.12 if band == 5 else 0.80)
            j = rng.randint(-5, 5)
            img.putpixel((x0 + x, y0 + y), (clamp(base[0] * k + j), clamp(base[1] * k + j),
                                            clamp(base[2] * k + j), 255))


def paint_fin(img, box, size, base, seed=3):
    """dorsal fin / mane / tail flame: vertical flame streaks, brighter towards the edge"""
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        for x in range(w):
            streak = abs(math.sin(x * 0.9)) * 0.45 + abs(math.sin(x * 0.31 + y * 0.13)) * 0.35
            k = 0.62 + 0.70 * streak
            j = rng.randint(-6, 6)
            img.putpixel((x0 + x, y0 + y), (clamp(base[0] * k + j), clamp(base[1] * k + j),
                                            clamp(base[2] * k + j), 255))


def paint_bone(img, box, size, base, seed=4):
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        for x in range(w):
            ring = 0.94 + 0.10 * ((y // 3) % 2)
            j = rng.randint(-7, 7)
            img.putpixel((x0 + x, y0 + y), (clamp(base[0] * ring + j), clamp(base[1] * ring + j),
                                            clamp(base[2] * ring + j), 255))


def paint_claw(img, box, size, base=(58, 46, 34), seed=5):
    x0, y0, w, h = box
    for y in range(h):
        for x in range(w):
            img.putpixel((x0 + x, y0 + y), shade(base, 0.6 + 0.7 * (x / float(max(1, w - 1)))))


def paint_eye(img, box, size, base, seed=6):
    x0, y0, w, h = box
    for y in range(h):
        for x in range(w):
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            if abs(x - cx) < 0.9 and abs(y - cy) < h * 0.40:
                img.putpixel((x0 + x, y0 + y), (26, 12, 6, 255))
            else:
                d = math.hypot((x - cx) / max(1.0, cx), (y - cy) / max(1.0, cy))
                img.putpixel((x0 + x, y0 + y), shade(base, max(0.40, 1.15 - 0.80 * d)))


MATERIALS = {
    "scale": lambda img, box, size: paint_scales(img, box, size, SCALE, SCALE_HI, 8, 21),
    "scale2": lambda img, box, size: paint_scales(img, box, size, SCALE2, SCALE_HI, 8, 22),
    "belly": lambda img, box, size: paint_belly(img, box, size, BELLY),
    "fin": lambda img, box, size: paint_fin(img, box, size, FIN),
    "bone": lambda img, box, size: paint_bone(img, box, size, BONE),
    "claw": lambda img, box, size: paint_claw(img, box, size),
    "eye": lambda img, box, size: paint_eye(img, box, size, EYE),
}


# ------------------------------------------------------------------ model
def cube(cx, cy, cz, w, h, d, mat, rot=None):
    """cube by CENTRE. Odd sizes are fine: the origin below uses true division, so a cube
    with cx=0 and an odd width still stays symmetric around x=0 (Blockbench writes .5 too)."""
    return {"c": (cx, cy, cz), "s": (w, h, d), "mat": mat, "rot": tuple(rot) if rot else None}


def left_bones():
    """left half (+X) + the centre bones; the right half is mirrored afterwards"""
    b = []

    def B(name, parent, pivot, cubes, rot=None):
        b.append({"name": name, "parent": parent, "pivot": list(pivot),
                  "rot": list(rot) if rot else None, "cubes": cubes})

    # ---------------- 脖子 + 头（东方龙的头才是重点 ✓）----------------
    B("neck1", "body1", (0, 34, -96), [
        cube(0, 38, -104, 14, 14, 14, "scale"),
        cube(0, 46, -104, 2, 6, 10, "fin"),                       # 颈鳍
    ], rot=[14, 0, 0])
    B("neck2", "neck1", (0, 42, -111), [
        cube(0, 47, -119, 14, 13, 14, "scale"),
        cube(0, 54, -119, 2, 6, 10, "fin"),
    ], rot=[10, 0, 0])
    B("head", "neck2", (0, 50, -126), [
        cube(0, 52, -136, 18, 15, 20, "scale"),                   # 颅
        cube(0, 60, -138, 16, 4, 14, "scale"),                    # 眉脊
        cube(0, 52, -156, 12, 11, 18, "scale"),                   # 上吻
        cube(0, 57, -158, 9, 4, 12, "scale"),                     # 鼻梁
        cube(0, 62, -160, 4, 6, 4, "bone"),                       # 鼻角
        cube(9, 58, -148, 3, 7, 10, "eye"),                       # ★ 大眼在前角 ⇒ 正面也看得见 ✓
        cube(-9, 58, -148, 3, 7, 10, "eye"),
        cube(9, 63, -146, 6, 3, 12, "fin"),                       # ★ 金眉（压在眼上 ✓）
        cube(-9, 63, -146, 6, 3, 12, "fin"),
        cube(9, 64, -152, 4, 6, 6, "fin", rot=[0, 0, -22.5]),     # 耳
        cube(-9, 64, -152, 4, 6, 6, "fin", rot=[0, 0, 22.5]),
        cube(0, 56, -128, 14, 4, 10, "fin"),                      # 头顶鬃（小）
        cube(5, 46, -160, 2, 5, 5, "bone"),                       # 獠牙
        cube(-5, 46, -160, 2, 5, 5, "bone"),
        cube(5, 46, -153, 2, 5, 5, "bone"),
        cube(-5, 46, -153, 2, 5, 5, "bone"),
    ], rot=[-6, 0, 0])
    B("jaw", "head", (0, 47, -152), [
        cube(0, 42, -155, 12, 7, 20, "scale"),                    # 下颌
        cube(0, 36, -148, 8, 7, 8, "fin"),                        # 颌下胡（那撮龙须 ✓）
        cube(6, 39, -144, 8, 3, 6, "fin", rot=[0, 0, -22.5]),
    ])
    B("antlerL", "head", (6, 60, -132), [
        cube(8, 67, -128, 5, 12, 5, "bone"),
        cube(11, 74, -126, 4, 9, 4, "bone", rot=[0, 0, -22.5]),
    ])
    B("antlerL2", "antlerL", (11, 72, -126), [
        cube(17, 75, -120, 5, 7, 5, "bone", rot=[0, 0, -22.5]),
        cube(6, 72, -110, 4, 8, 4, "bone", rot=[22.5, 0, 0]),     # 往回拐的那一叉 ✓
    ])
    B("whiskerL", "head", (6, 48, -156), [
        cube(15, 49, -150, 18, 3, 3, "bone", rot=[0, -22.5, 0]),  # 长须第 1 节
    ])
    B("whiskerL2", "whiskerL", (24, 49, -150), [
        cube(34, 51, -140, 18, 3, 3, "bone", rot=[0, -22.5, 22.5]),  # 第 2 节往上翘 ✓
    ])
    B("mane", "neck1", (0, 40, -100), [
        cube(0, 36, -94, 16, 4, 6, "fin"),
        cube(9, 40, -92, 12, 3, 6, "fin", rot=[0, 0, -22.5]),     # 后面几撮是镜像补的 ✓
        cube(9, 29, -92, 12, 3, 6, "fin", rot=[0, 0, 22.5]),
        cube(13, 40, -84, 14, 3, 6, "fin", rot=[0, -22.5, -22.5]),
        cube(13, 29, -84, 14, 3, 6, "fin", rot=[0, -22.5, 22.5]),
        cube(15, 35, -74, 14, 3, 6, "fin", rot=[0, -45, 0]),
    ])

    # ---------------- 身体：12 节，一节一根骨头（舞龙那种"一节一节" ✓）----------------
    sizes = [16, 16, 15, 15, 14, 13, 12, 11, 10, 9, 8, 7]
    bends = [4, 4, 4, 2, 2, 0, -2, -2, -2, -4, -4, -4]            # 横向 S 形（y 轴 = 转身 ✓）
    for i in range(12):
        z = -96 + 16 * i
        s = sizes[i]
        mat = "scale" if i % 2 == 0 else "scale2"
        cy = 30 if i < 6 else 29
        cubes = [
            cube(0, cy, z + 8, s, s, 16, mat),
            cube(0, cy + s // 2 + 4, z + 8, 2, 8, 14, "fin"),     # 背鳍（高一点才看得出 ✓）
            cube(0, cy - s // 2 - 2, z + 8, max(4, s - 4), 5, 14, "belly"),
            cube(0, cy, z + 1, s + 2, s + 2, 3, "belly"),         # ★ 节与节之间的**金箍** ✓
        ]                                                          #   = 舞龙那种一节一节 ✓
        if i == 1:                                                # 前腿挂这里
            B("legFL1", "body2", (9, 22, z + 8), [
                cube(10, 16, z + 8, 8, 12, 10, "scale"),
            ])
            B("legFL2", "legFL1", (10, 11, z + 8), [
                cube(10, 6, z + 8, 6, 10, 8, "scale"),
            ])
            B("pawFL", "legFL2", (10, 2, z + 8), [
                cube(10, 1, z + 6, 8, 4, 12, "scale"),
                cube(7, 1, z - 1, 2, 2, 5, "claw"),
                cube(10, 1, z - 2, 2, 2, 5, "claw"),
                cube(13, 1, z - 1, 2, 2, 5, "claw"),
            ])
        if i == 8:                                                # 后腿挂这里
            B("legBL1", "body9", (9, 21, z + 8), [
                cube(11, 15, z + 8, 8, 12, 11, "scale"),
            ])
            B("legBL2", "legBL1", (11, 10, z + 8), [
                cube(11, 5, z + 8, 6, 10, 8, "scale"),
            ])
            B("pawBL", "legBL2", (11, 2, z + 8), [
                cube(11, 1, z + 6, 8, 4, 12, "scale"),
                cube(8, 1, z - 1, 2, 2, 5, "claw"),
                cube(11, 1, z - 2, 2, 2, 5, "claw"),
                cube(14, 1, z - 1, 2, 2, 5, "claw"),
            ])
        B("body%d" % (i + 1), "body%d" % i if i else "root", (0, cy, z), cubes,
          rot=[0, bends[i], 0])

    # ---------------- 尾巴：5 节 + 尾焰 ----------------
    tail_sizes = [7, 6, 5, 4, 3]
    for i in range(5):
        z = 96 + 16 * i
        s = max(4, tail_sizes[i] + (tail_sizes[i] % 2))
        cubes = [
            cube(0, 29, z + 8, s, s, 16, "scale" if i % 2 == 0 else "scale2"),
            cube(0, 29 + s // 2 + 4, z + 8, 2, 7, 12, "fin"),
            cube(0, 29 - s // 2 - 2, z + 8, max(4, s - 2), 4, 12, "belly"),
            cube(0, 29, z + 1, s + 2, s + 2, 3, "belly"),
        ]
        B("tail%d" % (i + 1), "body12" if i == 0 else "tail%d" % i, (0, 29, z), cubes,
          rot=[-2, bends[11] if i == 0 else 0, 0])
    B("tailFin", "tail5", (0, 29, 176), [
        cube(0, 34, 188, 2, 22, 22, "fin"),                       # 主焰
        cube(0, 46, 184, 2, 12, 12, "fin", rot=[22.5, 0, 0]),
        cube(0, 24, 184, 2, 12, 12, "fin", rot=[-22.5, 0, 0]),
        cube(9, 34, 182, 12, 2, 16, "fin", rot=[0, 0, -22.5]),    # 侧焰（镜像补右边 ✓）
        cube(12, 40, 176, 12, 2, 14, "fin", rot=[0, -22.5, -22.5]),
    ])
    return b


def mirrored(bones):
    """right half: negate x on pivot + cube centre, negate the y/z rotation angles.
    NEVER a `mirror` flag (that flips geometry and texture again -> broken, see the wings)."""
    out = []
    for bone in bones:
        out.append(bone)
        if "L" in bone["name"]:
            def flip_rot(r):
                return None if r is None else (r[0], -r[1], -r[2])
            twin = {
                "name": bone["name"].replace("L", "R"),
                "parent": bone["parent"].replace("L", "R"),
                "pivot": [-bone["pivot"][0], bone["pivot"][1], bone["pivot"][2]],
                "rot": flip_rot(bone.get("rot")),
                "cubes": [dict(c, c=(-c["c"][0], c["c"][1], c["c"][2]), rot=flip_rot(c.get("rot")))
                          for c in bone["cubes"]],
            }
            out.append(twin)
    return out


class Packer:
    """shelf packer: every cube asks for a (2d+2w) x (d+h) rectangle (Bedrock box UV)"""

    def __init__(self, size):
        self.size = size
        self.x = MARGIN
        self.y = MARGIN
        self.row_h = 0
        self.used = 0

    def alloc(self, w, h):
        if self.x + w + MARGIN > self.size:
            self.x = MARGIN
            self.y += self.row_h + MARGIN
            self.row_h = 0
        if self.y + h + MARGIN > self.size:
            raise RuntimeError("texture atlas %dx%d is too small" % (self.size, self.size))
        box = (self.x, self.y, w, h)
        self.x += w + MARGIN
        self.row_h = max(self.row_h, h)
        self.used += w * h
        return box


def build():
    bones = mirrored(left_bones())
    bones.insert(0, {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": []})

    packer = Packer(TEX)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    out_bones = []
    for bone in bones:
        cubes = []
        for c in bone["cubes"]:
            cx, cy, cz = c["c"]
            w, h, d = c["s"]
            box = packer.alloc(2 * d + 2 * w, d + h)
            MATERIALS[c["mat"]](img, box, (w, h, d))
            entry = {
                "origin": [cx - w / 2.0, cy - h / 2.0, cz - d / 2.0],
                "size": [w, h, d],
                "uv": [box[0], box[1]],
            }
            if c.get("rot"):
                entry["rotation"] = list(c["rot"])
            cubes.append(entry)
        e = {"name": bone["name"], "pivot": bone["pivot"]}
        if bone["parent"]:
            e["parent"] = bone["parent"]
        if bone.get("rot"):
            e["rotation"] = list(bone["rot"])
        if cubes:
            e["cubes"] = cubes
        out_bones.append(e)

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (MODID, NAME),
                "texture_width": TEX, "texture_height": TEX,
                "visible_bounds_width": 24, "visible_bounds_height": 8,
                "visible_bounds_offset": [0, 3, 0],
            },
            "bones": out_bones,
        }],
    }
    report = {
        "bones": len(out_bones),
        "cubes": sum(len(b.get("cubes", [])) for b in out_bones),
        "atlas_used": packer.used,
        "atlas_pct": round(100.0 * packer.used / (TEX * TEX), 1),
    }
    return geo, img, report


def main():
    import sys
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "geo", "entity", NAME + ".geo.json")
    tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "entity", NAME + "_bedrock.png")
    pp = os.path.join(repo, "docs", "previews", NAME + "_texture.png")
    for p in (gp, tp, pp):
        os.makedirs(os.path.dirname(p), exist_ok=True)
    geo, tex, rep = build()

    # ★ 作者 2026-10-02 自己改过这个模型 ⇒ 这里**拒绝覆盖** ✗（不然他手改的东西就没了）
    #   要重画贴图请用 tools/retexture_dragon.py ✓（它只动 png，不动 geo ✓）
    if os.path.exists(gp) and "--force" not in sys.argv:
        try:
            old = json.load(open(gp, encoding="utf-8"))["minecraft:geometry"][0]
            old_cubes = sum(len(b.get("cubes", [])) for b in old["bones"])
        except Exception:
            old_cubes = -1
        if old_cubes != rep["cubes"]:
            print("REFUSING to overwrite %s" % os.path.relpath(gp, repo))
            print("  on disk : %d cubes   this script would write: %d cubes" % (old_cubes, rep["cubes"]))
            print("  (the author edited the model by hand - use tools/retexture_dragon.py instead;"
                  " add --force only if you really mean to throw his edit away)")
            return

    with open(gp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(geo, f, indent=2, ensure_ascii=False)
        f.write("\n")
    tex.save(tp)
    tex.resize((TEX, TEX), Image.NEAREST).save(pp)
    print("geo     -> %s" % os.path.relpath(gp, repo))
    print("texture -> %s" % os.path.relpath(tp, repo))
    print("bones=%d cubes=%d  atlas %d px (%.1f%%)"
          % (rep["bones"], rep["cubes"], rep["atlas_used"], rep["atlas_pct"]))


if __name__ == "__main__":
    main()
