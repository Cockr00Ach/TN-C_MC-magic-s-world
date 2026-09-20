"""Export a bounded area of a modern Anvil world as structure-template pieces.

The exporter reads 1.18+ chunk palettes directly, keeps block states and block
entity NBT, skips air, and writes pieces no larger than Minecraft's 48-block
structure-template limit.  It processes one horizontal piece at a time so a
large town does not have to be held in memory at once.
"""

from __future__ import annotations

import argparse
import json
import math
from functools import lru_cache
from pathlib import Path
from typing import Any

import anvil
import nbtlib


AIR_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def int_list(values: list[int]) -> nbtlib.List:
    return nbtlib.List[nbtlib.Int]([nbtlib.Int(value) for value in values])


def state_text(entry: Any) -> str:
    name = str(entry["Name"])
    properties = entry.get("Properties")
    if not properties:
        return name
    pairs = ",".join(
        f"{key}={properties[key].value if hasattr(properties[key], 'value') else str(properties[key])}"
        for key in sorted(properties.keys())
    )
    return f"{name}[{pairs}]"


def state_tag(text: str) -> nbtlib.Compound:
    if "[" not in text:
        return nbtlib.Compound({"Name": nbtlib.String(text)})
    name, encoded = text[:-1].split("[", 1)
    properties = {}
    for pair in encoded.split(","):
        key, value = pair.split("=", 1)
        properties[key] = nbtlib.String(value)
    return nbtlib.Compound(
        {
            "Name": nbtlib.String(name),
            "Properties": nbtlib.Compound(properties),
        }
    )


def convert_nbt_tag(tag: Any) -> Any:
    """Convert python-nbt tags returned by anvil-parser into nbtlib tags."""

    kind = type(tag).__name__
    scalar_types = {
        "TAG_Byte": nbtlib.Byte,
        "TAG_Short": nbtlib.Short,
        "TAG_Int": nbtlib.Int,
        "TAG_Long": nbtlib.Long,
        "TAG_Float": nbtlib.Float,
        "TAG_Double": nbtlib.Double,
        "TAG_String": nbtlib.String,
    }
    if kind in scalar_types:
        return scalar_types[kind](tag.value)
    if kind == "TAG_Byte_Array":
        return nbtlib.ByteArray(tag.value)
    if kind == "TAG_Int_Array":
        return nbtlib.IntArray(tag.value)
    if kind == "TAG_Long_Array":
        return nbtlib.LongArray(tag.value)
    if kind == "TAG_Compound":
        return nbtlib.Compound({child.name: convert_nbt_tag(child) for child in tag.tags})
    if kind == "TAG_List":
        converted = [convert_nbt_tag(child) for child in tag.tags]
        if not converted:
            return nbtlib.List[nbtlib.Compound]([])
        return nbtlib.List[type(converted[0])](converted)
    raise TypeError(f"unsupported NBT tag type: {kind}")


def section_palette(section: Any) -> tuple[list[str], list[int] | None]:
    states = section["block_states"]
    palette = [state_text(entry) for entry in states["palette"]]
    if len(palette) == 1 or "data" not in states:
        return palette, None
    values = [value if value >= 0 else value + 2**64 for value in states["data"].value]
    return palette, values


def palette_index(index: int, palette_size: int, values: list[int] | None) -> int:
    if values is None:
        return 0
    bits = max((palette_size - 1).bit_length(), 4)
    per_long = 64 // bits
    raw = values[index // per_long]
    return (raw >> ((index % per_long) * bits)) & ((1 << bits) - 1)


def write_piece(
    path: Path,
    size: tuple[int, int, int],
    blocks: list[tuple[int, int, int, str, nbtlib.Compound | None]],
    data_version: int,
) -> tuple[int, int]:
    palette_names: list[str] = []
    palette_ids: dict[str, int] = {}
    block_tags: list[nbtlib.Compound] = []
    block_entity_count = 0

    for x, y, z, state, block_entity in blocks:
        if state not in palette_ids:
            palette_ids[state] = len(palette_names)
            palette_names.append(state)
        block_tag: dict[str, Any] = {
            "pos": int_list([x, y, z]),
            "state": nbtlib.Int(palette_ids[state]),
        }
        if block_entity is not None:
            block_tag["nbt"] = block_entity
            block_entity_count += 1
        block_tags.append(nbtlib.Compound(block_tag))

    root = nbtlib.Compound(
        {
            # Keep the source version so the target game can data-fix 1.19
            # block entities such as signs into their 1.20.1 representation.
            "DataVersion": nbtlib.Int(data_version),
            "size": int_list(list(size)),
            "palette": nbtlib.List[nbtlib.Compound]([state_tag(state) for state in palette_names]),
            "blocks": nbtlib.List[nbtlib.Compound](block_tags),
            "entities": nbtlib.List[nbtlib.Compound]([]),
        }
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root).save(path, gzipped=True)
    return len(blocks), block_entity_count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--x0", type=int, required=True)
    parser.add_argument("--x1", type=int, required=True)
    parser.add_argument("--y0", type=int, required=True)
    parser.add_argument("--y1", type=int, required=True)
    parser.add_argument("--z0", type=int, required=True)
    parser.add_argument("--z1", type=int, required=True)
    parser.add_argument("--piece", type=int, default=48)
    args = parser.parse_args()

    if not (args.x0 <= args.x1 and args.y0 <= args.y1 and args.z0 <= args.z1):
        parser.error("coordinate minimums must be <= maximums")
    if args.piece < 1 or args.piece > 48:
        parser.error("--piece must be between 1 and 48")
    if not (args.world / "region").is_dir():
        parser.error(f"world region directory not found: {args.world / 'region'}")

    width = args.x1 - args.x0 + 1
    height = args.y1 - args.y0 + 1
    length = args.z1 - args.z0 + 1
    level_data = nbtlib.load(args.world / "level.dat")
    source_data_version = int(level_data["Data"]["DataVersion"])
    piece_x_count = math.ceil(width / args.piece)
    piece_y_count = math.ceil(height / args.piece)
    piece_z_count = math.ceil(length / args.piece)
    region_cache: dict[tuple[int, int], anvil.Region | None] = {}

    def get_region(rx: int, rz: int) -> anvil.Region | None:
        key = (rx, rz)
        if key not in region_cache:
            path = args.world / "region" / f"r.{rx}.{rz}.mca"
            region_cache[key] = anvil.Region.from_file(str(path)) if path.exists() else None
        return region_cache[key]

    @lru_cache(maxsize=96)
    def get_chunk(cx: int, cz: int) -> Any | None:
        region = get_region(cx // 32, cz // 32)
        if region is None:
            return None
        try:
            return region.chunk_data(cx % 32, cz % 32)
        except Exception:
            return None

    manifest: dict[str, Any] = {
        "source_world": args.world.name,
        "source_data_version": source_data_version,
        "source_bounds": {
            "x": [args.x0, args.x1],
            "y": [args.y0, args.y1],
            "z": [args.z0, args.z1],
        },
        "dimensions": [width, height, length],
        "piece_size": args.piece,
        "non_air_blocks": 0,
        "block_entities": 0,
        "pieces": [],
    }

    args.output.mkdir(parents=True, exist_ok=True)

    for px in range(piece_x_count):
        piece_x0 = args.x0 + px * args.piece
        piece_x1 = min(args.x1, piece_x0 + args.piece - 1)
        for pz in range(piece_z_count):
            piece_z0 = args.z0 + pz * args.piece
            piece_z1 = min(args.z1, piece_z0 + args.piece - 1)
            piece_blocks: dict[int, list[tuple[int, int, int, str, nbtlib.Compound | None]]] = {
                py: [] for py in range(piece_y_count)
            }

            for cx in range(piece_x0 // 16, piece_x1 // 16 + 1):
                for cz in range(piece_z0 // 16, piece_z1 // 16 + 1):
                    chunk = get_chunk(cx, cz)
                    if chunk is None:
                        continue
                    block_entities = {}
                    for entity in chunk.get("block_entities", []):
                        position = (int(entity["x"].value), int(entity["y"].value), int(entity["z"].value))
                        converted_entity = convert_nbt_tag(entity)
                        # StructureTemplate supplies the destination position
                        # when loading a block entity.  Match vanilla's own
                        # structure capture and discard source-world coords.
                        converted_entity.pop("x", None)
                        converted_entity.pop("y", None)
                        converted_entity.pop("z", None)
                        block_entities[position] = converted_entity

                    for section in chunk.get("sections", []):
                        section_y = int(section["Y"].value)
                        section_y0 = section_y * 16
                        section_y1 = section_y0 + 15
                        if section_y1 < args.y0 or section_y0 > args.y1 or "block_states" not in section:
                            continue
                        palette, values = section_palette(section)

                        for index in range(4096):
                            local_x = index & 15
                            local_z = (index >> 4) & 15
                            local_y = index >> 8
                            global_x = cx * 16 + local_x
                            global_y = section_y0 + local_y
                            global_z = cz * 16 + local_z
                            if not (
                                piece_x0 <= global_x <= piece_x1
                                and args.y0 <= global_y <= args.y1
                                and piece_z0 <= global_z <= piece_z1
                            ):
                                continue
                            state = palette[palette_index(index, len(palette), values)]
                            if state.split("[", 1)[0] in AIR_BLOCKS:
                                continue
                            py = (global_y - args.y0) // args.piece
                            piece_blocks[py].append(
                                (
                                    global_x - piece_x0,
                                    global_y - (args.y0 + py * args.piece),
                                    global_z - piece_z0,
                                    state,
                                    block_entities.get((global_x, global_y, global_z)),
                                )
                            )

            for py, blocks in piece_blocks.items():
                if not blocks:
                    continue
                size = (
                    piece_x1 - piece_x0 + 1,
                    min(args.piece, height - py * args.piece),
                    piece_z1 - piece_z0 + 1,
                )
                name = f"piece_{px}_{py}_{pz}"
                block_count, block_entity_count = write_piece(
                    args.output / f"{name}.nbt",
                    size,
                    blocks,
                    source_data_version,
                )
                manifest["non_air_blocks"] += block_count
                manifest["block_entities"] += block_entity_count
                manifest["pieces"].append(
                    {
                        "name": name,
                        "offset": [px * args.piece, py * args.piece, pz * args.piece],
                        "blocks": block_count,
                        "block_entities": block_entity_count,
                        "size": list(size),
                    }
                )
                print(
                    f"{name}: blocks={block_count} block_entities={block_entity_count}",
                    flush=True,
                )

    manifest["pieces"].sort(key=lambda item: item["name"])
    (args.output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
