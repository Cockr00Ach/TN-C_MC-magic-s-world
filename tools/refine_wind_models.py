"""Code-authored sylph silhouette and vanilla-baked feather/ribbon wind effects."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
def write(path,data):path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
def box(a,b,tex):
    return {'from':a,'to':b,'shade':False,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ('up','down','north','south','east','west')}}
textures={'air':'minecraft:block/light_blue_stained_glass','pearl':'minecraft:block/white_concrete','mint':'minecraft:block/sea_lantern','particle':'minecraft:block/light_blue_stained_glass'}
out=ASSETS/'models/wind';out.mkdir(exist_ok=True)
wing=[]
for i in range(9):
    x=8+i*.8;y=2+i*.20;length=8.5-i*.5
    for segment,width in enumerate((1.3,.85,.35)):
        element=box([x+(1.3-width)/2,y+length*segment/3,7.6],[x+(1.3+width)/2,y+length*(segment+1)/3,8.4],'pearl' if i%3==0 else 'air')
        element['rotation']={'origin':[8,2,8],'axis':'z','angle':-22.5,'rescale':False}
        wing.append(element)
write(out/'feather_wing.json',{'ambientocclusion':False,'textures':textures,'elements':wing})
ribbon=[]
for i in range(48):
    a=i*math.pi*2/24;x=8+math.cos(a)*7;z=8+math.sin(a)*7;y=i*.35
    ribbon.append(box([x-.92,y,z-.92],[x+.92,y+.35,z+.92],'air' if i%6 else 'mint'))
write(out/'orbit_ribbon.json',{'ambientocclusion':False,'textures':textures,'elements':ribbon})
path=ASSETS/'models/projectile/windblade.json';blade=json.loads(path.read_text())
blade['textures']['0']='minecraft:block/light_blue_stained_glass'
write(path,blade)
path=ASSETS/'geo/entity/wind_sprite.geo.json';geo=json.loads(path.read_text())
bones=geo['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones}
def cube(origin,size,uv,rotation=None,pivot=None):
    c={'origin':origin,'size':size,'uv':uv}
    # Reuse complete painted atlas regions instead of changing the original
    # cube unwrap dimensions (which exposes transparent, unpainted texels).
    regions={(0,0):([12,6],[6,7]),(0,32):([0,37],[5,5]),(0,20):([2,22],[2,3]),
             (16,0):([18,2],[7,4]),(20,32):([22,34],[2,5]),(32,0):([38,6],[2,2])}
    if tuple(uv) in regions:
        start,extent=regions[tuple(uv)]
        c['uv']={f:{'uv':start,'uv_size':extent} for f in ('north','south','east','west','up','down')}
        if uv==[0,32]:c['uv']['north']={'uv':[5,37],'uv_size':[5,5]}
    if rotation:c.update(rotation=rotation,pivot=pivot)
    return c
by['body']['cubes']=[cube([-1.9,4,-1.6],[3.8,5,3.2],[0,0]),cube([-2.4,2,-1.9],[4.8,2,3.8],[0,0]),cube([-2.8,.4,-2.1],[5.6,1.6,4.2],[0,0])]
by['head']['cubes']=[cube([-2.25,9,-2.25],[4.5,4.5,4.5],[0,32])]
for name,x in (('arm_left',2),('arm_right',-3.1)):
    if name in by:by[name]['cubes']=[cube([x,4.3,-.5],[1.1,4.1,1.1],[20,32])]
by['crest']['pivot']=[0,13,0]
by['crest']['cubes']=[cube([-2.8,12.7,-.7],[1,3.2,1.4],[0,20],[0,0,25],[-2,13,0]),cube([1.8,12.7,-.7],[1,3.2,1.4],[0,20],[0,0,-25],[2,13,0]),cube([-.6,13,-.6],[1.2,3.6,1.2],[0,20])]
for sign,name in ((1,'wing_left'),(-1,'wing_right')):
    by[name]['pivot']=[sign*2,7,0]
    by[name]['cubes']=[]
    for i in range(7):
        length=8.5-i*.65; x=2+i*.8 if sign>0 else -3.2-i*.8
        by[name]['cubes'].append(cube([x,5.3+i*.38,.2],[1.15,length,.75],[16,0],[0,0,-sign*(24+i*7)],[sign*2,7,0]))
by['tail']['cubes']=[cube([-1.4,-3-i*1.8,1.0+i*.7],[2.8-i*.55,3,1.1],[32,0],[12+i*9,0,0],[0,3,1]) for i in range(3)]
# The large secondary slabs obscured the new articulated feather fan.
for name in ('wing_lower_left','wing_lower_right','lower_wing_left','lower_wing_right','wing_left_lower','wing_right_lower'):
    if name in by:by[name]['cubes']=[]
geo['minecraft:geometry'][0]['description']['visible_bounds_width']=4
geo['minecraft:geometry'][0]['description']['visible_bounds_height']=4
write(path,geo)
print('Refined sylph geometry, transparent windblade and two registered-model wind effects.')
