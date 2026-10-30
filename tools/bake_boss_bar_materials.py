"""Author twelve relief frames and health materials as a compact, filtered nine-slice atlas.

Deterministic editable source: bevel normals, engraved channels, metal/stone grain,
feather plates and ink paper. Portrait assets are inputs for preview only and never changed.
"""
import json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFilter,ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'art/boss-bar-materials-20261026'
DEST=ROOT/'src/main/resources/assets/tnc/textures/gui/boss_hud'
W,H=1024,128
THEMES=[
 ('apollo','阿波罗',0xC49A4E,0xE3B853),('dark_giant','巨兽人领主',0x65586B,0x8960B5),
 ('stone_lancer','磐岳·岩枪古像',0x9A8765,0xBCA373),('water_leviathan','蓝金水渊兽',0xA89862,0x399DAA),
 ('yan_dark','公孙衍（迷失）',0x80505D,0xAC4269),('wind_general','御风将军',0x849990,0x7FA897),
 ('wind_warden','翠岚武尊',0x61977D,0x4DB482),('canglan_warlord','苍岚战将',0x8AA7AA,0x85BEC7),
 ('ink_abyss_drake','墨渊螭',0x939B9D,0xA3A9AB),('ink_crane_boss','玄翎墨鹤',0xB8BAB4,0xCDD0C8),
 ('ink_winged_tiger','玄墨翼虎',0x989D9C,0xB4B8B6),('ink_celestial_dragon','玄墨天龙',0xA4ABA9,0xC6CCC8)]

def rgb(c):return np.array([c>>16&255,c>>8&255,c&255],float)
def mask():return Image.new('L',(W,H))
def curve_points(p,n=64):
    p=np.array(p,float);t=np.linspace(0,1,n)[:,None];u=1-t
    return u**3*p[0]+3*u*u*t*p[1]+3*u*t*t*p[2]+t**3*p[3]
def stroke(m,p,width=4):ImageDraw.Draw(m).line([tuple(v) for v in curve_points(p)],fill=255,width=width,joint='curve')
def path(m,p,width=4):ImageDraw.Draw(m).line(p,fill=255,width=width,joint='curve')
def poly(m,p):ImageDraw.Draw(m).polygon(p,fill=255)
def ellipse(m,box,width=0):ImageDraw.Draw(m).ellipse(box,fill=255 if not width else None,outline=255 if width else None,width=width or 1)
def relief(canvas,m,color,bevel=2.0,height=5,rough=.015,opacity=255):
    alpha=np.array(m,float)/255
    if not alpha.any():return
    field=np.array(m.filter(ImageFilter.GaussianBlur(bevel)),float)/255*height
    dy,dx=np.gradient(field);nx=-dx;ny=-dy;nz=np.ones_like(dx);length=np.sqrt(nx*nx+ny*ny+1)
    light=np.maximum(0,(-.43*nx-.64*ny+.72*nz)/length)
    spec=np.maximum(0,(-.23*nx-.34*ny+.91*nz)/length)**22
    yy,xx=np.mgrid[:H,:W];grain=(np.sin(xx*2.13+yy*1.19)+np.sin(xx*.73-yy*3.71))*rough
    shade=.36+.69*light+grain
    base=rgb(color);pixels=np.zeros((H,W,4),np.uint8)
    pixels[:,:,:3]=np.uint8(np.clip(base[None,None,:]*shade[:,:,None]+spec[:,:,None]*68,0,255))
    pixels[:,:,3]=np.uint8(alpha*opacity)
    # The frame's drop shadow is baked, never a duplicate glowing outline.
    shadow=Image.new('RGBA',(W,H));shadow.putalpha(m.filter(ImageFilter.GaussianBlur(3)).point(lambda a:int(a*.45)))
    canvas.alpha_composite(shadow,(0,2));canvas.alpha_composite(Image.fromarray(pixels,'RGBA'))
def engrave(canvas,m,color=0x080D12,strength=150):
    tint=Image.new('RGBA',(W,H),tuple(rgb(color).astype(int))+(0,));tint.putalpha(m.point(lambda a:a*strength//255));canvas.alpha_composite(tint)
def feather(m,x,y,dx,dy,width=8):
    n=np.array([-dy,dx],float);n/=max(1,np.linalg.norm(n));n*=width
    a=np.array([x,y]);b=a+[dx,dy]
    one=curve_points([a,a+np.array([dx,dy])*.3+n,b+n*.6,b])
    two=curve_points([b,b-n*.6,a+np.array([dx,dy])*.3-n,a])
    poly(m,[tuple(v) for v in np.concatenate([one,two])])
def scroll(m,x,y,scale=1,flip=1,width=5):
    for p in [[(0,0),(10,-19),(41,-22),(43,-5)],[(43,-5),(45,12),(18,16),(22,0)],[(22,0),(25,-8),(34,-5),(32,1)]]:
        stroke(m,[(x+px*scale,y+py*scale*flip) for px,py in p],width)
def cloud(m,x,y,scale=1,width=5):
    for p in [[(0,5),(8,6),(6,-10),(18,-7)],[(18,-7),(13,-24),(43,-28),(42,-9)],[(42,-9),(60,-20),(72,-1),(59,6)],[(59,6),(45,13),(20,9),(10,11)]]:
        stroke(m,[(x+px*scale,y+py*scale) for px,py in p],width)

def frame(index):
    id,_,metal,accent=THEMES[index];ink=index>=8;im=Image.new('RGBA',(W,H))
    yy,xx=np.mgrid[:H,:W]
    # Closed, shaded rails replace the previous parallel single-pixel strokes.
    back=mask();d=ImageDraw.Draw(back)
    d.polygon([(20,39),(978,39),(998,64),(978,89),(20,89),(4,64)],fill=255)
    d.rectangle((27,45,970,83),fill=0)
    relief(im,back,0x21272D if not ink else 0x292D2E,1.5,5)
    rail=mask();d=ImageDraw.Draw(rail)
    d.polygon([(23,37),(978,37),(1000,64),(978,91),(23,91),(5,64)],fill=255)
    d.polygon([(25,41),(976,41),(996,64),(976,87),(25,87),(9,64)],fill=0)
    relief(im,rail,metal,1.0,7,.012 if not ink else .025)
    inset=mask();d=ImageDraw.Draw(inset)
    d.line((29,43,970,43),fill=255,width=2);d.line((29,85,970,85),fill=255,width=2)
    relief(im,inset,0xC6B994 if not ink else 0xD9DCD5,.6,3,0)
    # One sparse stitch along the outer edge gives scale without a repeated saw-tooth silhouette.
    stitch=mask();d=ImageDraw.Draw(stitch)
    for x in range(155,880,76):d.line((x,38,x+13,38),fill=255,width=1)
    engrave(im,stitch,0x080C11,120)
    for side in (0,1):
        ornaments=mask();cuts=mask();dark=mask()
        def finish():
            for m in (ornaments,cuts,dark):
                if side:m.paste(m.transpose(Image.Transpose.FLIP_LEFT_RIGHT))
            relief(im,dark,0x22282C,1.5,5)
            relief(im,ornaments,metal,1.65,6,.025 if index==2 else .01)
            engrave(im,cuts,0x090F16 if not ink else 0x15191B,160)
        # Collar is fitted to the portrait's right rim; the mirrored finial closes the far end.
        poly(dark,[(5,52),(24,33),(46,34),(61,41),(38,46),(29,56),(29,74),(38,82),(61,88),(46,96),(24,95),(5,77)])
        poly(ornaments,[(7,53),(26,35),(44,36),(53,40),(34,45),(25,56),(25,73),(35,83),(53,88),(44,93),(26,93),(7,76)])
        if index==0:
            scroll(ornaments,44,27,1,1,6);scroll(ornaments,45,102,.85,-1,5)
            for k in range(4):
                feather(ornaments,49+k*6,39,53-k*8,-20-k*3,6-k*.6)
                path(cuts,[(50+k*6,38),(99-k*2,20-k*2)],1)
            ellipse(ornaments,(7,53,29,75));ellipse(cuts,(12,58,24,70),2)
        elif index==1:
            for k in range(3):
                poly(ornaments,[(34+k*21,32),(42+k*21,18+k*4),(54+k*21,21+k*4),(60+k*21,37)])
                path(cuts,[(42+k*21,29),(47+k*21,26),(53+k*21,34)],2)
                ellipse(ornaments,(37+k*18,95,60+k*18,111),5)
            stroke(ornaments,[(24,42),(0,31),(1,14),(17,9)],7)
            ellipse(dark,(8,53,32,77));ellipse(ornaments,(12,57,28,73))
        elif index==2:
            for k in range(4):
                x=32+k*20;y=18+(k*7%13)
                poly(ornaments,[(x,y+8),(x+6,y),(x+21,y+3),(x+24,40),(x-3,40)])
                path(cuts,[(x+5,y+4),(x+12,y+13),(x+8,39)],2)
                poly(ornaments,[(x-2,88),(x+23,88),(x+17,108-k*2),(x+3,112-k*3)])
            path(cuts,[(29,77),(36,85),(28,94)],2)
        elif index==3:
            scroll(ornaments,43,104,1.3,-1,6);scroll(ornaments,58,25,.8,1,5)
            for k in range(4):
                feather(ornaments,39+k*6,39,52-k*5,-19-k*4,6)
                path(cuts,[(44+k*6,36),(89+k,19-k*2)],2)
            ellipse(ornaments,(7,52,31,76));ellipse(cuts,(11,56,27,72),2)
        elif index==4:
            poly(ornaments,[(33,40),(48,23),(60,29),(74,12),(78,32),(99,24),(106,40)])
            stroke(ornaments,[(34,92),(64,124),(91,91),(115,110)],5)
            path(cuts,[(41,38),(50,30),(60,33),(74,20),(74,36),(98,30)],2)
            ellipse(ornaments,(8,52,30,76));path(cuts,[(15,57),(24,64),(15,71)],2)
        elif index==5:
            poly(ornaments,[(33,38),(49,22),(91,24),(111,37),(87,34),(53,31),(43,40)])
            stroke(ornaments,[(34,94),(55,84),(74,113),(114,100)],8)
            stroke(cuts,[(39,94),(59,88),(75,107),(108,101)],2)
            for k in range(4):path(cuts,[(56+k*5,25),(54+k*5,31)],2)
        elif index==6:
            for k in range(4):
                feather(ornaments,35+k*5,39,66-k*10,-23+k*4,7)
                feather(ornaments,38+k*5,88,70-k*11,24-k*3,7)
                path(cuts,[(39+k*5,38),(98-k*5,19+k*4)],1)
                path(cuts,[(42+k*5,89),(105-k*5,108-k*2)],1)
        elif index==7:
            for k in range(3):
                poly(ornaments,[(33+k*19,36),(42+k*19,23),(61+k*19,28),(56+k*19,39)])
                poly(ornaments,[(33+k*19,91),(42+k*19,105),(61+k*19,100),(56+k*19,88)])
                path(cuts,[(43+k*19,28),(50+k*19,33),(47+k*19,37)],2)
            poly(ornaments,[(2,64),(18,48),(32,64),(18,80)]);poly(cuts,[(9,64),(18,56),(26,64),(18,72)])
        elif index==8:
            scroll(ornaments,35,31,1.5,1,7);scroll(ornaments,35,98,1.45,-1,7)
            stroke(cuts,[(41,30),(61,15),(93,30),(88,37)],2)
            for k in range(3):path(ornaments,[(53+k*16,39),(63+k*16,29),(72+k*16,37)],3)
        elif index==9:
            for k in range(4):
                feather(ornaments,33+k*4,40,76-k*10,-21-k*3,5)
                feather(ornaments,35+k*4,88,81-k*11,23-k*3,6)
                path(cuts,[(37+k*4,89),(112-k*7,110-k*2)],1)
            stroke(ornaments,[(36,30),(64,40),(82,19),(118,23)],3)
        elif index==10:
            path(ornaments,[(30,38),(40,22),(76,22),(90,37)],7)
            path(ornaments,[(30,90),(40,108),(76,108),(90,91)],7)
            path(cuts,[(43,36),(43,28),(69,28),(69,37)],2)
            path(cuts,[(43,94),(43,103),(69,103),(69,93)],2)
            for k in range(3):stroke(ornaments,[(95+k*8,17),(87+k*8,21),(88+k*8,35),(83+k*8,40)],4)
        elif index==11:
            cloud(ornaments,32,31,1.05,6);cloud(ornaments,39,103,1.0,6)
            scroll(cuts,46,32,.65,1,2);scroll(cuts,52,104,.6,-1,2)
            stroke(ornaments,[(78,23),(99,34),(103,12),(122,24)],3)
        finish()
    # A few inset fasteners add construction detail rather than a continuous ornamental pattern.
    if index<8:
        rivets=mask()
        for x in (124,325,698,901):ellipse(rivets,(x-2,36,x+2,40));ellipse(rivets,(x-2,88,x+2,92))
        relief(im,rivets,metal,1,4,0)
    if index>=8:
        edge=mask()
        for k in range(16):
            x=140+k*48
            stroke(edge,[(x,39),(x+10,36+(k%3)),(x+21,42),(x+31,39)],1)
        engrave(im,edge,0x171C1F,140)
    # The health channel stays genuinely transparent; the material below is never painted over.
    pixels=np.array(im);pixels[45:83,27:971,3]=0
    return Image.fromarray(pixels,'RGBA')

def field(index):
    _,_,_,accent=THEMES[index];base=rgb(accent);yy,xx=np.mgrid[:32,:W];u=yy/31
    highlight=np.exp(-((u-.22)/.18)**2)*.29
    shade=.47+.34*(1-u)+highlight+.025*np.sin(xx*.014+u*3)
    grain=(np.sin(xx*1.71+yy*3.13)+np.sin(xx*.313-yy*2.77))*.009
    shade+=grain
    if index>=8:
        flow=np.sin(xx*.022+np.sin(xx*.005)*2+yy*.24)*np.sin(xx*.007-yy*.19)
        shade=.52+.30*(1-u)+.10*flow+grain
        if index==9:shade+=.06*np.exp(-((u-.24)/.1)**2)
    pixels=np.zeros((32,W,4),np.uint8);pixels[:,:,:3]=np.uint8(np.clip(base[None,None,:]*shade[:,:,None]+3,0,255));pixels[:,:,3]=255
    image=Image.fromarray(pixels,'RGBA');cuts=Image.new('L',(W,32));d=ImageDraw.Draw(cuts)
    if index==0:
        for x in range(0,W,140):d.arc((x,-19,x+98,33),0,170,fill=46,width=1);d.arc((x+9,-17,x+86,29),0,170,fill=30,width=1)
    elif index==1:
        for x in range(50,W,155):d.line([(x,0),(x+8,9),(x+3,17),(x+15,31)],fill=84,width=2)
    elif index==2:
        for k,x in enumerate(range(35,W,123)):d.line([(x,0),(x+9,8),(x+6,16),(x+20,23),(x+18,31)],fill=100,width=2);d.line([(x+6,16),(x-8,21)],fill=60,width=1)
    elif index==3:
        for k in range(3):
            pts=[(x,round(8+k*8+3*math.sin(x*.015+k))) for x in range(W)];d.line(pts,fill=42,width=1)
    elif index==4:
        for k in range(3):d.line([(x,round(5+k*10+4*math.sin(x*.017+k))) for x in range(W)],fill=55,width=1)
    elif index==5:
        for y in range(0,32,4):d.line((0,y,W,y),fill=26,width=1)
        for x in range(0,W,9):d.line((x,0,x,32),fill=17,width=1)
    elif index==6:
        for x in range(0,W,94):d.line([(x,26),(x+54,8),(x+87,10)],fill=62,width=1);d.line([(x+23,18),(x+31,30)],fill=37,width=1)
    elif index==7:
        for k in range(16):x=k*63;d.line((x,6+k%5,x+43,6+k%5),fill=45,width=1)
    else:
        for k in range(175):
            x=(k*127+index*29)%W;y=(k*17+k//7)%30
            d.line((x,y,x+4+k%19,y+(k%3-1)),fill=28+k%35,width=1)
        if index==10:
            for x in range(20,W,160):d.line([(x,0),(x-9,12),(x+1,8),(x-4,28)],fill=60,width=2)
    tint=Image.new('RGBA',(W,32),(8,14,20,0));tint.putalpha(cuts);image.alpha_composite(tint)
    # A soft glancing highlight supplies depth without the former opaque bright stripe.
    return image

def main():
    OUT.mkdir(parents=True,exist_ok=True);DEST.mkdir(parents=True,exist_ok=True)
    atlas=Image.new('RGBA',(1024,2048));frames=[];fields=[]
    for i,(id,*_) in enumerate(THEMES):
        f=frame(i);v=field(i);frames.append(f);fields.append(v)
        atlas.paste(f,(0,i*128));atlas.paste(v,(0,1536+i*32));f.save(OUT/(id+'-frame.png'));v.save(OUT/(id+'-fill.png'))
    glint=np.zeros((32,128,4),np.uint8);glint[:,:,:3]=255
    for x in range(128):glint[:,x,3]=int(110*math.exp(-((x-64)/19)**2))
    atlas.paste(Image.fromarray(glint,'RGBA'),(0,1920))
    tip=Image.new('RGBA',(32,32));d=ImageDraw.Draw(tip)
    d.line((16,1,16,30),fill=(255,255,255,205),width=3);d.line((13,4,13,28),fill=(255,255,255,60),width=2)
    atlas.paste(tip,(128,1920));atlas.save(DEST/'materials_v1.png',optimize=True)
    (DEST/'materials_v1.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':True}}),encoding='utf-8')
    # Native-size layout preview, with the already accepted portrait images unmodified.
    preview=Image.new('RGB',(1300,1100),(13,19,28));d=ImageDraw.Draw(preview);font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
    for i,(id,label,*_) in enumerate(THEMES):
        x,y=70,40+i*86;scale=.60;left=round(128*scale);ww=1120
        d.text((x+ww/2,y-24),label,font=font,fill=(224,225,218),anchor='mt')
        cropped=Image.new('RGBA',(ww,round(128*scale)))
        cropped.paste(frames[i].crop((0,0,128,128)).resize((left,round(128*scale)),Image.Resampling.LANCZOS),(0,0))
        cropped.paste(frames[i].crop((128,0,896,128)).resize((ww-left*2,round(128*scale)),Image.Resampling.LANCZOS),(left,0))
        cropped.paste(frames[i].crop((896,0,1024,128)).resize((left,round(128*scale)),Image.Resampling.LANCZOS),(ww-left,0))
        trackx=x+round(27*scale);tracky=y+round(45*scale);trackw=ww-round(81*scale);trackh=round(38*scale)
        preview.paste(fields[i].resize((trackw,trackh),Image.Resampling.LANCZOS),(trackx,tracky))
        preview.paste(cropped,(x,y),cropped)
        portrait=Image.open(ROOT/'art/boss-hud-portraits-20261026'/(id+'.png')).resize((112,112),Image.Resampling.LANCZOS)
        preview.paste(portrait,(x-69,y-18),portrait)
    preview.save(OUT/'material-preview.png')
    (OUT/'layout.json').write_text(json.dumps({'width':1024,'height':2048,'frameHeight':128,'openingY':[45,83],
        'frameCaps':128,'fieldsY':1536,'fieldHeight':32,'glintY':1920,'themes':[v[0] for v in THEMES]},indent=2),encoding='utf-8')
    print(OUT/'material-preview.png')

if __name__=='__main__':main()
