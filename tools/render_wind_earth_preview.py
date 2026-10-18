"""Review actual Java-exported geometry; this is not a Minecraft screenshot."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
meshes=json.loads((ROOT/'work/wind-earth-geometry-preview.json').read_text(encoding='utf-8'))
image=Image.new('RGB',(1200,2100),'#101922')
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',14)
labels=['Wind: flight / feather streams','Wind: summoning / orbital currents','Wind: gale / spiral arcs',
        'Earth: projectile / stone seal','Earth: rupture / cracks and spires','Earth: mire / layered mud flow',
        'Earth: protection / stone and living roots']
for name,scene in meshes.items():
    kind,tier=scene['kind'],scene['tier']
    vertices=scene['vertices']; quads=[vertices[i:i+4] for i in range(0,len(vertices),4)]
    tile=Image.new('RGBA',(400,300),'#14212c'); ink=ImageDraw.Draw(tile)
    scale=110/max(scene['radius'],1.5)
    def project(v):
        x,y,z=v[:3]
        return (200+(x-z)*.707*scale,202+(x+z)*.3*scale-y*.86*scale)
    r=scene['radius']*1.1
    for n in range(-2,3):
        ink.line([project([n*r/2,0,-r]),project([n*r/2,0,r])],fill='#263c47')
        ink.line([project([-r,0,n*r/2]),project([r,0,n*r/2])],fill='#263c47')
    quads.sort(key=lambda q:sum(v[0]+v[2]-.15*v[1] for v in q))
    for quad in quads:
        layer=Image.new('RGBA',tile.size)
        color=tuple(max(0,min(255,round(quad[0][j]))) for j in range(3,7))
        ImageDraw.Draw(layer).polygon([project(v) for v in quad],fill=color)
        tile=Image.alpha_composite(tile,layer)
    ink=ImageDraw.Draw(tile)
    ink.text((12,12),f'T{tier}  {name.replace("_"," ")}',fill='#ddeaf4',font=font)
    ink.text((12,275),f'{len(vertices)} vertices / actual procedural mesh',fill='#9eb8ca',font=small)
    image.paste(tile.convert('RGB'),(((tier-1)//2)*400,kind*300))
image.save(ROOT/'work/wind-earth-preview.png')
print('Saved work/wind-earth-preview.png')
