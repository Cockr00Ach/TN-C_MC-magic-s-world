# -*- coding: utf-8 -*-
"""render_instructor_preview.py -- offline renders of the shipped tnc:instructor model.

One geometry, four skins (counter / guard / blade / wind). This tool draws whatever is
actually in the jar -- it reads ``assets/tnc/geo/entity/instructor.geo.json`` and the
``instructor_<variant>.png`` next to it, so the picture cannot drift from the model.

    python tools/render_instructor_preview.py                 # all four, one sheet
    python tools/render_instructor_preview.py counter         # just the sparring dummy

Output: ``work/instructor-preview/<variant>-turnaround.png`` and
``work/instructor-preview/all-variants.png``.

ASCII only in code; the sheet captions are Chinese because the author reads them.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402  (the validated renderer)

GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "instructor.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "instructor.animation.json")
TEX = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "entity",
                   "instructor_%s.png")
OUT = os.path.join("work", "instructor-preview")

VARIANTS = ("counter", "guard", "blade", "wind")
NAMES = {
    "counter": ("陪练傀儡", "counter · 守势反击的假想敌"),
    "guard": ("洛克", "guard · 举盾与格挡"),
    "blade": ("莉娅", "blade · 三段连击"),
    "wind": ("岚", "wind · 风刃与位移"),
}
# azimuth 0 looks at the model's -Z face, which is the face GeckoLib maps onto "looking
# at the player", i.e. the front. 35 is the usual three-quarter hero angle.
VIEWS = ((0, "正面"), (35, "前左 35°"), (90, "侧面"), (180, "背面"))
PANEL = 560
ZOOM = 15.0
CENTER = (0.0, 16.0, 0.0)
BACKGROUND = (14, 18, 26)


def font(size):
    for path in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    return ImageFont.load_default()


def render_variant(variant):
    """Render the four canonical views of one skin into a single row."""
    P.FRONT = "-Z"                                  # the eyes are painted on the -Z face
    P._GEO, order = P.load_geo(GEO)
    P._TEX = Image.open(TEX % variant).convert("RGBA")
    anim = P.load_anim(ANIM, "idle")                # the rest of the pose is the idle frame 0
    row = Image.new("RGB", (PANEL * len(VIEWS), PANEL + 44), BACKGROUND)
    draw = ImageDraw.Draw(row)
    caption = font(22)
    title = font(26)
    draw.text((18, 8), "%s  %s" % (NAMES[variant][0], NAMES[variant][1]),
              font=title, fill=(226, 214, 180))
    for i, (azimuth, label) in enumerate(VIEWS):
        panel = P.render(P._GEO, order, anim, 0.0, PANEL, azimuth, 8, ZOOM, "",
                         center=CENTER)
        row.paste(panel, (i * PANEL, 44))
        draw.text((i * PANEL + 14, PANEL + 22), label, font=caption, fill=(150, 172, 188))
    return row


def skeleton_sheet(variant):
    """Front view with every bone pivot labelled -- proof of the joint count."""
    P.FRONT = "-Z"
    P._GEO, order = P.load_geo(GEO)
    P._TEX = Image.open(TEX % variant).convert("RGBA")
    anim = P.load_anim(ANIM, "idle")
    size = PANEL
    panel = P.render(P._GEO, order, anim, 0.0, size, 0, 8, ZOOM, "", center=CENTER)
    draw = ImageDraw.Draw(panel)
    label = font(15)
    _, right, up = P.camera(0, 8)
    tfs = P.world_transforms(P._GEO, anim, 0.0)
    ident = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0.0, 0.0, 0.0])

    def project(p):
        v = [p[i] - CENTER[i] for i in range(3)]
        x = sum(v[i] * right[i] for i in range(3))
        y = sum(v[i] * up[i] for i in range(3))
        return (size / 2 + x * ZOOM, size / 2 - y * ZOOM)

    placed = []
    for index, name in enumerate(order):
        bone = P._GEO[name]
        parent = bone.get("parent")
        pivot = bone.get("pivot") or [0, 0, 0]
        track = anim["bones"].get(name, {})
        offset = P.sample(track.get("position"), 0.0, anim.get("animation_length", 0.0),
                          bool(anim.get("loop")))
        world = P.apply(tfs[parent] if parent else ident,
                        [pivot[i] + offset[i] for i in range(3)])
        px, py = project(world)
        # keep labels from piling up: alternate right / left, nudge by index
        side = 1 if px >= size / 2 else -1
        tx = px + side * 10
        ty = py - 9 + (index % 3) * 11
        draw.line([(px, py), (tx, ty + 8)], fill=(120, 210, 240), width=1)
        draw.text((tx if side > 0 else tx - 7 * len(name), ty), name, font=label,
                  fill=(226, 240, 248))
        placed.append(name)
    draw.rectangle([0, size - 30, size, size], fill=(10, 14, 20))
    draw.text((10, size - 26), "17 bones · arms 3 segments each side · legs 2 (thigh+shin)",
              font=font(16), fill=(150, 190, 205))
    return panel


def main(argv):
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    os.makedirs(OUT, exist_ok=True)

    wanted = [a for a in argv[1:] if a in VARIANTS] or list(VARIANTS)
    rows = []
    for variant in wanted:
        row = render_variant(variant)
        path = os.path.join(OUT, "%s-turnaround.png" % variant)
        row.save(path)
        print("wrote %s" % os.path.relpath(path, repo))
        rows.append(row)

    skeleton = skeleton_sheet(wanted[0])
    path = os.path.join(OUT, "bones-%s.png" % wanted[0])
    skeleton.save(path)
    print("wrote %s" % os.path.relpath(path, repo))

    if len(rows) > 1:
        width = max(r.width for r in rows)
        sheet = Image.new("RGB", (width, sum(r.height for r in rows)), BACKGROUND)
        y = 0
        for row in rows:
            sheet.paste(row, (0, y))
            y += row.height
        path = os.path.join(OUT, "all-variants.png")
        sheet.save(path)
        print("wrote %s" % os.path.relpath(path, repo))


if __name__ == "__main__":
    main(sys.argv)
