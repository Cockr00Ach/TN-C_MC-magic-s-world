"""Replace only death in existing resources and Blockbench projects. Never rebuild handmade geometry."""
import argparse
import ast
import copy
import json
import shutil
from pathlib import Path
from embed_animations import collect_bones, stable_uuid
from update_tortoise_transition import resolve
from tortoise_death_animation import build_death, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-dir", type=Path, required=True)
    args = parser.parse_args()
    paths = [ROOT / "src/main/resources/assets/tnc/animations/entity/volcanic_tortoise.animation.json",
             ROOT / "tools/gen_volcanic_tortoise_art.py",
             *[ROOT / f"art/volcanic_tortoise/volcanic_tortoise_phase{p}.bbmodel" for p in (1, 2)]]
    for path in paths:
        backup = args.backup_dir / path.relative_to(ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists(): shutil.copyfile(path, backup)
    clip = build_death()
    data = json.loads(paths[0].read_text(encoding="utf-8"))
    before = copy.deepcopy(data)
    data["animations"]["death"] = clip
    assert all(value == data["animations"][name] for name, value in before["animations"].items() if name != "death")
    paths[0].write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for phase, path in enumerate(paths[2:], 1):
        doc = json.loads(path.read_text(encoding="utf-8"))
        original = copy.deepcopy(doc)
        groups, animators = collect_bones(doc), {}
        for bone, channels in clip["bones"].items():
            if bone not in groups: continue
            keys = []
            for channel, points in channels.items():
                for time, vector in points.items():
                    keys.append({"channel": channel, "time": float(time), "interpolation": "linear",
                                 "data_points": [dict(zip(("x", "y", "z"), [resolve(v, phase) for v in vector]))],
                                 "uuid": stable_uuid(f"death|{bone}|{channel}|{time}"), "color": -1})
            animators[groups[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
        death = next(a for a in doc["animations"] if a["name"] == "death")
        death.update({"length": clip["animation_length"], "loop": "once", "animators": animators})
        assert doc["elements"] == original["elements"] and doc["textures"] == original["textures"]
        assert [a for a in doc["animations"] if a["name"] != "death"] == [a for a in original["animations"] if a["name"] != "death"]
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    p = paths[1]
    source = p.read_text(encoding="utf-8")
    function = next(n for n in ast.parse(source).body if isinstance(n, ast.FunctionDef) and n.name == "build_animations")
    assignment = next(n for n in function.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "death" for t in n.targets))
    lines = source.splitlines(keepends=True)
    replacement = "    death = build_death()\n"
    if not any(isinstance(n, ast.ImportFrom) and n.module == "tortoise_death_animation" for n in function.body):
        replacement = "    from tortoise_death_animation import build_death\n" + replacement
    lines[assignment.lineno - 1:assignment.end_lineno] = [replacement]
    p.write_text("".join(lines), encoding="utf-8")
    print("Updated death only: 12.2 seconds; existing geometry, textures, transition and other clips preserved.")


if __name__ == "__main__":
    main()
