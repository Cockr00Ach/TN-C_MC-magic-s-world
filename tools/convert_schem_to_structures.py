"""Convert a Sponge v2 schematic into Minecraft structure templates.

The input is split into pieces no larger than the requested dimensions.  The
default 48^3 limit is deliberately conservative and matches the safe range
for structure-block/jigsaw workflows on 1.20.1.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import nbtlib


STATE_RE = re.compile(r"^(?P<name>[^\[]+)(?:\[(?P<props>.*)\])?$")


def decode_varints(data: bytes, count: int) -> list[int]:
    values: list[int] = []
    index = 0
    for _ in range(count):
        value = 0
        shift = 0
        while True:
            if index >= len(data):
                raise ValueError("BlockData ended before all palette indices were decoded")
            byte = data[index]
            index += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
            if shift > 28:
                raise ValueError("invalid Sponge varint")
        values.append(value)
    return values


def parse_state(text: str) -> tuple[str, dict[str, str]]:
    match = STATE_RE.match(text)
    if not match:
        raise ValueError(f"invalid block state: {text}")
    props: dict[str, str] = {}
    if match.group("props"):
        for pair in match.group("props").split(","):
            key, value = pair.split("=", 1)
            props[key] = value
    return match.group("name"), props


def int_list(values: list[int]) -> nbtlib.List:
    return nbtlib.List[nbtlib.Int]([nbtlib.Int(value) for value in values])


def write_piece(path: Path, size: tuple[int, int, int], blocks: list[tuple[int, int, int, str]]) -> None:
    palette_names: list[str] = []
    palette_ids: dict[str, int] = {}
    block_tags: list[nbtlib.Compound] = []
    for x, y, z, state in blocks:
        if state not in palette_ids:
            palette_ids[state] = len(palette_names)
            palette_names.append(state)
        name, props = parse_state(state)
        tag = {"Name": nbtlib.String(name)}
        if props:
            tag["Properties"] = nbtlib.Compound({k: nbtlib.String(v) for k, v in props.items()})
        block_tags.append(nbtlib.Compound({"pos": int_list([x, y, z]), "state": nbtlib.Int(palette_ids[state])}))

    palette = []
    for state in palette_names:
        name, props = parse_state(state)
        tag = {"Name": nbtlib.String(name)}
        if props:
            tag["Properties"] = nbtlib.Compound({k: nbtlib.String(v) for k, v in props.items()})
        palette.append(nbtlib.Compound(tag))

    root = nbtlib.Compound(
        {
            # Minecraft 1.20.1 structure templates are data-fixed on load and
            # require a source data version. 3465 is the 1.20.1 release value.
            "DataVersion": nbtlib.Int(3465),
            "size": int_list(list(size)),
            "palette": nbtlib.List[nbtlib.Compound](palette),
            "blocks": nbtlib.List[nbtlib.Compound](block_tags),
            "entities": nbtlib.List[nbtlib.Compound]([]),
        }
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root).save(path, gzipped=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("schematic", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--piece", type=int, default=48)
    args = parser.parse_args()
    if args.piece < 1 or args.piece > 48:
        parser.error("--piece must be between 1 and 48")

    schematic = nbtlib.load(args.schematic)
    width = int(schematic["Width"])
    height = int(schematic["Height"])
    length = int(schematic["Length"])
    palette_by_id = {int(value): str(name) for name, value in schematic["Palette"].items()}
    indices = decode_varints(bytes(schematic["BlockData"]), width * height * length)

    pieces: dict[tuple[int, int, int], list[tuple[int, int, int, str]]] = {}
    non_air = 0
    for index, palette_id in enumerate(indices):
        state = palette_by_id[palette_id]
        if state == "minecraft:air":
            continue
        x = index % width
        z = (index // width) % length
        y = index // (width * length)
        key = (x // args.piece, y // args.piece, z // args.piece)
        pieces.setdefault(key, []).append((x % args.piece, y % args.piece, z % args.piece, state))
        non_air += 1

    manifest = {
        # Keep manifests portable and avoid embedding a collaborator's local path.
        "source": args.schematic.name,
        "dimensions": [width, height, length],
        "piece_size": args.piece,
        "non_air_blocks": non_air,
        "pieces": [],
    }
    for (px, py, pz), blocks in sorted(pieces.items()):
        sx = min(args.piece, width - px * args.piece)
        sy = min(args.piece, height - py * args.piece)
        sz = min(args.piece, length - pz * args.piece)
        name = f"piece_{px}_{py}_{pz}.nbt"
        write_piece(args.output / name, (sx, sy, sz), blocks)
        manifest["pieces"].append({"file": name, "offset": [px * args.piece, py * args.piece, pz * args.piece], "blocks": len(blocks), "size": [sx, sy, sz]})

    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"dimensions": [width, height, length], "non_air_blocks": non_air, "piece_count": len(pieces), "output": str(args.output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
