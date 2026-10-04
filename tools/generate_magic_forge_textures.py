"""Draw TN-C's original tiny workshop textures; no third-party mod art is used."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"

INK = "#14212d"
IRON = "#303947"
COPPER_DARK = "#5d362d"
COPPER = "#aa6744"
COPPER_LIGHT = "#e3a36a"
GOLD = "#ffd28a"
BLUE_DARK = "#163c59"
BLUE = "#41bbd0"
BLUE_LIGHT = "#bbfaff"
PURPLE = "#66578a"


def save(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination)


def forge_side() -> Image.Image:
    image = Image.new("RGBA", (16, 16), INK)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), fill=IRON)
    d.rectangle((1, 1, 14, 14), outline=COPPER_DARK, width=2)
    d.line((2, 2, 13, 2), fill=COPPER_LIGHT, width=1)
    d.line((2, 13, 13, 13), fill=COPPER, width=1)
    d.rectangle((4, 4, 11, 11), fill=BLUE_DARK, outline=COPPER)
    d.polygon([(8, 4), (11, 8), (8, 11), (5, 8)], outline=GOLD)
    d.polygon([(8, 6), (10, 8), (8, 10), (6, 8)], fill=BLUE)
    d.point((8, 8), fill=BLUE_LIGHT)
    for x in (2, 13):
        for y in (5, 10):
            d.rectangle((x, y, x + 1, y + 1), fill=COPPER_LIGHT)
    d.line((0, 15, 15, 15), fill=COPPER_DARK)
    return image


def forge_bottom() -> Image.Image:
    image = Image.new("RGBA", (16, 16), INK)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), outline=COPPER_DARK, width=2)
    for x in (4, 11):
        d.line((x, 2, x, 13), fill=IRON, width=2)
    for y in (4, 11):
        d.line((2, y, 13, y), fill=IRON, width=2)
    d.rectangle((6, 6, 9, 9), fill=COPPER)
    return image


def forge_top(lit: bool) -> Image.Image:
    image = Image.new("RGBA", (16, 16), COPPER_DARK)
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 14, 14), outline=COPPER_LIGHT, width=2)
    d.rectangle((3, 3, 12, 12), fill=INK, outline=COPPER)
    d.ellipse((4, 4, 11, 11), fill=BLUE if lit else BLUE_DARK, outline=GOLD if lit else PURPLE)
    d.ellipse((6, 6, 9, 9), fill=BLUE_LIGHT if lit else BLUE)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=GOLD)
    if lit:
        d.point((8, 4), fill=BLUE_LIGHT)
        d.point((4, 8), fill=BLUE_LIGHT)
        d.point((11, 8), fill=BLUE_LIGHT)
        d.point((8, 11), fill=BLUE_LIGHT)
    return image


def lamp_metal() -> Image.Image:
    image = Image.new("RGBA", (16, 16), COPPER)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), outline=COPPER_DARK, width=2)
    d.line((2, 2, 13, 2), fill=GOLD)
    d.line((2, 8, 13, 8), fill=COPPER_LIGHT)
    d.line((2, 13, 13, 13), fill=COPPER_DARK)
    for x, y in ((3, 4), (12, 4), (3, 11), (12, 11)):
        d.point((x, y), fill=GOLD)
    return image


def lamp_glass() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), outline=COPPER_LIGHT, width=2)
    d.line((2, 2, 13, 13), fill=PURPLE, width=1)
    d.line((13, 2, 2, 13), fill=BLUE, width=1)
    d.rectangle((3, 3, 12, 12), outline=BLUE, width=1)
    d.point((4, 4), fill=BLUE_LIGHT)
    d.point((11, 4), fill=BLUE_LIGHT)
    return image


def lamp_core() -> Image.Image:
    image = Image.new("RGBA", (16, 16), BLUE_DARK)
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 14, 14), fill=BLUE)
    d.polygon([(8, 1), (14, 8), (8, 14), (1, 8)], fill=BLUE_LIGHT)
    d.polygon([(8, 4), (11, 8), (8, 11), (4, 8)], fill=GOLD)
    d.point((8, 8), fill="#ffffff")
    return image


def copper_coil() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    # A perspective spool, with two open ends joined by copper windings.
    d.ellipse((6, 10, 25, 29), fill=COPPER_DARK, outline=INK, width=2)
    d.ellipse((6, 8, 25, 25), fill=COPPER, outline=COPPER_LIGHT, width=2)
    d.ellipse((9, 10, 22, 23), fill=IRON, outline=GOLD, width=2)
    d.ellipse((12, 13, 19, 20), fill=BLUE, outline=BLUE_LIGHT, width=1)
    d.point((15, 16), fill="#ffffff")
    d.arc((6, 7, 25, 24), 205, 335, fill=GOLD, width=2)
    d.arc((5, 10, 26, 30), 15, 150, fill=COPPER_LIGHT, width=2)
    d.line((5, 19, 8, 23, 10, 24), fill=COPPER_LIGHT, width=2)
    d.line((22, 23, 25, 19), fill=COPPER_LIGHT, width=2)
    d.polygon([(23, 5), (24, 8), (27, 9), (24, 10), (23, 13), (22, 10), (19, 9), (22, 8)], fill=BLUE_LIGHT)
    d.point((27, 15), fill=GOLD)
    d.point((3, 12), fill=BLUE)
    return image


def main() -> None:
    art = {
        BLOCK / "magic_forge_side.png": forge_side(),
        BLOCK / "magic_forge_bottom.png": forge_bottom(),
        BLOCK / "magic_forge_top.png": forge_top(False),
        BLOCK / "magic_forge_top_lit.png": forge_top(True),
        BLOCK / "homeward_lamp_metal.png": lamp_metal(),
        BLOCK / "homeward_lamp_glass.png": lamp_glass(),
        BLOCK / "homeward_lamp_core.png": lamp_core(),
        ITEM / "mana_copper_coil.png": copper_coil(),
    }
    for destination, image in art.items():
        save(image, destination)
        print(destination.relative_to(ROOT), image.size)


if __name__ == "__main__":
    main()
