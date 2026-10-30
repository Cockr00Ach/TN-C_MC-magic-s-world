# 苍岚战将：装甲与材质细化

用户确认仅细化苍岚战将，保留最近作者修改的持枪和攻击动画；不要大面积同色色块。

## 资产

- 原 50 根骨骼和 416 个部件的坐标、大小、旋转、父级关系均保留。
- 增加 78 个浅浮雕部件，总计 494 个：面甲叠片、眉甲、鼻梁、胸侧嵌线、肩甲筋条、护臂叠片、胫甲包边、裙甲系带、披风滚边。
- 动画文件 SHA256 保持 `90dafa1c79b8796bc985d379e31790fbfe4b2461e5d43d5de0b999abe67944b8`。
- 1024×1024 实际像素，沿用 256×256 **逻辑 UV 单位**。每个 64 单位材质区对应 256 实际像素；不是把旧图直接放大。
- 16 区材料图集，模型实际使用 15 区。分开深蓝钢、叠片、锤纹、风羽嵌线、古铜、兽面浮雕、银蓝金属、锁甲、云纹织物、羽纹披风、战旗、风能通道、晶体、玉饰和皮革。
- 原有武器锚点和攻击动画未改；第二阶段与残影继续共享这套材质。没有新增粒子或额外渲染通道。未进行整合包 FPS 基准测试。

## 可编辑文件与重建

`exports/CanglanWarlord-SurfaceRefined-20261022.zip` 包含模型、PNG 和原动画。在 Blockbench 打开 geo.json 并导入 PNG；不要把模型描述中的逻辑纹理尺寸改成 1024。

```powershell
python tools/paint_canglan_surface_atlas.py
python tools/refine_canglan_surfaces.py --atlas model-source/canglan_warlord/surface-refinement-20261022/material-atlas.png
```

原模型/贴图/动画快照位于 `model-source/canglan_warlord/surface-refinement-20261022/`；未覆盖作者原始编辑文件。不要重新运行旧造型/动画生成器覆盖作者动作。

图像生成技能尝试的请求没有返回，随后用户明确选择了程序化材质。本轮最终图集由确定性的 Python/Pillow 矢量笔画和像素材质代码生成，没有使用 AI 生成图片；尝试的提示词仅作记录，不是最终贴图的生成来源。

## 检查

29 项苍岚相关 JUnit 检查通过，包括作者动画一致性、原骨骼/原部件变换保留、UV 单一区域边界、实际材质对比度与方块预算。

测试日志：`work/canglan-surface-tests-20261022.log`。

实际 Minecraft/GeckoLib 渲染生成 6 张截图，报告 `failures=[]`；查看了待机、残影和突刺标签的截图，确认模型纹理正常。本次是外观资源检查，不是完整战斗行为或整合包 FPS 验证。记录：`work/canglan-surface-install-20261022/native-report.json`；成功日志 `work/canglan-surface-native-20261022-pass4.log`。隔离测试缓存先补齐后来新增的地牢、水墨类及 GeckoLib 运行依赖；没有把这些测试缓存内容打进发布补丁。

实机预览：`docs/previews/canglan_surface_idle_20261022.png` 和 `docs/previews/canglan_surface_echo_20261022.png`。

安装准备脚本只覆盖 `assets/tnc/geo/entity/canglan_warlord.geo.json` 和 `assets/tnc/textures/entity/canglan_warlord.png`；战斗类、动画和其余 16,941 个条目均逐字节保留。安装前再次检查正式包哈希并备份，机器报告位于 `work/canglan-surface-install-20261022/verification.json`。

安装包 SHA256：`4E5AD309064C6B8800ACC084D297AD4551AA7CA9754C2924DEAD51244BAEAC08`。
旧包备份：`work/install-backups/canglan-surface-20261022-2250/tnc-1.0.0.jar`。需重启游戏加载新资源。
