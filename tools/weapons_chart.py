"""把所有战士武器画成一张总览图（`docs/previews/weapons-20261114.png`）+ 一份表格。

数据全部从仓库**现有的真实来源**里读，不手抄：
- 家族 → `data/tnc/tags/items/*.json`
- 中文名 → `assets/tnc/lang/zh_cn.json`
- 刀光外壳色 → 从 `WarriorTrailAppearance.java` 的 case 里解析
- 重心/丝线密度 → 与 `WarriorTrail.plumeVariant` / `plumeFrequency` 同一套规则
- 图标 → `assets/tnc/textures/item/<id>.png`；没有单独贴图的（3D 模型物品）画家族字形代位

用法：
    python tools/weapons_chart.py
    python tools/weapons_chart.py --out a.png --md b.md
"""

from __future__ import annotations

import argparse
import json
import os
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TAGS = os.path.join(ROOT, "src", "main", "resources", "data", "tnc", "tags", "items")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "lang", "zh_cn.json")
ITEM_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "textures", "item")
APPEARANCE = os.path.join(ROOT, "src", "main", "java", "com", "tnc", "tnc", "warrior",
                          "WarriorTrailAppearance.java")

FAMILIES = [
    ("长剑", "longswords", (150, 190, 235), "单手 · 最快的三段连斩"),
    ("重剑", "greatswords", (120, 153, 213), "双手 · 慢但一大片"),
    ("锤", "hammers", (201, 138, 80), "双手 · 下砸会荡开一圈影"),
    ("长枪", "spears", (75, 213, 164), "双手 · 最长的突刺"),
    ("弓", "longbows", (176, 196, 120), "远程 · 不出刀光"),
]
THICKNESS = {"长剑": 0.075, "重剑": 0.11, "锤": 0.15, "长枪": 0.06}
FREQUENCY = {"长剑": 0.30, "重剑": 0.22, "锤": 0.18, "长枪": 0.46}
DEFAULT_COLOR = 0x70B7ED

BG = (12, 14, 19)
CARD = (23, 27, 35)
CARD_EDGE = (40, 46, 58)
TEXT = (232, 238, 247)
DIM = (140, 152, 170)


def font(size):
    for path in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyhl.ttc",
                 r"C:\Windows\Fonts\simhei.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def load_trail_colors():
    """从 Java 的 switch 里解析 `case "a","b"->0xRRGGBB`。"""
    with open(APPEARANCE, encoding="utf-8") as handle:
        source = handle.read()
    colors = {}
    for match in re.finditer(r'case((?:\s*"[^"]+"\s*,?)+)\s*->\s*0x([0-9A-Fa-f]{6})', source):
        ids = re.findall(r'"([^"]+)"', match.group(1))
        for item in ids:
            colors[item] = int(match.group(2), 16)
    default = re.search(r'default\s*->\s*0x([0-9A-Fa-f]{6})', source)
    return colors, int(default.group(1), 16) if default else DEFAULT_COLOR


CHARGE_RULES = os.path.join(ROOT, "src", "main", "java", "com", "tnc", "tnc", "warrior",
                            "WeaponChargeRules.java")
FAMILY_HOLD = {"长剑": "蓄势·立剑劈山", "重剑": "蓄势·巨剑断岳", "锤": "蓄势·震地重锤",
               "长枪": "蓄势·拦拿破军", "弓": "蓄势·穿云箭"}


def load_charge_names():
    """从 WeaponChargeRules.java 里解析"哪把武器蓄势叫什么"，解析不到的走家族默认。"""
    with open(CHARGE_RULES, encoding="utf-8") as handle:
        source = handle.read()
    names = {}
    for match in re.finditer(r'if\((.*?)\)\s*return new Profile\("([^"]+)"', source, re.S):
        body = match.group(1)
        name = match.group(2)
        for weapon_id in re.findall(r'id\.equals\("([^"]+)"\)', body):
            names[weapon_id] = name
    return names


def variant_of(weapon_id, family):
    if "scythe" in weapon_id or "sickle" in weapon_id:
        return "边缘重"
    if family == "锤" or "hammer" in weapon_id or "maul" in weapon_id:
        return "中心重"
    return "均衡"


def build_data():
    with open(LANG, encoding="utf-8") as handle:
        lang = json.load(handle)
    colors, default = load_trail_colors()
    charges = load_charge_names()
    rows = []
    for family, tag, accent, tagline in FAMILIES:
        with open(os.path.join(TAGS, tag + ".json"), encoding="utf-8") as handle:
            values = json.load(handle)["values"]
        for entry in values:
            if not entry.startswith("tnc:"):
                continue                    # 原版剑/弓不进这张图
            weapon_id = entry.split(":", 1)[1]
            rows.append({
                "id": weapon_id,
                "name": lang.get("item.tnc." + weapon_id, weapon_id),
                "family": family,
                "accent": accent,
                "tagline": tagline,
                "color": colors.get(weapon_id, default),
                "variant": "—" if family == "弓" else variant_of(weapon_id, family),
                "freq": None if family == "弓" else FREQUENCY.get(family),
                "thickness": None if family == "弓" else THICKNESS.get(family),
                "icon": os.path.join(ITEM_TEX, weapon_id + ".png"),
                "charge": charges.get(weapon_id, FAMILY_HOLD.get(family, "")),
            })
    return rows


def glyph(draw, box, family, color):
    """没有 32×32 贴图的物品（3D 模型 / 共用图集）画一个家族字形。"""
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    w = (x1 - x0)
    line = max(3, int(w * 0.09))
    if family in ("长剑", "重剑"):
        thick = 1.0 if family == "长剑" else 1.8
        draw.line([(cx, y0 + 4), (cx, y1 - 6)], fill=color + (255,), width=int(line * thick))
        draw.line([(cx - w * 0.22, y1 - w * 0.30), (cx + w * 0.22, y1 - w * 0.30)],
                  fill=color + (255,), width=line)
        draw.line([(cx, y1 - w * 0.30), (cx, y1 - 2)], fill=color + (200,), width=line)
    elif family == "锤":
        draw.line([(cx, y0 + 6), (cx, y1 - 4)], fill=color + (255,), width=line)
        draw.rounded_rectangle([cx - w * 0.26, y0 + 4, cx + w * 0.26, y0 + w * 0.34],
                               radius=3, fill=color + (255,))
    elif family == "长枪":
        draw.line([(cx, y0 + 2), (cx, y1 - 3)], fill=color + (255,), width=line)
        draw.polygon([(cx, y0 + 1), (cx - w * 0.12, y0 + w * 0.24), (cx + w * 0.12, y0 + w * 0.24)],
                     fill=color + (255,))
    else:
        draw.arc([cx - w * 0.26, y0 + 6, cx + w * 0.26, y1 - 6], 300, 60, fill=color + (255,),
                 width=line)
        draw.line([(cx + w * 0.10, y0 + 8), (cx + w * 0.10, y1 - 8)], fill=color + (120,), width=1)


def draw_card(sheet, draw, x, y, w, h, row, index):
    accent = row["accent"]
    draw.rounded_rectangle([x, y, x + w, y + h], radius=10, fill=CARD, outline=CARD_EDGE)
    # 左侧图标
    icon_box = (x + 10, y + 12, x + 10 + 56, y + 12 + 56)
    draw.rounded_rectangle(icon_box, radius=8, fill=(16, 19, 25), outline=(34, 39, 50))
    if os.path.exists(row["icon"]):
        with Image.open(row["icon"]) as icon:
            icon = icon.convert("RGBA").resize((48, 48), Image.NEAREST)
            sheet.alpha_composite(icon, (icon_box[0] + 4, icon_box[1] + 4))
    else:
        glyph(draw, (icon_box[0] + 6, icon_box[1] + 6, icon_box[2] - 6, icon_box[3] - 6),
              row["family"], accent)

    tx = icon_box[2] + 12
    draw.text((tx, y + 10), row["name"], font=font(19), fill=TEXT)
    draw.text((tx, y + 34), row["id"], font=font(11), fill=DIM)

    # 颜色块 + 色值
    chip_y = y + 54
    chip = accent if row["family"] == "弓" else tuple(row["color"] >> s & 255 for s in (16, 8, 0))
    draw.rounded_rectangle([tx, chip_y, tx + 16, chip_y + 16], radius=3, fill=chip + (255,))
    if row["family"] == "弓":
        note = "远程 · 不出刀光"
    else:
        note = f"重心 {row['variant']} · 丝密 {row['freq']:.2f}/格 · 厚 {row['thickness']:.3f} 格"
    draw.text((tx + 22, chip_y + 1), note, font=font(11), fill=DIM)
    if row["charge"]:
        draw.text((tx, y + h - 24), row["charge"], font=font(12),
                  fill=(accent[0] * 3 // 4 + 40, accent[1] * 3 // 4 + 40, accent[2] * 3 // 4 + 40))
    return x + w


def render(rows):
    cols = 4
    gap = 14
    card_w = 372
    card_h = 100
    pad = 28
    header_h = 46
    # 先算高度
    sections = []
    for family, _, accent, tagline in FAMILIES:
        items = [r for r in rows if r["family"] == family]
        sections.append((family, accent, tagline, items))
    total = pad + 96
    for _, _, _, items in sections:
        rows_count = (len(items) + cols - 1) // cols
        total += header_h + rows_count * (card_h + gap) - gap + 12
    total += pad
    width = pad * 2 + cols * card_w + (cols - 1) * gap

    sheet = Image.new("RGBA", (width, total + 200), BG)
    draw = ImageDraw.Draw(sheet, "RGBA")

    draw.text((pad, pad - 4), "TN-C 战士武器总览", font=font(34), fill=TEXT)
    draw.text((pad, pad + 44), f"{len(rows)} 把专属武器 · 5 个家族 · 刀光颜色 / 重心 / 丝线密度都在卡片上",
              font=font(15), fill=DIM)
    draw.text((pad, pad + 68), "（图标来自模组贴图；四把青铜器与长枪是 3D 模型物品，没有 32×32 图标，这里画家族字形）",
              font=font(11), fill=(104, 114, 130))

    y = pad + 96
    for family, accent, tagline, items in sections:
        draw.rounded_rectangle([pad, y, width - pad, y + header_h - 10], radius=8,
                               fill=(accent[0] // 5, accent[1] // 5, accent[2] // 5),
                               outline=(accent[0] // 2, accent[1] // 2, accent[2] // 2))
        draw.text((pad + 14, y + 6), f"{family}  ·  {len(items)} 把", font=font(20),
                  fill=(accent[0], accent[1], accent[2]))
        draw.text((pad + 170, y + 12), tagline, font=font(12), fill=DIM)
        y += header_h
        for i, row in enumerate(items):
            col = i % cols
            line = i // cols
            x = pad + col * (card_w + gap)
            cy = y + line * (card_h + gap)
            draw_card(sheet, draw, x, cy, card_w, card_h, row, i)
        y += ((len(items) + cols - 1) // cols) * (card_h + gap) + 12
    used = y - 12 + pad
    return sheet.crop((0, 0, width, min(sheet.height, used)))


def markdown(rows):
    lines = ["# TN-C 战士武器总览", "",
             "> 由 `tools/weapons_chart.py` 生成；同目录的总览图：`docs/previews/weapons-20261114.png`。",
             "> 数据来自 `data/tnc/tags/items/*.json`、`assets/tnc/lang/zh_cn.json`、",
             "> `WarriorTrailAppearance.java`（刀光色）、`WeaponChargeRules.java`（蓄势招式）。", "",
             "| 武器 | id | 家族 | 刀光颜色 | 重心 | 丝线密度 | 光带厚度 | 蓄势招式 |",
             "|---|---|---|---|---|---|---|---|"]
    for family, _, _, _ in FAMILIES:
        for row in [r for r in rows if r["family"] == family]:
            if row["family"] == "弓":
                lines.append(f"| {row['name']} | `{row['id']}` | 弓（远程） | — | — | — | — | {row['charge']} |")
            else:
                lines.append(
                    f"| {row['name']} | `{row['id']}` | {row['family']} | "
                    f"#{row['color']:06X} | {row['variant']} | "
                    f"{row['freq']:.2f}/格 | {row['thickness']:.3f} 格 | {row['charge']} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "docs", "previews", "weapons-20261114.png"))
    ap.add_argument("--md", default=os.path.join(ROOT, "docs", "warrior", "武器一览-20261114.md"))
    args = ap.parse_args()

    rows = build_data()
    sheet = render(rows)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    sheet.save(args.out)
    with open(args.md, "w", encoding="utf-8") as handle:
        handle.write(markdown(rows))
    print(f"wrote {args.out} ({sheet.width}x{sheet.height}) and {args.md}, {len(rows)} weapons")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
