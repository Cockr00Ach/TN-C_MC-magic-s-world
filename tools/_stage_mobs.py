# -*- coding: utf-8 -*-
"""Stage ONLY the dungeon-mob work.

lang/*.json and build.gradle also carry someone else's pending edits (ink / wind /
coins ...), so for those two we build a blob = HEAD + our lines only, and push that
blob into the index. The working tree is left untouched.
"""
import io
import json
import re
import subprocess
import sys

IDS = ["lantern_boar", "rust_hook_butcher", "moss_carapace_spider", "bone_crossbowman",
       "ash_flinger", "copper_fusilier", "sewer_crawler", "umbral_binder", "frost_weaver",
       "ash_monk", "steam_sentinel", "grave_grub", "goblin_skirmisher", "kobold_miner",
       "orc_brute", "minotaur_warden", "tentacle_horror"]
NEW = IDS[12:]


def git(*args, **kw):
    return subprocess.run(["git", *args], capture_output=True, check=True, **kw)


def head_text(path):
    return git("show", "HEAD:" + path).stdout.decode("utf-8")


def stage_blob(path, text):
    sha = git("hash-object", "-w", "--stdin", input=text.encode("utf-8")).stdout.decode().strip()
    git("update-index", "--add", "--cacheinfo", "100644,%s,%s" % (sha, path))
    print("  索引已更新(仅本次改动): %s" % path)


# ---------------- lang ----------------
for path in ("src/main/resources/assets/tnc/lang/zh_cn.json",
             "src/main/resources/assets/tnc/lang/en_us.json"):
    wt = io.open(path, encoding="utf-8").read().split("\n")
    ours = [l for l in wt if any(k in l for k in NEW)]
    assert len(ours) == 10, "%s 里我们的 key 应有 10 行，实际 %d" % (path, len(ours))
    hd = head_text(path).split("\n")
    close = max(i for i, l in enumerate(hd) if l.strip() == "}")
    last = close - 1
    while hd[last].strip() == "":
        last -= 1
    if not hd[last].rstrip().endswith(","):
        hd[last] = hd[last].rstrip() + ","
    merged = hd[:close] + ours + hd[close:]
    text = "\n".join(merged)
    json.loads(text)  # 必须仍然是合法 JSON
    stage_blob(path, text)

# ---------------- build.gradle ----------------
path = "build.gradle"
wt = io.open(path, encoding="utf-8").read().split("\n")
hd = head_text(path).split("\n")
hd_set = set(hd)

blocks = []
for start in [i for i, l in enumerate(wt) if "hasProperty('dungeonMobTests')" in l]:
    b = start
    while b - 1 >= 0 and wt[b - 1].strip().startswith("//"):
        b -= 1
    depth, e = 0, start
    for i in range(start, len(wt)):
        depth += wt[i].count("{") - wt[i].count("}")
        if depth == 0 and i > start:
            e = i
            break
    anchor = b - 1
    while anchor >= 0 and wt[anchor] not in hd_set:
        anchor -= 1
    assert anchor >= 0, "找不到 dungeon 块的锚点"
    blocks.append((wt[anchor], wt[b:e + 1]))

for anchor_line, block in sorted(blocks, key=lambda x: hd.index(x[0]), reverse=True):
    at = hd.index(anchor_line) + 1
    hd[at:at] = block
    print("  build.gradle: 插入 %d 行（锚点: %s）" % (len(block), anchor_line.strip()[:48]))
stage_blob(path, "\n".join(hd))

# ---------------- everything else ----------------
paths = [
    "src/main/java/com/tnc/tnc/dungeon",
    "src/test/java/com/tnc/tnc/dungeon",
    "src/main/resources/data/tncdungeon",
    "docs/dungeon",
    "docs/previews/mobs",
    "docs/previews/dungeon_mobs_preview.png",
    "docs/previews/dungeon_mobs_texture.png",
] + ["tools/%s.py" % t for t in ("gen_dungeon_mobs", "check_dungeon_mob_assets",
                                 "render_dungeon_mob_preview", "audit_dungeon_mob_rigs",
                                 "mob_ids", "apply_dungeon_mob_lists")]
for mid in IDS:
    paths += [
        "src/main/resources/assets/tnc/geo/entity/%s.geo.json" % mid,
        "src/main/resources/assets/tnc/animations/entity/%s.animation.json" % mid,
        "src/main/resources/assets/tnc/textures/entity/%s_bedrock.png" % mid,
        "src/main/resources/assets/tnc/models/item/%s_spawn_egg.json" % mid,
    ]
git("add", "--", *paths)
print("已 add %d 条路径" % len(paths))
