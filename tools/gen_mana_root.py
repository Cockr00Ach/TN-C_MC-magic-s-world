"""Generate original 16 px mana-root growth states and their resource data."""

from pathlib import Path
import json

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
A = ROOT / "assets/tnc"
D = ROOT / "data/tnc"


def write(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sprite(age: int, fed: int) -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(image)
    top = 13 - age * 3
    d.line((8, 15, 8, top), fill="#715c59", width=2)
    d.line((7, 14, 4, 15), fill="#4c6164")
    d.line((9, 14, 12, 15), fill="#4c6164")
    if age > 0:
        d.line((8, top + 3, 4, top + 1), fill="#718c75", width=1)
        d.line((8, top + 4, 12, top + 2), fill="#8da385", width=1)
        d.polygon([(4, top + 1), (2, top - 1), (6, top)], fill="#718d7a")
        d.polygon([(12, top + 2), (14, top), (10, top + 1)], fill="#719e96")
    if age > 1:
        d.ellipse((6, top, 10, top + 4), fill="#5a6d88", outline="#c4a26d")
    if age == 3:
        d.line((8, top, 8, top - 2), fill="#b6b195")
        d.point((8, top - 2), fill="#f7dfad")
    for i in range(fed):
        y = 13 - i * 2
        d.point((7 if i % 2 else 9, y), fill="#adcbec")
        if age >= 2:
            d.point((5 + (i * 3) % 8, max(0, top + i)), fill="#b9b3e9")
    return image


variants = {}
for age in range(4):
    for fed in range(5):
        name = f"mana_root_{age}_{fed}"
        sprite(age, fed).save(A / f"textures/block/{name}.png")
        write(A / f"models/block/{name}.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"tnc:block/{name}"}})
        variants[f"age={age},fed={fed}"] = {"model": f"tnc:block/{name}"}
write(A / "blockstates/mana_root.json", {"variants": variants})

for name in ("mana_root_seed", "mana_root_core"):
    image = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(image)
    if name.endswith("seed"):
        d.ellipse((4, 4, 11, 13), fill="#735c56", outline="#cbb281")
        d.line((8, 5, 8, 1), fill="#7da78b")
        d.polygon([(8, 3), (3, 1), (7, 6)], fill="#8fbca0")
        d.point((7, 10), fill="#a2c3dd")
    else:
        d.polygon([(7, 1), (12, 3), (14, 8), (11, 14), (5, 14), (2, 8), (4, 3)],
                  fill="#516e8f", outline="#c8a672")
        d.polygon([(8, 3), (11, 8), (8, 12), (5, 8)], fill="#a5c8ea", outline="#ebe1a8")
        d.point((8, 6), fill="#ffffff")
    image.save(A / f"textures/item/{name}.png")
    write(A / f"models/item/{name}.json", {"parent": "minecraft:item/generated",
        "textures": {"layer0": f"tnc:item/{name}"}})

write(D / "loot_tables/blocks/mana_root.json", {"type": "minecraft:block", "pools": [
    {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tnc:mana_root_seed"}]},
    {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tnc:mana_root_core",
        "conditions": [{"condition": "minecraft:block_state_property", "block": "tnc:mana_root",
                        "properties": {"age": "3", "fed": "4"}}]}]}
]})
write(D / "recipes/mana_root_seed.json", {"type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "minecraft:beetroot_seeds"}, {"item": "minecraft:amethyst_shard"},
                    {"item": "minecraft:copper_ingot"}],
    "result": {"item": "tnc:mana_root_seed", "count": 2}})
