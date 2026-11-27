from pathlib import Path
import argparse

import json, math, base64,uuid,io
from math import sin,cos,pi,tau,exp
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/tnc'
out=None
ids=['reef_glass_school','silverwake_school','veilbell_jelly','lantern_nudibranch']
names=['琉窗鲷群','银梭洄游群','垂帘铃水母','星灯海蛞蝓']
palettes=[
 ['18576a','428c91','eac987','ef8f66','4d314c','a1f8df','e4f9eb','172939'],
 ['394e6a','74a6b4','d0e0d5','a5bdde','41536c','9beedd','e6f6f5','182139'],
 ['5060aa','9a75b9','a4ccf4','f9d5cb','6d69ae','95fff0','fff4da','43386c'],
 ['39475d','616e87','bc7584','f1ae78','503956','90f8d5','ffd88f','242744']]
def write(path,data):
 path.parent.mkdir(exist_ok=True,parents=True);path.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def ident(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'tnc-sea-wild/'+s))
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def xyz(p):return tuple(round(x,4) for x in p)
class Art:
 def __init__(self,i):
  self.i=i;self.id=ids[i];self.parts={};self.joint('body',(0,0,0),'root')
 def joint(self,key,pivot,parent='body'):
  self.parts[key]={'hinge':[parent,*pivot],'points':[],'faces':[]};self.key=key
 def quad(self,points,mat,uv=None,lit=False,rev=False):
  points=list(map(xyz,points));uv=uv or [(0,0),(1,0),(1,1),(0,1)]
  if len(points)==3:points.append(points[-1]);uv=uv[:3]+[uv[2]]
  if rev:points=[points[2],points[1],points[0],points[-1]];uv=[uv[2],uv[1],uv[0],uv[-1]]
  if len(set(points[:3]))<3:return
  rec=self.parts[self.key];indices=[]
  for p in points:
   if p not in rec['points']:rec['points'].append(p)
   indices.append(rec['points'].index(p))
  t=[round(q,3) for x,y in uv for q in (mat%4*32+.5+31*x,mat//4*64+.5+63*y)]
  rec['faces'].append([*indices,*t,lit])
 def fishbody(self,sections,sides=8,metal=False):
  for i,((z,ry,rx,h),(zz,sy,sx,k)) in enumerate(zip(sections,sections[1:])):
   for j in range(sides):
    a=tau*j/sides;b=tau*(j+1)/sides
    pts=[(rx*cos(a),h+ry*sin(a),z),(rx*cos(b),h+ry*sin(b),z),(sx*cos(b),k+sy*sin(b),zz),(sx*cos(a),k+sy*sin(a),zz)]
    y=sin((a+b)/2)
    mat=0 if y>.67 else (1 if y>.05 else 2)
    if not metal and i in (1,3) and y<.75:mat=4
    self.quad(pts,mat,[(i/len(sections),j/sides), (i/len(sections),(j+1)/sides),((i+1)/len(sections),(j+1)/sides),((i+1)/len(sections),j/sides)])
 def plane(self,pts,tile,lit=False):
  self.quad(pts,tile,lit=lit)
 def orb(self,c,r,tile,lit=False,sides=8):
  for lat in range(4):
   aa=-pi*.49+pi*.98*lat/4;bb=-pi*.49+pi*.98*(lat+1)/4
   for j in range(sides):
    def pt(a,k):
     t=k*tau/sides
     return add(c,(r[0]*cos(a)*cos(t),r[1]*sin(a),r[2]*cos(a)*sin(t)))
    self.quad([pt(aa,j),pt(bb,j),pt(bb,j+1),pt(aa,j+1)],tile,lit=lit)
 def shell(self,layers,n,tile):
  for i,(lo,hi) in enumerate(zip(layers,layers[1:])):
   for j in range(n):
    def pt(p,k):
     y,rx,rz,w=p;theta=k*tau/n
     return (rx*cos(theta),y+w*cos(8*theta),rz*sin(theta))
    self.quad([pt(lo,j),pt(hi,j),pt(hi,j+1),pt(lo,j+1)],tile(i,j),[(j/n,i/len(layers)),(j/n,(i+1)/len(layers)),((j+1)/n,(i+1)/len(layers)),((j+1)/n,i/len(layers))])
 def fin(self,pts,tile,lit=False):
  # Two gently pitched fan sectors hold their inked ray pattern on either side.
  mid=tuple(sum(p[k] for p in pts)/len(pts) for k in range(3))
  for j,p in enumerate(pts):self.plane([mid,p,pts[(j+1)%len(pts)]],tile,lit)
 def texture(self):
  img=Image.new('RGBA',(128,128))
  for i,h in enumerate(palettes[self.i]):
   rgb=[int(h[k:k+2],16) for k in (0,2,4)]
   for x in range(32):
    for y in range(64):
     u=x/31;v=y/63
     noise=((x*17+y*31+x*y)%23-11)/220
     if self.i<2:
      tess=cos(tau*(6*u+.35*cos(12*pi*v)))
      gain=.83+.13*sin(pi*v)+noise+.08*tess*max(0,cos(8*pi*v))+.07*v
      if i in (3,4):gain+=.19*max(0,cos(tau*(5*u+1.4*v)))**7
     elif self.i==2:
      gain=.76+.23*sin(pi*v)+.12*cos(tau*(5*u+.2*sin(v*6)))+.19*max(0,cos(tau*(2*u+v)))**9+noise*.34
     else:gain=.82+.13*sin(pi*v)+noise+.14*max(0,cos(tau*(3*u+.3*sin(9*v))))**9
     alpha=(168 if i<3 else 223) if self.i==2 else 255
     img.putpixel((i%4*32+x,i//4*64+y),tuple(max(0,min(255,round(c*gain))) for c in rgb)+(alpha,))
  return img
 def export(self):
  atlas=self.texture();name='sea_'+self.id
  path=assets/'textures/entity/seawild'/(self.id+'.png');path.parent.mkdir(parents=True,exist_ok=True);atlas.save(path)
  write(assets/'pasture_meshes'/(name+'.json'),self.parts)
  t=[];groups={'root':[]};piv={'root':(0,0,0)}
  for key,part in self.parts.items():
   parent,*offset=part['hinge'];pivot=add(piv[parent],offset);piv[key]=pivot
   pts={f'v{i:x}':list(p) for i,p in enumerate(part['points'])}
   faces={}
   for i,poly in enumerate(part['faces']):
    ix=poly[:4];uv=poly[4:12];keys=[];coords={}
    for j,idx in enumerate(ix):
     k=f'v{idx:x}'
     if k not in keys:keys.append(k);coords[k]=[uv[2*j],uv[2*j+1]]
    faces[f'f{i:x}']={'vertices':keys,'uv':coords,'texture':0}
   eid=ident(name+'/mesh/'+key)
   elem=dict(type='mesh',name=key,uuid=eid,origin=pivot,vertices=pts,faces=faces)
   gid=ident(name+'/'+key);t.append(elem);groups[key]=[eid]
   groups[parent].append(dict(name=key,uuid=gid,origin=pivot,children=groups[key],isOpen=False))
  tx=dict(name=path.name,uuid=ident(name+'/tex'),id='0',source='data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode(),internal=True,mode='bitmap',width=128,height=128,uv_width=128,uv_height=128)
  if out is not None: write(out/(names[self.i]+'.bbmodel'),dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},name=names[self.i],model_identifier=self.id,resolution={'width':128,'height':128},elements=t,outliner=groups['root'],textures=[tx]))

def fish(i):
 a=Art(i);silver=i==1
 if silver: rings=[(-3.85,.24,.20,0),(-2.36,.69,.45,.02),(-.85,.94,.69,.09),(1.1,.99,.76,.13),(2.55,.66,.61,.09),(3.56,.12,.22,.04)]
 else:rings=[(-2.8,.38,.27,0),(-1.92,1.53,.61,.11),(-.85,2.13,.88,.24),(.44,2.11,1.03,.22),(1.53,1.62,.87,.13),(2.49,.67,.46,.01),(2.70,.20,.20,.08)]
 a.fishbody(rings,8,silver)
 def skin(side,z,f,raiseBy=.028):
  low,high=next((a,b) for a,b in zip(rings,rings[1:]) if a[0]<=z<=b[0])
  t=(z-low[0])/(high[0]-low[0]);_,ry,rx,h=[x+(y-x)*t for x,y in zip(low,high)]
  return (side*(rx*(max(0,1-f*f)**.5)+raiseBy),h+ry*f,z)
 for side in (-1,1):
  # Broken prismatic caustic marks follow the actual curvature of the scales.
  if silver:
   for z,w in [(-2.85,.87),(-1.87,.92),(-.87,1),(0.21,.92),(1.21,.70)]:
    a.quad([skin(side,z,.05),skin(side,z,.15),skin(side,z+w,.24),skin(side,z+w,.14)],5)
   for z in (-1.87,-1.37,-.87,-.37,.14):
    a.quad([skin(side,z,.36),skin(side,z+.10,.36),skin(side,z-.19,.66),skin(side,z-.27,.68)],2)
  else:
   for z,sz in [(-1.66,.37),(-.88,.45),(-.05,.37),(.63,.25)]:
    a.fin([skin(side,z+sz*.6,.37),skin(side,z,.55),skin(side,z-sz*.57,.38),skin(side,z,.24)],2)
    a.fin([skin(side,z+sz*.20,.38,.044),skin(side,z,.445,.044),skin(side,z-sz*.22,.38,.044),skin(side,z,.316,.044)],5)
   for z in (-1.71,-1.16,-.55,.10):
    a.plane([skin(side,z,-.42),skin(side,z+.12,-.39),skin(side,z-.06,-.19)],3)

 for side in (-1,1):
  ex=(.65 if silver else .85)*side;ez=2.03 if silver else 1.42;ey=.40 if silver else .59
  # Raised, ringed eyes. Each looks sideways on a real shoulder, rather than forward through the head.
  for r,mat,dx in [(.26 if silver else .37,6,0),(.17 if silver else .25,7,.038)]:
   a.fin([(ex+side*dx,ey+r*cos(tau*j/8),ez+r*sin(tau*j/8)) for j in range(8)],mat)
  a.plane([(side*(.56 if silver else .65),-.02,ez+.47),(side*(.85 if silver else .99),ey-.12,ez-.50),(side*(.88 if silver else 1.01),ey-.18,ez-.63)],5,True)
  a.joint('fin'+str(side),(side*(.67 if silver else .91),-.18,.48))
  a.fin([(0,.18,.22),(side*.55,-.10,-.29),(side*(1 if silver else 1.4),-.58,-1.25),(.05*side,-.43,-.48)],3)
  a.key='body'
 for side in (-1,1):
  d=1 if side==1 else -.8
  a.fin([(0,d*(.72 if silver else 1.51),.97),(0,d*(1.84 if silver else 3.72),-.85),
         (0,d*(1.13 if silver else 2.65),-1.94),(0,d*(.42 if silver else .7),-2.41)],4 if silver else 3)
 tailZ=rings[0][0];a.joint('tail',(0,0,tailZ))
 for side in (-1,1):
  a.fin([(0,0,.35),(0,side*.52,.02),(0,side*(1.69 if silver else 1.75),-1.94),
         (0,side*(.60 if silver else .82),-1.39),(0,0,-.57)],4 if silver else 3)
 # Membrane rays lie on both sides of the tail vane.
 for sgn in (-1,1):
  for direction in (-1,1):
   a.plane([(sgn*.012,direction*.23,-.08),(sgn*.012,direction*1.48,-1.71),(sgn*.012,direction*.48,-.55)],1 if silver else 2)
 a.export()
def jelly():
 a=Art(2)
 a.shell([(6.6,.10,.10,.02),(6.28,2.25,2.45,.02),(5.35,4.37,4.22,.04),(3.87,5.25,5.10,.09),(2.65,5.40,5.30,.20),(2.29,5.05,4.96,.40),(2.75,4.35,4.28,.12),(3.34,.18,.18,0)],24,lambda i,j:2 if i in (0,1) else 4 if i==5 else (1 if j%6<2 else 0))
 a.orb((0,3.32,0),(1.30,.87,1.26),3,sides=10)
 for j in range(8):
  th=tau*j/8;a.key='body'
  def ring(r,h,w):
   return (r*cos(th+w),h,r*sin(th+w))
  for r,s,h,k in [(1.5,3.1,6.51,5.97),(3.1,4.5,5.97,5.14),(4.5,5.27,5.14,3.66)]:
   a.quad([ring(r,h,-.018),ring(r,h,.018),ring(s,k,.022),ring(s,k,-.022)],5,lit=True)
  a.joint('thread'+str(j),(4.37*cos(th),2.48,4.37*sin(th)))
  length=6.2+1.15*(j%3)
  for k in range(3):
   if k:a.joint(f'thread{j}_{k}',(0,-length/3,0),f'thread{j}'+(f'_{k-1}' if k>1 else ''))
   w=(.22 if j%2 else .13)*(1-.21*k)
   a.fin([(-w/2,0,0),(w/2,0,0),(.25*cos(th)+w*.5,-length/3,0),(.25*cos(th)-w*.5,-length/3,0)],2 if j%2 else 4)
   if k==2:a.orb((.25*cos(th),-length/3,0),(.12,.23,.12),5,True,sides=4)
  if j%2==0:
   a.joint('veil'+str(j),(1.55*cos(th),3.0,1.55*sin(th)))
   for n in range(5):
    t=n/5;u=(n+1)/5
    def edge(t,s):
     r=.2+sin(8*t+th)*.49;w=(.7*(1-t)+.09)*s
     return (cos(th)*r-sin(th)*w,-6.7*t,sin(th)*r+cos(th)*w)
    a.quad([edge(t,-1),edge(t,1),edge(u,1),edge(u,-1)],1 if n%2 else 3)
 a.export()
def slug():
 a=Art(3)
 a.fishbody([(-5,.1,.09,.41),(-3.53,.48,.70,.46),(-1.94,.82,1.34,.78),(.10,1.10,1.78,1.01),(2.22,.88,1.65,.86),(3.49,.54,1.21,.58),(4.05,.20,.32,.35)],10)
 # Dorsal pearl markings trace the mantle without hiding the gill rosette.
 for side in (-1,1):
  for n in range(9):
   z=-3.15+n*.69;z=max(-3.53,min(3.39,z))
   sections=[(-3.53,.48,.70,.46),(-1.94,.82,1.34,.78),(.10,1.10,1.78,1.01),(2.22,.88,1.65,.86),(3.49,.54,1.21,.58)]
   l,r=next((l,r) for l,r in zip(sections,sections[1:]) if l[0]<=z<=r[0])
   st=(z-l[0])/(r[0]-l[0]);_,ry,rx,h=[u+(v-u)*st for u,v in zip(l,r)]
   f=.52+.09*sin(n*.86)
   c=(side*rx*f,h+ry*(1-f*f)**.5+.012,z)
   w=.12+.045*cos(n*.9)
   a.fin([add(c,(0,.017,w*1.55)),add(c,(side*w,-.045,0)),add(c,(0,.017,-w*1.55)),add(c,(-side*w,.086,0))],6 if n%3==0 else 2,n%3==0)
 for side in (-1,1):
  a.joint('skirt'+str(side),(side*.4,.69,0))
  for j in range(14):
   def edge(n,outer):
    z=-4.55+7.9*n/14;breadth=sin(pi*(n+.08)/14.16)**.65
    w=1+.7*outer+.28*outer*cos(n*1.85)
    return (side*(.05+breadth*w),.04+outer*.41*cos(n*1.85),z)
   a.quad([edge(j,0),edge(j,1),edge(j+1,1),edge(j+1,0)],2 if j%2 else 3)
   u,v=edge(j,1),edge(j+1,1)
   a.quad([u,v,add(v,(-side*.14,.035,0)),add(u,(-side*.14,.035,0))],5,lit=True)
  a.joint('horn'+str(side),(side*.76,1.27,2.31))
  for n in range(4):
   a.orb((side*n*.055,.24+.34*n,n*.07),(.24-.033*n,.30,.26-.03*n),6 if n==3 else 2,n==3,5)
 for i in range(5):
  th=tau*i/5;a.joint('gill'+str(i),(.34*cos(th),1.21,-2.14+.34*sin(th)))
  for j in range(4):
   t=j/4;u=(j+1)/4
   def edge(t,s):return (cos(th)*(.9*t)-sin(th)*s*.43*sin(pi*(t+.03)/1.06),1.63*t,sin(th)*(.9*t)+cos(th)*s*.43*sin(pi*(t+.03)/1.06))
   a.quad([edge(t,-1),edge(t,1),edge(u,1),edge(u,-1)],2 if j<3 else 6,lit=j==3)
 a.export()
if __name__=='__main__':
 parser=argparse.ArgumentParser(description='Rebuild articulated sea wildlife shapes and their 128px atlases.')
 parser.add_argument('--projects',type=Path,help='also export editable Blockbench sources into this folder')
 out=parser.parse_args().projects
 fish(0);fish(1);jelly();slug()


