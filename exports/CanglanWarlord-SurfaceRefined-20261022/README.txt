苍岚战将 — 装甲与材质细化（2026-10-22）

在 Blockbench 中打开 canglan_warlord.geo.json，并导入同目录 PNG。
贴图为 1024×1024 实际像素，模型继续使用 256×256 逻辑 UV 单位，
因此不要把模型的 texture_width/texture_height 改成 1024。

保留原有 50 根骨骼、原始方块位置、骨骼初始旋转、武器锚点和全部动画。
新增 78 个装甲浮雕部件，总计 494 个。
请勿重新运行旧版 gen_canglan_warlord.py 覆盖你已修改的动作。

材质使用用户确认的程序化绘制方案：16 个材料区域，包含深浅金属、
古铜蚀刻、锁甲、织物、羽纹、皮带及窄幅风能嵌线。
重建：先运行 tools/paint_canglan_surface_atlas.py，
再运行 tools/refine_canglan_surfaces.py --atlas
model-source/canglan_warlord/surface-refinement-20261022/material-atlas.png。

此前图像生成请求没有返回，未使用任何生成图片作为最终资产。
