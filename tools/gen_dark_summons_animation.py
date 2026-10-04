# -*- coding: utf-8 -*-
"""
gen_dark_summons_animation.py -- animation sets for the five DARK summon models.

Author 2026-10-02: "把暗魔法里面的五个召唤物都配上动作吧"

The five geos (all built by tools/gen_dark_summons.py, all face -Z, eyes/wisps confirm it):
    dark_imp    5 bones   floating blob  : blob, eyeL, eyeR, wisp          (0.69 blocks)
    dark_guard  8 bones   hooded swordsman: cloak, body, armRight+blade, armLeft, head, wisp
    dark_lord  10 bones   + cape + blade2
    dark_king  11 bones   + crown (on head)
    evil_god   18 bones   6 arms each with a blade + ring (halo) + robe + wisp

Every model gets the same five clip names the rest of the project uses, so an entity's
controllers can just call them:
    idle   loop  breathing / drifting / halo spin
    walk   loop  hover-glide (none of these have legs - they are dark spirits)
    attack      blade swing (or, for the imp, a lunge + flaring eyes)
    cast        arms raised, cape spread, ring/crown flare
    death       collapse forward, wisp fades, blades drop

ROTATION SIGNS (front = -Z, +Y up - the same rules validated on the angel/dragon):
    arms / legs (cubes BELOW the pivot)   +x = swing forward
    torso / head (cubes ABOVE the pivot)  -x = lean/look forward
    capes / cloaks (hang below, at +Z)    -x = flow backwards
    ring / crown                          y  = spin
ASCII only.   Usage:  python tools/gen_dark_summons_animation.py
"""
import json
import math
import os

OUT_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity")
GEO_DIR = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity")


def kf(pairs):
    return {("%.2f" % t): [round(v, 3) for v in xyz] for (t, xyz) in pairs}


def chan(period, samples, fn):
    d = {}
    for i in range(samples):
        t = period * i / float(samples - 1)
        d["%.2f" % t] = [round(v, 3) for v in fn(t)]
    return d


def sine(period, samples, fn):
    return chan(period, samples, fn)


def sample_channel(period, samples, amp_x, amp_y, amp_z, phase=0.0, base=(0.0, 0.0, 0.0)):
    return chan(period, samples, lambda t: [
        base[0] + amp_x * math.sin(2 * math.pi * t / period + phase),
        base[1] + amp_y * math.sin(2 * math.pi * t / period + phase),
        base[2] + amp_z * math.sin(2 * math.pi * t / period + phase),
    ])


# ------------------------------------------------------------------ shared pieces
def cloak_sway(period, samples, amp=6.0, phase=0.0, base=0.0):
    """cloak/cape/robe: hangs below and behind -> -x flows backwards, z wobbles"""
    return chan(period, samples, lambda t: [
        base - amp * (0.5 + 0.5 * math.sin(2 * math.pi * t / period + phase)),
        0.0,
        amp * 0.35 * math.sin(4 * math.pi * t / period + phase),
    ])


def idle_clips(bones, period=3.2, samples=17, torso=("body",), head=("head",),
               cloak=(), wisp=(), spin=(), arms=(), blades=()):
    """the shared breathing loop"""
    out = {}
    for name in torso:
        out[name] = {"position": chan(period, samples, lambda t: [0.0, 0.5 * math.sin(2 * math.pi * t / period), 0.0]),
                     "rotation": sample_channel(period, samples, 1.2, 0.0, 1.5)}
    for name in head:
        out[name] = {"rotation": sample_channel(period, samples, 2.0, 6.0, 1.5, phase=0.7)}
    # 手臂/刀在待机时也要有一点点飘 ✓（不然六条手臂像钉在身子上 ✗）
    for i, name in enumerate(arms):
        out[name] = {"rotation": sample_channel(period, samples, 1.6, 0.0, 2.2,
                                                phase=0.35 * i)}
    for i, name in enumerate(blades):
        out[name] = {"rotation": sample_channel(period, samples, 2.2, 0.0, 0.0,
                                                phase=0.5 * i)}
    for name in cloak:
        out[name] = {"rotation": cloak_sway(period, samples, 5.0)}
    for name in wisp:
        out[name] = {"rotation": sample_channel(period, samples, 6.0, 0.0, 8.0, phase=1.1),
                     "scale": chan(period, samples, lambda t: [1.0, 1.0, 1.0 + 0.10 * math.sin(2 * math.pi * t / period)])}
    return out


def walk_clips(bones, period=1.1, samples=12, torso=("body",), head=("head",),
               arms=(), cloak=(), blades=(), wisp=()):
    """hover-glide: a faster bob, arms drifting, cloak fluttering (no legs to step with)"""
    out = {}
    for name in torso:
        out[name] = {"position": chan(period, samples, lambda t: [0.0, 1.1 * math.sin(2 * math.pi * t / period), 0.0]),
                     "rotation": sample_channel(period, samples, 2.5, 0.0, 3.0)}
    for name in head:
        out[name] = {"rotation": sample_channel(period, samples, 2.0, 8.0, 2.0, phase=0.5)}
    for name in arms:
        out[name] = {"rotation": sample_channel(period, samples, 10.0, 0.0, 6.0, phase=math.pi)}
    for name in blades:
        out[name] = {"rotation": sample_channel(period, samples, 6.0, 0.0, 0.0)}
    for name in cloak:
        out[name] = {"rotation": cloak_sway(period, samples, 9.0)}
    for name in wisp:
        out[name] = {"rotation": sample_channel(period, samples, 10.0, 0.0, 12.0, phase=0.9)}
    return out


def attack_clips(arm=(), blade=(), torso=("body",), head=("head",), cloak=(), wisp=(),
                 period=0.8):
    """one blade swing: wind up slightly back, then a big forward slash"""
    out = {}
    for name in torso:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.2, (4, 0, 0)), (0.42, (-9, 0, 0)),
                                     (0.8, (0, 0, 0))])}
    for name in arm:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.2, (-32, 0, -12)), (0.42, (62, 0, 14)),
                                     (0.8, (0, 0, 0))])}
    for name in blade:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.2, (-18, 0, 0)), (0.42, (34, 0, 0)),
                                     (0.8, (0, 0, 0))])}
    for name in head:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.2, (4, 0, 0)), (0.42, (-8, 0, 0)),
                                     (0.8, (0, 0, 0))])}
    for name in cloak:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.42, (-14, 0, 0)), (0.8, (0, 0, 0))])}
    for name in wisp:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.42, (16, 0, 0)), (0.8, (0, 0, 0))]),
                     "scale": kf([(0.0, (1, 1, 1)), (0.42, (1.25, 1.25, 1.25)), (0.8, (1, 1, 1))])}
    return out


def cast_clips(arms=(), torso=("body",), head=("head",), cloak=(), wisp=(), spin=(),
               period=1.6):
    """arms raised, cloak spread, halo/crown flares"""
    out = {}
    for name in torso:
        out[name] = {"position": kf([(0.0, (0, 0, 0)), (0.8, (0, 1.2, 0)), (1.6, (0, 0, 0))]),
                     "rotation": kf([(0.0, (0, 0, 0)), (0.8, (-5, 0, 0)), (1.6, (0, 0, 0))])}
    for name in arms:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.7, (-46, 0, -18)), (1.3, (-46, 0, -18)),
                                     (1.6, (0, 0, 0))])}
    for name in head:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.8, (6, 0, 0)), (1.6, (0, 0, 0))])}
    for name in cloak:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.9, (-22, 0, 0)), (1.6, (0, 0, 0))])}
    for name in wisp:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (0.9, (-14, 0, 0)), (1.6, (0, 0, 0))]),
                     "scale": kf([(0.0, (1, 1, 1)), (0.9, (1.4, 1.4, 1.4)), (1.6, (1, 1, 1))])}
    for name in spin:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (1.6, (0, 360, 0))]),
                     "scale": kf([(0.0, (1, 1, 1)), (0.9, (1.3, 1.3, 1.3)), (1.6, (1, 1, 1))])}
    return out


def death_clips(torso=("body",), head=("head",), cloak=(), wisp=(), blades=(), spin=(),
                period=1.2):
    """torso pitches forward (-x), blades drop, wisp fades out"""
    out = {}
    for name in torso:
        out[name] = {"position": kf([(0.0, (0, 0, 0)), (1.2, (0, -3, 0))]),
                     "rotation": kf([(0.0, (0, 0, 0)), (0.6, (-30, 0, 0)), (1.2, (-70, 0, 0))])}
    for name in head:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (1.2, (-28, 0, 0))])}
    for name in cloak:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (1.2, (18, 0, 0))])}
    for name in wisp:
        out[name] = {"scale": kf([(0.0, (1, 1, 1)), (1.2, (0.05, 0.05, 0.05))])}
    for name in blades:
        out[name] = {"rotation": kf([(0.0, (0, 0, 0)), (1.2, (50, 0, 0))])}
    for name in spin:
        out[name] = {"scale": kf([(0.0, (1, 1, 1)), (1.2, (0.1, 0.1, 0.1))])}
    return out


# ------------------------------------------------------------------ per-model specs
def clips_for(name, bones):
    """bones = the set of bone names that actually exist in that geo (never animate a
    missing bone - it just does nothing and hides the mistake)"""
    out = {}

    if name == "dark_imp":
        # 一团飘着的小暗影：只有 blob / 两只眼 / 一条尾焰 ✓
        def has(b):
            return b in bones
        out["idle"] = {}
        out["idle"]["blob"] = {"position": chan(3.0, 16, lambda t: [0.0, 0.6 * math.sin(2 * math.pi * t / 3.0), 0.0]),
                               "rotation": sample_channel(3.0, 16, 2.5, 4.0, 0.0)}
        for eye in ("eyeL", "eyeR"):
            if has(eye):
                out["idle"][eye] = {"scale": chan(3.0, 16, lambda t: [
                    1.0, 1.0 + 0.22 * max(0.0, math.sin(2 * math.pi * t / 3.0)) ** 4, 1.0])}
        if has("wisp"):
            out["idle"]["wisp"] = {"rotation": sample_channel(3.0, 16, 12.0, 0.0, 10.0, phase=1.0),
                                   "scale": chan(3.0, 16, lambda t: [1.0, 1.0, 1.0 + 0.12 * math.sin(2 * math.pi * t / 3.0)])}
        out["walk"] = {
            "blob": {"position": chan(0.9, 10, lambda t: [0.0, 1.2 * math.sin(2 * math.pi * t / 0.9), 0.0]),
                     "rotation": sample_channel(0.9, 10, 5.0, 8.0, 0.0)},
            "wisp": {"rotation": sample_channel(0.9, 10, 18.0, 0.0, 16.0)},
        }
        out["attack"] = {
            "blob": {"position": kf([(0.0, (0, 0, 0)), (0.25, (0, 1.0, -3.0)), (0.5, (0, 0, -1.2)), (0.8, (0, 0, 0))]),
                     "rotation": kf([(0.0, (0, 0, 0)), (0.25, (14, 0, 0)), (0.5, (-6, 0, 0)), (0.8, (0, 0, 0))]),
                     "scale": kf([(0.0, (1, 1, 1)), (0.3, (1.25, 1.25, 1.25)), (0.8, (1, 1, 1))])},
            "wisp": {"scale": kf([(0.0, (1, 1, 1)), (0.3, (1.4, 1.4, 1.4)), (0.8, (1, 1, 1))])},
        }
        out["cast"] = {
            "blob": {"position": kf([(0.0, (0, 0, 0)), (0.8, (0, 1.6, 0)), (1.6, (0, 0, 0))]),
                     "rotation": kf([(0.0, (0, 0, 0)), (1.6, (0, 360, 0))]),
                     "scale": kf([(0.0, (1, 1, 1)), (0.9, (1.3, 1.3, 1.3)), (1.6, (1, 1, 1))])},
            "wisp": {"rotation": kf([(0.0, (0, 0, 0)), (1.6, (0, 360, 0))])},
        }
        out["death"] = {
            "blob": {"scale": kf([(0.0, (1, 1, 1)), (0.5, (1.15, 0.7, 1.15)), (1.2, (0.05, 0.05, 0.05))]),
                     "rotation": kf([(0.0, (0, 0, 0)), (1.2, (0, 180, 0))])},
            "wisp": {"scale": kf([(0.0, (1, 1, 1)), (1.2, (0.05, 0.05, 0.05))])},
        }
        if "eyeL" in bones:
            out["death"]["eyeL"] = {"scale": kf([(0.0, (1, 1, 1)), (0.9, (0.1, 0.1, 0.1))])}
            out["death"]["eyeR"] = {"scale": kf([(0.0, (1, 1, 1)), (0.9, (0.1, 0.1, 0.1))])}
        return out

    # ---- 人形/袍子那一族（guard / lord / king / evil_god）----
    two_handed = "blade2" in bones
    arms = [b for b in ("armRight", "armLeft", "armR1", "armL1", "armR2", "armL2", "armR3", "armL3")
            if b in bones]
    blades = [b for b in ("blade", "blade2", "bladeR1", "bladeL1", "bladeR2", "bladeL2",
                          "bladeR3", "bladeL3") if b in bones]
    cloak = [b for b in ("cloak", "cape", "robe") if b in bones]
    wisp = [b for b in ("wisp",) if b in bones]
    spin = [b for b in ("ring", "crown") if b in bones]

    out["idle"] = idle_clips(bones, torso=["body"], head=["head"], cloak=cloak, wisp=wisp,
                             arms=arms, blades=blades)
    out["idle"].update(sample_channels_for(spin))
    out["walk"] = walk_clips(bones, torso=["body"], head=["head"], arms=arms, cloak=cloak,
                             blades=blades, wisp=wisp)
    out["attack"] = attack_clips(arm=arms, blade=blades, torso=["body"], head=["head"],
                                 cloak=cloak, wisp=wisp)
    out["cast"] = cast_clips(arms=arms, torso=["body"], head=["head"], cloak=cloak, wisp=wisp,
                             spin=spin)
    out["death"] = death_clips(torso=["body"], head=["head"], cloak=cloak, wisp=wisp,
                               blades=blades, spin=spin)
    if two_handed:
        # 双刃的（lord/king）：两把刀错开一点，看起来像"双刀连斩"✓
        out["attack"]["blade2"] = {"rotation": kf([(0.0, (0, 0, 0)), (0.28, (-20, 0, 0)),
                                                   (0.5, (28, 0, 0)), (0.8, (0, 0, 0))])}
    return out


def sample_channels_for(names):
    """halo/crown: slow spin in idle"""
    out = {}
    for n in names:
        out[n] = {"rotation": chan(3.2, 17, lambda t: [0.0, 360.0 * t / 3.2, 0.0])}
    return out


def build_one(name, bones):
    """assemble: idle(3.2s) / walk(1.1s) / attack(0.8s) / cast(1.6s) / death(1.2s)"""
    body = clips_for(name, bones)
    clips = {
        "idle": {"loop": True, "animation_length": 3.2, "bones": body["idle"]},
        "walk": {"loop": True, "animation_length": 1.1, "bones": body["walk"]},
        "attack": {"loop": False, "animation_length": 0.8, "bones": body["attack"]},
        "cast": {"loop": False, "animation_length": 1.6, "bones": body["cast"]},
        "death": {"loop": False, "animation_length": 1.2, "bones": body["death"]},
    }
    # 只保留模型里真的有的骨头 ✗（写错了不报错，只是"看不到"，最容易骗过自己）
    for clip in clips.values():
        clip["bones"] = {k: v for k, v in clip["bones"].items() if k in bones}
    return {"format_version": "1.8.0", "animations": clips}


MODELS = ["dark_imp", "dark_guard", "dark_lord", "dark_king", "evil_god"]


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in MODELS:
        geo = json.load(open(os.path.join(GEO_DIR, name + ".geo.json"), encoding="utf-8"))
        bones = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
        data = build_one(name, set(bones))
        p = os.path.join(OUT_DIR, name + ".animation.json")
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False)
            f.write("\n")
        used = sorted({b for clip in data["animations"].values() for b in clip["bones"]})
        missing = [b for b in bones if b not in used]
        print("%-11s -> %s  clips=%s  animated bones=%d/%d%s"
              % (name, os.path.basename(p), ",".join(data["animations"].keys()),
                 len(used), len(bones),
                 ("   (no motion: %s)" % ",".join(missing)) if missing else ""))


if __name__ == "__main__":
    main()
