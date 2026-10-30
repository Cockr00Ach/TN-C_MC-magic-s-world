"""Narrow install: the authored brush boss and shared ink effects only."""
import copy,hashlib,json,zipfile,struct
from pathlib import Path
root=Path(__file__).resolve().parents[1]
live=Path('E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
out=root/'work/ink-brush-fx-install-20261028';out.mkdir(exist_ok=False)
names=['com/tnc/tnc/world/ink/'+n+'.class' for n in ['InkBrushBossEntity','InkBrushRegistration']]
names+=['com/tnc/tnc/world/ink/client/'+n+'.class' for n in ['InkBrushRenderer','InkBrushRenderer$Registration','InkWashVisuals','InkTigerSpellVisuals','InkCraneRitualVisuals','InkDragonBreathVisuals']]
asset='assets/tnc/meshes/ink_brush.mesh'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
base=live/'mods/tnc-1.0.0.jar';before=sha(base)
with zipfile.ZipFile(base) as old,zipfile.ZipFile(root/'build/libs/tnc-1.0.0.jar') as built:
 payload={n:built.read(n) for n in names};payload[asset]=(root/'src/main/resources'/asset).read_bytes()
 assert all('Audit' not in n for n in payload)
 dest=out/'tnc-1.0.0.jar'
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
  for info in old.infolist():z.writestr(copy.copy(info),payload.get(info.filename,old.read(info.filename)))
  for n in sorted(payload.keys()-set(old.namelist())):z.writestr(n,payload[n])
 with zipfile.ZipFile(dest) as z:
  assert z.testzip() is None
  unchanged=[n for n in old.namelist() if n not in payload]
  assert all(z.read(n)==old.read(n) for n in unchanged)
  assert all(z.read(n)==v for n,v in payload.items())
assert sha(base)==before
manifest={'files':[{'source':str(dest),'relative':'mods/tnc-1.0.0.jar','hash':sha(dest),'expectedLiveHash':before}], 'patchEntries':sorted(payload),'unrelatedEntriesByteIdentical':len(unchanged),'summon':'/summon tnc:ink_brush_boss ~ ~ ~','sourceScanNotIncluded':True,'combatAndThreeStageEncounterUnchanged':True}
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(manifest,ensure_ascii=False,indent=2))
