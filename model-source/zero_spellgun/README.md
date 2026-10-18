# 零式模型源文件

用 Blockbench 直接打开 `zero.bbmodel`。这是完整自由模型，包含枪身、双开放导轨、五瓣导流翼及核心；贴图已嵌入，可直接查看。

游戏中的每片导流翼共用一个原始零件，由 `ZeroGunRenderer` 绕枪口轴旋转五次，开火时加速并轻微展开。自由模型保留了这五片的完整摆位，便于整体修改。游戏实际零件在 `src/main/resources/assets/tnc/models/item/zero_spellgun_*.json`，第一及第三人称持握变换在 `zero_spellgun.json`。

几何生成入口：`python tools/build_zero_models.py`。它只生成零式，不动六联晶铳、机铠、任务书或天空岛。修改后不要未经备份重新运行生成脚本，以免覆盖手工模型。
