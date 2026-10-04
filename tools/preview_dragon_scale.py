# -*- coding: utf-8 -*-
"""
preview_dragon_scale.py -- 三条光龙的真实比例图（作者 2026-10-02 定了 t3/t4/t5 = 1/3/10 倍个头）。

为什么单独一个脚本：普通预览会 --fit 自动取景 ⇒ 大龙小龙在图上一样大 ✗。
这里每一档都自动取景，但**同时画一个玩家大小的参照方块**（1.8 格高 ✓ 蓝色那个 ✓）
—— 参照方块在画面里越小，就说明那条龙越大 ✓（另外每格画面都标了实际格数 ✓）。

输出 docs/previews/dragon_scale.png：上/中/下 = t3 0.30 倍（≈7 格长）/
t4 0.90 倍（≈21 格）/ t5 3.00 倍（≈69 格 ✓）。

ASCII only.  Usage:  python tools/preview_dragon_scale.py
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402

GEO = "src/main/resources/assets/tnc/geo/entity/dragon.geo.json"
OUT = os.path.join("docs", "previews", "dragon_scale.png")
SIZE = 760
SCALES = [(0.30, "t3  1x  = 6.9 blocks long"),
          (0.90, "t4  3x  = 20.7 blocks"),
          (3.00, "t5 10x  = 69.0 blocks")]


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    frames = []
    for scale, note in SCALES:
        out = os.path.join("docs", "previews", "_scale_tmp.png")
        # side view (the length reads horizontally) + the blue player box parked behind the
        # tail so it is never hidden behind the body; it stays 1.8 blocks - that IS the ratio
        P.strip(GEO, "-", "bind", [0.0], ["side"], out, size=SIZE, zoom=4.0,
                front="-Z", fit=True, model_scale=scale, reference=True,
                reference_z=200.0 * scale + 30.0)
        img = Image.open(out).convert("RGB")
        os.remove(out)
        frames.append((img, note))
    sheet = Image.new("RGB", (SIZE, SIZE * len(frames)), (26, 26, 34))
    for i, (img, note) in enumerate(frames):
        sheet.paste(img, (0, i * SIZE))
    sheet.save(OUT)
    print("preview -> %s (%dx%d)  blue box = a player (1.8 blocks tall)"
          % (OUT, sheet.size[0], sheet.size[1]))
    for scale, note in SCALES:
        print("   %-38s scale %.2f" % (note, scale))


if __name__ == "__main__":
    main()
