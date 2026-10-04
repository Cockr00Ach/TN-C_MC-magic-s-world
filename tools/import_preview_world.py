"""Safely import a partner's zipped Minecraft save as a separate preview world.

Usage: python tools/import_preview_world.py ARCHIVE SAVES_DIR PREVIEW_NAME
The original save and archive are never changed or deleted.
"""

from __future__ import annotations

import gzip
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import sys
import uuid
import zipfile


def import_preview(archive: Path, saves: Path, name: str) -> dict:
    archive = archive.resolve(strict=True)
    saves = saves.resolve(strict=True)
    if not saves.is_dir() or not name or name in {".", ".."} or any(c in name for c in "\\/:*?\"<>|"):
        raise ValueError("Invalid saves directory or preview name")
    target = (saves / name).resolve()
    staging = (saves / ("." + name + ".import-" + uuid.uuid4().hex[:8])).resolve()
    if not target.is_relative_to(saves) or not staging.is_relative_to(saves):
        raise ValueError("Destination escapes the Minecraft saves directory")
    if target.exists():
        raise FileExistsError(f"A world with this preview name already exists: {target}")

    extracted = 0
    total_bytes = 0
    with zipfile.ZipFile(archive) as bundle:
        members = bundle.infolist()
        roots = {PurePosixPath(info.filename).parts[0] for info in members if info.filename}
        if len(roots) != 1:
            raise ValueError(f"Expected one world folder; found {len(roots)} archive roots")
        root = roots.pop()
        if f"{root}/level.dat" not in bundle.namelist():
            raise ValueError("Archive has no level.dat under its world folder")
        if sum(info.file_size for info in members) > 3_000_000_000:
            raise ValueError("World archive expands beyond the 3 GB preview limit")
        staging.mkdir()
        for info in members:
            archive_path = PurePosixPath(info.filename)
            parts = archive_path.parts
            if not parts or parts[0] != root or len(parts) == 1:
                continue
            if any(part in {"", ".", ".."} for part in parts):
                raise ValueError(f"Unsafe archive path: {info.filename!r}")
            if stat.S_IFMT(info.external_attr >> 16) == stat.S_IFLNK:
                raise ValueError(f"Symlink not permitted in world archive: {info.filename!r}")
            if parts[-1] == "session.lock":
                continue
            destination = (staging.joinpath(*parts[1:])).resolve()
            if not destination.is_relative_to(staging):
                raise ValueError(f"Archive path leaves preview: {info.filename!r}")
            if info.is_dir():
                destination.mkdir(parents=True, exist_ok=True)
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(info) as source, destination.open("wb") as sink:
                shutil.copyfileobj(source, sink, length=1024 * 1024)
            extracted += 1
            total_bytes += info.file_size

    with gzip.open(staging / "level.dat", "rb") as level:
        if level.read(1) != b"\x0a":
            raise ValueError("level.dat does not contain a Minecraft NBT compound")
    if not any((staging / "region").glob("*.mca")):
        raise ValueError("Preview world has no Overworld region files")
    if target.exists():
        raise FileExistsError("Preview name became occupied during extraction")
    # Both absolute paths were checked to remain inside saves above.
    staging.rename(target)
    return {"archive": str(archive), "preview": str(target), "files": extracted,
            "uncompressed_bytes": total_bytes}


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    print(json.dumps(import_preview(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]),
                     ensure_ascii=False, indent=2))
