#!/usr/bin/env python3
"""Import the author's Blockbench-edited dark_hand.bbmodel back into the mod.

作者 2026-10-10 晚：「我现在更新了一下，你依照我的更新更新吧」——
他交给我们的是一份**导出的** .bbmodel（方块 31 -> 42、手指重新摆位、爪子加了几枚）＋ 重画过的贴图 ✓。

⚠️ **模型不是作者做的** ✗ —— 作者原话：「**手的模型不是我做的啊，我帮你导出了**」✓
   ⇒ 原作者与**授权/署名**待确认（本仓规矩：分发前逐个确认授权）；
      所有文档/注释里**不要**写成"作者手改/作者自制" ✗，写"作者提供（导出）" ✓。

=> 从这一刻起，**事实来源是那份 .bbmodel**：
   `tools/gen_dark_hand_model.py`（程序生成几何＋贴图）**不再是**产物来源，**别再跑它**
   （会按自己的调色板重画贴图、并按老坐标重建模型 => 作者的活全没了）。
   本脚本就是替代它的那一步（bbmodel -> 发货文件）。

用法：
    python tools/import_dark_hand_bbmodel.py            # 导入（会覆盖发货的模型与贴图）
    python tools/import_dark_hand_bbmodel.py --check    # 只校验不写文件

导入时会**逐条卡住**下面这些"违反就是游戏里不显示/画错"的硬约束：
  * 贴图分辨率必须 32x32（模型的 UV 是按 32x32 图集逐像素写的，改尺寸会全部错位）
  * from/to 必须落在 -16..32（原版加载器窗口；越界 => **整个模型不加载** => 游戏里隐形）
  * 旋转**只能单轴**（Java 模型格式不支持双轴）且角度必须是 **22.5 的整数倍**
    （原版只接受 -45/-22.5/0/22.5/45，一个非法角整个模型不加载）
  * 每个面的 uv 必须落在贴图内

注意：本脚本**只打印 ASCII** —— 本机控制台是 GBK，带勾叉符号的 print 会直接抛 UnicodeEncodeError。
"""

import argparse
import base64
import io
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(REPO, "magic_modelandtexture", "dark_hand", "dark_hand.bbmodel")
MODEL_OUT = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                         "models", "projectile", "dark_hand.json")
TEX_OUT = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                       "textures", "spell_projectile", "dark_hand.png")

TEXTURE_REF = "tnc:spell_projectile/dark_hand"
LOADER_MIN, LOADER_MAX = -16, 32          # vanilla model extents
LEGAL_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


def fail(msg):
    print("ERROR: " + msg)
    sys.exit(1)


def load_bbmodel():
    if not os.path.isfile(BBMODEL):
        fail("missing %s" % BBMODEL)
    with open(BBMODEL, "r", encoding="utf-8") as fh:
        return json.load(fh)


def decode_texture(project):
    textures = project.get("textures") or []
    if not textures:
        fail("bbmodel has no texture")
    source = textures[0].get("source", "")
    if "base64," not in source:
        fail("texture is not embedded (source is not data:...;base64) -- re-save the project")
    png = base64.b64decode(source.split("base64,", 1)[1])
    try:
        from PIL import Image
    except ImportError:
        fail("Pillow is required to check the texture size (pip install pillow)")
    return png, Image.open(io.BytesIO(png))


def clean(value):
    """int when it is a whole number, otherwise a rounded float."""
    f = float(value)
    return int(f) if f.is_integer() else round(f, 2)


def build_element(src, index, tex_size):
    frm = [float(v) for v in src["from"]]
    to = [float(v) for v in src["to"]]
    for axis, name in enumerate("xyz"):
        if frm[axis] < LOADER_MIN or to[axis] > LOADER_MAX:
            fail("element %d: %s out of the loader window (%s..%s, allowed %d..%d) "
                 "-- the WHOLE model would fail to load"
                 % (index, name, frm[axis], to[axis], LOADER_MIN, LOADER_MAX))
        if to[axis] <= frm[axis]:
            fail("element %d: %s size is not positive (vanilla silently drops the cube)"
                 % (index, name))

    element = {"from": [clean(v) for v in frm], "to": [clean(v) for v in to]}

    rotation = src.get("rotation") or [0, 0, 0]
    if isinstance(rotation, dict):            # older bbmodel shape
        angle = float(rotation.get("angle", 0))
        axis = {"x": 0, "y": 1, "z": 2}.get(rotation.get("axis", "y"), 1)
        angles = [0.0, 0.0, 0.0]
        angles[axis] = angle
        origin = rotation.get("origin", [0, 0, 0])
    else:
        angles = [float(v) for v in rotation]
        origin = src.get("origin", [0, 0, 0])
    used = [i for i, a in enumerate(angles) if abs(a) > 1e-6]
    if len(used) > 1:
        fail("element %d has a multi-axis rotation %s -- the Java model format cannot "
             "express it; use a single axis in Blockbench (or split the cube)" % (index, angles))
    if used:
        angle = angles[used[0]]
        if not any(abs(angle - legal) < 1e-6 for legal in LEGAL_ANGLES):
            fail("element %d: rotation %s is not a legal angle -- vanilla only accepts "
                 "-45/-22.5/0/22.5/45, and one bad angle makes the whole model invisible"
                 % (index, angle))
        element["rotation"] = {"angle": clean(angle), "axis": "xyz"[used[0]],
                               "origin": [clean(v) for v in origin]}

    faces = {}
    for name, face in (src.get("faces") or {}).items():
        uv = face.get("uv")
        if uv is None:
            continue
        u0, v0, u1, v1 = [float(v) for v in uv]
        if min(u0, v0) < 0 or max(u1, v1) > tex_size:
            fail("element %d: face %s uv %s is outside the %dpx texture"
                 % (index, name, uv, tex_size))
        if u1 <= u0 or v1 <= v0:
            fail("element %d: face %s uv %s is degenerate" % (index, name, uv))
        faces[name] = {"uv": [clean(v) for v in uv], "texture": "#0"}
    if not faces:
        fail("element %d has no faces" % index)
    element["faces"] = faces
    return element


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate only, write nothing")
    args = parser.parse_args()

    project = load_bbmodel()
    resolution = project.get("resolution") or {}
    tex_size = resolution.get("width", 0)
    if tex_size != resolution.get("height", 0):
        fail("bbmodel resolution is not square: %s" % resolution)
    if tex_size != 32:
        fail("texture resolution must stay 32x32 (got %s) -- the model UVs are written in "
             "pixels of a 32x32 atlas, resizing shifts every UV" % tex_size)

    png, image = decode_texture(project)
    if image.size != (tex_size, tex_size):
        fail("embedded texture %s does not match the project resolution %s"
             % (image.size, resolution))

    elements = [build_element(src, i, tex_size)
                for i, src in enumerate(project.get("elements") or [])]
    if not elements:
        fail("bbmodel has no elements")

    # ---- ★ 居中到原点（引擎与渲染器都按这个前提工作）----
    #  引擎/我们的渲染器让投射物模型**绕实体位置**旋转，而且贴地锚点也按"模型中心在原点"算
    #  （老文档："包围盒居中到原点同样是必须的：不居中就会变成绕前方一点翻滚"）。
    #  作者手改过的模型 bbox 中心通常在别处（这次是 x 3.3 / y -0.5 / z 2.75）
    #  => 这里整体平移，让 bbox 中心回到原点。**形状一模一样**，只是原点位置变了。
    #  ⚠️ 旋转方块的 origin 也要一起平移，否则旋转轴会错位。
    xs = [c for e in elements for c in (e["from"][0], e["to"][0])]
    ys = [c for e in elements for c in (e["from"][1], e["to"][1])]
    zs = [c for e in elements for c in (e["from"][2], e["to"][2])]
    shift = [(min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0, (min(zs) + max(zs)) / 2.0]
    half = [(max(xs) - min(xs)) / 2.0, (max(ys) - min(ys)) / 2.0, (max(zs) - min(zs)) / 2.0]
    for e in elements:
        for key in ("from", "to"):
            e[key] = [clean(e[key][i] - shift[i]) for i in range(3)]
        if "rotation" in e:
            e["rotation"]["origin"] = [clean(e["rotation"]["origin"][i] - shift[i])
                                       for i in range(3)]

    model = {
        "credit": "Supplied by the project author (exported); original model author unconfirmed ",
        "textures": {"0": TEXTURE_REF, "particle": TEXTURE_REF},
        "elements": elements,
    }

    print("bbmodel   : %s" % os.path.basename(BBMODEL))
    print("resolution: %dx%d [ok]   elements: %d [ok]   rotated: %d"
          % (tex_size, tex_size, len(elements),
             sum(1 for e in elements if "rotation" in e)))
    print("bbox(raw) : x %s..%s  y %s..%s  z %s..%s  (units, 16 = 1 block)"
          % (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    print("centered  : shifted by (%.2f, %.2f, %.2f) -> bbox is now symmetric about origin"
          % (-shift[0], -shift[1], -shift[2]))
    print("bbox blk  : %.2f x %.2f x %.2f blocks (after centering)"
          % (half[0] * 2 / 16.0, half[1] * 2 / 16.0, half[2] * 2 / 16.0))
    print("HALF-HEIGHT for the renderer anchor: %.1f units = %.4f blocks"
          % (half[1], half[1] / 16.0))
    print("  -> TNNightGripEntity.MODEL_HALF_HEIGHT_UNITS should be %.4f" % (half[1] / 16.0))

    if args.check:
        print("--check: all constraints passed, nothing written [ok]")
        return 0

    with open(MODEL_OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    with open(TEX_OUT, "wb") as fh:
        fh.write(png)
    print("wrote %s" % os.path.relpath(MODEL_OUT, REPO))
    print("wrote %s (%d bytes)" % (os.path.relpath(TEX_OUT, REPO), len(png)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
