"""Pixel-authored item icons and UV atlases for the repo-native wearable geometry."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, random
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc'
PALETTES={'bastion':('#343844','#171e28','#af8250'), 'astral':('#283a70','#101a39','#abc5e8'),
          'runic':('#693244','#291f32','#c0a369'),'wanderer':('#598781','#243e41','#c6ae7c'),
          'divine':('#e5dfcc','#23314f','#dfba65')}
def shade(c,f):
    return tuple(max(0,min(255,round(int(c[i:i+2],16)*f))) for i in (1,3,5))
def icon(style,tier,hat,relic=False):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    base,dark,trim=PALETTES[style];edge='#0d1323';shine=shade(trim,1.25)
    if relic:base='#605969';trim='#a58d68'
    if not hat:
        if style=='bastion':shape=[(8,3),(23,3),(29,8),(26,15),(23,13),(25,28),(6,28),(8,13),(5,15),(2,8)]
        elif style=='runic':shape=[(10,3),(21,3),(27,7),(25,16),(22,13),(23,27),(8,27),(9,13),(6,16),(4,7)]
        elif style=='wanderer':shape=[(10,3),(21,3),(28,8),(24,15),(21,12),(25,25),(20,23),(18,29),(11,25),(6,28),(10,13),(6,14),(3,8)]
        else:shape=[(10,2),(21,2),(27,7),(28,18),(23,16),(25,29),(6,29),(8,16),(3,18),(4,7)]
        d.polygon(shape,fill=base,outline=edge)
        d.polygon([(14,4),(17,4),(19,28),(12,28)],fill=dark)
        d.line([(11,3),(15,8),(20,3)],fill=trim,width=2)
        d.line([(9,14),(22,14)],fill=trim,width=2)
        if style=='bastion':
            for y in (9,18,22,26):d.line([(8,y),(23,y)],fill=shade(base,1.6));d.point((8,y-1),fill=shine);d.point((23,y-1),fill=shine)
            d.polygon([(11,7),(20,7),(20,12),(15,14),(11,12)],fill=shade(base,1.3),outline=trim)
        elif style=='runic':
            for y in range(7,27,2):
                for x in range(8,24,2):d.point((x+(y%4)//2,y),fill=shade('#a2aab7',.8))
            d.rectangle((12,5,18,24),fill=base);d.line((15,7,15,22),fill=trim)
        elif style=='wanderer':d.polygon([(8,3),(25,7),(23,11),(7,7)],fill=trim);d.line([(19,11),(23,25)],fill=trim)
        else:
            for x,y in [(10,11),(21,10),(10,19),(22,23)]:d.line((x-1,y,x+1,y),fill=trim);d.line((x,y-1,x,y+1),fill=trim)
            d.line([(7,28),(12,24),(17,28),(24,26)],fill=trim)
    else:
        if style in ('astral','divine'):
            d.polygon([(3,23),(29,23),(24,19),(21,13),(19,8),(20,3),(16,2),(13,8),(10,19)],fill=base,outline=edge)
            d.line([(4,23),(27,23)],fill=trim,width=2);d.line([(11,18),(22,18)],fill=trim,width=2)
            d.line([(16,4),(16,13),(13,16)],fill=trim);d.rectangle((14,17,17,19),fill=shine)
        elif style=='bastion':
            d.polygon([(2,22),(4,19),(9,18),(10,10),(22,10),(23,18),(28,19),(30,22)],fill=base,outline=edge)
            d.line((3,21,29,21),fill=trim,width=2);d.rectangle((10,16,22,18),fill=dark);d.rectangle((14,15,17,18),outline=trim)
        elif style=='runic':
            d.polygon([(6,15),(10,8),(22,8),(27,15),(24,23),(8,23)],fill=base,outline=edge)
            d.polygon([(7,16),(16,20),(26,16),(23,23),(9,23)],fill=dark,outline=trim);d.rectangle((14,14,17,17),fill=shine)
        else:
            d.polygon([(6,24),(4,16),(7,7),(14,4),(23,7),(27,15),(24,25),(20,28),(10,28)],fill=base,outline=edge)
            d.polygon([(10,12),(20,12),(23,22),(18,25),(12,25),(8,22)],fill=dark);d.line([(7,25),(15,28),(24,25)],fill=trim,width=2)
    if tier>=2:d.rectangle((4,7,5,9),fill=trim);d.rectangle((25,7,26,9),fill=trim)
    if tier>=3:d.polygon([(15,9),(17,11),(15,13),(13,11)],fill=shine)
    if tier>=4:
        for x,y in [(3,4),(28,4),(2,27),(28,27)]:d.line((x-1,y,x+1,y),fill=shine);d.line((x,y-1,x,y+1),fill=shine)
    if relic:
        d.line([(10,4),(16,10),(12,15),(18,20),(15,28)],fill=edge,width=2)
        d.rectangle((25,24,30,30),fill=dark,outline=trim);d.line((27,25,28,29),fill=trim)
    return im
def atlas(style,tier):
    base,dark,trim=PALETTES[style];rng=random.Random(style+str(tier));im=Image.new('RGBA',(64,64),base);d=ImageDraw.Draw(im)
    for y in range(64):
        for x in range(64):
            f=rng.uniform(.85,1.13)
            if style=='runic':f=1.15 if (x+y)%3==0 else .75
            if style=='bastion':f=1.15 if y%6==0 else .9
            d.point((x,y),fill=shade(base,f))
    for y in (0,15,31,43,62):d.line((0,y,63,y),fill=trim,width=1 if tier<3 else 2)
    for x in (0,15,31,47,62):d.line((x,0,x,63),fill=dark)
    # Front of humanoid chest and metal belt use deliberate UV locations.
    d.rectangle((20,20,27,31),fill=base);d.line((20,27,27,27),fill=trim,width=2)
    d.line((23,20,23,31),fill=dark,width=2)
    for x,y in [(5,47),(12,51),(25,23),(37,8),(49,39)]:
        if tier>=2:d.line((x-1,y,x+1,y),fill=trim);d.line((x,y-1,x,y+1),fill=trim)
    if tier>=3:d.rectangle((48,0,53,5),outline=trim);d.rectangle((50,1,51,4),fill=shade(trim,1.3))
    return im
def save(name,im,model=True):
    p=ROOT/'textures/item/gear'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
    if model:
        q=ROOT/'models/item'/f'{name}.json';q.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/gear/{name}'}},indent=2)+'\n',encoding='utf8')
for style in PALETTES:
    for t in ([5] if style=='divine' else range(1,5)):
        for hat in (False,True):
            name=f'{style}_{"hat" if hat else "outfit"}_{t}'
            save(name,icon(style,t,hat))
            p=ROOT/'textures/models/armor'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);atlas(style,t).save(p)
save('broken_divine_robe',icon('divine',1,False,True));save('broken_divine_hat',icon('divine',1,True,True))
for name,hat in [('empty_magic_hat',True),('empty_full_outfit',False)]:
    im=icon('astral',1,hat).resize((16,16),Image.Resampling.NEAREST)
    pixels=im.load()
    for y in range(16):
        for x in range(16):
            r,g,b,a=pixels[x,y];pixels[x,y]=(165,165,165,a)
    save(name,im,False)
out=ROOT.parents[4]/'work/gear-implementation';out.mkdir(parents=True,exist_ok=True)
preview=Image.new('RGBA',(5*160,8*80),(22,27,38,255));dr=ImageDraw.Draw(preview)
for row,(style,p) in enumerate(PALETTES.items()):
    for col,t in enumerate([5] if style=='divine' else range(1,5)):
        for hat in (False,True):
            x=col*160+(80 if hat else 0);y=row*120
            preview.alpha_composite(icon(style,t,hat).resize((64,64),Image.Resampling.NEAREST),(x,y+20))
            dr.text((x,y),f'{style} {t}',fill='#cfc5a8')
preview.convert('RGB').save(out/'gear-icons.png')
