"""Import the supplied casino building into a bounded, compare-and-set island overlay.

Reads the source save only. Never copies players, mobs, inventories or whole regions.
Requires nbtlib, numpy and Pillow; local packages may be supplied with PYTHONPATH.
"""
from pathlib import Path
import collections, copy, gzip, hashlib, importlib.util, io, json
import nbtlib as n
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'work/casino-map-20261011'
SOURCE = WORK / 'duchang'
PACK = ROOT / 'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc'
MIN = (64, 80, 232)
MAX = (124, 132, 307)
SHIFT = (82, 144, 286)  # v3: another four blocks down; terrace remains at y=95
spec = importlib.util.spec_from_file_location('repair', ROOT / 'tools/repair-town-landscape.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)

def inside(p):
    return all(MIN[i] <= p[i] <= MAX[i] for i in range(3))

def tag(name, **props):
    t = n.Compound({'Name': n.String(name)})
    if props:
        t['Properties'] = n.Compound({k: n.String(v) for k, v in props.items()})
    return t

def shifted(p):
    return tuple(int(p[i]) + SHIFT[i] for i in range(3))

def main():
    air = tag('minecraft:air')
    before = {}
    manifest = json.loads((PACK / 'sky_island/manifest.json').read_text(encoding='utf8'))
    for piece in manifest['pieces']:
        off, size = piece['offset'], piece['size']
        if any(off[i] > MAX[i] or off[i] + size[i] <= MIN[i] for i in range(3)):
            continue
        t = n.load(PACK / 'structures' / (piece['resource'].split(':')[1] + '.nbt'))
        for b in t['blocks']:
            p = tuple(int(b['pos'][i]) + off[i] for i in range(3))
            if inside(p):
                state = t['palette'][int(b['state'])]
                if str(state['Name']) != 'minecraft:structure_void':
                    before[p] = copy.deepcopy(state)
    patch = n.load(ROOT / 'src/main/resources/data/tnc/sky_island/landscape_v1.nbt')
    for tile in patch['Tiles']:
        values = tile['Changes']
        for i in range(0, len(values), 5):
            p = tuple(int(v) for v in values[i:i+3])
            if inside(p):
                before[p] = copy.deepcopy(patch['Palette'][int(values[i+4])])

    after = {p: copy.deepcopy(t) for p, t in before.items()}
    # Fill a retaining platform below the original map's ground. Clear only its lot.
    for x in range(64, 112):
        for z in range(232, 300):
            for y in range(80, 95):
                current = str(after.get((x,y,z), air)['Name'])
                if current == 'minecraft:air' or current.endswith(('_leaves', '_log')) or y >= 90:
                    after[x,y,z] = tag('minecraft:stone_bricks' if x in (64,111) or z in (232,299) else 'minecraft:stone')
            after[x,95,z] = tag('minecraft:grass_block', snowy='false')
            for y in range(96, 133):
                after[x,y,z] = air

    for x in range(66,110):
        for z in range(234,298):
            for y in range(84,133):after[x,y,z]=air

    furniture = {}
    source_floor=set()
    source_counts = collections.Counter()
    for path in (SOURCE / 'region').glob('*.mca'):
        for record in r.region_chunks(path.read_bytes()).values():
            c = r.read_chunk(record)
            cx, cz = int(c['xPos']), int(c['zPos'])
            if cx < -1 or cx > 1 or cz < -4 or cz > 0:
                continue
            for section in c.get('sections', []):
                if 'block_states' not in section:
                    continue
                y0 = int(section['Y']) * 16
                if y0 > -26 or y0 + 15 < -60:
                    continue
                states = section['block_states']
                pal, values = states['palette'], r.decode(states)
                for i in range(4096):
                    p = (cx*16+(i&15), y0+(i>>8), cz*16+((i>>4)&15))
                    if not (-16 <= p[0] <= 27 and -60 <= p[1] <= -26 and -52 <= p[2] <= 11):
                        continue
                    t = copy.deepcopy(pal[int(values[i])])
                    name = str(t['Name'])
                    if name == 'minecraft:air':
                        continue
                    if any(v in name for v in ('command_block', 'structure_block', 'jigsaw')):
                        raise ValueError('Unexpected control block: ' + name)
                    after[shifted(p)] = t
                    if p[1] in (-56,-55) and name not in {'minecraft:air','minecraft:grass_block'}:
                        source_floor.add((shifted(p)[0],shifted(p)[2]))
                    source_counts[name] += 1
            for raw in c.get('block_entities', []):
                p = tuple(int(raw[k]) for k in 'xyz')
                if not (-16 <= p[0] <= 27 and -60 <= p[1] <= -26 and -52 <= p[2] <= 11):
                    continue
                t = copy.deepcopy(raw)
                for key in ('x','y','z','Items','LootTable','LootTableSeed','UUID','Owner','SeatUUID'):
                    t.pop(key, None)
                furniture[shifted(p)] = t

    # Bury the outside of the basement, while its floor and interior stay hollow.
    for x in range(66,110):
        for z in range(234,298):
            if (x,z) in source_floor:continue
            for y in range(84,96):
                if str(after.get((x,y,z),air)['Name']) in {'minecraft:air','minecraft:grass_block'}:
                    after[x,y,z]=tag('minecraft:grass_block',snowy='false') if y==95 else tag('minecraft:dirt')

    # The original entrance stairs are retained. The apron meets the town's west edge.
    for x in range(95, 125):
        top = 95 + max(0, (x-110)//2)
        for z in range(300, 305):
            for y in range(80, top):
                if str(before.get((x,y,z),air)['Name']) == 'minecraft:air' or y >= 94:
                    after[x,y,z] = tag('minecraft:stone')
            after[x,top,z] = tag('minecraft:stone_brick_slab',type='bottom',waterlogged='false') if x>110 and x%2 else tag('minecraft:stone_bricks')
            for y in range(top+1,133):
                # Apron is on natural terrain; exporter verifies no architecture is cut.
                old = str(before.get((x,y,z),air)['Name'])
                natural = {'minecraft:air','minecraft:stone','minecraft:andesite','minecraft:tuff','minecraft:deepslate','minecraft:dirt','minecraft:grass_block','minecraft:coarse_dirt','minecraft:rooted_dirt','minecraft:moss_block','minecraft:grass','minecraft:tall_grass','minecraft:fern','minecraft:large_fern','minecraft:dandelion','minecraft:poppy'}
                if old not in natural and not old.endswith(('_leaves','_log')):
                    raise ValueError('Road meets existing architecture: ' + old)
                after[x,y,z] = air

    # Replace the central decorative gaming furniture with functional machines.
    machines = [('roulette_table',(3,-54,-32),(8,3,4)),
                ('card_table',(3,-54,-23),(8,3,4)),
                ('gamble_table',(3,-54,-14),(8,3,4)),
                ('lucky_wheel',(10,-54,-7),(4,5,2)),
                ('slot_machine',(14,-54,-31),(1,3,1)),
                ('slot_machine',(14,-54,-27),(1,3,1)),
                ('slot_machine',(14,-54,-23),(1,3,1))]
    placed = []
    for name, src, dims in machines:
        p = shifted(src)
        for dx in range(dims[0]):
            for dz in range(dims[2]):
                floor = after.get((p[0]+dx,p[1]-1,p[2]+dz),air)
                if str(floor['Name']) == 'minecraft:air':
                    raise ValueError('Machine extends outside gaming floor: '+name)
        for dx in range(dims[0]):
            for dy in range(dims[1]):
                for dz in range(dims[2]):
                    q = (p[0]+dx,p[1]+dy,p[2]+dz)
                    after[q] = air
                    furniture.pop(q, None)
        after[p] = tag('tnc:'+name, facing='north', spinning='false')
        placed.append({'block':'tnc:'+name, 'local':p, 'clearance':dims})

    # Welcome landing retains the requested receptionist height after lowering the hall.
    # In the user's current island ORIGIN=(-1028,90,-260), its centre is (-930,190,28).
    for x in range(97,100):
        for z in range(287,290):
            after[x,99,z]=tag('minecraft:polished_andesite')
            for y in (100,101):after[x,y,z]=air
            for y in (99,100,101):furniture.pop((x,y,z),None)
    for x,y in ((94,96),(95,97),(96,98)):
        after[x,y,288]=tag('minecraft:stone_brick_stairs',facing='east',half='bottom',shape='straight',waterlogged='false')
        furniture.pop((x,y,288),None)
        for head in (y+1,y+2):after[x,head,288]=air;furniture.pop((x,head,288),None)

    # Retain only static decorations. Players, villagers and animals stay in the source.
    decor = []
    for path in (SOURCE / 'entities').glob('*.mca'):
        if path.stat().st_size == 0:
            continue
        for record in r.region_chunks(path.read_bytes()).values():
            for raw in r.read_chunk(record).get('Entities', []):
                if str(raw['id']) not in {'minecraft:painting','minecraft:item_frame','minecraft:glow_item_frame','minecraft:armor_stand'}:
                    continue
                p = [float(v) for v in raw['Pos']]
                if not (-16<=p[0]<=28 and -60<=p[1]<=-25 and -52<=p[2]<=12):
                    continue
                t = copy.deepcopy(raw)
                for key in ('UUID','Passengers','Leash','Owner'):
                    t.pop(key,None)
                t['Pos'] = n.List[n.Double]([p[i]+SHIFT[i] for i in range(3)])
                for i,key in enumerate(('TileX','TileY','TileZ')):
                    if key in t:t[key]=n.Int(int(t[key])+SHIFT[i])
                decor.append(t)

    palette, ids = [], {}
    def pid(t):
        k = r.state_key(t)
        if k not in ids:
            ids[k] = len(palette); palette.append(copy.deepcopy(t))
        return ids[k]
    previous=n.load(ROOT/'src/main/resources/data/tnc/casino/island_v2.nbt')
    legacy=n.load(ROOT/'src/main/resources/data/tnc/casino/island_v1.nbt')
    old1={tuple(map(int,c['Pos'])):legacy['Palette'][int(c['After'])] for c in legacy['Changes']}
    old={tuple(map(int,c['Pos'])):previous['Palette'][int(c['After'])] for c in previous['Changes']}
    changes = []
    for p in sorted(set(after)|set(old)|set(old1), key=lambda p:(p[1],p[2],p[0])):
        b, a = before.get(p,air), after.get(p,before.get(p,air))
        prev=old.get(p,b)
        if r.state_key(a) != r.state_key(b) or r.state_key(prev)!=r.state_key(a) or r.state_key(old1.get(p,b))!=r.state_key(a) or p in furniture:
            changes.append(n.Compound({'Pos':n.IntArray(p),'Before':n.Int(pid(b)),'Previous':n.Int(pid(prev)), 'PreviousV1':n.Int(pid(old1.get(p,b))),'After':n.Int(pid(a))}))
    # Preserve moved furniture and outstanding machine winnings when upgrading v1 worlds.
    moves={tuple(map(int,b['Pos'])):tuple(map(int,b['Pos'])) for b in previous['BlockEntities']}
    moves={p:(p[0],p[1]-4,p[2]) for p in moves}
    old_report=json.loads((WORK/'import-report-v2.json').read_text(encoding='utf8'))
    for old_machine,new_machine in zip(old_report['machines'],placed):
        moves[tuple(old_machine['local'])]=tuple(new_machine['local'])
    moves={p:q for p,q in moves.items() if q in furniture or any(tuple(m['local'])==q for m in placed)}
    moves1={tuple(map(int,b['Pos'])):(int(b['Pos'][0]),int(b['Pos'][1])-11,int(b['Pos'][2])) for b in legacy['BlockEntities']}
    legacy_report=json.loads((WORK/'import-report.json').read_text(encoding='utf8'))
    for a,b in zip(legacy_report['machines'],placed):moves1[tuple(a['local'])]=tuple(b['local'])
    moves1={p:q for p,q in moves1.items() if q in furniture or any(tuple(m['local'])==q for m in placed)}
    # Find comfortable, supported positions in basement and entrance-level halls.
    walk=[]
    occupied=set()
    for m in placed:
        x,y,z=m['local'];w,h,d=m['clearance']
        occupied.update((x+dx,y,z+dz) for dx in range(w) for dz in range(d))
    for y in (90,96):
        for x in range(72,104,3):
            if y==90 and x not in (81,84,93):continue
            for z in range(242,291,4):
                if (x,y,z) in occupied:continue
                if all(str(after.get((x,y+dy,z),air)['Name'])=='minecraft:air' for dy in (0,1)) and str(after.get((x,y-1,z),air)['Name']) in {'minecraft:stone','minecraft:red_terracotta','minecraft:green_terracotta','supplementaries:checker_block','minecraft:polished_diorite','minecraft:andesite'}:
                    walk.append((x,y,z))
    if len(walk)<28:raise ValueError('Not enough safe walking space')
    root = n.File({'Revision':n.Int(3),'Bounds':n.IntArray((*MIN,*MAX)),
                   'Palette':n.List[n.Compound](palette),'Changes':n.List[n.Compound](changes),
                   'BlockEntities':n.List[n.Compound]([n.Compound({'Pos':n.IntArray(p),'Data':t}) for p,t in furniture.items()]),
                   'Moves':n.List[n.Compound]([n.Compound({'From':n.IntArray(p),'To':n.IntArray(q)}) for p,q in moves.items()]),
                   'MovesV1':n.List[n.Compound]([n.Compound({'From':n.IntArray(p),'To':n.IntArray(q)}) for p,q in moves1.items()]),
                   'Walk':n.List[n.IntArray]([n.IntArray(p) for p in walk]),
                   'Decor':n.List[n.Compound](decor)})
    out = ROOT/'src/main/resources/data/tnc/casino/island_v3.nbt'
    out.parent.mkdir(parents=True,exist_ok=True)
    buf=io.BytesIO();root.write(buf);out.write_bytes(gzip.compress(buf.getvalue(),mtime=0))
    report={'source':'duchang.zip','source_sha256':hashlib.sha256((ROOT/'duchang.zip').read_bytes()).hexdigest(),
            'source_bounds':[-16,-60,-52,27,-26,11],'island_bounds':[*MIN,*MAX],
            'building_bounds':[66,84,234,109,118,297],'shift':SHIFT,'changes':len(changes),
            'block_entities':len(furniture),'decor':len(decor),'machines':placed,'source_blocks':dict(source_counts)}
    WORK.mkdir(parents=True,exist_ok=True)
    (WORK/'import-report-v3.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    # Export a plan view of the gaming floor for review.
    im=Image.new('RGB',(48,68),(40,35,30))
    for (x,y,z),t in after.items():
        if not (64<=x<=111 and 232<=z<=299 and y==89):continue
        name=str(t['Name']);color=(176,158,124)
        if name=='minecraft:air':color=(74,58,45)
        elif 'green' in name:color=(28,93,67)
        elif 'red' in name:color=(121,48,58)
        elif 'wood' in name:color=(78,53,39)
        im.putpixel((x-64,z-232),color)
    im=im.resize((480,680),Image.Resampling.NEAREST);draw=ImageDraw.Draw(im)
    for i,m in enumerate(placed,1):
        x,y,z=m['local'];w,h,d=m['clearance'];box=((x-64)*10,(z-232)*10,(x-64+w)*10,(z-232+d)*10)
        draw.rectangle(box,fill=(26,88,66),outline=(207,178,108),width=2);draw.text((box[0]+2,box[1]+2),str(i),fill='white')
    im.save(WORK/'gaming-floor.png')
    print(json.dumps({k:v for k,v in report.items() if k!='source_blocks'},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
