"""Prepare an isolated test using existing installed FTB jars, without changing saves."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
from verify_life_routes import run

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    args = parser.parse_args()
    live = Path(args.live).resolve()
    run()
    jars = {
        'ftb-quests-forge-2001.4.22.jar': 'ftb-quests-forge-2001.4.22.jar',
        'ftb-library-forge-2001.2.13.jar': 'ftb-library-forge-2001.2.13.jar',
        'ftb-teams-forge-2001.3.2.jar': 'ftb-teams-forge-2001.3.2.jar',
        'architectury-9.2.14-forge.jar': 'architectury-9.2.14.jar',
    }
    for source in jars:
        if not (live / 'mods' / source).is_file():
            raise FileNotFoundError(live / 'mods' / source)
    (ROOT / 'libs').mkdir(exist_ok=True)
    for source, target in jars.items():
        shutil.copy2(live / 'mods' / source, ROOT / 'libs' / target)
    target = ROOT / 'work/ftb-route-load-test/config/ftbquests/quests'
    (target / 'chapters').mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name in ('tnc_field_02_botany', 'tnc_field_03_pasture',
                 'tnc_field_04_workshop', 'tnc_field_05_seasons'):
        shutil.copy2(ROOT / 'questbook/ftbquests/chapters' / (name + '.snbt'),
                     target / 'chapters' / (name + '.snbt'))
        source = ROOT / 'questbook/ftbquests/chapters' / (name + '.snbt')
        hashes[name + '.snbt'] = hashlib.sha256(source.read_bytes()).hexdigest()
    # FTB rewrites its live fixture on shutdown; preserve the exact tested input hashes.
    (ROOT / 'work/ftb-route-load-input.json').write_text(
        json.dumps(hashes, indent=2), encoding='utf-8')
    groups = {'chapter_groups': [{'id': '544E434C49464531',
                                 'title': 'RouchNao · 生活与魔导', 'collapse': False}]}
    (target / 'chapter_groups.snbt').write_text(
        json.dumps(groups, ensure_ascii=False), encoding='utf-8')
    print('Prepared isolated FTB fixture:', target)
    print('Run: ./gradlew.bat runGameTestServer -PlifeRoutesTests '
          '-PbuildingGameTests -PftbQuestLoadTests --offline --console=plain')


if __name__ == '__main__':
    main()
