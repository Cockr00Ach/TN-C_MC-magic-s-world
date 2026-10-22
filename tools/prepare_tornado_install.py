"""Overlay only the shared tornado mesh and atlas declaration onto the current pack."""
from pathlib import Path
import argparse,json,hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
ASSETS=['assets/tnc/models/projectile/wind_tornado.json','assets/tnc/models/projectile/wind_tornado.obj','assets/tnc/models/projectile/wind_tornado.mtl','assets/tnc/textures/spell_projectile/wind_tornado.png']
ATLAS='assets/minecraft/atlases/blocks.json'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,output):
 base=live/'mods/tnc-1.0.0.jar';before=digest(base);output.mkdir(parents=True,exist_ok=True);jar=output/'tnc-1.0.0.jar'
 payload={n:(ROOT/'src/main/resources'/n).read_bytes() for n in ASSETS}
 with zipfile.ZipFile(base) as source:
  atlas=json.loads(source.read(ATLAS)) if ATLAS in source.namelist() else {'sources':[]}
  addition={'type':'minecraft:single','resource':'tnc:spell_projectile/wind_tornado'}
  if addition not in atlas.setdefault('sources',[]):atlas['sources'].append(addition)
  payload[ATLAS]=(json.dumps(atlas,indent=2)+'\n').encode()
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as target:
   for entry in source.infolist():target.writestr(entry,payload.get(entry.filename,source.read(entry)))
   for n,data in payload.items():
    if n not in source.namelist():target.writestr(n,data)
 with zipfile.ZipFile(base) as source,zipfile.ZipFile(jar) as target:
  assert target.testzip() is None
  assert all(source.read(n)==target.read(n) for n in source.namelist() if n not in payload)
 assert digest(base)==before,'Live mod changed while packaging'
 manifest={'jar':str(jar.resolve()),'baseInstalledHash':before,'files':[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]}
 (output/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
 (output/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8')
 print('Prepared 5 tornado assets; all unrelated entries unchanged;',digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.output)
