"""Death poses: knee collapse, cooling rock pile, then hold while the renderer dissolves it."""
import json
import math
from pathlib import Path
from tortoise_transition_animation import sample, cube_vertices
from preview_geo_anim import mmul, translate, apply, rx, ry, rz

ROOT = Path(__file__).resolve().parents[1]
DURATION = 188


def rotation(angles):
    x, y, z = angles
    return mmul((rz(-z), [0, 0, 0]), mmul((ry(y), [0, 0, 0]), (rx(-x), [0, 0, 0])))


def transforms(geometry, pose, targets=None):
    bones = {b["name"]: b for b in geometry["bones"]}
    cache = {}
    def visit(name):
        if name not in cache:
            b = bones[name]
            pivot = b.get("pivot", [0, 0, 0])
            p = pose.get(name, {})
            angles = [a + c for a, c in zip(b.get("rotation", [0, 0, 0]), p.get("rotation", [0, 0, 0]))]
            local = mmul(translate(p.get("position", [0, 0, 0])),
                         mmul(translate(pivot), mmul(rotation(angles), translate([-v for v in pivot]))))
            cache[name] = mmul(visit(b["parent"]) if b.get("parent") else translate([0, 0, 0]), local)
        return cache[name]
    for name in targets if targets is not None else bones:
        visit(name)
    return cache


def load_geometry(phase):
    return json.loads((ROOT / f"src/main/resources/assets/tnc/geo/entity/volcanic_tortoise_phase{phase}.geo.json")
                      .read_text(encoding="utf-8"))["minecraft:geometry"][0]


def pose_at(tick):
    def value(keys): return sample(keys, tick)
    pose = {
        "body": {"position": [0, value({0: 0, 12: -0.05, 34: -1, 44: -6.4, 50: -6.7, 94: -7, 116: -8, 188: -8}), 0],
                 "rotation": [value({0: 0, 12: 0, 34: 6, 44: 6, 94: 3, 116: 2, 188: 2}), 0, 0]},
        "shell": {"rotation": [value({0: 0, 34: 0, 44: 2, 50: -1, 94: -2, 188: -2}), 0, 0]},
        "whorl": {"position": [0, value({0: 0, 50: 0, 94: -0.8, 116: -1.2, 188: -1.2}), 0]},
        "neck": {"rotation": [value({0: 4, 12: 4, 34: 8, 44: 4, 94: 2, 188: 2}), 0, 0]},
        "head": {"rotation": [value({0: 0, 3: -3, 12: -3, 188: -3}), 0, 0]},
        "jaw": {"rotation": [value({0: 0, 34: 2, 44: 18, 188: 18}), 0, 0]},
        "tail": {"rotation": [value({0: 0, 12: 0, 34: 12, 44: 8, 94: 2, 116: 0, 188: 0}),
                              value({0: -5, 34: 0, 188: 0}), 0]},
    }
    for name, sign in (("plate_l", -1), ("plate_r", 1)):
        pose[name] = {"rotation": [0, 0, sign * value({0: 22, 34: 22, 44: 10, 94: 2, 116: 0, 188: 0})]}
    for name in ("skirt_ch3", "skirt_ch4"):
        pose[name] = {"position": [0, value({0: 0, 34: 0, 44: -0.7, 94: -1, 188: -1}), 0]}
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        front = name.startswith("leg_f")
        sign = 1 if name.endswith("l") else -1
        fold = value({0: 0, 12: 0, 34: 25 if front else -10, 44: 64 if front else -42,
                      116: 78 if front else -70, 188: 78 if front else -70})
        spread = value({0: 0, 12: 0, 34: 14 if front else -8, 44: 20 if front else -10,
                        116: 10 if front else -4, 188: 10 if front else -4})
        pose[name] = {"rotation": [fold, 0, sign * spread], "position": [0, 0, 0]}
    return pose


def fitted_poses(phase):
    geo = load_geometry(phase)
    points = {b["name"]: [p for c in b.get("cubes", []) for p in cube_vertices(c)] for b in geo["bones"]}
    head_bones = [name for name in ("head", "jaw") if name in points]
    def head_min(pose):
        tf = transforms(geo, pose, head_bones)
        return min(apply(tf[name], p)[1] for name in head_bones for p in points[name])
    def base_pose(tick):
        pose = pose_at(tick)
        # The longer P2 beak and hanging lower jaw require a shallower torso drop.
        if phase == 2: pose["body"]["position"][1] *= 0.5
        return pose
    held_head = head_min(base_pose(12))
    poses = []
    for tick in range(DURATION + 1):
        pose = base_pose(tick)
        if tick > 12:
            target = sample({12: held_head, 34: held_head * 0.55, 44: 0.8, 94: 0.5, 116: 0.3, 188: 0.3}, tick)
            # The rear of the skull drops if the head tilts too far up. Locate the safe peak
            # first, then solve on its downward-facing branch instead of assuming monotonicity.
            samples = []
            for angle in range(-45, 36, 5):
                pose["head"]["rotation"][0] = angle
                samples.append((head_min(pose), angle))
            maximum, low = max(samples)
            assert maximum >= target - 0.001, (phase, tick, maximum, target)
            high = 35
            for _ in range(16):
                angle = (low + high) / 2
                pose["head"]["rotation"][0] = angle
                if head_min(pose) > target: low = angle
                else: high = angle
            pose["head"]["rotation"][0] = (low + high) / 2
        for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            tf = transforms(geo, pose)
            minimum = min(apply(tf[name], p)[1] for p in points[name])
            # Parent body pitch tilts local Y; compensate in world height without scaling the leg.
            cosine = math.cos(math.radians(pose["body"]["rotation"][0]))
            pose[name]["position"][1] = (0.03 - minimum) / cosine
        poses.append(pose)
    return poses


def build_death():
    phases = [fitted_poses(1), fitted_poses(2)]
    bones = {}
    for name, channels in phases[0][0].items():
        bones[name] = {}
        for channel in channels:
            keys = {}
            for tick in range(DURATION + 1):
                vector = []
                for axis in range(3):
                    a, b = [round(poses[tick][name][channel][axis], 5) for poses in phases]
                    vector.append(a if abs(a - b) < 0.00001 else f"query.tnc_phase_two ? {b} : {a}")
                keys[f"{tick / 20:g}"] = vector
            bones[name][channel] = keys
    return {"loop": False, "animation_length": DURATION / 20, "bones": bones}
