"""Only authors the new dungeon guide; never replaces player-authored chapters."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[1]
def uid(s):return format(int(hashlib.sha256(('tnc-leyline-v1:'+s).encode()).hexdigest()[:16],16)&0x7FFFFFFFFFFFFFFF,'016X')
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
rooms={
 'BRIDGE':('无形之桥','普通视界中断桥已塌，F8会显露风凝成的踏板。完整踩过各段再抵达终点；两侧灰色假路会抽空魔力。','minecraft:feather'),
 'DUAL_BRIDGE':('双界交错','石桥存在于普通视界，光桥存在于魔力视界。判断下一段再切换；每个人的碰撞独立，不会把队友脚下的桥关掉。','minecraft:quartz'),
 'FALSE_VAULT':('幻镜宝库','九只宝箱中只有一条金色活脉。F8分辨金色宝物流与黑色死气；错误开箱会惊醒拟态蟹。折光镜也能干涉幻象。','minecraft:chest'),
 'TIDE':('魔力潮汐','每五秒涌来一次潮波，观察F8浪前沿，在石柱的背风侧躲避。抵达尽头收取魔植；被击中会损失个人法力。','minecraft:prismarine_shard'),
 'ECHO':('时间回廊','饮用回声魔药并开启F8，按残影脚印顺序行走。抵达末端后用水魔法触及残影指向的石墙。','tnc:elixir_echo'),
 'LENS':('透镜寻珍','在白色底座放置折光镜，靠近镜子开启F8扫视石壁；只有瞄准处的3×3窗口显露核心。误凿超过三次核心毁坏。潜行空手左击可回收自放镜子。','tnc:mana_mirror'),
 'SUNDIAL':('影针日晷','水与风魔法拨动人工太阳，观察指针对应的四道门。F8辨认藏有宝物的门，调好方位再打开。错误方向可能放出闪灵。','minecraft:clock'),
 'FACTIONS':('双域斗场','火与冰守卫隔墙对峙。击破屏障核心让两方交战，可以趁机捕获虚弱的幸存者，而不必硬碰两军。','minecraft:blaze_powder'),
 'STOMACH':('沉睡胃袋','巨虫对高强度魔法十分敏感。找到通风口，用安宁或净化类魔药令其沉睡，再取出腹地宝物；惊醒会产生毒液。','tnc:elixir_cleanse'),
 'VINES':('饥饿魔藤','往石盆投入真实储有100M的魔力瓶，或一桶流动魔力，喂饱魔藤后通路打开，奖励可带回家的魔植种子。','tnc:flowing_mana_bucket'),
 'PUPPETS':('提线剧场','黄金魔偶的护盾来自高台地精。F8追踪暗影提线，切断三条线，或上台击败脆弱的操偶者。','minecraft:string'),
 'CHESS':('镜中棋局','水银镜像反射伤害并镜像你的脚步。利用立柱改变路线，让它走入F8显露的吸魔地漏。','minecraft:polished_blackstone'),
 'REFRACTION':('折光门扉','将折光镜摆在工程底座，以魔力计配对，绕开遮光石柱把入口光送到门锁。观察F8中的折射路径。','tnc:mana_meter'),
 'ALCHEMY':('失控药室','锅内已有药材与高温魔火。以水或冰魔法降到绿色温区，观察漩涡靠拢，黄金时机用自己携带的封魔盖封住；完成续煮后用玻璃瓶装走。','tnc:sealing_lid'),
 'MEAL':('五味封印','火与水石盘已经固定。用自制魔植产物补齐甘味中和、风和土，观察颜色联通，解除餐阵。食材不匹配不会消耗。','tnc:moon_honey_cake'),
 'MUSIC':('回声晶厅','F8观察中央护盾循环显示的三色音序，再按顺序用箭、法术或敲击触碰对应晶簇。每次探索的音序不同。','minecraft:amethyst_cluster'),
 'POLLUTION':('逆污净室','再生魔藤吸收一切纯净魔力。古怪废液可以污染池水；高淤积冒险者也能亲自进入池水净化自身并破坏藤蔓供给。','tnc:strange_waste'),
 'GRAVITY':('颠倒风暴','使用分流阀或风魔法激活逆向重力，上升到天花板寻找宝匣。重力只维持20秒，提前准备轻羽魔药。','tnc:mana_valve'),
 'RAPIDS':('魔流急湍','F8显露三层流动道路。让魔流接住你，利用移动和潜行调节路径，并以轻羽类魔药辅助换道，抵达上层平台。','tnc:elixir_feather'),
 'SCALE':('贪婪天平','右键手印后保持潜行，连续供给18秒，每秒消耗50M并增加5点淤积。提前准备法力与排毒方案，中途松手会重置。','minecraft:gold_ingot'),
 'CAULDRON':('饥饿坩埚','观察F8味泡判断它偏好甘甜生机还是火辣废液。把匹配的魔法食材投入嘴中，可换取五份臻品魔药。','tnc:spellflame_cauldron'),
 'EXHIBITION':('琥珀展馆','六件展品只够解冻两件。F8分辨宝物、受困同伴与陷阱，选择后用火魔法解冻，机会用尽无法重置。','minecraft:honey_block'),
 'PAWN':('地精典当与深泉','F8下与盲眼地精交易，将自身淤积作为筹码，每人独立结算一次。泡泉10秒获得眷顾；空桶收取原浆，带回地热核心永久升级温泉。','minecraft:water_bucket')}
themes=[('desert','黄沙遗庭','光、影、干涸与古代机关',['SUNDIAL','VINES','LENS','REFRACTION','FACTIONS','SCALE']),('crypt','锁钟地牢','回声、提线与被污染的魔脉',['PUPPETS','ECHO','POLLUTION','STOMACH','CAULDRON','PAWN']),('tide','沉潮回廊','潮汐、运输与元素温度',['TIDE','BRIDGE','RAPIDS','ALCHEMY','MEAL']),('astral','星眠幻庭','双视界、空间错觉与奇物',['DUAL_BRIDGE','FALSE_VAULT','CHESS','GRAVITY','EXHIBITION','MUSIC'])]
quests=[]
def quest(key,title,icon,x,y,desc,parent=None,adv=None):
 q=dict(id=uid(key),title=title,icon=icon,x=x,y=y,description=desc,dependencies=[uid(parent)] if parent else [],hide_dependency_lines=False,tasks=[dict(id=uid(key+'task'),type='advancement',advancement='tnc:leyline/'+adv.lower())] if adv else [dict(id=uid(key+'task'),type='checkmark')]);quests.append(q)
quest('entry','&b&l魔脉迷宫 · 两个视界的探险','tnc:dungeon_handbook',0,-4,['战斗房清场开门，奇遇房可先离开再回来，解谜成功才领取奖励。每5层保存，保留原有倒地与救援规则。','F8用于辨认供魔动脉、机关线索和隐藏实体。谜题进度由服务器保存；不要求一进门就带齐所有工具。','下面四列是各领域的代表房型。少量桥梁、宝库、乐音和典当奇遇会跨区域出现。','1—5层学习本领域基本语言，6层起加入工程与药学机关，11层起出现高风险交易和供魔挑战。','房间任务通过实际解开机关完成。背包满时奖励进入地下城邮件，腾出格子后自动重试，重连也能领取。'])
for col,(theme,title,subtitle,kinds) in enumerate(themes):
 x=(col-1.5)*6
 quest(theme,'&e&l'+title,'tnc:dungeon_handbook',x,-1,[subtitle,'对应不同的机关与怪物组合。主线通路、支线奖励分开，不会因缺少药水或工具卡死整层。'],'entry')
 for row,kind in enumerate(kinds):
  title_,desc,icon=rooms[kind];quest(kind,title_,icon,x,row*2+2,[desc,'需要补给时可退出这间奇遇，继续探索其他门。'],theme,kind);quests[-1]['hide_dependency_lines']=True
for i,(kind,title,desc) in enumerate([
 ('mimic_crystal_crab','拟态魔晶蟹','伪装宝匣，F8能看到深红核心。古怪废液可瓦解外壳；没有废液也能在醒来后战斗。'),
 ('mana_siphon_jelly','噬魔幽浮','普通视界难以察觉，会附身汲取个人法力。F8才能可靠命中，火系魔药泼向脚下可驱离。'),
 ('glare_gargoyle','强光闪灵','F8下直视胸核会短暂失明。观察线索后移开目光、利用立柱遮挡；不要全程盯住它。'),
 ('source_crystal_grub','源晶吞噬虫','它会吞吃魔力装置。压到四分之一血以下，用缩水捕捉药打开十秒窗口，拘束笼可把它带回产魔。')]):
 quest(kind,title,'tnc:'+kind+'_spawn_egg',(i-1.5)*6,17,[desc,'战斗房的母巢由灵石供能。切断供能可削弱怪物；废液与透支药水会改变母巢产物。'],'entry')
write(R/'questbook/ftbquests/chapters/tnc_leyline_dungeons.snbt',dict(id=uid('chapter'),filename='tnc_leyline_dungeons',title='&3&l魔脉迷宫 · 双视界探险',icon='minecraft:ender_eye',group='46D9BF1B84D709E1',order_index=19,default_hide_dependency_lines=False,default_quest_shape='hexagon',images=[],quests=quests))
for kind in rooms:write(R/f'src/main/resources/data/tnc/advancements/leyline/{kind.lower()}.json',dict(criteria={'solved':{'trigger':'minecraft:impossible'}}))
for lang in ['zh_cn','en_us']:
 p=R/f'src/main/resources/assets/tnc/lang/{lang}.json';v=json.loads(p.read_text(encoding='utf-8'));extra=R/'work/dungeon-ecology-lang.json'
 if extra.exists():v.update(json.loads(extra.read_text(encoding='utf-8')))
 for k,name in [('source','供魔灵石'),('nest','魔脉母巢'),('node','古代机关石座'),('veil','无形风桥'),('phase_material','物质界踏板'),('phase_aether','魔力界踏板')]:v['block.tnc.leyline_'+k]=name
 write(p,v)
print('Authored',len(quests),'quests and',len(rooms),'server-awarded advancements; existing chapters untouched.')
