"""Rasterize the authored altar seal and the blood-pool decal at supersampled resolution."""
from pathlib import Path
from math import cos, sin, pi
from PIL import Image, ImageDraw

out = Path(__file__).resolve().parents[2] / "src/main/resources/assets/tnc/textures/effects/sanctum"
out.mkdir(parents=True, exist_ok=True)
size = 512
center = (size - 1) / 2
def radial(angle, radius):
    return (center + radius*cos(angle), center + radius*sin(angle))
def circle(draw, radius, value, width):
    draw.ellipse([center-radius,center-radius,center+radius,center+radius], outline=value,width=width)
def save(mask, name):
    image=Image.new("RGBA",mask.size,"white")
    image.putalpha(mask)
    image.resize((256,256),Image.Resampling.LANCZOS).save(out/(name+".png"))
seal=Image.new("L",(size,size))
d=ImageDraw.Draw(seal)
d.ellipse((4,4,507,507),fill=30)
for r,a,w in [(251,248,5),(241,143,2),(213,191,3),(167,94,2),(47,213,3),(34,121,2)]:
    circle(d,r,a,w)
for i in range(16):
    a=i*pi/8
    d.line([radial(a,216),radial(a,237 if i%2==0 else 229)],fill=236,width=3)
    if i%2==0:
        d.line([radial(a-.08,147),radial(a,205),radial(a+.08,147)],fill=204,width=3)
        d.line([radial(a+.08,147),radial(a+pi/4-.08,147)],fill=117,width=2)
        d.line([radial(a,55),radial(a,135)],fill=120,width=2)
        d.polygon([radial(a-.025,196),radial(a,220),radial(a+.025,196),radial(a,179)],fill=237)
for i in range(4):
    a=pi/4+i*pi/2
    d.line([radial(a,132),radial(a+pi/2,132)],fill=100,width=3)
d.polygon([radial(i*pi/2,25) for i in range(4)],fill=201)
save(seal,"seal")
# A dark continuous footprint with irregular edges and a brighter drying meniscus.
pool=Image.new("L",(size,size))
d=ImageDraw.Draw(pool)
def edge(a):
    return 215+9*sin(3*a+.2)+14*cos(5*a)+8*sin(9*a+1.2)+4*cos(15*a-.7)
def outline(scale):
    return [radial(i*pi/128,edge(i*pi/128)*scale) for i in range(256)]
for scale,alpha in [(1,220),(.975,153),(.94,218),(.79,235),(.62,243)]:
    d.polygon(outline(scale),fill=alpha)
for i in range(11):
    a=i*2*pi/11+.19*sin(i)
    d.line([radial(a+.04*sin(j*.32),edge(a)*(.88+j*.002)) for j in range(46)],fill=250,width=3)
    x,y=radial(a,min(249,edge(a)+10))
    rr=3+(i%3)
    d.ellipse((x-rr,y-rr,x+rr,y+rr),fill=200)
save(pool,"pool")
Image.new("RGBA",(2,2),"white").save(out/"plain.png")
