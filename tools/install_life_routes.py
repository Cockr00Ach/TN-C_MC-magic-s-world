"""Deploy only the TN-C jar and four owned chapters; preserve all other chapters.
Usage: python tools/install_life_routes.py --live "your version folder" --apply
Build/check commands and copying are deliberately separate; never touches saves.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shutil,subprocess
from verify_life_routes import run as verify
R=Path(__file__).resolve().parents[1];Q=R/'questbook/ftbquests';GROUP='544E434C49464531'
FILES=['tnc_field_02_botany.snbt','tnc_field_03_pasture.snbt','tnc_field_04_workshop.snbt','tnc_field_05_seasons.snbt']
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def groups(text):
 if GROUP in text:return text
 group={'id':GROUP,'title':'&b&lRouchNao · 生活与魔导','collapse':False}
 try:
  value=json.loads(text);value.setdefault('chapter_groups',[]).append(group);return json.dumps(value,ensure_ascii=False,indent=2)+'\n'
 except json.JSONDecodeError:pass
 match=re.search(r'\bchapter_groups\s*:\s*\[',text)
 if not match:raise ValueError('No chapter_groups list; nothing installed')
 depth=1;quoted=False;escaped=False
 for i in range(match.end(),len(text)):
  ch=text[i]
  if quoted:
   if escaped:escaped=False
   elif ch=='\\':escaped=True
   elif ch=='"':quoted=False
   continue
  if ch=='"':quoted=True
  elif ch=='[':depth+=1
  elif ch==']':
   depth-=1
   if depth==0:return text[:i]+'\n\t\t'+json.dumps(group,ensure_ascii=False)+'\n\t'+text[i:]
 raise ValueError('Unclosed chapter_groups list; nothing installed')
def running(live):
 command="$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[System.Text.UTF8Encoding]::new(); Get-CimInstance Win32_Process | Where-Object {$_.Name -in @('java.exe','javaw.exe')} | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"
 result=subprocess.run(['powershell','-NoProfile','-Command',command],capture_output=True,encoding='utf8',errors='replace',timeout=30,check=True)
 if not result.stdout.strip():return []
 rows=json.loads(result.stdout);rows=rows if isinstance(rows,list) else [rows]
 return [r['ProcessId'] for r in rows if any(token in (r.get('CommandLine') or '') for token in [live.name,'BootstrapLauncher','net.minecraft.client.main.Main','forgeclientuserdev','forgegametestserveruserdev'])]
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--apply',action='store_true');a=ap.parse_args();live=Path(a.live).resolve();assert (live/'mods').is_dir(),live
 verify();jar=R/'build/libs/tnc-1.0.0.jar';assert jar.is_file()
 final=(R/'work/life-routes-final.log').read_text(encoding='utf8',errors='replace');assert 'All 22 required tests passed' in final and 'BUILD SUCCESSFUL' in final and 'failed!' not in final
 ftb=(R/'work/ftb-real-loading.log').read_text(encoding='utf8',errors='replace')
 assert '4 chapters, 409 quests' in ftb and 'All 22 required tests passed' in ftb and 'BUILD SUCCESSFUL' in ftb and 'NumberFormatException' not in ftb,'Actual installed-version FTB load test must pass'
 tested_input=R/'work/ftb-route-load-input.json';hashes=json.loads(tested_input.read_text(encoding='utf8'))
 assert (R/'work/ftb-real-loading.log').stat().st_mtime>=tested_input.stat().st_mtime,'FTB test predates prepared inputs'
 for name in FILES:
  assert hashes[name]==digest(Q/'chapters'/name),'FTB tested input is stale: '+name
 audit=json.loads((R/'work/life-routes-client-audit.json').read_text(encoding='utf8'));assert audit['screenshots']==8 and not audit['failures']
 latest_source=max(p.stat().st_mtime for root in [R/'src/main/java',R/'src/main/resources'] for p in root.rglob('*') if p.is_file())
 assert jar.stat().st_mtime>=latest_source,'Jar is older than runtime sources; rebuild first'
 assert (R/'work/life-routes-client-audit.json').stat().st_mtime>=latest_source,'Client audit is older than runtime sources; rerun it'
 junit=list((R/'build/test-results/test').glob('TEST-*.xml'));assert junit
 import xml.etree.ElementTree as ET
 count=0
 for report in junit:
  root=ET.parse(report).getroot();assert int(root.attrib['failures'])==0 and int(root.attrib['errors'])==0;count+=int(root.attrib['tests'])
 targets=[('live',live),('mirror',R/'modpack/元素觉醒1.4.3-魔改版-20260915')]
 planned=[]
 for name,target in targets:
  base=target/'config/ftbquests/quests';group=base/'chapter_groups.snbt'
  seed=group if group.exists() else live/'config/ftbquests/quests/chapter_groups.snbt'
  planned.append((name,target,groups(seed.read_text(encoding='utf-8-sig'))))
 override_spec=json.loads((R/'release/life-routes-collaborator-overrides-20261007.json').read_text(encoding='utf8'))
 mirror=R/'modpack/元素觉醒1.4.3-魔改版-20260915'
 for change in override_spec['changes']:
  path=Path(change['path']);assert not path.is_absolute() and '..' not in path.parts
  assert path.parts[0] in ('kubejs','config')
  if change['status']!='D':assert (mirror/path).is_file(),path
 pids=running(live);assert not pids,f'Minecraft/test JVM running: {pids}; no files copied'
 if not a.apply:print('Checks passed; dry run. Use --apply to install.');return
 backup=R/'work/backups'/('life-routes-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
 receipt={'installed_at':datetime.datetime.now().isoformat(),'jar_sha256':digest(jar),'backup':str(backup),'unit_tests':count,'route_gametests':22,'chapters':FILES,'saves_changed':False,'island_templates_changed':False,'installed':[]}
 for name,target,text in planned:
  base=target/'config/ftbquests/quests';chapters=base/'chapters';protected={p.name:digest(p) for p in chapters.glob('*.snbt') if p.name not in FILES};b=backup/name;b.mkdir()
  chapters.mkdir(parents=True,exist_ok=True);(target/'mods').mkdir(exist_ok=True)
  files=[target/'mods/tnc-1.0.0.jar',base/'chapter_groups.snbt']+[chapters/f for f in FILES]
  for p in files:
   if p.exists():shutil.copy2(p,b/p.name)
  shutil.copy2(jar,target/'mods/tnc-1.0.0.jar')
  for f in FILES:shutil.copy2(Q/'chapters'/f,chapters/f)
  (base/'chapter_groups.snbt').write_text(text,encoding='utf8')
  assert digest(target/'mods/tnc-1.0.0.jar')==digest(jar)
  assert all(digest(chapters/f)==value for f,value in protected.items())
  receipt['installed'].append({'target':name,'root':str(target),'unchanged_chapters':len(protected)})
 override_backup=backup/'collaborator-overrides';updated=[]
 for change in override_spec['changes']:
  rel=Path(change['path']);target=(live/rel).resolve();assert target.is_relative_to(live)
  if target.exists():
   assert target.is_file(),target
   saved=override_backup/rel;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
  if change['status']=='D':
   if target.exists():target.unlink()
  else:
   target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(mirror/rel,target);assert digest(target)==digest(mirror/rel)
  updated.append(change)
 verify(check_mirror=True)
 receipt['collaborator_override_changes']=updated
 (R/'work/life-routes-install-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
