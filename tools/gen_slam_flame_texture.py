"""生成重踏火环用的像素火焰序列（4 帧，32x64，带透明通道）。

一帧的结构（从上到下）：
- 上半部：2 束较宽的弯曲、分叉火舌，边缘橙红，主体橙黄，芯部亮黄；
- 底部约 31%：一条连续的亮黄带，用来让相邻火环粒子拼成一条不断的地面火带；
  亮带上缘做出细小的火舌缺口，避免出现一条笔直的水平接缝。

只写 assets/tnc/textures/particle/slam_flame_0..3.png，不碰其它资源。
"""

from __future__ import annotations

import math
import os
import random

from PIL import Image

W, H = 32, 64
BAND_TOP = 44          # 底部亮带从这一行开始（约 31% 高度）
FRAMES = 4

OUT_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle")


def mix(c0, c1, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c0[i] + (c1[i] - c0[i]) * t)) for i in range(3))


CORE = (255, 248, 206)
GOLD = (255, 210, 84)
ORANGE = (255, 150, 34)
DEEP = (222, 76, 24)
BAND_CORE = (255, 246, 196)
BAND_EDGE = (255, 186, 52)


def stamp(img, x, y, color, alpha=255):
    if 0 <= x < W and 0 <= y < H and alpha > 0:
        img[x, y] = (color[0], color[1], color[2], int(alpha))


def draw_band(px, frame):
    """底部连续亮带：中间近黄白，上缘过渡到橙黄，左右无缝。"""
    for y in range(BAND_TOP, H):
        t = (y - BAND_TOP) / float(H - 1 - BAND_TOP)        # 0 顶 -> 1 底
        core = 1.0 - abs(t - 0.45) * 1.5
        base = mix(BAND_EDGE, BAND_CORE, core)
        for x in range(W):
            # 上缘做 0~2 像素的起伏，拼起来不是一条直尺。
            edge = BAND_TOP + (1 if (x * 7 + frame * 3) % 11 < 3 else 0) + (1 if (x * 3) % 13 < 2 else 0)
            if y < edge:
                continue
            a = 255
            if t > 0.93:
                a = int(255 * (1.0 - (t - 0.93) / 0.07) * 0.9)
            stamp(px, x, y, base, a)


def draw_tongue(px, cx, height, width, curve, frame, seed):
    rnd = random.Random(seed + frame * 97)
    for i in range(height):
        t = i / max(1.0, height - 1)                 # 0 底 -> 1 顶
        y = BAND_TOP + 3 - i
        # 整体向后弯，顶端再轻微摆动；少量抖动制造翻卷，但不做噪点。
        bend = curve * (t ** 1.8) * 8.0 + math.sin(t * 4.2 + frame * 1.1 + seed) * 1.3
        half = width * 0.5 * (1.0 - t ** 1.5) + 0.9
        jitter = (rnd.random() - 0.5) * 0.9 * (0.2 + t)
        center = cx + bend + jitter
        fork = t > 0.78 and ((i + frame) % 6 == 0)
        for dx in range(-int(round(half)) - 1, int(round(half)) + 2):
            x = int(round(center)) + dx
            d = abs(dx) / max(0.9, half)
            if d > 1.0:
                continue
            if fork and abs(dx) <= 0:
                continue
            # 颜色分层：芯亮黄 -> 金黄 -> 橙 -> 少量橙红
            if d < 0.30:
                col = mix(CORE, GOLD, 0.10 + 0.30 * t)
            elif d < 0.66:
                col = mix(GOLD, ORANGE, 0.20 + 0.55 * t)
            else:
                col = mix(ORANGE, DEEP, 0.30 + 0.45 * t)
            alpha = 255 if d < 0.86 else int(255 * (1.0 - (d - 0.86) / 0.14))
            stamp(px, x, y, col, alpha)


def build_frame(frame):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    draw_band(px, frame)
    # 两束宽火舌：高度/位置/弯曲都不同，相邻粒子再镜像，避免整圈同一轮廓。
    specs = [
        (9.0, 26 + (4 if frame % 2 else 0), 10.0, -0.9, 11),
        (23.0, 19 + (6 if frame in (1, 2) else 0), 8.5, 0.7, 29),
    ]
    for idx, (cx, h, w, curve, seed) in enumerate(specs):
        draw_tongue(px, cx, h, w, curve, frame, seed + idx * 7)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for f in range(FRAMES):
        img = build_frame(f)
        path = os.path.join(OUT_DIR, f"slam_flame_{f}.png")
        img.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
