"""Install only the tested TN-C JAR and the new dungeon chapter; preserve every other quest/save."""
from pathlib import Path
import argparse, datetime, hashlib, json, re, shutil, xml.etree.ElementTree as ET
from install_life_routes import running

R=Path(__file__).resolve().parents[1]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');args=ap.parse_args()
    live=Path(args.live);jar=R/'build/libs/tnc-1.0.0.jar';chapter='tnc_leyline_dungeons.snbt'
    latest=max(p.stat().st_mtime for base in ('src/main/java','src/main/resources') for p in (R/base).rglob('*') if p.is_file())
    assert jar.stat().st_mtime>=latest,'Rebuild changed sources before installation'
    count=0
    for p in (R/'build/test-results/test').glob('TEST-*.xml'):
        root=ET.parse(p).getroot();assert int(root.attrib['failures'])==int(root.attrib['errors'])==0,p
        count+=int(root.attrib['tests'])
    assert count>=414
    log=R/'work/leyline-server-tests.log';data=log.read_text('utf8',errors='replace');passed=re.findall(r'All (\d+) required tests passed',data)
    assert 'BUILD SUCCESSFUL' in data and passed and log.stat().st_mtime>=latest,'Current server tests must pass'
    fixture=(R/'work/leyline-fluid-fixture-tests.log').read_text('utf8',errors='replace')
    assert 'BUILD SUCCESSFUL' in fixture and re.search(r'All (\d+) required tests passed',fixture),'Closed fluid fixture must pass'
    reports={}
    for mode in ('shaders','vanilla'):
        path=R/f'work/leyline-native-{mode}/report.json';report=json.loads(path.read_text('utf8'))
        assert not report['failures'] and len(report['screenshots'])==60
        assert report.get('jar_sha256')==sha(jar),'Native review must use this exact JAR'
        assert all((path.parent/name).exists() for name in report['screenshots'])
        reports[mode]=len(report['screenshots'])
    assert not running(live),'Save and fully close the live Minecraft client first'
    files={'mods/tnc-1.0.0.jar':jar,'config/ftbquests/quests/chapters/'+chapter:R/'questbook/ftbquests/chapters'/chapter}
    result={'jar_sha256':sha(jar),'unit_tests':count,'server_tests':int(passed[-1]),'native_screenshots':reports,'files':list(files)}
    if args.apply:
        backup=R/'work/backups'/('leyline-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        for label,pack in [('live',live),('mirror',R/'modpack'/live.name)]:
            quests=pack/'config/ftbquests/quests';before={str(p.relative_to(quests)):sha(p) for p in quests.rglob('*') if p.is_file() and p.name!=chapter}
            for rel,source in files.items():
                dest=pack/rel
                if dest.exists():
                    saved=backup/label/rel;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
                dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_name(dest.name+'.leyline-new');shutil.copy2(source,temp);temp.replace(dest);assert sha(dest)==sha(source)
            after={str(p.relative_to(quests)):sha(p) for p in quests.rglob('*') if p.is_file() and p.name!=chapter}
            assert before==after,'Unrelated quest bytes changed'
        result['backup']=str(backup);(R/'work/leyline-install-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
