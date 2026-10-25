"""生成火山巨龟 Boss 血条的 HUD 贴图（纯像素风，透明背景）。

输出到 src/main/resources/assets/tnc/textures/gui/bossbar/：
  frame.png    186x12  黑灰火山岩边框（含裂纹、不规则外轮廓）
  lava.png      16x8   可横向平铺的熔岩条（深橙红底 + 橙/橙黄纹 + 少量黄高光）
  cooled.png    16x8   已损失部分的冷却岩槽（很暗）
  volcano.png   34x26  血条上方的小火山（山体 + 火山口 + 熔岩裂隙）

坐标约定：MC GUI 逻辑像素；贴图用最近邻采样，alpha 只有 0 或 255。
"""

import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/tnc/textures/gui/bossbar"

ROCK_DARK = (34, 30, 28, 255)
ROCK_MID = (58, 52, 48, 255)
ROCK_LIGHT = (82, 74, 68, 255)
CRACK = (16, 13, 12, 255)
LAVA_DEEP = (84, 18, 10, 255)
LAVA_RED = (176, 42, 16, 255)
LAVA_ORANGE = (232, 104, 24, 255)
LAVA_GOLD = (250, 164, 48, 255)
LAVA_YELLOW = (255, 226, 140, 255)
COOL_DARK = (26, 22, 20, 255)
COOL_MID = (44, 38, 34, 255)


def bar_frame():
    w, h = 186, 12
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    # 3px 岩石边框：外圈最暗、中圈主色、内圈稍亮，读起来像粗糙岩框
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                px[x, y] = ROCK_DARK
            elif edge == 1:
                px[x, y] = ROCK_MID if (x * 3 + y) % 5 else ROCK_LIGHT
            elif edge == 2:
                px[x, y] = ROCK_DARK if (x + y * 2) % 4 else ROCK_MID
    # 顶边高光，像熔岩从上方照下来
    for x in range(3, w - 3):
        if px[x, 1][3]:
            px[x, 1] = ROCK_LIGHT if x % 9 else ROCK_MID
    # 外轮廓：四角切 1px，边框上偶尔凸起/缺口，避免“金属直边”
    for x, y in [(0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1),
                 (0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)]:
        px[x, y] = (0, 0, 0, 0)
    for x in (24, 25, 92, 93, 149, 150):
        px[x, 0] = ROCK_MID
        px[x, h - 1] = ROCK_MID
    # 裂纹：只走边框（edge <= 2），绝不进内容区
    for x, y in [(10, 2), (11, 3), (12, 3), (31, 9), (32, 8), (55, 1), (56, 2),
                 (88, 10), (89, 9), (119, 2), (120, 3), (150, 9), (151, 8), (167, 1), (168, 2)]:
        if 0 <= x < w and 0 <= y < h and px[x, y][3] and min(x, y, w - 1 - x, h - 1 - y) <= 2:
            px[x, y] = CRACK
    img.save(OUT / "frame.png")
    print("frame", img.size)


def lava_tile():
    """横向可平铺：左右边界像素在周期上连续。"""
    w, h = 16, 8
    img = Image.new("RGBA", (w, h), LAVA_DEEP)
    px = img.load()
    for x in range(w):
        # 两条缓慢起伏的熔岩脉，横向 16px 一个周期
        vein_a = 2.2 + 1.6 * math.sin(2 * math.pi * x / w)
        vein_b = 5.6 + 1.3 * math.sin(2 * math.pi * x / w + 2.1)
        for y in range(h):
            d = min(abs(y - vein_a), abs(y - vein_b))
            if d < 0.6:
                px[x, y] = LAVA_GOLD
            elif d < 1.2:
                px[x, y] = LAVA_ORANGE
            elif d < 2.0:
                px[x, y] = LAVA_RED
            else:
                px[x, y] = LAVA_DEEP
    # 少量黄色高光点（整条 16px 只有 2 个）
    for x, y in [(4, 2), (12, 6)]:
        px[x, y] = LAVA_YELLOW
    img.save(OUT / "lava.png")
    print("lava", img.size)


def cooled_tile():
    w, h = 16, 8
    img = Image.new("RGBA", (w, h), COOL_DARK)
    px = img.load()
    for x in range(w):
        for y in range(h):
            if (x * 3 + y * 5) % 11 == 0:
                px[x, y] = COOL_MID
    # 底部一条更暗的接缝，和熔岩部分对比更清楚
    for x in range(w):
        px[x, h - 1] = (18, 15, 13, 255)
    img.save(OUT / "cooled.png")
    print("cooled", img.size)


def volcano():
    """34x26 的小火山：锥形山体 + 顶部火山口 + 两条熔岩裂隙。"""
    w, h = 34, 26
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    cx = 16.5

    # 锥形高度：中心最高（y=4），向两侧落到 y=25
    def top_at(x):
        t = abs(x - cx) / 17.0
        return int(round(4 + (t ** 1.35) * 21))

    for x in range(w):
        top = top_at(x)
        for y in range(top, h):
            if x in (int(cx - 17), int(cx + 17)):
                continue
            # 山体：主色 + 少量亮面/暗面，形成斜向纹理
            lit = (x - y) % 7 == 0
            dark = (x + y) % 9 == 0
            px[x, y] = ROCK_LIGHT if lit else (ROCK_DARK if dark else ROCK_MID)
        # 轮廓线
        if 0 <= top < h:
            px[x, top] = ROCK_DARK

    # 火山口：中心挖出一个 9px 宽的凹口
    for x in range(12, 22):
        cut = 3 if abs(x - cx) < 2 else (2 if abs(x - cx) < 4 else 1)
        for y in range(4, 4 + cut):
            px[x, y] = (0, 0, 0, 0)
        for y in range(4 + cut, 4 + cut + 3):
            px[x, y] = LAVA_DEEP
        px[x, 4 + cut + 3] = LAVA_RED if abs(x - cx) > 2 else LAVA_GOLD
    # 口内亮部
    for x in range(14, 20):
        px[x, 6] = LAVA_ORANGE
    for x in range(15, 18):
        px[x, 6] = LAVA_GOLD

    # 熔岩裂隙：从火山口向左右下侧分叉
    for i, (x, y) in enumerate([(11, 9), (10, 10), (10, 11), (9, 12), (9, 13),
                                (22, 9), (23, 10), (23, 11), (24, 12), (24, 13), (25, 14)]):
        if 0 <= x < w:
            px[x, y] = LAVA_ORANGE if i % 3 else LAVA_RED
    # 山脚压暗，和血条接缝更自然
    for x in range(2, w - 2):
        if px[x, h - 1][3]:
            px[x, h - 1] = ROCK_DARK
    # 火山口上方一点灰烟（不用纯白）
    for x, y in [(16, 2), (17, 2), (16, 3), (18, 3)]:
        px[x, y] = (116, 108, 104, 255)

    img.save(OUT / "volcano.png")
    print("volcano", img.size)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    bar_frame()
    lava_tile()
    cooled_tile()
    volcano()
