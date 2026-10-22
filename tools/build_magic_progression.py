"""Authoritative 2026-10-22 blueprint assets. Only TN-C authored chapters are edited."""
from pathlib import Path
import json, hashlib, math
R=Path(__file__).resolve().parents[1]; A=R/'src/main/resources/assets/tnc'; D=R/'src/main/resources/data/tnc'
DEVICES={'mana_extractor':'魔力剥离台','elemental_igniter':'元素引火桩','leyline_compass':'地脉寻迹仪','beast_resonance_pedestal':'灵兽共鸣台','elixir_fumigator':'药雾熏蒸釜','elemental_phase_shifter':'元素折射转相仪'}
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
def box(a,b,t,rot=None):
 e={'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ['up','down','north','south','east','west']}}
 if rot:e['rotation']=rot
 return e
def models():
 tex={'stone':'tnc:block/alchemy_ceramic','gold':'tnc:block/alchemy_bronze','wood':'tnc:block/alchemy_wood','glow':'tnc:block/alchemy_glow','glass':'tnc:block/energy_glass','particle':'tnc:block/alchemy_ceramic'}
 for name in DEVICES:
  e=[]
  if name=='mana_extractor':
   e=[box([2,0,3],[14,2,13],'stone'),box([3,2,4],[13,3,12],'gold'),box([6,4,6],[10,11,10],'glow',{'origin':[8,7,8],'axis':'y','angle':45})]
   for x in [2,12]:e += [box([x,2,5],[x+2,12,11],'wood'),box([x,10,5],[x+2,13,11],'gold')]
   for y in [5,7,9]:e += [box([4,y,7.6],[12,y+.35,8.4],'glow')]
   e += [box([5,2,1],[11,4,4],'stone'),box([6,4,1.5],[10,4.4,3.5],'glow')]
  elif name=='elemental_igniter':
   e=[box([4,0,4],[12,2,12],'stone'),box([6,2,6],[10,10,10],'gold')]
   for x in [3,10]:e += [box([x,6,6],[x+3,12,10],'stone'),box([x+.5,10,6.5],[x+2.5,15,9.5],'glow'),box([x,13,6],[x+3,14,10],'gold')]
   e += [box([7,11,7],[9,13,9],'glass')]
  elif name=='leyline_compass':
   for x,z in [(3,3),(11,3),(7,11)]:e += [box([x,0,z],[x+2,7,z+2],'wood')]
   e += [box([2,7,2],[14,9,14],'gold'),box([3,9,3],[13,9.5,13],'stone'),box([7,9.5,7],[9,13,9],'glow',{'origin':[8,11,8],'axis':'y','angle':45})]
   for x,z,dx,dz in [(1,7,14,2),(7,1,2,14)]:e += [box([x,10,z],[x+dx,10.5,z+dz],'gold')]
  elif name=='beast_resonance_pedestal':
   e=[box([3,0,3],[13,2,13],'stone'),box([6,2,6],[10,6,10],'wood'),box([3,6,3],[13,7,13],'gold'),box([4,7,4],[12,8,12],'glass'),box([6,8,6],[10,9,10],'glow')]
   for x in [1,12]:e += [box([x,6,5],[x+3,10,11],'gold'),box([x+.5,10,6],[x+2.5,14,10],'stone'),box([x+1,14,7],[x+2,16,9],'glow')]
  elif name=='elixir_fumigator':
   e=[box([2,0,2],[14,2,14],'stone'),box([4,2,4],[12,4,12],'gold'),box([3,4,3],[13,5,13],'stone'),box([5,5,5],[11,5.5,11],'glow')]
   for x in [2,12]:
    for z in [2,12]:e += [box([x,2,z],[x+2,10,z+2],'wood')]
   for a,b in [([2,10,2],[14,11,4]),([2,10,12],[14,11,14]),([2,10,4],[4,11,12]),([12,10,4],[14,11,12])]:e += [box(a,b,'gold')]
   e += [box([6,7,6],[10,8,10],'glass'),box([7,8,7],[9,10,9],'glow')]
  else:
   e=[box([2,0,2],[14,2,14],'stone'),box([3,2,3],[13,3,13],'gold'),box([6,5,6],[10,12,10],'glass',{'origin':[8,8,8],'axis':'y','angle':45}),box([7,6,7],[9,11,9],'glow')]
   for i in range(4):
    a=i*math.pi/2;x=8+math.cos(a)*5;z=8+math.sin(a)*5
    e += [box([x-1,3,z-1],[x+1,10,z+1],'gold'),box([x-1.3,9,z-1.3],[x+1.3,12,z+1.3],'glow')]
   e += [box([4,12,7.5],[12,12.5,8.5],'gold'),box([7.5,12,4],[8.5,12.5,12],'gold')]
  write(A/f'models/block/{name}.json',{'textures':tex,'elements':e,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]}}})
  write(A/f'models/item/{name}.json',{'parent':f'tnc:block/{name}'})
  write(A/f'blockstates/{name}.json',{'variants':{'':{'model':f'tnc:block/{name}'}}})
  write(D/f'loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'tnc:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 for lang in ['zh_cn','en_us']:
  p=A/f'lang/{lang}.json';j=json.loads(p.read_text('utf8'))
  for id,title in DEVICES.items():j['block.tnc.'+id]=title if lang=='zh_cn' else id.replace('_',' ').title()
  j['item.tnc.pure_medicinal_powder']='纯净药粉' if lang=='zh_cn' else 'Purified Medicinal Powder'
  j['item.tnc.botanical_mana_filament']='草木魔力丝' if lang=='zh_cn' else 'Botanical Mana Filament'
  write(p,j)
 for id in ['pure_medicinal_powder','botanical_mana_filament']:
  write(A/f'models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/{id}'}})
def icons():
 from PIL import Image,ImageDraw
 # Original pixel silhouettes extending the approved outlined, warm metal/teal herb palette.
 for id in ['pure_medicinal_powder','botanical_mana_filament']:
  im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);edge='#302921'
  if id=='pure_medicinal_powder':
   d.polygon([(4,21),(8,15),(24,14),(29,21),(24,28),(8,28)],fill=edge)
   d.polygon([(6,21),(10,17),(23,16),(27,21),(23,26),(9,26)],fill='#b99a63')
   d.polygon([(6,21),(16,24),(27,21),(23,27),(9,27)],fill='#7b613e')
   d.polygon([(8,20),(12,15),(17,14),(24,18),(25,21),(17,24)],fill='#477f78')
   d.polygon([(9,19),(13,14),(18,14),(23,18),(22,21),(16,22)],fill='#88c5ab')
   for x,y in [(13,16),(17,18),(20,17),(14,20),(21,20),(16,15)]:d.rectangle([x,y,x+1,y],fill='#d1ecc6')
   d.polygon([(9,6),(11,6),(11,10),(14,10),(14,12),(11,12),(11,15),(9,15),(9,12),(6,12),(6,10),(9,10)],fill='#4b948a')
   d.rectangle([10,8,10,13],fill='#d8f8de');d.rectangle([8,11,12,11],fill='#d8f8de');d.point((22,9),fill='#c2e8d2');d.point((25,12),fill='#7bbfa7')
  else:
   d.polygon([(10,4),(21,3),(25,6),(24,9),(22,10),(22,23),(25,24),(24,28),(12,29),(8,26),(9,23),(11,22),(11,10),(8,8)],fill=edge)
   d.polygon([(11,5),(21,4),(23,6),(21,8),(11,9),(10,7)],fill='#b68e4d');d.line([(12,5),(20,5)],fill='#e7c47e',width=1)
   d.polygon([(11,23),(22,22),(24,25),(22,27),(12,28),(10,26)],fill='#a77b42');d.line([(12,24),(21,24)],fill='#dfb86c')
   d.rectangle([12,9,21,23],fill='#254b51')
   for y in range(10,23,3):
    d.line([(11,y+2),(22,y)],fill='#317b78',width=2);d.line([(12,y+1),(21,y-1)],fill='#6bc2aa',width=1)
   d.line([(14,9),(15,13),(18,15),(17,19),(20,23)],fill='#d2eed3',width=1)
   d.line([(21,10),(26,12),(26,16),(23,18),(24,21)],fill='#72c8b7',width=1);d.point((27,18),fill='#d8f9dc')
  im.save(A/f'textures/item/{id}.png')
def crafting(id,items,count=1):
 write(D/f'recipes/{id}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':k} for k,v in items for _ in range(v)],'result':{'item':'tnc:'+id,'count':count}})
def forge(id,items,cost=160):
 write(D/f'recipes/arcana/{id}.json',{'type':'tnc:mana_forging','ingredients':[{'item':k,'count':v} for k,v in items],'result':{'item':'tnc:'+id},'mana':cost,'work_ticks':120,'category':'workshop'})
def recipes():
 crafting('mana_extractor',[('tnc:spirit_iron',3),('minecraft:amethyst_shard',1),('minecraft:stone_bricks',1)])
 crafting('elemental_igniter',[('tnc:spirit_iron',2),('minecraft:copper_ingot',2),('tnc:forge_core',1)])
 crafting('leyline_compass',[('tnc:spirit_iron',3),('minecraft:compass',1),('minecraft:amethyst_shard',2)])
 forge('beast_resonance_pedestal',[('tnc:radiant_gold',2),('tnc:spirit_iron',4),('tnc:dream_humus',1)])
 forge('elixir_fumigator',[('tnc:radiant_gold',4),('minecraft:copper_ingot',4),('tnc:foldleaf_film',2)])
 forge('elemental_phase_shifter',[('tnc:star_marrow',2),('tnc:star_mirror',1),('tnc:prism_crown_petal',2)])
 forge('spirit_nurture_altar',[('tnc:star_marrow',4),('tnc:accessory_charger',1),('minecraft:crying_obsidian',2),('tnc:pure_medicinal_powder',2)])
 # Terminal weapons consume materials obtained during the corresponding realm expedition.
 for name,ingredients,cost,ticks in [
  ('mana_mech',[('tnc:star_marrow',8),('minecraft:netherite_ingot',2),('minecraft:blaze_rod',4),('tnc:mist_cloth',8)],1600,800),
  ('mech_spellgun',[('tnc:star_marrow',4),('minecraft:netherite_ingot',1),('minecraft:blaze_rod',4),('tnc:spirit_iron',8)],1200,640),
  ('zero_spellgun',[('tnc:star_marrow',8),('minecraft:netherite_ingot',2),('minecraft:dragon_breath',1),('minecraft:chorus_fruit',8)],1800,800)]:
  write(D/f'recipes/{name}.json',{'type':'tnc:mana_forging','ingredients':[{'item':i,'count':n} for i,n in ingredients],'result':{'item':'tnc:'+name},'mana':cost,'work_ticks':ticks,'category':'workshop'})
 # A balanced set of explicit food tags, extendable by datapacks.
 tastes={
  'fire':['tnc:hearth_pepper_fruit','minecraft:blaze_powder','minecraft:cooked_beef','minecraft:cooked_porkchop','minecraft:cooked_chicken','minecraft:cooked_mutton','minecraft:cooked_rabbit'],
  'water':['tnc:rainletter_berry','tnc:dewgrass_thread','minecraft:kelp','minecraft:dried_kelp','minecraft:cod','minecraft:salmon','minecraft:cooked_cod','minecraft:cooked_salmon'],
  'wind':['tnc:dance_petal','tnc:sail_fiber','tnc:page_fern_fiber','minecraft:sugar','minecraft:feather'],
  'earth':['tnc:stone_pattern_leaf','tnc:mana_root_core','tnc:ore_dream_flake','minecraft:potato','minecraft:baked_potato','minecraft:brown_mushroom','minecraft:red_mushroom'],
  'neutral':['tnc:homeward_flower_petal','tnc:star_dew_fruit','tnc:steam_lotus_heart','tnc:dawn_honey','minecraft:honey_bottle','minecraft:golden_apple']}
 for taste,values in tastes.items():write(D/f'tags/items/meal/{taste}.json',{'replace':False,'values':values})
def uid(text):return f'{int(hashlib.sha256(("tnc-blueprint-20261022:"+text).encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}'
def chapters():
 # New independent chapter; all existing IDs and authored pages remain intact.
 quests=[];last=None
 def q(key,title,item,lines,stage,col=0,deps=None,check=False):
  nonlocal last
  id=uid(key);depend=deps if deps is not None else ([last] if last else [])
  task={'id':uid(key+'task'),'type':'checkmark'} if check else {'id':uid(key+'task'),'type':'item','item':'tnc:'+item if ':' not in item else item,'count':1,'consume_items':False}
  quests.append({'id':id,'title':title,'icon':'tnc:'+item if ':' not in item else item,'x':col*3,'y':len(quests)*2,'description':lines,'dependencies':depend,'hide_dependency_lines':len(depend)>1,'tasks':[task],'rewards':[]});last=id;return id
 q('welcome','&b&lT0 · 从自身到天地','mana_infuser',['本章按总纲分T0—T4。M是储量，M/s是产能或带宽；同株、同兽共享真实余额。','F8观察供魔、药锅、餐盘和异界绿洲。亲手种植与喂养为主，自动农具仅可选。'],0,check=True)
 q('bottles','&b先装满第一瓶','mana_bottle',['注能台500M / 8M/s。空手保持右键每秒注5M，潜行右键开面板。','左一格放玻璃瓶，100M成满瓶；铁桶需400M，得到可流动的魔力。'],0)
 q('soil','&b九乘九的第一片魔圃','magic_soil_source',['八泥土与一满魔力瓶合成土源；放下十秒转化同高9×9泥土。','魔植直接种在土源或魔法土，无需锄地。八格内放流动魔力，观察湿润蓝绿微光。'],0)
 q('firstforge','&6T1 · 手注首炉，打破启动循环','magic_forge',['搭3×3×3：底层9砖，中层7砖＋炉口＋炉核，顶层8砖＋排烟顶。','第一炉无需集露网络！铁锭入炉，玩家手动注入60M，6秒产灵纹铁；再制造采集器和晶纤。'],1)
 q('network','&b第一条守恒网络','mana_thread',['成熟产魔植物旁一格放集露盏（100M/4M/s），晶纤8M/s，浅魔池1000M/8M/s。','晶纤可转弯升降，普通双端。整条路径取最窄带宽；检流尺看缺魔或断路。','守望铃与魔导灯验证第一条网络；基础池永不过载。'],1)
 root=last
 q('extractor','&a提纯，不只是堆材料','mana_extractor',['200M/8M/s。GUI左一放原料，右侧取出；一份15M，需3秒。','根类产蓄魔根芯，叶花果产纯净药粉，纤维产草木魔力丝；巨型魔果切八份原产物。'],1,-1)
 q('igniter','&c控温：火与水的两只手','elemental_igniter',['200M/8M/s，贴四方药锅放置。空手左拍升60温，右拍降60温；每次扣5M，间隔半秒。','这是魔法火流；打火石、普通火源均不能点锅。可以继续使用自己的火、水魔法。'],1,-1)
 q('kettle','&d看漩涡，决定封盖的一刻','spellflame_cauldron',['四格药锅倒水，投三味药；F8蓝冷、绿360—620、红热。','自动漩涡融合；100%闪星1.5秒内封盖为大师，淤积0。标准10，次品25。','四格封魔盖锁定品质，保持绿温续煮6秒，玻璃瓶装取。全部药方详见魔药章节。'],1,-1)
 q('compass','&6沿地脉去发现','leyline_compass',['400M/8M/s。右键耗200M，F8金色指引30秒，范围500格。','只指向已生成且尚未探索的真实稀有魔植或地下城入口；附近没有目标不扣魔力。'],1,1,[root])
 q('gold','&6T2 · 曜纹生态','radiant_gold',['金锭＋180M炼曜纹金；霞金晶纤32M/s、曜纹储池8000M。','捕获原创异兽、牧务杖设9×9窝，亲手喂食；与对应成熟魔植近六格，双方产能＋20%。'],2,0,[uid('kettle'),uid('compass')])
 q('beast','&a让照料结成馈赠','beast_resonance_pedestal',['400M/16M/s。置于集息器或色靶旁，亲喂后实际抽取异兽魔力才可凝宝。','镜瓣夜蛾凝镜鳞、曳彩角羚凝角片、星绒兽凝柔茸、鸣线蜥凝雷余晶。','只收同主人真实喂养波长，每次照料最多一份；输出格留空。'],2,-1)
 q('mist','&d用药雾点化万物','elixir_fumigator',['1000M/32M/s，左一放魔药，附近3×3熏蒸。每次40M、10秒，一药一目标，返空瓶。','扭曲药雾80%把水生魔植变蒸气魔莲；巨人药使魔植巨果化，分裂药洗礼幼兽精英。','坚肤药预处理金锭或钻石；这种实物矿坯入锻炉时该炉配方耗魔减半。'],2,-1)
 q('meals','&e五行药膳 · 日常自由配膳','carved_mana_platter',['只需2—9格，无需每次摆满！F8看食材红火、蓝水、青风、黄土、暖金甘味。','上下左右相邻：风火加冷却、火土加甲、土甘加血、甘水排毒回蓝、水风轻身水行。','水火、风土直接邻接会反胃禁魔。甘味居中可桥接，获得10分钟百味调和。','九格齐五类、零冲突、至少六生和线：3分钟微型太阳，法杖零耗，邻近自家机器供魔＋25%加速。'],2,1,[uid('gold')])
 q('spa','&b有冒险，也要回家泡澡','geothermal_mana_core',['接魔力、投舒缓浴盐，五格内源水成为温泉。每秒1M排2淤积。','排空获1世界日经脉通畅，自然回蓝＋50%，魔力视不涨精神压力。'],2,1)
 q('star','&bT3 · 向星空借力','star_marrow',['钻石＋600M炼星髓晶；虹星晶纤128M/s，星髓储池32000M。','日镜露天高于海面16格，白天24M/s；月盘夜间16M/s乘月相0.2—3。观星台预报极光。'],3,0,[uid('mist'),uid('meals')])
 q('phase','&d天体魔力，折射为七系','elemental_phase_shifter',['4000M/256M/s，只把太阳／太阴魔力1:1转相，信物不消耗，任意更换。','暖椒火；雨书莓水；雷冠尖雷；舞瓣风；岩纹叶土；影时瓣暗；曦蜜或荧石光。','用晶纤送出有颜色的实际元素魔力；普通未分类魔力不能凭空转化。'],3,-1)
 q('nurture','&6王杖，只接受同源滋养','spirit_nurture_altar',['右键插王级及以上法杖。只收对应元素，最高256M/s，累计10000M。','满进度后持下界之星、龙息或真实Boss突破核右键，耗一颗觉醒元素神技。','异属性拒入并冒黑烟；不投喂魔药。潜行空手可取回法杖，温养留在实物上。'],3,-1)
 q('industry','&3怪物工坊与天路','ecology_restraint_cage',['缩水药抓回残血非Boss怪，拘束笼必须持续供魔。史莱姆踏板、火怪挑衅吸魔、暗影折光各循习性。','标记杖先点发射环再逐点中继，每段≤20格，≤16段，8M/s/段；持续柔和托送人和气泡包裹。','饰品台128M/s为奇物和机铠补充真实储量，机炮由机铠供能。'],3,1,[uid('star')])
 q('contract','&5T4 · 高维之门','forbidden_scroll',['F8持续三分钟后精神压力积累，超过80%可遇高维魔灵。','用生命、经验、武器耐久或完美共鸣酊交换古卷／高维棱镜。读古卷解锁禁忌工程。'],4,0,[uid('nurture'),uid('industry')])
 q('tower','&b百万魔力，不只为了过载','abyss_mana_tower',['24聚能砖绕核心三层，中心上方两格留空；100万M/4096M/s，能直接收附近自有产魔生态。','四正途：100M/s维持跨维绿洲；30万M经观星台强开极光；64格王杖代付＋50%威力；邻充能台闪充机铠。','输入≤320M/s稳定。过载狩猎为可选旁支，基础魔力池不受影响。'],4)
 q('oasis','&3把魔力带进异界','dimensional_mana_prism',['先完成整合包原有维度解锁：邪恶之眼开下界，不朽护符开末地。','下界与末地默认无自然魔力：无法自然回蓝、种魔植或采集。药剂与随身余额仍可用。','主世界塔旁放高维棱镜，用检流尺潜行选中；带另一面到下界或末地再点，跨维互绑。','塔真实每秒扣100M维持，目标48格成为魔力绿洲，F8看球形软光边界。维护断供则绿洲消退。'],4)
 q('weapons','&6业火重工与星界武装','zero_spellgun',['下界绿洲锻炉：下界合金、星髓与烈焰材料，锻天穹机铠、六联晶铳。','末地绿洲，或主世界露天极光并送入星界魔力：锻零式、星界杖心、极光羽翼。','先建分基地和跨维供魔，再开启终局武装；这些设备不取代种植、牧养与炼药。'],4)
 q('optional','&c可选：深红崩坏的猎手','singularity_mana_core',['高流量推过载；橙区锻炉＋50%，泄压阀耗5000M降20点并排毒。','100%非破坏冲击并召虚空巨兽，优先吸食机器魔力；击杀掉坍缩奇点。','奇点核心上限精确20亿M；没有隐藏无限账本。'],4,1,[uid('tower')])
 write(R/'questbook/ftbquests/chapters/tnc_magic_progression.snbt',{'id':uid('chapter'),'filename':'tnc_magic_progression','title':'&b&l魔导纪行 · T0—T4','icon':'tnc:elemental_phase_shifter','group':'544E434C49464531','order_index':12,'default_hide_dependency_lines':False,'default_quest_shape':'circle','images':[],'quest_links':[],'quests':quests})
def reconcile():
 for name in ['tnc_alchemy','tnc_deep_arcana']:
  p=R/f'questbook/ftbquests/chapters/{name}.snbt';j=json.loads(p.read_text('utf8'))
  for q in j['quests']:
   q['description']=[v.replace('25/10/2淤积','25/10/0淤积').replace('应激+2','零淤积').replace('15分钟','3分钟').replace('十五分钟','三分钟') for v in q.get('description',[])]
   if '元素共鸣酊' in q['title']:q['description']=[v.replace('紫水晶碎片','纯净药粉') for v in q['description']]
   if '温养' in q['title']:q['description']=['王级以上法杖插入温养祭坛，只接受同属性元素魔力，最高256M/s。','太阳或太阴经元素转相仪放信物1:1转七系；不能直接用月光或魔药喂杖。','实际累计10000M后，手持下界之星、龙息或地下城突破核右键觉醒；法杖保留自己的温养进度。']
   if '餐盘' in q['title']:q['description']=['可放2—9格魔植产物或指定原版食材，F8观察红火蓝水青风黄土暖金甘味。','上下左右相邻按五行相生获得五种不同日常增益；水火、风土直接相邻会反胃禁魔15秒。','甘味居中桥接相克两端，赋予10分钟百味调和（魔药冷却、毒性减半）。','九格齐五类、零冲突、至少6条生和线：3分钟法杖无限火力，附近机器供魔与25%加速。']
   if '棱镜' in q['title']:q['description']=['高维棱镜可跨主世界和下界／末地配对；检流尺潜行依次选中两端。','主世界端必须在成型自有深渊塔4格内；每秒真实扣100M维持，实际送入最多4096M/s。','通电目标48格撑起魔力绿洲，恢复自然回蓝与魔植种养；断供后失效。']
  write(p,j)
 p=R/'questbook/ftbquests/chapters/tnc_field_04_workshop.snbt';j=json.loads(p.read_text('utf8'));j['title']='&7可选工坊 · 工具备忘';j['order_index']=90;j['default_hide_dependency_lines']=True
 for q in j['quests']:
  q['hide_dependency_lines']=True
  if not q.get('description',[]) or not q['description'][0].startswith('可选扩展'):q['description']=['可选扩展：阶段主线请看《魔导纪行·T0—T4》，这些自动农具不作为主线门槛。']+q.get('description',[])
 write(p,j)
 p=D/'life_routes/catalogue.json';j=json.loads(p.read_text('utf8'));j['machines']=[m for m in j['machines'] if m.get('id') not in DEVICES]
 for id,title in DEVICES.items():
  details={'mana_extractor':'200M/8M每秒；15M、3秒提纯。根芯供续魔，药粉供共鸣酊，草木丝织布。','elemental_igniter':'200M/8M每秒；贴药锅，空手左拍火+60、右拍水-60，5M一次。','leyline_compass':'400M/8M每秒；耗200M，F8指引500格内真实未探索地点30秒。','beast_resonance_pedestal':'400M/16M每秒；贴集息器，亲喂后真实产魔才凝宝，一次照料一份。','elixir_fumigator':'1000M/32M每秒；一份药40M、10秒，点化3×3。左一药、左二矿坯；返空瓶。','elemental_phase_shifter':'4000M/256M每秒；左一信物不耗，太阳/太阴1:1转七系，只收真实天体余额。'}
  j['machines'].append({'id':id,'name':title,'help':details[id]})
 write(p,j)
 for lang in ['zh_cn','en_us']:
  p=A/f'lang/{lang}.json';j=json.loads(p.read_text('utf8'))
  for id,title in zip(['meal_blazing','meal_rooted','meal_vitality','meal_clarity','meal_gale','meal_harmony','meal_no_magic'],['炙炎膳','固本膳','生机膳','清心膳','轻身膳','百味调和','炸膛禁魔']):j['effect.tnc.'+id]=title if lang=='zh_cn' else id.replace('_',' ').title()
  write(p,j)
def run():icons();models();recipes();chapters();reconcile()
if __name__=='__main__':run()
