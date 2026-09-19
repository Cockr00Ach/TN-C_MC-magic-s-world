"""Build the small KubeJS runtime manifest for the exported Medieval Town."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path


FILL_LIMIT = 32_768
MAX_CLEAR_HEIGHT_PER_TICK = 14


def local_position(position: list[int], minimums: list[int]) -> list[int]:
    return [position[index] - minimums[index] for index in range(3)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--version", type=int, default=4)
    parser.add_argument("--source-ground-y", type=int, default=64)
    parser.add_argument("--center", nargs=3, type=int, metavar=("X", "Y", "Z"), required=True)
    parser.add_argument("--arrival", nargs=3, type=int, metavar=("X", "Y", "Z"), required=True)
    args = parser.parse_args()

    source = json.loads(args.manifest.read_text(encoding="utf-8"))
    bounds = source["source_bounds"]
    minimums = [bounds["x"][0], bounds["y"][0], bounds["z"][0]]
    dimensions = source["dimensions"]
    ground_local = args.source_ground_y - minimums[1]
    center_local = local_position(args.center, minimums)
    arrival_local = local_position(args.arrival, minimums)

    for label, position in (("center", center_local), ("arrival", arrival_local)):
        if any(value < 0 or value >= dimensions[index] for index, value in enumerate(position)):
            raise ValueError(f"{label} lies outside the exported bounds: {position}")

    horizontal: dict[tuple[int, int, int, int], list[dict]] = defaultdict(list)
    for piece in source["pieces"]:
        offset_x, _, offset_z = piece["offset"]
        size_x, _, size_z = piece["size"]
        horizontal[(offset_x, offset_z, size_x, size_z)].append(piece)

    operations: list[list] = []
    counts = {"load": 0, "wait": 0, "clear": 0, "place": 0, "unload": 0}
    for offset_x, offset_z, size_x, size_z in sorted(horizontal):
        operations.append(["load", offset_x, offset_z, size_x, size_z])
        counts["load"] += 1
        operations.append(["wait", 2])
        counts["wait"] += 1

        area = size_x * size_z
        max_height = min(MAX_CLEAR_HEIGHT_PER_TICK, FILL_LIMIT // area)
        clear_y = ground_local + 1
        while clear_y < dimensions[1]:
            clear_y1 = min(dimensions[1] - 1, clear_y + max_height - 1)
            volume = area * (clear_y1 - clear_y + 1)
            if volume > FILL_LIMIT:
                raise ValueError(f"clear operation exceeds /fill limit: {volume}")
            operations.append(
                [
                    "clear",
                    offset_x,
                    clear_y,
                    offset_z,
                    offset_x + size_x - 1,
                    clear_y1,
                    offset_z + size_z - 1,
                ]
            )
            counts["clear"] += 1
            clear_y = clear_y1 + 1

        for piece in sorted(horizontal[(offset_x, offset_z, size_x, size_z)], key=lambda item: item["offset"][1]):
            offset = piece["offset"]
            operations.append(["place", piece["name"], offset[0], offset[1], offset[2]])
            counts["place"] += 1

        operations.append(["unload", offset_x, offset_z, size_x, size_z])
        counts["unload"] += 1

    runtime = {
        "version": args.version,
        "dimensions": dimensions,
        "sourceGroundLocalY": ground_local,
        "centerLocal": center_local,
        "arrivalLocal": arrival_local,
        "pieceCount": len(source["pieces"]),
        "nonAirBlocks": source["non_air_blocks"],
        "blockEntities": source["block_entities"],
        "operationCounts": counts,
        "operations": operations,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "global.TNC_TOWN_MANIFEST = " + json.dumps(runtime, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({**counts, "operations": len(operations), "output": str(args.output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
