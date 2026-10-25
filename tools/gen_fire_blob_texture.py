"""Generate the 4 pixel flame sprites used by the fireball particles.

Only writes these four images, nothing else is touched:
  src/main/resources/assets/tnc/textures/particle/fire_blob_0..3.png

Each sprite is a single small flame tongue (16x16, transparent background,
hard pixel edges, no radial gradient disc). The tongue leans and splits
differently per frame. The particle classes tint these sprites per layer,
so the base art is a warm near-white with orange edges.
"""

import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/tnc/textures/particle")

SIZE = 16

# Base sprite colours: bright enough that per-layer vertex tint decides the
# final hue. Core pixels stay near white, edges lean orange.
CORE = (255, 250, 232)
MID = (255, 214, 148)
EDGE = (248, 156, 78)
DARK = (222, 112, 48)


def width_at(t, phase):
    """Half width of the flame at height t (0 bottom .. 1 top)."""
    if t <= 0.0:
        base = 5.0
    else:
        base = 5.0 * (1.0 - t) ** 0.55
    wobble = 0.85 + 0.30 * math.sin(t * 6.4 + phase) + 0.12 * math.sin(t * 13.0 + phase * 2.1)
    return max(0.0, base * wobble)


def build_frame(index):
    seed = 1701 + index * 91
    random.seed(seed)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    phase = index * 1.7
    lean = (index - 1.5) * 0.55
    split_top = index % 2 == 0

    for y in range(SIZE):
        # PIL y=0 is the top row; the flame base must sit at the bottom of the image.
        t = 1.0 - y / (SIZE - 1.0)
        cx = 7.5 + lean * t + 0.9 * math.sin(t * 4.2 + phase)
        w = width_at(t, phase)
        if t > 0.86:
            w *= 0.55
        for x in range(SIZE):
            dx = x + 0.5 - cx
            inside = abs(dx) <= w
            # a thin split near the tip on every other frame
            if inside and split_top and t > 0.74 and abs(dx) < 0.55:
                inside = False
            if not inside:
                continue
            d = abs(dx) / max(w, 0.001)
            if t < 0.12:
                col = DARK
            elif d < 0.34:
                col = CORE
            elif d < 0.68:
                col = MID
            else:
                col = EDGE
            n = random.randint(-10, 10)
            if random.random() < 0.03:
                n += 18
            px[x, y] = (
                max(0, min(255, col[0] + n)),
                max(0, min(255, col[1] + n)),
                max(0, min(255, col[2] + n)),
                255,
            )

    # a couple of detached embers around the tongue, keeps the silhouette ragged
    for _ in range(random.randint(2, 4)):
        x = random.randint(1, SIZE - 2)
        y = random.randint(2, SIZE - 3)
        if px[x, y][3] == 0:
            px[x, y] = (EDGE[0], EDGE[1], EDGE[2], 235)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for i in range(4):
        path = os.path.join(OUT_DIR, "fire_blob_%d.png" % i)
        build_frame(i).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
