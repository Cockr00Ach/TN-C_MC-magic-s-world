# -*- coding: utf-8 -*-
"""
check_fire_wired.py  --  is the fire spell chain actually wired up in the game jar?

WHY
===
"A spell json exists in the repo" is not "the spell is castable". For a TN-C spell
to work in game, several separate places must agree:
  * the spell definition is in the jar (data/tnc/spells/*.json)
  * it is listed in the wand assignment / spell pool (otherwise no hotbar slot)
  * any custom model/particle texture it names is in the jar
  * any custom entity class it needs is in the jar

This script checks those for the fire chain and prints a per-part verdict. Read-only.

USAGE
=====
    python tools/check_fire_wired.py
"""

import io
import json
import os
import re
import sys
import zipfile

JAR = os.path.join("build", "libs", "tnc-1.0.0.jar")
FIRE_HINT = re.compile(r"fire|flame|lava|meteor|molten|scorch|burn|ember", re.I)


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    repo = repo_root()
    jar_path = os.path.join(repo, JAR)
    if not os.path.isfile(jar_path):
        print("ERROR: no jar at %s" % jar_path)
        return 1

    with zipfile.ZipFile(jar_path) as z:
        names = set(z.namelist())

        spells = sorted(n for n in names
                        if n.startswith("data/tnc/spells/") and n.endswith(".json"))
        fire_spells = [n for n in spells if FIRE_HINT.search(os.path.basename(n))]
        print("jar: %s" % jar_path)
        print("")
        print("== spell definitions in the jar ==")
        print("   total spells: %d" % len(spells))
        for n in fire_spells:
            print("   fire: %s" % os.path.basename(n))

        # what do the fire spells reference?
        needed_models, needed_textures, needed_entities = set(), set(), set()
        for n in fire_spells:
            data = json.loads(z.read(n).decode("utf-8-sig"))
            blob = json.dumps(data)
            for m in re.finditer(r'"(?:model_id|model)"\s*:\s*"([^"]+)"', blob):
                needed_models.add(m.group(1))
            for m in re.finditer(r'"particle_id"\s*:\s*"([^"]+)"', blob):
                needed_entities.add("particle:" + m.group(1))
        print("")
        print("== referenced models / particles ==")
        missing = []
        for mid in sorted(needed_models):
            path = "assets/%s/models/%s.json" % tuple(mid.split(":", 1)) if ":" in mid else mid
            ok = path in names
            print("   [%s] %s -> %s" % ("ok" if ok else "XX", mid, path))
            if not ok:
                missing.append(path)
        for pid in sorted(needed_entities):
            print("   [--] %s (particles are registry entries, not files)" % pid)

        print("")
        print("== wand assignment / pool ==")
        for n in sorted(x for x in names
                        if x.startswith("data/tnc/spell_assignments/")
                        or x.startswith("data/tnc/spell_pools/")):
            raw = z.read(n).decode("utf-8-sig", "replace")
            # these files are not always strict JSON (some are SNBT-ish), so fall back
            # to a plain text scan instead of failing the whole check
            try:
                blob = json.dumps(json.loads(raw))
                shape = "json"
            except Exception:
                blob = raw
                shape = "text"
            listed = [os.path.basename(f).replace(".json", "")
                      for f in fire_spells if os.path.basename(f).replace(".json", "") in blob]
            print("   %-46s [%s] fire spells listed: %s"
                  % (n, shape, listed if listed else "NONE"))

        print("")
        print("== fire textures in the jar ==")
        tex = sorted(n for n in names
                     if n.startswith("assets/tnc/textures/") and FIRE_HINT.search(n))
        for n in tex:
            print("   %s" % n)

        print("")
        print("== custom entity classes for fire ==")
        cls = sorted(n for n in names
                     if n.startswith("com/tnc/tnc/") and n.endswith(".class")
                     and FIRE_HINT.search(os.path.basename(n)))
        for n in cls:
            print("   %s" % n)
        if not cls:
            print("   (none -- fire chain may reuse engine entities)")

    print("")
    if missing:
        print("MISSING (%d): %s" % (len(missing), missing))
        return 1
    print("all referenced model files are present in the jar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
