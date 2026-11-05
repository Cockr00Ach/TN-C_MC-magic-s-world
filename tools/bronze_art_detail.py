"""Quarter-unit cast bronze weapons and a finely articulated ritual bronze puppet.

All previews rasterize the shipped cuboids, rotations, UV nets and texture PNGs.
No game, concept image, additional combat controller or runtime texture generation.
"""
from pathlib import Path
import copy,hashlib,json,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources';ASSET=RES/'assets/tnc';PREVIEW=ROOT/'docs/previews'
NAMES={'greatsword':'古铜大剑','spear':'古铜长枪','dao':'古铜大刀','dagger':'古铜匕首','halberd':'青铜双刃'}
COLORS=[(106,91,56),(43,91,74),(174,153,100),(55,40,29),(133,113,68),(25,38,32),(151,130,80),(40,32,22)]
SIDES=('down','up','west','north','east','south')
INDICES=((5,4,0,1),(2,3,7,6),(0,4,7,3),(1,0,3,2),(5,1,2,6),(4,5,6,7))

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    # Dense vanilla models are compact JSON: the unchanged Windows PS 5.1 installer
    # uses JavaScriptSerializer's 2 MiB text limit. No geometry or UVs are removed.
    compact=path.parent==ASSET/'models/item' and path.name.startswith('bronze_')
    options={'separators':(',',':')} if compact else {'indent':2}
    path.write_text(json.dumps(value,ensure_ascii=False,**options)+'\n',encoding='utf-8')

def texture_tile(material,size=128):
    """Coherent oxidation islands, casting pits and restrained worn edges, not white noise."""
    rng=np.random.default_rng(6173+material*97)
    yy,xx=np.mgrid[:size,:size];x=xx/size;y=yy/size
    field=np.zeros((size,size),dtype=float)
    for _ in range(14):
        cx,cy=rng.uniform(-.2,1.2,2);radius=rng.uniform(.055,.19)
        field+=rng.uniform(.4,1)*np.exp(-((x-cx)**2+(y-cy)**2)/(radius*radius))
    base=np.array(COLORS[material],dtype=float)
    result=np.broadcast_to(base,(size,size,3)).copy()
    if material in (0,2,4,6):
        mask=field>.61
        green=np.array((44,91,73) if material==0 else (57,94,72))
        blend=np.clip((field-.61)*1.3,0,.88)[...,None]
        result=result*(1-blend)+green*blend
        border=(field>.57)&(field<.65)
        result[border]*=.78
        # Several sparse flaws connect across adjacent blade cells through world UVs.
        cracks=(np.sin(x*31+y*11)+np.sin(y*39-x*15)>1.82)&mask
        result[cracks]*=.67
    noise=rng.integers(-5,6,(size,size,1))
    pits=rng.random((size,size))<.012
    result+=noise;result[pits]*=.58
    if material==3:result+=np.sin(xx*.8)[...,None]*5
    return Image.fromarray(np.uint8(np.clip(result,0,255)),'RGB')

def weapon_atlas():
    atlas=Image.new('RGB',(512,256))
    for material in range(8):atlas.paste(texture_tile(material),(material%4*128,material//4*128))
    p=ASSET/'textures/item/bronze_atlas.png';p.parent.mkdir(parents=True,exist_ok=True);atlas.save(p)

class Weapon:
    def __init__(self):self.elements=[]
    def box(self,x0,y0,z0,x1,y1,z1,mat=0,name='cast'):
        if min(x1-x0,y1-y0,z1-z0)<.018:return
        xyz=[x0+8,y0,z0+8];end=[x1+8,y1,z1+8]
        assert all(-16<=a<b<=32 for a,b in zip(xyz,end)),(xyz,end)
        faces={}
        for side in SIDES:
            if side in ('north','south'):u0,u1=(x0+12)*3,(x1+12)*3;v0,v1=(32-y1)*2,(32-y0)*2
            elif side in ('west','east'):u0,u1=(z0+12)*3,(z1+12)*3;v0,v1=(32-y1)*2,(32-y0)*2
            else:u0,u1=(x0+12)*3,(x1+12)*3;v0,v1=(z0+12)*3,(z1+12)*3
            uv=[mat%4*4+u0/32,mat//4*8+v0/16,mat%4*4+u1/32,mat//4*8+v1/16]
            assert all(0<=v<=16 for v in uv)
            faces[side]={'uv':[round(v,5) for v in uv],'texture':'#bronze'}
        self.elements.append({'name':name,'from':[round(v,4) for v in xyz],'to':[round(v,4) for v in end],'faces':faces})
    def relief(self,x,y,scale=1,side='both',mat=6):
        strokes=[(0,0,1.55,.19),(1.36,0,1.55,1.85),(.15,1.66,1.55,1.85),(.15,.63,.34,1.85),(.15,.63,1.02,.82),(.83,.63,1.02,1.28)]
        for sign in ((-1,1) if side=='both' else (-1,)):
            for a,b,c,d in strokes:
                cx=x+(a+c)*scale/2;cy=y+(b+d)*scale/2
                surfaces=[e['to'][2]-8 for e in self.elements if e['from'][0]<=cx+8<=e['to'][0] and e['from'][1]<=cy<=e['to'][1] and e['from'][2]<=8<=e['to'][2]]
                if not surfaces:continue
                depth=max(surfaces)
                self.box(x+a*scale,y+b*scale,sign*depth-.065,x+c*scale,y+d*scale,sign*depth+.065,mat,'leiwen_relief')
    def grip(self,bottom,top,r=.6):
        self.box(-r,bottom,-r,r,top,r,3,'ancient_wood_core')
        for i,y in enumerate(np.arange(bottom+.18,top-.2,.5)):
            for ix in range(4):
                x=-r+ix*r/2;rise=(ix if i%2 else 3-ix)*.055
                for z in (-r-.055,r):self.box(x,float(y+rise),z,x+r/2,float(y+rise+.18),z+.055,4,'woven_leather')
        for y in (bottom,top-.45):
            for i in range(5):
                inset=abs(i-2)*.055
                self.box(-r-.2+inset,y+i*.09,-r-.16+inset,r+.2-inset,y+(i+1)*.09,r+.16-inset,0,'stepped_ferrule')
        # Real grooves and edge wear along a long bronze/wood shaft.
        if top-bottom>20:
            for y in np.arange(bottom+.7,top-1,.75):
                for x in (-r+.12,r-.2):self.box(x,float(y),-r-.035,x+.08,float(y+.42),-r+.015,7,'shaft_incision')
    def mask_guard(self,y,width=3):
        # A chamfered cast taotie, narrow recessed eyes, bridge and curled horns.
        for i in range(7):
            yy=y+i*.25;inset=abs(i-3)*.13
            self.box(-width+inset,yy,-.8,width-inset,yy+.25,.8,0,'taotie_guard')
        for sign in (-1,1):
            x=sign*(width*.53)
            self.box(x-.55,y+.53,-.91,x+.55,y+.95,-.8,5,'eye_recess')
            self.box(x-.27,y+.67,-.97,x+.27,y+.82,-.9,2,'eye_inlay')
            for j in range(5):
                xx=sign*(width-.4+j*.17)
                self.box(xx-.13,y+.9+j*.11,-.6,xx+.13,y+1.15+j*.11,.6,0,'horn')
            self.relief(sign*width*.6-.5,y+.05,.42)
        self.box(-.18,y+.3,-1.02,.18,y+1.5,1.02,6,'taotie_nose')
        for sign in (-1,1):
            for i in range(7):
                x=sign*(.28+i*.27)
                self.box(x-.085,y-.1-i*.035,-.91,x+.085,y+.04-i*.035,.91,4,'guard_lip')
    def blade(self,kind,start,end,wide):
        for y in np.arange(start,end,.25):
            p=(y-start)/(end-start)
            if kind=='dao':
                left=-2.7+p*2.2;right=2.6+p*1.6
                if p>.78:left=right-(right-left)*(1-p)/.22
            else:
                taper=(1-p)**.65 if kind in ('spear','dagger','halberd') else min(1,(1-p)*5.2)
                left=-max(.12,wide*taper);right=max(.12,wide*taper)
            # Broad faces are half-unit cells; the silhouette and edge bevel use quarter units.
            positions=[left];x=math.ceil((left+.25)/.5)*.5
            while x<right-.25:positions.append(x);x+=.5
            positions.append(right)
            for xa,xb in zip(positions,positions[1:]):
                dist=min((xa+xb)/2-left,right-(xa+xb)/2)
                thickness=min(.68,.13+dist*.37)
                mat=2 if dist<.3 else 0
                self.box(xa,float(y),-thickness,xb,float(min(y+.25,end)),thickness,mat,'bevelled_blade')
            if kind!='dao' and p<.88:
                self.box(-.12,float(y),-.82,.12,float(y+.25),.82,6,'cast_mid_rib')
        if kind=='greatsword':
            for y in (6,10.2,14.4,18.6,22.8):
                for x in (-2.3,.65):self.relief(x,y,.85)
        elif kind=='dao':
            for i,y in enumerate((7,11,15,19)):
                self.relief(-1.5+i*.37,y,.75)
        else:
            for y in (start+1,start+4):
                for x in (-1.5,.48):self.relief(x,y,.48 if kind!='dagger' else .58)
    def crescent(self):
        # A hollow crescent outline, a forged bevel and two physically joined bridges.
        for y in np.arange(17,28.5,.25):
            p=(y-17)/11.5
            outer=-1.2-6.3*math.sin(math.pi*p)**.75
            inner=-.9-3.7*math.sin(math.pi*p)**.75
            for x in np.arange(outer,inner,.4):
                end=min(x+.4,inner);edge=(x-outer)<.4
                self.box(float(x),float(y),-.47 if edge else -.65,float(end),float(y+.25),.47 if edge else .65,2 if edge else 0,'crescent_blade')
        for y in (19,25.5):
            for x in np.arange(-4.8,.3,.35):self.box(float(x),y,-.55,float(x+.35),y+.55,.55,0,'crescent_bridge')
        for y in (19.4,22,24.6):self.relief(-5.1,y,.65)

def weapon(kind):
    if kind=='halberd':return twin_pair()
    w=Weapon()
    if kind in ('spear','halberd'):
        w.grip(-14.5,15.2,.46);w.mask_guard(14,1.65);w.blade(kind,17,31,2.6)
        w.box(-.43,15.15,-.43,.43,17.5,.43,0,'continuous_blade_socket')
        for yy in np.arange(15.5,17,.25):w.box(-.5,float(yy),-.5,.5,float(yy+.12),.5,6,'socket_ridge')
        for i in range(8):
            inset=abs(i-3.5)*.045
            w.box(-.63+inset,-15.5+i*.18,-.63+inset,.63-inset,-15.5+(i+1)*.18,.63-inset,0,'butt_cap')
        for yy in (4,8,12):
            for i in range(4):w.box(-.53,yy+i*.18,-.57,.53,yy+i*.18+.08,.57,6,'cast_shaft_ring')
        if kind=='halberd':w.crescent()
    else:
        w.grip(-8.8 if kind!='dagger' else -3.2,2,.68 if kind!='dagger' else .53)
        w.mask_guard(2,3.7 if kind=='greatsword' else 2.65)
        w.blade(kind,4,30.75 if kind=='greatsword' else 29 if kind=='dao' else 21.5,3 if kind=='greatsword' else 2.6)
        if kind=='dao':
            for y in np.arange(-12,-9,.25):
                w.box(-1.35,float(y),-.38,-.85,float(y+.25),.38,0,'ring_pommel')
                w.box(.85,float(y),-.38,1.35,float(y+.25),.38,0,'ring_pommel')
            w.box(-1.35,-12,-.38,1.35,-11.5,.38,0,'ring_pommel');w.box(-1.35,-9.5,-.38,1.35,-9,.38,0,'ring_pommel')
        else:
            y=-9.8 if kind=='greatsword' else -4.3
            w.mask_guard(y,1.2)
    scale={'greatsword':.85,'spear':.9,'dao':.8,'dagger':.65,'halberd':.9}[kind]
    return {'credit':'TN-C quarter-unit cast bronze; tools/bronze_art_detail.py','textures':{'bronze':'tnc:item/bronze_atlas','particle':'tnc:item/bronze_atlas'},'gui_light':'front',
        'display':{'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,2.5,1],'scale':[scale]*3},'thirdperson_lefthand':{'rotation':[0,90,0],'translation':[0,2.5,1],'scale':[scale]*3},'firstperson_righthand':{'rotation':[0,-90,15],'translation':[1,2,-2],'scale':[scale*.75]*3},'firstperson_lefthand':{'rotation':[0,90,-15],'translation':[1,2,-2],'scale':[scale*.75]*3},'gui':{'rotation':[0,0,-30],'translation':[0,-1,0],'scale':[.33 if kind in ('spear','halberd') else .4]*3},'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25]*3},'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.4]*3}},'elements':w.elements}

def twin_component(which):
    """Reference silhouette: a wide fullered sword and a recurved blade with a reverse tail."""
    w=Weapon();w.grip(-8.8,2,.57)
    if which=='main':
        profile=[(4,-3.1,3.1),(5,-4.5,4.5),(6.2,-3.4,3.4),(8,-3,3),(11,-3.3,3.3),(19,-3.0,3.0),(24,-2.1,2.1),(29,-.65,.65),(31,-.035,.035)]
    else:
        profile=[(4,-1.5,1.9),(5.3,-2.9,3.1),(6.3,-1.6,3.35),(9,-1.5,4.0),(10.4,-2.6,4.45),(11.5,-1.25,4.65),(14,-1,5.1),(15.2,-1.9,5.2),(16.1,-.2,5.15),(20,.3,4.7),(23,-.1,3.7),(26,-1.1,1.8),(28,-2,.2),(30,-2.75,-2.69)]
    def bounds(y):
        for a,b in zip(profile,profile[1:]):
            if a[0]<=y<=b[0]:
                t=(y-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t
        return profile[-1][1:]
    for y in np.arange(4,profile[-1][0],.25):
        left,right=bounds(float(y+.125));xs=[left]+[float(v) for v in np.arange(math.ceil(left/.5)*.5,right,.5)]+[right]
        hole=which=='main' and 13<y<23
        if hole:xs=sorted(set(xs+[-.2,.2]))
        for xa,xb in zip(xs,xs[1:]):
            if hole and xa>=-.201 and xb<=.201:continue
            dist=min((xa+xb)/2-left,right-(xa+xb)/2);d=min(.64,.12+dist*.33)
            w.box(xa,float(y),-d,xb,float(y+.25),d,2 if dist<.28 else 0,'forged_twin_bevel')
        if which=='main' and y<27:
            for x in ((-.33,.22) if hole else (-.1,)):w.box(x,float(y),-.69,x+.11,float(y+.25),.69,6,'fuller_shoulder')
    # Angular horned guard and nested bronze fastening plates.
    for i,width in enumerate((1.7,2.3,3.0,3.8,4.1,3.55)):
        size=width if which=='main' else width*.72
        w.box(-size,2+i*.25,-.77,size,2+(i+1)*.25,.77,0,'horned_guard')
    for y,width in ((3.5,1.6),(3.75,1.4),(4,1.2),(4.25,1.0)):
        w.box(-width,y,-.74,width,y+.25,.74,4,'socket_plate')
    def pin(x,y,r=.29):
        for i in range(8):
            angle=i*math.pi/4
            for z in (-.80,.68):w.box(x+math.cos(angle)*r-.08,y+math.sin(angle)*r-.08,z,x+math.cos(angle)*r+.08,y+math.sin(angle)*r+.08,z+.12,6,'bronze_pin_ring')
        w.box(x-.15,y-.15,-.86,x+.15,y+.15,.86,2,'pin_head')
    if which=='main':
        for x,y in ((-2.6,4.7),(2.6,4.7),(0,3.15)):pin(x,y)
        for y in (7,10,24):
            for x in (-2.2,.6):w.relief(x,y,.68)
        for i in range(8):
            width=.88-abs(i-3.5)*.10
            w.box(-width,-10.6+i*.22,-.53,width,-10.6+(i+1)*.22,.53,0,'pointed_cast_pommel')
        pin(0,-9.6,.21)
    else:
        for x,y in ((-1.0,5.4),(-.6,10.7),(.7,16.0)):pin(x,y,.23)
        for x,y in ((-.85,7),(0,12),(1.5,18)):w.relief(x,y,.65)
        # The second blade has a short, sharp reverse spike at the pommel.
        w.box(-.4,-9.6,-.45,.4,-8.7,.45,4,'tail_socket')
        for y in np.arange(-15.5,-9.4,.25):
            t=(y+15.5)/6.1;width=max(.055,1.2*t)
            for xa in np.arange(-width,width,.35):w.box(float(xa),float(y),-.27,float(min(xa+.35,width)),float(y+.25),.27,2 if abs(xa)>width-.4 else 0,'reverse_tail_blade')
        pin(0,-9.7,.22)
    result=weapon('dagger');result['credit']='TN-C inseparable bronze twinblades; tools/bronze_art_detail.py'
    # Both grips share the item pivot (8,8,8), so either hand clamps the actual handle.
    for element in w.elements:
        for key in ('from','to'):
            x,y,z=element[key];element[key]=[round(8+(x-8)*.75,4),round(8+(y+3.3)*.68,4),round(8+(z-8)*.75,4)]
    result['elements']=w.elements
    result['display']={
        'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,0,0],'scale':[1,1,1]},
        'thirdperson_lefthand':{'rotation':[0,90,0],'translation':[0,0,0],'scale':[1,1,1]},
        'firstperson_righthand':{'rotation':[0,-90,15],'translation':[0,0,0],'scale':[.88]*3},
        'firstperson_lefthand':{'rotation':[0,90,-15],'translation':[0,0,0],'scale':[.88]*3},
        'gui':{'rotation':[0,0,-18],'translation':[0,-1,0],'scale':[.40]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4]*3},
        'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.4]*3}}
    return result

def twin_pair():
    main=twin_component('main');off=twin_component('off')
    for name,model in (('bronze_twin_main',main),('bronze_twin_off',off)):write(ASSET/f'models/item/{name}.json',model)
    combined=copy.deepcopy(main);combined['parent']='builtin/entity';combined['elements']=[]
    for offset,model in ((-4.8,main),(4.8,off)):
        for original in model['elements']:
            element=copy.deepcopy(original)
            for key in ('from','to'):element[key][0]=round(element[key][0]+offset,4)
            combined['elements'].append(element)
    combined['display']['gui']['scale']=[.31]*3
    return combined

class Puppet:
    def __init__(self):self.parts=[];self.boxes=[]
    def part(self,name,pivot):
        self.current={'name':name,'pivot':pivot,'boxes':[]};self.parts.append(self.current)
    def b(self,x,y,z,w,h,d,mat=0,name='cast_plate',rotation=None):
        assert min(w,h,d)>.015,(name,w,h,d)
        box={'name':name,'box':[round(v,4) for v in (x,y,z,w,h,d)],'material':mat}
        if rotation:box['rotation']=rotation
        self.current['boxes'].append(box);self.boxes.append(box)
    def slab(self,x,y,z,w,h,d,mat=0,name='chamfered_plate',steps=5):
        # Layered bevel gives a cut corner on all four edges, rather than a large flat cube.
        bevel=min(.38,w*.13,h*.13)
        for i in range(steps):
            inset=bevel*abs((i+.5)/steps*2-1)
            self.b(x+inset,y+i*h/steps,z,w-inset*2,h/steps,d,mat,name)
    def line(self,x,y,z,points,width=.12,depth=.09,mat=6,name='cast_leiwen'):
        for (ax,ay),(bx,by) in zip(points,points[1:]):
            if ax==bx:self.b(x+ax-width/2,y+min(ay,by),z,width,abs(by-ay)+width,depth,mat,name)
            elif ay==by:self.b(x+min(ax,bx),y+ay-width/2,z,abs(bx-ax)+width,width,depth,mat,name)
            else:
                length=math.hypot(bx-ax,by-ay);angle=math.degrees(math.atan2(by-ay,bx-ax))
                self.b(x+(ax+bx)/2-length/2,y+(ay+by)/2-width/2,z,length,width,depth,mat,name,[0,0,angle])
    def spiral(self,x,y,z,s=1,mirror=1):
        pts=[(0,0),(1.5,0),(1.5,1.7),(.12,1.7),(.12,.65),(.91,.65),(.91,1.12)]
        self.line(x,y,z,[(a*s*mirror,b*s) for a,b in pts],.12*s,.10,6)
    def stud(self,x,y,z,r=.2):
        self.b(x-r,y-r,z,r*2,r*2,.10,4,'rivet_base')
        self.b(x-r*.58,y-r*.58,z-.06,r*1.16,r*1.16,.09,2,'worn_rivet')
    def joint(self,x,y,z,r=1.9):
        self.b(x-r*.7,y-r*.7,z,r*1.4,r*1.4,.25,5,'recessed_joint')
        for i in range(8):
            angle=i*math.pi/4;xx=x+math.sin(angle)*r;yy=y+math.cos(angle)*r
            self.b(xx-.3,yy-.3,z-.18,.6,.6,.45,0,'faceted_joint_ring',[0,0,-i*45])
        self.b(x-.6,y-.6,z-.27,1.2,1.2,.4,4,'hinge_hub')

def puppet_body():
    p=Puppet();p.part('head',[0,0,0])
    # Quiet, tapered mask silhouette. Fine volumes carry the expression, not a painted face.
    for i in range(20):
        y=-10+i*.5;cut=max(0,(-8.3-y)*.22,(y+3)*.25)
        p.b(-4.55+cut,y,-3.65,9.1-cut*2,.5,7.3,0,'tapered_mask')
    p.slab(-4.1,-9.9,-3.95,8.2,1.6,7.8,4,'crown_band')
    for sign in (-1,1):
        for j in range(6):
            xx=sign*(4.4-j*.25);yy=-9.6-j*.23
            p.b(xx-.32,yy,-2,.64,.42,2.7,0,'short_cast_horn')
        # Recess, actual pupil, drooping inward upper lid and strong angry brow.
        cx=sign*2.04
        p.b(cx-1.32,-6.15,-3.89,2.64,1.35,.32,5,'sunken_eye_socket')
        p.b(cx-1.0,-5.77,-4.04,2.0,.42,.19,4,'amber_eye_inlay')
        p.b(cx-.19,-5.82,-4.13,.38,.54,.11,7,'narrow_eye_pupil')
        p.b(cx-1.43,-6.74,-4.49,2.86,.66,.83,6,'angry_brow',[0,0,-sign*13])
        p.b(cx-1.1,-5.24,-4.19,2.20,.22,.29,0,'lower_eyelid',[0,0,sign*5])
        p.slab(sign*3.45-.64,-4.8,-4.25,1.28,2.2,.68,0,'cheekbone',4)
        p.line(sign*3.65,-4.6,-4.35,[(0,0),(-sign*.6,.4),(-sign*.8,1.5)],.16,.12,6,'cheek_fold')
        p.spiral(sign*3.55,-8.7,-4.06,.62,-sign)
        p.spiral(sign*3.8,-3.3,-4.16,.56,-sign)
        # Rolled ears have an open center and nested cast rims.
        p.b(sign*4.58-.43,-6.4,-.4,.86,1.2,.8,0,'ear_root')
        for i in range(12):
            angle=i*math.pi/6;yy=-5.8+math.sin(angle)*1.6;zz=math.cos(angle)*1.1
            p.b(sign*5.05-.28,yy-.25,zz-.25,.56,.50,.50,4,'rolled_ear')
        for yy in (-8.5,-1.3):p.stud(sign*3.7,yy,-4.09,.14)
    # Pronounced glabella, long fluted bridge and separate nostrils.
    for j in range(12):
        yy=-7.1+j*.4;ww=.60+j*.038;zz=-4.25-min(j,8)*.065
        p.b(-ww/2,yy,zz,ww,.4,-3.6-zz,6,'fluted_nose')
    for sign in (-1,1):
        p.b(sign*.64-.32,-3.2,-4.72,.64,.58,.65,0,'nose_wing')
        p.b(sign*.65-.14,-2.8,-4.81,.28,.18,.10,7,'nostril')
        p.line(sign*.25,-2.4,-4.1,[(0,0),(sign*.65,.22),(sign*1.65,.46)],.18,.18,5,'stern_mouth_seam')
        p.line(sign*.2,-2.12,-4.27,[(0,0),(sign*.8,.15),(sign*1.6,.38)],.22,.27,6,'downturned_lip')
    p.slab(-2.4,-1.6,-4.18,4.8,1.5,.70,0,'firm_chin')
    for i in range(7):p.b(-1.23+i*.36,-1.07,-4.42,.16,.45,.22,6,'chin_cast_ridge')
    for yy in (-8.5,-6.8,-5.1,-3.4):
        p.spiral(-2.9,yy,3.68,.65);p.spiral(.7,yy,3.68,.65)
    p.part('hat',[0,0,0])
    p.part('body',[0,0,0])
    p.slab(-2,-.8,-2,4,2,4,5,'neck_core')
    for i in range(4):p.b(-2.25,-.55+i*.34,-2.25,4.5,.17,4.5,4,'neck_collar')
    for i in range(20):
        y=i*.5;width=10.5-y*.22
        p.b(-width/2,y,-2.75,width,.5,5.5,0,'tapered_torso')
    for sign in (-1,1):
        x=-4.8 if sign<0 else .3
        p.slab(x,.65,-3.10,4.5,5.6,.65,0,'breastplate',7)
        p.spiral(x+.45,1.18,-3.28,1.02)
        p.spiral(x+2.3,3.6,-3.28,.62,-1)
        for yy in (1.1,5.55):
            for xx in (x+.3,x+4.15):p.stud(xx,yy,-3.32,.14)
        p.line(x+.3,6.7,-2.98,[(0,0),(3.6,0),(3.6,1.5)],.15,.13)
        for j in range(4):p.slab(sign*2.5-1.1,9.2+j*.65,-3.3-j*.025,2.2,.9,.3,0,'overlapping_apron',3)
    p.slab(-4.3,8.5,-3.15,8.6,1.3,6.3,4,'engraved_belt',4)
    p.slab(-1.4,8.7,-3.60,2.8,1.6,.55,0,'ritual_belt_buckle',4)
    p.spiral(-.9,8.9,-3.79,.85)
    for i in range(12):
        xx=-3.9+i*.7;p.stud(xx,9.04,-3.28,.12)
    for j in range(8):
        p.b(-.23,j*.95,2.78,.46,.63,.25,6,'cast_back_spine')
        p.spiral(-3.65,.9+j*.92,2.9,.48)
        p.spiral(2.8,.9+j*.92,2.9,.48,-1)
    for side,name in ((-1,'right_arm'),(1,'left_arm')):
        p.part(name,[side*6,2,0])
        p.slab(-2.65,-2,-2.65,5.3,3.4,5.3,0,'shoulder_casting',7)
        for j in range(3):p.slab(-2.9-j*.1,-1.5+j*.7,-2.9,5.8+j*.2,.85,5.8,4 if j==0 else 0,'shoulder_lame',4)
        for x in (-2.2,2.2):p.stud(x,-.4,-3.01,.17)
        p.spiral(-1.45,-.6,-3.03,.9)
        p.slab(-1.9,1.3,-1.9,3.8,3.8,3.8,0,'upper_arm',6)
        p.spiral(-1.15,2.0,-2.05,.83)
        p.joint(0,5.35,-2.05,1.45)
        p.slab(-2.05,6.25,-2.35,4.1,3.4,4.7,0,'fluted_bracer',7)
        for i in range(7):
            xx=-1.65+i*.55;p.b(xx,6.6,-2.5,.13,2.65,.22,6,'bracer_ridge')
        for yy in (6.35,9.25):p.b(-2.18,yy,-2.48,4.36,.24,4.96,4,'bracer_rim')
        p.slab(-1.75,9.65,-1.85,3.5,.75,3.7,5,'wrist_joint',3)
        p.slab(-1.75,10.05,-1.72,3.5,1.45,3.25,0,'palm',4)
        # Four separate curled fingers plus an opposed thumb, all with knuckles and seams.
        for i in range(4):
            xx=-1.59+i*.82
            p.slab(xx,10.9,-1.78,.66,.74,.78,0,'finger_proximal',3)
            p.b(xx+.04,11.53,-1.30,.58,.68,1.05,4,'finger_curled_tip',[22,0,0])
            p.stud(xx+.33,11.13,-1.91,.11)
        p.b(side*1.48-.35,10.34,-2.08,.7,1.6,.9,0,'opposed_thumb',[0,side*15,side*22])
    for side,name in ((-1,'right_leg'),(1,'left_leg')):
        p.part(name,[side*2.5,12,0])
        p.slab(-2.1,0,-2.25,4.2,3.9,4.5,0,'thigh_casting',7)
        p.spiral(-1.2,.55,-2.4,.87)
        for xx in (-1.65,1.65):p.stud(xx,.6,-2.4,.12)
        p.joint(0,4.7,-2.2,1.48)
        p.slab(-2.14,5.65,-2.65,4.28,3.55,5,0,'greave',7)
        for i in range(7):
            xx=-1.7+i*.55;p.b(xx,6.05,-2.82,.12,2.8,.21,6,'greave_ridge')
        p.spiral(-1.45,6.0,-2.91,.7)
        p.slab(-1.8,9,-1.8,3.6,.8,3.6,5,'ankle_joint',3)
        p.slab(-2.65,9.65,-3.8,5.3,2.3,7.4,0,'cast_foot',6)
        for j in range(5):p.b(-2.1+j*.88,10.55,-4.02,.70,1.15,.54,4,'toe_plate')
        for xx in (-2.2,2.2):p.stud(xx,10.2,-3.98,.14)
    return pack_puppet(p)

def pack_puppet(p):
    density=8
    for virtual in (128,256):
        shelves=[];bottom=0;fits=True
        for b in sorted(p.boxes,key=lambda b:-(b['box'][4]+b['box'][5])):
            _,_,_,w,h,d=b['box'];ww=math.ceil(2*(w+d))+2;hh=math.ceil(h+d)+2
            shelf=next((s for s in shelves if s[1]>=hh and s[2]+ww<=virtual),None)
            if shelf is None:
                if bottom+hh>virtual:fits=False;break
                shelf=[bottom,hh,0];shelves.append(shelf);bottom+=hh
            b['uv']=[shelf[2]+1,shelf[0]+1];shelf[2]+=ww
        if fits:break
    assert fits,'Puppet UV packing failed'
    size=virtual*density;texture=Image.new('RGB',(size,size),(24,30,25))
    tiles=[np.array(texture_tile(m,128)) for m in range(8)]
    for b in p.boxes:
        _,_,_,w,h,d=b['box'];u,v=b['uv'];material=b['material']
        tile=tiles[material];ww=math.ceil(2*(w+d)*density)+1;hh=math.ceil((h+d)*density)+1
        yy,xx=np.mgrid[:hh,:ww];seed=int(hashlib.sha1(b['name'].encode()).hexdigest()[:6],16)
        x,y,z=b['box'][:3]
        patch=tile[(yy+seed//128+round(y*density))%128,(xx+seed+round(x*density))%128]
        texture.paste(Image.fromarray(patch,'RGB'),(u*density,v*density))
    target=ASSET/'textures/entity/bronze_puppet.png';target.parent.mkdir(parents=True,exist_ok=True);texture.save(target)
    result={'format':2,'texture_width':size,'texture_height':size,'texel_density':density,'parts':p.parts}
    # AzureLib discovers every geo/*.json, including custom data with no Bedrock geometry.
    write(ASSET/'puppet/bronze_puppet_parts.json',result)
    legacy=ASSET/'geo/entity/bronze_puppet_parts.json'
    if legacy.exists():legacy.unlink()
    return result

def rotation(degrees):
    x,y,z=np.radians(degrees);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def corners(box):
    x,y,z,w,h,d=box
    return np.array([(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z),(x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)],dtype=float)

def cube_uv(box,uv,density):
    w,h,d=box[3:];u,v=uv
    nets=[(u+d,v,u+d+w,v+d),(u+d+w,v+d,u+d+w+w,v),(u,v+d,u+d,v+d+h),(u+d,v+d,u+d+w,v+d+h),(u+d+w,v+d,u+d+w+d,v+d+h),(u+d+w+d,v+d,u+d+w+d+w,v+d+h)]
    return {side:np.array([(b,c),(a,c),(a,e),(b,e)])*density for side,(a,c,b,e) in zip(SIDES,nets)}

def weapon_scene(kind,shift=(0,0,0),scale=1,flip_y=True):
    m=json.loads((ASSET/f'models/item/bronze_{kind}.json').read_text(encoding='utf-8'))
    tex=np.array(Image.open(ASSET/'textures/item/bronze_atlas.png').convert('RGB'));items=[]
    for e in m['elements']:
        a=np.array(e['from']);b=np.array(e['to']);box=[*a,*(b-a)];pts=corners(box);pts[:,0]-=8;pts[:,2]-=8
        if flip_y:pts[:,1]*=-1
        pts=pts*scale+shift
        uv={}
        for side,f in e['faces'].items():
            a,b,c,d=f['uv'];uv[side]=np.array([(c,b),(a,b),(a,d),(c,d)])*[tex.shape[1]/16,tex.shape[0]/16]
        items.append((pts,uv,tex))
    return items

def puppet_scene(head_only=False,armed=True):
    data=json.loads((ASSET/'puppet/bronze_puppet_parts.json').read_text(encoding='utf-8'))
    tex=np.array(Image.open(ASSET/'textures/entity/bronze_puppet.png').convert('RGB'));items=[]
    poses={'right_arm':[-7,0,12],'left_arm':[-7,0,-12]}
    for part in data['parts']:
        if head_only and part['name']!='head':continue
        matrix=rotation(poses.get(part['name'],[0,0,0]))
        for b in part['boxes']:
            box=b['box'];pts=corners(box)
            if 'rotation' in b:
                center=np.array(box[:3])+np.array(box[3:])/2;pts=(pts-center)@rotation(b['rotation']).T+center
            pts=pts@matrix.T+part['pivot'];items.append((pts,cube_uv(box,b['uv'],data['texel_density']),tex))
    if armed and not head_only:
        for part,side,mesh in (('right_arm',-1,'twin_main'),('left_arm',1,'twin_off')):
            hand=np.array([0,10.95,-.8])@rotation(poses[part]).T+np.array([side*6,2,0])
            items+=weapon_scene(mesh,hand+np.array([0,8*.9,0]),.9)
    return items

def raster(scene,width,height,azimuth=.34,pitch=.14,padding=.08):
    """Textured orthographic triangles with a depth buffer; hidden faces stay hidden."""
    bg=np.zeros((height,width,3),dtype=np.uint8);bg[:]=(24,29,27);depth=np.full((height,width),np.inf,dtype=np.float32)
    co,si=math.cos(azimuth),math.sin(azimuth);cp,sp=math.cos(pitch),math.sin(pitch)
    view=np.array([[co,0,si],[si*sp,cp,-co*sp],[-si*cp,sp,co*cp]])
    allpoints=np.concatenate([pts@view.T for pts,_,_ in scene]);lo=allpoints[:,:2].min(0);hi=allpoints[:,:2].max(0)
    zoom=min(width*(1-padding*2)/(hi[0]-lo[0]),height*(1-padding*2)/(hi[1]-lo[1]));center=(lo+hi)/2
    light=np.array([-.25,-.85,-.7]);light/=np.linalg.norm(light)
    for points,uv,tex in scene:
        orientation=1 if np.linalg.det(np.stack([points[1]-points[0],points[3]-points[0],points[4]-points[0]]))>0 else -1
        projected=points@view.T;projected[:,:2]=(projected[:,:2]-center)*zoom+[width/2,height/2]
        for side,indices in zip(SIDES,INDICES):
            poly=projected[list(indices)];ab=poly[1,:2]-poly[0,:2];ac=poly[2,:2]-poly[0,:2];cross=ab[0]*ac[1]-ab[1]*ac[0]
            if cross*orientation>=-1e-5:continue
            normal=np.cross(points[indices[1]]-points[indices[0]],points[indices[2]]-points[indices[0]])
            normal*=orientation;normal/=max(1e-8,np.linalg.norm(normal));shade=.65+.43*max(0,float(normal@light))
            for ids in ((0,1,2),(0,2,3)):
                tri=poly[list(ids)];tu=uv[side][list(ids)]
                x0=max(0,int(np.floor(tri[:,0].min())));x1=min(width-1,int(np.ceil(tri[:,0].max())))
                y0=max(0,int(np.floor(tri[:,1].min())));y1=min(height-1,int(np.ceil(tri[:,1].max())))
                if x1<x0 or y1<y0:continue
                yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
                a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-9:continue
                wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
                wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
                zz=wa*a[2]+wb*b[2]+wc*c[2];region=depth[y0:y1+1,x0:x1+1];mask=(wa>=-1e-6)&(wb>=-1e-6)&(wc>=-1e-6)&(zz<region)
                if not np.any(mask):continue
                tx=np.clip((wa*tu[0,0]+wb*tu[1,0]+wc*tu[2,0]).astype(int),0,tex.shape[1]-1)
                ty=np.clip((wa*tu[0,1]+wb*tu[1,1]+wc*tu[2,1]).astype(int),0,tex.shape[0]-1)
                colors=np.uint8(np.clip(tex[ty,tx]*shade,0,255));bg[y0:y1+1,x0:x1+1][mask]=colors[mask];region[mask]=zz[mask]
    return Image.fromarray(bg,'RGB')

def font(size):return ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',size)

def weapon_preview():
    canvas=Image.new('RGB',(2400,1500),(24,29,27));d=ImageDraw.Draw(canvas)
    d.text((65,35),'青铜兵器 · 铸纹精修',font=font(49),fill=(208,192,149))
    d.text((68,110),'刃面倒角  /  双面雷纹  /  兽面护手  /  分段皮缠',font=font(25),fill=(137,154,139))
    for i,kind in enumerate(NAMES):
        scene=weapon_scene(kind);im=raster(scene,465,1150,.30,.06,.09)
        canvas.paste(im,(30+i*473,205));count=len(scene)
        d.text((260+i*473,1390),NAMES[kind],font=font(32),anchor='mm',fill=(208,192,149))
        d.text((260+i*473,1442),f'{count:,} 个模型部件',font=font(20),anchor='mm',fill=(133,151,136))
    PREVIEW.mkdir(parents=True,exist_ok=True)
    for name in ('bronze_detail_weapons.png','bronze_weapons_finished.png'):canvas.save(PREVIEW/name)
    return canvas

def puppet_preview():
    data=json.loads((ASSET/'puppet/bronze_puppet_parts.json').read_text(encoding='utf-8'))
    count=sum(len(p['boxes']) for p in data['parts'])
    canvas=Image.new('RGB',(1900,1720),(24,29,27));d=ImageDraw.Draw(canvas)
    d.text((65,35),'青铜人偶 · 古铸战卫',font=font(49),fill=(208,192,149))
    d.text((68,112),'怒目兽面  /  铸纹甲片  /  分节关节  /  成套双刃',font=font(26),fill=(137,154,139))
    full=raster(puppet_scene(),1190,1420,.26,.10,.06);canvas.paste(full,(10,210))
    head=raster(puppet_scene(True,False),650,650,.40,.05,.11);canvas.paste(head,(1220,255))
    d.text((1270,960),'深眼窝 · 斜压怒眉',font=font(30),fill=(208,192,149))
    d.text((1270,1020),'立体鼻梁、闭口与收紧下颌',font=font(23),fill=(137,154,139))
    d.text((1270,1120),'关节与手指独立成形',font=font(30),fill=(208,192,149))
    d.text((1270,1180),'叠甲、铸钉与阴刻纹可见',font=font(23),fill=(137,154,139))
    d.text((1270,1350),f'{count:,} 个实体模型部件',font=font(27),fill=(208,192,149))
    d.text((1270,1400),f"{data['texture_width']} × {data['texture_height']} 独立 UV 材质",font=font(23),fill=(137,154,139))
    d.text((68,1650),'成品资源离线渲染 · 未启动游戏',font=font(23),fill=(137,154,139))
    for name in ('bronze_detail_puppet.png','bronze_puppet_finished.png'):canvas.save(PREVIEW/name)
    canvas.save(PREVIEW/'bronze_twin_equipped.png')
    close=Image.new('RGB',(1800,1240),(24,29,27));dr=ImageDraw.Draw(close)
    dr.text((65,30),'青铜人偶 · 头部铸造细节',font=font(47),fill=(208,192,149))
    for i,az in enumerate((0,.50)):close.paste(raster(puppet_scene(True,False),850,1010,az,.04,.1),(30+i*890,170))
    close.save(PREVIEW/'bronze_detail_head.png')
    return canvas

def twin_preview():
    canvas=Image.new('RGB',(2000,1550),(24,29,27));d=ImageDraw.Draw(canvas)
    d.text((65,35),'青铜双刃 · 四器合铸',font=font(49),fill=(208,192,149))
    d.text((68,112),'宽刃长剑 × 弯刃尾锋  /  成套双持  /  副手必须为空',font=font(27),fill=(137,154,139))
    for i,(kind,label) in enumerate((('twin_main','主手 · 宽刃长剑'),('twin_off','配对刃 · 弯刃尾锋'))):
        scene=weapon_scene(kind);canvas.paste(raster(scene,900,1160,.32,.07,.08),(60+i*970,210))
        d.text((505+i*970,1420),label,font=font(34),anchor='mm',fill=(208,192,149))
    d.text((68,1500),'一个物品 · 统一强化与耐久 · 成品模型资源离线渲染',font=font(24),fill=(137,154,139))
    PREVIEW.mkdir(parents=True,exist_ok=True);canvas.save(PREVIEW/'bronze_twin_finished.png')

def main():
    weapon_atlas()
    for kind in NAMES:
        model=weapon(kind);write(ASSET/f'models/item/bronze_{kind}.json',model)
        print(kind,len(model['elements']))
    data=puppet_body();print('puppet',sum(len(p['boxes']) for p in data['parts']),'texture',data['texture_width'])
    weapon_preview();puppet_preview()

if __name__=='__main__':main()
