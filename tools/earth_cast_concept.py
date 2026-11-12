"""Draw the three-beat earth cast windup concept (v2): jagged cracks with lifted lips crawl in ->
slabs and chunks heave out -> vortex + chest orb + foot spikes at the caster.

Schematic only; the in-game geometry is drawn live every frame. The crack shape here mirrors
EarthCastFx.crackPath (6 segments, inertial drift), and the lips mirror crackLips."""
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


W, H = 1280, 460
BG = (26, 20, 14)
PANEL = (36, 28, 20)
GOLD = (255, 196, 92)
EMBER = (226, 118, 46)
STONE = (150, 130, 104)
LIP = (122, 100, 76)
DEEP = (92, 66, 42)
VOID = (52, 36, 24)
TEXT = (222, 208, 186)
DIM = (150, 132, 110)

random.seed(20261106)


def crack(draw, cx, cy, angle, r0, r1, seed, hot):
    """7 points / 6 segments, inertial random walk, like EarthCastFx.crackPath."""
    rnd = random.Random(seed)
    pts = []
    drift = (rnd.random() - .5) * .34
    steps = 6
    for i in range(steps + 1):
        t = i / steps
        r = r0 + (r1 - r0) * (t * t * (.45 + .55 * t))
        if i:
            drift = drift * .58 + (rnd.random() - .5) * .34
        a = angle + drift * (.5 + 1.15 * t)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))

    # 缝底（地下是空的）+ 缝芯
    draw.line(pts, fill=VOID, width=9)
    draw.line(pts, fill=DEEP, width=5)
    # 两侧被顶起来的土唇
    for side in (-1, 1):
        lip = []
        for j, (px, py) in enumerate(pts):
            dx, dy = px - cx, py - cy
            length = math.hypot(dx, dy) or 1
            nx, ny = -dy / length, dx / length
            off = 5.5 * side * (1 - .45 * j / steps)
            lip.append((px + nx * off, py + ny * off))
        draw.line(lip, fill=LIP, width=2)
    # 缝内余烬边（偏一侧）
    draw.line(pts, fill=GOLD if hot else EMBER, width=1)

    # 卫星碎纹
    mid = pts[2]
    for k, turn in ((0, .8), (1, -.9)):
        base = math.atan2(mid[1] - cy, mid[0] - cx) + turn
        draw.line([mid, (mid[0] + math.cos(base) * 14, mid[1] + math.sin(base) * 14)],
                  fill=DEEP, width=2)


def slab(draw, cx, cy, angle, dist, w, h, hot):
    """A tilted slab heaved out of the crack."""
    px, py = cx + math.cos(angle) * dist, cy + math.sin(angle) * dist
    ca, sa = math.cos(angle + .6), math.sin(angle + .6)
    pts = []
    for sx, sy in ((-w, -h), (w, -h), (w, h), (-w, h)):
        pts.append((px + sx * ca - sy * sa, py + sx * sa + sy * ca))
    draw.polygon(pts, fill=STONE, outline=DEEP)
    draw.line([pts[0], pts[1]], fill=LIP, width=2)
    if hot:
        draw.line([pts[1], pts[2]], fill=EMBER, width=1)


def panel(draw, x0, title, stage):
    cx, cy = x0 + 200, 250
    draw.rectangle([x0, 40, x0 + 400, 420], fill=PANEL, outline=(70, 55, 38))
    draw.text((x0 + 16, 52), title, fill=TEXT, font=font(18))
    R = 150
    count = 10
    tip = (118, 62, 16)[stage]
    hot = stage == 2

    # 贴地尘雾带：两条很宽的暗环
    for k, rr in enumerate((R * .46, R * .66)):
        box = [cx - rr, cy - rr, cx + rr, cy + rr]
        draw.arc(box, start=0, end=360, fill=(48, 38, 28), width=26)

    for i in range(count):
        a = i * math.tau / count + (random.random() - .5) * .12
        crack(draw, cx, cy, a, tip, R, seed=i, hot=hot)
        if i % 2 == 0:
            crack(draw, cx, cy, a + .9, (tip + R) * .5, (tip + R) * .5 + 40, seed=100 + i, hot=hot)

    # 地面尘环脉冲
    for k in range(3):
        rr = 22 + k * 46 + stage * 18
        if rr < R * 1.02:
            draw.arc([cx - rr, cy - rr, cx + rr, cy + rr], start=0, end=360,
                     fill=(74, 56, 40), width=1)

    # 法阵
    for rr in (R * .62, R * .45):
        draw.arc([cx - rr, cy - rr, cx + rr, cy + rr], start=0, end=360, fill=(120, 96, 70), width=1)

    # 土板 + 碎块
    if stage >= 1:
        for i in range(5):
            a = i * math.tau / 5 + .4
            slab(draw, cx, cy, a, R * (.5 + .12 * (i % 3)), 9 + i, 6 + (i % 3), hot)
        for i in range(18):
            a = random.random() * math.tau
            r = tip + random.random() * (R - tip) * .7
            s = 3 + random.random() * 4
            px, py = cx + math.cos(a) * r, cy + math.sin(a) * r
            draw.rectangle([px - s, py - s, px + s, py + s], fill=STONE)
            draw.line([(px, py), (px - math.cos(a) * 10, py - math.sin(a) * 10)], fill=EMBER, width=1)

    # 地气抽吸：缝尖 -> 胸口
    if stage >= 1:
        for k in range(6):
            a = k * math.tau / 6
            start = tip + 6
            p0 = (cx + math.cos(a) * start, cy + math.sin(a) * start)
            p1 = (cx + math.cos(a) * (start * .45), cy + math.sin(a) * (start * .45))
            draw.line([p0, p1], fill=EMBER, width=1)

    # 玩家 + 胸口土光
    draw.ellipse([cx - 15, cy - 15, cx + 15, cy + 15], fill=(236, 224, 200), outline=GOLD)
    draw.text((cx - 17, cy + 22), "玩家", fill=DIM, font=font(15))
    orb = 5 + stage * 3
    draw.ellipse([cx - orb, cy - orb, cx + orb, cy + orb], outline=GOLD, width=2)
    for k in range(3):
        a = k * math.tau / 3 + .4
        sx, sy = cx + math.cos(a) * (orb + 8), cy + math.sin(a) * (orb + 8)
        draw.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=EMBER)

    # 旋涡
    if stage == 2:
        for k, r in enumerate((34, 52, 70)):
            draw.arc([cx - r, cy - r, cx + r, cy + r], start=20 + k * 40, end=320 + k * 40,
                     fill=GOLD, width=2)
        for i in range(9):
            a = i * math.tau / 9
            p0 = (cx + math.cos(a) * 76, cy + math.sin(a) * 76)
            p1 = (cx + math.cos(a + .5) * 30, cy + math.sin(a + .5) * 30)
            draw.line([p0, p1], fill=EMBER, width=2)
        # 脚边尖石
        for k in range(8):
            a = k * math.tau / 8 + .2
            base = (cx + math.cos(a) * 20, cy + math.sin(a) * 20)
            tipx = (cx + math.cos(a) * 12, cy + math.sin(a) * 12)
            draw.line([base, tipx], fill=VOID, width=4)
            draw.line([base, tipx], fill=EMBER, width=1)


canvas = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(canvas)
draw.text((24, 6), "土系读条前戏 v2：锯齿地裂 + 土唇土板 → 地气抽吸 → 石旋涡（俯视示意）",
          fill=TEXT, font=font(22))
for i, (title, stage) in enumerate((
        ("① 远处地裂：折线拐弯、土唇翻起", 0),
        ("② 土板顶出、碎块被吸向胸口", 1),
        ("③ 旋涡成形、胸口攒光、脚边尖石", 2))):
    panel(draw, 20 + i * 420, title, stage)

canvas.save(r"D:\ModTest\docs\spells\土法读条前戏-concept.png")
print("saved", canvas.size)
