"""Prepare user-provided gate buildings locally; original third-party files stay outside git."""
from pathlib import Path
import argparse,zipfile,gzip,io,json,hashlib
import nbtlib as n
ROOT=Path(__file__).resolve().parents[1]
FILES={'desert':'bwolfnpv5-schem.zip','crypt':'skullnpv18-schem.zip','tide':'guardiannpv4-schem.zip','astral':'darkunicornnpv3-schem.zip'}
def opening(size,states,palette):
    air={i for i,p in enumerate(palette) if str(p['Name']) in ('minecraft:air','minecraft:cave_air')}
    obs={i for i,p in enumerate(palette) if str(p['Name']) in ('minecraft:obsidian','minecraft:crying_obsidian')}
    best=None
    for axis in [0,2]:
        other=2 if axis==0 else 0;planes={}
        for pos,state in states.items():
            if state in obs:planes[pos[axis]]=planes.get(pos[axis],0)+1
        for plane,_ in sorted(planes.items(),key=lambda v:-v[1])[:12]:
            heights=[0]*size[other]
            def state(u,y):
                v=[0,y,0];v[axis]=plane;v[other]=u;return states.get(tuple(v),0)
            for y in range(min(size[1],45)):
                for u in range(size[other]):heights[u]=heights[u]+1 if state(u,y) in air else 0
                stack=[]
                for u in range(size[other]+1):
                    cur=heights[u] if u<size[other] else 0;start=u
                    while stack and stack[-1][1]>cur:
                        left,height=stack.pop();start=left;width=u-left;bottom=y-height+1
                        if not(3<=width<=34 and 4<=height<=35 and left>0 and u<size[other] and bottom>0):continue
                        sides=sum(state(left-1,yy) in obs for yy in range(bottom,y+1))+sum(state(u,yy) in obs for yy in range(bottom,y+1))
                        floor=sum(state(xx,bottom-1) in obs for xx in range(left,u));roof=sum(state(xx,y+1) in obs for xx in range(left,u))
                        if sides<height*1.2 or floor+roof<width*.6:continue
                        score=width*height*(.5+sides/(2*height))
                        if best is None or score>best[0]:best=(score,axis,plane,other,left,bottom,width,height)
                    if not stack or stack[-1][1]<cur:stack.append((start,cur))
    if best is None:raise ValueError('No framed portal opening found; inspect the source rather than invent an anchor')
    _,axis,plane,other,left,bottom,w,h=best
    points=[]
    for u in range(left,left+w):
        for y in range(bottom,bottom+h):
            p=[0,y,0];p[axis]=plane;p[other]=u;points.append(tuple(p))
    return points,dict(axis=axis,plane=plane,bottom=bottom,width=w,height=h,left=left)
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,default=Path.home()/'Downloads/地下城大门');ap.add_argument('--from-jar',type=Path);a=ap.parse_args();dest=ROOT/'work/dungeon-gate-resources';dest.mkdir(parents=True,exist_ok=True)
    if a.from_jar:
        with zipfile.ZipFile(a.from_jar) as z:
            names=[v for v in z.namelist() if v.startswith(('data/tnc/structures/dungeon_gates/','data/tnc/dungeon_gate_assets/')) and not v.endswith('/')]
            assert names,'Installed runtime has no dungeon gate asset cache'
            for name in names:
                p=dest/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
        print('Restored',len(names),'gate resources from installed runtime');return
    receipt={}
    for theme,filename in FILES.items():
        src=a.source/filename
        with zipfile.ZipFile(src) as z:
            name=next(v for v in z.namelist() if v.endswith('.nbt'));raw=z.read(name)
            if raw[:2]==b'\x1f\x8b':raw=gzip.decompress(raw)
            tag=n.File.parse(io.BytesIO(raw))
        size=list(map(int,tag['size']));palette=tag['palette'];states={tuple(map(int,b['pos'])):int(b['state']) for b in tag['blocks']};portal,info=opening(size,states,palette)
        palette.append(n.Compound({'Name':n.String('tnc:dungeon_gate_'+theme)}));portal_index=len(palette)-1
        for p in portal:states[p]=portal_index
        air={i for i,p in enumerate(palette) if str(p['Name']) in ('minecraft:air','minecraft:cave_air')};pieces=[]
        for x in range(0,size[0],16):
            for z in range(0,size[2],16):
                blocks=[]
                for (bx,by,bz),state in states.items():
                    if x<=bx<x+16 and z<=bz<z+16 and state not in air:blocks.append(n.Compound({'pos':n.List[n.Int]([bx-x,by,bz-z]),'state':n.Int(state)}))
                if not blocks:continue
                key=f'dungeon_gates/{theme}/{x}_{z}';p=dest/'data/tnc/structures'/f'{key}.nbt';p.parent.mkdir(parents=True,exist_ok=True)
                out=n.File({'DataVersion':tag.get('DataVersion',n.Int(3465)),'size':n.List[n.Int]([min(16,size[0]-x),size[1],min(16,size[2]-z)]),'palette':palette,'blocks':n.List[n.Compound](blocks),'entities':n.List[n.Compound]([])})
                bio=io.BytesIO();out.write(bio);p.write_bytes(gzip.compress(bio.getvalue(),mtime=0));pieces.append({'resource':'tnc:'+key,'offset':[x,0,z]})
        center=portal[len(portal)//2];manifest=dict(size=size,pieces=pieces,portal=list(center),opening=info,source_sha256=hashlib.sha256(src.read_bytes()).hexdigest(),credit='MazaVili / user-supplied source')
        p=dest/'data/tnc/dungeon_gate_assets'/f'{theme}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(manifest,indent=2)+'\n','utf8');receipt[theme]=manifest
        print(theme,'size',size,'portal',info,'tiles',len(pieces))
    (ROOT/'work/dungeon-gate-import-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n','utf8')
if __name__=='__main__':main()
