"""Read-only clearance audit against saved chunks; never generates or modifies a world."""
import argparse,json
from pathlib import Path
import numpy as np
import nbtlib
from export_stonecrest_fortress import WorldReader
from survey_world_buildings import section_array

def audit(world,asset,ox,oz,preferred):
    root=Path(__file__).resolve().parents[1]/'src/main/resources/data/tnc'
    m=json.loads((root/'buildings'/f'{asset}.json').read_text(encoding='utf-8'))
    w,h,d=m['dimensions'];bottom=np.full((d,w),10000,dtype=np.int32);top=np.full((d,w),-10000,dtype=np.int32)
    for n,p in enumerate(m['pieces']):
        name=p['resource'].split(':',1)[1];t=nbtlib.load(root/'structures'/f'{name}.nbt')
        valid=[str(s['Name']) not in ('minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void') for s in t['palette']]
        coords=np.asarray([b['pos'].unpack() for b in t['blocks'] if valid[int(b['state'])]],dtype=np.int32)
        if len(coords):
            coords+=np.asarray(p['offset']);xs,ys,zs=coords.T
            np.minimum.at(bottom,(zs,xs),ys);np.maximum.at(top,(zs,xs),ys)
        if n%500==0:print('templates',n,'/',len(m['pieces']),flush=True)
    allowed=np.ones(316-h-80+1,dtype=bool);reader=WorldReader(world);present=missing=0
    for cz in range(oz//16,(oz+d-1)//16+1):
        for cx in range(ox//16,(ox+w-1)//16+1):
            chunk=reader.chunk(cx,cz)
            if chunk is None:missing+=1;continue
            present+=1
            for sy in sorted(chunk.sections):
                if sy*16+15<76:continue
                arr,pal=section_array(chunk,sy)
                if arr is None:continue
                solid=np.asarray([s.name not in ('minecraft:air','minecraft:cave_air','minecraft:void_air') for s in pal])[arr]
                yy,zz,xx=np.where(solid);xx=xx+cx*16-ox;zz=zz+cz*16-oz;yy=yy+sy*16
                inside=(xx>=0)&(xx<w)&(zz>=0)&(zz<d)
                xx,zz,yy=xx[inside],zz[inside],yy[inside]
                low=yy-top[zz,xx]-4;high=yy-bottom[zz,xx]+4
                for y in range(80,317-h):
                    if allowed[y-80] and np.any((y>=low)&(y<=high)):allowed[y-80]=False
            reader.chunks.pop((cx,cz),None)
    safe=(np.flatnonzero(allowed)+80).tolist()
    print(json.dumps(dict(saved_chunks=present,missing_chunks=missing,safe_altitudes=safe,
                         selected=min(safe,key=lambda y:abs(y-preferred)) if safe else None,
                         limitation='Saved chunks only; missing chunks still require runtime survey'),ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('world',type=Path);p.add_argument('--asset',default='end_pvp_island')
    p.add_argument('--x',type=int,required=True);p.add_argument('--z',type=int,required=True);p.add_argument('--y',type=int,default=132)
    a=p.parse_args();audit(a.world,a.asset,a.x,a.z,a.y)
