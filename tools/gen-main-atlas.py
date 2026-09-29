"""Native FTB opening hub and editable numbered atlas, with source-based previews."""
from pathlib import Path
import json, shutil
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'questbook/ftbquests/chapters';RES=ROOT/'src/main/resources'
def write(p,o):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
guide=RES/'assets/tnc/textures/guide';guide.mkdir(parents=True,exist_ok=True)
cover=Image.new('RGBA',(1200,420),(0,0,0,0));d=ImageDraw.Draw(cover);font=ImageFont.truetype('C:/Windows/Fonts/simkai.ttf',250)
d.text((600,145),'歸',font=font,anchor='mm',fill=(186,144,75,255));small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',27)
d.line((180,315,490,315),fill=(186,144,75,180),width=2);d.line((710,315,1020,315),fill=(186,144,75,180),width=2);d.text((600,315),'世界 · 旅途 · 归处',font=small,anchor='mm',fill=(215,198,161,255));cover.save(guide/'gui_world_title.png')
rows=[('arrived','找到天空岛','minecraft:ender_pearl',0,0,[], '沿天空岛邀请与传送阵上岛。你的旅程从这里开始。'),('met_self','去酒馆找 Self','minecraft:book',0,3,[0],'41号酒馆入门上楼北侧找Self。他会介绍原剧情线索、三家商店与委托栏。'),('self_materials','Self交给你的材料','minecraft:copper_ingot',-8,5,[1],'腾出背包，Self一次性交给4木棍与2铜锭；交给莉娅制作基础法器。领取记录按角色保存。'),('forged','潮生制杖屋 · 第一根法杖','tnc:water_wand_1',-9,9,[1,2],'29号店的莉娅制作法杖。先找酒馆艾琳登记，再带4木棍、2铜锭选任一元素一阶，首次免人工费。'),('armored','炉石铁匠铺 · 第一件装备','minecraft:iron_chestplate',-3,9,[1],'28号店找铎恩，粗铁＋煤制作基础铁装备。首件免人工费，保留原版装备属性；法杖交给制杖师。'),('home_bought','归航银行 · 安顿下来','minecraft:oak_door',3,9,[1],'19号银行米洛办理40号空屋的全款购买，500铜。钱袋账户已可存取实体币；存取币已开放；贷款与分期方案详见设计文档，尚未开放。'),('visited_entertainment','茶灯会馆 · 先认个门','minecraft:jukebox',9,9,[1],'到34号茶灯会馆附近认路。本次只保留娱乐地点；打牌、赌时刻等规则等作者后续设计，当前没有下注或赢钱。')]
nodes=[]
for i,(goal,title,icon,x,y,deps,desc) in enumerate(rows):
    write(RES/f'data/tnc/advancements/onboarding/{goal}.json',{'criteria':{'done':{'trigger':'minecraft:impossible'}}})
    nodes.append({'id':f'544E460200{i:06X}','title':title,'icon':icon,'x':x,'y':y,'size':1.6 if i<2 else 1.2,'shape':'hexagon','description':['&6&l'+title,'','&f'+desc],'dependencies':[f'544E460200{j:06X}' for j in deps],'rewards':[],'tasks':[{'id':f'544E460300{i:06X}','type':'advancement','advancement':'tnc:onboarding/'+goal,'criterion':'done','title':'完成真实行为'}]})
write(DEST/'tnc_play_00_main.snbt',{'id':'544E460100000000','filename':'tnc_play_00_main','group':'544E434755494445','title':'&6&l主线 · 找到天空岛','icon':'minecraft:compass','order_index':-190,'default_hide_dependency_lines':False,'default_quest_shape':'hexagon','quest_links':[],'images':[],'quests':nodes})
mapdir=ROOT/'work/revision2/town-map';records=json.loads((mapdir/'建筑编号.json').read_text(encoding='utf-8'));shutil.copy2(mapdir/'天空岛建筑编号.png',guide/'town_atlas.png')
nodes=[]
for r in records:
    i=r['number'];nodes.append({'id':f'544E470200{i:06X}','title':f'{i:02} · '+r['proposal'],'icon':'minecraft:map','x':(90+(r['x']-100)*5)/50-22.3,'y':(125+(r['z']-85)*5)/50-20.05+0.5,'size':0.55,'shape':'circle','description':['&6&l'+f'{i:02} · '+r['proposal'],'','&f用途类别：'+r['category'],'&7局部入口：'+'/'.join(map(str,r['entry'])),'&7世界坐标需加当前存档天空岛原点；手册有已开放岗位的动态坐标。','', '&f'+r['note']],'dependencies':[],'rewards':[],'tasks':[{'id':f'544E470300{i:06X}','type':'checkmark','title':'阅读建筑用途（不购房、不发奖励）'}]})
write(DEST/'tnc_guide_10_atlas.snbt',{'id':'544E470100000000','filename':'tnc_guide_10_atlas','group':'544E434755494445','title':'&3&l城镇图鉴 · 建筑与用途','icon':'minecraft:filled_map','order_index':-180,'default_hide_dependency_lines':True,'default_quest_shape':'circle','quest_links':[],'images':[{'image':'tnc:textures/guide/town_atlas.png','x':0.0,'y':0.0,'width':44.6,'height':40.1,'rotation':0.0}],'quests':nodes})

# Render the actual node coordinates as an offline proof, not a claimed game screenshot.
ch=json.loads((DEST/'tnc_play_03_elements.snbt').read_text(encoding='utf-8'));im=Image.new('RGB',(1800,1510),(25,39,50));dr=ImageDraw.Draw(im);f=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17);head=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',34)
dr.text((65,25),'提升之路 · 七系法器',font=head,fill='#ead7a5');dr.text((65,79),'从实际FTB节点坐标绘制 · 35件法器 / 28项介质 / 24项研习 · 非游戏截图',font=f,fill='#a6bdc0')
def xy(x,y):return (900+x*39,330+y*39)
byid={n['id']:n for n in ch['quests']}
for n in ch['quests']:
    for parent in n['dependencies']:
        a=byid[parent];dr.line((*xy(a['x'],a['y']),*xy(n['x'],n['y'])),fill='#6a8991',width=3)
for i,e in enumerate(['water','fire','lightning','wind','earth','light','dark']):
    art=Image.open(RES/f'assets/tnc/textures/item/wands/{e}_wand_5.png').convert('RGBA').resize((125,125),Image.Resampling.NEAREST);x,y=xy(-18+i*6,-4);im.paste(art,(int(x-62),int(y-62)),art)
for n in ch['quests']:
    x,y=xy(n['x'],n['y']);iswand='_wand_' in n['icon'];r=20 if iswand else 9;dr.ellipse((x-r,y-r,x+r,y+r),fill='#b29154' if iswand else '#52787c',outline='#dfd9c4',width=2)
    if iswand:
        art=Image.open(RES/('assets/tnc/textures/item/wands/'+n['icon'].split(':')[1]+'.png')).convert('RGBA').resize((36,36),Image.Resampling.NEAREST);im.paste(art,(int(x-18),int(y-18)),art)
    label=n['title'];dr.text((x,y+23 if iswand else y+12),label,font=f,anchor='mt',fill='#efe1bb' if iswand else '#adbdc5')
im.save(ROOT/'work/revision2/任务书七系排版预览.png')
print('Generated main hub, numbered native atlas, title and layout preview.')
manifest=ROOT/'docs/town-progression-manifest.json';data=json.loads(manifest.read_text(encoding='utf-8'))
data.update(total_real_gameplay_nodes=191,opening_hub_nodes=7,total_chapters=17,numbered_atlas_nodes=41)
write(manifest,data)
