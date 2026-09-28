"""Read-only collision audit of the expanded portal against a saved Anvil world."""
import sys
from pathlib import Path
from functools import lru_cache
from collections import Counter
import anvil
import nbtlib
from export_town_structures import section_palette,palette_index
from portal_design import portal_blocks,C

world=Path(sys.argv[1])
tag=nbtlib.load(world/'data/tnc_sky_island_v5.dat')['data']
@lru_cache(maxsize=32)
def region(rx,rz): return anvil.Region.from_file(str(world/'region'/f'r.{rx}.{rz}.mca'))
@lru_cache(maxsize=128)
def sections(cx,cz):
    chunk=region(cx//32,cz//32).chunk_data(cx%32,cz%32)
    return {int(s['Y'].value):section_palette(s) for s in chunk['sections'] if 'block_states' in s}
def block(x,y,z):
    sec=sections(x//16,z//16).get(y//16)
    if sec is None: return 'minecraft:air'
    palette,values=sec
    return palette[palette_index((y%16)*256+(z%16)*16+x%16,len(palette),values)]
for name,center in [('island',(int(tag['ArrivalX']),int(tag['ArrivalY'])-1,int(tag['ArrivalZ']))),
                    ('ground',(int(tag['GroundPortalX'])+7,int(tag['GroundPortalY']),int(tag['GroundPortalZ'])+7))]:
    conflicts=[]
    for x,y,z,state,_ in portal_blocks():
        dx,dz=x-C,z-C
        if -7<=dx<=7 and -7<=dz<=7: continue # Existing altar legacy footprint is separately validated in Java.
        current=block(center[0]+dx,center[1]+y,center[2]+dz)
        base=current.split('[')[0]
        if base in ('minecraft:air','minecraft:cave_air','minecraft:grass','minecraft:tall_grass','minecraft:dandelion','minecraft:azure_bluet','minecraft:snow'): continue
        if y<=1 and base in ('minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:dirt_path','minecraft:coarse_dirt','minecraft:gravel','minecraft:sand'): continue
        conflicts.append((dx,y,dz,current))
    print(name,center,'conflicts',len(conflicts),Counter(c[3] for c in conflicts))
    print(conflicts[:25])
