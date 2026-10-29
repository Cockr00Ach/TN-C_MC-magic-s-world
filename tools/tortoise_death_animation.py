"""Death poses: slowly bow without collapsing, then hold during the whole-body fade."""
import json
import math
from pathlib import Path
from tortoise_transition_animation import sample, cube_vertices
from preview_geo_anim import mmul, translate, apply, rx, ry, rz

ROOT = Path(__file__).resolve().parents[1]
DURATION = 244


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
        "body": {"position": [0, value({0: 0, 20: 0, 120: -1.2, 244: -1.2}), 0],
                 "rotation": [0, 0, 0]},
        "shell": {"rotation": [0, 0, 0]},
        "whorl": {"position": [0, 0, 0]},
        "neck": {"rotation": [value({0: 0, 20: 0, 120: 8, 244: 8}), 0, 0]},
        "head": {"rotation": [value({0: 0, 20: 0, 120: 12, 244: 12}), 0, 0]},
        "jaw": {"rotation": [0, 0, 0]},
        "tail": {"rotation": [value({0: 0, 60: 0, 120: -3, 180: -3}), 0, 0]},
    }
    for name, sign in (("plate_l", -1), ("plate_r", 1)):
        pose[name] = {"rotation": [0, 0, sign * 22]}
    for name in ("skirt_ch3", "skirt_ch4"):
        pose[name] = {"position": [0, 0, 0]}
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        pose[name] = {"rotation": [0, 0, 0], "position": [0, 0, 0]}
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
        # Keep the taller phase-two torso close to its existing stance.
        if phase == 2: pose["body"]["position"][1] *= 0.5
        return pose
    poses = []
    for tick in range(DURATION + 1):
        pose = base_pose(tick)
        if phase == 2:
            pose["neck"]["rotation"][0] = sample({0: 0, 20: 0, 120: -10, 244: -10}, tick)
            pose["head"]["rotation"][0] = sample({0: 0, 20: 0, 120: 28, 244: 28}, tick)
        assert head_min(pose) >= 0.03, (phase, tick, "head/jaw below ground")
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
            # Keep both ends of constant runs; preserve every changing fitted pose.
            items = list(keys.items())
            bones[name][channel] = {time: vector for i, (time, vector) in enumerate(items)
                                    if i == 0 or i == len(items) - 1
                                    or vector != items[i - 1][1] or vector != items[i + 1][1]}
    return {"loop": False, "animation_length": DURATION / 20, "bones": bones}
