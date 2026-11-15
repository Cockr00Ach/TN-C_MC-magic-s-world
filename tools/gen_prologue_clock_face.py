# -*- coding: utf-8 -*-
"""衍的处决演出里那个"时钟"的纸面（作者 2026-11-15 要求把时钟换成水墨法阵）。

作者原话：
    「玩家被衍处决了之后不是屏幕会出现时钟吗，你把那个时钟换成就是我们之前水墨法阵，旋转，
      然后给那个水墨法阵加上分针时针啥的」

所以表盘 = 我们自己的 `textures/entity/ink_vortex_seal.png`（水墨漩涡阵 ✓，代码里旋转 ✓），
**指针**画在水墨阵上面 ✓。但那张水墨是**黑墨** ✗ —— 直接盖在昏暗的处决画面里会看不见 ✗，
所以这一张生成的是它下面那张**宣纸圆盘**：

    · 暖白纸色、边缘一圈软毛边（不是硬边圆 ✗）
    · 一点点纸纹噪点 + 几处淡墨渍（让它像纸，不像一块塑料盘 ✗）
    · 一圈很淡的墨线内框 + 12 个极淡的时辰点（指针要有参照物才读得出角度 ✓）

输出：assets/tnc/textures/gui/prologue_clock_paper.png（512×512）
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src/main/resources/assets/tnc/textures/gui")

SIZE = 512
CENTER = SIZE / 2.0
RADIUS = SIZE * 0.492


def main():
    random.seed(20261115)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # ① 纸面：由内到外一点点变暖、变暗（圆盘边缘压暗 ⇒ 有"纸的厚度"✓）
    pixels = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTER, y + 0.5 - CENTER
            d = math.hypot(dx, dy)
            if d > RADIUS:
                continue
            edge = min(1.0, (RADIUS - d) / 10.0)              # 边缘 10px 软化 ✓
            shade = 1.0 - 0.16 * (d / RADIUS) ** 2             # 外圈暗一点 ✓
            grain = random.uniform(-6, 6)                      # 纸纹
            r = min(255, max(0, int((238 * shade) + grain)))
            g = min(255, max(0, int((230 * shade) + grain)))
            b = min(255, max(0, int((212 * shade) + grain * 0.8)))
            pixels[x, y] = (r, g, b, int(235 * edge))

    # ② 淡墨渍：几团非常淡的墨，随机摊在纸上 ✓（"纸"和"塑料"的区别就在这几团）
    #    ⚠️ 必须画在**单独的图层**再 alpha_composite ✗ ——
    #    直接往 RGBA 图上 draw 是**覆盖**不是混合（第一版画出来是几坨实心大黑点 ✗）；
    #    而且合成之后 `ImageDraw` 还指着**旧图**✗ ⇒ 后面的框线和刻度全画丢了 ✗
    #    （所以画线的 draw 一定要在这一步**之后**重新建 ✓）。
    stain = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(stain)
    for _ in range(7):
        cx = CENTER + random.uniform(-RADIUS * 0.72, RADIUS * 0.72)
        cy = CENTER + random.uniform(-RADIUS * 0.72, RADIUS * 0.72)
        rad = random.uniform(RADIUS * 0.05, RADIUS * 0.16)
        sdraw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad],
                      fill=(58, 50, 42, random.randint(10, 22)))
    stain = stain.filter(ImageFilter.GaussianBlur(9))
    # 只在纸盘范围内显影 ✓（盘外不能有墨渍 ✗）
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).ellipse([CENTER - RADIUS, CENTER - RADIUS, CENTER + RADIUS, CENTER + RADIUS], fill=255)
    stain.putalpha(Image.composite(stain.getchannel("A"), Image.new("L", (SIZE, SIZE), 0), mask))
    img = Image.alpha_composite(img, stain)

    draw = ImageDraw.Draw(img, "RGBA")

    # ③ 内框墨线 + 外框墨线（很淡 ✓）
    for rr, width, alpha in ((RADIUS * 0.985, 3, 90), (RADIUS * 0.90, 1, 60)):
        draw.ellipse([CENTER - rr, CENTER - rr, CENTER + rr, CENTER + rr],
                     outline=(48, 42, 36, alpha), width=width)

    # ④ 12 个时辰点：整点长、半点短 ✓（指针要有参照物 ✓）
    for i in range(12):
        angle = math.radians(i * 30 - 90)
        for length, width, alpha in ((18, 3, 120),):
            x0 = CENTER + math.cos(angle) * (RADIUS * 0.88 - length)
            y0 = CENTER + math.sin(angle) * (RADIUS * 0.88 - length)
            x1 = CENTER + math.cos(angle) * (RADIUS * 0.88)
            y1 = CENTER + math.sin(angle) * (RADIUS * 0.88)
            draw.line([x0, y0, x1, y1], fill=(40, 34, 30, alpha), width=width)
    for i in range(60):
        if i % 5 == 0:
            continue
        angle = math.radians(i * 6 - 90)
        x0 = CENTER + math.cos(angle) * (RADIUS * 0.88 - 7)
        y0 = CENTER + math.sin(angle) * (RADIUS * 0.88 - 7)
        x1 = CENTER + math.cos(angle) * (RADIUS * 0.88)
        y1 = CENTER + math.sin(angle) * (RADIUS * 0.88)
        draw.line([x0, y0, x1, y1], fill=(40, 34, 30, 55), width=1)

    # ⑤ 一点点柔光：整张纸做一次轻微模糊，去掉"像素感"✓
    img = img.filter(ImageFilter.GaussianBlur(0.6))

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "prologue_clock_paper.png")
    img.save(path)
    print("wrote", os.path.basename(path), img.size)


if __name__ == "__main__":
    main()
