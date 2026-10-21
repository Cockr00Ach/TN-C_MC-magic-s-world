"""Prepare a scoped Earth/Wind BGM overlay using locally imported Vorbis tracks."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile
from prepare_dungeon_mob_install import members


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def prepare(live, built, output):
    root = Path(__file__).resolve().parents[1]
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    base = live / "mods/tnc-1.0.0.jar"
    base_hash = digest(base)
    asm = next((root / ".gradle-user/wrapper/dists").rglob("asm-9.7.jar"))
    subprocess.run([str(root / ".jdk17/bin/javac.exe"), "-cp", str(asm), "-d", str(output),
                    str(root / "tools/WireBossMusicMethod.java")], check=True)
    controller = "com/tnc/tnc/client/TravelMusicController.class"
    selector = "com/tnc/tnc/client/BossBattleMusicSelector.class"
    sound_name = "assets/tnc/sounds.json"
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(built) as compiled:
        (output / "original.class").write_bytes(src.read(controller))
        (output / "compiled.class").write_bytes(compiled.read(controller))
        subprocess.run([str(root / ".jdk17/bin/java.exe"), "-cp", str(asm) + ";" + str(output),
                        "WireBossMusicMethod", str(output / "original.class"), str(output / "compiled.class"),
                        str(output / "patched.class")], check=True)
        fields, methods = members(src.read(controller))
        newfields, newmethods = members((output / "patched.class").read_bytes())
        assert fields == newfields and methods.keys() == newmethods.keys()
        assert all(raw == newmethods[k] for k, raw in methods.items() if k[0] != "tickBossMusic")
        sounds = json.loads(src.read(sound_name))
        local_sounds_path = root / "src/main/resources" / sound_name
        local_sounds = json.loads(local_sounds_path.read_text(encoding="utf-8-sig"))
        payload = {controller: (output / "patched.class").read_bytes(), selector: compiled.read(selector)}
        for track in ["earth_dawn", "wind_phoenix"]:
            event = "music.boss." + track
            entry = {"sounds": [{"name": "tnc:music/boss/" + track, "stream": True}]}
            sounds[event] = entry
            local_sounds[event] = entry
            name = "assets/tnc/sounds/music/boss/" + track + ".ogg"
            payload[name] = (root / "src/main/resources" / name).read_bytes()
            assert payload[name][:4] == b"OggS"
        local_sounds_path.write_text(json.dumps(local_sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        payload[sound_name] = (json.dumps(sounds, ensure_ascii=False, indent=2) + "\n").encode()
        jar = output / "tnc-1.0.0.jar"
        with zipfile.ZipFile(jar, "w") as dst:
            for info in src.infolist():
                dst.writestr(info, payload.get(info.filename, src.read(info.filename)))
            for n in payload.keys() - set(src.namelist()):
                dst.writestr(n, payload[n], compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(jar) as dst:
        assert dst.testzip() is None
        assert all(src.read(n) == dst.read(n) for n in src.namelist() if n not in payload)
        assert dst.read("assets/tnc/sounds/music/boss/yan_crywolf.ogg") == src.read("assets/tnc/sounds/music/boss/yan_crywolf.ogg")
    assert digest(base) == base_hash, "Another installation changed the live base"
    manifest = {"jar": str(jar), "baseInstalledHash": base_hash,
                "files": [{"source": str(jar), "relative": "mods\\tnc-1.0.0.jar", "hash": digest(jar)}]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (output / "provenance.json").write_text(json.dumps({"entries": sorted(payload), "baseHash": base_hash,
                                                        "releaseHash": digest(jar)}, indent=2), encoding="utf-8")
    print("Earth and Wind BGM prepared; unrelated entries and Yan track unchanged:", digest(jar))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", type=Path, required=True)
    parser.add_argument("--built", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.live, args.built, args.output)
