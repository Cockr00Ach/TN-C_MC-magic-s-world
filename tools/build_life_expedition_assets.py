"""Original low-resolution assets and data for the established TN-C pixel/model pipeline."""
from pathlib import Path
import hashlib,json,math,re,random
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc';D=R/'src/main/resources/data/tnc'
def write(p,obj):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n','utf8')
def png(p,img):p.parent.mkdir(parents=True,exist_ok=True);img.save(p)
def cube(a,b,tex,rotation=None):
 o={'from':a,'to':b,'faces':{s:{'uv':[0,0,16,16],'texture':'#'+tex} for s in ['north','south','east','west','up','down']}}
 if rotation:o['rotation']=rotation
 return o
def tile(name,color):
 im=Image.new('RGBA',(32,32));r=random.Random(name)
 for y in range(32):
  for x in range(32):
   n=r.randrange(-12,13);im.putpixel((x,y),tuple(max(0,min(255,v+n)) for v in color)+(255,))
 d=ImageDraw.Draw(im)
 if name=='alchemy_bronze':
  for y in [2,15,29]:d.line((0,y,31,y),fill=(196,157,91,255));d.line((0,y+1,31,y+1),fill=(67,54,43,255))
 if name=='alchemy_ceramic':
  for x,y in [(4,7),(22,21),(12,27)]:d.line((x,y,x+3,y-3,x+5,y),fill=(72,111,114,255))
 png(A/f'textures/block/{name}.png',im)
for n,c in [('alchemy_bronze',(125,92,54)),('alchemy_ceramic',(34,55,65)),('alchemy_glow',(72,190,187)),('alchemy_wood',(43,53,66))]:tile(n,c)
png(A/'textures/misc/alchemy_white.png',Image.new('RGBA',(2,2),'white'))

# Octagonal open bowl, actual cavity, four feet, two handles, engraved collars and gem settings.
parts=[cube([3,3,3],[13,4,13],'ceramic'),cube([2,4,3],[4,11,13],'ceramic'),cube([12,4,3],[14,11,13],'ceramic'),cube([3,4,2],[13,11,4],'ceramic'),cube([3,4,12],[13,11,14],'ceramic')]
for x,z in [(2,2),(11,2),(2,11),(11,11)]:parts.extend([cube([x,0,z],[x+3,3,z+3],'bronze'),cube([x+.5,3,z+.5],[x+2.5,6,z+2.5],'bronze')])
for y in [4,10,12]:
 parts.extend([cube([2,y,2],[14,y+1,3],'bronze'),cube([2,y,13],[14,y+1,14],'bronze'),cube([2,y,3],[3,y+1,13],'bronze'),cube([13,y,3],[14,y+1,13],'bronze')])
for x in [0,14]:
 parts.extend([cube([x,8,5],[x+2,9,11],'bronze'),cube([x,9,5],[x+1,12,6],'bronze'),cube([x,9,10],[x+1,12,11],'bronze'),cube([x,12,5],[x+2,13,11],'bronze')])
for x,z in [(7,1.8),(7,13),(1.8,7),(13,7)]:parts.append(cube([x,7,z],[x+2,9,z+1.2],'glow'))
display={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.78,.78,.78]},'ground':{'translation':[0,3,0],'scale':[.45,.45,.45]},'fixed':{'scale':[1,1,1]}}
textures={n:f'tnc:block/alchemy_{n}' for n in ['ceramic','bronze','glow','wood']};textures['particle']=textures['ceramic']
write(A/'models/block/spellflame_cauldron.json',{'textures':textures,'elements':parts,'display':display,'ambientocclusion':False})
write(A/'models/item/spellflame_cauldron.json',{'parent':'tnc:block/spellflame_cauldron'})
write(A/'blockstates/spellflame_cauldron.json',{'variants':{'':{'model':'tnc:block/spellflame_cauldron'}}})
cache=[cube([2,1,3],[14,9,13],'wood'),cube([2,9,3],[14,12,13],'ceramic')]
for x in [2,6,12]:cache.append(cube([x,1,2.8],[x+1,12,13.2],'bronze'))
for z in [3,12]:cache.append(cube([2,8,z],[14,9,z+1],'bronze'))
cache.extend([cube([7,6,2],[10,10,3],'bronze'),cube([7.5,7,1.8],[9.5,9,2.2],'glow')])
write(A/'models/item/echo_hidden_cache.json',{'textures':textures,'elements':cache,'display':display,'ambientocclusion':False})
write(A/'models/block/echo_hidden_cache.json',{'textures':textures,'elements':[]})
write(A/'blockstates/echo_hidden_cache.json',{'variants':{'':{'model':'tnc:block/echo_hidden_cache'}}})
write(D/'loot_tables/blocks/spellflame_cauldron.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:spellflame_cauldron','functions':[{'function':'minecraft:copy_nbt','source':'block_entity','ops':[{'source':n,'target':'BlockEntityTag.'+n,'op':'replace'} for n in ['Items','Progress','Burn','Fire','Job','Color']]}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

source=(R/'src/main/java/com/tnc/tnc/alchemy/Elixir.java').read_text('utf8')
elixirs=[]
for m in re.finditer(r'\w+\("(\w+)","([^"]+)",0x([0-9a-f]+),"([^"]+)",(\d+),(\d+),([^;\n]+)\)',source):
 id,name,color,help,seconds,ticks,args=m.groups();ingredients=re.findall('"([^"]+)"',args);elixirs.append(dict(id=id,name=name,color=int(color,16),help=help,seconds=int(seconds),ticks=int(ticks),ingredients=ingredients))
assert len(elixirs)==16
for i,e in enumerate(elixirs):
 # Shapes vary by potion family: flask, tall ampoule, squared decanter, teardrop, faceted bottle.
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);col=tuple((e['color']>>s)&255 for s in [16,8,0]);edge=(28,47,55,255);glass=(123,174,171,255);highlight=(219,245,224,255)
 outlines=[[(12,3),(19,3),(19,10),(25,16),(25,24),(21,29),(10,29),(6,24),(6,16),(12,10)],[(13,2),(18,2),(18,8),(21,12),(21,27),(18,30),(12,30),(10,27),(10,12),(13,8)],[(11,4),(20,4),(20,10),(24,12),(24,27),(7,27),(7,12),(11,10)],[(13,2),(18,2),(18,12),(24,21),(24,25),(19,29),(12,29),(7,25),(7,21),(13,12)],[(12,3),(19,3),(19,10),(26,17),(23,26),(16,30),(8,26),(5,17),(12,10)]]
 points=outlines[i%len(outlines)];d.polygon(points,fill=glass,outline=edge,width=1)
 liquid=Image.new('RGBA',im.size);ld=ImageDraw.Draw(liquid);ld.polygon([(x, y) for x,y in points],fill=col+(255,));ld.rectangle((0,0,31,14+(i%4)),fill=(0,0,0,0));im.alpha_composite(liquid);d=ImageDraw.Draw(im);d.line(points+[points[0]],fill=edge,width=1)
 d.line((10,17,10,23),fill=highlight,width=1);d.point((11,16),fill=highlight);d.line((21,19,21,24),fill=tuple(max(0,v-45) for v in col)+(255,));d.rectangle((12,3,19,6),fill=(132,96,54,255));d.line((12,3,19,3),fill=(221,187,116,255));d.line((11,9,20,9),fill=(180,147,82,255));d.point((17,19),fill=(245,236,190,255));
 # Different embedded motifs, not a recolor of one generic oval.
 if i%4==0:d.line((13,24,15,22,17,24,19,22),fill=highlight)
 elif i%4==1:d.ellipse((13,21,18,25),outline=highlight)
 elif i%4==2:d.polygon([(16,20),(19,23),(16,26),(13,23)],outline=highlight)
 else:d.line((13,25,18,20),fill=highlight,width=2)
 png(A/f'textures/item/elixir_{e["id"]}.png',im);write(A/f'models/item/elixir_{e["id"]}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/elixir_{e["id"]}'}})
fuel=Image.new('RGBA',(32,32));d=ImageDraw.Draw(fuel);d.polygon([(5,23),(8,15),(14,11),(17,4),(20,12),(26,19),(26,27),(10,29)],fill=(76,66,49,255),outline=(27,41,46,255));d.polygon([(9,23),(12,17),(17,12),(20,18),(23,24),(18,27)],fill=(63,200,181,255));d.polygon([(14,23),(17,18),(20,24),(17,26)],fill=(246,193,85,255));png(A/'textures/item/ember_blossom_salt.png',fuel);write(A/'models/item/ember_blossom_salt.json',{'parent':'minecraft:item/generated','textures':{'layer0':'tnc:item/ember_blossom_salt'}})
png(A/'textures/mob_effect/echo_sight.png',Image.open(A/'textures/item/elixir_echo.png').resize((18,18),Image.Resampling.NEAREST))

for locale in ['zh_cn','en_us']:
 p=A/f'lang/{locale}.json';lang=json.loads(p.read_text('utf8'));lang.update({'block.tnc.spellflame_cauldron':'魔焰炼药釜','block.tnc.echo_hidden_cache':'回声隐匣','item.tnc.ember_blossom_salt':'焰蕊盐','effect.tnc.echo_sight':'回声显影','key.tnc.elixir_sip':'饮用携行魔药'});lang.update({f'item.tnc.elixir_{e["id"]}':e['name'] for e in elixirs});write(p,lang)
def shaped(id,pattern,key,out,count=1):write(D/f'recipes/{id}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'item':out,'count':count}})
shaped('spellflame_cauldron',['C C','IBI',' I '],{'C':'minecraft:copper_ingot','I':'minecraft:iron_ingot','B':'minecraft:cauldron'},'tnc:spellflame_cauldron')
write(D/'recipes/ember_blossom_salt.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'tnc:hearth_pepper_fruit'},{'item':'minecraft:charcoal'},{'item':'minecraft:glowstone_dust'}],'result':{'item':'tnc:ember_blossom_salt','count':4}})
offers=[('road_watch','路灯下的脚步','清理路边6只僵尸，让晚归的人安心。','kill','minecraft:zombie',6,1,75,80),('bone_rattle','骨哨声','巡路人说，夜里有弓弦在树后响。讨伐5只骷髅。','kill','minecraft:skeleton',5,1,90,100),('web_watch','屋檐蛛影','替送货人清理4只蜘蛛，保留小镇外的道路。','kill','minecraft:spider',4,1,70,75),('goblin_patrol','绿影巡查','地下城的哥布林斥候越来越多，清理6名战士。','kill','tnc:goblin_warrior',6,1,110,120),('desert_survey','黄沙勘路','记录黄沙遗庭3次真实下层经历。','floor','desert',3,1,120,130),('crypt_survey','旧钟下的路','穿过锁钟地牢的3层，回来说说你听见的钟声。','floor','crypt',3,1,120,130),('tide_survey','潮声探路','在沉潮回廊通过3层，替商队记下安全的路线。','floor','tide',3,1,120,130),('astral_survey','星光的岔路','在星眠幻庭通过3层，观测门后的回声。','floor','astral',3,1,120,130),('expedition_record','远行的第一页','不拘主题，累计通过5层地下城。','floor','*',5,1,165,180),('creeper_watch','别让它靠近仓库','在野外讨伐3只苦力怕。别在小镇试爆。','kill','minecraft:creeper',3,1,105,100)]
for id,title,story,kind,target,count,level,coins,xp in offers:write(D/f'commissions/{id}.json',dict(title=title,story=story,kind=kind,target=target,count=count,level=level,coins=coins,xp=xp,cooldown_seconds=1800))

# New chapter only. Stable IDs prevent the user's existing chapters being regenerated.
def uid(s):return f'{int(hashlib.sha256(("tnc-alchemy-20261020:"+s).encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}'
quests=[]
def quest(key,title,item,x,y,description,parents=(),check=False):
 q=dict(id=uid(key),title=title,icon=item,x=x,y=y,description=description,dependencies=[uid(k) for k in parents],hide_dependency_lines=False,tasks=[dict(id=uid(key+'-task'),type='checkmark') if check else dict(id=uid(key+'-task'),type='item',item=item,count=1,consume_items=False)],rewards=[]);quests.append(q)
quest('intro','&b&l魔焰药典','tnc:spellflame_cauldron',0,-5,['魔药学使用植物、药焰与玻璃瓶，拥有独立炼制流程；邻接魔力科技只作可选辅助。','不必选择职业，不消耗学习点。农业、交易和远征都能为药学供给材料。'],check=True)
quest('fuel','&6焰蕊盐 · 植物的火种','tnc:ember_blossom_salt',-2,-3,['暖椒＋木炭＋荧石粉，合成4份焰蕊盐。','一份盐提供1200刻实际炼制燃时；停工不白烧。无须接魔力电。'],['intro'])
quest('cauldron','&3第一口药釜','tnc:spellflame_cauldron',0,-1,['铜锭×2、铁锭×3、炼药锅×1，按JEI合成魔焰炼药釜。','左侧四格可放三种不同材料（顺序无关）；右侧空瓶、成品；下方焰蕊盐。','燧石右键点燃；学习任意火系法术后，可在GUI花5法力点出魔焰，也能用火法术命中釜体。','普通火耗时为魔焰的1.5倍。点火不烧屋，不破坏地形。'],['fuel'])
labels=json.loads((A/'lang/zh_cn.json').read_text('utf8'))
def ingredient_name(id):
 ns,key=id.split(':');return labels.get('item.'+ns+'.'+key,labels.get('block.'+ns+'.'+key,{'glowstone_dust':'荧石粉','kelp':'海带','iron_nugget':'铁粒','nether_wart':'下界疣','pufferfish':'河豚','glow_berries':'发光浆果','brown_mushroom':'棕色蘑菇','echo_shard':'回响碎片','bread':'面包','honey_bottle':'蜂蜜瓶','sugar':'糖','golden_carrot':'金胡萝卜','fermented_spider_eye':'发酵蛛眼','magma_cream':'岩浆膏','blaze_powder':'烈焰粉','prismarine_crystals':'海晶砂粒','scute':'鳞甲','phantom_membrane':'幻翼膜','rabbit_foot':'兔子脚','milk_bucket':'奶桶','redstone':'红石粉','lapis_lazuli':'青金石','amethyst_shard':'紫水晶碎片','ghast_tear':'恶魂之泪','golden_apple':'金苹果','wheat':'小麦','carrot':'胡萝卜','beetroot':'甜菜根','melon_slice':'西瓜片','spider_eye':'蜘蛛眼','feather':'羽毛','sweet_berries':'甜浆果','glistering_melon_slice':'闪烁的西瓜片'}.get(key,id)))
branches=[['bloom','spring','awaken','flow'],['edge','stride','shell','focus'],['cinder','tide','feather','cleanse'],['night','echo','hearth','revive']]
for branch,ids in enumerate(branches):
 for row,id in enumerate(ids):
  e=next(e for e in elixirs if e['id']==id);desc=[e['help']+'。','投入：'+' ＋ '.join(ingredient_name(id) for id in e['ingredients'])+'；再放空玻璃瓶与焰蕊盐。',f'魔焰炼制 {e["ticks"]/20:g} 秒；普通火为1.5倍。','所有魔药共用8秒冷却；重复饮用不永久叠加属性。']
  if id=='echo':desc+=['F8打开魔力视，寻找遗庭宝藏房侧面的隐匣；显影剂将可见距离从24格提高到52格。','隐匣按玩家记录、每匣只领一次，满包会保留。']
  quest(id,'&b'+e['name'],'tnc:elixir_'+id,-6+branch*4,2+row*3,desc,['cauldron'] if row==0 else [ids[row-1]])
quest('heat','&6让魔焰更稳定','tnc:mana_basin',-3,16,['炼药釜紧邻自己有权限使用的魔力装置，每秒可取1真实魔力，额外推进10刻炼制。','这是选配恒温：晶纤、储池、植物或动物供能沿用原魔力工坊，断能时照常用魔焰炼制。'],['flow','focus'])
quest('prepare','&3出发之前','tnc:elixir_spring',3,16,['在地下城大厅选段，进入远征备战；队员也可点整理行囊或输入 /prepare。','27格私人暂存永久保存。收纳按钮只搬主背包，快捷栏和穿戴保留。','右侧4格魔药按从左到右消耗；默认H快捷饮用，可在控制设置改键。','全员确认后，队长再次选择同一段启程；药品和背包物品都是真实的，不复制。'],['echo','hearth'],check=True)
quest('home','&6有人等你归来','minecraft:bread',3,19,['酒馆委托栏点击接单，自动记录真实讨伐和下层进度，回栏交付。','与伴侣在住处重聚，会回应委托、平安归来与孩子的问候。','潜行右键伴侣或输入 /family 打开家庭页；两份同种、饱食度至少4的食物可共餐。','共餐每个世界运行日一次，不回家不会惩罚关系；普通右键仍是MCA的送礼、婚姻与育儿。'],['prepare'],check=True)
write(R/'questbook/ftbquests/chapters/tnc_alchemy.snbt',dict(id=uid('chapter'),filename='tnc_alchemy',title='&3&l魔焰药典',icon='tnc:spellflame_cauldron',group='544E434C49464531',order_index=13,default_hide_dependency_lines=False,default_quest_shape='circle',images=[dict(image='tnc:textures/guide/alchemy_banner.png',x=0,y=-10,width=12,height=6.75,rotation=0.0)],quests=quests))
write(R/'work/life-expedition-catalog.json',dict(elixirs=elixirs,commissions=len(offers),quests=len(quests)))
print(f'Built {len(elixirs)} distinct potion icons, original cauldron/cache models, {len(offers)} commissions and {len(quests)} new quests')

# The physical ritual supersedes the original desk UI and generated bottle placeholders.
import runpy
runpy.run_path(str(R / "tools/build_ritual_alchemy_assets.py"), run_name="__main__")
