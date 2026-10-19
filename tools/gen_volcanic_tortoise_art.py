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

SHEET = 512
PAD = 2

# ---------------------------------------------------------------- 调色板 ----
# 一阶段：冷却下来的火山岩（黑玄武岩 + 灰岩 + 暗红裂缝 + 少量矿物/苔色）
# 二阶段：同一套岩体，但裂缝里灌满岩浆（亮橙 + 白热芯 + 黑曜石）
ROCK = {
    "basalt": (40, 36, 33),
    "rock": (66, 61, 55),
    "rock_lt": (92, 86, 77),
    "ash": (120, 112, 102),
    "mustard": (150, 128, 88),
    "belly": (78, 70, 62),
    "bone": (198, 190, 172),
    "lichen": (92, 108, 70),
    "obsidian": (26, 22, 30),
    "magma_dim": (128, 44, 20),
    "magma": (240, 104, 22),
    "magma_hot": (255, 188, 74),
    "core": (255, 242, 190),
    "ember": (255, 70, 14),
}

# 每个立方体用一个"主题"，决定六个面的底色与花纹
THEMES = {
    "shell": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma_dim", "crack": 3},
    "shell_hi": {"base": "rock_lt", "grain": "ash", "dark": "rock", "accent": "magma_dim", "crack": 2},
    "shell_low": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim", "crack": 2},
    "whorl": {"base": "rock_lt", "grain": "mustard", "dark": "rock", "accent": "magma_dim", "crack": 3},
    "belly": {"base": "belly", "grain": "ash", "dark": "basalt", "accent": "bone", "crack": 1},
    "leg": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim", "crack": 2},
    "foot": {"base": "rock", "grain": "ash", "dark": "basalt", "accent": "bone", "crack": 1},
    "head": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma_dim", "crack": 2},
    "jaw": {"base": "ash", "grain": "bone", "dark": "rock", "accent": "obsidian", "crack": 0},
    "eye": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core", "crack": 0, "flat": True},
    "tail": {"base": "basalt", "grain": "rock", "dark": "obsidian", "accent": "magma_dim", "crack": 1},
    # ---- 二阶段专用 ----
    "lava": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core", "crack": 0, "flat": True},
    "lava_rock": {"base": "obsidian", "grain": "basalt", "dark": "obsidian", "accent": "magma", "crack": 5},
    "spike": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma", "crack": 4},
}

# ---------------------------------------------------------------- 骨骼 ----
# (名字, pivot, 父骨骼)
BONES = [
    ("root", (0, 0, 0), None),
    ("body", (0, 18, 0), "root"),
    ("shell", (0, 24, 0), "body"),
    ("whorl", (0, 37, 0), "shell"),
    ("neck", (0, 18, -22), "body"),
    ("head", (0, 19, -34), "neck"),
    ("tail", (0, 15, 22), "body"),
    ("leg_fl", (22, 18, -20), "body"),
    ("leg_fr", (-22, 18, -20), "body"),
    ("leg_bl", (22, 18, 20), "body"),
    ("leg_br", (-22, 18, 20), "body"),
]

# (骨骼, 名字, 最小角 origin, 尺寸 size, 主题)
# 基准：1 单位 = 1/16 格；地面 y=0；前方是 -Z。
# 轮廓大约 宽 60 (3.75 格) × 高 44 (2.75 格) × 长 88 (5.5 格)。
# 层次（自下而上）：脚掌 → 腿 → 腹甲 → 壳缘(带缘盾) → 穹顶两级 → 壳顶螺旋。
CUBES_P1 = [
    # --- 腹甲 ---
    ("body", "belly", (-17, 14, -22), (34, 8, 44), "belly"),
    # --- 龟壳：壳缘 → 两级穹顶（每级都内收，方块风靠台阶撑轮廓）---
    ("shell", "rim", (-26, 22, -28), (52, 5, 56), "shell_low"),
    ("shell", "dome1", (-22, 27, -24), (44, 5, 48), "shell"),
    ("shell", "dome2", (-16, 32, -18), (32, 5, 36), "shell_hi"),
    # --- 缘盾：贴在壳缘外侧的扁方块，撑开剪影（不往上长）---
    ("shell", "scute_f", (-9, 22, -33), (18, 5, 6), "shell_low"),
    ("shell", "scute_b", (-9, 22, 28), (18, 5, 6), "shell_low"),
    ("shell", "scute_l", (24, 22, -11), (6, 5, 22), "shell_low"),
    ("shell", "scute_r", (-30, 22, -11), (6, 5, 22), "shell_low"),
    ("shell", "scute_fl", (18, 22, -30), (8, 5, 8), "shell_low"),
    ("shell", "scute_fr", (-26, 22, -30), (8, 5, 8), "shell_low"),
    ("shell", "scute_bl", (18, 22, 26), (8, 5, 8), "shell_low"),
    ("shell", "scute_br", (-26, 22, 26), (8, 5, 8), "shell_low"),
    # --- 壳顶螺旋（参考 GLB 的漩涡状壳心，越往上越小、越薄）---
    ("whorl", "whorl1", (-13, 37, -15), (26, 3, 30), "whorl"),
    ("whorl", "whorl2", (-8, 40, -10), (16, 2, 20), "whorl"),
    ("whorl", "whorl3", (-3, 42, -5), (6, 2, 10), "shell_hi"),
    ("whorl", "whorl_r1", (9, 37, -4), (7, 3, 12), "shell_hi"),
    ("whorl", "whorl_r2", (-16, 37, -8), (7, 3, 12), "shell_hi"),
    ("whorl", "whorl_r3", (-5, 37, 10), (12, 3, 7), "shell_hi"),
    ("whorl", "whorl_r4", (-5, 37, -18), (12, 3, 7), "shell_hi"),
    # --- 脖子 / 头 ---
    ("neck", "neck", (-9, 15, -36), (18, 10, 14), "shell_low"),
    ("head", "head", (-10, 15, -52), (20, 15, 16), "head"),
    ("head", "snout", (-7, 13, -57), (14, 6, 6), "jaw"),
    ("head", "brow", (-11, 28, -50), (22, 3, 9), "head"),
    ("head", "eye_l", (4, 21, -53), (4, 4, 2), "eye"),
    ("head", "eye_r", (-8, 21, -53), (4, 4, 2), "eye"),
    # --- 四条腿（上段藏在壳缘下，脚掌落地）---
    ("leg_fl", "thigh_fl", (16, 4, -24), (13, 13, 13), "leg"),
    ("leg_fl", "foot_fl", (15, 0, -25), (15, 5, 15), "foot"),
    ("leg_fr", "thigh_fr", (-29, 4, -24), (13, 13, 13), "leg"),
    ("leg_fr", "foot_fr", (-30, 0, -25), (15, 5, 15), "foot"),
    ("leg_bl", "thigh_bl", (16, 4, 12), (13, 13, 13), "leg"),
    ("leg_bl", "foot_bl", (15, 0, 11), (15, 5, 15), "foot"),
    ("leg_br", "thigh_br", (-29, 4, 12), (13, 13, 13), "leg"),
    ("leg_br", "foot_br", (-30, 0, 11), (15, 5, 15), "foot"),
    # --- 尾巴 ---
    ("tail", "tail1", (-5, 14, 22), (10, 7, 10), "tail"),
    ("tail", "tail2", (-3, 10, 32), (6, 5, 8), "tail"),
]

# 二阶段：同一副骨架，壳面换成熔岩岩板、螺心被岩浆顶开、壳缘与头顶长出岩刺
CUBES_P2 = [c for c in CUBES_P1
            if c[1] not in ("whorl1", "whorl2", "whorl3", "dome2", "brow")]
CUBES_P2 += [
    ("shell", "dome2b", (-16, 32, -18), (32, 5, 36), "lava_rock"),
    ("whorl", "whorl1b", (-13, 37, -15), (26, 3, 30), "lava_rock"),
    ("whorl", "whorl2b", (-8, 40, -10), (16, 3, 20), "lava_rock"),
    ("whorl", "whorl3b", (-4, 43, -6), (8, 3, 12), "lava"),
    ("shell", "spike_fl", (19, 27, -27), (7, 12, 7), "spike"),
    ("shell", "spike_fr", (-26, 27, -27), (7, 12, 7), "spike"),
    ("shell", "spike_bl", (19, 27, 20), (7, 12, 7), "spike"),
    ("shell", "spike_br", (-26, 27, 20), (7, 12, 7), "spike"),
    ("shell", "spike_c", (-4, 43, 4), (8, 10, 8), "spike"),
    ("shell", "lava_pool_a", (-14, 37, -13), (13, 2, 12), "lava"),
    ("shell", "lava_pool_b", (1, 38, 0), (10, 2, 9), "lava"),
    ("shell", "lava_pool_c", (-11, 40, 10), (9, 2, 8), "lava"),
    ("head", "crest", (-5, 29, -52), (10, 6, 10), "spike"),
    ("head", "eye_glow_l", (4, 21, -54), (4, 4, 2), "lava"),
    ("head", "eye_glow_r", (-8, 21, -54), (4, 4, 2), "lava"),
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


def paint_face(img, rect, theme, rng, glow, is_top, is_bottom):
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    base = ROCK[theme["base"]]
    grain = ROCK[theme["grain"]]
    dark = ROCK[theme["dark"]]
    accent = ROCK[theme["accent"]]
    px = img.load()
    flat = theme.get("flat", False)
    k_top = 1.06 if is_top else (0.86 if is_bottom else 1.0)
    for y in range(h):
        for x in range(w):
            if flat:
                # 岩浆面：竖向渐变 + 少量亮点，不铺噪点
                t = y / max(1, h - 1)
                c = tuple(int(base[i] * (1.0 - t) + grain[i] * t) for i in range(3))
                if rng.random() < 0.10:
                    c = ROCK["core"] if glow else ROCK["magma_hot"]
            else:
                n = rng.random()
                if n < 0.62:
                    c = base
                elif n < 0.82:
                    c = grain
                elif n < 0.93:
                    c = dark
                else:
                    c = shade(base, 0.9 + 0.2 * rng.random())
                c = shade(c, k_top)
            # 面边缘压一像素暗线，方块感更硬
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                c = shade(c, 0.72)
            px[x0 + x, y0 + y] = c + (255,)

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
                cx += rng.choice((0, 1))
                cy += rng.choice((-1, 0, 1)) if s % 2 else 0

    # 边角矿物 / 苔点
    if not flat and w > 5 and h > 5:
        for _ in range(max(1, (w * h) // 220)):
            x = rng.randint(1, w - 2)
            y = rng.randint(1, h - 2)
            px[x0 + x, y0 + y] = (ROCK["lichen"] if rng.random() < 0.6 else ROCK["bone"]) + (255,)


def build_texture(phase):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    packer = Packer(SHEET)
    rng = random.Random(20261019 + (0 if phase["glow"] else 7))
    uvs = {}
    for bone, name, origin, size, theme_name in phase["cubes"]:
        w, h, d = size
        u, v = packer.alloc(2 * (w + d), d + h)
        uvs[name] = (u, v)
        theme = THEMES[theme_name]
        for face, rect in face_rects(u, v, w, h, d).items():
            paint_face(img, rect, theme, rng, phase["glow"],
                       face == "up", face == "down")
    return img, uvs


# ---------------------------------------------------------------- geo.json --
def build_geo(phase):
    img, uvs = build_texture(phase)
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
                "visible_bounds_width": 7,
                "visible_bounds_height": 5,
                "visible_bounds_offset": [0, 2, 0],
            },
            "bones": bones,
        }],
    }
    return geo, img, uvs


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
        geo, img, uvs = build_geo(phase)
        phase["uvs"] = uvs
        tex_path = os.path.join(ASSETS, "textures", "entity", phase["texture"])
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        img.save(tex_path, "PNG", optimize=True)
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
