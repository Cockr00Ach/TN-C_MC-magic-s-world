"""全元素法术总览图：7 系 × 每条链 × 5 档，带图标和中文名。

    python tools/spell_overview.py      # → docs/spells/全法术总览-20261114.png
"""

import json
import pathlib
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path('.')
ENTRIES = json.loads((ROOT / 'build/spell_entries.json').read_text(encoding='utf-8'))
ICONS = ROOT / 'src/main/resources/assets/tnc/textures/spell'
CATALOG = (ROOT / 'src/main/java/com/tnc/tnc/magic/SpellCatalog.java').read_text(encoding='utf-8')

# 元素 → (中文名, 主色, 深色)
ELEMENTS = {
    'THUNDER': ('雷', (250, 214, 90), (86, 62, 12)),
    'FIRE': ('火', (255, 126, 48), (92, 26, 6)),
    'WIND': ('风', (120, 226, 206), (10, 62, 58)),
    'WATER': ('水', (86, 170, 246), (8, 38, 76)),
    'EARTH': ('土', (200, 152, 86), (56, 36, 16)),
    'LIGHT': ('光', (255, 222, 130), (70, 52, 8)),
    'DARK': ('暗', (176, 118, 240), (28, 12, 46)),
}
ORDER = ['THUNDER', 'FIRE', 'WIND', 'WATER', 'EARTH', 'LIGHT', 'DARK']

# 链的中文名：从 SpellCatalog 的 enum 里抓
CHAIN_CN = {}
for m in re.finditer(r'\b([A-Z_]+)\("([^"]*)"(?:,\s*"([^"]*)")?\)', CATALOG):
    name, desc = m.group(2), m.group(3) or ''
    CHAIN_CN.setdefault(m.group(1), (name, desc))

FONT = 'C:/Windows/Fonts/msyh.ttc'
F_TITLE = ImageFont.truetype(FONT, 40)
F_SUB = ImageFont.truetype(FONT, 18)
F_ELEM = ImageFont.truetype(FONT, 34)
F_CHAIN = ImageFont.truetype(FONT, 16)
F_NAME = ImageFont.truetype(FONT, 15)
F_TIER = ImageFont.truetype(FONT, 13)


def main():
    groups = {}
    for e in ENTRIES:
        groups.setdefault(e['element'], {}).setdefault(e['chain'], []).append(e)

    col_w, icon, row_h = 176, 44, 92
    label_w, margin = 210, 36
    header_h = 54
    width = margin * 2 + label_w + col_w * 5
    height = margin * 2 + 96 + sum(
        header_h + len(groups[el]) * row_h + 18 for el in ORDER)

    img = Image.new('RGB', (width, height), (16, 17, 22))
    d = ImageDraw.Draw(img)

    d.text((margin, margin), 'TN-C 全法术总览', font=F_TITLE, fill=(240, 240, 248))
    d.text((margin, margin + 50), '7 系 × 每条链 × 5 档 ｜ 图标 + 名称 ｜ 2026-11-14',
           font=F_SUB, fill=(150, 156, 172))

    y = margin + 96
    for el in ORDER:
        cn, main, dark = ELEMENTS[el]
        chains = groups[el]
        count = sum(len(v) for v in chains.values())
        # 元素标题条
        d.rounded_rectangle([margin, y, width - margin, y + header_h - 10], 10,
                            fill=(main[0] // 5, main[1] // 5, main[2] // 5))
        d.text((margin + 14, y + 4), f'{cn}系', font=F_ELEM, fill=main)
        d.text((margin + 96, y + 18), f'{len(chains)} 条链 · {count} 个法术',
               font=F_SUB, fill=(190, 194, 206))
        y += header_h

        for chain, rows in chains.items():
            ccn, cdesc = CHAIN_CN.get(chain, (chain, ''))
            d.text((margin + 6, y + 30), ccn, font=F_CHAIN, fill=main)
            if cdesc:
                d.text((margin + 6, y + 50), cdesc[:14], font=F_TIER, fill=(140, 146, 160))
            for e in sorted(rows, key=lambda z: z['tier']):
                cx = margin + label_w + (e['tier'] - 1) * col_w
                cell = [cx, y + 6, cx + col_w - 14, y + row_h - 8]
                d.rounded_rectangle(cell, 8, fill=(26, 28, 36), outline=(44, 46, 56))
                p = ICONS / (e['id'] + '.png')
                if p.exists():
                    im = Image.open(p).convert('RGBA').resize((icon, icon), Image.NEAREST)
                    img.paste(im, (cell[0] + 10, cell[1] + 12), im)
                d.text((cell[0] + icon + 20, cell[1] + 10), e['name'], font=F_NAME,
                       fill=(232, 234, 242))
                d.text((cell[0] + icon + 20, cell[1] + 32), f'{e["tier"]} 档', font=F_TIER,
                       fill=(150, 156, 170))
                sid = e['id'] if len(e['id']) <= 17 else e['id'][:16] + '...'
                d.text((cell[0] + icon + 20, cell[1] + 50), sid, font=F_TIER,
                       fill=(96, 102, 116))
            y += row_h
        y += 18

    out = ROOT / 'docs/spells/全法术总览-20261114.png'
    img.save(out)
    print('saved', out, img.size)


if __name__ == '__main__':
    main()
