# 整包 jar 更新专题 · 交接

> **维护者**：**整包 jar 更新专题**（agent `dsh` · 会话主题「jar更新」）· **建立** 2026-10-08
> **归属**：本文件由**整包 jar 更新专题**维护 ✓。
> **技术总现状**仍以 [`当前状态.md`](当前状态.md) 为准 ✗ —— 本文只写"**怎么把各专题的成果装进游戏**"这一件事 ✓。
> **各专题的实现细节**归各自专题 ✗（法术＝法术系统YYY · 任务＝任务系统BBB · 剧本＝编剧系统 AAA ·
> 动画＝动画系统MMM · 赌场＝赌场机器专题 · 地图＝地图/结构专题 · 女仆/NPC＝NPC 专题 ✓）。

---

## 一、这个专题负责什么

**一句话**：作者（用户）每完成一个话题，会来跟本专题**确认一句** ——
"东西都更新好了吗？能正常进游戏吗？" ✓ ——
本专题负责**把整个包更新到位**，并**用证据回答这两个问题** ✓。

### 1.1 职责边界（★ 别做重 ✗）

| 是**本专题**的活 ✓ | **不是**本专题的活 ✗ |
|---|---|
| 编译、`verify_mod_jar.ps1`、装机进实例、核对哈希 | 写/改各专题的 Java、法术 JSON、任务、剧本、模型 |
| 判断"这批改动是否需要重编译/重装机" | 决定玩法、数值、文案（那是作者与各专题的事） |
| 装机后核对"游戏还能不能起来、进不进得去世界" | 各专题自己的实机手感验收（那是作者的） |
| **在装机时发现并暴露跨专题的真崩** ✓（见 §四） | 替别的专题**改**他们的文件 ✗（先打招呼 ✓） |

> ⚠️ **重要**：本专题**不主动改别人的源码** ✗。
> 但**装机前后发现"游戏根本进不去"级别的故障时，本专题必须查到底、并把结论交回去** ✓ ——
> 因为"能不能进游戏"正是本专题唯一的产出 ✓（§四就是一次实例 ✓）。

---

## 二、★ 标准流程（每次作者来确认，照这个跑一遍）

**前提**：`_common.ps1` 会自动找整合包与游戏实例 ✓；
**游戏必须在运行中会被拒绝** ✗（`install-to-pack.ps1` 拒写 ✓ —— 这是对的，别绕过 ✓）。

```powershell
# 0) 先看清楚现在是什么状态（只读，安全 ✓）
git -C D:\ModTest status --short
#    ⇒ 有没有别的专题刚改完还没编译/还没装的东西 ✓
#    ⇒ 判断依据：build\libs\tnc-1.0.0.jar 的 mtime 是否早于 src/ 与 modpack/ 里最新的改动 ✓

# 1) 编译（Java 改动才需要 ✓）
$env:JAVA_HOME='D:\ModTest\.jdk17'
cd D:\ModTest ; .\gradlew.bat build --offline --console=plain
#    ⇒ 期望 BUILD SUCCESSFUL ✓（含全部 JUnit ✓）

# 2) 一条命令做完：装机 ＋ 全量闸门 ＋ 核对哈希
powershell -NoProfile -ExecutionPolicy Bypass -File D:\ModTest\tools\install-to-pack.ps1
#    ⇒ 期望结尾 mod jar verification passed. ＋ exit 0 ✓
#    ⇒ 必须看到 [ok] installed copy is identical to the build output ✓

# 3) ★ 如果改的是 kubejs / config（不走 jar 的那种），还要推数据
cd D:\ModTest\modpack
.\sync.cmd diff      # 只读，先看差异 ✓
.\sync.cmd push      # 时间戳感知，不会用旧文件盖新文件 ✓
#    ⇒ 数据改动**必须重启游戏**才生效 ✓

# 4) 装机后核对"游戏真的能进"（见 §三 ✓）
```

> 📌 **记不住就记这一条** ✓：
> **Java 改动 → `install-to-pack.ps1`；数据改动 → `sync.cmd push`** ✓。两个都改了 → 两个都跑 ✓。

---

## 三、★ "能正常进游戏"怎么证明（本专题的核心产出）

**注意**：本专题**不自己启动游戏** ✗（作者在自己的机器上玩 ✓）。
所以"能进游戏"这条，靠**三层证据**，逐层都能独立发现问题 ✓：

| 层 | 证据 | 能抓到什么 |
|---|---|---|
| **① 构建期闸门** | `gradlew build` 的 JUnit ＋ `verify_mod_jar.ps1` 全绿 ✓ | 资源缺失、注册漏项、模型越界、mixins 描述符错、法术/任务/对话引用对不上 ✓ |
| **② 装机一致性** | `installed copy is identical to the build output` ✓ | "改了一半没装上"、"装的是旧 jar" ✓ |
| **③ 实机日志复核** ★ | 读作者**上一次启动**的 `logs/debug.log` ＋ `crash-reports/` ✓ | **①②都抓不到的那类崩** ✗（§四 就是 ✓） |

### 3.1 ★ 第 ③ 层怎么做（这一步是本专题的"眼睛"）

```powershell
# 实例位置（别硬编码，含中文路径 ✓）
#   E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915
# 看启动是否成功 + 有没有崩溃：
#   logs\debug.log        ← 真正的启动日志（latest.log 是空的 ✓ 别被误导 ✗）
#   crash-reports\*.txt   ← 有新的就一定要读 ✓
```

**判读要点**（踩过的坑 ✓）：

| 现象 | 真相 |
|---|---|
| `logs\latest.log` 是空的 | **正常** ✗ —— Forge 1.20.1 的启动日志在 **`debug.log`** ✓ |
| 日志里中文是乱码 | 游戏用 `-Dsun.stdout.encoding=GBK` 跑 ✓ —— 按 **UTF-8** 读 ✓（`_common.ps1` 里有 `Read-TncSharedText` ✓） |
| 读取时报"非法字符/找不到路径" | 把脚本存成**带 BOM 的 UTF-8** ✓ —— PS 5.1 会把无 BOM 的文件当 ANSI 读 ⇒ 中文路径读坏 ✗（铁律 ⑫ 的老坑 ✓） |
| `Found valid mod file tnc-1.0.0.jar` | 我们的 jar 被正确识别 ✓ |
| `Mixing ... from mod tnc` | mixin 真的注入了 ✓ |
| 有 `crash-reports\crash-*.txt` | **必读** ✓ —— 尤其 `Description:` 那一行与栈里出现 `com.tnc.tnc` 的帧 ✓ |

> ★ **以"作者上一次启动的崩溃报告"为准** ✓，
> **不要**因为"我这边编译过了"就说"能进游戏" ✗ —— §四 正是这么漏掉的 ✓。

---

## 四、★ 战绩：2026-10-08 装机时抓到的真崩（★ 这一节说明第③层为什么必须有）

### 4.1 现象

作者来确认"能正常进游戏" ✓ —— 而**第①层全绿** ✓
（`gradlew build` ✅ · `verify_mod_jar.ps1` ✅ `exit 0` · 装机哈希一致 ✅），
两个 jar 的 SHA256 也**逐字节相同** ✓。

按 §3.1 去读实例的 `crash-reports/` ⇒ 发现**最新一份就是崩溃** ✗：

```
crash-2026-10-08_20.47.58-client.txt
Description: Rendering Block Entity
java.lang.IllegalStateException: Not filled all elements of the vertex
  at com.mojang.blaze3d.vertex.BufferBuilder.endVertex(BufferBuilder.java:264)
  at com.tnc.tnc.casino.client.TNCasinoGeometry.box(TNCasinoGeometry.java:94)
  at com.tnc.tnc.casino.client.TNSlotMachineRenderer.render(TNSlotMachineRenderer.java:164)
```

⇒ **我们的模组，一放下老虎机就崩客户端** ✗。
作者上一次启动**正是**这一版 jar ✓（日志 `20:45` 加载 `tnc-1.0.0.jar` ✓、
`20:47:47 Saving worlds` ⇒ `20:47:59` 崩 ✓）—— **已经装上了，也已经崩了** ✗。

### 4.2 根因与修法

**根因**：赌场几何的 `box()` **漏发顶点元素 `uv0`** ✗ ——
那三个渲染器绑的是 `RenderType.entityCutoutNoCull` ⇒ **实体顶点格式**（要求 uv ✓），
而 `box()` 只发了 position/color/overlay/uv2/normal ✓。
`BufferBuilder` 对"少发元素"**静默不报** ✗，只在 `endVertex()` 抛一次 ⇒ 崩 ✓。

**修法**：`.uv(0.0F, 0.0F)` 一行 ✓ ＋ 常量 `UV_ZERO` ✓ ＋ 长注释说明为什么不能省 ✓。

**防回归**：新增 `CasinoGeometryVertexFormatTest` ✓ ——
离线复刻 `BufferBuilder` 的那两条校验 ✓，**带两条反证**（漏 uv / 顺序错都必须抛 ✓），
期望格式**从 `DefaultVertexFormat.NEW_ENTITY` 读出来**而不是写死 ✓。

### 4.3 教训（★ 写给下一轮的自己）

1. **"编译过 ＋ 闸门过 ＋ 装机一致" ≠ "能进游戏"** ✗✗ ——
   这三条只管**资源与注册**，管不了**运行时渲染路径** ✓；
2. **实例的 `crash-reports/` 是"能不能进游戏"唯一的硬证据** ✓ ——
   每次作者来确认，**先把最新的崩溃报告看一眼** ✓（一条 `Get-ChildItem` 的事 ✓）；
3. **看到崩报里有 `com.tnc.tnc` 的帧就是我们的锅** ✓ ——
   哪怕 `Suspected Mods` 第一行写的是 Embeddium ✓（那次就是 ✓，
   栈里第二段才是我们的 `TNCasinoGeometry.box` ✓）；
4. **修完必须重新走 §二 全流程** ✓（编译 → 装机 → 核对 ✓），
   并把结论**写回对应专题的交接文档** ✓（本次写回 `casino/赌场机器_交接.md` §2.4 ✓）。

---

## 五、本机的固定事实（省得每次重新摸）

| 项 | 值 |
|---|---|
| 仓库 | `D:\ModTest` ✓ |
| 整合包（工作区） | `D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915` ✓ |
| 游戏实例（PCL 便携版） | `E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915` ✓ |
| JDK | `D:\ModTest\.jdk17` ✓（`$env:JAVA_HOME` 要指过去 ✓） |
| 构建产物 | `D:\ModTest\build\libs\tnc-1.0.0.jar` ✓ |
| 装进实例的位置 | `<实例>\mods\tnc-1.0.0.jar` ✓ |
| 跑脚本的方式 | **必须** `powershell -NoProfile -ExecutionPolicy Bypass -File ...` ✓（本机执行策略禁用脚本 ✗，直接 `. .\tools\_common.ps1` 会 `UnauthorizedAccess` ✗） |
| 提交守卫 | `git config core.hooksPath tools/git-hooks` ✓（别提交 jar / zip / 大文件 ✗） |

> ⚠️ **实例路径在仓库外** ✗ ⇒ 那里的东西**不进 git** ✓、也不受"只写工作区"的限制 ✓。
> 装机**只换 `<实例>\mods\tnc-1.0.0.jar`** ✓ —— 不碰存档、不碰其它 mod ✓。

---

## 六、每次收尾要交什么

给作者的回答**必须**包含这几条（有就给证据，没有就说没有 ✓）：

| # | 要说的 | 例子 |
|---|---|---|
| 1 | **编译**结果 | `BUILD SUCCESSFUL`（含 N 项 JUnit ✓） |
| 2 | **闸门**结果 | `mod jar verification passed.` `exit 0` ✓ |
| 3 | **装机**结果 | `installed copy is identical to the build output` ＋ jar 的 SHA256 前 16 位 ✓ |
| 4 | **数据同步** | 若改了 kubejs/config ⇒ `sync.cmd push` 已完成 ✓ |
| 5 | **★ 实机证据** | 读了实例最新 `crash-reports/` ＋ `debug.log`：**有无崩溃 / 上次启动加载的是不是这份 jar** ✓ |
| 6 | **待作者确认** | 需要重启游戏 / 需要作者试手感的部分 ✓ |

> ★ **第 5 条不许省** ✗ —— §四 就是省了会出的事 ✓。
