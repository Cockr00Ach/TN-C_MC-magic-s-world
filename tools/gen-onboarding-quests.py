"""Generate only TN-C owned advancement targets and a native FTB gameplay chapter."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
steps = [
    ("inspected", "认识魔法石", "minecraft:amethyst_shard", -6, 0, [], ["按V查看魔法石。亲和力是学习上限，魔力是施法资源，魔法点数用于学习。", "新角色有4点教学额度，每十冒险等级再获得5点；原版附魔经验不会扣冒险成长。"]),
    ("arrived", "天空岛的邀请", "minecraft:ender_pearl", -3, 0, [0], ["等待天空岛投来注视，沿提示寻找传送阵，蓄能后上岛。", "地图与剧情沿用现有内容；此目标检测实际抵达岛上入口。"]),
    ("registered", "酒馆登记", "tnc:adventure_handbook", 0, 0, [1], ["到酒馆入门上楼北侧，右键协会接待员艾琳。", "在协会档案页登记初级。Self仍是原有剧情人物。", "没有手册时可使用 /adventure 打开档案，/adventure handbook 补领手册。"]),
    ("accepted", "第一张委托", "minecraft:paper", 3, 0, [2], ["右键酒馆原生Bountiful栏接取纸张，或做一次性教学归档单：4原木与8圆石。", "原生纸张期限与数量见栏界面；已接的旧单仍可归档交付。"]),
    ("first_contract", "你的第一笔收入", "minecraft:sunflower", 6, 0, [3], ["手持完成的原生委托右键酒馆栏交付；教学归档单获得120经验、10声望与30铜。", "服务端只结算一次，任务书不再次发钱；FTB队伍共享进度不会共享个人账户。"]),
    ("ordered", "把材料交给匠人", "minecraft:anvil", 6, 3, [4], ["准备4木棍与2铜锭，找29号潮生制杖屋莉娅，选任一元素一阶；首次免人工费。", "七系共35件法器已可铸造；农具、家具和建筑配方保留。"]),
    ("forged", "炉火交付", "tnc:magic_wand", 3, 3, [5], ["20秒有效服务器运行时间后领取法杖。", "退出不丢订单；满背包不吞成品，腾出位置后再领。"]),
    ("learned", "你的第一道法术", "minecraft:enchanted_book", 0, 3, [6], ["按V，选择亲和力允许的一级法术学习。知识永久属于角色。", "学法只同步已有法杖，丢杖后请回馆补造。旧档已学内容与遗忘修复保留。"]),
    ("cast", "向世界出发", "minecraft:iron_sword", -3, 3, [7], ["持法杖在安全的野外施放一次已学法术，完成启程教学。", "讨伐与委托提供永久冒险经验。之后可接食材、料理和巡猎单，自由选择远征或生活。"]),
    ("promoted", "精锐的证明", "minecraft:golden_sword", -6, 3, [8], ["达到Lv10与100协会声望后，回酒馆申请精锐晋升。", "原生补给、食材与巡猎已开放；协会高阶职称挑战后续建设。"]),
]

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

quests=[]
for i,(key,title,icon,x,y,deps,description) in enumerate(steps):
    write(ROOT/f"src/main/resources/data/tnc/advancements/onboarding/{key}.json", {"criteria":{"done":{"trigger":"minecraft:impossible"}}})
    quests.append({"id":f"544E431200{i:06X}","title":title,"icon":icon,"x":x,"y":y,"size":1.2,
                   "shape":"hexagon","description":["&b&l"+title,"",*["&f"+s for s in description]],
                   "dependencies":[f"544E431200{d:06X}" for d in deps],"rewards":[],
                   "tasks":[{"id":f"544E431300{i:06X}","type":"advancement","advancement":f"tnc:onboarding/{key}","criterion":"done","title":"完成实际行为"}]})
write(ROOT/"questbook/ftbquests/chapters/tnc_play_01_onboarding.snbt",{
    "id":"544E431100000000","filename":"tnc_play_01_onboarding","group":"544E434755494445",
    "title":"&6&l启程 · 一次真正的冒险","icon":"tnc:adventure_handbook","order_index":-102,
    "default_hide_dependency_lines":False,"default_quest_shape":"hexagon","quest_links":[],
    "images":[{"image":"tnc:textures/guide/onboarding_header.png","x":0.0,"y":-4.0,"width":14.0,"height":7.0,"rotation":0.0}],
    "quests":quests})
life_steps=[
    ("pouches","整理旅途行囊","tnc:food_pouch",[],"E背包旁点行囊，检查独立钱袋槽、食物袋槽与五格食物。袋子余额与食物保存在个人账户。"),
    ("meal","给自己做一顿饭","minecraft:bread",[0],"普通食用或袋子快捷食用一次，消耗真实食物并返还容器。真死亡后饥饿6/20，请带好补给。"),
    ("farm_learned","小麦的谢礼","tnc:farm_focus",[1],"结算一份原生食材类委托，学农事并获得焦点；旧已接小麦单也兼容。通用魔法不花战斗学习点。"),
    ("farm_cast","初芽","minecraft:wheat",[2],"手持焦点对12格内农田右键，白名单作物促进一个阶段。Lv10/30开放后两阶，潜行右键切换。"),
    ("delicacy","记住一席珍馐","tnc:boss_feast_1",[1],"独立首领珍料＋熟牛肉＋碗。八种珍馐首次各加2生命，同一种不重复加。此为可选远征支线。"),
    ("neighbor_met","向邻居问好","tnc:resident",[],"东街与南街三栋居民房有六位成年邻居；右键后点击互动。剧情人物保持原有身份。"),
    ("home_bought","属于你的空屋","minecraft:oak_door",[1],"攒5银，看房后找19号归航银行米洛，确认购买南街三层空屋。自己布置床、储物和纪念品；独居也可完成。"),
    ("shared_meal","一起吃饭","minecraft:mushroom_stew",[5],"主手拿两份同菜，在邻居对话中明确选择共餐。每20分钟有效游戏时间一次，返还容器并保留料理效果。"),
    ("living_together","自愿的归处","minecraft:white_bed",[6,7],"亲密70、三种菜、三次共餐，自购房内两张床与安全空间。选择邀请，愿意的成年邻居才入住；可结束，友情保留。婚礼与孩子后续开放。")]
nodes=[]
for i,(key,title,icon,deps,description) in enumerate(life_steps):
    write(ROOT/f"src/main/resources/data/tnc/advancements/onboarding/{key}.json",{"criteria":{"done":{"trigger":"minecraft:impossible"}}})
    nodes.append({"id":f"544E441200{i:06X}","title":title,"icon":"minecraft:emerald" if icon=="tnc:resident" else icon,"x":-6+(i%5)*3,"y":(i//5)*3,"size":1.2,"shape":"hexagon","description":["&a&l"+title,"","&f"+description],"dependencies":[f"544E441200{d:06X}" for d in deps],"rewards":[],"tasks":[{"id":f"544E441300{i:06X}","type":"advancement","advancement":f"tnc:onboarding/{key}","criterion":"done","title":"完成实际行为"}]})
write(ROOT/"questbook/ftbquests/chapters/tnc_play_02_home.snbt",{"id":"544E441100000000","filename":"tnc_play_02_home","group":"544E434755494445","title":"&a&l归处 · 田园与同行者","icon":"tnc:food_pouch","order_index":-101,"default_hide_dependency_lines":False,"default_quest_shape":"hexagon","quest_links":[],"images":[],"quests":nodes})
