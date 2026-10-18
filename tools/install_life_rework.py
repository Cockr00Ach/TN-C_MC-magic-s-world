"""Validate/install the scoped life rework. Only the workshop chapter is replaced.
--prepare copies the entire current book into an isolated actual-FTB test fixture.
--apply requires passing fresh build, game tests, native client audit and FTB load.
No saves or island templates are written.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shutil,xml.etree.ElementTree as ET
from install_life_routes import running
from verify_life_routes import run as verify
R=Path(__file__).resolve().parents[1]
CHAPTER='tnc_field_04_workshop.snbt'
SOURCE=R/'questbook/ftbquests/chapters'/CHAPTER
INPUT=R/'work/life-rework-ftb-input.json'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def book_hashes(base):return {p.relative_to(base).as_posix():digest(p) for p in base.rglob('*.snbt')}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--prepare',action='store_true');ap.add_argument('--apply',action='store_true');a=ap.parse_args()
 live=Path(a.live).resolve();assert (live/'mods').is_dir();verify(check_mirror=True)
 base=live/'config/ftbquests/quests'
 if a.prepare:
  assert not running(live),'Close game before taking the test snapshot'
  fixture=R/'work/life-rework-ftb-test/config/ftbquests/quests'
  if fixture.exists():
   saved=fixture.with_name('quests-before-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));fixture.rename(saved)
  shutil.copytree(base,fixture);shutil.copy2(SOURCE,fixture/'chapters'/CHAPTER)
  chapters=len(list((fixture/'chapters').glob('*.snbt')))
  data={'prepared_at':datetime.datetime.now().isoformat(),'live':str(live),'live_book':book_hashes(base),'tested_book':book_hashes(fixture),'workshop':digest(SOURCE),'chapter_count':chapters,'fixture':str(fixture)}
  INPUT.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf8');print('Prepared full current book:',chapters,'chapters; only workshop replaced in test copy');return
 data=json.loads(INPUT.read_text('utf8'));assert data['live']==str(live);assert data['workshop']==digest(SOURCE)
 expected=len(re.findall(r'@GameTest\(', (R/'src/main/java/com/tnc/tnc/life/routes/RouteGameTests.java').read_text('utf8')))
 # The final combined command runs unit tests, build, real FTB GameTests and native client.
 for filename in ['life-rework-ftb.log']:
  log=R/'work'/filename;text=log.read_text('utf8',errors='replace');assert f'All {expected} required tests passed' in text and 'BUILD SUCCESSFUL' in text and 'failed!' not in text,filename
  if filename.endswith('ftb.log'):
   assert log.stat().st_mtime>=INPUT.stat().st_mtime and str(data['chapter_count'])+' chapters,' in text
   assert 'NumberFormatException' not in text and 'Failed to load quests' not in text
 audit_path=R/'work/life-routes-client-audit.json';audit=json.loads(audit_path.read_text('utf8'));assert audit['plants']==38 and audit['screenshots']==15 and not audit['failures']
 jar=R/'build/libs/tnc-1.0.0.jar';latest=max(p.stat().st_mtime for d in ['src/main/java','src/main/resources'] for p in (R/d).rglob('*') if p.is_file())
 assert jar.stat().st_mtime>=latest and audit_path.stat().st_mtime>=latest,'Rebuild and rerun native client audit after source changes'
 units=0
 for p in (R/'build/test-results/test').glob('TEST-*.xml'):
  report=ET.parse(p).getroot();assert int(report.attrib['failures'])==0 and int(report.attrib['errors'])==0;units+=int(report.attrib['tests'])
 assert units>=252;assert book_hashes(base)==data['live_book'],'Live questbook changed since tested snapshot; prepare and test again'
 assert not running(live),'Game is running; no files installed'
 if not a.apply:print('Validated; use --apply to install');return
 backup=R/'work/backups'/('life-rework-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 shutil.copytree(base,backup/'quests-full')
 dest=live/'mods/tnc-1.0.0.jar';shutil.copy2(dest,backup/'tnc-1.0.0.jar');shutil.copy2(jar,dest);shutil.copy2(SOURCE,base/'chapters'/CHAPTER)
 protected={p:h for p,h in data['live_book'].items() if p!='chapters/'+CHAPTER};after=book_hashes(base)
 assert len(after)==len(data['live_book']) and all(after[p]==h for p,h in protected.items());assert digest(dest)==digest(jar)
 mirror=R/'modpack'/live.name;shutil.copy2(jar,mirror/'mods/tnc-1.0.0.jar')
 receipt={'installed_at':datetime.datetime.now().isoformat(),'jar_sha256':digest(jar),'jar_bytes':jar.stat().st_size,'unit_tests':units,'route_game_tests':expected,'native_screenshots':audit['screenshots'],'plants_checked':38,'protected_quest_files_unchanged':len(protected),'replaced_chapters':[CHAPTER],'workshop_quests':len(json.loads(SOURCE.read_text('utf8'))['quests']),'retired_items':14,'backup':str(backup),'live':str(live),'saves_modified':False,'island_templates_modified':False}
 output=R/'release/life-rework-install-20261018.json';output.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
