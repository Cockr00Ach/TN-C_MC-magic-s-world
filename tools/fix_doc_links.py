# -*- coding: utf-8 -*-
"""
fix_doc_links.py  --  repair the clickable markdown links broken by the docs move.

WHY
===
tools/reorganize_docs.py moved documents into topic folders. Links written as
`[x](docs/foo.md)` (repo-root relative) or `[x](foo.md)` (same folder) now point at
nothing. Prose mentions in inline code (`当前状态.md`) are deliberately left alone:
they are not links, and a human still finds the file.

Strategy: with a file name that no longer resolves, look the document up by BASENAME
among the known new locations, then rewrite the link relative to the linking file.

USAGE
=====
    python tools/fix_doc_links.py            # dry run
    python tools/fix_doc_links.py --apply
"""

import argparse
import ast
import io
import os
import re
import sys

SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".jdk17", "run", "run-data",
             "node_modules", "__pycache__", "work", ".dsh-tmp", "libs"}
LINK = re.compile(r"(\[[^\]]*\]\()([^)#]+\.md)((?:#[^)]*)?\))")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load_moves(repo):
    """Extract the MOVES dict literal from tools/reorganize_docs.py without importing it."""
    path = os.path.join(repo, "tools", "reorganize_docs.py")
    with io.open(path, encoding="utf-8-sig") as f:
        text = f.read()
    start = text.index("MOVES = {")
    end = text.index("\n}", start) + 2
    return ast.literal_eval(text[start + len("MOVES = "):end])


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    moves = load_moves(repo)

    # basename -> new repo-relative path (only for files that actually moved)
    new_by_base = {}
    for old, new in moves.items():
        if old == new:
            continue
        new_by_base[os.path.basename(new)] = new

    # fallback index: EVERY .md in the repo by basename, so links that were already
    # wrong before the move (e.g. a spec path written from the repo root while the
    # linking file sits two folders deep) can be repaired too.
    all_by_base = {}
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if fn.endswith(".md"):
                rel = os.path.relpath(os.path.join(dirpath, fn), repo).replace("\\", "/")
                all_by_base.setdefault(fn, []).append(rel)

    changed_files = 0
    fixed = 0
    unresolved = []

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

            out = []
            pos = 0
            touched = False
            for m in LINK.finditer(text):
                target = m.group(2)
                if target.startswith(("http://", "https://", "mailto:")):
                    continue
                # Candidates: repo-root relative and file-relative.
                cands = [os.path.join(repo, target.replace("/", os.sep)),
                         os.path.join(dirpath, target.replace("/", os.sep))]
                if any(os.path.exists(c) for c in cands):
                    continue
                # Prefer the real file that already sits at the linked location (an
                # existing but wrong path like ../art/x when it really is docs/art/x);
                # fall back to the by-basename lookup.
                new_rel = None
                for c in cands:
                    if os.path.exists(c):
                        new_rel = os.path.relpath(c, repo).replace("\\", "/")
                        break
                if not new_rel:
                    new_rel = new_by_base.get(os.path.basename(target))
                if not new_rel:
                    candidates = all_by_base.get(os.path.basename(target), [])
                    if len(candidates) == 1:
                        new_rel = candidates[0]
                if not new_rel:
                    unresolved.append((rel, target))
                    continue
                # write the link relative to THIS file (portable)
                new_target = os.path.relpath(os.path.join(repo, new_rel.replace("/", os.sep)),
                                             dirpath).replace("\\", "/")
                out.append(text[pos:m.start()])
                out.append(m.group(1) + new_target + m.group(3))
                pos = m.end()
                fixed += 1
                touched = True
                print("  %s: %s -> %s" % (rel, target, new_target))
            if touched:
                out.append(text[pos:])
                changed_files += 1
                if args.apply:
                    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
                        f.write("".join(out))

    print("")
    print("links repaired: %d in %d file(s)" % (fixed, changed_files))
    if unresolved:
        print("could not resolve (left alone): %d" % len(unresolved))
        for rel, t in unresolved[:20]:
            print("   %s -> %s" % (rel, t))
    if not args.apply:
        print("dry run. Re-run with --apply to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
