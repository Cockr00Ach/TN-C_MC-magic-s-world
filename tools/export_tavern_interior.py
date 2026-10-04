"""Export only the existing tavern envelope, including removed blocks and furniture NBT.

The source world is read-only. The original island/landscape resources are unchanged.
"""
from pathlib import Path
import sys, json, io, gzip, copy, importlib.util
from collections import Counter
import nbtlib as n
from export_stonecrest_fortress import WorldReader

ROOT = Path(__file__).resolve().parents[1]
LIVE = Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
SOURCE = LIVE/'saves/RouchNao-内饰试玩-第一版-20261003'
BOUNDS = (416,89,270,486,131,327)  # exact original tavern envelope, inclusive
spec=importlib.util.spec_from_file_location('repair', ROOT/'tools/repair-town-landscape.py')
repair=importlib.util.module_from_spec(spec);spec.loader.exec_module(repair)

def tag(state):
    t=n.Compound({'Name':n.String(state.name)})
    if state.properties:t['Properties']=n.Compound({k:n.String(v) for k,v in state.properties})
    return t

def inside(p):
    return all(BOUNDS[i]<=p[i]<=BOUNDS[i+3] for i in range(3))

def export():
    island=n.load(SOURCE/'data/tnc_sky_island_v5.dat')['data']
    origin=tuple(int(island['Origin'+axis]) for axis in 'XYZ')
    old=n.load(ROOT/'src/main/resources/data/tnc/sky_island/landscape_v1.nbt')
    baseline={}
    for tile in old['Tiles']:
        values=tile['Changes'].tolist()
        for i in range(0,len(values),5):
            p=tuple(values[i:i+3])
            if inside(p):baseline[p]=old['Palette'][values[i+4]]
    reader=WorldReader(SOURCE);pal=[];ids={};changes=[];chairs=[];names=Counter();natural_skips=0
    def pid(t):
        key=repair.state_key(t)
        if key not in ids:ids[key]=len(pal);pal.append(copy.deepcopy(t))
        return ids[key]
    natural={'minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:grass','minecraft:tall_grass','minecraft:fern','minecraft:large_fern','minecraft:dirt_path'}
    for y in range(BOUNDS[1],BOUNDS[4]+1):
        for z in range(BOUNDS[2],BOUNDS[5]+1):
            for x in range(BOUNDS[0],BOUNDS[3]+1):
                p=(x,y,z);state=reader.state(*(p[i]+origin[i] for i in range(3)))
                after=tag(state);before=baseline.get(p,n.Compound({'Name':n.String('minecraft:air')}))
                if state.namespace=='conquest' and ('chair' in state.path or 'stool' in state.path):
                    chairs.append({'local':list(p),'block':state.name,'properties':dict(state.properties)})
                if repair.state_key(before)!=repair.state_key(after):
                    # Random grass spreading and garden mowing are not the inn's furnishings.
                    if str(before['Name']) in natural and state.name in natural:natural_skips+=1;continue
                    changes.append(n.Compound({'Pos':n.IntArray(p),'Before':n.Int(pid(before)),'After':n.Int(pid(after))}))
                    names[state.name]+=1
    bes=[];decor=[];changed_positions={tuple(int(v) for v in c['Pos']) for c in changes}
    for cx in range((BOUNDS[0]+origin[0])//16,(BOUNDS[3]+origin[0])//16+1):
        for cz in range((BOUNDS[2]+origin[2])//16,(BOUNDS[5]+origin[2])//16+1):
            path=SOURCE/'region'/f'r.{cx//32}.{cz//32}.mca'
            records=repair.region_chunks(path.read_bytes());chunk=repair.read_chunk(records[cx%32+cz%32*32])
            for be in chunk.get('block_entities',[]):
                local=tuple(int(be[a])-origin[i] for i,a in enumerate('xyz'))
                if inside(local):
                    data=copy.deepcopy(be)
                    for i,a in enumerate('xyz'):data[a]=n.Int(local[i])
                    # Seating entities are runtime state, never copied from another world.
                    data.pop('SeatUUID',None)
                    bes.append(n.Compound({'Pos':n.IntArray(local),'Data':data}))
                    if str(data['id'])=='conquest:seat' and not any(tuple(c['local'])==local for c in chairs):
                        state=reader.state(*(local[i]+origin[i] for i in range(3)))
                        chairs.append({'local':list(local),'block':state.name,'properties':dict(state.properties)})
                    if local not in changed_positions:
                        after=tag(reader.state(*(local[i]+origin[i] for i in range(3))))
                        before=baseline.get(local,n.Compound({'Name':n.String('minecraft:air')}))
                        changes.append(n.Compound({'Pos':n.IntArray(local),'Before':n.Int(pid(before)),'After':n.Int(pid(after))}))
                        changed_positions.add(local)
            epath=SOURCE/'entities'/f'r.{cx//32}.{cz//32}.mca'
            if not epath.exists():continue
            erecords=repair.region_chunks(epath.read_bytes());idx=cx%32+cz%32*32
            if idx not in erecords:continue
            for entity in repair.read_chunk(erecords[idx]).get('Entities',[]):
                local=[float(v)-origin[i] for i,v in enumerate(entity['Pos'])]
                if inside(local) and str(entity['id']) in {'minecraft:painting','minecraft:item_frame','minecraft:glow_item_frame','minecraft:armor_stand'}:
                    data=copy.deepcopy(entity);data.pop('UUID',None);data['Pos']=n.List[n.Double](local)
                    for i,a in enumerate(['TileX','TileY','TileZ']):
                        if a in data:data[a]=n.Int(int(data[a])-origin[i])
                    decor.append(data)
    root=n.File({'Revision':n.Int(1),'Bounds':n.IntArray(BOUNDS),'Palette':n.List[n.Compound](pal),
                 'Changes':n.List[n.Compound](changes),'BlockEntities':n.List[n.Compound](bes),'Decor':n.List[n.Compound](decor)})
    out=ROOT/'src/main/resources/data/tnc/tavern/interior_v1.nbt';out.parent.mkdir(parents=True,exist_ok=True)
    buf=io.BytesIO();root.write(buf);out.write_bytes(gzip.compress(buf.getvalue(),mtime=0))
    report={'source':str(SOURCE),'origin':origin,'bounds':BOUNDS,'changed_blocks':len(changes),'block_entities':len(bes),'decor':len(decor),
            'chairs':chairs,'changes_by_block':dict(names),'natural_changes_ignored':natural_skips,'block_entity_types':dict(Counter(str(b['Data']['id']) for b in bes))}
    target=ROOT/'work/tavern-source-survey.json';target.parent.mkdir(exist_ok=True);target.write_bytes(json.dumps(report,ensure_ascii=False,indent=2).encode('utf8'))
    print(json.dumps({k:v for k,v in report.items() if k not in {'chairs','changes_by_block'}},ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf8');export()
