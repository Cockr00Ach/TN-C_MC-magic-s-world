"""Seamless alpha material for native ink ribbon geometry; deterministic brush fibres."""
from pathlib import Path
import math
from PIL import Image

root=Path(__file__).resolve().parents[1]
image=Image.new('RGBA',(512,128),(255,255,255,0))
pixels=image.load()
for y in range(128):
    v=(y+0.5)/64-1
    for x in range(512):
        phase=x/512*math.tau
        edge=.86+.06*math.sin(phase*2)+.035*math.sin(phase*7+y*.13)
        envelope=max(0,min(1,(edge-abs(v))/.28))
        grain=.78+.12*math.sin(y*2.7+math.sin(phase*3))+.08*math.sin(y*.93-phase*5)
        fibre=abs(math.sin(y*.57+math.sin(phase)*.4))
        dry=.32 if fibre<.12 and abs(v)<.55 else 1
        pixels[x,y]=(255,255,255,int(255*envelope*grain*dry))
path=root/'src/main/resources/assets/tnc/textures/entity/ink_brush_strip.png'
image.save(path)
print(path)
