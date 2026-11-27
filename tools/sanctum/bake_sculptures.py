"""Bake the six original metre-scale glTF sculptures into indexed rigid bone batches.

Usage: python tools/sanctum/bake_sculptures.py PATH_TO_黑暗地下城造像
Only stdlib and NumPy are needed for geometry. Pillow is used for the portrait atlas.
"""
import argparse
import gzip
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

MODELS = {"angel": "守曦天使", "bleeding": "泣血圣使", "demon": "堕日魔身",
          "gate": "门神", "gargoyle": "檐狱石像鬼", "shade": "失影侍鬼"}
ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/tnc"


def lod(xyz, normal, uv, index, facial, overview):
    """Cluster within each artist-authored material island and each normal hemisphere."""
    index=index.reshape((-1,3))
    if len(index)<(14 if overview else 58):
        return xyz,normal,uv,index.ravel()
    extent=float(np.max(np.ptp(xyz,axis=0)))
    if extent<1.e-7:
        return xyz,normal,uv,index.ravel()
    aim=max(7,int(len(index)*(.095 if facial else .029))) if overview else max(32,int(len(index)*(.46 if facial else .18)))
    low,high=0.,.95 if overview else .46
    best=None
    dominant=np.argmax(np.abs(normal),axis=1)
    hemi=(dominant*2+(normal[np.arange(len(normal)),dominant]<0)).reshape((-1,1))
    local=(xyz-np.min(xyz,axis=0))/extent
    for _ in range(8):
        step=(low+high)*.5
        keys=np.column_stack((np.floor(local/step+.5).astype('i4'),hemi))
        _,first,ids=np.unique(keys,axis=0,return_index=True,return_inverse=True)
        faces=ids[index]
        faces=faces[(faces[:,0]!=faces[:,1])&(faces[:,1]!=faces[:,2])&(faces[:,0]!=faces[:,2])]
        if best is None or abs(len(faces)-aim)<abs(len(best[1])-aim):
            best=(first,faces)
        if len(faces)>aim:low=step
        else:high=step
    chosen,faces=best
    if not len(faces):return xyz,normal,uv,index.ravel()
    return xyz[chosen],normal[chosen],uv[chosen],faces.ravel()


def bake(source, key, overview=False):
    raw = source.read_bytes()
    length = struct.unpack_from("<I", raw, 12)[0]
    document = json.loads(raw[20:20 + length])
    binary = memoryview(raw)[28 + length:]

    def view(index):
        v = document["bufferViews"][index]
        start = v.get("byteOffset", 0)
        return binary[start:start + v["byteLength"]]

    def attribute(index):
        a = document["accessors"][index]
        cols = {"VEC3": 3, "VEC2": 2, "SCALAR": 1}[a["type"]]
        dtype = {5126: "<f4", 5123: "<u2", 5125: "<u4"}[a["componentType"]]
        assert "byteStride" not in document["bufferViews"][a["bufferView"]]
        return np.frombuffer(view(a["bufferView"]), dtype=dtype, count=a["count"] * cols,
                             offset=a.get("byteOffset", 0)).reshape((-1, cols))

    textures = ROOT / "textures/entity/sanctum"
    textures.mkdir(parents=True, exist_ok=True)
    atlas_name = {"angel":"sculpture","bleeding":"sculpture","gate":"gate"}.get(key,"demon")
    image = bytes(view(document["images"][0]["bufferView"]))
    texture = textures / (atlas_name + ".png")
    if key in {"bleeding", "gargoyle", "shade"}:
        assert texture.read_bytes() == image, "Shared source atlases differ"
    texture.write_bytes(image)
    bones = []
    triangle_total = 0
    authored_total = 0
    visited = set()

    def visit(index, parent):
        nonlocal triangle_total, authored_total
        assert index not in visited
        visited.add(index)
        node = document["nodes"][index]
        assert not any(k in node for k in ["rotation", "scale", "matrix"])
        if "mesh" in node:
            assert "translation" not in node and parent >= 0
            for p in document["meshes"][node["mesh"]]["primitives"]:
                assert p.get("mode", 4) == 4
                attr = p["attributes"]
                xyz, normal, uv = [attribute(attr[k]) for k in ["POSITION", "NORMAL", "TEXCOORD_0"]]
                ind = attribute(p["indices"]).ravel()
                assert len(ind) % 3 == 0 and max(ind) < len(xyz) and np.isfinite(xyz).all()
                authored_total+=len(ind)//3
                facial=bones[parent][0].startswith(("head","demon_orbit","demon_max","demon_man","gate_head","gate_mouth"))
                # Keep the real stone bearing under the feet. Its broad top is visible almost
                # edge-on from the fight floor, where aggressive merging showed pinholes.
                reduce=overview and bones[parent][0]!="chapel_plinth"
                xyz,normal,uv,ind=lod(xyz,normal,uv,ind,facial,reduce)
                mat = document["materials"][p["material"]]
                glow = max(mat.get("emissiveFactor", [0]))
                blend = mat.get("alphaMode") == "BLEND"
                # Fixed directional key plus soft reflected fill. Glow stays legible in the sanctuary dark.
                shade = np.clip(.65 + normal @ np.array([-.22, .32, .18]) + glow * .22, .29, 1)
                payload = np.zeros(len(xyz), dtype=[("p",">f4",(3,)), ("uv",">u2",(2,)), ("v","u1"),("n","i1",(3,)),("light","u1")])
                payload["p"] = xyz
                assert np.min(uv) >= -1e-5 and np.max(uv) <= 1.0001
                payload["uv"] = np.round(uv.clip(0, 1) * 65535)
                payload["v"] = np.round((.95 if blend else shade) * 255)
                payload["n"] = np.round(normal.clip(-1,1)*127)
                payload["light"] = min(15,round(13+glow*2))
                bones[parent][3].setdefault(blend, []).append((payload, ind))
                triangle_total += len(ind) // 3
            return
        current = len(bones)
        bones.append((node["name"], parent, node.get("translation", [0,0,0]), {}))
        for child in node.get("children", []):
            visit(child, current)

    for root in document["scenes"][document.get("scene", 0)]["nodes"]:
        visit(root, -1)
    assert len(visited) == len(document["nodes"])
    folder = ROOT / "models/sanctum"
    folder.mkdir(parents=True, exist_ok=True)
    def string(v):
        v = v.encode("utf-8")
        return struct.pack(">H",len(v)) + v
    output = bytearray(b"SCM2" + string(atlas_name) + struct.pack(">H",len(bones)))
    for name, parent, pivot, layers in bones:
        output.extend(string(name) + struct.pack(">h3fB",parent,*pivot,len(layers)))
        for translucent, entries in sorted(layers.items()):
            count = sum(len(v) for v,i in entries)
            indices = []
            offset = 0
            for vertices, index in entries:
                indices.append(index.astype(np.uint32) + offset)
                offset += len(vertices)
            output.extend(struct.pack(">?II",translucent,count,sum(map(len,indices))))
            output.extend(b"".join(v.tobytes() for v,i in entries))
            output.extend(np.concatenate(indices).astype(">u4").tobytes())
    (folder / (key + ("_view" if overview else "") + ".mesh.gz")).write_bytes(gzip.compress(output, compresslevel=7, mtime=0))
    print(key, "overview" if overview else "detail", len(bones), authored_total, "->", triangle_total, atlas_name)


def hud(source):
    folder = ROOT / "textures/gui/boss_hud"
    original = Image.open(folder / "materials_v1.png").convert("RGBA")
    result = Image.new("RGBA", original.size)
    portraits = Image.new("RGBA", (768,256))
    faces = Image.open(source / "三相面部特写.jpg").convert("RGB")
    colors = [((59,43,20),(199,151,74),(255,239,188)),
              ((15,8,14),(158,24,40),(255,135,108)),
              ((11,6,17),(67,24,54),(238,86,53))]
    for i, ((shadow, middle, highlight), source_row) in enumerate(zip(colors,[0,4,1])):
        for row, height, dest in [(source_row*128,128,i*128),(1536+source_row*32,32,1536+i*32)]:
            pixels = np.array(original.crop((0,row,1024,row+height)))
            value = np.dot(pixels[:,:,:3], [.3,.57,.13]) / 255
            points = [0,.46,.95]
            for c in range(3):
                pixels[:,:,c] = np.interp(value,points,[shadow[c],middle[c],highlight[c]])
            result.paste(Image.fromarray(pixels),(0,dest))
        face = faces.crop((i*710+96,118,i*710+608,630)).resize((214,214),Image.Resampling.LANCZOS)
        medallion = Image.new("RGBA",(256,256))
        draw = ImageDraw.Draw(medallion)
        draw.polygon([(128,0),(239,48),(250,157),(195,218),(128,254),(60,218),(6,157),(17,48)],fill=shadow,outline=highlight,width=5)
        clip = Image.new("L",(214,214))
        ImageDraw.Draw(clip).ellipse((1,1,212,212),fill=255)
        medallion.paste(face,(21,17),clip)
        draw.ellipse((19,15,236,232),outline=middle,width=6)
        portraits.paste(medallion,(256*i,0))
    result.paste(original.crop((0,1920,1024,1952)),(0,1920))
    result.save(folder / "sanctum_materials.png")
    portraits.save(folder / "sanctum_portraits.png")
    for dest, src in [("sanctum_materials","materials_v1"),("sanctum_portraits","portraits_v1")]:
        (folder/(dest+".png.mcmeta")).write_bytes((folder/(src+".png.mcmeta")).read_bytes())


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source",type=Path)
    source = parser.parse_args().source
    for key, name in MODELS.items():
        bake(source / "通用模型" / (name+".glb"),key)
        if key in {"angel","bleeding","demon","gate"}:
            bake(source / "通用模型" / (name+".glb"),key,overview=True)
    hud(source)
