"""Block-built dark ritual gate; original 15x15 footprint and landing stay fixed."""
import math

PORTAL_SIZE=(15,14,15)

def portal_blocks():
    blocks={}
    def put(x,y,z,state):
        assert 0<=x<15 and 0<=y<14 and 0<=z<15
        blocks[x,y,z]='minecraft:'+state
    for x in range(15):
        for z in range(15):
            d=math.hypot(x-7,z-7)
            if d>6.7: continue
            put(x,0,z,'polished_blackstone_bricks')
            if d<=5.7:
                rune=4.0<=d<=4.9 or (abs(x-7)==abs(z-7) and 1.5<d<4.0)
                put(x,0,z,'shroomlight' if rune else 'polished_blackstone_bricks')
                put(x,1,z,'red_stained_glass' if rune else 'polished_blackstone')
            elif abs(x-7)<=1 or abs(z-7)<=1:
                put(x,1,z,'polished_blackstone_slab[type=bottom,waterlogged=false]')
    put(7,1,7,'crying_obsidian')
    # Hooded sentinels and wing-like, rising buttresses in the four corners.
    for cx,cz in ((3,3),(3,11),(11,3),(11,11)):
        dx=1 if cx>7 else -1
        dz=1 if cz>7 else -1
        for y in range(1,6):
            put(cx,y,cz,'chiseled_polished_blackstone' if y==1 else 'polished_blackstone_bricks')
            put(cx+dx,y,cz,'blackstone')
            put(cx,y,cz+dz,'blackstone')
        for y in range(6,8):
            put(cx,y,cz,'coal_block')
            put(cx+dx,y,cz,'polished_blackstone')
            put(cx,y,cz+dz,'polished_blackstone')
        put(cx,6,cz,'redstone_block')
        put(cx,8,cz,'polished_blackstone_slab[type=bottom,waterlogged=false]')
        for wing in range(1,3):
            for y in range(3+wing,7+wing):
                put(cx+dx*wing,y,cz-dz,'blackstone')
                put(cx-dx,y,cz+dz*wing,'blackstone')
    # Sagging chains high above the open walking routes.
    for v in range(3,12):
        y=8-round(2*(1-abs(v-7)/4))
        for edge in (3,11):
            put(v,y,edge,'chain[axis=x,waterlogged=false]')
            put(edge,y,v,'chain[axis=z,waterlogged=false]')
    # Suspended crystal: six blocks of overhead clearance at the center.
    for y in range(7,14):
        radius=min(y-7,13-y,2)
        for x in range(7-radius,8+radius):
            for z in range(7-radius,8+radius):
                if abs(x-7)+abs(z-7)>radius: continue
                put(x,y,z,'red_stained_glass' if x==7 or z==7 else 'obsidian')
    put(7,10,7,'shroomlight')
    return [(x,y,z,s,None) for (x,y,z),s in sorted(blocks.items())]

if __name__=='__main__':
    from pathlib import Path
    from export_town_structures import write_piece
    repo=Path(__file__).resolve().parents[1]
    base=repo/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/structures/sky_island/portal'
    for name in ('ground_portal','island_portal'):
        count,_=write_piece(base/(name+'.nbt'),PORTAL_SIZE,portal_blocks(),3465)
        print(name,count,PORTAL_SIZE)
