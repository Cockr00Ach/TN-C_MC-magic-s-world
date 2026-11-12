# -*- coding: utf-8 -*-
"""罗马圣柱（水牢的隔板）—— 四段式方块模型 + 蓝色大理石贴图。

作者 2026-11-10：
    第一版「水牢的隔板再改改吧，我想做成一个个罗马圣柱那样」
    第二版「圣柱有点丑，不够精致，尽管做精致」
    第三版「你搜搜罗马圣柱的图片，借鉴一下」

我这台机器上不了网（web_search / web_fetch 都失败 ✗），所以**按古典柱式的标准分层**来做 ——
科林斯/复合柱式的解剖（维特鲁威、维尼奥拉那一套）：

    柱础（阿提卡式） = 方座 plinth → 下圆盘 torus → 凹弧 scotia → 上圆盘 torus → 小圆环 annulet
    柱身           = 带**凹槽 flutes** 的柱身，**收分 entasis**（上细下粗），顶端一圈小圆环 astragal
    柱头（科林斯） = 收颈 → 钟形 kalathos（三层张开）→ **茛苕叶 acanthus**（两排）→
                     **四角涡卷 volutes** → **方板 abacus**（上缘外挑）→ 方板每面中心一朵 **rosette**

为什么拆四个模型：
    - 柱础 / 柱身 / 柱头必须分开 ✗：水牢高度是变的（7 格 → 24 格），整根拉 y 会把柱础柱头拉成一坨 ✗；
    - **柱头浮雕（叶 / 涡卷 / 花）单独一个模型** ✓：这是"精致"的来源，也是最贵的一块 ✗ ——
      远处（>36 格）不画它，近处才画 ✓（`romanColumns(..., detailed)`）。
"""
import json
import math
import os

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(REPO, "src/main/resources/assets/tnc/models/water")
TEX_DIR = os.path.join(REPO, "src/main/resources/assets/tnc/textures/block")

TEXTURE = "tnc:block/roman_column_blue"

# 贴图分区（64×64，每格 16×16 ✓）
UV = {
    "flute": [0, 0, 16, 16],        # 柱身凹槽（8 条细密竖纹）
    "stone": [16, 0, 32, 16],       # 浅色大理石（斜向矿脉）
    "gold": [32, 0, 48, 16],        # 金色饰带（上下暗、中间亮）
    "deep": [0, 16, 16, 32],        # 深色石（方格暗纹）
    "band": [16, 16, 32, 32],       # 横向凹线（线脚）
    "scroll": [32, 16, 48, 32],     # 水波纹（涡卷 / 柱头装饰）
    "bright_gold": [0, 32, 16, 48],  # 亮金（高光边）
    "pearl": [16, 32, 32, 48],      # 白高光石
    "leaf": [32, 32, 48, 48],       # 茛苕叶（带叶脉 ✓）
}


def element(x0, y0, z0, x1, y1, z1, tex, uv=None, rotation=None):
    uv = uv or UV[tex]
    faces = {f: {"texture": "#" + tex, "uv": list(uv)}
             for f in ("north", "south", "east", "west", "up", "down")}
    part = {
        "from": [round(x0, 4), round(y0, 4), round(z0, 4)],
        "to": [round(x1, 4), round(y1, 4), round(z1, 4)],
        "shade": False,
        "faces": faces,
    }
    if rotation:
        part["rotation"] = rotation
    return part


def ring(half, y0, y1, tex, uv=None):
    """以柱子轴线 (8,·,8) 为中心的一圈方形截面 ✓。"""
    return element(8 - half, y0, 8 - half, 8 + half, y1, 8 + half, tex, uv)


def model(name, parts):
    data = {
        "credit": "TN-C roman water column; tools/gen_water_column_models.py",
        "ambientocclusion": False,
        "textures": {k: TEXTURE for k in
                     ("flute", "stone", "gold", "deep", "band", "scroll", "bright_gold", "pearl", "leaf", "particle")},
        "elements": parts,
    }
    os.makedirs(MODELS, exist_ok=True)
    path = os.path.join(MODELS, name + ".json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", os.path.basename(path), len(parts), "elements")


# ----------------------------------------------------------------------
#  柱础（阿提卡式）
# ----------------------------------------------------------------------
def build_base():
    return [
        # 方座 + 一块 45° 的（⇒ 轮廓不是死板的方 ✗，读作八角 ✓）
        ring(7.5, 0.0, 1.2, "deep"),
        element(2.2, 0.0, 2.2, 13.8, 1.2, 13.8, "deep", UV["band"],
                {"origin": [8, 0.6, 8], "axis": "y", "angle": 45}),
        ring(7.2, 1.2, 1.6, "band"),                            # 方座上缘斜边
        ring(6.5, 1.6, 2.6, "gold"),                            # 下圆盘 torus
        element(2.0, 1.7, 2.0, 14.0, 2.5, 14.0, "gold", UV["gold"],
                {"origin": [8, 2.1, 8], "axis": "y", "angle": 45}),
        ring(6.6, 2.6, 2.9, "bright_gold", UV["bright_gold"]),  # 圆盘上的一道亮金线 ✓
        ring(5.6, 2.9, 3.8, "deep", UV["band"]),                # 凹弧 scotia
        ring(6.1, 3.8, 4.7, "gold"),                            # 上圆盘 torus
        ring(5.1, 4.7, 5.2, "stone"),                           # 小圆环 annulet
        ring(4.4, 5.2, 5.7, "flute"),                           # 收颈（柱身从这里长出来 ✓）
        ring(4.9, 5.7, 6.2, "gold", UV["bright_gold"]),         # 方领
    ]


# ----------------------------------------------------------------------
#  柱身：8×8 芯 ＋ 四角棱条 ＋ 上下金线（收分 entasis 在渲染时做 ✓）
# ----------------------------------------------------------------------
def build_shaft():
    parts = [element(4, 0, 4, 12, 16, 12, "flute")]
    # 四角棱条：侧面看有一道亮棱 ⇒ 柱身既不圆也不方 ✓（古典柱的那道"角"✓）
    for x0, z0 in ((3.7, 3.7), (11.0, 3.7), (3.7, 11.0), (11.0, 11.0)):
        parts.append(element(x0, 0, z0, x0 + 1.3, 16, z0 + 1.3, "stone"))
    parts.append(ring(4.2, 0.0, 0.45, "gold", UV["bright_gold"]))
    parts.append(ring(4.2, 15.55, 16.0, "gold", UV["bright_gold"]))
    return parts


# ----------------------------------------------------------------------
#  柱头本体（不带浮雕）：收颈 → 钟形三层 → 方板
# ----------------------------------------------------------------------
def build_capital():
    return [
        ring(4.7, 0.0, 0.5, "band"),                       # 小圆环 astragal
        ring(5.0, 0.5, 1.0, "stone"),                      # 收颈
        ring(5.5, 1.0, 1.9, "gold"),                       # 钟形 kalathos 第一层
        ring(6.2, 1.9, 2.8, "stone"),                      # 第二层
        ring(6.9, 2.8, 3.6, "gold", UV["scroll"]),         # 第三层（水波纹金 ✓）
        element(1.2, 3.6, 1.2, 14.8, 4.5, 14.8, "stone"),  # 方板下缘
        element(1.0, 4.5, 1.0, 15.0, 5.4, 15.0, "gold"),   # 方板顶面
        element(1.6, 5.4, 1.6, 14.4, 5.75, 14.4, "pearl", UV["pearl"]),
    ]


# ----------------------------------------------------------------------
#  柱头浮雕（近处才画 ✓）：两排茛苕叶 ＋ 四角涡卷 ＋ 方板每面的玫瑰花
# ----------------------------------------------------------------------
def build_ornament():
    parts = []
    # ① 八个方向各一片茛苕叶：**露在钟形外面**（半径 6.3 ✓）、向外倾 22.5° ✓
    for i in range(8):
        angle = i * math.pi / 4
        dx, dz = math.sin(angle), math.cos(angle)
        cx, cz = 8 + dx * 6.3, 8 + dz * 6.3
        # 叶片朝外倒：绕"水平且与朝向垂直"的那根轴转 −22.5° ✓
        # （模型只支持单轴旋转 ✗ ⇒ 取朝向里分量大的那根轴 ✓）
        if abs(dz) >= abs(dx):
            axis, tilt = "x", -22.5 * (1 if dz > 0 else -1)
        else:
            axis, tilt = "z", -22.5 * (1 if dx > 0 else -1)
        if dz < 0 and abs(dz) >= abs(dx):
            tilt = 22.5
        if dx < 0 and abs(dx) > abs(dz):
            tilt = 22.5
        parts.append(element(cx - 1.2, 1.0, cz - 1.2, cx + 1.2, 4.2, cz + 1.2, "leaf",
                             UV["leaf"], {"origin": [round(cx, 4), 1.1, round(cz, 4)],
                                          "axis": axis, "angle": tilt}))
    # ② 四角涡卷：外挑一大块 + 内侧一小块错开 ⇒ 读得出"卷"✓
    for sx, sz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        cx, cz = 8 + sx * 6.0, 8 + sz * 6.0
        parts.append(element(cx - 1.7, 2.7, cz - 1.7, cx + 1.7, 4.6, cz + 1.7, "gold", UV["scroll"]))
        parts.append(element(cx - 1.1, 3.9, cz - 1.1, cx + 1.1, 5.2, cz + 1.1, "bright_gold", UV["bright_gold"]))
    # ③ 方板每面中心一朵玫瑰花（四个面 ✓）
    for i in range(4):
        angle = i * math.pi / 2
        cx, cz = 8 + math.sin(angle) * 7.45, 8 + math.cos(angle) * 7.45
        parts.append(element(cx - 1.0, 3.8, cz - 1.0, cx + 1.0, 4.9, cz + 1.0, "pearl", UV["pearl"]))
    return parts


# ----------------------------------------------------------------------
#  贴图：64×64，九格
# ----------------------------------------------------------------------
def build_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def cell16(u, v):
        for y in range(v, v + 16):
            for x in range(u, u + 16):
                yield x, y

    # ① 柱身凹槽（0,0）：16px 里 8 条纹 ✓
    for x, y in cell16(0, 0):
        stripe = (x // 2) % 4
        base = [(20, 66, 126), (56, 128, 206), (104, 182, 244), (38, 98, 168)][stripe]
        fade = 1.0 - 0.18 * abs(y - 7.5) / 7.5
        px[x, y] = (int(base[0] * fade), int(base[1] * fade), int(base[2] * fade), 255)

    # ② 浅色大理石（16,0）：斜向矿脉 ✓
    for x, y in cell16(16, 0):
        base = 132 + int(22 * math.sin((x + y) * 0.5))
        rgb = [base - 70, base - 20, base + 24]
        if (x * 3 + y * 5) % 23 == 0:
            rgb = [min(255, rgb[0] + 54), min(255, rgb[1] + 44), min(255, rgb[2] + 20)]
        px[x, y] = (rgb[0], rgb[1], rgb[2], 255)

    # ③ 金色饰带（32,0）
    for x, y in cell16(32, 0):
        t = abs(y - 7.5) / 7.5
        r, g, b = int(208 - 84 * t), int(170 - 70 * t), int(86 - 40 * t)
        if y in (7, 8):
            r, g, b = 246, 226, 150
        px[x, y] = (r, g, b, 255)

    # ④ 深色石（0,16）：方格暗纹
    for x, y in cell16(0, 16):
        shade = 8 if (x // 4 + y // 4) % 2 else 0
        px[x, y] = (14 + shade, 38 + shade, 82 + shade * 2, 255)

    # ⑤ 横向凹线（16,16）
    for x, y in cell16(16, 16):
        dark = y % 5 in (0, 1)
        px[x, y] = (24, 62, 112, 255) if dark else (84, 158, 228, 255)

    # ⑥ 水波纹（32,16）
    for x, y in cell16(32, 16):
        wave = math.sin(x * 0.9) * 3.0 + 8.0
        on = abs(y - wave) < 1.2
        px[x, y] = (236, 244, 255, 255) if on else (40, 104, 176, 255)

    # ⑦ 亮金（0,32）
    for x, y in cell16(0, 32):
        t = abs(y - 7.5) / 7.5
        px[x, y] = (int(255 - 30 * t), int(240 - 44 * t), int(186 - 70 * t), 255)

    # ⑧ 白高光石（16,32）
    for x, y in cell16(16, 32):
        base = 226 + int(18 * math.sin(x * 0.7 + y * 0.4))
        px[x, y] = (base - 34, base - 10, base, 255)

    # ⑨ 茛苕叶（32,32）：中肋 + 两侧叶脉 ✓
    for x, y in cell16(32, 32):
        leaf = (46, 128, 150) if x % 8 else (120, 210, 220)
        if abs(x - 7.5) < 1.2:
            leaf = (196, 240, 250)          # 中肋
        vein = (y % 6 == 0) and abs(x - 7.5) < 5
        if vein:
            leaf = (28, 92, 116)
        px[x, y] = (leaf[0], leaf[1], leaf[2], 255)

    os.makedirs(TEX_DIR, exist_ok=True)
    path = os.path.join(TEX_DIR, "roman_column_blue.png")
    img.save(path)
    print("wrote", os.path.basename(path), img.size)


def main():
    build_texture()
    model("roman_column_base", build_base())
    model("roman_column_shaft", build_shaft())
    model("roman_column_capital", build_capital())
    model("roman_column_ornament", build_ornament())


if __name__ == "__main__":
    main()
