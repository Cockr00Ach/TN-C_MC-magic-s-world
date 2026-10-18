"""Generate expansion icons and editable Blockbench references; runtime geometry lives in Java.
Run after gen_casino_bbmodel.py. Models include movable groups and a real colour atlas.
"""
from pathlib import Path
import math,json,base64,io,re,uuid
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
OUT=ROOT/'model-source/casino'
PALETTE={'wood':0x493126,'dark':0x281C19,'brass':0xC2AA72,'felt':0x174C40,'ivory':0xF4EFE2,'blue':0x68303A,'red':0x863C46,'black':0x1A1A1E}
MULT=[0,1,0,2,0,1,0,4,0,2,0,1]
# Each tuple is (group, lower corner, upper corner, colour, optional Z rotation).
# Editable cube references; the runtime wheel uses solid curved sector meshes.
wheel=[('base',(.08,0,.08),(2.92,.15,1.32),'dark',0),
       ('base',(.13,.15,.13),(2.87,.19,1.27),'brass',0),
       ('base',(1.2,.19,.82),(1.8,2.16,1.16),'wood',0),
       ('pointer',(1.42,3.35,.40),(1.58,3.59,.55),'brass',0),
       ('pointer',(1.465,3.24,.39),(1.535,3.45,.44),'red',0),
       ('base',(.95,.25,.14),(2.05,.35,.62),'wood',0)]
for i in range(72):
    a=i*5
    wheel.append(('surround',(1.4375,3.35,.56),(1.5625,3.46,.82),'brass',a))
for i,m in enumerate(MULT):
    col='blue' if m==0 else 'brass' if m==4 else 'felt'
    for band in range(6):
        r=.23+band*.18;half=r*math.tan(math.radians(14.5))
        wheel.append(('wheel_spin',(1.5-half,2.03+r-.09,.55),(1.5+half,2.03+r+.09,.615),col,i*30))
    for mark in range(m):wheel.append(('wheel_spin',(1.39+mark*.06,2.98,.535),(1.425+mark*.06,3.11,.545),'ivory',i*30))
wheel.append(('wheel_spin',(1.28,1.81,.48),(1.72,2.25,.55),'brass',0))
cards=[]
for rx,rz,lo,hi,col in [(2,1.25,.79,.95,'wood'),(1.96,1.21,.95,.97,'brass'),
                       (1.92,1.17,.97,1.07,'blue'),(1.76,1.01,1.07,1.081,'brass'),(1.73,.98,1.081,1.09,'felt')]:
    for i in range(32):
        z0=-rz+2*rz*i/32;z1=-rz+2*rz*(i+1)/32;z=(z0+z1)/2
        half=rx*math.sqrt(1-z*z/(rz*rz))
        cards.append(('table',(2-half,lo,1.25+z0),(2+half,hi,1.25+z1),col,0))
for x in [.95,3.05]:
    cards += [('table',(x-.34,0,.65),(x+.34,.1,1.85),'dark',0),
              ('table',(x-.26,.1,.82),(x+.26,.15,1.68),'brass',0),
              ('table',(x-.19,.15,.94),(x+.19,.79,1.56),'wood',0)]
cards += [('shoe',(.63,1.09,1.52),(1.06,1.22,1.9),'dark',0),
          ('shoe',(.65,1.22,1.54),(1.04,1.24,1.88),'brass',0),
          ('shoe',(.68,1.24,1.58),(1.01,1.26,1.84),'blue',0),
          ('tray',(2.78,1.073,1.53),(3.51,1.093,1.81),'dark',0)]
for stack in range(4):
    for layer in range(3+stack%2):
        x=2.83+stack*.17;y=1.093+layer*.025
        cards.append(('chips',(x-.068,y,1.59),(x+.068,y+.021,1.73),['red','blue','ivory','felt'][stack],0))
for i in range(2):
    x=1.51+i*1.02;group='card_player' if i==0 else 'card_dealer'
    cards += [(group,(x-.22,1.117,.77),(x+.22,1.143,1.37),'ivory',0),
              (group,(x-.20,1.114,.79),(x+.20,1.116,1.35),'blue',0)]
    masks=re.findall(r'"([01]{7})"',(ROOT/'src/main/java/com/tnc/tnc/casino/client/CasinoCardFaces.java').read_text(encoding='utf-8'))
    for row,bits in enumerate(masks[(0 if i==0 else 3)*7:(1 if i==0 else 4)*7]):
        for col,bit in enumerate(bits):
            if bit=='1':
                px=x+(col-3.5)*.025;pz=1.11+(row-3.5)*.025
                cards.append((group,(px,1.144,pz),(px+.025,1.146,pz+.025),'red' if i==0 else 'black',0))

def ident(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'tnc-casino/'+name))
def save_model(name,parts):
    atlas=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(atlas)
    for i,rgb in enumerate(PALETTE.values()):d.rectangle(((i%8)*8,(i//8)*8,(i%8)*8+7,(i//8)*8+7),fill=('#%06x'%rgb))
    stream=io.BytesIO();atlas.save(stream,format='PNG')
    groups={};elements=[]
    for i,(group,lo,hi,col,rotation) in enumerate(parts):
        n=list(PALETTE).index(col);uid=ident(name+'/'+str(i));origin=(1.5,2.03,.64) if name=='lucky_wheel' else ((1.51 if group=='card_player' else 2.53),1.13,1.07)
        element={'name':group+'_'+str(i),'type':'cube','uuid':uid,'from':[v*16 for v in lo],'to':[v*16 for v in hi],
                 'origin':[v*16 for v in origin],'rotation':[0,0,rotation],'box_uv':False,
                 'faces':{f:{'uv':[(n%8)*8+1,(n//8)*8+1,(n%8)*8+6,(n//8)*8+6],'texture':0} for f in ['north','south','east','west','up','down']}}
        elements.append(element);groups.setdefault(group,[]).append(uid)
    outliner=[{'name':g,'uuid':ident(name+'/'+g),'origin':[24,32.48,10.24] if name=='lucky_wheel' else [24.16,18.08,17.12] if g=='card_player' else [40.48,18.08,17.12] if g=='card_dealer' else [(1.45+int(g[-1])*.4)*16,14.64,19.2] if g.startswith('die_') else [0,0,0],
               'rotation':[0,0,0],'children':ids} for g,ids in groups.items()]
    model={'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},'name':name,
           'model_identifier':'geometry.tnc.'+name,'visible_box':[4,4,4],'resolution':{'width':64,'height':64},
           'elements':elements,'outliner':outliner,'textures':[{'id':'0','name':'casino_palette.png','uuid':ident('atlas'),
            'source':'data:image/png;base64,'+base64.b64encode(stream.getvalue()).decode()}]}
    # Match the final runtime footprint, including animation pivots.
    scale={'lucky_wheel':(4/3,4/3,4/3),'card_table':(2,1,1.6),
           'gamble_table':(2,1,2),'roulette_table':(2,1,1)}.get(name,(1,1,1))
    for element in elements:
        for field in ('from','to','origin'):
            element[field]=[round(v*s,5) for v,s in zip(element[field],scale)]
    for group in outliner:
        group['origin']=[round(v*s,5) for v,s in zip(group['origin'],scale)]
    model['visible_box']=[8,2,4] if name in ('card_table','gamble_table','roulette_table') else [4,5,2] if name=='lucky_wheel' else [1,3,1]
    (OUT/(name+'.bbmodel')).write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8')

for name,parts in [('lucky_wheel',wheel),('card_table',cards)]:
    save_model(name,parts)
    # All eight block states resolve to an intentionally invisible BER host model.
    variants={f'facing={f},spinning={s}':{'model':'tnc:block/'+name} for f in ['north','south','east','west'] for s in ['true','false']}
    for folder,data in [('blockstates',{'variants':variants}),('models/block',{'parent':'minecraft:block/block','textures':{'particle':'tnc:block/gamble_table'},'elements':[]}),
                        ('models/item',{'parent':'minecraft:item/generated','textures':{'layer0':'tnc:item/casino_'+name}})]:
        path=ASSETS/folder/(name+'.json');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    icon=Image.new('RGBA',(64,64));d=ImageDraw.Draw(icon)
    if name=='lucky_wheel':
        d.rounded_rectangle((15,50,49,59),radius=3,fill='#281c19',outline='#c2aa72',width=2)
        d.rectangle((28,27,36,51),fill='#493126')
        for i,m in enumerate(MULT):d.pieslice((8,5,56,53),i*30-90,(i+1)*30-90,fill='#68303a' if m==0 else '#897449' if m==4 else '#174c40',outline='#c2aa72',width=1)
        d.ellipse((27,24,37,34),fill='#c2aa72');d.polygon([(29,2),(35,2),(32,12)],fill='#863c46')
    else:
        d.rectangle((10,38,16,57),fill='#281c19');d.rectangle((48,38,54,57),fill='#281c19')
        d.ellipse((3,15,61,46),fill='#493126',outline='#c2aa72',width=2)
        d.ellipse((6,18,58,43),fill='#68303a')
        d.ellipse((10,21,54,40),fill='#174c40',outline='#c2aa72',width=1)
        d.rectangle((17,24,27,37),fill='#f4efe2');d.rectangle((35,24,45,37),fill='#f4efe2')
        d.text((20,25),'A',fill='#863c46');d.text((38,25),'K',fill='#1a1a1e')
    icon.save(ASSETS/'textures/item'/('casino_'+name+'.png'))

# A model preview, not a claimed game screenshot. Use actual Java-derived existing dice-table body.
import sys
sys.path.insert(0,str(ROOT/'tools'))
import gen_casino_bbmodel as old
source=(ROOT/'src/main/java/com/tnc/tnc/casino/client/TNGambleTableRenderer.java').read_text(encoding='utf-8')
boxes,_=old.parse_boxes(source,'dice table');palette=old.parse_palette()
dice=[]
for lo,hi,col in boxes: dice.append(('table',lo,hi,'wood',0,palette[col]))
for i in range(3):
    x=1.45+i*.4;dice.append(('dice',(x-.1,.815,1.1),(x+.1,1.015,1.3),'ivory',0,PALETTE['ivory']))
    # Display a 1 / 3 / 5 sample in the model preview.
    dots=[(0,0)] if i==0 else [(-1,-1),(0,0),(1,1)] if i==1 else [(-1,-1),(-1,1),(0,0),(1,-1),(1,1)]
    for dx,dz in dots:dice.append(('pips',(x+dx*.055-.013,1.0155,1.2+dz*.055-.013),(x+dx*.055+.013,1.020,1.2+dz*.055+.013),'black',0,PALETTE['black']))

# Export the converted table with its three independent movable dice groups.
editor=[]
for j,item in enumerate(dice):
    group,lo,hi,col,rot,rgb=item
    key='colour_%06x'%rgb;PALETTE[key]=rgb
    if group in ('dice','pips'):
        center=(lo[0]+hi[0])/2
        group='die_'+str(min(range(3),key=lambda i:abs(center-(1.45+i*.4))))
    editor.append((group,lo,hi,key,rot))
save_model('gamble_table',editor)

# Give the original cabinet and roulette table real embedded colour atlases too.
# These are static-body references; runtime reels and roulette remain procedural.
for name,renderer in [('slot_machine','TNSlotMachineRenderer'),('roulette_table','TNRouletteRenderer')]:
    body=(ROOT/'src/main/java/com/tnc/tnc/casino/client'/(renderer+'.java')).read_text(encoding='utf-8')
    parts,_=old.parse_boxes(body,name)
    reference=[]
    for lo,hi,col in parts:
        if name=='roulette_table':
            lo=tuple(v*2 if i!=1 else v for i,v in enumerate(lo));hi=tuple(v*2 if i!=1 else v for i,v in enumerate(hi))
        key='colour_%06x'%palette[col];PALETTE[key]=palette[col]
        reference.append(('body_'+col.lower(),lo,hi,key,0))
    if name=='roulette_table':
        for i,col in enumerate(['red','felt','black']):
            z=.9+i*.85
            reference += [('button_'+col,(2.8,.96,z),(3.7,1.0,z+.7),'brass',0),
                          ('button_'+col,(2.84,1.0,z+.04),(3.66,1.025,z+.66),col,0)]
    save_model(name,reference)

canvas=Image.new('RGB',(1500,740),'#151e22');d=ImageDraw.Draw(canvas)
font_path='C:/Windows/Fonts/msyh.ttc'
font=ImageFont.truetype(font_path,26);small=ImageFont.truetype(font_path,17)
faces=[([0,1,2,3],.66),([0,4,5,1],.82),([1,5,6,2],.72),([3,2,6,7],1),([0,3,7,4],.88),([4,7,6,5],1)]
def preview(parts,cx,cy,scale,centerx,centerz):
    polys=[]
    for item in parts:
        group,lo,hi,col,rot=item[:5];rgb=item[5] if len(item)>5 else PALETTE[col]
        x0,y0,z0=lo;x1,y1,z1=hi
        points=[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1),(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)]
        if rot:
            a=math.radians(rot);points=[(1.5+(x-1.5)*math.cos(a)-(y-2.03)*math.sin(a),2.03+(x-1.5)*math.sin(a)+(y-2.03)*math.cos(a),z) for x,y,z in points]
        for ids,k in faces:
            pts=[points[j] for j in ids]
            proj=[(cx+(x-centerx+(z-centerz))*.86*scale,cy+((x-centerx)-(z-centerz))*.34*scale-y*scale) for x,y,z in pts]
            color=tuple(int(((rgb>>s)&255)*k) for s in (16,8,0))
            depth=sum(x-z+y*.68 for x,y,z in pts)/4
            polys.append((depth,proj,color))
    for depth,poly,color in sorted(polys,key=lambda q:q[0]):d.polygon(poly,fill=color,outline=tuple(max(0,c-8) for c in color))
for x in [0,500,1000]:d.rounded_rectangle((x+18,105,x+482,605),radius=18,fill='#223037',outline='#405158',width=1)
d.text((36,25),'TN-C 赌场设备 · 胡桃木 / 香槟金 / 酒红 / 墨绿',font=font,fill='#f7ead0')
d.text((36,66),'模型预览 · 实际动态与灯光需在游戏中验收',font=small,fill='#a7bec6')
preview(wheel,250,555,114,1.5,.7);preview(cards,750,490,86,2,1.25);preview(dice,1250,475,86,2,1)
for x,title,desc in [(40,'幸运转轮','3格宽大转盘 · 实体指针 · 十二扇区'),(540,'比大小牌桌','4×2.5格椭圆桌 · 软包扶手 · 筹码盘'),(1040,'三骰赌桌','保留旧桌 ID · 三骰弹跳 · 实体点面')]:
    d.text((x,625),title,font=font,fill='#f0d599');d.text((x,669),desc,font=small,fill='#b5cbd0')
path=ROOT/'docs/previews/casino_expansion_20261010.png';canvas.save(path)
print('Created two Blockbench models, icons, resources and',path)
