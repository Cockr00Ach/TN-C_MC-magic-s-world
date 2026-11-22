"""Sculpt the authored Skyhaven terrain in local island coordinates, offline."""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import distance_transform_edt

HERE = Path(__file__).resolve().parent
X0, Z0 = -240, -152
WIDTH, DEPTH = 848, 800


def outline():
    shapes = json.loads((HERE / "skyhaven_headlands.json").read_text("utf-8"))
    image = Image.new("L", (WIDTH, DEPTH))
    draw = ImageDraw.Draw(image)
    def polygon(vertices, value):
        pts = np.asarray(vertices, float)
        for _ in range(2):
            nxt = np.roll(pts, -1, axis=0)
            pts = np.stack((.82*pts+.18*nxt, .18*pts+.82*nxt), axis=1).reshape(-1, 2)
        draw.polygon([(round(x-X0), round(z-Z0)) for x,z in pts], fill=value)
    for poly in [shapes["main"], *shapes["islets"]]:
        polygon(poly, 255)
    # Join the western headlands to the original mountain, reaching far out to sea.
    outer = [(x+260,z+260) for x,z in shapes["western_coast_world"]]
    polygon(outer,255)
    for inlet in shapes["west_inlets_world"]:
        polygon([(x+260,z+260) for x,z in inlet],0)
    polygon(shapes["window"], 0)
    return np.asarray(image) != 0


def write_coast(footprint):
    # 8-block grid, padded into the empty ocean. Signed distances are in block units.
    land = np.zeros((1281,1281), bool)
    x, z = 640 + X0 - 260, 640 + Z0 - 260
    land[z:z+DEPTH, x:x+WIDTH] = footprint
    signed = distance_transform_edt(~land) - distance_transform_edt(land)
    data = (np.rint(signed[::8,::8]).astype(">i2")).tobytes()
    dest = HERE.parent / "src/main/resources/data/tnc/skyhaven/headland_coast.bin"
    dest.write_bytes(data)

