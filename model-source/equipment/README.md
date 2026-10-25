# 法杖与穿戴装备：立体工程

2026-10-24 重做。这里的70个 Blockbench 工程对应游戏实际读取的模型，并内嵌独立的手持/穿戴贴图。

- `wands/`：36支法杖，包含基础杖与七系五阶。握柄中心为 `(8,5,8)`，手持模型和物品栏图标分开。
- `armor/`：34件帽子/全身装。四个常规款式各四阶，加一套独立神装。保留七个基础骨骼名称及衣摆分组。
- `volume-redesign-manifest.json`：模型清单、结构信息和74张原图标的校验值。

当前法杖是自创三维网格，不是原图像素加厚。每系采用独立杖头：水系潮汐冠、火系龙爪焰核、雷系分叉导角、风系羽刃、土系地脉晶冠、光系日轮星仪、暗系弯月。可从前后侧观察浮核、护架、环绕件的实际厚度。高阶加入更完整的外冠、轨道和卫星晶体。

法杖工程使用 Blockbench 的自由网格格式；直接编辑网格顶点和面，保留握柄中心与 `display` 握持设置。图标继续使用原有二维原画。

动态材质：每个面附带 `tnc` 元数据，记录材质、构件、自转轴心和环绕方式。Blockbench 保存会移除自定义字段，导入器通过保留的面编号找回运行模型中的材质与动画；删除、重排、再次导出后也保留编号。新建且没有元数据的面默认按静态金属处理。`textures/item/wands/held/*_emission.png` 是独立晶体发光贴图，编辑基础色不会自动重画发光层。动态光晕及自转由游戏渲染器绘制，Blockbench 静态预览不包含这些效果。

在项目根目录导入改好的工程：

```powershell
python tools/equipment_blockbench.py --import-model model-source/equipment/wands/water_wand_1.bbmodel
python tools/equipment_blockbench.py --import-model model-source/equipment/armor/astral_outfit_1.bbmodel
```

导入会同步该工程的独立模型和内嵌贴图，不会覆盖原物品栏图标。导入后运行 `python tools/verify_equipment_models.py` 检查往返编辑和图标完整性。

完整说明见 `docs/tools/Blockbench法杖与装备编辑说明-20261004.md`。`tools/rebuild_equipment_models.py` 调用 `tools/rebuild_sculpted_wands.py` 生成法杖并生成穿戴装备；开始人工编辑后不要运行生成器覆盖作品。需要重新导出当前资源时，使用 `tools/equipment_blockbench.py`，也应先备份自己改过的工程。
