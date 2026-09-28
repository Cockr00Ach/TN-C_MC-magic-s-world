"""Dark ritual amphitheatre: robed winged guardians, chains and split core.
Template centre is 17; Java offsets it to retain all existing landing coordinates.
"""
import math

PORTAL_SIZE = (35, 28, 35)
C = 17

def portal_blocks():
    blocks = {}
    def put(x,y,z,state):
        x,z = round(x)+C,round(z)+C
        if not (0<=x<35 and 0<=y<28 and 0<=z<35):
            raise ValueError((x,y,z,state))
        blocks[x,y,z]='minecraft:'+state

    tips=[(8.8*math.cos(-math.pi/2+i*math.tau/5),8.8*math.sin(-math.pi/2+i*math.tau/5)) for i in range(5)]
    star=set()
    for i,a in enumerate(tips):
        b=tips[(i+2)%5]
        for step in range(121):
            t=step/120; star.add((round(a[0]+(b[0]-a[0])*t),round(a[1]+(b[1]-a[1])*t)))
    # Concentric half-steps, with broad approaches on all four axes.
    for x in range(-17,18):
        for z in range(-17,18):
            d=math.hypot(x,z)
            if d>16.6: continue
            put(x,0,z,'polished_blackstone_bricks')
            if d>15.2:
                put(x,0,z,'polished_blackstone_brick_slab[type=bottom,waterlogged=false]')
            elif d>13.8:
                put(x,1,z,'polished_blackstone_slab[type=bottom,waterlogged=false]')
            else:
                rune=9.8<=d<10.6 or (x,z) in star
                if rune: put(x,0,z,'shroomlight')
                put(x,1,z,'red_stained_glass' if rune else
                    'chiseled_polished_blackstone' if 12.7<d<13.5 else 'polished_blackstone')
    put(0,1,0,'crying_obsidian')

    # Inward-facing hooded figures: tapered cloak, broad shoulders and recessed face.
    for dx,dz in ((-1,-1),(-1,1),(1,-1),(1,1)):
        cx,cz=dx*9,dz*9
        outward=(dx/math.sqrt(2),dz/math.sqrt(2))
        tangent=(dz/math.sqrt(2),-dx/math.sqrt(2))
        def local(u,y,v,state):
            put(cx+tangent[0]*u+outward[0]*v,y,cz+tangent[1]*u+outward[1]*v,state)
        for x in range(cx-6,cx+7):
            for z in range(cz-6,cz+7):
                u=(x-cx)*tangent[0]+(z-cz)*tangent[1]
                v=(x-cx)*outward[0]+(z-cz)*outward[1]
                if math.hypot(u,v)<=3.1:
                    put(x,1,z,'polished_blackstone_bricks')
                    put(x,2,z,'chiseled_polished_blackstone')
                for y in range(3,13):
                    width=2.8-(y-3)*.13
                    if abs(u)<=width and abs(v-.25)<1.8:
                        put(x,y,z,'polished_basalt[axis=y]' if round(u*1.4)%2==0 and v<-.3 else 'polished_blackstone')
                for y in range(12,18):
                    width=2.5 if y<16 else 2.0 if y==16 else 1.25
                    if abs(u)<=width and -1.75<=v<=1.75:
                        if y<16 and abs(u)<1.25 and v<-.35:
                            if v>-.9: put(x,y,z,'black_concrete')
                        else: put(x,y,z,'polished_blackstone')
        # Curved wing silhouette and long separated trailing feathers.
        for sign in (-1,1):
            for s in range(2,8):
                top=round(13+1.3*s-.085*s*s)
                bottom=round(10-.55*s) if s<6 else 10+s-6
                for y in range(bottom,top+1):
                    v=1.3+.22*s+.12*max(0,top-y)
                    local(sign*s,y,v,'blackstone' if s%2 else 'polished_blackstone')
                    if y>=top-2: local(sign*s,y,v-.6,'polished_blackstone')
                local(sign*s,top+1,1.3+.22*s,'polished_blackstone_slab[type=bottom,waterlogged=false]')
            for s in range(4):
                local(sign*(2-.35*s),10-s//2,-1.5-s*.7,'polished_blackstone')
        # Physical crystal centres shared with the client beam geometry.
        ex,ez=dx*6,dz*6
        put(ex,8,ez,'chiseled_polished_blackstone')
        put(ex,9,ez,'shroomlight')
        put(ex,10,ez,'red_stained_glass')
        put(ex,11,ez,'red_stained_glass_pane[north=true,south=true,east=true,west=true,waterlogged=false]')

    for v in range(-9,10):
        height=11-round(3.0*(1-(v/9)**2))
        for edge in (-9,9):
            for x,z,axis in ((v,edge,'x'),(edge,v,'z')):
                if (x+C,height,z+C) not in blocks:
                    put(x,height,z,f'chain[axis={axis},waterlogged=false]')

    # Faceted reliquary: red seam between obsidian shells, open air under it.
    for y in range(18,28):
        r=min(3.0, .7+(y-18)*.85, .7+(27-y)*.85)
        for x in range(-3,4):
            for z in range(-3,4):
                if abs(x)+abs(z)*.8>r: continue
                put(x,y,z,'red_stained_glass' if x==0 else 'obsidian')
    put(0,22,0,'shroomlight')
    for dx,dz in ((-1,-1),(-1,1),(1,-1),(1,1)):
        for x,z in ((dx*5,dz*14),(dx*14,dz*5)):
            for y in (1,2): put(x,y,z,'chiseled_polished_blackstone')
            for y in (3,4): put(x,y,z,'polished_blackstone_brick_wall')
            put(x,5,z,'soul_lantern[hanging=false,waterlogged=false]')
    return [(x,y,z,s,None) for (x,y,z),s in sorted(blocks.items())]

if __name__=='__main__':
    from pathlib import Path
    from export_town_structures import write_piece
    repo=Path(__file__).resolve().parents[1]
    base=repo/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/structures/sky_island/portal'
    for name in ('ground_portal','island_portal'):
        count,_=write_piece(base/(name+'.nbt'),PORTAL_SIZE,portal_blocks(),3465)
        print(name,count,PORTAL_SIZE)
    write_piece(repo/'src/main/resources/data/tnc/structures/sky_island/portal/ritual_gate.nbt',PORTAL_SIZE,portal_blocks(),3465)
