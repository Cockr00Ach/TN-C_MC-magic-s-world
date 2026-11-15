火手同源模型与预览 / 2026-11-13

模型源：src/main/java/com/tnc/tnc/client/FireHandPose.java 与 FireHandMesh.java，以及共用 FireFlamePalette.java。
FireHandRenderer 直接提交这里生成的同一批顶点；没有第二套只用于美化预览的几何。

fire-hand-tick-22.glb：张开；fire-hand-tick-36.glb：第一下捏紧；fire-hand-tick-66.glb：爆开松掌。
GLB 是上述三个时间点的静态可编辑网格快照，不是带运行骨骼的 bbmodel。
PNG 和 GIF 为同源顶点的离线普通 alpha 合成，供轮廓与抓握检查；不是 Minecraft 实机截图，
没有额外添加 bloom、屏幕折射或离线专用粒子。游戏中的热扰动由游戏渲染通道负责。

重建：
1. javac -d work/fire-hand-refinement-20261113/classes src/main/java/com/tnc/tnc/client/FireFlamePalette.java src/main/java/com/tnc/tnc/client/FireHandPose.java src/main/java/com/tnc/tnc/client/FireHandMesh.java tools/FireHandModelExport.java
2. java -cp work/fire-hand-refinement-20261113/classes FireHandModelExport work/fire-hand-refinement-20261113/mesh
3. python tools/preview_fire_hand_runtime.py

维持服务器 76 tick 生命周期与 36 / 46 / 56 三次捏、62 tick 爆开。
地面模型最多 2627 quads，空中模型 3083 quads；只用两个标准实体材质类型，
不采用 lightning / WEATHER_TARGET，不靠同一张面双重提交来增亮。

粗壮版比例：前臂/腕径 ×1.7，掌宽/指根间距 ×1.30，掌厚 ×1.4，四指径 ×1.35。
掌厚向掌背扩张，保留相同 grip 中心与内掌空间；不是仅拉长原模型。
T4 使用蓝鬼火（深蓝外缘、钴蓝主体、青蓝细缝、青白小核，运动速度0.45），
T5 使用橙红结构、电蓝细缝、白炽小核，T1–T3 保持橙红火。
新增 fire-hand-fire-palettes.png 为三种真实材质/真实档位尺寸对照，另有蓝火/离子火静态 GLB。
