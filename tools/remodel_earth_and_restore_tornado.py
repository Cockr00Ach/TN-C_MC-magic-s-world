"""Restore the saved pre-overhaul gale and author textured earth display models."""
from pathlib import Path
import json, shutil
ROOT=Path(__file__).resolve().parents[1]
old=ROOT/'work/wind-earth-source-baseline'
paths=[]
for family in ['magic/TNTornadoEntity','magic/TNWindBladeEntity','client/TNTornadoRenderer','client/TNWindBladeRenderer']:
    path='src/main/java/com/tnc/tnc/'+family+'.java'
    shutil.copy2(old/path,ROOT/path)
    paths.append(path)
for name in ['gale','great_gale','vast_gale','giant_gale','wind_god_gale']:
    path=f'src/main/resources/data/tnc/spells/{name}.json'
    shutil.copy2(old/path,ROOT/path);paths.append(path)
path='src/main/resources/assets/tnc/models/projectile/windblade.json'
shutil.copy2(old/path,ROOT/path);paths.append(path)

textures={'stone':'minecraft:block/stone','dark':'minecraft:block/deepslate','vein':'minecraft:block/sandstone',
          'soil':'minecraft:block/coarse_dirt','mud':'minecraft:block/rooted_dirt',
          'wood':'minecraft:block/oak_log','leaf':'minecraft:block/moss_block','particle':'minecraft:block/stone'}
def cube(a,b,texture,angle=0,axis='y',origin=(8,0,8)):
    out={'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ['north','south','east','west','up','down']}}
    if angle:out['rotation']={'origin':list(origin),'axis':axis,'angle':angle,'rescale':False}
    return out

pillar=[cube([4,0,4],[12,9,12],'dark'),cube([5,7,5],[11,14,11],'stone'),
        cube([6,12,6],[10,16,10],'stone'),cube([3.5,1,5],[4.5,7,11],'vein'),
        cube([11.5,2,5],[12.5,8,11],'vein'),cube([5,8,4.5],[6,13,5.5],'vein'),
        cube([3,0,3],[13,2,13],'stone'),cube([2,0,6],[5,3,10],'stone',22.5)]
mud=[cube([0,0,0],[16,1.2,16],'soil'),cube([1,1,1],[15,2.1,15],'mud'),
     cube([2,2,3],[13,2.8,12],'soil'),cube([0,1,7],[3,3.5,11],'dark'),
     cube([12,1,1],[16,3,5],'stone'),cube([5,2,5],[10,3.2,9],'mud')]
root=[cube([6,0,6],[10,10,10],'wood'),cube([8,4,7],[11,11,9],'wood',-22.5,'z',(8,4,8)),
      cube([5,3,7],[8,10,9],'wood',22.5,'z',(8,3,8)),
      cube([1,0,7],[8,2,9],'wood'),cube([8,0,6],[15,2,8],'wood',22.5),
      cube([4,9,5],[9,12,10],'leaf'),cube([10,10,6],[14,13,10],'leaf'),
      cube([6,11,6],[11,14,11],'leaf')]
for name,elements in [('earth_pillar',pillar),('earth_mud_plate',mud),('earth_root',root)]:
    path=f'src/main/resources/assets/tnc/models/projectile/{name}.json'
    (ROOT/path).write_text(json.dumps({'ambientocclusion':True,'textures':textures,'elements':elements},indent=2),encoding='utf-8')
    paths.append(path)
(ROOT/'work/earth-model-assets.json').write_text(json.dumps([p for p in paths if p.startswith('src/main/resources/')],indent=2),encoding='utf-8')
print('Restored tornado, emitted blades and gale spell particles; authored 3 textured earth display models.')
