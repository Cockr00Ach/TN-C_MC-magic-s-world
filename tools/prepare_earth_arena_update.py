"""Install the new earth realm without replacing unrelated live mod content."""
import argparse
import zipfile
from pathlib import Path
from prepare_water_leviathan_update import main


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
    }
    main(base, built, output, report, entries=entries, translation_keys=(
        "item.tnc.earth_arena_keystone", "dimension.tnc.earth_arena", "biome.tnc.earth_arena"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for option in ("base", "built", "output", "report"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base, args.built, args.output, args.report)
