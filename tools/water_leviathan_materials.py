"""Deterministic, face-aware pixel materials for the Leviathan's existing UV atlas."""
import hashlib
import math
import random

from PIL import Image, ImageDraw


def face_rects(cube):
    u, v = cube["uv"]
    w, h, d = map(int, cube["size"])
    return {
        "up": (u+d, v, w, d), "down": (u+d+w, v, w, d),
        "west": (u, v+d, d, h), "north": (u+d, v+d, w, h),
        "east": (u+d+w, v+d, d, h), "south": (u+2*d+w, v+d, w, h),
    }


def mix(a, b, t):
    return tuple(int(x*(1-t)+y*t) for x, y in zip(a[:3], b[:3]))


def material_face(material, name, face, w, h):
    seed = int.from_bytes(hashlib.sha256(f"{name}/{face}".encode()).digest()[:8], "big")
    rng = random.Random(seed)
    image = Image.new("RGBA", (w, h))
    glow = Image.new("RGBA", (w, h))
    pixels = image.load()
    palettes = {
        "abyss": ((5, 17, 35), (24, 91, 115)),
        "scale": ((8, 35, 66), (40, 137, 153)),
        "blue": ((10, 37, 83), (47, 156, 179)),
        "belly": ((47, 94, 112), (168, 209, 199)),
        "gold": ((67, 47, 28), (226, 185, 89)),
        "bone": ((86, 96, 104), (232, 222, 185)),
        "mouth": ((27, 10, 27), (128, 49, 65)),
        "blue_glow": ((15, 79, 132), (158, 246, 243)),
        "gold_glow": ((143, 87, 25), (255, 242, 161)),
    }
    low, high = palettes[material]
    for y in range(h):
        for x in range(w):
            # Broad color transitions remain readable from several blocks away.
            t = 0.30 + 0.37*(1-y/max(1, h-1)) + 0.09*math.sin(x*0.24+y*0.11)
            if face == "up": t += 0.13
            if face == "down": t -= 0.12
            if material == "bone": t = 0.25+0.67*(1-y/max(1, h-1))
            c = mix(low, high, max(0.08, min(0.97, t)))
            noise = rng.randint(-3, 3)
            pixels[x, y] = tuple(max(0, min(255, p+noise)) for p in c)+(255,)
    draw = ImageDraw.Draw(image)
    gd = ImageDraw.Draw(glow)

    if material in {"abyss", "scale", "belly"}:
        cell = 8 if material == "abyss" else 6
        row = max(3, cell-2)
        for sy in range(-row, h+row, row):
            offset = (sy//row % 2)*(cell//2)
            for sx in range(-cell, w+cell, cell):
                x = sx+offset
                shade = rng.choice([(20, 81, 107), (22, 104, 121), (29, 71, 113), (33, 122, 130)])
                if material == "belly": shade = rng.choice([(113, 169, 174), (141, 187, 184), (81, 140, 157)])
                draw.line([(x, sy), (x+cell//2, sy+row), (x+cell, sy)], fill=(3, 24, 43), width=1)
                draw.line([(x+1, sy+1), (x+cell//2, sy+row-2), (x+cell-1, sy+1)], fill=shade, width=1)
                if rng.random() < 0.28:
                    draw.point((x+cell//2, sy+row-2), fill=(85, 169, 178))
        # Sparse bioluminescent pores, deliberately less bright than the holy engravings.
        if material != "belly":
            for _ in range(max(1, w*h//140)):
                x, y = rng.randrange(w), rng.randrange(h)
                draw.point((x, y), fill=(54, 175, 192))
                gd.point((x, y), fill=(32, 103, 142, 115))

    elif material == "gold":
        draw.rectangle((0, 0, w-1, h-1), outline=(66, 43, 24))
        if min(w, h) > 3:
            draw.rectangle((1, 1, w-2, h-2), outline=(240, 203, 117))
            draw.line((2, h-3, w-3, h-3), fill=(92, 62, 29))
        # Inset lapis panels, turquoise oxidation in the recesses, gold engraved waves.
        if w >= 8 and h >= 7:
            draw.rectangle((3, 3, w-4, h-4), fill=(13, 57, 82), outline=(80, 133, 133))
            for cx in range(5, w-3, 10):
                cy = h//2
                draw.line([(cx-2, cy), (cx, max(3, cy-3)), (cx+2, cy),
                           (cx, min(h-4, cy+3)), (cx-2, cy)], fill=(218, 174, 80))
                draw.point((cx, cy), fill=(111, 223, 223))
                gd.point((cx, cy), fill=(55, 145, 164, 145))
        for x, y in [(1, 1), (w-2, 1), (1, h-2), (w-2, h-2)]:
            draw.point((x, y), fill=(255, 225, 150))
        for _ in range(max(1, w*h//90)):
            x, y = rng.randrange(w), rng.randrange(h)
            draw.line((x, y, min(w-1, x+2), min(h-1, y+1)), fill=(134, 110, 60))

    elif material == "blue":
        # Fin membranes have radial veins, thin gold ribs and dark scalloped segments.
        for x in range(-h, w+h, 7):
            draw.line((x, 0, x+h//2, h), fill=(15, 64, 107), width=2)
            draw.line((x+2, 0, x+h//2+2, h), fill=(65, 172, 186))
        draw.line((0, 0, w-1, 0), fill=(201, 172, 93))
        draw.line((0, h-1, w-1, h-1), fill=(21, 43, 79))

    elif material == "bone":
        for x in range(1, w, 3):
            draw.line((x, 0, max(0, x-2), h-1), fill=(156, 165, 151))
        for y in range(3, h, 6):
            draw.line((0, y, w-1, min(h-1, y+1)), fill=(118, 131, 127))
        draw.line((0, 0, w-1, 0), fill=(247, 235, 197))

    elif material == "mouth":
        for y in range(2, h, 4):
            draw.line((0, y, w-1, max(0, y-1)), fill=(159, 65, 80))
        draw.line((0, h-1, w-1, h-1), fill=(35, 14, 31))

    else:
        # Faceted inlays have a bright core and a darker mounting, rather than flat neon.
        draw.rectangle((0, 0, w-1, h-1), outline=low)
        if w > 2 and h > 2:
            draw.line((1, 1, w-2, 1), fill=high)
            draw.line((w//2, 1, w//2, h-2), fill=high)
        glow = image.copy()
        if "eye" in name:
            draw.line((w//2, 1, w//2, max(1, h-2)), fill=(3, 17, 28), width=1)
            ImageDraw.Draw(glow).line((w//2, 1, w//2, max(1, h-2)), fill=(0, 0, 0, 0))

    # A thin edge shadow separates adjacent faces even in full daylight.
    if material not in {"blue_glow", "gold_glow", "gold"}:
        draw.line((0, h-1, w-1, h-1), fill=mix(low, high, 0.18))
    return image, glow


def paint_atlas(cubes, size):
    atlas = Image.new("RGBA", (size, size))
    emissive = Image.new("RGBA", (size, size))
    for cube in cubes:
        for face, (x, y, w, h) in face_rects(cube).items():
            image, glow = material_face(cube["material"], cube["name"], face, w, h)
            atlas.paste(image, (x, y))
            emissive.paste(glow, (x, y))
    return atlas, emissive
