"""Replace only HUD artwork on the installed release. Preserve every combat/resource entry."""
import argparse, copy, hashlib, json, zipfile
from pathlib import Path

EXPECTED = '825B0F84491C58014DF5D93FC301F47490E61D4BB8959FACC5C0D292BA1D69C6'
PREFIX = 'com/tnc/tnc/bosshud/client/'
ENTRIES = {PREFIX + n + '.class' for n in ('EpicBossBarArt', 'BossBarEngraving', 'BossBarEngraving$1')}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()

def prepare(live, built, output):
    base = live / 'mods/tnc-1.0.0.jar'
    before = digest(base)
    assert before == EXPECTED, 'Formal release changed; inspect before rebasing the art patch'
    output.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(built) as new:
        payload = {n: new.read(n) for n in ENTRIES}
        assert PREFIX + 'BossBarEngraving.class' not in old.namelist()
        target = output / 'tnc-1.0.0.jar'
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as merged:
            for info in old.infolist():
                merged.writestr(copy.copy(info), payload.get(info.filename, old.read(info.filename)))
            for name in sorted(ENTRIES - set(old.namelist())):
                merged.writestr(name, payload[name])
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            changed = [n for n in old.namelist() if old.read(n) != merged.read(n)]
            assert changed == [PREFIX + 'EpicBossBarArt.class'], changed
            assert all(merged.read(n) == data for n, data in payload.items())
            assert set(merged.namelist()) - set(old.namelist()) == ENTRIES - set(old.namelist())
            unchanged = len(old.namelist()) - len(changed)
    assert digest(base) == before
    manifest = {
        'baseInstalledHash': before,
        'files': [{'source': str(target), 'relative': 'mods/tnc-1.0.0.jar',
                   'hash': digest(target), 'expectedLiveHash': before}],
        'patchEntries': sorted(ENTRIES), 'unrelatedEntriesByteIdentical': unchanged,
        'combatHealthNetworkResourcesAndSavesUntouched': True,
        'volcanicTortoiseUsesUnchangedOriginalDrawingPath': True,
    }
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', type=Path, required=True)
    parser.add_argument('--built', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.live.resolve(), args.built.resolve(), args.output.resolve())
