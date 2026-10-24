# -*- coding: utf-8 -*-
"""生成重踏火环的火焰粒子贴图（4 帧、32x32、透明背景）。

只写 assets/tnc/textures/particle/slam_flame_0..3.png，不动任何手工模型/贴图。
颜色分区：底部近白黄芯 → 橙黄主体 → 红橙外缘；形状是三束高低不一的火舌，
逐帧轻微摆动，形成翻卷感。不使用参考图，也不带白色背景。
"""
import math
import os
from PIL import Image

W = H = 32
OUT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle")

# 火舌束：(中心 x, 半宽, 高度, 相位)
TONGUES = [(16.0, 9.5, 27.0, 0.0), (8.5, 6.0, 21.0, 1.7), (23.5, 6.5, 23.0, 3.1)]


def tongue_height(x, frame):
    """返回该像素列的火舌顶端高度（以行计，从底部算）。"""
    best = 0.0
    for cx, half, tall, phase in TONGUES:
        d = abs(x - cx) / half
        if d > 1.15:
            continue
        shape = math.cos(d * 1.35) ** 1.4
        wave = 0.55 * math.sin(frame * 0.9 + phase) + 0.35 * math.sin(frame * 1.7 + x * 0.55)
        h = tall * shape + wave * shape
        best = max(best, h)
    return best


def color_at(t):
    """t: 0=底部, 1=顶端。返回 (r,g,b,a)。"""
    if t < 0.30:
        k = t / 0.30
        r, g, b = 255, int(248 - 18 * k), int(214 - 60 * k)
        a = 255
    elif t < 0.62:
        k = (t - 0.30) / 0.32
        r, g, b = 255, int(210 - 70 * k), int(120 - 70 * k)
        a = 255
    elif t < 0.86:
        k = (t - 0.62) / 0.24
        r, g, b = int(250 - 40 * k), int(120 - 60 * k), int(50 - 25 * k)
        a = int(245 - 70 * k)
    else:
        k = (t - 0.86) / 0.14
        r, g, b = int(210 - 50 * k), int(60 - 25 * k), int(25 - 10 * k)
        a = int(175 - 150 * k)
    return r, g, b, max(0, min(255, a))


def main():
    os.makedirs(OUT, exist_ok=True)
    for frame in range(4):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        px = img.load()
        for x in range(W):
            h = tongue_height(x + 0.5, frame)
            if h <= 1.0:
                continue
            for y in range(H):
                # 距底部的高度（行 31 是底部）
                up = (H - 1) - y
                if up > h:
                    continue
                t = up / h
                # 边缘横向羽化：靠近火舌外缘时透明度降低
                edge = 1.0
                for cx, half, tall, phase in TONGUES:
                    d = abs(x + 0.5 - cx) / half
                    if d <= 1.15:
                        edge = max(edge, 1.0 - max(0.0, (t - 0.72)) * 2.2 * d)
                r, g, b, a = color_at(t)
                a = int(a * max(0.0, min(1.0, edge)))
                if a > 3:
                    px[x, y] = (r, g, b, a)
        img.save(os.path.join(OUT, "slam_flame_%d.png" % frame))
    print("wrote 4 frames to", OUT)


if __name__ == "__main__":
    main()
