"""Author weapon-specific wushu-inspired body tracks and authoritative move specs.

Game adaptations of techniques, not reconstructions of a named traditional school.
Shared keys/unlocks remain the existing unified combat layout.
"""
import json
from pathlib import Path
from traditional_weapons import ROOT

# slot order: attack, held parry, parry breaker, specialties.
PROFILES = [
 ('yue_bronze_jian','LONGSWORD',2.8,False,[('进步直刺','thrust'),('提剑截腕','guard'),('压剑破架','press'),('挂撩穿刺','liao'),('退步点剑','retreat'),('剪腕回刺','wrist')]),
 ('willow_leaf_dao','LONGSWORD',2.9,False,[('弓步劈刀','chop'),('抱刀护身','guard'),('压刀进斩','press'),('缠头裹脑','wrap'),('退步反撩','retreat'),('云刀横斩','cloud')]),
 ('crescent_guandao','GREATSWORD',3.8,True,[('拖刀上劈','heavy'),('横刀拦门','guard'),('沉刀压架','press'),('拦腰大斩','sweep'),('回身拖劈','wrap'),('提刀斩落','chop')]),
 ('fangtian_ji','SPEAR',3.8,True,[('中平戟刺','thrust'),('横戟架挡','guard'),('月牙压架','press'),('勾挂回斩','hook'),('回身扫戟','sweep')]),
 ('red_tassel_spear','SPEAR',4.0,True,[('拦拿扎枪','spear'),('中平拦枪','guard'),('崩枪破架','press'),('进步穿枪','thrust'),('回马扎枪','return')]),
 ('ring_pommel_dao','LONGSWORD',2.9,False,[('进身斜劈','chop'),('立刀格挡','guard'),('刀背压架','press'),('连环劈斩','double'),('撤步截刀','retreat'),('翻腕上撩','liao')]),
 ('han_iron_jian','LONGSWORD',3.1,False,[('弓步穿刺','thrust'),('立剑截击','guard'),('沉腕压剑','press'),('左右挂撩','liao'),('撤步回刺','retreat'),('云剑点刺','wrist')]),
 ('iron_jian_mace','HAMMER',2.65,False,[('沉身劈锏','heavy'),('横锏护门','guard'),('短打破架','press'),('左右连打','double'),('回身砸锏','chop'),('提锏下压','heavy')]),
 ('monk_spade','GREATSWORD',3.7,True,[('推铲进击','thrust'),('横铲封门','guard'),('铲面压架','press'),('月牙勾挂','hook'),('转身平扫','sweep'),('抡铲下劈','heavy')]),
 ('qimei_staff','SPEAR',3.5,True,[('劈棍接戳','staff'),('横棍架挡','guard'),('压棍破架','press'),('立舞花棍','flower'),('转身低扫','sweep')]),
]

# Explicit staged arm/root routes. Each tuple is (phase, arm XYZ, body yaw, pitch).
ROUTES = {
 'thrust':[(0,(-52,0,12),0,0),(.24,(-78,-28,16),-20,-8),(.5,(-96,0,0),12,14),(.7,(-90,8,0),8,10),(1,(-52,0,12),0,0)],
 'guard':[(0,(-50,0,10),0,0),(.3,(-110,-20,48),-12,0),(1,(-106,-12,40),-8,2)],
 'press':[(0,(-54,0,10),0,0),(.3,(-125,-25,38),-18,-4),(.58,(-75,18,18),22,16),(1,(-54,0,10),0,0)],
 'chop':[(0,(-48,0,8),0,0),(.25,(-158,-28,15),-35,-9),(.58,(-32,25,-28),42,18),(.8,(-22,12,-20),28,10),(1,(-48,0,8),0,0)],
 'heavy':[(0,(-55,0,12),0,0),(.36,(-172,-18,8),-25,-14),(.62,(-24,18,-12),38,25),(.84,(-18,6,-8),20,17),(1,(-55,0,12),0,0)],
 'sweep':[(0,(-62,0,15),0,0),(.25,(-86,-78,16),-65,-4),(.62,(-83,85,-20),145,16),(.85,(-66,25,-12),200,8),(1,(-62,0,15),0,0)],
 'liao':[(0,(-50,0,10),0,0),(.22,(-25,-48,-32),-35,8),(.48,(-130,22,38),30,-10),(.72,(-92,0,0),10,12),(1,(-50,0,10),0,0)],
 'wrist':[(0,(-58,0,12),0,0),(.2,(-105,-42,58),-18,0),(.42,(-78,45,-50),22,4),(.65,(-110,-32,36),-15,2),(.82,(-92,0,0),12,14),(1,(-58,0,12),0,0)],
 'wrap':[(0,(-55,0,12),0,0),(.22,(-155,-68,72),-45,-8),(.44,(-175,62,-55),35,-5),(.7,(-38,65,-24),110,17),(1,(-55,0,12),0,0)],
 'cloud':[(0,(-55,0,12),0,0),(.22,(-150,-62,58),-40,-6),(.48,(-150,68,-52),45,-6),(.72,(-80,75,-20),95,8),(1,(-55,0,12),0,0)],
 'double':[(0,(-54,0,12),0,0),(.2,(-140,-48,35),-40,-6),(.4,(-35,48,-28),40,15),(.58,(-120,58,-35),48,-5),(.78,(-32,-48,28),-40,15),(1,(-54,0,12),0,0)],
 'hook':[(0,(-60,0,12),0,0),(.25,(-95,55,-20),45,10),(.52,(-58,-40,30),-45,-7),(.76,(-82,65,-25),65,12),(1,(-60,0,12),0,0)],
 'spear':[(0,(-60,0,12),0,0),(.2,(-82,-32,16),-20,0),(.4,(-84,28,-14),18,2),(.68,(-96,0,0),4,18),(.83,(-92,0,0),2,12),(1,(-60,0,12),0,0)],
 'return':[(0,(-60,0,12),0,0),(.28,(-70,70,-20),135,-5),(.54,(-95,0,0),180,15),(.76,(-85,-40,20),245,5),(1,(-60,0,12),360,0)],
 'staff':[(0,(-60,0,12),0,0),(.24,(-155,-30,20),-25,-8),(.46,(-30,25,-16),30,18),(.64,(-70,-20,15),-15,2),(.82,(-95,0,0),8,15),(1,(-60,0,12),0,0)],
 'flower':[(0,(-60,0,12),0,0),(.2,(-150,-55,65),-30,-5),(.4,(-28,45,-50),30,10),(.6,(-145,55,-60),40,-6),(.8,(-28,-50,45),-30,12),(1,(-60,0,12),0,0)],
 'retreat':[(0,(-55,0,12),0,0),(.25,(-65,-45,30),-25,-15),(.5,(-130,35,-25),25,-8),(.72,(-90,0,0),12,12),(1,(-55,0,12),0,0)],
}


def clip(kind,twohand,flavor):
    tracks={k:{'rotation':{}} for k in ('bone','head','armRight','armLeft','legRight','legLeft')}
    for phase,arm,yaw,pitch in ROUTES[kind]:
        key=str(round(phase,4));f=1 if phase in (0,1) else 1+(flavor-4)*.025
        r=[round(v*f,3) for v in arm]
        # The support arm remains on the shaft; sword off-hand stays in sword-finger balance.
        l=[r[0]+10,-r[1]*.65,-r[2]*.7] if twohand else [-28-pitch*.5,-18,28+yaw*.06]
        vals={'bone':[pitch,yaw,0],'head':[-pitch*.4,-yaw*.15,0], 'armRight':r,'armLeft':l,
              'legRight':[-pitch*1.2,0,-4], 'legLeft':[pitch*.75,0,5]}
        for bone,angles in vals.items():tracks[bone]['rotation'][key]=[round(v,3) for v in angles]
    return {'animation_length':1,'bones':tracks}


def main():
    assets=ROOT/'src/main/resources/assets/tnc';profiles={};animations={};rows=[]
    for flavor,(id,family,reach,twohand,moves) in enumerate(PROFILES):
        entries=[]
        for slot,(name,kind) in enumerate(moves):
            arc=35 if kind in ('thrust','spear','return') else 230 if kind in ('sweep','cloud','flower') else 110
            radius=3 if slot in (1,2) else reach
            dash=0 if slot in (0,1) else .25 if slot==2 else 1.8 if kind in ('thrust','spear') else .55
            text=name+'：'+{'thrust':'收势送尖，步身与前刺同步','spear':'枪尖外拦、内拿，收枪后中平送扎','guard':'提起兵器封住中线，按住末段保持架势','press':'贴近中线压下兵器后短促前顶，专破振刀','sweep':'转腰带动横向大扫，覆盖宽扇面','flower':'立圆左右舞花，接棍端横击','wrap':'贴肩绕背过刀，转腰接劈斩','cloud':'举刀平云后转腰横斩','liao':'先挂开兵器，再由下向上撩起接刺','wrist':'腕花换刃，点刺收锋','retreat':'撤步避锋，回身截击','return':'转身收枪再回扎','staff':'先抡劈，再收把平戳','hook':'月牙勾挂回收，再翻刃横带','double':'左右换势连打后收势','chop':'提起兵器蓄势，转腰斜劈收落','heavy':'沉腰提器，踏实下劈，留出重击回收'}[kind]
            entries.append(dict(name=name,kind=kind,radius=radius,arc=arc,dash=dash,text=text))
            animations[id+'_'+str(slot)]=clip(kind,twohand,flavor)
            rows.append(f'| {id} | {slot+1} | {name} | {kind} | {arc}° | {radius} |')
        profiles[id]=dict(family=family,moves=entries)
        for suffix,kind in [('q','liao' if family=='LONGSWORD' else 'chop'),('e','press'),('z','thrust' if id=='red_tassel_spear' else 'sweep')]:
            animations[id+'_basic_'+suffix]=clip(kind,twohand,flavor)
    path=assets/'warrior';path.mkdir(exist_ok=True)
    (path/'traditional_martial_arts.json').write_bytes((json.dumps(profiles,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    (assets/'animations/entity/traditional_martial_arts.json').write_bytes((json.dumps(dict(format_version='1.8.0',animations=animations),ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    (ROOT/'model-source/traditional-weapons/martial-moves.md').write_bytes(('# 十种传统兵器招式表\n\n槽位 1–6 的输入：QZE / EQE（末 E 长按）/ EQZ / QEZ / ZQE / ZZQ。枪类五槽。\n\n| 武器 ID | 槽位 | 招式 | 动作类型 | 扇面 | 半径 |\n|---|---:|---|---|---:|---:|\n'+'\n'.join(rows)+'\n').encode('utf8'))
    print(f'Built {sum(len(p["moves"]) for p in profiles.values())} named moves and {len(animations)} full-body clips')


if __name__=='__main__':main()
