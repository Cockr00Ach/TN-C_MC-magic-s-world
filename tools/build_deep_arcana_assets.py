"""Deep arcana assets: original atlas extraction plus native block geometry; preserves old quests."""
import json,shutil,re,math
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/tnc';D=R/'src/main/resources/data/tnc'
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n','utf8')
ids='sealing_lid strange_waste overdraft_elixir gigantism_elixir miniature_elixir flame_twist_elixir fission_elixir capture_shrink_elixir soothing_bath_salt mana_ash collapsed_mana_singularity abyss_tower_brick symbiosis_marker giant_mana_fruit slime_pudding moon_honey_cake forbidden_scroll void_parasitic_seed aurora_wings astral_wand_core shadow_mana_crystal thorn_tyrant_vine emerald_lifemoss predator_bud tailwind_boots'.split()
atlas=R/'art/arcana/deep-arcana-atlas.png';atlas.parent.mkdir(parents=True,exist_ok=True)
if not atlas.exists():shutil.copy2(Path('C:/Users/宋志坤/.codex/generated_images/01a0e9a7-09c2-7b12-b9af-d76132668563/exec-9668c5dc-ffbf-499f-b1f4-7e110e649ddd.png'),atlas)
im=Image.open(atlas).convert('RGBA')
for i,id in enumerate(ids):
 x,y=i%5,i//5;cell=im.crop((x*im.width//5,y*im.height//5,(x+1)*im.width//5,(y+1)*im.height//5));box=cell.getbbox()
 if box:cell=cell.crop(box)
 cell.thumbnail((60,60),Image.Resampling.NEAREST);icon=Image.new('RGBA',(64,64));icon.alpha_composite(cell,((64-cell.width)//2,(64-cell.height)//2));icon.save(A/f'textures/item/{id}.png')
 write(A/f'models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/{id}'}})
names=['封魔盖','古怪废液','狂躁透支药水','巨人化药水','微缩药水','属性扭曲药水','分裂药水','缩水捕捉魔药','舒缓浴盐','魔力灰烬','坍缩魔力奇点','深渊聚能砖','星路标记杖','巨型魔力果实','史莱姆Q弹布丁','月光蜂蜜糕','禁忌古卷','虚空寄生种','极光羽翼','星界杖心','暗影魔力结晶','荆棘暴君藤','翡翠生命苔','捕食者花苞','顺风魔导靴']
lang=dict(zip(['item.tnc.'+x for x in ids],names));lang['block.tnc.abyss_tower_brick']='深渊聚能砖';lang['block.tnc.enchanted_red_grass']='魔化红草';lang['entity.tnc.void_devourer']='魔力吞噬者·虚空巨兽';lang['entity.tnc.dimensional_spirit']='高维魔灵';lang['block.tnc.steam_lotus']='蒸气魔莲';lang['item.tnc.steam_lotus_seed']='蒸气魔莲种';lang['item.tnc.steam_lotus_heart']='蒸气莲心'
tex={'stone':'tnc:block/alchemy_ceramic','gold':'tnc:block/alchemy_bronze','glow':'tnc:block/alchemy_glow','wood':'tnc:block/alchemy_wood','glass':'tnc:block/energy_glass','particle':'tnc:block/alchemy_ceramic'}
def cube(a,b,t='stone',axis=None,angle=0,origin=(8,8,8)):
 v={'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ['north','south','east','west','up','down']}}
 if axis:v['rotation']={'origin':list(origin),'axis':axis,'angle':angle}
 return v
def ring(y=6,r=6,vertical=False):
 parts=[]
 for rot in (0,45):
  for x,z,dx,dz in [(8-r,5,1,6),(7+r,5,1,6),(5,8-r,6,1),(5,7+r,6,1)]:
   if vertical:parts.append(cube([x,z,7],[x+dx,z+dz,9],'gold','z',rot))
   else:parts.append(cube([x,y,z],[x+dx,y+1,z+dz],'gold','y',rot))
 return parts
base=[cube([2,0,2],[14,2,14]),cube([3,2,3],[13,3,13],'gold')]
def pedestal():return base+[cube([6,3,6],[10,8,10]),cube([5,8,5],[11,9,11],'gold')]
def crystal(x=8,y=9,z=8,size=2):return [cube([x-size,y,z-size],[x+size,y+size*2,z+size],'glow','y',45)]
models={}
models['abyss_mana_tower']=base+[cube([4,2,4],[12,5,12],'gold'),cube([6,5,6],[10,20,10]),cube([3,20,3],[13,22,13],'gold')]+crystal(y=9,size=3)+ring(7)+ring(17)
models['mana_relief_valve']=pedestal()+[cube([3,9,5],[13,12,11],'gold'),cube([6,10,0],[10,13,7],'gold')]+ring(14,4)
models['geothermal_mana_core']=base+ring(3)+[cube([4,3,4],[12,5,12],'glass')]+crystal(y=5,size=2)
models['arcane_sprinkler']=base+[cube([7,2,7],[9,18,9],'gold')]+ring(17,5)+[cube([2,17,6],[14,19,10],'gold')]+crystal(y=18,size=1)
models['corona_focusing_mirror']=pedestal()+ring(10,7)+[cube([3,10,3],[13,11,13],'glass')]+crystal(y=12,size=1)
models['moon_tide_basin']=base+ring(4,7)+[cube([2,3,2],[14,4,14],'glass')]+crystal(y=4,size=1)
models['arcane_observatory']=pedestal()+[cube([5,8,2],[11,14,14],'gold','x',22.5),cube([6,9,1],[10,13,2],'glass','x',22.5)]
models['spirit_nurture_altar']=base+[cube([5,3,5],[11,6,11]),cube([3,6,3],[13,7,13],'gold')]+[cube([x,3,z],[x+1,12,z+1],'gold') for x,z in [(2,2),(13,2),(2,13),(13,13)]]+crystal(y=6,size=1)
models['carved_mana_platter']=[cube([1,0,1],[15,1,15],'gold'),cube([2,1,2],[14,1.5,14],'stone')]+[cube([v,1.5,2],[v+.2,1.7,14],'gold') for v in (6,10)]+[cube([2,1.5,v],[14,1.7,v+.2],'gold') for v in (6,10)]
models['ecology_restraint_cage']=base+[cube([x,2,z],[x+1,23,z+1],'gold') for x,z in [(0,0),(15,0),(0,15),(15,15)]]+ring(22,7)+crystal(y=21,size=1)
models['mana_pressure_tread']=[cube([0,0,0],[16,1,16]),cube([1,1,1],[15,2,15],'gold'),cube([3,2,3],[13,2.5,13],'glow')]
models['mana_spray_provoker']=base+[cube([5,2,5],[11,8,11],'glass'),cube([7,8,7],[9,14,9],'gold'),cube([7,12,0],[9,14,9],'gold')]
models['living_mana_hood']=base+[cube([7,3,12],[9,18,14],'gold'),cube([3,15,3],[13,17,13],'gold'),cube([5,14,5],[11,15,11],'glass')]
for name in ['sky_mana_launch_ring','sky_mana_relay_ring']:models[name]=[cube([2,0,6],[14,2,10])]+ring(vertical=True,r=7)+[cube([0,7,6],[2,9,10],'glow'),cube([14,7,6],[16,9,10],'glow')]
models['singularity_mana_core']=base+ring(7)+ring(13)+crystal(y=5,size=3)+[cube([7,6,7],[9,15,9],'stone')]
for id,parts in models.items():
 write(A/f'models/block/{id}.json',{'textures':tex,'elements':parts,'display':{'gui':{'rotation':[30,225,0],'scale':[.58,.58,.58]},'ground':{'scale':[.3,.3,.3]}}})
 write(A/f'blockstates/{id}.json',{'variants':{'':{'model':f'tnc:block/{id}'}}});write(A/f'models/item/{id}.json',{'parent':f'tnc:block/{id}'})
for part in ['', '_base','_head']:
 obj=json.loads((A/f'models/block/star_mirror{part}.json').read_text('utf8'));write(A/f'models/block/dimensional_mana_prism{part}.json',obj)
write(A/'blockstates/dimensional_mana_prism.json',{'variants':{'':{'model':'tnc:block/dimensional_mana_prism'}}});write(A/'models/item/dimensional_mana_prism.json',{'parent':'tnc:block/dimensional_mana_prism'})
for id,texture in [('abyss_tower_brick','minecraft:block/deepslate_bricks'),('enchanted_red_grass','minecraft:block/crimson_nylium')]:
 write(A/f'blockstates/{id}.json',{'variants':{'':{'model':f'tnc:block/{id}'}}});write(A/f'models/block/{id}.json',{'parent':'minecraft:block/cube_all','textures':{'all':texture}})
 if id=='enchanted_red_grass':
  thorns=[cube([0,0,0],[16,16,16],'stone')]
  for x,z in [(3,3),(11,4),(6,11),(12,12)]:thorns.append(cube([x,15,z],[x+1,20,z+1],'gold','z',22.5,(x,15,z)))
  write(A/f'models/block/{id}.json',{'textures':{'stone':'minecraft:block/crimson_nylium','gold':'tnc:block/art_rose','particle':'minecraft:block/crimson_nylium'},'elements':thorns})
  write(A/f'models/item/{id}.json',{'parent':f'tnc:block/{id}'})
 else:
  parts=[cube([0,0,0],[16,16,16]),cube([0,1,0],[16,2,16],'gold'),cube([0,14,0],[16,15,16],'gold')]
  write(A/f'models/block/{id}.json',{'textures':tex,'elements':parts})
for id in list(models)+['dimensional_mana_prism','abyss_tower_brick','enchanted_red_grass']:
 functions=[{'function':'minecraft:copy_nbt','source':'block_entity','ops':[{'source':'ForgeData','target':'BlockEntityTag.ForgeData','op':'replace'},{'source':'Mana','target':'BlockEntityTag.Mana','op':'replace'},{'source':'Owner','target':'BlockEntityTag.Owner','op':'replace'}]}] if id in models or id=='dimensional_mana_prism' else []
 if id=='ecology_restraint_cage':
  functions[0]['ops'][0]={'source':'ForgeData.ElementMana','target':'BlockEntityTag.ForgeData.ElementMana','op':'replace'}
  functions[0]['ops'].append({'source':'ForgeData.SpecimenToken','target':'BlockEntityTag.ForgeData.SpecimenToken','op':'replace'})
 write(D/f'loot_tables/blocks/{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:'+id,'functions':functions}]}]})
# Preserve all potion state when a vessel is moved.
p=D/'loot_tables/blocks/spellflame_cauldron.json';obj=json.loads(p.read_text('utf8'));ops=obj['pools'][0]['entries'][0]['functions'][0]['ops']
for k in ['RitualVersion','HasIgnited','temperature','vortex_sync','Overheated','PerfectRemaining','Ruined','ReadyQuality','HeatedAt']:
 if not any(o['source']==k for o in ops):ops.append({'source':k,'target':'BlockEntityTag.'+k,'op':'replace'})
write(p,obj)
# Unique lotus crown made from layered slender tepals and a suspended glowing heart.
for age in range(4):
 scale=(age+1)/4;parts=[cube([7,0,7],[9,6*scale,9],'gold')]
 for rot in (0,45):
  for a,b in [([2,2,6],[7,3,10]),([9,2,6],[14,3,10]),([6,2,2],[10,3,7]),([6,2,9],[10,3,14])]:parts.append(cube(a,b,'glass','y',rot))
 if age>1:
  for rot in (0,45):parts += [cube([4,4,6],[7,7,10],'gold','y',rot),cube([9,4,6],[12,7,10],'gold','y',rot)]
 parts+=crystal(y=4*scale,size=1.5*scale)
 write(A/f'models/block/steam_lotus_{age}.json',{'textures':tex,'elements':parts})
write(A/'blockstates/steam_lotus.json',{'variants':{f'age={age}':{'model':f'tnc:block/steam_lotus_{age}'} for age in range(4)}})
for id,parent in [('steam_lotus_seed','tnc:item/void_parasitic_seed'),('steam_lotus_heart','tnc:item/astral_wand_core')]:write(A/f'models/item/{id}.json',{'parent':parent})
# Obtainable engineering recipes use real forge input costs.
def forge(id,inputs,count=1,mana=160,ticks=100):
 write(D/f'recipes/arcana/{id}.json',{'type':'tnc:mana_forging','ingredients':[{'item':i,'count':n} for i,n in inputs], 'result':{'item':'tnc:'+id,'count':count},'mana':mana,'work_ticks':ticks,'category':'workshop'})
recipes={
 'abyss_tower_brick':[('minecraft:deepslate_bricks',8),('tnc:star_marrow',1)],
 'abyss_mana_tower':[('tnc:star_basin',1),('tnc:star_marrow',8),('minecraft:nether_star',1)],
 'mana_relief_valve':[('tnc:mana_valve',1),('tnc:radiant_gold',4)],
 'geothermal_mana_core':[('tnc:spirit_iron',4),('minecraft:magma_block',1),('minecraft:amethyst_shard',2)],
 'arcane_sprinkler':[('tnc:gentle_irrigator',1),('tnc:radiant_gold',2),('minecraft:glass_bottle',3)],
 'corona_focusing_mirror':[('tnc:mana_mirror',1),('tnc:radiant_gold',4),('minecraft:daylight_detector',1)],
 'moon_tide_basin':[('tnc:mana_basin',1),('tnc:star_marrow',2),('minecraft:prismarine_crystals',4)],
 'arcane_observatory':[('minecraft:spyglass',1),('tnc:spirit_iron',2),('minecraft:amethyst_shard',4)],
 'spirit_nurture_altar':[('tnc:star_marrow',4),('tnc:accessory_charger',1),('minecraft:crying_obsidian',2)],
 'carved_mana_platter':[('tnc:radiant_gold',2),('minecraft:quartz',4)],
 'ecology_restraint_cage':[('tnc:spirit_iron',6),('tnc:radiant_gold',2),('minecraft:amethyst_shard',1)],
 'mana_pressure_tread':[('tnc:spirit_iron',2),('minecraft:slime_block',1)],
 'mana_spray_provoker':[('tnc:spirit_iron',2),('minecraft:bucket',1),('minecraft:dispenser',1)],
 'living_mana_hood':[('tnc:radiant_gold',3),('minecraft:hopper',1)],
 'sky_mana_launch_ring':[('tnc:star_marrow',4),('tnc:star_mirror',1),('minecraft:phantom_membrane',4)],
 'sky_mana_relay_ring':[('tnc:radiant_gold',4),('minecraft:amethyst_shard',4)],
 'singularity_mana_core':[('tnc:collapsed_mana_singularity',1),('tnc:abyss_mana_tower',1),('tnc:star_marrow',8)],
 'tailwind_boots':[('minecraft:diamond_boots',1),('tnc:star_marrow',2),('minecraft:feather',8)],
 'thorn_tyrant_vine':[('tnc:void_parasitic_seed',1),('minecraft:sweet_berries',8),('tnc:spirit_iron',4)],
 'emerald_lifemoss':[('tnc:void_parasitic_seed',1),('minecraft:moss_block',8),('minecraft:emerald',4)],
 'predator_bud':[('tnc:void_parasitic_seed',1),('minecraft:ender_pearl',4),('minecraft:bone',8)],
 'aurora_wings':[('tnc:astral_wand_core',1),('minecraft:elytra',1),('tnc:star_marrow',6)],
 'astral_wand_core':[('tnc:star_marrow',8),('tnc:shadow_mana_crystal',4),('minecraft:nether_star',1)]}
for id,inputs in recipes.items():forge(id,inputs,8 if id=='abyss_tower_brick' else 1,1000 if id in ['abyss_mana_tower','aurora_wings','astral_wand_core'] else 160)
# Small preparations can be made without an industrial system.
for id,items in {'sealing_lid':['minecraft:copper_ingot','minecraft:copper_ingot','minecraft:amethyst_shard'], 'symbiosis_marker':['minecraft:stick','minecraft:amethyst_shard','tnc:star_marrow'],'slime_pudding':['minecraft:slime_ball','minecraft:sugar','minecraft:bowl'],'moon_honey_cake':['minecraft:bread','minecraft:honey_bottle','tnc:star_dew_fruit']}.items():
 write(D/f'recipes/arcana/{id}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':i} for i in items],'result':{'item':'tnc:'+id}})
for lng in ['zh_cn','en_us']:
 p=A/f'lang/{lng}.json';obj=json.loads(p.read_text('utf8'));obj.update(lang)
 for id,name in re.findall(r'\w+\("([^"]+)","([^"]+)"', (R/'src/main/java/com/tnc/tnc/life/routes/RouteKind.java').read_text('utf8')):obj['block.tnc.'+id]=name
 write(p,obj)
print('Deep arcana: 25 original atlas icons, 18 sculpted devices, world lotus, 27 real recipes')
