"""Authored black lacquer / ivory calligraphy boss. No downloaded scan geometry in this version."""
import math,struct,json
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parents[1];pts=[];rgb=[];faces=[]
def vertex(p,c):pts.append(p);rgb.append([max(0,min(1,x)) for x in c]);return len(pts)-1
def lathe(profile,color,segments=64):
 base=len(pts)
 for row,(y,r) in enumerate(profile):
  for i in range(segments):
   a=i*2*math.pi/segments;grain=.5+.5*math.sin(i*7.3+row*.13);c=color(y,a,grain)
   vertex((math.cos(a)*r,y,math.sin(a)*r),c)
 for row in range(len(profile)-1):
  for i in range(segments):
   a=base+row*segments+i;b=base+row*segments+(i+1)%segments;c=b+segments;d=a+segments;faces.extend([(a,b,c),(a,c,d)])
def tube(path,r,color,sides=8):
 base=len(pts)
 for i,p in enumerate(path):
  tangent=np.array(path[min(i+1,len(path)-1)])-np.array(path[max(0,i-1)]);tangent/=max(1e-8,np.linalg.norm(tangent));u=np.cross(tangent,[0,1,0]);u=u/np.linalg.norm(u) if np.linalg.norm(u)>1e-8 else np.array([1.,0,0]);v=np.cross(tangent,u)
  for j in range(sides):vertex(np.array(p)+r*(u*math.cos(j*2*math.pi/sides)+v*math.sin(j*2*math.pi/sides)),color)
 for i in range(len(path)-1):
  for j in range(sides):a=base+i*sides+j;b=base+i*sides+(j+1)%sides;faces.extend([(a,b,b+sides),(a,b+sides,a+sides)])
def black(y,a,g):
 v=.018+.018*g+.009*(.5+.5*math.sin(a*3));return (v*.9,v*.95,v)
def ivory(y,a,g):v=.68+.14*(.5+.5*math.cos(a*3))+.035*g;return(v*.98,v,v)
# Elegant narrow shaft with a rounded pommel and a slightly fuller neck.
lathe([(2.08,.31),(2.25,.30),(2.5,.275),(3,.255),(4,.24),(5,.225),(6,.215),(7,.205),(8,.195),(8.7,.19),(8.87,.18),(8.94,.12),(8.96,0)],black)
lathe([(1.90,.28),(1.96,.36),(2.01,.38),(2.10,.38),(2.15,.34),(2.20,.32)],ivory)
for y in [2.01,2.11,2.30,8.57,8.67]:
 r=.333 if y<2.4 else .205
 lathe([(y-.014,r),(y,r+.022),(y+.014,r)],ivory,48)
# Long wolf-hair nib: broad ivory root, separated tapering fibres, wet jet-black tip.
def bristle(y,a,g):
 t=max(0,min(1,y/1.95));v=.012+.69*t**3;v*=.8+.2*g;return(v*.97,v*.985,v)
lathe([(0,.007),(.15,.065),(.4,.17),(.7,.26),(1,.33),(1.3,.36),(1.6,.34),(1.9,.29),(1.98,.28)],bristle,64)
for k in range(36):
 a=k*2*math.pi/36;path=[]
 for j in range(14):
  y=.10+j*1.82/13;r=np.interp(y,[0,.4,1,1.3,1.9],[.008,.17,.33,.365,.295])+.005
  angle=a+.045*math.sin(y*4+k);path.append((math.cos(angle)*r,y,math.sin(angle)*r))
 tube(path,.0045,(.11,.115,.12),4)
# Fine silver running-water inlay: ornament, no text, no broad flat colour panels.
for side in range(3):
 path=[]
 for j in range(72):
  y=2.65+j*4.9/71;a=side*2*math.pi/3+.25*math.sin(y*1.5);r=np.interp(y,[2.5,8.7],[.278,.195])+.004
  path.append((math.cos(a)*r,y,math.sin(a)*r))
 tube(path,.008,(.42,.45,.48),5)
# Hanging loop and paired silk cords at the end cap.
tube([(.105*math.cos(t),9.10+.18*math.sin(t),0) for t in np.linspace(0,2*math.pi,33)],.024,(.24,.27,.29),8)
for s in [-1,1]:
 tube([(s*.07+math.sin(t*5)*.025,8.98-t*.8,.13+math.sin(t*3)*.04) for t in np.linspace(0,1,20)],.015,(.49,.5,.52),6)
out=root/'src/main/resources/assets/tnc/meshes/ink_brush.mesh'
with out.open('wb') as stream:
 stream.write(struct.pack('>II',0x49425231,len(faces)*3))
 for f in faces:
  for i in f:stream.write(struct.pack('>fffBBB',*pts[i],*(np.array(rgb[i])*255).astype(int)))
src=root/'model-source/ink-calligraphy-brush';stats={'design':'authored black lacquer, silver water inlay, ivory collar, long ink-loaded nib','runtimeTriangles':len(faces),'heightBlocks':9.3,'usesDownloadedScanGeometry':False};(src/'black-brush.json').write_text(json.dumps(stats,indent=2));print(stats)
import matplotlib;matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
p=np.array(pts);f=np.array(faces);colors=np.array(rgb)[f].mean(axis=1)
# Editable, portable preview source, using the exact runtime geometry and colours.
chunks=[p.astype('<f4').tobytes(),np.column_stack([rgb,np.ones(len(rgb))]).astype('<f4').tobytes(),f.astype('<u4').tobytes()];offset=0;views=[]
for chunk in chunks:views.append({'buffer':0,'byteOffset':offset,'byteLength':len(chunk)});offset+=len(chunk)
gltf={'asset':{'version':'2.0','generator':'TN-C authored black ink brush'},'buffers':[{'byteLength':offset}],'bufferViews':views,'accessors':[{'bufferView':0,'componentType':5126,'count':len(p),'type':'VEC3','min':p.min(axis=0).tolist(),'max':p.max(axis=0).tolist()},{'bufferView':1,'componentType':5126,'count':len(p),'type':'VEC4'},{'bufferView':2,'componentType':5125,'count':len(f)*3,'type':'SCALAR'}],'materials':[{'doubleSided':True,'pbrMetallicRoughness':{'metallicFactor':.15,'roughnessFactor':.42}}],'meshes':[{'primitives':[{'attributes':{'POSITION':0,'COLOR_0':1},'indices':2,'material':0}]}],'nodes':[{'mesh':0}],'scenes':[{'nodes':[0]}],'scene':0}
j=json.dumps(gltf,separators=(',',':')).encode();j+=b' '*((-len(j))%4);binary=b''.join(chunks);binary+=b'\0'*((-len(binary))%4)
(src/'black_ink_brush.glb').write_bytes(struct.pack('<4sII','glTF'.encode(),2,28+len(j)+len(binary))+struct.pack('<I4s',len(j),b'JSON')+j+struct.pack('<I4s',len(binary),b'BIN\0')+binary)
# Soft directional modelling light, preserving the black lacquer.
n=np.cross(p[f[:,1]]-p[f[:,0]],p[f[:,2]]-p[f[:,0]]);n/=np.maximum(1e-8,np.linalg.norm(n,axis=1))[:,None];shade=.55+.45*np.maximum(0,n@np.array([.5,.2,.84]));colors=np.clip(colors*shade[:,None],0,1)
fig=plt.figure(figsize=(6,11),facecolor='#d8d5cc');ax=fig.add_subplot(projection='3d',facecolor='#d8d5cc');ax.add_collection3d(Poly3DCollection(p[f],facecolors=colors,linewidth=0));ax.set(xlim=(-.8,.8),ylim=(-.1,9.5),zlim=(-.8,.8));ax.set_box_aspect((1.6,9.6,1.6));ax.view_init(elev=86,azim=-90,roll=-8);ax.axis('off');fig.savefig(src/'black-brush-preview.png',dpi=160,bbox_inches='tight');plt.close(fig)
