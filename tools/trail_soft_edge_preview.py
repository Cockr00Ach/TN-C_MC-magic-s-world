"""刀光"软边"的离线对比图（不是游戏截图）。

左边：只有顶点色 —— 四边形的边由几何直接切出来，光带两侧是硬边（"贴纸感"）。
右边：同一套几何，但按 `WarriorTrailUVConsumer` 的规则给每个顶点算 u，
      乘上真实贴图 `trail_plume.png` 的横向渐隐 —— 边就化开了。

贴图直接读资源目录里的那张 PNG，所以这张图会随着贴图变化（贴图改了要重跑）。

用法：
    python tools/trail_soft_edge_preview.py                 # → docs/previews/trail-soft-edge-20261113.png
    python tools/trail_soft_edge_preview.py --out a.png
"""

from __future__ import annotations

import argparse
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

TEXTURE = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle",
                       "trail_plume.png")
OUT = os.path.join("docs", "previews", "trail-soft-edge-20261113.png")

FRAMES = 14
BLADE = 1.25
WRIST = 0.42
FROM = math.radians(-135.0)
TO = math.radians(50.0)
GHOST = 1.9          # 虚影倍率（练满）


def smoothstep(x: float) -> float:
    return x * x * (3.0 - 2.0 * x)


def pose(frame: int):
    u = smoothstep(frame / (FRAMES - 1.0))
    angle = FROM + (TO - FROM) * u
    wrist = (WRIST * math.cos(angle + math.radians(35.0)),
             WRIST * math.sin(angle + math.radians(35.0)) - 0.08)
    tip = (wrist[0] + BLADE * math.cos(angle), wrist[1] + BLADE * math.sin(angle))
    ghost = (wrist[0] + (tip[0] - wrist[0]) * GHOST,
             wrist[1] + (tip[1] - wrist[1]) * GHOST)
    return wrist, tip, ghost


def load_texture():
    """读整张贴图（u 横跨光带，v 沿轨迹）。"""
    with Image.open(TEXTURE) as image:
        rgba = image.convert("RGBA")
        return [[rgba.getpixel((x, y))[3] / 255.0 for x in range(rgba.width)]
                for y in range(rgba.height)]


def sample(tex, u: float, v: float) -> float:
    """和 `WarriorTrailUVConsumer` 一样：u = 横跨光带，v = 按世界坐标取的循环值。"""
    h = len(tex)
    w = len(tex[0])
    u = max(0.0, min(0.9999, u))
    v = v - math.floor(v)
    return tex[min(h - 1, int(v * h))][min(w - 1, int(u * w))]


def screen_v(point) -> float:
    """世界坐标 → 贴图 v（和 Java 侧同一个式子，预览是 2D 所以 z 取 0）。"""
    return (point[0] * 0.37 + point[1] * 0.23) * 0.30


def draw_panel(size, soft: bool, tex):
    w, h = size
    canvas = Image.new("RGBA", (w, h), (8, 10, 15, 255))
    draw = ImageDraw.Draw(canvas, "RGBA")
    pts = [pose(i) for i in range(FRAMES)]

    xs = [p[0][0] for p in pts] + [p[2][0] for p in pts]
    ys = [p[0][1] for p in pts] + [p[2][1] for p in pts]
    span_x = max(xs) - min(xs)
    span_y = max(ys) - min(ys)
    scale = min((w - 40) / span_x, (h - 90) / span_y)
    cx = w * 0.5 - (min(xs) + max(xs)) * 0.5 * scale
    cy = h * 0.5 + 14 + (min(ys) + max(ys)) * 0.5 * scale

    def px(p):
        return (cx + p[0] * scale, cy - p[1] * scale)

    for i in range(FRAMES - 1):
        wrist_a, _, ghost_a = pts[i]
        wrist_b, _, ghost_b = pts[i + 1]
        fade = 1.0 - i / (FRAMES - 1.0)           # 越老越淡
        weight = 0.28 + 0.72 * fade
        # 横跨方向切成 28 份：每份按 u 取贴图 alpha（soft）或恒 1（硬边）
        steps = 96
        inner = 0.35                      # 从剑身 35% 处开始铺，读起来才是一条"带"而不是扇面
        for k in range(steps):
            u0, u1 = k / steps, (k + 1) / steps
            p0 = inner + (1.0 - inner) * u0
            p1 = inner + (1.0 - inner) * u1
            a0 = (wrist_a[0] + (ghost_a[0] - wrist_a[0]) * p0,
                  wrist_a[1] + (ghost_a[1] - wrist_a[1]) * p0)
            a1 = (wrist_a[0] + (ghost_a[0] - wrist_a[0]) * p1,
                  wrist_a[1] + (ghost_a[1] - wrist_a[1]) * p1)
            b1 = (wrist_b[0] + (ghost_b[0] - wrist_b[0]) * p1,
                  wrist_b[1] + (ghost_b[1] - wrist_b[1]) * p1)
            b0 = (wrist_b[0] + (ghost_b[0] - wrist_b[0]) * p0,
                  wrist_b[1] + (ghost_b[1] - wrist_b[1]) * p0)
            mid = ((a0[0] + b1[0]) * 0.5, (a0[1] + b1[1]) * 0.5)
            shade_tex = sample(tex, (u0 + u1) * 0.5, screen_v(mid)) if soft else 1.0
            alpha = min(1.0, weight * shade_tex * 0.85)
            if alpha <= 0.004:
                continue
            shade = int(150 + 90 * p0)
            draw.polygon([px(a0), px(a1), px(b1), px(b0)],
                         fill=(shade, shade + 22, 255, int(alpha * 255)))
    return canvas


def label(canvas, text, xy, font):
    draw = ImageDraw.Draw(canvas, "RGBA")
    x, y = xy
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        draw.text((x + dx, y + dy), text, font=font, fill=(0, 0, 0, 230))
    draw.text(xy, text, font=font, fill=(230, 236, 246, 255))


def load_font(size):
    for path in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyhbd.ttc",
                 r"C:\Windows\Fonts\simhei.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    if not os.path.exists(TEXTURE):
        print(f"缺少贴图 {TEXTURE}，先跑 tools/gen_trail_texture.py")
        return 1
    tex = load_texture()

    panel_w, panel_h = 620, 440
    ss = 2                                    # 超采样：细丝只有 ~1/128 个带宽，不放大就会糊成一坨
    total = (panel_w * 2 + 3, panel_h)
    sheet = Image.new("RGBA", (total[0] * ss, total[1] * ss), (8, 10, 15, 255))
    sheet.alpha_composite(draw_panel((panel_w * ss, panel_h * ss), False, tex), (0, 0))
    sheet.alpha_composite(draw_panel((panel_w * ss, panel_h * ss), True, tex), ((panel_w + 3) * ss, 0))
    sheet = sheet.resize(total, Image.LANCZOS)

    font = load_font(19)
    small = load_font(14)
    label(sheet, "只有顶点色：边是几何切出来的", (16, 14), font)
    label(sheet, "乘上软边贴图：边化开、中间有细亮纹", (panel_w + 19, 14), font)
    label(sheet, "示意渲染（不是游戏截图）：同一套刀光几何，只换贴图", (16, panel_h - 28), small)
    draw = ImageDraw.Draw(sheet, "RGBA")
    draw.line([(panel_w, 0), (panel_w, panel_h)], fill=(58, 64, 78, 255), width=3)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    sheet.save(args.out)
    print(f"wrote {args.out} ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
