"""Render source templates only; numbered building/entrance groups are planning proposals."""
from pathlib import Path
import json, math, csv
import numpy as np
import nbtlib
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc'
OUT=ROOT/'work/revision2/town-map';OUT.mkdir(parents=True,exist_ok=True)
manifest=json.loads((DATA/'sky_island/manifest.json').read_text(encoding='utf-8-sig'))
top=np.full((520,520),-1,dtype=np.int16); names=np.empty((520,520),dtype=object);names[:]=''
blocks={}; doors=[]
for piece in manifest['pieces']:
    if piece['layer'] not in ('town','tavern'):continue
    t=nbtlib.load(DATA/'structures'/(piece['resource'].split(':')[1]+'.nbt'))
    palette=[(str(p['Name']),{str(k):str(v) for k,v in p.get('Properties',{}).items()}) for p in t['palette']]
    for b in t['blocks']:
        x,y,z=[int(v)+o for v,o in zip(b['pos'],piece['offset'])];name,props=palette[int(b['state'])]
        if name.endswith(':air') or name.endswith('_air'):continue
        blocks[(x,y,z)]=(name,props)
        if 'door' in name and 'trapdoor' not in name and props.get('half')=='lower':doors.append((x,y,z))
        if y<85 or not(0<=x<520 and 0<=z<520):continue
        if any(k in name for k in ('leaves','vine','tall_grass')):continue
        if y>=top[z,x]:top[z,x]=y;names[z,x]=name

# The current island also has a compare-and-set landscape overlay containing the
# tavern. Render its AFTER states, not the old bare template preview.
overlay=nbtlib.load(ROOT/'src/main/resources/data/tnc/sky_island/landscape_v1.nbt')
palette=[(str(p['Name']),{str(k):str(v) for k,v in p.get('Properties',{}).items()}) for p in overlay['Palette']]
for tile in overlay['Tiles']:
    values=tile['Changes']
    for i in range(0,len(values),5):
        x,y,z,before,after=map(int,values[i:i+5]);name,props=palette[after]
        if name.endswith(':air') or name.endswith('_air'):blocks.pop((x,y,z),None)
        else:blocks[(x,y,z)]=(name,props)
top[:]=-1;names[:]=''
for (x,y,z),(name,props) in blocks.items():
    if y<85 or not(0<=x<520 and 0<=z<520) or any(k in name for k in ('leaves','vine','tall_grass')):continue
    if y>=top[z,x]:top[z,x]=y;names[z,x]=name
json.dump({','.join(map(str,k)):v for k,v in blocks.items() if 410<=k[0]<=490 and 260<=k[2]<=330},(OUT/'tavern-blocks.json').open('w',encoding='utf-8'),ensure_ascii=False)

def color(n):
    if not n:return (93,128,86)
    if 'water' in n:return (78,135,161)
    if 'grass' in n or 'moss' in n:return (115,145,83)
    if 'dirt' in n or 'farmland' in n:return (129,99,64)
    if 'log' in n or 'wood' in n:return (88,61,46)
    if 'planks' in n:return (184,146,103)
    if 'terracotta' in n or 'brick' in n:return (156,90,63)
    if 'copper' in n:return (128,112,75)
    if 'sand' in n or 'quartz' in n:return (221,202,158)
    if 'glass' in n:return (131,190,198)
    if 'wool' in n or 'concrete' in n:return (211,196,172)
    if 'stone' in n or 'andesite' in n or 'slate' in n:return (147,148,142)
    if 'door' in n:return (105,67,45)
    if 'hay' in n:return (204,168,72)
    return (189,172,145)

pixels=np.array([[color(n) for n in row] for row in names],dtype=np.uint8)
for z in range(1,520):
    for x in range(1,520):
        if top[z,x]>=85:
            slope=int(top[z-1,x])-int(top[z,x]);factor=0.85 if slope<-1 else 1.10 if slope>1 else 1
            pixels[z,x]=np.clip(pixels[z,x].astype(float)*factor,0,255)

# Entrances within one multistory complex are grouped, not assigned separate shops.
remaining=set(doors);groups=[]
while remaining:
    first=min(remaining,key=lambda p:(p[2],p[0],p[1]));remaining.remove(first);group=[first];todo=[first]
    while todo:
        a=todo.pop();near=[b for b in remaining if (a[0]-b[0])**2+(a[2]-b[2])**2<=100 and abs(a[1]-b[1])<=18]
        for b in near:remaining.remove(b);group.append(b);todo.append(b)
    groups.append(group)

records=[]
for i,g in enumerate(sorted(groups,key=lambda g:(min(p[2] for p in g),min(p[0] for p in g))),1):
    entry=min(g,key=lambda p:(p[1],p[2],p[0]));x=sum(p[0] for p in g)/len(g);z=sum(p[2] for p in g)/len(g)
    role='待售候选';use='玩家住宅候选；需逐屋标注边界与预检后才可开放'
    if x>305 and z<225:role='公共保留';use='北区公共建筑/教会/学术设施，按整栋保留'
    elif z<205:role='公共保留';use='高台住宅/学舍；建议NPC与剧情公共用途'
    elif x<180:role='居民保留';use='西街居民宅；生活邻里与街区氛围'
    if min((p[0]-189)**2+(p[2]-394)**2 for p in g)<36:role='已开放房源';use='南街三层屋，500铜；已有产权与装修保护'
    if min((p[0]-198)**2+(p[2]-262)**2 for p in g)<36:role='NPC公共';use='铸器工坊候选；独立铁匠负责法器订单'
    if min((p[0]-231)**2+(p[2]-237)**2 for p in g)<36:role='NPC公共';use='城务房产事务所候选；独立房产负责人'
    if min((p[0]-331)**2+(p[2]-251)**2 for p in g)<36:role='居民保留';use='林禾/乔麦邻里住宅，避免出售'
    if min((p[0]-363)**2+(p[2]-279)**2 for p in g)<36:role='居民保留';use='蓝汐/燕岚邻里住宅，避免出售'
    if min((p[0]-222)**2+(p[2]-352)**2 for p in g)<36:role='居民保留';use='何舟/苏苒邻里住宅，避免出售'
    records.append(dict(number=i,x=round(x,1),z=round(z,1),entry=list(entry),doors=len(g),category=role,proposal=use))
records.append(dict(number=len(records)+1,x=450,z=291,entry=[419,90,291],doors=0,category='NPC公共',proposal='新增酒馆：Bountiful委托栏＋协会接待员；Self负责既有剧情'))
proposals={1:'法师学舍；导师与魔法石资料馆',2:'北区连体公共楼入口区：档案与藏书',3:'北区连体公共楼入口区：冥想与研习',4:'北区连体公共楼入口区：治疗与静养',5:'北区连体公共楼入口区：教堂/礼拜空间',6:'西高台学徒宿舍',7:'西高台导师住所',8:'北区宗教/学术附属楼',9:'北区主殿，剧情公共建筑保留',10:'水池广场入口，公共活动区',11:'东侧巡逻与观测岗',12:'协会仓库/补给铺候选',13:'药草铺候选',14:'公共花园与温室入口',15:'东区巡逻岗候选',16:'玩家中型宅候选',17:'玩家商住宅候选',18:'圆形训练场北门（与20号同一场地）',19:'归航银行：米洛办理全款购房；借贷/分期为设计方案',20:'圆形训练场南门（与18号同一场地）',21:'餐馆/公共厨房候选',22:'远行装备铺候选',23:'林禾/乔麦家，居民保留',24:'玩家双层住宅候选',25:'西街居民宅保留',26:'玩家小宅候选',27:'玩家小宅候选',28:'炉石铁匠铺：铎恩制作基础装备',29:'潮生制杖屋：莉娅制作七系五阶法器',30:'烘焙铺候选',31:'蓝汐/燕岚家，居民保留',32:'街心旅舍/商会候选',33:'小型观测亭/公共展陈候选',34:'茶灯会馆：娱乐预留，打牌等后续设计',35:'玩家联排住宅候选',36:'玩家庭院小宅候选',37:'何舟/苏苒家，居民保留',38:'玩家大宅候选',39:'玩家小宅候选',40:'南街三层屋，500铜；唯一已开放房源',41:'东侧酒馆：上楼北侧Bountiful原生栏与艾琳；Self仍负责剧情'}
public={12,13,18,20,21,22,29,30,32,33,34}
for r in records:
    r['proposal']=proposals[r['number']]
    if r['number'] in public:r['category']='NPC公共' if r['number'] not in {18,20,33} else '公共保留'
    r['note']='建筑/入口群编号；连体楼及18/20号需按用途分区人工核对。候选商店尚未实现交易，候选住宅尚未出售。'
    if r['number']==19:r['proposal']='归航银行：米洛办理存取币与全款购房；借贷/分期为设计方案'
    if r['number']==41:r['proposal']='东侧酒馆：上楼北侧Self、艾琳与Bountiful原生委托栏'
json.dump(records,(OUT/'建筑编号.json').open('w',encoding='utf-8'),ensure_ascii=False,indent=2)
with (OUT/'建筑用途提案.csv').open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f);w.writerow(['编号','类别','建议用途','局部入口X','局部入口Y','局部入口Z','门数量'])
    for r in records:w.writerow([r['number'],r['category'],r['proposal'],*r['entry'],r['doors']])

FONT='C:/Windows/Fonts/msyh.ttc';f=ImageFont.truetype(FONT,22);small=ImageFont.truetype(FONT,16);title=ImageFont.truetype(FONT,36)
scale=5;ox=100;oz=85;width=410;height=355;margin=90
canvas=Image.new('RGB',(width*scale+margin*2,height*scale+230),(245,239,224));draw=ImageDraw.Draw(canvas)
draw.text((margin,24),'天空岛建筑编号 · 用途提案',font=title,fill=(29,47,52))
draw.text((margin,77),'北 ↑  |  建筑与入口区编号；18/20为同一训练场两门，连体楼需核对分区。图源为当前NBT，未修改存档。',font=small,fill=(83,94,92))
canvas.paste(Image.fromarray(pixels[oz:oz+height,ox:ox+width]).resize((width*scale,height*scale),Image.Resampling.NEAREST),(margin,125));draw=ImageDraw.Draw(canvas)
categories={'NPC公共':(179,96,49),'公共保留':(57,102,127),'居民保留':(93,116,71),'待售候选':(128,87,123),'已开放房源':(203,139,44)}
for v in range(100,511,50):
    xx=margin+(v-ox)*scale;draw.line((xx,125,xx,125+height*scale),fill=(185,185,167),width=1);draw.text((xx-12,105),str(v),font=small,fill=(83,94,92))
for v in range(100,441,50):
    yy=125+(v-oz)*scale;draw.line((margin,yy,margin+width*scale,yy),fill=(185,185,167),width=1);draw.text((35,yy-8),str(v),font=small,fill=(83,94,92))
for r in records:
    x=margin+(r['x']-ox)*scale;y=125+(r['z']-oz)*scale;c=categories[r['category']]
    draw.ellipse((x-19,y-19,x+19,y+19),fill=c,outline=(255,245,221),width=3)
    text=f"{r['number']:02}";box=draw.textbbox((0,0),text,font=f);draw.text((x-(box[2]-box[0])/2,y-16),text,font=f,fill='white')
y=150+height*scale
for j,(k,c) in enumerate(categories.items()):
    x=margin+j*310;draw.ellipse((x,y,x+17,y+17),fill=c);draw.text((x+26,y-5),k,font=f,fill=(29,47,52))
canvas.save(OUT/'天空岛建筑编号.png')
# Data cache also allows checking candidate NPC spawn floors without reading any player save.
json.dump({','.join(map(str,k)):v for k,v in blocks.items() if 190<=k[0]<=335 and 225<=k[2]<=290},(OUT/'service-area-blocks.json').open('w',encoding='utf-8'),ensure_ascii=False)
print('numbered entrance/building groups',len(records));print([(r['number'],r['entry'],r['proposal']) for r in records if r['category']=='NPC公共'])
