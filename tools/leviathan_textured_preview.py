"""Texture-sampled depth-buffer rendering of the exported Leviathan for offline visual QA."""
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from preview_water_leviathan import rotation
from water_leviathan_materials import face_rects


def render_preview(geo_path, texture_path, output):
    bones = {b['name']: b for b in json.loads(Path(geo_path).read_text(encoding='utf8'))['minecraft:geometry'][0]['bones']}
    tex = np.asarray(Image.open(texture_path).convert('RGBA'))
    glow = np.asarray(Image.open(str(texture_path).replace('_bedrock.png', '_emissive.png')).convert('RGBA'))
    transforms = {}
    def transform(name):
        if name in transforms: return transforms[name]
        b = bones[name]
        r = rotation(b.get('rotation', [0, 0, 0]))
        p = np.array(b['pivot'], dtype=float)
        t = p-r@p
        if b.get('parent'):
            pr, pt = transform(b['parent'])
            r, t = pr@r, pr@t+pt
        transforms[name] = r, t
        return r, t
    faces = {'west': (2, 3, 1, 0), 'east': (7, 6, 4, 5), 'north': (6, 2, 0, 4),
             'south': (3, 7, 5, 1), 'up': (2, 6, 7, 3), 'down': (1, 5, 4, 0)}
    meshes = []
    for name, b in bones.items():
        r, t = transform(name)
        for c in b.get('cubes', []):
            inflate = c.get('inflate', 0)
            o, s = np.array(c['origin'])-inflate, np.array(c['size'])+inflate*2
            vertices = np.array([o+s*np.array([x, y, z]) for x in (0, 1) for y in (0, 1) for z in (0, 1)])
            meshes.append((vertices@r.T+t, face_rects(c)))

    def panel(w, h, yaw, pitch, zoom=1, focus=None):
        camera = rotation([pitch, yaw, 0])
        projected = [(v@camera.T, rect) for v, rect in meshes]
        pts = np.concatenate([v for v, _ in projected])
        lo, hi = pts[:, :2].min(axis=0), pts[:, :2].max(axis=0)
        scale = min((w-70)/(hi[0]-lo[0]), (h-50)/(hi[1]-lo[1]))*zoom
        center = (lo+hi)/2 if focus is None else (camera@np.array(focus))[:2]
        rgb = np.zeros((h, w, 3), dtype=np.uint8)
        rgb[:] = [9, 23, 34]
        depth = np.full((h, w), np.inf)
        def triangle(p, uv, shade):
            x0, x1 = max(0, int(p[:, 0].min())), min(w-1, int(np.ceil(p[:, 0].max())))
            y0, y1 = max(0, int(p[:, 1].min())), min(h-1, int(np.ceil(p[:, 1].max())))
            if x0>x1 or y0>y1: return
            a, b, c = p
            det = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(det)<1e-8: return
            yy, xx = np.mgrid[y0:y1+1, x0:x1+1]
            xx, yy = xx+0.5, yy+0.5
            wa = ((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det
            wb = ((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
            wc = 1-wa-wb
            zz = wa*a[2]+wb*b[2]+wc*c[2]
            target = depth[y0:y1+1, x0:x1+1]
            mask = (wa>=-1e-6)&(wb>=-1e-6)&(wc>=-1e-6)&(zz<target)
            if not mask.any(): return
            tx = np.clip((wa*uv[0, 0]+wb*uv[1, 0]+wc*uv[2, 0]).astype(int), 0, tex.shape[1]-1)
            ty = np.clip((wa*uv[0, 1]+wb*uv[1, 1]+wc*uv[2, 1]).astype(int), 0, tex.shape[0]-1)
            albedo, light = tex[ty, tx], glow[ty, tx]
            mask &= albedo[:, :, 3]>0
            strength = light[:, :, 3:4]/255.0
            color = albedo[:, :, :3]*shade*(1-strength)+light[:, :, :3]*strength
            rgb[y0:y1+1, x0:x1+1][mask] = np.clip(color[mask], 0, 255).astype(np.uint8)
            target[mask] = zz[mask]
        for vertices, rects in projected:
            screen = vertices.copy()
            screen[:, 0] = w/2+(vertices[:, 0]-center[0])*scale
            screen[:, 1] = h/2-(vertices[:, 1]-center[1])*scale
            for face, indices in faces.items():
                fv = vertices[list(indices)]
                normal = np.cross(fv[1]-fv[0], fv[2]-fv[0])
                normal /= max(1e-9, np.linalg.norm(normal))
                shade = 0.70+0.38*abs(float(normal@np.array([0.3, 0.8, -0.5])))
                u, v, fw, fh = rects[face]
                uv = np.array([[u, v], [u+fw-0.001, v], [u+fw-0.001, v+fh-0.001], [u, v+fh-0.001]])
                p = screen[list(indices)]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    triangle(p[list(tri)], uv[list(tri)], shade)
        return Image.fromarray(rgb)

    canvas = Image.new('RGB', (1800, 1200), (6, 16, 26))
    canvas.paste(panel(1800, 610, 57, 14), (0, 110))
    canvas.paste(panel(1060, 400, 90, 0), (0, 760))
    canvas.paste(panel(720, 400, 22, 8, 1.9, (0, 44, -78)), (1080, 760))
    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 32)
    small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 19)
    draw.text((40, 20), '圣渊利维坦 · 鳞甲与蓝金圣纹', font=title, fill=(234, 207, 143))
    draw.text((42, 70), '实际模型 + 实际贴图离线渲染 · 非游戏截图', font=small, fill=(119, 187, 205))
    draw.text((35, 725), '侧面全貌 · 叠鳞 / 分节腹甲 / 鳍骨与鳍膜', font=small, fill=(190, 211, 216))
    draw.text((1100, 725), '头部特写 · 镶嵌甲片 / 眼窝 / 獠牙', font=small, fill=(190, 211, 216))
    canvas.save(output)
