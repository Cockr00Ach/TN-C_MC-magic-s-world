"""战士武器名册体检：45 把武器逐项核对，缺什么直接列出来。

核对项（每一项在游戏里缺了都会出问题）：
- 中文名 / 英文名      → 缺了显示 `item.tnc.xxx`
- 物品模型             → 缺了是紫黑块
- 模型贴图             → 缺了是紫黑块
- 家族标签             → 缺了没有连招、没有刀光
- 刀光配色             → 缺了落到默认蓝（看起来像共用一种刀光）
- 虚影低模（warrior_ghost）→ 缺了攻击时没有虚影
- 蓄势招式名           → 缺了走家族默认

用法：python tools/check_warrior_roster.py [--md out.md]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tnc")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "tnc", "tnc", "warrior")

FAMILIES = [("长剑", "longswords"), ("重剑", "greatswords"), ("锤", "hammers"),
            ("长枪", "spears"), ("弓", "longbows")]


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def trail_colors():
    with open(os.path.join(JAVA, "WarriorTrailAppearance.java"), encoding="utf-8") as handle:
        source = handle.read()
    colors = {}
    for match in re.finditer(r'case((?:\s*"[^"]+"\s*,?)+)\s*->\s*0x([0-9A-Fa-f]{6})', source):
        for item in re.findall(r'"([^"]+)"', match.group(1)):
            colors[item] = int(match.group(2), 16)
    return colors


def charge_names():
    with open(os.path.join(JAVA, "WeaponChargeRules.java"), encoding="utf-8") as handle:
        source = handle.read()
    names = {}
    for match in re.finditer(r'if\((.*?)\)\s*return new Profile\("([^"]+)"', source, re.S):
        for item in re.findall(r'id\.equals\("([^"]+)"\)', match.group(1)):
            names[item] = match.group(2)
    return names


def ghost_model_ids() -> set[str]:
    """WarriorGhostModels.IDS 里登记的 id（只有它们才该有低模虚影）。"""
    with open(os.path.join(JAVA, "client", "WarriorGhostModels.java"), encoding="utf-8") as handle:
        source = handle.read()
    match = re.search(r'IDS\s*=\s*Set\.of\((.*?)\);', source, re.S)
    return set(re.findall(r'"([a-z0-9_]+)"', match.group(1))) if match else set()


def model_textures_problem(item_id: str) -> str:
    """返回空串=没问题，否则是问题描述。"""
    path = os.path.join(ASSETS, "models", "item", item_id + ".json")
    if not os.path.exists(path):
        return "缺模型"
    model = load(path)
    refs = [v for v in (model.get("textures") or {}).values() if isinstance(v, str)]
    for ref in refs:
        if ref.startswith("#") or ref.startswith("minecraft:"):
            continue
        namespace, _, rest = ref.partition(":")
        if not rest:
            namespace, rest = "tnc", ref
        png = os.path.join(RES, "assets", namespace, "textures", rest + ".png")
        if not os.path.exists(png):
            return f"贴图缺 {ref}"
    if not refs and not model.get("elements"):
        # 3D 模型物品（elements）或纯父模型都算正常；pure 父模型要能解析到原版
        parent = model.get("parent", "")
        if not parent:
            return "模型空且无父模型"
    return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", default=os.path.join(ROOT, "docs", "warrior", "武器名册体检-20261115.md"))
    args = ap.parse_args()

    zh = load(os.path.join(ASSETS, "lang", "zh_cn.json"))
    en = load(os.path.join(ASSETS, "lang", "en_us.json"))
    colors = trail_colors()
    charges = charge_names()
    ghost_ids = ghost_model_ids()

    rows = []
    problems = []
    for family, tag in FAMILIES:
        values = load(os.path.join(RES, "data", "tnc", "tags", "items", tag + ".json"))["values"]
        for entry in values:
            if not entry.startswith("tnc:"):
                continue
            item = entry.split(":", 1)[1]
            has_zh = "item.tnc." + item in zh
            has_en = "item.tnc." + item in en
            model_problem = model_textures_problem(item)
            # 虚影低模只在 WarriorGhostModels.IDS 里才要求有：其它武器直接用原模型当虚影（作者的选择）
            ghost_path = os.path.join(ASSETS, "models", "item", "warrior_ghost", item + ".json")
            ghost = os.path.exists(ghost_path)
            ghost_kind = "低模" if ghost else ("未登记" if item in ghost_ids else "原模型")
            color = colors.get(item)
            row = {
                "id": item,
                "name": zh.get("item.tnc." + item, item),
                "family": family,
                "zh": has_zh,
                "en": has_en,
                "model": model_problem or "✓",
                "ghost": ghost_kind,
                "color": f"#{color:06X}" if color else "默认蓝",
                "charge": charges.get(item, "（家族默认）"),
                "bow": family == "弓",
            }
            rows.append(row)
            if not has_zh:
                problems.append(f"{item}: 缺中文名")
            if not has_en:
                problems.append(f"{item}: 缺英文名")
            if model_problem:
                problems.append(f"{item}: {model_problem}")
            if item in ghost_ids and not ghost:
                problems.append(f"{item}: 登记了虚影低模但文件缺失 warrior_ghost/{item}.json")
            if color is None and not row["bow"]:
                problems.append(f"{item}: 刀光用默认蓝（没有专属配色）")

    lines = ["# 战士武器名册体检（2026-11-15）", "",
             f"共 **{len(rows)}** 把："
             + "、".join(f"{f} {sum(1 for r in rows if r['family'] == f)}"
                        for f, _ in FAMILIES), "",
             "| 武器 | id | 家族 | 中文 | 英文 | 模型/贴图 | 虚影低模 | 刀光色 | 蓄势 |",
             "|---|---|---|---|---|---|---|---|---|"]
    for row in rows:
        def mark(ok):
            return "✓" if ok else "✗"
        lines.append(
            f"| {row['name']} | `{row['id']}` | {row['family']} | {mark(row['zh'])} | {mark(row['en'])} | "
            f"{row['model']} | {row['ghost']} | {row['color']} | {row['charge']} |")
    lines += ["", "## 问题", ""]
    lines += [f"- {p}" for p in problems] if problems else ["- 无：名册完整"]
    os.makedirs(os.path.dirname(args.md), exist_ok=True)
    with open(args.md, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"weapons: {len(rows)}  problems: {len(problems)}")
    for p in problems:
        print("  " + p)
    print(f"wrote {args.md}")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
