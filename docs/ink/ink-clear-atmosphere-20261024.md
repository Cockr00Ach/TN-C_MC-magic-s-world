# 水墨维度清除环境云雾

仅清除 `tnc:ink_realm` 的环境云层、天空雾和地形距离雾。地面山水画及其画中的云、Boss 施法水墨拖尾和爆炸山水残影保留。没有更改玩家全局视距、云设置、光影开关或正式存档。

## 模组

`InkRealmClientEffects` 的云高度改为 `Float.NaN`，同时用 Forge 云渲染回调明确跳过绘制。维度内的 Forge `RenderFog` 事件使用有限且递增的远端雾平面（100 万／200 万格），使正常可视距离内不出现环境雾；覆盖天空、地形和流体相机雾。其他维度不干预。天空原有黑白灰配色不变，不添加全屏黑白滤镜。

7 项 JUnit 通过，覆盖云渲染字节码、雾范围、维度隔离及原维度／山水地画资源。记录：`work/ink-clear-atmosphere-20261024-verified.log`。客户端类的绘制入口用字节码检查，避免在普通单元测试进程中强行初始化游戏注册表。

## ComplementaryUnbound r5.6.1

该光影自绘体积云、空气雾、洞穴雾和边界雾，不受原版雾距离完全控制，因此单独提供维度适配变体。

工具：`tools/prepare_ink_clear_atmosphere_shader.py`。只读取原光影文件夹并输出新 zip，不修改原包。`dimension.worldink=tnc:ink_realm` 对应 59 个专属程序入口，沿用原 Overworld 的照明与材质程序，在 ink 入口定义专用宏：关闭体积云、云影、空气／边界／洞穴／辉光雾、体积光雾，并跳过该维度的 `DoFog`。原主世界、下界、末地入口逐文件不变，新增目录同步原程序启用条件。共享文件改动全部以该专用宏控制。

生成包：`exports/ComplementaryUnbound_r5.6.1-tnc-ink-clear-20261024.zip`。同名 JSON 记录源文件未改动、原世界入口未改动及 SHA-256；该报告中的运行时编译字段需结合独立客户端报告判断。

实际客户端验证通过：Oculus 1.8.0、Embeddium 0.3.31、适配后 Complementary 处于启用状态，进入真实 ink 维度；166 帧期间观察到 2007 次原版雾事件，范围均被清除；云高度禁用、云回调不绘制、OpenGL 检查均通过。报告 `work/ink-clear-atmosphere-native-report.json`，日志 `work/ink-clear-atmosphere-20261024-native.log`，截图 `work/ink-atmosphere-client-audit/screenshots/ink-clear-atmosphere.png`。这不是完整整合包 FPS 测试；开发测试环境另有缺少天空岛清单和光影对新版本 Pale Garden 生物群系变量的既有警告，不宣称整份日志完全无警告。

2026-10-24 模组和光影适配均已安装到正式整合包。Oculus 已选择新适配包，原 Complementary 文件夹和原有光影启用状态保留；已有模组及配置已备份。见 `docs/spells/light-mirror-ink-installed-20261024.md`。
