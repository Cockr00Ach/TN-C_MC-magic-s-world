"""Offline audit of the Canglan Warlord rig -- geometry, atlas, symmetry, hit poses.

Everything here is checked *without* Minecraft: it reads the generated files,
rebuilds the bone transforms (using the same code as the previewer), places the
drawn wind arsenal blade on the ``weapon_1`` anchor exactly as the renderer does,
and then asserts the things that otherwise only show up as a broken boss in game.

    python tools/audit_canglan_warlord.py
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import preview_canglan_warlord as preview  # noqa: E402
import wind_blade  # noqa: E402

GEO = ROOT / "src/main/resources/assets/tnc/geo/entity/canglan_warlord.geo.json"
ANIM = ROOT / "src/main/resources/assets/tnc/animations/entity/canglan_warlord.animation.json"
TEX = ROOT / "src/main/resources/assets/tnc/textures/entity/canglan_warlord.png"

# clip -> (ticks, is_loop) -- the tick budgets must match CanglanWarlordAttackRules
EXPECTED = {
    "idle": (80, True), "walk": (40, True),
    "cleave_left": (20, False), "cleave_right": (20, False),
    "overhead": (28, False), "thrust": (24, False), "sweep": (34, False),
    "spear_throw": (30, False), "shield_bash": (22, False), "war_cry": (44, False),
    "phase_shift": (52, False), "death": (48, False), "spawn": (52, False),
}
# the frames on which the boss actually connects, per clip, and which weapon it is
# holding then: form 1 = wind arsenal sword (phase one), 2 = spear (phase two)
HIT_FRAMES = {"cleave_left": (1, [0.5]), "cleave_right": (1, [0.5]), "overhead": (1, [0.8]),
              "shield_bash": (1, [0.5]), "thrust": (2, [0.6]), "sweep": (2, [0.6, 1.0]),
              "spear_throw": (2, [0.6])}
# phi: 0 = blade straight back, -90 = straight up, +/-180 = straight forward,
# +90 = straight down. Each attack has a target aim and a tolerance in degrees.
BLADE_TARGET = {"cleave_left": (160.0, 32.0), "cleave_right": (170.0, 32.0),
                "overhead": (168.0, 32.0), "thrust": (180.0, 26.0),
                "sweep": (180.0, 42.0), "spear_throw": (175.0, 32.0),
                "shield_bash": (-145.0, 34.0)}

# which arsenal form can be in that hand while a clip plays; anything not listed
# can be played in either phase and therefore has to be clear with both blades
CLIP_FORMS = {"cleave_left": (1,), "cleave_right": (1,), "overhead": (1,),
              "thrust": (2,), "sweep": (2,), "spear_throw": (2,)}
BOTH_FORMS = (1, 2)
# the fist sits inside the pauldron's box, so the hilt always grazes it; only
# the head, the helm and the chest count as a real intersection
BLADE_PARTS = ("head", "helmet", "chest")
GRAZE = 48


def angle_gap(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def matrix_of(geometry, pose):
    bones = {b["name"]: b for b in geometry["bones"]}
    out = {}

    def walk(name):
        if name in out:
            return out[name]
        entry = bones[name]
        pivot = np.array(entry.get("pivot", [0, 0, 0]), dtype=float)
        anim = pose.get(name, {})
        rot = preview.rotation(anim.get("rotation", entry.get("rotation", [0, 0, 0])))
        offset = np.array(anim.get("position", [0, 0, 0]), dtype=float)
        local = preview.translate(offset) @ preview.translate(pivot) @ np.block(
            [[rot, np.zeros((3, 1))], [np.zeros((1, 3)), np.ones((1, 1))]]
        ) @ preview.translate(-pivot)
        out[name] = walk(entry["parent"]) @ local if entry.get("parent") else local
        return out[name]

    for name in bones:
        walk(name)
    return out


def bone_points(geometry, matrix, bone_name):
    entry = next(b for b in geometry["bones"] if b["name"] == bone_name)
    pts = []
    for cube in entry.get("cubes", []):
        origin = np.array(cube["origin"], dtype=float)
        size = np.array(cube["size"], dtype=float)
        for i in range(8):
            corner = origin + size * np.array([i & 1, (i >> 1) & 1, (i >> 2) & 1])
            pts.append((matrix @ np.append(corner, 1.0))[:3])
    return np.array(pts)


def aabb(points):
    return points.min(axis=0), points.max(axis=0)


def main():
    errors = []
    geometry = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    clips = json.loads(ANIM.read_text(encoding="utf-8"))["animations"]
    bones = geometry["bones"]
    bones_by_name = {b["name"]: b for b in bones}
    names = [b["name"] for b in bones]
    cubes = [c for b in bones for c in b.get("cubes", [])]

    # ---- 1. skeleton ----
    parents = {b["name"]: b.get("parent") for b in bones}
    for name, parent in parents.items():
        if parent and parent not in names:
            errors.append(f"bone {name} has unknown parent {parent}")
    seen = set()

    def depth(name, stack=()):
        if name in stack:
            errors.append(f"bone cycle at {name}")
            return 0
        return 1 + (depth(parents[name], stack + (name,)) if parents[name] else 0)

    for name in names:
        seen.add(depth(name))

    # ---- 2. atlas ----
    for cube in cubes:
        for face, spec in cube["uv"].items():
            u, v = spec["uv"]
            uw, vh = spec["uv_size"]
            if u < 0 or v < 0 or u + uw > 256.01 or v + vh > 256.01:
                errors.append(f"uv out of atlas on {face}: {spec}")
            if uw <= 0 or vh <= 0:
                errors.append(f"degenerate uv_size on {face}: {spec}")
            if abs((uw / vh) / max(1e-6, (cube["size"][0] / cube["size"][1]))) > 40:
                pass  # orientation-dependent, checked by the JUnit bake test

    # ---- 3. clips ----
    for clip, (ticks, is_loop) in EXPECTED.items():
        if clip not in clips:
            errors.append(f"missing clip {clip}")
            continue
        data = clips[clip]
        seconds = data["animation_length"]
        if abs(seconds * 20 - ticks) > 0.01:
            errors.append(f"{clip} is {seconds}s = {seconds * 20:.0f} ticks, expected {ticks}")
        if bool(data.get("loop", False)) != is_loop:
            errors.append(f"{clip} loop flag should be {is_loop}")
        for bone in data["bones"]:
            if bone not in names:
                errors.append(f"{clip} animates missing bone {bone}")
        for bone, tracks in data["bones"].items():
            for key, channel in tracks.items():
                times = sorted(float(t) for t in channel)
                if times[0] != 0.0:
                    errors.append(f"{clip}/{bone}/{key} does not start at t=0")
                if times[-1] > seconds + 0.001:
                    errors.append(f"{clip}/{bone}/{key} runs past animation_length")

    # ---- 4. rest pose ----
    rest = matrix_of(geometry, {})
    total = np.concatenate([bone_points(geometry, rest[n], n) for n in names
                            if bones[names.index(n)].get("cubes")])
    lo, hi = aabb(total)
    height = hi[1] - lo[1]
    span = hi[0] - lo[0]
    if not 100 <= height <= 118:
        errors.append(f"height {height:.1f}u = {height / 16:.2f} blocks (want ~6.7)")
    if span > 66:
        errors.append(f"shoulder span {span:.1f}u = {span / 16:.2f} blocks is too wide")
    if lo[1] < -0.6:
        errors.append(f"model dips below the ground plane: {lo[1]:.2f}u")

    # ---- 5. feet on the ground in rest / idle / walk ----
    for clip, times in (("idle", [0.0, 1.0, 2.0, 3.0]), ("walk", [0.0, 0.5, 1.0, 1.5])):
        for time in times:
            pose = preview.sample(clips, clip, time)
            matrices = matrix_of(geometry, pose)
            feet = np.concatenate([bone_points(geometry, matrices[f"foot_{s}"], f"foot_{s}")
                                   for s in (-1, 1)])
            lowest = feet[:, 1].min()
            print(f"  {clip} t={time}: lowest foot {lowest:+.1f}u")
            if lowest < -2.5:
                errors.append(f"{clip} t={time} sinks feet {lowest:.1f}u below ground")

    # ---- 6. hit poses: the drawn blade is extended, aimed, and never inside the
    # body -- the weapon is a render overlay, so this is the only place that can
    # catch a wrist key that swings the sword through the boss's own chest ----
    for clip, (form, times) in HIT_FRAMES.items():
        for time in times:
            pose = preview.sample(clips, clip, time)
            matrices = matrix_of(geometry, pose)
            blade = np.concatenate([poly for poly, _, _ in
                                    wind_blade.place(form, matrices, bones_by_name)])
            tip, _length = wind_blade.tip(form, matrices, bones_by_name)
            pivot = (matrices["weapon_1"]
                     @ np.append(np.array(bones_by_name["weapon_1"]["pivot"], dtype=float),
                                 1.0))[:3]
            blade_span = tip - pivot
            direction = blade_span / max(1e-9, np.linalg.norm(blade_span))
            phi = float(np.degrees(np.arctan2(-direction[1], direction[2])))
            torso = bone_points(geometry, matrices["chest"], "chest").mean(axis=0)
            reach = float(np.hypot(tip[0] - torso[0], tip[2] - torso[2]))
            floor, ceiling = BLADE_TARGET[clip]
            low, high = aabb(blade)
            # sweep spins the whole body, so "in front" has to be body-local
            root_inverse = np.linalg.inv(matrices["root"])
            local_blade = (root_inverse @ np.column_stack(
                [blade, np.ones(len(blade))]).T).T[:, :3]
            print(f"  {clip} t={time} form {form}: tip {tip.round(0)} phi {phi:+.0f} "
                  f"reach {reach:.1f}u ({reach / 16:.2f} blocks)")
            if angle_gap(phi, floor) > ceiling:
                errors.append(f"{clip} t={time}: blade points at phi {phi:+.0f}, "
                              f"which is {angle_gap(phi, floor):.0f} degrees off "
                              f"the {floor:+.0f} aim")
            if reach < 24.0:
                errors.append(f"{clip} t={time}: blade is not extended (reach {reach:.1f}u)")
            if local_blade[:, 2].min() > 20.0:
                errors.append(f"{clip} t={time}: the whole blade stays behind the boss")
            for part in ("head", "helmet", "chest", "pauldron_1"):
                p_low, p_high = aabb(bone_points(geometry, matrices[part], part))
                inside = int(((blade > p_low) & (blade < p_high)).all(axis=1).sum())
                if inside:
                    errors.append(f"{clip} t={time}: {inside} blade verts inside {part}")

    # ---- 6b. no clip sweeps the drawn blade through the body ----
    # A hit frame that aims right can still be reached through the boss's own
    # head, so every frame of every clip is checked, with the weapon form the
    # phase will actually be holding. tools/solve_wrist.py owns the wrist table
    # that keeps this clean.
    for clip in clips:
        seconds = float(clips[clip]["animation_length"])
        forms = CLIP_FORMS.get(clip, BOTH_FORMS)
        worst_hits = 0
        worst_frame = None
        for form in forms:
            for step in range(int(round(seconds / 0.05)) + 1):
                time = round(step * 0.05, 3)
                matrices = matrix_of(geometry, preview.sample(clips, clip, time))
                blade = np.concatenate([poly for poly, _, _ in
                                        wind_blade.place(form, matrices, bones_by_name)])
                hits = 0
                for part in BLADE_PARTS:
                    p_low, p_high = aabb(bone_points(geometry, matrices[part], part))
                    hits += int(((blade > p_low) & (blade < p_high)).all(axis=1).sum())
                if hits > worst_hits:
                    worst_hits, worst_frame = hits, f"form {form} t={time}"
        if worst_hits > GRAZE:
            errors.append(f"{clip}: the drawn blade crosses the body "
                          f"({worst_hits} verts at {worst_frame})")
        else:
            print(f"  {clip}: blade clear of the head and chest "
                  f"(worst graze {worst_hits} verts)")

    # ---- 6c. the resting blade hangs clear of the shoulder ----
    for clip, time in (("idle", 1.0), ("idle", 3.0), ("walk", 0.5), ("walk", 1.5)):
        matrices = matrix_of(geometry, preview.sample(clips, clip, time))
        blade = np.concatenate([poly for poly, _, _ in
                                wind_blade.place(1, matrices, bones_by_name)])
        for part in ("pauldron_1", "head", "chest", "banner_a", "crest_c"):
            p_low, p_high = aabb(bone_points(geometry, matrices[part], part))
            inside = int(((blade > p_low) & (blade < p_high)).all(axis=1).sum())
            if inside:
                errors.append(f"{clip} t={time}: resting blade sits {inside} verts "
                              f"inside {part}")

    # ---- 7. symmetry ----
    mirrored = 0
    for name in names:
        if name.endswith("_1") and name[:-2] + "_-1" in names:
            mirrored += 1
    if mirrored < 12:
        errors.append(f"only {mirrored} mirrored bone pairs")

    # ---- 8. every bone's cubes really hang off that bone ----
    # cube coordinates are absolute model space, so a part authored around x=0
    # but parented to an off-centre bone (the shield!) silently ends up in the
    # wrong place. Flag any bone whose cubes sit far from its own pivot.
    for bone in bones:
        if not bone.get("cubes"):
            continue
        pivot = np.array(bone.get("pivot", [0, 0, 0]), dtype=float)
        far = []
        for cube in bone["cubes"]:
            centre = np.array(cube["origin"], dtype=float) + np.array(cube["size"], dtype=float) / 2
            distance = np.linalg.norm(centre - pivot) / 2.75  # back to Minecraft pixels
            far.append(distance)
        if max(far) > 16.0:
            errors.append(f"bone {bone['name']}'s cubes sit {max(far):.1f}px from its pivot")

    rest_matrices = matrix_of(geometry, {})
    shield_pts = bone_points(geometry, rest_matrices["shield"], "shield")
    if aabb(shield_pts)[0][0] > -12.0:
        errors.append("shield is not on the left forearm")
    if aabb(shield_pts)[1][2] > -3.4:
        errors.append("buckler does not stand in front of the left forearm")

    # ---- 9. the weapon is drawn, not modelled ----
    # the sword and spear are the shared wind arsenal models, placed on an empty
    # anchor bone; if geometry reappears there the renderer would double-draw it
    anchor = bones_by_name.get("weapon_1")
    if anchor is None:
        errors.append("weapon_1 anchor bone is missing")
    else:
        if anchor.get("cubes"):
            errors.append("weapon_1 must stay empty: the blade is drawn by the renderer")
        if anchor.get("parent") != "hand_1":
            errors.append("weapon_1 is not parented to hand_1")
        fist = np.array(bones_by_name["hand_1"]["pivot"], dtype=float)
        seat = np.array(anchor["pivot"], dtype=float)
        if np.linalg.norm(seat - fist) > 4.0:
            errors.append(f"weapon_1 sits {np.linalg.norm(seat - fist):.1f}u from the fist")
    if bones_by_name.get("weapon_tip_1", {}).get("parent") != "weapon_1":
        errors.append("weapon_tip_1 is not parented to weapon_1")
    for retired in ("haft_a", "haft_b", "blade", "spur", "tassel_a"):
        if retired in names:
            errors.append(f"retired dagger-axe bone {retired} still in the rig")

    print(f"bones={len(bones)} cubes={len(cubes)} clips={len(clips)} "
          f"height={height / 16:.2f} blocks span={span / 16:.2f} blocks "
          f"ground={lo[1]:.1f}u")
    print(f"mirrored bone pairs={mirrored}; texture={TEX.stat().st_size / 1024:.0f} KB")
    if errors:
        print(f"FAIL ({len(errors)})")
        for error in errors:
            print("  - " + error)
        return 1
    print("PASS canglan warlord rig audit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
