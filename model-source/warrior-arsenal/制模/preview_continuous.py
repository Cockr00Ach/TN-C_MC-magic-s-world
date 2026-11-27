"""Render the actual exported meshes at a grazing angle for geometry comparison."""
import io
from zipfile import ZipFile

import gallery
import numpy as np
from PIL import Image, ImageDraw
from roster import file_name, ROOT
from continuous_weapons import OUTPUT, PREFIX, MESH_PATH


def mesh_geometry(archive, item):
    stem = PREFIX+MESH_PATH+item
    materials = {}
    for line in archive.read(stem+'.mtl').decode().splitlines():
        if line.startswith('newmtl '):
            key = line.split()[1]
        elif line.startswith('map_Kd '):
            materials[key] = line.split()[1]
    vertex, uv = [], []
    triangles, coordinates, texture_keys = [], [], []
    for line in archive.read(stem+'.obj').decode().splitlines():
        tokens = line.split()
        command, values = tokens[0],tokens[1:]
        if command=='v':
            vertex.append(np.array(values,float)*16)
        elif command=='vt':
            uv.append(np.array(values,float))
        elif command=='usemtl':
            active = values[0]
        elif command=='f':
            ids = [tuple(int(n)-1 for n in v.split('/')) for v in values]
            for i in range(1,len(ids)-1):
                corners = (ids[0],ids[i],ids[i+1])
                triangles.append([vertex[j] for j,_,_ in corners])
                coordinates.append([uv[k] for _,k,_ in corners])
                texture_keys.append(active)
    textures = {}
    names = set(archive.namelist())
    for key, ref in materials.items():
        ns,loc = ref.split(':',1)
        path = f'assets/{ns}/textures/{loc}.png'
        textures[key] = Image.open(io.BytesIO(archive.read(path))).convert('RGBA') if path in names else gallery.texture(ref,True)
    sheet = Image.new('RGBA',(sum(im.width for im in textures.values()),max(im.height for im in textures.values())))
    coordinates = np.array(coordinates)
    offset = 0
    for key,im in textures.items():
        sheet.paste(im,(offset,0))
        mask = np.array(texture_keys)==key
        coordinates[mask] = coordinates[mask]*im.size+[offset,0]
        offset += im.width
    return (np.array(triangles),coordinates,sheet),{}


def make_preview():
    keys = ('yue_bronze_jian','cloudridge_jian','bronze_greatsword','doom_scythe','spear','ember_bow')
    width,height = 196,325
    page = Image.new('RGB',(width*len(keys),2*(height+45)),'#18222c')
    pen = ImageDraw.Draw(page)
    original = gallery.geometry
    with ZipFile(OUTPUT) as archive:
        try:
            for row,label in enumerate(('原模型 · 方块拼接','新版 · 连续斜面')):
                if row:
                    gallery.geometry = lambda key,patched: mesh_geometry(archive,key)
                y = row*(height+45)
                pen.text((10,y+4),label,font=gallery.font(17,True),fill='#edf2f6')
                for i,key in enumerate(keys):
                    sample = gallery.panel(key,True,(width,height),yaw=-39,pitch=10,roll=-14)
                    page.paste(sample,(i*width,y+29))
                    pen.text((i*width+width/2,y+height+35),file_name(key),font=gallery.font(13),
                             anchor='mm',fill='#e5edf1')
        finally:
            gallery.geometry = original
    page.save(ROOT/'预览/连续斜面手持几何对照.png')


if __name__ == '__main__':
    make_preview()
