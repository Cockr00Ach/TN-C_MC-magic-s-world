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
BLACK_FLAME = "soulsweapons:black_flame"       # SimpleParticleType ✓
#
# ⚠⚠ `fromtheshadows:shadow` AND `minecraft:smoke` WERE HERE -- NOW BANNED (2026-10-09)
#     Author: "为什么放以伤换伤也有雾" (why does trade-wounds also produce fog).
#     Because this chain spawned shadow/smoke on cast, and those two ids ARE the black
#     fog chain's material. A blood sacrifice must never read as fog.
#     This chain is blood + soul fire + embers ONLY; the fog ids sit in FORBIDDEN below
#     so the generator refuses to write them again.
FORBIDDEN = {"fromtheshadows:shadow", "minecraft:smoke", "minecraft:large_smoke",
             # the assets-only block_factorys_bosses ids that crash the client:
             "block_factorys_bosses:ink_fog", "block_factorys_bosses:fog_1",
             "block_factorys_bosses:mist_cloud"}


def batch(pid, shape, origin, count, mn, mx):
    if pid in FORBIDDEN:
        raise AssertionError("forbidden particle in the sacrifice chain: %s" % pid)
    return {"particle_id": pid, "shape": shape, "origin": origin,
            "count": float(count), "min_speed": float(mn), "max_speed": float(mx)}


def cast_fx(tier):
    """The caster cuts himself open: **blood only** -- clean, no ghost fire.

    ★ 2026-10-10（作者："血爪的特效要干净啊，把鬼火去掉"）：本链原来撒
    `minecraft:soul_fire_flame`（灵魂火＝鬼火）+ `soulsweapons:black_flame`（黑焰），
    在血里混着两团火，看着很杂 ✗ ⇒ **两个都撤掉**，只留血 ✓。
    """
    return [
        batch(BLOOD, "CIRCLE", "FEET", 14 + 4 * tier, 0.1, 0.5),
        batch(BLOOD, "SPHERE", "CENTER", 10 + 3 * tier, 0.05, 0.3),
    ]


def heal_action(coefficient):
    """HEAL, cast on self -- the only way this chain gets its blood back."""
    return {
        "action": {
            "type": "HEAL",
            "apply_to_caster": True,
            "heal": {"spell_power_coefficient": coefficient},
        },
        "particles": [batch(BLOOD, "SPHERE", "CENTER", 24.0, 0.1, 0.5)],
    }


def drain_action():
    """★ The LIFESTEAL settlement (author 2026-10-09).

    The engine's actions cannot do either half of what the author asked for:
    "damage the target for a PERCENTAGE of its max health" and "give the reward to the
    CASTER" are both outside DAMAGE/HEAL/STATUS_EFFECT/FIRE/SPAWN/TELEPORT. But the
    engine does call `SpellSpawnedEntity.onCreatedFromSpell(caster, spellId, spawn)` on
    whatever a SPAWN action creates -- so a SPAWN of our own `tnc:drain` entity is the
    hook that hands us the caster at the exact moment of impact. The entity does the
    drain + mana refund + max-health buff and discards itself (see
    src/main/java/com/tnc/tnc/magic/TNDarkDrainEntity.java, whose STATS table holds the
    per-tier numbers). Nothing about the visual changes: it is invisible, the blood
    particles come from this impact entry and from the entity's own burst.
    """
    return {
        "action": {
            "type": "SPAWN",
            "spawn": {
                "entity_type_id": "tnc:drain",
                "time_to_live_seconds": 1,
                "delay_ticks": 0,
            },
        },
        "particles": [batch(BLOOD, "CIRCLE", "FEET", 16.0, 0.15, 0.6)],
    }

def projectile_target(tier):
    """★ 2026-10-10: the chain becomes a HOMING projectile ("索敌").

    Author: "以伤换伤貌似还没有索敌效果，我希望有索敌".

    The real reason it had none: all five spells were `release.target: SELF` -- they
    never flew at anything, they just buffed/burned the caster. Turning them into a
    blood claw that homes onto a victim is what makes "从敌人身上抽血" possible at all;
    the drain settlement (`tnc:drain`) already lands on whatever the projectile hits.

    Numbers:
        velocity 0.75        slower than the night-hand claw (0.45-0.55) is for "fly long";
                             this one should reach the target, so 0.75 reads as a lunge.
        homing_angle         0.35 flat + 0.06 per tier => t1 0.41 .. t5 0.65 (radians/s),
                             so higher tiers turn harder and miss less.
        model.scale          0.55 + 0.15 * tier => t1 0.70 .. t5 1.30. NOT the night-hand
                             sizes: this is a claw, not the giant hand.
        orientation          TOWARDS_CAMERA (same reason as the night-hand chain: without
                             it the engine tumbles the model and the wrist leads half the
                             time -- the author's "手腕对着别人" complaint).
    The model reuses `tnc:projectile/dark_hand` (an open grasping claw, regenerated
    2026-10-10). Ask the author if he wants a separate blood-claw model.
    """
    return {
        "type": "PROJECTILE",
        "projectile": {
            "launch_properties": {
                "velocity": 0.75,
                "extra_launch_count": 0,
                "extra_launch_delay": 3,
            },
            "projectile": {
                # ★ 2026-10-10（作者："不是范围我直接开技能然后他自己去找敌人，是要玩家瞄住敌人的
                #   那种"）：引擎的 homing_angle 语义是"**自己去找最近的敌人**" ⇒ 会把
                #   "没瞄准也照样打中"做出来 ✗。所以把它**调小**（原来 0.41~0.65），
                #   让没锁定的血爪基本走直线；真正"追我瞄的那一只"由
                #   `TNDarkAimMechanics` 的 setFollowedTarget 负责 ✓。
                #   ⚠ 这仍是"没锁定时"的兜底转向；要让没瞄准时**完全**不追，把它设 0.0 ✓。
                "homing_angle": round(0.16 + 0.03 * tier, 3),
                "client_data": {
                    # ★ "一条血线"：只留血，且**速度为 0** ⇒ 粒子留在原地，
                    #   血爪飞过去就**拉出一条连续的线** ✓（原来混着鬼火，很杂 ✗）
                    "travel_particles": [
                        batch(BLOOD, "CIRCLE", "CENTER", 20.0 + 4 * tier, 0.0, 0.0),
                    ],
                    "model": {
                        "model_id": "tnc:projectile/dark_hand",
                        "scale": round(0.55 + 0.15 * tier, 2),
                        "orientation": "ALONG_MOTION",
                        "rotate_degrees_offset": 0.0,
                    },
                },
            },
        },
    }


def rewrite(spell, effect_id, heal_coef):
    tier = int(spell["learn"]["tier"])

    spell["cast"]["particles"] = cast_fx(tier)
    # ★ the chain now flies at the enemy instead of buffing only the caster
    spell["release"]["target"] = projectile_target(tier)

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
        # ★ 命中特效也**只留血**（作者 2026-10-10："血爪的特效要干净啊，把鬼火去掉"）✓
        impact["particles"] = [batch(BLOOD, "CIRCLE", "FEET", 14.0 + 4 * tier, 0.15, 0.5)]
        kept.append(impact)
    assert swapped == 1, "expected exactly one dark-sacrifice effect in %s" % effect_id
    assert all("action" in i for i in kept), "impact without action -> engine NPE on every cast"

    # the lifesteal settlement: drop any previous one (idempotent), then append
    kept = [i for i in kept if (i["action"] or {}).get("spawn", {}).get("entity_type_id") != "tnc:drain"]
    kept.append(drain_action())

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
