"""Extract five explicit source houses and a separately bounded land catalogue.
Reads shipped structure tiles only; never reads or writes a player world.
"""
from pathlib import Path
import json,nbtlib
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc'
OUT=ROOT/'src/main/resources/data/tnc/housing'
plots=[
 dict(id='sweet_cottage',number='26',name='甜麦小屋',kind='HOME',price=1800,min=[345,89,246],max=[355,115,258],entry=[350,90,255],detail='紧凑小屋，靠近制杖屋；适合先安顿再慢慢装修。'),
 dict(id='lamp_cottage',number='27',name='灯下小筑',kind='HOME',price=2600,min=[221,89,255],max=[239,118,267],entry=[233,90,264],detail='街心小筑，靠近铁匠与商行；留出餐厅和卧室空间。'),
 dict(id='south_cottage',number='40',name='南街三层屋',kind='HOME',price=3000,min=[182,90,384],max=[199,117,398],entry=[189,92,394],detail='南街三层住宅，邻近入镇方向，适合分层布置。'),
 dict(id='east_house',number='24',name='东庭双层宅',kind='HOME',price=3600,min=[374,89,241],max=[388,115,254],entry=[378,90,252],detail='东侧双层宅，远离商业街的忙碌，邻近东部花园方向。'),
 dict(id='book_house',number='16',name='书风宅',kind='HOME',price=4200,min=[201,99,211],max=[217,128,226],entry=[206,100,219],detail='北街较宽住宅，留出书房与家人共同生活的空间。'),
]
for i,(x,z) in enumerate([(312,348),(328,348),(312,369),(328,369)],1):
 plots.append(dict(id=f'field_0{i}',number=f'F0{i}',name=f'南圃{i}号农田',kind='FIELD',price=300,min=[x,78,z],max=[x+13,112,z+15],entry=[x+6,88,z-1],detail='14×16土地，原状交付，可自行整地灌溉；边界内可建农具棚。'))
plots.append(dict(id='pasture_01',number='P01',name='南圃牧场',kind='PASTURE',price=600,min=[312,78,392],max=[332,112,411],entry=[311,86,401],detail='21×20土地，原状交付，可围栏、养殖和建小型畜舍。'))
for i,a in enumerate(plots):
 for b in plots[i+1:]:
  assert any(a['max'][j]<b['min'][j] or b['max'][j]<a['min'][j] for j in range(3)),(a['id'],b['id'])
manifest=json.loads((DATA/'sky_island/manifest.json').read_text(encoding='utf-8-sig'))
remove=['_bed','chest','barrel','furnace','crafting_table','brewing_stand','enchanting_table','bookshelf','carpet','flower_pot','potted_','candle','lantern','torch','painting','head','skull','banner','anvil','grindstone','smithing_table','loom','cartography_table','stonecutter','lectern']
for plot in plots:
 if plot['kind']!='HOME' or plot['id']=='south_cottage':continue
 lo,hi=plot['min'],plot['max'];palette=[];state_ids={};cells={}
 for piece in manifest['pieces']:
  if piece['layer']!='town':continue
  o,s=piece['offset'],piece['size']
  if any(o[i]+s[i]<=lo[i] or o[i]>hi[i] for i in range(3)):continue
  tile=nbtlib.load(DATA/'structures'/f"{piece['resource'].split(':')[1]}.nbt")
  for b in tile['blocks']:
   p=tuple(int(v)+a for v,a in zip(b['pos'],o))
   if not all(lo[i]<=p[i]<=hi[i] for i in range(3)):continue
   st=tile['palette'][int(b['state'])];name=str(st['Name']);props={str(k):str(v) for k,v in st.get('Properties',{}).items()}
   key=json.dumps([name,props],sort_keys=True)
   if key not in state_ids:state_ids[key]=len(palette);palette.append(dict(name=name,properties=props))
   cells[p]=dict(pos=p,state=state_ids[key],clear=any(k in name for k in remove))
 air=len(palette);palette.append(dict(name='minecraft:air',properties={}))
 for x in range(lo[0],hi[0]+1):
  for y in range(lo[1],hi[1]+1):
   for z in range(lo[2],hi[2]+1):cells.setdefault((x,y,z),dict(pos=[x,y,z],state=air,clear=False))
 assert any('door' in st['name'] for st in palette),plot['id']
 OUT.mkdir(parents=True,exist_ok=True)
 (OUT/f"{plot['id']}.json").write_text(json.dumps(dict(id=plot['id'],min=lo,max=hi,entry=plot['entry'],palette=palette,blocks=list(cells.values())),ensure_ascii=False,separators=(',',':')),encoding='utf8')
 print(plot['number'],len(cells),'cells,',sum(c['clear'] for c in cells.values()),'furnishings')
(OUT/'catalog.json').write_text(json.dumps(dict(town='RouchNao',plots=plots),ensure_ascii=False,indent=2),encoding='utf8')
