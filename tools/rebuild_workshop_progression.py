"""One shared starter path, followed by useful, optional engineering branches.
Edits only the current workshop page, retaining its chapter ID and existing device IDs.
"""
from pathlib import Path
import json,hashlib,collections,re

ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
FILE='tnc_field_04_workshop.snbt'
def uid(key):return f'{int(hashlib.sha256(("life-20261007/"+key).encode()).hexdigest()[:16],16)&0x7FFFFFFFFFFFFFFF:016X}'
def dump(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def build():
 lang=json.loads((RES/'assets/tnc/lang/zh_cn.json').read_text(encoding='utf-8-sig'))
 def name(id):
  ns,path=id.split(':');return lang.get('item.'+ns+'.'+path,lang.get('block.'+ns+'.'+path,{'minecraft:stone_bricks':'石砖','minecraft:logs':'任意原木','minecraft:string':'线','minecraft:iron_axe':'铁斧','minecraft:glass':'玻璃','minecraft:stone':'石头','minecraft:clay_ball':'黏土球','minecraft:copper_ingot':'铜锭','minecraft:amethyst_shard':'紫水晶碎片'}.get(id,id)))
 def recipe(id):
  file=RES/'data/tnc/recipes'/(id+'.json')
  if not file.exists():return '材料与配方请按JEI实际显示准备。'
  r=json.loads(file.read_text(encoding='utf-8-sig'));counts=collections.Counter()
  if 'pattern' in r:
   for key,count in collections.Counter(''.join(r['pattern']).replace(' ','')).items():
    ing=r['key'][key];counts[ing.get('item',ing.get('tag',''))]+=count
  else:
   for ing in r.get('ingredients',[]):
    if isinstance(ing,dict):counts[ing.get('item',ing.get('tag',''))]+=1
  return '制作材料：'+'；'.join(name(k)+'×'+str(v) for k,v in counts.items())+'。' if counts else '按JEI实际配方制作。'
 chapter=json.loads((ROOT/'questbook/ftbquests/chapters'/FILE).read_text(encoding='utf-8-sig'))
 chapter.update(title='&3&l魔导工坊 · 从第一炉到庭院供能',quests=[],quest_links=[],images=[],default_hide_dependency_lines=False)
 quests=chapter['quests'];keys={}
 def add(key,title,icon,x,y,description,deps=(),item=None,adv=None,count=1,size=1.4):
  ident=uid(key);keys[key]=ident
  task={'id':uid('task/'+key),'type':'checkmark'}
  if item:task.update(type='item',item=item,count=count,consume_items=False)
  if adv:
   task.update(type='advancement',advancement='tnc:life_routes/'+adv)
   path=RES/'data/tnc/advancements/life_routes'/(adv+'.json')
   if not path.exists():dump(path,{'criteria':{'performed':{'trigger':'minecraft:impossible'}}})
  q={'id':ident,'title':title,'icon':icon,'x':x,'y':y,'size':size,'description':description,'tasks':[task],'dependencies':[keys.get(d,d) for d in deps]}
  q['hide_dependency_lines']=key.startswith(('node/','accessory/','workshop/source/','workshop/animal/')) and abs(x)>12 and y>75
  quests.append(q);return key
 def device(id,x,y,deps,details):
  label=name('tnc:'+id)
  return add('node/'+id,label,'tnc:'+id,x,y,[recipe(id)]+details,deps,item='tnc:'+id)
 start=add('workshop/start','&3&l从第一炉开始','tnc:mana_workshop_book',0,-6,
  ['沿中间主干依次完成：备料→搭炉→灵纹铁→魔法土与灌溉→供魔花园→第一次自动加工。',
   '之后再向两侧选择牧场供能、生产、农业与远行分支。不用把所有机器做一遍。',
   '蓝色的魔力既能装瓶，也能经过线路输送。田里的液体只负责灌溉，不能抽回去无限供能。'],size=2)
 quests[-1]['rewards']=[{'id':uid('reward/workshop/start'),'type':'item','item':'tnc:mana_workshop_book','count':1}]
 prep=add('workshop/stage0','&6阶段一 · 备料，不急着做机器','minecraft:crafting_table',0,0,['本阶段没有任何动植物发电前置。先准备普通材料，做第一座手动注魔的炉。'],[start],size=1.9)
 raws=[]
 for x,item,count,title in [(-18,'minecraft:brick',10,'砖与圆石 · 炉体'),(-9,'minecraft:copper_ingot',3,'铜锭 · 炉核与注能台'),(0,'minecraft:amethyst_shard',4,'紫水晶 · 炉核'),(9,'minecraft:iron_ingot',1,'铁锭 · 第一份灵纹铁'),(18,'minecraft:glass',1,'玻璃 · 注能台')]:
  raws.append(add('workshop/raw/'+item,title,item,x,5,['拿到材料即可记录，不收走。','另外备好圆石、熔炉、玻璃瓶、空桶、泥土、线和石砖。'],[prep],item=item,count=count))
 parts=[]
 for x,id,count in [(-15,'forge_firebrick',24),(-5,'magic_forge',1),(5,'forge_core',1),(15,'forge_exhaust',1)]:
  parts.append(add('workshop/part/'+id,name('tnc:'+id)+' ×'+str(count),'tnc:'+id,x,12,[recipe(id),'炉体需要24炉砖＋1炉口＋1炉核＋1排烟顶。配方还会消耗炉砖，建议先做40炉砖。'],([raws[0]] if id=='forge_firebrick' else [raws[1],raws[2]] if id=='forge_core' else ['workshop/part/forge_firebrick']),item='tnc:'+id,count=count))
 forge=add('workshop/forge','&6搭好 3×3×3 魔力炉','tnc:magic_forge',0,20,
  ['每行从左到右看，先认清炉口正面朝向：','底层：砖砖砖 / 砖砖砖 / 砖砖砖','中层：砖砖砖 / 砖核砖 / 砖炉砖','顶层：砖砖砖 / 砖烟砖 / 砖砖砖',
   '共24砖、1炉核、1炉口、1排烟顶；没有空心格。右键炉口开GUI，结构示意全部通过才算成型。'],parts,adv='workshop/forge_formed',size=2)
 injected=add('workshop/manual','&e先亲手注入魔力','tnc:magic_forge',0,26,
  ['打开炉口GUI，点击注入自身魔力，每次最多25M，确实扣玩家余额。','第一块灵纹铁要60M：注入三次，放铁锭，选择灵纹铁配方。','此时不用种花、不用抓动物，也不用任何旧注能口。'],[forge],adv='workshop/manual_forge')
 iron=add('workshop/iron','&f第一块灵纹铁','tnc:spirit_iron',0,32,['炉内：铁锭×1＋60M→灵纹铁×1。','后面集露盏、浅魔池、锯台和魔导丝各用3块，检流尺另用1块，逐步补够即可。'],[injected],item='tnc:spirit_iron')
 infuser=device('mana_infuser',0,38,[iron],['阶段二：先解决容器、魔法土与灌溉。','潜行右键开面板，将空玻璃瓶放第一个输入格。','关闭面板后空手持续按右键，或用GUI注能按钮，每秒从自己身上转入5M。普通瓶100M，桶400M。'])
 bottle=add('workshop/bottle','&b装满第一瓶 · 100M','tnc:mana_bottle',-8,44,['普通魔力瓶容量100。用注能台实装100M，通常需20秒。','创造栏里拿出的空瓶也必须注入余额，不能靠复制NBT得到更多能源。'],[infuser],item='tnc:mana_bottle')
 bucket=add('workshop/bucket','&b田边的流动魔力','tnc:flowing_mana_bucket',8,44,['空桶放注能台，注入400M得到魔力桶；也可用4个独立满瓶＋空桶合成。','像水一样沿地面流向低处。把它放在田边沟槽中，不要直接往植株上倒。','流动只允许在自己的产权区域内延续，不会淹到公共镇区或邻居家。'],[infuser],item='tnc:flowing_mana_bucket')
 soil=add('workshop/soil','&a魔法土源 · 一次准备一块田','tnc:magic_soil_source',-8,50,['工作台：8份泥土围1个满魔力瓶，扣100M并退回原空瓶。','把土源放在地面，同一高度9×9范围内获准修改的泥土在10秒后变成魔法土。','草地、石路、建筑不会被替换；土和土源都无需锄地。'],[bottle],item='tnc:magic_soil_source')
 water=add('workshop/water','&b完成第一次魔力灌溉','tnc:magic_soil',0,56,['在田边放魔力桶。魔法土八格内、有同高度或高一格的魔力液体，就能种魔植。','灌溉后的土会溢出细小青蓝微光；缺水时植株保留，只暂停成长。','不要把收集器放在液体里；液体灌溉与收集器输能是两件事。'],[soil,bucket],adv='workshop/poured_mana',size=1.8)
 seed=add('workshop/seed','&a先种两株翠脉枝','tnc:verdant_vein_seed',-16,62,['从野外寻找或委托指引取得种源，带回种在魔法土上。','翠脉枝成株后每2秒积累1M，最多40M；不需要再喂别的材料。','异兽骨粉右键推进一阶段；创造模式潜行右键直接成株，方便你观察。','仍需合法地块、魔法土和灌溉。'],[water],adv='plant/verdant_vein')
 dust=add('workshop/dust','试种工具 · 异兽骨粉','tnc:beast_bone_meal',-25,56,['石骨或霜骨可磨4份，角粉可磨2份；起步用普通骨粉＋紫水晶也能做2份。','只催生长，不灌满植株魔力，不替代其供魔触发。'],[soil],item='tnc:beast_bone_meal')
 collector=device('dew_collector',-8,63,[seed],['集露盏收根部周围3×3×3内、同属自己的成熟植物。','建议与植株根方块放同一高度，别埋进土下。一个盏最高收4M/秒，弱供魔株不会被强行变成4M/秒。'])
 wire=device('mana_thread',0,63,[water,iron],['每份配方产4根，逐格相邻放置，不允许中间空一格。','入门带宽8M/秒。无所谓电压、正负极和接头方向；是魔力线路，不是FE电。'])
 pool=device('mana_basin',8,63,[wire],['沿线放浅魔池，容量1000M。','俯视示例：翠脉枝→相邻集露盏→魔导丝→浅魔池。普通储池不是新的能源来源。'])
 meter=device('mana_meter',18,63,[iron],['右键机器，看当前余额、实际流入/流出和停机原因。','不亮先查：植物是否成熟和有灌溉→是不是自己的→收集距离→线路是否连续→机器输出是否塞满。'])
 collected=add('network/network/collected','&a看见第一次真实供能','tnc:dew_collector',-6,70,['阶段三：让成熟植物给线路供魔，再驱动第一台机器。','收集到真实魔力后自动完成，拿着盏不算。','翠脉枝40秒最多产生20M左右，两株可减少等待；更多产能方式在左侧供魔支线。'],[collector,wire],adv='network/collected')
 stored=add('network/network/stored','&b浅魔池里有了余额','tnc:mana_basin',6,70,['等魔力实际进入浅魔池后完成。','如果消费者先拿走了魔力，先暂时断开机器，确认储池能增长。'],[pool,collected],adv='network/stored')
 saw=device('mana_sawmill',0,77,[stored],['锯台与线路相邻。GUI第一个输入格放1原木，成品格留空间。','每次扣12M，4秒加工成6木板。没有魔力会等，不吞原料。'])
 worked=add('network/network/worked','&6第一份自动加工成品','tnc:mana_sawmill',0,84,['让锯台实际做出一次木板：这一步把植物→魔力→线路→机器串起来。','接下来可将线路接到魔力炉口旁，从手动注魔过渡到花园给炉子供能。'],[saw],adv='network/worked',size=1.8)
 planthead=add('workshop/plant_power','&a供魔支线 · 花园不是只放着好看','tnc:dawn_disk_seed',-31,70,['以下是不同的实际供魔方式，选择适合院子的即可。','所有产能只在区块加载时计算，果实收获可能清空植株能量缓冲。'],[collected],size=1.8)
 plant_sources=[('dawn_disk_seed','晨晖盘葵 · 日照','成熟、白天、露天时每秒1M。把集露盏放根边，不用消耗燃料。'),('wind_sail_seed','风铃帆草 · 自然风','成株露天，来风方向无遮挡。普通风约1M/秒、雨天约3M/秒；根边不要挤满同种帆草。'),('echo_bean_seed','回声豆 · 用声音开工','根边三格内敲出3种不同音高，再拿自己的豆荚右键喂入；随后6秒每秒2M。不是一直无料发电。'),('dream_compost_seed','沤梦菌 · 有机循环','成熟后喂有机料；三类料发酵90秒总计300M，同类料总计180M。'),('thermal_foldleaf_seed','温差折叶 · 冷热两侧','成株旁放真正燃烧的普通熔炉和水或冰，每秒2M。魔力炉不能作为无限回供热源。'),('hanging_dewgrass_seed','垂露丝草 · 留住雨水','真实露天雨水每秒蓄2M，晴天缓慢输出已积累的余额。')]
 for i,(id,title,detail) in enumerate(plant_sources):add('workshop/source/'+id,title,'tnc:'+id,-31,77+i*5,[detail,'共同接法：种好并成熟→根部相邻集露盏→魔导丝→储池/机器。'],[planthead],item='tnc:'+id)
 animalhead=add('workshop/animal_power','&6供魔支线 · 先有牧场，再谈取魔','tnc:pasture_staff',31,94,['基础只用饲料槽喂食。认养后用牧务杖选兽，再点自己的栖居标记设窝。','然后牧务杖选兽→右键集息器绑定，保持12格内。集息器默认取魔，潜行空手右键可切充能。'],[worked],size=1.8)
 cage=add('workshop/cage','捕获与安置工具','tnc:pasture_cage',25,101,[recipe('pasture_cage'),'灵纹笼负责低阶，曜纹笼负责中阶，星髓笼负责稀有兽。先按动物页完成信任，再长按捕获。'],[animalhead],item='tnc:pasture_cage')
 trough=add('workshop/trough','饲料槽 · 唯一基础喂食设施','tnc:pasture_trough',37,101,[recipe('pasture_trough'),'放在窝点可达处，右键投入该动物喜食；空手打开库存。普通饲养不需要再做各种盘。'],[cage],item='tnc:pasture_trough')
 breath=device('breath_collector',31,108,[trough],['牧务杖选自己的兽，再点此器绑定，默认从动物取魔。','生产型：喂食槽持续提供真实食物，移动/夜行/暖息等行为将饲料额度转成魔力。','储魔型：蓄魔鲸、鸣线蜥取的是已有储量，不是无料无限发电。'])
 for i,(id,title,detail) in enumerate([('springhoof_strider_spawn_egg','跃泉蹄兽 · 跑动供魔','提供合适窝点与食物，让它在允许范围内行走；每份饲料有120M工作额度，移动才释放。'),('forgegill_tapir_spawn_egg','炉鳃山貘 · 暖息供魔','温暖、吃饱并启用工作后，定期积累暖息魔力；集息器将工作缓冲取出。'),('drumbelly_otter_spawn_egg','鼓腹砂獭 · 滚动供魔','提供活动空间，滚动累计8格释放40M，每份食物最多转200M。'),('dewbound_whale_spawn_egg','蓄魔鲸 · 活体储池','先从你的网络充进去，再取出来；输入/输出最高32M每秒，容量16000M，不自发产生能量。')]):
  add('workshop/animal/'+id,title,'tnc:'+id,31,115+i*5,[detail,'请从异兽牧场页看捕获、喜食与栖居要求；刷怪蛋仅作图标，不要求玩家合成。'],[breath])
 target=device('color_target',43,135,[breath],['仅供曳彩角羚使用。绑定后看器件提示的偏好颜色，拿对应染料右键改色。','角羚吃饱后向色靶释放不破坏地形的能量，必须保持12格内和无遮挡。'])
 mid=add('workshop/stage4','&6阶段四 · 按用途扩建','tnc:radiant_gold',0,94,['基础循环完成后再开下面的分支。','机器任务里第一行总是制作材料，后面写操作、耗魔与产物；不用把每个分支都走完。'],[worked],size=2)
 gold=add('workshop/gold','曜纹金 · 中阶材料','tnc:radiant_gold',-8,101,['魔力炉：金锭×1＋180M→曜纹金×1。','先给炉口接上庭院线路，可以继续手动补魔。用于更高带宽、织机和充能。'],[mid],item='tnc:radiant_gold')
 crystal=add('workshop/crystal','星髓晶 · 高阶材料','tnc:star_marrow',8,101,['魔力炉：钻石×1＋600M→星髓晶×1。','先建立稳定产能与储池，再做这条高阶分支，不把鲸或水桶当免费能源。'],[gold],item='tnc:star_marrow')
 branches=[
 (-18,'&e生产线 · 纸、布和旅粮','tnc:mana_pulp_press'),(0,'&a农牧线 · 省事，不代替照料','tnc:harvest_arm'),(18,'&b工程线 · 管好传输和储存','tnc:radiant_thread')]
 heads=[]
 for x,title,icon in branches:heads.append(add('workshop/branch/'+str(x),title,icon,x,116,['从上面的材料阶段接入，按自己的生活需要选择。'],[gold],size=1.8))
 devices=[
 ('mana_pulp_press',-18,123,0,'两甘蔗或听潮苇放输入格，旁边一格要有普通清水；16M、4秒制3纸。'),
 ('mist_loom',-18,130,0,'两雾织棉纤维或帆纤维放第一格；30M、6秒制1雾布。'),
 ('meal_dryer',-18,137,0,'第一格放营养值至少6的食物，第二格放纸；24M、6秒制远行餐。'),
 ('harvest_arm',0,123,1,'面前9×9范围成熟原版作物，库存留种后补种；每株4M，两个成品格都要留空间。'),
 ('mana_feed_plate',0,130,1,'这是后期自动投料器，基础喂食不需要它。放在自己的饲料槽旁，输入真实食物；每次耗1M向槽分配最多8份。'),
 ('mana_incubator',0,137,1,'放自己动物产出的真实受精卵。无魔力仍基础孵化，有魔力0.15M/秒提高25%速度；不凭空生产卵。'),
 ('gentle_irrigator',0,144,1,'9×9普通耕地缺水时每10秒最多8M灌溉。只服务传统作物，不能替代魔植的流动魔力。'),
 ('climate_lantern',0,151,1,'在完整封闭的9×9传统温室里，每秒0.4M使作物不受反季减速。魔植本来就不分四季。'),
 ('seed_sorter',0,158,1,'把成批种子放输入格，耗5M搬到成品格；属于仓储便捷旁支，不是采种前置。'),
 ('radiant_thread',18,123,2,'提高主干到32M/秒；需要露丝。大量供魔株经过一条线仍共享这一带宽。'),
 ('radiant_basin',18,130,2,'中阶储池8000M、32M/秒；靠近用能区，承接低负载时的余量。'),
 ('mana_valve',18,137,2,'潜行右键切优先顺序；外部红石信号可关断。没有阀也能完成基础供魔。'),
 ('mana_mirror',18,144,2,'两镜沿六轴对齐，12格无遮挡；共享4M/秒。是跨空隙传能，不是放大器。'),
 ('mana_cart_track',18,151,2,'沿普通铁轨布置驱动座，推动载真实物品的货车；行进每10格耗1M。')]
 for id,x,y,branch,detail in devices:device(id,x,y,[heads[branch]],[detail])
 for i,(id,detail) in enumerate([('mana_lantern','每秒0.05M持续照明，最早可做的日常用能之一。'),('bright_mana_lantern','每秒0.1M，照亮庭院。'),('ward_bell','九格内发现敌意生物时耗2M响铃，输出短红石信号。')]):device(id,-42,120+i*7,[worked],[detail])
 cloth=add('workshop/cloth','雾布 · 充能工具的共同材料','tnc:mist_cloth',-26,144,['左侧雾纱织机：两份雾棉纤维或帆纤维＋30M，6秒制一份雾布。','归途缎带、缓降羽饰、耳坠、护符、建筑尺、符弩和机甲都需要它。','先做这份材料，再沿连线制作后面的工具。'],['node/mist_loom'],item='tnc:mist_cloth')
 charge=device('accessory_charger',-10,170,[gold],['接上魔力线路，充能饰品或机甲放第一输入格，每秒实际转入最多16M。','它负责物品充能，不负责动物喂食，也不是旧魔力瓶底座。'])
 charged=add('network/network/charged','&b完成一次真实物品充能','tnc:accessory_charger',-10,177,['观察饰品上的储量增长，真实完成转移后记录。'],[charge],adv='network/charged')
 for i,(id,detail) in enumerate([('homeward_ribbon','充入120M、脱战10秒后回到自己买下的房屋。'),('descent_feather','放副手，下降时耗2M/秒缓降。'),('ore_whisper_earring','每次15M，提示八格内矿物方向。'),('dew_ward_charm','放副手，受伤时花费魔力减伤，有冷却。')]):
  add('accessory/'+id,name('tnc:'+id),'tnc:'+id,-28+i*6,184,[recipe(id),detail,'先制作，再到上方充能台充能；不能用空饰品测试效果。'],[charge,cloth],item='tnc:'+id)
 for i,(id,detail) in enumerate([('star_thread','128M/秒的高阶主干，只有实际产能提高后才需要。'),('star_basin','32000M高阶储池，容量大也要慢慢积累。'),('star_mirror','六轴对齐24格、16M/秒，适合跨庭院传输。')]):device(id,20,169+i*7,[crystal],[detail])
 for i,(id,detail) in enumerate([('mana_building_ruler','使用真实建筑材料，每块3M；普通五格直线，潜行三格框。'),('rune_crossbow','手持发射，消耗实际箭和25M，先到充能台补足余额。'),('mana_mech','在充能台充入最多6000M，右键穿胸甲位；步行免费，冲刺耗魔。'),('mech_spellgun','必须穿机甲后长按右键，每发耗机甲20M和真实箭；最多8秒连射、冷却4秒。')]):
  add('accessory/'+id,name('tnc:'+id),'tnc:'+id,20,192+i*7,[recipe(id),detail],([crystal,charge,cloth] if id=='mana_mech' else ['accessory/mana_mech'] if id=='mech_spellgun' else [gold,charge,cloth]),item='tnc:'+id)
 add('workshop/checklist','&3检修清单 · 不工作时按顺序查','tnc:mana_meter',0,220,
  ['①种下去了但不长：魔法土？八格内灌溉？上方空间够吗？','②花不供魔：成熟？自己的？这种花的日照/声音/投料条件满足吗？',
   '③盏有魔力、机器没有：线路相邻连续？接到炉口而不是外墙？同一产权和主人？','④动物没出力：已认养设窝？食槽够得着？集息器绑对了吗？鲸是否只是没充电？',
   '⑤有魔力不加工：材料放对输入格？输出堵住？纸机旁有清水？库存和配方数量足够？',
   '⑥供魔时断时续：先用储池缓冲；产能不够就增加来源，不能只靠换粗线。'],[worked],size=1.8)
 return chapter

def main():
 chapter=build()
 # Keep existing IDs but teach the two-ended topology instead of obsolete copper wire.
 replacements={'魔导丝':'青辉导魔晶纤','曜纹导线':'霞金导魔晶纤','星髓导线':'虹星导魔晶纤'}
 for quest in chapter['quests']:
  for key in ['title','subtitle','description']:
   if key not in quest:continue
   def fix(text):
    for old,new in replacements.items():text=text.replace(old,new)
    return text
   quest[key]=[fix(line) for line in quest[key]] if isinstance(quest[key],list) else fix(quest[key])
  if quest.get('icon')=='tnc:mana_thread':quest['description'].append('每段晶纤最多两个接口；可转弯、竖接和连机器。分支请用独立的分流装置，晶纤不会自动六向连接。')
 for base in [ROOT/'questbook/ftbquests/chapters',ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915/config/ftbquests/quests/chapters']:dump(base/FILE,chapter)
 print('Workshop progression:',len(chapter['quests']),'quests')

if __name__=='__main__':main()
