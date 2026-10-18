"""Offline orthographic preview of the shipped Bedrock geometry (not an in-game screenshot)."""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def rotation(degrees):
    x, y, z = np.radians(degrees)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def render_preview(geo_path, texture_path, output):
    geometry = json.loads(Path(geo_path).read_text(encoding="utf-8"))["minecraft:geometry"][0]
    texture = Image.open(texture_path).convert("RGB")
    bones = {b["name"]: b for b in geometry["bones"]}
    transforms = {}

    def transform(name):
        if name in transforms:
            return transforms[name]
        b = bones[name]
        r = rotation(b.get("rotation", [0, 0, 0]))
        p = np.array(b["pivot"], dtype=float)
        t = p - r @ p
        if b.get("parent"):
            pr, pt = transform(b["parent"])
            r, t = pr @ r, pr @ t + pt
        transforms[name] = r, t
        return r, t

    # Consistent vertex indices for every face; painter's ordering handles hidden faces.
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1),
             (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    meshes = []
    for name, b in bones.items():
        r, t = transform(name)
        for cube in b.get("cubes", []):
            origin = np.array(cube["origin"], dtype=float)
            size = np.array(cube["size"], dtype=float)
            vertices = np.array([origin + size*np.array([x, y, z])
                                 for x in (0, 1) for y in (0, 1) for z in (0, 1)])
            vertices = (r @ vertices.T).T + t
            u, v = cube["uv"]
            color = texture.getpixel((int(u+size[2]+size[0]/2), int(v+size[2]+size[1]/2)))
            meshes.append((vertices, color))

    canvas = Image.new("RGB", (1600, 1000), (8, 19, 29))
    draw = ImageDraw.Draw(canvas)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    title = ImageFont.truetype(str(font_path), 32) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()
    draw.text((45, 25), "圣渊利维坦 · 实际几何离线预览", font=title, fill=(231, 204, 131))
    draw.text((46, 75), "深海鳞甲 / 蓝金圣纹 / 分节巨尾 / 凶兽獠牙    ·    非游戏截图", font=small, fill=(114, 188, 208))

    def panel(rect, yaw, pitch, label):
        x0, y0, width, height = rect
        camera = rotation([pitch, yaw, 0])
        projected = [(vertices @ camera.T, color) for vertices, color in meshes]
        all_points = np.concatenate([v for v, _ in projected])
        mn, mx = all_points[:, :2].min(axis=0), all_points[:, :2].max(axis=0)
        scale = min((width-80)/(mx[0]-mn[0]), (height-80)/(mx[1]-mn[1]))
        center = (mn+mx)/2
        polygons = []
        for vertices, color in projected:
            for indices in faces:
                face = vertices[list(indices)]
                normal = np.cross(face[1]-face[0], face[2]-face[0])
                normal /= max(1e-9, np.linalg.norm(normal))
                shade = 0.62 + 0.38*abs(float(normal @ np.array([0.3, 0.8, -0.5])))
                fill = tuple(min(255, int(c*shade+8)) for c in color)
                points = [(x0+width/2+(p[0]-center[0])*scale,
                           y0+height/2-(p[1]-center[1])*scale) for p in face]
                polygons.append((face[:, 2].mean(), points, fill))
        for _, points, fill in sorted(polygons, key=lambda q: q[0], reverse=True):
            draw.polygon(points, fill=fill)
            draw.line(points+[points[0]], fill=tuple(int(c*0.55) for c in fill), width=1)
        draw.text((x0+25, y0+height-35), label, font=small, fill=(184, 209, 216))

    panel((10, 110, 1580, 540), 57, 18, "斜前方 · 骨骼静止姿态")
    panel((10, 650, 1000, 340), 90, 0, "侧视 · 背脊与尾部轮廓")
    panel((1010, 650, 580, 340), 0, 12, "正面 · 双角与颚部")
    canvas.save(output)


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    render_preview(root/"src/main/resources/assets/tnc/geo/entity/water_leviathan.geo.json",
                   root/"src/main/resources/assets/tnc/textures/entity/water_leviathan_bedrock.png",
                   root/"docs/previews/water_leviathan_preview.png")
