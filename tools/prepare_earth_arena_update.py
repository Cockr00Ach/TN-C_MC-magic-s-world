"""Install the new earth realm without replacing unrelated live mod content."""
import argparse
import zipfile
import json
import tempfile
from pathlib import Path
from prepare_water_leviathan_update import main, digest


def prepare(base, built, output, report):
    with zipfile.ZipFile(built) as jar:
        entries = {name for name in jar.namelist()
                   if name.startswith("com/tnc/tnc/world/eartharena/") and name.endswith(".class")
                   and ("/client/" not in name or "/client/EarthArenaSky" in name) and "GameTests" not in name}
    entries |= {
        "data/tnc/dimension/earth_arena.json",
        "data/tnc/dimension_type/earth_arena.json",
        "data/tnc/worldgen/biome/earth_arena.json",
        "data/tnc/recipes/earth_arena_keystone.json",
        "assets/tnc/models/item/earth_arena_keystone.json",
        "assets/tnc/lang/zh_cn.json", "assets/tnc/lang/en_us.json",
        "assets/tnc/blockstates/earth_arena_pillar_stone.json",
        "assets/tnc/models/block/earth_arena_pillar_stone.json",
        "assets/tnc/models/item/earth_arena_pillar_stone.json",
        "assets/tnc/blockstates/earth_arena_black_pillar_stone.json",
        "assets/tnc/models/block/earth_arena_black_pillar_stone.json",
        "assets/tnc/models/item/earth_arena_black_pillar_stone.json",
        "assets/tnc/textures/block/earth_amber_stone.png",
        "com/tnc/tnc/boss/earth/StoneLancerEntity.class",
        "com/tnc/tnc/mixin/EarthArenaLevelMixin.class",
        "tnc.water-bore.mixins.json",
    }
    # The installed pack may carry extra fixes; append only our one new mixin.
    with zipfile.ZipFile(base) as live, zipfile.ZipFile(built) as compiled, tempfile.TemporaryDirectory(prefix="earth-arena-") as staging:
        adapted=Path(staging)/"arena-scoped.jar"
        with zipfile.ZipFile(adapted,"w",compression=zipfile.ZIP_DEFLATED) as jar:
            for name in entries:
                data=compiled.read(name)
                if name=="tnc.water-bore.mixins.json":
                    mixins=json.loads(live.read(name))
                    if "EarthArenaLevelMixin" not in mixins["mixins"]:mixins["mixins"].append("EarthArenaLevelMixin")
                    data=(json.dumps(mixins,indent=2)+"\n").encode()
                jar.writestr(name,data)
        main(base, adapted, output, report, entries=entries, translation_keys=(
            "item.tnc.earth_arena_keystone", "dimension.tnc.earth_arena", "biome.tnc.earth_arena", "block.tnc.earth_arena_pillar_stone", "block.tnc.earth_arena_black_pillar_stone"))
    evidence=json.loads(report.read_text(encoding="utf-8"))
    evidence.update(built=str(built),built_sha256=digest(built))
    report.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for option in ("base", "built", "output", "report"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base, args.built, args.output, args.report)
