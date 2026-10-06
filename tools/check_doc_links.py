# -*- coding: utf-8 -*-
"""
check_doc_links.py  --  find relative markdown links that broke when docs moved.

WHY
===
tools/reorganize_docs.py moved 86 documents into topic folders. A link written as
`docs/当前状态.md` from the repo root still works, but a link written as
`当前状态.md` (same folder) or `../剧情总纲.md` breaks the moment the file moves.

This scans every .md for markdown links / bare .md mentions and reports the ones that
no longer resolve.

USAGE
=====
    python tools/check_doc_links.py
"""

import io
import os
import re
import sys

SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".jdk17", "run", "run-data",
             "node_modules", "__pycache__", "work", ".dsh-tmp", "libs"}
LINK = re.compile(r"\[[^\]]*\]\(([^)]+\.md)\)")
BARE = re.compile(r"`([^`\n]+\.md)`")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    repo = repo_root()
    broken, checked = [], 0

    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if not fn.endswith(".md"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, repo).replace("\\", "/")
            try:
                with io.open(path, encoding="utf-8-sig", errors="replace") as f:
                    text = f.read()
            except OSError:
                continue
            targets = set(LINK.findall(text)) | set(BARE.findall(text))
            for t in targets:
                if t.startswith(("http://", "https://", "mailto:")):
                    continue
                t = t.split("#")[0].strip()
                if not t:
                    continue
                checked += 1
                # absolute-from-root or relative-to-this-file
                cands = [os.path.join(repo, t.replace("/", os.sep)),
                         os.path.join(dirpath, t.replace("/", os.sep))]
                if not any(os.path.exists(c) for c in cands):
                    broken.append((rel, t))

    print("links/bare mentions checked: %d" % checked)
    print("unresolved: %d" % len(broken))
    for rel, t in broken:
        print("   %s  ->  %s" % (rel, t))
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
