"""Offline material/silhouette preview of the real Wind Warden Bedrock rig.

This is *not* a game screenshot: it walks the generated geo.json, projects every
cube with its bone transform, and colours each face with the mean of the atlas
patch that face actually samples. That makes it good enough to judge proportions
and the emerald palette before anything is compiled.

The conjured sword / spear are deliberately absent: they are the shared
tnc:wind/arsenal item models, drawn at runtime by WindWardenRenderer, not bones.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
# first CLI arg picks the model, so one previewer serves every boss rig
MODEL = sys.argv[1] if len(sys.argv) > 1 else "wind_warden"
TARGET = ROOT / f"docs/previews/{MODEL}_model.png"

FACES = {
    "north": [0, 1, 3, 2],
    "south": [4, 6, 7, 5],
    "west": [0, 2, 6, 4],
    "east": [1, 5, 7, 3],
    "up": [2, 3, 7, 6],
    "down": [0, 4, 5, 1],
}


def rotation(degrees):
    x, y, z = np.radians(degrees)
    cx, sx = np.cos(x), np.sin(x)
    cy, sy = np.cos(y), np.sin(y)
    cz, sz = np.cos(z), np.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def load():
    geometry = json.loads((ASSETS / f"geo/entity/{MODEL}.geo.json")
                          .read_text(encoding="utf-8"))["minecraft:geometry"][0]
    texture = Image.open(ASSETS / f"textures/entity/{MODEL}.png").convert("RGB")
    return geometry, np.asarray(texture) / 255.0


def build_faces(geometry, texture):
    bones = {b["name"]: b for b in geometry["bones"]}
    transforms = {}

    def matrix(name):
        if name in transforms:
            return transforms[name]
        entry = bones[name]
        pivot = np.array(entry.get("pivot", [0, 0, 0]))
        rot = rotation(entry.get("rotation", [0, 0, 0]))
        local = np.eye(4)
        local[:3, :3] = rot
        local[:3, 3] = pivot - rot @ pivot
        result = matrix(entry["parent"]) @ local if entry.get("parent") else local
        transforms[name] = result
        return result

    out = []
    for bone in bones.values():
        for cube in bone.get("cubes", []):
            origin = np.array(cube["origin"], dtype=float)
            size = np.array(cube["size"], dtype=float)
            corners = np.array([origin + size * np.array([i & 1, (i >> 1) & 1, (i >> 2) & 1])
                                for i in range(8)])
            polygons = [(face, corners[ids]) for face, ids in FACES.items()]
            cube_pivot = np.array(cube.get("pivot", [0, 0, 0]))
            cube_rot = rotation(cube.get("rotation", [0, 0, 0]))
            for face, points in polygons:
                points = (cube_rot @ (points - cube_pivot).T).T + cube_pivot
                points = (matrix(bone["name"]) @ np.column_stack([points, np.ones(len(points))]).T).T[:, :3]
                spec = cube.get("uv", {}).get(face)
                if isinstance(spec, dict):
                    u, v = spec["uv"]
                    uw, vh = spec["uv_size"]
                    x0, x1 = sorted(int(a / 256 * texture.shape[1]) for a in (u, u + uw))
                    y0, y1 = sorted(int(a / 256 * texture.shape[0]) for a in (v, v + vh))
                    patch = texture[max(0, y0):max(y0 + 1, y1), max(0, x0):max(x0 + 1, x1)]
                    color = patch.mean(axis=(0, 1)) if patch.size else np.array([.10, .10, .11])
                else:
                    color = np.array([.10, .10, .11])
                normal = np.cross(points[1] - points[0], points[2] - points[0])
                normal /= max(1e-8, np.linalg.norm(normal))
                # Three-light model: flat two-tone shading was hiding all the sculpting.
                key = max(0.0, float(np.dot(normal, np.array([-0.35, -0.55, 0.76]))))
                fill = max(0.0, float(np.dot(normal, np.array([0.62, -0.30, 0.30]))))
                rim = max(0.0, float(np.dot(normal, np.array([0.10, 0.92, -0.20]))))
                lum = 0.34 + 0.58 * key + 0.24 * fill + 0.14 * rim
                color = color * lum
                # Cool shadow / warm highlight tint keeps the emerald from looking flat.
                color = color + (np.array([-0.015, 0.010, 0.004]) * key
                                 + np.array([0.006, 0.012, 0.014]) * rim)
                color = np.clip(color, 0, 1)
                out.append((points[:, [0, 2, 1]], color))
    return out


def main():
    geometry, texture = load()
    faces = build_faces(geometry, texture)

    polygons = [poly for poly, _ in faces]
    colours = [colour for _, colour in faces]

    # (title, azimuth, elevation, xlim, ylim, zlim)
    views = [
        ("SIDE / full silhouette", 0, 6, (-22, 44), (-22, 22), (0, 48)),
        ("FRONT / head-on", -90, 6, (-30, 30), (-30, 30), (0, 48)),
        ("ABOVE 3/4 / wings and tail", -55, 42, (-24, 40), (-26, 26), (0, 48)),
        ("HEAD / fangs, brow, ears", -60, 4, (-16, 16), (-18, 18), (26, 46)),
        ("WING / membrane and primaries", 40, 10, (-8, 40), (-16, 16), (22, 46)),
        ("FRONT-LOW / chest, forelegs, claws", -90, 4, (-18, 18), (-18, 18), (0, 40)),
    ]
    figure = plt.figure(figsize=(16, 10.4), facecolor="#0e1a17")
    for index, (title, azimuth, elevation, xlim, ylim, zlim) in enumerate(views):
        axes = figure.add_subplot(2, 3, index + 1, projection="3d")
        axes.set_facecolor("#0e1a17")
        axes.add_collection3d(Poly3DCollection(
            polygons, facecolors=colours,
            edgecolors=np.maximum(np.array(colours) * .82, .02), linewidths=.06))
        axes.set(xlim=xlim, ylim=ylim, zlim=zlim)
        axes.set_box_aspect([xlim[1] - xlim[0], ylim[1] - ylim[0], zlim[1] - zlim[0]])
        axes.view_init(elev=elevation, azim=azimuth)
        axes.set_proj_type("ortho")
        axes.set_axis_off()
        axes.set_title(title, color="#cfe9dd", fontsize=11)

    figure.suptitle("WIND WARDEN / Cui-Lan Wu-Zun -- phase 1 sword, phase 2 spear",
                    color="white", fontsize=18)
    figure.text(.5, .028,
                "Offline mesh preview · real geo.json + real atlas samples · "
                "conjured wind sword/spear are drawn at runtime, not modelled here",
                ha="center", color="#8fb3a6", fontsize=9.5)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(TARGET, dpi=150, facecolor=figure.get_facecolor(), bbox_inches="tight")
    print(TARGET)


if __name__ == "__main__":
    main()
