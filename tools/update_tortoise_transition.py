"""Update only the transition clip and skirt groups; preserve every handmade cube/UV/other clip."""
from pathlib import Path
import argparse
import ast
import copy
import json
import re
import shutil
import uuid
from tortoise_transition_animation import build_transition
from embed_animations import collect_bones, stable_uuid

ROOT = Path(__file__).resolve().parents[1]
GEO = ROOT / "src/main/resources/assets/tnc/geo/entity/volcanic_tortoise_phase2.geo.json"
ANIM = ROOT / "src/main/resources/assets/tnc/animations/entity/volcanic_tortoise.animation.json"
GEN = ROOT / "tools/gen_volcanic_tortoise_art.py"
BB = [ROOT / f"art/volcanic_tortoise/volcanic_tortoise_phase{phase}.bbmodel" for phase in (1, 2)]


def canonical_cubes(geo):
    return sorted(json.dumps(cube, sort_keys=True) for bone in geo["minecraft:geometry"][0]["bones"] for cube in bone.get("cubes", []))


def find_group(nodes, name):
    for node in nodes:
        if isinstance(node, dict):
            if node.get("name") == name:
                return node
            result = find_group(node.get("children", []), name)
            if result:
                return result
    return None


def resolve(value, phase):
    if isinstance(value, str):
        match = re.fullmatch(r"query\.tnc_phase_two \? ([-\d.]+) : ([-\d.]+)", value)
        assert match, value
        return float(match.group(1 if phase == 2 else 2))
    return value


def embed_transition(doc, clip, phase):
    groups = collect_bones(doc)
    animators = {}
    for bone, channels in clip["bones"].items():
        if bone not in groups:
            continue
        keys = []
        for channel, values in channels.items():
            for time, vector in values.items():
                point = [resolve(value, phase) for value in vector]
                keys.append({"channel": channel, "time": float(time), "interpolation": "linear",
                             "data_points": [dict(zip(("x", "y", "z"), point))],
                             "uuid": stable_uuid(f"transition|{bone}|{channel}|{time}"), "color": -1})
        animators[groups[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
    previous = next(anim for anim in doc["animations"] if anim["name"] == "transition")
    previous.update({"length": clip["animation_length"], "loop": "once", "animators": animators})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-dir", type=Path, required=True)
    args = parser.parse_args()
    args.backup_dir.mkdir(parents=True, exist_ok=True)
    files = [GEO, ANIM, GEN, *BB]
    for path in files:
        backup = args.backup_dir / path.relative_to(ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists():
            shutil.copyfile(path, backup)

    geo = json.loads(GEO.read_text(encoding="utf-8"))
    before_cubes = canonical_cubes(geo)
    docs = [json.loads(path.read_text(encoding="utf-8")) for path in BB]
    before_elements = [copy.deepcopy(doc["elements"]) for doc in docs]
    before_textures = [copy.deepcopy(doc.get("textures")) for doc in docs]
    before_other = [[copy.deepcopy(anim) for anim in doc["animations"] if anim["name"] != "transition"] for doc in docs]
    shell = next(bone for bone in geo["minecraft:geometry"][0]["bones"] if bone["name"] == "shell")
    parent = find_group(docs[1]["outliner"], "shell")
    for name in ("ch3", "ch4"):
        group_name = "skirt_" + name
        element = next(element for element in docs[1]["elements"] if element["name"] == name)
        if not any(bone["name"] == group_name for bone in geo["minecraft:geometry"][0]["bones"]):
            cube = next(cube for cube in shell["cubes"] if cube.get("name") == name)
            shell["cubes"].remove(cube)
            geo["minecraft:geometry"][0]["bones"].append({"name": group_name, "parent": "shell",
                "pivot": element["origin"], "cubes": [cube]})
        if find_group(docs[1]["outliner"], group_name) is None:
            index = parent["children"].index(element["uuid"])
            parent["children"][index] = {"name": group_name, "origin": element["origin"], "rotation": [0, 0, 0],
                "uuid": uuid.uuid5(uuid.NAMESPACE_URL, "tnc:transition:" + group_name).hex,
                "export": True, "isOpen": False, "locked": False, "visibility": True, "children": [element["uuid"]]}

    clip = build_transition()
    animation = json.loads(ANIM.read_text(encoding="utf-8"))
    other_clips = {name: copy.deepcopy(value) for name, value in animation["animations"].items() if name != "transition"}
    animation["animations"]["transition"] = clip
    for phase, doc in enumerate(docs, 1):
        embed_transition(doc, clip, phase)
        assert doc["elements"] == before_elements[phase - 1]
        assert doc.get("textures") == before_textures[phase - 1]
        assert [anim for anim in doc["animations"] if anim["name"] != "transition"] == before_other[phase - 1]
    assert canonical_cubes(geo) == before_cubes
    assert {name: value for name, value in animation["animations"].items() if name != "transition"} == other_clips

    # Replace the old transition expression only; never run the full art generator.
    source = GEN.read_text(encoding="utf-8")
    lines = source.splitlines(keepends=True)
    function = next(node for node in ast.parse(source).body if isinstance(node, ast.FunctionDef) and node.name == "build_animations")
    assignment = next(node for node in function.body if isinstance(node, ast.Assign)
                      and any(isinstance(target, ast.Name) and target.id == "transition" for target in node.targets))
    replacement = "    transition = build_transition()\n"
    if not any(isinstance(node, ast.ImportFrom) and node.module == "tortoise_transition_animation" for node in function.body):
        replacement = "    from tortoise_transition_animation import build_transition\n" + replacement
    lines[assignment.lineno - 1:assignment.end_lineno] = [replacement]
    source = "".join(lines).replace("# 转阶段 3.4s 四拍：①停步低头、四肢外撑 ②下沉+震动、裂隙渐亮\n    #                  ③壳侧岩甲与口沿向外掀开 ④口部短促喷发、抬头咆哮",
        "# 160 tick 五拍转换，由独立动画更新器维护；不要重生成手改模型。")
    for path, data in [(GEO, geo), (ANIM, animation), *zip(BB, docs)]:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1 if path.suffix == ".bbmodel" else 2)
                        + "\n", encoding="utf-8")
    GEN.write_text(source, encoding="utf-8")
    print("Updated 8-second transition; all original cubes, UVs, textures and other clips preserved.")


if __name__ == "__main__":
    main()
