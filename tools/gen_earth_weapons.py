"""Turn existing TN-C weapon models into earth/stone versions used by the earth spells.

For every weapon listed below the script:
  * builds a 16x16 stone tile from the mod's amber stone, tinted per weapon and
    modulated by the weapon texture's own palette brightness,
  * recentres and uniformly rescales the model into the 0..16 item space,
  * rewrites all face textures to the new single texture,
and drops the result into models/projectile/earth_wpn_<name>.json plus a
textures/block/earth_weapon/<name>.png tile.

The projectile renderer treats +Z as the flight axis, which is how these held-item
models are authored, so flying weapons need no extra rotation; standing them up as
a column only needs a -90 degree X rotation.
"""
import copy
import json
import pathlib

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/tnc/models/item"
TEXTURES = ROOT / "src/main/resources/assets/tnc/textures"
OUT_MODELS = ROOT / "src/main/resources/assets/tnc/models/projectile"
OUT_TEXTURES = TEXTURES / "block/earth_weapon"
OUT_DARK_TEXTURES = TEXTURES / "block/earth_weapon_dark"
BOSS_PREFIX = "stone_lancer_earth_wpn_dark_"

# weapon model -> stone palette used to repaint it
WEAPONS = {
    "dragon_sword": "amber",
    "frost_katana": "pale",
    "sky_halberd": "grey",
    "doom_scythe": "basalt",
    "earthshaker_hammer": "brown",
    "crimson_warblade": "crimson",
    "star_rapier": "silver",
    "crescent_guandao": "amber",
    "fangtian_ji": "grey",
    "han_iron_jian": "silver",
    "iron_jian_mace": "brown",
    "monk_spade": "basalt",
    "qimei_staff": "brown",
    "red_tassel_spear": "crimson",
    "ring_pommel_dao": "grey",
    "willow_leaf_dao": "pale",
    "yue_bronze_jian": "amber",
}

FACES = {"north", "south", "east", "west", "up", "down"}

TINTS = {
    "amber": (1.18, 0.95, 0.62),
    "pale": (1.02, 1.02, 1.0),
    "grey": (0.94, 0.96, 0.99),
    "basalt": (0.55, 0.54, 0.58),
    "brown": (1.0, 0.84, 0.62),
    "crimson": (0.96, 0.64, 0.55),
    "silver": (1.04, 1.05, 1.06),
}
GAIN = {"amber": 1.0, "pale": 1.22, "grey": 0.95, "basalt": 0.62, "brown": 0.9, "crimson": 0.82, "silver": 1.0}


def stone_tile(mode: str, size: int = 16) -> np.ndarray:
    """Downscale the mod's amber stone into a tile and tint it."""
    stone = Image.open(TEXTURES / "block/earth_amber_stone.png").convert("RGB")
    w, h = stone.size
    side = min(w, h)
    stone = stone.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))
    tile = np.asarray(stone.resize((size, size), Image.LANCZOS), dtype=np.float32) / 255.0
    lum = tile @ np.array([0.299, 0.587, 0.114], dtype=np.float32)
    detail = 0.45 * lum[..., None] + 0.55 * tile  # keep the grain, tame the colour
    out = detail * np.array(TINTS[mode], dtype=np.float32) * GAIN[mode]
    return np.clip(out, 0.0, 1.0)


def palette_shading(src: Image.Image, size: int = 16) -> np.ndarray:
    """Brightness map (0..1) of the weapon texture, at tile resolution."""
    small = np.asarray(src.convert("RGB").resize((size, size), Image.LANCZOS), dtype=np.float32) / 255.0
    return np.clip(small @ np.array([0.299, 0.587, 0.114], dtype=np.float32), 0.0, 1.0)


def dark_tile(palette: str, src_tex: Image.Image, size: int = 16) -> np.ndarray:
    """Boss material: near-black stone body with gold on the blade edges and ornaments."""
    grain = stone_tile(palette, size)
    lum = palette_shading(src_tex, size)
    edge = np.clip((lum - 0.34) / 0.50, 0.0, 1.0)[..., None] ** 1.2
    # A few deterministic gold veins so a flat face still reads as black-and-gold.
    veins = (np.random.default_rng(20261106).random((size, size)) < 0.14)[..., None].astype(np.float32)
    gold = np.clip(edge * 0.85 + veins * 0.55, 0.0, 1.0)
    base = np.array([0.075, 0.070, 0.064], dtype=np.float32) + grain * 0.13
    metal = np.array([0.95, 0.72, 0.28], dtype=np.float32) * (0.65 + 0.55 * grain)
    return np.clip(base * (1.0 - gold) + metal * gold, 0.0, 1.0)


def recentre(model: dict) -> None:
    els = model["elements"]
    xs = [v for e in els for v in (e["from"][0], e["to"][0])]
    ys = [v for e in els for v in (e["from"][1], e["to"][1])]
    zs = [v for e in els for v in (e["from"][2], e["to"][2])]
    dx, dy, dz = max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)
    scale = 16.0 / max(dx, dy, dz)
    cx, cy, cz = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2, (max(zs) + min(zs)) / 2

    def move(v):
        return [(v[0] - cx) * scale + 8, (v[1] - cy) * scale + 8, (v[2] - cz) * scale + 8]

    for e in els:
        e["from"] = move(e["from"])
        e["to"] = move(e["to"])
        lo = [min(a, b) for a, b in zip(e["from"], e["to"])]
        hi = [max(a, b) for a, b in zip(e["from"], e["to"])]
        e["from"], e["to"] = lo, hi


def check(model: dict, name: str) -> None:
    """Mirror the checks vanilla's BlockElement deserializer performs."""
    els = model["elements"]
    if not els:
        raise SystemExit(f"{name}: no elements")
    for i, e in enumerate(els):
        faces = e.get("faces") or {}
        if not 1 <= len(faces) <= 6:
            raise SystemExit(f"{name}[{i}]: {len(faces)} faces")
        bad = set(faces) - FACES
        if bad:
            raise SystemExit(f"{name}[{i}]: bad faces {bad}")
        for side, face in faces.items():
            if "uv" not in face:
                raise SystemExit(f"{name}[{i}].{side}: no uv")
            if not str(face.get("texture", "")).startswith("#"):
                raise SystemExit(f"{name}[{i}].{side}: bad texture {face.get('texture')}")
        for axis in range(3):
            for bound in ("from", "to"):
                v = e[bound][axis]
                if not -16 - 1e-6 <= v <= 32 + 1e-6:
                    raise SystemExit(f"{name}[{i}].{bound}[{axis}]={v} out of range")
            if e["from"][axis] > e["to"][axis] + 1e-6:
                raise SystemExit(f"{name}[{i}]: from > to on axis {axis}")


def main() -> None:
    OUT_MODELS.mkdir(parents=True, exist_ok=True)
    OUT_TEXTURES.mkdir(parents=True, exist_ok=True)
    OUT_DARK_TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, palette in WEAPONS.items():
        src_tex = Image.open(TEXTURES / f"item/{name}.png")
        tile = stone_tile(palette) * (0.55 + 0.9 * palette_shading(src_tex)[..., None])
        tile = np.clip(tile, 0.0, 1.0)
        Image.fromarray((tile * 255).round()[..., :3].astype(np.uint8), "RGB").save(
            OUT_TEXTURES / f"{name}.png")
        dark = dark_tile(palette, src_tex)
        Image.fromarray((dark * 255).round()[..., :3].astype(np.uint8), "RGB").save(
            OUT_DARK_TEXTURES / f"{name}.png")

        model = json.loads((ITEMS / f"{name}.json").read_text(encoding="utf-8"))
        # vanilla refuses an element with zero faces; such cubes are invisible anyway
        dropped = sum(1 for e in model["elements"] if not e.get("faces"))
        model["elements"] = [e for e in model["elements"] if e.get("faces")]
        recentre(model)
        tex = f"tnc:block/earth_weapon/{name}"
        model["textures"] = {"0": tex, "particle": tex}
        for key in ("display", "credit", "tnc_grip", "texture_size", "parent", "gui_light", "overrides"):
            model.pop(key, None)
        model["ambientocclusion"] = True
        for e in model["elements"]:
            for face in e["faces"].values():
                face["texture"] = "#0"
        check(model, name)

        out = OUT_MODELS / f"earth_wpn_{name}.json"
        out.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")

        # Same geometry, boss material: used by the Stone Lancer's quake/volley attacks.
        boss = copy.deepcopy(model)
        boss_tex = f"tnc:block/earth_weapon_dark/{name}"
        boss["textures"] = {"0": boss_tex, "particle": boss_tex}
        check(boss, name)
        boss_out = OUT_MODELS / f"{BOSS_PREFIX}{name}.json"
        boss_out.write_text(json.dumps(boss, separators=(",", ":")), encoding="utf-8")
        print(f"{name:24s} els={len(model['elements']):5d} dropped={dropped:3d} -> {out.stat().st_size / 1024:7.1f} KB + boss {boss_out.stat().st_size / 1024:7.1f} KB")


if __name__ == "__main__":
    main()
