"""Carved block-built lightning staves, sharing the shipped grip and material atlas."""
import math
from rebuild_sculpted_wands import Sculpt
class LightningStaff(Sculpt):
 def box(self,x,y,z,w,h,d,m=1,part='carved_block'):
  a,b=x-w/2,x+w/2;c,e=y-h/2,y+h/2;f,g=z-d/2,z+d/2
  for q in [[(a,c,f),(b,c,f),(b,e,f),(a,e,f)],[(b,c,g),(a,c,g),(a,e,g),(b,e,g)],[(a,c,g),(a,c,f),(a,e,f),(a,e,g)],[(b,c,f),(b,c,g),(b,e,g),(b,e,f)],[(a,e,f),(b,e,f),(b,e,g),(a,e,g)],[(a,c,g),(b,c,g),(b,c,f),(a,c,f)]]:self.quad(q,m,part,m==2)
 def tube(self,points,radii,material=1,sides=6,part='frame',bright=False):
  for i in range(len(points)-1):
   a,b=points[i:i+2];r=(radii[i]+radii[i+1])*.5
   self.box(*[(a[k]+b[k])/2 for k in range(3)],*[max(r*2,abs(b[k]-a[k])) for k in range(3)],material,part)
 def curve(self,points,radius,material=1,part='filigree',taper=.12,sides=5,bright=False):
  self.tube(points,[radius]*len(points),material,4,part)
 def gem(self,center,size,material=2,part='crystal',sides=6,twist=0):
  x,y,z=center;rx,ry,rz=size
  for dy,s,h in [(-.7,.45,.6),(-.2,1,.4),(.25,.8,.5),(.65,.5,.3),(.9,.23,.2)]:
   self.box(x,y+dy*ry,z,rx*2*s,ry*h,rz*2*s,material,part)
 def shaft(self):
  self.box(8,10.5,8,.8,21,.8,0,'square_wood_core')
  for y in [2,8,12,16,19.5]:
   self.box(8,y,8,1.25,.55,1.25,1,'stepped_ferrule')
   self.box(8,y+.38,8,1,.18,1,4,'ferrule_gold_lip')
  for i in range(9):self.box(8,3+i*.48,8,1.02,.25,1.02,3,'grip_segments')
  for side in [-1,1]:
   self.box(8+side*.43,13,8,.14,8,.88,1,'corner_armor')
   for i in range(6):
    self.box(8+side*(.17 if i%2 else -.17),9+i*1.5,7.54,.28,.9,.13,2,'recessed_thunder_rune')
  self.box(8,20,8,1.8,.7,1.8,1,'crown_socket')
  self.gem((8,19,7.35),(.25,.6,.15),2,'neck_inset')
 def rail(self,x,z,levels,m=1):
  for i,(y,r,h) in enumerate(levels):
   self.box(8+x*r,y,8+z*r,.65,h,.65,m,'stepped_crown_pillar')
   if i:
    old=levels[i-1][1];self.box(8+x*(r+old)/2,y-h/2,8+z*(r+old)/2,.65+abs(x*(r-old)),.35,.65+abs(z*(r-old)),m,'step_joint')
 def head(self):
  t=self.tier
  if t==1:
   for x,z in [(1,0),(-1,0),(0,1),(0,-1)]:
    self.rail(x,z,[(21,1,2),(23,1.65,2),(25,2.1,2),(27,1.65,2)],0)
   self.gem((8,24.5,8),(.75,2,.75),2,'lightning_seed')
  elif t==2:
   for x,z in [(1,1),(-1,1),(1,-1),(-1,-1)]:
    self.rail(x,z,[(21,1,1.5),(23,1.5,2.5),(25.5,1.5,2.5),(27.5,1,1.5),(28.5,.5,1)],1)
   self.gem((8,24.7,8),(.8,2.2,.8),2,'silver_heart')
  elif t==3:
   for x in [-1,1]:
    for z in [-1,1]:
     self.box(8+x*1.8,24.5,8+z*1.8,.9,7,.9,3,'obsidian_tower')
     self.box(8+x*1.8,28,8+z*1.8,1.3,.5,1.3,4,'tower_cap')
   for y in [21,27]:
    for z in [-1,1]:self.box(8,y,8+z*1.8,4.5,.65,.7,1,'cage_bridge')
    for x in [-1,1]:self.box(8+x*1.8,y,8,.7,.65,4.5,1,'cage_bridge')
   self.gem((8,24,8),(1.1,2,1.1),2,'caged_thunder')
  elif t==4:
   for z in [-.65,.65]:
    for x,y,w,h in [(0,21,3,.6),(0,28,3,.6),(-2.8,24.5,.6,3),(2.8,24.5,.6,3),(-2,21.8,1.4,.7),(2,21.8,1.4,.7),(-2,27.2,1.4,.7),(2,27.2,1.4,.7),(-2.5,22.5,.65,1.5),(2.5,22.5,.65,1.5),(-2.5,26.5,.65,1.5),(2.5,26.5,.65,1.5)]:self.box(8+x,y,8+z,w,h,.55,1,'octagonal_storm_frame')
   self.gem((8,24.5,8),(.8,2,.8),2,'storm_axis')
   for x in [-1,1]:self.gem((8+x*3.1,24.5,8),(.45,.85,.45),2,'side_crystal')
  else:
   self.box(8,22,8,5,.8,3,1,'crown_foundation')
   self.box(8,22.6,8,4.4,.35,2.6,4,'crown_recess')
   for x,h in [(-2,4),(-1,2.8),(0,5.5),(1,2.8),(2,4)]:
    for z in [-1,1]:
     self.box(8+x,23+h/2,8+z,.6,h,.65,1,'crown_merlon')
     self.gem((8+x,23+h,8+z),(.32,.75,.32),2,'crown_tip')
   self.gem((8,25,8),(.8,3,.65),2,'royal_core')
  # Recessed seams and raised studs make the broad blocks read as carved metal.
  for y in [8,12,16,19.5]:
   self.box(8,y,7.33,.62,.24,.10,3,'collar_recess')
   for x in [-.42,.42]:self.box(8+x,y,7.30,.13,.13,.13,4,'collar_rivet')
  for x in [-1,1]:
   self.box(8+x*.46,15,7.5,.12,4,.14,4,'fine_corner_trim')
  # Inset plaques and relief studs join the crown to the shaft.
  for z in [-1,1]:
   self.box(8,21,8+z,.9,1,.18,3,'recessed_plaque')
   self.box(8,21,8+z*1.12,.35,.65,.12,2,'plaque_inlay')
if __name__=='__main__':
 for t in range(1,6):
  s=LightningStaff('lightning',t);s.colors=['#6b5060','#b8c5df','#879fff','#252a43','#c7a66c','#f0f6ff'];print(s.save())
  from lightning_staff_materials import build
  build(t)
