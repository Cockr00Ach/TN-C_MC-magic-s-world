# 苍岚普通横扫误触特写修复

普通横扫此前无条件发送 80 tick 特写消息，即使目标不满足处决条件。现在服务端仅对已选定的真正处决目标发送开镜消息；客户端拒绝非处决开镜消息，保留零时长取消消息。普通横扫的伤害、攻击范围和真正处决的伤害判定均未修改。

处决入口仍要求生命值加吸收生命严格低于最大生命的一半；半血整不进入处决。真正处决在命中时继续校验生命、距离、视线与无敌状态。

验证：

- 编译、正式重混淆构建及 5 项 `CanglanFootworkRulesTest` 单元测试通过。
- 第一轮完整苍岚服务端测试 16 项中 13 项通过，包含新增的健康/半血/低血/吸收生命/创造模式镜头选择与清理检查。旧龙卷风接触伤害、三次位移及近地处决测试失败，未将其报告为全通过；本次不修改这些无关机制。
- 独立命名空间的镜头选择检查再次通过。额外尝试的高处即时伤害测试也未通过普通伤害断言，未将它作为有效伤害回归证据并已移除该临时测试；这次不宣称完整伤害回归通过，原有伤害代码保持不变。
- 合并包的 67 个相关类、780 个方法通过 ASM 栈/局部变量校验。
- 最终仅运行镜头条件的原生服务端 GameTest 成功（1 项，覆盖多个生命/吸收生命/创造模式及清理场景）；记录显示 `All 1 required tests passed` 与 `BUILD SUCCESSFUL`。开发用外部实体占位类已从主类输出目录移走，未进入安装包。

安装仅覆盖 `CanglanWarlordCombat`、`CanglanWarGodRules`、`CanglanCinematicCamera` 三个类；其余 17060 项按解压字节完全保留，包括已安装的光镜、信徒、水墨清雾、龙批量顶点与天空高度修复。无需修改存档或光影配置。

已安装到正式整合包，安装后哈希校验一致：`F269B1F4F9F1C87F0F309BB1CCBB91A531C7AC8FBC453148A52C739092AAAB90`。

备份：`E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915\backups\tnc-canglan-execution-camera-20261024-044225`。

安装清单：`work/canglan-execution-camera-install-20261024/manifest.json`。扩展独立测试记录：`work/canglan-execution-camera-20261024-isolated-native.log`。只检查本次镜头条件的独立运行记录：`work/canglan-execution-camera-20261024-camera-only-native.log`。
