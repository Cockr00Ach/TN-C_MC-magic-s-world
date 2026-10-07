#!/usr/bin/env python3
"""把「黑夜之手」模型导成一个**能直接双击打开的 Blockbench 工程**（.bbmodel）✓

作者 2026-10-10：「**把模型导进 blockbench，我改改贴图**」。

为什么需要这一步：
  * 这个模型是 `tools/gen_dark_hand_model.py` **程序生成**的（31 个方块 ＋ 程序画的 32x32 图集），
    仓库里**没有** .bbmodel ✗；
  * 让 Blockbench 直接导入那份 Java 模型 JSON 也做得成，但它的贴图引用是
    `tnc:spell_projectile/dark_hand`（带命名空间的路径）⇒ Blockbench 解析不到，
    得手动找 PNG、手动把分辨率设成 32x32 ✗；
  ⇒ 本脚本把**几何 ＋ 贴图（base64 内嵌）**一起写进 .bbmodel ⇒ 打开就能直接刷贴图 ✓。

★ 方块名字按 **UV 落在图集哪一块** 反推（palm / claw / digit_1 …）——
  光看 UV 认不出哪块是哪块 ✗，起了名字在 Blockbench 里就能整组选中（比如只看爪子）✓。
  调色板直接 **import 生成器**，不另抄一份 ⇒ 永远和产物同源 ✓。

输出（`magic_modelandtexture/` 在版本库里，会跟着提交 ✓）：
  magic_modelandtexture/dark_hand/dark_hand.bbmodel    ← 双击打开即用（贴图已在里面）
  magic_modelandtexture/dark_hand/dark_hand.png        ← 同款参考图，方便外部对照

改完贴图怎么回来（**这一步很重要** ✗）：
  1. 在 Blockbench 里刷完 → 右侧 Textures 面板里对 `dark_hand.png` 右键 → **Save As / Export**，
     覆盖到 `src/main/resources/assets/tnc/textures/spell_projectile/dark_hand.png` ✓
     （⚠️ 分辨率必须还是 **32x32** ✗ —— 模型的 UV 是按这张图集逐像素写的，改尺寸会让 UV 全错位）；
  2. 告诉我 → 我重编译 ＋ 装机 ✓。
  ⚠️ 别让谁再跑一次 `tools/gen_dark_hand_model.py` ✗ —— 那会**按调色板重画贴图**、覆盖你手绘的这张。

用法： python tools/gen_dark_hand_bbmodel.py
"""

import base64
import hashlib
import importlib.util
import json
import os
import sys
import uuid

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_JSON = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                          "models", "projectile", "dark_hand.json")
TEXTURE_PNG = os.path.join(REPO, "src", "main", "resources", "assets", "tnc",
                           "textures", "spell_projectile", "dark_hand.png")
OUT_DIR = os.path.join(REPO, "magic_modelandtexture", "dark_hand")
OUT_BBMODEL = os.path.join(OUT_DIR, "dark_hand.bbmodel")
OUT_PNG = os.path.join(OUT_DIR, "dark_hand.png")

TEX = 32                       # 与生成器里的图集同尺寸 ✓


def load_generator():
    """把生成器当模块读进来（它顶层只有常量与函数，import 不会跑任何东西 ✓）。"""
    path = os.path.join(REPO, "tools", "gen_dark_hand_model.py")
    spec = importlib.util.spec_from_file_location("gen_dark_hand_model", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def materials_of(gen, faces):
    """这块方块用到了哪几块贴图区域 ✓（可能不止一块），按**图集声明顺序**排。

    ★ 为什么要分两级判（2026-10-10 踩过两次 ✗）：
      图集里的矩形**会重叠** —— `claw`(x12..22,y8..18) 与 `palm`(x8..14,y0..12) 就重叠，
      爪子的面 UV 正好落在重叠区 ⇒ 只按"哪块包含它"去判会随机站错队
      （拆完 palm 之后它面积变小，9 个爪子一度又被判成 palm ✗）。
      所以：
        ① **先看整块方块的 UV 跨度**落在哪块里 —— 唯一命中就用它 ✓（腕/指/爪/灵光都走这条）；
        ② 一个都没唯一命中，说明这块方块**一面一块贴图**（掌板：south 掌心用 `palm`、
           north 手背用 `palm_back`）⇒ 逐面判，取并集 ✓；
      逐面判时同样取"完整包含它的**最小**那块"（最具体 ✓），都不完整包含就取中心最近的 ✓。
    """
    uvs = [f["uv"] for f in faces.values() if "uv" in f]
    if not uvs:
        return ["cube"]
    u0 = min(u[0] for u in uvs)
    v0 = min(u[1] for u in uvs)
    u1 = max(u[2] for u in uvs)
    v1 = max(u[3] for u in uvs)

    def contains(rect, a0, b0, a1, b1):
        x, y, w, h = rect[0]
        return x <= a0 and a1 <= x + w and y <= b0 and b1 <= y + h

    whole = [name for name, rect in gen.ATLAS.items() if contains(rect, u0, v0, u1, v1)]
    if len(whole) == 1:
        return whole

    found = []
    for face in faces.values():
        if "uv" not in face:
            continue
        a0, b0, a1, b1 = face["uv"]
        best = None
        for name, rect in gen.ATLAS.items():
            if contains(rect, a0, b0, a1, b1):
                area = rect[0][2] * rect[0][3]
                if best is None or area < best[0]:
                    best = (area, name)
        if best is None:
            cu, cv = (a0 + a1) / 2.0, (b0 + b1) / 2.0
            distance = None
            for name, rect in gen.ATLAS.items():
                x, y, w, h = rect[0]
                d = (cu - (x + w / 2.0)) ** 2 + (cv - (y + h / 2.0)) ** 2
                if distance is None or d < distance:
                    best, distance = (0, name), d
        if best and best[1] not in found:
            found.append(best[1])

    if not found:
        return ["cube"]
    # 按图集**声明顺序**排 ⇒ 掌板那块的第一块是 `palm`（分组落在 palm 上，最好找 ✓）
    order = list(gen.ATLAS.keys())
    found.sort(key=lambda n: order.index(n) if n in order else len(order))
    return found


def build():
    with open(MODEL_JSON, "r", encoding="utf-8") as fh:
        model = json.load(fh)
    with open(TEXTURE_PNG, "rb") as fh:
        png = fh.read()
    gen = load_generator()

    elements = []
    by_material = {}
    for src in model["elements"]:
        mats = materials_of(gen, src.get("faces", {}))
        # 方块名 = 用到的全部材质（掌板会是 `palm+palm_back` ✓）；
        # 分组按**第一块**（＝最具体/最先遇到的那块）⇒ Outliner 仍然是 9 组，干净 ✓
        primary = mats[0]
        name = "+".join(mats)
        rot = src.get("rotation") or {}
        angle = rot.get("angle", 0) or 0
        axis = rot.get("axis", "y")
        rotation = [0, 0, 0]
        if axis == "x":
            rotation[0] = angle
        elif axis == "y":
            rotation[1] = angle
        elif axis == "z":
            rotation[2] = angle

        element = {
            "name": name,
            "box_uv": False,
            "render_order": "default",
            "rescale": False,
            "locked": False,
            "shade": True,
            "light_emission": 0,
            "export": True,
            "scope": 0,
            "allow_mirror_modeling": True,
            "from": src["from"],
            "to": src["to"],
            "autouv": 0,
            "color": 6,
            "origin": rot.get("origin", [0, 0, 0]),
            "rotation": rotation,
            "faces": {name: {"uv": face["uv"], "texture": 0}
                      for name, face in src.get("faces", {}).items()},
            "uuid": str(uuid.uuid4()),
        }
        elements.append(element)
        by_material.setdefault(primary, []).append(element["uuid"])

    # 按材质分组（Blockbench 左侧 outliner 里能整组点选 ✓）
    outliner = []
    for mat, uuids in by_material.items():
        outliner.append({
            "name": mat,
            "origin": [0, 0, 0],
            "uuid": str(uuid.uuid4()),
            "color": 0,
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": uuids,
        })

    project = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "dark_hand",
        "parent": "",
        "java_block_version": "1.9.0",
        "ambientocclusion": model.get("ambientocclusion", True),
        "front_gui_light": False,
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": TEX, "height": TEX},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "path": "",
            "name": "dark_hand.png",
            "folder": "block",
            "namespace": "",
            "id": "0",
            # ★ Java 模型里 textures 有 "particle" 这一条 ⇒ 这里打开开关，
            #   来回导出时那个字段不会丢 ✓
            "particle": True,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "uuid": str(uuid.uuid4()),
            "source": "data:image/png;base64," + base64.b64encode(png).decode("ascii"),
        }],
    }

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(OUT_BBMODEL, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(project, fh, ensure_ascii=False)
    with open(OUT_PNG, "wb") as fh:
        fh.write(png)

    print("wrote %s (%d bytes)" % (OUT_BBMODEL, os.path.getsize(OUT_BBMODEL)))
    print("wrote %s (%d bytes)" % (OUT_PNG, os.path.getsize(OUT_PNG)))
    print("elements : %d   分组 %d 个" % (len(elements), len(by_material)))
    print("分组明细 : " + ", ".join("%s×%d" % (k, len(v)) for k, v in by_material.items()))
    return 0


if __name__ == "__main__":
    sys.exit(build())
