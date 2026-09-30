"""Generate one additive chapter; never rebuild or overwrite the author's existing book."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
RANKS = ['冒险者', '精练', '大师', '传说']
STYLES = [
 ('bastion','壁垒战甲','守望宽檐帽','高防御与韧性，适合承受攻击。身甲有少量移速代价。'),
 ('astral','星织长袍','观星尖帽','提升魔力上限，适合连续施法与长时间战斗。'),
 ('runic','秘纹锁甲','回响法冠','身甲兼顾防御与魔力；法冠提供额外回魔。'),
 ('wanderer','远行外衣','行旅兜帽','轻装提高移速，适合探索、赶路和拉开距离。'),
]
ARMOR = [[8,11,13,15],[3,5,7,9],[7,9,12,14],[4,6,8,10]]
HAT_ARMOR = [[2,3,4,5],[1,1,2,2],[1,2,2,3],[1,2,3,3]]
MANA = [[0]*4,[30,65,110,170],[15,35,65,100],[0,10,20,35]]
FEES, LEVELS, SECONDS = [40,220,1200,6000],[1,20,45,70],[20,40,70,120]
chapter = dict(filename='tnc_equipment',id='544E474100000001',group='544E434755494445',
 title='&d&l装备 · 身甲与魔法帽',icon='tnc:astral_outfit_4',order_index=16,
 default_quest_shape='hexagon',default_hide_dependency_lines=False,quests=[],images=[])
counter = 0
def node(title,item,x,y,description,reading=False):
 global counter
 counter += 1
 qid=f'544E4742{counter:08X}';tid=f'544E4743{counter:08X}'
 task = dict(id=tid,type='checkmark',title='阅读装备说明') if reading else dict(id=tid,type='item',item=item,consume_items=False)
 chapter['quests'].append(dict(id=qid,title=title,icon=item,x=x,y=y,size=1.1,
  shape='hexagon',description=description,tasks=[task],rewards=[],dependencies=[]))
 return qid

node('&d&l两件装备，一身行装','tnc:astral_outfit_4',0,-6.2,[
 '&f上方穿魔法帽，下方穿完整身甲。身甲包含胸甲、裤子和鞋子的外观与防护，只占一个装备槽。',
 '&7款式决定用途，阶级决定强度；不按元素限制，可以自由搭配帽子和身甲。',
 '&e四阶：冒险者 → 精练 → 大师 → 传说。下方各列展示完整款式，点击装备查看属性。',
 '&f前往28号炉石铁匠铺，右键屋内的铎恩，选身甲或帽子、款式和阶级，准备材料后下单制作。',
 '&7本页物品目标只检查持有，不收走装备，也不额外发钱。'],True)
for si,(style,outfit,hat,about) in enumerate(STYLES):
 for ishat,name in [(False,outfit),(True,hat)]:
  x=(si*2+int(ishat)-3.5)*3.0
  kind='hat' if ishat else 'outfit'
  chapter['images'].append(dict(image=f'tnc:textures/item/gear/{style}_{kind}_4.png',x=x,y=-3.8,width=2.4,height=2.4,rotation=0.0))
  for ti,rank in enumerate(RANKS):
   armor=(HAT_ARMOR if ishat else ARMOR)[si][ti];stat=[f'防御 {armor}']
   mana=([0]*4 if si!=1 else [15,30,50,80])[ti] if ishat else MANA[si][ti]
   if mana:stat.append(f'魔力 +{mana}')
   if not ishat and si in (0,2):
    tough=([0,1,2,3] if si==0 else [0,0,1,2])[ti]
    if tough:stat.append(f'韧性 {tough}')
   if not ishat and si==0:stat.append(f'移速 −{[4,3,2,1][ti]}%')
   if ishat and si==0 and ti:stat.append(f'抗击退 +{[0,5,10,15][ti]}%')
   if ishat and si==2:stat.append(f'每2秒回魔 +{ti+1}')
   if si==3:stat.append(f'移速 +{([2,3,4,6] if ishat else [3,5,7,9])[ti]}%')
   node(f'{rank} · {name}',f'tnc:{style}_{kind}_{ti+1}',x,-1.7+ti*1.8,[
    f'&d&l{rank} · {name}','',f'&f{about}',f'&b'+ ' · '.join(stat),'',
    f'&e铎恩制作：Lv{LEVELS[ti]}，人工费{FEES[ti]}铜，制作{SECONDS[ti]}秒。',
    '&7材料清单在铁匠页面显示；备齐材料再确认，订单完成后回来领取。'])
node('&5&l神级 · 归墟神袍','tnc:divine_outfit_5',-2.0,7.8,[
 '&5&l修复遗物，重现归墟','',
 '&f防御16 · 韧性4 · 魔力+200 · 每2秒回魔+4 · 移速+4%。',
 '&e修复：Lv85，破损的归墟神袍×1、下界之星×1、回响碎片×8、下界合金锭×4、钻石×8；人工费12000铜，制作300秒。',
 '&7神装仅此一个款式。遗物所在地以后开放，本页不代表现已放入某个遗迹。'])
node('&5&l神级 · 归墟星冠','tnc:divine_hat_5',2.0,7.8,[
 '&5&l断辉重续，群星归位','',
 '&f防御4 · 魔力+100 · 每2秒回魔+3 · 施法耗蓝−8%。',
 '&e修复：Lv85，断辉星冠×1、下界之星×1、回响碎片×4、下界合金锭×2、钻石×4；人工费8000铜，制作300秒。',
 '&d两件神装合穿：普通致命伤保留1点生命，并回复20%魔力，冷却8分钟。',
 '&7遗物地点以后决定；拿到遗物后交给铎恩修复。'])
out=ROOT/'questbook/ftbquests/chapters/tnc_equipment.snbt'
out.write_text(json.dumps(chapter,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Generated {len(chapter["quests"])} nodes: {out}')
