#!/usr/bin/env python3
"""黑夜之手链：把五个法术改成"手由我们自己飞"的形态（弧线 ＋ 掌心朝目标 ＋ 反噬头痛）。

为什么不再用引擎的投射物（作者 2026-10-10 的实机反馈把那条路否掉了）：
  1. "手一直在旋转"      —— 引擎 SpellProjectileRenderer 在 ALONG_MOTION 下把模型对齐到
                           **运动矢量**，而 homing 让运动矢量一直拐 ⇒ 手一直偏航；
  2. "不是掌心对着别人"  —— 姿态由引擎那套决定，我们只能给 orientation / offset 两个字段，定不住；
  3. "不能所有手都是直直飞出去，要有弯的弧线"
                         —— 引擎的 LaunchProperties 只有 velocity / extra_launch_count /
                            extra_launch_delay（javap 确认），**没有弧线、也没有散射角**。

现在：release.target 改成 SELF（不再产生引擎投射物），手由
`magic/TNDarkHandMechanics` 在 SPELL_CAST 的 RELEASE 上自己甩出去（从后背出发、二次贝塞尔弧线），
渲染也归我们（client/TNNightGripRenderer ⇒ 掌心一直朝着被抓的人）。

这个脚本做四件事（幂等，可以反复跑）：
  * release.target -> { "type": "SELF" }（删掉 projectile 块）
  * 删掉 area_impact（SELF 配它是硬规则禁止的：引擎会 NPE，因为 position 为 null）
  * impact 改成一条"反噬头痛"（weakness，作用在**施法者自己**身上；每档时长不同）
  * range 写成"手最远伸多少格"（与 TNNightGripEntity.reach() 同一个口径：12 + 4 * tier）

⚠️ 这五个法术住在 **mod jar 的 data/ 里**（不在 kubejs，kubejs 里没有同名文件）：
   改完必须 build -> install-to-pack -> 重启游戏；`sync.cmd push` 够不到它们。

用法： python tools/gen_dark_hand_chain.py
"""

import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPELL_DIR = os.path.join(REPO, "src", "main", "resources", "data", "tnc", "spells")

# 黑夜之手链五档（t1..t5；id 不改名 => 图标/lang/存档都不受影响）
CHAIN = ["night_hand", "night_raid", "night_embrace", "black_ruin", "slay_light"]

# 反噬"头痛"时长（tick，作用在施法者自己身上）。t1/t2 给 1 tick = 实际没有任何感觉，
# 只为让 impact 数组非空（引擎空 impact 的用法在本整合包里一例都没有，不去发明 ✗）
HEADACHE_TICKS = [1, 1, 10, 30, 60]


def reach(tier):
    """手最远伸多少格 —— 与 magic/TNNightGripEntity.reach() 同一个口径 ✓。"""
    return 12.0 + 4.0 * tier


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1

    problems = 0
    for tier, name in enumerate(CHAIN, start=1):
        path = os.path.join(SPELL_DIR, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue

        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)

        release = spell.get("release")
        if not isinstance(release, dict) or "target" not in release:
            print("SKIP %s: no release.target" % name)
            problems += 1
            continue

        old_target = release["target"].get("type")
        had_projectile = "projectile" in release["target"]
        had_area = "area_impact" in spell

        # ── 1. 不再产生引擎投射物：SELF ⇒ 手由 Java 自己飞 ✓
        release["target"] = {"type": "SELF"}

        # ── 2. SELF 绝不能配顶层 area_impact（引擎会因为 position 为 null 而 NPE ✗）
        spell.pop("area_impact", None)

        # ── 3. impact 只留一条"反噬头痛"（作用在施法者自己身上 ✓）
        spell["impact"] = [{
            "action": {
                "type": "STATUS_EFFECT",
                # ★ 必须是 true：这一条要落在**施法者**身上，不是被打的目标 ✓
                #   （同样写法见「以伤换伤」那条链 2026-10-10 的修正 ✓）
                "apply_to_caster": True,
                "status_effect": {
                    "effect_id": "minecraft:weakness",
                    "duration": HEADACHE_TICKS[tier - 1],
                    "amplifier": 0,
                    "apply_mode": "SET",
                    "show_particles": False,
                },
            },
        }]

        # ── 4. range 写成"手最远伸多少格"（不自欺：不再有 48 格的直线投射物了 ✓）
        spell["range"] = reach(tier)

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")

        print("%-16s t%d  target %-10s -> SELF   projectile=%-8s area_impact=%-8s range -> %-4.0f headache %dt"
              % (name, tier, old_target, "dropped" if had_projectile else "none",
                 "dropped" if had_area else "none", reach(tier), HEADACHE_TICKS[tier - 1]))

    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
