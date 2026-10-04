# -*- coding: utf-8 -*-
"""preview_vanilla_model.py -- offline render of a vanilla block model json.

Needed because the dragon is being moved from GeckoLib to a Display entity drawing a plain
block model (tools/gen_dragon_block_model.py). That converter folds a whole Bedrock bone
chain into per-element rotations, so "does it still look like a dragon" cannot be argued in
review -- it has to be looked at. This renders the vanilla model with the same camera /
painter / flat-shade approach as tools/preview_geo_anim.py, and samples the real texture, so
the output shows the actual palette and any face that ended up on the wrong side.

Usage:
  python tools/preview_vanilla_model.py <model.json> [--views=side,front,top,three]
                                       [--texture=path.png] [--fit] [--out=...]
ASCII only.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw


def rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(r, t, p):
    return [sum(r[i][k] * p[k] for k in range(3)) + t[i] for i in range(3)]


def norm(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / l for c in v]


def camera(azimuth, elevation):
    a, e = math.radians(azimuth), math.radians(elevation)
    f = norm([math.sin(a) * math.cos(e), -math.sin(e), math.cos(a) * math.cos(e)])
    wup = [0, 1, 0]
    right = norm([f[1] * wup[2] - f[2] * wup[1], f[2] * wup[0] - f[0] * wup[2],
                  f[0] * wup[1] - f[1] * wup[0]])
    up = norm([right[1] * f[2] - right[2] * f[1], right[2] * f[0] - right[0] * f[2],
               right[0] * f[1] - right[1] * f[0]])
    return f, right, up


# vanilla face name -> (corner indices, outward normal)
FACES = {
    "north": ([0, 1, 3, 2], (0, 0, -1)),
    "south": ([4, 5, 7, 6], (0, 0, 1)),
    "down":  ([0, 1, 5, 4], (0, -1, 0)),
    "up":    ([2, 3, 7, 6], (0, 1, 0)),
    "west":  ([0, 2, 6, 4], (-1, 0, 0)),
    "east":  ([1, 3, 7, 5], (1, 0, 0)),
}


def element_faces(el):
    frm, to = el["from"], el["to"]
    corners = []
    for i in (0, 1):
        for j in (0, 1):
            for k in (0, 1):
                corners.append([frm[0] + (to[0] - frm[0]) * i,
                                frm[1] + (to[1] - frm[1]) * j,
                                frm[2] + (to[2] - frm[2]) * k])
    rot = el.get("rotation")
    r = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    t = [0.0, 0.0, 0.0]
    if rot:
        o = rot.get("origin", [0, 0, 0])
        r = mmul(rx(-rot.get("x", 0.0)), mmul(ry(-rot.get("y", 0.0)), rz(rot.get("z", 0.0))))
        t = [o[i] - sum(r[i][k] * o[k] for k in range(3)) for i in range(3)]
    out = []
    for name, (idx, n) in FACES.items():
        face = el.get("faces", {}).get(name)
        if not face:
            continue
        pts = [apply(r, t, corners[i]) for i in idx]
        nrm = [sum(r[i][k] * n[k] for k in range(3)) for i in range(3)]
        out.append((pts, nrm, face.get("uv")))
    return out


def face_color(tex, rect, fallback):
    if tex is None or not rect:
        return fallback
    x0, y0, x1, y1 = [int(round(v)) for v in rect]
    x0 = max(0, min(tex.width - 1, x0))
    y0 = max(0, min(tex.height - 1, y0))
    x1 = max(x0 + 1, min(tex.width, x1))
    y1 = max(y0 + 1, min(tex.height, y1))
    px = tex.crop((x0, y0, x1, y1)).resize((1, 1), Image.BILINEAR).getpixel((0, 0))
    if len(px) == 4 and px[3] < 8:
        return fallback
    return (px[0], px[1], px[2])


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo)
    args = sys.argv[1:]
    opts, pos = {}, []
    for a in args:
        if a.startswith("--"):
            if "=" in a:
                k, v = a.split("=", 1)
                opts[k] = v
        else:
            pos.append(a)
    model_path = pos[0] if pos else os.path.join(
        "src", "main", "resources", "assets", "tnc", "models", "entity", "dragon_block.json")
    with open(model_path, encoding="utf-8") as f:
        model = json.load(f)

    texture = opts.get("--texture")
    if texture is None:
        ref = model.get("textures", {}).get("0", "tnc:entity/dragon_bedrock")
        guess = os.path.join("src", "main", "resources", "assets", "tnc",
                             ref.split(":")[-1] + ".png")
        texture = guess if os.path.exists(guess) else "-"
    tex = None if texture in (None, "-") else Image.open(texture).convert("RGBA")

    tris = []
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for el in model.get("elements", []):
        for pts, nrm, uv in element_faces(el):
            tris.append((pts, nrm, face_color(tex, uv, (225, 220, 205))))
            for p in pts:
                for c in range(3):
                    lo[c] = min(lo[c], p[c])
                    hi[c] = max(hi[c], p[c])
    center = [(lo[i] + hi[i]) / 2.0 for i in range(3)]

    views = opts.get("--views", "side,front,top,three").split(",")
    size = int(opts.get("--size", 320))
    sheet = Image.new("RGB", (size * len(views), size + 26), (26, 26, 34))
    light = norm([-0.4, 0.85, -0.35])
    for c, view in enumerate(views):
        az, el, tag = {"side": (-90, 8, "side(-X)"), "side2": (90, 8, "side(+X)"),
                       "front": (180, 8, "front(-Z)"), "back": (0, 8, "back(+Z)"),
                       "top": (180, 78, "top"), "three": (-135, 18, "3/4")}[view]
        f, right, up = camera(az, el)
        span = max(hi[i] - lo[i] for i in range(3))
        zoom = size * 0.88 / max(1e-3, span)

        def project(p):
            v = [p[i] - center[i] for i in range(3)]
            return (size / 2 + sum(v[i] * right[i] for i in range(3)) * zoom,
                    size / 2 - sum(v[i] * up[i] for i in range(3)) * zoom)

        img = Image.new("RGB", (size, size), (26, 26, 34))
        d = ImageDraw.Draw(img)
        ordered = sorted(tris, key=lambda e: -sum(sum(p[i] * f[i] for i in range(3))
                                                  for p in e[0]) / len(e[0]))
        for pts, nrm, base in ordered:
            shade = 0.62 + 0.42 * abs(sum(nrm[i] * light[i] for i in range(3)))
            if sum(nrm[i] * (-f[i]) for i in range(3)) < 0:
                shade *= 0.72
            col = tuple(min(255, int(v * shade)) for v in base)
            d.polygon([project(p) for p in pts], fill=col, outline=(40, 38, 44))
        d.rectangle([0, 0, size - 1, 13], fill=(12, 12, 16))
        d.text((3, 2), "%s  %s" % (os.path.basename(model_path), tag), fill=(230, 230, 240))
        sheet.paste(img, (c * size, 0))

    d = ImageDraw.Draw(sheet)
    d.text((6, size + 4), "elements=%d  bounds=%s..%s  (vanilla units, 16/block)"
           % (len(model.get("elements", [])), [round(v) for v in lo], [round(v) for v in hi]),
           fill=(230, 230, 240))
    out = opts.get("--out", os.path.join("docs", "previews", "dragon_block_model.png"))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("preview -> %s (%dx%d)" % (out, sheet.size[0], sheet.size[1]))


if __name__ == "__main__":
    main()
