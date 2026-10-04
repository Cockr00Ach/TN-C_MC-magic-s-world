"""Install this revision only; preserve unrelated quests, worlds and island templates."""
from pathlib import Path
import argparse,hashlib,json,subprocess,shutil,re,importlib.util,datetime,zipfile
R=Path(__file__).resolve().parents[1]
LIVE=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
MIRROR=R/'modpack/元素觉醒1.4.3-魔改版-20260915';CH='config/ftbquests/quests/chapters'
names=['tnc_field_02_botany.snbt','tnc_field_03_pasture.snbt','tnc_field_04_workshop.snbt']
spec=importlib.util.spec_from_file_location('snbt',R/'tools/merge-field-redesign-guide.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def canonical(id):return f'{int(id,16)&0x7FFFFFFFFFFFFFFF:016X}'
def merged(filename):
 baseline=json.loads((R/'tools/fixtures/ecology_feedback_chapter_baseline'/filename).read_text(encoding='utf8'))
 wanted=json.loads((R/'questbook/ftbquests/chapters'/filename).read_text(encoding='utf8'))
 live=m.SnbtReader((LIVE/CH/filename).read_text(encoding='utf-8-sig')).read()
 assert canonical(live['id'])==canonical(wanted['id']) and live['filename']==wanted['filename'],filename
 base={canonical(q['id']):q for q in baseline['quests']};by={canonical(q['id']):q for q in live['quests']};mapping={}
 for q in wanted['quests']:
  id=canonical(q['id']);old=by.get(id)
  if old is None and id in base:
   matches=[v for v in live['quests'] if v.get('title')==base[id].get('title') and v.get('icon')==base[id].get('icon')]
   if len(matches)==1:old=matches[0];by[id]=old
  mapping[id]=old['id'] if old else id
 preserved=[]
 for q in wanted['quests']:
  id=canonical(q['id']);old=by.get(id);new=json.loads(json.dumps(q));new['id']=mapping[id]
  new['dependencies']=[mapping.get(canonical(d),canonical(d)) for d in new.get('dependencies',[])]
  for t in new.get('tasks',[])+new.get('rewards',[]):t['id']=canonical(t['id'])
  if old is None:live['quests'].append(new);continue
  previous=base.get(id,{})
  for key,value in new.items():
   if key in ('id','tasks','rewards'):continue
   oldvalue=old.get(key,[] if key in ('dependencies','description') else None)
   basevalue=previous.get(key,[] if key in ('dependencies','description') else None)
   if oldvalue==basevalue or oldvalue==value:old[key]=value
   else:preserved.append((old['id'],key))
 liveids=[]
 for q in live['quests']:
  liveids.append(canonical(q['id']));liveids += [canonical(t['id']) for t in q.get('tasks',[])+q.get('rewards',[])]
 assert len(liveids)==len(set(liveids)),filename+' duplicate IDs'
 questids={canonical(q['id']) for q in live['quests']}
 assert all(canonical(d) in questids for q in live['quests'] for d in q.get('dependencies',[])),filename+' dangling dependency'
 return live,preserved
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--install',action='store_true');a=ap.parse_args()
 staging=R/'work/feedback-guide-staging';staging.mkdir(exist_ok=True);plans={};preserved={}
 for f in names:
  d,p=merged(f);(staging/f).write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8');plans[f]=len(d['quests']);preserved[f]=p
 globalids=set()
 for f in (LIVE/CH).glob('*.snbt'):
  target=staging/f.name if f.name in names else f;chapter=m.SnbtReader(target.read_text(encoding='utf-8-sig')).read()
  ids=[canonical(chapter['id'])]
  for q in chapter.get('quests',[]):
   ids.append(canonical(q['id']));ids += [canonical(t['id']) for t in q.get('tasks',[])+q.get('rewards',[])]
  assert not globalids.intersection(ids),f.name+' cross-chapter ID collision'
  globalids.update(ids)
 (R/'work/feedback-quest-merge-report.json').write_text(json.dumps({'chapters':plans,'preserved_manual_fields':preserved},ensure_ascii=False,indent=2),encoding='utf8')
 print('Prepared chapter merges:',plans,'manual fields retained:',sum(len(p) for p in preserved.values()))
 if not a.install:return
 log=(R/'work/ecology-feedback-final-tests.log').read_text(encoding='utf8',errors='replace')
 assert 'BUILD SUCCESSFUL' in log and re.search(r'All \d+ required tests passed',log) and 'failed!' not in log,'GameTests not passed'
 client=json.loads((R/'work/living-ecology-client-audit.json').read_text(encoding='utf8'));assert not client['failures']
 assert json.loads((R/'work/equipment-roundtrip-audit.json').read_text(encoding='utf8'))['round_trip']=='PASS'
 gamecheck=subprocess.run(['powershell','-NoProfile','-Command',"@(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match '元素觉醒1\\.4\\.3-魔改版-20260915' }).Count"],capture_output=True,text=True)
 assert gamecheck.returncode==0 and gamecheck.stdout.strip()=='0','Live game is running'
 jar=R/'build/libs/tnc-1.0.0.jar'
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None,'Jar archive is corrupt'
  entries=z.namelist();parsed=0
  for n in entries:
   if n.endswith('.json') and n.startswith(('assets/tnc/','data/tnc/')):json.loads(z.read(n));parsed+=1
  for n in ['assets/tnc/blockstates/forge_core.json','com/tnc/tnc/life/pasture/PastureFlight.class','com/tnc/tnc/production/ForgeBrickBlock.class']:assert n in entries,n
  assert len([n for n in entries if n.startswith('assets/tnc/models/armor/') and n.endswith('.json')])==34
  for p in (R/'src/main/resources/assets/tnc/models/armor').glob('*.json'):assert z.read('assets/tnc/models/armor/'+p.name)==p.read_bytes()
 protected={p.name:sha(p) for p in (LIVE/CH).glob('*.snbt') if p.name not in names}
 world={str(p):sha(p) for p in (LIVE/'saves').rglob('level.dat')}
 island_dirs=[LIVE/'kubejs/data/tnc/sky_island',LIVE/'kubejs/data/tnc/structures/sky_island']
 sky={str(p):sha(p) for directory in island_dirs for p in directory.rglob('*') if p.is_file()}
 stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S');backup=R/'work/backups'/('ecology-feedback-'+stamp)
 backup.mkdir(parents=True)
 installed=[]
 def copy(source,target,label):
  if target.exists():
   relative=target.relative_to(LIVE if label=='live' else MIRROR);old=backup/label/relative;old.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,old)
  target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);assert sha(source)==sha(target)
  installed.append(str(target))
 for label,target in [('live',LIVE),('mirror',MIRROR)]:
  copy(jar,target/'mods/tnc-1.0.0.jar',label)
  for f in names:copy(staging/f,target/CH/f,label)
  for f in ['ecology_plant_kit.mcfunction','ecology_animal_kit.mcfunction','ecology_workshop_kit.mcfunction']:
   copy(R/'src/main/resources/data/tnc/functions'/f,target/'kubejs/data/tnc/functions'/f,label)
 # Exact collaborator overlay changes only, never blanket-copy the whole modpack.
 files=subprocess.check_output(['git','-c','core.quotepath=false','diff','--name-only','e612b330','06be2ef2','--','modpack'],cwd=R,text=True,encoding='utf8').splitlines()
 for f in files:
  source=R/f;relative=source.relative_to(MIRROR)
  assert relative.as_posix().startswith('kubejs/assets/tnc/textures/spell/'),f
  copy(source,LIVE/relative,'live')
 for name,h in protected.items():assert sha(LIVE/CH/name)==h,name+' changed'
 assert all(sha(Path(p))==h for p,h in world.items()),'World metadata changed'
 assert all(sha(Path(p))==h for p,h in sky.items()),'Island template changed'
 receipt=dict(installed_at=datetime.datetime.now().isoformat(),live_pack=str(LIVE),backup=str(backup),sha256=sha(jar),gametests=int(re.search(r'All (\d+) required tests passed',log)[1]),client_audit=client,equipment_projects=70,chapters=plans,manual_fields_preserved=preserved,unchanged_chapters=len(protected),unchanged_chapter_hashes=protected,world_metadata_verified=len(world),island_template_files_verified=len(sky),parsed_jar_json=parsed,installed_files=installed,collaborator_head='9108ce05',merge_commit='06be2ef2',saved_worlds_modified=False,island_templates_modified=False)
 (R/'work/ecology-feedback-install-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf8')
 print('INSTALLED:',receipt['sha256'],'files:',len(installed),'GameTests:',receipt['gametests'],'protected chapters:',len(protected))
if __name__=='__main__':main()
