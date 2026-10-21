"""The wind arsenal sword and spear, placed on a rig bone, for offline previews.

The Canglan Warlord does not model a weapon: the renderer draws the player's own
wind arsenal blade at the empty ``weapon_1`` bone, so the boss swings the exact
sword (form 1) and spear (form 2) the wind spell chain conjures. This module
reproduces that placement outside the game -- same anchor bone, same rotation,
same scale -- so the previews and the rig audit see the weapon the player sees.

Coordinates: the returned polygons are in *geometry units* (1 block = 16 units,
the unit space of the bones), because that is what the rest of the offline tools
work in. Item models are authored in 1/16-block pixels, so everything here is
converted on the way out.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tnc"
TEXTURE = ASSETS / "textures/item/wind_relic_metal.png"

# form -> (asset name, scale, shift along the haft, X rotation, Z rotation)
# Mirrors CanglanWarlordRenderer.drawWeapon; the shift seats the fist on the haft
# instead of on the butt cap.
GEAR = {
    1: ("sword", 2.10, 0.25, -76.0, -8.0),
    2: ("spear", 2.40, 0.55, -80.0, 4.0),
}

FACES = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]),
                           (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]),
                           (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]),
                          (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]),
                          (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "up": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]),
                        (b[0], b[1], a[2]), (a[0], b[1], a[2])],
    "down": lambda a, b: [(a[0], a[1], a[2]), (b[0], a[1], a[2]),
                          (b[0], a[1], b[2]), (a[0], a[1], b[2])],
}


def _rotation(degrees):
    x, y, z = np.radians(degrees)
    cx, sx = np.cos(x), np.sin(x)
    cy, sy = np.cos(y), np.sin(y)
    cz, sz = np.cos(z), np.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def _axis_rotation(axis, degrees):
    if abs(degrees) < 1e-6:
        return np.eye(3)
    u = np.array({"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[axis.lower()], dtype=float)
    a = np.radians(degrees)
    c, s = np.cos(a), np.sin(a)
    cross = np.array([[0, -u[2], u[1]], [u[2], 0, -u[0]], [-u[1], u[0], 0]])
    return c * np.eye(3) + s * cross + (1 - c) * np.outer(u, u)


_LOADED = {}


def load(form):
    """The arsenal model, its texture and its grip numbers (cached per form)."""
    if form in _LOADED:
        return _LOADED[form]
    name, scale, shift, rx, rz = GEAR[form]
    model = json.loads((ASSETS / f"models/wind/arsenal/{name}.json")
                       .read_text(encoding="utf-8"))
    texture = np.asarray(Image.open(TEXTURE).convert("RGB"), dtype=float) / 255.0
    _LOADED[form] = (model, texture, scale, shift, rx, rz)
    return _LOADED[form]


def _element_quads(element):
    a = np.array(element["from"], dtype=float)
    b = np.array(element["to"], dtype=float)
    rotation = element.get("rotation")
    quads = {}
    for face, builder in FACES.items():
        spec = element["faces"].get(face)
        if spec is None:
            continue
        points = np.array(builder(a, b), dtype=float)
        if rotation:
            origin = np.array(rotation["origin"], dtype=float)
            rot = _axis_rotation(rotation["axis"], rotation["angle"])
            points = (rot @ (points - origin).T).T + origin
        quads[face] = (points, spec.get("uv"))
    return quads


def _translate(vector):
    matrix = np.eye(4)
    matrix[:3, 3] = vector
    return matrix


def _anchor(matrices, bones, name):
    """A bone matrix shifted so (0,0,0) is the bone's own pivot, in geometry units."""
    pivot = np.array(bones[name].get("pivot", [0, 0, 0]), dtype=float)
    return matrices[name] @ _translate(pivot)


def place(form, matrices, bones, name="weapon_1"):
    """World polygons for one weapon form, in geometry units.

    ``matrices`` maps bone name -> 4x4 model-space matrix (the same convention the
    previewer and the rig audit build); ``bones`` is the geometry's bone table.
    """
    return place_at(form, _anchor(matrices, bones, name))


def anchor_for(matrices, bones, x, z, name="weapon_1"):
    """The anchor matrix for an arbitrary wrist (x, z) without rebuilding the rig.

    ``M(bone) = M(parent) @ T(offset) @ T(pivot) @ R(rot) @ T(-pivot)``, so the
    anchor (``M(weapon_1) @ T(pivot)``) is just ``M(parent) @ T(offset) @ T(pivot)
    @ R(x, 0, z)``. Solvers can therefore pivot one wrist angle in a few matrix
    multiplies instead of rebuilding fifty bones per candidate.
    """
    entry = bones[name]
    pivot = np.array(entry["pivot"], dtype=float)
    offset = np.zeros(3)
    base = matrices[entry["parent"]] @ _translate(offset) @ _translate(pivot)
    rotation = np.eye(4)
    rotation[:3, :3] = _rotation((x, 0.0, z))
    return base @ rotation


_SHAPED = {}


def _shaped(form):
    """All weapon quads in the weapon's own space, ready to be put on an anchor."""
    if form in _SHAPED:
        return _SHAPED[form]
    model, texture, scale, shift, rx, rz = load(form)
    rotation = _rotation((rx, 0.0, rz))
    placement = rotation @ np.array([0.0, 0.0, shift])
    points = []
    colours = []
    for element in model.get("elements", []):
        for face, (quad, uv) in _element_quads(element).items():
            blocks = quad / 16.0 - 0.5            # item model space -> blocks
            shaped = (rotation @ (blocks * scale).T).T + placement
            points.append(shaped * 16.0)          # blocks -> geometry units
            colours.append(_uv_colour(texture, uv))
    _SHAPED[form] = (np.concatenate(points), colours)
    return _SHAPED[form]


def place_at(form, anchor):
    """World polygons for one weapon form, given an already-built anchor matrix."""
    points, colours = _shaped(form)
    world = (anchor @ np.column_stack(
        [points, np.ones(len(points))]).T).T[:, :3]
    return [(world[index * 4:index * 4 + 4], colour, "blade")
            for index, colour in enumerate(colours)]


def _uv_colour(texture, uv):
    """Mean colour of the item-model uv patch (0..16 across the texture)."""
    if not uv:
        return np.array([0.55, 0.72, 0.74])
    u1, v1, u2, v2 = uv
    h, w = texture.shape[:2]
    x0, x1 = sorted(int(round(u * w / 16.0)) for u in (u1, u2))
    y0, y1 = sorted(int(round(v * h / 16.0)) for v in (v1, v2))
    patch = texture[max(0, y0):max(y0 + 1, min(h, y1)), max(0, x0):max(x0 + 1, min(w, x1))]
    return patch.mean(axis=(0, 1)) if patch.size else np.array([0.55, 0.72, 0.74])


def tip(form, matrices, bones, name="weapon_1"):
    """The blade point: (world point, distance from the weapon bone's pivot)."""
    anchor = _anchor(matrices, bones, name)
    model, _texture, scale, shift, rx, rz = load(form)
    rotation = _rotation((rx, 0.0, rz))
    placement = rotation @ np.array([0.0, 0.0, shift])
    points = []
    for element in model.get("elements", []):
        a = np.array(element["from"], dtype=float)
        b = np.array(element["to"], dtype=float)
        corners = np.array([[b[0] if i & 1 else a[0],
                             b[1] if i & 2 else a[1],
                             b[2] if i & 4 else a[2]] for i in range(8)])
        if element.get("rotation"):
            spec = element["rotation"]
            origin = np.array(spec["origin"], dtype=float)
            corners = (_axis_rotation(spec["axis"], spec["angle"])
                       @ (corners - origin).T).T + origin
        points.append(corners)
    corners = np.concatenate(points)
    # the blade tip is the vertex furthest along the model's own +Z axis
    index = int(np.argmax(corners[:, 2]))
    geometry = (rotation @ ((corners / 16.0 - 0.5) * scale).T).T + placement
    world = (anchor @ np.column_stack(
        [geometry * 16.0, np.ones(len(geometry))]).T).T[:, :3]
    point = world[index]
    pivot = (anchor @ np.append(np.zeros(3), 1.0))[:3]
    return point, float(np.linalg.norm(point - pivot))
