"""Install the verified independent-island build, never edit production saves."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from install_life_routes import running
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text('utf-8-sig'))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args();live=Path(a.live);jar=ROOT/'build/libs/tnc-1.0.0.jar';digest=sha(jar)
 assert 'BUILD SUCCESSFUL' in (ROOT/'work/skyhaven-final-build.log').read_text('utf8',errors='replace')
 log=(ROOT/'work/skyhaven-migration-tests.log').read_text('utf8',errors='replace');assert 'All 7 required tests passed' in log and 'BUILD SUCCESSFUL' in log
 count=0
 for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
  t=ET.parse(p).getroot();assert int(t.get('failures'))==int(t.get('errors'))==0;count+=int(t.get('tests'))
 assert count>=460
 for mode in ('shaders','vanilla'):
  directory=ROOT/f'work/skyhaven-native-{mode}';launch=read(directory/'launch.json');report=read(directory/'report.json');stamp=datetime.datetime.fromisoformat(launch['started_utc'].replace('Z','+00:00')).timestamp()
  assert launch['jar_sha256']==digest and launch['shaders']==(mode=='shaders')
  assert report['portal_round_trip'] and report['normal_lifecycle'] and len(report['native_screenshots'])==9
  assert (directory/'report.json').stat().st_mtime>=stamp
  assert len(list(directory.glob('*.png')))==9
  assert all(p.stat().st_mtime>=stamp for p in directory.glob('*.png'))
 with zipfile.ZipFile(jar) as z:
  for name in ('data/tnc/dimension/skyhaven.json','data/tnc/worldgen/biome/skyhaven.json','data/tnc/blueprints/aurelian.nbt'):
   assert z.read(name)==(ROOT/'src/main/resources'/name).read_bytes()
 assert not running(live),'Only the actual live pack must be exited before replacement'
 name='tnc_sky_academy.snbt';book=live/'config/ftbquests/quests';before={str(p.relative_to(book)):sha(p) for p in book.rglob('*.snbt') if p.name!=name}
 report={'jar_sha256':digest,'unit_tests':count,'server_tests':7,'native_modes':['vanilla','shaders'],'actual_portal_round_trip':True,'new_worlds_only':True,'production_saves_changed':False}
 if a.apply:
  backup=ROOT/'work/backups'/('skyhaven-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
  chapter=ROOT/'questbook/ftbquests/chapters'/name
  for label,pack in [('live',live),('mirror',ROOT/'modpack'/live.name)]:
   for rel,src in [('mods/tnc-1.0.0.jar',jar),('config/ftbquests/quests/chapters/'+name,chapter)]:
    dest=pack/rel;saved=backup/label/rel
    if dest.exists():saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
    dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_name(dest.name+'.skyhaven-new');shutil.copy2(src,temp);assert sha(temp)==sha(src);temp.replace(dest)
  assert all(sha(book/path)==value for path,value in before.items());report.update(backup=str(backup),preserved_quest_files=len(before))
  target=ROOT/'release/skyhaven-20261030.zip';target.parent.mkdir(exist_ok=True)
  with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
   z.write(jar,'mods/tnc-1.0.0.jar');z.write(chapter,'config/ftbquests/quests/chapters/'+name);z.writestr('说明.txt','新世界出生在独立天空岛；旧世界不自动迁移。原门双向往返主世界。巨龙静态建筑：开启作弊后 /aurelian summon ~ 90 ~，先检查空位再生成；取消命令 /aurelian cancel，不回滚已经生成的部分。关闭游戏后合入mods和config。')
  report['package']=str(target);(ROOT/'work/skyhaven-install-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf8')
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
