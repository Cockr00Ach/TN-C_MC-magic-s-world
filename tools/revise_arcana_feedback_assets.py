"""Apply the approved four-block kettle and remove retired alchemy tools. Idempotent."""
from pathlib import Path
import json,copy
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc';D=R/'src/main/resources/data/tnc'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
def run():
 source=R/'art/arcana/cauldron-one-block-source.json'
 if not source.exists():source.write_bytes((A/'models/block/spellflame_cauldron.json').read_bytes())
 model=json.loads(source.read_text('utf8'))
 for e in model['elements']:
  for key in ['from','to']:
   e[key]=[round(e[key][0]*2,4),round(e[key][1]*1.17,4),round(e[key][2]*2,4)]
  if 'rotation' in e:e['rotation']['origin']=[e['rotation']['origin'][0]*2,e['rotation']['origin'][1]*1.17,e['rotation']['origin'][2]*2]
 model['display']['gui']={'rotation':[25,225,0],'translation':[0,-1,0],'scale':[.55,.55,.55]}
 write(A/'models/block/spellflame_cauldron.json',model)
 write(A/'blockstates/spellflame_cauldron.json',{'variants':{f'part={i}':{'model':'tnc:block/spellflame_cauldron' if i==0 else 'minecraft:block/air'} for i in range(4)}})
 def box(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
 lid={'textures':model['textures'],'elements':[box([3,15.25,3],[29,16,29],'ceramic'),box([4,16,4],[28,16.4,28],'bronze'),box([6,16.4,6],[26,16.6,26],'glow'),box([7,16.6,7],[25,17,25],'ceramic'),box([14,17,13],[18,19,19],'bronze')]}
 for x in [5,25]:lid['elements'].append(box([x,16.8,5],[x+2,17.2,27],'bronze'))
 write(A/'models/block/spellflame_cauldron_lid.json',lid)
 loot=json.loads((D/'loot_tables/blocks/spellflame_cauldron.json').read_text('utf8'));pool=loot['pools'][0]
 pool['conditions']=[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'tnc:spellflame_cauldron','properties':{'part':'0'}}]
 ops=pool['entries'][0]['functions'][0]['ops'];ops[:]=[o for o in ops if o['source'] not in ['Stirs','StirredAt']]
 for key in ['Sealed','CookTicks','FrozenJob']:
  if not any(o['source']==key for o in ops):ops.append({'source':key,'target':'BlockEntityTag.'+key,'op':'replace'})
 write(D/'loot_tables/blocks/spellflame_cauldron.json',loot)
 for id in ['ember_blossom_salt','ritual_stirrer']:
  for p in [D/f'recipes/{id}.json',A/f'models/item/{id}.json',A/f'textures/item/{id}.png']:
   if p.exists():p.unlink()
 for locale in ['zh_cn','en_us']:
  p=A/f'lang/{locale}.json';j=json.loads(p.read_text('utf8'))
  for k in ['item.tnc.ember_blossom_salt','item.tnc.ritual_stirrer']:j.pop(k,None)
  if locale=='zh_cn':j['block.tnc.spellflame_cauldron']='四方魔焰锅';j['item.tnc.sealing_lid']='四方封魔锅盖'
  else:j['block.tnc.spellflame_cauldron']='Grand Spellflame Kettle';j['item.tnc.sealing_lid']='Grand Sealing Lid'
  write(p,j)
 q=R/'questbook/ftbquests/chapters/tnc_alchemy.snbt';j=json.loads(q.read_text('utf8'))
 for n in j['quests']:
  if '焰蕊盐' in n['title']:
   n['title']='&6四方锅盖 · 锁住药性';n['icon']='tnc:sealing_lid';n['description']=['铜锭与紫水晶制作可反复使用的四格锅盖。无需焰蕊盐和搅拌棒。','先看药材漩涡；右键盖锅，立即锁住当前融合。随后保持绿色温区再煮六秒，才能装瓶。']
   for t in n['tasks']:
    if t.get('item')=='tnc:ember_blossom_salt':t['item']='tnc:sealing_lid'
  elif '第一口药釜' in n['title']:n['description']=['留出2×2空位放四方魔焰锅。水桶倒水，投三味材料，用火法命中锅体。','无需燃料或搅拌。F8看整个锅周的颜色：蓝色偏冷，绿色合适，红色过热。用火法升温，水冰法降温。','每种药材拥有自己的漩涡，随时间靠拢；完全合一时盖锅锁定大师药性。提前盖锅为次品或标准，错过后解离。','盖锅会停止融合；继续在绿色温区煮六秒，最后用玻璃瓶盛取。']
  else:n['description']=[s.replace('投入焰蕊盐','无需燃料').replace('控温搅拌后封釜收汁','自动融合，盖锅锁定后再煮六秒').replace('各阶段搅拌','等待自动融合').replace('按阶段搅拌，最后用空瓶盛取','观察自动融合，盖锅锁定后绿温续煮六秒，最后用空瓶盛取').replace('（不含等待搅拌的时间）','（自动融合；封盖后另需绿温续煮六秒）') for s in n.get('description',[])]
 write(q,j)
 q=R/'questbook/ftbquests/chapters/tnc_deep_arcana.snbt';j=json.loads(q.read_text('utf8'))
 for n in j['quests']:
  if '餐盘魔法阵' in n['title']:n['description']=['仅接受我们的魔法植物收获物，普通食物和种子不能摆盘。对准九格分别右键放入，潜行空手取回。','每格植萃散发属性微粒：红火、蓝水、绿风、琥珀土、金色中和。F8可看得更清楚。','红与蓝不可直接相邻，金色植萃可隔开中和。九格齐全且五类成阵后，空手吸收这一盘魔植精华。','保留十五分钟微型太阳与供魔效果。']
  if '深渊聚能工程' in n['title']:n['description'].append('无需先接晶纤：外壳周围三格内，自有成熟产魔植物和异兽可把真实魔力直接送入塔；进量参与过载。')
  if '天空魔力高速路' in n['title']:n['description'].append('标记杖潜行点环重选起点，潜行对空清空。通路持续柔和托送，不会突然把人弹出去。')
 # Do not append the same explanatory sentence repeatedly.
 for n in j['quests']:n['description']=list(dict.fromkeys(n.get('description',[])))
 write(q,j)
if __name__=='__main__':run()
