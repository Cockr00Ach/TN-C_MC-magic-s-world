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
import json
import pathlib

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/tnc/models/item"
TEXTURES = ROOT / "src/main/resources/assets/tnc/textures"
OUT_MODELS = ROOT / "src/main/resources/assets/tnc/models/projectile"
OUT_TEXTURES = TEXTURES / "block/earth_weapon"

# weapon model -> stone palette used to repaint it
WEAPONS = {
    "dragon_sword": "amber",
    "frost_katana": "pale",
    "sky_halberd": "grey",
    "doom_scythe": "basalt",
    "earthshaker_hammer": "brown",
    "crimson_warblade": "crimson",
    "star_rapier": "silver",
}

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


def main() -> None:
    OUT_MODELS.mkdir(parents=True, exist_ok=True)
    OUT_TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, palette in WEAPONS.items():
        src_tex = Image.open(TEXTURES / f"item/{name}.png")
        tile = stone_tile(palette) * (0.55 + 0.9 * palette_shading(src_tex)[..., None])
        tile = np.clip(tile, 0.0, 1.0)
        Image.fromarray((tile * 255).round()[..., :3].astype(np.uint8), "RGB").save(
            OUT_TEXTURES / f"{name}.png")

        model = json.loads((ITEMS / f"{name}.json").read_text(encoding="utf-8"))
        recentre(model)
        tex = f"tnc:block/earth_weapon/{name}"
        model["textures"] = {"0": tex, "particle": tex}
        for key in ("display", "credit", "tnc_grip", "texture_size"):
            model.pop(key, None)
        model["ambientocclusion"] = True
        for e in model["elements"]:
            for face in e["faces"].values():
                face["texture"] = "#0"

        out = OUT_MODELS / f"earth_wpn_{name}.json"
        out.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
        print(f"{name:24s} els={len(model['elements']):5d} -> {out.stat().st_size / 1024:7.1f} KB")


if __name__ == "__main__":
    main()
