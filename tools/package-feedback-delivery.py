"""Verify the installed revision and collect its actual evidence and previews."""
from pathlib import Path
import hashlib, json, shutil, xml.etree.ElementTree as ET, zipfile
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'work/revision3'
OUT=Path('C:/Users/宋志坤/Documents/Codex/2026-09-29/c-users-documents-codex-2026-09/outputs')
OUT.mkdir(parents=True,exist_ok=True)
LIVE=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
PACK=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
jar=ROOT/'build/libs/tnc-1.0.0.jar'; digest=sha(jar)
tests=failures=errors=0
for file in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    suite=ET.parse(file).getroot();tests+=int(suite.attrib['tests']);failures+=int(suite.attrib['failures']);errors+=int(suite.attrib['errors'])
assert tests==89 and failures==0 and errors==0
assert 'BUILD SUCCESSFUL' in (ROOT/'work/revision3-build.log').read_text(encoding='utf-8-sig')
assert 'mod jar verification passed.' in (WORK/'jar-verify.log').read_text(encoding='utf-8-sig')
chapters=list((ROOT/'questbook/ftbquests/chapters').glob('tnc_*.snbt'))
assert len(chapters)==17
for pack in [PACK,LIVE]:
    assert sha(pack/'mods/tnc-1.0.0.jar')==digest
    for chapter in chapters:assert sha(pack/'config/ftbquests/quests/chapters'/chapter.name)==sha(chapter)
    for name in ['e.snbt','2032E61CAD845DDF.snbt']:assert not (pack/'config/ftbquests/quests/chapters'/name).exists()
with zipfile.ZipFile(jar) as archive:
    for folder in ['models/item','textures/item/wands']:
        for file in (ROOT/'src/main/resources/assets/tnc'/folder).glob('*wand*'):
            assert archive.read('assets/tnc/'+folder+'/'+file.name)==file.read_bytes()
    for name in ['town_atlas','water_focus_1']:
        relative='assets/tnc/textures/guide/'+name+'.png'
        assert archive.read(relative)==(ROOT/'src/main/resources'/relative).read_bytes()
native=json.loads((WORK/'remaining-native-chapters-before.json').read_text(encoding='utf-8-sig'))
for entry in native:assert sha(LIVE/'config/ftbquests/quests/chapters'/entry['Name'])==entry['SHA256']
backups=[]
for name in ['install-work','install-live']:
    log=(WORK/(name+'.log')).read_text(encoding='utf-8-sig')
    assert 'Installed ' in log
    folder=Path(next(line.split('Backup: ',1)[1] for line in log.splitlines() if 'Backup: ' in line))
    backups.append(str(folder))
    shutil.copy2(folder/'manifest.json',OUT/('修订3-'+name+'-manifest.json'))
    removed=folder/'removed-chapters.json'
    if removed.exists():shutil.copy2(removed,OUT/('修订3-'+name+'-removed.json'))
for name in ['地图页修订预览.png','歸人物页修订预览.png','法杖页修订预览.png','七系法杖修订总览.png','七系冒险者法杖.png']:
    shutil.copy2(WORK/name,OUT/name)
shutil.copy2(ROOT/'work/revision2/任务书七系排版预览.png',OUT/'七系任务路线修订预览.png')
shutil.copy2(ROOT/'docs/town/天空岛内饰交付给建造者.md',OUT/'天空岛内饰交付给建造者.md')
report=f'''# 任务书与法杖修订3 · 2026-09-29批次

完成与安装日期：2026-09-30。

本轮已完成并安装用户提出的第1—6项修改。内饰由作者或建造者在装修存档中制作，收到成品后再导出并接入天空岛模板。

- 正式实例的作者“注意事项”“鸣谢名单”两章已移出任务书，并保留备份。其他 {len(native)} 个原有章节哈希保持一致。
- “歸”页保留上方繁体字，中央介绍归·吴归衡；下方三列介绍熙永槐、庄鹊让、周坐望、公孙衍、Self及城镇接待与匠人。原有世界概念说明保留在后方节点。
- 城镇图鉴采用作者提供的1173×1022原始地图截图，未修改底图；41组建筑用途、入口地址保留。节点由0.55放大到0.95，银行、铁匠、制杖、娱乐、住宅、酒馆使用各自功能图标。
- 一级正式命名“冒险者法杖”。七系一级全部重画为有完整握柄的法杖；水、火、雷、风、土、光、暗分别采用漂木水晶、焦木火尖、铜色雷叉、浅木羽叶、厚木土石、象牙日珠、黑荆紫晶。其余28件沿用上一轮认可的独立造型。
- 法杖阅读页的五阶插图与节点对齐；七系提升路线统一七列，缩短行列间距，保留全部旧目标、编号与依赖。
- 全部35件法杖添加第一、第三人称手持变换，尺寸随阶位放大为1.3/1.55/1.7/1.85/2.0，并修正左右手镜像。背包图标沿用原有显示尺度。

## 验证与安装

Java17离线生产构建成功；本轮JUnit {tests}项通过，0失败、0错误；595个任务/章节/目标编号唯一，独立复核通过。jar核验通过，已核对工作整合包与PCL实例的jar、17章任务书和相关资源。

本轮没有重新运行Forge GameTests或打开用户存档。配图为根据实际配置生成的预览，不能当作游戏截图。第一/第三人称实际握持效果和任务书逐页画面仍需进游戏观察；自动验证已完成。

jar SHA256：`{digest}`。

工作备份：`{backups[0]}`。

正式实例备份：`{backups[1]}`。

## 内饰怎么交付

用当前整合包生成专用装修存档，等待天空岛及地景全部生成。备份后把完整存档和同版整合包给建造者；对方创造模式装修，保存退出后将完整世界文件夹压缩返还，附建筑编号或坐标、移动过的NPC/委托栏位置及截图。必须包含level.dat、data、region等原有世界文件，不能只交截图。

开发者收到成品后按岛原点导出建筑、家具与方块实体，额外处理删除方块和装饰实体，接回NPC、委托栏以及地景层，再用两个新种子测试。之后新生成的天空岛使用新模板。已有世界另外做保留玩家改建的迁移，不整岛覆盖。详细步骤见《天空岛内饰交付给建造者.md》。

银行贷款/房屋分期和娱乐玩法仍处于上一轮所列设计状态，本次没有新增交易规则。
'''
(ROOT/'docs/quests/任务书与法杖修订3-20260929.md').write_text(report,encoding='utf-8')
(OUT/'任务书与法杖修订3-20260929.md').write_text(report,encoding='utf-8')
print(f'Verified revision3: {tests} tests, 17 chapters, {len(native)} untouched native chapters, installed SHA256 {digest}')
