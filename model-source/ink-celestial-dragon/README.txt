玄墨天龙 · 曲面精模初稿

用途：独立墨龙曲面精模，不替换墨螭、鹤或翼虎。
游戏接入第一版：/tnc ink dragon（需要作弊/管理员）；原版命令 /summon tnc:ink_celestial_dragon。
已有独立实体、1000 生命血条、固定空中盘旋姿态、轻微呼吸浮动、头部注视及鬃须摆动、水墨细拖尾和存档恢复；不主动攻击，暂无技能和自然生成。
游戏使用共享 GPU 静态网格缓冲，近处完整 140,414 三角面，中远处分别 125,798 / 97,254 面；不是把精模重新方块化。
召唤接口把墨龙放在面前约 30 格、脚下基准上方 2 格处，需要前方及上方开阔。
造型：中式长身盘龙，黑墨躯体，灰白腹鳞与轮廓，分叉鹿角、龙须、鬃毛、颊鳞、四肢五趾与弯爪。无西式蝠翼。
躯干曲线展开约 66.1 格；当前盘旋姿态整体包围约 20.3×20.4×7.7 格（最终数据见 model_stats.json）。

模型为真实圆润多边形网格，不含 cube 元素。共 497 个网格部件、93,614 个编辑顶点、140,414 个三角面。
鳞片具有实际立体起伏；头、角、须、鬃、各段脊身与四肢均已分组。

ink_celestial_dragon.bbmodel：Blockbench 可编辑自由模型，灰阶调色纹理已经内嵌。
ink_celestial_dragon.glb：通用 glTF 2.0 二进制模型，颜色与圆滑法线随文件保存，可用于其他 3D 软件。
ink_dragon_palette.png：独立灰阶调色纹理。
preview_full.png / preview_head.png / preview_side.png：直接从上述同一网格渲染的整体、龙首和侧面图，不是概念图，也不是游戏截图。
model_stats.json / verification.json：模型规模与导出结构校验。

编辑文件本身仍是静态盘旋造型，不含蒙皮骨骼动画。游戏中使用专用网格渲染器，按部件作轻微摆动，不是完整游龙飞行动画。
自由网格不能直接当成原来的 GeckoLib 方块 geo.json 使用；游戏资源为 assets/tnc/meshes/ink_celestial_dragon.mesh。
未使用参考图中的绿色，保留黑、灰、白的水墨配色。

生成器：D:/ModTest/tools/gen_ink_celestial_dragon_mesh.py
校验器：D:/ModTest/tools/verify_ink_dragon_mesh.py
后续手改前请另存，重新运行生成器会覆盖此文件夹中的同名生成资源。
