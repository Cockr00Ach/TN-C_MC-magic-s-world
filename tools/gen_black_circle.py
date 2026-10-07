#!/usr/bin/env python3
"""Generate textures/entity/black_circle.png -- the DARK chain's own magic circle.

作者 2026-10-10：「给暗魔法专门做一个**黑色的**魔法阵」+「手伸出去是穿过好几个**竖着的**魔法阵的感觉，
从 t3 开始」+「t 级越高魔法阵**越大**」。

为什么是"黑"的：这张图用 `entityTranslucentEmissive` 画（不受世界光照影响，按贴图原色显示）
=> 画成近黑 + 极暗的血红/冷紫线条，游戏里就是**一道压暗的阵**（黑魔法阵），
而不是发光的花纹。所以本图整体亮度刻意很低：
    * 外双环   (26,23,32) / (16,14,20)
    * 环间符文 (44,16,22) 暗血红
    * 内圈符印 (22,20,28)，关键笔画 (62,18,24)
    * 中心留空（不画 = 全透明）
⚠️ 太黑会在夜里几乎看不见 —— 要更清楚就整体提亮这几档（见下面的 PALETTE_GAIN）。

输出：src/main/resources/assets/tnc/textures/entity/black_circle.png  (128x128, RGBA)

用法： python tools/gen_black_circle.py
"""

import math
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                   "textures", "entity", "black_circle.png")

SIZE = 128
PALETTE_GAIN = 1.0          # ★ 整体明度旋钮（1.0 = 现值；嫌太黑就 1.4 / 1.8）

RING_OUTER = (26, 23, 32)
RING_INNER = (16, 14, 20)
RUNE = (44, 16, 22)          # 暗血红
SEAL = (22, 20, 28)
SEAL_ACCENT = (62, 18, 24)


def scaled(color):
    return tuple(min(255, int(c * PALETTE_GAIN)) for c in color)


def ring(draw, cx, cy, radius, width, color):
    for i in range(width):
        r = radius - i
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=scaled(color))


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = cy = SIZE / 2.0

    # 外双环（宽度不同，看起来像"刻痕"而不是一条细线）
    ring(draw, cx, cy, 60, 3, RING_OUTER)
    ring(draw, cx, cy, 52, 1, RING_INNER)
    ring(draw, cx, cy, 46, 2, RING_OUTER)

    # 环间符文：每 15° 一小段（只在外两环之间画，像一圈刻字）
    for step in range(24):
        angle = math.radians(step * 15.0)
        for t in (48.5, 51.0):
            x = cx + math.cos(angle) * t
            y = cy + math.sin(angle) * t
            draw.point((int(x), int(y)), fill=scaled(RUNE))
    # 四个方位加粗一点（上下左右各一小块）
    for step in range(4):
        angle = math.radians(step * 90.0)
        for t in (47.5, 49.5, 51.5):
            x = cx + math.cos(angle) * t
            y = cy + math.sin(angle) * t
            draw.rectangle([x - 1, y - 1, x + 1, y + 1], fill=scaled(RUNE))

    # 内圈
    ring(draw, cx, cy, 34, 2, SEAL)

    # 六芒（两个反向三角）：用线画，笔画用暗血红点缀
    def tri(radius, flip):
        pts = []
        for i in range(3):
            a = math.radians(90.0 + i * 120.0 + (180.0 if flip else 0.0))
            pts.append((cx + math.cos(a) * radius, cy + math.sin(a) * radius))
        return pts

    up = tri(30, False)
    down = tri(30, True)
    draw.polygon(up, outline=scaled(SEAL))
    draw.polygon(down, outline=scaled(SEAL_ACCENT))

    # 中心：一个小方框 + 四点（"封印"的读感），中间留空
    draw.rectangle([cx - 6, cy - 6, cx + 6, cy + 6], outline=scaled(SEAL_ACCENT))
    for dx, dy in ((-10, 0), (10, 0), (0, -10), (0, 10)):
        draw.rectangle([cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1],
                       fill=scaled(RUNE))

    img.save(OUT)
    print("wrote %s (%d bytes, %dx%d)" % (OUT, os.path.getsize(OUT), SIZE, SIZE))

    # 客观自检：不透明像素占比 / 最亮像素（我看不了图，用数字确认它不是空白或全黑）
    px = img.load()
    opaque = sum(1 for x in range(SIZE) for y in range(SIZE) if px[x, y][3] > 0)
    peak = max((px[x, y][0] + px[x, y][1] + px[x, y][2]) // 3
               for x in range(SIZE) for y in range(SIZE) if px[x, y][3] > 0)
    print("opaque pixels: %d (%.1f%%)   peak brightness: %d" %
          (opaque, 100.0 * opaque / (SIZE * SIZE), peak))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
