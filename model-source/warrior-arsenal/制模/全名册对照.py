"""Render each pair with one camera, one orthographic scale and one crop."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import math
import struct
import subprocess

import numpy as np
from PIL import Image, ImageDraw
from scipy.spatial.transform import Rotation

from gallery import RASTER, geometry, font
from roster import ROOT, FAMILIES, all_ids, file_name

OUT = ROOT / "预览" / "全武器对照"
DETAIL = OUT / "单件"
W, H = 432, 570
SS = 2
BACKGROUND = "#29353d"
PAGE = "#18222a"
TEXT = "#f0ebe2"
SECONDARY = "#b2c0c6"


def projected(geo, horizontal, rotation):
    points, uv, atlas = geo
    if horizontal:
        points = points[:, :, [0, 2, 1]].copy()
        points[:, :, 2] *= -1
    screen = rotation.apply(points.reshape(-1, 3)).reshape(points.shape)
    normals = np.cross(screen[:, 1] - screen[:, 0], screen[:, 2] - screen[:, 0])
    length = np.linalg.norm(normals, axis=1)
    normals /= np.maximum(length[:, None], 1e-12)
    normals *= np.where(normals[:, 2:] < 0, -1.0, 1.0)
    shade = .65 + .43 * np.maximum(0, normals @ [-.34, .61, .71]) + .14 * np.maximum(0, normals @ [.74, .49, -.3])
    return screen, uv, atlas, length > 1e-8, shade


def draw_surface(geo, center, scale):
    screen, uv, atlas, valid, shade = geo
    screen = screen.copy()
    ww, hh = W * SS, H * SS
    screen[:, :, 0] = (screen[:, :, 0] - center[0]) * scale + ww / 2
    screen[:, :, 1] = hh / 2 - (screen[:, :, 1] - center[1]) * scale
    points = np.concatenate([screen, uv], axis=2)
    tri = np.column_stack((points.reshape(-1, 15), shade, np.zeros((len(points), 2))))[valid].astype('<f4')
    header = struct.pack('<5i', ww, hh, *atlas.size, len(tri))
    raw = subprocess.run([str(RASTER)], input=header + atlas.tobytes() + bytes(atlas.width * atlas.height * 4) + tri.tobytes(),
                         stdout=subprocess.PIPE, check=True).stdout
    img = Image.frombytes('RGBA', (ww, hh), raw).resize((W, H), Image.Resampling.LANCZOS)
    box = img.getbbox()
    assert box and box[0] > 0 and box[1] > 0 and box[2] < W and box[3] < H, box
    return img


def pair(tag, key):
    from 对照平面物品 import sprite_geometry
    geos = []
    for revised in (False, True):
        geo, data = geometry(key, revised)
        geos.append(geo if geo is not None else sprite_geometry(key, data))
    # Team models use either +Z or +Y as the long axis. Use the revised asset's
    # authored rest axis for BOTH renderings; its width never determines framing alone.
    spread = np.ptp(geos[1][0], axis=(0, 1))
    horizontal = spread[2] > 2.0 * spread[1]
    angle = (-31, 12, -6) if key in ('greatsword','hammer') else (-19, 9, -9)
    rotation = Rotation.from_euler('yxz', angle, degrees=True)
    items = [projected(g, horizontal, rotation) for g in geos]
    lo = np.minimum(*[x[0].min(axis=(0, 1))[:2] for x in items])
    hi = np.maximum(*[x[0].max(axis=(0, 1))[:2] for x in items])
    scale = SS * min((W - 32) / (hi[0] - lo[0]), (H - 34) / (hi[1] - lo[1]))
    cell = Image.new("RGB", (W * 2 + 2, H + 89), BACKGROUND)
    d = ImageDraw.Draw(cell)
    d.text((19, 10), file_name(key), font=font(31, True), fill=TEXT)
    d.line((0, 57, cell.width, 57), fill="#40505a")
    d.line((W, 58, W, cell.height), fill="#40505a")
    for i, (g, label) in enumerate(zip(items, ('原版','精修后'))):
        obj = draw_surface(g, (lo+hi) / 2, scale)
        x = i * (W+2)
        cell.paste(obj, (x, 58), obj)
        d.text((x+W/2, H+66), label, font=font(19), fill=SECONDARY, anchor="mt")
    dest = DETAIL/FAMILIES[tag]/(file_name(key)+".jpg")
    dest.parent.mkdir(parents=True,exist_ok=True)
    cell.save(dest, quality=95, subsampling=0)
    return cell, dest


def main():
    entries = list(all_ids())
    assert len(entries)==len({k for _,k in entries})==45
    OUT.mkdir(parents=True,exist_ok=True)
    photos = []
    pages = 0
    for tag, family in FAMILIES.items():
        group = [k for t,k in entries if t==tag]
        half = math.ceil(len(group)/2) if len(group)>6 else len(group)
        chunks = [group[i:i+half] for i in range(0,len(group),half)]
        for index, keys in enumerate(chunks):
            photos_in_page=[]
            for k in keys:
                cell, path = pair(tag,k)
                photos_in_page.append(cell)
                photos.append(path)
            pages += 1
            margin,gap,head = 22,15,90
            cw,ch = photos_in_page[0].size
            rows=math.ceil(len(keys)/2)
            page=Image.new("RGB",(margin*2+2*cw+gap,head+rows*ch+(rows-1)*gap+margin),PAGE)
            d=ImageDraw.Draw(page)
            d.text((margin,16),family + "  ·  精修前后",font=font(42,True),fill=TEXT)
            subtitle = f"{index+1} / {len(chunks)}" if len(chunks)>1 else f"{len(keys)} 件"
            d.text((page.width-margin,30),subtitle,font=font(25),anchor="rt",fill=SECONDARY)
            for i,cell in enumerate(photos_in_page):
                x=margin+(i%2)*(cw+gap)
                if i==len(keys)-1 and len(keys)%2:x=(page.width-cw)//2
                page.paste(cell,(x,head+(i//2)*(ch+gap)))
            suffix=('-'+('上','下')[index]) if len(chunks)>1 else ''
            page.save(OUT/f"{pages:02d}-{family}{suffix}.jpg",quality=94,subsampling=0)
            print(f"{pages:02d} {family} {', '.join(file_name(k) for k in keys)}",flush=True)
    archive=OUT/'全45件武器单件高清对照.zip'
    with ZipFile(archive,'w',compression=ZIP_STORED) as z:
        for p in photos:z.write(p,p.relative_to(DETAIL))
    with ZipFile(archive) as z:
        assert len(z.namelist())==45 and z.testzip() is None
    assert pages==8


if __name__=="__main__":main()
