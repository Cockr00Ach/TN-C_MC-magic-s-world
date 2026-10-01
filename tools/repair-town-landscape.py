"""Prepare a compare-and-set repair in a separate directory; never writes the source save.

Reads the exact shipped overlay. Preserves every unrelated chunk record, entity,
container and player file. Any conflicting voxel or owned claim aborts preparation.
"""
from pathlib import Path
import argparse,io,zlib,gzip,hashlib,json,copy
import nbtlib as n
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
def state_key(t,leaves=False):
    name=str(t['Name']);props={str(k):str(v) for k,v in t.get('Properties',{}).items()}
    if leaves and name.endswith('_leaves'):props.pop('distance',None)
    return name,tuple(sorted(props.items()))
def compatible(actual,before,after):
    if state_key(actual,True) in (state_key(before,True),state_key(after,True)):return True
    a,b,c=map(lambda s:str(s['Name']),(actual,before,after))
    if a=='minecraft:grass_block' and b=='minecraft:air' and c=='minecraft:dirt':return True
    if a=='minecraft:dirt' and b=='minecraft:grass_block' and c in ('minecraft:stone','minecraft:dirt'):return True
    return a=='minecraft:water' and b=='minecraft:air' and int(actual.get('Properties',{}).get('level','0'))>0 and c in ('minecraft:stone','minecraft:dirt','minecraft:grass_block','minecraft:coarse_dirt','minecraft:rooted_dirt','minecraft:podzol','minecraft:mycelium','minecraft:moss_block')
def decode(states):
    pal=states['palette'];bits=max(4,(len(pal)-1).bit_length());per=64//bits
    if len(pal)==1:return np.zeros(4096,dtype=np.uint16)
    words=np.asarray([int(v)&((1<<64)-1) for v in states['data']],dtype=np.uint64);i=np.arange(4096,dtype=np.uint64)
    return ((words[i//per]>>((i%per)*bits))&((1<<bits)-1)).astype(np.uint16)
def encode(pal,values):
    result=n.Compound({'palette':n.List[n.Compound](pal)})
    if len(pal)==1:return result
    bits=max(4,(len(pal)-1).bit_length());per=64//bits;words=np.zeros((4096+per-1)//per,dtype=np.uint64)
    for shift in range(per):
        part=values[shift::per].astype(np.uint64);words[:len(part)]|=part<<(shift*bits)
    result['data']=n.LongArray(words.view(np.int64));return result
def region_chunks(raw):
    result={}
    for i in range(1024):
        off=int.from_bytes(raw[i*4:i*4+3],'big')*4096;count=raw[i*4+3]
        if off:
            size=int.from_bytes(raw[off:off+4],'big')
            if not count or size>count*4096-4:raise ValueError('Corrupt/oversized region record')
            result[i]=raw[off:off+4+size]
    return result
def read_chunk(record):
    payload=record[5:];method=record[4]
    if method==2:payload=zlib.decompress(payload)
    elif method==1:payload=gzip.decompress(payload)
    elif method!=3:raise ValueError('External/unknown compression; repair refused')
    return n.File.parse(io.BytesIO(payload))
def serialize(chunk):
    buf=io.BytesIO();chunk.write(buf);data=zlib.compress(buf.getvalue())
    return (len(data)+1).to_bytes(4,'big')+b'\x02'+data
def pack_region(original,records):
    header=bytearray(original[:8192]);body=bytearray();sector=2
    for i,record in sorted(records.items()):
        size=(len(record)+4095)//4096
        if size>255:raise ValueError('Oversized chunk; repair refused')
        header[i*4:i*4+4]=sector.to_bytes(3,'big')+bytes([size]);body+=record+b'\0'*(size*4096-len(record));sector+=size
    return bytes(header+body)
def prepare(world,out,patch_path):
    world=world.resolve();out=out.resolve()
    if world==out or world in out.parents:raise ValueError('Repair output must be outside source world')
    if out.exists():raise ValueError('Output already exists; original candidate preserved')
    island=n.load(world/'data/tnc_sky_island_v5.dat')['data'];origin=np.array([int(island[k]) for k in ('OriginX','OriginY','OriginZ')])
    if str(island['Phase'])!='COMPLETE':raise ValueError('Base island is not complete')
    account=n.load(world/'data/tnc_adventure_v1.dat')['data']
    if any(hasattr(v,'get') and 'Owner' in v for v in account.get('Housing',{}).values()):raise ValueError('Owned claims found; per-claim repair review required')
    patch=n.load(patch_path);pal=patch['Palette'];groups={};checks=0
    for tile in patch['Tiles']:
        for x,y,z,b,a in np.asarray(tile['Changes']).reshape(-1,5):
            wx,wy,wz=(np.array([x,y,z])+origin).tolist();groups.setdefault((wx//16,wz//16),[]).append((wx,wy,wz,int(b),int(a)));checks+=1
    regions={};changes=0;conflicts=[];changed_chunks=0
    for (cx,cz),points in groups.items():
        name=f'r.{cx//32}.{cz//32}.mca'
        if name not in regions:
            raw=(world/'region'/name).read_bytes();regions[name]=[raw,region_chunks(raw),False]
        raw,records,_=regions[name];idx=cx%32+cz%32*32
        if idx not in records:raise ValueError(f'Missing original chunk {cx},{cz}')
        chunk=read_chunk(records[idx]);sections={int(s['Y']):s for s in chunk['sections']};updated=set();decoded={}
        bes={(int(e['x']),int(e['y']),int(e['z'])):e for e in chunk.get('block_entities',[])}
        for x,y,z,b,a in points:
            sy=y//16
            if sy not in sections:
                s=n.Compound({'Y':n.Byte(sy),'block_states':n.Compound({'palette':n.List[n.Compound]([n.Compound({'Name':n.String('minecraft:air')})])})});sections[sy]=s;chunk['sections'].append(s)
            s=sections[sy]
            if sy not in decoded:
                states=s.get('block_states',n.Compound({'palette':n.List[n.Compound]([n.Compound({'Name':n.String('minecraft:air')})])}));p=list(states['palette']);decoded[sy]=[p,decode(states),{state_key(v):i for i,v in enumerate(p)}]
            p,arr,ids=decoded[sy];i=(y%16)*256+(z%16)*16+x%16;actual=p[int(arr[i])];before,after=pal[b],pal[a]
            be=bes.get((x,y,z));campfire=be is not None and str(be.get('id'))=='minecraft:campfire' and state_key(actual)==state_key(after)
            if not compatible(actual,before,after) or (be is not None and not campfire):
                if len(conflicts)<30:conflicts.append({'position':[x,y,z],'actual':state_key(actual),'before':state_key(before),'after':state_key(after),'container':be is not None})
                continue
            if state_key(actual)!=state_key(after):
                key=state_key(after)
                if key not in ids:ids[key]=len(p);p.append(copy.deepcopy(after))
                arr[i]=ids[key];updated.add(sy);changes+=1
        if updated:
            for sy in updated:
                p,arr,_=decoded[sy];sections[sy]['block_states']=encode(p,arr)
                sections[sy].pop('BlockLight',None);sections[sy].pop('SkyLight',None)
            # Vanilla rebuilds missing heightmaps; lighting invalidation is explicit.
            chunk.pop('Heightmaps',None);chunk['isLightOn']=n.Byte(0)
            records[idx]=serialize(chunk);regions[name][2]=True;changed_chunks+=1
    if conflicts:raise ValueError(json.dumps({'repair_refused_conflicts':conflicts},ensure_ascii=False))
    out.mkdir(parents=True);(out/'region').mkdir();(out/'data').mkdir()
    report={'source':str(world),'origin':origin.tolist(),'checked_voxels':checks,'changed_voxels':changes,'changed_chunks':changed_chunks,'regions':[],'overlay_hash':hashlib.sha256(patch_path.read_bytes()).hexdigest()}
    for name,(raw,records,changed) in regions.items():
        if not changed:continue
        result=pack_region(raw,records);(out/'region'/name).write_bytes(result)
        # Re-read every output record before permitting installation.
        reread=region_chunks(result)
        for i,record in records.items():assert reread[i]==record
        report['regions'].append({'name':name,'original_hash':hashlib.sha256(raw).hexdigest(),'repaired_hash':hashlib.sha256(result).hexdigest()})
    overlay=n.load(world/'data/tnc_sky_landscape_v1.dat');overlay['data']['Phase']=n.Int(2);overlay['data']['Tile']=n.Int(0);overlay['data']['Error']=n.String('')
    if str(overlay['data']['Hash'])!=report['overlay_hash']:raise ValueError('Patch identity changed')
    overlay.save(out/'data/tnc_sky_landscape_v1.dat')
    for name in ['tnc_sky_island_v5.dat','tnc_adventure_v1.dat']:(out/'data'/name).write_bytes((world/'data'/name).read_bytes())
    (out/'repair-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False));return report
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--patch',type=Path,default=ROOT/'src/main/resources/data/tnc/sky_island/landscape_v1.nbt');a=p.parse_args();prepare(a.world,a.output,a.patch)
