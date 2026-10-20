"""Install the verified journey/alchemy release, preserving all authored quest pages."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile
from install_life_routes import running

ROOT = Path(__file__).resolve().parents[1]
CHAPTER = 'config/ftbquests/quests/chapters/tnc_alchemy.snbt'
PATCH = {'launchIntoDestiny': False, 'allowDestinyTeleportation': False}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def protected_quests(pack):
    base = pack / 'config/ftbquests/quests'
    return {p.relative_to(base).as_posix(): sha(p) for p in base.rglob('*')
            if p.is_file() and p.relative_to(base).as_posix() != 'chapters/tnc_alchemy.snbt'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live).resolve()
    mirror = ROOT / 'modpack' / live.name
    jar = ROOT / 'build/libs/tnc-1.0.0.jar'
    chapter = ROOT / 'questbook/ftbquests/chapters/tnc_alchemy.snbt'
    latest = max(p.stat().st_mtime for folder in ['src/main/java', 'src/main/resources']
                 for p in (ROOT / folder).rglob('*') if p.is_file())
    latest = max(latest, chapter.stat().st_mtime)
    assert jar.stat().st_mtime >= latest, 'Rebuild current runtime sources first'
    log = ROOT / 'work/life-expedition-tests.log'
    content = log.read_text('utf8', errors='replace')
    assert log.stat().st_mtime >= latest and 'BUILD SUCCESSFUL' in content
    assert 'All 11 required tests passed' in content, 'Actual journey GameTests must run'
    dungeon_log = ROOT / 'work/life-expedition-dungeon-tests.log'
    dungeon_content = dungeon_log.read_text('utf8', errors='replace')
    assert dungeon_log.stat().st_mtime >= latest and 'BUILD SUCCESSFUL' in dungeon_content
    dungeon_count = re.search(r'All (\d+) required tests passed', dungeon_content)
    assert dungeon_count and int(dungeon_count[1]) >= 14, 'Expected actual dungeon regression tests'
    tests = 0
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        root = ET.parse(path).getroot()
        assert int(root.attrib['failures']) == int(root.attrib['errors']) == 0
        tests += int(root.attrib['tests'])
    assert tests >= 295, 'Expected complete unit suite'
    report_path = ROOT / 'work/life-expedition-native/report.json'
    report = json.loads(report_path.read_text('utf8'))
    assert report_path.stat().st_mtime >= latest and not report['failures']
    assert report['screenshots'] == 11 and len(list(report_path.parent.glob('*.png'))) == 11
    assert report['checks'] and all(report['checks'].values())
    assert report['checks'].get('actual_ftb_chapter_loaded'), 'Actual full-pack FTB chapter load required'
    with zipfile.ZipFile(jar) as archive:
        for name, value in report['class_sha256'].items():
            assert hashlib.sha256(archive.read(name.replace('.', '/') + '.class')).hexdigest() == value
        assert len([n for n in archive.namelist() if n.startswith('data/tnc/structures/dungeon_gates/') and n.endswith('.nbt')]) == 67
    assert (live / 'mods/tnc-1.0.0.jar').is_file() and mirror.is_dir()
    assert not running(live), 'Minecraft/test JVM running; close before replacing the jar'
    targets = [('live', live), ('mirror', mirror)]
    before = {name: protected_quests(pack) for name, pack in targets}
    assert before['live'], 'No existing quest book found'
    configs = {}
    for name, pack in targets:
        path = pack / 'config/mca.json'
        config = json.loads(path.read_text('utf-8-sig')) if path.exists() else {}
        assert isinstance(config, dict)
        configs[name] = config
    if not args.apply:
        print(json.dumps({'validated': True, 'unit_tests': tests, 'journey_tests': 11,
                          'dungeon_tests': int(dungeon_count[1]), 'native_screenshots': 11,
                          'protected_quest_files': len(before['live'])}, ensure_ascii=False))
        return
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = ROOT / 'work/backups' / ('life-expedition-' + stamp)
    backup.mkdir(parents=True)
    installed = []
    for name, pack in targets:
        saved = backup / name
        saved.mkdir()
        quest_root = pack / 'config/ftbquests/quests'
        if quest_root.exists():
            shutil.copytree(quest_root, saved / 'quests-full')
        for rel in ['mods/tnc-1.0.0.jar', 'config/mca.json']:
            source = pack / rel
            if source.exists():
                dest = saved / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, dest)
        shutil.copy2(jar, pack / 'mods/tnc-1.0.0.jar')
        dest = pack / CHAPTER
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(chapter, dest)
        config = {**configs[name], **PATCH}
        (pack / 'config/mca.json').write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n', 'utf8')
        assert sha(pack / 'mods/tnc-1.0.0.jar') == sha(jar) and sha(dest) == sha(chapter)
        assert protected_quests(pack) == before[name], 'An existing quest file changed'
        after_config = json.loads((pack / 'config/mca.json').read_text('utf8'))
        assert {k: v for k, v in after_config.items() if k not in PATCH} == {k: v for k, v in configs[name].items() if k not in PATCH}
        installed.append({'target': name, 'root': str(pack), 'protected_quest_files_unchanged': len(before[name])})
    files = {'mods/tnc-1.0.0.jar': sha(jar), CHAPTER: sha(chapter)}
    validation = {'validated_at': datetime.datetime.now().isoformat(), 'jar_sha256': sha(jar),
                  'unit_tests': tests, 'journey_gametests': 11, 'dungeon_gametests': int(dungeon_count[1]),
                  'native': report, 'native_ftb_quests': 22, 'review': 'Two independent read-only reviewers; important findings fixed and regression-tested'}
    receipt = {'installed_at': datetime.datetime.now().isoformat(), 'jar_sha256': sha(jar),
               'jar_bytes': jar.stat().st_size, 'files': files, 'mca_config_patch': PATCH,
               'backup': str(backup), 'installed': installed, 'saves_modified': False,
               'island_templates_modified': False, 'additional_third_party_jars': []}
    release = ROOT / 'release'
    release.mkdir(exist_ok=True)
    (release / 'life-expedition-validation-20261020.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n', 'utf8')
    (release / 'life-expedition-install-20261020.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', 'utf8')
    bundle = ROOT / 'work/life-expedition-update-20261020.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as archive:
        archive.write(jar, 'mods/tnc-1.0.0.jar')
        archive.write(chapter, CHAPTER)
        archive.write(ROOT / 'tools/apply_life_expedition_update.ps1', 'apply_life_expedition_update.ps1')
        archive.writestr('manifest.json', json.dumps({'files': files, 'mca_config_patch': PATCH}, indent=2))
    print(json.dumps({'installed': True, 'unit_tests': tests, 'journey_tests': 11,
                      'dungeon_tests': int(dungeon_count[1]), 'native_screenshots': 11,
                      'preserved_quests': len(before['live']), 'bundle': str(bundle),
                      'sha256': sha(jar)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
