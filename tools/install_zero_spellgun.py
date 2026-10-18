"""Install Zero only after real full-pack five-form firing and the server tests pass."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil, xml.etree.ElementTree as ET
from install_life_routes import running
ROOT=Path(__file__).resolve().parents[1]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def quests(root):return {p.relative_to(root).as_posix():digest(p) for p in root.rglob('*.snbt')}
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args();live=Path(a.live).resolve()
    jar=ROOT/'build/libs/tnc-1.0.0.jar'
    latest=max(p.stat().st_mtime for folder in ['src/main/java','src/main/resources'] for p in (ROOT/folder).rglob('*') if p.is_file())
    assert jar.stat().st_mtime>=latest
    path=ROOT/'work/zero-native-audit/report.json';report=json.loads(path.read_text('utf8'))
    assert path.stat().st_mtime>=latest and report['full_spell_engine'] and not report['failures'] and report['screenshots']==12
    expected={'water_cannon','great_fireball','earth_ball','great_thunder_orb','wind_ball_2'}
    assert {r['spell'] for r in report['forms']}==expected
    assert all(r['shots']==166 and r['remaining_mana']==48 and r['target_damage']>0 and r['attributed_spell_damage']>0 and r['personal_after']>=r['personal_before'] for r in report['forms'])
    log=(ROOT/'work/zero-gun-tests.log').read_text('utf8',errors='replace')
    assert 'All 52 required tests passed' in log and 'BUILD SUCCESSFUL' in log
    units=0
    for path in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        r=ET.parse(path).getroot();assert int(r.attrib['failures'])==int(r.attrib['errors'])==0;units+=int(r.attrib['tests'])
    assert units>=271 and not running(live),'Game still running or incomplete tests'
    if not a.apply:print('Validated; --apply installs');return
    before=quests(live/'config/ftbquests/quests');backup=ROOT/'work/backups'/('zero-spellgun-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
    target=live/'mods/tnc-1.0.0.jar';shutil.copy2(target,backup/target.name);shutil.copy2(jar,target)
    mirror=ROOT/'modpack'/live.name/'mods/tnc-1.0.0.jar';shutil.copy2(jar,mirror)
    assert digest(jar)==digest(target)==digest(mirror) and quests(live/'config/ftbquests/quests')==before
    receipt={'installed_at':datetime.datetime.now().isoformat(),'live':str(live),'jar_sha256':digest(jar),'jar_bytes':jar.stat().st_size,'unit_tests':units,'game_tests':52,'native_report':report,'protected_quest_files_unchanged':len(before),'backup':str(backup),'saves_modified':False,'island_templates_modified':False,'new_item':'tnc:zero_spellgun','crystal_gun_preserved':True}
    (ROOT/'release/zero-spellgun-install-20261018.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
