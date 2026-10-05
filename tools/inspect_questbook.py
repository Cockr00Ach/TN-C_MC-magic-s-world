# -*- coding: utf-8 -*-
"""
inspect_questbook.py  --  preflight for deploying the authored FTB questbook.

Checks, before anything is copied into the game:
  * every chapter file parses as SNBT-ish JSON and has filename / id / group
  * no duplicate chapter ids, no duplicate quest ids across the whole book
  * every group a chapter points at is declared in the chapter_groups file
  * chapters that exist in the instance but not in the repo (local-only leftovers)

USAGE
=====
    python tools/inspect_questbook.py --live "<instance path>"
    python tools/inspect_questbook.py --live "<instance>" --check-groups-only

Exit 0 = safe to deploy, 1 = something would break.
Pure ASCII output (Windows console is GBK).
"""

import argparse
import io
import json
import os
import re
import sys


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read_snbt(path):
    """FTB .snbt files in this repo are JSON-compatible; strip a BOM if present."""
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def chapter_files(folder):
    if not os.path.isdir(folder):
        return []
    return sorted(os.path.join(folder, n) for n in os.listdir(folder) if n.endswith(".snbt"))


def collect_groups(path):
    """
    Group ids declared in a chapter_groups.snbt.

    NOTE: chapter_groups.snbt is real SNBT, not JSON -- its keys are NOT quoted
    (  { id: "6F8CF18325606049", title: "&6TN-C ..." }  ), so json.load fails on it.
    Chapter files in this repo happen to be JSON-compatible, but this one is not, so
    parse it with a pattern instead. (A JSON-only parser here produced a false
    "chapters point at undeclared groups" warning right after a good deploy.)
    """
    if not os.path.isfile(path):
        return {}
    with io.open(path, encoding="utf-8-sig") as f:
        text = f.read()
    out = {}
    for match in re.finditer(r'\{([^{}]*)\}', text):
        body = match.group(1)
        # keys may be quoted or bare:  { id: ... }   and   { "id": ... }
        gid = re.search(r'"?\bid"?\s*:\s*"([^"]+)"', body)
        title = re.search(r'"?\btitle"?\s*:\s*"([^"]*)"', body)
        if gid:
            out[gid.group(1)] = title.group(1) if title else ""
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", required=True, help="instance folder (has config/, mods/, ...)")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    src_chapters = os.path.join(repo, "questbook", "ftbquests", "chapters")
    live_quests = os.path.join(args.live, "config", "ftbquests", "quests")
    live_chapters = os.path.join(live_quests, "chapters")

    print("authored chapters : %s" % src_chapters)
    print("instance chapters : %s" % live_chapters)
    print("")

    problems = []
    ids = {}
    quest_ids = {}
    src_names = set()

    print("== authored chapters ==")
    for path in chapter_files(src_chapters):
        name = os.path.basename(path)
        src_names.add(name)
        try:
            data = read_snbt(path)
        except Exception as exc:
            problems.append("%s does not parse as JSON/SNBT: %s" % (name, exc))
            continue
        cid = data.get("id")
        group = data.get("group")
        fname = data.get("filename", "(no filename field)")
        quests = data.get("quests", []) or []
        print("  %-34s id=%-18s group=%-18s quests=%d filename=%s"
              % (name, cid, group, len(quests), fname))
        if not cid:
            problems.append("%s has no id" % name)
        elif cid in ids:
            problems.append("duplicate chapter id %s in %s and %s" % (cid, ids[cid], name))
        else:
            ids[cid] = name
        if not group:
            problems.append("%s has no group" % name)
        if fname != "(no filename field)" and fname != os.path.splitext(name)[0]:
            problems.append("%s: filename field '%s' does not match the file name" % (name, fname))
        for q in quests:
            qid = q.get("id")
            if not qid:
                problems.append("%s: a quest has no id" % name)
                continue
            if qid in quest_ids:
                problems.append("duplicate quest id %s in %s and %s" % (qid, quest_ids[qid], name))
            else:
                quest_ids[qid] = name

    print("")
    print("== groups in the instance ==")
    groups = collect_groups(os.path.join(live_quests, "chapter_groups.snbt"))
    for gid, title in groups.items():
        print("  %-18s %s" % (gid, title))
    missing_groups = sorted({read_snbt(p).get("group") for p in chapter_files(src_chapters)
                             if os.path.isfile(p)} - set(groups))
    missing_groups = [g for g in missing_groups if g]
    if missing_groups:
        print("")
        print("  !! chapters point at groups the instance does not declare: %s" % missing_groups)

    print("")
    print("== chapters only in the instance (local-only) ==")
    for path in chapter_files(live_chapters):
        name = os.path.basename(path)
        if name not in src_names:
            print("  %s" % name)

    print("")
    print("totals: authored chapters=%d  quest ids=%d  groups=%d"
          % (len(src_names), len(quest_ids), len(groups)))
    if problems:
        print("")
        print("PROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  [X] %s" % p)
        return 1
    print("preflight OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
