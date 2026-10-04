"""Export the original cellar, including the air which must replace island soil."""
from pathlib import Path
import sys,copy,json,gzip,io
import nbtlib as n
from export_tavern_interior import LIVE,ROOT,SOURCE,repair,tag
from export_stonecrest_fortress import WorldReader

BOUNDS=(425,81,287,440,89,298)
NATURAL={'minecraft:air','minecraft:stone','minecraft:andesite','minecraft:tuff','minecraft:deepslate','minecraft:dirt','minecraft:rooted_dirt','minecraft:coarse_dirt','minecraft:grass_block'}

def main():
    island=n.load(SOURCE/'data/tnc_sky_island_v5.dat')['data'];origin=tuple(int(island['Origin'+a]) for a in 'XYZ');reader=WorldReader(SOURCE)
    reference=LIVE/'saves/新的世界 (1)';s=n.load(reference/'data/tnc_sky_island_v5.dat')['data'];ref_origin=tuple(int(s['Origin'+a]) for a in 'XYZ');before_reader=WorldReader(reference)
    palette=[];indices={};changes=[];positions=set()
    def index(t):
        key=repair.state_key(t)
        if key not in indices:indices[key]=len(palette);palette.append(t)
        return indices[key]
    for y in range(81,90):
        for x in range(425,441):
            for z in range(287,299):
                local=(x,y,z);after=reader.state(*(local[i]+origin[i] for i in range(3)))
                # Export the built room and its hollow space; leave random natural geology alone.
                if after.name in NATURAL and after.name!='minecraft:air':continue
                before=before_reader.state(*(local[i]+ref_origin[i] for i in range(3)))
                changes.append(n.Compound({'Pos':n.IntArray(local),'Before':n.Int(index(tag(before))),'After':n.Int(index(tag(after)))}));positions.add(local)
    bes=[]
    for cx in range((425+origin[0])//16,(440+origin[0])//16+1):
        for cz in range((287+origin[2])//16,(298+origin[2])//16+1):
            path=SOURCE/'region'/f'r.{cx//32}.{cz//32}.mca';records=repair.region_chunks(path.read_bytes());chunk=repair.read_chunk(records[cx%32+cz%32*32])
            for be in chunk.get('block_entities',[]):
                local=tuple(int(be[a])-origin[i] for i,a in enumerate('xyz'))
                if local not in positions:continue
                data=copy.deepcopy(be)
                for i,a in enumerate('xyz'):data[a]=n.Int(local[i])
                bes.append(n.Compound({'Pos':n.IntArray(local),'Data':data}))
    root=n.File({'Revision':n.Int(1),'Bounds':n.IntArray(BOUNDS),'Replaceable':n.List[n.String](n.String(s) for s in sorted(NATURAL)),
        'Palette':n.List[n.Compound](palette),'Changes':n.List[n.Compound](changes),'BlockEntities':n.List[n.Compound](bes),'Decor':n.List[n.Compound]()})
    data=io.BytesIO();root.write(data);target=ROOT/'src/main/resources/data/tnc/tavern/basement_v1.nbt';target.write_bytes(gzip.compress(data.getvalue(),mtime=0))
    atmosphere=ROOT/'src/main/resources/data/tnc/tavern/atmosphere_v1.json';a=json.loads(atmosphere.read_text(encoding='utf8'));retired=[f'guest_{i:02}' for i in range(1,7)]
    survey=json.loads((ROOT/'work/tavern-source-survey.json').read_text(encoding='utf8'))
    a['empty_seats']=[c['local'] for c in survey['chairs'] if c['local'][1]==90 and ('red_cushion' in c['block'])]
    a['guests']=[s for s in a['guests'] if s['id'] not in retired]
    # The bar's back walkway was outside the old music region.
    a['rooms'][1][3]=466.8
    for room in [[425.5,80.5,287.5,435.5,84.5,298.5],[432,81,294.5,440.5,90.5,298.5]]:
        if room not in a['rooms']:a['rooms'].append(room)
    atmosphere.write_bytes((json.dumps(a,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    report={'bounds':BOUNDS,'changes':len(changes),'block_entities':len(bes),'air':sum(str(palette[int(c['After'])]['Name'])=='minecraft:air' for c in changes),
        'guests':len(a['guests']),'empty_seats':a['empty_seats'],'source':str(SOURCE),'retired_guests':retired}
    (ROOT/'work/tavern-revision-20261005/export.json').write_bytes(json.dumps(report,ensure_ascii=False,indent=2).encode('utf8'));print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':sys.stdout.reconfigure(encoding='utf8');main()
