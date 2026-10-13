"""Finish native models and connect new harvests to the existing economy."""
from pathlib import Path
import json,re
R=Path(__file__).resolve().parents[1];S=R/'src/main/resources'
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def box(a,b,texture):return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['up','down','north','south','east','west']}}
for tier,id in [(2,'radiant_cage'),(3,'star_cage')]:
 elements=[box([1,0,1],[15,2,15],'wood'),box([1,13,1],[15,15,15],'metal')]
 for x in [2,7,12]:
  for z in [2,12]:elements.append(box([x,2,z],[x+1,13,z+1],'metal'))
 for z in [6,10]:
  for x in [2,12]:elements.append(box([x,2,z],[x+1,13,z+1],'metal'))
 elements.extend([box([1,6,1],[15,7,2],'metal'),box([1,6,13],[15,7,14],'metal'),box([6,4,0],[10,8,1],'glass')])
 if tier==3:elements.extend([box([5,15,5],[11,16,11],'glass'),box([0,2,0],[2,14,2],'glass'),box([14,2,14],[16,14,16],'glass')])
 write(S/f'assets/tnc/models/item/{id}.json',{'parent':'minecraft:block/block','textures':{'wood':'tnc:block/art_wood','metal':'tnc:block/art_copper' if tier==2 else 'tnc:block/art_iron','glass':'tnc:block/energy_glass','particle':'tnc:block/art_copper'},'elements':elements,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.72,.72,.72]},'firstperson_righthand':{'rotation':[0,40,0],'translation':[1,2,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.45,.45,.45]}}})
# Preserve old objectives; append bounded optional orders for the new source materials.
path=S/'data/bountiful/bounty_pools/tnc/tnc_supply_objs.json';pool=json.loads(path.read_text(encoding='utf8'))
new=re.findall(r'\w+\("[a-z_]+","[^"]+","([a-z_]+)","([^"]+)"',(R/'src/main/java/com/tnc/tnc/life/routes/NewPlantKind.java').read_text(encoding='utf8'))
new += [('prism_horn_shard','彩角碎'),('sand_otter_fiber','砂獭纤维')]
for id,name in new:pool['content']['tnc_supply_'+id]={'type':'item','content':'tnc:'+id,'amount':{'min':2,'max':4},'unitWorth':160,'rarity':'UNCOMMON','timeMult':3.0}
write(path,pool)
p=R/'src/main/java/com/tnc/tnc/adventure/ShopCatalog.java';s=p.read_text(encoding='utf8')
marker='    public static Goods find('
before,after=s.split(marker,1)
if 'new Goods("dream_humus"' not in before:
 at=before.rfind('));'); before=before[:at+1]+',\n'+',\n'.join(f'        new Goods("{id}","{name} ×4","tnc:{id}",4,20,"人工魔法土培育或异兽真实产物，可出售或留作工坊原料。")' for id,name in new)+');\n'+before[at+3:]
 s=before+marker+after;p.write_text(s,encoding='utf8')
print('Distinct cage models; eleven new materials added to native orders and shop buying.')
