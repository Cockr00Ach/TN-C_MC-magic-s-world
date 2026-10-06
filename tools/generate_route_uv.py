"""Author UV material maps for the cuboid skeletons, using the native pixel pipeline."""
from pathlib import Path
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc/textures'
def material(size,base,light,shadow):
 im=Image.new('RGBA',size,base);d=ImageDraw.Draw(im)
 for y in range(size[1]):
  for x in range(size[0]):
   if (x*3+y*5)%23==0:d.point((x,y),fill=light)
   elif (x*7+y*3)%29==0:d.point((x,y),fill=shadow)
 return im,d
for id,base,light,shadow in [('prismatic_antelope','#c8dbc8','#e6ecd6','#95b8aa'),('drumbelly_otter','#a07348','#c9945c','#6b513f')]:
 im,d=material((64,64),base,light,shadow);glow=Image.new('RGBA',(64,64));g=ImageDraw.Draw(glow)
 if id=='prismatic_antelope':
  # Pale abdomen, dark teal hooves, gold antler forks with blue-green crystal tips.
  d.rectangle((0,16,47,23),fill='#e7e4c7');d.rectangle((48,32,55,44),fill='#537a75');d.rectangle((54,40,63,44),fill='#385c60')
  for x in [48,56]:
   d.rectangle((x,0,min(63,x+7),13),fill='#8a8050');d.rectangle((x,1,min(63,x+7),4),fill='#63c8c4');g.rectangle((x,1,min(63,x+7),3),fill='#6ad9d2')
  d.rectangle((0,27,27,31),fill='#56888a');d.rectangle((9,32,10,33),fill='#183840');d.rectangle((19,32,20,33),fill='#183840')
  d.line([(4,6),(12,8),(20,6),(28,8),(36,6)],fill='#4f9e98',width=1)
 else:
  # Ocher back, cream cheeks and chest, blue-green whiskers and a striped tail.
  d.rectangle((0,9,27,14),fill='#d9bd85');d.rectangle((0,40,31,51),fill='#e4d5aa');d.rectangle((28,27,41,31),fill='#e6cda2')
  d.rectangle((0,28,27,31),fill='#755240');d.rectangle((9,32,10,33),fill='#282e2d');d.rectangle((22,32,23,33),fill='#282e2d')
  for y in [43,47,51]:d.rectangle((34,y,61,y+1),fill='#547f7a')
  d.line([(2,32),(5,33),(8,32)],fill='#609f95');d.line([(25,32),(28,33),(31,32)],fill='#609f95')
  g.rectangle((35,43,39,43),fill='#5ac3b3')
 folder=A/'entity/pasture';folder.mkdir(parents=True,exist_ok=True);im.save(folder/(id+'.png'));glow.save(folder/(id+'_glow.png'))
im,d=material((64,32),'#648a8a','#94b7af','#3c5d62')
d.rectangle((0,0,13,7),fill='#285758');d.rectangle((2,1,11,5),fill='#68cdc0');d.rectangle((5,1,8,5),fill='#b8ece0')
d.rectangle((32,0,55,13),fill='#425c61');d.rectangle((40,0,63,9),fill='#b19360');d.line([(40,7),(63,7)],fill='#ddc190')
for x in [20,30]:d.rectangle((x,21,x+3,27),fill='#446b70');d.line([(x,22),(x+3,22)],fill='#85d1c4')
folder=A/'models/armor';folder.mkdir(parents=True,exist_ok=True);im.save(folder/'mana_mech_layer_1.png')
print('Two unique 64px animal UV maps and a wearable 64x32 mech material map authored.')
