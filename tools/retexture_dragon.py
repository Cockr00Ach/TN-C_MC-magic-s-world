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

# ------------------------------------------------------------------ palettes
# 作者 2026-10-02：先要"光明龙"，紧接着"复制一下光龙，生成一个暗龙" ✓
#   ⇒ 同一套 UV / 同一套材质分类，只换调色板 ✓（龙模型是共享的，暗龙只是换贴图 ✓）
# 用法：python tools/retexture_dragon.py            （光明龙 -> dragon_bedrock.png）
#       python tools/retexture_dragon.py --dark     （暗龙   -> dragon_dark_bedrock.png）
PALETTES = {
    "light": {
        "name": "光明龙",
        "scale": (250, 248, 240),      # 珍珠白鳞
        "scale_hi": (255, 206, 92),    # 金边
        "scale2": (240, 230, 200),     # 隔节的暖白（舞龙那种一节一节 ✓）
        "belly": (255, 250, 236),      # 奶白腹甲
        "fin": (255, 206, 96),         # 金鳍 / 鬃 / 尾焰
        "bone": (252, 248, 236),       # 角 / 须 / 牙
        "claw": (232, 208, 152),       # 爪
        "eye": (255, 240, 180),        # 眼（白金发光）
        "sheen": (168, 220, 245),      # 很淡的冷色虹彩
        "seam": (214, 206, 186),       # 鳞缝
        "hoop": (255, 212, 104),       # 节与节之间的金箍
        "out": "dragon_bedrock.png",
        "preview": "dragon_texture.png",
    },
    "dark": {
        "name": "暗龙",
        "scale": (46, 38, 62),         # 玄黑紫鳞
        "scale_hi": (150, 62, 190),    # 幽紫亮边
        "scale2": (32, 26, 44),        # 隔节更深（一节一节 ✓）
        "belly": (92, 70, 118),        # 暗紫腹甲
        "fin": (168, 44, 108),         # 血红／紫红的鳍与尾焰
        "bone": (206, 200, 214),       # 角 / 须（冷骨白）
        "claw": (150, 132, 160),
        "eye": (255, 74, 84),          # 眼（血红发光）
        "sheen": (90, 60, 170),        # 幽紫偏色
        "seam": (24, 18, 32),
        "hoop": (120, 40, 150),        # 节与节之间的紫箍
        "out": "dragon_dark_bedrock.png",
        "preview": "dragon_dark_texture.png",
    },
}

SCALE = SCALE_HI = SCALE2 = BELLY = FIN = BONE = CLAW = EYE = SHEEN = SEAM = None
HOOP = None


def use_palette(key):
    """切换调色板 —— 下面那些 painter 都是按**模块全局名**取色的 ✓（所以这里改全局就生效 ✓）"""
    global SCALE, SCALE_HI, SCALE2, BELLY, FIN, BONE, CLAW, EYE, SHEEN, SEAM, HOOP, PAL
    p = PALETTES[key]
    PAL = p
    SCALE, SCALE_HI, SCALE2 = p["scale"], p["scale_hi"], p["scale2"]
    BELLY, FIN, BONE, CLAW, EYE = p["belly"], p["fin"], p["bone"], p["claw"], p["eye"]
    SHEEN, SEAM, HOOP = p["sheen"], p["seam"], p["hoop"]
    return p


PAL = PALETTES["light"]


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
    "hoop": lambda img, box, size: paint_belly(img, box, size, HOOP),
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
    import sys
    dark = "--dark" in sys.argv
    pal = use_palette("dark" if dark else "light")
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    geo_path = GEO
    tex_path = os.path.join(os.path.dirname(TEX), pal["out"])
    preview_path = os.path.join(os.path.dirname(PREVIEW), pal["preview"])
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
    os.makedirs(os.path.dirname(preview_path), exist_ok=True)
    img.resize((tw, th), Image.NEAREST).save(preview_path)

    after = hashlib.sha256(open(geo_path, "rb").read()).hexdigest()
    assert before == after, "the geo was modified - this tool must never touch the model"
    print("[%s] texture -> %s (%dx%d)" % (pal["name"], tex_path, tw, th))
    print("[%s] preview -> %s" % (pal["name"], preview_path))
    print("geo untouched (sha256 %s...)" % before[:12])
    print("cubes painted: %d  ->  %s" % (sum(report.values()),
          ", ".join("%s=%d" % kv for kv in sorted(report.items()))))


if __name__ == "__main__":
    main()
