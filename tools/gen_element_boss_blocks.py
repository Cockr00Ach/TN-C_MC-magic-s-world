"""Generate block metadata; artwork is generated with the built-in imagegen tool."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
THEMES = {
    "earth": ("岩枪·地核纹石", "Earth Core Runestone", "brown_dye"),
    "water": ("水渊·蓝金鳞纹石", "Abyssal Scale Runestone", "blue_dye"),
    "dark": ("暗蚀·虚空纹石", "Void Eclipse Runestone", "black_dye"),
    "thunder_violet": ("衍雷·紫霆纹石", "Violet Thunder Runestone", "purple_dye"),
    "thunder_gold": ("衍雷·金霆纹石", "Golden Thunder Runestone", "yellow_dye"),
    "light": ("圣光·日轮纹石", "Radiant Halo Runestone", "white_dye"),
}


def write(relative, value):
    path = RES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def generate():
    langs = {lang: json.loads((RES / f"assets/tnc/lang/{lang}.json").read_text(encoding="utf-8")) for lang in ("zh_cn", "en_us")}
    names = []
    for theme, (zh, en, dye) in THEMES.items():
        for shade in range(1, 9):
            name = f"{theme}_boss_runestone_{shade}"
            item = "tnc:" + name
            names.append(item)
            langs["zh_cn"]["block.tnc." + name] = f"{zh}·{shade}阶"
            langs["en_us"]["block.tnc." + name] = f"{en} {shade}"
            write(f"assets/tnc/blockstates/{name}.json", {"variants": {"": {"model": f"tnc:block/{name}"}}})
            write(f"assets/tnc/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"tnc:block/{name}"}})
            write(f"assets/tnc/models/item/{name}.json", {"parent": f"tnc:block/{name}"})
            write(f"data/tnc/loot_tables/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
            # One affordable base recipe per family; stonecutting selects every shade.
            if shade == 1:
                write(f"data/tnc/recipes/{name}.json", {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:stone"}] * 4 + [{"item": "minecraft:" + dye}], "result": {"item": item, "count": 4}})
            write(f"data/tnc/recipes/{name}_stonecutting.json", {"type": "minecraft:stonecutting", "ingredient": {"tag": f"tnc:boss_runestones/{theme}"}, "result": item, "count": 1})
        write(f"data/tnc/tags/blocks/boss_runestones/{theme}.json", {"replace": False, "values": names[-8:]})
        write(f"data/tnc/tags/items/boss_runestones/{theme}.json", {"replace": False, "values": names[-8:]})
    write("data/tnc/tags/blocks/boss_runestones.json", {"replace": False, "values": names})
    for tag in ("mineable/pickaxe", "needs_stone_tool"):
        relative = f"data/minecraft/tags/blocks/{tag}.json"
        path = RES / relative
        value = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        if "#tnc:boss_runestones" not in value["values"]:
            value["values"].append("#tnc:boss_runestones")
        write(relative, value)
    for lang, values in langs.items():
        write(f"assets/tnc/lang/{lang}.json", values)


if __name__ == "__main__":
    generate()
