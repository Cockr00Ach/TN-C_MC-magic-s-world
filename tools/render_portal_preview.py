"""Geometry-only isometric preview of the ACTUAL portal generator (not a gameplay screenshot)."""
from pathlib import Path
from PIL import Image,ImageDraw
from portal_design import portal_blocks

def render():
    scale=19; im=Image.new('RGB',(1400,1200),(19,20,28)); draw=ImageDraw.Draw(im)
    colors={'red_stained_glass':(186,34,54),'redstone_block':(155,27,39),'shroomlight':(221,88,38),
            'crying_obsidian':(78,41,105),'coal_block':(28,25,32),'obsidian':(43,30,55),'chain':(107,107,119),
            'blackstone':(52,49,63),'polished_blackstone':(73,69,82),'polished_blackstone_bricks':(65,61,75),
            'chiseled_polished_blackstone':(87,79,94)}
    def point(x,y,z): return (700+(x-z)*scale,840+(x+z-34)*scale*.48-y*scale)
    for x,y,z,s,_ in sorted(portal_blocks(),key=lambda v:(v[0]+v[2],v[1])):
        name=s.split(':')[1].split('[')[0]; col=colors.get(name,(69,65,79))
        low=0; high=1; inset=0
        if 'slab' in name: low,high=(.5,1) if 'type=top' in s else (0,.5)
        if name=='chain': inset=.38
        if name.endswith('_wall'): inset=.25
        xa,xb=x+inset,x+1-inset; za,zb=z+inset,z+1-inset
        if name=='chain' and 'axis=x' in s: xa,xb=x,x+1;low,high=.4,.6
        if name=='chain' and 'axis=z' in s: za,zb=z,z+1;low,high=.4,.6
        faces=[([point(xa,y+low,zb),point(xb,y+low,zb),point(xb,y+high,zb),point(xa,y+high,zb)],.72),
               ([point(xb,y+low,za),point(xb,y+low,zb),point(xb,y+high,zb),point(xb,y+high,za)],.9),
               ([point(xa,y+high,za),point(xb,y+high,za),point(xb,y+high,zb),point(xa,y+high,zb)],1.2)]
        for points,f in faces:
            draw.polygon(points,fill=tuple(min(255,int(c*f)) for c in col),outline=(27,26,35))
    draw.text((32,28),'TN-C / RITUAL GATE - block geometry preview',fill=(222,210,215))
    draw.text((32,50),'35 x 28 x 35 / geometry only; NOT a gameplay screenshot',fill=(158,155,164))
    out=Path('docs/previews/ritual-gate-geometry.png');out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
    print(out)

if __name__=='__main__': render()
