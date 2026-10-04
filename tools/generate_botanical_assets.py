"""Original cuboid plants, hard 16px palettes and real downstream recipes.

No borrowed pictures; mature silhouettes are authored separately for all 19 plants.
"""
from pathlib import Path
import json, math, hashlib
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1]; A=R/'src/main/resources/assets/tnc'; D=R/'src/main/resources/data/tnc'
def js(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
SPEC=[('dawn_disk','晨晖盘葵','dawn_honey','晨蜜',(239,187,61)),('hearth_pepper','炉息椒','hearth_pepper_fruit','暖椒',(214,99,38)),('mist_cotton','雾织棉','mist_cotton_fiber','雾棉',(191,215,210)),('stone_fern','岩函蕨','stone_pattern_leaf','岩纹叶',(119,137,110)),('mirror_lotus','镜潭莲','mirror_dew','镜露',(161,203,209)),('wish_puff','灯愿蒲','farlight_fruit','远照果',(207,189,79)),('echo_bean','回声豆','echo_bean_pod','回声豆',(125,157,73)),('ladder_vine','梯脊藤','vine_sinew','藤筋',(87,132,62)),('frost_chime','霜鸣蒿','frost_dew','霜露',(157,207,214)),('salt_ink','盐墨菌','salt_ink','盐墨',(91,86,126)),('wind_sail','风铃帆草','sail_fiber','帆纤维',(203,186,132)),('sleep_clock','钟眠草','sleep_leaf','静叶',(113,138,167)),('shadow_cut','影裁花','shadow_silk','影绢',(150,131,160)),('paper_tree','墨信树','paper_bark','纸皮',(204,195,164)),('flight_pod','换羽荚','rainproof_pod','防雨荚壳',(130,177,165)),('honey_cluster','共生蜜簇','honey_powder','蜜粉',(224,174,58)),('star_dew','星露藤','star_dew_fruit','星露果',(117,119,191)),('dance_bell','脉舞铃花','dance_petal','舞瓣',(186,117,147)),('star_rest','星憩蕊','star_rest_dew','星憩露',(139,158,204))]
def cube(lo,hi,tex='#stem',rot=None):
    out={'from':lo,'to':hi,'faces':{f:{'uv':[0,0,16,16],'texture':tex} for f in ['up','down','north','south','east','west']}}
    if rot:out['rotation']=rot
    return out
def leaf(x,y,z,w=5,d=3):return cube([x,y,z],[x+w,y+1,z+d],'#leaf')
def petal(x,y,z,w=3,h=2,d=3):return cube([x,y,z],[x+w,y+h,z+d],'#petal')
def stem(x,y,z,h=10,w=1):return cube([x,y,z],[x+w,y+h,z+w])
def geometry(s,age,mode):
    # Early growth develops organs in sequence. It is not a scaled mature mesh.
    if age==0:return [stem(7,0,7,3),leaf(5,2,7,3,2),leaf(8,3,6,3,2),petal(7,3,7,2,2,2)]
    h=8 if age==1 else 12 if age==2 else 15
    if s=='dawn_disk':
        e=[stem(7,0,7,10,2),leaf(3,4,6,5,4),leaf(9,7,5,5,4)]
        if age<3:e+=[petal(5,h-3,6,6,4,3)]
        return e
    if s=='hearth_pepper':
        e=[stem(7,0,7,h,2),stem(3,5,7,3,1),cube([4,7,7],[8,8,8]),cube([8,10,7],[13,11,8]),leaf(2,4,5,5,3),leaf(9,8,6,5,3)]
        for x,y,z in [(3,4,7),(11,7,7),(7,10,8)][:age]:e +=[petal(x,y,z,2,4,2),petal(x+1,y-1,z,2,2,2)]
        return e
    if s=='mist_cotton':
        e=[stem(7,0,7,h-2,2),leaf(2,4,5),leaf(9,6,7)]
        for x,y,z in [(3,6,5),(9,9,6),(5,12,9),(8,5,10)][:age+1]:e +=[petal(x,y,z,4,3,4),petal(x+1,y+3,z+1,2,1,2)]
        return e
    if s=='stone_fern':
        e=[stem(7,0,7,6,2)]
        for i in range(age+2):
            for sign in [-1,1]:e +=[leaf(7+sign*(i+1),2+i,6-i/2,3,4),cube([7+sign*(i+1),3+i,6-i/2],[8+sign*(i+1),4+i,10-i/2],'#petal')]
        return e
    if s=='mirror_lotus':
        e=[leaf(1,0,5,14,6),leaf(5,0,1,6,14),stem(7,0,7,4,2)]
        for x,z in [(5,5),(8,5),(5,8),(8,8)][:age+1]:e +=[petal(x,2,z,3,mode==3 and 4 or 2,3)]
        e +=[petal(7,3,7,2,3,2)];return e
    if s=='wish_puff':
        e=[stem(7,0,7,h-3,2),leaf(2,1,5,6,4),leaf(8,2,8,6,4)]
        for y,w in [(h-5,4),(h-3,8),(h-1,6)][:age]:e +=[petal(8-w/2,y,8-w/2,w,2,w)]
        e +=[petal(7,h,7,2,1,2)];return e
    if s=='echo_bean':
        e=[stem(7,0,7,h,2),leaf(2,4,6),leaf(9,8,7)]
        for i in range(age):e +=[cube([4+i*3,4+i*3,9],[6+i*3,7+i*3,11],'#leaf'),petal(4+i*3,5+i*3,11,2,1,1)]
        return e
    if s=='ladder_vine':return [stem(7,0,7,h,2),cube([3,5,7],[9,7,9]),cube([8,10,7],[13,12,9]),leaf(1,5,4,5,5),leaf(10,10,6,5,5)]
    if s=='frost_chime':
        e=[stem(7,0,7,h,1)]
        for x,y,z in [(3,2,6),(9,5,8),(5,8,10)][:age]:e +=[cube([x,y,z],[x+1,y+5,z+1],'#leaf'),petal(x-1,y+4,z-1,3,2,3),petal(x,y+6,z,1,2,1)]
        return e
    if s=='salt_ink':return [stem(6,0,6,3+age,3),petal(3,2+age,3,10,2,10),petal(4,4+age,4,8,2,8),petal(6,6+age,6,4,2,4),cube([4,3+age,4],[5,4+age,12],'#leaf'),cube([10,3+age,4],[11,4+age,12],'#leaf')]
    if s=='wind_sail':
        e=[stem(5,0,7,h,1),leaf(2,1,6,5,4)]
        for i in range(age):e +=[cube([6,5+i*3,6],[13-i,7+i*3,7],'#petal'),cube([12-i,4+i*3,6],[14-i,6+i*3,7],'#petal')]
        return e
    if s=='sleep_clock':
        e=[stem(7,0,7,h-3,2),leaf(2,2,6),leaf(9,5,6),petal(4,h-5,7,8,6,2),cube([7,h-4,9],[8,h-1,10],'#stem'),cube([5,h-2,9],[8,h-1,10],'#stem')];return e
    if s=='shadow_cut':
        e=[stem(7,0,7,h-4,2),leaf(2,3,6),leaf(9,5,7)]
        for x,z in [(3,6),(9,6),(6,3),(6,9)][:age+1]:e +=[petal(x,h-4,z,4,1,4),petal(x+1,h-3,z+1,2,2,2)]
        for i in range(mode+1):e +=[cube([4+i*2,h-2,7],[5+i*2,h-1,9],'#stem')]
        return e
    if s=='paper_tree':
        e=[stem(6,0,6,h,4)]
        for x,y,z in [(2,5,4),(9,8,7),(4,11,9)][:age]:e +=[leaf(x,y,z,5,5),cube([x,y-2,z],[x+4,y-1,z+1],'#petal'),cube([x+1,y-3,z],[x+4,y-2,z+1],'#petal')]
        return e
    if s=='flight_pod':
        e=[stem(7,0,7,6,2),leaf(2,2,4,6,5),leaf(8,4,8,6,5)]
        for i in range(age):e +=[petal(6,6+i*2,5+i,4,3,4),leaf(2,7+i*2,7+i,4,2),leaf(10,7+i*2,7+i,4,2)]
        return e
    if s=='honey_cluster':
        e=[stem(7,0,7,h-5,2),leaf(3,3,3,5,5),leaf(8,4,8,5,5)]
        for i,(x,z) in enumerate([(3,5),(8,5),(6,9)][:age]):e +=[cube([x,h-5,z],[x+4,h-2,z+4],'#petal' if mode>i else '#leaf'),cube([x+1,h-2,z+1],[x+3,h-1,z+3],'#stem')]
        return e
    if s=='star_dew':
        e=[cube([4,0,4],[6,h-3,6]),cube([10,0,10],[12,h-3,12]),cube([4,h-3,4],[12,h-2,6]),cube([10,h-3,4],[12,h-2,12]),leaf(1,3,4,4,4),leaf(11,6,8,4,4)]
        for i in range(age):e +=[petal(5+i*2,h-5-i,6+i,2,2,2)]
        return e
    if s=='dance_bell':
        e=[stem(7,0,7,4,2),leaf(3,1,6,5,4),leaf(8,2,8,5,4)]
        if age<3:e +=[stem(7,4,7,h-5,1),petal(6,h-3,6,4,3,4)]
        return e
    if s=='star_rest':
        e=[stem(7,0,7,h-7,2),leaf(3,1,3,5,5),leaf(8,2,8,5,5)]
        for i in range(age):
            y=h-6+i*2;e +=[petal(3+i,y,6,10-i*2,1,4),petal(6,y,3+i,4,1,10-i*2)]
        e +=[petal(7,h-1,7,2,2,2)];return e
    raise ValueError(s)
def texture(name,color,kind,seed):
    im=Image.new('RGBA',(16,16),color+(255,));d=ImageDraw.Draw(im)
    dark=tuple(max(0,c-35) for c in color);bright=tuple(min(255,c+30) for c in color)
    for y in range(16):
        for x in range(16):
            if (x*3+y*5+seed)%13<2:d.point((x,y),fill=bright)
            elif (x*5+y+seed)%17<2:d.point((x,y),fill=dark)
    if kind=='stem':
        for x in [3,8,13]:d.line((x,0,x,15),fill=dark)
    elif kind=='leaf':
        d.line((2,14,13,2),fill=bright,width=1)
        for i in [4,8,12]:d.line((i-1,15-i,i+3,15-i),fill=dark)
    else:
        for i in range(3):d.rectangle((3+i*4,4+(seed+i)%4,4+i*4,5+(seed+i)%4),fill=bright)
    (A/'textures/block').mkdir(parents=True,exist_ok=True);im.save(A/f'textures/block/{name}_{kind}.png')
def model(name,s,e,mode=1):js(A/f'models/block/{name}.json',{'render_type':'minecraft:cutout','textures':{'stem':f'tnc:block/{s}_stem','leaf':f'tnc:block/{s}_leaf','petal':f'tnc:block/{s}_petal','particle':f'tnc:block/{s}_leaf'},'elements':e})
zh={};en={};icons=[]
for index,(s,name,product,productname,color) in enumerate(SPEC):
    texture(s,(68+index%4*8,89+index%3*9,47+index%5*3),'stem',index)
    texture(s,(60+index%5*9,106+index%4*11,56+index%6*5),'leaf',index)
    texture(s,color,'petal',index)
    variants={}
    for age in range(4):
        for mode in range(4):
            key=f'{s}_{age}_{mode}';model(key,s,geometry(s,age,mode));variants[f'age={age},mode={mode}']={'model':f'tnc:block/{key}'}
    js(A/f'blockstates/{s}.json',{'variants':variants});icons +=[(s+'_seed',color,'seed'),(product,color,'product')]
    zh.update({f'block.tnc.{s}':name,f'item.tnc.{s}_seed':name+'种源',f'item.tnc.{product}':productname});en.update({f'block.tnc.{s}':s.replace('_',' ').title(),f'item.tnc.{s}_seed':s.replace('_',' ').title()+' Seed',f'item.tnc.{product}':product.replace('_',' ').title()})
model('dance_bell_joint','dance_bell',[stem(7,0,7,4,2),leaf(8,2,8,4,3)])
model('dance_bell_head','dance_bell',[petal(3,0,6,4,3,4),petal(9,0,6,4,3,4),petal(6,2,3,4,3,4),petal(6,2,9,4,3,4),petal(6,1,6,4,4,4)])
model('dawn_disk_head','dawn_disk',[petal(4,9,6,8,8,3),petal(2,11,6,2,4,3),petal(12,11,6,2,4,3),petal(6,17,6,4,2,3),cube([6,11,9],[10,15,10],'#stem')])
model('mist_cotton_ring','mist_cotton',[cube([2,7,2],[14,7.5,2.5],'#petal'),cube([13.5,7,2],[14,7.5,14],'#petal'),cube([2,7,13.5],[14,7.5,14],'#petal'),cube([2,7,2],[2.5,7.5,14],'#petal')])
model('botanical_sprite_body','star_rest',[petal(6,0,6,4,8,4),petal(5,8,5,6,5,6),cube([6,11,11],[7,12,12],'#stem'),cube([9,11,11],[10,12,12],'#stem')])
model('botanical_sprite_wing','star_rest',[petal(9,5,7,6,6,1),petal(11,11,7,3,2,1)])
for name,s,geo in [('botanical_vine_segment','ladder_vine',[stem(7,0,7,16,2),cube([3,5,7],[13,7,9]),cube([3,12,7],[13,14,9]),leaf(1,6,4,5,5)]),('botanical_paper_segment','paper_tree',[stem(6,0,6,16,4),leaf(1,6,2,7,7),leaf(8,11,7,7,7),petal(2,4,3,5,1,1),petal(10,9,8,4,1,1)]),('field_frame','ladder_vine',[stem(3,0,3,16,2),stem(11,0,3,16,2),cube([3,4,3],[13,6,5]),cube([3,12,3],[13,14,5])]),('salt_basin','salt_ink',[cube([2,0,2],[14,2,14]),cube([2,2,2],[14,5,4]),cube([2,2,12],[14,5,14]),cube([2,2,4],[4,5,12]),cube([12,2,4],[14,5,12]),cube([4,2,4],[12,3,12],'#petal')])]:
    model(name,s,geo);js(A/f'blockstates/{name}.json',{'variants':{f'segment={i}':{'model':f'tnc:block/{name}'} for i in range(1,4)}} if 'segment' in name else {'variants':{'':{'model':f'tnc:block/{name}'}}})
    if 'segment' not in name:js(A/f'models/item/{name}.json',{'parent':f'tnc:block/{name}'})
processed={'hearth_oil':'余热椒油','morning_honey_breakfast':'晨蜜早餐','hearth_pepper_soup':'暖椒汤','frost_pepper_meal':'霜椒餐','mist_cloth':'雾布','folded_mist_tent':'折叠雾帐','folded_descent_cloth':'折叠缓降布','mirror_lotus_drink':'镜潭饮','sleep_clock_tea':'钟眠茶','star_dew_drink':'星露饮','star_rest_drink':'星憩饮','honey_cluster_cake':'蜜簇糕','survey_route_page':'调查路签','stone_rubbing_page':'岩面拓页','shadow_pattern_card':'八纹图样卡','flower_gift':'花礼','field_envelope':'野邮信封','condensing_shell':'冷凝壳','energy_saving_crystal':'节能晶芯','companion_bell_chip':'伴步铃芯','dawn_direction_page':'定向墨页','plant_sample_clip':'植株采样夹','rain_watering_flask':'雨囊水壶','pollination_brush':'授粉刷','field_tuning_bell':'田间调律铃','rainproof_seed_box':'防雨种匣','survey_archive_folder':'调查档案夹','climbing_rope':'登山绳'}
for i,(s,name) in enumerate(processed.items()):icons.append((s,(124+i%5*19,115+i%4*17,82+i%7*13),'tool' if i>20 else 'processed'));zh['item.tnc.'+s]=name;en['item.tnc.'+s]=s.replace('_',' ').title()
for index,(s,color,kind) in enumerate(icons):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);dark=tuple(max(0,c-53) for c in color);bright=tuple(min(255,c+50) for c in color)
    if kind=='seed':
        x=4+index%3;d.polygon([(x,4),(x+4,2),(x+7,6),(x+5,12),(x+1,14),(x-1,9)],fill=color,outline=dark);d.line((x+2,5,x+4,11),fill=bright,width=1);d.point((x+5,7),fill=bright)
    elif s.endswith('drink') or s.endswith('tea') or s.endswith('dew') or s.endswith('honey'):
        d.rectangle((6,1,9,4),fill=(164,174,175));d.polygon([(5,5),(10,5),(12,8),(11,14),(4,14),(3,8)],fill=(150,178,180),outline=(51,73,79));d.rectangle((5,8,10,12),fill=color);d.line((5,6,4,10),fill=(223,233,225));d.rectangle((6,0,9,1),fill=(107,76,46))
    elif 'page' in s or 'card' in s or 'envelope' in s:
        d.rectangle((3,2,12,13),fill=(211,200,166),outline=(95,85,63));d.polygon([(9,2),(12,5),(9,5)],fill=(160,149,117));d.line((5,7,10,7),fill=dark);d.line((5,10,8+index%3,10),fill=color)
    elif kind=='tool':
        d.line((3,13,11,4),fill=(83,71,49),width=3);d.rectangle((9,2,13,6),fill=color,outline=dark);d.line((10,2,13,3),fill=bright)
    elif 'cloth' in s or 'fiber' in s or 'silk' in s or 'petal' in s:
        d.polygon([(3,4),(10,2),(13,6),(11,12),(5,14),(2,10)],fill=color,outline=dark);d.line((4,5,10,4),fill=bright);d.line((5,7,12,6),fill=dark);d.line((5,11,10,10),fill=bright)
    else:
        d.polygon([(3,6),(6,3),(11,4),(13,8),(11,12),(6,14),(3,11)],fill=color,outline=dark);d.rectangle((5,5,8,6),fill=bright);d.rectangle((8,10,10,12),fill=dark);d.point((11,7),fill=bright)
    (A/'textures/item').mkdir(parents=True,exist_ok=True);im.save(A/f'textures/item/{s}.png');js(A/f'models/item/{s}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'tnc:item/{s}'}})
zh.update({'block.tnc.field_frame':'田间木支架','block.tnc.salt_basin':'盐水盆','entity.tnc.botanical_sprite':'星憩花灵'});en.update({'block.tnc.field_frame':'Field Frame','block.tnc.salt_basin':'Salt Basin','entity.tnc.botanical_sprite':'Star-rest Sprite'})
js(R/'work/botanical-lang-zh.json',zh);js(R/'work/botanical-lang-en.json',en)
def ing(s):return {'type':'forge:nbt','item':'minecraft:potion','nbt':{'Potion':'minecraft:water'}} if s=='water_bottle' else {'item':s if ':' in s else 'tnc:'+s}
def craft(name,inputs,out,count=1):js(D/f'recipes/botanical_{name}.json',{'type':'tnc:field_kitchen','ingredients':[ing(s) for s in inputs],'result':{'item':out if ':' in out else 'tnc:'+out,'count':count}})
def forge(name,inputs,out,count,mana,ticks):js(D/f'recipes/botanical_{name}.json',{'type':'tnc:mana_forging','ingredients':[dict(ing(s),count=n) for s,n in inputs],'result':{'item':out if ':' in out else 'tnc:'+out,'count':count},'mana':mana,'work_ticks':ticks,'category':'workshop'})
craft('sample_clip',['minecraft:shears','minecraft:paper','minecraft:paper'],'plant_sample_clip');craft('water_flask',['minecraft:clay_ball','minecraft:clay_ball','minecraft:clay_ball','rainletter_berry','tide_reed_stem'],'rain_watering_flask');craft('brush',['honey_powder','minecraft:stick','minecraft:feather'],'pollination_brush');craft('tuning_bell',['minecraft:copper_ingot','minecraft:amethyst_shard','minecraft:stick'],'field_tuning_bell');craft('frame',['minecraft:stick']*4+['minecraft:string'],'field_frame');craft('salt_basin',['minecraft:clay_ball']*3+['minecraft:water_bucket'],'salt_basin')
craft('oil',['hearth_pepper_fruit'],'hearth_oil');craft('breakfast',['dawn_honey','minecraft:egg','road_bell_ear','road_bell_ear'],'morning_honey_breakfast');craft('warm_soup',['hearth_pepper_fruit']*2+['minecraft:cooked_beef','minecraft:bowl'],'hearth_pepper_soup');craft('frost_meal',['frost_dew','hearth_pepper_fruit','minecraft:cooked_beef'],'frost_pepper_meal');craft('mist_cloth',['mist_cotton_fiber']*2+['minecraft:string'],'mist_cloth');craft('mist_tent',['mist_cloth','minecraft:stick','minecraft:stick'],'folded_mist_tent');craft('slow_fall',['sail_fiber']*4+['minecraft:string']*2,'folded_descent_cloth');craft('mirror_drink',['mirror_dew']*2+['rainletter_berry','minecraft:glass_bottle'],'mirror_lotus_drink');craft('sleep_tea',['sleep_leaf']*2+['water_bottle'],'sleep_clock_tea');craft('star_drink',['star_dew_fruit','water_bottle'],'star_dew_drink');craft('rest_drink',['star_rest_dew','water_bottle'],'star_rest_drink');craft('honey_cake',['honey_powder']*2+['minecraft:honey_bottle','road_bell_ear'],'honey_cluster_cake',2);craft('survey_page',['minecraft:paper']*3+['salt_ink'],'survey_route_page');craft('direction_page',['dawn_honey','salt_ink','minecraft:paper','minecraft:paper'],'dawn_direction_page');craft('envelopes',['paper_bark','paper_bark','salt_ink'],'field_envelope',3);craft('paper',['paper_bark','paper_bark'],'minecraft:paper',3);craft('flower_gift',['dance_petal','dance_petal','minecraft:paper'],'flower_gift');craft('flight_shell_patch',['rainproof_pod','minecraft:string'],'minecraft:leather');craft('vine_lead',['vine_sinew','vine_sinew','minecraft:string'],'minecraft:lead');craft('echo_note',['echo_bean_pod','echo_bean_pod','minecraft:oak_planks','minecraft:redstone'],'minecraft:note_block')
craft('seed_box',['minecraft:oak_planks','minecraft:oak_planks','rainproof_pod','rainproof_pod','minecraft:slime_ball'],'rainproof_seed_box');craft('archive_folder',['paper_bark','paper_bark','minecraft:paper','minecraft:paper','minecraft:paper','minecraft:string'],'survey_archive_folder');craft('climbing_rope',['vine_sinew','vine_sinew','resonant_fleece'],'climbing_rope')
forge('bell_chip',[('dance_petal',2),('minecraft:copper_ingot',1)],'companion_bell_chip',1,15,100);forge('energy_crystal',[('star_rest_dew',1),('minecraft:quartz',1),('mana_copper_coil',1)],'energy_saving_crystal',1,20,100);forge('condensing_shell',[('frost_dew',2),('minecraft:slime_ball',1),('minecraft:clay_ball',2)],'condensing_shell',1,20,120)
js(D/'gifts/field_flower_gift.json',{'conditions':[],'responses':{'fail':'gift.flowers.fail','good':'gift.flowers.good','better':'gift.flowers.better','best':'gift.flowers.best'},'items':{'tnc:flower_gift':8,'tnc:sleep_clock_tea':6,'tnc:morning_honey_breakfast':6}})
js(R/'src/main/resources/data/minecraft/tags/blocks/flowers.json',{'replace':False,'values':['tnc:honey_cluster','tnc:dance_bell','tnc:dawn_disk','tnc:star_rest']})
for block in ('field_frame','salt_basin'):
    js(D/f'loot_tables/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'tnc:'+block}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
print(f'Generated {len(SPEC)} independent botanical silhouettes, {len(icons)} hard-pixel item assets, and gameplay recipes.')
