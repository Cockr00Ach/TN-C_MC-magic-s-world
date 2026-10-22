"""Build native cuboid models and mechanically slice the approved generated sprite atlas.
Does not redraw or synthesize raster art; existing copper/ceramic textures are reused.
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "src/main/resources/assets/tnc"
D = ROOT / "src/main/resources/data/tnc"

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf8")

def cube(a,b,tex,rotation=None):
    out={"from":a,"to":b,"faces":{f:{"texture":"#"+tex} for f in ["north","south","east","west","up","down"]}}
    if rotation is not None: out["rotation"]={"origin":[8,8,8],"axis":"y","angle":rotation}
    return out

textures={n:f"tnc:block/alchemy_{n}" for n in ["ceramic","bronze","glow","wood"]}
textures["particle"]=textures["ceramic"]
parts=[cube([4,3,4],[12,5,12],"ceramic")]
# Octagonal vessel: four straight walls plus four chamfered walls, genuinely open inside.
for y0,y1,r in [(4,6,5),(6,10,6),(10,12,6.5),(12,13,7)]:
    tex="bronze" if y0 in (4,12) else "ceramic"
    for rot in (0,45):
        for a,b in [([8-r,y0,5.4],[9-r,y1,10.6]),([7+r,y0,5.4],[8+r,y1,10.6]),
                    ([5.4,y0,8-r],[10.6,y1,9-r]),([5.4,y0,7+r],[10.6,y1,8+r])]:
            parts.append(cube(a,b,tex,rot))
# Three broad swept feet and their gem settings.
for x,z in [(3,3),(11,3),(7,12)]:
    parts.extend([cube([x,0,z],[x+2,1,z+2],"bronze"),cube([x+.4,1,z+.4],[x+1.6,4,z+1.6],"ceramic"),
                  cube([x+.5,2,z+.1],[x+1.5,3,z+.5],"glow")])
# Stout handles with a visible hole; no solid box across the mouth.
for x in (0,14):
    parts.extend([cube([x,8,5.5],[x+2,9.2,10.5],"bronze"),
                  cube([x,9,5.5],[x+1,12,6.5],"bronze"),
                  cube([x,9,9.5],[x+1,12,10.5],"bronze"),
                  cube([x,11.2,5.5],[x+1,12.2,10.5],"bronze")])
for x in (4,8,12):
    parts.append(cube([x-.35,8,1.6],[x+.35,10,2.2],"glow"))
display={"gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[.68,.68,.68]},
         "ground":{"scale":[.35,.35,.35]},"fixed":{"scale":[.65,.65,.65]}}
write(A/"models/block/spellflame_cauldron.json",{"textures":textures,"elements":parts,"display":display})
stirrer=[cube([7.5,0,7.5],[8.5,13,8.5],"bronze"),cube([6,1,7],[10,3,9],"ceramic"),
         cube([7,12,7],[9,15,9],"glow"),cube([6.5,11,6.5],[9.5,12,9.5],"bronze")]
write(A/"models/item/ritual_stirrer.json",{"textures":textures,"elements":stirrer,"display":{
    "gui":{"rotation":[0,0,-35],"scale":[.85,.85,.85]},"firstperson_righthand":{"rotation":[0,0,-25],"translation":[1,1,0],"scale":[.8,.8,.8]}}})
write(D/"recipes/ritual_stirrer.json",{"type":"minecraft:crafting_shaped","pattern":["  A"," C ","S  "],
    "key":{k:{"item":v} for k,v in {"A":"minecraft:amethyst_shard","C":"minecraft:copper_ingot","S":"minecraft:stick"}.items()},
    "result":{"item":"tnc:ritual_stirrer"}})
board=[cube([0,1,6],[16,16,9],"wood"),cube([0,0,5.5],[1,16,9.5],"bronze"),
       cube([15,0,5.5],[16,16,9.5],"bronze"),cube([0,15,5.5],[16,16,9.5],"bronze"),
       cube([0,0,5.5],[16,1,9.5],"bronze")]
textures["paper"]="minecraft:block/birch_planks"
for x,y in [(2,8),(8,8),(2,2),(9,2)]:
    board.extend([cube([x,y,9],[x+4.5,y+5,9.2],"paper"),
                  cube([x+1.8,y+4.5,9.2],[x+2.5,y+5.2,9.5],"glow"),
                  cube([x+.7,y+2.8,9.2],[x+3.7,y+3.05,9.25],"wood"),
                  cube([x+.7,y+1.8,9.2],[x+2.8,y+2.05,9.25],"wood")])
write(A/"models/block/commission_board.json",{"textures":textures,"elements":board,"display":display})
write(A/"models/item/commission_board.json",{"parent":"tnc:block/commission_board"})
write(A/"blockstates/commission_board.json",{"variants":{f"facing={f}":{"model":"tnc:block/commission_board","y":y} for f,y in [("south",0),("west",90),("north",180),("east",270)]}})
write(D/"loot_tables/blocks/commission_board.json",{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"tnc:commission_board"}]}]})
write(D/"recipes/commission_board.json",{"type":"minecraft:crafting_shaped","pattern":["CCC","PPP","CCC"],"key":{
    "C":{"item":"minecraft:dark_oak_planks"},"P":{"item":"minecraft:paper"}},"result":{"item":"tnc:commission_board"}})
loot=D/"loot_tables/blocks/spellflame_cauldron.json"
obj=json.loads(loot.read_text("utf8"))
ops=obj["pools"][0]["entries"][0]["functions"][0]["ops"]
for key in ["Water","Stirs","StirredAt","Ready","Burst"]:
    if not any(o["source"]==key for o in ops):ops.append({"source":key,"target":"BlockEntityTag."+key,"op":"replace"})
write(loot,obj)
for lang in ["zh_cn","en_us"]:
    p=A/f"lang/{lang}.json";data=json.loads(p.read_text("utf8"))
    data.update({"item.tnc.ritual_stirrer":"铜枝搅拌棒","block.tnc.commission_board":"旧铜纸签委托栏"})
    write(p,data)

atlas=ROOT/"art/alchemy/ritual-potions-atlas.png"
if atlas.exists():
    im=Image.open(atlas).convert("RGBA")
    ids="bloom spring awaken flow edge stride shell focus cinder tide feather cleanse night echo hearth revive".split()
    for i,id in enumerate(ids):
        x,y=i%4,i//4
        cell=im.crop((x*im.width//4,y*im.height//4,(x+1)*im.width//4,(y+1)*im.height//4))
        box=cell.getbbox()
        if box:cell=cell.crop(box)
        cell.thumbnail((58,58),Image.Resampling.NEAREST)
        icon=Image.new("RGBA",(64,64));icon.alpha_composite(cell,((64-cell.width)//2,(64-cell.height)//2))
        icon.save(A/f"textures/item/elixir_{id}.png")
    print("Extracted 16 distinct potion sprites from generated atlas")

# Preserve every other chapter and the stable IDs in this one.
qpath=ROOT/"questbook/ftbquests/chapters/tnc_alchemy.snbt"
q=json.loads(qpath.read_text("utf8"))
for node in q["quests"]:
    title=node["title"]
    if "第一口药釜" in title:
        node["description"]=["铜锭×2、铁锭×3、炼药锅×1，按JEI合成。",
        "水桶右键倒水，一桶可炼三剂。三种原料各一份丢进锅口，也可手持右键投放。",
        "加入焰蕊盐，再用实际火系法术（如火球）命中锅身点火。燧石、火焰弹物品和GUI均不能点火。",
        "浸润、融合、凝结三阶段，按提示空手或铜枝搅拌棒右键。每阶段搅拌一次；暂停不损失材料。",
        "完成后用空玻璃瓶盛取。潜行空手取回原料；手持书右键查看药典。"]
    else:
        node["description"]=[line.replace("；再放空玻璃瓶与焰蕊盐。","；投入焰蕊盐，用火系法术点燃，按阶段搅拌，最后用空瓶盛取。")
                            .replace("；普通火为1.5倍。","（不含等待搅拌的时间），仅火魔法可点燃。")
                            for line in node.get("description",[])]
write(qpath,q)
print("Built open ritual cauldron, stirrer, posted board and revised only alchemy chapter")

# Apply the current physical-cauldron and plant-only meal design after historical generation.
from revise_arcana_feedback_assets import run as revise_arcana_feedback
revise_arcana_feedback()

# Keep the approved all-stage blueprint authoritative after historical asset generation.
import build_magic_progression as _magic_blueprint
_magic_blueprint.run()
