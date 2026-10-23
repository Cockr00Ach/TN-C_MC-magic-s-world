"""Replace only owned Thunder Seal visuals in the currently installed mod."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RENDERER = 'com/tnc/tnc/magic/thunderseal/client/ThunderSealRenderer.class'
MODELS = ['assets/tnc/models/' + kind + '/thunder_seal_pylon.json' for kind in ['item', 'spell']]
TEXTURES = ['assets/tnc/textures/entity/thunder_seal_circle.png'] + [
    'assets/tnc/textures/block/thunder_seal/' + key + '.png' for key in ['dark', 'gold', 'blue', 'gem']]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def prepare(live, built, output):
    output.mkdir(parents=True, exist_ok=True)
    base = live / 'mods/tnc-1.0.0.jar'
    before = digest(base)
    prior = ROOT / 'work/thunder-seal-install-20261023/tnc-1.0.0.jar'
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(built) as donor, zipfile.ZipFile(prior) as known:
        payload = {name: donor.read(name) for name in [RENDERER, *MODELS, *TEXTURES]}
        for name in [RENDERER, *MODELS]:
            assert src.read(name) == known.read(name), 'Owned visual changed; rebase before installation: ' + name
        for name in TEXTURES:
            assert name not in src.namelist(), 'New texture already installed: ' + name
        jar = output / 'tnc-1.0.0.jar'
        with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as dst:
            for entry in src.infolist():
                dst.writestr(entry, payload.get(entry.filename, src.read(entry)))
            for name in TEXTURES:
                dst.writestr(name, payload[name])
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(jar) as dst:
        assert dst.testzip() is None
        assert set(dst.namelist()) == set(src.namelist()) | set(TEXTURES)
        assert all(src.read(n) == dst.read(n) for n in src.namelist() if n not in payload)
        # The preview harness and test code must never be shipped.
        assert not any('thunderseal' in n and ('ClientAudit' in n or 'GameTests' in n) for n in dst.namelist())
    assert digest(base) == before
    release = digest(jar)
    manifest = {'jar': str(jar.resolve()), 'baseInstalledHash': before,
                'files': [{'source': str(jar.resolve()), 'relative': 'mods\\tnc-1.0.0.jar', 'hash': release}]}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    (output / 'provenance.json').write_text(json.dumps({'entries': list(payload), 'baseHash': before,
                                                     'releaseHash': release}, indent=2), encoding='utf-8')
    print(release)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', type=Path, required=True)
    parser.add_argument('--built', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.live, args.built, args.output)
