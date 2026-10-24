#!/usr/bin/env python3
"""把作者手改的 fireball_core.bbmodel 转成 Minecraft 方块模型 + 贴图。

只写这两个文件，不动任何其它资源：
  src/main/resources/assets/tnc/models/projectile/fireball_core.json
  src/main/resources/assets/tnc/textures/block/fireball_core.png

注意：
- Blockbench 的 java_block 元素旋转只支持单轴 22.5 的倍数，作者这次保存的 8° 微倾
  在方块模型格式里无法表达，这里按无旋转导入（轮廓基本一致，误差在 1 像素级）。
- uv 已经是 0..16 空间（64px 贴图 / 4），不再换算。
"""
import base64
import io
import json
import os

SRC = r"C:\Users\邵子豪\Desktop\火山巨龟修改\我的修改\fireball_core.bbmodel"
ROOT = r"C:\Users\邵子豪\Documents\deepseek-harness\default-workspace\TN-C_MC-magic-s-world"
MODEL = os.path.join(ROOT, "src/main/resources/assets/tnc/models/projectile/fireball_core.json")
TEX = os.path.join(ROOT, "src/main/resources/assets/tnc/textures/block/fireball_core.png")

FACES = ("north", "east", "south", "west", "up", "down")


def main():
    data = json.load(io.open(SRC, encoding="utf-8"))
    elements = []
    for el in data["elements"]:
        if el.get("type") != "cube":
            continue
        faces = {}
        for name in FACES:
            face = el["faces"].get(name)
            if not face:
                continue
            uv = [round(float(v), 4) for v in face["uv"]]
            faces[name] = {"uv": uv, "texture": "#0"}
        elements.append({
            "from": [round(float(v), 4) for v in el["from"]],
            "to": [round(float(v), 4) for v in el["to"]],
            "faces": faces,
        })

    model = {
        "credit": "TN-C volcanic fire mage fireball core (edited by author)",
        "textures": {
            "0": "tnc:block/fireball_core",
            "particle": "tnc:block/fireball_core",
        },
        "elements": elements,
    }
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    with io.open(MODEL, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, ensure_ascii=False, separators=(",", ":"))

    src = data["textures"][0]["source"]
    if "," in src:
        src = src.split(",", 1)[1]
    with open(TEX, "wb") as f:
        f.write(base64.b64decode(src))

    print("model elements:", len(elements))
    print("texture bytes:", os.path.getsize(TEX))
    print("model bytes:", os.path.getsize(MODEL))


if __name__ == "__main__":
    main()
