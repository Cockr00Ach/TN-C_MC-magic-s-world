"""Install the screenshot-verified dungeon UI jar without writing quests or saves."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile
from install_life_routes import running

ROOT = Path(__file__).resolve().parents[1]
CLASS = 'com/tnc/tnc/dungeon/client/DungeonScreen.class'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def quests(pack):
    base = pack / 'config/ftbquests/quests'
    return {p.relative_to(base).as_posix(): sha(p) for p in base.rglob('*.snbt')}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live).resolve()
    mirror = ROOT / 'modpack' / live.name
    jar = ROOT / 'build/libs/tnc-1.0.0.jar'
    latest = max(p.stat().st_mtime for folder in ['src/main/java', 'src/main/resources']
                 for p in (ROOT / folder).rglob('*') if p.is_file())
    assert jar.stat().st_mtime >= latest, 'Rebuild current sources first'
    log = ROOT / 'work/dungeon-ui-build.log'
    assert log.stat().st_mtime >= latest and 'BUILD SUCCESSFUL' in log.read_text('utf8', errors='replace')
    tests = 0
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        root = ET.parse(path).getroot()
        assert int(root.attrib['failures']) == int(root.attrib['errors']) == 0
        tests += int(root.attrib['tests'])
    assert tests >= 277, 'Expected full unit suite'
    report_path = ROOT / 'work/dungeon-ui-audit/report.json'
    report = json.loads(report_path.read_text('utf8'))
    assert report_path.stat().st_mtime >= latest and not report['failures']
    assert report['screenshots'] == len(report['scenes']) == 14
    assert len(list(report_path.parent.glob('*.png'))) == 14
    with zipfile.ZipFile(jar) as archive:
        assert hashlib.sha256(archive.read(CLASS)).hexdigest() == report['screen_class_sha256'], 'UI screenshots do not match this jar'
        assert len([n for n in archive.namelist() if n.startswith('data/tnc/structures/dungeon_gates/') and n.endswith('.nbt')]) == 67
    assert not running(live), 'Minecraft is running; fully close before replacing the jar'
    if not args.apply:
        print('Validated; pass --apply to install the UI jar only')
        return
    targets = [live, mirror]
    before = [quests(pack) for pack in targets]
    assert before[0], 'No live quest files found'
    backup = ROOT / 'work/backups' / ('dungeon-ui-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    backup.mkdir(parents=True)
    for index, pack in enumerate(targets):
        target = pack / 'mods/tnc-1.0.0.jar'
        assert target.is_file(), target
        shutil.copy2(target, backup / ('live.jar' if index == 0 else 'mirror.jar'))
        shutil.copy2(jar, target)
        assert sha(target) == sha(jar) and quests(pack) == before[index]
    files = {'mods/tnc-1.0.0.jar': sha(jar)}
    receipt = {
        'installed_at': datetime.datetime.now().isoformat(), 'jar_sha256': sha(jar),
        'jar_bytes': jar.stat().st_size, 'files': files, 'unit_tests': tests,
        'native_ui_report': report, 'protected_quest_files_unchanged': len(before[0]),
        'backup': str(backup), 'saves_modified': False, 'island_templates_modified': False,
        'monster_behavior_modified': False,
        'scope': 'Dungeon screens and HUD only; previous gameplay verification remains in dungeons-validation-20261019.json'
    }
    (ROOT / 'release/dungeon-ui-install-20261019.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', 'utf8')
    bundle = ROOT / 'work/dungeons-ui-update-20261019.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as archive:
        archive.write(jar, 'mods/tnc-1.0.0.jar')
        archive.write(ROOT / 'tools/apply_dungeon_update.ps1', 'apply_dungeon_update.ps1')
        archive.writestr('manifest.json', json.dumps({'files': files}, indent=2))
    print(json.dumps({'installed': True, 'unit_tests': tests, 'screenshots': 14,
                      'preserved_quests': len(before[0]), 'bundle': str(bundle),
                      'sha256': sha(jar)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
