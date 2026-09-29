"""Offline explicit residential selection; never scans or edits a player's save."""
from pathlib import Path
import json, nbtlib
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc'
d=json.loads((DATA/'sky_island/manifest.json').read_text(encoding='utf-8-sig'))
lo=(182,90,384);hi=(199,117,398)
palette=[];states={};blocks=[]
furnish=['_bed','chest','barrel','furnace','crafting_table','brewing_stand','enchanting_table','bookshelf','carpet','flower_pot','potted_','candle','lantern','torch','painting','head','skull','banner','anvil','grindstone','smithing_table','loom','cartography_table','stonecutter','lectern']
for piece in d['pieces']:
    if piece['layer']!='town':continue
    ox,oy,oz=piece['offset'];sx,sy,sz=piece['size']
    if any(o+s<l or o>h for o,s,l,h in zip((ox,oy,oz),(sx,sy,sz),lo,hi)):continue
    t=nbtlib.load(DATA/'structures'/f"{piece['resource'].split(':')[1]}.nbt")
    for block in t['blocks']:
        pos=tuple(int(v)+o for v,o in zip(block['pos'],piece['offset']))
        if not all(l<=v<=h for l,v,h in zip(lo,pos,hi)):continue
        state=t['palette'][int(block['state'])];name=str(state['Name']);props={str(k):str(v) for k,v in state.get('Properties',{}).items()}
        key=name+('['+','.join(k+'='+v for k,v in sorted(props.items()))+']' if props else '')
        if key not in states:states[key]=len(palette);palette.append({'name':name,'properties':props})
        remove=any(s in name for s in furnish) or (name=='minecraft:hay_block' and 183<=pos[0]<=198 and 385<=pos[2]<=397)
        blocks.append({'pos':pos,'state':states[key],'clear':remove})
occupied={tuple(b['pos']) for b in blocks}
air=len(palette);palette.append({'name':'minecraft:air','properties':{}})
for x in range(lo[0],hi[0]+1):
    for y in range(lo[1],hi[1]+1):
        for z in range(lo[2],hi[2]+1):
            if (x,y,z) not in occupied:blocks.append({'pos':(x,y,z),'state':air,'clear':False})
out=ROOT/'src/main/resources/data/tnc/housing/south_cottage.json';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({'id':'south_cottage','name':'南街三层空屋','price':500,'min':lo,'max':hi,'entry':[189,92,394],'palette':palette,'blocks':blocks},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('baseline blocks',len(blocks),'furnishings',sum(b['clear'] for b in blocks))
