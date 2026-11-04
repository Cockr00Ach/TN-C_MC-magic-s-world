"""龙之剑那条龙的离线预览图。

读 `build/tnc-dragon-quads.csv` —— 那份数据是单测 `WarriorDragonGeometryTest` 从
`WarriorDragonGeometry.build(...)` 直接写出来的三角形，和客户端渲染器画的是同一批顶点。
所以这张图不是"另外画一遍"，只是把同一堆三角形成像（普通透明混合与深度测试，对应本轮实体几何渲染）。

用法：python tools/dragon_preview.py [输出路径]
默认输出 docs/previews/dragon-pixel-forge-20261105.png
"""
import csv
import sys
from pathlib import Path

from PIL import Image, ImageDraw
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "build" / "tnc-dragon-quads.csv"
TARGET = ROOT / "docs" / "previews" / "dragon-pixel-forge-20261105.png"

CELL_W, CELL_H = 860, 400
PAD = 22
# 3/4 俯侧视：龙沿 +Z 飞，横向摆动在 -X，所以屏幕上"右=前、上=高、左=摆"
FORWARD_X, FORWARD_Y = 0.70, 0.30
SIDE_X = 1.05
BACKGROUND = (7, 9, 14)
MARGIN = 42.0


def project(point, scale, ox, oy):
    x, y, z = point
    return (ox + ((-x) * SIDE_X + z * FORWARD_X) * scale,
            oy - (y - z * FORWARD_Y) * scale)


def load_frames():
    frames, current = [], None
    with SOURCE.open(newline="", encoding="utf-8") as handle:
        for row in csv.reader(handle):
            if not row:
                continue
            if row[0] == "FRAME":
                current = {"head": float(row[1]), "tris": []}
                frames.append(current)
                continue
            if current is None:
                raise SystemExit("csv must start with a FRAME line")
            color, alpha = int(row[0]), float(row[1])
            values = [float(v) for v in row[2:]]
            points = [tuple(values[i:i + 3]) for i in (0, 3, 6)]
            current["tris"].append((color, alpha, points))
    return frames


def fit(frames):
    """一套投影参数通吃两帧：按全部顶点的投影包围盒定缩放和偏移。"""
    xs, ys = [], []
    for frame in frames:
        for _color, _alpha, points in frame["tris"]:
            for point in points:
                x = (-point[0]) * SIDE_X + point[2] * FORWARD_X
                y = -(point[1] - point[2] * FORWARD_Y)
                xs.append(x)
                ys.append(y)
    span_x = max(xs) - min(xs)
    span_y = max(ys) - min(ys)
    scale = min((CELL_W - MARGIN * 2) / span_x, (CELL_H - MARGIN * 2) / span_y)
    ox = MARGIN - min(xs) * scale
    oy = MARGIN - min(ys) * scale
    return scale, ox, oy


def render_frame(frame, scale, ox, oy):
    buffer=np.full((CELL_H,CELL_W,3),BACKGROUND,dtype=float)
    zbuffer=np.full((CELL_H,CELL_W),-1e20)
    for color,alpha,points in frame['tris']:
        p=np.array([project(v,scale,ox,oy) for v in points]);depth=np.array([v[0]*.7+v[1]*.315+v[2]*1.05 for v in points])
        lo=np.maximum(0,np.floor(p.min(axis=0)).astype(int));hi=np.minimum([CELL_W-1,CELL_H-1],np.ceil(p.max(axis=0)).astype(int))
        if np.any(hi<lo):continue
        a,b=p[1]-p[0],p[2]-p[0];det=a[0]*b[1]-a[1]*b[0]
        if abs(det)<1e-9:continue
        gy,gx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];dx,dy=gx-p[0,0],gy-p[0,1]
        u=(dx*b[1]-dy*b[0])/det;v=(a[0]*dy-a[1]*dx)/det
        z=depth[0]+u*(depth[1]-depth[0])+v*(depth[2]-depth[0])
        target=buffer[lo[1]:hi[1]+1,lo[0]:hi[0]+1];zt=zbuffer[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
        mask=(u>=0)&(v>=0)&(u+v<=1)&(z>zt)
        rgb=np.array([(color>>16)&255,(color>>8)&255,color&255])
        target[mask]=target[mask]*(1-alpha)+rgb*alpha;zt[mask]=z[mask]
    return Image.fromarray(np.clip(buffer,0,255).astype('uint8'),'RGB')


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else TARGET
    frames = load_frames()
    scale, ox, oy = fit(frames)
    images = [render_frame(frame, scale, ox, oy) for frame in frames]
    sheet = Image.new("RGB", (CELL_W, CELL_H * len(images) + PAD * (len(images) - 1)), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for index, (frame, image) in enumerate(zip(frames, images)):
        top = index * (CELL_H + PAD)
        sheet.paste(image, (0, top))
        draw.text((16, top + 10), f"龙头进度 {frame['head']:.2f}  ({len(frame['tris'])} tris)",
                  fill=(208, 216, 228))
        draw.text((16, top + 26), "Production mesh: segmented armour / claws / jaws / forked antlers",
                  fill=(120, 134, 152))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(f"wrote {out} ({out.stat().st_size} bytes, {len(frames)} frames, scale={scale:.1f})")


if __name__ == "__main__":
    main()
