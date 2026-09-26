"""Read building blueprints without loading WorldEdit or mutating source assets.

Supports Sponge v2/v3 and Litematica packed palettes, including negative region
sizes. Source metadata is retained in reports; newer blocks are never silently
accepted as compatible with Minecraft 1.20.1.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import nbtlib
import numpy as np

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}


def state_text(tag):
    props = tag.get("Properties", {})
    return str(tag["Name"]) + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")


def varints(data, count):
    raw = np.asarray(data, dtype=np.int8).view(np.uint8)
    if not np.any(raw & 128):
        if len(raw) != count:
            raise ValueError(f"Block count mismatch: {len(raw)} != {count}")
        return raw.astype(np.uint16)
    result = np.empty(count, dtype=np.uint16)
    value = shift = index = 0
    for byte in raw:
        byte = int(byte)
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            if shift > 28:
                raise ValueError("Invalid VarInt")
        else:
            if index >= count or value > 65535:
                raise ValueError("Invalid palette index/count")
            result[index] = value
            index += 1
            value = shift = 0
    if index != count or shift:
        raise ValueError("Truncated block data")
    return result


def read_blueprint(path):
    root = nbtlib.load(path)
    root = root.get("Schematic", root)
    if "Regions" in root:
        if len(root["Regions"]) != 1:
            raise ValueError("Multiple Litematica regions require explicit assembly")
        region = next(iter(root["Regions"].values()))
        dims = tuple(abs(int(region["Size"][k])) for k in ("x", "y", "z"))
        palette = [state_text(t) for t in region["BlockStatePalette"]]
        bits = max(2, (len(palette) - 1).bit_length())
        raw = np.asarray(region["BlockStates"], dtype=np.int64).view(np.uint64)
        count = int(np.prod(dims))
        idx = np.arange(count, dtype=np.uint64) * bits
        word = idx // 64
        shift = idx % 64
        padded = np.append(raw, np.uint64(0))
        values = (padded[word] >> shift)
        crossed = shift + bits > 64
        values[crossed] |= padded[word[crossed] + 1] << (64 - shift[crossed])
        values = (values & ((1 << bits) - 1)).astype(np.uint16)
        # Litematica arrays are ordered from the minimum corner, even for negative Size.
        blocks = values.reshape((dims[1], dims[2], dims[0]))
        return blocks, palette, int(root.get("MinecraftDataVersion", 0)), list(region.get("TileEntities", []))
    dims = tuple(int(root[k]) for k in ("Width", "Height", "Length"))
    container = root.get("Blocks", root)
    pal = container["Palette"]
    palette = [""] * len(pal)
    for name, index in pal.items():
        palette[int(index)] = str(name)
    data = container["Data"] if "Blocks" in root else root["BlockData"]
    blocks = varints(data, int(np.prod(dims))).reshape((dims[1], dims[2], dims[0]))
    return blocks, palette, int(root.get("DataVersion", 0)), list(container.get("BlockEntities", []))


def audit(path):
    blocks, palette, version, entities = read_blueprint(path)
    if int(blocks.max()) >= len(palette):
        raise ValueError("Palette index out of bounds")
    solid = np.array([s.split("[")[0] not in AIR for s in palette])[blocks]
    ys = np.flatnonzero(np.any(solid, axis=(1, 2)))
    zs = np.flatnonzero(np.any(solid, axis=(0, 2)))
    xs = np.flatnonzero(np.any(solid, axis=(0, 1)))
    bounds = [[int(v[0]), int(v[-1])] for v in (xs, ys, zs)]
    counts = np.bincount(blocks.ravel(), minlength=len(palette))
    return {"file": str(path), "data_version": version,
            "dimensions": [blocks.shape[2], blocks.shape[0], blocks.shape[1]],
            "occupied_bounds": bounds, "occupied_dimensions": [b - a + 1 for a, b in bounds],
            "non_air_blocks": int(solid.sum()), "block_entities": len(entities),
            "palette": {s: int(n) for s, n in zip(palette, counts) if n}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    reports = []
    for path in args.paths:
        report = audit(path)
        reports.append(report)
        print(path.name, report["occupied_dimensions"], report["non_air_blocks"], flush=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(reports, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
