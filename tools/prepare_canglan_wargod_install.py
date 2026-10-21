"""Scoped war-god overlay. Preserve all other bosses, music, spells and data."""
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
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(built) as compiled:
        classes = {n for n in compiled.namelist() if n.startswith("com/tnc/tnc/world/wind/")
                   and "/Canglan" in n and n.endswith(".class") and "GameTests" not in n}
        classes |= {"com/tnc/tnc/magic/wind/WindVolleyEntity.class", "com/tnc/tnc/client/WindChainRenderers$Volley.class"}
        assert len(classes) >= 15, classes
        payload = {n: compiled.read(n) for n in classes}
        # Only change the shared hostile-caster targeting predicate, keeping every other live spell method.
        spell = "com/tnc/tnc/magic/wind/WindChainSpells.class"
        (output / "spell-original.class").write_bytes(src.read(spell))
        (output / "spell-donor.class").write_bytes(compiled.read(spell))
        subprocess.run([str(root / ".jdk17/bin/java.exe"), "-cp", str(asm) + ";" + str(output),
                        "WireBossMusicMethod", str(output / "spell-original.class"), str(output / "spell-donor.class"),
                        str(output / "spell-patched.class"), "enemy"], check=True)
        payload[spell] = (output / "spell-patched.class").read_bytes()
        fields, methods = members(src.read(spell)); newfields, newmethods = members(payload[spell])
        assert fields == newfields and methods.keys() == newmethods.keys()
        assert all(raw == newmethods[k] for k, raw in methods.items() if k[0] != "enemy")
        # The installed pack may predate the palm-anchor helper present in current sources.
        grip = "com/tnc/tnc/client/WindConstructModels.class"
        (output / "grip-original.class").write_bytes(src.read(grip))
        (output / "grip-donor.class").write_bytes(compiled.read(grip))
        subprocess.run([str(root / ".jdk17/bin/java.exe"), "-cp", str(asm) + ";" + str(output),
                        "WireBossMusicMethod", str(output / "grip-original.class"), str(output / "grip-donor.class"),
                        str(output / "grip-patched.class"), "atBone", "allow-add"], check=True)
        payload[grip] = (output / "grip-patched.class").read_bytes()
        fields, methods = members(src.read(grip)); newfields, newmethods = members(payload[grip])
        assert fields == newfields
        assert all(raw == newmethods[k] for k, raw in methods.items() if k[0] != "atBone")
        resources = ["geo/entity/canglan_warlord.geo.json", "geo/entity/canglan_echo.geo.json",
                     "animations/entity/canglan_warlord.animation.json", "textures/entity/canglan_warlord.png",
                     "models/projectile/canglan_gale_wave.json", "textures/spell_projectile/canglan_gale_wave.png"]
        for n in resources:
            payload["assets/tnc/" + n] = (root / "src/main/resources/assets/tnc" / n).read_bytes()
        jar = output / "tnc-1.0.0.jar"
        with zipfile.ZipFile(jar, "w") as dst:
            for info in src.infolist():
                dst.writestr(info, payload.get(info.filename, src.read(info.filename)))
            for n in payload.keys() - set(src.namelist()):
                dst.writestr(n, payload[n], compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(jar) as dst:
        assert dst.testzip() is None
        assert set(src.namelist()) <= set(dst.namelist())
        assert all(src.read(n) == dst.read(n) for n in src.namelist() if n not in payload)
    assert digest(base) == base_hash, "The live mod changed during packaging"
    manifest = {"jar": str(jar), "baseInstalledHash": base_hash,
                "files": [{"source": str(jar), "relative": "mods\\tnc-1.0.0.jar", "hash": digest(jar)}]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (output / "provenance.json").write_text(json.dumps({"entries": sorted(payload), "baseHash": base_hash,
                                                        "releaseHash": digest(jar)}, indent=2), encoding="utf-8")
    print("Prepared scoped Canglan war-god:", len(classes), "classes,", len(resources), "assets;", digest(jar))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", type=Path, required=True)
    parser.add_argument("--built", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.live, args.built, args.output)
