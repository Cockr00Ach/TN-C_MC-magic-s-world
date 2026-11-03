"""龙之剑那条龙的离线预览图。

读 `build/tnc-dragon-quads.csv` —— 那份数据是单测 `WarriorDragonGeometryTest` 从
`WarriorDragonGeometry.build(...)` 直接写出来的三角形，和客户端渲染器画的是同一批顶点。
所以这张图不是"另外画一遍"，只是把同一堆三角形成像（加算混合，和 `RenderType.lightning` 同一种猜法）。

用法：python tools/dragon_preview.py [输出路径]
默认输出 docs/previews/dragon-render-20261103.png
"""
import csv
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "build" / "tnc-dragon-quads.csv"
TARGET = ROOT / "docs" / "previews" / "dragon-render-20261103.png"

CELL_W, CELL_H = 860, 400
PAD = 22
# 3/4 俯侧视：龙沿 +Z 飞，横向摆动在 -X，所以屏幕上"右=前、上=高、左=摆"
FORWARD_X, FORWARD_Y = 0.70, 0.30
SIDE_X = 1.05
BACKGROUND = (7, 9, 14)
MARGIN = 42.0


def project(point, scale, ox, oy):
    x, y, z = point
    return (ox + ((-x) * SIDE_X + z * FORWARD_X) * scale,
            oy - (y - z * FORWARD_Y) * scale)


def load_frames():
    frames, current = [], None
    with SOURCE.open(newline="", encoding="utf-8") as handle:
        for row in csv.reader(handle):
            if not row:
                continue
            if row[0] == "FRAME":
                current = {"head": float(row[1]), "tris": []}
                frames.append(current)
                continue
            if current is None:
                raise SystemExit("csv must start with a FRAME line")
            color, alpha = int(row[0]), float(row[1])
            values = [float(v) for v in row[2:]]
            points = [tuple(values[i:i + 3]) for i in (0, 3, 6)]
            current["tris"].append((color, alpha, points))
    return frames


def fit(frames):
    """一套投影参数通吃两帧：按全部顶点的投影包围盒定缩放和偏移。"""
    xs, ys = [], []
    for frame in frames:
        for _color, _alpha, points in frame["tris"]:
            for point in points:
                x = (-point[0]) * SIDE_X + point[2] * FORWARD_X
                y = -(point[1] - point[2] * FORWARD_Y)
                xs.append(x)
                ys.append(y)
    span_x = max(xs) - min(xs)
    span_y = max(ys) - min(ys)
    scale = min((CELL_W - MARGIN * 2) / span_x, (CELL_H - MARGIN * 2) / span_y)
    ox = MARGIN - min(xs) * scale
    oy = MARGIN - min(ys) * scale
    return scale, ox, oy


def render_frame(frame, scale, ox, oy):
    width, height = CELL_W, CELL_H
    buffer = [[[float(c) for c in BACKGROUND] for _ in range(width)] for _ in range(height)]
    for color, alpha, points in frame["tris"]:
        rgb = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
        projected = [project(p, scale, ox, oy) for p in points]
        min_x = max(0, int(min(p[0] for p in projected)))
        max_x = min(width - 1, int(max(p[0] for p in projected)) + 1)
        min_y = max(0, int(min(p[1] for p in projected)))
        max_y = min(height - 1, int(max(p[1] for p in projected)) + 1)
        if min_x > max_x or min_y > max_y:
            continue
        mask = Image.new("L", (max_x - min_x + 1, max_y - min_y + 1), 0)
        ImageDraw.Draw(mask).polygon([(p[0] - min_x, p[1] - min_y) for p in projected], fill=255)
        pixels = mask.load()
        for y in range(min_y, max_y + 1):
            row = buffer[y]
            for x in range(min_x, max_x + 1):
                if pixels[x - min_x, y - min_y]:
                    cell = row[x]
                    cell[0] += rgb[0] * alpha
                    cell[1] += rgb[1] * alpha
                    cell[2] += rgb[2] * alpha
    image = Image.new("RGB", (width, height), BACKGROUND)
    image.putdata([
        (min(255, int(buffer[y][x][0])), min(255, int(buffer[y][x][1])), min(255, int(buffer[y][x][2])))
        for y in range(height) for x in range(width)
    ])
    return image


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else TARGET
    frames = load_frames()
    scale, ox, oy = fit(frames)
    images = [render_frame(frame, scale, ox, oy) for frame in frames]
    sheet = Image.new("RGB", (CELL_W, CELL_H * len(images) + PAD * (len(images) - 1)), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for index, (frame, image) in enumerate(zip(frames, images)):
        top = index * (CELL_H + PAD)
        sheet.paste(image, (0, top))
        draw.text((16, top + 10), f"龙头进度 {frame['head']:.2f}  ({len(frame['tris'])} tris)",
                  fill=(208, 216, 228))
        draw.text((16, top + 26), "WarriorDragonGeometry.build(): 4 棱管 x3 层 + 双翼 + 菱形龙头",
                  fill=(120, 134, 152))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(f"wrote {out} ({out.stat().st_size} bytes, {len(frames)} frames, scale={scale:.1f})")


if __name__ == "__main__":
    main()
