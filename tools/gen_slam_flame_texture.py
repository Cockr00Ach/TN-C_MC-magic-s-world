"""生成重踏火环用的像素火焰序列（4 帧，32x64，带透明通道）。

一帧的结构（从上到下）：
- 上半部：2 束较宽的弯曲、分叉火舌，边缘橙红，主体橙黄，芯部亮黄；
- 底部约 34%：贴地火焰根部，做成有亮暗斑块的橙黄纹理，而不是一条均匀白条；
  左右各 3 像素渐隐、上缘和内部带随机缺口，避免相邻贴片拼出整齐的矩形硬边。

只写 assets/tnc/textures/particle/slam_flame_0..3.png，不碰其它资源。
"""

from __future__ import annotations

import math
import os
import random

from PIL import Image

W, H = 32, 64
BAND_TOP = 42          # 底部火根从这一行开始（约 34% 高度）
FRAMES = 4

OUT_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle")


def mix(c0, c1, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c0[i] + (c1[i] - c0[i]) * t)) for i in range(3))


CORE = (255, 246, 198)
GOLD = (255, 206, 78)
ORANGE = (255, 148, 34)
DEEP = (216, 70, 22)
ROOT_LIT = (255, 232, 150)
ROOT_MID = (246, 168, 58)
ROOT_DARK = (206, 104, 30)


def stamp(img, x, y, color, alpha=255):
    if 0 <= x < W and 0 <= y < H and alpha > 0:
        img[x, y] = (color[0], color[1], color[2], int(alpha))


def edge_fade(x):
    """左右边缘 0..3 像素渐隐，削弱相邻贴片的矩形接缝。"""
    d = min(x, W - 1 - x)
    if d >= 3:
        return 1.0
    return (d + 1) / 4.0


def draw_band(px, frame):
    """贴地火根：橙黄斑块 + 少量亮黄，不再是一条均匀白带。"""
    rnd = random.Random(1000 + frame * 31)
    # 每个 x 的上缘高度不同，拼起来是连续但不平齐的火根轮廓。
    tops = []
    for x in range(W):
        t = 0.0
        t += 1.0 if (x * 5 + frame * 3) % 9 < 4 else 0.0
        t += 1.0 if (x * 3 + frame) % 11 < 3 else 0.0
        t += rnd.random() * 1.4
        tops.append(BAND_TOP + int(t))

    for y in range(BAND_TOP - 2, H):
        rel = (y - BAND_TOP) / float(H - 1 - BAND_TOP)     # 0 上 -> 1 下
        for x in range(W):
            if y < tops[x]:
                continue
            # 斑块亮度：低频 + 随机，避免整条一个颜色
            cell = (int(x / 3) * 7 + int(y / 4) * 11 + frame * 5) % 17
            lit = 0.5 + 0.5 * math.sin((x / 5.0) + (y / 7.0) + frame)
            if cell < 3:
                base = mix(ROOT_MID, ROOT_LIT, 0.55)
            elif cell < 6:
                base = mix(ROOT_MID, ROOT_DARK, 0.45)
            elif cell < 9:
                base = mix(ROOT_LIT, ROOT_MID, 0.35)
            else:
                base = mix(ROOT_MID, ORANGE, 0.35 + 0.35 * lit)
            if rel > 0.55:
                base = mix(base, ROOT_DARK, (rel - 0.55) * 0.9)
            a = 255.0 * edge_fade(x)
            if rel > 0.90:
                a *= 1.0 - (rel - 0.90) / 0.10 * 0.75
            stamp(px, x, y, base, int(a))


def draw_tongue(px, cx, height, width, curve, frame, seed):
    rnd = random.Random(seed + frame * 97)
    for i in range(height):
        t = i / max(1.0, height - 1)                 # 0 底 -> 1 顶
        y = BAND_TOP + 4 - i
        bend = curve * (t ** 1.8) * 8.0 + math.sin(t * 4.2 + frame * 1.1 + seed) * 1.3
        half = width * 0.5 * (1.0 - t ** 1.5) + 0.9
        jitter = (rnd.random() - 0.5) * 0.8 * (0.2 + t)
        center = cx + bend + jitter
        fork = t > 0.78 and ((i + frame) % 6 == 0)
        for dx in range(-int(round(half)) - 1, int(round(half)) + 2):
            x = int(round(center)) + dx
            d = abs(dx) / max(0.9, half)
            if d > 1.0:
                continue
            if fork and abs(dx) <= 0:
                continue
            if d < 0.30:
                col = mix(CORE, GOLD, 0.10 + 0.30 * t)
            elif d < 0.66:
                col = mix(GOLD, ORANGE, 0.20 + 0.55 * t)
            else:
                col = mix(ORANGE, DEEP, 0.30 + 0.45 * t)
            alpha = 255.0 * edge_fade(x)
            if d >= 0.86:
                alpha *= 1.0 - (d - 0.86) / 0.14
            stamp(px, x, y, col, int(alpha))


def build_frame(frame):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    draw_band(px, frame)
    specs = [
        (9.0, 24 + (4 if frame % 2 else 0), 10.0, -0.9, 11),
        (23.0, 18 + (6 if frame in (1, 2) else 0), 8.5, 0.7, 29),
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
