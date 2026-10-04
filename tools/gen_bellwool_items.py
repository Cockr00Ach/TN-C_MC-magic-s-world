"""Generate TN-C's original pixel icons for the bellwool husbandry loop."""

from pathlib import Path

from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/textures/item"
OUT.mkdir(parents=True, exist_ok=True)


def fodder() -> Image.Image:
    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    d.polygon([(3, 5), (12, 5), (14, 13), (12, 15), (3, 15), (1, 13)], fill="#8d624b", outline="#443b46")
    d.rectangle((3, 6, 12, 7), fill="#cba16b")
    d.polygon([(4, 8), (11, 8), (12, 13), (4, 13)], fill="#ead4a0")
    d.line((7, 11, 8, 8, 9, 11), fill="#97764e", width=1)
    d.ellipse((7, 10, 9, 12), fill="#e5b451", outline="#80553f")
    d.line((7, 5, 6, 2, 5, 1), fill="#659765")
    d.line((9, 5, 10, 2, 11, 1), fill="#82ad71")
    d.point((5, 1), fill="#f6e38e")
    d.point((11, 1), fill="#f6e38e")
    return im


def fleece() -> Image.Image:
    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    d.ellipse((2, 6, 8, 13), fill="#899ca3", outline="#3d5368")
    d.ellipse((7, 3, 14, 12), fill="#d9e4d3", outline="#586d79")
    d.ellipse((3, 2, 10, 10), fill="#f5efd3", outline="#70858a")
    d.line((6, 4, 7, 2, 9, 4), fill="#fff7d5")
    d.line((4, 8, 5, 11, 7, 12), fill="#bed9ce")
    d.point((11, 5), fill="#ffe7a0")
    d.point((12, 7), fill="#dba84e")
    d.point((3, 12), fill="#ecd784")
    return im


fodder().save(OUT / "bellwool_fodder.png")
fleece().save(OUT / "resonant_fleece.png")
