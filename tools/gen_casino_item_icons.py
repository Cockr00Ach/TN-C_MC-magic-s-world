#!/usr/bin/env python3
"""给三台赌场机器画**物品图标**（32x32 PNG）＋ 生成对应的 item 模型。

作者 2026-10-08：「物品都缺失了」。

为什么物品会"缺失"（上一版我修错了 ✗）：
  上一版把三个 item 模型指向了一个 **空方块模型**（`elements: []` ✓），
  本意是"至少别是紫黑方块" ✓ —— 但**空模型在物品栏里什么都不画** ✗
  ⇒ 创造页里那三个格子是**透明**的，看起来就是"物品缺失" ✓。
  （顺带一提：`minecraft:block/block` 那个父模型本身也是空的 ✓，指望不上 ✓。）

⇒ 正确做法是给每个物品**一张真图标** ✓（`item/generated` 平面贴图 ✓）——
  方块实体自绘那套**只在世界里**生效 ✗，物品栏走的是普通物品模型 ✓，两者互不相干 ✓。

图形是**照着机器自己的配色画的**（CasinoPalette ✓）：
  * 老虎机：红漆机身 ＋ 黄铜压边 ＋ 三只彩色滚轮 ＋ 结果灯 ＋ 红球拉杆
  * 转盘赌桌：绿呢台面 ＋ 木框 ＋ 转盘（红黑扇区）＋ 白球 ＋ 两颗骰子
  * 赌博桌：绿呢台面 ＋ 木框 ＋ 两张牌 ＋ 两颗骰子 ＋ 三摞筹码

⚠️ 这些是**我画的占位图标**，不是最终美术 ✓ —— 作者要改就换掉 PNG 就行 ✓。
⚠️ 本脚本零第三方依赖（纯 zlib + struct ✓），和仓库里其它生成器一样 ✓。

用法：
    python tools/gen_casino_item_icons.py
"""

import json
import os
import struct
import sys
import zlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc")
SIZE = 32

# Read the runtime palette so regenerated icons stay in the same furniture family.
import re
from pathlib import Path
palette_source=(Path(REPO)/'src/main/java/com/tnc/tnc/casino/client/CasinoPalette.java').read_text(encoding='utf-8')
for name,hex_value in re.findall(r'public static final int (\w+)\s*=\s*(0x[0-9A-Fa-f]+);',palette_source):
    rgb=int(hex_value,16);globals()[name]=tuple((rgb>>shift)&255 for shift in (16,8,0))
SYMBOL=[tuple((int(h,16)>>shift)&255 for shift in (16,8,0)) for h in re.findall(r'0x[0-9A-Fa-f]+',palette_source.split('int[] SYMBOL = {')[1].split('};')[0])]
WHITE=DICE;FELT_DARK=tuple(int(c*.72) for c in FELT);ROUGE=CHIP_RED;NOIR=PIP;GREEN_POCKET=FELT;LEVER_BALL=CHIP_RED

TRANSPARENT = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w=SIZE, h=SIZE):
        self.w, self.h = w, h
        self.px = [TRANSPARENT] * (w * h)

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[y * self.w + x] = c if len(c) == 4 else (c[0], c[1], c[2], 255)

    def rect(self, x0, y0, x1, y1, c):
        """闭区间填充 ✓（含 x1/y1）。"""
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.put(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        self.rect(x0, y0, x1, y0, c)
        self.rect(x0, y1, x1, y1, c)
        self.rect(x0, y0, x0, y1, c)
        self.rect(x1, y0, x1, y1, c)

    def disc(self, cx, cy, r, c):
        rr = r * r
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rr:
                    self.put(x, y, c)

    def ring(self, cx, cy, r, c):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                d = (x - cx) ** 2 + (y - cy) ** 2
                if (r - 0.9) ** 2 <= d <= r * r:
                    self.put(x, y, c)

    def png(self):
        rows = []
        for y in range(self.h):
            row = bytearray([0])
            for x in range(self.w):
                r, g, b, a = self.px[y * self.w + x]
                row += bytes((r, g, b, a))
            rows.append(bytes(row))
        return _png(self.w, self.h, b"".join(rows))


def _png(w, h, raw):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------------------
#  三个图标
# ---------------------------------------------------------------------------

def slot_machine():
    """正面视角的**立式**老虎机 ✓（红漆 ＋ 黄铜 ＋ 三只圆筒滚轮 ＋ 右侧红球拉杆 ✓）。

    ★ 2026-10-08 重画：作者要的是「高 2 宽 1 的那种经典老虎机」✓ ——
      第一版画成了**矮胖的方块** ✗，和"立式"完全不搭 ✓。
      现在按 1:2 的竖长比例画 ✓，机身只占中间 12 格宽 ✓。
    """
    c = Canvas()
    x0, x1 = 9, 22                      # 机身左右（12 格宽 ⇒ 竖长 ✓）
    # 机身
    c.rect(x0, 2, x1, 29, CABINET)
    c.rect(x0, 2, x1, 3, CABINET_TOP)          # 顶盖高光
    c.rect(x0, 28, x1, 29, CABINET_DARK)       # 底暗部
    c.rect(x0, 2, x0, 29, CABINET_TOP)         # 左侧受光
    c.rect(x1, 2, x1, 29, CABINET_DARK)        # 右侧背光
    # 顶部黄铜压边 ＋ 招牌
    c.rect(x0 - 1, 1, x1 + 1, 1, BRASS)
    c.rect(x0 + 1, 3, x1 - 1, 5, BRASS_DARK)
    # 三盏结果灯
    for i in range(3):
        lx = x0 + 2 + i * 4
        c.rect(lx, 3, lx + 2, 4, LAMP_ON if i != 1 else BRASS_DARK)
    # 滚轮窗（凹进去 ✓）
    c.rect(x0 + 1, 7, x1 - 1, 15, WELL)
    c.frame(x0 + 1, 7, x1 - 1, 15, BRASS_DARK)
    # ★ 三只**圆筒**滚轮：用圆角＋明暗表达"筒"，不是方块 ✓
    for i in range(3):
        cx = x0 + 2 + i * 4          # 筒左上角
        c.rect(cx, 8, cx + 3, 14, DRUM)                  # 筒身
        c.rect(cx, 8, cx + 3, 8, (0xDA, 0xD0, 0xB8))     # 上缘暗
        c.rect(cx + 1, 9, cx + 2, 13, SYMBOL[(i * 2) % len(SYMBOL)])  # 符号格
        c.rect(cx, 14, cx + 3, 14, (0xDA, 0xD0, 0xB8))   # 下缘暗
    # 中间的透明分格柱（三只筒是分开的 ✓）
    c.rect(x0 + 5, 7, x0 + 5, 15, BRASS_DARK)
    c.rect(x0 + 9, 7, x0 + 9, 15, BRASS_DARK)
    # 投币口
    c.rect(x0 + 4, 18, x1 - 4, 18, BRASS)
    c.rect(x0 + 5, 19, x1 - 5, 19, WELL)
    # 出币槽
    c.rect(x0 + 2, 24, x1 - 2, 27, WELL)
    c.frame(x0 + 2, 24, x1 - 2, 27, BRASS_DARK)
    # ★ 右侧红球拉杆（作者点名要的 ✓）
    c.rect(x1 + 1, 10, x1 + 1, 21, BRASS)      # 杆身
    c.disc(x1 + 1, 9, 2, LEVER_BALL)           # 红球头
    c.rect(x1 + 1, 21, x1 + 2, 22, IRON)       # 底座
    return c


def roulette_table():
    """俯视角的转盘赌桌 ✓（绿呢 ＋ 木框 ＋ 转盘 ＋ 白球 ＋ 两颗骰子 ✓）。"""
    c = Canvas()
    # 木框（8..23 的方台 ✓）
    c.rect(4, 6, 27, 25, WOOD)
    c.rect(4, 6, 27, 6, WOOD_TOP)
    c.rect(4, 25, 27, 25, WOOD_DARK)
    c.frame(4, 6, 27, 25, WOOD_DARK)
    # 台呢
    c.rect(6, 8, 25, 23, FELT)
    c.frame(6, 8, 25, 23, FELT_DARK)
    # 转盘（左半边 ✓）—— 用角度算扇区颜色 ✓
    cx, cy, r = 13.0, 15.5, 6.4
    for y in range(8, 24):
        for x in range(5, 23):
            dx, dy = x - cx, y - cy
            d2 = dx * dx + dy * dy
            if d2 <= r * r:
                if d2 >= (r - 1.0) ** 2:
                    c.put(x, y, BRASS_DARK)              # 外圈黄铜
                else:
                    import math
                    ang = math.degrees(math.atan2(dy, dx)) % 360.0
                    pocket = int(ang / 30.0) % 12
                    c.put(x, y, GREEN_POCKET if pocket == 0
                          else (ROUGE if pocket % 2 == 0 else NOIR))
    c.disc(cx, cy, 1.4, BRASS)                            # 盘心
    c.disc(cx + 4, cy - 4, 1.0, WHITE)                    # 小球
    # Physical red / green-zero / black buttons on the enlarged table.
    for y,color in ((10,CHIP_RED),(15,FELT),(20,PIP)):
        c.rect(21,y,25,y+3,color)
        c.frame(21,y,25,y+3,BRASS)
    return c


def gamble_table():
    """俯视角的 4x2 赌博桌 ✓（绿呢 ＋ 两张牌 ＋ 两颗骰子 ＋ 三摞筹码 ✓）。"""
    c = Canvas()
    # 木框
    c.rect(2, 6, 29, 25, WOOD)
    c.rect(2, 6, 29, 6, WOOD_TOP)
    c.rect(2, 25, 29, 25, WOOD_DARK)
    c.frame(2, 6, 29, 25, WOOD_DARK)
    # 台呢
    c.rect(4, 8, 27, 23, FELT)
    c.frame(4, 8, 27, 23, FELT_DARK)
    # 两张牌（斜着搭 ✓ 用错开的矩形示意 ✓）
    c.rect(7, 11, 14, 17, CARD_FACE)
    c.frame(7, 11, 14, 17, (0xC9, 0xC2, 0xB2))
    c.put(9, 13, (0xC0, 0x39, 0x2B))                      # 红桃/红方块的点
    c.rect(11, 14, 18, 20, CARD_BACK)
    c.frame(11, 14, 18, 20, (0x28, 0x3E, 0x54))
    # 两颗骰子
    for dx0, dy0, pips in ((20, 10, 2), (24, 14, 3)):
        c.rect(dx0, dy0, dx0 + 4, dy0 + 4, WHITE)
        c.frame(dx0, dy0, dx0 + 4, dy0 + 4, (0xC9, 0xC2, 0xB2))
        spots = {2: [(1, 1), (3, 3)], 3: [(1, 1), (2, 2), (3, 3)]}[pips]
        for px, py in spots:
            c.put(dx0 + px, dy0 + py, PIP)
    # 三摞筹码
    for i, (cx0, col) in enumerate(((8, CHIP_RED), (13, CHIP_BLUE), (18, CHIP_WHITE))):
        c.rect(cx0, 20, cx0 + 3, 22, col)
        c.rect(cx0, 20, cx0 + 3, 20, tuple(min(255, v + 30) for v in col))
    return c


ICONS = [
    ("slot_machine", "老虎机", slot_machine),
    ("roulette_table", "转盘赌桌", roulette_table),
    ("gamble_table", "赌博桌", gamble_table),
]


def main():
    tex_dir = os.path.join(ASSETS, "textures", "item")
    model_dir = os.path.join(ASSETS, "models", "item")
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)

    for machine_id, cn, draw in ICONS:
        png_path = os.path.join(tex_dir, "casino_" + machine_id + ".png")
        with open(png_path, "wb") as fh:
            fh.write(draw().png())

        # 平面物品图标 ✓（item/generated ✓，和绝大多数物品同款 ✓）
        model = {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": "tnc:item/casino_" + machine_id,
                "particle": "tnc:item/casino_" + machine_id,
            },
        }
        with open(os.path.join(model_dir, machine_id + ".json"), "w",
                  encoding="utf-8", newline="\n") as fh:
            json.dump(model, fh, ensure_ascii=False, indent=2)
            fh.write("\n")

        print("  %-16s %s  icon=%d bytes  (%s)"
              % (machine_id, "item/generated", os.path.getsize(png_path), cn))

    print("done: %d icons + %d item models" % (len(ICONS), len(ICONS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
