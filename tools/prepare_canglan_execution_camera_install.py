"""Overlay only the execution-camera fix onto the current formal installation."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ENTRIES = (
    'com/tnc/tnc/world/wind/CanglanWarlordCombat.class',
    'com/tnc/tnc/world/wind/CanglanWarGodRules.class',
    'com/tnc/tnc/world/wind/client/CanglanCinematicCamera.class',
)
EXPECTED_BASE = '645EBA24A7442D2FB1F6AA194EFD58EA023FBDA8DE0D6FC29B1462C80B880371'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def prepare(live, built, output):
    base = live / 'mods/tnc-1.0.0.jar'
    before = digest(base)
    assert before == EXPECTED_BASE, 'Formal jar changed; inspect and rebase the scoped patch'
    output.mkdir(parents=True, exist_ok=False)
    target = output / 'tnc-1.0.0.jar'
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(built) as compiled:
        payload = {name: compiled.read(name) for name in ENTRIES}
        assert all(original.read(name) != data for name, data in payload.items())
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as result:
            for entry in original.infolist():
                result.writestr(copy.copy(entry), payload.get(entry.filename, original.read(entry.filename)))
        with zipfile.ZipFile(target) as result:
            assert result.testzip() is None
            assert result.namelist() == original.namelist()
            unchanged = [name for name in original.namelist() if name not in payload]
            assert all(original.read(name) == result.read(name) for name in unchanged)
            assert all(result.read(name) == data for name, data in payload.items())
    assert digest(base) == before, 'Formal jar changed during preparation'
    manifest = {
        'baseInstalledHash': before,
        'files': [{'source': str(target), 'relative': 'mods/tnc-1.0.0.jar',
                   'hash': digest(target), 'expectedLiveHash': before}],
        'patchEntries': list(ENTRIES),
        'unrelatedEntriesByteIdentical': len(unchanged),
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
