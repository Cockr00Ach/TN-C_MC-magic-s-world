"""Install the checked startup/naming patch; do not modify saves, settings or quests."""
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
    latest=max(p.stat().st_mtime for root in ['src/main/java','src/main/resources'] for p in (R/root).rglob('*') if p.is_file())
    assert jar.stat().st_mtime>=latest,'Runtime sources are newer than built JAR'
    game_log=(R/'work/sky-startup-gametest.log').read_text('utf8',errors='replace')
    assert 'All 2 required tests passed' in game_log and '[TN-C Startup Test] ticket request=' in game_log and 'End arena native site=' in game_log,'Server tests did not complete'
    assert 'BUILD SUCCESSFUL' in (R/'work/startup-final-build.log').read_text('utf8',errors='replace')
    root=R/'work/startup-naming-native-v2';assert read(root/'complete.json')['jar_sha256']==digest
    audit=read(root/'naming/report.json');assert not audit['errors'] and all(audit['checks'].values())
    assert all(audit['checks'].get(k) for k in ['escape_keeps_initial_form','enter_does_not_submit','escape_enter_do_not_name','confirm_closes_form','server_name_saved'])
    reports=list((R/'build/test-results/test').glob('TEST-*.xml'));assert reports
    count=0
    for p in reports:
        t=ET.parse(p).getroot();assert int(t.get('failures'))==int(t.get('errors'))==0;count+=int(t.get('tests'))
    assert count>=417
    assert not running(live),'Game is running; save and exit before replacing JAR'
    result=dict(jar_sha256=digest,unit_tests=count,server_tests=2,naming_checks=audit['checks'],saves_changed=False,design_status='proposal_only')
    if args.apply:
        backup=R/'work/backups'/('startup-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        for label,pack in [('live',live),('mirror',R/'modpack'/live.name)]:
            dest=pack/'mods/tnc-1.0.0.jar';saved=backup/label/'tnc-1.0.0.jar'
            if dest.exists():saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
            dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_suffix('.jar.startup-new')
            shutil.copy2(jar,temp);assert sha(temp)==digest;temp.replace(dest);assert sha(dest)==digest
        result['backup']=str(backup)
        (R/'work/startup-install-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
