# 墨龙战斗接入与三倍本体（2026-10-19）

后续已安装舒展飞行与可见性修订，当前状态及安装哈希见 [v3 记录](ink-dragon-flight-v3.md)。本页保留 v2 战斗接入的历史状态。

本记录取代早期“只有视觉原型”的状态说明。旧预览图保留用于对比。

## 已实现

- 本体是初版的 3 倍；注册碰撞体为 24×63 格，曲面模型三角面数量不变。
- 常态无重力空中巡游；检查完整体积、地形高度、世界边界和已加载区块，不强制加载地图。
- GPU 连续形变驱动身体游动，复用静态缓冲；下颌、下排牙齿、下颌须单独开合。
- 自动索敌生存/冒险玩家；创造和旁观玩家不自动成为目标，也不承受招式伤害。
- 吐墨：先追踪目标和张嘴，34—106 tick 持续喷吐；射线检测地形，碰撞面爆开墨气。服务器按有限线段和遮挡检查结算伤害。
- 俯冲：本体上升，40—80 tick 在脚下汇聚巨墨球与本体三倍的龙影；80—110 tick 沿同一轨迹俯冲，110 tick 命中爆散；龙影与球消失。蓄力后锁定落点，可躲避。
- 招式取消、死亡、目标移除和重新载入不会留下持续攻击；所有效果不破坏方块。
- 初版 Blockbench 原始工程未改写，只重新导出运行时独立下颌批次，近景仍为 140,414 三角面。

## 操作

需要管理员权限，在开阔处使用：

- `/tnc ink dragon`：前方 90 格、上方 30 格召唤大型墨龙。
- `/tnc ink dragon_attack 1`：令最近墨龙向自己演示吐墨。
- `/tnc ink dragon_attack 2`：令最近墨龙向自己演示龙影俯冲。

创造模式可用手动指令看动作，不会受到伤害；生存模式会自动交战。`NoAI:1b` 禁止自动巡游/索敌，仍允许管理员指令触发演示。

## 验证

- 325 项 JUnit 测试通过。
- 24 项独立服务器 GameTests 通过，覆盖真实巡游、喷吐伤害、墙体阻挡、不破坏墙体、俯冲伤害、取消与存取档。
- 测试日志：`work/ink-dragon-combat-scale3-verified-tests.log`。
- 最终客户端与发布构建：`work/ink-dragon-live-scale3-final.log`。
- 实际服务端同步客户端截图/诊断：`work/ink-dragon-live-combat-report.json`，隔离开发存档，不是正式整合包帧率测试。

## 安装

已安装至 `E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/mods/tnc-1.0.0.jar`。

- 安装 SHA256：`FC878CB736526EE0D6B469ECA1B84B8F4AA549FF7089FAB2AC15B62927D2E0EA`，复制后再次读取核验通过。
- 原版备份：`D:/ModTest/work/install-backups/ink-dragon-combat-scale3-20261019-200413/tnc-1.0.0.jar`。
- 原版 SHA256：`EBAE73C812000CE5451F4C2154005A86B1C1C15FB89F19D8AB9E65E641EEB1C5`，备份哈希一致。
- 更新 83 项水墨类/资源，16,671 项其他条目内容逐字节保留；语言仅更新翼虎、墨龙名字。
- 发布合并报告：`work/ink-dragon-combat-scale3-merge-report.json`。
- 客户端报告确认 `bodyScale=3`、`independentJaw=true`、`continuousWaveShader=true`、喷吐阶段 `jaw=1`、`nativeGlError=0`。
- 当前实际客户端截图：`model-source/ink-celestial-dragon/preview_live_breath_scale3.png` 与 `preview_live_dive_scale3.png`。

正式包安装时未运行；没有修改正式玩家存档。需要重启游戏加载新 JAR。这里的客户端验证不等于完整整合包性能基准。
