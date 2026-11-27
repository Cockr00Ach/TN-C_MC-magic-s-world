"""Restore the original bronze line as block-built, patinated ceremonial weapons.

Reads the seven shipped Java models, preserves stock poses, and writes the resource
pack below this file. No registry, combat, instance or puppet files are touched.
"""
import argparse
from copy import deepcopy
from collections import defaultdict
from pathlib import Path
import json
import math
import numpy as np
from PIL import Image, ImageDraw

DEST=Path(__file__).resolve().parents[1]/'资源包/assets/tnc'
KINDS=('greatsword','spear','dao','dagger','twin_main','twin_off')
SIDES=('north','south','east','west','up','down')
# Six contiguous weapon-specific UV islands. The remaining 128 px contain shared fittings.
TILES={'bronze':(384,0,416,64),'edge':(416,0,448,64),'patina':(448,0,480,64),'black':(480,0,512,64),
       'brass':(384,64,416,128),'jade':(416,64,448,128),'wood':(448,64,480,128),'wrap':(480,64,512,128),
       'rib':(384,128,416,192),'line':(416,128,448,192),'red':(448,128,480,192),'darkgold':(480,128,512,192)}
COLORS={'bronze':(147,100,60),'edge':(232,190,127),'patina':(47,99,87),'black':(52,48,39),
        'brass':(199,144,78),'jade':(89,149,124),'wood':(75,43,32),'wrap':(112,52,38),
        'rib':(167,116,60),'line':(225,183,110),'red':(119,55,35),'darkgold':(97,69,47)}

def write_json(path,doc):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(doc,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')

def profile(kind,elems):
    source=[e for e in elems if e['name'] in ('forged_twin_bevel','bevelled_blade')]
    rows=defaultdict(list)
    for e in source:rows[e['from'][1]].append(e)
    sections=[]
    for y,items in sorted(rows.items()):
        spans=[]
        for e in sorted(items,key=lambda e:e['from'][0]):
            lo=e['from'][0];hi=e['to'][0]
            if spans and lo<=spans[-1][1]+.0002:spans[-1][1]=max(spans[-1][1],hi)
            else:spans.append([lo,hi])
        sections.append([y,items[0]['to'][1],spans])
    lo=min(s[2][0][0] for s in sections);hi=max(s[2][-1][1] for s in sections)
    return sections,(lo-.15,sections[0][0],hi+.15,sections[-1][1])

def blade_tile(kind,k,rows,bounds):
    xmin,ymin,xmax,ymax=bounds
    xx,yy=np.meshgrid(np.arange(64),np.arange(256));x=xmin+(xmax-xmin)*(xx-1)/61
    yw=ymax-(ymax-ymin)*(yy-2)/251
    idx=np.searchsorted([r[0] for r in rows],yw,side='right')-1;idx=idx.clip(0,len(rows)-1)
    lo=np.array([s[2][0][0] for s in rows])[idx];hi=np.array([s[2][-1][1] for s in rows])[idx]
    u=np.clip((x-lo)/np.maximum(hi-lo,.02),0,1);t=np.clip((yw-ymin)/(ymax-ymin),0,1)
    knot=[0,.045,.095,.155,.225,.38,.47,.49,.51,.545,.73,.85,.925,.978,1]
    pal=np.array([(255,219,159),(213,167,98),(135,90,47),(188,148,87),(104,72,47),(91,74,54),
        (176,128,72),(232,178,99),(135,93,54),(91,80,57),(110,88,60),(83,65,46),(156,115,68),(225,186,123),(248,221,162)])
    if kind in ('spear','dagger'):
        pal[[5,9,10]]=[[68,85,70],[54,78,68],[81,87,65]]
    if kind in ('dao','twin_off'):
        knot=[0,.03,.09,.17,.265,.39,.57,.60,.62,.65,.75,.86,.93,.978,1]
        pal[[0,1,12]]=[[135,99,61],[107,73,45],[175,132,84]]
    col=np.stack([np.interp(u,knot,pal[:,i]) for i in range(3)],axis=-1)
    field=np.sin(xx*.26+yy*.087)+.74*np.sin(yy*.12-xx*.19)+.49*np.cos(xx*.63+yy*.053)
    oxide=np.clip((field-.27)*.56,0,.88)*np.clip((np.maximum(0,.32-t)*1.8+.33),.24,.95)*np.clip(1-abs(u-.50)*2,0,1)
    col=col*(1-oxide[:,:,None])+np.array([37,93,77])*oxide[:,:,None]
    edgeDist=np.minimum(u,1-u)
    shine=14*(1-np.abs(np.sin(yy*.035+xx*.085+k)))*np.exp(-edgeDist*5)
    brush=(np.sin(xx*1.62+yy*.058)+np.cos(xx*.71-yy*.031))*(2+2*np.sin(yy*.08))
    col+=((shine+brush+3*np.sin(yy*.071+k))[:,:,None])
    im=Image.fromarray(np.uint8(col.clip(0,255)));g=ImageDraw.Draw(im)
    def uvpx(s,h):
        y=ymin+(ymax-ymin)*h
        row=rows[max(0,min(len(rows)-1,np.searchsorted([r[0] for r in rows],y)-1))]
        a,b=row[2][0][0],row[2][-1][1];wx=a+(b-a)*s
        return (round(1+(wx-xmin)/(xmax-xmin)*61),round(253-h*251))
    # Incised yunlei curls issue from the collar and leave the upper blade calm.
    def line(points,color):
        g.line([uvpx(a,b) for a,b in points],fill=color,width=1)
    for sign in (-1,1):
        def mirrorpts(pts):return [(.5+sign*(u-.5),v) for u,v in pts]
        seq=[(.67,.025),(.82,.07),(.82,.15),(.59,.23),(.59,.315),(.71,.35),(.71,.40),(.62,.432)]
        if kind in ('dao','twin_off'):seq=[(.67,.03),(.76,.12),(.68,.175),(.68,.24),(.78,.29),(.72,.34)]
        top=1 if kind not in ('dagger','spear') else .74
        pts=[(u,h*top) for u,h in seq]
        for a in [0,.145*top]:
            paths=[(u,h+a) for u,h in pts]
            line(mirrorpts([(u-.018,v+.004) for u,v in paths]),(48,56,44))
            line(mirrorpts(paths),(186,146,91))
        line(mirrorpts([(.63,.037),(.73,.072),(.73,.115),(.65,.14),(.62,.126),(.62,.105),(.665,.10)]),(76,129,105))
        for t0 in (.11,.46,.65):
            x0,y0=uvpx(.5+sign*.285,t0);g.point((x0,y0),fill=(223,175,102))
    return im

def paint_atlas(profiles):
    atlas=Image.new('RGB',(512,256),(65,53,41))
    for i,kind in enumerate(KINDS):atlas.paste(blade_tile(kind,i,*profiles[kind]),(i*64,0))
    for i,(m,(x,y,xx,yy)) in enumerate(TILES.items()):
        h,w=yy-y,xx-x;v,u=np.mgrid[:h,:w]
        texture=np.broadcast_to(np.array(COLORS[m]),(h,w,3)).astype(float).copy()
        if m=='wrap':
            band=(v+.62*u)%15
            texture+=np.interp(band,[0,1,3,5,10,13,15],[-31,-23,19,7,-2,-11,-31])[:,:,None]
            pits=(np.floor((u+v*.42)/3).astype(int)%3==0)&(np.abs(band-5)<.8)
            texture[pits]+=[28,14,7]
        elif m=='wood':
            grain=8*np.sin(u*.6+1.2*np.sin(v*.08))+3*np.cos(u*1.4+v*.11)
            texture+=grain[:,:,None]
        elif m=='jade':
            texture+=((np.sin(v*.075+u*.36)+np.cos(u*.44-v*.16))*8)[:,:,None]
        else:
            light=8*np.sin((u+.23*v)*.27)+3*np.cos(v*.31+u*.35)
            texture+=light[:,:,None]
            if m in ('bronze','darkgold','patina','rib'):
                verd=np.maximum(0,np.sin(u*.25-v*.19)+np.cos(u*.38+v*.11)-.8)*.40
                texture=texture*(1-verd[:,:,None])+np.array([56,99,78])*verd[:,:,None]
        atlas.paste(Image.fromarray(np.uint8(texture.clip(0,255))),(x,y))
    # One continuous taotie casting across the stepped guard. The square spirals
    # follow the exterior horns and the green enamel sits inside a dark chased line.
    v,u=np.mgrid[:64,:128];iv=np.minimum(u,127-u)
    mask_base=((-np.abs(v-33)*.5+10)+9*np.sin(iv*.088-v*.06))[:,:,None]
    face=Image.fromarray((np.array([125,86,53])+mask_base+3*np.cos(u*.75+v*.125)[:,:,None]).clip(0,255).astype('uint8'))
    pen=ImageDraw.Draw(face)
    for flip in [False,True]:
        def shape(points):return [(127-x if flip else x,y) for x,y in points]
        def chisel(points,width,light):
            pen.line(shape([(x,y+1) for x,y in points]),fill=(34,54,45),width=width+2)
            pen.line(shape(points),fill=light,width=width)
        chisel([(0,21),(12,26),(21,23),(18,14),(11,14),(11,17),(15,18)],2,(195,146,82))
        chisel([(0,42),(12,38),(19,41),(25,49),(37,49),(37,40),(29,36)],2,(54,113,91))
        chisel([(17,31),(29,21),(36,22),(40,27),(51,31),(57,28)],3,(186,139,75))
        chisel([(6,54),(18,50),(27,56),(43,56),(49,49),(49,43),(54,38)],1,(191,142,75))
        chisel([(40,5),(44,15),(56,22),(58,28)],2,(87,134,99))
        chisel([(46,57),(55,52),(59,46),(62,42)],2,(174,123,66))
        for x,y in ((7,8),(25,15),(24,51),(46,33),(54,12)):pen.point(shape([(x,y)])[0],fill=(233,180,105))
    atlas.paste(face,(384,192))
    path=DEST/'textures/item/bronze_atlas.png';path.parent.mkdir(parents=True,exist_ok=True);atlas.save(path,optimize=True)

def atlas_uv(px):
    return [round(float(v)/(32 if i%2==0 else 16),5) for i,v in enumerate(px)]

def build(kind,base,rows,bounds):
    elems=[];tag='#bronze' if kind.startswith('twin') else '#1'
    def block(name,low,high,material,front=None):
        lo=[round(float(v),4) for v in low];hi=[round(float(v),4) for v in high]
        if any(b-a<.002 for a,b in zip(lo,hi)):return
        x,y,z=lo;X,Y,Z=hi;px,py,ex,ey=TILES[material];cx=(px+ex)/2;cy=(py+ey)/2
        faces={}
        for side in SIDES:
            w,h=(X-x,Y-y) if side in ('north','south') else ((Z-z,Y-y) if side in ('east','west') else (X-x,Z-z))
            sw=min(ex-px-3,max(w*7,2));sh=min(ey-py-3,max(h*8,2))
            uv=atlas_uv([cx-sw/2,cy-sh/2,cx+sw/2,cy+sh/2])
            if front is not None and side in ('north','south'):
                # The custom paired renderer assigns its first UV to the lower edge.
                a,b,c,d=front
                if kind.startswith('twin'):
                    uv=atlas_uv([a,d,c,b] if side=='north' else [c,d,a,b])
                else:
                    uv=atlas_uv([c,b,a,d] if side=='north' else [a,b,c,d])
            faces[side]={'uv':uv,'texture':tag}
        elems.append(dict(name=name,**{'from':lo,'to':hi},faces=faces))

    blade_kind=KINDS.index(kind);xmin,ymin,xmax,ymax=bounds
    def painted(x,y,X,Y):
        return [blade_kind*64+1+(x-xmin)/(xmax-xmin)*61,2+(ymax-Y)/(ymax-ymin)*251,
                blade_kind*64+1+(X-xmin)/(xmax-xmin)*61,2+(ymax-y)/(ymax-ymin)*251]
    # Coalesce successive cross-sections while the silhouette drifts by <0.10 model unit.
    # Preserve each pierced region independently and keep the real widths of the hooked tips.
    i=0
    while i<len(rows):
        j=i+1
        while j<len(rows) and len(rows[j][2])==len(rows[i][2]) and max(abs(np.array(rows[j][2])-np.array(rows[i][2])).flat)<.105:j+=1
        y,Y=rows[i][0],rows[j-1][1];t=(y-ymin)/(ymax-ymin)
        spans=(np.array(rows[i][2])+np.array(rows[j-1][2]))*.5
        for s,(left,right) in enumerate(spans):
            width=right-left
            if kind=='twin_main' and len(spans)==2:
                if s==0:right-=.12
                else:left+=.12
            width=right-left
            if width<.46:
                block('honed tip',(left,y,7.86),(right,Y,8.14),'edge',painted(left,y,right,Y));continue
            # Thickness is carried by the casting, then steps down to a thin edge.
            bands=[0,.12,.29,.69,.88,1] if kind not in ('dao','twin_off') else [0,.11,.54,.77,.91,1]
            depths=[.25,.60,1,.66,.25] if kind not in ('dao','twin_off') else [.56,.84,1,.57,.21]
            cap=(.49 if kind.startswith('twin') else .66)*min(1,.45+width*.23)
            for k,v in enumerate(depths):
                a=left+width*bands[k];b=left+width*bands[k+1];z=max(.070,v*cap)
                block('forged bevel '+str(k),(a,y,8-z),(b,Y,8+z),'edge' if k in (0,4) else 'bronze',painted(a,y,b,Y))
        i=j
    src=base['elements']
    grip=next(e for e in src if e['name']=='ancient_wood_core')
    low,high=grip['from'],grip['to'];a,b=low[1],high[1];radius=(high[0]-low[0])/2
    def cuff(y,height,w,d,mat,name):
        block(name,(8-w,y,8-d),(8+w,y+height,8+d),mat)
    # Low-relief bindings and hemmed fittings express materials; the spear retains exposed shaft.
    cuff(a,b-a,radius,radius,'wood' if kind=='spear' else 'wrap','continuous grip core')
    wraps=[(.12,.27),(.46,.61)] if kind=='spear' else [(.07,.94)]
    for l,h in wraps:
        y0,y1=a+(b-a)*l,a+(b-a)*h
        sections=max(1,round((y1-y0)/3.8))
        for ix in range(sections):
            z0=y0+(y1-y0)*ix/sections;z1=y0+(y1-y0)*(ix+1)/sections
            cuff(z0,z1-z0,radius+.052,radius+.045,'wrap','oxblood wrapped grip')
        for y in (y0,y1-.11):cuff(y,.11,radius+.067,radius+.061,'darkgold','binding hem')
    for y in (a,b-.26):
        cuff(y,.26,radius+.17,radius+.13,'brass','burnished ferrule')
        cuff(y+.065,.068,radius+.195,radius+.152,'patina','incised ferrule')
    keep={'taotie_guard':'bronze','eye_recess':'black','eye_inlay':'jade','reverse_eye_recess':'black','reverse_eye_inlay':'jade',
        'taotie_nose':'brass','horn':'brass','guard_lip':'darkgold','ring_pommel':'brass','socket_plate':'patina','socket_ridge':'brass',
        'butt_cap':'brass','cast_shaft_ring':'patina','horned_guard':'bronze','continuous_blade_socket':'bronze','铸造刀根套口':'darkgold',
        'tail_socket':'bronze','pointed_cast_pommel':'bronze','bronze_pin_ring':'bronze','pin_head':'jade','reverse_tail_blade':'edge'}
    guard_y=14 if kind=='spear' else 2
    for e in src:
        name=e['name']
        if name not in keep:continue
        lo,hi=map(lambda q:np.array(q,float),(e['from'],e['to']))
        if hi[1]<0 and kind in ('greatsword','dagger'):
            factor=.54 if kind=='greatsword' else .51
            lo[0]=8+(lo[0]-8)*factor;hi[0]=8+(hi[0]-8)*factor
            lo[2]=8+(lo[2]-8)*.82;hi[2]=8+(hi[2]-8)*.82
        elif name in ('taotie_guard','horn','eye_recess','eye_inlay','reverse_eye_recess','reverse_eye_inlay','guard_lip','horned_guard'):
            gain={'greatsword':1.08,'dao':1.12,'dagger':.86,'spear':.85,'twin_main':1.05,'twin_off':1.04}[kind]
            lo[0]=8+(lo[0]-8)*gain;hi[0]=8+(hi[0]-8)*gain
        ornament=None
        if name=='taotie_guard':
            if hi[1]<0:
                g0=-9.8 if kind=='greatsword' else -4.3
                half=(3.70*.54 if kind=='greatsword' else 2.65*.51)
            else:
                g0=guard_y
                half={'greatsword':3.70*1.08,'dao':2.65*1.12,'dagger':2.65*.86,'spear':1.65*.85}[kind]
            uv=lambda x,y:(385+126*(x-8+half)/(2*half),194+58*(g0+1.75-y)/1.75)
            u0,v0=uv(lo[0],hi[1]);u1,v1=uv(hi[0],lo[1]);ornament=[u0,v0,u1,v1]
        block(name,lo,hi,keep[name],ornament)
    # Paired cheek flanges step toward the blade ferrule. Their teeth follow the weapon's hand guard.
    gy= b if kind in ('twin_main','twin_off') else (14 if kind=='spear' else 2)
    wing={'greatsword':3.78,'dao':3.02,'dagger':2.25,'spear':1.62,'twin_main':3.05,'twin_off':2.11}[kind]
    gheight=.68 if kind.startswith('twin') else 1
    # Raised keystone and paired-jaw nose. Depth is stepped away from the face so
    # the inlay reads from a glancing view as well as from the frontal inventory pose.
    for sign in (-1,1):
        for yoffs,height,hw,depth,material in [
              (.20,.20,.19,1.055,'brass'),(.40,.24,.33,1.065,'darkgold'),
              (.64,.44,.43,1.078,'brass'),(.76,.22,.26,1.108,'jade'),
              (1.08,.20,.29,1.057,'rib'),(1.28,.16,.15,1.023,'edge')]:
            z=8+sign*depth*gheight
            inner=8+sign*(depth-.26)*gheight
            block('taotie keystone',(8-hw*gheight,gy+yoffs*gheight,min(z,inner)),
                  (8+hw*gheight,gy+(yoffs+height)*gheight,max(z,inner)),material)
    for side in (-1,1):
        for k,(p,q,y,h,d,m) in enumerate([(.18,.46,.90,.32,.92,'patina'),(.43,.71,1.09,.34,.87,'bronze'),
                (.68,.89,1.28,.36,.76,'brass'),(.86,1.04,1.52,.43,.64,'bronze'),(.95,1.08,1.79,.29,.48,'edge')]):
            xs=sorted([8+side*wing*p,8+side*wing*q]);y0=gy+y*gheight
            block('rising guard quillon', (xs[0],y0,8-d*gheight),(xs[1],y0+h*gheight,8+d*gheight),m)
        # Chased brow and eye visible on both sides.
        for frontsign in (-1,1):
            xx=8+side*wing*.49;cy=gy+1.03*gheight;z=8+frontsign*.95*gheight
            block('recessed brow', (xx-.31,cy-.075,z-.041),(xx+.31,cy+.075,z+.041),'darkgold')
    collar=16.87 if kind=='spear' else (12.77 if kind.startswith('twin') else 3.62)
    cw={'greatsword':2.08,'spear':.63,'dao':1.72,'dagger':1.49,'twin_main':1.13,'twin_off':.87}[kind]
    for sign in (-1,1):
        for k in range(3):
            xx=8+sign*(.24+k*.23)*cw;cy=collar+(2-k)*.12;d=.82 if not kind.startswith('twin') else .60
            block('stepped cast throat',(xx-.19*cw,cy,8-d),(xx+.19*cw,cy+.48,8+d),'brass' if k==2 else 'patina')
    # Eliminate exact coboxes before hidden-face recovery, keeping the last visible material.
    seen=set();distinct=[]
    for e in reversed(elems):
        key=tuple(e['from']+e['to'])
        if key not in seen:distinct.append(e);seen.add(key)
    elems=list(reversed(distinct))
    if not kind.startswith('twin'):remove_hidden(elems)
    out=deepcopy(base);out['elements']=elems
    if 'groups' in out:out.pop('groups')
    return out

def remove_hidden(elems):
    lower=np.array([e['from'] for e in elems]);upper=np.array([e['to'] for e in elems]);eps=.00006
    for j,e in enumerate(elems):
        for s,axis,high in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
            coord=(upper if high else lower)[j,axis]
            other=[d for d in range(3) if d!=axis]
            mask=np.all(lower[:,other]<=lower[j,other]+eps,axis=1)&np.all(upper[:,other]>=upper[j,other]-eps,axis=1)
            if high:mask&=(lower[:,axis]<=coord+eps)&(upper[:,axis]>coord+eps)
            else:mask&=(upper[:,axis]>=coord-eps)&(lower[:,axis]<coord-eps)
            mask[j]=False
            if mask.any():e['faces'].pop(s)
    elems[:]=[e for e in elems if len(e['faces'])]

def generate(repo):
    source=repo/'src/main/resources/assets/tnc/models/item';target=DEST/'models/item'
    docs={k:json.loads((source/('bronze_'+k+'.json')).read_bytes()) for k in (*KINDS,'halberd')}
    profiles={k:profile(k,docs[k]['elements']) for k in KINDS}
    paint_atlas(profiles)
    finished={k:build(k,docs[k],*profiles[k]) for k in KINDS}
    both=deepcopy(docs['halberd']);both['elements']=[];both['groups']=[]
    for k,group,offset in zip(('twin_main','twin_off'),docs['halberd']['groups'],(-4.8,4.8)):
        start=len(both['elements'])
        for element in finished[k]['elements']:
            e=deepcopy(element)
            for key in ('from','to'):e[key][0]=round(e[key][0]+offset,4)
            for face in e['faces'].values():face['texture']='#1'
            both['elements'].append(e)
        g=deepcopy(group);g['children']=list(range(start,len(both['elements'])));both['groups'].append(g)
    finished['halberd']=both
    for k,out in finished.items():
        assert out['display']==docs[k]['display']
        for e in out['elements']:
            assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to']))
            assert all(0<=x<=16 for f in e['faces'].values() for x in f['uv'])
            if k.startswith('twin') or k=='halberd':assert set(e['faces'])==set(SIDES) and 'rotation' not in e
        write_json(target/('bronze_'+k+'.json'),out)
        print(k,len(docs[k]['elements']),'->',len(out['elements']),sum(len(e['faces']) for e in out['elements']))
    for y in (20,20.5,22.5,24.5,25):
        assert not any(e['from'][0]<8<e['to'][0] and e['from'][1]<=y<=e['to'][1] for e in finished['twin_main']['elements'])
    assert both['parent']=='builtin/entity'
    assert len(both['elements'])==len(finished['twin_main']['elements'])+len(finished['twin_off']['elements'])

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('repo',type=Path)
    generate(parser.parse_args().repo)
