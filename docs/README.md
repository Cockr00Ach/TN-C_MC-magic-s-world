# `docs/` 索引 — 先看这一页

> **维护者**：**动画系统MMM**（agent `dsh` · 会话主题「动画系统MMM」）· **建立** 2026-10-06 · **第二次整理** 2026-10-20
> **归属**：本索引由**动画系统MMM** 维护 ✓。**各文档本身仍归各自的维护者**（见每份文档开头的署名头）✗ 改内容前先看那行。
> **为什么有这页**：`docs/` **一级目录只放入口文档**（本页共 8 个文件），其余一律按主题进子目录。
> **怎么搬**：跑 `python tools/reorganize_docs.py --apply` ✓ —— 映射和移动历史都在那个脚本的 `MOVES` 表里，
> 别手工 `Move-Item` ✗（搬完还要跑 `check_doc_links.py` 并修引用）。

---

## 一、七个入口文档（想知道"现在什么状态"只看这几份）

| 文档 | 管什么 | 维护者 |
|---|---|---|
| **[`当前状态.md`](当前状态.md)** | **技术唯一事实来源**（"现在做到哪了"）| 法术系统YYY |
| **[`../PROJECT-STATE.md`](../PROJECT-STATE.md)** | 工程总览 / 环境 / 核心技术知识库 | 法术系统YYY |
| **[`法术专题_交接.md`](法术专题_交接.md)** | 法术体系入口（工作流 / 内容 / 配方 / 坑）| 法术系统YYY |
| **[`任务系统_交接.md`](任务系统_交接.md)** | FTB 任务实施侧作业手册 | 任务系统BBB |
| **[`动画专题_交接.md`](动画专题_交接.md)** | 剧情演出（台词 `@act` → NPC 动作）| 动画系统MMM |
| **[`整包jar更新_交接.md`](整包jar更新_交接.md)** | **把各专题的成果装进游戏**（编译 → 闸门 → 装机 → **实机日志/崩报复核**）| 整包 jar 更新 |
| **[`地图接入进度.md`](地图接入进度.md)** | 天空岛 / 主城 / 结构生成进度 | 地图专题 |

另外两份日常用：

| 文档 | 用途 |
|---|---|
| [`daily-workflow.md`](daily-workflow.md) | **每天开工前看**：一次性设置、日常命令、提交推送、排错对照表 |
| [`../剧情总纲.md`](../剧情总纲.md) | **剧情唯一事实来源**（世界观 / 主角 / 五阶 / 命名规范）· 编剧系统 AAA |

---

## 二、按主题找（`docs/` 一级目录只有下面这 8 个文件）

| 一级文件 | 是什么 |
|---|---|
| `README.md` | 本页 |
| `daily-workflow.md` | 日常命令速查 |
| `当前状态.md` | 技术唯一事实来源 |
| `法术专题_交接.md` / `任务系统_交接.md` / `动画专题_交接.md` / `整包jar更新_交接.md` | 四个专题的入口 |
| `地图接入进度.md` | 地图专题入口 |

**其余全部在主题目录里**，按下表找：

| 目录 | 管什么 | 篇数 | 先看这份 |
|---|---|---|---|
| [`spells/`](spells/) | 法术：六系链、投射物模型、暗手、风系、魔法石界面、图标 | 60 | [`法术总表_按设计文档.md`](spells/法术总表_按设计文档.md) |
| [`boss/`](boss/) | 巨兽 Boss：磐岳（岩枪古像）、暗系巨兽人、地核战场 | 4 | 最新一篇：[`earth_arena_recovery_20261019.md`](boss/earth_arena_recovery_20261019.md) |
| [`dungeon/`](dungeon/) | **地下城小怪 17 只**：模型/动画/攻击方式/刷怪表接入 | 1 | [`地下城小怪图鉴.md`](dungeon/地下城小怪图鉴.md) |
| [`ink/`](ink/) | 水墨系：墨鹤、墨龙（战斗/飞行/地面观察） | 4 | 最新一篇：[`ink-dragon-ground-observation-v4.md`](ink/ink-dragon-ground-observation-v4.md) |
| [`life/`](life/) | 四条生活路线：四时农圃 / 魔植花园 / 异兽牧场 / 魔导工坊，含魔力视与晶纤 | 7 | [`四条生活路线实装与测试说明-20261007.md`](life/四条生活路线实装与测试说明-20261007.md) |
| [`quests/`](quests/) | FTB 任务页设计、排版、银行/装备页、任务书恢复 | 10 | [`玩法指南交付-20260929.md`](quests/玩法指南交付-20260929.md) |
| [`tavern/`](tavern/) | 酒馆装修、常客、音乐、HUD 恢复 | 4 | [`装修酒馆与常客音乐实装记录-20261004.md`](tavern/装修酒馆与常客音乐实装记录-20261004.md) |
| [`ecology/`](ecology/) | 生态与异兽：植物/动物品种、牧养、生态美术 | 10 | [`异兽牧养完整系统设计-20261003.md`](ecology/异兽牧养完整系统设计-20261003.md) |
| [`town/`](town/) | RouchNao 小镇、赫萝斯堪德、天空岛内饰、进度清单 | 14 | [`RouchNao完整玩法蓝图-20261003.md`](town/RouchNao完整玩法蓝图-20261003.md) |
| [`worldmap/`](worldmap/) | 主世界首领总表、遗迹/传送阵、建筑导入与生成 | 7 | [`主世界首领总表.md`](worldmap/主世界首领总表.md) |
| [`casino/`](casino/) | 赌场五台机器的设计与调参 | 6 | [`赌场机器_交接.md`](casino/赌场机器_交接.md) |
| [`art/`](art/) | 美术资产清单、金币/法杖模型与贴图来源 | 4 | [`美术资产清单.md`](art/美术资产清单.md) |
| [`ui/`](ui/) | 双部位装备、法杖朝向与独立柜台 | 3 | [`正手法杖与独立柜台修订4-20260930.md`](ui/正手法杖与独立柜台修订4-20260930.md) |
| [`tools/`](tools/) | 产线配方：NPC 动作、Blockbench 编辑、闸门问题 | 3 | [`NPC动作产线_Blockbench配方.md`](tools/NPC动作产线_Blockbench配方.md) |
| [`perf/`](perf/) | **启动/加载/卡顿排查**、Conquest 换官方版、`spellcloud` 启动崩溃 | 4 ＋1 patch | [`启动卡顿排查-20261010.md`](perf/启动卡顿排查-20261010.md) |
| [`validation/`](validation/) | 实机验收记录（魔力视覆盖、天穹镜面粒子） | 2 | —— |
| [`previews/`](previews/) | **所有配图**：物品预览、模型预览、实机截图、动画条 | 156 | —— |
| [`concept-art/`](concept-art/) · [`概念图/`](概念图/) | 早期概念图 | 2 | —— |
| [`superpowers/`](superpowers/) | 插件（superpowers）自有的 `specs/` + `plans/`，**按插件约定保留** | 50 | —— |
| [`archive/`](archive/) | **过时的状态快照**（`实施进度.md` / `项目交接.md` / `验收表.md`）。**别拿它们当现状** ✗ | 3 | —— |
| [`misc/`](misc/) | 没有主题的一次性记录 ＋ **自动生成的清点表** | 7 | —— |

> **找一篇具体文档**：`docs/misc/_doc-inventory.md` 是 `tools/audit_docs.py` 自动生成的**全量清点**
> （活文档 / 带日期记录 / 被引用次数），想知道"某篇在不在、被谁引用"就查它 ✓。

---

## 三、整理规则（以后新增文档照此办）

1. **一级目录只放"入口文档"** ✗ 别往 `docs/` 顶层堆文件 —— 顶层 = 本页 ＋ `daily-workflow.md` ＋ 四份专题交接 ＋ 两份事实来源；
2. **活文档**（有维护者署名头 + 文件名不带日期）放它自己的主题目录，**路径尽量别再动** ✓；
3. **一次性记录**（修了什么、验收了什么）→ **文件名带 `YYYYMMDD`**，放对应主题目录；没有主题就放 `misc/`；
4. **配图一律进 [`previews/`](previews/)** ✓（旧规则"物品预览 PNG 留在一级目录别动"已于 2026-10-20 废止 ✗）；
5. **新主题就新开目录** —— 2026-10-20 这轮新增了 `boss/`、`ink/`、`life/`、`perf/` ✓；
6. **移动统一走 `tools/reorganize_docs.py`**（`git mv` + 映射表），搬完跑 `check_doc_links.py`；
   **按路径读文档的工具也要一起改** ✗（这轮动了 `generate_life_routes.py`、`generate_route_chapters.py`、
   `gen-main-atlas.py`、`gen-town-progression.py`、`audit_docs.py`）；
7. 改别人维护的文档**先打招呼**（见仓库根 `README.md` 的「多专题协作约定」）。

---

## 四、自查工具

| 工具 | 用途 |
|---|---|
| `python tools/audit_docs.py --md-out docs/misc/_doc-inventory.md` | 清点文档（活文档 / 日期快照 / 被引用次数）|
| `python tools/check_doc_links.py` | 找**断掉的可点击链接**（整理完必跑）|
| `python tools/reorganize_docs.py --dry-run` | 查看整理映射与移动历史（幂等，已搬过的会标 done）|
| `python tools/reorganize_docs.py --apply` | 按映射执行 `git mv` |
| `node tools/casino-machines.mjs` | **赌场机器的几何/动画自检**（`--view` / `--parts` / `--anim`）—— 那两台机器没有 json 模型可看 ✗，这是替代品 ✓ |

---

## 五、2026-10-20 第二次整理记录

上一轮（2026-10-06）把顶层从 ~95 个收到十几个；之后 14 天的新内容又让顶层涨回 **46 个文件**。这轮：

| 动作 | 数量 | 说明 |
|---|---|---|
| `docs/` 顶层文件 | **46 → 8** | 只剩入口文档 |
| 新增主题目录 | 4 | `boss/`（4）· `ink/`（4）· `life/`（7）· `perf/`（4＋patch）|
| 归入既有主题目录 | 24 | `spells/` ＋9 · `previews/` ＋6 · `quests/` ＋1 · `town/` ＋1 · `misc/` ＋1 |
| 引用修正 | 31 处 | 17 篇文档/脚本/模型源里的旧路径（含 4 个按路径读文档的工具）|
| 链接自检 | `check_doc_links.py` 106 | 整理前 107，**没有新增断链** ✓ |

其中 6 张配图（残卷/正史/导图/庄鹊让×2/魔法阵预览）原来是"留在一级目录、不要移动"，
这轮统一挪进 `previews/` 并改掉了引用；对应规则已在上方 §三.4 改写 ✓。

---

## 六、2026-10-20 地下城小怪（新增主题目录 `dungeon/`）

同日第二件事：作者要求"修改已有地下城小怪 + 做几个新的，各配动画与攻击方式"。
调查结论与交付：

| 事项 | 结果 |
|---|---|
| 地下城本体 | Dungeon-Realm（jar 内嵌 Library of Exile 框架，557 条 `library_of_exile/structures`）|
| **已有小怪能不能改动画** | **不能** —— 全是第三方 jar 编译死的实体（eeeabsmobs / blackgoldalliance / jerotesvillage / torchesbecomesunlight / 原版）|
| 能改什么 | 刷怪表 `library_of_exile_mob_list/*.json`（权重/名册）、词缀 `*_mob_affix/*.json`、掉落表 |
| 交付 | **17 只自研小怪**（模型 + 85 段动画 + 17 张手绘贴图 + 14 种攻击方式）+ 8 张刷怪表改造 |
| 新增目录 | `dungeon/`（1 篇：`地下城小怪图鉴.md`）|
| 新增工具 | `tools/gen_dungeon_mobs.py` · `tools/check_dungeon_mob_assets.py` · `tools/apply_dungeon_mob_lists.py` |
| 验证 | `compileJava` 通过 · `DungeonMobAssetsTest` 6 项全绿 · 资产校验 12/12 · **实机未验证** |
