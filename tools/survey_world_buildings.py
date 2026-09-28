"""Read-only architectural survey of Java worlds; vectorized modern Anvil decode."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from export_stonecrest_fortress import WorldReader, is_artificial


def section_array(chunk, sy):
    decoded = chunk._section(sy)
    if decoded is None:
        return None, None
    palette, packed, bits = decoded
    if packed is None:
        return np.zeros((16,16,16),dtype=np.uint16), palette
    # anvil-parser's NBT backend exposes unsigned longs on some versions.
    raw=np.asarray([int(v)&((1<<64)-1) for v in packed],dtype=np.uint64)
    idx=np.arange(4096,dtype=np.uint64)
    per=64//bits
    values=((raw[idx//per]>>((idx%per)*bits))&((1<<bits)-1)).astype(np.uint16)
    if int(values.max())>=len(palette): raise ValueError('Invalid Anvil palette')
    return values.reshape(16,16,16),palette


def tint(state):
    p=state.path
    if any(t in p for t in ('water','ice')): return (45,101,151)
    if any(t in p for t in ('leaves','grass','fern','bush','moss')): return (62,103,65)
    if any(t in p for t in ('roof','shingle','brick','red_','copper')): return (157,87,70)
    if any(t in p for t in ('sand','quartz','calcite','plaster','stucco')): return (198,185,157)
    if any(t in p for t in ('wood','plank','log')): return (118,86,56)
    if any(t in p for t in ('deepslate','blackstone','obsidian','coal')): return (70,64,79)
    return (143,143,137)


def survey(world,bounds,ymin,ymax,output):
    x0,x1,z0,z1=bounds
    width,depth=x1-x0+1,z1-z0+1
    heights=np.full((depth,width),-999,dtype=np.int16)
    built=np.zeros((depth,width),dtype=np.uint16)
    colors=np.zeros((depth,width,3),dtype=np.uint8)
    reader=WorldReader(world)
    total=((x1//16)-(x0//16)+1)*((z1//16)-(z0//16)+1)
    count=0
    for cz in range(z0//16,z1//16+1):
        for cx in range(x0//16,x1//16+1):
            count+=1
            chunk=reader.chunk(cx,cz)
            if chunk is None: continue
            xx0,xx1=max(x0,cx*16),min(x1+1,cx*16+16)
            zz0,zz1=max(z0,cz*16),min(z1+1,cz*16+16)
            dest=(slice(zz0-z0,zz1-z0),slice(xx0-x0,xx1-x0))
            crop=(slice(zz0-cz*16,zz1-cz*16),slice(xx0-cx*16,xx1-cx*16))
            for sy in sorted(chunk.sections):
                if sy*16>ymax or sy*16+15<ymin: continue
                arr,pal=section_array(chunk,sy)
                if arr is None: continue
                yy0,yy1=max(0,ymin-sy*16),min(16,ymax-sy*16+1)
                arr=arr[yy0:yy1]
                solid=np.array([s.path not in ('air','cave_air','void_air','structure_void') for s in pal])[arr]
                anysolid=solid.any(axis=0)
                top=(len(arr)-1-np.argmax(solid[::-1],axis=0)).astype(np.int16)
                topstate=np.take_along_axis(arr,top[None],axis=0)[0]
                h=top+sy*16+yy0
                update=anysolid[crop]&(h[crop]>heights[dest])
                heights[dest][update]=h[crop][update]
                rgb=np.array([tint(s) for s in pal],dtype=np.uint8)[topstate]
                colors[dest][update]=rgb[crop][update]
                flags=np.array([is_artificial(s) for s in pal])
                built[dest]+=flags[arr].sum(axis=0).astype(np.uint16)[crop]
            # Bounded cache: regions stay cached; decoded chunks are discarded.
            reader.chunks.pop((cx,cz),None)
        if (cz-z0//16)%16==0: print(f'{world.name}: {count}/{total} chunks',flush=True)
    output.parent.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(output.with_suffix('.npz'),heights=heights,built=built,colors=colors,bounds=bounds)
    shade=np.clip(.7+(heights-heights[heights>-999].min())/300,.7,1.3)
    rgb=np.clip(colors*shade[:,:,None],0,255).astype(np.uint8)
    im=Image.fromarray(rgb)
    draw=ImageDraw.Draw(im)
    for x in range((x0//128+1)*128,x1,128):
        draw.line((x-x0,0,x-x0,depth),fill=(90,92,92)); draw.text((x-x0+2,3),str(x),fill='white')
    for z in range((z0//128+1)*128,z1,128):
        draw.line((0,z-z0,width,z-z0),fill=(90,92,92)); draw.text((3,z-z0+2),str(z),fill='white')
    im.save(output.with_suffix('.png'))
    ys,xs=np.where(built>2)
    report={'world':str(world),'bounds':bounds,'y':[ymin,ymax],'architectural_bounds':
            [int(xs.min()+x0),int(xs.max()+x0),int(ys.min()+z0),int(ys.max()+z0)] if len(xs) else None}
    output.with_suffix('.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--world',type=Path,required=True)
    p.add_argument('--bounds',nargs=4,type=int,required=True); p.add_argument('--ymin',type=int,default=-64)
    p.add_argument('--ymax',type=int,default=319); p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); survey(a.world,a.bounds,a.ymin,a.ymax,a.output)
