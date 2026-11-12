from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]; java=root/'src/main/java/com/tnc/tnc/warrior'
# Each stroke is authored independently: S sweep, T thrust, R reverse, O orbit, H overhead, D draw-cut, V rising cut, B bow.
rows=[
('longsword','七星交锋','STRTS','交剑换线、回锋反切'),
('greatsword','王庭断章','SVHSH','高位架剑、横断接重劈'),('hammer','千钧天落','HHOVH','沉腰抡锤、三叠震地'),
('spear','游龙贯日','STTRT','拨杆进身、长线贯刺'),('dragon_sword','龙游九霄','VOSTH','挑剑引龙、盘旋斩落'),
('bow','天穹连珠','BBTBB','错步开弓、连珠贯空'),('moon_bow','月轮坠星','BOVBB','侧身挽月、扇面齐射'),
('storm_bow','雷隼逐影','BTBRB','换步连射、雷隼穿心'),('ember_bow','烬羽焚天','BHBVB','低弓引火、上挑焚羽'),
('frost_katana','雪尽无声','DRDSD','藏锋拔斩、反手纳刃'),('star_rapier','星芒礼赞','TRTTV','拨剑换线、弓步复刺'),
('crimson_warblade','赤潮断岳','SOSHH','缠头重扫、拖刀断岳'),('doom_scythe','冥月归墟','ROVOR','钩收旋镰、回环收割'),
('earthshaker_hammer','地脉天崩','HVHOH','挑锤沉身、地脉三震'),('sky_halberd','苍穹天衡','VTSOH','刺挑横挂、斩落天衡'),
('yue_bronze_jian','越女照水','TRSVT','点刺洗剑、撩腕归中'),('willow_leaf_dao','柳浪千叠','DSORS','缠头裹脑、云刀横断'),
('crescent_guandao','青龙偃月','OSHVH','拖刀提撩、回身劈山'),('fangtian_ji','辕门落日','TSRHO','月牙钩挂、刺劈回旋'),
('red_tassel_spear','百鸟朝凤','RTTST','拦拿连扎、回马贯日'),('ring_pommel_dao','朔风破军','HSDRS','朴直劈砍、进步截腰'),
('han_iron_jian','汉阙长歌','SVTRS','挂剑撩锋、横截归鞘'),('iron_jian_mace','金声裂石','RHVHH','架砸挑压、双落破甲'),
('monk_spade','禅月渡厄','ORHVS','铲推月挑、横封开道'),('qimei_staff','六合盘龙','SOTRO','舞花换把、扫挑点扎'),
('bronze_greatsword','古鼎镇山','HOSVH','横剑压阵、鼎势镇地'),('bronze_spear','玄鸟穿云','TVTRT','长杆截拿、玄鸟贯云'),
('bronze_dao','铜雀回风','SDOSR','卷刃绕肩、回风截步'),('bronze_dagger','寸芒惊鸿','DTRTD','贴身换腕、反握挑刺'),
('bronze_halberd','双阙合璧','RTOSH','双刃交错、合戟开天'),('wind_relic_spear','岚脉归一','TSVTT','拦风挑势、定点穿岚')]
profiles=[]; animations={}
poses={'S':(-90,-105,30,16),'T':(-94,4,0,20),'R':(-85,105,-32,-18),'O':(-100,-140,60,0),'H':(-15,-12,10,28),'D':(-65,-110,65,8),'V':(-160,60,-20,-12),'B':(-90,-70,0,-8)}
for idx,(id,name,strokes,text) in enumerate(rows):
    # 10 taps, distinct per weapon. Fixed lead-in avoids the shortest standard chains.
    n=idx; tail=''
    for _ in range(4):tail='qez'[n%3]+tail;n//=3
    keys='qqeezq'+tail
    times=[26,38+(idx%3),52+(idx%2),66+(idx%4),82]
    phases=[dict(tick=t,shape=s,yaw=([-24,28,-12,18,0][i] if s!='O' else 0),radius=(18 if s=='B' else 7 if s=='T' else 5.5 if s=='O' else 6),multiplier=(2.6 if i==4 else .85)) for i,(t,s) in enumerate(zip(times,strokes))]
    profiles.append(dict(id=id,name=name,keys=keys,description=text,strokes=phases))
    tracks={b:{} for b in ['bone','head','armRight','armLeft','legRight','legLeft']}
    def frame(t,body,head,right,left,legs):
        for b,v in zip(tracks,[body,head,right,left,legs,[-legs[0],-legs[1],-legs[2]]]):tracks[b][str(round(t/20,3))]=v
    frame(0,[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0])
    frame(8,[-4,-18,0],[9,18,0],[-55,-40,25],[-40,25,-15],[12,8,0])
    frame(18,[-6,-24,0],[-8,24,0],[-125,-15,20],[-70,30,-15],[18,8,0])
    for i,(t,s) in enumerate(zip(times,strokes)):
        x,y,z,lean=poses[s]; hand= -1 if i%2 else 1
        frame(t-4,[-6,-y*.40,0],[-4,y*.2,0],[-155,-y*.50,hand*25],[-105,20,-20],[22,8,0])
        frame(t,[lean,y*.38,hand*4],[-6,-y*.2,0],[x,y,z],[-85 if s!='B' else -92,y*.40,-25],[32 if s=='T' else 18,hand*12,0])
        frame(t+3,[lean*.65,y*.45,0],[0,-y*.25,0],[x+8,y*1.08,z],[-70,y*.45,-30],[18,6,0])
    frame(96,[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0])
    animations[id+'_ultimate']={'animation_length':4.8,'bones':{b:{'rotation':v} for b,v in tracks.items()}}
(root/'src/main/resources/assets/tnc/warrior/ultimates.json').write_text(json.dumps(profiles,ensure_ascii=False,indent=2),encoding='utf-8')
animations['ULTIMATE']=animations['longsword_ultimate']
(root/'src/main/resources/assets/tnc/animations/entity/warrior_ultimates.json').write_text(json.dumps({'format_version':'1.8.0','animations':animations},ensure_ascii=False,indent=2),encoding='utf-8')
