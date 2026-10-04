"""Close native asset references for all new ecological models before packaging."""
from pathlib import Path
import json,re
from PIL import Image
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
errors=[];models=[]
for p in (A/'models').rglob('*.json'):
    if p.name.startswith(('botanical_','sky_','canopy_','farlight_','field_sound_')) or p.parent.name=='block' and any(p.name.startswith(x) for x in ['dawn_disk','hearth_pepper','mist_cotton','stone_fern','mirror_lotus','wish_puff','echo_bean','ladder_vine','frost_chime','salt_ink','wind_sail','sleep_clock','shadow_cut','paper_tree','flight_pod','honey_cluster','star_dew','dance_bell','star_rest','pasture_','mana_bottle','refined_mana']):models.append(p)
def inspect(p,seen):
    if p in seen:return
    seen.add(p)
    try:d=json.loads(p.read_text(encoding='utf-8'))
    except Exception as e:errors.append(f'{p}: {e}');return
    parent=d.get('parent','')
    if parent.startswith('tnc:'):
        q=A/'models'/(parent[4:]+'.json')
        if not q.exists():errors.append(f'{p.name}: missing parent {parent}')
        else:inspect(q,seen)
    for ref in d.get('textures',{}).values():
        if ref.startswith('tnc:') and not (A/'textures'/(ref[4:]+'.png')).exists():errors.append(f'{p.name}: missing texture {ref}')
    for override in d.get('overrides',[]):
        ref=override.get('model','')
        if ref.startswith('tnc:'):
            q=A/'models'/(ref[4:]+'.json')
            if not q.exists():errors.append(f'{p.name}: missing override {ref}')
            else:inspect(q,seen)
seen=set()
for p in models:inspect(p,seen)
for p in (A/'blockstates').glob('*.json'):
    d=json.loads(p.read_text(encoding='utf-8'))
    def walk(v):
        if isinstance(v,dict):
            if v.get('model','').startswith('tnc:'):
                ref=v['model'];q=A/'models'/(ref[4:]+'.json')
                if not q.exists():errors.append(f'{p.name}: missing state model {ref}')
            for x in v.values():walk(x)
        elif isinstance(v,list):
            for x in v:walk(x)
    walk(d)
pixel=0
for p in (A/'textures').rglob('*.png'):
    if p.stem.startswith(('sky_','canopy_','sound_relay_')):
        if Image.open(p).size!=(16,16):errors.append(f'{p.name}: not native16px')
        pixel+=1
report={'checked_model_files':len(seen),'native_wonder_textures':pixel,'errors':errors}
(R/'work/living-ecology-assets-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False));raise SystemExit(bool(errors))
