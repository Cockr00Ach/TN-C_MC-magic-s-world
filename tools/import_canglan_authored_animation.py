from pathlib import Path
import json,copy,shutil,hashlib
ROOT=Path(__file__).resolve().parents[1];source=ROOT/'exports/canglan_warlord_animation_kit';backup=ROOT/'model-source/canglan_warlord/user-animation-20261022';backup.mkdir(parents=True,exist_ok=True)
for name in ['canglan_warlord_with_spear.editor.geo.json','canglan_warlord_with_spear.editor.animationnew.json','canglan_warlord_spear_editor.png']:
 target=backup/name
 if target.exists():assert target.read_bytes()==(source/name).read_bytes(),'Original author snapshot already exists and differs'
 else:shutil.copy2(source/name,target)
asset=ROOT/'src/main/resources/assets/tnc';geo=json.loads((source/'canglan_warlord_with_spear.editor.geo.json').read_text(encoding='utf-8-sig'))
g=geo['minecraft:geometry'][0];g['bones']=[b for b in g['bones'] if not b['name'].startswith('editor_spear_')];g['description']['texture_width']=256;g['description']['texture_height']=256
for bone in g['bones']:bone.setdefault('cubes',[])
for filename in ['canglan_warlord.geo.json','canglan_echo.geo.json']:
 result=copy.deepcopy(geo)
 if 'echo' in filename:result['minecraft:geometry'][0]['description']['identifier']='geometry.tnc.canglan_echo'
 (asset/'geo/entity'/filename).write_text(json.dumps(result,separators=(',',':'),ensure_ascii=False)+'\n',encoding='utf-8')
path=asset/'animations/entity/canglan_warlord.animation.json';data=json.loads((backup/'runtime_before.animation.json').read_text(encoding='utf-8-sig'));clips=data['animations'];before=copy.deepcopy(clips)
base={'arm_-1':[0,0,2],'arm_1':[0,0,-2],'fore_1':[-55,0,0],'weapon_1':[110,0,-8]}
for clip in clips.values():
 for name,offset in base.items():
  channel=clip.get('bones',{}).get(name,{}).get('rotation')
  if isinstance(channel,dict):
   for t,value in channel.items():
    if isinstance(value,list):channel[t]=[round(v-d,6) for v,d in zip(value,offset)]
author=json.loads((source/'canglan_warlord_with_spear.editor.animationnew.json').read_text(encoding='utf-8-sig'))['animations']
for source_name,target_name in [('突刺','thrust'),('释放武器链t4t5','sky_arsenal'),('横扫','sweep')]:
 clip=copy.deepcopy(author[source_name]);clip['loop']=False
 if target_name=='thrust':clip['bones']['root'].pop('position')
 clips[target_name]=clip
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(backup/'integration.json').write_text(json.dumps({'clips':{'突刺':'thrust','释放武器链t4t5':'sky_arsenal','横扫':'sweep'},'thrust_root_motion':{'distance_blocks':30.75,'seconds':1,'collision_checked':True},'sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in backup.iterdir() if p.name!='integration.json'}},ensure_ascii=False,indent=2),encoding='utf-8')
