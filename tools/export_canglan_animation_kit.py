"""Export the unchanged animation rig plus an editor-only attached spear preview."""
from pathlib import Path
import copy,json,math,shutil,zipfile
import numpy as np
from PIL import Image
from wind_blade import _rotation,_axis_rotation
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
OUT=ROOT/'exports/canglan_warlord_animation_kit'
def export():
 OUT.mkdir(parents=True,exist_ok=True)
 for source,name in [('geo/entity/canglan_warlord.geo.json','canglan_warlord.geo.json'),('animations/entity/canglan_warlord.animation.json','canglan_warlord.animation.json'),('textures/entity/canglan_warlord.png','canglan_warlord.png'),('models/wind/arsenal/spear.json','wind_spear.item.json'),('textures/item/wind_relic_metal.png','wind_relic_metal.png')]:shutil.copy2(ASSETS/source,OUT/name)
 model=json.loads((OUT/'canglan_warlord.geo.json').read_text(encoding='utf-8'))
 geo=model['minecraft:geometry'][0];bones=geo['bones'];anchor=next(b for b in bones if b['name']=='weapon_1');pivot=np.array(anchor['pivot'])
 geo['description']['texture_width']=512;geo['description']['texture_height']=512
 atlas=Image.new('RGBA',(512,512));atlas.paste(Image.open(OUT/'canglan_warlord.png'),(0,0));atlas.paste(Image.open(OUT/'wind_relic_metal.png'),(256,0));atlas.save(OUT/'canglan_warlord_spear_editor.png')
 spear=json.loads((OUT/'wind_spear.item.json').read_text())
 fixed=_rotation((-80,0,-4));mirror=np.diag([-1,1,1])
 def euler(m):
  y=math.asin(max(-1,min(1,-m[2,0])));x=math.atan2(m[2,1],m[2,2]);z=math.atan2(m[1,0],m[0,0]);return [-math.degrees(x),math.degrees(y),-math.degrees(z)]
 for i,element in enumerate(spear['elements']):
  a=np.array(element['from']);b=np.array(element['to']);center=(a+b)/2;rot=np.eye(3)
  if element.get('rotation'):
   r=element['rotation'];rot=_axis_rotation(r['axis'],r['angle']);origin=np.array(r['origin']);center=rot@(center-origin)+origin
  center=pivot+fixed@((center-8)*np.array([-1,1,1])*2.4+np.array([0,0,19.2]))
  size=(b-a)*2.4
  uv={}
  for face,spec in element['faces'].items():
   target={'east':'west','west':'east'}.get(face,face);u,v,u1,v1=spec.get('uv',[0,0,16,16]);uv[target]={'uv':[256+u*8,v*8],'uv_size':[(u1-u)*8,(v1-v)*8]}
  cube={'origin':(center-size/2).tolist(),'size':size.tolist(),'pivot':center.tolist(),'rotation':euler(fixed@mirror@rot@mirror),'uv':uv}
  bones.append({'name':f'editor_spear_{i:03d}','parent':'weapon_1','pivot':center.tolist(),'cubes':[cube]})
 (OUT/'canglan_warlord_with_spear.editor.geo.json').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8')
 text='''沧澜战神动画编辑包

推荐：Blockbench 安装 GeckoLib 插件，打开 canglan_warlord_with_spear.editor.geo.json，加载 canglan_warlord_spear_editor.png，再导入 canglan_warlord.animation.json。
这是带长矛的编辑预览，长矛跟随 weapon_1。editor_spear_* 是预览零件，请保留，通常只需 K 手臂、手掌和 weapon_1。
原始模型 canglan_warlord.geo.json 与贴图 canglan_warlord.png 也已附上，骨骼名与游戏一致。
完成后请发回动画 JSON，保留现有动画名和骨骼名。若新增动作，告诉我名称即可接入。
带 editor 的模型和合并贴图仅供编辑预览，游戏仍使用独立渲染的长矛；不要用编辑模型覆盖游戏原始模型，否则会出现两把长矛。
'''
 (OUT/'使用说明.txt').write_text(text,encoding='utf-8-sig')
 clips=json.loads((OUT/'canglan_warlord.animation.json').read_text(encoding='utf-8'))['animations'];known={b['name'] for b in bones}
 assert all(set(c.get('bones',{}))<=known for c in clips.values())
 with zipfile.ZipFile(OUT.with_suffix('.zip'),'w',zipfile.ZIP_DEFLATED) as z:
  for p in OUT.iterdir():z.write(p,p.name)
 print('Exported',len(clips),'animations;',len(spear['elements']),'attached spear pieces;',OUT.with_suffix('.zip'))
if __name__=='__main__':export()
