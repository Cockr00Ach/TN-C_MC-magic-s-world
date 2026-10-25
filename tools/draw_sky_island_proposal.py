"""Draw a schematic planning map, not a game screenshot or approved construction blueprint."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
R=Path(__file__).resolve().parents[1]
out=R/'docs/design/2026-10-25-天空岛布局提案.png'
im=Image.new('RGB',(1600,1120),'#101c25');d=ImageDraw.Draw(im)
font='C:/Windows/Fonts/msyh.ttc'
def text(x,y,s,size=24,color='#e5e5d7'):
    d.text((x,y),s,font=ImageFont.truetype(font,size),fill=color)
text(70,40,'云溪群落 · 天空岛布局提案',40)
text(72,99,'一座主镇 · 两处小聚落 · 四片自然景区',23,'#89b9b7')
d.line((70,145,1530,145),fill='#8b7855',width=2)
# Organic island outline and terrain contours.
outline=[(380,235),(540,180),(710,220),(865,200),(1000,280),(1110,410),(1140,565),(1080,720),(970,840),(770,935),(590,880),(430,920),(280,800),(210,620),(245,440)]
d.polygon(outline,fill='#294a45');d.line(outline+[outline[0]],fill='#77aaa1',width=4)
d.line([(320,430),(390,290),(550,245),(705,280),(850,260),(965,330),(1045,460)],fill='#426458',width=22)
d.ellipse((540,215,825,345),fill='#4c6461')
d.ellipse((280,320,485,500),fill='#245743')
d.ellipse((845,335,1040,505),fill='#326f7b')
d.line([(935,465),(940,540),(890,615),(920,705),(1040,763)],fill='#6cbbcf',width=15)
d.line([(940,465),(947,540),(897,615),(927,705),(1047,763)],fill='#b4dadd',width=3)
# Roads: central network, no exact world coordinates.
roads=[[(670,820),(650,710),(660,585),(660,470),(690,320)],[(660,570),(520,615),(410,670)],[(660,570),(810,620),(915,635)],[(660,490),(510,440),(390,410)],[(660,490),(810,420),(930,400)]]
for pts in roads:d.line(pts,fill='#b9aa7c',width=10,joint='curve')
d.rounded_rectangle((510,455,795,600),radius=24,fill='#544f42',outline='#c0a86f',width=3)
text(540,474,'保留中心小镇',29)
text(535,521,'酒馆 · 协会 · 商铺',23)
text(535,555,'既有建筑与产权保留',19,'#c8c7b5')
def label(x,y,title,sub):
    text(x,y,title,27);text(x,y+39,sub,18,'#add2c5')
label(555,250,'星冠山脊 / 观星高台','第一次 F8 · 夜间教学')
label(255,356,'风铃林 / 石拱','树冠 · 林窗 · 隐藏符号')
label(866,354,'镜湖飞瀑','月影 · 水系魔脉')
label(282,605,'青穗田庄','梯田 · 果园 · 养殖')
label(818,580,'溪畔工坊','药师庭院 · 公共练习院')
label(563,820,'抵达台 / 迎客花坡','从南侧进入，先见景再见镇')
for x,y,w in [(340,710,46),(400,728,46),(460,744,57),(880,716,57),(760,740,68)]:
    d.rounded_rectangle((x,y,x+w,y+45),radius=4,fill='#637552',outline='#c5c586',width=2)
text(313,787,'可购地块示意',19)
# Annotation column.
text(1210,210,'设计重点',29,'#d8bf83')
notes=['主岛约 520 × 520 格','不扩大成另一座巨城','','西南：田园与生活','东南：实践与研究','北侧：首次观星震撼','','32×32 初居庭院','48×48 林溪庄园','64×64 田园大地块','','96×96 公会庄园','另放后期附属岛']
for i,s in enumerate(notes):text(1210,267+i*39,s,21)
text(70,1000,'方向与动线草图 · 非游戏效果图 · 非精确施工坐标',25,'#d8bf83')
text(70,1044,'待确认后再叠加现有建筑、地形和产权边界；本轮不改地图或主线。',22,'#a9b7b6')
out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
print(out)
