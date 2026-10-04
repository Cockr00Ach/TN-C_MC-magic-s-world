"""Stage, audit and install the tavern-only patch into existing saves.

Region records outside the explicit patch are byte-for-byte preserved. Source save,
player files, FTB quests and all island-generation data are never rewritten.
"""
from pathlib import Path
import sys, json, copy, hashlib, uuid, shutil, time, importlib.util, subprocess, os
from collections import defaultdict
import nbtlib as n

ROOT=Path(__file__).resolve().parents[1]
LIVE=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
PATCH=ROOT/'src/main/resources/data/tnc/tavern/interior_v1.nbt'
spec=importlib.util.spec_from_file_location('repair',ROOT/'tools/repair-town-landscape.py');r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def packed_pos(x,y,z):
    value=((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&0xfff)
    return value-(1<<64) if value>=1<<63 else value
def translate_be(raw,origin):
    data=copy.deepcopy(raw)
    for i,a in enumerate('xyz'):data[a]=n.Int(int(data[a])+origin[i])
    return data

class Regions:
    def __init__(self,world,kind):self.world=world;self.kind=kind;self.regions={};self.chunks={};self.dirty=set()
    def chunk(self,cx,cz,create=False):
        key=(cx,cz)
        if key in self.chunks:return self.chunks[key]
        name=f'r.{cx//32}.{cz//32}.mca'
        if name not in self.regions:
            path=self.world/self.kind/name;raw=path.read_bytes() if path.exists() else bytes(8192)
            self.regions[name]=[raw,r.region_chunks(raw),path]
        records=self.regions[name][1];idx=cx%32+cz%32*32
        if idx in records:chunk=r.read_chunk(records[idx])
        elif create:chunk=n.File({'DataVersion':n.Int(3465),'Position':n.IntArray([cx,cz]),'Entities':n.List[n.Compound]()})
        else:raise ValueError(f'Missing chunk {self.world.name}/{cx},{cz}')
        self.chunks[key]=chunk;return chunk
    def write(self,out):
        names=set()
        for cx,cz in self.dirty:
            name=f'r.{cx//32}.{cz//32}.mca';self.regions[name][1][cx%32+cz%32*32]=r.serialize(self.chunks[cx,cz]);names.add(name)
        report=[]
        for name in sorted(names):
            raw,records,path=self.regions[name];packed=r.pack_region(raw,records);check=r.region_chunks(packed)
            assert check==records,'Repacked records changed unexpectedly'
            original=r.region_chunks(raw)
            for idx,record in original.items():
                cx=(int(name.split('.')[1])*32+idx%32);cz=(int(name.split('.')[2])*32+idx//32)
                if (cx,cz) not in self.dirty:assert check[idx]==record,'Unrelated chunk changed'
            target=out/self.kind/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(packed)
            report.append({'file':f'{self.kind}/{name}','before':hashlib.sha256(raw).hexdigest() if path.exists() else None,'after':digest(target)})
        return report

def prepare(world,out):
    out.mkdir(parents=True,exist_ok=False)
    island=n.load(world/'data/tnc_sky_island_v5.dat')['data'];origin=tuple(int(island['Origin'+a]) for a in 'XYZ')
    if str(island['Phase'])!='COMPLETE' or not int(island['LayoutReady']):raise ValueError('Base island incomplete')
    landscape=world/'data/tnc_sky_landscape_v1.dat'
    if not landscape.exists() or int(n.load(landscape)['data']['Phase'])!=2:
        report={'world':str(world),'stage':str(out),'origin':origin,'pending_runtime':True,'files':[],
                'reason':'Existing landscape/tavern shell has not been installed; automatic upgrade runs in order on world load',
                'protected_island_hash':digest(world/'data/tnc_sky_island_v5.dat')}
        (out/'report.json').write_bytes(json.dumps(report,ensure_ascii=False,indent=2).encode('utf8'));return report
    plan=n.load(PATCH);pal=plan['Palette'];bes={tuple(int(v) for v in t['Pos']):t['Data'] for t in plan['BlockEntities']};groups=defaultdict(list)
    for c in plan['Changes']:
        local=tuple(int(v) for v in c['Pos']);p=tuple(local[i]+origin[i] for i in range(3));groups[p[0]//16,p[2]//16].append((p,local,int(c['Before']),int(c['After'])))
    regions=Regions(world,'region');changed=0;conflicts=[];legacy=None;retained=0
    for (cx,cz),points in groups.items():
        chunk=regions.chunk(cx,cz);sections={int(s['Y']):s for s in chunk['sections']};decoded={};updated=set()
        entities={(int(be['x']),int(be['y']),int(be['z'])):be for be in chunk.get('block_entities',[])};be_changed=False
        for pos,local,b,a in points:
            x,y,z=pos;sy=y//16
            if sy not in sections:
                sections[sy]=n.Compound({'Y':n.Byte(sy),'block_states':n.Compound({'palette':n.List[n.Compound]([n.Compound({'Name':n.String('minecraft:air')})])})});chunk['sections'].append(sections[sy])
            if sy not in decoded:
                data=sections[sy]['block_states'];p=list(data['palette']);decoded[sy]=[p,r.decode(data),{r.state_key(v):i for i,v in enumerate(p)}]
            p,arr,ids=decoded[sy];index=(y%16)*256+(z%16)*16+x%16;actual=p[int(arr[index])];before,after=pal[b],pal[a];be=entities.get(pos)
            old_board=local==(426,94,285) and be is not None and str(be.get('id'))=='bountiful:board-be'
            same=r.state_key(actual)==r.state_key(after)
            if r.state_key(actual) not in (r.state_key(before),r.state_key(after)) and not old_board:
                conflicts.append({'position':pos,'actual':r.state_key(actual),'before':r.state_key(before),'after':r.state_key(after)});continue
            if be is not None and not same and not old_board:
                conflicts.append({'position':pos,'container':str(be.get('id'))});continue
            if old_board:legacy=copy.deepcopy(be)
            if not same:
                key=r.state_key(after)
                if key not in ids:ids[key]=len(p);p.append(copy.deepcopy(after))
                arr[index]=ids[key];updated.add(sy);changed+=1;entities.pop(pos,None);be_changed|=be is not None
            if local in bes:
                if same and be is not None:retained+=1
                else:entities[pos]=translate_be(bes[local],origin);be_changed=True
        if updated or be_changed:
            for sy in updated:sections[sy]['block_states']=r.encode(*decoded[sy][:2]);sections[sy].pop('BlockLight',None);sections[sy].pop('SkyLight',None)
            chunk['block_entities']=n.List[n.Compound](entities.values());chunk.pop('Heightmaps',None);chunk['isLightOn']=n.Byte(0);regions.dirty.add((cx,cz))
    if conflicts:
        (out/'conflicts.json').write_bytes(json.dumps(conflicts,ensure_ascii=False,indent=2).encode())
        raise ValueError(f'{world.name}: {len(conflicts)} conflicts; originals untouched, see {out}/conflicts.json')
    if legacy is not None:
        pos=tuple(origin[i]+(432,91,285)[i] for i in range(3));chunk=regions.chunk(pos[0]//16,pos[2]//16)
        for i,a in enumerate('xyz'):legacy[a]=n.Int(pos[i])
        for i,be in enumerate(chunk['block_entities']):
            if tuple(int(be[a]) for a in 'xyz')==pos:chunk['block_entities'][i]=legacy;break
        regions.dirty.add((pos[0]//16,pos[2]//16))
    entity_regions=Regions(world,'entities');decor_count=0;actors_moved=0
    bounds=[int(v) for v in plan['Bounds']]
    def local_inside(entity):
        p=[float(v)-origin[i] for i,v in enumerate(entity['Pos'])]
        return all(bounds[i]<=p[i]<=bounds[i+3]+1 for i in range(3))
    moved=[];all_existing=[]
    for (cx,cz) in groups:
        chunk=entity_regions.chunk(cx,cz,True)
        for entity in list(chunk['Entities']):
            all_existing.append(entity)
            role=str(entity.get('TncServiceRole',''))
            if local_inside(entity) and (str(entity.get('id'))=='tnc:self' or (str(entity.get('id'))=='tnc:town_service' and role=='guild')):
                target=(430.5,90,294.5) if str(entity['id'])=='tnc:self' else (434.5,90,285.5)
                entity['Pos']=n.List[n.Double]([target[i]+origin[i] for i in range(3)]);entity['Rotation']=n.List[n.Float]([180 if str(entity['id'])=='tnc:self' else 0,0]);entity['Motion']=n.List[n.Double]([0,0,0]);
                chunk['Entities'].remove(entity);entity_regions.dirty.add((cx,cz));moved.append(entity);actors_moved+=1
    for entity in moved:
        x,y,z=map(float,entity['Pos']);key=(int(x//16),int(z//16));entity_regions.chunk(*key,True)['Entities'].append(entity);entity_regions.dirty.add(key)
    for i,raw in enumerate(plan['Decor']):
        desired=[float(v)+origin[j] for j,v in enumerate(raw['Pos'])]
        if any(str(e['id'])==str(raw['id']) and all(abs(float(e['Pos'][j])-desired[j])<.3 for j in range(3)) for e in all_existing):continue
        entity=copy.deepcopy(raw);entity['Pos']=n.List[n.Double](desired)
        for j,key in enumerate(['TileX','TileY','TileZ']):
            if key in entity:entity[key]=n.Int(int(entity[key])+origin[j])
        uid=uuid.uuid5(uuid.NAMESPACE_URL,f'tnc-tavern:{world.name}:{origin}:{i}');entity['UUID']=n.IntArray([int.from_bytes(uid.bytes[j:j+4],'big',signed=True) for j in range(0,16,4)])
        key=(int(desired[0]//16),int(desired[2]//16));entity_regions.chunk(*key,True)['Entities'].append(entity);entity_regions.dirty.add(key);decor_count+=1
    files=regions.write(out)+entity_regions.write(out);(out/'data').mkdir(exist_ok=True)
    state=n.File({'data':n.Compound({'Phase':n.Int(2),'Hash':n.String(digest(PATCH)),'Origin':n.Long(packed_pos(*origin)),'Error':n.String('')})},gzipped=True);state.save(out/'data/tnc_tavern_v1.dat')
    path=world/'data/tnc_npc_placements.dat'
    if path.exists():
        data=n.load(path)
        for entry in data['data'].get('Npcs',[]):
            if str(entry.get('Id'))=='self':
                entry['Anchor']=n.String('ORIGIN')
                for key,val in zip(['DX','DY','DZ'],[430,90,294]):entry[key]=n.Int(val)
        data['data'].pop('PreviousSelf',None);data.save(out/'data'/path.name)
    path=world/'data/tnc_adventure_v1.dat'
    if path.exists():
        data=n.load(path);housing=data['data'].setdefault('Housing',n.Compound());records=housing.setdefault('TownServices',n.Compound());guild=records.setdefault('guild',n.Compound());guild['LayoutVersion']=n.Int(0);records['BoardPlaced']=n.Byte(1)
        if legacy is None:records['BoardInstalled']=n.Byte(0)
        data.save(out/'data'/path.name)
    for p in (out/'data').iterdir():
        original=world/'data'/p.name;files.append({'file':f'data/{p.name}','before':digest(original) if original.exists() else None,'after':digest(p)})
    report={'world':str(world),'stage':str(out),'origin':origin,'checked_blocks':len(plan['Changes']),'changed_blocks':changed,'retained_furniture':retained,'decor_added':decor_count,'actors_moved':actors_moved,'guest_seats':49,'files':files,'protected_island_hash':digest(world/'data/tnc_sky_island_v5.dat')}
    (out/'report.json').write_bytes(json.dumps(report,ensure_ascii=False,indent=2).encode('utf8'));return report

def game_closed():
    command="Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\\.exe$' -and $_.CommandLine -like '*元素觉醒1.4.3-魔改版-20260915*' } | Select-Object -ExpandProperty ProcessId"
    result=subprocess.run(['pwsh','-NoProfile','-Command',command],capture_output=True,text=True)
    if result.returncode or result.stdout.strip():raise RuntimeError('Game still running or process check failed; install refused')

def install(stage):
    game_closed();reports=[json.loads(p.read_text(encoding='utf8')) for p in stage.glob('*/report.json')]
    if len(reports)!=4:raise ValueError('Expected all four staged worlds')
    for report in reports:
        world=Path(report['world']);out=Path(report['stage'])
        if digest(world/'data/tnc_sky_island_v5.dat')!=report['protected_island_hash']:raise ValueError('Island metadata changed')
        for f in report['files']:
            p=world/f['file']
            if (digest(p) if p.exists() else None) not in (f['before'],f['after']) or digest(out/f['file'])!=f['after']:raise ValueError('Source or staged file changed; install refused')
    journal=stage/'install-journal.json'
    if journal.exists():backup=Path(json.loads(journal.read_text(encoding='utf8'))['backup'])
    else:
        backup=ROOT/'work/backups'/('tavern-'+time.strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
        journal.write_bytes(json.dumps({'backup':str(backup)},ensure_ascii=False).encode('utf8'))
    for report in reports:
        world=Path(report['world']);out=Path(report['stage'])
        for f in report['files']:
            p=world/f['file'];b=backup/world.name/f['file'];b.parent.mkdir(parents=True,exist_ok=True)
            if b.exists():
                if digest(b)!=f['before']:raise ValueError('Original backup hash mismatch')
            elif p.exists() and digest(p)==f['before']:shutil.copy2(p,b)
            elif f['before'] is not None:raise ValueError('Original backup missing; cannot resume safely')
    # All validation and backups complete before the first live write.
    for report in reports:
        world=Path(report['world']);out=Path(report['stage'])
        for f in report['files']:
            target=world/f['file'];target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists() and digest(target)==f['after']:continue
            temporary=target.with_name(target.name+'.tnc-tavern-tmp');shutil.copy2(out/f['file'],temporary)
            if digest(temporary)!=f['after']:raise ValueError('Temporary file hash mismatch')
            os.replace(temporary,target)
            if digest(target)!=f['after']:raise ValueError('Installed file hash mismatch')
        assert digest(world/'data/tnc_sky_island_v5.dat')==report['protected_island_hash']
    receipt={'backup':str(backup),'worlds':reports};(ROOT/'work/tavern-world-install-receipt.json').write_bytes(json.dumps(receipt,ensure_ascii=False,indent=2).encode());print(json.dumps({'installed_worlds':len(reports),'backup':str(backup)},ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf8')
    if len(sys.argv)>1 and sys.argv[1]=='--install':install(Path(sys.argv[2]))
    else:
        stage=ROOT/'work'/('tavern-stage-'+time.strftime('%Y%m%d-%H%M%S'));stage.mkdir()
        for world in sorted((LIVE/'saves').iterdir()):
            if (world/'data/tnc_sky_island_v5.dat').exists():
                report=prepare(world,stage/world.name);print(json.dumps({k:v for k,v in report.items() if k!='files'},ensure_ascii=False))
        print('Staged at',stage)
