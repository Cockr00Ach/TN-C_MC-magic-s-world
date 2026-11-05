"""Author the halberd and angry bronze humanoid as the actual game resources; render offline."""
from pathlib import Path
import json,math,random
from PIL import Image,ImageDraw,ImageFont
from gen_bronze_weapons import ROOT,RES,ASSET,build,write

def make_halberd():
    model=build('spear')
    def box(x,y,z,w,h,d,mat=0):
        u=mat%4*4;v=mat//4*8
        model['elements'].append({'from':[x+8,y,z+8],'to':[x+w+8,y+h,z+d+8],'faces':{side:{'texture':'#bronze','uv':[u,v,u+4,v+8]} for side in ('north','south','east','west','up','down')}})
    # All central pieces share X=8, Z=8; only the side blade is intentionally asymmetric.
    box(-.8,-16,-.6,1.6,1,1.2,2)
    for i in range(7):
        x=-8+i;y=21+i*.55
        box(x,y,-.5,1,2.8,1)
        box(x,y,-.3,.25,2.8,.6,2)
    box(-4.5,22,-.65,4.5,1.4,1.3)
    for i in range(5):
        for x in (-.7,.3):box(x,12+i*.4,-.7,.4,.15,.15,6)
    write(ASSET/'models/item/bronze_halberd.json',model)
    write(RES/'data/tnc/recipes/bronze_halberd.json',{'type':'tnc:bronze_fusion','category':'equipment'})
    for name in ('spears','infusible_weapons'):
        p=RES/f'data/tnc/tags/items/{name}.json';data=json.loads(p.read_text(encoding='utf-8'))
        if 'tnc:bronze_halberd' not in data['values']:data['values'].append('tnc:bronze_halberd')
        write(p,data)

def make_body():
    parts=[]
    def part(name,pivot,boxes):parts.append({'name':name,'pivot':pivot,'boxes':boxes})
    def b(x,y,z,w,h,d,material=0):return {'box':[x,y,z,w,h,d],'uv':[(material%2)*64,(material//2)*64]}
    head=[b(-5,-10,-5,10,10,10),b(-3.5,-5.5,-5.2,2.4,1.2,.3,3),b(1.1,-5.5,-5.2,2.4,1.2,.3,3),
        b(-4.8,-7.1,-5.8,2.7,1.2,.9,1),b(-2.5,-6.5,-5.9,1.8,1.1,1,1),
        b(2.1,-7.1,-5.8,2.7,1.2,.9,1),b(.7,-6.5,-5.9,1.8,1.1,1,1),
        b(-.7,-6.5,-6.3,1.4,5.1,1.5,1),b(-3,-2,-5.3,6,.65,.3,2),b(-3,-1.4,-5.8,6,1.4,1,1),
        b(-5.6,-7,-3,1,4,3),b(4.6,-7,-3,1,4,3)]
    for side in (-1,1):
        x=side*3.5;head.extend([b(x-.7,-4,-5.6,1.4,.5,.5,1),b(x-.7,-4,-5.6,.5,2,.5,1),b(x-.7,-2.5,-5.6,1.4,.5,.5,1)])
    part('head',[0,0,0],head);part('hat',[0,0,0],[])
    body=[b(-5,0,-3,10,10,6),b(-4,10,-2.5,8,2,5),b(-2,0,-2.5,4,2,5,2)]
    for side in (-1,1):
        x=side*2.5;body.extend([b(x-1.8,2,-3.7,3.6,.7,.7,1),b(x-1.8,2,-3.7,.7,4,.7,1),b(x-1.8,5.3,-3.7,3.6,.7,.7,1),b(x+1.1,3.5,-3.7,.7,2.5,.7,1)])
    part('body',[0,0,0],body)
    for side,name in ((-1,'right_arm'),(1,'left_arm')):
        part(name,[side*6,2,0],[b(-3,-2,-3,6,4,6),b(-2.2,2,-2.2,4.4,4,4.4),b(-2,5,-2,4,2,4,2),b(-2.7,7,-2.7,5.4,4,5.4),b(-2.8,10.5,-2.8,5.6,2,5.6)])
    for side,name in ((-1,'right_leg'),(1,'left_leg')):
        part(name,[side*2.5,12,0],[b(-2.3,0,-2.5,4.6,4,5),b(-2,4,-2,4,2,4,2),b(-2.5,5.7,-2.7,5,4,5.4),b(-3,9,-4,6,3,8)])
    write(ASSET/'geo/entity/bronze_puppet_parts.json',parts)
    rng=random.Random(20261105);tex=Image.new('RGB',(128,128))
    for y in range(128):
        for x in range(128):
            material=x//64+y//64*2;localx=x%64;localy=y%64
            if material==0:
                color=(75,70,50) if math.sin(localx*.23)+math.cos(localy*.26)<.3 else (43,74,60)
            else:color={1:(118,107,78),2:(30,32,25),3:(139,83,29)}[material]
            noise=rng.randint(-12,12)
            if material<2 and rng.random()<.05:noise-=30
            tex.putpixel((x,y),tuple(max(0,min(255,c+noise)) for c in color))
    p=ASSET/'textures/entity/bronze_puppet.png';p.parent.mkdir(parents=True,exist_ok=True);tex.save(p)
    return parts

def preview(parts):
    canvas=Image.new('RGB',(1500,1350),(29,31,29));draw=ImageDraw.Draw(canvas)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',32);title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',46)
    draw.text((60,35),'青铜战戟与人偶 · 成品模型',font=title,fill=(211,198,163));draw.text((64,100),'游戏模型、材质离线预览',font=font,fill=(145,155,136))
    faces=[];texture=Image.open(ASSET/'textures/entity/bronze_puppet.png')
    def cube(x,y,z,w,h,d,color):
        for side,pts in [('front',[(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z)]),('right',[(x+w,y,z),(x+w,y,z+d),(x+w,y+h,z+d),(x+w,y+h,z)]),('top',[(x,y,z),(x,y,z+d),(x+w,y,z+d),(x+w,y,z)])]:
            depth=sum(p[2] for p in pts)/4;faces.append((depth,pts,side,color))
    for part in parts:
        p=part['pivot']
        for item in part['boxes']:
            x,y,z,w,h,d=item['box'];u,v=item['uv'];cube(x+p[0],y+p[1],z+p[2],w,h,d,(texture,u,v,64,64))
    item=json.loads((ASSET/'models/item/bronze_halberd.json').read_text(encoding='utf-8'))
    itemtex=Image.open(ASSET/'textures/item/bronze_atlas.png')
    for el in item['elements']:
        a=el['from'];b=el['to'];uv=el['faces']['north']['uv'];material=(itemtex,uv[0]/16*itemtex.width,uv[1]/16*itemtex.height,max(1,(uv[2]-uv[0])/16*itemtex.width),max(1,(uv[3]-uv[1])/16*itemtex.height))
        cube(a[0]-22,18-b[1],a[2]-9,b[0]-a[0],b[1]-a[1],b[2]-a[2],material)
    scale=20
    def project(p):x,y,z=p;return (790+(x+z*.22)*scale,540+(y-z*.08)*scale)
    rng=random.Random(7)
    for depth,pts,side,color in sorted(faces,reverse=True):
        shade={'front':1.14,'right':.65,'top':1.3}[side]
        # Pixel facets are sampled from the material palette; geometry is exactly the shipped cuboids.
        for j in range(8):
            for i in range(8):
                def point(u,v):return tuple(pts[0][k]*(1-u)*(1-v)+pts[1][k]*u*(1-v)+pts[2][k]*u*v+pts[3][k]*(1-u)*v for k in range(3))
                tex,u,v,w,h=color;base=tex.getpixel((min(tex.width-1,int(u+w*(i+.5)/8)),min(tex.height-1,int(v+h*(j+.5)/8))))
                n=rng.randint(-3,3);col=tuple(max(0,min(255,int(c*shade)+n)) for c in base)
                draw.polygon([project(point(i/8,j/8)),project(point((i+1)/8,j/8)),project(point((i+1)/8,(j+1)/8)),project(point(i/8,(j+1)/8))],fill=col)
    draw.text((70,1280),'每人一个  ·  最多10分钟  ·  消失后2分钟冷却',font=font,fill=(211,198,163))
    out=ROOT/'docs/previews/bronze_puppet_finished.png';out.parent.mkdir(parents=True,exist_ok=True);canvas.save(out);print(out)

def main():
    make_halberd();parts=make_body()
    for lang in ('zh_cn','en_us'):
        p=ASSET/f'lang/{lang}.json';data=json.loads(p.read_text(encoding='utf-8'))
        entries={'item.tnc.bronze_halberd':('青铜战戟','Bronze Halberd'),'entity.tnc.bronze_puppet':('青铜人偶','Bronze Puppet'),
            'tooltip.tnc.bronze_summon':('潜行＋右键：召唤青铜人偶（最多10分钟）','Sneak + use: summon bronze puppet (up to 10 minutes)'),
            'message.tnc.puppet_exists':('你的青铜人偶仍然存在','Your bronze puppet is still present'),
            'message.tnc.puppet_cooldown':('人偶召唤冷却：剩余 %s 秒','Puppet summon cooldown: %s seconds'),
            'message.tnc.puppet_no_space':('周围没有足够空间召唤人偶','Not enough space to summon the puppet'),
            'message.tnc.puppet_summoned':('青铜人偶已召唤，最多存在10分钟','Bronze puppet summoned for up to 10 minutes')}
        for key,value in entries.items():data[key]=value[0 if lang=='zh_cn' else 1]
        write(p,data)
    preview(parts)

if __name__=='__main__':main()
