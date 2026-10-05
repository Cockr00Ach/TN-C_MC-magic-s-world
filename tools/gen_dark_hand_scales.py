# -*- coding: utf-8 -*-
"""
gen_dark_hand_scales.py -- set the five NIGHT HAND sizes to the author's numbers.

Author 2026-10-09: "黑夜之手这个魔法，t2体积增加2倍，t3五倍，t410倍，t520倍"
(night hand's projectile volume: t2 x2, t3 x5, t4 x10, t5 x20).

HOW THE VOLUME NUMBERS CONVERT TO `scale`
    A `scale` in the spell json multiplies every dimension, so the VOLUME grows with
    the CUBE:
        volume_multiplier = (new_scale / base_scale) ** 3
        new_scale         = base_scale * volume_multiplier ** (1/3)
    The chain's model (tnc:projectile/dark_hand) is the same hand for all five tiers,
    so the base is tier 1's current scale, 0.85:
        t2  x2   -> 0.85 * 1.2599 = 1.07
        t3  x5   -> 0.85 * 1.7100 = 1.45
        t4  x10  -> 0.85 * 2.1544 = 1.83
        t5  x20  -> 0.85 * 2.7144 = 2.30
    Sizes in blocks (model is 1.12 x 0.56 x 1.88 at scale 1):
        t1 0.95, t2 1.20, t3 1.63, t4 2.05, t5 2.58 blocks long.

⚠ If the author meant the EDGE length x2/x5/x10/x20 instead of the volume, the scale
  numbers are just 0.85 * those multipliers (t5 would then be 17 -> ~32 blocks long,
  bigger than two chunks). Say the word and it is a one-line change here.

ASCII only. Usage: python tools/gen_dark_hand_scales.py
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# ⚠ the NIGHT HAND chain lives in the MOD JAR, not in kubejs:
#   tools/.../6b29d3af moved every model-bearing spell into
#   src/main/resources/data/tnc/spells/ because a kubejs copy of the same id wins
#   and silently reverts the spell (that is how the model_id was lost once).
SPELL_DIR = os.path.join(REPO, "src", "main", "resources", "data", "tnc", "spells")

BASE_SCALE = 0.85                      # tier 1, the chain's reference size
# chain order: night_hand -> night_raid -> night_embrace -> black_ruin -> slay_light
VOLUMES = {
    "night_hand": 1.0,                 # t1 unchanged
    "night_raid": 2.0,
    "night_embrace": 5.0,
    "black_ruin": 10.0,
    "slay_light": 20.0,
}


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1
    problems = 0
    for name, volume in VOLUMES.items():
        path = os.path.join(SPELL_DIR, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue
        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)
        model = spell["release"]["target"]["projectile"]["projectile"]["client_data"]["model"]
        if model.get("model_id") != "tnc:projectile/dark_hand":
            print("SKIP %s: model_id is %s (this script only owns the night hand chain)"
                  % (name, model.get("model_id")))
            problems += 1
            continue
        old = float(model["scale"])
        new = round(BASE_SCALE * (volume ** (1.0 / 3.0)), 2)
        model["scale"] = new
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print("%-16s tier=%d volume x%-5s scale %s -> %-5s (%.2f blocks long)"
              % (name, spell["learn"]["tier"], volume, old, new, 1.88 * new))
    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
