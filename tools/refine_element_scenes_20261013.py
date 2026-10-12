from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1];backup=root/'work/element-scenes-source-baseline';paths=[]
def original(path):
    p=root/path;b=backup/path;b.parent.mkdir(parents=True,exist_ok=True)
    if not b.exists():b.write_bytes(p.read_bytes())
    return json.loads(b.read_text(encoding='utf-8-sig'))
def save(path,data):
    (root/path).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8');paths.append(path)
for tier,name in enumerate(['earth_shot','earth_ball','rock_cannon','ten_thousand_rocks','earth_god_spear'],1):
    path=f'src/main/resources/data/tnc/spells/{name}.json';s=original(path)
    if tier<=2:s['release']['target']['projectile']['projectile']['client_data']['model']['scale']*=2
    else:s['release']['target']={'type':'SELF'};s['impact']=[];s.pop('area_impact',None)
    save(path,s)
folder='modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/spells'
for name in ['earth_spike','rock_burst','earth_rift','star_quake']:
    path=f'{folder}/{name}.json';s=original(path);s['release']['target']={'type':'SELF'};s['impact']=[];s.pop('area_impact',None);save(path,s)
for name in ['mud','pit','mire','earth_flow','earth_core_burst']:
    path=f'{folder}/{name}.json';s=original(path);s['range']*=2;s['release']['target']['cloud']['volume']['radius']*=2;save(path,s)
path='src/main/java/com/tnc/tnc/magic/WindEarthVisualRules.java';b=backup/path;b.parent.mkdir(parents=True,exist_ok=True)
if not b.exists():b.write_bytes((root/path).read_bytes())
source=b.read_text(encoding='utf-8-sig')
source=re.sub(r'new Spec\(5, (\d), ([\d.]+)D',lambda m:f'new Spec(5, {m[1]}, {float(m[2])*2:.3f}D',source)
(root/path).write_text(source,encoding='utf-8')
textures={'stone':'minecraft:block/deepslate','vein':'minecraft:block/sandstone','particle':'minecraft:block/deepslate'}
elements=[]
def cube(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
elements.append(cube([5,5,0],[11,11,16],'stone'))
for z,width in [(16,7),(20,5),(24,3),(27,1.5),(29,.6),(31,.12)]:elements.append(cube([8-width/2,8-width/2,z],[8+width/2,8+width/2,min(32,z+4)],'stone'))
elements+= [cube([4.8,7,1],[5.3,9,20],'vein'),cube([10.7,7,1],[11.2,9,20],'vein')]
save('src/main/resources/assets/tnc/models/projectile/earth_spear_sharp.json',{'ambientocclusion':True,'textures':textures,'elements':elements})
elements=[]
for y,width in [(0,10),(4,8),(7,6),(10,4),(12,2.5),(14,1),(15.5,.12)]:
    elements.append(cube([8-width/2,y,8-width/2],[8+width/2,min(16,y+4),8+width/2],'stone'))
save('src/main/resources/assets/tnc/models/projectile/earth_spike_sharp.json',{'ambientocclusion':True,'textures':textures,'elements':elements})
for p in (root/'src/main/resources/assets/tnc/models/projectile').glob('earth_*.json'):
    path=p.relative_to(root).as_posix();s=json.loads(p.read_text(encoding='utf-8-sig'));changed=False
    for element in s['elements']:
        for face in element['faces'].values():
            if 'uv' not in face:face['uv']=[0,0,16,16];changed=True
    if changed:
        original(path);save(path,s)
(root/'work/element-scenes-assets.json').write_text(json.dumps(paths,ensure_ascii=False,indent=2),encoding='utf-8')
print('Authored sharp spear; doubled two projectiles and five mud radii; moved staged earth attacks off engine cloud/projectile releases.')
