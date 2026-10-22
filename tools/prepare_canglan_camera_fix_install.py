"""Four-class follow-up: stable face/spear framing and strict execution eligibility."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
CLASSES=['com/tnc/tnc/world/wind/'+n+'.class' for n in ['CanglanWarGodRules','CanglanWarlordCombat','client/CanglanWarlordRenderer','client/CanglanCinematicCamera']]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor,zipfile.ZipFile(ROOT/'work/canglan-tactical-install-20261023/tnc-1.0.0.jar') as prior:
  assert all(src.read(n)==prior.read(n) for n in CLASSES),'Owned Canglan code changed externally; merge before installing'
  payload={n:donor.read(n) for n in CLASSES};jar=out/'tnc-1.0.0.jar'
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as dst:
   for entry in src.infolist():dst.writestr(entry,payload.get(entry.filename,src.read(entry)))
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(jar) as dst:
  assert dst.testzip() is None
  assert set(src.namelist())==set(dst.namelist())
  assert all(src.read(n)==dst.read(n) for n in src.namelist() if n not in payload)
 assert digest(base)==before
 manifest={'jar':str(jar.resolve()),'baseInstalledHash':before,'files':[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]}
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8')
 print('Prepared stable camera and execution fix:',digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
