# -*- coding: utf-8 -*-
"""
retexture_dragon.py -- paint a NEW texture for the author's edited dragon geo.

Author 2026-10-02 (after editing the model himself):
    "我修改了一下模型，你按照这个新生成一下贴图，然后我希望贴图可以改成光明龙的感觉"

So the author's `dragon.geo.json` is now the SOURCE OF TRUTH and this script:
  * reads his geo (41 bones / 100 cubes) and his **own box-UV layout** (512x512, verified:
    every rectangle is in bounds and NO two cubes share a pixel -> nothing to repack);
  * classifies every cube into a material role (by bone name + shape, see classify());
  * paints each cube's own rectangle in place with the LIGHT-DRAGON palette;
  * writes ONLY the png - the geo is never modified (the script asserts its hash first/last).

PALETTE: 光明龙 (radiant / "白金龙")  -  pearl-white scales with gold rims and a faint
iridescent sheen, cream belly, bright gold fins / mane / tail flame, white-gold antlers,
white-hot eyes.  (If the renderer later uses entityTranslucentEmissive the bright gold and
the white core will actually glow, the same trick as the light wings / angel.)
ASCII only.  Usage:  python tools/retexture_dragon.py
"""
import hashlib
import json
import math
import os
import random

from PIL import Image

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "dragon.geo.json")
TEX = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "entity", "dragon_bedrock.png")
PREVIEW = os.path.join("docs", "previews", "dragon_texture.png")

# ------------------------------------------------------------------ light-dragon palette
SCALE = (250, 248, 240)         # 珍珠白鳞（更亮 ✓）
SCALE_HI = (255, 206, 92)       # 金边（更饱和 ✓）
SCALE2 = (240, 230, 200)        # 隔节的暖白（舞龙那种一节一节 ✓）
BELLY = (255, 250, 236)         # 奶白腹甲
FIN = (255, 206, 96)            # 金鳍 / 鬃 / 尾焰（亮金 ✓）
BONE = (252, 248, 236)          # 角 / 须 / 牙（白金色 ✓）
CLAW = (232, 208, 152)          # 爪（浅金 ✓）
EYE = (255, 240, 180)           # 眼（白金发光 ✓）
SHEEN = (168, 220, 245)         # 虹彩偏色（很淡的一点冷色 ⇒ "光"的感觉 ✓）
SEAM = (214, 206, 186)          # 鳞缝：淡暖灰（别太重，不然整条龙发灰 ✗）


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, k):
    return tuple(clamp(a[i] * (1.0 - k) + b[i] * k) for i in range(3))


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


def paint_scales(img, box, size, base, hi, period=8, seed=1, dorsal=0.88, ventral=1.06):
    """pearl scales + gold rims + a faint iridescent sheen; box-UV aware (back/underside)"""
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
                c = mix(base, SEAM, 0.55)                 # 鳞缝：淡暖灰
            # 很淡的虹彩：每隔几行偏一点冷色/暖色 ✓（"光"的彩色感 ✓）
            k = 0.08 + 0.05 * math.sin((x * 0.7 + y * 1.3) * 0.35)
            c = mix(c, SHEEN if (row % 3 == 0) else (255, 232, 200), max(0.0, k))
            j = rng.randint(-5, 5)
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] + j), clamp(c[1] + j), clamp(c[2] + j), 255))
    _tone(img, box, (cd, 0, cw, cd), dorsal, 5, rng)              # up   -> 背脊
    _tone(img, box, (cd + cw, 0, cw, cd), ventral, 5, rng)        # down -> 腹面更亮


def paint_belly(img, box, size, base, seed=2):
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        band = y % 7
        for x in range(w):
            k = 1.0 if band < 5 else (1.0 if band == 5 else 0.90)
            c = mix(base, (255, 214, 118), 0.35 if band == 5 else 0.0)   # 金色横缝 ✓
            j = rng.randint(-4, 4)
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] * k + j), clamp(c[1] * k + j),
                                            clamp(c[2] * k + j), 255))


def paint_fin(img, box, size, base, seed=3):
    """bright gold fin / mane / tail flame: solid gold, white-hot towards the outer edge"""
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        for x in range(w):
            # 越靠外缘越白（外缘 = 该矩形靠上的那一段 = 鳍尖 ✓），中间保持亮金 ✓
            edge = max(0.0, 1.0 - y / max(1.0, h * 0.45))
            streak = abs(math.sin(x * 0.9)) * 0.25
            c = mix(base, (255, 253, 244), min(0.85, edge * 1.1 + streak))
            k = 0.94 + 0.16 * streak
            j = rng.randint(-5, 5)
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] * k + j), clamp(c[1] * k + j),
                                            clamp(c[2] * k + j), 255))


def paint_bone(img, box, size, base, seed=4):
    x0, y0, w, h = box
    rng = random.Random(seed)
    for y in range(h):
        for x in range(w):
            ring = (y // 4) % 2
            c = mix(base, (255, 214, 118), 0.30 if ring else 0.0)        # 金色环纹 ✓
            j = rng.randint(-5, 5)
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] + j), clamp(c[1] + j), clamp(c[2] + j), 255))


def paint_claw(img, box, size, base, seed=5):
    x0, y0, w, h = box
    for y in range(h):
        for x in range(w):
            k = 0.80 + 0.35 * (x / float(max(1, w - 1)))
            c = mix(base, (255, 250, 235), 0.35 * (1.0 - x / float(max(1, w - 1))))
            img.putpixel((x0 + x, y0 + y), (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255))


def paint_eye(img, box, size, base, seed=6):
    """white-hot core, gold iris, dark slit"""
    x0, y0, w, h = box
    for y in range(h):
        for x in range(w):
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            if abs(x - cx) < 0.9 and abs(y - cy) < h * 0.40:
                img.putpixel((x0 + x, y0 + y), (46, 30, 14, 255))
            else:
                d = math.hypot((x - cx) / max(1.0, cx), (y - cy) / max(1.0, cy))
                c = mix(base, (255, 255, 250), max(0.0, 1.0 - d))         # 中心接近纯白 ✓
                k = max(0.55, 1.10 - 0.55 * d)
                img.putpixel((x0 + x, y0 + y), (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255))


# ------------------------------------------------------------------ classification
def classify(bone, size, origin):
    """bone name + shape -> material role. Mirrors how the model was built (and how the
    author's re-export kept the same cube roles)."""
    w, h, d = [int(round(v)) for v in size]
    name = bone.lower()

    if name.startswith("antler") or name.startswith("whisker"):
        return "bone"
    if name == "mane" or name == "tailfin":
        return "fin"
    if name.startswith("paw") or name.startswith("claw"):
        return "claw" if max(w, h, d) <= 5 else "scale"
    if name.startswith("leg"):
        return "scale"

    if name == "head":
        if w == 3:                                   # 眼球
            return "eye"
        if w == 2:                                   # 獠牙
            return "bone"
        if w == 4 and h == 6 and d == 4:             # 鼻角
            return "bone"
        if (w == 6 and h == 3) or (w == 4 and h == 6 and d == 6) or (w >= 12 and h <= 4):
            return "fin"                             # 金眉 / 耳 / 头顶鬃
        return "scale"                               # 颅 / 上吻 / 鼻梁 / 眉脊

    if name == "jaw":
        return "fin" if max(w, h, d) <= 10 else "scale"

    # 身体 / 脖子 / 尾巴：一节四块 = 主节 + 背鳍 + 腹甲 + 金箍
    if d <= 4 and w >= 6 and h >= 6:
        return "hoop"                                # 薄薄的（z 很扁）=> 节与节之间的金箍
    if w <= 2:
        return "fin"                                 # 薄薄的（x 很扁）=> 背鳍
    if h <= 5 and w >= 4:
        return "belly"                               # 扁而低 => 腹甲
    return "main"


PAINTERS = {
    "scale": lambda img, box, size: paint_scales(img, box, size, SCALE, SCALE_HI, 8, 31),
    "scale2": lambda img, box, size: paint_scales(img, box, size, SCALE2, SCALE_HI, 8, 32),
    "belly": lambda img, box, size: paint_belly(img, box, size, BELLY),
    "hoop": lambda img, box, size: paint_belly(img, box, size, (255, 212, 104)),
    "fin": lambda img, box, size: paint_fin(img, box, size, FIN),
    "bone": lambda img, box, size: paint_bone(img, box, size, BONE),
    "claw": lambda img, box, size: paint_claw(img, box, size, CLAW),
    "eye": lambda img, box, size: paint_eye(img, box, size, EYE),
}


def segment_index(bone):
    for prefix in ("body", "tail"):
        if bone.startswith(prefix):
            digits = "".join(ch for ch in bone[len(prefix):] if ch.isdigit())
            if digits:
                return int(digits)
    return 0


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    geo_path, tex_path = GEO, TEX
    before = hashlib.sha256(open(geo_path, "rb").read()).hexdigest()

    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    tw = geo["description"]["texture_width"]
    th = geo["description"]["texture_height"]
    img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))

    report = {}
    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            u, v = cube["uv"]
            w, h, d = [int(round(x)) for x in cube["size"]]
            box = (int(round(u)), int(round(v)), 2 * d + 2 * w, d + h)
            role = classify(bone["name"], cube["size"], cube["origin"])
            if role == "main":                       # 隔节换深浅 => 舞龙那种一节一节 ✓
                role = "scale" if segment_index(bone["name"]) % 2 else "scale2"
            PAINTERS[role](img, box, (w, h, d))
            report[role] = report.get(role, 0) + 1

    os.makedirs(os.path.dirname(tex_path), exist_ok=True)
    img.save(tex_path)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    img.resize((tw, th), Image.NEAREST).save(PREVIEW)

    after = hashlib.sha256(open(geo_path, "rb").read()).hexdigest()
    assert before == after, "the geo was modified - this tool must never touch the model"
    print("texture -> %s (%dx%d)" % (tex_path, tw, th))
    print("preview -> %s" % PREVIEW)
    print("geo untouched (sha256 %s...)" % before[:12])
    print("cubes painted: %d  ->  %s" % (sum(report.values()),
          ", ".join("%s=%d" % kv for kv in sorted(report.items()))))


if __name__ == "__main__":
    main()
