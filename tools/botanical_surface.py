"""Shared surface primitives for rooted botanical sculptures and the Blockbench source export."""
from collections import defaultdict
from math import sin, cos, sqrt, pi, tau, exp
import base64
import json
import uuid

from PIL import Image
from rebuild_arcane_plant_models import PALETTES
from rebuild_magic_plant_world_models import ASSETS

PALETTES = dict(PALETTES, steam_lotus=[0x253F58, 0x278D91, 0xDA7248, 0x76C9C1, 0xFFE2A1])
# Stems, leaves, bloom front/reverse, nectar; the fruit tile is mixed separately.
PALETTES.update(hushcap_mushroom=[0x423752,0x706181,0x956FAC,0xE9C9DA,0xD6E6FF],
                salt_ink=[0x273442,0x616C76,0x48617E,0xA5B7C0,0xA9E2DD],
                dream_compost=[0x4C434C,0x7A8575,0xB36E73,0xE6B391,0xA7F4C0],
                warning_moss=[0x294844,0x508E75,0xC17F42,0xDAC477,0xFFE5A1],
                star_dew=[0x344779,0x548D9A,0x74ACD3,0xA49CDA,0xCBFFF0],
                homeward_flower=[0x376962,0x56A485,0xCF646E,0xF3B398,0xFFECB1],
                ore_sleep_moss=[0x344942,0x618B68,0x8D748E,0xB0B2AC,0xB9FED1],
                star_rest=[0x414765,0x758A94,0x809ED7,0xDAC6EB,0xE6FFF4])

def plus(a,b): return tuple(x+y for x,y in zip(a,b))
def minus(a,b): return tuple(x-y for x,y in zip(a,b))
def mul(a,s): return tuple(x*s for x in a)
def length(a): return sqrt(sum(x*x for x in a))
def unit(a): return mul(a,1/max(1e-12,length(a)))
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def mix(a,b,t): return tuple(x*(1-t)+y*t for x,y in zip(a,b))
def radial(p,angle,reach=0,rise=0): return (p[0]+cos(angle)*reach,p[1]+rise,p[2]+sin(angle)*reach)
def qround(x): return round(x,4)

def atlas(species):
    swatches=[((c>>16)&255,(c>>8)&255,c&255) for c in PALETTES[species]]
    swatches.append(mix(swatches[2],swatches[3],.21))
    silk=species in ('mist_cotton','wish_puff','hanging_dewgrass')
    minerals=species in ('ore_sleep_moss','stone_fern','prism_crown','storm_crown','frost_chime')
    fungi=species in ('hushcap_mushroom','dream_compost','salt_ink')
    image=Image.new('RGB',(96,64))
    for tile,base in enumerate(swatches):
        for y in range(32):
            v=y/31
            for x in range(32):
                u=x/31
                flank=abs(2*u-1)
                # Pigment follows the local long axis, with a raised midrib and paired secondary veins.
                vein=exp(-((u-.5)/.034)**2)
                barb=exp(-(((v-.75*flank+.075)% .18-.09)/.022)**2)
                grain=((x*37+y*53+x*y*7)%31-15)/520
                if tile==0:
                    light=.81+.14*cos(3*tau*u+1.7*sin(7*v))+.10*v
                elif tile==1:
                    light=.75+.22*(1-flank)+.16*v+.24*vein+.12*barb
                elif tile in (2,3):
                    light=.67+.40*v+.13*(1-flank)+.11*cos(tau*(3*u+.24*sin(9*v)))
                    if minerals: light+=.12*(int(u*6+v*2)%2)
                    if fungi: light+=.11*cos(5*tau*v+.55*sin(9*u))
                    if silk: light+=.10*sin(5*tau*u+4*v)
                elif tile==4:
                    light=.82+.28*(1-flank)+.16*v
                else:
                    light=.76+.30*(1-flank)+.13*v+.11*barb
                highlight=(.26 if tile==2 else .12)*v**3 if tile in (1,2,3) else 0
                hue=mix(base,swatches[4],highlight) if highlight else base
                rgb=tuple(max(0,min(255,round(c*(light+grain)))) for c in hue)
                image.putpixel((tile%3*32+x,tile//3*32+y),rgb)
    path=ASSETS/'textures/block/botanical'/f'{species}.png'
    image.save(path)
    return path

class Mesh:
    def __init__(self,species,age):
        self.species,self.age=species,age
        self.faces=[]
        self.part='根茎'
    def face(self,points,tile=1,uv=None,back=False,glow=False):
        if uv is None: uv=[(0,0),(1,0),(1,1),(0,1)]
        pts=[tuple(map(qround,p)) for p in points]
        if len(set(pts))<3:return
        if length(cross(minus(pts[1],pts[0]),minus(pts[2],pts[0])))<.000001 and length(cross(minus(pts[2],pts[0]),minus(pts[3],pts[0])))<.000001:return
        tex=[(qround((tile%3*32+.25+u*31.5)/6),qround((tile//3*32+.25+v*31.5)/4)) for u,v in uv]
        self.faces.append((self.part,pts,tex,glow))
        if back:self.faces.append((self.part,pts[::-1],tex[::-1],glow))
    def stem(self,points,radii,mat=0,sides=6,glow=False):
        if isinstance(radii,(int,float)):
            radii=[radii*(1-.72*i/(len(points)-1)) for i in range(len(points))]
        rings=[]
        last_normal=None
        for i,p in enumerate(points):
            axis=unit(minus(points[min(i+1,len(points)-1)],points[max(i-1,0)]))
            ref=last_normal or ((1,0,0) if abs(axis[0])<.8 else (0,0,1))
            side=unit(cross(axis,ref));normal=cross(side,axis);last_normal=normal
            rings.append([plus(p,mul(plus(mul(normal,cos(tau*j/sides)),mul(side,sin(tau*j/sides))),radii[i])) for j in range(sides)])
        for i,(lower,upper) in enumerate(zip(rings,rings[1:])):
            for j in range(sides):
                k=(j+1)%sides
                self.face([lower[j],lower[k],upper[k],upper[j]],mat,[(j/sides,i/(len(rings)-1)),((j+1)/sides,i/(len(rings)-1)),((j+1)/sides,(i+1)/(len(rings)-1)),(j/sides,(i+1)/(len(rings)-1))],glow=glow)
    def blade(self,p,a,reach,width,lift,arch=0,mat=1,twist=0,ridge=.16,steps=4,glow=False):
        def at(t,s):
            phase=a+twist*t
            w=(.025+sin(pi*t)**.8*.975)*width*.5*s
            q=radial(p,phase,reach*t,lift*t+arch*sin(pi*t)-abs(s)*ridge*width*sin(pi*t))
            return plus(q,(-sin(phase)*w,0,cos(phase)*w))
        for i in range(steps):
            t,u=i/steps,(i+1)/steps
            for l,r in [(-1,0),(0,1)]:
                self.face([at(t,l),at(t,r),at(u,r),at(u,l)],mat,[((l+1)/2,t),((r+1)/2,t),((r+1)/2,u),((l+1)/2,u)],back=True,glow=glow)
    def spindle(self,p,radius,height,mat=5,profile=None,sides=8,pleat=0,lean=(0,0),glow=False):
        # p marks the bottom. Rings are counterclockwise looking down the axis.
        profile=profile or [(0,.025),(.20,.68),(.55,1),(.83,.75),(1,.025)]
        for i,((t,r),(u,s)) in enumerate(zip(profile,profile[1:])):
            def at(f,rr,j):
                a=-tau*j/sides;w=radius*rr*(1+pleat*cos(a*3));return (p[0]+f*lean[0]+cos(a)*w,p[1]+f*height,p[2]+f*lean[1]+sin(a)*w)
            for j in range(sides):
                self.face([at(t,r,j),at(t,r,j+1),at(u,s,j+1),at(u,s,j)],mat,[(j/sides,t),((j+1)/sides,t),((j+1)/sides,u),(j/sides,u)],glow=glow)
    def crown(self,p,n,reach,width,lift,arch,mat=2,spin=0,twist=0):
        for i in range(n):
            self.blade(p,spin+tau*i/n,reach,width,lift,arch,mat,twist=twist)
    def wire(self,points,width=.09,mat=4,glow=False):
        self.stem(points,[width]*len(points),mat,4,glow)
    def export_faces(self):
        return [dict(vertices=pts,uv=uv,glow=lit) for _,pts,uv,lit in self.faces]

def write_bbmodel(mesh,name,texture,destination):
    # Separate face groups keep the blossom, stems and local luminous surfaces editable.
    groups=defaultdict(list)
    for organ,pts,uv,glow in mesh.faces:
        groups[organ+(' · 亮芯' if glow else '')].append((pts,uv))
    ident=lambda key:str(uuid.uuid5(uuid.NAMESPACE_URL,'tnc/botanical/'+mesh.species+'/'+key))
    elems=[]
    for part,faces in groups.items():
        points={};positions={};polys={}
        for pts,uv in faces:
            keys=[];mapping={}
            for p,t in zip(pts,uv):
                p=tuple(p)
                if p not in points:
                    k='v'+format(len(points),'x'); points[p]=k;positions[k]=p
                k=points[p]
                if k not in mapping:
                    keys.append(k);mapping[k]=[round(t[0]*6,4),round(t[1]*4,4)]
            if len(keys)>=3:
                polys['f'+format(len(polys),'x')]={'vertices':keys,'uv':mapping,'texture':0}
        elems.append(dict(name=part,type='mesh',uuid=ident(part),origin=[0,0,0],vertices=positions,faces=polys,color=2))
    texture_uuid=ident('material')
    tex={'name':texture.name,'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode(),'uuid':texture_uuid,'id':'0','relative_path':'','width':96,'height':64,'uv_width':96,'uv_height':64,'internal':True,'mode':'bitmap'}
    project={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'model_identifier':mesh.species,'resolution':{'width':96,'height':64},'elements':elems,'outliner':[e['uuid'] for e in elems],'textures':[tex]}
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(project,ensure_ascii=False,separators=(',',':')),encoding='utf-8')

