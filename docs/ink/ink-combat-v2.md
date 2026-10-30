# 水墨三相战斗调整

相对原版本：鹤本体 1.5 倍，翼虎和墨龙本体 2 倍。鹤本体骨骼与几何不改，虚影翼骨沿翼长方向额外 1.5 倍；横扫判定半径由 14 改为 28。

鹤俯冲会生成旋转墨阵并短时定身目标，落地、取消或死亡释放。虎空闲绕行并注视玩家，四个方向的幻影在 22/38/54/70 tick 分批释放，冲刺速度由 1.15 改为 2.3。虎头上方的聚墨球随半径上移，球体下缘保持在头部之上。近距离非创造玩家血量不超过一半时可触发 64 tick 抓咬，受击者使用头部近景镜头。

龙落墨改为七根粗墨柱，外围柱间距半径 30。第四招改为入地、旋转墨阵、16 tick 破地上升、强制升空、空中聚墨吸引爆炸。定身使用短期服务端位置约束，客户端只限制移动，不限制视角；不会永久修改玩家属性或重力。施法呼啸通过维度内有距离限制的网络包触发衰减镜头震动。

## 法阵资产

使用内置 imagegen 对用户参考图提取透明背景，未更改原图。运行时文件：`src/main/resources/assets/tnc/textures/entity/ink_vortex_seal.png`。

生成提示词：

> Use case: background-extraction. Asset type: Minecraft boss ritual ground decal texture. Input image 1 is the edit target. Preserve the reference's three sweeping asymmetrical black ink crescents, delicate smoky wisps and circular overall composition. Remove ONLY the solid white background including white open spaces inside the circle and replace it with genuinely transparent alpha. Keep black ink and semi-transparent gray veining/wisps with soft natural edges. Top-down flat texture, centered with a small transparent margin, square 1024x1024, no text, no symbols, no added objects, no solid white disc, no checkerboard baked into the image. This will rotate on the ground underneath a player.

战斗音乐及素材授权见 `ink-boss-audio.md`。发布采用覆盖包合并，保留正式整合包内非本次范围条目。

## 验证及安装（2026-10-21）

- `test jar` 构建成功；402 项单元测试，0 失败。
- `runGameTestServer -PinkCraneTests`：30 项实机服务端测试全部通过，含半血处决门槛、抓取解除、四方向虚影的释放次序及速度、鹤定身清理、龙破地携带目标上升超过 40 格、墨柱遮挡及伤害。
- 客户端两次在 AMD OpenGL 原生驱动 `atio6axx.dll` 初始化中崩溃（尚未进入游戏），因此本次音乐实际播放、镜头构图和新贴图的游戏内画面未完成复核。旧截图及旧客户端审计报告不能代表当前版本。
- 已安装正式包 `E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/mods/tnc-1.0.0.jar`，SHA256：`8617AB49223972E7C1B5CC1CF936F5EBF1F0EE27FC29DBF06015D795A46B318D`。
- 安装前备份：`D:/ModTest/work/install-backups/ink-combat-v2-20261021-201314/tnc-1.0.0.jar`。16,764 个无关条目逐字节保留，未修改正式存档或其他模组。

## 后续：光影兼容、处决镜头与墨魇（2026-10-21）

已修复并安装 v3：

- 龙改用标准 NEW_ENTITY 顶点属性、带法线的四边形提交及普通实体材质。保留原始自由网格及三档 LOD，连续摆动改为 CPU 波形表；不再直接绑定自定义 GL 程序或覆盖全局投影。已追踪的龙走正常实体通道，未追踪远景仍有独立代理，避免重复绘制。
- 山水、法阵和黑墨虚影改走标准带纹理透明材质，不强占主帧缓冲，也不把黑墨归类为发光材质。停用维度强制加载原版 desaturate 的方式；改为光影完成合成后处理颜色，输出 alpha 固定为 1。普通维度去色在 HUD 前执行，墨魇则在最终帧执行，覆盖物品栏和背包界面。
- 盔甲架镜头同步头部朝向及上一帧头部朝向，修正 LivingEntity.getViewYRot 读取不到身体朝向的问题。实际处决近景截图已经能看见虎头、牙齿和被抓取的玩家。
- 龙新增第五招：旋转墨阵、约 2 秒近身飞行、约 4 秒张嘴长啸，墨丝从受害者汇向龙嘴；随后恢复空中姿态。命中且无遮挡时施加 240 tick（12 秒）墨魇。移动及攻击速度乘 0.25，喝药/食物/蓄力使用计时每四 tick 前进一步，挥手时长乘四，挖掘速度乘 0.25。
- Spell Engine 施法时长乘四、动画速度乘 0.25，并处理已经开始施法的进度切换；持续施法的脉冲也降频，避免延长施法反而造成四倍伤害。普通瞬发技能仍然瞬发，不修改整个世界的 tick 速度。死亡/换维度/效果到期会解除对应状态；牛奶可以清除。效果图标复用原版缓慢图标。

验证：406 项单元测试和 32 项真实服务端 GameTest 全部通过，覆盖近身距离、开口、状态施加与恢复、32 tick 药水约 128 tick 完成、收招后返回安全高度；另对本地 Spell Engine 0.15.12 的实际字节码校验了注入目标签名。独立测试环境没有完整 Spell Engine/Connector，因此完整整合包中的施法动画与中途施法重计时仍需回归，不将接口校验冒称为整包实测。

已使用同版本 Oculus 1.8.0、Embeddium 0.3.31、Complementary Unbound r5.6.1 在 NVIDIA RTX 3050 Ti 上完成三阶段原生客户端审计：7 张截图，龙头及身体可见，玩家和地面不再透出背景，黑墨法阵不是白片，处决镜头指向正确，三阶段音乐均实际播放并在战斗结束停止，OpenGL 错误码 0。背包失色截图的 R/G 和 G/B 最大差值均为 0。此为隔离存档验证，不声称是整个整合包的 FPS 基准。之前 AMD 初始化崩溃记录属于旧的开发运行环境，不再是当前验证阻塞。

当前正式模组 SHA256：`0024EA811607D16603602DDD8108FFAB318C46B14953F1D3BF2A6C0556330A28`。备份：`D:/ModTest/work/install-backups/ink-shader-drain-v3-20261021-231658/tnc-1.0.0.jar`。以当时最新正式包为基础合并，保留 16,766 个无关条目，保留原有 Manifest 属性并仅追加水墨 Mixin 配置，未修改正式光影设置或存档。
