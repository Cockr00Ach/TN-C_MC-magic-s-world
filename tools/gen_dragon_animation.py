# -*- coding: utf-8 -*-
"""
gen_dragon_animation.py -- the Eastern dragon's first animation set (Bedrock 1.8.0).

Author 2026-10-02: "然后你可以k一个动画，就是向前冲刺的感觉" -> the `dash` clip below.
(`idle` is thrown in because an entity needs a resting loop anyway.)

HOW THE DASH IS BUILT (this is the whole trick for a serpentine dragon):
  * a **travelling wave** runs BACKWARDS along the spine: body segment i gets
        y = A_i * sin(2*pi*t/T - i*delta)          (y = 转身 = 横向摆动)
    Because the bones are a parent chain the angles add up, so a chain of small sines
    becomes one smooth undulation - and because the wave travels backwards it reads as
    "the dragon is pushing itself forward" (exactly how a snake swims).
  * amplitude grows towards the tail (A_i = 1.5 + 0.5*i) so the front stays rigid/streamlined
    and the tail whips.
  * the pose itself is a charge: neck stretched forward-down, head level, mouth ajar,
    mane and whiskers blown straight back, antlers pinned back, legs tucked under the body.
  * `idle` is the same wave at rest: tiny amplitude, slow, head held high.

ASCII only.   Usage:  python tools/gen_dragon_animation.py
"""
import json
import math
import os

NAME = "dragon"
OUT = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                   NAME + ".animation.json")

BODY = ["body%d" % i for i in range(1, 13)]
TAIL = ["tail%d" % i for i in range(1, 6)]
LEGS = {
    "legFL1": (28, 2), "legFL2": (-40, 2), "pawFL": (18, 2),
    "legFR1": (28, 2), "legFR2": (-40, 2), "pawFR": (18, 2),
    "legBL1": (-24, 2), "legBL2": (34, 2), "pawBL": (-14, 2),
    "legBR1": (-24, 2), "legBR2": (34, 2), "pawBR": (-14, 2),
}


def keys(samples, period):
    return {("%.2f" % (period * i / float(samples - 1))): v for i, v in enumerate(samples)
            if i == 0 or True}


def wave(period, samples, amp, phase, delta_i, offset=0.0, base=0.0):
    """one channel of the travelling wave, sampled into keyframes"""
    out = []
    for i in range(samples):
        t = period * i / float(samples - 1)
        phi = 2.0 * math.pi * t / period
        out.append(round(base + amp * math.sin(phi - delta_i + offset), 2))
    return out


def chan(period, samples, fn):
    """fn(t) -> [x, y, z]"""
    d = {}
    for i in range(samples):
        t = period * i / float(samples - 1)
        d["%.2f" % t] = [round(v, 2) for v in fn(t)]
    return d


def dash():
    period, samples, delta = 1.0, 11, 0.62
    bones = {}

    # ---- 身体：向后传播的波 + 整体略低头（冲刺姿态 ✓）----
    #   注意：脖子/尾巴在模型里带了静态抬头角度（颈 +14/+10、尾 -2 ×5 ✓）
    #   ⇒ 冲刺时要**反着补**，头才压得下来、尾巴才不翘 ✗（不然看着像在原地昂首 ✗）
    for idx, name in enumerate(BODY, start=1):
        amp = 1.5 + 0.5 * idx
        base_x = -3.0 if idx == 1 else -0.8
        bones[name] = {
            "rotation": chan(period, samples, lambda t, i=idx, a=amp, bx=base_x: [
                bx + 0.9 * math.sin(2 * math.pi * t / period - i * delta + 0.85),
                a * math.sin(2 * math.pi * t / period - i * delta),
                1.2 * math.sin(2 * math.pi * t / period - i * delta * 0.5),
            ]),
        }

    # ---- 尾巴：反向补掉静态上扬 + 甩得更凶 ✓ ----
    for j, name in enumerate(TAIL, start=1):
        amp = 9.0 + 1.6 * j
        bones[name] = {
            "rotation": chan(period, samples, lambda t, k=12 + j, a=amp: [
                1.6 + 1.2 * math.sin(2 * math.pi * t / period - k * delta + 0.6),
                a * math.sin(2 * math.pi * t / period - k * delta),
                0.0,
            ]),
        }
    bones["tailFin"] = {
        "rotation": chan(period, samples, lambda t: [
            2.0 + 2.0 * math.sin(2 * math.pi * t / period - 18 * delta),
            6.0 * math.sin(2 * math.pi * t / period - 18 * delta + 0.5),
            0.0,
        ]),
        "scale": chan(period, samples, lambda t: [1.0, 1.0,
                                                  1.0 + 0.08 * (0.5 + 0.5 * math.sin(2 * math.pi * t / period))]),
    }

    # ---- 头颈：往前压平（抵消静态抬头）+ 下巴微张 + 鬃须被风吹直 ✓ ----
    bones["neck1"] = {"rotation": chan(period, samples,
                                       lambda t: [-8.0 + 1.5 * math.sin(2 * math.pi * t / period),
                                                  1.5 * math.sin(2 * math.pi * t / period + 0.5), 0.0])}
    bones["neck2"] = {"rotation": chan(period, samples,
                                       lambda t: [-6.0 + 1.2 * math.sin(2 * math.pi * t / period + 0.4),
                                                  1.2 * math.sin(2 * math.pi * t / period + 0.9), 0.0])}
    bones["head"] = {"rotation": chan(period, samples, lambda t: [
        -4.0 + 1.3 * math.sin(2 * math.pi * t / period + 0.8),
        -3.5 * math.sin(2 * math.pi * t / period + 1.1),
        2.5 * math.sin(2 * math.pi * t / period + 0.3),
    ])}
    bones["jaw"] = {"rotation": chan(period, samples, lambda t: [
        -7.0 - 3.0 * math.sin(4 * math.pi * t / period), 0.0, 0.0])}
    bones["mane"] = {"rotation": chan(period, samples, lambda t: [
        -22.0 + 5.0 * math.sin(2 * math.pi * t / period + 1.2),
        0.0,
        4.0 * math.sin(2 * math.pi * t / period + 0.5),
    ])}
    for side, sgn in (("L", 1.0), ("R", -1.0)):
        for tag, extra in (("", 0.0), ("2", 0.6)):
            bones["whisker%s%s" % (side, tag)] = {"rotation": chan(period, samples, lambda t, g=sgn, e=extra: [
                0.0,
                g * (9.0 + 3.0 * math.sin(2 * math.pi * t / period + e)),
                g * (7.0 + 2.5 * math.sin(2 * math.pi * t / period + e + 0.4)),
            ])}
            bones["antler%s%s" % (side, tag)] = {"rotation": chan(period, samples, lambda t, e=extra: [
                -4.0 + 1.5 * math.sin(2 * math.pi * t / period + e), 0.0, 0.0])}

    # ---- 四条腿：冲刺时收在身下 ✓（作者的模型现在腿是空的，键留着，补上方块就会动 ✓）----
    for name, (base, flutter) in LEGS.items():
        bones[name] = {"rotation": chan(period, samples, lambda t, b=base, f=flutter: [
            b + f * math.sin(2 * math.pi * t / period), 0.0, 0.0])}

    return {"loop": True, "animation_length": period, "bones": bones}


def idle():
    period, samples, delta = 2.4, 13, 0.5
    bones = {}
    for idx, name in enumerate(BODY, start=1):
        amp = 1.0 + 0.32 * idx
        bones[name] = {"rotation": chan(period, samples, lambda t, i=idx, a=amp: [
            0.8 * math.sin(2 * math.pi * t / period - i * delta + 0.7),
            a * math.sin(2 * math.pi * t / period - i * delta),
            0.0,
        ])}
    for j, name in enumerate(TAIL, start=1):
        amp = 5.0 + 1.1 * j
        bones[name] = {"rotation": chan(period, samples, lambda t, k=12 + j, a=amp: [
            -1.5 + 1.0 * math.sin(2 * math.pi * t / period - k * delta),
            a * math.sin(2 * math.pi * t / period - k * delta),
            0.0,
        ])}
    bones["tailFin"] = {"rotation": chan(period, samples,
                                         lambda t: [0.0, 4.0 * math.sin(2 * math.pi * t / period - 9), 0.0])}
    bones["neck1"] = {"rotation": chan(period, samples, lambda t: [
        13.0 + 1.5 * math.sin(2 * math.pi * t / period), 0.0, 0.0])}
    bones["neck2"] = {"rotation": chan(period, samples, lambda t: [
        9.0 + 1.2 * math.sin(2 * math.pi * t / period + 0.5), 0.0, 0.0])}
    bones["head"] = {"rotation": chan(period, samples, lambda t: [
        -5.0 + 1.0 * math.sin(2 * math.pi * t / period + 0.9),
        5.0 * math.sin(2 * math.pi * t / period * 0.5), 0.0])}
    bones["jaw"] = {"rotation": chan(period, samples, lambda t: [
        -3.0 - 2.0 * math.sin(2 * math.pi * t / period), 0.0, 0.0])}
    bones["mane"] = {"rotation": chan(period, samples, lambda t: [
        -6.0 + 3.0 * math.sin(2 * math.pi * t / period + 0.8), 0.0,
        2.5 * math.sin(2 * math.pi * t / period),])}
    for side, sgn in (("L", 1.0), ("R", -1.0)):
        for tag, extra in (("", 0.0), ("2", 0.5)):
            bones["whisker%s%s" % (side, tag)] = {"rotation": chan(period, samples, lambda t, g=sgn, e=extra: [
                0.0, g * (5.0 + 2.5 * math.sin(2 * math.pi * t / period + e)),
                g * (3.0 + 2.0 * math.sin(2 * math.pi * t / period + e + 0.7))])}
    for name in LEGS:
        bones[name] = {"rotation": chan(period, samples, lambda t: [
            2.0 * math.sin(2 * math.pi * t / period), 0.0, 0.0])}
    return {"loop": True, "animation_length": period, "bones": bones}


def build():
    return {"format_version": "1.8.0", "animations": {"dash": dash(), "idle": idle()}}


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
        print("   %-6s %.2fs loop=%s bones=%d" % (name, a["animation_length"], a["loop"], len(a["bones"])))


if __name__ == "__main__":
    main()
