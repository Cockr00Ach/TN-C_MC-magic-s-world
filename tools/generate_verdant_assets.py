"""Original hard-pixel shrub with forked branches and layered leaves, no borrowed art."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/tnc'
def js(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for glow in range(3):
    im=Image.new('RGBA',(16,16),(43,67,32,255));d=ImageDraw.Draw(im)
    for y in range(16):
        for x in range(16):
            if (x*3+y*7)%9<2:d.point((x,y),fill=(65,94,43,255))
    d.line([(1,15),(7,8),(13,1)],fill=[(83,112,42),(125,170,54),(174,226,80)][glow],width=2)
    for y in range(3,15,4):d.line([(6,y+1),(13,y-2)],fill=(87,125,48),width=1)
    out=A/f'textures/block/verdant_leaf_{glow}.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
im=Image.new('RGBA',(16,16),(60,49,33,255));d=ImageDraw.Draw(im)
for x in (2,5,9,13):d.line([(x,0),(x,15)],fill=(85,78,41),width=1)
d.line([(8,0),(8,15)],fill=(95,131,50),width=1);im.save(A/'textures/block/verdant_stem.png')
def cube(lo,hi,texture):return {'from':lo,'to':hi,'faces':{f:{'uv':[0,0,16,16],'texture':texture} for f in ('up','down','north','south','east','west')}}
variants={}
for age in range(4):
    h=(5,8,12,16)[age]
    for glow in range(3):
        elements=[cube([7,0,7],[9,h,9],'#stem')]
        if age>0:elements.extend([cube([4,h*.45,7],[8,h*.45+2,9],'#stem'),cube([8,h*.7,7],[12,h*.7+2,9],'#stem')])
        for x,y,z,w in ((3,h*.44,6,4),(9,h*.72,6,4),(5,h-2,5,6)):
            elements.append(cube([x,y,z],[x+w,min(h+1,y+2),z+4],'#leaf'))
        name=f'verdant_vein_{age}_{glow}'
        js(A/f'models/block/{name}.json',{'textures':{'stem':'tnc:block/verdant_stem','leaf':f'tnc:block/verdant_leaf_{glow}','particle':'tnc:block/verdant_stem'},'elements':elements})
        variants[f'age={age},glow={glow}']={'model':'tnc:block/'+name}
js(A/'blockstates/verdant_vein.json',{'variants':variants})
for name in ('verdant_vein_seed','verdant_branch','insulating_forge_clay'):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    if name.endswith('seed'):
        d.polygon([(5,5),(8,3),(11,6),(10,11),(7,13),(4,10)],fill=(44,60,27),outline=(23,38,22))
        d.line([(6,6),(8,11),(10,7)],fill=(144,186,71),width=2)
    elif name == 'verdant_branch':
        d.line([(3,14),(7,9),(7,3)],fill=(81,67,39),width=3);d.line([(7,9),(12,5)],fill=(65,99,39),width=2)
        d.rectangle((9,2,13,5),fill=(68,110,45));d.line([(7,10),(10,6)],fill=(171,205,84))
    else:
        d.polygon([(3,7),(6,4),(11,4),(14,8),(12,12),(5,13),(2,10)],fill=(110,99,80),outline=(54,52,46))
        d.line([(4,7),(7,5),(11,5)],fill=(161,148,119),width=1)
        d.line([(5,10),(8,8),(11,10)],fill=(80,101,58),width=2)
        d.point((11,7),fill=(194,170,111))
    (A/'textures/item').mkdir(parents=True,exist_ok=True);im.save(A/f'textures/item/{name}.png')
    js(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'tnc:item/'+name}})
for lang,values in {'zh_cn':{'block.tnc.verdant_vein':'绿脉枝','item.tnc.verdant_vein_seed':'绿脉枝种子','item.tnc.verdant_branch':'导魔枝','item.tnc.insulating_forge_clay':'绝缘炉泥'},'en_us':{'block.tnc.verdant_vein':'Verdant Vein','item.tnc.verdant_vein_seed':'Verdant Vein Seed','item.tnc.verdant_branch':'Mana-conducting Branch','item.tnc.insulating_forge_clay':'Insulating Forge Clay'}}.items():
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf-8'));v.update(values);js(p,v)
print('Generated 12 layered shrub models and original 16px textures.')
