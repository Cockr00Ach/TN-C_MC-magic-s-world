"""Draw the three-beat earth cast windup concept: cracks crawl in -> chunks rise ->
vortex forms at the caster. Schematic only; the in-game geometry is drawn live."""
import math
import random
from PIL import Image, ImageDraw, ImageFont


def font(size):
    for path in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


W, H = 1280, 440
BG = (26, 20, 14)
PANEL = (36, 28, 20)
GOLD = (255, 196, 92)
EMBER = (226, 118, 46)
STONE = (150, 130, 104)
DEEP = (92, 66, 42)
TEXT = (222, 208, 186)
DIM = (150, 132, 110)

random.seed(20261106)


def crack(draw, cx, cy, angle, r0, r1, color, width=2, seed=0):
    rnd = random.Random(seed)
    points = []
    steps = 7
    a = angle
    for i in range(steps + 1):
        t = i / steps
        r = r0 + (r1 - r0) * t
        if i:
            a += (rnd.random() - .5) * .24
        points.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    draw.line(points, fill=color, width=width)


def panel(draw, x0, title, stage):
    cx, cy = x0 + 200, 250
    draw.rectangle([x0, 40, x0 + 400, 400], fill=PANEL, outline=(70, 55, 38))
    draw.text((x0 + 16, 52), title, fill=TEXT, font=font(19))
    R = 150
    count = 10
    if stage == 0:
        tip = 118
    elif stage == 1:
        tip = 46
    else:
        tip = 16
    for i in range(count):
        a = i * math.tau / count + (random.random() - .5) * .12
        crack(draw, cx, cy, a, tip, R, DEEP, 6, seed=i)
        crack(draw, cx, cy, a, tip, R, GOLD if stage == 2 else EMBER, 2, seed=i)
        if i % 2 == 0:
            crack(draw, cx, cy, a + 1.1, (tip + R) * .5, (tip + R) * .5 + 34, DEEP, 4, seed=100 + i)
            crack(draw, cx, cy, a + 1.1, (tip + R) * .5, (tip + R) * .5 + 34, EMBER, 1, seed=100 + i)

    # caster
    draw.ellipse([cx - 15, cy - 15, cx + 15, cy + 15], fill=(236, 224, 200), outline=GOLD)
    draw.text((cx - 17, cy + 22), "玩家", fill=DIM, font=font(15))

    # chunks rising out of the cracks
    if stage >= 1:
        for i in range(18):
            a = random.random() * math.tau
            r = tip + random.random() * (R - tip) * .7
            s = 3 + random.random() * 4
            px, py = cx + math.cos(a) * r, cy + math.sin(a) * r
            draw.rectangle([px - s, py - s, px + s, py + s], fill=STONE)
            draw.line([(px, py), (px - math.cos(a) * 10, py - math.sin(a) * 10)], fill=EMBER, width=1)

    # vortex rings at the caster
    if stage == 2:
        for k, r in enumerate((34, 52, 70)):
            box = [cx - r, cy - r, cx + r, cy + r]
            draw.arc(box, start=20 + k * 40, end=320 + k * 40, fill=GOLD, width=2)
        for i in range(9):
            a = i * math.tau / 9
            r0, r1 = 76, 30
            p0 = (cx + math.cos(a) * r0, cy + math.sin(a) * r0)
            p1 = (cx + math.cos(a + .5) * r1, cy + math.sin(a + .5) * r1)
            draw.line([p0, p1], fill=EMBER, width=2)

    # ground rings
    for r in (R, R * .72):
        box = [cx - r, cy - r, cx + r, cy + r]
        draw.arc(box, start=0, end=360, fill=(120, 96, 70), width=1)


canvas = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(canvas)
draw.text((24, 6), "土系读条前戏：地裂 → 冒土 → 石旋涡（俯视示意）", fill=TEXT, font=font(22))
for i, (title, stage) in enumerate((
        ("① 远处地裂，缝尖往里爬", 0),
        ("② 土块顶出裂缝，被吸向玩家", 1),
        ("③ 旋涡成形，裂缝收到脚边", 2))):
    panel(draw, 20 + i * 420, title, stage)

canvas.save(r"D:\ModTest\docs\spells\土法读条前戏-concept.png")
print("saved", canvas.size)
