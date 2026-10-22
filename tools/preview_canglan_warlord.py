"""Offline material / silhouette / pose preview of the real Canglan Warlord rig.

This is not a game screenshot: it walks the generated ``canglan_warlord.geo.json``,
applies the bone hierarchy *and* the keyframes of a chosen animation clip, then
colours every face with the mean of the atlas patch that face actually samples.
The conjured wind arsenal blade is placed on the ``weapon_1`` anchor with the
same transform the renderer uses, so the sword / spear phases are both visible.
Good enough to judge proportions, the palette and whether a swing reads, before
anything is compiled.

Lighting is computed in *view* space (key/fill/rim), so the side and back panels
are as readable as the front one.

    python tools/preview_canglan_warlord.py                 # docs/previews/canglan_warlord_model.png
    python tools/preview_canglan_warlord.py --only pose     # docs/previews/canglan_warlord_poses.png
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

import wind_blade

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
MODEL = "canglan_warlord"
ONLY = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
OUT = ROOT / ("docs/previews/canglan_warlord_poses.png" if ONLY == "pose"
              else "docs/previews/canglan_warlord_model.png")

MATERIALS = (("steel", (0, 0)), ("mist", (128, 0)),
             ("edge", (0, 128)), ("gale", (128, 128)))

FACES = {
    "north": [0, 1, 3, 2],
    "south": [4, 6, 7, 5],
    "west": [0, 2, 6, 4],
    "east": [1, 5, 7, 3],
    "up": [2, 3, 7, 6],
    "down": [0, 4, 5, 1],
}
KEY = np.array([-0.45, 0.55, -0.70])
FILL = np.array([0.65, 0.10, -0.45])
RIM = np.array([0.15, 0.85, 0.45])
for _v in (KEY, FILL, RIM):
    _v /= np.linalg.norm(_v)


def rotation(degrees):
    # GeckoLib bakes animation X/Y negated and model X mirrored. In the
    # authored geometry frame this becomes (-X,+Y,-Z), not raw Euler angles.
    x, y, z = np.radians(np.array(degrees) * np.array([-1,1,-1]))
    cx, sx = np.cos(x), np.sin(x)
    cy, sy = np.cos(y), np.sin(y)
    cz, sz = np.cos(z), np.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def translate(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def view_rot(name):
    """Model -> view space; the camera always sits on -Z and looks toward +Z."""
    if name == "side":
        return np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]], dtype=float)
    if name == "back":
        return np.array([[-1, 0, 0], [0, 1, 0], [0, 0, -1]], dtype=float)
    if name == "threequarter":
        a = math.radians(34)
        return np.array([[math.cos(a), 0, -math.sin(a)], [0, 1, 0],
                         [math.sin(a), 0, math.cos(a)]], dtype=float)
    if name == "side_left":
        return np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]], dtype=float)
    return np.eye(3)


def load():
    geometry = json.loads((ASSETS / f"geo/entity/{MODEL}.geo.json")
                          .read_text(encoding="utf-8"))["minecraft:geometry"][0]
    texture = Image.open(ASSETS / f"textures/entity/{MODEL}.png").convert("RGB")
    clips = json.loads((ASSETS / f"animations/entity/{MODEL}.animation.json")
                       .read_text(encoding="utf-8"))["animations"]
    return geometry, np.asarray(texture) / 255.0, clips


def sample(clips, clip, time):
    """Linear pose sample of one clip: {bone: {'rotation': .., 'position': ..}}."""
    pose = {}
    data = clips.get(clip)
    if not data:
        return pose
    for bone, tracks in data["bones"].items():
        entry = {}
        for key, channel in tracks.items():
            keys = sorted(((float(t), v) for t, v in channel.items()), key=lambda p: p[0])
            if time <= keys[0][0]:
                entry[key] = np.array(keys[0][1], dtype=float)
            elif time >= keys[-1][0]:
                entry[key] = np.array(keys[-1][1], dtype=float)
            else:
                for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                    if t0 <= time <= t1:
                        f = 0.0 if t1 <= t0 else (time - t0) / (t1 - t0)
                        f = f * f * (3 - 2 * f)
                        entry[key] = np.array(v0, dtype=float) * (1 - f) + np.array(v1, dtype=float) * f
                        break
        pose[bone] = entry
    return pose


def build_faces(geometry, texture, pose):
    """Model-space quads, each carrying the atlas colour it samples."""
    bones = {b["name"]: b for b in geometry["bones"]}
    matrices = {}

    def matrix(name):
        if name in matrices:
            return matrices[name]
        entry = bones[name]
        pivot = np.array(entry.get("pivot", [0, 0, 0]), dtype=float)
        anim = pose.get(name, {})
        rot = rotation(anim.get("rotation", entry.get("rotation", [0, 0, 0])))
        offset = np.array(anim.get("position", [0, 0, 0]), dtype=float)
        local = translate(offset) @ translate(pivot) @ np.block(
            [[rot, np.zeros((3, 1))], [np.zeros((1, 3)), np.ones((1, 1))]]
        ) @ translate(-pivot)
        result = matrix(entry["parent"]) @ local if entry.get("parent") else local
        matrices[name] = result
        return result

    out = []
    for name in bones:          # includes empty anchor bones such as weapon_1
        matrix(name)
    for bone in bones.values():
        for cube in bone.get("cubes", []):
            origin = np.array(cube["origin"], dtype=float)
            size = np.array(cube["size"], dtype=float)
            corners = np.array([origin + size * np.array([i & 1, (i >> 1) & 1, (i >> 2) & 1])
                                for i in range(8)])
            cube_pivot = np.array(cube.get("pivot", [0, 0, 0]), dtype=float)
            cube_rot = rotation(cube.get("rotation", [0, 0, 0]))
            for face, ids in FACES.items():
                points = corners[ids]
                points = (cube_rot @ (points - cube_pivot).T).T + cube_pivot
                points = (matrix(bone["name"]) @ np.column_stack(
                    [points, np.ones(len(points))]).T).T[:, :3]
                spec = cube.get("uv", {}).get(face)
                mat = "?"
                if isinstance(spec, dict):
                    u, v = spec["uv"]
                    uw, vh = spec["uv_size"]
                    x0, x1 = sorted(int(a / 256 * texture.shape[1]) for a in (u, u + uw))
                    y0, y1 = sorted(int(a / 256 * texture.shape[0]) for a in (v, v + vh))
                    patch = texture[max(0, y0):max(y0 + 1, y1), max(0, x0):max(x0 + 1, x1)]
                    color = patch.mean(axis=(0, 1)) if patch.size else np.array([.10, .10, .11])
                    for name, (qu, qv) in MATERIALS:
                        if qu <= u < qu + 128 and qv <= v < qv + 128:
                            mat = name
                            break
                else:
                    color = np.array([.10, .10, .11])
                out.append((points, color, mat))
    return out, matrices, bones


def with_weapon(faces, matrices, bones, form):
    """Add the drawn wind arsenal blade for the given phase (1 sword, 2 spear)."""
    if form is None:
        return faces
    return faces + wind_blade.place(form, matrices, bones)


def shade(faces, rot):
    """Rotate into view space and apply the three-light model there."""
    lit = []
    for points, colour, mat in faces:
        view = (rot @ points.T).T
        normal = np.cross(view[1] - view[0], view[2] - view[0])
        normal /= max(1e-8, np.linalg.norm(normal))
        key = max(0.0, float(normal @ KEY))
        fill = max(0.0, float(normal @ FILL))
        rim = max(0.0, float(normal @ RIM))
        lum = 0.40 + 0.78 * key + 0.34 * fill + 0.26 * rim
        colour = np.clip(colour * lum
                         + np.array([-0.010, 0.008, 0.004]) * key
                         + np.array([0.010, 0.014, 0.024]) * rim, 0, 1)
        lit.append((view[:, [0, 2, 1]], colour, mat))
    return lit


def panel(axes, faces, title, view, elev, limits):
    polygons = [poly for poly, _, _ in faces]
    colours = [colour for _, colour, _ in faces]
    axes.add_collection3d(Poly3DCollection(
        polygons, facecolors=colours,
        edgecolors=np.maximum(np.array(colours) * .76, .02), linewidths=.05))
    (x0, x1), (y0, y1), (z0, z1) = limits
    axes.set(xlim=(x0, x1), ylim=(y0, y1), zlim=(z0, z1))
    axes.set_box_aspect([x1 - x0, y1 - y0, z1 - z0])
    axes.view_init(elev=elev, azim=-90)
    axes.set_proj_type("ortho")
    axes.set_axis_off()
    axes.set_title(title, color="#f0e2cf", fontsize=10.5)


def frame(faces_list, pad=0.08, floor=-2.0):
    """Limits that hold every pose in a sheet without cropping the drawn blade."""
    points = np.concatenate([poly for faces in faces_list for poly, _, _ in faces])
    low, high = points.min(axis=0), points.max(axis=0)
    span = max(high - low)
    margin = span * pad
    return ((low[0] - margin, high[0] + margin),
            (low[1] - margin, high[1] + margin),
            (max(floor, low[2] - margin), high[2] + margin))


def main():
    geometry, texture, clips = load()
    head = ((-21, 21), (-26, 26), (58, 122))

    if ONLY == "pose":
        specs = [
            ("PHASE 1 IDLE / sword  t=1.0s", "idle", 1.0, 1, "threequarter", 8),
            ("PHASE 2 IDLE / spear  t=1.0s", "idle", 1.0, 2, "threequarter", 8),
            ("CLEAVE L / sword  t=0.50s (hit)", "cleave_left", 0.50, 1, "front", 8),
            ("OVERHEAD / sword  t=0.80s (hit)", "overhead", 0.80, 1, "side", 6),
            ("SHIELD BASH / sword  t=0.55s (hit)", "shield_bash", 0.55, 1, "threequarter", 8),
            ("THRUST / spear  t=0.60s (hit)", "thrust", 0.60, 2, "side", 6),
            ("SPEAR SWEEP / spear  t=0.90s", "sweep", 0.90, 2, "threequarter", 8),
            ("SPEAR THROW / spear  t=0.62s (release)", "spear_throw", 0.62, 2, "side", 6),
        ]
        shaded = [(title, view, elev, shade(with_weapon(
            *build_faces(geometry, texture, sample(clips, clip, time)), form), view_rot(view)))
            for title, clip, time, form, view, elev in specs]
        limits = frame([faces for _, _, _, faces in shaded])
        figure = plt.figure(figsize=(13.2, 9.8), facecolor="#12100f")
        for index, (title, view, elev, faces) in enumerate(shaded):
            axes = figure.add_subplot(2, 4, index + 1, projection="3d")
            axes.set_facecolor("#12100f")
            panel(axes, faces, title, view, elev, limits)
        figure.suptitle("CANGLAN WARLORD - sword phase one, spear phase two "
                        "(real geo.json + drawn wind arsenal blade)",
                        color="white", fontsize=16.5)
        figure.text(.5, .028, "Offline pose preview - panels 3-5 are the sword hit frames, "
                    "6-8 the spear hit frames; the weapon is the shared wind arsenal model",
                    ha="center", color="#bfa98a", fontsize=9.5)
    else:
        specs = [
            ("FRONT / mask, cuirass, sword", 1, "front", 4),
            ("SIDE / weapon arm, crest, cape", 1, "side", 4),
            ("BACK / cape, banner, pauldrons", 1, "back", 4),
            ("ABOVE 3/4 / pauldrons, banner", 1, "threequarter", 32),
            ("HEAD / mask, horns, cloth crest", None, "threequarter", 8),
            ("PHASE 2 / spear / open-hand guard", 2, "threequarter", 6),
        ]
        pose = sample(clips, "idle", 1.0)
        body, matrices, bones = build_faces(geometry, texture, pose)
        shaded = [(title, view, elev,
                   shade(body if form is None else with_weapon(body, matrices, bones, form),
                         view_rot(view)))
                  for title, form, view, elev in specs]
        body_limits = frame([faces for title, _, _, faces in shaded[:4]]
                            + [shaded[5][3]])
        figure = plt.figure(figsize=(10.8, 10.8), facecolor="#12100f")
        for index, (title, view, elev, faces) in enumerate(shaded):
            axes = figure.add_subplot(2, 3, index + 1, projection="3d")
            axes.set_facecolor("#12100f")
            panel(axes, faces, title, view, elev, head if index == 4 else body_limits)
        figure.suptitle("CANGLAN WARLORD - relic steel, cyan edge, mist cloth, gale glow",
                        color="white", fontsize=17.5)
        figure.text(.5, .028, "Offline mesh preview - real geo.json + real atlas samples - "
                    "hitbox 2.8 x 6.4 blocks",
                    ha="center", color="#bfa98a", fontsize=9.5)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(OUT, dpi=145, facecolor=figure.get_facecolor(), bbox_inches="tight")
    print(OUT)


if __name__ == "__main__":
    main()
