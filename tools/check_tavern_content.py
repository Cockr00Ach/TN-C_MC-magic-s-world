# -*- coding: utf-8 -*-
"""
check_tavern_content.py  --  where does the tavern content live, and is it in sync?

WHY
===
"Tavern content" is spread over four different mechanisms, and a gap in any one of
them looks like "the tavern was never updated":
  1. blocks / items / entities  -> src/main/... (shipped inside the mod jar)
  2. the questbook chapter      -> questbook/ftbquests/chapters/tnc_play_05_tavern.snbt
                                   (must be COPIED into the instance; FTB does not
                                    read the repo folder)
  3. whisperingquests tasks     -> modpack/<pack>/kubejs/data/ysjxmodel/...(mirrored)
  4. textures / lang            -> part of the jar

This script reports each of those, with the last git commit that touched them and
whether the instance copy matches. Read-only.

USAGE
=====
    python tools/check_tavern_content.py --live "<instance path>"
"""

import argparse
import hashlib
import io
import os
import subprocess
import sys
import zipfile

REPO_PACK = os.path.join("modpack", "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915")
JAR = os.path.join("build", "libs", "tnc-1.0.0.jar")
NEEDLE = ("tavern", "senluo")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def git_last_commit(repo, path):
    try:
        # NOTE: do not let Python pick the console codec (GBK here) -- git prints
        # UTF-8 commit subjects with Chinese in them and the reader thread dies.
        out = subprocess.run(["git", "log", "-1", "--format=%h %ad %s", "--date=short", "--", path],
                             cwd=repo, capture_output=True, text=True, timeout=60,
                             encoding="utf-8", errors="replace")
        return (out.stdout or "").strip() or "(not in git)"
    except Exception as exc:
        return "(git failed: %s)" % exc


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", required=True)
    args = ap.parse_args(argv[1:])
    repo = repo_root()
    live = args.live

    # ---- 1. source-side tavern files ----
    print("== 1. tavern source files in the repo (shipped inside the jar) ==")
    hits = []
    for base in ("src/main/java", "src/main/resources"):
        for dirpath, _d, files in os.walk(os.path.join(repo, base)):
            for fn in files:
                low = fn.lower()
                rel = os.path.relpath(os.path.join(dirpath, fn), repo).replace("\\", "/")
                if any(n in low for n in NEEDLE) or "tavern" in rel.lower():
                    hits.append(rel)
    for rel in sorted(hits):
        print("   %-72s %s" % (rel, git_last_commit(repo, rel)))
    print("   total: %d file(s)" % len(hits))

    # ---- 2. are they in the jar? ----
    print("")
    print("== 2. present in the built jar? ==")
    jar = os.path.join(repo, JAR)
    if os.path.isfile(jar):
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            tex = sorted(n for n in names if "tavern" in n.lower())
            for n in tex:
                print("   [ok] %s" % n)
            if not tex:
                print("   !! no tavern textures/models in the jar")
            lang = z.read("assets/tnc/lang/zh_cn.json").decode("utf-8-sig")
            keys = [k for k in ("tavern_table", "tavern_counter", "tavern_bottle", "tavern_lamp") if k in lang]
            print("   lang keys present: %s" % (keys if keys else "NONE"))
    else:
        print("   (no jar built yet)")

    # ---- 3. questbook chapter: repo vs instance ----
    print("")
    print("== 3. tavern quest chapter (repo vs instance) ==")
    rel = "questbook/ftbquests/chapters/tnc_play_05_tavern.snbt"
    src = os.path.join(repo, rel.replace("/", os.sep))
    dst = os.path.join(live, "config", "ftbquests", "quests", "chapters", "tnc_play_05_tavern.snbt")
    print("   repo     : %s" % ("exists, %d B" % os.path.getsize(src) if os.path.isfile(src) else "MISSING"))
    print("   instance : %s" % ("exists, %d B" % os.path.getsize(dst) if os.path.isfile(dst) else "MISSING"))
    if os.path.isfile(src) and os.path.isfile(dst):
        same = sha(src) == sha(dst)
        print("   identical: %s" % ("YES" if same else "NO  <-- instance copy is stale, redeploy"))
    print("   last commit: %s" % git_last_commit(repo, rel))

    # ---- 4. whisperingquests tasks ----
    print("")
    print("== 4. tavern whisperingquests tasks (repo mirror vs instance) ==")
    for sub in ("main", "side", "daily"):
        d_rel = "%s/kubejs/data/ysjxmodel/whisperingquests/tasks/%s" % (REPO_PACK, sub)
        d_repo = os.path.join(repo, d_rel.replace("/", os.sep))
        if not os.path.isdir(d_repo):
            continue
        for fn in sorted(os.listdir(d_repo)):
            if not any(n in fn.lower() for n in NEEDLE):
                continue
            a = os.path.join(d_repo, fn)
            b = os.path.join(live, "kubejs", "data", "ysjxmodel", "whisperingquests", "tasks", sub, fn)
            state = "MISSING in instance"
            if os.path.isfile(b):
                state = "identical" if sha(a) == sha(b) else "DIFFERS"
            print("   %-44s %s" % ("%s/%s" % (sub, fn), state))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
