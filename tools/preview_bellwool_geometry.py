"""Offline orthographic preview of the actual Java mesh and pixel texture.

Reads createBodyLayer instead of making a separate concept animal. This is
asset QA only; it cannot prove Minecraft animation/render integration.
"""
from pathlib import Path
import math
import re
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/tnc/tnc/life/fauna/client/BellwoolSheepModel.java'
TEXTURE = ROOT / 'src/main/resources/assets/tnc/textures/entity/bellwool_sheep.png'
OUT = ROOT / 'work/previews/bellwool-actual-geometry.png'


def floats(text):
    return [float(x.rstrip('Ff')) for x in text.split(',')]


def mesh():
    source = JAVA.read_text(encoding='utf-8').split('createBodyLayer() {', 1)[1].split('return LayerDefinition', 1)[0]
    parts = {'root': ((0, 0, 0), [])}
    builders = {}
    for statement in source.split(';'):
        builder = re.search(r'CubeListBuilder (\w+) = (.*)', statement, re.S)
        if builder:
            builders[builder[1]] = builder[2]
        m = re.search(r'(\w+)\.addOrReplaceChild\("([^"]+)",\s*(.*),\s*PartPose\.(ZERO|offset\([^)]*\))\)', statement, re.S)
        if not m:
            continue
        parent, name, chain, pose = m.groups()
        offset = (0, 0, 0) if pose == 'ZERO' else tuple(floats(pose[7:-1]))
        origin = tuple(a+b for a, b in zip(parts[parent][0], offset))
        chain = builders.get(chain.strip(), chain)
        boxes = []
        uv = None
        for token in re.finditer(r'\.(texOffs|addBox)\(([^)]*)\)', chain):
            values = floats(token[2])
            if token[1] == 'texOffs':
                uv = values
            else:
                boxes.append((values, uv))
        parts[name] = (origin, boxes)
    assert len(parts) == 10 and sum(len(p[1]) for p in parts.values()) == 19, parts
    return parts


def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', size)


def render(parts, sheared, yaw):
    tile = Image.new('RGB', (560, 430), '#e4e3d7')
    tex = Image.open(TEXTURE).convert('RGB')
    cos, sin = math.cos(yaw), math.sin(yaw)
    def project(p):
        x, y, z = p
        rx, rz = cos*x-sin*z, sin*x+cos*z
        return (280+rx*11, 336+(y-24)*10+rz*4.5)
    faces = []
    for name, (origin, boxes) in parts.items():
        if sheared and name == 'fleece':
            continue
        for values, uv in boxes:
            x, y, z, w, h, d = values
            x, y, z = x+origin[0], y+origin[1], z+origin[2]
            u, v = uv
            assert u+2*d+2*w <= 64.01 and v+d+h <= 64.01, (name, values, uv)
            definitions = [
                ([(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z)], (u+d,v+d,u+d+w,v+d+h), .91),
                ([(x+w,y,z),(x+w,y,z+d),(x+w,y+h,z+d),(x+w,y+h,z)], (u+d+w,v+d,u+2*d+w,v+d+h), .72),
                ([(x,y,z+d),(x,y,z),(x,y+h,z),(x,y+h,z+d)], (u,v+d,u+d,v+d+h), .72),
                ([(x+w,y,z+d),(x,y,z+d),(x,y+h,z+d),(x+w,y+h,z+d)], (u+2*d+w,v+d,u+2*d+2*w,v+d+h), .91),
                ([(x,y,z+d),(x+w,y,z+d),(x+w,y,z),(x,y,z)], (u+d,v,u+d+w,v+d), 1.0),
            ]
            for points, box, shade in definitions:
                a, b, c = [project(p) for p in points[:3]]
                # Backface culling in the screen's downward-Y coordinates.
                cross = (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
                if cross >= 0:
                    continue
                depth = sum((sin*p[0]+cos*p[2])*.9 - p[1]*.4 for p in points)/4
                faces.append((depth, points, box, shade))
    for _, points, box, shade in sorted(faces):
        pts = [project(p) for p in points]
        crop = tex.crop(tuple(round(k) for k in box))
        if crop.width == 0 or crop.height == 0:
            continue
        if shade != 1:
            crop = crop.point(lambda c: int(c*shade))
        # Affine source-to-parallelogram mapping, nearest pixels throughout.
        p0, p1, _, p3 = pts
        ax, ay = (p1[0]-p0[0])/crop.width, (p1[1]-p0[1])/crop.width
        bx, by = (p3[0]-p0[0])/crop.height, (p3[1]-p0[1])/crop.height
        det = ax*by-ay*bx
        if abs(det) < .001:
            continue
        coeff = (by/det, -bx/det, (bx*p0[1]-by*p0[0])/det,
                 -ay/det, ax/det, (ay*p0[0]-ax*p0[1])/det)
        layer = crop.transform(tile.size, Image.Transform.AFFINE, coeff, Image.Resampling.NEAREST)
        mask = Image.new('L', tile.size)
        ImageDraw.Draw(mask).polygon(pts, fill=255)
        tile.paste(layer, (0, 0), mask)
    draw = ImageDraw.Draw(tile)
    draw.text((26, 20), '活剪后' if sheared else '成熟响铃羊', font=font(25), fill='#29443d')
    return tile


if __name__ == '__main__':
    parts = mesh()
    output = Image.new('RGB', (1120, 520), '#efeee4')
    output.paste(render(parts, False, math.pi-.66), (0, 70))
    output.paste(render(parts, True, math.pi-.66), (560, 70))
    draw = ImageDraw.Draw(output)
    draw.text((28, 16), '响铃羊 · 游戏资产几何预览', font=font(30), fill='#203a33')
    draw.text((28, 492), '直接读取 Java 模型与 64px 贴图；离线预览，不是游戏截图。', font=font(17), fill='#52645b')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    output.save(OUT)
    print(OUT)
