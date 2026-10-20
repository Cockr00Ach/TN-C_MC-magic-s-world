# -*- coding: utf-8 -*-
"""apply_dungeon_mob_lists.py -- put the 12 TN-C minions into the Dungeon-Realm spawn tables.

WHY THIS IS A SCRIPT AND NOT 10 HAND EDITS
==========================================
Dungeon-Realm picks mobs from data/library_of_exile/library_of_exile_mob_list/<theme>.json
(`mobs[].mob_id` + `weight`). Those files are the only lever we have: the theme rosters are
otherwise vanilla/third-party entities whose animations we cannot touch, so the way our own mobs
enter the dungeon is by weight in these lists.

Two different treatments, on purpose:

  VANILLA-FILLER THEMES (forest, generic_undead, spider_forest, green, map_default)
      full rewrite -- these five were literally zombies/spiders/slimes and are the weak spot.

  THIRD-PARTY THEMES (cement, nether, sandstone, tent, evil_villager)
      append-only -- those rosters come from mods with their own animations and balance, so we
      only give ours a small share instead of stepping on the author's work.

Idempotent: re-running rewrites the vanilla themes to the same table and never duplicates the
appended entries.

    python tools/apply_dungeon_mob_lists.py --check     # report, change nothing
    python tools/apply_dungeon_mob_lists.py --apply
"""
import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST_DIR = os.path.join(
    ROOT, "modpack", "元素觉醒1.4.3-魔改版-20260915", "config", "openloader", "data",
    "yuansujuexing", "data", "library_of_exile", "library_of_exile_mob_list")

# full rewrites -- the five vanilla-filler themes
REWRITE = {
    # 蛛巢：先钻出来吐网，再扑脸；原版蜘蛛退成陪衬。哥布林是巢穴的"住客"。
    "forest": [
        ("tnc:sewer_crawler", 380),
        ("tnc:moss_carapace_spider", 250),
        ("tnc:goblin_skirmisher", 220),
        ("minecraft:cave_spider", 150),
        ("minecraft:witch", 100),
    ],
    # 骸骨队列：弩手在后排压制，僵尸成前排肉盾
    "generic_undead": [
        ("tnc:bone_crossbowman", 400),
        ("minecraft:zombie", 300),
        ("minecraft:skeleton", 200),
        ("minecraft:husk", 100),
    ],
    "spider_forest": [
        ("tnc:sewer_crawler", 600),
        ("tnc:moss_carapace_spider", 250),
        ("minecraft:cave_spider", 150),
    ],
    # 绿：投火手负责逼走位，尸虫负责逼集火，触手怪守住水边
    "green": [
        ("minecraft:slime", 280),
        ("tnc:tentacle_horror", 220),
        ("minecraft:creeper", 220),
        ("tnc:ash_flinger", 180),
        ("tnc:grave_grub", 140),
        ("minecraft:phantom", 90),
    ],
    # 通用底池：每只新怪都露个脸，和原版大约五五开 —— 前期不会突然变地狱，
    # 但下几次地城一定见得到我们的怪。
    "map_default": [
        ("minecraft:zombie", 400),
        ("minecraft:skeleton", 250),
        ("minecraft:spider", 150),
        ("minecraft:pillager", 150),
        ("tnc:lantern_boar", 130),
        ("tnc:bone_crossbowman", 130),
        ("tnc:sewer_crawler", 110),
        ("tnc:goblin_skirmisher", 110),
        ("tnc:grave_grub", 110),
        ("tnc:rust_hook_butcher", 90),
        ("tnc:ash_flinger", 90),
        ("tnc:kobold_miner", 90),
        ("tnc:frost_weaver", 75),
        ("tnc:umbral_binder", 75),
        ("tnc:copper_fusilier", 75),
        ("tnc:tentacle_horror", 70),
        ("tnc:orc_brute", 60),
        ("tnc:ash_monk", 55),
        ("tnc:steam_sentinel", 55),
        ("tnc:minotaur_warden", 45),
        ("minecraft:witch", 25),
        ("minecraft:slime", 25),
    ],
}

# append-only -- give our mobs a minority share without reworking the author's rosters
APPEND = {
    "cement": [("tnc:steam_sentinel", 120), ("tnc:rust_hook_butcher", 150), ("tnc:orc_brute", 110)],
    "sandstone": [("tnc:bone_crossbowman", 150), ("tnc:ash_flinger", 150),
                  ("tnc:minotaur_warden", 110)],
    "tent": [("tnc:ash_monk", 120), ("tnc:umbral_binder", 120), ("tnc:kobold_miner", 80)],
}


def load(theme):
    path = os.path.join(LIST_DIR, theme + ".json")
    with open(path, encoding="utf-8") as f:
        return path, json.load(f)


def render(theme):
    """returns (path, old_doc, new_doc)"""
    path, doc = load(theme)
    if theme in REWRITE:
        doc["mobs"] = [{"mob_id": mid, "weight": weight} for mid, weight in REWRITE[theme]]
    if theme in APPEND:
        have = {m["mob_id"] for m in doc["mobs"]}
        for mid, weight in APPEND[theme]:
            if mid not in have:
                doc["mobs"].append({"mob_id": mid, "weight": weight})
        doc["_comment_tnc"] = "TN-C 地下城小怪：仅追加，作者原有名册不动（2026-10-20）"
    return path, doc


def summarize(doc, prefix):
    total = sum(m["weight"] for m in doc["mobs"])
    ours = [m for m in doc["mobs"] if m["mob_id"].startswith("tnc:")]
    share = sum(m["weight"] for m in ours) / float(total) if total else 0.0
    return "%s%s  %2d 种 / 权重 %d / 我方 %d 种占 %.0f%%" % (
        prefix, "", len(doc["mobs"]), total, len(ours), share * 100)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真的写文件")
    ap.add_argument("--check", action="store_true", help="只报告")
    args = ap.parse_args()
    if not os.path.isdir(LIST_DIR):
        raise SystemExit("mob_list 目录不存在: %s" % LIST_DIR)

    for theme in list(REWRITE) + list(APPEND):
        path, doc = render(theme)
        print(summarize(doc, "%-15s" % theme))
        if args.apply:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(doc, f, indent=2, ensure_ascii=False)
                f.write("\n")
    if not args.apply:
        print("\n(dry run —— 加 --apply 才写文件)")


if __name__ == "__main__":
    main()
