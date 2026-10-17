"""Keep the original UV islands while thickening and faceting the overlord's rig."""
import copy
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GEO = ROOT / 'src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json'
ANIM = ROOT / 'src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json'


def faces(cube):
    if isinstance(cube['uv'], dict):
        return
    u, v = cube['uv']; x, y, z = cube['size']
    cube['uv'] = {
        'east': {'uv': [u, v+z], 'uv_size': [z, y]},
        'north': {'uv': [u+z, v+z], 'uv_size': [x, y]},
        'west': {'uv': [u+z+x, v+z], 'uv_size': [z, y]},
        'south': {'uv': [u+z+x+z, v+z], 'uv_size': [x, y]},
        'up': {'uv': [u+z, v], 'uv_size': [x, z]},
        'down': {'uv': [u+z+x, v+z], 'uv_size': [x, -z]},
    }


def facet(cube, rotation):
    cube['pivot'] = [round(o+s/2, 4) for o, s in zip(cube['origin'], cube['size'])]
    cube['rotation'] = rotation


def geometry():
    data = json.loads(GEO.read_text(encoding='utf-8-sig'))
    g = data['minecraft:geometry'][0]
    if any(b['name'] == 'shinright' for b in g['bones']):
        return
    bones = g['bones']
    for bone in bones:
        for cube in bone.get('cubes', []):
            faces(cube)
        name = bone['name']
        factor = 1.65 if name == 'body' else 1.25 if name.startswith(('arm', 'pauldron')) else 1.12 if name == 'head' else 1
        if factor != 1:
            for cube in bone.get('cubes', []):
                cube['origin'][2] *= factor
                cube['size'][2] *= factor
                if 'pivot' in cube: cube['pivot'][2] *= factor
        if name == 'cape':
            bone['pivot'][2] += 6
            for cube in bone['cubes']: cube['origin'][2] += 6
        if name == 'body':
            for cube in bone['cubes']:
                x,y,z=cube['origin']; w,h,d=cube['size']
                if d < 5 and y >= 43:
                    facet(cube, [-8, 0, (-14 if x+w/2 < -1 else 14 if x+w/2 > 1 else 0)])
            # Slanted waist plates create a V-shaped skirt with open leg clearance.
            template=copy.deepcopy(bone['cubes'][13])
            for sign in [-1,1]:
                plate=copy.deepcopy(template)
                plate['origin']=[sign*8-5,29,-14];plate['size']=[10,11,3]
                facet(plate,[-12,sign*12,sign*18]);bone['cubes'].append(plate)
        if name.startswith('pauldron'):
            sign=-1 if name.endswith('right') else 1
            for i,cube in enumerate(bone['cubes']):
                facet(cube,[-8 if i<5 else 0,sign*8,sign*(22 if i<5 else 12 if i<7 else 18)])
        if name.startswith('arm'):
            sign=-1 if name.endswith('right') else 1
            for cube in bone['cubes']:
                if cube['size'][1] < 12: facet(cube,[-5,sign*8,sign*9])
    for side, offset in [('right',-2.5),('left',2.5)]:
        thigh=next(b for b in bones if b['name']=='leg'+side)
        thigh['pivot'][0]+=offset
        shin={'name':'shin'+side,'parent':thigh['name'],'pivot':[thigh['pivot'][0],20,0],'cubes':[]}
        foot={'name':'foot'+side,'parent':shin['name'],'pivot':[thigh['pivot'][0],5,-1],'cubes':[]}
        kept=[]
        for cube in thigh['cubes']:
            cube['origin'][0]+=offset
            y=cube['origin'][1]
            if y<5:
                # Parallel, narrower boots remove the old touching, splayed feet.
                center=thigh['pivot'][0]
                cube['origin'][0]=center+(cube['origin'][0]-center)*.78
                cube['size'][0]*=.78
                foot['cubes'].append(cube)
            elif y<20:
                if y>=13: facet(cube,[-9,0,0])
                shin['cubes'].append(cube)
            else: kept.append(cube)
        thigh['cubes']=kept;thigh.pop('rotation',None)
        bones.extend([shin,foot])
    g['description'].update(visible_bounds_width=6,visible_bounds_height=7,visible_bounds_offset=[0,3,0])
    GEO.write_bytes((json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode())


def armor_contours():
    data=json.loads(GEO.read_text(encoding='utf-8-sig'))
    bones=data['minecraft:geometry'][0]['bones']
    body=next(b for b in bones if b['name']=='body');metal=copy.deepcopy(body['cubes'][1]['uv'])
    output=[]
    for cube in body['cubes']:
        x,y,z=cube['origin'];w,h,depth=cube['size']
        if w>=28 and h>=16 and z<-12 and depth<5:
            for sign,width in [(0,14),(-1,10),(1,10)]:
                plate=copy.deepcopy(cube);plate['size']=[width,h if sign==0 else h-2,depth]
                plate['origin']=[-width/2+sign*10,y+(0 if sign==0 else 2),z-(1 if sign==0 else 0)]
                plate['uv']=copy.deepcopy(metal);plate['pivot']=[plate['origin'][0]+width/2,y+h/2,z+depth/2]
                plate['rotation']=[-8,sign*18,sign*12];output.append(plate)
        else:
            if w>=10 and y>=54:
                for face in ['north','south']:cube['uv'][face]=copy.deepcopy(metal[face])
            output.append(cube)
    body['cubes']=output
    for bone in bones:
        if bone['name'].startswith('pauldron'):
            sign=-1 if bone['name'].endswith('right') else 1
            for i,cube in enumerate(bone['cubes'][:5]):
                cx=cube['origin'][0]+cube['size'][0]/2;cz=cube['origin'][2]+cube['size'][2]/2
                cube['origin']=[cx-.9,74,cz-1.15];cube['size'][0]=1.8;cube['size'][2]=2.3
                cube['pivot']=[cx,74,cz];cube['rotation']=[-12,sign*8,sign*(25+i*7)]
        if bone['name']=='head':
            for cube in bone['cubes']:
                if cube['origin'][1]>=91 and 'rotation' not in cube:
                    cx=cube['origin'][0]+cube['size'][0]/2;cube['size'][0]*=.6;cube['origin'][0]=cx-cube['size'][0]/2
                    cube['pivot']=[cx,cube['origin'][1],cube['origin'][2]];cube['rotation']=[-5,0,0 if abs(cx)<1 else (-12 if cx<0 else 12)]
    GEO.write_bytes((json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode())


def animations():
    data=json.loads(ANIM.read_text(encoding='utf-8-sig'))
    idle={'loop':True,'animation_length':4,'bones':{}}
    def keys(values):return {str(t):v for t,v in values}
    for side in ['right','left']:
        for prefix in ['leg','shin','foot']:
            idle['bones'][prefix+side]={'rotation':[0,0,0]}
    idle['bones'].update({
        'body':{'rotation':keys([(0,[-1,0,0]),(2,[0,0,0]),(4,[-1,0,0])]),'scale':keys([(0,[1,1,1]),(2,[1.008,1.004,1.012]),(4,[1,1,1])])},
        'head':{'rotation':keys([(0,[0,0,0]),(2,[-1,1,0]),(4,[0,0,0])])},
        'armright':{'rotation':keys([(0,[0,0,-3]),(2,[-1,0,-4]),(4,[0,0,-3])])},
        'armleft':{'rotation':keys([(0,[0,0,3]),(2,[-1,0,4]),(4,[0,0,3])])},
        'cape':{'rotation':keys([(0,[3,0,0]),(2,[5,0,0]),(4,[3,0,0])])},
    })
    walk={'loop':True,'animation_length':2.2,'bones':{}}
    for side,phase in [('right',0),('left',math.pi)]:
        hip={};knee={};foot={}
        for i in range(17):
            t=round(i*2.2/16,4);angle=i*math.pi*2/16+phase
            z=5.6*math.cos(angle);lift=1.5*max(0,math.sin(angle));y=-28.3+lift
            dist=min(28.99,math.hypot(y,z));l1,l2=14,15
            a=-math.atan2(z,-y)+math.acos((l1*l1+dist*dist-l2*l2)/(2*l1*dist))
            b=-math.acos((dist*dist-l1*l1-l2*l2)/(2*l1*l2))
            hip[str(t)]=[round(math.degrees(a),3),0,0]
            knee[str(t)]=[round(math.degrees(b),3),0,0]
            foot[str(t)]=[round(-math.degrees(a+b),3),0,0]
        walk['bones']['leg'+side]={'rotation':hip}
        walk['bones']['shin'+side]={'rotation':knee}
        walk['bones']['foot'+side]={'rotation':foot}
    walk['bones'].update({
        'root':{'position':[0,-.7,0]},
        'body':{'rotation':keys([(0,[-1,1,0]),(.55,[-2,0,.4]),(1.1,[-1,-1,0]),(1.65,[-2,0,-.4]),(2.2,[-1,1,0])])},
        'head':{'rotation':keys([(0,[1,-1,0]),(1.1,[1,1,0]),(2.2,[1,-1,0])])},
        'armright':{'rotation':keys([(0,[-6,0,-3]),(1.1,[6,0,-3]),(2.2,[-6,0,-3])])},
        'armleft':{'rotation':keys([(0,[6,0,3]),(1.1,[-6,0,3]),(2.2,[6,0,3])])},
        'cape':{'rotation':keys([(0,[6,0,0]),(1.1,[9,0,0]),(2.2,[6,0,0])])},
    })
    data['animations']['idle']=idle;data['animations']['walk']=walk
    ANIM.write_bytes((json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode())


if __name__=='__main__':
    geometry();armor_contours();animations()
