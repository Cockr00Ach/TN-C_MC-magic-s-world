"""Pixel-author the independent bellwool sheep's 64px entity skin.

The atlas coordinates match BellwoolSheepModel. Its design uses warm fleece,
dark teal stepped horns/collar, a copper chest bell and two visible eyes.
"""

from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/textures/entity/bellwool_sheep.png"
ITEM_OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/textures/item/resonant_felt.png"


def rgba(hex_color):
    return (*bytes.fromhex(hex_color), 255)


im = Image.new("RGBA", (64, 64), rgba("473e3c"))
p = im.load()


def area(x0, y0, x1, y1, base, dark, light, scale=5):
    a, b, c = map(rgba, (base, dark, light))
    for y in range(y0, y1):
        for x in range(x0, x1):
            if x in (x0, x1-1) or y in (y1-1,):
                p[x,y] = b
            elif (x + 2*y) % (scale*2+3) in (0, 1):
                p[x,y] = c
            else:
                p[x,y] = a


# Each rectangular region is one cubic-model UV island; no smooth gradients.
area(0, 0, 29, 15, "e7d6b6", "bca68a", "f4e5ca")      # head
area(32, 0, 64, 18, "2b6970", "183e4a", "49838a")    # horns + collar
area(0, 18, 52, 45, "e1cfad", "a9977d", "f0dfc1")     # long torso
area(52, 18, 64, 30, "504a46", "2c3033", "77726c")   # short legs/hooves
area(0, 46, 52, 64, "f2dfc0", "c4ae91", "fff0d0")     # cuttable fleece ridge and side panels
area(52, 42, 64, 49, "bd7443", "76492f", "edaa62")    # copper bell

# Face pixels: two dark eyes and a low square pink muzzle in the north-face UV.
for x in (9, 13):
    p[x, 9] = rgba("2d3138")
    p[x, 10] = rgba("161e29")
for x in range(10, 13):
    for y in range(12, 14):
        p[x, y] = rgba("bb8986") if y == 12 else rgba("8d646b")
p[11, 13] = rgba("604c54")

# Woven cyan path through the raised wool. Tiny enough to feel like a thread.
for x in (5, 18, 30, 43):
    for dx, dy in ((0, 0), (1, 1), (2, 2)):
        p[x+dx, 52+dy] = rgba("70aaa8")

# Gold seam on the neck band; this reads even when the fleece has been sheared.
for x in (38, 45, 53):
    p[x, 9] = rgba("c5905b")
    p[x, 10] = rgba("ecc089")

OUT.parent.mkdir(parents=True, exist_ok=True)
im.save(OUT)

# A small rolled felt travel mat with the same cyan resonant seam.
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
q = icon.load()
for y in range(4, 13):
    for x in range(2, 14):
        if (x - 7.5)**2 / 40 + (y - 8)**2 / 22 <= 1.3:
            q[x,y] = rgba("c4ad8d") if x in (2, 13) or y in (4, 12) else rgba("e6d4b0")
for y in range(5, 12):
    q[4,y] = rgba("356f73")
    q[5,y] = rgba("70aca5") if y % 2 else rgba("2e606b")
for x in range(6, 13):
    q[x,8] = rgba("95c9b8") if x % 3 == 0 else rgba("466f70")
for x, y in ((11, 5), (12, 6), (11, 11), (12, 10)):
    q[x,y] = rgba("f7e7c7")
ITEM_OUT.parent.mkdir(parents=True, exist_ok=True)
icon.save(ITEM_OUT)
print(OUT, ITEM_OUT)
