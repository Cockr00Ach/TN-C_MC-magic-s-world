"""Generate the Stone Lancer Bedrock rig and animation clips; material atlas is AI-authored."""
import json,random
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
random.seed(3417)
bones=[]
def bone(name,pivot,parent='torso',rotation=None):
 b={'name':name,'pivot':pivot,'cubes':[]}
 if parent:b['parent']=parent
 if rotation:b['rotation']=rotation
 bones.append(b);return b

def cube(b,at,size,mat=0,rot=None):
 origins=[(0,0),(128,0),(0,128),(128,128)]
 u,v=origins[mat];span=min(72,max(24,max(size)*1.4))
 c={'origin':at,'size':size,'uv':{}}
 for f in ['north','south','east','west','up','down']:
  c['uv'][f]={'uv':[round(u+random.uniform(2,124-span),2),round(v+random.uniform(2,124-span),2)],'uv_size':[round(span,2),round(span,2)]}
 if rot:c.update(rotation=rot,pivot=[at[i]+size[i]/2 for i in range(3)])
 b['cubes'].append(c)

def generate():
 bones.clear()
 bone('root',[0,0,0],None)
 hip=bone('pelvis',[0,51,0],'root');cube(hip,[-22,47,-9],[44,24,27],1,[0,0,4]);cube(hip,[-16,43,-16],[32,18,13],0,[4,0,-5])
 body=bone('torso',[0,68,2],'pelvis')
 cube(body,[-26,66,-13],[52,48,33],1,[-4,0,0])
 for sign in [-1,1]:
  cube(body,[sign*14-12,77,-20],[24,30,13],0,[0,sign*12,sign*17]);cube(body,[sign*17-9,64,-17],[18,21,12],0,[0,0,sign*-15])
  cube(body,[sign*25-9,95,-10],[18,23,32],0,[0,0,sign*-12])
  cube(body,[sign*12-11,77,16],[22,33,16],1,[15,sign*10,sign*10])
 frame=bone('core_frame',[0,92,-17]);cube(frame,[-15,77,-22],[30,30,7],2,[0,0,45])
 core=bone('glow_core',[0,92,-23]);cube(core,[-9.8,82,-25],[19.6,19.6,6],3,[0,0,45])
 veins=bone('glow_veins',[0,85,-18])
 for sign in [-1,1]:
  cube(veins,[sign*19-1.2,88,-22.5],[2.4,18,1.3],2,[0,0,sign*-36]);cube(veins,[sign*14-1,69,-19],[2,14,1.4],2,[0,0,sign*24])
 head=bone('head',[0,112,-2]);cube(head,[-15,111,-12],[30,22,25],1,[7,0,-3]);cube(head,[-15,124,-5],[29,18,23],0,[8,5,-13])
 cube(head,[-12,112,-18],[24,9,9],0,[10,0,0]);cube(head,[-20,119,-8],[8,16,21],0,[0,0,-17]);cube(head,[12,118,-8],[8,17,21],0,[0,0,19])
 eyes=bone('glow_eyes',[0,124,-14],'head')
 for sign in [-1,1]:cube(eyes,[sign*7-5,123,-15],[10,2.2,2],3,[0,0,sign*-8])
 for sign in [-1,1]:
  horn=bone('crown_point_up_'+str(sign),[sign*11,134,4],'head',[9,0,sign*-18]);cube(horn,[sign*11-5,132,0],[10,22+(sign+1)*2,10],2)
  leg=bone('leg_'+str(sign),[sign*15,54,2],'pelvis');cube(leg,[sign*15-10,23,-8],[20,31,23],1,[0,0,sign*4]);cube(leg,[sign*15-12,32,-14],[24,18,15],0,[8,0,sign*6])
  foot=bone('foot_'+str(sign),[sign*15,22,-1],'leg_'+str(sign));cube(foot,[sign*15-12,3,-22],[24,22,33],0,[-5,0,sign*3]);cube(foot,[sign*15-10,1,-27],[20,11,19],1,[0,0,0])
  for j in range(3):cube(foot,[sign*15-10+j*7,2,-30],[6,8,10],0,[0,0,0])
  arm=bone('arm_'+str(sign),[sign*35,106,0],'torso',[0,0,sign*7]);cube(arm,[sign*37-14,91,-12],[28,27,29],0,[4,sign*10,sign*10]);cube(arm,[sign*41-10,76,-9],[20,25,21],1,[0,0,sign*6])
  cube(arm,[sign*44-8,83,-15],[16,26,12],0,[0,0,sign*-12])
  fore=bone('fore_'+str(sign),[sign*44,78,-1],'arm_'+str(sign))
  cube(fore,[sign*44-12,61,-12],[24,20,26],1,[0,0,sign*6]);cube(fore,[sign*44-11,46,-11],[22,24,24],0,[0,sign*8,sign*-5]);cube(fore,[sign*44-9,35,-10],[18,20,21],2,[0,0,sign*3])
  tip=bone('lance_point_down_'+str(sign),[sign*44,47,0],'fore_'+str(sign));cube(tip,[sign*44-10,7 if sign==1 else 11,-11],[20,40 if sign==1 else 36,22],0)
  glow=bone('glow_arm_'+str(sign),[sign*44,64,-12],'fore_'+str(sign));cube(glow,[sign*44-1.1,42,-12.3],[2.2,32,1.5],2,[0,0,sign*7])
  brace=bone('arm_brace_point_down_'+str(sign),[sign*50,104,2],'arm_'+str(sign),[0,0,sign*10]);cube(brace,[sign*50-5,71,-6],[10,36,17],0)
  for j in range(2):
   shoulder=bone('shoulder_point_up_'+str(sign)+'_'+str(j),[sign*(40+j*9),111,4+j*3],'arm_'+str(sign),[10+j*8,0,sign*(-20-j*16)])
   cube(shoulder,[sign*(40+j*9)-5,108,j*3],[10,27-j*4,13],1 if j else 2)
 for j in range(4):
  spike=bone('back_point_up_'+str(j),[(j-1.5)*13,104,20],'torso',[28+abs(j-1.5)*10,0,(j-1.5)*-10]);cube(spike,[(j-1.5)*13-5,101,17],[10,37-abs(j-1.5)*7,14],1 if j%2 else 2)
 for b in bones:
  if b['name'] in ['head','glow_eyes'] or b.get('parent')=='head':
   # Keep the face readable above the broad chest instead of burying it in the shoulders.
   b['pivot']=[b['pivot'][0],b['pivot'][1]+8,b['pivot'][2]-10]
   for c in b['cubes']:
    for key in ['origin','pivot']:
     if key in c:c[key]=[c[key][0],c[key][1]+8,c[key][2]-10]
  # Short, broad silhouette. Arm pieces get a second thickness pass about their pivots.
  arm_part=any(k in b['name'] for k in ['arm_', 'fore_', 'lance_', 'shoulder_'])
  if arm_part:
   for c in b['cubes']:
    for axis in [0,2]:
     centre=c['origin'][axis]+c['size'][axis]/2
     c['size'][axis]*=1.45
     c['origin'][axis]=centre-c['size'][axis]/2
  for key in ['pivot']:
   if key in b:b[key]=[round(v*s,4) for v,s in zip(b[key],[1.35,.62,1.5])]
  for c in b['cubes']:
   for key in ['origin','size','pivot']:
    if key in c:c[key]=[round(v*s,4) for v,s in zip(c[key],[1.35,.62,1.5])]
   # Preserve aspect ratio using final face dimensions, rather than stretching square UVs.
   for face,uv in c['uv'].items():
    u=128 if uv['uv'][0]>=128 else 0;v=128 if uv['uv'][1]>=128 else 0
    sx,sy,sz=c['size'];w,h=(sx,sy) if face in ['north','south'] else (sz,sy) if face in ['east','west'] else (sx,sz)
    density=min(.95,118/max(w,h));uw,uh=max(1,w*density),max(1,h*density)
    uv.update(uv=[round(u+random.uniform(2,126-uw),3),round(v+random.uniform(2,126-uh),3)],uv_size=[round(uw,3),round(uh,3)])
  if b['name'] in ['glow_core','glow_eyes']:
   for c in b['cubes']:
    for face in c['uv'].values():face.update(uv=[151,142],uv_size=[35,31])
 from refine_stone_lancer_geometry import refine
 refine(bones)
 from stone_lancer_piece_atlas import taller_head, separate_silhouette, pack, ATLAS
 taller_head(bones)
 separate_silhouette(bones)
 pack(bones)
 geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.tnc.stone_lancer','texture_width':ATLAS,'texture_height':ATLAS,'visible_bounds_width':13,'visible_bounds_height':12,'visible_bounds_offset':[0,5,0]},'bones':bones}]}
 out=ROOT/'src/main/resources/assets/tnc/geo/entity/stone_lancer.geo.json';out.write_text(json.dumps(geo,indent=2),encoding='utf-8',newline='\n')
 animations={}
 def clip(name,length,loop=False):
  d={'animation_length':length,'bones':{}}
  if loop:d['loop']=True
  animations[name]=d;return d['bones']
 def channel(b,n,key,values):b.setdefault(n,{})[key]={str(t):v for t,v in values}
 b=clip('idle',3.2,True)
 channel(b,'torso','rotation',[(0,[1,0,-.7]),(1.6,[-1.5,0,.7]),(3.2,[1,0,-.7])])
 channel(b,'glow_core','scale',[(0,[1,1,1]),(1.6,[1.055,1.055,1.055]),(3.2,[1,1,1])])
 b=clip('march',1.8,True)
 channel(b,'root','position',[(0,[0,0,0]),(.45,[0,2,0]),(.9,[0,0,0]),(1.35,[0,2,0]),(1.8,[0,0,0])])
 channel(b,'torso','rotation',[(0,[3,0,2]),(.9,[3,0,-2]),(1.8,[3,0,2])])
 for s in [-1,1]:
  channel(b,'leg_'+str(s),'rotation',[(0,[s*12,0,0]),(.9,[-s*12,0,0]),(1.8,[s*12,0,0])])
  channel(b,'arm_'+str(s),'rotation',[(0,[-s*5,0,s*7]),(.9,[s*5,0,s*7]),(1.8,[-s*5,0,s*7])])
 for name,length,release in [('volley',2.1,1.4),('fissure',2.5,1.6),('sky',2.8,2.2),('core',2.6,1.8),('melee',1.2,.4)]:
  b=clip(name,length)
  for s in [-1,1]:
   pose={'volley':[-78,0,s*-12],'fissure':[-153,0,s*-15],'sky':[-148,0,s*-38],'core':[-80,0,s*-45],'melee':[-65,0,s*-8]}[name]
   strike=[-18,0,s*8] if name=='fissure' else [-104,0,s*-10] if name in ['volley','melee'] else pose
   channel(b,'arm_'+str(s),'rotation',[(0,[0,0,s*7]),(release*.65,pose),(release-.07,pose),(release,strike),(length,[0,0,s*7])])
   channel(b,'fore_'+str(s),'rotation',[(0,[0,0,0]),(release*.65,[-12,0,0]),(release,[-25,0,0]),(length,[0,0,0])])
  channel(b,'torso','rotation',[(0,[0,0,0]),(release*.7,[-6,0,0]),(release,[15 if name=='fissure' else 5,0,0]),(length,[0,0,0])])
  channel(b,'glow_core','scale',[(0,[1,1,1]),(release,[1.12,1.12,1.12]),(length,[1,1,1])])
 b=clip('death',1)
 channel(b,'root','position',[(0,[0,0,0]),(.65,[0,-12,0]),(1,[0,-30,0])]);channel(b,'torso','rotation',[(0,[0,0,0]),(.65,[35,0,8]),(1,[65,0,12])])
 b=clip('burrow',1.2)
 channel(b,'torso','rotation',[(0,[0,0,0]),(.45,[18,0,0]),(1.2,[28,0,0])])
 for s in [-1,1]:
  channel(b,'arm_'+str(s),'rotation',[(0,[0,0,s*7]),(.45,[-48,0,s*-12]),(.85,[-12,0,s*5]),(1.2,[0,0,s*7])])
 b=clip('emerge_core',1.4)
 channel(b,'torso','rotation',[(0,[28,0,0]),(.7,[5,0,0]),(1.4,[-6,0,0])])
 for s in [-1,1]:
  channel(b,'arm_'+str(s),'rotation',[(0,[0,0,s*7]),(.8,[-45,0,s*-25]),(1.4,[-80,0,s*-45])])
  channel(b,'fore_'+str(s),'rotation',[(0,[0,0,0]),(1.4,[-12,0,0])])
 channel(b,'glow_core','scale',[(0,[1,1,1]),(1.4,[1.1,1.1,1.1])])
 b=clip('core_followup',2)
 channel(b,'torso','rotation',[(0,[-6,0,0]),(1.2,[5,0,0]),(2,[0,0,0])])
 for s in [-1,1]:
  channel(b,'arm_'+str(s),'rotation',[(0,[-80,0,s*-45]),(1.2,[-80,0,s*-45]),(2,[0,0,s*7])])
  channel(b,'fore_'+str(s),'rotation',[(0,[-12,0,0]),(1.2,[-25,0,0]),(2,[0,0,0])])
 channel(b,'glow_core','scale',[(0,[1.1,1.1,1.1]),(1.2,[1.25,1.25,1.25]),(2,[1,1,1])])
 b=clip('gravity_core',7.2)
 channel(b,'torso','rotation',[(0,[0,0,0]),(2,[-8,0,0]),(6.8,[-8,0,0]),(7.2,[0,0,0])])
 for s in [-1,1]:
  channel(b,'arm_'+str(s),'rotation',[(0,[0,0,s*7]),(2,[-112,0,s*-48]),(6.8,[-112,0,s*-48]),(7.2,[0,0,s*7])])
  channel(b,'fore_'+str(s),'rotation',[(0,[0,0,0]),(2,[-12,0,0]),(6.8,[-12,0,0]),(7.2,[0,0,0])])
 channel(b,'glow_core','scale',[(0,[1,1,1]),(3,[1.3,1.3,1.3]),(6.8,[1.3,1.3,1.3]),(7.2,[1,1,1])])
 out=ROOT/'src/main/resources/assets/tnc/animations/entity/stone_lancer.animation.json';out.write_text(json.dumps({'format_version':'1.8.0','animations':animations},indent=2),encoding='utf-8',newline='\n')
 print(len(bones),'bones;',sum(len(b['cubes']) for b in bones),'rock pieces;',len(animations),'animation clips')
if __name__=='__main__':generate()
