"""Five-beat, 160-tick transition. Reads existing geometry; never regenerates art."""
from pathlib import Path
import itertools
import json
import math

ROOT = Path(__file__).resolve().parents[1]
GEO = ROOT / "src/main/resources/assets/tnc/geo/entity"


def sample(keys, tick):
    entries = sorted(keys.items())
    if tick <= entries[0][0]:
        return entries[0][1]
    for (a, av), (b, bv) in zip(entries, entries[1:]):
        if tick <= b:
            return av + (bv - av) * (tick - a) / (b - a)
    return entries[-1][1]


def channel(keys, axis=0):
    result = {}
    for tick, value in keys.items():
        vector = [0, 0, 0]
        vector[axis] = round(value, 5)
        result[f"{tick / 20:g}"] = vector
    return result


def body_y(tick):
    if tick < 24:
        t = tick / 24
        value = -4.8 * t * t * (3 - 2 * t)
        if tick in (4, 8, 12, 16, 20):
            value += (0.06 + 0.08 * t) * (1 if tick % 8 else -1)
        return value
    return sample({24: -4.8, 30: -4.8, 31: -1.0, 32: 6.4, 34: 5.2,
                   40: 2.8, 44: -0.6, 52: 0, 100: 0}, tick)


def cube_vertices(cube):
    origin, size = cube["origin"], cube["size"]
    pivot = cube.get("pivot", [0, 0, 0])
    # Gecko mirrors geometry X, negates authored X/Y angles and renders Rz * Ry * Rx.
    # Conjugating back into the original Bedrock coordinates gives (-X, +Y, -Z).
    rotation = [math.radians(value * sign) for value, sign in
                zip(cube.get("rotation", [0, 0, 0]), (-1, 1, -1))]
    for bits in itertools.product((0, 1), repeat=3):
        point = [origin[i] + bits[i] * size[i] - pivot[i] for i in range(3)]
        for axis in (0, 1, 2):
            a, b = ((1, 2), (2, 0), (0, 1))[axis]
            c, s = math.cos(rotation[axis]), math.sin(rotation[axis])
            point[a], point[b] = point[a] * c - point[b] * s, point[a] * s + point[b] * c
        yield [point[i] + pivot[i] for i in range(3)]


def foot_offset(bone, angle):
    points = [point for cube in bone.get("cubes", []) for point in cube_vertices(cube)]
    px, py, _ = bone["pivot"]
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    return min(point[1] for point in points) - min(
        py + s * (point[0] - px) + c * (point[1] - py) for point in points)


# Preserve existing poses; stretch the visible opening separately from the model-swap flash.
def retime_tick(tick):
    return sample({0: 0, 24: 40, 30: 52, 31: 53, 32: 70, 33: 73, 34: 76,
                   38: 84, 40: 90, 44: 98, 52: 112, 60: 118, 78: 124,
                   80: 130, 84: 134, 86: 138, 88: 140, 100: 160}, tick)


def build_transition():
    body = {tick: body_y(tick) for tick in range(101)}
    bones = {
        "body": {"position": channel(body, 1), "rotation": channel({0: 0, 100: 0})},
        "shell": {"rotation": channel({0: 0, 24: 4.0, 30: 4.0, 32: -6, 34: -4,
                                         40: 3.0, 44: -1.6, 52: 0, 100: 0})},
        "whorl": {"rotation": channel({0: 0, 24: 0, 30: 0, 32: 5, 40: -2.5, 52: 0, 100: 0}, 2)},
        "neck": {"rotation": channel({0: 4, 24: 8, 30: 8, 31: -26, 34: -20,
                                        40: -12, 52: 4, 78: -3, 80: -18, 84: -22, 88: -8, 100: 4})},
        "head": {"rotation": channel({0: 0, 24: 4, 30: 4, 31: -24, 34: -18,
                                        40: -8, 52: 0, 78: -4, 80: -15, 84: -10, 88: -3, 100: 0})},
        # This model faces -Z. Gecko negates authored X, so +32 opens its lower jaw downwards.
        "jaw": {"rotation": channel({0: 0, 30: 0, 31: 32, 34: 26, 40: 12, 44: 0,
                                       78: 0, 80: 32, 86: 32, 88: 0, 100: 0})},
        "tail": {"rotation": channel({0: -5, 24: -8, 30: -8, 32: 8, 40: -3, 52: 0,
                                        78: 0, 88: 0, 100: -5}, 1)},
    }
    plate = {0: 0, 30: 0, 31: 0, 32: 34, 33: 24, 34: 22, 40: 26, 44: 20, 52: 22, 100: 22}
    for name, sign in (("plate_l", -1), ("plate_r", 1)):
        bones[name] = {"rotation": channel({tick: value * sign for tick, value in plate.items()}, 2)}
    # idle_p2 offsets its right plate's breathing phase; blend to that exact starting pose.
    bones["plate_r"]["rotation"]["4.4"] = [0, 0, 22]
    bones["plate_r"]["rotation"]["5"] = [0, 0, 21.41]
    for name in ("skirt_ch3", "skirt_ch4"):
        bones[name] = {"position": channel({0: 0, 34: 0, 38: -1.2, 40: -1.2, 44: 0.24, 52: 0, 100: 0}, 1)}

    geos = [{bone["name"]: bone for bone in json.loads(
        (GEO / f"volcanic_tortoise_phase{phase}.geo.json").read_text(encoding="utf-8"))
        ["minecraft:geometry"][0]["bones"]} for phase in (1, 2)]
    spread = {0: 0, 24: 7, 30: 7, 32: 9, 34: 7, 40: 5, 52: 0, 100: 0}
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        # Authored Z is mirrored by Gecko's geometry-X conversion: negative opens left legs.
        sign = -1 if name.endswith("l") else 1
        positions = {}
        for tick in range(101):
            angle = sign * sample(spread, tick)
            values = [round(-body[tick] + foot_offset(geo[name], -angle), 5) for geo in geos]
            y = values[0] if abs(values[0] - values[1]) < 0.00001 else f"query.tnc_phase_two ? {values[1]} : {values[0]}"
            positions[f"{tick / 20:g}"] = [0, y, 0]
        bones[name] = {"rotation": channel({tick: sign * value for tick, value in spread.items()}, 2),
                       "position": positions}
    for channels in bones.values():
        for name, keys in channels.items():
            # A piecewise time warp must split every channel at its rate-change boundaries.
            # Otherwise rotation can drift from the densely sampled foot-position compensation.
            original = {float(time) * 20: value for time, value in keys.items()}
            for tick in (0, 24, 30, 31, 32, 33, 34, 38, 40, 44, 52, 60, 78, 80, 84, 86, 88, 100):
                if tick not in original:
                    keys[f"{tick / 20:g}"] = [round(sample({t: v[axis] for t, v in original.items()}, tick), 5)
                                              for axis in range(3)]
            channels[name] = {f"{retime_tick(float(time) * 20) / 20:.6f}".rstrip("0").rstrip("."): value
                              for time, value in keys.items()}
    return {"loop": False, "animation_length": 8.0, "bones": bones}
