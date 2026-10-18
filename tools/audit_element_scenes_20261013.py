from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
paths=json.loads((root/'work/element-scenes-assets.json').read_text(encoding='utf-8'))
for path in paths:
    current=json.loads((root/path).read_text(encoding='utf-8-sig'))
    if '/spells/' in path:
        previous=json.loads((root/'work/element-scenes-source-baseline'/path).read_text(encoding='utf-8-sig'))
        for field in ['cost','learn','school','group']:assert current[field]==previous[field],(path,field)
        if current['group']=='earth_mud':assert current['release']['target']['cloud']['volume']['radius']==previous['release']['target']['cloud']['volume']['radius']*2
        elif current['learn']['tier']>=3 or current['group']=='earth_move':assert current['release']['target']['type']=='SELF' and current['impact']==[],path
        else:assert current['release']['target']['projectile']['projectile']['client_data']['model']['scale']==previous['release']['target']['projectile']['projectile']['client_data']['model']['scale']*2
for path in (root/'src/main/resources/assets/tnc/models/projectile').glob('earth_*.json'):
    s=json.loads(path.read_text(encoding='utf-8-sig'));assert len(s['elements'])<=32
    for cube in s['elements']:
        assert all(a<b for a,b in zip(cube['from'],cube['to']));assert all(-16<=v<=32 for v in cube['from']+cube['to'])
        for face in cube['faces'].values():assert face['texture'].lstrip('#') in s['textures'] and all(0<=v<=16 for v in face.get('uv',[0,0,16,16]))
print('PASS: costs, tiers and schools retained; doubled radii/scales match; staged spells cannot double-hit through engine impacts; solid models and UVs valid.')
