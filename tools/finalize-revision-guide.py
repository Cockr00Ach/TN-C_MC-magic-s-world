"""Update revision generators and factual guide without changing existing task IDs."""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[1]
replacements={
 '法器找工坊铎恩，房产找城务米洛。Self保持原剧情。':'法杖找29号潮生制杖屋莉娅，铁装备找28号炉石铁匠铺铎恩，存取币与房产找19号归航银行米洛。Self在41号酒馆介绍道路并继续原剧情。',
 '右键铸器工坊的铎恩':'右键29号潮生制杖屋的莉娅',
 'Self只负责既有剧情。锻造与买房分别由铎恩、米洛办理':'Self介绍道路并推进既有剧情。法杖、装备、银行分别由莉娅、铎恩、米洛办理',
 '六个玩法章节已开放 · 184个行为/物品目标':'七个玩法章节已开放 · 191个行为/物品目标',
 '六章包括启程、归处、七系法器、远行、城镇履历、归处续篇。新增加165个真实目标，连同原19个共184个':'七章包括主线起点、启程、归处、七系法器、远行、城镇履历、归处续篇。原19个加七系等165个与主线起点7个，共191个真实目标',
 'Self沿用剧情交互。':'主线页从上岛到酒馆见Self，再分制杖、铁匠、银行与茶灯会馆四路。',
 '订单找铎恩，不能在Self处领取。':'法杖订单找莉娅，铁装备找铎恩；Self会一次性给基础制杖材料。',
 '19城务、28工坊、41酒馆已安排岗位':'19银行、28铁匠、29制杖、41酒馆已安排岗位；34茶灯会馆保留娱乐用途',
 '19号城务事务所':'19号归航银行',
 '此档水系物品已可找铎恩打造。':'此档水系物品已可找莉娅打造。',
 '28号工坊铎恩':'29号潮生制杖屋莉娅',
 '19号城务米洛':'19号归航银行米洛',
 '铎恩订单：':'莉娅订单：',
 '铎恩 · 铸器工坊':'莉娅 · 潮生制杖屋',
 '米洛 · 城务房产':'米洛 · 归航银行',
 '真实目标请查看六个玩法章节':'真实目标请查看七个玩法章节',
 '银行专用存款、贷款与分期方案详见设计文档，尚未开放。':'存取币已开放；贷款与分期方案详见设计文档，尚未开放。',
}
for rel in ['tools/revise-town-guide.py','tools/gen-onboarding-quests.py','tools/gen-town-progression.py','tools/gen-ftb-guide.ps1','tools/gen-main-atlas.py','src/main/resources/assets/tnc/guide/gameplay.json']:
 p=R/rel;s=p.read_text(encoding='utf-8-sig')
 for a,b in replacements.items():s=s.replace(a,b)
 p.write_text(s,encoding='utf-8')
p=R/'src/main/resources/assets/tnc/guide/gameplay.json';o=json.loads(p.read_text(encoding='utf-8'))
for page in o['pages']:
 if page['id']=='money':
  page['sections'][2]['text']='收入来自原生酒馆委托及旧单归档，支出为七系法器、铁匠装备与500铜住宅。最近五笔流水在档案显示。\n19号归航银行可一次存入背包中的实体铜/银/金币，或每次取出一枚指定币；银行与钱袋共用个人余额。借贷和房屋分期是下一阶段设计，当前没有贷款按钮或自动扣息。'
p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Updated roles, opening structure, bank scope and 191 real goals.')
