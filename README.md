# TN-C（元素觉醒 · 魔法系统改造）

Minecraft 1.20.1 / Forge 47.4.22 的魔改工程。给整合包 **元素觉醒 1.4.3** 加一套自研的
**魔法石 + 法杖** 体系，取代原来"卷轴 + 法术注册台"那一套。

当前阶段：**六系 108 个法术已可用**（雷/火/风各 3 条链、水/土/暗各 4 条链），
魔法石界面 + 法杖施法已跑通，魔力值真正参与施法（施法前硬拦截，不是事后补扣）。
**任务系统与剧情正文尚未开工**（现有 FTB 章节基本是物品收集清单式）。

> 📌 **每天开工前先看 [`docs/daily-workflow.md`](docs/daily-workflow.md)** ——
> 里面有一次性设置、日常命令、提交推送流程、以及"出问题对照表"，
> 可以直接复制粘贴，也可以打印出来贴屏幕上。
>
> 📌 **现在做到哪了** → [`docs/当前状态.md`](docs/当前状态.md)（技术唯一事实来源）。
> 📌 **故事是什么、任务怎么写** → [`剧情总纲.md`](剧情总纲.md)（剧情唯一事实来源，**编剧系统 AAA** 维护）。
> 　　其余文档的维护者：技术现状 = 法术专题（`docs/当前状态.md`）；地图/结构 = 地图专题（`docs/地图接入进度.md`）。
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

- **法术系统YYY**：docs/当前状态.md、docs/法术专题_交接.md、docs/投射物模型_配方.md、docs/法术制作与测试.md、docs/特殊魔法_设计.md、docs/法术总表_按设计文档.md、docs/美术资产清单.md、docs/实施进度.md、docs/项目交接.md、docs/验收表.md、docs/导图_全法术.png、src/main/java/com/tnc/tnc/magic/**、src/main/java/com/tnc/tnc/client/**、src/main/resources/data/tnc/spells/**、src/main/resources/assets/tnc/**、tools/gen_tnc_*.ps1、tools/verify_mod_jar.ps1、tools/install-to-pack.ps1。
- **文书专题**（agent dsh · 会话主题「文书」）：剧情唯一事实来源 `剧情总纲.md`。**只产文本，不实现** —— 新机制/新物品写「需求单」。
- **地图/结构专题**：docs/地图接入进度.md、地图生成/结构资产相关文件。
- **任务系统专题 / 任务系统BBB**（agent dsh · 会话主题「任务系统BBB」）：`docs/任务系统_交接.md`、`config/ftbquests/quests/chapters/**`、`config/ftbquests/quests/chapter_groups.snbt`、`config/whisperingquests/ftbq_bindings.json`、`archive/author-quest-content/**`。
  - **职责**：把文书专题的剧情文本**实施**成 FTB 任务正文（总纲 §零 写明"把任务写进 `config/ftbquests/` —— 队友实施"）。
  - **不碰**：法术/数值/美术（法术专题）、剧情设定与文案（文书专题）、地图与结构资产（地图专题）。
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

