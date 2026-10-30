# 教官模型 · Blockbench 作者用文件

`instructor-blockbench.geo.json` 是**给动画师看的**模型，不进包、不参与构建。

- 正式模型：`tools/build_instructor_model.py` → `src/main/resources/assets/tnc/geo/entity/instructor.geo.json`
  （17 骨 / 16 方块，**空手**——游戏里的剑盾是装备，由 `InstructorHeldItemLayer` 画在 `forearm_r` / `forearm_l` 上）
- 作者用模型：`tools/build_instructor_authoring.py` → 本目录这份（17 骨 / 20 方块，
  `forearm_r` 里多了剑的三个方块，`forearm_l` 里多了一面盾）

## 用法

1. 重新生成：`python tools/build_instructor_authoring.py`（会顺带渲染
   `work/instructor-preview/authoring-weapon.png`：idle / 挥到一半 / 收势）。
2. Blockbench → New Project → **Bedrock Model** → 导入 `instructor-blockbench.geo.json`
   → 贴图选 `src/main/resources/assets/tnc/textures/entity/instructor_counter.png`
   （四套皮形状相同，任选）。
3. 照常 K 动画。骨骼只有那 17 根，武器方块**没有独立骨骼**，所以腕部怎么摆、剑就怎么走。
4. 导出：File → Export → **Bedrock Animations**，覆盖
   `src/main/resources/assets/tnc/animations/entity/instructor.animation.json`。
   GeckoLib 的动画只认骨骼名，多出来的通道会被忽略；但**别导出模型**——
   这份文件里的剑盾方块会一起进包，然后和装备渲染叠成两把剑。

## 为什么武器位置可信

剑盾方块的 pivot 就是拳头 `(-6, 12, 0)` / `(6, 12, 0)`，剑的旋转**只有** `x = -55°`
（`y = z = 0`），与 `InstructorHeldItemLayer.GRIP_ROT_X` 是同一个数字。

**为什么坚持单轴**：欧拉角的合成顺序各家不同——Blockbench 走 three.js，方块按
`Rx·Ry·Rz` 合成；Bedrock/GeckoLib 按 `Rz·Ry·Rx` 合成。三轴角在两边会摆出不同姿势
（游戏里朝前、编辑器里朝后，这个坑已经踩过一次）。单轴旋转在两种顺序下结果相同，
所以 Blockbench 里看到的必然就是游戏里的。叉刃（+X）不受影响，护手仍然横在刀身两侧。

单元测试 `InstructorGripModelTest` 会断言：

- `GRIP_ROT_X` 在 XYZ 与 ZYX 两种合成下结果一致、刀身朝上偏前、护手横着；
- 本文件里剑方块的旋转/握点 = 渲染器常量。

任何一处改动而没同步另一处，测试会直接红。

## UV 说明

剑盾的 UV 由脚本自动指向贴图里接近纯色的金属/木料小块（低方差区域），
所以 Blockbench 里看到的是"形状正确、颜色单调"的参考物——目的是摆姿势时看得见武器，
不影响正式贴图。想让它在 Blockbench 里更漂亮，把 UV 挪到空白处自己画一格即可。
