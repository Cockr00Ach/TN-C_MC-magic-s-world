"""Sculpt and surface TN-C's playable non-ritual blades and mauls. All authored dimensions are model units."""
from pathlib import Path
from copy import deepcopy
import json
import math
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(os.environ.get("TNC_WEAPON_SOURCE", Path(__file__).resolve().parents[3]))/"src/main/resources/assets/tnc"
OUT = ROOT/"资源包/assets/tnc"
SIDES = ("down","up","north","south","east","west")
# Blade engraving takes a full 256 square. Narrow metal edges, gems and hafts share the other half.
ISLANDS = [(0,0,256,256),(256,128,128,128),(256,0,128,128),(384,0,128,160),(384,160,128,96)]
ATLAS = (512,256)

# Blade/body, carved fittings, bindings, inlay. Inlaid motifs are painted in model space.
DESIGNS = {
 "frost_katana":("779ead","485676","20293e","b6ede7","snow",5.2),
 "star_rapier":("b6c9ce","bf9257","342737","f8d785","star",4.5),
 "dragon_sword":("683e54","ac7a46","362b3b","e05458","scale",5.5),
 "yue_bronze_jian":("84926a","a88d59","45392c","539b89","lozenge",4.3),
 "willow_leaf_dao":("809c95","807248","303e35","b9ccb1","willow",4.25),
 "ring_pommel_dao":("8c98a3","816447","343034","bc8659","reed",4.0),
 "han_iron_jian":("7a8999","78624f","3e3032","a99a7e","lines",4.3),
 "ashwood_jian":("b18b63","886745","68513b","e3c593","grain",4.2),
 "cloudridge_jian":("8da7a0","9f8058","2a4547","98cdb5","cloud",4.2),
 "greatsword":("768ba3","9f793e","503c2e","d1b575","lines",4.0),
 "crimson_warblade":("9d514f","ac7952","422c32","f1aa62","flare",6.0),
 "doom_scythe":("695881","a08ca8","372b42","d59bed","eclipse",12.5),
 "crescent_guandao":("7a9a8b","8b714b","4e3833","b4cbae","scale",12.0),
 "monk_spade":("978a71","a58652","44372c","d2af6f","lotus",12.0),
 "field_scythe":("858778","816345","715137","b19872","reed",11.7),
 "iron_crescent_scythe":("8a9ba8","6c6e69","3c3534","c4cfd1","crescent",12.1),
 "white_bone_scythe":("c8b8a0","77645d","494042","dccbc0","ivory",11.4),
 "eclipse_scythe":("66506f","a98056","47364e","f0a1d9","eclipse",12.1),
 "hammer":("797d83","957146","5e402c","c9a36d","lines",10),
 "earthshaker_hammer":("705f53","a27643","43352f","ebac5d","fault",10.7),
 "iron_jian_mace":("75818c","7a705d","443c36","b8ac8a","lines",4.2),
 "ironwood_mallet":("8f6b49","6c7479","60462f","b49568","grain",9.5),
 "ring_flange_hammer":("839398","8c7854","38474c","b7d2cc","crescent",11.6),
 "thunder_bronze_hammer":("958556","8c7350","4e4030","dfc06e","thunder",10.5),
}

def col(s):
    return np.array([int(s[i:i+2],16) for i in (0,2,4)],dtype=float)

def rgb(c):
    return tuple(int(v) for v in np.clip(c,0,255))

def packed(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,separators=(',',':')),encoding='utf8')

def shape(name,lo,hi,role,angle=0,pivot=None):
    e=dict(name=name,lo=tuple(lo),hi=tuple(hi),role=role)
    if angle:e["rot"]={"axis":"y","angle":angle,"origin":list(pivot)}
    return e

def inferred_parts(d):
    result=[]
    for e in d['elements']:
        rect=next(iter(e['faces'].values()))['uv']
        u,v=(rect[0]+rect[2])*.5,(rect[1]+rect[3])*.5
        role=min(4,(int(v)*16+int(u))//8)
        q=shape(e.get('name',['Blade','Edge','Fitting','Haft','Inlay'][role]),e['from'],e['to'],role)
        if 'rotation' in e:q['rot']=e['rotation']
        result.append(q)
    return result

def coalesce(parts):
    """Join coplanar material cells only when their union exactly equals a single orthotope."""
    for repeat in range(3):
        old=len(parts)
        for ax in (0,2,1):
            others=[i for i in range(3) if i!=ax]; groups={}
            for e in parts:
                key=(e['role'],json.dumps(e.get('rot'),sort_keys=True),*(e[k][i] for i in others for k in ('lo','hi')))
                groups.setdefault(key,[]).append(e)
            result=[]
            for group in groups.values():
                last=None
                for e in sorted(group,key=lambda j:j['lo'][ax]):
                    if last and abs(last['hi'][ax]-e['lo'][ax])<.00001:
                        hi=list(last['hi']);hi[ax]=e['hi'][ax];last['hi']=tuple(hi)
                    else:
                        last=e.copy();result.append(last)
            parts=result
        if len(parts)==old:break
    return parts


def face_regions(parts):
    lo=np.min([p['lo'] for p in parts],axis=0);hi=np.max([p['hi'] for p in parts],axis=0)
    return lo-.15,hi+.15


def engraved_atlas(key,parts):
    iron,fittings,wrap,accent,motif,ricasso=DESIGNS[key]
    iron,fittings,wrap,accent=map(col,(iron,fittings,wrap,accent))
    regions=face_regions(parts);lo,hi=regions
    side=256
    Y,X=np.mgrid[:side,:side]
    wx=lo[0]+(hi[0]-lo[0])*(X-3)/(side-7)
    wz=lo[2]+(hi[2]-lo[2])*(Y-3)/(side-7)
    across=(X-3)/(side-7)
    phase=sum(map(ord,key))*.013
    band=(np.sin(wx*1.86-wz*.51+phase)*.5+.5)**4
    fine=np.sin(wx*20+wz*.34)*1.2+np.cos(wx*9.75-wz*1.12)*1.0
    light=np.array([215,220,221],float)
    if motif in ('grain','lozenge','thunder'):light=np.array([236,219,178],float)
    metal=(iron*.72)[None,None,:]+(band*26+10*np.sin(wx*2.4+wz*.26)+7+fine)[...,None]
    tex=Image.new("RGB",ATLAS)
    masks=[p for p in parts if p['role'] in (0,1) and p['hi'][2]>=ricasso-.8 and 'rot' not in p]
    # Draw the long steel facets, gold wire and inlay with the same coordinate mapping on all sections.
    wm=np.full(side,np.nan);we=np.full(side,np.nan)
    row_z=wz[:,0]
    for j,z in enumerate(row_z):
        es=[p for p in masks if p['lo'][2]-.011<=z<=p['hi'][2]+.011]
        if es:wm[j]=min(v['lo'][0] for v in es);we[j]=max(v['hi'][0] for v in es)
    finite=np.where(np.isfinite(wm))[0]
    if len(finite):
        wm=np.interp(np.arange(side),finite,wm[finite]);we=np.interp(np.arange(side),finite,we[finite])
    else:wm=np.full(side,lo[0]);we=np.full(side,hi[0])
    rel=(wx-wm[:,None])/np.maximum(.1,we-wm)[:,None]
    change=np.clip(rel,0,1)
    metal+=((np.where(change<.49,12,-10))+(25*np.exp(-((change-.49)/.047)**2))+18*np.exp(-((change-.11)/.06)**2))[...,None]
    if motif=='grain':
        fibre=np.sin(wx*23+1.25*np.sin(wz*.63)+np.cos(wx*2.3+wz*.72)*.6)
        metal=iron[None,None,:]+(11*fibre+7*np.sin(wx*8+wz*.16)-8+5*band)[...,None]
    if motif in ('eclipse','flare','snow','scale','cloud'):
        light=light*.56+accent*.44
    edge=np.broadcast_to(light,(side,side,3)).copy()*(.78+.19*band+.06*np.sin(wz*1.24))[...,None]
    hardware=fittings[None,None,:]+(8+18*band+6*np.sin(wx*3.3+wz*1.02)+fine)[...,None]
    if motif in ('lozenge','thunder','scale','cloud'):
        pat=np.maximum(0,np.sin(wx*1.7+wz*.6)+np.cos(wz*1.42-wx*.2)-.8)*.20
        hardware=hardware*(1-pat[...,None])+np.array([40,96,85])*pat[...,None]
    leather=wrap[None,None,:]+(6+6*np.sin(wx*9.4+wz*.46)+4*np.sin(wx*5.2-wz*.45))[...,None]
    wave=(wz*1.57+wx*.51)%1
    leather+=np.where(wave<.115,-14,np.where(wave<.19,17,0))[...,None]
    stone=accent[None,None,:]*(.80+.29*band+.12*across)[...,None]
    stone+=np.where(((wx-wz*.22)%1.17)<.035,37,0)[...,None]
    base_tiles=[metal,edge,hardware,leather,stone]
    tile0=Image.fromarray(np.uint8(np.clip(metal,0,255)))
    d=ImageDraw.Draw(tile0)
    def point(x,z):
        return (round(3+(x-lo[0])/(hi[0]-lo[0])*(side-7)),round(3+(z-lo[2])/(hi[2]-lo[2])*(side-7)))
    def line(coords,color,width=1):
        d.line([point(x,z) for x,z in coords],fill=rgb(color),width=width,joint='curve')
    def stroke(coords):
        line([(x+.038,z+.046) for x,z in coords],iron*.48,2)
        line(coords,accent*.76+metal.mean(axis=(0,1))*.24,1)
    def contour(s,t):
        j=np.clip(round(3+(t-lo[2])/(hi[2]-lo[2])*(side-7)),0,side-1)
        return (wm[j]+s*(we[j]-wm[j]),t)
    scythe='scythe' in key
    longhead=hi[2]-ricasso
    # Each artisan mark occupies the blade's root; the far tip retains room to read.
    if not scythe and key not in ('hammer','earthshaker_hammer','ironwood_mallet','ring_flange_hammer','thunder_bronze_hammer'):
        if motif not in ('grain','lines'):
            steps=6 if motif in ('lozenge','star','cloud') else 4
            for j in range(steps):
                t=ricasso+longhead*(.085+j*.068)
                dz=min(longhead*.052,.75)
                if motif in ('lozenge','scale'):
                    for a in (.23,.65):
                        stroke([contour(a,t),contour(a+.11,t+dz*.5),contour(a,t+dz),contour(a-.11,t+dz*.5),contour(a,t)])
                elif motif in ('snow','willow','reed'):
                    for a,sign in ((.3,-1),(.68,1)):
                        stroke([contour(a,t),contour(a+.13*sign,t+dz*.30),contour(a+.17*sign,t+dz*.76)])
                elif motif=='star':
                    a=contour(.50,t);stroke([(a[0]-.09,t),(a[0],t+dz*.40),(a[0]+.09,t),(a[0],t-dz*.4),(a[0]-.09,t)])
                elif motif in ('cloud','lotus','flare'):
                    stroke([contour(.30,t),contour(.17,t+dz*.45),contour(.35,t+dz),contour(.63,t+dz*.75),contour(.74,t+dz*.27)])
        else:
            stroke([contour(.38,ricasso+longhead*u) for u in np.linspace(.10,.48,26)])
    if motif in ('fault','thunder','scale','eclipse','crescent','ivory') and (scythe or 'hammer' in key):
        mid=(lo[0]+hi[0])*.5;left=lo[0]+.35;right=hi[0]-.45
        top=hi[2]-.4;bottom=ricasso
        for h in (-1,1):
            anchor=mid+h*(right-left)*.18
            if scythe:anchor=left+(right-left)*(.32 if h<0 else .53)
            depth=top-bottom
            if motif in ('thunder','fault'):
                geom=[(-.65,.22),(.32,.22),(.32,.67),(-.32,.67),(-.32,.42),(.05,.42)]
            elif motif=='ivory':
                geom=[(-.35,.24),(-.25,.46),(.20,.66),(.42,.72)]
            else:
                geom=[(-.52,.26),(-.62,.44),(-.33,.64),(.16,.69),(.42,.54),(.24,.39)]
            stroke([(anchor+h*x,bottom+z*depth) for x,z in geom])
    for i,arr in enumerate(base_tiles):
        tile=tile0 if i==0 else Image.fromarray(np.uint8(np.clip(arr,0,255)))
        x,y,w,h=ISLANDS[i]
        tex.paste(tile.resize((w,h),Image.Resampling.LANCZOS),(x,y))
    return tex,regions


def put_uv(side,p,regions):
    lo,hi=regions; a,b=p['lo'],p['hi'];role=p['role']
    ox,oy,w,h=ISLANDS[role]
    def point(x,z):
        u=(ox+(np.clip((x-lo[0])/(hi[0]-lo[0]),0,1)*249+3.5)*w/256)*16/ATLAS[0]
        v=(oy+(np.clip((z-lo[2])/(hi[2]-lo[2]),0,1)*249+3.5)*h/256)*16/ATLAS[1]
        return round(float(u),5),round(float(v),5)
    if side in ('up','down'):
        uv=(*point(a[0],a[2]),*point(b[0],b[2])) if side=='up' else (*point(a[0],b[2]),*point(b[0],a[2]))
    elif side in ('west','east'):
        x=a[0] if side=='west' else b[0]
        uv=(*point(x-.04,a[2]),*point(x+.04,b[2]))
    else:
        z=a[2] if side=='north' else b[2]
        uv=(*point(a[0],z-.036),*point(b[0],z+.036))
    return list(uv)


def fully_tiled(rect,tiles):
    """Prove coverage in exact exported coordinate units. Both bounds are inclusive."""
    a,b,c,d=rect
    overlaps=[]
    for x0,y0,x1,y1 in tiles:
        if x0<=a and y0<=b and x1>=c and y1>=d:return True
        left,right=max(a,x0),min(c,x1)
        bottom,top=max(b,y0),min(d,y1)
        if right>left and top>bottom:overlaps.append((left,bottom,right,top))
    if not overlaps:return False
    breaks=sorted({a,c,*[x for v in overlaps for x in (v[0],v[2])]})
    for low,high in zip(breaks,breaks[1:]):
        level=b
        for y0,y1 in sorted((r[1],r[3]) for r in overlaps if r[0]<=low and r[2]>=high):
            if y0>level:return False
            level=max(level,y1)
            if level>=d:break
        if level<d:return False
    return True


def covered_faces(parts):
    # All five material regions are opaque RGB. The same pivot and rotation must apply
    # to both faces. An uncovered subrectangle, even one export-unit wide, retains the face.
    planes={};hidden=set()
    axes=((0,'west','east'),(1,'down','up'),(2,'north','south'))
    for i,p in enumerate(parts):
        rot=json.dumps(p.get('rot'),sort_keys=True)
        lo=[round(float(x)*100_000) for x in p['lo']]
        hi=[round(float(x)*100_000) for x in p['hi']]
        for axis,neg,pos in axes:
            u,v=(j for j in range(3) if j!=axis)
            rectangle=(lo[u],lo[v],hi[u],hi[v])
            for sign,coord,name in ((0,lo[axis],neg),(1,hi[axis],pos)):
                sides=planes.setdefault((rot,axis,coord),([],[]))
                sides[sign].append((i,name,rectangle))
    for sides in planes.values():
        for side,covers in ((sides[0],sides[1]),(sides[1],sides[0])):
            candidates=[r for _,_,r in covers]
            if not candidates:continue
            for i,name,r in side:
                if fully_tiled(r,candidates):hidden.add((i,name))
    return hidden


def make_elements(parts,regions):
    hidden=covered_faces(parts)
    out=[]
    for i,p in enumerate(parts):
        faces={s:dict(texture='#0',uv=put_uv(s,p,regions)) for s in SIDES if (i,s) not in hidden}
        if not faces:continue
        e=dict(name=p['name'],**{'from':[round(x,5) for x in p['lo']],'to':[round(x,5) for x in p['hi']]},faces=faces)
        if 'rot' in p:e['rotation']=p['rot']
        out.append(e)
    return out


def embossed_sprite(key):
    """Preserve both original XY facades and put the real forged relief into the Z cross-section."""
    src=SOURCE/'textures/item'/(key+'.png')
    pixels=np.asarray(Image.open(src).convert('RGBA'))
    assert pixels.shape==(32,32,4) and set(np.unique(pixels[:,:,3]))=={0,255}
    materials={}
    for row,colno in zip(*np.where(pixels[:,:,3]>0)):
        x,y=(colno+.5)/2,(31.5-row)/2
        normal=x-y;length=x+y;red,green,blue,_=map(int,pixels[row,colno])
        amber=red>blue*1.30;steel=blue>=red and red>48
        if key=='hammer':
            if length<17.2:
                label,role,half=("Bound hickory",3,.57 if abs(normal)<.86 else .42)
                if length<10.7:half+=.14
            else:
                label,role,half=("Forged hammer cheek",0,1.09 if abs(normal)<3.95 else .81)
                if amber:label,role,half="Wedge and straps",2,1.24
                elif abs(normal)>3.4 and steel:label,role,half="Striking bevel",1,.78
        elif length<8.8:
            label,role,half="Peened bronze pommel",2,.66 if abs(normal)<.75 else .53
        elif length<12.1:
            label,role,half="Bound leather grip",3,.50 if abs(normal)<.65 else .38
        elif amber or (12.1<=length<13.7 and abs(normal)>1.05):
            label,role,half="Bronze quillons",2,.70 if abs(normal)<1.70 else .53
        else:
            label,role,half="Tempered steel",0,.46 if abs(normal+.32)<.58 else .31
            if red>178 or (not amber and red<42):label,role,half="Ground edges",1,.19
        materials[colno,row]=(label,role,half)
    # Greedy disjoint rectangles keep each authored cross-section. Texture handles all color boundaries.
    parts=[]
    for row in range(32):
        for colno in range(32):
            mat=materials.get((colno,row))
            if not mat:continue
            right=colno+1
            while materials.get((right,row))==mat:right+=1
            bottom=row+1
            while all(materials.get((u,bottom))==mat for u in range(colno,right)):bottom+=1
            for u in range(colno,right):
                for v in range(row,bottom):del materials[u,v]
            label,role,half=mat
            parts.append(shape(label,(colno/2,16-bottom/2,8-half),(right/2,16-row/2,8+half),role))
    return parts


def skin_embossed(elements):
    for e in elements:
        x0,y0,_=e['from'];x1,y1,_=e['to']
        for side,uv in [('south',[x0,16-y1,x1,16-y0]),('north',[x1,16-y1,x0,16-y0])]:
            if side in e['faces']:e['faces'][side]=dict(texture='#facade',uv=uv)


def generate(key):
    original=json.loads((SOURCE/'models/item'/(key+'.json')).read_bytes())
    base=deepcopy(original);sprite='elements' not in original
    if sprite:parts=embossed_sprite(key)
    else:parts=coalesce(inferred_parts(original))+ornaments(key,original['tnc_grip'])
    picture,regions=engraved_atlas(key,parts)
    tex=OUT/'textures/item/arsenal_26'/(key+'.png');tex.parent.mkdir(parents=True,exist_ok=True);picture.save(tex,optimize=True)
    path=OUT/'models/item'
    resource='tnc:item/arsenal_26/'+key
    base['textures']={'0':resource,'particle':resource}
    base['elements']=make_elements(parts,regions)
    base['texture_size']=list(ATLAS);base['credit']='TN-C · revised forged armory'
    if sprite:
        base.pop('parent',None);base['gui_light']='front'
        base['textures']['facade']=original['textures']['layer0']
        base['textures']['particle']=original['textures']['layer0']
        skin_embossed(base['elements'])
        # Preserve vanilla generated defaults for the two slots not overridden by the old handheld.
        base['display'].setdefault('fixed',{'rotation':[0,180,0],'scale':[1,1,1]})
        base['display'].setdefault('head',{'rotation':[0,180,0],'translation':[0,13,7]})
        packed(path/(key+'.json'),base)
    else:
        packed(path/(key+'.json'),base)
        ghost=SOURCE/'models/item/warrior_ghost'/(key+'.json')
        if ghost.is_file():
            ghostmodel=json.loads(ghost.read_bytes())
            ghostparts=coalesce(inferred_parts(ghostmodel))
            # The phantom keeps just the original broad outline; its material coordinates follow the new atlas.
            ghostmodel['elements']=make_elements(ghostparts,regions)
            ghostmodel['textures']=deepcopy(base['textures']);ghostmodel['texture_size']=base['texture_size']
            packed(path/'warrior_ghost'/(key+'.json'),ghostmodel)
    print(key,len(original.get('elements',[])),'->',len(base['elements']),flush=True)


def ornaments(key,grip):
    parts=[];cx,cy,_=grip
    def unit(label,x,z,w,l,h,role=2,a=0,y=0):
        x+=cx;y+=cy
        parts.append(shape(label,(x-w/2,y-h/2,z-l/2),(x+w/2,y+h/2,z+l/2),role,a,(x,y,z)))
    def twins(label,x,z,w,l,h,role=2,ang=0,y=0):
        for si in (-1,1):unit(label,si*x,z,w,l,h,role,si*ang,y)
    def seal(x,z,w,l,h,y=.59):
        for si in (-1,1):
            unit("Polished boss",x,z,w,l,h,2,45,y*si)
            unit("Inlaid stone",x,z,w*.59,l*.59,h*.7,4,45,(y+h*.5)*si)
    def hoop(label,x,z,r,role,thick):
        for deg in range(0,360,45):
            a=math.radians(deg)
            px=x+math.cos(a)*r;pz=z+math.sin(a)*r
            if deg%180==90:unit(label,px,pz,r*.86,thick,thick*1.25,role)
            else:
                angle={0:0,45:-45,135:45}[deg%180]
                unit(label,px,pz,thick,r*.86,thick*1.25,role,angle)
    if key=='frost_katana':
        twins("Lunar tsuba",1.06,4.49,.40,1.02,1.16,2,45)
        twins("Snow-lipped tsuba",1.20,4.28,.16,.63,.78,1,45)
        unit("Blue habaki",.16,5.15,1.27,.48,.83,2);seal(.06,4.62,.51,.51,.18)
    elif key=='star_rapier':
        twins("Swept quillon",1.70,4.06,.26,1.83,.50,2,45)
        twins("Quillon pearl",2.27,4.68,.33,.43,.42,4,45)
        unit("Knuckle arch",-1.15,2.55,.26,2.25,.40,2,-22.5)
        unit("Guard return",-.78,1.10,.24,1.12,.40,2,-45);seal(0,4.06,.60,.60,.15)
    elif key=='dragon_sword':
        twins("Dragon pinion",1.90,5.47,.61,2.22,1.13,2,45)
        twins("Split outer horn",2.54,6.22,.28,1.40,.63,1,22.5)
        twins("Jaw barb",1.18,4.61,.31,.98,.97,2,-45);seal(0,5.72,.92,1.12,.24,.78)
    elif key=='yue_bronze_jian':
        twins("Upswept sword guard",1.14,3.93,.36,1.02,1.07,2,-45)
        twins("Turquoise guard sleeve",.61,4.06,.24,.55,.95,4,22.5)
        unit("Pommel medallion",0,-.62,1.39,.43,1.28,2);seal(0,3.87,.48,.48,.14)
    elif key=='willow_leaf_dao':
        unit("Willow knuckle",-.98,3.30,.23,1.45,.65,2,22.5)
        twins("Shell guard",.85,3.95,.42,.82,1.10,2,-45);seal(0,3.9,.49,.49,.15)
    elif key=='ring_pommel_dao':
        twins("Ring shoulders",.61,-1.15,.24,.92,.53,2,22.5)
        twins("Gold ring return",.45,-1.91,.24,.69,.53,2,-45)
        twins("Clipped ferrule",.78,3.51,.32,.85,.87,2,45)
    elif key=='han_iron_jian':
        twins("Pointed iron quillon",1.06,3.91,.36,1.04,.95,2,45);seal(0,3.77,.49,.49,.17)
    elif key=='ashwood_jian':
        twins("Chamfered ash guard",1.30,3.80,.40,.90,.99,2,45)
        unit("Lacquered collar",0,4.37,1.46,.30,1.07,2)
    elif key=='cloudridge_jian':
        twins("Folded cloud scroll",1.72,4.26,.47,1.35,.78,2,45)
        twins("Cloud scroll return",2.13,4.88,.19,.7,.55,1,-22.5)
        seal(0,4.15,.70,.81,.20,.75)
        twins("Jade raindrop",1.87,4.27,.20,.44,.93,4,45)
    elif key=='crimson_warblade':
        twins("Ash-flame guard",1.91,5.79,.51,1.78,1.16,2,45)
        unit("Back swept breaker",1.60,7.01,.35,1.72,.70,2,22.5)
        seal(-.42,6.21,.92,.92,.17,.75)
    elif key in ('doom_scythe','iron_crescent_scythe','white_bone_scythe','eclipse_scythe','field_scythe'):
        z={'doom_scythe':13.5,'iron_crescent_scythe':13.1,'white_bone_scythe':13.8,'eclipse_scythe':14.0,'field_scythe':12.8}[key]
        if key=='field_scythe':
            unit("Forged heel clamp",-.42,z,.35,1.39,1.12,2,22.5)
        elif key=='iron_crescent_scythe':
            unit("Rising heel pick",1.80,z+.14,.44,2.2,.85,0,45)
            seal(-.10,z,.67,.67,.18)
        elif key=='white_bone_scythe':
            for j in range(3):
                unit("Ivory rib",-.73-j*.72,z+.45+j*.18,.34,1.21-j*.18,1.25-j*.08,0,-22.5)
            unit("Split heel fang",1.05,z-.36,.45,1.77,.77,1,-22.5)
            unit("Hooked ivory tine",1.46,z-1.05,.23,1.04,.56,0,22.5)
            seal(-.21,z,.49,.62,.15)
        else:
            for j in range(2):
                unit("Raised split spine",-1.36-j*.81,z+1.0+j*.28,.40,1.37-j*.18,1.02,2,-45)
            seal(-.28,z,.82,.82,.23,.78)
            if key=='eclipse_scythe':
                hoop("Eclipsed socket",1.83,z+.42,1.07,2,.20)
                unit("Crescent heel talon",2.54,z-.12,.28,1.13,.48,4,-22.5)
                seal(.12,11.9,.52,.64,.14)
            else:
                unit("Reaping spur",1.12,z+.65,.49,1.56,.85,2,45)
                unit("Reaping spur tip",1.72,z+1.15,.25,1.14,.56,4,22.5)
    elif key=='crescent_guandao':
        twins("Dragon collar fins",1.10,12.37,.37,1.43,1.02,2,45)
        seal(.35,13.46,.87,.75,.2,.72)
    elif key=='monk_spade':
        twins("Lotus collar",.67,12.17,.29,1.45,.79,2,22.5)
        seal(0,12.37,.6,.60,.18)
    elif key=='iron_jian_mace':
        twins("Square-section collar",.85,3.92,.35,1.10,1.02,2,45)
        for q in (4.9,7.85,10.8):unit("Flute step",0,q,.40,.28,1.28,1)
    elif key=='earthshaker_hammer':
        twins("Vented bronze shoulder",2.72,12.78,.57,2.7,3.05,2,22.5,.95)
        seal(0,12.85,1.0,1.0,.25,2.1)
        twins("Ground tooth",2.17,10.57,.40,1.08,1.15,0,-22.5,.83)
    elif key=='ironwood_mallet':
        unit("Wedge plate",0,13.57,1.01,.35,1.18,2)
        twins("Wrought ferrule lugs",2.95,11.56,.42,1.56,2.51,2,0,.06)
    elif key=='ring_flange_hammer':
        twins("Splayed iron fins",2.6,13.54,.48,2.23,1.72,0,22.5)
        twins("Flange collars",1.43,12.50,.34,.92,1.45,2,-45)
    elif key=='thunder_bronze_hammer':
        twins("Striking cheek",4.31,13.34,.50,2.47,2.95,0)
        seal(0,13.37,1.21,1.21,.19,1.96)
    return parts


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('ids',nargs='*')
    for weapon in parser.parse_args().ids or DESIGNS:generate(weapon)
