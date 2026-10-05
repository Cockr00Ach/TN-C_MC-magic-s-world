# -*- coding: utf-8 -*-
"""
inventory_deploy_gap.py  --  what does the repo have that the game instance does not?

WHY
===
The FTB questbook lives in the repo at questbook/ftbquests/ (git-tracked) because
modpack/*/config/* is gitignored, but FTB only LOADS from
<instance>/config/ftbquests/quests/. So "the repo has it" never means "the game
has it". The same is true for anything else that the pack loads from config/ or
kubejs/ but that we author outside those folders.

This script prints, per AREA, how many files each side has and lists the ones that
differ, so a deploy can be planned instead of guessed.

USAGE
=====
    python tools/inventory_deploy_gap.py
    python tools/inventory_deploy_gap.py --live "<instance path>"

Exit code is always 0: this is a report, not a gate.
"""

import argparse
import hashlib
import io
import os
import sys

WORK_PACK = os.path.join("modpack", "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915")
INSTANCE = "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915"

# (label, repo-relative dir, instance-relative dir)
AREAS = [
    ("FTB chapters (authored)", "questbook/ftbquests/chapters", "config/ftbquests/quests/chapters"),
    ("FTB quests root (workspace mirror)", WORK_PACK + "/config/ftbquests/quests", "config/ftbquests/quests"),
    ("kubejs (workspace mirror)", WORK_PACK + "/kubejs", "kubejs"),
    ("defaultconfigs", WORK_PACK + "/defaultconfigs", "defaultconfigs"),
    ("openloader (workspace mirror)", WORK_PACK + "/config/openloader", "config/openloader"),
    ("tlm_custom_pack", WORK_PACK + "/tlm_custom_pack", "tlm_custom_pack"),
]

SKIP_EXT = (".pyc", ".class", ".log")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find_instance():
    name = INSTANCE
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


def scan(root):
    """relpath -> (size, sha256) for every file under root."""
    out = {}
    if not os.path.isdir(root):
        return out
    for dirpath, _dirs, files in os.walk(root):
        for fn in files:
            if fn.endswith(SKIP_EXT):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root).replace("\\", "/")
            try:
                with open(path, "rb") as f:
                    data = f.read()
            except OSError:
                continue
            out[rel] = (len(data), hashlib.sha256(data).hexdigest())
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", default=None, help="instance folder (contains mods/, config/, ...)")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    live = args.live or find_instance()
    if not live:
        print("ERROR: could not find the live instance; pass --live <path>")
        return 1
    print("repo     : %s" % repo)
    print("instance : %s" % live)
    print("")

    for label, repo_rel, live_rel in AREAS:
        left = scan(os.path.join(repo, repo_rel))
        right = scan(os.path.join(live, live_rel))
        only_left = sorted(set(left) - set(right))
        only_right = sorted(set(right) - set(left))
        differing = sorted(n for n in set(left) & set(right) if left[n] != right[n])
        status = "OK" if not (only_left or differing) else "GAP"
        print("[%s] %s" % (status, label))
        print("      repo=%d  instance=%d  only-in-repo=%d  only-in-instance=%d  content-differs=%d"
              % (len(left), len(right), len(only_left), len(only_right), len(differing)))
        for n in only_left[:15]:
            print("        repo-only : %s" % n)
        if len(only_left) > 15:
            print("        ... and %d more repo-only" % (len(only_left) - 15))
        for n in differing[:10]:
            print("        differs   : %s" % n)
        if len(differing) > 10:
            print("        ... and %d more differing" % (len(differing) - 10))
        print("")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
