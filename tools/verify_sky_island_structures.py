"""Validate every generated v5 sky-island structure and its manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import nbtlib


def verify(structure_dir: Path, manifest_path: Path) -> dict[str, int]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors: list[str] = []
    resources: set[str] = set()
    checked_blocks = 0
    checked_block_entities = 0

    if manifest.get("version") != 5:
        errors.append(f"version={manifest.get('version')}, expected 5")
    if manifest.get("dimensions") != [520, 220, 520]:
        errors.append(f"dimensions={manifest.get('dimensions')}")
    if manifest.get("arrival_local") != [209, 90, 424]:
        errors.append(f"arrival_local={manifest.get('arrival_local')}")

    pieces = manifest.get("pieces", [])
    if len(pieces) != manifest.get("piece_count"):
        errors.append(f"piece_count={manifest.get('piece_count')} entries={len(pieces)}")

    for entry in pieces:
        resource = entry["resource"]
        if resource in resources:
            errors.append(f"duplicate resource: {resource}")
        resources.add(resource)
        relative = resource.removeprefix("tnc:sky_island/") + ".nbt"
        path = structure_dir / relative
        if not path.is_file():
            errors.append(f"missing template: {relative}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != entry["sha256"]:
            errors.append(f"hash mismatch: {relative}")

        root = nbtlib.load(path)
        size = [int(value) for value in root["size"]]
        if size != entry["size"]:
            errors.append(f"size mismatch: {relative} {size} != {entry['size']}")
        if any(value < 1 or value > 48 for value in size):
            errors.append(f"invalid size: {relative} {size}")
        if int(root["DataVersion"]) != manifest["data_version"]:
            errors.append(f"DataVersion mismatch: {relative}")

        positions: set[tuple[int, int, int]] = set()
        block_entities = 0
        for block in root["blocks"]:
            position = tuple(int(value) for value in block["pos"])
            if position in positions:
                errors.append(f"duplicate position: {relative} {position}")
            positions.add(position)
            if not all(0 <= position[index] < size[index] for index in range(3)):
                errors.append(f"out-of-range position: {relative} {position} size={size}")
            if "nbt" in block:
                block_entities += 1
                for coordinate in ("x", "y", "z"):
                    if coordinate in block["nbt"]:
                        errors.append(f"absolute block-entity coordinate: {relative} {position} {coordinate}")

        if len(positions) != entry["blocks"]:
            errors.append(f"block count mismatch: {relative} {len(positions)} != {entry['blocks']}")
        if block_entities != entry["block_entities"]:
            errors.append(
                f"block entity count mismatch: {relative} {block_entities} != {entry['block_entities']}"
            )
        checked_blocks += len(positions)
        checked_block_entities += block_entities

    actual_templates = set()
    for path in structure_dir.rglob("*.nbt"):
        relative = path.relative_to(structure_dir).as_posix()
        if relative.startswith("portal/"):
            continue
        actual_templates.add("tnc:sky_island/" + relative.removesuffix(".nbt"))
    extras = actual_templates - resources
    missing = resources - actual_templates
    if extras:
        errors.append(f"unlisted templates: {sorted(extras)}")
    if missing:
        errors.append(f"manifest templates absent on disk: {sorted(missing)}")

    if checked_blocks != manifest.get("non_air_blocks"):
        errors.append(f"total blocks={checked_blocks}, manifest={manifest.get('non_air_blocks')}")
    if checked_block_entities != manifest.get("block_entities"):
        errors.append(
            f"total block entities={checked_block_entities}, manifest={manifest.get('block_entities')}"
        )
    if checked_block_entities != 2183:
        errors.append(f"town block entities={checked_block_entities}, expected 2183")

    if errors:
        raise AssertionError("\n".join(errors))
    return {
        "pieces": len(pieces),
        "blocks": checked_blocks,
        "block_entities": checked_block_entities,
        "portal_templates": len(list((structure_dir / "portal").glob("*.nbt"))),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("structure_dir", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.structure_dir, args.manifest), indent=2))


if __name__ == "__main__":
    main()
