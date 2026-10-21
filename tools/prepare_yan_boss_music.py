"""Overlay locally supplied Yan battle music on the current installed mod."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

EVENT = "music.boss.yan_crywolf"
SOUND = {"sounds": [{"name": "tnc:music/boss/yan_crywolf", "stream": True}]}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def prepare(live, output):
    root = Path(__file__).resolve().parents[1]
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    base = live / "mods/tnc-1.0.0.jar"
    before_hash = digest(base)
    audio = root / "src/main/resources/assets/tnc/sounds/music/boss/yan_crywolf.ogg"
    assert audio.read_bytes()[:4] == b"OggS", "Import Crywolf as OGG/Vorbis first"
    source_sounds = root / "src/main/resources/assets/tnc/sounds.json"
    source = json.loads(source_sounds.read_text(encoding="utf-8-sig"))
    source[EVENT] = SOUND
    source_sounds.write_text(json.dumps(source, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    asm = next((root / ".gradle-user/wrapper/dists").rglob("asm-9.7.jar"))
    subprocess.run([str(root / ".jdk17/bin/javac.exe"), "-cp", str(asm), "-d", str(output),
                    str(root / "tools/WireYanBossMusic.java")], check=True)
    name = "com/tnc/tnc/client/TravelMusicController.class"
    with zipfile.ZipFile(base) as src:
        (output / "before.class").write_bytes(src.read(name))
        subprocess.run([str(root / ".jdk17/bin/java.exe"), "-cp", str(asm) + ";" + str(output),
                        "WireYanBossMusic", str(output / "before.class"), str(output / "after.class")], check=True)
        # Every method except the constant initializer must retain its exact bytecode.
        from prepare_dungeon_mob_install import members
        fields, methods = members((output / "before.class").read_bytes())
        newfields, newmethods = members((output / "after.class").read_bytes())
        assert fields == newfields and methods.keys() == newmethods.keys()
        assert all(raw == newmethods[key] for key, raw in methods.items() if key[0] != "<clinit>")
        sounds_name = "assets/tnc/sounds.json"
        sounds = json.loads(src.read(sounds_name))
        sounds[EVENT] = SOUND
        payload = {name: (output / "after.class").read_bytes(),
                   sounds_name: (json.dumps(sounds, ensure_ascii=False, indent=2) + "\n").encode(),
                   "assets/tnc/sounds/music/boss/yan_crywolf.ogg": audio.read_bytes()}
        jar = output / "tnc-1.0.0.jar"
        with zipfile.ZipFile(jar, "w") as dst:
            for info in src.infolist():
                dst.writestr(info, payload.get(info.filename, src.read(info.filename)))
            for n in payload.keys() - set(src.namelist()):
                dst.writestr(n, payload[n], compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(jar) as dst:
        assert dst.testzip() is None
        assert all(src.read(n) == dst.read(n) for n in src.namelist() if n not in payload)
    assert digest(base) == before_hash, "Live mod changed while preparing"
    manifest = {"jar": str(jar), "baseInstalledHash": before_hash,
                "files": [{"source": str(jar), "relative": "mods\\tnc-1.0.0.jar", "hash": digest(jar)}]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("Prepared Yan battle music; unrelated entries unchanged; SHA256", digest(jar))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audio", type=Path, help="Optional local Crywolf source audio")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    if args.audio:
        target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/sounds/music/boss/yan_crywolf.ogg"
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([args.ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(args.audio),
                        "-vn", "-map_metadata", "-1", "-ac", "2", "-ar", "44100", "-c:a", "libvorbis",
                        "-q:a", "5", str(target)], check=True)
    prepare(args.live, args.output)
