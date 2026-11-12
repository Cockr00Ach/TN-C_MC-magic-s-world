"""Texture-mapped orthographic material review; no flat face-color approximation."""
from pathlib import Path
import json,numpy as np
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
def render(t,scale,w,h,cy):
 m=json.loads((A/f'models/item/wands/held/lightning_wand_{t}.json').read_text());tex=np.asarray(Image.open(A/f'textures/item/wands/held/lightning_wand_{t}.png').convert('RGB'))
 pixels=np.full((h,w,3),(25,34,49),dtype=np.uint8);depth=np.full((h,w),-1e9)
 for face in m['surfaces']:
  v=np.array(face['vertices']);uv=np.array(face['uv'])*32
  assert np.isfinite(v).all() and uv.min()>=0 and uv.max()<512
  p=np.column_stack((w/2+((v[:,0]-8)*.86+(v[:,2]-8)*.5)*scale,cy-v[:,1]*scale))
  z=-v[:,0]*.5+v[:,2]*.86
  normal=np.cross(v[1]-v[0],v[2]-v[0]);normal/=max(np.linalg.norm(normal),1e-9)
  light=.70+.30*abs(normal@np.array([.3,.8,.5]))
  for ids in [(0,1,2),(0,2,3)]:
   a,b,c=p[list(ids)];xmin=max(0,int(min(a[0],b[0],c[0])));xmax=min(w-1,int(max(a[0],b[0],c[0]))+1);ymin=max(0,int(min(a[1],b[1],c[1])));ymax=min(h-1,int(max(a[1],b[1],c[1]))+1)
   if xmax<xmin or ymax<ymin:continue
   xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
   den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
   if abs(den)<1e-8:continue
   wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
   zz=wa*z[ids[0]]+wb*z[ids[1]]+wc*z[ids[2]];sl=np.s_[ymin:ymax+1,xmin:xmax+1];mask=(wa>=0)&(wb>=0)&(wc>=0)&(zz>=depth[sl])
   uu=np.clip(wa*uv[ids[0],0]+wb*uv[ids[1],0]+wc*uv[ids[2],0],0,511).astype(int);vv=np.clip(wa*uv[ids[0],1]+wb*uv[ids[1],1]+wc*uv[ids[2],1],0,511).astype(int)
   pixels[sl][mask]=np.clip(tex[vv,uu][mask]*light,0,255).astype(np.uint8);depth[sl][mask]=zz[mask]
 return Image.fromarray(pixels)
if __name__=='__main__':
 sheet=Image.new('RGB',(1500,1050),(25,34,49));d=ImageDraw.Draw(sheet)
 for t in range(1,6):
  sheet.paste(render(t,16,300,690,610),((t-1)*300,0))
  sheet.paste(render(t,37,300,340,1300),((t-1)*300,710))
  d.text(((t-1)*300+90,690),f'LIGHTNING {t}',fill=(207,221,247))
 sheet.save(R/'docs/previews/textured_lightning_staves.png');print('Five textured meshes validated and rendered')
