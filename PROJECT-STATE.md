# TN-C 项目进度总览（Handoff）

> **这份文件的用途**：新会话只需读这一份，就能完全接手项目。
> 最后更新：2026-09-15（新包「魔改版」已落地，见 4.8）
> 维护约定：每次有重大进展，回来更新这里。

---

# 一、项目是什么

**TN-C** = 一个剑与魔法主题的 Minecraft 整合包 + 自研核心 mod。

- 以 **元素觉醒 1.4.3** 为起点做魔改
- 但最终目标是**完全替换**它的魔法系统（自研魔法石体系）
- 也会替换战斗/职业系统（剑士路线走同样结构）

| 项 | 值 |
|---|---|
| Minecraft | **1.20.1** |
| 加载器 | **Forge** |
| 整合包当前版本 | 元素觉醒 **1.4.3 魔改版**（2026-09-15 打包，Forge 47.4.22，278 mods）|
| TN-C mod 工程 | Forge **47.4.13** ⚠️ 应与整合包对齐到 47.4.22 |
| mod_id | `tnc` |
| 包名 | `com.tnc.tnc` |
| 主类 | `TNMod` |
| 显示名 | TN-C |

---

# 二、路径速查表

| 东西 | 路径 |
|---|---|
| **TN-C Java mod 工程** | `D:\ModTest` |
| **整合包工作区副本**（编辑用）| `D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\` |
| **游戏包**（PCL2 实际运行）| `E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915\` |
| 旧游戏包（原版 1.4.3，未动，仍可玩）| `E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3\` |
| 新包原始压缩档 | `D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip` |
| **设计文档** | `D:\ModTest\design\TN-C-魔法系统设计.md` |
| **模型源文件** | `D:\ModTest\model-source\` |
| **Blockbench 工作目录** | `D:\blockbench\newprojet\` |
| **PCL2 启动器** | `E:\download\正式版 2.12.6.1\Plain Craft Launcher 2.exe` |
| Blockbench 程序 | `D:\blockbench\Blockbench.exe` |
| 同步脚本 | `D:\ModTest\modpack\sync.cmd` |

**其它装了车万女仆的整合包**（也装了我们的模型包）：`1.6.2`、`你好，新蒸程V1.5.9`

---

# 三、环境与工具

| 项 | 值 |
|---|---|
| JDK 17 | `C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta`（Microsoft OpenJDK 17.0.15，完整 JDK 含 javac）|
| JDK 21 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`（系统 PATH 上的）|
| Gradle toolchain 配置 | `C:\Users\FDCX\.gradle\gradle.properties` → `org.gradle.java.installations.paths` 指向上面那个 JDK 17 |
| IntelliJ 项目 | `D:\ModTest`（Gradle 项目，Project SDK 需设为 17）|
| Shell | **Windows PowerShell 5.1**（不是 pwsh！pwsh 没装）|
| 系统代码页 | **936 (GBK)** —— `.ps1` 文件必须带 **UTF-8 BOM**，否则中文乱码 |
| 脚本执行策略 | 本机**禁止直接运行 `.ps1`**（`& .\sync.ps1` 报 UnauthorizedAccess）→ 用 `sync.cmd`，或 `powershell -NoProfile -ExecutionPolicy Bypass -File <脚本>`（`sync.ps1` 已带 BOM，读中文路径没问题）|
| 文件沙箱 | `danger-full-access`，**无审批提示**（不要请求提权）|

**为什么用 PCL 自带的 JDK 17**：`api.foojay.io` 在国内连接被重置，Gradle 自动下载 toolchain 会失败。PCL 那个 JDK 17 是完整的，够用。

---

# 四、已完成的工作

## 4.1 TN-C Forge mod 工程（可用）

```
D:\ModTest\
├── build.gradle / gradle.properties / settings.gradle
├── src/main/java/com/tnc/tnc/
│   ├── TNMod.java       ← 主类（已清理 MDK 演示内容）
│   └── Config.java      ← 配置类
├── src/main/resources/
│   ├── META-INF/mods.toml
│   ├── pack.mcmeta                     (pack_format 15 = 1.20.1)
│   └── assets/tnc/
│       ├── models/item/sword.json      ← 试作之剑模型
│       ├── textures/item/sword.png
│       └── lang/{zh_cn,en_us}.json
└── model-source/                        ← 模型源文件与参考
```

**状态**：`gradlew build` 成功、`runClient` 能启动并加载 mod ✅

**已做的改动**：
- MDK 模板改名为 TN-C（`mod_id=tnc`、包 `com.tnc.tnc`、主类 `TNMod`）
- 删除 MDK 演示内容（`example_block` / `example_item` / `addCreative`）
- 创造模式标签页改为 `tnc:main`，名称 "TN-C"
- `build.gradle` 加了 `idea.module { downloadJavadoc = downloadSources = true }`（能跳进 MC 源码）
- `Config.java` 修掉过时 API（`new ResourceLocation(s)` → `ResourceLocation.tryParse(s)`）
- **未完成**：`mod_authors` 还是占位符

## 4.2 试作之剑（已完成，游戏内可见）

- 3D 模型 + 贴图 + `display` 显示设置
- 关键教训：**模型平躺在 XZ 平面（Y 薄）时，必须自己写 `display`，不能用 `parent: item/handheld`**（那是给立在 XY 平面的模型用的）
- `gui` 旋转用 `[-90, 0, 0]` 把平躺模型立起来
- 游戏内名字：试作之剑 / Prototype Sword

## 4.3 songzhikun 角色模型（TLM 车万女仆）

- **24 骨骼**的精简骨架（从参考模型 80 骨骼精简）
- UV 布局已算好（无重叠，塞进 64×64）
- 已打包成 TLM 模型包 `tnc_pet`，装在 4 个整合包里
- 文件：`model-source/songzhikun-v4-packed64.json`（模型）、`songzhikun-checker.png`（验证贴图）、`songzhikun-guide64.png`（UV 参照）

**TLM 1.5.0 的关键知识**（见第五节）

## 4.4 整合包工作区 + 同步工具（可用）

```
D:\ModTest\modpack\
├── README.md      ← 使用说明
├── sync.cmd       ← diff / push / pull / push -Force
├── sync.ps1
└── 元素觉醒1.4.3-魔改版-20260915\  ← 把包放这一层（当前就是它）
```

**脚本会「自动识别」整合包名字** —— 换包时只要换文件夹，不用改配置。
它同时校验游戏包是否存在：`E:\download\正式版 2.12.6.1\.minecraft\versions\<同名>\`

**只同步这些目录**：`kubejs` `config` `defaultconfigs` `local` `data` `tlm_custom_pack` `vaultpatcher` `hotai` `immersive_furniture`
**绝不动**：`mods` `saves` `logs` `backups` `xaero` `resourcepacks`

**⚠️ push 是时间戳感知的**：只覆盖"工作区比游戏包新"的文件。因为游戏运行时不停改写 `config/`，无脑 push 会覆盖掉。

**⚠️ 2026-09-14 变更**：旧的 `元素觉醒1.4.3` 工作区副本已删除，我们自己的 KubeJS 文件
**归档**到 `D:\ModTest\archive\blink-spell\`（带 README 说明）。

**⚠️ 2026-09-15 变更**：用户提供了新包 `元素觉醒1.4.3-魔改版-20260915.zip`（1.6 GB）。
已解压为新**实例**（原 `元素觉醒1.4.3` 实例完全没动），并 `sync.cmd init` 生成同名工作区。
新包**只有** `mods` `config` `defaultconfigs` `kubejs` `resourcepacks` `shaderpacks` +
版本 json/jar；**没有** `tlm_custom_pack` `vaultpatcher` `hotai` `immersive_furniture` `local` `data`。
→ 我们的女仆模型包 `tnc_pet` 和汉化补丁还只在旧实例里，要用得手动搬。详见 4.8。

## 4.5 第一个原创法术：闪现（**已跑通，已归档**）

> 📦 文件已移到 `D:\ModTest\archive\blink-spell\`，那里有完整的还原说明。


```
雷系无关，这是验证链路用的奥术法术
tnc:blink_1  距离 8 格   tier 1  冷却 6s
tnc:blink_2  距离 16 格  tier 2  冷却 9s
tnc:blink_3  距离 24 格  tier 3  冷却 12s
```

**打通的完整链路**（这是最有价值的成果）：

```
data/tnc/spells/blink_*.json              ← 法术本体（纯数据）
        ↓
kubejs/startup_scripts/item/tnc_blink_scroll.js   ← 注册卷轴物品
        ↓
kubejs/data/spellanvil/tags/items/scroll.json     ← 加入 spellanvil 的物品标签
        ↓
kubejs/server_scripts/spell/tnc_blink_bind.js     ← SpellAnvilEvents 绑定
        ↓
kubejs/client_scripts/tnc_blink_tooltip.js        ← tooltip
        ↓
法术注册台 → 法术书 → 施放 ✅
```

**全部是新增文件，没有修改作者的任何代码。**

## 4.6 元素觉醒架构分析（已完成）

**三层结构**：

```
① 引擎层（开源 mod，不能改）
   spell_engine / spell_power / spellbladenext / wizards / paladins / rogues ...

② 作者自研 mod（闭源 jar）
   ysjxspells / ysjx_weapons / ysjx_dimension / ysjxteams / ysjxmodel

③ 内容层（★ 可改主战场）
   kubejs/ (189 文件) / config/ (3582 文件) / config/openloader/ (3021 文件)
```

**作者自己的数据包在 `config/openloader/data/yuansujuexing/`**（2656 文件）—— 他用 OpenLoader 把一切都做成了可覆写的形式。这是给魔改者留的门。

## 4.7 魔法系统设计（进行中）

见 `D:\ModTest\design\TN-C-魔法系统设计.md`（当前 v0.3）

**核心设计**：
- 七元素（水/火/雷/风/土/光/暗），亲和力 1–6，天生固定
- 五等级（冒险者/精英/王/传说/神），每元素独立进度
- 魔力值上限 ← 亲和力总和 + 原版等级；魔法点数 ← 魔力上限档位
- 学习 = 卷轴（路线）+ 投魔法点数
- 不可撤回，只能靠遗忘药水重置
- 领域魔法（特殊魔法）：默认只有最高亲和力那个；任务书道具可解锁多个
- 融合：临时，领域范围内触发，评级向下取

**⚠️ 重要决策**：`simplyskills` **废弃并移除**（用户 2026-09-14 决定）→ **2026-09-15 已在新包里完成并核实**（见 4.8）

## 4.8 新包「元素觉醒1.4.3-魔改版-20260915」（2026-09-15 已落地并核实）

**来源**：`D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip`（1.6 GB / 8399 条目）。
包里带一份合作者写的 **`README-给合作者.txt`** —— 它的每一条改动我都对着实际文件核过了，**全部属实**。

**落地链路**（本次完成）：

```
D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip        ← 原始压缩包（保留，未删）
   ↓ 解压
E:\...\versions\元素觉醒1.4.3-魔改版-20260915\      ← 新实例（8393 文件 / 1.6 GB）
   ↓ sync.cmd init
D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\   ← 工作区（3768 文件 / 46.6 MB）
```
`sync.cmd diff` 结果：**In sync.** ✅

**⚠️ 解压后必须做的改名（PCL 的硬要求）**：压缩包里的文件名是 `元素觉醒1.4.3.json` /
`元素觉醒1.4.3.jar`，但 PCL 要求 **文件夹名 = JSON 名 = jar 名**，且 JSON 里的 `id` 也要一致。
所以已改为：
- `元素觉醒1.4.3.json` → `元素觉醒1.4.3-魔改版-20260915.json`（并把 `"id"` 改成同名）
- `元素觉醒1.4.3.jar`  → `元素觉醒1.4.3-魔改版-20260915.jar`

改名前后的 json/jar 与旧实例的**完全一致**（原版 1.20.1 客户端 jar + Forge 47.4.22 清单，
117 个 library，`assetIndex` = `5`，`assets\indexes\5.json` 已存在）。**内容变的是 mods/config/kubejs。**

**本版相对原版 1.4.3 的改动**（合作者所述 → 我的核实结果）：

| 改动 | 内容 | 核实 |
|---|---|---|
| **移除 Simply Skills** | 删 `simplyskills-1.7.2` + `simplyskills-tree-bridge-1.0.0` 两个 jar、`config\simplyskills\`、`config\simplyskills_tree_bridge.json`、openloader 里的技能树与资源 | ✅ 都不在了 |
| 修补 `Spell_Effectss.js` | 删 2 处失效引用（L63 `simplyskills:marksmanship`、L90 `simplyskills:magic_circle`），原值留注释 | ✅ 注释在 |
| **新增原创「章国源之矢」** | 6 个 kubejs 文件 + `wizards\spells\zhangguoyuan_bolt.json` + spell_assignment + spellanvil 的 scroll 标签 + 2 张贴图 + 物品贴图（速度 0.15、伤害系数 200）| ✅ 全在 |
| **新增 MCA Reborn 7.6.26** | NPC 关系 / 结婚 / 生子 / 家族树；前置 architectury 9.2.14（包内已有）| ✅ 在，⚠️ **只装未测** |
| **新增 Bountiful 6.0.4** | 委托板悬赏；前置 Kambrik 6.1.1 + kotlinforforge 4.12.0（包内已有）| ✅ 在，⚠️ **只装未测** |
| 保留 puffish 框架 | `puffish_skills-0.18.3` / `puffish_attributes-0.8.2` 未动 | ✅ 在 |

**代价**：ELA 原本那两棵技能树（ascendancy / tree）随 Simply Skills 一起没了，
**puffish 技能界面现在是空的 —— 这是预期状态**。另外被删的 `magic_circle` 原本给全部
8 种 spell_power +0.3 倍率，若发现某些玩法强度掉了，原因就在这。

**新包缺的目录**（旧实例有、新实例没有）：`tlm_custom_pack`（1266 文件，我们的 `tnc_pet`
女仆模型包就在这）、`vaultpatcher`（158 文件，汉化）、`hotai`、`immersive_furniture`、`local`、
`data`；另外 `mods\.connector`（信雅互联缓存）被有意剔除，**首次启动会自动重建，会比较慢，属正常**。

**✅ 2026-09-15 已从旧实例搬入新实例**（用户选定的部分，搬完工作区已 `pull` 同步，diff = In sync）：

| 搬的东西 | 数量 | 状态 |
|---|---|---|
| `tlm_custom_pack\`（含我们的 `tnc_pet` 女仆模型包）| 1266 文件 / 37.4 MB | ✅ 逐文件核对，与旧实例完全一致 |
| `vaultpatcher\`（汉化补丁）| 158 文件 / 0.8 MB | ✅ |
| 闪现链路的 7 个文件 | 7 文件 | ✅ 按 `archive\blink-spell\README.md` 重放（见下） |

仍**没搬**（用户本次没选）：`hotai`、`immersive_furniture`、`local`、`data`。

**🧪 首次启动实测（2026-09-15 21:05，失败 —— 但原因不在包）**：

- ✅ 游戏**正常进到主菜单**，KubeJS / 资源 / 数据包全部加载完成
- ✅ 整份 `latest.log`（13348 行）**只有一种错误：内存溢出**，没有任何 mod 报错、没有 Mixin 失败
- ❌ 用户建新世界「新的世界」，`准备生成区域中` 走到 54%，**21:09:40 爆 `java.lang.OutOfMemoryError: Java heap space`**，
  随后几万条 OOM，卡在 58% 约 15 分钟，直到 21:24 手动关闭
- **根因**：PCL 这次只给了 **3174 MB** 堆（见 8.3），跟魔改版的内容无关
- 残留：半成品存档 `saves\新的世界\`（405 文件 / 693 MB）→ **已于 2026-09-15 删除**，`saves\` 现在是空的
- 这一轮运行还让实例自己生成了 `data\`、`local\`、`config\mca.json`（MCA 的配置），
  并把大量 `config\*.toml` 与 `vaultpatcher\cache\*.class` 改了时间戳
  → 工作区 `diff` 会显示 425 处 `pack-newer`，**属正常**（push 是时间戳感知的，不会覆盖游戏写的新配置）

**我们的 KubeJS 成果原来不在新包里**（下面是**重放前**的新旧 kubejs 逐文件对比）：

```
只在旧实例 ：client_scripts\tnc_blink_tooltip.js
             data\spellanvil\tags\items\scroll.json       ← 我们加的卷轴标签
             data\tnc\spells\blink_{1,2,3}.json
             server_scripts\spell\tnc_blink_bind.js
             startup_scripts\item\tnc_blink_scroll.js
只在新包   ：assets\kubejs\textures\item\zhangguoyuan.png
             client_scripts\zhangguoyuan_tooltip.js
             server_scripts\shaped\zhangguoyuan.js
             server_scripts\spell\zhangguoyuan_bolt_scroll.js
             startup_scripts\item\zhangguoyuan.js / zhangguoyuan_bolt_scroll.js
```
闪现的**完整还原说明**仍在 `D:\ModTest\archive\blink-spell\`（照它重放即可）。

**✅ 上面「只在旧实例」那 7 个文件已于 2026-09-15 重放回新包**。
关键点：我们的 `kubejs\data\spellanvil\tags\items\scroll.json` 是 `"replace": false`，
而合作者把他的 `zhangguoyuan_bolt_scroll` 加在 **openloader 数据包**的
`config\openloader\data\yuansujuexing\data\spellanvil\tags\items\scroll.json`（同样是 `replace: false`）
—— **两个数据包的同名标签会合并，互不覆盖**，已实测确认合作者那条没被动过。

## 4.9 作者 NPC / 任务系统的“冲突”排查（2026-09-15）

> 用户反馈：新包进游戏后「原作者的 NPC 跟任务都消失了」。逐条查完的结论：
> **不是 MCA 干的，也不是魔改版删了作者的内容；MCA 在物理上换不掉作者的 NPC。**

**作者的 NPC 系统是什么**（`ysjxmodel-1.0.0.jar`，作者自研闭源）：
- 4 个实体类 `YileinaEntity` / `JiuhuEntity` / `TbsCharacterEntity` / `PlayerSkinNpcEntity`
- `javap` 实测：**全部 `extends net.minecraft.world.entity.PathfinderMob`**（不是 `Villager`！），
  并实现 `com.p1nero.dialog_lib.api.entity.custom.IEntityNpc` + GeckoLib `GeoEntity`
- 名单：伊蕾娜 / 酒狐×6 / 迷迭香 / 霜星 / 秦亦禾 / 作者 sh1ntaro / 守卫·布兰特 /
  七星 / 五大商人（武器·材料·珠宝·符文·烧烤·农业）等
- 任务 = `whisperingquests` 3.2 + `p1nero_dl` 2.3.0 对话库 + FTB Quests 16 个章节
  （`config\whisperingquests\ftbq_bindings.json` 把 `ysjxmodel:main/xxx` 绑到 FTB 任务 ID）

**关键证据链**：

| # | 查到什么 | 说明 |
|---|---|---|
| 1 | MCA 配置 `overwriteOriginalVillagers: true` + `fractionOfVanillaVillages: 0.0` | MCA **只替换原版村民**，作者的 NPC 不是村民 → 换不掉 |
| 2 | 作者 NPC 全在 | 比对时新旧实例 mods 都是 278（现新实例已 279，因为装回了 TLM），作者 mod 一个不少 |
| 3 | 作者把 5 个维度做成了世界模板 | `ysjx_dimension-1.0.0.jar` 里有 `world_templates/{magic_association,abandoned_city,undead_fortress,sacred_arena,lueduodushi}/`，共 **358 个 `.mca`** |
| 4 | 解压新世界大厅实体区块实测 | 新世界大厅里**确实存在 14 种作者 NPC 实体**（伊蕾娜、迷迭香、霜星、各大商人…），不是“全没了” |
| 5 | 对照作者模板 = 21 种 | 新世界比模板/老世界**少 7 种**：守卫·布兰特、炼金术师·七星、珠宝商、烧烤师傅、农业商人、巫女酒狐、传教者 dt（多为商店/守卫类） |
| 6 | 两版都有的作者遗留 bug | 数据包 `tags/functions/{load,tick}.json` 引用 `yuansujuexing:welcome/load`、`welcome/tick`，但**全包（含所有 jar）找不到这两个 `.mcfunction`** |
| 7 | 真·mixin 冲突（新旧版都有） | `ysjxspells` 的 `LegendaryObliteratorDamageCapMixin` 对 `legendary_monsters` 的 TheObliterator 应用失败（`@Shadow getAttackState` 找不到）→ 那个伤害上限静默失效 |

**⚠️ 合作者 README 没写的两处改动**（mods 目录逐文件比对，当时 278 vs 278）：

```
移除： touhoulittlemaid-1.5.2-forge+mc1.20.1.jar   ← 车万女仆！README 只提了 simplyskills
       simplyskills-1.7.2 + simplyskills-tree-bridge-1.0.0   （这个 README 写了）
新增： minecraft-comes-alive-7.6.26（MCA）、Bountiful-6.0.4   （README 写了）
       [自定义局域网联机] lanserverproperties-1.11.1         （README 没写）
```
→ **TLM 原本不在新包里**，当时我们搬进去的 `tlm_custom_pack\tnc_pet` 女仆模型包没有宿主 mod。

**✅ 2026-09-15 已装回 TLM**：从旧实例拷 `mods\touhoulittlemaid-1.5.2-forge+mc1.20.1.jar`
（23.27 MB，MD5 一致），新实例 mods **278 → 279** 个 jar。
TLM 1.5.2 只要求 Forge≥46 + MC 1.20~1.20.2（不内嵌 GeckoLib），包内已有 `geckolib-forge-1.20.1-4.8.4`。
TLM 的 `config\touhou_little_maid*` 没搬（首次启动自动重建），
模型包清单 `tlm_custom_pack\tnc_pet\assets\tnc_pet\maid_model.json`（songzhikun ×2）已在位。
⚠️ **`mods` 目录不参与 sync**，这类改动只存在于游戏包。

**真正被 MCA 影响的东西**：依赖**原版村民**的内容 —— 村庄拼图/村民交易
（日志里 MCA 的 `Missing trade item listings for ... IafVillagerRegistry / LNEVillagerTrades` 警告）。
想恢复原版村民：把 `config\mca.json` 的 `overwriteOriginalVillagers` 改成 `false`
（或用 `moddedVillagerWhitelist` 白名单），重启即可。

**仍未解释**：那 7 种 NPC 为什么在新世界大厅里没有。可能是「实体数据只保存了已加载区块」
（模板 4 个实体区块文件，新老世界都只有 4 个但内容不同）。**待验证方法**：进游戏把大厅整个逛一圈，
再解压实体区块复扫一次。

## 4.10 雷系五级法术「小闪电 / 雷场 / 雷击 / 雷暴 / 天打五雷轰」（2026-09-15 首批完成）

**纯数据 + KubeJS，没写一行 Java** —— 走的还是闪现验证过的那条链路（`archive/blink-spell/`）。
详细数值表、文件清单、验证步骤写在设计文档 **`design\TN-C-魔法系统设计.md` 第十五节**，这里只记要点。

| 级 | 名称 | 法术 id | 形态 | 伤害系数 | 冷却 |
|---|---|---|---|---|---|
| 1 | 小闪电 | `tnc:spark` | 光标单体 | 0.6 | 3s |
| 2 | 雷场 | `tnc:lightning_field` | 自身领域（半径 4，6s）| 0.35/跳 | 14s |
| 3 | 雷击 | `tnc:lightning_strike` | 单体重击 + 2.5 格溅射 | 1.4 | 8s |
| 4 | 雷暴 | `tnc:lightning_storm` | 自身风暴（半径 8，10s）| 0.7/跳 | 24s |
| 5 | 天打五雷轰 | `tnc:heavenly_thunder` | 5 发天雷落地 + 6 格溅射 | 2.2×5 | 45s |

**新增文件**（工作区）：`kubejs\data\tnc\spells\*.json`（5 个）、
`kubejs\startup_scripts\item\tnc_lightning_scrolls.js`、`kubejs\server_scripts\spell\tnc_lightning_bind.js`、
`kubejs\client_scripts\tnc_lightning_tooltip.js`、`config\openloader\resources\TN-C\`（我们的资源包 + 5 个 16×16 法术图标）。
卷轴 id = `kubejs:tnc_<名>_scroll`，已加进 `data\spellanvil\tags\items\scroll.json`。

**本轮学到的 SpellEngine 硬知识**（写在这里省得下次再扒 jar）：

- `release.target.type` 只有 8 种：`AREA` `BEAM` `CURSOR` `SELF` `PROJECTILE` `METEOR` `CLOUD` `SHOOT_ARROW`
- **`CLOUD` = 持续领域**（`radius` + `impact_tick_interval` + `time_to_live_seconds`），
  样例都是 `range: 0` → **以施法者为中心**，每 N tick 重放一次 `impact`。雷场/雷暴就是靠它做的
- **`METEOR` = 从天上砸下来**（`launch_height` + `launch_properties.extra_launch_count` 可一次多发），
  天打五雷轰 = `extra_launch_count: 4`（共 5 发），投射物模型借用了包里的 `berserker_rpg:projectile/lightning_bolt`
- `impact[].action.type` 只有 6 种：`DAMAGE` `HEAL` `STATUS_EFFECT` `FIRE` `SPAWN` `TELEPORT`
- `cost` 字段：`exhaust`（饥饿）/`item_id`+`consume_item`（符文材料）/`durability`/`cooldown_duration`/
  `cooldown_haste_affected`。**本包 `config\spell_engine\client.json5` 已经配好了「数字键 1–9 施法」**
  （`spellHotbar_2_defer: HOTBAR_KEY_2`…）—— 设计文档的阶段 5 其实是现成能力
- 可用素材：法术动画 22 个（雷系适合 `one_handed_sky_charge`）、粒子 30 个
  （`electric_arc_a/b`、`white_spark_mini`、`smoke_medium`…）、音效 18 个（`generic_lightning_casting/release`）
- **法术图标** = `assets\<命名空间>\textures\spell\<法术名>.png`，**16×16**；
  投射物贴图是 `textures\spell_projectile\`，32×32

**图标流水线**：`D:\ModTest\tools\spell_icon_gen.ps1`（ASCII 掩码 → PNG，
自带尺寸校验 + 8 倍放大预览图 `design\refs\icon-preview.png`，用 `read_image` 看效果再改掩码）。
⚠️ 该脚本是**纯 ASCII**（整合包目录自动识别），所以**不需要 BOM** —— 这是踩过 BOM 坑之后的写法。

**法术体检工具**：`D:\ModTest\tools\verify_spells.ps1`（**写新法术前后都跑一遍**）。
它把我们的法术和**包里 158 个既有法术**对照，检查：
① 用到的字段名/枚举值是否被既有法术证明过（防拼错、防臆造）；
② 交付链路是否完整：法术 json → 卷轴物品 → spellanvil 标签 → 注册台绑定 → tooltip → 16×16 图标。
2026-09-15 跑的结果：**雷系 5 个全部 `y/y/y/y/y`，0 问题**；闪现 3 个标为 `legacy`（旧命名约定，缺图标）。
另外确认过 `config\openloader\advanced_options.json` 里 `resourcePacks.enabled = true` 且
`additionalFolders` 为空 → OpenLoader 确实会加载 `config\openloader\resources\TN-C\`，我们的图标会被读到。

**✅ 已核对**：工作区 15 个文件与游戏包**逐字节一致**（push 之后做的哈希比对）。

**还没做**：卷轴的获取途径（现在只能 `/give`）、雷系领域魔法、以及把 `cost` 换成 TN-C 自己的魔力值。

> ⚠️ **重要认知修正（2026-09-15，用户指出）**：卷轴 + 法术注册台是 TN-C <b>要替换掉</b>的旧体系。
> 上面那套只当**临时测试入口**用，等魔法石的解锁流程能用了就**删掉**（`kubejs/data/tnc/spells/*.json`
> 这 5 个法术数据文件要保留 —— 它们是引擎层的法术本体，魔法石解锁后施放的就是它们）。

## 4.11 魔法石数据层（Java，2026-09-15 完成并已装进游戏）

按设计文档**阶段 1** 写的第一块 Java 代码。**没有 GUI、没有解锁界面、没有施法** —— 纯数据 + 调试命令。
设计文档第十六节有完整的字段表/数值表/验证步骤，这里记要点和踩坑。

**TN-C mod 已经进游戏了**：`build\libs\tnc-1.0.0.jar` → 整合包 `mods\`，**mods 279 → 280 个 jar** ✅
（工程还是 Forge **47.4.13**，整合包是 **47.4.22** —— 47.x 同线二进制兼容，实测能加载；
本地 Gradle 缓存里只有 47.4.13，对齐要联网下载，所以**先不对齐**，等真出问题再说。）

新增 Java 文件（`D:\ModTest\src\main\java\com\tnc\tnc\`）：

```
Config.java                       ← 全部数值配置（替换了 MDK 演示配置）
magic\Element.java                ← 七大元素枚举 + 五等级名称
magic\MagicStoneData.java         ← ★ 核心数据：亲和力[7]/魔力/上限/点数/进度/已学 + NBT
magic\MagicStone.java             ← Capability 挂载·复制·登录·tick + Provider
network\MagicStoneNetwork.java    ← 网络通道 + 整包同步
network\MagicStoneClientSync.java ← 客户端写回（单独类，避免服务器加载 MC 客户端类）
command\MagicStoneCommand.java    ← /tnc 调试命令
```

**🔴 本轮踩的坑（值得记住）**：
1. **`@Mod.EventBusSubscriber` 漏了就静默失效** —— `MagicStone` 类里挂着"挂载/重生/登录/tick"事件，
   我第一版忘了给类加注解，编译照样通过、但事件永远不会触发（capability 根本挂不上，
   `/tnc magic` 只会说"拿不到数据"）。**编译通过 ≠ 会生效。**
2. **`new ResourceLocation(ns, path)` 在这个 Forge 版本被标记为"待删除"**（只是警告不报错），
   正确写法是 `ResourceLocation.fromNamespaceAndPath(ns, path)`。
3. **lambda 里不能抛受检异常** —— 命令的 lambda 调了 `player(ctx)`（会抛 `CommandSyntaxException`），
   改成把 `ServerPlayer` 作为参数传进 lambda。

**调试命令**（权限 2，单人开作弊）：`/tnc magic`、`/tnc mana set|add|full`、
`/tnc affinity set <元素> <1-6>|roll`、`/tnc progress set <元素> <0-5>`、`/tnc learn|forget <法术 id>`、`/tnc reset`。

**数值全在 `config/tnc-common.toml`**（默认：亲和力系数 10、每级 +10、默认亲和力 3、
点数档位 100/300/500/800/1200、解锁花费 1/2/3/4/5、每秒回蓝 1）。

**验证步骤**：进游戏 → `/tnc magic` → 期望看到七元素亲和力全 3、魔力 210/210、可用点数 1
（210 只跨过 100 这一档）→ 升级看上限每级 +10 → 退出重进确认数据还在。

**下一批（阶段 3）**：魔法石 GUI（物品栏中间入口 + 亲和力/点数/已学/可解锁）+
真正的"花点数解锁法术"校验流程。

## 4.12 魔法石 GUI 第一版（Java，2026-09-15 完成并已装进游戏）

阶段 3 的第一版：**能看数据、能花魔法点数解锁法术**。详细说明见设计文档第十七节。

| 新增文件 | 作用 |
|---|---|
| `client\MagicStoneButton.java` | 物品栏里那颗菱形石头（`AbstractWidget` + `GuiGraphics.fill` 手绘） |
| `client\MagicStoneScreen.java` | 魔法石界面本体（336×224 面板：左列七元素、右列法术目录） |
| `client\MagicStoneClientEvents.java` | 用 `ScreenEvent.Init.Post` 把石头塞进 `InventoryScreen` |
| `magic\SpellCatalog.java` | 法术目录（雷系 5 个，硬编码，以后改数据驱动） |
| `magic\MagicStoneLearning.java` | ★ 四条门槛的解锁判定（命令与 GUI **共用同一份**） |
| `network\MagicStoneActionPacket.java` | 客户端 → 服务端：`REQUEST_SYNC` / `UNLOCK` |

**🔑 重要决策：入口没用 Mixin。** 设计文档原写「Mixin `InventoryScreen`」，
实际改用 Forge 的 `ScreenEvent.Init.Post` + 自绘控件 —— 不用配 mixin/refmap，
也不会和包里那堆界面 mod（FancyMenu / ModernUI / HUD）抢屏幕。

**🔴 本轮又踩一个坑（自己代码里的死循环）**：界面 `init()` 里请求同步 → 服务端回包 →
界面 `rebuildWidgets()` → 又 `init()` → 又请求同步…… 无限发包。
用 `syncRequested` 标志位保证只请求一次。**"拉数据 → 刷新界面"这条链一定要防自激。**

**⚠️ Forge 版本已对齐到 47.4.22**（本地 Gradle 缓存里也有 mapped jar 了）。
上一轮说"先不对齐"是在缓存缺失时的权宜；这一轮联网补上并重建通过。

**验收步骤**：进游戏 → 按 E → 点玩家模型右边那颗紫色菱形石头 → 期望看到
亲和力全 3 / 魔力 210/210 / 点数 1 可用、「小闪电」可解锁其余显示"需先学上一级" →
点「解锁 1点」→ 点数归零、已学变 1。命令行等价：`/tnc spells`、`/tnc learn tnc:spark`。

**还没做**：石头的贴图/模型（现在纯色块）、法术列表滚动分页、
**解锁后怎么施放**（路线还没定）、亲和力分配规则（一律全 3）、遗忘药水。

## 4.13 运行时验证 + 「解锁后怎么施放」的路线结论（2026-09-15）

### ✅ mod 真的能在运行时加载（objective ① 的"进游戏确认"）

**服务端**（`gradlew runServer`，`run\` 目录里先补了 `eula.txt` + 一份轻量 `server.properties`）：

```
[modloading-worker-0/INFO] [com.tnc.tnc.TNMod]: TN-C common setup complete, 1 item(s) registered
[modloading-worker-0/INFO] [com.tnc.tnc.TNMod]: TN-C magic stone data layer ready (...)
[Server thread/INFO] [DedicatedServer]: Done (4.187s)! For help, type "help"
```
外加 `run\config\tnc-common.toml`（937 字节）自动生成 → **注册、配置、命令都没问题**。

**客户端**（`gradlew runClient`）：
```
[Worker-Main-5/INFO] [com.tnc.tnc.TNMod]: TN-C client setup, player = Dev
[Render thread/INFO] [Minecraft]: Setting user: Dev
[Render thread/INFO] [SoundEngine]: Sound engine started      ← 到主菜单
```
无异常、无类加载错误 → **客户端类（GUI/事件订阅）也能正常加载**。

**魔法石自检**（`MagicStoneSelfTest`，服务器启动自动跑 + `/tnc selftest`）：
```
TN-C magic stone self-test [server start]: 14/14 passed
  [ok] capability attached : MagicStoneData present on [Minecraft]
  [ok] affinity sum = 21 / max mana = 210 @level 0 / = 310 @level 10
  [ok] points total = 2 @310
  [ok] cannot skip to tier2 : OUT_OF_ORDER
  [ok] tier4 blocked by affinity : maxTier=3 -> AFFINITY_TOO_LOW
  [ok] unlock tier1 works : result=OK learned=true spent=1 progress=1
  [ok] NBT round-trip : affinity=21 maxMana=310 learned=1
```
→ **capability 真的挂得上、数值算法真的按预期跑、NBT 真的存得进读得出**。

### 🔴 决定性技术约束：**Java 不能直接调 SpellEngine**

`spell_engine` 是 **Fabric mod**，jar 里是 **intermediary 名字**（`javap` 看到的是
`net.minecraft.class_1657` 这种），它靠 Sinytra Connector 才跑在 Forge 上。
我们的 Forge mod 用 mojmap 编译 —— **两边在编译期就对不上**，所以：

> **所有和法术引擎的交互只能走 KubeJS / 数据包**（作者全用 KubeJS 就是这个原因）。
> `net.spell_engine.api.event.CombatEvents.SPELL_CAST` 这类"公开 API"看着能用，
> 实际从 Forge Java 侧调不通；而且它是**观察者事件（不可取消）**，
> 结构是 `caster / spell / targets / action(CHANNEL|RELEASE) / progress`。

### 🔑 关键发现：法术池是纯数据，且可被数据包覆盖

法术书能装哪些法术由 **spell pool** 决定，而它是纯 json：

```
ysjx_weapons-1.0.0.jar → data/ysjx_weapons/spell_pools/lightning.json
{ "spell_ids": [ 5 个 ysjxspells: 雷法 ] }
```

我们已经在 `kubejs\data\ysjx_weapons\spell_pools\lightning.json` **覆盖**它，
把 tnc 的 5 个雷法加进去（**并集 10 个**，不能只写自己的 —— 这个格式是整体替换，不是合并）。
→ 这样拿一本 `ysjx_weapons:lightning_spell_book` 就能走引擎原有流程学到/施放我们的法术。

⚠️ **覆盖语义要分清**：**tags 是跨数据包合并的**（所以我们的 `spellanvil/tags/items/scroll.json`
和作者的能共存），而 **spell_pools / spells 这种普通数据文件是整体替换**（优先级高的包赢）。

### 三条路线（"解锁后怎么施放"）

| 路线 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 数据+引擎优先**（已做）| 法术进池 → 玩家拿雷系法术书 → 引擎的绑定/施法流程（数字键已配好）| **零 Java、立刻能放** | 入口还是引擎那套（绑定台/青金石）|
| **B. 魔法石 GUI 当唯一入口**（✅ 2026-09-15 已实现）| 解锁成功 → **Java 直接把法术写进玩家的法术书** | 玩家彻底见不到卷轴/绑定台 | 魔力仍只能事后扣（见下）|
| **C. 自研施法**（设计文档阶段 5 终局）| 自己做法杖 + 自己判魔力/冷却/已学，再执行法术 | **唯一能"没魔力就放不出来"** | 最费工 |

### ⚠️ 上面那条"Java 不能调引擎"的结论要**修正**

原版 `spell_engine` jar 确实是 Fabric/intermediary 名字、Forge 侧编译不了。**但是**：
整合包 `mods\.connector\` 里躺着 Sinytra Connector **重映射过**的版本 ——

```
mods\.connector\spell_engine-0.15.12+1.20.1_mapped_srg_1.20.1.jar
mods\.connector\spell_power-0.12.0+1.20.1_mapped_srg_1.20.1.jar
```

它里面的签名已经是正常的 MC 类名，**我们的 Forge mod 可以直接编译调用**。做法：

```
D:\ModTest\libs\            ← 把上面两个 jar 拷进来
build.gradle                ← compileOnly files('libs/spell_engine-...jar', 'libs/spell_power-...jar')
```

**关键**：dev 环境（runServer）没有引擎，所以所有碰引擎类的代码集中在
`magic\compat\SpellEngineBridge`，严格执行
`ModList.get().isLoaded("spell_engine")` 判断 → 真代码全在内层 `Impl` 类里，
没引擎时那些指令根本不会执行到，JVM 也就不会去解析那些类。
**已验证**：带上这个桥接类后，dev 服务器照样启动、零类加载错误、自检仍 14/14。

### 路线 B 的实现（`SpellEngineBridge`）

用引擎自己的 API 把法术写进法术书：

```java
SpellContainerHelper.containerFromItemStack(stack)   // 读出物品里的法术容器
SpellContainerHelper.contains(container, spell)       // 已经会了？
SpellContainerHelper.getPool(container).spellIds()    // 池子认不认这个法术
SpellContainerHelper.addSpell(spell, container)       // 加进去
SpellContainerHelper.addContainerToItemStack(container, stack)  // 写回物品
```

逻辑：遍历背包找**池子里认这个法术**的法术书 → 写进去；
没有的话发一本 `ysjx_weapons:lightning_spell_book` 并把法术写进去（法术位上限 8）。
解锁成功会同时给两条消息：点数扣了 + 法术书写好了（或者为什么没写成）。

**结论：先 A（验证 5 个雷法数据在游戏里真能打）→ B 已落地 → C 留阶段 5。**
魔力值在 A/B 阶段只能"施法后记账"（`CombatEvents.SPELL_CAST` 是观察者事件、不可取消），
真正的硬门槛要等 C；不过 B 之后 C 也变容易了 —— 引擎 API 已经能直接调。

## 4.14 魔力值参与施法（2026-09-15，第一版）

**做了什么**：

| 项 | 内容 |
|---|---|
| 魔力消耗表 | `Config.manaCostPerTier` 默认 **20 / 40 / 60 / 100 / 160**（设计文档给的基准是"火球耗 50 魔力"）|
| 施法扣魔力 | 接引擎的 `CombatEvents.SPELL_CAST`，**只处理 `SpellCatalog` 里的 TN-C 法术**，服务端权威 |
| 起手警告 | `action == CHANNEL` 且魔力不足 → 动作栏提示"现在收手还来得及"（只在进度刚开始时提示一次）|
| 真正扣费 | `action == RELEASE` → 扣魔力 + 动作栏显示 `魔力 -20 → 190/210` |
| 不足时 | 魔力清零 + **力竭（虚弱 3 秒）** + 聊天栏说明差多少 |
| 界面 | 法术 tooltip 现在显示「解锁消耗 N 点 / 施放消耗 M 魔力」|
| 自检 | 从 14 项加到 **18 项**（新增：消耗表递增、`spendMana` 够就扣不够就拒绝、恢复量随上限增长、每级都放得起）|

### 魔力恢复模型（2026-09-15 调整）

原来只有固定 `manaRegenPerSecond = 1`：神级法术要 160 魔力 → **要回 160 秒**，等于放不出来。
现在改成 **固定值 + 上限百分比**：

```
每秒恢复 = manaRegenPerSecond + floor(魔力上限 × manaRegenPercentPerSecond%)
默认：1 + 5%  →  上限 210 时 = 11/秒
```

| 法术 | 消耗 | 回一发要多久（11/秒）|
|---|---|---|
| 小闪电 | 20 | ~2 秒 |
| 雷场 | 40 | ~4 秒 |
| 雷击 | 60 | ~6 秒 |
| 雷暴 | 100 | ~9 秒 |
| 天打五雷轰 | 160 | ~15 秒 |
| 空蓝回满 | 210 | ~19 秒 |

这样高等级角色（魔力上限高）的续航自动跟得上高等级法术，不用为每个等级单独调数值。
两个参数都在配置里，想回到"固定 1/秒"把百分比设 0 即可。

代码：`magic\compat\SpellEngineManaHook`（同样分内外两层，没引擎就安静跳过 —— dev 服务器实测输出
`TN-C: SpellEngine not present -> mana hook not registered`）。

**⚠️ 现阶段的诚实边界：这是"事后扣"，不是硬拦截。**
引擎的 `CombatEvents.ItemUse` 只有 `START/TICK/END`、没有 cancel，`SPELL_CAST` 也是观察者 ——
所以魔力不足时**拦不住那一发**，只能扣光魔力 + 力竭惩罚。

**真拦截（"没魔力放不出来"）的可行方案已经查清**，下一步二选一：

1. **Mixin `SpellHelper.attemptCasting`**（推荐）：这个方法是引擎自己的施法前判定
   （返回 `SpellCast$Attempt`，有 `success/failMissingItem/failOnCooldown/none` 工厂）。
   注入后，"是我们的法术且魔力不足"就返回失败 → **引擎自己就拒绝施法**，
   连它自己的报错提示（`showSpellCastErrors`）都能复用。目标类/方法名都是引擎自己的
   （`net.spell_engine.internals.SpellHelper.attemptCasting`），不受 Connector 重映射影响。
   代价：工程要加 mixin 配置 + refmap。
2. **自研施法入口**：自己做法杖，先查魔力再调引擎的
   `SpellHelper.startCasting / performSpell / imposeCooldown`（这几个都是公开的）。
   完全自主，但要自己处理 client/server 施法同步（引擎有 `SpellCastSyncHelper`），工作量大。

## 4.15 魔力硬拦截「没魔力放不出来」（2026-09-15 实现，待整合包内验证）

**做法**：Mixin 注入引擎自己的施法前判定 `SpellHelper.attemptCasting`（**两个重载都注入**），
在 HEAD 处取消并返回 `SpellCast$Attempt.none()` → 引擎自己放弃这次施法。

```
src/main/java/com/tnc/tnc/mixin/SpellHelperManaGateMixin.java   ← 薄适配器（只调 ManaGate）
src/main/java/com/tnc/tnc/mixin/TncMixinPlugin.java             ← 没装引擎就不注入
src/main/java/com/tnc/tnc/magic/ManaGate.java                   ← 纯逻辑：服务端 + 我们的法术 + 魔力不足 → 拦
src/main/resources/tnc.mixins.json                              ← mixin 配置（plugin / JAVA_17 / required:false）
build.gradle                                                    ← jar 清单加 MixinConfigs
```

几个关键设计点：

- **两个重载都注入不会重复拦**：4 参版本内部会调 3 参版本，而在 4 参 HEAD 取消之后，
  3 参那次调用根本不会发生 —— 天然不会重复提示。
- `remap = false`：目标类/方法都是**引擎自己的名字**（不是 MC 的），不需要 refmap，
  这也是它在 Connector 环境下安全的原因。
- **`TncMixinPlugin` 是必需的**：目标是引擎的类，dev 环境没有引擎，硬上会报
  "target class not found"。插件里 `ModList.get().isLoaded("spell_engine")` 为假就跳过注入。
  ✅ 实测：带 Mixin 后 dev 服务器照常启动、自检仍 16/16、无任何 mixin 报错。
- **`/tnc engine` 诊断命令**：打印「引擎已加载 / 硬拦截 Mixin 已生效 / 已拦截施法 N 次（最近：…）」。
  Mixin 的静态块会置 `ManaGate.markMixinApplied()` —— **这是游戏里判断"注入到底成没成"的唯一可靠办法**
  （引擎在但 Mixin 没生效的话，施法仍会扣魔力，但拦不住魔力不足的那一发）。
  **拦截计数器比"注入成功"更硬**：真拦过才 +1，一直是 0 就说明拦截没生效。

**🔧 体检工具升级**（`tools\verify_mod_jar.ps1`）新增三项，专防"静默失效"：
1. `tnc.mixins.json` 是否打进 jar
2. **jar 清单里有没有 `MixinConfigs`**（漏了 = 所有 Mixin 都不会加载）
3. **把 Mixin 里的方法描述符拿去和真引擎 jar 的 javap 输出逐一比对**
   （差一个字符 = 注入静默失败）—— 现在两条 `attemptCasting` 描述符都 ✅ 匹配

> 顺带一提：第 3 项刚写出来时报了两个 PROBLEM，查下来是**我的检查写错了**
> （`javap -s` 输出的是 `descriptor: (...)`，不含方法名），Mixin 本身是对的。

### 4.15.1 拦截判定升级：魔法石成为唯一入口（2026-09-15）

原来只拦"魔力不足"。现在**先看有没有解锁，再看魔力** —— 没在魔法石里解锁的法术，
即使书里有（卷轴时代绑进去的）也放不出来：

```java
public static Decision evaluate(MagicStoneData data, Entry entry, boolean requireLearned) {
    if (requireLearned && !data.hasLearned(entry.id())) return NOT_LEARNED;   // 先查解锁
    if (data.getMana() < entry.manaCost())               return NOT_ENOUGH_MANA;
    return ALLOW;
}
```

- 配置项 `requireLearnedToCast`（默认 **true**）—— 这就是"取代卷轴与法术注册台"的强制手段；
  调试时设 false 放行。
- 判定逻辑抽成**纯函数**（`ManaGate.evaluate`），所以自检能直接测它，不需要玩家实例。
- 自检从 18 项加到 **20 项**：新增「未解锁必拦 / 要求关闭时放行」「解锁后有魔力放行 / 没魔力拦」。
  实测：`要求解锁时=拦，不要求时=放`、`解锁+有魔力=放，解锁+没魔力=拦` ✅
- `/tnc engine` 现在报三个数：**判定执行次数 / 拦下次数 / 其中未解锁次数**。
  「判定次数」是关键 —— 它一直是 0 就说明 Mixin 根本没执行到我们的代码（比"注入成功"信号灯更硬）。

### 4.15.2 🔴 用 dev 桩引擎试出来的致命坑（2026-09-15）

为了在 dev 环境验证 Mixin 能不能注入，我做了一个**桩引擎** fixture
（`tools\dev-fixtures\build-stub-engine.ps1`：提供一个类名/方法签名和真引擎一致的
`net.spell_engine.internals.SpellHelper`，modId 也叫 spell_engine）。
它当场抓到一个**会让游戏启动失败**的 bug：

```
[main/ERROR] [mixin/]: NullPointerException: Cannot invoke "ModList.isLoaded(String)"
                      because the return value of "ModList.get()" is null
  at MixinInfo.<init> → MixinConfig.prepareMixins → MixinProcessor.prepareConfigs
[main/ERROR] [net.minecraft.server.Main/FATAL]: Failed to start the minecraft server
```

**Mixin 准备配置的时机非常早 —— 早到 Forge 的 `ModList` 还是 null。**
我原来的 `TncMixinPlugin.shouldApplyMixin` 里用 `ModList.get().isLoaded("spell_engine")` 判断，
结果抛异常 → 而且**插件里抛异常是致命的**：游戏直接起不来，**不是**安静跳过。
（如果我直接扔给用户测，他那边会开游戏即崩。）

**已改**：干脆不要插件了 —— `tnc.mixins.json` 去掉 `plugin`，保留 `"required": false`，
让 Mixin 自己处理"目标不存在"。这样：
- 整合包（有引擎）→ 正常注入；
- dev（没引擎）→ 目标类不存在，Mixin 自己跳过，日志干净。
✅ 实测：去掉插件后 dev 服务器正常启动、自检 20/20、零 mixin 报错。

⚠️ **桩引擎当时的局限（已在 4.15.4 解决）**：dev 里 mixin 配置是**桩 jar 的清单**声明的，
而 Mixin 会去**声明配置的那个容器**里找我们的 mixin 类 —— 找不到，所以**当时**桩环境下不会真的注入。
这条限制在 4.15.4 里被修掉了（把配置和 mixin 类一起放进桩 jar），**现在桩能证明注入真的发生**。
桩的价值因此从"①抓致命坑 ②证明配置能加载"升级为**"注入本身可验证"**。

（顺带踩到：`job_kill` 只杀掉了 pwsh 包装进程，**java 服务器进程会残留**并锁住世界 →
下次启动报 `另一个程序已锁定文件的一部分`。重启 dev 服务器前记得确认 `run\logs` 之外没有残留 java。）

### 🔴🔴 4.15.2b 「清理残留 java」把用户的游戏杀了（2026-09-15 事故）

**我干过的最严重的一次错事，写在这里防止重犯。**

为了清掉 dev 残留的 java，我跑了一句"按进程名杀全部 java"：

```powershell
Get-CimInstance Win32_Process -Filter "Name like '%java%'" | Stop-Process -Force   # ❌ 绝对不要！
```

**结果把用户正在加载的整合包游戏一起杀了**（PCL 日志：`PID 24444`）。

致命之处在于**它伪装成了一次 mod 崩溃**：

| PCL / 日志现象 | 看起来像 |
|---|---|
| `Minecraft 已退出，返回值：-1` | 崩溃 |
| `Minecraft 返回值异常，可能已崩溃` | 崩溃 |
| `latest.log` 停在 mod 加载中间 | 崩溃 |
| PCL 崩溃分析：**未找到可能的原因** | "查不出来，很可疑" |

而真相是：**没有异常、没有 crash-report、没有 `hs_err_pid`、日志戛然而止** ——
这四条合起来才是"被强行终止"的特征。真正的 mod 崩溃一定会留下
`Failed to start the minecraft server` 或 crash-report。

**铁律**：
1. **永远不要按进程名杀 java。** 只杀命令行里匹配本工程（`D:\ModTest`）的进程。
2. 用 `tools\kill-dev-java.ps1` —— 它按命令行区分 dev / 游戏，遇到像游戏的一律 `[SKIP]`。
3. 判断"崩溃 vs 被杀"：先看有没有 **crash-report / hs_err / 堆栈**，再看退出码是不是 `-1`。
4. **不要一边往 `mods\` 复制 jar 一边让用户启动游戏**（会覆盖正在读的文件）。复制前先确认没有 java 进程。

### 4.15.3 只注入一个重载 + `/tnc gatetest` 实测（2026-09-15）

**① 4.15 里"两个重载都注入"是错的（注释也写反了）。** 用 `javap -c` 读真引擎字节码：

```
attemptCasting(Player, ItemStack, ResourceLocation)            // 3 参：纯转发
    iconst_1
    invokestatic attemptCasting(...,Z)                         //   → 转调 4 参
attemptCasting(Player, ItemStack, ResourceLocation, boolean)   // 4 参：真正的实现
    checkcast SpellCasterEntity / SpellRegistry.getSpell(id)
    isCoolingDown(id) / ammoForSpell(...) / Attempt.success()
```

原文写的是"4 参内部会走 3 参"，**实际是反过来的**。所以：
- 只注入 **4 参**那一个点就覆盖了**所有**调用路径（谁调 3 参都会走到 4 参）；
- 更重要的理由：配置是 `required:false` + `defaultRequire:1`，
  **任何一个注入点没对上 → Mixin 抛错 → 整份配置静默失效**（游戏照常跑，只是魔力再也拦不住施法）。
  **每个注入点都是一条"静默失效"路径，越少越安全。** 现在固定为 1 个，verifier 会断言这个数量。

**② `SpellHelper.attemptCasting` 的返回值确认**（javap）：4 参实现只有读操作
（查注册表 / 查冷却 / 查弹药），**没有副作用** —— 所以可以安全地拿它做探针。

**③ 新增 `/tnc gatetest`：给硬拦截做"实测"，不再只靠静态比对。**
字节码描述符对得上只说明"注入器**应该**能对上"，说明不了"运行时**真的**注入了"。
这条命令在**一次性假玩家**上跑三级阶梯（不碰真玩家任何数据）：

| 步骤 | 条件 | 期望 |
|---|---|---|
| 1 | 魔力**够**、但**没解锁** | 拦住，算"未解锁" |
| 2 | 已解锁、魔力**差 1 点** | 拦住，算"魔力不足" |
| 3 | 已解锁、魔力**刚好够** | **放行** |

第 1 步同时是"注入生效"的**硬证据**：它看 `ManaGate.gateChecks` 涨没涨，
而这个计数器是**注入进去的代码里加的** —— 涨了就说明注入真的发生了，伪造不了。
三步合起来才说明"判定在按魔力和解锁状态区分"（一个永远返回"拦"的实现也能过第 1 步）。

**④ 启动时自动跑一遍**（`MagicStoneDiagnostics.runGateProbe`），结论直接进日志：

```
TN-C mana gate probe [server start]: 3/3 passed (硬拦截生效)
TN-C mana gate probe   [ok]   Mixin 已注入：判定代码真的被执行到 : gateChecks +1
```

这样**不用手动敲命令**，开服日志里就有明确结论。dev 环境没引擎 → 打一行
`skipped (...)` 就过（实测确认不会抛 NoClassDefFoundError）。

**⑤ verifier 加了两个"静默失效"回归断言**（`tools\verify_mod_jar.ps1`）：
- `tnc.mixins.json` **不能有 `plugin`**（4.15.2 那个启动崩溃坑）
- `tnc.mixins.json` 必须是 `required:false`
- mixin 类里 `@Inject` **必须恰好 1 个**

**⑥ 一个实现细节坑**：假玩家是按维度**缓存**的单例，所以探针每次跑之前要显式 `forget()`；
而且新假玩家 `maxMana = 0` → 不先 `setMaxMana()` 的话 `setMana(cost)` 会被夹成 0，
第 2 步就永远"魔力不足"，测试会假通过。

### 4.15.4 ✅ 注入本身在 dev 里被证明了（2026-09-15）

4.15.2 留下的遗憾是"桩环境下不会真的注入，整合包里到底注不注入只能靠整合包验证"。
这一轮把它解决了 —— **dev 里已经能完整证明注入生效**。

**怎么解决的（两个关键点）**

1. **Mixin 从"声明配置的那个容器"里找 mixin 类。** 桩 jar 用清单声明了 `MixinConfigs`，
   所以 Mixin 去**桩 jar 里**找 mixin 类 → 找不到 → 静默不注入。
   **修法**：把 `tnc.mixins.json`（改名 `tnc-devstub.mixins.json`）和 mixin 的 .class 一起打进桩 jar。

2. **但不能直接拷 —— JPMS 会拒绝"分包"（split package）**：

   ```
   java.lang.module.ResolutionException: Module spell_engine contains package com.tnc.tnc.mixin,
   module tnc exports package com.tnc.tnc.mixin to spell_engine
   ```

   ModLauncher 建模块层时，两个模块含同一个包 = 直接起不来。
   **修法**：桩里的那份挪到它独占的包 `com.tnc.xnc.mixin`。改包用的是**等长字节替换**
   （`com/tnc/tnc/mixin` → `com/tnc/xnc/mixin`，都是 17 字符），
   所以 class 文件里 UTF8 常量的长度前缀**不用动**，字节码保持合法，
   而且除了自己的包名之外和真 mixin **逐字节相同**（`@Mixin` 目标、`@Inject` 描述符、方法体全部不变）。
   脚本最后会**断言桩里的描述符和真 mixin 一致**，防止 fixture 悄悄漂移。

3. 顺带修了桩的一个**保真度 bug**：桩原来写成"4 参转调 3 参"，**和真引擎相反**，
   于是它验证的是已经废弃的"两个重载都注入"那套设计。现在桩严格照抄真引擎
   （**3 参 → 4 参**），所以它验证的是"**只注入 4 参，而 3 参的调用者一样被拦到**"。

**实测结果**（`tools\dev-fixtures\build-stub-engine.ps1` + `.\gradlew.bat runServer`）：

```
[mixin/]: Mixing SpellHelperManaGateMixin from tnc-devstub.mixins.json
          into net.spell_engine.internals.SpellHelper          ← 注入真的发生了
TN-C magic stone self-test [server start]: 21/21 passed
TN-C mana gate probe [server start]: 4/4 passed (硬拦截生效)
  [ok] Mixin 已注入：判定代码真的被执行到               : gateChecks +1
  [ok] 魔力充足但未解锁 → 拦住（算在「未解锁」头上）     : blocked +1 · unlearned +1
  [ok] 已解锁但魔力差 1 点 → 拦住（算在「魔力不足」头上） : 魔力 19 / 20 · blocked +1 · unlearned +0
  [ok] 已解锁、魔力刚好够 → 放行（不再拦）               : 魔力 20 / 20 · blocked +0
```

这证明了：**配置能被加载 → 描述符能对上 → 注入真的执行 → 判定按"解锁状态"和"魔力多少"正确区分**。
其中探针走的是**3 参**入口，所以也顺带证实了 4.15.3 那个"只注入 4 参就覆盖全部调用路径"的字节码结论。

**仍然只能靠整合包验证的部分**：Sinytra Connector 把 Fabric 版 SpellEngine 的类交给
Forge 的转换类加载器时，我们的 Forge mixin 能不能同样注入进去。
（`_mapped_srg_1.20.1.jar` 的存在说明 Connector 就是把 Fabric mod 转成 Forge mod 加载的，
理论上同一套机制；但这属于"理论"，要整合包实测。）

**⚠️ 用桩的纪律**：桩只放 `D:\ModTest\run\mods`，**绝不能进整合包**。
每次用完必须 `-Remove`，并确认三个 mods 目录里都没有 `*devstub*`（脚本里也加了"游戏在跑就拒绝动手"的保护）。

### 4.15.5 ✅ 「Connector 环境下能不能注入」—— 已由包内既有 mod 证明（2026-09-15）

4.15.4 说还剩一个理论未知：Sinytra Connector 把 Fabric 版 SpellEngine 交给 Forge 加载时，
我们的 **Forge mixin 能不能注入进 Connector 加载的引擎类**。这一轮从整合包自己的日志里找到了**直接答案**。

**① 同一套机制：Connector 把 Fabric mod 交给 Forge 的 Mixin 服务处理。**

```
[ModLauncher]: Found transformer services : [vaultpatcher, mixin, fml, hotai, mixin-transmogrifier, connector_loader]
[mixin/]: Mixing client.action_impair.MinecraftClientActionImpairing from spell_engine.mixins.json into net.minecraft.client.Minecraft
[mixin/]: spell_engine.mixins.json:... from mod spell_engine->@Inject::doAttack_HEAD_...
                                                ^^^^^^^^^^^^^^ 引擎被当成一个 Forge mod
```

Connector 注册了 `connector_loader` / `mixin-transmogrifier` 两个 ModLauncher 服务，
Fabric mod 的 mixin 由**同一个 `[mixin/]` 服务**应用 —— 也就是同一个类转换管线、同一个模块层。

**② 决定性证据：这个包里已经有 Forge mod 成功注入进引擎类。**

```
[mixin/]: Mixing SpellEngineModMixin         from ysjx_weapons.mixins.json    into net.spell_engine.SpellEngineMod
[mixin/]: Mixing SpellEngineTargetHelperMixin from ysjxteams.mixins.json      into net.spell_engine.utils.TargetHelper
[mixin/]: Mixing SpellProjectileMixin         from spellbladenext.mixins.json into net.spell_engine.entity.SpellProjectile
```

其中 **`ysjxteams-1.0.0.jar` 是 Forge mod**（有 `mods.toml`，清单里 `MixinConfigs=ysjxteams.mixins.json`），
它的配置和我们**几乎一模一样**：

```json
{ "required": false, "package": "com.ysjxteams.mixin",
  "mixins": ["SpellEngineTargetHelperMixin"], "injectors": { "defaultRequire": 0 } }
```

javap 确认它用的是 `@Mixin(value = <引擎类>, remap = false)` + `@Inject`，**没有 refmap** ——
和我们的设计完全一致。**这就是"我们的做法在这个包里可行"的实证，不再只是推理。**

**③ 因此把 `defaultRequire` 从 1 改成 0**（`required:false` 保留）。
注入器对不上时只会打警告、不会报错 —— **游戏永远起得来**。
代价是"静默失效"，但**这个代价已经被两件事抵消**：`/tnc gatetest` 和启动探针每次都会如实报 FAIL。
（这也正是上面那个能正常工作的 `ysjxteams` 的选择。）

**④ 整合包运行时已确认的一环**：

```
TN-C: mana hook registered on SpellEngine SPELL_CAST
```

**目标 ① 的接线在 Connector 真环境下跑通了**（引擎事件 API 可解析、监听器真的挂上）——
这条在 dev 里跑不到（没引擎），此前只有 javap 静态比对。

**⑤ 顺带发现**：被 Connector 转换过的引擎 jar 有 `fabric.mod.json` 但**没有 `mods.toml`**，
却在清单里声明了 `MixinConfigs` —— 说明它不是走 Forge 的 mod 扫描器，而是 Connector 的定位器。
（Connector 还有个 `SplitPackageMerger`，专门合并分包 —— 和 4.15.4 里我们撞到的 JPMS 分包问题是同一类东西。）

### 4.15.6 证据总表（截至 2026-09-15 23:30）

| 目标 | 代码 | dev 实测 | 整合包实测 |
|---|---|---|---|
| ① 施法扣魔力（服务端权威） | ✅ | 算术边界 5 条自检；注册路径可跑 | ✅ **钩子已注册到 SPELL_CAST**；事件触发后扣魔待进世界 |
| ② 魔力不足的反馈 | ✅ | 拦截分支被真实执行（三级阶梯） | 待进世界 |
| ③ 施法前真拦截（硬拦截） | ✅ | ✅ **Mixin 确实注入 + 阶梯 4/4** | ⏳ 待进世界；**可行性已由 `ysjxteams` 证明** |
| ④ 数值表 + GUI 显示消耗 | ✅ | 数值表自检通过 | 待 GUI 目视 |

**唯一还剩的未知**：进世界后 `SpellHelper` 被加载的那一刻，我们的 mixin 是否真的应用。
一条命令就能读出结论（`tools\check-pack-verdict.ps1`）。

### 4.15.8 ✅✅ 整合包实测通过：硬拦截在 Connector 环境下真的生效了（2026-09-15 23:34）

**这是整个「魔力参与施法」的收官证据。**

```
[23:34:50.137] [Server thread/DEBUG] [mixin/]:
    Mixing SpellHelperManaGateMixin from tnc.mixins.json
    into net.spell_engine.internals.SpellHelper
[23:34:50.168] TN-C magic stone self-test [server start]: 21/21 passed
[23:34:50.202] TN-C mana gate probe [server start]: 4/4 passed (硬拦截生效)
  [ok] Mixin 已注入：判定代码真的被执行到                  : gateChecks +1
  [ok] 魔力充足但未解锁 → 拦住（算在「未解锁」头上）        : blocked +1 · unlearned +1
  [ok] 已解锁但魔力差 1 点 → 拦住（算在「魔力不足」头上）    : 魔力 19 / 20 · blocked +1 · unlearned +0
  [ok] 已解锁且魔力刚好够 → 放行（不再拦）                  : 魔力 20 / 20 · blocked +0
```

加上更早的 `TN-C: mana hook registered on SpellEngine SPELL_CAST`，
四个目标**全部拿到整合包级证据**：引擎事件挂上了、Mixin 注入进去了、
判定确实按"解锁状态 + 魔力多少"区分、界面数值表已就位。

**⚠️ 一个把我自己坑了一次的细节**：`Mixing ... into ...` 是 **DEBUG 级**，只写进 `debug.log`，
**不写进 `latest.log`**。`check-pack-verdict.ps1` 第一版只看 `latest.log`，
于是明明 4/4 通过却报了 `FAIL`。判据要用**两个**来源：
`debug.log` 里 Mixin 自己那行（直接证据）+ 探针第 1 条断言（`gateChecks` 涨了 = 注入的代码被执行到，
这个伪造不了）。**先看探针结论，别只看日志 grep。**

### 4.15.9 证据总表（最终）

| 目标 | 代码 | dev 实测 | 整合包实测 |
|---|---|---|---|
| ① 施法扣魔力（服务端权威） | ✅ | 算术 5 条边界自检 | ✅ 钩子已注册到 `SPELL_CAST` |
| ② 魔力不足的反馈 | ✅ | 拦截分支被真实执行 | ✅ 拦下路径被执行（探针 2/3 步） |
| ③ 施法前真拦截（硬拦截） | ✅ | ✅ 桩引擎注入 + 阶梯 4/4 | ✅ **Mixin 真的注入 + 阶梯 4/4** |
| ④ 数值表 + GUI 显示消耗 | ✅ | 数值表自检通过 | ✅ 自检 21/21 |

**还没做过的事（都不是阻塞项）**：
- 在游戏里真放一次法术，肉眼看动作栏的 `魔力 -20 → …`（服务端扣魔已由注册+算术覆盖）
- 装含 `ManaCharge` 重构的新版 jar（自检 21 → 26 条）：`tools\install-to-pack.ps1 -Wait`
- GUI 里目视确认每个法术的消耗数字

---

### 4.15.11 🔎 `performSpell` 的内部结构（读字节码得到的，很关键）

用户反馈"施法后魔力没扣"。读引擎 `performSpell` 的字节码，得到两件重要事实：

```
performSpell(Level, Player, ResourceLocation, List<Entity>, Action, float):
  33: Player.getMainHandItem()
  42: invokestatic attemptCasting(Player,ItemStack,ResourceLocation)   ← 引擎自己又判一次
  49: Attempt.isSuccess()
  52: ifne 56
  55: return                        ← 不成功就直接不放，事件也不会触发
  ...（扣弹药 / 算命中 / 粒子音效 / 上冷却）...
 902: getstatic CombatEvents.SPELL_CAST
 905: Event.isListened()
 908: ifeq 941                      ← 没人监听就跳过
 938: Event.invoke(Consumer)        ← 事件在**所有效果处理完之后**才播报
 941: return
```

**① 硬拦截其实有"两道"**：`performSpell` 内部会再调一次 `attemptCasting`。
所以我们拦 `attemptCasting` 等于同时守住了"起手判定"和"真正放出去之前"两道门 —— 比预想的更稳。

**② 被拦下时事件根本不会触发**（55 行提前 return），所以"拦住了"和"没扣魔力"是一致的、不是 bug。

**③ 那"魔力没扣"最可能是什么**：法术 id 不是 `tnc:*`。
作者的雷系法术书池子里一共 **10 个法术**（作者的 5 个 + 我们的 5 个），
随手按到槽位 1 很可能放的是**作者的法术**；而 TN-C 故意只对自己的法术扣魔力（`entry == null` 就 return）。

**为了不再靠猜**，这一轮加了：
- `SpellEngineManaHook` 里记 `seen / ours / otherMod` 三个计数 + `lastSeen` 一行，
  `/tnc engine` 直接显示"施法事件 N 次 · TN-C 法术 M 次 · 别家法术 K 次"，并在 `seen>0 && ours==0` 时明确提示
- 每次施法在日志里打一行 `TN-C: SPELL_CAST spell=... action=... caster=... ours=true/false`
- **`/tnc cast <法术id>`**：直接调 `performSpell` 放指定法术，不看法术书、不管键位槽位。
  走的是和真实施法完全相同的路径（含那道 `attemptCasting`），所以一条命令能同时验"硬拦截"和"扣魔力"

### 4.15.12 法术书的发放逻辑（bug 修复）

用户反馈"每解锁一个法术就给一本新书"。原因是 `SpellEngineBridge` 原来的写法：
遍历背包，**只往"池子认这个法术"的书里写**（`getPool(container).spellIds().contains(spell)`），
只要 `containerFromItemStack` 对作者那本书返回 null、或它自带池子的名字/内容和我们以为的不一样，
这一轮就被判成"没有合适的书" → 走 `giveNewBook` → 每解锁一次发一本。

**改法**：按**物品**匹配 —— 身上有 `ysjx_weapons:lightning_spell_book` 就往里写
（没有 container 就补一个）；池子只用来决定"新书默认认哪些法术"，不再当"能不能写"的门槛。
玩家背包 `getContainerSize()` 是 41（36+4+1），所以副手那本也能找到。

---

### 4.16 🔄 设计改向：抛弃法术书，改成「法杖 + 魔法石」（2026-09-15 用户拍板）

**用户原话**：「抛弃魔法书的概念吧，你既然都有魔法石了，只要你有法杖，你就可以按数字释放魔法，
不然的话你这样魔法书逻辑太难弄了，你可以把这个逻辑绑到魔法石上面去。」

这个判断是对的。法术书那套是我引入的多余一层，而且**那本书是作者的**（池子里有他自己的 5 个法术），
玩家随手按到别人的法术 → TN-C 不扣魔力 → 看起来像坏了。更要命的是书会丢、内容会少、槽位和"第几个已解锁法术"对不上。

**新模型**：

```
魔法石（权威，隐藏数据）  ──单向同步──▶  法杖（派生道具，SpellContainer）
        │                                      │
        ├─ 已解锁哪些法术 ─────────────────────┘
        └─ 你手上得拿着法杖才能放（requireWandToCast）
```

- 登录 / 解锁 / `/tnc wand` → 把"已解锁集合"同步进法杖；**没有法杖但解锁过法术就补发一根**
- 法杖认**我们自己的法术池** `tnc:tnc_lightning`（`data/tnc/spell_pools/tnc_lightning.json`，只含 5 个 tnc 雷法）
  → 槽位顺序 = 目录里等级的顺序，可预期
- 顺手把作者的 `ysjx_weapons:lightning` 池**改回只有他原来的 5 个**（我们的法术以后只从法杖出）
- 新增判定 `Decision.NO_WAND`（手上没法杖就拦住）+ 计数器 `noWandCount`

### 🔴🔴 4.16.1 ~~关键发现：引擎的快捷栏是按 `instanceof SpellBookItem` 认物品的~~ —— **这条结论是错的，见下方更正**

> ⚠️ **2026-09-16 更正**：下面这段的结论**完全反了**，而且它直接把法杖做坏了。
> `ifne` 是"成立就跳走"，不是"成立就走这里"。保留原文是为了留下教训：
> **读到 `ifne`/`ifeq` 一定要确认跳转目标的含义，光看"某个 instanceof 出现在字节码里"就下结论会南辕北辙。**
> 正确结论见 §4.16.6。

**（以下是当初的错误推理，勿信）**：我最初的实现是"一个普通 `Item` + 往它 NBT 里写 `SpellContainer`"，
理由是 `SpellContainerHelper.containerFromItemStack()` 读 NBT、与物品 Java 类型无关。
读引擎 `client.input.SpellHotbar` 的字节码时，我看到：

```
 77: instanceof net/spell_engine/api/item/trinket/SpellBookItem
 90: getfield SpellContainer.spell_ids
```

于是**错误地**推断"物品必须是 SpellBookItem 才会被取用"，
并把法杖改成继承 `SpellBookVanillaItem`。**结果正好相反 —— 这一改恰恰让快捷栏彻底不认它。**

**但这就带来一个加载顺序问题**：这个类 `extends` 引擎类，dev 环境（没引擎）一加载就 `NoClassDefFoundError`，
连物品注册都过不去。解法还是工程里那个老套路 —— **把 `new` 关在嵌套类里**：

```java
public static Item createWandItem() {
    if (!enginePresent()) return new Item(...);   // 没引擎：退化，保证注册不炸
    return EngineWand.create();                  // 有引擎：才加载 MagicWandItem
}
private static final class EngineWand { static Item create() { return new MagicWandItem(); } }
```

嵌套类只在第一次被用到时加载，而那时已经确认引擎存在了。javap 核对过：
`SpellEngineBridge$EngineWand` 只引用 `MagicWandItem`，不直接引用任何引擎类。

**玩家角度这只是"一根法杖"** —— `SpellBookItem` 是引擎内部实现的名字，不暴露。

> ⚠️ 以上整段（`MagicWandItem extends SpellBookVanillaItem` + 嵌套类隔离）**已在 §4.16.6 被整体删除** ——
> 它建立在一个读反了的字节码判断上。现在 `createWandItem()` 就是一句 `new Item(...)`，
> 既不需要嵌套类隔离，也没有引擎依赖。

### 4.16.6 ✅✅ 真正的结论：法杖必须是**普通 Item**（不是 `SpellBookItem`）

**用户反馈"还是没有数字键"之后，重新逐条读 `SpellHotbar.update()` 的完整字节码，看到了跳转目标：**

```
74: aload 5 ; invokevirtual ItemStack.getItem()
77: instanceof net/spell_engine/api/item/trinket/SpellBookItem
80: ifne 516          ← ★ 是 SpellBookItem 就"跳到 516"，跳过下面整段
83: aload 6
85: ifnull 516        ← 没有容器也跳过
88: getfield SpellContainer.spell_ids        ← 只有"不是 SpellBookItem 但有容器"才读这里
95: ... stream().map(→SpellInfo).filter(...).toList()
```

**`ifne 516` = "成立就跳走"。** 所以快捷栏是**两条互斥的路**：

| 主手物品 | 快捷栏从哪取法术 |
|---|---|
| **是** `SpellBookItem` | 另一条分支（偏移 516 之后；要靠池 / `SpellBooks` 那一套登记） |
| **不是** `SpellBookItem`，但有 `SpellContainer` | **直接读容器的 `spell_ids`** |

作者的杖 `extends StaffItem`（**不是** `SpellBookItem`）+ 自带 `spell_assignments` → 走第二条，所以一直好用。
我把法杖做成 `SpellBookItem`，正好被 `ifne` 跳开 → **数字键完全没有**。

**最终实现（三处配合，缺一不可）**：

1. **法杖 = 普通 `new Item(...)`**（`createWandItem()` 一行，无引擎依赖、dev 也能注册）
2. **`data/tnc/spell_assignments/magic_wand.json`**：让引擎（含客户端）知道这个物品是法术载体；
   内容照抄作者那根能用的杖的形状（`is_proxy: true` + 5 个雷法），作为**兜底**
3. **NBT 容器**（`is_proxy: false`，内容 = 已解锁法术按等级排序）→ 覆盖兜底，实现"魔法石驱动槽位"
   - 写完必须 **`player.inventoryMenu.broadcastChanges()`** 强制同步：
     **快捷栏跑在客户端**，就地改服务端 NBT 不保证推过去

**实测确认（2026-09-16 00:39）**：用户连续施放 10 次，日志逐条：

```
TN-C: SPELL_CAST spell=tnc:spark            action=RELEASE caster=Cockr0Ach ours=true
TN-C: SPELL_CAST spell=tnc:lightning_field  action=RELEASE caster=Cockr0Ach ours=true
TN-C: SPELL_CAST spell=tnc:lightning_strike action=RELEASE caster=Cockr0Ach ours=true
self-test 26/26 · mana gate probe 6/6
```

**法杖被引擎认了、数字键能放了、钩子每次施法都收到 → 魔力在扣。**

**这一串坑的教训（比结论更值钱）**：
1. **`ifne`/`ifeq` 要看跳转目标** —— 光看到"字节码里有个 instanceof"就下结论，会得出完全相反的答案。
2. **引擎的契约不全在 Java 签名里**：`spell_assignments`（物品↔容器的数据文件）在 API 上完全看不见，
   少一个文件就静默失效。
3. **要抄就抄一个已经能工作的真实 mod**：作者那三根杖的数据目录（`spell_assignments`）和物品基类
   （`StaffItem`）就是最好的规格说明书。
4. **客户端/服务端要分清楚**：快捷栏、HUD、按键都在客户端；服务端改完 NBT 不主动推，客户端就看不到。

### 4.16.2 顺带修好的：`requireWandToCast` 会把启动探针测坏

探针用的是**假玩家**，而假玩家手上没有法杖 —— 加了法杖要求之后，
"魔力够就该放行"那一步会被"没法杖"这个原因拦掉，看起来像魔力判定坏了。

所以探针改成先测一条新断言「**没拿法杖 → 拦住**」，再给假玩家塞一根法杖，
之后才测解锁/魔力那三级。这样每一条失败都能指到正确的原因上。

---

### 🔴🔴 4.16.4 法杖不被引擎认（没有数字键）—— 缺的是 `spell_assignments` 数据文件

**现象**：手里拿着 TN-C 的法杖，**不出现法术快捷栏、按数字键没反应**；换成作者的法杖就好了。

**根因**（读引擎 `SpellRegistry` 字节码找到的）：

```java
public static void loadContainers(ResourceManager rm) {
    String folder = "spell_assignments";                     // ← 物品↔法术容器的关联
    Map<ResourceLocation, Resource> files = rm.listResources(folder, ...);
    // 文件名 = 物品 id，内容 = 一个 SpellContainer（JSON）
    containers.put(itemId, container);
}
public static final Map<ResourceLocation, SpellContainer> containers;
public static SpellContainer containerForItem(ResourceLocation itemId);
```

也就是说"这个物品能装法术"这件事，引擎是**从 `data/<命名空间>/spell_assignments/<物品名>.json` 读的**，
而且这份数据会**同步到客户端**。我们的法杖只有 NBT，没有这个文件 →
**客户端根本不知道它是法术载体 → 不给快捷栏 → 按键全无反应，而且完全是静默的。**

作者的杖有这个文件，所以我们用他的杖时一切正常：

```
data/ysjx_weapons/spell_assignments/lightning_staff.json
{ "is_proxy": true, "spell_ids": [ "ysjxspells:leiji_basic" ] }
```

**修法**：新增 `src/main/resources/data/tnc/spell_assignments/magic_wand.json`，
内容照抄那个已经被证明能用的形状（`is_proxy: true` + 我们的 5 个雷法），
并把 `writeContainer` 里的 `is_proxy` 也从 false 改成 true（`SpellContainer.isValid()` 对 proxy 直接返回 true）。

**教训**：这类"缺一个数据文件就静默失效"的机制，光读 Java API 是看不出来的 ——
**要去翻一个已经能工作的真实 mod 的数据目录，照着它的形状抄。**
（这条和 4.16.1 的 `instanceof SpellBookItem` 是同一类问题：引擎的契约不全在 Java 签名里。）

**顺带确认（这一轮日志）**：法杖方案的其他部分其实都通了 ——
`self-test 26/26`、`mana gate probe 6/6`（含新增的「没拿法杖→拦住」）、
`gave a new magic wand (2 spells)`、`synced 1 magic wand(s) ... 3 spell(s)`，
而且**用户真的放出了 `tnc:lightning_field`，钩子收到了 `SPELL_CAST ... ours=true`** ——
说明目标①那条"引擎会播报我们的法术"已经实测成立。

---

### 4.16.5 工具表（法杖阶段新增/修好的）

| 工具 | 作用 |
|---|---|
| `tools\check-pack-verdict.ps1` | 一条命令读出整合包判定。处理四个坑：日志是 **GBK**、游戏运行时日志被**独占锁**（要用 `FileShare.ReadWrite`）、**区分"被杀"还是"崩溃"**、以及 Mixin 那行在 **debug.log 而非 latest.log** |
| `tools\install-to-pack.ps1` | 安全安装 jar：**有 java 在跑就拒绝**（`-Wait` 可等），装完自动 verify |
| `tools\kill-dev-java.ps1` | 已修：原来用 `BootstrapLauncher` 当"游戏标志"，可 **Forge dev 服务器也用它**，导致残留 dev JVM 被误判放过（→ 世界存档被锁）。现在按 `--launchTarget` 判定 |

---

### 4.17 魔力条 HUD 版面：按**包内实际坐标**重排（2026-09-16）

用户反馈：「魔力条把血条完全挡住了」「聊天框的暗底把紫色那条占了」
「中心的魔法石被遮住了」。查下来是三件独立的事：
**坐标算错了**（血条位置）、**一个多余的 return**（开界面就消失）、
**深度缓冲**（聊天框之后画的东西直接被丢掉）—— 见 4.17.1 / 4.17.3 / 4.17.5。

#### 4.17.1 这个包的状态条不在原版位置（实测）

包里的血条/饱食度被 `whisperingstatusbar-1.3.jar` 换成了 11 像素高的贴图条，
于是整条往上长了一行。**拿用户截图逐像素扫出来的**（截图 856x512 = GUI 428x256，
GUI 缩放 2，所以 GUI y = 物理 y / 2）：

| GUI y | 画的是什么 | 是谁画的 |
|---|---|---|
| `高-61 .. 高-51` | 护甲条（**左半边**，右半边空着） | 包（whisperingstatusbar） |
| `高-50 .. 高-40` | 血条（左）/ 饱食度（右） | 包 |
| `高-49 .. 高-40` | **聊天框最下面一行的暗底** | 原版 `ChatComponent` |
| `高-39` | 原版血条锚点（这个包里没人用） | —— |
| `高-22` | 快捷栏 | 原版 |

**教训**：原版血条在 `高-39`，但这个包把它画到了 `高-50`。
我们原来按原版算，把魔力条画在 `高-49` —— 正好横在血条身上，**全盖住了**。
所以现在摆在 `高-61`（饱食度正上方一行 = 护甲条那一行的右半边，那里本来就是空的），
和**饱食度**同宽（80）同右边界（`宽/2 + 91`）。

#### 4.17.2 聊天框暗底是"一条横带"，正好压在血条那一行

从 `ChatComponent.render` 读出来的（不是猜的）：

```java
int l  = guiHeight();                        // 256
int i1 = Mth.floor((l - 40) / scale);        // = 216
// 每行消息（j2 = 0 是最新那条）：
int i4 = i1 - j2 * lineHeight;               // 最新一行 i4 = 216，lineHeight = 9
pose.translate(4, 0, 0);                     // 整块往右挪 4
fill(-4, i4 - 9, getWidth() + 8, i4, k3 << 24);   // => GUI x 0..332, y 207..216
```

所以**最新那条消息的暗底 = GUI `x 0..332`、`y 207..216`，而且是全屏宽度**
（`getWidth()` 默认 320，`chatWidth` 选项拉满时更宽）。
血条/饱食度（`206..216`）正好长在带子里 —— 这就是为什么一有聊天消息它们会变暗；
而护甲条（`195..205`）在带子**外面**，所以它一直不暗。

魔力条挪到 `高-61`（= `195..205`）以后，**整条都在带子外面**（带宽最多到约 `宽/2+12`），
所以条子本身安全了。但**图标在屏幕正中间，躲不开**（带宽 `0..332` 正好盖住中线），
它的下场见 4.17.5 —— 那才是"被遮住"的真身。

> 预览图：`docs\previews\mana_bar_layout.png`（拿用户截图合成的前后对比）

#### 4.17.3 🔴 真正的 bug：`if (minecraft.screen != null) return;`

用户说"血条、饱食度一直看得见、就是半透明，只有魔力条整条没了"。
原因就是我们自己写的那个判断。**原版开着界面时照样画 HUD**：

```java
// GameRenderer.render
if (!this.minecraft.options.hideGui || this.minecraft.screen != null) {
    this.minecraft.gui.render(guigraphics, p_109094_);   // ← HUD（含我们的条、含聊天框）
}
...
} else if (this.minecraft.screen != null) {
    ForgeHooksClient.drawScreen(this.minecraft.screen, ...);   // ← 界面在这之后才画上去
}
```

顺序是：`Gui.render`（我们的 `RenderGuiEvent.Post` 在这里面，**在聊天框之后** ——
`chat.render` 是 `Gui.render` 的倒数第二件事）→ 然后才画界面。
所以：

- 按 T 开聊天框时，血条/饱食度被"聊天框自己那张暗底"压暗，但**一直在**；
- 我们写了 `screen != null` 就 return，于是**全场只有魔力条消失**，
  玩家一眼就看出是 bug。

**改法**：删掉 `screen != null` 判断，只留 `hideGui`（F1）。
界面本来就会盖在我们上面，不用我们操心。

#### 4.17.4 顺便记住的两条

1. **HUD 是画在界面底下的，不是不画**（`hideGui || screen != null` 都要画）。
   `RenderGuiEvent.Post` 画在界面之下 —— 想挂 `ScreenEvent.Render.Post` 让条子
   盖住聊天界面是错的，那会把聊天文字一起挡住。（但"盖过聊天框**暗底**"是另一回事，
   能做到也应该做，见 4.17.5。）
2. 改条子位置只需动 `MagicStoneHud` 顶部那几个常量：
   `PACK_BAR_ROW_HEIGHT`（步长 11）、`HUNGER_ROW_TOP_MARGIN`、`MANA_BAR_TOP_MARGIN`、
   `MANA_BAR_X_OFFSET`、`ICON_BOTTOM_MARGIN`。

---

#### 4.17.5 🔴🔴 真凶：聊天框暗底把我们的像素**从深度缓冲里丢掉**了（2026-09-16）

用户第二张截图（1920x1080，GUI 缩放 3 → GUI 640x360）：条子在右边好好的，
**中间的魔法石整个不见**。

先排除"变暗"这个可能：逐像素扫魔法石该在的位置
（GUI `312..328, 296..312` → 物理 `936..984, 888..936`），
再把该位置反推的几种颜色全图搜一遍 —— 宝石描边 `0xB9A7FF`、填充 `0x9C86F5`、
高光 `0xF0E8FF`，以及它们被压暗 50% / 60% / 75% 后的样子：

| 颜色 | 全图命中 |
|---|---|
| `0x9C86F5`（宝石填充） | **0** |
| `0xF0E8FF`（宝石高光） | **0** |
| `0xB9A7FF`（描边） | 1469，**全在魔力条矩形里**（物理 996..1232, 897..926） |
| 上面各色 ×50%/40%/25% | **全是 0** |

**结论：不是被压暗，是根本没画上（被丢了）。** 而且用户上一轮说的
"魔法石被遮到了一半、就少了一半"，少的正好是落进暗底矩形的那一半 —— 完全吻合。

原因（读 `RenderType` / `RenderStateShard` 源码确认，不是猜）：

```java
// RenderType.java L144
private static final RenderType GUI = create("gui", POSITION_COLOR, QUADS, 256,
    CompositeState.builder()
        .setShaderState(RENDERTYPE_GUI_SHADER)
        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
        .setDepthTestState(LEQUAL_DEPTH_TEST)      // ← 深度测试
        .createCompositeState(false));
// RenderStateShard.java L567 / L574 —— CompositeState 的两个默认值
private DepthTestStateShard depthTestState = LEQUAL_DEPTH_TEST;
private WriteMaskStateShard writeMaskState = COLOR_DEPTH_WRITE;   // ← 还写深度
```

而 `ChatComponent.render` 里，暗底画在 `z = +50`、文字画在 `z = +100`
（连着两次 `pose().translate(0, 0, 50)`）。于是：

| 谁 | 什么时候画 | z | 结果 |
|---|---|---|---|
| 包内血条/饱食度 | 聊天框**之前** | 0 | 被暗底蒙一层 → 变暗但看得见 |
| 聊天框暗底 | —— | **+50** | 写深度 |
| 聊天文字 | —— | **+100** | 写深度 |
| **我们的条/宝石** | 聊天框**之后** | 0 | 落在暗底矩形里的像素 **LEQUAL 失败 → 丢弃（全无）** |

注意 `Gui.java:323` 的 `chat.render(...)` 就在 `Gui.render` 结尾附近，
而 Forge 的 `RenderGuiEvent.Post` 在那之后 —— 所以我们比聊天框**晚**画，
这就是"晚画反而被吃掉"的原因。

**修法（一行）**：把我们的绘制整体抬到 `z = +75`
（`graphics.pose().translate(0, 0, HUD_Z)`）：

- **75 > 50** → 压过聊天框暗底，条子和宝石永远看得见；
- **75 < 100** → 仍在聊天文字之下，长消息的文字会盖在我们上面，绝不挡字；
- 位置一个都不用动。

> ⚠️ 别再犯的误判：上一轮把这个现象当成"配色不够亮、被暗底吃掉了"，
> 于是去提亮颜色 —— 方向完全错了，颜色再亮也会被丢掉。
> **教训：HUD 元素"整块不见"时，第一反应应该是查 z / 深度，而不是查颜色。**

---

### 4.18 魔力条换成贴图条「thundermagicbar」（2026-09-16）

用户自己拿来的两张 168x26 贴图（`thundermagicbar_empty.png` / `thundermagicbar_fill.png`）：
左边画好了一颗魔法石、中间一条轨道、右边一道闪电，外面套着和血条同款的金框。
所以代码里只剩两件事：**按魔力比例把中间那段轨道填起来**、**把数字压上去**。

#### 4.18.1 贴图规格（逐像素量出来的，改图要照着来）

| 参数 | 值 | 说明 |
|---|---|---|
| 画布 | **168 x 26** | 和包内 `health_*.png` / `food_*.png` **完全同规格** |
| 游戏里画的尺寸 | **84 x 13** | 包内血条/饱食度也是按 **0.5 倍**画的（量过：饱食度那条在屏幕上 84 宽 13 高） |
| 轨道（会涨的那段） | **u 42..148, v 14..19** | 左边 0..41 是宝石+金框、右边 149+ 是闪电，按比例裁剪时不能碰 |
| empty vs fill | 只差轨道颜色 | fill 的轨道是紫的（约 `v14..19` 那条带），empty 是深棕 |

#### 4.18.2 三个必须用对的地方

1. **裁剪 + 缩放要用带 `uWidth/vHeight` 的 blit 重载**
   `blit(loc, x, y, 宽, 高, u, v, uWidth, vHeight, texW, texH)` ——
   它允许"目标尺寸"和"取样尺寸"不同，所以一刀就能**既裁轨道、又维持 0.5 倍**。
   （用上一版那个"目标宽 = 取样宽"的重载，一裁剪会把左边的宝石和右边的闪电一起切掉。）
2. **填充只在 `u 42..148` 里按比例长**：`filled` 按屏幕像素取整（= 半个贴图像素），
   取样宽取 `filled * 2` —— 正好 0.5 倍，而且永远画不出轨道之外。
3. **位置**：左边界 `宽/2 + 11`、框高 13，紧贴饱食度贴图框正上方 →
   `高-63 .. 高-50`，恰好落在包内**护甲条那一行的右半边**（那里本来就是空的）。

> 预览图：`docs\previews\mana_bar_thunder.png`
> 上一版自己画的 `mana_empty.png` / `mana_fill.png` 和生成脚本 `tools\mana_bar_texture_gen.ps1`
> 都留着了，想改回代码画的那版只需把 `MANA_BAR_EMPTY/FILL` 两个路径换回去。

> ⚠️ **授权待确认**：这两张图是从 `D:\查看\whisperingstatusbar-贴图\` 拿来的，
> 和 `whisperingstatusbar`（`All Rights Reserved`）那套贴图**同规格、同风格**；
> 但扫遍整合包所有 mod jar，**没有任何 mod 自带 `thundermagicbar*` 这两个文件名**
> （所以也可能是用户自己画/从别处拿的）。如果它确实出自那个 mod，放进我们这个**公开仓库**
> 就等于转发 ARR 素材 —— 需要用户确认来源；不行的话用自己的生成脚本画一套等价替换。

---

# 五、核心技术知识库

> 这一节是花了一整晚用 `javap` 读编译类、拆 jar、试错换来的。别重复踩。

## 5.1 SpellEngine（法术引擎）

**法术是纯数据**：`data/<namespace>/spells/<name>.json`

**顶层字段**（来自 `javap net.spell_engine.api.spell.Spell`）：
```
school (SpellSchool)  range (float)  group (String)  learn
mode  cast  item_use  arrow_perks  release  impact[]  area_impact  cost
```

**动作类型只有 6 种**（`Spell$Impact$Action$Type`）：
```
DAMAGE  HEAL  STATUS_EFFECT  FIRE  SPAWN  TELEPORT
```

**TELEPORT 的完整字段**（`javap Spell$Impact$Action$Teleport`）：
```
mode                     FORWARD | BEHIND_TARGET
forward.distance         float      ← 写死的浮点数，不支持系数/属性缩放！
behind_target.distance   float
required_clearance_block_y  int
intent                   HARMFUL | BENEFICIAL | HELPFUL
depart_particles / arrive_particles
```

**🔴 关键限制**：`distance` 不支持缩放 → **"等级越高越强"必须靠"一个等级一个法术文件"实现**。作者就是这么干的（`airslash` / `airslash_2` / `airslash_3`）。

**学派**（12 个，来自 `spell_power`，数据驱动）：
```
AIR ARCANE ARMOR BERSERKER_MELEE EARTH FIRE FROST
HEALING LIGHTNING PHYSICAL_MELEE PHYSICAL_RANGED SOUL WATER
```

**可用素材统计**（已排除闭源 `ysjx*`）：
| 素材 | 数量 |
|---|---|
| 纯数据法术（可照抄）| **183 个** |
| 施法动画 | 68 个 |
| 状态效果 | 60 种 |
| 法术音效 | 254 个 |
| 学派 | 12 个 |
| 动作类型 | 6 个 |

**法术图标**：SpellEngine 会在 `assets/<namespace>/textures/spell/<法术名>.png` 找图标（我们还没做，日志里有 FileNotFoundException）

## 5.2 SpellAnvil（法术注册台）

- mod：`spellanvil`（`org.hiedacamellia.spellanvil`）
- 方块 ID：**`spellanvil:anvil`**
- **用物品标签识别内容**：
  - `data/spellanvil/tags/items/scroll.json`（493 条，在 jar 里）
  - `data/spellanvil/tags/items/book.json`（18 条）
- **往标签里加自己的物品**：在 `kubejs/data/spellanvil/tags/items/scroll.json` 写 `{"replace": false, "values": [...]}`

**KubeJS 事件**（`SpellAnvilEvents`）：
```javascript
SpellAnvilEvents.getItemStackSpells(e => { if (e.itemStack == 'x') e.addSpell('ns:spell') })
SpellAnvilEvents.validateSpellPool(e => { if (e.itemStack == 'x') e.setPool('ns:pool') })
SpellAnvilEvents.getSpellCost(...)
SpellAnvilEvents.spellRemove(...)
```

## 5.3 数据包覆写 mod 数据（**超强能力**）

**同路径文件放在 `kubejs/data/<mod命名空间>/...` 即可覆盖 mod jar 里的版本。**

作者自己就这么干 —— 他覆写了 **132 个 mod 法术**：
```
spellbladenext 78 / elemental_wizards_rpg 18 / wizards 15 / paladins 9 / rogues 8 / archers 4
```

**推论**：连闭源 `ysjxspells` 的 385 个法术，**外壳也能覆写**（学派/冷却/耗材/动画/音效/粒子/目标）。
只有 `custom_impact: true` 的实际效果在 Java 里改不了 —— 但你可以**覆写时去掉 `custom_impact`，换成纯数据效果**，那个法术就完全归你了。

## 5.4 KubeJS

- **自动加载目录下所有 `.js`** → 新增自己的文件，不用改作者的
- 目录：`startup_scripts/`（注册物品）`server_scripts/`（逻辑/配方/掉落）`client_scripts/`（tooltip）
- `kubejs/data/` **会被当数据包加载**（namespace = 子文件夹名）
- 日志验证：`Loaded 25/25 KubeJS startup scripts with 0 errors and 0 warnings`

**卷轴命名约定**（作者的 `Scroll.js`）：
```javascript
function getScrollItemId(spellId) {
    const [namespace, path] = spellId.split(':');
    return `${path}_scroll`;     // 'tnc:blink_1' → 'blink_1_scroll'
}
```

## 5.5 车万女仆 TLM 1.5.0（模型包）

**两套格式并存**：
| 格式 | 标志 | 骨骼 |
|---|---|---|
| 旧格式 | 无 | 简单（`head`/`armLeft`）|
| **新 GeckoLib 格式** | `"is_gecko": true` | **93 根**（含手指/眼皮/头发/裙子）|

**模型包结构**：
```
tnc_pet/                                  ← 资源包根
├── pack.mcmeta
└── assets/tnc_pet/
    ├── maid_model.json                   ← 清单
    ├── models/entity/songzhikun.json
    └── textures/entity/songzhikun.png    ← 按模型文件名约定
```

**清单格式**：
```json
{
  "pack_name": "...", "author": ["..."], "version": "1.0.0",
  "model_list": [{
    "model_id": "tnc_pet:songzhikun",
    "is_gecko": true,
    "animation": ["touhou_little_maid:animation/maid.animation.json"],
    "render_entity_scale": 0.65
  }]
}
```

**游戏内换模型**：空手右键女仆 → **"Change Skin"** 按钮（`gui.touhou_little_maid.button.skin`）
**模型切换器方块**（`touhou_little_maid:model_switcher`）是**服务器展示用**的，自己玩不需要。

**TLM 依赖**：只要求 Forge≥46 + MC 1.20~1.20.2，**不内嵌 GeckoLib**，需要外部安装。

## 5.6 Blockbench

- **Java Block/Item** = 方块/物品；**Bedrock Entity** = 生物（GeckoLib/TLM）
- 新建时要选对**格式**和**MC 版本**
- **box UV** 是十字展开，占面积很大（头 9.2³ 需要 36.8×18.4）→ 手动摆极易重叠，**要算**
- 导出会**重置贴图命名空间**（写成 `"texture"`）→ 每次导出后要检查
- **`.bbmodel` 是源文件，`.json` 是产物** —— Blockbench 有自动备份（`%APPDATA%\Blockbench\backups\`，`<lz>` 压缩格式）
- 模型改动要**重新构建**才生效（F3+T 只重载构建输出）

## 5.7 用 javap 读编译类

**不要猜 API，直接读**：
```powershell
& "C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\javap.exe" `
  -p -classpath "<jar>" 'net.spell_engine.api.spell.Spell$Impact$Action$Teleport'
```
反混淆的 Forge jar（**2026-09-15 起用 47.4.22**，与工程/整合包一致；旧路径把版本号换成 47.4.13 也能用）：
`C:\Users\FDCX\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.22_mapped_official_1.20.1\forge-1.20.1-47.4.22_mapped_official_1.20.1.jar`

**写 mod 代码时的两个自查工具**（都在 `D:\ModTest\tools\`，纯 ASCII 脚本、自动识别路径）：
- `verify_mod_jar.ps1` —— 构建后跑：检查 jar 里类是否齐全、
  **`@Mod.EventBusSubscriber` 是否在**（漏了会"编译通过但静默失效"）、字节码版本、是否已装进游戏包
- `verify_spells.ps1` —— 写法术数据前后跑：字段名/枚举值是否被包里既有法术证明过 + 交付链路是否完整

---

# 六、踩过的坑与教训

| 坑 | 教训 |
|---|---|
| **沙箱写不了 `D:\blockbench`** | 现已改为 `danger-full-access`，但历史上要提权 |
| **改了资源但游戏没变化** | 游戏读 `build/resources/main`，不是 `src/`。要么重新构建，要么直接同步过去 |
| **`sync push` 覆盖了游戏改写的 4167 个 config** | 已改成时间戳感知（只推更新的）|
| **`.ps1` 里的中文乱码** | 系统是 GBK 代码页，PS 5.1 读 `.ps1` 需 **UTF-8 BOM** |
| **`pwsh` 不存在** | 底层是 Windows PowerShell 5.1 |
| **Blockbench 导出重置贴图命名空间** | 每次导出后检查 `textures` 字段 |
| **box UV 重叠导致全身花掉** | UV 要用程序算，不能手摆 |
| **模型浮空** | 脚底必须 y=0；参考模型尺度 ~39 单位高 |
| **模型和原版剑"垂直"** | 平躺 XZ 平面的模型不能用 `parent: item/handheld` |
| **KubeJS 脚本不生效** | startup 脚本要重启游戏；server 脚本可 `/reload` |
| **`api.foojay.io` 连不上** | 用本地已有 JDK，不靠 Gradle 自动下载 |
| **PCL 认不出解压出来的实例** | PCL 要求 **文件夹名 = JSON 名 = jar 名**，且 JSON 里的 `id` 也要同名；别人打包的实例常常 JSON 还是旧名（本次就是），必须改名 + 改 `id` |
| **开游戏时内存被别的程序吃掉** | PCL「自动分配内存」按剩余内存算 → 只剩 3.1 GB 就只给 3174 MB，新世界生成到 57% 必 `OutOfMemoryError`、卡死十几分钟（日志里几万条 OOM）。**PCL 内存要设固定值** |
| **怀疑 NPC/村民被 mod 换掉** | 先 `javap` 看实体类的父类，别猜：作者的 NPC 全是 `PathfinderMob`（MCA 只换 `Villager`，换不到它们）；真正被影响的是**依赖原版村民**的内容 |
| **想看存档里到底有什么实体** | `.mca` 是自定义区域格式（表头在尾部），但可以直接**暴力找 `78 9C` zlib 流并解压**，再在区块 NBT 里搜实体 id。注意 PS 5.1 的 `New-Object X($a,$b,$c)` 传参不可靠，要用 `[X]::new(...)` |

---

# 七、当前状态与待办

## 进行中
- [x] **移除 `simplyskills`** —— 2026-09-15 已在新包里完成并核实（jar / config / 技能树全没了）
- [ ] **新实例首次启动测试**（MCA Reborn + Bountiful 只装未测）——重点看：
      能不能正常进游戏、村庄有没有 MCA 村民、ELA 原本的剧情 NPC 还在不在、TPS 有没有明显下降
      （⚠️ 启动前先把 PCL 内存设成固定 6–7 GB，见 8.3）
- [ ] **魔法石（数据层 + GUI）游戏内验证**：按 E → 点玩家模型右边那颗紫色石头 →
      期望亲和力全 3 / 魔力 210/210 / 点数 1 可用 / 「小闪电」可解锁（见 4.11、4.12）
- [ ] **雷系 5 法游戏内验证**（临时入口，用完即删）：重启 → `/give @s kubejs:tnc_spark_scroll` →
      法术注册台绑定 → 施放（数据 / 卷轴 / 图标 / 标签都已 push 进游戏包，见 4.10）
- [ ] 魔法系统设计待确认数值（见设计文档第十三节；雷系数值已按第十五节落地，先试手感再调）

## 待办
- [x] 把 `tlm_custom_pack\tnc_pet`（女仆模型包）与 `vaultpatcher\`（汉化）从旧实例搬进新实例 ✅ 2026-09-15
- [x] 把归档的闪现 7 个文件重放回新包 ✅ 2026-09-15（工作区已 pull 同步）
- [x] 把 `touhoulittlemaid-1.5.2` 装回新实例（魔改版删掉了它，`tnc_pet` 模型包需要它）✅ 2026-09-15，mods 278→279
- [ ] 视需要再搬 `hotai` / `immersive_furniture` / `local` / `data`（本次没搬）
- [x] TN-C 工程 Forge 版本从 47.4.13 对齐到整合包的 **47.4.22** ✅ 2026-09-15（联网补下 mapped jar，重建通过）
- [ ] 补上闪现的法术图标 `assets/tnc/textures/spell/blink_*.png`
- [ ] `mod_authors` 占位符待填
- [ ] 剑的贴图还是测试色块（未画真贴图）
- [ ] songzhikun 贴图未画（现在用的是 UV 验证图）

## 下一步（按设计文档的阶段计划）
```
阶段 0  清理（摘 simplyskills）← ✅ 已完成（魔改版里已删干净）
阶段 1  MagicStoneData Java 数据类（亲和力/魔力/点数/进度/已学）← ✅ 已完成（2026-09-15，见 4.11）
阶段 2  雷系 5 级法术 ← ✅ 首批 5 个数据已完成（2026-09-15，见 4.10）
        └ 还差：雷系领域魔法
阶段 3  魔法石 GUI + 「花魔法点数解锁法术」← 🚧 **第一版已完成**（2026-09-15，见 4.12）
        └ 还差：石头美术、列表滚动、解锁后的施放方式
阶段 4  HUD（重绘血条 + 新增魔力条）
阶段 5  施法（法杖 + 长按数字键）← 注意：本包 client.json5 已配好「数字键施法」，可能省一半活
阶段 6  领域魔法 + 融合
```

---

# 八、重要提醒

## 8.1 三个高难度模块需要 Java
GUI、HUD（血条/魔力条）、长按数字键施法 —— 这三个**超出 KubeJS 能力**，必须写 Java + Mixin。
**所以 TN-C 那个 Forge 工程是主力，不是配角。**

## 8.2 授权
- `车万女仆` = MIT / CC BY-NC-SA 4.0（素材非商用、需署名）
- 我们的 songzhikun 骨架源自 TLM 模型 → **也是 CC BY-NC-SA**
- 东方 Project 二次创作规约：必须标注是同人作品
- 整合包里各 mod 授权各异，**分发前要逐个确认**

## 8.3 性能
- 机器 **13.9 GB 内存**，跑 278 mods 的包很紧张
- **新世界生成**会造成 `Can't keep up!`（服务器线程落后几十秒），不是 mod 冲突
- 启动游戏前关掉 IntelliJ / QQ / 浏览器

### 🔴 PCL 的「自动分配内存」是个陷阱（2026-09-15 实测）

PCL2 默认按**开游戏那一刻的系统剩余内存**来算堆大小，于是：

| 启动时间 | PCL 分配的内存 | 结果 |
|---|---|---|
| 09-13 13:45 | 7270 MB | 正常 |
| 09-13 18:01 | 6246 MB | 正常 |
| 09-14 01:27 | 6758 MB | 正常 |
| **09-15 21:05（新实例首次）** | **3174 MB**（`-Xmx3174m`）| **世界生成 OOM，卡死** |

那天开游戏前 QQ×2 / Edge×2 / msedgewebview2 / 飞书 / node 一起吃掉了约 9 GB，
PCL 日志写着「当前剩余内存：3.1G」→ 就只给了 **3.1 GB**。
278 mods 生成新世界在 3 GB 堆里必然爆。

**结论：在 PCL 里把内存设成固定值（6–7 GB），不要用「自动分配」。**
另外开游戏前把 QQ / Edge / 飞书 / IntelliJ 都关掉。

## 8.4 工作流
```
1. IntelliJ 改 D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\...
2. cd D:\ModTest\modpack && .\sync.cmd push
3. PCL2 启动 元素觉醒1.4.3-魔改版-20260915 测试
```
**IntelliJ 不能运行整合包** —— 整合包只能由 PCL2 启动。

---

# 九、版本控制与协作（2026-09-16 建立）

远端：`https://github.com/Cockr00Ach/TN-C_MC-magic-s-world`（公开）
本地：`D:\ModTest`，分支 `main`。**仓库只有 1.55 MB / 309 个文件**，clone 秒级。

## 9.1 核心原则：**只跟踪我们自己写的东西**

`.gitignore` 是这个工程的命脉。整合包里绝大多数是第三方内容，进版本库有三个问题：
**版权（公开仓库二次分发 mod 会被投诉）、体积（GB 级）、而且我们从不改它们。**

| 进仓库 | 不进仓库 |
|---|---|
| `src/` `design/` `tools/` `model-source/` | `modpack/*/mods/`（280 个 jar） |
| `modpack/*/kubejs/`（211 个文件，含作者脚本） | `modpack/*/config/`（45.9 MB，只保留我们的 openloader 覆盖层 6 个文件） |
| `modpack/*/config/openloader/resources/TN-C/` | `modpack/*/tlm_custom_pack/`（37.4 MB 女仆模型包） |
| 根目录配置 / README / 本文档 | `build/` `run/` `.gradle/` `libs/*.jar` `*.zip` |

> ⚠️ **`config` 那段不能直接写 `modpack/*/config/`** —— 一旦整个目录被排除，
> git 就不进去看，后面用 `!` 放行的规则**全部失效**。必须"排除子项、再放行子路径"（见 .gitignore 注释）。

## 9.2 朋友怎么拿到完整环境

```
基础整合包 zip（1.5 GB，网盘发）      ← 279 个 mod + config + 女仆模型包
        ＋
git clone（1.55 MB）                  ← 我们的代码 + kubejs + 法术图标
        ＋
额外两个 jar（见下）                   ← 基础包缺 / 构建产物
        ＝ 完整可运行环境
```

**⚠️ 基础包里缺 `touhoulittlemaid-1.5.2-forge+mc1.20.1.jar`**（作者没写进说明，我们当初也是手工补的）。
对比过：zip 里 **279** 个 jar，好的实例里是 **280** 个。朋友用同一个包会一模一样地缺，必须单独发。
`tnc-1.0.0.jar` 他自己 `gradlew build` 就有，不必发。

朋友的步骤（已在 `README.md` 里）：
```powershell
git clone https://github.com/Cockr00Ach/TN-C_MC-magic-s-world.git
cd TN-C_MC-magic-s-world
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1   # 取回编译用 jar
.\gradlew.bat build
powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-to-pack.ps1 -Wait
```

## 9.3 踩过的三个坑

**① 首次提交时 `.git` 悄悄涨到 1545 MB。**
仓库根目录放着整合包原包 `元素觉醒1.4.3-魔改版-20260915.zip`（1547 MB），
`git add -A` 把它收了进去。发现得早（只有一个提交、还没配远端）→ **删掉 `.git` 重建**最干净。
现在 `.gitignore` 挡了 `*.zip / *.7z / *.rar`。

> **铁律：`git add -A` 之后一定看一眼文件数。** 健康值 ~309 个文件 / ~1.6 MB；
> 哪天变成几十 MB 或几百个文件，就是有大件混进来了。

**② GitHub 直连被墙。**
`github.com:443` TCP 四次全失败（DNS 解析正常）。但机器上跑着 Clash（内核 `ninja-mihomo`）
在 `127.0.0.1:6789`，系统代理也是开的 —— **而 git 默认不用 Windows 系统代理**。
所以只给 github 配了代理（不影响别的站点）：
```
git config --global http.https://github.com.proxy http://127.0.0.1:6789
```
⚠️ **Clash 没开时 git 连 GitHub 会报 `Connection was reset`** —— 那是代理没开，不是仓库坏了。

**③ 工具脚本里一堆硬编码绝对路径。**
`verify_mod_jar.ps1` 等 6 个脚本写死了 `D:\ModTest\`、`E:\download\...`，
甚至 `C:\Users\FDCX\...\javap.exe`；`modpack\sync.ps1` 写死了
`E:\download\正式版 2.12.6.1\.minecraft\versions`。
**朋友克隆到别的路径就全废。** 已全部改成自动推断：
- `tools\_common.ps1`：统一的仓库根 / 整合包 / 实时实例 / java 进程分类 / GBK 日志读取
- `Find-TncJdkTool`：javap/javac/jar 从 `JAVA_HOME` → 启动器自带 runtime → PATH 里找
- `sync.ps1` 支持 `-LiveRoot <路径>` 或环境变量 `TNC_LIVE_ROOT`，否则自动搜索

## 9.4 日常协作流程

```powershell
git pull                       # 动手前先拉
git checkout -b 你的分支名       # 一人一分支，别都往 main 直接写
# ...改代码... 至少保证 .\gradlew.bat build 能过
git add -A ; git status        # ★ 确认文件数还是 ~309
git commit -m "做了什么"
git push -u origin 你的分支名
# 再到 GitHub 发 Pull Request，另一个人看过再合进 main
```

**最容易冲突的三个文件**，改之前互相说一声：
`PROJECT-STATE.md`（两人都在记）、`kubejs/data/tnc/spells/*.json`（同一批数值）、
`Config.java` / `SpellCatalog.java`（数值表与法术目录）。

## 9.5 ★ 提交守卫：机械拦截 mod / 大文件（2026-09-16）

**9.3 的坑①不能靠记性防**（`.gitignore` 只在"有人记得写规则"时有用），所以做了自动化：

```
tools/git-hooks/pre-commit   ← git 的 pre-commit 钩子壳
tools/check-staged.ps1       ← 真正的检查（也可手动跑）
```

**每个 clone 要开一次**（`core.hooksPath` 是 per-clone 的）：
```
git config core.hooksPath tools/git-hooks
```
`tools\fetch-libs.ps1` 结尾会检查并提示这一条 —— 但**不会擅自改你的 git 配置**。

**拒绝规则**：`mods/*.jar`、`*.zip|7z|rar`、`libs/*.jar`、`tlm_custom_pack/`、`.connector/`、
`build/` `run/` `.gradle/`、`*.class|dll|so`、以及**任何 > 2 MB 的文件**（兜底）。
被拒时退出码 1，并打印原因 + 撤销方法（`git reset HEAD <file>`）。

**实测**（不是写完就交）：塞入 3 MB 文件 + 用 `git add -f` 强行暂存一个 `mods/*.jar`
→ 提交被拒，两类问题都列出（`[forbidden]` / `[too big]`）；清理后正常提交放行，
钩子自己打印 `check-staged: 4 file(s), 0.02 MB - ok`。

**踩到的一个坑（值得记）**：钩子输出里原本每行都被包成
`System.Management.Automation.RemoteException`，看着像钩子坏了。
根因是 **empty-string 输出**（`Write-Host ''` / `Write-Output ''`）在
"MSYS sh → powershell" 这条路上会被转成异常对象。
**用 `Write-Output ' '`（一个空格）代替空行就干净了。**
（注意：在 pwsh 里直接跑复现不出来，只在 git 钩子这条路上出现。）

> **健康基线：约 311 个文件 / 约 1.6 MB。** 钩子是兜底，习惯仍是第一道：
> `git add -A` 之后扫一眼 `git status` 的文件数。


