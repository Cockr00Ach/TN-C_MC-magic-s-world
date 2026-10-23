"""Install the geometric Thunder Seal sigil without touching other spell visuals."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RENDERER = 'com/tnc/tnc/magic/thunderseal/client/ThunderSealRenderer.class'
ADDITIONS = ['com/tnc/tnc/magic/thunderseal/client/ThunderSealSigil.class',
             'assets/tnc/textures/entity/thunder_seal_ink.png']


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def prepare(live, built, output):
    output.mkdir(parents=True, exist_ok=True)
    base = live / 'mods/tnc-1.0.0.jar'
    before = digest(base)
    previous = ROOT / 'work/thunder-seal-visual-install-20261023/tnc-1.0.0.jar'
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(built) as donor, zipfile.ZipFile(previous) as known:
        assert src.read(RENDERER) == known.read(RENDERER), 'Renderer changed; rebase before install'
        assert not set(ADDITIONS) & set(src.namelist()), 'New sigil already installed'
        payload = {name: donor.read(name) for name in [RENDERER, *ADDITIONS]}
        jar = output / 'tnc-1.0.0.jar'
        with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as dst:
            for entry in src.infolist():
                dst.writestr(entry, payload.get(entry.filename, src.read(entry)))
            for name in ADDITIONS:
                dst.writestr(name, payload[name])
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(jar) as dst:
        assert dst.testzip() is None
        assert set(dst.namelist()) == set(src.namelist()) | set(ADDITIONS)
        assert all(src.read(n) == dst.read(n) for n in src.namelist() if n != RENDERER)
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
