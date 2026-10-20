"""Bake the approved free mesh into shared, GPU-ready triangle batches (three LODs)."""
import copy,hashlib,json,struct
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'model-source/ink-celestial-dragon/ink_celestial_dragon.bbmodel'
TARGET=ROOT/'src/main/resources/assets/tnc/meshes/ink_celestial_dragon.mesh'
PALETTE=np.array([.022,.055,.10,.17,.26,.38,.53,.69,.84,.95])
def unit(x):return x/max(np.linalg.norm(x),1e-10)
def normals(v,f):
    n=np.zeros_like(v);face=np.cross(v[f[:,1]]-v[f[:,0]],v[f[:,2]]-v[f[:,0]])
    for i in range(3):np.add.at(n,f[:,i],face)
    return n/np.maximum(np.linalg.norm(n,axis=1)[:,None],1e-10)

# Unwrap the approved sculpture along its actual centreline. Keep the detailed
# original file intact; preserve every near-LOD triangle and anatomical part.
def spline(points,count):
    p=np.array([points[0],*points,points[-1]],dtype=float);result=[]
    for t in np.linspace(0,len(points)-1,count):
        i=min(int(t),len(points)-2);u=t-i;a,b,c,d=p[i:i+4]
        result.append(.5*((2*b)+(-a+c)*u+(2*a-5*b+4*c-d)*u*u+(-a+3*b-3*c+d)*u**3))
    return np.array(result)
curve=spline([[-4,15,0],[-1,17,.1],[4,14,1],[3,9,.4],[-2,7,-.5],[-6,7,-.5],[-7,4,.3],[-3,2,1],
              [3,3,1.2],[6,5,.6],[8,3,-.3],[5,.5,-1],[0,0,-.5],[-5,1,-.3],[-8,.2,0],[-10,2,.5]],225)
arc=np.r_[0,np.cumsum(np.linalg.norm(np.diff(curve,axis=0),axis=1))]
tang=np.array([unit(t) for t in np.gradient(curve,axis=0)])
normal=np.array([unit(np.cross([0,0,1],t)) for t in tang]);binormal=np.cross(tang,normal)
def rigid(v,j):
    delta=v-curve[j]
    return np.column_stack([-4+arc[j]+delta@tang[j],5+delta@normal[j],delta@binormal[j]])
def flight(v,group,name):
    if group.startswith('spine_') or group=='dorsal_crest':
        # Projection onto connected segments, not closest rings, avoids banding.
        lo,hi=(max(0,int(group[-2:])*8-2),min(224,int(group[-2:])*8+10)) if group.startswith('spine_') else (0,224)
        a=curve[lo:hi];d=curve[lo+1:hi+1]-a;den=np.sum(d*d,axis=1)
        result=[]
        for start in range(0,len(v),2048):
            p=v[start:start+2048];u=np.clip(np.einsum('nsi,si->ns',p[:,None,:]-a,d)/den,0,1)
            projection=a+u[:,:,None]*d
            choice=np.argmin(np.sum((p[:,None,:]-projection)**2,axis=2),axis=1);rows=np.arange(len(p));mix=u[rows,choice];j=choice+lo
            t=tang[j]*(1-mix[:,None])+tang[j+1]*mix[:,None];t/=np.linalg.norm(t,axis=1)[:,None]
            n=np.cross(np.tile([0,0,1],(len(p),1)),t);n/=np.linalg.norm(n,axis=1)[:,None];b=np.cross(t,n)
            residual=p-projection[rows,choice];s=arc[j]+mix*(arc[j+1]-arc[j])
            result.append(np.column_stack([-4+s+np.sum(residual*t,axis=1),5+np.sum(residual*n,axis=1),np.sum(residual*b,axis=1)]))
        return np.concatenate(result)
    if group.startswith(('fore_leg','hind_leg')):
        j=int((.27 if group.startswith('fore_leg') else .62)*224)
        return v-curve[j]+np.array([-4+arc[j],5,0])
    if group=='tail':return rigid(v,216)
    return v-np.array([0,10,0])

def simplify(v,f,m,lod):
    if not lod or len(f)<4 or f[0,0]!=0 or f[0,1]!=1:return f,m
    sides=int(f[0,2])-1
    if not 6<=sides<=40:return f,m
    caps=(len(v)-2)%sides==0
    if not caps and len(v)%sides:return f,m
    rows=(len(v)-2 if caps else len(v))//sides
    if rows<3:return f,m
    rings=list(range(0,rows,2 if lod==1 else 3))
    if rings[-1]!=rows-1:rings.append(rows-1)
    cols=list(range(0,sides,2 if sides>=12 else 1));faces=[];materials=[]
    for a,b in zip(rings,rings[1:]):
        for k,c in enumerate(cols):
            e=cols[(k+1)%len(cols)];faces.extend([(a*sides+c,a*sides+e,b*sides+e),(a*sides+c,b*sides+e,b*sides+c)])
            materials.extend([m[min(len(m)-1,2*(a*sides+c))]]*2)
    if caps:
        for k,c in enumerate(cols):
            e=cols[(k+1)%len(cols)];faces.extend([(len(v)-2,e,c),(len(v)-1,(rows-1)*sides+c,(rows-1)*sides+e)]);materials.extend([m[-1]]*2)
    return np.array(faces),np.array(materials)
doc=json.loads(SOURCE.read_text(encoding='utf-8'))
flight_doc=copy.deepcopy(doc)
groups={uid:group['name'] for group in doc['outliner'] for uid in group['children']}
batch={name:[] for name in ['body','head','jaw','mane','beard','whiskers','tail','fore_leg_-1','fore_leg_1','hind_leg_-1','hind_leg_1']}
for e,editable in zip(doc['elements'],flight_doc['elements']):
    assert e['type']=='mesh'
    keys=list(e['vertices']);index={k:i for i,k in enumerate(keys)}
    v=np.array([e['vertices'][k] for k in keys],dtype=float)/16
    faces=list(e['faces'].values());f=np.array([[index[k] for k in face['vertices']] for face in faces])
    material=np.array([int(next(iter(face['uv'].values()))[0]//25) for face in faces])
    g=groups[e['uuid']]
    v=flight(v,g,e['name']);editable['vertices']={k:(point*16).round(5).tolist() for k,point in zip(keys,v)}
    n=normals(v,f);brightness=.3+.55*np.abs(n@unit(np.array([-.6,.8,1])))+.15*np.abs(n@unit(np.array([.5,.3,-1])))
    g='body' if g.startswith('spine_') or g=='dorsal_crest' else 'head' if g in ['head','face_armour','antlers'] else g
    if e['name'] in ('lower_jaw','chin') or e['name'].startswith(('lower_tooth_','lower_whisker_')):g='jaw'
    batch[g].append((e['name'],v,f,material,brightness))
pivots={'body':[0,0,0],'head':[-4,5,0],'jaw':[-5.7,4.15,0],'mane':[-4.4,5.1,0],'beard':[-6.3,3.9,0],
        'whiskers':[-7.5,4.5,0],'tail':[-4+arc[216],5,0]}
binary=bytearray(struct.pack('>IIII',0x49444D31,1,len(batch),3));counts=[0,0,0]
for group,parts in batch.items():
    name=group.encode('ascii');binary.extend(struct.pack('>H',len(name))+name)
    pivot=pivots.get(group)
    if pivot is None:
        points=np.concatenate([p[1] for p in parts]);pivot=[float(points[:,0].mean()),float(points[:,1].max()),float(points[:,2].mean())]
    binary.extend(struct.pack('>fff',*pivot))
    for lod in range(3):
        vertices=[];colors=[]
        for name,v,faces,m,b in parts:
            ids=np.arange(len(faces))
            if name.startswith('overlapping_scales_'):
                if lod==1:ids=ids[(ids//8)%2==0]
                elif lod==2:continue
            if lod==2 and ('filament' in name or 'head_scale' in name or 'facial_armour' in name):continue
            f,materials=simplify(v,faces[ids],m[ids],lod)
            shade=b[f].reshape(-1)*np.repeat(PALETTE[materials],3)
            vertices.append(v[f].reshape(-1,3));colors.append(np.uint8(np.clip(shade*255,0,255)))
        v=np.concatenate(vertices).astype('>f4');c=np.concatenate(colors);count=len(v);counts[lod]+=count
        packed=np.empty(count,dtype=[('position','>f4',(3,)),('rgba','u1',(4,))]);packed['position']=v
        packed['rgba'][:,:3]=c[:,None];packed['rgba'][:,3]=255
        binary.extend(struct.pack('>I',count));binary.extend(packed.tobytes())
TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(binary)
flight_doc['name']='ink_celestial_dragon_flight'
(ROOT/'model-source/ink-celestial-dragon/ink_celestial_dragon_flight.bbmodel').write_text(json.dumps(flight_doc,separators=(',',':')),encoding='utf-8')
points=np.concatenate([p[1] for parts in batch.values() for p in parts])
report={'sourceSHA256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'runtimeSHA256':hashlib.sha256(binary).hexdigest(),
        'groups':list(batch),'triangles_by_lod':[n//3 for n in counts],'vertices_by_lod':counts,'bytes':len(binary),
        'near_mesh_matches_approved_triangles':counts[0]//3==sum(len(e['faces']) for e in doc['elements']),
        'pose':'extended horizontal flight, not the old sculpture coil','centrelineLength':float(arc[-1]),
        'flightBounds':{'min':points.min(axis=0).tolist(),'max':points.max(axis=0).tolist()}}
assert report['near_mesh_matches_approved_triangles']
(ROOT/'model-source/ink-celestial-dragon/runtime_export.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
