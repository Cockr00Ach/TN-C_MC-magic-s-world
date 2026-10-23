"""Append new wind/water realm code and metadata while preserving the live pack."""
from pathlib import Path
import argparse,hashlib,json,zipfile,subprocess
from prepare_dungeon_mob_install import members
ROOT=Path(__file__).resolve().parents[1]
TRANSLATIONS={
 'zh_cn':{'block.tnc.wind_arena_runestone_1':'苍岚·青绿风纹石','block.tnc.wind_arena_runestone_2':'苍岚·深碧风纹石','dimension.tnc.wind_arena':'苍岚柱阵','dimension.tnc.water_arena':'水渊棋境','biome.tnc.wind_arena':'苍岚柱阵','biome.tnc.water_arena':'水渊棋境'},
 'en_us':{'block.tnc.wind_arena_runestone_1':'Jade Gale Runestone','block.tnc.wind_arena_runestone_2':'Deep Gale Runestone','dimension.tnc.wind_arena':'Gale Chessboard','dimension.tnc.water_arena':'Abyssal Chessboard','biome.tnc.wind_arena':'Gale Chessboard','biome.tnc.water_arena':'Abyssal Chessboard'}}
for i,zh in enumerate(['玉白石','暗青石','翠玉石','风暴板岩','香槟金','卷风徽石','风庭槽柱石','金线墨石'],1):
 TRANSLATIONS['zh_cn']['block.tnc.wind_arena_runestone_'+str(i)]='苍岚·'+zh
 TRANSLATIONS['en_us']['block.tnc.wind_arena_runestone_'+str(i)]='Gale Runestone '+str(i)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor:
  names=[n for n in donor.namelist() if n.startswith('com/tnc/tnc/world/elementarena/') and n.endswith('.class') and 'GameTests' not in n and 'ClientAudit' not in n]
  names += [n for n in donor.namelist() if (n.endswith('.json') and n.startswith(('data/tnc/dimension/','data/tnc/dimension_type/','data/tnc/worldgen/biome/')) and Path(n).stem in ['wind_arena','water_arena']) or (n.startswith('assets/tnc/') and 'wind_arena_runestone_' in n and n.endswith(('.json','.png')))]
  assert names and all(n not in src.namelist() for n in names),'Arena content already exists; merge before replacing'
  assert all('assets/tnc/textures/block/'+n in src.namelist() for n in ['light_boss_runestone_2.png','light_boss_runestone_7.png','water_boss_runestone_2.png','water_boss_runestone_7.png'])
  payload={n:donor.read(n) for n in names}
  for lang,changes in TRANSLATIONS.items():
   n='assets/tnc/lang/'+lang+'.json';data=json.loads(src.read(n));data.update(changes);payload[n]=json.dumps(data,ensure_ascii=False,indent=2).encode()
  n='com/tnc/tnc/world/ink/InkRealmTravel.class'
  asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
  subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireBossMusicMethod.java')],check=True)
  a=out/'ink-original.class';b=out/'ink-donor.class';c=out/'ink-patched.class';a.write_bytes(src.read(n));b.write_bytes(donor.read(n))
  subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireBossMusicMethod',str(a),str(b),str(c),'teleport'],check=True)
  beforefields,beforemethods=members(src.read(n));afterfields,aftermethods=members(c.read_bytes());assert beforefields==afterfields and beforemethods.keys()==aftermethods.keys()
  assert all(raw==aftermethods[k] for k,raw in beforemethods.items() if k[0]!='teleport')
  payload[n]=c.read_bytes()
  jar=out/'tnc-1.0.0.jar'
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as dst:
   for entry in src.infolist():dst.writestr(entry,payload.get(entry.filename,src.read(entry)))
   for n,data in payload.items():
    if n not in src.namelist():dst.writestr(n,data)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(jar) as dst:
  assert dst.testzip() is None;assert all(src.read(n)==dst.read(n) for n in src.namelist() if n not in payload)
 assert digest(base)==before
 manifest={'jar':str(jar.resolve()),'baseInstalledHash':before,'files':[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]}
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8');print(digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
