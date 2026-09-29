"""Deterministic native Bountiful pools and FTB routes; never edits a player save."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def write(p,obj):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
groups={
 'supply': [('oak_log',4,24,80),('spruce_log',4,24,80),('birch_log',4,24,80),('dark_oak_log',4,16,100),('cobblestone',16,64,20),('deepslate',16,48,25),('sand',8,32,45),('gravel',8,32,45),('clay_ball',8,32,50),('glass',4,16,90),('coal',4,20,120),('charcoal',4,16,120),('iron_ingot',2,8,500),('copper_ingot',4,16,160),('gold_ingot',1,4,700),('redstone',4,20,160),('lapis_lazuli',4,16,180),('quartz',4,16,180),('amethyst_shard',4,12,250),('leather',2,8,250),('string',4,16,120),('paper',8,24,70),('white_wool',2,8,250),('prismarine_shard',2,8,500),('blaze_powder',2,6,700),('ender_pearl',1,4,900),('obsidian',2,8,700),('ice',4,12,200)],
 'food': [('wheat',8,32,80),('carrot',8,32,80),('potato',8,32,80),('beetroot',8,32,80),('melon_slice',8,32,60),('pumpkin',2,8,200),('sugar_cane',8,24,80),('sweet_berries',8,32,70),('apple',2,8,180),('bread',4,16,240),('baked_potato',4,16,160),('cooked_beef',2,8,400),('cooked_porkchop',2,8,350),('cooked_chicken',2,8,280),('cooked_mutton',2,8,300),('cooked_cod',2,8,240),('cooked_salmon',2,8,280),('cookie',8,24,80),('pumpkin_pie',2,6,400),('egg',4,12,180),('sugar',4,12,150),('dried_kelp',8,32,80)],
 'hunt': [('zombie',3,12,350),('skeleton',3,10,400),('spider',3,10,300),('creeper',2,8,550),('drowned',2,8,450),('husk',2,8,400),('stray',2,8,450),('enderman',1,5,900),('blaze',2,6,850),('wither_skeleton',1,5,1000),('witch',1,4,1000),('pillager',2,6,600)]
}
for kind,entries in groups.items():
    pool={}
    for item,lo,hi,worth in entries:
        pool['tnc_'+kind+'_'+item]={'type':'entity' if kind=='hunt' else 'item','content':'minecraft:'+item,'amount':{'min':lo,'max':hi},'unitWorth':worth,'rarity':'RARE' if worth>=900 else 'UNCOMMON' if worth>=500 else 'COMMON','timeMult':2.0}
    write(RES/f'data/bountiful/bounty_pools/tnc/tnc_{kind}_objs.json',{'content':pool})
    write(RES/f'data/bountiful/bounty_pools/tnc/tnc_{kind}_rews.json',{'content':{'tnc_'+kind+'_pay':{'type':'command','content':f'tnc_bounty_reward "%PLAYER_NAME%" "{kind}" "%BOUNTY_AMOUNT%"','name':{'supply':'城镇补给报酬','food':'酒馆食材报酬','hunt':'巡猎报酬'}[kind],'icon':'tnc:copper_coin','amount':{'min':1,'max':40},'unitWorth':600,'timeMult':2.0}}})
    write(RES/f'data/bountiful/bounty_decrees/tnc/tnc_{kind}.json',{'objectives':['tnc_'+kind+'_objs'],'rewards':['tnc_'+kind+'_rews']})

serial=0; all_nodes=[]
def node(title,icon,x,y,description,deps=(),item=None,count=1,goal=None):
    global serial
    serial+=1;qid=f'544E4502{serial:08X}';tid=f'544E4503{serial:08X}'
    task={'id':tid,'type':'item','item':item,'count':count,'consume_items':False} if item else {'id':tid,'type':'advancement','advancement':'tnc:onboarding/'+goal,'criterion':'done','title':'完成真实行为'}
    if goal:write(RES/f'data/tnc/advancements/onboarding/{goal}.json',{'criteria':{'done':{'trigger':'minecraft:impossible'}}})
    q={'id':qid,'title':title,'icon':icon,'x':x,'y':y,'size':1.1,'shape':'hexagon','description':['&b&l'+title,'','&f'+description],'dependencies':list(deps),'rewards':[],'tasks':[task]};all_nodes.append(q);return q
def chapter(index,name,title,icon,quests,images=()):
    write(ROOT/f'questbook/ftbquests/chapters/tnc_play_{index:02}_{name}.snbt',{'id':f'544E4501{index:08X}','filename':f'tnc_play_{index:02}_{name}','group':'544E434755494445','title':title,'icon':icon,'order_index':-110+index,'default_hide_dependency_lines':False,'default_quest_shape':'hexagon','quest_links':[],'images':list(images),'quests':quests})

elements=['water','fire','lightning','wind','earth','light','dark'];cn=['水','火','雷','风','土','光','暗'];gems=['prismarine_shard','blaze_powder','amethyst_shard','feather','quartz','glowstone_dust','ender_pearl'];gods=['傲慢的水龙王','冠烬烈阳','审判的天穹','无拘的长风','不动的山君','不灭的晨星','吞夜的君主']
route=[];images=[]
for col,(e,c,gem,god) in enumerate(zip(elements,cn,gems,gods)):
    x=-13.5+col*4.5
    images.append({'image':f'tnc:textures/item/wands/{e}_wand_5.png','x':x,'y':-3.0,'width':2.6,'height':2.6,'rotation':0.0})
    previous=[]
    for tier in range(1,6):
        y=(tier-1)*4.2
        if tier>=2:
            m=node(c+'系介质 · '+str(tier)+'阶','minecraft:'+gem,x-1.0,y-1.4,'在对应环境收集介质。任务只检测持有；铸器师下单时才扣材料。',item='minecraft:'+gem,count=tier*2);route.append(m)
        w=node(god if tier==5 else c+'系 · '+['','冒险者法杖','精良法杖','王级法杖','传说法杖'][tier],f'tnc:{e}_wand_{tier}',x,y,f'莉娅订单：Lv{[0,1,10,25,50,85][tier]}；人工费{[0,30,250,1500,8000,50000][tier]}铜（首次一阶免费）。材料详见工坊。承载本系已学1～{tier}阶法术，含通用术。'+('光系普通战斗链尚未开放，可承载通用术；不强制学习占位领域。' if e=='light' else '升阶保留魔法石知识。'),previous,item=f'tnc:{e}_wand_{tier}');route.append(w);previous=[w['id']]
        if tier<=4 and e!='light':
            a=node(c+f'系研习 · {tier}阶','minecraft:enchanted_book',x+1.1,y+1.3,'按V学习本系至少一道该阶真实法术。亲和与学习点限制照旧；这是一条可选专精路线。',goal=f'learn_{e}_{tier}');route.append(a)
chapter(3,'elements','&b&l提升之路 · 七系法器','tnc:water_wand_5',route,images)

route=[]
materials=[('torch',32,'照亮营地'),('shield',1,'挡住第一击'),('iron_pickaxe',1,'深入矿层'),('water_bucket',1,'随身水源'),('oak_boat',1,'水路出发'),('compass',1,'记住归路'),('white_bed',1,'夜间营地'),('bread',16,'一周口粮'),('cooked_beef',8,'远行蛋白'),('golden_apple',2,'危急储备'),('diamond',4,'钻石矿脉'),('obsidian',10,'点亮地狱门'),('flint_and_steel',1,'火种'),('blaze_rod',4,'烈焰堡垒'),('ghast_tear',2,'灵魂谷的眼泪'),('nether_wart',8,'酿造储备'),('ender_pearl',8,'末影的线索'),('ender_eye',12,'寻找要塞'),('prismarine_shard',8,'海底远征'),('sponge',1,'潮水退去'),('echo_shard',2,'深暗之声'),('recovery_compass',1,'失落的坐标'),('netherite_ingot',2,'远古锻材'),('shulker_shell',2,'末地行囊'),('elytra',1,'天空归途'),('dragon_breath',1,'龙息采集'),('totem_of_undying',1,'林地战利品'),('nether_star',1,'凋零试炼')]
for i,(it,n,title) in enumerate(materials):route.append(node(title,'minecraft:'+it,-12+(i%7)*4,(i//7)*4,'为下一段旅程准备真实物资。持有检测不消耗；任务路线是可选旅行建议，不要求购买不存在的服务。',item='minecraft:'+it,count=n))
for i,(goal,title,icon) in enumerate([('entered_nether','踏入下界','minecraft:netherrack'),('entered_end','末地星空','minecraft:end_stone'),('kills_25','二十五次实战','minecraft:iron_sword'),('kills_100','百战巡猎','minecraft:diamond_sword'),('bosses_1','首领初战','tnc:boss_ingredient_1'),('bosses_8','八次首领远征','tnc:boss_ingredient_2')]):route.append(node(title,icon,-10+i*4,18,'检测生存玩家实际到达维度/参与击杀；首领目标沿用已有八类首领名单。',goal=goal))
chapter(4,'expedition','&6&l远行 · 补给与遗迹','minecraft:compass',route)
route=[]
for i,n in enumerate([1,5,10,25,50,100]):route.append(node(f'酒馆履历 · {n}次','bountiful:bounty',-10+i*4,0,'完成实际Bountiful城镇纸张交付。账户奖励只在原生结算时发放；本任务书不重复送钱。',goal=f'bounties_{n}'))
for i,n in enumerate([5,10,20,30,50,70,85,100]):route.append(node(f'冒险等级 · Lv{n}','minecraft:experience_bottle',-14+i*4,6,'通过真实讨伐与委托获得永久经验。附魔消费不会降低冒险等级。',goal=f'level_{n}'))
for i,role in enumerate(['guild','smith','broker']):route.append(node(['艾琳 · 协会接待','莉娅 · 潮生制杖屋','米洛 · 归航银行'][i],['tnc:adventure_handbook','minecraft:anvil','minecraft:map'][i],-4+i*4,11,'找到并右键对应独立NPC。Self保持原有剧情身份。',goal='met_'+role))
chapter(5,'tavern','&e&l城镇履历 · 酒馆与岗位','bountiful:bountyboard',route)
route=[]
foods=[('bread',8,'烘焙'),('baked_potato',8,'炉边'),('cooked_cod',8,'河鲜'),('cooked_salmon',8,'归渔'),('pumpkin_pie',4,'丰收'),('cookie',16,'午后'),('mushroom_stew',4,'林间'),('rabbit_stew',2,'炖锅'),('beetroot_soup',4,'田野'),('golden_carrot',4,'远行'),('honey_bottle',4,'蜂蜜')]
for i,(it,n,title) in enumerate(foods):route.append(node(title+'料理','minecraft:'+it,-10+(i%6)*4,(i//6)*4,'准备并持有料理，可自食、收进行囊或送给成年邻居。此目标不消耗料理。',item='minecraft:'+it,count=n))
for i in range(1,9):route.append(node(f'首领珍馐 · 第{i}席',f'tnc:boss_feast_{i}',-14+(i-1)*4,10,'独立首领珍料＋熟牛肉＋碗制作。每种首次品尝永久生命+2；仅持有此节点不会发放生命奖励。',item=f'tnc:boss_feast_{i}'))
for i,(it,n,title) in enumerate([('chest',4,'收纳'),('oak_planks',64,'装修用材'),('lantern',8,'暖灯'),('flower_pot',4,'窗台'),('bookshelf',6,'书房'),('painting',2,'纪念墙'),('white_bed',2,'安稳的床'),('composter',2,'庭院')]):route.append(node(title,'minecraft:'+it,-14+i*4,16,'自购空屋后自行布置。持有任务不越过产权保护，不自动替玩家装修。',item='minecraft:'+it,count=n))
chapter(6,'living','&a&l归处续篇 · 厨房与装点','tnc:food_pouch',route)
write(ROOT/'docs/town-progression-manifest.json',{'new_gameplay_nodes':serial,'bountiful_objective_entries':sum(map(len,groups.values())),'chapters':[3,4,5,6],'wand_items':35})
print('New real gameplay nodes:',serial,'native objectives:',sum(map(len,groups.values())))
