# 移除Conquest原版材质覆盖 · 2026-09-28

用户要求尽快去掉该材质包，并明确不以城堡外观为限制。游戏退出后已在当前PCL实例安装本地裁剪版Conquest 1.6.0。

## 根因与处理

`ModBuiltinRP` 将CRRP注册为required，资源包界面没有停用箭头。主要原版贴图/模型实际上直接在模组jar的 `assets/minecraft` 中，删options中的CRRP条目无效。

`tools/remove-conquest-vanilla-resources.ps1` 读取该实例Minecraft原版client jar的资源清单，移除Conquest内与原版同名的覆盖资源、对应附加动画元数据、OptiFine覆盖及CRRP原版颜色覆盖。保留非覆盖的自定义支撑资源，以及所有class、data和assets/conquest。不直接删除整个模组，亦不改注册ID、存档、options或光影设置。

删除34,898项资源，jar由230,528,362字节降至107,870,881字节。修改的是本地第三方jar，不上传修改后的二进制到Git；同伴需在关闭游戏后对自己的1.6.0实例运行脚本：

```powershell
.\tools\remove-conquest-vanilla-resources.ps1 -LivePack '<实例完整路径>' -Install
```

脚本不带Install只准备副本；安装必须无Java进程并验证源文件未变化。

## 校验与恢复

- 保留的156,487项class/data/Conquest自身资源逐项SHA256一致。
- Conquest jar内原版橡树叶blockstate和贴图覆盖均已移除；安装文件与准备文件SHA256一致。
- 原文件备份：`work/backups/conquest-vanilla-removal-20260928-162724-698/ConquestReforged-original.jar`。
- 恢复：完整退出游戏，将上述备份复制回该实例 `mods/ConquestReforged-forge-1.20.1-1.6.0.jar`。
- 早先仅准备、未安装的全命名空间裁剪副本在162020备份目录；不要安装那个副本。

## 限制

CRRP/Conquest仍可能在资源包界面显示，因其注册与方块模组仍在；这不等于它继续覆盖原版材质。没有声称模组已卸载。

本次完成资源/字节级校验，尚未重启客户端视觉验收或测量FPS。其他模组、光影或非原版树叶自身模型问题不能据此保证全部修复。以后升级/重新安装原版Conquest jar会恢复覆盖，需要重新处理。
