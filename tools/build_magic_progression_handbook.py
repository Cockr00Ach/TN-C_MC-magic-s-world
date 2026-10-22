from pathlib import Path
import json,re,collections
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources';P=R/'docs/魔导纪行全阶段实装手册-20261022.md'
V={'glass':'玻璃','copper_ingot':'铜锭','stone':'石头','iron_ingot':'铁锭','gold_ingot':'金锭','diamond':'钻石','stone_bricks':'石砖','clay_ball':'黏土球','string':'线','stick':'木棍','amethyst_shard':'紫水晶碎片','compass':'指南针','nether_star':'下界之星','netherite_ingot':'下界合金锭','dragon_breath':'龙息','chorus_fruit':'紫颂果','blaze_rod':'烈焰棒','crying_obsidian':'哭泣黑曜石','glistering_melon_slice':'闪烁的西瓜片','wheat':'小麦','sugar':'糖','kelp':'海带','honey_bottle':'蜂蜜瓶','redstone':'红石','iron_nugget':'铁粒','sweet_berries':'甜浆果','feather':'羽毛','scute':'鳞甲','magma_cream':'岩浆膏','nether_wart':'下界疣','pufferfish':'河豚','phantom_membrane':'幻翼膜','milk_bucket':'牛奶桶','glow_berries':'发光浆果','golden_carrot':'金胡萝卜','brown_mushroom':'棕色蘑菇','echo_shard':'回响碎片','bread':'面包','golden_apple':'金苹果','blaze_powder':'烈焰粉','bone_meal':'骨粉','fermented_spider_eye':'发酵蛛眼','slime_ball':'黏液球','prismarine_crystals':'海晶砂粒','glass_bottle':'玻璃瓶','bucket':'铁桶','daylight_detector':'阳光探测器','oak_log':'橡木原木','chest':'箱子','torch':'火把','leather':'皮革','gold_nugget':'金粒','ender_pearl':'末影珍珠','quartz':'下界石英','redstone_torch':'红石火把','obsidian':'黑曜石','paper':'纸','white_wool':'白羊毛'}
lang=json.loads((A/'assets/tnc/lang/zh_cn.json').read_text('utf8'))
def name(i):
 ns,k=i.split(':');return V.get(k,i) if ns=='minecraft' else lang.get('item.'+ns+'.'+k,lang.get('block.'+ns+'.'+k,i))
def ingredients(j):
 values=[]
 if 'pattern' in j:
  count=collections.Counter(''.join(j['pattern']))
  for k,v in j['key'].items():values.append((v,count[k]))
 else:values=[(v,v.get('count',1)) for v in j.get('ingredients',[]) if isinstance(v,dict)]
 merged=collections.OrderedDict()
 for v,n in values:
  key=v.get('item','#'+v.get('tag','?'));merged[key]=merged.get(key,0)+n
 return '、'.join((name(i)+f' ×{n} (`{i}`)' if not i.startswith('#') else i+f' ×{n}') for i,n in merged.items())
def run():
 s=P.read_text('utf8');rows=[]
 java=(R/'src/main/java/com/tnc/tnc/alchemy/Elixir.java').read_text('utf8')
 for m in re.finditer(r'\w+\("([^"]+)","([^"]+)",0x[0-9a-f]+,"([^"]+)",(\d+),(\d+),"([^"]+)","([^"]+)","([^"]+)"\)',java):
  id,title,help,sec,ticks,*ing=m.groups();out='soothing_bath_salt' if id=='bath_salt' else id+'_elixir' if id in ['overdraft','gigantism','miniature','flame_twist','fission','capture_shrink'] else 'elixir_'+id
  rows.append(f"| {title} `tnc:{out}` | {'、'.join(name(i)+' `'+i+'`' for i in ing)} | {help} | {int(ticks)/20:g}秒 |")
 start=s.index('| 魔药 / 物品ID');end=s.index('## 7.',start);s=s[:start]+'| 魔药 / 物品ID | 三味材料 | 基础效果 | 融合基准 |\n| --- | --- | --- | --- |\n'+'\n'.join(rows)+'\n\n'+s[end:]
 recipes=[]
 for p in (A/'data/tnc/recipes').rglob('*.json'):
  j=json.loads(p.read_text('utf8'));v=j.get('result',{});id=v.get('item') if isinstance(v,dict) else v
  if id:recipes.append((id,j))
 rows=[];java=(R/'src/main/java/com/tnc/tnc/life/routes/RouteKind.java').read_text('utf8')
 for m in re.finditer(r'\w+\("([^"]+)","([^"]+)",(\d+),(\d+)\)',java):
  id,title,cap,rate=m.groups();descs=[]
  for key,j in recipes:
   if key!='tnc:'+id:continue
   desc=('锻炉 '+str(j.get('mana',0))+'M / '+str(j.get('work_ticks',0))+'刻' if j['type']=='tnc:mana_forging' else '工作台')+'：'+ingredients(j)
   if desc not in descs:descs.append(desc)
  rows.append(f"| {title} | `tnc:{id}` | {int(cap):,} | {rate} | {'；'.join(descs) or '特殊来源/高维交易，见上文'} |")
 start=s.index('| 设备 | ID |');end=s.index('原有锯台',start);s=s[:start]+'| 设备 | ID | 储量M | 接口M/s | 合成途径及材料 |\n| --- | --- | --- | --- | --- |\n'+'\n'.join(rows)+'\n\n'+s[end:];P.write_text(s,'utf8');print('current manual',len(rows),'devices')
if __name__=='__main__':run()
