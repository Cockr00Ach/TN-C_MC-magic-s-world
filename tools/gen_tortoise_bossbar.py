"""生成火山巨龟 Boss 血条的熔岩填充贴图（岩框与火山由 build_bossbar_art.py 从参考图生成）。

输出到 src/main/resources/assets/tnc/textures/gui/bossbar/：
  frame.png    186x14  黑岩边框（不规则外轮廓、内侧熔岩泛光）
  lava.png      16x10  亮熔岩填充（红橙底 + 黄芯 + 深色岩块）
  cooled.png    16x10  已损失部分的冷却岩槽（很暗）
  volcano.png   40x34  血条中央上方的小火山（山体+火山口+熔岩流+灰烟）

坐标约定：MC GUI 逻辑像素；最近邻采样，alpha 只有 0 或 255。
"""

import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/tnc/textures/gui/bossbar"

ROCK_DARK = (26, 23, 21, 255)
ROCK_MID = (48, 42, 38, 255)
ROCK_LIGHT = (74, 66, 60, 255)
GLOW_DEEP = (86, 14, 10, 255)
GLOW_RED = (168, 26, 14, 255)
LAVA_DEEP = (96, 16, 8, 255)
LAVA_RED = (196, 34, 14, 255)
LAVA_ORANGE = (238, 84, 18, 255)
LAVA_GOLD = (252, 152, 36, 255)
LAVA_YELLOW = (255, 224, 120, 255)
COOL_DARK = (24, 20, 18, 255)
COOL_MID = (42, 36, 32, 255)

BAR_W, BAR_H = 182, 25
FRAME_W, FRAME_H = 186, 14


def bar_frame():
    img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    # 内侧留给血条（2px 边框 -> 182x10 内容区）
    for y in range(FRAME_H):
        for x in range(FRAME_W):
            edge = min(x, y, FRAME_W - 1 - x, FRAME_H - 1 - y)
            if edge == 0:
                px[x, y] = ROCK_DARK
            elif edge == 1:
                px[x, y] = ROCK_MID if (x * 7 + y * 3) % 6 else ROCK_LIGHT
            elif edge == 2:
                # 内侧熔岩泛光：靠近血条的一圈暗红，模拟被烤热的岩壁
                px[x, y] = GLOW_RED if (x + y) % 5 else GLOW_DEEP
    # 外轮廓做成不规则岩块：每 4~7 px 随机向外凸 1px 或向内缺 1px
    rng = random.Random(20261025)
    x = 0
    while x < FRAME_W:
        run = rng.randint(3, 7)
        bump = rng.choice([-1, 0, 0, 1])
        for i in range(run):
            xx = x + i
            if xx >= FRAME_W:
                break
            if bump > 0:
                px[xx, 0] = ROCK_MID
            elif bump < 0:
                px[xx, 0] = (0, 0, 0, 0)
            if bump > 0:
                px[xx, FRAME_H - 1] = ROCK_DARK
            elif bump < 0:
                px[xx, FRAME_H - 1] = (0, 0, 0, 0)
        x += run
    # 左右两端加厚，像包住血条的岩块
    for y in range(1, FRAME_H - 1):
        px[0, y] = ROCK_MID if y % 3 else ROCK_DARK
        px[FRAME_W - 1, y] = ROCK_MID if y % 3 else ROCK_DARK
    # 裂纹
    for cx, cy in [(15, 2), (16, 3), (17, 4), (48, 11), (49, 10), (88, 2), (89, 3),
                   (120, 11), (121, 10), (152, 2), (153, 3), (170, 11)]:
        if 0 <= cx < FRAME_W and 0 <= cy < FRAME_H and px[cx, cy][3]:
            px[cx, cy] = (14, 11, 10, 255)
    img.save(OUT / "frame.png")
    print("frame", img.size)


def lava_tile():
    """横向可平铺：左右边界在周期上连续。"""
    img = Image.new("RGBA", (BAR_W, BAR_H), LAVA_DEEP)
    tile = Image.new("RGBA", (16, BAR_H), LAVA_DEEP)
    px = tile.load()
    for x in range(16):
        core = 4.6 + 1.5 * math.sin(2 * math.pi * x / 16)
        for y in range(BAR_H):
            d = abs(y - core)
            if d < 0.7:
                px[x, y] = LAVA_YELLOW
            elif d < 1.5:
                px[x, y] = LAVA_GOLD
            elif d < 2.6:
                px[x, y] = LAVA_ORANGE
            elif d < 3.6:
                px[x, y] = LAVA_RED
            else:
                px[x, y] = LAVA_DEEP
    # 深色岩块：熔岩里漂浮的冷却岩，带橙边
    for bx, by, bw, bh in [(2, 1, 3, 2), (9, 7, 3, 2), (12, 1, 2, 2)]:
        for yy in range(by, by + bh):
            for xx in range(bx, bx + bw):
                if 0 <= xx < 16 and 0 <= yy < BAR_H:
                    px[xx, yy] = ROCK_DARK if (xx + yy) % 3 else ROCK_MID
        for yy in range(by - 1, by + bh + 1):
            for xx in range(bx - 1, bx + bw + 1):
                if 0 <= xx < 16 and 0 <= yy < BAR_H and px[xx, yy][0] < 120:
                    px[xx, yy] = LAVA_RED
    # 平铺到 182 宽，保证左右接缝连续
    for x in range(BAR_W):
        for y in range(BAR_H):
            img.putpixel((x, y), px[x % 16, y])
    img.save(OUT / "lava.png")
    print("lava", img.size)


def cooled_tile():
    img = Image.new("RGBA", (BAR_W, BAR_H), COOL_DARK)
    px = img.load()
    for x in range(BAR_W):
        for y in range(BAR_H):
            if (x * 3 + y * 5) % 13 == 0:
                px[x, y] = COOL_MID
            if y == BAR_H - 1:
                px[x, y] = (16, 13, 12, 255)
    img.save(OUT / "cooled.png")
    print("cooled", img.size)


def volcano():
    """40x34：中央火山，山脚向两侧摊开，熔岩从火山口沿坡流下。"""
    w, h = 40, 34
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    cx = (w - 1) / 2.0

    def top_at(x):
        t = abs(x - cx) / (w / 2.0)
        return int(round(3 + (t ** 1.25) * 26))

    for x in range(w):
        top = top_at(x)
        for y in range(top, h):
            lit = (x - y) % 7 == 0
            dark = (x + y) % 9 == 0
            px[x, y] = ROCK_LIGHT if lit else (ROCK_DARK if dark else ROCK_MID)
        px[x, top] = ROCK_DARK

    # 火山口凹陷
    for x in range(14, 26):
        cut = 3 if abs(x - cx) < 2 else (2 if abs(x - cx) < 4 else 1)
        for y in range(3, 3 + cut):
            px[x, y] = (0, 0, 0, 0)
        for y in range(3 + cut, 3 + cut + 3):
            px[x, y] = LAVA_DEEP
        px[x, 3 + cut + 3] = LAVA_GOLD if abs(x - cx) <= 2 else LAVA_RED
    for x in range(16, 24):
        px[x, 5] = LAVA_ORANGE
    for x in range(18, 22):
        px[x, 5] = LAVA_YELLOW

    # 熔岩流：从口沿沿两侧山坡往下，越往下越细
    for side in (-1, 1):
        for i in range(12):
            y = 8 + i
            x = int(round(cx + side * (2 + i * 0.75) + math.sin(i * 0.9) * 0.8))
            if 0 <= x < w and 0 <= y < h:
                px[x, y] = LAVA_ORANGE if i % 3 else LAVA_GOLD
                if i > 6 and 0 <= x + side < w:
                    px[x + side, y] = LAVA_RED

    # 山脚岩块压暗
    for x in range(w):
        for y in (h - 2, h - 1):
            if px[x, y][3] and px[x, y][0] < 90:
                px[x, y] = ROCK_DARK

    # 火山口上方灰烟（不用纯白）
    for x, y in [(19, 1), (20, 1), (20, 2), (21, 2), (19, 2)]:
        px[x, y] = (120, 112, 108, 255)

    img.save(OUT / "volcano.png")
    print("volcano", img.size)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    lava_tile()
    cooled_tile()
