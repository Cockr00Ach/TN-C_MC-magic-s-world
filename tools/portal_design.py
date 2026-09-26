"""Tall winged ritual gate. Ground bounds/landing remain compatible with existing saves."""
import math

PORTAL_SIZE=(15,22,15)

def portal_blocks():
    blocks={}
    # Five-point star inside the luminous ring, matching the reference's ritual seal.
    tips=[(7+4*math.cos(-math.pi/2+i*math.tau/5),7+4*math.sin(-math.pi/2+i*math.tau/5)) for i in range(5)]
    star=set()
    for i in range(5):
        a,b=tips[i],tips[(i+2)%5]
        for step in range(33):
            t=step/32
            star.add((round(a[0]+(b[0]-a[0])*t),round(a[1]+(b[1]-a[1])*t)))
    def put(x,y,z,state):
        assert 0<=x<15 and 0<=y<22 and 0<=z<15
        blocks[x,y,z]='minecraft:'+state
    for x in range(15):
        for z in range(15):
            d=math.hypot(x-7,z-7)
            if d>6.7: continue
            put(x,0,z,'polished_blackstone_bricks')
            if d<=5.7:
                rune=4.0<=d<=4.9 or (x,z) in star
                put(x,0,z,'shroomlight' if rune else 'polished_blackstone_bricks')
                put(x,1,z,'red_stained_glass' if rune else 'polished_blackstone')
            elif abs(x-7)<=1 or abs(z-7)<=1:
                put(x,1,z,'polished_blackstone_slab[type=bottom,waterlogged=false]')
    put(7,1,7,'crying_obsidian')
    # Thin robed bodies, hollow diagonal hoods and individually stepped feathers.
    # Avoid solid square towers: silhouette and negative space do most of the work.
    for cx,cz in ((3,3),(3,11),(11,3),(11,11)):
        dx=1 if cx>7 else -1
        dz=1 if cz>7 else -1
        for y in range(1,10):
            put(cx,y,cz,'chiseled_polished_blackstone' if y==1 else 'polished_blackstone')
            if y<5:
                put(cx+dx,y,cz,'polished_blackstone_brick_wall')
                put(cx,y,cz+dz,'polished_blackstone_brick_wall')
            elif y<8:
                put(cx+dx,y,cz+dz,'polished_blackstone_brick_wall')
        for y in range(10,13):
            put(cx+dx,y,cz,'polished_blackstone')
            put(cx,y,cz+dz,'polished_blackstone')
            put(cx+dx,y,cz+dz,'blackstone')
        put(cx,11,cz,'black_stained_glass')  # Recessed face, not a solid red cube.
        for x,z in ((cx,cz),(cx+dx,cz),(cx,cz+dz)):
            put(x,13,z,'polished_blackstone_slab[type=bottom,waterlogged=false]')
        # Swept wings: disconnected feather tips, raised shoulder and open lower arches.
        for spread in range(1,4):
            for y in range(5+spread*2,12+spread):
                for x,z in ((cx+dx*spread,cz-dz),(cx-dx,cz+dz*spread)):
                    put(x,y,z,'polished_blackstone_brick_wall' if y==5+spread*2 else 'blackstone')
            for x,z in ((cx+dx*spread,cz-dz),(cx-dx,cz+dz*spread)):
                put(x,12+spread,z,'polished_blackstone_slab[type=bottom,waterlogged=false]')
        # Raised arms cradle the emitter. Renderer starts exactly at this red crystal.
        put(cx-dx,8,cz,'polished_blackstone_brick_slab[type=top,waterlogged=false]')
        put(cx,8,cz-dz,'polished_blackstone_brick_slab[type=top,waterlogged=false]')
        put(cx-dx,9,cz-dz,'shroomlight')
        put(cx-dx,10,cz-dz,'red_stained_glass')
    # Perimeter chain catenaries leave the central view and all four routes open.
    for v in range(3,12):
        y=11-round(3*(1-abs(v-7)/4))
        for edge in (3,11):
            if (v,y,edge) not in blocks: put(v,y,edge,'chain[axis=x,waterlogged=false]')
            if (edge,y,v) not in blocks: put(edge,y,v,'chain[axis=z,waterlogged=false]')
    # A split obsidian reliquary with a luminous seam, well above the statues' arms.
    for y in range(13,22):
        radius=min(y-13,21-y,2)
        for x in range(7-radius,8+radius):
            for z in range(7-radius,8+radius):
                if abs(x-7)+abs(z-7)>radius: continue
                put(x,y,z,'red_stained_glass' if x==7 else 'obsidian')
    put(7,16,7,'shroomlight')
    # Four narrow pylons echo the reference's outer finials without closing the routes.
    for x,z in ((1,5),(5,13),(13,9),(9,1)):
        put(x,1,z,'chiseled_polished_blackstone')
        for y in range(2,4): put(x,y,z,'polished_blackstone_brick_wall')
        put(x,4,z,'soul_lantern[hanging=false,waterlogged=false]')
    return [(x,y,z,s,None) for (x,y,z),s in sorted(blocks.items())]

if __name__=='__main__':
    from pathlib import Path
    from export_town_structures import write_piece
    repo=Path(__file__).resolve().parents[1]
    base=repo/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/structures/sky_island/portal'
    for name in ('ground_portal','island_portal'):
        count,_=write_piece(base/(name+'.nbt'),PORTAL_SIZE,portal_blocks(),3465)
        print(name,count,PORTAL_SIZE)
    write_piece(repo/'src/main/resources/data/tnc/structures/sky_island/portal/ritual_gate.nbt',
                PORTAL_SIZE,portal_blocks(),3465)
