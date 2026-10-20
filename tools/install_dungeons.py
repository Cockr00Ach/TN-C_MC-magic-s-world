"""Install the tested dungeon jar and one NEW chapter, preserving every pre-existing quest and save."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from install_life_routes import running
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def quests(root):return {p.relative_to(root).as_posix():sha(p) for p in root.rglob('*.snbt') if p.name!='tnc_dungeons.snbt'}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args();live=Path(a.live).resolve();jar=ROOT/'build/libs/tnc-1.0.0.jar';chapter=ROOT/'questbook/ftbquests/chapters/tnc_dungeons.snbt'
 latest=max(p.stat().st_mtime for folder in ['src/main/java','src/main/resources'] for p in (ROOT/folder).rglob('*') if p.is_file());assert jar.stat().st_mtime>=latest,'Rebuild current sources'
 log=(ROOT/'work/dungeon-tests-final.log').read_text('utf8',errors='replace');assert 'All 16 required tests passed' in log and 'BUILD SUCCESSFUL' in log
 tests=0
 for path in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
  r=ET.parse(path).getroot();assert int(r.attrib['failures'])==int(r.attrib['errors'])==0;tests+=int(r.attrib['tests'])
 assert tests>=275
 reportpath=ROOT/'work/dungeon-native-audit/report.json';report=json.loads(reportpath.read_text('utf8'));assert reportpath.stat().st_mtime>=latest and not report['failures'] and report['five_floors_completed'] and report['failure_kept_inventory'] and report['failure_lost_only_six_tokens'] and report['unlock']>=6
 shots=list(reportpath.parent.glob('*.png'));assert len(shots)==16
 with zipfile.ZipFile(jar) as z:
  assert len([n for n in z.namelist() if n.startswith('data/tnc/structures/dungeon_gates/') and n.endswith('.nbt')])==67
  for name in ['desert','crypt','tide','astral']:assert f'data/tnc/dungeon_gate_assets/{name}.json' in z.namelist()
 assert not running(live),'Minecraft is running; close before replacing the jar'
 if not a.apply:print('Validated; pass --apply to install');return
 before=quests(live/'config/ftbquests/quests');backup=ROOT/'work/backups'/('dungeons-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 target=live/'mods/tnc-1.0.0.jar';shutil.copy2(target,backup/target.name)
 targetchapter=live/'config/ftbquests/quests/chapters/tnc_dungeons.snbt'
 if targetchapter.exists():shutil.copy2(targetchapter,backup/targetchapter.name)
 shutil.copy2(jar,target);shutil.copy2(chapter,targetchapter)
 mirror=ROOT/'modpack'/live.name;shutil.copy2(jar,mirror/'mods/tnc-1.0.0.jar');shutil.copy2(chapter,mirror/'config/ftbquests/quests/chapters/tnc_dungeons.snbt')
 assert sha(target)==sha(jar)==sha(mirror/'mods/tnc-1.0.0.jar') and quests(live/'config/ftbquests/quests')==before
 files={'mods/tnc-1.0.0.jar':sha(jar),'config/ftbquests/quests/chapters/tnc_dungeons.snbt':sha(chapter)}
 release=ROOT/'release/dungeons-install-20261019.json';receipt={'installed_at':datetime.datetime.now().isoformat(),'jar_sha256':sha(jar),'jar_bytes':jar.stat().st_size,'files':files,'unit_tests':tests,'dungeon_game_tests':16,'native_report':report,'screenshots':len(shots),'protected_quest_files_unchanged':len(before),'backup':str(backup),'saves_modified':False,'island_templates_modified':False,'upstream':'607b2b6d','third_party_gate_assets':'Local runtime package only; source geometry not uploaded to Git'};release.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8')
 bundle=ROOT/'work/dungeons-collaborator-update-20261019.zip'
 with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  z.write(jar,'mods/tnc-1.0.0.jar');z.write(chapter,'config/ftbquests/quests/chapters/tnc_dungeons.snbt');z.write(ROOT/'tools/apply_dungeon_update.ps1','apply_dungeon_update.ps1');z.writestr('manifest.json',json.dumps({'files':files},indent=2))
 print(json.dumps({'installed':True,'unit_tests':tests,'game_tests':16,'preserved_quests':len(before),'bundle':str(bundle),'sha256':sha(jar)},ensure_ascii=False))
if __name__=='__main__':main()
