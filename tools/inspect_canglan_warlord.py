"""Text-mode inspector for the Canglan Warlord rig -- a z-buffered ASCII render.

The offline PNG preview is the deliverable, but a text-only session cannot
*look* at a PNG. This renders the same geometry (bone hierarchy + optional
animation pose + optional drawn wind arsenal blade) to an ASCII depth/lighting
map, so silhouette regressions are visible in a terminal:

    python tools/inspect_canglan_warlord.py
    python tools/inspect_canglan_warlord.py --clip overhead --time 0.8 --form 1
    python tools/inspect_canglan_warlord.py --view side --clip spear_throw --form 2
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview_canglan_warlord as preview  # noqa: E402

RAMP = " .:-=+*#%@"


def triangles(lit_faces):
    tris = []
    for points, colour, mat in lit_faces:
        # shade() already applied lighting; recover a 0..1 luminance for the ramp
        level = float(np.clip(colour.mean() * 1.55, 0.0, 1.0))
        tris.append((points[0], points[1], points[2], level, mat))
        tris.append((points[0], points[2], points[3], level, mat))
    return tris


def render(lit_faces, cols=104, rows=66, pad=2.0, material=False):
    MAT_CHAR = {"steel": "S", "mist": "M", "edge": "E", "gale": "G", "blade": "B", "?": "?"}
    tris = triangles(lit_faces)
    pts = np.array([p for tri in tris for p in tri[:3]])
    # shade() maps model space to (right, depth, up): screen x, z, height
    lo, hi = pts[:, 0].min(), pts[:, 0].max()
    ylo, yhi = pts[:, 2].min(), pts[:, 2].max()
    sx = (hi - lo) + pad * 2
    sy = (yhi - ylo) + pad * 2
    # one terminal cell is about twice as tall as it is wide
    scale = min(cols / sx, rows * 2.0 / sy)
    width = max(1, int(sx * scale))
    height = max(1, int(sy * scale * 0.5))
    grid = [[" "] * width for _ in range(height)]
    zbuf = np.full((height, width), np.inf)

    def project(p):
        return ((p[0] - lo + pad) * scale,
                (yhi - p[2] + pad) * scale * 0.5,
                p[1])

    for pa, pb, pc, level, mat in tris:
        ax, ay, az = project(pa)
        bx, by, bz = project(pb)
        cx, cy, cz = project(pc)
        minx = max(0, int(math.floor(min(ax, bx, cx))))
        maxx = min(width - 1, int(math.ceil(max(ax, bx, cx))))
        miny = max(0, int(math.floor(min(ay, by, cy))))
        maxy = min(height - 1, int(math.ceil(max(ay, by, cy))))
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
                w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
                w2 = 1 - w0 - w1
                if w0 < -0.002 or w1 < -0.002 or w2 < -0.002:
                    continue
                depth = w0 * az + w1 * bz + w2 * cz
                if depth < zbuf[py, px]:
                    zbuf[py, px] = depth
                    if material:
                        grid[py][px] = MAT_CHAR.get(mat, "?")
                    else:
                        index = max(1, min(len(RAMP) - 1, int(level * (len(RAMP) - 1) + 0.5)))
                        grid[py][px] = RAMP[index]
    return "\n".join("".join(row).rstrip() for row in grid)


def main():
    args = sys.argv[1:]
    clip = args[args.index("--clip") + 1] if "--clip" in args else None
    time = float(args[args.index("--time") + 1]) if "--time" in args else 0.0
    view = args[args.index("--view") + 1] if "--view" in args else "front"
    cols = int(args[args.index("--cols") + 1]) if "--cols" in args else 104
    form = int(args[args.index("--form") + 1]) if "--form" in args else None
    geometry, texture, clips = preview.load()
    pose = preview.sample(clips, clip, time) if clip else {}
    faces = preview.shade(preview.with_weapon(*preview.build_faces(geometry, texture, pose), form),
                          preview.view_rot(view))
    label = f"{clip} t={time:.2f}s" if clip else "rest pose"
    print(f"=== CANGLAN WARLORD / {view} / {label} / form {form} ===")
    print(render(faces, cols=cols, material="--mat" in args))
    pts = np.array([p for poly, _, _ in faces for p in poly])
    print(f"quads={len(faces)} top={pts[:, 2].max():.1f}u "
          f"width={pts[:, 0].max() - pts[:, 0].min():.1f}u")
    print(f"weapon tip reach from body centre: "
          f"{np.linalg.norm(wind_blade_tip(faces)):.1f}u = "
          f"{np.linalg.norm(wind_blade_tip(faces)) / 16:.2f} blocks")


def wind_blade_tip(faces):
    """Cheap stand-in: the drawn blade face furthest from the chest centre."""
    points = np.array([p for poly, _, mat in faces if mat == "blade" for p in poly])
    if not len(points):
        return np.zeros(3)
    centre = np.array([0.0, 40.0, 0.0])
    return points[np.argmax(np.linalg.norm(points - centre, axis=1))]


if __name__ == "__main__":
    main()
