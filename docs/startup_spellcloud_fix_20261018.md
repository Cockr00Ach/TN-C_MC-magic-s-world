# PCL2 启动冲突修复（2026-10-18）

正式客户端启动时报 `NoClassDefFoundError: net/spell_engine/entity/ConfigurableKnockback`。
该接口实际存在于 Spell Engine；TN-C 包却混入了原生预览使用的
`net/spell_engine/entity/SpellCloud.class`，形成分包冲突，使 Forge 无法解析引擎的接口。

修复以当前安装包为基础，只移除这一个占位类及其三个目录条目。
其余 15,627 个条目的内容逐字节保持一致，包括新版暗系巨兽人上半身和其他并行更新。
正式包 SHA-256：`8DCAC2FBAC32C9016C5B3C87FDAAB2DCAC5260D53BE6B3374A4912BB9525F0A7`。
安装前备份位于 `work/backups/startup-spellcloud-fix-20261018-20261018-053741`。

防复发措施：Gradle JAR 任务排除 TN-C 命名空间以外的类，发布校验则拒绝已经包含此类的包。
这也保护基于旧包叠加的发布流程。依赖本身仍由各自的正式模组提供。

验证：污染原包被新增检查拒绝；修复包通过完整 `verify_mod_jar.ps1`；
保留编译目录中的占位类执行 `jar -x compileJava -x processResources -x reobfJar`，
生成包中外部命名空间类数量为零。打包验证的完整产物没有用于安装。
另修复 PCL 启动测试脚本对中文 Windows 导出 GBK 批处理的解码。
