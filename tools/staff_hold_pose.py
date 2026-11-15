"""Author staff transforms against the real player hand attachment, without changing meshes."""
import argparse
import hashlib
import json
import math
from pathlib import Path

# The qimei staff uses the standard weapon grip. Its +Z axis, transformed by
# the weapon's third-person display, matches a +Y staff rotated 11° about X.
WEAPON_GRIP = (0, -1.91910, 1.54370)


def item_display(grip, tip, first_person_height=14.0, third_person_scale=.94):
    result = {}
    for first in (False, True):
        rotation = [0, -25, -10] if first else [11, 0, 0]
        scale = first_person_height / (tip[1] - grip[1]) if first else third_person_scale
        x, y, z = [(v - 8) * scale for v in grip]
        rz, ry, rx = [math.radians(rotation[i]) for i in (2, 1, 0)]
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        target = (0, 0, 0) if first else WEAPON_GRIP
        for hand in ("righthand", "lefthand"):
            result[("firstperson_" if first else "thirdperson_") + hand] = {
                "rotation": rotation[:],
                "translation": [round(target[i] - v, 8) for i, v in enumerate((x, y, z))],
                "scale": [scale] * 3,
            }
    result["ground"] = dict(rotation=[0, 0, 15], translation=[0, 3, 0], scale=[.35] * 3)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    held = root / "src/main/resources/assets/tnc/models/item/wands/held"
    changed = 0
    fingerprints = []
    for path in sorted(held.glob("*.json")):
        text = path.read_text(encoding="utf-8")
        model = json.loads(text)
        expected = item_display(model["tnc_grip"], model["tnc_tip"])
        if model["display"] != expected:
            if args.check:
                raise AssertionError(f"Stale player hand transform: {path.name}")
            start = text.index("{", text.index('"display"'))
            _, length = json.JSONDecoder().raw_decode(text[start:])
            replacement = json.dumps(expected, ensure_ascii=False, indent=2).replace("\n", "\n  ")
            updated = text[:start] + replacement + text[start + length:]
            check = json.loads(updated)
            original_payload = {k: v for k, v in model.items() if k != "display"}
            assert {k: v for k, v in check.items() if k != "display"} == original_payload
            path.write_text(updated, encoding="utf-8", newline="\n")
            changed += 1
        fingerprints.append(hashlib.sha256(json.dumps(model["surfaces"], sort_keys=True).encode()).hexdigest())
    assert len(fingerprints) == 36, "Expected the legacy staff and all 35 elemental staffs"
    print(f"Staff hand transforms: {len(fingerprints)} checked, {changed} updated; all meshes retained")


if __name__ == "__main__":
    main()
