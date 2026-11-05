# 传统兵器源文件

权威几何定义与材质：`tools/weapons/traditional_weapons.py`。

生产模型位于 `src/main/resources/assets/tnc/models/item/`，同名 PNG 位于 `textures/item/`；低成本攻击虚影位于 `models/item/warrior_ghost/`。十把 ID 及块数见本目录 `manifest.json`。

招式数据和 87 份动作由 `tools/weapons/martial_arts.py` 生成，57 个招式列表见本目录 `martial-moves.md`；`tools/weapons/preview_martial.py` 使用生产关键帧和匹配虚影模型生成离线骨架预览。

运行 `python tools/weapons/traditional_weapons.py` 可单独重建本批资源。手工修改生产模型前应保留副本；再次运行生成器会覆盖同名产物。预览与侧面细节图位于 `docs/previews/weapon-<id>*.png`。形制来源、属性和配方见 `docs/warrior/中国传统兵器-20261105.md`。
