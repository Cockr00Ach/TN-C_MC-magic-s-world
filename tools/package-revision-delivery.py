"""Collect only verified revision artifacts; never copies launcher credentials or saves."""
from pathlib import Path
import json,hashlib,shutil,csv,sys,xml.etree.ElementTree as ET,zipfile
R=Path(__file__).resolve().parents[1];W=R/'work/revision2';O=Path(sys.argv[1]);O.mkdir(parents=True,exist_ok=True)
live=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
work=R/'modpack/元素觉醒1.4.3-魔改版-20260915'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest().upper()
jar=R/'build/libs/tnc-1.0.0.jar';digest=sha(jar)
assert digest==sha(work/'mods/tnc-1.0.0.jar')==sha(live/'mods/tnc-1.0.0.jar')
assert 'All 104 required tests passed' in (W/'tests-delivery.log').read_text(encoding='utf-8-sig',errors='replace')
assert 'BUILD SUCCESSFUL' in (W/'build-delivery.log').read_text(encoding='utf-8-sig',errors='replace')
assert 'mod jar verification passed.' in (W/'jar-verify.log').read_text(encoding='utf-8-sig',errors='replace')
tests=failures=errors=0
for p in (R/'build/test-results/test').glob('TEST-*.xml'):
 t=ET.parse(p).getroot();tests+=int(t.attrib['tests']);failures+=int(t.attrib['failures']);errors+=int(t.attrib['errors'])
assert tests==89 and failures==errors==0
originals=list(csv.DictReader((W/'original-chapters-before.csv').open(encoding='utf-8-sig')))
for row in originals:assert sha(live/'config/ftbquests/quests/chapters'/row['Name'])==row['SHA256']
with zipfile.ZipFile(jar) as z:
 names=set(z.namelist())
 for e in ['water','fire','lightning','wind','earth','light','dark']:
  for tier in range(1,6):
   assert f'assets/tnc/models/item/{e}_wand_{tier}.json' in names
   assert f'assets/tnc/textures/item/wands/{e}_wand_{tier}.png' in names
 for role in ['supply','food','hunt']:assert f'data/bountiful/bounty_decrees/tnc/tnc_{role}.json' in names
chapters=[json.loads(p.read_text(encoding='utf-8-sig')) for p in (R/'questbook/ftbquests/chapters').glob('tnc_*.snbt')]
for c in chapters:
 p=R/'questbook/ftbquests/chapters'/(c['filename']+'.snbt')
 for pack in [work,live]:assert sha(p)==sha(pack/'config/ftbquests/quests/chapters'/p.name)
copies={W/'七系法器总览.png':'七系法器总览.png',W/'任务书七系排版预览.png':'任务书七系排版预览.png',W/'town-map/天空岛建筑编号.png':'天空岛建筑编号.png',W/'town-map/建筑用途提案.csv':'建筑用途提案.csv',W/'town-map/建筑编号.json':'建筑编号.json',R/'src/main/resources/assets/tnc/textures/guide/gui_world_title.png':'世界页-歸.png',R/'docs/归航银行-借贷与房屋分期设计.md':'归航银行-借贷与房屋分期设计.md',R/'docs/任务书排版与建筑地图修改说明.md':'任务书排版与建筑地图修改说明.md'}
for src,dest in copies.items():shutil.copy2(src,O/dest)
install_logs='\n'.join((W/p).read_text(encoding='utf-8-sig') for p in ['install-work.log','install-live.log'])
proof=f'JUnit: {tests} passed, 0 failures, 0 errors\nForge GameTests: 104 passed, actual Bountiful 6.0.4 + Kambrik 6.1.1\nMagic stone startup diagnostics: 30/30\nProduction build: SUCCESSFUL\nJar verifier: PASSED\nSHA256: {digest}\nOriginal FTB chapters unchanged: {len(originals)}\n\n'+install_logs
(O/'续作验证与安装结果.txt').write_text(proof,encoding='utf-8')
backups=[]
for log in install_logs.splitlines():
 if 'Backup: ' in log:
  folder=Path(log.split('Backup: ',1)[1]);backups.append(str(folder));shutil.copy2(folder/'manifest.json',O/('续作工作安装manifest.json' if len(backups)==1 else '续作正式安装manifest.json'))
report=f'''# 续作交付 · 2026-09-29

本轮玩法、资源、任务书和安装已完成。原有剧情与另一边最新雷法提交一并保留。贷款/房屋分期完成设计，尚未上线交易；娱乐规则仍由作者后续设计。

## 已实现

- 17个TN-C原生FTB章节：10个指南/图鉴、7个玩法；191个真实行为/物品目标，41个建筑图鉴阅读节点。保留原19个玩法目标ID。
- 首页顶部繁体“歸”，正文世界观；主线第一页找到天空岛→酒馆找Self→制杖、铁匠、银行、茶灯会馆四路。
- Self默认位置升至v8，改为41号酒馆上楼北侧。已有存档下一次运行时加载原位置、迁移原实体并保留UUID；地图整修完成后执行。首次交谈提供4木棍、2铜锭，背包不足可重试；原剧情条件、动作与任务推进保持。
- 29号潮生制杖屋莉娅制作水/火/雷/风/土/光/暗七系五阶，共35件独立外观实际物品。一级法阵至五级神杖；首次一阶免人工费。材料、Lv门槛、成品身份、满背包重领均已接入。
- 28号炉石铁匠铺铎恩独立接粗铁+煤炭订单，制作铁盔、铁甲、护腿、铁靴、铁剑；首件基础装备另行免人工费。两家共用单张个人提货凭证；必须先回原商家领取再下单。
- 41号酒馆实际使用Bountiful原生委托栏，三池62种目标：补给28、食材22、巡猎12。原生纸张负责期限、任务物资消耗和消失；TN-C负责个人铜币、经验与声望到账。一次原生纸张多条报酬仍只计一次完成，重复点击不重复付款。酒馆原生完成数包括在该栏交付的外地原生纸张；只有TN-C报酬命令支付TN-C个人账户。
- 艾琳负责协会登记和晋升；Self介绍路与原剧情，其他业务各有岗位。
- 19号归航银行米洛提供实体铜/银/金币存取与500铜全款购房。银行与钱袋共用个人余额。低视距会临时加载房源，先预检再扣款；结构改动与箱内物品会阻止出售并保留现场。
- 34号茶灯会馆有真实到访任务；没有下注、打牌或收益玩法。40号仍是唯一正式房源，其他住宅编号只是候选。

## 怎么拿到法杖

正式游玩用生存模式。到41号酒馆上楼北侧与Self交谈并腾出背包；找艾琳登记。按任务书地图去29号店右键莉娅，选元素与一阶，交4木棍、2铜锭。首次人工费为0，等待20秒有效冒险服务器时间后，在莉娅处领取。后续各阶Lv要求1/10/25/50/85，详情在工坊。手册或`/adventure`会显示本存档岗位动态坐标。

创造模式只测试外观时可用管理员命令，例如`/give @s tnc:fire_wand_3`；这不经过实际下单与任务流程。七系ID前缀为water、fire、lightning、wind、earth、light、dark，阶位1～5。旧万能基础杖保留兼容，新订单不会再制作旧万能杖。光系普通战斗法术尚未开放，光杖当前可承载通用术。

## 验证与安装

JUnit {tests}项通过，Forge真实功能测试104项通过，自检30/30，生产构建与jar核验通过。覆盖35种真实订单、原生Bountiful生成/多报酬交付/重复交付/错误栏与未登记保护、银行存取守恒与满包、错商家领取、Self原实体UUID迁移/重复补位/原剧情字段与材料赠送、房源临时加载及严格预检。

正式PCL实例和仓库工作整合包的jar、17章任务书均已安装并核对SHA256；原版现存{len(originals)}个任务章节哈希保持一致。没有编辑用户存档或替换第三方jar。

jar SHA256：`{digest}`。

工作备份：`{backups[0]}`。正式实例备份：`{backups[1]}`。

正式整合包已使用现有PCL启动配置完成客户端烟测，纹理图集与声音初始化成功，窗口已创建；没有打开用户世界。启动证据单独保留，没有复制启动认证参数。

任务书排版图是依据实际节点坐标绘制的预览，建筑图来自源NBT与当前景观覆盖层。尚未逐页完成游戏内视觉验收；没有把预览称为游戏截图。服务NPC当前使用独立岗位村民外观，可后续替换作者人物模型。41组编号包含建筑/入口分区，18和20是同一训练场两门。

## 可继续细调

按建筑编号调整NPC/玩家房源用途；按预览调整任务书间距、字体和地图节点。贷款与分期见“归航银行-借贷与房屋分期设计.md”，有首付150、5期×74、总付520的具体方案与账本恢复要求，当前没有债务或自动扣息。娱乐后续规则由作者决定。
'''
(R/'docs/续作完成报告-20260929.md').write_text(report,encoding='utf-8');(O/'续作完成报告-20260929.md').write_text(report,encoding='utf-8')
print(f'Collected verified revision delivery: {tests} unit tests / 104 GameTests / {len(chapters)} chapters / 35 wands.')
