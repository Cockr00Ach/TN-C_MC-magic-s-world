"""Overlay only authored Canglan animation timing, combat and model assets."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
CLASSES=['com/tnc/tnc/world/wind/'+n+'.class' for n in ['CanglanWarGodRules','CanglanWarlordCombat','CanglanWarlordAttackRules','CanglanEchoEntity','CanglanWarlordRegistration','client/CanglanEchoRenderer','client/CanglanEchoRenderer$Model','CanglanTempestEntity','client/CanglanTempestRenderer']]
ASSETS=['assets/tnc/'+n for n in ['geo/entity/canglan_warlord.geo.json','geo/entity/canglan_echo.geo.json','animations/entity/canglan_warlord.animation.json']]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor:
  # The installed private combat/timing classes must still be the previous validated baseline.
  baseline=ROOT/'work/canglan-spear-install-20261022/tnc-1.0.0.jar'
  with zipfile.ZipFile(baseline) as prior:
   assert all(src.read(n)==prior.read(n) for n in CLASSES),'Canglan combat was updated externally; merge before installation'
  payload={n:donor.read(n) for n in CLASSES}
  payload.update({n:(ROOT/'src/main/resources'/n).read_bytes() for n in ASSETS})
  jar=out/'tnc-1.0.0.jar'
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as dst:
   for entry in src.infolist():dst.writestr(entry,payload.get(entry.filename,src.read(entry)))
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(jar) as dst:
  assert dst.testzip() is None and set(src.namelist())==set(dst.namelist())
  assert all(src.read(n)==dst.read(n) for n in src.namelist() if n not in payload)
 assert digest(base)==before
 manifest={'jar':str(jar.resolve()),'baseInstalledHash':before,'files':[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]}
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8')
 print('Prepared authored Canglan overlay:',digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
