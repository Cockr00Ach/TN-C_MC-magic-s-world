# -*- coding: utf-8 -*-
"""
inventory_map_gap.py  --  compare the map / worldgen content between repo and instance.

WHY
===
tools/inventory_deploy_gap.py covers the FTB questbook and kubejs, but the map work
lives in other places: worldgen datapack files (data/), the vaultpatcher overrides,
generated structures, and the world save itself. This script covers those, so
"did the map get deployed?" can be answered instead of assumed.

It reports, per area: how many files each side has, which exist only on one side,
and which differ in content. Read-only.

USAGE
=====
    python tools/inventory_map_gap.py --live "<instance path>"

Pure ASCII output.
"""

import argparse
import hashlib
import os
import sys

REPO_PACK = os.path.join("modpack", "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915")

# (label, repo-relative, instance-relative)
AREAS = [
    ("pack data/ (worldgen datapack)", REPO_PACK + "/data", "data"),
    ("pack vaultpatcher/", REPO_PACK + "/vaultpatcher", "vaultpatcher"),
    ("pack hotai/", REPO_PACK + "/hotai", "hotai"),
    ("pack resourcepacks/", REPO_PACK + "/resourcepacks", "resourcepacks"),
    ("pack moddata/", REPO_PACK + "/moddata", "moddata"),
    ("pack schematics/", REPO_PACK + "/schematics", "schematics"),
    ("mod jar worldgen (src)", "src/main/resources/data/tnc/worldgen", None),
    ("mod jar structures (src)", "src/main/resources/data/tnc/structures", None),
    ("mod jar stonecrest manifest (src)", "src/main/resources/data/tnc/stonecrest", None),
    ("tools map generators", "tools", None),
]

SKIP_EXT = (".pyc", ".class", ".log", ".bak")
SKIP_DIRS = {"cache", "exported_packs", "__pycache__", "node_modules"}


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def scan(root, limit_note=None):
    out = {}
    if not root or not os.path.isdir(root):
        return out
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
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
    ap.add_argument("--live", required=True)
    ap.add_argument("--list", type=int, default=10, help="how many names to print per area")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    print("instance: %s" % args.live)
    print("")

    for label, repo_rel, live_rel in AREAS:
        left = scan(os.path.join(repo, repo_rel))
        if live_rel is None:
            print("[INFO] %s -> %d file(s) in the repo" % (label, len(left)))
            for n in sorted(left)[:args.list]:
                print("        %s" % n)
            print("")
            continue
        right = scan(os.path.join(args.live, live_rel))
        only_left = sorted(set(left) - set(right))
        only_right = sorted(set(right) - set(left))
        differing = sorted(n for n in set(left) & set(right) if left[n] != right[n])
        status = "OK" if not (only_left or differing) else "GAP"
        print("[%s] %s" % (status, label))
        print("      repo=%d instance=%d only-in-repo=%d only-in-instance=%d differs=%d"
              % (len(left), len(right), len(only_left), len(only_right), len(differing)))
        for n in only_left[:args.list]:
            print("        repo-only    : %s" % n)
        if len(only_left) > args.list:
            print("        ... %d more repo-only" % (len(only_left) - args.list))
        for n in only_right[:args.list]:
            print("        instance-only: %s" % n)
        if len(only_right) > args.list:
            print("        ... %d more instance-only" % (len(only_right) - args.list))
        for n in differing[:args.list]:
            print("        differs      : %s" % n)
        print("")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
