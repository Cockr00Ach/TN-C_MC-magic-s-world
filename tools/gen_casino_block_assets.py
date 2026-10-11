#!/usr/bin/env python3
"""给三台赌场机器生成"空"的 blockstate / block model / item model。

作者 2026-10-08 报的两个症状：
  * 「赌博桌老虎机什么的都没有成功导入游戏」⇒ 创造页里是**紫黑方块** ✗
  * 日志里 `Exception loading blockstate definition: 'tnc:blockstates/slot_machine.json'
    missing model for variant: 'tnc:slot_machine#facing=north,spinning=false'`（每个朝向两条）✗

为什么需要这一步：
  三台机器是**代码自绘**（BlockEntityRenderer ✓），所以刻意**没有**普通的方块模型 ✗ ——
  方块自己的渲染形状也是 RenderShape.INVISIBLE ✓，实机只画我们自己的几何 ✓。
  但是：
    1. **物品形态**必须有 `models/item/<id>.json` ✗ —— 没有的话创造页/背包里就是紫黑方块 ✓；
    2. **方块状态**没有 JSON 时，模型加载器每个 variant 都报一次
       "missing model" ✓（8 个朝向×旋转组合 = 8 条刷屏 ✗）；
    3. 方块模型缺失还会让"挖掉方块"的**破坏粒子**取不到贴图 ✓。
  ⇒ 补一层**空模型**（`elements: []` ⇒ 什么都不画 ✓，但模型存在、粒子贴图有效 ✓）。

⚠️ 这三份是**生成的**，别手改 ✗ —— 改就改本脚本重跑 ✓。
⚠️ 以后作者给正式贴图时，把 ITEM_TEXTURE 换掉、再跑一次就行 ✓。

用法：
    python tools/gen_casino_block_assets.py
"""

import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tnc")

# (方块 id, 中文名, 粒子贴图 —— 决定"挖掉方块时飞出来的碎屑"长什么样)
#
# ★ 刻意用**暖色木纹**（`tnc:block/gamble_table` = 木框＋绿呢＋筹码的那张图 ✓），
#   和机器本身的红木/黄铜配色接近 ✓。
#   ⚠️ 别用 `tnc:block/<machine_id>` ✗ —— 那些贴图**不存在** ✓
#   （自绘不需要贴图 ✗），引用了会在模型加载时报 Missing textures ✓。
MACHINES = [
    ("slot_machine", "老虎机", "tnc:block/gamble_table"),
    ("roulette_table", "转盘赌桌", "tnc:block/gamble_table"),
    ("gamble_table", "赌博桌", "tnc:block/gamble_table"),
]

BLOCKSTATE = {
    "variants": {
        "facing=north,spinning=false": {"model": "tnc:block/%s"},
        "facing=north,spinning=true": {"model": "tnc:block/%s"},
        "facing=east,spinning=false": {"model": "tnc:block/%s"},
        "facing=east,spinning=true": {"model": "tnc:block/%s"},
        "facing=south,spinning=false": {"model": "tnc:block/%s"},
        "facing=south,spinning=true": {"model": "tnc:block/%s"},
        "facing=west,spinning=false": {"model": "tnc:block/%s"},
        "facing=west,spinning=true": {"model": "tnc:block/%s"},
    }
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    return os.path.getsize(path)


def main():
    total = 0
    for machine_id, cn, particle in MACHINES:
        # ① 方块状态：8 个 variant 全部指向下面那个空模型 ✓
        bs = {"variants": {k: {"model": v["model"] % machine_id}
                           for k, v in BLOCKSTATE["variants"].items()}}
        total += write(os.path.join(ASSETS, "blockstates", machine_id + ".json"), bs)

        # ② 空方块模型：elements 为空 ⇒ 什么都不画 ✓，但粒子贴图存在 ✓
        #    ⚠️ 别加 "ambientocclusion"/"display" 之类，空模型用不上 ✓
        model = {
            "parent": "minecraft:block/block",
            "textures": {"particle": particle},
            "elements": []
        }
        total += write(os.path.join(ASSETS, "models", "block", machine_id + ".json"), model)

        # ③ 物品模型：parent 指向上面那个空模型 ✓
        #    ⇒ 至少不再是紫黑方块 ✓；正式图标等作者贴图到位再换 ✓
        item = {"parent": "tnc:block/" + machine_id}
        total += write(os.path.join(ASSETS, "models", "item", machine_id + ".json"), item)

        print("  %-16s blockstate + block model + item model  (%s)" % (machine_id, cn))

    print("wrote %d files, %d bytes total" % (len(MACHINES) * 3, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
