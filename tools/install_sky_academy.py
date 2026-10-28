"""Install one checked JAR + one new chapter. Never opens or changes production saves."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from install_life_routes import running
ROOT=Path(__file__).resolve().parents[1]
CHAPTER='tnc_sky_academy.snbt'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text('utf-8-sig'))
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args()
    live=Path(a.live);jar=ROOT/'build/libs/tnc-1.0.0.jar';digest=sha(jar)
    assert 'BUILD SUCCESSFUL' in (ROOT/'work/sky-academy-final-build.log').read_text('utf8',errors='replace')
    count=0
    for file in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        t=ET.parse(file).getroot();assert int(t.get('failures'))==int(t.get('errors'))==0;count+=int(t.get('tests'))
    assert count>=421
    server_log=(ROOT/'work/sky-academy-server-build.log').read_text('utf8',errors='replace')
    assert 'All 3 required tests passed' in server_log and 'BUILD SUCCESSFUL' in server_log
    for variant in ('vanilla','shaders'):
        audit=ROOT/('work/sky-academy-native-'+variant)
        launch=read(audit/'launch.json')
        assert launch['jar_sha256']==digest
        assert launch['shaders']==(variant=='shaders'),'Both native shader modes must be checked'
        report=read(audit/'report.json');assert len(report['native_screenshots'])==7
        assert len(list(audit.glob('*.png')))==7
    with zipfile.ZipFile(jar) as z:
        for path in ('data/tnc/sky_academy/landscape.nbt','data/tnc/sky_academy/sites.json','data/tnc/housing/catalog.json'):
            assert z.read(path)==(ROOT/'src/main/resources'/path).read_bytes(),path
    assert not running(live),'Save and exit the running game before installation'
    source=ROOT/'questbook/ftbquests/chapters'/CHAPTER
    book=live/'config/ftbquests/quests';before={str(p.relative_to(book)):sha(p) for p in book.rglob('*.snbt') if p.name!=CHAPTER}
    result={'jar_sha256':digest,'unit_tests':count,'academy_server_tests':3,'native_variants':['vanilla','shaders'],'saves_changed':False,'new_worlds_only':True,'chapter':CHAPTER}
    if a.apply:
        backup=ROOT/'work/backups'/('sky-academy-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
        for label,pack in [('live',live),('mirror',ROOT/'modpack'/live.name)]:
            for rel,src in [('mods/tnc-1.0.0.jar',jar),('config/ftbquests/quests/chapters/'+CHAPTER,source)]:
                dest=pack/rel;saved=backup/label/rel
                if dest.exists():saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
                dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_name(dest.name+'.academy-new');shutil.copy2(src,temp);assert sha(temp)==sha(src);temp.replace(dest)
        assert all(sha(book/path)==digest for path,digest in before.items())
        result['preserved_quest_files']=len(before);result['backup']=str(backup)
        release=ROOT/'release/sky-academy-20261026.zip';release.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(release,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
            z.write(jar,'mods/tnc-1.0.0.jar');z.write(source,'config/ftbquests/quests/chapters/'+CHAPTER)
            z.writestr('安装说明.txt','关闭游戏后，将mods与config合入对应游戏版本目录。仅替换TN-C JAR并增加云溪课堂章节，保留其他任务。新版地形仅用于新建世界；旧存档不自动翻修。\n')
        result['package']=str(release)
        (ROOT/'work/sky-academy-install-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
