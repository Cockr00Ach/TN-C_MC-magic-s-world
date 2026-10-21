"""Generate the non-humanoid ink-realm boss: 墨渊螭 / Ink Abyss Drake.

Outputs a detailed GeckoLib Bedrock model, grayscale texture, idle animation and
an authored preview.  Run from any directory; all paths resolve from this file.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
GEO = ASSETS / "geo/entity/ink_abyss_drake.geo.json"
ANIM = ASSETS / "animations/entity/ink_abyss_drake.animation.json"
TEX = ASSETS / "textures/entity/ink_abyss_drake.png"
PREVIEW = ROOT / "docs/previews/ink_abyss_drake_preview.png"

INK = [0, 0]
CHARCOAL = [64, 0]
ASH = [96, 64]
PAPER = [128, 0]
CRACK = [160, 64]
EYE = [192, 64]
DRY = [0, 128]
RING = [128, 128]


def cube(origin, size, uv, rotation=None, pivot=None, inflate=None):
    value = {"origin": list(origin), "size": list(size), "uv": list(uv)}
    if rotation is not None:
        value["rotation"] = list(rotation)
        value["pivot"] = list(pivot or origin)
    if inflate is not None:
        value["inflate"] = inflate
    return value


def bone(name, parent="root", pivot=(0, 0, 0), cubes=None):
    value = {"name": name, "pivot": list(pivot)}
    if parent:
        value["parent"] = parent
    if cubes:
        value["cubes"] = cubes
    return value


def build_bones():
    b = [bone("root", parent=None)]
    b.append(bone("torso", pivot=(0, 24, 0), cubes=[
        cube((-12, 15, -14), (24, 18, 28), INK),
        cube((-10, 13, -10), (20, 5, 21), CHARCOAL),
        cube((-9, 30, -9), (18, 5, 18), DRY),
        cube((-13, 19, -8), (2, 9, 17), ASH),
        cube((11, 19, -8), (2, 9, 17), ASH),
    ]))
    b.append(bone("neck", "torso", (0, 25, -12), [
        cube((-10, 17, -29), (20, 16, 18), CHARCOAL),
        cube((-8, 30, -27), (16, 5, 13), DRY),
    ]))
    b.append(bone("head", "neck", (0, 27, -27), [
        cube((-13, 19, -44), (26, 17, 19), INK),
        cube((-11, 16, -52), (22, 12, 12), CHARCOAL),
        cube((-14, 27, -40), (28, 6, 12), DRY, (0, 0, 5), (0, 29, -34)),
    ]))
    b.append(bone("paper_mask", "head", (0, 28, -44), [
        cube((-9, 26, -53), (8, 8, 2), PAPER, (0, -8, -4), (-5, 29, -52)),
        cube((1, 26, -53), (8, 8, 2), PAPER, (0, 8, 4), (5, 29, -52)),
        cube((-5, 20, -54), (10, 6, 2), PAPER),
        cube((-2, 24, -55), (4, 9, 1), CRACK),
    ]))
    b.append(bone("lower_jaw", "head", (0, 20, -42), [
        cube((-10, 14, -51), (20, 5, 13), INK),
        cube((-8, 18, -52), (3, 3, 2), PAPER),
        cube((-3, 18, -52), (3, 3, 2), PAPER),
        cube((2, 18, -52), (3, 3, 2), PAPER),
        cube((7, 18, -52), (3, 3, 2), PAPER),
    ]))
    b.append(bone("eyes", "head", (0, 29, -52), [
        cube((-9, 28, -54), (6, 2, 1), EYE, (0, 0, -10), (-6, 29, -54)),
        cube((3, 28, -54), (6, 2, 1), EYE, (0, 0, 10), (6, 29, -54)),
    ]))

    # Branching antler-horns, deliberately asymmetric like two brush characters.
    b += [
        bone("horn_l_base", "head", (-8, 34, -36), [cube((-12, 33, -39), (5, 12, 5), PAPER, (0, 0, -18), (-9, 34, -36))]),
        bone("horn_l_tip", "horn_l_base", (-10, 43, -36), [
            cube((-18, 41, -38), (10, 4, 4), PAPER, (0, 0, 22), (-10, 43, -36)),
            cube((-18, 43, -37), (4, 10, 3), PAPER, (0, 0, -18), (-16, 44, -36)),
        ]),
        bone("horn_r_base", "head", (8, 34, -36), [cube((7, 33, -39), (5, 13, 5), ASH, (0, 0, 16), (9, 34, -36))]),
        bone("horn_r_tip", "horn_r_base", (10, 44, -36), [
            cube((8, 43, -38), (11, 4, 4), ASH, (0, 0, -24), (10, 44, -36)),
            cube((15, 45, -37), (4, 9, 3), PAPER, (0, 0, 20), (16, 45, -36)),
        ]),
    ]

    # Long whiskers and beard are thin articulated brush hairs, not a human face.
    b.append(bone("whisker_l", "head", (-8, 21, -50), [
        cube((-30, 20, -53), (23, 1, 1), DRY, (0, 14, -8), (-8, 21, -51)),
        cube((-27, 17, -52), (18, 1, 1), DRY, (0, -10, 10), (-9, 20, -51)),
    ]))
    b.append(bone("whisker_r", "head", (8, 21, -50), [
        cube((7, 20, -53), (23, 1, 1), DRY, (0, -14, 8), (8, 21, -51)),
        cube((9, 17, -52), (18, 1, 1), DRY, (0, 10, -10), (9, 20, -51)),
    ]))
    b.append(bone("beard", "lower_jaw", (0, 15, -46), [
        cube((-4, 3, -49), (3, 13, 3), DRY, (12, 0, -7), (-2, 15, -47)),
        cube((1, 1, -48), (3, 15, 3), DRY, (-10, 0, 6), (2, 15, -47)),
    ]))

    # Four broad fins are layered like loaded-brush strokes.
    for side, sx, angle in (("l", -1, -18), ("r", 1, 18)):
        b.append(bone(f"fore_fin_{side}", "torso", (sx * 10, 26, -7), [
            cube((sx * 10 - (30 if sx < 0 else 0), 22, -10), (30, 3, 13), INK,
                 (0, 0, angle), (sx * 10, 24, -6)),
            cube((sx * 11 - (23 if sx < 0 else 0), 25, -8), (23, 2, 9), ASH,
                 (0, 0, angle * 1.35), (sx * 10, 26, -5)),
        ]))
        b.append(bone(f"rear_fin_{side}", "torso", (sx * 10, 22, 8), [
            cube((sx * 10 - (24 if sx < 0 else 0), 17, 5), (24, 3, 14), CHARCOAL,
                 (0, 0, angle * 0.8), (sx * 10, 21, 8)),
            cube((sx * 10 - (18 if sx < 0 else 0), 20, 8), (18, 2, 10), DRY,
                 (0, 0, angle * 1.2), (sx * 10, 22, 10)),
        ]))

    # Dorsal brush tips: alternating paper-white and black produces dry-brush rhythm.
    for i, z in enumerate(range(-21, 21, 7)):
        h = 13 - abs(z) // 7
        b.append(bone(f"spine_{i}", "torso" if z >= -14 else "neck", (0, 33, z), [
            cube((-2, 32, z - 2), (4, h, 5), PAPER if i % 3 == 0 else DRY,
                 (8 if i % 2 else -8, 0, 0), (0, 33, z)),
        ]))

    # Fully articulated serpentine tail; three tips dissolve into independent ink wisps.
    tail_specs = [
        ("tail_1", "torso", (0, 23, 12), (-10, 15, 11), (20, 16, 18), CHARCOAL),
        ("tail_2", "tail_1", (0, 23, 27), (-8, 16, 26), (16, 14, 17), INK),
        ("tail_3", "tail_2", (0, 23, 42), (-6, 17, 41), (12, 12, 16), CHARCOAL),
        ("tail_4", "tail_3", (0, 23, 56), (-5, 18, 55), (10, 10, 15), INK),
        ("tail_5", "tail_4", (0, 23, 69), (-4, 19, 68), (8, 8, 14), DRY),
    ]
    for name, parent, pivot, origin, size, uv in tail_specs:
        b.append(bone(name, parent, pivot, [cube(origin, size, uv)]))
    b += [
        bone("tail_wisp_l", "tail_5", (-2, 23, 80), [cube((-9, 20, 79), (8, 4, 25), DRY, (0, -18, -8), (-2, 23, 80))]),
        bone("tail_wisp_c", "tail_5", (0, 23, 81), [cube((-2, 20, 80), (4, 5, 30), DRY, (7, 0, 0), (0, 23, 81))]),
        bone("tail_wisp_r", "tail_5", (2, 23, 80), [cube((1, 20, 79), (8, 4, 25), DRY, (0, 18, 8), (2, 23, 80))]),
    ]

    # Broken vertical enso halo, built from separate strokes so the circle has gaps.
    ring_cubes = []
    radius = 24
    for i in range(14):
        if i in (2, 9):
            continue
        angle = i * 360.0 / 14.0
        rad = math.radians(angle)
        cx = math.cos(rad) * radius
        cy = 25 + math.sin(rad) * radius
        ring_cubes.append(cube((cx - 5, cy - 1.5, 17), (10, 3, 3), RING,
                               (0, 0, angle + 90), (cx, cy, 18)))
    b.append(bone("broken_enso", "root", (0, 25, 18), ring_cubes))

    for i, (x, y, z, s) in enumerate(((-25, 12, 9, 3), (24, 34, 5, 4), (-18, 42, 20, 2),
                                       (18, 5, 25, 3), (7, 52, 13, 2), (-30, 28, 23, 2))):
        b.append(bone(f"ink_drop_{i}", "root", (x, y, z), [cube((x-s/2, y-s/2, z-s/2), (s, s, s), INK)]))
    return b


def write_geo():
    data = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tnc.ink_abyss_drake",
                "texture_width": 256,
                "texture_height": 256,
                "visible_bounds_width": 10,
                "visible_bounds_height": 7,
                "visible_bounds_offset": [0, 2.5, 1.5],
            },
            "bones": build_bones(),
        }],
    }
    GEO.parent.mkdir(parents=True, exist_ok=True)
    GEO.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def keyframes(values):
    return {"0.0": values[0], "2.0": values[1], "4.0": values[0]}


def write_animation():
    bones = {
        "root": {"position": keyframes([[0, 0, 0], [0, 1.4, 0]])},
        "torso": {"rotation": keyframes([[0, -2, 0], [0, 2, 0]])},
        "neck": {"rotation": keyframes([[1, -4, 0], [-1, 4, 0]])},
        "head": {"rotation": keyframes([[0, 3, 0], [2, -3, 0]])},
        "lower_jaw": {"rotation": keyframes([[0, 0, 0], [-7, 0, 0]])},
        "broken_enso": {"rotation": {"0.0": [0, 0, 0], "4.0": [0, 0, 360]}},
        "fore_fin_l": {"rotation": keyframes([[0, 0, -4], [0, 0, 8]])},
        "fore_fin_r": {"rotation": keyframes([[0, 0, 4], [0, 0, -8]])},
        "rear_fin_l": {"rotation": keyframes([[0, 0, -3], [0, 0, 6]])},
        "rear_fin_r": {"rotation": keyframes([[0, 0, 3], [0, 0, -6]])},
        "whisker_l": {"rotation": keyframes([[0, -4, -2], [0, 5, 3]])},
        "whisker_r": {"rotation": keyframes([[0, 4, 2], [0, -5, -3]])},
    }
    for i in range(1, 6):
        amplitude = 3 + i * 2
        bones[f"tail_{i}"] = {"rotation": keyframes([[0, -amplitude, 0], [0, amplitude, 0]])}
    for name, sign in (("tail_wisp_l", -1), ("tail_wisp_c", 1), ("tail_wisp_r", 1)):
        bones[name] = {"rotation": keyframes([[sign * 4, sign * 7, 0], [-sign * 5, -sign * 8, 0]])}
    for i in range(6):
        bones[f"ink_drop_{i}"] = {"position": keyframes([[0, -1.2 + i * 0.1, 0], [0, 1.2 - i * 0.1, 0]])}
    data = {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 4.0, "bones": bones}
    }}
    ANIM.parent.mkdir(parents=True, exist_ok=True)
    ANIM.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_texture():
    rng = random.Random(20261018)
    image = Image.new("RGBA", (256, 256), (238, 237, 229, 255))
    px = image.load()
    palettes = [
        ((8, 8, 8), (40, 40, 40)), ((41, 41, 41), (86, 86, 86)),
        ((111, 111, 111), (163, 163, 163)), ((204, 204, 204), (244, 244, 244)),
    ]
    for y in range(256):
        band = min(3, y // 64)
        lo, hi = palettes[band]
        for x in range(256):
            fibre = int(9 * math.sin(x * 0.33 + y * 0.07)) + rng.randint(-10, 10)
            t = ((x % 64) / 63.0) * 0.45 + 0.25
            c = tuple(max(0, min(255, int(lo[i] * (1-t) + hi[i] * t) + fibre)) for i in range(3))
            alpha = 255
            if y >= 128 and ((x * 13 + y * 7 + rng.randrange(11)) % 37 < 4):
                alpha = 0  # dry-brush holes on wisp/ring regions
            px[x, y] = (*c, alpha)
    draw = ImageDraw.Draw(image)
    # White mask field, charcoal cracks and luminous paper-white beast eyes.
    draw.rectangle((128, 0, 191, 63), fill=(222, 222, 222, 255))
    for i in range(36):
        x = rng.randint(130, 190); y = rng.randint(2, 61)
        draw.line((x, y, x + rng.randint(-8, 8), y + rng.randint(4, 15)), fill=(75, 76, 76, 255), width=1)
    draw.rectangle((192, 64, 255, 95), fill=(247, 247, 247, 255))
    for y in range(64, 96, 5):
        draw.line((192, y, 255, y), fill=(182, 182, 178, 255), width=1)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    image.save(TEX)


def write_preview():
    w, h = 1600, 900
    img = Image.new("RGB", (w, h), (226, 224, 214))
    wash = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wash)
    rng = random.Random(20261018)
    for _ in range(26):
        x = rng.randint(80, w-80); y = rng.randint(80, h-80); r = rng.randint(30, 150)
        gray = rng.randint(25, 110); alpha = rng.randint(8, 26)
        wd.ellipse((x-r, y-r//2, x+r, y+r//2), fill=(gray, gray, gray, alpha))
    wash = wash.filter(ImageFilter.GaussianBlur(32))
    img = Image.alpha_composite(img.convert("RGBA"), wash)
    d = ImageDraw.Draw(img)
    # Enso behind the creature.
    d.arc((335, 88, 1210, 820), 18, 332, fill=(45, 47, 47, 175), width=31)
    d.arc((365, 112, 1182, 800), 22, 205, fill=(117, 118, 114, 120), width=9)
    # Serpentine silhouette, broad head left and tapering dry-brush tail right.
    spine = [(400,455),(500,402),(620,390),(750,430),(875,470),(1010,450),(1135,390),(1275,430),(1400,520)]
    for width, color in ((128,(20,22,23,255)), (92,(60,62,62,255)), (56,(16,17,18,255))):
        d.line(spine, fill=color, width=width, joint="curve")
    # Head, jaws and fractured paper mask.
    d.polygon([(225,365),(405,335),(500,410),(452,520),(270,535),(175,470)], fill=(14,15,16,255))
    d.polygon([(190,475),(420,470),(375,560),(220,545)], fill=(45,46,47,255))
    d.polygon([(240,372),(340,350),(382,414),(315,455),(220,435)], fill=(221,220,210,255))
    d.line((294,367,315,438,280,420,340,399), fill=(76,77,76,255), width=6)
    d.polygon([(238,416),(282,405),(267,427)], fill=(250,250,244,255))
    d.polygon([(334,397),(374,405),(342,420)], fill=(250,250,244,255))
    # Antlers and whiskers.
    d.line((255,370,205,285,135,250,170,190), fill=(226,225,214,255), width=19, joint="curve")
    d.line((348,355,405,265,472,225,440,160), fill=(139,140,137,255), width=17, joint="curve")
    d.line((210,461,78,420,45,375), fill=(25,26,27,230), width=6)
    d.line((214,480,60,500,20,550), fill=(64,65,64,210), width=4)
    # Layered brush fins.
    for poly, color in [
        ([(520,408),(420,215),(650,370)],(16,17,18,255)),
        ([(570,430),(455,650),(700,475)],(52,54,54,255)),
        ([(770,447),(690,240),(880,455)],(27,29,29,255)),
        ([(850,470),(790,685),(955,493)],(92,93,91,255)),
    ]:
        d.polygon(poly, fill=color)
    # Dorsal white brush tips.
    for i, x in enumerate(range(490, 940, 62)):
        top = 275 + (i % 3) * 24
        d.polygon([(x,390),(x+24,top),(x+45,405)], fill=(218,217,208,245))
    # Tail flying-white splits and floating droplets.
    d.line((1320,470,1510,390), fill=(15,16,17,230), width=24)
    d.line((1350,495,1535,510), fill=(55,56,56,190), width=15)
    d.line((1325,530,1490,620), fill=(112,112,108,155), width=10)
    for x, y, r in ((1280,280,13),(1385,315,8),(1210,650,11),(1465,675,7),(1040,250,9)):
        d.ellipse((x-r,y-r,x+r,y+r), fill=(22,23,24,220))
    d.text((70, 65), "INK ABYSS DRAKE  /  墨渊螭", fill=(18, 19, 20, 255), stroke_width=1)
    d.text((72, 105), "non-humanoid boss model · fractured inkstone head · brush fins · dry-ink tail · broken enso", fill=(70, 71, 70, 255))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(PREVIEW, quality=95)


def main():
    write_geo()
    write_animation()
    write_texture()
    write_preview()
    geo = json.loads(GEO.read_text(encoding="utf-8"))
    bones = geo["minecraft:geometry"][0]["bones"]
    cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"generated {GEO.relative_to(ROOT)} ({len(bones)} bones, {cubes} cubes)")
    print(f"generated {ANIM.relative_to(ROOT)}")
    print(f"generated {TEX.relative_to(ROOT)}")
    print(f"generated {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
