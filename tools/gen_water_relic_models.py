"""Generate the baked blue-gold sea-god sword and detailed prison panel."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/tnc/models/water"

TEXTURES = {
    "blue": "tnc:block/water_relic_blue_gold",
    "gold": "minecraft:block/gold_block",
    "light": "minecraft:block/sea_lantern",
    "deep": "minecraft:block/blue_concrete",
    "particle": "tnc:block/water_relic_blue_gold",
}

def box(a, b, texture, shade=True, rotation=None):
    faces = {face: {"texture": f"#{texture}", "uv": [0, 0, 16, 16]}
             for face in ("north", "south", "east", "west", "up", "down")}
    value = {"from": a, "to": b, "shade": shade, "faces": faces}
    if rotation:
        value["rotation"] = rotation
    return value

def write(name, elements):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"credit": "TN-C blue-gold water relic models", "ambientocclusion": False,
               "textures": TEXTURES, "elements": elements}
    (OUT / f"{name}.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

# Tip at y=0 and pommel at y=64. Rendered at x16 scale, so the final sword is 64 blocks tall.
sword = [
    box([6.65, 5, 7.2], [9.35, 15, 8.8], "blue", False),
    box([5.4, 13, 6.9], [10.6, 42.5, 9.1], "blue", False),
    box([7.2, 7, 6.55], [8.8, 43.5, 9.45], "light", False),
    box([4.85, 17, 7.0], [5.65, 42, 9.0], "gold", False),
    box([10.35, 17, 7.0], [11.15, 42, 9.0], "gold", False),
    box([5.1, 41.5, 6.55], [10.9, 44.5, 9.45], "gold", False),
    box([-4.5, 44, 7.0], [20.5, 47.2, 9.0], "gold", False),
    box([-6, 43.1, 7.25], [-3.5, 48.2, 8.75], "light", False),
    box([19.5, 43.1, 7.25], [22, 48.2, 8.75], "light", False),
    box([-2.5, 46.6, 7.35], [2.5, 50.1, 8.65], "blue", False,
        {"origin": [0, 47.5, 8], "axis": "z", "angle": -22.5, "rescale": True}),
    box([13.5, 46.6, 7.35], [18.5, 50.1, 8.65], "blue", False,
        {"origin": [16, 47.5, 8], "axis": "z", "angle": 22.5, "rescale": True}),
    box([6.65, 47, 6.9], [9.35, 59.5, 9.1], "deep", True),
    box([6.2, 48.5, 6.45], [9.8, 50, 9.55], "gold", False),
    box([6.2, 52.5, 6.45], [9.8, 54, 9.55], "gold", False),
    box([6.2, 56.5, 6.45], [9.8, 58, 9.55], "gold", False),
    box([5.5, 59, 6.5], [10.5, 63, 9.5], "gold", False),
    box([6.5, 62, 7], [9.5, 66, 9], "light", False),
]
# Vanilla BlockElement coordinates must stay within -16..32. Keep the authored
# silhouette, normalize it for baking, and restore the giant size in the renderer.
for element in sword:
    for key in ("from", "to"):
        x, y, z = element[key]
        element[key] = [8 + (x - 8) / 3, y / 3, 8 + (z - 8) / 3]
    if "rotation" in element:
        x, y, z = element["rotation"]["origin"]
        element["rotation"]["origin"] = [8 + (x - 8) / 3, y / 3, 8 + (z - 8) / 3]
write("sea_god_sword", sword)

# One one-block-high panel, stretched by the renderer to the cage height.
panel = [
    box([2.2, 0, 7.45], [13.8, 16, 8.55], "blue", False),
    box([1.65, 0, 7.0], [2.8, 16, 9.0], "gold", False),
    box([13.2, 0, 7.0], [14.35, 16, 9.0], "gold", False),
    box([2.2, 0, 7.0], [13.8, 1.2, 9.0], "gold", False),
    box([2.2, 14.8, 7.0], [13.8, 16, 9.0], "gold", False),
    box([7.15, 3.0, 6.7], [8.85, 13.0, 9.3], "light", False),
    box([4.7, 6.7, 6.65], [11.3, 9.3, 9.35], "light", False),
    box([6.1, 6.1, 6.25], [9.9, 9.9, 9.75], "gold", False,
        {"origin": [8, 8, 8], "axis": "z", "angle": 45, "rescale": True}),
]
write("water_prison_panel", panel)

print(OUT / "sea_god_sword.json")
print(OUT / "water_prison_panel.json")
