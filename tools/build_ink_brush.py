"""Convert artsmia's CC0 scan to a bounded vertex-colour Minecraft mesh; preserve the editable original GLB."""
from pathlib import Path
import struct,json,io,hashlib
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1];src=root/'model-source/ink-calligraphy-brush';raw=(src/'source.glb').read_bytes()
assert hashlib.md5(raw).hexdigest()=='faa06d02f181687c1792c37b6b248bba'
n=struct.unpack_from('<I',raw,12)[0];doc=json.loads(raw[20:20+n]);blob=raw[28+n:]
def access(i):
 a=doc['accessors'][i];v=doc['bufferViews'][a['bufferView']];size={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']];dt={5126:'<f4',5125:'<u4'}[a['componentType']]
 return np.frombuffer(blob,dtype=dt,count=a['count']*size,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,size).copy()
p=doc['meshes'][0]['primitives'][0];xyz=access(p['attributes']['POSITION']);uv=access(p['attributes']['TEXCOORD_0']);faces=access(p['indices']).reshape(-1,3)
v=doc['bufferViews'][doc['images'][0]['bufferView']];im=Image.open(io.BytesIO(blob[v.get('byteOffset',0):v.get('byteOffset',0)+v['byteLength']])).convert('RGB');tex=np.asarray(im)
colors=tex[np.clip((uv[:,1]*im.height).astype(int),0,im.height-1),np.clip((uv[:,0]*im.width).astype(int),0,im.width-1)]/255.
# Raw scan's long axis is X. Choose the bushier end as the bottom by measuring its cross-section.
x=(xyz[:,0]-xyz[:,0].min())/np.ptp(xyz[:,0]);widths=[float(np.ptp(xyz[(x>lo)&(x<hi),1])) for lo,hi in ((.03,.18),(.82,.97))];flip=widths[1]>widths[0]
y=(1-x if flip else x)*8;pos=np.stack(((xyz[:,1]-np.mean(xyz[:,1]))*8/np.ptp(xyz[:,0]),y,(xyz[:,2]-np.mean(xyz[:,2]))*8/np.ptp(xyz[:,0])),axis=1)
keys=np.round(pos/.08).astype(int);_,inv=np.unique(keys,axis=0,return_inverse=True);counts=np.bincount(inv);points=np.stack([np.bincount(inv,weights=pos[:,i])/counts for i in range(3)],axis=1);rgb=np.stack([np.bincount(inv,weights=colors[:,i])/counts for i in range(3)],axis=1)
f=inv[faces];f=f[(f[:,0]!=f[:,1])&(f[:,1]!=f[:,2])&(f[:,2]!=f[:,0])];_,unique=np.unique(np.sort(f,axis=1),axis=0,return_index=True);f=f[np.sort(unique)]
# Keep the carved historical shaft; add a long, separated, ink-loaded boss nib below its scanned collar.
gray=rgb@np.array([.2126,.7152,.0722]);rgb=np.repeat((gray*.72)[:,None],3,axis=1)
points[:,1]+=1.5;extra=[];extra_rgb=[];extra_f=[];base=len(points)
for row in range(13):
 t=row/12;r=.46*(1-t)**.65+.008
 for side in range(32):
  a=side*np.pi/16;extra.append([np.cos(a)*r+.24*t*t,1.55*(1-t),np.sin(a)*r]);g=(.5*(1-t)**2+.012)*(1+.2*np.sin(side*2.3));extra_rgb.append([g]*3)
for row in range(12):
 for side in range(32):
  a=base+row*32+side;b=base+row*32+(side+1)%32;c=b+32;d=a+32;extra_f.extend([[a,b,c],[a,c,d]])
points=np.vstack([points,extra]);rgb=np.vstack([rgb,extra_rgb]);f=np.vstack([f,extra_f])
assert 500<len(f)<30000
out=root/'src/main/resources/assets/tnc/meshes/ink_brush.mesh';out.parent.mkdir(parents=True,exist_ok=True)
with out.open('wb') as stream:
 stream.write(struct.pack('>II',0x49425231,len(f)*3))
 for face in f:
  for k in face:stream.write(struct.pack('>fffBBB',*points[k],*(np.clip(rgb[k]*255,0,255).astype(int))))
stats={'source':'https://zenodo.org/records/10286824','author':'artsmia / Minneapolis Institute of Art','license':'CC0-1.0','originalTriangles':len(faces),'runtimeTriangles':len(f),'heightBlocks':9.5,'adaptation':'grayscale carved shaft, extended tapered ink-loaded nib','bottomRawXMaximum':flip,'sourceMd5Verified':True}
(src/'conversion.json').write_text(json.dumps(stats,indent=2),encoding='utf-8');print(stats)
# Inspect a faithful side view of the converted scan.
import matplotlib;matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
fig=plt.figure(figsize=(5,10),facecolor='#ddd8ce');ax=fig.add_subplot(projection='3d');ax.add_collection3d(Poly3DCollection(points[f],facecolors=rgb[f].mean(axis=1),linewidth=0));ax.set(xlim=(-1,1),ylim=(0,9.5),zlim=(-1,1));ax.set_box_aspect((2,9.5,2));ax.view_init(elev=90,azim=-90,roll=0);ax.axis('off');fig.savefig(src/'preview.png',dpi=130,bbox_inches='tight');plt.close(fig)
