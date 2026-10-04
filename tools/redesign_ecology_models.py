"""Authored native cuboid silhouettes for plants, machines and actual feed piles.

Models share material scale, not geometry. Item artwork is exported separately
from the imagegen masters by export_ecology_art.py.
"""
from pathlib import Path
import json,ast,math,copy
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
T={n:'tnc:block/art_'+n for n in ['leaf','bark','pollen','cotton','pepper','ice','violet','rose','paper','copper','linen','wood','iron','hay','grain','pellets']}
T.update(stone='minecraft:block/deepslate_tiles',glass='minecraft:block/glass',gold='minecraft:block/gold_block',light='minecraft:block/sea_lantern',green='minecraft:block/amethyst_block',white='minecraft:block/quartz_block_side',brick='tnc:block/forge_firebrick')
WRITTEN=[]
def write(name,es,particle='wood',item=False):
    model={'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':dict(T,particle=T[particle]),'elements':es}
    if item:model['display']={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.82,.82,.82]},'ground':{'translation':[0,3,0],'scale':[.45,.45,.45]},'fixed':{'rotation':[0,180,0],'scale':[.7,.7,.7]}}
    p=A/f'models/block/{name}.json';p.write_bytes((json.dumps(model,ensure_ascii=False,indent=2)+'\n').encode());WRITTEN.append(name)
    if item:
        p=A/f'models/item/{name}.json';p.write_bytes((json.dumps({'parent':'tnc:block/'+name},indent=2)+'\n').encode())
def C(lo,hi,mat='leaf',angle=0,axis='z',origin=None):
    w,h,d=[hi[i]-lo[i] for i in range(3)]
    spans={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}
    o={'from':list(lo),'to':list(hi),'faces':{f:{'texture':'#'+mat,'uv':[0,0,min(16,x),min(16,y)]}for f,(x,y) in spans.items()}}
    if angle:o['rotation']={'angle':angle,'axis':axis,'origin':origin or [(lo[i]+hi[i])/2 for i in range(3)],'rescale':False}
    return o
def leaf(x,y,z,w=5,d=2,angle=0):return C((x,y,z),(x+w,y+.5,z+d),'leaf',angle)
def stalk(x,y,z,h,w=1,mat='leaf'):return C((x,y,z),(x+w,y+h,z+w),mat)
def bulb(x,y,z,mat='rose',w=3,h=4):
    core=[C((x+.5,y,z+.5),(x+w-.5,y+h,z+w-.5),mat)]
    return core+([C((x,y+1,z),(x+w,y+h-1,z+w),mat)] if h>2 else [])
def rosette(y=1):return [leaf(1,y,6,6,3,22.5),leaf(9,y+.5,6,6,3,-22.5),leaf(6,y,1,3,6),leaf(6,y+.5,9,3,6)]
def blossom(x,y,z,mat,w=3):
    es=[]
    for dx,dz in [(-3,0),(3,0),(0,-3),(0,3)]:es.append(C((x+dx,y,z+dz),(x+dx+w,y+1,z+dz+w),mat))
    inset=.25 if w<=1 else .5
    es.append(C((x+inset,y+1,z+inset),(x+w-inset,y+2,z+w-inset),'pollen'));return es

def plant(s,age,mode):
    # Every cultivar owns a different developing silhouette and organ layout.
    h=[3,6,10,14][age]
    if s=='dawn_disk':return [stalk(7,0,7,min(h,11),1.5),leaf(3,3,6,5,3,22.5),leaf(8,6 if age>1 else 2,7,6,3,-22.5)]+([] if age==3 else bulb(6,h-1,6,'pollen',3,2))
    if s=='hearth_pepper':
        es=[stalk(7,0,7,h,1.5),leaf(4,1,6,4,3,-22.5)]
        for i,(x,y,z) in enumerate([(3,4,6),(10,7,8),(5,11,10)]):
            if i>=age:break
            es+=[C((min(x,7),y+2,z),(max(x,8)+1,y+2.75,z+1),'leaf'),leaf(x-1,y+3,z-1,5,3,22.5 if i%2 else -22.5)]
            if age>1:es+=bulb(x,y-2,z,'pepper',2,4)+[C((x+.5,y-3,z+.5),(x+1.5,y-2,z+1.5),'pepper')]
        return es
    if s=='mist_cotton':
        es=[stalk(7,0,7,h-1,1.5),leaf(2,2,6,5,3,22.5),leaf(9,4,6,5,3,-22.5)]
        for i,(x,y,z) in enumerate([(3,6,5),(9,9,8),(5,12,9)]):
            if i>=age:break
            es+=[stalk(x+1,3,z+1,y-2,.75),C((x,y-1,z),(x+3,y,z+3),'bark')]+bulb(x-1,y,z-1,'cotton',5,3)+[C((x,y+3,z),(x+3,y+4,z+3),'cotton')]
        return es
    if s=='stone_fern':
        es=[stalk(7,0,7,2+age*2)]
        for i in range(age+2):
            y=1+i*1.5;reach=min(6,i+2)
            for sign in [-1,1]:
                x=7+sign*reach;es+=[C((min(x,7),y,7),(max(x,8),y+.5,8),'leaf'),leaf(x-1,y,4,3,7),C((x,y+1,6),(x+1,y+2,8),'paper')]
        return es
    if s=='mirror_lotus':
        es=[leaf(2,0,3,12,9),leaf(4,.25,1,8,13),stalk(7,0,7,2+age)]
        for i in range(age+1):
            y=2+i;reach=max(1,4-i);es+=blossom(6,y,6,'ice',reach)
        if age==3:es+=[C((7,6,7),(9,7,9),'pollen')]
        return es
    if s=='wish_puff':
        es=rosette()+[stalk(7,0,7,h,1)]
        if age==0:return es
        top=h-2;es+=bulb(5,top,5,'cotton' if age>=2 else 'leaf',6,4)
        if age==3:
            for x,z in [(3,7),(11,7),(7,3),(7,11)]:es+=[C((x,top+1,z),(x+2,top+3,z+2),'paper'),C((x+.5,top,z+.5),(x+1.5,top+1,z+1.5),'pollen')]
        return es
    if s=='echo_bean':
        es=[stalk(5,0,7,h),stalk(10,0,8,max(2,h-2))]+[leaf(1,2,6,6,3),leaf(10,5 if age>1 else 2,8,5,3)]
        for i in range(age):
            x,y,z=5+(i%2)*5,3+i*3,9;es+=[C((x,y,z),(x+1,y+5,z+2),'leaf'),C((x-1,y+1,z),(x+1,y+4,z+2),'leaf')]
            for yy in [y+1,y+3]:es+=[C((x-.25,yy,z+2),(x+.75,yy+1,z+2.25),'cotton')]
        return es
    if s=='ladder_vine':
        es=[stalk(6,0,7,h,1.5),stalk(9,0,8,max(2,h-2),1)]
        for i in range(age+1):es+=[C((3,2+i*4,7),(12,3+i*4,8),'leaf'),leaf(1 if i%2==0 else 8,3+i*4,5,6,4,22.5 if i%2 else -22.5)]
        return es
    if s=='frost_chime':
        es=rosette()+[stalk(7,0,7,h)]
        for i,(x,y,z) in enumerate([(3,5,5),(10,8,7),(6,12,10)]):
            if i>=age:break
            es+=[stalk(x,1,z,y),C((x-1,y,z-1),(x+2,y+1,z+2),'ice'),C((x,y-2,z),(x+1,y,z+1),'cotton'),C((x-1,y+1,z),(x+2,y+2,z+1),'ice')]
        return es
    if s=='salt_ink':
        es=[]
        for i,(x,y,z,size) in enumerate([(4,1,5,7),(10,0,10,4),(3,0,11,3)]):
            if i>age:break
            tall=2+age*1.25;es+=[stalk(x+size/2-.5,y,z+size/2-.5,tall,1),C((x,y+tall,z),(x+size,y+tall+1,z+size),'paper'),C((x,y+tall+1,z),(x+size,y+tall+2,z+size),'violet'),C((x+1,y+tall+2,z+1),(x+size-1,y+tall+3,z+size-1),'violet')]
        return es
    if s=='wind_sail':
        es=rosette()+[stalk(5,0,7,h)]
        for i in range(age+1):
            y=2+i*3;es+=[C((6,y,7),(11,y+2,7.5),'linen',-22.5),C((10,y+1,7),(14,y+3,7.5),'paper',22.5)]
        return es
    if s=='sleep_clock':
        es=[stalk(7,0,7,h-1,1.5),leaf(2,2,6,5,3),leaf(9,4 if age>1 else 1,6,5,3)]
        if age<2:return es+bulb(6,h-1,6,'ice',3,2)
        y=h-4;es+=blossom(6,y,6,'ice',3)+[C((6,y+1,6),(10,y+1.5,10),'paper'),C((7,y+1.5,7),(8,y+2.25,10),'iron'),C((7,y+1.5,8),(10,y+2.25,9),'iron')];return es
    if s=='shadow_cut':
        es=[stalk(7,0,7,h-2,1.5)]+rosette()
        if age>0:
            for x,z in [(3,6),(9,6),(6,3),(6,9)]:es+=[C((x,h-3,z),(x+4,h-2,z+3),'violet',22.5 if x==3 or z==3 else -22.5),C((x+1,h-2,z),(x+3,h-1,z+2),'rose')]
            es+=[C((7,h-2,7),(9,h,9),'iron')]
            for i in range(mode):es+=[C((5+i*2,h-.5,6),(6+i*2,h,7),'paper')]
        return es
    if s=='paper_tree':
        es=[stalk(7,0,7,h,2,'bark'),leaf(4,min(h,3),6,5,3,22.5),leaf(8,min(h,3),7,4,3,-22.5)]
        for i,(x,y,z) in enumerate([(2,5,4),(9,9,7),(4,13,9)]):
            if i>=age:break
            es+=[C((min(x+2,7),y-1,7),(max(x+3,9),y,8),'bark'),leaf(x,y,z,5,4),C((x,y-2,z),(x+4,y-1.5,z+.5),'paper'),C((x+1,y-3,z),(x+3,y-2,z+.5),'paper')]
        return es
    if s=='flight_pod':
        es=rosette()+[stalk(7,0,7,h-2,1.5)]
        for i in range(age):
            y=4+i*3;es+=bulb(6,y,6,'leaf',3,3)+[C((2,y+1,7),(6,y+2,7.5),'paper',22.5),C((9,y+1,7),(13,y+2,7.5),'paper',-22.5)]
        return es
    if s=='honey_cluster':
        es=rosette()+[stalk(7,0,7,max(2,h-4),1.5)]
        for i,(x,z) in enumerate([(2,5),(9,5),(5,10)]):
            if i>=age:break
            y=h-4;es+=[C((min(x+2,7),y,7),(max(x+3,9),y+.75,8),'leaf'),C((x+1.5,y,min(z+2,7)),(x+2.5,y+.75,max(z+3,8)),'leaf'),C((x,y,z),(x+4,y+1,z+4),'bark'),C((x,y+1,z),(x+1,y+3,z+4),'pollen'),C((x+3,y+1,z),(x+4,y+3,z+4),'pollen'),C((x+1,y+1,z),(x+3,y+3,z+1),'pollen'),C((x+1,y+1,z+3),(x+3,y+3,z+4),'pollen'),C((x+1,y+1,z+1),(x+3,y+2,z+3),'pepper' if mode>i else 'leaf')]
        return es
    if s=='star_dew':
        es=[stalk(4,0,6,h,1.25),stalk(10,0,9,max(2,h-2),1.25),leaf(1,3,4,5,4),leaf(10,6 if age>1 else 2,8,5,4)]
        if age>0:es+=[C((4,h-1,6),(11,h,7),'leaf')]
        if age>1:
            for x,y,z in [(5,h-4,7),(8,h-5,8),(10,h-3,10)][:age]:es+=bulb(x,y,z,'violet',2.5,2.5)+[C((x+.5,y+2,z-.15),(x+1.5,y+2.75,z+.05),'cotton')]
        return es
    if s=='dance_bell':return rosette()+[stalk(7,0,7,4 if age==3 else h-1,1.5)]+([] if age==3 else bulb(6,h-1,6,'rose',3,3))
    if s=='star_rest':
        es=rosette()+[stalk(7,0,7,max(3,h-5),1.5)]
        if age:
            for i in range(age):es+=blossom(6,3+i*2,6,'violet' if i==0 else 'ice',max(2,4-i))
            es+=[C((7,4+age*2,7),(9,5+age*2,9),'cotton')]
        return es
    raise ValueError(s)

def plants():
    module=ast.parse((R/'tools/generate_botanical_assets.py').read_text(encoding='utf8'))
    spec=ast.literal_eval(next(n.value for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='SPEC' for t in n.targets)))
    for s,*_ in spec:
        for age in range(4):
            for mode in range(4):write(f'{s}_{age}_{mode}',plant(s,age,mode),'leaf')
    write('dance_bell_joint',[stalk(7,0,7,4,1.5),leaf(8,2,7,4,2)],'bark')
    head=[]
    for x,z in [(3,7),(10,7),(7,3),(7,10)]:head+=bulb(x,0,z,'rose',3,4)+[C((x-.5,0,z-.5),(x+3.5,1,z+3.5),'violet')]
    head+=blossom(6,3,6,'rose',3);write('dance_bell_head',head,'rose')
    head=[C((4,10,7),(12,17,8.5),'pollen'),C((2,12,7),(14,15,8),'pollen'),C((6,8,7),(10,19,8),'pollen'),C((5,11,6.75),(11,16,7),'bark')]
    for x,y in [(6,12),(9,13),(7,15)]:head+=[C((x,y,6.5),(x+1,y+1,6.75),'pollen')]
    write('dawn_disk_head',head,'pollen')
    write('botanical_vine_segment',plant('ladder_vine',3,0)+[stalk(6,14,7,2,1.5)],'bark')
    write('botanical_paper_segment',plant('paper_tree',3,0)+[stalk(7,14,7,2,2,'bark')],'bark')
    # Old crops keep their exact blockstate/property identities, but get organ
    # geometry consistent with the new set instead of billboard recolors.
    for p in list((A/'models/block').glob('*.json')):
        name=p.stem
        if name.startswith('road_bell_crop_'):
            age=int(name.rsplit('_',1)[1]);es=[]
            for x,z,offset in [(4,5,0),(10,8,2),(7,11,1)]:
                h=2+age*1.5-offset;es+=[stalk(x,0,z,max(2,h)),leaf(x-2,2,z,4,2,22.5)]
                if age>=3:
                    for yy in range(3):es+=[C((x-.5,h-3+yy,z-.5),(x+1.5,h-2+yy,z+1.5),'grain'),C((x,h+yy-3,z-1),(x+.5,h+yy-2,z),'pollen')]
            write(name,es,'grain')
        elif name.startswith('night_gourd_crop_'):
            age=int(name.rsplit('_',1)[1]);es=rosette()+[stalk(7,0,7,2+age/2,1)]
            if age>=3:
                w=3+age*.65;y=1;es+=bulb(8-w/2,y,8-w/2,'violet',w,3+age*.5)
                for x in [7-w/3,9+w/4]:es+=[C((x,2,8-w/2-.1),(x+.5,3+age*.5,8-w/2+.15),'pollen')]
            write(name,es,'violet')
        elif name.startswith('tide_reed_crop_'):
            age=int(name.rsplit('_',1)[1]);es=[]
            for x,z,dy in [(3,4,1),(10,8,-2),(6,11,0)]:
                h=4+age*3+dy;es+=[stalk(x,0,z,h,.9),leaf(x-1,1,z,3,4,45)]
                for y in range(3,int(h),3):es+=[C((x-.1,y,z-.1),(x+1,y+.4,z+1),'paper')]
                if age>=2:
                    # Cut hollow reed mouth, unlike wheat's solid seed ear.
                    es+=[C((x-.5,h,z-.5),(x+1.5,h+1,z),'ice'),C((x-.5,h,z+1),(x+1.5,h+1,z+1.5),'ice'),
                         C((x-.5,h,z),(x,h+1,z+1),'leaf'),C((x+1,h,z),(x+1.5,h+1,z+1),'leaf')]
            write(name,es,'leaf')
        elif name.startswith('hushcap_') and name.endswith(('_open','_closed')):
            _,age,op=name.split('_');age=int(age);es=[]
            for x,z,w in [(3,4,7),(10,10,4)][:min(2,age+1)]:
                h=2+age*1.5;es+=[C((x+w/2-.5,0,z+w/2-.5),(x+w/2+.5,h,z+w/2+.5),'paper')]
                if op=='open':es+=[C((x,h,z),(x+w,h+1,z+w),'paper'),C((x+.5,h+1,z+.5),(x+w-.5,h+2,z+w-.5),'bark')]
                else:es+=bulb(x+1,h-1,z+1,'bark',max(2,w-2),3)
            write(name,es,'bark')
        elif name.startswith('rainletter_') and name.endswith(('_dry','_wet')):
            _,age,wet=name.split('_');age=int(age);es=[]
            for i,(x,z) in enumerate([(4,5),(9,9),(4,10)][:min(3,age+1)]):
                h=2+age*2-i*.7;es+=[stalk(x,0,z,h,1.3),leaf(x-2,h-1,z-1,5,4,22.5 if i%2 else -22.5)]
                if wet=='wet':es+=[C((x+.3,h-.5,z+.3),(x+.9,h+.1,z+.9),'ice')]
                if age>=2:
                    # Short rounded berry bunches under a wide low shrub canopy.
                    for dx,dz,dy in [(-1,-1,0),(1,0,1),(0,1,2)][:age]:
                        es+=[C((x+dx,h-2-dy*.3,z+dz),(x+dx+1.8,h-dy*.3,z+dz+1.8),'rose')]
                        if wet=='wet':es+=[C((x+dx+.5,h-dy*.3,z+dz+.5),(x+dx+1,h+.5-dy*.3,z+dz+1),'ice')]
            write(name,es,'rose')
        elif name in ['homeward_flower_bloom','homeward_flower_bud']:
            es=rosette()+[stalk(7,0,7,10,1)]
            es+=blossom(6,10,6,'ice',3) if name.endswith('bloom') else bulb(6,9,6,'rose',3,4);write(name,es,'ice')
        elif name.startswith('warning_moss_') and name.endswith(('_lit','_dark')) and name.split('_')[2].isdigit():
            _,_,age,lit=name.split('_');age=int(age);es=[]
            for x,y,w,h in [(6,4,4,5),(3,7,4,4),(10,6,3,5),(5,11,5,3)][:age+1]:
                es+=[C((x,y,15),(x+w,y+h,16),'leaf'),C((x+1,y+1,14.5),(x+w-1,y+h-.5,15.25),'hay')]
                if age>=2:es+=[C((x+1,y+2,14),(x+2,y+3,14.5),'pollen' if lit=='lit' else 'bark')]
            write(name,es,'leaf')
        elif name.startswith('mana_root_') and len(name.split('_'))==4:
            age,fed=map(int,name.split('_')[2:]);es=rosette()+[stalk(7,0,7,3+age*2,2)]
            for x,z in [(3,7),(9,4),(10,10)]:es+=[C((min(x,7),0,min(z,7)),(max(x+1,9),1.5,max(z+1,9)),'bark')]
            if age>=2:es+=bulb(5,2,5,'leaf',6,4)+[C((7,2,4.8),(9,3+fed*.75,5),'ice')]
            write(name,es,'bark')
        elif name.startswith('verdant_vein_') and len(name.split('_'))==4:
            age,glow=map(int,name.split('_')[2:]);h=2+age*3;es=[stalk(7,0,7,h,2,'bark')]
            for i,(x,z) in enumerate([(3,5),(10,8),(5,11)][:age+1]):
                y=max(1,h-1-i*2);es+=[C((min(x,7),y,7),(max(x+1,9),y+1,8),'leaf'),
                    C((x,y,z),(x+3,y+2,z+3),'leaf'),C((x+.5,y+2,z+.5),(x+2.5,y+2.75,z+2.5),'hay')]
                if age>=2:es+=[C((x+.8,y+1,z-.15),(x+1.6,y+2,z+.1),'light' if glow else 'ice')]
            write(name,es,'leaf')
        elif name.startswith('sky_vine_') and name.rsplit('_',1)[-1].isdigit():
            age=int(name.rsplit('_',1)[-1]);h=[3,6,10,14,16][age]
            es=[stalk(7,0,7,h,2,'bark' if age>=3 else 'leaf'),leaf(3,1,5,7,5,22.5)]
            for i in range(min(4,age+1)):
                y=2+i*3;x=2 if i%2==0 else 9
                es+=[C((min(x+2,7),y,7),(max(x+3,9),y+1,8),'leaf'),
                    leaf(x,y+1,4 if i%2==0 else 8,6,5,22.5 if i%2 else -22.5)]
            if age==4:es+=[stalk(5,0,6,16,3,'bark'),C((5,4,6),(14,5,9),'bark'),leaf(9,5,3,7,7),leaf(0,11,7,7,7)]
            write(name,es,'bark')
    write('sky_vine_stem',[stalk(5,0,6,16,3,'bark'),stalk(9,0,8,16,2,'bark'),leaf(10,8,8,5,4)],'bark')

def machines():
    models={}
    # Generator: raised chamber, visible horizontal windings, crystal and bus bars.
    es=[C((1,0,1),(15,2,15),'stone'),C((2,2,2),(14,3,14),'iron'),C((4,3,4),(12,4,12),'copper')]
    for x,z in [(3,3),(11,3),(3,11),(11,11)]:es+=[C((x,3,z),(x+2,13,z+2),'iron')]
    for y in [5,7,9,11]:
        for lo,hi in [((3,y,3),(13,y+1,4)),((3,y,12),(13,y+1,13)),((3,y,4),(4,y+1,12)),((12,y,4),(13,y+1,12))]:es+=[C(lo,hi,'copper')]
    es+=[C((6,4,6),(10,11,10),'ice',45,'y'),C((4,13,4),(12,15,12),'iron'),C((5,15,5),(11,16,11),'copper'),C((5,4,2),(11,6,3),'copper'),C((7,4.5,1.75),(9,5.5,2),'light')];models['mana_generator']=es
    # Battery: two independently visible storage cells, unlike the generator coil.
    es=[C((2,0,2),(14,2,14),'stone'),C((3,2,3),(13,3,13),'copper')]
    for x in [4,9]:es+=[C((x,3,5),(x+3,12,11),'violet'),C((x-.25,3,4.7),(x+3.25,12,5),'glass'),C((x,12,5),(x+3,13,11),'copper')]
    for y in [3,7,11]:es+=[C((2,y,4),(14,y+.75,12),'iron')]
    es+=[C((4,13,4),(12,15,12),'iron'),C((5,15,5),(7,16,7),'copper'),C((9,15,9),(11,16,11),'copper')]
    for x in [5,7,9]:es+=[C((x,4,2.5),(x+1,5,3),'ice')]
    models['mana_battery']=es
    es=[C((3,0,3),(13,2,13),'stone'),C((7,2,7),(9,9,9),'iron'),C((4,9,4),(12,10,12),'copper'),C((5,10,5),(11,15,11),'light')]
    for x,z in [(4,4),(11,4),(4,11),(11,11)]:es+=[C((x,10,z),(x+1,15,z+1),'iron')]
    es+=[C((4,15,4),(12,16,12),'iron')];models['mana_work_lamp']=es
    # Press: work bed, posts, threaded shaft and projecting wooden handwheel.
    es=[C((1,0,1),(15,2,15),'stone'),C((2,2,2),(14,3,14),'wood'),C((4,3,3),(12,4,13),'paper'),C((3,6,3),(13,7,13),'iron')]
    for x,z in [(2,2),(12,2),(2,12),(12,12)]:es+=[C((x,2,z),(x+2,13,z+2),'iron')]
    es+=[C((2,13,2),(14,15,14),'wood'),C((7,7,7),(9,16,9),'copper'),C((3,15,7),(13,16,9),'wood'),C((3,15,6),(4,16,10),'copper'),C((12,15,6),(13,16,10),'copper')]
    for y in [8,10,12]:es+=[C((6.5,y,6.5),(9.5,y+.5,9.5),'iron')]
    models['mana_paper_press']=es;models['mana_paper_press_lit']=es+[C((4,4.1,4),(12,4.6,12),'ice')]
    es=[C((6,6,6),(10,10,10),'ice')]
    for axis in range(3):
        lo=[7,7,7];hi=[9,9,9];lo[axis]=0;hi[axis]=16;es+=[C(lo,hi,'copper')]
    for axis in range(3):
        for at in [1,12]:lo=[5.5,5.5,5.5];hi=[10.5,10.5,10.5];lo[axis]=at;hi[axis]=at+3;es+=[C(lo,hi,'iron')]
    models['mana_cable']=es
    models['mana_bottle_base']=[C((2,0,2),(14,1.5,14),'stone'),C((4,1.5,4),(12,2.5,12),'copper'),C((6,2.5,6),(10,3,10),'ice')]+[C((x,2,z),(x+2,4,z+2),'iron')for x,z in [(3,3),(11,3),(3,11),(11,11)]]
    models['pasture_trough']=[C((1,3,1),(3,6,15),'wood'),C((13,3,1),(15,6,15),'wood'),C((3,3,1),(13,6,3),'wood'),C((3,3,13),(13,6,15),'wood'),C((3,2,3),(13,3,13),'wood')]
    for x,z in [(1,1),(13,1),(1,13),(13,13)]:models['pasture_trough']+=[C((x,0,z),(x+2,3,z+2),'iron')]
    for z in [3,11]:models['pasture_trough']+=[C((.75,3,z),(1,6,z+1),'copper'),C((15,3,z),(15.25,6,z+1),'copper')]
    models['pasture_tray']=[C((1,0,1),(15,1.5,15),'wood')]+[C(lo,hi,'wood')for lo,hi in [((1,1,1),(2,4,15)),((14,1,1),(15,4,15)),((2,1,1),(14,4,2)),((2,1,14),(14,4,15))]]
    models['egg_rack']=copy.deepcopy(models['pasture_tray'])+[C((2,1.5,2),(7,2,7),'grain'),C((9,1.5,2),(14,2,7),'grain'),C((2,1.5,9),(7,2,14),'grain'),C((9,1.5,9),(14,2,14),'grain')]
    es=[C((2,0,2),(14,2,14),'wood')]
    for x,z in [(2,2),(12,2),(2,12),(12,12)]:es+=[C((x,2,z),(x+2,12,z+2),'wood')]
    es+=[C((2,12,2),(14,13,14),'linen'),C((3,3,3),(13,4,13),'copper')];models['dew_rack']=es
    models['habitat_marker']=[C((6,0,6),(10,2,10),'stone'),stalk(7,2,7,12,2,'bark'),C((3,9,6),(13,14,7),'wood'),C((6,11,5.7),(10,12,6),'leaf')]
    es=[C((2,0,2),(14,2,14),'stone'),C((3,2,3),(13,4,13),'copper'),C((7,4,7),(9,12,9),'wood'),C((1,12,7),(15,14,9),'wood')]
    for x in [2,12]:es+=[C((x,14,6),(x+2,15,10),'copper'),C((x,14.25,5.75),(x+2,14.75,6),'ice')]
    models['charging_perch']=es
    models['forge_core']=[C((2,0,2),(14,2,14),'stone'),C((4,2,4),(12,3,12),'copper'),C((6,3,6),(10,12,10),'ice',45,'y'),C((5,12,5),(11,14,11),'copper')]+[C((x,2,z),(x+2,10,z+2),'iron')for x,z in [(3,3),(11,3),(3,11),(11,11)]]
    models['forge_exhaust']=[C((0,0,0),(16,9,16),'brick'),C((3,9,3),(13,10,13),'copper')]+[C(lo,hi,'iron')for lo,hi in [((4,10,4),(6,16,12)),((10,10,4),(12,16,12)),((6,10,4),(10,16,6)),((6,10,10),(10,16,12))]]
    # The furnace is a full multiblock shell part; keep its opaque back and sides.
    es=[C((0,0,1),(16,16,16),'brick'),C((1,1,0),(15,15,1),'iron'),C((3,3,-.01),(13,12,.05),'bark')]
    es+=[C((2,2,-.4),(4,13,.2),'copper'),C((12,2,-.4),(14,13,.2),'copper'),C((4,12,-.4),(12,14,.2),'copper'),C((4,1,-.5),(12,3,.4),'copper'),C((5,3,-.2),(11,4,.1),'ice')]
    models['magic_forge']=es;models['magic_forge_lit']=es+[C((5,5,-.3),(11,9,-.05),'light')]
    for name,es in models.items():write(name,es,'wood' if 'pasture' in name else 'copper',name not in ['magic_forge_lit','mana_paper_press_lit'])

def feed():
    for name,mat in [('hay','hay'),('grain','grain'),('pellets','pellets'),('roots','pepper'),('fruit','rose'),('fish','ice'),('fungus','paper')]:
        es=[]
        if name=='hay':
            for i in range(7):es+=[C((2,1+i%2,2+i*1.5),(14,2+i%2,3+i*1.5),mat),C((4+i,1,3),(5+i,3,13),mat)]
            es+=[C((5,3,5),(11,4.5,11),mat)]
        elif name in ['grain','pellets']:
            es+=[C((2,0,2),(14,1.5,14),mat),C((3,1.5,3),(13,3,13),mat),C((5,3,5),(11,4.5,11),mat)]
            for x,z in [(3,4),(10,4),(5,10),(11,9)]:es+=[C((x,3,z),(x+2,4,z+2),mat)]
        elif name=='roots':
            for x,z in [(3,3),(8,4),(5,9)]:es+=bulb(x,0,z,mat,4,4)+[C((x+1,4,z+1),(x+3,4.5,z+3),'leaf')]
        elif name=='fruit':
            for x,z in [(3,4),(9,3),(7,10)]:es+=bulb(x,0,z,mat,3.5,3)+[C((x+1,3,z+1),(x+2,4.5,z+2),'leaf')]
        elif name=='fish':
            for x,z in [(3,4),(7,9)]:es+=[C((x,0,z),(x+6,3,z+3),mat),C((x+1,3,z+1),(x+5,4.5,z+2),'cotton')]
        else:
            for x,z in [(3,3),(9,5),(5,10)]:es+=[C((x+1,0,z+1),(x+2,2,z+2),'paper'),C((x,2,z),(x+3,3.5,z+3),'bark'),C((x+.5,3.5,z+.5),(x+2.5,4.5,z+2.5),'bark')]
        write('feed_'+name,es,mat)

def main():
    plants();machines();feed()
    for n in WRITTEN:
        d=json.loads((A/f'models/block/{n}.json').read_text(encoding='utf8'))
        for e in d['elements']:assert all(a<b for a,b in zip(e['from'],e['to'])),n
    out=R/'art/ecology-redesign-20261004/model-audit.json';out.write_bytes((json.dumps({'models':len(WRITTEN),'ids':WRITTEN,'plant_species':29,'feed_meshes':7,'native_materials':16,'uv':'Minecraft geometric UV, no full-tile stretching on thin bars'},indent=2)+'\n').encode())
    print('Authored',len(WRITTEN),'native models including all growth variants, devices and seven feed meshes')
if __name__=='__main__':main()
