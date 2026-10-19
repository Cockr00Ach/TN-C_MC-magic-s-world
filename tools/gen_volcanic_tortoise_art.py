"""火山巨龟（Volcanic Tortoise）两阶段美术资源生成器。

一份数据（BONES / CUBES）同时产出四类文件，保证模型、贴图、动画、Blockbench 工程
三者坐标与 UV 完全一致：

    src/main/resources/assets/tnc/geo/entity/volcanic_tortoise_phase1.geo.json
    src/main/resources/assets/tnc/geo/entity/volcanic_tortoise_phase2.geo.json
    src/main/resources/assets/tnc/animations/entity/volcanic_tortoise.animation.json
    src/main/resources/assets/tnc/textures/entity/volcanic_tortoise_phase1.png
    src/main/resources/assets/tnc/textures/entity/volcanic_tortoise_phase2.png
    art/volcanic_tortoise/volcanic_tortoise_phase1.bbmodel
    art/volcanic_tortoise/volcanic_tortoise_phase2.bbmodel

坐标约定（与 Bedrock / GeckoLib 一致）：
    +X 右，+Y 上，-Z 前（模型朝 -Z 看，与 MC 原版生物模型一致）
    1 单位 = 1/16 格；地面在 y = 0。

贴图采用方块 UV（box_uv）：每个立方体在贴图里占一块 (2*(宽+深)) x (深+高) 的区域，
六个面的位置由 MC 标准布局决定，因此贴图与模型不会错位。

用法：  python tools/gen_volcanic_tortoise_art.py
"""

from __future__ import annotations

import base64
import io
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc")
ART = os.path.join(ROOT, "art", "volcanic_tortoise")

SHEET = 1024
PAD = 2

# ---------------------------------------------------------------- 调色板 ----
# 一阶段：冷却下来的火山岩（黑玄武岩 + 灰岩 + 暗红裂缝 + 少量矿物/苔色）
# 二阶段：同一套岩体，但裂缝里灌满岩浆（亮橙 + 白热芯 + 黑曜石）
ROCK = {
    # 关光影验收用：整体抬亮一档，最深也只到深灰，避免大面积近黑
    "basalt": (84, 78, 72),
    "rock": (108, 100, 92),
    "rock_lt": (134, 125, 113),
    "ash": (158, 148, 134),
    "mustard": (146, 122, 88),
    "belly": (100, 92, 82),
    "bone": (206, 198, 180),
    "lichen": (108, 128, 84),
    "obsidian": (68, 60, 64),
    "obsidian_dk": (44, 38, 42),
    "magma_dim": (156, 64, 28),
    "magma": (242, 110, 26),
    "magma_hot": (255, 190, 78),
    "core": (255, 244, 196),
    "ember": (255, 74, 16),
}

# 每个立方体用一个"主题"，决定六个面的底色与花纹
# strata=岩层带 / scales=鳞片格 / moss=苔藓块数
THEMES = {
    "shell": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "mustard",
              "crack": 3, "strata": True, "scales": True, "moss": 3},
    "shell_hi": {"base": "rock_lt", "grain": "ash", "dark": "rock", "accent": "mustard",
                 "crack": 2, "strata": True, "scales": True, "moss": 2},
    "shell_low": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim",
                  "crack": 2, "strata": True, "moss": 1},
    "belly": {"base": "belly", "grain": "rock_lt", "dark": "basalt", "accent": "bone",
              "crack": 1, "strata": True},
    "leg": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim",
            "crack": 2, "strata": True, "moss": 1},
    "foot": {"base": "rock", "grain": "ash", "dark": "basalt", "accent": "bone", "crack": 1},
    "head": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma_dim",
             "crack": 2, "scales": True, "moss": 2},
    "jaw": {"base": "ash", "grain": "bone", "dark": "rock", "accent": "obsidian", "crack": 0},
    "tail": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim",
             "crack": 1},
    "eye": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core",
            "crack": 0, "flat": True},
    # 一阶段的冷却熔岩沟：暗红为主，别让整条沟是黑的
    "groove": {"base": "magma_dim", "grain": "basalt", "dark": "obsidian", "accent": "magma",
               "crack": 4, "strata": True},
    # ---- 二阶段专用 ----
    "lava": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core",
             "crack": 0, "flat": True},
    "lava_rock": {"base": "obsidian", "grain": "basalt", "dark": "obsidian_dk",
                  "accent": "magma", "crack": 6, "strata": True, "scales": True},
    "lava_groove": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core",
                    "crack": 0},
    "spike": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma",
              "crack": 4},
}

# ---------------------------------------------------------------- 骨骼 ----
# 名字一个都没改（动画兼容）；只是 pivot 跟着放大的躯体往上/外挪。
# (名字, pivot, 父骨骼)
BONES = [
    ("root", (0, 0, 0), None),
    ("body", (0, 20, 0), "root"),
    ("shell", (0, 31, 0), "body"),
    ("whorl", (0, 66, 0), "shell"),
    ("neck", (0, 22, -34), "body"),
    ("head", (0, 24, -54), "neck"),
    ("tail", (0, 18, 32), "body"),
    ("leg_fl", (33, 18, -30), "body"),
    ("leg_fr", (-33, 18, -30), "body"),
    ("leg_bl", (33, 18, 26), "body"),
    ("leg_br", (-33, 18, 26), "body"),
]

# (骨骼, 名字, 最小角 origin, 尺寸 size, 主题)
# 基准：1 单位 = 1/16 格；地面 y=0；前方是 -Z。
# 轮廓：宽 84 (5.25 格) × 高 66 (4.1 格) × 长 139 (8.7 格) —— 约为上一版 1.5 倍。
# 层次（自下而上）：脚掌 → 腿 → 腹甲 → 壳缘(带缘盾) → 火山一级 → 二级 → 火山口内壁 → 厚口沿。
CUBES_P1 = [
    # --- 腹甲 ---
    ("body", "belly", (-26, 14, -32), (52, 12, 64), "belly"),
    # --- 壳缘：宽厚的一圈，正面/侧面都能看出龟壳轮廓 ---
    ("shell", "rim", (-42, 24, -42), (84, 9, 84), "shell_low"),
    ("shell", "scute_f", (-12, 24, -46), (24, 9, 6), "shell_low"),
    ("shell", "scute_b", (-12, 24, 40), (24, 9, 6), "shell_low"),
    ("shell", "scute_l", (40, 24, -14), (6, 9, 28), "shell_low"),
    ("shell", "scute_r", (-46, 24, -14), (6, 9, 28), "shell_low"),
    ("shell", "scute_fl", (30, 24, -44), (12, 9, 12), "shell_low"),
    ("shell", "scute_fr", (-42, 24, -44), (12, 9, 12), "shell_low"),
    ("shell", "scute_bl", (30, 24, 32), (12, 9, 12), "shell_low"),
    ("shell", "scute_br", (-42, 24, 32), (12, 9, 12), "shell_low"),
    # --- 龟壳穹顶两级（保住"龟"的轮廓，火山从它上面长出来）---
    ("shell", "dome1", (-34, 33, -34), (68, 6, 68), "shell_hi"),
    ("shell", "dome2", (-26, 39, -26), (52, 5, 52), "shell"),
    # --- 火山锥：六级，每级各收 5~6 单位，坡度接近真火山而不是直上直下的塔 ---
    ("shell", "volc1", (-22, 44, -22), (44, 5, 44), "shell_low"),
    ("shell", "volc2", (-19, 49, -19), (38, 5, 38), "shell_low"),
    ("shell", "volc3", (-16, 54, -16), (32, 5, 32), "shell_low"),
    ("shell", "volc4", (-13, 59, -13), (26, 5, 26), "shell_low"),
    # --- 火山口：上面两级做成"环"，中间是通的竖井，往上看能看见岩浆 ---
    ("shell", "ring5_f", (-10, 64, -10), (20, 5, 4), "shell"),
    ("shell", "ring5_b", (-10, 64, 6), (20, 5, 4), "shell"),
    ("shell", "ring5_l", (6, 64, -6), (4, 5, 12), "shell"),
    ("shell", "ring5_r", (-10, 64, -6), (4, 5, 12), "shell"),
    ("shell", "lip_f", (-9, 69, -9), (18, 5, 4), "shell_hi"),
    ("shell", "lip_b", (-9, 69, 5), (18, 5, 4), "shell_hi"),
    ("shell", "lip_l", (5, 69, -5), (4, 5, 10), "shell_hi"),
    ("shell", "lip_r", (-9, 69, -5), (4, 5, 10), "shell_hi"),
    # --- 熔岩沟槽：四级坡面各四面，顺着坡往下淌 ---
    ("shell", "groove_f1", (-2, 44, -24), (4, 5, 3), "groove"),
    ("shell", "groove_f2", (-2, 49, -21), (4, 5, 3), "groove"),
    ("shell", "groove_f3", (-2, 54, -18), (4, 5, 3), "groove"),
    ("shell", "groove_f4", (-2, 59, -15), (4, 5, 3), "groove"),
    ("shell", "groove_b1", (-2, 44, 21), (4, 5, 3), "groove"),
    ("shell", "groove_b2", (-2, 49, 18), (4, 5, 3), "groove"),
    ("shell", "groove_b3", (-2, 54, 15), (4, 5, 3), "groove"),
    ("shell", "groove_b4", (-2, 59, 12), (4, 5, 3), "groove"),
    ("shell", "groove_l1", (22, 44, -2), (3, 5, 4), "groove"),
    ("shell", "groove_l2", (19, 49, -2), (3, 5, 4), "groove"),
    ("shell", "groove_l3", (16, 54, -2), (3, 5, 4), "groove"),
    ("shell", "groove_l4", (13, 59, -2), (3, 5, 4), "groove"),
    ("shell", "groove_r1", (-25, 44, -2), (3, 5, 4), "groove"),
    ("shell", "groove_r2", (-22, 49, -2), (3, 5, 4), "groove"),
    ("shell", "groove_r3", (-19, 54, -2), (3, 5, 4), "groove"),
    ("shell", "groove_r4", (-16, 59, -2), (3, 5, 4), "groove"),
    # --- 溢流：口沿上漫出来的岩浆（侧面一眼就能看见）---
    ("shell", "spill_f", (-4, 74, -9), (8, 2, 5), "groove"),
    ("shell", "spill_b", (-4, 74, 4), (8, 2, 5), "groove"),
    # --- 火山口里的岩浆（挂 whorl 骨骼，idle 里慢慢转）---
    ("whorl", "lava_pool", (-6, 66, -6), (12, 3, 12), "lava"),
    # --- 脖子 / 头 ---
    ("neck", "neck", (-14, 20, -50), (28, 18, 20), "shell_low"),
    ("head", "head", (-16, 20, -76), (32, 24, 26), "head"),
    ("head", "snout", (-11, 16, -85), (22, 10, 10), "jaw"),
    ("head", "brow", (-17, 44, -76), (34, 4, 14), "head"),
    ("head", "eye_l", (9, 32, -78), (4, 5, 3), "eye"),
    ("head", "eye_r", (-13, 32, -78), (4, 5, 3), "eye"),
    # --- 四条腿（粗腿 + 外露脚掌）---
    ("leg_fl", "thigh_fl", (26, 4, -34), (20, 20, 20), "leg"),
    ("leg_fl", "foot_fl", (25, 0, -35), (22, 6, 22), "foot"),
    ("leg_fr", "thigh_fr", (-46, 4, -34), (20, 20, 20), "leg"),
    ("leg_fr", "foot_fr", (-47, 0, -35), (22, 6, 22), "foot"),
    ("leg_bl", "thigh_bl", (26, 4, 14), (20, 20, 20), "leg"),
    ("leg_bl", "foot_bl", (25, 0, 13), (22, 6, 22), "foot"),
    ("leg_br", "thigh_br", (-46, 4, 14), (20, 20, 20), "leg"),
    ("leg_br", "foot_br", (-47, 0, 13), (22, 6, 22), "foot"),
    # --- 尾巴 ---
    ("tail", "tail1", (-7, 16, 32), (14, 10, 14), "tail"),
    ("tail", "tail2", (-4, 11, 45), (8, 8, 12), "tail"),
]

# 二阶段：同骨架同尺寸；山体/壳面换熔岩岩板、沟槽灌满岩浆、口沿与头部长刺
CUBES_P2 = [c for c in CUBES_P1 if not (
    c[1].startswith("groove_") or c[1].startswith("spill_")
    or c[1] in ("lava_pool", "eye_l", "eye_r", "brow",
                "volc1", "volc2", "volc3", "volc4", "dome2"))]
CUBES_P2 += [
    ("shell", "dome2", (-26, 39, -26), (52, 5, 52), "lava_rock"),
    ("shell", "volc1", (-22, 44, -22), (44, 5, 44), "lava_rock"),
    ("shell", "volc2", (-19, 49, -19), (38, 5, 38), "lava_rock"),
    ("shell", "volc3", (-16, 54, -16), (32, 5, 32), "lava_rock"),
    ("shell", "volc4", (-13, 59, -13), (26, 5, 26), "lava_rock"),
    ("shell", "groove_f1", (-2, 44, -24), (5, 5, 3), "lava_groove"),
    ("shell", "groove_f2", (-2, 49, -21), (5, 5, 3), "lava_groove"),
    ("shell", "groove_f3", (-2, 54, -18), (5, 5, 3), "lava_groove"),
    ("shell", "groove_f4", (-2, 59, -15), (5, 5, 3), "lava_groove"),
    ("shell", "groove_b1", (-2, 44, 21), (5, 5, 3), "lava_groove"),
    ("shell", "groove_b2", (-2, 49, 18), (5, 5, 3), "lava_groove"),
    ("shell", "groove_b3", (-2, 54, 15), (5, 5, 3), "lava_groove"),
    ("shell", "groove_b4", (-2, 59, 12), (5, 5, 3), "lava_groove"),
    ("shell", "groove_l1", (22, 44, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_l2", (19, 49, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_l3", (16, 54, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_l4", (13, 59, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_r1", (-25, 44, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_r2", (-22, 49, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_r3", (-19, 54, -2), (3, 5, 5), "lava_groove"),
    ("shell", "groove_r4", (-16, 59, -2), (3, 5, 5), "lava_groove"),
    ("shell", "spill_f", (-5, 74, -9), (10, 2, 5), "lava"),
    ("shell", "spill_b", (-5, 74, 4), (10, 2, 5), "lava"),
    ("whorl", "lava_pool", (-7, 65, -7), (14, 4, 14), "lava"),
    ("whorl", "lava_pool_hi", (-4, 69, -4), (8, 2, 8), "lava"),
    # 山体岩刺：四面各一根 + 顶上一根
    ("shell", "spike_f", (-3, 44, -26), (6, 16, 6), "spike"),
    ("shell", "spike_b", (-3, 44, 20), (6, 16, 6), "spike"),
    ("shell", "spike_l", (20, 44, -3), (6, 16, 6), "spike"),
    ("shell", "spike_r", (-26, 44, -3), (6, 16, 6), "spike"),
    ("shell", "spike_top", (-4, 74, -4), (8, 9, 8), "spike"),
    ("head", "crest", (-6, 44, -76), (12, 9, 14), "spike"),
    ("head", "eye_glow_l", (9, 32, -79), (4, 5, 3), "lava"),
    ("head", "eye_glow_r", (-13, 32, -79), (4, 5, 3), "lava"),
]

PHASES = {
    "phase1": {
        "cubes": CUBES_P1,
        "identifier": "geometry.tnc.volcanic_tortoise_phase1",
        "texture": "volcanic_tortoise_phase1.png",
        "glow": False,
    },
    "phase2": {
        "cubes": CUBES_P2,
        "identifier": "geometry.tnc.volcanic_tortoise_phase2",
        "texture": "volcanic_tortoise_phase2.png",
        "glow": True,
    },
}


# ---------------------------------------------------------------- UV 打包 --
class Packer:
    def __init__(self, size):
        self.size = size
        self.x = PAD
        self.y = PAD
        self.row = 0

    def alloc(self, w, h):
        if self.x + w + PAD > self.size:
            self.x = PAD
            self.y += self.row + PAD
            self.row = 0
        if self.y + h + PAD > self.size:
            raise RuntimeError("texture sheet is full: %dx%d" % (w, h))
        pos = (self.x, self.y)
        self.x += w + PAD
        self.row = max(self.row, h)
        return pos


def face_rects(u, v, w, h, d):
    """MC 标准 box-UV 布局：上排 up/down，下排 east/north/west/south。"""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


# ---------------------------------------------------------------- 贴图绘制 -
def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


HOT_KEYS = ("magma", "magma_hot", "core", "ember")


def glow_of(c):
    """亮到能自发光的颜色返回自身（写进发光层），其余返回 None。"""
    for k in HOT_KEYS:
        if c == ROCK[k]:
            return c
    return None


def paint_face(img, gimg, rect, theme, rng, glow, is_top, is_bottom):
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    base = ROCK[theme["base"]]
    grain = ROCK[theme["grain"]]
    dark = ROCK[theme["dark"]]
    accent = ROCK[theme["accent"]]
    px = img.load()
    gpx = gimg.load() if gimg is not None else None
    flat = theme.get("flat", False)
    strata = theme.get("strata", False)
    k_top = 1.06 if is_top else (0.94 if is_bottom else 1.0)
    for y in range(h):
        # 岩层：每 4 行压一条明暗带，关光影时也能看出层理，而不是一片糊黑
        band = 1.0
        if strata:
            band = 1.10 if (y // 4) % 2 == 0 else 0.90
        for x in range(w):
            if flat:
                # 岩浆面：竖向渐变 + 少量亮点，不铺噪点
                tt = y / max(1, h - 1)
                c = tuple(int(base[i] * (1.0 - tt) + grain[i] * tt) for i in range(3))
                if rng.random() < 0.10:
                    c = ROCK["core"] if glow else ROCK["magma_hot"]
            else:
                n = rng.random()
                if n < 0.58:
                    c = base
                elif n < 0.80:
                    c = grain
                elif n < 0.92:
                    c = dark
                else:
                    c = shade(base, 0.94 + 0.18 * rng.random())
                c = shade(c, k_top * band)
            # 面边缘只轻轻压暗一档，别把每块方砖描成黑框
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                c = shade(c, 0.86)
            px[x0 + x, y0 + y] = c + (255,)
            if gpx is not None:
                gv = c if flat else glow_of(c)
                gpx[x0 + x, y0 + y] = (gv if gv else (0, 0, 0)) + (255,)

    # 鳞片 / 岩板：每 6 像素一道暗线，靠格子撑出层次（不用纯黑）
    if theme.get("scales") and not flat and w > 8 and h > 8:
        for gx in range(6, w - 1, 6):
            for y in range(h):
                c = px[x0 + gx, y0 + y]
                px[x0 + gx, y0 + y] = shade(c[:3], 0.88) + (255,)
        for gy in range(6, h - 1, 6):
            for x in range(w):
                c = px[x0 + x, y0 + gy]
                px[x0 + x, y0 + gy] = shade(c[:3], 0.88) + (255,)

    # 裂缝：短折线，一阶段暗红、二阶段亮岩浆
    cracks = theme.get("crack", 0)
    if cracks and w > 6 and h > 6:
        for _ in range(cracks):
            cx = rng.randint(2, max(3, w - 3))
            cy = rng.randint(2, max(3, h - 3))
            length = rng.randint(3, max(4, min(w, h) // 2))
            for s in range(length):
                if not (0 <= cx < w and 0 <= cy < h):
                    break
                col = accent
                if glow and rng.random() < 0.45:
                    col = ROCK["magma"] if rng.random() < 0.6 else ROCK["magma_hot"]
                px[x0 + cx, y0 + cy] = col + (255,)
                if gpx is not None:
                    gv = glow_of(col)
                    gpx[x0 + cx, y0 + cy] = (gv if gv else (0, 0, 0)) + (255,)
                cx += rng.choice((0, 1))
                cy += rng.choice((-1, 0, 1)) if s % 2 else 0

    # 苔藓块：2x2 一簇，画进基础色（不是发光），关光影也看得见
    for _ in range(theme.get("moss", 0)):
        if w < 6 or h < 6:
            break
        mx = rng.randint(1, w - 4)
        my = rng.randint(1, h - 4)
        for dx in range(3):
            for dy in range(2):
                if rng.random() < 0.72:
                    px[x0 + mx + dx, y0 + my + dy] = ROCK["lichen"] + (255,)

    # 边角矿物 / 白点
    if not flat and w > 5 and h > 5:
        for _ in range(max(1, (w * h) // 260)):
            x = rng.randint(1, w - 2)
            y = rng.randint(1, h - 2)
            px[x0 + x, y0 + y] = (ROCK["lichen"] if rng.random() < 0.45 else ROCK["bone"]) + (255,)


def build_texture(phase):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    gimg = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 255))
    packer = Packer(SHEET)
    rng = random.Random(20261019 + (0 if phase["glow"] else 7))
    uvs = {}
    for bone, name, origin, size, theme_name in phase["cubes"]:
        w, h, d = size
        u, v = packer.alloc(2 * (w + d), d + h)
        uvs[name] = (u, v)
        theme = THEMES[theme_name]
        for face, rect in face_rects(u, v, w, h, d).items():
            paint_face(img, gimg, rect, theme, rng, phase["glow"],
                       face == "up", face == "down")
    return img, gimg, uvs


# ---------------------------------------------------------------- geo.json --
def build_geo(phase):
    img, gimg, uvs = build_texture(phase)
    bones = []
    for name, pivot, parent in BONES:
        cubes = []
        for bone, cname, origin, size, theme_name in phase["cubes"]:
            if bone != name:
                continue
            u, v = uvs[cname]
            cubes.append({
                "name": cname,
                "origin": list(origin),
                "size": list(size),
                "uv": [u, v],
            })
        entry = {"name": name, "pivot": list(pivot)}
        if parent:
            entry["parent"] = parent
        entry["cubes"] = cubes
        bones.append(entry)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": phase["identifier"],
                "texture_width": SHEET,
                "texture_height": SHEET,
                "visible_bounds_width": 13,
                "visible_bounds_height": 8,
                "visible_bounds_offset": [0, 4.5, 0],
            },
            "bones": bones,
        }],
    }
    return geo, img, gimg, uvs


# ------------------------------------------------------------ animation ----
def loop_anim(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


def once_anim(length, bones):
    return {"loop": False, "animation_length": length, "bones": bones}


def build_animations():
    legs_fwd = {"rotation": {"0": [24, 0, 0], "0.5": [-24, 0, 0], "1.0": [24, 0, 0]}}
    legs_bwd = {"rotation": {"0": [-24, 0, 0], "0.5": [24, 0, 0], "1.0": [-24, 0, 0]}}
    walk = loop_anim(1.0, {
        "leg_fl": legs_fwd, "leg_br": legs_fwd,
        "leg_fr": legs_bwd, "leg_bl": legs_bwd,
        "body": {"position": {"0": [0, 0, 0], "0.25": [0, 0.8, 0], "0.5": [0, 0, 0],
                              "0.75": [0, 0.8, 0], "1.0": [0, 0, 0]},
                 "rotation": {"0": [0, -2, 0], "0.5": [0, 2, 0], "1.0": [0, -2, 0]}},
        "neck": {"rotation": {"0": [3, 0, 0], "0.5": [-3, 0, 0], "1.0": [3, 0, 0]}},
        "tail": {"rotation": {"0": [0, -6, 0], "0.5": [0, 6, 0], "1.0": [0, -6, 0]}},
    })

    idle = loop_anim(2.6, {
        "body": {"position": {"0": [0, 0, 0], "1.3": [0, 0.7, 0], "2.6": [0, 0, 0]}},
        "shell": {"rotation": {"0": [0, 0, 0], "1.3": [0.8, 0, 0], "2.6": [0, 0, 0]}},
        "whorl": {"rotation": {"0": [0, 0, 0], "1.3": [0, 7, 0], "2.6": [0, 0, 0]}},
        "neck": {"rotation": {"0": [4, 0, 0], "1.3": [-2, 0, 0], "2.6": [4, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.9": [-5, 4, 0], "1.7": [2, -4, 0],
                              "2.6": [0, 0, 0]}},
        "tail": {"rotation": {"0": [0, -5, 0], "1.3": [0, 5, 0], "2.6": [0, -5, 0]}},
    })

    slam = once_anim(1.4, {
        "body": {"position": {"0": [0, 0, 0], "0.35": [0, 3.5, 0], "0.5": [0, -2.5, 0],
                              "0.75": [0, -2.5, 0], "1.4": [0, 0, 0]},
                 "rotation": {"0": [0, 0, 0], "0.35": [-10, 0, 0], "0.5": [6, 0, 0],
                              "0.75": [6, 0, 0], "1.4": [0, 0, 0]}},
        "neck": {"rotation": {"0": [0, 0, 0], "0.35": [-16, 0, 0], "0.5": [12, 0, 0],
                              "0.75": [12, 0, 0], "1.4": [0, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.35": [-18, 0, 0], "0.5": [14, 0, 0],
                              "0.75": [14, 0, 0], "1.4": [0, 0, 0]}},
        "leg_fl": {"rotation": {"0": [0, 0, 0], "0.35": [-18, 0, 0], "0.5": [10, 0, 0],
                                "1.4": [0, 0, 0]}},
        "leg_fr": {"rotation": {"0": [0, 0, 0], "0.35": [-18, 0, 0], "0.5": [10, 0, 0],
                                "1.4": [0, 0, 0]}},
        "leg_bl": {"rotation": {"0": [0, 0, 0], "0.5": [6, 0, 0], "1.4": [0, 0, 0]}},
        "leg_br": {"rotation": {"0": [0, 0, 0], "0.5": [6, 0, 0], "1.4": [0, 0, 0]}},
        "tail": {"rotation": {"0": [0, 0, 0], "0.35": [-14, 0, 0], "0.5": [8, 0, 0],
                              "1.4": [0, 0, 0]}},
    })

    transition = once_anim(2.0, {
        "body": {"position": {"0": [0, 0, 0], "0.5": [0, 4, 0], "1.1": [0, 4, 0],
                              "1.6": [0, 1.5, 0], "2.0": [0, 0, 0]},
                 "rotation": {"0": [0, 0, 0], "0.5": [-14, 0, 0], "1.1": [-14, 0, 0],
                              "1.6": [4, 0, 0], "2.0": [0, 0, 0]}},
        "shell": {"rotation": {"0": [0, 0, 0], "0.5": [-8, 0, 0], "1.1": [-8, 0, 0],
                               "2.0": [0, 0, 0]}},
        "whorl": {"rotation": {"0": [0, 0, 0], "0.9": [0, 180, 0], "1.6": [0, 320, 0],
                               "2.0": [0, 360, 0]}},
        "neck": {"rotation": {"0": [0, 0, 0], "0.4": [-26, 0, 0], "1.2": [-26, 0, 0],
                              "2.0": [0, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.4": [-12, 0, 0], "1.2": [-12, 0, 0],
                              "1.7": [10, 0, 0], "2.0": [0, 0, 0]}},
        "leg_fl": {"rotation": {"0": [0, 0, 0], "0.6": [-12, 0, 0], "1.4": [-12, 0, 0],
                                "2.0": [0, 0, 0]}},
        "leg_fr": {"rotation": {"0": [0, 0, 0], "0.6": [-12, 0, 0], "1.4": [-12, 0, 0],
                                "2.0": [0, 0, 0]}},
    })

    death = once_anim(1.8, {
        "body": {"position": {"0": [0, 0, 0], "0.5": [0, -6, 0], "1.2": [0, -11, 0],
                              "1.8": [0, -12, 0]},
                 "rotation": {"0": [0, 0, 0], "0.5": [8, 0, 6], "1.2": [16, 0, 10],
                              "1.8": [18, 0, 11]}},
        "neck": {"rotation": {"0": [0, 0, 0], "0.6": [16, 0, 0], "1.8": [26, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.6": [12, 0, 0], "1.8": [20, 0, 0]}},
        "leg_fl": {"rotation": {"0": [0, 0, 0], "1.8": [-34, 0, 14]}},
        "leg_fr": {"rotation": {"0": [0, 0, 0], "1.8": [-34, 0, -14]}},
        "leg_bl": {"rotation": {"0": [0, 0, 0], "1.8": [26, 0, 10]}},
        "leg_br": {"rotation": {"0": [0, 0, 0], "1.8": [26, 0, -10]}},
        "tail": {"rotation": {"0": [0, 0, 0], "1.8": [-18, 0, 0]}},
    })

    bite = once_anim(0.6, {
        "body": {"position": {"0": [0, 0, 0], "0.22": [0, 0, 1.5], "0.36": [0, 0, -1.5],
                              "0.6": [0, 0, 0]}},
        "neck": {"rotation": {"0": [0, 0, 0], "0.22": [16, 0, 0], "0.36": [-22, 0, 0],
                              "0.6": [0, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.22": [10, 0, 0], "0.36": [-30, 0, 0],
                              "0.6": [0, 0, 0]}},
        "leg_fl": {"rotation": {"0": [0, 0, 0], "0.36": [8, 0, 0], "0.6": [0, 0, 0]}},
        "leg_fr": {"rotation": {"0": [0, 0, 0], "0.36": [8, 0, 0], "0.6": [0, 0, 0]}},
        "tail": {"rotation": {"0": [0, 0, 0], "0.36": [10, 0, 0], "0.6": [0, 0, 0]}},
    })

    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": idle,
            "walk": walk,
            "slam": slam,
            "bite": bite,
            "transition": transition,
            "death": death,
        },
    }


# --------------------------------------------------------------- bbmodel ----
def build_bbmodel(phase, png_bytes, geo):
    """生成可直接用 Blockbench 打开继续编辑的工程（贴图内嵌，几何与 geo.json 一致）。"""
    import uuid

    elements = []
    groups = {}
    for bone, name, origin, size, theme_name in phase["cubes"]:
        u, v = phase["uvs"][name]
        ox, oy, oz = origin
        sx, sy, sz = size
        el_uuid = str(uuid.uuid4())
        faces = {}
        for face, rect in face_rects(u, v, sx, sy, sz).items():
            ru, rv, rw, rh = rect
            faces[face] = {"uv": [ru, rv, ru + rw, rv + rh], "texture": 0}
        elements.append({
            "name": name,
            "box_uv": True,
            "rescale": False,
            "locked": False,
            "light_emission": 10 if phase["glow"] and THEMES[theme_name].get("flat") else 0,
            "from": [ox, oy, oz],
            "to": [ox + sx, oy + sy, oz + sz],
            "autouv": 0,
            "color": 0,
            "origin": [0, 0, 0],
            "faces": faces,
            "type": "cube",
            "uuid": el_uuid,
        })
        groups.setdefault(bone, []).append(el_uuid)

    pivots = {name: pivot for name, pivot, _ in BONES}
    parents = {name: parent for name, _, parent in BONES}
    outliner = []
    for bone, _, _ in BONES:
        if parents[bone] is None:
            continue
        group = {
            "name": bone,
            "origin": list(pivots[bone]),
            "color": 0,
            "uuid": str(uuid.uuid4()),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "children": list(groups.get(bone, [])),
        }
        outliner.append(group)

    # 把子骨骼分组嵌进父分组
    by_name = {g["name"]: g for g in outliner}
    roots = []
    for bone, _, parent in BONES:
        if parent is None or parent == "root":
            continue
        node = by_name.get(bone)
        if node is None:
            continue
        if parent in by_name:
            by_name[parent]["children"].append(node)
        else:
            roots.append(node)

    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "free",
            "box_uv": True,
        },
        "name": "volcanic_tortoise_" + ("phase2" if phase["glow"] else "phase1"),
        "model_identifier": phase["identifier"],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": SHEET, "height": SHEET},
        "elements": elements,
        "outliner": roots,
        "textures": [{
            "path": "",
            "name": phase["texture"],
            "folder": "entity",
            "namespace": "tnc",
            "id": "0",
            "width": SHEET,
            "height": SHEET,
            "uv_width": SHEET,
            "uv_height": SHEET,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": False,
            "uuid": str(uuid.uuid4()),
            "relative_path": "",
            "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii"),
        }],
        "animations": [],
    }


# ------------------------------------------------------------------- main --
def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


def main():
    os.makedirs(ART, exist_ok=True)
    for key, phase in PHASES.items():
        geo, img, gimg, uvs = build_geo(phase)
        phase["uvs"] = uvs
        tex_path = os.path.join(ASSETS, "textures", "entity", phase["texture"])
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        img.save(tex_path, "PNG", optimize=True)
        glow_path = os.path.join(ASSETS, "textures", "entity",
                                 phase["texture"].replace(".png", "_glow.png"))
        gimg.save(glow_path, "PNG", optimize=True)
        geo_path = os.path.join(ASSETS, "geo", "entity",
                                "volcanic_tortoise_" + key + ".geo.json")
        write_json(geo_path, geo)
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        bb = build_bbmodel(phase, buf.getvalue(), geo)
        write_json(os.path.join(ART, "volcanic_tortoise_" + key + ".bbmodel"), bb)
        print("wrote %s (%d cubes, texture %d bytes)"
              % (key, len(phase["cubes"]), os.path.getsize(tex_path)))

    anim_path = os.path.join(ASSETS, "animations", "entity",
                             "volcanic_tortoise.animation.json")
    write_json(anim_path, build_animations())
    print("wrote animations:", anim_path)


if __name__ == "__main__":
    main()
