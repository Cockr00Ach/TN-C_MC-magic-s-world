黑夜之手 · Blockbench 工程（给作者改贴图用）
================================================

这个文件夹是"黑夜之手"道具模型的 Blockbench 工作副本。
用法见下面，两分钟能上手。

【要打开的文件】
  dark_hand.bbmodel   ← 双击 / Blockbench 里 File > Open Model
                        **贴图已经内嵌在里面**，打开就能直接在 Paint 模式刷图
  dark_hand.png       ← 同一张贴图的独立副本（想在 Photoshop / Paint.NET 里改也行）

【模型是什么】
  31 个方块，分辨率 32x32，按材质分成了 9 个组（左侧 Outliner 里能整组点选）：
      wrist x1   palm x1   back x2   knuckle x1
      digit_0 x3   digit_1 x4   digit_2 x7   claw x9   aura x3
  朝向约定（**别改**，两个渲染器都靠它）：
      +Z = 掌心（朝着敌人 / 天降时朝着正下方）
      +Y = 上（手指朝上）
      -Z = 手腕
  改**几何**之前先说一声 —— 模型的"贴地锚点"是按 y 跨度算的（±13 单位 ⇒ 半高 13/16 格），
  几何一改那个常数也要跟着改。

【贴图图集对照表】（32x32，每一块就是一个材质，按像素坐标）
      wrist     x  0..7    y  0..7     (8x8)
      palm      x  8..19   y  0..11    (12x12)   ← 掌面（最主要的一块）
      back      x 20..27   y  0..7     (8x8)     ← 掌根 / 手背
      digit_0   x  0..11   y  8..13    (12x6)    ← 食指那一段
      digit_1   x  0..11   y 14..19    (12x6)
      digit_2   x  0..11   y 20..25    (12x6)
      knuckle   x  0..11   y 26..31    (12x6)    ← 掌指关节横条
      claw      x 12..21   y  8..17    (10x10)   ← ★ 爪尖：全图唯一的暖色（干血红）
      aura      x 22..29   y  8..15    (8x8)     ← 灵光碎片
      tether    x 24..31   y 16..23    (8x8)     ← **现在没有方块用它**（黑带已删）✓
  想更快看懂：在 Blockbench 里点某一块方块 → 右侧 UV 面板会高亮它用的是图集哪一块。

【改完贴图怎么回到游戏】（★ 这一步必须照做，不然看不到变化）
  1. 在 Blockbench 的 Textures 面板里对 dark_hand.png 右键 → **Save As / Export Texture**；
  2. 覆盖到：
        src/main/resources/assets/tnc/textures/spell_projectile/dark_hand.png
     ⚠️ 分辨率必须还是 **32x32** —— 模型的 UV 是按这张图集逐像素写的，改尺寸会让所有 UV 错位；
  3. 告诉我 → 我重编译 ＋ 装机（装机前要关游戏）。

【⚠️ 别让谁再跑一次生成器】
  tools/gen_dark_hand_model.py 会**按它自己的调色板重画整张贴图**，把你手绘的这张覆盖掉。
  这个模型和贴图本来就是那个脚本生成的（所以仓库里原先没有 .bbmodel）。
  如果你更想"手绘为准"，说一声，我把你画好的 PNG 反向烘进生成器 ⇒ 以后重跑也不会丢。

【这个 .bbmodel 是怎么来的】
  python tools/gen_dark_hand_bbmodel.py
  （它读 models/projectile/dark_hand.json ＋ 贴图 PNG，把几何和内嵌贴图一起写成 .bbmodel；
    方块名字是按 UV 落在图集哪一块自动命名的，重新生成也不会变。）

【当前配色（2026-10-10 改过一版）】
  旧版：亮紫肉质 ＋ **洋红爪尖 ＋ 近白高光** ⇒ 作者反馈"太花了"，
        量化对照：最亮像素 240.7 → **47.1**，亮度>120 的像素占比 4.9% → **0.0%**。
  现版：焦黑的碳 ＋ 冷紫极窄描边，唯一的暖色留给**爪尖的干血红**。
