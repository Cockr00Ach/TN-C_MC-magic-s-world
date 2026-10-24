# 法杖与穿戴装备：立体工程

2026-10-24 重做。这里的70个 Blockbench 工程对应游戏实际读取的模型，并内嵌独立的手持/穿戴贴图。

- `wands/`：36支法杖，包含基础杖与七系五阶。握柄中心为 `(8,5,8)`，手持模型和物品栏图标分开。
- `armor/`：34件帽子/全身装。四个常规款式各四阶，加一套独立神装。保留七个基础骨骼名称及衣摆分组。
- `volume-redesign-manifest.json`：模型清单、结构信息和74张原图标的校验值。

在项目根目录导入改好的工程：

```powershell
python tools/equipment_blockbench.py --import-model model-source/equipment/wands/water_wand_1.bbmodel
python tools/equipment_blockbench.py --import-model model-source/equipment/armor/astral_outfit_1.bbmodel
```

导入会同步该工程的独立模型和内嵌贴图，不会覆盖原物品栏图标。导入后运行 `python tools/verify_equipment_models.py` 检查往返编辑和图标完整性。

完整说明见 `docs/tools/Blockbench法杖与装备编辑说明-20261004.md`。`tools/rebuild_equipment_models.py` 是本轮原始造型生成器；开始人工编辑后不要运行它覆盖作品。需要重新导出当前资源时，使用 `tools/equipment_blockbench.py`，也应先备份自己改过的工程。
