"""Articulated overlord arms; deterministic UV packing of the authored material source.

The torso/legs and their UVs are preserved from the committed baseline. Rotation
signs follow GeckoLib 4's Bedrock loader (X/Y are negated on import).
"""
import copy, json, math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'model-source/dark_giant_upperbody'
ASSETS=ROOT/'src/main/resources/assets/tnc'
data=json.loads((SOURCE/'base_dark_giant.geo.json').read_text(encoding='utf-8-sig'))
geo=data['minecraft:geometry'][0]
anim=json.loads((SOURCE/'base_dark_giant.animation.json').read_text(encoding='utf-8-sig'))
atlas=Image.new('RGBA',(2048,2048))
atlas.paste(Image.open(SOURCE/'base_dark_giant_bedrock.png').convert('RGBA').resize((1024,1024),Image.Resampling.LANCZOS),(0,0))
glow=Image.new('RGBA',atlas.size)
glow.paste(Image.open(SOURCE/'base_dark_giant_emissive.png').convert('RGBA').resize((1024,1024),Image.Resampling.NEAREST),(0,0))
materials=Image.open(SOURCE/'materials.png').convert('RGBA')
tiles=[materials.crop((x*384,y*384,(x+1)*384,(y+1)*384)) for y in range(2) for x in range(2)]
cursor=[1028,4,0]

def paint_face(w,h,material,index,rune=False):
    # Actual-sized individual face islands, with aspect-correct borders and hardware.
    w=max(4,int(math.ceil(w*3)));h=max(4,int(math.ceil(h*3)))
    if cursor[0]+w>2044:cursor[0]=1028;cursor[1]+=cursor[2]+4;cursor[2]=0
    if cursor[1]+h>2044:raise ValueError('Upper-body UV atlas exhausted')
    x,y=cursor[:2];cursor[0]+=w+4;cursor[2]=max(cursor[2],h)
    source=tiles[material];sx=(index*43)%160;sy=(index*29)%160
    patch=source.crop((sx,sy,sx+224,sy+224)).resize((w,h),Image.Resampling.LANCZOS)
    draw=ImageDraw.Draw(patch);edge=(111,114,137,255) if material!=1 else (54,43,64,255)
    if w>12 and h>12:
        draw.rectangle((1,1,w-2,h-2),outline=edge,width=1)
        draw.line((2,h-3,w-3,h-3),fill=(16,13,25,255),width=1)
        if material!=1:
            for px,py in [(4,4),(w-5,4),(4,h-5),(w-5,h-5)]:draw.rectangle((px,py,px+1,py+1),fill=(169,146,101,255))
    atlas.paste(patch,(x,y))
    if rune and w>16 and h>16:
        # A narrow emission mask shares the exact new UV island, never a mismatched atlas.
        ink=ImageDraw.Draw(atlas);light=ImageDraw.Draw(glow);cx=x+w//2;cy=y+h//2
        path=[(cx,y+int(h*.18)),(cx-3,cy-3),(cx+4,cy+2),(cx,y+int(h*.82))]
        ink.line(path,fill=(165,68,220,255),width=2);light.line(path,fill=(147,43,225,210),width=2)
        for side in [-1,1]:
            branch=[(cx,cy),(cx+side*max(3,w//5),cy-h//7)]
            ink.line(branch,fill=(147,53,210,255));light.line(branch,fill=(147,43,225,185))
    return {'uv':[x/2,y/2],'uv_size':[w/2,h/2]}

index=0
def cube(origin,size,material=0,rotation=None,rune=False):
    global index
    index+=1;w,h,d=size
    faces={}
    for name,a,b in [('north',w,h),('south',w,h),('east',d,h),('west',d,h),('up',w,d),('down',w,d)]:
        faces[name]=paint_face(a,b,material,index,rune and name=='north')
    out={'origin':origin,'size':size,'uv':faces}
    if rotation:
        out['pivot']=[round(o+s/2,4) for o,s in zip(origin,size)];out['rotation']=rotation
    return out

bones=[b for b in geo['bones'] if not b['name'].startswith(('arm','pauldron'))]
new_names=[]
def bone(name,parent,pivot,cubes=None):
    b={'name':name,'parent':parent,'pivot':pivot}
    if cubes:b['cubes']=cubes
    bones.append(b);new_names.append(name);return b

for side,sign in [('right',-1),('left',1)]:
    x=24*sign
    bone('shoulder'+side,'body',[x,67,0])
    bone('arm'+side,'shoulder'+side,[x,67,0],[
        cube([x-5.7,47,-6.7],[11.4,19,13.4],1),
        cube([x-5.1,50,-8.2],[10.2,13,3.1],0),
        cube([x-5.2,50,5.8],[10.4,13,2.6],0),
        cube([x+sign*5.3-1.2,49,-5.5],[2.4,13,11],3,[0,0,-sign*7]),
    ])
    bone('armforearm'+side,'arm'+side,[x,46,0],[
        cube([x-5.7,43.3,-5.7],[11.4,5.4,11.4],1),
        cube([x-6.1,31,-6.6],[12.2,13,13.2],0),
        cube([x-5.5,33,-9.4],[11,10,3.2],2,rune=True),
        cube([x-7,29.7,-7.4],[14,3.6,14.8],3),
        cube([x+sign*6.1-1,33,-5.9],[2,10,11.8],3,[0,0,-sign*9]),
    ])
    bone('armhand'+side,'armforearm'+side,[x,29.5,-.5],[
        cube([x-5.3,21.5,-4.8],[10.6,8.8,8.4],1),
        cube([x-5.7,22.8,-6.5],[11.4,6.2,2.4],2,rune=True),
        cube([x-5.8,21,-4.7],[11.6,2.2,6.4],3),
    ])
    for digit in range(4):
        fx=x-3.9+digit*2.6;length=4 if digit in (1,2) else 3.4
        name=f'finger_{side}_{digit}'
        bone(name,'armhand'+side,[fx,22,-2],[cube([fx-1.08,22-length,-4.1],[2.16,length,3.7],0)])
        bone(name+'_tip',name,[fx,22-length,-2],[cube([fx-1,18.8-length,-3.8],[2,3.2,3.4],3)])
    thumb='thumb_'+side
    bone(thumb,'armhand'+side,[x-sign*5,27,-1],[cube([x-sign*6.5-1.4,22,-3.6],[2.8,5,4],0,[0,sign*12,-sign*24])])
    bone(thumb+'_tip',thumb,[x-sign*6.5,22.3,-1],[cube([x-sign*6.5-1.3,19,-3.4],[2.6,3.3,3.6],3)])
    # Three overlapping shoulder shells: articulation is carried by the scapula,
    # not rigidly tied to the full humerus swing.
    bone('pauldron'+side,'shoulder'+side,[x,67,0],[
        cube([x-8.5,69,-10],[17,7,20],3,[0,0,-sign*9]),
        cube([x-9.3,65,-9.4],[18.6,5.6,18.8],0,[0,0,-sign*15]),
        cube([x-8.3,61.5,-8.5],[16.6,4.5,17],3,[0,0,-sign*18]),
        cube([x-6,68.5,-11.1],[12,5.4,2],2,rune=True),
        cube([x+sign*7.5-1.3,74,-5.3],[2.6,7,3.4],0,[-10,0,-sign*26]),
        cube([x+sign*6-1.1,74,2],[2.2,5.5,3],3,[7,0,-sign*19]),
    ])
geo['bones']=bones
geo['description'].update(texture_width=1024,texture_height=1024,visible_bounds_width=10,visible_bounds_height=9,visible_bounds_offset=[0,3.5,0])

REST={'body':[0,0,0],'head':[0,0,0],'cape':[4,0,0]}
for side,sign in [('right',1),('left',-1)]:
    REST.update({'shoulder'+side:[-2,0,sign*2],'arm'+side:[-3,0,sign*6],
        'armforearm'+side:[-12,0,0],'armhand'+side:[3,0,sign*2],'pauldron'+side:[0,0,0]})
    for digit in range(4):
        REST[f'finger_{side}_{digit}']=[-10,0,0];REST[f'finger_{side}_{digit}_tip']=[-18,0,0]
    REST['thumb_'+side]=[-12,0,0];REST['thumb_'+side+'_tip']=[-15,0,0]

def pose(right,left,elbows,body=(3,0,0),wrists=(0,0,0),open_hand=True):
    p=copy.deepcopy(REST);p.update(body=list(body),head=[-body[0]*.4,-body[1]*.6,0])
    for side,sign,upper,elbow in [('right',1,right,elbows[0]),('left',-1,left,elbows[1])]:
        p['arm'+side]=list(upper);p['armforearm'+side]=[elbow,0,0]
        p['shoulder'+side]=[max(-10,upper[0]*.06),0,sign*(2+abs(upper[2])*.08)]
        p['armhand'+side]=list(wrists)
        for digit in range(4):
            p[f'finger_{side}_{digit}']=[-3 if open_hand else -48,0,0]
            p[f'finger_{side}_{digit}_tip']=[-5 if open_hand else -62,0,0]
    return p

def track(values):
    out={}
    for i,(t,v) in enumerate(values):
        if i:
            a,av=values[i-1]
            for j in range(1,5):
                f=j/4;s=f*f*(3-2*f)
                out[str(round(a+(t-a)*f,4))]=[round(x+(y-x)*s,4) for x,y in zip(av,v)]
        else:out[str(t)]=v
    return out

def casting(seconds,prepare,release):
    clips={}
    for name in REST:
        p=prepare.get(name,REST[name]);r=release.get(name,REST[name])
        clips[name]={'rotation':track([(0,REST[name]),(.3,REST[name]),(seconds*.65,p),(seconds-.18,p),(seconds,r),(seconds+.12,r),(round(seconds+.6,4),REST[name])])}
    return {'loop':False,'animation_length':round(seconds+.6,4),'bones':clips}

poses={
 'cast_hands':(2.2,pose([-53,-6,12],[-28,4,-14],[-42,-52],(2,-4,0),(5,0,0)),pose([-85,0,9],[-32,3,-13],[-7,-42],(6,3,0),(-4,0,0))),
 'cast_sky_hands':(2.8,pose([-136,-3,14],[-136,3,-14],[-23,-23],(-2,0,0)),pose([-54,0,12],[-54,0,-12],[-34,-34],(9,0,0),(-6,0,0))),
 'cast_mist':(2.6,pose([-32,-6,16],[-32,6,-16],[-54,-54],(3,0,0)),pose([-30,8,43],[-30,-8,-43],[-20,-20],(-2,0,0),(5,0,0))),
 'cast_siphon':(2.2,pose([-75,-3,10],[-75,3,-10],[-12,-12],(4,0,0)),pose([-32,2,12],[-32,-2,-12],[-78,-78],(-2,0,0),(4,0,0),False)),
 'command_army':(2.8,pose([-69,-7,16],[-36,8,-25],[-30,-48],(-1,0,0)),pose([-88,0,12],[-33,4,-15],[-8,-32],(4,-4,0))),
}
for name,(seconds,p,r) in poses.items():anim['animations'][name]=casting(seconds,p,r)
# Legacy triggers retain their names/durations, but share the articulated upper rig.
for old,new in [('playmagic','cast_hands'),('playmagicsummonandsmogy','command_army'),('treadon','cast_mist')]:
    clip=anim['animations'][old];seconds=clip['animation_length']-.6
    arms=casting(seconds,poses[new][1],poses[new][2])['bones']
    clip['bones'].update(arms)
idle=anim['animations']['idle'];walk=anim['animations']['walk']
for name,value in REST.items():
    idle['bones'][name]={'rotation':track([(0,value),(2,[value[0]+(.5 if name in ('body','armright','armleft') else 0),value[1],value[2]]),(4,value)])}
    if name!='body' and name!='cape':walk['bones'][name]={'rotation':value}
idle['bones']['body'].pop('scale',None)
for side,phase in [('right',0),('left',math.pi)]:
    for name,amp,base in [('arm',9,-3),('armforearm',3,-14),('armhand',1.5,3),('shoulder',1,-2)]:
        sign=1 if side=='right' else -1;z=REST[name+side][2]
        walk['bones'][name+side]={'rotation':{str(round(i*2.2/24,4)):[round(base+amp*math.cos(i*math.tau/24+phase),4),0,z] for i in range(25)}}
for path,obj in [(ASSETS/'geo/entity/dark_giant.geo.json',data),(ASSETS/'animations/entity/dark_giant.animation.json',anim)]:
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
atlas.save(ASSETS/'textures/entity/dark_giant_bedrock.png',optimize=True)
glow.save(ASSETS/'textures/entity/dark_giant_emissive.png',optimize=True)
print('Articulated bones:',len(bones),'new upper-body parts:',index,'atlas:',atlas.size,'packed bottom:',cursor[1]+cursor[2])
