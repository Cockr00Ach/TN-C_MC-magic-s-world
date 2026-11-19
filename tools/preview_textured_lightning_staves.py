"""Texture-mapped orthographic material review; no flat face-color approximation."""
from pathlib import Path
import argparse, json, math, numpy as np
from PIL import Image, ImageDraw, ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
def render(t,scale,w,h,cy,*,transparent=False,angle=.43):
 m=json.loads((A/f'models/item/wands/held/lightning_wand_{t}.json').read_text())
 base=A/f'textures/item/wands/held/lightning_wand_{t}.png'
 tex=np.asarray(Image.open(base).convert('RGB'))
 emission=np.asarray(Image.open(base.with_name(base.stem+'_emission.png')).convert('RGB'))
 sin,cos=math.sin(angle),math.cos(angle)
 pixels=np.full((h,w,3),(25,34,49),dtype=np.uint8);depth=np.full((h,w),-1e9)
 for face in m['surfaces']:
  v=np.array(face['vertices']);uv=np.array(face['uv'])*32
  assert np.isfinite(v).all() and uv.min()>=0 and uv.max()<512
  p=np.column_stack((w/2+((v[:,0]-8)*cos+(v[:,2]-8)*sin)*scale,cy-v[:,1]*scale))
  z=-v[:,0]*sin+v[:,2]*cos
  normal=np.cross(v[1]-v[0],v[2]-v[0]);normal/=max(np.linalg.norm(normal),1e-9)
  light=.58+.43*abs(normal@np.array([.46,.70,.53]))
  for ids in [(0,1,2),(0,2,3)]:
   a,b,c=p[list(ids)];xmin=max(0,int(min(a[0],b[0],c[0])));xmax=min(w-1,int(max(a[0],b[0],c[0]))+1);ymin=max(0,int(min(a[1],b[1],c[1])));ymax=min(h-1,int(max(a[1],b[1],c[1]))+1)
   if xmax<xmin or ymax<ymin:continue
   xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
   den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
   if abs(den)<1e-8:continue
   wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
   zz=wa*z[ids[0]]+wb*z[ids[1]]+wc*z[ids[2]];sl=np.s_[ymin:ymax+1,xmin:xmax+1];mask=(wa>=0)&(wb>=0)&(wc>=0)&(zz>=depth[sl])
   uu=np.clip(wa*uv[ids[0],0]+wb*uv[ids[1],0]+wc*uv[ids[2],0],0,511).astype(int);vv=np.clip(wa*uv[ids[0],1]+wb*uv[ids[1],1]+wc*uv[ids[2],1],0,511).astype(int)
   shade=tex[vv,uu][mask]*light
   if face['material']==2:shade+=.76*emission[vv,uu][mask]
   pixels[sl][mask]=np.clip(shade,0,255).astype(np.uint8);depth[sl][mask]=zz[mask]
 if transparent:
  return Image.fromarray(np.dstack((pixels,np.where(depth>-1e9,255,0).astype(np.uint8))))
 return Image.fromarray(pixels)

def portfolio(output):
 colors=[(122,190,218),(164,211,241),(198,159,245),(123,218,247),(251,210,143)]
 names=['引雷','银锋','镇雷','星枢','至尊']
 fontdir=Path('C:/Windows/Fonts')
 namefont=ImageFont.truetype(str(fontdir/'msyh.ttc'),25)
 bodyfont=ImageFont.truetype(str(fontdir/'msyh.ttc'),16)
 width,height=1500,1020
 y,x=np.indices((height,width))
 gradient=(1-np.clip(np.hypot((x-750)/1140,(y-425)/855),0,1))[...,None]
 backdrop=np.array([13,16,24])+gradient*np.array([17,23,35])
 sheet=Image.fromarray(backdrop.astype(np.uint8)).convert('RGBA')
 draw=ImageDraw.Draw(sheet)
 draw.text((75,47),'雷系法杖',font=ImageFont.truetype(str(fontdir/'msyhbd.ttc'),36),fill=(221,232,245))
 draw.line((76,104,1424,104),fill=(89,102,123),width=1)
 for t,tint in enumerate(colors,1):
  cx=160+(t-1)*295
  draw.line((cx,160,cx,701),fill=(46,58,77),width=1)
  draw.arc((cx-96,173,cx+96,365),start=18,end=162,fill=(50,62,84))
  whole=render(t,11.9,270,538,472,transparent=True)
  sheet.alpha_composite(whole,(cx-135,139))
  draw.text((cx,691),f"{['I','II','III','IV','V'][t-1]}  {names[t-1]}",font=namefont,fill=tint,anchor='mm')
  draw.line((cx-104,724,cx+104,724),fill=(70,82,106),width=1)
  center_y, scale = [(27,21), (28.1,19), (28.2,18), (29,17), (29.7,16)][t-1]
  detail=render(t,scale,262,222,153+center_y*scale,transparent=True,angle=-.53)
  sheet.alpha_composite(detail,(cx-131,746))
  icon=Image.open(A/f'textures/item/wands/lightning_wand_{t}.png').convert('RGBA')
  sheet.alpha_composite(icon,(cx+65,609))
 draw.text((81,977),'实装网格与材质预览',font=bodyfont,fill=(148,165,187))
 output.parent.mkdir(parents=True,exist_ok=True)
 sheet.convert('RGB').save(output)

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--out',type=Path,default=R/'docs/previews/textured_lightning_staves.png')
 portfolio(parser.parse_args().out)
 print('Five textured meshes rendered')
