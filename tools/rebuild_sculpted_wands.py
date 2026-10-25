"""Authored three-dimensional fantasy staffs; inventory paintings stay read-only.

No pixel extrusion or silhouette tracing. Each head is constructed around a
faceted core with spatial ribs, forged blades, inlays and deliberately open space.
All geometry is also exported as editable Blockbench meshes.
"""
from pathlib import Path
import math, hashlib
from PIL import Image
from rebuild_equipment_models import A, S, ELEMENTS, WAND_COLORS, write
from equipment_blockbench import export_wand

TAU = math.tau
def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def mul(a,b): return tuple(x*b for x in a)
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def unit(a): return mul(a,1/max(1e-9,math.sqrt(sum(x*x for x in a))))
def mix(a,b,t): return add(mul(a,1-t),mul(b,t))
def rgb(h): return tuple(int(h[i:i+2],16) for i in (1,3,5))

class Sculpt:
    def __init__(self,element,tier,base=False):
        self.element,self.tier=element,tier
        self.name='magic_wand' if base else f'{element}_wand_{tier}'
        wood,metal,gem=WAND_COLORS[element]
        jewel={'water':'#26cfff','fire':'#ff692b','lightning':'#839aff','wind':'#36edc4','earth':'#ffa840','light':'#ffe996','dark':'#de59fc'}[element]
        self.colors=[wood,metal,jewel,'#27202c','#e3c684', '#e5f7ef']
        self.surfaces=[]
        self.parts={}

    def quad(self,points,material=1,part='frame',bright=False):
        # A 32px material tile supplies directional edge highlights, grain and
        # inset engraving. Geometry itself supplies real shading and thickness.
        x=(material%4)*4;y=(material//4)*4
        self.surfaces.append(dict(vertices=[[round(v,5) for v in p] for p in points],
            uv=[[x+.15,y+.15],[x+3.85,y+.15],[x+3.85,y+3.85],[x+.15,y+3.85]],
            material=material,part=part,**({'glow':True} if bright else {})))
        self.parts[part]=self.parts.get(part,0)+1

    def tube(self,points,radii,material=1,sides=6,part='frame',bright=False):
        rings=[]
        for i,p in enumerate(points):
            direction=unit(sub(points[min(i+1,len(points)-1)],points[max(0,i-1)]))
            u=unit(cross(direction,(0,0,1) if abs(direction[2])<.9 else (1,0,0)))
            v=cross(direction,u)
            rings.append([add(p,add(mul(u,math.cos(TAU*j/sides)*radii[i]),mul(v,math.sin(TAU*j/sides)*radii[i]))) for j in range(sides)])
        for i in range(len(rings)-1):
            for j in range(sides):
                k=(j+1)%sides
                self.quad([rings[i][j],rings[i][k],rings[i+1][k],rings[i+1][j]],material,part,bright)
        for ring,p,reverse in [(rings[0],points[0],True),(rings[-1],points[-1],False)]:
            for j in range(sides):
                q=[p,ring[j],ring[(j+1)%sides],p]
                self.quad(q[::-1] if reverse else q,material,part,bright)

    def curve(self,points,radius,material=1,part='filigree',taper=.12,sides=5,bright=False):
        # Catmull-Rom rails give claws a sculpted arc, not a staircase of boxes.
        dense=[]
        for i in range(len(points)-1):
            p0,p1,p2,p3=points[max(0,i-1)],points[i],points[i+1],points[min(len(points)-1,i+2)]
            for j in range(4):
                t=j/4
                dense.append(tuple(.5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t) for a,b,c,d in zip(p0,p1,p2,p3)))
        dense.append(points[-1])
        self.tube(dense,[radius*(1-(1-taper)*(i/(len(dense)-1))**2) for i in range(len(dense))],material,sides,part,bright)

    def gem(self,center,size,material=2,part='crystal',sides=6,twist=0):
        start=len(self.surfaces)
        cx,cy,cz=center;rx,ry,rz=size
        levels=[(-1,0),(-.45,.84),(.25,1),(.64,.62),(1,0)]
        rings=[[(cx+rx*r*math.cos(TAU*i/sides+twist),cy+ry*y,cz+rz*r*math.sin(TAU*i/sides+twist)) for i in range(sides)] for y,r in levels]
        for level,(a,b) in enumerate(zip(rings,rings[1:])):
            for j in range(sides):
                k=(j+1)%sides
                self.quad([a[j],b[j],b[k],a[k]],material,part)
                if material==2:
                    # Continuous vertical facets, not a repeated crack decal on
                    # every small polygon. Offset the facet highlight per side.
                    x=(material%4)*4;y=(material//4)*4
                    v0=y+.1+(1-levels[level][0])*1.9
                    v1=y+.1+(1-levels[level+1][0])*1.9
                    u0=x+.15+(j%3)*.32;u1=x+3.85-(j%2)*.23
                    self.surfaces[-1]['uv']=[[u0,v0],[u0,v1],[u1,v1],[u1,v0]]
        if material==2 and center[1]>21:
            for face in self.surfaces[start:]:
                face['motion']='orbit' if any(word in part for word in ['satellite','moons','floating','orbiting']) else 'spin'
                face['pivot']=list(center)
                face['phase']=round(center[0]*.37+center[2]*.61,4)

    def band(self,y,radius=.64,height=.3,material=1):
        self.tube([(8,y-height/2,8),(8,y+height/2,8)],[radius]*2,material,8,'collar')

    def ring(self,center,radius,thickness,normal=(0,0,1),material=1,start=0,end=TAU,part='orbit'):
        n=unit(normal);u=unit(cross(n,(0,1,0) if abs(n[1])<.9 else (1,0,0)));v=cross(n,u)
        points=[add(center,add(mul(u,radius*math.cos(start+(end-start)*i/32)),mul(v,radius*math.sin(start+(end-start)*i/32)))) for i in range(33)]
        self.tube(points,[thickness]*len(points),material,5,part)

    def blade(self,points,widths,thickness=.24,material=1,part='blade'):
        # Four-sided diamond cross sections: two broad bevels and a raised spine.
        rings=[]
        for i,p in enumerate(points):
            t=unit(sub(points[min(i+1,len(points)-1)],points[max(0,i-1)]))
            u=unit(cross(t,(0,0,1)));v=unit(cross(t,u))
            rings.append([add(p,mul(u,widths[i]*.72)),add(p,mul(v,thickness)),add(p,mul(u,-widths[i]*.72)),add(p,mul(v,-thickness))])
        for a,b in zip(rings,rings[1:]):
            for j in range(4):self.quad([a[j],a[(j+1)%4],b[(j+1)%4],b[j]],material,part)
        self.quad(rings[0][::-1],material,part);self.quad(rings[-1],material,part)

    def shaft(self):
        t=self.tier
        self.tube([(8,.8,8),(8,2,8),(8,4,8),(8,10,8),(8,16,8),(8,20.5,8)], [.25,.48,.43,.4,.48,.7],0,8,'shaft')
        self.gem((8,.85,8),(.48,.7,.48),1,'pommel')
        # Wrapped grip and chased collars, distinct from smooth upper stave.
        for i in range(8):self.band(3+i*.6,.48,.12,3)
        for y in [2.1,2.6,8.1,8.55,17.7,18.2,19.5]:self.band(y,.57 if y<10 else .7,.22)
        for angle in [0,math.pi]:
            self.curve([(8+math.cos(angle+k*.22)*.45,9+k*1.25,8+math.sin(angle+k*.22)*.45) for k in range(8)],.085,4,'shaft_inlay',.8)
        for y in ([11,15] if t>=3 else [13]):self.gem((8,y,7.5),(.19,.7,.15),2,'inset')
        self.gem((8,19.3,8),(.95,1.4,.95),1,'socket')
        self.gem((8,19.4,7.1),(.38,.65,.25),2,'socket_jewel')
        if t>=3:
            for sign in [-1,1]:
                self.curve([(8,18,8),(8+sign*1.5,19,8.3),(8+sign*1.2,20.5,8.8)],.25,1,'neck_guard')
        if t>=4:
            self.curve([(7.5,17.4,8),(6.9,15.5,8.3),(7.2,13.8,8.8)],.12,1,'chain',.7)
            self.gem((7.2,13.3,8.8),(.28,.65,.28),2,'hanging_seal')

    def head(self):
        t=self.tier;e=self.element
        # Tier changes affect silhouette, negative space and number of spatial
        # layers, not just tiny gems. Keep a consistent grip across all models.
        spread=2.1+.42*t;cy=24
        if e=='fire':
            self.gem((8,24,8),(1.05+.13*t,2.65,1.05+.13*t),2,'ember')
            for i in range(3 if t<3 else 4):
                a=TAU*i/(3 if t<3 else 4)+.3
                p=lambda r,y:(8+math.cos(a)*r,y,8+math.sin(a)*r)
                self.curve([p(.4,20),p(spread,22),p(spread,25),p(1.1,27.6)],.47,1,'dragon_claw',.04,6)
                if t>=2:self.blade([p(spread,22.5),p(spread+1,24),p(spread+.55,26.5)],[.4,.7,.01],.24,0,'obsidian_scale')
            if t>=3:
                for sign in [-1,1]:
                    self.blade([(8+sign*2,23,8),(8+sign*4.2,25.2,8.6),(8+sign*3.8,28.3,9.2),(8+sign*2.1,29.7,9.7)],[.3,.9,.48,.01],.3,1,'swept_horn')
            if t>=4:self.ring((8,24,8),3.1,.13,(.3,1,.4),4,start=.3,end=5.9)
            if t==5:
                self.gem((8,29.4,8),(.5,1.1,.5),2,'crown_ember')
                for sign in [-1,1]:self.gem((8+sign*4.7,25.7,8.3),(.4,.9,.4),2,'satellite_ember')
        elif e=='water':
            self.gem((8,24.3,8),(.9+.13*t,2.9,1.05),2,'tide_drop')
            for sign in [-1,1]:
                self.blade([(8,20,8),(8+sign*spread,22.3,8.3),(8+sign*(spread+.4),25.5,8.8),(8+sign*1.7,28.9,8.4)],[.35,.75,.55,.01],.28,1,'tide_crown')
                self.curve([(8,20.5,7.8),(8+sign*(spread-.4),23,7.8),(8+sign*1.7,26.2,7.8)],.13,4,'wave_inlay')
            if t>=2:self.curve([(8,20,8),(6.7,23,6.5),(7.7,26.1,5.5),(9.4,27.7,6.3)],.28,1,'rear_wave',.05)
            if t>=3:
                self.ring((8,24,8),3.0,.15,(.3,1,.4),1,start=.2,end=5.6)
                for sign in [-1,1]:self.gem((8+sign*3.7,23.5,7.1),(.42,.9,.42),2,'water_moons')
            if t>=4:self.blade([(8,20.2,7.6),(8,24,4.8),(8,28.5,5.2),(8,30,6.8)],[.4,.55,.35,.01],.27,1,'rear_crown')
            if t==5:self.ring((8,24.5,8),4.65,.12,(.1,.35,1),4,start=.2,end=5.9)
        elif e=='lightning':
            self.gem((8,24.5,8),(.8+.1*t,2.3,.9),2,'storm_prism',sides=4,twist=.8)
            for i in range(3 if t<3 else 4):
                a=TAU*i/(3 if t<3 else 4)+.3
                p=lambda r,y:(8+math.cos(a)*r,y,8+math.sin(a)*r)
                self.tube([p(.4,20),p(spread*.8,22),p(spread*.7,24),p(spread,26.5),p(spread*.9,29)], [.42,.37,.28,.2,.03],1,5,'lightning_tine')
                self.gem(p(spread*.92,27.4),(.25,.7,.25),2,'charged_tip')
            if t>=2:self.ring((8,23,8),2.6,.16,(0,1,0),1,start=.1,end=5.8)
            if t>=3:self.ring((8,25.4,8),2.7,.15,(.5,1,.2),4,start=.5,end=5.6)
            if t>=4:
                for sign in [-1,1]:self.blade([(8+sign*2,22,8),(8+sign*4.4,24,8),(8+sign*3.4,25,8.3),(8+sign*4.8,28,8.5)],[.3,.65,.5,.01],.28,1,'forked_blade')
            if t==5:self.gem((8,29.8,8),(.5,.7,.5),2,'storm_star')
        elif e=='wind':
            self.gem((8,24,8),(1.05,2.0,1.05),2,'eye_of_gale')
            for sign in [-1,1]:
                self.blade([(8,20,8),(8+sign*spread,22,8+sign*.6),(8+sign*4,25,8+sign*1.1),(8+sign*2.7,28.8,8+sign*.6)],[.3,.75,.75,.01],.24,1,'feather_scythe')
                if t>=2:
                    for j in range(3):self.blade([(8+sign*(2.2+j*.4),23+j,8+sign*.5),(8+sign*(3.6+j*.35),25+j*.8,8+sign*.8),(8+sign*(3.2+j*.3),27+j*.6,8+sign*1.2)],[.22,.46,.01],.15,0,'feather')
            if t>=3:self.ring((8,24,8),2.9,.13,(1,.4,.5),4,start=.3,end=5.7)
            if t>=4:
                self.blade([(8,20.5,8),(8,22.5,10.9),(8,26,11.9),(8,29,10.2)],[.3,.7,.65,.01],.2,1,'rear_wing')
            if t==5:
                for sign in [-1,1]:self.gem((8+sign*4.7,27.5,8),(.3,.8,.3),2,'floating_feather')
        elif e=='earth':
            self.gem((8,24.2,8),(1.65,2.6,1.55),2,'geode',sides=6,twist=.3)
            for i in range(4):
                a=TAU*i/4+.8;p=lambda r,y:(8+math.cos(a)*r,y,8+math.sin(a)*r)
                self.tube([p(.5,20),p(spread*.8,21.8),p(spread,24.8),p(spread*.8,27.2)], [.55,.65,.52,.07],0,6,'root_buttress')
                self.curve([p(.7,20.1),p(spread*.82,22),p(spread*1.02,24.9)],.13,4,'root_gilding')
            if t>=2:self.ring((8,24,8),3.15,.32,(0,1,0),1,start=.3,end=5.9)
            if t>=3:
                for sign in [-1,1]:self.gem((8+sign*3.5,25.4,8),(.65,1.9,.65),1,'stone_crown')
            if t>=4:self.ring((8,24.5,8),4.2,.23,(0,0,1),4,start=0,end=math.pi*1.85)
            if t==5:
                for j in range(5):
                    a=math.pi*j/4
                    self.gem((8+math.cos(a)*4.6,25+math.sin(a)*4.6,8),(.42,.8,.42),2,'orbiting_geode')
        elif e=='light':
            self.gem((8,24,8),(.85,2.5,.85),2,'dawn_core')
            self.ring((8,24,8),2.5+.23*t,.22,(0,0,1),1,part='sun_halo')
            for sign in [-1,1]:
                self.curve([(8,20,8),(8+sign*1.5,21,8),(8+sign*2,23,8)],.3,1,'sun_cradle')
            for i in range(8 if t<3 else 12):
                a=TAU*i/(8 if t<3 else 12);r=2.5+.23*t
                p=(8+math.cos(a)*r,24+math.sin(a)*r,8)
                q=(8+math.cos(a)*(r+(.8 if i%2 else 1.4)),24+math.sin(a)*(r+(.8 if i%2 else 1.4)),8)
                self.tube([p,q],[.21,.015],1,4,'sun_ray')
            if t>=2:self.ring((8,24,8),2.1,.13,(1,.4,.35),4,part='astrolabe')
            if t>=3:
                for sign in [-1,1]:self.blade([(8+sign*1.3,20.5,8),(8+sign*4.2,21.7,8.3),(8+sign*4.7,25,8.8),(8+sign*3.4,27.2,9)],[.2,.7,.55,.01],.22,0,'ivory_wing')
            if t>=4:self.ring((8,24,8),3.8,.14,(.2,1,.2),1,part='equatorial_halo')
            if t==5:self.gem((8,30.1,8),(.45,.4,.45),2,'zenith_star')
        else:
            self.gem((8,24,8),(1.2,1.8,1.2),2,'void_heart')
            # Crescent sweeps around, in front of and behind the core.
            points=[(8+math.cos(a)*3.2,24+math.sin(a)*3.7,8+.65*math.sin(a*1.5)) for a in [math.radians(-75+i*30) for i in range(11)]]
            self.blade(points,[.03,.45,.8,1,1,.9,.8,.7,.5,.25,.01],.42,1,'crescent')
            self.curve([(8,20,8),(6.4,22,9.4),(6.8,25,10.5),(8.5,27.5,9.8)],.28,0,'rear_talon',.04)
            if t>=2:
                for sign in [-1,1]:self.gem((8+sign*2.3,22,7.1),(.35,.7,.35),2,'watching_eye')
            if t>=3:self.ring((8,24,8),2.5,.13,(.9,.4,.5),4,start=.5,end=5.7)
            if t>=4:self.blade([(8,20,8),(11.8,22.3,9),(12.7,26,10),(10.8,29.5,9)],[.2,.6,.6,.01],.3,0,'outer_crescent')
            if t==5:
                for sign in [-1,1]:self.gem((8+sign*4.5,26.4,8),(.35,.85,.35),2,'void_satellite')

    def save(self,base=False):
        self.shaft();self.head()
        # Extend the lower ferrule and insert six units of chased stave below
        # the crown. The head itself stays delicate, rather than being enlarged.
        self.tube([(8,-4,8),(8,.8,8)],[.22,.48],0,8,'lower_shaft')
        self.gem((8,-4,8),(.32,.65,.32),1,'lower_ferrule')
        for side in [-1,1]:
            self.curve([(8+side*.8,14,8),(8+side*1.1,17,8.5),(8+side*.7,19,9.1)],.085,2,'conduit_inlay',.6,5,True)
        def stretch(p):
            x,y,z=p
            return [8+(x-8)*(1.3 if y<18 else 1),y if y<=8 else 8+(y-8)*1.5 if y<20 else y+6,8+(z-8)*(1.3 if y<18 else 1)]
        for face in self.surfaces:
            face['vertices']=[[round(v,5) for v in stretch(p)] for p in face['vertices']]
            if 'pivot' in face:face['pivot']=stretch(face['pivot'])
        atlas=Image.new('RGBA',(256,256),(0,0,0,255));emission=Image.new('RGBA',(256,256),(0,0,0,255))
        for m,color in enumerate(self.colors):
            c=rgb(color);ox=(m%4)*64;oy=(m//4)*64
            for y in range(64):
                for x in range(64):
                    edge=min(x,y,63-x,63-y);noise=((x*73+y*151+x*y*13)%37)/37
                    if m==0:
                        grain=math.sin(x*.53+math.sin(y*.035)*1.4)
                        f=.45+.19*grain+.10*noise
                        if x%17==0:f+=.3
                        pixel=tuple(round(v*f) for v in c)
                    elif m==2:
                        # Irregular mineral cleavage, deep saturated facets and
                        # bright internal fissures, not one flat opaque polygon.
                        spine=math.exp(-((x-30-3*math.sin(y*.075))/5.5)**2)
                        edge_light=math.exp(-((x-5)/2.2)**2)*.55
                        cleavage=max(0,1-abs(x-52+y*.26)/.8)*.24
                        depth=.24+.29*math.sin(math.pi*x/64)**2+.03*noise
                        pale=min(.88,spine*.8+edge_light+cleavage)
                        pixel=tuple(round(v*depth*(1-pale)+255*pale) for v in c)
                        # Broad saturated inner radiance with a white-hot spine.
                        # Additive emission remains legible in vanilla daylight.
                        glow=.38+.20*math.sin(math.pi*y/64)+.45*spine
                        emission.putpixel((ox+x,oy+y),tuple(min(255,round(v*glow+120*spine+60*edge_light)) for v in c)+(255,))
                    elif m==3:
                        f=.42+.1*((y//4)%2)+.07*noise;pixel=tuple(round(v*f) for v in c)
                    else:
                        # Dark inset, slender specular bevels, chased scrollwork,
                        # directional brushing and sparse patina in the recesses.
                        f=.40+.07*noise+.15*max(0,math.cos((x-12)*.09))
                        if edge<1:f=.4
                        elif edge<3:f=1.05
                        elif edge<5:f=.65
                        scroll=abs(x-32-13*math.sin(y*.13))
                        if scroll<.8 or (y%21<2 and 15<x<49):f+=.32
                        pixel=tuple(round(v*f) for v in c)
                        if edge in [2,3]:pixel=tuple(round(v*.8+50) for v in c)
                    atlas.putpixel((ox+x,oy+y),tuple(min(255,max(0,v)) for v in pixel)+(255,))
        target=A/'textures/item/wands/held'/f'{self.name}.png';target.parent.mkdir(parents=True,exist_ok=True);atlas.save(target)
        emission.save(target.with_name(self.name+'_emission.png'))
        display={};tip=36.5
        for first in [False,True]:
            rotation=[0,-25,-10] if first else [90,0,0];scale=14/(tip-5) if first else .94
            rz,ry,rx=[math.radians(rotation[i]) for i in [2,1,0]];x,y,z=0,-3*scale,0
            x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
            x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
            y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
            for hand in ['righthand','lefthand']:
                display[('firstperson_' if first else 'thirdperson_')+hand]=dict(rotation=rotation,translation=[-x-(2.8 if first else 0),-y-(2.5 if first else 0),-z],scale=[scale]*3)
        display['ground']=dict(rotation=[0,0,15],translation=[0,3,0],scale=[.35]*3)
        icon=A/'textures/item'/('magic_wand.png' if base else f'wands/{self.name}.png')
        model=dict(loader='tnc:botanical_mesh',staff_render=True,surfaces=self.surfaces,elements=[],ambientocclusion=False,
            textures=dict(plant=f'tnc:item/wands/held/{self.name}',body=f'tnc:item/wands/held/{self.name}',particle=f'tnc:item/wands/held/{self.name}'),display=display,
            tnc_grip=[8,5,8],tnc_tip=[8,tip,8],tnc_reference_sha256=hashlib.sha256(icon.read_bytes()).hexdigest(),
            tnc_design='luminous_articulated_staff',tnc_parts=self.parts,tnc_color=list(rgb(self.colors[2])),tnc_core=[8,30,8],tnc_tier=self.tier)
        write(A/'models/item/wands/held'/f'{self.name}.json',model)
        export_wand(A/'models/item'/f'{self.name}.json')
        return dict(id=self.name,geometry=len(self.surfaces),parts=self.parts,depth=max(v[2] for f in self.surfaces for v in f['vertices'])-min(v[2] for f in self.surfaces for v in f['vertices']))

def build(element,tier,base=False):return Sculpt(element,tier,base).save(base)
if __name__=='__main__':
    results=[build(e,t) for e in ELEMENTS for t in range(1,6)]+[build('water',1,True)]
    write(S/'sculpted-wands.json',results)
    print('Built 36 authored volumetric staffs with distinct elemental architecture.')
