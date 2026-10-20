# -*- coding: utf-8 -*-
"""把 12 只地下城小怪**真的渲染出来**，给作者看长相。

为什么要有这个脚本
------------------
这个仓库的模型是 GeckoLib 的 bedrock geo（轴对齐立方体 + 盒式 UV），
所以完全可以用正交投影 + 逐面贴图在离线把静息姿势画出来 —— 不需要客户端、不需要 OpenGL。
它不替代游戏里看效果，但足够回答"长啥样 / 比例对不对 / 贴图有没有跑偏"。

管线
----
geo.json 的 cubes(origin,size,uv) → 6 个面 → 背面剔除（法线 · 视线）
→ 正交投影到屏幕（偏航 35°、俯角 18°）→ 每个面用仿射变换把贴图那块抠出来贴上
→ 按深度排序画家算法 → 深色描边（手绘精描风的关键）。

输出
----
docs/previews/mobs/<id>.png     每只一张大图（512×620）
docs/previews/dungeon_mobs_preview.png   12 只合照
"""
import json
import math
import os
import sys
import re

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "geo", "entity")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "textures", "entity")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "lang", "zh_cn.json")
JAVA = os.path.join(
    ROOT, "src", "main", "java", "com", "tnc", "tnc", "dungeon", "DungeonSpecies.java")
OUT_DIR = os.path.join(ROOT, "docs", "previews", "mobs")
SHEET = os.path.join(ROOT, "docs", "previews", "dungeon_mobs_preview.png")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mob_ids import ids as _mob_ids  # noqa: E402  (single source of truth: DungeonSpecies.java)

IDS = _mob_ids()

YAW = math.radians(35.0)
PITCH = math.radians(18.0)
LIGHT = np.array([0.38, 0.86, 0.34])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\simsun.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def lang_names():
    with open(LANG, encoding="utf-8") as f:
        table = json.load(f)
    return {i: table.get("entity.tnc." + i, i) for i in IDS}


def species_stats():
    """从 DungeonSpecies.java 抓 (id -> (hp, dmg))，避免手抄一份会漂移的数值。"""
    src = open(JAVA, encoding="utf-8").read()
    out = {}
    for m in re.finditer(
            r'new DungeonSpecies\(\s*"([a-z_]+)"\s*,\s*Kind\.([A-Z_]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)',
            src):
        out[m.group(1)] = (float(m.group(3)), float(m.group(4)), m.group(2))
    return out


def face_uv(w, h, d, u, v):
    """bedrock 盒式 UV 排布：横 2*(d+w)，纵 d+h。"""
    return {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + w + w, v + d),
        "west": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + d + w + d, v + d + h),
        "south": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


NORMALS = {
    "up": (0, 1, 0), "down": (0, -1, 0),
    "west": (-1, 0, 0), "east": (1, 0, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
}


def corners(name, x0, y0, z0, x1, y1, z1):
    """按 (u0,v0)(u1,v0)(u1,v1)(u0,v1) 的顺序给 4 个角，保证贴图方向正确。"""
    if name == "up":
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    if name == "down":
        return [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]
    if name == "north":
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if name == "south":
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if name == "west":
        return [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)]
    return [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)]


def camera_basis(yaw=YAW):
    cam = np.array([
        math.sin(yaw) * math.cos(PITCH),
        -math.sin(PITCH),
        math.cos(yaw) * math.cos(PITCH),
    ])
    cam /= np.linalg.norm(cam)
    right = np.cross(cam, np.array([0.0, 1.0, 0.0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, cam)
    return cam, right, up


def render(mob_id, size=(512, 620), ss=2, stats=None, name=None, yaw=YAW):
    geo = json.load(open(os.path.join(GEO_DIR, mob_id + ".geo.json"), encoding="utf-8"))
    geometry = geo["minecraft:geometry"][0]
    desc = geometry["description"]
    tex = Image.open(os.path.join(TEX_DIR, mob_id + "_bedrock.png")).convert("RGB")
    tex_w = desc.get("texture_width", 1024)
    tex_h = desc.get("texture_height", 1024)

    boxes = []
    for bone in geometry["bones"]:
        for cube in bone.get("cubes", []):
            ox, oy, oz = cube["origin"]
            sx, sy, sz = cube["size"]
            u, v = cube["uv"]
            boxes.append((ox, oy, oz, ox + sx, oy + sy, oz + sz, u, v, sx, sy, sz))
    if not boxes:
        raise SystemExit("%s 没有方块" % mob_id)

    cam, right, up = camera_basis(yaw)

    # 先把所有角点投影一遍，用来定中心和缩放。
    all_pts = []
    for b in boxes:
        for name in NORMALS:
            for p in corners(name, *b[:6]):
                all_pts.append(p)
    pts = np.array(all_pts, dtype=float)
    center = (pts.min(axis=0) + pts.max(axis=0)) / 2.0
    rel = pts - center
    px = rel @ right
    py = -(rel @ up)
    W, H = size[0] * ss, size[1] * ss
    span_x = max(px.max() - px.min(), 1e-6)
    span_y = max(py.max() - py.min(), 1e-6)
    scale = min(W * 0.78 / span_x, H * 0.74 / span_y)
    cx = W / 2.0 - (px.min() + px.max()) / 2.0 * scale
    cy = H / 2.0 + (py.min() + py.max()) / 2.0 * scale

    def project(p):
        r = np.array(p, dtype=float) - center
        return np.array([cx + (r @ right) * scale, cy - (r @ up) * scale])

    def depth(p):
        return (np.array(p, dtype=float) - center) @ cam

    faces = []
    for b in boxes:
        x0, y0, z0, x1, y1, z1, u, v, sx, sy, sz = b
        rects = face_uv(sx, sy, sz, u, v)
        for name, n in NORMALS.items():
            if np.dot(np.array(n, dtype=float), cam) >= -1e-6:
                continue  # 背面剔除
            quad = [project(p) for p in corners(name, x0, y0, z0, x1, y1, z1)]
            u0, v0, u1, v1 = rects[name]
            shade = 0.56 + 0.44 * max(0.0, np.dot(np.array(n, dtype=float), LIGHT))
            faces.append((float(np.mean([depth(p) for p in corners(name, x0, y0, z0, x1, y1, z1)])),
                          quad, (u0, v0, u1, v1), shade, name))

    faces.sort(key=lambda f: -f[0])  # 远 → 近

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    outline = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(outline)

    for _, quad, (u0, v0, u1, v1), shade, _name in faces:
        q = np.array(quad)
        bw = int(math.ceil(q[:, 0].max() - q[:, 0].min())) + 2
        bh = int(math.ceil(q[:, 1].max() - q[:, 1].min())) + 2
        ox = int(math.floor(q[:, 0].min())) - 1
        oy = int(math.floor(q[:, 1].min())) - 1
        if bw <= 1 or bh <= 1:
            continue
        p00 = q[0]
        ex = q[1] - q[0]
        ey = q[3] - q[0]
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-9:
            continue
        # 屏幕点 → 面内参数 (s,t)；再把 (s,t) 映射到贴图矩形。
        inv = np.array([[ey[1], -ey[0]], [-ex[1], ex[0]]]) / det
        base = inv @ (np.array([ox, oy], dtype=float) - p00)  # 图层原点处的 (s,t)
        uw = u1 - u0
        vh = v1 - v0
        # src = (u0,v0) + (uw*s, vh*t)
        a = uw * inv[0, 0]
        b = uw * inv[0, 1]
        c = u0 + uw * base[0]
        d = vh * inv[1, 0]
        e = vh * inv[1, 1]
        f = v0 + vh * base[1]
        tile = tex.crop((int(round(u0)), int(round(v0)), int(round(u1)), int(round(v1))))
        if tile.width < 1 or tile.height < 1:
            continue
        warped = tile.transform((bw, bh), Image.AFFINE, (a, b, c - u0, d, e, f - v0),
                                resample=Image.BICUBIC)
        shade_img = Image.new("RGB", (bw, bh), (int(255 * shade),) * 3)
        warped = ImageChops.multiply(warped, shade_img).convert("RGBA")
        mask = Image.new("L", (bw, bh), 0)
        ImageDraw.Draw(mask).polygon([(p[0] - ox, p[1] - oy) for p in quad], fill=255)
        canvas.paste(warped, (ox, oy), mask)
        odraw.polygon([(p[0], p[1]) for p in quad], outline=(28, 22, 18, 200))

    # 描边只留边缘：把描边图层稍微"吃掉"已填充区域内部是做不到的，
    # 直接叠一层半透明线稿即可，观感接近手绘勾线。
    canvas = Image.alpha_composite(canvas, outline)
    canvas = canvas.resize(size, Image.LANCZOS)

    return canvas, {
        "boxes": len(boxes), "faces": len(faces),
        "name": name or mob_id, "stats": stats,
    }


def compose_card(img):
    """把透明底的渲染图放到羊皮纸底上，再补一层落地阴影。

    为什么不直接用深色底：矿灯疣猪/影缚牧师/锈钩屠夫这三只主打暗色，
    深底上的暗模型会糊成一团。浅暖底 + 暗描边才是手绘精描的读法。
    """
    w, h = img.size
    yy = np.linspace(0.0, 1.0, h)[:, None]
    xx = np.linspace(0.0, 1.0, w)[None, :]
    top = np.array([226, 212, 186], dtype=float)
    bottom = np.array([168, 146, 118], dtype=float)
    grad = top[None, None, :] * (1 - yy[:, :, None]) + bottom[None, None, :] * yy[:, :, None]
    # 中间偏上的柔光，让主体后脑有一点点提亮
    glow = np.exp(-(((xx - 0.5) ** 2) / 0.12 + ((yy - 0.42) ** 2) / 0.16))
    grad = grad + glow[:, :, None] * 26.0
    vig = 1.0 - 0.28 * np.clip(((xx - 0.5) ** 2 + (yy - 0.5) ** 2) * 2.4, 0, 1)
    grad = np.clip(grad * vig[:, :, None], 0, 255).astype(np.uint8)
    board = Image.fromarray(grad, "RGB").convert("RGBA")

    alpha = img.split()[-1]
    bbox = alpha.getbbox()
    if bbox:
        # 落地阴影：在脚底附近拉一个模糊椭圆
        shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        cx = (bbox[0] + bbox[2]) // 2
        sw = int((bbox[2] - bbox[0]) * 0.62)
        sh = max(10, int(h * 0.035))
        sd.ellipse([cx - sw // 2, bbox[3] - sh, cx + sw // 2, bbox[3] + sh],
                   fill=(60, 46, 34, 120))
        board.alpha_composite(shadow)

    return Image.alpha_composite(board, img).convert("RGB")


def ascii_silhouette(img, cols=46):
    """我（模型）看不到图，用字符画自己检查一遍剪影对不对。"""
    small = img.split()[-1].resize((cols, max(6, int(cols * img.height / img.width * 0.5))))
    px = np.array(small)
    ramp = " .:-=+*#%@"
    lines = []
    for row in px:
        lines.append("".join(ramp[min(len(ramp) - 1, int(v / 256 * len(ramp)))] for v in row))
    return "\n".join(lines)


def fit(img, box_w, box_h):
    scale = min(box_w / img.width, box_h / img.height)
    return img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))),
                      Image.LANCZOS)


def sheet(renders, names, stats):
    cols = 6 if len(renders) > 12 else 4
    rows = (len(renders) + cols - 1) // cols
    cw, ch = 330, 400
    pad = 18
    head = 96
    W = pad + cols * (cw + pad)
    H = head + rows * (ch + pad) + pad
    board = Image.new("RGBA", (W, H), (32, 28, 24, 255))
    draw = ImageDraw.Draw(board)
    for x in range(0, W, 4):
        for y in range(0, H, 4):
            if ((x // 4) + (y // 4)) % 2 == 0:
                draw.rectangle([x, y, x + 3, y + 3], fill=(35, 30, 26, 255))

    f_title = load_font(34)
    f_name = load_font(22)
    f_small = load_font(15)
    draw.text((pad + 4, 24), "TN-C 地下城小怪 · %d 只" % len(renders),
              font=f_title, fill=(240, 226, 200))
    draw.text((pad + 4, 66),
              "由 geo.json 立方体 + 贴图逐面贴图正交渲染（非游戏截图，静息姿势）",
              font=f_small, fill=(176, 160, 138))

    for i, (img, name, st) in enumerate(zip(renders, names, stats)):
        cx = pad + (i % cols) * (cw + pad)
        cy = head + (i // cols) * (ch + pad)
        draw.rounded_rectangle([cx, cy, cx + cw, cy + ch], radius=10,
                               fill=(58, 48, 38, 255), outline=(120, 100, 78, 255), width=2)
        card = compose_card(img).convert("RGBA")
        thumb = fit(card, cw - 24, ch - 96)
        tw, th = thumb.size
        mask = Image.new("L", (tw, th), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, tw - 1, th - 1], radius=8, fill=255)
        board.paste(thumb, (cx + (cw - tw) // 2, cy + 12), mask)
        draw.text((cx + 14, cy + ch - 74), name, font=f_name, fill=(240, 226, 200))
        if st:
            hp, dmg, kind = st
            draw.text((cx + 14, cy + ch - 44),
                      "%s · HP %g · DMG %g" % (kind, hp, dmg),
                      font=f_small, fill=(190, 176, 152))
        draw.text((cx + 14, cy + ch - 24), IDS[i], font=f_small, fill=(140, 126, 108))
    board.convert("RGB").save(SHEET)
    return SHEET


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    names = lang_names()
    stats = species_stats()
    renders = []
    for mob_id in IDS:
        views = []
        iso = None
        info = None
        for k, yaw in enumerate((YAW, 0.0, math.radians(90.0))):
            img, info = render(mob_id, stats=stats.get(mob_id), name=names[mob_id], yaw=yaw)
            if k == 0:
                iso = img
            views.append(compose_card(img))
        strip = Image.new("RGB", (sum(v.width for v in views), views[0].height), (32, 28, 24))
        x = 0
        for v in views:
            strip.paste(v, (x, 0))
            x += v.width
        strip.save(os.path.join(OUT_DIR, mob_id + ".png"))
        renders.append(iso)
        print("%-20s 方块 %3d 面 %3d  %s" % (mob_id, info["boxes"], info["faces"], names[mob_id]))

    print()
    for mob_id in ("lantern_boar", "moss_carapace_spider", "steam_sentinel"):
        idx = IDS.index(mob_id)
        print("=== %s 剪影 ===" % names[mob_id])
        print(ascii_silhouette(renders[idx]))
        print()

    path = sheet(renders, [names[i] for i in IDS], [stats.get(i) for i in IDS])
    print("written:", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
