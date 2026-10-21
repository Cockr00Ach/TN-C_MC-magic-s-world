# -*- coding: utf-8 -*-
"""
reorganize_docs.py  --  tidy the workspace documents into topic folders.

PRINCIPLE
=========
Move, never delete. The first level of docs/ holds ONLY entry documents: the index,
the living handovers (`*_交接.md`), the two fact sources (`当前状态.md`,
`地图接入进度.md`) and the daily workflow. Everything else lives in a topic folder:
dated implementation records and fix reports go to their topic (`spells/`,
`casino/`, `life/`, `ink/`, ...), one-off records with no topic go to `misc/`,
superseded snapshots go to `archive/`, and every image goes to `previews/`.

Living documents KEEP their current path, because other sessions and javadoc
comments point at them and a moved path breaks muscle memory. Dated files are free
to move -- after each move, run `python tools/check_doc_links.py` and fix the
references (tools that read a doc by path must be updated too).

The MOVES table below is the single source of truth for the mapping, and doubles as
a move log: re-running is idempotent, entries whose source is already gone and whose
destination exists are reported as "done" and skipped.

Everything uses `git mv`, so history and blame follow the file.

USAGE
=====
    python tools/reorganize_docs.py --dry-run     # print the plan
    python tools/reorganize_docs.py --apply       # do it
"""

import argparse
import io
import os
import subprocess
import sys

# explicit moves: old -> new (both repo-relative, forward slashes)
MOVES = {
    # ---- handovers: keep the three entry points where they are ----
    "PROJECT-STATE.md": "PROJECT-STATE.md",
    "README.md": "README.md",
    "docs/当前状态.md": "docs/当前状态.md",
    "docs/法术专题_交接.md": "docs/法术专题_交接.md",
    "docs/任务系统_交接.md": "docs/任务系统_交接.md",
    "docs/动画专题_交接.md": "docs/动画专题_交接.md",
    "docs/地图接入进度.md": "docs/地图接入进度.md",
    "docs/daily-workflow.md": "docs/daily-workflow.md",
    # older handover snapshots -> archive
    "docs/实施进度.md": "docs/archive/实施进度.md",
    "docs/项目交接.md": "docs/archive/项目交接.md",
    "docs/验收表.md": "docs/archive/验收表.md",

    # ---- spells ----
    "docs/法术总表_按设计文档.md": "docs/spells/法术总表_按设计文档.md",
    "docs/法术制作与测试.md": "docs/spells/法术制作与测试.md",
    "docs/特殊魔法_设计.md": "docs/spells/特殊魔法_设计.md",
    "docs/投射物模型_配方.md": "docs/spells/投射物模型_配方.md",
    "docs/法术热键分页与配键-20260929.md": "docs/spells/法术热键分页与配键-20260929.md",
    "docs/火球链_现状-20261005.md": "docs/spells/火球链_现状-20261005.md",
    "docs/光系链_设计.md": "docs/spells/光系链_设计.md",
    "docs/光系五条链_20261002.md": "docs/spells/光系五条链_20261002.md",
    "docs/召唤天使链_设计.md": "docs/spells/召唤天使链_设计.md",
    "docs/暗系召唤物_动作.md": "docs/spells/暗系召唤物_动作.md",
    "docs/黑夜之手_模型-20261002.md": "docs/spells/黑夜之手_模型-20261002.md",
    "docs/黑夜之手尺寸与震屏-20261009.md": "docs/spells/黑夜之手尺寸与震屏-20261009.md",
    "docs/暗系黑雾重做-20261009.md": "docs/spells/暗系黑雾重做-20261009.md",
    "docs/黑雾闪退与黑夜之手体积-20261009.md": "docs/spells/黑雾闪退与黑夜之手体积-20261009.md",
    "docs/黑雾致盲与屏幕变黑-20261010.md": "docs/spells/黑雾致盲与屏幕变黑-20261010.md",
    "docs/以伤换伤改吸血-20261009.md": "docs/spells/以伤换伤改吸血-20261009.md",
    "docs/water-magic-20260928.md": "docs/spells/water-magic-20260928.md",
    "docs/water-magic-feedback-20260928.md": "docs/spells/water-magic-feedback-20260928.md",
    "docs/water-ocean-barrage-20260928.md": "docs/spells/water-ocean-barrage-20260928.md",
    "docs/water-scale-20260928.md": "docs/spells/water-scale-20260928.md",
    "docs/战斗玩法与水法更新-20260928.md": "docs/spells/战斗玩法与水法更新-20260928.md",
    "docs/法术热键分页与配键-20260929.md": "docs/spells/法术热键分页与配键-20260929.md",

    # ---- quests ----
    "docs/玩法任务书正文草案-20260929.md": "docs/quests/玩法任务书正文草案-20260929.md",
    "docs/玩法指南交付-20260929.md": "docs/quests/玩法指南交付-20260929.md",
    "docs/任务书与法杖修订3-20260929.md": "docs/quests/任务书与法杖修订3-20260929.md",
    "docs/任务书排版与建筑地图修改说明.md": "docs/quests/任务书排版与建筑地图修改说明.md",
    "docs/作者自己编辑任务书-20260930.md": "docs/quests/作者自己编辑任务书-20260930.md",
    "docs/银行与装备任务页实装-20261001.md": "docs/quests/银行与装备任务页实装-20261001.md",
    "docs/任务线参考研究与RouchNao设计-20261004.md": "docs/quests/任务线参考研究与RouchNao设计-20261004.md",
    "docs/周坐望_任务设计.md": "docs/quests/周坐望_任务设计.md",
    "docs/归航银行-借贷与房屋分期设计.md": "docs/quests/归航银行-借贷与房屋分期设计.md",

    # ---- tavern ----
    "docs/装修酒馆与常客音乐实装记录-20261004.md": "docs/tavern/装修酒馆与常客音乐实装记录-20261004.md",
    "docs/酒馆范围与旧HUD恢复实装记录-20261005.md": "docs/tavern/酒馆范围与旧HUD恢复实装记录-20261005.md",
    "docs/生命饱食度与酒馆修订实装记录-20261005.md": "docs/tavern/生命饱食度与酒馆修订实装记录-20261005.md",
    "docs/同伙同步酒馆与模组更新-20261005.md": "docs/tavern/同伙同步酒馆与模组更新-20261005.md",

    # ---- ecology / mounts ----
    "docs/原创生态品种与物品规格-20261003.md": "docs/ecology/原创生态品种与物品规格-20261003.md",
    "docs/异兽牧养完整系统设计-20261003.md": "docs/ecology/异兽牧养完整系统设计-20261003.md",
    "docs/生态全量实装与测试入口-20261004.md": "docs/ecology/生态全量实装与测试入口-20261004.md",
    "docs/生态第二轮修改方案-20261004.md": "docs/ecology/生态第二轮修改方案-20261004.md",
    "docs/生态第二轮修订实装记录-20261004.md": "docs/ecology/生态第二轮修订实装记录-20261004.md",
    "docs/生态美术重做实装记录-20261004.md": "docs/ecology/生态美术重做实装记录-20261004.md",
    "docs/生态与魔法工坊实装验收-20261003.md": "docs/ecology/生态与魔法工坊实装验收-20261003.md",
    "docs/生态返工安装与真实范围-20261003.md": "docs/ecology/生态返工安装与真实范围-20261003.md",
    "docs/巨龙模型_骨骼.md": "docs/ecology/巨龙模型_骨骼.md",
    "docs/dragon-full-aperture-20260928.md": "docs/ecology/dragon-full-aperture-20260928.md",

    # ---- town (RouchNao) ----
    "docs/RouchNao完整玩法蓝图-20261003.md": "docs/town/RouchNao完整玩法蓝图-20261003.md",
    "docs/RouchNao传送阵收尾修复-20261001.md": "docs/town/RouchNao传送阵收尾修复-20261001.md",
    "docs/RouchNao内饰制作交接-20261001.md": "docs/town/RouchNao内饰制作交接-20261001.md",
    "docs/RouchNao剧情与生活推进-20261001.md": "docs/town/RouchNao剧情与生活推进-20261001.md",
    "docs/RouchNao生成事故修复验收-20261001.md": "docs/town/RouchNao生成事故修复验收-20261001.md",
    "docs/RouchNao生活小镇实装验收-20261001.md": "docs/town/RouchNao生活小镇实装验收-20261001.md",
    "docs/RouchNao首领遗迹与酒馆特别委托矩阵-20261002.md": "docs/town/RouchNao首领遗迹与酒馆特别委托矩阵-20261002.md",
    "docs/赫萝斯堪德整套建筑与地景-20261001.md": "docs/town/赫萝斯堪德整套建筑与地景-20261001.md",
    "docs/各玩法路线与细调清单-20261004.md": "docs/town/各玩法路线与细调清单-20261004.md",
    "docs/新同伙环境与协作交接-20261004.md": "docs/town/新同伙环境与协作交接-20261004.md",
    "docs/天空岛内饰交付给建造者.md": "docs/town/天空岛内饰交付给建造者.md",
    "docs/首版可玩系统完成报告-20260929.md": "docs/town/首版可玩系统完成报告-20260929.md",
    "docs/续作完成报告-20260929.md": "docs/town/续作完成报告-20260929.md",
    "docs/项目续作交接-20260929.md": "docs/town/项目续作交接-20260929.md",

    # ---- world map / landmarks ----
    "docs/遗迹定位与传送阵-20260927.md": "docs/worldmap/遗迹定位与传送阵-20260927.md",
    "docs/large-landmarks-20260927.md": "docs/worldmap/large-landmarks-20260927.md",
    "docs/portal-lasers-20260927.md": "docs/worldmap/portal-lasers-20260927.md",
    "docs/building-import-20260927.md": "docs/worldmap/building-import-20260927.md",
    "docs/generation-fix-20260927.md": "docs/worldmap/generation-fix-20260927.md",
    "docs/建筑生成排查与升级-20260930.md": "docs/worldmap/建筑生成排查与升级-20260930.md",
    "docs/主世界首领总表.md": "docs/worldmap/主世界首领总表.md",

    # ---- art ----
    "docs/美术资产清单.md": "docs/art/美术资产清单.md",
    "docs/金币模型_用作者的三份-20261009.md": "docs/art/金币模型_用作者的三份-20261009.md",
    "docs/金币模型紫黑修复-20261009.md": "docs/art/金币模型紫黑修复-20261009.md",
    "docs/模型加载失败排查-多轴rotation-20261004.md": "docs/art/模型加载失败排查-多轴rotation-20261004.md",

    # ---- ui / game feel ----
    "docs/双部位装备实装-20260930.md": "docs/ui/双部位装备实装-20260930.md",
    "docs/正手法杖与独立柜台修订4-20260930.md": "docs/ui/正手法杖与独立柜台修订4-20260930.md",
    "docs/第一人称法杖朝向修订5-20260930.md": "docs/ui/第一人称法杖朝向修订5-20260930.md",

    # ---- tools / workflow ----
    "docs/Blockbench法杖与装备编辑说明-20261004.md": "docs/tools/Blockbench法杖与装备编辑说明-20261004.md",
    "docs/NPC动作产线_Blockbench配方.md": "docs/tools/NPC动作产线_Blockbench配方.md",
    "docs/协作者报的三条闸门问题-20261010.md": "docs/tools/协作者报的三条闸门问题-20261010.md",

    # ---- misc one-off records ----
    "docs/conquest-vanilla-removal-20260928.md": "docs/misc/conquest-vanilla-removal-20260928.md",
    "docs/sea-god-sword-20260928.md": "docs/misc/sea-god-sword-20260928.md",
    "docs/宫殿紫珠灌木崩溃修复-20261001.md": "docs/misc/宫殿紫珠灌木崩溃修复-20261001.md",
    "docs/遗忘后无法学习修复-20260929.md": "docs/misc/遗忘后无法学习修复-20260929.md",
    "docs/下一位Agent完整交接-20260929.md": "docs/misc/下一位Agent完整交接-20260929.md",
    "docs/specs/20260930-counter-and-wand-fixes.md": "docs/misc/20260930-counter-and-wand-fixes.md",

    # ===============================================================
    # 2026-10-20 第二批：10-06 整理之后又堆起来的一级文件。
    # 一级目录只留入口文档；新主题另开目录（boss / ink / life / perf）。
    # ===============================================================

    # ---- spells：风系两条链、暗手三轮、魔法石界面、火法合并 ----
    "docs/wind_two_chains_20261018.md": "docs/spells/wind_two_chains_20261018.md",
    "docs/wind_relic_refinement_20261018.md": "docs/spells/wind_relic_refinement_20261018.md",
    "docs/wind_arsenal_20261018.md": "docs/spells/wind_arsenal_20261018.md",
    "docs/wind_phantom_weapons_20261018.md": "docs/spells/wind_phantom_weapons_20261018.md",
    "docs/手与血爪与索敌-20261010.md": "docs/spells/手与血爪与索敌-20261010.md",
    "docs/手掌方向与索敌吸血-20261010.md": "docs/spells/手掌方向与索敌吸血-20261010.md",
    "docs/手掌黑线多手-20261010.md": "docs/spells/手掌黑线多手-20261010.md",
    "docs/魔法石界面布局修复-20261018.md": "docs/spells/魔法石界面布局修复-20261018.md",
    "docs/同伙火法合并与新世界测试-20261005.md": "docs/spells/同伙火法合并与新世界测试-20261005.md",

    # ---- boss：巨兽 / 巨像 Boss / 地核战场（2026-10-17 起的新主题）----
    "docs/stone_lancer_heavy_20261017.md": "docs/boss/stone_lancer_heavy_20261017.md",
    "docs/stone_lancer_burrow_20261017.md": "docs/boss/stone_lancer_burrow_20261017.md",
    "docs/dark_giant_upperbody_20261018.md": "docs/boss/dark_giant_upperbody_20261018.md",
    "docs/earth_arena_recovery_20261019.md": "docs/boss/earth_arena_recovery_20261019.md",

    # ---- ink：水墨系 Boss / 坐骑（墨鹤、墨龙）----
    "docs/ink-crane-v5.md": "docs/ink/ink-crane-v5.md",
    "docs/ink-dragon-combat-v2.md": "docs/ink/ink-dragon-combat-v2.md",
    "docs/ink-dragon-flight-v3.md": "docs/ink/ink-dragon-flight-v3.md",
    "docs/ink-dragon-ground-observation-v4.md": "docs/ink/ink-dragon-ground-observation-v4.md",

    # ---- life：四条生活路线 / 魔植 / 工坊 / 魔力视 ----
    # 这几篇互相引用成一簇，整簇一起搬，相对链接才不会断。
    "docs/FTB四条生活路线与魔力体系方案-20261006.md": "docs/life/FTB四条生活路线与魔力体系方案-20261006.md",
    "docs/传统植物盘点与四季安排-20261006.md": "docs/life/传统植物盘点与四季安排-20261006.md",
    "docs/魔植与异兽逐品种任务规格-20261006.md": "docs/life/魔植与异兽逐品种任务规格-20261006.md",
    "docs/四条生活路线实装与测试说明-20261007.md": "docs/life/四条生活路线实装与测试说明-20261007.md",
    "docs/生活路线验证记录-20261007.md": "docs/life/生活路线验证记录-20261007.md",
    "docs/玩法路线与修复-20261018.md": "docs/life/玩法路线与修复-20261018.md",
    "docs/魔力视与独立城堡-测试说明-20261018.md": "docs/life/魔力视与独立城堡-测试说明-20261018.md",

    # ---- perf：启动 / 加载 / 卡顿 排查 ----
    "docs/启动卡顿排查-20261010.md": "docs/perf/启动卡顿排查-20261010.md",
    "docs/地图卡顿_勘测阻塞生成-20261010.md": "docs/perf/地图卡顿_勘测阻塞生成-20261010.md",
    "docs/skyisland-perf-fix-20261010.patch": "docs/perf/skyisland-perf-fix-20261010.patch",
    "docs/Conquest换官方版-20261010.md": "docs/perf/Conquest换官方版-20261010.md",
    "docs/startup_spellcloud_fix_20261018.md": "docs/perf/startup_spellcloud_fix_20261018.md",

    # ---- quests ----
    "docs/玩家任务书恢复说明-20261007.md": "docs/quests/玩家任务书恢复说明-20261007.md",

    # ---- town：被 tools/ 读写的进度清单也归主题目录 ----
    "docs/town-progression-manifest.json": "docs/town/town-progression-manifest.json",

    # ---- 配图一律进 previews/（旧规则是"留在一级目录别动"，2026-10-20 起改掉）----
    "docs/一周目正史_物品预览.png": "docs/previews/一周目正史_物品预览.png",
    "docs/宝箱传说残卷_物品预览.png": "docs/previews/宝箱传说残卷_物品预览.png",
    "docs/导图_全法术.png": "docs/previews/导图_全法术.png",
    "docs/庄鹊让_NPC皮肤预览.png": "docs/previews/庄鹊让_NPC皮肤预览.png",
    "docs/庄鹊让_候选模型对比.png": "docs/previews/庄鹊让_候选模型对比.png",
    "docs/魔法阵_贴图预览.png": "docs/previews/魔法阵_贴图预览.png",

    # ---- 生成物（audit_docs.py 的产出，别当文档读）----
    "docs/_doc-inventory.md": "docs/misc/_doc-inventory.md",
}


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    plan = []
    for old, new in sorted(MOVES.items()):
        if old == new:
            continue
        src = os.path.join(repo, old.replace("/", os.sep))
        dst = os.path.join(repo, new.replace("/", os.sep))
        if not os.path.isfile(src) and os.path.isfile(dst):
            # an earlier run already moved it: the entry stays as move history
            plan.append(("done", old, new))
        elif not os.path.isfile(src):
            plan.append(("MISSING", old, new))
        elif os.path.exists(dst):
            plan.append(("EXISTS", old, new))
        else:
            plan.append(("move", old, new))

    bad = [p for p in plan if p[0] in ("MISSING", "EXISTS")]
    todo = [p for p in plan if p[0] == "move"]
    print("planned moves: %d   already moved: %d   problems: %d"
          % (len(todo), len(plan) - len(todo) - len(bad), len(bad)))
    for kind, old, new in plan:
        if kind == "move":
            print("  %-64s -> %s" % (old, new))
    for kind, old, new in bad:
        print("  [%s] %s -> %s" % (kind, old, new))

    if not args.apply:
        print("\ndry run only. Re-run with --apply to move them.")
        return 0 if not bad else 1
    if bad:
        print("\nrefusing to apply: there are problems above.")
        return 1

    # make the target folders first
    for _kind, _old, new in plan:
        d = os.path.dirname(os.path.join(repo, new.replace("/", os.sep)))
        os.makedirs(d, exist_ok=True)

    moved = 0
    for kind, old, new in plan:
        if kind != "move":
            continue
        out = subprocess.run(["git", "mv", "-f", old, new], cwd=repo,
                             capture_output=True, text=True, encoding="utf-8",
                             errors="replace")
        if out.returncode != 0:
            print("  FAILED %s: %s" % (old, (out.stderr or "").strip()[:120]))
            continue
        moved += 1
    print("\nmoved %d file(s) with git mv." % moved)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
