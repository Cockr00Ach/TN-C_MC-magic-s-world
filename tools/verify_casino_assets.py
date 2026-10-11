"""Validate casino resource references without changing the pack or saves."""
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
IDS=['slot_machine','roulette_table','gamble_table','lucky_wheel','card_table']
errors=[]
for name in IDS:
    bs=json.loads((ASSETS/'blockstates'/(name+'.json')).read_text(encoding='utf-8'))
    if len(bs['variants'])!=8:errors.append(name+': expected eight directional states')
    for v in bs['variants'].values():
        if v['model']!='tnc:block/'+name:errors.append(name+': wrong host model')
    model=json.loads((ASSETS/'models/item'/(name+'.json')).read_text(encoding='utf-8'))
    tex=model['textures']['layer0'].replace('tnc:','')
    if not (ASSETS/'textures'/(tex+'.png')).exists():errors.append(name+': missing icon')
    recipe=json.loads((ROOT/'src/main/resources/data/tnc/recipes'/('casino_'+name+'.json')).read_text())
    if recipe['result']['item']!='tnc:'+name:errors.append(name+': wrong recipe result')
    loot=json.loads((ROOT/'src/main/resources/data/tnc/loot_tables/blocks'/(name+'.json')).read_text())
    if loot['pools'][0]['entries'][0]['name']!='tnc:'+name:errors.append(name+': incorrect device drop')
for language in ['en_us','zh_cn']:
    loc=json.loads((ASSETS/'lang'/(language+'.json')).read_text(encoding='utf-8'))
    for name in IDS:
        if 'block.tnc.'+name not in loc:errors.append(language+': missing name '+name)
for name in IDS:
    model=json.loads((ROOT/'model-source/casino'/(name+'.bbmodel')).read_text(encoding='utf-8'))
    if not model['elements'] or not model['textures'][0]['source'].startswith('data:image/png;base64,'):errors.append(name+': incomplete editable reference')
print(json.dumps({'machines':len(IDS),'recipes':len(IDS),'editable_models':len(IDS),'failures':errors},ensure_ascii=False,indent=2))
sys.exit(bool(errors))
