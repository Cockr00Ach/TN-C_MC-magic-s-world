from pathlib import Path
import json,math,copy,shutil,hashlib,importlib.util
root=Path('D:/ModTest');base=root/'work/wind-earth-source-baseline';resources=root/'src/main/resources'
changed=[]
def save(p,d):
 if p.exists():
  old=base/p.relative_to(root)
  if not old.exists():old.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,old)
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');changed.append(str(p.relative_to(root)).replace('\\','/'))
def box(frm,to,mat='stone',rotation=None):
 e={'from':frm,'to':to,'faces':{f:{'uv':[0,0,16,16],'texture':'#'+mat} for f in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']={'origin':[8,8,8],'axis':rotation[0],'angle':rotation[1],'rescale':False}
 return e
textures={'soil':'minecraft:block/coarse_dirt','stone':'minecraft:block/stone','dark':'minecraft:block/deepslate','vein':'minecraft:block/sandstone','moss':'minecraft:block/moss_block','particle':'minecraft:block/stone'}
models={}
models['earth_clod']=[box([3,4,3],[12,12,13],'soil',('y',22.5)),box([2,5,6],[6,10,11],'soil'),box([9,3,4],[13,9,10],'stone'),box([6,10,5],[10,13,9],'soil')]
orb=[]
for axis in ['x','y','z']:
 orb.append(box([3,3,3],[13,13,13],'stone',(axis,45)))
for x,y,z in [(3,4,7),(9,9,4),(7,3,10),(4,10,7)]:orb.append(box([x,y,z],[x+3,y+3,z+3],'dark'))
for i in range(4):orb.append(box([2.9+i*2.5,6+i*.25,7.6],[3.15+i*2.5,10+i*.25,8.1],'vein'))
models['earth_orb']=orb
shell=[box([4,4,1],[12,12,14],'dark',('z',45)),box([3,5,3],[13,11,14],'stone')]
for i in range(5):
 half=4-i*.7;z=13+i*1.6;shell.append(box([8-half,8-half,z],[8+half,8+half,z+1.7],'stone' if i%2 else 'dark'))
for x,y in [(3.8,7.7),(11.5,7.7),(7.7,3.8),(7.7,11.5)]:shell.append(box([x,y,2],[x+.7,y+.7,13.4],'vein'))
models['earth_shell']=shell
salvo=[]
for x,y,z,a in [(-3,2,3,-22.5),(7,5,1,22.5),(2,9,8,45)]:
 salvo += [box([x,y,z],[x+7,y+5,z+12],'stone',('z',a)),box([x+1,y+1,z+11],[x+6,y+4,z+15],'dark'),box([x+3,y-.1,z+2],[x+3.5,y+.3,z+12],'vein')]
models['earth_salvo']=salvo
spear=[box([6.7,6.7,-10],[9.3,9.3,11],'dark',('z',45)),box([6.5,6.5,-8],[9.5,9.5,-6],'vein'),box([5.5,5.5,6],[10.5,10.5,8],'vein',('z',45))]
for i in range(7):
 half=4.7-i*.6;z=9+i*2.0;spear.append(box([8-half,8-half,z],[8+half,8+half,z+2.1],'stone' if i%2 else 'dark',('z',45)))
for side in [0,1,2,3]:
 a=side*math.pi/2;x=8+math.cos(a)*2.8;y=8+math.sin(a)*2.8;spear.append(box([x-.22,y-.22,6],[x+.22,y+.22,13],'vein'))
models['earth_spear']=spear
for name,elements in models.items():
 assert len(elements)<=28
 save(resources/'assets/tnc/models/projectile'/f'{name}.json',{'ambientocclusion':True,'textures':textures,'elements':elements,'display':{}})
# Refine only the code-native crescent mesh. Keep the existing bitmap untouched.
spec=importlib.util.spec_from_file_location('wind_model',root/'tools/gen_wind_blade_model.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
mod.SEGMENTS=32
old=json.loads((resources/'assets/tnc/models/projectile/windblade.json').read_text())
old['elements']=mod.recenter(mod.build_boxes())
save(resources/'assets/tnc/models/projectile/windblade.json',old)
# Feathered silhouette, with unchanged existing atlas and animation bones.
p=resources/'assets/tnc/geo/entity/wind_sprite.geo.json';geo=json.loads(p.read_text());bones=geo['minecraft:geometry'][0]['bones']
for bone in bones:
 if bone['name'] in ['wing_left','wing_right']:
  left=bone['name']=='wing_left';cubes=[]
  for i in range(5):
   length=5.7-i*.6; x=4+i*.55 if left else -4-i*.55-length
   cubes.append({'origin':[round(x,3),4+i*.65,-.8],'size':[round(length,3),1.2,1.6],'uv':[16,0],'pivot':[3 if left else -3,6,0],'rotation':[0,0,(-1 if left else 1)*i*5],'mirror':not left})
  bone['cubes']=cubes
 elif bone['name']=='tail':
  bone['cubes']=[{'origin':[-1.4+i*.9,3-i*.25,-7.5-i*.6],'size':[.9,1.1,4.5],'uv':[32,0],'pivot':[0,4,0],'rotation':[12,(-1+i)*8,0]} for i in range(3)]
 elif bone['name']=='crest':
  bone['cubes']=[{'origin':[-1,9,-1],'size':[2,1.6,2],'uv':[0,20]},{'origin':[-.55,10.4,-.6],'size':[1.1,1.5,1.2],'uv':[0,20],'pivot':[0,10,0],'rotation':[0,0,12]}]
save(p,geo)
# Change presentation fields only, never action coefficients, radius, costs, or cooldowns.
spellnames={r[0] for r in json.loads((root/'work/wind-earth-spell-specs.json').read_text())}
paths=list((resources/'data/tnc/spells').glob('*.json'))+list((root/'modpack').glob('*/kubejs/data/tnc/spells/*.json'))
earthModels=dict(zip(['earth_shot','earth_ball','rock_cannon','ten_thousand_rocks','earth_god_spear'],models))
for p in paths:
 if p.stem not in spellnames:continue
 d=json.loads(p.read_text(encoding='utf-8-sig'));earth=d['group'].startswith('earth');tier=d['learn']['tier']
 def polish(v,path=()):
  if isinstance(v,dict):
   for key,item in list(v.items()):
    if key in ['particles','travel_particles'] and isinstance(item,list):
     for batch in item:
      if not isinstance(batch,dict):continue
      before=batch.get('count',0)
      presence='client_data' in path or key=='travel_particles'
      batch['count']=min(before,(2 if tier<4 else 3) if presence else (3+tier if earth else 4+tier))
      batch['min_speed']=min(batch.get('min_speed',0),.035 if presence else .08)
      batch['max_speed']=min(batch.get('max_speed',0),.08 if presence else .35)
      batch['particle_id']='minecraft:poof' if earth else 'minecraft:cloud'
    elif key=='show_particles':v[key]=False
    else:polish(item,path+(key,))
  elif isinstance(v,list):
   for item in v:polish(item,path)
 polish(d)
 if p.stem in earthModels:
  model=d['release']['target']['projectile']['projectile']['client_data']['model']
  model['model_id']='tnc:projectile/'+earthModels[p.stem];model['orientation']='TOWARDS_MOTION';model['light_emission']='NONE'
 save(p,d)
(root/'work/wind-earth-asset-paths.json').write_text(json.dumps(changed,ensure_ascii=False,indent=2),encoding='utf-8')
print('Refined assets:',len(changed),'Five earth models:',[(n,len(v)) for n,v in models.items()])
