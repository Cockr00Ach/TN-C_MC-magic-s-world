"""Offline, fail-closed imports into the terrain-aware 1.20.1 building pipeline.

Does not touch the source or install WorldEdit. Re-run only into a new output
directory when changing source bounds; manifests enumerate the exact pieces.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import zipfile
from copy import deepcopy
from pathlib import Path
import nbtlib
import numpy as np
from building_assets import AIR, read_blueprint
from export_town_structures import write_piece

REMAP = {
    "iron_chain": "chain",
    "short_grass": "grass", "pale_moss_block": "moss_block",
    "polished_tuff": "polished_andesite", "tuff_bricks": "stone_bricks",
    "chiseled_tuff": "chiseled_stone_bricks", "chiseled_tuff_bricks": "chiseled_stone_bricks",
    **{f"{a}_{b}": f"{c}_{b}" for a, c in
       (("tuff", "cobblestone"), ("polished_tuff", "stone_brick"), ("tuff_brick", "stone_brick"))
       for b in ("stairs", "slab", "wall")},
}
FORBIDDEN = {"command_block", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw"}


def compatible(state):
    name, sep, props = state.partition("[")
    path = name.removeprefix("minecraft:")
    if path in FORBIDDEN:
        return "minecraft:chiseled_stone_bricks"
    name = "minecraft:" + REMAP[path] if path in REMAP else name
    # Shape and orientation properties survive material replacement.
    return name + (sep + props if sep else "")


def dilate(mask, distance):
    mask = mask.copy()
    for _ in range(distance):
        old = mask.copy()
        mask[1:] |= old[:-1]
        mask[:-1] |= old[1:]
        mask[:, 1:] |= old[:, :-1]
        mask[:, :-1] |= old[:, 1:]
    return mask


def runs(mask):
    result = []
    for row in mask:
        edges = np.diff(np.pad(row.astype(np.int8), (1, 1)))
        result.append([[int(a), int(b - 1)] for a, b in zip(np.flatnonzero(edges == 1), np.flatnonzero(edges == -1))])
    return result


def block_entities(tags):
    result = {}
    for tag in tags:
        position = tuple(int(v) for v in tag["Pos"]) if "Pos" in tag else tuple(int(tag[k]) for k in ("x", "y", "z"))
        value = deepcopy(tag.get("Data", tag))
        identity = str(tag.get("Id", tag.get("id", value.get("id", ""))))
        # Decorative NBT only: no source command execution, imported loot, or spawners.
        if not any(identity.endswith(s) for s in ("sign", "banner", "skull", "decorated_pot")):
            continue
        for key in ("Pos", "Id", "x", "y", "z", "Items", "LootTable", "LootTableSeed"):
            value.pop(key, None)
        value["id"] = nbtlib.String(identity)
        result[position] = value
    return result


def export(source, output, asset, vanilla_jar, spacing=48):
    blocks, original, version, entities = read_blueprint(source)
    palette = [compatible(s) for s in original]
    with zipfile.ZipFile(vanilla_jar) as jar:
        names = {"minecraft:" + p.split("/")[-1][:-5] for p in jar.namelist()
                 if p.startswith("assets/minecraft/blockstates/") and p.endswith(".json")} | AIR
    unknown = sorted({s.split("[")[0] for s in palette} - names)
    if unknown:
        raise ValueError(f"Unmapped blocks (nothing exported): {unknown}")
    solid = np.array([s.split("[")[0] not in AIR for s in palette])[blocks]
    axes = [np.flatnonzero(np.any(solid, axis=a)) for a in ((0, 1), (0, 2), (1, 2))]
    x0, z0, y0 = [int(v[0]) for v in axes]
    x1, z1, y1 = [int(v[-1]) + 1 for v in axes]
    margin = 24
    dims = [x1-x0+margin*2, y1-y0, z1-z0+margin*2]
    if dims[0] > 272 or dims[2] > 272 or dims[1] > 256:
        raise ValueError(f"Requires large-landmark pipeline, not native building: {dims}")
    cropped = blocks[y0:y1, z0:z1, x0:x1]
    mask = np.pad(np.any(solid[y0:y1, z0:z1, x0:x1], axis=0), margin)
    be = block_entities(entities)
    pieces = []
    for y in range(0, dims[1], 32):
        for z in range(0, cropped.shape[1], 32):
            for x in range(0, cropped.shape[2], 32):
                tile = cropped[y:y+32, z:z+32, x:x+32]
                records = []
                for ly, lz, lx in np.argwhere(np.array([s.split("[")[0] not in AIR for s in palette])[tile]):
                    lx, ly, lz = int(lx), int(ly), int(lz)
                    records.append((lx, ly, lz, palette[int(tile[ly, lz, lx])], be.get((x0+x+lx, y0+y+ly, z0+z+lz))))
                if not records:
                    continue
                name = f"piece_{x}_{y}_{z}"
                path = output / "structures" / "buildings" / asset / (name + ".nbt")
                size = (tile.shape[2], tile.shape[0], tile.shape[1])
                count, bes = write_piece(path, size, records, min(3465, version))
                pieces.append(dict(resource=f"tnc:buildings/{asset}/{name}", offset=[x+margin, y, z+margin],
                                   size=list(size), blocks=count, block_entities=bes))
    manifest = dict(dimensions=dims, anchor_local=[dims[0]//2, 0, dims[2]//2],
                    mask_rows=runs(mask), terrain_mask_rows=runs(dilate(mask, margin)),
                    pieces=pieces, piece_count=len(pieces), source_file=source.name,
                    source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(), source_data_version=version,
                    source_bounds=[x0, y0, z0, x1-1, y1-1, z1-1],
                    palette_replacements={a:b for a,b in zip(original,palette) if a != b},
                    policy="Decorative block entities only; no imported inventories, mobs or commands")
    write_json(output / "buildings" / (asset + ".json"), manifest)
    write_json(output / "worldgen/structure" / (asset + ".json"),
               dict(type="tnc:imported_building", asset=asset, biomes="#tnc:has_structure/imported_buildings",
                    step="surface_structures", spawn_overrides={}, terrain_adaptation="none"))
    write_json(output / "worldgen/structure_set" / (asset + ".json"),
               dict(structures=[dict(structure="tnc:"+asset, weight=1)], placement=dict(type="minecraft:random_spread",
                    salt=int(hashlib.sha256(asset.encode()).hexdigest()[:7],16), spacing=spacing, separation=spacing//2)))
    write_json(output / "tags/worldgen/biome/has_structure/imported_buildings.json",
               dict(replace=False, values=["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:birch_forest", "minecraft:taiga"]))
    print(f"{asset}: {dims}, {len(pieces)} pieces, {sum(p['blocks'] for p in pieces)} blocks", flush=True)
    return manifest


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("asset")
    parser.add_argument("--vanilla-jar", required=True, type=Path)
    parser.add_argument("--spacing", type=int, default=48)
    args = parser.parse_args()
    export(args.source, args.output, args.asset, args.vanilla_jar, args.spacing)
