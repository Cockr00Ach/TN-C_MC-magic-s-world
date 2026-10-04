"""Original 16-pixel workshop art and matching block/item JSON for the 3x3x3 forge.

Run from any directory. Deliberately generates only the forge's own assets.
"""

import json
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc"
TEX = ROOT / "textures/block"
MODELS = ROOT / "models/block"
ITEMS = ROOT / "models/item"
ITEM_TEX = ROOT / "textures/item"
STATES = ROOT / "blockstates"
LOOT = ROOT.parent.parent / "data/tnc/loot_tables/blocks"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def brick(name, base="#454c54", seam="#272e36", accent="#aa6c49"):
    image = Image.new("RGBA", (16, 16), base)
    pen = ImageDraw.Draw(image)
    rng = random.Random(741 + sum(map(ord, name)))
    for y in (0, 7, 15):
        pen.line((0, y, 15, y), fill=seam)
    for x, y0, y1 in ((5, 0, 6), (12, 0, 6), (2, 8, 14), (9, 8, 14)):
        pen.line((x, y0, x, y1), fill=seam)
    for _ in range(13):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        if y not in (7, 15): pen.point((x, y), fill=rng.choice(("#697178", "#58616a", accent)))
    image.save(TEX / f"{name}.png")


def face(name, kind, lit=False):
    image = Image.open(TEX / "forge_firebrick.png").copy()
    pen = ImageDraw.Draw(image)
    ink, copper, highlight = "#17252e", "#a66b48", "#e4a777"
    if kind == "core":
        pen.rectangle((2, 2, 13, 14), fill=copper, outline="#523b37")
        pen.rectangle((3, 3, 12, 13), fill=ink)
        pen.arc((3, 2, 12, 11), 180, 360, fill=highlight, width=2)
        pen.polygon(((6, 11), (8, 6), (10, 11), (9, 13), (7, 13)),
                    fill="#65e1cc" if lit else "#568ca3")
        pen.line((2, 14, 13, 14), fill=highlight, width=1)
    elif kind == "input":
        pen.rectangle((2, 3, 13, 12), fill=copper, outline=highlight)
        pen.rectangle((4, 5, 11, 10), fill=ink)
        for x in (5, 8, 11): pen.line((x, 5, x, 10), fill="#e3aa72")
        pen.point((8, 13), fill="#5fdfca")
    elif kind == "output":
        pen.rectangle((2, 3, 13, 13), fill=copper, outline=highlight)
        pen.polygon(((4, 5), (11, 5), (10, 11), (5, 11)), fill=ink)
        pen.line((5, 10, 10, 10), fill="#71d7c9", width=1)
    elif kind == "injector":
        pen.rectangle((2, 2, 13, 13), fill=ink, outline=copper, width=2)
        pen.polygon(((8, 2), (12, 8), (8, 13), (4, 8)), fill="#5d7195", outline=highlight)
        pen.polygon(((8, 4), (10, 8), (8, 11), (6, 8)), fill="#9de9de")
    image.save(TEX / f"{name}.png")


def vent():
    image = Image.new("RGBA", (16, 16), "#3b4149")
    pen = ImageDraw.Draw(image)
    pen.rectangle((1, 1, 14, 14), fill="#bd8055", outline="#543c34", width=2)
    pen.rectangle((4, 4, 11, 11), fill="#18232d", outline="#efa971")
    pen.line((5, 7, 10, 7), fill="#727e84")
    pen.line((5, 9, 10, 9), fill="#727e84")
    image.save(TEX / "forge_exhaust.png")


def frame(lit):
    image = Image.new("RGBA", (16, 16), "#76513d")
    pen = ImageDraw.Draw(image)
    pen.rectangle((0, 0, 15, 15), outline="#281f24", width=2)
    for y in (2, 13): pen.line((2, y, 13, y), fill="#d9a06a", width=2)
    pen.line((5, 4, 10, 11), fill="#4d352e", width=2)
    pen.line((10, 4, 5, 11), fill="#4d352e", width=2)
    pen.rectangle((6, 6, 9, 9), fill="#74e6cd" if lit else "#567a79")
    image.save(TEX / ("forge_frame_glow.png" if lit else "forge_frame.png"))


def guide_icon():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)
    pen.polygon(((2, 3), (8, 2), (14, 3), (14, 13), (8, 12), (2, 13)), fill="#a96a42", outline="#35272a")
    pen.polygon(((3, 4), (8, 3), (8, 11), (3, 12)), fill="#efe1bb")
    pen.polygon(((8, 3), (13, 4), (13, 12), (8, 11)), fill="#d8c399")
    pen.line((8, 3, 8, 12), fill="#4c3a33")
    pen.rectangle((4, 6, 6, 8), fill="#568d8e")
    pen.rectangle((10, 6, 11, 8), fill="#568d8e")
    pen.line((4, 10, 6, 10), fill="#956b4e")
    pen.line((10, 10, 12, 10), fill="#956b4e")
    ITEM_TEX.mkdir(parents=True, exist_ok=True)
    image.save(ITEM_TEX / "forge_guide.png")
    write(ITEMS / "forge_guide.json", {"parent": "minecraft:item/generated",
          "textures": {"layer0": "tnc:item/forge_guide"}})


def cube(name, texture):
    write(MODELS / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"tnc:block/{texture}"}})
    write(ITEMS / f"{name}.json", {"parent": f"tnc:block/{name}"})
    write(STATES / f"{name}.json", {"variants": {"": {"model": f"tnc:block/{name}"}}})


def orient(name, front, side="forge_firebrick", top="forge_firebrick"):
    write(MODELS / f"{name}.json", {"parent": "minecraft:block/orientable_with_bottom",
          "textures": {"front": f"tnc:block/{front}", "side": f"tnc:block/{side}",
                       "top": f"tnc:block/{top}", "bottom": f"tnc:block/{side}"}})
    write(ITEMS / f"{name}.json", {"parent": f"tnc:block/{name}"})


def direction_variants(name, lit=False):
    values = {}
    for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        if lit:
            for active in (False, True):
                values[f"facing={facing},lit={str(active).lower()}"] = {
                    "model": f"tnc:block/{name}{'_lit' if active else ''}", "y": rotation}
        else: values[f"facing={facing}"] = {"model": f"tnc:block/{name}", "y": rotation}
    write(STATES / f"{name}.json", {"variants": values})


def frame_model(name, texture):
    # Narrow posts and wider cap and foot reveal a real hollow chamber between corners.
    def element(start, end):
        return {"from": start, "to": end, "faces": {side: {"texture": "#pillar"}
                for side in ("north", "south", "east", "west", "up", "down")}}
    write(MODELS / f"{name}.json", {"textures": {"pillar": f"tnc:block/{texture}",
          "particle": f"tnc:block/{texture}"}, "elements": [
              element([4, 0, 4], [12, 16, 12]),
              element([2, 0, 2], [14, 3, 14]),
              element([2, 13, 2], [14, 16, 14])]})


def exhaust_model():
    def element(start, end):
        return {"from": start, "to": end, "faces": {side: {"texture": "#metal"}
                for side in ("north", "south", "east", "west", "up", "down")}}
    write(MODELS / "forge_exhaust.json", {"textures": {"metal": "tnc:block/forge_exhaust",
          "particle": "tnc:block/forge_exhaust"}, "elements": [
              element([0, 0, 0], [16, 5, 16]), element([3, 5, 3], [13, 16, 13])]})
    write(ITEMS / "forge_exhaust.json", {"parent": "tnc:block/forge_exhaust"})
    write(STATES / "forge_exhaust.json", {"variants": {"": {"model": "tnc:block/forge_exhaust"}}})


def main():
    for folder in (TEX, MODELS, ITEMS, STATES): folder.mkdir(parents=True, exist_ok=True)
    brick("forge_firebrick")
    face("forge_core_front", "core")
    face("forge_core_front_lit", "core", True)
    face("forge_input_front", "input")
    face("forge_output_front", "output")
    face("forge_injector_front", "injector")
    vent()
    frame(False); frame(True)
    guide_icon()
    cube("forge_firebrick", "forge_firebrick")
    cube("forge_input_port", "forge_input_front")
    cube("forge_output_port", "forge_output_front")
    orient("magic_forge", "forge_core_front")
    orient("magic_forge_lit", "forge_core_front_lit")
    direction_variants("magic_forge", True)
    orient("forge_mana_injector", "forge_injector_front")
    direction_variants("forge_mana_injector")
    frame_model("forge_copper_frame", "forge_frame")
    frame_model("forge_copper_frame_glow", "forge_frame_glow")
    write(ITEMS / "forge_copper_frame.json", {"parent": "tnc:block/forge_copper_frame"})
    write(STATES / "forge_copper_frame.json", {"variants": {
        "glow=false": {"model": "tnc:block/forge_copper_frame"},
        "glow=true": {"model": "tnc:block/forge_copper_frame_glow"}}})
    exhaust_model()
    for name in ("forge_firebrick", "forge_copper_frame", "forge_input_port",
                 "forge_output_port", "forge_mana_injector", "forge_exhaust"):
        write(LOOT / f"{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"tnc:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


if __name__ == "__main__": main()
