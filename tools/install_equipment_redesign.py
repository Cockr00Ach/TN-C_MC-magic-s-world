"""Install the exact equipment JAR verified in the isolated full-pack client."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil, xml.etree.ElementTree as ET
from install_life_routes import running

R = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--live', required=True)
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    live = Path(args.live)
    assert (live / 'mods').is_dir(), 'Expected an existing Minecraft instance'
    jar = R / 'build/libs/tnc-1.0.0.jar'
    latest = max(p.stat().st_mtime for base in ('src/main/java', 'src/main/resources')
                 for p in (R / base).rglob('*') if p.is_file())
    assert jar.stat().st_mtime >= latest, 'Rebuild changed sources first'
    build_log = (R / 'work/equipment-build.log').read_text('utf8', errors='replace')
    assert 'BUILD SUCCESSFUL' in build_log, 'Equipment build must pass'
    count = 0
    for path in (R / 'build/test-results/test').glob('TEST-*.xml'):
        root = ET.parse(path).getroot()
        assert int(root.attrib['failures']) == int(root.attrib['errors']) == 0, path
        count += int(root.attrib['tests'])
    assert count >= 416, 'Missing regression tests'
    icons = json.loads((R / 'src/main/resources/assets/tnc/equipment/icon-integrity.json').read_text('utf8'))
    for name, expected in icons.items():
        assert sha(R / 'src/main/resources/assets/tnc' / name) == expected, name
    jar_sha = sha(jar)
    reports = {}
    for mode in ('vanilla', 'shaders'):
        path = R / f'work/equipment-native-{mode}/report.json'
        report = json.loads(path.read_text('utf8'))
        assert report['jar_sha256'] == jar_sha, 'Native checks used a different JAR'
        assert not report['failures'], report['failures']
        assert report['expected'] == len(report['screenshots']) == 85
        assert all((path.parent / name).is_file() for name in report['screenshots'])
        reports[mode] = len(report['screenshots'])
    assert not running(live), 'Save and fully close the live Minecraft client first'
    result = dict(jar_sha256=jar_sha, unit_tests=count, approved_icons=len(icons),
                  native_screenshots=reports, files=['mods/tnc-1.0.0.jar'])
    if args.apply:
        backup = R / 'work/backups' / ('equipment-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        for label, pack in [('live', live), ('mirror', R / 'modpack' / live.name)]:
            dest = pack / 'mods/tnc-1.0.0.jar'
            if dest.exists():
                saved = backup / label / 'mods/tnc-1.0.0.jar'
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(dest, saved)
            dest.parent.mkdir(parents=True, exist_ok=True)
            temp = dest.with_name(dest.name + '.equipment-new')
            shutil.copy2(jar, temp)
            temp.replace(dest)
            assert sha(dest) == jar_sha
        result['backup'] = str(backup)
        (R / 'work/equipment-install-report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), 'utf8')
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
