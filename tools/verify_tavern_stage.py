"""Read-only audit of staged tavern blocks, container data and untouched chunks."""
from pathlib import Path
import sys, json, copy
import numpy as np
import nbtlib as n
from install_tavern import ROOT, PATCH, r, Regions, translate_be

def blocks(section):
    data=section['block_states'];palette=[repr(r.state_key(state)) for state in data['palette']]
    return np.array(palette,dtype=object)[r.decode(data)]

def audit(stage):
    plan=n.load(PATCH);palette=plan['Palette'];source_be={tuple(map(int,entry['Pos'])):entry['Data'] for entry in plan['BlockEntities']};reports=[]
    for path in stage.glob('*/report.json'):
        report=json.loads(path.read_text(encoding='utf8'));world=Path(report['world']);out=Path(report['stage']);origin=report['origin']
        if report.get('pending_runtime'):reports.append({'world':world.name,'pending_runtime':True});continue
        original=Regions(world,'region');after={};allowed={tuple(int(c['Pos'][i])+origin[i] for i in range(3)) for c in plan['Changes']};retained=0
        def chunk(cx,cz):
            if (cx,cz) not in after:
                file=out/'region'/f'r.{cx//32}.{cz//32}.mca'
                after[cx,cz]=r.read_chunk(r.region_chunks(file.read_bytes())[cx%32+cz%32*32]) if file.exists() else original.chunk(cx,cz)
            return after[cx,cz]
        def state(pos):
            x,y,z=pos;data=next(s for s in chunk(x//16,z//16)['sections'] if int(s['Y'])==y//16)['block_states'];idx=int(r.decode(data)[y%16*256+z%16*16+x%16]);return r.state_key(data['palette'][idx])
        for c in plan['Changes']:
            local=tuple(map(int,c['Pos']));pos=tuple(local[i]+origin[i] for i in range(3));assert state(pos)==r.state_key(palette[int(c['After'])]),pos
            if local in source_be:
                def find_be(data):return next((be for be in data.get('block_entities',[]) if tuple(int(be[a]) for a in 'xyz')==pos),None)
                old=find_be(original.chunk(pos[0]//16,pos[2]//16));new=find_be(chunk(pos[0]//16,pos[2]//16));assert new is not None,pos
                if old is not None and str(old['id'])==str(new['id']):assert new==old,('Existing container changed',pos);retained+=1
                elif local!=(432,91,285):assert new==translate_be(source_be[local],origin),('Source furniture data changed',pos)
        verified=0
        for file in out.glob('region/*.mca'):
            old_records=r.region_chunks((world/'region'/file.name).read_bytes());new_records=r.region_chunks(file.read_bytes());rx,rz=map(int,file.name.split('.')[1:3])
            assert old_records.keys()==new_records.keys()
            for idx,record in old_records.items():
                if record==new_records[idx]:continue
                cx,cz=rx*32+idx%32,rz*32+idx//32;old=r.read_chunk(record);new=r.read_chunk(new_records[idx]);old_sections={int(s['Y']):s for s in old['sections']};new_sections={int(s['Y']):s for s in new['sections']}
                for sy in old_sections.keys()|new_sections.keys():
                    blank=np.full(4096,repr(r.state_key(n.Compound({'Name':n.String('minecraft:air')}))),dtype=object)
                    a=blocks(old_sections[sy]) if sy in old_sections else blank;b=blocks(new_sections[sy]) if sy in new_sections else blank
                    for index in np.flatnonzero(a!=b):
                        pos=(cx*16+int(index)%16,sy*16+int(index)//256,cz*16+(int(index)//16)%16);assert pos in allowed,('Unrelated block changed',pos)
                    verified+=4096
                old_be={tuple(int(be[a]) for a in 'xyz'):be for be in old.get('block_entities',[])};new_be={tuple(int(be[a]) for a in 'xyz'):be for be in new.get('block_entities',[])}
                for pos in old_be.keys()|new_be.keys():
                    if pos not in allowed:assert old_be.get(pos)==new_be.get(pos),('Unrelated container changed',pos)
        for local in [(430,90,294),(434,90,285)]:
            pos=tuple(origin[i]+local[i] for i in range(3));assert "minecraft:air" in state(pos) and "minecraft:air" in state((pos[0],pos[1]+1,pos[2]));assert "minecraft:air" not in state((pos[0],pos[1]-1,pos[2]))
        reports.append({'world':world.name,'checked_patch_blocks':len(plan['Changes']),'retained_containers':retained,'audited_chunk_blocks':verified,'unexpected_changes':0})
    assert len(reports)==4
    output={'stage':str(stage),'worlds':reports,'failures':[]};(ROOT/'work/tavern-stage-audit.json').write_bytes(json.dumps(output,ensure_ascii=False,indent=2).encode('utf8'));print(json.dumps(output,ensure_ascii=False))

if __name__=='__main__':sys.stdout.reconfigure(encoding='utf8');audit(Path(sys.argv[1]))
