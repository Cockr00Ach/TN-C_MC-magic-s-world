"""Generate original 16 px crop sprites and their Minecraft data resources.

No source pixels are copied from Minecraft or another mod. Re-run after changing
the palettes below; the resulting files are ordinary, editable pack assets.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "tnc"
DATA = ROOT / "data" / "tnc"


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def pixel(draw: ImageDraw.ImageDraw, x: int, y: int, color: str) -> None:
    if 0 <= x < 16 and 0 <= y < 16:
        draw.point((x, y), fill=color)


def road_bell(age: int) -> Image.Image:
    im, d = canvas()
    height = min(12, 2 + age * 2)
    top = 15 - height
    d.rectangle((7, top, 8, 15), fill="#315b40")
    d.line((8, top, 8, 14), fill="#5e9958")
    for i in range(max(1, age // 2)):
        y = 13 - i * 3
        d.line((7, y, 4 - i % 2, y - 2), fill="#559c67", width=1)
        d.line((9, y - 1, 12 + i % 2, y - 3), fill="#77bd78", width=1)
        pixel(d, 4 - i % 2, y - 2, "#a0ca78")
    if age >= 4:
        d.rectangle((6, top, 9, top + 2), fill="#d6ad62")
        d.line((6, top, 8, top + 2), fill="#fff1ab")
    if age >= 6:
        d.rectangle((5, top + 2, 10, top + 4), fill="#aa693f")
        d.line((5, top + 2, 10, top + 2), fill="#ffdb79")
        for x in (5, 7, 9):
            pixel(d, x, top + 4, "#fce4a2")
    if age == 7:
        for x, y in ((3, 5), (12, 3), (11, 8)):
            pixel(d, x, y, "#ffe79a")
    return im


def night_gourd(age: int) -> Image.Image:
    im, d = canvas()
    stem_top = 14 - min(10, age * 2)
    d.line((5, 15, 6, stem_top, 11, stem_top + 2), fill="#38405d", width=1)
    d.line((6, 14, 8, stem_top + 2), fill="#627394", width=1)
    if age >= 2:
        d.polygon([(6, 12), (2, 9), (5, 9)], fill="#4d6974")
        d.polygon([(8, 10), (11, 7), (12, 10)], fill="#68848a")
    if age >= 4:
        size = 1 if age == 4 else 2 if age == 5 else 3
        d.ellipse((10 - size, 11 - size, 10 + size, 11 + size), fill="#df965a", outline="#74466a")
        pixel(d, 9, 10, "#ffe1a0")
    if age >= 6:
        d.line((9, 7, 10, 5), fill="#bb9a77")
        d.arc((7, 5, 14, 12), 245, 345, fill="#ffe2a0", width=1)
    if age == 7:
        for x, y in ((3, 5), (14, 6), (13, 14)):
            pixel(d, x, y, "#ffdf95")
    return im


def tide_reed(age: int) -> Image.Image:
    im, d = canvas()
    top = 13 - age * 3
    for base_x, delta, color in ((5, -1, "#497b83"), (8, 1, "#6aa5a0"), (11, 0, "#386579")):
        d.line((base_x, 15, base_x + delta, top), fill=color, width=1)
        if age > 0:
            d.line((base_x, 12, base_x - 2, 10), fill="#84b9a3", width=1)
        if age > 1:
            d.line((base_x, 9, base_x + 2, 7), fill="#93c7ae", width=1)
        if age == 3:
            d.rectangle((base_x + delta - 1, top - 3, base_x + delta, top), fill="#d6ac70")
            pixel(d, base_x + delta, top - 3, "#fae6ae")
    if age == 3:
        for x, y in ((2, 10), (14, 11)):
            pixel(d, x, y, "#8fdacb")
    return im


def item_sprite(kind: str) -> Image.Image:
    im, d = canvas()
    if kind.endswith("_wild_sample"):
        d.polygon([(2, 1), (12, 1), (14, 3), (14, 15), (2, 15)], fill="#e7d5aa", outline="#745b65")
        d.polygon([(12, 1), (12, 3), (14, 3)], fill="#b69883")
        d.line((4, 12, 11, 12), fill="#a68c73")
        if kind == "road_bell_wild_sample":
            d.line((8, 10, 8, 4), fill="#558c59")
            d.ellipse((6, 3, 10, 6), fill="#eac479", outline="#936d4e")
            d.line((7, 8, 5, 6), fill="#68a96c")
        elif kind == "night_gourd_wild_sample":
            d.ellipse((5, 5, 11, 10), fill="#e4a66d", outline="#745075")
            d.line((8, 4, 9, 3), fill="#62917a")
            pixel(d, 7, 6, "#ffe5a4")
        else:
            for x, color in ((6, "#4e858a"), (8, "#75a9a5"), (10, "#468093")):
                d.line((x, 10, x - 1, 4), fill=color)
            d.line((4, 10, 11, 10), fill="#d3ac75")
    elif kind == "road_bell_seed":
        for x, y in ((5, 10), (9, 7), (10, 11)):
            d.ellipse((x - 1, y - 2, x + 1, y + 1), fill="#bc7647", outline="#f6cf83")
        d.line((6, 4, 7, 6), fill="#6eaf70")
    elif kind == "road_bell_ear":
        d.line((7, 15, 8, 3), fill="#5c985e", width=2)
        d.polygon([(6, 11), (2, 8), (4, 7), (7, 10)], fill="#6dac75")
        d.polygon([(9, 10), (13, 7), (14, 8), (9, 12)], fill="#8cbe78")
        d.ellipse((5, 1, 10, 7), fill="#dba558", outline="#fff0a3")
        d.line((7, 2, 7, 6), fill="#fff4bc")
    elif kind == "road_bell_travel_bread":
        d.ellipse((1, 4, 14, 13), fill="#8e4c35", outline="#4d3541", width=1)
        d.ellipse((2, 3, 13, 12), fill="#d29a56", outline="#f6d589", width=1)
        d.arc((4, 4, 11, 10), 195, 345, fill="#fff0b0", width=2)
        d.line((5, 10, 9, 6, 12, 8), fill="#a05e39", width=1)
        for x, y in ((4, 7), (7, 5), (11, 10)):
            pixel(d, x, y, "#ffeab4")
    elif kind == "night_gourd_seed":
        for x, y in ((5, 10), (9, 7), (10, 11)):
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill="#6e587e", outline="#e5b87f")
    elif kind == "night_gourd":
        d.ellipse((2, 4, 13, 14), fill="#d88b58", outline="#5a4776", width=2)
        d.arc((3, 5, 11, 12), 255, 105, fill="#ffdaa0", width=2)
        d.rectangle((7, 1, 9, 5), fill="#548777")
        for x, y in ((3, 2), (13, 3), (14, 11)):
            pixel(d, x, y, "#ffe3a2")
    elif kind == "tide_reed_seed":
        for x, y in ((5, 10), (9, 6), (11, 11)):
            d.ellipse((x - 1, y - 2, x + 1, y), fill="#8b775d", outline="#d0b68a")
            pixel(d, x + 1, y - 2, "#8dd6c3")
    elif kind == "tide_reed_stem":
        for x, upper, color in ((5, 4, "#528992"), (8, 2, "#76b2a9"), (11, 5, "#3e7887")):
            d.line((x, 15, x - 1, upper), fill=color, width=2)
            d.rectangle((x - 2, upper - 2, x, upper), fill="#cda672")
        d.line((4, 12, 11, 14), fill="#b7a574")
    return im


def crop_loot(crop_id: str, seed_id: str, produce_id: str, mature: int, amount: int) -> dict:
    mature_condition = {"condition": "minecraft:block_state_property", "block": f"tnc:{crop_id}",
                        "properties": {"age": str(mature)}}
    return {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{seed_id}"}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{seed_id}",
                                      "conditions": [mature_condition]}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{produce_id}",
                                      "conditions": [mature_condition],
                                      "functions": [{"function": "minecraft:set_count", "count": amount}]}]}
        ]
    }


def main() -> None:
    variants = {
        "road_bell_crop": (8, road_bell),
        "night_gourd_crop": (8, night_gourd),
        "tide_reed_crop": (4, tide_reed),
    }
    for crop_id, (count, paint) in variants.items():
        blockstates = {}
        for age in range(count):
            name = f"{crop_id}_{age}"
            path = ASSETS / "textures" / "block" / f"{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            paint(age).save(path)
            write_json(ASSETS / "models" / "block" / f"{name}.json",
                       {"parent": "minecraft:block/crop", "textures": {"crop": f"tnc:block/{name}"},
                        "render_type": "minecraft:cutout"})
            blockstates[f"age={age}"] = {"model": f"tnc:block/{name}"}
        write_json(ASSETS / "blockstates" / f"{crop_id}.json", {"variants": blockstates})
    wild_plants = (
        ("wild_road_bell", "road_bell_crop_7", "road_bell_seed", "road_bell_ear"),
        ("wild_night_gourd", "night_gourd_crop_7", "night_gourd_seed", "night_gourd"),
        ("wild_tide_reed", "tide_reed_crop_3", "tide_reed_seed", "tide_reed_stem"),
    )
    for wild_id, mature_model, seed_id, produce_id in wild_plants:
        sample_id = wild_id.removeprefix("wild_") + "_wild_sample"
        write_json(ASSETS / "blockstates" / f"{wild_id}.json",
                   {"variants": {"": {"model": f"tnc:block/{mature_model}"}}})
        write_json(DATA / "loot_tables" / "blocks" / f"{wild_id}.json",
                   {"type": "minecraft:block", "pools": [
                       {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{seed_id}"}]},
                       {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{produce_id}"}]},
                       {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{sample_id}"}]}
                   ]})
    for item in ("road_bell_seed", "road_bell_ear", "road_bell_travel_bread", "night_gourd_seed", "night_gourd",
                 "tide_reed_seed", "tide_reed_stem", "road_bell_wild_sample",
                 "night_gourd_wild_sample", "tide_reed_wild_sample"):
        path = ASSETS / "textures" / "item" / f"{item}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        item_sprite(item).save(path)
        write_json(ASSETS / "models" / "item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"tnc:item/{item}"}})
    write_json(DATA / "loot_tables" / "blocks" / "road_bell_crop.json",
               crop_loot("road_bell_crop", "road_bell_seed", "road_bell_ear", 7, 2))
    write_json(DATA / "loot_tables" / "blocks" / "night_gourd_crop.json",
               crop_loot("night_gourd_crop", "night_gourd_seed", "night_gourd", 7, 2))
    write_json(DATA / "loot_tables" / "blocks" / "tide_reed_crop.json",
               crop_loot("tide_reed_crop", "tide_reed_seed", "tide_reed_stem", 3, 2))
    write_json(DATA / "recipes" / "road_bell_bread.json",
               {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "tnc:road_bell_ear"}] * 3,
                "result": {"item": "tnc:road_bell_travel_bread", "count": 1}})
    write_json(DATA / "recipes" / "tide_reed_paper.json",
               {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "tnc:tide_reed_stem"}] * 3,
                "result": {"item": "minecraft:paper", "count": 3}})


if __name__ == "__main__":
    main()
