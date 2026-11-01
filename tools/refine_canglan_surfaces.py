"""Add bounded armour relief and per-part UVs without regenerating authored motion.

The 1024px atlas uses the existing 256-unit logical UV space: one 64-unit
tile is 256 actual pixels. No image painting/cropping is done in this script.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tnc'
SOURCE = ROOT / 'model-source/canglan_warlord/surface-refinement-20261022'
GEO = ASSETS / 'geo/entity/canglan_warlord.geo.json'
TEX = ASSETS / 'textures/entity/canglan_warlord.png'
ANIM = ASSETS / 'animations/entity/canglan_warlord.animation.json'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def uv(tile, width, height, face):
    # Keep physical aspect ratio; broad surfaces sample a centred engraved panel,
    # narrow edges sample brushed material instead of a squashed full medallion.
    ratio = max(.06, min(16, width / max(.01, height)))
    w, h = (58, 58 / ratio) if ratio >= 1 else (58 * ratio, 58)
    x, y = (tile % 4) * 64, (tile // 4) * 64
    return {'uv': [round(x + (64-w)/2,3), round(y + (64-h)/2,3)],
            'uv_size': [round(w,3), round(h,3)]}

def remap(cube, tile):
    x,y,z = cube['size']
    dimensions = {'north':(x,y),'south':(x,y),'east':(z,y),'west':(z,y),'up':(x,z),'down':(x,z)}
    cube['uv'] = {face:uv(tile,*size,face) for face,size in dimensions.items()}

def old_material(cube):
    u,v = cube['uv']['north']['uv']
    return ('steel' if u<128 else 'mist') if v<128 else ('edge' if u<128 else 'gale')

def tile_for(bone, cube, index):
    material=old_material(cube)
    if material=='gale': return 13 if bone in ('eyes','helmet') else 12
    if material=='edge':
        # Central beast relief is dark bronze, not another large flat gold slab.
        return 5 if bone in ('chest','waist','jaw') and max(cube['size'])>4 else (6 if index%5==0 else 4)
    if bone.startswith('cape_'): return 9 if material=='mist' or index>1 else 8
    if bone.startswith('banner_') and bone!='banner_pole': return 10
    if bone.startswith('crest_'): return 8 if material=='mist' else 12
    if material=='mist': return 15 if bone in ('pelvis','waist') else (8 if bone.startswith(('arm_','thigh_')) else 3)
    if index==0 and bone.startswith(('arm_','fore_','thigh_','neck','torso')): return 7
    if bone.startswith(('pauldron_','skirt_')): return 1 if index%3 else 3
    if bone in ('head','jaw'): return 2 if index%2 else 6
    if bone.startswith('helmet'): return 0 if index%3 else 2
    return [0,2,3][index%3]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--atlas',type=Path,required=True)
    args=parser.parse_args()
    with Image.open(args.atlas) as image:
        assert image.size==(1024,1024), image.size
        assert image.mode in ('RGB','RGBA')
    original=json.loads((SOURCE/'before.geo.json').read_text(encoding='utf-8'))
    geometry=copy.deepcopy(original); bones=geometry['minecraft:geometry'][0]['bones']; by_name={b['name']:b for b in bones}
    animation_hash=sha(ANIM)
    assert animation_hash==sha(SOURCE/'unchanged.animation.json'), 'Authored animation changed since snapshot'
    for bone in bones:
        for i,cube in enumerate(bone.get('cubes',[])):
            remap(cube,tile_for(bone['name'],cube,i))
    additions=[]
    def box(bone,centre,size,tile,rotation=None):
        # Same template coordinate system as the established sculpt generator.
        c=[v*2.75 for v in centre];s=[v*2.75 for v in size]
        cube={'origin':[round(c[i]-s[i]/2,3) for i in range(3)],'size':[round(v,3) for v in s]}
        if rotation is not None:cube.update(pivot=[round(v,3) for v in c],rotation=list(rotation))
        remap(cube,tile);by_name[bone].setdefault('cubes',[]).append(cube);additions.append(bone)
    # Layered mask cheek plates and swept brow: a readable fierce face at distance.
    for side in (-1,1):
        for i in range(3):
            box('head',(side*(2.1+i*.38),29.3-i*.63,-5.03),(.95,.38,.30),6,(0,0,side*(18+i*9)))
        box('head',(side*2.1,31.55,-5.05),(2.45,.24,.28),5,(0,0,side*-12))
        box('head',(side*3.05,28.0,-4.95),(.25,1.4,.32),4,(0,0,side*18))
        # Split chest inlays frame the existing beast relief, not cover it.
        for i in range(3):
            box('chest',(side*(3.0+i*.30),23.6-i*.75,-3.96),(1.2,.24,.28),6,(0,0,side*28))
        box('chest',(side*3.7,21.15,-3.98),(.22,1.35,.28),4)
        box('chest',(side*3.7,20.6,-4.14),(.40,.40,.25),14,(0,0,45))
        # Shoulder vanes keep the original silhouette and attach to animated shoulders.
        for i in range(3):
            box('pauldron_'+str(side),(side*(5.5+i*.95),25.9-i*.33,-3.59),(.34,1.45,.32),6,(0,0,side*-26))
            box('pauldron_'+str(side),(side*(5.2+i*.85),24.15-i*.30,-3.29),(.85,.18,.26),4,(0,0,side*14))
        # Forearm ribs and a narrow enamel channel, no changes to palms or anchors.
        for i in range(3):
            box('fore_'+str(side),(side*6.1,14.8-i*.72,-2.66),(2.45,.20,.25),6,(0,0,side*5))
        box('fore_'+str(side),(side*6.1,14.0,-2.89),(.28,2.45,.18),12)
        box('arm_'+str(side),(side*6.1,18.0,-2.93),(2.6,.25,.25),4)
        # Split knee and greave trims create shadows against the slate plates.
        for i in range(3):
            box('shin_'+str(side),(side*2.7,5.8-i*.62,-3.04),(2.8-i*.36,.19,.28),6,(0,0,side*5))
        box('thigh_'+str(side),(side*2.7,11.25,-3.0),(2.8,.25,.32),4)
        for i in range(3):
            box('foot_'+str(side),(side*2.7,2.95,-2.3-i*.55),(3.5,.25,.24),6)
    # A shallow central nose ridge breaks the former flat rectangular mask.
    box('head',(0,30.1,-5.40),(.60,2.1,.50),2)
    box('head',(0,29.4,-5.72),(1.15,.32,.40),6)
    # Real cord ties on front/back tassets and visible cloth edges on the cape.
    for bone,z in (('skirt_f',-3.2),('skirt_b',3.2)):
        for side in (-1,1):
            for i in range(3):box(bone,(side*1.45,11.2-i*1.35,z),(.18,.9,.20),15)
    for bone,y,z,w in (('cape_a',21.8,3.55,9.3),('cape_b',18,3.95,8.7),('cape_c',14.2,4.2,8),('cape_d',10.6,4.35,7.2)):
        for side in (-1,1):box(bone,(side*(w*.5-.16),y,z+.53),(.18,4.1,.14),4)
    count=sum(len(b.get('cubes',[])) for b in bones)
    assert count<=560, count  # Ten echoes can share this geometry; do not explode the budget.
    # All original geometry, hierarchy, rest rotations and pivot coordinates are intact.
    for old,new in zip(original['minecraft:geometry'][0]['bones'],bones):
        assert {k:v for k,v in old.items() if k!='cubes'}=={k:v for k,v in new.items() if k!='cubes'}
        for before,after in zip(old.get('cubes',[]),new.get('cubes',[])):
            assert {k:v for k,v in before.items() if k!='uv'}=={k:v for k,v in after.items() if k!='uv'}
    GEO.write_text(json.dumps(geometry,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
    shutil.copyfile(args.atlas,TEX)
    assert sha(ANIM)==animation_hash
    report={'bones':len(bones),'beforeCubes':sum(len(b.get('cubes',[])) for b in original['minecraft:geometry'][0]['bones']),
            'afterCubes':count,'addedReliefCubes':len(additions),'atlasPixels':1024,'logicalUVSize':256,
            'animationSHA256Unchanged':animation_hash,'atlasSHA256':sha(TEX),'geoSHA256':sha(GEO),
            'originalBoneAndCubeTransformsPreserved':True,'imageWorkflow':'User-approved deterministic procedural atlas; image_gen attempt did not return'}
    (SOURCE/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
