"""World-only V3 plants: distinct botanical silhouettes, tinted native pixel materials.
Inventory art is deliberately untouched. All stages stay above their soil seam.
"""
from pathlib import Path
import json,math,re,hashlib
from rebuild_magic_plant_world_models import FAMILIES,model_refs,dump,ASSETS,ROOT
# Stem, foliage, petals, secondary petals, emissive nectar; coherent per-species palettes.
COLORS=[
('homeward_flower','305E66 58B7A5 F19089 FFE5B3 9FFFF2'),('warning_moss','35564C 64A78A 7CDDE9 B5D2B9 C5FFF0'),
('road_bell_crop','633C56 A9844B DCB567 924755 FFE6AA'),('night_gourd_crop','383047 625281 B378C5 E4A9DF D9C4FF'),
('tide_reed_crop','325C68 6FA9BD A6D9DF D7E6C6 B8FFFF'),('hushcap_mushroom','513C62 8B6898 C59FC7 E6C7CD BFC0FF'),
('rainletter_bush','314D79 638EC0 C2D9E8 A288A5 FFE7B7'),('mana_root','3B6E5B 7DBB99 C3E6C0 6CB4BD A5FFF0'),
('verdant_vein','325F53 65B5A0 8CE2C1 D0E9A0 90FFF3'),('sky_vine','2D5F57 559E83 E998A6 E8C994 B1FFE2'),
('dawn_disk','825D4B C69265 F5C878 F18F88 FFEEC2'),('hearth_pepper','583343 8A5264 D77567 F2AE69 FFD5AE'),
('mist_cotton','3E6972 7DAEB7 C3BCDF E4D6E6 D9FFFF'),('stone_fern','3A414E 6D8092 98B9A7 CDBB80 B8DAFF'),
('mirror_lotus','394768 788DBD C999D5 E1BCE4 B9FAFF'),('wish_puff','4D7A76 9BBEB0 E9EAD6 E7BAC6 C8FFFF'),
('echo_bean','414F7A 7C87BF A4A0D5 9ACABF DED9FF'),('ladder_vine','46664C 83AA68 BCD580 80C7AE E1FFC0'),
('frost_chime','354368 6C88B0 93C9E9 D3D8EB C7FFFF'),('salt_ink','64465D A46C88 D2A1B2 AEC0B8 F6D3EF'),
('wind_sail','426B74 72A89C EAA4A0 EADAC4 E4FFFF'),('sleep_clock','3F4268 7A7EA8 D3AA70 E0C799 D1C8FF'),
('shadow_cut','343442 695673 A87C9C C4ABC8 E8C6FF'),('paper_tree','593F56 986D85 E8D3B2 E5B0BB FFF3DA'),
('flight_pod','495C78 849DB1 DFA0BC C6BAD9 C1FFFF'),('honey_cluster','576848 99AA68 E1BF69 E9D99C E7FFD6'),
('star_dew','3C5C7B 769DBC B0D4DF E0D8EC DAFFFF'),('dance_bell','5B4770 9B74B4 DE9FC8 E9C091 FFE2FF'),
('star_rest','454D75 8A95BB CCD2E8 A8C8CD F6FFFF'),('dream_compost','3A665F 77ABA0 D9988F E6C1B0 B4FFEA'),
('thermal_foldleaf','55576B 888CA7 D98296 80B8D1 D6F9FF'),('shadow_hour_orchid','373343 74618B B9A2CD E3D5E9 F6E2FF'),
('turnpage_fern','5D536A A293AE E9DAC6 BCB9DB FFF4D7'),('daynight_sandgrain','66536A A5907D E1C382 B2B8D6 FFEFD2'),
('hanging_dewgrass','3B736F 72B4A4 A4DBD1 B7BADD C9FFFF'),('prism_crown','4F596D 859DA3 D7ABD9 9DD7CB FFFFF0'),
('ore_sleep_moss','465F58 769C81 B89C6B BA879F D0FFDB'),('storm_crown','35495A 6A91A5 A8C4CD D9C588 F3FFFF')]
PALETTES={k:[int(v,16) for v in c.split()] for k,c in COLORS}
TEXTURES={'stem':'tnc:block/art_cotton','leaf':'tnc:block/art_cotton','petal':'tnc:block/art_cotton','edge':'tnc:block/art_cotton','glow':'tnc:block/art_cotton','particle':'tnc:block/art_leaf'}

def mesh(species,age):
 e=[];family=FAMILIES[species];seed=int(hashlib.sha256(species.encode()).hexdigest()[:6],16);g=(age+1)/4
 def box(x,y,z,w,h,d,mat=1,rotation=None):
  assert y>=0 and min(w,h,d)>0,(species,y)
  faces={f:{'texture':'#'+['stem','leaf','petal','edge','glow'][mat],'tintindex':mat,'uv':[0,0,min(16,w if f not in ['east','west'] else d),min(16,h if f not in ['up','down'] else d)]} for f in ['north','south','east','west','up','down']}
  out={'from':[round(v,3) for v in [x,y,z]],'to':[round(v,3) for v in [x+w,y+h,z+d]],'faces':faces}
  if rotation:out['rotation']={'origin':[8,1,8],'axis':'y','angle':rotation}
  e.append(out)
 def stem(x,z,h,base=.15,w=.65):box(x-w/2,base,z-w/2,w,h,w,0)
 def leaf(x,y,z,length,side=1,mat=1):
  for j in range(3):box(x+side*j*length/3-(length/3 if side<0 else 0),y+j*.3,z-(1.4-j*.25)/2,length/3+.2,.35,1.4-j*.25,mat)
 def orb(x,y,z,size,mat=2):
  box(x-size*.45,y,z-size*.3,size*.9,size*.6,size*.6,mat);box(x-size*.3,y+size*.6,z-size*.45,size*.6,size*.35,size*.9,mat)
 def ring(x,y,z,r,mat=3,n=12,vertical=False):
  for i in range(n):
   a=i*math.tau/n;xx=x+math.cos(a)*r;yy=y+math.sin(a)*r if vertical else y;zz=z if vertical else z+math.sin(a)*r
   box(xx-.3,max(.1,yy),zz-.3,.6,.45,.6,mat)
 def crystal(x,y,z,h,mat=2):
  box(x-.65,y,z-.65,1.3,h*.65,1.3,mat);box(x-.4,y+h*.65,z-.4,.8,h*.25,.8,mat);box(x-.18,y+h*.9,z-.18,.36,h*.12,.36,4)
 h=3+age*(2.4+seed%3*.35)
 if age==0:
  # Visible sprouts show species shape already, never a flat seed texture.
  if family in ['moss','ore_moss','mushroom','shelf_mushroom']:
   for x,z,w in [(6.2,6.8,2.4),(9,8.4,1.6)]:box(x,.15,z,w,.5,w,1);orb(x+w/2,.65,z+w/2,w*.55,2)
  elif family in ['grain','twin_grain','reed','dew_grass']:
   for i in range(3):stem(6.5+i*1.5,8,2+i*.7,w=.4);orb(6.5+i*1.5,2.2+i*.7,8,.65,3)
  else:
   stem(8,8,2.5);leaf(8,1.1,8,2.6,-1);leaf(8,2,8,2.1,1);orb(8,2.8,8,1.3,2)
  box(7.7,3.8,7.7,.55,.5,.55,4);return e
 # Full plants start with a rooted, asymmetrical foliage cluster.
 low=family in ['moss','ore_moss','lotus','root','shelf_mushroom']
 if not low:
  stem(8,8,h,w=.8 if family in ['paper_tree','crystal_tree'] else .6)
  for row in range(2+age):
   a=(row%2)*math.pi+seed*.01;xx=8+math.cos(a)*.5;zz=8+math.sin(a)*.5
   leaf(xx,1+row*1.35,zz,2.1+(row%3)*.5,-1 if row%2 else 1)
 if family in ['grain','twin_grain','reed','dew_grass','dew_vine']:
  for i in range(5):
   x=4.5+i*1.7;z=6+(i%3);top=h+(i%3)*.8;stem(x,z,top,w=.35)
   leaf(x,top*.4,z,2.2,-1 if i%2 else 1)
   if family in ['dew_grass','dew_vine']:orb(x,top,z,1.5*g,2)
   else:
    for j in range(4):box(x-.7+(j%2)*.3,top-2+j*.7,z-.45,1.1,.55,.9,2 if i%2 else 3)
  if species=='tide_reed_crop':ring(8,1.1,8,3.7,3,12)
  if species=='daynight_sandgrain':ring(8,h+1,8,1.7,3,10,True)
 elif family in ['moss','ore_moss']:
  for i in range(8):
   a=i*2.4;r=1.5+i*.35;x=8+math.cos(a)*r;z=8+math.sin(a)*r;box(x-1,.15,z-1,2.6,.6+(i%2)*.4,2.4,1)
   if age>=2:crystal(x,1,z,1.3+(i%3)*.6,3 if i%2 else 2)
  if family=='ore_moss':orb(8,.5,8,3.5,0);ring(8,3.3,8,2.7,4,8)
 elif family in ['mushroom','shelf_mushroom']:
  for i in range(3):
   x,z=([8,8] if i==0 else [4.5+i*3,6+i]);top=2+h*.35-i*.7;stem(x,z,top,w=.9)
   for layer,w in enumerate([5.2,6.6,5.4,3.6]):box(x-w/2,top+layer*.55,z-w*.4,w,.55,w*.8,2 if layer%2 else 3)
   for j in range(4):box(x-2+j*1.25,top-.45,z-1.4,.22,.45,2.8,4)
  if species=='salt_ink':orb(8,h+1.2,8,2,3)
  if species=='dream_compost':ring(8,h+1.7,8,2.7,4,9)
 elif family in ['fern','page_fern','fan','foldleaf','spiral']:
  for row in range(3+age):
   y=1+row*1.55;length=max(1.5,5-row*.55)
   for side in [-1,1]:
    for j in range(4):box(8+side*(j+1)*length/4-.45,y+j*.55,6.7,.85,.35,2.6-(j*.3),2 if family=='page_fern' or family=='foldleaf' and side>0 else 1,22.5 if row%2 else -22.5)
   if family=='fan':box(7.6,y,7.7,.8,1.7,3.8,3)
  if family=='spiral':
   for i in range(18):
    a=i*.48;box(7.7+math.cos(a)*2,h-1+i*.12,7.7+math.sin(a)*2,.55,.5,.55,2)
  if family=='foldleaf':crystal(5,h+1,8,2.2,2);crystal(11,h+1,8,2.2,3)
  if family=='page_fern':ring(8,h+1,8,2.1,4,8,True)
 elif family in ['gourd','root','berries','pepper','beans','pod','cluster','cotton']:
  count=2+age
  for i in range(count):
   a=i*2.4;xx=8+math.cos(a)*3;zz=8+math.sin(a)*3;yy=2+i*1.45
   leaf(8,yy,zz,abs(xx-8),-1 if xx<8 else 1)
   if age>=2:
    if family=='cotton':
     for j in range(3):orb(xx+(j-1)*.7,yy+.5+(j%2)*.6,zz,2.5,2 if j%2 else 3)
    elif family=='beans':
     for j in range(3):orb(xx,yy-j*.6,zz,1.2,2);box(xx-.15,yy-1.4,zz-.15,.3,1.9,.3,4)
    elif family=='pepper':crystal(xx,yy,zz,2.7,2)
    else:orb(xx,yy,zz,(3.3 if family in ['gourd','pod'] else 1.8),2 if i%2 else 3)
  if family=='root':orb(8,.2,8,4,2);ring(8,3.5,8,2.6,4,10)
  if family=='pod':
   for side in [-1,1]:box(8+side*3-1,h,6,2,.4,5,3,side*22.5)
  if family=='cluster':
   for j in range(6):orb(6+j%3*1.6,h+j//3*1.4,8,1.7,2)
 elif family in ['paper_tree','crystal_tree','vine']:
  for i in range(6):
   a=i*math.pi/3;x=8+math.cos(a)*3;z=8+math.sin(a)*3;y=3+i*1.4
   box(min(x,8),y,min(z,8),abs(x-8)+.5,.5,abs(z-8)+.5,0)
   for j in range(3):box(x-1.5+j*.65,y+.6+j*.25,z-1.5,2.3,.4,3,2 if family=='paper_tree' else 1)
   if family=='crystal_tree':crystal(x,y+.6,z,2.7,2)
   if species=='sky_vine':orb(x,y+1,z,1.2,3)
  if family=='vine':
   for i in range(20):
    a=i*.6;box(7.7+math.cos(a)*1.3,1+i*.6,7.7+math.sin(a)*1.3,.65,.65,.65,0)
 elif family=='lotus':
  for layer in range(3):
   for i in range(8):
    a=i*math.tau/8+layer*.4;r=3.8-layer*1.1;x=8+math.cos(a)*r;z=8+math.sin(a)*r;box(x-1.4,.2+layer*1.5,z-1.4,2.8,.5+layer*.5,2.8,1 if layer==0 else 2 if i%2 else 3)
  orb(8,4.7,8,2.3,4);ring(8,6.8,8,3,3,16)
 elif family in ['crystal_crown','storm','hourglass','star','bells','sunflower','flower','orchid','puff']:
  top=h
  if family=='crystal_crown':
   for i in range(7):
    a=i*math.tau/7;crystal(8+math.cos(a)*3.4,3,8+math.sin(a)*3.4,4+(i%3)*1.5,2 if i%2 else 3)
   ring(8,9.7,8,3.7,4,14);orb(8,11,8,2,2)
  elif family=='storm':
   for j in range(4):
    x=6+(j%2)*2;box(x,top-3+j,7,2,.45,1,4);box(x,top-3+j,7,.45,1.4,1,4)
   for i in range(5):orb(5+i*1.4,top+1+(i%2),8,2.7,3)
  elif family=='hourglass':
   for j,w in enumerate([4,3,1.5,.8,1.5,3,4]):box(8-w/2,top-3+j*.65,8-w/2,w,.6,w,2 if j%2 else 3)
   ring(8,top-3.4,8,3,3,12);ring(8,top+1.8,8,3,3,12)
  elif family=='bells':
   box(3.8,top,7.7,8.4,.45,.6,0)
   for i in range(3):
    x=4.4+i*3.5;y=top-2.8-(i%2)*1.2;stem(x,8,2.7,y,w=.3)
    for j,w in enumerate([2.7,2.2,1.4]):box(x-w/2,y+j*.7,8-w/2,w,.7,w,2 if i%2 else 3)
    orb(x,y-.6,8,.75,4)
   if species=='dance_bell':ring(8,top+1.4,8,3.3,4,8)
  elif family=='sunflower':
   for layer in range(2):
    for i in range(10):
     a=(i+layer*.5)*math.tau/10;r=3.4-layer*.9;box(7+math.cos(a)*r,top+math.sin(a)*r,7.4-layer*.4,2,1.5,1.1,2 if layer==0 else 3)
   orb(8,top-.8,7.7,2.2,4)
  elif family=='puff':
   for i in range(18):
    a=i*2.4;r=1.4+(i%3)*.65;orb(8+math.cos(a)*r,top+math.sin(i)*1.2,8+math.sin(a)*r,1.1,2 if i%2 else 3)
   ring(8,top+3.4,8,3.6,4,9)
  else:
   for layer in range(2):
    for i in range(5+(seed%3)):
     a=i*math.tau/(5+seed%3)+layer*.5;r=2.8-layer*.7;x=8+math.cos(a)*r;z=8+math.sin(a)*r
     box(x-.8,top+layer*1.2+math.sin(a)*.5,z-.8,1.6,.8,1.6,2 if layer==0 else 3)
   orb(8,top+2,8,1.4,4)
   if family=='star':ring(8,top+3.2,8,3.5,3,10,True)
   if family=='orchid':
    for side in [-1,1]:box(8+side*3-.6,top-3,7.4,1.2,2.7,1.2,3)
 # Species-specific constellations, never a six-faced full block.
 if age==3 and seed%3==0:
  for i in range(3):box(4+i*3.4,h+2+(i%2),6+(i%2)*4,.4,.55,.4,4)
 return e

def main():
 count=0
 for species in FAMILIES:
  path=ASSETS/'blockstates'/(species+'.json');data=json.loads(path.read_text(encoding='utf-8-sig'));variants=data.get('variants',{});maximum=max([int(m.group(1)) for key in variants for m in re.finditer(r'(?:^|,)age=(\d+)',key)] or [3]);handled=set()
  for key,value in variants.items():
   m=re.search(r'(?:^|,)age=(\d+)',key);age=min(3,round(int(m.group(1))*3/maximum)) if m else (3 if 'true' in key else 1)
   for name in model_refs(value):
    if not name.startswith('tnc:') or name in handled:continue
    handled.add(name);elements=mesh(species,age)
    if species=='warning_moss':
     mapping={'north':'down','south':'up','up':'north','down':'south','east':'east','west':'west'}
     for element in elements:
      a,b=element['from'],element['to'];element['from']=[a[0],a[2],round(16-b[1],3)];element['to']=[b[0],b[2],round(16-a[1],3)];element['faces']={mapping[f]:v for f,v in element['faces'].items()}
    dump(ASSETS/'models'/(name.split(':')[1]+'.json'),{'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':TEXTURES,'elements':elements});count+=1
 dump(ASSETS/'plant_palettes.json',PALETTES)
 dump(ROOT/'work/arcane-plants-v3.json',{'species':38,'models':count,'palettes':38,'inventory_icons_modified':False})
 print('Rebuilt',count,'world models; 38 individual palettes')
if __name__=='__main__':main()
