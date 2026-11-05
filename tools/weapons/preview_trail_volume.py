"""Visualize the exact swept-volume quads exported by WarriorTrailVolumeTest."""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]


def render(faces,yaw,pitch,size=520):
    cy,sy=math.cos(yaw),math.sin(yaw);cp,sp=math.cos(pitch),math.sin(pitch)
    def project(p):
        x,y,z=p;x,z=x*cy-z*sy,x*sy+z*cy
        return np.array([x,y*cp-z*sp,y*sp+z*cp])
    geometry=[([project(p) for p in f['points']],f) for f in faces]
    points=np.array([p for poly,_ in geometry for p in poly]);lo,hi=points[:,:2].min(axis=0),points[:,:2].max(axis=0)
    scale=(size-80)/max(hi-lo);center=(lo+hi)*.5
    image=np.empty((size,size,3),dtype=float);image[:]=[20,23,32]
    for poly,face in sorted(geometry,key=lambda item:sum(p[2] for p in item[0])):
        coords=np.array([((p[0]-center[0])*scale+size/2,size/2-(p[1]-center[1])*scale) for p in poly])
        c=face['color'];color=np.array([(c>>16)&255,(c>>8)&255,c&255])
        written=np.zeros((size,size),dtype=bool)
        for indices in ((0,1,2),(0,2,3)):
            p0,p1,p2=coords[list(indices)];a,b=p1-p0,p2-p0;det=a[0]*b[1]-a[1]*b[0]
            if abs(det)<1e-9:continue
            lo=np.maximum(0,np.floor(np.minimum(np.minimum(p0,p1),p2)).astype(int));hi=np.minimum(size-1,np.ceil(np.maximum(np.maximum(p0,p1),p2)).astype(int))
            if np.any(hi<lo):continue
            yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];dx,dy=xx+.5-p0[0],yy+.5-p0[1]
            u=(dx*b[1]-dy*b[0])/det;v=(a[0]*dy-a[1]*dx)/det
            used=written[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            mask=(u>=0)&(v>=0)&(u+v<=1)&~used
            aa,ab,ac=[face['alpha'][i] for i in indices];alpha=np.clip(aa+u*(ab-aa)+v*(ac-aa),0,1)[mask,None]
            target=image[lo[1]:hi[1]+1,lo[0]:hi[0]+1];target[mask]=target[mask]*(1-alpha)+color*alpha;used[mask]=True
    return Image.fromarray(np.clip(image,0,255).astype('uint8'),'RGB')



def main():
    faces=json.loads((ROOT/'build/tnc-warrior-trail-volume.json').read_text('utf8'))
    sheet=Image.new('RGB',(1560,610),(14,17,24));draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',24)
    draw.text((24,12),'刀光体积 · 实际 Java 几何离线预览',font=font,fill=(235,237,245))
    for i,(pitch,label) in enumerate([(-1.0,'俯视弧面'),(-.28,'斜视厚度'),(0,'平视侧壁')]):
        image=render(faces,.35,pitch);sheet.paste(image,(i*520,54));draw.text((i*520+24,574),label,font=font,fill=(205,211,226))
    sheet.save(ROOT/'docs/previews/warrior-trail-volume-20261105.png')


if __name__=='__main__':main()
