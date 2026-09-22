# TN-C（元素觉醒 · 魔法系统改造）

Minecraft 1.20.1 / Forge 47.4.22 的魔改工程。给整合包 **元素觉醒 1.4.3** 加一套自研的
**魔法石 + 法杖** 体系，取代原来"卷轴 + 法术注册台"那一套。

当前阶段：**六系 108 个法术已可用**（雷/火/风各 3 条链、水/土/暗各 4 条链），
魔法石界面 + 法杖施法已跑通，魔力值真正参与施法（施法前硬拦截，不是事后补扣）。
**原作者的任务内容已全部清空** ✅（2026-09-21，范围 B）：工作区与游戏实例的 `chapters\` 均为空，
FTB 任务界面**没有任何残留**；玩家可见的任务将**只有我们写的**。
剧情文本以 `剧情总纲.md` 为准，任务正文由 **任务系统BBB** 实施。

### 📢 待办派单（2026-09-21 · 编剧系统 AAA 提出 → 交 任务系统BBB 执行）✅ 已执行完毕

> **用户决定：原作者的任务内容彻底删干净（范围 B）。** 流程记录：编剧系统 AAA 先查证并请示 →
> 用户选定「6 章图鉴类 + `archive/author-quest-content/` 归档 **全删**」且指定「由任务系统BBB 动手」。
> 编剧系统 AAA 只产文本、不实现，故在此派单 ✓。
> **执行结果**：5 条全部完成 ✅ —— 见下方"执行记录"。

| # | 动作 | 目标 | 备注 |
|---|---|---|---|
| 1 | 删工作区 6 章 | `modpack\<包>\config\ftbquests\quests\chapters\` 下 `3061DAB8FCCB715B`(食物列表统计) `boss`(Boss分类) `37C8437D5B9D0F18`(冠军词条) `500552A3129FF14C`(驯龙指南) `2032E61CAD845DDF`(鸣谢名单) `e`(注意事项) | 删后 FTB 任务界面应**为空** |
| 2 | 删游戏实例同样 6 章 | `E:\...\versions\元素觉醒1.4.3-魔改版-20260915\config\ftbquests\quests\chapters\` | ⚠️ `config/` 不在 sync 范围，**必须手删**，否则游戏里还能看见 |
| 3 | 删整个归档 | `archive/author-quest-content/`（14 文件，已进 git）| ~~🔴 **不可逆**：原始 zip 内 0 个 ftbquests 章节，别处再无副本~~ → **见下方更正 ✗** |
| 4 | 补 `.gitignore` 例外 | 撤销 `!archive/author-quest-content/` 这条唯一例外 | 归档没了，例外就是死规则 |
| 5 | 收尾核对 | 分组文件、悬空引用 | 4 个分组里若 `世界地图`/`挑战目录`/`休闲分类` 因此变空，一并处理；检查有无指向已删章节的 NPC 绑定 |

> #### ✗ 第 3 条前提错误 —— 已由任务系统BBB 实测更正（2026-09-21）
>
> 原文写「原始 zip 内 **0 个** ftbquests 章节 …… 别处再无副本」，**与事实相反**。
> 实测（`tar -tf` 原始包 + 逐文件 MD5 比对）：
>
> - `元素觉醒1.4.3-魔改版-20260915.zip`（1.6 GB / **8399 条目**，本地保留、未删）里
>   **完整带着 `config/ftbquests/quests/`**：全部 **16 个章节** + `chapter_groups.snbt` +
>   `data.snbt` + 3 张 `reward_tables` + `config/whisperingquests/ftbq_bindings.json` + `config/p1nero_dl-client.toml`。
> - 我们把**被删的全部 16 章**逐一从 zip 解出与手上副本比对：**16/16 哈希一致，0 失败**。
> - 结论：**删这份归档并不是"不可逆"** —— 随时能从本地 zip 重新解出来，信息不会丢。
>
> 只是因为用户已确认按范围 B 全删，第 3 条**照原样执行**了 ✓。
> （`Medieval Town.rar` 那条线索无关：它是存档，里面没有任务章节 ✓）

> #### ✅ 执行记录（任务系统BBB，2026-09-21）
>
> | # | 结果 |
> |---|---|
> | 1 | 工作区 `chapters\` **0 章** ✓ |
> | 2 | 游戏实例 `chapters\` **0 章** ✓（手删，`sync` 不负责删）|
> | 3 | 归档已删 ✓（删前已完成 16/16 可还原性验证，见上）|
> | 4 | `.gitignore` 例外已撤销 ✓ |
> | 5 | `chapter_groups.snbt` → `chapter_groups: [ ]`；`ftbq_bindings.json` → `bindings: {}`（原剩 3 条所绑任务全在已删章节里）；两文件已 `sync push` 到实例并**哈希核对一致** ✓；两个存档的 `ftbquests` 进度已清 ✓ |
>
> **验收**：工作区与游戏实例的 `chapters\` 目录**均为空** ✓

> 📌 **每天开工前先看 [`docs/daily-workflow.md`](docs/daily-workflow.md)** ——
> 里面有一次性设置、日常命令、提交推送流程、以及"出问题对照表"，
> 可以直接复制粘贴，也可以打印出来贴屏幕上。
>
> 📌 **现在做到哪了** → [`docs/当前状态.md`](docs/当前状态.md)（技术唯一事实来源，法术系统YYY 维护）。
> 📌 **故事是什么、任务怎么写** → [`剧情总纲.md`](剧情总纲.md)（剧情唯一事实来源，**编剧系统 AAA** 维护）。
> 📌 **任务系统的坑与流程** → [`docs/任务系统_交接.md`](docs/任务系统_交接.md)（**任务系统BBB** 维护）。
> 　　各文档首行都写明**维护者与归属**，改别人负责的文件前先打招呼。

---

## 一、这个仓库里有什么 / 没有什么

| 在仓库里（我们的成果） | 不在仓库里（第三方/产物） |
|---|---|
| `src/` Java mod 源码 | `modpack/*/mods/` —— 280 个第三方 mod jar |
| `design/` 设计文档 | `modpack/*/config/` 绝大部分（45 MB，整合包自带） |
| `tools/` 工具脚本 | `modpack/*/tlm_custom_pack/`（37 MB 女仆模型包） |
| `PROJECT-STATE.md` 交接文档 | `build/`、`.gradle/`、`run/` 构建与运行产物 |
| `modpack/*/kubejs/data/tnc/` 我们写的法术 | `libs/*.jar`（第三方 remap jar，用脚本取） |
| `modpack/*/config/openloader/resources/TN-C/` 法术图标 | |

**所以：仓库 + 你自己那份整合包 = 完整环境。** 不需要互相传 mod 文件。

> ⚠️ **绝对不要**把 `mods/` 里的 jar 提交进来 —— 那是别人的版权内容，而且会让仓库暴涨到 GB 级。
> `.gitignore` 已经挡住了，但 `git add -f` 能强行绕过，别那么干。

---

## 二、第一次跑起来

前置：**JDK 17**、一份整合包（放在任何位置）、PowerShell。

```powershell
git clone <仓库地址>            # 克隆到任意路径都行，脚本不再假设 D:\ModTest
cd <克隆目录>

# 1) 从本地整合包里取回编译所需的两个第三方 jar（它们是 compileOnly，不在 git 里）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1
#    如果脚本找不到整合包，就显式指路：
#    ... -File tools\fetch-libs.ps1 -PackDir "X:\...\<整合包>\mods\.connector"

# 2) ★ 开启「提交守卫」（每个 clone 只需一次）
git config core.hooksPath tools/git-hooks

# 3) 编译
.\gradlew.bat build

# 4) 校验产物（会检查一堆"编译通过但运行时静默失效"的坑）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify_mod_jar.ps1
```

### ★ 提交守卫：防止把 mod / 大文件提交进去

**为什么需要它**：这仓库曾经在**第一次提交**时就被 `git add -A` 吞掉了根目录那个
**1547 MB 的整合包 zip**，`.git` 直接涨到 1545 MB。`.gitignore` 只在"有人记得写规则"时有用，
所以改成**机械拦截**：用 git 的 pre-commit 钩子，在提交发生前就拒绝。

开了之后，只要你提交里含下面任何一类，提交会**直接被拒**（并告诉你为什么、怎么撤销）：

| 拒绝的东西 | 原因 |
|---|---|
| `mods/*.jar` | 第三方 mod（版权 + 体积） |
| `*.zip` / `*.7z` / `*.rar` | 压缩包（整合包 zip 就 1.5 GB） |
| `libs/*.jar` | 第三方编译用 jar（由 `fetch-libs.ps1` 取） |
| `tlm_custom_pack/`、`.connector/` | 第三方内容 |
| `build/` `run/` `.gradle/` | 构建产物 / 世界存档 |
| `*.class` `.dll` `.so` | 编译二进制 |
| **任何 > 2 MB 的文件** | 兜底：大文件一律先问清楚 |

被拒了怎么处理：
```powershell
git reset HEAD <那个文件>     # 只是取消暂存，文件还在磁盘上
# 然后在 .gitignore 里加一条规则，别用 git add -f 硬塞
```
确实需要临时绕过：`git commit --no-verify`（**别养成习惯**）。

想手动查一遍暂存区（不提交也能跑）：
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-staged.ps1
# 正常输出类似： check-staged: 309 file(s), 1.55 MB - ok
```

> **健康基线：约 309 个文件 / 约 1.6 MB。** 每次 `git add -A` 后扫一眼文件数 ——
> 数量级不对就是有大件混进来了。

### 装进游戏并验证

```powershell
# 游戏必须完全关闭（脚本会拒绝在 java 运行时覆盖 jar）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-to-pack.ps1 -Wait

# 启动游戏 → 进一个世界（探针在世界加载时才跑）→ 然后：
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-pack-verdict.ps1
```

期望看到 `PASS  mana gate probe 6/6`。

---

## 三、怎么玩（游戏内）

需要作弊/OP（命令权限等级 2）。

```
/tnc points add 10              赠送魔法点数（调试用）
/tnc learn "tnc:spark"          解锁法术（会同步进法杖）
/tnc magic                      查看魔法石数据
/tnc spells                     看法术目录
/tnc wand                       同步/补发法杖
/tnc engine                     引擎接线状态 + 施法事件账本
/tnc gatetest                   硬拦截实测（三级阶梯）
/tnc cast "tnc:spark"           直接放一个法术（不依赖法杖/键位）
/tnc selftest                   数值/门槛/NBT 自检
```

**施法**：手拿**魔法法杖**，槽位 1 = 右键，槽位 2–5 = 数字键 2–5，**按住约 0.5 秒**。

三条硬性条件（缺一放不出来，会有红字提示）：**拿着法杖** + **已在魔法石解锁** + **魔力够**。

---

## 四、几个人一起改代码的约定

```powershell
git pull                     # 动手前先拉，别在旧版本上写
git checkout -b 你的分支名    # 一人一个分支，别都往 main 上直接写
# ...改代码...
.\gradlew.bat build          # 至少保证能编译
git add -A ; git commit -m "做了什么"
git push -u origin 你的分支名
# 然后在 Gitee 上发起合并请求（Pull Request），另一个人看过再合进 main
```

**最容易被抢改的文件，改之前先说一声**：

| 文件 | 为什么 |
|---|---|
| `PROJECT-STATE.md` | 两个人都在往里记东西，最容易冲突 |
| `modpack/*/kubejs/data/tnc/spells/*.json` | 同一批法术数值 |
| `magic/Config.java` / `SpellCatalog.java` | 数值表与法术目录 |

真冲突了不要慌：`git status` 会列出 `both modified` 的文件，打开会看到
`<<<<<<<` / `=======` / `>>>>>>>` 三段，留下你要的、删掉标记，再 `git add` 即可。
**拿不准就先 `git stash` 把自己没提交的改动收起来，或者直接问另一个人。**

---

## 五、更详细的背景

`PROJECT-STATE.md` 是这个工程的完整交接文档：**踩过的坑、引擎行为、数值设计、
以及每条结论是怎么验证出来的**。改代码前值得先扫一遍 ——
里面记录了好几个"编译通过但运行时静默失效"的陷阱（mixin / spell_assignments /
客户端-服务端同步），照着它走能省很多时间。

---

## 多专题协作约定

- **法术系统YYY**：docs/当前状态.md、docs/法术专题_交接.md、docs/投射物模型_配方.md、docs/法术制作与测试.md、docs/特殊魔法_设计.md、docs/法术总表_按设计文档.md、docs/美术资产清单.md、docs/实施进度.md、docs/项目交接.md、docs/验收表.md、docs/导图_全法术.png、docs/宝箱传说残卷_物品预览.png、docs/一周目正史_物品预览.png、docs/庄鹊让_候选模型对比.png、docs/庄鹊让_NPC皮肤预览.png、src/main/java/com/tnc/tnc/magic/**、src/main/java/com/tnc/tnc/client/**、src/main/resources/data/tnc/spells/**、src/main/resources/assets/tnc/**、tools/gen_tnc_*.ps1、tools/gen_scroll_lang.ps1、tools/scroll_lang_extra.json、tools/gen_records_lang.ps1、tools/records_lang_extra.json、tools/maid_models.json、tools/sync-tlm-models.ps1、tools/gen_zhuangquerang_skin.ps1、tools/verify_mod_jar.ps1、tools/install-to-pack.ps1。
  - **也负责**：剧情道具「宝箱传说残卷」六卷 ＋「一周目正史」五张记录纸（物品本体 + 右键阅读）、剧情角色 **庄鹊让**（一周目剧情 NPC `tnc:zhuangquerang` + 二周目可选角色设计）；**剧情文案本身**仍归编剧系统 AAA ✗。
  - **与 NPC 专题共用** `src/main/java/com/tnc/tnc/npc/**` ✓ —— 我按他们的范式**新增**了 `zhuangquerang` 一个 NPC 及该动的接线（实体/属性/刷怪蛋/渲染器/创造页/默认位置/purge 名单/lang）；**不重构、不提交**他们的 WIP ✗。
  - **也碰**：`build.gradle`（`compileOnly fg.deobf` + `flatDir` 仓库）与 `libs\touhoulittlemaid-1.5.2.jar`、`libs\geckolib-4.8.4.jar` —— **只为把女仆模型画在庄鹊让身上** ✓，两个 jar 不进我们的产物 ✗。
- **编剧系统 AAA**（agent dsh · 会话主题「文书」）：剧情唯一事实来源 `剧情总纲.md`。**只产文本，不实现** —— 新机制/新物品写「需求单」。
- **动画系统MMM**（agent dsh · 会话主题「动画系统MMM」，2026-09-22 建立）：`docs/动画专题_交接.md`（**本专题入口**）、`docs/NPC动作产线_Blockbench配方.md`（**动作怎么做的配方**）、`tools/gen_npc_bedrock.py`、`assets/tnc/geo/entity/*.geo.json`、`assets/tnc/textures/entity/*_bedrock.png`、`assets/tnc/animations/entity/*.animation.json`（**与 YYY 共用，见下**）。
  - **职责**：**表现剧情的动画** —— 剧本里的 `（…）` 动作提示要在游戏里**真的做出来**：NPC 动作（待机/点头/抬手/推/合簿/转身/走动）＋ 场景调度（谁、什么时候、做什么）＋ 过场镜头。
    上游是 `剧情\开场_分离之后.md` 附注点名的那**七处动作提示**（那杯酒／钱压杯下／推登记簿／合上登记簿／后屋声响／塞盾／回头看烟）。
  - **不碰**：法术/数值/投射物模型/法术图标与贴图（法术系统YYY）、剧情设定与文案（编剧系统 AAA）、任务正文（任务系统BBB）、地图与结构资产（地图专题）。
  - **与 YYY 的接口**：`tnc:zhuangquerang` 的实体/模型/贴图/渲染器与其 `animations/entity/*.json` 现归法术系统YYY ✗ —— 我**只读不动**；
    要加动作**先打招呼、请对方加**（照 `README` §四「最容易被抢改的文件，改之前先说一声」）✓，方向反过来同样成立：他们往现有动画里加东西也先知会我 ✓。
  - **边界（别做重）**：**玩家**自己的跑动/待机/施法全套动作 ✗ 与**法术施法动画**（法术 JSON 的 `animation` 字段 → spell_engine 的 `spell_animations`）✗ **都不在我这儿**，别当成我的活。
  - **也碰**（2026-09-22，**只读**）：`data/tnc/dialogues/*.txt` 与 `dialogue/**`（动画要靠**对话剧本的时间轴**触发，只读不改台词）✗；**台词与剧情文案本身**仍归编剧系统 AAA ✗。
- **地图/结构专题**：docs/地图接入进度.md、地图生成/结构资产相关文件。
- **任务系统BBB**（agent dsh · 会话主题「任务系统BBB」）：`docs/任务系统_交接.md`、`config/ftbquests/quests/chapters/**`、`config/ftbquests/quests/chapter_groups.snbt`、`config/whisperingquests/ftbq_bindings.json`、`archive/author-quest-content/**`。
  - **职责**：把编剧系统 AAA 的剧情文本**实施**成 FTB 任务正文（`剧情总纲.md` §零 写明"把任务写进 `config/ftbquests/` —— 队友实施"）。
  - **不碰**：法术/数值/美术（法术系统YYY）、剧情设定与文案（编剧系统 AAA）、地图与结构资产（地图专题）。
- 每份文档都应带**维护者署名头** ✓；全局状态**只维护 docs/当前状态.md 一页** ✓。

### ⚠️ 任务系统专题的一条基建缺口（2026-09-21，任务系统BBB 提出）

`.gitignore` 里的 `modpack/*/config/*` **把 `config/ftbquests/` 整个排除了** →
**我们新写的 FTB 章节不会进版本库**：换机就丢、也没法和队友共享，只能手工搬进整合包。

作者原有的章节就是这个下场（见下），别再让我们的正文重演：

- 2026-09-21 按计划把作者原有的**剧情/教学类 10 章**移出工作区，移走后才确认
  **它们从未进过 git**，`git checkout` 也救不回来 → 现唯一副本是
  `archive/author-quest-content/`（已为它开了 `.gitignore` 唯一例外，提交 `b707a43`）。
- **需求（待拍板）**：给 `config/ftbquests/quests/` 也放开一个例外，纳入版本控制。
  不做的话，任务系统的产出就是"**不在仓库里的仓库内容**" ✓。

