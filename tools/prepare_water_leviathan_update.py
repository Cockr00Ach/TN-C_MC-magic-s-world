"""Overlay only the water Leviathan boss onto an installed TN-C jar."""
import argparse
import copy
import hashlib
import json
import zipfile
from pathlib import Path


ENTRIES = {
    "com/tnc/tnc/TNMod.class",
    "com/tnc/tnc/npc/TNNpcs.class",
    "com/tnc/tnc/npc/TNNpcAttributes.class",
    "com/tnc/tnc/npc/client/TNNpcClientEvents.class",
    "com/tnc/tnc/boss/TNWaterLeviathanEntity.class",
    "com/tnc/tnc/boss/client/TNWaterLeviathanGeoModel.class",
    "com/tnc/tnc/boss/client/TNWaterLeviathanRenderer.class",
    "com/tnc/tnc/boss/client/TNWaterLeviathanEmissiveLayer.class",
    "assets/tnc/geo/entity/water_leviathan.geo.json",
    "assets/tnc/animations/entity/water_leviathan.animation.json",
    "assets/tnc/textures/entity/water_leviathan_bedrock.png",
    "assets/tnc/textures/entity/water_leviathan_emissive.png",
    "assets/tnc/models/item/water_leviathan_spawn_egg.json",
    "assets/tnc/lang/zh_cn.json",
    "assets/tnc/lang/en_us.json",
}

MOVEMENT_ENTRIES = {
    "com/tnc/tnc/boss/TNWaterLeviathanEntity.class",
    "com/tnc/tnc/boss/TNWaterLeviathanEntity$LeviathanMoveGoal.class",
    "com/tnc/tnc/boss/LeviathanMovementRules.class",
    "com/tnc/tnc/boss/client/LeviathanFlightVisuals.class",
    "assets/tnc/animations/entity/water_leviathan.animation.json",
}
ENTRIES |= MOVEMENT_ENTRIES


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(base: Path, built: Path, output: Path, report: Path, entries=ENTRIES) -> None:
    if output.exists() or output.resolve() in (base.resolve(), built.resolve()):
        raise ValueError("output must be a new, separate jar")
    base_hash = digest(base)
    changed, preserved = [], 0
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(built) as new:
        available = {i.filename: i for i in new.infolist() if i.filename in entries}
        missing = entries - available.keys()
        if missing:
            raise ValueError(f"incomplete Leviathan build: {sorted(missing)}")
        with zipfile.ZipFile(output, "w") as result:
            for info in old.infolist():
                name = info.filename
                if name in available:
                    replacement = available.pop(name)
                    data = new.read(name)
                    if name in {"assets/tnc/lang/zh_cn.json", "assets/tnc/lang/en_us.json"}:
                        translations = json.loads(old.read(name))
                        updates = json.loads(data)
                        for key in ("entity.tnc.water_leviathan", "item.tnc.water_leviathan_spawn_egg"):
                            translations[key] = updates[key]
                        data = (json.dumps(translations, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
                    result.writestr(copy.copy(replacement), data)
                    changed.append(name)
                else:
                    result.writestr(copy.copy(info), old.read(name))
                    preserved += 1
            for name, info in available.items():
                result.writestr(copy.copy(info), new.read(name))
                changed.append(name)
        with zipfile.ZipFile(output) as result:
            if result.testzip() is not None:
                raise ValueError("candidate jar CRC failure")
            for name in old.namelist():
                if name not in entries and result.read(name) != old.read(name):
                    raise ValueError(f"unrelated entry changed: {name}")
    if digest(base) != base_hash:
        raise ValueError("installed jar changed while preparing update")
    evidence = {
        "base": str(base), "base_sha256": base_hash,
        "built": str(built), "built_sha256": digest(built),
        "output": str(output), "output_sha256": digest(output),
        "preserved_entries": preserved, "changed_entries": sorted(changed),
        "unrelated_entries_identical": True,
    }
    report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--base", type=Path, required=True)
    p.add_argument("--built", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--movement-only", action="store_true")
    a = p.parse_args()
    main(a.base, a.built, a.output, a.report, MOVEMENT_ENTRIES if a.movement_only else ENTRIES)
