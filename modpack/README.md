# TN-C 整合包工作区

在这里编辑整合包**可以改的部分**。`mods/`（GB 级 jar）和存档不进来。

## 目录约定

```
D:\ModTest\modpack\
├── README.md      ← 本文件
├── sync.cmd       ← 同步工具
├── sync.ps1
└── <整合包文件夹>\  ← ★ 只能有一个，就是它
```

**把整合包文件夹直接放在这一层。** 脚本会自动识别它的名字，并去找同名游戏包：

```
工作区  D:\ModTest\modpack\<名字>\
游戏包  E:\download\正式版 2.12.6.1\.minecraft\versions\<名字>\
                                        ↑ 必须和 PCL 版本文件夹名完全一致
```

**换包时**：删掉旧的文件夹，放进新的，脚本自动跟上，**不用改任何配置**。

## 同步

```powershell
cd D:\ModTest\modpack
.\sync.cmd diff          # 看两边差异（标注 ws-newer / pack-newer）
.\sync.cmd push          # 推送（只覆盖工作区更新的文件）
.\sync.cmd push -Force   # 强制全量覆盖
.\sync.cmd pull          # 从游戏包拉回来
```

### 为什么 push 是"时间戳感知"的

游戏运行时**不停改写 `config/*.toml`**。无脑 push 会把那些新文件覆盖成旧的工作区版本。

所以 `push` 只在**工作区文件更新**时才覆盖。想强制用 `-Force`。

### 改完必须

```
1. sync.cmd push
2. 重启游戏
```
（KubeJS 的 **server 脚本**可以试试 `/reload`；**startup 脚本**和资源必须重启）

## 哪些目录会被同步

```
kubejs  config  defaultconfigs  local  data
tlm_custom_pack  vaultpatcher  hotai  immersive_furniture
```

**绝不碰**：`mods` `saves` `logs` `backups` `xaero` `resourcepacks` `shaderpacks`

## 归档

`D:\ModTest\archive\` 放我们**自己写的东西**（不随整合包替换而丢失）。
每个子文件夹有自己的 README 说明「这些文件是什么、放到哪」。

## 改整合包的入口

| 想改什么 | 改哪 |
|---|---|
| 法术数值 / 新增法术 | `kubejs/data/`（数据包）|
| 卷轴、套装、配方 | `kubejs/startup_scripts/` `server_scripts/` |
| tooltip | `kubejs/client_scripts/` |
| mod 配置 | `config/*.toml` |
| 覆写作者的技能树 / 法术 | `config/openloader/data/` |
| 汉化 | `config/openloader/resources/` `vaultpatcher/` |

## 当前状态

| 项 | 值 |
|---|---|
| 整合包副本 | **元素觉醒1.4.3-魔改版-20260915**（2026-09-15 建，5199 文件 / 84.7 MB）|
| 归档 | `archive/blink-spell/` —— 第一个原创法术的完整范例（**已重放回本工作区**）|
| 游戏包 | 新实例 `E:\...\versions\元素觉醒1.4.3-魔改版-20260915\`；旧包 `元素觉醒1.4.3` 仍在，可玩 |

## 关于新包（魔改版）

新包**只带** `mods` `config` `defaultconfigs` `kubejs` `resourcepacks` `shaderpacks` + 版本 json/jar。

**它原本没有**这些同步目录：`tlm_custom_pack`（我们的 `tnc_pet` 女仆模型包在里面）、`vaultpatcher`（汉化）、
`hotai`、`immersive_furniture`、`local`、`data`。

2026-09-15 已从旧实例 `元素觉醒1.4.3\` 搬入 **`tlm_custom_pack`（1266 文件）+ `vaultpatcher`（158 文件）**，
并把归档的**闪现 7 个文件**重放回 `kubejs\`（照 `archive/blink-spell/README.md`）。
仍未搬：`hotai`、`immersive_furniture`、`local`、`data`。

另外把 **`mods\touhoulittlemaid-1.5.2-forge+mc1.20.1.jar` 装回了新实例**（魔改版把它删了，
而我们的 `tnc_pet` 女仆模型包需要它）。⚠️ **`mods` 目录不参与同步** —— 这类改动只在游戏包里。

**`mods\.connector`（信雅互联缓存）被有意剔除**，首次启动会自动重建，那一次启动会比较慢，属正常。
