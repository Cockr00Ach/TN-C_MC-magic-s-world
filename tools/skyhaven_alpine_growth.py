"""Plant clusters of native ReTerraForged spruce, pine and birch in open highland terrain."""
from dataclasses import dataclass
import gzip
import io
from pathlib import PurePosixPath
from zipfile import ZipFile

import nbtlib as nbt
import numpy as np
from scipy import ndimage


@dataclass
class Tree:
    group: str
    variants: list
    radius: int


def tree_library(v, jar):
    groups = {}
    with ZipFile(jar) as archive:
        for name in sorted(archive.namelist()):
            path = PurePosixPath(name)
            if not name.startswith("data/reterraforged/structures/trees/") or path.suffix != ".nbt":
                continue
            species = path.parts[4]
            if species not in ("spruce", "birch", "pine"):
                continue
            data = nbt.File.parse(io.BytesIO(gzip.decompress(archive.read(name))))
            names = [str(state["Name"]) for state in data["palette"]]
            blocks = [(tuple(map(int, b["pos"])), int(b["state"])) for b in data["blocks"]
                      if names[int(b["state"])].endswith(("_log", "_leaves"))]
            base = min((pos for pos, i in blocks if names[i].endswith("_log")), key=lambda p: p[1])
            xyz = np.array([pos for pos, i in blocks], dtype=np.int16) - np.array(base, dtype=np.int16)
            state_indices = np.array([i for pos, i in blocks], dtype=np.int16)
            tree_group = "bush" if "bush" in path.stem else species
            variants = []
            for rotation in range(4):
                coords = xyz.copy()
                for turn in range(rotation):
                    coords[:, [0, 2]] = np.column_stack((-coords[:, 2], coords[:, 0]))
                palette = []
                for state in data["palette"]:
                    properties = {key: str(val) for key, val in state.get("Properties", {}).items()}
                    axis = properties.get("axis")
                    if rotation % 2 and axis in ("x", "z"):
                        properties["axis"] = "z" if axis == "x" else "x"
                    block = str(state["Name"])
                    if properties:
                        block += "[" + ",".join(f"{k}={val}" for k, val in sorted(properties.items())) + "]"
                    palette.append(v.id(block))
                variants.append((coords.T, np.array(palette, dtype=np.uint16)[state_indices]))
            radius = int(np.ceil(np.hypot(xyz[:, 0], xyz[:, 2]).max()))
            groups.setdefault(tree_group, []).append(Tree(tree_group, variants, radius))
    if not all(groups.get(group) for group in ("spruce", "birch", "pine", "bush")):
        raise ValueError("Missing native ReTerraForged forest structures")
    return groups


def habitat_field(rng, shape, spacing):
    """Smooth but irregular stands separated by clearings wider than a mature crown."""
    rows, columns = shape
    control = rng.random((rows // spacing + 4, columns // spacing + 4))
    zz, xx = np.indices(shape, dtype=np.float32) / spacing + 1
    return np.clip(ndimage.map_coordinates(control, [zz, xx], order=3), 0, 1)


def plant(v, x, z, h, plantable, wet, jar):
    """The [z,x] mask includes the grass surface at local Y=h and all protected columns."""
    rng = np.random.default_rng(20261121)
    if h.shape != v.blocks.shape[1:] or x.shape != h.shape or z.shape != h.shape:
        raise ValueError("Terrain and foliage grids differ")
    open_ground = np.asarray(plantable, dtype=bool) & ~wet & (h >= 0) & (h + 1 < v.blocks.shape[0])
    if not np.any(open_ground):
        return {}
    trees = tree_library(v, jar)
    stand = habitat_field(rng, h.shape, 43) * .72 + habitat_field(rng, h.shape, 16) * .28
    meadow = habitat_field(rng, h.shape, 25)
    water_distance = ndimage.distance_transform_edt(~wet) if np.any(wet) else np.full(h.shape, 1000)
    altitude = np.clip((h - 115.) / 40, 0, 1)
    forest = np.clip((stand - .32) / .46, 0, 1)
    density = (.0005 + .038 * forest ** 2) * (1 - .91 * altitude ** 2)
    candidates = np.argwhere(open_ground & (rng.random(h.shape) < density))
    rng.shuffle(candidates)
    occupied = np.zeros(h.shape, dtype=bool)
    stats = {"spruce": 0, "pine": 0, "birch": 0, "bush": 0, "groundcover": 0}
    soil = {v.id(s) for s in ("minecraft:grass_block[snowy=false]", "minecraft:podzol[snowy=false]", "minecraft:moss_block", "minecraft:dirt")}
    eligible = open_ground & np.isin(v.blocks[h.clip(0, v.blocks.shape[0]-1), z - z.min(), x - x.min()], list(soil))
    zz_max, xx_max = h.shape
    for iz, ix in candidates:
        if not eligible[iz, ix] or occupied[iz, ix]:
            continue
        tall = altitude[iz, ix]
        if rng.random() < .045 + tall * .67:
            group = "bush"
        elif rng.random() < .12 + tall * .30:
            group = "pine"
        elif rng.random() < (.65 if water_distance[iz, ix] < 20 else .38) * (1 - tall):
            group = "birch"
        else:
            group = "spruce"
        options = trees[group]
        tree = options[rng.integers(len(options))]
        (dx, dy, dz), states = tree.variants[rng.integers(4)]
        xx, zz, yy = ix + dx, iz + dz, h[iz, ix] + 1 + dy
        if xx.min() < 0 or xx.max() >= xx_max or zz.min() < 0 or zz.max() >= zz_max or yy.min() < 0 or yy.max() >= v.blocks.shape[0]:
            continue
        if not np.all(open_ground[zz, xx]) or np.any(v.blocks[yy, zz, xx] != 0):
            continue
        v.blocks[yy, zz, xx] = states
        radius = max(3, tree.radius + (2 if forest[iz, ix] < .45 else 0))
        z0, z1 = max(0, iz - radius), min(zz_max, iz + radius + 1)
        x0, x1 = max(0, ix - radius), min(xx_max, ix + radius + 1)
        nearby_z, nearby_x = np.ogrid[z0:z1, x0:x1]
        occupied[z0:z1, x0:x1] |= (nearby_z - iz) ** 2 + (nearby_x - ix) ** 2 <= radius ** 2
        stats[group] += 1

    # Group flowers by meadow, with ferns on shaded loam or the shoulders of streams.
    above = (h + 1).clip(0, v.blocks.shape[0]-1)
    iz, ix = np.indices(h.shape)
    clear = eligible & (v.blocks[above, iz, ix] == 0)
    chosen = clear & (rng.random(h.shape) < (.025 + .14 * meadow ** 2) * (1 - .75 * altitude))
    iz, ix = np.where(chosen)
    patch = meadow[iz, ix]
    shaded = occupied[iz, ix] | ((water_distance[iz, ix] < 14) & (forest[iz, ix] > .45))
    ids = np.full(iz.shape, v.id("minecraft:grass"), dtype=np.uint16)
    ids[shaded] = v.id("minecraft:fern")
    flowers = (~shaded) & (rng.random(len(iz)) < (.08 + .36 * patch ** 2)) & (patch > .48)
    flowerbed = np.digitize(stand[iz, ix], [.34, .52, .72])
    flower_ids = np.array([v.id("minecraft:" + name) for name in ("oxeye_daisy", "azure_bluet", "blue_orchid", "allium")])
    ids[flowers] = flower_ids[flowerbed[flowers]]
    v.blocks[above[iz, ix], iz, ix] = ids
    stats["groundcover"] = len(ix)
    print(f"Native RTF forest and meadow: {stats}", flush=True)
    return stats
