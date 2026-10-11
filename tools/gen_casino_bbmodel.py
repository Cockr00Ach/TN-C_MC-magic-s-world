#!/usr/bin/env python3
"""把赌场那三台机器的几何导成**能直接双击打开的 Blockbench 工程**（.bbmodel）。

作者 2026-10-10：「**先给我看看 blockbench 文件**」。

背景（这一步为什么必须存在）：
  * 三台机器（老虎机 / 转盘赌桌 / 赌博桌）的几何是**写在 Java 里的**
    （各自渲染器的 `buildParts()`，一行一个 `add(out, x0,y0,z0, x1,y1,z1, CasinoPalette.XXX)`）；
  * 当初**故意**没做 `models/*.json`（原版加载器只允许顶点在 -16..32，2 格宽的机器会被直接拒绝，
    而且 element 只能绕单轴转固定角度，做不出"转盘真的在转"）——
    详见 docs/casino/赌场机器_交接.md 第二节；
  * 于是**仓库里一个 .bbmodel 都没有** ✗ ⇒ 作者没法在 Blockbench 里看、也没法改。
  ⇒ 本脚本从 Java 源码里的盒子表**反向导出**成 .bbmodel，让作者能转着看、能改。

★ 事实来源仍然是 **Java 源码**（这份 .bbmodel 是它的投影）。
  作者改完 .bbmodel 之后，要用 `tools/import_casino_bbmodel.py` 导回 Java —— 见那个脚本的头注释。

坐标换算（两套单位，别搞混）：
  * Java 源码里的 `add()` 坐标单位是**格**（1.0 = 1 格）；**例外**是赌博桌，
    它的坐标是"模型单位"（1 单位 = 1/16 格，照抄 tools/gen_gamble_table.py），
    在 `add()` 里统一除以 16 ⇒ 本脚本按源码里有没有 `/ 16.0F` 自动判分母。
  * Blockbench 的 `from`/`to` 单位是 **1/16 格**（一个方块 = 0..16）
    ⇒ 本脚本把"格"乘 16 写进去。
  ⇒ 所以 **1 格 = 16 个 Blockbench 单位**；一台 2x2x1 的机器在 Blockbench 里就是 0..32 x 0..16 x 0..32。

贴图：
  自绘的产物**根本不读贴图**（纯顶点色 + 逐面明暗）⇒ 这里只生成一张**16x16 中灰占位图**
  内嵌进工程，纯粹是为了 Blockbench 能显示"有张图"。**在 Blockbench 里看到的颜色来自
  Cube 的 vertex colors**（本脚本按 CasinoPalette 逐个写进去了），不是来自这张占位图。

用法：
    python tools/gen_casino_bbmodel.py

输出（`model-source/` 在版本库里，会跟着提交）：
    model-source/casino/slot_machine.bbmodel
    model-source/casino/roulette_table.bbmodel
    model-source/casino/gamble_table.bbmodel
    model-source/casino/_atlas_placeholder.png
"""

import base64
import json
import os
import re
import struct
import sys
import uuid
import zlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(REPO, "src", "main", "java", "com", "tnc", "tnc", "casino")
OUT_DIR = os.path.join(REPO, "model-source", "casino")

PALETTE_JAVA = os.path.join(SRC_DIR, "client", "CasinoPalette.java")
TEXTURE_SIZE = 16

# 三台机器：源码文件 + 输出名 + 中文名（分组前缀用）
MACHINES = [
    ("client/TNSlotMachineRenderer.java", "slot_machine", "slot"),
    ("client/TNRouletteRenderer.java", "roulette_table", "roulette"),
    ("client/TNGambleTableRenderer.java", "gamble_table", "gamble"),
]

# 严格数字字面量 —— 与 tools/casino-machines.mjs 用同一条规则 ✗
# （`[\\d.]+` 太贪心，会把好多个逗号隔开的数字吞进一次匹配里 ⇒ 方块数静默变少）
NUM = r"(-?\d+(?:\.\d+)?)F"
ADD_RE = re.compile(
    r"add\(out,\s*" + NUM + r",\s*" + NUM + r",\s*" + NUM
    + r",\s*" + NUM + r",\s*" + NUM + r",\s*" + NUM
    + r",\s*CasinoPalette\.(\w+)\);\s*(?://.*)?$",
    re.MULTILINE,
)


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def parse_palette():
    """从 CasinoPalette.java 读颜色 ⇒ 永不和代码脱节（不另抄一份）。"""
    text = read(PALETTE_JAVA)
    out = {}
    for m in re.finditer(r"public static final int (\w+)\s*=\s*(0x[0-9A-Fa-f]+);", text):
        out[m.group(1)] = int(m.group(2), 16)
    return out


def extract_build_parts(source, label):
    """取出 `List<Part> buildParts()` 的大括号内部（花括号配平，不用正则硬啃）。"""
    decl = source.find("List<Part> buildParts()")
    if decl < 0:
        raise SystemExit("buildParts() not found in %s" % label)
    open_at = source.find("{", decl)
    depth = 0
    for i in range(open_at, len(source)):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[open_at:i]
    raise SystemExit("unbalanced braces in buildParts() of %s" % label)


def parse_boxes(source, label):
    """⇒ [(from_xyz, to_xyz, 调色板名)]，单位=格。"""
    body = extract_build_parts(source, label)
    # 赌博桌的坐标是模型单位（1/16 格）⇒ 它自己在 add() 里除以 16。
    # 按"源码里出现了 / 16.0F"来判，别写死。
    divisor = 16.0 if re.search(r"/\s*16\.0F", source) else 1.0
    boxes = []
    for m in ADD_RE.finditer(body):
        vals = [float(m.group(i)) / divisor for i in range(1, 7)]
        boxes.append((vals[0:3], vals[3:6], m.group(7)))
    declared = len(re.findall(r"^\s*add\(out,", body, re.MULTILINE))
    if declared != len(boxes):
        raise SystemExit(
            "WARNING-grade problem in %s: %d add() lines but only %d parsed.\n"
            "A box is probably written as an expression, a bare int, or inside a loop.\n"
            "Rewrite it as literal NN.NNF values (see docs/casino/赌场机器_模型与动画调参.md)."
            % (label, declared, len(boxes))
        )
    return boxes, divisor


def make_placeholder_png(size):
    """一张 size×size 的中灰 PNG（纯占位；颜色来自顶点色，不来自它）。"""
    rows = []
    for _ in range(size):
        row = bytearray([0])                      # filter type 0
        for _ in range(size):
            row += bytes((128, 128, 128, 255))    # RGBA 中灰
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)   # 8-bit RGBA
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def bb_units(blocks):
    """格 -> Blockbench 单位（1 格 = 16）。"""
    return [round(v * 16.0, 4) for v in blocks]


def build_machine(rel, out_name, prefix, palette, png_b64):
    source = read(os.path.join(SRC_DIR, rel))
    boxes, divisor = parse_boxes(source, rel)

    # 每块方块一个颜色；同一调色板名归到一个分组（Outliner 里能整组点选）
    elements = []
    groups = {}
    max_x = max_y = max_z = 0.0
    for index, (frm, to, colour) in enumerate(boxes):
        rgb = palette.get(colour)
        if rgb is None:
            raise SystemExit("colour %s not found in CasinoPalette" % colour)
        max_x = max(max_x, to[0])
        max_y = max(max_y, to[1])
        max_z = max(max_z, to[2])
        element_faces = {
            face: {"uv": [0, 0, TEXTURE_SIZE, TEXTURE_SIZE], "texture": 0}
            for face in ("north", "east", "south", "west", "up", "down")
        }
        element = {
            "name": "%s.%02d.%s" % (prefix, index, colour),
            "box_uv": False,
            "render_order": "default",
            "rescale": False,
            "locked": False,
            "shade": True,
            "light_emission": 0,
            "export": True,
            "scope": 0,
            "allow_mirror_modeling": True,
            "from": bb_units(frm),
            "to": bb_units(to),
            "autouv": 0,
            # ★ 顶点色：Blockbench 里看到的颜色来自这里 ✓
            #   自绘管线本来就是"顶点色 x 逐面明暗"，没有 UV 采样 ⇒ 这样最贴近实机 ✓
            "faces": element_faces,
            "uuid": str(uuid.uuid4()),
        }
        # Blockbench 的 `color` 是**调色板索引**不是颜色值 ✗ ⇒ 用 faces 里的
        # per-face color 写真实值（java_block 格式支持 face.color，0..255 整数 RGB）
        r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
        for face in element["faces"].values():
            face["color"] = [r, g, b]
        elements.append(element)
        groups.setdefault(colour, []).append(element["uuid"])

    outliner = []
    for colour, uuids in groups.items():
        outliner.append({
            "name": colour,
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

    # visible_box 要装得下整台机器（Blockbench 用它画选框；2 格宽 / 4 格宽的桌子都得够大）
    visible = [max(1, int(round(max_x))), max(1, int(round(max_y))), max(1, int(round(max_z)))]

    project = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": out_name,
        "parent": "",
        "java_block_version": "1.9.0",
        "ambientocclusion": True,
        "front_gui_light": False,
        "visible_box": visible,
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": TEXTURE_SIZE, "height": TEXTURE_SIZE},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "path": "",
            "name": "_atlas_placeholder.png",
            "folder": "",
            "namespace": "",
            "id": "0",
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "uuid": str(uuid.uuid4()),
            "source": "data:image/png;base64," + png_b64,
        }],
    }

    path = os.path.join(OUT_DIR, out_name + ".bbmodel")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(project, fh, ensure_ascii=False, indent=1)

    print("%-22s %3d elements  %2d groups  visible_box=%s  kb=%d  (unit divisor %g)"
          % (out_name, len(elements), len(outliner), visible,
             os.path.getsize(path) // 1024, divisor))
    return len(elements)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    palette = parse_palette()
    png = make_placeholder_png(TEXTURE_SIZE)
    with open(os.path.join(OUT_DIR, "_atlas_placeholder.png"), "wb") as fh:
        fh.write(png)
    b64 = base64.b64encode(png).decode("ascii")

    print("CasinoPalette: %d colours" % len(palette))
    total = 0
    for rel, out_name, prefix in MACHINES:
        total += build_machine(rel, out_name, prefix, palette, b64)
    print("total %d elements -> %s" % (total, OUT_DIR))
    print()
    print("Blockbench: File > Open Model > 选一个 .bbmodel 就行（贴图已内嵌，不用另外找）")
    print("注意：看到的颜色来自 Cube 的 vertex colors，不是那张占位图。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
