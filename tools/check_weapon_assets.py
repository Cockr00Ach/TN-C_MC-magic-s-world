"""检查新增武器/物品的"资源完整性"：注册了但没有文字/模型/贴图的东西，进游戏就是紫黑块或原始 key。

检查四件事：
1. 每个 `tnc:<id>` 物品在 `zh_cn.json` / `en_us.json` 里有名字；
2. 有 `models/item/<id>.json`（或者被 `parent` 链带到原版模型）；
3. 模型引用的贴图真的存在（含 `layer0` / `textures` 表）；
4. 家族标签里的 id 都真的注册过（拼错就是"这把武器没连招"）。

用法：python tools/check_weapon_assets.py
"""

from __future__ import annotations

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "tnc")
JAVA = os.path.join(ROOT, "src", "main", "java")

ITEM_IDS: dict[str, str] = {}       # id -> 注册它的 java 文件
ITEM_MODEL_IDS: set[str] = set()

# 只认真正的物品注册：`ITEMS.register("id", ...)`。别的 register(...) 是结构/效果/音效/法术，
# 它们不需要物品模型，混进来会产生一堆假问题。
REGISTER_PATTERNS = [r'ITEMS\.register\(\s*"([a-z0-9_]+)"']


def scan_registrations():
    for dirpath, _, files in os.walk(JAVA):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="ignore") as handle:
                source = handle.read()
            if "ITEMS.register(" not in source:
                continue
            for pattern in REGISTER_PATTERNS:
                for item_id in re.findall(pattern, source):
                    ITEM_IDS.setdefault(item_id, os.path.relpath(path, ROOT))


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def resolve_texture(ref: str) -> str:
    """'tnc:item/xxx' -> 实际 png 路径。"""
    namespace, _, path = ref.partition(":")
    if not path:
        namespace, path = "minecraft", namespace
    base = os.path.join(ROOT, "src", "main", "resources", "assets", namespace, "textures")
    return os.path.join(base, path + ".png")


def check_model(item_id: str) -> list[str]:
    problems = []
    model_path = os.path.join(ASSETS, "models", "item", item_id + ".json")
    if not os.path.exists(model_path):
        # 允许 item/ 下的子目录形式
        found = None
        for dirpath, _, files in os.walk(os.path.join(ASSETS, "models", "item")):
            if item_id + ".json" in files:
                found = os.path.join(dirpath, item_id + ".json")
                break
        if not found:
            return [f"缺模型 models/item/{item_id}.json"]
        model_path = found
    model = load(model_path)
    textured = False
    for key, value in (model.get("textures") or {}).items():
        if not isinstance(value, str):
            continue
        if value.startswith("#"):
            continue
        if value.startswith("minecraft:") or (":" not in value and value.startswith("item/")):
            continue                  # 原版贴图，不在我们仓库里
        textured = True
        if not os.path.exists(resolve_texture(value)):
            problems.append(f"贴图缺失 {value}（来自 {os.path.basename(model_path)}）")
    if not textured:
        # 3D 模型物品：只要模型里有 elements 就算完整
        if not model.get("elements"):
            parent = model.get("parent", "")
            if parent.startswith("minecraft:item/") or parent.startswith("item/"):
                return problems          # 原版父模型自带贴图，跳过
            problems.append(f"模型既没有贴图引用也没有 elements（{os.path.basename(model_path)}）")
    return problems


def main() -> int:
    scan_registrations()
    zh = load(os.path.join(ASSETS, "lang", "zh_cn.json"))
    en = load(os.path.join(ASSETS, "lang", "en_us.json"))
    problems: list[str] = []
    stats = {"items": len(ITEM_IDS), "no_zh": 0, "no_en": 0, "no_model": 0, "bad_tex": 0}

    for item_id, where in sorted(ITEM_IDS.items()):
        if "item.tnc." + item_id not in zh:
            stats["no_zh"] += 1
            problems.append(f"[lang] {item_id}: 没有中文名（{where}）")
        if "item.tnc." + item_id not in en:
            stats["no_en"] += 1
            problems.append(f"[lang] {item_id}: 没有英文名（{where}）")
        model_problems = check_model(item_id)
        if any(p.startswith("缺模型") for p in model_problems):
            stats["no_model"] += 1
        if any(p.startswith("贴图缺失") for p in model_problems):
            stats["bad_tex"] += 1
        problems.extend(f"[asset] {item_id}: {p}" for p in model_problems)

    # 家族标签引用的 id 必须真的注册过
    tags_dir = os.path.join(DATA, "tags", "items")
    tag_ids = set()
    for name in sorted(f for f in os.listdir(tags_dir) if f.endswith(".json")):
        tag = load(os.path.join(tags_dir, name))
        for entry in tag.get("values", []):
            if entry.startswith("tnc:"):
                item = entry.split(":", 1)[1]
                tag_ids.add(item)
                if item not in ITEM_IDS:
                    problems.append(f"[tag] {name}: 引用了没注册的物品 tnc:{item}")
            elif not entry.startswith("minecraft:"):
                problems.append(f"[tag] {name}: 引用了外部命名空间 {entry}（可能没装那个模组）")

    print(f"注册物品 {stats['items']} 个；缺中文 {stats['no_zh']}、缺英文 {stats['no_en']}、"
          f"缺模型 {stats['no_model']}、贴图问题 {stats['bad_tex']}")
    if problems:
        print(f"发现 {len(problems)} 个问题：")
        for line in problems:
            print("  " + line)
        return 1
    print("武器/物品资源检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
