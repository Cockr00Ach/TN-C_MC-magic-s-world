"""Overlay only the new elemental building content, preserving the live pack."""
import argparse
import json
import tempfile
import zipfile
from pathlib import Path
from prepare_water_leviathan_update import main

THEMES = ("earth", "water", "dark", "thunder_violet", "thunder_gold", "light")
LANGS = {"assets/tnc/lang/zh_cn.json", "assets/tnc/lang/en_us.json"}
MINING = {"data/minecraft/tags/blocks/mineable/pickaxe.json", "data/minecraft/tags/blocks/needs_stone_tool.json"}


def prepare(base, built, output, report):
    with zipfile.ZipFile(base) as installed, zipfile.ZipFile(built) as compiled:
        entries = {
            name for name in compiled.namelist()
            if ("_boss_runestone_" in name and name.startswith(("assets/tnc/", "data/tnc/")))
            or name.startswith(("data/tnc/tags/blocks/boss_runestones", "data/tnc/tags/items/boss_runestones"))
            or name == "com/tnc/tnc/building/ElementBossBlocks.class"
        } | LANGS | MINING
        # Merge vanilla tags as well as translations: other mods' current entries survive.
        with tempfile.TemporaryDirectory(prefix="element-block-update-") as staging:
            adapted = Path(staging) / "element-blocks.jar"
            with zipfile.ZipFile(adapted, "w", compression=zipfile.ZIP_DEFLATED) as jar:
                for name in entries:
                    data = compiled.read(name)
                    if name in MINING and name in installed.namelist():
                        tag = json.loads(installed.read(name))
                        if "#tnc:boss_runestones" not in tag["values"]:
                            tag["values"].append("#tnc:boss_runestones")
                        data = (json.dumps(tag, ensure_ascii=False, indent=2) + "\n").encode()
                    jar.writestr(name, data)
            keys = tuple(f"block.tnc.{theme}_boss_runestone_{shade}" for theme in THEMES for shade in range(1, 9))
            main(base, adapted, output, report, entries=entries, translation_keys=keys)
    evidence = json.loads(report.read_text(encoding="utf-8"))
    evidence["built"] = str(built)
    from prepare_water_leviathan_update import digest
    evidence["built_sha256"] = digest(built)
    evidence["scope"] = "48 elemental boss building blocks; existing translations and mining-tag members preserved"
    report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for option in ("base", "built", "output", "report"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base, args.built, args.output, args.report)
