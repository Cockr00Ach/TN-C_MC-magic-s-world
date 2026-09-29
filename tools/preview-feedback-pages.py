"""Offline proofs of native chapter coordinates, explicitly not game screenshots."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'work/revision3'; OUT.mkdir(parents=True,exist_ok=True)
ASSETS=ROOT/'src/main/resources/assets/tnc'
CHAPTERS=ROOT/'questbook/ftbquests/chapters'
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',14)
def read(name):return json.loads((CHAPTERS/(name+'.snbt')).read_text(encoding='utf-8-sig'))
def plain(s):
    import re
    return re.sub(r'&[0-9a-fklmnor]','',s)

atlas=read('tnc_guide_10_atlas')
canvas=Image.new('RGB',(1873,1082),'#182530')
canvas.paste(Image.open(ASSETS/'textures/guide/town_atlas.png').convert('RGB'),(0,40))
draw=ImageDraw.Draw(canvas)
draw.text((15,8),'城镇图鉴 · 实际节点位置预览（非游戏截图）',font=font,fill='#ead7a5')
for i,n in enumerate(atlas['quests']):
    x=n['x']*40+1173/2; y=n['y']*40+1022/2+40
    color='#d2ad5e' if i+1 in (19,28,29,34,40,41) else '#54878b'
    draw.ellipse((x-19,y-19,x+19,y+19),fill=color,outline='#efdfaf',width=2)
    draw.text((x,y),f'{i+1:02}',font=font,fill='#ffffff',anchor='mm')
    draw.text((1190,45+i*24),plain(n['title']),font=small,fill='#eaddbf')
canvas.save(OUT/'地图页修订预览.png')

for name,label in [('tnc_guide_01_world','歸人物页修订预览'),('tnc_guide_02_weapons','法杖页修订预览')]:
    chapter=read(name)
    canvas=Image.new('RGB',(1100,1080),'#182530');draw=ImageDraw.Draw(canvas)
    draw.text((20,12),label+' · 实际节点位置（非游戏截图）',font=font,fill='#ead7a5')
    def xy(x,y):return (550+x*70,430+y*50)
    for illustration in chapter['images']:
        art=Image.open(ASSETS/illustration['image'].split('tnc:')[1]).convert('RGBA')
        size=(round(illustration['width']*70),round(illustration['height']*50))
        art=art.resize(size,Image.Resampling.NEAREST)
        x,y=xy(illustration['x'],illustration['y'])
        canvas.paste(art,(round(x-size[0]/2),round(y-size[1]/2)),art)
    for n in chapter['quests']:
        x,y=xy(n['x'],n['y']);r=round(n['size']*17)
        draw.rounded_rectangle((x-r,y-r,x+r,y+r),radius=5,fill='#3d626d',outline='#d2ad5e',width=2)
        if n['icon'].startswith('tnc:water_wand_'):
            art=Image.open(ASSETS/('textures/item/wands/'+n['icon'].split(':')[1]+'.png')).resize((32,32),Image.Resampling.NEAREST)
            canvas.paste(art,(round(x-16),round(y-16)),art)
        draw.text((x,y+r+8),plain(n['title']),font=small,fill='#ead7a5',anchor='mt')
    canvas.save(OUT/(label+'.png'))

canvas=Image.new('RGB',(1100,920),'#182530');draw=ImageDraw.Draw(canvas)
draw.text((35,18),'七系法杖 · 五阶独立造型',font=font,fill='#ead7a5')
for col,label in enumerate(['冒险者法杖','精良法杖','王级法杖','传说法杖','神杖']):draw.text((190+col*178,64),label,font=small,fill='#bfcbd0')
for row,(element,cn) in enumerate(zip(['water','fire','lightning','wind','earth','light','dark'],['水','火','雷','风','土','光','暗'])):
    y=110+row*110;draw.text((35,y+35),cn+'系',font=font,fill='#ead7a5')
    for tier in range(1,6):
        art=Image.open(ASSETS/f'textures/item/wands/{element}_wand_{tier}.png').resize((96,96),Image.Resampling.NEAREST)
        canvas.paste(art,(185+(tier-1)*178,y),art)
canvas.save(OUT/'七系法杖修订总览.png')
print('Rendered four chapter/art proofs.')
