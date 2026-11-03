"""刀光的离线形状预览（不用起客户端）。

这个脚本把 `WarriorTrail` / `WarriorTrailCurve` 里的几何**照抄一遍**（Catmull-Rom 插值、
按 4° 自适应细分、逐段四边形、三层叠加、外沿羽化），然后把同一次挥砍分别用"以前的做法"
和"现在的做法"画出来做对比。用途只有一个：在拿不到游戏内截图的时候，至少能看见
"顺滑"到底顺在哪、羽化有没有把硬边抹掉。

注意：**这不是游戏截图**，是纯几何重绘；颜色/亮度是近似值（游戏里走的是加色混合 +
顶点色，这里是 source-over 叠近似）。真正的手感还得进游戏看。

用法：
    python tools/trail_preview.py                 # 写 docs/previews/trail-20261103.png
    python tools/trail_preview.py --out a.png --scale 2
"""

from __future__ import annotations

import argparse
import math
import os

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- 常量（对齐 Java 侧）

MAX_SEGMENT_DEGREES = 4.0   # WarriorTrailCurve.MAX_SEGMENT_DEGREES
MAX_SUBDIVISIONS = 12       # WarriorTrailCurve.MAX_SUBDIVISIONS
GHOST_FEATHER = 0.14        # WarriorTrail.GHOST_FEATHER
HILT_RATIO = 0.10           # WarriorTrail.HILT_RATIO
MAX_SAMPLES = 320

# 长剑那一档的手感（LONGSWORD_PROFILE / Shape）
LIFE_MS = 200
OUTER_ALPHA = 0.50
CORE_ALPHA = 1.00
OUTER_START = 0.18
CORE_START = 0.54
FALLOFF_OUTER = 1.5
FALLOFF_CORE = 0.8
GHOST_SCALE = 1.90          # 磨砺练满：WarriorPhantom.MAX_GROWTH = 0.90

FRAMES = 12
FPS = 60.0
BLADE = 1.25                # 剑长（格）
WRIST = 0.42                # 手腕离肩多远（格）
SWING_FROM = math.radians(-140.0)
SWING_TO = math.radians(55.0)


# ---------------------------------------------------------------- 几何

def catmull(p0, p1, p2, p3, t):
    """标准 Catmull-Rom（张力 0.5），和 Java 侧逐项对应。"""
    t2 = t * t
    t3 = t2 * t
    out = []
    for a, b, c, d in zip(p0, p1, p2, p3):
        out.append(0.5 * ((2 * b)
                          + (-a + c) * t
                          + (2 * a - 5 * b + 4 * c - d) * t2
                          + (-a + 3 * b - 3 * c + d) * t3))
    return tuple(out)


def subdivisions(deg):
    """按转角决定插几段：每段 ≤ 4°，最多 12 段，退化输入返回 1。"""
    if deg is None or not math.isfinite(deg) or deg <= MAX_SEGMENT_DEGREES:
        return 1
    return max(1, min(MAX_SUBDIVISIONS, int(math.ceil(deg / MAX_SEGMENT_DEGREES))))


def limit(steps, budget):
    """顶点预算：整体等比降密（每段至少 1），照抄 WarriorTrailCurve.limit。"""
    if not steps:
        return list(steps)
    total = sum(max(1, n) for n in steps)
    if budget >= total or budget <= 0:
        return list(steps)
    spare = max(0, budget - len(steps))
    denom = max(1, total - len(steps))
    out = []
    for n in steps:
        n = max(1, n)
        if n == 1 or spare == 0:
            out.append(1)
        else:
            out.append(max(1, 1 + round((n - 1) * spare / denom)))
    return out


def smoothstep(x):
    return x * x * (3.0 - 2.0 * x)


def blade_pose(frame):
    """第 frame 帧的姿态：返回 (hilt, tip, ghost_tip)。快速挥砍用 ease-out，前段快。"""
    u = smoothstep(frame / (FRAMES - 1.0))
    angle = SWING_FROM + (SWING_TO - SWING_FROM) * u
    # 手腕跟着甩，但只走一小段弧 —— 于是剑尖画出的大弧线更"开"
    wrist = (WRIST * math.cos(angle + math.radians(35.0)),
             WRIST * math.sin(angle + math.radians(35.0)) - 0.08)
    tip = (wrist[0] + BLADE * math.cos(angle), wrist[1] + BLADE * math.sin(angle))
    ghost = (wrist[0] + (tip[0] - wrist[0]) * GHOST_SCALE,
             wrist[1] + (tip[1] - wrist[1]) * GHOST_SCALE)
    return wrist, tip, ghost


SAMPLES = [blade_pose(i) for i in range(FRAMES)]
STAMPS = [i * (1000.0 / FPS) for i in range(FRAMES)]
NOW = STAMPS[-1]


def angle_between(a, b):
    la = math.hypot(*a)
    lb = math.hypot(*b)
    if la < 1e-9 or lb < 1e-9:
        return 0.0
    cos = max(-1.0, min(1.0, (a[0] * b[0] + a[1] * b[1]) / (la * lb)))
    return math.degrees(math.acos(cos))


def old_curve():
    """以前：固定每两帧插 3 个点，直线连。"""
    points = []
    for i in range(FRAMES):
        points.append((SAMPLES[i], STAMPS[i]))
        if i < FRAMES - 1:
            for k in (1, 2, 3):
                t = k / 4.0
                a, b = SAMPLES[i], SAMPLES[i + 1]
                mid = (a[0][0] + (b[0][0] - a[0][0]) * t,
                       a[0][1] + (b[0][1] - a[0][1]) * t)
                mip = (a[1][0] + (b[1][0] - a[1][0]) * t,
                       a[1][1] + (b[1][1] - a[1][1]) * t)
                points.append(((mid, mip, mip), STAMPS[i] + (STAMPS[i + 1] - STAMPS[i]) * t))
    return points


def new_curve():
    """现在：逐段按转角自适应细分 + Catmull-Rom，超预算再整体降密。"""
    steps = []
    for i in range(FRAMES - 1):
        a = (SAMPLES[i + 1][1][0] - SAMPLES[i][1][0],
             SAMPLES[i + 1][1][1] - SAMPLES[i][1][1])
        b = (SAMPLES[min(FRAMES - 1, i + 2)][1][0] - SAMPLES[i][1][0],
             SAMPLES[min(FRAMES - 1, i + 2)][1][1] - SAMPLES[i][1][1])
        steps.append(subdivisions(angle_between(a, b)))
    if 1 + sum(steps) > MAX_SAMPLES:
        steps = limit(steps, MAX_SAMPLES - FRAMES)
    out = [(SAMPLES[0], STAMPS[0])]
    for i in range(FRAMES - 1):
        p0 = SAMPLES[max(0, i - 1)]
        p1 = SAMPLES[i]
        p2 = SAMPLES[i + 1]
        p3 = SAMPLES[min(FRAMES - 1, i + 2)]
        n = steps[i]
        for s in range(1, n + 1):
            if s == n:
                out.append((p2, STAMPS[i + 1]))
                continue
            t = s / n
            hilt = catmull(p0[0], p1[0], p2[0], p3[0], t)
            tip = catmull(p0[1], p1[1], p2[1], p3[1], t)
            ghost = catmull(p0[2], p1[2], p2[2], p3[2], t)
            out.append(((hilt, tip, ghost), STAMPS[i] + (STAMPS[i + 1] - STAMPS[i]) * t))
    return out


def fade_of(stamp, life, falloff):
    age = NOW - stamp
    if age <= 0:
        return 1.0
    if age >= life:
        return 0.0
    return min(1.0, max(0.0, (1.0 - age / life) ** falloff))


def lerp(a, b, t):
    return a + (b - a) * t


def bands(curve, tint, alpha, life, start_fraction, falloff, ghost, feather):
    """把曲线铺成一串四边形；返回 [(p0,p1,p2,p3, a0,a1,a2,a3), ...]（透明度已含衰减）。"""
    out = []
    last = len(curve) - 1
    prev_start = prev_end = prev_outer = None
    prev = None
    for i, (pose, stamp) in enumerate(curve):
        fade = fade_of(stamp, life, falloff)
        if fade <= 0.02:
            if prev_start is None:
                continue
            break
        head = (i / last) ** 4.0
        start_frac = lerp(start_fraction, 0.94, 1.0 - fade)
        bulge = 1.0 if ghost else 1.0 + 0.13 * head
        tip_point = pose[2] if ghost else pose[1]
        sp = (lerp(pose[0][0], pose[1][0], start_frac), lerp(pose[0][1], pose[1][1], start_frac))
        ep = (lerp(pose[0][0], tip_point[0], bulge), lerp(pose[0][1], tip_point[1], bulge))
        op = None
        if feather > 0:
            op = (lerp(pose[0][0], tip_point[0], bulge + feather),
                  lerp(pose[0][1], tip_point[1], bulge + feather))
        boost = (1.0 + 0.40 * head) * lerp(1.0, 1.24, head)
        weight = min(1.0, max(0.0, alpha * boost * fade))
        sa = weight * HILT_RATIO
        ea = weight
        if prev_start is not None:
            out.append((prev_start, prev_end, ep, sp, prev[0], prev[1], ea, sa))
            if op is not None:
                out.append((prev_end, prev_outer, op, ep, prev[1], 0.0, ea, 0.0))
        prev_start, prev_end, prev_outer = sp, ep, op
        prev = (sa, ea)
    return out


# ---------------------------------------------------------------- 画

def to_px(point, cx, cy, scale):
    return (cx + point[0] * scale, cy - point[1] * scale)


def draw_quad(draw, quad, color, rings=4):
    """一个四边形 → 平涂 + 几圈"内缩"环，近似游戏里的径向渐隐。

    <p>（PIL 的多边形只能纯色，逐格插值会把接近 0 的透明度量化成一条条锯齿边，
    看起来像我们的光带本身有毛刺 —— 那是画图的锅，不是渲染的锅。所以这里改成：
    整片平涂一层，再往里缩一半、缩到底各补一层，三圈叠出"从刃口往外渐隐"的观感。）
    """
    a, b, c, d, a0, b0, c1, d0 = quad
    base = (a0 + b0 + c1 + d0) * 0.25
    for ring in range(rings):
        # v 从 0（握把）到 1（刃口）：内缩环只画靠刃口那一段
        v0 = ring / (rings + 1.0)
        p = [a, b, c, d]
        inner = [
            (lerp(a[0], b[0], v0), lerp(a[1], b[1], v0)),
            b,
            c,
            (lerp(d[0], c[0], v0), lerp(d[1], c[1], v0)),
        ]
        weight = 1.0 if ring == 0 else 0.55 / ring
        alpha = min(1.0, max(0.0, base * weight * 1.55))
        if alpha <= 0.006:
            continue
        draw.polygon(inner if ring else p, fill=color + (int(255 * alpha),))


def model_bounds(curve):
    xs, ys = [], []
    for pose, _ in curve:
        for point in pose:
            xs.append(point[0])
            ys.append(point[1])
    return min(xs), min(ys), max(xs), max(ys)


def render_panel(mode, size):
    """mode: 'old' | 'new'。返回 RGBA 图（自动按图形范围取景，不会被裁掉）。"""
    w, h = size
    canvas = Image.new("RGBA", (w, h), (9, 11, 16, 255))
    draw = ImageDraw.Draw(canvas, "RGBA")
    curve = new_curve() if mode == "new" else old_curve()
    # 羽化会让外圈再往外扩一点，取景时要把这部分也算进去
    x0, y0, x1, y1 = model_bounds(curve)
    x0 -= GHOST_FEATHER * BLADE * 1.1
    x1 += GHOST_FEATHER * BLADE * 1.1
    y0 -= GHOST_FEATHER * BLADE * 1.1
    y1 += GHOST_FEATHER * BLADE * 1.1
    span_x = max(0.6, x1 - x0)
    span_y = max(0.6, y1 - y0)
    scale = min((w - 48) / span_x, (h - 110) / span_y)
    mx, my = (x0 + x1) * 0.5, (y0 + y1) * 0.5
    cx = w * 0.5 - mx * scale                  # 模型水平中心 → 画面中心
    cy = h * 0.5 + 18 + my * scale             # 模型垂直中心 → 画面中心（y 轴朝上）

    # 剑的轨迹（细灰线）当参考：让人看得出"光带该贴着这条弧"
    path = [to_px(p[1], cx, cy, scale) for p in SAMPLES]
    draw.line(path, fill=(70, 78, 92, 150), width=1)

    if mode == "old":
        # 单层、无羽化、无虚影（以前就是这样）
        for quad in bands(curve, None, OUTER_ALPHA * 1.30, LIFE_MS, OUTER_START, 1.2, False, 0.0):
            p = tuple(to_px(q, cx, cy, scale) for q in quad[:4])
            draw_quad(draw, (p[0], p[1], p[2], p[3], *quad[4:]), (176, 214, 255))
    else:
        # 外圈斩气：走虚影剑尖 + 外沿羽化；内芯：走真剑尖；剑影：剑尖一条细线
        for quad in bands(curve, None, OUTER_ALPHA, LIFE_MS, OUTER_START, FALLOFF_OUTER, True, GHOST_FEATHER):
            p = tuple(to_px(q, cx, cy, scale) for q in quad[:4])
            draw_quad(draw, (p[0], p[1], p[2], p[3], *quad[4:]), (120, 160, 235))
        for quad in bands(curve, None, CORE_ALPHA, LIFE_MS, CORE_START, FALLOFF_CORE, False, 0.0):
            p = tuple(to_px(q, cx, cy, scale) for q in quad[:4])
            draw_quad(draw, (p[0], p[1], p[2], p[3], *quad[4:]), (236, 246, 255))
        # 剑尖剑影
        for i in range(1, len(curve)):
            (a, b) = curve[i - 1], curve[i]
            fade = fade_of(b[1], LIFE_MS, 1.0)
            if fade <= 0.03:
                continue
            draw.line([to_px(a[0][1], cx, cy, scale), to_px(b[0][1], cx, cy, scale)],
                      fill=(255, 255, 255, int(150 * fade)), width=max(1, int(scale * 0.012)))
    return canvas


def label(canvas, text, xy, font):
    draw = ImageDraw.Draw(canvas, "RGBA")
    x, y = xy
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        draw.text((x + dx, y + dy), text, font=font, fill=(0, 0, 0, 220))
    draw.text(xy, text, font=font, fill=(228, 234, 244, 255))


def load_font(size):
    for path in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyhl.ttc",
                 r"C:\Windows\Fonts\simhei.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join("docs", "previews", "trail-20261103.png"))
    ap.add_argument("--scale", type=int, default=2, help="超采样倍数（2 = 放大后再缩，边缘更干净）")
    args = ap.parse_args()

    panel_w, panel_h = 640, 460
    w, h = panel_w * 2, panel_h
    big = (w * args.scale, h * args.scale)

    old = render_panel("old", (panel_w * args.scale, panel_h * args.scale))
    new = render_panel("new", (panel_w * args.scale, panel_h * args.scale))

    sheet = Image.new("RGBA", big, (9, 11, 16, 255))
    sheet.alpha_composite(old, (0, 0))
    sheet.alpha_composite(new, (panel_w * args.scale, 0))
    sheet = sheet.resize((w, h), Image.LANCZOS)

    font = load_font(20)
    small = load_font(14)
    label(sheet, "以前：固定插 3 点 / 单层 / 没有羽化", (18, 16), font)
    label(sheet, "现在：按 4° 自适应 + 三层 + 外沿羽化 + 走虚影剑尖", (panel_w + 18, 16), font)
    label(sheet, "细灰线 = 剑尖真实轨迹；右边外圈已扩到虚影剑尖（1.90x）",
          (18, h - 30), small)
    draw = ImageDraw.Draw(sheet, "RGBA")
    draw.line([(panel_w, 0), (panel_w, h)], fill=(60, 66, 80, 255), width=2)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    sheet.save(args.out)
    print(f"wrote {args.out} ({sheet.size[0]}x{sheet.size[1]})")
    print(f"old curve points: {len(old_curve())},  new curve points: {len(new_curve())}")


if __name__ == "__main__":
    main()
