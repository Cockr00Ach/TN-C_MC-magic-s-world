"""Authored 16px plants and practical tools; no raster resampling or borrowed art."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw

R=Path(__file__).resolve().parents[1]
A=R/'src/main/resources/assets/tnc'
D=R/'src/main/resources/data'
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def tex(name,base,seed):
    im=Image.new('RGBA',(16,16)); r=random.Random(seed)
    for y in range(16):
        for x in range(16):
            n=r.choice([-12,-6,0,0,6,12])
            im.putpixel((x,y),tuple(max(0,min(255,c+n)) for c in base)+(255,))
    (A/'textures/block').mkdir(parents=True,exist_ok=True)
    im.save(A/'textures/block'/f'{name}.png')
    return im
def cube(lo,hi,t):
    return {'from':lo,'to':hi,'faces':{f:{'uv':[0,0,16,16],'texture':t} for f in ('up','down','north','south','east','west')}}
tex('sky_vine_stem',(55,92,57),73)
leaf=tex('sky_vine_leaf',(67,120,77),74)
for y in range(16):
    for x in range(16):
        if (x*3+y*5)%11==0:leaf.putpixel((x,y),(0,0,0,0))
leaf.save(A/'textures/block/sky_vine_leaf.png')
tex('sky_vine_pod',(133,174,112),75)
tex('canopy_rope',(150,163,105),76)
tex('sound_relay_copper',(156,97,58),77)
tex('sound_relay_glass',(85,162,157),78)
for age in range(5):
    elements=[cube([6,0,6],[10,5+age*2,10],'#stem'),cube([2,2,6],[7,3,10],'#leaf'),cube([9,4,5],[14,5,9],'#leaf')]
    if age>0:elements +=[cube([5,6,7],[10,7,12],'#leaf'),cube([9,8,3],[13,9,7],'#leaf')]
    if age>=3:elements +=[cube([4,10,6],[7,13,9],'#pod'),cube([10,11,7],[13,14,10],'#pod')]
    write(A/'models/block'/f'sky_vine_{age}.json',{'ambientocclusion':False,'textures':{'stem':'tnc:block/sky_vine_stem','leaf':'tnc:block/sky_vine_leaf','pod':'tnc:block/sky_vine_pod','particle':'tnc:block/sky_vine_leaf'},'elements':elements})
write(A/'blockstates/sky_vine.json',{'variants':{f'age={n}':{'model':f'tnc:block/sky_vine_{n}'} for n in range(5)}})
write(A/'models/block/sky_vine_stem.json',{'textures':{'stem':'tnc:block/sky_vine_stem','particle':'tnc:block/sky_vine_stem'},'elements':[cube([5,0,5],[11,16,11],'#stem'),cube([2,0,6],[5,16,10],'#stem'),cube([11,0,6],[14,16,10],'#stem')]})
write(A/'models/block/sky_vine_leaf.json',{'parent':'minecraft:block/leaves','textures':{'all':'tnc:block/sky_vine_leaf'}})
write(A/'models/block/canopy_rope_segment.json',{'ambientocclusion':False,'textures':{'rope':'tnc:block/canopy_rope','particle':'tnc:block/canopy_rope'},'elements':[cube([7,0,7],[9,16,9],'#rope'),cube([5,3,6],[11,4,10],'#rope'),cube([5,11,6],[11,12,10],'#rope')]})
write(A/'models/block/farlight_node.json',{'textures':{'particle':'minecraft:block/white_wool'},'elements':[]})
relay={'textures':{'base':'tnc:block/sound_relay_copper','glass':'tnc:block/sound_relay_glass','particle':'tnc:block/sound_relay_copper'},'elements':[cube([1,0,1],[15,3,15],'#base'),cube([5,3,5],[11,10,11],'#glass'),cube([4,10,4],[12,12,12],'#base'),cube([7,12,7],[9,15,9],'#glass')]}
write(A/'models/block/field_sound_relay.json',relay)
write(A/'blockstates/field_sound_relay.json',{'variants':{'powered=false':{'model':'tnc:block/field_sound_relay'},'powered=true':{'model':'tnc:block/field_sound_relay'}}})
for name in ['sky_vine_stem','sky_vine_leaf','canopy_rope_segment','farlight_node']:
    write(A/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'tnc:block/{name}'}}})
    write(D/'tnc/loot_tables/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[]})
write(D/'tnc/loot_tables/blocks/field_sound_relay.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:field_sound_relay'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name in ['sky_vine_seed','sky_canopy_fiber','sky_canopy_rope','sky_vine_survey']:
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    if name=='sky_vine_seed':
        d.polygon([(5,2),(10,2),(12,5),(11,11),(8,14),(4,11),(3,6)],fill='#344934');d.polygon([(6,3),(9,3),(10,5),(9,11),(7,12),(5,9),(5,5)],fill='#8bad70');d.line([(7,4),(7,10)],fill='#d5e9ad');d.rectangle([9,5,10,7],fill='#477c62')
    elif name=='sky_canopy_fiber':
        for start in [3,6,9,12]:d.line([(start,2),(start+1,5),(start-1,9),(start,13)],fill='#496846',width=2);d.line([(start,3),(start+1,6),(start,11)],fill='#b4ca87')
    elif name=='sky_canopy_rope':
        d.ellipse([2,2,13,13],outline='#46523c',width=3);d.ellipse([4,4,11,11],outline='#b7c78a',width=2);d.rectangle([6,10,8,15],fill='#708454');d.line([(8,11),(8,14)],fill='#d3d99d')
    else:
        d.rectangle([3,2,12,13],fill='#59533f');d.rectangle([4,3,11,12],fill='#dacdad');d.rectangle([2,1,5,3],fill='#9e865e');d.rectangle([10,12,13,14],fill='#9e865e');d.line([(6,5),(9,5)],fill='#44634c');d.line([(6,7),(10,7)],fill='#44634c');d.line([(6,9),(8,9)],fill='#44634c')
    (A/'textures/item').mkdir(parents=True,exist_ok=True);im.save(A/'textures/item'/f'{name}.png')
    write(A/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/{name}'}})
write(A/'models/item/field_sound_relay.json',{'parent':'tnc:block/field_sound_relay'})
def shaped(name,pattern,key,result,count=1):
    write(D/'tnc/recipes'/f'{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'item':result,'count':count}})
shaped('sky_vine_survey',['P P','PSP',' P '],{'P':'minecraft:paper','S':'minecraft:spyglass'},'tnc:sky_vine_survey')
shaped('sky_canopy_rope',['FSF','FSF','FSF'],{'F':'tnc:sky_canopy_fiber','S':'minecraft:string'},'tnc:sky_canopy_rope')
shaped('field_sound_relay',[' E ','CRC',' P '],{'E':'tnc:echo_bean_pod','C':'minecraft:copper_ingot','R':'minecraft:redstone','P':'minecraft:oak_planks'},'tnc:field_sound_relay')
path=D/'minecraft/tags/blocks/climbable.json'
tag=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
if 'tnc:canopy_rope_segment' not in tag['values']:tag['values'].append('tnc:canopy_rope_segment')
write(path,tag)
zh={'block.tnc.sky_vine':'望天蔓','block.tnc.sky_vine_stem':'望天蔓茎','block.tnc.sky_vine_leaf':'天幕叶','block.tnc.canopy_rope_segment':'天幕攀绳','block.tnc.farlight_node':'远照微光','block.tnc.field_sound_relay':'节律继电器','item.tnc.sky_vine_seed':'望天蔓种源','item.tnc.sky_canopy_fiber':'天幕纤维','item.tnc.sky_canopy_rope':'天幕长绳','item.tnc.sky_vine_survey':'望天蔓三境调查卷','entity.tnc.farlight_fruit':'远照果'}
en={'block.tnc.sky_vine':'Skyward Vine','block.tnc.sky_vine_stem':'Skyward Stem','block.tnc.sky_vine_leaf':'Canopy Leaf','block.tnc.canopy_rope_segment':'Canopy Rope','block.tnc.farlight_node':'Farlight Glow','block.tnc.field_sound_relay':'Tuned Sound Relay','item.tnc.sky_vine_seed':'Skyward Vine Seed','item.tnc.sky_canopy_fiber':'Canopy Fiber','item.tnc.sky_canopy_rope':'Canopy Rope','item.tnc.sky_vine_survey':'Three Biomes Survey','entity.tnc.farlight_fruit':'Farlight Fruit'}
write(R/'work/wonder-lang-zh.json',zh);write(R/'work/wonder-lang-en.json',en)
print('Generated wonder block/item models, native 16px textures, recipes and climbable tag.')
