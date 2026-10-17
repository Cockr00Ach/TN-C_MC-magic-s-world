"""Native, editable Minecraft crystal models; does not change recipes or item sprites elsewhere."""
from pathlib import Path
import json,math
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
def save(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf8')
def box(a,b,mat):return dict(from_=a,to=b,faces={f:dict(texture='#'+mat) for f in ['north','south','east','west','up','down']})
def mesh(elements,textures,display=None):
 out={'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':dict(textures,particle=next(iter(textures.values()))),'elements':[{('from' if k=='from_' else k):v for k,v in e.items()} for e in elements]}
 if display:out['display']=display
 return out
DISPLAY={'gui':{'rotation':[20,35,0],'translation':[0,0,0],'scale':[.9,.9,.9]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.7,.7,.7]},'firstperson_righthand':{'rotation':[0,-35,15],'translation':[0,0,0],'scale':[.55,.55,.55]},'thirdperson_righthand':{'rotation':[0,0,15],'translation':[0,0,0],'scale':[.45,.45,.45]}}
for tier,name,skin,accent in [(0,'mana_thread','ice','cotton'),(1,'radiant_thread','rose','pollen'),(2,'star_thread','violet','ice')]:
 textures={'skin':'tnc:block/art_'+skin,'accent':'tnc:block/art_'+accent,'white':'tnc:block/art_cotton','glass':'tnc:block/energy_glass'}
 core=[box([6,6,6],[10,10,10],'skin'),box([7,5.3,7],[9,10.7,9],'accent'),box([7.7,7.7,5.7],[8.3,8.3,10.3],'white')]
 save(A/'models/block'/('fiber_'+name+'_core.json'),mesh(core,textures))
 parts=[{'apply':{'model':'tnc:block/fiber_'+name+'_core'}}]
 for direction,axis,positive in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
  elems=[]
  # Two interlaced crystalline strands terminate flush at the matching socket.
  for step in range(5):
   u=(step+.5)/5;center=[8,8,8];center[axis]=8+(1 if positive else -1)*(1.7+u*5.8)
   a1,a2=[i for i in range(3) if i!=axis]
   for braid in range(2):
    c=center.copy();a=u*math.pi*1.5+braid*math.pi;c[a1]+=.7*math.cos(a);c[a2]+=.7*math.sin(a)
    lo=[v-.48 for v in c];hi=[v+.48 for v in c];lo[axis]=max(0,center[axis]-.8);hi[axis]=min(16,center[axis]+.8)
    elems.append(box(lo,hi,'skin' if braid==0 else 'accent'))
  stem='fiber_'+name+'_'+direction;save(A/'models/block'/(stem+'.json'),mesh(elems,textures));parts.append({'when':{direction:'true'},'apply':{'model':'tnc:block/'+stem}})
 save(A/'blockstates'/(name+'.json'),{'multipart':parts})
 save(A/'models/item'/(name+'.json'),mesh(core+[box([2,7.3,7.3],[14,8.7,8.7],'accent')],textures,DISPLAY))
# Three different materials: fractured alloy shard, amber crescent and stellar geode.
for name in ['spirit_iron','radiant_gold','star_marrow']:
 textures={'shell':'tnc:block/art_iron','cyan':'tnc:block/art_ice','gold':'tnc:block/art_pollen','pink':'tnc:block/art_rose','violet':'tnc:block/art_violet','white':'tnc:block/art_cotton'};e=[]
 if name=='spirit_iron':
  for x,y,w,h in [(7,1,3,4),(6,4,5,5),(7,8,4,5),(8,12,2,3)]:e.append(box([x,y,6],[x+w,y+h,10],'shell'))
  e += [box([6.5,4.5,5.6],[7.4,11,6.3],'cyan'),box([8,8,5.4],[9,14,6.2],'cyan'),box([10.3,5,6.4],[12,8,9.5],'white')]
 elif name=='radiant_gold':
  for i in range(10):
   a=(50+i*28)*math.pi/180;x,y=8+math.cos(a)*4,8+math.sin(a)*5;e.append(box([x-1,y-1,6.7],[x+1,y+1,9.3],'gold'))
  e += [box([7,5,6],[10,10,10],'pink'),box([7.8,10,6.8],[9.2,13,9.2],'white'),box([6.4,3,7],[10.6,4,9],'violet')]
 else:
  for i in range(8):
   a=i*math.tau/8;x,y=8+math.cos(a)*4,8+math.sin(a)*4;e.append(box([x-1,y-1,5.7],[x+1,y+1,10.3],'shell' if i%2 else 'violet'))
  e += [box([6,5,6],[10,11,10],'cyan'),box([7.3,3,7.3],[8.7,13,8.7],'white'),box([4,7.3,7.3],[12,8.7,8.7],'pink'),box([7.4,7.4,4.5],[8.6,8.6,11.5],'white')]
 save(A/'models/item'/(name+'.json'),mesh(e,textures,DISPLAY))
for locale in ['zh_cn','en_us']:
 p=A/'lang'/(locale+'.json');d=json.loads(p.read_text('utf-8-sig'))
 for id,label in [('mana_thread','青辉导魔晶纤'),('radiant_thread','霞金导魔晶纤'),('star_thread','虹星导魔晶纤')]:
  for prefix in ['item','block']:d[prefix+'.tnc.'+id]=label if locale=='zh_cn' else {'mana_thread':'Azure Mana Fiber','radiant_thread':'Dawn Mana Fiber','star_thread':'Iridescent Mana Fiber'}[id]
 save(p,d)
print('Built two-ended crystal fibers with six possible orientations and three distinct material models')
