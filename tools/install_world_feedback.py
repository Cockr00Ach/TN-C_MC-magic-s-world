"""Install the exact, visually checked world-feedback build without touching saves."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,xml.etree.ElementTree as ET
from install_life_routes import running
R=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text('utf-8-sig'))
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');args=ap.parse_args()
    live=Path(args.live);assert (live/'mods').is_dir()
    jar=R/'build/libs/tnc-1.0.0.jar';digest=sha(jar)
    assert jar.stat().st_mtime>=max(p.stat().st_mtime for root in ['src/main/java','src/main/resources'] for p in (R/root).rglob('*') if p.is_file()),'Rebuild runtime changes first'
    for filename in ['sculpted-feedback-final-build.log','sculpted-dungeon-tests.log','sculpted-journey-tests.log']:
        assert 'BUILD SUCCESSFUL' in (R/'work'/filename).read_text('utf8',errors='replace'),filename
    tests=0
    for p in (R/'build/test-results/test').glob('TEST-*.xml'):
        report=ET.parse(p).getroot();assert int(report.get('failures'))==int(report.get('errors'))==0,p
        tests+=int(report.get('tests'))
    assert tests>=416
    icons=read(R/'src/main/resources/assets/tnc/equipment/icon-integrity.json')
    for path,expected in icons.items():assert sha(R/'src/main/resources/assets/tnc'/path)==expected,path
    checked={}
    for mode in ['vanilla','shaders']:
        root=R/f'work/world-feedback-final-{mode}'
        assert read(root/'complete.json')['jar_sha256']==digest,'Native audit JAR differs'
        assert read(root/'launch.json')['shaders']==(mode=='shaders')
        for suite,expected in [('equipment',85),('botanical',39),('leyline',60),('feedback',11),('naming',3)]:
            data=read(root/suite/'report.json')
            assert not data.get('failures',[]) and not data.get('errors',[]),(mode,suite,data)
            assert all(data.get('checks',{}).values()),(mode,suite,data.get('checks'))
            if 'jar_sha256' in data:assert data['jar_sha256']==digest
            if isinstance(data['screenshots'],list):
                assert len(data['screenshots'])==expected
                assert all((root/suite/name).is_file() for name in data['screenshots'])
            else:
                assert data['screenshots']==expected
                assert len(list((root/suite).glob('*.png')))==expected
                assert all(data['checks'].values()),data['checks']
            checked[f'{mode}/{suite}']=expected
    assert not running(live),'Save and completely close all Minecraft clients before installation'
    result=dict(jar_sha256=digest,unit_tests=tests,native_screenshots=checked,approved_icons=len(icons),saves_changed=False)
    if args.apply:
        backup=R/'work/backups'/('world-feedback-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        for label,pack in [('live',live),('mirror',R/'modpack'/live.name)]:
            dest=pack/'mods/tnc-1.0.0.jar';saved=backup/label/'mods/tnc-1.0.0.jar'
            if dest.exists():saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
            dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_suffix('.jar.feedback-new')
            shutil.copy2(jar,temp);assert sha(temp)==digest;temp.replace(dest);assert sha(dest)==digest
        result['backup']=str(backup)
        (R/'work/world-feedback-install-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
