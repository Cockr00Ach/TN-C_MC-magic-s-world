"""Render actual vanilla/Forge item surfaces and textures using the workspace depth rasterizer."""
import json,math,struct,subprocess
from functools import lru_cache
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.spatial.transform import Rotation
from roster import ROOT,PACK,FAMILIES,all_ids,file_name,model,texture,tex_ref

RASTER=ROOT.parent/'TN-C-法杖全阶/work/catalog_raster.exe'
SIDES={'down':(5,4,0,1),'up':(2,3,7,6),'west':(0,4,7,3),'north':(1,0,3,2),'east':(5,1,2,6),'south':(4,5,6,7)}
CORNERS=np.array(((0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)))
FAN=np.array(((0,1,2),(0,2,3)))

def font(size,bold=False):return ImageFont.truetype('C:/Windows/Fonts/'+('msyhbd.ttc' if bold else 'msyh.ttc'),size)

def sheet_textures(names,patched):
    imgs=[texture(n,patched) for n in names];out=Image.new('RGBA',(sum(im.width for im in imgs),max(im.height for im in imgs)))
    refs={};offset=0
    for n,im in zip(names,imgs):
        out.paste(im,(offset,0));refs[n]=(im.width,im.height,offset);offset+=im.width
    return out,refs

def geometry(ident,patched):
    data=model('tnc:item/'+ident,patched)
    if not data.get('elements'):return None,data
    positions=[];uvs=[];materials=[]
    for part in data['elements']:
        a,b=np.array(part['from']),np.array(part['to']);v=a+CORNERS*(b-a)
        rot=part.get('rotation')
        if rot and rot.get('angle',0):
            pivot=np.array(rot['origin']);v=v-pivot
            axis=rot['axis']
            if rot.get('rescale'):
                v[:,[i for i in range(3) if i!='xyz'.index(axis)]]/=math.cos(math.radians(rot['angle']))
            v=Rotation.from_euler(axis,rot['angle'],degrees=True).apply(v)+pivot
        for side,face in part['faces'].items():
            if not face.get('texture'):continue
            mat=tex_ref(face['texture'],data['textures']);i=SIDES[side]
            x,y,x2,y2=face.get('uv',[0,0,16,16]);tex=np.array([[x,y2],[x2,y2],[x2,y],[x,y]],dtype=float)
            if side in ('up','down') or ident in ('bronze_halberd','bronze_twin_main','bronze_twin_off'):
                tex=np.array([[x2,y],[x,y],[x,y2],[x2,y2]],dtype=float)
            tex=np.roll(tex,-(face.get('rotation',0)//90),axis=0)
            positions.append(v[list(i)][FAN]);uvs.append(tex[FAN]);materials.extend([mat,mat])
    atlas,refs=sheet_textures(list(dict.fromkeys(materials)),patched)
    xyz=np.concatenate(positions);uv=np.concatenate(uvs);mt=np.array(materials)
    for m,(w,h,start) in refs.items():
        hit=mt==m;uv[hit]=(uv[hit]/16.)*np.array([w,h])+[start,0]
    return (xyz,uv,atlas),data

def render(ident,patched,w,h,yaw=-19,pitch=9,roll=-9):
    geo,data=geometry(ident,patched);ss=2;ww,hh=w*ss,h*ss
    if geo is None:
        textures=data.get('textures',{})
        if 'layer0' not in textures:raise ValueError('No renderable surfaces for '+ident)
        img=texture(tex_ref(textures['layer0'],textures),patched);img=img.crop(img.getbbox());img.thumbnail((w-14,h-14),Image.Resampling.NEAREST)
        ratio=min((w-20)/img.width,(h-20)/img.height)
        thumb=img.resize((max(1,round(img.width*ratio)),max(1,round(img.height*ratio))),Image.Resampling.NEAREST)
        out=Image.new('RGBA',(w,h));out.alpha_composite(thumb,((w-thumb.width)//2,(h-thumb.height)//2));return out
    xyz,uv,atlas=geo
    spread=np.ptp(xyz,axis=(0,1));slim=int(np.argmin(spread))
    if slim==1:xyz=xyz[:,:,[0,2,1]].copy();xyz[:,:,2]*=-1
    elif slim==0:xyz=xyz[:,:,[2,1,0]].copy()
    if np.ptp(xyz[:,:,0])>1.65*np.ptp(xyz[:,:,1]):
        xyz=xyz[:,:,[1,0,2]].copy()
    r=Rotation.from_euler('yxz',(yaw,pitch,roll),degrees=True);screen=r.apply(xyz.reshape(-1,3)).reshape(xyz.shape)
    normals=np.cross(screen[:,1]-screen[:,0],screen[:,2]-screen[:,0]);nl=np.linalg.norm(normals,axis=1)
    normals/=np.maximum(nl[:,None],1e-12);normals*=np.where(normals[:,2:]<0,-1.,1.)
    shade=.61+.46*np.maximum(0,normals@[-.34,.61,.71])+.17*np.maximum(0,normals@[.74,.49,-.3])
    lo=screen.min(axis=(0,1));hi=screen.max(axis=(0,1));scale=min((ww-ss*17)/max(.01,hi[0]-lo[0]),(hh-ss*19)/max(.01,hi[1]-lo[1]))
    screen[:,:,0]=(screen[:,:,0]-(lo[0]+hi[0])/2)*scale+ww/2
    screen[:,:,1]=hh/2-(screen[:,:,1]-(lo[1]+hi[1])/2)*scale
    vu=np.concatenate([screen,uv],axis=2)
    tri=np.column_stack((vu.reshape(-1,15),shade,np.zeros((len(vu),2))))[nl>1e-8].astype('<f4')
    raw=subprocess.run([str(RASTER)],input=struct.pack('<5i',ww,hh,*atlas.size,len(tri))+atlas.tobytes()+bytes(atlas.width*atlas.height*4)+tri.tobytes(),stdout=subprocess.PIPE,check=True).stdout
    return Image.frombytes('RGBA',(ww,hh),raw).resize((w,h),Image.Resampling.LANCZOS)

def panel(ident,patch,sz=(240,335),**settings):
    bg=Image.new('RGBA',sz,'#24303a');item=render(ident,patch,*sz,**settings);bg.alpha_composite(item);return bg

def gallery():
    out=ROOT/'预览';out.mkdir(exist_ok=True)
    ids=list(all_ids());header=(232,227,215);muted=(167,183,192)
    for tag,label in FAMILIES.items():
        group=[i for t,i in ids if t==tag];cols=6 if len(group)>6 else len(group)
        rows=math.ceil(len(group)/cols);w,h=238,365;margin=22
        page=Image.new('RGB',(margin*2+cols*w,86+rows*h+16),'#151c23');d=ImageDraw.Draw(page)
        d.text((margin,16),label+'  /  '+str(len(group))+' 件',font=font(34,True),fill=header)
        for i,id in enumerate(group):
            x,y=margin+(i%cols)*w,79+(i//cols)*h;page.paste(panel(id,True,(w-8,h-50)),(x,y))
            d.text((x+8,y+h-43),file_name(id),font=font(21),fill=header)
        page.save(out/(label+'.jpg'),quality=93,subsampling=0)
    reps=['star_rapier','crescent_guandao','thunder_bronze_hammer','coiled_dragon_spear','moon_bow','bronze_halberd']
    w,h=258,290;page=Image.new('RGB',(6*w+40,2*h+157),'#161e26');d=ImageDraw.Draw(page)
    d.text((20,13),'战士兵器 · 同角度对照',font=font(32,True),fill=header)
    d.text((20,60),'原版模型',font=font(18),fill=muted)
    for i,id in enumerate(reps):
        x=20+i*w;page.paste(panel(id,False,(w-8,h)),(x,87));page.paste(panel(id,True,(w-8,h)),(x,h+123))
        d.text((x+4,h*2+124),file_name(id),font=font(20),fill=header)
    d.text((20,h+92),'精修后',font=font(18),fill=muted)
    page.save(out/'六件兵器前后对照.jpg',quality=93,subsampling=0)
if __name__=='__main__':gallery()
