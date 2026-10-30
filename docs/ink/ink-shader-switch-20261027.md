# Ink 维度光影切换与 Ink Wash 试装

日期：2026-10-27。已安装至正式 Forge 1.20.1 整合包，原 Oculus 1.8.0、Embeddium 0.3.31 保留。

## 玩家使用

重启游戏生效。默认进入 `tnc:ink_realm` 自动关闭光影，离开后恢复进入前的启用状态及选中的光影包。原来关闭的玩家离开后仍然关闭。断线返回菜单也恢复；异常退出保留恢复记录，下次启动先恢复原设置。

- `/tncinkshader ink`：Ink 内使用 Ink Wash，离开后恢复原设置。
- `/tncinkshader off`：Ink 内自动关闭光影，推荐默认值。
- `/tncinkshader status`：查看模式。

每个客户端独立管理。命令在维度外仅修改后续进入策略。配置 `config/tnc-ink-shader.json`，缺省 `off`；临时恢复记录 `config/tnc-ink-shader-session.json`，成功恢复后删除。通过 Oculus API 保存并重载，不直接重写其配置文本。不安装额外的 Melon 切换模组。

## 原包与视觉实测

来源：[Ink Wash Shader Pack](https://modrinth.com/shader/ink-wash-shaderpack)，1.1.0，官方原 ZIP，已核对 Modrinth API SHA512；未修改或公开再分发。放在正式 `shaderpacks/ink-wash-shaderpack.zip`。Complementary 保留。

隔离原生客户端测试：`work/ink-shader-visual-fixed-client-20261027/report.json`，6 项状态检查无失败。已人工检查同目录 `screenshots/`：无光影龙头、Ink Wash 龙头、旋转法阵、墨球汇聚、爆炸后山水虚影、主世界恢复。

龙、法阵和墨球可见，物品栏保留彩色。Ink Wash 的描边会明显凸显龙模型的面片网格，墨球也出现较强的网格描边，因此只作为可选试用，默认仍为自动关闭。这里不宣称整合包全量兼容或帧率提升。

最初视觉截图被首次登记界面遮挡；第二次截图摄像机因跨维度飞行状态重置落到地面。这些未用于龙及法阵视觉验收。最后一次使用固定测试镜头补拍通过；测试辅助代码均未安装。

## 验证

- 8 项 `InkShaderSessionTest` 单元测试通过，覆盖开/关恢复、重复 Tick 不重载、模式变更、异常回滚、恢复日志。
- 完整真实客户端生命周期 10 项状态复核通过：`work/ink-shader-client-20261027/verified.json`。原始报告最后一项错误要求断线菜单仍有世界渲染管线；实际应为 `rendering=false`，启用偏好及包名正确恢复。原始报告保留，断言已修正并独立复核全部状态。
- 单独视觉运行 6 项状态检查通过；实际启用原版 Ink Wash 成功，无光影编译失败。
- 合并发布包 ASM 栈/局部变量分析通过：105 个类、1189 个方法，其中包含新增的 8 个生产类。
- 测试运行时借用的 SpellCloud 桩已移出编译目录；临时装备 mixin 资源已按原哈希恢复。未修改相关源文件。

## 安装范围和回退

仅在现有正式模组 JAR 新增 8 个 `com/tnc/tnc/inkshader/` 类，原有 17120 个条目逐项字节一致。另添加官方光影 ZIP。未覆盖 Oculus/Complementary 配置及存档；正式 Oculus 配置安装前后 SHA256 相同。

安装清单：`work/ink-shader-switch-install-20261027/manifest.json`。

正式 JAR SHA256：`60237FE3F27845D2C0A76B2798B95D254A0968282730999A88884ECC5EB10972`。

备份：`E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/backups/tnc-ink-shader-switch-20261027-143438`。恢复其中原 JAR 可回退本次模块；回退前先离开 Ink 或正常断开，让原光影状态恢复。
