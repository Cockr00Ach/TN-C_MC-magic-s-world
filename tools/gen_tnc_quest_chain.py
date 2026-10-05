#!/usr/bin/env python
"""
gen_tnc_quest_chain.py -- write the TN-C main-story quest chain into the pack.

One source of truth for the whole chain (4 story segments -> quests), so the
structure can be reviewed and re-generated instead of hand-editing dozens of
near-identical JSON files.

Where the data lands
--------------------
modpack/<pack>/kubejs/data/tnc/whisperingquests/
    chapters/main.json          one chapter per story segment
    tasks/main/<name>.json      one quest per story beat

Every quest is `refresh_pool: "triggered"` or started from a dialogue; none of
them carry `start_triggers` -- a quest that declares start_triggers can NOT be
started by startQuest() (see docs/任务系统_交接.md 6.1).

ASCII only on purpose: this console is cp936 and CJK would come out as mojibake.
All Chinese lives in the JSON string values, written as UTF-8.

Usage:
    python tools/gen_tnc_quest_chain.py            # write
    python tools/gen_tnc_quest_chain.py --check    # only report what would change
"""

import argparse
import io
import json
import os
import sys

NS = "tnc"
CATEGORY = "main"

# ---------------------------------------------------------------------------
# chapters: one per story segment
# ---------------------------------------------------------------------------
CHAPTERS = [
    ("s1", "TN-C \u00b7 \u4e00 \u5206\u79bb\u4e4b\u540e"),   # 一 分离之后
    ("s2", "TN-C \u00b7 \u4e8c \u9ed1\u6697\u6f6e"),        # 二 黑暗潮
    ("s3", "TN-C \u00b7 \u4e09 \u9ed1\u6697\u6df1\u5904"),  # 三 黑暗深处
    ("s4", "TN-C \u00b7 \u56db \u6325\u6208"),               # 四 挥戈
]

# ---------------------------------------------------------------------------
# chapters (new) -- the five FTB-style overview beats become real quests here
# ---------------------------------------------------------------------------
# chapter table used by the quests below; order controls tab order
CH = {
    "s1": {"title": "\u4e00 \u00b7 \u5206\u79bb\u4e4b\u540e", "desc": "\u4ece\u4e3b\u57ce\u51fa\u53d1\uff0c\u627e\u5230\u4ed6\u4e3a\u4ec0\u4e48\u8d70\u3002", "order": 10},
    "s2": {"title": "\u4e8c \u00b7 \u9ed1\u6697\u6f6e", "desc": "\u6f6e\u8fdb\u57ce\u4e86\u3002\u5899\u4e0a\u90a3\u9053\u75d5\u4e0d\u662f\u5b83\u4eec\u7559\u7684\u3002", "order": 20},
    "s3": {"title": "\u4e09 \u00b7 \u9ed1\u6697\u6df1\u5904", "desc": "\u95e8\u540e\u9762\u7684\u4eba\uff0c\u4f60\u5df2\u7ecf\u627e\u4e86\u5f88\u591a\u5e74\u3002", "order": 30},
    "s4": {"title": "\u56db \u00b7 \u6325\u6208", "desc": "\u4e0d\u7ad9\u3001\u4e0d\u8fdb\u3001\u4e0d\u8dd1\u3002", "order": 40},
}

# ---------------------------------------------------------------------------
# the chain
#   id            quest file name (tnc:main/<id>)
#   chapter       s1..s4
#   title         <=12 chars
#   short         one line, the quest-book subtitle
#   desc          2-4 sentences, "why" not "what"
#   objectives    list of dicts (type/id/text/amount + extras)
#   rewards       list of dicts
#   next          quest ids unlocked on completion
#   icon          item id
# ---------------------------------------------------------------------------
def q(qid, chapter, title, short, desc, objectives, rewards=None, nxt=None, icon="minecraft:paper"):
    return {
        "id": "%s:main/%s" % (NS, qid),
        "enabled": True,
        "weight": 1,
        "category": CATEGORY,
        "chapter": "%s:chapters/%s" % (NS, chapter),
        "repeatable": False,
        "sync_with_team": False,
        "title": title,
        "short_description": short,
        "description": desc,
        "icon": icon,
        "refresh_pool": "triggered",
        "min_player_level": 0,
        "objectives": objectives,
        "rewards": rewards if rewards is not None else [],
        "next": nxt if nxt is not None else [],
    }


def talk(oid, text):
    """A dialogue objective: completed by our own @quest hook, not by the engine."""
    return {
        "type": "dialogue",
        "id": oid,
        "text": text,
        "amount": 1,
        "consume": False,
        "radius": 10,
        "waypoint_requires_tracking": True,
    }


def kill(oid, text, entity, amount):
    return {"type": "kill", "id": oid, "text": text, "amount": amount, "consume": False, "entity": entity}


def dim(oid, text, dimension):
    return {"type": "dimension", "id": oid, "text": text, "amount": 1, "consume": False, "dimension": dimension}


def xp(amount):
    return {"type": "experience", "amount": amount, "count": 1}


def item(iid, count=1):
    return {"type": "item", "amount": 0, "count": count, "item": iid}


# --- the historical record sheets that already exist in the game -----------
REC = {
    "qianqing": "tnc:zhengshi_qianqing",
    "s1": "tnc:zhengshi_1",
    "s2": "tnc:zhengshi_2",
    "s3": "tnc:zhengshi_3",
    "s4": "tnc:zhengshi_4",
}

QUESTS = [
    # ================= segment 1: 分离之后 =================
    q("s1_self", "s1",
      "\u7b2c\u4e8c\u676f\u9152",                                      # 第二杯酒
      "\u548c\u9152\u9986\u8001\u677f Self \u8bf4\u8bf4\u8bdd",        # 和酒馆老板 Self 说说话
      "\u4e3b\u57ce\u7684\u9152\u9986\u91cc\uff0cSelf \u4e00\u76f4\u5728\u64e6\u90a3\u53ea\u6ca1\u4eba\u7528\u7684\u676f\u5b50\u3002\n\n\u00a77\u4ed6\u597d\u50cf\u6709\u8bdd\u8981\u8bf4\u3002\u00a7r",
      [talk("talk_to_self", "\u548c Self \u5bf9\u8bdd")],
      [xp(20)],
      ["s1_guild"],
      icon="tnc:self_spawn_egg"),

    q("s1_guild", "s1",
      "\u5178\u7c4d\u9986\u6700\u91cc\u9762\u90a3\u4e00\u683c",          # 典籍馆最里面那一格
      "\u53bb\u9b54\u6cd5\u534f\u4f1a\u7684\u5178\u7c4d\u9986\uff0c\u627e\u7ba1\u4e66\u7684\u4eba",
      "\u4ed6\u8d70\u4e4b\u524d\u5728\u9152\u9986\u7ffb\u4e86\u4e00\u5bbf\u7684\u4e66\u67b6\u3002\n\n\u00a77Self \u8bb0\u5f97\u4ed6\u7ffb\u7684\u662f\u54ea\u4e00\u683c\u3002\u00a7r",
      [{"type": "structure", "id": "reach_village", "text": "\u524d\u5f80\u4e00\u5ea7\u6751\u5e84",
        "amount": 1, "consume": False, "radius": 10, "waypoint_requires_tracking": True,
        "structure": "minecraft:village_plains"}],
      [xp(30)],
      ["s1_zhuang"],
      icon="minecraft:bookshelf"),

    q("s1_zhuang", "s1",
      "\u4e0d\u5916\u501f\u7684\u90a3\u4e00\u534a",                      # 不外借的那一半
      "\u4e0e\u5e84\u9e4a\u8ba9\u5bf9\u8bdd",
      "\u5979\u8bb0\u4e86\u5341\u4e00\u5e74\u7684\u7c3f\u5b50\uff0c\u54ea\u5929\u8c01\u501f\u4e86\u4ec0\u4e48\u90fd\u5728\u4e0a\u9762\u3002\n\n\u00a77\u53ea\u6709\u5979\u8fd8\u8bb0\u5f97\u4ed6\u501f\u7684\u662f\u54ea\u4e00\u672c\u3002\u00a7r",
      [talk("talk_to_zhuangquerang", "\u4e0e\u5e84\u9e4a\u8ba9\u5bf9\u8bdd")],
      [item(REC["qianqing"]), xp(30)],
      ["s1_shield"],
      icon="tnc:zhuangquerang_spawn_egg"),

    q("s1_shield", "s1",
      "\u4e00\u9762\u6ca1\u6253\u5b8c\u7684\u76fe",                      # 一面没打完的盾
      "\u53bb\u94c1\u5320\u94fa\u627e\u69d0",
      "\u94c1\u5320\u8bf4\uff1a\u4ed6\u5341\u4e5d\u4e86\uff0c\u8fd8\u6ca1\u51fa\u8fc7\u57ce\u3002\n\n\u00a77\u201c\u5e26\u7740\u5b83\u51fa\u95e8\uff0c\u5c31\u5e26\u7740\u5b83\u8fdb\u95e8\u3002\u201d\u00a7r",
      [talk("talk_to_huai", "\u4e0e\u69d0\u5bf9\u8bdd")],
      [xp(30)],
      ["s1_leave"],
      icon="minecraft:shield"),

    q("s1_leave", "s1",
      "\u51fa\u57ce",                                                  # 出城
      "\u4e09\u5339\u9a6c\u51fa\u57ce",
      "\u5979\u95ee\u5148\u53bb\u54ea\u513f\u3002\u4f60\u7ed9\u4e0d\u51fa\u65b9\u5411\u3002\n\n\u00a77\u53ea\u77e5\u9053\u4ed6\u5728\u67e5\u4eba\u2014\u2014\u4ed6\u5728\u67e5\u8c01\uff1f\u00a7r",
      [{"type": "location", "id": "leave_city", "text": "\u8d70\u51fa\u57ce\u53bb",
        "amount": 1, "consume": False, "radius": 24, "waypoint_requires_tracking": True,
        "x": 0, "y": 70, "z": 0}],
      [item(REC["s1"]), xp(40)],
      ["s2_night"],
      icon="minecraft:saddle"),

    # ================= segment 2: 黑暗潮 =================
    q("s2_night", "s2",
      "\u534a\u591c\u91cc\u7684\u949f",                                # 半夜里的钟
      "\u6f6e\u8fdb\u57ce\u4e86",
      "\u5b83\u4eec\u4e0d\u54ac\u4eba\uff0c\u53ea\u62c6\u57ce\u3002\u62c6\u5b8c\u5c31\u8d70\uff0c\u8c01\u4e5f\u4e0d\u8ffd\u3002\n\n\u00a77\u8fd9\u4e0d\u662f\u63a0\u593a\uff0c\u8fd9\u662f\u6e05\u573a\u3002\u00a7r",
      [kill("thin_the_tide", "\u6e05\u6389\u8fdb\u57ce\u7684\u6f6e", "minecraft:zombie", 12)],
      [xp(60)],
      ["s2_wall"],
      icon="minecraft:bell"),

    q("s2_wall", "s2",
      "\u5899\u4e0a\u7684\u90a3\u9053\u75d5",                          # 墙上的那道痕
      "\u4e1c\u5899\u3002\u90a3\u9053\u7126\u75d5\u4e0d\u5bf9\u52b2",
      "\u7b14\u76f4\u3001\u5f88\u6df1\u3001\u8fb9\u7f18\u53d1\u767d\u2014\u2014\u50cf\u88ab\u4ec0\u4e48\u4ece\u5916\u9762\u4e00\u8def\u7281\u8fdb\u6765\u3002\n\n\u00a77\u6f6e\u53ea\u4f1a\u538b\u5899\u3002\u8fd9\u9053\u75d5\u662f\u4ece\u57ce\u5916\u5f80\u57ce\u91cc\u6253\u3002\u00a7r",
      [{"type": "location", "id": "east_wall", "text": "\u53bb\u4e1c\u5899\u770b\u90a3\u9053\u75d5",
        "amount": 1, "consume": False, "radius": 16, "waypoint_requires_tracking": True,
        "x": 0, "y": 70, "z": 0}],
      [xp(60)],
      ["s2_mountain"],
      icon="minecraft:blackstone"),

    q("s2_mountain", "s2",
      "\u5c71\u4e0a\u7684\u90a3\u4e2a\u4eba",                          # 山上的那个人
      "\u5c71\u91cc\u3002\u4e00\u95f4\u5f88\u5c0f\u7684\u5c4b\u5b50",
      "\u95ee\u5230\u7684\u4e09\u4e2a\u4eba\u90fd\u6307\u8fd9\u4e00\u4e2a\u65b9\u5411\u3002\n\n\u00a77\u95e8\u5f00\u7740\u3002\u4e00\u4e2a\u8001\u4eba\u5750\u5728\u95e8\u91cc\u7684\u9634\u5f71\u4e2d\u3002\u00a7r",
      [talk("talk_to_zuowang", "\u4e0e\u5468\u5750\u671b\u5bf9\u8bdd")],
      [item(REC["s2"]), xp(80)],
      ["s2_see"],
      icon="tnc:zuowang_spawn_egg"),

    q("s2_see", "s2",
      "\u770b\u75d5",                                                  # 看痕
      "\u4ed6\u8ba9\u4f60\u5148\u53bb\u628a\u90a3\u9053\u75d5\u770b\u6e05\u695a",
      "\u201c\u4e1c\u5899\u90a3\u9053\u75d5\uff0c\u8fd8\u5728\u3002\u4f60\u8981\u662f\u771f\u60f3\u95ee\uff0c\u5c31\u81ea\u5df1\u53bb\u770b\u6e05\u695a\u3002\u201d\n\n\u00a77\u4ed6\u77e5\u9053\u7b54\u6848\u3002\u4f46\u90a3\u662f\u4ed6\u7684\u7b54\u6848\u3002\u00a7r",
      [{"type": "location", "id": "back_to_wall", "text": "\u518d\u53bb\u4e00\u8d9f\u4e1c\u5899",
        "amount": 1, "consume": False, "radius": 16, "waypoint_requires_tracking": True,
        "x": 0, "y": 70, "z": 0},
       talk("report_back", "\u56de\u53bb\u544a\u8bc9\u4ed6\u90a3\u662f\u4ec0\u4e48")],
      [xp(60)],
      ["s2_door"],
      icon="minecraft:spyglass"),

    q("s2_door", "s2",
      "\u95e8\u677e\u4e86",                                            # 门松了
      "\u4ed6\u7b54\u5e94\u8ddf\u4f60\u4eec\u8d70",
      "\u201c\u6f6e\u8fdb\u57ce\u4e86\u3002\u4ee5\u524d\u5b83\u4eec\u53ea\u5728\u6ca1\u4eba\u70df\u7684\u5730\u65b9\u8f6c\u3002\u201d\n\n\u00a77\u518d\u677e\u4e0b\u53bb\uff0c\u62c6\u7684\u5c31\u4e0d\u53ea\u662f\u4e00\u5ea7\u8fb9\u57ce\u3002\u00a7r",
      [talk("open_the_door", "\u4e0e\u5468\u5750\u671b\u5bf9\u8bdd")],
      [item(REC["s3"]), xp(100)],
      ["s3_gate"],
      icon="minecraft:deepslate_tiles"),

    # ================= segment 3: 黑暗深处 =================
    q("s3_gate", "s3",
      "\u95e8\u524d",                                                  # 门前
      "\u4e00\u9053\u51e0\u4e4e\u6ca1\u6709\u539a\u5ea6\u7684\u7f1d",
      "\u201c\u8fd9\u9053\u53e3\u5b50\u4e0d\u8ba4\u5c5e\u6027\uff0c\u53ea\u8ba4\u2018\u7167\u2019\u3002\u201d\n\n\u00a77\u4ed6\u62ff\u81ea\u5df1\u7167\u4e86\u4e09\u5e74\uff0c\u591f\u5f00\u4e00\u6b21\u3002\u00a7r",
      [{"type": "location", "id": "the_seam", "text": "\u5230\u90a3\u9053\u7f1d\u524d\u9762\u53bb",
        "amount": 1, "consume": False, "radius": 12, "waypoint_requires_tracking": True,
        "x": 0, "y": 40, "z": 0}],
      [xp(120)],
      ["s3_him"],
      icon="minecraft:end_portal_frame"),

    q("s3_him", "s3",
      "\u4ed6",                                                        # 他
      "\u6700\u91cc\u9762\u3002\u4ed6\u7ad9\u5728\u90a3\u513f",
      "\u80a9\u8180\u5728\u6296\uff0c\u4eba\u9489\u5f97\u7b14\u76f4\u3002\n\n\u00a77\u4f60\u53eb\u4e86\u4e00\u58f0\u4ed6\u7684\u540d\u5b57\u3002\u00a7r",
      [talk("face_him", "\u9762\u5bf9\u4ed6")],
      [xp(150)],
      ["s3_account"],
      icon="minecraft:netherite_sword"),

    q("s3_account", "s3",
      "\u4e09\u4e2a\u4eba\u7684\u8d26",                                # 三个人的账
      "\u4ed6\u4eec\u628a\u8d26\u7ed3\u5728\u4e86\u8fd9\u91cc",
      "\u69d0\u6a2a\u77fe\u5728\u524d\u9762\uff0c\u4eba\u6ca1\u52a8\uff1b\u8ba9\u4e00\u6b21\u90fd\u6ca1\u72b9\u8c6b\u3002\n\n\u00a77\u800c\u4f60\u53ea\u80fd\u5f80\u524d\u8d70\u3002\u00a7r",
      [talk("both_of_them", "\u8ba9\u4ed6\u4eec\u628a\u8d26\u7ed3\u5b8c")],
      [xp(200)],
      ["s3_stone"],
      icon="minecraft:shield"),

    q("s3_stone", "s3",
      "\u5f97\u77f3",                                                  # 得石
      "\u4e00\u9897\u77f3\u5934\u3002\u8fd8\u662f\u70ed\u7684",
      "\u201c\u770b\u4e00\u773c\u3002\u201d\n\n\u00a77\u4ed6\u63e1\u7d27\u4e86\u3002\u7136\u540e\u662f\u5f88\u591a\u5e74\u7684\u9ed1\u3002\u00a7r",
      [talk("read_the_stone", "\u770b\u4ed6\u7559\u4e0b\u7684\u4e1c\u897f")],
      [item(REC["s4"]), xp(250)],
      ["s4_hand"],
      icon="minecraft:echo_shard"),

    # ================= segment 4: 挥戈 =================
    q("s4_hand", "s4",
      "\u624b\u653e\u4e0a\u53bb",                                      # 手放上去
      "\u4e03\u6837\u4e1c\u897f\u4e00\u8d77\u649e\u8fdb\u6765",
      "\u6ca1\u6709\u75bc\u3002\u662f\u6ee1\u3002\n\n\u00a77\u7136\u540e\u4e16\u754c\u53d8\u6210\u7a7a\u767d\u3002\u00a7r",
      [{"type": "location", "id": "touch_the_rift", "text": "\u628a\u624b\u653e\u5230\u88c2\u53e3\u4e0a",
        "amount": 1, "consume": False, "radius": 8, "waypoint_requires_tracking": True,
        "x": 0, "y": 40, "z": 0}],
      [xp(300)],
      ["s4_lynn"],
      icon="minecraft:amethyst_shard"),

    q("s4_lynn", "s4",
      "\u65f6\u95f4\u95f4\u9699",                                      # 时间间隙
      "\u6ca1\u6709\u5929\uff0c\u6ca1\u6709\u5730\uff0c\u6ca1\u6709\u4e0a\u4e0b",
      "\u201c\u4f60\u6765\u4e86\u3002\u201d\n\n\u00a77\u5979\u4ece\u4e0d\u52a8\u624b\u3002\u5979\u53ea\u9700\u8981\u7b49\u3002\u00a7r",
      [talk("meet_lynn", "\u9762\u5bf9\u5979")],
      [xp(350)],
      ["s4_ge"],
      icon="minecraft:end_crystal"),

    q("s4_ge", "s4",
      "\u6325\u6208",                                                  # 挥戈
      "\u4f60\u4e0d\u7ad9\u3001\u4e0d\u8fdb\u3001\u4e0d\u8dd1",
      "\u201c\u6211\u8fd9\u4e00\u4e0b\uff0c\u4e0d\u7528\u9000\u4e09\u820d\u3002\u201d\n\n\u00a77\u9000\u5230\u591f\u7528\u5c31\u884c\u3002\u00a7r",
      [talk("brandish_the_ge", "\u6325\u51fa\u90a3\u4e00\u4e0b")],
      [xp(500)],
      ["s4_stones"],
      icon="minecraft:nether_star"),

    q("s4_stones", "s4",
      "\u56db\u5757\u77f3\u5934",                                      # 四块石头
      "\u4e3b\u57ce\u3002\u6e05\u6668\u3002\u9152\u9986\u95e8\u53e3",
      "\u201c\u7b49\u4e00\u4ef6\u6211\u4e0d\u786e\u5b9a\u6709\u6ca1\u6709\u53d1\u751f\u8fc7\u7684\u4e8b\u3002\u201d\n\n\u00a77\u4ed6\u628a\u5e03\u6536\u8d77\u6765\uff0c\u4e00\u5757\u4e00\u5757\u6536\u8fdb\u6000\u91cc\u3002\u00a7r",
      [talk("the_four_stones", "\u56de\u9152\u9986\u95e8\u53e3\u53bb\u770b\u4e00\u773c")],
      [item(REC["s4"]), xp(1000)],
      [],
      icon="minecraft:heart_of_the_sea"),
]


def write(path, text, check):
    if os.path.exists(path):
        with io.open(path, encoding="utf-8") as fh:
            if fh.read() == text:
                return False
    if not check:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
    return True


def dump(obj):
    # 2-space indent + LF + trailing newline, same shape as the hand-written files
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    packs = [
        d for d in os.listdir(os.path.join(repo, "modpack"))
        if os.path.isdir(os.path.join(repo, "modpack", d, "kubejs"))
    ]
    if not packs:
        print("ERROR: no pack folder with kubejs/ under modpack/")
        return 1

    changed = 0
    for pack in packs:
        root = os.path.join(repo, "modpack", pack, "kubejs", "data", NS, "whisperingquests")

        # chapters
        for cid, meta in CH.items():
            obj = {
                "id": "%s:chapters/%s" % (NS, cid),
                "category": CATEGORY,
                "title": meta["title"],
                "description": meta["desc"],
                "order": meta["order"],
            }
            p = os.path.join(root, "chapters", "%s.json" % cid)
            if write(p, dump(obj), args.check):
                changed += 1
                print("%s chapters/%s.json" % ("would write" if args.check else "wrote", cid))

        # quests
        for quest in QUESTS:
            name = quest["id"].split("/", 1)[1]
            p = os.path.join(root, "tasks", CATEGORY, "%s.json" % name)
            if write(p, dump(quest), args.check):
                changed += 1
                print("%s tasks/%s/%s.json" % ("would write" if args.check else "wrote", CATEGORY, name))

    print("%d file(s) %s" % (changed, "differ" if args.check else "written"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
