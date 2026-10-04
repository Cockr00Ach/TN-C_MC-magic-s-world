"""Create original 16 px sprites and data resources for hushcap and rainletter.

These images are drawn from geometric pixels here, not sampled from another pack.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/tnc"
DATA = ROOT / "data/tnc"


def write(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def picture() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (16, 16))
    return image, ImageDraw.Draw(image)


def hushcap(age: int, opened: bool) -> Image.Image:
    image, d = picture()
    height = 3 + age * 2
    stem_top = 14 - height
    d.rectangle((7, stem_top + 2, 8, 15), fill="#7f90a4")
    d.line((8, stem_top + 3, 8, 14), fill="#d4ddc9")
    if opened:
        span = 2 + age
        d.polygon([(8, stem_top), (8 - span, stem_top + 3), (8 + span, stem_top + 3)],
                  fill="#394e78", outline="#82acbe")
        d.line((8 - span + 1, stem_top + 3, 8 + span - 1, stem_top + 3), fill="#f3c678")
        d.point((5, 13 - age), fill="#b6d5d1")
        d.point((11, 14 - age), fill="#f5d88a")
    else:
        d.ellipse((6 - age // 2, stem_top, 10 + age // 2, stem_top + 5),
                  fill="#2d3d64", outline="#688ca8")
        d.point((8, stem_top + 1), fill="#b1bec3")
    return image


def rainletter(age: int, wet: bool) -> Image.Image:
    image, d = picture()
    top = 13 - age * 3
    d.line((7, 15, 7, top), fill="#355a57", width=2)
    for side, y in [(-1, 13), (1, 11), (-1, 9), (1, 7)]:
        if y < top + 3:
            continue
        x1, x2 = 7, 7 + side * (2 + age // 2)
        d.line((x1, y, x2, y - 2), fill="#72a887")
        d.polygon([(x2, y - 2), (x2 + side * 2, y - 3), (x2 + side, y)],
                  fill="#529d92" if wet else "#607f73")
    if age >= 2:
        for x, y in [(4, 10), (11, 9), (6, 5)]:
            if y >= top:
                d.ellipse((x - 1, y - 1, x + 1, y + 1), fill="#6b6fb0", outline="#b4bfd9")
    if age == 3:
        for x, y in [(3, 7), (10, 6), (12, 11)]:
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill="#5d71c6", outline="#d7dded")
    if wet:
        d.point((11, 2), fill="#a7e2f5")
        d.point((3, 12), fill="#c7e9ef")
    return image


def item_icon(name: str) -> Image.Image:
    image, d = picture()
    if name == "hushcap_spore":
        d.ellipse((4, 5, 11, 12), fill="#4c668b", outline="#a6b8bb")
        for x, y in [(5, 4), (9, 2), (12, 7), (3, 10), (8, 13)]:
            d.point((x, y), fill="#ead6a2")
    elif name == "hushcap_slice":
        d.polygon([(2, 10), (7, 3), (13, 7), (11, 13), (5, 14)], fill="#536c91", outline="#d6b875")
        d.line((4, 10, 10, 12), fill="#f1d392")
        d.line((7, 7, 11, 8), fill="#a8c9d0")
    elif name == "hushcap_broth":
        d.polygon([(2, 6), (14, 6), (12, 13), (4, 13)], fill="#6b493a", outline="#3d3b4b")
        d.ellipse((2, 4, 14, 9), fill="#758d9b", outline="#efce8a")
        d.ellipse((5, 5, 8, 8), fill="#d1c69d")
        d.point((11, 6), fill="#f4dc9a")
    elif name == "rainletter_seed":
        for x, y in [(4, 10), (8, 6), (11, 11)]:
            d.ellipse((x - 1, y - 2, x + 1, y + 1), fill="#526faa", outline="#bad0d3")
        d.line((8, 3, 9, 1), fill="#71aa8c")
    elif name == "rainletter_berry":
        d.line((8, 4, 9, 1), fill="#5d967a")
        d.polygon([(7, 3), (3, 2), (6, 6)], fill="#78b599")
        d.ellipse((4, 5, 12, 14), fill="#526fbd", outline="#c4d1e4")
        d.point((6, 7), fill="#e3e8eb")
        d.point((10, 11), fill="#879fdb")
    elif name == "rainletter_cordial":
        d.polygon([(6, 2), (10, 2), (10, 5), (12, 7), (12, 14), (4, 14), (4, 7), (6, 5)],
                  fill="#9db1bd", outline="#536678")
        d.rectangle((5, 8, 11, 13), fill="#627cb7")
        d.rectangle((7, 3, 9, 5), fill="#6e503f")
        d.point((7, 9), fill="#c3d7df")
    return image


for age in range(4):
    for open_ in (False, True):
        name = f"hushcap_{age}_{'open' if open_ else 'closed'}"
        hushcap(age, open_).save(ASSETS / f"textures/block/{name}.png")
        write(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"tnc:block/{name}"}})
    for wet in (False, True):
        name = f"rainletter_{age}_{'wet' if wet else 'dry'}"
        rainletter(age, wet).save(ASSETS / f"textures/block/{name}.png")
        write(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"tnc:block/{name}"}})

write(ASSETS / "blockstates/hushcap_mushroom.json", {"variants": {
    f"age={age},quiet={quiet}": {"model": f"tnc:block/hushcap_{age}_{'open' if quiet == 3 else 'closed'}"}
    for age in range(4) for quiet in range(4)}})
write(ASSETS / "blockstates/rainletter_bush.json", {"variants": {
    f"age={age},wet={str(wet).lower()}": {"model": f"tnc:block/rainletter_{age}_{'wet' if wet else 'dry'}"}
    for age in range(4) for wet in (False, True)}})

for name in ("hushcap_spore", "hushcap_slice", "hushcap_broth", "rainletter_seed",
             "rainletter_berry", "rainletter_cordial"):
    item_icon(name).save(ASSETS / f"textures/item/{name}.png")
    write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated",
        "textures": {"layer0": f"tnc:item/{name}"}})

for block, seed, crop in (
    ("hushcap_mushroom", "hushcap_spore", "hushcap_slice"),
    ("rainletter_bush", "rainletter_seed", "rainletter_berry"),
):
    write(DATA / f"loot_tables/blocks/{block}.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{seed}"}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{crop}",
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"tnc:{block}",
                            "properties": {"age": "3"}}],
            "functions": [{"function": "minecraft:set_count", "count": 2}]}]}
    ]})

write(DATA / "recipes/hushcap_spore.json", {"type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "minecraft:brown_mushroom"}, {"item": "minecraft:moss_block"},
                    {"item": "minecraft:amethyst_shard"}],
    "result": {"item": "tnc:hushcap_spore", "count": 2}})
write(DATA / "recipes/hushcap_broth.json", {"type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "minecraft:bowl"}, {"item": "tnc:hushcap_slice"},
                    {"item": "tnc:hushcap_slice"}, {"item": "minecraft:carrot"}],
    "result": {"item": "tnc:hushcap_broth"}})
write(DATA / "recipes/rainletter_seed.json", {"type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "minecraft:sweet_berries"}, {"item": "minecraft:amethyst_shard"},
                    {"item": "minecraft:paper"}],
    "result": {"item": "tnc:rainletter_seed", "count": 2}})
write(DATA / "recipes/rainletter_cordial.json", {"type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "tnc:rainletter_berry"},
                    {"item": "tnc:rainletter_berry"}, {"item": "minecraft:sugar"}],
    "result": {"item": "tnc:rainletter_cordial"}})
