"""Verify actual client/server evidence, then atomically install the tavern JAR.

World migration is handled separately by install_tavern.py. This installer never
copies a complete pack or a save and preserves the already reviewed ecology art.
"""
from pathlib import Path
import argparse, datetime, hashlib, json, os, shutil, zipfile, subprocess
import xml.etree.ElementTree as ET
from install_tavern import game_closed

ROOT=Path(__file__).resolve().parents[1]
LIVE=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
MIRROR=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915'
JAR=ROOT/'build/libs/tnc-1.0.0.jar'
SOURCE=ROOT/'src/main/resources'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def snapshot(root):
    files=[]
    for rel in ['config/ftbquests/quests','kubejs/data/tnc/sky_island','kubejs/data/tnc/structures/sky_island']:
        files.extend(p for p in (root/rel).rglob('*') if p.is_file())
    for pattern in ['level.dat','tnc_sky_island_v5.dat','tnc_sky_landscape_v1.dat','*.mca','*.dat']:
        files.extend((root/'saves').rglob(pattern))
    return {str(p):sha(p) for p in set(files)}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');parser.add_argument('--corrections',action='store_true');parser.add_argument('--access-fix',action='store_true');parser.add_argument('--merged-resources-from');parser.add_argument('--test-log',type=Path);parser.add_argument('--receipt-output',type=Path);args=parser.parse_args()
    if args.access_fix:args.corrections=True
    audit=ROOT/'work/tavern-revision-20261005' if args.corrections else ROOT/'work'
    guest_count,tracks,game_tests,min_unit=(43,3,232,139) if args.corrections else (49,2,229,134)
    if args.access_fix:game_tests,min_unit=235,136
    client=json.loads((audit/('client-audit.json' if args.corrections else 'tavern-client-audit.json')).read_text(encoding='utf8'))
    assert client['failures']==[] and client['guest_definitions']==guest_count and client['screenshots']==3
    assert any('200 entry ticks' in line for line in client['evidence'])
    assert 'BUILD SUCCESSFUL' in (audit/('client.log' if args.corrections else 'tavern-client.log')).read_text(encoding='utf8',errors='replace')
    log=(args.test_log or (ROOT/'work/tavern-access-20261005-tests.log' if args.access_fix else audit/('final-tests.log' if args.corrections else 'tavern-final-tests.log'))).read_text(encoding='utf8',errors='replace')
    assert f'All {game_tests} required tests passed' in log and 'BUILD SUCCESSFUL' in log
    if args.corrections:
        assert any('Natural end' in line for line in client['evidence'])
        if not args.access_fix:
            hud=json.loads((audit/'hud-client-audit.json').read_text(encoding='utf8'))
            assert hud['food']==14 and hud['health']<hud['maximum'] and hud['absorption']==4
            assert 'BUILD SUCCESSFUL' in (audit/'hud-client.log').read_text(encoding='utf8',errors='replace')
    count=0
    for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        s=ET.parse(p).getroot();count+=int(s.attrib['tests']);assert int(s.attrib['failures'])==0 and int(s.attrib['errors'])==0
    assert count>=min_unit
    with zipfile.ZipFile(JAR) as new,zipfile.ZipFile(LIVE/'mods/tnc-1.0.0.jar') as old:
        assert new.testzip() is None
        required=['assets/tnc/sounds/tavern/wander_ward.ogg','assets/tnc/sounds/tavern/wander_ward_1.ogg',
            'data/tnc/tavern/interior_v1.nbt','data/tnc/tavern/atmosphere_v1.json','assets/tnc/tavern-sounds.json']
        required.extend(f'data/tnc/dialogues/tavern/guest_{i:02}.txt' for i in range(1,50))
        if args.corrections:required.extend(['data/tnc/tavern/basement_v1.nbt','assets/tnc/sounds/tavern/tavern_third.ogg'])
        if args.corrections and not args.access_fix:required.extend(['assets/tnc/textures/gui/health_bar/health_a_frame.png','assets/tnc/textures/gui/hunger_bar/hunger_c_frame.png'])
        for name in required:assert new.read(name)==(SOURCE/name).read_bytes(),name
        for name in ['com/tnc/tnc/tavern/TavernUpgrade.class','com/tnc/tnc/tavern/TavernGuestEntity.class','com/tnc/tnc/tavern/client/TavernGuestRenderer.class']:
            assert name in new.namelist()
        catalog=json.loads(new.read('data/tnc/tavern/atmosphere_v1.json'));assert len(catalog['guests'])==guest_count and catalog['total_seats']==75
        if args.corrections:
            assert len(catalog['empty_seats'])==10
            assert not {f'guest_{i:02}' for i in range(1,7)} & {g['id'] for g in catalog['guests']}
            assert 'com/tnc/tnc/tavern/TavernBasementUpgrade.class' in new.namelist()
            for name in ['com/tnc/tnc/client/HealthGaugeHud.class','com/tnc/tnc/client/HungerGaugeHud.class']:assert (name in new.namelist())!=args.access_fix
            if args.access_fix:assert catalog['rooms']==[[414,79,268,489,134,330]]
        sounds=json.loads(new.read('assets/tnc/sounds.json'))
        for key in json.loads(new.read('assets/tnc/tavern-sounds.json')):assert sounds[key]['sounds'][0]['stream']
        protected=[name for name in old.namelist() if '/sky_island/' in name or ('SkyIsland' in name and 'SkyIslandEvents' not in name)]
        protected.extend(name for name in old.namelist() if name.startswith(('assets/tnc/textures/','assets/tnc/models/')))
        removed_hud={'assets/tnc/textures/gui/health_bar/health_a_frame.png','assets/tnc/textures/gui/hunger_bar/hunger_c_frame.png'} if args.access_fix else set()
        merged_resources=set()
        if args.merged_resources_from:
            changed=subprocess.check_output(['git','diff','--name-only',args.merged_resources_from,'HEAD','--','src/main/resources/assets/tnc/textures','src/main/resources/assets/tnc/models'],cwd=ROOT,text=True).splitlines()
            merged_resources={name.removeprefix('src/main/resources/') for name in changed}
            for name in merged_resources:
                source=SOURCE/name
                if source.exists():assert new.read(name)==source.read_bytes(),'Merged resource differs from reviewed source: '+name
                else:assert name not in new.namelist(),'Deleted resource still packaged: '+name
        for name in protected:
            if name.endswith("/") or name in removed_hud or name in merged_resources:continue
            assert new.read(name)==old.read(name),'Protected resource changed: '+name
        for name in new.namelist():
            if name.endswith('.json') and name.startswith(('assets/tnc/','data/tnc/')):json.loads(new.read(name))
    game_closed();print(f'Verified {guest_count}/75 seats, {tracks} streamed tracks, {count} unit tests, {game_tests} GameTests, actual client captures')
    if not args.install:return
    before=snapshot(LIVE)|snapshot(MIRROR)
    backup=ROOT/'work/backups'/('tavern-package-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
    targets=[]
    for label,root in [('live',LIVE),('mirror',MIRROR)]:
        target=root/'mods/tnc-1.0.0.jar';saved=backup/label/'tnc-1.0.0.jar';saved.parent.mkdir(parents=True);shutil.copy2(target,saved);targets.append((target,saved))
    try:
        for target,saved in targets:
            temporary=target.with_name(target.name+'.tnc-tavern-tmp');shutil.copy2(JAR,temporary);assert sha(temporary)==sha(JAR);os.replace(temporary,target);assert sha(target)==sha(JAR)
        assert snapshot(LIVE)|snapshot(MIRROR)==before,'Protected pack/save files changed'
    except Exception:
        for target,saved in targets:
            temporary=target.with_name(target.name+'.tnc-tavern-tmp');shutil.copy2(saved,temporary);os.replace(temporary,target)
        raise
    receipt={'installed_at':datetime.datetime.now().isoformat(),'sha256':sha(JAR),'backup':str(backup),'installed_files':[str(target) for target,saved in targets],
        'guest_seats':guest_count,'total_seats':75,'unit_tests':count,'game_tests':game_tests,'client':client,'protected_resources':len(protected),'protected_files':len(before)}
    (args.receipt_output or (ROOT/'work/tavern-access-20261005-package-install.json' if args.access_fix else audit/('package-install.json' if args.corrections else 'tavern-package-install-receipt.json'))).write_bytes(json.dumps(receipt,ensure_ascii=False,indent=2).encode('utf8'))
    print('Installed package:',receipt['sha256'])

if __name__=='__main__':main()
