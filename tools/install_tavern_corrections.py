"""Bounded cellar + staff correction; stage first, verify, back up, then install.

Never rewrites the completed interior v1, quest book, island metadata or players.
Future worlds receive the same patch through TavernBasementUpgrade.
"""
from pathlib import Path
from collections import defaultdict
import argparse, copy, json, os, shutil, time
import nbtlib as n
from install_tavern import ROOT, LIVE, Regions, r, digest, packed_pos, translate_be, game_closed

PATCH=ROOT/'src/main/resources/data/tnc/tavern/basement_v1.nbt'
NATURAL={'minecraft:air','minecraft:stone','minecraft:andesite','minecraft:tuff','minecraft:deepslate','minecraft:dirt','minecraft:rooted_dirt','minecraft:coarse_dirt','minecraft:grass_block'}
REMOVED={f'guest_{i:02}' for i in range(1,7)}
PROTECTED=['level.dat','data/tnc_sky_island_v5.dat','data/tnc_sky_landscape_v1.dat','data/tnc_tavern_v1.dat']

def protected(world):
    files=[world/p for p in PROTECTED]
    files.extend(p for folder in ['playerdata','advancements','stats'] for p in (world/folder).glob('*') if p.is_file())
    files.extend((world/'data').glob('*ftb*'))
    return {str(p.relative_to(world)):digest(p) for p in files if p.is_file()}

def prepare(world,out):
    out.mkdir(parents=True)
    island=n.load(world/'data/tnc_sky_island_v5.dat')['data'];origin=tuple(int(island['Origin'+a]) for a in 'XYZ')
    report={'world':str(world),'stage':str(out),'origin':origin,'protected':protected(world),'files':[]}
    old=world/'data/tnc_tavern_v1.dat'
    if not old.exists() or int(n.load(old)['data']['Phase'])!=2:
        report['pending_runtime']=True
        report['reason']='Initial tavern not installed yet; correction follows automatic initial construction'
        (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');return report
    plan=n.load(PATCH);palette=plan['Palette'];bes={tuple(map(int,t['Pos'])):t['Data'] for t in plan['BlockEntities']}
    groups=defaultdict(list)
    for c in plan['Changes']:
        local=tuple(map(int,c['Pos']));pos=tuple(local[i]+origin[i] for i in range(3))
        groups[pos[0]//16,pos[2]//16].append((pos,local,int(c['After'])))
    regions=Regions(world,'region');changed=0;retained=0
    for key,points in groups.items():
        chunk=regions.chunk(*key);sections={int(s['Y']):s for s in chunk['sections']};decoded={};updates=set()
        entities={tuple(int(be[a]) for a in 'xyz'):be for be in chunk.get('block_entities',[])};be_changed=False
        for pos,local,a in points:
            x,y,z=pos;sy=y//16
            if sy not in decoded:
                data=sections[sy]['block_states'];p=list(data['palette']);decoded[sy]=[p,r.decode(data),{r.state_key(v):i for i,v in enumerate(p)}]
            p,arr,ids=decoded[sy];index=(y%16)*256+(z%16)*16+x%16;actual=p[int(arr[index])];after=palette[a]
            same=r.state_key(actual)==r.state_key(after);be=entities.get(pos)
            if not same and (str(actual['Name']) not in NATURAL or be is not None):
                raise ValueError(f'{world.name}: protected player block/container at {pos}: {r.state_key(actual)}')
            if not same:
                code=r.state_key(after)
                if code not in ids:ids[code]=len(p);p.append(copy.deepcopy(after))
                arr[index]=ids[code];updates.add(sy);changed+=1
            if local in bes:
                if same and be is not None:retained+=1
                else:entities[pos]=translate_be(bes[local],origin);be_changed=True
        if updates or be_changed:
            for sy in updates:
                sections[sy]['block_states']=r.encode(*decoded[sy][:2]);sections[sy].pop('BlockLight',None);sections[sy].pop('SkyLight',None)
            chunk['block_entities']=n.List[n.Compound](entities.values());chunk.pop('Heightmaps',None);chunk['isLightOn']=n.Byte(0);regions.dirty.add(key)
    actors=Regions(world,'entities');moved=[];removed=[]
    for cx in range((origin[0]+416)//16,(origin[0]+487)//16+1):
        for cz in range((origin[2]+270)//16,(origin[2]+328)//16+1):
            chunk=actors.chunk(cx,cz,True)
            for entity in list(chunk['Entities']):
                local=[float(v)-origin[i] for i,v in enumerate(entity['Pos'])]
                if not (416<=local[0]<=487 and 80<=local[1]<=133 and 270<=local[2]<=328):continue
                typ=str(entity.get('id',''));role=str(entity.get('TncServiceRole',''))
                if typ=='tnc:tavern_guest' and str(entity.get('TavernSeat','')) in REMOVED:
                    removed.append(str(entity['TavernSeat']));chunk['Entities'].remove(entity);actors.dirty.add((cx,cz))
                elif typ=='tnc:self' or (typ=='tnc:town_service' and role=='guild'):
                    target,yaw=((465.5,90,292.5),90) if typ=='tnc:self' else ((436.5,90,286.5),0)
                    entity['Pos']=n.List[n.Double]([target[i]+origin[i] for i in range(3)])
                    entity['Rotation']=n.List[n.Float]([yaw,0]);entity['Motion']=n.List[n.Double]([0,0,0])
                    chunk['Entities'].remove(entity);actors.dirty.add((cx,cz));moved.append(entity)
    for entity in moved:
        x,y,z=map(float,entity['Pos']);key=(int(x//16),int(z//16));actors.chunk(*key,True)['Entities'].append(entity);actors.dirty.add(key)
    report['files']=regions.write(out)+actors.write(out)
    (out/'data').mkdir(exist_ok=True)
    n.File({'data':n.Compound({'Phase':n.Int(2),'Hash':n.String(digest(PATCH)),'Origin':n.Long(packed_pos(*origin)),'Error':n.String('')})},gzipped=True).save(out/'data/tnc_tavern_basement_v1.dat')
    path=world/'data/tnc_npc_placements.dat'
    if path.exists():
        data=n.load(path)
        for entry in data['data'].get('Npcs',[]):
            if str(entry.get('Id'))=='self':
                entry['Anchor']=n.String('ORIGIN')
                for key,val in zip(['DX','DY','DZ'],[465,90,292]):entry[key]=n.Int(val)
        data['data'].pop('PreviousSelf',None);data.save(out/'data'/path.name)
    path=world/'data/tnc_adventure_v1.dat'
    if path.exists():
        data=n.load(path);guild=data['data'].setdefault('Housing',n.Compound()).setdefault('TownServices',n.Compound()).setdefault('guild',n.Compound())
        guild['LayoutVersion']=n.Int(0)
        data.save(out/'data'/path.name)
    for path in (out/'data').iterdir():
        original=world/'data'/path.name;report['files'].append({'file':f'data/{path.name}','before':digest(original) if original.exists() else None,'after':digest(path)})
    report.update(checked_blocks=len(plan['Changes']),changed_blocks=changed,retained_furniture=retained,removed_guests=removed,staff_moved=len(moved))
    (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    return report

def install(stage):
    game_closed();reports=[json.loads(p.read_text(encoding='utf8')) for p in stage.glob('*/report.json')]
    assert reports and {Path(t['world']) for t in reports}=={p for p in (LIVE/'saves').iterdir() if (p/'data/tnc_sky_island_v5.dat').exists()}
    for t in reports:
        world=Path(t['world']);out=Path(t['stage']);assert protected(world)==t['protected']
        for f in t['files']:
            p=world/f['file'];assert (digest(p) if p.exists() else None) in (f['before'],f['after']);assert digest(out/f['file'])==f['after']
    journal=stage/'install-journal.json'
    if journal.exists():backup=Path(json.loads(journal.read_text(encoding='utf8'))['backup'])
    else:
        backup=ROOT/'work/backups'/('tavern-corrections-'+time.strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
        journal.write_text(json.dumps({'backup':str(backup)},ensure_ascii=False),encoding='utf8')
    targets=[]
    for t in reports:
        for f in t['files']:
            p=Path(t['world'])/f['file'];b=backup/Path(t['world']).name/f['file'];b.parent.mkdir(parents=True,exist_ok=True)
            if b.exists():assert digest(b)==f['before'],'Original backup changed'
            elif p.exists() and digest(p)==f['before']:shutil.copy2(p,b)
            elif f['before'] is not None:raise ValueError('Original backup missing; cannot resume safely')
            targets.append((p,b,Path(t['stage'])/f['file'],f))
    try:
        for p,b,source,f in targets:
            if p.exists() and digest(p)==f['after']:continue
            p.parent.mkdir(parents=True,exist_ok=True);tmp=p.with_name(p.name+'.tnc-cellar-tmp');shutil.copy2(source,tmp)
            assert digest(tmp)==f['after'];os.replace(tmp,p);assert digest(p)==f['after']
        for t in reports:assert protected(Path(t['world']))==t['protected']
    except Exception:
        for p,b,source,f in targets:
            if b.exists():
                tmp=p.with_name(p.name+'.tnc-cellar-rollback');shutil.copy2(b,tmp);assert digest(tmp)==f['before'];os.replace(tmp,p)
            elif p.exists() and f['before'] is None:p.unlink()
        raise
    receipt={'backup':str(backup),'worlds':reports}
    (ROOT/'work/tavern-revision-20261005/world-install.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'installed_worlds':len(reports),'backup':str(backup)},ensure_ascii=False))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--install',type=Path);args=parser.parse_args()
    if args.install:install(args.install)
    else:
        stage=ROOT/'work'/('tavern-corrections-stage-'+time.strftime('%Y%m%d-%H%M%S'));stage.mkdir()
        for world in sorted((LIVE/'saves').iterdir()):
            if (world/'data/tnc_sky_island_v5.dat').exists():
                report=prepare(world,stage/world.name);print(json.dumps({k:v for k,v in report.items() if k not in ['files','protected']},ensure_ascii=False))
        print('Staged at',stage)
