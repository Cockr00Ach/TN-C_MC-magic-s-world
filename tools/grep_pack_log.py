# -*- coding: utf-8 -*-
"""Scan the live pack's latest.log for the mod's own messages / renderer crashes."""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
inst = json.load(open(os.path.join(HERE, "tnc_instance.json"), encoding="utf-8"))["instance"]
path = os.path.join(inst, "logs", "latest.log")
pat = re.compile(sys.argv[1] if len(sys.argv) > 1 else r"dragon|TN-C|geckolib", re.I)
n = 0
with open(path, encoding="utf-8", errors="replace") as f:
    for line in f:
        if pat.search(line):
            sys.stdout.buffer.write(line.rstrip("\n").encode("utf-8", "replace") + b"\n")
            n += 1
            if n > 4000:
                break
print("---- matches:", n)
