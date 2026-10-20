"""Offline silhouette/material preview of the actual Bedrock rig (not a game screenshot)."""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

ROOT=Path(__file__).resolve().parents[1]


def rotation(v):
    x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])


def model(path):
    g=json.loads(path.read_text(encoding='utf-8-sig'))['minecraft:geometry'][0]
    texture_width=g['description']['texture_width'];texture_height=g['description']['texture_height']
    bones={b['name']:b for b in g['bones']};transforms={}
    def matrix(name):
        if name in transforms:return transforms[name]
        b=bones[name];p=np.array(b.get('pivot',[0,0,0]));r=rotation(b.get('rotation',[0,0,0]));m=np.eye(4);m[:3,:3]=r;m[:3,3]=p-r@p
        if b.get('parent'):m=matrix(b['parent'])@m
        transforms[name]=m;return m
    texture=np.array(Image.open(ROOT/'src/main/resources/assets/tnc/textures/entity/stone_lancer.png').convert('RGB'))/255
    out=[]
    indexes={'north':[0,1,3,2],'south':[4,6,7,5],'west':[0,2,6,4],'east':[1,5,7,3],'up':[2,3,7,6],'down':[0,4,5,1]}
    for bone in bones.values():
        for cube in bone.get('cubes',[]):
            o=np.array(cube['origin']);size=np.array(cube['size']);x,y,z=o;w,h,depth=size
            vertices=np.array([o+size*np.array([i&1,(i>>1)&1,(i>>2)&1]) for i in range(8)])
            polygons=[(face,vertices[ids]) for face,ids in indexes.items()]
            if 'point_' not in bone['name']:
                cut=min(w,h)*.18
                ring=np.array([[x+cut,y],[x+w-cut,y],[x+w,y+cut],[x+w,y+h-cut],
                               [x+w-cut,y+h],[x+cut,y+h],[x,y+h-cut],[x,y+cut]])
                front=np.column_stack([ring,np.full(8,z)]);back=np.column_stack([ring,np.full(8,z+depth)])
                polygons=[('north',front[::-1]),('south',back)]
                polygons.extend(('east',np.array([front[i],front[(i+1)%8],back[(i+1)%8],back[i]])) for i in range(8))
            if 'point_' in bone['name']:
                up='point_up' in bone['name'];base=y if up else y+h
                ring=np.array([[x,base,z],[x+w,base,z],[x+w,base,z+depth],[x,base,z+depth]])
                tip=np.array([x+w/2,y+h if up else y,z+depth/2])
                polygons=[('north',np.array([ring[i],ring[(i+1)%4],tip])) for i in range(4)]+[('down',ring)]
            for face,points in polygons:
                pivot=np.array(cube.get('pivot',[0,0,0]));points=(rotation(cube.get('rotation',[0,0,0]))@(points-pivot).T).T+pivot
                matrix_bone=matrix(bone['name']);points=(matrix_bone@np.column_stack([points,np.ones(len(points))]).T).T[:,:3]
                uv=cube.get('uv',{})
                if isinstance(uv,dict):
                    q=uv[face];u,v=q['uv'];uw,vh=q['uv_size'];u0,u1=sorted([u,u+uw]);v0,v1=sorted([v,v+vh])
                    x0,x1=[int(a/texture_width*texture.shape[1]) for a in [u0,u1]];y0,y1=[int(a/texture_height*texture.shape[0]) for a in [v0,v1]]
                    patch=texture[max(0,y0):max(y0+1,y1),max(0,x0):max(x0+1,x1)]
                    color=patch.mean(axis=(0,1)) if patch.size else np.array([.13,.12,.17])
                else:color=np.array([.13,.12,.17])
                normal=np.cross(points[1]-points[0],points[2]-points[0]);normal/=max(1e-8,np.linalg.norm(normal))
                shade=.72+.28*max(0,np.dot(normal,np.array([.3,-.6,.74])))
                color=np.clip(color*shade+.045,0,1)
                out.append((points[:,[0,2,1]],color))
    return out


def main():
    faces=model(ROOT/'src/main/resources/assets/tnc/geo/entity/stone_lancer.geo.json')
    fig=plt.figure(figsize=(15,7),facecolor='#161923')
    for i,(title,az) in enumerate([('FRONT / paired rock lances',-90),('THREE QUARTER / fractured granite',-55),('SIDE / crystal dorsal ridge',0)]):
        ax=fig.add_subplot(1,3,i+1,projection='3d');ax.set_facecolor('#161923')
        for poly,color in faces:ax.add_collection3d(Poly3DCollection([poly],facecolors=[color],edgecolors=[np.maximum(color*.65,.025)],linewidths=.22))
        ax.set(xlim=(-75,75),ylim=(-48,48),zlim=(0,165));ax.set_box_aspect([150,96,165]);ax.view_init(elev=7,azim=az);ax.set_proj_type('ortho');ax.set_axis_off();ax.set_title(title,color='#d8cde8',fontsize=11)
    fig.suptitle('STONE LANCER — Panyue, lithic sovereign',color='white',fontsize=18)
    fig.text(.5,.04,'Offline mesh preview • direct model geometry • simplified material samples',ha='center',color='#a8a1b2',fontsize=10)
    target=ROOT/'docs/previews/stone_lancer_20261017.png';fig.savefig(target,dpi=150,facecolor=fig.get_facecolor(),bbox_inches='tight');print(target)


if __name__=='__main__':main()
