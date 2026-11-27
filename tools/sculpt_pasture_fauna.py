"""Build rest-pose pasture sculptures for the existing Minecraft skeletons and export editable meshes."""
from __future__ import annotations
import argparse
import base64
import io
import json
import math
from pathlib import Path
import re
import uuid
from collections import defaultdict
from itertools import pairwise
from PIL import Image
import numpy as np

from generate_pasture_models import PALETTES, GLOW

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main'
LIFE = MAIN / 'java/com/tnc/tnc/life'
ASSETS = MAIN / 'resources/assets/tnc'
ATLAS = 128
TILE = 20
PALETTES.update({
    'iris': ('396c78','26344a','71c7c5','e0fff1'),
    'bronze': ('ad7a44','654939','dba862','f6d597'),
    'pearl': ('d3c8da','736b97','eee4e8','fff6e2'),
    'wool': ('d3c29f','998e82','eee2ca','fff4df'),
    'bell_horn': ('527d79','354751','8aa8a1','c8d8c3'),
    'sheep_hide': ('ac9e84','716b63','cfc2a2','eadcbd'),
    'prism_hide': ('beb9a1','787d71','e1ddc4','fbf4d6'),
    'prism': ('53a79b','505780','a8e2c8','f7f2be'),
    'sand_fur': ('a76d45','664637','dbad74','f7d39c'),
    'ink': ('333953','202234','566184','8b96ac')})
GLOW = GLOW | {'prism'}
# Selective saturation lets the luminous tissue remain the highest-value landmark.
for key in ('whale_skin','mirror_rim','moth_body','lizard_skin','toad_skin','marten_fur','hare_fur','strider_hide','ray_body'):
    colors=np.asarray([list(bytes.fromhex(h)) for h in PALETTES[key]],float)
    colors=.78*colors+.22*colors.mean(axis=1,keepdims=True)
    PALETTES[key]=tuple(bytes(c.astype('uint8')).hex() for c in colors)

def numbers(s):
    return [float(n.strip().rstrip('Ff')) for n in s.split(',') if n.strip()]

def parse_parts(source):
    parts=[]
    legbar=re.search(r'CubeListBuilder legs\s*=\s*([^;]+);',source)
    pattern=r'(\w+)\.addOrReplaceChild\("(\w+)"\s*,\s*([^;]*?)\s*,\s*(PartPose\.(?:ZERO|offset(?:AndRotation)?\([^)]*\)))\s*\)\s*;'
    for parent,name,builder,pose in re.findall(pattern,source):
        a=numbers(pose.split('(')[1][:-1]) if '(' in pose else [0,0,0]
        builder=legbar[1] if builder.strip()=='legs' else builder
        boxes=[]
        for uv,b in re.findall(r'texOffs\(([^)]*)\)\s*\.addBox\(([^)]*)\)',builder):
            vals=numbers(b)
            boxes.append(dict(position=vals[:3],size=vals[3:],uv=numbers(uv)))
        a += [0.]*(6-len(a))
        parts.append(dict(name=name,parent=parent,pivot=a[:3],rotation=a[3:],boxes=boxes))
    return parts

def sources():
    models={f.stem:json.loads(f.read_text(encoding='utf8'))['parts'] for f in sorted((ROOT/'art/pasture/models').glob('*.json'))}
    assert len(models)==23
    sheep=parse_parts((LIFE/'fauna/client/BellwoolSheepModel.java').read_text('utf8').split('createBodyLayer() {')[1].split('return LayerDefinition')[0])
    for p in sheep:
        for b in p['boxes']:
            uv=b['uv']
            b['material']=('wool' if p['name']=='fleece' else 'bronze' if p['name']=='bell' else 'bell_horn' if uv==[32,0] else 'hoof' if 'leg' in p['name'] else 'sheep_hide')
    models['bellwool_sheep']=sheep
    routes=(LIFE/'pasture/client/RoutePastureModels.java').read_text('utf8')
    for id_,method in [('prismatic_antelope','antelope'),('drumbelly_otter','otter')]:
        source=routes.split('LayerDefinition '+method+'(){')[1].split('return LayerDefinition.create')[0]
        ps=parse_parts(source)
        for p in ps:
            for b in p['boxes']:
                name=p['name']
                b['material']='prism' if name.startswith('horn') else 'prism_hide' if method=='antelope' else 'sand_fur'
        for name,x,y,z,h in re.findall(r'leg\(root,"(\w+)",(-?\d+),(\d+),(-?\d+),(\d+)\)',source):
            h=int(h)
            ps.append(dict(name=name,parent='root',pivot=[int(x),int(y),int(z)],rotation=[0]*3,boxes=[
                dict(position=[-1,0,-1],size=[2,h,2],material='prism_hide' if method=='antelope' else 'sand_fur'),
                dict(position=[-1,h-2,-2],size=[2,2,3],material='hoof')]))
        models[id_]=ps
    return models

class Surface:
    def __init__(self):
        self.polys=[]
    def face(self,mat,points,uv):
        points=[tuple(round(float(c),3) for c in pt) for pt in points]
        if np.linalg.norm(np.cross(np.subtract(points[1],points[0]),np.subtract(points[2],points[0])))<.0002:return
        self.polys.append((mat,points,uv))
    def loft(self,material,axis,sections,sides=8,exponent=1):
        """Station order is along the named axis. Other coordinates follow cyclic XYZ."""
        dim='xyz'.index(axis); a=(dim+1)%3; b=(dim+2)%3
        if sections[-1][0]<sections[0][0]:sections=sections[::-1]
        rings=[]
        for pos,u,v,ru,rv in sections:
            ring=[]
            for i in range(sides):
                theta=(i+.5)*math.tau/sides
                cs,sn=math.cos(theta),math.sin(theta)
                pt=[0.,0.,0.];pt[dim]=pos
                pt[a]=u+ru*math.copysign(abs(cs)**exponent,cs);pt[b]=v+rv*math.copysign(abs(sn)**exponent,sn)
                ring.append(pt)
            rings.append(ring)
        for j,(r,t) in enumerate(pairwise(rings)):
            y0=j/(len(rings)-1);y1=(j+1)/(len(rings)-1)
            for i in range(sides):
                k=(i+1)%sides; u0=i/sides;u1=(i+1)/sides
                self.face(material,[r[i],r[k],t[k],t[i]],[(u0,y0),(u1,y0),(u1,y1),(u0,y1)])
        for j,ring in [(0,rings[0]),(-1,rings[-1])]:
            center=np.mean(ring,axis=0)
            for i in range(sides):
                k=(i+1)%sides
                p1,p2=(ring[k],ring[i]) if j==0 else (ring[i],ring[k])
                self.face(material,[center,p1,p2],[(.5,.5),(.22,.19),(.78,.19)])
    def rounded(self,material,xyz,size,axis=None,shape='tissue'):
        axis=axis or 'xyz'[int(np.argmax(size))]
        dim='xyz'.index(axis);u=(dim+1)%3;v=(dim+2)%3
        c=[q+s/2 for q,s in zip(xyz,size)]
        profile={'tissue':[(0,.61),(.15,.94),(.53,1),(.84,.95),(1,.59)],
                 'soft':[(0,.30),(.16,.82),(.45,1),(.78,.92),(1,.31)],
                 'hard':[(0,.83),(.12,1),(.86,1),(1,.8)],
                 'prism':[(0,.37),(.25,1),(.80,.80),(1,.23)]}[shape]
        sides=8 if max(size)>5 else 6
        power=.70 if shape=='hard' else .9
        self.loft(material,axis,[(xyz[dim]+size[dim]*d,c[u],c[v],a*size[u]/2,a*size[v]/2) for d,a in profile],sides,power)
    def blade(self,mat,root,tip,width,normal=(0,-1,0),ridge=.25):
        """A double-sided, creased lamina with a swept tip."""
        start=np.array(root,float);end=np.array(tip,float)
        direct=end-start;direct/=np.linalg.norm(direct)
        n=np.array(normal,float);side=np.cross(direct,n);side/=np.linalg.norm(side)
        n=np.cross(side,direct);n/=np.linalg.norm(n)
        stations=[(0,.14),(.28,.85),(.60,1),(.82,.66),(1,.025)]
        top=[];bottom=[]
        for t,r in stations:
            center=start+(end-start)*t; w=width*r
            top.append([center-side*w,center+n*ridge*r,center+side*w])
            bottom.append([center-side*w,center-n*ridge*.5*r,center+side*w])
        for side_index,rings in enumerate((top,bottom)):
            for j,(a,b) in enumerate(pairwise(rings)):
                for k in (0,1):
                    pts=[a[k],a[k+1],b[k+1],b[k]]
                    uv=[(k/2,stations[j][0]),((k+1)/2,stations[j][0]),((k+1)/2,stations[j+1][0]),(k/2,stations[j+1][0])]
                    if side_index==1:pts=pts[::-1];uv=uv[::-1]
                    self.face(mat,pts,uv)

def shelf(subject):
    materials=sorted({m for bone in subject.values() for m,_,_ in bone.polys})
    assert len(materials)<=36
    return {m:(2+i%6*TILE,2+i//6*TILE) for i,m in enumerate(materials)}

def paint(materials):
    image=Image.new('RGBA',(ATLAS,ATLAS)); glow=Image.new('RGBA',image.size)
    for mat,(x,y) in materials.items():
        cols=[np.array(list(bytes.fromhex(h)),float) for h in PALETTES[mat]]
        base,shade,lit,spark=cols; seed=sum(map(ord,mat))
        for j in range(TILE):
            for i in range(TILE):
                u=i/(TILE-1); v=j/(TILE-1)
                value=.30+.37*math.sin(math.pi*v)+.16*math.cos(2*math.pi*(u-.12))
                grain=math.sin(i*3.83+j*2.77+seed)*.035
                if any(k in mat for k in ('fur','wool','hide','mane','body','skin')):
                    value+=.11*math.cos(i*1.45+j*.44+.5*math.sin(j*.4))
                elif any(k in mat for k in ('feather','sail','fin','mirror','pearl','leaf')):
                    value+=.17*(1-abs(math.sin((j+9*abs(u-.5))*.63)))+.16*(abs(u-.5)<.045)
                elif 'stone' in mat or 'plate' in mat or 'shell' in mat:
                    value-=.22*(abs(math.sin(i*.21+j*.48+math.sin(i*.34)))<.13)
                if mat in {'deer_fur','prism_hide','hare_fur','sand_fur','marten_fur'}:
                    dots=sum((u-xx)**2/.00075+(v-yy)**2/.00165<1 for xx,yy in ((.16,.22),(.33,.31),(.73,.25),(.87,.38),(.57,.18)))
                    value+=.34*min(1,dots)
                elif mat in {'whale_skin','tide_hide','otter_belly','lizard_skin'}:
                    line=abs(v-(.52+.085*math.sin(u*math.tau*2+seed)))
                    value+=.25*max(0,1-line/.05)
                if mat in GLOW or mat in {'bronze','horn','antler','ivory'}:
                    value+=.16*math.cos(u*math.tau*3-v*2)
                t=max(0,min(1,value+grain))
                col=(shade*(1-2*t)+base*(2*t)) if t<.5 else base*(2-2*t)+lit*(2*t-1)
                if mat in GLOW and (.16<abs(u-.49)<.20 or (i+seed)%12==0 and j%7==0):
                    col=.35*col+.65*spark
                val=(*np.clip(col,0,255).astype('uint8').tolist(),255)
                image.putpixel((x+i,y+j),val)
                if mat in GLOW:glow.putpixel((x+i,y+j),val)
    return image,glow


def sculpt(sid,parts):
    skin=defaultdict(Surface)
    for part in parts:
        joint=part['name'];mesh=skin[joint]
        for box in part['boxes']:
            mat=box['material']; size=box['size']; p=box['position']
            if mat in {'eye','eye_light'}:
                mesh.rounded('eye',p,[max(1.3,size[0]),max(1.35,size[1]),max(1.1,size[2])],shape='soft')
                cx=p[0]+size[0]*.5;sg=-1 if cx<0 else 1
                mesh.rounded('iris',[cx+sg*.57-.14,p[1]+.10,p[2]+.09],[.49,.80,.78],shape='soft')
                continue
            if mat in GLOW or mat in {'ivory','leaf_horn','horn','antler','bell_horn'}:style='prism'
            elif nameplate(mat,joint):style='hard'
            elif 'leg' in joint or mat=='hoof':style='hard' if mat=='hoof' else 'tissue'
            else:style='soft' if joint in ('head','body') else 'tissue'
            mesh.rounded(mat,p,size,shape=style)
    def reset(name):
        skin[name]=Surface()
        return skin[name]
    def ball(bone,mat,center,size):
        skin[bone].rounded(mat,[c-s*.5 for c,s in zip(center,size)],size,shape='soft')
    def eyes(bone,w,y,z,r=1):
        for sign in (-1,1):
            ball(bone,'eye',(sign*w,y,z),(r*1.15,r*1.6,r*1.8))
            ball(bone,'iris',(sign*(w+r*.38),y-.05*r,z-.20*r),(.56*r,r*.94,r))
            ball(bone,'eye_light',(sign*(w+r*.49),y-.30*r,z-.36*r),(.36*r,.43*r,.43*r))
    def paired(bone,material,start,end,width,normal=(0,-1,0),ridge=.2):
        for s in (-1,1):
            skin[bone].blade(material,(s*start[0],*start[1:]),(s*end[0],*end[1:]),width,(s*normal[0],*normal[1:]),ridge)
    def crest(bone,mat,base,lengths,tipz,spread=.7):
        x,y,z=base
        for i,h in enumerate(lengths):
            skin[bone].blade(mat,(x,y,z+i*spread),(x,y-h,z+i*spread+tipz),h*.24,(1,0,0),.30)
    if sid=='dewbound_whale':
        reset('body').loft('whale_skin','z',[(-11,0,-.25,6.1,3.6),(-7,0,-.15,8,5.7),(-2,0,0,8.55,6.25),(4,0,.1,7.4,5.8),(8,0,0,5.6,4),(12,0,-.45,3.5,2.7)],12)
        skin['body'].loft('whale_belly','z',[(-10,0,2.15,5.4,1.85),(-5,0,3.95,7.7,2.25),(1,0,4.66,6.45,1.9),(7,0,2.9,5,1.7),(11.1,0,1,2.9,1.4)],10)
        reset('head').loft('whale_skin','z',[(-7.2,0,.4,1.5,1.2),(-5.5,0,.2,4.6,2.7),(-2,0,.25,6.65,4.35),(1.2,0,.45,7,4.3),(3,0,.8,6.3,3.6)],12)
        skin['head'].loft('whale_belly','z',[(-6.9,0,1.3,1.8,.5),(-4,0,2.7,5.0,1.0),(0,0,3.5,6.35,1.5),(2.0,0,3,5.2,1.4)],10)
        eyes('head',6.55,0,-.9,1.04)
        for s,l in [(-1,'l'),(1,'r')]:
            reset('fin_front_'+l).blade('whale_skin',(0,0,-1),(s*8.6,1.7,6),2,(0,-1,0),.66)
            skin['fin_front_'+l].blade('dew_glow',(s*.9,-.27,-1.2),(s*7.8,1.6,5.3),.22)
            reset('fin_back_'+l).blade('whale_skin',(-s,0,-1),(s*5.1,-1.1,4.8),1.55,(0,-1,0),.45)
            for i in range(4):
                skin['body'].blade('dew_glow',(s*(7.8-i*.18),-.8, -4+i*1.65),(s*(8-i*.37),.4,-3.2+i*1.7),.21,(s,0,0))
        reset('tail').loft('whale_skin','z',[(-1,0,-.45,3.6,2.6),(2,0,-.45,2.95,2.2),(5,0,-.85,1.55,1.4)],10)
        tail=reset('tail_tip')
        paired('tail_tip','whale_skin',(0,-.8,1),(9,-1.65,5),1.7,(0,-1,0),.6)
        paired('tail_tip','dew_glow',(.9,-1.05,2),(8.4,-1.65,4.4),.2)
        for i in range(4):
            g=reset('pip_'+str(i))
            for s in (-1,1):g.blade('dew_glow',(s*1.5,.5,-.55),(s*(2.3+i*.06),-1.55,.9),.43,(0,-1,0),.4)
    elif sid=='papersail_ray':
        reset('body').loft('ray_body','z',[(-7.5,0,0,1.8,1.1),(-4,0,-.2,4.0,2),(0,0,0,4.1,2),(5,0,.45,2.6,1.5),(8,0,.2,1.0,.8)],10)
        eyes('head',2.48,-.79,-1.5,.65)
        paired('head','ray_edge',(1.1,0,-1.5),(2.5,-.6,-5),.75)
        for s,l in [(-1,'l'),(1,'r')]:
            for name,stations in [('wing_'+l,[(0,.05,-.5,1.1,7.4),(2.8,-.12,-.2,.84,7.15),(6,0,-.22,.53,5.35)]),('wing_'+l+'_mid',[(0,0,-.22,.53,5.45),(2.9,0,0,.43,4.7),(5,.1,.8,.31,3.65)]),('wing_'+l+'_tip',[(0,.1,-.2,.32,3.75),(2.6,-.18,.68,.29,2.35),(5.6,-1.4,2.4,.11,.3)])]:
                wing=reset(name)
                wing.loft('ray_sail','x',[(s*x,y,z,ry,rz) for x,y,z,ry,rz in stations],8,.87)
                for angle in (-1,0,1):
                    x,y,z,ry,rz=stations[0]; b,h,d,rb,rzb=stations[-1]
                    wing.blade('ray_edge',(s*x,y-ry*.87,z+angle*rz*.60),(s*b,h-rb*.88,d+angle*rzb*.58),.12,ridge=.045)
            for fore in ('f','b'):
                sk=reset('leg_'+fore+l)
                sk.blade('ray_edge',(0,-1.1,0),(s*.8,.75,2.15),.58)
        reset('resource_air').loft('water_glow','z',[(-3,0,.4,.25,.35),(-.5,0,0,1.45,.60),(2.8,0,.4,.22,.24)],8)
    elif sid=='mirrorwing_moth':
        for sign,label in [(-1,'l'),(1,'r')]:
            fore=reset('wing_front_'+label)
            fore.blade('mirror_rim',(0,0,1),(sign*11,-.6,-6),3.65,ridge=.38)
            fore.blade('mirror',(sign*1.15,-.34,.4),(sign*9.7,-.83,-5.15),2.2,ridge=.11)
            fore.blade('mirror_glow',(sign*4.1,-.56,-2.6),(sign*7.95,-.66,-4.0),.80,ridge=.07)
            aft=reset('wing_back_'+label)
            aft.blade('mirror_rim',(0,0,-.85),(sign*8,.2,5),2.7,ridge=.28)
            aft.blade('mirror',(sign*.7,-.24,-.3),(sign*7.3,0,4.5),1.54,ridge=.1)
            aft.blade('mirror_rim',(sign*5.4,0,2.5),(sign*7.4,.4,8.2),.95)
            feeler=skin['antenna_'+label]
            for j in range(3):feeler.blade('mirror_rim',(0,-1.5-j,0),(sign*(1.35-.2*j),-2.35-j,0),.25,(0,0,-1),.12)
    elif sid in ('post_heron','froststride_fowl'):
        crane=sid=='post_heron'; dark='heron_dark' if crane else 'frost_dark'; feather='heron_feather' if crane else 'frost_feather'
        for sign,label in [(-1,'l'),(1,'r')]:
            primary='wing_'+label+('_tip' if crane else '')
            for i in range(4):
                y=(0 if crane else 3.8)+i*.38;z=-1.8+i*1.25
                skin[primary].blade(feather if i%2 else dark,(sign*.28,y,z),(sign*(.7+.13*i),y+4.6-.41*i,z+3.8),.86,(sign,0,0),.28)
            for j in range(3):
                skin['leg_f'+label].blade('heron_bill' if crane else dark,(0,8.25 if crane else 8,0),((j-1)*1.65,8.65 if crane else 8.65,-3.4+(j-1)**2*.65),.31)
        crest('head',dark,(0,-2.0,.5),[1.5,2.0,1.8],3.2)
        paired('tail',feather,(.3,0,1),(2.7,.0,7.1),.86)
    elif sid=='watch_mantis':
        for sign,l in [(-1,'l'),(1,'r')]:
            for j in range(4):skin['arm_'+l+'_sickle'].blade('ivory',(0,1.2+j*.94,-.4),(sign*.2,1.95+j*.92,-2.0+j*.12),.28,(sign,0,0))
            skin['wing_'+l].blade('mantis_leaf',(0,0,0),(sign*.65,-.20,9.1),1.5)
            skin['wing_'+l].blade('lamp_glow',(0,-.18,.9),(sign*.55,-.31,7.8),.13)
        paired('head','mantis_dark',(1.3,-.83,-.9),(4.2,-1.35,-.65),.56,(0,-1,0))
    elif sid=='prismatic_antelope':
        skin['body'].loft('prism_hide','z',[(-9,0,-2.6,1.7,2.6),(-7,0,-.4,3.25,3.85),(-4,0,.4,3.1,3.15)],9)
        for s,l in [(-1,'l'),(1,'r')]:
            g=reset('horn_'+l)
            g.loft('prism','y',[(-.5,0,0,.92,1.05),(-4.7,.4,s*.60,.73,.80),(-8,1.2,s*1.6,.53,.64),(-12,2.35,s*1.3,.09,.09)],7)
            g.blade('prism',(s*.6,-4.4,.38),(s*2.9,-7.0,-1.05),.53,(0,0,-1),.30)
            skin['head'].blade('prism_hide',(s*1.7,1,-3),(s*2.7,2.8,-1),.65,(s,0,0))
        eyes('head',2.86,-1.12,-3.15,.71)
    elif sid=='drumbelly_otter':
        eyes('head',3.55,-1.2,-3.55,.94)
        for s in (-1,1):
            for i in range(3):skin['head'].blade('ivory',(s*2.25,.6,-5.6),(s*(5.45+i*.48),.42+i*.34,-5.65+i*.7),.13,(0,-1,0),.07)
            skin['body'].blade('sand_fur',(s*4.8,-1,-4),(s*6.5,2.8,2.9),1.25,(s,0,0))
        ball('body','bronze',(0,3.5,-.9),(10,3.05,9.3))
    elif sid=='bellwool_sheep':
        skin['head']=Surface();g=skin['head']
        g.loft('sheep_hide','z',[(-6.2,0,.95,1.8,1.35),(-4.1,0,.05,2.85,2.5),(-1,0,-.35,3.6,3.4),(2.15,0,-.1,2.9,2.4)],10)
        eyes('head',3.0,-1.15,-2.8,.71)
        for s in (-1,1):
            g.loft('bell_horn','x',[(s*2.45,-2.4,.5,1.2,1.4),(s*3.7,-4.05,.65,1.2,1.5),(s*5.15,-4.9,.6,1.05,1.35),(s*6.25,-4.7,-.2,.78,1.05),(s*6.55,-3.4,-.95,.32,.54)],8)
            g.blade('sheep_hide',(s*2.3,-.9,.6),(s*5.2,-.35,.8),1.0)
            for i in range(4):
                ball('fleece','wool',(s*3.7,-1.0+i%2*.6,-4.4+i*3.1),(3.45,5.0,4.0))
        g=reset('bell')
        g.loft('bronze','y',[(-.6,0,0,.60,.6),(.1,0,0,.85,.85),(1.65,0,0,1.25,1.25),(2.35,0,0,1.55,1.55)],10)
        ball('bell','bell_horn',(0,2.42,0),(.75,1.05,.75))
    else:
        signature(sid,skin,ball,paired,crest)
    knit_anatomy(sid,parts,skin)
    return skin

def tendon(mesh,mat,first,last,radii,flat=1):
    start=np.array(first,float);end=np.array(last,float);delta=end-start
    axis=int(np.argmax(np.abs(delta)))
    if abs(delta[axis])<.06:return
    u=(axis+1)%3;v=(axis+2)%3
    sections=[]
    for t,r in zip((0,.23,.62,1),radii):
        spot=start+delta*t
        sections.append((spot[axis],spot[u],spot[v],max(.06,r),max(.06,r*flat)))
    mesh.loft(mat,'xyz'[axis],sections,8,.88)

def matrix(rt):
    x,y,z=rt
    rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
    ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
    rz=np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
    return rz@ry@rx

def knit_anatomy(sid,parts,skin):
    # The older cuboids meet at their corners. Sculpted extremities need an actual
    # shoulder/haunch, a neck and inset eye sockets at those attachment sites.
    tree={'root':(np.eye(3),np.zeros(3))}; byname={}
    for p in parts:
        par,base=tree[p['parent']]
        tree[p['name']]=(par@matrix(p['rotation']),base+par@p['pivot'])
        byname[p['name']]=p
    def to_bone(point,src,dst):
        sr,so=tree[src];dr,do=tree[dst]
        return dr.T@(sr@np.asarray(point)+so-do)
    body=byname['body']['boxes'][0];pos=np.array(body['position']);size=np.array(body['size'])
    center=pos+size/2;mat=body['material']
    if sid=='bellwool_sheep':
        fleece=skin['fleece']=Surface()
        fleece.loft('wool','z',[(-8.25,0,-.2,3.6,4.05),(-6.5,0,-.4,5.65,5.3),(-3,0,-.45,5.95,5.45),
            (1,0,-.28,6.05,5.25),(5.1,0,-.3,5.7,5.30),(8,0,-.05,4.0,4.15)],12,.78)
        for sign in (-1,1):
            for i in range(5):
                fleece.rounded('wool',(sign*4.6-1.1,-2.05+(.5 if i%2 else 0),-6.6+i*2.5),(2.55,5,4.1),shape='soft')
    legs=[n for n in byname if re.fullmatch('leg_[fbm][lr]|(right|left)_(front|hind)_leg|arm_[lr]',n)]
    for n in legs:
        if sid=='papersail_ray':continue  # pelvic lobes emerge directly from the disc
        box=byname[n]['boxes'][0]; bpos=np.array(box['position']); dims=np.array(box['size'])
        mid=bpos+dims*.5
        tip=to_bone(mid,n,'body')
        rel=(tip-center)/(size*.5)
        h=math.sqrt(rel@rel)
        inside=center+(tip-center)*min(1,.66/max(.01,h))
        body_radius=min(size[0],size[1])*.26
        r=min(body_radius,max(.50,min(dims[0],dims[2])*.54))
        if sid in ('bellwool_sheep','dusk_lantern_deer','prismatic_antelope','springhoof_strider'):r=max(r,1.35)
        if 'wing' in sid or sid=='watch_mantis':r=min(r,.67)
        shoulder=to_bone((0,0,0),n,'body')
        end=shoulder+(tip-shoulder)*.55
        tendon(skin['body'],mat,inside,end,[r,r*1.35,r*1.05,r*.77],1.13)
        # The joint is wider than the gap opened by a full stride.
        tendon(skin[n],box['material'],to_bone(shoulder+(inside-shoulder)*.26,'body',n),mid,[r*.74,r,r*.85,r*.62])
        if len(byname[n]['boxes'])>1:
            ankle=byname[n]['boxes'][1]
            a=np.array(ankle['position'])+np.array(ankle['size'])*.5
            if np.linalg.norm(a-mid)>.9:tendon(skin[n],box['material'],mid,a,[r*.8,r*.70,r*.67,r*.53])
    head=byname['head']['boxes'][0];hp=np.array(head['position']);hs=np.array(head['size'])
    skull=hp+hs*.5
    if sid not in ('dewbound_whale','papersail_ray'):
        front=to_bone(skull,'head','body')
        neckroot=center+(front-center)*.39
        neckend=center+(front-center)*.91
        rad=min(hs[0]*.45,size[0]*.33)
        tendon(skin['body'],mat,neckroot,neckend,[rad,rad*1.04,rad*.86,rad*.80],1.07)
    for box in byname['head']['boxes']:
        if box['material'] not in ('eye','eye_light'):continue
        p=np.array(box['position']); e=np.array(box['size']);target=p+e*.5
        attach=skull+(target-skull)*.71
        r=max(.53,min(e[1]*.63,1.3))
        tendon(skin['head'],head['material'],attach,target,[r*1.25,r*1.32,r*1.07,r*.69])
    if sid in ('forgegill_tapir','stonebarrow_boar','emberback_hog','mistbelly_otter','drumbelly_otter','pillowlight_marten','pageforage_raccoon','bowlhorn_rhino'):
        for b in byname['head']['boxes']:
            loc=np.array(b['position']);bs=np.array(b['size']);end=loc+bs*.5
            if bs[0]<1.35 or end[1]>hp[1]+.9 or b['material'] in ('eye','eye_light'):continue
            if end[2]<hp[2] or abs(end[0])<hs[0]*.21:continue
            r=min(1.0,max(.5,bs[0]*.56))
            tendon(skin['head'],b['material'],skull+(end-skull)*.53,end,[r*1.10,r*1.18,r,r*.68])
    if sid=='springhoof_strider':
        for sign,lr in [(-1,'l'),(1,'r')]:
            src=to_bone((0,0,0),'horn_'+lr,'head')
            tendon(skin['head'],'leaf_horn',skull,src,[1.25,1.45,.92,.77])
    if sid=='apiary_toad':
        for sign in (-1,1):
            tendon(skin['head'],'toad_skin',(sign*2.4,-.3,-.8),(sign*3.5,-2.8,-1.33),[1.25,1.42,1.39,.94])
            skin['head'].rounded('eye',(sign*3.55-1.06,-3.65,-2.40),(2.12,1.66,1.23),'x','soft')
            skin['head'].rounded('bronze',(sign*3.55-.73,-3.35,-2.59),(1.46,1.12,.48),'x','soft')
            skin['head'].rounded('eye',(sign*3.55-.53,-3.02,-2.72),(1.05,.32,.22),'x','soft')
    if sid=='mirrorwing_moth':
        for n in legs:
            suffix=n[-1];sign=-1 if suffix=='l' else 1
            mesh=skin[n]
            mesh.blade('moth_body',(0,3.1,0),(sign*1.37,4.55,.65),.26,(0,0,-1),.19)
    if sid=='post_heron':
        for sign in (-1,1):
            skin['neck'].blade('heron_feather',(0,-1.45,0),(sign*1.3,1.6,.65),.48,(0,0,-1))
    if sid=='dewbound_whale':
        for sign in (-1,1):
            for i in range(4):
                skin['head'].blade('whale_belly',(sign*(1.2+i*.85),3.8,-2),(sign*(1.35+i*.86),3.3,-4.5),.21,(0,1,0),.1)

def nameplate(material,bone):
    return any(s in material for s in ('stone','shell','plate','board','bag','paper','rim')) or bone.startswith('pip_')

def signature(sid,skin,ball,paired,crest):
    if sid=='stonebarrow_boar':
        paired('head','ivory',(3.1,2.1,-6.7),(4.55,-1.95,-9.5),.76,(0,0,-1),.66)
        for j in range(3):paired('body','stone',(4.0,-2.5,-5.1+j*3),(6.7,-4.3,-2.8+j*3),1.38,(.8,-.6,0),.50)
    elif sid=='emberback_hog':
        crest('body','ember_plate',(0,-3.2,-5),[2.0,2.9,3.3,2.8,2.1],2.2,2.05)
        paired('head','ivory',(1.65,1.4,-8),(3,-.4,-10.6),.46,(0,0,-1),.31)
    elif sid=='tideback_newt':
        for i in range(3):
            paired('head','tide_fin',(4,0,-1.8+i*.88),(8.3-.57*i,-1.55+i*.55,.2+i*1.15),.63,(0,-1,0),.23)
            paired('head','water_glow',(4.65,-.15,-1.8+i*.88),(7.6-.5*i,-1.35+i*.55,.12+i),.15)
    elif sid=='apiary_toad':
        for i in range(3):
            for s in (-1,1):
                ball('body','honey',(s*(2.5+i*.26),-3.14,-2.4+i*2.15),(1.55,1.4,1.8))
                skin['leg_b'+('l' if s<0 else 'r')].blade('honey',((i-1)*.55,3,2),((i-1)*1.95,3.85,6.5-.55*abs(i-1)),.5)
    elif sid=='starfelt_hare':
        paired('head','hare_fur',(1.35,.7,-1.4),(3.7,1.2,-.55),.83,(0,0,-1))
        for s,l in [(-1,'l'),(1,'r')]:
            skin['ear_'+l].blade('hare_fur',(0,-6,0),(s*.53,-10.1,.25),.88,(0,0,-1),.35)
            skin['ear_'+l].blade('star_glow',(0,-5.7,-.63),(s*.42,-9.4,-.29),.22,(0,0,-1),.09)
    elif sid=='dusk_lantern_deer':
        for s,l in [(-1,'l'),(1,'r')]:
            g=skin['horn_'+l]
            g.blade('antler',(s*.45,-4.4,1.5),(s*3.8,-9,3.4),.65,(0,0,-1),.48)
            g.blade('antler_glow',(s*2.6,-7.35,2.8),(s*3.8,-9.4,3.55),.48,(0,0,-1),.28)
        paired('neck','deer_mane',(1,0,-1),(2.1,2.7,-1.8),.64,(0,0,-1))
    elif sid=='pattern_shell_snail':
        g=skin['shell']=Surface()
        g.loft('snail_shell','x',[(-5.4,-4.3,0,1.7,1.8),(-4.9,-4.4,0,3.5,3.45),
            (-3.65,-4.5,0,5.35,5.4),(-1.0,-4.7,.15,6.15,6.05),
            (2.1,-4.65,.1,6.0,5.9),(3.8,-4.5,0,5.25,5.3),
            (5.0,-4.4,0,3.5,3.5),(5.4,-4.3,0,1.75,1.75)],14)
        for side in (-1,1):
            center=[]
            for j in range(25):
                a=j*.42;r=.40+4.85*j/24
                span=np.interp(r,[0,1.7,3.5,5.3],[5.47,5.44,5.03,3.82])
                center.append(np.array([side*span,-4.39+math.sin(a)*r,math.cos(a)*r]))
            # One raised cord follows the shell surface into the heart of its whorl.
            for k,(p,q) in enumerate(pairwise(center)):
                tendon(g,'shell_stripe',p,q,[.22,.27,.27,.22])
        paired('head','snail_body',(1.7,-.3,-2.2),(4.1,-.25,-4.3),.41)
    elif sid=='forgegill_tapir':
        for i in range(4):
            for s,l in [(-1,'l'),(1,'r')]:skin['gill_'+l+str(i)].blade('tapir_gill',(0,1,0),(s*1.05,3.45,1.85),.63,(s,0,0),.23)
        skin['head'].loft('tapir_hide','z',[(-11.3,0,3.3,1.3,1),(-9,0,2,2.4,1.75),(-5.6,0,1,3.1,2.45)],8)
    elif sid=='mistbelly_otter':
        for i in range(3):paired('head','ivory',(2.25,.65,-5),(5.9+.2*i,.5+.32*i,-5.3+i*.72),.12)
        paired('tail_tip','tide_fin',(.65,0,.6),(3.2,-.3,6.7),.85)
    elif sid=='ringstone_tortoise':
        shell=skin['shell']=Surface()
        shell.loft('tortoise_shell','y',[(-7.35,0,0,.9,.85),(-6.2,0,0,4.05,3.15),
            (-4.5,0,0,6.1,4.8),(-2.15,0,0,8.05,6.50),(.2,0,0,8.3,6.65)],12,.85)
        for j in range(3):
            paired('shell','earth_ring',(1.1,-4.8,-4.6+j*3.6),(4.7,-3.7,-2.9+j*3.6),1.02,(.5,-1,0),.52)
        crest('head','stone',(0,-1.65,-1.8),[.95,.7],.55)
    elif sid=='wirecall_lizard':
        paired('head','electric_glow',(2.0,-.7,-2.5),(3.7,-2.25,.55),.48,(1,-1,0))
        for i in range(3):paired('body','electric_glow',(2.6,-.15,-3+i*3),(3,-.6,-1.6+i*3),.16,(1,0,0))
    elif sid=='satchelback_runner':
        for s,l in [(-1,'l'),(1,'r')]:
            g=skin['resource_bag_'+l];g.blade('bag_seam',(s*2.1,-1,-2.9),(s*2.9,4.3,1.9),.55,(s,0,0))
            ball('resource_bag_'+l,'bronze',(s*2.7,2,0),(1,1.65,1.55))
        paired('head','runner_hide',(2.5,-2,-.35),(4.0,-4.3,.8),.70,(0,0,-1))
    elif sid=='bowlhorn_rhino':
        g=skin['bowl_horn'];g.loft('horn','y',[(-8.8,-.5,0,.34,.45),(-6.1,-.1,0,1.08,1.43),(-3.9,.65,0,1.65,1.8),(-2,1.12,0,1.75,2.0)],9)
        paired('body','rhino_plate',(4.2,-3,-6),(6.8,-6,1.5),1.75,(.5,-1,0),.63)
    elif sid=='pageforage_raccoon':
        for j in range(3):paired('head','raccoon_fur',(2.1,.4,-2.4),(4.9-j*.25,.25+j*.52,-.2+j*.55),.52,(0,0,-1))
        skin['resource_paperbag'].blade('paper',(-1,-.2,-1),(1.1,-3,-.65),1.05,(0,0,-1),.2)
    elif sid=='patternbuild_beaver':
        for s in (-1,1):
            ball('head','ivory',(s*.93,2.75,-6.95),(1.4,2.4,1.25))
            for j in range(3):skin['tail'].blade('bag_seam',(s*.5,-.52,2+j*1.9),(s*2.95,-.39,3.3+j*1.6),.16)
    elif sid=='springhoof_strider':
        for s,l in [(-1,'l'),(1,'r')]:
            skin['horn_'+l].blade('leaf_horn',(0,-3,.0),(s*1.6,-8,1.55),.86,(0,0,-1),.26)
            skin['leg_b'+l].blade('water_glow',(0,7.4,-.42),(s*.45,9.5,-1.4),.26)
        paired('neck','strider_hide',(1.15,-1,-.3),(2.5,2.35,1.9),.78,(0,0,-1))
    elif sid=='pillowlight_marten':
        for j in range(3):
            paired('tail_tip','marten_tail',(.4,-.45,1.2+j),(3.2-j*.45,-1.1+j*.26,5.5+j*.9),.65)
        paired('resource_lamp','lamp_glow',(.46,-.8,-1.42),(1.25,-2.7,.45),.28)
    else: raise ValueError(sid)


def articulate_legs(id_,parts):
    ungulates={'dusk_lantern_deer','springhoof_strider','stonebarrow_boar','emberback_hog',
        'bowlhorn_rhino','satchelback_runner','forgegill_tapir','prismatic_antelope','post_heron','froststride_fowl'}
    if id_ not in ungulates:return
    for p in parts.copy():
        if not re.fullmatch('leg_[fb][lr]',p['name']):continue
        upper=p['boxes'][0]
        length=upper['size'][1]
        hinge=max(2.4,min(5.2,length*.55))
        x,y,z=upper['position'];w,h,d=upper['size']
        if h<=hinge+.3:continue
        upper['size'][1]=hinge+.35
        remaining=[];kept=[upper]
        for b in p['boxes'][1:]:
            if b['position'][1]>=hinge-.35:
                b['position'][1]-=hinge;remaining.append(b)
            else:kept.append(b)
        p['boxes']=kept
        lower=dict(position=[x+.07,-.28,z+.05],size=[w-.14,h-hinge+.28,d-.1],material=upper['material'])
        parts.append(dict(name='shin_'+p['name'][4:],parent=p['name'],pivot=[0,hinge,0],rotation=[0]*3,
            boxes=[lower]+remaining,articulated=True))

def packed_surface(surface,tiles):
    pts=[]; lookup={}; polys=[]
    for mat,points,uv in surface.polys:
        ids=[]
        for p in points:
            if p not in lookup:
                lookup[p]=len(pts)
                pts.append(list(p))
            ids.append(lookup[p])
        if len(ids)==3:
            ids.append(ids[-1]);uv=uv+[uv[-1]]
        x,y=tiles[mat]; flat=[]
        for u,v in uv:
            flat.extend((round(x+.55+u*(TILE-1.1),3),round(y+.55+v*(TILE-1.1),3)))
        polys.append(ids+flat+[mat in GLOW])
    return dict(points=pts,faces=polys)

def uid(id_,name):
    return str(uuid.uuid5(uuid.NAMESPACE_DNS,'tnc.pasture.'+id_+'.'+name))

def export_bb(sid,parts,mesh,img):
    names={}; tree=[]; elems=[]
    pivots={'root':np.zeros(3)}
    for part in parts:
        name=part['name'];parent=part['parent']
        piv=pivots[name]=pivots[parent]+np.array(part['pivot'])
        origin=[piv[0],24-piv[1],piv[2]]
        rot=np.degrees(part['rotation'])*[-1,1,-1]
        group={'name':name,'uuid':uid(sid,'joint.'+name),'origin':origin,'rotation':rot.tolist(),'children':[]}
        names[name]=group
        (tree if parent=='root' else names[parent]['children']).append(group)
        geo=mesh.get(name)
        if not geo or not geo['faces']:continue
        verts={str(i):[x,-y,z] for i,(x,y,z) in enumerate(geo['points'])}
        faces={}
        for j,face in enumerate(geo['faces']):
            ns=face[:4];uv=face[4:12]
            n=3 if ns[2]==ns[3] else 4
            ordered=[str(k) for k in reversed(ns[:n])]
            faces[str(j)]={'vertices':ordered,'uv':{str(ns[i]):uv[2*i:2*i+2] for i in range(n)},'texture':0}
        token=uid(sid,'skin.'+name)
        elem={'type':'mesh','uuid':token,'name':name+'_surface','origin':origin,'vertices':verts,'faces':faces,'color':3}
        elems.append(elem);group['children'].insert(0,token)
    raw=io.BytesIO();img.save(raw,format='PNG')
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':sid,
            'resolution':{'width':ATLAS,'height':ATLAS},'elements':elems,'outliner':tree,
            'textures':[{'name':sid+'.png','id':'0','uuid':uid(sid,'texture'),'mode':'bitmap',
                'internal':True,'source':'data:image/png;base64,'+base64.b64encode(raw.getvalue()).decode()}]}

def generate(export,fmt=''):
    skels=sources()
    if fmt:skels={fmt:skels[fmt]}
    dst=ASSETS/'pasture_meshes';dst.mkdir(exist_ok=True)
    texdir=ASSETS/'textures/entity/pasture_sculpt';texdir.mkdir(exist_ok=True)
    if export:export.mkdir(parents=True,exist_ok=True)
    for sid,parts in skels.items():
        assert len(set(p['name'] for p in parts))==len(parts)
        articulate_legs(sid,parts)
        skin=sculpt(sid,parts);tiles=shelf(skin); img,glow=paint(tiles)
        mesh={n:packed_surface(s,tiles) for n,s in skin.items() if s.polys}
        assert set(mesh)<=set(p['name'] for p in parts)
        for p in parts:
            if p.get('articulated'):mesh[p['name']]['hinge']=[p['parent'],*p['pivot']]
        # Hand-edited Blockbench embellishments are joint-local and use the same texture atlas.
        accessory=ROOT/'model-source/pasture/ecology-polish'/(sid+'.json')
        if accessory.is_file():
            for name,piece in json.loads(accessory.read_text(encoding='utf-8-sig')).items():
                bone=mesh[name];offset=len(bone['points'])
                bone['points'].extend(piece['points'])
                for face in piece['faces']:
                    bone['faces'].append([idx+offset for idx in face[:4]]+face[4:])
        (dst/(sid+'.json')).write_text(json.dumps(mesh,separators=(',',':')),encoding='utf8')
        img.save(texdir/(sid+'.png'))
        if sid!='bellwool_sheep':glow.save(texdir/(sid+'_glow.png'))
        if export:
            (export/(sid+'.bbmodel')).write_text(json.dumps(export_bb(sid,parts,mesh,img),separators=(',',':')),encoding='utf8')
        print(sid,len(mesh),sum(len(p['faces']) for p in mesh.values()),flush=True)

if __name__=='__main__':
    args=argparse.ArgumentParser(description=__doc__)
    args.add_argument('--blockbench',type=Path)
    args.add_argument('--species',default='')
    opts=args.parse_args()
    generate(opts.blockbench,opts.species)


