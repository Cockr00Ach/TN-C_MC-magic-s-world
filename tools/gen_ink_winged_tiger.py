"""Build a rigged four-legged ink tiger using the existing editable grayscale material atlas."""
import json, math, shutil, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
INK=(0,0);GRAY=(64,0);ASH=(96,64);WHITE=(128,0);EYE=(192,64)
bones=[]
def cube(o,s,mat=INK,r=None,p=None):
    c={'origin':list(o),'size':list(s),'uv':{f:{'uv':list(mat),'uv_size':[8,8]} for f in ('north','south','east','west','up','down')}}
    if r:c.update(rotation=list(r),pivot=list(p or o))
    return c
def bone(n,parent='body',p=(0,0,0),cs=None):
    b={'name':n,'pivot':list(p)}
    if parent:b['parent']=parent
    if cs:b['cubes']=cs
    bones.append(b);return b
def build():
    bone('root',None);bone('body','root',(0,28,0),[
        cube((-13,19,-22),(26,21,43)),cube((-11,17,-15),(22,6,31),GRAY),
        cube((-15,23,-18),(30,16,15)),cube((-11,23,16),(22,15,12))])
    bone('chest','body',(0,32,-20),[cube((-14,23,-31),(28,20,17)),cube((-10,21,-34),(20,10,7),ASH)])
    bone('neck','chest',(0,36,-29),[cube((-11,30,-39),(22,17,16)),cube((-9,29,-42),(18,8,8),GRAY)])
    # Tapered, overlapping skull volumes instead of a single rectangular head.
    bone('head','neck',(0,40,-38),[
        cube((-8,35,-48),(16,14,17)),cube((-10.7,36,-46),(21.4,11.5,12)),
        cube((-6.3,48,-44),(12.6,3.1,10)),cube((-7.5,35,-35),(15,13,6.5)),
        cube((-3.6,35,-55),(7.2,11,9)),cube((-2,40,-55.6),(4,6.5,1.1),GRAY)])
    bone('jaw','head',(0,32,-45),[
        cube((-7.5,26.8,-54.7),(15,4.2,11),INK),cube((-5,25.7,-52),(10,2,8),GRAY),
        cube((-5.4,29,-58),(10.8,1,4),GRAY),cube((-3.7,30.7,-58.6),(7.4,.8,2.5))])
    eyes=[]
    for s in (-1,1):
        # Inner corners are physically lower than outer corners: a sharp hostile V, not sad brows.
        for i in range(3):
            eyes.append(cube((s*(4.5+i*1.8)-.95,41.6+i*.7,-52.5),(1.9,.85,.6),EYE))
        eyes.append(cube((s*6.3-.22,42.1,-53),(.44,1.45,.35),INK))
    bone('glow_eyes','head',(0,43,-50),eyes)
    bone('nose','head',(0,36,-58),[cube((-4.3,35.9,-60),(8.6,3.2,2.8)),
        cube((-2.7,34.5,-60.4),(5.4,2.2,2.1)),cube((-2,38.3,-60.1),(4,.45,.6),GRAY),
        cube((-.4,31,-59.8),(.8,4,1.2))])
    for side,sgn in [('l',-1),('r',1)]:
        bone('temple_'+side,'head',(sgn*9,43,-43),[
            cube((sgn*9-2.6,36.5,-47),(5.2,11,12),INK,(0,sgn*24,-sgn*8),(sgn*9,43,-43)),
            cube((sgn*8-2,47,-44),(4,3.6,9),GRAY,(0,sgn*18,sgn*16),(sgn*8,47,-43))])
        bone('brow_'+side,'head',(sgn*6.5,45,-50),[
            cube((sgn*(4.5+i*1.8)-1.45,42.55+i*.7,-53.15),(2.9,2.5,3.8),INK) for i in range(3)])
        bone('snarl_fold_'+side,'head',(sgn*8,37,-55),[
            cube((sgn*8-1,35.8,-55.8),(2,5,1),GRAY,(0,0,-sgn*23),(sgn*8,38,-55))])
        bone('muzzle_'+side,'head',(sgn*5.5,33.5,-55.5),[
            cube((sgn*5.5-3.9,30.3,-57.5),(7.8,6.7,7),ASH,(0,sgn*12,sgn*8),(sgn*5.5,34,-55)),
            cube((sgn*5.5-3.1,32.2,-59),(6.2,3.5,3),GRAY,(0,sgn*8,-sgn*8),(sgn*5.5,34,-55)),
            cube((sgn*5.5-2.4,29.7,-56),(4.8,2.2,5),GRAY),
            cube((sgn*7-2.6,33.5,-59.4),(5.2,.9,.85),INK,(0,0,-sgn*16),(sgn*7,34,-58))])
        bone('ear_'+side,'head',(sgn*9.5,48.5,-35.5),[
            cube((sgn*9.5-3.1,47.6,-38),(6.2,5.8,5),INK,(0,0,-sgn*16),(sgn*9.5,49,-36)),
            cube((sgn*9.5-2,52,-37.5),(4,1.8,3.8),INK),
            cube((sgn*9.5-1.5,49,-38.4),(3,3.2,.55),ASH)])
        bone('fang_'+side,'head',(sgn*7,32,-55),[
            cube((sgn*7-1.4,29,-57.5),(2.8,4,2.8),WHITE,(-8,0,-sgn*5),(sgn*7,32,-55)),
            cube((sgn*7-.9,26.8,-58.2),(1.8,3,2),WHITE,(-14,0,-sgn*4),(sgn*7,29,-57)),
            cube((sgn*7-.4,25.8,-58.7),(.8,1.5,1.2),WHITE)])
        bone('lower_fang_'+side,'jaw',(sgn*7,30,-53),[
            cube((sgn*7-.8,30,-54),(1.6,3.3,1.8),WHITE,(-10,0,sgn*7),(sgn*7,30,-53))])
        for tooth in range(3):
            bone(f'incisor_{side}_{tooth}','jaw',(sgn*(1.5+tooth*1.6),30,-57),[
                cube((sgn*(1.5+tooth*1.6)-.55,30.5,-57.8),(1.1,1.8,1.3),WHITE)])
        for i in range(5):
            bone(f'cheek_{side}_{i}','head',(sgn*12,35+i*2,-41),[
                cube((sgn*13-2,31+i*2,-43+i),(4,7,9),GRAY if i%2 else INK,(12,sgn*15,-sgn*(15+i*4)),(sgn*12,37,-40))])
        for i in range(4):
            bone(f'whisker_{side}_{i}','head',(sgn*8,33,-55),[
                cube((sgn*8-(12+i*2 if sgn<0 else 0),32.5-i*.5,-57-i*.3),(12+i*2,.35,.45),WHITE,
                     (0,sgn*(12+i*6),sgn*(i-1)*7),(sgn*8,33,-55))])
        for i in range(11):
            z=-20+i*4
            for j in range(3):
                bone(f'stripe_{side}_{i}_{j}','body',(sgn*13,30,z),[
                    cube((sgn*13-.35,24+j*4,z),( .7,5,1.6),WHITE,(0,0,sgn*(18-i%3*13)),(sgn*13,32,z))])
        for i in range(10):
            bone(f'ruff_{side}_{i}','chest',(sgn*13,36,-23+i*.5),[
                cube((sgn*13-1.6,30-i*.7,-29+i*1.2),(3.2,8,6),ASH if i%4==0 else INK,
                     (-18,sgn*16,-sgn*(15+i)),(sgn*13,36,-24))])
    for i in range(7):
        bone('forehead_ink_'+str(i),'head',(0,48,-46),[
            cube((-7+i*2.2,46.5,-49.4),(1.2,3.4,.65),ASH,(0,0,(-1 if i<3 else 1)*24),(0,48,-49))])
    for leg,z in [('fore',-19),('hind',18)]:
        for side,s in [('l',-1),('r',1)]:
            name=leg+'_'+side;x=s*10
            bone(name,'body',(x,29,z),[cube((x-5,12,z-5),(10,20,12)),cube((x-4,14,z-6),(8,11,3),GRAY)])
            bone(name+'_shin',name,(x,14,z),[cube((x-3.5,3,z-4),(7,13,8)),cube((x-4,4,z-4.3),(8,3,1),WHITE)])
            bone(name+'_paw',name+'_shin',(x,4,z-3),[cube((x-5,0,z-10),(10,5,13)),cube((x-4,3.8,z-9),(8,.6,9),GRAY)])
            for claw in range(4):
                bone(name+'_claw_'+str(claw),name+'_paw',(x-3.6+claw*2.4,2,z-9),[
                    cube((x-4+claw*2.4,.6,z-13),(1.4,2,5),WHITE,(-12,0,0),(x,2,z-9)),
                    cube((x-3.7+claw*2.4,.5,z-14),( .8,1,2),WHITE)])
            for k in range(3):
                bone(name+'_mark_'+str(k),name,(x,20,z),[cube((x-5.1,18+k*3,z-5),(.6,1.4,11),ASH)])
    parent='body'
    for i in range(9):
        p=(math.sin(i*.5)*5,26+i*.65,24+i*5)
        bone('tail_'+str(i),parent,p,[cube((p[0]-2.3,p[1]-2.3,p[2]),(4.6,4.6,7),INK),
            cube((p[0]-2.5,p[1]-2.5,p[2]+2),(5,5,1.2),WHITE if i%2==0 else GRAY)])
        parent='tail_'+str(i)
    bone('tail_brush',parent,(p[0],p[1],p[2]+6),[cube((p[0]-4,p[1]-3,p[2]+4),(8,6,13)),
        cube((p[0]-2,p[1]-1,p[2]+15),(4,2,9),ASH)])
    bone('emitter_tail','tail_brush',(p[0],p[1],p[2]+23))
    for side,s in [('l',-1),('r',1)]:
        w='wing_'+side
        bone(w,'body',(s*12,37,-13),[cube((min(s*12,s*43)-2,35,-19),(35,5,13)),
            cube((min(s*14,s*42),39,-17),(28,1.2,9),ASH)])
        bone(w+'_fore',w,(s*42,38,-8),[cube((min(s*41,s*70),36,-11),(30,3.5,9)),
            cube((min(s*44,s*69),39,-10),(24,.7,6),WHITE)])
        for i in range(15):
            x=s*(29+i*3.1);length=35-i*.7;z=-8+i*.9
            name=f'{w}_primary_{i}'
            bone(name,w+'_fore' if i>5 else w,(x,37,z),[
                cube((x-2.1,35,z),(4.2,2.6,length),INK,(0,s*(i-5)*2.6,s*7),(x,37,z)),
                cube((x-1.5,37.2,z+3),(3,.55,length-5),GRAY,(0,s*(i-5)*2.6,s*7),(x,37,z)),
                cube((x-.3,37.8,z+6),(.6,.4,length-6),WHITE,(0,s*(i-5)*2.6,s*7),(x,37,z))])
        for row in range(3):
            for i in range(10):
                x=s*(15+i*4.8);z=-15+row*6
                bone(f'{w}_covert_{row}_{i}',w,(x,40,z),[
                    cube((x-2,39+row*.3,z),(4,.9,12+row*2),INK,(0,s*(i-3)*2,0),(x,40,z)),
                    cube((x-.3,40+row*.3,z+3),(.6,.3,8),ASH)])
        bone('emitter_'+w,w+'_fore',(s*78,38,27))
    # Bake the requested 2x wing size into editable geometry, including joints and emitters.
    for b in bones:
        if not (b['name'].startswith('wing_') or b['name'].startswith('emitter_wing_')):continue
        sign=-1 if b['name'].startswith('wing_l') or b['name']=='emitter_wing_l' else 1
        anchor=(sign*12,37,-13)
        transform=lambda p:[anchor[i]+(p[i]-anchor[i])*2 for i in range(3)]
        b['pivot']=transform(b['pivot'])
        for c in b.get('cubes',[]):
            c['origin']=transform(c['origin']);c['size']=[v*2 for v in c['size']]
            if 'pivot' in c:c['pivot']=transform(c['pivot'])
    # A separate articulated folded silhouette overlaps its long feathers along the flanks.
    # Open wings keep their full doubled size for flight, spells and the fivefold apparition.
    for side,s in [('l',-1),('r',1)]:
        w='folded_wing_'+side
        bone(w,'body',(s*14,36,-16),[
            cube((s*15-2,32,-20),(4,8,22),INK,(-8,0,0),(s*14,36,-16)),
            cube((s*16-1.7,29,-8),(3.4,7,25),INK),
            cube((s*16-1.9,35,-15),(3.8,.55,25),GRAY)])
        for i in range(15):
            x=s*(15.2+i*.24);y=33-i*.65;z=-18+i*.9;length=34-i*.65
            bone(f'{w}_feather_{i}',w,(x,y,z),[
                cube((x-.65,y-.55,z),(1.3,1.1,length),INK,(-4,0,s*(i%3-1)*2),(x,y,z)),
                cube((x+s*.48-.12,y+.02,z+3),(.24,.3,length-4),GRAY if i%3 else ASH)])
        bone('emitter_folded_'+side,w,(s*18,24,24))
    return bones

def animations():
    idle={'body':{'rotation':['math.sin(query.anim_time*100)*1.3',0,0]}}
    walk={}
    for n in ('fore_l','fore_r','hind_l','hind_r'):
        sign=1 if n in ('fore_l','hind_r') else -1
        walk[n]={'rotation':[f'math.sin(query.anim_time*450)*{sign*24}',0,0]}
    for s,sign in [('l',-1),('r',1)]:
        idle['folded_wing_'+s]={'rotation':['math.sin(query.anim_time*100)*.6',0,0]}
        walk['folded_wing_'+s]={'rotation':['math.sin(query.anim_time*230)*1.2',0,0]}
    for i in range(9):idle['tail_'+str(i)]={'rotation':[0,f'math.sin(query.anim_time*110-{i*18})*5',0]}
    walk={**idle,**walk}
    def action(kind):
        pose={'body':[-8,0,0],'neck':[-8,0,0],'jaw':[25,0,0]}
        if kind=='claw':pose.update(fore_l=[-70,0,-15],fore_r=[-55,0,15])
        if kind=='pounce':pose.update(body=[-12,0,0],fore_l=[-65,0,0],fore_r=[-65,0,0],hind_l=[55,0,0],hind_r=[55,0,0])
        if kind=='roar':pose.update(neck=[-20,0,0],head=[-18,0,0],jaw=[36,0,0])
        for s,sign in [('l',-1),('r',1)]:pose['wing_'+s]=[-20,sign*5,sign*(-15 if kind=='pounce' else 4)]
        bs={n:{'rotation':{'0.0':[0,0,0],'.35':v,'1.4':v,'2.3':[0,0,0]}} for n,v in pose.items()}
        return {'animation_length':2.4,'bones':bs}
    def keys(times):return {'rotation':times}
    sky={'body':keys({'0.0':[0,0,0],'1.0':[-12,0,0],'4.2':[-12,0,0],'5.9':[0,0,0]}),
         'jaw':keys({'0.0':[0,0,0],'2.7':[8,0,0],'3.1':[38,0,0],'3.7':[8,0,0],'5.9':[0,0,0]})}
    phantom={'neck':keys({'0.0':[0,0,0],'.6':[-22,0,0],'1.3':[-22,0,0],'3.9':[0,0,0]}),
             'jaw':keys({'0.0':[0,0,0],'.7':[30,0,0],'2.0':[22,0,0],'3.9':[0,0,0]})}
    roar={'neck':keys({'0.0':[0,0,0],'1.0':[-44,0,0],'4.4':[-44,0,0],'4.8':[0,0,0],'7.4':[0,0,0]}),
          'head':keys({'0.0':[0,0,0],'1.0':[-25,0,0],'4.4':[-25,0,0],'4.8':[0,0,0]}),
          'jaw':keys({'0.0':[0,0,0],'.8':[42,0,0],'4.4':[42,0,0],'4.8':[12,0,0],'7.4':[0,0,0]})}
    ghost={'body':{'rotation':[-12,0,0]},'jaw':{'rotation':[28,0,0]},
           'fore_l':{'rotation':[-65,0,0]},'fore_r':{'rotation':[-65,0,0]},
           'hind_l':{'rotation':[40,0,0]},'hind_r':{'rotation':[40,0,0]}}
    for s,sign in [('l',-1),('r',1)]:
        sky['wing_'+s]={'rotation':[-12,sign*4,f'math.sin(query.anim_time*680)*{sign*24}']}
        sky['wing_'+s+'_fore']={'rotation':[0,sign*8,f'math.sin(query.anim_time*680-35)*{sign*12}']}
        phantom['wing_'+s]=keys({'0.0':[-14,sign*17,sign*42],'.9':[-18,sign*3,-sign*16],'2.5':[-18,sign*3,-sign*16],'3.9':[-14,sign*17,sign*42]})
        roar['wing_'+s]=keys({'0.0':[-14,sign*17,sign*42],'1.0':[-22,sign*3,-sign*26],'4.5':[-22,sign*3,-sign*26],'4.9':[0,0,0],'6.7':[0,0,0],'7.4':[-14,sign*17,sign*42]})
        ghost['wing_'+s]={'rotation':[-20,sign*4,-sign*12]}
    return {'format_version':'1.8.0','animations':{
        'idle':{'loop':True,'animation_length':4,'bones':idle},
        'walk':{'loop':True,'animation_length':2,'bones':walk},**{n:action(n) for n in ('claw','pounce','roar')},
        'sky_spit':{'animation_length':6,'bones':sky},
        'phantom_cast':{'animation_length':4,'bones':phantom},
        'ink_roar':{'animation_length':7.5,'bones':roar},
        'ghost_charge':{'loop':True,'animation_length':2,'bones':ghost}}}

if __name__=='__main__':
    texture=ASSETS/'textures/entity/ink_winged_tiger.png'
    if not texture.exists():shutil.copyfile(ASSETS/'textures/entity/ink_abyss_drake.png',texture)
    bs=build()
    geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{
        'identifier':'geometry.tnc.ink_winged_tiger','texture_width':256,'texture_height':256,
        'visible_bounds_width':28,'visible_bounds_height':16,'visible_bounds_offset':[0,5,1]},'bones':bs}]}
    for path,data in [(ASSETS/'geo/entity/ink_winged_tiger.geo.json',geo),
                      (ASSETS/'animations/entity/ink_winged_tiger.animation.json',animations())]:
        if '--animations-only' in sys.argv and '/geo/' in path.as_posix():continue
        path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    print(f'Ink winged tiger: {len(bs)} bones, {sum(len(b.get("cubes",[])) for b in bs)} cubes; existing monochrome drake material reused.')
