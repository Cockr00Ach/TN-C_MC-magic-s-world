"""Standalone mesh study: rounded Chinese ink dragon, editable BBModel + standard GLB.

Does not touch existing mod assets, boss registrations, jars or authored dragon rigs.
All previews are rasterizations of the exported mesh, never concept-art substitutes.
"""
import json, math, struct, uuid
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'model-source/ink-celestial-dragon'
OUT.mkdir(parents=True,exist_ok=True)
PALETTE=[(.022,)*3,(.055,)*3,(.10,)*3,(.17,)*3,(.26,)*3,(.38,)*3,(.53,)*3,(.69,)*3,(.84,)*3,(.95,)*3]
PARTS=[]
rng=np.random.default_rng(731)
def unit(v):
    v=np.asarray(v,dtype=float);return v/max(1e-10,np.linalg.norm(v))
def frames(path):
    tang=np.gradient(path,axis=0);tang=np.array([unit(t) for t in tang]);norm=[];binorm=[]
    for t in tang:
        n=unit(np.cross([0,0,1],t));b=unit(np.cross(t,n));norm.append(n);binorm.append(b)
    return tang,np.array(norm),np.array(binorm)
def spline(points,count):
    p=np.array([points[0],*points,points[-1]],dtype=float);result=[]
    for t in np.linspace(0,len(points)-1,count):
        i=min(int(t),len(points)-2);u=t-i;a,b,c,d=p[i:i+4]
        result.append(.5*((2*b)+(-a+c)*u+(2*a-5*b+4*c-d)*u*u+(-a+3*b-3*c+d)*u**3))
    return np.array(result)
def part(name,v,f,m,group):
    if not len(f):return
    PARTS.append(dict(name=name,v=np.asarray(v,dtype=float),f=np.asarray(f,dtype=np.int32),
                      m=np.full(len(f),m,dtype=int) if np.isscalar(m) else np.array(m,dtype=int),group=group))
def tube(name,points,radii,mat=1,group='head',sides=12,count=24,elliptic=1):
    p=spline(points,count);t,n,b=frames(p);rs=np.interp(np.linspace(0,1,count),np.linspace(0,1,len(radii)),radii)
    v=[];f=[]
    for j,at in enumerate(p):
        for k in range(sides):
            a=k*math.tau/sides;v.append(at+rs[j]*(math.cos(a)*n[j]+math.sin(a)*b[j]*elliptic))
    for j in range(count-1):
        for k in range(sides):
            a=j*sides+k;c=j*sides+(k+1)%sides;f.extend([(a,c,c+sides),(a,c+sides,a+sides)])
    v.extend([p[0],p[-1]]);base=len(v)-2
    for k in range(sides):
        f.append((base,(k+1)%sides,k));f.append((base+1,(count-1)*sides+k,(count-1)*sides+(k+1)%sides))
    part(name,v,f,mat,group)
def ellipsoid(name,center,scale,mat,group='head',sides=28,rings=14,tilt=0):
    v=[];f=[];cz,sz=math.cos(tilt),math.sin(tilt)
    for j in range(rings+1):
        lat=-math.pi/2+math.pi*j/rings
        for k in range(sides):
            a=math.tau*k/sides;x=scale[0]*math.cos(lat)*math.cos(a);y=scale[1]*math.sin(lat);z=scale[2]*math.cos(lat)*math.sin(a)
            v.append(np.array(center)+[cz*x-sz*y,sz*x+cz*y,z])
    for j in range(rings):
        for k in range(sides):
            a=j*sides+k;b=j*sides+(k+1)%sides;f.extend([(a,b,b+sides),(a,b+sides,a+sides)])
    part(name,v,f,mat,group)
def leaf(name,points,width,mat=0,group='mane',outline=True):
    p=spline(points,14);v=[];f=[]
    for i,at in enumerate(p):
        t=unit(p[min(i+1,len(p)-1)]-p[max(i-1,0)])
        n=unit(np.cross(t,[0,0,1]));w=width*math.sin(math.pi*(i+.4)/(len(p)+.4))**.8
        v.extend([at-n*w,at+np.array([0,0,.035]),at+n*w])
    for i in range(len(p)-1):
        a=i*3;f.extend([(a,a+3,a+1),(a+1,a+3,a+4),(a+1,a+4,a+2),(a+2,a+4,a+5)])
    part(name,v,f,mat,group)
    if outline:
        tube(name+'_pale_edge',np.array(v)[::3], [.018,.012,.001],6,group,6,16)

# Large, airborne S-coil; one coordinate unit is one Minecraft block.
controls=[[-4,15,0],[-1,17,.1],[4,14,1],[3,9,.4],[-2,7,-.5],[-6,7,-.5],[-7,4,.3],[-3,2,1],
          [3,3,1.2],[6,5,.6],[8,3,-.3],[5,.5,-1],[0,0,-.5],[-5,1,-.3],[-8,.2,0],[-10,2,.5]]
body=spline(controls,225);tangent,normal,binormal=frames(body)
ts=np.linspace(0,1,len(body));radius=np.interp(ts,[0,.12,.35,.58,.72,.86,1],[.72,.92,1.05,.9,.66,.34,.018])
SIDES=40
for segment in range(28):
    start=segment*8;end=min(start+8,224);v=[];f=[];materials=[]
    for j in range(start,end+1):
        for k in range(SIDES):
            a=k*math.tau/SIDES;v.append(body[j]+radius[j]*(math.cos(a)*normal[j]+math.sin(a)*binormal[j]))
    for j in range(end-start):
        for k in range(SIDES):
            a=j*SIDES+k;b=j*SIDES+(k+1)%SIDES
            f.extend([(a,b,b+SIDES),(a,b+SIDES,a+SIDES)])
            belly=abs(k-SIDES/2)<5;materials.extend([7 if belly else 1]*2)
    part('round_spine_%02d'%segment,v,f,materials,'spine_%02d'%segment)
    # Individually raised overlapping shield-shaped scales; not a flat texture.
    sv=[];sf=[];sm=[]
    for j in range(start+1,end+1):
        if ts[j]>.91:continue
        for k in range(18):
            a=(k+(.5 if j%2 else 0))*math.tau/18
            outward=math.cos(a)*normal[j]+math.sin(a)*binormal[j]
            around=-math.sin(a)*normal[j]+math.cos(a)*binormal[j]
            center=body[j]+outward*(radius[j]+.035);r=radius[j]
            width=r*.20;length=max(.18,float(np.linalg.norm(body[j]-body[j-1]))*1.5)
            base=len(sv);sv.append(center+outward*.08*r)
            outline=[(-.48,0),(-.28,-.83),(.15,-1),(.62,-.58),(.92,0),(.62,.58),(.15,1),(-.28,.83)]
            for x,y in outline:sv.append(center+tangent[j]*x*length+around*y*width)
            m=7 if math.cos(a)<-.72 else int(rng.choice([1,2,3,4],p=[.22,.43,.28,.07]))
            for edge in range(8):sf.append((base,base+1+edge,base+1+(edge+1)%8));sm.append(m if edge<6 else min(8,m+1))
    part('overlapping_scales_%02d'%segment,sv,sf,sm,'spine_%02d'%segment)
    if segment%1==0 and start<208:
        j=start+3;p=body[j]+normal[j]*radius[j]
        h=.32 if ts[j]<.6 else .18
        leaf('dorsal_brush_%02d'%segment,[p,p+normal[j]*h-tangent[j]*.10,p+normal[j]*(h*.55)+tangent[j]*.65],.14,1,'spine_%02d'%segment)

# A continuous low dorsal crest connects the wisps, rather than isolated giant hooks.
ridge=[];rf=[];tips=[]
for j in range(4,216):
    base=body[j]+normal[j]*(radius[j]+.04);tip=base+normal[j]*(.16+.09*math.sin(j*.65)**2)
    ridge.extend([base,tip]);tips.append(tip)
    if j>4:
        a=(j-5)*2;rf.extend([(a,a+1,a+3),(a,a+3,a+2)])
part('continuous_dorsal_fin',ridge,rf,2,'dorsal_crest')
tube('dorsal_silver_rim',tips,[.014,.02,.003],5,'dorsal_crest',6,212)

# Sculpted muzzle, cheek masses, open jaw, nostrils and slanted recessed eyes.
ellipsoid('cranium',[-5.05,14.9,0],[1.32,.82,.8],1)
ellipsoid('broad_brow_bridge',[-5.75,15.2,0],[.88,.46,.65],2)
ellipsoid('tapered_upper_muzzle',[-6.8,14.55,0],[1.18,.37,.55],2,tilt=.12)
ellipsoid('nose',[-7.78,14.56,0],[.34,.28,.48],2)
ellipsoid('lower_jaw',[-6.7,13.91,0],[1.1,.18,.46],2,tilt=-.08)
ellipsoid('mouth_cavity',[-6.72,14.12,0],[1.05,.18,.4],0)
ellipsoid('chin',[-5.9,13.88,0],[.5,.23,.5],3)
for s in [-1,1]:
    ellipsoid('cheek_'+str(s),[-5.4,14.5,s*.58],[.69,.57,.36],1)
    ellipsoid('nostril_'+str(s),[-7.77,14.69,s*.385],[.14,.105,.045],0,sides=16,rings=8)
    ellipsoid('eye_socket_'+str(s),[-5.75,15.13,s*.69],[.44,.22,.16],0)
    ellipsoid('silver_eye_'+str(s),[-5.93,15.12,s*.805],[.21,.075,.055],9,sides=20,rings=8,tilt=-.25)
    ellipsoid('slit_pupil_'+str(s),[-5.985,15.125,s*.854],[.023,.069,.013],0,sides=12,rings=8)
    tube('angry_brow_'+str(s),[[-6.3,15.17,s*.75],[-5.95,15.36,s*.83],[-5.42,15.52,s*.72],[-5.08,15.43,s*.53]], [.11,.14,.08,.005],0,sides=12,count=28)
    tube('muzzle_ridge_'+str(s),[[-7.82,14.78,s*.32],[-7.05,14.92,s*.43],[-6.48,14.9,s*.47]], [.06,.07,.018],5,sides=10)
    tube('flared_nostril_rim_'+str(s),[[-7.94,14.66,s*.38],[-7.81,14.82,s*.45],[-7.61,14.78,s*.46]],[.035,.05,.012],5,sides=10,count=20)
    tube('upper_snarl_lip_'+str(s),[[-7.78,14.36,s*.42],[-7.03,14.33,s*.56],[-6.35,14.39,s*.57],[-5.88,14.65,s*.62]],[.055,.065,.075,.012],3,sides=10,count=35)
    tube('cheek_swept_ridge_'+str(s),[[-6.15,14.78,s*.71],[-5.75,14.63,s*.87],[-5.09,14.57,s*.88],[-4.57,14.90,s*.72]],[.04,.12,.14,.015],3,sides=12,count=30)
    for row in range(4):
        for col in range(5):
            p=np.array([-6.2+col*.26,15.38-row*.24,s*(.66+.09*math.sin(col*.8))])
            if row<2:p[1]+=.12
            leaf(f'facial_armour_{s}_{row}_{col}',[p,p+[.15,.045,s*.045],p+[.36,.12,s*.02]],.055,2+(row+col)%3,'face_armour',False)
    for i in range(4):
        p=np.array([-6.7-i*.21,14.91,s*.21])
        leaf(f'bridge_scale_{s}_{i}',[p,p+[.12,.09,s*.03],p+[.3,.04,s*.02]],.09,3+(i%2),'face_armour',False)
    for i in range(7):
        x=-7.55+i*.25
        length=.23+.09*math.sin(i*2.3)**2
        tube(f'upper_tooth_{s}_{i}',[[x,14.32,s*.39],[x-.06,14.32-length*.6,s*.40],[x-.12,14.32-length,s*.38]],[.065+(i%3)*.012,.04,.002],8,sides=8,count=8)
    for i in range(5):
        x=-7.4+i*.3
        tube(f'lower_tooth_{s}_{i}',[[x,14.02,s*.37],[x-.03,14.18,s*.38]],[.06,.001],7,sides=8,count=6)
    tube('curved_fang_'+str(s),[[-6.14,14.35,s*.47],[-6.27,13.93,s*.54],[-6.50,13.65,s*.48]],[.13,.085,.002],8,count=18)
    # Antler main beam and three forks, all rounded tapered tubes.
    horn=[[-4.85,15.5,s*.5],[-4.6,16.35,s*.68],[-4.05,17.35,s*.9],[-3.5,18.3,s*1.08],[-3.8,19.25,s*1.12]]
    tube('antler_beam_'+str(s),horn,[.23,.2,.14,.075,.003],6,'antlers',14,55)
    for j in range(3):
        p=np.array(horn[j+1]);tube(f'antler_fork_{s}_{j}',[p,p+[-.7,.55,s*.1],p+[-1.1,1.25,s*.24]],[.13-j*.025,.07,.002],7,'antlers',10,25)
    tube('flowing_whisker_'+str(s),[[-7.5,14.5,s*.46],[-8.65,14.7,s*.9],[-10.4,15.5,s*1.3],[-11.2,15.0,s*1.6],[-11,13.85,s*1.8]], [.06,.05,.035,.017,.001],6,'whiskers',8,75)
    tube('lower_whisker_'+str(s),[[-7,14.1,s*.48],[-8.7,13.5,s*1.1],[-9.35,12.9,s*1.45]], [.038,.025,.001],4,'whiskers',7,45)
    for i in range(15):
        p=np.array([-4.8+i*.075,15.1+(i%5)*.13,s*(.5+i*.028)])
        length=1.5+(i%6)*.27
        leaf(f'mane_flame_{s}_{i}',[p,p+[length*.45,.5+(i%3)*.18,s*.28],p+[length,1.5-i*.09,s*.58]],.14+(i%4)*.045,0,'mane')
    for i in range(9):
        p=np.array([-6.3+i*.11,13.84,s*(.06+i*.045)])
        leaf(f'chin_beard_{s}_{i}',[p,p+[.25,-.7,s*.13],p+[1.2+i*.08,-1.8,s*.45]],.035+(i%3)*.020,1,'beard')
        tube(f'beard_filament_{s}_{i}',[p,p+[.3,-.85,s*.15],p+[1.2+i*.08,-1.7,s*.45],p+[1.8+i*.11,-1.6,s*.5]],[.025,.018,.011,.001],4,'beard',6,30)
    for i in range(11):
        p=np.array([-5.9+(i%4)*.22,15.5+(i//4)*.13,s*(.18+i*.043)])
        leaf(f'head_scale_{s}_{i}',[p,p+[.35,.10,s*.04],p+[.55,.02,s*.05]],.08,3,'head')

# Third-form crown: layered brow plates, etched horn roots, swept gills and an articulated mouth.
# Fine facial etching is deliberately named facial_armour so the far LOD drops it.
for s in [-1,1]:
    for row in range(3):
        p=np.array([-6.35+row*.12,15.22+row*.12,s*(.76-row*.05)])
        leaf(f'crown_brow_plate_{s}_{row}',[p,p+[.48,.25,s*.07],p+[1.18,.33,s*.02]],.08+row*.015,2+row,'head')
    for i in range(6):
        p=np.array([-5.32+i*.105,14.53+(i%2)*.14,s*(.88+i*.025)])
        leaf(f'cheek_gill_blade_{s}_{i}',[p,p+[.42,.18,s*.16],p+[.97,.52+i*.04,s*.13]],.075,1+i%3,'head')
    for i in range(8):
        p=np.array([-4.85+i*.026,15.6+i*.085,s*(.51+i*.017)])
        tube(f'facial_armour_horn_etch_{s}_{i}',[p+[-.14,0,-s*.08],p+[-.17,.04,0],p+[0,.08,s*.17]], [.012,.022,.008],3+i%3,'antlers',6,8)
    for i in range(10):
        p=np.array([-7.55+i*.14,14.86+math.sin(i*.4)*.08,s*(.28+i*.016)])
        leaf(f'facial_armour_muzzle_{s}_{i}',[p,p+[.12,.04,s*.03],p+[.23,0,s*.03]],.04,3+i%3,'head',False)
    tube(f'tear_duct_silver_{s}',[[-6.17,15.11,s*.8],[-6.05,15.04,s*.85],[-5.84,15.04,s*.85]],[.018,.026,.004],7,'head',8,16)
    tube(f'jaw_hinge_{s}',[[-5.73,14.23,s*.63],[-5.88,14.03,s*.66],[-6.2,13.95,s*.49]],[.12,.10,.02],2,'jaw',12,18)
    for i in range(5):
        p=np.array([-4.5+i*.11,15.4+i*.12,s*(.43+i*.06)])
        leaf(f'crown_ink_plume_{s}_{i}',[p,p+[.7,.65,s*.2],p+[1.6,.9+i*.1,s*.45]],.105,0,'mane')
ellipsoid('inner_mouth_shadow',[-6.85,14.19,0],[.95,.12,.36],0,'jaw',24,10)
tube('tongue_ink_ridge',[[-6.1,14.09,0],[-6.8,14.12,0],[-7.4,14.1,0]],[.16,.18,.07],3,'jaw',12,16)
leaf('forehead_central_keel',[[-6.1,15.55,0],[-5.55,16.02,0],[-4.6,16.25,0]],.20,5,'head')

# Four articulated-looking legs, five separate digits with curled claws on each paw.
for limb,u in [('fore',.27),('hind',.62)]:
    j=int(u*224)
    for s in [-1,1]:
        group=f'{limb}_leg_{s}';root=body[j]+binormal[j]*s*radius[j]*.7
        elbow=root+np.array([.8,-.7,s*.9]);wrist=root+np.array([.25,-1.7,s*1.7]);paw=root+np.array([-.45,-2.05,s*2.05])
        tube(group+'_upper',[root,root+[.4,-.2,s*.3],elbow],[.34,.40,.25],1,group,18,22)
        tube(group+'_forearm',[elbow,wrist,paw],[.25,.18,.22],2,group,16,22)
        ellipsoid(group+'_palm',paw,[.42,.18,.4],1,group,20,10)
        for d in range(5):
            offset=(d-2)*.18;p=paw+np.array([-.1,0,offset]);tip=p+[-.9-(2-abs(d-2))*.12,-.07,offset*.7]
            tube(f'{group}_digit_{d}',[p,p+[-.45,-.10,offset*.4],tip],[.115,.08,.045],2,group,10,16)
            tube(f'{group}_talon_{d}',[tip,tip+[-.22,-.12,0],tip+[-.35,-.05,-offset*.25]],[.08,.045,.001],8,group,10,16)
        for i in range(5):
            p=root+np.array([.3-i*.15,-.2-i*.12,s*.18])
            leaf(f'{group}_ink_tuft_{i}',[p,p+[.9,.4,s*.25],p+[1.6,.3,s*.3]],.10,0,group)
for i in range(13):
    p=body[-9]+normal[-9]*(i-6)*.035
    leaf('tail_ink_flame_%02d'%i,[p,p+[-.6,.6+(i%4)*.18,(i-6)*.08],p+[-1.8,1.0+(i%5)*.21,(i-6)*.13]],.06+(i%3)*.025,0,'tail')

def normals(v,f):
    n=np.zeros_like(v);face=np.cross(v[f[:,1]]-v[f[:,0]],v[f[:,2]]-v[f[:,0]])
    for i in range(3):np.add.at(n,f[:,i],face)
    lengths=np.linalg.norm(n,axis=1);n/=np.maximum(lengths[:,None],1e-10)
    return n

def export_glb():
    chunks=bytearray();views=[];access=[];meshes=[];nodes=[]
    def acc(a,kind,component):
        a=np.ascontiguousarray(a);offset=len(chunks);data=a.tobytes();chunks.extend(data);chunks.extend(b'\0'*((-len(chunks))%4))
        views.append({'buffer':0,'byteOffset':offset,'byteLength':len(data)})
        item={'bufferView':len(views)-1,'componentType':component,'count':len(a),'type':kind}
        if kind=='VEC3':item.update(min=a.min(axis=0).tolist(),max=a.max(axis=0).tolist())
        access.append(item);return len(access)-1
    for p in PARTS:
        # Split by vertex colour to preserve the real scale material variations in GLB.
        f=p['f'];v=p['v'][f].reshape(-1,3);n=normals(p['v'],f)[f].reshape(-1,3)
        c=np.repeat(np.array(PALETTE)[p['m']],3,axis=0)
        attrs={'POSITION':acc(v.astype('<f4'),'VEC3',5126),'NORMAL':acc(n.astype('<f4'),'VEC3',5126),'COLOR_0':acc(c.astype('<f4'),'VEC3',5126)}
        meshes.append({'name':p['name'],'primitives':[{'attributes':attrs,'material':0,'mode':4}]})
        nodes.append({'name':p['name'],'mesh':len(meshes)-1})
    doc={'asset':{'version':'2.0','generator':'TN-C ink dragon mesh study'},'scene':0,'scenes':[{'nodes':list(range(len(nodes)))}],
         'nodes':nodes,'meshes':meshes,'buffers':[{'byteLength':len(chunks)}],'bufferViews':views,'accessors':access,
         'materials':[{'name':'Ink grayscale','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[1,1,1,1],'metallicFactor':.08,'roughnessFactor':.62}}]}
    j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*((-len(j))%4)
    total=12+8+len(j)+8+len(chunks)
    (OUT/'ink_celestial_dragon.glb').write_bytes(struct.pack('<III',0x46546C67,2,total)+struct.pack('<II',len(j),0x4E4F534A)+j+struct.pack('<II',len(chunks),0x004E4942)+chunks)

def export_bbmodel():
    import base64,io
    tex=Image.new('RGB',(256,32));draw=ImageDraw.Draw(tex)
    for i,c in enumerate(PALETTE):draw.rectangle((i*25,0,i*25+24,31),fill=tuple(round(x*255) for x in c))
    tex.save(OUT/'ink_dragon_palette.png');stream=io.BytesIO();tex.save(stream,format='PNG')
    elements=[];groups={}
    for p in PARTS:
        uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'ink_dragon/'+p['name']));vertices={f'v{i}':(v*16).round(5).tolist() for i,v in enumerate(p['v'])}
        faces={}
        for i,(f,m) in enumerate(zip(p['f'],p['m'])):
            keys=[f'v{n}' for n in f];u=int(m)*25
            faces[f'f{i}']={'vertices':keys,'uv':dict(zip(keys,[[u+2,2],[u+22,2],[u+12,28]])),'texture':0}
        elements.append({'name':p['name'],'type':'mesh','uuid':uid,'origin':[0,0,0],'rotation':[0,0,0],
                         'vertices':vertices,'faces':faces,'color':0,'visibility':True,'export':True,'locked':False,'autouv':0})
        groups.setdefault(p['group'],[]).append(uid)
    outliner=[{'name':k,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'ink_dragon/group/'+k)),'origin':[0,0,0],
               'rotation':[0,0,0],'children':v,'export':True,'isOpen':False,'visibility':True} for k,v in groups.items()]
    doc={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'玄墨天龙 · 曲面精模',
         'model_identifier':'ink_celestial_dragon','resolution':{'width':256,'height':32},'elements':elements,'outliner':outliner,
         'textures':[{'name':'ink_dragon_palette.png','id':'0','uuid':str(uuid.uuid4()),'mode':'bitmap','saved':False,
                      'source':'data:image/png;base64,'+base64.b64encode(stream.getvalue()).decode(),'width':256,'height':32,'uv_width':256,'uv_height':32}],
         'animations':[],'display':{}}
    (OUT/'ink_celestial_dragon.bbmodel').write_text(json.dumps(doc,ensure_ascii=False,separators=(',',':')),encoding='utf-8')

def render(name,eye,target,width,height,span):
    forward=unit(np.array(target)-eye);right=unit(np.cross(forward,[0,1,0]));up=unit(np.cross(right,forward));basis=np.array([right,up,forward])
    image=np.zeros((height,width,3),dtype=np.float32);image[:]=[.93,.925,.91];depth=np.full((height,width),np.inf,dtype=np.float32)
    scale=width/span;lights=[unit([-.6,.8,1]),unit([.5,.3,-1])]
    for p in PARTS:
        v=(p['v']-target)@basis.T;screen=np.column_stack([v[:,0]*scale+width*.5,-v[:,1]*scale+height*.5,v[:,2]])
        n=normals(p['v'],p['f']);shade=.3+.55*np.abs(n@lights[0])+.15*np.abs(n@lights[1])
        for f,m in zip(p['f'],p['m']):
            tri=screen[f];x0=max(0,int(tri[:,0].min()));x1=min(width-1,int(tri[:,0].max()+1));y0=max(0,int(tri[:,1].min()));y1=min(height-1,int(tri[:,1].max()+1))
            if x1<x0 or y1<y0:continue
            a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
            w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
            w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
            z=w0*a[2]+w1*b[2]+w2*c[2];region=depth[y0:y1+1,x0:x1+1];mask=(w0>=0)&(w1>=0)&(w2>=0)&(z<region)
            if not np.any(mask):continue
            brightness=w0*shade[f[0]]+w1*shade[f[1]]+w2*shade[f[2]]
            rgb=np.clip(np.array(PALETTE[int(m)])[None,None,:]*brightness[:,:,None],0,1)
            image[y0:y1+1,x0:x1+1][mask]=rgb[mask];region[mask]=z[mask]
    pic=Image.fromarray(np.uint8(np.clip(image,0,1)*255));draw=ImageDraw.Draw(pic)
    draw.text((30,24),'INK CELESTIAL DRAGON / ACTUAL EXPORTED MESH / NO CUBES',fill=(45,45,45))
    draw.text((30,height-35),'BLACK INK / PALE SCALES / ANTLERS / FIVE-TOED CLAWS / FLOWING MANE',fill=(75,75,75))
    pic.save(OUT/name)

if __name__=='__main__':
    export_glb();export_bbmodel()
    points=np.concatenate([p['v'] for p in PARTS]);triangles=sum(len(p['f']) for p in PARTS)
    report={'mesh_parts':len(PARTS),'triangles':triangles,'vertices':sum(len(p['v']) for p in PARTS),
            'bounds_blocks':[points.min(axis=0).round(2).tolist(),points.max(axis=0).round(2).tolist()],
            'body_length_blocks':round(float(np.linalg.norm(np.diff(body,axis=0),axis=1).sum()),2),
            'format':'Free mesh BBModel + GLB; no cubes; not yet a GeckoLib runtime entity'}
    (OUT/'model_stats.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report),flush=True)
    render('preview_full.png',np.array([16,17,46]),np.array([-1,9,0]),1600,1600,26)
    print('full preview ready',flush=True)
    render('preview_head.png',np.array([-14,18,22]),np.array([-5.9,15.8,0]),1600,1200,12.4)
    print('head preview ready',flush=True)
    render('preview_side.png',np.array([0,10,50]),np.array([-1,9,0]),1600,1600,26)
    print('side preview ready',flush=True)
