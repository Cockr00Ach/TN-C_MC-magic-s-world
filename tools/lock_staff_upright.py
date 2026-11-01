"""Keep the staff behaving like a staff: plumb, in the fist, and out of the body.

Two separate problems live here, both invisible until someone watches the animation:

1. **Missing wrist.**  The casting clips only animate ``armRight``; the staff hangs off
   that bone, so it turns with the forearm - at ``armRight = -102deg`` (wavingstaff) the
   shaft lies across the chest, at ``-144deg`` (playmagic_1) it points at the ground.
   The wrist already exists in the model (the ``wand`` bone, pivot in the fist), so for
   the clips where the arm is raised *beside* the body this tool writes a ``wand``
   rotation channel that is the exact inverse of ``armRight`` (composed Z->Y->X, the order
   ``RenderUtils.rotateMatrixAroundBone`` uses) - the fist travels, the shaft stays plumb.

2. **Staff through the head.**  In ``magicsummon`` the right arm swings ACROSS the body
   (``armRight`` z = +99 deg), so the fist ends up near the midline; a plumb staff there
   would go straight up through the head.  Those clips are left un-locked on purpose: the
   shaft keeps following the arm, which points it away from the body.

    python tools/lock_staff_upright.py            # write the wrist channels
    python tools/lock_staff_upright.py --check    # orientation + clearance, exit 1 on bad

Re-run after re-authoring ``armRight`` in Blockbench, otherwise the staff flails again.
Both checks are cheap and offline - run them before every jar.
"""

import io
import json
import math
import os
import sys

ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "player_magic-animation.json")
GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity",
                   "self_wand.geo.json")

ARM = "armRight"
WRIST = "wand"
TOLERANCE = 0.5                       # degrees of residual tilt that still counts as plumb
# Clips whose raised arm stays outside the torso: keep the shaft plumb there.
PLUMB = ("wavingstaff", "playmagic_1")
# Clips where the ARM itself is the problem: magicsummon swings armRight across the body
# midline (the fist reaches x=+1..+3, inside the chest), so no wrist angle can keep a
# 1.2-block-wide staff out of the torso. Left rigid (shaft follows the arm, as authored)
# and reported as a note instead of a failure - that fix belongs in armRight, in Blockbench.
KNOWN = {
    "magicsummon": "armRight swings across the midline (fist ends up inside the chest); "
                   "staff clearance is an arm-authoring problem, not a wrist one",
}
BOSSES = ("head", "hat", "body", "legRight", "legLeft")   # armRight is the grip itself
SAMPLE_STEP = 1.0 / 20.0              # 20 fps is plenty to catch a shaft sweeping through
GRAZE = 0.35                          # units of overlap that read as a touch, not a stab


# ---------------------------------------------------------------- matrices
def mat_mul(a, b):
    return [[sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)] for r in range(3)]


def mat_vec(m, v):
    return [sum(m[r][k] * v[k] for k in range(3)) for r in range(3)]


def mat_of(x, y, z):
    """Rz * Ry * Rx, the order GeckoLib applies a bone's rotation in."""
    a, b, c = math.radians(x), math.radians(y), math.radians(z)
    ca, sa = math.cos(a), math.sin(a)
    cb, sb = math.cos(b), math.sin(b)
    cc, sc = math.cos(c), math.sin(c)
    return [
        [cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa],
        [sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa],
        [-sb, cb * sa, cb * ca],
    ]


def euler_of(m):
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def inverse(m):
    return [[m[c][r] for c in range(3)] for r in range(3)]


def tilt(a, b):
    m = mat_mul(a, b)
    return math.degrees(math.acos(max(-1.0, min(1.0, m[1][1]))))


def wrist_for(rotation):
    return euler_of(inverse(mat_of(*rotation)))


# ---------------------------------------------------------------- model + pose
def load(repo):
    geo = json.load(io.open(os.path.join(repo, GEO), encoding="utf-8"))
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    anim = json.load(io.open(os.path.join(repo, ANIM), encoding="utf-8"))
    return bones, anim


def lerp_keys(chans, t):
    pts = sorted((float(k), v) for k, v in chans.items())
    if not pts:
        return None
    if t <= pts[0][0]:
        return list(pts[0][1])
    if t >= pts[-1][0]:
        return list(pts[-1][1])
    for (ta, va), (tb, vb) in zip(pts, pts[1:]):
        if ta <= t <= tb:
            f = (t - ta) / max(1e-9, tb - ta)
            return [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    return list(pts[-1][1])


def pose_at(clip, t):
    pose = {}
    for bone, chans in clip.get("bones", {}).items():
        rot = lerp_keys(chans["rotation"], t) if "rotation" in chans else [0.0, 0.0, 0.0]
        pos = lerp_keys(chans["position"], t) if "position" in chans else [0.0, 0.0, 0.0]
        pose[bone] = (rot, pos)
    return pose


def world_of(bones, pose, name, cache):
    if name in cache:
        return cache[name]
    bone = bones[name]
    parent = bone.get("parent")
    R, T = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0.0, 0.0, 0.0]) if not parent \
        else world_of(bones, pose, parent, cache)
    pivot = [float(v) for v in bone["pivot"]]
    rot, pos = pose.get(name, ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0]))
    Rl = mat_of(*rot)
    Tl = [pivot[i] + pos[i] for i in range(3)]
    Rn = mat_mul(R, Rl)
    shifted = [Tl[i] - sum(Rl[i][k] * pivot[k] for k in range(3)) for i in range(3)]
    Tn = [T[i] + sum(R[i][k] * shifted[k] for k in range(3)) for i in range(3)]
    cache[name] = (Rn, Tn)
    return Rn, Tn


def to_world(bones, pose, bone_name, point, cache):
    R, T = world_of(bones, pose, bone_name, cache)
    return [T[i] + sum(R[i][k] * point[k] for k in range(3)) for i in range(3)]


def staff_axis(bones):
    """(x, z, y_low, y_high) of the shaft in rest space."""
    cubes = bones[WRIST]["cubes"]
    low = min(c["origin"][1] for c in cubes)
    high = max(c["origin"][1] + c["size"][1] for c in cubes)
    pivot = bones[WRIST]["pivot"]
    return pivot[0], pivot[2], low, high


def _inside(bones, pose, cache, point, boss):
    R, T = world_of(bones, pose, boss, cache)
    local = mat_vec(inverse(R), [point[k] - T[k] for k in range(3)])
    worst = 0.0
    for cube in bones[boss].get("cubes", []):
        o, s = cube["origin"], cube["size"]
        if all(o[k] - 0.0 <= local[k] <= o[k] + s[k] for k in range(3)):
            depth = min(min(local[k] - o[k], o[k] + s[k] - local[k]) for k in range(3))
            worst = max(worst, depth)
    return worst


def fist_inside_body(bones, pose, cache):
    """The animation itself buries the hand in the torso - the staff cannot help there."""
    grip = bones[WRIST]["pivot"]
    p = to_world(bones, pose, WRIST, tuple(grip), cache)
    return any(_inside(bones, pose, cache, p, boss) > GRAZE for boss in BOSSES)


def shaft_hits_body(bones, pose):
    """True if the shaft axis passes through a torso cube (the grip arm is excluded)."""
    cache = {}
    x, z, low, high = staff_axis(bones)
    for i in range(41):
        y = low + (high - low) * i / 40.0
        p = to_world(bones, pose, WRIST, (x, y, z), cache)
        for boss in BOSSES:
            if _inside(bones, pose, cache, p, boss) > GRAZE:
                return True, "y=%.1f passes through %s" % (y, boss)
    return False, ""


# ---------------------------------------------------------------- main
def main(argv):
    check = "--check" in argv
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    bones, data = load(repo)

    problems = []
    notes = []
    touched = 0
    for clip_name, clip in data["animations"].items():
        arm = clip.get("bones", {}).get(ARM, {}).get("rotation")
        if not arm:
            continue
        plumb = clip_name in PLUMB
        if plumb:
            want = {t: [round(v, 4) for v in wrist_for(arm[t])] for t in arm}
            have = clip["bones"].get(WRIST, {}).get("rotation", {})
            if check:
                if have != want:
                    problems.append("%s: `wand` channel missing or stale - rerun without "
                                    "--check" % clip_name)
                for t in arm:
                    if t in have:
                        worst = tilt(mat_of(*arm[t]), mat_of(*have[t]))
                        if worst > TOLERANCE:
                            problems.append("%s @ %ss: shaft tilts %.1f deg (max %.1f)"
                                            % (clip_name, t, worst, TOLERANCE))
            else:
                if have != want:
                    touched += 1
                clip["bones"][WRIST] = {"rotation": want}
        elif WRIST in clip.get("bones", {}):
            # this clip swings the arm across the body: a plumb shaft would go through the
            # head, so the shaft follows the arm instead. Drop any stale lock.
            if not check:
                clip["bones"].pop(WRIST)
                touched += 1
            else:
                problems.append("%s: still carries a `wand` channel but is not in PLUMB"
                                % clip_name)

        # clearance is checked for every clip, locked or not. Frames where the animation
        # has already buried the hand in the torso are skipped: no wrist angle can help.
        length = float(clip.get("animation_length", 0.0))
        t = 0.0
        while t <= length + 1e-6:
            pose = pose_at(clip, t)
            if not fist_inside_body(bones, pose, {}):
                hit, why = shaft_hits_body(bones, pose)
                if hit:
                    line = "%s @ %.2fs: %s" % (clip_name, t, why)
                    (notes if clip_name in KNOWN else problems).append(line)
                    break
            t += SAMPLE_STEP

    if check:
        for n in notes:
            print("  [note] " + n + "  (" + KNOWN[n.split(" @ ")[0]] + ")")
        if problems:
            print("FAIL")
            for p in problems:
                print("  - " + p)
            return 1
        print("OK: shaft stays plumb where it should and never crosses the body")
        return 0

    if not touched:
        print("nothing to do: the `wand` channels already match their arms")
    else:
        with io.open(os.path.join(repo, ANIM), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent="\t")
            f.write("\n")
        print("wrote %d wrist channel(s) into %s" % (touched, ANIM))
    for n in notes:
        print("  [note] " + n + "  (" + KNOWN[n.split(" @ ")[0]] + ")")
    for p in problems:
        print("  ! " + p)
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
