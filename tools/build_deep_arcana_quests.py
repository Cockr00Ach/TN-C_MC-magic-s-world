import json,hashlib
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def uid(s):return f'{int(hashlib.sha256(("tnc-deep-"+s).encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}'
def write(p,o):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n','utf8')
quests=[]
def q(key,title,icon,x,y,lines,dep=None,item=False):
 n={'id':uid(key),'title':title,'icon':'tnc:'+icon,'x':x,'y':y,'description':lines,'dependencies':[uid(dep)] if dep else [],'hide_dependency_lines':False,'tasks':[{'id':uid(key+'task'),'type':'item' if item else 'checkmark',**({'item':'tnc:'+icon,'count':1,'consume_items':False} if item else {})}],'rewards':[]};quests.append(n);return n
q('start','&b&l魔力的另一面','arcane_observatory',0,-8,['这些是生产与探索的旁支，不阻拦主线。浅魔池等基础储池永远不会过载。','F8魔力视能观察药釜热环、器灵、餐盘、过载与天路。各装置手持原料右键投入，潜行空手取回。','工程装置在魔力锻炉制作；配方可查JEI。创造测试可用创造魔力棱镜给装置补魔。'])
q('sight','&3内视与精神','elixir_echo',0,-6,['开启魔力视后，左上角显示淤积和精神过载。连续三分钟后精神压力逐渐上升，凝视太阳或聚能塔更快。','关闭魔力视会缓慢回落；经脉通畅时不累积。超过80%会遇见只在过载魔力视中可见的高维魔灵。'],'start')
q('spa','&6回家泡一会儿','geothermal_mana_core',-6,-4,['饮用次品/标准/大师药剂分别增加25/10/2淤积。超过50新药效减半，超过80拒绝饮药并干扰自己附近的机器。','地热核心接魔力后投入舒缓浴盐，在五格内铺源水。入水后每秒花1M、减少2淤积。','排空后获一个世界日经脉通畅：回魔+50%，魔力视不增精神压力。温泉建筑由玩家自由装修。'],'sight',True)
q('pact','&d高维契约','forbidden_scroll',6,-4,['精神过载80%以上、开启魔力视时，周围16~32格可能出现高维魔灵。靠近八格内右键契约。','可用当前90%生命/3级经验/献祭普通武器/满融合大师共鸣酊交换棱镜、古卷、寄生种、星界杖心。','交易前留背包位置；同一契约每世界日一次。攻击它会清空魔力并被送上高空。','禁忌古卷右键阅读后，才能在锻炉制作深渊塔、奇点与星界装备。'],'sight')
q('eco','&a相邻，就会共鸣','honey_cluster_seed',-9,0,['合适的成熟魔植与自己养的成年魔兽放在六格内，双方产魔收益+20%。同类伙伴不会叠加。','蜜簇花/曦盘花与镜翅蛾、蜜窖蟾；暖椒/温差折叶/蒸气魔莲与雾腹獭、炉鳃貘。','回声豆/风帆草与鸣线蜥、纸帆鳐；星露/星栖植物与蓄魔鲸；影漏兰与暮灯鹿；虹折冠与曳彩角羚。','不用配对杖，不用阵列，也不增加额外喂养操作。'],'start')
q('farm','&a农用魔药','arcane_sprinkler',-6,0,['农用魔药同样在药釜炼制，打开药典翻到农用配方。手持右键目标；喷灌塔每五秒对9×9内一株施药，消耗一瓶和10M。','透支药：成熟产魔株产能+400%两分钟，随后枯死掉落魔力灰烬。魔力收集和存储仍要有足够吞吐。','巨人/微缩改变魔植和魔兽体型。巨型植株收获巨果，必须送魔导锯台切开，得到原种类八份产物。','属性扭曲仅对水生魔植使用：50%烧毁，50%永久变异蒸气魔莲。不要对珍贵独株赌博。','分裂药对成年产魔昆虫或小史莱姆使用；每次另需一份糖；每只每天一次，附近同种上限十二只。'],'eco',True)
q('cage','&6抓来的魔物也能工作','ecology_restraint_cage',-3,0,['将普通杂兵打到25%以下生命，手持缩水捕捉魔药右键，再在十秒内用空拘束笼右键捕获。Boss和高阶怪不允许抓。','放下装有魔物的笼子，连接魔力，每秒维持3M。连续断供十秒，里面的真实魔物会越狱。','笼中魔物不会伤人、也不会被玩家伤害；不能偷捕别人的笼中魔物。'],'farm',True)
q('slime','&a史莱姆蹦床','mana_pressure_tread',-4,3,['在拘束笼下铺魔力压电踏板，史莱姆每次实际落地制造20土魔力。','拿糖右键笼子增加高跳次数。由踏板输出到晶纤，注意同时给笼子供魔。'],'cage',True)
q('blaze','&c红温车间','mana_spray_provoker',-2,3,['喷水挑衅器旁放水、接魔力，每五秒花5M激怒附近自有笼内烈焰人。','活体吸魔罩放在六格内，吸收愤怒热量：24火魔力/秒。普通挂机烈焰人不产热魔。','遮光怪可在日光镜旁受惊，经罩收集暗影魔力和暗影结晶。'],'cage',True)
q('sun','&6日冕与月华','corona_focusing_mirror',0,0,['日镜必须露天且高于海平面16格，白天产24日冕魔力/秒。月盘夜间产魔，满月三倍，新月两成。','魔力属性会沿同一条真实晶纤网络传输，数量守恒。用同属性魔植/魔兽收集器也能供给器灵。'],'start',True)
q('aurora','&d极光之夜','arcane_observatory',0,3,['每5~8个世界日出现一晚极光，观星台可以查看预报。打开魔力视，整片天空会出现多重极光帘。','极光夜天体采集器产量六倍，并直接产生稀有星界魔力。可提前建储池保留。','终极星界锻造：阅读禁忌古卷，锻炉上方露天、极光正在发生，旁边同属节点备足200星界魔力，才可制作星界杖心/极光羽翼。'],'sun',True)
q('wings','&b乘极光飞行','aurora_wings',0,6,['极光羽翼手持长按右键，以8M/秒沿视线飞行，松手停止推进，短时防摔。','星界杖心也可通过高维魔灵交换，留给后续神器制作。'],'aurora',True)
q('tower','&c深渊聚能工程','abyss_mana_tower',3,0,['后期才建造。以塔核为底层中心，3×3外围连续堆三层，共24块深渊聚能砖；中心上方两格留空。','成型容量100万M，进流超过320M/秒会逐渐过载。没有成型不会吞魔力；浅魔池等基础容器不参与过载。','0~40%平稳；41~75%燥热，连接锻炉/锯台效率+50%；76~99%红色临界，附近红草异变与动物躁动。','100%非破坏性冲击波，召出真实的虚空巨兽。先准备泄压阀，不要在新手农场盲试。'],'pact',True)
q('relief','&6魔力温泉雨','mana_relief_valve',2,3,['泄压阀与同属塔核相邻。潜行空手右键，真实抽取塔内5000M、过载下降20点。','喷出的温泉雨清理附近玩家少量淤积。可以故意在红利区间维持压力，也可以求稳降温。'],'tower',True)
q('beast','&4魔力吞噬者','collapsed_mana_singularity',4,3,['巨兽优先追逐你已储魔的导魔节点并吞吃其中魔力恢复生命，缺少食物才追玩家。','击杀获取唯一材料坍缩魔力奇点，用于制作奇点魔力核心。不会炸毁周围地形。','奇点核心拥有20亿M流转缓冲和64位长期余额，可跨重启保留。'],'tower',True)
q('living','&a会呼吸的装甲','void_parasitic_seed',6,0,['用契约获得的虚空寄生种，在锻炉选择荆棘暴君藤/翡翠生命苔/捕食者花苞三条路线。','首次穿戴扣1生命扎根，随后每秒1M温养；缺魔时消耗饱食。铠甲有独立储魔，不是免费触发。'],'pact',True)
q('thorn','&2荆棘暴君藤','thorn_tyrant_vine',5,3,['四格内有敌人时，每两秒耗6储魔抽打至多三只怪，伤害并击退。魔力视看见藤蔓源流。'],'living',True)
q('moss','&a翡翠生命苔','emerald_lifemoss',6,5,['受伤时花10储魔吸收6伤害；站在草地上可持续消耗储魔回血。'],'living',True)
q('bud','&c捕食者花苞','predator_bud',7,3,['吞噬三格内的实际经验球以及腐肉、骨头、线、火药，把少量魔力还给你。不会凭空生成掉落物。'],'living',True)
q('nurture','&b王杖器灵','spirit_nurture_altar',9,0,['把王级及以上法杖插入祭坛，接入100同属性、太阴或星界魔力，投入大师药剂或Boss核心。','同属性由对应植物/魔兽收集器得到。每次真实消耗材料与魔力，温养成长写在原法杖上。','大师药剂+20成长，核心+100。成长达到100觉醒陨星回响或治疗波，施法命中时20%几率触发、有耗魔与冷却。'],'sun',True)
q('meal','&6餐盘魔法阵','carved_mana_platter',9,3,['手持可食食物，对餐盘不同位置右键，逐格摆满九格；潜行空手取回一份。','需要燥热火、温润水、轻灵风、厚重土、中和五类口味。火椒和鱼不能直接相邻，放布丁/蜂蜜糕隔开。','魔力视下冲突呈红光，成阵呈金光。空手享用，真实消耗九份料理；相克强食会扣血、混乱。','成阵后一刻钟自身回魔充盈，靠近自有魔力节点会供能并加速，适合出发前准备。'],'start',True)
q('skyroad','&b天空魔力高速路','sky_mana_launch_ring',12,0,['喷射环与中继环摆在空中，相邻两环距离不超过20格，可转弯、上坡。','用星路标记杖先右键发射端，再依序右键后续节点。潜行空手清除节点去向。','给发射端接魔力，每一段消耗8M/秒。路径要求全部加载且无实心墙穿过。'],'start',True)
q('surf','&f肉身冲浪与气泡货流','tailwind_boots',12,3,['跳进光流后会被托举前进；潜行离开。穿着全身护甲，手持顺风魔导靴右键，将导靴永久装入护甲；随后进一步加速并防摔。','把自己丢出的物品扔进流中，会形成气泡物流。末端下方/上方放箱子或漏斗自动收货。','出口满了物品会保留，不吞货。通路不强制加载远方区块。'],'skyroad',True)
chapter={'id':uid('chapter'),'filename':'tnc_deep_arcana','title':'&b&l深层魔法 · 世界的回响','icon':'tnc:arcane_observatory','group':'544E434C49464531','order_index':14,'default_hide_dependency_lines':False,'default_quest_shape':'circle','quests':quests,'images':[]}
write(R/'questbook/ftbquests/chapters/tnc_deep_arcana.snbt',chapter)
# Only our own existing alchemy chapter is revised. All IDs and player-written chapters survive.
p=R/'questbook/ftbquests/chapters/tnc_alchemy.snbt';alchemy=json.loads(p.read_text('utf8'));oldids={q['id'] for q in alchemy['quests']}
for n in alchemy['quests']:
 if '第一口药釜' in n['title']:n['description']=['水桶倒水，一桶炼三剂。投三种材料各一份，再加焰蕊盐，用火系法术命中点燃。','按F8看热环：360~620翠绿温区才能融合，水/冰法术降220温度。超过800持续五秒会炸锅并冒毒雾。','浸润、融合、凝结各搅拌一次。不同药性漩涡逐步向中心靠拢。','60~89%融合封釜是标准；100%同心闪星后1.5秒内用封魔盖（或潜行持法杖）右键收汁，是大师品。','提前封釜是30%次品；融合后继续烧则变古怪废液。封釜后用空瓶盛取，书只打开药典。']
 if '焰蕊盐' in n['title']:n['description']=['暖椒+木炭+荧石粉，制作四份焰蕊盐。','每份燃烧1200刻。点火后会持续升温，需观察温度控制；停下搅拌不会自动熄火。']
farm=[('overdraft_elixir','狂躁透支','tnc:hearth_pepper_fruit、红石、烈焰粉'),('gigantism_elixir','巨人化','tnc:mana_root_core、棕色蘑菇、骨粉'),('miniature_elixir','微缩','tnc:rainletter_berry、发酵蛛眼、糖'),('flame_twist_elixir','属性扭曲','tnc:hearth_pepper_fruit、岩浆膏、紫水晶碎片'),('fission_elixir','分裂','tnc:honey_powder、黏液球、发光浆果'),('capture_shrink_elixir','缩水捕捉','tnc:shadow_hour_petal、发酵蛛眼、线'),('soothing_bath_salt','舒缓浴盐','tnc:homeward_flower_petal、海晶砂粒、蜂蜜瓶')]
for i,(icon,title,recipe) in enumerate(farm):
 key='farmformula-'+icon
 if uid(key) in oldids:continue
 alchemy['quests'].append({'id':uid(key),'title':'&a农用 · '+title,'icon':'tnc:'+icon,'x':-6+i*2,'y':13,'description':[recipe+'，三种各一份。','同样在药釜加水、投入焰蕊盐、用火法点燃，在魔力视下控温搅拌后封釜收汁。','用途见「深层魔法·世界的回响」。'],'dependencies':[],'hide_dependency_lines':True,'tasks':[{'id':uid(key+'task'),'type':'item','item':'tnc:'+icon,'count':1,'consume_items':False}],'rewards':[]})
write(p,alchemy)
print('Deep arcana quests:',len(quests),'Alchemy:',len(alchemy['quests']))

# Apply the current physical-cauldron and plant-only meal design after historical generation.
from revise_arcana_feedback_assets import run as revise_arcana_feedback
revise_arcana_feedback()
