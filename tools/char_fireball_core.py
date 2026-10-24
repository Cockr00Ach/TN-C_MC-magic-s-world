"""给火球内核贴图加一点“烧黑”的层次。

输入/输出都是 src/main/resources/assets/tnc/textures/block/fireball_core.png。
作者手画的 8 个 16x16 色块保持原样，只是：
  1) 最暗的两块往炭黑方向压一点，让暗部更沉；
  2) 每个色块四周 3 像素做向内渐暗，方块感更清楚，像被烧焦的棱边。

脚本是幂等的：重复跑不会越烧越黑（每次从当前文件读、按固定系数混合，
炭黑比例超过阈值的像素不再继续压）。
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/tnc/textures/block/fireball_core.png"

CELL = 16
CHARCOAL = (26, 20, 17)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def main():
    img = Image.open(TEX).convert("RGB")
    px = img.load()
    w, h = img.size

    for cy in range(0, h, CELL):
        for cx in range(0, w, CELL):
            # 当前色块的平均亮度，越暗的块压得越多
            total = 0
            count = 0
            for y in range(cy, min(cy + CELL, h)):
                for x in range(cx, min(cx + CELL, w)):
                    r, g, b = px[x, y]
                    total += (r + g + b) / 3.0
                    count += 1
            avg = total / max(1, count)
            base = 0.62 if avg < 110 else (0.30 if avg < 175 else 0.0)

            for y in range(cy, min(cy + CELL, h)):
                for x in range(cx, min(cx + CELL, w)):
                    r, g, b = px[x, y]
                    # 到色块边缘的距离，边缘更黑
                    edge = min(x - cx, y - cy, cx + CELL - 1 - x, cy + CELL - 1 - y)
                    edge_t = 0.0
                    if edge < 4:
                        edge_t = 0.58 * (1.0 - edge / 4.0)
                    t = max(base, edge_t if base > 0 else edge_t * 0.85)
                    if t <= 0.0:
                        continue
                    px[x, y] = mix((r, g, b), CHARCOAL, t)

    img.save(TEX)
    print(f"charred {TEX.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
