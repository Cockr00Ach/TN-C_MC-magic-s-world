"""Author exaggerated full-body combo clips with readable windup, strike, follow-through and recovery."""
import json,re,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/'src/main/java/com/tnc/tnc/warrior'
def pose(root=(0,0,0),right=(0,0,0),left=(0,0,0),legs=(0,0),head=(0,0,0)):
    return dict(bone=root,armRight=right,armLeft=left,legRight=(legs[0],0,8),legLeft=(legs[1],0,-8),head=head)
ZERO={k:(0,0,0) for k in ['bone','armRight','armLeft','legRight','legLeft','head']}

def two_hand_grip(p, swing):
    # R = Rx(swing) Rz(+/-30): both wrists converge between the shoulders.
    # Convert to ModelPart's Rz Ry Rx order, preserving the large overhead swing.
    t=math.radians(swing)
    for bone,z in [('armRight',-math.pi/6),('armLeft',math.pi/6)]:
        x=math.degrees(math.atan2(math.sin(t)*math.cos(z),math.cos(t)))
        while x-swing>180:x-=360
        while x-swing< -180:x+=360
        y=math.degrees(math.asin(-math.sin(t)*math.sin(z)))
        roll=math.degrees(math.atan2(math.cos(t)*math.sin(z),math.cos(z)))
        p[bone]=(round(x,3),round(y,3),round(roll,3))
    return p
def frames(style,index,stance=False):
    sign=-1 if index%2 else 1
    if style=='spear':
        return [ZERO,pose((5,-35,0),(-75,90,35),(-95,-35,-25),(25,-25)),
            pose((8,75,-8),(-145,-100,-30),(-135,35,35),(-30,40)),
            pose((10,-80,8),(-65,115,45),(-95,-55,-35),(40,-25)),
            pose((-8,-25,-12),(-150,-55,15),(-155,35,-15),(-35,30)),
            # One-handed extension: support hand visibly releases for the longest thrust.
            pose((32,20,8),(-100,5,-8),(25,15,-20),(60,-55)),
            pose((12,50,0),(-65,-55,55),(-110,45,-25),(-20,30)),
            pose((0,0,0),(-95,-20,0),(-115,35,-15)) if stance else ZERO]
    if style=='scythe':
        turns=720 if index in (1,4,5) else 360
        return [ZERO,pose((-12,-45,15),(-160,-60,55),(-140,35,-25),(-35,40)),
            pose((24,90,-20),(-85,70,90),(-110,15,55),(55,-45)),
            pose((35,240,15),(-65,-85,-75),(-100,-30,-50),(-40,50)),
            pose((-15,turns-110,-15),(-190,45,35),(-175,-25,-25),(25,-40)),
            pose((42,turns,10),(15,-65,-55),(-30,25,30),(65,-60)),
            pose((20,turns+20,0),(-40,50,80),(-60,20,60),(-25,35)),
            pose((0,turns,0),(-90,-15,0),(-115,30,0)) if stance else pose((0,turns,0))]
    if style=='bow':
        out=[dict(ZERO),pose((-15,-30,-18),(-65,-35,45),(-100,20,-20),(40,-35)),
            pose((-25,-55,-25),(-110,-70,75),(-90,5,-10),(65,-60)),
            pose((-18,-65,-15),(-100,-95,85),(-90,0,-5),(35,-20)),
            pose((-15,-70,-10),(-95,-110,90),(-90,0,-5),(25,-15)),
            pose((15,-30,25),(-55,-25,65),(-85,10,-25),(-55,65)),
            pose((5,20,5),(-20,10,45),(-65,20,-15),(20,-15)),dict(ZERO)]
        for i,p in enumerate(out):
            if i in (0,len(out)-1):continue
            drawing=p['armRight'];support=p['armLeft']
            p['armRight']=(support[0],-p['bone'][1],-support[2])
            p['armLeft']=(drawing[0],drawing[1],-drawing[2])
            p['head']=(0,-p['bone'][1],0)
        return out
    if style in ('heavy','hammer'):
        high=index in (1,4) or style=='hammer'
        return [ZERO,pose((-12,-50,-12),(-110,-55,30),(-125,40,-20),(-25,35)),
            pose((-25,-65,-18),(-190,-20,-25),(-180,35,25),(-45,50)) if high else pose((0,-100,-15),(-85,-100,65),(-95,60,-35),(-45,40)),
            pose((-30,-45,-10),(-205,-10,-15),(-195,30,20),(-40,50)) if high else pose((5,-125,-10),(-95,-115,75),(-105,55,-40),(-40,45)),
            pose((25,20,15),(-35,70,-55),(-55,35,45),(55,-45)),
            pose((40,85,20),(30,100,-75),(15,55,-35),(65,-60)),
            pose((20,100,5),(-10,70,-35),(-30,40,-20),(25,-20)),
            pose((5,0,0),(-105,-15,-20),(-115,35,15)) if stance else ZERO]
    return [ZERO,pose((-8,-40*sign,5),(-140,-65*sign,45*sign),(-60,25,-25),(-25,30)),
        pose((-15,-75*sign,10),(-175,-90*sign,65*sign),(-80,35,-40),(-40,45)),
        pose((5,-35*sign,0),(-120,-40*sign,35*sign),(-55,20,-25),(-20,30)),
        pose((24,65*sign,-15),(-20,75*sign,-70*sign),(30,-20,40),(55,-45)),
        pose((15,110*sign,-10),(20,90*sign,-85*sign),(15,-10,35),(30,-20)),
        pose((8,55*sign,0),(-35,20,25),(-35,10,-20),(-15,25)),
        pose((0,0,0),(-105,-20,0),(-100,30,-15)) if stance else ZERO]

# Named choreography, independent of enum/map iteration order. Rotations are
# deliberately unwrapped: a return to 360/720 is a completed turn, not a rewind.
YAW={
 'SWORD_BREAKER':[0,-35,-75,-65,25,110,35,0],
 'SWORD_SPIN':[0,-45,40,180,340,550,690,720],
 'SWORD_STANCE':[0,20,45,35,10,-15,-10,0],
 'SWORD_COUNTER':[0,55,100,65,-35,-150,-55,0],
 'SWORD_RETREAT':[0,25,65,100,40,-90,-35,0],
 'SWORD_SWALLOW':[0,-50,-110,-80,70,240,345,360],
 'GS_SWEEP':[0,-35,-105,-125,-50,80,110,0],
 'GS_SMASH':[0,-20,-40,-45,-15,45,25,0],
 'GS_WALL':[0,30,55,45,5,-35,-20,0],
 'GS_STANCE':[0,-15,-35,-30,-10,10,15,0],
 'GS_RIFT':[0,20,55,70,30,-55,-35,0],
 'GS_ADVANCE':[0,45,90,65,-30,-115,-45,0],
 'HM_CRUSH':[0,-50,-85,-65,30,115,70,0],
 'HM_QUAKE':[0,10,20,15,0,-15,-10,0],
 'HM_CUT':[0,20,45,35,-15,-45,-10,0],
 'HM_STANCE':[0,-25,-45,-35,5,20,10,0],
 'HM_RUIN':[0,45,110,180,245,335,360,360],
 'HM_DRIVE':[0,-30,-65,-40,15,65,30,0],
 'SPEAR_BREAK':[0,-40,80,-75,-20,25,55,0],
 'SPEAR_DRAGON':[0,-60,105,-110,-35,20,70,0],
 'SPEAR_RETURN':[0,35,110,200,255,340,360,360],
 'SPEAR_GUARD':[0,20,-45,55,15,-25,-10,0],
 'SPEAR_FLOW':[0,30,85,155,190,120,30,0],
 'SCYTHE_POUNCE':[0,-60,-100,-40,90,290,350,360],
 'SCYTHE_REAP':[0,35,135,265,390,550,690,720],
 'SCYTHE_HOOK':[0,70,150,100,-10,190,335,360],
 'SCYTHE_GUARD':[0,-30,35,135,220,300,350,360],
 'SCYTHE_EXECUTE':[0,-45,110,310,610,720,740,720],
 'SCYTHE_WHIRL':[0,50,180,325,470,605,710,720],
 'BOW_AIM':[0,-15,-30,-40,-40,-20,10,0],
 'BOW_DASH_SHOT':[0,-35,-85,-110,-60,20,45,0],
 'BOW_BACK_SHOT':[0,20,45,60,35,-20,-40,0],
 'BOW_PIERCE':[0,-20,-50,-70,-70,-25,15,0],
 'BOW_STRAFE':[0,45,100,130,95,30,-25,0],
 'BOW_VOLLEY':[0,-35,-100,-190,-275,-335,-360,-360],
}

def signature(animation,style,poses):
    poses=[dict(p) for p in poses]
    if animation in YAW:
        for p,y in zip(poses,YAW[animation]):p['bone']=(p['bone'][0],y,p['bone'][2])
    # Heavy choreography preserves two-handed grip while changing the actual
    # weapon path: dragging low, reverse rising cut, brace, or overhead impact.
    swings={
      'GS_SWEEP':[-65,-55,-50,-85,-135,-105],
      'GS_WALL':[-75,-95,-100,-85,-65,-85],
      'GS_STANCE':[-80,-105,-110,-110,-105,-100],
      'GS_RIFT':[-35,-20,-15,-100,-205,-160],
      'GS_ADVANCE':[-45,-25,-30,-95,-155,-105],
      'HM_QUAKE':[-130,-190,-210,-165,35,10],
      'HM_CUT':[-60,-95,-110,-80,-50,-80],
      'HM_STANCE':[-55,-70,-80,-80,-75,-70],
      'HM_RUIN':[-70,-150,-205,-150,35,-30],
      'HM_DRIVE':[-35,-55,-70,-100,-140,-85]}
    if animation in swings:
        for i,s in enumerate(swings[animation],1):two_hand_grip(poses[i],s)
        if animation.endswith('STANCE'):two_hand_grip(poses[-1],swings[animation][-1])
    if animation=='SWORD_SWALLOW':
        poses[3]=pose((-22,-80,22),(-190,70,40),(-120,-45,-60),(65,-55))
        poses[4]=pose((35,70,-25),(-40,-80,-95),(35,25,55),(-60,70))
    if animation=='SWORD_RETREAT':
        poses[2]=pose((20,65,-15),(-70,90,-35),(-30,-25,65),(-55,60))
        poses[4]=pose((-18,40,20),(-180,-40,80),(-65,45,-40),(50,-35))
    if animation=='SPEAR_FLOW':
        # Turn away, look back over shoulder, then a single-hand returning thrust.
        poses[3]=pose((-15,155,-18),(-55,100,70),(-110,-65,-30),(-55,45),(0,-100,0))
        poses[5]=pose((38,120,15),(-105,-100,-15),(35,45,-35),(70,-65),(0,-95,0))
    if animation=='SPEAR_GUARD':
        for i in (2,3,4,5,6):two_hand_grip(poses[i],[-85,-105,-95,-70,-90][i-2])
    if animation=='SCYTHE_HOOK':
        poses[2]=pose((30,150,-20),(-35,110,85),(-95,-40,-55),(60,-50))
        poses[3]=pose((38,100,25),(15,75,65),(-65,-35,40),(-60,55))
        poses[4]=pose((-20,-10,-15),(-165,-90,-80),(-140,45,35),(45,-35))
    if animation=='SCYTHE_REAP':
        for i in (2,3,4,5):
            p=poses[i];p['bone']=(42,p['bone'][1],(-1 if i%2 else 1)*20)
            p['armRight']=(-45,p['armRight'][1],75 if i%2 else -75)
    if animation=='SCYTHE_POUNCE':
        poses[2]=pose((-30,-100,-15),(-205,-40,30),(-180,30,-25),(-55,60))
        poses[4]=pose((45,90,20),(20,60,-80),(-40,-35,45),(75,-65))
    if style=='bow' and animation!='BOW_CHARGE':
        lean={'BOW_AIM':4,'BOW_DASH_SHOT':25,'BOW_BACK_SHOT':-30,'BOW_PIERCE':12,'BOW_STRAFE':32,'BOW_VOLLEY':-22}[animation]
        for i in range(1,7):
            p=poses[i];yaw=p['bone'][1]
            p['bone']=(p['bone'][0],yaw,lean*(1 if i<5 else -.5))
            p['armRight']=(p['armRight'][0],-yaw,p['armRight'][2])
            p['head']=(0,-yaw,0)
        if animation=='BOW_BACK_SHOT':
            poses[2]['legRight']=(-75,0,15);poses[2]['legLeft']=(-55,0,-15)
    return poses

def readable_profile(animation,poses):
    main={'SWORD_BREAKER':8,'GS_SMASH':18,'HM_QUAKE':30,'BOW_AIM':-8,'SPEAR_BREAK':22,'SCYTHE_EXECUTE':35}
    guards={'SWORD_STANCE':-95,'GS_STANCE':-105,'HM_STANCE':-75,'BOW_PIERCE':-85,'SPEAR_GUARD':-100,'SCYTHE_GUARD':-90}
    breakers={'SWORD_COUNTER':-75,'GS_WALL':-90,'HM_CUT':-65,'BOW_STRAFE':-80,'SPEAR_RETURN':-100,'SCYTHE_HOOK':-85}
    if animation in main:
        # One readable load, one attack, one follow-through. No flourish or full turn.
        for i,p in enumerate(poses):
            pitch=p['bone'][0];p['bone']=(pitch,[0,-15,-25,-25,5,30,15,0][i],p['bone'][2]*.35)
        if animation=='SPEAR_BREAK':
            for i in (1,2,3):two_hand_grip(poses[i],-115)
            poses[4]['armRight']=(-100,0,0);poses[4]['armLeft']=(25,25,-25)
        if animation=='SCYTHE_EXECUTE':
            for i,s in enumerate([-100,-160,-190,-200,-55,25,-45],1):two_hand_grip(poses[i],s)
            poses[-1]=pose()
    if animation in guards:
        grip=guards[animation]
        for i in range(1,8):
            poses[i]=two_hand_grip(pose((5,[-10,-15,-15,-5,0,0,0][i-1],0),legs=(10,-10)),grip)
    if animation in breakers:
        grip=breakers[animation]
        for i in range(1,7):
            poses[i]=two_hand_grip(pose(([0,-5,-8,24,30,10][i-1],[10,20,20,0,-10,0][i-1],0),legs=(-15,15) if i<4 else (40,-30)),grip+([10,5,0,15,30,10][i-1]))
        poses[-1]=pose()
    return poses
def main():
    enum=(J/'WarriorAnimation.java').read_text(encoding='utf8')
    durations={a:int(b) for a,b in re.findall(r'^\s*(\w+)\((\d+)\)[,;]',enum,re.M)}
    loader=(J/'client/WarriorComboAnimations.java').read_text(encoding='utf8')
    names={animation:clip for clip,animation in re.findall(r'Map.entry\("([^"]+)", WarriorAnimation\.(\w+)\)',loader)}
    clips={}
    for animation,name in names.items():
        style='scythe' if animation.startswith('SCYTHE') else 'spear' if animation.startswith('SPEAR') else 'heavy' if animation.startswith('GS') else 'hammer' if animation.startswith('HM') else 'bow' if animation.startswith('BOW') else 'sword'
        index=list(names).index(animation)
        stance='STANCE' in animation or 'GUARD' in animation
        if style in ('heavy','hammer'): index=1 if animation.endswith(('SMASH','RIFT','CRUSH','QUAKE','RUIN')) else 0
        if style=='scythe': index=1 if animation.endswith(('REAP','WHIRL')) else 4 if animation.endswith('EXECUTE') else 0
        poses=[dict(p) for p in frames(style,index,stance)]
        if style in ('heavy','hammer'):
            swings=[-110,-190,-205,-35,30,-10,-105] if index else [-110,-145,-160,-65,-35,-75,-105]
            for i in range(1,7):two_hand_grip(poses[i],swings[i-1])
            if stance:two_hand_grip(poses[-1],swings[-1])
        if style=='spear':
            if animation=='SPEAR_RETURN':
                for i,p in enumerate(poses):p['bone']=(p['bone'][0],(0,35,100,190,240,340,360,360)[i],p['bone'][2])
            elif animation=='SPEAR_FLOW':
                for p in poses:p['bone']=(-p['bone'][0]*.7,p['bone'][1]*-.7,p['bone'][2])
            elif stance:
                poses[5]=pose((8,20,0),(-100,-35,10),(-120,45,-15),(25,-25))
        if animation=='SCYTHE_EXECUTE':
            poses[4]=pose((-30,610,-10),(-210,-20,-30),(-195,35,25),(-50,55))
            poses[5]=pose((45,720,10),(25,60,-60),(10,35,40),(65,-60))
        if animation=='SWORD_SPIN':
            for i,p in enumerate(poses): p['bone']=(p['bone'][0],round(720*i/(len(poses)-1)),p['bone'][2])
        if animation=='BOW_CHARGE':
            poses=frames('bow',0)[:5];poses[-1]=frames('bow',0)[4]
        poses=signature(animation,style,poses)
        poses=readable_profile(animation,poses)
        if animation in ('SWORD_CHARGE','GS_CHARGE','HM_CHARGE','SPEAR_CHARGE'):
            swing={'SWORD_CHARGE':-145,'GS_CHARGE':-190,'HM_CHARGE':-200,'SPEAR_CHARGE':-115}[animation]
            poses=[dict(ZERO),two_hand_grip(pose((-5,-15,0),legs=(-10,15)),swing*.7),two_hand_grip(pose((-15,-25,-5),legs=(-25,30)),swing)]

        times=[0,.10,.22,.34,.46,.58,.78,1] if len(poses)==8 else [0,.20,.45,.75,1] if len(poses)==5 else [0,.35,1]
        # Ease into held anticipation and out of recovery; strikes retain a short
        # fast passage. Bake samples so runtime remains compatible with linear tracks.
        keys=list(zip(times,poses));baked=[]
        for (a,pa),(b,pb) in zip(keys,keys[1:]):
            for step in range(4):
                f=step/4; eased=f*f*(3-2*f)
                baked.append((a+(b-a)*f,{bone:tuple(pa[bone][j]+(pb[bone][j]-pa[bone][j])*eased for j in range(3)) for bone in ZERO}))
        baked.append(keys[-1]);times,poses=zip(*baked)
        length=durations[animation]/20
        clips[name]={'animation_length':length,'bones':{bone:{'rotation':{str(round(t*length,5)):list(p[bone]) for t,p in zip(times,poses)}} for bone in ZERO}}
    out=ROOT/'src/main/resources/assets/tnc/animations/entity/warrior_performance.json'
    out.write_text(json.dumps({'format_version':'1.8.0','animations':clips},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('Authored',len(clips),'full-body clips')
if __name__=='__main__':main()
