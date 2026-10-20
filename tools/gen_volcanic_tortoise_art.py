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
    # 二阶段山体：仍是可辨认的深灰/灰褐岩石（不是黑曜石黑），裂缝才用岩浆色
    "lava_rock": {"base": "rock", "grain": "rock_lt", "dark": "basalt",
                  "accent": "magma", "crack": 5, "strata": True, "scales": True},
    "lava_groove": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core",
                    "crack": 0},
    "spike": {"base": "rock", "grain": "rock_lt", "dark": "basalt", "accent": "magma",
              "crack": 4},
    # ---- 二阶段专用（追加） ----
    "p2_rock": {"base": "basalt", "grain": "rock", "dark": "obsidian",
                "accent": "magma_dim", "crack": 4, "strata": True, "scales": True},
    "p2_shell": {"base": "rock", "grain": "rock_lt", "dark": "basalt",
                 "accent": "mustard", "crack": 3, "strata": True, "scales": True},
    "lava_hot": {"base": "magma_hot", "grain": "core", "dark": "magma", "accent": "core",
                 "crack": 0, "flat": True},
    "lava_mid": {"base": "magma", "grain": "magma_hot", "dark": "ember", "accent": "core",
                 "crack": 0, "flat": True},
    "lava_dim": {"base": "magma_dim", "grain": "ember", "dark": "obsidian", "accent": "magma",
                 "crack": 2, "flat": True},
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

# --- 火山：不再是一圈圈同心方台，改成不对称的岩块堆 ---
# 设计要点（对应作者 2026-10-19 的反馈）：
#   * 正面(-Z)/背面(+Z)/左右(+X/-X) 坡度不同：背面 bt_b 高、+X 陡壁、-X 低台、正面有突出山嘴
#   * 岩块高低错落、部分外挑（slab1 / ldg1 / ldg2）形成断层与岩台，水平边缘不环绕一整圈
#   * 火山口口沿高低不一（78/77/76/75/74），正面留一道缺口，岩浆面 y=71 低于全部口沿
#   * 两条主熔岩通道（正面 ch_a*、+X 侧 ch_c*）+ 一条支流（ch_b*），宽窄不一、被 occl* 岩块遮住
VOLCANO_P1 = [
    # 山脚：四个方向伸出长度/高度都不同，并且包住龟壳穹顶（不再是"壳上摆一座独立建筑"）
    ("shell", "ap_f", (-30, 41, -34), (60, 7, 22), "shell_low"),
    ("shell", "ap_b", (-28, 41, 10), (56, 8, 20), "shell_low"),
    ("shell", "ap_x", (-36, 41, -16), (14, 9, 34), "shell_low"),
    ("shell", "ap_y", (20, 41, -18), (14, 6, 36), "shell_low"),
    # 主山体：每层高度(9/4/6/9/7/5)与内缩量(6/2/4/3)都不一样，且逐层偏心
    ("shell", "m_low", (-24, 47, -24), (48, 9, 46), "shell_low"),
    ("shell", "m_mid", (-20, 56, -18), (38, 4, 34), "shell"),
    ("shell", "m_mid2", (-26, 54, -26), (20, 6, 14), "shell_low"),
    ("shell", "m_up", (-16, 60, -14), (30, 9, 26), "shell"),
    ("shell", "m_up2", (2, 58, -24), (12, 7, 14), "shell_low"),
    ("shell", "m_top", (-13, 67, -10), (24, 5, 24), "shell"),
    # 四向各不相同：正面山嘴、背面高扶壁、+X 陡壁、-X 低台
    ("shell", "bt_f", (-16, 44, -30), (20, 9, 8), "shell_low"),
    ("shell", "bt_f2", (2, 46, -28), (9, 7, 7), "shell_low"),
    ("shell", "bt_b", (-10, 52, 18), (18, 11, 8), "shell"),
    ("shell", "bt_x", (18, 45, -10), (9, 12, 18), "shell_low"),
    ("shell", "bt_y", (-30, 48, -4), (8, 6, 20), "shell_low"),
    # 岩台 / 内凹台阶 / 外挑岩板（外挑让轮廓出现断层）
    ("shell", "ldg1", (-30, 55, 4), (12, 4, 12), "shell"),
    ("shell", "ldg2", (6, 58, -20), (10, 4, 9), "shell"),
    ("shell", "step1", (-18, 64, -8), (7, 5, 9), "shell"),
    ("shell", "step2", (14, 62, 2), (6, 4, 8), "shell"),
    ("shell", "slab1", (-8, 64, -18), (12, 3, 8), "shell"),
    # 火山口口沿：五段，顶面 78/77/76/75/75 高低不一，正面 x -2..2 留缺口
    ("shell", "rim_b", (-13, 71, 6), (26, 7, 8), "shell"),
    ("shell", "rim_x", (-17, 71, -8), (7, 6, 14), "shell"),
    ("shell", "rim_y", (6, 71, -9), (7, 4, 14), "shell"),
    ("shell", "rim_f1", (-13, 71, -14), (11, 5, 7), "shell_hi"),
    ("shell", "rim_f2", (2, 71, -15), (8, 4, 7), "shell_hi"),
    # 口内岩浆：y 67..71，比最低口沿(75)低 4 格，侧面看不到、俯视能看到
    ("whorl", "lava_pool", (-11, 67, -7), (17, 4, 13), "lava"),
    # 正面主通道：上窄下宽
    ("shell", "ch_a1", (-1, 64, -17), (4, 8, 7), "groove"),
    ("shell", "ch_a2", (-1, 57, -21), (4, 8, 6), "groove"),
    ("shell", "ch_a3", (-2, 50, -25), (5, 8, 6), "groove"),
    ("shell", "ch_a4", (-3, 44, -29), (6, 7, 7), "groove"),
    ("shell", "spill_n", (-1, 70, -16), (4, 2, 4), "groove"),
    # 支流：更细，半途分叉
    ("shell", "ch_b1", (5, 58, -19), (3, 6, 6), "groove"),
    ("shell", "ch_b2", (7, 52, -23), (3, 6, 6), "groove"),
    ("shell", "ch_b3", (9, 46, -26), (3, 6, 6), "groove"),
    # +X 侧通道：最细最长
    ("shell", "ch_c1", (10, 66, -6), (3, 5, 8), "groove"),
    ("shell", "ch_c2", (12, 60, -9), (3, 6, 7), "groove"),
    ("shell", "ch_c3", (13, 54, -12), (3, 6, 7), "groove"),
    ("shell", "ch_c4", (14, 48, -15), (3, 6, 6), "groove"),
    # 挡住通道的岩块：让岩浆断断续续
    ("shell", "occl1", (-8, 56, -22), (8, 6, 6), "shell"),
    ("shell", "occl2", (2, 50, -24), (6, 5, 5), "shell"),
]

CUBES_P1 = CUBES_P1 + VOLCANO_P1

# ==== 二阶段：崩裂的火山 / 掀开的壳侧岩甲 / 前压的冲撞姿态 ====
# 作者 2026-10-20 要求：关光影、关粒子、关发光时也要一眼分出一二阶段。
# 所以二阶段不再"由一阶段逐条派生"，而是自成一套几何：
#   * 山体去阶梯化：三大块偏心堆叠 + 口沿炸开（正面右侧整段缺失）+ 熔岩核心外露
#   * 5 组断裂岩脊，带 rotation，像被掀起的岩层（与主体相连，不是浮空碎石）
#   * 壳侧前后各一块大岩甲，绕根部向外掀起（独立骨骼 plate_l / plate_r，供转阶段动画）
#   * 头：眉脊下压压住眼睛、眼睛变窄并内嵌、上下颌分开留嘴缝、下颌三颗粗獠牙
#   * 站姿：前肢更粗并外撑、前肩压低、火山抬到最高 ⇒ 前低后高的剪影
#   * 熔岩亮度分三档：火山口/主裂口 lava_hot、通道 lava_mid、末端小裂纹 lava_dim
# 全部按 P2_SCALE 放大（相对一阶段 1.2 倍，只乘这一次，不在旧缩放上叠乘）。
P2_SCALE = 1.2

BONES_P2 = [
    ("root", (0, 0, 0), None),
    ("body", (0, 20, 0), "root"),
    ("shell", (0, 31, 0), "body"),
    ("whorl", (0, 70, 0), "shell"),
    ("neck", (0, 20, -36), "body"),
    ("head", (0, 22, -58), "neck"),
    ("jaw", (0, 16, -58), "head"),
    ("tail", (0, 18, 32), "body"),
    ("leg_fl", (34, 18, -32), "body"),
    ("leg_fr", (-34, 18, -32), "body"),
    ("leg_bl", (33, 18, 24), "body"),
    ("leg_br", (-33, 18, 24), "body"),
    ("plate_l", (38, 27, 0), "shell"),
    ("plate_r", (-38, 27, 0), "shell"),
]

# 名字 -> (旋转轴, 角度(度), 旋转中心)
ROT_P2 = {
    "plate_l_f": ("z", -24, (36, 31, -21)),
    "plate_l_b": ("z", -17, (36, 31, 20)),
    "plate_r_f": ("z", 24, (-36, 31, -21)),
    "plate_r_b": ("z", 17, (-36, 31, 20)),
    "rg1": ("z", 17, (-16, 74, -12)),
    "rg2": ("z", -13, (13, 76, -8)),
    "rg3": ("x", 11, (-2, 81, 15)),
    "rg4": ("x", -9, (-1, 90, -8)),
    "rg5": ("z", -9, (19, 62, 8)),
    "brow": ("x", -11, (0, 44, -77)),
    "lojaw": ("x", 9, (0, 16, -85)),
}

P2_CUBES = [
    # ---------------- 躯干：加宽加厚，前胸与胯部各垫一层 ----------------
    ("body", "belly", (-28, 13, -34), (56, 13, 68), "belly"),
    ("body", "chest", (-25, 20, -42), (50, 10, 16), "p2_rock"),
    ("body", "hip", (-23, 20, 26), (46, 10, 14), "p2_rock"),

    # ---------------- 壳：只留一级穹顶，边缘交给会掀起的岩甲 ----------------
    ("shell", "rim_core", (-38, 24, -32), (76, 9, 64), "shell_low"),
    ("shell", "rim_f", (-28, 24, -40), (56, 9, 9), "shell_low"),
    ("shell", "rim_b", (-26, 24, 31), (52, 10, 9), "shell_low"),
    ("shell", "dome1", (-32, 33, -32), (64, 7, 64), "shell_hi"),

    # ---------------- 壳侧大岩甲：向外掀起（独立骨骼） ----------------
    ("plate_l", "plate_l_f", (34, 27, -34), (17, 9, 26), "p2_shell"),
    ("plate_l", "plate_l_b", (34, 27, 8), (16, 9, 24), "p2_shell"),
    ("plate_r", "plate_r_f", (-51, 27, -34), (17, 9, 26), "p2_shell"),
    ("plate_r", "plate_r_b", (-50, 27, 8), (16, 9, 24), "p2_shell"),
    # 岩甲被撑开后的熔岩裂隙（夹在壳与岩甲之间，最深最亮）
    ("shell", "gap_l1", (33, 25, -30), (5, 8, 22), "lava_mid"),
    ("shell", "gap_l2", (33, 25, 4), (5, 8, 20), "lava_mid"),
    ("shell", "gap_r1", (-38, 25, -30), (5, 8, 22), "lava_mid"),
    ("shell", "gap_r2", (-38, 25, 4), (5, 8, 20), "lava_mid"),

    # ---------------- 火山：三大块偏心堆叠（不是同心台阶） ----------------
    ("shell", "vb_1", (-22, 40, -22), (44, 12, 40), "p2_rock"),
    ("shell", "vb_2", (-16, 40, -32), (22, 9, 12), "p2_rock"),
    ("shell", "vb_3", (13, 40, -6), (11, 9, 22), "p2_rock"),
    ("shell", "vm_1", (-16, 52, -16), (32, 14, 30), "p2_rock"),
    ("shell", "vm_2", (-6, 52, -28), (15, 9, 11), "p2_rock"),
    ("shell", "vm_3", (11, 49, 4), (10, 8, 13), "p2_rock"),
    ("shell", "vu_1", (-11, 66, -11), (22, 14, 22), "p2_rock"),
    ("shell", "vu_2", (2, 64, -21), (10, 8, 10), "p2_rock"),

    ("shell", "vw_1", (-7, 80, -7), (14, 10, 14), "p2_rock"),

    # ---------------- 火山口：口沿炸开，正面右侧整段缺失 ----------------
    ("shell", "cw_b", (-9, 90, 2), (18, 13, 9), "p2_rock"),
    ("shell", "cw_l", (-10, 91, -6), (7, 11, 12), "p2_rock"),
    ("shell", "cw_r", (3, 88, -5), (7, 15, 13), "p2_rock"),
    ("shell", "cw_f", (-9, 90, -9), (7, 9, 6), "p2_rock"),
    # 缺口处往外翻的一块残沿（不对齐任何一侧）
    ("shell", "cw_lip", (-1, 94, -10), (7, 5, 6), "p2_shell"),

    # ---------------- 火山口内部：熔岩核心（最高一档亮度） ----------------
    ("whorl", "lava_core", (-5, 86, -5), (11, 10, 11), "lava_hot"),
    ("whorl", "lava_deep", (-3, 83, -3), (8, 4, 8), "lava_mid"),
    ("whorl", "lava_spill", (-2, 90, -10), (6, 3, 6), "lava_hot"),

    # ---------------- 5 组断裂岩脊（撕裂、掀起的岩层） ----------------
    ("shell", "rg1", (-24, 66, -19), (16, 16, 15), "p2_rock"),
    ("shell", "rg2", (7, 68, -15), (13, 17, 14), "p2_rock"),
    ("shell", "rg3", (-9, 72, 8), (14, 18, 14), "p2_rock"),
    ("shell", "rg4", (-6, 84, -14), (10, 12, 11), "p2_rock"),
    ("shell", "rg5", (13, 56, 2), (12, 13, 13), "p2_rock"),

    # ---------------- 山体两侧大裂口：侧面也能看见壳里有熔岩 ----------------
    ("shell", "fis_r_rock", (23, 42, -3), (7, 15, 17), "p2_rock"),
    ("whorl", "fis_r_lava", (24, 42, -2), (5, 13, 15), "lava_mid"),
    ("shell", "fis_l_rock", (-31, 44, 0), (7, 13, 15), "p2_rock"),
    ("whorl", "fis_l_lava", (-30, 44, 1), (5, 11, 13), "lava_mid"),

    # ---------------- 熔岩通道：三组粗细不一，非对称 ----------------
    ("shell", "ch1", (-2, 66, -15), (8, 20, 8), "lava_mid"),
    ("shell", "ch2", (-2, 54, -21), (9, 13, 8), "lava_mid"),
    ("shell", "ch3", (-3, 44, -28), (10, 10, 8), "lava_mid"),
    ("shell", "ch4", (-5, 41, -33), (12, 4, 7), "lava_dim"),
    ("shell", "chb1", (9, 56, -12), (5, 9, 6), "lava_dim"),
    ("shell", "chb2", (11, 48, -16), (5, 9, 6), "lava_dim"),
    ("shell", "chb3", (13, 41, -20), (5, 8, 6), "lava_dim"),
    ("shell", "chc1", (17, 52, -6), (3, 8, 6), "lava_dim"),
    ("shell", "chc2", (19, 45, -9), (3, 7, 6), "lava_dim"),
    # 挡住通道的岩块：让岩浆断断续续
    ("shell", "oc1", (-6, 54, -24), (8, 6, 6), "p2_rock"),
    ("shell", "oc2", (4, 47, -26), (6, 5, 5), "p2_rock"),

    # ---------------- 颈 / 头：前伸压低、眉脊下压、上下颌分开 ----------------
    ("neck", "neck", (-15, 18, -56), (30, 20, 22), "p2_rock"),
    ("neck", "neck_pl1", (-18, 30, -54), (36, 6, 12), "p2_shell"),
    ("neck", "neck_pl2", (-18, 22, -56), (36, 6, 12), "p2_rock"),
    ("neck", "neck_lava", (-7, 20, -55), (14, 3, 4), "lava_dim"),
    ("head", "head", (-17, 18, -82), (34, 26, 26), "p2_rock"),
    ("head", "brow", (-19, 38, -86), (38, 6, 18), "p2_shell"),
    ("head", "eye_l", (6, 31, -84), (5, 2, 3), "eye"),
    ("head", "eye_r", (-11, 31, -84), (5, 2, 3), "eye"),
    ("head", "cheek_l", (10, 23, -80), (7, 9, 10), "p2_rock"),
    ("head", "cheek_r", (-17, 23, -80), (7, 9, 10), "p2_rock"),
    ("head", "upjaw", (-14, 16, -92), (28, 10, 14), "p2_rock"),
    ("head", "mouth_lava", (-8, 15, -90), (16, 3, 5), "lava_dim"),
    ("jaw", "lojaw", (-13, 7, -92), (26, 9, 14), "p2_rock"),
    ("jaw", "fang1", (-9, 13, -94), (4, 7, 4), "jaw"),
    ("jaw", "fang2", (-2, 13, -94), (4, 7, 4), "jaw"),
    ("jaw", "fang3", (5, 13, -94), (4, 7, 4), "jaw"),

    # ---------------- 尾：更粗更短 ----------------
    ("tail", "tail1", (-8, 14, 32), (16, 12, 14), "p2_rock"),
    ("tail", "tail2", (-5, 8, 44), (10, 9, 12), "p2_rock"),

    # ---------------- 前肢：更粗、外撑、分段、宽脚掌带岩爪 ----------------
    ("leg_fl", "sh_fl", (20, 22, -42), (28, 8, 22), "p2_shell"),
    ("leg_fl", "up_fl", (24, 10, -38), (22, 15, 20), "p2_rock"),
    ("leg_fl", "lo_fl", (27, 2, -36), (18, 10, 18), "p2_rock"),
    ("leg_fl", "ft_fl", (21, 0, -40), (28, 5, 26), "p2_rock"),
    ("leg_fl", "cl_fl1", (23, 0, -47), (6, 4, 6), "foot"),
    ("leg_fl", "cl_fl2", (33, 0, -45), (6, 4, 6), "foot"),
    ("leg_fl", "cl_fl3", (42, 0, -42), (6, 4, 6), "foot"),
    ("leg_fr", "sh_fr", (-48, 22, -42), (28, 8, 22), "p2_shell"),
    ("leg_fr", "up_fr", (-46, 10, -38), (22, 15, 20), "p2_rock"),
    ("leg_fr", "lo_fr", (-45, 2, -36), (18, 10, 18), "p2_rock"),
    ("leg_fr", "ft_fr", (-49, 0, -40), (28, 5, 26), "p2_rock"),
    ("leg_fr", "cl_fr1", (-29, 0, -47), (6, 4, 6), "foot"),
    ("leg_fr", "cl_fr2", (-39, 0, -45), (6, 4, 6), "foot"),
    ("leg_fr", "cl_fr3", (-48, 0, -42), (6, 4, 6), "foot"),

    # ---------------- 后肢：比前肢略窄，仍要分段 ----------------
    ("leg_bl", "sh_bl", (22, 22, 18), (26, 8, 20), "p2_shell"),
    ("leg_bl", "up_bl", (25, 10, 16), (20, 15, 20), "p2_rock"),
    ("leg_bl", "lo_bl", (27, 2, 14), (16, 10, 18), "p2_rock"),
    ("leg_bl", "ft_bl", (23, 0, 10), (26, 5, 24), "p2_rock"),
    ("leg_bl", "cl_bl1", (25, 0, 6), (6, 4, 6), "foot"),
    ("leg_bl", "cl_bl2", (38, 0, 10), (6, 4, 6), "foot"),
    ("leg_br", "sh_br", (-48, 22, 18), (26, 8, 20), "p2_shell"),
    ("leg_br", "up_br", (-45, 10, 16), (20, 15, 20), "p2_rock"),
    ("leg_br", "lo_br", (-43, 2, 14), (16, 10, 18), "p2_rock"),
    ("leg_br", "ft_br", (-49, 0, 10), (26, 5, 24), "p2_rock"),
    ("leg_br", "cl_br1", (-31, 0, 6), (6, 4, 6), "foot"),
    ("leg_br", "cl_br2", (-44, 0, 10), (6, 4, 6), "foot"),
]

CUBES_P2 = P2_CUBES
CUBES_P2 += [
    ("shell", "spike_f", (-3, 44, -26), (6, 16, 6), "spike"),
    ("shell", "spike_b", (-3, 44, 20), (6, 16, 6), "spike"),
    ("shell", "spike_l", (20, 44, -3), (6, 16, 6), "spike"),
    ("shell", "spike_r", (-26, 44, -3), (6, 16, 6), "spike"),
    ("head", "crest", (-6, 44, -76), (12, 9, 14), "spike"),
    ("head", "eye_glow_l", (9, 32, -79), (4, 5, 3), "lava"),
    ("head", "eye_glow_r", (-13, 32, -79), (4, 5, 3), "lava"),
]


def _fix_coplanar(cubes):
    """让相邻方块的共面面错开 0.02 单位。

    堆叠/贴合的两个方块如果某个面正好落在同一个平面（例如壳缘顶面 vs 甲片底面、
    腹甲侧面 vs 大腿侧面），两个面都会画出来，深度相同 ⇒ 深度测试打平，屏幕上出现
    密集条纹/闪烁。这里把"体积较小的那个"沿该轴往较大方块的中心推 0.02 单位，
    肉眼看不出位移，但共面消失。
    """
    items = [[b, n, [float(x) for x in o], [int(x) for x in s], th] for (b, n, o, s, th) in cubes]
    vol = [it[3][0] * it[3][1] * it[3][2] for it in items]
    for _ in range(10):
        changed = False
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                for axis in range(3):
                    a, b = items[i], items[j]
                    a0, a1 = a[2][axis], a[2][axis] + a[3][axis]
                    b0, b1 = b[2][axis], b[2][axis] + b[3][axis]
                    planes = [(a1, b0, +1), (a0, b1, -1), (a0, b0, 0), (a1, b1, 0)]
                    for pa, pb, push in planes:
                        if abs(pa - pb) > 1e-9:
                            continue
                        o1, o2 = [k for k in range(3) if k != axis]
                        if (min(a[2][o1] + a[3][o1], b[2][o1] + b[3][o1]) - max(a[2][o1], b[2][o1]) <= 0.5
                                or min(a[2][o2] + a[3][o2], b[2][o2] + b[3][o2]) - max(a[2][o2], b[2][o2]) <= 0.5):
                            continue
                        small, big = (i, j) if vol[i] <= vol[j] else (j, i)
                        if push == 0:
                            ca = items[small][2][axis] + items[small][3][axis] / 2.0
                            cb = items[big][2][axis] + items[big][3][axis] / 2.0
                            push = 1 if cb > ca else -1
                        items[small][2][axis] += 0.02 * push
                        vol[small] = items[small][3][0] * items[small][3][1] * items[small][3][2]
                        changed = True
                        break
        if not changed:
            break
    return [(it[0], it[1], tuple(it[2]), tuple(it[3]), it[4]) for it in items]


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
        "bones": BONES_P2,
        "rot": ROT_P2,
        "scale": P2_SCALE,
    },
}

# 相邻方块的共面面错开（否则关光影下会看到密集条纹/闪烁）
for _ph in PHASES.values():
    _ph["cubes"] = _fix_coplanar(_ph["cubes"])


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
                gpx[x0 + x, y0 + y] = (gv + (255,)) if gv else (0, 0, 0, 0)

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
                    gpx[x0 + cx, y0 + cy] = (gv + (255,)) if gv else (0, 0, 0, 0)
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
    # 发光层底色必须是**全透明**：不透明黑会把整个模型盖成黑的（二阶段纯黑就是这个 bug）
    gimg = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    packer = Packer(SHEET)
    rng = random.Random(20261019 + (0 if phase["glow"] else 7))
    uvs = {}
    # 贴图布局必须用"最终立方体尺寸"（含阶段缩放），否则 box-UV 的六个面会错位
    sc = phase.get("scale", 1.0)
    for bone, name, origin, size, theme_name in phase["cubes"]:
        w, h, d = [int(round(v * sc)) for v in size]
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
    sc = phase.get("scale", 1.0)
    rot = phase.get("rot", {})
    for name, pivot, parent in phase.get("bones", BONES):
        cubes = []
        for bone, cname, origin, size, theme_name in phase["cubes"]:
            if bone != name:
                continue
            u, v = uvs[cname]
            cube = {
                "name": cname,
                "origin": [round(origin[i] * sc, 3) for i in range(3)],
                "size": [int(round(size[i] * sc)) for i in range(3)],
                "uv": [u, v],
            }
            r = rot.get(cname)
            if r:
                axis, ang, piv = r
                cube["rotation"] = {
                    "origin": [round(piv[i] * sc, 3) for i in range(3)],
                    "axis": axis,
                    "angle": ang,
                }
            cubes.append(cube)
        entry = {"name": name, "pivot": [round(pivot[i] * sc, 3) for i in range(3)]}
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

    # 转阶段 3.4s 四拍：①停步低头、四肢外撑 ②下沉+震动、裂隙渐亮
    #                  ③壳侧岩甲与口沿向外掀开 ④口部短促喷发、抬头咆哮
    transition = once_anim(3.4, {
        "body": {"position": {"0": [0, 0, 0], "0.5": [0, -1.2, 0], "1.4": [0, -2.0, 0],
                              "1.6": [0, -1.6, 0], "1.8": [0, -2.2, 0], "2.0": [0, -1.7, 0],
                              "2.4": [0, 1.0, 0], "3.0": [0, 0.4, 0], "3.4": [0, 0, 0]},
                 "rotation": {"0": [0, 0, 0], "0.6": [8, 0, 0], "1.6": [10, 0, 0],
                              "2.0": [12, 0, 0], "2.6": [-8, 0, 0], "3.4": [0, 0, 0]}},
        "shell": {"rotation": {"0": [0, 0, 0], "1.6": [5, 0, 0], "2.0": [7, 0, 0],
                               "2.6": [-4, 0, 0], "3.4": [0, 0, 0]}},
        "whorl": {"rotation": {"0": [0, 0, 0], "1.6": [0, 120, 0], "2.6": [0, 300, 0],
                               "3.4": [0, 360, 0]}},
        "plate_l": {"rotation": {"0": [0, 0, 0], "2.0": [0, 0, 0], "2.6": [0, 0, -26],
                                 "3.4": [0, 0, -22]}},
        "plate_r": {"rotation": {"0": [0, 0, 0], "2.0": [0, 0, 0], "2.6": [0, 0, 26],
                                 "3.4": [0, 0, 22]}},
        "neck": {"rotation": {"0": [0, 0, 0], "0.6": [22, 0, 0], "1.8": [24, 0, 0],
                              "2.6": [-20, 0, 0], "3.4": [-6, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, 0], "0.6": [14, 0, 0], "1.8": [16, 0, 0],
                              "2.6": [-16, 0, 0], "3.4": [-4, 0, 0]}},
        "jaw": {"rotation": {"0": [0, 0, 0], "1.8": [0, 0, 0], "2.6": [-18, 0, 0],
                             "3.0": [6, 0, 0], "3.4": [0, 0, 0]}},
        "leg_fl": {"rotation": {"0": [0, 0, 0], "0.8": [0, 0, -10], "1.8": [-8, 0, -12],
                                "2.6": [4, 0, -8], "3.4": [0, 0, 0]}},
        "leg_fr": {"rotation": {"0": [0, 0, 0], "0.8": [0, 0, 10], "1.8": [-8, 0, 12],
                                "2.6": [4, 0, 8], "3.4": [0, 0, 0]}},
        "leg_bl": {"rotation": {"0": [0, 0, 0], "1.8": [6, 0, 0], "3.4": [0, 0, 0]}},
        "leg_br": {"rotation": {"0": [0, 0, 0], "1.8": [6, 0, 0], "3.4": [0, 0, 0]}},
        "tail": {"rotation": {"0": [0, 0, 0], "1.8": [-10, 0, 0], "2.6": [12, 0, 0],
                              "3.4": [0, 0, 0]}},
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
