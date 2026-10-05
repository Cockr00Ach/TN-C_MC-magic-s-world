# -*- coding: utf-8 -*-
"""
sync_kubejs_back.py  --  pull instance-side kubejs content that never made it to the repo.

WHY
===
The pack's kubejs folder is mirrored in the repo (modpack/<pack>/kubejs) so that
"repo + your own base modpack = full environment". But work done directly in the
game can drift: right now the instance holds light-spell textures and rebalanced
spell jsons that the repo never received. Sitting only in the instance means one
machine failure loses them.

This script copies ONLY files that are missing or newer on the instance side, and
prints a table. It never deletes and never touches the instance.

USAGE
=====
    python tools/sync_kubejs_back.py --live "<instance path>"            # dry run
    python tools/sync_kubejs_back.py --live "<instance path>" --apply

Pure ASCII script (repo rule).
"""

import argparse
import hashlib
import io
import os
import shutil
import sys

REPO_PACK = os.path.join("modpack", "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915")
SKIP_DIRS = {"local", "cache", "exported_packs", "node_modules", "__pycache__"}
SKIP_EXT = (".pyc", ".class", ".log", ".bak", ".tmp")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", required=True, help="instance folder")
    ap.add_argument("--apply", action="store_true", help="copy (default: dry run)")
    ap.add_argument("--only-newer", action="store_true",
                    help="copy only when the instance copy is strictly newer")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    repo_kubejs = os.path.join(repo, REPO_PACK, "kubejs")
    live_kubejs = os.path.join(args.live, "kubejs")
    if not os.path.isdir(repo_kubejs) or not os.path.isdir(live_kubejs):
        print("ERROR: need both %s and %s" % (repo_kubejs, live_kubejs))
        return 1

    print("mode: %s" % ("APPLY" if args.apply else "DRY RUN"))
    print("repo   : %s" % repo_kubejs)
    print("instance: %s" % live_kubejs)
    print("")

    copy_list = []
    for dirpath, dirs, files in os.walk(live_kubejs):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if fn.endswith(SKIP_EXT):
                continue
            src = os.path.join(dirpath, fn)
            rel = os.path.relpath(src, live_kubejs)
            dst = os.path.join(repo_kubejs, rel)
            if not os.path.isfile(dst):
                copy_list.append(("NEW", rel, src, dst))
            else:
                if args.only_newer and os.path.getmtime(src) <= os.path.getmtime(dst):
                    continue
                if sha(src) != sha(dst):
                    copy_list.append(("UPD", rel, src, dst))

    if not copy_list:
        print("nothing to bring back (repo mirror already matches the instance)")
        return 0

    for kind, rel, src, _dst in copy_list:
        print("  [%s] %s  (%d B, instance %s)"
              % (kind, rel.replace("\\", "/"), os.path.getsize(src),
                 __import__("datetime").datetime.fromtimestamp(
                     os.path.getmtime(src)).strftime("%m-%d %H:%M")))

    print("")
    print("total: %d file(s)" % len(copy_list))
    if not args.apply:
        print("dry run only. Re-run with --apply to copy them into the repo mirror.")
        return 0

    for _kind, _rel, src, dst in copy_list:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
    print("copied %d file(s) into the repo mirror." % len(copy_list))
    print("Next: review with 'git status', then commit/push so the team gets them.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
