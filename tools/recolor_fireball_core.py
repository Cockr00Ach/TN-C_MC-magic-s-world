"""把火球内核贴图重上色成“红橙为主 + 少量黑与白”。

顺序：先 `python tools/import_fireball_bbmodel.py` 导出作者原图，
再跑本脚本覆盖 `src/main/resources/assets/tnc/textures/block/fireball_core.png`。
本脚本不是幂等的：重复跑会在已上色的图上再上色，所以每次改模型都要
先重新导入一次，再跑这里。

颜色是一条约 5 段的渐变（黑 → 暗红 → 红 → 橙 → 近白），按原图每个像素的
亮度取样，保留原来的噪点质感；另外在最亮和最暗处各撒极少量纯白/纯黑点。
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/tnc/textures/block/fireball_core.png"

# 渐变节点：位置 0..1 → 颜色
STOPS = [
    (0.00, (16, 11, 9)),        # 焦黑
    (0.16, (96, 18, 14)),       # 暗红
    (0.38, (186, 40, 22)),      # 红
    (0.62, (232, 92, 30)),      # 红橙
    (0.82, (248, 150, 52)),     # 橙
    (1.00, (252, 238, 208)),    # 近白热芯
]


def ramp(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(STOPS) - 1):
        p0, c0 = STOPS[i]
        p1, c1 = STOPS[i + 1]
        if t <= p1:
            k = (t - p0) / (p1 - p0) if p1 > p0 else 0.0
            return tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(3))
    return STOPS[-1][1]


def main():
    img = Image.open(TEX).convert("RGB")
    px = img.load()
    w, h = img.size

    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            # 提一点对比，让暗部更暗、亮部更亮
            t = (lum - 0.5) * 1.15 + 0.5
            col = ramp(t)

            # 保留原图噪点，避免整片纯色
            n = ((x * 73856093) ^ (y * 19349663)) & 0xFF
            jitter = (n / 255.0 - 0.5) * 18.0
            r2, g2, b2 = (max(0, min(255, int(c + jitter * (1.0 if i < 2 else 0.6))))
                          for i, c in enumerate(col))

            # 极少量纯黑 / 纯白点
            if n < 5 and t < 0.45:
                r2, g2, b2 = (14, 10, 9)
            elif n > 250 and t > 0.80:
                r2, g2, b2 = (255, 246, 226)

            px[x, y] = (r2, g2, b2)

    img.save(TEX)
    print(f"recolored {TEX.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
