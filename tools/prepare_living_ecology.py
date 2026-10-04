"""Publish our own optional field guides, additive locale keys and tavern demand."""
from pathlib import Path
import hashlib,json,re
R=Path(__file__).resolve().parents[1]
def write(p,d):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for locale,suffix in [('zh_cn','zh'),('en_us','en')]:
    path=R/f'src/main/resources/assets/tnc/lang/{locale}.json';data=json.loads(path.read_text(encoding='utf-8'))
    for kind in ['botanical','pasture','wonder']:
        data.update(json.loads((R/f'work/{kind}-lang-{suffix}.json').read_text(encoding='utf-8')))
    write(path,data)
zh=json.loads((R/'src/main/resources/assets/tnc/lang/zh_cn.json').read_text(encoding='utf-8'))
def uid(text):return f'{int(hashlib.sha256(text.encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}'
def node(page,key,title,icon,x,y,desc,reading=False,size=1.2):
    return {'id':uid(page+key),'title':title,'icon':'tnc:'+icon if ':' not in icon else icon,'x':x,'y':y,'size':size,'shape':'square' if reading else 'hexagon','description':desc,'dependencies':[],'rewards':[],'tasks':[{'id':uid(page+key+'task'),'type':'checkmark','title':'阅读这项实际玩法说明'} if reading else {'id':uid(page+key+'task'),'type':'item','item':'tnc:'+icon if ':' not in icon else icon,'count':1,'consume_items':False,'title':'背包持有此物（不消耗；不等同于已亲自种养）'}]}
def chapter(name,title,icon,order,nodes):
    write(R/f'questbook/ftbquests/chapters/{name}.snbt',{'id':uid(name),'filename':name,'group':'59249BFD5667691A','title':title,'icon':'tnc:'+icon,'order_index':order,'default_hide_dependency_lines':False,'default_quest_shape':'hexagon','quest_links':[],'images':[],'quests':nodes})
page='tnc_field_02_botany';nodes=[node(page,'intro','&a&l魔法植物 · 找到自己的花园','plant_sample_clip',0,-4,['&a&l观察环境 → 找到种源 → 布置花床 → 留种收获 → 料理、工具和交易','','&f新旧世界的普通野外都能种，不要求先买天空岛田地。天空岛的公共区域才受保护。','&7这些是可选生活玩法，没有强制主线依赖。单株节点检测背包持有，不冒充亲手栽培。','&e剪刀或采样夹对对应原版植株采样：首次十次有效采样保底两粒种源。野外也可遇到已成熟魔法植物。'],True,2)]
species=[
('dawn_disk','跨过黎明','露天日照；亲眼见它从深夜迎来黎明，睡觉跳时不算。晨蜜做早餐或定向调查页。'),
('hearth_pepper','炉边长出的余热','附近有正在燃烧的熔炉或点燃营火才生长。暖椒制保暖料理，余热椒油装进锻炉升级槽，十次实际完成后用尽。'),
('mist_cotton','半阴织雾','湿润且半阴环境。收获雾棉，织布做折叠雾帐；原地30秒内阻止远处怪新锁定，攻击会结束。'),
('stone_fern','四类表层岩纹','在岩床生长，叶纹写入产物NBT。岩叶与纸加工时保留纹样，可装调查档案夹。'),
('mirror_lotus','清水下的镜潭','清澈源水与泥/黏土底床。镜露用真实空瓶取，制镜潭饮：水下清晰视野60秒，饮后返瓶。'),
('wish_puff','把远照果丢出去','夜间湿润花床。成熟收远照果；投掷后在可见表面铺真实方块光，水平半径50格、20秒，不隔实体墙照亮屋内。'),
('echo_bean','让豆荚听见三音','附近音符盒奏C—E—G。豆荚制作音符盒和节律继电器，声音能真正触发红石。'),
('ladder_vine','搭架向上','相邻田间藤架，留出上方空间。藤筋制登山绳，能部署12格真实攀绳、潜行收回。'),
('frost_chime','寒地的露','低温与水分。霜露用瓶取，配暖椒成霜椒餐，可获得10秒耐火。'),
('salt_ink','海风写字','海岸附近盐水/盐盆条件。盐墨制调查页、信封，也是墨信树的种源研究材料。'),
('wind_sail','让花床迎风','高处露天与上方空间。帆纤维制折叠缓降布，使用8秒缓降，落地结束。'),
('sleep_clock','跟着主人作息','床边培育，真实睡够再醒来会留下照料事件。静叶制钟眠茶：解除挖掘疲劳，非战斗回复4生命。'),
('shadow_cut','半阴花床的纹样','半阴照明。八种纹样留在影绢上；与纸加工成图样卡，署名记录与原纹样一同保存。'),
('paper_tree','把书带进花园','桦苗采样时随身两纸与一盐墨可研究种源。栽种后附近阅读研究，预留上方两格，持续取纸皮。'),
('flight_pod','给种子做行囊','水边湿润环境。荚壳制防雨种匣，四品种各16粒；可打开真实容器，内容随物品保存。'),
('honey_cluster','养蜂也养花','真正蜜蜂到花边完成授粉；对刷同一花有节流，授粉刷每次耗耐久。蜜粉做蜜簇糕、授粉刷和其他料理。'),
('star_dew','夜里长出的法力','露天夜间与水分。星露果食用16刻完成，回复30真实法力，四秒冷却；也可制瓶饮。'),
('dance_bell','记住主人的花','水壶照料认主。成熟后主人近身就连续摆动，不是只转一下模型；舞瓣可做居民礼物和铃片。'),
('star_rest','三只会回家的花灵','高处晴夜观察十秒获得种源。成熟植株在合适时段生出三只独立花灵，围绕母花飞；离开母花会返回，重载不重复增加。')]
for i,(key,tag,help) in enumerate(species):
    name=zh.get('block.tnc.'+key,key);nodes.append(node(page,key,'&a'+name+' · '+tag,key+'_seed',[-20,-10,0,10,20][i%5],(i//5)*6,[help,'','&7留种后可继续扩培；产物可选择自用、加工、供酒馆或出售。']))
nodes += [node(page,'sky','&2望天蔓 · 四日通天','sky_vine_seed',-15,25,['制作三境调查卷，在相距256格的林地、高地、谷地各观察一次，首次完成得到两粒种源。','预留9×9空地和上方64格。四游戏日分阶段生长，遇建筑暂停；不替换原有方块，也不强制加载区块。','成熟用剪刀每游戏日取四天幕纤维与一粒种源。纤维制24格可收回长绳。']),node(page,'mana','&a翠脉枝 · 可见且真实的输送','verdant_vein_seed',0,25,['成熟翠脉枝向相邻合规设备输送绿色魔力；绿色轨迹对应真实扣除与接收。','接发电座或锻炉注口，存魔与采枝共享同一资源账，不能同时免费取得。']),node(page,'tools','&6种匣、档案与日常照料','rainproof_seed_box',15,25,['雨囊水壶存水、授粉刷有耐久、调律铃与音符盒照料特定植株。','种匣四品种各16粒；档案夹十二份署名调查页。持有槽锁定，防止把容器装进自己或复制库存。'])]
chapter(page,'&a&l花园与魔法植物','dance_bell_seed',3,nodes)
page='tnc_field_03_pasture';nodes=[node(page,'intro','&6&l异兽牧养 · 食物、伙伴和工作','pasture_book',0,-4,['&f已有响铃羊，加上23种各自注册、各自模型的新异兽。','&e喜食食物照料三次，每次有效照料间隔60秒，建立主人与窝点。牧养册右键动物查看状态。','&7买房不是野外养殖的前置。活产、肉食、元素、助工四种路线都可选。','&f窝点、饲料、栖居条件、工作路线与资源保存到存档；幼兽至少两个游戏日长大。'],True,2)]
source=(R/'src/main/java/com/tnc/tnc/life/pasture/PastureSpecies.java').read_text(encoding='utf-8')
animals=re.findall(r's\("([^"]+)","([^"]+)",Role\.(\w+),Habitat\.(\w+),Items\.(\w+),([0-9]+),([0-9]+),([0-9]+),"([^"]+)"',source)
help={
'stonebarrow_boar':'留养繁殖成年屠宰，石垒肉与石骨做厚实锅食；吃锅食下一次受击减少2伤害。',
'emberback_hog':'温暖窝点，专属暮炭肉与暖脂供保暖炖食、暖饲料。',
'tideback_newt':'真实水体窝点，潮背肉和水膜供水边料理及防水材料。',
'froststride_fowl':'鸟肉做御寒串；留养也可周期取银霜卵。繁育受精卵在卵架实际孵两天。',
'apiary_toad':'蛙肉可做料理；活体取蜜露需真实空瓶，不能凭空造瓶。',
'starfelt_hare':'专属兔肉与软绒；远行卷给一分钟分次回复12魔力。',
'dusk_lantern_deer':'成熟周期自然落角，托盘/采露架可收，不必杀鹿。角给随身提灯添12分钟燃料。',
'pattern_shell_snail':'水边养，木刮具取壳胶，每次扣真实储备与工具耐久；壳胶是工具配方材料。',
'post_heron':'可取羽毛，也能选择两只自己的托盘、128格内真实搬运；走到两端才交接货物。',
'watch_mantis':'两个自己的栖居标记设夜巡路线；真实附近敌对生物触发预警，翅材制警路镜片。',
'mirrorwing_moth':'夜间探访真实植物并有限助长；鳞片做聚光镜片，工作灯真实亮度12升15。',
'forgegill_tapir':'温暖窝点积暖息，用空暖息罐采集。喝后保暖一分钟并返罐；也做暖饲料。',
'mistbelly_otter':'自己的食水槽供真水：每次取四次浇灌的浓露扣四水份，用空瓶接。没有水不会产水。',
'ringstone_tortoise':'消耗饲料产润土砾，浇灌既有耕地/植物保湿十分钟，不凭空造水或矿。',
'papersail_ray':'开阔窝点，取风羽；使用小幅上升与六秒缓降，也可加工风瓶。',
'wirecall_lizard':'雷雨可存电，充电栖架实际扣100FE只存80FE；雷余晶每份50FE，输电扣存量，不造免费电。',
'dewbound_whale':'露天慢蓄魔，成年每日48、最多96。魔力瓶右键采四秒，每次最多24，采后有间隔。新鲸与幼鲸从零存量开始。',
'satchelback_runner':'两托盘32格内四格各16物品真实搬运，每四趟耗一份饲料；货物随动物存档和搬迁笼保存。',
'bowlhorn_rhino':'牧务杖批准3×3地块，消耗饲料逐格到达开垦；不会越权挖玩家建筑。',
'pageforage_raccoon':'只拾取主人掉落的种子/纸。记住实际见过种源后，每两日可用纸皮换该目录一粒种子；不会解锁未发现植物。',
'patternbuild_beaver':'最多记八处已有栅栏，托盘供真木栅栏；缺口出现后到场修补并扣材料。',
'springhoof_strider':'可装兽鞍骑乘与蓄力跳；耐力/饲料实际消耗，蹄胶供攀绳配方。',
'pillowlight_marten':'瓶中魔力注入，最多存60；跟随消耗魔力生成真实移动灯，灯有期限，不留永久光。取灯蜡供提灯。'}
for i,(key,name,role,hab,food,young,breed,days,product) in enumerate(animals):
    foodname=zh.get('item.minecraft.'+food.lower(),food.lower());rolezh={'MEAT':'肉食与料理','LIVE':'留养周期取产','ELEMENT':'元素资源','HELPER':'实际助工'}[role]
    icon='mana_bottle' if product=='stored_mana' else product
    nodes.append(node(page,key,'&e'+name,icon,[-20,-10,0,10,20][i%5],(i//5)*6,['&6'+rolezh+' · 幼兽'+young+'游戏日成熟',help[key],'','&7普通生存寻找对应环境的野生个体。节点检查真实产物持有；购得产物也可以阅读完成，不能证明亲手养成。' if product!='stored_mana' else '&7本节点是魔力采集说明，读完自行勾选。用空瓶制作不冒充已收集魔力。'],product=='stored_mana'))
nodes += [node(page,'bottles','&b魔力瓶 · 一份储备，三种用途','mana_bottle',-15,31,['普通瓶24、精制瓶72容量。采鲸四秒；空中饮用回复真实魔力，右键炉后注口或发电座转入。','魔瓶座放一瓶，每次红石上升沿向相邻同主人设备转最多5魔力。节律继电器可驱动脉冲。','瓶复制NBT仍共享同一世界账；不会复制可用魔力。']),node(page,'facility','&6先建窝，再安排工作','pasture_staff',0,31,['食水槽：四槽各16真实饲料、16份真实水。托盘：搬运端点和物资箱。','栖居标记定窝，卵架孵育，采露架收落物，充电栖架存电；各自有主人与四槽原生GUI。','牧务杖选动物后右键标记/托盘/地块设路线；潜行可调整跟随与留守。']),node(page,'cage','&6搬迁笼 · 货物也带走','pasture_cage',15,31,['只可搬自己的异兽，同UUID保存、健康与资源照原值保留。','笼使用世界票据；复制笼NBT只允许成功放出一次。普通放出检查碰撞与地界。']),node(page,'economy','&a养殖是生活，不只是任务','minecraft:emerald',0,37,['朝夕商行现收购新增魔法农产、专属肉材和留养产物。酒馆委托栏也会出现料理、远征供货、布料与纸材委托。','决定把收获留下自用、扩大繁育，还是出售买家具和房屋。每日推荐与个人收购额度沿用现有系统。'],True)]
chapter(page,'&6&l异兽牧养与伙伴','pasture_book',4,nodes)
page='tnc_field_04_workshop';nodes=[node(page,'intro','&b&l魔导工坊 · 魔力成为工作','forge_guide',0,-4,['&f魔力来自自己、成熟翠脉枝和养成后的浮鲸。存储与输送是实际资源账，不靠改名物品。','&7这一页教你装炉、接线、做有限自动化；不是必须完成的主线。'],True,2)]
work=[('forge_guide','&6先读3×3×3搭炉说明',['按说明书摆完整27格：上下层铜框与耐火砖，中层四接口与四立柱，最上方中央排烟口。','任意缺块、接口反向或材料不足都会暂停，完成前不会提前吃掉材料。']),('magic_forge','&6打开炉子的真实GUI',['炉芯右键打开四输入、一输出、一升级槽。配方列表显示材料、魔力与进度；后注能口空手注魔，或根芯/魔力瓶注入。','加魔、点火、加工与完成各有粒子和声音；输入/输出接口可接自己的漏斗。']),('hearth_oil','&e炉油与冷凝壳',['余热椒油十次成功作业，冷凝壳二十次成功作业，都让当前工作20%更快。','只有实际完成才扣次数，暂停不扣；移出升级物保留剩余次数。']),('mana_generator','&a魔力发电',['发电座收真实魔力，按单向规则转换FE。不会把FE转换回无限魔力。']),('mana_cable','&a铜脉接线',['连接同主人设备；网络有限节点、吞吐和加载范围，不会强行加载天空岛或远处区块。']),('mana_battery','&b存电再分配',['魔力先存瓶或发电座；电存在5000FE蓄电箱。只能向合规设备实际转移可用电量。']),('mana_work_lamp','&e给工作台照明',['工作灯可常亮、夜间或红石控制，默认每四有效照明刻耗1FE。','节能晶芯：有效照明十分钟，改为每八刻1FE。聚光镜片：真实方块光12升15。都可拆，剩余次数保存。']),('mana_paper_press','&6一台会吃水的压纸机',['听潮苇、水与FE实际加工成纸，缺任一原料暂停。可接合规漏斗供料、取产。']),('field_sound_relay','&d声音控制红石',['空手右键循环音高0—24；六格内同音符触发十刻红石脉冲，五秒冷却。','装伴步铃芯后空手可手动响铃；潜行调音，剪刀拆回铃芯。可驱动魔瓶座。']),('storm_crystal','&b动物也参加工坊',['鸣线蜥把实际存电压成50FE雷余晶，右键蓄能设备只转入可接收电量，余量仍在晶中。','雾腹水獭供灌溉、锦纹筑狸修围栏、囊背负兽搬货；自动化从伙伴和资源分工开始。'])]
for i,(key,title,desc) in enumerate(work):nodes.append(node(page,key,title,key,[-16,0,16][i%3],(i//3)*6,desc))
chapter(page,'&b&l魔导工坊与小自动化','forge_guide',5,nodes)
old=R/'questbook/ftbquests/chapters/tnc_field_01_expeditions.snbt';data=json.loads(old.read_text(encoding='utf-8'));data['quests'][0]['description'][1]='&7共29种已实现植物、24种独立异兽与魔导工坊，把远方带回生活';write(old,data)
foods=['star_dew_fruit','morning_honey_breakfast','hearth_pepper_soup','frost_pepper_meal','honey_cluster_cake','stonebarrow_hotpot','emberback_stew','tideback_chowder','frostwarm_skewer','honeydew_casserole','starfelt_travel_roll']
supplies=['mist_cotton_fiber','stone_pattern_leaf','vine_sinew','salt_ink','sail_fiber','paper_bark','rainproof_pod','dance_petal','sky_canopy_fiber','lantern_antler','shell_glue','flight_feather','mirror_scale','soft_down','nest_glue','hoof_glue','lamp_wax']
for kind,ids in [('food',foods),('supply',supplies)]:
    p=R/f'src/main/resources/data/bountiful/bounty_pools/tnc/tnc_{kind}_objs.json';data=json.loads(p.read_text(encoding='utf-8'))
    for id in ids:data['content']['tnc_'+kind+'_'+id]={'type':'item','content':'tnc:'+id,'amount':{'min':1 if kind=='food' else 2,'max':3 if kind=='food' else 6},'unitWorth':360 if kind=='food' else 250,'rarity':'UNCOMMON','timeMult':3.0}
    write(p,data)
print(f'Prepared three optional chapters, {len(animals)} animal introductions and 28 tavern supply/food entries; merged locale patches.')
