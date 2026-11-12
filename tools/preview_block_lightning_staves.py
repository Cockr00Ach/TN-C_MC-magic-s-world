from pathlib import Path
import json,math
from PIL import Image,ImageDraw
r=Path(__file__).resolve().parents[1];a=r/'src/main/resources/assets/tnc';im=Image.new('RGB',(1400,850),'#1b2639');d=ImageDraw.Draw(im)
for t in range(1,6):
 m=json.loads((a/f'models/item/wands/held/lightning_wand_{t}.json').read_text());tex=Image.open(a/f'textures/item/wands/held/lightning_wand_{t}.png').convert('RGB');faces=[]
 assert m['tnc_grip']==[8,5,8] and len(m['surfaces'])<1000
 for f in m['surfaces']:
  vs=f['vertices'];assert all(math.isfinite(v) for p in vs for v in p)
  u=sum(p[0] for p in f['uv'])/4;v=sum(p[1] for p in f['uv'])/4;c=tex.getpixel((min(255,int(u*16)),min(255,int(v*16))))
  aa=[vs[1][k]-vs[0][k] for k in range(3)];bb=[vs[2][k]-vs[0][k] for k in range(3)];n=[aa[1]*bb[2]-aa[2]*bb[1],aa[2]*bb[0]-aa[0]*bb[2],aa[0]*bb[1]-aa[1]*bb[0]];le=math.sqrt(sum(x*x for x in n));shade=.7+.5*abs(sum(n[k]*[.3,.8,.5][k] for k in range(3))/max(le,.00001));c=tuple(min(255,int(x*shade*1.3)) for x in c)
  pts=[((t-.5)*280+(x-8)*14*.86+(z-8)*14*.5,740-(y+4)*16) for x,y,z in vs]
  faces.append((sum(-p[0]*.5+p[2]*.86 for p in vs),pts,c))
 for _,pts,c in sorted(faces):d.polygon(pts,fill=c)
 d.text(((t-1)*280+85,800),f'LIGHTNING {t}',fill='#c7dcff')
im.save(r/'docs/previews/block_lightning_staves.png')
print('Validated 5 meshes and generated preview')
