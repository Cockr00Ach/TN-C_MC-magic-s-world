#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rework_wind_spirit.py -- 风灵（wind_sprite）的第二次重做：模型 + 贴图。

作者 2026-11-07："来改改风 ... 优化一下风灵风球模型"。

做完第一次重做（`refine_wind_models.py`）之后，风灵在游戏里仍然只是
"一根竖条 + 一对纸扇"：
  * 7 片翅膀全在**同一个竖直平面**里、只绕 Z 转，所以正面看是一把扇子、
    侧面看是一块板（没有后掠、没有分层、没有厚度差）；
  * 头是一个 4.5^3 的方块，没有口鼻、没有眼神；
  * 冠是 3 根竖直小棒；尾巴是 3 片往下掉的小板；"光环"是两根金条。

这一版按"真的会飞的小风灵"重画：
  * 翅膀 = **后掠 + 上反角 + 两层**：6 根初级飞羽（yaw 9..51 度、roll 17..12 度、
    长度 7.6..3.5）、4 根覆羽（更短、更高、盖住羽根）、1 块肩羽；每根羽毛都绕
    自己的羽根 pivot 转，所以扇动时是**整只翅膀一起张合**而不是一根根乱抖；
  * 翅膀骨改成 `body` 的子骨（以前挂在 root 上，身体悬停时翅膀不跟着上下）；
  * 头有口鼻、眉脊、两只眼睛（独立"眼"材质，不是身上那块色）；
  * 胸口一颗风精核心（gem 材质，渲染时配合 `entityTranslucentEmissive` 发亮）；
  * 冠改成 4 根**往后上方掠**的羽，尾巴是 3 根**往后下方拖**的流苏；
  * 原来的两根金条换成 4 段**镀金飘带**（从肩后拖出去，和风系法阵的
    (224,188,92) 金线同一个色系）。

贴图仍是 64x64，用"材质色板 + 逐面 UV"的写法（`refine_wind_models.py` 已经验证过
这条路可行）：每个方块 6 个面都指向对应材质的小色板，色板里画渐变和细节，
所以任意尺寸的方块都不会出现"没画到的透明像素"。

BONES（`wind_sprite.animation.json` 的 idle 用到 body / wing_left / wing_right /
crest / tail / tail_left / tail_right，其余是渲染/挂点用的，一个都不能删）

ASCII only (project tooling rule).
"""
import json
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc")
GEO_PATH = os.path.join(ASSETS, "geo", "entity", "wind_sprite.geo.json")
TEX_PATH = os.path.join(ASSETS, "textures", "entity", "wind_sprite_bedrock.png")
PREVIEW_TEX = os.path.join(REPO, "work", "wind_sprite_bedrock_v3.png")

TEX = 64

# material -> (u, v, w, h) swatch inside the 64x64 atlas
SWATCH = {
    "feather": (0, 0, 16, 16),
    "body": (16, 0, 16, 16),
    "crest": (32, 0, 16, 16),
    "gold": (48, 0, 16, 16),
    "eye": (0, 16, 8, 8),
    "gem": (8, 16, 8, 8),
    "shadow": (16, 16, 8, 8),
}

FACES = ("north", "south", "east", "west", "up", "down")


def face_uv(material, only=None):
    u, v, w, h = SWATCH[material]
    return {f: {"uv": [u, v], "uv_size": [w, h]} for f in FACES}


def cube(origin, size, material, rot=None, pivot=None, faces=None):
    c = {
        "origin": [round(x, 3) for x in origin],
        "size": [round(x, 3) for x in size],
        "uv": face_uv(material),
    }
    if faces:
        for name, mat in faces.items():
            u, v, w, h = SWATCH[mat]
            c["uv"][name] = {"uv": [u, v], "uv_size": [w, h]}
    if rot:
        c["rotation"] = [round(x, 3) for x in rot]
        c["pivot"] = [round(x, 3) for x in pivot]
    return c


def mirror(c):
    """Mirror a cube across x=0: position flips, Y/Z rotations negate, winding is kept."""
    out = dict(c)
    o, s = c["origin"], c["size"]
    out["origin"] = [round(-o[0] - s[0], 3), o[1], o[2]]
    if "rotation" in c:
        r = c["rotation"]
        out["rotation"] = [r[0], -r[1], -r[2]]
        p = c["pivot"]
        out["pivot"] = [round(-p[0], 3), p[1], p[2]]
    return out


def wing(sign):
    """One swept, layered wing: 1 scapular + 6 primaries + 4 coverts."""
    cubes = []
    # scapular blade: hides the feather roots at the shoulder
    cubes.append(cube([1.6, 8.9, -0.9], [4.2, 0.9, 2.4], "feather",
                      rot=(0, -8, 16), pivot=(2.2, 9.3, 0)))
    # primaries: long, sweeping back and up
    for i in range(6):
        length = 6.9 - i * 0.72
        yaw = -(8 + i * 8.0)
        roll = 18 - i * 1.4
        cubes.append(cube(
            [2.30 + i * 0.10, 9.15 - i * 0.10, 0.05 + i * 0.30],
            [length, 0.50, 1.75 - i * 0.06],
            "feather",
            rot=(0, yaw, roll),
            pivot=(2.2 + i * 0.08, 9.4 - i * 0.10, 0.10 + i * 0.30)))
    # coverts: shorter, higher, in front of the primaries
    for i in range(4):
        length = 4.2 - i * 0.55
        yaw = -(3 + i * 6.5)
        roll = 27 - i * 2
        cubes.append(cube(
            [2.25 + i * 0.12, 9.55 - i * 0.05, -0.55 + i * 0.26],
            [length, 0.48, 1.45],
            "feather",
            rot=(0, yaw, roll),
            pivot=(2.2, 9.65, -0.50 + i * 0.26)))
    if sign < 0:
        cubes = [mirror(c) for c in cubes]
    return cubes


def alula(sign):
    """Two small tip feathers on the lower-wing bone (the wing's outer hand)."""
    cubes = [
        cube([5.9, 9.00, 1.00], [4.2, 0.42, 1.25], "feather",
             rot=(0, -36, 12), pivot=(2.2, 9.3, 0.3)),
        cube([6.3, 8.60, 1.60], [3.4, 0.40, 1.10], "feather",
             rot=(0, -48, 4), pivot=(2.4, 9.3, 0.5)),
    ]
    if sign < 0:
        cubes = [mirror(c) for c in cubes]
    return cubes


def build_bones():
    body = [
        cube([-1.7, 2.6, -1.4], [3.4, 2.0, 2.8], "body"),
        cube([-1.4, 4.4, -1.2], [2.8, 2.2, 2.4], "body"),
        cube([-1.9, 6.4, -1.6], [3.8, 2.4, 3.2], "body"),
        cube([-2.2, 8.4, -1.3], [4.4, 1.0, 2.6], "body"),
        cube([-0.8, 9.2, -0.7], [1.6, 0.9, 1.4], "body"),
        cube([-0.55, 6.9, -1.95], [1.1, 1.3, 0.35], "gem"),
    ]
    head = [
        cube([-1.9, 10.0, -2.1], [3.8, 3.4, 3.8], "body"),
        cube([-1.2, 10.35, -2.75], [2.4, 1.5, 0.8], "body"),
        cube([-1.5, 12.45, -2.2], [3.0, 0.45, 0.45], "crest"),
        # eyes are painted on the front face only
        cube([-1.35, 11.55, -2.35], [0.70, 0.75, 0.30], "eye"),
        cube([0.65, 11.55, -2.35], [0.70, 0.75, 0.30], "eye"),
    ]
    crest = [
        cube([-0.45, 12.85, -0.55], [0.90, 0.42, 3.20], "crest",
             rot=(-42, 0, 0), pivot=(0, 13.2, -0.3)),
        cube([0.25, 12.65, -0.65], [0.75, 0.38, 2.50], "crest",
             rot=(-38, 0, 6), pivot=(0.25, 13.0, -0.35)),
        mirror(cube([0.25, 12.65, -0.65], [0.75, 0.38, 2.50], "crest",
                    rot=(-38, 0, 6), pivot=(0.25, 13.0, -0.35))),
    ]
    arm_left = [cube([2.2, 4.0, -0.5], [0.8, 3.4, 0.8], "shadow",
                     rot=(10, 0, -8), pivot=(2.4, 7.2, -0.2))]
    arm_right = [mirror(c) for c in arm_left]
    tail = [
        cube([-0.55, -0.90, 0.50], [1.10, 5.00, 0.45], "feather",
             rot=(-50, 0, 0), pivot=(0, 3.2, -1.3)),
    ]
    tail_left = [cube([0.40, 0.30, 0.40], [0.85, 4.00, 0.40], "feather",
                      rot=(-46, 0, 9), pivot=(0.7, 2.8, -1.4))]
    tail_right = [mirror(c) for c in tail_left]
    halo_left = [
        cube([1.15, 8.30, 0.70], [0.50, 0.18, 4.20], "gold",
             rot=(2, -12, 8), pivot=(1.4, 8.7, 0.9)),
        cube([1.50, 7.90, 4.20], [0.45, 0.16, 3.60], "gold",
             rot=(-10, -18, -6), pivot=(1.6, 8.2, 4.4)),
    ]
    halo_right = [mirror(c) for c in halo_left]

    return [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 6, 0], "cubes": body},
        {"name": "head", "parent": "body", "pivot": [0, 10.6, 0], "cubes": head},
        {"name": "crest", "parent": "body", "pivot": [0, 13.6, 0], "cubes": crest},
        {"name": "wing_left", "parent": "body", "pivot": [2.2, 9.3, 0], "cubes": wing(1)},
        {"name": "wing_right", "parent": "body", "pivot": [-2.2, 9.3, 0], "cubes": wing(-1)},
        {"name": "wing_left_lower", "parent": "wing_left", "pivot": [7.0, 9.2, 0.6], "cubes": alula(1)},
        {"name": "wing_right_lower", "parent": "wing_right", "pivot": [-7.0, 9.2, 0.6], "cubes": alula(-1)},
        {"name": "arm_left", "parent": "body", "pivot": [2.6, 7.4, -0.2], "cubes": arm_left},
        {"name": "arm_right", "parent": "body", "pivot": [-2.6, 7.4, -0.2], "cubes": arm_right},
        {"name": "tail", "parent": "body", "pivot": [0, 3.4, -1.4], "cubes": tail},
        {"name": "tail_left", "parent": "body", "pivot": [0.8, 3.0, -1.5], "cubes": tail_left},
        {"name": "tail_right", "parent": "body", "pivot": [-0.8, 3.0, -1.5], "cubes": tail_right},
        {"name": "halo_left", "parent": "body", "pivot": [2.0, 8.6, 0.4], "cubes": halo_left},
        {"name": "halo_right", "parent": "body", "pivot": [-2.0, 8.6, 0.4], "cubes": halo_right},
    ]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def paint_swatch(px, box, top, bottom, stripes=None, glow=None):
    u, v, w, h = box
    for y in range(h):
        t = y / max(1, h - 1)
        col = lerp(top, bottom, t)
        for x in range(w):
            px[u + x, v + y] = col + (255,)
    if stripes:
        step, col = stripes
        for y in range(0, h, step):
            for x in range(w):
                px[u + x, v + y] = col + (255,)
    if glow:
        cx, cy, radius, col = glow
        for y in range(h):
            for x in range(w):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= radius:
                    k = max(0.0, 1.0 - d / radius)
                    base = px[u + x, v + y][:3]
                    px[u + x, v + y] = lerp(base, col, k) + (255,)


def build_texture():
    image = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = image.load()
    paint_swatch(px, SWATCH["feather"], (252, 255, 253), (176, 224, 214),
                 stripes=(5, (206, 240, 232)))
    paint_swatch(px, SWATCH["body"], (234, 250, 246), (150, 206, 198))
    paint_swatch(px, SWATCH["crest"], (255, 255, 255), (206, 242, 236))
    paint_swatch(px, SWATCH["gold"], (240, 208, 124), (196, 158, 72))
    paint_swatch(px, SWATCH["shadow"], (142, 194, 190), (112, 162, 160))
    paint_swatch(px, SWATCH["eye"], (34, 96, 112), (18, 58, 72),
                 glow=(4.0, 4.2, 3.0, (150, 246, 236)))
    paint_swatch(px, SWATCH["gem"], (150, 226, 216), (226, 252, 248),
                 glow=(4.0, 4.0, 3.4, (255, 255, 255)))
    # a bright rim on the body swatch reads as the chest highlight
    draw = ImageDraw.Draw(image)
    u, v, w, h = SWATCH["body"]
    draw.line([(u, v + 2), (u + w - 1, v + 2)], fill=(255, 255, 255, 255))
    return image


def main():
    bones = build_bones()
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tnc.wind_sprite",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }
    with open(GEO_PATH, "w", encoding="utf-8") as fh:
        json.dump(geo, fh, indent=2)
        fh.write("\n")
    tex = build_texture()
    tex.save(TEX_PATH)
    os.makedirs(os.path.dirname(PREVIEW_TEX), exist_ok=True)
    tex.save(PREVIEW_TEX)

    cubes = sum(len(b.get("cubes", [])) for b in bones)
    print("bones : %s" % ", ".join(b["name"] for b in bones))
    print("cubes : %d" % cubes)
    print("wrote %s" % GEO_PATH)
    print("wrote %s (%dx%d)" % (TEX_PATH, TEX, TEX))


if __name__ == "__main__":
    main()
