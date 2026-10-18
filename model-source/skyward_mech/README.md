# 天穹式魔导铠与六联晶铳

- `skyward_mech.geo.json`：Blockbench → 基岩版实体模型打开。包含头、身体、四肢、背部翼板和晶核；游戏使用原生 Humanoid 骨骼，不依赖额外动画库。
- `crystal_gun.json`：Blockbench → Java 方块/物品模型打开。展示完整枪体；游戏拆成枪身、六联转管和发光口三个模型，转管独立旋转。
- 盔甲贴图：`../../src/main/resources/assets/tnc/textures/models/armor/mana_mech_layer_1.png`，256×256。0–47蓝钢、48–95骨白陶甲、96–143暗金属、144–207旧铜、208–255发光部分；独立透明发光蒙版为同目录 `mana_mech_glow.png`。
- 枪械贴图：`../../src/main/resources/assets/tnc/textures/item/mech_*.png`。

当前几何的生成源为 `../../tools/build_skyward_models.py`。修改 Blockbench 导出后请同时更新生成源或告知 Agent 导入修改，避免下次生成覆盖手工调整。原生盔甲模型位置见 `RouteMechModel.java`；枪的第一/第三人称变换在 `models/item/mech_spellgun.json`。

玩法：魔力炉锻造→充能台充入12000M→胸甲位穿戴→持晶铳长按右键。潜行右键换七元素；V突进、B屏障、G过载、潜行V震荡，按键均可重设。机炮与技能只花机铠储魔，不吃箭。射击积热，满热排放3秒。完整操作和配方见 FTB「魔导工坊」页。
