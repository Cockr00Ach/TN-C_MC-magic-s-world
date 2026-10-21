"""Solve the drawn blade's wrist keys so it never passes through the boss.

The Canglan Warlord models no weapon: an empty ``weapon_1`` anchor on the right
fist carries the shared wind arsenal blade, and the renderer draws it there every
frame. That means a wrist rotation which looked fine on a 戈 becomes a 2.5-block
sword sweeping through the boss's own chest, and the only way to see it is to
place the real model on every frame of every clip.

This tool does that: it walks each clip in 0.05 s steps, finds frames where any
blade vertex is inside the head, helmet, chest or right pauldron, and then
searches wrist angles for the key that drives that stretch of the clip until the
stretch stops intersecting the body. Hit frames keep their aim: a candidate that
clears the body but points the blade somewhere else is rejected.

The chosen angles are written back into the ``WEAPON_KEYS`` block of
``tools/gen_canglan_warlord.py`` -- the single source of truth for the drawn
blade's wrist -- and the generator is re-run.

    python tools/solve_wrist.py             # solve, then report
    python tools/solve_wrist.py --check     # only report, change nothing
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import audit_canglan_warlord as audit  # noqa: E402
import preview_canglan_warlord as preview  # noqa: E402
import wind_blade  # noqa: E402

GENERATOR = ROOT / "tools/gen_canglan_warlord.py"
START = "# <<< WEAPON_KEYS >>>"
END = "# <<< END WEAPON_KEYS >>>"
PARTS = audit.BLADE_PARTS
GRAZE = audit.GRAZE
STEP = 0.05
# a wrist may only move this far per key, so a solved pose still reads as the
# same swing instead of flipping the blade around mid-clip
MAX_TURN = 75
# which arsenal form each clip can be holding; clips outside this table can be
# played in either phase, so they have to be clear with both blades
CLIP_FORMS = {"cleave_left": (1,), "cleave_right": (1,), "overhead": (1,),
             "thrust": (2,), "sweep": (2,), "spear_throw": (2,)}
BOTH = audit.BOTH_FORMS


def load_geometry():
    geo = json.loads(audit.GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    return geo, {b["name"]: b for b in geo["bones"]}


def read_table():
    text = GENERATOR.read_text(encoding="utf-8")
    block = text.split(START)[1].split(END)[0]
    namespace = {}
    exec(block, namespace)  # our own generated block
    return namespace["WEAPON_KEYS"], text


def format_table(table):
    out = ["WEAPON_KEYS = {"]
    for clip, keys in table.items():
        out.append(f'    "{clip}": [')
        for time, x, z in keys:
            out.append(f"        ({time:.3f}, {x:.0f}, {z:.0f}),")
        out.append("    ],")
    out.append("}")
    return "\n".join(out)


def write_table(table):
    text = GENERATOR.read_text(encoding="utf-8")
    head, rest = text.split(START, 1)
    _, tail = rest.split(END, 1)
    GENERATOR.write_text(head + START + "\n" + format_table(table) + "\n" + END + tail,
                         encoding="utf-8", newline="\n")


def regenerate():
    subprocess.run([sys.executable, str(GENERATOR)], check=True,
                   stdout=subprocess.DEVNULL, cwd=ROOT)


def frames_of(seconds):
    return [round(i * STEP, 3) for i in range(int(round(seconds / STEP)) + 1)]


def set_track(clips, clip, keys):
    clips[clip]["bones"].setdefault("weapon_1", {})["rotation"] = {
        f"{time:.3f}": [x, 0.0, z] for time, x, z in keys}


class Frame:
    """Everything about one frame that does not depend on the wrist angle."""

    cache = {}

    def __init__(self, geo, bones, clips, clip, time):
        matrices = audit.matrix_of(geo, preview.sample(clips, clip, time))
        self.matrices = matrices
        self.boxes = [audit.aabb(audit.bone_points(geo, matrices[part], part))
                      for part in PARTS]
        self.torso = audit.bone_points(geo, matrices["chest"], "chest").mean(axis=0)

    def measure(self, bones, form, x, z):
        """(vertices inside the body, blade aim in degrees, tip, reach in units)."""
        anchor = wind_blade.anchor_for(self.matrices, bones, x, z)
        blade = np.concatenate([poly for poly, _, _ in wind_blade.place_at(form, anchor)])
        hits = 0
        for low, high in self.boxes:
            hits += int(((blade > low) & (blade < high)).all(axis=1).sum())
        pivot = (self.matrices["hand_1"]
                 @ np.append(np.array(bones["weapon_1"]["pivot"], dtype=float),
                             np.ones(1)))[:3]
        # the anchor sits on the fist; the tip is the furthest blade point from it
        far = blade[np.linalg.norm(blade - anchor[:3, 3], axis=1).argmax()]
        span = far - pivot
        length = max(1e-9, float(np.linalg.norm(span)))
        phi = float(np.degrees(np.arctan2(-span[1] / length, span[2] / length)))
        reach = float(np.hypot(far[0] - self.torso[0], far[2] - self.torso[2]))
        return hits, phi, far, reach


def aim_ok(clip, time, phi):
    target = audit.BLADE_TARGET.get(clip)
    if target is None or time not in audit.HIT_FRAMES.get(clip, (None, ()))[1]:
        return True
    return audit.angle_gap(phi, target[0]) <= target[1]


def frame_for(geo, bones, clips, clip, time):
    """Cached frame: the parent chain never depends on the wrist angle."""
    key = (clip, time)
    frame = Frame.cache.get(key)
    if frame is None:
        frame = Frame(geo, bones, clips, clip, time)
        Frame.cache[key] = frame
    return frame


def clip_report(geo, bones, clips, clip, detail=False):
    seconds = float(clips[clip]["animation_length"])
    forms = CLIP_FORMS.get(clip, BOTH)
    bad = {}
    for form in forms:
        for time in frames_of(seconds):
            frame = frame_for(geo, bones, clips, clip, time)
            hits, _phi, _tip, _reach = frame.measure(bones, form, *wrist_at(clips, clip, time))
            if hits > GRAZE:
                bad[time] = max(bad.get(time, 0), hits)
    return bad


def wrist_at(clips, clip, time):
    """The sampled wrist angles at one frame (x, z)."""
    entry = clips[clip]["bones"].get("weapon_1", {}).get("rotation", {})
    keys = sorted((float(t), v) for t, v in entry.items())
    if not keys:
        return 0.0, 0.0
    if time <= keys[0][0]:
        return keys[0][1][0], keys[0][1][2]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 - 1e-9 <= time <= t1 + 1e-9:
            f = (time - t0) / max(1e-9, t1 - t0)
            f = f * f * (3.0 - 2.0 * f)
            return v0[0] + (v1[0] - v0[0]) * f, v0[2] + (v1[2] - v0[2]) * f
    return keys[-1][1][0], keys[-1][1][2]


def owner_of(keys, time):
    """Index of the key nearest this frame, and the stretch it controls."""
    times = [key[0] for key in keys]
    for index in range(len(keys) - 1):
        if times[index] - 1e-6 <= time <= times[index + 1] + 1e-6:
            owner = index if (time - times[index]) <= (times[index + 1] - time) else index + 1
            return owner, times[index], times[index + 1]
    return 0, times[0], times[-1]


def candidates(current_x, current_z):
    coarse = {(x, z) for x in range(-180, 181, 20) for z in range(-60, 61, 20)}
    fine = {(x, z) for x in range(int(current_x) - 40, int(current_x) + 41, 10)
            for z in range(int(current_z) - 40, int(current_z) + 41, 10)
            if -200 <= x <= 200 and -90 <= z <= 90}
    allowed = [(x, z) for x, z in coarse | fine
               if abs(x - current_x) <= MAX_TURN and abs(z - current_z) <= MAX_TURN]
    return sorted(allowed,
                  key=lambda pair: abs(pair[0] - current_x) + abs(pair[1] - current_z))


def stretch_score(geo, bones, clips, clip, keys, owner, window, forms, clip_name):
    """(hits in the stretch, aim still valid) for one trial key value."""
    score = 0
    aim_ok_all = True
    for frame_time in window:
        frame = frame_for(geo, bones, clips, clip, frame_time)
        wx, wz = wrist_at(clips, clip, frame_time)
        for form in forms:
            hits, phi, _tip, _reach = frame.measure(bones, form, wx, wz)
            score += hits
            if hits <= GRAZE and not aim_ok(clip_name, frame_time, phi):
                aim_ok_all = False
    return score, aim_ok_all


def solve_clip(geo, bones, clips, clip, table):
    keys = [list(key) for key in table[clip]]
    seconds = float(clips[clip]["animation_length"])
    forms = CLIP_FORMS.get(clip, BOTH)
    locked = {keys[0][0], keys[-1][0]}
    for _sweep in range(6):
        bad = clip_report(geo, bones, clips, clip)
        if not bad:
            break
        progressed = False
        for time in sorted(bad):
            owner, start, end = owner_of(keys, time)
            if keys[owner][0] in locked:
                owner = 1 if owner == 0 else len(keys) - 2
                if owner < 0 or keys[owner][0] in locked:
                    continue
                start, end = keys[max(0, owner - 1)][0], keys[min(len(keys) - 1, owner + 1)][0]
            window = [frame for frame in frames_of(seconds)
                      if start - 1e-6 <= frame <= end + 1e-6]
            base = list(keys[owner])
            best_score = None
            best_deviation = 0
            best_pair = None
            for x, z in candidates(base[1], base[2]):
                keys[owner] = [base[0], x, z]
                set_track(clips, clip, keys)
                score, aim_ok_all = stretch_score(geo, bones, clips, clip, keys, owner,
                                                  window, forms, clip)
                if aim_ok_all:
                    deviation = abs(x - base[1]) + abs(z - base[2])
                    if best_score is None or (score, deviation) < (best_score, best_deviation):
                        best_score, best_deviation, best_pair = score, deviation, (x, z)
                    if score <= GRAZE:
                        break
            keys[owner] = base
            set_track(clips, clip, keys)
            if best_pair is not None and best_score is not None and best_score <= GRAZE:
                keys[owner] = [base[0], best_pair[0], best_pair[1]]
                set_track(clips, clip, keys)
                progressed = True
                continue
            # nothing on the existing keys helps: steer the interpolation with a
            # new key exactly on the offending frame
            frame = frame_for(geo, bones, clips, clip, time)
            wx, wz = wrist_at(clips, clip, time)
            single_best = None
            for x, z in candidates(int(round(wx)), int(round(wz))):
                worst = 0
                for form in forms:
                    hits, phi, _tip, _reach = frame.measure(bones, form, x, z)
                    worst = max(worst, hits)
                    if hits <= GRAZE and not aim_ok(clip, time, phi):
                        worst = 10 ** 6
                if single_best is None or worst < single_best[0]:
                    single_best = (worst, x, z)
                if worst <= GRAZE:
                    break
            if single_best is not None:
                keys.append([time, single_best[1], single_best[2]])
                keys.sort(key=lambda key: key[0])
                set_track(clips, clip, keys)
                progressed = True
        if not progressed:
            break
    table[clip] = [tuple(key) for key in keys]
    set_track(clips, clip, table[clip])


def prune_clip(geo, bones, clips, clip, table):
    """Drop keys the clip does not need, so a solved wrist stays smooth."""
    unique = {}
    for time, x, z in table[clip]:
        unique[round(float(time), 3)] = [round(float(time), 3), float(x), float(z)]
    keys = [unique[time] for time in sorted(unique)]
    dropped = True
    while dropped and len(keys) > 2:
        dropped = False
        for index in range(1, len(keys) - 1):
            trial = [key for position, key in enumerate(keys) if position != index]
            set_track(clips, clip, trial)
            if not clip_report(geo, bones, clips, clip):
                keys = trial
                dropped = True
                break
    table[clip] = [tuple(key) for key in keys]
    set_track(clips, clip, table[clip])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--prune", action="store_true")
    parser.add_argument("--passes", type=int, default=3)
    args = parser.parse_args()

    table, _text = read_table()
    geo, bones = load_geometry()
    clips = json.loads(audit.ANIM.read_text(encoding="utf-8"))["animations"]

    if args.prune:
        for clip in clips:
            prune_clip(geo, bones, clips, clip, table)
        write_table(table)
        regenerate()
        print("pruned; run --check to confirm")
        return 0

    for attempt in range(args.passes):
        bad_total = 0
        for clip in clips:
            bad = clip_report(geo, bones, clips, clip)
            if bad:
                bad_total += len(bad)
                worst = max(bad.items(), key=lambda item: item[1])
                print(f"  {clip}: {len(bad)} frames clipped, worst {worst[1]} verts "
                      f"at t={worst[0]}")
        if bad_total == 0:
            print("all clips clear: the drawn blade never enters the body")
            return 0
        if args.check:
            print(f"{bad_total} frames still clip (--check: nothing changed)")
            return 1
        print(f"pass {attempt + 1}: fixing {bad_total} frames")
        for clip in clips:
            if clip_report(geo, bones, clips, clip):
                solve_clip(geo, bones, clips, clip, table)
        write_table(table)
        regenerate()
        clips = json.loads(audit.ANIM.read_text(encoding="utf-8"))["animations"]

    remaining = {clip: len(clip_report(geo, bones, clips, clip)) for clip in clips}
    remaining = {clip: count for clip, count in remaining.items() if count}
    if remaining:
        print("still clipped:", remaining)
        return 1
    print("all clips clear after the final pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
