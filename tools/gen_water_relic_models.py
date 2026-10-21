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

# Broad two-handed executioner sword: long two-hand grip, heavy quillons, ricasso and fuller.
# The authored length is normalized for vanilla baking and restored by the renderer.
sword = [
    # Broad double-edged blade and a raised central fuller.
    box([6.2, 5, 7.0], [9.8, 13, 9.0], "blue", False),
    box([4.0, 12, 6.55], [12.0, 43.5, 9.45], "blue", False),
    box([7.0, 6, 6.15], [9.0, 44.5, 9.85], "light", False),
    box([3.35, 17, 6.7], [4.35, 42.5, 9.3], "gold", False),
    box([11.65, 17, 6.7], [12.65, 42.5, 9.3], "gold", False),
    # Ricasso and oversized crossguard make the silhouette read as a greatsword.
    box([3.6, 41.5, 6.1], [12.4, 46.0, 9.9], "gold", False),
    box([-7.5, 44.5, 6.65], [23.5, 48.5, 9.35], "gold", False),
    box([-9.0, 43.5, 7.0], [-6.2, 50.0, 9.0], "light", False),
    box([22.2, 43.5, 7.0], [25.0, 50.0, 9.0], "light", False),
    box([-4.0, 47.0, 7.0], [2.2, 51.5, 9.0], "blue", False,
        {"origin": [0, 47.5, 8], "axis": "z", "angle": -22.5, "rescale": True}),
    box([13.8, 47.0, 7.0], [20.0, 51.5, 9.0], "blue", False,
        {"origin": [16, 47.5, 8], "axis": "z", "angle": 22.5, "rescale": True}),
    # A true two-hand grip, nearly half a blade-width longer than the old hilt.
    box([6.35, 48, 6.8], [9.65, 70.0, 9.2], "deep", True),
    box([5.9, 50.0, 6.35], [10.1, 52.0, 9.65], "gold", False),
    box([5.9, 56.0, 6.35], [10.1, 58.0, 9.65], "gold", False),
    box([5.9, 62.0, 6.35], [10.1, 64.0, 9.65], "gold", False),
    box([5.9, 68.0, 6.35], [10.1, 70.0, 9.65], "gold", False),
    box([5.0, 69.5, 6.2], [11.0, 74.0, 9.8], "gold", False),
    box([6.25, 73.0, 6.8], [9.75, 78.0, 9.2], "light", False),
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
