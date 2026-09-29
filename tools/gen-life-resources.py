"""TN-C owned item models, translatable names and eight real boss-cuisine recipes."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]/"src/main/resources"
def write(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
models={"money_pouch":"minecraft:item/bundle","food_pouch":"minecraft:item/bundle","copper_coin":"minecraft:item/raw_copper","silver_coin":"minecraft:item/iron_nugget","gold_coin":"minecraft:item/gold_nugget","farm_focus":"minecraft:item/wheat"}
names={"adventure_handbook":"冒险手册","money_pouch":"钱袋 · 专属槽","food_pouch":"旅途食物袋 · 五格","copper_coin":"铜币","silver_coin":"银币","gold_coin":"金币","farm_focus":"农事魔法焦点"}
flavors=["天穹龙宴","幽冥炖锅","炎冠煨肉","熔炉浓汤","虚空烩盘","潮渊鲜锅","荒漠香炖","亡魂珍馐"]
for i,name in enumerate(flavors,1):
    models[f"boss_feast_{i}"]="minecraft:item/rabbit_stew"
    models[f"boss_ingredient_{i}"]="minecraft:item/nether_star"
    names[f"boss_feast_{i}"]=name+" · 首次品尝生命+2"
    names[f"boss_ingredient_{i}"]=name+"的可食珍料"
    write(ROOT/f"data/tnc/recipes/boss_feast_{i}.json",{"type":"minecraft:crafting_shapeless","ingredients":[{"item":f"tnc:boss_ingredient_{i}"},{"item":"minecraft:cooked_beef"},{"item":"minecraft:bowl"}],"result":{"item":f"tnc:boss_feast_{i}","count":1}})
for item,texture in models.items():write(ROOT/f"assets/tnc/models/item/{item}.json",{"parent":"minecraft:item/generated","textures":{"layer0":texture}})
for locale in ["zh_cn","en_us"]:
    path=ROOT/f"assets/tnc/lang/{locale}.json";data=json.loads(path.read_text(encoding="utf-8-sig"));data.update({f"item.tnc.{k}":v for k,v in names.items()});write(path,data)
for key in ["pouches","meal","delicacy","farm_learned","farm_cast","home_bought","neighbor_met","shared_meal","living_together"]:write(ROOT/f"data/tnc/advancements/onboarding/{key}.json",{"criteria":{"done":{"trigger":"minecraft:impossible"}}})
names={"money_pouch":[{"item":"minecraft:leather"},{"item":"minecraft:string"}],"food_pouch":[{"item":"minecraft:leather"},{"item":"minecraft:chest"}]}
for item,ingredients in names.items():write(ROOT/f"data/tnc/recipes/{item}.json",{"type":"minecraft:crafting_shapeless","ingredients":ingredients,"result":{"item":f"tnc:{item}","count":1}})
