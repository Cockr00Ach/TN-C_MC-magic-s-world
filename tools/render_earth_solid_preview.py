"""Inspect authored model boxes and rotations; material colours approximate in-game textures."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1]
names=['earth_clod','earth_orb','earth_shell','earth_salvo','earth_spear','earth_pillar','earth_mud_plate','earth_root']
colors={'soil':(114,83,53),'stone':(139,140,133),'dark':(79,82,82),'vein':(194,176,131),
        'moss':(86,131,66),'mud':(130,96,64),'wood':(102,76,47),'leaf':(84,131,64)}
image=Image.new('RGB',(1200,650),'#17232c')
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
ink=ImageDraw.Draw(image)
ink.text((18,14),'EARTH / actual baked model geometry / material colours approximated / not a game screenshot',font=font,fill='#e1e7df')
faces={'north':[0,1,3,2],'south':[4,6,7,5],'west':[0,2,6,4],
       'east':[1,5,7,3],'down':[0,4,5,1],'up':[2,3,7,6]}
for index,name in enumerate(names):
    model=json.loads((root/f'src/main/resources/assets/tnc/models/projectile/{name}.json').read_text(encoding='utf-8'))
    polygons=[]
    for box in model['elements']:
        a,b=box['from'],box['to']
        points=[[x,y,z] for z in [a[2],b[2]] for y in [a[1],b[1]] for x in [a[0],b[0]]]
        if 'rotation' in box:
            rot=box['rotation'];axis=rot['axis'];origin=rot['origin'];angle=math.radians(rot['angle']);c,s=math.cos(angle),math.sin(angle)
            for p in points:
                x,y,z=[p[i]-origin[i] for i in range(3)]
                if axis=='y':x,z=x*c+z*s,-x*s+z*c
                elif axis=='z':x,y=x*c-y*s,x*s+y*c
                else:y,z=y*c-z*s,y*s+z*c
                p[:]=[x+origin[0],y+origin[1],z+origin[2]]
        for face,ids in faces.items():
            material=box['faces'][face]['texture'].lstrip('#')
            color=colors.get(material,(142,142,133))
            shade={'up':1.12,'south':.83,'east':.95,'north':.72,'west':.78,'down':.6}[face]
            polygons.append(([points[i] for i in ids],tuple(min(255,int(v*shade)) for v in color)))
    def raw(p):return ((p[0]-p[2])*.707,(p[0]+p[2])*.3-p[1]*.88)
    projected=[raw(p) for poly,_ in polygons for p in poly]
    xmin,xmax=min(p[0] for p in projected),max(p[0] for p in projected)
    ymin,ymax=min(p[1] for p in projected),max(p[1] for p in projected)
    scale=min(225/max(1,xmax-xmin),220/max(1,ymax-ymin))
    ox,oy=(index%4)*300,(index//4)*290+50
    def project(p):
        x,y=raw(p);return (ox+150+(x-(xmin+xmax)/2)*scale,oy+150+(y-(ymin+ymax)/2)*scale)
    polygons.sort(key=lambda item:sum(p[0]+p[2]-p[1]*.2 for p in item[0]))
    for poly,color in polygons:ink.polygon([project(p) for p in poly],fill=color,outline=tuple(int(v*.86) for v in color))
    ink.text((ox+15,oy+262),name.replace('_',' '),font=font,fill='#d8e2da')
image.save(root/'work/earth-solid-models-preview.png')
