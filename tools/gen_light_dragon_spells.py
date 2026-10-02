# -*- coding: utf-8 -*-
"""
gen_light_dragon_spells.py -- the 5 spell JSONs of the light chain #5 (光龙).

Author 2026-10-02: "然后新增加一条光龙链" (on top of the Eastern light dragon model).

The JSONs are PERFORMANCE ONLY (animation / sound / particles + a small absorption buff):
the real work is done by light/TNLightDragonChain (breath beam / scales buff / RELEASING
charging dragons - the engine's SPAWN action cannot set direction / speed / damage).

Ids / names (tier -> spell):
    1  light_dragon_breath    光龙吐息   forward golden beam (reuses the beam chain's entity)
    2  light_dragon_scales    光龙鳞甲   self+allies: DR 50% / speed +20% / light dmg +30%, 20s
    3  summon_light_dragon    光龙出击   1 dragon that CHARGES forward ~45 blocks (0.30x)
    4  light_dragon_dive      光龙俯冲   2 dragons (0.40x, +-7 deg) charge ~60 blocks + a small sky beam
    5  light_dragon_descend   光龙降世   3 dragons (0.45x, fan +-16 deg) charge ~75 blocks + a big sky beam
ASCII only.
"""
import json
import os

OUT_DIR = os.path.join("src", "main", "resources", "data", "tnc", "spells")

# path, tier, cast duration, cooldown, effect seconds, cast particles, grand?
SPELLS = [
    ("light_dragon_breath",   1, 0.8, 30,  8,  24.0, False),
    ("light_dragon_scales",   2, 1.0, 60,  20, 34.0, False),
    ("summon_light_dragon",   3, 1.2, 90,  45, 44.0, True),
    ("light_dragon_dive",     4, 1.4, 130, 50, 60.0, True),
    ("light_dragon_descend",  5, 1.6, 180, 60, 80.0, True),
]


def build(path, tier, cast_duration, cooldown, effect_seconds, particles, grand):
    return {
        "school": "HEALING",
        "group": "light_dragon",
        "range": 0.0,
        "learn": {"tier": tier},
        "cast": {
            "duration": cast_duration,
            "animation": "spell_engine:two_handed_channeling" if grand
                         else "spell_engine:one_handed_area_charge",
            "sound": {"id": "entity.ender_dragon.growl", "randomness": 0.2},
            "particles": [
                {"particle_id": "end_rod", "shape": "CIRCLE", "origin": "FEET",
                 "count": particles, "min_speed": 0.05, "max_speed": 0.35},
                {"particle_id": "firework", "shape": "SPHERE", "origin": "CENTER",
                 "count": particles * 0.5, "min_speed": 0.2, "max_speed": 0.7},
            ],
        },
        "release": {
            "target": {"type": "SELF"},
            "animation": "spell_engine:one_handed_area_release",
            "sound": {"id": "block.beacon.activate"},
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
                     "count": particles, "min_speed": 0.25, "max_speed": 0.8},
                    {"particle_id": "firework", "shape": "SPHERE", "origin": "CENTER",
                     "count": particles * 0.7, "min_speed": 0.3, "max_speed": 1.0},
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
