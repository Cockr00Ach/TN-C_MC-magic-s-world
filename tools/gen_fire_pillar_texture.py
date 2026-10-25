"""生成火柱用的 4 帧像素火舌贴图（16x32，透明背景）。

只写 assets/tnc/textures/particle/fire_pillar_0..3.png，不覆盖任何其它资源。
火焰根部在图片底部，向上收窄、边缘锯齿、逐帧摆动与分叉。
"""
from PIL import Image, ImageDraw
import math
import os
import random

SIZE_W, SIZE_H = 16, 32
FRAMES = 4
OUT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle")

# 底部近白黄芯 → 金黄 → 橙 → 深橙红
STOPS = [
    (0.00, (255, 250, 224)),
    (0.18, (255, 228, 140)),
    (0.42, (255, 190, 64)),
    (0.68, (248, 140, 36)),
    (0.86, (214, 82, 24)),
    (1.00, (168, 52, 18)),
]


def color_at(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(STOPS) - 1):
        t0, c0 = STOPS[i]
        t1, c1 = STOPS[i + 1]
        if t <= t1:
            k = (t - t0) / max(1e-6, t1 - t0)
            return tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(3))
    return STOPS[-1][1]


def draw_frame(frame):
    img = Image.new("RGBA", (SIZE_W, SIZE_H), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(9001 + frame * 37)
    cx = SIZE_W / 2.0 - 0.5
    # 从底到顶：宽度先略扩再收窄，顶端分叉
    for y in range(SIZE_H):
        v = 1.0 - y / (SIZE_H - 1.0)          # 0 底部, 1 顶部
        half = 5.4 * (1.0 - v) ** 0.55 + 0.45  # 底部宽、顶部细
        sway = math.sin(v * 3.2 + frame * 1.35) * (0.9 + 1.4 * v)
        branch = 1.0
        if v > 0.62:
            branch = 0.55 + 0.45 * abs(math.sin(v * 7.0 + frame * 1.9))
        for x in range(SIZE_W):
            dx = abs((x - cx) - sway)
            if dx > half * branch:
                continue
            edge = dx / max(0.001, half * branch)
            if edge > 0.78 and rnd.random() < 0.35:
                continue
            t = 0.10 + 0.82 * v + 0.10 * edge
            r, g, b = color_at(t)
            a = 255
            if v > 0.80:
                a = int(255 * max(0.0, (1.0 - v) / 0.20))
            if edge > 0.86:
                a = int(a * 0.65)
            px[x, y] = (r, g, b, a)
    # 顶部两缕分叉的小火舌，避免所有帧一模一样的尖顶
    d = ImageDraw.Draw(img)
    for k in range(2):
        base_x = int(cx + (-3 + 6 * k) + math.sin(frame + k) * 1.5)
        top_y = 2 + (frame + k) % 3
        for i in range(6):
            yy = top_y + i
            if 0 <= yy < SIZE_H:
                xx = base_x + int(round(math.sin(i * 0.9 + frame) * 1.2))
                if 0 <= xx < SIZE_W and px[xx, yy][3] == 0:
                    r, g, b = color_at(0.75 + 0.05 * i)
                    px[xx, yy] = (r, g, b, max(0, 210 - i * 26))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in range(FRAMES):
        img = draw_frame(f)
        path = os.path.join(OUT, f"fire_pillar_{f}.png")
        img.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
