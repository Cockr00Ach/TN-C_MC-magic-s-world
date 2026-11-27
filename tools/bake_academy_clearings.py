"""Author the three lesson clearings on the surveyed Skyhaven starter.

The same sparse before/after palette patch upgrades existing highlands saves.
Run after baking the RTF highlands. Never opens a player world.
"""
import argparse
import gzip
import io
import json
import math
import tempfile
import zipfile
from functools import lru_cache
from pathlib import Path

import nbtlib as n
import numpy as np
from finish_skyhaven_gateway import anvil

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/tnc"
STARTER = DATA / "skyhaven/starter.zip"
PATCH = DATA / "sky_academy/lesson_clearings.nbt"
MARK = "TncLessonClearingsV1"
ORIGIN = (-260, 90, -260)
FEET = {
    "wind": (62,219,-151), "rune0": (55,219,-158), "rune1": (62,219,-162),
    "rune2": (69,219,-158), "farm": (-169,154,122), "workshop": (-15,179,144),
}

@lru_cache(maxsize=None)
def state(name):
    raw, _, props = name.partition("[")
    if ":" not in raw:
        raw = "minecraft:" + raw
    block = n.Compound({"Name": n.String(raw)})
    if props:
        block["Properties"] = n.Compound({k:n.String(v) for k,v in (p.split("=") for p in props[:-1].split(","))})
    return block

AIR = state("air")
GRASS = "grass_block[snowy=false]"
FOLIAGE = {"grass","fern","tall_grass","large_fern","oxeye_daisy","azure_bluet","allium","dandelion","moss_carpet","cornflower","lily_of_the_valley","poppy","blue_orchid","pink_tulip","white_tulip","orange_tulip"}

class ClearingMap:
    def __init__(self, files):
        self.files = files
        self.regions, self.chunks, self.sections, self.changes = {}, {}, {}, {}
        self.original_surface = {}
        self.container_positions = set()

    def section(self, x,y,z):
        cx,sy,cz = x//16,y//16,z//16
        key = cx,sy,cz
        if key in self.sections:
            return self.sections[key]
        name = f"realm/region/r.{cx//32}.{cz//32}.mca"
        if name not in self.regions:
            self.regions[name] = anvil.region_chunks(self.files[name])
        if (cx,cz) not in self.chunks:
            chunk = anvil.read_chunk(self.regions[name][cx%32+32*(cz%32)])
            self.chunks[cx,cz] = chunk
            self.container_positions.update(tuple(int(be[k]) for k in "xyz") for be in chunk["block_entities"])
        chunk = self.chunks[cx,cz]
        sec = next((s for s in chunk["sections"] if int(s["Y"]) == sy),None)
        if sec is None:
            sec = n.Compound({"Y": n.Byte(sy), "biomes": n.Compound({"palette": n.List[n.String]([n.String("tnc:skyhaven")])})})
            chunk["sections"].append(sec)
        bs = sec.get("block_states",n.Compound({"palette": n.List[n.Compound]([AIR])}))
        palette = list(bs["palette"])
        self.sections[key] = (sec,palette,anvil.decode(bs),{anvil.state_key(v):i for i,v in enumerate(palette)})
        return self.sections[key]

    def read(self,x,y,z):
        _, pal, values, _ = self.section(x,y,z)
        return pal[int(values[(y%16)*256+(z%16)*16+x%16])]

    def put(self,x,y,z,block):
        pos = (int(x),int(y),int(z))
        new = state(block) if isinstance(block,str) else block
        before = self.read(*pos)
        if pos in self.container_positions:
            raise ValueError(f"Design overlaps a block entity at {pos}")
        if anvil.state_key(before)==anvil.state_key(new):
            if pos in self.changes: del self.changes[pos]
        else: self.changes[pos]=(before,new)

    def surface(self,x,z):
        if (x,z) not in self.original_surface:
            for y in range(245,140,-1):
                raw=str(self.read(x,y,z)["Name"]).split(":")[-1]
                if raw in FOLIAGE or raw in ("air","cave_air") or raw.endswith(("_leaves","_log")): continue
                self.original_surface[x,z]=y
                break
            else: raise ValueError(f"No terrain under {x,z}")
        return self.original_surface[x,z]

    def grade(self,x,z,height,cover):
        current=self.surface(x,z)
        if abs(current-height)>6: raise ValueError(f"Excessive cut or fill at {x,z}: {current} -> {height}")
        for y in range(min(current,height)+1,height):
            self.put(x,y,z,"dirt" if height-y<3 else "andesite")
        self.put(x,height,z,cover)
        for y in range(height+1,max(current+3,height+6)):
            old=str(self.read(x,y,z)["Name"]).split(":")[-1]
            if old in ("air","cave_air"): continue
            if any(s in old for s in ("planks","lantern","brick","glass","door","stairs","cobblestone_wall","chest")):
                raise ValueError(f"Reserved architecture at {x,y,z}: {old}")
            self.put(x,y,z,"air")

    def encode(self):
        chunks=set()
        palette, ids, rows=[], {}, []
        def pal(s):
            key=anvil.state_key(s)
            if key not in ids:
                ids[key]=len(palette); palette.append(s)
            return ids[key]
        for (x,y,z),(before,after) in sorted(self.changes.items()):
            cx,sy,cz=x//16,y//16,z//16
            chunks.add((cx,cz))
            sec,states,cells,keys=self.section(x,y,z); key=anvil.state_key(after)
            if key not in keys:
                keys[key]=len(states);states.append(after)
            cells[(y%16)*256+(z%16)*16+x%16]=keys[key]
            rows.extend([x-ORIGIN[0],y-ORIGIN[1],z-ORIGIN[2],pal(before),pal(after)])
        changed_sections={(x//16,y//16,z//16) for x,y,z in self.changes}
        for key in changed_sections:
            sec,states,cells,_=self.sections[key]
            sec["block_states"]=anvil.encode(states,cells)
        for cx,cz in chunks:
            c=self.chunks[cx,cz];c.pop("Heightmaps",None);c["isLightOn"]=n.Byte(0);c[MARK]=n.Byte(1)
            for s in c["sections"]:
                s.pop("BlockLight",None);s.pop("SkyLight",None)
            name=f"realm/region/r.{cx//32}.{cz//32}.mca"
            self.regions[name][cx%32+32*(cz%32)]=anvil.serialize(c)
        for name,records in self.regions.items():
            self.files[name]=anvil.pack_region(self.files[name],records)
        tag=n.File({"Version":n.Int(1),"Palette":n.List[n.Compound](palette),"Changes":n.IntArray(rows)})
        raw=io.BytesIO();tag.write(raw)
        return gzip.compress(raw.getvalue(),mtime=0)

def ellipse(m,cx,cz,rx,rz,level,cover):
    for z in range(cz-rz-2,cz+rz+3):
        for x in range(cx-rx-2,cx+rx+3):
            radius=math.hypot((x-cx)/rx,(z-cz)/rz)
            if radius>1.06: continue
            blend=max(0.,min(1.,(1.06-radius)/.22))
            height=round(level*blend + m.surface(x,z)*(1-blend))
            m.grade(x,z,height,cover(x-cx,z-cz,radius))

def trail(m,vertices):
    centres=[]
    for (ax,ay,az),(bx,by,bz) in zip(vertices,vertices[1:]):
        steps=max(abs(ax-bx),abs(az-bz))
        for i in range(steps+1):
            f=i/steps
            cell=round(ax+(bx-ax)*f),round(ay+(by-ay)*f),round(az+(bz-az)*f)
            if not centres or centres[-1]!=cell: centres.append(cell)
    # Let the nearest centre control the height across each cross-section.
    ribbon={}
    for i,(x,y,z) in enumerate(centres):
        for dx,dz in ((0,0),(0,-1),(0,1),(-1,0),(1,0)):
            key=x+dx,z+dz
            if key not in ribbon or dx*dx+dz*dz<ribbon[key][0]:
                ribbon[key]=(dx*dx+dz*dz,y,i)
    for (x,z),(edge,y,i) in ribbon.items():
        surface=m.surface(x,z)
        if abs(surface-y)>6: raise ValueError(f"Trail {x,z}: {surface}/{y}")
        if i in (0,len(centres)-1) and edge: continue
        cover=("andesite" if i%12 in (0,1) else "gravel") if not edge else ("coarse_dirt" if (x+z)%4 else GRASS)
        m.grade(x,z,y,cover)
    return centres

def describe_wind(m,highlands=True):
    def floor(dx,dz,r):
        if r>.88: return "moss_block" if dz<0 and (dx+3*dz)%6 else GRASS
        # Two thin, broken arcs echo the played note across a largely grassy floor.
        ring=math.hypot(dx*.76,dz+2+1.1*math.sin(dx/4))
        if abs(ring-6.6)<.48 and (dx<6 or dz>1): return "weathered_cut_copper"
        if abs(ring-8.7)<.6 and dz<4: return "smooth_basalt"
        if dz>0 and abs(dx-math.sin(dz/3))<1.6: return "gravel"
        return "moss_block" if math.sin(dx*.44+dz*.31)>.16 else GRASS
    ellipse(m,62,-157,13,9,218,floor)
    if highlands: trail(m,[(74,227,-177),(76,225,-172),(75,224,-167),(73,222,-164),(71,220,-161),(71,218,-157)])
    for rune in range(3):
        x,y,z=FEET["rune"+str(rune)]
        for dz in range(-2,3):
            for dx in range(-2,3):
                d=abs(dx)+abs(dz)
                if d>2: continue
                m.grade(x+dx,z+dz,y-1,"polished_deepslate" if d<=1 else "andesite")
        for dx in (-1,1): m.put(x+dx,y-1,z,"oxidized_cut_copper")
        for dy in range(3): m.put(x,y+dy,z,f"tnc:wind_chime_pillar[voice={rune},section={dy}]")
    # Low basalt fins on the northern bank, leaving the whole camera corridor clear.
    for x,z in ((51,-160),(53,-164),(56,-165),(68,-166),(73,-162)):
        height=m.surface(x,z)
        if (x,z) in ((53,-164),(68,-166)):
            m.put(x,height+1,z,"polished_basalt[axis=y]")
            m.put(x,height+2,z,"oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
        else: m.put(x,height+1,z,"polished_blackstone_slab[type=bottom,waterlogged=false]")
    for x,z in ((52,-154),(53,-152),(56,-151),(65,-151),(70,-153),(72,-154)):
        m.put(x,219,z,"azure_bluet")

def fence(m,line,y):
    posts=set(line)
    for x,z in posts:
        dirs=[("north",(x,z-1)),("south",(x,z+1)),("west",(x-1,z)),("east",(x+1,z))]
        props=",".join(k+"="+str(p in posts).lower() for k,p in dirs)
        m.put(x,y,z,"spruce_fence["+props+",waterlogged=false]")

def describe_farm(m,highlands=True):
    def floor(dx,dz,r):
        if abs(dx+1)<2 and dz>0: return "coarse_dirt"
        if dx<0 and dz<1 and r<.83: return "rooted_dirt" if dx%3 else "moss_block"
        return GRASS if dx>1 or dz>2 else "moss_block"
    ellipse(m,-169,116,12,10,153,floor)
    if highlands: trail(m,[(-139,181,64),(-142,180,69),(-150,175,74),(-154,170,79),
             (-159,166,85),(-166,160,91),(-171,155,98),(-171,153,107)])
    # A little runnel catches water from a low stone spout at the back of the bed.
    for z in range(109,120):
        m.put(-177,152,z,"clay");m.put(-177,153,z,"water[level=0]")
        for x in (-178,-176):m.grade(x,z,153,"moss_block" if z%4 else "smooth_stone")
    for z in (108,120):m.grade(-177,z,153,"stone_bricks")
    for x,z in ((-179,115),(-179,116),(-175,118),(-175,119),(-174,109),(-174,108)):
        m.put(x,154,z,"blue_orchid" if z%3 else "oxeye_daisy")
    edge=[(x,108) for x in range(-167,-160)]+[(-161,z) for z in range(109,118)]+[(x,117) for x in range(-165,-161)]
    for x,z in edge:m.grade(x,z,153,GRASS)
    fence(m,edge,154)
    for x,z in ((-167,108),(-161,108),(-161,117)):
        m.put(x,154,z,"stripped_spruce_log[axis=y]")
        m.put(x,155,z,"spruce_slab[type=bottom,waterlogged=false]")

def describe_workshop(m,highlands=True):
    def floor(dx,dz,r):
        if r>.89:return GRASS if dx<0 or dz>0 else "andesite"
        if dz<-2 or dx>4:return "polished_andesite" if dz%4 else "stone_bricks"
        if dz==0 or dx==-5:return "exposed_cut_copper"
        return "smooth_stone" if dz>=0 else "polished_andesite"
    ellipse(m,-16,140,10,8,178,floor)
    if highlands: trail(m,[(-27,179,143),(-25,179,143),(-22,178,142)])
    # The real pool, wires and lamp occupy (-20..-16,179,140). Keep the aisle open.
    for x in range(-22,-10):
        m.put(x,177,136,"dark_prismarine")
        m.put(x,178,136,"water[level=0]")
        for z in (135,137):m.grade(x,z,178,"waxed_weathered_cut_copper" if x%4==0 else "stone_bricks")
    for x in (-23,-10):m.grade(x,136,178,"polished_andesite")
    for x,z in ((-22,134),(-12,134)):
        m.grade(x,z,178,"stone_bricks")
        for dy,block in enumerate(("polished_basalt[axis=y]","stripped_dark_oak_log[axis=y]",
                "waxed_oxidized_cut_copper","sea_lantern","deepslate_tile_slab[type=bottom,waterlogged=false]")):
            m.put(x,179+dy,z,block)
    for x in (-20,-18,-16):
        m.put(x,178,139,"waxed_oxidized_cut_copper")
    m.put(-11,179,139,"stone_brick_slab[type=bottom,waterlogged=false]")

LEGACY_OFFSETS = {"wind":(-162,-45,-53), "farm":(33,26,3), "workshop":(30,-4,-1)}

class OffsetMap:
    """The pre-highlands snapshot has different hills. Reuse props, not terrain assumptions."""
    def __init__(self, original, shift):
        self.original=original
        self.dx,self.dy,self.dz=shift
    def grade(self,x,z,y,cover):
        self.original.grade(x+self.dx,z+self.dz,y+self.dy,cover)
    def put(self,x,y,z,block):
        self.original.put(x+self.dx,y+self.dy,z+self.dz,block)
    def surface(self,x,z):
        return self.original.surface(x+self.dx,z+self.dz)-self.dy

def legacy(source):
    with zipfile.ZipFile(source) as z:
        files={name:z.read(name) for name in z.namelist()}
    manifest=json.loads(files["manifest.json"])
    if manifest.get("headlands"): raise ValueError("Expected the pre-highlands starter")
    m=ClearingMap(files)
    for key,build_site in (("wind",describe_wind),("farm",describe_farm),("workshop",describe_workshop)):
        view=OffsetMap(m,LEGACY_OFFSETS[key])
        build_site(view,False)
        # Short walk-in from the natural meadow, grading from its own original contour.
        x,y,z=FEET[key]
        trail(view,[(x+dx,view.surface(x+dx,z+dz),z+dz) for dx,dz in ((0,2),(3,6),(5,9),(5,12),(7,16))])
        view.grade(x,z,y-1,"gravel" if key=="wind" else "coarse_dirt" if key=="farm" else "smooth_stone")
    retire_stones(m)
    (DATA/"sky_academy/lesson_clearings_legacy.nbt").write_bytes(m.encode())
    sites=json.loads((DATA/"sky_academy/sites.json").read_text("utf8"))
    for site,coords in FEET.items():
        shift=LEGACY_OFFSETS["wind" if site.startswith("rune") else site]
        sites[site]=[coord+delta-origin for coord,delta,origin in zip(coords,shift,ORIGIN)]
    (DATA/"sky_academy/sites_legacy.json").write_text(json.dumps(sites,indent=2)+"\n","utf8")
    print(f"Legacy snapshot: {len(m.changes)} matched changes")

def retire_stones(m):
    for x,y,z in ((-181,198,-80),(-172,198,-84),(-163,198,-80)):
        for h in range(y-2,y+1):
            if str(m.read(x,h,z)["Name"])=="minecraft:chiseled_stone_bricks":
                m.put(x,h,z,"moss_block" if h==y-2 else "air")

def build(source,dest,patch_path):
    with zipfile.ZipFile(source) as f: files={k:f.read(k) for k in f.namelist()}
    manifest=json.loads(files["manifest.json"])
    if not manifest.get("headlands") or manifest.get("lesson_clearings"):
        raise ValueError("Use the unmodified RTF highlands starter as input")
    m=ClearingMap(files)
    describe_wind(m);describe_farm(m);describe_workshop(m)
    retire_stones(m)
    for name,(x,y,z) in FEET.items():
        if name.startswith("rune"): continue
        m.grade(x,z,y-1,"gravel" if name=="wind" else "coarse_dirt" if name=="farm" else "smooth_stone")
    patch=m.encode()
    manifest["lesson_clearings"]=1
    files["manifest.json"]=json.dumps(manifest,ensure_ascii=False).encode()
    with tempfile.NamedTemporaryFile(dir=dest.parent,suffix=".zip",delete=False) as tmp: tmpname=Path(tmp.name)
    try:
        with zipfile.ZipFile(tmpname,"w",zipfile.ZIP_DEFLATED,compresslevel=5) as z:
            for name,raw in files.items():
                info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(info,raw,compresslevel=5)
        tmpname.replace(dest)
    finally:tmpname.unlink(missing_ok=True)
    patch_path.write_bytes(patch)
    loc=DATA/"sky_academy/sites.json"
    sites=json.loads(loc.read_text("utf8"))
    sites.update({key:[i-o for i,o in zip(at,ORIGIN)] for key,at in FEET.items()})
    loc.write_text(json.dumps(sites,indent=2)+"\n","utf8")
    print(f"Ready: {len(m.changes)} graded/decorated blocks, {len(patch)} patch bytes")

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",type=Path,default=STARTER)
    parser.add_argument("--output",type=Path,default=STARTER)
    parser.add_argument("--patch",type=Path,default=PATCH)
    parser.add_argument("--legacy-source",type=Path,help="Build the migration patch for the pre-RTF snapshot instead")
    opts=parser.parse_args()
    if opts.legacy_source: legacy(opts.legacy_source)
    else: build(opts.source,opts.output,opts.patch)
