"""Deterministic authored material atlas; user-approved procedural fallback.

Creates a new image, never paints over the user's existing texture or editor file.
"""
import math
import random
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'model-source/canglan_warlord/surface-refinement-20261022/material-atlas.png'
COLORS=[(32,53,69),(39,67,82),(24,42,56),(43,82,98),
        (145,121,72),(95,77,49),(92,125,142),(15,25,31),
        (42,72,88),(22,44,65),(30,55,76),(24,34,43),
        (19,64,70),(110,187,199),(49,91,79),(43,34,27)]

def cloud(draw,x,y,s,color,width=3):
    # Coiled wind scroll with a long swept tail, sampled as a smooth vector stroke.
    points=[]
    for i in range(90):
        t=i/89;angle=t*math.pi*3.4;r=s*(1-.82*t)
        points.append((x+math.cos(angle)*r,y+math.sin(angle)*r*.65))
    draw.line(points,fill=color,width=width,joint='curve')
    draw.line([(x+s,y),(x+s*1.8,y-s*.7),(x+s*2.8,y-s*.8)],fill=color,width=width,joint='curve')

def main():
    rng=random.Random(20261022);atlas=Image.new('RGBA',(1024,1024),(0,0,0,255))
    for tile,rgb in enumerate(COLORS):
        image=Image.new('RGBA',(256,256));pixels=image.load()
        for y in range(256):
            for x in range(256):
                grain=rng.randrange(-3,4)
                if tile in (8,9,10,11):
                    shade=grain+((x%4==0)*5)-((y%4==0)*5)+7*math.sin(x*.07)
                else:
                    edge=min(x,y,255-x,255-y)
                    shade=grain+10*math.sin(x*.023)+7*math.cos(y*.031)+(9 if 5<edge<14 else -14 if edge<5 else 0)
                pixels[x,y]=tuple(max(0,min(255,round(c+shade+rng.randrange(-1,2)))) for c in rgb)+(255,)
        d=ImageDraw.Draw(image);gold=(174,155,102);silver=(135,166,178);deep=(14,25,33);teal=(81,163,172)
        if tile in (0,2,3,4,5,6):
            rim=gold if tile in (0,4,5) else silver
            d.rounded_rectangle((8,8,247,247),radius=13,outline=deep,width=7)
            d.rounded_rectangle((15,15,240,240),radius=10,outline=rim,width=3)
            for x in (24,232):
                for y in (24,232):
                    d.ellipse((x-6,y-6,x+6,y+6),fill=deep)
                    d.ellipse((x-4,y-5,x+4,y+3),fill=rim)
            for k in range(20):
                x=rng.randrange(28,225);y=rng.randrange(30,225)
                length=rng.randrange(4,23)
                d.line((x,y,x+length,y-1),fill=tuple(min(255,c+22) for c in rgb),width=1)
            for x,y,s in ((65,85,25),(153,165,33),(55,205,18)):
                cloud(d,x,y,s,deep,7);cloud(d,x-1,y-2,s,rim,3)
        if tile==1:
            for row in range(4):
                for col in range(4):
                    x=col*64+(28 if row%2 else 0);y=row*62
                    shape=[(x+3,y+2),(x+58,y+2),(x+54,y+45),(x+31,y+61),(x+7,y+45)]
                    d.polygon(shape,fill=(28+row*5,55+row*6,70+row*6),outline=(124,155,163),width=3)
                    d.line((x+15,y+13,x+30,y+41,x+47,y+13),fill=(61,102,116),width=4)
        if tile in (3,6):
            for side in (-1,1):
                for i in range(4):
                    y=64+i*36
                    d.line([(128,y+21),(128+side*23,y+6),(128+side*61,y-3)],fill=deep,width=7)
                    d.line([(128,y+17),(128+side*23,y+2),(128+side*61,y-7)],fill=silver,width=3)
        if tile==5:
            # Shallow stylised beast mask, not a broad solid gold rectangle.
            for side in (-1,1):
                def line(points,c,w):d.line([(128+side*x,y) for x,y in points],fill=c,width=w,joint='curve')
                line([(0,66),(24,49),(60,52),(78,76),(52,87),(30,83)],gold,8)
                line([(18,98),(50,102),(66,120),(46,147),(20,146)],deep,10)
                line([(22,111),(42,113),(46,123),(28,128)],silver,4)
                line([(52,149),(72,161),(55,194),(18,207)],gold,5)
            d.rounded_rectangle((117,110,139,177),radius=7,fill=(53,50,39),outline=gold,width=3)
        if tile==7:
            for row in range(18):
                for col in range(17):
                    x=col*16+8*(row%2)-5;y=row*15-5
                    d.ellipse((x,y,x+22,y+24),outline=(61,81,90),width=3)
                    d.arc((x+2,y+2,x+20,y+22),205,325,fill=(111,131,138),width=2)
        if tile in (8,9,10):
            for y in range(42,256,74):
                cloud(d,49+(y//74%2)*38,y,29,(92,132,150),4)
                cloud(d,166,y+30,22,(66,104,124),3)
            for x in (12,243):
                d.line((x,0,x,256),fill=gold,width=4)
                for y in range(3,256,10):d.line((x-3,y,x+3,y+4),fill=(42,46,44),width=2)
            if tile in (9,10):
                for side in (-1,1):
                    for k in range(6):
                        y=34+k*32
                        d.line([(128,y+28),(128+side*29,y+10),(128+side*61,y-1)],fill=(103,148,159),width=4)
                d.line((128,15,128,239),fill=gold,width=3)
        if tile==11:
            for x in range(-256,512,32):
                d.line((x,0,x+256,256),fill=(60,78,89),width=3)
                d.line((x,0,x-256,256),fill=(60,78,89),width=3)
        if tile==12:
            for row in range(4):
                y=32+row*60
                cloud(d,55,y,24,deep,14);cloud(d,55,y-2,24,(126,219,221),4)
            d.line((210,0,210,94,182,122,182,256),fill=(84,175,185),width=6)
        if tile==13:
            for k in range(11):
                x=rng.randrange(256);y=rng.randrange(256);r=rng.randrange(25,77)
                d.polygon([(x-r,y),(x,y-r),(x+r,y),(x,y+r)],fill=(85+k*5,157+k*6,172+k*6),outline=(206,246,247),width=3)
        if tile==14:
            for k in range(7):
                pts=[(x,31+k*34+math.sin(x*.033+k)*12) for x in range(256)]
                d.line(pts,fill=(100+k*4,145+k*3,124+k*4),width=3)
            d.polygon([(128,46),(200,128),(128,210),(56,128)],outline=(158,199,169),width=7)
        if tile==15:
            for y in (25,230):
                d.line((0,y,256,y),fill=(20,19,17),width=7)
                for x in range(0,256,13):d.line((x,y-2,x+6,y+2),fill=(156,131,92),width=3)
            for y in (72,180):d.line((0,y,256,y),fill=(68,51,34),width=9)
        atlas.paste(image,((tile%4)*256,(tile//4)*256))
    OUT.parent.mkdir(parents=True,exist_ok=True);atlas.save(OUT)
    print(OUT)

if __name__=='__main__':main()
