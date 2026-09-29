"""Copy generated art, preserve alpha and make production 32px sprites/models."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,shutil
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/tnc'
jobs=json.loads((ROOT/'work/revision2/generated-wands.json').read_text(encoding='utf-8-sig'))
raw=ROOT/'work/revision2/wand-originals';raw.mkdir(exist_ok=True)
out=ASSETS/'textures/item/wands';out.mkdir(parents=True,exist_ok=True)
provenance=[]
for job in jobs:
    source=Path(job['path']);element,tier=job['id'].rsplit('_',1);name=f'{element}_wand_{tier}'
    shutil.copy2(source,raw/(name+'.png'))
    image=Image.open(source).convert('RGBA');box=image.getchannel('A').getbbox()
    if not box:raise ValueError('Empty generated image '+name)
    image=image.crop(box);image.thumbnail((30,30),Image.Resampling.NEAREST)
    sprite=Image.new('RGBA',(32,32));sprite.alpha_composite(image,((32-image.width)//2,(32-image.height)//2));sprite.save(out/(name+'.png'))
    provenance.append({'item':name,'source_file':source.name,'prompt':job.get('prompt','Floating angular broken charcoal ember seal; tier1 fire; transparent pixel inventory sprite'),'method':'built-in imagegen; alpha crop; nearest neighbor 32px'})
for tier in range(1,6):
    source=ASSETS/f'textures/guide/{"water_focus_1" if tier==1 else "water_staff_"+str(tier)}.png'
    shutil.copy2(source,out/f'water_wand_{tier}.png')
names={'water':'水','fire':'火','lightning':'雷','wind':'风','earth':'土','light':'光','dark':'暗'}
gods={'water':'傲慢的水龙王','fire':'冠烬烈阳','lightning':'审判的天穹','wind':'无拘的长风','earth':'不动的山君','light':'不灭的晨星','dark':'吞夜的君主'}
tiers=['','冒险者法阵','精良法杖','王级法杖','传说法杖','神杖']
for language in ('zh_cn','en_us'):
    path=ASSETS/f'lang/{language}.json';data=json.loads(path.read_text(encoding='utf-8-sig'))
    for element,cn in names.items():
        for tier in range(1,6):
            name=f'{element}_wand_{tier}';data['item.tnc.'+name]=gods[element] if tier==5 else f'{cn} · {tiers[tier]}'
            model={'parent':'minecraft:item/generated' if tier==1 else 'minecraft:item/handheld','textures':{'layer0':'tnc:item/wands/'+name}}
            (ASSETS/f'models/item/{name}.json').write_text(json.dumps(model,indent=2),encoding='utf-8')
    data['entity.tnc.town_service']='城镇服务人员'
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
doc=ROOT/'docs/art';doc.mkdir(exist_ok=True)
(doc/'element-wand-art-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2),encoding='utf-8')
canvas=Image.new('RGB',(1100,920),(24,37,48));draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',23);small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17)
draw.text((35,18),'七系法器 · 三十五件独立造型',font=font,fill=(241,230,205))
for col,label in enumerate(tiers[1:]):draw.text((190+col*178,64),label,font=small,fill=(190,202,207))
for row,(element,cn) in enumerate(names.items()):
    y=110+row*110;draw.text((35,y+35),cn+'系',font=font,fill=(241,230,205))
    for tier in range(1,6):
        sprite=Image.open(out/f'{element}_wand_{tier}.png').resize((96,96),Image.Resampling.NEAREST)
        canvas.paste(sprite,(185+(tier-1)*178,y),sprite)
canvas.save(ROOT/'work/revision2/七系法器总览.png')
print('35 production sprites and models; 30 generated + 5 preserved water sprites')
