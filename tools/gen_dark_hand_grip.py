#!/usr/bin/env python3
"""黑夜之手链：给五个法术 JSON 补上"抓住不放的那只手"（tnc:night_grip）。

作者 2026-10-10：暗魔法的黑夜之手整个改一下（参考《Re:Zero》486 的不可视之手）。
投射物那一段保持不变（它负责"伸出去"的观感，作者前面几轮已经验收过）；
缺的是"抓住之后" —— 引擎在命中的那一刻就把投射物回收了，所以"抓/拽/捏"要有一个
留在目标身上的实体来做。

做法与本项目「以伤换伤 · 献祭」那条链**完全相同**（那边已经实机跑过）：
在 `impact` 里追加一条 `SPAWN`，`entity_type_id` 指向我们自己的实体
=> 引擎在命中那一刻造出它，并通过 `SpellSpawnedEntity` 回调把施法者交给我们。
不发明任何新的引擎语义。

⚠️ 这五个法术住在 **mod jar 的 data/ 里**（不在 kubejs）：
   改完必须 build -> install-to-pack -> 重启游戏，`sync.cmd push` 够不到它们。
   生成器：python tools/gen_dark_hand_grip.py

Idempotent: 已经有这条 SPAWN 的文件会原样跳过。
"""

import json
import os
import sys

# 仓库根（本脚本在 tools/ 下）
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPELL_DIR = os.path.join(REPO, "src", "main", "resources", "data", "tnc", "spells")

# 黑夜之手链五档（t1..t5；id 不改名 => 图标/lang/存档都不受影响）
CHAIN = ["night_hand", "night_raid", "night_embrace", "black_ruin", "slay_light"]

GRIP_ENTITY = "tnc:night_grip"

# 引擎可能在 TTL 之后回收这个实体 => 给足（t5 要抓 3 秒 = 60 tick）；
# 实体自己到点会 discard，所以给长一点只是保险，不会真的活这么久 ✓
GRIP_TTL_SECONDS = 6


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1

    problems = 0
    for name in CHAIN:
        path = os.path.join(SPELL_DIR, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue

        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)

        impacts = spell.get("impact")
        if not isinstance(impacts, list):
            print("SKIP %s: no impact list" % name)
            problems += 1
            continue

        # 本项目的硬规则：impact 里每个元素都必须有 action
        # （只有 particles 没有 action 的那种会让引擎在 performSpell 里 NPE，
        #  表现是"有雷声没效果、按了没反应"）
        for i, entry in enumerate(impacts):
            if "action" not in entry:
                print("SKIP %s: impact[%d] has no action (engine NPE risk)" % (name, i))
                problems += 1
                break
        else:
            already = [
                entry["action"].get("spawn", {}).get("entity_type_id")
                for entry in impacts
                if entry.get("action", {}).get("type") == "SPAWN"
            ]
            if GRIP_ENTITY in already:
                print("%-16s already wired to %s (skip)" % (name, GRIP_ENTITY))
                continue

            impacts.append({
                "action": {
                    "type": "SPAWN",
                    "spawn": {
                        "entity_type_id": GRIP_ENTITY,
                        "time_to_live_seconds": GRIP_TTL_SECONDS,
                        "delay_ticks": 0,
                    },
                },
            })

            with open(path, "w", encoding="utf-8", newline="\n") as fh:
                json.dump(spell, fh, indent=2, ensure_ascii=False)
                fh.write("\n")

            kinds = [entry["action"].get("type") for entry in impacts]
            print("%-16s impact actions -> %s   (+ %s)" % (name, "/".join(kinds), GRIP_ENTITY))

    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
