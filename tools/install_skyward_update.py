"""Scoped installation: TN-C JAR and workshop page; preserve the rest of the live book and every save.
Prepare a complete live-book fixture, run the documented tests, then --apply.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shutil,xml.etree.ElementTree as ET
from install_life_routes import running
from verify_life_routes import run as verify
R=Path(__file__).resolve().parents[1]
CHAPTER='tnc_field_04_workshop.snbt'
SOURCE=R/'questbook/ftbquests/chapters'/CHAPTER
INPUT=R/'work/skyward-ftb-input.json'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def hashes(root):return {p.relative_to(root).as_posix():digest(p) for p in root.rglob('*.snbt')}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--prepare',action='store_true');ap.add_argument('--apply',action='store_true');a=ap.parse_args()
 live=Path(a.live).resolve();assert (live/'mods').is_dir();base=live/'config/ftbquests/quests';verify(check_mirror=True)
 if a.prepare:
  fixture=R/'work/skyward-ftb-test/config/ftbquests/quests'
  if fixture.exists():fixture.rename(fixture.with_name('quests-before-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')))
  shutil.copytree(base,fixture);shutil.copy2(SOURCE,fixture/'chapters'/CHAPTER)
  INPUT.write_text(json.dumps({'live':str(live),'live_book':hashes(base),'tested_book':hashes(fixture),'workshop':digest(SOURCE),'chapter_count':len(list((fixture/'chapters').glob('*.snbt'))),'fixture':str(fixture)},ensure_ascii=False,indent=2)+'\n','utf8');print('Prepared complete live quest book');return
 data=json.loads(INPUT.read_text('utf8'));assert data['live']==str(live) and data['workshop']==digest(SOURCE)
 # FTB serializes loaded chapters back to canonical SNBT at shutdown. Verify the recorded pre-load input instead.
 expected_input=dict(data['live_book']);expected_input['chapters/'+CHAPTER]=digest(SOURCE)
 assert expected_input==data['tested_book'],'Prepared test input differs from the intended complete book'
 expected=sum(len(re.findall(r'@GameTest\(',p.read_text('utf8'))) for p in [R/'src/main/java/com/tnc/tnc/life/routes/RouteGameTests.java',R/'src/main/java/com/tnc/tnc/life/routes/mech/MechGameTests.java'])
 logpath=R/'work/skyward-ftb-final.log';log=logpath.read_text('utf8',errors='replace')
 assert f'All {expected} required tests passed' in log and 'BUILD SUCCESSFUL' in log and 'failed!' not in log
 assert str(data['chapter_count'])+' chapters,' in log and 'NumberFormatException' not in log and 'Failed to load quests' not in log
 assert logpath.stat().st_mtime>=INPUT.stat().st_mtime
 jar=R/'build/libs/tnc-1.0.0.jar';latest=max(p.stat().st_mtime for d in ['src/main/java','src/main/resources'] for p in (R/d).rglob('*') if p.is_file())
 assert jar.stat().st_mtime>=latest,'Rebuild JAR after source changes'
 auditpath=R/'work/mana-sight-shader-audit/report.json';audit=json.loads(auditpath.read_text('utf8'))
 assert not audit['failures'] and audit['screenshots']==16 and audit['motion_frames']==60 and audit['mech_and_mirror_scenes']
 assert audit['shader_pack']=='ComplementaryUnbound_r5.6.1' and audit['oculus_opaque_passes_skipped']>0
 assert 300<audit['max_particles']<=5200 and audit['max_actual_flows']>0 and audit['emitted_transfer_particles']>0
 assert auditpath.stat().st_mtime>=latest,'Rerun native shader audit after source changes'
 units=0
 for p in (R/'build/test-results/test').glob('TEST-*.xml'):
  e=ET.parse(p).getroot();assert int(e.attrib['failures'])==int(e.attrib['errors'])==0;units+=int(e.attrib['tests'])
 assert units>=264 and hashes(base)==data['live_book'],'Live book changed; prepare and test again'
 assert not running(live),'Game running; close before installation'
 if not a.apply:print('Validated; --apply installs');return
 backup=R/'work/backups'/('skyward-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 shutil.copytree(base,backup/'quests-full');dest=live/'mods/tnc-1.0.0.jar';shutil.copy2(dest,backup/'tnc-1.0.0.jar')
 shutil.copy2(jar,dest);shutil.copy2(SOURCE,base/'chapters'/CHAPTER)
 protected={p:h for p,h in data['live_book'].items() if p!='chapters/'+CHAPTER};after=hashes(base)
 assert len(after)==len(data['live_book']) and all(after[p]==h for p,h in protected.items())
 mirror=R/'modpack'/live.name;shutil.copy2(jar,mirror/'mods/tnc-1.0.0.jar')
 assert digest(jar)==digest(dest)==digest(mirror/'mods/tnc-1.0.0.jar')
 receipt={'installed_at':datetime.datetime.now().isoformat(),'live':str(live),'jar_sha256':digest(jar),'jar_bytes':jar.stat().st_size,'unit_tests':units,'game_tests':expected,'native_shader_audit':audit,'protected_quest_files_unchanged':len(protected),'replaced_chapters':[CHAPTER],'backup':str(backup),'saves_modified':False,'island_templates_modified':False}
 (R/'release/skyward-particles-mirrors-install-20261018.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
