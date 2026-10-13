"""Overlay only the dark giant redesign onto the currently installed TN-C jar."""
import argparse
import copy
import hashlib
import json
import zipfile
from pathlib import Path


ENTRIES = {
    "com/tnc/tnc/boss/TNDarkGiantEntity.class",
    "com/tnc/tnc/boss/client/TNDarkGiantRenderer.class",
    "com/tnc/tnc/boss/client/TNDarkGiantEmissiveLayer.class",
    "assets/tnc/geo/entity/dark_giant.geo.json",
    "assets/tnc/textures/entity/dark_giant_bedrock.png",
    "assets/tnc/textures/entity/dark_giant_emissive.png",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(base: Path, built: Path, output: Path, report: Path) -> None:
    if output.resolve() in (base.resolve(), built.resolve()) or output.exists():
        raise ValueError("output must be a new, separate jar")
    before_hash = digest(base)
    changed = []
    preserved = 0
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(built) as update:
        if len(original.namelist()) != len(set(original.namelist())):
            raise ValueError("duplicate base entries")
        if any(n.startswith("META-INF/") and n.upper().endswith((".SF", ".RSA", ".DSA"))
               for n in original.namelist()):
            raise ValueError("signed base jar requires another distribution process")
        files = {i.filename: i for i in update.infolist() if i.filename in ENTRIES}
        missing = ENTRIES - files.keys()
        if missing:
            raise ValueError(f"incomplete dark boss build: {sorted(missing)}")
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w") as result:
            for info in original.infolist():
                name = info.filename
                if name in files:
                    entry = files.pop(name)
                    data = update.read(name)
                    if data != original.read(name):
                        changed.append(name)
                    result.writestr(copy.copy(entry), data)
                else:
                    result.writestr(copy.copy(info), original.read(name))
                    preserved += 1
            for name, info in files.items():
                result.writestr(copy.copy(info), update.read(name))
                changed.append(name)
        with zipfile.ZipFile(output) as result:
            if result.testzip() is not None:
                raise ValueError("output CRC verification failed")
            for name in original.namelist():
                if name not in ENTRIES and result.read(name) != original.read(name):
                    raise ValueError(f"unrelated entry changed: {name}")
        if digest(base) != before_hash:
            raise ValueError("installed jar changed during preparation")
    evidence = {
        "base": str(base),
        "base_sha256": before_hash,
        "built": str(built),
        "built_sha256": digest(built),
        "output": str(output),
        "output_sha256": digest(output),
        "preserved_entries": preserved,
        "changed_dark_boss_entries": changed,
        "unrelated_entries_identical": True,
    }
    report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"preserved_entries": preserved, "changed_entries": len(changed),
                      "sha256": evidence["output_sha256"]}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--built", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base, args.built, args.output, args.report)
