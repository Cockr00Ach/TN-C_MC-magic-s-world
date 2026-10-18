"""Original UV-mapped cuboid monsters, editable Blockbench sources, and dungeon block assets."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random,base64,io,math,uuid
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'src/main/resources/assets/tnc';SPECS={}
UV={'skin':[0,0],'armor':[0,48],'metal':[0,80],'cloth':[64,80],'bone':[0,104],'eyes':[96,112],'mouth':[64,112]}
def bone(parts,name,pos,boxes,parent='root',rot=(0,0,0)):
    parts.append(dict(name=name,parent=parent,pos=list(pos),rot=list(rot),boxes=[dict(at=list(at),size=list(size),uv=UV[mat]) for at,size,mat in boxes]))
def box(at,size,mat='skin'):return (at,size,mat)
def biped(family,scale=1):
    p=[];big=family in ('orc','demon');h=22 if big else 17;w=7 if big else 5
    bone(p,'body',(0,24,0),[box((-w,-h,-4),(2*w,h-9,8)),box((-w-.5,-h+2,-4.5),(2*w+1,4,9),'armor'),box((-w,-11,-4),(2*w,3,8),'metal')])
    bone(p,'head',(0,24-h,0),[box((-w,-8,-4.5),(2*w,8,9)),box((-w,-6,-5.3),(2*w,2,1),'armor'),box((-w+1,-5,-5.5),(2,2,1),'eyes'),box((w-3,-5,-5.5),(2,2,1),'eyes')])
    for side,sign in [('l',1),('r',-1)]:
        bone(p,'arm_'+side,(sign*(w+2),24-h+2,0),[box((-2,-1,-2),(4,h-8,4)),box((-2.5,-1,-2.5),(5,4,5),'armor')])
        bone(p,'leg_'+side,(sign*3,15,0),[box((-2,0,-2),(4,9,4),'cloth'),box((-2.5,7,-3),(5,2,6),'metal')])
    if family=='goblin':
        bone(p,'ear_l',(5,9,-1),[box((0,-2,-1),(5,3,2))],rot=(0,0,-.25));bone(p,'ear_r',(-5,9,-1),[box((-5,-2,-1),(5,3,2))],rot=(0,0,.25))
        bone(p,'nose',(0,10,-5),[box((-1,-1,-3),(2,3,3))]);bone(p,'cap',(0,5,0),[box((-5,-1,-4),(10,2,8),'cloth'),box((-3,-4,-2),(6,3,4),'cloth')])
    if family=='orc':
        bone(p,'jaw',(0,7,-3),[box((-5,-1,-3),(10,5,6)),box((-4,-3,-3),(2,5,2),'bone'),box((2,-3,-3),(2,5,2),'bone')]);bone(p,'shoulder_l',(10,5,0),[box((-4,-2,-5),(8,5,10),'metal'),box((-1,-5,-1),(2,4,2),'bone')]);bone(p,'shoulder_r',(-10,5,0),[box((-4,-2,-5),(8,5,10),'armor')])
    if family=='lizardfolk':
        bone(p,'snout',(0,5,-4),[box((-4,1,-5),(8,4,6)),box((-4,4,-5),(8,1,5),'bone')]);bone(p,'crest',(0,-1,0),[box((-1,-4,-1),(2,7,7),'cloth')]);bone(p,'tail',(0,16,4),[box((-2,-1,0),(4,4,12)),box((-1,0,12),(2,3,7),'armor')],rot=(.18,0,0))
    if family=='demon':
        for sign,side in [(-1,'r'),(1,'l')]:
            bone(p,'horn_'+side,(sign*5,-5,0),[box((-1,-5,-1),(3,8,3),'bone')],rot=(0,0,sign*.35))
            bone(p,'wing_'+side,(sign*6,5,4),[box((0 if sign>0 else -15,-5,0),(15,12,1),'cloth'),box((0 if sign>0 else -15,-6,-.5),(15,2,2),'bone')],rot=(0,sign*.4,sign*-.15))
    return p
for family in ['goblin','orc','lizardfolk','demon']:SPECS[family]=biped(family)
p=[]
bone(p,'body',(0,24,0),[box((-4,-21,-2),(8,9,4),'bone'),box((-1,-14,-1),(2,5,2),'bone')])
for y in [-19,-16,-13]:bone(p,'rib_'+str(-y),(0,24,0),[box((-5,y,-3),(10,1,6),'bone')])
bone(p,'head',(0,2,0),[box((-4,-7,-4),(8,8,8),'bone'),box((-3,-4,-4.5),(2,3,1),'eyes'),box((1,-4,-4.5),(2,3,1),'eyes'),box((-2,-1,-4.5),(4,1,1),'mouth')])
for sign,side in [(-1,'r'),(1,'l')]:
 bone(p,'arm_'+side,(sign*6,4,0),[box((-1,0,-1),(2,15,2),'bone')]);bone(p,'leg_'+side,(sign*2,15,0),[box((-1,0,-1),(2,9,2),'bone')])
SPECS['skeleton']=p
p=[];bone(p,'body',(0,24,0),[box((-8,-16,-8),(16,16,16)),box((-5,-11,-8.4),(3,3,1),'eyes'),box((2,-11,-8.4),(3,3,1),'eyes'),box((-2,-5,-8.4),(4,1,1),'mouth')]);SPECS['slime']=p
p=[];bone(p,'body',(0,24,0),[box((-9,-14,-8),(18,10,16)),box((-6,-19,-6),(12,6,12),'armor'),box((-4,-12,-8.5),(8,6,1),'mouth')]);
for i in range(8):
 a=i*math.pi/4;x=math.cos(a)*8;z=math.sin(a)*8
 bone(p,'tentacle_'+str(i),(x,18,z),[box((-2,-1,0),(4,4,9)),box((-1,1,9),(2,3,7),'armor')],rot=(0,a,.18))
bone(p,'eye',(0,7,-6),[box((-3,-1,-1),(6,3,2),'eyes')]);SPECS['tentacle']=p
p=[];bone(p,'body',(0,24,0),[box((-13,-15,-10),(26,12,22)),box((-10,-9,-12),(20,7,3),'armor'),box((-10,-8,-12.4),(20,1,1),'mouth')]);
for sign,side in [(-1,'r'),(1,'l')]:
 bone(p,'eye_'+side,(sign*8,8,-6),[box((-3,-5,-3),(6,6,6)),box((-2,-4,-3.5),(4,4,1),'eyes')]);bone(p,'leg_'+side,(sign*11,20,4),[box((-5,-3,-4),(10,6,10),'armor'),box((-5,0,-11),(10,4,9))]);bone(p,'arm_'+side,(sign*11,19,-5),[box((-2,-1,-2),(4,6,4)),box((-3,4,-5),(6,1,7),'bone')])
SPECS['toad']=p
p=[];bone(p,'body',(0,24,0),[box((-9,-13,-13),(18,10,27)),box((-6,-16,-9),(12,4,20),'armor')]);bone(p,'head',(0,14,-12),[box((-7,-5,-12),(14,8,14)),box((-7,1,-12.3),(14,1,12),'mouth'),box((-6,-5,-10),(3,2,1),'eyes'),box((3,-5,-10),(3,2,1),'eyes')]);bone(p,'tail',(0,16,13),[box((-5,-3,0),(10,6,13)),box((-3,-2,13),(6,4,12)),box((-1,-1,25),(2,2,10),'armor')]);
for i,(x,z) in enumerate([(-9,-7),(9,-7),(-9,8),(9,8)]):bone(p,'leg_'+str(i),(x,18,z),[box((-3,-2,-3),(6,7,6),'armor'),box((-3,4,-5),(6,2,8),'bone')])
SPECS['lizard']=p
p=[];bone(p,'body',(0,24,0),[box((-8,-9,-6),(16,9,12),'armor'),box((-8,-2,-6.3),(16,2,1),'metal')]);bone(p,'lid',(0,15,6),[box((-8,-5,-12),(16,5,12),'armor'),box((-7,-1,-11.5),(2,3,2),'bone'),box((5,-1,-11.5),(2,3,2),'bone'),box((-2,-1,-11.5),(4,2,2),'bone')]);bone(p,'tongue',(0,18,-5),[box((-3,0,-11),(6,2,12),'mouth')]);bone(p,'eye_l',(4,16,-6.5),[box((-1,-1,-1),(2,2,1),'eyes')]);bone(p,'eye_r',(-4,16,-6.5),[box((-1,-1,-1),(2,2,1),'eyes')]);SPECS['mimic']=p

# Parent facial anatomy to the head, after correcting authored absolute pivots.
for family in ['goblin','orc','lizardfolk','demon']:
 parts=SPECS[family];head=next(p for p in parts if p['name']=='head')['pos']
 for part in parts:
  name=part['name']
  if not(name.startswith(('ear_','horn_')) or name in ('nose','cap','jaw','snout','crest')):continue
  if family=='goblin':part['pos'][1]={'nose':4,'cap':-1}.get(name,2)
  if family=='orc' and name=='jaw':part['pos'][1]=0
  if family=='lizardfolk' and name=='snout':part['pos'][1]=2
  part['parent']='head';part['pos']=[a-b for a,b in zip(part['pos'],head)]
for part in SPECS['lizard']:
 if part['name']=='head':
  for cube in part['boxes']:
   if cube['uv']==UV['eyes']:cube['at'][2]=-12.4
for part in SPECS['tentacle']:
 if part['name'].startswith('tentacle_'):
  a=int(part['name'].split('_')[1])*math.pi/4;part['rot'][1]=math.pi/2-a
for family in ['goblin','orc','lizardfolk','demon','skeleton']:
 p=SPECS[family]
 bone(p,'weapon_blade',(0,8,-1),[box((-1,-10,-4),(2,12,2),'metal'),box((-3,1,-4),(6,2,2),'bone')],parent='arm_r')
 bone(p,'weapon_staff',(0,7,-1),[box((-1,-18,-2),(2,24,2),'armor'),box((-3,-22,-4),(6,5,5),'eyes')],parent='arm_r')
 bone(p,'weapon_shield',(0,5,-2),[box((-5,-6,-3),(10,13,3),'metal'),box((-1,-5,-3.5),(2,11,1),'armor')],parent='arm_l')
 bone(p,'weapon_bow',(0,6,-2),[box((-1,-8,-4),(2,16,2),'armor'),box((-1,-8,-4),(2,2,6),'bone'),box((-1,6,-4),(2,2,6),'bone')],parent='arm_l')

def write_json(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
for family,parts in SPECS.items():write_json(AS/'dungeon_models'/f'{family}.json',dict(parts=parts,texture_width=128,texture_height=128))
# Read the explicit Java catalog, which is the gameplay source of truth.
import re
java=(ROOT/'src/main/java/com/tnc/tnc/dungeon/MobCatalog.java').read_text('utf8')
species=re.findall(r's\("([a-z_]+)","([^"]+)",Family\.([A-Z]+),Role\.([A-Z]+),0x([0-9a-f]+),0x([0-9a-f]+)',java)
for ident,name,family,role,skin,accent in species:
 family=family.lower();skin=int(skin,16);accent=int(accent,16);rs=random.Random(ident)
 rgb=lambda c:((c>>16)&255,(c>>8)&255,c&255)
 shade=lambda c,m:tuple(max(0,min(255,int(v*m))) for v in rgb(c))
 im=Image.new('RGBA',(128,128));draw=ImageDraw.Draw(im)
 regions=[((0,0,128,48),skin),((0,48,128,80),accent),((0,80,64,104),0x6c7980),((64,80,128,112),accent),((0,104,64,128),0xddcfaa),((64,112,96,128),0x642f42),((96,112,128,128),0xf3df9d)]
 for (x1,y1,x2,y2),c in regions:
  for y in range(y1,y2):
   for x in range(x1,x2):
    factor=1+rs.choice([-.14,-.06,0,.04,.1]);edge=(x%16 in (0,15) or y%16 in (0,15));color=shade(c,factor*(.8 if edge else 1));im.putpixel((x,y),(*color,210 if family=='slime' and y<48 else 255))
 if family in ('toad','lizard','lizardfolk','tentacle'):
  for _ in range(80):
   x=rs.randrange(126);y=rs.randrange(46);draw.rectangle((x,y,x+1,y+1),fill=(*rgb(accent),255))
 if role=='CASTER':
  for x in range(68,124,8):draw.line((x,82,x+3,106),fill=(*shade(accent,1.35),255),width=1)
 path=AS/'textures/entity/dungeon'/f'{ident}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 # Complete editable mesh, per-family bone hierarchy and embedded UV atlas.
 elements=[];groups=[];lookup={};global_pos={'root':[0,0,0]}
 for part in SPECS[family]:
  origin=[a+b for a,b in zip(global_pos[part['parent']],part['pos'])];global_pos[part['name']]=origin;gid=str(uuid.uuid5(uuid.NAMESPACE_URL,ident+part['name']));children=[]
  for j,cube in enumerate(part['boxes']):
   uid=str(uuid.uuid5(uuid.NAMESPACE_URL,ident+part['name']+str(j)));at=[origin[0]+cube['at'][0],24-origin[1]-cube['at'][1]-cube['size'][1],origin[2]+cube['at'][2]];to=[at[i]+cube['size'][i] for i in range(3)];u,v=cube['uv'];w,h,d=cube['size'];faces={n:dict(uv=uv,texture=0) for n,uv in {'north':[u+d,v+d,u+d+w,v+d+h],'west':[u,v+d,u+d,v+d+h],'south':[u+2*d+w,v+d,u+2*d+2*w,v+d+h],'east':[u+d+w,v+d,u+2*d+w,v+d+h],'up':[u+d,v+d,u+d+w,v],'down':[u+d+w,v,u+d+2*w,v+d]}.items()};elements.append(dict(name=part['name'],uuid=uid,type='cube',from_=at,to=to,origin=[origin[0],24-origin[1],origin[2]],faces=faces));elements[-1]['from']=elements[-1].pop('from_');children.append(uid)
  group=dict(name=part['name'],uuid=gid,origin=[origin[0],24-origin[1],origin[2]],rotation=[-math.degrees(part['rot'][0]),math.degrees(part['rot'][1]),-math.degrees(part['rot'][2])],children=children);lookup[part['name']]=group
  if part['parent']=='root':groups.append(group)
  else:lookup[part['parent']]['children'].append(group)
 bio=io.BytesIO();im.save(bio,format='PNG');write_json(ROOT/'model-source/dungeon'/f'{ident}.bbmodel',dict(meta=dict(format_version='4.10',model_format='free',box_uv=False),name=name,resolution=dict(width=128,height=128),elements=elements,outliner=groups,textures=[dict(name=ident+'.png',id='0',source='data:image/png;base64,'+base64.b64encode(bio.getvalue()).decode())]))

theme_colors={'desert':0x66dcd0,'crypt':0xefad69,'tide':0x48c7d5,'astral':0xa9bcf1}
for theme,c in theme_colors.items():
 im=Image.new('RGBA',(16,64));rs=random.Random(theme)
 for frame in range(4):
  for y in range(16):
   for x in range(16):
    glow=.65+.25*math.sin((x+y+frame*3)*.7);star=((x*13+y*7+frame*3)%61)==0;col=(235,255,250) if star else tuple(int(v*glow) for v in rgb(c));im.putpixel((x,y+frame*16),(*col,205 if star else 115))
 path=AS/'textures/block'/f'dungeon_gate_{theme}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path);write_json(Path(str(path)+'.mcmeta'),dict(animation=dict(frametime=5)))
 model=dict(render_type='minecraft:translucent',textures={'portal':f'tnc:block/dungeon_gate_{theme}','particle':f'tnc:block/dungeon_gate_{theme}'},elements=[dict(from_=[0,0,7],to=[16,16,9],faces={n:dict(texture='#portal',uv=[0,0,16,16]) for n in ['north','south']})]);model['elements'][0]['from']=model['elements'][0].pop('from_');write_json(AS/'models/block'/f'dungeon_gate_{theme}.json',model);write_json(AS/'blockstates'/f'dungeon_gate_{theme}.json',{'variants':{'':{'model':f'tnc:block/dungeon_gate_{theme}'}}});write_json(AS/'models/item'/f'dungeon_gate_{theme}.json',dict(parent=f'tnc:block/dungeon_gate_{theme}'))
for kind,c in [('console',0x4f707c),('cache',0x8e643d),('waystone',0x486778)]:
 im=Image.new('RGBA',(16,16));rs=random.Random(kind)
 for y in range(16):
  for x in range(16):im.putpixel((x,y),(*shade(c,rs.choice([.75,.9,1,1.15])),255))
 dr=ImageDraw.Draw(im);dr.rectangle((1,1,14,14),outline='#c2a66b');dr.line((3,8,12,8),fill='#78e3da');dr.line((8,3,8,12),fill='#78e3da');im.save(AS/'textures/block'/f'dungeon_{kind}.png')
 elements=[]
 for low,high in {'console':[([2,0,2],[14,3,14]),([5,3,5],[11,10,11]),([1,10,1],[15,12,15])],'cache':[([1,0,2],[15,9,14]),([1,9,2],[15,13,14]),([7,6,1],[9,10,3])],'waystone':[([2,0,2],[14,3,14]),([5,3,5],[11,13,11]),([4,13,4],[12,15,12])]}[kind]:elements.append({'from':low,'to':high,'faces':{n:dict(texture='#all') for n in ['up','down','north','south','east','west']}})
 write_json(AS/'models/block'/f'dungeon_{kind}.json',dict(textures={'all':f'tnc:block/dungeon_{kind}','particle':f'tnc:block/dungeon_{kind}'},elements=elements));write_json(AS/'blockstates'/f'dungeon_{kind}.json',{'variants':{'':{'model':f'tnc:block/dungeon_{kind}'}}})
for ident,c in [('dungeon_handbook',0x486c8f),('sunwheel_shard',0xd9ad55),('old_bell_bronze',0xb28d51),('tidal_shell_crystal',0x56b9b4),('star_dream_shard',0x99b6e4),('dungeon_memento',0x9877a5)]:
 im=Image.new('RGBA',(24,24));dr=ImageDraw.Draw(im)
 if ident=='dungeon_handbook':dr.polygon([(5,3),(20,2),(20,20),(5,22),(2,19),(2,5)],fill='#171f34',outline='#bba36d');dr.rectangle((6,4,18,18),fill='#425b83');dr.ellipse((9,7,16,14),outline='#d3c79d',width=2)
 elif ident=='old_bell_bronze':dr.polygon([(8,4),(16,4),(17,15),(21,18),(3,18),(7,15)],fill='#66502f',outline='#d1b46d');dr.ellipse((10,17,14,21),fill='#a98247')
 elif ident=='tidal_shell_crystal':
  for i in range(6):dr.arc((3+i,3+i,21-i,21-i),i*20,290,fill=(*shade(c,1-i*.07),255),width=2)
 elif ident=='dungeon_memento':dr.rectangle((6,2,18,22),fill='#423442',outline='#b4a27b');dr.polygon([(12,5),(16,12),(12,18),(8,12)],fill='#9a8ab5');dr.line((8,19,16,19),fill='#d3bc7f')
 else:dr.polygon([(12,1),(18,8),(17,18),(10,23),(5,15),(7,6)],fill=(*shade(c,.55),255),outline=(*shade(c,1.2),255));dr.polygon([(12,3),(15,9),(10,18),(9,10)],fill=(*rgb(c),255))
 path=AS/'textures/item'/f'{ident}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path);write_json(AS/'models/item'/f'{ident}.json',dict(parent='minecraft:item/generated',textures={'layer0':f'tnc:item/{ident}'}))
for ident,name,fam,role,skin,accent in species:write_json(AS/'models/item'/f'{ident}_spawn_egg.json',dict(parent='minecraft:item/template_spawn_egg'))
for lang in ['zh_cn','en_us']:
 p=AS/'lang'/f'{lang}.json';v=json.loads(p.read_text('utf8'))
 for ident,name,*_ in species:v[f'entity.tnc.{ident}']=name if lang=='zh_cn' else ident.replace('_',' ').title();v[f'item.tnc.{ident}_spawn_egg']=(name+'刷怪蛋') if lang=='zh_cn' else ident.replace('_',' ').title()+' Spawn Egg'
 for t,n in [('desert','黄沙遗庭'),('crypt','锁钟地牢'),('tide','沉潮回廊'),('astral','星眠幻庭')]:v[f'block.tnc.dungeon_gate_{t}']=n+'界门' if lang=='zh_cn' else t.title()+' Dungeon Gate'
 for key,name in [('dungeon_console','回声星仪'),('dungeon_cache','遗庭宝匣'),('dungeon_waystone','归途界碑')]:v[f'block.tnc.{key}']=name if lang=='zh_cn' else key.replace('_',' ').title()
 for key,name in [('dungeon_handbook','门后的三十层'),('sunwheel_shard','日轮砂片'),('old_bell_bronze','旧钟铜'),('tidal_shell_crystal','潮纹贝晶'),('star_dream_shard','眠星碎片'),('dungeon_memento','地下城纪念物')]:v[f'item.tnc.{key}']=name if lang=='zh_cn' else key.replace('_',' ').title()
 write_json(p,v)
write_json(AS/'models/item/dungeon_bolt.json',dict(parent='minecraft:item/generated',textures={'layer0':'tnc:item/tidal_shell_crystal'}))
print(f'Generated {len(species)} monster atlases and Blockbench meshes / {len(SPECS)} distinct family skeletons / four gates')
