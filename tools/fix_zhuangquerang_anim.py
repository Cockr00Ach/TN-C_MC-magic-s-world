# -*- coding: utf-8 -*-
"""
fix_zhuangquerang_anim.py  --  one-off cleanup for the exported maid-model animations.

WHAT IT DOES
------------
1. Renames the animation whose name picked up a stray ideographic comma:
       "confusing\u3001"  ->  "confusing"
   (GeckoLib can technically carry any name, but our @act marker + the
    NpcAnimationTrigger guard only accept short ASCII words on purpose, and a
    name nobody can type is a name nobody can call.)
2. Sets  "loop": false  on every action animation, keeping idle looping.
   GeckoLib's thenPlayXTimes(name, 2) needs loop=false to be able to count plays.

Pure ASCII script (repo rule). Chinese docs live in docs/.
"""

import io
import json
import os
import sys

TARGET = os.path.join("src", "main", "resources", "assets", "tnc",
                      "animations", "entity", "zhuangquerang.animation.json")

WRONG = "confusing\u3001"   # confusing + IDEOGRAPHIC COMMA
RIGHT = "confusing"


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(repo, TARGET)
    if not os.path.isfile(path):
        print("ERROR: not found: %s" % path)
        return 1

    text = io.open(path, encoding="utf-8").read()
    if WRONG in text:
        text = text.replace('"%s"' % WRONG, '"%s"' % RIGHT)
        io.open(path, "w", encoding="utf-8").write(text)
        print("renamed animation key: %r -> %r" % (WRONG, RIGHT))
    else:
        print("no stray key found (already fixed)")

    data = json.load(io.open(path, encoding="utf-8"))
    changed = []
    for name, anim in data.get("animations", {}).items():
        if name == "idle":
            continue
        if anim.get("loop") is not False:
            anim["loop"] = False
            changed.append(name)
    io.open(path, "w", encoding="utf-8").write(
        json.dumps(data, ensure_ascii=False, indent="\t") + "\n")
    print("set loop=false on: %s" % (", ".join(changed) if changed else "(nothing)"))

    print("")
    for name, anim in data["animations"].items():
        print("  %-14s loop=%-5s length=%s bones=%s"
              % (name, anim.get("loop"), anim.get("animation_length"),
                 ",".join(anim.get("bones", {}).keys())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
