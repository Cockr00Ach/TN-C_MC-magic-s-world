# Blockbench：法杖、帽子和全身装备源文件

这里提供真实可编辑的`.bbmodel`工程，已导出36个法杖工程与34个穿戴装备工程，模型内嵌贴图，可直接打开。不是只给物品栏PNG让你改。

## 1. 打开哪个文件

项目根目录：`C:/Users/宋志坤/Documents/Codex/2026-09-19/mu-q/work/TN-C_MC-magic-s-world`

- 法杖：`model-source/equipment/wands/*.bbmodel`。比如`water_wand_1.bbmodel`、`magic_wand.bbmodel`。
- 实际穿戴帽子/全身装：`model-source/equipment/armor/*.bbmodel`。比如`astral_hat_1.bbmodel`、`astral_outfit_1.bbmodel`。
- 款式：`bastion`堡垒战装、`astral`星织法袍、`runic`符纹锁甲、`wanderer`游历轻装。后缀1—4对应冒险者、精练、大师、传说；`divine_hat_5`和`divine_outfit_5`是单独的神装。

Blockbench“文件→打开模型”，选择工程。法杖使用Minecraft Java物品格式；穿戴装备使用Modded Entity格式。后者由模组专用加载器读取，不是原版Java物品JSON。

## 2. 法杖：模型和拿法分开改

在编辑页改变立体结构；在“显示/Display”页分别修改第一人称右手、第一人称左手、第三人称右手、第三人称左手的旋转、位置和大小。这四组互不等价，请逐个预览。物品栏显示另在GUI项，不必为了修手持把物品栏图也改掉。

目前工程由现有像素轮廓还原成薄立体结构，你可以直接增删方块、加厚杖头和握柄。Java物品的单方块旋转只支持单轴0、±22.5、±45度；更自由的结构要拆成几个方块。导入工具会拒绝不支持的旋转，避免做完游戏读不进。

保存`.bbmodel`后，在项目根目录PowerShell运行：

```powershell
python tools/equipment_blockbench.py --import-model model-source/equipment/wands/water_wand_1.bbmodel
```

写入：`src/main/resources/assets/tnc/models/item/water_wand_1.json`。

如果你通过Blockbench直接“导出→Minecraft Java模型”，需要确认贴图引用仍指向原来的`tnc:item/wands/...`。推荐用上面的工具保留正确贴图地址。

## 3. 装备：穿上身的形状怎么改

请保留最外层七个骨骼名称：`head`、`hat`、`body`、`right_arm`、`left_arm`、`right_leg`、`left_leg`，它们负责跟随人物动作。可以在骨骼下增加/删除子组、方块，改变尺寸、UV与膨胀值。

需要旋转形状时旋转骨骼组，不要单独旋转方块；保持Box UV。这样导入工具可以把模型还原为Minecraft实体的骨骼与CubeDeformation。不要把裤腿、鞋子删成两件独立物品，全身装仍是一个装备槽，模型覆盖身体、腿和脚。

```powershell
python tools/equipment_blockbench.py --import-model model-source/equipment/armor/astral_outfit_1.bbmodel
```

写入：`src/main/resources/assets/tnc/models/armor/astral_outfit_1.json`，游戏中的穿戴渲染现在会读取该文件。原先形状写在`equipment/client/GearModels.java`，本轮增加资源模型加载，不再要求你写Java才能改外形。

## 4. 贴图怎么改

工程已内嵌贴图。你在Blockbench绘制后，请将PNG导出覆盖对应资源文件；模型导入命令不会擅自覆盖PNG。

- 法杖贴图：`src/main/resources/assets/tnc/textures/item/wands/`，基础`magic_wand`以工程里显示的原贴图路径为准。
- 装备穿戴贴图：`src/main/resources/assets/tnc/textures/models/armor/`。
- 装备物品栏图：`src/main/resources/assets/tnc/textures/item/equipment/`，这是独立图标，修改它不会改变穿戴外形。

## 5. 装回游戏

改完可直接把工程和PNG交给当前Agent，说明哪些文件改了，Agent导入、编译、安装、测试即可。自己操作时使用Java17，项目根目录执行：

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.12.7-hotspot'
./gradlew.bat build --offline
```

彻底退出游戏后，把`build/libs`中新构建的主模组JAR替换到运行整合包的`mods`；只保留一个TN-C主模组。重新启动游戏检查穿戴动作和四种拿法。穿戴模型有缓存，不能只依赖F3+T。

当前整合包的KubeJS或其他资源包若含同名模型/贴图，会覆盖JAR。Agent安装时必须检查同名覆盖；新同伙也要明确运行目录，别编译进一个目录却启动另一个目录。

导出脚本无参数运行会重新生成原始模板工程。自己开始修改后不要无参数重跑覆盖作品；只对改好的工程使用`--import-model`。推荐先复制工程再修改，保留原版对照。

## 6. 你的修改交接

|工程文件|改模型/贴图/显示变换|希望看的效果|是否所有阶级一起改|
|---|---|---|---|
|填写|填写|填写|填写|
