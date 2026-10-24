"""Authored low-poly fantasy bestiary. Every cube has an independent, non-overlapping UV island.
Outputs the actual Java-model meshes, role-specific atlases and editable Blockbench originals.
No downloads or repackaged reference art. Run after build_dungeon_assets if regenerating legacy assets.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import json,math,re,random,uuid,io,base64
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc';MODELS={}
def cube(at,size,mat='skin'):return dict(at=list(at),size=list(size),mat=mat)
def part(p,name,pos,boxes=(),parent='root',rot=(0,0,0)):
 p.append(dict(name=name,parent=parent,pos=list(pos),rot=list(rot),boxes=list(boxes)));return p[-1]
def c(x,y,z,w,h,d,mat='skin'):return cube((x,y,z),(w,h,d),mat)
def facial(p,family,head='head'):
 """Recessed sockets, inset irises, jutting brow, nostrils and separate teeth read in silhouette."""
 orc=family=='orc';lizard=family=='lizardfolk';demon=family=='demon'
 width=5 if orc else 4;front=-5 if not lizard else -4
 part(p,'jaw',(0,1.5,front+.7),[c(-width+1,0,-2,width*2-2,3,3,'belly'),c(-width+1,.5,-2.2,width*2-2,.7,.35,'mouth')],head)
 for sign in [-1,1]:
  x=sign*(2.4 if orc else 2.1)
  part(p,'socket_'+str(sign),(x,-2,front),[c(-1.65,-1.05,-.2,3.3,2,.65,'socket'),c(-1.1,-.65,-.45,2.2,1.1,.3,'iris'),c(-.2,-.72,-.64,.5,1.25,.15,'pupil'),c(-.6,-.6,-.7,.35,.3,.1,'glint')],head)
  part(p,'brow_'+str(sign),(x,-3,front-.1),[c(-1.9,-.35,-.5,3.8,1.2,1,'skin_dark')],head,(0,0,-sign*.19))
  part(p,'tusk_'+str(sign),(sign*(width-1),3,front-1.2),[c(-.5,-3,-.55,1,3,1,'bone'),c(-.32,-4,-.45,.65,1.6,.7,'bone')],head,(0,0,sign*.2))
  part(p,'ear_'+str(sign),(sign*width,-2,0),[c(0 if sign>0 else -3,-1,-1,3,2.5,1.5,'skin'),c(2 if sign>0 else -5,-.7,-.8,3,1.6,1,'skin'),c(4.5 if sign>0 else -6,-.4,-.7,1.5,.7,.7,'skin_dark'),c(.5 if sign>0 else -3,-.5,-1.12,2.5,1,.15,'ear')],head,(0,sign*.1,-sign*.23))
  if sign==1:part(p,'earring',(sign*(width+3),0,-.3),[c(-.6,0,0,1.2,2,.5,'gold'),c(-.2,.4,-.1,.4,1,.7,'socket')],head)
 if not lizard:
  part(p,'nose',(0,-1,front),[c(-1,-1,-1.8,2,3,2,'skin'),c(-1.65,1,-2.7,3.3,1.5,2,'skin_dark'),c(-1.3,1.3,-2.85,.8,.5,.2,'socket'),c(.5,1.3,-2.85,.8,.5,.2,'socket')],head)
 else:
  part(p,'snout',(0,0,front),[c(-3,-1,-4,6,3,4,'skin'),c(-2.3,1.8,-4,4.6,1.2,4,'belly'),c(-2.8,1.4,-4.12,5.6,.4,.2,'mouth'),c(-2,-.5,-4.15,.8,.5,.2,'socket'),c(1.2,-.5,-4.15,.8,.5,.2,'socket')],head)
 for x in [-2,-.8,.8,2]:part(p,'tooth_'+str(x),(x,2,front-1.6),[c(-.3,0,-.4,.6,.8,.5,'bone')],head)

def weapons(p,family):
 arm='arm_r';left='arm_l'
 # Distinctive hooked knife, brutal cleaver, spear, and split rune staff.
 if family=='lizardfolk': blade=[c(-.55,-20,-1,1.1,27,1.1,'wood'),c(-1.4,-26,-1.5,2.8,7,1,'steel'),c(-.65,-29,-1.5,1.3,3,1,'steel')]
 elif family=='orc':blade=[c(-.7,-9,-1,1.4,18,1.4,'wood'),c(-5,-12,-1.5,9,7,2,'steel'),c(-7,-10,-1.5,3,4,2,'steel_dark'),c(3,-11,-1.5,3,3,2,'steel_dark'),c(-1,-4,-1.4,2,4,2,'leather')]
 else:blade=[c(-.65,0,-1.2,1.3,4,1.5,'leather'),c(-2.3,-.8,-1.7,4.6,1,2,'gold'),c(-1.1,-9,-1.3,2.2,8,1,'steel'),c(-.2,-12,-1.3,2.1,3.5,1,'steel'),c(1,-13.5,-1.3,1.8,2,1,'steel')]
 part(p,'weapon_blade',(0,9,-.5),blade,arm,(-.15,0,0))
 part(p,'weapon_staff',(0,9,-.5),[c(-.6,-20,-.7,1.2,27,1.4,'wood'),c(-1,-8,-1,2,3,2,'leather'),c(-2.8,-23,-1,1.3,5,1.6,'wood'),c(1.5,-23,-1,1.3,5,1.6,'wood'),c(-2,-24,-1.5,4,4,3,'crystal'),c(-.8,-26,-1,1.6,2,2,'crystal'),c(-3.3,-18,-.5,.8,4,.8,'bone')],arm)
 part(p,'weapon_shield',(0,6,-3),[c(-4.8,-5,-2,9.6,11,2,'wood'),c(-5.2,-5.4,-2.4,10.4,1,2.6,'steel'),c(-5.2,5.3,-2.4,10.4,1,2.6,'steel'),c(-5.2,-5,-2.4,1,11,2.6,'steel'),c(4.2,-5,-2.4,1,11,2.6,'steel'),c(-1.5,-1.5,-3.4,3,3,2,'gold')],left,(0,0,.1))
 part(p,'weapon_bow',(0,7,-2),[c(-.5,-3,-.5,1,6,1,'leather'),c(-.6,-8,-1.5,1.2,5,1.2,'wood'),c(-.6,3,-1.5,1.2,5,1.2,'wood'),c(-.6,-11,-3,1.2,3,1.2,'wood'),c(-.6,8,-3,1.2,3,1.2,'wood'),c(-.15,-10,-2.5,.3,20,.3,'rope')],left,(-.15,0,0))

def humanoid(family):
 p=[];gob=family=='goblin';orc=family=='orc';dem=family=='demon';liz=family=='lizardfolk'
 torso=6.4 if orc else 4;hip=12 if gob else 7;heady=-6 if gob else -8
 part(p,'body',(0,hip,1),[c(-torso,-7,-3.5,torso*2,9,7,'skin'),c(-torso+.8,-5.5,-3.6,torso*2-1.6,6,.5,'belly'),c(-torso-.2,1,-3.8,torso*2+.4,1.5,7.6,'leather'),c(-1,1,-4.4,2,1.5,.7,'gold')],rot=(.14 if gob else .05,0,0))
 part(p,'head',(0,heady,-2.5),[c(-5 if orc else -4,-6,-4,10 if orc else 8,8,7,'skin'),c(-3.2,-6.5,-2,6.4,1.5,4.5,'skin_dark')],'body')
 facial(p,family)
 # Small shoulder asymmetries and separately modelled fingers, knee guards, ragged kilt.
 for sign in [-1,1]:
  label='r' if sign<0 else 'l';sx=sign*(torso+1.3)
  part(p,'arm_'+label,(sx,hip-5,1),[c(-1.7,-1,-2,3.4,7,4,'skin'),c(-2,4,-2.25,4,1.5,4.5,'leather'),c(-2,6,-2,4,4,4,'skin_dark')],rot=(.12,0,-sign*.13))
  for finger in range(3):part(p,'finger_'+label+str(finger),(-1.5+finger*1.2,9,-.8),[c(-.45,0,-1,.9,2,1.5,'skin'),c(-.35,1.7,-1,.7,.7,.8,'claw')],'arm_'+label,(-.18,0,0))
  part(p,'thumb_'+label,(sign*2,7,-1),[c(-.6,0,-1,1.2,2.8,1.5,'skin')],'arm_'+label,(0,0,sign*.5))
  ly=16 if gob else 13;leglen=7 if gob else 10
  part(p,'leg_'+label,(sign*2.5,ly,1),[c(-1.6,0,-1.8,3.2,leglen,3.6,'skin'),c(-1.9,3,-2.1,3.8,2,4,'leather'),c(-2,leglen-1,-4,4,2,6,'skin_dark')],rot=(0,sign*.08,sign*.06))
  for toe in range(3):part(p,'toe_'+label+str(toe),(-1.3+toe*1.25,leglen,-4),[c(-.4,-.2,-1,.8,.8,1.4,'claw')],'leg_'+label)
  part(p,'skirt_'+label,(sign*2,hip+3,-.3),[c(-2,-1,-3.9,4,4,1,'cloth'),c(-1.5,2,-4,3,1.8,.8,'cloth'),c(-2,-1,3,4,4,.8,'cloth')],rot=(0,sign*.1,sign*.1))
 part(p,'strap',(0,hip-4,-2.8),[c(-.8,-4,-1,1.6,9,.75,'leather'),c(-1,-.5,-1.15,2,1.5,.4,'gold')],rot=(0,0,-.65))
 part(p,'pouch',(4,hip+3,1),[c(-1.5,0,-1.8,3,3,3,'leather'),c(-1.6,0,-2,3.2,.8,3.3,'leather_dark'),c(-.35,1,-2.2,.7,.8,.3,'gold')])
 part(p,'armor_tank',(0,-5,0),[c(-torso-1,-2,-4.1,torso*2+2,4,8,'steel_dark'),c(-torso-1,1,-4.3,torso*2+2,1,1,'gold'),c(-1,-1,-4.8,2,3,1,'steel')],'body')
 part(p,'garb_caster',(0,2,0),[c(-4.2,0,-4,8.4,7,8,'cloth'),c(-4.5,6,-4.2,9,1,8.4,'gold'),c(-.5,0,-4.25,1,7,.4,'rune')],'body')
 part(p,'quiver_archer',(3,0,4),[c(-1.5,-3,0,3,9,3,'leather'),c(-1,-6,1,.6,7,.6,'wood'),c(.6,-7,1.2,.6,8,.6,'wood'),c(-1.2,-6.5,.7,1.1,1.5,.6,'bone'),c(.4,-7.5,.8,1.1,1.5,.6,'bone')],'body',(.2,0,.2))
 if orc:
  part(p,'mane',(0,-3,3),[c(-3,-5,-1,6,12,3,'hair'),c(-1.8,6,0,3.6,4,2,'hair'),c(-2,5,-.2,4,1,3,'gold')],'head')
  for sign in [-1,1]:
   part(p,'pauldron_'+str(sign),(sign*7,-5,0),[c(-3,-3,-3.5,6,3,7,'steel_dark'),c(-3.3,-1,-3.8,6.6,1,7.6,'gold'),c(-2,-5,-1,1,3,1,'bone'),c(1,-6,1,1,4,1,'bone')],'body',(0,0,-sign*.18))
  for i in range(5):part(p,'chain_'+str(i),(3,-1+i*1.3,-4),[c(-.6,0,0,1.2,1,.45,'steel')],'body',(0,0,(-1 if i%2 else 1)*.4))
 if liz:
  for i in range(5):part(p,'crest_'+str(i),(0,-6.5+i*.4,-3+i*1.2),[c(-.4,-2,0,.8,2.5,1.5,'fin')],'head')
  for i in range(4):part(p,'backscale_'+str(i),(0,-6+i*2,4),[c(-1,-1,0,2,2,1.5,'fin')],'body')
  part(p,'tail',(0,hip+3,3),[c(-2,-1,0,4,3,7,'skin')],rot=(.15,0,0))
  part(p,'tail_tip',(0,0,6),[c(-1.4,-.6,0,2.8,2,7,'skin'),c(-.7,-.3,6,1.4,1,4,'fin')],'tail',(-.12,.2,0))
 if dem:
  for sign in [-1,1]:
   part(p,'horn_'+str(sign),(sign*3,-5,1),[c(-.8,-4,-.8,1.6,4,1.6,'bone'),c(-.55,-7,-.55,1.1,3,1.1,'bone'),c(-.3,-9,-.3,.6,2,.6,'claw')],'head',(-.3,0,sign*.35))
   label='l' if sign>0 else 'r';part(p,'wing_'+label,(sign*3,hip-7,4),[c(0 if sign>0 else -8,-2,0,8,2,1.5,'bone'),c(1 if sign>0 else -9,0,.3,8,6,.6,'membrane'),c(3 if sign>0 else -10,6,.3,7,3,.6,'membrane'),c(8 if sign>0 else -11,-1,0,3,12,1,'bone')],rot=(.1,-sign*.35,sign*.18))
  part(p,'tail',(0,hip+3,4),[c(-.6,0,0,1.2,1.2,8,'skin'),c(-2,-.5,7,4,2,3,'fin')],rot=(-.2,0,0))
 if gob:part(p,'chin',(0,4,-4),[c(-1.8,0,-1,3.6,1.6,2,'skin_dark')],'head')
 weapons(p,family);return p

for family in ['goblin','orc','lizardfolk','demon']:MODELS[family]=humanoid(family)

def skeleton():
 p=[];part(p,'body',(0,5,1),[c(-1,-5,-1,2,12,2,'bone'),c(-3,5,-2,6,3,4,'bone_dark')])
 for i in range(4):
  for sign in [-1,1]:part(p,'rib_'+str(sign)+'_'+str(i),(sign, -3+i*2,-1),[c(0 if sign>0 else -3,0,-2,3,1,3,'bone'),c(2 if sign>0 else -3,0,0,1,1,3,'bone')],'body',(0,0,sign*.05))
 part(p,'head',(0,-5,0),[c(-3.8,-6,-3.5,7.6,5.5,6.5,'bone'),c(-3,-.5,-3,6,2,5,'bone_dark'),c(-2.9,-4.2,-3.7,2.2,1.9,.4,'socket'),c(.7,-4.2,-3.7,2.2,1.9,.4,'socket'),c(-.6,-2.8,-3.8,1.2,1.7,.5,'socket')],'body')
 for x in [-2.3,-1.2,0,1.2,2.3]:part(p,'tooth_'+str(x),(x,0,-3.5),[c(-.35,-.7,-.4,.7,1,.8,'bone')],'head')
 for sign in [-1,1]:
  label='r' if sign<0 else 'l';part(p,'arm_'+label,(sign*5,2,1),[c(-.8,0,-.8,1.6,7,1.6,'bone'),c(-1.1,6,-1.1,2.2,2,2.2,'bone_dark'),c(-.65,8,-.8,1.3,4,1.6,'bone')],rot=(.1,0,sign*.07))
  for x in [-.8,0,.8]:part(p,'finger_'+label+str(x),(x,11,-1),[c(-.25,0,0,.5,2,.7,'bone')],'arm_'+label)
  part(p,'leg_'+label,(sign*2.5,12,1),[c(-.9,0,-.9,1.8,5,1.8,'bone'),c(-1.2,4,-1.2,2.4,2,2.4,'bone_dark'),c(-.8,6,-.8,1.6,5,1.6,'bone'),c(-1.2,11,-3,2.4,1,4,'bone')])
 part(p,'armor_tank',(0,-3,0),[c(-5,-2,-4,10,4,7,'steel_dark'),c(-5,1,-4.2,10,1,1,'gold')],'body')
 part(p,'garb_caster',(0,3,0),[c(-3.5,0,-2.4,7,6,5,'cloth'),c(-.5,0,-2.6,1,6,.4,'rune')],'body')
 part(p,'quiver_archer',(2,0,2),[c(-1,-3,0,2,9,3,'leather')],'body');weapons(p,'skeleton');return p
MODELS['skeleton']=skeleton()

def toad():
 p=[];part(p,'body',(0,18,2),[c(-7,-5,-6,14,8,13,'skin'),c(-6,-2,-6.3,12,5,1,'belly')])
 part(p,'head',(0,-1,-5),[c(-6,-4,-4,12,6,7,'skin'),c(-5.8,1,-4.2,11.6,1,.5,'mouth'),c(-5,2,-4,10,1,5,'belly')],'body')
 for sign in [-1,1]:
  label='r' if sign<0 else 'l';part(p,'eye_'+label,(sign*4,-4,-2),[c(-2,-2,-1,4,3,3,'skin_dark'),c(-1.5,-1.5,-1.3,3,2.2,.5,'iris'),c(-1.2,-.65,-1.65,2.4,.5,.2,'pupil')],'head')
  part(p,'leg_'+label,(sign*6,18,5),[c(-3,-2,-3,6,6,7,'skin'),c(-2,3,-3,4,2,4,'belly')],rot=(0,sign*.22,0))
  for j in range(3):part(p,'toe_'+label+str(j),(-2+j*1.7,5,-3),[c(-.6,-.4,-3,1.2,.8,4,'belly')],'leg_'+label,(0,(j-1)*.22,0))
  part(p,'arm_'+label,(sign*6,18,-5),[c(-1.3,-1,-1.5,2.6,5,3,'skin'),c(-2,4,-3,4,1,4,'belly')],rot=(.1,0,-sign*.2))
 for i,(x,z) in enumerate([(-4,0),(3,1),(0,3),(-3,5),(5,4),(-5,3),(1,-2)]):part(p,'wart_'+str(i),(x,-5,z),[c(-.8,-.8,-.8,1.6,1.2,1.6,'skin_dark')],'body')
 part(p,'variant_toad_coral',(0,-4,3),[c(-4,-5,0,1,6,1,'fin'),c(-5,-4,0,3,1,1,'fin'),c(3,-4,2,1,5,1,'fin'),c(2,-3,2,3,1,1,'fin')],'body');return p
MODELS['toad']=toad()

def lizard():
 p=[];part(p,'body',(0,17,2),[c(-5,-3,-7,10,6,15,'skin'),c(-4,2,-6,8,1,13,'belly')])
 part(p,'head',(0,-1,-7),[c(-4,-2,-7,8,4,8,'skin'),c(-3,1.5,-8,6,1.5,8,'belly'),c(-3,-.5,-8.2,6,1,.4,'mouth')],'body')
 for sign in [-1,1]:
  part(p,'eye_'+str(sign),(sign*3,-1,-4),[c(-.8,-.8,-1,1.6,1.5,1.3,'iris'),c(-.2,-.8,-1.2,.4,1.5,.2,'pupil')],'head')
  for i in range(2):
   idx=(0 if sign<0 else 2)+i;part(p,'leg_'+str(idx),(sign*4,18,-3+i*10),[c(-2,-1,-2,4,3,4,'skin'),c(sign,1,-2,2,4,3,'skin_dark'),c(sign-.5,5,-3,3,1,5,'claw')],rot=(0,sign*.3,sign*.2))
 for i in range(6):part(p,'spine_'+str(i),(0,-3,-5+i*2.2),[c(-.7,-2,0,1.4,2.8,2,'fin')],'body',(0,0,.08*(-1 if i%2 else 1)))
 part(p,'tail',(0,17,9),[c(-3,-1.8,0,6,3.6,7,'skin')],rot=(-.1,0,0));part(p,'tail_tip',(0,0,6),[c(-2,-1.2,0,4,2.4,7,'skin')],'tail',(0,.15,0));part(p,'tail_end',(0,0,6),[c(-1,-.6,0,2,1.2,6,'fin')],'tail_tip',(0,.2,0));return p
MODELS['lizard']=lizard()

def tentacle():
 p=[];part(p,'body',(0,15,0),[c(-5,-5,-4,10,8,9,'skin'),c(-3.8,-7,-3,7.6,3,7,'skin_dark'),c(-3,2,-3,6,3,6,'mouth')])
 part(p,'head',(0,0,-3),[c(-3.8,-1,-2,7.6,3,2,'skin')],'body')
 for sign in [-1,1]:part(p,'eye_'+str(sign),(sign*3,-1,-1.8),[c(-1.4,-.8,-.5,2.8,1.7,1,'iris'),c(-1,-.15,-1,2,.4,.15,'pupil')],'head')
 for i in range(8):
  angle=i*math.pi/4
  part(p,'tentacle_'+str(i),(math.sin(angle)*3,18,math.cos(angle)*3),[c(-1.5,-1.5,0,3,3,5,'skin')],rot=(-.2,angle,0))
  parent='tentacle_'+str(i)
  for seg,(w,length) in enumerate([(2.4,5),(1.7,4),(.9,3)]):
   name='tip_'+str(i)+'_'+str(seg);part(p,name,(0,0,4.5 if seg==0 else 4),[c(-w/2,-w/2,0,w,w,length,'skin')],parent,(-.22 if seg<2 else -.5,(-1 if i%2 else 1)*.23,0))
   for j in range(2):
    for sign in [-1,1]:part(p,'sucker_'+str(i)+'_'+str(seg)+'_'+str(j)+'_'+str(sign),(sign*w*.25,w*.45,1+j*1.6),[c(-.35,0,-.35,.7,.55,.7,'sucker'),c(-.15,.5,-.15,.3,.12,.3,'mouth')],name)
   parent=name
 return p
MODELS['tentacle']=tentacle()

def slime():
 p=[];part(p,'body',(0,17,0),[c(-6,-6,-5,12,11,10,'gel'),c(-4,-8,-4,8,2,8,'gel'),c(-7,3,-6,14,4,12,'gel'),c(-2,-3,-2,4,5,4,'crystal')])
 for sign in [-1,1]:part(p,'eye_'+str(sign),(sign*2.2,-1,-5.2),[c(-.9,-1,-.3,1.8,2,.6,'pupil'),c(-.6,-.8,-.7,.5,.6,.3,'glint')],'body')
 part(p,'mouth',(0,1.2,-5.3),[c(-1.1,0,0,2.2,.6,.3,'mouth')],'body')
 for variant in ['azure','ember','venom','prism']:
  boxes={'azure':[c(-4,-9,0,2,2,2,'glint'),c(2,-10,1,3,3,3,'gel')],'ember':[c(-2,-10,0,4,4,2,'fin'),c(-1,-12,0,2,2,2,'fin')],'venom':[c(2,-10,0,1,4,1,'bone'),c(0,-11,-1,5,1.5,4,'fin')],'prism':[c(-3,-11,-1,2,5,2,'crystal'),c(1,-13,0,2,7,2,'crystal')]}[variant]
  part(p,'variant_slime_'+variant,(0,0,0),boxes,'body')
 return p
MODELS['slime']=slime()

def mimic():
 p=[];part(p,'body',(0,23,0),[c(-7,-8,-5,14,8,10,'wood'),c(-7.3,-8,-5.2,1,8,10.4,'steel_dark'),c(6.3,-8,-5.2,1,8,10.4,'steel_dark'),c(-7,-1,-5.3,14,1,10.6,'steel_dark'),c(-6.3,-8,-4.5,12.6,1,9,'mouth')])
 part(p,'lid',(0,15,5),[c(-7,-4,-10,14,4,10,'wood'),c(-7.3,-4,-10.2,1,4,10.4,'steel_dark'),c(6.3,-4,-10.2,1,4,10.4,'steel_dark'),c(-1.2,-1.5,-10.5,2.4,3,1,'gold')])
 for i,x in enumerate([-5,-3,-1,1,3,5]):
  part(p,'upper_tooth_'+str(i),(x,0,-9),[c(-.5,-.5,-.5,1,2.5 if i%2 else 1.5,1,'bone')],'lid',(0,0,.1*(-1 if i%2 else 1)))
  part(p,'lower_tooth_'+str(i),(x,-8,-4),[c(-.4,-1.5,-.4,.8,2,.8,'bone')],'body')
 part(p,'tongue',(0,17,-4),[c(-2.5,0,-6,5,1.5,7,'mouth')]);part(p,'tongue_tip',(0,0,-5),[c(-2,0,-5,4,1,5,'ear')],'tongue',(.3,.2,0))
 for sign in [-1,1]:
  part(p,'eye_'+str(sign),(sign*4,16,-5.2),[c(-1,-1,-.3,2,1.5,.6,'iris'),c(-.2,-1,-.7,.4,1.5,.2,'pupil')])
  part(p,'leg_'+('r' if sign<0 else 'l'),(sign*5,22,2),[c(-1.2,0,-2,2.4,2,4,'skin_dark'),c(-1.6,1,-3,3.2,1,3,'claw')])
 return p
MODELS['mimic']=mimic()

def rgb(h):return ((h>>16)&255,(h>>8)&255,h&255)
def tint(c,f):return tuple(max(0,min(255,round(v*f))) for v in c)
def palette(fam,skin,accent):
 v=dict(skin=rgb(skin),skin_dark=tint(rgb(skin),.65),belly=tint(rgb(skin),1.2),ear=(128,90,71),socket=(31,35,24),pupil=(15,20,18),iris=(225,173,53),glint=(248,241,210),mouth=(73,30,39),bone=(210,194,145),bone_dark=(136,122,88),claw=(66,58,47),leather=(104,63,36),leather_dark=(59,37,26),cloth=rgb(accent),rune=tint(rgb(accent),1.6),gold=(173,133,68),steel=(151,162,162),steel_dark=(59,72,78),wood=(110,73,42),rope=(175,153,103),hair=(33,37,30),fin=rgb(accent),membrane=(91,43,55),crystal=rgb(accent),sucker=(193,160,141),gel=rgb(skin))
 if fam in ['tentacle','lizard','lizardfolk','toad']:v['belly']=(166,167,118);v['iris']=(232,191,93)
 if fam=='skeleton':v['iris']=rgb(accent)
 return v
def atlas(parts,fam,skin,accent,ident):
 im=Image.new('RGBA',(256,256));d=ImageDraw.Draw(im);pal=palette(fam,skin,accent);x=y=row=0;rs=random.Random(ident)
 for part_ in parts:
  for b in part_['boxes']:
   w,h,depth=[math.ceil(n) for n in b['size']];tw=2*(w+depth);th=h+depth
   if x+tw+2>256:x=0;y+=row+2;row=0
   assert y+th+1<=256,(fam,y,th)
   b['uv']=[x,y];mat=b['mat'];base=pal[mat];alpha=190 if mat=='gel' else 255
   # Fractional Java cuboids sample between the integer UV face boundaries.
   # Give the whole reserved island a material backing so thin ears, fingers,
   # tusks and suckers cannot sample transparent corners of the unfolded net.
   d.rectangle((x,y,x+tw-1,y+th-1),fill=(*base,alpha))
   # Lighting follows each UV face, not arbitrary noise across a shared flat-color patch.
   rects=[(depth,0,w,depth,1.2),(depth+w,0,w,depth,.68),(0,depth,depth,h,.75),(depth,depth,w,h,1),(depth+w,depth,depth,h,.88),(2*depth+w,depth,w,h,.8)]
   for fx,fy,fw,fh,light in rects:
    for py in range(fh):
     for px in range(fw):
      edge=px==0 or py==0 or px==fw-1 or py==fh-1
      k=light*(.82 if edge else 1)*(1-.13*py/max(1,fh-1))
      if mat in ['wood','leather','cloth']:
       k*=.78 if (mat=='wood' and px%4==0) or (mat=='cloth' and px%5==1) else 1
       if mat=='leather' and py==1 and px%3==1:k*=1.3
      elif mat in ['skin','skin_dark'] and fam in ['tentacle','lizard','lizardfolk','toad']:
       if (px+(py//3)%2*2)%4==0 and py%3==0:k*=.62
       if px%4==1 and py%3==1:k*=1.18
      elif mat in ['steel','steel_dark','gold','crystal']:k*=1.2 if px==1 else .86 if px==fw-2 else 1
      elif mat not in ['pupil','iris','socket','glint']:k*=rs.choice([.96,1,1.03])
      d.point((x+fx+px,y+fy+py),fill=(*tint(base,k),alpha))
   x+=tw+2;row=max(row,th)
 return im
def dump(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def bbmodel(parts,im,ident,name):
 elements=[];groups=[];lookup={};world={'root':[0,0,0]}
 for p in parts:
  o=[a+b for a,b in zip(world[p['parent']],p['pos'])];world[p['name']]=o;children=[]
  for i,b in enumerate(p['boxes']):
   key=str(uuid.uuid5(uuid.NAMESPACE_URL,ident+p['name']+str(i)));lo=[o[0]+b['at'][0],24-o[1]-b['at'][1]-b['size'][1],o[2]+b['at'][2]];hi=[lo[j]+b['size'][j] for j in range(3)];u,v=b['uv'];w,h,z=b['size'];faces={k:dict(uv=uv,texture=0) for k,uv in {'north':[u+z,v+z,u+z+w,v+z+h],'west':[u,v+z,u+z,v+z+h],'south':[u+2*z+w,v+z,u+2*z+2*w,v+z+h],'east':[u+z+w,v+z,u+2*z+w,v+z+h],'up':[u+z,v+z,u+z+w,v],'down':[u+z+w,v,u+z+2*w,v+z]}.items()};elements.append({'name':p['name']+' '+b['mat'],'uuid':key,'type':'cube','from':lo,'to':hi,'origin':[o[0],24-o[1],o[2]],'faces':faces});children.append(key)
  group=dict(name=p['name'],uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,ident+p['name'])),origin=[o[0],24-o[1],o[2]],rotation=[-math.degrees(p['rot'][0]),math.degrees(p['rot'][1]),-math.degrees(p['rot'][2])],children=children);lookup[p['name']]=group
  if p['parent']=='root':groups.append(group)
  else:lookup[p['parent']]['children'].append(group)
 bio=io.BytesIO();im.save(bio,format='PNG');return dict(meta=dict(format_version='4.10',model_format='free',box_uv=False),name=name,resolution=dict(width=256,height=256),elements=elements,outliner=groups,textures=[dict(name=ident+'.png',id='0',source='data:image/png;base64,'+base64.b64encode(bio.getvalue()).decode())])
species=re.findall(r's\("([a-z_]+)","([^"]+)",Family\.([A-Z]+),Role\.([A-Z]+),0x([0-9a-f]+),0x([0-9a-f]+)',(R/'src/main/java/com/tnc/tnc/dungeon/MobCatalog.java').read_text(encoding='utf-8'))
counts={}
for ident,name,fam,role,skin,accent in species:
 fam=fam.lower();parts=MODELS[fam];im=atlas(parts,fam,int(skin,16),int(accent,16),ident)
 path=A/'textures/entity/dungeon'/f'{ident}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 dump(R/'model-source/dungeon'/f'{ident}.bbmodel',bbmodel(parts,im,ident,name))
 dump(A/'dungeon_models'/f'{fam}.json',dict(texture_width=256,texture_height=256,parts=[{**p,'boxes':[{k:v for k,v in b.items() if k!='mat'} for b in p['boxes']]} for p in parts]))
 counts[fam]=dict(bones=len(parts),cubes=sum(len(p['boxes']) for p in parts))
dump(R/'model-source/dungeon/redesign-manifest.json',dict(species=len(species),families=counts,texture_size=256,source='tools/redesign_dungeon_bestiary.py'))
print(json.dumps(counts))
