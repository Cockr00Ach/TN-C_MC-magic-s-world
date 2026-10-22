"""Scoped rehearsal command + movement starvation fix; preserve all other installed code."""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile
from prepare_dungeon_mob_install import members
ROOT=Path(__file__).resolve().parents[1]
CLASSES=['com/tnc/tnc/world/wind/'+n+'.class' for n in ['CanglanWarGodRules','CanglanWarlordCombat','CanglanTestCommands']]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
 subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireBossMusicMethod.java')],check=True)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor:
  with zipfile.ZipFile(ROOT/'work/canglan-authored-fusion-install-20261022/tnc-1.0.0.jar') as prior:
   assert all(src.read(n)==prior.read(n) for n in CLASSES[:2]),'Canglan combat changed externally; merge before installing'
  payload={n:donor.read(n) for n in CLASSES}
  n='com/tnc/tnc/world/wind/CanglanWarlordEntity.class';original=out/'original.class';compiled=out/'donor.class';patched=out/'patched.class'
  original.write_bytes(src.read(n));compiled.write_bytes(donor.read(n))
  subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireBossMusicMethod',str(original),str(compiled),str(patched),'canApproach'],check=True)
  data=patched.read_bytes();fields,methods=members(src.read(n));newfields,newmethods=members(data)
  assert fields==newfields and methods.keys()==newmethods.keys()
  assert all(raw==newmethods[k] for k,raw in methods.items() if k[0]!='canApproach')
  payload[n]=data;jar=out/'tnc-1.0.0.jar'
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as dst:
   for entry in src.infolist():dst.writestr(entry,payload.get(entry.filename,src.read(entry)))
   for n,data in payload.items():
    if n not in src.namelist():dst.writestr(n,data)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(jar) as dst:
  assert dst.testzip() is None
  assert all(src.read(n)==dst.read(n) for n in src.namelist() if n not in payload)
 assert digest(base)==before
 manifest={'jar':str(jar.resolve()),'baseInstalledHash':before,'files':[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]}
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8')
 print('Prepared four-class rehearsal overlay; only canApproach changed in entity:',digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
