"""生成刀光贴图 —— **每个武器家族一张，重心不同**。

用户的要求：镰刀的刀光"重边缘"，锤子的刀光"重中心"，其余武器保持现在的均衡丝线。

三张图共用同一套"斜丝"布局（固定种子，可复现），只有**重心曲线**不同：

    trail_plume.png        均衡：两侧羽化 + 中间斜丝（长剑 / 重剑 / 长枪）
    trail_plume_edge.png   边缘重：中间压到 0.45、两端抬满（镰刀：刃在两头，光跟着刃走）
    trail_plume_core.png   中心重：中间满、两侧收到 0.35（锤：钝器是一团闷光，重量在中间）

为什么是三张图而不是一张图分三档：分档会在线性采样/多级渐远时把相邻档糊到一起，
三条边界会变成一道横向的亮带；三张独立贴图各自平铺，没有这个问题。

用法：
    python tools/gen_trail_texture.py            # 写入三张资源
    python tools/gen_trail_texture.py --check    # 校验尺寸/重心/丝数量/斜移（CI 用）
"""

from __future__ import annotations

import argparse
import math
import os
import sys

from PIL import Image

WIDTH = 128     # u：横跨光带
HEIGHT = 128    # v：沿轨迹
OUT_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "particle")

EDGE_POWER = 0.60
BASE = 0.52
FILAMENT_COUNT = 15
SLOPE = 0.055
GRAIN = 0.018
SEED = 20261113

# variant -> (文件名, 说明)
VARIANTS = {
    "balanced": ("trail_plume.png", "均衡"),
    "edge": ("trail_plume_edge.png", "边缘重"),
    "core": ("trail_plume_core.png", "中心重"),
}


def _lcg(seed: int):
    state = seed & 0xFFFFFFFF

    def nxt() -> float:
        nonlocal state
        state = (1103515245 * state + 12345) & 0x7FFFFFFF
        return state / 0x7FFFFFFF

    return nxt


def filaments():
    """(u 中心, 半宽, 亮度, 斜度, 明暗相位, 明暗幅度)，固定种子。"""
    rnd = _lcg(SEED)
    positions = []
    while len(positions) < FILAMENT_COUNT:
        candidate = 0.045 + 0.91 * rnd()
        if all(abs(candidate - p) > 0.040 for p in positions):
            positions.append(candidate)
    positions.sort()
    out = []
    for p in positions:
        width = 0.004 + 0.010 * rnd()
        strength = 0.20 + 0.30 * rnd()
        slope = (rnd() * 2.0 - 1.0) * SLOPE
        phase = rnd()
        modamp = 0.15 + 0.45 * rnd()
        out.append((p, width, strength, slope, phase, modamp))
    out.append((0.5, 0.016, 0.26, 0.0, 0.5, 0.10))
    out.sort()
    return out


FILAMENTS = filaments()


def band_weight(variant: str, u: float) -> float:
    """重心曲线：edge = 两头重，core = 中间重，balanced = sin 羽化。"""
    middle = math.sin(math.pi * u) ** EDGE_POWER
    if variant == "edge":
        return 0.45 + 0.55 * (1.0 - middle)
    if variant == "core":
        return 0.35 + 0.65 * middle
    return middle


def alpha_at(variant: str, u: float, v: float) -> float:
    weight = band_weight(variant, u)
    value = BASE * weight
    for position, width, strength, slope, phase, modamp in FILAMENTS:
        center = position + slope * (v - 0.5)
        d = (u - center) / width
        if abs(d) > 4.0:
            continue
        brightness = 1.0 - modamp * (0.5 + 0.5 * math.cos(2.0 * math.pi * (v * 1.5 + phase)))
        value += strength * brightness * weight * math.exp(-d * d * 0.6931)
    value += GRAIN * math.sin(2.0 * math.pi * 19.0 * u + 1.3) * math.sin(2.0 * math.pi * 11.0 * v)
    return max(0.0, min(1.0, value))


def build(variant: str) -> Image.Image:
    image = Image.new("RGBA", (WIDTH, HEIGHT))
    pixels = image.load()
    for y in range(HEIGHT):
        v = (y + 0.5) / HEIGHT
        for x in range(WIDTH):
            u = (x + 0.5) / WIDTH
            # 颜色保持纯白：真正的颜色由顶点色（刃色/元素色）乘上去
            pixels[x, y] = (255, 255, 255, int(round(alpha_at(variant, u, v) * 255)))
    return image


def _column_means(rgba) -> list[float]:
    return [sum(rgba.getpixel((x, y))[3] for y in range(rgba.height)) / rgba.height
            for x in range(rgba.width)]


def _peaks(values: list[float], threshold: float) -> int:
    count = 0
    for i in range(1, len(values) - 1):
        if values[i] >= threshold and values[i] >= values[i - 1] and values[i] > values[i + 1]:
            count += 1
    return count


def _best_shift(a: list[float], b: list[float], window: int) -> int:
    best, best_score = 0, -1e18
    for shift in range(-window, window + 1):
        score = 0.0
        for i in range(window, len(a) - window):
            score += a[i] * b[i + shift]
        if score > best_score:
            best_score, best = score, shift
    return best


def check_one(variant: str) -> int:
    path = os.path.join(OUT_DIR, VARIANTS[variant][0])
    if not os.path.exists(path):
        print(f"[fail] 缺少贴图 {path}")
        return 1
    with Image.open(path) as image:
        if image.size != (WIDTH, HEIGHT):
            print(f"[fail] {path} 尺寸不对：{image.size} != {(WIDTH, HEIGHT)}")
            return 1
        rgba = image.convert("RGBA")

        # 1) 和公式一致
        worst = 0.0
        for y in range(0, HEIGHT, 5):
            v = (y + 0.5) / HEIGHT
            for x in range(0, WIDTH, 4):
                u = (x + 0.5) / WIDTH
                worst = max(worst, abs(rgba.getpixel((x, y))[3] / 255.0 - alpha_at(variant, u, v)))
        if worst > 0.03:
            print(f"[fail] {path} 内容和公式对不上（最大偏差 {worst:.3f}）")
            return 1

        columns = _column_means(rgba)
        mid = WIDTH // 2
        left = (columns[1] + columns[2]) * 0.5
        right = (columns[-2] + columns[-3]) * 0.5
        edge_side = (left + right) * 0.5

        # 2) 重心：edge 档两侧平均必须高于中心；core 档中心必须高于两侧；balanced 中心最高
        if variant == "edge":
            if edge_side <= columns[mid]:
                print(f"[fail] edge 档两侧不够亮：{edge_side:.0f} vs 中心 {columns[mid]:.0f}")
                return 1
        elif variant == "core":
            if columns[mid] <= edge_side * 1.15:
                print(f"[fail] core 档中心不够亮：{columns[mid]:.0f} vs 两侧 {edge_side:.0f}")
                return 1
        else:
            if columns[mid] <= edge_side:
                print(f"[fail] balanced 档中心不够亮：{columns[mid]:.0f} vs 两侧 {edge_side:.0f}")
                return 1

        # 3) 丝的数量与斜向（阈值按这一档自己的最亮点取，免得"边缘重"那档被中间的低亮度卡住）
        y = HEIGHT // 2
        row = [rgba.getpixel((x, y))[3] for x in range(WIDTH)]
        peaks = _peaks(row, 0.55 * max(row))
        if peaks < 5:
            print(f"[fail] {path} 只有 {peaks} 条丝（要 ≥5）")
            return 1
        top = [rgba.getpixel((x, HEIGHT // 4))[3] for x in range(WIDTH)]
        bottom = [rgba.getpixel((x, HEIGHT * 3 // 4))[3] for x in range(WIDTH)]
        if abs(_best_shift(top, bottom, 10)) < 1:
            print(f"[fail] {path} 的丝是直的（会像靶环）")
            return 1
        print(f"[ok] {VARIANTS[variant][1]:<4} {path}：中心 {columns[mid]:.0f}、两侧 {edge_side:.0f}、"
              f"{peaks} 条斜丝")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if args.check:
        return max(check_one(v) for v in VARIANTS)
    os.makedirs(OUT_DIR, exist_ok=True)
    for variant, (name, label) in VARIANTS.items():
        build(variant).save(os.path.join(OUT_DIR, name))
        print(f"wrote {os.path.join(OUT_DIR, name)} ({label}) {WIDTH}x{HEIGHT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
