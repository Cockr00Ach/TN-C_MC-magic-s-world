"""Rebuild the warrior spear and bow roster into native 1.20.1 item geometry.
A cut metal profile is sculpted once in its actual model plane. Narrow inlays,
engraving and fibres live on paint, not on stacked decorative cubes.
"""
from pathlib import Path
import os
import json, math
import numpy as np
from PIL import Image,ImageDraw
from scipy.ndimage import distance_transform_edt
from scipy.interpolate import PchipInterpolator

ROOT=Path(__file__).resolve().parents[1]
REPO=Path(os.environ.get('TNC_WEAPON_REPO', Path(__file__).resolve().parents[3]))
SOURCE=REPO/'src/main/resources/assets/tnc'
PACK=ROOT/'资源包/assets/tnc'
MODELS=PACK/'models/item'
TEXTURES=PACK/'textures/item/arsenal_26'
SIZE=256
PPU=6
Y0=156
Y,X=np.mgrid[0:SIZE,0:SIZE]
U=(X-128+.5)/PPU
V=(Y0-Y-.5)/PPU

def rgb(hexstr):
    return np.array(list(bytes.fromhex(hexstr)),float)

# Timber, grip, armour steel/horn, sharpened edge, chape, figure, stone/silk.
PALETTE={
'spear':('253e45','253535','40736b','bdebd4','7e865b','ecda93','53e1ae'),
'sky_halberd':('34445b','303b50','3a829e','a8eaf0','716b75','e4be76','39e2e7'),
'fangtian_ji':('53292a','37222a','585a68','cbd4d6','8e5630','f7c378','bd433b'),
'red_tassel_spear':('785237','50352b','728391','e2e7de','966639','e1b678','b72836'),
'qimei_staff':('a87542','60412d','776043','cec19a','70613e','c8a674','687c61'),
'militia_spear':('80694f','5b4237','646969','bec5bb','786956','ab9975','9c8471'),
'silver_hook_spear':('344c52','293d45','748894','d6f0ee','657b82','b7d5d2','75b4b1'),
'coiled_dragon_spear':('652d32','4e2a35','ad7737','ffe6a5','9c5329','efc273','4bafa4'),
'bronze_ge_halberd':('684432','48362a','87734b','d7bd7e','726243','c5a161','5c9b89'),
'single_crescent_halberd':('5e4435','4c3535','637877','d8e7d7','807047','d2a975','74ac93'),
'black_iron_halberd':('392f42','36273c','515568','c3bed2','6a506c','cbadba','b17cc5'),
'bow':('94643c','563529','705139','cda67a','6d5540','c9a66b','828753'),
'moon_bow':('3f394e','423049','655776','d4c3ec','8a749f','d8bdd3','a78bc8'),
'storm_bow':('3e5a59','243b40','4c807b','b8e9df','526e69','d2c291','55c6bb'),
'ember_bow':('6b3f39','47302d','793e3c','f8b179','774839','e7ab6d','f06938'),
'laminated_horn_bow':('966748','572f2b','49403f','d4bc8c','6c5139','d5a767','a24840'),
}
ROLES=['wood','wrap','metal','edge','bronze','gold','accent']

class Surface:
    def __init__(self,name,x0,zdepth,bow=False):
        self.name=name;self.x0=x0;self.zdepth=zdepth;self.bow=bow
        self.col=dict(zip(ROLES,map(rgb,PALETTE[name])))
        self.img=np.zeros((SIZE,SIZE,3),np.uint8)
        self.thick=np.zeros((SIZE,SIZE),np.int16)
        self.labels=np.zeros((SIZE,SIZE),np.uint8)
        self.names=['empty']
    def px(self,x,y):return (round(128+x*PPU),round(Y0-y*PPU))
    def mask(self,points):
        bitmap=Image.new('1',(SIZE,SIZE))
        ImageDraw.Draw(bitmap).polygon([self.px(x,y) for x,y in points],fill=1)
        return np.array(bitmap)
    def poly(self,pts,role,half,title,faceted=False):
        # Restrict each shape to a bounding box; disconnected relief remains an editable part.
        mask=self.mask(pts)
        if not mask.any():return
        ys,xs=np.where(mask)
        ly,hy=max(0,ys.min()-1),min(SIZE,ys.max()+2)
        lx,hx=max(0,xs.min()-1),min(SIZE,xs.max()+2)
        crop=np.s_[ly:hy,lx:hx];m=mask[crop];u,v=U[crop],V[crop]
        dist=distance_transform_edt(m)
        inrow=np.maximum(1,m.sum(axis=1,keepdims=True))
        across=(np.cumsum(m,axis=1)-.5)/inrow
        base=self.col[role]
        grain=.5*np.sin(7.2*u+.2*v+1.6*np.sin(v*.53))+ .30*np.sin(12.8*u-.13*v)
        if role=='wood':
            shade=.78+.27*np.sin(np.pi*across)+grain*.15+.037*np.sin(3*v+.9*u)
        elif role=='wrap':
            braid=np.mod(v*1.32+u*.72,1.)
            shade=.71+.19*np.sin(np.pi*across)+.24*np.cos(2*np.pi*braid)
            shade+=.15*((braid>.72)&(braid<.83))
        elif role=='accent' and self.name=='red_tassel_spear':
            shade=.68+.34*np.sin(np.pi*across)+.2*grain
        else:
            shade=.76+.11*np.sin(v*.71-u*1.9)+.12*np.sin(v*.23+.6)
            shade+=.32*(across<.46)+.27*np.exp(-((across-.47)/.085)**2)
        color=np.clip(base[None,None,:]*shade[:,:,None],0,255)
        if faceted:
            border=(dist<=1.05)&m;edgeCol=self.col['edge'] if role in ('metal','edge') else self.col['gold']
            color[border]=.76*color[border]+.24*edgeCol
        self.img[crop][m]=color[m].astype('uint8')
        depth=np.full(m.shape,half)
        if faceted:
            # Three bevel planes carry the cut profile into side views.
            depth=half*np.where(dist<=1.05,.45,np.where(dist<=2.05,.74,1))
        self.thick[crop][m]=np.maximum(1,np.rint(depth[m]*24))
        if title not in self.names:self.names.append(title)
        self.labels[crop][m]=self.names.index(title)
    def band(self,y,h,w,role,th,title,flare=0):
        self.poly([(-w,y),(-w-flare,y+h*.3),(-w-flare,y+h*.7),(-w,y+h),(w,y+h),(w+flare,y+h*.7),(w+flare,y+h*.3),(w,y)],role,th,title,True)
    def ink(self,pts,color,width=1):
        temp=Image.new('1',(SIZE,SIZE))
        ImageDraw.Draw(temp).line([self.px(x,y) for x,y in pts],fill=1,width=width)
        mask=np.array(temp)&(self.thick>0)
        self.img[mask]=np.uint8(np.clip(color,0,255))
    def cord(self,pts,width,mat,half,title):
        v=np.asarray(pts,float);t=np.gradient(v,axis=0)
        normal=np.column_stack((-t[:,1],t[:,0]))/np.linalg.norm(t,axis=1,keepdims=True)
        width=np.broadcast_to(width,len(v))[:,None]
        self.poly(np.vstack((v+normal*width,(v-normal*width)[::-1])).tolist(),mat,half,title,True)
    def tile(self):
        mask=self.thick>0
        iy,ix=distance_transform_edt(~mask,return_distances=False,return_indices=True)
        return Image.fromarray(self.img[iy,ix])
    def elements(self,slot=(0,0),tiles=1):
        # Rectangles share only coplanar pixels and thickness; remove enclosed side faces before export.
        th=self.thick;used=np.zeros_like(th,dtype=bool);out=[]
        for row,col in zip(*np.nonzero(th)):
            if used[row,col]:continue
            value=th[row,col];label=self.labels[row,col]
            end=col+1
            while end<SIZE and th[row,end]==value and self.labels[row,end]==label and not used[row,end]:end+=1
            top=row+1
            while top<SIZE and np.all(th[top,col:end]==value) and np.all(self.labels[top,col:end]==label) and not used[top,col:end].any():top+=1
            used[row:top,col:end]=True
            x1=(col-128)/PPU+self.x0;x2=(end-128)/PPU+self.x0;a=(Y0-top)/PPU;b=(Y0-row)/PPU
            half=value/24;low=self.zdepth-half;high=self.zdepth+half
            xy=[x1,a,low,x2,b,high] if self.bow else [x1,low,a,x2,high,b]
            sx,sy=slot
            def uv(x,y):return [round((x+sx*SIZE)*16/SIZE/tiles,5),round((y+sy*SIZE)*16/SIZE/tiles,5)]
            def face(a,b,c,d):return {'uv':uv(a,b)+uv(c,d),'texture':'#0'}
            # Outward x, y and z faces use the atlas projection of their actual border.
            lower=(top==SIZE or np.any(th[top,col:end]<value));upper=row==0 or np.any(th[row-1,col:end]<value)
            left=col==0 or np.any(th[row:top,col-1]<value);right=end==SIZE or np.any(th[row:top,end]<value)
            if self.bow:
                faces={'south':face(col,row,end,top),'north':face(end,row,col,top)}
                if lower:faces['down']=face(col,top-.6,end,top-.4)
                if upper:faces['up']=face(col,row+.4,end,row+.6)
                if left:faces['west']=face(col+.4,row,col+.6,top)
                if right:faces['east']=face(end-.6,row,end-.4,top)
            else:
                faces={'up':face(col,top,end,row),'down':face(col,row,end,top)}
                if lower:faces['north']=face(end,top-.65,col,top-.35)
                if upper:faces['south']=face(col,row+.35,end,row+.65)
                if left:faces['west']=dict(face(col+.35,row,col+.65,top),rotation=90)
                if right:faces['east']=dict(face(end-.65,row,end-.35,top),rotation=270)
            out.append(dict(name=self.names[label],**{'from':[round(n,5) for n in xy[:3]],'to':[round(n,5) for n in xy[3:]]},faces=faces))
        return out


# All profiles are hand-keyed in the source model's longitudinal plane.
# Neck/tip lengths and grip axes preserve the original hit and ornament anchors.
SPEARS={
 'spear':(8,8,-15.9,9.3,25.05,.50),
 'sky_halberd':(7.5,.50,-1.3,11.0,20.9,.42),
 'fangtian_ji':(7,.52,-10,11.9,20.5,.42),
 'red_tassel_spear':(7,.52,-10,12.0,18.35,.40),
 'qimei_staff':(7,.62,-9,15.9,17,.53),
 'militia_spear':(7,.50,-9.1,12.20,17.6,.43),
 'silver_hook_spear':(7,.50,-10.3,12.25,20.9,.43),
 'coiled_dragon_spear':(7,.50,-10.5,12.8,22.75,.47),
 'bronze_ge_halberd':(7,.50,-9.9,11.9,18.35,.46),
 'single_crescent_halberd':(7,.50,-10.1,12.10,20.7,.45),
 'black_iron_halberd':(7,.50,-10.5,12.10,22.2,.47),
}
def head(s,profile,mat='metal',half=.53,title='forged head'):
    s.poly([(a,h) for h,a,b in profile]+[(b,h) for h,a,b in reversed(profile)],mat,half,title,True)
    # Interrupted cutting edge and fuller follow the outside taper.
    levels=np.array(profile);lo,hi=levels[0,0],levels[-1,0]
    z=np.linspace(lo+.1,hi-.22,20)
    left=np.interp(z,levels[:,0],levels[:,1]);right=np.interp(z,levels[:,0],levels[:,2])
    s.ink(list(zip(left+.12*(right-left),z)),s.col['edge']*.94)
    s.ink(list(zip(left+.50*(right-left),z)),s.col['metal']*.57)
    s.ink(list(zip(left+.55*(right-left),z)),s.col['edge']*.82)
def gem(s,x,y,rx,ry):
    s.poly([(x,y+ry),(x+rx,y),(x,y-ry),(x-rx,y)],'gold',.52,'cloisonne bezel',True)
    s.poly([(x,y+ry*.69),(x+rx*.66,y),(x,y-ry*.69),(x-rx*.66,y)],'accent',.61,'inlaid stone',True)
    s.ink([(x-rx*.30,y+.04),(x,y+ry*.43)],s.col['edge'])
def spear(id):
    x,d,bottom,neck,tip,w=SPEARS[id];s=Surface(id,x,d)
    if id=='qimei_staff':
        s.poly([(-.36,bottom),(-.54,bottom+.55),(-.57,-4),(-.45,4),(-.54,tip-.4),(-.35,tip),(.36,tip),(.54,tip-.5),(.44,3),(.51,-3),(.52,bottom+.4),(.36,bottom)],'wood',.51,'turned white-wax wood',True)
        for mid in (bottom+.9,tip-1.8):
            s.band(mid,.87,.56,'bronze',.54,'fishmouth ferrule',.10)
            for j in (0,1):s.band(mid+.12+j*.53,.12,.62,'gold',.56,'rolled lip')
        s.band(-.3,4.35,.55,'wrap',.61,'bound palm grip')
        for j in range(4):
            zz=6.8+j*1.1;s.ink([(-.22,zz),(.22,zz+.28),(-.14,zz+.75)],s.col['wrap'])
        for zz in (bottom+.30,tip-.55):
            s.band(zz,.39,.51,'metal',.50,'sealed end')
        return s
    s.poly([(-w*.71,bottom),(-w,bottom+.55),(-w*.83,neck*.54),(-w*1.05,neck),(w*1.05,neck),(w*.86,neck*.54),(w,bottom+.55),(w*.71,bottom)],'wood',w*.95,'tapered haft',True)
    # Laced grip is positioned on the exact original palm. Each family has a different
    # fitting language, from workmanlike iron/wax cord to patterned gilded chevrons.
    gy=0 if id!='spear' else -2
    s.band(gy,3.3,w+.05,'wrap',w+.09,'bound palm')
    for zz in (gy-.2,gy+3.32):
        s.band(zz,.23,w+.08,'bronze',w+.12,'grip keeper')
    s.band(bottom+.05,.85,w,'bronze',w,'ground shoe',.10)
    if id not in ('militia_spear','red_tassel_spear'):
        s.band(bottom+.78,.24,w+.06,'gold',w+.03,'shoe collar')
    s.poly([(-w*.92,neck-2.26),(-w-.18,neck-.45),(-w-.23,neck+.38),(w+.23,neck+.38),(w+.18,neck-.45),(w*.92,neck-2.26)],'bronze',w+.11,'tapered blade socket',True)
    for zz in (neck-1.99,neck-.33):
        s.band(zz,.21,w+.19,'gold',w+.15,'swaged socket lip')
    if id=='spear':
        head(s,[(neck,-.44,.44),(11.5,-2.12,2.12),(14.0,-1.79,1.79),(17.1,-1.48,1.48),(20.3,-.70,.70),(23,-.35,.35),(tip,-.04,.04)],half=1.05)
        for k in (-1,1):
            s.cord([(k*.50,8.2),(k*1.45,7.9),(k*2.5,7.05),(k*3.61,6.85),(k*3.13,8.33),(k*2.40,8.75)],[.19,.35,.36,.19,.14,.06],'bronze',.4,'swept wind vane')
            s.ink([(k*.75,11.0),(k*1.65,11.65),(k*.85,15.6),(k*.36,18.1)],s.col['accent'])
        gem(s,0,10.65,.74,1.10)
    elif id=='sky_halberd':
        head(s,[(10.9,-1.0,.31),(12.4,-3.9,.42),(14.0,-5.15,.56),(15.8,-3.5,.52),(17,-.68,.45),(tip,-.14,.08)],half=.62)
        s.cord([(.2,12),(1.11,12.15),(2.0,12.72),(2.4,13.75),(1.85,13.27)],[.28,.36,.40,.09,.035],'bronze',.39,'reverse vane')
        for j in range(3):
            s.ink([(-3.2+.55*j,13.1),(-2.5+.47*j,13.4),(-1.3+.36*j,15.1),(-1.3+.3*j,15.6)],s.col['accent']*(.7+.1*j))
        gem(s,-.18,14.50,.57,.81)
    elif id in ('fangtian_ji','single_crescent_halberd','black_iron_halberd'):
        sides=(1,) if id=='single_crescent_halberd' else (-1,1)
        width={'fangtian_ji':3.72,'single_crescent_halberd':4.31,'black_iron_halberd':4.98}[id]
        height={'fangtian_ji':6.65,'single_crescent_halberd':7.42,'black_iron_halberd':8.22}[id]
        for k in sides:
            for h in (.88,height*.66):
                s.cord([(0,neck+h),(k*width*.37,neck+h-.09),(k*width*.69,neck+h+.23)],[.19,.20,.11],'bronze',.31,'chased crescent bridge')
            points=[(.45,0),(.64,.12),(.78,.34),(.75,.63),(.65,.83),(.46,1),(.72,.89),(.92,.67),(1,.43),(.96,.26),(.78,.10)]
            s.poly([(k*width*px,neck+height*py) for px,py in points],'metal',.43,'crescent wing',True)
            s.ink([(k*width*px,neck+height*py) for px,py in [(.72,.87),(.88,.65),(.94,.43),(.9,.28)]],s.col['edge'])
        head(s,[(neck,-.48,.48),(neck+1.85,-1.03,1.03),(neck+3.1,-.91,.91),(tip-.95,-.22,.22),(tip,-.04,.04)],half=.59)
        gem(s,0,neck+.73,.43,.61)
        if id=='black_iron_halberd':
            for k in (-1,1):
                s.cord([(k*.23,neck-1.30),(k*1.14,neck-.57),(k*1.48,neck+.75)],[.17,.21,.03],'bronze',.38,'split iron heel')
    elif id=='red_tassel_spear':
        for k in (-1,1):
            for j in (0,1):
                s.cord([(k*.28,neck+.12),(k*(.48+.25*j),neck-.74),(k*(.55+.38*j),neck-1.66),(k*(1.32+.43*j),neck-2.62-.55*j)],
                       [.27,.29,.19,.035],'accent',.26+j*.035,'cut silk tassel')
        head(s,[(neck,-.43,.43),(neck+.89,-.9,.9),(neck+2,-1.02,1.02),(tip,-.035,.035)],half=.51)
        s.band(neck-.26,.38,.60,'gold',.56,'tassel knot')
    elif id=='militia_spear':
        head(s,[(neck,-.33,.33),(neck+.85,-.73,.73),(neck+1.70,-.62,.62),(tip,-.045,.045)],half=.47)
        s.ink([(-.25,neck-.98),(.25,neck-.91)],s.col['edge']*.65)
    elif id=='silver_hook_spear':
        s.cord([(.2,neck+.34),(1.45,neck+.02),(2.37,neck+.79),(2.85,neck+1.92),(2.69,neck+3.11),(2.09,neck+4.22),(1.78,neck+4.47)],
               [.28,.48,.60,.58,.41,.12,.025],'metal',.45,'returning silver hook')
        head(s,[(neck,-.42,.42),(neck+1.55,-.98,.98),(neck+3.1,-.84,.84),(tip,-.045,.045)],half=.53)
        gem(s,.12,neck+.84,.38,.52)
    elif id=='coiled_dragon_spear':
        head(s,[(neck,-.56,.56),(neck+1.65,-1.26,1.26),(neck+3.1,-1.03,1.03),(neck+5.8,-.58,.58),(tip,-.045,.045)],half=.66)
        s.cord([(-.18,neck-4.2),(.55,neck-3.4),(.60,neck-2.85),(-.44,neck-2.04),(-.59,neck-1.42),(.56,neck-.75),(.68,neck+.09)],[.07,.15,.26,.25,.28,.28,.46],'gold',.65,'coiled gilded dragon')
        for k in (-1,1):
            s.cord([(k*.28,neck+.16),(k*.87,neck+.16),(k*1.09,neck+1.02),(k*1.0,neck+1.4)],[.23,.34,.11,.04],'bronze',.42,'dragon antler')
        gem(s,0,neck+.5,.54,.56)
        s.ink([(.30,neck+1.75),(.48,neck+2.60),(.24,neck+3.10),(.41,neck+3.7)],s.col['gold'])
    elif id=='bronze_ge_halberd':
        head(s,[(neck,-.41,.41),(neck+1.33,-.85,.85),(neck+2.35,-.67,.67),(tip,-.045,.045)],half=.51)
        s.poly([(0,neck-.4),(-1.01,neck-.30),(-2.08,neck-.06),(-5.50,neck+.45),(-4.38,neck+1.12),(-3.28,neck+1.48),(-1.20,neck+.99),(.20,neck+.69)],'metal',.42,'cast transverse ge',True)
        s.ink([(-4.28,neck+.58),(-3.05,neck+.89),(-2.1,neck+.72),(-2.07,neck+.35),(-2.8,neck+.43)],s.col['accent'])
        for j in range(3):s.ink([(-1.52+.19*j,neck+.05),(-1.49+.19*j,neck+.57)],s.col['gold'])
    return s


# Each stave has its own deflex/reflex profile, laminate, tips and physical ornament.
BOWS={
 'bow':(12.55,[-.18,-1.38,-2.38,-2.58,-1.76,.38,2.20],[.50,.54,.49,.43,.35,.27,.17]),
 'moon_bow':(12.55,[-.18,-1.42,-2.67,-3.13,-2.40,.24,2.28],[.50,.68,.54,.44,.29,.24,.12]),
 'storm_bow':(12.55,[-.18,-1.6,-2.56,-2.77,-1.82,.57,2.42],[.50,.60,.46,.34,.29,.26,.15]),
 'ember_bow':(12.55,[-.18,-1.47,-2.39,-2.43,-1.49,.56,2.32],[.53,.73,.68,.54,.42,.32,.17]),
 'laminated_horn_bow':(11.37,[-.05,-1.44,-2.56,-2.72,-2.06,-.22,1.02],[.54,.58,.52,.43,.35,.29,.19]),
}
def bow(id,pull):
    s=Surface(id,8,8,True)
    h,xs,widths=BOWS[id];laminated=id=='laminated_horn_bow'
    nock=1.3+4.10*pull if laminated else 3.0+3.5*pull
    span=h-pull*(.70 if laminated else .48)
    tkeys=[0,.13,.33,.54,.72,.88,1]
    cx=PchipInterpolator(tkeys,xs);wid=PchipInterpolator(tkeys,widths)
    def x(t):return cx(t)+pull*t**2*(1.08 if laminated else .66)
    def headlength(t):return 1.58+(span-1.58)*t
    for sign in (-1,1):
        # String, stave and nocked arrow share their physical end points.
        tipx=x(1); tipy=8+sign*span
        s.cord([(tipx,tipy),(nock,8)],[.064,.064],'edge',.072,'served bowstring')
        ts=np.linspace(0,1,58);xp=x(ts);yp=8+sign*headlength(ts);ww=wid(ts)
        def strip(left,right,mat,half,title):
            s.poly(np.vstack((np.column_stack([xp+left*ww,yp]),np.column_stack([xp+right*ww,yp])[::-1])).tolist(),mat,half,title,True)
        outer={'bow':'wood','moon_bow':'metal','storm_bow':'metal','ember_bow':'metal','laminated_horn_bow':'metal'}[id]
        strip(-1,1,outer,.38 if not laminated else .42,'recurved outer limb')
        strip(.51,.95,'wood' if laminated else 'bronze',.36,'tension laminate')
        # Inlaid stripes travel along the limbs. No floating decals or duplicated bow.
        s.ink(list(zip(xp+.41*ww,yp)),s.col['edge']*.85)
        for at in (.16,.68,.93):
            if id=='bow' and at==.68:continue
            gx,gy=x(at),8+sign*headlength(at);w=wid(at)
            s.poly([(gx-w-.10,gy-sign*.25),(gx+w+.1,gy-sign*.22),(gx+w+.07,gy+sign*.23),(gx-w-.07,gy+sign*.24)],'bronze',.48,'limb stitch')
            s.ink([(gx-w*.81,gy),(gx+w*.81,gy+sign*.03)],s.col['gold'])
        if id=='moon_bow':
            # Deliberate gap within the silver lunette. Its two heels meet the stave.
            ts=np.linspace(.12,.48,17)
            points=[(float(x(t))-.20-1.05*math.sin((t-.12)/.36*math.pi),8+sign*headlength(t)) for t in ts]
            s.cord(points,.17+.09*np.sin(np.linspace(0,math.pi,len(points))),'gold',.26,'pierced moon limb')
            for at in (.25,.33,.40):
                gx,gy=x(at),8+sign*headlength(at)
                s.ink([(gx-.24,gy-sign*.28),(gx+.03,gy),(gx-.24,gy+sign*.26)],s.col['accent']*1.32)
        if id in ('storm_bow','ember_bow'):
            # Each feather has an overlapping root and an independent swept tip.
            for index,at in enumerate((.13,.24,.36)):
                gx,gy=x(at),8+sign*headlength(at)
                reach=(1.58-.16*index)*(1 if id=='storm_bow' else 1.23)
                s.poly([(gx+.07,gy-sign*.17),(gx-.20,gy+sign*.88),(gx-reach*.69,gy+sign*1.22),
                        (gx-reach,gy+sign*(1.63 if id=='storm_bow' else 1.15)),(gx-reach*.86,gy+sign*.42),(gx-.51,gy-sign*.02)],
                       'metal' if id=='storm_bow' else 'bronze',.32,'swept air vane' if id=='storm_bow' else 'ember feather')
                s.ink([(gx-.22,gy+sign*.27),(gx-reach*.67,gy+sign*.65),(gx-reach*.87,gy+sign*1.1)],s.col['accent']*1.05)
        if id=='laminated_horn_bow':
            # Lacquered horn tips overlap near the string nock.
            s.cord([(float(x(.86)),8+sign*headlength(.86)),(float(x(.94)),8+sign*headlength(.94)),(float(x(1)),tipy)],
                   [.28,.29,.15],'bronze',.32,'birch siyah')
            for at in (.40,.44,.48):
                gx,gy=x(at),8+sign*headlength(at);s.ink([(gx-.44,gy-sign*.13),(gx+.38,gy+sign*.2)],s.col['gold']*.72)
        s.cord([(tipx-.04,tipy-sign*.35),(tipx+.12,tipy-sign*.07),(tipx+.14,tipy+sign*.15)],[.18,.17,.09],'bronze',.30,'hooked nock')
    s.poly([(-.57,5.55),(-.74,5.90),(-.49,6.55),(-.45,8.25),(-.51,9.57),(-.75,10.06),(-.60,10.54),(.49,10.46),(.61,9.85),(.43,9.21),(.41,6.49),(.59,5.96),(.50,5.55)],'wrap',.66,'palm swell')
    for gy in (5.58,10.07):s.band(gy,.34,.58,'bronze',.68,'riser ferrule',.14)
    s.poly([(-.82,7.63),(0,7.47),(.73,7.63),(1.07,7.92),(.96,8.06),(-.80,7.94)],'gold',.77,'arrow shelf',True)
    if id in ('moon_bow','storm_bow','ember_bow'):gem(s,-.23,9.78,.41,.54)
    if pull:
        # Arrow shaft ends at nock, two-cut fletching and pyramidal bodkin taper.
        s.poly([(nock-11.8,7.92),(nock,7.92),(nock,8.08),(nock-11.8,8.08)],'wood',.095,'nocked arrow shaft')
        s.poly([(nock-12.97,8),(nock-11.82,8.39),(nock-11.63,8.13),(nock-11.65,7.88),(nock-11.82,7.61)],'edge',.20,'arrow point',True)
        for sign in (-1,1):
            s.poly([(nock-2.35,8),(nock-1.68,8+sign*.38),(nock-.49,8+sign*.32),(nock-.69,8)],'accent',.12,'fletching',True)
    return s

def phantom(surface,id,source):
    """Bake a cheap rigid silhouette with wide spans and the matching authored paint."""
    template=json.loads((source/'warrior_ghost'/(id+'.json')).read_bytes())
    step=3
    occupied=surface.thick>0
    # Half-model-unit cells preserve holes in both moons and the hooked tip.
    padded=np.pad(occupied,((0,2),(0,2)),constant_values=False)
    coverage=padded.reshape(86,step,86,step).mean(axis=(1,3))
    active=coverage>(1/9+.0001)
    simplified=Surface(id,surface.x0,surface.zdepth)
    th=(np.repeat(np.repeat(active,step,axis=0),step,axis=1))[:SIZE,:SIZE]
    simplified.thick[th]=7
    simplified.labels[th]=1;simplified.names.append('phantom silhouette')
    template['elements']=simplified.elements()
    template['textures']={'0':'tnc:item/arsenal_26/'+id,'particle':'tnc:item/arsenal_26/'+id}
    template.pop('texture_size',None)
    template['credit']='TN-C arsenal 26 / reduced silhouette'
    path=MODELS/'warrior_ghost';path.mkdir(exist_ok=True)
    (path/(id+'.json')).write_text(json.dumps(template,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    return len(template['elements'])

def main():
    MODELS.mkdir(parents=True,exist_ok=True);TEXTURES.mkdir(parents=True,exist_ok=True)
    source=SOURCE/'models/item'
    def read(id):return json.loads((source/(id+'.json')).read_bytes())
    def write(id,state,s,els):
        old=read(state)
        if id=='spear':
            old['display']=read('wind_relic_spear')['display']
            old.pop('parent')
            # The old fallback at z=8 selected the shoe at -16 as the farthest
            # point. The wrapped grip spans -2..1.3, with the blade at +25.
            old['tnc_grip']=[8,8,0]
        for k in ('credit','texture_size','textures','elements','groups'):old.pop(k,None)
        model={'credit':'TN-C arsenal 26 / hand-painted profile and relief','textures':{'0':'tnc:item/arsenal_26/'+id,'particle':'tnc:item/arsenal_26/'+id},**old,'elements':els}
        assert all(-16.00001<=c<=32.00001 for e in els for a in ('from','to') for c in e[a]),(id,'range')
        assert all(all(a<b for a,b in zip(e['from'],e['to'])) for e in els),(id,'volume')
        assert all(0<=v<=16 for e in els for f in e['faces'].values() for v in f['uv'])
        (MODELS/(state+'.json')).write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    summary=[]
    for id in SPEARS:
        surface=spear(id);el=surface.elements();write(id,id,surface,el);surface.tile().save(TEXTURES/(id+'.png'),optimize=True)
        ghosts=phantom(surface,id,source) if (source/'warrior_ghost'/(id+'.json')).exists() else 0
        summary.append((id,len(el),sum(len(e['faces']) for e in el),ghosts))
    for id in BOWS:
        tiles=Image.new('RGB',(SIZE*2,SIZE*2))
        for i,pull in enumerate((0,.25,.65,1)):
            slot=(i%2,i//2);s=bow(id,pull);els=s.elements(slot,2)
            nm=id if i==0 else id+'_pulling_'+str(i-1)
            write(id,nm,s,els);tiles.paste(s.tile(),tuple(v*SIZE for v in slot))
            summary.append((nm,len(els),sum(len(e['faces']) for e in els)))
        tiles.save(TEXTURES/(id+'.png'),optimize=True)
    print('\n'.join(str(row) for row in summary))
if __name__=='__main__':main()
