"""Install the arrival UI only after genuine screen-transition regression audits."""
from pathlib import Path
import argparse,datetime,json,shutil,struct,zipfile,xml.etree.ElementTree as ET
from install_skyhaven_startup import ROOT,sha
from install_life_routes import running

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--live',required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    live=Path(args.live);jar=ROOT/'build/libs/tnc-1.0.0.jar';digest=sha(jar)
    assert 'BUILD SUCCESSFUL' in (ROOT/'work/skyhaven-loading-build.log').read_text('utf-8',errors='replace')
    tests=skipped=0
    for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        r=ET.parse(p).getroot();assert int(r.get('failures'))==int(r.get('errors'))==0
        tests+=int(r.get('tests'));skipped+=int(r.get('skipped'))
    assert tests>=562
    assert 'All 7 required tests passed' in (ROOT/'work/skyhaven-loading-server.log').read_text('utf-8',errors='replace')
    reports={}
    for mode in ['shaders','vanilla']:
        folder=ROOT/f'work/skyhaven-loading-{mode}'
        launch=json.loads((folder/'launch.json').read_text('utf-8-sig'));report=json.loads((folder/'report.json').read_text('utf-8'))
        assert launch['jar_sha256']==digest and launch['shaders']==(mode=='shaders')
        assert all(report[k] for k in ['normal_arrival','complete_on_entry','naming_confirmed','transient_screen_probed'])
        started=datetime.datetime.fromisoformat(launch['started_utc'].replace('Z','+00:00')).timestamp()
        assert not (folder/'failed.txt').exists() and (folder/'report.json').stat().st_mtime>=started
        for name in ['arrival-loading.png','arrival-naming.png','arrival-cinematic.png','startup-0.png']:
            assert (folder/name).stat().st_mtime>=started
            header=(folder/name).read_bytes()[:24];assert header[:8]==b'\x89PNG\r\n\x1a\n'
            w,h=struct.unpack('>II',header[16:24]);assert w>=800 and h>=450,'Invalid offscreen framebuffer'
        reports[mode]=report
    with zipfile.ZipFile(jar) as z:
        assert len(z.read('assets/tnc/textures/gui/skyhaven_arrival.png'))>100000
        assert len(z.read('assets/tnc/textures/gui/profile_sanctum.png'))>100000
    assert not running(live),'Live game is still running'
    receipt=dict(jar_sha256=digest,tests=tests,skipped=skipped,native=reports,production_saves_changed=False)
    if args.apply:
        backup=ROOT/'work/backups'/('skyhaven-arrival-ui-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
        for label,pack in [('live',live),('mirror',ROOT/'modpack'/live.name)]:
            dest=pack/'mods/tnc-1.0.0.jar';shutil.copy2(dest,backup/(label+'-before.jar'))
            temp=dest.with_suffix('.arrival-new');shutil.copy2(jar,temp);assert sha(temp)==digest;temp.replace(dest)
        package=ROOT/'release/skyhaven-arrival-ui-20261101.zip'
        with zipfile.ZipFile(package,'w',zipfile.ZIP_DEFLATED) as z:
            z.write(jar,'mods/tnc-1.0.0.jar');z.writestr('说明.txt','退出游戏后替换mods/tnc-1.0.0.jar。更换天空岛加载背景和人物命名界面，移除旧版天空岛觉醒及坐标广播，修复完成加载后卡在加入世界中。不会修改已有存档；联机双方需用相同JAR。')
        receipt.update(backup=str(backup),package=str(package));(ROOT/'work/skyhaven-loading-install.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
