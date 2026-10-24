"""生成火球/蓄力用的 4 帧像素火团贴图（32x32）。

只写这 4 张图，不动任何已有贴图：
  src/main/resources/assets/tnc/textures/particle/fire_blob_0..3.png

火团是径向渐变的“胖火苗”：中心近白黄、外圈橙、边缘深橙红，
逐帧轻微起伏 + 噪点，播放起来像在翻滚。粒子按三层取色，叠成球体。
"""

import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/tnc/textures/particle")

SIZE = 32
CORE = (255, 246, 198)
GOLD = (255, 206, 96)
ORANGE = (246, 138, 40)
DEEP = (206, 74, 24)
EDGE = (150, 40, 18)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def frame(seed):
    random.seed(seed)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    cx = cy = (SIZE - 1) / 2.0
    # 每帧火焰的重心与半径轻微摆动
    dx = (seed % 3 - 1) * 0.9
    dy = ((seed * 7) % 3 - 1) * 0.9
    radius = SIZE * 0.46
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx - dx) ** 2 + (y - cy - dy) ** 2) ** 0.5 / radius
            if d > 1.0:
                continue
            # 径向调色：核心 -> 金 -> 橙 -> 深红
            if d < 0.35:
                col = lerp(CORE, GOLD, d / 0.35)
            elif d < 0.62:
                col = lerp(GOLD, ORANGE, (d - 0.35) / 0.27)
            else:
                col = lerp(ORANGE, DEEP, (d - 0.62) / 0.38)
            n = random.randint(-16, 16)
            if random.random() < 0.05:
                n += 24
            r = max(0, min(255, col[0] + n))
            g = max(0, min(255, col[1] + n))
            b = max(0, min(255, col[2] + n))
            # 边缘一像素做半透明，避免方块感
            alpha = 255 if d < 0.86 else int(255 * (1.0 - (d - 0.86) / 0.14))
            px[x, y] = (r, g, b, max(0, alpha))
    # 几缕向外飘的火舌，让轮廓不是完美圆形
    draw = ImageDraw.Draw(img)
    for i in range(5):
        ang = random.random() * 6.283
        r0 = radius * 0.75
        r1 = radius * (1.02 + random.random() * 0.22)
        x0 = cx + dx + r0 * __import__("math").cos(ang)
        y0 = cy + dy + r0 * __import__("math").sin(ang)
        x1 = cx + dx + r1 * __import__("math").cos(ang)
        y1 = cy + dy + r1 * __import__("math").sin(ang)
        draw.line([x0, y0, x1, y1], fill=EDGE + (170,), width=1)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for i in range(4):
        path = os.path.join(OUT_DIR, "fire_blob_%d.png" % i)
        frame(i + 11).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
