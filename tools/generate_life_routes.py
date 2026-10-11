"""Reproducible registry assets and chapter data for approved life routes."""
from pathlib import Path
import json,re,hashlib
R=Path(__file__).resolve().parents[1]; S=R/'src/main/resources'; J=R/'src/main/java/com/tnc/tnc/life/routes'
def write(path,value):
    p=S/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Exact IDs from the approved inventory. Saplings grow all year; only fruit leaves are seasonal.
seasons={}
for line in (R/'docs/传统植物盘点与四季安排-20261006.md').read_text(encoding='utf-8').splitlines():
    if not line.startswith('|') or '`' not in line:continue
    columns=line.split('|');season_column=columns[3] if len(columns)>4 else ''
    mask=15 if '全年' in season_column else sum(1<<i for i,n in enumerate('春夏秋冬') if n in season_column)
    if not mask:continue
    prefix='';ids=[]
    for code in re.findall(r'`([^`]+)`',columns[2]):
        if ':' in code:prefix=code.split(':')[0]
        elif prefix:code=prefix+':'+code
        else:continue
        if ' ' in code or '/' in code:continue
        ids.append(code)
    for code in ids:seasons[code]=15 if code.endswith('_sapling') or code.endswith(':bamboo_sapling') else mask
write(Path('data/tnc/life_routes/seasons.json'),seasons)
def loot(id):write(Path('data/tnc/loot_tables/blocks')/(id+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:'+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
nodes=re.findall(r'\w+\("([a-z_]+)","([^"]+)",\d+,\d+\)',(J/'RouteKind.java').read_text(encoding='utf-8'))
plants=re.findall(r'\w+\("([a-z_]+)","([^"]+)","([a-z_]+)","([^"]+)"',(J/'NewPlantKind.java').read_text(encoding='utf-8'))
for name,source in [('magic_soil',False),('magic_soil_source',True)]:
    top=[0,8,8,16] if source else [0,0,8,8];side=[8,8,16,16] if source else [8,0,16,8]
    faces={d:{'texture':'#soil','uv':top if d=='up' else side,'cullface':d} for d in ['up','down','north','south','east','west']}
    write(Path('assets/tnc/models/block')/(name+'.json'),{'textures':{'soil':'tnc:block/magic_soil_atlas','particle':'tnc:block/magic_soil_atlas'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':faces}]})
    write(Path('assets/tnc/blockstates')/(name+'.json'),{'variants':{'':{'model':'tnc:block/'+name}}});write(Path('assets/tnc/models/item')/(name+'.json'),{'parent':'tnc:block/'+name});loot(name)
write(Path('assets/tnc/blockstates/flowing_mana.json'),{'variants':{'':{'model':'tnc:block/flowing_mana'}}})
write(Path('data/tnc/recipes/magic_soil_source.json'),{'type':'tnc:mana_container_crafting','category':'misc'})
write(Path('data/tnc/recipes/mana_bucket_merge.json'),{'type':'tnc:mana_container_crafting','category':'misc'})
for id,input,cost,sec in [('spirit_iron','iron_ingot',60,6),('radiant_gold','gold_ingot',180,10),('star_marrow','diamond',600,16)]:
    write(Path('data/tnc/recipes')/('mana_forge_'+id+'.json'),{'type':'tnc:mana_forging','ingredients':[{'item':'minecraft:'+input,'count':1}],'result':{'item':'tnc:'+id},'mana':cost,'work_ticks':sec*20,'category':'materials'})
def shaped(id,pattern,key,count=1):write(Path('data/tnc/recipes')/(id+'.json'),{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'tag' if v.startswith('#') else 'item':v.lstrip('#')} for k,v in key.items()},'result':{'item':'tnc:'+id,'count':count}})
shaped('pasture_cage',['III','IBI','L L'],{'I':'tnc:spirit_iron','B':'minecraft:glass_bottle','L':'#minecraft:logs'})
shaped('radiant_cage',['GIG','LBL','GIG'],{'I':'tnc:spirit_iron','G':'tnc:radiant_gold','B':'minecraft:glass_bottle','L':'#minecraft:logs'})
shaped('star_cage',['CGC','IBI','LGL'],{'I':'tnc:spirit_iron','G':'tnc:radiant_gold','C':'tnc:star_marrow','B':'minecraft:glass_bottle','L':'#minecraft:logs'})
shaped('mana_meter',[' C ',' I ',' L '],{'C':'minecraft:glass','I':'tnc:spirit_iron','L':'minecraft:stick'})
reagents={
'mana_infuser':'minecraft:copper_ingot','dew_collector':'tnc:verdant_vein_seed','breath_collector':'tnc:empty_breath_jar','color_target':'minecraft:red_dye',
'mana_thread':'minecraft:string','radiant_thread':'tnc:dewgrass_thread','star_thread':'tnc:storm_crown_tip','mana_basin':'minecraft:clay_ball','radiant_basin':'tnc:foldleaf_film','star_basin':'tnc:cycle_sandgrain',
'mana_mirror':'tnc:mirror_scale','star_mirror':'tnc:prism_crown_petal','mana_valve':'tnc:shadow_hour_petal','mana_lantern':'minecraft:torch','bright_mana_lantern':'minecraft:glowstone_dust',
'mana_pulp_press':'tnc:page_fern_fiber','mana_feed_plate':'tnc:dream_humus','gentle_irrigator':'minecraft:bucket','climate_lantern':'tnc:dewgrass_thread','mana_sawmill':'minecraft:iron_axe',
'mist_loom':'tnc:mist_cotton_fiber','meal_dryer':'minecraft:smoker','mana_incubator':'tnc:soft_down','harvest_arm':'minecraft:iron_hoe','seed_sorter':'minecraft:wheat_seeds',
'mana_cart_track':'minecraft:rail','ward_bell':'tnc:warning_moss_spore','accessory_charger':'tnc:ore_dream_flake'}
for id,name in nodes:
    material='tnc:star_marrow' if id.startswith('star_') else 'tnc:radiant_gold' if id.startswith('radiant_') or id in ['accessory_charger','mist_loom'] else 'tnc:spirit_iron'
    if id=='mana_infuser':
        shaped(id,[' G ','C C','SSS'],{'G':'minecraft:glass','C':'minecraft:copper_ingot','S':'minecraft:stone'});continue
    shaped(id,[' M ','MLM',' B '],{'M':material,'L':reagents[id],'B':'minecraft:stone_bricks'},4 if 'thread' in id else 1)
accessory_core={'homeward_ribbon':'tnc:homeward_flower_petal','descent_feather':'tnc:air_plume','ore_whisper_earring':'tnc:ore_dream_flake','dew_ward_charm':'tnc:lantern_antler','mana_building_ruler':'minecraft:iron_pickaxe','rune_crossbow':'minecraft:crossbow','mana_mech':'minecraft:iron_chestplate','mech_spellgun':'tnc:storm_crystal'}
for id,name,capacity in re.findall(r'\w+\("([a-z_]+)","([^"]+)",(\d+)\)',(J/'RouteAccessory.java').read_text(encoding='utf-8')):
    shaped(id,[' C ','MTM',' I '],{'C':accessory_core[id],'M':'tnc:star_marrow' if id in ['mana_mech','mech_spellgun'] else 'tnc:radiant_gold','T':'tnc:mist_cloth','I':'tnc:spirit_iron'})
for id,ingredient in [('season_handbook','minecraft:wheat'),('magic_garden_book','tnc:verdant_vein_seed'),('beast_ranch_book','minecraft:hay_block'),('mana_workshop_book','minecraft:copper_ingot')]:shaped(id,[' P ',' B '],{'P':ingredient,'B':'minecraft:book'})
# Every behaviour task references a real awarded advancement.
legacy=['road_bell_crop','night_gourd_crop','tide_reed_crop','hushcap_mushroom','rainletter_bush','homeward_flower','warning_moss','mana_root','verdant_vein','sky_vine']
botanical=re.findall(r'\w+\("([a-z_]+)","([^"]+)","([a-z_]+)","([^"]+)"',(R/'src/main/java/com/tnc/tnc/life/botanical/BotanicalSpecies.java').read_text(encoding='utf-8'))
animals=re.findall(r's\("([a-z_]+)","([^"]+)"',(R/'src/main/java/com/tnc/tnc/life/pasture/PastureSpecies.java').read_text(encoding='utf-8'))+[('bellwool_sheep','响铃羊')]
keys=['traditional/planted','traditional/harvested','traditional/cooked','network/collected','network/stored','network/worked','network/charged']
keys += [phase+'/'+id for phase in ['plant','harvest'] for id in legacy+[x[0] for x in botanical+plants]]
keys += [phase+'/'+id for phase in ['capture','nest','harvest'] for id,_ in animals]
for key in keys:write(Path('data/tnc/advancements/life_routes')/(key+'.json'),{'criteria':{'performed':{'trigger':'minecraft:impossible'}}})
write(Path('data/tnc/life_routes/manifest.json'),{'version':'20261007','magic_plants':legacy+[x[0] for x in botanical+plants],'animals':[x[0] for x in animals],'season_blocks':list(seasons),'machines':[x[0] for x in nodes]})
print(f'Assets/spec index: {len(seasons)} explicit seasonal block states; {len(botanical)+len(plants)+len(legacy)} magic plants; {len(animals)} animals; {len(nodes)} network blocks.')
