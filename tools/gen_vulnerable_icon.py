#!/usr/bin/env python3
"""易伤效果（tnc:vulnerable）的 HUD 图标 —— 18x18，写进 assets/tnc/textures/mob_effect/。

为什么要这一张：
  * 原版把效果图标当**图集**加载（assets/<ns>/textures/mob_effect/<id>.png）⇒
    **缺文件 = HUD 里一个紫黑方块** ✗；
  * 易伤不只挂怪 —— 它也会挂在**玩家（含队友）**身上（作者："其他玩家增加易伤"）⇒
    队友自己的 HUD 上就会看到这一格 ⇒ 这张图标必须存在 ✓。
  * ⚠️ 现状提醒：暗系另外几个效果（`dark_veil` / `dark_fog` / `blood_*` / `dark_power`）
    **也没有图标** ✗ —— 它们主要是挂在怪身上，所以一直没被注意到。要补就跑同一条路子 ✓。

图案：**一面裂开的盾**（易伤 = 防御破了），暗红裂纹在灰盾上 —— 18px 下也读得出来 ✓。
生成器与产物一致：改图案改这里，别手改 PNG ✗。

用法： python tools/gen_vulnerable_icon.py
"""

import os
import sys

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                   "textures", "mob_effect", "vulnerable.png")

SIZE = 18                      # 原版效果图标就是 18x18 ✓
SHIELD = (86, 84, 94)          # 盾面：冷灰（和暗系那套冷调一致）
SHIELD_HI = (112, 110, 122)    # 盾面高光（左上）
EDGE = (34, 32, 40)            # 描边：近黑
CRACK = (214, 42, 46)          # 裂纹：血红（整张图唯一的暖色 ✓）


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 盾牌轮廓：上宽下尖（骑士盾）
    d.polygon([(2, 3), (15, 3), (15, 9), (8, 16), (2, 9)], fill=EDGE)
    d.polygon([(3, 4), (14, 4), (14, 9), (8, 15), (3, 9)], fill=SHIELD)
    # 左上高光：让它在深色 HUD 上也立得住
    d.polygon([(3, 4), (10, 4), (7, 8), (3, 8)], fill=SHIELD_HI)

    # ★ 裂纹：从盾顶劈到底尖的折线（易伤 = 盾破了）
    d.line([(9, 3), (7, 6), (10, 8), (7, 11), (9, 15)], fill=CRACK, width=1)
    d.line([(8, 4), (6, 6), (9, 8)], fill=CRACK, width=1)      # 一道分叉
    # 裂纹两侧压暗一点 ⇒ 有"破开"的厚度感
    d.point([(7, 6), (10, 8), (7, 11)], fill=(120, 24, 28, 255))

    img.save(OUT)
    print("wrote %s (%d bytes)" % (OUT, os.path.getsize(OUT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
