"""Revision 3: user map, aligned native chapter layouts, accurate cast introduction."""
from pathlib import Path
import json, shutil, re, runpy
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/assets/tnc'
CH=ROOT/'questbook/ftbquests/chapters'
def write(p,o): p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Keep item/task IDs; rename visible names and their source generators together.
for folder in [ROOT/'tools',ROOT/'src/main',CH]:
    for p in folder.rglob('*'):
        if p.suffix not in ['.json','.java','.py','.ps1','.snbt']: continue
        if p==Path(__file__): continue
        s=p.read_text(encoding='utf-8-sig')
        n=s.replace('冒险者法阵','冒险者法杖').replace('一级法阵','一级冒险者法杖')
        if n!=s:p.write_text(n,encoding='utf-8')

bookpath=RES/'guide/gameplay.json'
book=json.loads(bookpath.read_text(encoding='utf-8'))
world=book['pages'][0]
world['subtitle']='归的旅途 · 先认识同行的人'
concepts=[s for s in world['sections'] if s['heading'] in ['七种魔法师','先分清四个词','路线，而非一排图标','现在可以做']]
world['sections']=[
 {'heading':'熙永槐 · 盾与归路','text':'铁匠的儿子，战士。家庭美满，却还没有真正走出过城。\n他带着父亲尚未打完的盾踏上旅途，总念叨着早点找到衍、早点回去交差。'},
 {'heading':'庄鹊让 · 典籍与同行','text':'出身贵族的水系魔法师，与魔法协会及典籍管理有关。\n她心怀善意，也认识常来查书的衍。古籍与书架把她带入这段追寻。'},
 {'heading':'周坐望 · 看见的人','text':'大光魔法师，最初魔法协会的成立者，后来退隐山林。\n他曾激活归的魔法石，也看出了归的不同。他习惯先看，而后才开口。'},
 {'heading':'公孙衍 · 不告而别','text':'归的挚友，从普通市井走出的雷法天才。雷亲和六，无法使用光。\n他独自离开，归由此踏上寻找他的路。留下的书架与古籍，是最初的线索。'},
 {'heading':'Self · 酒馆的灯','text':'天空岛41号酒馆里的剧情人物。那杯未动的酒、挚友离开的消息，旅程从这次谈话继续。\n先找他听故事和认路；协会登记、制杖、铁匠与银行各由对应人员办理。'},
 {'heading':'城镇的匠人与接待','text':'艾琳：酒馆协会登记与晋升。莉娅：29号潮生制杖屋。\n铎恩：28号炉石铁匠铺。米洛：19号归航银行。\n他们各自经营自己的岗位，陪你完成最初的安顿。'},
 *concepts
]
weapons=book['pages'][1]
weapons['illustrations'][0]['description']='有完整杖柄的水系冒险者法杖，是莉娅制作的第一件法器。不是悬浮法阵。'
weapons['illustrations'][0]['texture']='tnc:textures/guide/water_focus_1.png'
write(bookpath,book)

# Customize the common generator, rather than hand-editing generated chapters.
p=ROOT/'tools/gen-ftb-guide.ps1';s=p.read_text(encoding='utf-8')
s=s.replace("$x=-6.0+$n*3.0", "$x=-4.0+$n*2.0")
s=s.replace("x=$x;y=-4.0;width=1.5;height=1.5", "x=$x;y=-3.0;width=1.8;height=1.8")
s=s.replace("'minecraft:stick' $x -2.0 0.75", "('tnc:water_wand_'+($n+1)) $x -1.6 0.85")
s=s.replace("$x=-6.0+($s%5)*3.0", "$x=$(if($i -eq 0){-4.0+($s%3)*4.0}elseif($i -eq 1){-3.0+($s%2)*6.0}else{-6.0+($s%5)*3.0})")
s=s.replace("$y=3.0+[math]::Floor($s/5)*3.0", "$y=$(if($i -eq 0){3.0+[math]::Floor($s/3)*2.6}elseif($i -eq 1){2.5+[math]::Floor($s/2)*2.6}else{3.0+[math]::Floor($s/5)*3.0})")
old="if($i -eq 0){$chapter.title='&6&l歸 · 世界';"
new="""if($i -eq 0){
        $nodes[0].title='&6&l归 · 吴归衡'
        $nodes[0].icon='minecraft:compass'
        $nodes[0].description=@('&6&l归 · 吴归衡','','&f普通市井家庭的少年，七系亲和皆为三，没有单一主修。','&f挚友衍不告而别后，他从酒馆出发，去寻找那个没有说清的答案。','','&7这片世界有剑、铠甲、魔法与未知。远行之外，酒馆、工坊和家也为你留着归处。')
        $castIcons=@('minecraft:shield','minecraft:enchanted_book','tnc:zuowang_spawn_egg','minecraft:amethyst_shard','tnc:self_spawn_egg','minecraft:anvil')
        for($c=0;$c -lt 6;$c++){$nodes[$c+1].icon=$castIcons[$c];$nodes[$c+1].size=1.2}
        $chapter.title='&6&l歸 · 世界';"""
if old in s:s=s.replace(old,new)
p.write_text(s,encoding='utf-8')

# Compact seven routes with a single aligned header row.
p=ROOT/'tools/gen-town-progression.py';s=p.read_text(encoding='utf-8')
s=s.replace('x=-18+col*6','x=-13.5+col*4.5').replace("'y':-5.5,'width':4.5,'height':4.5", "'y':-3.0,'width':2.6,'height':2.6")
s=s.replace('y=(tier-1)*6','y=(tier-1)*4.2').replace('x-1.5,y-2.2','x-1.0,y-1.4').replace('x+1.8,y+1.7','x+1.1,y+1.3')
p.write_text(s,encoding='utf-8')

# Screenshot copied without raster alterations. Locations are registered to its map grid.
source=ROOT/'docs/art/sky-island-user-map-20260929.png'
if not source.exists():
    source.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2('C:/Users/宋志坤/AppData/Local/Temp/codex-clipboard-073f5408-66e5-4689-9038-a5be559182fd.png',source)
shutil.copy2(source,RES/'textures/guide/town_atlas.png')

p=ROOT/'tools/gen-main-atlas.py';s=p.read_text(encoding='utf-8')
s=s.replace("shutil.copy2(mapdir/'天空岛建筑编号.png',guide/'town_atlas.png')", "shutil.copy2(ROOT/'docs/art/sky-island-user-map-20260929.png',guide/'town_atlas.png')")
# Three blocks per screenshot pixel grid step; the screenshot is shifted relative to the old crop.
s=s.replace("(90+(r['x']-100)*5)/50-22.3", "(r['x']*3-280)/40-1173/80")
s=s.replace("(125+(r['z']-85)*5)/50-20.05+0.5", "(r['z']*3-290)/40-1022/80")
s=s.replace("'size':0.55", "'size':0.95").replace("'width':44.6,'height':40.1", "'width':29.325,'height':25.55")
s=s.replace("'icon':'minecraft:map'", "'icon':{19:'tnc:copper_coin',28:'minecraft:anvil',29:'tnc:water_wand_1',34:'minecraft:jukebox',40:'minecraft:oak_door',41:'bountiful:bountyboard'}.get(i,'minecraft:map')")
s=s.replace('xy(-18+i*6,-4)','xy(-13.5+i*4.5,-3)')
p.write_text(s,encoding='utf-8')
runpy.run_path(str(ROOT/'tools/wand-display.py'),run_name='__main__')
print('Revision 3 sources updated; run native chapter generators next.')
