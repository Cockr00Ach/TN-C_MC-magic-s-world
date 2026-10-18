"""Install only the tested TN-C JAR. All quests, saves, island blueprints and shader settings are preserved."""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shutil,xml.etree.ElementTree as ET
from install_life_routes import running
R=Path(__file__).resolve().parents[1]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def hashes(root):return {p.relative_to(root).as_posix():digest(p) for p in root.rglob('*.snbt')}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args();live=Path(a.live).resolve();assert (live/'mods/tnc-1.0.0.jar').is_file()
 expected=sum(len(re.findall(r'@GameTest\(',p.read_text('utf8'))) for p in [R/'src/main/java/com/tnc/tnc/life/routes/RouteGameTests.java',R/'src/main/java/com/tnc/tnc/life/routes/mech/MechGameTests.java'])
 log=(R/'work/charge-coverage-tests.log').read_text('utf8',errors='replace');assert f'All {expected} required tests passed' in log and 'BUILD SUCCESSFUL' in log and 'failed!' not in log
 jar=R/'build/libs/tnc-1.0.0.jar';latest=max(p.stat().st_mtime for d in ['src/main/java','src/main/resources'] for p in (R/d).rglob('*') if p.is_file());assert jar.stat().st_mtime>=latest
 reports={}
 for name,folder,shader in [('shader','mana-sight-shader-audit','ComplementaryUnbound_r5.6.1'),('vanilla','mana-sight-particles-audit','none')]:
  path=R/'work'/folder/'report.json';j=json.loads(path.read_text('utf8'));assert path.stat().st_mtime>=latest and not j['failures'],name
  assert j['screenshots']==10 and j['configured_range']==96 and 16000<=j['max_particles']<=j['particle_limit']==24000 and j['max_native_particles']>=16000 and j['max_distance']>72
  assert j['shader_pack']==shader and j['charger_manual_received']>0 and j['infuser_manual_received']>0 and j['motion_frames']==60
  if name=='shader':assert j['oculus_opaque_passes_skipped']>0
  reports[name]=j
 units=0
 for p in (R/'build/test-results/test').glob('TEST-*.xml'):
  e=ET.parse(p).getroot();assert int(e.attrib['failures'])==int(e.attrib['errors'])==0;units+=int(e.attrib['tests'])
 assert units>=268 and not running(live),'Game running; close it before installing'
 if not a.apply:print('Validated; --apply installs');return
 base=live/'config/ftbquests/quests';before=hashes(base);backup=R/'work/backups'/('mana-coverage-charge-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 dest=live/'mods/tnc-1.0.0.jar';shutil.copy2(dest,backup/'tnc-1.0.0.jar');shutil.copy2(jar,dest);mirror=R/'modpack'/live.name;shutil.copy2(jar,mirror/'mods/tnc-1.0.0.jar')
 assert digest(jar)==digest(dest)==digest(mirror/'mods/tnc-1.0.0.jar') and hashes(base)==before
 receipt={'installed_at':datetime.datetime.now().isoformat(),'live':str(live),'jar_sha256':digest(jar),'jar_bytes':jar.stat().st_size,'unit_tests':units,'game_tests':expected,'native_reports':reports,'protected_quest_files_unchanged':len(before),'replaced_chapters':[],'backup':str(backup),'saves_modified':False,'island_templates_modified':False,'shader_files_modified':False,'test_item':'tnc:creative_mana_prism'}
 (R/'release/mana-coverage-charge-install-20261018.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
