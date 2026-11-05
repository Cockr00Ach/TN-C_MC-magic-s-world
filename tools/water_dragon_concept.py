"""Draw the water-dragon cast concept: side view of the dragon (proportions + parts) and the
three-beat flight (water gate -> bezier flight into the chest -> splash burst).

Schematic only; the in-game dragon is live geometry (WaterDragonFx), not a model or screenshot."""
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


W, H = 1360, 470
BG = (8, 16, 26)
PANEL = (14, 26, 40)
FOAM = (222, 248, 255)
LIGHT = (140, 234, 255)
CYAN = (66, 204, 255)
DEEP = (16, 92, 168)
NIGHT = (6, 26, 52)
TEXT = (216, 236, 246)
DIM = (128, 168, 194)
GOLD = (242, 196, 96)

random.seed(20261106)


def spine_points(cx, cy, length, n=12, sway=1.0):
    pts = []
    for i in range(n):
        t = i / (n - 1)
        x = cx + (0.5 - t) * length
        y = cy + math.sin(t * 5.4) * length * .035 * sway - t * length * .02
        pts.append((x, y))
    return pts


def dragon(draw, cx, cy, length, scale=1.0, labels=True):
    pts = spine_points(cx, cy, length)
    # 四层"管"：深蓝辉光 -> 青 -> 浅 -> 白芯（和游戏里一样按宽度叠）
    layers = ((.030, .18, DEEP), (.021, .34, CYAN), (.014, .52, LIGHT), (.006, .78, FOAM))
    for width, alpha, color in layers:
        for i in range(len(pts) - 1):
            t = i / (len(pts) - 1)
            w = max(1, int(length * width * (1 - .35 * t)))
            draw.line([pts[i], pts[i + 1]], fill=color, width=w)

    # 背鳍
    for k in range(7):
        t = .12 + k * .10
        idx = t * (len(pts) - 1)
        i = int(idx)
        j = min(len(pts) - 1, i + 1)
        bx, by = pts[i]
        nx, ny = pts[j]
        height = length * (.10 - .007 * k)
        tipx = (bx + nx) * .5
        tipy = (by + ny) * .5 - height
        draw.polygon([(bx, by), (nx, ny), (tipx, tipy)], fill=CYAN)
        draw.line([(bx, by), (tipx, tipy)], fill=FOAM, width=1)

    # 尾鳍
    tx, ty = pts[-1]
    for i in range(5):
        a = (i - 2) * .34
        ex = tx - length * .085 * math.cos(a)
        ey = ty + length * .075 * math.sin(a)
        draw.line([(tx, ty), (ex, ey)], fill=LIGHT, width=2)
    draw.polygon([(tx, ty + length * .03), (tx, ty - length * .03),
                  (tx - length * .085, ty - length * .05), (tx - length * .085, ty + length * .05)],
                 fill=CYAN)

    # 头（颅/吻/下颚/眼/四角/龙须）
    hx, hy = pts[0]
    s = length / 280.0
    draw.polygon([(hx + 22 * s, hy - 6 * s), (hx + 8 * s, hy - 16 * s), (hx - 16 * s, hy - 12 * s),
                  (hx - 16 * s, hy + 12 * s), (hx + 8 * s, hy + 15 * s), (hx + 24 * s, hy + 5 * s)],
                 fill=CYAN, outline=LIGHT)
    draw.polygon([(hx + 40 * s, hy + 1 * s), (hx + 20 * s, hy - 8 * s),
                  (hx + 18 * s, hy + 8 * s)], fill=LIGHT)
    draw.polygon([(hx + 22 * s, hy + 10 * s), (hx - 4 * s, hy + 14 * s),
                  (hx - 2 * s, hy + 22 * s), (hx + 26 * s, hy + 16 * s)], fill=CYAN)
    # 眼
    draw.ellipse([hx + 6 * s, hy - 6 * s, hx + 13 * s, hy + 1 * s], fill=FOAM)
    # 角
    for side in (-1, 1):
        for k in range(2):
            sx = hx - (6 + k * 6) * s
            sy = hy - (10 + k * 3) * s
            for seg in range(5):
                t = seg / 5
                ex = sx - (7 + seg * 2) * s
                ey = sy - (5 + seg * 3) * s
                draw.line([(sx, sy), (ex, ey)], fill=LIGHT, width=max(1, int(3 - seg * .4)))
                sx, sy = ex, ey
    # 龙须（两条，往后飘）
    for side, phase in ((1, 0.0), (-1, 1.6)):
        px, py = hx + 26 * s, hy + 4 * s
        for seg in range(1, 15):
            t = seg / 14
            x = px - (10 + 34 * t) * s
            y = py + math.sin(phase + t * 4.4) * 9 * s * t + 4 * s * t
            draw.line([(px, py), (x, y)], fill=FOAM, width=1)
            px, py = x, y

    # 四腿
    for t, side in ((.26, -1), (.26, 1), (.58, -1), (.58, 1)):
        idx = int(t * (len(pts) - 1))
        bx, by = pts[idx]
        kx, ky = bx + side * 8 * s, by + 26 * s
        fx, fy = kx + side * 3 * s, ky + 16 * s
        draw.line([(bx, by), (kx, ky)], fill=CYAN, width=2)
        draw.line([(kx, ky), (fx, fy)], fill=LIGHT, width=2)
        for claw in (-1, 0, 1):
            draw.line([(fx, fy), (fx - 8 * s + claw * 3 * s, fy + 5 * s)], fill=FOAM, width=1)

    if labels:
        # 标注
        draw.text((cx + length * .34, cy - length * .13), "四角", fill=DIM, font=font(13))
        draw.text((cx + length * .18, cy + length * .12), "两口长龙须", fill=DIM, font=font(13))
        draw.text((cx - length * .08, cy - length * .17), "背鳍（水膜）", fill=DIM, font=font(13))
        draw.text((cx - length * .44, cy + length * .06), "尾鳍", fill=DIM, font=font(13))
        draw.text((cx - length * .02, cy + length * .28), "4 层管身 / 三圈水环", fill=DIM, font=font(13))


def panel(draw, x0, y0, w, h, title):
    draw.rectangle([x0, y0, x0 + w, y0 + h], fill=PANEL, outline=(34, 62, 88))
    draw.text((x0 + 14, y0 + 10), title, fill=TEXT, font=font(17))


def flight(draw, x0, y0):
    """三段：水涡 → 弧线飞行 → 入体。"""
    cx, cy = x0 + 150, y0 + 250
    # ① 水涡
    for k, r in enumerate((16, 24, 32)):
        draw.arc([cx - 190 - r, cy - 60 - r, cx - 190 + r, cy - 60 + r], 0, 360,
                 fill=CYAN if k != 1 else FOAM, width=1)
    for i in range(6):
        a = i * math.tau / 6
        draw.line([(cx - 190, cy - 60), (cx - 190 + math.cos(a) * 30, cy - 60 + math.sin(a) * 30)],
                  fill=CYAN, width=1)
    draw.text((cx - 175, cy + 8), "① 水涡：先亮一下", fill=DIM, font=font(13))

    # ② 贝塞尔：从水涡到胸口，往上拱
    from_p = (cx - 190, cy - 60)
    to_p = (cx + 20, cy - 110)
    ctrl = ((from_p[0] + to_p[0]) / 2, cy - 235)
    path = []
    for i in range(41):
        f = i / 40
        g = 1 - f
        path.append((g * g * from_p[0] + 2 * g * f * ctrl[0] + f * f * to_p[0],
                     g * g * from_p[1] + 2 * g * f * ctrl[1] + f * f * to_p[1]))
    draw.line(path, fill=(40, 90, 120), width=1)
    # 龙在 40% 处的剪影
    at = path[16]
    ahead = path[18]
    ang = math.atan2(ahead[1] - at[1], ahead[0] - at[0])
    # 局部画一条缩小版
    small = []
    L = 120
    for i in range(12):
        t = i / 11
        x = at[0] + math.cos(ang) * (.5 - t) * L + math.sin(ang) * math.sin(t * 5.4) * L * .05
        y = at[1] + math.sin(ang) * (.5 - t) * L - math.cos(ang) * math.sin(t * 5.4) * L * .05
        small.append((x, y))
    for width, color in ((5, DEEP), (3, CYAN), (1, FOAM)):
        for i in range(len(small) - 1):
            draw.line([small[i], small[i + 1]], fill=color, width=width)
    draw.polygon([(small[0][0] + 12, small[0][1]), (small[0][0] - 8, small[0][1] - 9),
                  (small[0][0] - 8, small[0][1] + 9)], fill=LIGHT)
    draw.text((cx - 120, cy - 250), "② 沿弧线飞向胸口（全长 3~3.9 格）", fill=DIM, font=font(13))

    # ③ 入体水爆
    draw.ellipse([to_p[0] - 26, to_p[1] - 26, to_p[0] + 26, to_p[1] + 26], outline=CYAN, width=2)
    draw.ellipse([to_p[0] - 14, to_p[1] - 14, to_p[0] + 14, to_p[1] + 14], outline=FOAM, width=1)
    for i in range(6):
        a = i * math.tau / 6
        draw.line([(to_p[0], to_p[1]),
                   (to_p[0] + math.cos(a) * 40, to_p[1] + math.sin(a) * 40)], fill=(120, 200, 230), width=1)
    # 玩家
    draw.ellipse([to_p[0] - 9, to_p[1] + 26, to_p[0] + 9, to_p[1] + 62], outline=FOAM, width=2)
    draw.text((to_p[0] - 22, to_p[1] + 66), "玩家", fill=DIM, font=font(13))
    draw.text((cx + 60, cy - 160), "③ 入体：胸口一圈水爆", fill=DIM, font=font(13))


canvas = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(canvas)
draw.text((22, 8), "水法读条前戏：水龙（照雷法小雷龙的路子做，但更大只）", fill=TEXT, font=font(22))
panel(draw, 20, 42, 620, 410, "水龙侧视：管身 + 水膜鳍 + 龙须（比例示意）")
dragon(draw, 330, 300, 500, labels=True)
panel(draw, 660, 42, 680, 410, "读条三拍：水涡 → 飞行 → 入体")
flight(draw, 660, 42)
canvas.save(r"D:\ModTest\docs\spells\水法读条水龙-concept.png")
print("saved", canvas.size)
