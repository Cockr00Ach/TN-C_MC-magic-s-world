"""Render the actual Java-exported rain quads for review (not an in-game screenshot)."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
meshes = json.loads((ROOT / 'build/rain-preview-mesh.json').read_text())
image = Image.new('RGB', (1600, 400), '#101c2c')
draw = ImageDraw.Draw(image)
font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 17)
small = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 13)
draw.text((24, 18), 'RAIN GARDENS / actual procedural mesh / geometry preview', fill='#d9edff', font=font)
names = ['Dew', 'Jade rain', 'Lotus rain', 'Storm sentinels', 'Heavenly flood']
radii = [8, 12, 16, 22, 32]
for tier, vertices in enumerate(meshes):
    tile = Image.new('RGBA', (310, 310), '#142438')
    ink = ImageDraw.Draw(tile)
    ink.text((14, 12), f'T{tier+1}  {names[tier]}', fill='#d9edff', font=font)
    scale = 95 / radii[tier]
    def project(v):
        x, y, z = v[:3]
        return (155 + (x-z)*.707*scale, 210+(x+z)*.31*scale-y*.88*scale)
    for n in range(-3,4):
        rr=radii[tier]*1.15
        a, b=project([n*rr/3,0,-rr]),project([n*rr/3,0,rr])
        ink.line([a,b], fill='#24384b')
        a, b=project([-rr,0,n*rr/3]),project([rr,0,n*rr/3])
        ink.line([a,b], fill='#24384b')
    quads=[vertices[i:i+4] for i in range(0,len(vertices),4)]
    quads.sort(key=lambda q: sum(v[0]+v[2]-v[1]*.2 for v in q))
    for quad in quads:
        overlay=Image.new('RGBA',tile.size)
        color=tuple(max(0,min(255,round(quad[0][j]*255))) for j in range(3,7))
        ImageDraw.Draw(overlay).polygon([project(v) for v in quad],fill=color)
        tile=Image.alpha_composite(tile,overlay)
    ink=ImageDraw.Draw(tile)
    px, py=project([0,0,0])
    ink.ellipse((px-2,py-10,px+2,py-6),fill='#c0c9ce')
    ink.line((px,py-6,px,py), fill='#c0c9ce',width=2)
    ink.text((14,270),f'Radius {radii[tier]} blocks | {len(vertices)} vertices',fill='#95afc7',font=small)
    image.paste(tile.convert('RGB'),(8+tier*319,65))
target=ROOT/'docs/previews/water_rain_gardens_20261010.png'
image.save(target)
print(target)
