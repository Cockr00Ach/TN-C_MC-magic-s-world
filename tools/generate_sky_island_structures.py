"""Build the v5 starter-town sky island from the existing v4 town templates.

The runtime receives only ordinary Minecraft structure templates.  All expensive
shape work (irregular island edge, shell, roots, vegetation and portal pads) is
performed here so every world gets the same reviewed scene and does not need to
procedurally sculpt millions of blocks while a player is online.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import shutil
import tempfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import nbtlib
from PIL import Image, ImageDraw

from export_town_structures import AIR_BLOCKS, state_text, write_piece


DATA_VERSION = 3105
PIECE_SIZE = 48
ISLAND_SIZE = (520, 220, 520)
ISLAND_CENTER = (260, 260)
ISLAND_WORLD_Y = 90
SURFACE_Y = 84
TOWN_OFFSET = (120, 53, 92)
TOWN_KEEP_MIN_Y = 20
TOWN_DIMS = (281, 160, 337)
TOWN_CENTER_LOCAL = (120, 37, 168)
TOWN_ARRIVAL_LOCAL = (89, 37, 332)
SEED = 0x544E43_534B59

TOWN_BOUNDS = (
    TOWN_OFFSET[0],
    TOWN_OFFSET[0] + TOWN_DIMS[0] - 1,
    TOWN_OFFSET[2],
    TOWN_OFFSET[2] + TOWN_DIMS[2] - 1,
)

GRASS = "minecraft:grass_block[snowy=false]"
DIRT = "minecraft:dirt"
ROOTED_DIRT = "minecraft:rooted_dirt"
COARSE_DIRT = "minecraft:coarse_dirt"
MOSS = "minecraft:moss_block"
STONE = "minecraft:stone"
TUFF = "minecraft:tuff"
ANDESITE = "minecraft:andesite"
DEEPSLATE = "minecraft:deepslate[axis=y]"
DARK_OAK_LOG = "minecraft:dark_oak_log[axis=y]"
DARK_OAK_LEAVES = (
    "minecraft:dark_oak_leaves[distance=1,persistent=true,waterlogged=false]"
)
OAK_LOG = "minecraft:oak_log[axis=y]"
OAK_LEAVES = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"
AMETHYST = "minecraft:amethyst_block"
BUDDING_AMETHYST = "minecraft:budding_amethyst"
SEA_LANTERN = "minecraft:sea_lantern"


@dataclass(frozen=True)
class RootSpec:
    angle: float
    radial_start: float
    end_dx: float
    end_dz: float
    start_y: float
    end_y: float
    start_radius: float
    end_radius: float


def stable_noise(x: int, z: int, salt: int = 0) -> float:
    """Return deterministic smooth-ish noise in [-1, 1] without dependencies."""

    value = math.sin((x + salt * 17) * 0.031) * 0.43
    value += math.sin((z - salt * 29) * 0.027) * 0.31
    value += math.sin((x + z + salt * 7) * 0.013) * 0.18
    value += math.sin((x - z - salt * 11) * 0.071) * 0.08
    return max(-1.0, min(1.0, value))


def boundary_radius(angle: float) -> float:
    """Organic but bounded island radius; always fits the 520-block canvas."""

    variation = (
        math.sin(angle * 3.0 + 0.7) * 1.8
        + math.sin(angle * 5.0 - 1.1) * 1.2
    )
    return 256.0 + variation


def surface_height(x: int, z: int) -> int | None:
    cx, cz = ISLAND_CENTER
    dx = x - cx
    dz = z - cz
    distance = math.hypot(dx, dz)
    angle = math.atan2(dz, dx)
    radius = boundary_radius(angle)
    if distance > radius:
        return None

    x0, x1, z0, z1 = TOWN_BOUNDS
    if x0 <= x <= x1 and z0 <= z <= z1:
        return SURFACE_Y

    edge_drop = 0
    edge_distance = radius - distance
    if edge_distance < 18:
        edge_drop = round((18 - edge_distance) * 0.42)
    noise = round(stable_noise(x, z) * 4.5)
    return SURFACE_Y + noise - edge_drop


def shell_bottom(x: int, z: int, top: int) -> int:
    cx, cz = ISLAND_CENTER
    distance = math.hypot(x - cx, z - cz)
    angle = math.atan2(z - cz, x - cx)
    edge_distance = max(0.0, boundary_radius(angle) - distance)
    thickness = 10 + min(14, round(edge_distance / 13.0))
    thickness += round((stable_noise(x, z, 3) + 1.0) * 2.0)
    return max(55, top - thickness)


def shell_state(x: int, y: int, z: int, top: int, bottom: int) -> str:
    depth = top - y
    selector = abs((x * 7349) ^ (y * 9151) ^ (z * 3253) ^ SEED)
    if depth == 0:
        if selector % 29 == 0:
            return MOSS
        return GRASS
    if depth <= 3:
        if selector % 11 == 0:
            return ROOTED_DIRT
        if selector % 17 == 0:
            return COARSE_DIRT
        return DIRT
    if y - bottom <= 2 and selector % 5 == 0:
        return DEEPSLATE
    if selector % 13 == 0:
        return TUFF
    if selector % 9 == 0:
        return ANDESITE
    return STONE


def root_specs() -> list[RootSpec]:
    specs: list[RootSpec] = []
    rng = random.Random(SEED ^ 0xA11CE)
    for index in range(10):
        main = index < 4
        base_angle = index * math.tau / 10.0
        angle = base_angle + rng.uniform(-0.20, 0.20)
        specs.append(
            RootSpec(
                angle=angle,
                radial_start=rng.uniform(120.0, 205.0),
                end_dx=rng.uniform(-42.0, 42.0),
                end_dz=rng.uniform(-42.0, 42.0),
                start_y=rng.uniform(61.0, 70.0),
                end_y=rng.uniform(4.0, 28.0),
                start_radius=rng.uniform(9.5, 12.5) if main else rng.uniform(6.0, 8.5),
                end_radius=rng.uniform(2.0, 3.5),
            )
        )
    return specs


def root_center(spec: RootSpec, t: float) -> tuple[float, float, float]:
    cx, cz = ISLAND_CENTER
    start_x = cx + math.cos(spec.angle) * spec.radial_start
    start_z = cz + math.sin(spec.angle) * spec.radial_start
    outward = 18.0 * math.sin(math.pi * t)
    sway = math.sin(t * math.pi * 2.0 + spec.angle * 3.0) * 13.0 * t
    x = start_x + spec.end_dx * t + math.cos(spec.angle) * outward - math.sin(spec.angle) * sway
    z = start_z + spec.end_dz * t + math.sin(spec.angle) * outward + math.cos(spec.angle) * sway
    y = spec.start_y + (spec.end_y - spec.start_y) * (t ** 0.82)
    return x, y, z


def piece_key(x: int, y: int, z: int) -> tuple[int, int, int]:
    return x // PIECE_SIZE, y // PIECE_SIZE, z // PIECE_SIZE


def add_sphere(
    target: dict[tuple[int, int, int], dict[tuple[int, int, int], str]],
    center: tuple[float, float, float],
    radius: float,
    state_picker,
) -> None:
    cx, cy, cz = center
    limit = math.ceil(radius)
    r2 = radius * radius
    for x in range(math.floor(cx) - limit, math.floor(cx) + limit + 1):
        if not 0 <= x < ISLAND_SIZE[0]:
            continue
        for y in range(math.floor(cy) - limit, math.floor(cy) + limit + 1):
            if not 0 <= y < ISLAND_SIZE[1]:
                continue
            for z in range(math.floor(cz) - limit, math.floor(cz) + limit + 1):
                if not 0 <= z < ISLAND_SIZE[2]:
                    continue
                if (x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 > r2:
                    continue
                target[piece_key(x, y, z)][(x, y, z)] = state_picker(x, y, z)


def build_root_blocks() -> tuple[
    dict[tuple[int, int, int], dict[tuple[int, int, int], str]],
    list[tuple[int, int, int]],
]:
    blocks: dict[tuple[int, int, int], dict[tuple[int, int, int], str]] = defaultdict(dict)
    tips: list[tuple[int, int, int]] = []
    for root_index, spec in enumerate(root_specs()):
        steps = max(32, round((spec.start_y - spec.end_y) * 1.45))
        for step in range(steps + 1):
            t = step / steps
            center = root_center(spec, t)
            radius = spec.start_radius + (spec.end_radius - spec.start_radius) * (t ** 0.8)

            def root_picker(x: int, y: int, z: int, index: int = root_index) -> str:
                value = abs((x * 31 + y * 17 + z * 13 + index * 101) ^ SEED)
                if value % 19 == 0:
                    return ROOTED_DIRT
                return DARK_OAK_LOG

            add_sphere(blocks, center, radius, root_picker)

        tip = tuple(round(value) for value in root_center(spec, 1.0))
        tips.append(tip)
        add_sphere(
            blocks,
            (tip[0], tip[1] - 1, tip[2]),
            3.2 if root_index < 4 else 2.4,
            lambda x, y, z: BUDDING_AMETHYST if (x + y + z) % 7 == 0 else AMETHYST,
        )
        blocks[piece_key(tip[0], tip[1] - 2, tip[2])][(tip[0], tip[1] - 2, tip[2])] = SEA_LANTERN
    return blocks, tips


def in_town_buffer(x: int, z: int, margin: int = 8) -> bool:
    x0, x1, z0, z1 = TOWN_BOUNDS
    return x0 - margin <= x <= x1 + margin and z0 - margin <= z <= z1 + margin


def tree_positions() -> list[tuple[int, int, int, int]]:
    rng = random.Random(SEED ^ 0x7AEE)
    positions: list[tuple[int, int, int, int]] = []
    attempts = 0
    while len(positions) < 115 and attempts < 20_000:
        attempts += 1
        x = rng.randrange(18, ISLAND_SIZE[0] - 18)
        z = rng.randrange(18, ISLAND_SIZE[2] - 18)
        if in_town_buffer(x, z, 10):
            continue
        top = surface_height(x, z)
        if top is None:
            continue
        angle = math.atan2(z - ISLAND_CENTER[1], x - ISLAND_CENTER[0])
        distance = math.hypot(x - ISLAND_CENTER[0], z - ISLAND_CENTER[1])
        if distance > boundary_radius(angle) - 13:
            continue
        if any((x - px) ** 2 + (z - pz) ** 2 < 9 * 9 for px, _, pz, _ in positions):
            continue
        positions.append((x, top + 1, z, rng.randrange(5, 9)))
    return positions


def build_detail_blocks() -> dict[tuple[int, int, int], dict[tuple[int, int, int], str]]:
    blocks: dict[tuple[int, int, int], dict[tuple[int, int, int], str]] = defaultdict(dict)
    for tree_index, (x, y, z, height) in enumerate(tree_positions()):
        log = DARK_OAK_LOG if tree_index % 3 else OAK_LOG
        leaves = DARK_OAK_LEAVES if tree_index % 3 else OAK_LEAVES
        for dy in range(height):
            gy = y + dy
            blocks[piece_key(x, gy, z)][(x, gy, z)] = log
        canopy_y = y + height - 1
        for dx in range(-3, 4):
            for dy in range(-2, 3):
                for dz in range(-3, 4):
                    score = dx * dx + dz * dz + (dy * 1.5) ** 2
                    if score > 11.5:
                        continue
                    gx, gy, gz = x + dx, canopy_y + dy, z + dz
                    if (gx, gy, gz) in blocks[piece_key(gx, gy, gz)]:
                        continue
                    blocks[piece_key(gx, gy, gz)][(gx, gy, gz)] = leaves

    rng = random.Random(SEED ^ 0xB0A1DE2)
    rocks = 0
    while rocks < 70:
        x = rng.randrange(12, ISLAND_SIZE[0] - 12)
        z = rng.randrange(12, ISLAND_SIZE[2] - 12)
        if in_town_buffer(x, z, 5):
            continue
        top = surface_height(x, z)
        if top is None:
            continue
        radius = rng.uniform(1.3, 3.2)
        add_sphere(
            blocks,
            (x, top + 1, z),
            radius,
            lambda gx, gy, gz: MOSS if gy > top + 1 and (gx + gz) % 3 == 0 else ANDESITE,
        )
        rocks += 1
    return blocks


def load_structure(path: Path) -> tuple[tuple[int, int, int], list[tuple[int, int, int, str, Any | None]], int]:
    root = nbtlib.load(path)
    palette = [state_text(entry) for entry in root["palette"]]
    blocks = []
    for entry in root["blocks"]:
        x, y, z = (int(value) for value in entry["pos"])
        state = palette[int(entry["state"])]
        blocks.append((x, y, z, state, entry.get("nbt")))
    return tuple(int(value) for value in root["size"]), blocks, int(root["DataVersion"])


def write_grouped_blocks(
    output: Path,
    folder: str,
    prefix: str,
    grouped: dict[tuple[int, int, int], dict[tuple[int, int, int], str]],
    entries: list[dict[str, Any]],
) -> tuple[int, int]:
    block_total = 0
    piece_total = 0
    for key in sorted(grouped):
        global_blocks = grouped[key]
        if not global_blocks:
            continue
        px, py, pz = key
        offset = [px * PIECE_SIZE, py * PIECE_SIZE, pz * PIECE_SIZE]
        size = [
            min(PIECE_SIZE, ISLAND_SIZE[0] - offset[0]),
            min(PIECE_SIZE, ISLAND_SIZE[1] - offset[1]),
            min(PIECE_SIZE, ISLAND_SIZE[2] - offset[2]),
        ]
        blocks = [
            (x - offset[0], y - offset[1], z - offset[2], state, None)
            for (x, y, z), state in sorted(global_blocks.items())
        ]
        name = f"{prefix}_{px}_{py}_{pz}"
        count, block_entities = write_piece(output / folder / f"{name}.nbt", tuple(size), blocks, DATA_VERSION)
        digest = hashlib.sha256((output / folder / f"{name}.nbt").read_bytes()).hexdigest()
        entries.append(
            {
                "layer": folder,
                "name": name,
                "resource": f"tnc:sky_island/{folder}/{name}",
                "offset": offset,
                "size": size,
                "blocks": count,
                "block_entities": block_entities,
                "sha256": digest,
            }
        )
        block_total += count
        piece_total += 1
    return piece_total, block_total


def build_shell(output: Path, root_blocks, entries: list[dict[str, Any]]) -> tuple[int, int]:
    grouped: dict[tuple[int, int, int], dict[tuple[int, int, int], str]] = defaultdict(dict)
    for x in range(ISLAND_SIZE[0]):
        for z in range(ISLAND_SIZE[2]):
            top = surface_height(x, z)
            if top is None:
                continue
            bottom = shell_bottom(x, z, top)
            for y in range(bottom, top + 1):
                grouped[piece_key(x, y, z)][(x, y, z)] = shell_state(x, y, z, top, bottom)

    for key, blocks in root_blocks.items():
        grouped[key].update(blocks)
    return write_grouped_blocks(output, "shell", "shell", grouped, entries)


def build_town(town_input: Path, output: Path, entries: list[dict[str, Any]]) -> tuple[int, int, int]:
    manifest = json.loads((town_input / "manifest.json").read_text(encoding="utf-8"))
    piece_total = 0
    block_total = 0
    block_entity_total = 0
    for source_entry in manifest["pieces"]:
        source_name = source_entry["name"]
        size, blocks, data_version = load_structure(town_input / f"{source_name}.nbt")
        source_offset = source_entry["offset"]
        kept = []
        for x, y, z, state, block_entity in blocks:
            global_town_y = source_offset[1] + y
            if global_town_y < TOWN_KEEP_MIN_Y:
                continue
            kept.append((x, y, z, state, block_entity))
        if not kept:
            continue
        name = f"town_{source_name}"
        path = output / "town" / f"{name}.nbt"
        count, block_entities = write_piece(path, size, kept, data_version)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        offset = [
            TOWN_OFFSET[0] + source_offset[0],
            TOWN_OFFSET[1] + source_offset[1],
            TOWN_OFFSET[2] + source_offset[2],
        ]
        entries.append(
            {
                "layer": "town",
                "name": name,
                "resource": f"tnc:sky_island/town/{name}",
                "offset": offset,
                "size": list(size),
                "blocks": count,
                "block_entities": block_entities,
                "sha256": digest,
            }
        )
        piece_total += 1
        block_total += count
        block_entity_total += block_entities
    return piece_total, block_total, block_entity_total


def portal_blocks() -> list[tuple[int, int, int, str, Any | None]]:
    blocks = []
    center = 7
    for x in range(15):
        for z in range(15):
            distance = math.hypot(x - center, z - center)
            if distance <= 6.7:
                state = "minecraft:polished_andesite"
                if 4.3 <= distance <= 6.7:
                    state = "minecraft:stone_bricks"
                if distance <= 2.2:
                    state = AMETHYST
                blocks.append((x, 0, z, state, None))
    for angle_index in range(8):
        angle = angle_index * math.tau / 8
        x = round(center + math.cos(angle) * 5.5)
        z = round(center + math.sin(angle) * 5.5)
        blocks.append((x, 1, z, "minecraft:chiseled_stone_bricks", None))
        blocks.append((x, 2, z, SEA_LANTERN, None))
    blocks.append((center, 1, center, SEA_LANTERN, None))
    return blocks


def build_portals(output: Path, entries: list[dict[str, Any]]) -> dict[str, str]:
    resources = {}
    for name in ("ground_portal", "island_portal"):
        path = output / "portal" / f"{name}.nbt"
        blocks = portal_blocks()
        count, block_entities = write_piece(path, (15, 4, 15), blocks, DATA_VERSION)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        entries.append(
            {
                "layer": "portal",
                "name": name,
                "resource": f"tnc:sky_island/portal/{name}",
                "offset": [0, 0, 0],
                "size": [15, 4, 15],
                "blocks": count,
                "block_entities": block_entities,
                "sha256": digest,
                "runtime_placed": True,
            }
        )
        resources[name] = f"tnc:sky_island/portal/{name}"
    return resources


def write_previews(preview_dir: Path, root_tips: Iterable[tuple[int, int, int]]) -> None:
    preview_dir.mkdir(parents=True, exist_ok=True)
    scale = 2
    top = Image.new("RGB", (ISLAND_SIZE[0], ISLAND_SIZE[2]), (218, 238, 250))
    pixels = top.load()
    for x in range(ISLAND_SIZE[0]):
        for z in range(ISLAND_SIZE[2]):
            y = surface_height(x, z)
            if y is None:
                continue
            shade = max(0, min(35, y - SURFACE_Y + 18))
            pixels[x, z] = (70 + shade, 118 + shade, 72 + shade // 2)
    draw = ImageDraw.Draw(top)
    x0, x1, z0, z1 = TOWN_BOUNDS
    draw.rectangle((x0, z0, x1, z1), outline=(238, 205, 132), width=3)
    draw.ellipse((ISLAND_CENTER[0] - 4, ISLAND_CENTER[1] - 4, ISLAND_CENTER[0] + 4, ISLAND_CENTER[1] + 4), fill=(255, 245, 180))
    top.resize((ISLAND_SIZE[0] * scale, ISLAND_SIZE[2] * scale), Image.Resampling.NEAREST).save(preview_dir / "sky_island_top.png")

    side = Image.new("RGB", (ISLAND_SIZE[0], ISLAND_SIZE[1]), (218, 238, 250))
    side_pixels = side.load()
    for x in range(ISLAND_SIZE[0]):
        tops = [surface_height(x, z) for z in range(ISLAND_SIZE[2])]
        valid = [value for value in tops if value is not None]
        if not valid:
            continue
        top_y = max(valid)
        representative_z = max(range(ISLAND_SIZE[2]), key=lambda z: surface_height(x, z) or -1)
        bottom_y = shell_bottom(x, representative_z, top_y)
        for y in range(bottom_y, top_y + 1):
            side_pixels[x, ISLAND_SIZE[1] - 1 - y] = (104, 96, 76) if y < top_y - 3 else (94, 139, 73)
    side_draw = ImageDraw.Draw(side)
    for x, y, _ in root_tips:
        side_draw.line((x, ISLAND_SIZE[1] - 1 - 67, x, ISLAND_SIZE[1] - 1 - y), fill=(94, 61, 40), width=5)
        side_draw.ellipse((x - 3, ISLAND_SIZE[1] - 1 - y - 3, x + 3, ISLAND_SIZE[1] - 1 - y + 3), fill=(126, 238, 201))
    side.resize((ISLAND_SIZE[0] * scale, ISLAND_SIZE[1] * scale), Image.Resampling.NEAREST).save(preview_dir / "sky_island_side.png")


def build_manifest(entries: list[dict[str, Any]], root_tips, portal_resources) -> dict[str, Any]:
    build_entries = [entry for entry in entries if not entry.get("runtime_placed")]
    build_entries.sort(key=lambda entry: ({"shell": 0, "town": 1, "detail": 2}[entry["layer"]], entry["offset"][1], entry["offset"][0], entry["offset"][2], entry["name"]))
    town_center = [
        TOWN_OFFSET[0] + TOWN_CENTER_LOCAL[0],
        TOWN_OFFSET[1] + TOWN_CENTER_LOCAL[1],
        TOWN_OFFSET[2] + TOWN_CENTER_LOCAL[2],
    ]
    arrival = [
        TOWN_OFFSET[0] + TOWN_ARRIVAL_LOCAL[0],
        TOWN_OFFSET[1] + TOWN_ARRIVAL_LOCAL[1],
        TOWN_OFFSET[2] + TOWN_ARRIVAL_LOCAL[2],
    ]
    return {
        "version": 5,
        "data_version": DATA_VERSION,
        "seed": SEED,
        "dimensions": list(ISLAND_SIZE),
        "world_origin_y": ISLAND_WORLD_Y,
        "surface_local_y": SURFACE_Y,
        "town_offset": list(TOWN_OFFSET),
        "town_center_local": town_center,
        "arrival_local": arrival,
        "root_tips": [list(tip) for tip in root_tips],
        "portal_templates": portal_resources,
        "piece_count": len(build_entries),
        "non_air_blocks": sum(entry["blocks"] for entry in build_entries),
        "block_entities": sum(entry["block_entities"] for entry in build_entries),
        "layer_counts": {
            layer: sum(1 for entry in build_entries if entry["layer"] == layer)
            for layer in ("shell", "town", "detail")
        },
        "pieces": build_entries,
    }


def replace_directory(source: Path, destination: Path) -> None:
    backup = destination.with_name(destination.name + ".previous")
    if backup.exists():
        shutil.rmtree(backup)
    if destination.exists():
        destination.replace(backup)
    source.replace(destination)
    if backup.exists():
        shutil.rmtree(backup)


def generate(town_input: Path, output: Path, manifest_output: Path, preview_dir: Path) -> dict[str, Any]:
    if not (town_input / "manifest.json").is_file():
        raise FileNotFoundError(f"town manifest not found: {town_input / 'manifest.json'}")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tnc-sky-island-", dir=output.parent) as temp_name:
        temp_output = Path(temp_name) / "sky_island"
        temp_output.mkdir(parents=True)
        entries: list[dict[str, Any]] = []
        root_blocks, root_tips = build_root_blocks()
        shell_pieces, shell_blocks = build_shell(temp_output, root_blocks, entries)
        town_pieces, town_blocks, town_block_entities = build_town(town_input, temp_output, entries)
        detail_blocks = build_detail_blocks()
        detail_pieces, detail_count = write_grouped_blocks(temp_output, "detail", "detail", detail_blocks, entries)
        portal_resources = build_portals(temp_output, entries)
        manifest = build_manifest(entries, root_tips, portal_resources)
        (temp_output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        replace_directory(temp_output, output)

    manifest_output.parent.mkdir(parents=True, exist_ok=True)
    manifest_output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_previews(preview_dir, root_tips)
    print(
        json.dumps(
            {
                "shell": {"pieces": shell_pieces, "blocks": shell_blocks},
                "town": {"pieces": town_pieces, "blocks": town_blocks, "block_entities": town_block_entities},
                "detail": {"pieces": detail_pieces, "blocks": detail_count},
                "total": {"pieces": manifest["piece_count"], "blocks": manifest["non_air_blocks"]},
                "output": str(output),
                "manifest": str(manifest_output),
                "previews": str(preview_dir),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("town_input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("manifest_output", type=Path)
    parser.add_argument("preview_dir", type=Path)
    args = parser.parse_args()
    generate(args.town_input, args.output, args.manifest_output, args.preview_dir)


if __name__ == "__main__":
    main()
