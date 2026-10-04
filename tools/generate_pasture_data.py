"""Data only: biome spawns, production-tool recipes and real animal food consumers."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'src/main/resources/data/tnc'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def ingredient(id):
    return {'tag':id[1:]} if id.startswith('#') else {'item':id if ':' in id else 'tnc:'+id}
def recipe(name,result,inputs,count=1):
    write(DATA/'recipes'/('pasture_'+name+'.json'),{'type':'minecraft:crafting_shapeless','ingredients':[ingredient(i) for i in inputs],'result':{'item':'tnc:'+result,'count':count}})
def forge(name,result,inputs,mana=20,count=1):
    write(DATA/'recipes'/('pasture_'+name+'.json'),{'type':'tnc:mana_forging','category':'husbandry','mana':mana,'work_ticks':120,'ingredients':[dict(ingredient(i),count=n) for i,n in inputs],'result':{'item':'tnc:'+result,'count':count}})

BIOMES={
 'stonebarrow_boar':['minecraft:plains','minecraft:meadow'],
 'emberback_hog':['minecraft:savanna','minecraft:sparse_jungle'],
 'tideback_newt':['minecraft:swamp','minecraft:mangrove_swamp'],
 'froststride_fowl':['minecraft:snowy_plains','minecraft:snowy_taiga'],
 'apiary_toad':['minecraft:flower_forest','minecraft:swamp'],
 'starfelt_hare':['minecraft:meadow','minecraft:grove'],
 'dusk_lantern_deer':['minecraft:forest','minecraft:dark_forest'],
 'pattern_shell_snail':['minecraft:swamp','minecraft:old_growth_birch_forest'],
 'post_heron':['minecraft:swamp','minecraft:river'],
 'watch_mantis':['minecraft:flower_forest','minecraft:birch_forest'],
 'mirrorwing_moth':['minecraft:flower_forest','minecraft:dark_forest'],
 'forgegill_tapir':['minecraft:savanna','minecraft:windswept_savanna'],
 'mistbelly_otter':['minecraft:river','minecraft:swamp'],
 'ringstone_tortoise':['minecraft:meadow','minecraft:windswept_hills'],
 'papersail_ray':['minecraft:meadow','minecraft:windswept_savanna'],
 'wirecall_lizard':['minecraft:windswept_hills','minecraft:stony_peaks'],
 'dewbound_whale':['minecraft:meadow','minecraft:old_growth_birch_forest'],
 'satchelback_runner':['minecraft:plains','minecraft:savanna'],
 'bowlhorn_rhino':['minecraft:savanna','minecraft:meadow'],
 'pageforage_raccoon':['minecraft:forest','minecraft:birch_forest'],
 'patternbuild_beaver':['minecraft:river','minecraft:swamp'],
 'springhoof_strider':['minecraft:meadow','minecraft:plains'],
 'pillowlight_marten':['minecraft:taiga','minecraft:dark_forest']}
for index,(id,biomes) in enumerate(BIOMES.items()):
    write(DATA/'tags/worldgen/biome'/('pasture_'+id+'.json'),{'replace':False,'values':biomes})
    write(DATA/'forge/biome_modifier'/('pasture_'+id+'.json'),{'type':'forge:add_spawns','biomes':'#tnc:pasture_'+id,'spawners':{'type':'tnc:'+id,'weight':3 if index<6 else 1,'minCount':2 if index<6 else 1,'maxCount':4 if index<6 else 2}})
    write(DATA/'loot_tables/entities'/(id+'.json'),{'type':'minecraft:entity','pools':[]}) # genuine adult-dependent drops in entity

recipe('book','pasture_book',['minecraft:paper']*3+['minecraft:leather','minecraft:string'])
recipe('staff','pasture_staff',['minecraft:stick']*3+['minecraft:paper'])
recipe('staff_horn','pasture_staff',['minecraft:stick']*2+['horn_powder'])
recipe('trough','pasture_trough',['#minecraft:planks']*5+['minecraft:stick']*2)
recipe('marker','habitat_marker',['minecraft:stick']*3+['minecraft:paper'])
recipe('tray','pasture_tray',['#minecraft:planks']*3+['minecraft:copper_ingot'])
recipe('eggs','egg_rack',['#minecraft:planks']*4+['minecraft:hay_block','minecraft:string','minecraft:string'])
recipe('eggs_glue','egg_rack',['#minecraft:planks']*4+['minecraft:hay_block','nest_glue'])
recipe('dew','dew_rack',['#minecraft:planks']*3+['minecraft:glass_bottle']*2)
recipe('charging','charging_perch',['#minecraft:planks']*3+['minecraft:copper_ingot']*2+['minecraft:redstone'])
recipe('cage','pasture_cage',['minecraft:iron_bars']*4+['minecraft:lead','minecraft:stick'])
recipe('scraper','pasture_scraper',['#minecraft:planks','minecraft:stick'])
recipe('jar','empty_breath_jar',['minecraft:glass','minecraft:copper_ingot'])
recipe('bottle','mana_bottle',['minecraft:glass']*3+['minecraft:copper_ingot']*2+['minecraft:amethyst_shard'])
forge('refined_bottle','refined_mana_bottle',[('mana_bottle',1),('mana_copper_coil',1),('mirror_scale',1)],30)
forge('bottle_base','mana_bottle_base',[('minecraft:copper_ingot',2),('minecraft:stone',2),('minecraft:redstone',1)])
recipe('boar_meal','stonebarrow_hotpot',['stonebarrow_meat']*2+['minecraft:potato','hearth_pepper_fruit','minecraft:bowl'])
recipe('hog_meal','emberback_stew',['emberback_meat']*2+['night_gourd']*2+['warm_fat','minecraft:bowl'])
recipe('newt_meal','tideback_chowder',['tideback_meat']*2+['rainletter_berry','minecraft:bowl'])
recipe('fowl_meal','frostwarm_skewer',['froststride_meat']*2+['hearth_pepper_fruit'])
recipe('toad_meal','honeydew_casserole',['apiary_meat']*2+['honeydew','minecraft:brown_mushroom','minecraft:bowl'])
recipe('hare_meal','starfelt_travel_roll',['starfelt_meat']*2+['star_dew_fruit','road_bell_ear'])
# Ordinary ingredients are a viable wilderness start; magic ingredients are alternate flavours.
recipe('boar_meal_basic','stonebarrow_hotpot',['stonebarrow_meat']*2+['minecraft:potato','minecraft:beetroot','minecraft:bowl'])
recipe('hog_meal_basic','emberback_stew',['emberback_meat']*2+['minecraft:pumpkin','warm_fat','minecraft:bowl'])
recipe('newt_meal_basic','tideback_chowder',['tideback_meat']*2+['minecraft:kelp','minecraft:brown_mushroom','minecraft:bowl'])
recipe('fowl_meal_basic','frostwarm_skewer',['froststride_meat']*2+['minecraft:carrot','minecraft:baked_potato'])
recipe('hare_meal_basic','starfelt_travel_roll',['starfelt_meat']*2+['minecraft:sweet_berries','minecraft:bread'])
recipe('quiet_felt','quiet_felt',['soft_down']*2+['minecraft:string','minecraft:leather'])
recipe('calming_bell','calming_bell',['minecraft:copper_ingot','resonant_fleece','minecraft:string'])
recipe('soft_lantern','soft_lantern',['minecraft:glass_bottle','minecraft:copper_ingot','minecraft:stick','flight_feather'])
recipe('waterproof','waterproof_seed_wrap',['water_membrane']*2+['minecraft:string']*2)
recipe('wind','wind_bottle',['air_plume','minecraft:glass_bottle'])
recipe('warm_feed','warm_feed',['warm_fat','minecraft:wheat','minecraft:hay_block'],2)
recipe('warm_feed_breath','warm_feed',['warm_breath','minecraft:wheat','minecraft:hay_block'],2)
recipe('cord','climbing_cord',['hoof_glue','minecraft:string']*2)
recipe('saddle','beast_saddle',['runner_hide','minecraft:leather']*2+['minecraft:iron_ingot'])
recipe('warning_lens','warning_lens',['watch_wing','minecraft:glass','minecraft:copper_ingot'])
forge('focus_lens','focus_lens',[('mirror_scale',2),('minecraft:glass',1),('minecraft:amethyst_shard',1)])
forge('snail_clay','insulating_forge_clay',[('shell_glue',1),('minecraft:clay_ball',2),('minecraft:sand',1)],20,2)
forge('frost_clay','insulating_forge_clay',[('frost_bone',1),('minecraft:clay_ball',2),('minecraft:sand',1)],20,2)
write(DATA/'recipes/pasture_stone_bone_meal.json',{'type':'minecraft:crafting_shapeless','ingredients':[ingredient('stone_bone')],'result':{'item':'minecraft:bone_meal','count':3}})
for id in ['pasture_trough','habitat_marker','pasture_tray','egg_rack','dew_rack','charging_perch','mana_bottle_base']:
    write(DATA/'loot_tables/blocks'/(id+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:'+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

names={'pasture_book':'牧养册','pasture_staff':'牧务杖','pasture_cage':'搬迁兽笼','fertile_pasture_egg':'异兽受精卵','mana_bottle':'铜环魔力瓶','refined_mana_bottle':'精练魔力瓶','pasture_trough':'牧场食槽','habitat_marker':'栖居标记','pasture_tray':'牧场托盘','egg_rack':'孵卵架','dew_rack':'采露架','charging_perch':'充电栖架','mana_bottle_base':'脉冲魔瓶座','pasture_glow':'枕光微光','stonebarrow_meat':'垒脊肉','stone_bone':'石骨片','emberback_meat':'暮炭肉','warm_fat':'温脂','tideback_meat':'潮筋肉','water_membrane':'水韧膜','froststride_meat':'银霜禽肉','frost_bone':'霜骨','froststride_egg':'银霜食用蛋','apiary_meat':'蜜香蛙肉','sweet_fat':'甜脂','honeydew':'蜜露','starfelt_meat':'星茸肉','soft_down':'柔茸','lantern_antler':'灯鹿落角','shell_glue':'纹壳胶','flight_feather':'航纹羽','watch_wing':'灯翼壳','mirror_scale':'镜鳞','warm_breath':'暖息罐','spring_concentrate':'润泉液','loam_pebble':'结壤粒','air_plume':'空羽囊','storm_crystal':'雷余晶','runner_hide':'硬囊皮','horn_powder':'碗角粉','forage_paper_hide':'觅页纸皮','nest_glue':'巢黏','hoof_glue':'落蹄胶','lamp_wax':'盏灯蜡','stonebarrow_hotpot':'垒脊焖锅','emberback_stew':'暮炭慢炖','tideback_chowder':'渡水烩','frostwarm_skewer':'霜暖串烧','honeydew_casserole':'蜜露炖盅','starfelt_travel_roll':'星茸旅卷','soft_lantern':'行旅柔光灯','waterproof_seed_wrap':'防水种包套','quiet_felt':'静行绒毡','calming_bell':'安畜铃','warning_lens':'警灯镜片','focus_lens':'聚光镜片','wind_bottle':'顺风瓶','warm_feed':'保温饲料','climbing_cord':'登山绳','beast_saddle':'异兽鞍','empty_breath_jar':'空暖息罐','pasture_scraper':'木刮片'}
zh={}
for id,name in names.items():
    zh[('block' if id in ['pasture_trough','habitat_marker','pasture_tray','egg_rack','dew_rack','charging_perch','mana_bottle_base','pasture_glow'] else 'item')+'.tnc.'+id]=name
species_names=['石垒豪豕','暮炭野豕','潮背鳄螈','银霜步禽','蜂囊腴蛙','星茸耳兔','暮灯鹿','纹壳蜗','邮羽鹭','巡灯螳螂','镜瓣夜蛾','炉鳃山貘','雾腹水獭','环砾龟','纸帆魟','鸣线蜥','凝露浮鲸','囊背负兽','碗角犀','觅页浣兽','锦纹筑狸','跃泉蹄兽','枕光貂']
for (id,_),name in zip(BIOMES.items(),species_names):zh['entity.tnc.'+id]=name;zh['item.tnc.'+id+'_spawn_egg']=name+'刷怪蛋'
write(ROOT/'work/pasture-lang-zh.json',zh)
write(ROOT/'work/pasture-lang-en.json',{key:key.split('.')[-1].replace('_',' ').title() for key in zh})
print('Pasture data generated:23 spawn types, recipes/loot, language fragments')


