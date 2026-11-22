"""Grade the town meadow and cut a spring, hanging cascade and dry grotto.

The authoring coordinates here are world X/Y/Z so the documented viewpoints,
cave entrance and NPC location can be checked in the game directly.
"""
import numpy as np
from scipy import ndimage as nd

MASTER = (-174, 207, -143)
POOL_LEVEL = 201

def ease(t):
    t = np.clip(t, 0., 1.)
    return t*t*(3-2*t)


def meadow_grade(x, z, ground, protected, land):
    # Rounded shoulders carry the grade back to the town lanes and the coastal
    # escarpment. The centre has a true level pad with room for future builds.
    radius = (np.abs((x-64)/57)**3.4 + np.abs((z-88)/55)**3.4)**(1/3.4)
    coast = nd.distance_transform_edt(land)
    distance = nd.distance_transform_edt(~protected)
    interior = (radius < .91) & (distance >= 4) & (coast >= 8) & land
    amount = (1-ease((radius-.9)/.39))*ease(distance/8)*ease(coast/13)
    pasture = 176 + ease((radius-.76)/.42)*(2.2*np.sin(.056*x)+1.1*np.cos(.063*z))
    return ground + (pasture-ground)*amount, interior


def pond(x,z):
    # The crescent-shaped plunge pool opens to the west; preserve the right bank
    # where the path reaches the ledge behind the lower fall.
    return np.hypot((x+175-2.5*np.sin((z+111)*.09))/28, (z+108+.12*(x+174))/17)


def bank_approach(x, z):
    # World-space support heights; each turn is reachable from its neighbour
    # without a jump higher than one block.
    route = [(-170,196,-75),(-178,196,-85),(-177,198,-94),(-169,200,-99),
             (-158,201,-100),(-150,203,-104),(-146,204,-111),(-148,204,-116),
             (-153,204,-120),(-156,204,-123)]
    return path_grade(x,z,route)


def path_grade(x,z,route):
    nearest = np.full(x.shape,np.inf)
    height = np.zeros(x.shape)
    for (ax,ay,az),(bx,by,bz) in zip(route,route[1:]):
        dx,dz = bx-ax,bz-az
        t=np.clip(((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz),0,1)
        distance=np.hypot(x-ax-t*dx,z-az-t*dz)
        nearer=distance<nearest
        height[nearer]=ay+(by-ay)*t[nearer]
        nearest=np.minimum(nearest,distance)
    return nearest,height


def grade(x,z,h,protect,land):
    wx,wz=x-260,z-260
    ground,meadow=meadow_grade(wx,wz,h+90,protect,land)
    head, margin, station = cascade(wx, wz)
    # A linked catchment: the raised upper bowl feeds three steps, each with
    # a different lip, setback and width. The low shelf widens into the plunge pool.
    basin = pond(wx,wz)
    pool_weight = 1-ease((basin-.85)/.68)
    basin_floor = POOL_LEVEL-4 + 9*ease((basin-.50)/.81)
    ground += (basin_floor-ground)*pool_weight
    banks = np.maximum(0., margin)
    side = 1-ease((banks-4)/20)
    long_mask = ease((station+195)/8)*(1-ease((station+111)/4))
    bank_cut = side*long_mask
    ripples = .7*np.sin(wx*.65+wz*.28)+.48*np.sin(wz*.63-wx*.17)
    # Keep wide flood benches at the outside of bends, with a higher dry rim
    # and a second scarp a few blocks behind the wet stone.
    shelf = 3*ease(banks/3.8) + 4.8*ease((banks-6-ripples)/3.5) + 9*ease((banks-13)/8)
    bed = head-2 + shelf + ripples*ease(banks/5)
    ground += (bed-ground)*bank_cut
    foot,foot_y=bank_approach(wx,wz)
    ground += (foot_y-ground)*(1-ease((foot-1.4)/4.0))*ease(nd.distance_transform_edt(~protect)/3)
    falls = (bank_cut>.05)|(pool_weight>.04)|(foot<3)
    water = np.full(x.shape,-1,np.int16)
    pool=(basin<1.13)&(ground<POOL_LEVEL)&(foot>2.8)
    water[pool]=POOL_LEVEL-90
    course=(margin<=0)&(station>=-188)&(station< -109)&(ground<head-.5)&(foot>2.8)
    water[course]=np.maximum(water[course],head[course]-90).astype(int)
    return ground-90,meadow,falls,water,foot


def cascade(x,z):
    # Station follows the winding edge of the bed. The two halves of a
    # fall consequently meet the receiving shelf at different depths.
    dx=x+167
    station=z+1.7*np.sin(dx*.24)+.17*dx+1.1*np.sin(x*.17+z*.085)
    fork=ease((station+165)/25)
    join=ease((station+124)/14)
    axis=-168+3.4*np.sin((z+157)*.095) - 6.5*(1-ease((z+182)/29))
    west=axis-fork*(25-10*join)
    east=axis+fork*(4.5-2*join)
    width=6.4+1.1*np.cos(z*.19)+2*ease((station+159)/15)+3.5*np.exp(-((z+161)/9)**2)
    main=np.abs(x-east)-width
    braid=np.abs(x-west)-(width*.71+join)
    margin=np.minimum(main,braid)
    # Each arm reaches a rock step at a different station. Upstream of the
    # split and in the collecting pool the two elevations meet again.
    branch=ease((east-x-3)/12)*fork
    t=station+branch*(9-3.5*join)
    head=np.interp(t,[-192,-180,-172,-166,-164,-161,-159,-151,-149,-138,-135,-132,-126,-123,-122,-116,-114,-111,-106],
                      [289,284,282,281,264,262,261,259,245,243,241,233,232,231,212,211,207,201,201])
    return np.rint(head).astype(int),margin,station


def riparian_shelves(v,gx,gz,margin,station,near_path,h,x,z):
    """Damp, fissured walls, soil pockets and willow-like birches on spray benches."""
    ix, iz = gx+260-int(x.min()), gz+260-int(z.min())
    ground = h[iz,ix].astype(int)
    water = v.id("minecraft:water[level=0]")
    falling = v.id("minecraft:water[level=8]")
    slate = v.id("conquest:wet_slate")
    green = v.id("conquest:green_schist")
    limestone = v.id("conquest:moss_covered_limestone")
    moss = v.id("minecraft:moss_block")
    grass = v.id("minecraft:grass_block[snowy=false]")
    dirt = v.id("minecraft:rooted_dirt")
    carpet = v.id("minecraft:moss_carpet")
    fern = v.id("minecraft:fern")
    leaves = v.id("minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]")
    black_leaves = v.id("minecraft:dark_oak_leaves[distance=1,persistent=true,waterlogged=false]")
    trunk = v.id("minecraft:birch_log[axis=y]")
    crest = (station>=-188)&(station< -109)
    pond_edge = pond(gx,gz)
    banks = ((crest&(margin>=0)&(margin<18))|((pond_edge>=.79)&(pond_edge<1.38)))&(near_path>2.8)
    slope_z,slope_x = np.gradient(nd.gaussian_filter(ground.astype(float),1))
    slope=np.hypot(slope_z,slope_x)
    veins = np.sin(.17*gx+.08*gz) + .62*np.sin(.23*gz-.13*gx)
    moisture = np.cos(.19*gx-.08*gz) + .55*np.sin(.33*gz+.1*gx)
    # Follow exposed faces vertically, so a large patch of silt or moss on a
    # ledge continues down the wet cliff instead of reading as a painted cap.
    soil = banks&(slope<1.65)&(moisture>-.74)&(ground+90<275)
    substrate = np.where(veins>.55,green,slate)
    for depth in range(11):
        sy = ground-depth
        block = v.blocks[sy,iz,ix]
        exposed = banks&(block!=0)&(block!=water)&(block!=falling)
        rock = np.where(moisture>.26+depth*.12,limestone,substrate)
        replacement = np.where(soil & (depth<2),dirt,rock)
        if depth==0:
            replacement=np.where(soil,np.where(moisture>.30,moss,grass),rock)
        v.blocks[sy[exposed],iz[exposed],ix[exposed]]=replacement[exposed]
    rng = np.random.default_rng(185398)
    # Place a few uneven groups of trees so the size of the falling water is
    # legible from below. Leave the sky above the crown and the bank trail open.
    clear = (v.blocks[ground+1,iz,ix]==0)
    green_sites = soil&clear
    plants = green_sites & (rng.random(gx.shape)<.23+.10*np.maximum(veins,0))
    for j,i in zip(*np.where(plants)):
        v.blocks[ground[j,i]+1,iz[j,i],ix[j,i]]=fern if moisture[j,i]>.30 else carpet
    candidates=np.argwhere(green_sites&(near_path>6)&(slope<.90)&(ground+90<256)&(moisture>.14))
    rng.shuffle(candidates)
    trees = []
    for j,i in candidates:
        px,pz=int(ix[j,i]),int(iz[j,i])
        if any((px-a)**2+(pz-b)**2<34 for a,b in trees):continue
        base=int(ground[j,i])+1
        height=int(rng.integers(4,8))
        if np.any(v.blocks[base+1:base+height+2,pz,px]):continue
        trees.append((px,pz))
        v.blocks[base:base+height,pz,px]=trunk
        # A crown with a large up-valley mass and a lower downwind branch.
        for dx,dy,dz,rx,ry,rz in [(0,0,0,2.8,1.9,2.3),(-1,-1,1,2.1,1.3,1.8),(2,-2,0,1.8,1.2,1.6)]:
            for by in range(-2,3):
                for bz in range(-3,4):
                    for bx in range(-3,4):
                        if (bx/rx)**2+(by/ry)**2+(bz/rz)**2>rng.uniform(.83,1.22):continue
                        key=(base+height-1+dy+by,pz+dz+bz,px+dx+bx)
                        if v.blocks[key]==0:v.blocks[key]=black_leaves if dy==-2 else leaves
        if len(trees)>=24:break
    # Short irregular strands attached to actual vertical rock. Skip the flow,
    # so they trail beside a sheet and never obstruct the players' dry ledge.
    for j,i in np.argwhere(green_sites & (near_path>5) & (rng.random(gx.shape)<.13)):
        px,pz=int(ix[j,i]),int(iz[j,i])
        top=int(ground[j,i])
        for dx,dz,face in [(0,1,"north"),(1,0,"west"),(-1,0,"east")]:
            if v.blocks[top,pz+dz,px+dx]!=0:continue
            vine=v.id("minecraft:vine[east="+str(face=="east").lower()+",north="+str(face=="north").lower()
                      +",south=false,up=false,west="+str(face=="west").lower()+"]")
            for by in range(top,top-int(rng.integers(3,9)),-1):
                if v.blocks[by,pz+dz,px+dx]!=0 or v.blocks[by,pz,px] in (0,water,falling):break
                v.blocks[by,pz+dz,px+dx]=vine
            break
    return int(soil.sum()),len(trees)


def finish(v,x,z,h):
    wx,wz=x-260,z-260
    def put(px,py,pz,state):
        v.set(px+260,py-90,pz+260,state)
    wet=v.id("minecraft:water[level=8]")
    source=v.id("minecraft:water[level=0]")
    ledge=v.id("conquest:wet_slate")
    limestone=v.id("conquest:moss_covered_limestone")
    # Fill the drop from the high side onto its downstream receiving bed.
    # The intervening shelves stay wet and the raised dividing spur stays dry.
    gx,gz=np.meshgrid(np.arange(-216,-130),np.arange(-202,-86))
    level,margin,station=cascade(gx,gz)
    near_path,_=bank_approach(gx,gz)
    in_bed=(margin<=0)&(station>=-188)&(station< -109)&(near_path>2.8)
    filled=0
    for j,i in zip(*np.where(in_bed)):
        px,pz,py=int(gx[j,i]),int(gz[j,i]),int(level[j,i])
        floor=int(h[pz+260-int(z.min()),px+260-int(x.min())])+90
        if floor>=py:continue
        put(px,floor,pz,ledge if np.sin(px*.31+pz*.57)>.15 else limestone)
        upstream=max((level[b,a] for b,a in ((j-1,i),(j,i-1),(j,i+1))
                      if in_bed[b,a]),default=py)
        for y in range(py+1,int(upstream)+1):
            put(px,y,pz,wet)
            filled+=1
    # Boulder toes at the waterline give each arm a different landing and
    # leave a wet fringe. All rocks avoid the eastern bank's walking route.
    for cx,cy,cz,rx,ry,rz in [(-179,201,-111,4.5,2.7,3.3),(-174,201,-107,2.5,1.5,2.2),
                              (-184,204,-118,4.,5.,3.),(-168,201,-114,2.0,1.6,2.3),
                              (-184,242,-138,3.4,2.0,2.8),(-169,233,-132,3.1,2.6,2.2)]:
        for px in range(int(cx-rx)-1,int(cx+rx)+2):
            for pz in range(int(cz-rz)-1,int(cz+rz)+2):
                for y in range(int(cy-ry),int(cy+ry)+1):
                    if ((px-cx+.17*(y-cy))/rx)**2+((y-cy)/ry)**2+((pz-cz)/rz)**2<1+.08*np.sin(px*.71+pz):
                        put(px,y,pz,limestone if y>=cy+ry*.48 else ledge)
    shelves, shrubs = riparian_shelves(v,gx,gz,margin,station,near_path,h,x,z)
    # A path skirts the bank, ducks behind the sheet, and bends out of sight.
    # Its ceiling follows a shallow arch; the inner chamber keeps a dry sill.
    route=[(-156,204,-123),(-158,204,-125),(-165,204,-126),(-168,205,-129),
           (-167,205,-135),(-171,206,-140),(-174,206,-143)]
    dist,trail=path_grade(wx,wz,route)
    chamber=np.hypot((wx+175)/8.4,(wz+144)/6.0)
    in_room=chamber<=1
    seats=in_room|(dist<=1.95)
    for iz,ix in zip(*np.where(seats)):
        px,pz=int(wx[iz,ix]),int(wz[iz,ix])
        floor=206 if in_room[iz,ix] else round(trail[iz,ix])
        ceiling=(3+int(2.9*np.sqrt(max(0,1-chamber[iz,ix]**2))) if in_room[iz,ix]
                 else 3+int(dist[iz,ix]<1.0))
        put(px,floor,pz,limestone if (px*3+pz)%8<2 else ledge)
        for y in range(floor+1,floor+ceiling+1):
            put(px,y,pz,0)
    # Low light in recesses draws the eye round each bend without revealing
    # the teacher from the shore. The actual path remains two blocks wide.
    for px,py,pz in [(-164,206,-127),(-166,207,-134),(-181,208,-144),(-172,208,-149)]:
        put(px,py,pz,"minecraft:glow_lichen[down=false,east=false,north=false,south=false,up=true,waterlogged=false,west=false]")
        put(px,py+1,pz,limestone)
    for px,pz in [(-180,-145),(-172,-148),(-178,-148)]:
        put(px,207,pz,"minecraft:small_amethyst_bud[facing=up,waterlogged=false]")
    for px,pz in [(-180,-144),(-172,-147)]:
        put(px,206,pz,"minecraft:ochre_froglight[axis=y]")
        put(px,207,pz,"minecraft:moss_carpet")
    highest=int(level[in_bed].max())
    return {"master_world":list(MASTER),"lip_world":[-175,281,-166],"bed_drop":highest-POOL_LEVEL,"falling_blocks":filled,
            "moss_shelves":shelves,"bankside_shrubs":shrubs,
            "pool_world":[-166,POOL_LEVEL,-113],"trailhead_world":[-170,197,-75]}
