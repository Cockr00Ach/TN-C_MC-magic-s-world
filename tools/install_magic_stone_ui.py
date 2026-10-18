"""Install the native-audited magic-stone UI JAR, preserving quests and worlds."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from install_life_routes import running

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def quests(root):
    return {p.relative_to(root).as_posix(): digest(p) for p in root.rglob('*.snbt')}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live).resolve()
    destination = live / 'mods/tnc-1.0.0.jar'
    assert destination.is_file()
    jar = ROOT / 'build/libs/tnc-1.0.0.jar'
    latest = max(p.stat().st_mtime for directory in ['src/main/java', 'src/main/resources']
                 for p in (ROOT / directory).rglob('*') if p.is_file())
    assert jar.stat().st_mtime >= latest, 'Rebuild the latest source first'
    report_path = ROOT / 'work/magic-stone-ui-audit/report.json'
    report = json.loads(report_path.read_text('utf8'))
    assert report_path.stat().st_mtime >= latest
    assert not report['failures'] and report['screenshots'] == 8 and report['physical_clicks'] >= 12
    assert len(report['scenes']) == 8 and report['hover_frames'] >= 10
    units = 0
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        result = ET.parse(path).getroot()
        assert int(result.attrib['failures']) == int(result.attrib['errors']) == 0
        units += int(result.attrib['tests'])
    assert units >= 268
    assert not running(live), 'Game running; close it before installing'
    if not args.apply:
        print('Validated; --apply installs')
        return
    quest_root = live / 'config/ftbquests/quests'
    before = quests(quest_root)
    backup = ROOT / 'work/backups' / ('magic-stone-ui-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    backup.mkdir(parents=True)
    shutil.copy2(destination, backup / destination.name)
    shutil.copy2(jar, destination)
    mirror = ROOT / 'modpack' / live.name / 'mods' / destination.name
    shutil.copy2(jar, mirror)
    assert digest(jar) == digest(destination) == digest(mirror)
    assert quests(quest_root) == before
    receipt = {'installed_at': datetime.datetime.now().isoformat(), 'live': str(live),
               'jar_sha256': digest(jar), 'jar_bytes': jar.stat().st_size, 'unit_tests': units,
               'native_report': report, 'protected_quest_files_unchanged': len(before),
               'saves_modified': False, 'island_templates_modified': False, 'backup': str(backup)}
    (ROOT / 'release/magic-stone-ui-install-20261018.json').write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', 'utf8')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
