"""工程文件：把图标画成"一眼看懂是什么法术"的剪影。

- 64x64 画矢量（可以画曲线和尖角），再 BOX 缩到 16x16，最后把每个像素吸附到
  三色（暗边 / 主体 / 高光），得到和旧图标一样的硬边像素块。
- 每个图标 = 一个能认出来的主体剪影 + 内核高光 + 暗边 + 少量碎屑。
"""

import json
import math
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

N = 64
S = 16
C = (32.0, 32.0)
OUT = pathlib.Path('src/main/resources/assets/tnc/textures/spell')
ENTRIES = pathlib.Path('build/spell_entries.json')

PALETTE = {
    'THUNDER': ((56, 40, 8), (232, 192, 64), (248, 248, 208)),
    'FIRE': ((72, 16, 0), (224, 80, 24), (248, 208, 128)),
    'WIND': ((8, 48, 48), (96, 208, 192), (216, 248, 240)),
    'WATER': ((0, 24, 56), (40, 144, 224), (192, 240, 248)),
    'EARTH': ((48, 30, 12), (160, 112, 56), (232, 200, 144)),
    'LIGHT': ((56, 40, 0), (240, 200, 96), (248, 248, 232)),
    'DARK': ((16, 8, 32), (104, 56, 168), (200, 168, 240)),
}

TARGETS = {
    'EARTH_SHOT', 'EARTH_MOVE', 'EARTH_MUD', 'EARTH_RING',
    'LIGHT_MIRROR', 'THUNDER_SEAL', 'WIND_BALL', 'WIND_WEAPON', 'GALE',
}
BY_ID = {'god_descent'}


# ------------------------------------------------------------------ helpers
def sc(pts, k=1.0, dx=0.0, dy=0.0):
    return [(C[0] + (x - C[0]) * k + dx, C[1] + (y - C[1]) * k + dy) for x, y in pts]


def poly(d, pts, col, k=1.0, dx=0.0, dy=0.0):
    d.polygon(sc(pts, k, dx, dy), fill=col)


def solid(d, pts, pal, k=1.0, rim=4.5):
    """主体：暗边（右下偏移）在下面，主体色盖在上面。"""
    poly(d, pts, pal[0], k, rim, rim)
    poly(d, pts, pal[1], k)


def blob(d, cx, cy, r, pal, k=1.0, rim=4.5):
    box = [C[0] + (cx - r - C[0]) * k, C[1] + (cy - r - C[1]) * k,
           C[0] + (cx + r - C[0]) * k, C[1] + (cy + r - C[1]) * k]
    d.ellipse([box[0] + rim, box[1] + rim, box[2] + rim, box[3] + rim], fill=pal[0])
    d.ellipse(box, fill=pal[1])


def ring(d, cx, cy, r, col, k=1.0, w=3.0):
    d.ellipse([C[0] + (cx - r - C[0]) * k, C[1] + (cy - r - C[1]) * k,
               C[0] + (cx + r - C[0]) * k, C[1] + (cy + r - C[1]) * k],
              outline=col, width=max(1, int(round(w * k))))


def bez(p0, p1, p2, n=14):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
            for t in (i / n for i in range(n + 1))]


def taper(d, pts, w0, w1, col, k=1.0):
    """沿折线画一条两端粗细不同的带子（风的流线、裂纹、光带）。"""
    left, right = [], []
    n = len(pts) - 1
    for i, (x, y) in enumerate(pts):
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n:
            dx, dy = x - pts[n - 1][0], y - pts[n - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        w = (w0 + (w1 - w0) * i / n) * 0.5
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    d.polygon(sc(left + right[::-1], k), fill=col)


def spike(d, x0, y0, x1, y1, half, pal, k=1.0):
    """一根尖锐的石刺 / 冰锥。"""
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / ln * half, dx / ln * half
    tip = (x1, y1)
    base_l, base_r = (x0 + nx, y0 + ny), (x0 - nx, y0 - ny)
    solid(d, [base_l, tip, base_r], pal, k)
    poly(d, [(x0, y0), ((x0 + x1) / 2 - nx * .3, (y0 + y1) / 2 - ny * .3),
             (x1, y1)], pal[2], k)


def dust(d, pts, col, k=1.0, r=1.6):
    for x, y in pts:
        X, Y = sc([(x, y)], k)[0]
        d.rectangle([X - r, Y - r, X + r, Y + r], fill=col)


# ------------------------------------------------------------------ earth
def g_earth_shot(d, pal, tier, k):
    """岩弹：一块尖石头被射出去，后面拖着三道风。"""
    shard = [(34, 6), (46, 22), (40, 38), (30, 34), (24, 46), (22, 26), (26, 14)]
    solid(d, shard, pal, k)
    poly(d, [(32, 12), (41, 23), (37, 34), (31, 30), (28, 20)], pal[2], k)
    for i in range(tier):
        y = 18 + i * 7
        taper(d, bez((8, y + 4), (16, y), (22, y + 2)), 2, 6, pal[2], k)

def g_earth_move(d, pal, tier, k):
    """地动：地面裂开，石刺从底下顶上来。"""
    base = [(4, 52), (14, 46), (24, 51), (34, 45), (44, 51), (54, 46), (60, 52)]
    poly(d, base, pal[0], k)
    poly(d, [(4, 50), (14, 44), (24, 49), (34, 43), (44, 49), (54, 44), (60, 50)], pal[1], k)
    n = min(5, 1 + tier)
    xs = [14, 26, 38, 48, 20]
    for i in range(n):
        x = xs[i]
        h = 26 + (i % 2) * 8 + tier
        spike(d, x, 48, x + (i % 3 - 1) * 5, 48 - h, 7, pal, k)

def g_earth_mud(d, pal, tier, k):
    """泥潭：一汪泥，中间是个往下陷的洞。"""
    blob(d, 32, 34, 25, pal, k)
    d.ellipse([C[0] - 17 * k, C[1] - 13 * k, C[0] + 17 * k, C[1] + 9 * k], fill=pal[0])
    d.ellipse([C[0] - 13 * k, C[1] - 10 * k, C[0] + 13 * k, C[1] + 6 * k], fill=pal[2])
    d.ellipse([C[0] - 10 * k, C[1] - 8 * k, C[0] + 10 * k, C[1] + 4 * k], fill=pal[1])
    spiral = bez((32, 34), (40, 28), (32, 22)) + bez((32, 22), (24, 24), (26, 30))[1:]
    taper(d, spiral, 2, 4, pal[0], k)
    for i in range(tier - 1):
        a = -2.4 + i * 0.8
        ring(d, 32 + math.cos(a) * 26, 34 + math.sin(a) * 16, 3.5, pal[2], k, 2)

def g_earth_ring(d, pal, tier, k):
    """石环：一圈石头立成环，中间在发亮。"""
    n = 6 + tier
    for i in range(n):
        a = i * math.tau / n
        x, y = 32 + math.cos(a) * 24, 32 + math.sin(a) * 24
        w, h = 8, 9
        solid(d, [(x - w, y - h + 2), (x - w + 2, y - h), (x + w - 2, y - h + 1),
                  (x + w, y + h - 2), (x + w - 2, y + h), (x - w + 2, y + h - 1)], pal, k)
        poly(d, [(x - w + 3, y - h + 3), (x + 1, y - h + 2), (x + 1, y), (x - w + 4, y + 1)],
             pal[2], k)
    ring(d, 32, 32, 11, pal[2], k, 2)
    blob(d, 32, 32, 4, pal, k)

# ------------------------------------------------------------------ light
def g_light_mirror(d, pal, tier, k):
    """光镜：一块立着的水晶，斜着切过一道反光。"""
    shard = [(32, 5), (43, 22), (39, 47), (25, 45), (21, 22)]
    solid(d, shard, pal, k)
    taper(d, [(25, 40), (40, 16)], 3, 3, pal[2], k)
    taper(d, [(27, 42), (38, 20)], 1.5, 1.5, pal[1], k)
    poly(d, [(32, 9), (36, 22), (32, 30), (28, 22)], pal[2], k)
    for i in range(tier + 1):
        a = -2.7 + i * 0.42
        dust(d, [(32 + math.cos(a) * 27, 28 + math.sin(a) * 27)], pal[2], k, 1.5)

# ---------------------------------------------------------------- thunder
def g_thunder_seal(d, pal, tier, k):
    """雷印：地上的法印，中间一道雷楔。"""
    ring(d, 32, 32, 24, pal[1], k, 4)
    ring(d, 32, 32, 15, pal[2], k, 2)
    n = 2 + tier
    for i in range(n):
        a = -math.pi / 2 + i * math.tau / n
        taper(d, [(32 + math.cos(a) * 16, 32 + math.sin(a) * 16),
                  (32 + math.cos(a) * 26, 32 + math.sin(a) * 26)], 5, 2, pal[2], k)
    bolt = [(35, 18), (26, 34), (31, 34), (28, 46), (40, 29), (34, 29), (39, 18)]
    solid(d, bolt, pal, k)


# ------------------------------------------------------------------ wind
def g_wind_ball(d, pal, tier, k):
    """风球：一团打着旋的风，后面拖着尾巴。"""
    r = 13 + tier
    blob(d, 34, 32, r, pal, k)
    ring(d, 34, 32, r - 5, pal[2], k, 2)
    taper(d, bez((30, 24), (14, 18), (10, 34)), 5, 1, pal[1], k)
    taper(d, bez((38, 42), (22, 52), (12, 44)), 4, 1, pal[1], k)
    for i in range(tier - 1):
        a = -2.0 + i * 0.7
        dust(d, [(34 + math.cos(a) * (r + 8), 32 + math.sin(a) * (r + 8))], pal[2], k, 1.4)

def g_wind_weapon(d, pal, tier, k):
    """风刃：一柄弯刀。"""
    n = 1 + tier // 3
    for i in range(n):
        off = (i - (n - 1) / 2) * 18
        blade = bez((18 + off, 52), (48 + off, 34), (40 + off, 8))
        taper(d, blade, 2, 11, pal[1], k)
        edge = bez((19 + off, 54), (50 + off, 35), (42 + off, 9))
        taper(d, edge, 1, 3, pal[2], k)
        solid(d, [(14 + off, 50), (26 + off, 46), (28 + off, 54), (16 + off, 58)], pal, k)
    for i in range(tier - 1):
        dust(d, [(8 + i * 4, 18 + i * 5)], pal[2], k, 1.3)

def g_gale(d, pal, tier, k):
    """狂风：几道打了卷的风。"""
    n = 1 + tier // 2
    for i in range(n):
        y = 16 + i * (32 / max(1, n - 1) if n > 1 else 0)
        main = bez((6, y + 6), (32, y - 10), (54, y))
        taper(d, main, 2, 7, pal[1], k)
        curl = bez((54, y), (60, y - 5), (50, y - 7))
        taper(d, curl, 4, 1, pal[1], k)
        taper(d, bez((12, y + 8), (34, y - 4), (48, y + 3)), 1, 2.5, pal[2], k)

# --------------------------------------------------------------- special
def g_god_descent(d, pal, tier, k):
    """神在投篮：雷球正落进地上那个圈（篮筐）。"""
    ring(d, 32, 46, 15, pal[2], k, 3)
    ring(d, 32, 46, 15, pal[1], k, 1)
    blob(d, 32, 18, 12, pal, k)
    d.ellipse([C[0] - 5 * k, C[1] - 18 * k, C[0] + 2 * k, C[1] - 10 * k], fill=pal[2])
    for i in range(2):
        taper(d, bez((24 - i * 4, 10), (18 - i * 4, 22), (22 - i * 4, 32)), 2, 5, pal[1], k)
        taper(d, bez((40 + i * 4, 10), (46 + i * 4, 22), (42 + i * 4, 32)), 2, 5, pal[1], k)
    for i in range(min(4, tier)):
        dust(d, [(12 + i * 13, 56)], pal[2], k, 1.3)

GLYPHS = {
    'EARTH_SHOT': g_earth_shot,
    'EARTH_MOVE': g_earth_move,
    'EARTH_MUD': g_earth_mud,
    'EARTH_RING': g_earth_ring,
    'LIGHT_MIRROR': g_light_mirror,
    'THUNDER_SEAL': g_thunder_seal,
    'WIND_BALL': g_wind_ball,
    'WIND_WEAPON': g_wind_weapon,
    'GALE': g_gale,
}
ID_GLYPHS = {'god_descent': g_god_descent}


def quantise(img, pal):
    out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = out.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = img.getpixel((x, y))
            if a < 120:
                continue
            best, bi = 1e9, 1
            for i in range(3):
                cr, cg, cb = pal[i]
                dist = (r - cr) ** 2 + (g - cg) ** 2 + (b - cb) ** 2
                if dist < best:
                    best, bi = dist, i
            px[x, y] = pal[bi] + (255,)
    return out


def build(element, tier, glyph):
    pal = PALETTE[element]
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = 0.80 + 0.045 * tier
    glyph(d, pal, tier, k)
    return quantise(img.resize((S, S), Image.BOX), pal)


def main():
    entries = json.loads(ENTRIES.read_text(encoding='utf-8'))
    OUT.mkdir(parents=True, exist_ok=True)
    drawn = kept = 0
    made = []
    for e in entries:
        glyph = ID_GLYPHS.get(e['id']) or (GLYPHS[e['chain']] if e['chain'] in TARGETS else None)
        if glyph:
            build(e['element'], e['tier'], glyph).save(OUT / (e['id'] + '.png'))
            made.append(e)
            drawn += 1
        else:
            if not (OUT / (e['id'] + '.png')).exists():
                raise SystemExit('missing icon and no glyph: ' + e['id'])
            kept += 1
    print('drawn', drawn, '| kept', kept)

    if '--sheet' in sys.argv:
        zoom, pad, lab = 5, 4, 96
        cell = S * zoom
        order = ['EARTH_SHOT', 'EARTH_MOVE', 'EARTH_MUD', 'EARTH_RING', 'LIGHT_MIRROR',
                 'THUNDER_SEAL', 'WIND_BALL', 'WIND_WEAPON', 'GALE']
        rows = [[e for e in made if e['chain'] == c] for c in order]
        rows.append([e for e in made if e['id'] == 'god_descent'])
        rows = [r for r in rows if r]
        W = lab + 5 * (cell + pad) + pad
        H = pad + len(rows) * (cell + pad)
        sheet = Image.new('RGBA', (W, H), (20, 22, 30, 255))
        dr = ImageDraw.Draw(sheet)
        try:
            font = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 10)
        except Exception:
            font = ImageFont.load_default()
        for r, row in enumerate(rows):
            y = pad + r * (cell + pad)
            dr.text((3, y + cell // 2 - 5), row[0]['chain'][:15], fill=(200, 206, 220, 255), font=font)
            for i, e in enumerate(sorted(row, key=lambda z: z['tier'])):
                im = Image.open(OUT / (e['id'] + '.png')).convert('RGBA')
                sheet.alpha_composite(im.resize((cell, cell), Image.NEAREST),
                                      (lab + i * (cell + pad), y))
        sheet.save('build/new_icons.png')
        print('preview build/new_icons.png', sheet.size)


if __name__ == '__main__':
    main()
