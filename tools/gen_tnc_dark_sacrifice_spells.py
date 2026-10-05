# -*- coding: utf-8 -*-
"""
gen_tnc_dark_sacrifice_spells.py -- rewire the DARK SACRIFICE chain ("以伤换伤 · 献祭").

Author 2026-10-09: "你还可以再优化一下以伤换伤" (you can optimise trade-wounds too).

THE BUG THIS FIXES (measured, not guessed)
    All five spells shipped the FIRE burn line's effects as their "cost":
        trade_wounds -> tnc:fire_aspect
        blood_burn   -> tnc:ember_burn
        sacrifice    -> tnc:blaze_burn
        possess      -> tnc:inferno_burn
        i_am_god     -> tnc:total_burn
    Those add `spell_power:fire`, so a DARK spell gets nothing from them, and:
      * `tnc:fire_aspect` is the fire line's NO-BURN tier, so tier 1 cost nothing at all;
      * `tnc:total_burn` locks health at 1 AND grants invulnerability while adding a fire
        bonus worth exactly 0 to a dark spell => tier 5 was a free "god mode";
      * the burn mechanic drains HP only for ember/blaze/inferno, i.e. it did fire the
        health drain, but converted it into a stat the dark chain cannot use.
    Net effect: the cost was real for t2-t4 but the reward was zero; t1 and t5 had no cost.

WHAT IT WRITES NOW
    * tnc:blood_mark / blood_burn / blood_sacrifice / blood_possess / blood_god
      (registered in TNEffects, each adds spell_power:soul => the reward is real)
      and the burn side is settled by magic/TNDarkSacrificeMechanics, the same
      never-lethal rule the fire line uses (floor of 1 HP).
    * a HEAL action per tier: the chain has to have a way to get the blood back,
      otherwise it is just a slow suicide button. Heal = spell_power:soul coefficient
      (5% / 8% / 12% / 18% / 30%), cast on SELF.
    * a shared summon-flavoured visual: blood particles instead of generic soul fire.

NOT TOUCHED: school / group / range / learn.tier / cooldowns / cast times /
the five spell ids. Those are the author's numbers.

ASCII only. Usage: python tools/gen_tnc_dark_sacrifice_spells.py
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_NAME = "元素觉醒1.4.3-魔改版-20260915"
SPELL_DIR = os.path.join(REPO, "modpack", PACK_NAME, "kubejs", "data", "tnc", "spells")

# old fire effect -> new dark effect (same tier, soul power instead of fire power)
OLD_TO_NEW = {
    "tnc:fire_aspect": "tnc:blood_mark",
    "tnc:ember_burn": "tnc:blood_burn",
    "tnc:blaze_burn": "tnc:blood_sacrifice",
    "tnc:inferno_burn": "tnc:blood_possess",
    "tnc:total_burn": "tnc:blood_god",
}

# per spell: (new effect id, heal coefficient, extra flavour batches)
SPELLS = {
    "trade_wounds": ("tnc:blood_mark", 0.05),
    "blood_burn": ("tnc:blood_burn", 0.08),
    "sacrifice": ("tnc:blood_sacrifice", 0.12),
    "possess": ("tnc:blood_possess", 0.18),
    "i_am_god": ("tnc:blood_god", 0.30),
}

# "blood" particles that are REAL particle types in this pack (checked with javap
# against each mod's registry -- see the PARTICLE SAFETY note in
# tools/gen_dark_fog_spells.py: a json under assets/<ns>/particles/ proves nothing,
# and a bad id crashes the client when the engine casts it).
BLOOD = "fromtheshadows:blood"                 # SimpleParticleType ✓
SOUL_FIRE = "minecraft:soul_fire_flame"        # vanilla ✓
SHADOW = "fromtheshadows:shadow"               # SimpleParticleType ✓ (dark vapour)
# ⚠ block_factorys_bosses:ink_fog used to be here -- it is a BEDROCK particle there,
#   not a ParticleType, and looking it up crashes SpellEngine with a ClassCastException.
BLACK_FLAME = "soulsweapons:black_flame"       # SimpleParticleType ✓


def batch(pid, shape, origin, count, mn, mx):
    return {"particle_id": pid, "shape": shape, "origin": origin,
            "count": float(count), "min_speed": float(mn), "max_speed": float(mx)}


def cast_fx(tier):
    """The caster cuts himself open: a ring of blood at the feet, soul fire licking up."""
    return [
        batch(BLOOD, "CIRCLE", "FEET", 10 + 4 * tier, 0.1, 0.5),
        batch(SOUL_FIRE, "CIRCLE", "FEET", 12 + 4 * tier, 0.1, 0.4),
        batch(SHADOW, "SPHERE", "CENTER", 10 + 3 * tier, 0.05, 0.3),
    ]


def heal_action(coefficient):
    """HEAL, cast on self -- the only way this chain gets its blood back."""
    return {
        "action": {
            "type": "HEAL",
            "apply_to_caster": True,
            "heal": {"spell_power_coefficient": coefficient},
        },
        "particles": [batch(BLOOD, "SPHERE", "CENTER", 24.0, 0.1, 0.5),
                      batch(SOUL_FIRE, "CIRCLE", "FEET", 16.0, 0.05, 0.3)],
    }


def rewrite(spell, effect_id, heal_coef):
    tier = int(spell["learn"]["tier"])

    spell["cast"]["particles"] = cast_fx(tier)

    # swap the borrowed fire effect for the dark one, keep the JSON's own duration/amplifier
    swapped = 0
    kept = []
    for impact in spell.get("impact", []):
        action = impact.get("action")
        if not isinstance(action, dict):
            continue
        if action.get("type") == "STATUS_EFFECT":
            current = (action.get("status_effect") or {}).get("effect_id")
            if current in OLD_TO_NEW:
                action["status_effect"]["effect_id"] = OLD_TO_NEW[current]
                swapped += 1
            elif current and current.startswith("tnc:blood_"):
                swapped += 1                      # already migrated (idempotent re-run)
        # every remaining impact gets a blood-flavoured particle set
        impact["particles"] = [batch(BLOOD, "CIRCLE", "FEET", 10.0 + 3 * tier, 0.1, 0.4),
                               batch(SOUL_FIRE, "CIRCLE", "FEET", 8.0 + 2 * tier, 0.05, 0.3)]
        kept.append(impact)
    assert swapped == 1, "expected exactly one dark-sacrifice effect in %s" % effect_id
    assert all("action" in i for i in kept), "impact without action -> engine NPE on every cast"

    # the heal: drop any previous one (idempotent), then append
    kept = [i for i in kept if i["action"].get("type") != "HEAL"]
    kept.append(heal_action(heal_coef))
    spell["impact"] = kept
    return spell


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1
    problems = 0
    for name, (effect_id, heal_coef) in SPELLS.items():
        path = os.path.join(SPELL_DIR, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue
        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)
        before = json.dumps(spell, sort_keys=True)
        spell = rewrite(spell, effect_id, heal_coef)

        # sanity: the knobs we must not have broken
        effects = [i["action"]["status_effect"]["effect_id"] for i in spell["impact"]
                   if i["action"].get("type") == "STATUS_EFFECT"]
        assert effect_id in effects, "%s lost its %s" % (name, effect_id)
        assert "tnc:dark_power" in effects, "%s lost dark_power" % name
        assert not any(e.startswith("tnc:fire_") or e in OLD_TO_NEW for e in effects), \
            "%s still borrows a fire effect: %s" % (name, effects)

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        changed = before != json.dumps(spell, sort_keys=True)
        print("%-14s tier=%d -> %-22s heal=%.2f impacts=%d %s"
              % (name, spell["learn"]["tier"], effect_id, heal_coef, len(spell["impact"]),
                 "updated" if changed else "unchanged"))
    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
