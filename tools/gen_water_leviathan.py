"""Generate the Hallowed Abyss Leviathan GeckoLib model, textures, animations and preview."""
from __future__ import annotations

import json
from pathlib import Path

from water_leviathan_materials import paint_atlas

ROOT = Path(__file__).resolve().parents[1]
GEO = ROOT / "src/main/resources/assets/tnc/geo/entity/water_leviathan.geo.json"
ANIM = ROOT / "src/main/resources/assets/tnc/animations/entity/water_leviathan.animation.json"
TEX = ROOT / "src/main/resources/assets/tnc/textures/entity/water_leviathan_bedrock.png"
GLOW = ROOT / "src/main/resources/assets/tnc/textures/entity/water_leviathan_emissive.png"
PREVIEW = ROOT / "docs/previews/water_leviathan_preview.png"
ATLAS = 1024

bones: list[dict] = []
cubes: list[dict] = []


def bone(name: str, parent: str | None, pivot, rotation=None):
    b = {"name": name, "pivot": pivot, "cubes": []}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    bones.append(b)
    return b


def box(b, name: str, origin, size, material: str, inflate=0.0):
    c = {"name": name, "origin": origin, "size": size, "material": material}
    if inflate:
        c["inflate"] = inflate
    b["cubes"].append(c)
    cubes.append(c)


root = bone("root", None, [0, 0, 0])
body = bone("body", "root", [0, 34, 0])
box(body, "torso_core", [-28, 18, -34], [56, 35, 72], "abyss")
box(body, "torso_scale", [-32, 24, -30], [64, 26, 58], "scale")
box(body, "chest_mass", [-35, 20, -43], [70, 36, 35], "abyss")
box(body, "belly_keel", [-22, 12, -30], [44, 12, 60], "belly")
for z in (-29, -14, 1, 16, 29):
    box(body, f"spine_plate_{z}", [-20, 50, z], [40, 5, 10], "gold")
    box(body, f"spine_rune_{z}", [-11, 55, z + 2], [22, 2, 6], "blue_glow")
for x in (-31, 25):
    box(body, f"flank_armor_{x}", [x, 31, -26], [6, 17, 50], "gold")

neck = bone("neck", "body", [0, 37, -37], [-8, 0, 0])
box(neck, "neck_core", [-29, 20, -58], [58, 34, 28], "scale")
box(neck, "throat", [-22, 12, -58], [44, 15, 28], "belly")
box(neck, "neck_collar", [-34, 40, -51], [68, 7, 14], "gold")
box(neck, "neck_rune", [-18, 47, -48], [36, 2, 8], "gold_glow")

head = bone("head", "neck", [0, 37, -56])
box(head, "skull", [-27, 22, -82], [54, 32, 36], "abyss")
box(head, "brow", [-31, 39, -84], [62, 12, 25], "scale")
box(head, "snout_rear", [-24, 23, -91], [48, 22, 16], "abyss")
box(head, "snout_mid", [-21, 23, -103], [42, 21, 12], "scale")
box(head, "snout_tip", [-18, 24, -109], [36, 19, 6], "abyss")
box(head, "nose_armor", [-18, 42, -104], [36, 6, 30], "gold")
box(head, "forehead_gem", [-7, 49, -82], [14, 6, 16], "blue_glow", 0.2)
box(head, "eye_left", [14, 36, -106], [7, 4, 2], "gold_glow", 0.2)
box(head, "eye_right", [-21, 36, -106], [7, 4, 2], "gold_glow", 0.2)
for side in (-1, 1):
    sx = -35 if side < 0 else 28
    box(head, f"cheek_guard_{side}", [sx, 27, -83], [7, 19, 30], "gold")
    box(head, f"gill_glow_{side}", [sx + (1 if side < 0 else 2), 31, -61], [4, 12, 3], "blue_glow")

jaw = bone("jaw", "head", [0, 28, -77])
box(jaw, "lower_jaw", [-25, 9, -103], [50, 8, 34], "scale")
box(jaw, "inner_gums", [-22, 17, -101], [44, 2, 29], "mouth")
box(jaw, "tongue", [-9, 19, -96], [18, 2, 17], "mouth")
box(jaw, "jaw_armor", [-28, 6, -94], [56, 5, 22], "gold")
for i, x in enumerate(range(-20, 21, 8)):
    box(jaw, f"lower_tooth_{i}", [x, 18, -102], [4, 7, 5], "bone")
    box(head, f"upper_tooth_{i}", [x, 16, -101], [4, 9, 5], "bone")

for side in (-1, 1):
    horn = bone(f"horn_{'left' if side > 0 else 'right'}", "head", [side * 23, 49, -68], [0, 0, side * -18])
    base_x = 20 if side > 0 else -31
    box(horn, f"horn_base_{side}", [base_x, 47, -72], [11, 16, 12], "gold")
    box(horn, f"horn_mid_{side}", [base_x + side * 5, 59, -69], [8, 18, 8], "bone")
    box(horn, f"horn_tip_{side}", [base_x + side * 9, 74, -67], [5, 15, 5], "gold_glow")

for side in (-1, 1):
    x = -31 if side < 0 else 31
    fin = bone(f"fin_front_{side}", "body", [x, 34, -22], [0, side * 8, side * -18])
    box(fin, f"front_fin_arm_{side}", [x - (22 if side < 0 else 0), 24, -30], [22, 12, 17], "scale")
    box(fin, f"front_fin_wing_{side}", [x - (67 if side < 0 else -22), 18, -35], [45, 6, 28], "blue")
    for stripe in range(3):
        box(fin, f"front_fin_gold_{side}_{stripe}",
            [x - (33 + stripe * 12 if side < 0 else -25 - stripe * 12), 23, -31 + stripe * 5],
            [8, 2, 18], "gold_glow")
    rear = bone(f"fin_rear_{side}", "body", [x, 31, 22], [0, side * -8, side * -13])
    box(rear, f"rear_fin_arm_{side}", [x - (18 if side < 0 else 0), 22, 15], [18, 11, 15], "scale")
    box(rear, f"rear_fin_wing_{side}", [x - (56 if side < 0 else -18), 17, 12], [38, 6, 26], "blue")

tail1 = bone("tail1", "body", [0, 35, 34])
box(tail1, "tail_base", [-23, 21, 32], [46, 28, 40], "scale")
box(tail1, "tail_base_armor", [-17, 46, 37], [34, 5, 27], "gold")
tail2 = bone("tail2", "tail1", [0, 34, 68], [0, 7, 0])
box(tail2, "tail_mid", [-17, 23, 65], [34, 22, 45], "abyss")
box(tail2, "tail_mid_rune", [-7, 44, 73], [14, 3, 28], "blue_glow")
tail3 = bone("tail3", "tail2", [0, 34, 106], [0, -7, 0])
box(tail3, "tail_tip", [-11, 27, 103], [22, 14, 48], "scale")
box(tail3, "tail_blade_left", [-38, 27, 132], [30, 5, 29], "blue")
box(tail3, "tail_blade_right", [8, 27, 132], [30, 5, 29], "blue")
box(tail3, "tail_blade_gold", [-4, 39, 119], [8, 4, 36], "gold_glow")

# Raised, swept-back dorsal spears and overlapping flank scales break the box silhouette.
for i, z in enumerate(range(-32, 104, 17)):
    parent = body if z < 34 else (tail1 if z < 68 else tail2)
    height = 24 - i * 2
    spike = bone(f"dorsal_spear_{i}", parent["name"], [0, 49 - i, z], [26, 0, 0])
    box(spike, f"dorsal_gold_{i}", [-5, 49-i, z], [10, height, 7], "gold")
    box(spike, f"dorsal_tip_{i}", [-2, 49-i+height, z+1], [4, 8, 4], "blue_glow")
for side in (-1, 1):
    for i, z in enumerate(range(-34, 29, 12)):
        plate = bone(f"flank_scale_{side}_{i}", "body", [side*31, 37, z], [0, side*-18, side*-12])
        box(plate, f"flank_scale_{side}_{i}", [side*31-4, 29, z], [8, 18, 16], "scale")
        box(plate, f"flank_edge_{side}_{i}", [side*31-4, 46, z], [8, 2, 14], "gold")
    # Side-facing eyes remain visible when looking at the beast's profile.
    box(head, f"profile_eye_{side}", [side*27-(2 if side<0 else 0), 37, -77], [2, 5, 10], "gold_glow")
    for i in range(4):
        z = -92 + i*7
        box(head, f"side_fang_upper_{side}_{i}", [side*23-2, 15, z], [4, 11, 4], "bone")
        box(jaw, f"side_fang_lower_{side}_{i}", [side*23-2, 19, z+3], [4, 10, 4], "bone")
    for i in range(3):
        gill = bone(f"gill_{side}_{i}", "neck", [side*28, 36, -50+i*7], [0, side*-24, 0])
        box(gill, f"gill_plate_{side}_{i}", [side*28-3, 25, -50+i*7], [6, 22, 5], "gold")
        box(gill, f"gill_light_{side}_{i}", [side*28-2, 27, -51+i*7], [4, 17, 2], "blue_glow")

# Individual scutes, sensory ridges and fin rays add readable large-scale details.
for i, z in enumerate(range(-32, 33, 8)):
    box(body, f"ventral_scute_{i}", [-23, 10, z], [46, 3, 6], "belly")
    box(body, f"ventral_keystone_{i}", [-4, 9, z+1], [8, 2, 4], "gold")
for side in (-1, 1):
    # Gold brow sockets frame the slit eyes. Cheek fins make the skull feel predatory.
    brow = bone(f"brow_arch_{side}", "head", [side*18, 42, -103], [0, 0, side*-12])
    box(brow, f"brow_rim_{side}", [side*18-8, 41, -107], [16, 3, 4], "gold")
    box(head, f"nostril_{side}", [side*9-3, 28, -106], [6, 3, 2], "abyss")
    cheek = bone(f"cheek_blade_{side}", "head", [side*29, 33, -64], [18, side*-20, side*-22])
    box(cheek, f"cheek_blade_base_{side}", [side*29-3, 31, -64], [6, 6, 17], "gold")
    box(cheek, f"cheek_blade_end_{side}", [side*29-2, 32, -47], [4, 4, 11], "blue")
    tusk = bone(f"jaw_tusk_{side}", "jaw", [side*23, 18, -98], [0, 0, side*-15])
    box(tusk, f"jaw_tusk_base_{side}", [side*23-3, 17, -99], [6, 12, 6], "bone")
    box(tusk, f"jaw_tusk_tip_{side}", [side*23-2, 29, -98], [4, 6, 4], "bone")
    # Three rows of shoulder tiles form a transition from blue scales to golden gills.
    for row in range(2):
        for i in range(5):
            box(body, f"shoulder_tile_{side}_{row}_{i}",
                [side*35-(2 if side<0 else 0), 23+row*12, -38+i*7],
                [2, 9, 6], "scale" if row else "belly")
    # Thin fin rays sit on the existing animated fin bones; no extra particle entities.
    for front, length, base_z in ((True, 45, -35), (False, 38, 12)):
        fin = next(b for b in bones if b["name"] == f"fin_{'front' if front else 'rear'}_{side}")
        wing_name = f"{'front' if front else 'rear'}_fin_wing_{side}"
        wing = next(c for c in fin["cubes"] if c["name"] == wing_name)
        fin["cubes"].remove(wing)
        cubes.remove(wing)
        # A stepped taper replaces each rectangular paddle with a swept fin outline.
        for strip in range(5):
            width = length//5
            xpos = (53 if front else 49)+strip*width
            xpos = xpos if side>0 else -xpos-width
            depth = 30-strip*4
            zpos = base_z+strip*3
            box(fin, f"fin_membrane_{front}_{side}_{strip}", [xpos, 18 if front else 17, zpos],
                [width, 3, depth], "blue")
            box(fin, f"fin_ray_{front}_{side}_{strip}", [xpos, 21 if front else 20, zpos],
                [width, 1, 2], "gold")
    for i, z in enumerate(range(-28, 30, 12)):
        # Small gemstone settings interrupt the large side armour panels.
        xpos = side*35-(2 if side<0 else 0)
        box(body, f"flank_setting_{side}_{i}", [xpos, 38, z], [2, 7, 7], "gold")
        box(body, f"flank_jewel_{side}_{i}", [xpos+side, 40, z+2], [2, 3, 3], "blue_glow")

for parent, base_y, zs in ((tail1, 45, (40, 52, 64)), (tail2, 42, (76, 90, 104)),
                           (tail3, 39, (115, 130, 144))):
    for z in zs:
        box(parent, f"tail_ridge_mount_{z}", [-7, base_y, z], [14, 3, 6], "gold")
        box(parent, f"tail_ridge_pearl_{z}", [-2, base_y+3, z+1], [4, 3, 4], "blue_glow")

# Pack standard Bedrock box UV footprints.
x = y = row_h = 2
for c in sorted(cubes, key=lambda q: -(q["size"][1] + q["size"][2])):
    w, h, d = (max(1, int(round(v))) for v in c["size"])
    fw, fh = 2 * (d + w) + 2, d + h + 2
    if x + fw >= ATLAS:
        x, y, row_h = 2, y + row_h + 2, 0
    if y + fh >= ATLAS:
        raise RuntimeError("Leviathan texture atlas overflow")
    c["uv"] = [x, y]
    c["footprint"] = (x, y, fw - 2, fh - 2)
    x += fw
    row_h = max(row_h, fh)

texture, glow = paint_atlas(cubes, ATLAS)

for b in bones:
    for c in b["cubes"]:
        c.pop("material")
        c.pop("footprint")

geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.tnc.water_leviathan",
            "texture_width": ATLAS,
            "texture_height": ATLAS,
            "visible_bounds_width": 14,
            "visible_bounds_height": 7,
            "visible_bounds_offset": [0, 3, 2],
        },
        "bones": bones,
    }],
}

animations = {
    "format_version": "1.8.0",
    "animations": {
        "idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"position": {"0": [0, 0, 0], "1.5": [0, 1.3, 0], "3": [0, 0, 0]}},
            "head": {"rotation": {"0": [-2, -3, 0], "1.5": [2, 3, 0], "3": [-2, -3, 0]}},
            "jaw": {"rotation": {"0": [0, 0, 0], "1.5": [5, 0, 0], "3": [0, 0, 0]}},
            "tail1": {"rotation": {"0": [0, -5, 0], "1.5": [0, 5, 0], "3": [0, -5, 0]}},
            "tail2": {"rotation": {"0": [0, 8, 0], "1.5": [0, -8, 0], "3": [0, 8, 0]}},
            "tail3": {"rotation": {"0": [0, 11, 0], "1.5": [0, -11, 0], "3": [0, 11, 0]}},
        }},
        "swim": {"loop": True, "animation_length": 1.25, "bones": {
            "body": {"rotation": {"0": [0, -3, 0], "0.625": [0, 3, 0], "1.25": [0, -3, 0]}},
            "tail1": {"rotation": {"0": [0, -12, 0], "0.625": [0, 12, 0], "1.25": [0, -12, 0]}},
            "tail2": {"rotation": {"0": [0, 20, 0], "0.625": [0, -20, 0], "1.25": [0, 20, 0]}},
            "tail3": {"rotation": {"0": [0, 28, 0], "0.625": [0, -28, 0], "1.25": [0, 28, 0]}},
            "fin_front_-1": {"rotation": {"0": [0, 0, -16], "0.625": [0, 0, 8], "1.25": [0, 0, -16]}},
            "fin_front_1": {"rotation": {"0": [0, 0, 16], "0.625": [0, 0, -8], "1.25": [0, 0, 16]}},
        }},
        "bite": {"animation_length": 0.65, "bones": {
            "head": {"rotation": {"0": [0, 0, 0], "0.22": [-16, 0, 0], "0.48": [10, 0, 0], "0.65": [0, 0, 0]}},
            "jaw": {"rotation": {"0": [0, 0, 0], "0.22": [38, 0, 0], "0.48": [-5, 0, 0], "0.65": [0, 0, 0]}},
        }},
        "roar": {"animation_length": 2.2, "bones": {
            "body": {"position": {"0": [0, 0, 0], "0.7": [0, 4, 0], "2.2": [0, 0, 0]}},
            "head": {"rotation": {"0": [0, 0, 0], "0.7": [-28, 0, 0], "1.7": [-18, 0, 0], "2.2": [0, 0, 0]}},
            "jaw": {"rotation": {"0": [0, 0, 0], "0.7": [45, 0, 0], "1.7": [42, 0, 0], "2.2": [0, 0, 0]}},
        }},
        "breath": {"animation_length": 1.5, "bones": {
            "body": {"rotation": {"0": [0, 0, 0], "0.8": [-7, 0, 0], "1.5": [4, 0, 0]}},
            "head": {"rotation": {"0": [0, 0, 0], "0.8": [18, 0, 0], "1.5": [-14, 0, 0]}},
            "jaw": {"rotation": {"0": [0, 0, 0], "0.8": [30, 0, 0], "1.5": [22, 0, 0]}},
        }},
        "tail_sweep": {"animation_length": 0.9, "bones": {
            "body": {"rotation": {"0": [0, -22, 0], "0.42": [0, 48, 0], "0.9": [0, 0, 0]}},
            "tail1": {"rotation": {"0": [0, -35, 0], "0.42": [0, 45, 0], "0.9": [0, 0, 0]}},
            "tail2": {"rotation": {"0": [0, -42, 0], "0.42": [0, 58, 0], "0.9": [0, 0, 0]}},
            "tail3": {"rotation": {"0": [0, -55, 0], "0.42": [0, 70, 0], "0.9": [0, 0, 0]}},
        }},
    },
}

# A separate slow fin-and-tail cycle makes hovering read as flight rather than walking.
animations["animations"]["mouth_open"] = {
    "loop": True, "animation_length": 2,
    "bones": {"jaw": {"rotation": {"0.0": [32,0,0], "1.0": [34,0,0], "2.0": [32,0,0]}}}}
animations["animations"]["mouth_closed"] = {
    "loop": True, "animation_length": 1, "bones": {"jaw": {"rotation": [0,0,0]}}}
animations["animations"]["glide"] = {
    "loop": True, "animation_length": 2.8, "bones": {
        "body": {"position": {"0": [0, 0, 0], "1.4": [0, 0.7, 0], "2.8": [0, 0, 0]}},
        "tail1": {"rotation": {"0": [0, -5, 0], "1.4": [0, 5, 0], "2.8": [0, -5, 0]}},
        "tail2": {"rotation": {"0": [0, 9, 0], "1.4": [0, -9, 0], "2.8": [0, 9, 0]}},
        "tail3": {"rotation": {"0": [0, 14, 0], "1.4": [0, -14, 0], "2.8": [0, 14, 0]}},
    },
}
for side in (-1, 1):
    for part, amplitude in (("front", 9), ("rear", 6)):
        animations["animations"]["glide"]["bones"][f"fin_{part}_{side}"] = {
            "rotation": {"0": [0, 0, side*amplitude], "1.4": [0, 0, -side*amplitude],
                         "2.8": [0, 0, side*amplitude]}}

# Smooth, phase-delayed tail propagation and a wide, sustained jaw pose.
from leviathan_animation_curves import refine_animations
refine_animations(animations)

for path in (GEO, ANIM, TEX, GLOW, PREVIEW):
    path.parent.mkdir(parents=True, exist_ok=True)
with GEO.open("w", encoding="utf-8", newline="\n") as handle:
    handle.write(json.dumps(geo, ensure_ascii=False, indent=2) + "\n")
with ANIM.open("w", encoding="utf-8", newline="\n") as handle:
    handle.write(json.dumps(animations, ensure_ascii=False, indent=2) + "\n")
texture.save(TEX)
glow.save(GLOW)

# Render the exported geometry rather than an unrelated silhouette sketch.
from leviathan_textured_preview import render_preview
render_preview(GEO, TEX, PREVIEW)
print(json.dumps({"bones": len(bones), "cubes": len(cubes), "geo": str(GEO),
                  "preview": str(PREVIEW)}, indent=2))
