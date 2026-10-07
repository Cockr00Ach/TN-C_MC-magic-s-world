"""Build four editable FTB chapters and in-game catalogues from the approved inventory.
Only the three TN-C field chapters and the new seasonal chapter are replaced.
Entity icon keys were checked against the installed FTB Entity Visualization bytecode.
"""
from pathlib import Path
import json,re,hashlib,zipfile
R=Path(__file__).resolve().parents[1]; RES=R/'src/main/resources'; J=R/'src/main/java/com/tnc/tnc/life'
PACK=R/'modpack/元素觉醒1.4.3-魔改版-20260915'; LIVE=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
Q=PACK/'config/ftbquests/quests'; GROUP='544E434C49464531'
def dump(p,x):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def uid(k):
 # Installed FTB QuestLink uses Long.parseLong(code, 16), not parseUnsignedLong.
 # Keep every generated identity and reference in the positive signed-long range.
 value=int(hashlib.sha256(('life-20261007/'+k).encode()).hexdigest()[:16],16)&0x7FFFFFFFFFFFFFFF
 return f'{value or 1:016X}'
def task(k,typ='checkmark',**kw):return dict(id=uid('task/'+k),type=typ,**kw)
def itemtask(k,item):return task(k,'item',item=item,consume_items=False,title='持有种源或成品（不消耗）')
def action(k):return task(k,'advancement',advancement='tnc:life_routes/'+k,title='完成真实操作')
def chapter(file,title,icon,index):return dict(id=uid(file),filename=file,title=title,icon=icon,group=GROUP,order_index=index,default_hide_dependency_lines=False,default_quest_shape='hexagon',quests=[],images=[],quest_links=[])
def quest(c,k,title,icon,x,y,desc,t=None,deps=(),size=1.2,book=None):
 q=dict(id=uid(k),title=title,icon=icon,x=x,y=y,size=size,description=desc,tasks=[t or task(k)],dependencies=list(deps))
 if book:q['rewards']=[dict(id=uid('reward/'+k),type='item',item='tnc:'+book,count=1)]
 c['quests'].append(q);return q
def adv(key):dump(RES/'data/tnc/advancements/life_routes'/f'{key}.json',{'criteria':{'performed':{'trigger':'minecraft:impossible'}}})
# Resolve item icons using actual item models; never assume a crop block is an item.
models=set(); names={}; recipes=[]
for jar in sorted((LIVE/'mods').glob('*.jar')):
 try:
  with zipfile.ZipFile(jar) as z:
   for name in z.namelist():
    m=re.fullmatch(r'assets/([^/]+)/models/item/([^/]+)\.json',name)
    if m:models.add(':'.join(m.groups()))
    if name.endswith('/lang/zh_cn.json'):
     try:names.update(json.loads(z.read(name)))
     except (ValueError,UnicodeError):pass
    if '/recipes/' in name and name.endswith('.json'):
     try:recipes.append(json.loads(z.read(name)))
     except (ValueError,UnicodeError):pass
 except zipfile.BadZipFile:pass
models.update('tnc:'+p.stem for p in (RES/'assets/tnc/models/item').glob('*.json'))
vanilla={'wheat':'wheat_seeds','carrots':'carrot','potatoes':'potato','beetroots':'beetroot_seeds','melon_stem':'melon_seeds','pumpkin_stem':'pumpkin_seeds','sweet_berry_bush':'sweet_berries','cave_vines':'glow_berries','cocoa':'cocoa_beans','kelp_plant':'kelp','bamboo_sapling':'bamboo','chorus_plant':'chorus_flower'}
overrides={'farmersdelight:cabbages':'farmersdelight:cabbage_seeds','farmersdelight:budding_tomatoes':'farmersdelight:tomato_seeds','farmersdelight:onions':'farmersdelight:onion','farmersdelight:rice':'farmersdelight:rice','kaleidoscope_cookery:tomato_crop':'kaleidoscope_cookery:tomato_seed','kaleidoscope_cookery:lettuce_crop':'kaleidoscope_cookery:lettuce_seed','kaleidoscope_cookery:chili_crop':'kaleidoscope_cookery:chili_seed','kaleidoscope_cookery:rice_crop':'kaleidoscope_cookery:rice_seed'}
def resolve(code):
 aliases={'biomeswevegone:blueberry_bush':'biomeswevegone:blueberries','biomeswevegone:oddion_crop':'biomeswevegone:oddion_bulb','biomeswevegone:apple_fruit':'minecraft:apple','biomeswevegone:green_apple_fruit':'biomeswevegone:green_apple','jerotesvillage:second_round_nigrums':'jerotesvillage:second_round_nigrum','jerotesvillage:bright_melons':'jerotesvillage:bright_melon_seeds','kaleidoscope_end:dream_berry_head':'kaleidoscope_end:dream_berry'}
 if code in aliases:return aliases[code]
 ns,p=code.split(':');candidates=[overrides.get(code,''),f'minecraft:{vanilla.get(p,p)}' if ns=='minecraft' else code]
 stem=p.removesuffix('_crop').removesuffix('_bush').removesuffix('_stem').removesuffix('_leaves')
 candidates += [f'{ns}:{stem}_seeds',f'{ns}:{stem}_seed',f'{ns}:{stem}',f'{ns}:{stem}_sapling']
 return next((c for c in candidates if c and (c in models or c.startswith('minecraft:'))),None)
traditional=[]; seen=set(); unresolved=[]
for line in (R/'docs/传统植物盘点与四季安排-20261006.md').read_text(encoding='utf-8').splitlines():
 if not line.startswith('|') or '`' not in line:continue
 cells=line.split('|');codes=[];ns=''
 for code in re.findall(r'`([^`]+)`',cells[2]):
  if ':' in code:ns=code.split(':')[0]
  elif ns:code=ns+':'+code
  else:continue
  if ' ' not in code:codes.append(code)
 if not codes:continue
 icon=next((resolve(code) for code in codes if resolve(code)),None)
 if not icon:unresolved.append({'name':cells[1].strip(),'blocks':codes});continue
 if icon in seen:continue
 seen.add(icon); seasons=cells[3].strip();mask=15 if '全年' in seasons else sum(1<<i for i,s in enumerate('春夏秋冬') if s in seasons)
 if not mask:mask=15
 traditional.append(dict(name=cells[1].strip(),blocks=codes,item=icon,seasons=seasons,mask=mask,use=cells[4].strip() if len(cells)>5 else '按 JEI 当前配方加工'))
def results_for(item):
 out=[]
 inputs={item}
 ns,key=item.split(':')
 if key.endswith('_seeds'):inputs.add(ns+':'+key[:-6])
 if key.endswith('_seed'):inputs.add(ns+':'+key[:-5])
 if item=='minecraft:wheat_seeds':out.append('minecraft:bread')
 def contains(obj):
  if isinstance(obj,dict):return (isinstance(obj.get('item'),str) and obj.get('item') in inputs) or any(contains(v) for k,v in obj.items() if k not in ('result','results','output'))
  return isinstance(obj,list) and any(contains(v) for v in obj)
 for r in recipes:
  if not isinstance(r,dict) or not contains(r):continue
  output=r.get('result',r.get('output'))
  rid=output if isinstance(output,str) else output.get('item') if isinstance(output,dict) else None
  if rid and rid not in out and re.search(r'bread|stew|soup|salad|pie|tea|juice|cake|cookie|dumpling|pudding|noodle|burger|sandwich|sausage|cooked|skewer|meal|steak|jelly|jam|roll|pasta|tart|candy|omelette|taco|drink|wine|bun|sushi|porridge|hotpot|casserole|potage|fried|congee|smoothie',rid.split(':')[1]):out.append(rid)
 return out[:4]
season=chapter('tnc_field_05_seasons','&a&l四时农圃','tnc:season_handbook',2)
season['images']=[dict(image='tnc:textures/guide/seasons-banner.png',x=0.,y=-8.,width=35.,height=12.38,rotation=0.)]
quest(season,'seasons/start','&a四时 · 把日子种进田里','tnc:season_handbook',0,-16,['每季七个游戏日。应季100%、相邻季60%、反季25%。换季不会毁掉作物。','普通植物保持原种法，任何获准的野外地块都能种。睡觉推进农历，生长累计真实运行时间。','下面每栏是应季品种；多季作物在对应季节也有入口，共用一个完成记录。'],size=2,book='season_handbook')
for i,label in enumerate('春夏秋冬'):
 x=(i-1.5)*9
 quest(season,'season/'+label,'&a&l'+label+' · 农圃','minecraft:'+['oak_sapling','melon','pumpkin','snowball'][i],x,0,['应季正常生长；打开品种节点看种源和厨房用途。'],size=1.8)
season_counts=[0]*5
year_y=12+max(sum(r['mask']!=15 and r['mask']&(1<<g)>0 for r in traditional) for g in range(4))//2*3.4
for i,row in enumerate(traditional):
 groups=[g for g in range(4) if row['mask']&(1<<g)] if row['mask']!=15 else [4]
 positions=[]
 for group in groups:
  n=season_counts[group];season_counts[group]+=1
  x=(group-1.5)*9+(n%2-.5)*3.7 if group<4 else (n%8-3.5)*4.2
  y=4+(n//2)*3.4 if group<4 else year_y+(n//8)*3.4
  positions.append((group,x,y))
 _,x,y=positions[0]
 recipe_names=[names.get('item.'+rid.replace(':','.'),rid) for rid in results_for(row['item'])]
 row['recipes']=recipe_names
 q=quest(season,'crop/'+row['item'],row['name'],row['item'],x,y,['适种：'+row['seasons'],'种源：'+row['item'],'用途：'+row['use'],'原模组配方例：'+('、'.join(recipe_names) if recipe_names else '用 JEI 查看原模组的采收、烹调与加工配方。'),'整合包启用的配方以 JEI 为准。'],itemtask('crop/'+row['item'],row['item']))
 for group,x,y in positions[1:]:season['quest_links'].append(dict(id=uid('season-link/'+row['item']+'/'+str(group)),linked_quest=q['id'],x=x,y=y,size=1.2))
quest(season,'seasons/year','全年种植 · 菌菇、水生与异境','minecraft:red_mushroom',0,year_y-3,['这些植物保持原种法；全年可种并不取消原模组自己的生长机制。'],size=1.8)
for i,row in enumerate(unresolved):quest(season,'decor/'+row['blocks'][0],row['name'],'minecraft:flowering_azalea',i*4,year_y+((season_counts[4]+7)//8)*3.4+4,['野外景观植物：'+', '.join(row['blocks']),'当前资源里未找到可作为独立种源的物品，因此列为探索说明，不要求取得不存在的种子。'])
quest(season,'seasons/plant','种下一株','minecraft:iron_hoe',-9,-13,['亲手放下普通作物，自动记录。'],action('traditional/planted'))
quest(season,'seasons/harvest','第一份熟成','minecraft:wheat',0,-13,['亲手采收成熟 CropBlock 作物，自动记录。'],action('traditional/harvested'))
quest(season,'seasons/cook','厨房开火','minecraft:bread',9,-13,['在工作台制作一份真正的食物。烹锅配方可自由尝试。'],action('traditional/cooked'),book='season_handbook')
# Existing plants: exact registry names, not design shorthand.
legacy=[('homeward_flower','归巢花','homeward_flower_seed','homeward_flower_petal','归途缎带'),('warning_moss','警铃苔','warning_moss_spore','warning_moss_flake','守望铃'),('road_bell_crop','铃穗草','road_bell_seed','road_bell_ear','响铃羊饲料'),('night_gourd_crop','夜露葫','night_gourd_seed','night_gourd','夜露料理'),('tide_reed_crop','听潮苇','tide_reed_seed','tide_reed_stem','纸皮压机'),('hushcap_mushroom','静息菇','hushcap_spore','hushcap_slice','静息蕈汤'),('rainletter_bush','雨信蔓','rainletter_seed','rainletter_berry','雨信莓露'),('mana_root','蓄魔根','mana_root_seed','mana_root_core','炉供魔（须先注入自身魔力）'),('verdant_vein','翠脉枝','verdant_vein_seed','verdant_branch','成熟以0.5魔力/秒供给集露盏'),('sky_vine','天穹藤','sky_vine_seed','sky_canopy_fiber','真实攀爬与巨冠供能')]
magic=[dict(id=a,name=b,seed=c,product=d,use=e,help='人工种植需要魔法土与八格内流动魔力。') for a,b,c,d,e in legacy]
for file in ['botanical/BotanicalSpecies.java','routes/NewPlantKind.java']:
 for a,b,c,d in re.findall(r'\w+\("([a-z_]+)","([^"]+)","([a-z_]+)","([^"]+)"',(J/file).read_text(encoding='utf-8')):
  line=next(l for l in (J/file).read_text(encoding='utf-8').splitlines() if '"'+a+'"' in l)
  quoted=re.findall(r'"([^"]+)"',line)
  magic.append(dict(id=a,name=b,seed=a+'_seed',product=c,productName=d,help=quoted[-1],use='采收、厨房加工或对应工坊器件；按 JEI 查看实际配方。'))
table={}
for line in (R/'docs/魔植与异兽逐品种任务规格-20261006.md').read_text(encoding='utf-8').splitlines():
 if line.startswith('|') and '`' in line:
  cols=line.split('|');m=re.search(r'`([a-z_]+)`',cols[1])
  if m:table[m.group(1)]=[re.sub('`','',c.strip()) for c in cols[2:-1]]
garden=chapter('tnc_field_02_botany','&b&l魔植花园','tnc:magic_garden_book',3)
root=quest(garden,'garden/start','&b&l把野外奇迹带回家','tnc:magic_garden_book',0,-10,['三十八株各有一列。探索与经营可自行选，不锁主线。','人工株种在魔法土/源上，不需要再锄；八格内有流动魔力才继续生长。','果实可直接种；根芽用普通剪刀取；挖根至少返一份种源。不需要采样夹。'],size=2,book='magic_garden_book')
for x,id,title,desc in [(-9,'magic_soil_source','土源 · 十秒铺田','八泥土＋一满100魔力瓶；同层9×9的泥土转为魔法土，返空瓶。不转草地或建筑。'),(0,'flowing_mana_bucket','流动魔力 · 八格灌溉','注能台花400真实魔力装满桶；亦可由四个独立满瓶与一桶合成。不建立无限水源。'),(9,'dew_collector','花园也会供能','集露盏只收周围3×3×3真实供魔植株。成熟、环境窗口和实际饲料决定产能。')]:quest(garden,'garden/'+id,title,'tnc:'+id,x,-5,[desc],itemtask('garden/'+id,'tnc:'+id))
for i,p in enumerate(magic):
 x=(i%6-2.5)*4.5;y=3+(i//6)*14
 details=table.get(p['id'],table.get(p['id'].removesuffix('_crop').removesuffix('_mushroom').removesuffix('_bush'),[]))
 if details:p['discovery']=details[0];p['use']=details[-1].replace('（拟新增）','').replace('拟新增','').replace('拟','')
 a=quest(garden,'magic/'+p['id']+'/seed',p['name'],'tnc:'+p['seed'],x,y,[p.get('discovery','野外对应林地、草坡、河岸或山地寻找自然株。'),'不消耗种源。留一份种回家，采一份用于生活。'],itemtask('magic/'+p['id']+'/seed','tnc:'+p['seed']),size=1.8)
 b=quest(garden,'magic/'+p['id']+'/plant','扎下魔法根','tnc:magic_soil',x,y+2.8,[p['help'],'亲手种下记录；生长不分春夏秋冬。'],action('plant/'+p['id']),[a['id']])
 c=quest(garden,'magic/'+p['id']+'/harvest','亲手收获','tnc:'+p['product'],x,y+5.6,['成熟收获会真实记录。液体产物须拿空瓶；取芽用剪刀。'],action('harvest/'+p['id']),[b['id']])
 quest(garden,'magic/'+p['id']+'/use','生活与工坊','tnc:'+p['product'],x,y+8.4,[p['use'],'本节点为用途说明；打开 JEI 查看全部实际配方。'],deps=[c['id']])
animals=[]
pattern=r's\("([a-z_]+)","([^"]+)",Role\.(\w+),Habitat\.(\w+),Items\.(\w+),\d+,\d+,\d+,"([a-z_]+)"'
for a,b,role,habitat,food,product in re.findall(pattern,(J/'pasture/PastureSpecies.java').read_text(encoding='utf-8')):animals.append(dict(id=a,name=b,role=role,habitat=habitat,food='minecraft:'+food.lower(),product=product))
animals.insert(0,dict(id='bellwool_sheep',name='响铃羊',role='LIVE',habitat='LAND',food='tnc:bellwool_fodder',product='resonant_fleece'))
beasts=chapter('tnc_field_03_pasture','&6&l异兽牧场','tnc:beast_ranch_book',4)
quest(beasts,'beasts/start','&6&l与二十六种异兽相处','tnc:beast_ranch_book',0,-9,['喂一次喜食取得信任→长按笼捕获→安置→继续照料→牧务杖设窝→食槽喂食。','灵纹笼3秒、曜纹笼5秒、星髓笼8秒。高档兼容低档；III级先用安抚铃。','地面9×9，飞行9×9×9；只有玩家明确安排的运输/巡逻会离窝。饥饿停工，不会饿死。'],size=2,book='beast_ranch_book')
for x,id,title in [(-9,'pasture_cage','灵纹笼 · I'),(0,'radiant_cage','曜纹笼 · II'),(9,'star_cage','星髓笼 · III')]:quest(beasts,'beasts/'+id,title,'tnc:'+id,x,-4,['材料在3×3×3魔力炉处理。笼每次装一只，释放后可复用，不复制个体。'],itemtask('beasts/'+id,'tnc:'+id))
special=['dewbound_whale','papersail_ray','wirecall_lizard']; ordered=[p for p in animals if p['id'] not in special]+[next(p for p in animals if p['id']==s) for s in special]
for i,p in enumerate(ordered):
 is_special=p['id'] in special; x=(i%6-2.5)*4.5 if not is_special else (list(special).index(p['id'])-1)*9
 y=3+(i//6)*14 if not is_special else 3+4*14
 details=table.get(p['id'],[]);desc=details[1:] if details else [p['habitat'],p['food']]
 t=action('capture/'+p['id']);t['ftbquestsentityvis_icon']=dict(enabled=True,entity='tnc:'+p['id'],size=1.,offset_x=0.,offset_y=0.,rotation=25.,spin_mode=False,idle_mode=True,walk_mode=False,silhouette_mode=False,use_as_quest_icon=True,nbt='{}')
 a=quest(beasts,'beast/'+p['id']+'/capture',p['name'],'tnc:'+('mana_bottle' if p['product']=='stored_mana' else p['product']),x,y,['长按捕获自动记录。']+desc,t,size=2 if is_special else 1.8)
 b=quest(beasts,'beast/'+p['id']+'/nest','安置与照料','tnc:pasture_staff',x,y+2.8,['喜食：'+p['food'],'牧务杖选兽→点自己的栖居标记。窝点须有可达食槽与相应浅水/遮棚/净空。'],action('nest/'+p['id']),[a['id']])
 if p['product']=='stored_mana':key='harvest/'+p['id'];title='真实取魔';icon='tnc:mana_bottle'
 else:key='harvest/'+p['id'];title='第一份产物';icon='tnc:'+p['product']
 c=quest(beasts,'beast/'+p['id']+'/harvest',title,icon,x,y+5.6,['活取、真正的掉落或瓶装取魔才记录；肉用种类可正常屠宰。'],action(key),[b['id']])
 quest(beasts,'beast/'+p['id']+'/use','伙伴的用途',icon,x,y+8.4,[details[-1] if details else '产物用于器件、料理与经营。','特殊储魔兽：集息器空手潜行右键切取魔/充能模式；同一余额不可复制。'],deps=[c['id']])
workshop=chapter('tnc_field_04_workshop','&3&l魔导工坊','tnc:mana_workshop_book',5)
quest(workshop,'workshop/start','&3&l让庭院的魔力流动','tnc:mana_workshop_book',0,-9,['植物/动物→收集器→线路→储池→工作装置。全程纯魔力、真实余额。','土中的流动魔力仅灌溉，不再回抽为能源。区块未加载就暂停；不补算离线产能。'],size=2,book='mana_workshop_book')
nodes=re.findall(r'\w+\("([a-z_]+)","([^"]+)",(\d+),(\d+)\)',(J/'routes/RouteKind.java').read_text(encoding='utf-8'))
helps={'mana_infuser':'空手按住右键，每秒注入自身5魔力，松开停止；潜行右键开界面放取第一个槽的容器，界面按钮也可注入五秒。普通瓶100，精炼瓶400，桶400。接线可自动装。','breath_collector':'牧务杖选兽后右键绑定。空手潜行右键切取魔/充能；鲸32、蜥8、貂25魔力/秒各有上限。','mana_pulp_press':'两听潮苇或甘蔗＋相邻水，16魔力、4秒制三纸。','mana_sawmill':'一原木＋12魔力，4秒制六木板。','mist_loom':'两雾棉或帆纤维＋30魔力，6秒制雾布。','meal_dryer':'一份熟食＋纸＋24魔力，6秒制远行餐。','mana_incubator':'放自己实际受精卵；0.15魔力/秒加快25%，不会生成新卵。','accessory_charger':'放充能饰品或机甲，接线每秒最多16魔力充入真实账本。','mana_cart_track':'这是沿普通铁轨铺设的魔导驱动座，真实载货矿车每10格花1魔力。','mana_valve':'潜行右键切优先。红石信号可关闭；消费者先获得魔力，余量进储池。','mana_mirror':'两镜沿六轴对齐，12格无遮挡传能；共享4魔力/秒。','star_mirror':'沿六轴对齐，24格无遮挡，16魔力/秒。','color_target':'拿染料右键改色。对应角羚12格内无遮挡发射，不伤人、不破坏地形。'}
for i,(id,name,cap,rate) in enumerate(nodes):quest(workshop,'node/'+id,name,'tnc:'+id,(i%6-2.5)*4.5,2+(i//6)*5.7,[helps.get(id,'放在获准区域并连接相邻线路；检流尺右键看真实工作状态。'),f'缓冲{cap}；带宽{rate}魔力/秒。'],itemtask('node/'+id,'tnc:'+id),size=1.5)
for i,(id,label,key) in enumerate([('dew_collector','看见第一次真实供能','network/collected'),('mana_basin','存下真实魔力','network/stored'),('mana_sawmill','实际完成一次加工','network/worked'),('accessory_charger','真正充满一部分饰品','network/charged')]):quest(workshop,'network/'+key,label,'tnc:'+id,(i-1.5)*7,34,['这个节点检测实际转移或加工，而非持有机器。'],action(key))
for i,(id,name,cap) in enumerate(re.findall(r'\w+\("([a-z_]+)","([^"]+)",(\d+)\)',(J/'routes/RouteAccessory.java').read_text(encoding='utf-8'))):quest(workshop,'accessory/'+id,name,'tnc:'+id,(i%4-1.5)*6,41+(i//4)*6,[('由穿戴的魔道机甲供能；模块不单独充能。' if id=='mech_spellgun' else f'容量{cap}。先在饰品充能台充能，再使用。'),'副手饰品生效；机甲穿胸甲位。用途与消耗见物品提示。'],itemtask('accessory/'+id,'tnc:'+id),size=1.5)
from install_life_routes import groups as append_life_group
existing_group=(Q/'chapter_groups.snbt').read_text(encoding='utf-8-sig') if (Q/'chapter_groups.snbt').exists() else '{chapter_groups: []}'
(Q/'chapter_groups.snbt').parent.mkdir(parents=True,exist_ok=True)
(Q/'chapter_groups.snbt').write_text(append_life_group(existing_group),encoding='utf-8')
for c in [season,garden,beasts,workshop]:
 dump(Q/'chapters'/f"{c['filename']}.snbt",c)
 dump(R/'questbook/ftbquests/chapters'/f"{c['filename']}.snbt",c)
dump(RES/'data/tnc/life_routes/catalogue.json',dict(traditional=traditional,magic=magic,animals=animals,machines=[dict(id=i,name=n,help=helps.get(i,'接线供魔后工作；材料放输入位，成品从输出位取。')) for i,n,_,_ in nodes]))
dump(R/'work/life-routes-unresolved-icons.json',unresolved)
print(f'Four chapters: {sum(len(c["quests"]) for c in [season,garden,beasts,workshop])} quests; {len(traditional)} traditional entries; {len(magic)} magic plants; {len(animals)} beasts. Unresolved traditional icons: {len(unresolved)}')
