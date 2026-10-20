# -*- coding: utf-8 -*-
"""Single source of truth for "which dungeon mobs exist": the Java enum.

Three tools used to keep their own 12-id list and they had already drifted once (a new mob
would generate assets and then be silently skipped by the checker / renderer). Reading the
enum means adding a species in one place is enough.

The ids live in ``DungeonSpecies.java`` as ``CONSTANT ("some_id", Kind.X, ...)``; only the
part of the file before the nested ``Kind`` enum is scanned.
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPECIES_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "tnc", "tnc", "dungeon",
                            "DungeonSpecies.java")
_PATTERN = re.compile(r'^\s*[A-Z][A-Z0-9_]*\s*\(\s*"([a-z][a-z0-9_]*)"', re.M)


def ids():
    with open(SPECIES_JAVA, encoding="utf-8") as fh:
        src = fh.read()
    head = src.split("public enum Kind", 1)[0]
    found = _PATTERN.findall(head)
    if not found:
        raise SystemExit("在 %s 里没找到任何物种 id" % SPECIES_JAVA)
    return found


if __name__ == "__main__":
    for i, mob in enumerate(ids(), 1):
        print("%2d. %s" % (i, mob))
