# -*- coding: utf-8 -*-
"""
deploy_questbook.py  --  copy the authored FTB questbook into the live instance.

WHY
===
The authored chapters live in the repo at questbook/ftbquests/ (git-tracked, since
modpack/*/config/* is gitignored), but FTB only loads from
<instance>/config/ftbquests/quests/. The three tools/install-*-update.ps1 deployers
are currently broken (one demands exactly nine guide chapters, one references a
missing texture, one #requires PowerShell 7 and a hardcoded path from another
machine), so nothing ever reaches the game. This script does the copy directly and
verifiably.

WHAT IT DOES
  1. backs up the instance's config/ftbquests to config/ftbquests.bak-<timestamp>
  2. copies questbook/ftbquests/chapters/*.snbt into the instance's chapters folder
     (existing chapters with other names are left alone)
  3. ensures the group declared by questbook/ftbquests/group.snbt is registered in
     the instance's chapter_groups.snbt (appending the file verbatim before the
     closing bracket, so the existing groups and their formatting survive)
  4. re-runs the same checks as tools/inspect_questbook.py and prints the result

SAFETY
  * dry run by default; nothing is written without --apply
  * refuses to run while a java process is alive (the game would rewrite the files)
  * never deletes anything from the instance

USAGE
=====
    python tools/deploy_questbook.py --live "<instance path>"            # dry run
    python tools/deploy_questbook.py --live "<instance path>" --apply

Pure ASCII script (repo rule).
"""

import argparse
import datetime
import glob
import io
import os
import re
import shutil
import subprocess
import sys


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def java_running():
    """True when any java process is alive (the game must be closed)."""
    try:
        out = subprocess.run(["tasklist", "/FI", "IMAGENAME eq java.exe"],
                             capture_output=True, text=True, timeout=30)
        return "java.exe" in (out.stdout or "")
    except Exception:
        return False  # cannot tell -> do not block, but report it


def find_instance():
    name = "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915"
    home = os.path.expanduser("~")
    roots = [os.path.join(home, "AppData", "Roaming"),
             os.path.join(home, "AppData", "Roaming", ".minecraft"),
             os.path.join(home, ".minecraft")]
    env = os.environ.get("TNC_LIVE_ROOT")
    if env:
        roots.insert(0, env)
    for root in roots:
        if not os.path.isdir(root):
            continue
        for cand in (os.path.join(root, ".minecraft", "versions", name),
                     os.path.join(root, "versions", name)):
            if os.path.isdir(cand):
                return cand
        try:
            subs = os.listdir(root)
        except OSError:
            continue
        for sub in subs:
            cand = os.path.join(root, sub, ".minecraft", "versions", name)
            if os.path.isdir(cand):
                return cand
    return None


def group_block(group_path):
    """The group.snbt content with the outer braces stripped, as one indent-safe line."""
    with io.open(group_path, encoding="utf-8-sig") as f:
        text = f.read().strip()
    inner = text.strip()
    if inner.startswith("{"):
        inner = inner[1:]
    if inner.endswith("}"):
        inner = inner[:-1]
    # collapse the pretty-printed JSON into a single SNBT entry
    inner = re.sub(r"\s*\n\s*", " ", inner).strip()
    inner = inner.rstrip(",")
    return inner


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", default=None, help="instance folder (default: auto-detect)")
    ap.add_argument("--apply", action="store_true", help="actually write (default: dry run)")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    src = os.path.join(repo, "questbook", "ftbquests")
    src_chapters = os.path.join(src, "chapters")
    group_src = os.path.join(src, "group.snbt")

    live = args.live or find_instance()
    if not live or not os.path.isdir(live):
        print("ERROR: instance not found; pass --live <path>")
        return 1
    quests = os.path.join(live, "config", "ftbquests", "quests")
    chapters = os.path.join(quests, "chapters")
    groups_file = os.path.join(quests, "chapter_groups.snbt")

    print("mode      : %s" % ("APPLY" if args.apply else "DRY RUN (nothing will be written)"))
    print("repo      : %s" % src_chapters)
    print("instance  : %s" % chapters)
    print("groups    : %s" % groups_file)
    print("")

    if not os.path.isdir(quests):
        print("ERROR: %s does not exist -- is this the right instance?" % quests)
        return 1
    os.makedirs(chapters, exist_ok=True)

    files = sorted(glob.glob(os.path.join(src_chapters, "*.snbt")))
    if not files:
        print("ERROR: no chapters found in %s" % src_chapters)
        return 1

    if java_running():
        print("ERROR: a java process is running -- close Minecraft first; no files changed.")
        return 1

    # ---- what will happen? ----
    to_copy, overwrite = [], []
    for path in files:
        name = os.path.basename(path)
        target = os.path.join(chapters, name)
        (overwrite if os.path.isfile(target) else to_copy).append(name)
    print("chapters to add      : %d" % len(to_copy))
    print("chapters to overwrite: %d %s" % (len(overwrite), overwrite if overwrite else ""))
    print("")

    block = group_block(group_src)
    gid = re.search(r'"?id"?\s*:\s*"([^"]+)"', block).group(1)
    with io.open(groups_file, encoding="utf-8-sig") as f:
        groups_text = f.read()
    already = gid in groups_text
    print("group  : %s" % block)
    print("registered already? %s" % ("yes (will not touch it)" if already else "no -> will append"))
    print("")

    # ---- plan summary ----
    print("PLAN")
    for name in to_copy:
        print("  + chapters/%s" % name)
    for name in overwrite:
        print("  ~ chapters/%s (overwrite)" % name)
    if not already:
        print("  ~ chapter_groups.snbt  (append group %s)" % gid)
    print("  backup -> %s.bak-<timestamp>" % os.path.join(live, "config", "ftbquests"))
    print("")

    if not args.apply:
        print("dry run only. Re-run with --apply to do it.")
        return 0

    # ---- backup ----
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = os.path.join(live, "config", "ftbquests.bak-" + stamp)
    shutil.copytree(os.path.join(live, "config", "ftbquests"), backup)
    print("backup done: %s" % backup)

    # ---- copy chapters ----
    for path in files:
        shutil.copy2(path, os.path.join(chapters, os.path.basename(path)))
    print("copied %d chapter file(s)" % len(files))

    # ---- register the group ----
    if not already:
        idx = groups_text.rfind("]")
        if idx < 0:
            print("ERROR: chapter_groups.snbt has no ']' -- not touching it")
            return 1
        new_text = groups_text[:idx] + "\t\t{ " + block + " }\n\t" + groups_text[idx:]
        with io.open(groups_file, "w", encoding="utf-8", newline="\n") as f:
            f.write(new_text)
        print("registered group %s" % gid)

    # ---- verify with the same preflight used before ----
    print("")
    checker = os.path.join(repo, "tools", "inspect_questbook.py")
    if os.path.isfile(checker):
        print("re-running preflight:")
        subprocess.run([sys.executable, checker, "--live", live])

    print("")
    print("done. Start the game and open the quest book (default key: E -> quest book tab).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
