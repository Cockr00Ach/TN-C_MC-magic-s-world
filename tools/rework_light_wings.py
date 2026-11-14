#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rework_light_wings.py -- 重做光系法阵给的「光翼」（作者 2026-11-07：「那个翅膀有点丑，你做个新的」）。

★ 这是**当前权威**的光翼生成器 ✓（旧的 `gen_light_wings.py` / `gen_light_wings_model.py` 是
2026-10-01 那一版，跑起来会把这一版覆盖回去 ✗）。

旧版（离线预览 `LightWingsPreviewTest` 里能看到）的四个毛病：
  * 每根羽毛是 **1 x 6 x 1** 的细条 ⇒ 一排针，背面看像一堆稻草；
  * 4 块大平板（8~11 x 3）用 25~62.5 度硬扇开 ⇒ 像扇子骨架，不像翼；
  * 两翼各是一张 **1 单位厚** 的纸，还分别贴在 z=0 / z=1 两个平面 ⇒ 侧看是一条线；
  * 只铺到 x -19..-10、y -2..17 ⇒ 比玩家还矮，像背了个风筝；
    没有前缘骨条、没有覆羽分层、没有上缘的「拇指羽」⇒ 轮廓完全不像鸟翼。

这一版按「天使翼」重画，但只能用方块 + box-UV（渲染器 `TNLightWingsModel` 只认这一种 ✗）：
  * **前缘**：4 段沿「肩 → 肘 → 腕」的锥形骨条（2.4~1.9 x 4~6 x 1.5），翅膀的脊柱 ——
    羽毛的根一律**压在这根骨条下面 1 格**，所以骨条始终是上缘 ✓；
  * **初级飞羽 7**：从腕/掌往外扫（+4 度 → -38 度），长 14 → 9 ⇒ 翅膀最宽处；
  * **次级飞羽 6**：从肘往下挂（-62 度 → -84 度），长 15 → 12 ⇒ 垂到胯的那道下缘；
  * **覆羽 8**：短（6~7.6）、更白、贴在最前面一层（z 1.2~1.5）⇒ 盖住所有羽根接缝；
  * **拇指羽 4**：肩/腕往**上**翘（+58/+44/+30/+22 度）⇒ 那个鸟翼的尖；
  * 每根羽毛 = **base + tip 两块**（tip 更窄更短 ⇒ 尖端是尖的，不是锯子）；
    宽 1.8~3.3、厚 0.8~0.9，沿 z 交错 0 / 0.28 / 0.56 三层 ⇒ 有厚度、不闪面、侧看也有东西。
  ⇒ 单翼 39 块，两翼 78 + 2 参考块 + 4 肩块 = **84 块 / 504 面**。

坐标约定（沿用旧文件 —— 渲染器与放置参数都按它写死了，不能改）：
  * 模型 **+Z = 胸口**、+Y 上、+X 左；左翼几何在 **x 负** 侧、z 0..2（贴在后背 0.125 格内 ✓）；
  * 右翼 = **同一份几何** 经 `wingright` 的 `rotation:[0,180,0]`（绕 (0,0,1)）镜像 ⇒
    方块 z 要写成 `2 - (z + d)` 才是镜像后的同一深度 ✓（旧版两翼写死 0/1，差了一个单位深 ✗）；
  * 两个 `uv:[-4,-1]` 的方块是**参考块**（作者的「后背 z1..4 / 胸前 z4..7」），
    渲染器按「uv 有负数」跳过不画 —— **原样保留** ✓（放置数学靠它们标定 ✓）。

贴图：64x64，**8 列 x 3 行**的 8x20 小格；每格一种材质（羽 / 覆羽 / 骨条 / 拇指羽 / 肩块），
长度方向「根部亮 → 尖端转金 → 尖端 alpha 渐隐」，中轴一条亮羽轴、两侧略暗 ⇒
拉伸到任何长度都还像羽毛，且尖端是软的（`entityTranslucentEmissive` 认贴图 alpha ✓）。

ASCII only (project tooling rule).
"""
import json
import math
import os

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc")
GEO_PATH = os.path.join(ASSETS, "geo", "entity", "light_wings.geo.json")
TEX_PATH = os.path.join(ASSETS, "textures", "entity", "light_wings_bedrock.png")
PREVIEW_TEX = os.path.join(REPO, "work", "light_wings_bedrock_v2.png")

TEX = 64
CELL_W = 8
CELL_H = 20

# name -> (column, row) in the 8 x 3 grid of 8x20 cells
SLOTS = {
    "primary": (0, 0),
    "secondary": (1, 0),
    "covert": (2, 0),
    "arm": (3, 0),
    "tuft": (4, 0),
    "stub": (5, 0),
    "primaryBack": (0, 1),
    "secondaryBack": (1, 1),
    "covertBack": (2, 1),
    "armBack": (3, 1),
}


def slot_uv(name):
    col, row = SLOTS[name]
    return col * CELL_W, row * CELL_H


# ---------------------------------------------------------------- geometry --


def cube(origin, size, material, rot=None, pivot=None):
    u, v = slot_uv(material)
    c = {
        "origin": [round(x, 3) for x in origin],
        "size": [round(x, 3) for x in size],
        "uv": [u, v],
    }
    if rot:
        c["rotation"] = [round(x, 3) for x in rot]
        c["pivot"] = [round(x, 3) for x in pivot]
    return c


def feather(root, lift_deg, length, width, depth, material):
    """One plate of `length` pointing outward (-X), lifted `lift_deg` above horizontal.

    The cube's long axis is +Y: that keeps the box-UV net narrow ((2d+2w) x (L+d)),
    which fits an 8x20 cell. Rotating it by (lift - 90) degrees about Z points +Y at
    (-cos lift, +sin lift) -- i.e. outward and up for a positive lift.
    """
    rx, ry, rz = root
    return cube(
        [rx - width / 2.0, ry, rz],
        [width, length, depth],
        material,
        rot=(0, 0, lift_deg - 90.0),
        pivot=(rx, ry, rz),
    )


def blade(root, lift_deg, length, width, depth, material, tip_material=None):
    """A feather: one wide base plate plus a narrower, shorter tip plate -> pointed end."""
    if tip_material is None:
        tip_material = material
    out = [feather(root, lift_deg, length * 0.74, width, depth, material)]
    rad = math.radians(lift_deg)
    tip = (root[0] - math.cos(rad) * length * 0.70,
           root[1] + math.sin(rad) * length * 0.70,
           root[2])
    out.append(feather(tip, lift_deg, length * 0.34, width * 0.58, depth * 0.9, tip_material))
    return out


def arm_segment(a, b, width, depth, material):
    """One tapered bone segment from point a to point b (both in the XY plane)."""
    ax, ay = a
    bx, by = b
    mx, my = (ax + bx) / 2.0, (ay + by) / 2.0
    length = math.hypot(bx - ax, by - ay)
    angle = math.degrees(math.atan2(by - ay, bx - ax))
    return cube(
        [mx - width / 2.0, my - length / 2.0, -depth / 2.0],
        [width, length, depth],
        material,
        rot=(0, 0, angle - 90.0),
        pivot=(mx, my, 0.0),
    )


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


SHOULDER = (-2.2, 9.0)
ELBOW = (-9.2, 15.4)
WRIST = (-12.6, 18.6)
HAND = (-15.6, 19.4)


def left_wing_bones():
    """The left wing, split over the four bones the author's `waving` clip drives."""
    # ---- wingLeft: shoulder region (upward spikes + a shoulder plate + the humerus) ----
    shoulder = []
    for i, (lift, length, width) in enumerate([(52.0, 6.0, 1.9), (38.0, 7.0, 2.1), (24.0, 8.0, 2.3)]):
        shoulder += blade((-2.8 - i * 0.5, 9.4 + i * 0.9, 0.30 + i * 0.30),
                          lift, length, width, 0.85, "tuft")
    shoulder += blade((-2.9, 8.4, 0.9), -32.0, 8.5, 3.0, 0.9, "covert")
    shoulder.append(arm_segment(SHOULDER, lerp(SHOULDER, ELBOW, 0.55), 2.6, 1.6, "arm"))

    # ---- wingLeftMid: elbow region (forearm + the hanging secondaries + coverts) ----
    mid = []
    mid.append(arm_segment(lerp(SHOULDER, ELBOW, 0.45), ELBOW, 2.4, 1.6, "arm"))
    mid.append(arm_segment(ELBOW, lerp(ELBOW, WRIST, 0.55), 2.2, 1.5, "arm"))
    for j in range(6):
        root = lerp(lerp(SHOULDER, ELBOW, 0.45), WRIST, j * 0.16)
        lift = -60.0 - j * 4.2
        length = 16.5 - j * 0.5
        width = 3.3 - j * 0.10
        mat = "secondary" if j % 2 == 0 else "secondaryBack"
        mid += blade((root[0], root[1] - 1.6, 0.10 + j * 0.24), lift, length, width, 0.9, mat)
    for k in range(4):
        root = lerp(lerp(SHOULDER, ELBOW, 0.25), WRIST, k * 0.26)
        lift = -24.0 - k * 15.0
        mat = "covert" if k % 2 == 0 else "covertBack"
        mid += blade((root[0], root[1] - 0.9, 1.40), lift, 8.2 - k * 0.3, 2.9, 0.8, mat)

    # ---- wingLeftTip: wrist / hand (the outward sweep + alula) ----
    tip = []
    tip.append(arm_segment(lerp(ELBOW, WRIST, 0.45), WRIST, 2.0, 1.4, "arm"))
    for i in range(7):
        root = lerp(WRIST, HAND, 1.0 - i * 0.12)
        lift = -6.0 - i * 6.4
        length = 17.0 - i * 1.0
        width = 3.5 - i * 0.12
        mat = "primary" if i % 2 == 0 else "primaryBack"
        tip += blade((root[0], root[1] - 1.8, 0.10 + (i % 3) * 0.28), lift, length, width, 0.9, mat)
    tip += blade((WRIST[0] + 0.2, WRIST[1] - 0.4, 1.5), 16.0, 5.5, 1.9, 0.8, "tuft")
    for k in range(2):
        root = lerp(WRIST, HAND, 0.25 + k * 0.45)
        tip += blade((root[0], root[1] - 1.0, 1.45), -20.0 - k * 15.0, 7.0 - k * 0.5, 2.6, 0.8, "covert")

    # ---- wingLeftInner: three short feathers that close the gap over the spine ----
    inner = []
    for k in range(3):
        inner += blade((-3.2, 7.4 + k * 1.4, 0.55 + k * 0.25),
                       -72.0 - k * 5.0, 10.0 - k * 0.8, 2.8, 0.85, "secondary")
    return shoulder, mid, tip, inner


def mirror_cubes(cubes):
    """Right wing = the same geometry seen through `wingright`'s 180 deg Y rotation about z=1."""
    out = []
    for c in cubes:
        m = dict(c)
        o, s = c["origin"], c["size"]
        m["origin"] = [round(o[0], 3), round(o[1], 3), round(2.0 - (o[2] + s[2]), 3)]
        m["size"] = list(s)
        m["uv"] = list(c["uv"])
        if "pivot" in c:
            p = c["pivot"]
            m["pivot"] = [round(p[0], 3), round(p[1], 3), round(2.0 - p[2], 3)]
            m["rotation"] = list(c["rotation"])
        out.append(m)
    return out


def build_bones():
    shoulder, mid, tip, inner = left_wing_bones()
    return [
        {
            "name": "wing",
            "pivot": [0, 0, 0],
            "cubes": [
                # the two reference blocks (uv negative => never drawn); keep EXACTLY as before
                {"origin": [-4, 0, 1], "size": [10, 7, 3], "uv": [-4, -1]},
                {"origin": [-4, 0, 4], "size": [10, 7, 3], "uv": [-4, -1]},
                cube([-4.2, 7.7, 0.1], [1.4, 1.4, 1.2], "stub"),
                cube([2.8, 7.7, 0.1], [1.4, 1.4, 1.2], "stub"),
            ],
        },
        {"name": "wingLeft", "parent": "wing", "pivot": [0, 0, 0], "cubes": shoulder},
        {"name": "wingLeftMid", "parent": "wingLeft", "pivot": [-9, 3, 0], "cubes": mid},
        {"name": "wingLeftTip", "parent": "wingLeftMid", "pivot": [-13, 7, 0], "cubes": tip},
        {"name": "wingLeftInner", "parent": "wingLeft", "pivot": [-5, 0, 0], "cubes": inner},
        {
            "name": "wingright",
            "parent": "wing",
            "pivot": [0, 0, 1],
            "rotation": [0, 180, 0],
            # 右翼的"肩" = 左翼 `wingLeft` 那堆（上翘小羽 + 肩块 + 上臂）的镜像 ✓
            # （v2 漏过这一步 ⇒ 右翼少了整块肩膀，静止镜像误差 0.125 格就是这么来的 ✗）
            "cubes": mirror_cubes(shoulder),
        },
        {"name": "wingLeftMid2", "parent": "wingright", "pivot": [-9, 3, 1], "cubes": mirror_cubes(mid)},
        {"name": "wingLeftTip2", "parent": "wingLeftMid2", "pivot": [-13, 7, 1], "cubes": mirror_cubes(tip)},
        {"name": "wingLeftInner2", "parent": "wingright", "pivot": [-5, 0, 1], "cubes": mirror_cubes(inner)},
    ]


# ----------------------------------------------------------------- texture --


def lerp_colour(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint_cell(px, name, root, tip, edge, quill, fade_tip=True):
    """A vertical feather: bright quill down the middle, soft gold edges, alpha at the tip."""
    u, v = slot_uv(name)
    cx = (CELL_W - 1) / 2.0
    for y in range(CELL_H):
        t = y / (CELL_H - 1.0)
        base = lerp_colour(root, tip, t)
        for x in range(CELL_W):
            d = abs(x - cx) / max(1.0, cx)
            col = lerp_colour(base, edge, d * 0.55)
            if abs(x - cx) < 0.75:
                col = lerp_colour(col, quill, 0.7)
            alpha = 255
            if fade_tip and t > 0.65:
                alpha = int(round(255 * max(0.18, 1.0 - (t - 0.65) / 0.35 * 0.82)))
            px[u + x, v + y] = col + (alpha,)


def build_texture():
    image = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = image.load()
    paint_cell(px, "primary", (255, 250, 232), (250, 206, 138), (232, 186, 118), (255, 255, 252))
    paint_cell(px, "primaryBack", (250, 240, 214), (240, 192, 126), (222, 172, 108), (255, 252, 238))
    paint_cell(px, "secondary", (255, 248, 226), (246, 198, 130), (228, 178, 112), (255, 255, 250))
    paint_cell(px, "secondaryBack", (248, 236, 208), (238, 188, 122), (218, 168, 104), (255, 250, 234))
    paint_cell(px, "covert", (255, 254, 246), (252, 224, 168), (238, 200, 142), (255, 255, 255))
    paint_cell(px, "covertBack", (252, 248, 236), (246, 212, 154), (230, 190, 134), (255, 255, 248))
    paint_cell(px, "arm", (255, 240, 202), (238, 190, 120), (214, 160, 96), (255, 255, 240), fade_tip=False)
    paint_cell(px, "armBack", (250, 230, 190), (230, 180, 112), (206, 152, 92), (255, 250, 230), fade_tip=False)
    paint_cell(px, "tuft", (255, 255, 252), (252, 214, 150), (236, 192, 130), (255, 255, 255))
    paint_cell(px, "stub", (255, 236, 190), (226, 174, 104), (204, 148, 88), (255, 255, 228), fade_tip=False)
    return image


def main():
    bones = build_bones()
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tnc.light_wings",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 4,
                "visible_bounds_height": 2.5,
                "visible_bounds_offset": [0, 0.75, 0],
            },
            "bones": bones,
        }],
    }
    with open(GEO_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(geo, fh, indent="\t")
        fh.write("\n")
    tex = build_texture()
    tex.save(TEX_PATH)
    os.makedirs(os.path.dirname(PREVIEW_TEX), exist_ok=True)
    tex.save(PREVIEW_TEX)

    visible = 0
    xs = []
    ys = []
    zs = []
    for b in bones:
        for c in b.get("cubes", []):
            if c["uv"][0] < 0:
                continue
            visible += 1
            xs.append(c["origin"][0])
            xs.append(c["origin"][0] + c["size"][0])
            ys.append(c["origin"][1])
            ys.append(c["origin"][1] + c["size"][1])
            zs.append(c["origin"][2])
            zs.append(c["origin"][2] + c["size"][2])
    print("bones  : %d" % len(bones))
    print("cubes  : %d visible" % visible)
    print("x range: %.2f .. %.2f  (span %.2f units = %.2f blocks)"
          % (min(xs), max(xs), max(xs) - min(xs), (max(xs) - min(xs)) / 16.0))
    print("y range: %.2f .. %.2f" % (min(ys), max(ys)))
    print("z range: %.2f .. %.2f  (must stay <= 3.2 so the wings sit behind the torso)"
          % (min(zs), max(zs)))
    print("wrote %s" % GEO_PATH)
    print("wrote %s (%dx%d)" % (TEX_PATH, TEX, TEX))


if __name__ == "__main__":
    main()
