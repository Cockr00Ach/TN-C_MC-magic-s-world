"""生成大地震波“环形波带”的贴图（淡灰金 + 少量暖橙亮纹）。

输出：src/main/resources/assets/tnc/textures/entity/quake_band.png（64x64）

贴图坐标约定：
- u（横向）沿圆周重复；
- v（纵向）横跨波带的径向宽度，0 = 内缘、1 = 外缘；
- 亮度与 alpha 沿 v 呈中间高、两侧透明的波峰分布；
- 渲染时让 v 随时间缓慢滚动，就形成“一圈圈向外推”的观感。
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

BASE = (214, 206, 190)      # 淡灰金
WARM = (240, 198, 142)      # 暖金
ACCENT = (255, 176, 92)     # 少量暖橙亮纹

# 沿 v 的几条亮纹，滚动时像一波波向外推的浪峰
BANDS = ((0.46, 0.09, 1.0), (0.60, 0.06, 0.55), (0.33, 0.05, 0.40))


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

for y in range(H):
    v = y / (H - 1)
    env = math.sin(math.pi * v) ** 0.85
    alpha = 0.38 * env
    if alpha <= 0.01:
        continue

    warm = min(1.0, max(0.0, (v - 0.30) / 0.45))
    color = lerp(BASE, WARM, warm * 0.8)

    for band_v, width, strength in BANDS:
        d = abs(v - band_v)
        if d < width:
            color = lerp(color, ACCENT, strength * (1.0 - d / width) * 0.55)

    for x in range(W):
        ripple = 0.90 + 0.10 * math.sin(x * 0.22 + v * 1.7) + 0.04 * math.sin(x * 0.9)
        r, g, b = [min(255, max(0, int(c * ripple))) for c in color]
        n = random.randint(-5, 5)
        r, g, b = [min(255, max(0, c + n)) for c in (r, g, b)]
        a = int(round(255 * alpha * (0.85 + 0.15 * ripple)))
        px[x, y] = (r, g, b, max(0, min(255, a)))

img.save(OUT)
print(f"wrote {OUT.relative_to(ROOT)} ({W}x{H})")
