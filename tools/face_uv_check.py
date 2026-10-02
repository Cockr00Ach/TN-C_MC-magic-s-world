# -*- coding: utf-8 -*-
"""
face_uv_check.py -- which side of the head is the FACE (i.e. which way is "front")?

Box-UV layout used by Bedrock geometry with a single per-cube "uv" (same as vanilla
CubeListBuilder): for a cube of size (w,h,d) at texture offset (u,v) the side faces sit in
one strip at y = v+d .. v+d+h, in the order

    (u,        v+d) -X   (west)
    (u+d,      v+d) -Z   (north)
    (u+d+w,    v+d) +X   (east)
    (u+2d+w,   v+d) +Z   (south)

So cropping that strip of the head cube and looking at it says which axis carries the eyes.
`yan` / `zuowang` are the control: those models are verified in game, and their hair names
say so too (hairBangs = -Z = front). If the fighting angel's face lands on the other
column, its front is +Z and the renderer/animation must follow that.

Output: docs/previews/face_uv_<model>.png    (head strip, 8x zoom, columns labelled)
ASCII only.
"""
import json
import os

from PIL import Image, ImageDraw

LABELS = ["-X", "-Z", "+X", "+Z"]
MODELS = {
    "fightingangel": ("geo/entity/fightingangel.geo.json", "textures/entity/fightingangel_bedrock.png"),
    "angel": ("geo/entity/angel.geo.json", "textures/entity/angel_bedrock.png"),
    "yan": ("geo/entity/yan.geo.json", "textures/entity/yan_bedrock.png"),
    "zuowang": ("geo/entity/zuowang.geo.json", "textures/entity/zuowang_bedrock.png"),
}
ROOT = os.path.join("src", "main", "resources", "assets", "tnc")
ZOOM = 8


def head_cube(geo_path):
    with open(geo_path, encoding="utf-8") as f:
        geo = json.load(f)["minecraft:geometry"][0]
    for b in geo["bones"]:
        if b["name"] == "head":
            for c in b.get("cubes", []):
                if isinstance(c.get("uv"), list):
                    return c
    return None


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    os.makedirs(os.path.join("docs", "previews"), exist_ok=True)
    for name, (g, tex) in MODELS.items():
        gp, tp = os.path.join(ROOT, g), os.path.join(ROOT, tex)
        if not (os.path.exists(gp) and os.path.exists(tp)):
            print("skip %s (missing geo or texture)" % name)
            continue
        c = head_cube(gp)
        if c is None:
            print("skip %s (head has no box uv)" % name)
            continue
        u, v = c["uv"]
        w, h, d = [int(round(x)) for x in c["size"]]
        img = Image.open(tp).convert("RGBA")
        # the whole head strip: 2*d + 2*w wide, d + h tall
        box = (u, v, u + 2 * d + 2 * w, v + d + h)
        crop = img.crop(box)
        big = crop.resize((crop.width * ZOOM, crop.height * ZOOM), Image.NEAREST)
        sheet = Image.new("RGBA", (big.width, big.height + 22), (24, 24, 32, 255))
        sheet.paste(big, (0, 22))
        d0 = ImageDraw.Draw(sheet)
        # column x offsets inside the strip: -X at 0, -Z at d, +X at d+w, +Z at 2d+w
        for i, (off, wdt, lab) in enumerate([(0, d, LABELS[0]), (d, w, LABELS[1]),
                                             (d + w, d, LABELS[2]), (2 * d + w, w, LABELS[3])]):
            x0 = off * ZOOM
            x1 = (off + wdt) * ZOOM
            d0.rectangle([x0, 0, max(x0 + 1, x1 - 1), 20], fill=(90, 90, 100))
            d0.text((x0 + 3, 5), lab, fill=(255, 240, 160))
            d0.line([x0, 22, x0, sheet.height], fill=(255, 90, 90))
        out = os.path.join("docs", "previews", "face_uv_%s.png" % name)
        sheet.save(out)
        # second sheet: only the 4 side faces of row v+d .. v+d+h, each blown up separately
        row = img.crop((u, v + d, u + 2 * d + 2 * w, v + d + h))
        faces = [row.crop((0, 0, d, h)), row.crop((d, 0, d + w, h)),
                 row.crop((d + w, 0, d + w + d, h)), row.crop((d + w + d, 0, 2 * d + 2 * w, h))]
        z = 20
        cell = (w * z, h * z)
        sheet2 = Image.new("RGBA", (cell[0] * 4, cell[1] + 22), (24, 24, 32, 255))
        d1 = ImageDraw.Draw(sheet2)
        for i, f in enumerate(faces):
            sheet2.paste(f.resize((f.width * z, f.height * z), Image.NEAREST), (i * cell[0], 22))
            d1.rectangle([i * cell[0], 0, (i + 1) * cell[0] - 2, 20], fill=(90, 90, 100))
            d1.text((i * cell[0] + 4, 5), LABELS[i], fill=(255, 240, 160))
            d1.rectangle([i * cell[0], 22, (i + 1) * cell[0] - 1, sheet2.height - 1],
                         outline=(255, 90, 90))
        out2 = os.path.join("docs", "previews", "face_sides_%s.png" % name)
        sheet2.save(out2)
        print("%-14s uv=%s size=%s -> %s" % (name, (u, v), (w, h, d), out2))


if __name__ == "__main__":
    main()
