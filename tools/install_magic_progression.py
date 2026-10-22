"""Install only the verified TN-C runtime and our four blueprint pages; preserve authored quests."""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shutil,subprocess,xml.etree.ElementTree as ET,zipfile
from install_life_routes import running
R=Path(__file__).resolve().parents[1]
CHAPTERS=['tnc_alchemy.snbt','tnc_deep_arcana.snbt','tnc_field_04_workshop.snbt','tnc_magic_progression.snbt']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def protected(pack):
 q=pack/'config/ftbquests/quests'
 return {p.relative_to(q).as_posix():sha(p) for p in q.rglob('*') if p.is_file() and p.relative_to(q).as_posix() not in ['chapters/'+c for c in CHAPTERS]}
def main():
 a=argparse.ArgumentParser();a.add_argument('--live',required=True);a.add_argument('--apply',action='store_true');args=a.parse_args();live=Path(args.live);mirror=R/'modpack'/live.name;jar=R/'build/libs/tnc-1.0.0.jar'
 latest=max(p.stat().st_mtime for d in ['src/main/java','src/main/resources'] for p in (R/d).rglob('*') if p.is_file())
 assert jar.stat().st_mtime>=latest,'Rebuild all current sources first'
 log=R/'work/blueprint-tests.log';s=log.read_text('utf8',errors='replace');counts=re.findall(r'All (\d+) required tests passed',s)
 assert log.stat().st_mtime>=latest and 'BUILD SUCCESSFUL' in s and counts and int(counts[-1])>=144,'Current combined arcana, journey and dungeon GameTests required'
 units=0
 for p in (R/'build/test-results/test').glob('TEST-*.xml'):
  t=ET.parse(p).getroot();assert int(t.attrib['failures'])==int(t.attrib['errors'])==0;units+=int(t.attrib['tests'])
 assert units>=398
 reportpath=R/'work/magic-progression-native/report.json';report=json.loads(reportpath.read_text('utf8'))
 assert reportpath.stat().st_mtime>=latest and report['screenshots']==33 and not report['failures'] and all(report['checks'].values())
 assert report['checks'].get('actual_ftb_chapter_loaded') and report['checks'].get('actual_deep_arcana_chapter_loaded')
 assert len(list(reportpath.parent.glob('*.png')))==33
 for key in ['actual_progression_chapter_loaded','actual_engine_sponsor_release','actual_dead_realm_postprocess','actual_powered_oasis_mesh']:assert report['checks'].get(key),key
 with zipfile.ZipFile(jar) as z:
  for n,h in report['class_sha256'].items():assert hashlib.sha256(z.read(n.replace('.','/')+'.class')).hexdigest()==h
  for n in ['com/tnc/tnc/arcana/ArcanaDevices.class','com/tnc/tnc/world/eartharena/EarthArenaEncounter.class','com/tnc/tnc/boss/wind/WindGeneralEntity.class','com/tnc/tnc/dungeon/TNDungeonMob.class']:assert n in z.namelist()
 assert not running(live),'Close Minecraft/test JVM before installing'
 before={n:protected(p) for n,p in [('live',live),('mirror',mirror)]};assert before['live']
 files={'mods/tnc-1.0.0.jar':jar};files.update({'config/ftbquests/quests/chapters/'+c:R/'questbook/ftbquests/chapters'/c for c in CHAPTERS})
 if not args.apply:print(json.dumps({'validated':True,'units':units,'gametests':int(counts[-1]),'screenshots':33,'protected':len(before['live'])}));return
 backup=R/'work/backups'/('magic-progression-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 for name,pack in [('live',live),('mirror',mirror)]:
  saved=backup/name;saved.mkdir();shutil.copytree(pack/'config/ftbquests/quests',saved/'quests-full')
  for rel,source in files.items():
   dest=pack/rel
   if dest.exists():old=saved/rel;old.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,old)
   dest.parent.mkdir(parents=True,exist_ok=True);tmp=dest.with_name(dest.name+'.arcana-new');shutil.copy2(source,tmp);assert sha(tmp)==sha(source);tmp.replace(dest)
  assert protected(pack)==before[name],'An authored quest file changed'
 receipt={'installed_at':datetime.datetime.now().isoformat(),'jar_sha256':sha(jar),'source_commit_before_release':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip(),'unit_tests':units,'combined_game_tests':int(counts[-1]),'native_screenshots':33,'native':report,'protected_quests':{n:len(v) for n,v in before.items()},'files':{n:sha(p) for n,p in files.items()},'backup':str(backup),'saves_modified':False,'island_templates_modified':False,'additional_third_party_jars':[]}
 (R/'release/magic-progression-install-20261022.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8')
 bundle=R/'work/magic-progression-update-20261022.zip'
 with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  for n,p in files.items():z.write(p,n)
  z.write(R/'tools/apply_magic_progression_update.ps1','apply_magic_progression_update.ps1');z.writestr('manifest.json',json.dumps({'files':receipt['files']},indent=2))
 print(json.dumps({'installed':True,'units':units,'gametests':int(counts[-1]),'native_screenshots':33,'protected':len(before['live']),'sha256':sha(jar),'bundle':str(bundle)},ensure_ascii=False))
if __name__=='__main__':main()
