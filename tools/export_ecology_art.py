"""Export imagegen atlas cells to native game assets; no procedural replacement art.

The creative artwork lives in art/ecology-redesign-20261004/masters. This step
only slices, sizes and normalizes alpha/palette for Minecraft's pixel pipeline.
"""
from pathlib import Path
import json, shutil, hashlib
from PIL import Image, ImageDraw, ImageFont

R=Path(__file__).resolve().parents[1]
ART=R/'art/ecology-redesign-20261004'; A=R/'src/main/resources/assets/tnc'
MATERIALS=['leaf','bark','pollen','cotton','pepper','ice','violet','rose','paper','copper','linen','wood','iron','hay','grain','pellets']

def cell(im,index):
    w,h=im.size;x=index%4;y=index//4
    return im.crop((round(x*w/4),round(y*h/4),round((x+1)*w/4),round((y+1)*h/4)))

def connected(alpha):
    w,h=alpha.size;p=alpha.load();seen=set();out=[]
    for y in range(h):
        for x in range(w):
            if not p[x,y] or (x,y) in seen:continue
            todo=[(x,y)];seen.add((x,y));group=[]
            while todo:
                a,b=todo.pop();group.append((a,b))
                for dx,dy in [(0,1),(0,-1),(1,0),(-1,0),(1,1),(1,-1),(-1,1),(-1,-1)]:
                    xx,yy=a+dx,b+dy
                    if 0<=xx<w and 0<=yy<h and p[xx,yy] and (xx,yy) not in seen:seen.add((xx,yy));todo.append((xx,yy))
            out.append(group)
    return sorted(out,key=len,reverse=True)

def box(group):return (min(x for x,y in group),min(y for x,y in group),max(x for x,y in group)+1,max(y for x,y in group)+1)

def trim_foreign(tile):
    # Remove isolated atlas-cell bleed while preserving detached pieces of the item.
    small=tile.resize((128,128),Image.Resampling.NEAREST);alpha=small.getchannel('A').point(lambda x:255 if x>=160 else 0)
    groups=connected(alpha)
    if not groups:raise ValueError('Empty generated cell')
    main=box(groups[0]);keep=Image.new('L',(128,128));p=keep.load()
    for g in groups:
        b=box(g);dx=max(main[0]-b[2],b[0]-main[2],0);dy=max(main[1]-b[3],b[1]-main[3],0)
        edge=b[0]<=1 or b[1]<=1 or b[2]>=127 or b[3]>=127
        if g is not groups[0] and edge and len(g)<len(groups[0])*.4:continue
        if g is groups[0] or max(dx,dy)<=3 or (len(g)>=len(groups[0])*.18 and not edge):
            for x,y in g:p[x,y]=255
    native=tile.getchannel('A');mask=keep.resize(tile.size,Image.Resampling.NEAREST)
    from PIL import ImageChops
    tile.putalpha(ImageChops.multiply(native,mask));return tile

def partial_cells(im,count):
    # Imagegen lays a partially filled last row across its width. Segment these
    # isolated objects instead of assuming four equal columns and cutting them.
    small=im.resize((192,192),Image.Resampling.NEAREST);groups=connected(small.getchannel('A').point(lambda x:255 if x>=160 else 0))
    main=sorted(groups[:count],key=lambda g:(box(g)[0]+box(g)[2])/2)
    results=[]
    for g in main:
        b=box(g);pad=3
        results.append(im.crop((max(0,b[0]-pad)*im.width/192,max(0,b[1]-pad)*im.height/192,min(192,b[2]+pad)*im.width/192,min(192,b[3]+pad)*im.height/192)))
    return results

def pixel_export(tile,size,trim=False):
    tile=tile.convert('RGBA')
    alpha=tile.getchannel('A').point(lambda x:255 if x>=160 else 0)
    tile.putalpha(alpha)
    if trim:
        tile=trim_foreign(tile);alpha=tile.getchannel('A')
        box=alpha.getbbox()
        if box is None:raise ValueError('Empty sprite cell')
        tile=tile.crop(box);w,h=tile.size;ratio=(size-4)/max(w,h)
        tile=tile.resize((max(1,round(w*ratio)),max(1,round(h*ratio))),Image.Resampling.NEAREST)
        dest=Image.new('RGBA',(size,size));dest.alpha_composite(tile,((size-tile.width)//2,(size-tile.height)//2));tile=dest
    else:tile=tile.resize((size,size),Image.Resampling.NEAREST)
    # Discrete pixel material ramps; no dithering or interpolated alpha fringes.
    alpha=tile.getchannel('A');rgb=tile.convert('RGB').quantize(colors=32,method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE).convert('RGB')
    rgb.putalpha(alpha);return rgb

def main():
    manifest=json.loads((ART/'manifest.json').read_text(encoding='utf8'));backup=R/'work/backups/ecology-art-before-20261004'
    items=[]
    for sheet in manifest['sheets']:
        master=Image.open(ART/'masters'/f"{sheet['id']}.png").convert('RGBA')
        partial=partial_cells(master,len(sheet['assets'])) if len(sheet['assets'])<16 else None
        for i,asset in enumerate(sheet['assets']):
            name=asset['id'];path=A/f'textures/item/{name}.png'
            if path.exists() and not (backup/name).exists():
                backup.mkdir(parents=True,exist_ok=True);shutil.copy2(path,backup/name)
            im=pixel_export(partial[i] if partial else cell(master,i),32,True);im.save(path);items.append((name,im))
    mat=Image.open(ART/'masters/materials.png')
    for i,name in enumerate(MATERIALS):pixel_export(cell(mat,i),16).save(A/f'textures/block/art_{name}.png')
    ids=[n for n,_ in items];hashes=[hashlib.sha256(im.tobytes()).hexdigest() for _,im in items]
    assert len(set(hashes))==len(hashes),'Duplicate exported sprites'
    for name,im in items:
        assert set(im.getchannel('A').getdata())<={0,255},name
        assert len(im.getcolors(1025) or [])<=33,name
        assert im.getbbox() is not None,name
    preview=ART/'previews';preview.mkdir(exist_ok=True)
    font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',10)
    for page,start in enumerate(range(0,len(items),54),1):
        selected=items[start:start+54];cols=9;rows=(len(selected)+cols-1)//cols
        canvas=Image.new('RGB',(cols*104,rows*122+32),'#25272b');draw=ImageDraw.Draw(canvas)
        draw.text((12,9),f'ACTUAL 32px GAME ASSETS / {page} / nearest-neighbour preview',font=font,fill='#e2d4b4')
        for i,(name,im) in enumerate(selected):
            x=(i%cols)*104;y=(i//cols)*122+32
            draw.rectangle((x+2,y+2,x+101,y+101),fill='#8b8b8b',outline='#373737',width=3)
            scaled=im.resize((80,80),Image.Resampling.NEAREST);canvas.paste(scaled,(x+12,y+12),scaled)
            draw.text((x+3,y+105),name[:16],font=font,fill='#dacbaa')
        canvas.save(preview/f'items-{page}.png')
    (ART/'export-audit.json').write_bytes((json.dumps({'sprites':len(items),'material_tiles':len(MATERIALS),'unique_sprites':len(set(hashes)),'native_sprite_size':32,'native_tile_size':16,'alpha':'binary','palette_limit':32,'ids':ids},indent=2)+'\n').encode())
    print('EXPORTED',len(items),'unique sprites and',len(MATERIALS),'material tiles')

if __name__=='__main__':main()
