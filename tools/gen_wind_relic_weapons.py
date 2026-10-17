"""Faceted wind relics, matching held items, and a hand-painted-style material atlas."""
from pathlib import Path
import json,math,random
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc'
OUT=ROOT/'models/wind/arsenal'
random.seed(218)
im=Image.new('RGBA',(128,128));px=im.load()
colors=[(34,80,91),(108,202,202),(221,246,244),(255,216,125),(133,255,229),(29,48,66),(66,115,126),(238,251,246)]
for tile,c in enumerate(colors):
 ox=(tile%4)*32;oy=(tile//4)*64
 for y in range(64):
  for x in range(32):
   light=.67+.32*math.sin(x/31*math.pi)+.1*math.sin(y*.25)+random.random()*.035
   px[ox+x,oy+y]=tuple(min(255,int(v*light)) for v in c)+(255,)
 d=ImageDraw.Draw(im)
 for y in range(5,61,11):
  d.line([(ox+10,oy+y+4),(ox+16,oy+y),(ox+22,oy+y+4)],fill=tuple(min(255,v+25) for v in c)+(255,),width=1)
(ROOT/'textures/item').mkdir(exist_ok=True)
im.save(ROOT/'textures/item/wind_relic_metal.png')
textures={str(i):'tnc:item/wind_relic_metal' for i in range(8)};textures['particle']='tnc:item/wind_relic_metal'
def uv(mat):
 x=mat%4*4;y=mat//4*8
 return [x+.2,y+.2,x+3.8,y+7.8]
def box(x,y,z,w,h,l,mat=0,rot=None):
 e={'from':[round(x,4),round(y,4),round(z,4)],'to':[round(x+w,4),round(y+h,4),round(z+l,4)],'shade':True,'faces':{f:{'uv':uv(mat),'texture':'#'+str(mat)} for f in ['north','south','east','west','up','down']}}
 if rot:e['rotation']={'origin':[8,8,z+l*.5],'axis':rot[0],'angle':rot[1]}
 return e
def save(name,parts):
 data={'ambientocclusion':False,'textures':textures,'elements':parts}
 (OUT/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
 display={
 'thirdperson_righthand':{'rotation':[-90,0,0],'translation':[0,10,0],'scale':[.8,.8,.8]},
 'thirdperson_lefthand':{'rotation':[-90,0,0],'translation':[0,10,0],'scale':[.8,.8,.8]},
 'firstperson_righthand':{'rotation':[-90,-15,-12],'translation':[1,8,0],'scale':[.7,.7,.7]},
 'firstperson_lefthand':{'rotation':[-90,15,12],'translation':[1,8,0],'scale':[.7,.7,.7]},
 'gui':{'rotation':[-80,0,-35],'translation':[0,0,0],'scale':[.65,.65,.65]},
 'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
 'fixed':{'rotation':[-90,0,0],'scale':[.7,.7,.7]}}
 (ROOT/f'models/item/wind_relic_{name}.json').write_text(json.dumps({'parent':'tnc:wind/arsenal/'+name,'display':display},indent=2)+'\n')
# Sword: central dark fuller, bevelled paired cutting edges, swept split wing guard, jewel and wrapped grip.
a=[]
for j in range(14):
 z=5+j*1.25;w=3.3*(1-j/16)**.55;th=.42*(1-j/19)
 a += [box(8-w/2,8-th/2,z,w,th,1.3,2),box(7.73,7.68,z,.54,.64,1.3,5)]
 for side in [-1,1]:
  x=8+side*(w/2-.16)
  a.append(box(x-.13,7.88,z,.26,.24,1.3,7))
  if j%2==0:a.append(box(8+side*.48-.11,8.23,z+.2,.22,.08,.7,4))
a += [box(7.7,7.7,22.4,.6,.6,1.5,7,('z',45)),box(7.45,7.45,-4.8,1.1,1.1,8,5)]
for j in range(9):a.append(box(7.35,7.35,-4.5+j*.8,1.3,1.3,.18,3,('z',22.5 if j%2 else -22.5)))
for side in [-1,1]:
 for j in range(5):
  x=8+side*(1+j*.78)
  a += [box(x-.58,7.58,3-j*.37,1.16,.84,.7,3,('z',side*22.5)),box(x-.44,7.9,3.15-j*.37,.88,.16,.32,4)]
 a.append(box(8+side*2.2-.24,7.66,4.4,.48,.68,2.5,1,('y',side*22.5)))
a += [box(7.1,7.1,2.7,1.8,1.8,1.8,3,('z',45)),box(7.48,7.48,2.55,1.04,1.04,2.1,4,('z',45)),box(7.2,7.2,-6.2,1.6,1.6,1.6,3,('z',45)),box(7.6,7.6,-7.2,.8,.8,1,4)]
a += [box(8-(.65-j*.1)/2,8-(.65-j*.1)/2,22.4+j*.45,.65-j*.1,.65-j*.1,.46,7) for j in range(6)]
save('sword',a)
# Spear: segmented reinforced haft, tri-fin spearhead, detached winglets, collar and counterweight.
a=[box(7.58,7.58,-13,.84,.84,28,5),box(7.83,7.42,-12,.34,.16,25,4)]
for j in range(13):
 z=-11+j*1.65;a.append(box(7.4,7.4,z,1.2,1.2,.25,3,('z',45 if j%2 else 0)))
for j in range(10):
 z=10+j*1.25;w=4.6*(1-j/10)**.7
 a += [box(8-w/2,7.8,z,w,.4,1.28,2),box(7.8,8-w*.3,z,.4,w*.6,1.28,0),box(7.78,7.55,z,.44,.9,1.28,4)]
 for side in [-1,1]:a.append(box(8+side*(w/2-.1)-.1,7.85,z,.2,.3,1.28,7))
for side in [-1,1]:
 for j in range(4):
  a.append(box(8+side*(1.6+j*.65)-.3,7.65,9-j*.75,.6,.7,2,3,('y',side*22.5)))
 a.append(box(8+side*2.8-.2,7.8,6.5,.4,.4,2.1,4))
a += [box(7.05,7.05,8.7,1.9,1.9,1.2,3,('z',45)),box(7.55,7.55,22.4,.9,.9,1.4,7,('z',45)),box(7.3,7.3,-14.5,1.4,1.4,1.5,3,('z',45)),box(7.65,7.65,-16,.7,.7,1.5,7)]
a += [box(8-(.75-j*.12)/2,8-(.75-j*.12)/2,22.4+j*.45,.75-j*.12,.75-j*.12,.46,7) for j in range(6)]
save('spear',a)
print('Generated detailed sword, spear, matching held item models and 128px engraved material atlas')

# Soft native billboard-faced mist puffs make the huge orb read as wind, rather than a glass cube.
mist=Image.new('RGBA',(64,64));px=mist.load()
for y in range(64):
 for x in range(64):
  r=math.hypot((x-31.5)/32,(y-31.5)/32)
  a=int(max(0,1-r)**1.8*110*(.8+.2*math.sin(x*.3+y*.19)))
  px[x,y]=(160,223,214,a)
mist.save(ROOT/'textures/item/wind_relic_mist.png')
puffs=[]
for row,num in enumerate([6,10,12,10,6]):
 lat=(row-2)*.48;rr=math.cos(lat)*4.3
 for j in range(num):
  angle=j*math.tau/num+row*.47;x=8+rr*math.cos(angle);y=8+math.sin(lat)*4.3;z=8+rr*math.sin(angle);size=3.1
  e=box(x-size/2,y-size/2,z-size/2,size,size,size)
  for f in e['faces'].values():f['texture']='#mist';f['uv']=[0,0,16,16]
  puffs.append(e)
(OUT/'core.json').write_text(json.dumps({'ambientocclusion':False,'textures':{'mist':'tnc:item/wind_relic_mist','particle':'tnc:item/wind_relic_mist'},'elements':puffs},indent=2)+'\n')
