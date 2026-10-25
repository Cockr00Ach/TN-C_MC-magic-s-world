"""生成大地震波用的“波带”贴图（透明、自发光）。

输出：src/main/resources/assets/tnc/textures/entity/quake_band.png（64x64）
- 底部（贴地）近白黄、最亮；向上过渡到橙、深橙红，顶部完全透明；
- 竖向条纹让波带有“能量流动”的质感，横向再做一圈圈柔和起伏；
- 透明区保持干净（alpha=0），避免出现灰色方块边。
"""

import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/tnc/textures/entity/quake_band.png"
W = 64
H = 64
random.seed(20261025)

# 每一列的亮度系数：少数几条特别亮，像流动的能量丝
column_gain = []
for x in range(W):
    base = 0.82 + 0.18 * math.sin(x * 0.55) + 0.06 * math.sin(x * 1.9 + 1.3)
    if random.random() < 0.10:
        base += 0.22
    column_gain.append(max(0.55, min(1.35, base)))


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


CORE = (255, 250, 232)
GOLD = (255, 226, 150)
ORANGE = (255, 150, 48)
DEEP = (214, 78, 22)


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

for y in range(H):
    # t=0 顶部（透明），t=1 底部（贴地、最亮）：图片 y=0 是顶部，所以直接按 y 递增
    t = y / (H - 1)
    if t < 0.35:
        color = lerp(DEEP, ORANGE, t / 0.35)
    elif t < 0.72:
        color = lerp(ORANGE, GOLD, (t - 0.35) / 0.37)
    else:
        color = lerp(GOLD, CORE, (t - 0.72) / 0.28)

    # 贴地最实，往上快速透明
    if t <= 0.06:
        alpha = 0.0
    elif t < 0.30:
        alpha = 0.10 + 0.45 * ((t - 0.06) / 0.24)
    elif t < 0.75:
        alpha = 0.55 + 0.32 * ((t - 0.30) / 0.45)
    else:
        alpha = 0.87 + 0.13 * ((t - 0.75) / 0.25)

    for x in range(W):
        gain = column_gain[x]
        r, g, b = [min(255, int(c * gain)) for c in color]
        # 横向柔和起伏，避免整张贴图是均匀渐变
        wobble = 1.0 + 0.06 * math.sin(x * 0.34 + y * 0.12)
        r, g, b = [min(255, int(c * wobble)) for c in (r, g, b)]
        a = int(round(255 * max(0.0, min(1.0, alpha))))
        px[x, y] = (r, g, b, a)

img.save(OUT)
print(f"wrote {OUT.relative_to(ROOT)} ({W}x{H})")
