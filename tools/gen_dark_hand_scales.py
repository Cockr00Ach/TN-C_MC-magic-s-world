# -*- coding: utf-8 -*-
"""
gen_dark_hand_scales.py -- set the five NIGHT HAND sizes to the author's numbers.

Author 2026-10-09 (second pass): "手的模型的大小我记得我调整过，怎么没变化，那你再调整一下，
                    t2 放大两倍，t3 5倍，t4 10倍，t5 20倍"

WHY THE PREVIOUS PASS LOOKED LIKE IT DID NOTHING -- two separate reasons
    1. The first version of this script read the numbers as VOLUME multipliers:
           new_scale = 0.85 * multiplier ** (1/3)
       => 1.07 / 1.45 / 1.83 / 2.31, i.e. only 1.26x .. 2.71x per dimension. Hard to see,
       and not what "放大两倍" means. THIS version applies them literally as a size
       (edge) multiplier:
           new_scale = 0.85 * multiplier      => 1.70 / 4.25 / 8.50 / 17.00
    2. These five spells live in the MOD JAR (commit 6b29d3af moved every model-bearing
       spell there, because a kubejs copy of the same id silently wins over the jar).
       Editing a copy inside the game instance does nothing, and `sync.cmd push` cannot
       reach them either. The only path is:
           python tools/gen_dark_hand_scales.py  ->  gradlew build  ->  install-to-pack

SIZES (model is 1.12 x 0.56 x 1.88 blocks at scale 1)
    t1 0.85 ->  1.6 blocks long
    t2 1.70 ->  3.2
    t3 4.25 ->  8.0
    t4 8.50 -> 16.0
    t5 17.0 -> 32.0    <-- wider than two chunks

⚠ ABOUT THE VERY LARGE TIERS: the engine draws a projectile through the vanilla renderer,
  which culls by bounding box, so at scale 17 the hand can vanish while its centre is
  off screen. If the author reports "飞到一半就没了", the fix is either our own
  `getBoundingBoxForCulling` on the projectile or smaller numbers here -- both are
  one-line changes, but ask before guessing.

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
MODEL_PATH = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                          "models", "projectile", "dark_hand.json")

# chain order: night_hand -> night_raid -> night_embrace -> black_ruin -> slay_light
# ★ the SIZE INTENT, in blocks long (t1 as originally shipped, then x2 / x5 / x10 / x20)
INTENT_BLOCKS = {
    "night_hand": 1.60,
    "night_raid": 3.20,
    "night_embrace": 8.00,
    "black_ruin": 16.00,
    "slay_light": 32.00,
}


def model_length_blocks():
    """How long the CURRENT hand model is along its travel axis, in blocks.

    ★ 2026-10-10: the model was reworked (open grasp) and became longer: 1.88 -> 2.50
    model-blocks. `scale` multiplies the model, so keeping the old scale numbers would
    have silently made every tier ~33% bigger than intended. Deriving `scale` from the
    model's real length keeps the author's per-tier sizes correct across model edits.
    """
    with open(MODEL_PATH, "r", encoding="utf-8-sig") as fh:
        model = json.load(fh)
    zs = [v for e in model["elements"] for v in (e["from"][2], e["to"][2])]
    return (max(zs) - min(zs)) / 16.0


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1
    length = model_length_blocks()
    print("current dark_hand model length: %.2f blocks (at scale 1.0)" % length)
    problems = 0
    for name, want_blocks in INTENT_BLOCKS.items():
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
        new = round(want_blocks / length, 2)
        model["scale"] = new
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print("%-16s tier=%d  want %5.1f blocks  scale %-6s -> %-6s  (actual %5.2f blocks)"
              % (name, spell["learn"]["tier"], want_blocks, old, new, length * new))
    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
