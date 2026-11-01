"""Install verified gateway/sea/loading fixes. Existing player saves are never touched."""
from pathlib import Path
import argparse
import datetime
import io
import json
import shutil
import zipfile
import xml.etree.ElementTree as ET
from install_skyhaven_startup import ROOT, sha, tune
from install_life_routes import running


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live)
    jar = ROOT / 'build/libs/tnc-1.0.0.jar'
    digest = sha(jar)
    assert 'BUILD SUCCESSFUL' in (ROOT / 'work/skyhaven-polish-build.log').read_text('utf-8', errors='replace')
    assert 'All 7 required tests passed' in (ROOT / 'work/skyhaven-polish-server.log').read_text('utf-8', errors='replace')
    count = 0
    for file in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        test = ET.parse(file).getroot()
        assert int(test.get('failures')) == int(test.get('errors')) == 0
        count += int(test.get('tests'))
    assert count >= 496
    native = {}
    for mode in ['shaders', 'vanilla']:
        folder = ROOT / f'work/skyhaven-polish-{mode}'
        launch = json.loads((folder / 'launch.json').read_text('utf-8-sig'))
        report = json.loads((folder / 'report.json').read_text('utf-8'))
        started = datetime.datetime.fromisoformat(launch['started_utc'].replace('Z', '+00:00')).timestamp()
        assert launch['jar_sha256'] == digest and launch['shaders'] == (mode == 'shaders')
        assert report['normal_arrival'] and report['complete_on_entry']
        assert (folder / 'report.json').stat().st_mtime >= started and not (folder / 'failed.txt').exists()
        assert len(report['screenshots']) == 5
        assert all((folder / name).stat().st_mtime >= started for name in report['screenshots'])
        native[mode] = report
    with zipfile.ZipFile(jar) as z:
        with zipfile.ZipFile(io.BytesIO(z.read('data/tnc/skyhaven/starter.zip'))) as terrain:
            manifest = json.loads(terrain.read('manifest.json'))
            assert manifest['revision'] == 2 and manifest['gateway_foundation']['raised_columns'] >= 1000
    assert not running(live), 'Exit only the actual live game before replacement'
    receipt = {'jar_sha256': digest, 'unit_tests': count, 'server_tests': 7,
               'native': native, 'production_saves_changed': False}
    if args.apply:
        backup = ROOT / 'work/backups' / ('skyhaven-polish-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        backup.mkdir(parents=True)
        for label, pack in [('live', live), ('mirror', ROOT / 'modpack' / live.name)]:
            dest = pack / 'mods/tnc-1.0.0.jar'
            saved = backup / label
            saved.mkdir()
            shutil.copy2(dest, saved / 'tnc-before.jar')
            temp = dest.with_suffix('.polish-new')
            shutil.copy2(jar, temp)
            assert sha(temp) == digest
            temp.replace(dest)
        tune(live, backup / 'live/settings')
        package = ROOT / 'release/skyhaven-polish-20261030.zip'
        with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED) as z:
            z.write(jar, 'mods/tnc-1.0.0.jar')
            z.writestr('说明.txt', '关闭游戏后替换mods/tnc-1.0.0.jar。新创建的世界补齐传送阵石基及草坡；已有存档地形不覆盖。玄潮海F8大漩涡约70至90格直径。首次抵达增加周边区块和画面就绪预载，再进入游戏。联机双方需使用相同新版JAR。')
        receipt.update(backup=str(backup), package=str(package))
        (ROOT / 'work/skyhaven-polish-install.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), 'utf-8')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
