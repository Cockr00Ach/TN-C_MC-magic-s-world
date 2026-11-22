"""Bake the headlands into the authored fresh-world starter, outside Minecraft.

The source can be the original starter or the output of bake_skyhaven_starter.
The historical Git blob is accepted as the baseline when rebuilding the shipped
revision in place. Player worlds are never inputs to this tool.
"""
import argparse
import gzip
import io
import json
import os
import subprocess
import tempfile
import zipfile
from copy import deepcopy
from pathlib import Path

import nbtlib as n
import numpy as np

from finish_skyhaven_gateway import anvil
import skyhaven_districts as districts
import skyhaven_alpine as alpine
import skyhaven_sculpt as terrain

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/tnc"
TARGET = DATA / "skyhaven/starter.zip"
BASE_BLOB = "760ded8d04504af94edc5c2c3c548dc037fc74c5"
ORIGIN = (-260, 90, -260)
WEST, NORTH = terrain.X0, terrain.Z0
NX, NZ, NY = terrain.WIDTH, terrain.DEPTH, 414
CHUNKS_X = range((WEST+ORIGIN[0])//16,(WEST+ORIGIN[0]+NX-1)//16+1)
CHUNKS_Z = range((NORTH+ORIGIN[2])//16,(NORTH+ORIGIN[2]+NZ-1)//16+1)
AIR = n.Compound({"Name": n.String("minecraft:air")})
BIOME = n.Compound({"palette": n.List[n.String]([n.String("tnc:skyhaven")])})


class Volume:
    def __init__(self, directory):
        self.blocks = np.memmap(Path(directory)/"terrain.raw", mode="w+", dtype=np.uint16, shape=(NY,NZ,NX))
        self.palette = []
        self.keys = {}
        self.strings = {}
        self.register(AIR)

    def register(self, state):
        key = anvil.state_key(state)
        if key not in self.keys:
            self.keys[key] = len(self.palette)
            self.palette.append(state)
        return self.keys[key]

    def id(self, text):
        if text not in self.strings:
            name, _, properties = text.partition("[")
            state = n.Compound({"Name": n.String(name)})
            if properties:
                state["Properties"] = n.Compound({k:n.String(v) for k,v in
                    (pair.split("=",1) for pair in properties.removesuffix("]").split(","))})
            self.strings[text] = self.register(state)
        return self.strings[text]

    def set(self, x,y,z,block):
        if not (WEST<=x<WEST+NX and NORTH<=z<NORTH+NZ and 0<=y<NY):
            raise IndexError((x,y,z))
        self.blocks[y,z-NORTH,x-WEST] = self.id(block) if isinstance(block,str) else block

    def views(self, cx,sy,cz):
        x = cx*16-ORIGIN[0]-WEST
        z = cz*16-ORIGIN[2]-NORTH
        y = sy*16-ORIGIN[1]
        lo = (max(0,y),max(0,z),max(0,x))
        hi = (min(NY,y+16),min(NZ,z+16),min(NX,x+16))
        if any(b<=a for a,b in zip(lo,hi)):
            return None
        return (tuple(slice(a,b) for a,b in zip(lo,hi)),
                tuple(slice(a-v,b-v) for a,b,v in zip(lo,hi,(y,z,x))))


def region_name(cx,cz):
    return f"realm/region/r.{cx//32}.{cz//32}.mca"


def unpack(v,regions):
    for cx in CHUNKS_X:
        for cz in CHUNKS_Z:
            record=regions[region_name(cx,cz)].get(cx%32+32*(cz%32))
            if not record:
                continue
            chunk=anvil.read_chunk(record)
            for section in chunk["sections"]:
                states=section.get("block_states")
                bounds=v.views(cx,int(section["Y"]),cz)
                if bounds is None or states is None:
                    continue
                pal=np.array([v.register(s) for s in states["palette"]],dtype=np.uint16)
                if np.any(pal):
                    target,inner=bounds
                    v.blocks[target]=pal[anvil.decode(states).reshape(16,16,16)[inner]]


def apply_peak(v):
    data=n.load(DATA/"sky_academy/peak_v2.nbt")
    pal=np.array([v.register(s) for s in data["Palette"]],dtype=np.uint16)
    x,y,z,b,a=np.asarray(data["Changes"]).reshape(-1,5).T
    key=(y,z-NORTH,x-WEST)
    current=v.blocks[key]
    ok=(current==pal[b])|(current==pal[a])
    if not np.all(ok):
        # Leaf distance is recalculated during gameplay and does not change the block.
        for i in np.flatnonzero(~ok):
            if not anvil.compatible(v.palette[current[i]],v.palette[pal[b[i]]],v.palette[pal[a[i]]]):
                raise ValueError(f"Summit source changed at {x[i],y[i],z[i]}")
    v.blocks[key]=pal[a]


def write_chunks(v,regions,extras):
    ocean=anvil.read_chunk(regions[region_name(16,16)][16+16*32])
    count=added=0
    for cx in CHUNKS_X:
        for cz in CHUNKS_Z:
            records=regions[region_name(cx,cz)]
            idx=cx%32+32*(cz%32)
            record=records.get(idx)
            has_be=(cx,cz) in extras
            if not record and not has_be:
                # Avoid allocating a generated ocean chunk outside the new land.
                bounds=v.views(cx,6,cz)
                if bounds is None or not np.any(v.blocks[:,bounds[0][1],bounds[0][2]]):
                    continue
            c=anvil.read_chunk(record) if record else deepcopy(ocean)
            sections={int(s["Y"]):s for s in c["sections"]}
            retained=[]
            for entity in c["block_entities"]:
                bx,by,bz=(int(entity[k]) for k in "xyz")
                ix,iy,iz=bx-ORIGIN[0]-WEST,by-ORIGIN[1],bz-ORIGIN[2]-NORTH
                if 0<=ix<NX and 0<=iy<NY and 0<=iz<NZ:
                    section=sections[by//16]["block_states"]
                    original=section["palette"][int(anvil.decode(section)[(by%16)*256+(bz%16)*16+bx%16])]
                    new=v.palette[int(v.blocks[iy,iz,ix])]
                    if original["Name"]!=new["Name"]:
                        print(f"Retired template fixture {entity['id']} at {(bx,by,bz)}",flush=True)
                        continue
                retained.append(entity)
            c["block_entities"]=n.List[n.Compound](retained)
            changed=False
            for sy in range(5,32):
                bounds=v.views(cx,sy,cz)
                if bounds is None:
                    continue
                target,inner=bounds
                wanted=v.blocks[target]
                s=sections.get(sy)
                if s is None and not np.any(wanted):
                    continue
                old=s.get("block_states") if s is not None else None
                if old:
                    pal=np.array([v.register(st) for st in old["palette"]],dtype=np.uint16)
                    cells=pal[anvil.decode(old)].reshape(16,16,16)
                else:
                    cells=np.zeros((16,16,16),dtype=np.uint16)
                if np.array_equal(cells[inner],wanted): continue
                cells[inner]=wanted
                used,values=np.unique(cells.reshape(-1),return_inverse=True)
                if s is None:
                    s=n.Compound({"Y":n.Byte(sy),"biomes":deepcopy(BIOME)})
                    c["sections"].append(s)
                s["block_states"]=anvil.encode([v.palette[i] for i in used],values.reshape(-1))
                changed=True
            if not record:
                c["xPos"],c["zPos"]=n.Int(cx),n.Int(cz)
                c["structures"]=n.Compound({"starts":n.Compound(),"References":n.Compound()})
                added+=1
            if has_be:
                entries=extras.pop((cx,cz))
                existing={(int(b["x"]),int(b["y"]),int(b["z"])) for b in c["block_entities"]}
                if existing.intersection(entries):raise ValueError(f"Overlapping block entity at {cx,cz}")
                c["block_entities"].extend(entries.values())
            # Heightmaps use ten bits per column in the 512-high dimension. The game
            # rebuilds missing maps and relights authored chunks on their first load.
            if changed or has_be or "Heightmaps" in c:
                c.pop("Heightmaps",None)
                if changed or not record:
                    c["isLightOn"]=n.Byte(0)
                    for s in c["sections"]:
                        s.pop("BlockLight",None);s.pop("SkyLight",None)
                records[idx]=anvil.serialize(c)
                count+=changed
    if extras:raise ValueError(f"Block entities outside authored island: {list(extras)}")
    return count,added


def bake(rtf_jar,base=TARGET,destination=TARGET):
    with zipfile.ZipFile(base) as z:
        files={name:z.read(name) for name in z.namelist()}
    manifest=json.loads(files["manifest.json"])
    if manifest.get("headlands"):
        raw=subprocess.check_output(["git","cat-file","blob",BASE_BLOB],cwd=ROOT)
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            files={name:z.read(name) for name in z.namelist()}
        manifest=json.loads(files["manifest.json"])
    master=n.File.parse(io.BytesIO(gzip.decompress(files["island.dat"])))
    state=master["data"]
    if tuple(int(state["Origin"+k]) for k in "XYZ")!=ORIGIN:raise ValueError("Unrecognized island origin")
    regions={region_name(x,z):anvil.region_chunks(files[region_name(x,z)]) for x in (-1,0) for z in (-1,0)}
    work=ROOT/"work"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work,prefix="headlands-") as temp:
        v=Volume(temp)
        try:
            unpack(v,regions);apply_peak(v)
            saved=districts.capture(v)
            print("Loaded starter, summit and portal",flush=True)
            districts.remove_origin(v,saved)
            land,survey=alpine.write_terrain(v,rtf_jar)
            districts.place(v,saved)
            changed,added=write_chunks(v,regions,{})
        finally:
            v.blocks._mmap.close()
    for name,records in regions.items():
        files[name]=anvil.pack_region(files[name],records)
    state["ArrivalX"],state["ArrivalY"],state["ArrivalZ"]=map(n.Int,(-102,180,188))
    state["RebuiltSkyhaven"]=n.Byte(1)
    def encoded(tag):
        stream=io.BytesIO();tag.write(stream);return gzip.compress(stream.getvalue(),mtime=0)
    files["island.dat"]=encoded(master)
    files["realm/data/tnc_first_sight_peak_v2.dat"]=encoded(n.File({"DataVersion":n.Int(3465),"data":n.Compound({"Done":n.Byte(1)})}))
    from skyhaven_waterfall_master import place_master
    place_master(files,survey["waterfall"]["master_world"])
    files["realm/.tnc-highlands-lod-v1"]=b"1"
    manifest.update(revision=7,headlands=True,chunks=manifest["chunks"]+added,
                    gateway_foundation={"centre":[-102,178,188]},rtf_survey=survey)
    files["manifest.json"]=json.dumps(manifest).encode()
    destination=Path(destination)
    with tempfile.NamedTemporaryFile(dir=destination.parent,suffix=".zip",delete=False) as tmp:
        stage=Path(tmp.name)
    try:
        with zipfile.ZipFile(stage,"w",zipfile.ZIP_DEFLATED,compresslevel=5) as archive:
            for name,content in files.items():archive.writestr(name,content)
        os.replace(stage,destination)
        if destination.resolve()==TARGET.resolve():
            terrain.write_coast(land)
    finally:
        stage.unlink(missing_ok=True)
    print(f"Baked {changed} changed chunks and {added} additional chunks into {destination}")


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",type=Path,default=TARGET)
    parser.add_argument("--output",type=Path,default=TARGET)
    parser.add_argument("--rtf-jar",type=Path,required=True,help="ReTerraForged 0.0.6 Forge 1.20.1 used for the survey")
    parser.add_argument("--survey",type=Path,help="Fit a fresh continuous generator sample before baking")
    args=parser.parse_args()
    if args.survey: alpine.fit_survey(args.survey)
    bake(args.rtf_jar,args.source,args.output)
