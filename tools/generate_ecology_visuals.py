"""Build hand-authored, hard-pixel ecology materials and cuboid block models.

The geometry and palettes are intentionally kept here as editable source. The
output is standard vanilla 1.20.1 resource-pack JSON/PNG and needs no library
at game runtime. No borrowed mod assets are used.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc"
TEXTURES = ROOT / "textures/block"
MODELS = ROOT / "models/block"
ENTITY_TEXTURES = ROOT / "textures/entity"


def color(hex_value: str) -> tuple[int, int, int, int]:
    value = hex_value.removeprefix("#")
    return tuple(bytes.fromhex(value)) + (255,)


# Deliberate palette variation, not full-canvas random noise. At 16px the
# slight streaks read like vanilla wool, vines, roots and baked ceramic.
PALETTES = {
    "eco_stem": ("526f46", "344d38", "79945c"),
    "eco_leaf": ("557b62", "35574d", "83a76f"),
    "eco_rose": ("c87691", "8b4d72", "efadad"),
    "eco_rose_bud": ("a6688a", "654e75", "d298ae"),
    "eco_gold": ("e6be70", "a77b49", "fae8a6"),
    "eco_cap": ("485c78", "303d59", "79a5a6"),
    "eco_cap_closed": ("536179", "34435d", "84929c"),
    "eco_gill": ("99c8ba", "5b8894", "d4e5b9"),
    "eco_wood": ("715c53", "4a4649", "a88769"),
    "eco_rain_leaf": ("477c74", "295854", "8ebfa8"),
    "eco_rain_leaf_dry": ("667a70", "3c5b57", "9cae85"),
    "eco_berry": ("79b9c8", "386d8e", "b6e4d6"),
    "eco_berry_dry": ("6b8694", "3d5d73", "9cb4ac"),
    "eco_root": ("7d695e", "504d59", "ae9a79"),
    "eco_arcane": ("6daeb0", "426f91", "b8e6cb"),
    "eco_wheat": ("c6a872", "8b714c", "ead29b"),
    "eco_gourd": ("8e6a83", "594f71", "bb8e91"),
    "eco_reed": ("438a83", "2d6068", "8dc5ac"),
    "eco_reed_head": ("d4bc84", "817d69", "e2dfb0"),
    "eco_moss": ("567c5c", "355a4d", "8fb878"),
    "eco_moss_glow": ("a7d1a0", "639d89", "d9eba4"),
    "eco_vein": ("4b8264", "2a6154", "84bf80"),
    "eco_vein_glow": ("77ca81", "478b6f", "b5e6a1"),
}


def write_materials() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, values in PALETTES.items():
        base, shadow, highlight = map(color, values)
        image = Image.new("RGBA", (16, 16), base)
        pixels = image.load()
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y == 15:
                    pixels[x, y] = shadow
                elif (x in (3, 10) and y in (2, 3, 9, 10)) or (x, y) in ((6, 5), (13, 6), (5, 12)):
                    pixels[x, y] = highlight
                elif (x + 2 * y) % 17 == 0:
                    pixels[x, y] = shadow
        if name == "eco_gourd":
            for x in (3, 8, 12):
                for y in range(2, 15):
                    pixels[x, y] = shadow if y % 4 else highlight
        if name in ("eco_arcane", "eco_vein_glow", "eco_moss_glow"):
            for x, y in ((2, 4), (6, 9), (11, 3), (13, 12)):
                pixels[x, y] = highlight
        if name in ("eco_wood", "eco_root", "eco_stem"):
            for x in (4, 11):
                for y in (3, 7, 11):
                    pixels[x, y] = shadow
        image.save(TEXTURES / f"{name}.png")


def write_bellwool_texture() -> None:
    """64px atlas aligned with BellwoolAdornmentModel's four texOffs bands."""
    ENTITY_TEXTURES.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (64, 64))
    pixels = image.load()
    bands = (
        ("e3d4b9", "bba88e", "f2e4cd"),  # short layered fleece
        ("285e66", "1c414c", "477e83"),  # stepped horns + clapper
        ("bd7849", "80503b", "e9ad65"),  # copper bell
        ("2b6971", "194752", "56918a"),  # stitched collar
    )
    for band, values in enumerate(bands):
        base, shadow, highlight = map(color, values)
        for y in range(band * 16, band * 16 + 16):
            for x in range(64):
                edge = y % 16
                if edge == 15 or x % 16 == 0:
                    pixels[x, y] = shadow
                elif (x // 3 + y // 4) % 7 == 0 or edge == 1:
                    pixels[x, y] = highlight
                else:
                    pixels[x, y] = base
    # Two cyan stitches in the collar; each is large enough to survive UVs.
    for x in (8, 19, 38, 51):
        for y in (51, 52):
            pixels[x, y] = color("8fcbc0")
    image.save(ENTITY_TEXTURES / "bellwool_sheep.png")


def box(x1, y1, z1, x2, y2, z2, tex: str, *, glow=False) -> dict:
    element = {
        "from": [x1, y1, z1],
        "to": [x2, y2, z2],
        "faces": {face: {"texture": f"#{tex}"} for face in
                  ("down", "up", "north", "south", "west", "east")},
    }
    if glow:
        element["shade"] = False
    return element


def model(name: str, elements: list[dict], **textures: str) -> None:
    payload = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {key: f"tnc:block/{value}" for key, value in textures.items()},
        "elements": elements,
    }
    MODELS.mkdir(parents=True, exist_ok=True)
    (MODELS / f"{name}.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def flower() -> None:
    t = dict(stem="eco_stem", leaf="eco_leaf", petal="eco_rose", bud="eco_rose_bud", heart="eco_gold")
    for bloom in (False, True):
        e = [box(7, 0, 7, 9, 11, 9, "stem"),
             box(3, 4, 6, 8, 5, 10, "leaf"),
             box(8, 6, 6, 13, 7, 10, "leaf")]
        if bloom:
            e += [box(5, 10, 5, 11, 12, 11, "petal"),
                  box(3, 10, 6, 6, 12, 10, "petal"),
                  box(10, 10, 6, 13, 12, 10, "petal"),
                  box(6, 10, 3, 10, 12, 6, "petal"),
                  box(6, 10, 10, 10, 12, 13, "petal"),
                  box(6, 12, 6, 10, 14, 10, "heart", glow=True)]
        else:
            e += [box(6, 10, 6, 10, 13, 10, "bud"),
                  box(7, 13, 7, 9, 14, 9, "heart")]
        model("homeward_flower_bloom" if bloom else "homeward_flower_bud", e, **t)


def hushcap() -> None:
    t = dict(stalk="eco_gill", cap="eco_cap", closed="eco_cap_closed", gill="eco_gill")
    for age in range(4):
        height = (3, 5, 8, 10)[age]
        for open_cap in (False, True):
            e = [box(7, 0, 7, 9, height - 1, 9, "stalk")]
            if age <= 1:
                e.append(box(6, height - 1, 6, 10, height + 1, 10, "closed"))
            elif open_cap:
                width = 5 if age == 2 else 4
                e += [box(width, height - 1, width, 16 - width, height, 16 - width, "gill"),
                      box(width, height, width, 16 - width, height + 2, 16 - width, "cap"),
                      box(width + 2, height + 2, width + 2, 14 - width, height + 3, 14 - width, "cap")]
            else:
                e += [box(5, height - 1, 5, 11, height + 2, 11, "closed"),
                      box(6, height + 2, 6, 10, height + 3, 10, "closed")]
            model(f"hushcap_{age}_{'open' if open_cap else 'closed'}", e, **t)


def rainletter() -> None:
    t = dict(wood="eco_wood", wetleaf="eco_rain_leaf", dryleaf="eco_rain_leaf_dry",
             wetberry="eco_berry", dryberry="eco_berry_dry")
    for age in range(4):
        for wet in (False, True):
            leaf = "wetleaf" if wet else "dryleaf"
            berry = "wetberry" if wet else "dryberry"
            top = (4, 6, 8, 10)[age]
            e = [box(7, 0, 7, 9, top, 9, "wood"),
                 box(5, top - 1, 5, 11, top + 1, 11, leaf)]
            if age >= 1:
                e += [box(4, 3, 7, 8, 4, 9, "wood"), box(8, 4, 7, 12, 5, 9, "wood"),
                      box(3, 3, 6, 7, 5, 10, leaf), box(9, 4, 6, 13, 6, 10, leaf)]
            if age >= 2:
                e += [box(6, 5, 3, 10, 6, 8, leaf), box(6, 5, 9, 10, 7, 13, leaf)]
            if age == 3:
                e += [box(3, 2, 7, 5, 4, 9, berry, glow=wet),
                      box(11, 3, 7, 13, 5, 9, berry, glow=wet),
                      box(7, 6, 3, 9, 8, 5, berry, glow=wet),
                      box(7, 6, 11, 9, 8, 13, berry, glow=wet)]
            model(f"rainletter_{age}_{'wet' if wet else 'dry'}", e, **t)


def mana_root() -> None:
    t = dict(root="eco_root", stem="eco_stem", leaf="eco_leaf", charge="eco_arcane")
    for age in range(4):
        for charge in range(5):
            e = [box(6, 0, 6, 10, 3 if age else 2, 10, "root")]
            if age >= 1:
                e += [box(7, 3, 7, 9, 5 + age, 9, "stem"),
                      box(4, 4, 6, 8, 5, 10, "leaf")]
            if age >= 2:
                e += [box(8, 5, 6, 12, 6, 10, "leaf"),
                      box(6, 6, 4, 10, 7, 8, "leaf")]
            if age >= 3:
                e += [box(6, 4, 6, 10, 7, 10, "root"),
                      box(6, 7, 8, 10, 8, 12, "leaf")]
            if charge:
                e.append(box(7, 1 + age, 7, 9, 3 + age + charge // 2, 9, "charge", glow=True))
            model(f"mana_root_{age}_{charge}", e, **t)


def roadbell() -> None:
    t = dict(stem="eco_stem", leaf="eco_leaf", bell="eco_wheat", tip="eco_gold")
    for age in range(8):
        height = 2 + age + age // 2
        e = []
        for x, offset in ((5, -2), (8, 0), (11, -1)):
            if age < 3 and x != 8 or age < 5 and x == 11:
                continue
            h = max(2, height + offset)
            e += [box(x, 0, 7, x + 1, h, 8, "stem"),
                  box(x - 2, h // 2, 6, x + 1, h // 2 + 1, 9, "leaf")]
            if age >= 4:
                e += [box(x - 1, h - 2, 6, x + 2, h + 1, 9, "bell"),
                      box(x, h + 1, 7, x + 1, h + 2, 8, "tip")]
        model(f"road_bell_crop_{age}", e, **t)


def night_gourd() -> None:
    t = dict(stem="eco_stem", leaf="eco_leaf", rind="eco_gourd", glow="eco_gold")
    for age in range(8):
        e = [box(7, 0, 3, 9, 1, 13, "stem"),
             box(4, 0, 7, 12, 1, 9, "stem")]
        if age >= 1:
            e += [box(3, 1, 5, 7, 2, 9, "leaf"),
                  box(9, 1, 7, 13, 2, 11, "leaf")]
        if age >= 4:
            size = 3 if age < 6 else 4
            e += [box(8 - size, 1, 8 - size, 8 + size, 2 + size, 8 + size, "rind"),
                  box(7, 2 + size, 7, 9, 5 + size, 9, "stem")]
            if age == 7:
                e += [box(8 - size, 3, 7, 9 - size, 5, 9, "glow", glow=True),
                      box(7 + size, 3, 7, 8 + size, 5, 9, "glow", glow=True)]
        model(f"night_gourd_crop_{age}", e, **t)


def tide_reed() -> None:
    t = dict(reed="eco_reed", head="eco_reed_head", leaf="eco_leaf")
    for age in range(4):
        height = 4 + age * 3
        e = []
        for x, offset in ((5, -2), (8, 0), (11, -1)):
            if age == 0 and x != 8:
                continue
            h = height + offset
            e += [box(x, 0, 7, x + 1, h, 8, "reed"),
                  box(x - 2, h // 2, 6, x + 1, h // 2 + 1, 9, "leaf")]
            if age >= 2:
                e.append(box(x - 1, h - 1, 6, x + 2, h + 2, 9, "head"))
        model(f"tide_reed_crop_{age}", e, **t)


def warning_moss() -> None:
    t = dict(moss="eco_moss", glow="eco_moss_glow")
    for age in range(4):
        for lit in (False, True):
            e = [box(5 - age // 2, 3, 14, 11 + age // 2, 8 + age, 16, "moss")]
            if age >= 1:
                e += [box(3, 5, 13, 7, 10, 15, "moss"),
                      box(9, 4, 13, 13, 11, 15, "moss")]
            if age >= 2:
                e.append(box(6, 9, 12, 10, 13, 14, "moss"))
            if lit:
                e += [box(6, 6, 12, 8, 8, 13, "glow", glow=True),
                      box(10, 8, 12, 12, 10, 13, "glow", glow=True)]
            model(f"warning_moss_{age}_{'lit' if lit else 'dark'}", e, **t)


def verdant_vein() -> None:
    t = dict(stem="eco_vein", leaf="eco_leaf", glow="eco_vein_glow", root="eco_root")
    for age in range(4):
        h = (4, 7, 10, 13)[age]
        e = [box(7, 0, 7, 9, h, 9, "stem"),
             box(5, 0, 6, 11, 2, 10, "root")]
        if age >= 1:
            e += [box(3, 4, 7, 8, 5, 10, "leaf"),
                  box(8, 6, 6, 13, 7, 9, "leaf")]
        if age >= 2:
            e += [box(6, h - 1, 3, 10, h, 8, "leaf"),
                  box(6, h - 2, 8, 10, h - 1, 13, "leaf")]
        if age == 3:
            e += [box(6, h, 6, 10, h + 2, 10, "glow", glow=True),
                  box(7, 5, 6, 9, 6, 10, "glow", glow=True)]
        model(f"verdant_vein_{age}", e, **t)


def main() -> None:
    write_materials()
    for generate in (flower, hushcap, rainletter, mana_root, roadbell,
                     night_gourd, tide_reed, warning_moss, verdant_vein):
        generate()
    print(f"Wrote {len(PALETTES)} materials and 70 Minecraft cuboid plant models")


if __name__ == "__main__":
    main()
