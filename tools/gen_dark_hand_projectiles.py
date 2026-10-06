# -*- coding: utf-8 -*-
"""
gen_dark_hand_projectiles.py -- the night-hand projectile behaviour the author asked for.

Author 2026-10-10:
    "手的方向是不是有点错了，好像是手腕对着别人不是手指"
    "我希望你把手掌张开也就是弄成要去抓住别人的感觉，然后掌心对着别人冲过去，
     有点像下界铁掌这个boss的形状"
    "然后手掌可以飞久一点，并且携带黑粒子"

WHAT THIS CHANGES (per tier, all five share the same model)
    1. `model.orientation = "TOWARDS_CAMERA"`
       ★ This is the fix for "手腕对着别人". The five spells had NO orientation field,
       and the engine's default then TUMBLES the model about the entity position
       (the same trap the `flash` lightning hit on 2026-09-27: "怎么雷电是躺着的").
       With the hand tumbling, whichever end happens to point at the victim is random --
       so the wrist leads about as often as the fingers do.
       TOWARDS_CAMERA is the one variant that only yaws the model (no pitch/roll), so
       "fingers along +Z, palm down" is preserved frame to frame.
       (The other two: TOWARDS_MOTION points the model's +Z at the viewer's camera,
        ALONG_MOTION rotates +Z onto the motion vector and adds a fixed yaw offset --
        see docs/黑夜之手_模型-20261002.md and the projectile-model recipe.)

    2. `model.rotate_degrees_per_tick` left unset (0): a spinning hand reads as debris,
       not as a reaching claw. The dome/fog still rotates; the hand does not.

    3. FLIGHT TIME x2.2: `launch_properties.velocity` 1.3/1.3/1.2/1.2/1.1 -> ~0.5,
       and `range` 34 -> 48 so it may actually travel that long. Slower + longer is
       what "飞久一点" needs; the engine retires a projectile when it leaves `range`.
       Scale is untouched: the author set the per-tier sizes explicitly on 2026-10-09.

    4. `travel_particles`: the hand now carries a DARK trail (squid_ink + smoke +
       fromtheshadows:shadow, all verified ParticleTypes) instead of a single soul puff.

NOT TOUCHED: range-per-tier ordering, homing_angle (the chain already homes at 0.5 rad,
    tapering with distance), scale, impact/area_impact, ids, cooldowns.

ASCII only. Usage: python tools/gen_dark_hand_projectiles.py
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# the night-hand chain lives in the MOD JAR (a kubejs copy of the same id would win and
# silently revert the spell -- that is why 6b29d3af moved every model-bearing spell here)
SPELL_DIR = os.path.join(REPO, "src", "main", "resources", "data", "tnc", "spells")

CHAIN = ["night_hand", "night_raid", "night_embrace", "black_ruin", "slay_light"]

# per tier: velocity, range. Slower than before so the hand hangs in the air longer,
# and a longer range so it is allowed to.
FLIGHT = {
    "night_hand": (0.55, 48.0),
    "night_raid": (0.52, 48.0),
    "night_embrace": (0.50, 48.0),
    "black_ruin": (0.48, 48.0),
    "slay_light": (0.45, 52.0),
}

# ★★ 2026-10-10 第三版（作者："手发射出去怎么一直在旋转，掌心直直出去就好" +
#    "三个手掌黏在一起很丑" + "黑带很怪…这个雾很适合放在黑雾那边"）：
#
#  1. **朝向 = ALONG_MOTION**（原来是 TOWARDS_CAMERA）✗
#     TOWARDS_CAMERA 是"朝相机偏航" ⇒ 手飞过去的时候，相机与它的相对角度一直在变，
#     看起来就**一直在转** ✗。ALONG_MOTION 把模型的 +Z 锁到**飞行方向**上 ⇒
#     模型相对弹道固定，不再转 ✓；而模型现在是**掌心朝 +Z** 的平板
#     ⇒ 正好就是"掌心直直朝着前面出去" ✓。
#     `rotate_degrees_offset` 显式写 0：不要再叠任何自转 ✓。
#
#  2. **好几只手 = 多发，靠发射延迟错开** ✓
#     extra_launch_count 2 ⇒ 一次 3 只手；extra_launch_delay 5 tick ⇒
#     相邻两只沿弹道差 5 x 0.55 ≈ 2.7 格 ⇒ **不会黏在一起** ✓
#     （模型里那只手是单独的，所以也不存在"三个掌心互相穿插"✗）。
#
#  3. **拖尾只留黑粒子，不要雾、也不要鬼火** ✓
#     原来的 squid_ink / smoke / shadow 是**黑雾链的材质**（作者说"这个雾很适合放在黑雾那边"）
#     ⇒ 从这里撤掉 ✓；灵魂火（鬼火）也不要（作者："把鬼火去掉"）✓。
#     只留**黑焰**（soulsweapons:black_flame，本项目验证过的安全粒子 ✓）
#     ⇒ "携带黑粒子" ✓ 但不杂 ✓。
TRAIL = [
    {"particle_id": "soulsweapons:black_flame", "shape": "CIRCLE", "rotation": "LOOK",
     "origin": "CENTER", "count": 16.0, "min_speed": 0.0, "max_speed": 0.06},
]

# 一次放几只手（额外发数）与相邻间隔 tick
EXTRA_HANDS = 2
EXTRA_DELAY_TICKS = 5


def main():
    if not os.path.isdir(SPELL_DIR):
        print("ERROR: spell dir not found: %s" % SPELL_DIR)
        return 1
    problems = 0
    for name in CHAIN:
        path = os.path.join(SPELL_DIR, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue
        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)

        target = spell["release"]["target"]
        if "projectile" not in target:
            print("SKIP %s: release target is %s, not PROJECTILE" % (name, target.get("type")))
            problems += 1
            continue

        velocity, travel_range = FLIGHT[name]
        launch = target["projectile"]["launch_properties"]
        old_velocity = launch.get("velocity")
        launch["velocity"] = velocity
        # ★ 一次放好几只手：额外 2 发 + 每发间隔 5 tick ⇒ 沿弹道错开，不黏在一起 ✓
        launch["extra_launch_count"] = EXTRA_HANDS
        launch["extra_launch_delay"] = EXTRA_DELAY_TICKS
        spell["range"] = travel_range

        model = target["projectile"]["projectile"]["client_data"]["model"]
        old_orientation = model.get("orientation")
        # ★ 锁到**飞行方向**（不是相机）⇒ 不再看起来一直在转 ✓；模型掌心朝 +Z
        #   ⇒ 正好是"掌心直直朝前出去" ✓
        model["orientation"] = "ALONG_MOTION"
        model["rotate_degrees_offset"] = 0.0
        model.pop("rotate_degrees_per_tick", None)

        target["projectile"]["projectile"]["client_data"]["travel_particles"] = TRAIL

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print("%-16s v %-5s -> %-5s   range %-5s -> %-5s   orientation %-14s -> ALONG_MOTION   hands 1 -> %d   trail %d batch(es)"
              % (name, old_velocity, velocity, "?", travel_range,
                 old_orientation or "none", EXTRA_HANDS + 1, len(TRAIL)))

    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
