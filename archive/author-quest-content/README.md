# 归档：作者（元素觉醒）原有的任务 / 剧情内容

**归档日期**：2026-09-21
**归档人**：负责「任务系统 + 剧情系统」的这一侧
**原因**：TN-C 要自己的一套剧情与任务线；按用户决定，把作者原有的**剧情/教学类**章节
从整合包工作区移除，只保留**图鉴/说明类**章节。

---

## 🔴 首先看这一条：**这份归档是唯一副本**

作者的任务章节位于 `modpack\<包>\config\ftbquests\quests\`，
而 **`.gitignore` 第 59 行的 `modpack/*/config/*` 把整个 `config/` 排除了**（只放行了
`config/openloader/resources/TN-C/`）。

**结论**：这些章节**从来没进过 git**。删除它们**不会**在 `git status` 里出现，
也**不能**靠 `git checkout` 恢复。

→ 移走之前的那份工作区副本**已经被删掉了**；
→ **本目录（`archive/author-quest-content/`）是现存唯一的副本**。
→ 所以已对 `.gitignore` 加了一条例外，把这个目录**纳入版本控制**（见文末"为什么破例"）。

---

## 一、从工作区移走的 10 个章节（`ftbquests/chapters/`）

按用户选择：**删"剧情/教学"类，留"图鉴"类。**

| 章节文件 | 标题 | 归属分组 | 任务节点 |
|---|---|---|---|
| `0A14259964475B24.snbt` | `&f元素法师` | 提升之路 | ~291 |
| `0B6FFA2454FD16AB.snbt` | `&e元素觉醒特殊饰品统计` | 提升之路 | ~99 |
| `282E55946FAF4CED.snbt` | `&c近战 &f\| &d近战法师 &f\| &e圣骑士` | 提升之路 | ~246 |
| `451ED236A1A84376.snbt` | `&f卷轴/附魔/法术` | 提升之路 | ~141 |
| `a.snbt` | `&a游侠` | 提升之路 | ~51 |
| `1325F0684FE975ED.snbt` | `&f前往末地` | 世界地图 | ~59 |
| `2168A1F561C76718.snbt` | `&f亡灵堡垒` | 世界地图 | ~11 |
| `3A49366CC2EE2DA6.snbt` | `&f魔法协会` | 世界地图 | ~15 |
| `5F6818AC32FDA88C.snbt` | `&f废弃都市` | 世界地图 | ~21 |
| `6DAA3041BE0CA8A9.snbt` | `&f掠夺` | 世界地图 | ~33 |

> 任务节点数是按 `id: "16位十六进制"` 的出现次数估的（不是精确的"任务"数，含任务/奖励 id）。

## 二、保留下来的 6 个章节（**仍在工作区**）

这些是作者在 `e.snbt` 里自称的**图鉴/说明**用途（"职业与专精教学 / Boss 列表展示 / 物品图鉴查询"），
按用户决定保留：

| 章节文件 | 标题 | 归属分组 |
|---|---|---|
| `3061DAB8FCCB715B.snbt` | `食物列表统计`（最大的一章，~935 节点） | 休闲分类 |
| `boss.snbt` | `&cBoss分类`（~135 节点） | 挑战目录 |
| `37C8437D5B9D0F18.snbt` | `&f冠军词条/应对手段` | 挑战目录 |
| `500552A3129FF14C.snbt` | `驯龙指南` | 休闲分类 |
| `2032E61CAD845DDF.snbt` | `鸣谢名单` | *(无分组)* |
| `e.snbt` | `&e注意事项（游玩前必看）` | *(无分组)* |

## 三、配套改动（都发生在工作区，**不在本归档内**）

1. **`config/ftbquests/quests/chapter_groups.snbt`**
   删掉了分组 `16A3BF08B404CCE1` = `&e提升之路` ——
   它底下的 5 章**全部**被移走了，留着就是一个空分组。
   现存 3 个分组：`世界地图` / `挑战目录` / `休闲分类`。

2. **`config/whisperingquests/ftbq_bindings.json`**（NPC↔FTB 任务绑定）
   原文在 `config/ftbq_bindings.original.json`（**本归档里有**）。
   删掉的 4 条 —— 它们绑定的任务**落在被移走的章节**里，留着就是悬空引用：

   | NPC 对话节点 | 任务 id | 原本所在章节 |
   |---|---|---|
   | `ysjxmodel:main/spell_scroll_crafting` | `10f463ac5792aab9` | 卷轴/附魔/法术 ★已移走 |
   | `ysjxmodel:main/spell_anvil_materials` | `10f463ac5792aab9` | 卷轴/附魔/法术 ★已移走 |
   | `ysjxmodel:main/find_frost_wine_fox` | `319eaaa7d9425f37` | 卷轴/附魔/法术 ★已移走 |
   | `ysjxmodel:main/enter_the_end` | `6f0e38f7fd7382a8` | 前往末地 ★已移走 |

   保留的 3 条（任务在保留章节里）：

   | NPC 对话节点 | 任务 id | 所在章节 |
   |---|---|---|
   | `ysjxmodel:main/celestial_jellyfish` | `759b14686715881a` | boss（保留）|
   | `ysjxmodel:main/travel_companion` | `4dc87af1c5eaae6b` | 驯龙指南（保留）|
   | `ysjxmodel:main/forlorn_dark_parade` | `42c4ecb48c0768c8` | boss（保留）|

3. **`config/p1nero_dl-client.toml`**（对话库配置）
   副本存进本归档（`config/p1nero_dl-client.toml`）。**工作区那份没有删** ——
   它是对话库的通用配置，不是剧情正文。

4. **存档里的任务进度**（游戏实例，不在工作区）
   FTB 的任务进度**按存档存**在 `<存档>\ftbquests\*.snbt`。
   按用户决定，两个存档（`Medieval Town` / `新的世界`）的该数据已清掉并备份到
   `archive/local-save-backup/`（**不进 git**，见下）。

## 四、怎么恢复

```powershell
$ws = 'D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915'
# 1) 章节
Copy-Item 'D:\ModTest\archive\author-quest-content\ftbquests\chapters\*.snbt' `
          "$ws\config\ftbquests\quests\chapters\" -Force
# 2) 分组（把"提升之路"加回去）
#    编辑 "$ws\config\ftbquests\quests\chapter_groups.snbt"，加回
#    { id: "16A3BF08B404CCE1", title: "&e提升之路" }
# 3) NPC 绑定
Copy-Item 'D:\ModTest\archive\author-quest-content\config\ftbq_bindings.original.json' `
          "$ws\config\whisperingquests\ftbq_bindings.json" -Force
```
改完 **必须重启游戏**（FTB 任务数据在启动时读；`/reload` 不一定重读任务）。

## 五、为什么对本目录破例进 git

`.gitignore` 的原则是"别人的版权内容不进公开仓库"。本目录**确实**是作者的内容，
但它是**唯一副本**，而我们要在它之上做替换 —— 丢了就没法回退，也无法核对"到底删了什么"。

所以折中：

```
modpack/*/config/*        # 仍然排除整合包的全部 config（原样）
!archive/author-quest-content/   # 只放行这一份"删除前快照"
```

⚠️ 这是**唯一的例外**，不要拿它当先例去放行 `config/` 里的其它东西。

## 六、文件清单

```
archive/author-quest-content/
├── README.md                                  ← 本文
├── MANIFEST.txt                               ← 逐文件哈希（用于验证副本完整）
├── ftbquests/chapters/                        ← 10 个被移走的章节原文
│   ├── 0A14259964475B24.snbt
│   ├── 0B6FFA2454FD16AB.snbt
│   ├── 1325F0684FE975ED.snbt
│   ├── 2168A1F561C76718.snbt
│   ├── 282E55946FAF4CED.snbt
│   ├── 3A49366CC2EE2DA6.snbt
│   ├── 451ED236A1A84376.snbt
│   ├── 5F6818AC32FDA88C.snbt
│   ├── 6DAA3041BE0CA8A9.snbt
│   └── a.snbt
└── config/
    ├── ftbq_bindings.original.json            ← 改动前的绑定文件原文
    └── p1nero_dl-client.toml                  ← 对话库配置副本
```

> **没进归档的东西**（要一起删就一起删，但不是"作者的剧情"）：
> `config/ftbquests/quests/data.snbt`（任务数据索引）、
> `quests/reward_tables/`（3 张奖励表，**保留章节仍在引用**）、
> 以及 `defaultconfigs/ftb*`（mod 默认配置）。这些**都留在工作区没动**。
