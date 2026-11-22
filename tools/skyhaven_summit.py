"""Fit the first-sight scene to the surveyed RTF ridge above the north town road."""
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import distance_transform_edt


# Island-local coordinates, shared with the NPC, quest and client camera rig.
_SITES = Path(__file__).resolve().parents[1] / "src/main/resources/data/tnc/sky_academy/sites.json"
MX, FEET, MZ = json.loads(_SITES.read_text("utf8"))["stargaze"]
LIP = MZ + 6
TOP = FEET - 1

# Start on the eastern saddle. Follow the hillside to its southern shoulder,
# then turn north under the crest before approaching the lookout from behind.
TRAIL = [(323,76),(331,62),(331,53),(324,45),(312,42),(301,48),
         (296,56),(289,69),(280,78),(274,80),(270,76),(272,66),
         (278,55),(278,47),(272,42),(266,45),(263,52),(261,59),(MX,MZ)]


def _ease(r):
    t = np.clip(r, 0, 1)
    return t*t*(3-2*t)


def _window(x, z):
    return np.s_[MZ-88-int(z.min()):MZ+77-int(z.min()),
                 MX-90-int(x.min()):MX+106-int(x.min())]


def _trail(x, z):
    segments = np.diff(TRAIL, axis=0)
    lengths = np.hypot(segments[:,0], segments[:,1])
    cumulative = np.r_[0, np.cumsum(lengths)]
    distance = np.full(x.shape, np.inf)
    height = np.zeros(x.shape, float)
    for i, (dx, dz) in enumerate(segments):
        ax, az = TRAIL[i]
        t = np.clip(((x-ax)*dx+(z-az)*dz) / lengths[i]**2, 0, 1)
        d = np.hypot(x-ax-dx*t, z-az-dz*t)
        # Reach the finished rock bench before entering its northern lip.
        fraction = np.minimum(1., (cumulative[i]+t*lengths[i])/(cumulative[-1]-12))
        local_grade = 142 + (TOP-142)*fraction
        np.copyto(height, local_grade, where=d<distance)
        np.minimum(distance, d, out=distance)
    return distance, height


def _shelf(x, z):
    # The eastern side follows a natural bedding fracture. The southern end is
    # exactly the block under the player's feet when the conversation releases.
    return (((x-MX + .20*(z-MZ))/6.2)**2 + ((z-MZ-1.2)/8.4)**2 <= 1) & (z<=LIP)


def grade(x, z, h, raw, protect, land):
    w = _window(x, z)
    xx, zz = x[w], z[w]
    elevation = h[w].copy()
    # The highest sampled local spur lies a few blocks south of the mentor.
    # Preserve its dissected RTF side valleys while lifting the viewing rock
    # to the historic camera datum. The margins meet the unmodified catchment.
    radius = np.hypot((xx-MX+.23*(zz-MZ))/69, (zz-MZ)/68)
    crest_weight = 1-_ease((radius-.12)/.88)
    native_peak = raw[MZ+6-int(z.min()), MX-int(x.min())]
    ridge = TOP + (raw[w]-native_peak)*.88
    elevation += (ridge-elevation)*crest_weight
    route, path_y = _trail(xx, zz)
    shoulder = 1-_ease((route-1.8)/12)
    elevation += (path_y-elevation)*shoulder
    # Worn stone treads change by half blocks along the central footpath.
    tread = np.rint(path_y*2)*.5
    walk = route<=1.8
    shelf = _shelf(xx,zz)
    distance = distance_transform_edt(~shelf)
    elevation += (TOP-elevation)*(1-_ease(distance/8))*(zz<=LIP)
    elevation[walk] = np.floor(tread[walk])
    elevation[shelf] = TOP
    # Descending bedrock below the ledge keeps both the town and the sky in
    # frame. This fan opens outwards; its margins fade into the original slope.
    forward = np.maximum(zz-LIP, 0)
    breadth = 6+.45*forward
    fan = (1-_ease((np.abs(xx-MX)-breadth)/12))*(1-_ease((forward-30)/32))
    ceiling = TOP-2.7*np.sqrt(forward)-.34*forward
    elevation -= fan*np.maximum(0,elevation-ceiling)*(forward>0)
    editable = ~protect[w] & land[w]
    np.copyto(h[w],elevation,where=editable)
    clear = np.zeros_like(land)
    clear[w] = editable & ((route<=3.15)|(distance<=5)|((forward>0)&(fan>.8)))
    return h, clear


def finish(v, x, z, h):
    w = _window(x,z)
    xx, zz = x[w], z[w]
    ix, iz = xx-int(x.min()), zz-int(z.min())
    surface = h[w]
    route, path_y = _trail(xx,zz)
    shelf = _shelf(xx,zz)
    # Rock slabs blend into the exposed strata. Within the lookout's shallow
    # approach the final heightfield, not the nominal trail grade, is authoritative.
    walk = (route<=1.8) & ~shelf
    slab = v.id("minecraft:andesite_slab[type=bottom,waterlogged=false]")
    path_stone = v.id("minecraft:andesite")
    grit = v.id("minecraft:tuff")
    rough = np.sin(xx*.43+zz*.26)+.6*np.cos(zz*.53-xx*.17)>.92
    v.blocks[surface[walk],iz[walk],ix[walk]] = np.where(rough[walk],grit,path_stone)
    half = walk & (np.rint(path_y*2) == 2*surface+1)
    v.blocks[surface[half]+1,iz[half],ix[half]] = slab
    # An old star chart cut into the rock. Its level deck allows the mentor
    # and player to stand at the exact server positions in third and first person.
    v.blocks[TOP,iz[shelf],ix[shelf]] = path_stone
    radius = np.hypot(xx-MX,zz-MZ)
    rim = shelf & (np.abs(radius-3.65)<.35)
    v.blocks[TOP,iz[rim],ix[rim]] = v.id("minecraft:polished_andesite")
    for dx, dz in [(0,0),(-4,0),(4,0),(0,-4),(0,4)]:
        v.set(MX+dx,TOP,MZ+dz,"minecraft:smooth_basalt" if dx or dz else "minecraft:chiseled_deepslate")
    validate(v,x,z,h)


def validate(v,x,z,h):
    """Fail a bake if either cast member, a shot, or the walking grade is obstructed."""
    from collections import deque

    x0,z0 = int(x.min()),int(z.min())
    for px,pz in ((MX,MZ),(MX,LIP)):
        if h[pz-z0,px-x0]!=TOP or not v.blocks[TOP,pz-z0,px-x0] or np.any(v.blocks[FEET:FEET+3,pz-z0,px-x0]):
            raise ValueError(f"Obstructed first-sight position at {(px,pz)}")
    for lift, pitch in ((2.32,-22/85),(2.82,32/85)):
        for t in np.arange(0,83,.5):
            pz = int(np.floor(LIP+.5+2.5+t))
            py = int(np.floor(FEET+lift+pitch*t))
            if v.blocks[py,pz-z0,MX-x0]:
                raise ValueError(f"First-sight camera ray intersects terrain at {(MX,py,pz)}")
    if h[LIP+1-z0,MX-x0]>=TOP-1:
        raise ValueError("Viewpoint no longer stands at the edge")

    w = _window(x,z)
    xx,zz = x[w],z[w]
    dist,_ = _trail(xx,zz)
    path = (dist<=1.8)|_shelf(xx,zz)
    xx,zz = xx[path],zz[path]
    iy = h[w][path]
    ix,iz = xx-x0,zz-z0
    slabs = v.blocks[iy+1,iz,ix] == v.id("minecraft:andesite_slab[type=bottom,waterlogged=false]")
    clear = ((v.blocks[iy+1,iz,ix]==0)|slabs) & (v.blocks[iy+2,iz,ix]==0) & (v.blocks[iy+3,iz,ix]==0)
    heights = dict(zip(zip(xx[clear],zz[clear]),2*iy[clear]+slabs[clear]))
    visited = {TRAIL[0]}
    steps = deque(visited)
    while steps:
        px,pz = steps.popleft()
        for dest in ((px+1,pz),(px-1,pz),(px,pz-1),(px,pz+1)):
            if dest not in visited and dest in heights and abs(heights[dest]-heights[px,pz])<=1:
                visited.add(dest)
                steps.append(dest)
    if (MX,LIP) not in visited or any(p not in visited for p in TRAIL):
        raise ValueError("Disconnected no-jump route to the stargazer")
    print(f"Observatory: {len(visited)} walkable half-step trail tiles, mentor and ledge supported, both cinematic sightlines clear.",flush=True)
