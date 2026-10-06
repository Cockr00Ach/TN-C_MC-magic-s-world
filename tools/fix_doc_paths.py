# -*- coding: utf-8 -*-
"""
fix_doc_paths.py  --  repoint scripts and instructions at the moved documents.

After tools/reorganize_docs.py moved 86 documents, everything that addressed them by
path has to follow. This script only rewrites KNOWN, EXACT strings (old path -> new
path) in tools/ and src/, so it cannot mangle unrelated text.

It reports every replacement it makes and every pattern it could not find.

USAGE
=====
    python tools/fix_doc_paths.py            # dry run
    python tools/fix_doc_paths.py --apply
"""

import argparse
import io
import os
import sys

# old path fragment -> new path fragment (both forward slashes)
REPOINT = {
    "docs/天空岛内饰交付给建造者.md": "docs/town/天空岛内饰交付给建造者.md",
    "docs/任务书与法杖修订3-20260929.md": "docs/quests/任务书与法杖修订3-20260929.md",
    "docs/归航银行-借贷与房屋分期设计.md": "docs/quests/归航银行-借贷与房屋分期设计.md",
    "docs/任务书排版与建筑地图修改说明.md": "docs/quests/任务书排版与建筑地图修改说明.md",
    "docs/续作完成报告-20260929.md": "docs/town/续作完成报告-20260929.md",
}

# files that actually ADDRESS those documents by path.
# NOTE: deliberately an explicit list, not a directory sweep -- a sweep would also
# rewrite tools/reorganize_docs.py, whose MOVES table stores the old->new mapping as
# string literals, destroying the record of where everything went.
TARGET_FILES = (
    "tools/package-feedback-delivery.py",
    "tools/package-revision-delivery.py",
)


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    hits = 0
    for rel in TARGET_FILES:
        path = os.path.join(repo, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            print("  [skip] %s (not found)" % rel)
            continue
        try:
            with io.open(path, encoding="utf-8-sig", errors="replace") as f:
                text = f.read()
        except OSError:
            continue
        original = text
        for old, new in REPOINT.items():
            if old in text:
                text = text.replace(old, new)
                print("  %s: %s -> %s" % (rel, old, new))
                hits += 1
        if text != original:
            if args.apply:
                with io.open(path, "w", encoding="utf-8", newline="\n") as f:
                    f.write(text)
            print("    %s %s" % ("wrote" if args.apply else "would write", rel))

    print("")
    print("replacements: %d" % hits)
    if not args.apply:
        print("dry run. Re-run with --apply to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
