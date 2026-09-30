# -*- coding: utf-8 -*-
"""mirror_wing.py -- copy the LEFT wing bones onto the RIGHT side, mirrored.

Rule (mirror across the x=0 plane):
    cube.origin.x = -(origin.x + size.x)      keep size
    bone.pivot.x  = -pivot.x
    rotation: axis y/z -> angle = -angle ; axis x -> angle unchanged ; origin.x -> -origin.x
    cube.mirror   = true                      (so the TEXTURE mirrors too, not repeats)

Reads/writes src/main/resources/assets/tnc/geo/entity/light_wings.geo.json in place,
keeping the author's left wing untouched and preserving bone order.
ASCII only.
"""
import json, copy, os

P = "src/main/resources/assets/tnc/geo/entity/light_wings.geo.json"
PREFIX_L, PREFIX_R = "wingLeft", "wingRight"

def mirror_cube(c):
    d = copy.deepcopy(c)
    o = d["origin"]; s = d["size"]
    d["origin"] = [-(o[0] + s[0]), o[1], o[2]]
    d["mirror"] = True
    rot = d.get("rotation")
    if rot and rot.get("angle"):
        ax = rot.get("axis", "y")
        if ax in ("y", "z"):
            rot["angle"] = -rot["angle"]
        ro = rot.get("origin")
        if ro:
            rot["origin"] = [-ro[0], ro[1], ro[2]]
    return d

def mirror_bone(b):
    d = copy.deepcopy(b)
    d["name"] = PREFIX_R + b["name"][len(PREFIX_L):]
    if b.get("parent", "").startswith(PREFIX_L):
        d["parent"] = PREFIX_R + b["parent"][len(PREFIX_L):]
    p = d.get("pivot")
    if p:
        d["pivot"] = [-p[0], p[1], p[2]]
    d["cubes"] = [mirror_cube(c) for c in (b.get("cubes") or [])]
    return d

def main():
    doc = json.load(open(P, encoding="utf-8"))
    geos = doc["minecraft:geometry"]
    for g in geos:
        bones = g["bones"]
        left = [b for b in bones if b["name"].startswith(PREFIX_L)]
        if not left:
            print("no left wing bones found")
            continue
        mirrors = {PREFIX_R + b["name"][len(PREFIX_L):]: mirror_bone(b) for b in left}
        out, used = [], set()
        for b in bones:
            if b["name"].startswith(PREFIX_R):
                m = mirrors.get(b["name"])
                if m:
                    out.append(m)          # replace the old right bones in place
                    used.add(b["name"])
                else:
                    out.append(b)
            else:
                out.append(b)
        for name, m in mirrors.items():
            if name not in used:
                out.append(m)              # a new right bone the author added
        g["bones"] = out
        print("left bones :", [b["name"] for b in left])
        print("mirrored   :", list(mirrors.keys()))
    open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, indent=2, ensure_ascii=False) + "\n")
    print("written:", P, os.path.getsize(P), "bytes")

if __name__ == "__main__":
    main()