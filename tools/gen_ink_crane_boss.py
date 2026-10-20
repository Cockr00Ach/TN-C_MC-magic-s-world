"""Refine the user's tall crane proportions into an articulated ink crane.

The preview is projected from the actual exported cubes and bone transforms.
Every cube face gets its own packed UV rectangle; no shared overflowing box UV.
"""
from pathlib import Path
import copy
import json
import math
import random
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tnc'
SOURCE = ROOT / 'model-source/ink-crane/user-proportions-20261018.geo.json'
GEO = ASSETS / 'geo/entity/ink_crane_boss.geo.json'
ANIM = ASSETS / 'animations/entity/ink_crane_boss.animation.json'
TEX = ASSETS / 'textures/entity/ink_crane_boss.png'
PREVIEW = ROOT / 'docs/previews/ink_crane_boss_preview.png'
MATERIALS = {'ink': 13, 'soft': 26, 'feather': 18, 'silver': 192, 'white': 242, 'claw': 40}
BONES = []


def bone(name, parent, pivot, rotation=None):
    value = {'name': name, 'pivot': list(pivot)}
    if parent:
        value['parent'] = parent
    if rotation:
        value['rotation'] = list(rotation)
    BONES.append(value)
    return value


def box(target, origin, size, material='ink', rotation=None, pivot=None):
    value = {'origin': list(origin), 'size': list(size), '_material': material}
    if rotation:
        value['rotation'] = list(rotation)
        value['pivot'] = list(pivot or origin)
    target.setdefault('cubes', []).append(value)
    return value


def segment(target, start, end, width, depth=None, material='ink'):
    """A pivoted tapered-profile segment along a 3D centerline."""
    dx, dy, dz = [end[i] - start[i] for i in range(3)]
    length = math.sqrt(dx*dx + dy*dy + dz*dz)
    yaw = math.degrees(math.atan2(dx, dz))
    pitch = -math.degrees(math.asin(dy / max(length, .001)))
    return box(target, (start[0]-width/2, start[1]-(depth or width)/2, start[2]),
               (width, depth or width, length+.25), material, (pitch, yaw, 0), start)


def plume(name, parent, points, widths, glow=False):
    root = bone(name, parent, points[0])
    rim = bone(('glow_outline_' if glow else 'outline_') + name, name, points[0])
    for i in range(len(points)-1):
        a, b = points[i], points[i+1]
        w = widths[i]
        segment(root, a, b, w, max(.8, w*.16), 'feather')
        # Thin white quill follows the top surface, with black vanes visible.
        segment(rim, (a[0], a[1]+max(.6, w*.1), a[2]),
                (b[0], b[1]+max(.6, widths[i+1]*.1), b[2]),
                max(.22, w*.055), .24, 'white' if glow else 'silver')
    return root


def curve(points, subdivisions=5):
    result=[]
    for i in range(len(points)-1):
        p0=np.array(points[max(0,i-1)],dtype=float);p1=np.array(points[i],dtype=float)
        p2=np.array(points[i+1],dtype=float);p3=np.array(points[min(len(points)-1,i+2)],dtype=float)
        for k in range(subdivisions):
            t=k/subdivisions
            p=.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t**3)
            result.append(p.tolist())
    result.append(list(points[-1]));return result


def build_bones():
    BONES.clear()
    authored = json.loads(SOURCE.read_text(encoding='utf-8'))['minecraft:geometry'][0]
    landmarks = {b['name']: b['pivot'] for b in authored['bones']}
    body_y, head_y = landmarks['body'][1], landmarks['head'][1]
    bone('root', None, (0, 0, 0))
    body = bone('body', 'root', (0, body_y, 0))
    # Slender overlapping sections form an elongated ribcage, not a round mass.
    for i, (z, w, h) in enumerate([(-24, 17, 17), (-16, 23, 23), (-6, 27, 27),
                                  (6, 28, 26), (18, 24, 21), (28, 17, 16)]):
        box(body, (-w/2, body_y-h/2, z), (w, h, 13), 'ink' if i%2 else 'soft')
    bone('chest', 'body', (0, body_y+9, -20))
    # Connected S-shaped neck retains the user's raised head landmark.
    necks = [('neck_low', 'chest', [(0,body_y+6,-21), (0,body_y+29,-27), (0,body_y+48,-41)], [10,8.5,7]),
             ('neck_mid', 'neck_low', [(0,body_y+48,-41), (0,head_y-22,-48), (0,head_y-12,-43)], [7,6.3,6]),
             ('neck_high', 'neck_mid', [(0,head_y-12,-43), (0,head_y-3,-48), (0,head_y,-58)], [6,5.5,5])]
    for name, parent, pts, ws in necks:
        n = bone(name, parent, pts[0])
        path=curve(pts)
        for i in range(len(path)-1):
            w=ws[0]+(ws[-1]-ws[0])*i/(len(path)-1)
            segment(n, path[i], path[i+1], w, w, 'ink')
        rim = bone('outline_'+name, name, pts[0])
        for i in range(len(path)-1):
            a,b=path[i],path[i+1];w=ws[0]+(ws[-1]-ws[0])*i/(len(path)-1)
            for side in (-1,1):
                segment(rim, (a[0]+side*(w/2+.1),a[1],a[2]),
                        (b[0]+side*(w/2+.1),b[1],b[2]), .35, .35, 'white')

    head = bone('head', 'neck_high', (0, head_y, -58))
    bone('emitter_head','head',(0,head_y+6,-55))
    for origin,size,mat in [((-6,head_y-5,-69),(12,10,18),'ink'),
                            ((-4.5,head_y+2,-66),(9,5,12),'soft'),
                            ((-5,head_y-3,-72),(10,6,9),'ink')]:
        box(head,origin,size,mat)
    beak = bone('beak','head',(0,head_y-1,-72))
    for i in range(5):
        w=5.2-i*.95
        box(beak,(-w/2,head_y-2.5+i*.22,-79-i*7),(w,3.6-i*.6,8),'claw')
    beak_rim=bone('outline_beak','beak',(0,head_y-1,-72))
    segment(beak_rim,(0,head_y+1,-74),(0,head_y-1,-108),.3,.3,'silver')
    eye = bone('eyes','head',(0,head_y+1,-67))
    for side in (-1,1):
        box(eye,(side*6.1-.18,head_y+.5,-66),(0.36,1.5,4),'white')
        brow=bone('brow_'+str(side),'head',(side*5,head_y+3,-66))
        segment(brow,(side*5.6,head_y+3,-70),(side*6.6,head_y+4,-61),.45,.5,'silver')
    bone('crown','head',(0,head_y+5,-55))
    for i in range(9):
        s=(i-4)/4
        plume(f'crown_plume_{i+1}','crown',[(s*3,head_y+4,-54),
              (s*7,head_y+16+5*(1-abs(s)),-47),
              (s*10,head_y+23+9*(1-abs(s)),-36),
              (s*13,head_y+22+13*(1-abs(s)),-24)], [2.6,2.0,.8,.15], i%3==0)

    # Independent mantle and scapular feathers cover the back in overlapping rows.
    for s in (-1,1):
        for row in range(3):
            for i in range(6):
                x=s*(5+row*3); z=-19+i*8
                plume(f'mantle_{s}_{row}_{i}','body',[(x,body_y+13-row*2,z),
                      (x+s*4,body_y+9-row*2,z+10), (x+s*5,body_y+5-row*2,z+18)],
                      [4.5,3,.3])

    # Wings are authored folded alongside the body. Their roots unfold during attacks.
    for side,s in [('l',-1),('r',1)]:
        shoulder=(s*13,body_y+7,-13)
        upper=bone('wing_'+side,'body',shoulder)
        segment(upper,shoulder,(s*20,body_y-1,7),7,8,'ink')
        elbow=(s*20,body_y-1,7)
        fore=bone('wing_'+side+'_fore','wing_'+side,elbow)
        segment(fore,elbow,(s*19,body_y-11,27),5,6,'soft')
        bone('wing_'+side+'_palm','wing_'+side+'_fore',(s*19,body_y-11,27))
        for i in range(10):
            anchor=(s*(18+.65*(i%3)),body_y-3-i*1.3,-2+i*3.1)
            plume(f'wing_{side}_finger_{i+1}','wing_'+side+'_fore',
                  [anchor,(s*(21+i*.22),body_y-15-i,anchor[2]+16),
                   (s*(18+i*.28),body_y-22-i*.6,anchor[2]+35),
                   (s*(16+i*.35),body_y-26-i*.6,anchor[2]+46)], [6.2,5,2.5,.2])
        for row in range(2):
            for i in range(7):
                plume(f'wing_{side}_covert_{row}_{i}','wing_'+side,
                      [(s*(14+row*3),body_y+6-row*6,-14+i*5),
                       (s*(21+row),body_y-2-row*6,-3+i*5),
                       (s*(20+row),body_y-10-row*6,7+i*5)], [5.5,4,.25])
        tip = bone('emitter_wing_'+side,'wing_'+side+'_finger_10',
                   (s*19,body_y-32,72))
        tip['locators']={'ink_tip':[s*19,body_y-32,72]}

    bone('tail','body',(0,body_y+3,31))
    for i in range(11):
        s=(i-5)/5; x=s*11; length=62+(1-abs(s))*28
        plume(f'tail_feather_{i+1}','tail',[(x,body_y+3,29),
              (x*1.4,body_y-3,47),(x*1.7,body_y-17,72),
              (x*1.8,body_y-35,29+length),(x*2,body_y-41,35+length)],
              [4.6,5.3,3.8,1.4,.15],True)
    bone('emitter_tail','tail',(0,body_y-37,115))

    # Continuous long legs, articulated hocks, three forward toes and a rear toe.
    for side,x in [('l',-7),('r',7)]:
        leg=bone('leg_'+side,'root',(x,body_y-9,5))
        segment(leg,(x,body_y-9,5),(x,68,9),3.4,3.6,'ink')
        knee=bone('hock_'+side,'leg_'+side,(x,68,9))
        box(knee,(x-2.5,65,6),(5,6,6),'claw')
        segment(knee,(x,66,9),(x,8,0),2.6,2.8,'ink')
        for i in range(12):
            box(knee,(x-1.45,12+i*4.1,-.6+i*.58),(2.9,.5,3.1),'silver' if i%4==0 else 'claw')
        foot=bone('foot_'+side,'hock_'+side,(x,7,0))
        bone('emitter_foot_'+side,'foot_'+side,(x,4,-12))
        box(foot,(x-2,4,-3),(4,4,7),'claw')
        for j in range(4):
            if j==3:
                pts=[(x,5,1),(x+1,3,9),(x+2,2,15)]
            else:
                spread=(j-1)*8
                pts=[(x,5,-1),(x+spread*.5,3,-10),(x+spread,2,-23+abs(j-1)*4)]
            toe=bone(f'toe_{side}_{j+1}','foot_'+side,pts[0])
            segment(toe,pts[0],pts[1],1.9,1.5,'claw')
            segment(toe,pts[1],pts[2],1.2,1,'claw')
            end=pts[-1]; claw=bone(f'glow_claw_{side}_{j+1}',toe['name'],end)
            segment(claw,end,(end[0],end[1]+.5,end[2]+(3 if j==3 else -3)),.7,.6,'white')
    return BONES


def pack_texture(bones):
    atlas=Image.new('RGBA',(2048,2048),(8,8,8,255)); draw=ImageDraw.Draw(atlas)
    x,y,row=2,2,0; rng=random.Random(20261018)
    for b in bones:
        for c in b.get('cubes',[]):
            mat=c.pop('_material'); c['_preview_gray']=MATERIALS[mat]
            sx,sy,sz=c['size']; c['uv']={}
            for face,fw,fh in [('north',sx,sy),('south',sx,sy),('east',sz,sy),
                               ('west',sz,sy),('up',sx,sz),('down',sx,sz)]:
                w,h=max(2,math.ceil(fw)),max(2,math.ceil(fh))
                if x+w+2>2048: x=2;y+=row+2;row=0
                if y+h+2>2048: raise ValueError('UV atlas overflow')
                c['uv'][face]={'uv':[x,y],'uv_size':[w,h]}
                gray=MATERIALS[mat];draw.rectangle((x,y,x+w-1,y+h-1),fill=(gray,gray,gray,255))
                if mat in ('feather','soft','claw'):
                    for k in range(1,max(2,w//3)):
                        px=x+rng.randrange(w); v=min(255,gray+rng.randint(3,19))
                        draw.line((px,y,px,y+h-1),fill=(v,v,v,255))
                    if w>=4:
                        draw.line((x,y,x,y+h-1),fill=(156,156,156,255))
                x+=w+2;row=max(row,h)
    TEX.parent.mkdir(parents=True,exist_ok=True);atlas.save(TEX)


def write_animations():
    def loop(a,b): return {'0.0':a,'2.5':b,'5.0':a}
    idle={'body':{'rotation':loop([0,0,-.3],[0,0,.3])},
          'neck_low':{'rotation':loop([0,-1,0],[1,1,0])},
          'neck_mid':{'rotation':loop([0,1,0],[-1,-1,0])}}
    for side,s in [('l',-1),('r',1)]:
        idle['wing_'+side]={'rotation':loop([0,0,0],[0,s*.8,0])}
        idle['wing_'+side+'_fore']={'rotation':loop([0,0,0],[0,s*.6,.5])}
    for i in range(11):
        idle[f'tail_feather_{i+1}']={'rotation':loop([0,-.4,0],[1.2,.4,(-1)**i*.7])}
    for i in range(9):
        idle[f'crown_plume_{i+1}']={'rotation':loop([0,-1,0],[2,1,(-1)**i*1.5])}
    animations={'idle':{'loop':True,'animation_length':5,'bones':idle}}
    for clip,sides in [('wing_slash_left',[('l',-1)]),('wing_slash_right',[('r',1)]),
                       ('wing_double',[('l',-1),('r',1)])]:
        parts={'body':{'rotation':{'0.0':[0,0,0],'.35':[-3,0,0],'.7':[4,0,0],'1.1':[0,0,0]}}}
        for side,s in sides:
            parts['wing_'+side]={'rotation':{'0.0':[0,0,0],'.24':[-18,s*75,s*25],
                  '.53':[14,-s*35,-s*35],'.85':[0,s*30,s*10],'1.1':[0,0,0]}}
            parts['wing_'+side+'_fore']={'rotation':{'0.0':[0,0,0],'.24':[0,s*30,0],
                  '.53':[12,-s*50,0],'1.1':[0,0,0]}}
            for i in range(10):
                parts[f'wing_{side}_finger_{i+1}']={'rotation':{'0.0':[0,0,0],
                      '.24':[i*.9,s*(i-4)*3,0],'.53':[-i*.8,-s*(i-4)*4,s*5],'1.1':[0,0,0]}}
        animations[clip]={'animation_length':1.1,'bones':parts}
    walk=copy.deepcopy(idle)
    for side,s in [('l',-1),('r',1)]:
        walk['leg_'+side]={'rotation':{'0.0':[s*7,0,0],'.6':[-s*7,0,0],'1.2':[s*7,0,0]}}
        walk['foot_'+side]={'rotation':{'0.0':[-s*5,0,0],'.6':[s*5,0,0],'1.2':[-s*5,0,0]}}
    # Use 1.2-second keyframes for all walk channels, including feather sway.
    for channels in walk.values():
        for key,frames in channels.items():
            if '5.0' in frames:
                channels[key]={'0.0':frames['0.0'],'.6':frames['2.5'],'1.2':frames['5.0']}
    animations['walk']={'loop':True,'animation_length':1.2,'bones':walk}
    flap={}
    for side,s in [('l',-1),('r',1)]:
        flap['wing_'+side]={'rotation':{'0.0':[0,0,0],'.25':[-15,s*80,s*30],
             '.55':[12,s*60,-s*35],'.8':[-15,s*80,s*30],'1.1':[0,0,0]}}
        flap['wing_'+side+'_fore']={'rotation':{'0.0':[0,0,0],'.25':[0,s*25,0],'.55':[0,-s*25,0],'1.1':[0,0,0]}}
    animations['ink_flap']={'animation_length':1.1,'bones':flap}
    flight={}
    for side,s in [('l',-1),('r',1)]:
        flight['wing_'+side]={'rotation':{'0.0':[0,s*75,s*15],'.4':[0,s*85,-s*25],'.8':[0,s*75,s*15]}}
        flight['wing_'+side+'_fore']={'rotation':{'0.0':[0,0,s*8],'.4':[0,0,-s*10],'.8':[0,0,s*8]}}
    animations['phantom_flight']={'loop':True,'animation_length':.8,'bones':flight}
    sweep=copy.deepcopy(animations['wing_double'])
    sweep['animation_length']=3.25
    for channels in sweep['bones'].values():
        for key,frames in channels.items():
            channels[key]={str(round(float(t)*2.5,3)):v for t,v in frames.items()}
    animations['spectral_sweep']=sweep
    ANIM.write_text(json.dumps({'format_version':'1.8.0','animations':animations},indent=2)+'\n',encoding='utf-8')


def matrix(pivot, angles):
    p=np.array(pivot,dtype=float);x,y,z=np.radians(angles)
    rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
    ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
    rz=np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
    r=rz@ry@rx;out=np.eye(4);out[:3,:3]=r;out[:3,3]=p-r@p
    return out


def render_preview(bones):
    im=Image.new('RGB',(1700,1350),(218,217,208));draw=ImageDraw.Draw(im)
    faces=[(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)]
    for panel,(angle,title) in enumerate([(135,'FOLDED / THREE QUARTER'),(90,'FOLDED / SIDE')]):
        camera=matrix((0,0,0),(-7,angle,0))[:3,:3]
        transforms={};polys=[]
        for b in bones:
            transform=transforms.get(b.get('parent'),np.eye(4))@matrix(b['pivot'],b.get('rotation',[0,0,0]))
            transforms[b['name']]=transform
            for c in b.get('cubes',[]):
                origin=np.array(c['origin']);size=np.array(c['size'])
                vertices=np.array([origin+size*np.array([x,y,z]) for x in (0,1) for y in (0,1) for z in (0,1)])
                local=matrix(c.get('pivot',c['origin']),c.get('rotation',[0,0,0]))
                world=(transform@local@np.column_stack((vertices,np.ones(8))).T).T[:,:3]
                points=(camera@world.T).T
                for face in faces:
                    q=points[list(face)];n=np.cross(q[1]-q[0],q[2]-q[0]);n/=max(.001,np.linalg.norm(n))
                    shade=.72+.28*abs(n[1]);v=int(c['_preview_gray']*shade)
                    polys.append((q[:,2].mean(),[(panel*850+390+p[0]*3.15,1160-p[1]*3.8) for p in q],(v,v,v)))
        for _,pts,color in sorted(polys,key=lambda item:item[0],reverse=True):draw.polygon(pts,fill=color)
        draw.text((panel*850+40,40),title,fill=(30,30,30))
    draw.text((40,1300),'Actual exported geometry / user height landmarks / folded wings / segmented quills and toes',fill=(30,30,30))
    PREVIEW.parent.mkdir(parents=True,exist_ok=True);im.save(PREVIEW)


def main():
    Image.new('RGBA',(1,1),(255,255,255,255)).save(ASSETS/'textures/entity/ink_crane_primitive.png')
    bones=build_bones();pack_texture(bones);render_preview(bones)
    for b in bones:
        for c in b.get('cubes',[]):c.pop('_preview_gray',None)
    data={'format_version':'1.12.0','minecraft:geometry':[{'description':{
          'identifier':'geometry.tnc.ink_crane_boss','texture_width':2048,'texture_height':2048,
          'visible_bounds_width':20,'visible_bounds_height':20,'visible_bounds_offset':[0,7,0]},'bones':bones}]}
    GEO.parent.mkdir(parents=True,exist_ok=True);ANIM.parent.mkdir(parents=True,exist_ok=True)
    GEO.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8');write_animations()
    # Independent baked instances prevent phantom wing hiding/scaling from
    # leaking into the real boss through GeckoLib's shared geometry cache.
    for source,name,pivot in [(GEO,'ink_crane_phantom',123),
            (ASSETS/'geo/entity/ink_abyss_drake.geo.json','ink_abyss_drake_phantom',24)]:
        phantom=json.loads(source.read_text(encoding='utf-8'))
        geometry=phantom['minecraft:geometry'][0]
        geometry['description']['identifier']='geometry.tnc.'+name
        for b in geometry['bones']:
            if b['name']=='root':b['pivot']=[0,pivot,0]
        (ASSETS/f'geo/entity/{name}.geo.json').write_text(json.dumps(phantom,indent=2)+'\n',encoding='utf-8')
    print(f'{len(bones)} bones, {sum(len(b.get("cubes",[])) for b in bones)} cubes; unique per-face UV atlas 2048x2048')
    print(PREVIEW)
    if (ROOT/'model-source/ink-crane/author-attacks-v4/attack-examples.animation.json').exists():
        from refine_ink_crane_attacks import refine
        refine()


if __name__=='__main__':main()
