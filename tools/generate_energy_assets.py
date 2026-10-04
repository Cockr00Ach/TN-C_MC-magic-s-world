"""Build exact 16px hard-pixel textures and small cuboid work-device models."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc"
TEX = ROOT / "textures/block"
MODEL = ROOT / "models/block"
ITEM = ROOT / "models/item"
STATES = ROOT / "blockstates"
for folder in (TEX, MODEL, ITEM, STATES):
    folder.mkdir(parents=True, exist_ok=True)


def save_texture(name: str, pixels: list[str], palette: dict[str, str]) -> None:
    assert len(pixels) == 16, name
    image = Image.new("RGBA", (16, 16))
    for y, line in enumerate(pixels):
        line = (line + line[-1] * 16)[:16]
        for x, letter in enumerate(line):
            image.putpixel((x, y), tuple(bytes.fromhex(palette[letter])))
    image.save(TEX / f"{name}.png")


COPPER = {"d": "403a3aff", "s": "62545aff", "c": "966c55ff", "h": "b5815fff", "l": "d0a071ff", "t": "538c8bff", "u": "75c1b0ff"}
STONE = {"d": "343a43ff", "s": "4a535dff", "m": "667078ff", "l": "818b8eff", "t": "4b7777ff"}
WIRE = {"d": "30393dff", "s": "475258ff", "c": "986c55ff", "h": "c18d61ff", "t": "3e9693ff", "u": "72dfbcff"}
GLASS = {"d": "273a47ff", "s": "3e6572ff", "t": "62a19fff", "u": "a1ded0ff", "b": "dbf2dcff"}
PAPER = {"d": "5b5149ff", "e": "b79c77ff", "p": "dfcba3ff", "h": "f2ddb4ff", "i": "6d9088ff", "b": "49716fff"}

save_texture("energy_copper", [
    "ddssssssddssssdd", "dshhhhhhdshhhhsd", "shlccccclshlcccsh", "shcddddcshcddddsh",
    "shcdttdcshcdttdsh", "shcdttdcshcdttdsh", "shcddddcshcddddsh", "shlccccclshlcccsh",
    "ddssssssddssssdd", "dshhhhhhdshhhhsd", "shlccccclshlcccsh", "shcddddcshcddddsh",
    "shcdtudcshcdtudsh", "shcdttdcshcdttdsh", "shcddddcshcddddsh", "shlccccclshlcccsh",
], COPPER)
save_texture("energy_stone", [
    "dssssssddssssssd", "smmmlmmssmmmlmms", "smmmmmmsmmmmmmls", "smlmmmlsmlmmmmls",
    "dssssssddssssssd", "smmmmmlssmmmmmls", "smmmtmmsmmmlmmls", "smmmmmmsmmmmmmls",
    "dssssssddssssssd", "smlmmmmsmmmmmmls", "smmmmmmsmmtmmmls", "smmmmmlssmmmmmms",
    "dssssssddssssssd", "smmmmmmsmmmmmmls", "smlmmmmsmmmmmlms", "dssssssddssssssd",
], STONE)
save_texture("energy_wire", [
    "dddddddddddddddd", "dssssssssssssssd", "dsccccccccccccsd", "dschhhhhhhhhhcsd",
    "dschtttttttthcsd", "dschuuuuuuuuhcsd", "dschuuuuuuuuhcsd", "dschuuuuuuuuhcsd",
    "dschuuuuuuuuhcsd", "dschuuuuuuuuhcsd", "dschuuuuuuuuhcsd", "dschtttttttthcsd",
    "dschhhhhhhhhhcsd", "dsccccccccccccsd", "dssssssssssssssd", "dddddddddddddddd",
], WIRE)
save_texture("energy_glass", [
    "dddddddddddddddd", "dssssssssssssssd", "dsttttttttttttsd", "dstuuuuuuuuuuutsd",
    "dstuuuuuuuuuuutsd", "dstuuuubbbuuutsd", "dstuuubbbbuuu tsd".replace(" ", ""), "dstuuubbbbuuu tsd".replace(" ", ""),
    "dstuuubbbbuuu tsd".replace(" ", ""), "dstuuubbbbuuu tsd".replace(" ", ""), "dstuuuubbbuuutsd", "dstuuuuuuuuuuutsd",
    "dstuuuuuuuuuuutsd", "dsttttttttttttsd", "dssssssssssssssd", "dddddddddddddddd",
], GLASS)
save_texture("energy_parchment", [
    "dddddddddddddddd", "deeeeeeeeeeeeeed", "deppphpphppppped", "depppppppppppped",
    "depppibbbipppped", "deppipppppbppped", "deppipppppbppped", "depppibbbipppped",
    "depppppppppppped", "depppibbbipppped", "deppipppppbppped", "depppibbbipppped",
    "depppppppppppped", "depphppppphppped", "deeeeeeeeeeeeeed", "dddddddddddddddd",
], PAPER)


def faces(texture: str) -> dict[str, dict[str, str]]:
    return {direction: {"texture": f"#{texture}"} for direction in ("north", "south", "east", "west", "up", "down")}


def part(low: tuple[int, int, int], high: tuple[int, int, int], texture: str) -> dict:
    return {"from": list(low), "to": list(high), "faces": faces(texture)}


TEXTURES = {
    "copper": "tnc:block/energy_copper",
    "stone": "tnc:block/energy_stone",
    "wire": "tnc:block/energy_wire",
    "glass": "tnc:block/energy_glass",
    "paper": "tnc:block/energy_parchment",
}
MODELS = {
    "mana_generator": [
        part((2, 0, 2), (14, 3, 14), "stone"),
        part((3, 3, 3), (13, 6, 13), "copper"),
        part((5, 6, 5), (11, 12, 11), "wire"),
        part((4, 12, 4), (12, 14, 12), "copper"),
        part((6, 14, 6), (10, 16, 10), "glass"),
    ],
    "mana_battery": [
        part((2, 0, 2), (14, 3, 14), "copper"),
        part((3, 3, 3), (13, 13, 13), "glass"),
        part((5, 5, 5), (11, 12, 11), "wire"),
        part((2, 13, 2), (14, 16, 14), "copper"),
    ],
    "mana_work_lamp": [
        part((5, 0, 5), (11, 2, 11), "stone"),
        part((7, 2, 7), (9, 9, 9), "copper"),
        part((3, 9, 3), (13, 14, 13), "glass"),
        part((5, 10, 5), (11, 13, 11), "wire"),
        part((2, 14, 2), (14, 16, 14), "copper"),
    ],
    "mana_cable": [
        part((5, 5, 5), (11, 11, 11), "wire"),
        part((0, 6, 6), (5, 10, 10), "copper"),
        part((11, 6, 6), (16, 10, 10), "copper"),
        part((6, 6, 0), (10, 10, 5), "copper"),
        part((6, 6, 11), (10, 10, 16), "copper"),
        part((6, 0, 6), (10, 5, 10), "copper"),
        part((6, 11, 6), (10, 16, 10), "copper"),
    ],
    "mana_paper_press": [
        part((1, 0, 1), (15, 3, 15), "stone"),
        part((2, 3, 2), (14, 5, 14), "copper"),
        part((3, 5, 4), (13, 6, 12), "paper"),
        part((2, 6, 3), (4, 9, 13), "wire"),
        part((12, 6, 3), (14, 9, 13), "wire"),
        part((2, 9, 2), (14, 11, 14), "copper"),
        part((5, 11, 5), (11, 14, 11), "glass"),
    ],
}

for name, elements in MODELS.items():
    (MODEL / f"{name}.json").write_text(json.dumps({
        "render_type": "minecraft:cutout",
        "textures": {"particle": TEXTURES["copper"], **TEXTURES},
        "elements": elements,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (ITEM / f"{name}.json").write_text(json.dumps({
        "parent": f"tnc:block/{name}"
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (STATES / f"{name}.json").write_text(json.dumps({
        "variants": {
            "lit=false": {"model": f"tnc:block/{name}"},
            "lit=true": {"model": f"tnc:block/{name}"},
        }
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

press_lit = {
    "render_type": "minecraft:cutout",
    "textures": {"particle": TEXTURES["copper"], **TEXTURES},
    "elements": MODELS["mana_paper_press"] + [part((4, 6, 4), (12, 7, 12), "glass")],
}
(MODEL / "mana_paper_press_lit.json").write_text(
    json.dumps(press_lit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(STATES / "mana_paper_press.json").write_text(json.dumps({
    "variants": {
        "lit=false": {"model": "tnc:block/mana_paper_press"},
        "lit=true": {"model": "tnc:block/mana_paper_press_lit"},
    }
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
