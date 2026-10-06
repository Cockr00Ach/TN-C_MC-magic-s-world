# -*- coding: utf-8 -*-
"""
audit_docs.py  --  inventory the workspace documents so a cleanup can be planned.

WHY
===
The repo accumulated ~150 markdown files. Before moving anything we need to know:
  * which docs are LIVING (have a maintainer header) vs dated snapshots
  * which docs are REFERENCED from elsewhere (moving those breaks other sessions'
    muscle memory and any hardcoded path in tools/)
  * which are already stale (superseded, old date, never referenced)

Read-only. Prints a classification plus a reference table.

USAGE
=====
    python tools/audit_docs.py
    python tools/audit_docs.py --md-out docs/_doc-inventory.md
"""

import argparse
import io
import os
import re
import sys
from collections import defaultdict

SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".jdk17", "run", "run-data",
             "node_modules", "__pycache__", "work", ".dsh-tmp"}
# files we do not treat as "documents" for this audit
SKIP_ROOTS = (os.path.join("docs", "superpowers"),)

DATE_IN_NAME = re.compile(r"(20\d{6})")
MAINTAINER = re.compile(r"维护者|维护方")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def first_lines(path, n=4):
    try:
        with io.open(path, encoding="utf-8-sig", errors="replace") as f:
            out = []
            for _ in range(n):
                line = f.readline()
                if not line:
                    break
                out.append(line.rstrip("\n"))
            return out
    except OSError:
        return []


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--md-out", default=None)
    args = ap.parse_args(argv[1:])

    repo = repo_root()

    docs = []
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if not fn.endswith(".md"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, repo).replace("\\", "/")
            docs.append(rel)
    docs.sort()

    # ---- reference scan: which files mention which doc basenames? ----
    basenames = {}
    for rel in docs:
        base = os.path.basename(rel)
        basenames.setdefault(base, []).append(rel)

    refs = defaultdict(set)
    scan_exts = (".md", ".ps1", ".py", ".java", ".json", ".txt", ".json5", ".snbt")
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if not fn.endswith(scan_exts):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, repo).replace("\\", "/")
            try:
                with io.open(path, encoding="utf-8-sig", errors="replace") as f:
                    text = f.read()
            except OSError:
                continue
            for base in basenames:
                if base in text:
                    refs[base].add(rel)

    # ---- classify ----
    living, snapshots, other, superseded = [], [], [], []
    for rel in docs:
        head = "\n".join(first_lines(os.path.join(repo, rel), 4))
        is_living = bool(MAINTAINER.search(head))
        m = DATE_IN_NAME.search(os.path.basename(rel))
        dated = bool(m)
        # a dated file that is ALSO the maintained handover is still living
        if is_living and not dated:
            living.append(rel)
        elif dated:
            (superseded if is_living else snapshots).append(rel)
        else:
            other.append(rel)

    out = []
    w = out.append

    w("# 文档清点（tools/audit_docs.py 生成，只读盘点）")
    w("")
    w("总计 **%d** 个 .md（不含 superpowers 的规格稿）" % len(docs))
    w("")
    w("## 一、活文档（有维护者署名、文件名无日期）")
    w("")
    w("| 文档 | 被引用 |")
    w("|---|---|")
    for rel in living:
        w("| `%s` | %d |" % (rel, len(refs.get(os.path.basename(rel), set()))))
    w("")
    w("## 二、带日期的记录/修复稿（历史快照，`YYYYMMDD`）")
    w("")
    w("| 文档 | 有维护者头 | 被引用 |")
    w("|---|---|---|")
    for rel in sorted(snapshots + superseded):
        base = os.path.basename(rel)
        head = "\n".join(first_lines(os.path.join(repo, rel), 4))
        w("| `%s` | %s | %d |" % (rel, "是" if MAINTAINER.search(head) else "否",
                                  len(refs.get(base, set()))))
    w("")
    w("## 三、其他（无日期、无维护者头）")
    w("")
    for rel in other:
        w("- `%s`" % rel)
    w("")

    text = "\n".join(out)
    print(text)
    if args.md_out:
        path = os.path.join(repo, args.md_out)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text + "\n")
        print("\nwritten: %s" % args.md_out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
