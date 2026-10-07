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


def material_of(gen, faces):
    """按面的 UV 落在图集哪一块矩形里，反推材质名 ✓（认不出就退回 cube）。

    ★ 不能只看"第一个面的 UV 起点落在哪块里" ✗ —— 图集里两块矩形会**重叠**：
      `palm` 是 x 8..20 / y 0..12，`claw` 是 x 12..22 / y 8..18，
      爪子那 9 个方块的面 UV 起点是 (12, 8)，**两块都命中** ⇒ 取先出现的会把爪子标成 palm ✗
      （实测：第一次生成时 9 个 claw 全被标成 palm ✓ 就是这么来的）。
    正确判据：**整块方块的 UV 跨度被哪块矩形完整包含**，包含的那几块里取**面积最小**的
    （＝最具体的那块）✓；一个都不完整包含时，退回"中心最近" ✓；再不行才叫 cube ✓。
    """
    uvs = [f["uv"] for f in faces.values() if "uv" in f]
    if not uvs:
        return "cube"
    u0 = min(u[0] for u in uvs)
    v0 = min(u[1] for u in uvs)
    u1 = max(u[2] for u in uvs)
    v1 = max(u[3] for u in uvs)

    containing = []
    for name, rect in gen.ATLAS.items():
        x, y, w, h = rect[0]
        if x <= u0 and u1 <= x + w and y <= v0 and v1 <= y + h:
            containing.append((w * h, name))
    if containing:
        containing.sort()
        return containing[0][1]

    cu, cv = (u0 + u1) / 2.0, (v0 + v1) / 2.0
    best, best_distance = None, None
    for name, rect in gen.ATLAS.items():
        x, y, w, h = rect[0]
        distance = (cu - (x + w / 2.0)) ** 2 + (cv - (y + h / 2.0)) ** 2
        if best_distance is None or distance < best_distance:
            best, best_distance = name, distance
    return best or "cube"


def build():
    with open(MODEL_JSON, "r", encoding="utf-8") as fh:
        model = json.load(fh)
    with open(TEXTURE_PNG, "rb") as fh:
        png = fh.read()
    gen = load_generator()

    elements = []
    by_material = {}
    for src in model["elements"]:
        mat = material_of(gen, src.get("faces", {}))
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
            "name": mat,
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
        by_material.setdefault(mat, []).append(element["uuid"])

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
