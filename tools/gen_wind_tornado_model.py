"""Author a baked, curved OBJ tornado shared by player gale and Canglan spells.

Geometry is static real mesh loaded by Forge; existing entity renderers rotate it.
No per-frame geometry generation or extra particles/entities are required.
"""
from pathlib import Path
import json,math
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
MODEL=ASSETS/'models/projectile/wind_tornado'
TEXTURE=ASSETS/'textures/spell_projectile/wind_tornado.png'

def point(t,angle,radius=1):
    r=(.065+.79*t**1.85)*radius
    # A gentle bent axis, pinned at ground contact.
    cx=.5+.10*math.sin(t*2.5)*t
    cz=.5+.07*math.sin(t*4.2)*t
    return (cx+r*math.cos(angle),2*t,cz+r*math.sin(angle))

def build():
    quads=[]
    # Two complete nested funnel layers, phase-shifted to reveal both silhouettes.
    for layer in range(2):
        for arm in range(3):
            for i in range(48):
                t0=.015+i/48*.965;t1=.015+(i+1)/48*.965
                def edges(t):
                    angle=arm*math.tau/3+t*math.tau*(2.1+layer*.25)+layer*.85+.12*math.sin(t*8+arm)
                    width=(.105+.195*t)*math.sin(math.pi*t)**.4*(1 if layer==0 else .65)
                    radius=1 if layer==0 else .66
                    return point(max(.005,t-width/2),angle,radius),point(min(1,t+width/2),angle,radius)
                a,b=edges(t0);c,d=edges(t1)
                quads.append(([a,c,d,b],[(i/48,0),((i+1)/48,0),((i+1)/48,1),(i/48,1)]))
    # One low, open spiral traces air feeding into the foot, not floating debris cubes.
    for i in range(12):
        def ground(t,side):
            a=t*math.tau*1.15;r=.15+.48*t+side*.090*math.sin(math.pi*t)
            return (.5+r*math.cos(a),.025+.06*t,.5+r*math.sin(a))
        a=i/12;b=(i+1)/12
        quads.append(([ground(a,-1),ground(b,-1),ground(b,1),ground(a,1)],[(a,0),(b,0),(b,1),(a,1)]))
    lines=['# TN-C curved wind ribbons; generated mesh, coordinates in blocks','mtllib wind_tornado.mtl','o wind_funnel','usemtl wind']
    for points,uvs in quads:
        for p in points:lines.append('v '+' '.join(f'{v:.6f}' for v in p))
    for points,uvs in quads:
        for uv in uvs:lines.append('vt '+' '.join(f'{v:.6f}' for v in uv))
    for i,(points,uvs) in enumerate(quads):
        indices=[i*4+j+1 for j in range(4)]
        # Explicit reverse faces keep the thin ribbons visible from inside the funnel.
        for order in (indices,list(reversed(indices))):lines.append('f '+' '.join(f'{j}/{j}' for j in order))
    assert len(quads)*2<=600
    assert all(-1<=v<=2 for points,_ in quads for p in points for v in p)
    MODEL.with_suffix('.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    MODEL.with_suffix('.mtl').write_text('newmtl wind\nKd 1 1 1\nd 1\nmap_Kd tnc:spell_projectile/wind_tornado\n',encoding='utf-8')
    MODEL.with_suffix('.json').write_text(json.dumps({'loader':'forge:obj','model':'tnc:models/projectile/wind_tornado.obj','automatic_culling':False,'shade_quads':False,'flip_v':False,'emissive_ambient':False,'textures':{'particle':'tnc:spell_projectile/wind_tornado'}},indent=2)+'\n',encoding='utf-8')
    # Longitudinal wisps, feathered across each ribbon instead of hard white borders.
    image=Image.new('RGBA',(256,64))
    for y in range(64):
        v=y/63;edge=math.sin(math.pi*v)**1.6
        for x in range(256):
            u=x/255
            streak=.68+.12*math.sin(v*19+u*6)+.08*math.sin(v*35-u*9)
            end=math.sin(math.pi*u)**.30
            alpha=int(205*edge*end*max(.18,streak))
            image.putpixel((x,y),(190+int(35*edge),221+int(21*edge),239+int(13*edge),alpha))
    image.save(TEXTURE)
    print(f'Curved tornado: {len(quads)*2} baked quads; two nested layers of 3 ribbons; OBJ/MTL/texture generated')

if __name__=='__main__':build()
