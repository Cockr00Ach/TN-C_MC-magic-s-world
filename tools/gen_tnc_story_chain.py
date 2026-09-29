#!/usr/bin/env python
"""
gen_tnc_story_chain.py -- the TN-C main story chain: quests + gated dialogue.

Source of truth for the whole first-playthrough story implemented from
剧情/开场_分离之后.md, 第二段_黑暗潮.md, 第三段_黑暗深处.md, 第四段_挥戈.md.

Produces, under modpack/<pack>/kubejs/data/tnc/whisperingquests/ and
modpack/<pack>/kubejs/src... (dialogues live in the JAR, see DIALOGUE_OUT):

    chapters/<seg>.json          one chapter per story segment
    tasks/main/<name>.json       one quest per story beat

and, into the mod resources:

    src/main/resources/data/tnc/dialogues/<npc>[_NN].txt

Design constraints that shaped this (all verified against the real mods):
  * whisperingquests has NO 'checkmark' objective.  Usable types:
    dialogue / kill / item / location / dimension / biome / structure / advancement.
  * A quest that declares start_triggers can NOT be started by startQuest()
    (canPlayerTake rejects it).  So nothing here declares start_triggers.
  * A quest only shows in the book once it is active/claimable/refreshed, so
    every quest is either `refresh_pool: "triggered"` (reached through `next`)
    or started explicitly by a dialogue's @activate.
  * Our NPCs are not villagers and do not use p1nero_dl, so the engine never
    fires their dialogue objectives -- our own @quest / @activate hooks do.
  * Dialogue gating uses @requires / @excludes on COMPLETED quests; the picker
    takes the highest-numbered script whose gates pass (DialoguePicker).

ASCII only on purpose: this console is cp936 and CJK would come out as mojibake.

Usage:
    python tools/gen_tnc_story_chain.py
    python tools/gen_tnc_story_chain.py --check
"""

import argparse
import io
import json
import os
import sys

NS = "tnc"
CAT = "main"

# ---------------------------------------------------------------------------
# chapters: one per story segment
# ---------------------------------------------------------------------------
CH = {
    "s1": ("\u4e00 \u00b7 \u5206\u79bb\u4e4b\u540e",
           "\u4ece\u4e3b\u57ce\u51fa\u53d1\uff0c\u627e\u5230\u4ed6\u4e3a\u4ec0\u4e48\u8d70\u3002", 10),
    "s2": ("\u4e8c \u00b7 \u9ed1\u6697\u6f6e",
           "\u6f6e\u8fdb\u57ce\u4e86\u3002\u5899\u4e0a\u90a3\u9053\u75d5\u4e0d\u662f\u5b83\u4eec\u7559\u7684\u3002", 20),
    "s3": ("\u4e09 \u00b7 \u9ed1\u6697\u6df1\u5904",
           "\u95e8\u540e\u9762\u7684\u4eba\uff0c\u4f60\u5df2\u7ecf\u627e\u4e86\u5f88\u591a\u5e74\u3002", 30),
    "s4": ("\u56db \u00b7 \u6325\u6208",
           "\u4e0d\u7ad9\u3001\u4e0d\u8fdb\u3001\u4e0d\u8dd1\u3002", 40),
}

# a stable, save-independent spot: the ground portal ring, placed near world spawn
PORTAL = (137, 82, -343)

# ---------------------------------------------------------------------------
# quests
# ---------------------------------------------------------------------------
def talk(oid, text):
    return {"type": "dialogue", "id": oid, "text": text, "amount": 1, "consume": False,
            "radius": 10, "waypoint_requires_tracking": True}


def kill(oid, text, entity, amount):
    return {"type": "kill", "id": oid, "text": text, "amount": amount,
            "consume": False, "entity": entity}


def goto(oid, text, radius=10, pos=PORTAL):
    """
    A location objective -- NOT USED BY THE MAIN CHAIN ANY MORE (2026-09-29).

    Why it is still here: the engine accepts it, and hand-written quests outside
    this chain may still want it.  But the story chain was moved off it because
    the position is a FIXED number in a static JSON file, while the sky island
    and its ground portal are generated per save near that save's spawn point.
    A hardcoded position is therefore correct in exactly one world and silently
    unfinishable in every other -- the quest just sits there, blocking the chain.

    (Schema note, learned the hard way: the engine reads a NESTED position
    object.  Flat x/y/z is rejected at load time with
    "Location objective requires position object" and the quest is dropped.)
    """
    return {"type": "location", "id": oid, "text": text, "amount": 1, "consume": False,
            "radius": radius, "waypoint_requires_tracking": True,
            "position": {"x": pos[0], "y": pos[1], "z": pos[2]}}


def xp(n):
    return {"type": "experience", "amount": n, "count": 1}


def item(iid, count=1):
    return {"type": "item", "amount": 0, "count": count, "item": iid}


REC = {
    "qianqing": "tnc:zhengshi_qianqing",
    "s1": "tnc:zhengshi_1",
    "s2": "tnc:zhengshi_2",
    "s3": "tnc:zhengshi_3",
    "s4": "tnc:zhengshi_4",
}

# id, chapter, title, short, description, objectives, rewards, next, icon
Q = [
    # ===================== segment 1 =====================
    ("s1_self", "s1",
     "\u7b2c\u4e8c\u676f\u9152",
     "\u548c\u9152\u9986\u8001\u677f Self \u8bf4\u8bf4\u8bdd",
     "\u4e3b\u57ce\u7684\u9152\u9986\u91cc\uff0cSelf \u4e00\u76f4\u5728\u64e6\u90a3\u53ea\u6ca1\u4eba\u7528\u7684\u676f\u5b50\u3002\n\n\u00a77\u4ed6\u597d\u50cf\u6709\u8bdd\u8981\u8bf4\u3002\u00a7r",
     [talk("talk_to_self", "\u548c Self \u5bf9\u8bdd")], [xp(20)], ["s1_guild"],
     "tnc:self_spawn_egg"),

    ("s1_guild", "s1",
     "\u5178\u7c4d\u9986\u6700\u91cc\u9762\u90a3\u4e00\u683c",
     "\u53bb\u9b54\u6cd5\u534f\u4f1a\u7684\u5178\u7c4d\u9986\uff0c\u627e\u7ba1\u4e66\u7684\u4eba",
     "\u4ed6\u8d70\u4e4b\u524d\u5728\u9152\u9986\u7ffb\u4e86\u4e00\u5bbf\u7684\u4e66\u67b6\u3002\n\n\u00a77Self \u8bb0\u5f97\u4ed6\u7ffb\u7684\u662f\u54ea\u4e00\u683c\u3002\u00a7r",
     [talk("reach_archive", "\u53bb\u5178\u7c4d\u9986\u627e\u7ba1\u4e66\u7684\u4eba")], [xp(30)], ["s1_zhuang"],
     "minecraft:bookshelf"),

    ("s1_zhuang", "s1",
     "\u4e0d\u5916\u501f\u7684\u90a3\u4e00\u534a",
     "\u4e0e\u5e84\u9e4a\u8ba9\u5bf9\u8bdd",
     "\u5979\u8bb0\u4e86\u5341\u4e00\u5e74\u7684\u7c3f\u5b50\uff0c\u54ea\u5929\u8c01\u501f\u4e86\u4ec0\u4e48\u90fd\u5728\u4e0a\u9762\u3002\n\n\u00a77\u53ea\u6709\u5979\u8fd8\u8bb0\u5f97\u4ed6\u501f\u7684\u662f\u54ea\u4e00\u672c\u3002\u00a7r",
     [talk("talk_to_zhuangquerang", "\u4e0e\u5e84\u9e4a\u8ba9\u5bf9\u8bdd")],
     [item(REC["qianqing"]), xp(30)], ["s1_shield"], "tnc:zhuangquerang_spawn_egg"),

    ("s1_shield", "s1",
     "\u4e00\u9762\u6ca1\u6253\u5b8c\u7684\u76fe",
     "\u53bb\u94c1\u5320\u94fa\u627e\u69d0",
     "\u94c1\u5320\u8bf4\uff1a\u4ed6\u5341\u4e5d\u4e86\uff0c\u8fd8\u6ca1\u51fa\u8fc7\u57ce\u3002\n\n\u00a77\u201c\u5e26\u7740\u5b83\u51fa\u95e8\uff0c\u5c31\u5e26\u7740\u5b83\u8fdb\u95e8\u3002\u201d\u00a7r",
     [talk("talk_to_huai", "\u4e0e\u69d0\u5bf9\u8bdd")], [xp(30)], ["s1_leave"],
     "minecraft:shield"),

    ("s1_leave", "s1",
     "\u51fa\u57ce",
     "\u4e09\u5339\u9a6c\u51fa\u57ce",
     "\u5979\u95ee\u5148\u53bb\u54ea\u513f\u3002\u4f60\u7ed9\u4e0d\u51fa\u65b9\u5411\u3002\n\n\u00a77\u53ea\u77e5\u9053\u4ed6\u5728\u67e5\u4eba\u2014\u2014\u4ed6\u5728\u67e5\u8c01\uff1f\u00a7r",
     # ⚠️ 目标文案不要写人称 ✗ —— 这一环由**庄鹊让自己**的对话完成（zhuangquerang_02），
     #    写成"告诉她你准备走了"就成了"告诉她自己她要走了"（2026-09-29 用户一眼看出来的）。
     #    同理：以后加"找某人"的目标时，先确认那一环挂在谁身上再写人称。
     [talk("leave_city", "\u628a\u51fa\u53d1\u7684\u4e8b\u5b9a\u4e0b\u6765")],
     [item(REC["s1"]), xp(40)], ["s2_bell"], "minecraft:saddle"),

    # ===================== segment 2 =====================
    ("s2_bell", "s2",
     "\u534a\u591c\u91cc\u7684\u949f",
     "\u6f6e\u8fdb\u57ce\u4e86",
     "\u5b83\u4eec\u4e0d\u54ac\u4eba\uff0c\u53ea\u62c6\u57ce\u3002\u62c6\u5b8c\u5c31\u8d70\uff0c\u8c01\u4e5f\u4e0d\u8ffd\u3002\n\n\u00a77\u8fd9\u4e0d\u662f\u63a0\u593a\uff0c\u8fd9\u662f\u6e05\u573a\u3002\u00a7r",
     [kill("thin_the_tide", "\u6e05\u6389\u8fdb\u57ce\u7684\u6f6e\uff0812\u53ea\uff09", "minecraft:zombie", 12)],
     [xp(60)], ["s2_wall"], "minecraft:bell"),

    ("s2_wall", "s2",
     "\u5899\u4e0a\u7684\u90a3\u9053\u75d5",
     "\u4e1c\u5899\u3002\u90a3\u9053\u7126\u75d5\u4e0d\u5bf9\u52b2",
     "\u7b14\u76f4\u3001\u5f88\u6df1\u3001\u8fb9\u7f18\u53d1\u767d\u2014\u2014\u50cf\u88ab\u4ec0\u4e48\u4ece\u5916\u9762\u4e00\u8def\u7281\u8fdb\u6765\u3002\n\n\u00a77\u6f6e\u53ea\u4f1a\u538b\u5899\u3002\u8fd9\u9053\u75d5\u662f\u4ece\u57ce\u5916\u5f80\u57ce\u91cc\u6253\u3002\u00a7r",
     [talk("read_the_mark", "\u8ddf\u8ba9\u770b\u90a3\u9053\u75d5")], [xp(60)], ["s2_mountain"],
     "minecraft:blackstone"),

    ("s2_mountain", "s2",
     "\u5c71\u4e0a\u7684\u90a3\u4e2a\u4eba",
     "\u5c71\u91cc\u3002\u4e00\u95f4\u5f88\u5c0f\u7684\u5c4b\u5b50",
     "\u95ee\u5230\u7684\u4e09\u4e2a\u4eba\u90fd\u6307\u8fd9\u4e00\u4e2a\u65b9\u5411\u3002\n\n\u00a77\u95e8\u5f00\u7740\u3002\u4e00\u4e2a\u8001\u4eba\u5750\u5728\u95e8\u91cc\u7684\u9634\u5f71\u4e2d\u3002\u00a7r",
     [talk("talk_to_zuowang", "\u4e0e\u5468\u5750\u671b\u5bf9\u8bdd")],
     [item(REC["s2"]), xp(80)], ["s2_see"], "tnc:zuowang_spawn_egg"),

    ("s2_see", "s2",
     "\u770b\u75d5",
     "\u4ed6\u8ba9\u4f60\u5148\u53bb\u628a\u90a3\u9053\u75d5\u770b\u6e05\u695a",
     "\u201c\u4e1c\u5899\u90a3\u9053\u75d5\uff0c\u8fd8\u5728\u3002\u4f60\u8981\u662f\u771f\u60f3\u95ee\uff0c\u5c31\u81ea\u5df1\u53bb\u770b\u6e05\u695a\u3002\u201d\n\n\u00a77\u4ed6\u77e5\u9053\u7b54\u6848\u3002\u4f46\u90a3\u662f\u4ed6\u7684\u7b54\u6848\u3002\u00a7r",
     [talk("back_to_the_mark", "\u56de\u53bb\u628a\u770b\u5230\u7684\u8bf4\u51fa\u6765")],
     [xp(60)], ["s2_door"], "minecraft:spyglass"),

    ("s2_door", "s2",
     "\u95e8\u677e\u4e86",
     "\u4ed6\u7b54\u5e94\u8ddf\u4f60\u4eec\u8d70",
     "\u201c\u6f6e\u8fdb\u57ce\u4e86\u3002\u4ee5\u524d\u5b83\u4eec\u53ea\u5728\u6ca1\u4eba\u70df\u7684\u5730\u65b9\u8f6c\u3002\u201d\n\n\u00a77\u518d\u677e\u4e0b\u53bb\uff0c\u62c6\u7684\u5c31\u4e0d\u53ea\u662f\u4e00\u5ea7\u8fb9\u57ce\u3002\u00a7r",
     [talk("open_the_door", "\u4e0e\u5468\u5750\u671b\u5bf9\u8bdd")],
     [item(REC["s3"]), xp(100)], ["s3_gate"], "minecraft:deepslate_tiles"),

    # ===================== segment 3 =====================
    ("s3_gate", "s3",
     "\u95e8\u524d",
     "\u4e00\u9053\u51e0\u4e4e\u6ca1\u6709\u539a\u5ea6\u7684\u7f1d",
     "\u201c\u8fd9\u9053\u53e3\u5b50\u4e0d\u8ba4\u5c5e\u6027\uff0c\u53ea\u8ba4\u2018\u7167\u2019\u3002\u201d\n\n\u00a77\u4ed6\u62ff\u81ea\u5df1\u7167\u4e86\u4e09\u5e74\uff0c\u591f\u5f00\u4e00\u6b21\u3002\u00a7r",
     [talk("the_seam", "\u8ddf\u4ed6\u5230\u90a3\u9053\u7f1d\u524d\u9762")], [xp(120)], ["s3_him"],
     "minecraft:end_portal_frame"),

    ("s3_him", "s3",
     "\u4ed6",
     "\u6700\u91cc\u9762\u3002\u4ed6\u7ad9\u5728\u90a3\u513f",
     "\u80a9\u8180\u5728\u6296\uff0c\u4eba\u9489\u5f97\u7b14\u76f4\u3002\n\n\u00a77\u4f60\u53eb\u4e86\u4e00\u58f0\u4ed6\u7684\u540d\u5b57\u3002\u00a7r",
     [talk("face_him", "\u9762\u5bf9\u4ed6")], [xp(150)], ["s3_account"], "minecraft:netherite_sword"),

    ("s3_account", "s3",
     "\u4e09\u4e2a\u4eba\u7684\u8d26",
     "\u4ed6\u4eec\u628a\u8d26\u7ed3\u5728\u4e86\u8fd9\u91cc",
     "\u69d0\u6a2a\u77fe\u5728\u524d\u9762\uff0c\u4eba\u6ca1\u52a8\uff1b\u8ba9\u4e00\u6b21\u90fd\u6ca1\u72b9\u8c6b\u3002\n\n\u00a77\u800c\u4f60\u53ea\u80fd\u5f80\u524d\u8d70\u3002\u00a7r",
     [talk("both_of_them", "\u8ba9\u4ed6\u4eec\u628a\u8d26\u7ed3\u5b8c")], [xp(200)], ["s3_stone"],
     "minecraft:shield"),

    ("s3_stone", "s3",
     "\u5f97\u77f3",
     "\u4e00\u9897\u77f3\u5934\u3002\u8fd8\u662f\u70ed\u7684",
     "\u201c\u770b\u4e00\u773c\u3002\u201d\n\n\u00a77\u4ed6\u63e1\u7d27\u4e86\u3002\u7136\u540e\u662f\u5f88\u591a\u5e74\u7684\u9ed1\u3002\u00a7r",
     [talk("read_the_stone", "\u770b\u4ed6\u7559\u4e0b\u7684\u4e1c\u897f")],
     [item(REC["s4"]), xp(250)], ["s4_hand"], "minecraft:echo_shard"),

    # ===================== segment 4 =====================
    ("s4_hand", "s4",
     "\u624b\u653e\u4e0a\u53bb",
     "\u4e03\u6837\u4e1c\u897f\u4e00\u8d77\u649e\u8fdb\u6765",
     "\u6ca1\u6709\u75bc\u3002\u662f\u6ee1\u3002\n\n\u00a77\u7136\u540e\u4e16\u754c\u53d8\u6210\u7a7a\u767d\u3002\u00a7r",
     [talk("touch_the_rift", "\u8d70\u5230\u88c2\u53e3\u524d\u9762")], [xp(300)], ["s4_lynn"],
     "minecraft:amethyst_shard"),

    ("s4_lynn", "s4",
     "\u65f6\u95f4\u95f4\u9699",
     "\u6ca1\u6709\u5929\uff0c\u6ca1\u6709\u5730\uff0c\u6ca1\u6709\u4e0a\u4e0b",
     "\u201c\u4f60\u6765\u4e86\u3002\u201d\n\n\u00a77\u5979\u4ece\u4e0d\u52a8\u624b\u3002\u5979\u53ea\u9700\u8981\u7b49\u3002\u00a7r",
     [talk("meet_lynn", "\u9762\u5bf9\u5979")], [xp(350)], ["s4_ge"], "minecraft:end_crystal"),

    ("s4_ge", "s4",
     "\u6325\u6208",
     "\u4f60\u4e0d\u7ad9\u3001\u4e0d\u8fdb\u3001\u4e0d\u8dd1",
     "\u201c\u6211\u8fd9\u4e00\u4e0b\uff0c\u4e0d\u7528\u9000\u4e09\u820d\u3002\u201d\n\n\u00a77\u9000\u5230\u591f\u7528\u5c31\u884c\u3002\u00a7r",
     [talk("brandish_the_ge", "\u6325\u51fa\u90a3\u4e00\u4e0b")], [xp(500)], ["s4_stones"],
     "minecraft:nether_star"),

    ("s4_stones", "s4",
     "\u56db\u5757\u77f3\u5934",
     "\u4e3b\u57ce\u3002\u6e05\u6668\u3002\u9152\u9986\u95e8\u53e3",
     "\u201c\u7b49\u4e00\u4ef6\u6211\u4e0d\u786e\u5b9a\u6709\u6ca1\u6709\u53d1\u751f\u8fc7\u7684\u4e8b\u3002\u201d\n\n\u00a77\u4ed6\u628a\u5e03\u6536\u8d77\u6765\uff0c\u4e00\u5757\u4e00\u5757\u6536\u8fdb\u6000\u91cc\u3002\u00a7r",
     [talk("the_four_stones", "\u56de\u9152\u9986\u95e8\u53e3\u53bb\u770b\u4e00\u773c")],
     [item(REC["s4"]), xp(1000)], [], "minecraft:heart_of_the_sea"),
]


# ---------------------------------------------------------------------------
# dialogue scripts: (npc, seq, requires, excludes, quests, activate, lines)
#   seq 0 -> "<npc>_first", otherwise "<npc>_%02d"
#   lines: (speaker, text); speaker "" = narration
# ---------------------------------------------------------------------------
def D(npc, seq, lines, requires=(), excludes=(), quests=(), activate=None):
    return dict(npc=npc, seq=seq, lines=lines,
                requires=list(requires), excludes=list(excludes),
                quests=list(quests), activate=activate)


DIALOGUES = [
    # ---------------- Self ----------------
    D("self", 0, [
        ("", "\uff08\u9152\u9986\u91cc\u53ea\u6709\u4f60\u4eec\u4e24\u4e2a\u4eba\u3002\u684c\u4e0a\u90a3\u676f\u9152\u4e00\u53e3\u6ca1\u52a8\u3002\uff09"),
        ("Self", "\u4f60\u5728\u8fd9\u513f\u5750\u4e86\u4e00\u6574\u591c\u4e86\uff0c\u5f52\u3002\u684c\u4e0a\u90a3\u676f\u9152\u4f60\u4e00\u53e3\u6ca1\u52a8\uff0c\u6211\u7ed9\u4f60\u6362\u4e00\u676f\u70ed\u7684\u5427\uff0c\u51c9\u7740\u559d\u4f24\u80c3\u3002"),
        ("\u5f52", "\u4e0d\u7528\u6362\u3002\u90a3\u676f\u4e0d\u662f\u6211\u7684\u3002"),
        ("Self", "\u2026\u2026\u4ed6\u5929\u6ca1\u4eae\u5c31\u8d70\u4e86\u3002\u8d70\u4e4b\u524d\u8fd8\u56de\u5934\u770b\u4e86\u4e00\u773c\uff0c\u50cf\u662f\u60f3\u7559\u53e5\u8bdd\uff0c\u6700\u540e\u4ec0\u4e48\u4e5f\u6ca1\u8bf4\u3002"),
        ("\u5f52", "\u6211\u77e5\u9053\u3002\u4ed6\u8981\u662f\u60f3\u8bf4\uff0c\u6628\u5929\u5c31\u8bf4\u4e86\u3002"),
        ("Self", "\u90a3\u4f60\u8fd8\u5750\u5728\u8fd9\u513f\u5e72\u4ec0\u4e48\uff1f"),
        ("\u5f52", "\u7b49\u4ed6\u56de\u6765\u3002"),
        ("Self", "\u4ed6\u8ddf\u4f60\u8bf4\u8fc7\u300c\u770b\u6e05\u695a\u4e86\u5c31\u56de\u6765\u300d\uff0c\u8fd9\u8bdd\u6211\u4eb2\u8033\u542c\u89c1\u7684\u3002\u4f60\u8fd8\u6709\u4ec0\u4e48\u4e0d\u653e\u5fc3\u7684\u3002"),
        ("\u5f52", "\u6211\u4e0d\u662f\u4e0d\u653e\u5fc3\u90a3\u53e5\u8bdd\u3002\u4ed6\u8bf4\u7684\u662f\u300c\u770b\u6e05\u695a\u4e86\u300d\u2014\u2014\u53ef\u4ed6\u6ca1\u8bf4\uff0c\u4ed6\u53bb\u770b\u4ec0\u4e48\u3002"),
        ("Self", "\u2026\u2026\u4f60\u8fd9\u4e2a\u4eba\uff0c\u8ba4\u8d77\u6b7b\u7406\u6765\u6bd4\u8c01\u90fd\u7284\u3002\u884c\uff0c\u6211\u7ed9\u4f60\u7559\u7740\uff0c\u8c01\u95ee\u90fd\u8bf4\u662f\u6211\u7684\u9152\u3002"),
        ("Self", "\u4ed6\u8d70\u4e4b\u524d\uff0c\u5728\u6211\u8fd9\u513f\u7ffb\u4e86\u4e00\u665a\u4e0a\u7684\u4e66\u67b6\uff0c\u7ffb\u5f97\u4e71\u4e03\u516b\u7cdf\uff0c\u5929\u4eae\u4e86\u624d\u8d70\u3002"),
        ("Self", "\u6211\u4e0d\u77e5\u9053\u4ed6\u627e\u7684\u662f\u4ec0\u4e48\u4e66 \u2014\u2014 \u4f46\u6211\u8bb0\u5f97\u4ed6\u7ffb\u7684\u662f\u54ea\u4e00\u683c\u3002"),
        ("Self", "\u534f\u4f1a\u5178\u7c4d\u9986\u6700\u91cc\u9762\u90a3\u4e00\u683c\u3002\u90a3\u683c\u5b50\u91cc\u7684\u4e66\uff0c\u5168\u57ce\u53ea\u6709\u4e00\u4e2a\u4eba\u80fd\u62ff\u51fa\u6765\u3002"),
        ("\u5f52", "\u2026\u2026"),
        ("Self", "\u53bb\u5427\u3002\u522b\u5728\u6211\u8fd9\u513f\u628a\u81ea\u5df1\u5750\u6210\u4e00\u5757\u77f3\u5934\u3002"),
    ], quests=["tnc:main/s1_self"]),

    D("self", 3, [
        ("Self", "\u56de\u6765\u4e86\u3002"),
        ("\u5f52", "\u55ef\u3002"),
        ("Self", "\u4f60\u90a3\u676f\u9152\u6211\u8fd8\u7559\u7740 \u2014\u2014 \u73b0\u5728\u662f\u4e24\u676f\u4e86\u3002"),
        ("\u5f52", "\u4e00\u676f\u4ed6\u7684\u3002"),
        ("Self", "\u4f60\u4eec\u8fd9\u4e00\u8d9f\u8d70\u4e86\u591a\u5c11\u5e74\uff1f"),
        ("\u5f52", "\u4e09\u5e74\u3002"),
        ("Self", "\u4e09\u5e74\u3002\u90a3\u676f\u9152\u5374\u6ca1\u53d8\u5473 \u2014\u2014 \u6211\u5929\u5929\u6362\u3002"),
        ("Self", "\u884c\u4e86\u3002\u627e\u5230\u4ed6\u5c31\u56de\u6765\u5750\u5750\u3002\u6211\u7ed9\u4f60\u7559\u7740\u4f4d\u7f6e\u3002"),
    ], requires=["tnc:main/s2_wall"], excludes=["tnc:main/s2_mountain"]),

    # ---------------- 庄鹊让 ----------------
    D("zhuangquerang", 0, [
        ("\u5e84\u9e4a\u8ba9", "\u5f52\u3002\u4f60\u5f88\u5c11\u6765\u8fd9\u513f\uff0c\u4eca\u5929\u600e\u4e48\u2026\u2026"),
        ("\u5f52", "\u884d\u8fd9\u534a\u4e2a\u6708\uff0c\u5728\u4f60\u8fd9\u513f\u501f\u8fc7\u4ec0\u4e48\u3002"),
        ("", "\uff08\u5979\u7684\u624b\u505c\u5728\u540d\u518c\u4e0a\uff0c\u6ca1\u6709\u62ac\u5934\u3002\uff09"),
        ("\u5e84\u9e4a\u8ba9", "\u2026\u2026\u4f60\u4e5f\u6765\u95ee\u8fd9\u4e2a\u3002\u6ca1\u6709\u4eba\u95ee\u8fc7\u6211\uff0c\u662f\u4ed6\u81ea\u5df1\u6765\u95ee\u7684 \u2014\u2014 \u4ed6\u8d70\u7684\u524d\u4e00\u665a\u5c31\u5750\u5728\u4f60\u73b0\u5728\u7ad9\u7684\u4f4d\u7f6e\uff0c\u95ee\u6211\u501f\u4e00\u672c\u4e0d\u5916\u501f\u7684\u4e66\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u6211\u501f\u4e86\uff0c\u7b2c\u4e8c\u5929\u4ed6\u5c31\u8d70\u4e86\u3002\u6240\u4ee5\u4f60\u73b0\u5728\u6765\u95ee\u300c\u4ed6\u501f\u8fc7\u4ec0\u4e48\u300d\uff0c\u6211\u4e00\u70b9\u90fd\u4e0d\u610f\u5916\uff1b\u6211\u610f\u5916\u7684\u662f\uff0c\u6765\u7684\u4eba\u662f\u4f60\u3002"),
        ("\u5f52", "\u90a3\u672c\u4e66\u91cc\u5199\u4e86\u4ec0\u4e48\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u90a3\u4e00\u9875\u4e0d\u5728\u4e86\u3002\u4e66\u5323\u8fd8\u5728\uff0c\u767b\u8bb0\u8fd8\u5728\uff0c\u4e66\u672c\u8eab\u4e5f\u8fd8\u5728 \u2014\u2014 \u4f46\u88ab\u4eba\u62bd\u6389\u4e86\u51e0\u9875\uff0c\u62bd\u5f97\u5f88\u5e72\u51c0\uff0c\u770b\u4e0d\u51fa\u662f\u54ea\u4e00\u5e74\u52a8\u7684\u624b\u3002"),
        ("", "\uff08\u5979\u628a\u4e00\u672c\u8584\u8584\u7684\u767b\u8bb0\u7c3f\u63a8\u8fc7\u6765\uff0c\u7ffb\u5230\u6700\u540e\u4e00\u9875\u3002\uff09"),
        ("\u5e84\u9e4a\u8ba9", "\u6700\u540e\u4e00\u4e2a\u501f\u5b83\u7684\u4eba\u662f\u4ed6\uff0c\u65e5\u671f\u662f\u4ed6\u8d70\u7684\u524d\u4e00\u5929\u3002\u8fd9\u4e00\u884c\u662f\u6211\u4eb2\u624b\u5199\u7684\u3002"),
        ("\u5f52", "\u4f60\u5199\u7684\u65f6\u5019\uff0c\u77e5\u9053\u4ed6\u8981\u8d70\u5417\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u4e0d\u77e5\u9053\u3002\u4ed6\u6c42\u6211\u7684\u65f6\u5019\u6211\u72b9\u8c6b\u4e86\u4e00\u4e0b \u2014\u2014 \u6211\u5f53\u65f6\u60f3\u7684\u662f\u300c\u8fd9\u4e0d\u5408\u89c4\u77e9\u300d\uff0c\u5c31\u8fd9\u4e48\u4e00\u77ac\u95f4\uff0c\u7136\u540e\u6211\u628a\u4e66\u7ed9\u4ed6\u4e86\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u6211\u8bb0\u4e86\u5341\u4e00\u5e74\u7684\u7c3f\u5b50\uff0c\u54ea\u5929\u8c01\u501f\u4e86\u4ec0\u4e48\u90fd\u5728\u4e0a\u9762\uff0c\u53ef\u90a3\u5929\u6211\u7b2c\u4e00\u4e2a\u5ff5\u5934\u4e0d\u662f\u300c\u5e2e\u4ed6\u300d\uff0c\u662f\u300c\u8fd9\u4e0d\u5408\u89c4\u77e9\u300d\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u5f52\uff0c\u6211\u4e0d\u60f3\u8fd9\u8f88\u5b50\u53ea\u6709\u90a3\u4e00\u4e2a\u5ff5\u5934\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u4f60\u8981\u627e\u7684\u4e66\uff0c\u6211\u80fd\u5e2e\u4f60\u627e\u3002\u4f46\u4f60\u5f97\u5e26\u6211\u8d70\u3002"),
    # ⚠️ **故意不设 @requires**：这是她唯一"随时能说"的一段，
    #    也是任务链的真正入口（s1_guild 由它完成、s1_guild 的 next 接着放 s1_zhuang）。
    #    DialoguePicker 取"序号最大且门槛通过"的一段；若她名下所有段都有门槛，
    #    玩家在门槛满足前右键她就会**什么都不播** ✗ ——
    #    tools/check_tnc_quest_chain.py 会专门拦这条。
    ], quests=["tnc:main/s1_guild", "tnc:main/s1_zhuang"]),

    # 让 · 离开主城之后（第二段开场：潮进城的那个晚上）
    #
    # ★ 分段原则（踩过坑，见 tools/check_tnc_dialogue_segments.py）：
    #   选段规则是"序号最大且门槛通过者胜" ⇒ **后一段的门槛必须比前一段严，或者
    #   用 @excludes 把前一段排掉**，否则前一段永远轮不到（= 写了但永远播不到 ✗）。
    #   这里：_02 要求 s1_shield（拿完盾回到让这里），完成 s1_leave 并放行 s2_bell；
    #        _03 要求 s2_bell（潮已经打过）才接管 —— 两段各有各的窗口 ✓。
    D("zhuangquerang", 2, [
        ("\u5e84\u9e4a\u8ba9", "\u540d\u518c\u90a3\u4ef6\u4e8b\uff0c\u6211\u60f3\u4e86\u5f88\u4e45\u3002"),
        ("\u5f52", "\u55ef\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u4ed6\u5728\u67e5\u4eba \u2014\u2014 \u53ef\u4ed6\u4e5f\u5728\u67e5\u4ed6\u81ea\u5df1\u3002\u540d\u518c\u4e0a\u6709\u4ed6\u7684\u540d\u5b57\uff0c\u65e5\u671f\u662f\u5341\u4e09\u5e74\u524d\u3002"),
        ("\u5f52", "\u5341\u4e09\u5e74\u524d\u4ed6\u8fd8\u6ca1\u6765\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u5bf9\u3002\u6240\u4ee5\u4ed6\u5728\u67e5\u7684\u4e0d\u662f\u81ea\u5df1 \u2014\u2014 \u662f\u540c\u4e00\u5929\u5165\u518c\u7684\u53e6\u4e00\u4e2a\u4eba\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u5f52\u3002\u6211\u4eec\u51fa\u53d1\u5427\u3002\u518d\u665a\uff0c\u8fde\u8ffd\u7684\u65b9\u5411\u90fd\u6ca1\u4e86\u3002"),
        ("\u5f52", "\u8d70\u3002"),
        ("", "\uff08\u57ce\u95e8\u3002\u6668\u5149\u3002\u4e09\u5339\u9a6c\u51fa\u57ce\u3002\uff09"),
    ], requires=["tnc:main/s1_shield"], excludes=["tnc:main/s2_bell"],
       quests=["tnc:main/s1_leave"], activate="tnc:main/s2_bell"),

    # 让 · 墙上的那道痕（第二段第三场）—— 要求 s2_bell（潮已经打过）才轮到她
    D("zhuangquerang", 3, [
        ("\u5e84\u9e4a\u8ba9", "\u5f52\uff0c\u4f60\u8fc7\u6765\u770b\uff0c\u4e1c\u5899\u90a3\u4e2a\u4e0d\u5bf9\u52b2\u3002"),
        ("", "\uff08\u6b8b\u5899\u4e0a\u4e00\u9053\u7126\u75d5\uff1a\u7b14\u76f4\u3001\u5f88\u6df1\u3001\u8fb9\u7f18\u53d1\u767d\u3002\uff09"),
        ("\u5e84\u9e4a\u8ba9", "\u8fd9\u4e0d\u662f\u6f6e\u7559\u4e0b\u7684\u3002\u6f6e\u53ea\u4f1a\u538b\u5899\u3001\u7838\u5899\uff0c\u4ece\u6765\u4e0d\u70e7\u5899\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u800c\u4e14\u4f60\u770b\u8fd9\u4e2a\u8d70\u5411 \u2014\u2014 \u5b83\u662f\u4ece\u5916\u9762\u6253\u8fdb\u6765\u7684\u3002\u6f6e\u662f\u4ece\u57ce\u91cc\u5f80\u5916\u62c6\uff0c\u8fd9\u9053\u75d5\u662f\u4ece\u57ce\u5916\u5f80\u57ce\u91cc\u6253\u3002"),
        ("\u5f52", "\u96f7\u6cd5\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u96f7\uff1f\u90a3\u6279\u4e1c\u897f\u4e0d\u4f1a\u7528\u96f7\uff0c\u6211\u6253\u4e86\u534a\u5bbf\u6ca1\u89c1\u4e00\u9053\u7535\u3002"),
        ("\u5f52", "\u4e0d\u662f\u5b83\u4eec\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u2026\u2026\u90a3\u662f\u8c01\u3002"),
        ("\u5f52", "\u5899\u5916\u3002\u6709\u4eba\u5728\u5f80\u56de\u6253\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u5f52\uff0c\u4f60\u8ba4\u5f97\u8fd9\u4e2a\u624b\u6cd5\u3002\u4f60\u6478\u90a3\u9053\u75d5\u7684\u65f6\u5019\u624b\u505c\u4f4f\u4e86\uff0c\u4f60\u5e73\u65f6\u4e0d\u8fd9\u6837\u3002"),
        ("\u5f52", "\u8ba4\u5f97\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u2026\u2026\u662f\u4ed6\u3002"),
        ("\u5f52", "\u55ef\u3002"),
        ("\u5f52", "\u53bb\u95ee\u4ed6\u3002"),
    ], requires=["tnc:main/s2_bell"], excludes=["tnc:main/s2_mountain"],
       quests=["tnc:main/s2_wall"]),

    # ---------------- 铁匠 cava（槐的父亲）----------------
    D("cava", 0, [
        ("\u94c1\u5320", "\u4f60\u8981\u51fa\u95e8\u3002"),
        ("\u5f52", "\u55ef\u3002\u53bb\u627e\u884d\u3002"),
        ("\u94c1\u5320", "\u51e0\u4e2a\u4eba\u3002"),
        ("\u5f52", "\u4e24\u4e2a\u3002\u6211\uff0c\u8fd8\u6709\u4e00\u4e2a\u7ba1\u4e66\u7684\u3002"),
        ("\u94c1\u5320", "\u5979\u80fd\u8ddf\u4f60\u8d70\uff1f"),
        ("\u5f52", "\u5979\u8bf4\u5979\u5f97\u8ddf\u7740\u3002"),
        ("\u94c1\u5320", "\u2026\u2026\u884c\u3002\u90a3\u5c31\u4e0d\u5dee\u4e00\u4e2a\u4e86\u3002"),
        ("", "\uff08\u4ed6\u5f80\u7827\u4e0a\u4e00\u6572\uff0c\u626d\u5934\u671d\u540e\u5c4b\u558a\u3002\uff09"),
        ("\u94c1\u5320", "\u69d0\uff01"),
    ], quests=["tnc:main/s1_zhuang", "tnc:main/s1_shield"], activate="tnc:main/s1_shield"),

    # ---------------- 熙永槐 ----------------
    D("huai", 0, [
        ("", "\uff08\u540e\u5c4b\u4e00\u9635\u4e1c\u897f\u5012\u4e0b\u7684\u58f0\u54cd\u3002\u69d0\u8dd1\u51fa\u6765\uff0c\u624b\u8fd8\u5728\u56f4\u88d9\u4e0a\u64e6\u3002\uff09"),
        ("\u69d0", "\u548b\u4e86\u7239\u3002"),
        ("\u94c1\u5320", "\u8ddf\u4ed6\u8d70\u3002\u4ed6\u53bb\u627e\u4eba\uff0c\u4f60\u8ddf\u7740\u53bb\u5386\u7ec3\u5386\u7ec3\u3002"),
        ("\u5f52", "\u4e0d\u7528\u3002\u8fd9\u662f\u6211\u81ea\u5df1\u7684\u4e8b\uff0c\u8def\u4e0a\u4e0d\u4e00\u5b9a\u5b89\u7a33\u3002"),
        ("\u94c1\u5320", "\u6211\u77e5\u9053\u662f\u4f60\u81ea\u5df1\u7684\u4e8b\u3002\u4ed6\u5341\u4e5d\u4e86\uff0c\u8fd8\u6ca1\u51fa\u8fc7\u57ce\uff0c\u5929\u5929\u5728\u8fd9\u7089\u5b50\u8fb9\u4e0a\u6572\u94c1\uff0c\u6572\u5f97\u6bd4\u4ed6\u7239\u8fd8\u50cf\u4ed6\u7239\u3002"),
        ("\u94c1\u5320", "\u522b\u4eba\u5bb6\u7684\u5b69\u5b50\u90fd\u5728\u5916\u9762\u8d70\uff0c\u6211\u4e5f\u60f3\u8ba9\u4ed6\u8d70\u4e00\u8d9f \u2014\u2014 \u4f60\u8981\u662f\u5acc\u4ed6\u62d6\u7d2f\uff0c\u8ba9\u4ed6\u81ea\u5df1\u8d70\u4e5f\u884c\uff0c\u53cd\u6b63\u522b\u7559\u5728\u8fd9\u5c4b\u91cc\u3002"),
        ("\u69d0", "\u90a3\u2026\u2026\u6211\u6536\u62fe\u4e00\u4e0b\uff1f\u5c31\u4e00\u4f1a\u513f\uff0c\u6211\u628a\u624b\u4e0a\u8fd9\u5757\u6599\u6536\u8fdb\u7089\u5b50\u91cc\u3002"),
        ("\u94c1\u5320", "\u5c11\u5e9f\u8bdd\u3002"),
        ("\u69d0", "\u884c\uff0c\u90a3\u6211\u8ddf\u4f60\u53bb\u3002\u627e\u5230\u4eba\u6211\u5c31\u56de\u6765\uff0c\u56de\u6765\u8ddf\u8001\u7239\u4ea4\u5dee\u3002"),
        ("", "\uff08\u94c1\u5320\u4ece\u67b6\u5b50\u4e0a\u53d6\u4e0b\u4e00\u4ef6\u4e1c\u897f\uff0c\u585e\u7ed9\u69d0 \u2014\u2014 \u4e00\u9762\u76fe\uff0c\u68f1\u89d2\u8fd8\u662f\u751f\u7684\u3002\uff09"),
        ("\u69d0", "\u7239\uff0c\u8fd9\u4e2a\u8fd8\u6ca1\u6253\u5b8c\u5462\u3002"),
        ("\u94c1\u5320", "\u6253\u5b8c\u4f60\u5c31\u8be5\u56de\u6765\u4e86\u3002\u8def\u4e0a\u522b\u5acc\u5b83\u6c89\uff0c\u4e5f\u522b\u628a\u5b83\u4e22\u4e86 \u2014\u2014 \u4f60\u5e26\u7740\u5b83\u51fa\u95e8\uff0c\u5c31\u5e26\u7740\u5b83\u8fdb\u95e8\u3002"),
        ("\u69d0", "\u77e5\u9053\u4e86\u77e5\u9053\u4e86\u3002"),
        ("\u94c1\u5320", "\u4ed6\u8981\u662f\u56de\u6765\u7684\u65f6\u5019\u7626\u4e86\uff0c\u6211\u627e\u4f60\u7b97\u8d26\u3002"),
    ], quests=["tnc:main/s1_shield"], activate="tnc:main/s1_leave"),

    # ---------------- 周坐望 ----------------
    D("zuowang", 0, [
        ("", "\uff08\u5c4b\u91cc\u6ca1\u6709\u70b9\u706f\u3002\u4ed6\u5c31\u5750\u5728\u95e8\u8fb9\uff0c\u770b\u7740\u9662\u5b50\u3002\uff09"),
        ("\u5468\u5750\u671b", "\u706f\u4e0d\u7528\u70b9\u3002\u4f60\u4eec\u8fdb\u6765\u7684\u65f6\u5019\uff0c\u8eab\u4e0a\u5e26\u5149\u3002"),
        ("\u5f52", "\u4f60\u8ba4\u8bc6\u6211\u3002"),
        ("\u5468\u5750\u671b", "\u4e0d\u8ba4\u8bc6\u3002\u8ba4\u8bc6\u4f60\u7684\u5149\u3002"),
        ("\u5468\u5750\u671b", "\u4f60\u4eec\u8d70\u4e86\u4e09\u5e74\u624d\u627e\u5230\u6211\uff0c\u6bd4\u6211\u7b97\u7684\u6162\u4e86\u534a\u5e74\u3002\u4e0d\u8fc7\u4e5f\u4e0d\u7b97\u8fdf\uff0c\u8def\u4e0a\u90a3\u51e0\u5ea7\u57ce\u4f60\u4eec\u6e05\u5f97\u8fd8\u7b97\u5e72\u51c0\u3002"),
        ("\u5f52", "\u4f60\u7b97\u8fc7\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u4e0d\u7b97\uff0c\u6211\u770b\u3002\u4f60\u4eec\u6765\u7684\u8def\u4e0a\u6b7b\u4e86\u51e0\u4e2a\u4eba\u3001\u5728\u54ea\u6761\u6cb3\u8fb9\u6b47\u8fc7\u3001\u4f60\u90a3\u9762\u76fe\u4e0a\u78d5\u4e86\u51e0\u9053\u53e3\u5b50\uff0c\u6211\u90fd\u770b\u5f97\u51fa\u6765 \u2014\u2014 \u4f46\u6211\u770b\u4e0d\u51fa\u6765\u4f60\u4eec\u4e3a\u4ec0\u4e48\u8981\u6765\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u524d\u8f88\uff0c\u6211\u4eec\u662f\u4e3a\u884d\u6765\u7684\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u77e5\u9053\u3002\u4f60\u4eec\u628a\u4e1c\u5899\u90a3\u9053\u75d5\u770b\u8fc7\u4e86\u5427\u3002\u90a3\u9053\u75d5\u662f\u6211\u5148\u8ba4\u51fa\u6765\u7684 \u2014\u2014 \u4e09\u5e74\u524d\u3002"),
        ("\u69d0", "\u4f60\u77e5\u9053\u4e09\u5e74\u4e86\uff1f\u4f60\u77e5\u9053\u4ed6\u5728\u5916\u9762\u62c6\u57ce\u3001\u4f60\u5728\u5c71\u4e0a\u5750\u7740\uff1f\u8001\u5148\u751f\uff0c\u4f60\u8fd9\u662f\u4ec0\u4e48\u9053\u7406\u3002"),
        ("\u5468\u5750\u671b", "\u8bf4\u4e86\uff0c\u4f60\u4eec\u4e5f\u4e0d\u6539\u3002\u6211\u8fd9\u4e00\u8f88\u5b50\u8bf4\u8fc7\u5f88\u591a\u6b21\uff0c\u8bf4\u5b8c\u4ed6\u4eec\u7167\u65e7\u8fc7\u65e5\u5b50\uff0c\u8be5\u6765\u7684\u8fd8\u662f\u6765\u3002"),
        ("\u5468\u5750\u671b", "\u6240\u4ee5\u540e\u6765\u6211\u5b66\u4f1a\u4e86\u5148\u770b\uff0c\u7b49\u4e00\u4ef6\u4e8b\u81ea\u5df1\u8d70\u5230\u5fc5\u987b\u89e3\u51b3\u7684\u65f6\u5019\u518d\u5f00\u53e3 \u2014\u2014 \u90a3\u6837\u81f3\u5c11\u6709\u4eba\u80af\u542c\u3002"),
        ("\u5f52", "\u4ec0\u4e48\u95e8\u3002"),
        ("\u5468\u5750\u671b", "\u9ed1\u6697\u6df1\u5904\u7684\u95e8\u3002\u4f60\u4eec\u8981\u627e\u7684\u4eba\uff0c\u5c31\u5728\u90a3\u6247\u95e8\u540e\u9762\u3002"),
        ("\u5f52", "\u4f60\u8fdb\u53bb\u8fc7\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u9001\u4ed6\u8fdb\u53bb\u7684\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u524d\u8f88\uff0c\u90a3\u60a8\u600e\u4e48\u4e0d\u81ea\u5df1\u53bb\u3002"),
        ("\u5468\u5750\u671b", "\u56e0\u4e3a\u6211\u4e00\u76f4\u5728\u7b49\u4e00\u4e2a\u7406\u7531\u3002\u4ed6\u4e0d\u7528\u7b49\uff0c\u4ed6\u6709\u7406\u7531\u3002\u6211\u8fd9\u8f88\u5b50\u4ec0\u4e48\u90fd\u770b\u5f97\u89c1\uff0c\u5c31\u662f\u4e00\u76f4\u6ca1\u627e\u5230\u90a3\u4e2a\u7406\u7531\u3002"),
        ("\u5f52", "\u6211\u53bb\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u77e5\u9053\u3002\u4f60\u6478\u5230\u90a3\u9053\u75d5\u7684\u90a3\u5929\u8d77\uff0c\u4f60\u5c31\u5df2\u7ecf\u51b3\u5b9a\u8981\u53bb\u4e86 \u2014\u2014 \u6240\u4ee5\u6211\u8ddf\u4f60\u4eec\u53bb\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u524d\u8f88\uff0c\u60a8\u521a\u624d\u8fd8\u8bf4\u300c\u8bf4\u4e86\u4f60\u4eec\u4e5f\u4e0d\u6539\u300d\u3002"),
        ("\u5468\u5750\u671b", "\u90a3\u53e5\u8bdd\u662f\u5bf9\u522b\u4eba\u8bf4\u7684\u3002\u8fd9\u6b21\u4e0d\u4e00\u6837\uff0c\u8fd9\u6b21\u662f\u6211\u81ea\u5df1\u8981\u4e0b\u53bb\u3002\u6211\u5728\u5c71\u4e0a\u5750\u4e86\u8fd9\u4e48\u591a\u5e74\uff0c\u603b\u5f97\u6709\u4e00\u6b21\u4e0d\u662f\u5750\u7740\u770b\u3002"),
    # ★ 第一段必须给自己上闸，否则会被后面的段永久遮住（第六次踩坑）：
    #   选段规则是"序号最大且门槛通过的那一段"。_02 早先**没有 @requires**，
    #   于是它从第一秒起就永远压过这一段 ⇒ 山上初遇这段从来没播过
    #   （tools/check_tnc_dialogue_segments.py 报 SHADOWED）。
    #   ⇒ 本段完成 s2_mountain，就用 @excludes 同一个任务把自己退场，
    #      让 _02 用 @requires=s2_mountain 接上。两段窗口首尾相接、不重叠 ✓。
    ], excludes=["tnc:main/s2_mountain"], quests=["tnc:main/s2_mountain"]),

    # ★★ 分段原则（第五次踩坑后总结，照做）：
    #   后一段的 @requires 必须是**前一段完成过的任务** ✓，
    #   绝不能写成"后一段自己要完成的任务" —— 那是死锁，永远轮不到 ✗。
    #   本轮就栽在这：zuowang_02 原写 requires=s2_see，而 s2_see 正是它自己完成的
    #   ⇒ 永远落选、永远回落第一段（用户实测「看痕任务无法完成」）。
    #
    #   时间线（照剧情）：山上初遇 → 去看痕 → 回来报告 → 他带你走
    #   ⇒ _00 接取 s2_see；_02 回来报告（**完成** s2_see）并放行 s2_door；
    #      _03 门前（完成 s2_door＋第三段三环）并放行 s4_hand；_04 裂口前；_05 终章。
    D("zuowang", 2, [
        ("\u5468\u5750\u671b", "\u4f60\u770b\u6e05\u695a\u4e86\u3002"),
        ("\u5f52", "\u55ef\u3002"),
        ("\u5468\u5750\u671b", "\u90a3\u662f\u4ec0\u4e48\u3002"),
        ("\u5f52", "\u6709\u4eba\u4ece\u91cc\u9762\u5212\u51fa\u6765\u7684\u3002"),
        ("\u5468\u5750\u671b", "\u2026\u2026\u4f60\u6bd4\u4ed6\u4eec\u80af\u770b\u3002"),
        ("\u5468\u5750\u671b", "\u4e09\u5e74\u524d\u6211\u5c31\u8ba4\u51fa\u90a3\u662f\u4ed6\u7684\u624b\u6cd5\u3002\u6211\u6ca1\u8bf4\uff0c\u4e5f\u6ca1\u52a8\u3002\u8fd9\u4e09\u5e74\u6211\u4e00\u76f4\u5750\u5728\u8fd9\u5f20\u6905\u5b50\u4e0a\uff0c\u7b49\u6709\u4eba\u6765\u95ee\u3002\u4f60\u4eec\u662f\u7b2c\u4e00\u4e2a\u3002"),
        ("\u5468\u5750\u671b", "\u73b0\u5728\u662f\u90a3\u4ef6\u4e8b\u8d70\u5230\u5fc5\u987b\u89e3\u51b3\u7684\u65f6\u5019\u4e86\u3002\u6f6e\u8fdb\u57ce\u4e86 \u2014\u2014 \u8bf4\u660e\u90a3\u6247\u95e8\u677e\u4e86\u3002"),
        ("\u5468\u5750\u671b", "\u8d70\u5427\u3002\u6211\u5e26\u4f60\u4eec\u53bb\u90a3\u9053\u7f1d\u524d\u9762\u3002"),
    ], requires=["tnc:main/s2_mountain"], excludes=["tnc:main/s3_stone"],
       quests=["tnc:main/s2_see"],
       activate="tnc:main/s2_door"),

    # 第四段 · 门前：他去歇那一下（s4_hand 在这里完成）
    #
    # ★ 分段原则：后一段的门槛取"前一段完成过的任务"里**最后一个**，
    #   并且前一段用 @excludes 同一个任务 —— 两段的窗口正好首尾相接、不重叠 ✓。
    #   （早先这里写成"后一段要求它自己完成的任务"，那一段就永远轮不到；
    #     tools/check_tnc_dialogue_segments.py 会把它报成 SHADOWED。）
    D("zuowang", 3, [
        ("\u5468\u5750\u671b", "\u5230\u4e86\u3002\u5c31\u662f\u8fd9\u513f\u3002"),
        ("", "\uff08\u4e00\u9053\u51e0\u4e4e\u6ca1\u6709\u539a\u5ea6\u7684\u7f1d\u3002\u91cc\u9762\u4ec0\u4e48\u90fd\u6ca1\u6709\u3002\uff09"),
        ("\u5468\u5750\u671b", "\u8fd9\u9053\u53e3\u5b50\u4e0d\u8ba4\u5c5e\u6027\uff0c\u53ea\u8ba4\u2018\u7167\u2019\u3002\u6211\u62ff\u81ea\u5df1\u7167\u4e86\u4e09\u5e74\uff0c\u591f\u5f00\u4e00\u6b21\u3002"),
        ("\u5f52", "\u5f00\u5b8c\u4f60\u4f1a\u600e\u4e48\u6837\u3002"),
        ("\u5468\u5750\u671b", "\u80fd\u52a8\u3002\u53ea\u662f\u5f97\u6b47\u4e00\u9635\u3002"),
        ("\u5468\u5750\u671b", "\u4f60\u4eec\u5148\u8fdb\u53bb\u3002\u6211\u8ddf\u4e0a\u3002"),
        ("\u5e84\u9e4a\u8ba9", "\u524d\u8f88\u2014\u2014"),
        ("\u5468\u5750\u671b", "\u6211\u4e0d\u7b97\uff0c\u6211\u770b\u3002\u770b\u4e86\u4e00\u8f88\u5b50\u4e86\u3002"),
        ("\u5468\u5750\u671b", "\u8fd9\u4e00\u6b21\u6211\u8ddf\u4f60\u4eec\u4e00\u8d77\u8fdb\u53bb\u3002"),
    ], requires=["tnc:main/s2_see"], excludes=["tnc:main/s4_ge"],
       quests=["tnc:main/s2_door", "tnc:main/s3_gate",
               "tnc:main/s3_him", "tnc:main/s3_account", "tnc:main/s3_stone"],
       activate="tnc:main/s4_hand"),

    # 第四段 · 裂口前：只有望还在（他跟不上，但这一句得有人接）
    #
    # ⚠️ 时间间隙里直面 Chiller Lynn 的那两环，暂时由"跟望的这一段"收尾 ——
    #    因为她还没有可对话的实体。要做得完整，得给她建个 NPC
    #    （照周坐望那八处接线走一遍）。这一段因此写成"望着你走向裂口"，
    #    台词不冒充 Lynn 说任何话 ✓。
    D("zuowang", 4, [
        ("\u5468\u5750\u671b", "\u4f60\u8981\u53bb\u4e86\u3002"),
        ("\u5f52", "\u55ef\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u8ddf\u4e0d\u4e0a\u3002\u90a3\u9053\u53e3\u5b50\u53ea\u8ba4\u4e00\u4e2a\u4eba\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u5c31\u5728\u8fd9\u513f\u770b\u7740\u3002\u770b\u4e86\u4e00\u8f88\u5b50\u4e86\uff0c\u4e0d\u5dee\u8fd9\u4e00\u4f1a\u513f\u3002"),
        ("\u5f52", "\u2026\u2026"),
        ("\u5468\u5750\u671b", "\u53bb\u5427\u3002\u522b\u56de\u5934\u3002"),
    ], requires=["tnc:main/s3_stone"], excludes=["tnc:main/s4_ge"],
       quests=["tnc:main/s4_hand", "tnc:main/s4_lynn", "tnc:main/s4_ge"],
       activate="tnc:main/s4_stones"),

    # ---------------- Chiller Lynn（时间间隙）----------------
    # 她没有 NPC 实体，所以 s4_lynn / s4_ge 这两环只能靠"跟望对话"一并收尾 ——
    # 台词也**故意不冒充 Lynn 说话**（那会显得像 bug）。
    # ⚠️ 已知缺口：要做得完整，得给 Lynn 做一个 NPC 实体（照周坐望那八处接线走一遍）。

    # 终章之后：周坐望在酒馆门口（第四段第六场 · s4_stones 在这里完成）
    D("zuowang", 5, [
        ("", "\uff08\u9152\u9986\u95e8\u53e3\u90a3\u5f20\u65e7\u6905\u5b50\u4e0a\u5750\u7740\u4e00\u4e2a\u4eba\u3002\u817f\u4e0a\u644a\u7740\u4e00\u5757\u5e03\uff0c\u5e03\u4e0a\u6392\u7740\u51e0\u5757\u77f3\u5934\u3002\uff09"),
        ("Self", "\u2026\u2026\u8001\u5148\u751f\uff1f\u60a8\u5750\u8fd9\u513f\u4e00\u65e9\u4e0a\u4e86\u3002\u7b49\u4eba\uff1f"),
        ("\u5468\u5750\u671b", "\u2026\u2026\u4e0d\u7b97\u7b49\u4eba\u3002"),
        ("Self", "\u90a3\u7b49\u4ec0\u4e48\u3002"),
        ("\u5468\u5750\u671b", "\u7b49\u4e00\u4ef6\u6211\u4e0d\u786e\u5b9a\u6709\u6ca1\u6709\u53d1\u751f\u8fc7\u7684\u4e8b\u3002"),
        ("Self", "\u54df\uff0c\u60a8\u8fd9\u51e0\u5757\u77f3\u5934\uff0c\u6210\u8272\u5404\u4e0d\u4e00\u6837\u554a\u3002\u54ea\u513f\u6765\u7684\uff1f"),
        ("\u5468\u5750\u671b", "\u6361\u7684\u3002"),
        ("Self", "\u6361\u8fd9\u4e48\u591a\uff1f"),
        ("\u5468\u5750\u671b", "\u2026\u2026\u4e00\u4e2a\u4e00\u4e2a\u6361\u7684\u3002"),
        ("", "\uff08\u4ed6\u628a\u5e03\u6536\u8d77\u6765\uff0c\u4e00\u5757\u4e00\u5757\u6536\u8fdb\u6000\u91cc\u3002\u77f3\u5934\u662f\u70ed\u7684 \u2014\u2014 \u6309\u9053\u7406\u4e0d\u8be5\u662f\u70ed\u7684\u3002\uff09"),
        ("Self", "\u90a3\u51e0\u5757\u77f3\u5934\u7684\u4e3b\u4eba\u5462\uff1f"),
        ("\u5468\u5750\u671b", "\u90fd\u4e0d\u5728\u4e86\u3002"),
        ("Self", "\u53ef\u60dc\u3002"),
        ("\u5468\u5750\u671b", "\u4e0d\u53ef\u60dc\u3002\u4ed6\u4eec\u505a\u6210\u4e86\u3002"),
        ("Self", "\u505a\u6210\u4ec0\u4e48\u4e86\uff1f"),
        ("\u5468\u5750\u671b", "\u2026\u2026\u6ca1\u4ec0\u4e48\u3002"),
        ("\u5468\u5750\u671b", "\u6211\u8bb0\u7740\u5c31\u884c\u3002"),
    ], requires=["tnc:main/s4_ge"], quests=["tnc:main/s4_stones"]),
]


def dump(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"

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


# 每环要找的那个人 —— 写进任务 JSON 的 `npc.entity_type`。
#
# 为什么写这个：
#   * 引擎用它把"任务 ↔ NPC"关联起来（作者自己的任务是靠它配的）；
#   * 我们的 HUD 标记（client/QuestMarkerHud）读它来决定**追踪时标记谁** ✓。
# 为什么**不**写坐标：天空岛/传送阵每存档位置不同，而 NPC 会走动 ——
#   坐标交给客户端每帧现查（见 QuestMarkerTarget），写死必错。
NPC_OF_QUEST = {
    "s1_self": ("tnc:self", "Self"),
    "s1_guild": ("tnc:zhuangquerang", "\u5e84\u9e4a\u8ba9"),
    "s1_zhuang": ("tnc:zhuangquerang", "\u5e84\u9e4a\u8ba9"),
    "s1_shield": ("tnc:huai", "\u69d0"),
    "s1_leave": ("tnc:zhuangquerang", "\u5e84\u9e4a\u8ba9"),
    "s2_bell": ("tnc:zhuangquerang", "\u5e84\u9e4a\u8ba9"),
    "s2_wall": ("tnc:zhuangquerang", "\u5e84\u9e4a\u8ba9"),
    "s2_mountain": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s2_see": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s2_door": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s3_gate": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s3_him": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s3_account": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s3_stone": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s4_hand": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s4_lynn": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s4_ge": ("tnc:zuowang", "\u5468\u5750\u671b"),
    "s4_stones": ("tnc:zuowang", "\u5468\u5750\u671b"),
}


def quest_json(row):
    qid, chapter, title, short, desc, objectives, rewards, nxt, icon = row
    out = {
        "id": "%s:main/%s" % (NS, qid),
        "enabled": True,
        "weight": 1,
        "category": CAT,
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
        "rewards": rewards,
        "next": ["%s:main/%s" % (NS, n) for n in nxt],
    }
    npc = NPC_OF_QUEST.get(qid)
    if npc:
        # ⚠️ entity_type 必须是**已注册**的实体类型，否则引擎解析时直接抛错丢任务 ✗
        #    （QuestDataManager 会 new JsonParseException("unknown entity type ...")）。
        out["npc"] = {"entity_type": npc[0], "display_name": npc[1]}
    return out


HEADER = """# ---------------------------------------------------------------------------
#  TN-C 对话剧本 —— {who} · 第 {seq} 段
#
#  这是**生成出来的**文件（tools/gen_tnc_story_chain.py）✗ 别手改：
#  改文案请改那个脚本里的台词表，然后重跑一次。
#
#  格式速查（详见 DialogueLoader 的类注释）：
#    @id <剧本id>        必须与文件名一致
#    @quest <任务id>     本段演完**完成**这条任务（可多行）
#    @activate <任务id>  本段演完**接取**这条任务
#    @requires <任务id>  **必须先完成**这条，本段才会被选中（选段见 DialoguePicker）
#    @excludes <任务id>  **完成**这条之后，本段退场
#    @act <动作名>       给紧接着的那一行挂动作
#    说话人|台词         说话人留空 = 旁白
# ---------------------------------------------------------------------------
"""


def dialogue_text(entry):
    who = entry["npc"]
    seq = entry["seq"]
    out = [HEADER.format(who=who, seq=("first" if seq == 0 else "%02d" % seq))]
    out.append("@id tnc:%s" % (who if seq == 0 else "%s_%02d" % (who, seq)))
    for r in entry["requires"]:
        out.append("@requires %s" % r)
    for e in entry["excludes"]:
        out.append("@excludes %s" % e)
    if entry["activate"]:
        out.append("@activate %s" % entry["activate"])
    for q in entry["quests"]:
        out.append("@quest %s" % q)
    out.append("")
    for speaker, text in entry["lines"]:
        out.append("%s|%s" % (speaker, text))
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    packs = [d for d in os.listdir(os.path.join(repo, "modpack"))
             if os.path.isdir(os.path.join(repo, "modpack", d, "kubejs"))]
    if not packs:
        print("ERROR: no pack folder with kubejs/ under modpack/")
        return 1

    changed = 0
    for pack in packs:
        root = os.path.join(repo, "modpack", pack, "kubejs", "data", NS, "whisperingquests")
        for cid, (title, desc, order) in CH.items():
            obj = {"id": "%s:chapters/%s" % (NS, cid), "category": CAT,
                   "title": title, "description": desc, "order": order}
            if write(os.path.join(root, "chapters", "%s.json" % cid), dump(obj), args.check):
                changed += 1
                print("%s chapters/%s.json" % ("would write" if args.check else "wrote", cid))
        for row in Q:
            name = row[0]
            if write(os.path.join(root, "tasks", CAT, "%s.json" % name),
                     dump(quest_json(row)), args.check):
                changed += 1
                print("%s tasks/%s/%s.json" % ("would write" if args.check else "wrote", CAT, name))

    # dialogues go into the mod jar (resources), not the pack
    dlg_root = os.path.join(repo, "src", "main", "resources", "data", NS, "dialogues")
    for entry in DIALOGUES:
        seq = entry["seq"]
        name = entry["npc"] if seq == 0 else "%s_%02d" % (entry["npc"], seq)
        if write(os.path.join(dlg_root, "%s.txt" % name), dialogue_text(entry), args.check):
            changed += 1
            print("%s dialogues/%s.txt" % ("would write" if args.check else "wrote", name))

    print("%d file(s) %s" % (changed, "differ" if args.check else "written"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
