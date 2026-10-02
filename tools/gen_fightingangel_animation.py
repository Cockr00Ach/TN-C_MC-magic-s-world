# -*- coding: utf-8 -*-
"""
gen_fightingangel_animation.py -- several animations for the author's fightingangel model.

Writes assets/tnc/animations/entity/fightingangel.animation.json (GeckoLib / Bedrock 1.8.0).

Animations (bone names taken from the actual model: robe, body, armRight/armLeft,
armLeft2/3/4, armRight4, head, halo, wingRight/Mid/Tip, wingLeft/Mid/Tip):
    idle    4.0s loop  hovering: breathing bob, slow wing flap, halo spin, robe sway, head sway
    fly     0.8s loop  FLIGHT: hard wing beats, body leaning into the dive, robe trailing back
    walk    1.0s loop  faster bob + wing beat + robe sway   (this model has no legs)
    attack  0.8s       right arm swings down/forward, body lunge, wings snap
    cast    1.6s       both arms raised, halo grows, wings spread wide
    death   1.2s       falls forward, wings droop, halo fades (scale 0)

Keyframes use the plain Bedrock form:  bone -> channel -> { "<time>": [x,y,z] }  (degrees / units)
ASCII only.
"""
import json, math, os

NAME = "fightingangel"
OUT = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity", NAME + ".animation.json")

def kf(pairs):
    return {("%.2f" % t): v for (t, v) in pairs}

def bob(ampl, period, n=5):
    """a sampled sine bob for position channels"""
    return kf([(round(period * i / float(n), 2), [0.0, round(ampl * math.sin(2 * math.pi * i / float(n)), 3), 0.0]) for i in range(n + 1)])

def swing(ampl, period, n=5):
    return kf([(round(period * i / float(n), 2), [0.0, 0.0, round(ampl * math.sin(2 * math.pi * i / float(n)), 3)]) for i in range(n + 1)])

def build():
    anims = {}

    # ---------------- idle (loop) ----------------
    anims["idle"] = {
        "loop": True, "animation_length": 4.0, "bones": {
            "body": {"position": bob(0.5, 4.0), "rotation": swing(1.5, 4.0)},
            "head": {"rotation": swing(2.5, 4.0)},
            "robe": {"rotation": swing(1.0, 4.0)},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -6]), (1.0, [0, 0, -16]), (2.0, [0, 0, -6]), (3.0, [0, 0, -16]), (4.0, [0, 0, -6])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 6]), (1.0, [0, 0, 16]), (2.0, [0, 0, 6]), (3.0, [0, 0, 16]), (4.0, [0, 0, 6])])},
            "wingRightMid": {"rotation": kf([(0.0, [0, 0, -4]), (1.0, [0, 0, -10]), (2.0, [0, 0, -4]), (3.0, [0, 0, -10]), (4.0, [0, 0, -4])])},
            "wingLeftMid": {"rotation": kf([(0.0, [0, 0, 4]), (1.0, [0, 0, 10]), (2.0, [0, 0, 4]), (3.0, [0, 0, 10]), (4.0, [0, 0, 4])])},
            "halo": {"rotation": kf([(0.0, [0, 0, 0]), (2.0, [0, 180, 0]), (4.0, [0, 360, 0])])},
        },
    }

    # ---------------- fly (loop) ----------------
    # 作者 2026-10-02："这个召唤出来的天使要能飞的" => 飞行专用循环：
    # 翅拍幅度/频率都明显大于 idle（-14 <-> -48，一次 0.4s），身体前倾、袍子被风带向后方 ✓
    anims["fly"] = {
        "loop": True, "animation_length": 0.8, "bones": {
            "body": {"position": bob(0.35, 0.8, 4),
                     "rotation": kf([(0.0, [14.0, 0.0, 0.0]), (0.2, [16.0, 0.0, 2.0]),
                                     (0.4, [14.0, 0.0, 0.0]), (0.6, [16.0, 0.0, -2.0]),
                                     (0.8, [14.0, 0.0, 0.0])])},
            "robe": {"rotation": kf([(0.0, [-18.0, 0.0, 0.0]), (0.2, [-21.0, 0.0, 3.0]),
                                     (0.4, [-18.0, 0.0, 0.0]), (0.6, [-21.0, 0.0, -3.0]),
                                     (0.8, [-18.0, 0.0, 0.0])])},
            "head": {"rotation": kf([(0.0, [-8.0, 0.0, 0.0]), (0.4, [-5.0, 0.0, 0.0]), (0.8, [-8.0, 0.0, 0.0])])},
            "halo": {"rotation": kf([(0.0, [0, 0, 0]), (0.8, [0, 360, 0])])},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -14]), (0.2, [0, 0, -48]), (0.4, [0, 0, -14]),
                                          (0.6, [0, 0, -48]), (0.8, [0, 0, -14])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 14]), (0.2, [0, 0, 48]), (0.4, [0, 0, 14]),
                                         (0.6, [0, 0, 48]), (0.8, [0, 0, 14])])},
            "wingRightMid": {"rotation": kf([(0.0, [0, 0, -8]), (0.2, [0, 0, -26]), (0.4, [0, 0, -8]),
                                             (0.6, [0, 0, -26]), (0.8, [0, 0, -8])])},
            "wingLeftMid": {"rotation": kf([(0.0, [0, 0, 8]), (0.2, [0, 0, 26]), (0.4, [0, 0, 8]),
                                            (0.6, [0, 0, 26]), (0.8, [0, 0, 8])])},
            "wingRightTip": {"rotation": kf([(0.0, [0, 0, -4]), (0.2, [0, 0, -14]), (0.4, [0, 0, -4]),
                                             (0.6, [0, 0, -14]), (0.8, [0, 0, -4])])},
            "wingLeftTip": {"rotation": kf([(0.0, [0, 0, 4]), (0.2, [0, 0, 14]), (0.4, [0, 0, 4]),
                                            (0.6, [0, 0, 14]), (0.8, [0, 0, 4])])},
            "armRight": {"rotation": kf([(0.0, [-10, 0, -6]), (0.4, [-15, 0, -9]), (0.8, [-10, 0, -6])])},
            "armLeft": {"rotation": kf([(0.0, [-10, 0, 6]), (0.4, [-15, 0, 9]), (0.8, [-10, 0, 6])])},
        },
    }

    # ---------------- walk (loop) ----------------
    anims["walk"] = {
        "loop": True, "animation_length": 1.0, "bones": {
            "body": {"position": bob(0.9, 1.0, 4), "rotation": swing(3.0, 1.0, 4)},
            "robe": {"rotation": swing(5.0, 1.0, 4)},
            "head": {"rotation": swing(-2.0, 1.0, 4)},
            "armRight": {"rotation": swing(12.0, 1.0, 4)},
            "armLeft": {"rotation": swing(-12.0, 1.0, 4)},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -10]), (0.25, [0, 0, -22]), (0.5, [0, 0, -10]), (0.75, [0, 0, -22]), (1.0, [0, 0, -10])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 10]), (0.25, [0, 0, 22]), (0.5, [0, 0, 10]), (0.75, [0, 0, 22]), (1.0, [0, 0, 10])])},
        },
    }

    # ---------------- attack ----------------
    anims["attack"] = {
        "loop": False, "animation_length": 0.8, "bones": {
            "body": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [12, 0, 0]), (0.55, [-6, 0, 0]), (0.8, [0, 0, 0])])},
            "armRight": {"rotation": kf([(0.0, [0, 0, 0]), (0.25, [-95, 0, 0]), (0.5, [35, 0, 0]), (0.8, [0, 0, 0])])},
            "armLeft": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [30, 0, -20]), (0.8, [0, 0, 0])])},
            "armRight4": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-40, 0, 20]), (0.8, [0, 0, 0])])},
            "armLeft2": {"rotation": kf([(0.0, [0, 0, 0]), (0.35, [20, 0, -15]), (0.8, [0, 0, 0])])},
            "armLeft3": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [15, 0, -10]), (0.8, [0, 0, 0])])},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -8]), (0.25, [0, 0, -40]), (0.8, [0, 0, -8])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 8]), (0.25, [0, 0, 40]), (0.8, [0, 0, 8])])},
            "head": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [10, 0, 0]), (0.8, [0, 0, 0])])},
        },
    }

    # ---------------- cast ----------------
    anims["cast"] = {
        "loop": False, "animation_length": 1.6, "bones": {
            "body": {"position": kf([(0.0, [0, 0, 0]), (0.8, [0, 1.5, 0]), (1.6, [0, 0, 0])]),
                     "rotation": kf([(0.0, [0, 0, 0]), (0.8, [-6, 0, 0]), (1.6, [0, 0, 0])])},
            "armRight": {"rotation": kf([(0.0, [0, 0, 0]), (0.7, [-70, 0, -25]), (1.3, [-70, 0, -25]), (1.6, [0, 0, 0])])},
            "armLeft": {"rotation": kf([(0.0, [0, 0, 0]), (0.7, [-70, 0, 25]), (1.3, [-70, 0, 25]), (1.6, [0, 0, 0])])},
            "armLeft2": {"rotation": kf([(0.0, [0, 0, 0]), (0.8, [-45, 0, 20]), (1.6, [0, 0, 0])])},
            "armLeft3": {"rotation": kf([(0.0, [0, 0, 0]), (0.9, [-30, 0, 15]), (1.6, [0, 0, 0])])},
            "armLeft4": {"rotation": kf([(0.0, [0, 0, 0]), (1.0, [-20, 0, 10]), (1.6, [0, 0, 0])])},
            "armRight4": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-45, 0, -20]), (1.6, [0, 0, 0])])},
            "halo": {"scale": kf([(0.0, [1, 1, 1]), (1.0, [1.35, 1.35, 1.35]), (1.6, [1, 1, 1])]),
                     "rotation": kf([(0.0, [0, 0, 0]), (1.6, [0, 360, 0])])},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -10]), (1.0, [0, 0, -55]), (1.6, [0, 0, -10])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 10]), (1.0, [0, 0, 55]), (1.6, [0, 0, 10])])},
            "wingRightTip": {"rotation": kf([(0.0, [0, 0, 0]), (1.1, [0, 0, -25]), (1.6, [0, 0, 0])])},
            "wingLeftTip": {"rotation": kf([(0.0, [0, 0, 0]), (1.1, [0, 0, 25]), (1.6, [0, 0, 0])])},
        },
    }

    # ---------------- death ----------------
    anims["death"] = {
        "loop": False, "animation_length": 1.2, "bones": {
            "body": {"position": kf([(0.0, [0, 0, 0]), (1.2, [0, -4, 0])]),
                     "rotation": kf([(0.0, [0, 0, 0]), (0.6, [40, 0, 0]), (1.2, [85, 0, 0])])},
            "head": {"rotation": kf([(0.0, [0, 0, 0]), (1.2, [25, 0, 0])])},
            "halo": {"scale": kf([(0.0, [1, 1, 1]), (1.2, [0.05, 0.05, 0.05])])},
            "wingRight": {"rotation": kf([(0.0, [0, 0, -10]), (1.2, [0, 0, 45])])},
            "wingLeft": {"rotation": kf([(0.0, [0, 0, 10]), (1.2, [0, 0, -45])])},
            "armRight": {"rotation": kf([(0.0, [0, 0, 0]), (1.2, [40, 0, 0])])},
            "armLeft": {"rotation": kf([(0.0, [0, 0, 0]), (1.2, [40, 0, 0])])},
        },
    }
    return {"format_version": "1.8.0", "animations": anims}

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    p = os.path.join(repo, OUT)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    d = build()
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, ensure_ascii=False)
        f.write("\n")
    print("animations -> %s (%d bytes)" % (OUT, os.path.getsize(p)))
    for name, a in d["animations"].items():
        print("   %-8s %.2fs loop=%s bones=%d" % (name, a["animation_length"], a.get("loop", False), len(a["bones"])))

if __name__ == "__main__":
    main()