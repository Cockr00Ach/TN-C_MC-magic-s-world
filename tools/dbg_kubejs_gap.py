# -*- coding: utf-8 -*-
"""
dbg_kubejs_gap.py  --  show what differs between the repo mirror and the instance
                       for the kubejs spell files that drifted apart.

Read-only diagnostic: prints a compact structural diff (top-level keys, and for
lists/dicts the per-key differences) so a human can tell "the instance has newer
content that should be synced back" from "the repo is authoritative".

USAGE
=====
    python tools/dbg_kubejs_gap.py --live "<instance path>"
"""

import argparse
import io
import json
import os
import sys

REPO_PACK = os.path.join("modpack", "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load(path):
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def walk_diff(a, b, prefix="", out=None, limit=40):
    if out is None:
        out = []
    if len(out) >= limit:
        return out
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append("%s.%s  (only in instance)" % (prefix, k))
            elif k not in b:
                out.append("%s.%s  (only in repo)" % (prefix, k))
            else:
                walk_diff(a[k], b[k], "%s.%s" % (prefix, k), out, limit)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            out.append("%s  list length repo=%d instance=%d" % (prefix, len(a), len(b)))
        for i in range(min(len(a), len(b))):
            walk_diff(a[i], b[i], "%s[%d]" % (prefix, i), out, limit)
    else:
        if a != b:
            out.append("%s  repo=%r instance=%r" % (prefix, a, b))
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", required=True)
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    names = ["dark_king", "evil_god", "summon_dark", "summon_elite", "summon_lord"]
    for n in names:
        rel = "kubejs/data/tnc/spells/%s.json" % n
        a_path = os.path.join(repo, REPO_PACK, rel.replace("/", os.sep))
        b_path = os.path.join(args.live, rel.replace("/", os.sep))
        print("=== %s ===" % rel)
        if not (os.path.isfile(a_path) and os.path.isfile(b_path)):
            print("   missing on one side (repo=%s instance=%s)"
                  % (os.path.isfile(a_path), os.path.isfile(b_path)))
            continue
        a, b = load(a_path), load(b_path)
        print("   repo top-level keys    : %s" % sorted(a))
        print("   instance top-level keys: %s" % sorted(b))
        diffs = walk_diff(a, b)
        if not diffs:
            print("   (structurally identical)")
        for d in diffs[:25]:
            print("   %s" % d)
        print("")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
