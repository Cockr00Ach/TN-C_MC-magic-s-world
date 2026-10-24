"""生成火法 Boss 火球的方块模型（JSON 运行资源 + Blockbench 工程 + 贴图）。

只写这三个文件，不碰任何已有模型/贴图：
  src/main/resources/assets/tnc/models/projectile/fireball_core.json
  src/main/resources/assets/tnc/textures/spell_projectile/fireball_core.png
  art/fireball/fireball_core.bbmodel

坐标约定：模型空间 16 单位 = 1 格，中心 (8,8,8)；前进方向 -Z（北），尾迹朝 +Z。
渲染缩放 2.0 时，主体外径约 1.75 格。
"""

import base64
import io
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_REL = "src/main/resources/assets/tnc/textures/spell_projectile/fireball_core.png"
MODEL_REL = "src/main/resources/assets/tnc/models/projectile/fireball_core.json"
BB_REL = "art/fireball/fireball_core.bbmodel"

RES = 64
SW = 16  # 每个色块的边长

# 8 个 16x16 色块：核心 2、外壳 3、暗红 1、尾迹 2
SWATCHES = {
    "core_white": (SW * 0, SW * 0),
    "core_gold": (SW * 1, SW * 0),
    "shell_light": (SW * 2, SW * 0),
    "shell_orange": (SW * 3, SW * 0),
    "shell_deep": (SW * 0, SW * 1),
    "dark_red": (SW * 1, SW * 1),
    "tail_orange": (SW * 2, SW * 1),
    "tail_dark": (SW * 3, SW * 1),
}

BASE_COLORS = {
    "core_white": (255, 252, 238),
    "core_gold": (255, 234, 152),
    "shell_light": (255, 188, 74),
    "shell_orange": (250, 140, 34),
    "shell_deep": (214, 84, 22),
    "dark_red": (162, 50, 20),
    "tail_orange": (240, 112, 30),
    "tail_dark": (138, 42, 18),
}

# from, to, 每个面的色块（north/south/east/west/up/down）
ELEMENTS = [
    # 明亮方块核心：向前（-Z）凸出，正面直接可见，不被外壳完全包住
    ((5.5, 5.5, 2.6), (10.5, 10.5, 9.5),
     dict(north="core_white", south="core_gold", east="core_white", west="core_gold",
          up="core_white", down="core_gold")),
    # 中层燃烧块：左右前后都不对称，且把正面中间让给核心
    ((2.4, 4.2, 3.6), (6.6, 8.4, 8.0),
     dict(north="shell_orange", south="shell_deep", east="shell_light", west="shell_deep",
          up="shell_light", down="shell_deep")),
    ((9.4, 4.6, 3.8), (13.6, 8.8, 8.2),
     dict(north="shell_light", south="shell_orange", east="shell_deep", west="shell_light",
          up="shell_orange", down="shell_deep")),
    ((5.0, 8.8, 4.4), (11.0, 13.4, 8.8),
     dict(north="shell_light", south="shell_orange", east="shell_light", west="shell_orange",
          up="shell_light", down="shell_orange")),
    ((5.2, 2.0, 4.6), (10.8, 6.0, 9.0),
     dict(north="shell_orange", south="shell_deep", east="shell_orange", west="shell_deep",
          up="shell_deep", down="shell_deep")),
    # 背部大块 + 尾迹起点
    ((7.0, 7.0, 8.6), (13.0, 12.0, 12.4),
     dict(north="shell_orange", south="shell_deep", east="shell_orange", west="shell_light",
          up="shell_deep", down="shell_orange")),
    # 外缘暗红块
    ((1.0, 5.4, 5.4), (3.4, 8.2, 8.6),
     dict(north="dark_red", south="dark_red", east="shell_deep", west="dark_red",
          up="shell_deep", down="dark_red")),
    ((13.2, 8.6, 4.8), (15.4, 11.2, 8.0),
     dict(north="shell_deep", south="dark_red", east="dark_red", west="dark_red",
          up="dark_red", down="dark_red")),
    # 少量碎块，离开主体一点点，形成"碎屑"
    ((1.4, 1.6, 1.8), (3.2, 3.4, 3.6),
     dict(north="dark_red", south="dark_red", east="shell_deep", west="dark_red",
          up="shell_deep", down="dark_red")),
    ((12.6, 12.2, 8.4), (14.2, 13.8, 10.0),
     dict(north="shell_deep", south="dark_red", east="dark_red", west="shell_deep",
          up="dark_red", down="dark_red")),
    ((4.2, 12.6, 3.2), (5.8, 14.2, 4.8),
     dict(north="dark_red", south="shell_deep", east="dark_red", west="shell_deep",
          up="shell_deep", down="dark_red")),
    # 尾迹：向后（+Z）收缩，明确前进方向
    ((5.4, 5.4, 9.4), (10.6, 10.6, 11.8),
     dict(north="tail_orange", south="tail_orange", east="tail_dark", west="tail_dark",
          up="tail_orange", down="tail_dark")),
    ((6.2, 6.2, 11.6), (9.8, 9.8, 13.6),
     dict(north="tail_dark", south="tail_orange", east="tail_dark", west="tail_orange",
          up="tail_dark", down="tail_dark")),
    ((6.9, 6.9, 13.4), (9.1, 9.1, 14.8),
     dict(north="tail_dark", south="tail_dark", east="tail_dark", west="tail_dark",
          up="tail_dark", down="tail_dark")),
]
# 少量元素做轴向旋转，避免"整块立方体堆"的呆板感
# 注意：原版方块模型只接受 -45 / -22.5 / 0 / 22.5 / 45 这五个角度，
# 其它值会让整个模型烘焙失败（游戏里就是紫黑方块），所以这里只用合法角度。
ROTATIONS = {
    2: dict(axis="y", angle=22.5, origin=(8.0, 8.0, 8.0)),
    4: dict(axis="z", angle=22.5, origin=(8.0, 8.0, 8.0)),
    5: dict(axis="x", angle=-22.5, origin=(8.0, 8.0, 8.0)),
    7: dict(axis="y", angle=-22.5, origin=(8.0, 8.0, 8.0)),
    12: dict(axis="x", angle=22.5, origin=(8.0, 8.0, 9.0)),
}

FACES = ["north", "east", "south", "west", "up", "down"]


def make_texture(path):
    """64x64 像素风贴图：8 个色块，各自带噪点与亮点，近白黄/橙/暗红分层。"""
    random.seed(20261024)
    img = Image.new("RGBA", (RES, RES), (0, 0, 0, 0))
    px = img.load()
    for name, (ox, oy) in SWATCHES.items():
        base = BASE_COLORS[name]
        for y in range(SW):
            for x in range(SW):
                n = random.randint(-14, 14)
                # 少量更亮的燃烧颗粒
                if random.random() < 0.06:
                    n += 26
                if random.random() < 0.04:
                    n -= 22
                r = max(0, min(255, base[0] + n))
                g = max(0, min(255, base[1] + n))
                b = max(0, min(255, base[2] + n))
                px[ox + x, oy + y] = (r, g, b, 255)
    # 每个色块描一圈更暗的边，贴到方块上像"烧焦接缝"
    draw = ImageDraw.Draw(img)
    for name, (ox, oy) in SWATCHES.items():
        base = BASE_COLORS[name]
        edge = (max(0, base[0] - 28), max(0, base[1] - 28), max(0, base[2] - 28), 255)
        draw.rectangle([ox, oy, ox + SW - 1, oy + SW - 1], outline=edge)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return img


def uv_for(name, axis_size):
    """返回该面在贴图上的 uv 矩形（0..16 空间，原版方块模型的规定）。

    贴图是 64x64，而 uv 空间固定 0..16 ⇒ 像素坐标要除以 4（RES / 16）。
    超出 0..16 的 uv 会采样到隔壁贴图，模型看着"花掉"。
    """
    ox, oy = SWATCHES[name]
    k = RES / 16.0
    w = max(0.5, min(4.0, axis_size))
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
        "credit": "TN-C volcanic fire mage fireball",
        "textures": {"0": "tnc:spell_projectile/fireball_core", "particle": "tnc:spell_projectile/fireball_core"},
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
            "uuid": "00000000-0000-4000-8000-%012d" % index,
        }
        if "rotation" in element:
            rot = element["rotation"]
            bb["rotation"] = [rot["origin"][0], rot["origin"][1], rot["origin"][2],
                              rot["angle"], rot["axis"].upper()]
        bb_elements.append(bb)

    children = [item["uuid"] for item in bb_elements]
    project = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "fireball_core",
        "resolution": {"width": RES, "height": RES},
        "elements": bb_elements,
        "outliner": [{"name": "fireball", "origin": [8, 8, 8], "uuid": "00000000-0000-4000-8000-0000000000ff",
                      "children": children}],
        "textures": [{"name": "fireball_core.png", "id": 0, "path": "", "source": "data:image/png;base64," + encoded}],
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
