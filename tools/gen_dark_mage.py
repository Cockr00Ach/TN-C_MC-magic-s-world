# -*- coding: utf-8 -*-
"""
gen_dark_mage.py -- 黑魔法师（暗系新 boss）的 Bedrock geo + 贴图 + 动作 + 预览。

设计意图（作者 2026-11-07："你做一个黑魔法师，通身黑暗"）
------------------------------------------------------------------
"通身黑暗"不是"涂黑"，而是：

  1) **没有脸**：兜帽里是一团比全身更深的黑（void 贴图），只有两道暗红眼缝发光；
  2) **没有脚**：袍摆下缘是一圈参差的破布，离地 10 单位 —— 他是**浮着**的，
     走路是"滑"，不是"迈腿"；
  3) **只有三个色阶**：近黑（袍）/ 亮一档的炭黑（破布边、护肩、帽檐）/ 暗红（眼、符文、杖心）。
     不出现任何暖色高光 —— 一有暖色就"不黑暗"了；
  4) **剪影优先**：尖兜帽 + 宽护肩 + 喇叭形袍摆 + 一根比自己还高的法杖 + 头后一圈**断裂**的环。
     远看轮廓就认得出"黑魔法师"，不靠细节。

坐标约定：1 单位 = 1/16 格，+Y 向上，脚底 y=0，**正面 = -Z**（和 yan / dark_giant 一致）。
总高 62 单位 = 3.875 格；最宽处（护肩）20 单位 = 1.25 格。

输出
----
  assets/tnc/geo/entity/dark_mage.geo.json
  assets/tnc/textures/entity/dark_mage_bedrock.png
  assets/tnc/textures/entity/dark_mage_emissive.png     （只有眼 / 符文 / 杖心，其余全透明）
  assets/tnc/animations/entity/dark_mage.animation.json
  docs/previews/dark_mage_model_texture.png
  docs/previews/mobs/dark_mage.png                      （三视图，复用地下城小怪那套离线渲染器）

改这个模型的时候记住一件事：**每一档明度都要能互相分开**。
第一版把袍子/破布/法杖全压在近黑（#06~#15），渲染出来就是一坨分辨不出的黑块 ✗。
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc")
GEO = os.path.join(ASSETS, "geo", "entity", "dark_mage.geo.json")
TEX = os.path.join(ASSETS, "textures", "entity", "dark_mage_bedrock.png")
EMI = os.path.join(ASSETS, "textures", "entity", "dark_mage_emissive.png")
ANIM = os.path.join(ASSETS, "animations", "entity", "dark_mage.animation.json")
PREVIEW = os.path.join(ROOT, "docs", "previews", "dark_mage_model_texture.png")

NAME = "dark_mage"
TEX_W, TEX_H = 256, 256

# ---------------------------------------------------------------------------
# 贴图分区：命名 -> (x, y, w, h)。方块 UV 只能落在自己那块里（见 uv()）
# ---------------------------------------------------------------------------
PATCH = {
    "cloth": (0, 0, 128, 128),     # 主袍：近黑，带竖向褶痕
    "edge":  (128, 0, 96, 64),     # 破布边 / 护肩 / 帽檐：亮一档的炭黑
    "void":  (224, 0, 32, 32),     # 兜帽内腔：比袍子更深
    "rune":  (224, 32, 32, 32),    # 胸前符文
    "bone":  (128, 64, 64, 64),    # 手 / 爪
    "metal": (0, 128, 64, 64),     # 法杖杆 / 杖爪 / 断环 / 护肩尖刺
    "orb":   (64, 128, 32, 32),    # 杖心球
    "eye":   (96, 128, 16, 16),    # 眼缝（发光）
}

_uv_counter = {"n": 0}


def uv(patch, w, h, d):
    """在分区内给一个安全的 uv 原点（带一点抖动，避免所有方块角对齐）。"""
    px, py, pw, ph = PATCH[patch]
    need_w, need_h = 2 * (w + d), d + h
    if need_w > pw or need_h > ph:
        raise ValueError("%s 放不下 %dx%dx%d 的方块（需要 %dx%d）"
                         % (patch, w, h, d, need_w, need_h))
    _uv_counter["n"] += 1
    n = _uv_counter["n"]
    # 眼 / 符文 / 杖心三块要整块同色（emissive 要按 UV 对应着点亮），所以不打散
    if patch in ("eye", "rune", "orb"):
        return [px, py]
    jx = (n * 13) % max(1, pw - need_w + 1)
    jy = (n * 7) % max(1, ph - need_h + 1)
    return [px + jx, py + jy]


def box(cx, y0, cz, w, h, d, patch, mirror=False):
    """以 (cx, cz) 为中心、y0 为**底面**的方块。"""
    cube = {
        "origin": [cx - w / 2.0, y0, cz - d / 2.0],
        "size": [w, h, d],
        "uv": uv(patch, w, h, d),
    }
    if mirror:
        cube["mirror"] = True
    return cube


def bone(name, parent=None, pivot=(0, 0, 0), rotation=None, cubes=None):
    b = {"name": name}
    if parent:
        b["parent"] = parent
    b["pivot"] = list(pivot)
    if rotation:
        b["rotation"] = list(rotation)
    if cubes:
        b["cubes"] = cubes
    return b


# ---------------------------------------------------------------------------
# 模型
# ---------------------------------------------------------------------------
def build_model():
    """比例照"高瘦的幽魂法师"定：总高 62 单位（3.9 格），最宽 20 单位（1.25 格）。

    高/宽 ≈ 3.1。第一版是 2.5（像个矮胖的桶 ✗），所以整段重定过一次。
    """
    bones = [bone("root"), bone("hover", "root")]

    # ---- 袍摆：上窄下宽的锥形 + 一圈参差破布（"没有脚"）----
    robe = [
        box(0, 10, 0, 15, 12, 12, "cloth"),   # 最下一节（最宽）
        box(0, 22, 0, 12, 8, 10, "cloth"),    # 中节
        box(0, 30, 0, 9, 5, 7, "cloth"),      # 腰
        box(0, 29, 0, 10, 2, 8, "edge"),      # 腰带（亮一圈，把上下分开）
        box(0, 10, 0, 15, 1, 12, "edge"),     # 下缘亮边（不然整块糊成一团）
    ]
    # 破布 15 片：角度带抖动、长度/宽度/前后都不齐。
    # 等角度等长会读成一排栅栏（第一版就是这样 ✗），参差才像"撕开的布"。
    jitter = (0.0, 0.23, -0.17, 0.31, -0.09, 0.14, -0.26, 0.05,
              0.19, -0.12, 0.27, -0.21, 0.08, -0.30, 0.16)
    for i in range(15):
        a = i * math.pi * 2 / 15.0 + jitter[i]
        x = math.cos(a) * 6.6
        z = math.sin(a) * 5.2
        drop = 3 + (i * 7) % 8                 # 3~10 单位
        w = 2 + (i * 5) % 5                    # 2~6 宽
        z2 = z + ((i * 3) % 3 - 1) * 1.2       # 前后错开，不是一圈同心
        robe.append(box(x, 10 - drop, z2, w, drop, 2, "edge"))
    bones.append(bone("robe", "hover", (0, 10, 0), cubes=robe))

    # ---- 躯干（窄，往上是收的）----
    body = [
        box(0, 34, 0, 9, 10, 6, "cloth"),      # 胸
        box(0, 36, -3.1, 4, 6, 1, "rune"),     # 胸前符文（唯一"亮"的织物）
        box(0, 43, 0, 7, 2, 5, "edge"),        # 领口
    ]
    bones.append(bone("body", "hover", (0, 34, 0), cubes=body))

    # ---- 护肩：宽、薄、带尖 —— 一横一竖，剪影才"压得住" ----
    mantle = [
        box(0, 44, 0, 20, 3, 10, "edge"),      # 主片
        box(-10, 41.5, 0, 3, 5, 9, "edge"),    # 左垂片
        box(10, 41.5, 0, 3, 5, 9, "edge"),     # 右垂片
        box(-8.5, 46.5, 0, 3, 5, 3, "metal"),  # 左尖刺
        box(8.5, 46.5, 0, 3, 5, 3, "metal"),   # 右尖刺
        box(0, 46.5, 4.5, 3, 4, 3, "metal"),   # 后尖刺
        box(0, 46.5, -4.5, 3, 4, 3, "metal"),  # 前尖刺
        box(-6.5, 39, -3.4, 3, 4, 2, "edge"),  # 前垂片（左）
        box(6.5, 39, -3.4, 3, 4, 2, "edge"),   # 前垂片（右）
    ]
    bones.append(bone("mantle", "body", (0, 45, 0), cubes=mantle))

    # ---- 披风：窄、贴在背后，下缘撕开 ----
    cape = [
        box(0, 14, 6.4, 12, 31, 1, "cloth"),
        box(0, 12, 6.9, 12, 2, 1, "edge"),
    ]
    for i, x in enumerate((-4.5, -1.8, 0.9, 3.6, 5.4)):
        cape.append(box(x, 6 + (i % 2) * 2, 7.1, 2, 7, 1, "edge"))
    bones.append(bone("cape", "body", (0, 45, 6.8), cubes=cape))

    # ---- 右臂：向外伸，手落到法杖上（法杖在身体轮廓**外面**，正面才看得见）----
    arm_r = [
        box(-6.5, 38, 0, 5, 6, 5, "cloth"),    # 肩
        box(-9, 33, 0, 5, 6, 5, "cloth"),      # 上臂
        box(-10.5, 29, 0, 4, 4, 4, "edge"),    # 袖口
        box(-12, 25, 0, 4, 4, 4, "bone"),      # 手
    ]
    for i, dx in enumerate((-1.2, 0.2, 1.6)):
        arm_r.append(box(-12 + dx, 20 + (i % 2), -1.1, 1, 5, 1, "bone"))   # 三根爪指
    bones.append(bone("armRight", "body", (-6.5, 39, 0), cubes=arm_r))

    staff = [
        box(-15.5, 6, 0, 2, 46, 2, "metal"),   # 杆（2.9 格）
        box(-15.5, 51, 0, 4, 2, 4, "metal"),   # 托
        box(-17.4, 52.5, 0, 1, 7, 1, "metal"), # 爪笼（四根）
        box(-13.6, 52.5, 0, 1, 7, 1, "metal"),
        box(-15.5, 52.5, -1.9, 1, 7, 1, "metal"),
        box(-15.5, 52.5, 1.9, 1, 7, 1, "metal"),
        box(-15.5, 58, 0, 5, 1, 5, "metal"),   # 爪尖收口
    ]
    bones.append(bone("staff", "armRight", (-12, 25, 0), cubes=staff))
    bones.append(bone("orb", "staff", (-15.5, 55, 0),
                      cubes=[box(-15.5, 53, 0, 4, 4, 4, "orb")]))

    # ---- 左臂（空手，五指张开）----
    arm_l = [
        box(6.5, 38, 0, 5, 6, 5, "cloth"),
        box(8.5, 33, 0, 5, 6, 5, "cloth"),
        box(9.5, 29, 0, 4, 4, 4, "edge"),
        box(10.5, 25, 0, 4, 4, 4, "bone"),
    ]
    for i, dx in enumerate((-1.8, -0.6, 0.6, 1.8)):
        arm_l.append(box(10.5 + dx, 20 + (i % 2), -1.1, 1, 5, 1, "bone"))
    bones.append(bone("armLeft", "body", (6.5, 39, 0), cubes=arm_l))

    # ---- 头 / 兜帽：窄、高、帽尖往前压 ----
    head = [
        box(0, 45, 0, 9, 10, 9, "cloth"),      # 兜帽主体
        box(0, 54, -0.6, 6, 4, 7, "cloth"),    # 帽尖（往前压）
        box(0, 57, -1.8, 4, 3, 5, "cloth"),    # 帽尖再收
        box(0, 59.5, -2.8, 2.5, 3, 3.5, "cloth"),  # 帽尖最上一节（收成尖）
        box(0, 44.2, -4.4, 7, 8, 1, "void"),   # 帽檐下的"脸"＝一团更深的黑
        box(0, 51.2, -3.7, 8, 2, 2, "edge"),   # 帽檐（亮边）
        box(0, 43, 4.2, 7, 11, 1, "cloth"),    # 帽后垂布
    ]
    eyes = [
        box(-1.7, 48.2, -4.9, 2, 1, 1, "eye"),
        box(1.7, 48.2, -4.9, 2, 1, 1, "eye"),
    ]
    bones.append(bone("head", "body", (0, 45, 0), cubes=head + eyes))

    # ---- 头后的断环：正面看是"头后面一圈环"，侧面看才看出它离身体有距离 ----
    # 用 edge 材质而不是 metal：metal 太黑，环在正面会"消失"，只剩几块飘着的碎屑 ✗
    halo = []
    for i in range(14):
        if i in (4, 11):                       # 缺两段 = 断的
            continue
        a = i * math.pi * 2 / 14.0 + 0.22
        halo.append(box(math.cos(a) * 11.0, 52 + math.sin(a) * 11.0, 7.6, 5, 2, 1, "edge"))
    bones.append(bone("halo", "body", (0, 52, 7.6), cubes=halo))

    # ---- 漂浮碎块：四根独立骨，动画各自绕 ----
    for i, (sx, sy, sz) in enumerate([(-13, 42, 1), (13, 46, -1), (-11, 32, -3), (12, 35, 3)]):
        bones.append(bone("shard%d" % i, "body", (sx, sy, sz), (0, 0, 45 + i * 20),
                          [box(sx, sy - 1.5, sz, 3, 3, 1, "edge")]))

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tnc.%s" % NAME,
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 4,
                "visible_bounds_height": 5,
                "visible_bounds_offset": [0, 2, 0],
            },
            "bones": bones,
        }],
    }


# ---------------------------------------------------------------------------
# 贴图
# ---------------------------------------------------------------------------
def paint_cloth(img, patch, base, fold, ridge, rng):
    x0, y0, w, h = PATCH[patch]
    px = img.load()
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rng.randint(-3, 3)
            c = base
            if (x - x0) % 7 == 0:                 # 竖向褶（暗）
                c = fold
            elif (x - x0) % 11 == 0:              # 褶脊（亮一格）
                c = ridge
            px[x, y] = (max(0, c[0] + n), max(0, c[1] + n), max(0, c[2] + n), 255)


def build_textures():
    rng = random.Random(20261107)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 255))
    emi = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    draw = ImageDraw.Draw(img)

    # 主袍：近黑偏蓝紫（#0c0b15）
    paint_cloth(img, "cloth", (13, 12, 21), (5, 5, 11), (21, 20, 33), rng)
    # 破布边 / 护肩 / 帽檐：**整块亮一档**（#2a2638）—— 这一档是"看得见剪影"的关键，
    # 全部压到近黑就只剩一团黑糊 ✗
    paint_cloth(img, "edge", (42, 38, 55), (23, 21, 33), (58, 52, 72), rng)

    # 兜帽内腔：比袍子更深，中央几乎纯黑
    vx, vy, vw, vh = PATCH["void"]
    for y in range(vy, vy + vh):
        for x in range(vx, vx + vw):
            t = 1.0 - math.hypot(x - vx - vw / 2.0, y - vy - vh / 2.0) / (vw / 2.0)
            v = max(0, int(7 * max(0.0, t)))
            px[x, y] = (v, 0, v + 3, 255)

    # 胸前符文：暗红底 + 更亮的刻线（同时点亮 emissive）
    rx, ry, rw, rh = PATCH["rune"]
    for y in range(ry, ry + rh):
        for x in range(rx, rx + rw):
            px[x, y] = (30, 5, 9, 255)
    for i in range(8):
        draw.line([(rx + 4 + i * 3, ry + 6), (rx + 4 + i * 3, ry + rh - 6)], fill=(104, 13, 20, 255))
    draw.rectangle([rx + 8, ry + 14, rx + rw - 8, ry + 17], fill=(158, 24, 30, 255))

    # 手 / 爪：冷灰（他很"薄"，不壮）
    bx, by, bw, bh = PATCH["bone"]
    for y in range(by, by + bh):
        for x in range(bx, bx + bw):
            t = (y - by) / float(bh)
            v = int(66 + 30 * t) + rng.randint(-4, 4)
            px[x, y] = (v, v - 2, v + 6, 255)

    # 法杖 / 断环 / 尖刺：黑，但留一道窄高光（不然杖身看不见）
    mx, my, mw, mh = PATCH["metal"]
    for y in range(my, my + mh):
        for x in range(mx, mx + mw):
            hi = 34 if (x - mx) % 7 == 0 else 0
            v = 20 + hi + rng.randint(0, 4)
            px[x, y] = (v, v - 1, v + 6, 255)

    # 杖心球：中心暗红 -> 边缘近黑（自发光）
    ox, oy, ow, oh = PATCH["orb"]
    for y in range(oy, oy + oh):
        for x in range(ox, ox + ow):
            t = 1.0 - min(1.0, math.hypot(x - ox - ow / 2.0, y - oy - oh / 2.0) / (ow / 2.0))
            px[x, y] = (int(44 + 190 * t), int(4 + 44 * t), int(12 + 36 * t), 255)

    # 眼缝：暗红转白热（发光）
    ex, ey, ew, eh = PATCH["eye"]
    for y in range(ey, ey + eh):
        for x in range(ex, ex + ew):
            px[x, y] = (255, 96, 74, 255)
    draw.rectangle([ex, ey + 6, ex + ew, ey + 9], fill=(255, 228, 204, 255))

    # ---- emissive：只留眼 / 符文 / 杖心（其余 alpha=0 => 不画）----
    ep = emi.load()
    for y in range(ey, ey + eh):
        for x in range(ex, ex + ew):
            ep[x, y] = (255, 104, 78, 255)
    for y in range(ry, ry + rh):
        for x in range(rx, rx + rw):
            r, g, b, _ = px[x, y]
            if r > 70:
                ep[x, y] = (min(255, r + 70), g, b, 255)
    for y in range(oy, oy + oh):
        for x in range(ox, ox + ow):
            r, g, b, _ = px[x, y]
            if r > 120:
                ep[x, y] = (r, g, b, 255)

    return img, emi


# ---------------------------------------------------------------------------
# 动作
# ---------------------------------------------------------------------------
def kf(pairs):
    return {("%.1f" % t): v for t, v in pairs}


def anim(length, bones, loop=None, hold=False):
    a = {}
    if loop:
        a["loop"] = True
    if hold:
        a["loop"] = "hold_on_last_frame"
    a["animation_length"] = float(length)
    a["bones"] = bones
    return a


def build_animations():
    A = {}

    # ---- 静止（循环 4s）----------------------------------------------------
    # 整体上下浮（他是浮着的）+ 披风轻摆 + 断环自转 + 碎块各自绕 + 头极慢地扫
    idle = {
        "hover": {"position": kf([(0, [0, 0, 0]), (1.4, [0, 1.2, 0]), (2.8, [0, 0, 0]),
                                  (4.0, [0, 0.9, 0])])},
        "robe": {"rotation": kf([(0, [0, 0, 0]), (2.0, [1.5, 0, 1.0]), (4.0, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [2, 0, 0]), (1.6, [5, 0, 1.5]), (3.2, [2, 0, -1.5]),
                                 (4.0, [2, 0, 0])])},
        # ★ 头不是左右摇，是"扫"：先停 1.2s，再慢慢偏向一边 —— 停住的那段比动更瘆人
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.2, [-1, 2, 0]), (2.4, [-2, -4, 0]),
                                 (3.4, [-2, -4, 1]), (4.0, [0, 0, 0])])},
        "mantle": {"rotation": kf([(0, [0, 0, 0]), (2.0, [0, 0, 0.8]), (4.0, [0, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.0, [0, 90, 0]), (4.0, [0, 180, 0])])},
        "orb": {"position": kf([(0, [0, 0, 0]), (1.0, [0, 0.5, 0]), (2.0, [0, 0, 0]),
                                (3.0, [0, 0.5, 0]), (4.0, [0, 0, 0])]),
                "scale": kf([(0, [1, 1, 1]), (1.5, [1.06, 1.06, 1.06]), (3.0, [0.95, 0.95, 0.95]),
                             (4.0, [1, 1, 1])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (2.0, [-1.5, 0, 0]), (4.0, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (2.0, [-1.5, 0, 0]), (4.0, [0, 0, 0])])},
    }
    for i in range(4):
        idle["shard%d" % i] = {
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (2.0, [0, 180, 45 + i * 20]),
                            (4.0, [0, 360, 45 + i * 20])]),
            "position": kf([(0, [0, 0, 0]), (1.0 + i * 0.3, [0, 1.4, 0]),
                            (2.0 + i * 0.3, [0, 0, 0]), (4.0, [0, 0, 0])]),
        }
    A["idle"] = anim(4.0, idle, loop=True)

    # ---- 静止·注视（5.2s）：几乎不动，只有头极慢地转向你 --------------------
    # 用在"玩家靠近/他盯上你"的时候：全身静止 3 秒，只有头转 16°，中途碎块猛地一抽。
    watch = {
        "hover": {"position": kf([(0, [0, 0, 0]), (2.6, [0, 0.5, 0]), (5.2, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (1.6, [0, -5, 0]), (3.4, [0, -5, 0]),
                                 (5.2, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.6, [3, -16, 0]), (3.4, [3, -16, 0]),
                                 (4.4, [0, -6, 0]), (5.2, [0, 0, 0])])},
        "mantle": {"rotation": kf([(0, [0, 0, 0]), (3.4, [0, 0, -2]), (5.2, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [2, 0, 0]), (2.6, [3, 0, 1]), (5.2, [2, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (5.2, [0, 30, 0])])},
        "orb": {"position": kf([(0, [0, 0, 0]), (2.6, [0, 0.3, 0]), (5.2, [0, 0, 0])])},
        # 一片碎块在 4.5s 突然"抽"了一下 —— 唯一的破绽
        "shard0": {"rotation": kf([(0, [0, 0, 45]), (4.5, [0, 0, 45]), (4.6, [0, 0, 82]),
                                   (4.8, [0, 0, 45]), (5.2, [0, 0, 45])])},
    }
    for i in range(1, 4):
        watch["shard%d" % i] = {"rotation": kf([(0, [0, 0, 45 + i * 20]),
                                               (5.2, [0, 360, 45 + i * 20])])}
    # 注意：不写 loop —— 它是被 perform() 用 thenPlay 触发的"演一段就还回去"✓
    # （写了 loop:true 也行，但数据要和用法一致，不然以后改成 idle 会静默变成永不到头 ✗）
    A["idle_watch"] = anim(5.2, watch)

    # ---- 抽搐（0.9s，一次性）：唯一"快"的动作 ---------------------------------
    # 全身慢慢浮着的时候突然一下 44° 甩头 + 断环跳 120°，然后再慢慢归位。
    # ★ 快慢对比才是"诡异"的来源：其它动作全在 2 秒以上，只有它 0.1 秒完成。
    twitch = {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 0.6, 0]), (0.4, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, -44, 7]), (0.3, [0, -38, 5]),
                                 (0.5, [0, -14, 2]), (0.9, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 9, 0]), (0.3, [0, 7, 0]),
                                 (0.9, [0, 0, 0])])},
        "mantle": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 0, 4]), (0.4, [0, 0, 1]),
                                   (0.9, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-16, 0, -8]), (0.4, [-6, 0, -2]),
                                     (0.9, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-16, 0, 8]), (0.4, [-6, 0, 2]),
                                    (0.9, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (0.1, [-8, 0, 0]), (0.9, [4, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, 120, 0]), (0.4, [0, 150, 0]),
                                 (0.9, [0, 160, 0])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (0.1, [1.4, 1.4, 1.4]), (0.4, [1, 1, 1])])},
    }
    for i in range(4):
        twitch["shard%d" % i] = {"rotation": kf([(0, [0, 0, 45 + i * 20]),
                                                 (0.1, [0, 60, 45 + i * 20]),
                                                 (0.9, [0, 75, 45 + i * 20])])}
    A["twitch"] = anim(0.9, twitch)

    # ---- 招手（2.4s）：空手抬起来"来"两下，头歪着看 -------------------------
    beckon = {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.8, [0, -0.4, 0]), (2.4, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-6, -12, 0]), (1.6, [-7, -14, 0]),
                                 (2.4, [0, 0, 0])])},
        # 空手（左臂）抬起后两次"勾"—— 没有手指骨，所以用整条小臂的顿挫代替
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-78, 0, 18]), (1.0, [-88, 0, 26]),
                                    (1.3, [-70, 0, 12]), (1.7, [-88, 0, 26]), (2.4, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [12, 10, 0]), (1.6, [14, 12, 0]),
                                 (2.4, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (0.7, [8, 0, 0]), (1.2, [0, 0, 0]),
                                  (1.8, [6, 0, 0]), (2.4, [0, 0, 0])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (0.8, [1.25, 1.25, 1.25]), (1.4, [1, 1, 1]),
                             (2.0, [1.2, 1.2, 1.2]), (2.4, [1, 1, 1])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (1.2, [10, 0, 0]), (2.4, [4, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.4, [0, -30, 0])])},
    }
    A["beckon"] = anim(2.4, beckon)

    # ---- 溃散（1.6s）：被打中时"闪"一下 ---------------------------------------
    # 整个人像信号不好：身体压扁-拉伸-回弹，碎块炸开再收，断环狂转一圈半。
    unravel = {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.3, [0, 1.0, 0]), (0.9, [0, 0.3, 0]),
                                  (1.6, [0, 0, 0])])},
        "body": {"scale": kf([(0, [1, 1, 1]), (0.2, [0.94, 1.05, 0.94]), (0.5, [1.03, 0.96, 1.03]),
                              (0.8, [0.98, 1.02, 0.98]), (1.2, [1.01, 0.99, 1.01]), (1.6, [1, 1, 1])]),
                 "rotation": kf([(0, [0, 0, 0]), (0.2, [0, -6, 0]), (0.6, [0, 5, 0]),
                                 (1.0, [0, -2, 0]), (1.6, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.1, [0, -10, 0]), (0.3, [0, 8, 0]),
                                 (0.5, [0, -5, 0]), (0.8, [0, 3, 0]), (1.6, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (0.3, [-24, 0, 0]), (0.8, [-10, 0, 0]),
                                 (1.6, [4, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-30, 0, -24]), (0.9, [-10, 0, -8]),
                                     (1.6, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.3, [10, 0, 30]), (0.9, [4, 0, 10]),
                                    (1.6, [0, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (1.2, [0, 540, 0]), (1.6, [0, 540, 0])]),
                 "scale": kf([(0, [1, 1, 1]), (0.4, [1.5, 1.5, 1]), (1.2, [1, 1, 1])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (0.3, [1.5, 1.5, 1.5]), (0.8, [0.85, 0.85, 0.85]),
                             (1.6, [1, 1, 1])])},
    }
    push = [[1.6, 0.8, 0], [-1.6, -0.4, 0.6], [0.9, 1.5, -0.9], [-0.9, -1.2, -0.6]]
    for i in range(4):
        dx, dy, dz = push[i]
        unravel["shard%d" % i] = {
            "position": kf([(0, [0, 0, 0]), (0.4, [dx, dy, dz]), (0.9, [dx * 0.4, dy * 0.4, dz * 0.4]),
                            (1.6, [0, 0, 0])]),
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (0.4, [120, 90, 45 + i * 20]),
                            (1.6, [200, 150, 45 + i * 20])]),
        }
    A["unravel"] = anim(1.6, unravel)

    # ---- 逼视（2.2s）：凑近、低头、盯着你 -------------------------------------
    stare = {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.7, [0, -1.4, 0]), (1.8, [0, -1.2, 0]),
                                  (2.2, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.7, [11, 0, 0]), (1.6, [12, 0, 0]),
                                 (2.2, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.7, [17, 0, 0]), (1.4, [17, 0, -7]),
                                 (2.2, [0, 0, 0])])},
        "mantle": {"rotation": kf([(0, [0, 0, 0]), (0.9, [6, 0, 0]), (2.2, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.9, [8, 0, -5]), (2.2, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.9, [8, 0, 5]), (2.2, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (1.0, [-6, 0, 0]), (2.2, [4, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.2, [0, -24, 0])])},
        "orb": {"position": kf([(0, [0, 0, 0]), (0.9, [0, -0.4, 0]), (2.2, [0, 0, 0])])},
    }
    A["stare"] = anim(2.2, stare)

    # ---- 滑行（1.4s 循环）：他浮着前进，倾一点 + 袍摆后拖，不是迈腿 -----------
    A["glide"] = anim(1.4, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.35, [0, 0.8, 0]), (0.7, [0, 0, 0]),
                                  (1.05, [0, 0.8, 0]), (1.4, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [-3, 0, 0]), (0.7, [-4.5, 0, 1.2]), (1.4, [-3, 0, 0])])},
        "robe": {"rotation": kf([(0, [5, 0, 0]), (0.7, [8, 0, -1.5]), (1.4, [5, 0, 0])])},
        "cape": {"rotation": kf([(0, [12, 0, 0]), (0.7, [16, 0, 2]), (1.4, [12, 0, 0])])},
        "armRight": {"rotation": kf([(0, [4, 0, 0]), (0.7, [7, 0, -2]), (1.4, [4, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [4, 0, 0]), (0.7, [7, 0, 2]), (1.4, [4, 0, 0])])},
        "head": {"rotation": kf([(0, [2, 0, 0]), (1.4, [2, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (1.4, [0, 40, 0])])},
        "shard1": {"rotation": kf([(0, [0, 0, 65]), (1.4, [0, 120, 65])])},
        "shard3": {"rotation": kf([(0, [0, 0, 105]), (1.4, [0, -120, 105])])},
    }, loop=True)

    # ---- 双手前推（黑夜之手 T3/T4）------------------------------------------
    A["cast_hands"] = anim(2.2, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.55, [0, -1.0, 0]), (2.2, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.55, [-9, 0, 0]), (1.4, [-11, 0, 0]),
                                 (2.2, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.55, [-72, 0, -10]), (1.4, [-84, 0, -6]),
                                     (2.2, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.55, [-72, 0, 10]), (1.4, [-84, 0, 6]),
                                    (2.2, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (1.4, [-14, 0, 0]), (2.2, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (1.4, [-10, 0, 0]), (2.2, [4, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.4, [-10, 0, 0]), (2.2, [0, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (1.4, [0, 60, 0]), (2.2, [0, 60, 0])])},
    })
    for i in range(4):
        A["cast_hands"]["bones"]["shard%d" % i] = {
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (1.4, [0, 220, 45 + i * 20]),
                            (2.2, [0, 0, 45 + i * 20])])}

    # ---- 双手举天（黑夜之手 T5）----------------------------------------------
    A["cast_sky_hands"] = anim(2.8, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.7, [0, 2.0, 0]), (2.0, [0, 2.4, 0]),
                                  (2.8, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.7, [7, 0, 0]), (2.0, [9, 0, 0]),
                                 (2.8, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.7, [-150, 0, -22]), (2.0, [-166, 0, -16]),
                                     (2.8, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.7, [-150, 0, 22]), (2.0, [-166, 0, 16]),
                                    (2.8, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (2.0, [-30, 0, 0]), (2.8, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.7, [22, 0, 0]), (2.0, [26, 0, 0]),
                                 (2.8, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (0.7, [-18, 0, 0]), (2.0, [-22, 0, 0]),
                                 (2.8, [4, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.8, [0, 360, 0])])},
        "orb": {"position": kf([(0, [0, 0, 0]), (1.4, [0, 1.2, 0]), (2.8, [0, 0, 0])]),
                "scale": kf([(0, [1, 1, 1]), (1.6, [1.3, 1.3, 1.3]), (2.4, [1, 1, 1])])},
    })
    for i in range(4):
        A["cast_sky_hands"]["bones"]["shard%d" % i] = {
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (2.8, [0, 540, 45 + i * 20])]),
            "position": kf([(0, [0, 0, 0]), (1.4, [0, 1.0 + i * 0.3, 0]), (2.8, [0, 0, 0])])}

    # ---- 法杖横扫、双臂摊开（黑雾 T5）----------------------------------------
    A["cast_mist"] = anim(2.6, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.8, [0, -0.6, 0]), (2.6, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-6, -14, 0]), (1.7, [-6, 14, 0]),
                                 (2.6, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-30, 0, -38]), (1.7, [-24, 0, -52]),
                                     (2.6, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-20, 0, 40]), (1.7, [-16, 0, 54]),
                                    (2.6, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (0.8, [34, 0, 20]), (1.7, [18, 0, 34]),
                                  (2.6, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (1.0, [16, 0, 6]), (2.0, [16, 0, -6]),
                                 (2.6, [4, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (0.8, [0, -50, 0]), (1.7, [0, -95, 0]),
                                 (2.6, [0, -95, 0])])},
        "shard2": {"position": kf([(0, [0, 0, 0]), (1.0, [0.8, 0.4, 0]), (1.8, [-0.8, 0.2, 0]),
                                   (2.6, [0, 0, 0])])},
    })
    for i in range(4):
        A["cast_mist"]["bones"]["shard%d" % i] = {
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (1.7, [0, -200, 45 + i * 20]),
                            (2.6, [0, 0, 45 + i * 20])])}

    # ---- 单臂探出、法杖指人（以伤换伤 T3）------------------------------------
    A["cast_siphon"] = anim(2.2, {
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-8, 16, 0]), (1.5, [-10, 18, 0]),
                                 (2.2, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-96, 0, 4]), (1.5, [-104, 0, 2]),
                                     (2.2, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.6, [26, 0, 26]), (1.5, [30, 0, 30]),
                                    (2.2, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (1.5, [-92, 0, 0]), (2.2, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.5, [-6, -12, 0]), (2.2, [0, 0, 0])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (0.7, [1.45, 1.45, 1.45]), (1.5, [1.1, 1.1, 1.1]),
                             (2.2, [1, 1, 1])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.2, [0, -40, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (1.2, [12, 0, -6]), (2.2, [4, 0, 0])])},
    })

    # ---- 法杖顿地、双臂摊开（召唤 T4/T5）—— 他浮着，所以"顿"是整体下沉 ------
    A["command_army"] = anim(2.8, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.9, [0, -2.2, 0]), (1.6, [0, -2.4, 0]),
                                  (2.8, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-12, 0, 0]), (1.6, [-14, 0, 0]),
                                 (2.8, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-58, 0, -26]), (1.6, [-62, 0, -22]),
                                     (2.8, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-40, 0, 44]), (1.6, [-44, 0, 52]),
                                    (2.8, [0, 0, 0])])},
        "staff": {"rotation": kf([(0, [0, 0, 0]), (0.9, [64, 0, 0]), (1.6, [70, 0, 0]),
                                  (2.8, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (0.9, [-16, 0, 0]), (2.0, [-20, 0, 0]),
                                 (2.8, [4, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-14, 0, 0]), (2.8, [0, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (2.8, [0, -360, 0])]),
                 "scale": kf([(0, [1, 1, 1]), (1.4, [1.3, 1.3, 1]), (2.4, [1, 1, 1])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (1.0, [1.5, 1.5, 1.5]), (1.8, [1, 1, 1])])},
    })
    for i in range(4):
        A["command_army"]["bones"]["shard%d" % i] = {
            "position": kf([(0, [0, 0, 0]), (1.2, [0, 1.6, 0]), (2.8, [0, 0, 0])])}

    # ---- 二阶段：整个人"涨"开（袍摆外扩、护肩抬起、断环放大）----------------
    A["phase2"] = anim(3.0, {
        "hover": {"position": kf([(0, [0, 0, 0]), (0.8, [0, 3.5, 0]), (1.8, [0, 2.2, 0]),
                                  (3.0, [0, 0, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0]), (0.8, [12, 0, 0]), (1.8, [8, 0, 0]),
                                 (3.0, [0, 0, 0])])},
        "armRight": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-120, 0, -46]), (1.8, [-104, 0, -40]),
                                     (3.0, [0, 0, 0])])},
        "armLeft": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-120, 0, 46]), (1.8, [-104, 0, 40]),
                                    (3.0, [0, 0, 0])])},
        "robe": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-6, 0, 0]), (3.0, [0, 0, 0])])},
        "cape": {"rotation": kf([(0, [4, 0, 0]), (0.9, [-34, 0, 0]), (2.0, [-26, 0, 0]),
                                 (3.0, [4, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.8, [28, 0, 0]), (2.0, [16, 0, 0]),
                                 (3.0, [0, 0, 0])])},
        "halo": {"rotation": kf([(0, [0, 0, 0]), (3.0, [0, 720, 0])]),
                 "scale": kf([(0, [1, 1, 1]), (1.2, [1.35, 1.35, 1]), (3.0, [1, 1, 1])])},
        "orb": {"scale": kf([(0, [1, 1, 1]), (1.0, [1.6, 1.6, 1.6]), (2.6, [1, 1, 1])])},
    }, hold=True)
    for i in range(4):
        A["phase2"]["bones"]["shard%d" % i] = {
            "position": kf([(0, [0, 0, 0]), (1.2, [0, 2.0 + i * 0.4, 0]), (3.0, [0, 0, 0])]),
            "rotation": kf([(0, [0, 0, 45 + i * 20]), (3.0, [0, 360, 45 + i * 20])])}

    return {"format_version": "1.8.0", "animations": A}

# ---------------------------------------------------------------------------
def main():
    for p in (GEO, TEX, EMI, ANIM, PREVIEW):
        os.makedirs(os.path.dirname(p), exist_ok=True)

    model = build_model()
    with open(GEO, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
        f.write("\n")

    tex, emi = build_textures()
    tex.save(TEX)
    emi.save(EMI)

    anims = build_animations()
    with open(ANIM, "w", encoding="utf-8", newline="\n") as f:
        json.dump(anims, f, indent=1, ensure_ascii=False)
        f.write("\n")

    board = Image.new("RGB", (TEX_W * 2 + 24, TEX_H), (24, 24, 28))
    board.paste(tex.convert("RGB"), (0, 0))
    board.paste(emi.convert("RGB"), (TEX_W + 24, 0))
    board.save(PREVIEW)

    geo = model["minecraft:geometry"][0]
    cubes = sum(len(b.get("cubes", [])) for b in geo["bones"])
    print("geo     -> %s  (%d bones, %d cubes)"
          % (os.path.relpath(GEO, ROOT), len(geo["bones"]), cubes))
    print("texture -> %s" % os.path.relpath(TEX, ROOT))
    print("emissive-> %s" % os.path.relpath(EMI, ROOT))
    print("anim    -> %s  (%s)" % (os.path.relpath(ANIM, ROOT), ", ".join(sorted(anims["animations"]))))
    print("preview -> %s" % os.path.relpath(PREVIEW, ROOT))

    # 三视图（复用地下城小怪那套离线渲染器）
    try:
        sys.path.insert(0, os.path.join(ROOT, "tools"))
        import render_dungeon_mob_preview as rdp
        out_dir = os.path.join(ROOT, "docs", "previews", "mobs")
        os.makedirs(out_dir, exist_ok=True)
        views = []
        info = None
        for yaw in (rdp.YAW, 0.0, math.radians(90.0)):
            img, info = rdp.render(NAME, yaw=yaw)
            views.append(rdp.compose_card(img))
        strip = Image.new("RGB", (sum(v.width for v in views), views[0].height), (32, 28, 24))
        x = 0
        for v in views:
            strip.paste(v, (x, 0))
            x += v.width
        path = os.path.join(out_dir, NAME + ".png")
        strip.save(path)
        print("views   -> %s  (boxes=%d faces=%d)"
              % (os.path.relpath(path, ROOT), info["boxes"], info["faces"]))
    except Exception as exc:                                    # noqa: BLE001
        print("views   -- skipped:", exc)


if __name__ == "__main__":
    main()
