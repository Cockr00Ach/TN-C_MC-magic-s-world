from pathlib import Path
from PIL import Image
import json,math
root=Path('src/main/resources/assets/tnc');geo=json.loads((root/'geo/entity/canglan_warlord.geo.json').read_text())['minecraft:geometry'][0]
image=Image.new('RGBA',(256,256));eyes=next(b for b in geo['bones'] if b['name']=='eyes')
for cube in eyes['cubes'][:2]:
 for face in cube['uv'].values():
  u,v=face['uv'];w,h=face['uv_size']
  for y in range(max(0,int(v)),min(256,math.ceil(v+h))):
   for x in range(max(0,int(u)),min(256,math.ceil(u+w))):
    image.putpixel((x,y),(135,255,255,255))
image.save(root/'textures/entity/canglan_eyes_emissive.png')
flow=Image.new('RGBA',(128,32))
for y in range(32):
 for x in range(128):
  edge=math.sin(math.pi*y/31)**1.5;dash=.6+.4*math.cos(x/128*math.tau*4)**8
  flow.putpixel((x,y),(180,255,255,int(255*edge*dash)))
flow.save(root/'textures/entity/canglan_mana_flow.png')
