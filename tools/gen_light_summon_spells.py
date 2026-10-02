# -*- coding: utf-8 -*-
"""
gen_light_summon_spells.py -- the 5 spell JSONs of the light chain #4 (summon_angel).

Author 2026-10-02: "光法的第四个链,召唤天使" -> fighting angel is the summon.
The JSONs are PERFORMANCE ONLY (animation / sound / particles + a tiny absorption buff):
the actual angels are spawned by light/TNLightChainMechanics.castSummon (it needs
tier / owner / lifetime / hp / damage, none of which the engine's SPAWN action can set).

Ids / names (tier -> spell):
    1  summon_angel      召唤天使       1 angel, 30s, CD 30
    2  angel_twins       天使双卫       2 angels, 30s, CD 40
    3  angel_legion      天使军团       3 angels, 40s, CD 60
    4  seraph_descent    炽天使降临     3 angels, 45s, CD 90
    5  archangel         大天使长       2 angels, 60s, CD 140
ASCII only.
"""
import json, os

OUT_DIR = os.path.join("src", "main", "resources", "data", "tnc", "spells")

# path, tier, cast duration, cooldown, effect seconds (== angel lifetime), cast particles
SPELLS = [
    ("summon_angel",   1, 1.0, 30, 30, 28.0),
    ("angel_twins",    2, 1.0, 40, 30, 36.0),
    ("angel_legion",   3, 1.2, 60, 40, 48.0),
    ("seraph_descent", 4, 1.4, 90, 45, 60.0),
    ("archangel",      5, 1.6, 140, 60, 80.0),
]


def build(path, tier, cast_duration, cooldown, effect_seconds, cast_particles):
    grand = tier >= 4
    return {
        "school": "HEALING",
        "group": "light_summon",
        "range": 0.0,
        "learn": {"tier": tier},
        "cast": {
            "duration": cast_duration,
            "animation": "spell_engine:two_handed_channeling" if grand
                         else "spell_engine:one_handed_area_charge",
            "sound": {"id": "block.beacon.activate", "randomness": 0},
            "particles": [
                {"particle_id": "end_rod", "shape": "CIRCLE", "origin": "FEET",
                 "count": cast_particles, "min_speed": 0.05, "max_speed": 0.3},
            ],
        },
        "release": {
            "target": {"type": "SELF"},
            "animation": "spell_engine:one_handed_area_release",
            "sound": {"id": "block.amethyst_block.chime"},
        },
        "impact": [
            {
                "action": {
                    "type": "STATUS_EFFECT",
                    "status_effect": {
                        "effect_id": "minecraft:absorption",
                        "duration": effect_seconds,
                        "amplifier": 1 if grand else 0,
                        "apply_mode": "SET",
                        "show_particles": False,
                    },
                },
                "particles": [
                    {"particle_id": "end_rod", "shape": "SPHERE", "origin": "CENTER",
                     "count": cast_particles, "min_speed": 0.2, "max_speed": 0.7},
                    {"particle_id": "firework", "shape": "SPHERE", "origin": "CENTER",
                     "count": cast_particles * 0.6, "min_speed": 0.25, "max_speed": 0.8},
                ],
            }
        ],
        "cost": {"exhaust": 0.0, "cooldown_duration": cooldown},
    }


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(repo, OUT_DIR)
    os.makedirs(out, exist_ok=True)
    for spec in SPELLS:
        path = os.path.join(out, spec[0] + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(build(*spec), f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("spell -> %s.json (%d bytes)" % (spec[0], os.path.getsize(path)))


if __name__ == "__main__":
    main()
