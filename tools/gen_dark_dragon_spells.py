# -*- coding: utf-8 -*-
"""
gen_dark_dragon_spells.py -- the 5 spell JSONs of the dark chain (暗龙).

Author 2026-10-02: "复制一下光龙，生成一个暗龙" -> this is the dark copy of
tools/gen_light_dragon_spells.py: same shape, dark school (SOUL), dark particles.

The JSONs are PERFORMANCE ONLY (animation / sound / particles + a small absorption buff):
the real work is done by dark/TNDarkDragonChain (it releases / orbits TNDragonEntity
instances registered as tnc:dark_dragon).

Ids / names (tier -> spell):
    1  dark_dragon_breath     暗龙吐息   one small dragon charges ~54 blocks, 14 dmg
    2  dark_dragon_scales     暗龙鳞甲   3 dragons orbit you + DR 50% / speed +20% / dark dmg +30%, 20s
    3  dark_dragon_charge     暗龙出击   one big dragon (1.5x) charges ~90 blocks, 24 dmg
    4  dark_dragon_dive       暗龙俯冲   2 dragons (3x, +-7 deg) charge ~122 blocks, 34 dmg
    5  dark_dragon_descend    暗龙降世   3 dragons (6x, fan +-16 deg) charge ~155 blocks, 44 dmg
ASCII only.
"""
import json
import os

OUT_DIR = os.path.join("src", "main", "resources", "data", "tnc", "spells")

# path, tier, cast duration, cooldown, effect seconds, particles, grand?
SPELLS = [
    ("dark_dragon_breath",   1, 0.8, 30,  8,  24.0, False),
    ("dark_dragon_scales",   2, 1.0, 60,  20, 34.0, False),
    ("dark_dragon_charge",   3, 1.2, 90,  45, 44.0, True),
    ("dark_dragon_dive",     4, 1.4, 130, 50, 60.0, True),
    ("dark_dragon_descend",  5, 1.6, 180, 60, 80.0, True),
]


def build(path, tier, cast_duration, cooldown, effect_seconds, particles, grand):
    return {
        "school": "SOUL",                      # 暗系 = SOUL ✓（和 night_hand 那批一致 ✓）
        "group": "dark_dragon",
        "range": 0.0,
        "learn": {"tier": tier},
        "cast": {
            "duration": cast_duration,
            "animation": "spell_engine:two_handed_channeling" if grand
                         else "spell_engine:one_handed_area_charge",
            "sound": {"id": "entity.ender_dragon.growl", "randomness": 0.2},
            "particles": [
                {"particle_id": "soul", "shape": "CIRCLE", "origin": "FEET",
                 "count": particles, "min_speed": 0.05, "max_speed": 0.35},
                {"particle_id": "smoke", "shape": "SPHERE", "origin": "CENTER",
                 "count": particles * 0.6, "min_speed": 0.15, "max_speed": 0.6},
            ],
        },
        "release": {
            "target": {"type": "SELF"},
            "animation": "spell_engine:one_handed_area_release",
            "sound": {"id": "entity.wither.spawn"},
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
                    {"particle_id": "soul_fire_flame", "shape": "SPHERE", "origin": "CENTER",
                     "count": particles, "min_speed": 0.25, "max_speed": 0.8},
                    {"particle_id": "smoke", "shape": "SPHERE", "origin": "CENTER",
                     "count": particles * 0.8, "min_speed": 0.3, "max_speed": 1.0},
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
