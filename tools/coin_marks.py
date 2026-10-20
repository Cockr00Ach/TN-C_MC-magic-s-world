# -*- coding: utf-8 -*-
"""
coin_marks.py -- keep the seven marks on the coin faces identical on gold / silver / copper.

WHAT THESE ARE (author 2026-10-08: "金币上有七个不同的像素点在正反面，
                  这七个像素点在银铜币上都要保留")
    coin_gold.png carries seven deliberately placed single-pixel marks on the coin's front
    face, and the same seven again on the back face (the atlas repeats the face 16 px lower):

        (6,1) red            (7,1)+(8,1) pale yellow (one 2 px mark)
        (9,1) blue           (1,6) black
        (14,6) white         (4,14) green
        (11,14) brown

    `coin_gold.png` is the reference.  The silver/copper recolours merged two of the marks
    into their own palette -- silver turned the pale yellow into (237,237,237) and the white
    into (67,67,67), copper turned the pale yellow into (250,213,176) -- so those marks are
    no longer visible on the silver and copper coins.  This tool copies the reference value
    of every mark pixel (front and back) onto the other two textures.

USAGE
    python tools/coin_marks.py            # copy the missing/renamed marks, report the diffs
    python tools/coin_marks.py --check    # report only, exit 1 when a mark is off

ASCII only.
"""
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "tnc", "textures", "item")
REFERENCE = "coin_gold"
OTHERS = ("coin_silver", "coin_copper")

# the seven marks, front face; the back face is the same 16 px lower in the atlas
MARKS = ((6, 1), (7, 1), (8, 1), (9, 1), (1, 6), (14, 6), (4, 14), (11, 14))
FACE_OFFSETS = (0, 16)


def load(name):
    return Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")


def mark_pixels():
    out = []
    for x, y in MARKS:
        for offset in FACE_OFFSETS:
            out.append((x, y + offset))
    return out


def main():
    check_only = "--check" in sys.argv
    reference = load(REFERENCE)
    pixels = mark_pixels()

    colours = {reference.getpixel(p) for p in pixels}
    print("%s reference marks: %d pixels, %d distinct colours (the 'seven marks')"
          % (REFERENCE, len(pixels), len(colours)))

    problems = 0
    for name in OTHERS:
        path = os.path.join(TEX_DIR, name + ".png")
        image = load(name)
        different = [(p, image.getpixel(p), reference.getpixel(p))
                     for p in pixels if image.getpixel(p) != reference.getpixel(p)]
        if not different:
            print("%-12s all %d mark pixels identical to %s" % (name, len(pixels), REFERENCE))
            continue
        for point, got, want in different:
            print("%-12s mark %s is %s, reference %s" % (name, point, got[:3], want[:3]))
        if check_only:
            problems += 1
            continue
        for point, _got, want in different:
            image.putpixel(point, want)
        image.save(path)
        print("%-12s %d mark pixel(s) restored from %s" % (name, len(different), REFERENCE))

    if check_only and problems:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
