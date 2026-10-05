# -*- coding: utf-8 -*-
"""
resolve_lang_conflict.py  --  finish the en_us.json conflict of an in-flight rebase.

WHY
===
Another session's rebase of the dark-fog commit onto origin/main stopped on
src/main/resources/assets/tnc/lang/en_us.json. Both sides only appended keys at the
end of the object:

    ours  (origin/main):  "effect.tnc.burning_body", "effect.tnc.fire_cast"
    theirs (dark fog):    "effect.tnc.dark_fog"

Neither key is optional, so the correct resolution is the UNION. This script does
exactly that (and nothing else), then validates the result is loadable JSON.

It refuses to touch the file if the conflict is anything other than "both sides
appended keys at the end" -- a real overlap should be resolved by a human.

USAGE
=====
    python tools/resolve_lang_conflict.py --file <path> [--apply]

Pure ASCII script (repo rule).
"""

import argparse
import io
import json
import re
import sys


def parse_conflicts(text):
    """Return (head_text_without_conflicts, list_of (ours_lines, theirs_lines))."""
    conflicts = []
    pattern = re.compile(
        r"<<<<<<< [^\n]*\n(.*?)\n?=======\n(.*?)\n?>>>>>>> [^\n]*\n",
        re.S)
    for m in pattern.finditer(text):
        conflicts.append((m.group(1), m.group(2)))
    cleaned = pattern.sub("", text)
    return cleaned, conflicts


def entries(block):
    """Key/value pairs out of a fragment of a JSON object body."""
    out = []
    for line in block.splitlines():
        line = line.strip().rstrip(",")
        if not line:
            continue
        m = re.match(r'^"([^"]+)"\s*:\s*(.+)$', line)
        if not m:
            return None  # something more complex than a simple key: value
        out.append((m.group(1), m.group(2)))
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args(argv[1:])

    with io.open(args.file, encoding="utf-8-sig") as f:
        text = f.read()

    if "<<<<<<<" not in text:
        print("no conflict markers in %s -- nothing to do" % args.file)
        return 0

    cleaned, conflicts = parse_conflicts(text)
    print("conflict blocks: %d" % len(conflicts))

    merged_keys = []
    for i, (ours, theirs) in enumerate(conflicts):
        ours_e = entries(ours)
        theirs_e = entries(theirs)
        if ours_e is None or theirs_e is None:
            print("  block %d is not a simple key/value append -- refusing" % (i + 1))
            return 1
        print("  block %d: ours=%s"
              % (i + 1, [k for k, _ in ours_e]))
        print("           theirs=%s"
              % [k for k, _ in theirs_e])
        for k, v in ours_e + theirs_e:
            if k not in [mk for mk, _ in merged_keys]:
                merged_keys.append((k, v))

    # rebuild: put the merged keys where the first conflict was, inside the object
    marker = "\n<<<<<<<"
    # the cleaned text lost the conflict, so re-insert before the final closing brace
    idx = cleaned.rstrip().rfind("}")
    insertion = "".join(
        '  "%s": %s%s\n' % (k, v, "," if not last else "")
        for last, (k, v) in zip(
            [False] * (len(merged_keys) - 1) + [True], merged_keys))
    # note: keep the preceding key's comma intact; the merged keys go last
    body = cleaned.rstrip()
    assert body.endswith("}"), "unexpected file shape"
    new_text = body[:-1].rstrip()
    if not new_text.endswith(","):
        new_text += ","
    new_text += "\n" + insertion.rstrip("\n") + "\n}\n"

    # validate
    try:
        data = json.loads(new_text)
    except Exception as exc:
        print("REFUSING: merged result is not valid JSON: %s" % exc)
        return 1
    print("merged result is valid JSON with %d keys" % len(data))
    for k, _v in merged_keys:
        print("  kept %s -> %s" % (k, data.get(k)))

    if not args.apply:
        print("dry run. Re-run with --apply to write it, then continue the rebase.")
        return 0

    with io.open(args.file, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_text)
    print("written: %s" % args.file)
    print("next: git add the file, then 'git rebase --continue'")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
