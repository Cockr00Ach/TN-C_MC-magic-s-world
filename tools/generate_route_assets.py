"""Native cuboid models and UV references; AI master atlases remain untouched."""
from pathlib import Path
import json,re,shutil
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc';J=R/'src/main/java/com/tnc/tnc/life/routes'
ART=R/'art/life-mana-20261007';ART.mkdir(parents=True,exist_ok=True)
def put(path,data):
 p=A/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
masters=Path('C:/Users/宋志坤/.codex/generated_images/01a0e9a7-09c2-7b12-b9af-d76132668563')
for name,file in [('route_plants','exec-5dc706aa-d9c2-4370-9660-3cd01a19d3fb.png'),('route_goods','exec-c0fcf688-b067-4b20-b813-2920b333ef43.png')]:
 shutil.copy2(masters/file,ART/(name+'.png'));shutil.copy2(masters/file,A/'textures/block'/(name+'.png'))
def uv(i):
 x=i%4*4;y=i//4*4;return [x,y,x+4,y+4]
DISPLAY={'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]},'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,4,.5],'scale':[.85,.85,.85]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1,3,1],'scale':[.7,.7,.7]}}
def sprite(name,sheet,cell):
 put(Path('models/item')/(name+'.json'),{'gui_light':'front','render_type':'minecraft:cutout','textures':{'sprite':'tnc:block/'+sheet,'particle':'tnc:block/'+sheet},'elements':[{'from':[0,0,7.5],'to':[16,16,8.5],'faces':{d:{'texture':'#sprite','uv':uv(cell)} for d in ['north','south']}}],'display':DISPLAY})
plants=re.findall(r'\w+\("([a-z_]+)","([^"]+)","([a-z_]+)","([^"]+)"',(J/'NewPlantKind.java').read_text(encoding='utf-8'))
for i,(id,name,product,pname) in enumerate(plants):
 sprite(id+'_seed','route_plants',9+i if i<7 else i);sprite(product,'route_goods',i)
 for age in range(4):
  height=[5,8,12,16][age];width=[5,8,12,16][age];elements=[]
  for rotation in [45,-45]:
   elements.append({'from':[(16-width)/2,0,7.99],'to':[(16+width)/2,height,8.01],'rotation':{'origin':[8,0,8],'axis':'y','angle':rotation,'rescale':True},'faces':{d:{'texture':'#plant','uv':uv(i)} for d in ['north','south']}})
  put(Path('models/block')/(id+f'_{age}.json'),{'render_type':'minecraft:cutout','textures':{'plant':'tnc:block/route_plants','particle':'tnc:block/route_plants'},'elements':elements})
 put(Path('blockstates')/(id+'.json'),{'variants':{f'age={age}':{'model':f'tnc:block/{id}_{age}'} for age in range(4)}})
for id,cell in [('spirit_iron',9),('radiant_gold',10),('star_marrow',11),('homeward_ribbon',12),('descent_feather',13),('ore_whisper_earring',14),('dew_ward_charm',15),('prism_horn_shard',6),('sand_otter_fiber',3)]:sprite(id,'route_goods',cell)
# Device models use the previously accepted Minecraft material textures, with distinct silhouettes.
TEXTURES={'wood':'tnc:block/art_wood','metal':'tnc:block/art_copper','iron':'tnc:block/art_iron','cloth':'tnc:block/art_linen','glass':'tnc:block/energy_glass','green':'tnc:block/art_leaf','paper':'tnc:block/art_paper','stone':'tnc:block/energy_stone'}
def box(a,b,mat):return {'from':list(a),'to':list(b),'faces':{d:{'texture':'#'+mat} for d in ['north','south','east','west','up','down']}}
def foot():return [box((2,0,2),(14,2,14),'stone')]
def table():return foot()+[box((3,2,3),(5,10,5),'wood'),box((11,2,3),(13,10,5),'wood'),box((3,2,11),(5,10,13),'wood'),box((11,2,11),(13,10,13),'wood'),box((1,10,1),(15,12,15),'wood')]
def bowl(width=14,h=8):
 lo=(16-width)/2;hi=16-lo;return foot()+[box((lo,2,lo),(hi,3,hi),'iron'),box((lo,3,lo),(hi,h,lo+2),'metal'),box((lo,3,hi-2),(hi,h,hi),'metal'),box((lo,3,lo+2),(lo+2,h,hi-2),'metal'),box((hi-2,3,lo+2),(hi,h,hi-2),'metal'),box((lo+2,3,lo+2),(hi-2,4,hi-2),'glass')]
def lantern(tall=False):return foot()+[box((6,2,6),(10,4,10),'metal'),box((4,4,4),(12,11 if tall else 9,12),'glass'),box((3,11 if tall else 9,3),(13,13 if tall else 11,13),'metal'),box((7,13 if tall else 11,7),(9,15 if tall else 13,9),'iron')]
models={
'mana_infuser':table()+[box((6,12,6),(10,13,10),'glass'),box((2,12,12),(4,16,14),'metal')],
'dew_collector':bowl(14,7)+[box((7,4,7),(9,11,9),'glass')],
'breath_collector':foot()+[box((5,2,5),(11,11,11),'glass'),box((3,11,3),(13,13,13),'metal'),box((2,5,7),(5,8,9),'iron'),box((11,5,7),(14,8,9),'iron')],
'color_target':foot()+[box((7,2,7),(9,8,9),'wood'),box((2,8,7),(14,15,9),'metal'),box((4,9,6),(12,14,7),'glass'),box((7,10,5),(9,12,6),'green')],
'mana_basin':bowl(), 'radiant_basin':bowl(16,13)+[box((5,4,5),(11,10,11),'glass')],
'star_basin':bowl(16,16)+[box((6,4,6),(10,14,10),'glass'),box((1,14,1),(3,16,15),'iron'),box((13,14,1),(15,16,15),'iron')],
'mana_mirror':foot()+[box((7,2,7),(9,8,9),'metal'),box((3,8,7),(13,16,9),'metal'),box((4,9,6),(12,15,7),'glass')],
'star_mirror':foot()+[box((6,2,6),(10,7,10),'metal'),box((1,7,7),(15,16,9),'iron'),box((3,9,6),(13,15,7),'glass'),box((7,9,5),(9,15,6),'metal')],
'mana_valve':foot()+[box((3,2,3),(13,6,13),'metal'),box((7,6,7),(9,10,9),'iron'),box((4,10,4),(12,12,12),'metal'),box((6,12,6),(10,14,10),'glass')],
'mana_lantern':lantern(), 'bright_mana_lantern':lantern(True),
'mana_pulp_press':table()+[box((2,12,2),(4,16,14),'iron'),box((12,12,2),(14,16,14),'iron'),box((4,14,2),(12,16,14),'metal'),box((4,12,4),(12,13,12),'paper')],
'mana_feed_plate':bowl(16,6)+[box((7,6,1),(9,12,5),'iron'),box((5,10,1),(11,12,5),'wood')],
'gentle_irrigator':foot()+[box((5,2,5),(11,7,11),'glass'),box((7,7,7),(9,13,9),'metal'),box((2,12,7),(14,14,9),'iron')],
'climate_lantern':lantern(True)+[box((0,8,7),(4,10,9),'green'),box((12,8,7),(16,10,9),'green')],
'mana_sawmill':table()+[box((7,12,3),(9,16,13),'iron'),box((2,12,2),(6,14,14),'wood'),box((10,12,2),(14,14,14),'wood')],
'mist_loom':table()+[box((2,12,2),(4,16,14),'wood'),box((12,12,2),(14,16,14),'wood'),box((4,12,3),(12,16,5),'cloth'),box((4,15,10),(12,16,12),'metal')],
'meal_dryer':foot()+[box((2,2,2),(14,12,14),'wood'),box((3,4,1),(13,10,2),'glass'),box((4,12,4),(12,14,12),'iron'),box((7,14,7),(9,16,9),'metal')],
'mana_incubator':bowl(16,9)+[box((4,4,4),(12,5,12),'cloth'),box((2,9,2),(14,11,14),'glass')],
'harvest_arm':foot()+[box((2,2,6),(6,12,10),'metal'),box((6,10,7),(14,12,9),'iron'),box((12,7,5),(14,9,11),'wood'),box((13,4,5),(15,7,7),'iron'),box((13,4,9),(15,7,11),'iron')],
'seed_sorter':table()+[box((3,12,3),(13,14,13),'wood'),box((7,14,3),(9,16,13),'iron'),box((3,14,7),(13,16,9),'iron')],
'mana_cart_track':foot()+[box((2,2,2),(4,3,14),'iron'),box((12,2,2),(14,3,14),'iron'),box((5,2,5),(11,6,11),'glass'),box((6,6,6),(10,8,10),'metal')],
'ward_bell':foot()+[box((3,2,7),(5,15,9),'wood'),box((11,2,7),(13,15,9),'wood'),box((3,14,7),(13,16,9),'wood'),box((7,11,7),(9,14,9),'iron'),box((5,7,5),(11,11,11),'metal'),box((4,6,4),(12,7,12),'metal')],
'accessory_charger':foot()+[box((4,2,4),(12,7,12),'glass'),box((3,7,3),(13,9,13),'metal'),box((6,9,6),(10,11,10),'cloth'),box((1,3,1),(3,12,3),'iron'),box((13,3,13),(15,12,15),'iron')]
}
for id,h in [('mana_thread',2),('radiant_thread',3),('star_thread',4)]:models[id]=[box((0,0,6),(16,h,10),'metal'),box((6,0,0),(10,h,16),'metal'),box((6,h,6),(10,h+1,10),'glass')]
nodes=re.findall(r'\w+\("([a-z_]+)","([^"]+)",\d+,\d+\)',(J/'RouteKind.java').read_text(encoding='utf-8'))
for id,name in nodes:
 assert id in models,id
 put(Path('models/block')/(id+'.json'),{'parent':'minecraft:block/block','textures':{**TEXTURES,'particle':TEXTURES['metal']},'elements':models[id]})
 put(Path('models/item')/(id+'.json'),{'parent':'tnc:block/'+id});put(Path('blockstates')/(id+'.json'),{'variants':{'':{'model':'tnc:block/'+id}}})
 put(Path('../../data/tnc/loot_tables/blocks')/(id+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:'+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
# Handheld engineer tools have their own shape, rather than reusing a round token.
tools={'mana_building_ruler':[box((4,1,7),(7,15,9),'wood'),box((7,12,7),(13,15,9),'iron'),box((5,3,6),(6,10,7),'glass')],
'rune_crossbow':[box((7,1,7),(9,14,9),'wood'),box((1,11,7),(15,13,9),'iron'),box((4,4,7),(12,6,9),'metal'),box((7,7,6),(9,9,7),'glass')],
'mech_spellgun':[box((5,2,6),(8,7,10),'wood'),box((4,7,5),(12,12,11),'metal'),box((7,11,3),(9,14,10),'glass'),box((7,9,0),(9,11,5),'iron')],
'mana_mech':[box((3,4,4),(13,14,12),'iron'),box((5,7,3),(11,12,4),'glass'),box((1,11,4),(3,15,12),'metal'),box((13,11,4),(15,15,12),'metal')],
'flowing_mana_bucket':bowl(12,13)+[box((3,13,7),(5,16,9),'iron'),box((11,13,7),(13,16,9),'iron'),box((5,15,7),(11,16,9),'iron')],
'mana_meter':[box((7,1,7),(9,9,9),'wood'),box((3,9,6),(13,15,10),'metal'),box((4,10,5),(12,14,6),'glass')]}
for id,elements in tools.items():put(Path('models/item')/(id+'.json'),{'parent':'minecraft:block/block','textures':{**TEXTURES,'particle':TEXTURES['metal']},'elements':elements,'display':{'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-90,15],'translation':[1,2,0],'scale':[.7,.7,.7]}}})
for id,texture in [('packed_meal','starfelt_travel_roll'),('season_handbook','planting_book'),('magic_garden_book','botanical_book'),('beast_ranch_book','pasture_book'),('mana_workshop_book','magic_forge_manual'),('radiant_cage','pasture_cage'),('star_cage','pasture_cage')]:
 parent=A/'models/item'/(texture+'.json')
 put(Path('models/item')/(id+'.json'),{'parent':'tnc:item/'+texture} if parent.exists() else {'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'}})
langfile=A/'lang/zh_cn.json';lang=json.loads(langfile.read_text(encoding='utf-8-sig'))
for id,name in nodes:lang['block.tnc.'+id]=name;lang['item.tnc.'+id]=name
for id,name,product,pname in plants:lang['block.tnc.'+id]=name;lang['item.tnc.'+id+'_seed']=name+'种源';lang['item.tnc.'+product]=pname
for id,name in re.findall(r'\w+\("([a-z_]+)","([^"]+)",\d+\)',(J/'RouteAccessory.java').read_text(encoding='utf-8')):lang['item.tnc.'+id]=name
for id,name in {'magic_soil':'魔法土','magic_soil_source':'魔法土源','flowing_mana':'流动魔力'}.items():lang['block.tnc.'+id]=name
for id,name in {'spirit_iron':'灵纹铁','radiant_gold':'曜纹金','star_marrow':'星髓晶','flowing_mana_bucket':'流动魔力桶','mana_meter':'检流尺','packed_meal':'远行餐包','season_handbook':'四时食农手册','magic_garden_book':'魔植花园图鉴','beast_ranch_book':'异兽牧场图鉴','mana_workshop_book':'魔导工坊手册','radiant_cage':'曜纹魔法笼 · II','star_cage':'星髓魔法笼 · III','prism_horn_shard':'曳彩角片','sand_otter_fiber':'砂獭绒纤'}.items():lang['item.tnc.'+id]=name
for id,name in [('prismatic_antelope','曳彩角羚'),('drumbelly_otter','鼓腹砂獭')]:
 lang['entity.tnc.'+id]=name;lang['item.tnc.'+id+'_spawn_egg']=name+'刷怪蛋';put(Path('models/item')/(id+'_spawn_egg.json'),{'parent':'minecraft:item/template_spawn_egg'})
put(Path('lang/zh_cn.json'),lang)
print(f'UV atlas references, {len(models)} distinct device models, 9 four-stage plant models, tools and localizations generated.')
