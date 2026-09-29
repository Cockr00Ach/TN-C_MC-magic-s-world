"""Import seven generated novice wands and retain reproducible sprite sources."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json, shutil

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'docs/art/adventurer-wands'
ART.mkdir(parents=True, exist_ok=True)
GENERATED = Path('C:/Users/宋志坤/.codex/generated_images/01a0e9a7-09c2-7b12-b9af-d76132668563')
SOURCES = {
    'water': 'exec-3f6b9500-e4cd-4ce0-896e-504b5f2df569.png',
    'fire': 'exec-eeeaf5ea-7a4b-4191-8305-fe001c2ab420.png',
    'lightning': 'exec-411f3df2-f0eb-4a0b-b3a4-bae3bfd5ca4a.png',
    'wind': 'exec-6112b9dd-6ba2-4f94-b402-90ec88f423c3.png',
    'earth': 'exec-c49b3448-677c-4295-9c00-c8da4fcd2f90.png',
    'light': 'exec-0b28eb7c-6864-4cb2-8f73-800249788a62.png',
    'dark': 'exec-62875d21-85cd-4c10-b2e1-d5edfa69de86.png',
}
ASSETS = ROOT / 'src/main/resources/assets/tnc'
rows = []
for element, filename in SOURCES.items():
    source = GENERATED / filename
    image = Image.open(source).convert('RGBA')
    box = image.getchannel('A').getbbox()
    if not box:
        raise ValueError('Empty image: ' + element)
    image = image.crop(box)
    image.thumbnail((30, 30), Image.Resampling.NEAREST)
    sprite = Image.new('RGBA', (32, 32))
    sprite.alpha_composite(image, ((32-image.width)//2, (32-image.height)//2))
    sprite.save(ART / (element+'.png'))
    shutil.copy2(ART / (element+'.png'), ASSETS / f'textures/item/wands/{element}_wand_1.png')
    rows.append({'element': element, 'generated_source': filename, 'method': 'built-in imagegen; alpha crop and nearest-neighbor resize; distinct novice wand with complete grip'})
shutil.copy2(ART / 'water.png', ASSETS / 'textures/guide/water_focus_1.png')
(ART / 'provenance.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
catalog=ROOT/'docs/art/element-wand-art-provenance.json'
entries=json.loads(catalog.read_text(encoding='utf-8'))
entries=[entry for entry in entries if not entry['item'].endswith('_wand_1')]
for entry in rows:
    entries.append({'item':entry['element']+'_wand_1','source_file':entry['generated_source'],'method':entry['method']})
catalog.write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
preview = Image.new('RGB', (1050,240), '#182530')
draw = ImageDraw.Draw(preview)
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 19)
for col, (element, cn) in enumerate(zip(SOURCES, ['水','火','雷','风','土','光','暗'])):
    sprite = Image.open(ART/(element+'.png')).resize((128,128), Image.Resampling.NEAREST)
    preview.paste(sprite, (col*150+10,25), sprite)
    draw.text((col*150+75,175), cn+'·冒险者法杖', font=font, fill='#eaddbf', anchor='mt')
work = ROOT/'work/revision3'
work.mkdir(parents=True, exist_ok=True)
preview.save(work/'七系冒险者法杖.png')
print('Imported seven distinct novice sprites, including guide illustration.')
