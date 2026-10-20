"""把 geo.json + 贴图导成 Blockbench 可以直接打开的 .bbmodel。

为什么要单独写这个脚本：生成器原本自己拼 bbmodel，但拼错了三处，导致在
Blockbench 里打开是"缺面 / 透明 / 部件错位"：

  1. meta.box_uv 写成 True，而每个 cube 里存的是逐面 uv —— 编辑器按 Box UV
     解释，六个面全部落到贴图外的透明区；
  2. element.box_uv 也写成 true，同样会让方块回到"盒子 UV"模式；
  3. outliner 是空数组 —— 没有任何骨骼，方块全散在根上，层级丢失。

这里按 Blockbench 自己保存的工程结构（参照 art/pasture/models/*.bbmodel）
重建：box_uv=false、每个 cube 逐面 uv、outliner 里是骨骼树、骨骼 children
里放 cube 的 uuid。

用法：
    python tools/export_bbmodel.py <geo.json> <贴图.png> <输出.bbmodel>
"""

import base64
import io
import json
import sys
import uuid


def stable_uuid(seed: str) -> str:
    """由名字推出稳定的 uuid，重复导出不会每次都不一样。"""
    return uuid.uuid5(uuid.NAMESPACE_URL, "tnc:" + seed).hex


def face_rects(w, h, d):
    """与生成器一致的 box-UV 六面矩形：上排 up/down，下排 east/north/west/south。"""
    return {
        "up": (d, 0, w, d),
        "down": (d + w, 0, w, d),
        "east": (0, d, d, h),
        "north": (d, d, w, h),
        "west": (d + w, d, d, h),
        "south": (d + w + d, d, w, h),
    }


def build(geo_path, tex_path, out_path, name=None):
    geo = json.load(io.open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    tw = geo["description"].get("texture_width", 64)
    th = geo["description"].get("texture_height", 64)
    with open(tex_path, "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")

    elements = []
    own = {}          # 骨骼名 -> 属于它的 cube uuid 列表
    bones = {}        # 骨骼名 -> 骨骼数据
    order = []

    for bone in geo["bones"]:
        bname = bone["name"]
        bones[bname] = bone
        own.setdefault(bname, [])
        order.append(bname)
        for cube in bone.get("cubes", []):
            ox, oy, oz = cube["origin"]
            sx, sy, sz = cube["size"]
            cname = cube.get("name") or (bname + "_cube")
            uid = stable_uuid(cname)
            u, v = cube["uv"]
            rects = face_rects(int(round(sx)), int(round(sy)), int(round(sz)))
            faces = {}
            for fname, (dx, dy, fw, fh) in rects.items():
                faces[fname] = {
                    "uv": [u + dx, v + dy, u + dx + fw, v + dy + fh],
                    "texture": 0,
                }
            el = {
                "name": cname,
                "box_uv": False,
                "rescale": False,
                "locked": False,
                "light_emission": 0,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": [ox, oy, oz],
                "to": [ox + sx, oy + sy, oz + sz],
                "autouv": 0,
                "color": 0,
                "origin": cube.get("pivot", [ox + sx / 2.0, oy, oz + sz / 2.0]),
                "faces": faces,
                "type": "cube",
                "uuid": uid,
            }
            rot = cube.get("rotation")
            if rot:
                el["rotation"] = [float(rot[0]), float(rot[1]), float(rot[2])]
            elements.append(el)
            own[bname].append(uid)

    # 组骨骼树：children 里 cube 用 uuid 字符串，子骨骼用对象
    children_of = {b: [] for b in order}
    roots = []
    for bname in order:
        parent = bones[bname].get("parent")
        if parent and parent in children_of:
            children_of[parent].append(bname)
        else:
            roots.append(bname)

    def make_bone(bname):
        b = bones[bname]
        node = {
            "name": bname,
            "origin": b.get("pivot", [0, 0, 0]),
            "rotation": [float(x) for x in b.get("rotation", [0, 0, 0])],
            "uuid": stable_uuid("bone:" + bname),
            "children": list(own.get(bname, [])),
        }
        for child in children_of[bname]:
            node["children"].append(make_bone(child))
        return node

    outliner = [make_bone(b) for b in roots]

    doc = {
        "meta": {
            "format_version": "4.10",
            "model_format": "free",
            "box_uv": False,
        },
        "name": name or geo["description"].get("identifier", "model"),
        "model_identifier": geo["description"].get("identifier", "model"),
        "resolution": {"width": tw, "height": th},
        "elements": elements,
        "outliner": outliner,
        "textures": [
            {
                "path": "",
                "name": tex_path.replace("\\", "/").split("/")[-1],
                "folder": "entity",
                "namespace": "tnc",
                "id": "0",
                "width": tw,
                "height": th,
                "uv_width": tw,
                "uv_height": th,
                "particle": False,
                "use_as_default": False,
                "layers_enabled": False,
                "render_mode": "default",
                "render_sides": "auto",
                "frame_time": 1,
                "frame_order_type": "loop",
                "frame_interpolate": False,
                "visible": True,
                "internal": True,
                "saved": False,
                "uuid": stable_uuid("tex:" + tex_path),
                "relative_path": "",
                "source": "data:image/png;base64," + b64,
            }
        ],
        "display": {},
        "animations": [],
    }

    io.open(out_path, "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, ensure_ascii=False, indent=1)
    )
    return len(elements), len(bones), len(roots)


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print(__doc__)
        raise SystemExit(2)
    n_el, n_bone, n_root = build(sys.argv[1], sys.argv[2], sys.argv[3])
    print("wrote %s: %d cubes, %d bones (%d roots)" % (sys.argv[3], n_el, n_bone, n_root))
