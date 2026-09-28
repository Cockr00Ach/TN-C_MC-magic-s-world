#!/usr/bin/env python
"""
check_tnc_dialogue_segments.py -- can every dialogue segment EVER be played?

Why this exists
---------------
DialoguePicker resolves a segment by "highest sequence number whose gates pass".
That silently SHADOWS a segment: if a later segment has the same (or weaker)
@requires and no @excludes separating them, the earlier one can never be chosen
-- it is dead content that looks perfectly fine in the file listing.

A concrete case found on 2026-09-29: zuowang_02 required `s2_see`, but `s2_see`
is COMPLETED by zuowang_02 itself, so it could never fire; zhuangquerang_02 was
also masked by zhuangquerang_03, which had a weaker gate.

How it works
------------
1. Start from the login entry quest, simulate the chain along the `next` graph
   (each quest completed in turn) and collect every reachable "state" -- the set
   of quests completed at that moment.
2. For each state, ask the picker's own rule which segment would be chosen.
3. Report any segment that is never chosen in any reachable state.

This is a static model: it assumes the player advances along `next` and talks to
each NPC whenever a segment is available.  That is exactly the happy path we ship,
so "never chosen" here means "unreachable for anyone who plays the story".

ASCII only: this console is cp936 and CJK would come out as mojibake.
"""

import argparse
import io
import os
import re
import sys


def load_dialogues(dlg_root):
    out = {}
    for name in sorted(os.listdir(dlg_root)):
        if not name.endswith(".txt"):
            continue
        text = io.open(os.path.join(dlg_root, name), encoding="utf-8").read()
        m = re.search(r"(?m)^\s*@id\s+(\S+)", text)
        if not m:
            continue
        entry = {
            "id": m.group(1),
            "file": name,
            "requires": re.findall(r"(?m)^\s*@requires\s+(\S+)", text),
            "excludes": re.findall(r"(?m)^\s*@excludes\s+(\S+)", text),
            "quests": re.findall(r"(?m)^\s*@quest\s+(\S+)", text),
            "activate": (re.search(r"(?m)^\s*@activate\s+(\S+)", text) or [None, None])[1],
        }
        out[entry["id"]] = entry
    return out


def npc_of(dialogue_id):
    base = dialogue_id.split(":")[-1]
    return re.sub(r"_\d+$", "", base)


def seq_of(dialogue_id):
    base = dialogue_id.split(":")[-1]
    m = re.search(r"_(\d+)$", base)
    return int(m.group(1)) if m else 0


def chosen_at(dialogues, npc, completed):
    """Mirror DialoguePicker: highest seq whose gates pass; else lowest."""
    cands = sorted([d for d in dialogues.values() if npc_of(d["id"]) == npc],
                   key=lambda d: -seq_of(d["id"]))
    if not cands:
        return None
    for d in cands:
        if all(q in completed for q in d["requires"]) and \
           not any(q in completed for q in d["excludes"]):
            return d
    return cands[-1]          # the loader's fallback: lowest-numbered anyway


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default=None)
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    packs = [args.pack] if args.pack else [
        d for d in os.listdir(os.path.join(repo, "modpack"))
        if os.path.isdir(os.path.join(repo, "modpack", d, "kubejs"))]
    packs = [p for p in packs if p]

    problems = []
    for pack in packs:
        base = pack if os.path.isabs(pack) else os.path.join(repo, "modpack", pack)
        tasks_root = os.path.join(base, "kubejs", "data", "tnc", "whisperingquests", "tasks")
        dlg_root = os.path.join(repo, "src", "main", "resources", "data", "tnc", "dialogues")
        if not os.path.isdir(tasks_root):
            continue

        quests = {}
        for dirpath, _d, files in os.walk(tasks_root):
            for name in files:
                if name.endswith(".json"):
                    import json
                    q = json.loads(io.open(os.path.join(dirpath, name), encoding="utf-8").read())
                    quests[q["id"]] = q
        dialogues = load_dialogues(dlg_root)

        script = os.path.join(base, "kubejs", "server_scripts", "world", "tnc_quest_start.js")
        entries = []
        if os.path.exists(script):
            txt = io.open(script, encoding="utf-8").read()
            m = re.search(r"TNC_STARTUP_QUESTS\s*=\s*\[([^\]]*)\]", txt)
            if m:
                entries = re.findall(r"'([^']+)'", m.group(1))

        # Simulate the happy path: at every state the player talks to every NPC
        # (picking whatever segment the loader would pick), which completes that
        # segment's quests and may hand out @activate'd ones.  The chain `next`
        # links fire too.  Iterate to a fixpoint.
        states = []
        completed = set()
        pending = [e for e in entries if e in quests]
        seen_quests = set()
        rounds = 0
        while rounds < 200:
            rounds += 1
            progressed = False

            # 1) quests that are queued become completed
            while pending:
                cur = pending.pop(0)
                if cur in seen_quests or cur not in quests:
                    continue
                seen_quests.add(cur)
                completed.add(cur)
                states.append(frozenset(completed))
                progressed = True
                for nxt in quests[cur].get("next", []):
                    if nxt not in seen_quests:
                        pending.append(nxt)

            # 2) then the player may talk to every NPC once at this state
            for npc in {npc_of(d["id"]) for d in dialogues.values()}:
                d = chosen_at(dialogues, npc, completed)
                if not d:
                    continue
                for q in d["quests"]:
                    if q in quests and q not in seen_quests:
                        pending.append(q)
                        progressed = True
                if d["activate"] and d["activate"] in quests \
                        and d["activate"] not in seen_quests:
                    pending.append(d["activate"])
                    progressed = True

            if not progressed:
                break

        chosen = set()
        for state in states:
            for npc in {npc_of(d["id"]) for d in dialogues.values()}:
                d = chosen_at(dialogues, npc, state)
                if d:
                    chosen.add(d["id"])

        all_dialogues = set(dialogues)
        dead = sorted(all_dialogues - chosen)
        print("== %s : %d state(s) simulated" % (os.path.basename(base), len(states)))
        print("   pickable: %d/%d segment(s)" % (len(chosen), len(all_dialogues)))
        if dead:
            for d in dead:
                info = dialogues[d]
                problems.append(
                    "%s (%s): never selected -- requires=%s excludes=%s"
                    % (d, info["file"], info["requires"] or "[]", info["excludes"] or "[]"))

    print()
    if problems:
        print("SHADOWED SEGMENTS (%d) -- written but never played:" % len(problems))
        for p in problems:
            print("  -", p)
        return 1
    print("every segment is reachable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
