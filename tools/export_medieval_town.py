"""Export a rectangular area from a 1.19+/1.20 Anvil world to Sponge v2.

This is an intermediate, read-only extraction tool for the Medieval Town
world.  It deliberately exports only block states in the selected box; block
entities are reported but not copied yet, so the first output can be checked
for dimensions and block compatibility before we build the final structure.

Requires: pip install nbtlib anvil-parser
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import anvil
import nbtlib


class ModernChunk:
    """Small reader for the 1.18+ lowercase Anvil chunk format."""

    def __init__(self, nbt_data):
        self.data = nbt_data
        self.tile_entities = nbt_data.get("block_entities", [])
        self._sections = {int(section["Y"].value): section for section in nbt_data.get("sections", [])}

    def get_block(self, x: int, y: int, z: int) -> anvil.Block:
        section = self._sections.get(y // 16)
        if section is None or "block_states" not in section:
            return anvil.Block.from_name("minecraft:air")
        states = section["block_states"]
        palette = states["palette"]
        palette_blocks = [anvil.Block.from_palette(item) for item in palette]
        if len(palette_blocks) == 1 or "data" not in states:
            return palette_blocks[0]
        bits = max((len(palette_blocks) - 1).bit_length(), 4)
        index = (y % 16) * 16 * 16 + z * 16 + x
        values = states["data"].value
        per_long = 64 // bits
        raw = values[index // per_long]
        if raw < 0:
            raw += 2**64
        palette_index = (raw >> ((index % per_long) * bits)) & ((1 << bits) - 1)
        return palette_blocks[palette_index]


def state_name(block: anvil.Block) -> str:
    if not block.properties:
        return block.name()
    props = ",".join(f"{key}={block.properties[key]}" for key in sorted(block.properties))
    return f"{block.name()}[{props}]"


def region_path(world: Path, cx: int, cz: int) -> Path:
    return world / "region" / f"r.{cx // 32}.{cz // 32}.mca"


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
    args = parser.parse_args()

    if not (args.x0 <= args.x1 and args.y0 <= args.y1 and args.z0 <= args.z1):
        parser.error("coordinate minimums must be <= maximums")

    width = args.x1 - args.x0 + 1
    height = args.y1 - args.y0 + 1
    length = args.z1 - args.z0 + 1
    volume = width * height * length
    if max(width, height, length) > 32767:
        parser.error("Sponge v2 dimensions exceed the signed short limit")

    region_cache: dict[tuple[int, int], anvil.Region] = {}
    chunk_cache: dict[tuple[int, int], ModernChunk | None] = {}
    palette: dict[str, int] = {"minecraft:air": 0}
    block_data = bytearray()
    tile_entities = 0

    def get_chunk(cx: int, cz: int) -> ModernChunk | None:
        key = (cx, cz)
        if key in chunk_cache:
            return chunk_cache[key]
        rkey = (cx // 32, cz // 32)
        if rkey not in region_cache:
            path = region_path(args.world, cx, cz)
            if not path.exists():
                region_cache[rkey] = None  # type: ignore[assignment]
            else:
                region_cache[rkey] = anvil.Region.from_file(str(path))
        region = region_cache[rkey]
        raw = None if region is None else region.chunk_data(cx % 32, cz % 32)
        chunk = None if raw is None else ModernChunk(raw)
        chunk_cache[key] = chunk
        return chunk

    # Sponge stores X/Z as the horizontal axes and Y as the vertical axis.
    for y in range(args.y0, args.y1 + 1):
        for z in range(args.z0, args.z1 + 1):
            for x in range(args.x0, args.x1 + 1):
                chunk = get_chunk(x // 16, z // 16)
                block = anvil.Block.from_name("minecraft:air") if chunk is None else chunk.get_block(x % 16, y, z % 16)
                name = state_name(block)
                index = palette.setdefault(name, len(palette))
                # Sponge v2 uses unsigned varints for palette indices.
                value = index
                while value > 0x7F:
                    block_data.append((value & 0x7F) | 0x80)
                    value >>= 7
                block_data.append(value)

    # Count block entities in the selected chunks for the next extraction pass.
    for chunk in chunk_cache.values():
        if chunk is None:
            continue
        for entity in chunk.tile_entities:
            x = int(entity.get("x", 0).value)
            y = int(entity.get("y", 0).value)
            z = int(entity.get("z", 0).value)
            if args.x0 <= x <= args.x1 and args.y0 <= y <= args.y1 and args.z0 <= z <= args.z1:
                tile_entities += 1

    palette_tag = nbtlib.Compound({name: nbtlib.Int(index) for name, index in palette.items()})
    root = nbtlib.Compound(
        {
            "Version": nbtlib.Int(2),
            "DataVersion": nbtlib.Int(3105),
            "Width": nbtlib.Short(width),
            "Height": nbtlib.Short(height),
            "Length": nbtlib.Short(length),
            "Palette": palette_tag,
            "BlockData": nbtlib.ByteArray(block_data),
            "BlockEntities": nbtlib.List[nbtlib.Compound]([]),
            "Metadata": nbtlib.Compound({"Note": nbtlib.String("Medieval Town intermediate export; block entities pending")}),
        }
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root).save(args.output, gzipped=True)

    report = {
        "bounds": {"x": [args.x0, args.x1], "y": [args.y0, args.y1], "z": [args.z0, args.z1]},
        "dimensions": [width, height, length],
        "volume": volume,
        "palette_size": len(palette),
        "encoded_blockdata_bytes": len(block_data),
        "block_entities_in_bounds": tile_entities,
        "output": str(args.output),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
