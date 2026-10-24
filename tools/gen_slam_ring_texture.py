"""生成重踏火环的环带贴图（像素风火焰，水平无缝平铺）。

用法：python tools/gen_slam_ring_texture.py
输出：src/main/resources/assets/tnc/textures/entity/volcanic_tortoise_slam_ring.png
不触碰任何模型、动画或既有贴图。
"""
import math
import os
from PIL import Image

W, H = 512, 128
OUT = os.path.join("src", "main", "resources", "assets", "tnc",
                   "textures", "entity", "volcanic_tortoise_slam_ring.png")


def noise(x, seed):
    """两层周期噪声：所有频率都是整数，保证水平方向首尾相接。"""
    v = 0.0
    v += math.sin(2 * math.pi * (3 * x) + seed) * 0.50
    v += math.sin(2 * math.pi * (7 * x) + seed * 2.3) * 0.28
    v += math.sin(2 * math.pi * (13 * x) + seed * 3.7) * 0.14
    v += math.sin(2 * math.pi * (23 * x) + seed * 5.1) * 0.08
    return v


def ramp(t):
    """底部近黄白 -> 橙黄 -> 橙红 -> 暗红顶。"""
    stops = [
        (0.00, (255, 250, 214)),
        (0.22, (255, 226, 150)),
        (0.45, (255, 168, 62)),
        (0.68, (243, 112, 26)),
        (0.86, (196, 58, 14)),
        (1.00, (120, 20, 6)),
    ]
    for i in range(len(stops) - 1):
        a, ca = stops[i]
        b, cb = stops[i + 1]
        if a <= t <= b:
            k = (t - a) / (b - a)
            return tuple(int(ca[j] + (cb[j] - ca[j]) * k) for j in range(3))
    return stops[-1][1]


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()
for x in range(W):
    u = x / W
    # 火舌高度：低频起伏 + 高频锯齿，量化成像素台阶
    prof = 0.62 + 0.24 * noise(u, 1.7) + 0.10 * noise(u, 9.1)
    prof = max(0.28, min(1.0, prof))
    prof = round(prof * 32) / 32
    wisp = noise(u, 4.3) * 0.5 + 0.5
    for y in range(H):
        # y=0 是环带底部，y=1 是火舌顶端
        v = 1.0 - y / H
        if v > prof:
            continue
        t = v / max(0.05, prof)
        r, g, b = ramp(t)
        # 竖向流动条纹 + 火舌尖端的透明度
        streak = 0.78 + 0.30 * (0.5 + 0.5 * noise(u * 1.7 + t * 0.35, 12.0 + t * 2.0))
        r = min(255, int(r * streak))
        g = min(255, int(g * streak))
        b = min(255, int(b * streak))
        a = 255
        edge = prof - v
        if edge < 0.22:
            a = int(255 * (edge / 0.22) ** 0.75)
        a = int(a * (0.75 + 0.25 * wisp))
        px[x, y] = (r, g, b, a)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", OUT, img.size)
