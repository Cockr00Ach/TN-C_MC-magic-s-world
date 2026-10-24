"""生成火法 Boss 火柱的方块模型（JSON 运行资源 + Blockbench 工程 + 贴图）。

只写这三个文件，不碰任何已有模型/贴图：
  src/main/resources/assets/tnc/models/projectile/fire_pillar.json
  src/main/resources/assets/tnc/textures/spell_projectile/fire_pillar.png
  art/fireball/fire_pillar.bbmodel

坐标约定：模型空间 16 单位 = 1 格；底面 y=0 贴地，向上生长到 y=40（2.5 格）。
俯视占地约 0.5 格见方，向上收尖；运行时按缩放 1.0 使用即可。
"""

import base64
import io
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_REL = "src/main/resources/assets/tnc/textures/spell_projectile/fire_pillar.png"
MODEL_REL = "src/main/resources/assets/tnc/models/projectile/fire_pillar.json"
BB_REL = "art/fireball/fire_pillar.bbmodel"

RES = 64
SW = 16

# 8 个 16x16 色块：焦壳 3、熔岩 3、亮芯 1、底焰 1
SWATCHES = {
    "crust_dark": (SW * 0, SW * 0),
    "crust_mid": (SW * 1, SW * 0),
    "char": (SW * 2, SW * 0),
    "lava_mid": (SW * 3, SW * 0),
    "lava_hot": (SW * 0, SW * 1),
    "lava_deep": (SW * 1, SW * 1),
    "glow_top": (SW * 2, SW * 1),
    "base_flame": (SW * 3, SW * 1),
}

BASE_COLORS = {
    "crust_dark": (74, 42, 34),
    "crust_mid": (110, 62, 44),
    "char": (52, 30, 26),
    "lava_mid": (236, 122, 34),
    "lava_hot": (255, 186, 66),
    "lava_deep": (186, 68, 24),
    "glow_top": (255, 236, 170),
    "base_flame": (250, 150, 48),
}

# from, to, 每面的色块（按方位给不同明暗），整体由下往上：焦壳 → 熔岩 → 亮芯
ELEMENTS = [
    # 基座（贴地的一圈冷却岩壳）
    ((5.0, 0.0, 5.0), (11.0, 3.0, 11.0),
     dict(north="crust_dark", south="crust_dark", east="crust_mid", west="crust_mid",
          up="base_flame", down="crust_dark")),
    # 柱身：下粗上细，交替熔岩与焦壳
    ((4.0, 3.0, 4.0), (12.0, 9.0, 12.0),
     dict(north="lava_mid", south="lava_deep", east="lava_hot", west="lava_mid",
          up="lava_mid", down="base_flame")),
    ((4.6, 9.0, 4.6), (11.4, 14.0, 11.4),
     dict(north="crust_mid", south="crust_dark", east="lava_mid", west="crust_mid",
          up="lava_hot", down="lava_mid")),
    ((5.2, 14.0, 5.2), (10.8, 19.0, 10.8),
     dict(north="lava_hot", south="lava_mid", east="lava_deep", west="lava_hot",
          up="glow_top", down="lava_mid")),
    ((5.7, 19.0, 5.7), (10.3, 24.0, 10.3),
     dict(north="lava_mid", south="lava_hot", east="lava_mid", west="lava_deep",
          up="lava_hot", down="lava_deep")),
    ((6.2, 24.0, 6.2), (9.8, 28.0, 9.8),
     dict(north="lava_hot", south="glow_top", east="lava_hot", west="lava_mid",
          up="glow_top", down="lava_mid")),
    ((6.6, 28.0, 6.6), (9.4, 32.0, 9.4),
     dict(north="glow_top", south="lava_hot", east="glow_top", west="lava_hot",
          up="glow_top", down="lava_hot")),
    ((7.0, 32.0, 7.0), (9.0, 35.5, 9.0),
     dict(north="glow_top", south="glow_top", east="lava_hot", west="lava_hot",
          up="glow_top", down="glow_top")),
    # 顶端火舌（收尖）
    ((7.3, 35.5, 7.3), (8.7, 38.0, 8.7),
     dict(north="glow_top", south="glow_top", east="glow_top", west="glow_top",
          up="glow_top", down="glow_top")),
    ((7.6, 38.0, 7.6), (8.4, 40.0, 8.4),
     dict(north="glow_top", south="glow_top", east="glow_top", west="glow_top",
          up="glow_top", down="glow_top")),
    # 外壳上挂着的几块焦岩，制造"裂开的岩壳"
    ((2.6, 5.0, 6.0), (4.4, 11.0, 10.0),
     dict(north="crust_mid", south="crust_dark", east="crust_dark", west="crust_dark",
          up="crust_mid", down="char")),
    ((11.6, 8.0, 6.0), (13.4, 14.0, 10.0),
     dict(north="crust_dark", south="crust_mid", east="crust_mid", west="crust_dark",
          up="crust_dark", down="char")),
    ((6.0, 16.0, 2.8), (10.0, 22.0, 4.6),
     dict(north="char", south="crust_dark", east="crust_mid", west="crust_mid",
          up="crust_mid", down="char")),
    ((6.0, 25.0, 11.4), (10.0, 31.0, 13.2),
     dict(north="crust_mid", south="char", east="crust_mid", west="crust_dark",
          up="crust_dark", down="char")),
    # 底部外沿的熔岩溢流
    ((3.0, 0.0, 3.0), (5.2, 1.6, 5.2),
     dict(north="base_flame", south="lava_deep", east="base_flame", west="lava_deep",
          up="base_flame", down="lava_deep")),
    ((10.8, 0.0, 10.8), (13.0, 1.6, 13.0),
     dict(north="lava_deep", south="base_flame", east="lava_deep", west="base_flame",
          up="base_flame", down="lava_deep")),
]

# 只用合法角度：-45 / -22.5 / 0 / 22.5 / 45，其它角度会让整个模型烘焙失败
ROTATIONS = {
    10: dict(axis="y", angle=22.5, origin=(8.0, 8.0, 8.0)),
    11: dict(axis="y", angle=-22.5, origin=(8.0, 8.0, 8.0)),
    12: dict(axis="x", angle=22.5, origin=(8.0, 20.0, 4.0)),
    13: dict(axis="x", angle=-22.5, origin=(8.0, 28.0, 12.0)),
    14: dict(axis="y", angle=45.0, origin=(4.0, 1.0, 4.0)),
    15: dict(axis="y", angle=-45.0, origin=(12.0, 1.0, 12.0)),
}

FACES = ["north", "east", "south", "west", "up", "down"]


def make_texture(path):
    random.seed(20261025)
    img = Image.new("RGBA", (RES, RES), (0, 0, 0, 0))
    px = img.load()
    for name, (ox, oy) in SWATCHES.items():
        base = BASE_COLORS[name]
        for y in range(SW):
            for x in range(SW):
                n = random.randint(-16, 16)
                if random.random() < 0.05:
                    n += 30
                if random.random() < 0.05:
                    n -= 24
                # 竖向的熔岩流纹：同一列亮度接近
                n += int(10 * ((x * 7 + y * 3) % 5 - 2) / 2.0)
                r = max(0, min(255, base[0] + n))
                g = max(0, min(255, base[1] + n))
                b = max(0, min(255, base[2] + n))
                px[ox + x, oy + y] = (r, g, b, 255)
    draw = ImageDraw.Draw(img)
    for name, (ox, oy) in SWATCHES.items():
        base = BASE_COLORS[name]
        edge = (max(0, base[0] - 30), max(0, base[1] - 30), max(0, base[2] - 30), 255)
        draw.rectangle([ox, oy, ox + SW - 1, oy + SW - 1], outline=edge)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return img


def uv_for(name, axis_size):
    """uv 空间固定 0..16，贴图 64x64 ⇒ 像素坐标除以 4。"""
    ox, oy = SWATCHES[name]
    k = RES / 16.0
    w = max(0.5, min(4.0, axis_size * 0.5))
    x0, y0 = ox / k, oy / k
    return [round(x0, 3), round(y0, 3), round(min(16.0, x0 + w), 3), round(min(16.0, y0 + w), 3)]


def build_elements():
    elements = []
    for index, (frm, to, faces) in enumerate(ELEMENTS):
        size_x = to[0] - frm[0]
        size_y = to[1] - frm[1]
        size_z = to[2] - frm[2]
        face_map = {}
        for face in FACES:
            if face in ("north", "south"):
                w = size_x
            elif face in ("east", "west"):
                w = size_z
            else:
                w = size_x
            face_map[face] = {"uv": uv_for(faces[face], w), "texture": "#0"}
        element = {
            "from": [round(v, 3) for v in frm],
            "to": [round(v, 3) for v in to],
            "faces": face_map,
        }
        rot = ROTATIONS.get(index)
        if rot:
            element["rotation"] = {
                "origin": [round(v, 3) for v in rot["origin"]],
                "axis": rot["axis"],
                "angle": rot["angle"],
                "rescale": False,
            }
        elements.append(element)
    return elements


def write_model(elements):
    model = {
        "credit": "TN-C volcanic fire mage fire pillar",
        "textures": {"0": "tnc:spell_projectile/fire_pillar", "particle": "tnc:spell_projectile/fire_pillar"},
        "elements": elements,
        "display": {},
    }
    path = os.path.join(ROOT, MODEL_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(model, handle, ensure_ascii=False, separators=(",", ":"))
    return path


def write_bbmodel(elements, texture_img):
    buffer = io.BytesIO()
    texture_img.save(buffer, format="PNG")
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")

    bb_elements = []
    for index, element in enumerate(elements):
        faces = {}
        for face in FACES:
            uv = element["faces"][face]["uv"]
            faces[face] = {"uv": uv, "texture": 0}
        bb = {
            "from": element["from"],
            "to": element["to"],
            "faces": faces,
            "name": "part_%02d" % index,
            "uuid": "00000000-0000-4000-9000-%012d" % index,
        }
        if "rotation" in element:
            rot = element["rotation"]
            bb["rotation"] = [rot["origin"][0], rot["origin"][1], rot["origin"][2],
                              rot["angle"], rot["axis"].upper()]
        bb_elements.append(bb)

    children = [item["uuid"] for item in bb_elements]
    project = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "fire_pillar",
        "resolution": {"width": RES, "height": RES},
        "elements": bb_elements,
        "outliner": [{"name": "fire_pillar", "origin": [8, 0, 8], "uuid": "00000000-0000-4000-9000-0000000000ff",
                      "children": children}],
        "textures": [{"name": "fire_pillar.png", "id": 0, "path": "", "source": "data:image/png;base64," + encoded}],
    }
    path = os.path.join(ROOT, BB_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(project, handle, ensure_ascii=False, separators=(",", ":"))
    return path


def main():
    tex_path = os.path.join(ROOT, TEX_REL)
    texture = make_texture(tex_path)
    elements = build_elements()
    print("wrote", write_model(elements))
    print("wrote", tex_path)
    print("wrote", write_bbmodel(elements, texture))
    print("elements", len(elements))


if __name__ == "__main__":
    main()
