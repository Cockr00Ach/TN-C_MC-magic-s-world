"""Restore the reviewed player snapshot, keeping the four new routes alongside it.

This is an explicit one-time recovery, not a routine update installer.
Every target quest directory is fully backed up first. No saves or game jars change.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import shutil
from install_life_routes import running, FILES

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'questbook/player-restored/ftbquests'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live).resolve()
    assert (live / 'mods').is_dir()
    hashes = json.loads((SOURCE.parent / 'snapshot-sha256.json').read_text(encoding='utf-8'))
    assert all(sha(SOURCE / name) == expected for name, expected in hashes.items()), 'Recovery snapshot has changed'
    assert len(list((SOURCE / 'chapters').glob('*.snbt'))) == 37
    log = (ROOT / 'work/ftb-restored-full-loading.log').read_text(
        encoding='utf-8', errors='replace')
    # Book recovery depends on the actual FTB load, not unrelated animal tests.
    assert '37 chapters, 1787 quests, 3 reward tables' in log
    assert 'NumberFormatException' not in log
    assert not running(live), 'Game/test process is still running; nothing copied'
    chapters = live / 'config/ftbquests/quests/chapters'
    for name in FILES:
        # The game reformats SNBT on exit; compare IDs and content via the reviewed
        # snapshot, and separately preserve its current on-disk bytes on recovery.
        assert (chapters / name).is_file()
    if not args.apply:
        print('37-page recovery checks passed; dry run.')
        return
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = ROOT / 'work/backups' / ('player-quest-recovery-' + stamp)
    targets = [('live', live), ('mirror', ROOT / 'modpack/元素觉醒1.4.3-魔改版-20260915')]
    receipt = {'time': stamp, 'backup': str(backup), 'saves_changed': False,
               'jar_changed': False, 'chapters': 37, 'quests': 1787, 'targets': []}
    for label, root in targets:
        target = root / 'config/ftbquests/quests'
        if target.exists():
            shutil.copytree(target, backup / label)
        # Preserve the exact current four new pages, including any later edits.
        new_hashes = {name: sha(target / 'chapters' / name) for name in FILES}
        for source in SOURCE.rglob('*'):
            if not source.is_file():
                continue
            relative = source.relative_to(SOURCE)
            if relative.parts[0] == 'chapters' and relative.name in FILES:
                continue
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            assert sha(source) == sha(destination)
        assert all(sha(target / 'chapters' / name) == value
                   for name, value in new_hashes.items())
        receipt['targets'].append({'name': label, 'new_chapters_unchanged': new_hashes,
                                  'installed_chapter_count': len(list((target / 'chapters').glob('*.snbt')))})
    (ROOT / 'work/player-quest-recovery-receipt.json').write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
