"""Engraved, face-varied material atlas for block lightning staves."""
from pathlib import Path
import math,json,zlib
from PIL import Image,ImageDraw
from equipment_blockbench import export_wand
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc'
def build(t):
 atlas=Image.new('RGBA',(512,512),(0,0,0,255));em=Image.new('RGBA',(512,512),(0,0,0,255))
 colors=[(88,61,66),(151,174,204),(82,115,230),(39,46,72),(173,128,65),(205,222,243)]
 for mat,c in enumerate(colors):
  for variant in range(2):
   tile=Image.new('RGBA',(128,128));glow=Image.new('RGBA',(128,128),(0,0,0,255))
   for y in range(128):
    for x in range(128):
     grain=((x*73+y*151+x*y*13+variant*41)%37)/37
     edge=min(x,y,127-x,127-y)
     if mat==0: f=.6+.2*math.sin(x*.32+math.sin(y*.09)*.9)+.08*grain
     elif mat==2:f=.38+.35*(x//24%2)+.13*math.sin(y*.07)+.08*grain
     else:f=.57+.14*math.cos(x*.055)+.10*grain+.08*(y%3==0)
     if edge<3:f=.23
     elif edge<6:f=1.15
     elif edge<9:f=.43
     tile.putpixel((x,y),tuple(min(255,int(v*f)) for v in c)+(255,))
   d=ImageDraw.Draw(tile);g=ImageDraw.Draw(glow)
   if mat in [1,3,4,5]:
    # Bevelled recessed panels and stepped thunder fret, inset from UV borders.
    d.rectangle((14,14,113,113),outline=(26,32,49),width=3)
    d.line([(17,110),(17,17),(110,17)],fill=(190,205,220) if mat!=4 else (221,179,101),width=1)
    path=[(76,26),(46,26),(46,49),(70,49),(70,73),(49,73),(49,101)]
    if variant:path=[(128-x,y) for x,y in path]
    d.line([(x+2,y+2) for x,y in path],fill=(188,152,85),width=3)
    d.line(path,fill=(17,25,43),width=4)
    for x,y in [(23,23),(104,23),(23,104),(104,104)]:
     d.rectangle((x-3,y-3,x+3,y+3),fill=(24,29,40));d.rectangle((x-2,y-2,x+1,y+1),fill=(206,184,133))
    for yy in [38,89]:d.line((24,yy,34,yy),fill=(203,211,226),width=1)
   elif mat==0:
    for x in [25,57,93]:d.line([(x,10),(x-4,40),(x+2,76),(x-2,117)],fill=(33,25,34),width=2)
    d.rectangle((12,12,115,115),outline=(116,82,78),width=2)
   elif mat==2:
    path=[(73,9),(59,38),(72,38),(47,79),(61,79),(44,118)]
    d.line(path,fill=(151,189,255),width=5);d.line(path,fill=(232,246,255),width=2)
    g.line(path,fill=(98,141,255),width=7);g.line(path,fill=(209,235,255),width=2)
    d.line((18,10,18,118),fill=(121,160,246),width=2)
   index=mat*2+variant;at=((index%4)*128,(index//4)*128);atlas.paste(tile,at);em.paste(glow,at)
 modelpath=A/f'models/item/wands/held/lightning_wand_{t}.json';model=json.loads(modelpath.read_text())
 for i,f in enumerate(model['surfaces']):
  mat=f.get('material',1);variant=(zlib.crc32(f.get('part','').encode())+i//6)%2;idx=mat*2+variant;x=(idx%4)*4;y=(idx//4)*4
  f['uv']=[[x+.04,y+.04],[x+3.96,y+.04],[x+3.96,y+3.96],[x+.04,y+3.96]]
  # Emission now comes only from narrow fissures, preserving dark crystal facets.
  f.pop('glow',None)
 model['tnc_design']='carved_blocks_with_engraved_material_panels'
 modelpath.write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 atlas.save(A/f'textures/item/wands/held/lightning_wand_{t}.png');em.save(A/f'textures/item/wands/held/lightning_wand_{t}_emission.png')
 export_wand(A/f'models/item/lightning_wand_{t}.json')
if __name__=='__main__':
 for t in range(1,6):build(t)
 print('Five engraved atlases and face UVs updated')
