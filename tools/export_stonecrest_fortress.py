"""Extract the Stonecrest main fortress as tiled vanilla structure templates.

The source world is never modified.  The exporter keeps the connected main
castle around an anchor point, trims gardens/town terrain with an irregular
mask, remaps Fabric-only blocks, and emits 32-cubed structure NBT pieces plus
a manifest consumed by the TN-C Forge worldgen code.

Requires the same local tooling as export_medieval_town.py:
    pip install anvil-parser nbtlib
"""

from __future__ import annotations

import argparse
import json
import math
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import anvil
import nbtlib


DEFAULT_WORLD = Path(
    r"D:\赫萝斯堪德 资源 新\赫萝斯堪德 资源 新\赫萝斯堪德 资源\地图\4x4.1"
)

# The southern monumental fortress fits inside Minecraft's eight-chunk
# structure-reference radius.  The northern town/palace, round gardens and
# detached side landscaping are deliberately outside this export.
DEFAULT_BOUNDS = (2128, 2399, 888, 1159)
DEFAULT_Y = (128, 319)
DEFAULT_ANCHOR = (2264, 1150)
DEFAULT_GROUND_Y = 166
TILE_SIZE = 32
DATA_VERSION = 3465  # Minecraft 1.20.1

ARTIFICIAL_TOKENS = (
    "plaster", "stucco", "shingle", "brick", "beam", "door", "window",
    "arch", "railing", "balustrade", "cabinet", "cupboard", "chair",
    "table", "desk", "bed", "crate", "candle", "lantern", "tile",
    "roof", "panel", "plank", "frame", "fence", "pillar", "column",
    "carved", "stage", "wardrobe", "shelf", "amphora", "stool",
)


@dataclass(frozen=True)
class State:
    name: str
    properties: tuple[tuple[str, str], ...] = ()

    @classmethod
    def from_block(cls, block: anvil.Block) -> "State":
        return cls(
            block.name(),
            tuple(sorted((str(key), str(value)) for key, value in (block.properties or {}).items())),
        )

    @property
    def namespace(self) -> str:
        return self.name.split(":", 1)[0]

    @property
    def path(self) -> str:
        return self.name.split(":", 1)[-1]


AIR = State("minecraft:air")


class ModernChunk:
    """Reader for the 1.18+ lowercase Anvil chunk format."""

    def __init__(self, nbt_data):
        self.data = nbt_data
        self.block_entities = nbt_data.get("block_entities", [])
        self.sections = {int(section["Y"].value): section for section in nbt_data.get("sections", [])}
        self._decoded: dict[int, tuple[list[State], list[int] | None, int]] = {}

    def _section(self, section_y: int) -> tuple[list[State], list[int] | None, int] | None:
        if section_y in self._decoded:
            return self._decoded[section_y]
        section = self.sections.get(section_y)
        if section is None or "block_states" not in section:
            return None
        states = section["block_states"]
        palette = [State.from_block(anvil.Block.from_palette(item)) for item in states["palette"]]
        bits = max((len(palette) - 1).bit_length(), 4)
        packed = None if len(palette) == 1 or "data" not in states else list(states["data"].value)
        decoded = (palette, packed, bits)
        self._decoded[section_y] = decoded
        return decoded

    def state(self, x: int, y: int, z: int) -> State:
        decoded = self._section(y // 16)
        if decoded is None:
            return AIR
        palette, packed, bits = decoded
        if packed is None:
            return palette[0]
        index = (y & 15) * 256 + z * 16 + x
        per_long = 64 // bits
        raw = packed[index // per_long]
        if raw < 0:
            raw += 1 << 64
        palette_index = (raw >> ((index % per_long) * bits)) & ((1 << bits) - 1)
        return palette[palette_index] if palette_index < len(palette) else AIR


class WorldReader:
    def __init__(self, world: Path):
        self.world = world
        self.regions: dict[tuple[int, int], anvil.Region | None] = {}
        self.chunks: dict[tuple[int, int], ModernChunk | None] = {}

    def chunk(self, cx: int, cz: int) -> ModernChunk | None:
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        region_key = (cx // 32, cz // 32)
        if region_key not in self.regions:
            path = self.world / "region" / f"r.{region_key[0]}.{region_key[1]}.mca"
            self.regions[region_key] = anvil.Region.from_file(str(path)) if path.exists() else None
        region = self.regions[region_key]
        raw = None if region is None else region.chunk_data(cx % 32, cz % 32)
        result = None if raw is None else ModernChunk(raw)
        self.chunks[key] = result
        return result

    def state(self, x: int, y: int, z: int) -> State:
        chunk = self.chunk(x // 16, z // 16)
        return AIR if chunk is None else chunk.state(x & 15, y, z & 15)


def is_artificial(state: State) -> bool:
    if state.namespace not in ("minecraft", "conquest"):
        return True
    path = state.path
    if state.namespace == "minecraft":
        return any(token in path for token in (
            "brick", "plank", "door", "glass", "lantern", "torch", "wall",
            "stairs", "slab", "fence", "chain", "barrel", "chest", "bed",
        ))
    return any(token in path for token in ARTIFICIAL_TOKENS)


def replacement_for(state: State) -> State:
    """Return a block state that exists in the Forge pack."""
    if state.namespace in ("minecraft", "conquest"):
        return state

    path = state.path.lower()
    words = set(path.split("_"))
    if "barrel" in path:
        return State("minecraft:barrel")
    if "bed" in path:
        # A source double-bed model is frequently one block.  Mapping it to a
        # vanilla bed would create isolated head/foot halves, so use a stable
        # upholstery-like full block until bespoke Forge furniture is added.
        return State("minecraft:brown_wool")
    if any(word in path for word in ("lantern", "lamp", "candle")):
        return State("minecraft:lantern")
    if any(word in path for word in ("glass", "window")):
        return State("minecraft:glass_pane")
    if "brick" in path or words.intersection(("oven", "stove", "fireplace")):
        return State("minecraft:bricks")
    if any(word in path for word in ("chair", "stool")):
        return State("conquest:oak_chair")
    if any(word in path for word in ("table", "desk")):
        return State("conquest:oak_wood_table")
    if any(word in path for word in ("cabinet", "cupboard", "shelf", "wardrobe")):
        return State("conquest:cupboards")
    if "crate" in path:
        return State("conquest:empty_crate")
    if "door" in path:
        return State("minecraft:oak_planks")
    if "beam" in path or state.namespace == "architects":
        return State("conquest:apple_wood_beam_lintel")
    if state.namespace in ("hearthfire", "victorymod", "believemod"):
        return State("conquest:apple_wood_planks")
    return State("minecraft:stone_bricks")


def dilate(points: set[tuple[int, int]], radius: int, width: int, depth: int) -> set[tuple[int, int]]:
    result: set[tuple[int, int]] = set()
    offsets = [
        (dx, dz)
        for dx in range(-radius, radius + 1)
        for dz in range(-radius, radius + 1)
        if dx * dx + dz * dz <= radius * radius
    ]
    for x, z in points:
        for dx, dz in offsets:
            nx, nz = x + dx, z + dz
            if 0 <= nx < width and 0 <= nz < depth:
                result.add((nx, nz))
    return result


def connected_component(points: set[tuple[int, int]], anchor: tuple[int, int]) -> set[tuple[int, int]]:
    if not points:
        return set()
    start = min(points, key=lambda p: (p[0] - anchor[0]) ** 2 + (p[1] - anchor[1]) ** 2)
    result = {start}
    queue = deque([start])
    while queue:
        x, z = queue.popleft()
        for neighbor in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            if neighbor in points and neighbor not in result:
                result.add(neighbor)
                queue.append(neighbor)
    return result


def fill_small_holes(mask: set[tuple[int, int]], width: int, depth: int, limit: int = 2048) -> set[tuple[int, int]]:
    outside: set[tuple[int, int]] = set()
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        for z in (0, depth - 1):
            if (x, z) not in mask and (x, z) not in outside:
                outside.add((x, z)); queue.append((x, z))
    for z in range(depth):
        for x in (0, width - 1):
            if (x, z) not in mask and (x, z) not in outside:
                outside.add((x, z)); queue.append((x, z))
    while queue:
        x, z = queue.popleft()
        for nx, nz in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            if 0 <= nx < width and 0 <= nz < depth and (nx, nz) not in mask and (nx, nz) not in outside:
                outside.add((nx, nz)); queue.append((nx, nz))
    unvisited = {(x, z) for x in range(width) for z in range(depth) if (x, z) not in mask and (x, z) not in outside}
    result = set(mask)
    while unvisited:
        start = unvisited.pop()
        hole = {start}
        queue = deque([start])
        while queue:
            x, z = queue.popleft()
            for neighbor in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                if neighbor in unvisited:
                    unvisited.remove(neighbor)
                    hole.add(neighbor)
                    queue.append(neighbor)
        if len(hole) <= limit:
            result.update(hole)
    return result


def palette_tag(state: State) -> nbtlib.Compound:
    value = nbtlib.Compound({"Name": nbtlib.String(state.name)})
    if state.properties:
        value["Properties"] = nbtlib.Compound({k: nbtlib.String(v) for k, v in state.properties})
    return value


def write_template(path: Path, size: tuple[int, int, int], blocks: list[tuple[int, int, int, State]]) -> None:
    palette: list[State] = []
    palette_index: dict[State, int] = {}
    block_tags = nbtlib.List[nbtlib.Compound]()
    for x, y, z, state in blocks:
        index = palette_index.get(state)
        if index is None:
            index = len(palette)
            palette_index[state] = index
            palette.append(state)
        block_tags.append(nbtlib.Compound({
            "pos": nbtlib.List[nbtlib.Int]([x, y, z]),
            "state": nbtlib.Int(index),
        }))
    root = nbtlib.Compound({
        "DataVersion": nbtlib.Int(DATA_VERSION),
        "size": nbtlib.List[nbtlib.Int](list(size)),
        "palette": nbtlib.List[nbtlib.Compound]([palette_tag(state) for state in palette]),
        "blocks": block_tags,
        "entities": nbtlib.List[nbtlib.Compound](),
    })
    path.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root).save(path, gzipped=True)


def mask_rows(mask: set[tuple[int, int]], width: int, depth: int) -> list[list[list[int]]]:
    rows: list[list[list[int]]] = []
    for z in range(depth):
        runs: list[list[int]] = []
        x = 0
        while x < width:
            while x < width and (x, z) not in mask:
                x += 1
            if x >= width:
                break
            start = x
            while x < width and (x, z) in mask:
                x += 1
            runs.append([start, x - 1])
        rows.append(runs)
    return rows


def build_mask(reader: WorldReader, bounds: tuple[int, int, int, int], y_bounds: tuple[int, int], anchor: tuple[int, int]):
    x0, x1, z0, z1 = bounds
    y0, y1 = y_bounds
    width, depth = x1 - x0 + 1, z1 - z0 + 1
    seeds: set[tuple[int, int]] = set()
    source_counts: Counter[str] = Counter()
    unsupported_counts: Counter[str] = Counter()

    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            artificial = False
            for y in range(y0, y1 + 1):
                state = reader.state(x, y, z)
                if state != AIR:
                    source_counts[state.name] += 1
                    if state.namespace not in ("minecraft", "conquest"):
                        unsupported_counts[state.name] += 1
                artificial = artificial or is_artificial(state)
            if artificial:
                seeds.add((x - x0, z - z0))

    connected = connected_component(
        dilate(seeds, 9, width, depth),
        (anchor[0] - x0, anchor[1] - z0),
    )
    selected_seeds = seeds & connected
    # The 272-square source bounds already isolate the main fortress.  Keep
    # every connected architectural seed inside them so towers and wings are
    # not silently clipped a second time by a hard-coded inner rectangle.
    # The template mask hugs actual architecture.  A separate, wider terrain
    # mask is used at runtime for the feathered local-ground transition.
    building_mask = fill_small_holes(dilate(selected_seeds, 2, width, depth), width, depth)
    terrain_mask = dilate(building_mask, 24, width, depth)
    return selected_seeds, building_mask, terrain_mask, source_counts, unsupported_counts


def export(args: argparse.Namespace) -> dict:
    bounds = (args.x0, args.x1, args.z0, args.z1)
    y_bounds = (args.y0, args.y1)
    reader = WorldReader(args.world)
    seeds, mask, terrain_mask, source_counts, unsupported_counts = build_mask(
        reader, bounds, y_bounds, (args.anchor_x, args.anchor_z)
    )
    x0, x1, z0, z1 = bounds
    y0, y1 = y_bounds
    width, height, depth = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1

    report = {
        "source_world": str(args.world),
        "source_bounds": {"x": [x0, x1], "y": [y0, y1], "z": [z0, z1]},
        "dimensions": [width, height, depth],
        "anchor": [args.anchor_x, args.anchor_z],
        "seed_columns": len(seeds),
        "selected_columns": len(mask),
        "selected_fraction": round(len(mask) / (width * depth), 5),
        "terrain_transition_columns": len(terrain_mask),
        "source_block_top": source_counts.most_common(50),
        "fabric_only_blocks": unsupported_counts.most_common(),
    }
    if args.analyze_only:
        return report

    template_dir = args.output / "data" / "tnc" / "structures" / "stonecrest"
    if template_dir.exists():
        for old_piece in template_dir.glob("piece_*.nbt"):
            old_piece.unlink()

    pieces = []
    total_blocks = 0
    replacement_counts: Counter[str] = Counter()
    for tile_z in range(0, depth, TILE_SIZE):
        for tile_y in range(0, height, TILE_SIZE):
            for tile_x in range(0, width, TILE_SIZE):
                sx = min(TILE_SIZE, width - tile_x)
                sy = min(TILE_SIZE, height - tile_y)
                sz = min(TILE_SIZE, depth - tile_z)
                blocks: list[tuple[int, int, int, State]] = []
                for lz in range(sz):
                    for lx in range(sx):
                        column = (tile_x + lx, tile_z + lz)
                        if column not in mask:
                            continue
                        for ly in range(sy):
                            source = reader.state(
                                x0 + tile_x + lx,
                                y0 + tile_y + ly,
                                z0 + tile_z + lz,
                            )
                            if source == AIR:
                                continue
                            target = replacement_for(source)
                            if target != source:
                                replacement_counts[f"{source.name} -> {target.name}"] += 1
                            blocks.append((lx, ly, lz, target))
                if not blocks:
                    continue
                name = f"piece_{tile_x // TILE_SIZE}_{tile_y // TILE_SIZE}_{tile_z // TILE_SIZE}"
                path = template_dir / f"{name}.nbt"
                write_template(path, (sx, sy, sz), blocks)
                pieces.append({
                    "resource": f"tnc:stonecrest/{name}",
                    "offset": [tile_x, tile_y, tile_z],
                    "size": [sx, sy, sz],
                    "blocks": len(blocks),
                })
                total_blocks += len(blocks)

    manifest = {
        "version": 1,
        "source_bounds": {"x": [x0, x1], "y": [y0, y1], "z": [z0, z1]},
        "dimensions": [width, height, depth],
        "anchor_local": [args.anchor_x - x0, args.ground_y - y0, args.anchor_z - z0],
        "piece_count": len(pieces),
        "block_count": total_blocks,
        "mask_rows": mask_rows(mask, width, depth),
        "terrain_mask_rows": mask_rows(terrain_mask, width, depth),
        "pieces": pieces,
    }
    manifest_path = args.output / "data" / "tnc" / "stonecrest" / "manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report["piece_count"] = len(pieces)
    report["exported_blocks"] = total_blocks
    report["replacement_counts"] = replacement_counts.most_common()
    report["manifest"] = str(manifest_path)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--world", type=Path, default=DEFAULT_WORLD)
    parser.add_argument("--output", type=Path, default=Path("src/main/resources"))
    parser.add_argument("--x0", type=int, default=DEFAULT_BOUNDS[0])
    parser.add_argument("--x1", type=int, default=DEFAULT_BOUNDS[1])
    parser.add_argument("--z0", type=int, default=DEFAULT_BOUNDS[2])
    parser.add_argument("--z1", type=int, default=DEFAULT_BOUNDS[3])
    parser.add_argument("--y0", type=int, default=DEFAULT_Y[0])
    parser.add_argument("--y1", type=int, default=DEFAULT_Y[1])
    parser.add_argument("--anchor-x", type=int, default=DEFAULT_ANCHOR[0])
    parser.add_argument("--anchor-z", type=int, default=DEFAULT_ANCHOR[1])
    parser.add_argument("--ground-y", type=int, default=DEFAULT_GROUND_Y)
    parser.add_argument("--analyze-only", action="store_true")
    args = parser.parse_args()
    if not args.world.joinpath("level.dat").exists():
        parser.error(f"not a Minecraft world: {args.world}")
    if args.x0 > args.x1 or args.y0 > args.y1 or args.z0 > args.z1:
        parser.error("minimum coordinates must not exceed maximum coordinates")
    if not args.y0 <= args.ground_y <= args.y1:
        parser.error("ground Y must stay inside the exported vertical range")
    if max(args.x1 - args.x0 + 1, args.z1 - args.z0 + 1) > 272:
        parser.error("Stonecrest footprint must stay within Minecraft's 17-chunk structure reference diameter")
    return args


def main() -> None:
    args = parse_args()
    result = export(args)
    report_path = Path("work/stonecrest-export-report.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
