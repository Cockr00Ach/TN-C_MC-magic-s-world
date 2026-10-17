"""Native Minecraft cuboid plants. Existing pixel materials, never inventory sprites.

Roots are above y=0; no full-block foliage cubes. All ages have real geometry.
Only blockstate-referenced plant models are replaced; inventory icons stay intact.
"""
from pathlib import Path
import json, math, hashlib, re

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
FAMILIES={
 'homeward_flower':'flower','warning_moss':'moss','road_bell_crop':'grain',
 'night_gourd_crop':'gourd','tide_reed_crop':'reed','hushcap_mushroom':'mushroom',
 'rainletter_bush':'berries','mana_root':'root','verdant_vein':'crystal_tree','sky_vine':'vine',
 'dawn_disk':'sunflower','hearth_pepper':'pepper','mist_cotton':'cotton','stone_fern':'fern',
 'mirror_lotus':'lotus','wish_puff':'puff','echo_bean':'beans','ladder_vine':'vine',
 'frost_chime':'bells','salt_ink':'mushroom','wind_sail':'fan','sleep_clock':'spiral',
 'shadow_cut':'orchid','paper_tree':'paper_tree','flight_pod':'pod','honey_cluster':'cluster',
 'star_dew':'dew_vine','dance_bell':'bells','star_rest':'star',
 'dream_compost':'shelf_mushroom','thermal_foldleaf':'foldleaf','shadow_hour_orchid':'hourglass',
 'turnpage_fern':'page_fern','daynight_sandgrain':'twin_grain','hanging_dewgrass':'dew_grass',
 'prism_crown':'crystal_crown','ore_sleep_moss':'ore_moss','storm_crown':'storm',
}
MATERIALS={k:'tnc:block/art_'+v for k,v in {
 'leaf':'leaf','stem':'bark','gold':'pollen','white':'cotton','red':'pepper','blue':'ice',
 'purple':'violet','pink':'rose','paper':'paper','metal':'copper','stone':'iron','grain':'grain'
}.items()}
for texture in MATERIALS.values():
 assert (ASSETS/'textures'/(texture.split(':')[1]+'.png')).is_file(),texture

def dump(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def mesh(species,age):
 family=FAMILIES[species];seed=int(hashlib.sha256(species.encode()).hexdigest()[:6],16)
 elements=[]
 def box(x,y,z,w,h,d,material='leaf'):
  # Guard the soil seam and planted-cell footprint at authoring time.
  assert y>=0 and h>0 and w>0 and d>0,(species,x,y,z,w,h,d)
  faces={}
  for face in ('north','south','east','west','up','down'):
   a,b=(w,d) if face in ('up','down') else ((d,h) if face in ('east','west') else (w,h))
   faces[face]={'texture':'#'+material,'uv':[0,0,round(min(16,a),3),round(min(16,b),3)]}
  elements.append({'from':[round(x,3),round(y,3),round(z,3)],'to':[round(x+w,3),round(y+h,3),round(z+d,3)],'faces':faces})
 def stem(x,z,height,base=.2,width=.8):box(x-width/2,base,z-width/2,width,height,width,'stem')
 def leaf(x,y,z,length=3,side=1,mat='leaf'):
  box(x if side>0 else x-length,y,z-.7,length,.55,1.4,mat)
  box(x+side*(length-1)-.6,y+.45,z-.5,1.2,.4,1,mat)
 def orb(x,y,z,size,mat):
  box(x-size*.5,y,z-size*.35,size,size*.75,size*.7,mat)
  box(x-size*.35,y+size*.75,z-size*.25,size*.7,size*.3,size*.5,mat)
 color={'sunflower':'gold','flower':'pink','orchid':'purple','bells':'gold','hourglass':'blue',
        'star':'white','storm':'blue','mushroom':'purple','shelf_mushroom':'blue','pod':'blue',
        'cotton':'white','berries':'pink','pepper':'red','lotus':'pink','puff':'white'}.get(family,'gold')
 if age==0:
  # Germination follows the plant family instead of reusing one seed billboard.
  if family in ('mushroom','shelf_mushroom'):
   stem(8,8,1.7,.2,.8);box(6.5,1.9,6.5,3,1,3,color);box(7,2.9,7,2,.5,2,color)
   box(7.7,3.4,7.7,.6,.4,.6,'blue')
  elif family in ('moss','ore_moss'):
   box(6.4,.15,6.6,3.1,.45,2.5,'leaf');box(8.8,.2,8.1,1.7,.7,1.6,'leaf');orb(7.3,.65,7.4,.7,'blue')
  elif family in ('reed','grain','twin_grain','dew_grass'):
   for dx,height in [(-1,2.8),(.1,4),(1.2,2.3)]:stem(8+dx,8,height,.2,.4)
   box(7.8,4,7.8,.5,.55,.5,'blue')
  elif family in ('vine','dew_vine','beans','spiral','paper_tree','crystal_tree','crystal_crown','storm'):
   orb(8,.2,8,1.9,'stem');stem(8,8,2.6,.5,.55);leaf(8,2,8,1.8,-1)
   box(7.7,3.1,7.7,1.8,.5,.5,'blue');box(9,2.7,7.7,.5,.5,.5,'blue')
  else:
   orb(8,.2,8,1.8,'stem');stem(8,8,2.4,.5,.55)
   leaf(8,1.8,8,1.9,-1);leaf(8,2.35,8,1.6,1)
   box(7.65,3.1,7.65,.7,.65,.7,'blue' if seed%2 else 'gold')
  return elements
 grow=age/3;h=4+grow*(7+seed%3);leaf_len=2+grow*2
 if family in ('moss','ore_moss'):
  for x,z,size in [(5,5,2.6),(9,5.8,3.1),(6.8,9,3.8),(10.5,10.2,2.3)]:
   box(x,.15,z,size,.5+grow*.5,size*.6,'leaf')
   if age>=2:orb(x+size*.5,.8,z+size*.3,.65+grow*.5,'blue' if family=='moss' else 'stone')
 elif family in ('mushroom','shelf_mushroom'):
  for i,(x,z,k) in enumerate([(8,8,1),(4.4,6.2,.58),(11.6,9.5,.68)]):
   if i>0 and age<2:continue
   top=1+h*.55*k;stem(x,z,top-.3,.2,1.1*k)
   width=(3+3*grow)*k
   box(x-width*.4,top,z-width*.4,width*.8,1,width*.8,'white')
   box(x-width*.5,top+1,z-width*.5,width,1.2*k,width,color)
   box(x-width*.3,top+2,z-width*.3,width*.6,.7*k,width*.6,color)
   if age==3:box(x-.4,top+2.75,z-.4,.8,.4,.8,'gold')
 elif family in ('reed','grain','twin_grain','dew_grass','dew_vine'):
  count=3 if age==1 else 5
  for i in range(count):
   x=4.5+i*1.65;z=7+(i%2)*2-1;top=h+(i%3)*1.2;stem(x,z,top,.2,.5)
   leaf(x,top*.45,z,2.4,-1 if i%2 else 1)
   if family in ('dew_grass','dew_vine'):orb(x,top+.1,z,.8+grow,'blue')
   else:
    for j in range(3):box(x-.65,top-2+j*1.2,z-.7,1.3,.8,1.4,'blue' if family=='twin_grain' and i%2 else 'grain')
 elif family in ('fern','page_fern','fan','foldleaf','spiral'):
  stem(8,8,h*.75)
  for row in range(3 if age==3 else 2):
   y=1.5+row*2.3;length=max(1.3,leaf_len-row*.55)
   for side in [-1,1]:
    material='paper' if family=='page_fern' else ('blue' if family=='foldleaf' and side>0 else 'leaf')
    for j in range(3):
     x=8+side*(j+1)*length/3
     box(x-.5,y+j*.55,7-(row%2)*.4,1.2,.6,2.1,material)
     if family=='fan':box(x-.45,y+j*.55+.6,6.7,.9,1.2,2.7,'leaf')
  if family=='spiral':
   for j in range(7):
    a=j*.7;box(7.5+math.cos(a)*1.4,h*.65+j*.35,7.5+math.sin(a)*1.4,1,.6,1,'gold')
 elif family in ('root','gourd','berries','pepper','beans','pod','cluster','cotton'):
  stem(8,8,h*.8)
  for i,side in enumerate([-1,1,-1,1]):
   y=2+i*1.65;leaf(8,y,8,leaf_len,side)
   if age>=2:
    x=8+side*(leaf_len-.4);size=(2.4 if family in ('gourd','cotton','pod') else 1.5)*grow
    orb(x,y+.7,8,size,'white' if family=='cotton' else 'blue' if family in ('root','pod') else color)
    if family=='beans':box(x-.4,y-1,7.5,.8,2.2,1,'leaf')
  if family=='root':orb(8,.2,8,3.4,'blue')
 elif family=='crystal_crown':
  stem(8,8,3+grow*3,.2,1)
  for i in range(5):
   a=i*math.tau/5;xx=8+math.cos(a)*2.7;zz=8+math.sin(a)*2.7
   box(min(8,xx),1.2,min(8,zz),abs(xx-8)+.7,.65,abs(zz-8)+.7,'leaf')
   if age>=2:
    hh=(3.5+i%3)*grow;mat=['blue','pink','gold','white','purple'][i]
    box(xx-.65,3.2,zz-.65,1.3,hh,1.3,mat);box(xx-.35,3.2+hh,zz-.35,.7,.7,.7,mat)
  if age==3:orb(8,6.5,8,2.2,'blue')
 elif family=='storm':
  stem(8,8,5+grow*6,.2,.65)
  for i in range(3):
   y=2+i*2.2;leaf(8,y,8,3-i*.4,-1 if i%2 else 1,'blue')
  if age>=2:
   for i in range(5):
    xx=6.6+(i%2)*1.5;yy=7.5+i*.95
    box(xx,yy,7.6,2,.65,.8,'blue');box(xx,yy,7.6,.55,1.3,.8,'blue')
   for xx,zz,yy,w in [(5.2,7.2,12,2.6),(8,6.9,13,3),(10.5,8,11.8,2)]:
    box(xx,yy,zz,w,.65,1.8,'white')
  if age==3:box(7.75,14,7.75,.5,1.2,.5,'gold')
 elif family in ('vine','paper_tree','crystal_tree'):
  stem(8,8,h, .2,1 if family=='paper_tree' else .8)
  for i,side in enumerate([-1,1,-1,1]):
   y=2+i*2;length=2+grow*(1+i%2);leaf(8,y,8,length,side,'paper' if family=='paper_tree' else 'leaf')
   if age>=2 and family in ('crystal_tree','crystal_crown','storm'):
    x=8+side*length;box(x-.5,y+.8,7.4,1,2+grow,1.2,'blue' if i%2 else 'gold')
  if family=='storm':
   for i in range(4):box(7.5+(i%2)*.8,h-1+i*.9,7.6,1,.9,.8,'blue')
  if family=='crystal_crown' and age==3:
   for i in range(5):
    a=i*math.tau/5;box(7.4+math.cos(a)*3,h-2,7.4+math.sin(a)*3,1.2,3.2,1.2,'blue' if i%2 else 'gold')
 elif family=='lotus':
  for i in range(5):
   a=i*math.tau/5;box(6+math.cos(a)*2.3,.15,6+math.sin(a)*2.3,4,.5,4,'leaf')
  stem(8,8,2+grow*2)
  if age>=2:
   for i in range(6):
    a=i*math.tau/6;box(7+math.cos(a)*2,3+grow,7+math.sin(a)*2,2,1.2,2,'pink' if i%2 else 'white')
   orb(8,4.3,8,1.5,'gold')
 else:
  stem(8,8,h);leaf(8,2.7,8,leaf_len,-1);leaf(8,5.1,8,leaf_len-1,1)
  if age>=2:
   if family=='bells':
    box(5.3,h-1,7.6,5.4,.65,.8,'stem')
    for i,x in enumerate([5.5,8,10.5]):
     y=h-2.6-(i%2)*.7;box(x-.8,y,7.2,1.6,1.7,1.6,color);box(x-1.05,y-.5,6.95,2.1,.5,2.1,color)
   elif family=='hourglass':
    for j,w in enumerate([2.8,1.6,.8,1.6,2.8]):box(8-w/2,h-2+j*.7,8-w/2,w,.7,w,'blue')
    box(6.2,h-2.5,6.2,3.6,.5,3.6,'gold');box(6.2,h+1.5,6.2,3.6,.5,3.6,'gold')
   elif family=='sunflower':
    for i in range(8):
     a=i*math.tau/8;box(7+math.cos(a)*2.4,h-1+math.sin(a)*2.4,7.5,2,2,.7,'gold')
    box(6.5,h-1.5,7.3,3,3,1.2,'stem')
   elif family=='puff':
    orb(8,h,8,4,'white')
    for x,z in [(5,7),(11,8),(8,11)]:box(x,h+2,z,.55,.7,.55,'blue')
   else:
    petals=5 if family=='orchid' else 6
    for i in range(petals):
     a=i*math.tau/petals;box(7.1+math.cos(a)*2.3,h+math.sin(a)*.8,7.1+math.sin(a)*2.3,1.8,1,1.8,color)
    orb(8,h+.5,8,1.3,'gold' if family!='star' else 'blue')
 return elements

def model_refs(value):
 if isinstance(value,dict):
  if isinstance(value.get('model'),str):yield value['model']
  for child in value.values():yield from model_refs(child)
 elif isinstance(value,list):
  for child in value:yield from model_refs(child)

def main():
 count=0;report=[]
 for species,family in FAMILIES.items():
  path=ASSETS/'blockstates'/(species+'.json')
  if not path.exists():continue
  data=json.loads(path.read_text(encoding='utf-8-sig'))
  variants=data.get('variants',{})
  max_age=max([int(m.group(1)) for key in variants for m in re.finditer(r'(?:^|,)age=(\d+)',key)] or [3])
  handled=set()
  for key,value in variants.items():
   match=re.search(r'(?:^|,)age=(\d+)',key)
   age=min(3,round(int(match.group(1))*3/max_age)) if match else (3 if 'true' in key else 1)
   for name in model_refs(value):
    if not name.startswith('tnc:') or name in handled:continue
    handled.add(name);elements=mesh(species,age)
    if species=='warning_moss':
     # This species attaches to a wall: rotate root plane onto the backing face.
     mapping={'north':'down','south':'up','up':'north','down':'south','east':'east','west':'west'}
     for element in elements:
      a,b=element['from'],element['to'];element['from']=[a[0],a[2],round(16-b[1],3)];element['to']=[b[0],b[2],round(16-a[1],3)]
      element['faces']={mapping[face]:value for face,value in element['faces'].items()}
    dump(ASSETS/'models'/(name.split(':')[1]+'.json'),{'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':dict(MATERIALS,particle=MATERIALS['leaf']),'elements':elements})
    count+=1
  report.append({'id':species,'family':family,'models':len(handled)})
 # Keep distinct, useful logistics equipment: cargo crate rather than another dish.
 def crate_box(a,b,texture):return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['up','down','north','south','east','west']}}
 dump(ASSETS/'models/block/pasture_tray.json',{'parent':'minecraft:block/block','textures':{'wood':'tnc:block/art_wood','iron':'tnc:block/art_iron','particle':'tnc:block/art_wood'},'elements':[
  crate_box([1,.1,1],[15,1.2,15],'wood'),crate_box([1,1.2,1],[2.2,11,15],'wood'),crate_box([13.8,1.2,1],[15,11,15],'wood'),crate_box([2.2,1.2,1],[13.8,11,2.2],'wood'),crate_box([2.2,1.2,13.8],[13.8,11,15],'wood'),crate_box([.8,2,.8],[15.2,3,2.4],'iron'),crate_box([.8,8,.8],[15.2,9,2.4],'iron')]})
 out=ROOT/'work/magic-plant-world-models-20261017.json';out.parent.mkdir(exist_ok=True)
 dump(out,{'species':report,'model_count':count,'inventory_icons_modified':False,'min_root_y':.1})
 print('Rebuilt',count,'native plant models for',len(report),'species')

if __name__=='__main__':main()
