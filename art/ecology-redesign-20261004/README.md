# 生态美术重做 · 2026-10-04

本目录保存本轮原创设计、imagegen原始图、导出规则和实际客户端截图。用户提供的第三张图仅作为Minecraft画风参考，没有提取或复制该模组资源。

## 已完成的资产

| 内容 | 数量 | 做法 |
| --- | ---: | --- |
| 原创物品图标 | 147 | 按种子、果实、食物、皮毛、骨、器具逐件设计轮廓；32×32透明PNG、硬像素边缘 |
| 方块材质 | 16 | 16×16叶、木、铜、铁、棉、花瓣、草料、谷粒等材质 |
| 原生方块模型 | 417 | 29株植物全部年龄和外观状态、动态部件、工坊和牧场设施、7类饲料 |
| 工坊/牧场设施 | 16种 | 线圈发电机、双芯蓄能器、灯、压纸机、炉芯、烟口、木槽等各自造型 |
| 饲料堆 | 7类 | 草、谷粒、颗粒、根蔬、果、鱼、菌菇；读取食槽真实库存 |

417是模型文件数，不是417件新物品。植物图库每株展示4个代表性成长阶段；原有8龄作物的全部8龄模型也已更新。贴墙的警示苔仍贴墙，图库中另加了墙面作为依附展示。

雨书莓各年龄有湿叶提示；跳舞花的幼年花苞与茎相连，成熟时保留原来的分节摇摆；参天蔓、纸皮木的延长段也已更换。没有修改生长、经济、任务或天空岛规则。

## 文件和继续编辑

- `sprite-designs.tsv`：每个物品ID和单独设计描述。
- `manifest.json`：原图分组、顺序、风格约束。
- `masters/`：11张imagegen原创原图，包含10张物品图和1张材质图。
- `previews/items-1.png`至`items-3.png`：147个实际导出PNG的总览。
- `client-preview/`：Minecraft客户端实际渲染截图，涵盖物品、植物、设施、饲料。
- `export-audit.json`、`model-audit.json`：导出尺寸、透明度、模型ID清单。
- `generated-files.json`：生成原图的来源记录，工程已保存本地副本。

游戏资源在仓库的 `src/main/resources/assets/tnc/`：

- 图标：`textures/item/<物品ID>.png`。
- 新材质：`textures/block/art_<材质>.png`。
- 种下后的模型：`models/block/<植物ID>_<年龄>_<状态>.json`。
- 动态花朵：`models/block/dance_bell_joint.json`、`dance_bell_head.json`、`dawn_disk_head.json`。
- 饲料：`models/block/feed_hay.json` 等7个文件。
- 设施：`models/block/mana_generator.json`、`pasture_trough.json` 等。

模型是Minecraft Java原生JSON，可以在Blockbench导入编辑。材质引用在每个JSON的`textures`中。修改PNG或JSON后重新打包模组即可；注意不要再运行旧的生态资产生成脚本，否则它们会覆盖本轮新资源。

确实需要重新导出本轮原图时运行 `python tools/export_ecology_art.py`；确实需要重建本轮模型时运行 `python tools/redesign_ecology_models.py`。这两个操作都会覆盖相应源资源，应先保留手改文件。

## 饲料显示约定

食槽没有库存时为空。同种物品及相同标签跨库存格会合并显示为中央一堆，1—64份逐步加宽、加高；不同饲料分区显示。显示层只处理库存副本，不改变可取出数量。

取空后对应堆消失。食槽底高3/16格，饲料底部从此处开始，最大堆顶约0.4744格，外缘留在槽内。低量鱼料、根蔬、菌料也有可见体积。

## 验证

运行命令：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.12.7-hotspot'
./gradlew.bat test build runClient --offline -PbuildingGameTests -PecologyVisualAudit
```

该选项使用独立开发世界 `run-client-ecology-audit/`，不会打开玩家存档。客户端检查所有147件物品、29种植物的723个方块状态、16种设施的方块和背包模型、7个饲料模型。截图同时覆盖0/1/4/8/16份、32份同种跨格、第四库存格单份和低量混料。

图标/模型规则经过独立只读检查。最终编译和130项单元测试通过，14张实际客户端渲染截图均已保存。上一轮222项玩法GameTests结果不计为本轮新增测试；本轮修改集中于美术和客户端显示。

本轮验证范围为Minecraft开发客户端原生物品、方块、方块实体渲染；完整整合包的光影环境没有在这组图库中复现。安装时只替换两处TN-C JAR，保留全部任务章节、玩家世界和天空岛资源，并保存旧JAR备份。
