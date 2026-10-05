"""Move only SELF one block toward the bar. Preserve every block and other actor.

Uses the journaled atomic installer from install_tavern_corrections; no cellar
or interior patch is reapplied. Defaults v9 handles future worlds automatically.
"""
from pathlib import Path
import copy,json,time,argparse
import nbtlib as n
from install_tavern import ROOT,LIVE,Regions,digest
from install_tavern_corrections import install,protected

def prepare(world,out):
    out.mkdir(parents=True)
    island=n.load(world/'data/tnc_sky_island_v5.dat')['data'];origin=tuple(int(island['Origin'+a]) for a in 'XYZ')
    report={'world':str(world),'stage':str(out),'origin':origin,'protected':protected(world),'files':[]}
    # All block regions are protected; this correction is actor-only.
    report['protected'].update({str(p.relative_to(world)):digest(p) for p in (world/'region').glob('*.mca')})
    actors=Regions(world,'entities');moved=[]
    for cx in range((origin[0]+416)//16,(origin[0]+487)//16+1):
        for cz in range((origin[2]+270)//16,(origin[2]+328)//16+1):
            chunk=actors.chunk(cx,cz,True)
            for entity in list(chunk['Entities']):
                if str(entity.get('id'))!='tnc:self':continue
                local=[float(v)-origin[i] for i,v in enumerate(entity['Pos'])]
                if not (416<=local[0]<=487 and 80<=local[1]<=133 and 270<=local[2]<=328):continue
                uuid=list(map(int,entity['UUID']))
                entity['Pos']=n.List[n.Double]([origin[0]+464.5,origin[1]+90,origin[2]+292.5]);entity['Rotation']=n.List[n.Float]([90,0]);entity['Motion']=n.List[n.Double]([0,0,0])
                chunk['Entities'].remove(entity);actors.dirty.add((cx,cz));moved.append((entity,uuid))
    for entity,uuid in moved:
        x,y,z=map(float,entity['Pos']);key=(int(x//16),int(z//16));actors.chunk(*key,True)['Entities'].append(entity);actors.dirty.add(key)
    assert len(moved)<=1,'Refuse ambiguous duplicate SELF migration'
    report['files']=actors.write(out)
    path=world/'data/tnc_npc_placements.dat'
    if path.exists():
        data=n.load(path)
        for entry in data['data'].get('Npcs',[]):
            if str(entry.get('Id'))!='self':continue
            if not moved and 'PreviousSelf' not in data['data']:
                previous=copy.deepcopy(entry);previous.pop('Id',None);data['data']['PreviousSelf']=previous
            elif moved:data['data'].pop('PreviousSelf',None)
            entry['Anchor']=n.String('ORIGIN')
            for key,val in zip(['DX','DY','DZ'],[464,90,292]):entry[key]=n.Int(val)
        data['data']['DefaultsVersion']=n.Int(9)
        (out/'data').mkdir(exist_ok=True);data.save(out/'data'/path.name)
        report['files'].append({'file':f'data/{path.name}','before':digest(path),'after':digest(out/'data'/path.name)})
    report['staff_moved']=len(moved);report['preserved_self_uuids']=[u for e,u in moved]
    (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--install',type=Path);args=parser.parse_args()
    if args.install:install(args.install,ROOT/'work/tavern-access-20261005-world-install.json')
    else:
        stage=ROOT/'work'/('tavern-access-stage-'+time.strftime('%Y%m%d-%H%M%S'));stage.mkdir()
        for world in sorted((LIVE/'saves').iterdir()):
            if (world/'data/tnc_sky_island_v5.dat').exists():
                report=prepare(world,stage/world.name);print(json.dumps({k:v for k,v in report.items() if k not in ['protected','files']},ensure_ascii=False))
        print('Staged at',stage)
