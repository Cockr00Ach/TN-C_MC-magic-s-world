"""Overlay only Canglan tactics, cinematic and visuals onto the latest installed pack."""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile
from prepare_dungeon_mob_install import members
ROOT=Path(__file__).resolve().parents[1]
PREFIX='com/tnc/tnc/world/wind/'
CLASSES=[PREFIX+n+'.class' for n in ['CanglanWarGodRules','CanglanWarlordCombat','CanglanTestCommands','CanglanTempestEntity','CanglanCinematicNetwork','CanglanCinematicNetwork$Shot','client/CanglanTempestRenderer','client/CanglanEchoRenderer','client/CanglanManaLinks','client/CanglanWarlordRenderer','client/CanglanSpearTrail','client/CanglanSpearTrail$Trail','client/CanglanCinematicCamera']]
ASSETS=['assets/tnc/textures/'+n for n in ['entity/canglan_eyes_emissive.png','entity/canglan_mana_flow.png','spell_projectile/wind_tornado.png']]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
 subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireBossMusicMethod.java')],check=True)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor,zipfile.ZipFile(ROOT/'work/canglan-test-install-20261022/tnc-1.0.0.jar') as prior:
  for n in CLASSES:
   if n in prior.namelist():assert src.read(n)==prior.read(n),'Owned class changed externally: '+n
   else:assert n not in src.namelist(),'New class already exists: '+n
  payload={n:donor.read(n) for n in CLASSES+ASSETS}
  n=PREFIX+'CanglanWarlordEntity.class';original=out/'original.class';compiled=out/'donor.class';patched=out/'patched.class'
  original.write_bytes(src.read(n));compiled.write_bytes(donor.read(n));data=src.read(n)
  allowed={'createAttributes','m_8099_','m_8107_'}
  for method in sorted(allowed):
   original.write_bytes(data)
   subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireBossMusicMethod',str(original),str(compiled),str(patched),method],check=True)
   data=patched.read_bytes()
  fields,methods=members(src.read(n));newfields,newmethods=members(data)
  assert fields==newfields and methods.keys()==newmethods.keys()
  assert all(raw==newmethods[k] for k,raw in methods.items() if k[0] not in allowed)
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
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'entityMethods':sorted(allowed),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8')
 print('Prepared scoped tactics/cinematic overlay:',digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
