"""Generate a tiled floor mask made only from oversized calligraphic strokes."""
from pathlib import Path
import math
import random
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MASK_OUT = ROOT / "src/main/resources/assets/tnc/textures/environment/ink_floor_calligraphy.png"
PREVIEW_OUT = ROOT / "docs/previews/ink_realm_floor_preview.png"
WIDTH, HEIGHT = 640, 768
SCALE = 3


def interpolate(points, steps_per_segment=80):
    result = []
    for index in range(len(points) - 1):
        x0, y0 = points[index]
        x1, y1 = points[index + 1]
        xp, yp = points[max(0, index - 1)]
        xn, yn = points[min(len(points) - 1, index + 2)]
        for step in range(steps_per_segment):
            t = step / steps_per_segment
            # Cubic Hermite tangents round the brush trajectory at each knot.
            a, b = 2*t**3 - 3*t*t + 1, t**3 - 2*t*t + t
            c, d = -2*t**3 + 3*t*t, t**3 - t*t
            result.append((a*x0 + b*(x1-xp)*0.4 + c*x1 + d*(xn-x0)*0.4,
                           a*y0 + b*(y1-yp)*0.4 + c*y1 + d*(yn-y0)*0.4))
    result.append(points[-1])
    return result


def brush_stroke(canvas, points, widths, seed, dry=True):
    """Paint one isolated pressure-sensitive brush mark; never combines glyphs."""
    rng = random.Random(seed)
    path = interpolate([(x * SCALE, y * SCALE) for x, y in points])
    layer = Image.new("L", canvas.size, 255)
    draw = ImageDraw.Draw(layer)
    count = len(path)

    for pass_color, pass_scale in ((205, 1.48), (125, 1.24), (10, 1.0)):
        for i, (x, y) in enumerate(path):
            t = i / max(1, count - 1)
            pos = t * (len(widths) - 1)
            lo = min(int(pos), len(widths) - 2)
            f = pos - lo
            width = (widths[lo] * (1.0 - f) + widths[lo + 1] * f) * SCALE * pass_scale
            width *= 1.0 + 0.018 * math.sin(i * 0.31 + seed)
            radius = max(1.0, width / 2.0)
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=pass_color)

    if dry:
        cut = ImageDraw.Draw(layer)
        for _ in range(max(4, int(sum(widths) / 12))):
            i = rng.randrange(count // 5, max(count // 5 + 1, count * 4 // 5))
            x, y = path[i]
            before = path[max(0, i - 2)]
            after = path[min(count - 1, i + 2)]
            dx, dy = after[0] - before[0], after[1] - before[1]
            length = math.hypot(dx, dy) or 1.0
            dx, dy = dx / length, dy / length
            nx, ny = -dy, dx
            offset = rng.uniform(-0.23, 0.23) * widths[len(widths) // 2] * SCALE
            streak = rng.uniform(4, 11) * SCALE
            sx, sy = x + nx * offset, y + ny * offset
            cut.line((sx - dx * streak, sy - dy * streak,
                      sx + dx * streak, sy + dy * streak),
                     fill=rng.choice((155, 205, 225)), width=rng.randint(1, 2) * SCALE)

    canvas.paste(ImageChops.darker(canvas, layer))


def make_mask():
    canvas = Image.new("L", (WIDTH * SCALE, HEIGHT * SCALE), 255)

    # One entry equals one basic stroke. Most span 190-270 blocks, roughly
    # three times the old 64-76 block characters. They never form glyphs.
    brush_stroke(canvas, [(35, 92), (46, 85), (130, 84), (240, 79), (291, 68), (301, 78)],
                 [3, 17, 14, 13, 25, 4], 101)                 # 横，顿笔
    brush_stroke(canvas, [(518, 28), (510, 105), (526, 195), (495, 278)],
                 [6, 25, 31, 5], 102)                         # 竖
    brush_stroke(canvas, [(392, 72), (348, 135), (278, 219), (190, 291)],
                 [7, 31, 23, 3], 103)                         # 撇
    brush_stroke(canvas, [(317, 286), (376, 345), (453, 402), (521, 436), (574, 438)],
                 [3, 13, 26, 34, 1], 104)                     # 捺，收锋
    brush_stroke(canvas, [(80, 246), (98, 269), (132, 291)],
                 [9, 34, 3], 105, dry=False)                  # 点
    brush_stroke(canvas, [(45, 489), (111, 458), (188, 415), (274, 368)],
                 [5, 20, 27, 4], 106)                         # 提
    brush_stroke(canvas, [(363, 529), (448, 515), (569, 523),
                          (574, 592), (558, 718)],
                 [6, 27, 22, 29, 4], 107)                     # 横折
    brush_stroke(canvas, [(286, 512), (279, 596), (287, 692),
                          (260, 733), (219, 724)],
                 [6, 26, 29, 16, 3], 108)                     # 竖钩
    brush_stroke(canvas, [(29, 607), (72, 619), (126, 661), (185, 672), (205, 647)],
                 [3, 11, 23, 25, 1], 109)                     # 弯钩

    return canvas.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)


def main():
    mask = make_mask()
    MASK_OUT.parent.mkdir(parents=True, exist_ok=True)
    # Java ImageIO applies a transfer curve to grayscale PNGs. Store equal RGB
    # channels so runtime block thresholds match the authored preview exactly.
    mask.convert("RGB").save(MASK_OUT, optimize=True)

    paper = Image.new("RGB", (WIDTH, HEIGHT), (238, 237, 228))
    pixels, ink = paper.load(), mask.load()
    palette = [(13, 14, 15), (82, 84, 83), (170, 170, 164), (238, 237, 228)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            value = ink[x, y]
            pixels[x, y] = palette[0] if value < 64 else palette[1] if value < 170 else palette[2] if value < 235 else palette[3]

    shown = paper.resize((960, 1152), Image.Resampling.NEAREST)
    preview = Image.new("RGB", (1200, 1240), (30, 31, 32))
    preview.paste(shown, (120, 44))
    ImageDraw.Draw(preview).text((120, 1210),
            "INK REALM FLOOR · isolated 3x brush strokes · no characters",
            fill=(220, 220, 214))
    PREVIEW_OUT.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_OUT)
    print(MASK_OUT.relative_to(ROOT))
    print(PREVIEW_OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
