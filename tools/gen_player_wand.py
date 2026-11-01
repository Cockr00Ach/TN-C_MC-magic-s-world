"""gen_player_wand.py -- the player rig (tnc:self) with the shipped staff in the right hand.

WHY THIS EXISTS
===============
The author wanted a player model that already HOLDS the mod's staff, so casting poses can be
animated in Blockbench with the prop in hand instead of an empty fist.

It does not invent a staff: it reads the staff the mod already ships
(assets/tnc/models/item/wands/held/<wand>.json + its 256x256 texture) and reduces it to
axis-aligned boxes, which is the only thing a Bedrock entity model can hold. Lengths, part
order, widths and colours all come from that mesh; see docs/tools/玩家持杖模型_20261029.md
for the part-by-part mapping table.

THE RIG
=======
The seven humanoid bones are copied byte-for-byte from self.geo.json, so every animation
made for the plain player model still applies. Two bones are added:

    wand       parent armRight, pivot at the grip  -> the whole staff
    wandJewel  parent wand, pivot at the drop      -> the jewel in the crown, for glow/spin

The staff is vertical (no rest rotation anywhere: Bedrock animation rotation replaces the
whole vector, so a baked-in angle would be lost the moment a clip plays).

USAGE
=====
    python tools/gen_player_wand.py                 # magic_wand (the base staff)
    python tools/gen_player_wand.py water_wand_1    # any of the 36 shipped staffs

OUTPUT
======
    src/main/resources/assets/tnc/geo/entity/self_wand.geo.json
    src/main/resources/assets/tnc/textures/entity/self_wand_bedrock.png

Pure ASCII (repo rule; Chinese docs live in docs/).
"""

import io
import json
import math
import os
import random
import sys
from collections import defaultdict

from PIL import Image, ImageDraw

MODID = "tnc"
NAME = "self_wand"
SOURCE = "self"
SHEET = 128
FREE_X = 64
SEED = 20261029

# Bedrock model space: 1 unit = 1/16 block, feet y=0, the model's face looks toward -Z.
# The right fist is the bottom of armRight: cube x[-8,-4] y[11,23] z[-2,2].
# The staff axis sits slightly in front of and outside the fist centre so the shaft is
# visible in front of the arm above the hand (never buried inside the forearm), and its
# widest part (the crown) still clears the hat layer at x=-4.5.
GRIP = [-6.9, 13.0, -2.3]     # x, y, z of the fist's grip point (= the wand bone's pivot)
CROWN_CLEARANCE_X = -4.70     # keep the crown this far outside the hat layer (-X face -4.5)

# Where the fist sits ALONG the staff, in units, on top of the item's own grip point.
#
# This stays 0.0 - the fist goes exactly where the *item* is held (tnc_grip y=5 in item
# space, i.e. 9.65 units above the butt), so the NPC holds the staff the same way the
# player holds it in hand.  User, 2026-10-31: "以前版本的法杖握法是正确的啊".
#
# 2026-10-30 I briefly set this to +7.0 (fist at the bottom of the shaft, "like a sword
# hilt"). That was my misreading of "法杖怎么能这么拿" - the actual complaint was about the
# shaft flailing during the animations (missing wrist), not about where the hand sits.
# See tools/lock_staff_upright.py. Do not shift this again without the user's word.
HAND_SHIFT = 0.0

# How each named part of the shipped mesh becomes boxes. "sleeve"/"ring"/"jewel" are single
# boxes, "cage" is four side panels plus a cap (the crown is a hollow shell around the
# drop), and "inlay" is painted onto its parent instead of getting its own geometry.
RULES = {
    "lower_ferrule": ("ring", "shaftDark"),
    "lower_shaft": ("sleeve", "shaftDark"),
    "pommel": ("ring", "metal"),
    "shaft": ("sleeve", "shaft"),
    "collar": ("inlay", "binding"),
    "shaft_inlay": ("inlay", "brass"),
    "inset": ("ring", "jewel"),
    "conduit_inlay": ("inlay", "conduit"),
    "socket": ("sleeve", "metal"),
    "socket_jewel": ("ring", "jewel"),
    "wave_inlay": ("inlay", "brass"),
    "tide_crown": ("cage", "metal"),
    "tide_drop": ("jewel", "jewel"),
}

FACES = ("up", "down", "west", "north", "east", "south")


# ---------------------------------------------------------------------------
# reading the shipped staff
# ---------------------------------------------------------------------------
def load_wand(name):
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    model = os.path.join(repo, "src", "main", "resources", "assets", MODID, "models",
                         "item", "wands", "held", name + ".json")
    tex = os.path.join(repo, "src", "main", "resources", "assets", MODID, "textures",
                       "item", "wands", "held", name + ".png")
    data = json.load(io.open(model, encoding="utf-8"))
    image = Image.open(tex).convert("RGBA")
    return data, image


def part_stats(data, image):
    """Per named part: y span, max radius around the grip axis, dominant + hi colour."""
    grip = data["tnc_grip"]
    gx, gz = grip[0], grip[2]
    w, h = image.size
    uvs = [(u, v) for s in data["surfaces"] for (u, v) in s["uv"]]
    uvmax = max(max(u, v) for u, v in uvs)
    uvscale = float(w) / 16.0 if uvmax <= 16.5 else 1.0

    def sample(u0, v0, u1, v1):
        x0, x1 = sorted((u0 * uvscale, u1 * uvscale))
        y0, y1 = sorted((v0 * uvscale, v1 * uvscale))
        acc, n = [0, 0, 0], 0
        for y in range(int(max(0, min(h - 1, y0))), int(max(0, min(h - 1, y1))) + 1):
            for x in range(int(max(0, min(w - 1, x0))), int(max(0, min(w - 1, x1))) + 1):
                r, g, b, a = image.getpixel((x, y))
                if a < 32:
                    continue
                acc[0] += r
                acc[1] += g
                acc[2] += b
                n += 1
        if not n:
            return None
        return tuple(int(round(c / float(n))) for c in acc)

    per_part = defaultdict(list)
    for s in data["surfaces"]:
        part = s.get("part", "?")
        ys = [v[1] for v in s["vertices"]]
        rs = [math.hypot(v[0] - gx, v[2] - gz) for v in s["vertices"]]
        per_part[part].append((min(ys), max(ys), max(rs),
                               sample(s["uv"][0][0], s["uv"][0][1],
                                      s["uv"][2][0], s["uv"][2][1])))
    stats = {}
    for part, items in per_part.items():
        cols = [i[3] for i in items if i[3]]
        cols.sort(key=sum)
        lo = cols[len(cols) // 8] if cols else (90, 90, 90)
        hi = cols[-max(1, len(cols) // 8)] if cols else (200, 200, 200)
        mid = cols[len(cols) // 2] if cols else (140, 140, 140)
        stats[part] = {
            "y0": min(i[0] for i in items),
            "y1": max(i[1] for i in items),
            "r": max(i[2] for i in items),
            "lo": lo, "mid": mid, "hi": hi,
            "quads": len(items),
        }
    return stats, grip


# ---------------------------------------------------------------------------
# boxes
# ---------------------------------------------------------------------------
def build_boxes(stats, grip):
    """Turn the measured parts into model-space boxes (fist at y=GRIP[1] + HAND_SHIFT)."""
    def my(item_y):
        return GRIP[1] + HAND_SHIFT + (item_y - grip[1])

    boxes = []
    for part, (kind, role) in RULES.items():
        if kind == "inlay" or part not in stats:
            continue
        s = stats[part]
        y0, y1 = my(s["y0"]), my(s["y1"])
        half = s["r"]
        if kind == "ring":
            half += 0.12          # rings have to stand proud of the shaft to be visible
        if part == "tide_crown":
            half = min(half, CROWN_CLEARANCE_X - GRIP[0])
        if kind == "cage":
            # Four corner posts + a base ring + a cap: an open cage, so the jewel is
            # visible between the posts from every side (a solid plate would read as a
            # paddle, not as the crest the mesh actually has).
            panels = []
            thick = 0.95
            for sx in (-1, 1):
                for sz in (-1, 1):
                    panels.append(((GRIP[0] + sx * (half - thick / 2) - thick / 2, y0,
                                    GRIP[2] + sz * (half - thick / 2) - thick / 2),
                                   (thick, y1 - y0, thick), "cagePost"))
            panels.append(((GRIP[0] - half, y0, GRIP[2] - half),
                           (2 * half, 0.85, 2 * half), "cageBase"))
            panels.append(((GRIP[0] - half, y1 - 0.70, GRIP[2] - half),
                           (2 * half, 0.70, 2 * half), "cageTop"))
            for origin, size, sub in panels:
                boxes.append({"part": part, "origin": list(origin), "size": list(size),
                              "role": role, "sub": sub})
            continue
        origin = [GRIP[0] - half, y0, GRIP[2] - half]
        size = [2 * half, y1 - y0, 2 * half]
        boxes.append({"part": part, "origin": origin, "size": size, "role": role,
                      "sub": kind})
    boxes.sort(key=lambda b: b["origin"][1])
    return boxes


def colour_of(stats, role):
    """Role -> (base, light, dark) taken from the part that role belongs to."""
    part = {"shaft": "shaft", "shaftDark": "lower_shaft", "binding": "collar",
            "brass": "shaft_inlay", "metal": "socket", "jewel": "tide_drop",
            "conduit": "conduit_inlay"}[role]
    s = stats.get(part) or stats["tide_drop"]
    return s["mid"], s["hi"], s["lo"]


# ---------------------------------------------------------------------------
# texture
# ---------------------------------------------------------------------------
def face_rects(u, v, w, h, d):
    return {
        "up":    (u + d, v, w, d),
        "down":  (u + d + w, v, w, d),
        "west":  (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east":  (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def net_size(w, h, d):
    return (int(math.ceil(2 * d + 2 * w)), int(math.ceil(d + h)))


def pack(sizes):
    order = sorted(range(len(sizes)), key=lambda i: -net_size(*sizes[i])[1])
    pos, x, y, shelf = {}, FREE_X, 0, 0
    for i in order:
        nw, nh = net_size(*sizes[i])
        if x + nw > SHEET:
            x, y, shelf = FREE_X, y + shelf + 1, 0
        pos[i] = (x, y)
        x += nw + 1
        shelf = max(shelf, nh)
    return pos


def shade(color, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in color)


def box_fill(draw, x0, y0, x1, y1, color):
    x0, x1 = sorted((int(round(x0)), int(round(x1))))
    y0, y1 = sorted((int(round(y0)), int(round(y1))))
    draw.rectangle([x0, y0, x1, y1], fill=tuple(color) + (255,))


def paint(draw, rects, box, palette, bands):
    """Gradient face + optional horizontal bands (used for the shaft inlays)."""
    base, light, dark = palette[box["role"]]
    for face in FACES:
        x0, y0, fw, fh = [int(round(v)) for v in rects[face]]
        if fw <= 0 or fh <= 0:
            continue
        for row in range(fh):
            f = row / max(1, fh - 1)
            box_fill(draw, x0, y0 + row, x0 + fw - 1, y0 + row, shade(base, 1.12 - 0.30 * f))
        if face == "up":
            box_fill(draw, x0, y0, x0 + fw - 1, y0, shade(light, 1.05))
        elif face == "down":
            box_fill(draw, x0, y0 + fh - 1, x0 + fw - 1, y0 + fh - 1, shade(dark, 0.9))
        elif face in ("west", "north", "east", "south"):
            for band in bands:
                b0 = (band[0] - box["origin"][1]) / box["size"][1]
                b1 = (band[1] - box["origin"][1]) / box["size"][1]
                r0 = int(max(0, min(fh - 1, round(b0 * fh))))
                r1 = int(max(0, min(fh - 1, round(b1 * fh))))
                if r1 < r0:
                    r0, r1 = r1, r0
                if band[3] == "solid":
                    rows = range(r0, r1 + 1)
                else:
                    # inlays in the shipped mesh are filigree, not solid sleeves: draw them
                    # as evenly spaced hairlines so the shaft keeps its dark body colour
                    step = max(2, (r1 - r0) // 7)
                    rows = range(r0, r1 + 1, step)
                for row in rows:
                    box_fill(draw, x0, y0 + row, x0 + fw - 1, y0 + row, band[2])
            # vertical seam so the shaft does not read as one flat slab
            if box["size"][0] >= 1.4 and fw > 3:
                box_fill(draw, x0, y0, x0, y0 + fh - 1, shade(light, 1.02))
                box_fill(draw, x0 + fw - 1, y0, x0 + fw - 1, y0 + fh - 1, shade(dark, 0.92))


# ---------------------------------------------------------------------------
def build_geo(player_bones, boxes, packed):
    out = [json.loads(json.dumps(b)) for b in player_bones]
    wand = {"name": "wand", "parent": "armRight", "pivot": list(GRIP), "cubes": []}
    for i, box in enumerate(boxes):
        wand["cubes"].append({"origin": [round(v, 4) for v in box["origin"]],
                              "size": [round(v, 4) for v in box["size"]],
                              "uv": list(packed[i])})
    jewel = None
    for i, box in enumerate(boxes):
        if box["part"] == "tide_drop":
            jewel = {"name": "wandJewel", "parent": "wand",
                     "pivot": [GRIP[0], round(box["origin"][1] + box["size"][1] / 2.0, 4),
                               GRIP[2]],
                     "cubes": [{"origin": [round(v, 4) for v in box["origin"]],
                                "size": [round(v, 4) for v in box["size"]],
                                "uv": list(packed[i])}]}
            wand["cubes"].pop(i)
            break
    out.append(wand)
    if jewel:
        out.append(jewel)
    return out


def main():
    wand_name = sys.argv[1] if len(sys.argv) > 1 else "magic_wand"
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    geo_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID, "geo", "entity")
    tex_dir = os.path.join(repo, "src", "main", "resources", "assets", MODID, "textures", "entity")
    src_geo = os.path.join(geo_dir, SOURCE + ".geo.json")
    src_tex = os.path.join(tex_dir, SOURCE + "_bedrock.png")

    for path in (src_geo, src_tex):
        if not os.path.isfile(path):
            print("ERROR: missing %s" % path)
            return 1

    data, wand_tex = load_wand(wand_name)
    stats, grip = part_stats(data, wand_tex)
    boxes = build_boxes(stats, grip)
    sizes = [(b["size"][0], b["size"][1], b["size"][2]) for b in boxes]
    packed = pack(sizes)

    # colours come from the shipped 256x256 staff texture, not from taste
    palette = {role: colour_of(stats, role)
               for role in ("shaft", "shaftDark", "binding", "brass", "metal", "jewel",
                            "conduit")}
    def my(item_y):
        return GRIP[1] + HAND_SHIFT + (item_y - grip[1])
    # inlays have no geometry: they are painted bands on the box they sit on
    bands = {"shaft": [], "tide_crown": []}
    if "shaft_inlay" in stats:
        bands["shaft"].append((my(stats["shaft_inlay"]["y0"]),
                               my(stats["shaft_inlay"]["y1"]), palette["brass"][0], "rings"))
    if "conduit_inlay" in stats:
        bands["shaft"].append((my(stats["conduit_inlay"]["y0"]),
                               my(stats["conduit_inlay"]["y1"]), palette["conduit"][0], "rings"))
    if "collar" in stats:
        bands["shaft"].append((my(stats["collar"]["y0"]), my(stats["collar"]["y0"]) + 0.9,
                               palette["binding"][0], "solid"))
        bands["shaft"].append((my(stats["collar"]["y1"]) - 0.9, my(stats["collar"]["y1"]),
                               palette["binding"][0], "solid"))
    if "wave_inlay" in stats:
        bands["tide_crown"].append((my(stats["wave_inlay"]["y0"]),
                                    my(stats["wave_inlay"]["y1"]), palette["brass"][0], "rings"))

    sheet = Image.open(src_tex).convert("RGBA")
    if sheet.size != (SHEET, SHEET):
        print("ERROR: source skin is %s, expected %dx%d" % (sheet.size, SHEET, SHEET))
        return 1
    draw = ImageDraw.Draw(sheet)
    for i, box in enumerate(boxes):
        rects = face_rects(packed[i][0], packed[i][1], box["size"][0], box["size"][1],
                           box["size"][2])
        paint(draw, rects, box, palette, bands.get(box["part"], []))

    player_bones = json.load(io.open(src_geo, encoding="utf-8"))["minecraft:geometry"][0]["bones"]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.%s.%s" % (MODID, NAME),
                "texture_width": SHEET,
                "texture_height": SHEET,
                "visible_bounds_width": 3,
                "visible_bounds_height": 6,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": build_geo(player_bones, boxes, packed),
        }],
    }

    geo_path = os.path.join(geo_dir, NAME + ".geo.json")
    tex_path = os.path.join(tex_dir, NAME + "_bedrock.png")
    with io.open(geo_path, "w", encoding="utf-8") as f:
        json.dump(geo, f, ensure_ascii=False, indent="\t")
        f.write("\n")
    sheet.save(tex_path, optimize=True)

    bones = geo["minecraft:geometry"][0]["bones"]
    print("[%s] staff source: %s (%d quads, %.2f blocks tall)" % (
        NAME, wand_name, len(data["surfaces"]),
        (max(stats[p]["y1"] for p in stats) - min(stats[p]["y0"] for p in stats)) / 16.0))
    print("[%s] geo -> %s (%d bones, %d cubes)" % (
        NAME, os.path.relpath(geo_path, repo), len(bones),
        sum(len(b.get("cubes", [])) for b in bones)))
    print("[%s] tex -> %s" % (NAME, os.path.relpath(tex_path, repo)))
    print("")
    print("%-16s %-7s %8s %8s %8s  %s" % ("part", "kind", "y0", "y1", "width", "colour"))
    for i, box in enumerate(boxes):
        print("%-16s %-7s %8.2f %8.2f %8.2f  #%02x%02x%02x" % (
            box["part"], box["sub"], box["origin"][1], box["origin"][1] + box["size"][1],
            box["size"][0],
            palette[box["role"]][0][0], palette[box["role"]][0][1],
            palette[box["role"]][0][2]))
    for part in ("shaft_inlay", "collar", "wave_inlay"):
        if part in stats:
            print("%-16s %-7s %8.2f %8.2f %8s  painted, no geometry" % (
                part, "inlay", my(stats[part]["y0"]), my(stats[part]["y1"]), "-"))
    print("")
    print("grip %s  crown width %.2f  staff top %.2f" % (
        GRIP, max(b["size"][0] for b in boxes),
        max(b["origin"][1] + b["size"][1] for b in boxes)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
