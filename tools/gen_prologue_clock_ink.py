# -*- coding: utf-8 -*-
"""衍的处决「时钟」：**亮墨**漩涡阵 + 毛笔指针（作者 2026-11-15 第二版）。

作者原话：
    第一版「把那个时钟换成我们之前水墨法阵，旋转，然后加上分针时针啥的」
    第二版「时针分针一点水墨感都没有，然后不用这个白色圆盘」

所以这一版删掉宣纸圆盘 ✗，改成**夜空里的亮墨**：
    · `ink_clock_seal.png`          —— 把墨系那张漩涡阵**反相**（黑墨 → 月白），512×512 ✓
    · `ink_clock_hand_hour.png`     —— 时针：短、粗、笔锋收尖 ✓
    · `ink_clock_hand_minute.png`   —— 分针：长、细、笔锋更利 ✓

为什么必须反相：原图是**黑墨** ✗ —— 处决画面本来就暗，黑墨贴上去等于没画 ✗（第一版垫了张白纸，
但作者不要白盘 ✗）⇒ 唯一的办法是把墨本身做成"亮墨"（月白偏冷 ✓，像夜色里洇开的墨 ✓）。

为什么指针要单独出图：`gui.fill` 画的是**矩形** ✗ —— 直边、方头、没有粗细变化，
一点毛笔的感觉都没有 ✗（作者第二版的原话）。毛笔笔锋要有：起笔粗 → 收笔尖、边缘不齐、几道干笔飞白 ✓。
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(REPO, "src/main/resources/assets/tnc/textures/gui")
ENTITY = os.path.join(REPO, "src/main/resources/assets/tnc/textures/entity")


def invert_to_light_ink(size=512):
    """把墨系漩涡阵变成"亮墨"✓：**墨越黑的地方越亮、纸白的地方直接透明** ✓。

    只反相 RGB 是不够的 ✗：原图的亮部会变成**黑块** ⇒ 盖在暗处决画面上就是几坨黑斑 ✗。
    所以 alpha 也按"原来的暗度"给 ⇒ 只有真正的墨迹才发光 ✓，纸面全透明 ✓。
    """
    src = Image.open(os.path.join(ENTITY, "ink_vortex_seal.png")).convert("RGBA")
    src = src.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size))
    src_px, out_px = src.load(), out.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = src_px[x, y]
            if a == 0:
                out_px[x, y] = (0, 0, 0, 0)
                continue
            luma = 0.299 * r + 0.587 * g + 0.114 * b
            ink = max(0.0, min(1.0, 1.0 - luma / 255.0))
            # 亮墨的颜色：月白偏冷，墨越浓越亮 ✓
            cr = int(196 + 59 * ink)
            cg = int(206 + 49 * ink)
            cb = int(200 + 40 * ink)
            out_px[x, y] = (cr, cg, cb, int(a * ink ** 0.85))
    out = out.filter(ImageFilter.GaussianBlur(0.4))
    path = os.path.join(GUI, "ink_clock_seal.png")
    out.save(path)
    print("wrote", os.path.basename(path), out.size)


def brush_hand(name, width, length, taper, bristles, seed):
    """一支毛笔笔锋 ✓：起笔粗、收笔尖、边缘不齐、几道干笔飞白。

    坐标：图的下端是**转轴**（指针原点），上端是**笔尖** ✓ —— 渲染时绕图的下端中点旋转。
    """
    random.seed(seed)
    img = Image.new("RGBA", (width, length + 12), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def half_width(t):
        # 起笔略粗、中段最粗、收笔迅速收尖 ✓（毛笔不是等宽矩形 ✗）
        if t < 0.12:
            return width * 0.30 * (0.55 + t / 0.12 * 0.45)
        return width * 0.30 * (1.0 - taper * ((t - 0.12) / 0.88) ** 1.6)

    # ① 笔身：一条"中间略鼓、边缘带抖动"的带状多边形 ✓
    left, right = [], []
    for i in range(length + 1):
        t = i / length
        cx = width / 2.0 + math.sin(t * 2.4 + seed) * width * 0.045   # 极轻的弧度 ✓
        w = max(0.6, half_width(t) * random.uniform(0.86, 1.06))       # 边缘不齐 ✓
        right.append((cx + w, length + 6 - i))                        # 从下往上画
        left.append((cx - w, length + 6 - i))
    polygon = right + left[::-1]
    # 月白偏冷（和阵法同色系 ✓）；alpha 由笔锋决定 ✓
    draw.polygon(polygon, fill=(236, 240, 226, 236))

    # ② 飞白：随机几道**更淡**的横向断口 ⇒ 干笔的感觉 ✓
    for _ in range(bristles):
        by = random.uniform(length * 0.15, length * 0.92)
        bt = 1.0 - by / length
        bw = half_width(bt) * random.uniform(0.5, 1.1)
        bx = width / 2.0 + random.uniform(-width * 0.12, width * 0.12)
        draw.line([bx - bw, length + 6 - by, bx + bw, length + 6 - by],
                  fill=(236, 240, 226, random.randint(20, 70)), width=random.randint(1, 2))

    # ③ 笔尖：最后 4px 再收一下，避免"方头"✗
    tip = Image.new("RGBA", (width, 14), (0, 0, 0, 0))
    ImageDraw.Draw(tip).polygon(
        [(width / 2 - 1.6, 14), (width / 2 + 1.6, 14), (width / 2, 0)],
        fill=(236, 240, 226, 210))
    img.alpha_composite(tip, (0, 0))

    # ④ 一点点墨晕（湿笔的边缘 ✓）
    glow = img.filter(ImageFilter.GaussianBlur(2.2))
    glow.putalpha(glow.getchannel("A").point(lambda a: int(a * 0.35)))
    img = Image.alpha_composite(glow, img)

    path = os.path.join(GUI, name)
    img.save(path)
    print("wrote", os.path.basename(path), img.size)


def main():
    os.makedirs(GUI, exist_ok=True)
    invert_to_light_ink()
    brush_hand("ink_clock_hand_hour.png", 40, 150, taper=0.86, bristles=7, seed=11)
    brush_hand("ink_clock_hand_minute.png", 30, 250, taper=0.92, bristles=9, seed=29)


if __name__ == "__main__":
    main()
