"""Install only the reviewed ecology art JAR, preserving quests/worlds/islands."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

R = Path(__file__).resolve().parents[1]
LIVE = Path("D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915")
MIRROR = R / "modpack/元素觉醒1.4.3-魔改版-20260915"
ART = R / "art/ecology-redesign-20261004"
JAR = R / "build/libs/tnc-1.0.0.jar"

def digest(data):
    return hashlib.sha256(data).hexdigest().upper()

def sha(path):
    return digest(path.read_bytes())

def snapshot(root):
    selected = []
    for rel in ["config/ftbquests/quests/chapters", "kubejs/data/tnc/sky_island", "kubejs/data/tnc/structures/sky_island"]:
        selected += [p for p in (root / rel).rglob("*") if p.is_file()]
    selected += list((root / "saves").rglob("level.dat"))
    return {str(p): sha(p) for p in selected}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true")
    args = ap.parse_args()
    client = json.loads((R / "work/ecology-visual-client-audit.json").read_text())
    assert client["failures"] == [] and client["screenshots"] == 14
    assert client["items"] == 147 and client["plant_species"] == 29 and client["feed_models"] == 7
    assert "BUILD SUCCESSFUL" in (R / "work/ecology-visual-client-run.log").read_text(encoding="utf8", errors="replace")
    unit_tests = failures = errors = 0
    for path in (R / "build/test-results/test").glob("TEST-*.xml"):
        suite = ET.parse(path).getroot()
        unit_tests += int(suite.attrib["tests"])
        failures += int(suite.attrib["failures"])
        errors += int(suite.attrib["errors"])
    assert unit_tests >= 130 and failures == 0 and errors == 0
    sprites = json.loads((ART / "export-audit.json").read_text())["ids"]
    models = json.loads((ART / "model-audit.json").read_text())["ids"]
    source = R / "src/main/resources"
    resources = [source / f"assets/tnc/textures/item/{name}.png" for name in sprites]
    resources += list((source / "assets/tnc/textures/block").glob("art_*.png"))
    resources += [source / f"assets/tnc/models/block/{name}.json" for name in models]
    resources += [source / f"assets/tnc/models/item/{name}.json" for name in models if (source / f"assets/tnc/models/item/{name}.json").exists()]
    assert len(sprites) == 147 and len(set(sprites)) == 147 and len(models) == 417
    assert JAR.stat().st_mtime >= max(p.stat().st_mtime for p in resources)
    with zipfile.ZipFile(JAR) as z, zipfile.ZipFile(LIVE / "mods/tnc-1.0.0.jar") as old:
        assert z.testzip() is None
        for p in resources:
            assert z.read(p.relative_to(source).as_posix()) == p.read_bytes(), str(p)
        for n in ["com/tnc/tnc/life/pasture/client/PastureFacilityRenderer.class", "com/tnc/tnc/life/pasture/client/PastureClientEvents.class"]:
            assert n in z.namelist()
        protected_jar = [n for n in old.namelist() if "SkyIsland" in n or "/sky_island/" in n]
        assert protected_jar
        for n in protected_jar:
            assert z.read(n) == old.read(n), "Island JAR entry changed: " + n
        jar_json = 0
        for n in z.namelist():
            if n.endswith(".json") and n.startswith(("assets/tnc/", "data/tnc/")):
                json.loads(z.read(n))
                jar_json += 1
    # Loose resource packs must not hide the new art.
    overrides = []
    for root in [LIVE / "kubejs", LIVE / "resources", LIVE / "config/openloader/resources"]:
        for p in root.rglob("*"):
            if not p.is_file() or "assets/tnc/" not in p.as_posix():
                continue
            relative = p.as_posix().split("assets/tnc/", 1)[1]
            if any(q.relative_to(source / "assets/tnc").as_posix() == relative for q in resources):
                overrides.append(str(p))
    assert not overrides, "Old loose art overrides: " + str(overrides)
    gamecheck = subprocess.run(["powershell", "-NoProfile", "-Command",
        "@(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match '元素觉醒1\\.4\\.3-魔改版-20260915' }).Count"],
        capture_output=True, text=True)
    assert gamecheck.returncode == 0 and gamecheck.stdout.strip() == "0", "Live game is running"
    print("Verified", len(resources), "source resources,", unit_tests, "unit tests, 14 client screenshots; protected", len(protected_jar), "island JAR entries")
    if not args.install:
        return
    before = snapshot(LIVE) | snapshot(MIRROR)
    backup = R / "work/backups" / ("ecology-art-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    backup.mkdir(parents=True)
    installed = []
    try:
        for label, root in [("live", LIVE), ("mirror", MIRROR)]:
            target = root / "mods/tnc-1.0.0.jar"
            saved = backup / label / "tnc-1.0.0.jar"
            saved.parent.mkdir(parents=True)
            shutil.copy2(target, saved)
            installed.append((target, saved))
            shutil.copy2(JAR, target)
            assert sha(target) == sha(JAR)
        assert snapshot(LIVE) | snapshot(MIRROR) == before, "Protected files changed"
    except Exception:
        for target, saved in installed:
            shutil.copy2(saved, target)
        raise
    receipt = {
        "installed_at": datetime.datetime.now().isoformat(),
        "live_pack": str(LIVE), "sha256": sha(JAR), "backup": str(backup),
        "sprites": 147, "plant_species": 29, "native_models": 417, "feed_meshes": 7,
        "unit_tests": unit_tests, "client_audit": client, "verified_source_resources": len(resources),
        "protected_jar_island_entries": len(protected_jar), "jar_json_parsed": jar_json,
        "protected_hashes": before, "installed_files": [str(t) for t, s in installed],
        "quests_changed": False, "player_worlds_changed": False, "island_templates_changed": False
    }
    (R / "work/ecology-art-install-receipt.json").write_bytes((json.dumps(receipt, ensure_ascii=False, indent=2) + "\n").encode())
    print("INSTALLED", receipt["sha256"], "files", len(installed), "protected files", len(before))

if __name__ == "__main__":
    main()
