"""Bake the installed bosses' actual meshes and UV textures into a static HUD atlas.

Orthographic software rasterization with a depth buffer, texture sampling and studio
lighting. No generated character designs; no live entity rendering in the shipped HUD.
"""
import io, json, math, struct, zipfile, hashlib
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LIVE = Path('E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/mods/tnc-1.0.0.jar')
OUT = ROOT / 'art/boss-hud-portraits-20261026'
DEST = ROOT / 'src/main/resources/assets/tnc/textures/gui/boss_hud'
EXPECTED = '239411A9FE2413BBF91AF66BD3B5E5DC9D34D9524C1C556D63E970C9E477C096'
SIZE = 512
SPECS = [
    ('apollo','gothic_priest','gothic_priest_bedrock',('head',),-18,7,0xD2AB60,'阿波罗'),
    ('dark_giant','dark_giant','dark_giant_bedrock',('head',),-16,5,0x8866A9,'巨兽人领主'),
    ('stone_lancer','stone_lancer','stone_lancer',('head',),-24,9,0xB49157,'磐岳·岩枪古像'),
    ('water_leviathan','water_leviathan','water_leviathan_bedrock',('head',),-29,8,0x55AAB2,'蓝金水渊兽'),
    ('yan_dark','yan_dark','yan_dark_bedrock',('head',),-24,7,0xA85977,'公孙衍（迷失）'),
    ('wind_general','wind_general','wind_general',('head','neck'),-24,6,0x82ADA1,'御风将军'),
    ('wind_warden','wind_warden','wind_warden',('head',),-22,6,0x6FC59B,'翠岚武尊'),
    ('canglan_warlord','canglan_warlord','canglan_warlord',('head',),-22,5,0x9CBEBE,'苍岚战将'),
    ('ink_abyss_drake','ink_abyss_drake','ink_abyss_drake',('head',),-32,8,0xA6AAA9,'墨渊螭'),
    ('ink_crane_boss','ink_crane_boss','ink_crane_boss',('neck_mid',),-61,4,0xCBCAC4,'玄翎墨鹤'),
    ('ink_winged_tiger','ink_winged_tiger','ink_winged_tiger',('head',),-16,7,0xB2B5B2,'玄墨翼虎'),
    ('ink_celestial_dragon',None,None,None,46,8,0xC1C3BF,'玄墨天龙'),
]
FACES = {'north':[0,1,3,2],'south':[4,6,7,5],'west':[0,2,6,4],
         'east':[1,5,7,3],'up':[2,3,7,6],'down':[0,4,5,1]}

def rot(v):
    x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]]) @ np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]) @ np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def gecko(z,model,texture,roots):
    geo=json.loads(z.read(f'assets/tnc/geo/entity/{model}.geo.json'))['minecraft:geometry'][0]
    tex=np.asarray(Image.open(io.BytesIO(z.read(f'assets/tnc/textures/entity/{texture}.png'))).convert('RGBA')).astype(float)/255
    tw,th=geo['description']['texture_width'],geo['description']['texture_height']
    bones={b['name']:b for b in geo['bones']};matrices={};selected={}
    def matrix(n):
        if n not in matrices:
            b=bones[n];p=np.array(b.get('pivot',[0,0,0]),float);r=rot(b.get('rotation',[0,0,0]));m=np.eye(4);m[:3,:3]=r;m[:3,3]=p-r@p
            matrices[n]=matrix(b['parent'])@m if b.get('parent') else m
        return matrices[n]
    def include(n):
        if n not in selected:selected[n]=n in roots or bool(bones[n].get('parent') and include(bones[n]['parent']))
        return selected[n]
    result=[]
    for n,b in bones.items():
        if not include(n) or n.startswith('emitter_'):continue
        for cube in b.get('cubes',[]):
            o=np.array(cube['origin'],float);size=np.array(cube['size'],float);inflate=cube.get('inflate',0)
            o-=inflate;size+=inflate*2
            corners=np.array([o+size*np.array([i&1,(i>>1)&1,(i>>2)&1]) for i in range(8)])
            p=np.array(cube.get('pivot',[0,0,0]),float);corners=(corners-p)@rot(cube.get('rotation',[0,0,0])).T+p
            corners=(matrix(n)@np.column_stack([corners,np.ones(8)]).T).T[:,:3]
            uv=cube.get('uv',[0,0]);sx,sy,sz=cube['size']
            if isinstance(uv,list):
                u,v=uv;boxes={'north':(u+sz,v+sz,sx,sy),'south':(u+2*sz+sx,v+sz,sx,sy),
                  'west':(u,v+sz,sz,sy),'east':(u+sz+sx,v+sz,sz,sy),
                  'up':(u+sz,v,sx,sz),'down':(u+sz+sx,v,sx,sz)}
            else:boxes={f:(q['uv'][0],q['uv'][1],*q.get('uv_size',[sx,sy])) for f,q in uv.items()}
            for face,ids in FACES.items():
                if face not in boxes:continue
                u,v,w,h=boxes[face];coords=np.array([[u+w,v+h],[u,v+h],[u,v],[u+w,v]],float)/[tw,th]
                if cube.get('mirror',b.get('mirror',False)):coords=coords[[1,0,3,2]]
                # Head texture detail remains per texel; do not average the atlas to a flat face color.
                for tri in ([0,1,2],[0,2,3]):result.append((corners[np.array(ids)[tri]],coords[tri],None,('eye' in n or n.startswith('glow_'))))
    return result,tex

def dragon(z):
    raw=z.read('assets/tnc/meshes/ink_celestial_dragon.mesh');pos=16
    count,lods=struct.unpack_from('>II',raw,8);result=[]
    for _ in range(count):
        length=struct.unpack_from('>H',raw,pos)[0];pos+=2;name=raw[pos:pos+length].decode();pos+=length+12
        for lod in range(lods):
            vertices=struct.unpack_from('>I',raw,pos)[0];pos+=4
            if lod==0 and name in ('head','jaw','mane','beard','whiskers'):
                data=np.frombuffer(raw,dtype=np.dtype([('xyz','>f4',(3,)),('rgba','u1',(4,))]),count=vertices,offset=pos)
                xyz=data['xyz'].astype(float);rgb=data['rgba'].astype(float)/255
                # The dragon looks down -X; rotate it to the same portrait camera convention.
                xyz=xyz@rot([0,-90,0]).T
                for i in range(0,vertices,3):result.append((xyz[i:i+3],None,rgb[i:i+3],False))
            pos+=vertices*16
    return result,None

def solar_relief():
    """Dedicated light-god relief: the legacy priest atlas contains UV diagnostic colors."""
    faces=[]
    def tri(p,color):faces.append((np.array(p,float),None,np.tile(np.array((*color,255))/255,(3,1)),False))
    def sphere(center,radii,color,lat=12,lon=28):
        center=np.array(center);radii=np.array(radii)
        def v(a,b):return center+radii*np.array([math.sin(a)*math.cos(b),math.cos(a),math.sin(a)*math.sin(b)])
        for j in range(lat):
            a,b=j*math.pi/lat,(j+1)*math.pi/lat
            for k in range(lon):
                c,d=k*2*math.pi/lon,(k+1)*2*math.pi/lon
                p=[v(a,c),v(b,c),v(b,d),v(a,d)];tri(p[:3],color);tri([p[0],p[2],p[3]],color)
    def ring(radius,tube,color):
        def v(a,b):return [(radius+tube*math.cos(b))*math.cos(a),(radius+tube*math.cos(b))*math.sin(a),tube*math.sin(b)]
        for j in range(72):
            a,b=j*math.tau/72,(j+1)*math.tau/72
            for k in range(6):
                c,d=k*math.tau/6,(k+1)*math.tau/6;p=[v(a,c),v(b,c),v(b,d),v(a,d)]
                tri(p[:3],color);tri([p[0],p[2],p[3]],color)
    gold=(206,153,62);light=(244,215,144);bronze=(106,66,28)
    for i in range(32):
        a=i*math.tau/32;long=i%2==0;r0=.79;r1=1.21 if long else 1.04
        def p(r,angle,z):return [r*math.cos(angle),r*math.sin(angle),z]
        verts=[p(r0,a-.036,.035),p(r1,a,.035),p(r0,a+.036,.035),p(.95,a,-.065)]
        tri([verts[0],verts[1],verts[3]],gold);tri([verts[1],verts[2],verts[3]],light);tri([verts[2],verts[0],verts[3]],bronze)
    ring(.78,.065,gold);ring(.67,.022,light);ring(.61,.025,bronze)
    sphere((0,0,.06),(.59,.64,.16),bronze)
    for i in range(16):
        a=i*math.tau/16;sphere((.72*math.cos(a),.72*math.sin(a),-.08),(.025,.025,.03),light,6,10)
    sphere((0,-.03,-.12),(.39,.53,.24),gold)
    sphere((-.24,-.09,-.29),(.14,.21,.10),light);sphere((.24,-.09,-.29),(.14,.21,.10),light)
    for side in (-1,1):
        sphere((side*.18,.12,-.34),(.11,.055,.045),(46,29,14))
        sphere((side*.18,.13,-.38),(.065,.012,.018),(248,239,208))
        sphere((side*.19,.21,-.30),(.15,.055,.11),light)
        sphere((side*.35,.06,-.16),(.07,.16,.13),bronze)
    sphere((0,.04,-.34),(.065,.20,.12),light);sphere((0,-.14,-.41),(.08,.06,.065),gold)
    sphere((0,-.30,-.35),(.15,.021,.03),(60,36,16));sphere((0,-.35,-.31),(.15,.025,.055),light)
    sphere((0,-.45,-.18),(.18,.12,.10),gold)
    for i in range(5):
        a=-.66+i*.33;sphere((a*.4,.43-abs(a)*.13,-.25),(.05,.13,.06),light)
    return faces,None

def raster(faces,texture,yaw,pitch):
    camera=rot([pitch,yaw,0]);points=np.concatenate([p for p,_,_,_ in faces])@camera.T
    low,high=points[:,:2].min(0),points[:,:2].max(0);center=(low+high)/2
    scale=min(SIZE*.77/(high[0]-low[0]),SIZE*.83/(high[1]-low[1]))
    canvas=np.zeros((SIZE,SIZE,4),float);depth=np.full((SIZE,SIZE),np.inf)
    key=np.array([-.5,.72,-.6]);key/=np.linalg.norm(key)
    for points,uv,color,emissive in faces:
        p=points@camera.T;projected=(p[:,:2]-center)*[scale,-scale]+[SIZE*.5,SIZE*.47]
        x0,y0=np.maximum(0,np.floor(projected.min(0)).astype(int));x1,y1=np.minimum(SIZE-1,np.ceil(projected.max(0)).astype(int))
        if x1<x0 or y1<y0:continue
        a,b,c=projected;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9:continue
        yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
        zz=wa*p[0,2]+wb*p[1,2]+wc*p[2,2]
        visible=(wa>=-1e-5)&(wb>=-1e-5)&(wc>=-1e-5)&(zz<depth[y0:y1+1,x0:x1+1])
        if not visible.any():continue
        weights=np.array([wa[visible],wb[visible],wc[visible]]).T
        if texture is not None:
            sample=weights@uv;tx=np.clip((sample[:,0]*texture.shape[1]).astype(int),0,texture.shape[1]-1);ty=np.clip((sample[:,1]*texture.shape[0]).astype(int),0,texture.shape[0]-1)
            rgba=texture[ty,tx].copy()
        else:rgba=weights@color
        normal=np.cross(p[1]-p[0],p[2]-p[0]);normal/=max(1e-9,np.linalg.norm(normal))
        # Two-sided meshes have both outward orientations; favor the normal facing the camera.
        if normal[2]>0:normal=-normal
        shade=1 if emissive else .58+.48*max(0,float(normal@key))
        rgba[:,:3]=np.clip(rgba[:,:3]*shade+.045,0,1)
        solid=rgba[:,3]>.35;ys,xs=np.where(visible);ys+=y0;xs+=x0;ys=ys[solid];xs=xs[solid]
        canvas[ys,xs]=rgba[solid];depth[ys,xs]=zz[visible][solid]
    return Image.fromarray(np.uint8(np.clip(canvas*255,0,255)),'RGBA')

def mount(image,index,accent):
    # A filled, shaded backing supports dark anatomy against both a bright sky and the ink floor.
    rgb=np.array([(accent>>16)&255,(accent>>8)&255,accent&255]);ink=index>=8
    yy,xx=np.mgrid[0:SIZE,0:SIZE];r=np.sqrt(((xx-256)/202)**2+((yy-270)/204)**2)
    mask=np.clip((1-r)*40,0,1);base=np.zeros((SIZE,SIZE,4),np.uint8)
    light=np.clip(1-r,0,1)*(.10 if ink else .32)+np.clip((512-yy)/512,0,1)*(.045 if ink else .12)
    for c in range(3):base[:,:,c]=np.uint8(9+light*rgb[c])
    base[:,:,3]=np.uint8(mask*245);back=Image.fromarray(base,'RGBA');d=ImageDraw.Draw(back)
    outer=tuple(int(v*.65) for v in rgb)+(255,);edge=tuple(int(v*.75+60) for v in rgb)+(255,)
    if not ink:
        d.ellipse((48,62,464,478),outline=(4,6,9,230),width=15)
        d.arc((53,67,459,473),196,345,fill=edge,width=4);d.arc((53,67,459,473),17,177,fill=outer,width=4)
        d.arc((66,80,446,460),35,159,fill=outer,width=2)
        # Individual settings: cathedral rays, iron rivets, stone cuts and the wind's layered vanes.
        for k in range(12 if index==0 else 6):
            angle=math.radians(k*(30 if index==0 else 60)+15);cx=256+math.cos(angle)*211;cy=270+math.sin(angle)*211
            if index==0:d.line((cx,cy,256+math.cos(angle)*231,270+math.sin(angle)*231),fill=edge,width=4)
            else:d.ellipse((cx-4,cy-4,cx+4,cy+4),fill=edge)
    else:
        for k in range(3):d.arc((49+k*5,63+k*4,462-k*3,475-k*4),20+k*7,313-k*12,fill=tuple(rgb)+(155-k*35,),width=3-k)
        for k in range(15):
            a=k*2.399+index;rr=205+(k*13%26);cx=256+math.cos(a)*rr;cy=270+math.sin(a)*rr
            d.ellipse((cx-2,cy-2,cx+2,cy+2),fill=tuple(rgb)+(80+k*6,))
    if ink:
        pixels=np.array(image);v=pixels[:,:,:3].astype(float)/255
        pixels[:,:,:3]=np.uint8(np.clip(.045+.91*np.power(v,.58),0,1)*255);image=Image.fromarray(pixels,'RGBA')
        a=image.getchannel('A');rim=Image.new('RGBA',image.size,(166,172,173,0))
        rim.putalpha(a.filter(ImageFilter.MaxFilter(5)).point(lambda value:int(value*.65)))
        back.alpha_composite(rim)
    # A small cast shadow separates face plates, whiskers and crown from the backing.
    alpha=image.getchannel('A');shadow=Image.new('RGBA',image.size,(0,0,0,0));shadow.putalpha(alpha.filter(ImageFilter.GaussianBlur(9)))
    back.alpha_composite(shadow,(0,5));back.alpha_composite(image)
    return back.resize((256,256),Image.Resampling.LANCZOS)

def clean_preview():
    sheet=Image.new('RGB',(1200,900),(15,20,28));draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22)
    for i,spec in enumerate(SPECS):
        image=Image.open(OUT/(spec[0]+'.png')).convert('RGBA');xx=i%4*300;yy=i//4*300
        sheet.paste(image,(xx+22,yy),image)
        draw.text((xx+150,yy+263),spec[-1],font=font,fill=(222,219,207),anchor='mt')
    target=ROOT/'docs/previews/boss_hud_portraits_20261026.png';target.parent.mkdir(parents=True,exist_ok=True)
    sheet.save(target);return target

def main():
    assert hashlib.sha256(LIVE.read_bytes()).hexdigest().upper()==EXPECTED,'Installed source changed'
    OUT.mkdir(parents=True,exist_ok=True);DEST.mkdir(parents=True,exist_ok=True)
    atlas=Image.new('RGBA',(1024,768));sheet=Image.new('RGB',(1200,960),(15,20,28));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22);small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',14);stats=[]
    with zipfile.ZipFile(LIVE) as z:
        for i,(id,model,texture,roots,yaw,pitch,accent,label) in enumerate(SPECS):
            faces,tex=solar_relief() if i==0 else dragon(z) if model is None else gecko(z,model,texture,roots)
            assert len(faces)>0,id
            image=mount(raster(faces,tex,yaw,pitch),i,accent)
            image.save(OUT/(id+'.png'));atlas.paste(image,(i%4*256,i//4*256))
            xx=i%4*300;yy=i//4*320;sheet.paste(image,(xx+22,yy+5),image)
            # Show the real in-game sampling footprint beside the source-resolution art.
            thumb=image.resize((72,72),Image.Resampling.LANCZOS);sheet.paste(thumb,(xx+220,yy+204),thumb)
            d.text((xx+20,yy+270),label,font=font,fill=(222,219,207));d.text((xx+20,yy+301),f'{len(faces):,} source triangles',font=small,fill=(119,140,151))
            stats.append({'entity':'tnc:'+id,'triangles':len(faces),'atlasSlot':i,'model':model,'texture':texture,'dedicatedSolarRelief':i==0})
            print(id,len(faces),flush=True)
    atlas.save(DEST/'portraits_v1.png',optimize=True)
    (DEST/'portraits_v1.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':True}}),encoding='utf-8')
    sheet.save(OUT/'contact-sheet.png');(OUT/'sources.json').write_text(json.dumps({'installedSourceSha256':EXPECTED,'portraits':stats},ensure_ascii=False,indent=2),encoding='utf-8')
    clean_preview()
    print(OUT/'contact-sheet.png')

if __name__=='__main__':main()
