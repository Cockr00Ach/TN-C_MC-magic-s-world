# -*- coding: utf-8 -*-
"""gen_light_wings.py -- light-element wing texture (translucent, glowing, feathered).

Layout (64x64, one wing; the renderer mirrors it for the other side):
    the wing grows from the bottom-right corner (shoulder) up-left (tip),
    so the renderer can flip it on the X axis for the left wing.
    UV space is 0..16 like every other prop here, so a quad samples 0,0 .. 16,16.

ASCII only.
"""
import os
from PIL import Image

NAME = "light_wings"

def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # main feather sweeps: from the shoulder (bottom-right) to the tip (top-left)
    sweeps = [
        (58, 62, 6, 8, 9),    # (x0, y0, x1, y1, thickness)
        (58, 60, 10, 14, 8),
        (56, 58, 14, 20, 8),
        (54, 56, 18, 26, 7),
        (52, 54, 22, 32, 7),
        (50, 52, 26, 38, 6),
        (48, 50, 30, 44, 6),
        (46, 48, 34, 50, 5),
    ]
    for (x0, y0, x1, y1, th) in sweeps:
        steps = max(abs(x1 - x0), abs(y1 - y0)) * 2
        for i in range(steps + 1):
            t = i / steps
            cx = x0 + (x1 - x0) * t
            cy = y0 + (y1 - y0) * t
            fade = 1.0 - 0.55 * t                      # tip is fainter
            for dx in range(-th, th + 1):
                for dy in range(-th, th + 1):
                    d = (dx * dx + dy * dy) ** 0.5
                    if d > th:
                        continue
                    a = int(235 * fade * (1.0 - 0.75 * (d / max(1, th))))
                    if a <= 4:
                        continue
                    x, y = int(cx) + dx, int(cy) + dy
                    if 0 <= x < 64 and 0 <= y < 64:
                        # warm white core -> gold edge
                        core = 1.0 - (d / max(1, th))
                        r = int(255)
                        g = int(232 + 20 * core)
                        b = int(150 + 95 * core)
                        old = img.getpixel((x, y))
                        if a > old[3]:
                            img.putpixel((x, y), (min(255, r), min(255, g), min(255, b), a))
    # bright leading edge
    for i in range(0, 60):
        for (x0, y0, x1, y1, th) in sweeps[:1]:
            pass
    return img

def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tp = os.path.join(repo, "src", "main", "resources", "assets", "tnc", "textures", "entity", NAME + ".png")
    pp = os.path.join(repo, "docs", "previews", NAME + "_preview.png")
    os.makedirs(os.path.dirname(tp), exist_ok=True)
    tex = texture()
    tex.save(tp)
    tex.resize((256, 256), Image.NEAREST).save(pp)
    print("texture -> %s (%d bytes)" % (os.path.relpath(tp, repo), os.path.getsize(tp)))
    print("preview -> %s" % os.path.relpath(pp, repo))

if __name__ == "__main__":
    main()