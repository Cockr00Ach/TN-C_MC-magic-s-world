"""Editable, vanilla-baked water seals and rain models; no runtime mesh generation."""
import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/tnc/models/water'
TEXTURES={'deep':'minecraft:block/blue_concrete','water':'minecraft:block/cyan_concrete',
          'light':'minecraft:block/white_concrete','pearl':'minecraft:block/white_concrete'}
def box(x,y,z,w,h,d,tex):
    return {'from':[round(x-w/2,4),round(y,4),round(z-d/2,4)],
            'to':[round(x+w/2,4),round(y+h,4),round(z+d/2,4)],'shade':False,
            'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ['up','down','north','south','east','west']}}
def save(name,parts):
    OUT.mkdir(parents=True,exist_ok=True)
    model={'credit':'TN-C water models; tools/gen_water_solid_models.py','ambientocclusion':False,
           'textures':dict(TEXTURES,particle=TEXTURES['water']),'elements':parts}
    (OUT/(name+'.json')).write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
seal=[]
for radius,width,y,tex in [(7.6,.34,0,'deep'),(7.25,.25,.20,'light'),(5.7,.25,.12,'water'),(2.4,.28,.23,'light')]:
    # Tangential rectangular segments rotated in legal 22.5 degree increments.
    for i in range(16):
        angle=i*math.pi/8;x=8+math.sin(angle)*radius;z=8+math.cos(angle)*radius
        part=box(x,y,z,radius*.41,.22,width,tex)
        part['rotation']={'origin':[8,y,8],'axis':'y','angle':0}
        # Rotate each bar around its own centre with only vanilla legal angles,
        # swapping axes for the other quadrants.
        degrees=i*22.5;turn=((degrees+45)%90)-45
        if int((degrees+45)//90)%2:part=box(x,y,z,width,.22,radius*.41,tex)
        part['rotation']={'origin':[round(x,4),y,round(z,4)],'axis':'y','angle':turn}
        seal.append(part)
for i in range(8):
    a=i*math.pi/4;x=8+math.sin(a)*6.45;z=8+math.cos(a)*6.45
    seal.extend([box(x,.40,z,.25,.27,.85,'pearl'),box(x,.40,z+.38,.65,.27,.18,'light'),
                 box(x+.22,.40,z-.16,.18,.27,.40,'water')])
for i in range(8):
    a=i*math.pi/4
    seal.append(box(8+math.sin(a)*3.65,.15,8+math.cos(a)*3.65,.8,.3,.8,'water'))
# Raised eight-point star and a carved droplet at the heart of the seal.
for degrees in [0,45]:
    for x,z,w,d in [(8,5.2,5.6,.20),(8,10.8,5.6,.20),(5.2,8,.20,5.6),(10.8,8,.20,5.6)]:
        part=box(x,.32,z,w,.24,d,'water')
        part['rotation']={'origin':[8,.32,8],'axis':'y','angle':degrees};seal.append(part)
for row,width in enumerate([.30,.65,1.1,1.6,2.15,2.35,2.0,1.2]):
    seal.append(box(8,.35,6.5+row*.40,width,.40,.42,'light'))
for i in range(8):
    a=i*math.pi/4;x=8+math.sin(a)*4.75;z=8+math.cos(a)*4.75
    part=box(x,.28,z,.20,.28,.85,'light')
    part['rotation']={'origin':[x,.28,z],'axis':'y','angle':45 if i%2 else 0};seal.append(part)
save('seal',seal)
save('raindrop',[box(8,1,8,1.2,1.6,1.2,'light'),box(8,2.6,8,2.3,3.5,2.3,'water'),
                box(8,6.1,8,1.6,3.2,1.6,'light'),box(8,9.3,8,.8,3.4,.8,'pearl')])
crown=[]
for i in range(12):
    a=i*math.pi/6;x=8+math.sin(a)*6.8;z=8+math.cos(a)*6.8
    crown.extend([box(x,0,z,2.1,.7,2.1,'water'),box(x,.65,z,1.55,.8,1.55,'light'),box(x+.5,.3,z+.2,.8,.65,.8,'light')])
save('rain_crown',crown)
print('Generated solid water models:', ', '.join(p.name for p in OUT.glob('*.json')))
