#!/usr/bin/env python
"""
check_tnc_quest_bridge.py -- verify every reflection target the TN-C quest bridge uses.

Why this exists
---------------
WhisperingQuestBridge is deliberately reflection-based: no compile dependency on the
third-party mod, and a missing method degrades to "no quest advancement" instead of
crashing.  The cost is that a WRONG reflection target is invisible at compile time
and only surfaces as a line in the game log -- by which time the player just sees
"this NPC still plays the first line".

That mistake shipped three times on 2026-09-29:
  1. QuestManager.isCompletedForPlayer(ServerPlayer, ResourceLocation)
       -- the real method is PRIVATE, so Class.getMethod never finds it.
  2. QuestManager.getQuest(ResourceLocation)
       -- getQuest lives on QuestDataManager, not QuestManager.
  3. (earlier) reading completions from the team state only
       -- per-player quests are not in that set.

This script asks javap what the mod jar actually contains, so the answer comes from
the jar rather than from anyone's memory.  javap is used instead of a hand-written
class parser: it is already on this machine (bundled with .jdk17) and handles the
whole class-file format correctly.

Usage:
    python tools/check_tnc_quest_bridge.py
    python tools/check_tnc_quest_bridge.py --jar "<path to whisperingquests jar>"

ASCII only on purpose (cp936 console).
"""

import argparse
import io
import os
import re
import subprocess
import sys
import zipfile

SRC = os.path.join("src", "main", "java", "com", "tnc", "tnc", "dialogue", "compat",
                   "WhisperingQuestBridge.java")

# (target class, method name, parameter simple names in order)
# Keep in sync with WhisperingQuestBridge.
EXPECTED_METHODS = [
    ("com.lirxowo.whisperingquests.quest.QuestManager", "completeDialogueObjective",
     ["ServerPlayer", "ResourceLocation"]),
    ("com.lirxowo.whisperingquests.quest.QuestManager", "startQuest",
     ["ServerPlayer", "ResourceLocation"]),
    # claimReward is what actually pushes a quest into completedQuests
    # (finishQuest is only reachable from here), so the bridge calls it after
    # completing a dialogue objective -- otherwise the next segment's @requires
    # never passes.
    ("com.lirxowo.whisperingquests.quest.QuestManager", "claimReward",
     ["ServerPlayer", "ResourceLocation"]),
    ("com.lirxowo.whisperingquests.quest.QuestManager", "getQuestState",
     ["ServerPlayer", "QuestDefinition"]),
    ("com.lirxowo.whisperingquests.data.QuestDataManager", "getQuest",
     ["ResourceLocation"]),
    ("com.lirxowo.whisperingquests.quest.TeamQuestState", "activeQuests", []),
    ("com.lirxowo.whisperingquests.quest.TeamQuestState", "completedQuests", []),
]

# public static FIELDS the bridge reads
EXPECTED_FIELDS = [
    ("com.lirxowo.whisperingquests.data.QuestDataManager", "INSTANCE"),
]


def find_jar(explicit=None):
    if explicit and os.path.exists(explicit):
        return explicit
    live = r"E:\download\正式版 2.12.6.1\.minecraft\versions"
    candidates = []
    if os.path.isdir(live):
        for pack in os.listdir(live):
            mods = os.path.join(live, pack, "mods")
            if os.path.isdir(mods):
                for f in os.listdir(mods):
                    if f.startswith("whisperingquests") and f.endswith(".jar"):
                        candidates.append(os.path.join(mods, f))
    return candidates[0] if candidates else None


def javap(javap_exe, classpath, cls):
    """Return javap's declaration lines for one class, or None if it could not read it."""
    try:
        out = subprocess.run(
            [javap_exe, "-p", "-classpath", classpath, cls],
            capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError) as exc:
        print("  ! javap failed: %s" % exc)
        return None
    if out.returncode != 0:
        return None
    return out.stdout


def simple(type_text):
    """'net.minecraft.server.level.ServerPlayer' -> 'ServerPlayer'"""
    return type_text.strip().rsplit(".", 1)[-1]


def parse_params(param_text):
    """'(a.B, c.D)' -> ['B', 'D']; handles generics by cutting at '<'."""
    if not param_text:
        return []
    inner = param_text.strip()
    if not inner.startswith("("):
        return []
    inner = inner[1:inner.find(")")]
    if not inner.strip():
        return []
    parts, depth, cur = [], 0, ""
    for ch in inner:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
            continue
        cur += ch
    parts.append(cur)
    return [simple(p.split("<")[0]) for p in parts if p.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=None)
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jar = find_jar(args.jar)
    if not jar:
        print("could not locate the whisperingquests jar (pass --jar)")
        return 1
    print("jar: %s" % jar)
    print("     (%s)" % os.path.basename(jar))

    javap_exe = os.path.join(repo, ".jdk17", "bin", "javap.exe")
    if not os.path.exists(javap_exe):
        javap_exe = "javap"

    src_path = os.path.join(repo, SRC)
    if not os.path.exists(src_path):
        print("missing source: %s" % src_path)
        return 1
    src = io.open(src_path, encoding="utf-8").read()

    problems = []
    # A correct method listed in comments cannot prove the actual string passed to
    # getMethod(accessor) is correct. Verify the call-site accessor names as well.
    state_accessors = re.findall(r'engineState\(player,\s*questId,\s*"([^"]+)"\)', src)
    if len(state_accessors) != 2:
        problems.append("expected the active/completed engineState call sites; review accessor validation")
    state_declarations = javap(javap_exe, jar, "com.lirxowo.whisperingquests.quest.TeamQuestState") or ""
    for accessor in state_accessors:
        if not any(line.strip().startswith("public ") and accessor + "()" in line
                   for line in state_declarations.splitlines()):
            problems.append("actual engineState accessor %s is not a public TeamQuestState getter" % accessor)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for cls, member, want in EXPECTED_METHODS:
            entry = cls.replace(".", "/") + ".class"
            print("  %-30s %s" % (cls.rsplit(".", 1)[-1], member))
            if entry not in names:
                problems.append("%s: class not in jar" % cls)
                continue
            text = javap(javap_exe, jar, cls)
            if text is None:
                problems.append("%s: javap could not read it" % cls)
                continue
            found = False
            for line in text.splitlines():
                line = line.strip()
                if not line.endswith(";") or member + "(" not in line:
                    continue
                is_public = line.startswith("public")
                params = parse_params(line[line.find("("):])
                if params == want or (not want and not params):
                    found = True
                    if not is_public:
                        problems.append("%s#%s(%s): exists but is NOT public -- "
                                        "Class.getMethod cannot see it"
                                        % (cls, member, ",".join(want)))
                    break
            if not found:
                problems.append("%s#%s(%s): no public method with that exact signature"
                                % (cls, member, ",".join(want)))
            if member not in src:
                problems.append("%s#%s: not referenced in the bridge source "
                                "(this EXPECTED list is stale)" % (cls, member))

        for cls, field in EXPECTED_FIELDS:
            print("  %-30s %s (field)" % (cls.rsplit(".", 1)[-1], field))
            text = javap(javap_exe, jar, cls)
            if text is None:
                problems.append("%s: javap could not read it" % cls)
                continue
            ok = any(field in ln and ln.strip().endswith(";") and "public" in ln
                     for ln in text.splitlines())
            if not ok:
                problems.append("%s#%s: no public static field" % (cls, field))
            if field not in src:
                problems.append("%s#%s: not referenced in the bridge source" % (cls, field))

    print()
    if problems:
        print("BRIDGE PROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  -", p)
        print()
        print("A wrong target here does NOT crash: the bridge logs it and returns false,")
        print("so the player only sees 'this NPC never advances'.")
        return 1
    print("every reflection target resolves and is public")
    return 0


if __name__ == "__main__":
    sys.exit(main())
