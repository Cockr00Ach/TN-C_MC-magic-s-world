# -*- coding: utf-8 -*-
"""
gen_dark_summon_icons.py -- 32x32 icons for the dark chain #3 (召唤: 5 summons).

Author 2026-10-04: "暗魔法的召唤流没实装吗" -> the five summons are now wired up, and the
magic stone needs an icon per spell (it draws assets/tnc/textures/spell/<spell id>.png).

Ids (the pack already defines these five spells):
    summon_dark  (t1, 小恶魔)   summon_elite (t2, 暗卫)   summon_lord (t3, 暗之统领)
    dark_king    (t4, 暗之国王)  evil_god     (t5, 邪神)

Art: dark violet smoke + a bit of soul-fire, drawn as a growing silhouette per tier
(1 blob -> tall hooded figure -> +cape -> +crown -> six-armed god with a ring).
ASCII only.  Usage:  python tools/gen_dark_summon_icons.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
SS = 8
W = SIZE * SS
CORE = (232, 222, 248, 255)       # 幽紫白（影子本体）
ACCENT = (176, 72, 208, 255)      # 紫红镶边
DEEP = (58, 34, 92, 255)          # 深紫（披风/袍）


def layer():
    return Image.new("RGBA", (W, W), (0, 0, 0, 0))


def smoke(d, cx, cy, r, n=7):
    """a puff of dark smoke behind the figure"""
    for i in range(n):
        a = 2 * math.pi * i / n
        rr = r * (0.55 + 0.35 * ((i * 7) % 5) / 4.0)
        x = cx + math.cos(a) * r * 0.55
        y = cy + math.sin(a) * r * 0.35
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(46, 28, 74, 190))


def figure(d, cx, cy, h, cape=False, crown=False, arms=0, ring=False):
    """hooded dark figure; taller tiers add cape / crown / extra arm pairs / a halo ring"""
    if cape:
        d.polygon([(cx - 0.30 * h, cy - 0.20 * h), (cx + 0.30 * h, cy - 0.20 * h),
                   (cx + 0.52 * h, cy + 0.48 * h), (cx - 0.52 * h, cy + 0.48 * h)], fill=DEEP)
    # robe / body
    d.polygon([(cx - 0.20 * h, cy - 0.18 * h), (cx + 0.20 * h, cy - 0.18 * h),
               (cx + 0.30 * h, cy + 0.46 * h), (cx - 0.30 * h, cy + 0.46 * h)], fill=CORE)
    # hood / head
    hr = 0.20 * h
    d.ellipse([cx - hr, cy - 0.46 * h - hr, cx + hr, cy - 0.46 * h + hr], fill=CORE)
    d.polygon([(cx - hr * 1.15, cy - 0.30 * h), (cx + hr * 1.15, cy - 0.30 * h),
               (cx, cy - 0.78 * h)], fill=DEEP)
    if crown:
        for k in (-1, 0, 1):
            x = cx + k * hr * 0.62
            d.polygon([(x - hr * 0.20, cy - 0.72 * h), (x + hr * 0.20, cy - 0.72 * h),
                       (x, cy - 0.96 * h)], fill=ACCENT)
    for i in range(arms):
        side = -1 if i % 2 == 0 else 1
        y = cy - 0.10 * h + (i // 2) * 0.16 * h
        d.line([(cx + side * 0.18 * h, y), (cx + side * 0.62 * h, y - 0.10 * h)],
               fill=ACCENT, width=int(round(0.06 * h)))
    if ring:
        rr = 0.40 * h
        d.ellipse([cx - rr, cy - 0.80 * h - rr * 0.36, cx + rr, cy - 0.80 * h + rr * 0.36],
                  outline=ACCENT, width=int(round(0.035 * h)))


def compose(core, glow=0.03):
    blur = core.filter(ImageFilter.GaussianBlur(W * glow))
    return Image.alpha_composite(blur, core).resize((SIZE, SIZE), Image.LANCZOS)


def icon(tier):
    c = layer()
    d = ImageDraw.Draw(c)
    smoke(d, W * 0.5, W * 0.58, W * 0.26, 7 + tier)
    if tier == 1:
        d.ellipse([W * 0.34, W * 0.34, W * 0.66, W * 0.72], fill=CORE)          # 一团飘着的影
        d.ellipse([W * 0.42, W * 0.46, W * 0.47, W * 0.54], fill=ACCENT)
        d.ellipse([W * 0.53, W * 0.46, W * 0.58, W * 0.54], fill=ACCENT)
    else:
        figure(d, W * 0.5, W * 0.58, W * (0.62 + 0.05 * (tier - 2)),
               cape=tier >= 3, crown=tier >= 4, arms=0 if tier < 5 else 6, ring=tier >= 5)
    return compose(c)


ICONS = {
    "summon_dark": icon(1),
    "summon_elite": icon(2),
    "summon_lord": icon(3),
    "dark_king": icon(4),
    "evil_god": icon(5),
}

OUT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "spell")


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(repo, OUT)
    os.makedirs(out, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 4 * len(ICONS), SIZE * 4), (24, 24, 32, 255))
    for i, (name, im) in enumerate(ICONS.items()):
        p = os.path.join(out, name + ".png")
        im.save(p)
        sheet.paste(im.resize((SIZE * 4, SIZE * 4), Image.NEAREST), (i * SIZE * 4, 0))
        print("icon -> %s.png (%d bytes)" % (name, os.path.getsize(p)))
    preview = os.path.join(repo, "docs", "previews")
    os.makedirs(preview, exist_ok=True)
    sheet.save(os.path.join(preview, "dark_summon_icons.png"))
    print("preview -> docs/previews/dark_summon_icons.png")


if __name__ == "__main__":
    main()
