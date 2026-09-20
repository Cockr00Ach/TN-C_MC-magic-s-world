"""Convert local travel-music inbox files into Minecraft streaming sound assets.

The audio itself remains local and ignored.  The Java controller discovers the
generated sound events at runtime, so the source code still builds when a
collaborator has not received the separately licensed music files.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path


SUPPORTED = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac"}
EVENT_PREFIX = "music.travel."


def run(command: list[str]) -> None:
    completed = subprocess.run(command, text=True, capture_output=True)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(detail or f"command failed: {command[0]}")


def console_report(imported: list[dict[str, object]]) -> str:
    """Return a report that is safe on legacy Windows console encodings."""
    return json.dumps({"count": len(imported), "tracks": imported}, ensure_ascii=True, indent=2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("inbox", type=Path)
    parser.add_argument("assets", type=Path)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()

    ffmpeg = shutil.which(args.ffmpeg) if Path(args.ffmpeg).name == args.ffmpeg else args.ffmpeg
    if not ffmpeg or not Path(ffmpeg).is_file():
        raise FileNotFoundError("ffmpeg not found; install it or pass --ffmpeg <path>")

    sources = sorted(
        (path for path in args.inbox.iterdir() if path.is_file() and path.suffix.lower() in SUPPORTED),
        key=lambda path: path.name.casefold(),
    )
    if not sources:
        raise FileNotFoundError(f"no supported audio files in {args.inbox}")

    sound_dir = args.assets / "sounds" / "music" / "travel"
    sound_parent = sound_dir.parent
    sound_parent.mkdir(parents=True, exist_ok=True)

    sounds_path = args.assets / "sounds.json"
    if sounds_path.is_file():
        sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    else:
        sounds = {}
    sounds = {key: value for key, value in sounds.items() if not key.startswith(EVENT_PREFIX)}

    imported = []
    staging: Path | None = Path(tempfile.mkdtemp(prefix=".travel-import-", dir=sound_parent))
    try:
        for index, source in enumerate(sources, start=1):
            track = f"track_{index:03d}"
            output = staging / f"{track}.ogg"
            run(
                [
                    str(ffmpeg), "-hide_banner", "-loglevel", "error", "-y",
                    "-i", str(source), "-vn", "-map_metadata", "-1",
                    "-ac", "2", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5",
                    str(output),
                ]
            )
            event = EVENT_PREFIX + track
            sounds[event] = {
                "sounds": [
                    {
                        "name": f"tnc:music/travel/{track}",
                        "stream": True,
                    }
                ]
            }
            imported.append({"event": f"tnc:{event}", "source": source.name, "bytes": output.stat().st_size})

        backup = sound_parent / ".travel-import-backup"
        if backup.exists():
            shutil.rmtree(backup)
        if sound_dir.exists():
            sound_dir.replace(backup)
        try:
            staging.replace(sound_dir)
            staging = None
            temporary_json = sounds_path.with_suffix(".json.tmp")
            temporary_json.write_text(
                json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            temporary_json.replace(sounds_path)
        except Exception:
            if sound_dir.exists():
                shutil.rmtree(sound_dir)
            if backup.exists():
                backup.replace(sound_dir)
            raise
        else:
            if backup.exists():
                shutil.rmtree(backup)
    finally:
        if staging is not None and staging.exists():
            shutil.rmtree(staging)

    print(console_report(imported))


if __name__ == "__main__":
    main()
