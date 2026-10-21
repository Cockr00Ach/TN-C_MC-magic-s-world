"""Print any image as ASCII art (dev aid for text-only sessions).

The screenshot/preview PNGs in this repo normally get *looked* at. When the
session has no image input, this is the fallback: downsample the PNG to a
character grid so layout problems (blank panel, camera wrong, cropped head) are
still visible.

    python tools/img2ascii.py docs/previews/canglan_warlord_model.png
    python tools/img2ascii.py docs/previews/canglan_warlord_model.png --cols 150
"""
import sys
from pathlib import Path

from PIL import Image

RAMP = " .:-=+*#%@"


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 1
    path = Path(args[0])
    cols = int(args[args.index("--cols") + 1]) if "--cols" in args else 120
    image = Image.open(path).convert("L")
    width, height = image.size
    # terminal cells are about twice as tall as wide
    rows = max(1, int(cols * height / width * 0.5))
    small = image.resize((cols, rows), Image.LANCZOS)
    pixels = small.load()
    for y in range(rows):
        line = "".join(RAMP[min(len(RAMP) - 1, pixels[x, y] * len(RAMP) // 256)]
                       for x in range(cols))
        print(line.rstrip())
    print(f"{path.name} {width}x{height} -> {cols}x{rows}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
