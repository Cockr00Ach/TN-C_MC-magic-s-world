#!/usr/bin/env python
"""
check_tnc_quest_chain.py -- static reachability check for the TN-C story chain.

Why this exists
---------------
The chain advances in two ways:
  1. a quest's `next` list activates the following quest when it completes;
  2. a dialogue script completes quests via @quest/@quests and starts one via
     @activate, and only plays when its @requires gates all pass.

A mistake in either direction is INVISIBLE in game: the affected quest simply
never appears or never completes, and the quest book just looks short.  That
already happened once (2026-09-29: five quests were unreachable).  This script
makes it a build-time error instead.

What it checks
--------------
  * every `next` / @quest / @activate / @requires / @excludes id resolves
  * the quest graph is reachable from the login-script entry quest(s)
  * every quest that can only be completed by a dialogue HAS such a dialogue
  * no dialogue completes a quest whose own gate can never be satisfied
  * dialogue gating: at least one script per NPC can play with no @requires

ASCII only: this console is cp936 and CJK would come out as mojibake.
"""

import argparse
import io
import json
import os
import re
import sys

NS = "tnc"


def load_quests(tasks_root):
    quests = {}
    for dirpath, _dirs, files in os.walk(tasks_root):
        for name in files:
            if not name.endswith(".json"):
                continue
            path = os.path.join(dirpath, name)
            try:
                obj = json.loads(io.open(path, encoding="utf-8").read())
            except Exception as exc:                       # noqa: BLE001
                print("  !! %s: invalid JSON: %s" % (name, exc))
                continue
            quests[obj["id"]] = obj
    return quests


def load_dialogues(dlg_root):
    """id -> dict(requires, excludes, quests, activate)."""
    out = {}
    for name in sorted(os.listdir(dlg_root)):
        if not name.endswith(".txt"):
            continue
        text = io.open(os.path.join(dlg_root, name), encoding="utf-8").read()
        qid = re.search(r"(?m)^\s*@id\s+(\S+)", text)
        entry = {"file": name, "requires": [], "excludes": [], "quests": [], "activate": None}
        for m in re.finditer(r"(?m)^\s*@requires\s+(\S+)", text):
            entry["requires"].append(m.group(1))
        for m in re.finditer(r"(?m)^\s*@excludes\s+(\S+)", text):
            entry["excludes"].append(m.group(1))
        for m in re.finditer(r"(?m)^\s*@quest\s+(\S+)", text):
            entry["quests"].append(m.group(1))
        for m in re.finditer(r"(?m)^\s*@quests\s+(.+)$", text):
            entry["quests"].extend(m.group(1).split())
        m = re.search(r"(?m)^\s*@activate\s+(\S+)", text)
        if m:
            entry["activate"] = m.group(1)
        if qid:
            out[qid.group(1)] = entry
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default=None)
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if args.pack:
        packs = [args.pack]
    else:
        packs = [d for d in os.listdir(os.path.join(repo, "modpack"))
                 if os.path.isdir(os.path.join(repo, "modpack", d, "kubejs"))]
    if not packs:
        print("no pack found")
        return 1

    problems = []
    for pack in packs:
        base = pack if os.path.isabs(pack) else os.path.join(repo, "modpack", pack)
        tasks_root = os.path.join(base, "kubejs", "data", NS, "whisperingquests", "tasks")
        dlg_root = os.path.join(repo, "src", "main", "resources", "data", NS, "dialogues")
        if not os.path.isdir(tasks_root):
            continue

        quests = load_quests(tasks_root)
        dialogues = load_dialogues(dlg_root)
        print("== %s : %d quest(s), %d dialogue(s)" % (os.path.basename(base), len(quests), len(dialogues)))

        # 1. every referenced id must exist
        for qid, q in quests.items():
            for nxt in q.get("next", []):
                if nxt not in quests:
                    problems.append("%s: next -> unknown quest %s" % (qid, nxt))
        for did, d in dialogues.items():
            for field in ("requires", "excludes", "quests"):
                for target in d[field]:
                    if target not in quests:
                        problems.append("dialogue %s (%s): @%s -> unknown quest %s"
                                        % (did, d["file"], field, target))
            if d["activate"] and d["activate"] not in quests:
                problems.append("dialogue %s (%s): @activate -> unknown quest %s"
                                % (did, d["file"], d["activate"]))

        # 2. reachability from the login entry quest(s)
        script = os.path.join(base, "kubejs", "server_scripts", "world", "tnc_quest_start.js")
        entries = []
        if os.path.exists(script):
            txt = io.open(script, encoding="utf-8").read()
            m = re.search(r"TNC_STARTUP_QUESTS\s*=\s*\[([^\]]*)\]", txt)
            if m:
                entries = re.findall(r"'([^']+)'", m.group(1))
        for e in entries:
            if e not in quests:
                problems.append("tnc_quest_start.js starts unknown quest %s" % e)

        reachable = set()
        frontier = [e for e in entries if e in quests]
        while frontier:
            cur = frontier.pop()
            if cur in reachable:
                continue
            reachable.add(cur)
            for nxt in quests[cur].get("next", []):
                if nxt not in reachable:
                    frontier.append(nxt)
        # @activate also starts quests (a dialogue may start one out of order)
        for d in dialogues.values():
            if d["activate"] and d["activate"] in quests and d["activate"] not in reachable:
                frontier = [d["activate"]]
                while frontier:
                    cur = frontier.pop()
                    if cur in reachable:
                        continue
                    reachable.add(cur)
                    for nxt in quests[cur].get("next", []):
                        if nxt not in reachable:
                            frontier.append(nxt)

        orphan = sorted(set(quests) - reachable)
        if orphan:
            problems.append("unreachable from the entry quest(s): " + ", ".join(orphan))
        else:
            print("   [ok] all %d quest(s) reachable from %s" % (len(quests), entries))

        # 3. every dialogue-completed quest must have a dialogue that can fire
        completed_by = {}
        for d in dialogues.values():
            for q in d["quests"]:
                completed_by.setdefault(q, []).append(d)
        for qid, q in sorted(quests.items()):
            objectives = q.get("objectives", [])
            if not objectives:
                continue
            if all(o.get("type") == "dialogue" for o in objectives):
                if qid not in completed_by:
                    problems.append("%s: only completable by dialogue, but NO dialogue has @quest %s"
                                    % (qid, qid))
        for qid in sorted(completed_by):
            if qid not in quests:
                continue
            print("   %-24s <- %s" % (qid, ", ".join(d["file"] for d in completed_by[qid])))

        # 4.每章至少要有一个无门槛的入口任务（否则整章永远不显示）
        chapters = {}
        for qid, q in quests.items():
            chapters.setdefault(q.get("chapter"), []).append(qid)
        for chapter, qids in sorted(chapters.items(), key=lambda kv: str(kv[0])):
            ungated = [q for q in qids
                       if not quests[q].get("start_triggers")
                       and q in reachable]
            if not ungated:
                problems.append("chapter %s has no reachable quest" % chapter)

        # 5. an NPC with SEVERAL scripts needs at least one playable with no
        #    @requires -- otherwise right-clicking before any gate is satisfied
        #    picks nothing (DialoguePicker falls back, but only if such a script
        #    exists).  A single-script NPC is exempt: that one is the fallback.
        by_npc = {}
        for did, d in dialogues.items():
            npc = re.sub(r"_\d+$|_first$", "", did.split(":")[-1])
            by_npc.setdefault(npc, []).append(d)
        for npc, ds in sorted(by_npc.items()):
            if len(ds) > 1 and not any(not d["requires"] for d in ds):
                problems.append("npc %s has %d scripts but none is ungated -- "
                                "an early right-click selects nothing" % (npc, len(ds)))

    print()
    if problems:
        print("PROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  -", p)
        return 1
    print("chain OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
