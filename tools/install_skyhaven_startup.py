"""Install the verified fresh-world startup fix; never modify existing saves."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import zipfile
import xml.etree.ElementTree as ET

from install_life_routes import running

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tune(pack, backup):
    """Bound background chunk work while retaining shaders and all magic effects."""
    backup.mkdir(parents=True, exist_ok=True)
    options = pack / 'options.txt'
    if options.exists():
        shutil.copy2(options, backup / 'options.txt')
        values = {'renderDistance': '8', 'simulationDistance': '5'}
        lines = options.read_text('utf-8').splitlines()
        if 'guiScale:0' in lines:
            values['guiScale'] = '2'
        options.write_text('\n'.join(k + ':' + values[k] if (k := line.partition(':')[0]) in values else line for line in lines) + '\n', 'utf-8')
    emb = pack / 'config/embeddium-options.json'
    if emb.exists():
        shutil.copy2(emb, backup / 'embeddium-options.json')
        data = json.loads(emb.read_text('utf-8'))
        data['performance']['chunk_builder_threads'] = 2
        emb.write_text(json.dumps(data, indent=2) + '\n', 'utf-8')



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live)
    jar = ROOT / 'build/libs/tnc-1.0.0.jar'
    digest = sha(jar)
    assert 'BUILD SUCCESSFUL' in (ROOT / 'work/skyhaven-starter-build.log').read_text('utf-8', errors='replace')
    count = 0
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        t = ET.parse(path).getroot()
        assert int(t.get('failures')) == int(t.get('errors')) == 0
        count += int(t.get('tests'))
    assert count >= 490
    assert 'All 7 required tests passed' in (ROOT / 'work/skyhaven-starter-server.log').read_text('utf-8', errors='replace')
    reports = []
    for mode in ['vanilla', 'shaders']:
        folder = ROOT / f'work/skyhaven-startup-{mode}'
        launch = json.loads((folder / 'launch.json').read_text('utf-8-sig'))
        report = json.loads((folder / 'report.json').read_text('utf-8'))
        started = datetime.datetime.fromisoformat(launch['started_utc'].replace('Z', '+00:00')).timestamp()
        assert launch['jar_sha256'] == digest and launch['shaders'] == (mode == 'shaders')
        assert report['normal_arrival'] and report['complete_on_entry']
        assert (folder / 'report.json').stat().st_mtime >= started
        assert (folder / 'arrival.txt').stat().st_mtime >= started
        assert all((folder / f'startup-{i}.png').stat().st_mtime >= started for i in [0, 1])
        assert not (folder / 'failed.txt').exists()
        reports.append(report)
    assert not running(live), 'The live game must be closed'
    receipt = {'jar_sha256': digest, 'tests': count, 'native': reports, 'production_saves_changed': False}
    if args.apply:
        backup = ROOT / 'work/backups' / ('skyhaven-startup-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        backup.mkdir(parents=True)
        for label, pack in [('live', live), ('mirror', ROOT / 'modpack' / live.name)]:
            target = pack / 'mods/tnc-1.0.0.jar'
            saved = backup / label
            saved.mkdir()
            if target.exists():
                shutil.copy2(target, saved / 'tnc-before.jar')
            temp = target.with_suffix('.startup-new')
            shutil.copy2(jar, temp)
            assert sha(temp) == digest
            temp.replace(target)
        tune(live, backup / 'live/settings')
        receipt['backup'] = str(backup)
        package = ROOT / 'release/skyhaven-startup-20261030.zip'
        with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.write(jar, 'mods/tnc-1.0.0.jar')
            archive.writestr('说明.txt', '关闭游戏后替换mods/tnc-1.0.0.jar。新创建世界在加载时准备完整天空岛，首次从花坡抵达。旧存档不修改。建议视距8、模拟距离4、区块编译线程2；保留光影和魔力视。传送后必须走出阵圈才能重新触发。')
        receipt['package'] = str(package)
        (ROOT / 'work/skyhaven-startup-install.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), 'utf-8')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
