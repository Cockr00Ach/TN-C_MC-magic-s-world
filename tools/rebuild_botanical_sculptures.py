"""Rooted botanical meshes: curved leaves, veins, calyces and individual flowers.

Runtime quads are baked into Minecraft's atlas; there is no per-frame mesh build.
Every species has an authored botanical family and dedicated pixel-material atlas.
The approved inventory illustrations are deliberately not modified.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import math,json,re,hashlib
from rebuild_magic_plant_world_models import FAMILIES,ASSETS,ROOT,model_refs,dump
from rebuild_arcane_plant_models import PALETTES

FAMILIES=dict(FAMILIES,steam_lotus='steam_lotus')
PALETTES=dict(PALETTES,steam_lotus=[0x32566a,0x53b5b0,0xf5ab6e,0xb4e8e5,0xfdf2cd])
TAU=math.tau
def rgb(v):return ((v>>16)&255,(v>>8)&255,v&255)
def blend(a,b,t):return tuple(round(x*(1-t)+y*t) for x,y in zip(a,b))

def texture(species):
    colors=list(map(rgb,PALETTES[species]));im=Image.new('RGBA',(96,64))
    # Six tiles: bark, leaf, primary petal, reverse petal, nectar, fruit.
    for tile in range(6):
        c=colors[min(tile,4)] if tile<5 else colors[2]
        ox=(tile%3)*32;oy=(tile//3)*32
        for y in range(32):
            for x in range(32):
                edge=abs(x-15.5)/16
                if tile==0:
                    f=.67+.22*math.sin(x*.7)+.12*math.cos(y*.25+x*.13)
                elif tile==1:
                    f=.73+.4*(1-edge)-.13*y/32
                    if abs(x-15)<1 or abs((y-(abs(x-15)*.55))%7)<.7:f+=.23
                elif tile in [2,3]:
                    f=.72+.35*y/31+.13*math.sin(x*.35)
                    if x%7==0:f-=.1
                    if y>27:f+=.16
                elif tile==4:f=1.05+.2*(1-edge)
                else:
                    f=.65+.42*math.sin((x+2)*math.pi/36)+.18*(1-y/32)
                    if (x*7+y*13)%37==0:f+=.15
                im.putpixel((ox+x,oy+y),(*[max(0,min(255,round(v*f))) for v in c],255))
    path=ASSETS/'textures/block/botanical'/f'{species}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

class Sculpture:
    def __init__(self,species,age):
        self.id=species;self.age=age;self.family=FAMILIES[species];self.seed=int(hashlib.sha256(species.encode()).hexdigest()[:6],16)
        self.q=[];self.g=[.25,.5,.76,1][age];self.height=12+(self.seed%5)*1.1
    def face(self,points,tile=1,uv=None,double=False,glow=False,reverse=False):
        if uv is None:uv=[(0,0),(1,0),(1,1),(0,1)]
        if reverse:points=list(reversed(points));uv=list(reversed(uv))
        ox=tile%3*32;oy=tile//3*32
        coords=[[round((ox+.25+u*31.5)/6,5),round((oy+.25+v*31.5)/4,5)] for u,v in uv]
        points=[[round(c,5) for c in p] for p in points]
        self.q.append(dict(vertices=points,uv=coords,glow=glow))
        if double:self.q.append(dict(vertices=list(reversed(points)),uv=list(reversed(coords)),glow=glow))
    def tube(self,points,radius=.3,tile=0,end=.08,n=6):
        for i in range(len(points)-1):
            a,b=points[i:i+2];r=radius+(end-radius)*i/(len(points)-1);s=radius+(end-radius)*(i+1)/(len(points)-1)
            for j in range(n):
                u=j*TAU/n;v=(j+1)*TAU/n
                self.face([(a[0]+math.cos(u)*r,a[1],a[2]+math.sin(u)*r),(a[0]+math.cos(v)*r,a[1],a[2]+math.sin(v)*r),
                           (b[0]+math.cos(v)*s,b[1],b[2]+math.sin(v)*s),(b[0]+math.cos(u)*s,b[1],b[2]+math.sin(u)*s)],tile,reverse=True)
    def leaf(self,x,y,z,a,length,width,tile=1,rise=.7,curl=.5):
        # Paired cambered ribbons share a raised midrib, with a tapered tip.
        def p(t,side):
            wid=(max(.008,math.sin(math.pi*t))**.8)*width*.5*side
            dist=t*length
            return (x+math.cos(a)*dist-math.sin(a)*wid,y+rise*math.sin(t*math.pi/2)+curl*math.sin(math.pi*t)+abs(side)*-.18*math.sin(math.pi*t),z+math.sin(a)*dist+math.cos(a)*wid)
        for i in range(7):
            t=i/7;u=(i+1)/7
            for side in [-1,1]:
                self.face([p(t,0),p(t,side),p(u,side),p(u,0)],tile,[(.5,t),(.5+side*.5,t),(.5+side*.5,u),(.5,u)],True)
    def orb(self,x,y,z,r,tile=5,stretch=1,glow=False):
        for row in range(5):
            lo=-math.pi/2+row*math.pi/5;hi=lo+math.pi/5
            for j in range(8):
                a=j*TAU/8;b=(j+1)*TAU/8
                pts=[(x+math.cos(t)*math.cos(s)*r,y+math.sin(t)*r*stretch,z+math.cos(t)*math.sin(s)*r) for t,s in [(lo,a),(lo,b),(hi,b),(hi,a)]]
                self.face(pts,tile,[(j/8,row/5),((j+1)/8,row/5),((j+1)/8,(row+1)/5),(j/8,(row+1)/5)],glow=glow,reverse=True)
    def blossom(self,x,y,z,r,tile=2,petals=7,layers=2,vertical=False):
        start=len(self.q)
        for layer in range(layers):
            for j in range(petals):
                a=j*TAU/petals+layer*.37
                self.leaf(x,y+layer*.45,z,a,r*(1-layer*.22),r*.75,tile if layer%2==0 else 3,r*.28+layer*.45,-r*.2)
        for j in range(5):
            a=j*TAU/5;self.tube([(x,y,z),(x+math.cos(a)*r*.2,y+.6,z+math.sin(a)*r*.2)],.09,3,.05,4)
            self.orb(x+math.cos(a)*r*.2,y+.65,z+math.sin(a)*r*.2,.17,4,glow=True)
        if vertical:
            # Turn the flower toward the viewer, leaving the stalk upright.
            for face in self.q[start:]:
                for p in face['vertices']:p[1],p[2]=y+(p[2]-z),z-(p[1]-y)
    def bell(self,x,y,z,r,tile=2):
        for row in range(5):
            t=row/5;u=(row+1)/5
            for j in range(8):
                a=j*TAU/8;b=(j+1)*TAU/8
                def p(v,ang):
                    rad=r*(.28+.65*v*v);return (x+math.cos(ang)*rad,y-v*r*1.5,z+math.sin(ang)*rad)
                self.face([p(t,a),p(t,b),p(u,b),p(u,a)],tile,double=True)
        self.tube([(x,y-.1,z),(x,y-r*1.5,z)],.07,3,.04,4);self.orb(x,y-r*1.6,z,.21,4,glow=True)
    def build(self):
        f=self.family;g=self.g;h=self.height*g
        # Four curling roots meet above the exact ground seam.
        for j in range(4):
            a=j*TAU/4+.4;self.tube([(8+math.cos(a)*2*g,.12,8+math.sin(a)*2*g),(8+math.cos(a)*.8*g,.3*g,8+math.sin(a)*.8*g),(8,.7*g,8)],.12*g,0,.2*g,5)
        if self.age==0:
            self.tube([(8,.15,8),(7.7,1.4,8),(8.1,2.5,8)],.2,0,.08)
            for j in range(3):self.leaf(8,1.4+j*.3,8,j*2.4+self.seed,2.3,1.1,1,.6,.4)
            if f in ['mushroom','shelf_mushroom']:self.bell(8,3.3,8,1.2)
            elif f in ['lotus','steam_lotus']:self.blossom(8,1.1,8,2,2,5,1)
            else:self.orb(8,2.7,8,.44,2,1.6)
            return self
        if f in ['moss','ore_moss']:
            for i in range(9):
                a=i*2.4;x=8+math.cos(a)*(1+i*.48);z=8+math.sin(a)*(1+i*.48)
                for j in range(3):self.leaf(x,.2,z,a+j*2,2*g,.8,1,.5,.3)
                if self.age>=2:self.tube([(x,.3,z),(x+.3,1.3*g,z),(x,2*g,z)],.08,0,.05,4);self.orb(x,2*g,z,.22,4,glow=True)
        elif f in ['lotus','steam_lotus']:
            for i in range(7):self.leaf(8,.3,8,i*TAU/7,6.6*g,4*g,1,.25,.4)
            self.blossom(8,1.2*g,8,5.5*g,2,9,3)
            self.orb(8,3.2*g,8,.75*g,4,glow=True)
        elif f in ['mushroom','shelf_mushroom']:
            for i in range(3 if self.age>1 else 1):
                x,z=[(8,8),(4.8,6.2),(11.1,9.8)][i];top=h*(.66-i*.12);rad=(4-i*.7)*g
                if getattr(self,'closed',False):rad*=.48;top+=g
                self.tube([(x,.15,z),(x-.5,top*.6,z+.3),(x,top,z)],.6*g,0,.25*g)
                for ring in range(4):
                    for j in range(12):
                        a=j*TAU/12;b=(j+1)*TAU/12
                        def p(t,s):return(x+math.cos(s)*rad*t,top+rad*(1.15 if getattr(self,'closed',False) else .44)*(1-t*t)+.12*math.cos(s*6),z+math.sin(s)*rad*t)
                        self.face([p(ring/4,a),p(ring/4,b),p((ring+1)/4,b),p((ring+1)/4,a)],2 if i%2==0 else 3,double=True)
                for j in range(12):
                    a=j*TAU/12;self.leaf(x,top-.14,z,a,rad*.95,.16,4,-.15,0)
                for j in range(7):
                    a=j*2.4;r=rad*.62;self.orb(x+math.cos(a)*r,top+rad*.3,z+math.sin(a)*r,.13*g,4,glow=True)
        elif f in ['fern','page_fern','fan','foldleaf','spiral']:
            for frond in range(5 if self.age>1 else 3):
                a=frond*TAU/5+self.seed*.01;length=(7+frond%2*2)*g
                pts=[(8+math.cos(a)*t*length, .3+math.sin(t*math.pi*.65)*h*.68,8+math.sin(a)*t*length) for t in [i/7 for i in range(8)]]
                self.tube(pts,.16,0,.04,5)
                for i,p in enumerate(pts[1:-1]):
                    for side in [-1,1]:self.leaf(*p,a+side*1.0, (2.5-i*.28)*g,1.15*g,2 if f=='page_fern' else 3 if f=='foldleaf' and side==1 else 1,.45,.4)
        elif f in ['grain','twin_grain','reed','dew_grass','dew_vine']:
            for i in range(5):
                a=i*2.4;x=8+math.cos(a)*2.4;z=8+math.sin(a)*2.4;top=h*(.75+i*.06)
                self.tube([(x,.2,z),(x+.35,top*.5,z),(x+math.cos(a)*1.4,top,z+math.sin(a)*1.4)],.14,0,.08,5)
                for j in range(2):self.leaf(x,1+j*top*.3,z,a+j*2.7,5*g,1.05,1,2*g,-.6)
                if self.age>=2:
                    for j in range(4):
                        xx=x+math.cos(a)*1.4;zz=z+math.sin(a)*1.4;y=top-j*.7
                        self.orb(xx+(j%2-.5)*.6,y,zz,.36*g,4 if f in ['dew_grass','dew_vine'] else 2 if i%2 else 3,1.5,glow=f in ['dew_grass','dew_vine'])
        elif f in ['gourd','root','berries','pepper','beans','pod','cluster','cotton']:
            self.tube([(8,.2,8),(7.5,h*.4,8),(8.6,h*.7,8.4),(8,h,8)],.35*g,0,.08)
            for i in range(3+self.age):
                a=i*2.4;yy=2+i*h/(4+self.age);r=3.5*g;xx=8+math.cos(a)*r;zz=8+math.sin(a)*r
                self.tube([(8,yy-1,8),(xx,yy,zz)],.16,0,.07,5)
                self.leaf(xx,yy,zz,a,3*g,2.1*g,1,1,.8)
                if self.age>=2:
                    if f=='cotton':
                        for j in range(3):self.orb(xx+(j-1)*.65,yy+.5+(j%2)*.4,zz,1*g,2 if j%2 else 3)
                    elif f in ['pepper','beans','pod']:
                        self.orb(xx,max(yy-.5,.65*g*2.5+.12),zz,.65*g,5,2.5);self.leaf(xx,yy+.3,zz,a+2,1.2,.7,1,-.5,.2)
                    elif f=='cluster':
                        for j in range(5):self.orb(xx+math.cos(j*2.4)*.7,yy-j*.3,zz+math.sin(j*2.4)*.7,.48*g,5)
                    else:self.orb(xx,yy+.6,zz,(1.5 if f=='gourd' else .7)*g,5,1.15)
            if f=='root':self.orb(8,1.4*g*1.2+.12,8,1.4*g,5,1.2)
        elif f in ['paper_tree','crystal_tree','vine']:
            pts=[(8+math.sin(i*.7)*g,.2+i*h/9,8+math.cos(i*.7)*g) for i in range(10)]
            self.tube(pts,.44*g,0,.11)
            for i in range(6):
                a=i*2.4;yy=2+i*h/7;xx=8+math.cos(a)*3*g;zz=8+math.sin(a)*3*g
                self.tube([(8,yy-1,8),(xx,yy,zz)],.22*g,0,.06)
                for j in range(3):self.leaf(xx,yy,zz,a+(j-1)*.8,3.4*g,2*g,2 if f=='paper_tree' else 1,.8,.5)
                if self.age==3:self.blossom(xx,yy+.3,zz,1.2*g,3,5,1) if f=='vine' else self.orb(xx,yy+.5,zz,.5,4,1.5,True)
        else:
            self.tube([(8,.2,8),(7.6,h*.45,8.3),(8.4,h*.8,7.8),(8,h,8)],.3*g,0,.12)
            for i in range(3+self.age):self.leaf(8,1+i*h/(5+self.age),8,i*2.4,4.1*g,2*g,1,1,.8)
            if f=='bells':
                for i in range(3):
                    a=i*TAU/3;xx=8+math.cos(a)*3*g;zz=8+math.sin(a)*3*g
                    self.tube([(8,h-2*g,8),(xx,h+1*g,zz),(xx,h,zz)],.13,0,.06,5);self.bell(xx,h,zz,1.5*g,2 if i%2 else 3)
            elif f in ['crystal_crown','storm']:
                for j in range(7):
                    a=j*TAU/7;xx=8+math.cos(a)*3*g;zz=8+math.sin(a)*3*g
                    self.leaf(8,h-3*g,8,a,4.5*g,1.5*g,3,3.5*g,-1)
                    self.orb(xx,h,zz,.55*g,4,2.2,True)
                self.blossom(8,h-1,8,3*g,2,6,1)
            elif f=='hourglass':
                self.bell(8,h+1.4*g,8,2.2*g,2);self.blossom(8,h-2.2*g,8,2.7*g,3,6,1)
                for i in range(5):self.orb(8,h-i*.5,8,.12,4,glow=True)
            elif f=='puff':
                for j in range(18):
                    a=j*2.4;yy=h+math.sin(j*1.8)*2*g;xx=8+math.cos(a)*2.7*g;zz=8+math.sin(a)*2.7*g
                    self.tube([(8,h,8),(xx,yy,zz)],.05,3,.03,4);self.blossom(xx,yy,zz,.6*g,3,4,1)
            else:
                self.blossom(8,h,8,(4.5 if f=='sunflower' else 3.8)*g,2,9 if f=='sunflower' else 5 if f in ['orchid','star'] else 7,3 if self.age==3 else 2,vertical=f in ['sunflower','orchid'])
                if f=='star':
                    for i in range(5):self.orb(8+math.cos(i*TAU/5)*3.5*g,h+1.8,8+math.sin(i*TAU/5)*3.5*g,.2,4,glow=True)
        return self

def main():
    report=[]
    for species in FAMILIES:
        path=ASSETS/'blockstates'/f'{species}.json'
        if not path.exists():raise FileNotFoundError(path)
        texture(species);variants=json.loads(path.read_text('utf-8-sig')).get('variants',{})
        maximum=max([int(m.group(1)) for key in variants for m in re.finditer(r'(?:^|,)age=(\d+)',key)] or [3]);seen=set()
        for key,value in variants.items():
            m=re.search(r'(?:^|,)age=(\d+)',key);age=min(3,round(int(m.group(1))*3/maximum)) if m else (3 if 'true' in key else 1)
            for name in model_refs(value):
                if not name.startswith('tnc:') or name in seen:continue
                seen.add(name);model=Sculpture(species,age)
                closed=species=='hushcap_mushroom' and name.endswith('_closed')
                model.closed=closed;model.build()
                wet='wet=true' in key
                if wet:
                    for i in range(7):
                        a=i*2.4;model.orb(8+math.cos(a)*2.5,3+age*2+i*.5,8+math.sin(a)*2.5,.18,4,1.35,True)
                if species=='warning_moss':
                    for face in model.q:
                        for p in face['vertices']:p[1],p[2]=p[2],16-p[1]
                tex=f'tnc:block/botanical/{species}'
                suffix=('_wet' if wet else '')+('_closed' if closed else '')
                geometry=ASSETS/'botanical_meshes'/f'{species}_{age}{suffix}.json';geometry.parent.mkdir(exist_ok=True)
                geometry.write_text(json.dumps(model.q,separators=(',',':'))+'\n',encoding='utf8')
                dump(ASSETS/'models'/(name.split(':')[1]+'.json'),dict(loader='tnc:botanical_mesh',render_type='minecraft:cutout',
                     ambientocclusion=False,glow_enabled='lit=false' not in key,
                     textures=dict(plant=tex,particle=tex),geometry=f'tnc:botanical_meshes/{species}_{age}{suffix}.json'))
        report.append(dict(species=species,family=FAMILIES[species],models=len(seen),mature_surfaces=len(Sculpture(species,3).build().q)))
    # The new mesh carries its own colour, including emissive nectar, no flat tint recolouring.
    dump(ASSETS/'plant_palettes.json',{s:[0xffffff]*5 for s in FAMILIES})
    dump(ROOT/'model-source/botanical/sculpture-manifest.json',report)
    print('Rebuilt',len(report),'species,',sum(x['models'] for x in report),'stage models')
if __name__=='__main__':main()
