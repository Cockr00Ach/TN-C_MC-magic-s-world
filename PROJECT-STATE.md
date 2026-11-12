> **2026-11-12 03:23 装机：战士「终极技」这批新内容（队友 01:3x~01:5x 的改动）已打包进实例**：作者「刚刚新增了一点东西，你帮忙装机一下，是战士相关的」。**这批新增**（未提交，工作区里）：`warrior/WarriorUltimates` / `WarriorUltimateService` / `WarriorUltimateInput` / `WarriorBladeEcho`、`warrior/client/WarriorUltimateCamera` / `WarriorUltimateEffects`、数据 `assets/tnc/warrior/ultimates.json`（**31 条**）与动画 `assets/tnc/animations/entity/warrior_ultimates.json`、以及 `WarriorComboAnimations` / `WarriorTrail` / `WarriorPoseMath` / `WarriorComboService` / `WarriorAnimation` 等改动；配套测试 `WarriorUltimateTest`（每把武器专属演出 + 十段输入；超时/换武器/连点不能凑满；突刺与横扫命中形状不同；幻影刃几何有界且会过期）与 `WarriorComboAnimationsTest`。**验证**：`warrior.*` 全量 **213 用例 / 53 套件全绿**（03:22），`build -x test` 成功。**装机**：`mods/tnc-1.0.0.jar` = **200,756,983 B、SHA `ED556BAD110ADBDDD83419971EDD4B7D529C17FD0BF3C350CE921F7A6F743A87`**（03:22:59 构建、03:23:18 装入；装前 0 个 java 进程；装前那版（同尺寸队友构建 `DBC08CCF…`）已备份 `…jar.bak-20261112-032317`；回读逐字节一致；包内核对 `WarriorUltimates` / `WarriorUltimateService` / `WarriorUltimateInput` / `WarriorBladeEcho` / `WarriorUltimateCamera` / `WarriorUltimateEffects` / `ultimates.json` / `warrior_ultimates.json` 全部在，战士相关文件 238 个）✓。**下次启动游戏即生效**。**注**：这批不是我写的代码，我只做了"跑测试 → 构建 → 装机 → 核对包内文件"，没有改一行 ✓。

> **2026-11-11 18:30 黑雾「瞬间拉进来 + 体积随档位一起长（宽＋高）」已实装**：作者「噢噢那个黑雾不是可以拉周围的敌人吗，我要你瞬间给他拉进来，然后黑雾的大小也要随着 t 级增长变大，你别只加宽度，高度也要加」。**①以前**：`DarkFogCloudEntity.gripIntruders` 每 6 tick 给目标一个 `0.10×距离` 的小速度（要拽好几秒才进得来 ✗，像"很多手慢慢蹭"）；体积只有 JSON 的 `volume.radius` 随档位长（4.5/5/6/7.5/9 ⇒ 宽 9→18 格），**高度是渲染器写死的 `1.1+档位×0.1`**（1.2→1.6，几乎不变 ✗）⇒ 高档黑雾是"又宽又扁的一层饼" ✗。**②瞬间拉**：够到抓取半径（雾半径 ×1.45：t1 6.5 → t5 13 格）且高度在圆柱内 ⇒ 直接 `victim.teleportTo(...)` 到离雾心 **45%×半径** 处（t1≈2.0 → t5≈4.1 格，至少 0.6 格防抖），清速度、清 `fallDistance`、补一串 `SQUID_INK` 黑雾粒 ⇒ "被一把抓进去" ✓。**③体积宽高一起长**：新增纯规则类 `magic/DarkFogRules`（`HEIGHT_RATIO=0.5`、`MIN_HALF_HEIGHT=2.0`、`INSIDE_RATIO=0.45`、`gripRadius`、`insideVolume`）—— 半高 = 半径×0.5 ⇒ t1 宽 9 / 总高 **4.5**、t5 宽 18 / 总高 **9**（一起翻倍 ✓，以前高度只有 2.4~3.2 ✗）；`DarkFogCloudEntity` 新增同步字段 `DATA_FOG_HEIGHT` + `syncedHeight()`，抓取判定从"只看水平"变成**圆柱**（`|目标中心−雾心| ≤ 半高+半身位`，悬崖上的人不再被隔空拽进雾底 ✓），`DarkMistRenderer` 用 `entity.syncedHeight()` 散布雾片（不再全挤脚边 ✓）。**④两个有意例外**：Boss 领域雾（半径>64）**不瞬移**（150 格半径瞬移会把玩家甩到 BOSS 脸上 67 格 ✗，仍只轻推）且半高仍 3.2（150×0.5=75 格高会糊满屏 ✗）✓。**⑤闸门**：新增 `DarkFogRulesTest` **6 条**（核心：宽度和高度都必须随档位严格变大；半高=半径×0.5 且有 2.0 下限；领域雾半高 3.2、抓取封顶 96；抓取 >雾半径×1.3、落点 <半径×0.5 且 ≥0.6；圆柱判定雾内/高度内 ✓、头顶太高/半径外 ✗；源码扫描 `victim.teleportTo(`/`SQUID_INK`/`DarkFogRules.halfHeight(`/`insideDistance(` 都接上，且 `if (domain) {` 必须排在 `teleportTo` 之前，渲染器必须读 `syncedHeight()` 且不再有 `1.1F+tier*.1F`）。**⑥回归**：`magic.*` + `client.*` + `warrior.*` + `world.wind.*` **501 用例 / 120 套件全绿**。**⑦装机**：`mods/tnc-1.0.0.jar` = **200,701,925 B、SHA `09D4BB9512E1E28F352DFDD348DD9575190E3A70FEA12F5E99360F2E384046BA`**（18:29:12 构建、18:30:42 装入；构建时 java=1 故后台等退出才替换；装前那版已备份 `…jar.bak-20261111-183042`；回读逐字节一致；包内 `javap` 确认 `DarkFogRules.HEIGHT_RATIO=0.5`/`INSIDE_RATIO=0.45`、实体引用 `minecraft:squid_ink`、渲染器调用 `syncedHeight()`）✓。**下次启动游戏即生效**。**待你定**：要不要把 JSON 半径本身再放大一档（现在 t5 半径 9 / 宽 18 格）、被拽进雾里要不要再叠"黑暗"（现在只有减速）、拽进来那下要不要音效。详见[docs/spells/黑雾瞬拉与体积-20261111.md](docs/spells/黑雾瞬拉与体积-20261111.md)。

> **2026-11-11 14:32 风灵「出手方式分档 + 吸力有画面 + 命中炸开」已实装**：作者「风灵再改改，我们现在风灵的攻击手段是啥」。**改之前的手段（回答）**：风灵是真实体（无 AI、不可被攻击、绕主人转：半径 1.9 格 / 高 1.15 格 / 80 tick 一圈），**每 10 tick 出手一次**——① 找 `5.5+档位×0.5` 格内有视线的非玩家/非队友敌人；② 最多 12 个敌人被**朝风灵方向吸**（`0.08+档位×0.012`、上限 0.55 格/tick，**无任何画面** ✗）；③ 朝**最近一个**敌人发射**1 把** `WindWeaponEntity`（伤害 `4×档位系数`＝4/4/8.8/8.8/11.2、速度 `1.15+档位×0.1`、寿命 32 tick、同玩家最多 16 把、4 种形态按档位轮换、命中只掉血 + 5 颗粒子 ✗）；每档风灵数 **3/5/1/3/5**。**这一轮改了什么**：①**出手方式分档**（`WindArsenalRules.shots`）：t1/t2 单发、t3/t4 **双发**、t5 **三发**，目标从近到远挑且彼此**至少隔 1.5 格**（不然几发糊在同一只身上 ✗）；t5 每 **5** 次出手来一轮**风环齐射**（这一只 4 连发 + 风灵身上炸一圈 `SWEEP_ATTACK`/云）。②**多发≠多倍伤害**：单发按 **1/√n** 分摊（`shotDamageFactor`）⇒ 双发总伤 ×1.41、三发 ×1.73（不是 ×3 ✗），齐射那轮再 ×0.75。③**吸力有画面**：每个被吸目标甩 3 颗云粒沿"目标→风灵"线飞（+2 颗在起点）✓。④**命中炸开**：`WindWeaponEntity.burst(...)` —— 半径 `1.0+0.12×档位` 格内的其它敌人吃主伤害 **35%** 并被轻推，补一圈 `SWEEP_ATTACK` + 8 颗云 ⇒ 每发都看得出"斩到了" ✓。**闸门**：新增 `WindArsenalRulesTest` **6 条**（发数 1/1/2/2/3 且越界夹取；只有 t5 有齐射（每 5 次、4 连发）；单发系数就是 1/√n 且三发总倍率 1.5~1.9；炸开半径 1~2 格且档越高越大、占比 0.2~0.5；出手间隔仍 10 tick；**源码扫描** `shots(`/`pickTargets(`/`shotDamageFactor(`/`inwardStreak(`/`volleyShots(`/`SWEEP_ATTACK`/`burst(victim)` 全接上）。**回归**：`magic.*` + `client.*` + `world.wind.*` **286 用例 / 67 套件全绿**。**装机**：`mods/tnc-1.0.0.jar` = **200,700,238 B、SHA `124D68B6D63AD2EFAB6D7206301FE3CDCC8F5AB9CFBCE6BFF089BCEAC395A81A`**（14:31:55 构建、14:32:14 装入；构建时 java=1 故后台等退出才替换；装前那版已备份 `…jar.bak-20261111-143214`；回读逐字节一致；包内 `javap` 确认 `shots`/`volleyEvery`/`volleyShots`/`shotDamageFactor`/`burstRadius`/`burst` 都在）✓。**下次启动游戏即生效**。**待你定**：t3 只有 1 只风灵要不要改成 2 只×单发；齐射要不要加真的环形伤害；风灵要不要一个"出手"动作；4 种形态要不要各带不同效果（穿透/吸附）。详见[docs/spells/风灵攻击手段-20261111.md](docs/spells/风灵攻击手段-20261111.md)。

> **2026-11-10 22:14 雷球改动全部撤回 ＋ 登神链雷龙位置修复 ＋ 风球线条改「漩涡/三倍/渐变」已实装**：作者「登神链的雷龙显示位置异常，然后雷球的改动全部撤回」＋「风系的那个线条……没必要还是那样组成一个球，你可以给我漩涡的那种感觉。并且线条数可以变为现在的三倍，不过我不要一样的线条，你可以多做一些渐变线条」＋「龙卷风不用改」。**①雷球全部撤回**：删掉 `OrbStormRules` / `OrbStormService` / `ThunderOrbAura` / `ThunderOrbAuraRenderer` 与 `OrbStormRulesTest` / `ThunderOrbAuraTest`，去掉 `TnSpellMechanics.onServerTick` 里的 `OrbStormService.tick(...)`，5 个雷球 JSON（thunder_orb / great_thunder_orb / explosive_thunder_orb / cataclysm_thunder_orb / god_descent）**git checkout 回 HEAD** ⇒ `travel_particles` 回来、`light_emission`/`rotate_degrees_per_tick` 去掉、impact 粒子回到原值 ✓；**唯一偏离 HEAD**：`god_descent.json` 的 `cost.exhaust` 从 1.0 改回 **0.0**（项目规矩"引擎 exhaustion 一律 0"，否则 `SpellBalanceTest` 红 ✗）。**②雷龙位置异常**：诊断 —— 缠绕形态在 `CoilDragonOverlay`（AFTER_ENTITIES）里画，原来读**雷龙实体自己**的位置 `entity.getPosition(partial)`；而 `TNLightningDragonEntity` 是**非生物实体**，位置包大约**每 20 tick**才发一次（`tick()` 在客户端直接 return，自己不会修位置），可它每 tick 都在 `setPos(wearer...)` ⇒ 玩家一跑一跳，龙最多**晚 1 秒**，表现为"拖在人后面/浮空" ✗。修法：新增 `wearerPosition(mc, dragon, partial)`，按 `ownerId()` 先 `getPlayerByUUID`、再在 `entitiesForRendering` 里按 UUID 找（非玩家穿戴者也支持），读**客户端带插值**的位置，找不到才退回实体位置 ⇒ 延迟从最多 1 秒变 0 ✓；龙本身的几何/半径/高度/转速**一个字没改** ✓。**③风球线条**：`WindBallLines` 从"绕成球壳"改成 **漩涡漏斗**（半径 `R·(0.28→1.05u)`、高度 `±1.2R`、绕轴打旋），条数 **3×（t1=15 …… t5=27）**，并且每条线自带渐变（主线 1.15~1.70 宽 / 0.80~1.00 亮 / 白或青绿；淡线 0.45~0.90 宽 / 0.30~0.70 亮 / 深青或青绿），**淡线只占 30~55% 高度的短风丝**（否则 10 段铺满 2.4 圈会被拉成大斜线 ✗）⇒ 亮白细丝 + 青绿中层 + 深青宽扫 ✓；渲染器仍是"先原有 `WindConstructModels.orb` / `cage`，再叠漩涡线"（顺序有闸门）✓。**④预算**：t1 漩涡 5280 顶点、t5 漩涡 9504、t5 炸开 1776（闸门 ≤12000）✓。**⑤闸门**：`WindBallLinesTest` 7 条（漩涡不是球：主线跨负→正高度、上口 ≥ 下口 1.8 倍、任何线不出口径；条数正好三倍；渐变：宽度 ≥6 种/亮度 ≥5 种/配色 ≥2 档、主线比淡线粗 1.5 倍亮 1.4 倍；自转换位置；冲击波外扩；预算；渲染器"原有+漩涡"两层且 orb 在 vortex 之前）。**⑥龙卷风一行没碰**（TNTornadoEntity / TornadoLineShape / WindTornadoLines / WindTornadoRenderer 全未改）✓。**⑦回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **489 用例 / 118 套件全绿**（撤掉雷球两套测试后由 497 降为 489；中途树上有队友在改 `WaterGateRulesTest`/`TNWaterGateRenderer`，等他们可编译才跑），`SpellBalanceTest` 7/7 ✓。**⑧装机**：我这版 **200,697,790 B、SHA `9B25544E0F75D77F0EB7F6A6BE9A76AC5ED28BF285A819DAD34647D62D9236FD`**（22:13:48 构建、22:14:07 装入、构建时 java=3 故后台等退出才替换、备份 `…jar.bak-20261110-221407`、回读一致）；随后**队友 22:13:06 的构建又覆盖了一次**（200,697,790 B、SHA `69313104…`）—— 已解包核对：`CoilDragonOverlay`（含 `wearerPosition`）、`WindBallLines`（含 `vortex`/`count`/`boldCount`）都在，`OrbStormService`/`ThunderOrbAuraRenderer` 不在，`thunder_orb.json` 的 `travel_particles` 在、`RADIATE` 无 ⇒ **我的改动全在里面** ✓，故不再覆盖（再覆盖会把队友那部分退回去 ✗）。**下次启动游戏即生效**。详见[docs/spells/雷球撤回与雷龙位置-20261110.md](docs/spells/雷球撤回与雷龙位置-20261110.md) 与[docs/spells/风球-线条化-20261110.md](docs/spells/风球-线条化-20261110.md) 第三轮。

> **2026-11-10 22:15 水波根部收细（从细到粗再恒定）＋ 齐射法阵钉在原地、只排 240° 扇面且离敌人更远 已实装**：作者「t4t5 发射水波时，水波的根部应该从细到粗然后保持一样，也就是法阵那个根部要细细的，不然就很突兀」＋「几座法阵出现就在原地，不要跟着玩家，跟着玩家的只有那两个法阵，然后法阵出现的位置可以再拉开一点，并且离敌人远点」。①**根部收细**：`LightningBoltGeometry` 新增 `polylineTapered(...)`（逐点宽度倍率 ✓，段两端各用自己的倍率 ⇒ 过渡连续 ✓；老 `polyline`/`ribbon` 全部原样转发 ×1.0，落雷/风线/暗线一个字节没变 ✓）；`WaterBeamVisuals` 里加 `rootTaper(ts)`：阵口处只有 **10%** 粗，走到全长 13% 处长到 100%，之后**一直 100%** ✓（作者要的是"从细到粗然后保持一样"，不是整根锥形 ✗）；为此把折线的 t 也改成**根部加密**（0 / .05 / .12 …）——均匀分点会把锥形拉满整段 ✗。②**法阵钉在原地**：`TNWaterGateEntity.tick` 只在 `IDLE` 阶段 `setPos(owner)` ✓ —— 一开阵，实体就停在原地当**齐射锚点** ✓（玩家打完这一下跑开，阵还留在原处 ✓）；射水波/汇聚点全部改用 `position()`（锚点）而不是玩家当前脚下 ✓。③**只排 240° 扇面、离敌人更远**：新增同步字段 `AWAY_YAW`（触发那一刻由"敌人→玩家"的朝向定死 ✓，目标跑动也不会让阵跟着转 ✗）；`WaterGateRules.portalPos(anchor, tier, index, count, awayYaw)` 改成**以背离敌人为中心、张角 240° 的扇面** ✓（朝敌人的那 120° 一个阵都没有 ✓）；环半径 3.2/4.4 → **4.2/5.8** ✓；`convergePos(anchor, awayYaw)` 改成"玩家和敌人之间身前 2.2 格"✓（法阵从背后收拢到身前、再朝敌人射出去 ✓）。④**测试**：`WaterGateRulesTest` 13 条（新增/改写「阵全落在同一条弧上且在胸口高度」「朝敌人的 120° 扇区必须空着」「扇面张角 = 240°」「汇聚点在玩家与敌人之间」「换边跟着翻」）；`WaterVisualsCostTest` 仍然过（t4 近 368 / t5 近 1712、远 576，预算 1800 ✓）；全量 `test` **全绿**（22:10）。⑤**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,697,790 B、SHA `69313104C475D73B45B3F1F90913F2EE61CE3D1C5C6B0C542CFCAF1B59F91D89`**（22:13:06 构建、22:14 装入，`identical: True` + `mod jar verification passed`，装前确认无游戏进程、jar 未被锁 ✓）。**需完全重启游戏生效**。**本轮没进客户端实看**：根部锥形那 5 格够不够"从细到粗"（`ROOT_MIN` 0.10 / `ROOT_SPAN` 0.13 两个数）、240° 扇面够不够开、阵锚在原地会不会离玩家太远导致水波从背后飞出去（`ringRadius` 4.2/5.8）都要试玩再调。
>

> **2026-11-10 21:33 罗马圣柱第二版（做精致：古典柱式解剖 + 柱头浮雕）已实装**：作者「圣柱有点丑，不够精致，尽管做精致」＋「你搜搜罗马圣柱的图片，借鉴一下」（⚠️ 本机 `web_search` / `web_fetch` 都失败 ✗ ⇒ 按**古典柱式标准解剖**做：阿提卡柱础 / 带凹槽与收分的柱身 / 科林斯柱头）。①**柱础（11 块）**：方座 plinth（＋45° 垫块 ⇒ 八角轮廓）→ 斜边 → **下圆盘 torus** → 亮金线 → **凹弧 scotia** → **上圆盘 torus** → 小圆环 annulet → 收颈 → 方领。②**柱身（7 块）**：8×8 芯（贴图 8 条凹槽 flutes）＋**四角棱条**（读作八角柱）+ 上下亮金线；渲染时**分三段、直径 100%/98.5%/97%** ⇒ 古典柱的**收分 entasis** ✓（零额外方块 ✓）。③**柱头（8 块）**：小圆环 → 收颈 → **钟形 kalathos 三层**（金 / 石 / 水波纹金）→ 方板下缘 → 方板顶面 → 珍珠线。④**柱头浮雕（新模型 `roman_column_ornament`，20 块）**：**八片茛苕叶**（向外倾 22.5°、露在钟形外 ✓）＋**四角涡卷**（每角两块错开 ✓）＋**方板每面一朵玫瑰花** ✓ —— 只给**离镜头 18 格内**的那几根画 ✓（逐根判距离，`romanColumns(..., camera)` ✓）。⑤**贴图**扩到九格：柱身 8 条竖凹槽 / 浅大理石（斜向矿脉+高光）/ 金饰带（高光线）/ 深石方格 / 横向凹线 / 水波纹 / 亮金 / 白高光石 / **茛苕叶（中肋+叶脉）**。⑥**柱距**放到 4.2 格（上限 22 根）⇒ t1 8 / t3 18 / t5 22 根，粗细 1.08 / 1.94 / 2.60 格（6.5~9:1 ✓）。⑦**面数**：t5 远处 3432 quads / 近处（身边 6 根带浮雕）4152 / 老隔板 5760 ⇒ 仍然更省 ✓（`WaterPrisonColumnTest` 6 条钉住：四段模型必须在 `WaterSolidModels.MODELS` 里否则隐形 ✗、贴图指向、三段居中、顶板比柱身宽、浮雕元素 ≥8、贴图真是蓝的且不透明、柱廊不挤、面数预算）。⑧**预览**：`WaterPrisonColumnPreviewTest` 出等轴测图（柱头特写 / 柱础特写 / 四根柱子的比例与 LOD ✓）⇒ `docs/previews/water-prison-columns-20261110.png`。全量 `test` **全绿**（21:29）。⑨**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,706,430 B、SHA `C0EB15A6B54BA48204C5C3DF9D647CE1909D1619745A238DC4E8EED2E94C772E`**（21:31:42 构建、21:32 装入，`identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：半透明 0.62 + 自发光 0.30 的观感、浮雕在 18 格处突然出现会不会突兀、金饰带偏棕要不要更"金"（贴图 (32,0) 那一格）都要试玩再调。详见[docs/spells/水法水牢罗马圣柱-20261110.md](docs/spells/水法水牢罗马圣柱-20261110.md)。
>

> **2026-11-10 21:23 风球纠正为「原有特效 + 叠加线条」已实装**：作者「你应该是在原有的特效上加上这个线条去优化」。**我做错的地方**：上一版把 `WindConstructModels.orb(...)`（核心 + 3 条 60° 大圆笼子 + 内收环 + 风刃）**整个删掉**只留线条 ✗ —— 那套色带是作者早就认可的画面，删了是"重做"不是"优化" ✗。**现在**：`WindChainRenderers.Ball` 里**先画原有**（飞行/吸住 → `WindConstructModels.orb(2, age, ...)`；炸开 → 原来那个 `Axis.XP 80°` 翻倒并渐隐的 `cage`），**再把线条叠上去**（`WindBallLines.swirl` 螺旋风线，半径略大于模型 ⇒ 像风裹在球外面转；炸开再叠冲击波环 + 径向风痕）；压缩 >55% 仍补一下亮芯 ✓。**坐标系上的坑**：原模型吃 `poseStack.scale(radius)`（模型是单位球），线条按世界尺度算 ⇒ 线条**不能**再吃那次缩放 ✗，所以是 `pushPose → scale → 画原有 → popPose → 画线条` ✓。**闸门**：`WindBallLinesTest` 加第 7 条 `theRendererLayersTheLinesOnTopOfTheOriginalEffects` —— 渲染器里必须同时出现 `WindConstructModels.orb(`、`draw("cage"`、`WindBallLines.swirl(`、`shockRings(`，且 `orb(` 必须**排在** `swirl(` 之前（顺序错就红）✓，专门防下次又"用新的替换旧的"。**回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **497 用例 / 120 套件全绿**（全量跑之前树上有队友在改 `TNWaterFieldRenderer`/`WaterPrisonColumnPreviewTest`，等他们 save 成可编译状态才跑 ✓）。**装机**：`mods/tnc-1.0.0.jar` = **200,706,333 B、SHA `5853B91647C65A2A57B7A16E1824F5033AE6D6379C046D3C3835685E91D3C97E`**（21:23:12 构建、21:23:43 装入；构建时 `java` 进程 = 4 ⇒ 后台轮询等到退出才替换；装前那版已备份 `…jar.bak-20261110-212343`；回读逐字节一致）。**下次启动游戏即生效**。详见[docs/spells/风球-线条化-20261110.md](docs/spells/风球-线条化-20261110.md) 第二轮。

> **2026-11-10 20:09 风球「色带模型 → 螺旋风线」已实装**：作者「风球也可以优化一下」。①**旧观感的问题**：`WindChainRenderers.Ball` 调 `WindConstructModels.orb(...)` —— 几片带贴图的"核心/笼子"色带贴在球面上 ✗，静态、糊、而且和已经线条化的龙卷风/风刃/雷球不是一个体系 ✗。②**新画法**：新增 `client/WindBallLines`（纯几何）+ 重写 `Ball` 渲染 —— 飞行/吸住时画**一束从南极绕到北极的风线**（`swirl`：每根 `phi=π(1-u)` 上升螺旋 + 每根自己的摆动相位）＋ 很淡的经纬轮廓（`shell`，3 纬 + 2 经）；压缩阶段球半径按 `WindChainRules.ballSize` 急速变小、线跟着挤 ⇒ 越吸越紧，压缩 >55% 补一下 `billboardFlash`"亮芯"；炸开那 12 tick 换成**冲击波环**（`shockRings`：0.35→2.2 倍半径、三道错峰）＋ **径向风痕**（`streaks`：10 条往外甩）。③**档位**：线数 `4 + tier`（t1=5 …… t5=9）、绕圈 1.37→2.25、转速 飞行 22°/tick、**压缩 34°/tick**、炸开 8°/tick ✓；颜色沿用风系（白芯 / `#9EFFDB` / `#1AB885`）⇒ 和龙卷风、风刃一致 ✓。④**线宽按半径比例**（`radius × 0.018`，下限 0.012 格）—— t1 球 2 格、t5 球 **67.5 格**，固定线宽会一边太粗一边看不见 ✗。⑤**预算**：t1 飞行 3600 顶点、t5 飞行 5136、t5 炸开 1296（炸开反而更省，因为不画整球），闸门上限 8000 ✓。⑥**闸门**：新增 `WindBallLinesTest` **6 条**（风线到球心距离 ±18% 内、起点南极终点北极；档越高线越多绕圈越密；自转换位置；轮廓闭合且在球面；冲击波往外扩 1.5 倍以上且 ≤2.4 倍、风痕朝外；线宽成比例 + 预算 ≤8000）。⑦**回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **496 用例 / 120 套件全绿**。⑧**装机（游戏开着）**：20:08:47 构建时 `java` 进程数 = 1 ⇒ 后台轮询，20:09:02 检测到 0 个进程后装入；`mods/tnc-1.0.0.jar` = **200,703,513 B、SHA `202474500618CB4188B39F140FAA0FC85CD1C3E07332EE706C595898BD312E78`**，装前那版（队友同尺寸构建 `EC489D48…`）已备份 `…jar.bak-20261110-200902`，回读逐字节一致 ✓。**下次启动游戏即生效**。**未实机验收 / 待定**：冲击波现在是水平三圈、要不要改成球面扩散；压缩阶段要不要加"往中心吸的短线"；`WindConstructModels.orb(...)` 现在没人调了要不要删。详见[docs/spells/风球-线条化-20261110.md](docs/spells/风球-线条化-20261110.md)。

> **2026-11-10 20:04 水牢隔板 → 罗马圣柱柱廊（蓝色贴图 + 虚化）已实装**：作者「水牢的隔板再改改吧，我想做成一个个罗马圣柱那样，你可以先做个圣柱的建模，然后弄成蓝色贴图再虚化」。①**建模**：新生成器 `tools/gen_water_column_models.py`（模型 + 贴图一起出，别手改 JSON ✗）⇒ 三个方块模型 `models/water/roman_column_base|shaft|capital.json`（柱础：方座→圆盘线脚→凹弧→上圆盘→收颈，5px；柱身：8×8 + 上下金线，16px；柱头：钟形→金饰带→**方形顶板**→四角涡卷，4.2px）。**为什么拆三段**：水牢高度是变的（t1 约 7 格 → t5 约 24 格 ✗），一根整柱只能整体拉 y ⇒ 柱础柱头会被拉成一坨 ✗；拆开后柱础/柱头按直径等比、柱身只拉高度 ✓。试过用两块 45° 板把柱身凑八边形 ✗ —— 轴对齐方块＋45° 方块的并集是**八角星**不是八边形（角凸出去，预览图一眼可见 ✗），最后定成**方形柱身＋贴图竖凹槽** ✓。②**贴图**：`textures/block/roman_column_blue.png` 64×64、五个 16×16 小格（柱身竖凹槽 / 浅石面+高光 / 金色饰带 / 深石底 / 横向凹线圆盘），模型每个面各取一格 ✓。③**虚化**：`WaterSolidModels.romanColumns(...)` 走**半透明**通道（opacity = fade×0.62 ✓）＋ 叠一层很淡的**加色自发光**（0.30 ✓，只半透明会像塑料 ✗）；三段分开摆：柱础→柱身（拉高）→柱头。④**柱距与粗细**：新 `WaterGeometry.columnCount`（柱距 3.4 格 ⇒ t1 9 / t3 22 / t5 28 根，上限 28）与 `columnDiameter`（取柱距六成 / **高度÷6.5** / 2.6 格三者最小 ⇒ t1 1.08、t3 1.94、t5 2.60 格，比例 6.5~9:1 ✓ 不会做成细针或水塔）；柱缝之间的亮竖线也改成落在两根柱子**正中间**（柱子自己有自发光 ✓）。⑤**测试**：`WaterPrisonColumnTest` **5 条**（三段模型必须在 `WaterSolidModels.MODELS` 里——不在就隐形 ✗、贴图指向对、三段接着且绕轴线居中、柱础/柱头顶板比柱身宽、贴图真的是蓝的且不透明、柱廊比老隔板少且不挤、**整圈面数 ≤3600**（实测 28×18 块=3024 面，老隔板约 5760 ✓ 更省））＋ `WaterPrisonColumnPreviewTest`（等轴测预览图 `build/water_prison_columns.png`：三档高度 + 贴图原图）；全量 `test` **全绿**（19:57）。⑥**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,699,330 B、SHA `EC489D4897ADC88419382EBFC4CFEAFCAAD26B5D445AA7392B69F054AD61CD5F`**（20:02:26 构建、20:03 装入，`identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：半透明 0.62 + 自发光 0.30 会不会太淡/太亮、柱子够不够"罗马"、t5 冥穴 28 根 + 天降巨剑会不会太满，都要试玩再调。预览图 `docs/previews/water-prison-columns-20261110.png`。详见[docs/spells/水法水牢罗马圣柱-20261110.md](docs/spells/水法水牢罗马圣柱-20261110.md)。
>

> **2026-11-10 19:57 风刃「不转 + 加大」＋ 雷球「球面游走闪电 + 自己电周围」已实装**：作者「发射出的风刃不要一直旋转，就顺着龙卷风的风向直射出去就行，然后体积加大／然后雷球的建模和特效可以优化一下，雷球上面有游走的闪电，去掉大部分混乱的粒子特效，雷球的建模也可以再精致一点，自发光，t级越高游走闪电越多，并且会自己持续电周围的敌人」。**①风刃**：`TNTornadoEntity.fireBlades` 的 `setSpinRate(18~28°)` → **0**（固定朝向顺风直射 ✓，风守卫 Boss 那两发 26°/tick 没动）；`TNWindBladeEntity.SPELL_BLADE_SIZE` 2.4 → **3.8**（跨度 2.8→**4.5 格**、判定 1.7→**2.1 格**）。**②雷球为什么不能只改 JSON**：三颗雷球是 SpellEngine 自己的 `SpellProjectile`——模型静态（闪电没法"游走"✗）、带电花全靠 `travel_particles`（就是作者说的乱粒子 ✗）、而"飞行途中持续电周围"JSON 根本没这个口子 ✗ ⇒ 分两头做。**②JSON（5 个雷球法术，用 `work/trim_orb_jsons.py`）**：`model.light_emission` 全部 `RADIATE`（自发光 ✓）、`rotate_degrees_per_tick = 0`、**删掉全部 `travel_particles`**（t1 8 / t2 6 / t3 21 / t4 36 颗）、`impact` 粒子封顶 8 颗且每组最多 2 批（最多原 87 颗）。**③球面那层电（客户端）**：新增 `client/ThunderOrbAura`（纯几何：4 纬 + 3 经的球面网格 + "切平面随机拐 ≤31°、再投回球面"的游走折线）+ `ThunderOrbAuraRenderer`（在世界空间按球心画：经纬线 0.30 alpha、游走闪电 0.92 alpha、`billboardFlash` 亮晕；每 2 tick 换种子 ⇒ 一直在爬 ✓；半径按 JSON `model.scale` 算 ⇒ t1 球 0.56 格、t3 球 3.5 格，线贴球面 ✓）。**④"自己电周围"（服务端）**：新增 `magic/OrbStormService`（挂引擎 `SpellEvents.PROJECTILE_SHOOT`，只认 `tnc:*` + `group=="orb"`，每 tick 维护，到点以球心为圆心电一圈）—— 半径 3.2/3.6/3.9/4.3/4.6 格、伤害 3.0/3.9/4.8/5.7/6.6、间隔 10/9/8/7/6 tick（`OrbStormRules` 纯数值）；打之前 `invulnerableTime = 0` ⇒ 高频小电击不会吞玩家的刀 ✓（和领域那次同一个坑）；判敌不误伤队友、每次最多 6 个目标、命中拉 `arcLine` 电弧。**⑤闸门**：新增 `OrbStormRulesTest` 4 条（档位单调、数值区间、tier 夹 1..5、只认 orb 组）与 `ThunderOrbAuraTest` 4 条（经纬线每点都在球面且环闭合、游走闪电贴球面且真的在动/换种子换路径、预算随条数增长 ≤8000、`polyline` 顶点格式填满防 2026-11-10 崩溃复发）；`WindBladeQiTest` 加"龙卷风必须 setSpinRate(0.0F)"+"跨度 3~6 格"。**回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **489 用例 / 119 套件全绿**。**装机**：`mods/tnc-1.0.0.jar` = **200,699,341 B、SHA `583ACA43EED97D5698E991D69606804A89E40F42F805547C10D4F45277B6A0AE`**（19:57:36 构建、19:57:49 装入；装时 0 个 java 进程；装前那版已备份 `…jar.bak-20261110-195748`；回读逐字节一致）；包内 `javap` 确认 `OrbStormRules`/`ThunderOrbAura` 在、`SPELL_BLADE_SIZE=3.8`、`thunder_orb.json` 无 `travel_particles` 且有 `RADIATE` ✓。**需要完整重启游戏生效**。**未实机验收 / 待定**：经纬线要不要减到 3+2（现在像地球仪）、游走闪电要不要在命中瞬间炸一圈、`cataclysm_thunder_orb`/`god_descent` 没有 `client_data.model`（这次只清了粒子）要不要也做球。详见[docs/spells/风刃与雷球-20261110.md](docs/spells/风刃与雷球-20261110.md)。

> **2026-11-10 19:23 齐射之后法阵残留半秒（LINGER 阶段）已实装**：作者「射完激光之后法阵还要残留半秒」。①**新增 `LINGER` 阶段**（`TNWaterGateEntity.LINGER = 5` —— 编号**故意不用 3**✗：3/4 早被 CONVERGE/GIANT 占了，客户端按整数分支 ✗）：`FIRE`（8 tick 闪光齐射）→ **`LINGER`（`WaterGateRules.LINGER_TICKS = 10` tick = 0.5 秒）** → t4 回 IDLE / t5 进 CONVERGE。这半秒里**不结算任何东西** ✗，只是法阵在原地淡出 ✓ —— 不留这一段的话六~八座阵会在齐射结束那一 tick "啪"地同时消失 ✗。②**淡出曲线**：`WaterGateRules.linger(t)` = `(1-t)²`（**先慢后快**✓）；直线淡出看起来像"被谁按着灯关掉"✗，二次曲线才是"阵自己散掉"✓ —— 残留过半时还剩 0.25 亮度（半秒里阵还看得见 ✓），半径再涨 6%（余波没散 ✓）。③**渲染**：`TNWaterGateRenderer` 把 LINGER 并进 OPEN/FIRE 那一支，但**不能再拿 phaseTick 重算出现进度** ✗（那样残留时八座阵会同时缩回去 ✗）⇒ 残留阶段 `open` 恒为 1.0，只乘 `linger()` 淡出 ✓。④**时间线记账**：`cycleTicks(tier)` 现在 = 开阵 + 齐射 + **残留**（+ t5 汇聚与大波）⇒ 内置冷却自动盖住这半秒，不会"上一轮的阵还没散就又要开"✗。⑤**测试**：`WaterGateRulesTest` 新增 `sealsLingerHalfASecondAfterTheVolley`（半秒 = 10 tick、首 tick 全亮、末端散尽、**单调变淡**、过半还看得见）＋把 `volleyCooldownCoversTheWholeAnimation` 改成逐项核对时间线记账 ✓；全量 `test` **全绿**（19:19）。⑥**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,683,316 B、SHA `908AA8A36E2E08BB693664E4FD530A0355F01CFCBFFCE740E1C7FFBD9D0296F4`**（19:20:59 构建、19:22 装入，`identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：0.5 秒的残留够不够"余韵"、二次曲线会不会太"拖"（`LINGER_TICKS` / `linger()` 两个地方）都要试玩再定。详见[docs/spells/水法水球链t4t5法阵齐射-20261109.md](docs/spells/水法水球链t4t5法阵齐射-20261109.md)。
>

> **2026-11-10 16:01 水球链 t3「龙王吼」改回"水炮弹弹幕"版（删掉水渊兽过场）已实装**：作者「然后把 t3 改回不是蓝金水渊兽的版本」。①**删掉的**：`magic/water/DragonRoarCombo.java`（咬住→升空→砸地的状态机）、`magic/water/DragonRoarRules.java`（BITE/IMPACT/END/height 时间线）、`client/DragonRoarVisual.java`（水渊兽虚影 + 巨剑虚影渲染），以及 `TNWaterSpellEntity.tick` 里那句 `if(tier()==3 && !BOSS_CAST) roarCombo.tick(...)` 和那根 `roarCombo` 字段 ✗ —— 玩家和 Boss 现在走**同一段** t3 逻辑 ✓（Boss 那版本来就是这套，一点没变 ✓）。②**现在是什么**：回到作者最初描述的「在身后展开竖直水色法阵，持续 10 秒连续射出 150 枚单体水炮弹」—— 施法时展开一张阵（`richCircle`，t3 半径 4 格），之后每 4 tick 从阵面螺旋喷 3 发水炮弹（`TNWaterBoltEntity`，带弹道、追准星锁到的目标），10 秒 50 轮 ×3 = 150 发，每轮补阵面水花 + 阵口喷发；t3 的 `length()` 恒为 0 ⇒ 光柱那一段根本不会画 ✓，屏幕上是"阵 + 弹幕"。文案 zh/en 都改回来了 ✓。③**没跟着删的**（不只服务 t3）：`WaterRestraints`（t5 水缚/水牢/冥穴锁人 ✓）、`SeaGodSwordVisuals`（t5 冥穴天降巨剑 ✓）、`LeviathanGhostVisual`（**读条前戏**里从门里飞出的水渊兽残影，作者 2026-11-06 要的 ✓）—— 也就是说"t3 不再出现水渊兽"✗，但前戏那只小的和 t5 的巨剑都还在 ✓。④**测试**：编译 ✓、全量 `test` **全绿**（16:01，含 `LeviathanSpellRulesTest` 的 Boss t3 名单 ✓）。⑤**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,683,052 B、SHA `3AA11502238C79EA033C72F9CC92FE8F45B17B254C02A2319474C367AB6EA9CB`**（15:59:47 构建、16:00 装入，`identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：阵的大小/亮度、150 发会不会太密（`WaterSpellRules.duration(3)` = 200 tick 与"每 4 tick 三发"都在 `TNWaterSpellEntity.tick` 的 t3 分支里）都要试玩再调。详见[docs/spells/水法t3龙王吼改回水炮弹-20261110.md](docs/spells/水法t3龙王吼改回水炮弹-20261110.md)。
>

> **2026-11-10 15:47 法阵重做（七层潮汐阵 · 会发光）＋ 法阵间距拉大 ＋ t5 汇聚大阵 = 小阵 5 倍 已实装**：作者「法阵太简陋了吧，你根本没设计吧／然后几座法阵之间的间隔要大一点啊，这个法阵也要发光，因为你的水波是发光的／然后 t5 汇聚成的大小要是小的的 5 倍大」。①**法阵重设计**：`client/TNWaterGateRenderer.seal` 从"三环 + 辐条 + 一个点"（作者原话：简陋 ✗）改成**七层双通道潮汐阵** —— 由外到内：三层渐亮的淡盘洗底（一团"水光"而不是几根线）→ 外环带（宽柔光晕 + 半透明水体环 + 珠白亮线，**发光就是这三层叠出来的** ✓）→ 内带（0.88R 柔光 + 水线 + 亮边、0.52R 细环）→ 12 个自己转的符文短刻（大阵 20 个）→ **六芒星**（两个三角形反向转 + 顶点星点）→ 六片/四片**水瓣**（小阵，0.52R→0.86R 的瓣形弧，内外两层）或三条**水涡螺旋臂**（大阵）→ 阵心（双环 + 白核 + 四道短光）→ 外缘**三道开口浪弧**（不闭环，闭环会读成"香炉"✗）。全部走 `body`（alpha 混合的水体）+ `glow`（`WaterRenderTypes.glow` 加色）双通道 ⇒ 和水波同一种"发光"语言 ✓。②**LOD 闸门**：新增 `seal(..., boolean full)` —— 齐射那一瞬间同屏六~八座阵 ✗，每座画满 1860 顶点就是 1.5 万 ✗ ⇒ 齐射/汇聚用精简版 **1060 顶点/座**（8 座 8480 ≤ 9000 ✓），只有"平时环绕的两个"和"t5 汇聚出来的大阵"画满（1860 / 2732 ✓）；`WaterGateSealPreviewTest` 里新加 `sealCostStaysWithinBudget` 把这三个数钉住 ✓。③**间距**：`WaterGateRules.ringRadius` 1.95/2.75 → **3.2 / 4.4**（相邻阵心差 3.2 / 3.4 格，阵面直径 1.24 / 1.9 格 ⇒ 中间空出一格多 ✓），新增测试 `circlesAreSpreadOutEnoughToRead`（弦长 > 阵面直径 ×1.6 ✓）。④**t5 汇聚 = 5 倍**：新增 `WaterGateRules.giantSealRadius(tier) = sealRadius(tier) × 5`（0.95 → **4.75 格**，正好 5 倍 ✓），巨大水波的粗细也改成等于大阵半径（4.75 格，原来 6.16 会"溢出阵口"✗）；新增测试 `mergedSealIsExactlyFiveTimesTheSmallOne` 钉住 5 倍关系与"水波填满阵口"✓。⑤**测试**：`WaterGateRulesTest` **12 条**、`WaterGateSealPreviewTest` 2 条（出 `build/water_gate_seals.png` 对照图 + 顶点预算闸门）、全量 `test` **全绿**（15:44，含队友当天用例）。⑥**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,688,301 B、SHA `51DA10039F6F0B53D241C4C9FEB9A84FD56EE2EC49C0A3BB679F37733F4B0ECA`**（15:46:16 构建、15:47 装入，`identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：法阵的发光强度（`glow` 那几层的 alpha）、3.2/4.4 的环够不够开、5 倍的大阵会不会太大（`giantSealRadius` 一个数）都要试玩再定；对照图 `docs/previews/water-gate-seals-20261110.png`（软件光栅，暗背景，实际在游戏里叠加到场景上会更亮 ✓）。详见[docs/spells/水法水球链t4t5法阵齐射-20261109.md](docs/spells/水法水球链t4t5法阵齐射-20261109.md)。
>

> **2026-11-10 03:08 修掉「风刃渲染崩溃」（顶点格式只填了一半）已实装**：作者「报错了」。崩溃报告 `crash-2026-11-10_03.00.56-client.txt`：`Description: Rendering entity in world`，`java.lang.IllegalStateException: Not filled all elements of the vertex`，栈顶 `WindBladeQi.vertex` → `band` → `draw` → `TNWindBladeRenderer.render`。**根因**：`LightningRenderTypes.BOLT` 的顶点格式是 **position + color + uv + overlay + uv2 + normal** 六项，而我写 `WindBladeQi.vertex` 时只发了 position + color 就 `endVertex()` ⇒ `BufferBuilder` 发现少元素当场抛 ✗（`LightningBoltGeometry.vertex` 之所以没事，是它把 `uv(0.5,0.5)` / `overlayCoords(NO_OVERLAY)` / `uv2(FULL_BRIGHT)` / `normal(0,1,0)` 都填了，我抄几何时漏了这四句 ✗）。**修法**：`WindBladeQi.vertex` 补齐成和雷电完全一致 ✓。**为什么测试没抓到**：原来的假 `VertexConsumer` 只记 position/alpha、根本不管格式全不全 ⇒ 测试全绿、一进游戏就崩 ✗；现在换成**较真的假消费者**（每个顶点少一个元素就在 `endVertex()` 抛，和真 `BufferBuilder` 一样），并新增第 6 条测试 `everyVertexFillsTheWholeFormat` 专门守它 ✓。**顺带排查**：全库扫了一遍"有 `endVertex()` 但没有 `.uv(`" 的文件，其余 20 个都走各自的 `POSITION_COLOR` 系 render type（`WarriorTrailRenderType` / 自定义 GUI），不受影响 ✓，只有我这一个用错了格式。**回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **471 用例 / 114 套件全绿**。**装机**：构建时 `java` 进程数 = 1（作者在游戏里）⇒ 没硬覆盖，后台轮询等退出；03:08:43 检测到 0 个进程后装入，`mods/tnc-1.0.0.jar` = **200,685,834 B、SHA `2EDAB64F24FFB1BE9CCD79D261ACE0EB12D5D790736FB0DAA7B359D79C6FCCD5`**，装前那版（队友同尺寸构建 `38D8F41F…`）已备份 `…jar.bak-20261110-030843`，回读逐字节一致；包内 `javap` 确认 `WindBladeQi` 引用了 `OverlayTexture` ✓。**下次启动游戏即生效**（替换发生在游戏退出之后）。**教训**：用别人的 render type 就得把它的顶点格式填满 —— 只发 position+color 在 `RenderType.lines()` 那种格式里没事，在 `POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL` 里是必崩。详见[docs/spells/风刃改剑气-20261109.md](docs/spells/风刃改剑气-20261109.md) 崩溃修复一节。

> **2026-11-09 22:24 水球链 t4/t5 重做成「金闪闪法阵群」＋ 水波改成加粗闪电 已实装**：作者「直接射一个太单调了，你看过圣杯战争吗，就是金闪闪那样……t5 就是法阵和水波变大，然后攻击结束之后几个法阵汇聚到一起射出一个巨大的水波。这个水波可以就不用原来那样持久，就跟光线一样就行了」。①**先改的是水波本体**（作者上一句：「一根直直的闪电像不像水波，把它加粗加大到跟水波一样，替换一下看看效果」）：`client/WaterBeamVisuals` 整根重写 —— 老的是真 3D 水管（外壁 tube ＋ 中轴细管 ＋ 缠绕水流线 ＋ 每 8/16 格一圈水环 ＋ 末端圆盘），现在换成雷系那套加色光带（`LightningBoltGeometry.polyline`，外辉光/主色/白芯三层，配色改成水蓝）；**半径 >4 格时改成一束 10 根细闪电**（30 格宽的一根光带就是一堵墙 ✗ —— 第一版软件光栅图里能直接看到平板边 ✗），抖动量压到半径的 0.10~0.45 倍（作者要"直直的"✗，不用雷系那种按段长抖 ✗）；给 `LightningBoltGeometry.boltTinted` 加了个 `jitterScale` 重载（老签名原样转发 ⇒ 落雷/雷龙/暗线/旅行形态一个字节没变 ✓）。**顶点预算 1800 不变**：t4 近 320/远 120，t5 近 1712/远 576（`WaterVisualsCostTest` 钉着 ✓）。新增 `WaterBeamLightningPreviewTest`：把**真渲染代码**的顶点录下来用软件光栅 + 加色混合 + tonemap 画"旧 vs 新"对照图（同相机、同 20 格尺子）⇒ `docs/previews/water-beam-t4/t5-lightning-20261109.png`。②**t4/t5 机制重做**：新增纯规则 `magic/water/WaterGateRules`（时间线/法阵几何/粗细伤害，服务端判定与客户端表现**读同一份常量** ✗）＋ `TNWaterGateEntity`（跟着主人走、推阶段、到点生成水波、记住这一轮打谁）＋ `TNWaterLanceEntity`（一道水波：一闪而过、**只结算一次**伤害、不钻地形 ✗）＋ `WaterGateEvents`（扳机走 `LivingHurtEvent`：**真的打出伤害**才算 ✓，空挥/打队友/自己水波打的不算 ✗ 否则一轮滚成无限轮）；`client/TNWaterGateRenderer`（三环＋辐条＋阵心点的水色阵，法阵**朝着目标**✗ 不是朝玩家前方）＋ `client/TNWaterLanceRenderer`（复用 `WaterBeamVisuals`，淡入淡出）。时间线：施法 ⇒ **IDLE 20 秒**（两个小阵绕玩家转）⇒ 玩家打中敌人（每次攻击都触发、内置 2 秒冷却）⇒ **OPEN**（t4 六座 / t5 八座，每 2 tick 亮一座、亮起 4 tick，**全部亮完再憋 6 tick** ⇒ 作者特意强调"先出阵、全部出现完再一起射"✓）⇒ **FIRE**（同一 tick 全部射出，各自从自己的阵飞向目标）⇒ t4 回 IDLE；**t5 再 CONVERGE（八座阵飞到身前 2.2 格叠起来）→ GIANT（撑开一座大阵射出一道巨大水波：×2 伤害、×2.8 粗）** ⇒ 回 IDLE。**t4/t5 不再当场放持续光柱** ✗（t1~t3 与利维坦 Boss 的 `castBoss` 一个字没动 ✓；老的钻掘代码仍在类里，只是玩家这条路不走了）。数值：单道水波 4/7 伤害、粗 0.9/2.2 格、活 10/12 tick，巨大水波 ×2 伤害、粗 6.2 格。③**测试**：新增 `WaterGateRulesTest` **10 条**（各档法阵个数/半径/粗细递增、**齐射一定晚于最后一个法阵亮满**、第一个阵从 0 tick 就开始长、冷却盖得住整个动作、法阵落在同一圆环/胸口高度、两个环绕阵分得开、汇聚点只看水平朝向（抬头不会把阵甩上天 ✗）、巨大水波 ≈2 倍且单道低于老光柱单次结算、水波寿命明显短于老光柱、零向量朝向不炸）；`WaterMagicGameTests` 两条 GameTest 跟着改成新设计（施法只留一组阵且没有残留光柱 ✓；触发后先只有阵、一道水波都没有，推够 tick 后同一刻出现 6 道 ✓）；全量 `test` **922 用例全绿**（22:20）。④**装机**：实例＋仓库镜像 `mods/tnc-1.0.0.jar` = **200,685,660 B、SHA `38D8F41F71ACE029A97185A1FA53B00D05B35989199DC4356F5AE2F3191F6F62`**（22:22:39 构建、22:24 装入，`install-to-pack.ps1` 报 `identical: True` + `mod jar verification passed`，装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：法阵美不美（现在是三环+辐条+阵心）、齐射够不够"齐"、单道 4/7 的伤害手感、20 秒是不是太长（`GATE_TICKS` 一个数）、水波粗细（`lanceRadius`）都要试玩再调。详见[docs/spells/水法水球链t4t5法阵齐射-20261109.md](docs/spells/水法水球链t4t5法阵齐射-20261109.md)、[docs/spells/水法水波改闪电-20261109.md](docs/spells/水法水波改闪电-20261109.md)（对照图 `docs/previews/water-beam-t4/t5-lightning-20261109.png`）。
>

> **2026-11-09 22:00 法杖 / 魔法石：换杖丢法术 + 遗忘不生效**：作者「学习了的魔法是绑定到魔法杖上而不是魔法石上，拿别的法杖就没有学习着的魔法，遗忘好像也一样」，拍板「取消限制，但是对同样元素的法术有伤害和能力加强」。查 `/saves/新的世界 (3)` 的 playerdata 实证：魔法石 `Learned` = 10 个**风系**法术，手上是 `tnc:dark_wand_3` 且 **NBT 全空**（连 spell_container 都没有）。三个原因：① `ElementWands.supported` 与 `ManaGate.hasWandFor` 都要求元素一致 ⇒ 暗杖永远不显示风法（设计，但没人读提示）；② `ensureWand` 只在登录/学/忘/铸杖/命令时跑、只写"当时在背包里"的杖 ⇒ 箱子掏出来的、别人给的杖从来没 container，遗忘后也不掉；③ 去掉元素后一阶能凑 28 条 > 引擎 8 个槽位。改法：`supported()` 去元素、按「本元素优先→高阶优先→目录顺序」排序并截断 8；`hasWandFor` 去元素只留阶位，提示改成"需要手持能承载 N 阶法术的法杖"；新增 `WandSync`（服务端每 1 秒扫背包对齐魔法石，**不补发**、**读条中跳过**）；新增 `WandResonance`（同元素共鸣：施放放行时开 3 秒窗口，窗口内 `spell_power` 命名空间的伤害 ×1.25，近战/弓不受影响）。`SpellEngineBridge` 抽出 `syncExistingWands` 并新增 `refreshWands`。测试改成新规则（`TownRevisionGameTests` 两条），`gradlew test` 通过。装机 200,669,001 字节，`mod jar verification passed`（SHA 5B274E29），jar 里能查到 `WandResonance.class`/`WandSync.class`。文档 `docs/spells/法杖与魔法石-20261109.md`。**教训**：这个 mod 里"数据在哪"和"什么时候同步"是两件事，`PlayerTickEvent` 在这个环境整局不进来（见 MagicStone 注释），常驻钩子必须挂 `ServerTickEvent`。

> **2026-11-09 21:33 风刃改成「战士剑气」＋体积统一已实装**：作者「这个风刃也改一改，其实可以改成战士那里的剑气你觉得如何，体积统一一下」。**判断：该改** —— ①战士剑气是"四层渐隐月牙光带"（`WarriorWaveRenderer`），风刃却是一张烘焙模型贴半透明图，两个都是"斩出去的一片"却完全不是一个画风 ✗；②旧尺寸表 t3/t4/t5 = **5 / 10 / 20** 倍（模型跨度 1.179 格）⇒ t5 的刃 **23.6 格**宽，比龙卷风本体还宽 ✗。**改法**：新增 `client/WindBladeQi`（程序化弯月，和 `WarriorWaveRenderer` 同一种读法）—— 四层（外辉光/主色/白芯/极细高光）+ **两道残影**（沿飞行方向后拖，alpha 0.26/0.12）+ **刃口**（外缘沿 Z 的薄带 ⇒ 侧看不会变一张纸 ✗）；颜色沿用龙卷风那套（白芯 / 青绿 `#9EFFDB` / 深青 `#1AB885`）⇒ 风、雷、战士同一美术体系 ✓；出生 2 tick 淡入、最后 6 tick 淡出。**体积统一**：`TNWindBladeEntity.SPELL_BLADE_SIZE = 2.4` ⇒ 跨度 **2.83 格**（战士剑气半宽 1.2 ⇒ 2.4 格，同量级 ✓）、命中半径 `1.1 × √2.4` = **1.70 格**（仍封顶 5 格）；`TNTornadoEntity.bladeSizeMultiplier()` 改成返回它，旧数组 `BLADE_SIZE_MULTIPLIER` 标 `@Deprecated` 只为留档；**只统一风法** ✗（风守卫 Boss 的 2.6 / 4.2 倍照旧 ✓）。**一个坑**：新版几何按世界尺寸生成 ⇒ 渲染器**不能再 poseStack.scale** ✗（多乘一次又会"打中了但看着没打到"），尺寸只从 `halfSpanBlocks()` 出来一次 ✓。**闸门**：新增 `WindBladeQiTest` **5 条**（顶点有限且 == `vertices()`、≤3000、alpha∈[0,1]；形状：中间厚两端收尖、带弧度、左右对称；alpha=0/尺寸=0 不发顶点；体积统一：龙卷风必须用 `SPELL_BLADE_SIZE`、跨度 2~4 格、判定仍是 √尺寸+封顶；渲染器必须走 `WindBladeQi.draw` 且不许再出现 `CustomModels.render`）。⚠️ 测试里**不能**调 `hitRadiusFor(...)`（会触发 `SynchedEntityData` 静态初始化 ⇒ `ExceptionInInitializerError`），那一条改成源码扫描 ✓。**回归**：`client.*` + `magic.*` + `warrior.*` + `world.wind.*` **459 用例 / 112 套件全绿**。**装机**：我这版 = 200,660,937 B、SHA `2174F11A…4C2B47`（21:33:19 构建、21:33:34 装入，装前 0 个 java 进程，备份 `…jar.bak-20261109-213334`，回读一致）；**随后队友在同一棵树上 21:33:26 又构建并安装了一版**（200,664,075 B、SHA `E3AE63AA…A733A871`），把我的覆盖了 —— 已 `javap` 核对：实例里的包里 `WindBladeQi`（SEGMENTS=40 / BOW=0.34）、`TNWindBladeEntity.SPELL_BLADE_SIZE=2.4`、`TNTornadoEntity.MODEL_BASE_RADIUS=2.4` **全都在** ⇒ 我的改动已包含在内（且多了队友的改动）✓，无需再覆盖（再覆盖反而会把队友那部分回退 ✗）。**需要完整重启游戏生效**（客户端渲染器）。**未实机验收 / 待定**：弧度 `BOW=0.34` 要不要更弯、残影要不要三条、旧模型 `tnc:projectile/windblade` 现在只剩 Canglan 审计引用要不要清掉。详见[docs/spells/风刃改剑气-20261109.md](docs/spells/风刃改剑气-20261109.md)。

> **2026-11-09 18:05 风法龙卷风「根部再收窄」已实装**：作者「根部可以半径再小一点」。①**只收根、不收顶**：`TNTornadoEntity.MODEL_BASE_RADIUS` **4.0 → 2.4**（单位 = 1/16 格），接地半径 t3/t4/t5 从 0.50/1.25/2.00 格变成 **0.30/0.75/1.20 格**；`MODEL_TOP_RADIUS` 保持 13.5 ⇒ 锥度更陡，落地那一小截更像"从地上抽起来"的 ✓；两层按比例一起收（内层仍是外层 0.55 倍）⇒ 里外还套得住 ✓。②**粒子跟着收**（容易漏的那条）：`spawnSwirlParticles` 原来算半径是**直线插值** `baseR + (topR−baseR)·u`，和光带的 `u^0.72` 剖面不是同一条曲线 —— 根部收窄 40% 后直线插值会在接地处明显凸出去 ✗ ⇒ 现在改成同一句 `TornadoLineShape.profile(u, baseR, topR)`，线 / 粒子 / 风刃出生点根部同宽 ✓。③**闸门**：9 条不变（测试里的 `BOTTOM` 是从 `MODEL_BASE_RADIUS` 算的 ⇒ 自动跟着收；"漏斗下 1/3 比上 1/3 窄"余量更大，两层"内层每点半径 < 外层最大半径"仍成立）✓。④**回归**：`magic.*` + `client.*` + `world.wind.*` **245 用例 / 59 套件全绿**。⑤**装机（这次踩到"游戏正开着"）**：18:04:41 构建时 `java` 进程数 = **1**（作者在游戏里）⇒ 没有硬覆盖，改成后台任务轮询等游戏退出，18:05:00 检测到 0 个 java 进程后装入；`mods/tnc-1.0.0.jar` = **200,656,192 B、SHA `673F261693D21C39AE34079B7DA2EB716C5E5B26C2A0B6FD52C130D9AEDAB954`**，装前那版已备份 `…jar.bak-20261109-180500`，回读逐字节一致 ✓。**需要再完整重启一次才生效**（替换发生在游戏退出之后）。**未实机验收 / 待定**：根部还能不能更细（2.0 会和"卷人"手感脱节）、要不要给根部加一圈贴地小旋纹。详见[docs/spells/风法龙卷风-线条化-20261109.md](docs/spells/风法龙卷风-线条化-20261109.md) 第三轮。

> **2026-11-09 17:55 风法龙卷风改「两层 + 线加粗」已实装**：作者「做成两层，里一层外一层，然后线条变粗一些」。①**两层**：`TornadoLineShape` 新增 `Shell` 记录（`name/radiusScale/strands/turnsScale/phaseShift/widthScale/alphaScale/salt/whiteness`）+ `SHELLS` 列表 —— 外层 `OUTER`（半径 1.0、7 根、1.7 圈，**纯白芯**），内层 `INNER`（半径 **0.55**、5 根、0.92 倍圈数、整体错开 **72°**、线宽 ×0.80、**白度 0.30 偏青绿**）⇒ 加色混合下"外白内绿"，一眼能分出里外 ✓（两层都纯白会糊成一根发光管 ✗）。几何生成改成 `strands(..., Shell)`，半径按层的比例缩、角度带层的圈数与相位、每层独立种子盐 ⇒ 两层折点不同 ✓。②**加粗**：`CORE_WIDTH_RATIO` **0.018 → 0.028**（约 +55%）—— t3 白芯半径 0.11 格、t4 0.27、t5 **0.43** 格；加粗和两层是配套的（细线套两层会互相穿帮像乱麻 ✗）✓。③**预算**：一层版 2952 顶点 → 两层版 **5328**（12 根 × 17 段 + 6 道风切），闸门上限 4000 → **6500**；两发 t5 同屏约 1 万顶点，和雷暴/雷龙同量级 ✓。④**闸门**：`TornadoLineShapeTest` 8 → **9 条**，新增 `thereAreTwoNestedShells`（正好两层、内层半径 < 外层 0.8 倍、内层 ≥4 根、两层圈数不同、内层白度更低、**几何上内层每个点半径都 < 外层最大半径**）✓。⑤**离线预览**：预览脚本改成按 `SHELLS` 循环 + 按 whiteness 混芯色，出新图 `docs/spells/assets/风法龙卷风-两层加粗-20261109.png`（调参时确认了"内层纯白会糊成一根管"，才改成偏青绿）✓。⑥**回归**：`magic.*` + `client.*` + `world.wind.*` **245 用例 / 59 套件全绿**。⑦**装机**：`mods/tnc-1.0.0.jar` = **200,656,162 B、SHA `6FDDCF67A96423B29E10C7B8A9F48DCF007FE74F5AB3E225C9D659A78ED17015`**（17:55:21 构建、17:55:34 装入；装前 0 个 java 进程；装前那版已备份 `…jar.bak-20261109-175533`；回读逐字节一致）。**需要完整重启游戏生效**（客户端渲染器）。**未实机验收 / 待定**：内层要不要再暗一点（现在 0.95）、要不要加第三层"最里面的细芯"（3 根 / 半径 0.3）、苍岚军神那只仍是老模型。详见[docs/spells/风法龙卷风-线条化-20261109.md](docs/spells/风法龙卷风-线条化-20261109.md)。

> **2026-11-09 01:54 风法龙卷风改成「 lightning 那种线螺旋缠绕」（不再用薄板模型）已实装**：作者「你可以优化一下风法的龙卷风吗，我想做成就是，我们的闪电嘛，他不是一个一个线吗，我想这个线螺旋缠绕起来变成龙卷风，你看过鬼灭之刃吗，就是那种感觉，可以有明确的锐角，龙卷风极具那种线条特色」。①**不再画模型**：旧的 `TNTornadoRenderer` 走 SpellEngine `CustomModels` 画烘焙模型（7 层 × 4 片薄板），作者实机看到的是"几块板在转"✗ ⇒ 现在<b>一个模型都不画</b>，整团风由一束螺旋折线构成 ✓（模型 `tnc:projectile/wind_tornado` 保留给苍岚军神的 `CanglanTempestRenderer` ✓）。②**新增纯数学几何** `magic/TornadoLineShape`：`STRANDS=7`（多根才像"缠绕"）、`TURNS=1.70`、`SEGMENTS=17`（段数少 ⇒ 折角大）、`KINK=0.14`（半径外挑/内收交替）、`SPUR=0.26`（偶发"刃尖"，越靠顶概率越低）、顶口按 `u^0.72` 收口 + 顶部 18% 往内收 + 每根线到顶高度不同（0.84~1.0）⇒ 顶口不会变成"帽檐" ✗；刃尖挑上去的高度**累积衰减** ⇒ y 始终单调（不会出现倒流的一小段 ✗）。③**新增 `client/WindTornadoLines` + `LightningBoltGeometry.polyline`**：把折线画成三层加色光带（外辉光 3.4× / 主色 1.5× / 白芯 0.5×，**和闪电同一套**）⇒ 风与雷是一个美术体系 ✓；线宽按 `高度 × 0.018` 走（t3 白芯 0.07 格、t5 0.28 格），三档同一种画风 ✓。④**自转烘进顶点**：光带要朝向相机（侧向量 = 切线 × 视线），`poseStack.rotate` 会让侧向量算错 ✗ ⇒ 渲染器只 `scale` 不旋转，转角读实体的 `visualSpin(partialTick)`（服务端同步 + 帧间插值）✓；另外加了出生 4 tick 淡入 / 消散 10 tick 淡出 ✓。⑤**粒子必须同源**：`TNTornadoEntity.spawnSwirlParticles` 的扭转原来取模型 135°，线现在绕 612° ⇒ 改成读 `TornadoLineShape.totalTwist()`，否则粒子会**逆着线走**一眼穿帮 ✗。⑥**闸门**：新增 `TornadoLineShapeTest` **8 条**（7 根/18 点/y 单调/无 NaN；漏斗下 1/3 比上 1/3 窄 —— 不能用首末点比，首点可能是刃尖；**中位拐角 ≥25°、最大 ≥60°、≥10° 折点 ≥55%、≥30° ≥1/4**；半径必须锯齿；自转是纯旋转 `(x,z)→(z,−x)`；6 道风切 V 形 ≥15°；顶点预算 ≤4000（实测 2952）且 `draw()` 返回值 == 实际顶点数；alpha=0 不发顶点）。⑦**离线预览**：`work/preview_tornado.py` 用同一套几何 + 同样的三层光带画出 `docs/spells/assets/风法龙卷风-线条-20261109.png`，不进游戏就能改形状看效果 ✓（调参靠它迭代了 4 轮：先发现 SPUR 太大变成"横杠笼子"，再收敛 KINK、调 TURNS、加顶口收拢）。⑧**回归**：`magic.*` + `client.*` + `world.wind.*` **244 用例 / 59 套件全绿**。⑨**装机**：`mods/tnc-1.0.0.jar` = **200,653,879 B、SHA `94C6D6466DC706EC3D64C38C70B3EE6411BB32F7BCF390B7DD01224ECEC2DFB5`**（01:53:49 构建、01:54:14 装入；装前 0 个 java 进程；装前那版已备份 `…jar.bak-20261109-015414`；回读逐字节一致）；包内 `javap` 确认 `TornadoLineShape` / `WindTornadoLines` 在。**需要完整重启游戏生效**（客户端渲染器）。**未实机验收 / 待定**：7 根线 + 6 道风切要不要再加一层很淡的外轮廓；顶端甩出漏斗外的那几根长线保留还是收掉；苍岚军神那只龙卷风还是模型，要不要也换成这套线；颜色要不要更冷（接近雷的紫白）。详见[docs/spells/风法龙卷风-线条化-20261109.md](docs/spells/风法龙卷风-线条化-20261109.md)。

> **2026-11-08 04:44 修掉「学领域就报错」（客户端渲染 NPE）+ 领域悬停文案 + 名字改「领域魔法」已实装**：作者报「学领域就报错了」。崩溃报告 `crash-2026-11-08_04.35.39-client.txt`：`Description: Rendering screen`，栈顶 `MagicStoneScreen.effectSummary(MagicStoneScreen.java:495)` → `java.lang.NullPointerException: ... SpellCatalog$Entry.element() is null`。**根因**：领域法术是独立法术（`element == null`，这样只出现在「独立」页、不进七元素 NBT 数组），但 `effectSummary()` 在**没有手写介绍**时无条件 `entry.element().cn()`；前两轮雷/暗的介绍键也没写 ⇒ 鼠标一划就 NPE 崩客户端 ✗（不是"学"坏了，是悬停/点选时渲染崩的）。**修了四处空元素**：①`MagicStoneScreen.effectSummary` 改三支（有元素→「火系」/ 是领域→「焚天领域」/ 其它→「独立魔法」）；②`MagicStoneLearning.describe` 的 `AFFINITY_TOO_LOW`、`OUT_OF_ORDER` 两支新增 `elementName(entry)` 兜底，空元素不再碰 `maxTierFor(null)`；③`ChantRules.words` 默认句空元素 → 「领域」；④`SpellCatalog.Entry.fullName()` 对领域显示「**领域魔法 · 火**」而不是「独立魔法 · 无元素」（它是有元素的，只是不进七元素数组）。**顺带补文案**：`zh_cn` / `en_us` 各补 7 条 `spell.tnc.<domain>.description`，把「只能选一个 / 需该系亲和力 / 30 秒 / 90 秒 / 7 格光环 / 每隔几秒出手 / 战士附伤与刀光变色」写进悬停说明。**闸门**：`DomainSpellTest` 9→**11 条**，新增 `everyLineShownForADomainIsNullSafe`（七个 Result 全在领域上跑一遍 `describe`，任何一句 NPE 或字面 null 判红；并钉住 `effectSummary` 的空元素分支、`fullName()` 不许写「无元素」）与 `everyDomainHasTooltipText`（两种语言都要有 7 条领域介绍）。**回归**：`magic.*` + `warrior.*` + `light.*` **382 用例 / 95 套件全绿**（04:44）。**装机**：`mods/tnc-1.0.0.jar` = **200,647,178 B、SHA `C4AC55D95E251DAC3F0808E8F1A9DCA21A08213C71BF678CB02D455F69154E25`**（04:44:44 构建、04:44:55 装入；装前 0 个 java 进程；装前那版已备份 `…jar.bak-20261108-044454`；回读逐字节一致）；从实例包内确认 `zh_cn.json` 有 7 条领域介绍、`DomainService.aura` 在。**需要完整重启游戏生效**。**教训**：element 为 null 是独立法术的正常状态，任何"给玩家看一句话"的地方都必须先判空 —— 这条已写成测试，不再靠记忆。详见[docs/spells/领域法术-范围化-20261108.md](docs/spells/领域法术-范围化-20261108.md) §8。

> **2026-11-08 03:52 领域法术第三轮：范围化（常驻光环 + 一次三个落点 + 单次范围放大）已实装**：作者「领域我希望来点范围的那种效果」—— 前两轮的领域其实是"点名"（隔几秒挑一个目标、脚下铺一圈、砸一下，圈只有 2~3 格），这一轮三层一起上。①**常驻光环**：新 `DomainService.aura` —— 每 **1 秒**（`AURA_INTERVAL`=20 tick）在玩家身边 **7 格**（`AURA_RADIUS`）内撒一圈本系粒子（16 颗，纯粒子不生成实体），范围内**所有敌人**掉 **1 点**魔法伤害（`AURA_DAMAGE`），每两秒再染一次本系状态（雷=麻 0.6 s / 暗=黑暗 2 s / 火=持续着火 / 风=轻推 0.06 格 / 水=潮湿 1.2 s / 土=沉重 0.6 s / 光=发光标记 + **范围内所有玩家含自己每秒回 0.5 血**）；光环**一律不碰玩家**（`hittable` 排除 Player，PVP 不会莫名掉血），光领域是唯一例外（它是给玩家回血的）。②**一次多点**：`pickVictim` → `pickVictims`，从"最近被玩家/队友打过的 8 秒名单"里按最近挨打排序取，**新落点必须离已选 ≥2 格**（`TARGET_SPACING`）、最多 **3 个**（`MAX_TARGETS`），`PENDING` 从单条改成 `List<Pending>` ⇒ 玩家看到的是一片**同时亮起来**的地面，而不是一个点。③**单次范围放大**：雷 2.2→**3.2** / 暗 2.0→**3.0** / 火 3.0→**4.0** / 风 2.6→**3.6** / 水 3.0→**4.0** / 土 2.6→**3.6** / 光 3.0→**4.0** 格（全部 ≥3 格，且都小于光环半径，闸门钉住）。④**抓到一个真 bug**：原版受击有 20 tick 无敌帧，光环每秒 1 点的小伤害会给目标挂上无敌帧，导致玩家砍出的刀被**吞掉**（伤害归零还查不出原因）⇒ 领域所有伤害改走 `applyMagic()`：**先 `invulnerableTime = 0` 再 `hurt`**，等于领域伤害不占玩家的受击窗口；这条写进了闸门（源码里必须有 `invulnerableTime = 0`）。⑤**闸门**：`DomainSpellTest` 8→**9 条**，新增 `domainsHaveAnAreaNotJustSinglePointHits`（光环常量必须被调用、`MAX_TARGETS`/`TARGET_SPACING` 必须有、`invulnerableTime = 0` 必须有、光环半径 ≥6 / 伤害 >0 / 节拍 ≥0.5 秒、七系单次半径 ≥3 格且小于光环半径）。⑥**回归**：`DomainSpellTest` **9/9**，`magic.*` + `warrior.*` **324 用例 / 84 套件全绿**。⑦**装机**：`mods/tnc-1.0.0.jar` = **200,645,667 B、SHA `84EE8FE0E71263010997F1AF933C19CE25116CE8E5A7AB5A539C73C3BBCE58BD`**（03:52:00 构建、03:52:19 装入；装前 0 个 java 进程；装前那版已备份 `…jar.bak-20261108-035218`；回读逐字节一致）。**需要完整重启游戏生效**。**过程**：本轮队友在改 `travel/client/TravelFormRenderer.java`（`trailArc` 签名、double/float），整树编译失败两次，等他把文件存成能编译的状态后测试/构建才绿 —— 没动他的文件。**未实机验收 / 待定**：光环 1 点/秒 7 格会不会太强（五个怪围上来 = 5 点/秒 × 30 秒）、3 落点 × 3~4 格在怪多时粒子会不会刷屏、光环要不要也吃「亲和力 ×5%」、火/水仍用通用法阵要不要专属样式；最该盯的是"领域开着砍怪，刀的伤害不能被光环的 1 点吞掉"。详见[docs/spells/领域法术-范围化-20261108.md](docs/spells/领域法术-范围化-20261108.md)。

> **2026-11-08 03:29 领域法术第二轮：七系补齐（火/风/水/土/光）已实装**：作者「可以尝试做做别的法术领域」。第一轮只有雷、暗，这一轮把另外五系按同一套骨架补齐 —— 现在**七个元素各有一条领域**，全在魔法石「独立」页，规则/状态/闸门共用一份代码，只有"出手长什么样"不同。①**重构**：`DomainSpells` 变成**一张表** `Domain(element, path, name, kind, interval, telegraph, style, damage, radius)` + 查表函数（`of/byPath/elementOf/pathOf/nameOf/isDomain/active`），`DomainService` 的 `resolve` 按 `kind` 分派到七个方法（`bolt/grip/burn/cut/wave/quake/smite`），新增域的活从此只要加一行表 + 一个 JSON；`TNEffects` 补 5 个效果（`domain_fire/wind/water/earth/light`）+ `DOMAIN_EFFECTS: Element → RegistryObject` 查表，「状态＝效果」的模型没变（自动同步 ⇒ 客户端刀光变色不用发包、自带倒计时、HUD 有图标）。②**七系数值**：雷 6.0 秒/14 伤/2.2 格（落雷，没人可打时周围随机落**纯视觉**氛围雷）；暗 8.0 秒/黑雾+手/按住 3 秒；火 5.5 秒/12 伤/3.0 格 + **点燃 4 秒**；风 5.0 秒/9 伤/2.6 格 + **推出去 0.85 格**；水 6.5 秒/11 伤/3.0 格 + **泡住减速 2.5 秒**；土 7.0 秒/13 伤/2.6 格 + **定身 1.2 秒**；光 7.5 秒/10 伤/3.0 格 + **范围内所有玩家/队友回 4 点血**；共用 30 秒持续 / 90 秒冷却 / 16 格半径 / 8 秒目标记忆 / 战士 +30% 封顶 12。③**预警**：每个领域都有自己的预警时长（0.7~1.2 秒）和阵样式（雷 11 / 暗 13 / 土 4 / 风 5 / 光 9 有专属，**火、水暂用 0 号通用阵**靠落点粒子区分 —— 专属样式列为下一轮待办 ✓）。④**刀光**：`WarriorTrail` / `WarriorWaves` 早就按元素取色，这次一行没改就自动支持七系（雷紫/暗近黑/火橙/风青绿/水青蓝/土黄/光白金），闸门里钉了「七色不许撞」。⑤**「加 X 伤」零新代码**：`TNSpecialMagic` 的「亲和力 ×5% → `spell_power:<school>`」本来就覆盖七系（lightning/fire/air/water/earth/healing/soul），学会领域即打开开关 ✓。⑥**闸门**：`DomainSpellTest` 6→**8 条**（新增 `everyElementHasADomainAndNoElementIsLeftOut`：元素数=领域数、间隔 ≥4 秒、预警>0 且短于间隔、阵样式至少 5 种；`everyDomainHasItsOwnStatusEffect`：七个效果都注册 + 有查表；`darknessTurnsTheWarriorTrailBlack...` 扩成七系不撞色；`onlyOneDomainEffectCanBeOpenAtATime` 改成遍历七系）；**注意**：五个新 JSON **只在 jar 里**，数据包不许再放一份（重复会被 `SpellBalanceTest` 的「同档两个法术」判红，这条已写进闸门）。⑦**回归**：`magic.*` + `warrior.*` **323 用例 / 84 套件全绿**（含 `SpellBalanceTest` 7、`SpellCatalogStructureTest` 4）。⑧**装机**：`mods/tnc-1.0.0.jar` = **200,637,619 B、SHA `28164CA4C461514F4658113B00199198B401D9C2D4E4AA7EECCBC027D14D49E0`**（03:29:02 构建、03:29:40 装入；装前 0 个 java 进程；装前那版已备份 `…jar.bak-20261108-032940`；回读逐字节一致）；从**实例包内**核对到 7 个 `*_domain.json` + `magic/domain/*.class` 齐全。**需要完整重启游戏生效**（新增 5 个状态效果 + 5 个法术 id）。**未实机验收**：火/水的通用阵好不好认、五系 5~7.5 秒间隔够不够久、土定身/暗按住对 boss 要不要减半、光领域回血是不是太强，都要试玩再定。详见[docs/spells/领域法术-七系补齐-20261108.md](docs/spells/领域法术-七系补齐-20261108.md)（第一轮见[docs/spells/领域法术-雷霆与暗黑-20261108.md](docs/spells/领域法术-雷霆与暗黑-20261108.md)）。

> **2026-11-08 03:12 领域法术第一轮（雷霆领域 / 暗黑领域）+ 雷锲改成「直接闪出」 已实装**：作者「准备做领域法术，你先做雷，这个领域法术放在独立法术那里，并且告知玩家只能选择一个领域法术，条件是有亲和力就行」＋「雷锲的出现直接闪出就行，你不要雷劈了之后还从地里长出来」。①**位置与门槛**：两个领域 `tnc:lightning_domain` / `tnc:dark_domain` 进 `SpellCatalog` 的 `Chain.INDEPENDENT`、**元素写 null**（只出现在魔法石「独立」页，不进七元素 NBT 数组），真实归属由新类 `magic/domain/DomainSpells.elementOf(id)` 反查；`MagicStoneLearning.check` 里加两道闸 —— 已有别的领域 ⇒ 新 `Result.DOMAIN_CONFLICT`、该系亲和力 < 1 ⇒ `DOMAIN_NEEDS_AFFINITY`（**不看等级、不看链进度**）；`unlock` 成功即 `data.grantSpecial(该系)`（`TNSpecialMagic` 的「亲和力 ×5% → spell_power」立刻生效）；想换领域先在魔法石遗忘旧的（`revokeSpecial` 后 `check` 立刻放行，不会永久锁死）。**告知玩家**：魔法石悬停三行「领域魔法 · 全部元素只能选一个 / 要求 X 亲和力 ≥ 1（不看等级）/ 当前：已选「…」」，学不了时聊天栏给中文原因。②**状态用状态效果**（新 `TNEffects.DOMAIN_LIGHTNING` / `DOMAIN_DARK`）：原版自动同步 ⇒ 客户端 `hasEffect` 直接读到（刀光变色不用发包），自带 duration ⇒ 不用每 tick 维护时间戳，HUD 上有图标；开新领域会 `removeEffect` 旧领域兜底。③**雷霆**（`DomainService`，数值全在 `DomainSpells` 顶部）：展开 **30 秒**、冷却 **90 秒**、半径 **16 格**；每 **6 秒**从「玩家和队友正在打的目标」里挑最近挨打的那个（`HIT` 表按目标 uuid 记 gameTime、记忆 **8 秒**、所有玩家共用 ⇒ 队友打的也算、排除 `isAlliedTo` 友军与自己），先在它**脚下**铺一圈 `STYLE_LIGHTNING_MINOR` 预警（**1.1 秒**，作者「劈之前地面有明显落点」），到点落一记 `setVisualOnly` 真雷（视觉走原版、伤害我们自己结算，避免双算）⇒ 半径 2.2 格内 **14 点**魔法伤害；战士每一刀命中 +30%（封顶 +12）附雷伤 + 溅电火花 + 25% 补一根小雷。④**暗黑**：同样 30 秒 / 90 秒，每 **8 秒**出手；先铺 `STYLE_DARK_MINOR` 暗紫圈，1.1 秒后脚下炸开一小团黑雾（`SQUID_INK` + `SCULK_SOUL`），再从雾里伸出一只手 —— 复用 `TNNightGripEntity` 新增的 `launchFrom(start, caster, targetId, tier)`（起点在脚底下 0.9 格 ⇒ 读作「从地里伸出来」），落点 2 格内全部按住 **3 秒**（移速 6 档 + 黑暗 + 发光标记 + 清零动量）；战士附暗伤 + 刀光剑影转近黑（`WarriorTrail` / `WarriorWaves` 检测到领域效果就把元素改成 `dark` ⇒ 拖尾底色 `0x10101B`、剑波同色；雷则是紫电 `0xE2C9FF`）。⑤**雷锲**：删掉一切「长」的痕迹 —— 模型不再从 0.02 缩放到 1（整根一起变大＝从土里顶出来）、不再有 6 道从地面往楔轴上收的短雷（那就是「从地里长出来」的读法）、没劈到的楔子一根雷都不画；改成**落地即满尺寸直接出现**，余波是雷环**向外**炸开（越炸越大、离楔轴越远）。保留 30 格天雷砸下 + 落点白闪 + 竖雷骨架 + 一楔一道雷。⑥**测试**：新增 `DomainSpellTest` **6/6**（独立页/元素 null/不分档、亲和力是唯一门槛 + 只能选一个 + 遗忘可换、JSON 与数值表对齐且**禁止数据包重复定义**、暗拖尾近黑雷拖尾紫电、源码扫描预警圈/`visualOnly`/记忆窗/排除友军/黑雾/`launchFrom`/减速控制/附伤走 `indirectMagic`、开新领域必关旧领域）；`ThunderSealBirthTest` 的雷锲闸门同步改成「禁止任何长大痕迹」（禁 `float pop =`、禁按 `born` 淡入、禁 `800L + slot`，要求满尺寸直闪）**4/4**；magic 包全量 **119/119** 全绿。⑦**装机**：`mods/tnc-1.0.0.jar` = **200,628,606 B、SHA `4C2E023FDB890A98EBDB30B828725AB20F7381F8FADE05CA6536E235E13A4D0B`**（03:16:37 构建、03:16:47 装入；装前是 200,628,606 B `0D5542C8…`，已备份 `…jar.bak-20261108-031646`，回读逐字节一致）；jar 内已核 `data/tnc/spells/lightning_domain.json`、`dark_domain.json`、`magic/domain/*.class`。**数据包故意不加这两个 JSON**（jar 里已有；两个目录各一份会被读成两条同 group 同档法术，`SpellBalanceTest` 当场判红 —— 这一条也写进了闸门）。**需要完整重启游戏生效**（新增效果 + 服务端逻辑）。**未进客户端实看**：落雷 6 秒/暗抓 8 秒的手感、1.1 秒预警够不够躲、暗领域 3 秒控制硬不硬、刀光变色是否够黑，都要试玩再定。详见[docs/spells/领域法术-雷霆与暗黑-20261108.md](docs/spells/领域法术-雷霆与暗黑-20261108.md)。

> **2026-11-08 03:10 法术图标：补土系 20 张、拆掉 5 组共用图**：作者「有些图标都复用了，特别是土还一个图标都没有」「让玩家看到这个就知道具体啥意思」。体检 135 条法术（`build/icon_parse.py`）：土系 4 链 20 条**完全没有文件**、`god_descent` 缺、`light_beam` 与 `light_mirror_t1..t5` 同一张、雷主链与雷印链 5 组撞车、风球/风刃/狂风三组撞车。重画 46 张（土系 20 + 光镜 5 + 雷印 5 + 风球 5 + 风刃 5 + 狂风 5 + 神在投篮 1），其余 89 张原本一法一图、风格也没问题，不动。做法：`tools/spell_icons.py` 在 64×64 画矢量（曲线、尖角、渐细的带子），BOX 缩到 16×16，**把每个像素吸附到三色**（暗边/主体/高光）——不吸附就是一片糊边；三色是 `build/icon_palette.py` 从旧图统计出来的，所以新旧混排不串味。每张图 = 一个能认出的主体剪影 + 内核高光 + 右下暗边 + 碎屑；档位靠内容不靠角标（风刃多一把、狂风多一道、石环多两块）。复查：缺图 0、共用 0。文档 `docs/spells/法术图标-20261108.md`，预览 `docs/spells/法术图标-预览.png`。打包 200,628,746 字节，已装入实例并通过 `mod jar verification passed`（SHA 2EF6A02E）。**踩坑**：先试过 32px 粗轮廓 + 角标版本，渲染出来又灰又丑、完全不像旧图标；又试过手写 16px ASCII 点阵，画出来认不出（土系图标像面包）。最后是"矢量大图 → 缩小 → 三色吸附"才对。

> **2026-11-07 17:14 光翼重做（新几何/贴图/真扇动）+ 光耀 t5 永久翅膀 + 光镜可瞄准 已实装**：作者「那个翅膀有点丑，你做个新的」／「翅膀的animation别忘记做了，然后升到光法t5的玩家可以永久有这个翅膀」／「那个镜子要把它弄成可以瞄准的东西嘞，不然光线t4t5砸不到它身上」。①**光翼**：新权威生成器 `tools/rework_light_wings.py`（旧的 `gen_light_wings.py` / `gen_light_wings_model.py` 别再跑，会把这一版覆盖回去 ✗），**9 骨 / 118 块 / 708 面**，单翼 1.64 格、两翼总跨约 3.6 格、翼尖约 2.25 格高；前缘 4 段锥形骨条（每根羽毛的根一律压在骨条下 1.6~1.8 单位 ⇒ 上缘不再被羽尖戳成锯齿 ✓）、7 根初级飞羽（−6°→−44°）、6 根次级（−60°→−81°）、7 根覆羽/拇指羽/肩块、4 根上翘小羽；每根羽毛 = **base + tip 两块**（尖端取 0.58 宽 / 0.34 长 ⇒ 是尖的，不是锯子），沿 z 交错 0/0.28/0.56 三层 ⇒ 有厚度；64×64 贴图改成 8 列 × 3 行的 8×20 材质格（根部亮 → 尖端转金 → 尖 35% alpha 渐隐 + 中轴亮羽轴 + 两侧金边），`LightWingsProjectionTest` 逐块采样 **100% 不透明 / 0 越界像素**。骨头名与 pivot、两个 `uv:[-4,-1]` 参考块、`z ≤ 3.2` 全部原样保留 ✓。中途抓到自己的 bug：v2 只给右翼镜像了肘/腕/内侧、**漏了整块肩膀** ✗ ⇒ 补上后 110→118 块。②**动画**：`waving` 重写 —— 旧的 2 秒 / 只绕 Y 摆 ±7.5° 是给"一排针"写的左右晃，新翅膀要上下扇 ✓；现 1.0 秒循环、峰在 0.25（下压）/ 0.75（上扬），肩 ±12°、肘/腕**反向** ∓4°/∓7°（同向叠加会把翅膀折成一朵蝴蝶结 ✗，预览图上直接能看出来）。★**镜像手性是扫出来的、不是推出来的**：右翼几何是「z 镜像 + 绕 (0,0,1) 转 180°」，所以 `wingright` 自己那根要 **Y 同号 / Z 反号**，它下面的 `wingLeftMid2` / `wingLeftTip2` 要 **Y、Z 都同号**；16 种组合用「右半边点到左半边镜像点集的最近距离」当选尺子，最好一组误差 **0.121 格** ≈ 静止姿势基线 **0.125 格**（我按"全部取反"推的那版实测左右差 2.6 倍 ✗）。留下的回归 `LightWingsAnimationTest` 3 条（含两个相位的 PNG：`build/light_wings_flap_down|up.png`）。③**永久翅膀**：新增 `light/LightWingOwners`（光耀链 t5 = `angel_mercy` ⇒ 永久 ✓；t4 ✗、光线/召唤/光镜 t5 ✗），`TNLightWingsRenderer` 的门槛从"必须有 `tnc:light_wings` buff"改成「有 buff **或** 永久」；**飞行没顺手送**：`mayfly` 仍挂在 buff 上 ⇒ 永久那条只给"看得见"，要送永久飞改 `tickGraceFlight` 一处（已留注释）。`LightWingOwnersTest` 4 条（空石头 / t5 有 / t4 不够 / 别的链不算）。④**光镜可瞄准**：新增 `light/LightMirrorAim`（视线锥 20°、靶子 = `opticalBounds()` 外扩 0.45 格、取沿视线最先命中的那面、再补一次方块遮挡、上限 48 格），`castSky` 在准星瞄中自己的光镜时把天阵开在**镜子正上方**且**只落一列** ✓（t5 撒五根、每根再分六道会把场面糊掉 ✗），阵 / 音效 / `SKY_DELAY` 读秒 / `STYLE_DESCENT` 每 tick 判定一个字没动；`TNLightMirrorEntity.getPickRadius()` 补成 1.1 格（**不动 `getBoundingBox()`** —— `LightMirrorSpells.cast` 拿它做放置检查 ✗）。`LightMirrorAimTest` 6 条（正前 / 背后侧面超距 / 三面取最近 / 锥外近镜不挡锥内远镜 / 擦边 0.12 格算命中而 1.8 格不算 / 空输入不炸）。⑤**测试**：`cleanTest test` **860 用例全绿**（17:01）；光翼三套（Preview / Projection / Animation）与两个新测试全过。⑥**装机**：实例 + 仓库镜像 `mods/tnc-1.0.0.jar` = **200,551,802 B、SHA `E075689F441B6352F6D3DD162A73957A3989E98C1247AAFA82056BC9D43115A9`**（17:12:14 构建、17:13 装入；`install-to-pack.ps1` 报 `identical: True` + `mod jar verification passed`，jar 内 geo / 贴图 / 动画三个哈希与工作区一致 ✓；装前无 java 进程）。**需完全重启游戏生效**。**本轮没进客户端实看**：翅膀大小、扇动幅度、贴身是否穿插（`config/tnc/light_wings_placement.json` 可热调）、光镜锥度、t5 只落一列的手感都要试玩再定。详见[docs/spells/光翼重做与永久翅膀-20261107.md](docs/spells/光翼重做与永久翅膀-20261107.md)、[docs/spells/光镜可瞄准-20261107.md](docs/spells/光镜可瞄准-20261107.md)（预览图 `docs/previews/light-wings-20261107-back|3q|side|flap-up|flap-down.png`）。
>

> **2026-11-07 06:45 法术平衡第一轮：什么该瞬发、什么该蓄力、冷却重排（140 个法术）已实装**：作者「然后我现在希望你开始调平衡吧数值冷却时间这块，什么技能该瞬发，什么技能该蓄力，你考虑一下」。①**只动两个数**：`cast.duration`（读条）与 `cost.cooldown_duration`（冷却），外加把引擎 `cost.exhaust` **全部清零**；伤害系数/动画/音效/粒子一个字节没碰。②**四条原则**：瞬发只给"晚 0.1 秒就没意义"的位置（闪现 1~3、闪电移位、光线 t1~t3）；同一条链必须逐级变重；高级招冷却必须比低级长；蓝耗只走魔法石（`Config.manaCostForTier`）一套。③**六条读条曲线**（SNAP 0 / SELF .35~.55 / SHOT .45→1.60 / ZONE .50→1.50 / SUMMON 1.0→2.2 / ULT 1.5→2.8，按 t1..t5）。④**修掉的病**：主链冷却全是 **0.5 秒**（天打五雷轰 2.5 秒读条 + 0.5 秒冷却 = 蓝够就无限连发 ✗）⇒ 0.5/2/3.5/6/**10**；火/暗/水/地/风五个元素**一档到底**（t1 和 t5 都是 0.6~0.8 秒）⇒ 补上坡度（黑雾 .55→1.25、暗召唤 1.0→2.2、地刺/泥/环 .5→1.2、水球系列 .45→1.2、范围风 .45→1.15）；`water_ball` 那行有**真 bug**（t4「龙吼」1.2 秒、t5「龙灭」反而 0.8 秒，越高级越快）⇒ t5 = 1.2；光系治疗/召唤读条 1.8~3.8 秒太长（实战里根本读不出来）⇒ 收到 1.2~2.4，长冷却（最高 160 秒）保留不动。⑤**工具即设计稿**：`tools/spell_balance.py`（顶部 `CAST`/`CD`/`GROUP`/`OVERRIDE` 就是表），`--report/--check/--apply`，只改三个数、**其余字节原样保留**（含每个文件各自的 CRLF/LF 混排）。⑥**闸门**：新增 `SpellBalanceTest` **7/7**（瞬发白名单双向、同链读条不倒挂、冷却严格变长、t5 攻击读条 ≥1.1 秒、exhaust 全 0、同链同档恰好一个、全书法术可解析）；**变异验证**：把 `heavenly_thunder` 改回 `duration 0 + cd 0.5` 后该测试 **4 条红**并给出中文原因（闸门真会咬）。顺手修掉一个静默跳过：只改 JSON 不动 java 时 Gradle 把 `test` 判成 UP-TO-DATE，这类"读数据文件"的闸门根本不跑 ⇒ `build.gradle` 的 `test` 任务登记两个法术目录为 `inputs.dir`。**测试**：`SpellBalanceTest` 7/7 + `ThunderSealBirthTest` 4/4 + `YanDarkSpellAudioTest` 7/7 = **18/18**。**装机**：`mods/tnc-1.0.0.jar` = **200,546,715 B、SHA `67370907EA0C66011C1085619F0B1B5F64774C44E4C828F10417CD7332A5C168`**（06:41:19 构建、06:42:02 装入，装前是队友 06:29:49 的 `6846F15D…`，已备份 `…bak-20261107-064202`，回读逐字节一致）；**数据包同时改**：实机 `kubejs/data/tnc/spells` 69 个文件（改前整目录备份 `spells.bak-20261107-063406`）+ 仓库镜像 68 个 + jar 内资源 72 个，三处数值一致。**需要完整重启游戏生效**。详见[docs/spells/法术平衡-瞬发与蓄力-20261107.md](docs/spells/法术平衡-瞬发与蓄力-20261107.md)。
>

> **2026-11-07 06:38 风系前戏（风涡阵 + 风灵入体）与风灵/风球模型重做 已实装**：作者「来改改风，改改法阵，加上前戏和优化一下风灵风球模型」。风是最后一个没有读条前戏的元素（读条时脚下空的 ✗）。①**法阵**：新增 `WindEarthGeometry.windSeal(...)`（断口外弧 + 3 主螺旋臂 + 3 逆旋细臂 + 12/16 羽状刻痕 + 3 段反向眼弧 + 中心扇点 + 虚线内环 + t3+ 低语环，薄荷/珠白/淡青三色，body+glow 双通道；单阵 1 448~1 576 顶点，前戏/场地阵 1 960~2 664，上限 6 500），`spell()` 风系分支与 `WindSolidModels.seal()`（不再用 120 元素方块 `wind/seal.json`；`ElementSealRenderer` / `WindChainRenderers` 两个调用点）全部改走它 ⇒ **前戏阵、落地阵、链瞄准阵是同一份几何**（120 元素方块模型与 `draw("seal")` 注册保留给沧澜督军渲染器）。②**前戏**：新 `client/WindCastFx`（约 620 行，`wind_ball`/`wind_weapon` 两组，只读 `SpellCast.Process.progress().ratio()`，纯 immediate 几何、**不生成实体**、出错自关并写 `TN-C/windfx`）：脚下阵随读条 `(2.4+tier×0.48)×(0.45+0.55×charge)` 从半阵长到满阵（t5 约 4.8 格）+ 3 条上升气流 + `8+2×tier` 条风羽往胸口收 + 胸口风环 + 分家风味（BALL 2~3 颗绕阵小球、WEAPON `3+tier` 道扫刃光）+ 后段 1~3 只**风灵虚影**沿贝塞尔入体（`ratio ≥ .30/.20`，`.90` 后淡出）+ 释放瞬间 `Dive` 爆发（3 圈扩散环 + 4 段风柱）+ 极淡屏幕青雾与 t4/t5 心跳推镜。③**风灵虚影**：新 `client/WindSpiritGhost` 借**真 GeckoLib 模型**（dummy `create()` 不进世界、`previewTier(5)` 固定高阶形态、`entityTranslucentEmissive` + FULL_BRIGHT、薄荷偏色、`.45~1.8` 格），失败走 `spiritWisp` 兜底；为此给 `TNWindSpiritEntity` 加 `previewTier(int)`（仅客户端）。④**风灵模型**：新权威生成器 `tools/rework_wind_spirit.py`（先 `gen_wind_sprite_model.py` + `refine_wind_models.py`，别再跑它们覆盖），15 骨/49 块、骨骼名全保留（idle 动画引用的 7 根一根没删），翅膀改成**后掠+上反+两层**（1 肩羽 + 6 初级飞羽 yaw -9→-51°/roll 17→12°/长 7.6→3.5 + 4 覆羽 + 2 末端小羽，每根绕羽根 pivot ⇒ 整翅张合），翅膀骨改挂 `body`（原来挂 root 不随身体悬停）；头有口鼻/眉脊/独立"眼"材质、胸口风精核心、冠 3 根后掠羽、尾 3 根流苏、金条换成 4 段镀金飘带；64×64 贴图改成"材质色板 + 逐面 UV"（7 个渐变小板，任意尺寸方块都不会透）。⑤**风球**：`WindConstructModels.orb` 重排——内核两层反自转、3 条立起来的 60° 大圆经线、反向赤道环、上下对称两道收缩碎环、月牙刃分到 0/38/76° 三个轨道面、±2% 呼吸缩放（绘制次数不变）。测试：新增 `WindCastFxTest` **4/4**、`WindEarthGeometryTest` 通过；全量 836 用例仅 2 个与本轮无关的失败（队友在改的 `YanDarkSpellAudioTest` / `ThunderSealBirthTest`，单跑同样失败）；`runGameTestServer -ParcanaTests` 仅 `boundtapirproducesoffworldtickboundary`（绑定貘离开区块，城镇系统）失败。隔离构建 `jar` → `verify_mod_jar.ps1` 全 `[ok]`，实例 `mods/tnc-1.0.0.jar` = **200,546,704 B、SHA `6846F15D33B194FDF17DDBFB043A761B97D8E50E412E5EB733F1A7E6322D8532`**，`modpack` 镜像同 SHA；实例配置仍只有一条 `mixin.perf.dynamic_resources=true`；装前无 java 进程，旧包（实例 06:24 `53A6D745…`、镜像 06:15 `430AEBD6…`）已备份到 `work\installed-backups\`。**需完全退出游戏再启动**。**本轮未进客户端实看**：前戏亮度、风灵入体时机、t5 满阵大小、风灵头部是否仍太"机器人"、t1 风球是否够"风"，都要试玩再微调。详见[docs/spells/风法前戏与风灵风球重做-20261107.md](docs/spells/风法前戏与风灵风球重做-20261107.md)（含 `docs/previews/wind-seal-20261107.png`、`wind-sprite-20261107.png`、`wind-sprite-flap-20261107.png`）。

> **2026-11-07 06:20 水法前戏改成「逆雨」**：作者「前戏改成逆雨，雨从地面回到天上」。新增 `client/WaterReverseRain.java`（约 260 行，纯几何无状态）：地面湿痕 → 6 处水纹倒着收 + 离地小水冠 → `110+34×档` 条水线**向上**走（`y = cycle × 水柱高`，不是往下掉）→ 头顶十来团软水堆成一片云 + 亮芯 + 旋臂，末尾云收成一颗水珠往下压，正好接出手。档位：水柱 5.7~9.8 格、水线 144~280 条。`WaterCastFx.ritual()` 开头加一个 `WaterReverseRain.ENABLED` 分支接管前戏，旧的「地面潮汐阵 + 四系法阵」整段留在下面（改一个常量就退回）；`tickParticles` 里 `water_rain` 那条链原本从天上下落的粒子改成从地面向上（否则和几何对冲）。门／回声门／水渊兽残影不动，BOSS 读条也会用逆雨。**关键经验**：静止画面里竖直水线和普通下雨长得一模一样，所以给了三个方向暗示——每条水线最上面 30% 加亮成「头」、起水点有朝上的水冠、水是往上收进一片你看着它长大的云。`WaterReverseRainTest` 4/4。文档 `docs/spells/水法逆雨-20261107.md`，预览图 `docs/spells/水法逆雨-model.png`（`tools/reverse_rain_model.py` 按同一份几何 CPU 重画，t5 三档进度）。中间试过在中心加一根水柱、以及用硬边圆盘当云，渲染出来一个像玻璃管、一个像飞碟，都改了。装机：被打包的队友文件 `WindSpiritGhost.java`（0602 起编译不过，0608 修好）挡了约 20 分钟，最后 200,546,878 字节装入实例。

> **2026-11-07 05:50 水法读条水龙：整套撤掉**：作者看了模型图后「丑死了删了吧」，确认范围＝整套全撤。删除 `client/WaterDragonFx.java`（约 820 行，读条时从水涡飞进胸口的水龙）、`WaterDragonFxTest.java`、`tools/water_dragon_model.py`（模型预览渲染器）、`tools/water_dragon_concept.py`、`docs/spells/水法读条水龙-20261106.md` ＋ 两张图（concept / model）、`work\tnc-water-dragon-model-F112997F.jar`。`WaterDragonFx` 是自成一个 `@EventBusSubscriber` 的客户端类，全仓库除自己的测试外**零引用**，删掉后 `assemble` 通过，**水法读条时不再出现水龙**（水法前戏的其它部分 `WaterCastFx` 一行没动，仍照旧）。重新打包 200,501,463 字节并装入实例。注：读条水龙里踩到的两个坑留在文档外的经验里 —— `up = dir.cross(right)` 在右手系得到 `(0,-1,0)`（要 `right.cross(dir)`）；分段等半径圆柱拼接会成「一叠藕片」，需要平行移动坐标架扫掠。雷法小雷龙 `LightningCastFx.smallDragon` 未受影响。

> **2026-11-07 04:50 海神剑 Boss 升空改走作者手绘航线，到高空化作“一直发光”的虚影再落剑**：作者发来手绘航线图（玩家在右 → 红线外冲弧 → 折向上方 `top`，线上几段蓝色线圈）并说「他是这么个行走路线，头是要对着方向的不是单纯平移，然后要飞到很高的天空再消失，然后要先出现一个法阵，再出现巨剑，那我觉得这个boss的虚影也可以发着光因为巨剑是发着光的」，追加「虚影是一直发光的」。①**航线**：`TNWaterLeviathanEntity` 里原来的 `moveRitualToward(channelPosition)` 是“原地斜着平移上去”，改成**二次贝塞尔**——`P0=起飞点`、`P1=P0+水平单位向量×out+(0,rise,0)`、`P2=旧的天阵站点（一格未改）`，`out=clamp(水平距离×0.45,9,26)`、`rise=clamp(|Δy|×0.025,1.5,6)`（只抬一点 ⇒ 起点切线≈水平、终点切线≈竖直，正好是画里“先外冲再拔高”），采样 34~40 tick 爬完 100~150 格，**起飞前 10 tick 站定不动**（法阵先亮、兽先抬头）；②**朝向**：`climbAscent()` 每 tick 用切线算 `yaw=atan2(-n.x,n.z)`、`pitch=-deg(asin(n.y))` 写回 `setYRot/setXRot/CAST_PITCH` ⇒ 头真朝航向；`castingPitch` 新增 `castPose()==2` 分支读 `CAST_PITCH`（不再固定低头 90°），`tick()` 里 `castTilt` 的爬升条件从 `pose==2||3` 收紧成**只有 3**，到站再把切线末端俯仰灌进 `castTilt` 做“仰头→低头”的平滑交接；③**虚影**：新同步量 `CAST_GHOST`（0=本体，`GHOST_TICKS=40`=完全不可见），到站 2 秒内淡完、赶在 `APPEAR_TICK=100` 的巨剑之前；渲染侧 `getRenderType` 换 **`entityTranslucentEmissive`**＋`FULL_BRIGHT`＋冷蓝偏色（R×.72/G×.94/B×1.12）＋`getRenderColor` 逐顶点 alpha，符文自发光层同乘 `ghostAlpha` ⇒ **整段一直发光**（不是一闪一闪），`ghostAlpha<=0.01` 直接 return 省一次骨骼遍历；实体本体、碰撞、`mouthOpen()`、嘴对天印的断言全部保留；④**回程**：仪式结束从天上飞回时按“剩余路程/起始路程”把 `CAST_GHOST` 从 40 线性降到 0，不再落地那一帧“啪”地出现（存档恢复在回程中同样保持虚影）；⑤**法阵先行**：Boss 渲染器 `drawLaunchSeal` 在起飞点画一张 **11 格**水阵（8 tick 淡入、70 tick 淡出，几何复用玩家读条脚下那套，新增唯一公开入口 `client/WaterRitualSeal.launch`），`SeaGodSwordRules` 新增 `SEAL_TICK=APPEAR_TICK-48=52` ＋ `sealVisible/sealOpacity`，`SeaGodSwordVisuals` 的两座天印（半径 36/24）改用 `sealOpacity` ⇒ 读起来是“阵先铺开 → 兽升空消失 → 剑才出现”；玩家自己放 t5 冥穴天阵也提前 2.4 秒亮（无升空段）。**数值零改动**：伤害 60、26 格领域、64 格巨剑/落程、140 命中、8 把连发（Boss 1 把）、200 tick 仪式时长全部原样。**测试**：新增 `SeaGodSwordRulesTest.skySealOpensBeforeTheGiantSwordAppears`（天阵早 ≥40 tick、52 tick opacity=0、100 tick=1、END+8 归零），连同 `SeaGodSwordPathTest`/`WaterSealStyleTest`/`WaterBeastGeometryTest` 全过；`-PleviathanTests` **11/11 通过**（`cryptAscends…` 改写为：tick 8 抬头朝航向 `xRot<-5`、tick 30 高 25 格且 `xRot<-40` 且水平位移>4 格且 `lookAngle.y>.2`、tick 55 `ghosting()` 且嘴仍正对天印、tick 195 `ghostAlpha<.05`、210/260 回程与落地）。装机：实例 `mods/tnc-1.0.0.jar` = **200,514,440 B、SHA `C3D80AAEEE7450DD447002E9E888A4E8D4A91383FD5AC6D22CC11E47F14B9E44`**（隔离构建于 04:47，装前实际实例与镜像都是队友 04:38 的 `F112997F…`/200,517,545 B，已备份 `work\installed-backups\tnc-1.0.0-20261107-0440-F112997F.jar` 与 `…-mirror-…`；稳定副本 `work\tnc-water-skyghost-C3D80AAE.jar`；`modpack` 镜像同 SHA；`tools/verify_mod_jar.ps1` 报 `installed copy is identical to the build output` / `mod jar verification passed`）；装时无 `java/javaw` 游戏进程，**需完全退出游戏再启动**；实际实例 `config/modernfix-mixins.properties` 已核对为唯一一条 `mixin.perf.dynamic_resources=true`。**未做客户端画面验收**（本轮没启动游戏），航线弧度、头朝向、虚影是否真发光、起飞阵是否被地形挡住都必须实机看；可调参数只有 `ASCENT_DELAY`、`beginAscent` 的 `out/rise/3.4`、`GHOST_TICKS`、`LAUNCH_SEAL_RADIUS/TICKS`、`SEAL_TICK`。详见[docs/spells/水法海神剑升空虚影-20261107.md](docs/spells/水法海神剑升空虚影-20261107.md)。

> **2026-11-07 04:45 水龙模型返工：出模型图时发现四个形体问题**：作者「模型图片给我看看」。新增 `tools/water_dragon_model.py`（纯 CPU 重画 `WaterDragonFx.dragonGeometry` 的同一份几何：深度排序+兰伯特着色的结构图 ＋ 按 `RenderType.lightning()` 加色规则的观感图），产出 `docs/spells/水法读条水龙-model.png`（2×2：侧视／四分之三／加色观感／龙头特写）。**画图直接暴露并修掉 4 个问题**：(1) `up = dir.cross(right)` 在右手系里得到 `(0,-1,0)`，背鳍／四角／龙须全朝下 → 两处改成 `right.cross(dir)`；(2) 身子是一段段等半径圆柱接起来的，像「一叠藕片」→ 新增 `sweep()`（26 个采样点 ＋ 平行移动坐标架扫一条连续管，四层共用同一条脊线）；(3) 头是 6 面 `box()` 拼的，加色下一眼是「贴了个箱子」→ 头／脖／吻／下颚全改扫掠锥管，方块版删掉；(4) 龙须往 `right` 张开 1.2 格像两根天线 → 收到 `.10+.34t`、贴着脖子往后飘。另调：躯干半径 `.108-.052t`（原 `.150-.062t` 太粗、头比身子还细）、四层 α `.05/.13/.20/.40`（原合计 1.11 会烧成纯白塑料管）、尾骨 `.24/.17`（原像一圈太阳芒）、尾膜改成骨间扇形、背鳍水膜 α `.14→.30`、腿改成「膝往外下、脚往前、爪朝前」的蹬水姿势。JAR 200,517,545 字节、SHA `F112997F8E0B9C0FE664D42E3AF315FF1597D2D69C23DBC6D446BF1EE33E99A8`（稳定副本 `work\tnc-water-dragon-model-F112997F.jar`，`modpack` 镜像同 SHA）。

> **2026-11-02 · Agent拉取更新必读：** [先读协作更新与运行实例同步](docs/协作更新与运行实例同步.md)。pull后必须核对**实际游戏实例**的 `config/modernfix-mixins.properties` 已启用 `mixin.perf.dynamic_resources=true`，保留其他配置；Java/资源更新另需构建安装JAR。只更新源码或只换JAR，都不能代表本轮内存优化已安装。根目录 `AGENTS.md` 为Agent接手入口。下方旧日期记录不是当前删除／清空操作指令。

> **2026-11-07 06:25 雷锲改成"天雷砸地 → 聚成楔"，天打五雷轰 6 道 → 19 道**：作者「雷锲我感觉还是一般，那个雷锲就不要从地底冒出来吧，这样，一个天雷砸地，然后汇聚出一个雷锲如何」／「六个就是六道」／「天打五雷轰轰的次数太少了」。①**雷锲不再从地下冒**：旧版整个笼子一起长（`growth`），楔子模型的 y 偏移写成 `node.y - (1-growth)*height` ⇒ 看着就是"柱子从土里顶上来"✗；现在每个楔**自己一道天雷**（30 格高砸下、一楔一道、彼此隔 2 tick），落地那一下白闪 ＋ 落点雷网往里收 ＋ 6 道短雷收到楔轴上，模型**落地即出现**（只留 0.2 秒过冲 pop），`translate` 里再没有 y 偏移 ✓。②**六个就是六道**：`ThunderSealRules.strikeTick(slot) = 2 + 2·slot`，雷数**等于**楔数（t1..t5 = 1/2/3/6/9 道），并保证「最后一道 ＋ `birthTicks()` ≤ `charge`」（雷没聚完就开始掉血会让人懵 ✗）。③**天打五雷轰**：玩家 t5 从「1+5=6 道、两秒」改成 **1+9+6+3 = 19 道、四秒**（贴脸 9 道 → 外圈 6 道 → 3 道收尾大雷；伤害仍由公式/流星结算，纯视觉不重复）；衍 boss 那 12 道原来**同一 tick** 全落（只看得见一团白闪、只听得一声"轰"）⇒ 改成每波 4 道、隔 4 tick 的 `pendingStrikes` 队列（伤害总量/落点不变），boss 死亡时清空。**测试**：新增 `ThunderSealBirthTest` 4 条（一楔一道雷 / 落地有序且都在脉冲前成型 / **读源码**禁止 `node.y - (1-growth)*height` 复活 / 19 道与分波入口），并顺手修了**早就红了**的 `YanDarkSpellAudioTest`（队友把 `castRandomSpell` 拆成 `beginDarkRitual` ＋ `executeDarkRitual`，报点声搬了家，闸门指针跟着挪，标准没放宽）⇒ **11/11 通过**。**装机**：`mods/tnc-1.0.0.jar` = **200,546,704 B、SHA `53A6D7450C5DA0A248C3F279CD4B0D5A2D7A9C7A962622D29BE0774E0659E224`**（06:24:31 构建、06:24:47 装入；装前是队友 06:15:11 的包，已备份 `…bak-20261107-062447`；`javap` 从实例包确认 `strikeTick`/`birthTicks`/`HEAVENLY_THUNDER_BOLTS=19`/`queueHeavenlyVolley`/`tickPendingStrikes` 都在包内）。**需要完整重启游戏生效**（渲染代码变了）。详见[docs/spells/雷锲天雷成形-20261107.md](docs/spells/雷锲天雷成形-20261107.md)。

> **2026-11-07 06:07 黑魔法师：黑雾分身（放完黑雾分两个、雾散齐射、打碎反噬 8%）已实装**：作者「我还希望这个boss有分身的能力，能在释放黑雾之后分出两个分身，等黑雾时间结束之后一起攻击，分身有血量，玩家可摧毁，摧毁分身会扣除boss百分比血量」。①**不另写实体类**：分身就是同一只 `TNDarkMageEntity` 加了一个同步标记 `DATA_CLONE`（另写一个类要再注册 EntityType ＋ 一套模型/渲染器/自发光层，而且以后改本体外观还得同步改分身）；差别只有四条 —— 血 **220**（本体 2600 的 8.5%）、经验 0、**不近战也不自己抽招**、**不挂血条也不进二阶段**；`isAlliedTo` 让本体↔分身↔同批分身为友军（否则黑雾会抓自己的分身、抽血会抽到分身）。②**时间轴**：`devour_light` 读完条那一刻（`beginMist`）在他身体左右 2.6 格各分一个，并记下"雾什么时候散"；雾散时（`tickSwarm`）先一声 `WARDEN_SONIC_CHARGE` 预警 **20 tick**，再由 `launchJointAttack` 让本体和**还活着的**分身同一 tick 起手 `night_embrace`（T3、44 tick 起手、各 3 只黑手）⇒ 都在就是 **9 只黑手从三个方向同时砸下来**；分身比雾多活 80 tick，打完自己 `dissolve()`（不是被打死 ⇒ **不**反噬本体）。③**雾的时长是"读数据"不是写死**：新增 `TNDarkGiantCasting.mistTicks(path)` 读法术 JSON 的 `release.target.cloud.time_to_live_seconds`（现在是 15.0 秒 = 300 tick，`tools/gen_dark_fog_spells.py` 里有断言钉住），读不到才回落 300 ⇒ 以后改雾的时长，分身自动跟着改。④**反噬**：`onCloneDefeated` 扣 `CLONE_HP_PENALTY = 8%` 的**最大生命**（2600×8% = 208），两个都打碎 = **416（16%）**；`setHealth(max(1, …))` **不会直接把本体打死**（否则"养分身再杀"能跳过整个二阶段）；本体死亡时 `die()` 里让分身一起散（不留两个"没主的黑魔法师"）。⑤**看得到才算做到**：黑雾在共用权重表里只占 2/21，再叠上"每三招里两招是定死的"，实际约 **3%** 的出手才是黑雾 ⇒ 平均两分钟才见一次分身 ⇒ `TNDarkMageSpells.pick` 加了一条**只属于法师**的节奏「`completedCasts % 5 == 2` 必放黑雾」，招式表/权重/巨人的出手顺序**一个字节没动**（测试钉住巨人前两招仍是 #6/#3）。⑥**存档**：`TnClone/TnAnchor/TnCloneLife/TnSwarmEnd/TnJoint/TnSwarm` 全进 NBT，读档时把分身的 `MAX_HEALTH` 基值重新压回 220（不然退出重进分身会变成 2600 血的"假 boss"）。**测试**：`DarkMageBossTest` 7→**8 个**（新增 `cloneSwarmRulesAreSane`：2 个分身、分身血 < 本体 1/4、反噬比例 ∈(0,1) 且两个加起来 <50%、`devour_light`/`night_embrace` 真在招式表里、齐射动作长度 = 起手 tick/20、法师第 3 招必是黑雾、巨人前两招没被带跑），`gradlew test --tests com.tnc.tnc.boss.DarkMageBossTest` **8/8 通过**；`gradlew build -x test` 通过（06:02:48）。**装机**：实例 `mods/tnc-1.0.0.jar` = **200,521,792 B、SHA `EA2B2F3E2EE349E23CE061136114629F55EE4B0DEEC9B2CCAEC85EFDB6DEA243`**（06:06:49 写入，从当前树构建；装前是队友 06:04:45 的 `2F303834…`，已备份 `…jar.bak-20261107-060649`；`javap` 从**实例包**确认 `CLONE_COUNT/CLONE_MAX_HP/CLONE_HP_PENALTY/VOLLEY_WARNING_TICKS/isClone/configureAsClone/onCloneDefeated` 与 `mistTicks/MIST_AT/indexOf` 都在包内，`jar` 内 13 段动作齐全；装后回读逐字节一致）。**需要完整重启游戏生效**。过程：玩家 06:03 又开了一局，后台脚本等到 06:05:53 退出才装；06:06 想重建时队友 `WindCastFx.java` 正处于半改状态、全树 `BUILD FAILED` ⇒ **没动队友文件**，直接装了自己 06:02:48 那份完整通过编译的产物。详见[docs/boss/黑魔法师-20261107.md](docs/boss/黑魔法师-20261107.md) §3.4。

> **2026-11-07 05:03 黑魔法师加戏：动作 8 段→13 段（诡异/神秘）已实装**：作者「他的动作你也可以多做一点，让我有诡异神秘的感觉」。不是"多加几个动作"，是靠**四条节奏规则**做出来的：①**快慢对比**——全场动作都在 1.4~5.2 秒慢慢来，只有新增的 `twitch`（抽搐）是 **0.9 秒、核心 0.1 秒完成**（甩头 −44° + 断环跳 120° + 碎块全跳 60°，再慢慢归位），人眼对"慢中的一次快"最敏感，这一下是诡异感主力；②**不完全静止**——`idle_watch`（久视）5.2 秒身体几乎不动、只有头 1.6 秒转到 −16°，第 4.5 秒**一片碎块突然抽一下**；③**被动反应**——被打中时 `unravel`（溃散）：身体 0.94↔1.03 压扁-拉伸-回弹、碎块炸开再收、断环狂转 540°，这是唯一由玩家动作触发的表演（15 秒冷却 + 起手中不打断，否则连击会让他永远放不出法术）；④**声音陪着**——`stare`（逼视：整体下沉 1.4 格 + 躯干前倾 11° + 头低 17°）配末影人凝视音 0.85/0.55、`twitch` 配末影人环境音 0.55/0.45（压低后的末影人音色很"空"）。另有 `beckon`（招手：空手抬起"来"两下 + 头歪 14° + 杖心两下搏动）。排期 `TNDarkMageEntity.tickActions`：没起手、没演时 **5~12 秒**排一次，目标在 16 格内 45% 逼视 / 35% 抽搐 / 15% 久视 / 5% 招手；演出期间 `isBusy()` 冻结身体（不移动、不放招、不近战），客户端移动控制器据此停掉 idle/glide（不然两个片段抢同一批骨骼会叠在一起抽）。顺手把 5 个施法片段都加了二级动作（断环反转/碎块绕转/杖心涨缩），片段长度**仍精确等于 `castTicks/20`**（巨人那边多留 0.6 秒余量，法师没有）。测试：`DarkMageBossTest` 6→**7 个**，新增 `eeriePerformanceClipsExistAndMatchTheEntityConstants`（5 个片段都在、名字和实体常量对得上、长度对、骨骼都存在、都不循环、且抽搐必须是全场最短），`gradlew test --tests …DarkMageBossTest` **7/7 通过**。装机：实例 `mods/tnc-1.0.0.jar` = **200,517,088 B、SHA `A50A0D0A28B68C04A4DF1E7D7A2605A0809374EA481677B6B444021791F32B8C`**（05:02:23 写入，从当前树构建于 05:02:06，源码最新改动 04:57:27；`javap -constants` 已确认 `ANIM_WATCH/TWITCH/BECKON/UNRAVEL/STARE` 与 `isBusy/isPerforming/perform` 在包内，`jar` 内 13 个片段齐全；装后回读逐字节一致）。装前实例是队友 05:0x 的 `8310AF4E…`（200,520,201 B），已备份 `…jar.bak-20261107-050223`。**需要完整重启游戏生效**（新动作片段，热重载不生效）。离线连拍自检图：`docs/previews/dark_mage_twitch.png` / `_stare.png` / `_unravel.png`（`tools/preview_geo_anim.py`，只验姿态不验观感）。详见[docs/boss/黑魔法师-20261107.md](docs/boss/黑魔法师-20261107.md) §2.3~2.4。

> **2026-11-07 04:42 黑魔法师（暗系新 boss，法术与巨兽人领主同一套）已实装**：作者「你新做一个暗系boss吧，现在有的这个我想到时候拿去做战士那边的boss / 你做一个黑魔法师，通身黑暗，你可以搜搜网络上的素材，做一个，然后把现在的暗系boss的法术绑到它身上」。①**造型**（作者要的"通身黑暗"）：新增 `tnc:dark_mage`，16 骨 / 87 方块 / 256² 双贴图，高 **3.875 格**、肩宽 1.25 格；四条能自检的规则 —— **无脸**（兜帽里是纯黑 void 方块 + 只有两道暗红眼缝 emissive）、**无脚**（袍摆离地 0.625 格 + 15 片角度/长短抖动的破布 ⇒ 浮着走，`glide` 是滑不是迈腿）、**三档明度**（近黑 `#0C0B15` / 破布边·护肩·帽檐 `#2A2638` / 暗红符文·杖心 `#B0202C`；第一版全压黑 ⇒ 一坨分辨不出的黑块）、**剪影**（尖兜帽 + 带 4 刺的宽护肩 + 比自己高的法杖 + 头后 14 段缺 2 的断环）；8 段动作（`idle`/`glide`/`phase2` + 巨人那 5 个施法片段名，直接对上 `TNDarkGiantSpells.animation()`，起手时模型才有动作）。②**法术绑定=转发，不复制**：`TNDarkMageSpells` 每条都转到 `TNDarkGiantSpells`/`TNDarkGiantCasting`，全 mod **只有一份 `MOVES`**（7 招/权重/连招顺序/起手 tick 全共用 ⇒ 以后改巨人，法师同步生效）；只把召唤那一路写死的 `setScale(10)` 与 `+14` 提成参数：**巨人仍传 10 / 14**（算式 `24+14=38`、`setScale(10)` 与以前逐字节一致，**零行为变化**），法师传 2.6 / 4.5 ⇒ 召唤物 ×2.6、落在他身边 5.6 格（照抄 14 会摆到 15 格外）。③**数值**：血 2600（巨人 3000）/ 攻 14 / 移速 0.22 / 甲 6、两招间隔 70·40（巨人 90·45）、二阶段 50% 走同一条 `TNDarkGiantPhase.shouldEnterPhaseTwo` 但表现是自己的（人涨开 + 断环加速 + 一声尖啸 + 紫字，**不画**巨人那种背后虚影）、血条用原版紫色 `ServerBossEvent`（**不动** `BossHudTheme`——那个枚举被 `BossHudRulesTest` 钉死 13 值 + 12 格头像图集）。④**接线 8 处**（EntityType / 属性表（漏了生成即崩）/ 刷怪蛋 / 创造页签 / 客户端渲染器（**必须在 if-else 外**，漏了 Oculus 影子 pass NPE 崩客户端）/ 中英 lang / 刷怪蛋 item 模型 / `DarkCastFx` 读条前戏从"只认巨人"改成"认暗系 boss"，法师环半径 8.5→5.5）。**巨兽人领主本体、`TNDarkGiantSpells`、`TNDarkGiantPhase`、巨人的模型贴图与动作、`BossHudTheme`、`BossSiteSpawns` 全部未改**（巨人留给作者后面做战士系 boss）。⑤**测试闸门**：新增 `DarkMageBossTest`（6/6 通过）—— 7 招→5 个片段名都存在且 `animation_length == castTicks/20`、片段里没有不存在的骨骼（漏一个就是"起手时模型不动"）、`idle`/`glide` 循环而 `phase2` 不循环、自发光像素**只**落在 eye/rune/orb 三块（跑偏就是"袍子也亮"）、碰撞箱 3.4 < 模型 3.875 且宽<高、召唤参数在 `[0.1,16]` 且落点 5.6 格（巨人仍 24+14=38）、二阶段 50% 共用 `shouldEnterPhaseTwo`。装机：实例 `mods/tnc-1.0.0.jar` = **200,514,393 B、SHA `FC303790A281BAA963B35B9D367674BC88AB77C7C8B51B75C57F1E4C8B6D2625`**（04:42:21 写入，从当前树构建于 04:40:16，含队友 04:32~04:35 水系/海神剑改动；`javap -constants` 已确认 `SUMMON_SCALE=2.6` / `SUMMON_RADIUS=4.5` / `cast(…,double,double)` 重载在包内；装后回读逐字节一致）。经过：04:34:50 先装过一版 `A2EAA43D…`，随后发现召唤半径被写成"半宽+5"会让巨人变成 17 格（动了巨人行为）⇒ 改回原公式只参数化加数并重建；期间队友 04:38:26 装了他们的整包 `F112997F…`（含我的 5 个 mage 类，但 `TNDarkMageSpells`/`TNDarkGiantCasting` 是改前那版），玩家 04:40:33 启动 ⇒ 没覆盖使用中的 JAR，等进程退出后 04:42:21 装回修正版并回读校验 ✓（备份 `tnc-1.0.0.jar.bak-20261107-043450` = 11-06 13:55 队友包、`…044220` = 04:38 队友包）。装完 04:42:21 后玩家 **04:45:52 才启动** ⇒ **正在跑的这一局已经是新版**（不用再重启；队友 04:42:46 又改的 `TNWaterLeviathanEntity.java` 不在这个包里，属他们自己的下一轮）；本轮**未实机看**（只做了离线三视图 + 装机校验），最可能要微调的三处：整体黑度、眼缝亮度、法杖会不会挡脸。网络参考素材**没拿到**（`web_search` 报 `DeepSeek search has no API key for "DEEPSEEK_API_KEY"`）⇒ 外形按通用视觉母题自定，不涉任何具体作品角色。详见[docs/boss/黑魔法师-20261107.md](docs/boss/黑魔法师-20261107.md)。

> **2026-11-06 05:22 第二轮打磨（雷/暗前戏收尾 + 手链真正变慢）已实装**：作者「继续」。①**雷系前戏**：三张阵之间拉一圈电（相邻阵心互连、每 4 tick 换抖动）⇒ 远距离的"三个圈"读成一个阵组；阵散时边缘甩出 6 道短电（原来 alpha 到 0 无声消失）；三张阵大小带 0.85~1.18 随机（不再像模具刻的）；②**暗系前戏**：小阵散开甩 5 道墨线（带 0.1 弧度偏移）；幽灵改成**绕着飞**进来（侧移 `sin(2.2πf)·0.22·(1−f)`，原来沿贝塞尔是直线滑）；③**黑暗之手链**：实体 `TNNightGripEntity.FLY_TICKS` **12→18 tick**（0.6→0.9 秒，配合渲染侧 20 tick 张指，终于能看清"甩出去→追上→抓住"；手是投射物，晚到 6 tick 不影响读条）；④**性能**：`CoilDragonOverlay` 的 `getEntitiesOfClass` 从"每帧一次"改成"**每 tick 一次**"并缓存实体引用（渲染帧率是 tick 的 2~10 倍，位置仍每帧重读、插值不受影响）。新增几何全走已有 helper，无新 RenderType、无注册项 ⇒ 纯客户端。装机：实例 `mods/tnc-1.0.0.jar` = 200,379,659 B、SHA `207710D0456A24DD534F51E07AE51255D0F7B3F002BCE998380FD9FFD240215F`（05:22 写入，从当前树构建，含队友 05:12~05:15 水系改动；`javap` 已确认 `planePoint` / `CACHE` / `FLY_TICKS=18` / `handAt` 在内）。需要完整重启游戏生效。详见[docs/spells/暗手与缠绕龙修正-20261106.md](docs/spells/暗手与缠绕龙修正-20261106.md) §7。

> **2026-11-06 05:12 水法主阵中心改漩涡（去掉“眼睛”）**：作者「水法阵还是可以优化，中间像个眼睛一样不好看，你可以做成漩涡感」。旧中心确实是眼睛结构：**水滴外轮廓 + 0.095R 内环 + 0.032R 白核**，t3 起外面还有 **0.34R 整环**，俯视就是“尖头眼眶 + 圆瞳孔 + 虹膜”。本轮只改 `WaterGeometry.seal` 的客户端几何：①删除 `sealDrop` 和 0.34R 整环；②新增 `sealVortex`：0.225R 深色盆底、三股从 0.30R 卷到 0.045R 的渐细螺旋带（t4/t5 多四缕更紧亮细流）、0.245/0.205/0.165R 三段断续内流弧、两道反向开口逗号、0.04R 深色排水孔 + 0.011R 亮点、3 颗泡沫；**没有任何闭合圆环包着实心核**；③保留外环、0.855R/0.58R 潮圈、月牙、浪鳍、外圈潮汐旋臂和金色刻度，不推翻上一轮已认可的部分。旋转 `time*0.032`，比外阵快，短读条也能看出流向。测试：`WaterSealStyleTest` 4/4（新增“不能出现 `sealDrop`”闸门）、`WaterBeastGeometryTest` 4/4、`BossCastFxStyleTest` 2/2、`WaterVisualsCostTest` 10/10；顶点 t1/t3/t5 = **2856/3552/3808**，仍 ≤4.2k。隔离 JAR **199,108,759 B、SHA `ECB8BF4E17E31DE59556B47B760C90993BD93E8FD715586DE857468404865FF2`、2,087 class**，05:11 装入实际实例 + `modpack` 镜像并回读一致，`verify_mod_jar.ps1` 通过；装前 04:54:48 的 `429215CB...` 已备份 `work\installed-backups\tnc-1.0.0-20261106-0454-429215CB.jar`（镜像同名），稳定副本 `work\tnc-water-vortex-ECB8BF4E.jar`。**仍未实机看新漩涡**；旧三档预览图还是水滴中心，已从文档正文撤下，不要拿来判断新版。生成新预览的临时 dump 测试因队友 `WarriorComboService.java` 的 `Stashed changes` 冲突标记挡住整包编译而没跑成，临时测试已删除、未改队友文件；实际 JAR 是在冲突出现前构建并验证的。详见[docs/spells/水法阵重绘与读条前戏-20261106.md](docs/spells/水法阵重绘与读条前戏-20261106.md)。

> **2026-11-06 05:00 反馈修正批：前戏放大 / 缠绕龙层级 / 黑雾顺序 / 手链变慢变粗变深 已实装**：作者「雷法的前戏不咋喜欢，太小家碧玉了，那个什么唤龙小阵可以离玩家远点啊…暗的也不错，但是也可以优化一下…缠绕着玩家的龙模型加载怎么在玩家模型之后…雾加载在手之后，导致手一直看的见，黑雾抓周围的敌人的距离要很远…黑暗之手链手出去的速度可以慢些，手线可以粗些…深暗红色…雷锲出现的太突兀」。①**雷系前戏**：唤龙小阵 2.05→**4.30~5.90 格**、阵半径 0.46→**0.85~1.30**、小雷龙 scale 0.28→**0.52**（体长 1.4~1.9 格）、飞行 13→**18** tick、脚边雷网 0.85→**1.30~2.30** 并加 14 根外刺、聚雷球抬到 2.70、释放爆闪 R 2.0→**3.4+0.9×档**，另加"阵心→胸口"的引导电弧把远阵和玩家连起来；②**暗系前戏**：阵型从"整环+刻度齿（雷锲感）"改成 **断弧+爪痕+墨渍**，主阵/小阵分别 8/9 tick **淡入**（原来第 1 tick 就 0.32 亮度 = 突兀），主阵 1.55→**1.95**、小阵 0.48→**0.70** 且挪到 **3.3~4.7 格**，幽灵 12→**18** tick、scale 0.55→**0.64**，t2+ 就有反向外圈，**t4+ 阵边伸出 3 只黑影手**（复用 `DarkHandLines`）；③**缠绕龙层级**：新增 `client/CoilDragonOverlay`，`coil()` 几何从实体层搬到 `RenderLevelStageEvent.AFTER_ENTITIES` 补画 —— BOLT 不写深度，实体层里先画会被玩家整段盖掉（所以"永远在背后"）；画在实体之后，深度的 `LEQUAL` 自己把后半圈挡掉 ⇒ 头在前、尾在后，真正"绕"起来；④**黑雾**：`renderHands` 从雾片之后挪到**之前**、手改用测深度的 `DARK`（原 `DARK_OVERLAY` 是 NO_DEPTH_TEST ⇒ 永远压在最上面），抓取半径改成 **雾半径×1.45**（`DarkFogCloudEntity#gripIntruders`，领域雾封顶 96 格），渲染搜人同步 ×1.45；⑤**黑暗之手链**：grow 9→**20 tick**（`HANG_GROW_TICKS`）、淡入 3→6、手线 0.026/0.028/0.024→**0.042/0.045/0.038**、血珠 0.045→0.062、手背索 0.050→0.075，颜色换 **深暗红** `BLOOD_DEEP{0.30,0.012,0.030}`（新增 `darkArcDeep/darkBoltDeep/darkBeadDeep`，默认 `darkArc` 一个字节没改 ⇒ **献祭保持原样**）。装机：实例 `mods/tnc-1.0.0.jar` = 199,107,200 B、SHA `429215CBB7EC0495991E63249553673405F874C2A3502960B779137D4F5B7931`（04:54:48 写入，队友从当前树构建，`javap` 已确认本轮全部类/常量在内；自己那份 `work/tnc-rev-35070A10.jar` 更早，未覆盖）。详见[docs/spells/暗手与缠绕龙修正-20261106.md](docs/spells/暗手与缠绕龙修正-20261106.md)。

> **2026-11-06 04:56 水法读条前戏：水龙（照雷法小雷龙的路子做，更大只）**：作者「弄完你去抄一下雷法新做的雷龙，抄一下，做一个水龙出来，要比较大只的水龙」。新增客户端 `client/WaterDragonFx.java`（约 700 行，自成一个 `@EventBusSubscriber`，**没改队友的 `WaterCastFx` 一行**）：读条时在施法者周围地面亮一口**水涡**（双环 + 6 条旋转吸水臂 + 4 条小水柱，9 tick），一条**水龙**从涡心窜出，沿二次贝塞尔弧线飞进胸口（`身高×0.62`），入体炸两圈水环 + 6 道水花，t4+ 推一下镜头。抄的是雷法 `LightningCastFx.smallDragon` 的「小法阵→龙→入体」节奏与贝塞尔飞行；换成水的身子和更大的体型：**脊干 12 节、每节 4 层圆管**（深蓝辉光 .07/×2.25 → 青 .16/×1.55 → 浅青 .28 → 浪白芯 .60/×0.42，不是线勾）、7 片半透明背鳍 + 亮脊线、5 根扇骨尾鳍、头（颅/吻/可张合下颚/眉/外朝眼/两对四支角/两口 14 节长龙须/上下各 4 牙/每侧 3 鳃鳍）、四条两段腿 + 白爪、3 圈绕身水环 + 4 颗水珠、拖尾 2 颗 SPLASH/BUBBLE/tick。**体型**：`scale = 1.08 + .08×档`、`全长 = 2.8 × scale` ⇒ t1 3.02 格 / t5 3.92 格，是雷龙小版（0.75 格）的 4~5 倍，只有一个自由度、不会像土神枪那样被拉长（测试钉住）。放龙：t1~3 各 1 条（ratio .34）、t4 2 条、t5 3 条（.22/.46/.70），寿命 18~22 tick，`AFTER_WEATHER` + `RenderType.lightning()` 现画（和水法前戏同一阶段/同一层；实体阶段用这层看不见）。测试 `WaterDragonFxTest` 4/4（比小雷龙大 3.5 倍以上、体型/全长单调且比例恒定、阈值有序递增、贝塞尔端点=出生点/胸口），`EarthCastFxTest` 6/6 同轮重跑。JAR 199,107,200 字节、SHA `429215CBB7EC0495991E63249553673405F874C2A3502960B779137D4F5B7931`（04:54:48 构建，稳定副本 `work\tnc-water-dragon-429215CB.jar`）；**构建时游戏/队友 Gradle 进程还在跑，`install-to-pack.ps1` 按规矩拒绝了覆盖**，改用 `-Wait` 挂后台等进程退出，随后自动装入（实例与构建产物同 SHA、`installed copy is identical to the build output`、`mod jar verification passed`，`modpack` 镜像同 SHA；需完全退出游戏再启动）。另：本轮首次 `assemble` 失败是队友 4:48~4:50 在改的 `TNWaterSpellEntity`/`DragonRoarCombo` 语法错误（重复定义/类型不匹配），我**没动他们的文件**，等他们修好后重新构建通过。仍未进客户端实看：体型、龙须长度、四层管亮度是最可能要微调的三处，详见[docs/spells/水法读条水龙-20261106.md](docs/spells/水法读条水龙-20261106.md)。

> **2026-11-06 04:55 水法唤兽门第二版：真实 Boss 残影改即时渲染 + 正前方主门/回声门（已并入 4292 集成包）**：作者「我没看见小兽啊，啥都没有啊，你学习一下雷法暗法他们怎么做的 然后前戏内容你自己再丰富优化一下」。**只改玩家水法读条里的唤兽门**，脚下主阵/Boss 阵/法术数值不动。①残影不再走 `GeoRenderer.reRender/actuallyRender`（第一版很可能因为 `actuallyRender` 不套实体朝向、又只有 0.10 缩放/0.58 alpha 而看不见），改成和 `LightningCastFx`/`DarkCastFx` 一样的即时 `MultiBufferSource`：`GHOST_SCALE=0.12`、`entityTranslucent` 原 Boss 模型原贴图、`LightTexture.FULL_BRIGHT`、自己压 `Axis.YP` 朝向，对 `model.topLevelBones()` 调 `renderRecursively(..., isReRender=true)`，不碰 Boss 的 4 倍缩放/发光层；模型或 renderer 不可用时同帧画 `ghostWisp` 兜底并写 `TN-C/waterghost` 日志。②主门不再 360° 随机：以读条开始时玩家 yaw 为 0°，落在**正前方 ±60°**，距离 2.05–2.90 格，只取立/斜两种；再加一张更小的回声门（偏 ±106–137°、2.25–3.40 格），主门半径 `0.74+tier×0.05`。门里新增八片张开的潮叶、四段留白潮弧、金刻度、中心亮核、三颗绕门水珠、出场亮环、到地面的三条细水线；残影加淡回声和三点拖尾，门心在兽出场前滴落水粒子，入体加短屏幕水膜。③测试：`WaterBeastGeometryTest` 4/4（新增“主门 250 次全在正前方半球”）、`WaterSealStyleTest` 4/4、`BossCastFxStyleTest` 2/2、`WaterVisualsCostTest` 10/10；`LeviathanSpellGameTests` 11/11（05:04 集成包重跑）。为打通整包编译，合并了队友水法实体重复的 `remove(RemovalReason)`，并把 `TNWaterFieldEntity.aimEnemy` 从 `ServerPlayer` 放宽到 `LivingEntity`（保留双方逻辑）。本轮自己的验收包 199,104,062 B、SHA `5B455E8C...`、2,086 class；**最终实际装入的是队友 04:54:48 的集成包 199,107,200 B、SHA `429215CB...`、2,087 class**（`javap` 确认含本轮 `gateFx/echoCenter/ghostWisp/ghostTrail` + 队友 `WaterDragonFx`），实际实例与 `modpack` 镜像同一 SHA；装前实际实例队友 04:53 的 `22F39590...`、镜像 04:35 的 `CFE2B89B...`，以及本轮 5B 包，均已备份到 `work\installed-backups\`。安装时无游戏进程，**需要完全退出游戏再启动**。**本轮仍未实机看画面**；集成包里队友的水龙与本文的唤兽门/残影会同时在读条里出现，未删改队友水龙。若仍看不见兽，先查 `logs/latest.log` 的 `TN-C/waterghost: direct GeckoLib render armed` / `ghost render disabled once`。详见[docs/spells/水法水渊兽前戏-20261106.md](docs/spells/水法水渊兽前戏-20261106.md)。

> **2026-11-06 04:40 土系前戏第二版：裂缝重做 + 加厚 + 神枪改回原比例**：作者「前戏的土裂缝太假了，非常的假，然后前戏也太简单了，我觉得你照着你的想法去优化一下，丰富一下，然后武器幻化当然不错，当时你好像把他拉长了，我希望保持他原有的比例，可以放大的其实」。①**裂缝整条重写**：7 点/6 段带惯性随机游走的锯齿折线（`drift=drift×0.58+jag×0.52`，所以不闪、偶尔大折角）、加速曲线往脚下收、缝宽逐点收细；每条裂缝现在有**缝底柔黑（地下是空的）+ 缝芯 + 两侧被顶起来的土唇（内缘贴地、外缘抬高 0.026~0.036 格）+ 缝内半埋碎石 + 2 道卫星碎纹**，偶数条必分叉、t4 起全部分叉；亮部只画**缝内偏侧的一条余烬边**，外轮廓描边彻底去掉（那是最像贴纸的画法）。②**加厚七层**：贴地尘雾带（2 条宽暗环）、每 12 tick 从脚下推出一圈的地面尘环（3 圈错相位）、3~5 块从缝里顶起又塌回去的土板（`darkBox` 五面）、6 条缝尖→胸口的地气抽吸线、胸口土光团（两盘交叉＋余烬环＋3 颗绕转火星）、最后 0.12 秒脚边 8 根尖石、t5 神枪虚影。③**神枪比例改回原样**：长度 `4.4+2.8×form` 是唯一自由度，半宽 `×0.0669`、枪头长 `×0.30`、杆半径 `×0.018`（0.0669 是实测原枪模型 16 长 / 2.14 宽），任何 form 下长宽比恒定，只整体放大，不再越到后面越细长。测试：`EarthCastFxTest` 6/6（新增"折线真的在拐弯且向尖端收细"、"神枪三比例跨 form 恒定"）。JAR 199,057,506 字节、SHA `CFE2B89B43906631FD3AF311F5178B9E36DBC099468D4ED694040E4328B439A8`，04:35:26 构建，04:36 装入并回读一致，`verify_mod_jar.ps1` 全 `[ok]`，实例 = build/libs = `modpack` 镜像同一 SHA，稳定副本 `work\tnc-earth-cast-CFE2B89B.jar`。**需要完全退出游戏再启动**；仍未进客户端实看，土唇高度/缝宽/余烬边三处是最可能要微调的。详见[docs/spells/土法读条前戏-20261106.md](docs/spells/土法读条前戏-20261106.md)。

> **2026-11-06 03:50 土系前戏崩溃已修复（补齐暗部顶点元素）**：作者「是不是哪里装机没装好，检查一下」。**装机没装错**——实例里 03:26 的队友整包（`C4B28376…`）确实带了我的 `EarthCastFx`；真正的问题是这段代码崩：`crash-2026-11-06_03.41.15-client.txt` 里 `java.lang.IllegalStateException: Not filled all elements of the vertex`，栈顶 `WaterGeometry.vertex → quad → stroke → EarthCastFx.crackCore → ritualDark → onRenderWorld`（`latest.log` 里同一栈 4 次）。根因：暗部几何借了 `WaterGeometry` 的写法（只写位置＋颜色），而 `LightningRenderTypes.DARK` 的格式是 `NEW_ENTITY`，每个顶点还要 uv／overlayCoords／uv2／normal，少一个就在 `BufferBuilder.endVertex` 抛异常——一放土法就崩到桌面，和装机无关。修复：`EarthCastFx` 自带 `darkVertex/darkQuad/darkStroke/darkDisk/darkRing/darkCube`，暗色这一遍每个顶点六件套写全；亮部仍走 `WaterGeometry`（加色层 `POSITION_COLOR` 格式对得上）。顺手把 `immediate` 缓冲"空帧不 `endBatch` 就切渲染类型"的隐患也修了（两遍无条件收尾，无内容先早退）。新增 `EarthCastFxTest.darkGeometryFillsEveryVertexElement`：动态代理录下顶点调用序列，断言每个 `endVertex` 之前 vertex/color/uv/overlayCoords/uv2/normal 都在——回归测试 4/4。修后 JAR 199,051,813 字节、SHA `6C40789B705035BB3FF7313D7BF1472741E6058601377B01C20B1DA7BCEBA724`（03:54:53 构建，03:55 装入并回读一致，`verify_mod_jar.ps1` 全 `[ok]`；实例 = build/libs = `modpack` 镜像同一 SHA；稳定副本 `work\tnc-earth-cast-6C40789B.jar`；`javap` 确认实例包里有 `darkQuad/darkStroke/darkDisk/darkRing/darkCube`）。另加了兜底：`onRenderWorld` 外层 `try/catch(Throwable)`，再画错只关掉这套前戏并写 `TN-C/earthfx` 错误日志，不会再把客户端带走。**需要完全退出游戏再启动**再试土法；这次只能确认"不再因顶点元素崩溃"，画面好不好看仍未实看。详见[docs/spells/土法读条前戏-20261106.md](docs/spells/土法读条前戏-20261106.md) §8/§9。

> **2026-11-06 03:26 水法唤兽门重画 + 真实水渊兽模型残影（纠正 02:45 版）**：作者反馈「法阵这么丑？？？然后我说的小兽是我们的水系boss的模型，你把他缩小了不就行了？加上透明度即可啊」，并补充「我刚刚说的法阵是唤出小兽的法阵」。**只改玩家水法读条里唤兽的那张侧门**，脚下主阵/Boss 阵/法术规则都不动。①侧门不再复用整张 `WaterGeometry.seal`：半径 `0.64+tier×0.045`，只留深蓝水镜、外环、1.10×细光晕、四段留白潮弧、四枚短金刻度、中央细环+露珠、两颗绕门水珠；立/斜门高度按 `max(sideHeight, gate+.18/.22)` 抬高，保证圆环下缘不切地；②小兽改为真实 Boss 模型：新增 `client/LeviathanGhostVisual`，直接烘焙 `TNWaterLeviathanEntity` 的 GeckoLib 模型和 `water_leviathan_bedrock.png`，`GHOST_SCALE=.10`（约 1.5–1.7 格长、0.9–1.2 格宽），`RenderType.entityTranslucent` + 顶点 alpha 半透明；不调用 `reRender`（避开 Boss 的 `withScale(4)` 和不透明发光层），改为 `preRender` 抵消 4 倍缩放后 `actuallyRender(..., renderLayers=false, alpha)`，并反射补 `GeoEntityRenderer.animatable` 防 tracking 骨骼 NPE；`miniBeast`/616 顶点手画蛇形已删除；③侧门方位/距离/三姿态仍每个 `CastState` 只随机一次，残影沿原二次贝塞尔飞进胸口，`beastEnd` 触发 `absorb` 胸口水花；脚边 `rippleGlow`+`SPLASH` 不变；Boss `playerCast=false` 不长门/兽。测试：`WaterBeastGeometryTest` 3/3（缩放/yaw、1250 次侧门不穿地不穿玩家、半透明层 flush 顺序）、`WaterSealStyleTest` 4/4（禁止 `miniBeast`、要求真实模型/半透明）、`BossCastFxStyleTest` 2/2、`WaterVisualsCostTest` 10/10；`LeviathanSpellGameTests` 11/11、`LightMirrorGameTests` 8/8 本轮重跑。隔离产物 199,050,060 B、SHA `C4B28376AF001A89D4E0902EB1C8A09B4BAB81DBD87ACAC3B15D478F5AF22833`、2,067 class；03:26 装入实际实例并回读一致，`modpack` 镜像同 SHA，`verify_mod_jar.ps1` 全 `[ok]`；装前 03:15 的 `8D950991...` 已备份 `work\installed-backups\tnc-1.0.0-20261106-0326-8D950991.jar`（镜像同名 `...-mirror-...`），安装时无 Java 游戏进程，需**完全退出游戏再启动**。**本轮仍未进客户端实看**：真实模型是否出现/朝向是否正确、1.5 格残影是否过大、半透明层与加色层叠加是否发灰，都要试玩；02:45 的手画残影预览图已删除，详见[docs/spells/水法水渊兽前戏-20261106.md](docs/spells/水法水渊兽前戏-20261106.md)。

> **2026-11-06 02:45 水法单侧小阵与蓝金水渊兽残影已实装**：作者「水法的前戏你也可以优化做做，我希望是周围随机出现一个不同角度的小法阵，然后跑出我们的小型蓝金水渊兽的身影飞进玩家的身体，脚边出现小水波」。在 `client/WaterCastFx` 的玩家 `CastState` 里新增 `playerCast/sideAngle/sideDistance/sideHeight/sideStyle/beastStart/beastEnd/beastImpacted`：①每个读条只在构造时随机一次，周围 `1.55~2.50` 格、`0.82~1.60` 格高，三种姿态（平贴 / 立着朝玩家 / 斜插各约 1/3），半径 `0.60+tier×0.035`（0.6~0.8 格，tier 封顶 3）；小阵复用 `WaterGeometry.seal` 潮汐母题＋青白外环＋两颗绕门水珠，`ratio≈0.055` 淡入，小兽飞走后淡出；②`miniBeast` 用 7 段蛇形薄管＋暗蓝体色＋6 片金色背帆＋金环头/双角/胸鳍/双尾鳍，从侧阵沿二次贝塞尔飞进胸口，约 616 顶点，`u=0..0.14` 淡入、`0.82..1` 缩小淡出，到 `beastEnd` 触发 `absorb` 胸口水花；③脚边 `rippleGlow` 小水波（`min(2.6,1.05+tier×0.12)`）＋每 tick 1–2 颗 `SPLASH`，小兽沿路补 `FALLING_WATER/BUBBLE`；Boss 的 `ritual` 也有脚边小水波，但 `playerCast=false` 不会长小阵/小兽。纯客户端，只读 `SpellCast.Process.progress()`，不改读条/伤害/半径/实体/网络包。测试：`WaterBeastGeometryTest` 2/2（616 顶点、有限坐标；1250 次随机侧阵不穿地/不穿玩家）、`WaterSealStyleTest` 4/4、`BossCastFxStyleTest` 2/2、`WaterVisualsCostTest` 通过；`LeviathanSpellGameTests` 11/11、`LightMirrorGameTests` 8/8。隔离产物 `work\water-isolated-build-20261106\libs\tnc-1.0.0.jar`，199,045,945 B，SHA `269E53F6972C977308E1C12D7C913AEB4A04F2AED1932A1C8C2A5AD84D138780`，2,065 class；02:45 装入实际实例并回读一致（装前为队友 02:22 的 `3C6C0481...`，199,041,572 B，备份 `work\installed-backups\tnc-1.0.0-20261106-0245-3C6C0481.jar`；镜像旧版备份 `...-mirror-...-0245-3C6C0481.jar`），`modpack` 镜像同步 269E，`verify_mod_jar.ps1` 全 `[ok]`；安装时无 Java 游戏进程，需**完全退出游戏再启动**。**未进客户端实看**：侧阵三种角度、短读条里小兽可读性、脚边水波强度都要试玩。详见[docs/spells/水法水渊兽前戏-20261106.md](docs/spells/水法水渊兽前戏-20261106.md)；旧版手画残影预览已删除，见 03:12 修正条。

> **2026-11-06 02:24 土系读条前戏已实装**：作者「地面的裂纹逐渐向玩家脚下靠近然后土块土粒子从裂缝中冒出，汇聚到玩家身上形成一个漩涡然后施法，你有别的吗」。新增 `client/EarthCastFx`（纯客户端，只读 `SpellCast.Process.progress()`，不生成实体、不改读条时长、不碰伤害；暗部走 `LightningRenderTypes.DARK`、亮部走 `RenderType.lightning()`，两遍分开 `endBatch`）：①**地裂往脚下爬**——`earth_shot` 组五条链按档 6/8/10/13/16 条放射裂缝，起点半径 `R×(0.78~1.0)`（`R=4.8+0.85×档`，上限 9.5 格），缝尖 `R×(1−0.93×reach)` 随 `smoothstep(ratio/0.85)` 收到脚边（≤1.2 格）；4 段折线用确定性 hash 抖动（不闪）、偶数缝带分叉、缝尖一颗余烬点；②**土块土粒子从缝里冒出来再被吸走**——每条缝 3 块几何碎石先竖直顶出 0.35~0.9 格，ratio>0.40 后被 lerp 进绕身螺旋（半径 2.1→0.5、升到胸口）；真实 `BLOCK`(coarse_dirt/stone) 粒子隔帧从缝尖喷出，`tnc:earth_dust` 沿切线吸向胸口，`MINIMAL` 下减半；③**石旋涡**——`1/1/2/3/3` 圈倾斜石环（半径随档与进度收紧）＋ `7+2×档` 条螺旋石屑＋地面 1/2/3 圈法阵＋`8+3×档` 根刻度；④**额外四样**：缝缘跑金光（暗缝＋金边，越读越亮）、t4+ 身侧 4/6 块浮石（各套金环）、t5（土神的枪）ratio>0.45 头顶凝金色巨枪虚影指向准星、t3+ 地鸣（每 12/8/5 tick 推镜 `0.05+0.045×档` 度，越近越密）；⑤释放余波 16 tick：三重尘环＋12 条放射碎石＋t4+ 竖向余烬＋`22+10×档` 碎石粒子＋震屏 `0.14+0.05×档`；屏幕土黄尘线＋四周压暗 t1 0.18 → t5 1.00（低阶不压暗、不震屏）。`EarthCastFxTest` 3/3（裂缝条数递增、方向铺满整圈、缝尖单调收缩到 ≤1.2 格、`reach` 单调），`gradlew assemble` 通过；全量 792 个用例只有队友的 `YanDarkSpellAudioTest` 那 1 个失败（断言其正在改的 `YanDarkBossEntity.castRandomSpell` 报点音效，与本轮无关，单独重跑同样失败）。首版 199,041,572 字节（SHA `3C6C0481…`，02:22:38）装机后**03:41 一放土法就崩**（暗部顶点元素不全，见顶部 03:50 修复条）。**未进客户端实看：裂缝疏密、金边亮度、尘幕强度要试玩。** 详见[docs/spells/土法读条前戏-20261106.md](docs/spells/土法读条前戏-20261106.md)＋[概念图](docs/spells/土法读条前戏-concept.png)。

> **2026-11-06 02:10 雷系读条前戏已实装**：作者「雷法的前戏你也可以优化做做，我希望是周围随机出现三个不同角度的小法阵，然后跑出我们的小雷龙飞进玩家的身体，脚边出现小闪电这样」。新增 `client/LightningCastFx`（纯客户端，只读 `SpellCast.Process.progress()`，不生成实体、不碰伤害，走加色 `BOLT` 层）：①**固定三张**小阵（2.05~2.6 格随机位置，朝向 34% 平贴 / 36% 立着 / 30% 斜插），0.10/0.22/0.34 进度各出一张；②每张阵亮一会儿就“开”，一条**线勾小雷龙**（白闪头＋双角＋6 节脊＋两对翼＋四腿＋分叉尾＋3 条绕身电套，`scale .28+.025×档`，按速度方向摆姿势）从阵心窜出、沿二次贝塞尔飞进胸口，入体爆闪＋6 条放射电弧＋电火花，t4+ 一张阵放两条；③**脚边小闪电**：全程小雷网（`.85+.07×档`）＋ `3+档` 条每 2 tick 换位置的短电弧；④额外：t2+ 头顶聚雷球、身侧爆电（每 3 tick 换一批）、释放雷环外扩＋地光＋白闪＋震屏、屏幕极短白闪＋淡蓝黑暗角（不压暗）。**按学派判**（`spell.school.id` 含 `lightning`，组名兜底）——雷系没有 `dark_*` 那种 group，以后加新雷法术自动吃到。与 `TNSpellClientVisuals`（只有雷速 FOV／落雷震屏）不重复。装机 `tnc-1.0.0.jar` SHA `99BAFE3A5C2AF75B95018713347A70CA6CE10B6A0D39699CFFDF8359CE93AC48`（02:10 装入并回读校验；稳定副本 `work\tnc-light-cast-99BAFE3A.jar`；备份 `.bak-light-cast-*`；含 01:53 暗系前戏那版全部内容）。详见[docs/spells/雷系读条前戏-20261106.md](docs/spells/雷系读条前戏-20261106.md)＋[概念图](docs/spells/雷系读条前戏-concept.png)。

> **2026-11-06 02:15 水系 Boss 与光系 Boss 阿波罗的法阵前戏已实装**：作者「那你记得连着水系boss和光系boss阿波罗也一起改了」。①利维坦（`TNWaterLeviathanEntity`）不走 SpellEngine，新增 `CAST_REMAIN/CAST_TOTAL/CAST_VISUAL_TIER/CAST_VISUAL_FAMILY` 四个 synched int，`beginVisualWindup`/`clearVisualWindup` 与 `actionTicks` 成对；`WaterCastFx.scanBoss/bossRitual` 画 10.5~18 格 Boss 尺寸潮汐主阵，并在真实 `MOUTH_FORWARD`/`MOUTH_HEIGHT` 的嘴前立一张竖直潮汐印＋外环；水缚招走 BIND，水柱/横环按 Boss 半径加粗加高。②阿波罗（`TNApolloEntity`）新增 `DATA_CAST_SPELL/DATA_CAST_TICKS` 两个 synched int，`castDuration()` 给客户端 55/60 tick 总时长，`startCast`/`aiStep`/`applySpell`/目标取消/死亡同步与清理；`LightCastFx.scanApollo/apolloRitual` 复用日轮主阵，范围 6.4~7.3 格，头顶加两圈日冕环，覆盖召唤信徒、光线 t2~t5、光耀 t4/t5。③召唤时服务端旧 `TNMagicCircleEntity` 从 `STYLE_LIGHT` 提到 `STYLE_LIGHT_GRAND`、半径 3.8→4.4，避免新阵旁出现低档小阵。④海神剑 64 格落剑路径印也从旧方块 `WaterSolidModels.seal` 换成 `WaterGeometry.staticSeal`，水 Boss 整条仪式链统一。测试：`BossCastFxStyleTest` 2/2、`LeviathanSpellGameTests` 11/11、`LightMirrorGameTests` 8/8（新增阿波罗读条 60→1→0、释放/取消清零断言）、`WaterSealStyleTest` 3/3、`WaterVisualsCostTest` 通过；`verify_mod_jar.ps1` 全 `[ok]`。隔离产物 `work\water-isolated-build-20261106\libs\tnc-1.0.0.jar`，199,039,340 B，SHA `7C8984213CF60CF19AF25A8B88AE0455FD281B24C105F042B701DCD70B0A9AA8`，2,063 class。02:05 首装 E178 后，队友在 02:08/02:12 并发重编整包并覆盖；最终实际实例为 `0DAF2C43A63AA4008327362822B31756E253DD038DC426008FD12C0605BE8EB8`（199,039,340 B，2,063 class，02:12:41），其 2,063 个 class 与本地 7C89 逐字节一致，`javap` 确认两个 Boss 同步 getter、`WaterCastFx.scanBoss/bossRitual`、`LightCastFx.scanApollo/apolloRitual` 都在包内，`verify_mod_jar.ps1` 全 `[ok]`。装前备份 `work\installed-backups\tnc-1.0.0-20261106-0205-4A4CAB5C.jar`（队友 01:52 的 4A4C 整包，含其土 Boss/暗系内容）；`modpack` 镜像已同步最终 0DAF，镜像备份 `work\installed-backups\tnc-1.0.0-mirror-20261106-0215-E1788D77.jar`。**未进客户端实看 Boss 前戏，嘴部阵是否穿模、日冕是否挡视线要试玩。** 详见[docs/boss/水系boss与阿波罗法阵前戏-20261106.md](docs/boss/水系boss与阿波罗法阵前戏-20261106.md)。

> **2026-11-06 01:52 土系 Boss（石枪）也换成巨剑已实装**：作者要求「连着土系 boss 也一起改」。`tools/gen_earth_weapons.py` 现在同时产出 17 个 Boss 专用黑金模型 `stone_lancer_earth_wpn_dark_<name>`（几何复用，贴图 `tnc:block/earth_weapon_dark/*`：近黑石体＋原武器亮部与固定金色矿脉），`EarthWeaponModels.bossModels()` 与 `StoneLancerClient` 注册 17 个模型。Boss 三个破土技能同步：天降枪雨（move 0）6/8 发里隔一发换黑金巨剑（仍 15 宽、37 长，保持 `scale()==15` 断言）；扇形破土（move 1）15 根里隔一根换（17 宽、40~60 高）且整轮只放一声低频爆炸；大地震（move 4）24 根里每 3 根换 1 把（24~44 宽、50~86 高），实体上限仍 24、`scale>=12 && height>=25` 不变；天降巨枪 80/250/190 与技能循环未动。渲染与玩家土刺共用 `renderWeaponSpike`（新增 `EarthWeaponModels.isBossWeapon`，同样有裂纹环/飞溅碎石/震屏）。`EarthWeaponModelsTest` 新增 Boss 用例（2/2，原版 `BlockModel.fromStream` 解析 34 个模型并校验黑金贴图路径），`TNDirectSpellModelsTest` 通过，`assemble` 成功；期间并行队友改 `LightCastFx`/`TNWaterLeviathanEntity` 打断编译 3 次，重试后通过。实装 JAR SHA-256 `4A4CAB5C7B9BE90DF9B22C31428CD50D136319FD3CCBA7D02EC6D49799EC9E83`（198,998,668 字节，01:52:51，= 队友 01:53 暗系那版整包，`verify_mod_jar.ps1` 全 `[ok]`，`javap` 确认 `StoneLancerCombat.pickBoss` / `EarthWeaponModels.bossModels` / `EarthAttackRenderer.isBossWeapon` / `StoneLancerClient` 注册都在包内；实例与镜像一致）；稳定副本 `work\tnc-earth-boss-weapons-20261106.jar`（`C4D4B322…`，198,976,060）。详见[土法石化武器](docs/spells/土法石化武器-20261105.md) §土系Boss同步。

> **2026-11-06 01:53 暗系读条前戏已实装**：作者「周围随机出现不同角度的小法阵，然后跑出幽灵飞进玩家的身体，还有别的吗」。新增 `client/DarkCastFx`（纯客户端，只读 `SpellCast.Process.progress()`，不生成实体、不碰伤害）：①读条一开始脚下主阵就亮（双环反转＋刻度＋地面爬线）；②按进度依次浮出 **3~6 张**小阵（t1~t2=3、t3=4、t4=5、t5=6），位置随机、**朝向随机**（34% 平贴 / 38% 立着朝外 / 28% 斜插）；③每张阵亮一会儿就“开”，一只**线勾幽灵**（兜帽＋肩线＋三缕袍＋两条手臂＋两颗血眼）沿二次贝塞尔飞进施法者胸口，入体那一下胸口炸一圈黑线＋墨粒子，身后带 16% 路程的拖尾残影；④额外六样：主阵、地面爬线、t3+ 鬼火收紧、t4+ 头顶黑日、t5 心跳推镜、释放墨爆＋屏幕暗闪（紫黑暗角，与水法蓝、光法过曝区分）。覆盖 `dark_hand / dark_sacrifice / dark_summon / dark_fog / dark_dragon`；黑暗巨人用 `isCasting`＋本地 70 tick 估算同步播一遍；衍的两段式仪式保留叠加。短读条有兜底（ratio>0.88 强制开阵）。装机 `tnc-1.0.0.jar` 198,998,668 B、SHA `4A4CAB5C7B9BE90DF9B22C31428CD50D136319FD3CCBA7D02EC6D49799EC9E83`（01:53 装入并回读校验；稳定副本 `work\tnc-dark-cast-4A4CAB5C.jar`；备份 `.bak-dark-cast-*`；含 01:37 巨人领域雾那版全部内容）。详见[docs/spells/暗系读条前戏-20261106.md](docs/spells/暗系读条前戏-20261106.md)＋[概念图](docs/spells/暗系读条前戏-concept.png)。

> **2026-11-06 01:37 黑暗巨人领域雾的黑手已实装**：作者「那你记得连着黑暗boss的也一起改了」。巨人「吞噬光」走 `TNDarkGiantFog`（`cloud.volume.radius *= 30`，>64 格），仍是 `tnc:fog` 类型 ⇒ `DarkMistRenderer` 与 `gripIntruders()` 自动覆盖；本次加 domain 分支：①视觉——雾心 **6 只**大手聚在中心 `2.6 + i×2.0` 格（普通雾按 `radius×0.2` 比例，150 格会被甩到雾皮上 ✗）、scale **1.7**、抓人 scale `0.95+0.07×档`、手臂只在 **20 格内**画（否则横穿半屏）、跳过 `TNDarkGiantEntity`；②手感——判定 **12 tick**、推力 **0.06**、减速封顶 **III**、`owner` 非玩家时**只抓玩家**（BOSS 召的黑暗之王/领主不被按进雾里）。其余暗 Boss 视觉（线手、血线、暗阵、暗色落雷）自动继承，无需单独改。装机 `tnc-1.0.0.jar` 198,666,410 B、SHA `3534725C2A6E0A49960EB1EBD869D582638FE4D9441654FCF0310C9062E8B0F4`（01:37 装入并回读校验；稳定副本 `work\tnc-boss-fog-3534725C.jar`；备份 `.bak-boss-fog-*`；含 00:47 黑雾手那版全部内容）。详见[docs/spells/衍boss施法前戏-20261105.md](docs/spells/衍boss施法前戏-20261105.md) §11。

> **2026-11-06 01:06 水法阵重绘＋读条前戏已实装**：作者「你现在看看水法，优化优化水法的法阵，然后也给他加上前戏」。①`client/WaterGeometry.richCircle` 不再贴旧的 120 元素方块 `seal.json`，改成程序化潮汐印：外白青环＋细金圈、0.855R 青潮圈、0.58R 白内圈、8/12 道浪形符、4/6/8 片有宽度的浪鳍、两端收尖的月牙（t3 起，t4/t5 更大）、t4 3 条/t5 4 条渐细旋臂、中心水滴＋白核；t1/t2 没有月牙和旋臂，低高阶结构明确分家。单阵含发光层 t1=2336 / t3=3104 / t5=3360 顶点，≤4.2k。②新增 `client/WaterCastFx`：读条期地面潮汐阵＋四链分家前戏——水球胸前水珠/三向环、水波 t3 起环形潮墙（低阶只有贴地涟漪）、水缚 4/6/8 根水柱＋t3 起横环＋t5 封顶、雨链头顶雨阵＋12/20/28 条水线＋t5 第二张反向阵；低阶不竖潮墙（水缚仅 4 根短柱）/不压屏/不震屏，t3 起才有轻微暗角和水线（t4/t5 加重），t3 起释放有轻微震屏；③实体阵/雨阵天印/海神剑 36/24 格天印全部接新几何；大半径调用点卫星降到 2 个，雨阵 t5 第二张天印降 tier 2，避免叠顶点。④`WaterSealStyleTest`（3/3，含四链与顶点预算）＋`WaterVisualsCostTest` 通过；隔离构建产物 198,665,959 B、SHA `9BD4893B21B3286173EB583CFE4F0F42B8478604935DEA60722C1B0E0D502E4F`、2,045 class；01:06 装入实际实例并回读校验（装前备份 `work\installed-backups\tnc-1.0.0-20261106-010558-0947F855.jar`，含队友 00:47 那版全部内容；安装时无 Java 游戏进程）。**预览图是顶点 dump 的离线俯视图，不是游戏内截图；本轮尚未进客户端逐档试玩。** 详见[docs/spells/水法阵重绘与读条前戏-20261106.md](docs/spells/水法阵重绘与读条前戏-20261106.md)。

> **2026-11-06 00:47 黑雾里冒出手、把敌人抓紧雾中已实装**：作者「黑雾加一点那个就是，中心是不是会冒出手，把敌人抓紧黑雾之中」。①手本体抽成 `client/DarkHandLines`（黑手与雾中手**共用一份骨架**；`clench` 0..1 让五指从张开→收拢）；②`DarkMistRenderer` 雾心冒 3 只无主之手（domain 雾 2 只，从 −0.55 升起、clench 呼吸），雾内**最近 4 个敌人**身上各抓一只（手腕在脚下 −0.72、手指朝上、朝雾心倾 14°、clench 0.82–0.96 脉动），并从雾心 `(0,0.3,0)` 拉一条 `darkBolt` 手臂过去（g>0.45 才长）；③新增 `LightningRenderTypes.DARK_OVERLAY`（不测深度——雾片写深度会把手挡没 ✗）＋ `nearFade`（48 格外淡出，防穿墙）；④服务端 `DarkFogCloudEntity.gripIntruders()` 每 6 tick：往雾心推 `0.10×min(1,d/r)`（`hurtMarked` 同步玩家）、`MOVEMENT_SLOWDOWN` 40 tick 等级 `min(4,tier)`、上跳砍到 35% ⇒ 被按住但**还能还手**；不拽施法者/队友，仅 `isDarkFog` 生效。装机 `tnc-1.0.0.jar` 198,665,541 B、SHA `0947F85536CF947C5C1623F2A4343F4B75BC6E484F1DDF68B0C942392B67DD8D`（00:47 装入并回读校验；稳定副本 `work\tnc-fog-hands-0947F855.jar`；备份 `.bak-fog-hands-*`；含 00:36 那版全部内容）。详见[docs/spells/衍boss施法前戏-20261105.md](docs/spells/衍boss施法前戏-20261105.md) §10。

> **2026-11-06 00:36 暗魔法线化已实装（去掉手模型 ＋ 一节一节的血线）**：作者「那个手模型可以去掉，我比较想是就是我们的这个黑线简单勾勒一个手，你有看过超凡蜘蛛侠2吗，最后钟楼蜘蛛侠伸出蛛丝，那个蛛丝的顶端展开五根线，像手一样」「献祭的血线也是同理」「我不希望像雷电一样瞬间就过去，你可以一节一节过去」。①`LightningBoltGeometry.darkBolt/darkArc` 新增 `growth`（0..1）重载：按**弧长比例**剪线、末点插值、线头点一颗 `darkBead`（黑芯＋血边血珠）⇒ 线是**爬**过去的；②`TNNightGripRenderer` 删掉 `CustomModels.render(DARK_HAND)`，改 `renderLineHand()`：指根弧 5 段＋掌缘 2 段＋腕口＋**五指各两节**（错开 1.6 tick、每根 8 tick，指尖血珠）＋拇指两节＋背后黑索（也按 growth 爬）—— 就是「蛛丝顶端展开五根线」；相机量用位姿矩阵求逆取，任意旋转/缩放都正对相机；③`DarkSiphonRenderer`（献祭/吸血血线）从实心管 `NightHandGeometry.tube` 换成 `darkBolt`：第 i 条晚 2 tick 出发、14 tick 长完、长满后 8 颗血珠沿 `DarkSiphonRules.flow` 往施法者流 ⇒ 「血被抽走」。装机 `tnc-1.0.0.jar` 198,637,798 B、SHA `4E61F15DE5AD6DE37F3F7D92516241B559E36344CB50911B82D68BC860CBD71E`（00:36 装入并回读校验；稳定副本 `work\tnc-line-hand-4E61F15D.jar`；备份 `.bak-line-hand-*`；含 00:12 那版全部内容）。详见[docs/spells/衍boss施法前戏-20261105.md](docs/spells/衍boss施法前戏-20261105.md) §9（含[骨架示意图](docs/spells/dark-hand-lines-concept.png)）。

> **2026-11-06 00:12 暗系「黑线 ＋ 血线」已实装**：作者「其实闪电要是横着来是不是就是黑线和血线了，你觉得呢」——是，且已落地。新增 `LightningRenderTypes.DARK`（**普通 alpha 混合**，黑线靠遮光才成立；加色层里黑色 = 看不见 ✗）＋ `LightningBoltGeometry.darkBolt/darkArc/darkRing`（四层：血雾 → 血线 → 黑芯 → 一线血高光，配色只有黑 0.015/0.010/0.030 与血 0.62/0.03/0.07）。接线三处：①`TNLightningArcRenderer` 在 `theme == DARK` 时改画黑线，命中落点从"雷网＋地光"换成两圈往回收的黑线环；②`DarkSealRenderer` 阵上方加 3（低阶）/5（高阶）条从阵心甩到环外、边转边起伏的游动黑线；③所有 `LightningTheme.DARK` 电弧（衍的法术连线、雷锲 t5 雷索、登神 t5 暗弧）一并变黑线血线。竖着劈的暗色落雷（`flash_dark`/`lightingball_dark`/`god_dark` 模型）不动，正好和横线分工。装机 `tnc-1.0.0.jar` 198,629,350 B、SHA `6B26C831C6820F7EEA9970EB1D6B290066D7EC293AA5A54F8E6BC54F3E163303`（00:12 装入并回读校验；稳定副本 `work\tnc-dark-lines-6B26C831.jar`；备份 `.bak-dark-lines-*`；含 00:03 那版全部内容）。详见[docs/spells/衍boss施法前戏-20261105.md](docs/spells/衍boss施法前戏-20261105.md) §8。

> **2026-11-06 00:03 黑暗衍施法前戏已实装**：作者「优化一下衍boss的施法 … 衍是有黑暗元素的，你懂吧，但是不要雷锲」。衍（`tnc:yan_dark`）四个技能改成**两段式**：冷却到了先 `beginDarkRitual()` 在**落点**铺暗系仪式阵（主阵立刻出现＝报点），卫星小阵按 `DarkRitualSequence.ignitionTick` 一颗颗亮、门环按 `gateTick` 一道道立、出手前 6 tick 黑雾炸开；`pendingTicks` 走完才 `executeDarkRitual()` 真放招，蓄力期间 `navigation().stop()` 站定且不插别的技能。四档 `WHISPER/VEIL/ECLIPSE/SOVEREIGN`（移位/雷暴/天打五雷轰/神在投篮）＝ 前戏 18/24/32/42 tick、主阵半径 7/10/13/17、小阵 2/3/5/7、门环 0/2/3/5。新增 `STYLE_DARK_MINOR=13` / `STYLE_DARK_GRAND=14` ＋ `client/DarkSealRenderer`：`dark_circle.png` 反向转 ＋ `black_circle.png` 压出黑心 ＋ 6/12 道**两折勾爪**（外段暗紫粗、内段亮紫细、随 `pull` 往中心拉）＋ 高阶外环/刻痕/8 道内拉弧 ＋ 阵心一暗一亮两"眼"；只用暗紫/亮紫，不沾金蓝。**没有雷锲**（不生成 `ThunderSealEntity`、无禁锢/雷索/雷网）；天打五雷轰里原那张旧 `STYLE_LIGHTNING` 阵删除，改由前戏暗阵承担；伤害/半径/球速/天气全部未动。装机 `tnc-1.0.0.jar` 198,627,812 B、SHA `472A07431D6BC0B67C33E5A0337D2B39D0370F4598120D8A6429B3EC45C32D85`（00:03 装入并回读校验；稳定副本 `work\tnc-dark-ritual-472A0743.jar`；备份 `.bak-dark-ritual-*`）。详见[docs/spells/衍boss施法前戏-20261105.md](docs/spells/衍boss施法前戏-20261105.md)。

> **2026-11-05 23:52 雷电第三 / 第四批已实装**：①**登神瘦身**——跟随神（`maintainGodFollower`）与那圈 `STYLE_STORM` 雷笼（"雷网"的真正来源）都不再生成，龙脚下也不铺网/地光，登神期间只剩「身上电弧 ＋ 脚下雷印 ＋ 盘身雷龙」；②**雷牢真的钉人**——`ThunderSealEntity` 新增 `rooted`（UUID→解除 tick）：距中心 `min(radius, 4)` 格内的敌人每轮脉冲 `setNoAi(true)` ＋ 高倍减速 ＋ 每 tick 清零水平速度 ＋ 拉一条雷索，封印移除/施法者死亡时全部解冻，笼外照旧只吃伤害；③**照光法做前戏/法阵**——新增 `STYLE_LIGHTNING_MINOR=11` / `STYLE_LIGHTNING_GRAND=12` ＋ `client/LightningSealRenderer`（真雷画的环/辐条/刻痕/电须）＋ `ThunderRitualSequence`（BRAND/CAGE/TRIBUNAL 三档、小阵 2/3/4/6/8、门环 0/0/2/4/6）＋ `ThunderSealSpells` 的 `PENDING` 排程：主阵立刻出现、小阵按蓄雷一颗颗点亮、门环中段立起、后半段 3 道收束电；④**雷球 t4/t5 俯冲龙**——`TNLightningDragonEntity.MODE_DIVE`：龙停在落点、渲染器把它抬到 53~63 格高再倒转 180°（头朝下）一路降进地里，身后沿同一条线拖 4/5 颗巨大雷球（几何壳＋白核＋球面电须），落点有被吸起的电环；触发在雷球链 t4 `cataclysm_thunder_orb` / t5 `god_descent`。装机 `tnc-1.0.0.jar` 198,616,254 B、SHA `EB3FD729CC8F38AE11371E3F2B3C37378342211C6CFEC3453843D6A9ECBDC062`（23:52 装入；这份是**等队友构建结束后用当前源码重编的合集**，含他 23:25 的 `LightCastFx` 小日轮 ＋ 我的雷电四项；稳定副本 `work\tnc-lightning-b4-EB3FD729.jar`）。详见[docs/spells/雷电全面重制-20261105.md](docs/spells/雷电全面重制-20261105.md) §10。

> **2026-11-05 23:26 土刺换巨剑（土法压迫感）已实装**：作者指出「土法还是没压迫感」且「土刺不是三根吗，可以换成巨剑」。土刺（kind 4，tier 2/3/4）现在隔一根换成**巨型石化武器**从地里捅出：tier 2 的 3 根 → 2 把巨剑＋1 根岩刺（高 11 格、宽 2.8~3.6），tier 3 的 6 根 → 3 把（高 13），tier 4 的 36 根 → 6 把（高 18）；其余岩刺也整体加大（tier 2 1.1 宽/2.6 高起、tier 3 2.2/5 起）。渲染时武器绕 X -90° 立起并按高度/2 抬升（剑尖朝上、柄插地），带 12° 外倾、地面裂纹环、10 块飞溅碎石和 `drawChips` 碎屑；每把落地播 `STONE_BREAK`＋`GENERIC_EXPLODE`，高 ≥7 格的破土给 3.4 级震屏并在前 5 tick 触发 FOV 内收＋尘幕。同时删掉 `renderBurst` 里重复调用的一次 `drawBurstColumns`（原先每帧画两遍）。受并行队友改 `LightCastFx` 影响 `gradlew test` 两次被打断，本次只改渲染/数值、未动模型资源；`gradlew assemble` 成功，`verify_mod_jar.ps1` 全 `[ok]`（含 17 个 projectile 模型可烘焙/贴图检查）。实装 JAR SHA-256 `604AC9D3996FCFCDC65D2A28591BE456C123EB3D342D46165B306464EE39686A`（198,615,756 字节，23:26:20），实例/build/libs/镜像三者一致。详见[土法石化武器](docs/spells/土法石化武器-20261105.md) §土刺换巨剑。本条优先于下方旧记录。

> **2026-11-05 23:50 光法低阶「六瓣小日轮」已实装**：作者 23:08 说「初级的法阵有点不大好看」。本轮只重画 t1/t2（`INSCRIPTION` 铭刻）的主阵，没有动档位结构、数值，也没有把高阶几何缩小。低阶常驻阵 `STYLE_LIGHT_MINOR` 改用独立的 `minorSeal`：多圈细环（外白/0.80R 金/0.50R 白/0.30R 金/1.055R 外晕）＋六片白瓣金心＋六根细花茎＋六道根部有宽度的锥形外光（尖端小菱形）＋十二道短刻度＋中心六片小花瓣围亮核；不再用穿过中心的六根十字光。读条阵 `LightCastFx.groundSeal` 共用同一母题，花瓣/外光按读条顺序点亮，并保留反向双弧“笔尖描环”。半径 `r >= 3` 的主阵铺满花瓣，卫星/天使落点的小半径用紧凑版（环＋外光＋核），避免几十颗小阵顶点爆炸。低阶仍没有门环、竖环、光柱、光带、光雨、压暗、心跳、震屏。`LightSealStyleTest`＋`LightRitualSequenceTest` 全绿（新增“MINOR 必须有独立小日轮几何”守门）。最终版因队友并发构建会覆盖 `build\libs`（甚至写出无 class 的资源包），改用隔离 buildDir `work\light-isolated-build-20261105` 构建，产物 2029 个 class；隔离构建 SHA-256 `8D02A27E55A917A4294792B20D8245F1F7EA85EDB44099E59812B05836B2B5D1`（198,616,261 字节，23:49:38），实际实例随后又被队友整包覆盖为 SHA-256 `EB3FD729CC8F38AE11371E3F2B3C37378342211C6CFEC3453843D6A9ECBDC062`（198,616,254 字节，23:53:27）；`javap` 对比确认其 `ElementSealRenderer`/`LightCastFx` 字节码与隔离构建 0 差异，光系改动仍在包内。备份 `tnc-1.0.0.jar.bak-light-minor-8D02A-20261105-2350`（装前 `...pre687-...2334`、`...flower-...2331` 也保留）。安装时无游戏进程、目标 JAR 可独占打开；客户端需完全退出再启动。详见[光法阵规范](docs/spells/光法阵规范-20261105.md) §4、[光法施法前戏与光污染](docs/spells/光法施法前戏与光污染-20261105.md) §8c。本条优先于下方旧记录。

> **2026-11-05 23:35 构建协作提示**：并发构建期间 `ThunderSealEntity.java` 出现字面 `\r\n` 转义（整段代码挤成一行）、`TNLightningDragonEntity` 缺 `Vec3` import、`LightningSealRenderer` 把 `0.045D` 传给 `float` 参数；为让光系通过编译做了最小修复（加 import、`0.045F`、把字面 `\r\n` 还原为换行），没有改雷电数值/逻辑，雷系作者可继续覆盖。备份 `work\ThunderSealEntity.java.bak-mojibake-20261105-2319`。

> **2026-11-05 21:22 光法「低阶/高阶分开施法」已实装**：作者看完第一版光法阵后指出「法阵要是每个都是一样的施法方式就不好看了，低级和高级应该是有区别的」。本轮把光耀/召唤从“同一套仪式缩放”拆成三种结构：`LightRitualSequence.Rite` = `INSCRIPTION`（t1/t2，一张干净地面符＋2/3 颗贴地小阵，无竖环/无光柱/无压暗/无心跳/不震屏）、`SANCTUARY`（t3，4 颗小阵各带竖立伴生环＋2 道门环＋轻微暗角/一次心跳/细光柱）、`DESCENT`（t4/t5，6/8 颗小阵＋4/6 道门环＋满档暗角/光柱/光带/脉冲/光雨）。新增 `STYLE_LIGHT_MINOR=9`（简阵）与 `STYLE_LIGHT_GRAND=10`（多一圈外环＋12 道刻痕），`TNMagicCircleRenderer` 已同步贴图与渲染分派，服务端主阵/卫星/落点按档位选样式，t3 起竖环 1/2/3 道；光耀/召唤 10 个 JSON 的手部动画也按档换成单手托光、单手聚球、单手举天、双手引导、双手旋转。`LightRitualSequenceTest`＋`LightSealStyleTest` 全绿（含“三档结构差异”“四种光阵样式分派”守门），`gradlew build -x test --offline` 通过；实装 JAR SHA-256 `05E3849C05A71AA3816812405B955143491B5C54FBAFBF20CEE8D763B2B7642D`（198,588,170 字节，21:28:56），备份 `tnc-1.0.0.jar.bak-light-tier-rites-final-20261105-2129`（同日 `...-2122`、`...-2126` 两份同级备份也保留）。`STYLE_LIGHT_GRAND` 即使半径 <4 也走满配几何，保证 t4/t5 的落点阵不会被“小半径简化”悄悄降级。安装时无游戏进程；客户端需完全退出再启动。详见[光法阵规范](docs/spells/光法阵规范-20261105.md)、[光法施法前戏与光污染](docs/spells/光法施法前戏与光污染-20261105.md)。本条优先于下方旧记录。

> **2026-11-05 21:22 雷电第二批已实装**（同 `3C4D47C1…` 那份 JAR）：①**雷速链雷龙分档＋缠紧**——`LightningDragonGeometry.coil` 去掉 `turns` 参数、按脊线长度反算圈数（`turns = √(L²−h²)/(2πr)`），半径 1.57→1.10 格、跨度 4.6→3.05 格、≈3.4 圈，真正贴着身体缠；t3「雷风」0.30 ／ t4「雷充」0.42 ／ t5「闪电登神」0.55 各一条龙，由新增的 `COILS`（UUID → 体型 ＋ 到期 tick）驱动，**不再按 buff 判断**（t1「电光」和 t4「雷充」挂的是同一个 `tnc:lightning_haste`，分不出档 ✗）。②**跟随神半透明**——反编译 `CustomModels#render` 确认它确实用传入的 RenderType，但走的是 baked-item 路径、顶点 alpha 写死 1.0，只剩贴图镂空能透（`lightning_god.png` 仅 63% 像素透明）；新增 `client/lightning/AlphaFadeBuffer`（`MultiBufferSource` 包装器，逐顶点乘 alpha）⇒ 神 0.60 ／ 跟随神 0.52 ／ 雷球 0.85。③**雷锲链整套重做**——旧版扁片子立柱 ＋ `beacon_beam` 细线全删，`ThunderSealRenderer` 重写：竖雷笼骨（`min(18, count*2+4)` 根收拢到 `0.40R`）、两道正反旋转的短雷横箍、顶冠 8 道汇聚雷、雷楔节点引雷，立顶是 40 格大雷 ＋ 每楔一根天雷 ＋ 12 道径向爆雷 ＋ 扩散冲击环，脉冲与实体每 12 tick 的伤害同拍。并发构建下必须用 `-I work/ln-isolated-build.init.gradle` 单独构建、并用 `javap` 确认是 SRG 名（`m_6299_`）再装机（`build\libs` 会被队友的 `:jar` 中间产物覆盖 ✗）。详见[雷电全面重制](docs/spells/雷电全面重制-20261105.md) §9。

> **2026-11-05 19:57 雷电全面重制已实装**：①**雷球链透明修复**——反编译实际装的 `spell_engine-0.15.12` 定位到根因：`CustomLayers.spellObject(LightEmission)` 里 `translucent` 硬编码 false ⇒ `NO_TRANSPARENCY`，贴图 alpha 全被忽略；改为自建 `LightningRenderTypes.SPELL_MODEL`（TRANSLUCENT＋自发光＋不写深度＋方块图集），神/大雷球/环绕雷球全部恢复半透明，并在模型下垫主题色辉光。②**t1 与 t3 终于有表现**——新增实体 `tnc:lightning_arc`（横向雷电，四层白芯加色、每 2 tick 换种子），t1 起手→落点一道细电＋落点雷网，t3 落点先按到地面再劈并把起手电弧接上。③**天打五雷轰**——30 颗旧流星模型 `scale` 降到 0.05（看不见，只留伤害载体），改成中心 1 道 12.5× ＋ 周围 5 道 7~9× 错峰落雷（`QUEUED_BOLTS`），雷龙跟上。④**粒子大砍**——20 个法术 JSON（主链 10 ＋ 雷球链 5 ＋ 雷锲链 5）按规则砍 70~90%，`witch`/`squid_ink`/`smoke_medium` 整组删除；登神链环绕电光 3→2 条/隔 tick、雷印 1.0→1.6 格、上限 10→6。⑤**登神链雷龙缠身**——`TNLightningDragonEntity` 新增 `MODE_COIL`（跟随主人 UUID），`LightningDragonGeometry.coil` 与破土龙同一套骨头、脊线换螺旋（2.35 半径×2.5 圈×4.6 高，持续游走），登神期间恒有一条。⑥**雷锲链**接上新雷电：封印脚下旋转雷网＋地光、立顶白闪、t5 九霄天刑换成 38 格真落雷。`gradlew build -x test --offline --no-daemon` 通过，实装 JAR SHA-256 `CEC7072004F1CAD949E007C16A09D60ADE8802CEA47156B8F5C48888281C12F1`（198,012,954 字节，19:56:21），`install-to-pack.ps1` 全部 `[ok]`、`identical: True`。详见[雷电全面重制](docs/spells/雷电全面重制-20261105.md)。本条优先于下方旧记录。

> **2026-11-05 21:08 土法石化武器（17 把）已重装修复紫黑方块**：复制 17 把现成 3D 武器（龙剑/霜刀/空戟/厄镰/震地锤/阔刃/细剑/偃月刀/方天戟/汉铁剑/铁锏/月牙铲/七昧杖/红缨枪/环首刀/柳叶刀/越王剑）重绘成石/土材质。上一版紫黑方块根因：原模型含 125 个 faces 为空的 cube，原版 `BlockElement.Deserializer` 要求 1~6 个面 → 整个模型被替换成 missing model；生成脚本现已丢弃这些元素，并新增 `EarthWeaponModelsTest` 用原版 `BlockModel.fromStream` 真解析 17 个模型。土神的枪每次随机一把 **5.2 格长**石化兵器（原细土柱 3.4 格，宽/厚 2.8 倍）；巨柱升天时柱身外侧插出 6 把武器、爆点小岩柱每 3 根换 1 根（2.6/3.4 格高）。`TNDirectSpellModels` 13→30 条。`EarthWeaponModelsTest`、`TNDirectSpellModelsTest`、`EarthLaunchLayoutTest`、`gradlew assemble` 通过；实装 JAR SHA-256 `18B11D9996DF6212DD85268DD603552A0F3A5ADD514C50BBA6395A32439A5FD2`（198,313,718 字节，21:17:09），`identical: True`。详见[土法石化武器](docs/spells/土法石化武器-20261105.md)。

> **2026-11-05 19:43 土神的枪重做（巨柱升天）已实装**：`earth_god_spear` 不再从天上砸 25 倍巨枪（旧 mode 3 用方块图集画 projectile 模型，容易贴图错位/穿插）。新表现：细土柱（0.42 宽 × 3.4 长，沿飞行方向）射向目标；命中后地面升起 96 格巨柱（45 tick，约 2.25 秒），柱顶托着目标一起上天，半径 2.6 格内敌人一并带走；命中瞬间屏幕尘幕、FOV 内收、震屏、柱脚尘环与高空碎石雨，到顶炸开石屑，再 40 tick 整柱崩解。普通土爆也补了地面冲击环、尘墙与落石。`EarthLaunchLayoutTest` 4/4、`EarthRuptureLayoutTest` 4/4、`gradlew assemble` 通过；实装 JAR SHA-256 `44DAE7861099F503A43A87E7800417B767D7A726EC20075C79424DEF7060D32A`（198,007,788 字节，19:43:12），实际实例与工作区镜像一致。详见[土神之枪巨柱升天](docs/spells/土神之枪巨柱升天-20261105.md)。

> **2026-11-05 土法岩柱破土与爆炸碎石已实装**：震地/护身岩柱改为过冲破土（顶端峰值约 +15% 高度、柱身 +8% 宽度、外倾 7.5° 后回正），柱脚按各自柱位迸出 8–18 颗碎石并带地面扩张尘环与 12 颗冲击尘；所有爆炸落点新增 9 根（巨型 14 根）依次冲出的小岩柱，叠加在原有四散碎石上而非替换；补镜头震动与 `STONE_BREAK` 音。713 项单元测试通过（第二轮后布局测试 4/4；全量测试因并行分支 `BankAccountTest` 重构未能重编译）；截至 15:20 实际实例 JAR SHA-256 `B3FA14FE4189402978F8D65C4038AEE247B1D6865ED7304365218EBFEB95D132`（15:12:34，`verify_mod_jar.ps1` 通过），工作整合包镜像已同步。同日第二轮再加破土前 2 tick 柱脚预震、落定补尘、开场快速尘环与爆炸低空双尘环/向上尘柱。详见[土法岩柱破土与爆炸碎石](docs/spells/土法岩柱破土与爆炸碎石-20261105.md)。

> **2026-11-05 01:48 雷暴重制＋雷龙已实装**：落雷不再只掉一个模型 —— 从云层到落点一整根（46 格起、随体型更高，主雷＋2~5 道副雷＋分叉，天雷还有回击），白芯→金/蓝/紫四层加色发光；法阵与施法者新增蛛网雷网、会跟着落雷「呼吸」的雷笼（雷场/雷暴/天雷/登神四条链都挂）；t5 天雷落点与神在投篮大雷球爆心会从地面窜出「没有身体、全雷电勾线」的雷龙（≈230 个方块＋80 道电弧，头/下颚/龙须/龙角/四爪/尾鳍全动，约 45~58 格长），黑暗衍天雷同步 1.3 倍暗紫版；落点会按到地面、同地不叠龙，破土瞬间带震屏＋白闪。`gradlew build -x test` 通过，实装 JAR SHA-256 `CF399386804A518B1F6C4853DE76FFD5EA8C47CFD7132878BE248BC30D351096`，实际实例 mods 副本哈希一致。全量测试 48 项失败均为本次之前就存在的 Wand/Wearable/Guidebook/LightWings 用例，雷系测试全绿。详见[雷暴重制与雷龙](docs/spells/雷暴重制与雷龙-20261105.md)。本条优先于下方旧记录。

> **2026-11-05 19:39 天使模型全部重制已实装**：`angel`（法阵中心 4/6/10 格高雕像）与 `fightingangel`（召唤战斗天使）从 15 个扁盒子重建为 106 个 cube：四层收腰裙摆＋前后裙片＋金边、胸甲/护心宝石/肩甲/护腕、后发/刘海/金冠/几何眼睛、16 段宝石光环、每侧 16 片四层羽毛、右手白金光矛；贴图从 128px 重叠 UV 换成 256px 独立 box-UV，按皮肤/头发/白袍/金/羽毛/钢/宝石分材质绘制。骨骼名与动画通道全部保留，攻击/飞行逐帧预演无炸模；主臂 pivot 从体外 20 格拉回肩关节。多余六臂收掉（骨骼留空，动画照常），雕像新增 8 秒呼吸 idle（浮沉/摆衣/光环自转/轻扇翅）。`gradlew compileJava`、三项光系测试与 `gradlew build -x test` 通过，实装 JAR SHA-256 `68482C243CFCFA4D12A50695AACF8EB5292C4F058AF394EE442F14D9B092E51A`，上一版备份 `tnc-1.0.0.jar.bak-angel-models-20261105-1939`。详见[天使模型重制](docs/spells/天使模型重制-20261105.md)。本条优先于下方旧记录。
>
> **2026-11-05 15:57 光法阵统一＋施法前戏已实装**：光系所有阵统一到 `STYLE_LIGHT`（白金光阵），新增 `STYLE_LIGHT_SKY` 专供光线 t4/t5 的天阵（同一套几何、不往上长柱、改往下垂 12 片光幕）；光线落点阵也从雷法 `STYLE_STORM` 改成光阵。光耀/召唤/光龙/Apollo 全部走光阵。读条仍是「压云→点灯→过曝→释放」四段：3~7 颗小阵、2~5 枚竖环、日冕 1.16R、7 格光柱、四边收暗/暖白过曝/心跳震屏/光雨；天使落地 ~11 格粒子光柱，光线枪口光、撞墙不再冒火。光镜链与所有数值未动。光耀/召唤读条 1.8~3.8 秒；光线 t4/t5 接阵，t1~t3 保持瞬发。`LightSealStyleTest`＋`LightMirrorRulesTest`＋`LightRitualSequenceTest` 与 `gradlew build` 通过，实装 JAR SHA-256 `95287A2312E376BF54EE758B97D57E7C68003AB8857D18E5F72B09BCFE341A3E`，上一版备份 `tnc-1.0.0.jar.bak-light-seal-unify-20261105-1557`。规范见[光法阵规范](docs/spells/光法阵规范-20261105.md)，前戏见[光法施法前戏与光污染](docs/spells/光法施法前戏与光污染-20261105.md)。本条优先于下方旧记录。

> **2026-10-05 21:35 火法合并版已安装**：火球链与焚身分支、远端3c93ecc4最新火球/陨星/黑雾改动已合并；修复黑雾父类同步字段初始化。167项JUnit、235项GameTests通过，三份JAR和31项资源安装校验通过。用户最新范围：只关注新世界，不修复旧存档。实装哈希和试玩方式见[火法合并记录](docs/spells/同伙火法合并与新世界测试-20261005.md)。本条优先于下方历史状态。

> **2026-10-05 16:27 修复已实装**：生命/饱食度退回旧版，原魔力槽保留。SELF 局部464/90/292，保留UUID；音乐连续覆盖整个酒馆、地下室、厨房、二三楼及外围两格。163项JUnit、235项GameTests、合并后实际客户端207.45秒完整切歌通过。同伙远端638c8217更新已合并。安装凭据见 [本轮实装记录](docs/tavern/酒馆范围与旧HUD恢复实装记录-20261005.md)；同伙须拉取后重新编译安装JAR，操作见 [同步说明](docs/tavern/同伙同步酒馆与模组更新-20261005.md)。本条优先于下方旧版状态。

> **2026-10-05 状态条与酒馆修订已实装**：用户确认A生命槽、C森果饱食槽；原魔力HUD/贴图保持原样。入口整片十席留空，移除六人；43名其他常客保留。地下室746格独立补丁恢复楼梯与酒窖，不改原内饰hash。艾琳局部436/90/286面南，SELF465/90/292面西，五个旧世界按完成状态修正或待自动施工，新世界自动。三首BGM进门10秒随机，独占背景音乐，完整放完自动换曲，出门恢复；实测207秒自然切歌。139项JUnit、232项GameTests及有效满血/部分血HUD截图通过。双份运行JAR SHA256 EBF239250624E338F67CFB033492DCDD896A3D2FE6DEA3D63A00CCB1817D4E66。详见[本轮实装记录](docs/tavern/生命饱食度与酒馆修订实装记录-20261005.md)。本条优先于下方旧版快照。

> **2026-10-04 装修酒馆已安装（23:56）**：同伙内饰以独立局部补丁接入“新的世界”和内饰试玩存档；另外两个尚无酒馆建筑的展示存档载入后按原生成顺序自动接入，新世界同样自动。75席安排49名坐姿常客（65.3%），右键简短对话；两首用户BGM进门10秒随机一首循环，出门停止。134项单元测试、229项GameTests、实际Conquest客户端坐姿/对话/音乐验收通过。原天空岛主体资源、任务书及上一轮生态美术均保留。详见[酒馆实装记录](docs/tavern/装修酒馆与常客音乐实装记录-20261004.md)。本条优先于下方旧版本快照。

> **2026-10-04 生态美术重做已安装（19:30）**：147张原创图标、29株植物全成长模型、16种设施和7类实存饲料立体堆更新。130项单元测试、14张实际客户端渲染检查通过，资源审计147物品/723植物状态/16设施/7饲料均无失败。运行包33章、305天空岛资源、4份世界元数据不变。详见[生态美术实装记录](docs/ecology/生态美术重做实装记录-20261004.md)；源码与原图见仓库art/ecology-redesign-20261004/。

# TN-C 项目进度总览（Handoff）

> **2026-10-04 第二轮修订已安装（16:39）**：动物配色/体型/飞行、四材料3×3×3锻炉与GUI、栽培自由、70份Blockbench工程、参考六包188章的三页74节点引导均已安装。222项GameTests、130项单元测试、客户端资源与炉GUI检查通过。30个其他任务章节、玩家存档和天空岛模板保持相同。同伙远端`9108ce05`已合并，本地尚未推送。详见[本轮实际安装记录](docs/ecology/生态第二轮修订实装记录-20261004.md)。当前源码根目录为`C:/Users/宋志坤/Documents/Codex/2026-09-19/mu-q/work/TN-C_MC-magic-s-world`；运行包为`D:\垃圾桶\PCL 正式版 2.9.3\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915`。以下旧路径、日期和统计是历史快照，本条优先。

> **2026-10-04 生态实装更新**：29种植物、24种独立动物、牧养设施/魔力瓶、工坊升级及三页可选FTB指南已安装到实际PCL和仓库镜像。217/217 GameTests、109/109单元测试通过；真实开发客户端155个物品模型、608个植物状态和23个新增动物渲染器无缺失。SHA-256 `617A913541FEE6E07E87A169D1F50AAE9E3A5CA22FDF9CCFA53622BFB32F6E72`。详见 [本轮实际范围和测试入口](docs/ecology/生态全量实装与测试入口-20261004.md)。另外29个任务章节哈希一致，玩家存档/天空岛模板未改；完整281模组实景动画和平衡仍待试玩。以下旧专题段落保留各自写作时间，不覆盖这次安装记录。

> **这份文件的用途**：新会话只需读这一份，就能完全接手项目。
> 最后更新：2026-09-15（新包「魔改版」已落地，见 4.8）
> 维护约定：每次有重大进展，回来更新这里。
>
> 🔗 **别的两份事实来源**：技术现状 → `docs\当前状态.md`；
> **剧情 / 任务 / 文案 → 工作区根目录 `剧情总纲.md`**（本文只管技术，不管故事）。

---

# 一、项目是什么

**TN-C** = 一个剑与魔法主题的 Minecraft 整合包 + 自研核心 mod。

- 以 **元素觉醒 1.4.3** 为起点做魔改
- 但最终目标是**完全替换**它的魔法系统（自研魔法石体系）
- 也会替换战斗/职业系统（剑士路线走同样结构）

| 项 | 值 |
|---|---|
| Minecraft | **1.20.1** |
| 加载器 | **Forge** |
| 整合包当前版本 | 元素觉醒 **1.4.3 魔改版**（2026-09-15 打包，Forge 47.4.22，278 mods）|
| TN-C mod 工程 | Forge **47.4.13** ⚠️ 应与整合包对齐到 47.4.22 |
| mod_id | `tnc` |
| 包名 | `com.tnc.tnc` |
| 主类 | `TNMod` |
| 显示名 | TN-C |

---

# 二、路径速查表

| 东西 | 路径 |
|---|---|
| **TN-C Java mod 工程** | `D:\ModTest` |
| **整合包工作区副本**（编辑用）| `D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\` |
| **游戏包**（PCL2 实际运行）| `E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915\` |
| 旧游戏包（原版 1.4.3，未动，仍可玩）| `E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3\` |
| 新包原始压缩档 | `D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip` |
| **设计文档** | `D:\ModTest\design\TN-C-魔法系统设计.md` |
| **模型源文件** | `D:\ModTest\model-source\` |
| **Blockbench 工作目录** | `D:\blockbench\newprojet\` |
| **PCL2 启动器** | `E:\download\正式版 2.12.6.1\Plain Craft Launcher 2.exe` |
| Blockbench 程序 | `D:\blockbench\Blockbench.exe` |
| 同步脚本 | `D:\ModTest\modpack\sync.cmd` |

**其它装了车万女仆的整合包**（也装了我们的模型包）：`1.6.2`、`你好，新蒸程V1.5.9`

---

# 三、环境与工具

| 项 | 值 |
|---|---|
| JDK 17 | `C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta`（Microsoft OpenJDK 17.0.15，完整 JDK 含 javac）|
| JDK 21 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`（系统 PATH 上的）|
| Gradle toolchain 配置 | `C:\Users\FDCX\.gradle\gradle.properties` → `org.gradle.java.installations.paths` 指向上面那个 JDK 17 |
| IntelliJ 项目 | `D:\ModTest`（Gradle 项目，Project SDK 需设为 17）|
| Shell | **Windows PowerShell 5.1**（不是 pwsh！pwsh 没装）|
| 系统代码页 | **936 (GBK)** —— `.ps1` 文件必须带 **UTF-8 BOM**，否则中文乱码 |
| 脚本执行策略 | 本机**禁止直接运行 `.ps1`**（`& .\sync.ps1` 报 UnauthorizedAccess）→ 用 `sync.cmd`，或 `powershell -NoProfile -ExecutionPolicy Bypass -File <脚本>`（`sync.ps1` 已带 BOM，读中文路径没问题）|
| 文件沙箱 | `danger-full-access`，**无审批提示**（不要请求提权）|

**为什么用 PCL 自带的 JDK 17**：`api.foojay.io` 在国内连接被重置，Gradle 自动下载 toolchain 会失败。PCL 那个 JDK 17 是完整的，够用。

---

# 四、已完成的工作

## 4.1 TN-C Forge mod 工程（可用）

```
D:\ModTest\
├── build.gradle / gradle.properties / settings.gradle
├── src/main/java/com/tnc/tnc/
│   ├── TNMod.java       ← 主类（已清理 MDK 演示内容）
│   └── Config.java      ← 配置类
├── src/main/resources/
│   ├── META-INF/mods.toml
│   ├── pack.mcmeta                     (pack_format 15 = 1.20.1)
│   └── assets/tnc/
│       ├── models/item/*.json          ← 战士武器模型（重剑/锤/枪/弓/龙之剑/新武器…）
│       ├── textures/item/*.png
│       └── lang/{zh_cn,en_us}.json
└── model-source/                        ← 模型源文件与参考
```

**状态**：`gradlew build` 成功、`runClient` 能启动并加载 mod ✅

**已做的改动**：
- MDK 模板改名为 TN-C（`mod_id=tnc`、包 `com.tnc.tnc`、主类 `TNMod`）
- 删除 MDK 演示内容（`example_block` / `example_item` / `addCreative`）
- 创造模式标签页改为 `tnc:main`，名称 "TN-C"
- `build.gradle` 加了 `idea.module { downloadJavadoc = downloadSources = true }`（能跳进 MC 源码）
- `Config.java` 修掉过时 API（`new ResourceLocation(s)` → `ResourceLocation.tryParse(s)`）
- **未完成**：`mod_authors` 还是占位符

## 4.2 试作之剑（2026-11-03 已删除）

早期用来验证"自定义 3D 物品模型"的样机，官方定位一直是废案：**已彻底删除**——
注册、模型、贴图、语言条目、`infusible_weapons` 引用全部清掉，存档里若还有它只会变成空气。

- ~~3D 模型 + 贴图 + `display` 显示设置~~（文件已删）
- 唯一保留下来的是**教训**：模型平躺在 XZ 平面（Y 薄）时，必须自己写 `display`，
  不能用 `parent: item/handheld`（那是给立在 XY 平面的模型用的）；
  `gui` 旋转用 `[-90, 0, 0]` 把平躺模型立起来。
- 现在的武器一律自带完整 `display`，见 `tools/weapons/weapon_gen.py` 顶部的 `DISPLAY`。

## 4.3 songzhikun 角色模型（TLM 车万女仆）

- **24 骨骼**的精简骨架（从参考模型 80 骨骼精简）
- UV 布局已算好（无重叠，塞进 64×64）
- 已打包成 TLM 模型包 `tnc_pet`，装在 4 个整合包里
- 文件：`model-source/songzhikun-v4-packed64.json`（模型）、`songzhikun-checker.png`（验证贴图）、`songzhikun-guide64.png`（UV 参照）

**2026-09-21 新增（法术系统YYY）**：`tnc_pet:zhuangquerang`（**庄鹊让**）—— 复制现成模型 `touhou_little_maid:izayoi_sakuya`（十六夜咲夜，蓝白女仆装）。
模型清单的可复现记录在 `tools/maid_models.json`（配方）+ `tools/sync-tlm-models.ps1`（重建脚本），因为 `tlm_custom_pack/` 不进 git。
现状与选型理由见 `docs/当前状态.md` 第四节。

**TLM 1.5.0 的关键知识**（见第五节）

## 4.4 整合包工作区 + 同步工具（可用）

```
D:\ModTest\modpack\
├── README.md      ← 使用说明
├── sync.cmd       ← diff / push / pull / push -Force
├── sync.ps1
└── 元素觉醒1.4.3-魔改版-20260915\  ← 把包放这一层（当前就是它）
```

**脚本会「自动识别」整合包名字** —— 换包时只要换文件夹，不用改配置。
它同时校验游戏包是否存在：`E:\download\正式版 2.12.6.1\.minecraft\versions\<同名>\`

**只同步这些目录**：`kubejs` `config` `defaultconfigs` `local` `data` `tlm_custom_pack` `vaultpatcher` `hotai` `immersive_furniture`
**绝不动**：`mods` `saves` `logs` `backups` `xaero` `resourcepacks`

**⚠️ push 是时间戳感知的**：只覆盖"工作区比游戏包新"的文件。因为游戏运行时不停改写 `config/`，无脑 push 会覆盖掉。

**⚠️ 2026-09-14 变更**：旧的 `元素觉醒1.4.3` 工作区副本已删除，我们自己的 KubeJS 文件
**归档**到 `D:\ModTest\archive\blink-spell\`（带 README 说明）。

**⚠️ 2026-09-15 变更**：用户提供了新包 `元素觉醒1.4.3-魔改版-20260915.zip`（1.6 GB）。
已解压为新**实例**（原 `元素觉醒1.4.3` 实例完全没动），并 `sync.cmd init` 生成同名工作区。
新包**只有** `mods` `config` `defaultconfigs` `kubejs` `resourcepacks` `shaderpacks` +
版本 json/jar；**没有** `tlm_custom_pack` `vaultpatcher` `hotai` `immersive_furniture` `local` `data`。
→ 我们的女仆模型包 `tnc_pet` 和汉化补丁还只在旧实例里，要用得手动搬。详见 4.8。

## 4.5 第一个原创法术：闪现（**已跑通，已归档**）

> 📦 文件已移到 `D:\ModTest\archive\blink-spell\`，那里有完整的还原说明。


```
雷系无关，这是验证链路用的奥术法术
tnc:blink_1  距离 8 格   tier 1  冷却 6s
tnc:blink_2  距离 16 格  tier 2  冷却 9s
tnc:blink_3  距离 24 格  tier 3  冷却 12s
```

**打通的完整链路**（这是最有价值的成果）：

```
data/tnc/spells/blink_*.json              ← 法术本体（纯数据）
        ↓
kubejs/startup_scripts/item/tnc_blink_scroll.js   ← 注册卷轴物品
        ↓
kubejs/data/spellanvil/tags/items/scroll.json     ← 加入 spellanvil 的物品标签
        ↓
kubejs/server_scripts/spell/tnc_blink_bind.js     ← SpellAnvilEvents 绑定
        ↓
kubejs/client_scripts/tnc_blink_tooltip.js        ← tooltip
        ↓
法术注册台 → 法术书 → 施放 ✅
```

**全部是新增文件，没有修改作者的任何代码。**

## 4.6 元素觉醒架构分析（已完成）

**三层结构**：

```
① 引擎层（开源 mod，不能改）
   spell_engine / spell_power / spellbladenext / wizards / paladins / rogues ...

② 作者自研 mod（闭源 jar）
   ysjxspells / ysjx_weapons / ysjx_dimension / ysjxteams / ysjxmodel

③ 内容层（★ 可改主战场）
   kubejs/ (189 文件) / config/ (3582 文件) / config/openloader/ (3021 文件)
```

**作者自己的数据包在 `config/openloader/data/yuansujuexing/`**（2656 文件）—— 他用 OpenLoader 把一切都做成了可覆写的形式。这是给魔改者留的门。

## 4.7 魔法系统设计（进行中）

见 `D:\ModTest\design\TN-C-魔法系统设计.md`（当前 v0.3）

**核心设计**：
- 七元素（水/火/雷/风/土/光/暗），亲和力 1–6，天生固定
- 五等级（冒险者/精英/王/传说/神），每元素独立进度
- 魔力值上限 ← 亲和力总和 + 原版等级；魔法点数 ← 魔力上限档位
- 学习 = 卷轴（路线）+ 投魔法点数
- 不可撤回，只能靠遗忘药水重置
- 领域魔法（特殊魔法）：默认只有最高亲和力那个；任务书道具可解锁多个
- 融合：临时，领域范围内触发，评级向下取

**⚠️ 重要决策**：`simplyskills` **废弃并移除**（用户 2026-09-14 决定）→ **2026-09-15 已在新包里完成并核实**（见 4.8）

## 4.8 新包「元素觉醒1.4.3-魔改版-20260915」（2026-09-15 已落地并核实）

**来源**：`D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip`（1.6 GB / 8399 条目）。
包里带一份合作者写的 **`README-给合作者.txt`** —— 它的每一条改动我都对着实际文件核过了，**全部属实**。

**落地链路**（本次完成）：

```
D:\ModTest\元素觉醒1.4.3-魔改版-20260915.zip        ← 原始压缩包（保留，未删）
   ↓ 解压
E:\...\versions\元素觉醒1.4.3-魔改版-20260915\      ← 新实例（8393 文件 / 1.6 GB）
   ↓ sync.cmd init
D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\   ← 工作区（3768 文件 / 46.6 MB）
```
`sync.cmd diff` 结果：**In sync.** ✅

**⚠️ 解压后必须做的改名（PCL 的硬要求）**：压缩包里的文件名是 `元素觉醒1.4.3.json` /
`元素觉醒1.4.3.jar`，但 PCL 要求 **文件夹名 = JSON 名 = jar 名**，且 JSON 里的 `id` 也要一致。
所以已改为：
- `元素觉醒1.4.3.json` → `元素觉醒1.4.3-魔改版-20260915.json`（并把 `"id"` 改成同名）
- `元素觉醒1.4.3.jar`  → `元素觉醒1.4.3-魔改版-20260915.jar`

改名前后的 json/jar 与旧实例的**完全一致**（原版 1.20.1 客户端 jar + Forge 47.4.22 清单，
117 个 library，`assetIndex` = `5`，`assets\indexes\5.json` 已存在）。**内容变的是 mods/config/kubejs。**

**本版相对原版 1.4.3 的改动**（合作者所述 → 我的核实结果）：

| 改动 | 内容 | 核实 |
|---|---|---|
| **移除 Simply Skills** | 删 `simplyskills-1.7.2` + `simplyskills-tree-bridge-1.0.0` 两个 jar、`config\simplyskills\`、`config\simplyskills_tree_bridge.json`、openloader 里的技能树与资源 | ✅ 都不在了 |
| 修补 `Spell_Effectss.js` | 删 2 处失效引用（L63 `simplyskills:marksmanship`、L90 `simplyskills:magic_circle`），原值留注释 | ✅ 注释在 |
| **新增原创「章国源之矢」** | 6 个 kubejs 文件 + `wizards\spells\zhangguoyuan_bolt.json` + spell_assignment + spellanvil 的 scroll 标签 + 2 张贴图 + 物品贴图（速度 0.15、伤害系数 200）| ✅ 全在 |
| **新增 MCA Reborn 7.6.26** | NPC 关系 / 结婚 / 生子 / 家族树；前置 architectury 9.2.14（包内已有）| ✅ 在，⚠️ **只装未测** |
| **新增 Bountiful 6.0.4** | 委托板悬赏；前置 Kambrik 6.1.1 + kotlinforforge 4.12.0（包内已有）| ✅ 在，⚠️ **只装未测** |
| 保留 puffish 框架 | `puffish_skills-0.18.3` / `puffish_attributes-0.8.2` 未动 | ✅ 在 |

**代价**：ELA 原本那两棵技能树（ascendancy / tree）随 Simply Skills 一起没了，
**puffish 技能界面现在是空的 —— 这是预期状态**。另外被删的 `magic_circle` 原本给全部
8 种 spell_power +0.3 倍率，若发现某些玩法强度掉了，原因就在这。

**新包缺的目录**（旧实例有、新实例没有）：`tlm_custom_pack`（1266 文件，我们的 `tnc_pet`
女仆模型包就在这）、`vaultpatcher`（158 文件，汉化）、`hotai`、`immersive_furniture`、`local`、
`data`；另外 `mods\.connector`（信雅互联缓存）被有意剔除，**首次启动会自动重建，会比较慢，属正常**。

**✅ 2026-09-15 已从旧实例搬入新实例**（用户选定的部分，搬完工作区已 `pull` 同步，diff = In sync）：

| 搬的东西 | 数量 | 状态 |
|---|---|---|
| `tlm_custom_pack\`（含我们的 `tnc_pet` 女仆模型包）| 1266 文件 / 37.4 MB | ✅ 逐文件核对，与旧实例完全一致 |
| `vaultpatcher\`（汉化补丁）| 158 文件 / 0.8 MB | ✅ |
| 闪现链路的 7 个文件 | 7 文件 | ✅ 按 `archive\blink-spell\README.md` 重放（见下） |

仍**没搬**（用户本次没选）：`hotai`、`immersive_furniture`、`local`、`data`。

**🧪 首次启动实测（2026-09-15 21:05，失败 —— 但原因不在包）**：

- ✅ 游戏**正常进到主菜单**，KubeJS / 资源 / 数据包全部加载完成
- ✅ 整份 `latest.log`（13348 行）**只有一种错误：内存溢出**，没有任何 mod 报错、没有 Mixin 失败
- ❌ 用户建新世界「新的世界」，`准备生成区域中` 走到 54%，**21:09:40 爆 `java.lang.OutOfMemoryError: Java heap space`**，
  随后几万条 OOM，卡在 58% 约 15 分钟，直到 21:24 手动关闭
- **根因**：PCL 这次只给了 **3174 MB** 堆（见 8.3），跟魔改版的内容无关
- 残留：半成品存档 `saves\新的世界\`（405 文件 / 693 MB）→ **已于 2026-09-15 删除**，`saves\` 现在是空的
- 这一轮运行还让实例自己生成了 `data\`、`local\`、`config\mca.json`（MCA 的配置），
  并把大量 `config\*.toml` 与 `vaultpatcher\cache\*.class` 改了时间戳
  → 工作区 `diff` 会显示 425 处 `pack-newer`，**属正常**（push 是时间戳感知的，不会覆盖游戏写的新配置）

**我们的 KubeJS 成果原来不在新包里**（下面是**重放前**的新旧 kubejs 逐文件对比）：

```
只在旧实例 ：client_scripts\tnc_blink_tooltip.js
             data\spellanvil\tags\items\scroll.json       ← 我们加的卷轴标签
             data\tnc\spells\blink_{1,2,3}.json
             server_scripts\spell\tnc_blink_bind.js
             startup_scripts\item\tnc_blink_scroll.js
只在新包   ：assets\kubejs\textures\item\zhangguoyuan.png
             client_scripts\zhangguoyuan_tooltip.js
             server_scripts\shaped\zhangguoyuan.js
             server_scripts\spell\zhangguoyuan_bolt_scroll.js
             startup_scripts\item\zhangguoyuan.js / zhangguoyuan_bolt_scroll.js
```
闪现的**完整还原说明**仍在 `D:\ModTest\archive\blink-spell\`（照它重放即可）。

**✅ 上面「只在旧实例」那 7 个文件已于 2026-09-15 重放回新包**。
关键点：我们的 `kubejs\data\spellanvil\tags\items\scroll.json` 是 `"replace": false`，
而合作者把他的 `zhangguoyuan_bolt_scroll` 加在 **openloader 数据包**的
`config\openloader\data\yuansujuexing\data\spellanvil\tags\items\scroll.json`（同样是 `replace: false`）
—— **两个数据包的同名标签会合并，互不覆盖**，已实测确认合作者那条没被动过。

## 4.9 作者 NPC / 任务系统的“冲突”排查（2026-09-15）

> 用户反馈：新包进游戏后「原作者的 NPC 跟任务都消失了」。逐条查完的结论：
> **不是 MCA 干的，也不是魔改版删了作者的内容；MCA 在物理上换不掉作者的 NPC。**

**作者的 NPC 系统是什么**（`ysjxmodel-1.0.0.jar`，作者自研闭源）：
- 4 个实体类 `YileinaEntity` / `JiuhuEntity` / `TbsCharacterEntity` / `PlayerSkinNpcEntity`
- `javap` 实测：**全部 `extends net.minecraft.world.entity.PathfinderMob`**（不是 `Villager`！），
  并实现 `com.p1nero.dialog_lib.api.entity.custom.IEntityNpc` + GeckoLib `GeoEntity`
- 名单：伊蕾娜 / 酒狐×6 / 迷迭香 / 霜星 / 秦亦禾 / 作者 sh1ntaro / 守卫·布兰特 /
  七星 / 五大商人（武器·材料·珠宝·符文·烧烤·农业）等
- 任务 = `whisperingquests` 3.2 + `p1nero_dl` 2.3.0 对话库 + FTB Quests 16 个章节
  （`config\whisperingquests\ftbq_bindings.json` 把 `ysjxmodel:main/xxx` 绑到 FTB 任务 ID）

**关键证据链**：

| # | 查到什么 | 说明 |
|---|---|---|
| 1 | MCA 配置 `overwriteOriginalVillagers: true` + `fractionOfVanillaVillages: 0.0` | MCA **只替换原版村民**，作者的 NPC 不是村民 → 换不掉 |
| 2 | 作者 NPC 全在 | 比对时新旧实例 mods 都是 278（现新实例已 279，因为装回了 TLM），作者 mod 一个不少 |
| 3 | 作者把 5 个维度做成了世界模板 | `ysjx_dimension-1.0.0.jar` 里有 `world_templates/{magic_association,abandoned_city,undead_fortress,sacred_arena,lueduodushi}/`，共 **358 个 `.mca`** |
| 4 | 解压新世界大厅实体区块实测 | 新世界大厅里**确实存在 14 种作者 NPC 实体**（伊蕾娜、迷迭香、霜星、各大商人…），不是“全没了” |
| 5 | 对照作者模板 = 21 种 | 新世界比模板/老世界**少 7 种**：守卫·布兰特、炼金术师·七星、珠宝商、烧烤师傅、农业商人、巫女酒狐、传教者 dt（多为商店/守卫类） |
| 6 | 两版都有的作者遗留 bug | 数据包 `tags/functions/{load,tick}.json` 引用 `yuansujuexing:welcome/load`、`welcome/tick`，但**全包（含所有 jar）找不到这两个 `.mcfunction`** |
| 7 | 真·mixin 冲突（新旧版都有） | `ysjxspells` 的 `LegendaryObliteratorDamageCapMixin` 对 `legendary_monsters` 的 TheObliterator 应用失败（`@Shadow getAttackState` 找不到）→ 那个伤害上限静默失效 |

**⚠️ 合作者 README 没写的两处改动**（mods 目录逐文件比对，当时 278 vs 278）：

```
移除： touhoulittlemaid-1.5.2-forge+mc1.20.1.jar   ← 车万女仆！README 只提了 simplyskills
       simplyskills-1.7.2 + simplyskills-tree-bridge-1.0.0   （这个 README 写了）
新增： minecraft-comes-alive-7.6.26（MCA）、Bountiful-6.0.4   （README 写了）
       [自定义局域网联机] lanserverproperties-1.11.1         （README 没写）
```
→ **TLM 原本不在新包里**，当时我们搬进去的 `tlm_custom_pack\tnc_pet` 女仆模型包没有宿主 mod。

**✅ 2026-09-15 已装回 TLM**：从旧实例拷 `mods\touhoulittlemaid-1.5.2-forge+mc1.20.1.jar`
（23.27 MB，MD5 一致），新实例 mods **278 → 279** 个 jar。
TLM 1.5.2 只要求 Forge≥46 + MC 1.20~1.20.2（不内嵌 GeckoLib），包内已有 `geckolib-forge-1.20.1-4.8.4`。
TLM 的 `config\touhou_little_maid*` 没搬（首次启动自动重建），
模型包清单 `tlm_custom_pack\tnc_pet\assets\tnc_pet\maid_model.json`（songzhikun ×2）已在位。
⚠️ **`mods` 目录不参与 sync**，这类改动只存在于游戏包。

**真正被 MCA 影响的东西**：依赖**原版村民**的内容 —— 村庄拼图/村民交易
（日志里 MCA 的 `Missing trade item listings for ... IafVillagerRegistry / LNEVillagerTrades` 警告）。
想恢复原版村民：把 `config\mca.json` 的 `overwriteOriginalVillagers` 改成 `false`
（或用 `moddedVillagerWhitelist` 白名单），重启即可。

**仍未解释**：那 7 种 NPC 为什么在新世界大厅里没有。可能是「实体数据只保存了已加载区块」
（模板 4 个实体区块文件，新老世界都只有 4 个但内容不同）。**待验证方法**：进游戏把大厅整个逛一圈，
再解压实体区块复扫一次。

## 4.10 雷系五级法术「小闪电 / 雷场 / 雷击 / 雷暴 / 天打五雷轰」（2026-09-15 首批完成）

**纯数据 + KubeJS，没写一行 Java** —— 走的还是闪现验证过的那条链路（`archive/blink-spell/`）。
详细数值表、文件清单、验证步骤写在设计文档 **`design\TN-C-魔法系统设计.md` 第十五节**，这里只记要点。

| 级 | 名称 | 法术 id | 形态 | 伤害系数 | 冷却 |
|---|---|---|---|---|---|
| 1 | 小闪电 | `tnc:spark` | 光标单体 | 0.6 | 3s |
| 2 | 雷场 | `tnc:lightning_field` | 自身领域（半径 4，6s）| 0.35/跳 | 14s |
| 3 | 雷击 | `tnc:lightning_strike` | 单体重击 + 2.5 格溅射 | 1.4 | 8s |
| 4 | 雷暴 | `tnc:lightning_storm` | 自身风暴（半径 8，10s）| 0.7/跳 | 24s |
| 5 | 天打五雷轰 | `tnc:heavenly_thunder` | 5 发天雷落地 + 6 格溅射 | 2.2×5 | 45s |

**新增文件**（工作区）：`kubejs\data\tnc\spells\*.json`（5 个）、
`kubejs\startup_scripts\item\tnc_lightning_scrolls.js`、`kubejs\server_scripts\spell\tnc_lightning_bind.js`、
`kubejs\client_scripts\tnc_lightning_tooltip.js`、`config\openloader\resources\TN-C\`（我们的资源包 + 5 个 16×16 法术图标）。
卷轴 id = `kubejs:tnc_<名>_scroll`，已加进 `data\spellanvil\tags\items\scroll.json`。

**本轮学到的 SpellEngine 硬知识**（写在这里省得下次再扒 jar）：

- `release.target.type` 只有 8 种：`AREA` `BEAM` `CURSOR` `SELF` `PROJECTILE` `METEOR` `CLOUD` `SHOOT_ARROW`
- **`CLOUD` = 持续领域**（`radius` + `impact_tick_interval` + `time_to_live_seconds`），
  样例都是 `range: 0` → **以施法者为中心**，每 N tick 重放一次 `impact`。雷场/雷暴就是靠它做的
- **`METEOR` = 从天上砸下来**（`launch_height` + `launch_properties.extra_launch_count` 可一次多发），
  天打五雷轰 = `extra_launch_count: 4`（共 5 发），投射物模型借用了包里的 `berserker_rpg:projectile/lightning_bolt`
- `impact[].action.type` 只有 6 种：`DAMAGE` `HEAL` `STATUS_EFFECT` `FIRE` `SPAWN` `TELEPORT`
- `cost` 字段：`exhaust`（饥饿）/`item_id`+`consume_item`（符文材料）/`durability`/`cooldown_duration`/
  `cooldown_haste_affected`。**本包 `config\spell_engine\client.json5` 已经配好了「数字键 1–9 施法」**
  （`spellHotbar_2_defer: HOTBAR_KEY_2`…）—— 设计文档的阶段 5 其实是现成能力
- 可用素材：法术动画 22 个（雷系适合 `one_handed_sky_charge`）、粒子 30 个
  （`electric_arc_a/b`、`white_spark_mini`、`smoke_medium`…）、音效 18 个（`generic_lightning_casting/release`）
- **法术图标** = `assets\<命名空间>\textures\spell\<法术名>.png`，**16×16**；
  投射物贴图是 `textures\spell_projectile\`，32×32

**图标流水线**：`D:\ModTest\tools\spell_icon_gen.ps1`（ASCII 掩码 → PNG，
自带尺寸校验 + 8 倍放大预览图 `design\refs\icon-preview.png`，用 `read_image` 看效果再改掩码）。
⚠️ 该脚本是**纯 ASCII**（整合包目录自动识别），所以**不需要 BOM** —— 这是踩过 BOM 坑之后的写法。

**法术体检工具**：`D:\ModTest\tools\verify_spells.ps1`（**写新法术前后都跑一遍**）。
它把我们的法术和**包里 158 个既有法术**对照，检查：
① 用到的字段名/枚举值是否被既有法术证明过（防拼错、防臆造）；
② 交付链路是否完整：法术 json → 卷轴物品 → spellanvil 标签 → 注册台绑定 → tooltip → 16×16 图标。
2026-09-15 跑的结果：**雷系 5 个全部 `y/y/y/y/y`，0 问题**；闪现 3 个标为 `legacy`（旧命名约定，缺图标）。
另外确认过 `config\openloader\advanced_options.json` 里 `resourcePacks.enabled = true` 且
`additionalFolders` 为空 → OpenLoader 确实会加载 `config\openloader\resources\TN-C\`，我们的图标会被读到。

**✅ 已核对**：工作区 15 个文件与游戏包**逐字节一致**（push 之后做的哈希比对）。

**还没做**：卷轴的获取途径（现在只能 `/give`）、雷系领域魔法、以及把 `cost` 换成 TN-C 自己的魔力值。

> ⚠️ **重要认知修正（2026-09-15，用户指出）**：卷轴 + 法术注册台是 TN-C <b>要替换掉</b>的旧体系。
> 上面那套只当**临时测试入口**用，等魔法石的解锁流程能用了就**删掉**（`kubejs/data/tnc/spells/*.json`
> 这 5 个法术数据文件要保留 —— 它们是引擎层的法术本体，魔法石解锁后施放的就是它们）。

## 4.11 魔法石数据层（Java，2026-09-15 完成并已装进游戏）

按设计文档**阶段 1** 写的第一块 Java 代码。**没有 GUI、没有解锁界面、没有施法** —— 纯数据 + 调试命令。
设计文档第十六节有完整的字段表/数值表/验证步骤，这里记要点和踩坑。

**TN-C mod 已经进游戏了**：`build\libs\tnc-1.0.0.jar` → 整合包 `mods\`，**mods 279 → 280 个 jar** ✅
（工程还是 Forge **47.4.13**，整合包是 **47.4.22** —— 47.x 同线二进制兼容，实测能加载；
本地 Gradle 缓存里只有 47.4.13，对齐要联网下载，所以**先不对齐**，等真出问题再说。）

新增 Java 文件（`D:\ModTest\src\main\java\com\tnc\tnc\`）：

```
Config.java                       ← 全部数值配置（替换了 MDK 演示配置）
magic\Element.java                ← 七大元素枚举 + 五等级名称
magic\MagicStoneData.java         ← ★ 核心数据：亲和力[7]/魔力/上限/点数/进度/已学 + NBT
magic\MagicStone.java             ← Capability 挂载·复制·登录·tick + Provider
network\MagicStoneNetwork.java    ← 网络通道 + 整包同步
network\MagicStoneClientSync.java ← 客户端写回（单独类，避免服务器加载 MC 客户端类）
command\MagicStoneCommand.java    ← /tnc 调试命令
```

**🔴 本轮踩的坑（值得记住）**：
1. **`@Mod.EventBusSubscriber` 漏了就静默失效** —— `MagicStone` 类里挂着"挂载/重生/登录/tick"事件，
   我第一版忘了给类加注解，编译照样通过、但事件永远不会触发（capability 根本挂不上，
   `/tnc magic` 只会说"拿不到数据"）。**编译通过 ≠ 会生效。**
2. **`new ResourceLocation(ns, path)` 在这个 Forge 版本被标记为"待删除"**（只是警告不报错），
   正确写法是 `ResourceLocation.fromNamespaceAndPath(ns, path)`。
3. **lambda 里不能抛受检异常** —— 命令的 lambda 调了 `player(ctx)`（会抛 `CommandSyntaxException`），
   改成把 `ServerPlayer` 作为参数传进 lambda。

**调试命令**（权限 2，单人开作弊）：`/tnc magic`、`/tnc mana set|add|full`、
`/tnc affinity set <元素> <1-6>|roll`、`/tnc progress set <元素> <0-5>`、`/tnc learn|forget <法术 id>`、`/tnc reset`。

**数值全在 `config/tnc-common.toml`**（默认：亲和力系数 10、每级 +10、默认亲和力 3、
点数档位 100/300/500/800/1200、解锁花费 1/2/3/4/5、每秒回蓝 1）。

**验证步骤**：进游戏 → `/tnc magic` → 期望看到七元素亲和力全 3、魔力 210/210、可用点数 1
（210 只跨过 100 这一档）→ 升级看上限每级 +10 → 退出重进确认数据还在。

**下一批（阶段 3）**：魔法石 GUI（物品栏中间入口 + 亲和力/点数/已学/可解锁）+
真正的"花点数解锁法术"校验流程。

## 4.12 魔法石 GUI 第一版（Java，2026-09-15 完成并已装进游戏）

阶段 3 的第一版：**能看数据、能花魔法点数解锁法术**。详细说明见设计文档第十七节。

| 新增文件 | 作用 |
|---|---|
| `client\MagicStoneButton.java` | 物品栏里那颗菱形石头（`AbstractWidget` + `GuiGraphics.fill` 手绘） |
| `client\MagicStoneScreen.java` | 魔法石界面本体（336×224 面板：左列七元素、右列法术目录） |
| `client\MagicStoneClientEvents.java` | 用 `ScreenEvent.Init.Post` 把石头塞进 `InventoryScreen` |
| `magic\SpellCatalog.java` | 法术目录（雷系 5 个，硬编码，以后改数据驱动） |
| `magic\MagicStoneLearning.java` | ★ 四条门槛的解锁判定（命令与 GUI **共用同一份**） |
| `network\MagicStoneActionPacket.java` | 客户端 → 服务端：`REQUEST_SYNC` / `UNLOCK` |

**🔑 重要决策：入口没用 Mixin。** 设计文档原写「Mixin `InventoryScreen`」，
实际改用 Forge 的 `ScreenEvent.Init.Post` + 自绘控件 —— 不用配 mixin/refmap，
也不会和包里那堆界面 mod（FancyMenu / ModernUI / HUD）抢屏幕。

**🔴 本轮又踩一个坑（自己代码里的死循环）**：界面 `init()` 里请求同步 → 服务端回包 →
界面 `rebuildWidgets()` → 又 `init()` → 又请求同步…… 无限发包。
用 `syncRequested` 标志位保证只请求一次。**"拉数据 → 刷新界面"这条链一定要防自激。**

**⚠️ Forge 版本已对齐到 47.4.22**（本地 Gradle 缓存里也有 mapped jar 了）。
上一轮说"先不对齐"是在缓存缺失时的权宜；这一轮联网补上并重建通过。

**验收步骤**：进游戏 → 按 E → 点玩家模型右边那颗紫色菱形石头 → 期望看到
亲和力全 3 / 魔力 210/210 / 点数 1 可用、「小闪电」可解锁其余显示"需先学上一级" →
点「解锁 1点」→ 点数归零、已学变 1。命令行等价：`/tnc spells`、`/tnc learn tnc:spark`。

**还没做**：石头的贴图/模型（现在纯色块）、法术列表滚动分页、
**解锁后怎么施放**（路线还没定）、亲和力分配规则（一律全 3）、遗忘药水。

## 4.13 运行时验证 + 「解锁后怎么施放」的路线结论（2026-09-15）

### ✅ mod 真的能在运行时加载（objective ① 的"进游戏确认"）

**服务端**（`gradlew runServer`，`run\` 目录里先补了 `eula.txt` + 一份轻量 `server.properties`）：

```
[modloading-worker-0/INFO] [com.tnc.tnc.TNMod]: TN-C common setup complete, 1 item(s) registered
[modloading-worker-0/INFO] [com.tnc.tnc.TNMod]: TN-C magic stone data layer ready (...)
[Server thread/INFO] [DedicatedServer]: Done (4.187s)! For help, type "help"
```
外加 `run\config\tnc-common.toml`（937 字节）自动生成 → **注册、配置、命令都没问题**。

**客户端**（`gradlew runClient`）：
```
[Worker-Main-5/INFO] [com.tnc.tnc.TNMod]: TN-C client setup, player = Dev
[Render thread/INFO] [Minecraft]: Setting user: Dev
[Render thread/INFO] [SoundEngine]: Sound engine started      ← 到主菜单
```
无异常、无类加载错误 → **客户端类（GUI/事件订阅）也能正常加载**。

**魔法石自检**（`MagicStoneSelfTest`，服务器启动自动跑 + `/tnc selftest`）：
```
TN-C magic stone self-test [server start]: 14/14 passed
  [ok] capability attached : MagicStoneData present on [Minecraft]
  [ok] affinity sum = 21 / max mana = 210 @level 0 / = 310 @level 10
  [ok] points total = 2 @310
  [ok] cannot skip to tier2 : OUT_OF_ORDER
  [ok] tier4 blocked by affinity : maxTier=3 -> AFFINITY_TOO_LOW
  [ok] unlock tier1 works : result=OK learned=true spent=1 progress=1
  [ok] NBT round-trip : affinity=21 maxMana=310 learned=1
```
→ **capability 真的挂得上、数值算法真的按预期跑、NBT 真的存得进读得出**。

### 🔴 决定性技术约束：**Java 不能直接调 SpellEngine**

`spell_engine` 是 **Fabric mod**，jar 里是 **intermediary 名字**（`javap` 看到的是
`net.minecraft.class_1657` 这种），它靠 Sinytra Connector 才跑在 Forge 上。
我们的 Forge mod 用 mojmap 编译 —— **两边在编译期就对不上**，所以：

> **所有和法术引擎的交互只能走 KubeJS / 数据包**（作者全用 KubeJS 就是这个原因）。
> `net.spell_engine.api.event.CombatEvents.SPELL_CAST` 这类"公开 API"看着能用，
> 实际从 Forge Java 侧调不通；而且它是**观察者事件（不可取消）**，
> 结构是 `caster / spell / targets / action(CHANNEL|RELEASE) / progress`。

### 🔑 关键发现：法术池是纯数据，且可被数据包覆盖

法术书能装哪些法术由 **spell pool** 决定，而它是纯 json：

```
ysjx_weapons-1.0.0.jar → data/ysjx_weapons/spell_pools/lightning.json
{ "spell_ids": [ 5 个 ysjxspells: 雷法 ] }
```

我们已经在 `kubejs\data\ysjx_weapons\spell_pools\lightning.json` **覆盖**它，
把 tnc 的 5 个雷法加进去（**并集 10 个**，不能只写自己的 —— 这个格式是整体替换，不是合并）。
→ 这样拿一本 `ysjx_weapons:lightning_spell_book` 就能走引擎原有流程学到/施放我们的法术。

⚠️ **覆盖语义要分清**：**tags 是跨数据包合并的**（所以我们的 `spellanvil/tags/items/scroll.json`
和作者的能共存），而 **spell_pools / spells 这种普通数据文件是整体替换**（优先级高的包赢）。

### 三条路线（"解锁后怎么施放"）

| 路线 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 数据+引擎优先**（已做）| 法术进池 → 玩家拿雷系法术书 → 引擎的绑定/施法流程（数字键已配好）| **零 Java、立刻能放** | 入口还是引擎那套（绑定台/青金石）|
| **B. 魔法石 GUI 当唯一入口**（✅ 2026-09-15 已实现）| 解锁成功 → **Java 直接把法术写进玩家的法术书** | 玩家彻底见不到卷轴/绑定台 | 魔力仍只能事后扣（见下）|
| **C. 自研施法**（设计文档阶段 5 终局）| 自己做法杖 + 自己判魔力/冷却/已学，再执行法术 | **唯一能"没魔力就放不出来"** | 最费工 |

### ⚠️ 上面那条"Java 不能调引擎"的结论要**修正**

原版 `spell_engine` jar 确实是 Fabric/intermediary 名字、Forge 侧编译不了。**但是**：
整合包 `mods\.connector\` 里躺着 Sinytra Connector **重映射过**的版本 ——

```
mods\.connector\spell_engine-0.15.12+1.20.1_mapped_srg_1.20.1.jar
mods\.connector\spell_power-0.12.0+1.20.1_mapped_srg_1.20.1.jar
```

它里面的签名已经是正常的 MC 类名，**我们的 Forge mod 可以直接编译调用**。做法：

```
D:\ModTest\libs\            ← 把上面两个 jar 拷进来
build.gradle                ← compileOnly files('libs/spell_engine-...jar', 'libs/spell_power-...jar')
```

**关键**：dev 环境（runServer）没有引擎，所以所有碰引擎类的代码集中在
`magic\compat\SpellEngineBridge`，严格执行
`ModList.get().isLoaded("spell_engine")` 判断 → 真代码全在内层 `Impl` 类里，
没引擎时那些指令根本不会执行到，JVM 也就不会去解析那些类。
**已验证**：带上这个桥接类后，dev 服务器照样启动、零类加载错误、自检仍 14/14。

### 路线 B 的实现（`SpellEngineBridge`）

用引擎自己的 API 把法术写进法术书：

```java
SpellContainerHelper.containerFromItemStack(stack)   // 读出物品里的法术容器
SpellContainerHelper.contains(container, spell)       // 已经会了？
SpellContainerHelper.getPool(container).spellIds()    // 池子认不认这个法术
SpellContainerHelper.addSpell(spell, container)       // 加进去
SpellContainerHelper.addContainerToItemStack(container, stack)  // 写回物品
```

逻辑：遍历背包找**池子里认这个法术**的法术书 → 写进去；
没有的话发一本 `ysjx_weapons:lightning_spell_book` 并把法术写进去（法术位上限 8）。
解锁成功会同时给两条消息：点数扣了 + 法术书写好了（或者为什么没写成）。

**结论：先 A（验证 5 个雷法数据在游戏里真能打）→ B 已落地 → C 留阶段 5。**
魔力值在 A/B 阶段只能"施法后记账"（`CombatEvents.SPELL_CAST` 是观察者事件、不可取消），
真正的硬门槛要等 C；不过 B 之后 C 也变容易了 —— 引擎 API 已经能直接调。

## 4.14 魔力值参与施法（2026-09-15，第一版）

**做了什么**：

| 项 | 内容 |
|---|---|
| 魔力消耗表 | `Config.manaCostPerTier` 默认 **20 / 40 / 60 / 100 / 160**（设计文档给的基准是"火球耗 50 魔力"）|
| 施法扣魔力 | 接引擎的 `CombatEvents.SPELL_CAST`，**只处理 `SpellCatalog` 里的 TN-C 法术**，服务端权威 |
| 起手警告 | `action == CHANNEL` 且魔力不足 → 动作栏提示"现在收手还来得及"（只在进度刚开始时提示一次）|
| 真正扣费 | `action == RELEASE` → 扣魔力 + 动作栏显示 `魔力 -20 → 190/210` |
| 不足时 | 魔力清零 + **力竭（虚弱 3 秒）** + 聊天栏说明差多少 |
| 界面 | 法术 tooltip 现在显示「解锁消耗 N 点 / 施放消耗 M 魔力」|
| 自检 | 从 14 项加到 **18 项**（新增：消耗表递增、`spendMana` 够就扣不够就拒绝、恢复量随上限增长、每级都放得起）|

### 魔力恢复模型（2026-09-15 调整）

原来只有固定 `manaRegenPerSecond = 1`：神级法术要 160 魔力 → **要回 160 秒**，等于放不出来。
现在改成 **固定值 + 上限百分比**：

```
每秒恢复 = manaRegenPerSecond + floor(魔力上限 × manaRegenPercentPerSecond%)
默认：1 + 5%  →  上限 210 时 = 11/秒
```

| 法术 | 消耗 | 回一发要多久（11/秒）|
|---|---|---|
| 小闪电 | 20 | ~2 秒 |
| 雷场 | 40 | ~4 秒 |
| 雷击 | 60 | ~6 秒 |
| 雷暴 | 100 | ~9 秒 |
| 天打五雷轰 | 160 | ~15 秒 |
| 空蓝回满 | 210 | ~19 秒 |

这样高等级角色（魔力上限高）的续航自动跟得上高等级法术，不用为每个等级单独调数值。
两个参数都在配置里，想回到"固定 1/秒"把百分比设 0 即可。

代码：`magic\compat\SpellEngineManaHook`（同样分内外两层，没引擎就安静跳过 —— dev 服务器实测输出
`TN-C: SpellEngine not present -> mana hook not registered`）。

**⚠️ 现阶段的诚实边界：这是"事后扣"，不是硬拦截。**
引擎的 `CombatEvents.ItemUse` 只有 `START/TICK/END`、没有 cancel，`SPELL_CAST` 也是观察者 ——
所以魔力不足时**拦不住那一发**，只能扣光魔力 + 力竭惩罚。

**真拦截（"没魔力放不出来"）的可行方案已经查清**，下一步二选一：

1. **Mixin `SpellHelper.attemptCasting`**（推荐）：这个方法是引擎自己的施法前判定
   （返回 `SpellCast$Attempt`，有 `success/failMissingItem/failOnCooldown/none` 工厂）。
   注入后，"是我们的法术且魔力不足"就返回失败 → **引擎自己就拒绝施法**，
   连它自己的报错提示（`showSpellCastErrors`）都能复用。目标类/方法名都是引擎自己的
   （`net.spell_engine.internals.SpellHelper.attemptCasting`），不受 Connector 重映射影响。
   代价：工程要加 mixin 配置 + refmap。
2. **自研施法入口**：自己做法杖，先查魔力再调引擎的
   `SpellHelper.startCasting / performSpell / imposeCooldown`（这几个都是公开的）。
   完全自主，但要自己处理 client/server 施法同步（引擎有 `SpellCastSyncHelper`），工作量大。

## 4.15 魔力硬拦截「没魔力放不出来」（2026-09-15 实现，待整合包内验证）

**做法**：Mixin 注入引擎自己的施法前判定 `SpellHelper.attemptCasting`（**两个重载都注入**），
在 HEAD 处取消并返回 `SpellCast$Attempt.none()` → 引擎自己放弃这次施法。

```
src/main/java/com/tnc/tnc/mixin/SpellHelperManaGateMixin.java   ← 薄适配器（只调 ManaGate）
src/main/java/com/tnc/tnc/mixin/TncMixinPlugin.java             ← 没装引擎就不注入
src/main/java/com/tnc/tnc/magic/ManaGate.java                   ← 纯逻辑：服务端 + 我们的法术 + 魔力不足 → 拦
src/main/resources/tnc.mixins.json                              ← mixin 配置（plugin / JAVA_17 / required:false）
build.gradle                                                    ← jar 清单加 MixinConfigs
```

几个关键设计点：

- **两个重载都注入不会重复拦**：4 参版本内部会调 3 参版本，而在 4 参 HEAD 取消之后，
  3 参那次调用根本不会发生 —— 天然不会重复提示。
- `remap = false`：目标类/方法都是**引擎自己的名字**（不是 MC 的），不需要 refmap，
  这也是它在 Connector 环境下安全的原因。
- **`TncMixinPlugin` 是必需的**：目标是引擎的类，dev 环境没有引擎，硬上会报
  "target class not found"。插件里 `ModList.get().isLoaded("spell_engine")` 为假就跳过注入。
  ✅ 实测：带 Mixin 后 dev 服务器照常启动、自检仍 16/16、无任何 mixin 报错。
- **`/tnc engine` 诊断命令**：打印「引擎已加载 / 硬拦截 Mixin 已生效 / 已拦截施法 N 次（最近：…）」。
  Mixin 的静态块会置 `ManaGate.markMixinApplied()` —— **这是游戏里判断"注入到底成没成"的唯一可靠办法**
  （引擎在但 Mixin 没生效的话，施法仍会扣魔力，但拦不住魔力不足的那一发）。
  **拦截计数器比"注入成功"更硬**：真拦过才 +1，一直是 0 就说明拦截没生效。

**🔧 体检工具升级**（`tools\verify_mod_jar.ps1`）新增三项，专防"静默失效"：
1. `tnc.mixins.json` 是否打进 jar
2. **jar 清单里有没有 `MixinConfigs`**（漏了 = 所有 Mixin 都不会加载）
3. **把 Mixin 里的方法描述符拿去和真引擎 jar 的 javap 输出逐一比对**
   （差一个字符 = 注入静默失败）—— 现在两条 `attemptCasting` 描述符都 ✅ 匹配

> 顺带一提：第 3 项刚写出来时报了两个 PROBLEM，查下来是**我的检查写错了**
> （`javap -s` 输出的是 `descriptor: (...)`，不含方法名），Mixin 本身是对的。

### 4.15.1 拦截判定升级：魔法石成为唯一入口（2026-09-15）

原来只拦"魔力不足"。现在**先看有没有解锁，再看魔力** —— 没在魔法石里解锁的法术，
即使书里有（卷轴时代绑进去的）也放不出来：

```java
public static Decision evaluate(MagicStoneData data, Entry entry, boolean requireLearned) {
    if (requireLearned && !data.hasLearned(entry.id())) return NOT_LEARNED;   // 先查解锁
    if (data.getMana() < entry.manaCost())               return NOT_ENOUGH_MANA;
    return ALLOW;
}
```

- 配置项 `requireLearnedToCast`（默认 **true**）—— 这就是"取代卷轴与法术注册台"的强制手段；
  调试时设 false 放行。
- 判定逻辑抽成**纯函数**（`ManaGate.evaluate`），所以自检能直接测它，不需要玩家实例。
- 自检从 18 项加到 **20 项**：新增「未解锁必拦 / 要求关闭时放行」「解锁后有魔力放行 / 没魔力拦」。
  实测：`要求解锁时=拦，不要求时=放`、`解锁+有魔力=放，解锁+没魔力=拦` ✅
- `/tnc engine` 现在报三个数：**判定执行次数 / 拦下次数 / 其中未解锁次数**。
  「判定次数」是关键 —— 它一直是 0 就说明 Mixin 根本没执行到我们的代码（比"注入成功"信号灯更硬）。

### 4.15.2 🔴 用 dev 桩引擎试出来的致命坑（2026-09-15）

为了在 dev 环境验证 Mixin 能不能注入，我做了一个**桩引擎** fixture
（`tools\dev-fixtures\build-stub-engine.ps1`：提供一个类名/方法签名和真引擎一致的
`net.spell_engine.internals.SpellHelper`，modId 也叫 spell_engine）。
它当场抓到一个**会让游戏启动失败**的 bug：

```
[main/ERROR] [mixin/]: NullPointerException: Cannot invoke "ModList.isLoaded(String)"
                      because the return value of "ModList.get()" is null
  at MixinInfo.<init> → MixinConfig.prepareMixins → MixinProcessor.prepareConfigs
[main/ERROR] [net.minecraft.server.Main/FATAL]: Failed to start the minecraft server
```

**Mixin 准备配置的时机非常早 —— 早到 Forge 的 `ModList` 还是 null。**
我原来的 `TncMixinPlugin.shouldApplyMixin` 里用 `ModList.get().isLoaded("spell_engine")` 判断，
结果抛异常 → 而且**插件里抛异常是致命的**：游戏直接起不来，**不是**安静跳过。
（如果我直接扔给用户测，他那边会开游戏即崩。）

**已改**：干脆不要插件了 —— `tnc.mixins.json` 去掉 `plugin`，保留 `"required": false`，
让 Mixin 自己处理"目标不存在"。这样：
- 整合包（有引擎）→ 正常注入；
- dev（没引擎）→ 目标类不存在，Mixin 自己跳过，日志干净。
✅ 实测：去掉插件后 dev 服务器正常启动、自检 20/20、零 mixin 报错。

⚠️ **桩引擎当时的局限（已在 4.15.4 解决）**：dev 里 mixin 配置是**桩 jar 的清单**声明的，
而 Mixin 会去**声明配置的那个容器**里找我们的 mixin 类 —— 找不到，所以**当时**桩环境下不会真的注入。
这条限制在 4.15.4 里被修掉了（把配置和 mixin 类一起放进桩 jar），**现在桩能证明注入真的发生**。
桩的价值因此从"①抓致命坑 ②证明配置能加载"升级为**"注入本身可验证"**。

（顺带踩到：`job_kill` 只杀掉了 pwsh 包装进程，**java 服务器进程会残留**并锁住世界 →
下次启动报 `另一个程序已锁定文件的一部分`。重启 dev 服务器前记得确认 `run\logs` 之外没有残留 java。）

### 🔴🔴 4.15.2b 「清理残留 java」把用户的游戏杀了（2026-09-15 事故）

**我干过的最严重的一次错事，写在这里防止重犯。**

为了清掉 dev 残留的 java，我跑了一句"按进程名杀全部 java"：

```powershell
Get-CimInstance Win32_Process -Filter "Name like '%java%'" | Stop-Process -Force   # ❌ 绝对不要！
```

**结果把用户正在加载的整合包游戏一起杀了**（PCL 日志：`PID 24444`）。

致命之处在于**它伪装成了一次 mod 崩溃**：

| PCL / 日志现象 | 看起来像 |
|---|---|
| `Minecraft 已退出，返回值：-1` | 崩溃 |
| `Minecraft 返回值异常，可能已崩溃` | 崩溃 |
| `latest.log` 停在 mod 加载中间 | 崩溃 |
| PCL 崩溃分析：**未找到可能的原因** | "查不出来，很可疑" |

而真相是：**没有异常、没有 crash-report、没有 `hs_err_pid`、日志戛然而止** ——
这四条合起来才是"被强行终止"的特征。真正的 mod 崩溃一定会留下
`Failed to start the minecraft server` 或 crash-report。

**铁律**：
1. **永远不要按进程名杀 java。** 只杀命令行里匹配本工程（`D:\ModTest`）的进程。
2. 用 `tools\kill-dev-java.ps1` —— 它按命令行区分 dev / 游戏，遇到像游戏的一律 `[SKIP]`。
3. 判断"崩溃 vs 被杀"：先看有没有 **crash-report / hs_err / 堆栈**，再看退出码是不是 `-1`。
4. **不要一边往 `mods\` 复制 jar 一边让用户启动游戏**（会覆盖正在读的文件）。复制前先确认没有 java 进程。

### 4.15.3 只注入一个重载 + `/tnc gatetest` 实测（2026-09-15）

**① 4.15 里"两个重载都注入"是错的（注释也写反了）。** 用 `javap -c` 读真引擎字节码：

```
attemptCasting(Player, ItemStack, ResourceLocation)            // 3 参：纯转发
    iconst_1
    invokestatic attemptCasting(...,Z)                         //   → 转调 4 参
attemptCasting(Player, ItemStack, ResourceLocation, boolean)   // 4 参：真正的实现
    checkcast SpellCasterEntity / SpellRegistry.getSpell(id)
    isCoolingDown(id) / ammoForSpell(...) / Attempt.success()
```

原文写的是"4 参内部会走 3 参"，**实际是反过来的**。所以：
- 只注入 **4 参**那一个点就覆盖了**所有**调用路径（谁调 3 参都会走到 4 参）；
- 更重要的理由：配置是 `required:false` + `defaultRequire:1`，
  **任何一个注入点没对上 → Mixin 抛错 → 整份配置静默失效**（游戏照常跑，只是魔力再也拦不住施法）。
  **每个注入点都是一条"静默失效"路径，越少越安全。** 现在固定为 1 个，verifier 会断言这个数量。

**② `SpellHelper.attemptCasting` 的返回值确认**（javap）：4 参实现只有读操作
（查注册表 / 查冷却 / 查弹药），**没有副作用** —— 所以可以安全地拿它做探针。

**③ 新增 `/tnc gatetest`：给硬拦截做"实测"，不再只靠静态比对。**
字节码描述符对得上只说明"注入器**应该**能对上"，说明不了"运行时**真的**注入了"。
这条命令在**一次性假玩家**上跑三级阶梯（不碰真玩家任何数据）：

| 步骤 | 条件 | 期望 |
|---|---|---|
| 1 | 魔力**够**、但**没解锁** | 拦住，算"未解锁" |
| 2 | 已解锁、魔力**差 1 点** | 拦住，算"魔力不足" |
| 3 | 已解锁、魔力**刚好够** | **放行** |

第 1 步同时是"注入生效"的**硬证据**：它看 `ManaGate.gateChecks` 涨没涨，
而这个计数器是**注入进去的代码里加的** —— 涨了就说明注入真的发生了，伪造不了。
三步合起来才说明"判定在按魔力和解锁状态区分"（一个永远返回"拦"的实现也能过第 1 步）。

**④ 启动时自动跑一遍**（`MagicStoneDiagnostics.runGateProbe`），结论直接进日志：

```
TN-C mana gate probe [server start]: 3/3 passed (硬拦截生效)
TN-C mana gate probe   [ok]   Mixin 已注入：判定代码真的被执行到 : gateChecks +1
```

这样**不用手动敲命令**，开服日志里就有明确结论。dev 环境没引擎 → 打一行
`skipped (...)` 就过（实测确认不会抛 NoClassDefFoundError）。

**⑤ verifier 加了两个"静默失效"回归断言**（`tools\verify_mod_jar.ps1`）：
- `tnc.mixins.json` **不能有 `plugin`**（4.15.2 那个启动崩溃坑）
- `tnc.mixins.json` 必须是 `required:false`
- mixin 类里 `@Inject` **必须恰好 1 个**

**⑥ 一个实现细节坑**：假玩家是按维度**缓存**的单例，所以探针每次跑之前要显式 `forget()`；
而且新假玩家 `maxMana = 0` → 不先 `setMaxMana()` 的话 `setMana(cost)` 会被夹成 0，
第 2 步就永远"魔力不足"，测试会假通过。

### 4.15.4 ✅ 注入本身在 dev 里被证明了（2026-09-15）

4.15.2 留下的遗憾是"桩环境下不会真的注入，整合包里到底注不注入只能靠整合包验证"。
这一轮把它解决了 —— **dev 里已经能完整证明注入生效**。

**怎么解决的（两个关键点）**

1. **Mixin 从"声明配置的那个容器"里找 mixin 类。** 桩 jar 用清单声明了 `MixinConfigs`，
   所以 Mixin 去**桩 jar 里**找 mixin 类 → 找不到 → 静默不注入。
   **修法**：把 `tnc.mixins.json`（改名 `tnc-devstub.mixins.json`）和 mixin 的 .class 一起打进桩 jar。

2. **但不能直接拷 —— JPMS 会拒绝"分包"（split package）**：

   ```
   java.lang.module.ResolutionException: Module spell_engine contains package com.tnc.tnc.mixin,
   module tnc exports package com.tnc.tnc.mixin to spell_engine
   ```

   ModLauncher 建模块层时，两个模块含同一个包 = 直接起不来。
   **修法**：桩里的那份挪到它独占的包 `com.tnc.xnc.mixin`。改包用的是**等长字节替换**
   （`com/tnc/tnc/mixin` → `com/tnc/xnc/mixin`，都是 17 字符），
   所以 class 文件里 UTF8 常量的长度前缀**不用动**，字节码保持合法，
   而且除了自己的包名之外和真 mixin **逐字节相同**（`@Mixin` 目标、`@Inject` 描述符、方法体全部不变）。
   脚本最后会**断言桩里的描述符和真 mixin 一致**，防止 fixture 悄悄漂移。

3. 顺带修了桩的一个**保真度 bug**：桩原来写成"4 参转调 3 参"，**和真引擎相反**，
   于是它验证的是已经废弃的"两个重载都注入"那套设计。现在桩严格照抄真引擎
   （**3 参 → 4 参**），所以它验证的是"**只注入 4 参，而 3 参的调用者一样被拦到**"。

**实测结果**（`tools\dev-fixtures\build-stub-engine.ps1` + `.\gradlew.bat runServer`）：

```
[mixin/]: Mixing SpellHelperManaGateMixin from tnc-devstub.mixins.json
          into net.spell_engine.internals.SpellHelper          ← 注入真的发生了
TN-C magic stone self-test [server start]: 21/21 passed
TN-C mana gate probe [server start]: 4/4 passed (硬拦截生效)
  [ok] Mixin 已注入：判定代码真的被执行到               : gateChecks +1
  [ok] 魔力充足但未解锁 → 拦住（算在「未解锁」头上）     : blocked +1 · unlearned +1
  [ok] 已解锁但魔力差 1 点 → 拦住（算在「魔力不足」头上） : 魔力 19 / 20 · blocked +1 · unlearned +0
  [ok] 已解锁、魔力刚好够 → 放行（不再拦）               : 魔力 20 / 20 · blocked +0
```

这证明了：**配置能被加载 → 描述符能对上 → 注入真的执行 → 判定按"解锁状态"和"魔力多少"正确区分**。
其中探针走的是**3 参**入口，所以也顺带证实了 4.15.3 那个"只注入 4 参就覆盖全部调用路径"的字节码结论。

**仍然只能靠整合包验证的部分**：Sinytra Connector 把 Fabric 版 SpellEngine 的类交给
Forge 的转换类加载器时，我们的 Forge mixin 能不能同样注入进去。
（`_mapped_srg_1.20.1.jar` 的存在说明 Connector 就是把 Fabric mod 转成 Forge mod 加载的，
理论上同一套机制；但这属于"理论"，要整合包实测。）

**⚠️ 用桩的纪律**：桩只放 `D:\ModTest\run\mods`，**绝不能进整合包**。
每次用完必须 `-Remove`，并确认三个 mods 目录里都没有 `*devstub*`（脚本里也加了"游戏在跑就拒绝动手"的保护）。

### 4.15.5 ✅ 「Connector 环境下能不能注入」—— 已由包内既有 mod 证明（2026-09-15）

4.15.4 说还剩一个理论未知：Sinytra Connector 把 Fabric 版 SpellEngine 交给 Forge 加载时，
我们的 **Forge mixin 能不能注入进 Connector 加载的引擎类**。这一轮从整合包自己的日志里找到了**直接答案**。

**① 同一套机制：Connector 把 Fabric mod 交给 Forge 的 Mixin 服务处理。**

```
[ModLauncher]: Found transformer services : [vaultpatcher, mixin, fml, hotai, mixin-transmogrifier, connector_loader]
[mixin/]: Mixing client.action_impair.MinecraftClientActionImpairing from spell_engine.mixins.json into net.minecraft.client.Minecraft
[mixin/]: spell_engine.mixins.json:... from mod spell_engine->@Inject::doAttack_HEAD_...
                                                ^^^^^^^^^^^^^^ 引擎被当成一个 Forge mod
```

Connector 注册了 `connector_loader` / `mixin-transmogrifier` 两个 ModLauncher 服务，
Fabric mod 的 mixin 由**同一个 `[mixin/]` 服务**应用 —— 也就是同一个类转换管线、同一个模块层。

**② 决定性证据：这个包里已经有 Forge mod 成功注入进引擎类。**

```
[mixin/]: Mixing SpellEngineModMixin         from ysjx_weapons.mixins.json    into net.spell_engine.SpellEngineMod
[mixin/]: Mixing SpellEngineTargetHelperMixin from ysjxteams.mixins.json      into net.spell_engine.utils.TargetHelper
[mixin/]: Mixing SpellProjectileMixin         from spellbladenext.mixins.json into net.spell_engine.entity.SpellProjectile
```

其中 **`ysjxteams-1.0.0.jar` 是 Forge mod**（有 `mods.toml`，清单里 `MixinConfigs=ysjxteams.mixins.json`），
它的配置和我们**几乎一模一样**：

```json
{ "required": false, "package": "com.ysjxteams.mixin",
  "mixins": ["SpellEngineTargetHelperMixin"], "injectors": { "defaultRequire": 0 } }
```

javap 确认它用的是 `@Mixin(value = <引擎类>, remap = false)` + `@Inject`，**没有 refmap** ——
和我们的设计完全一致。**这就是"我们的做法在这个包里可行"的实证，不再只是推理。**

**③ 因此把 `defaultRequire` 从 1 改成 0**（`required:false` 保留）。
注入器对不上时只会打警告、不会报错 —— **游戏永远起得来**。
代价是"静默失效"，但**这个代价已经被两件事抵消**：`/tnc gatetest` 和启动探针每次都会如实报 FAIL。
（这也正是上面那个能正常工作的 `ysjxteams` 的选择。）

**④ 整合包运行时已确认的一环**：

```
TN-C: mana hook registered on SpellEngine SPELL_CAST
```

**目标 ① 的接线在 Connector 真环境下跑通了**（引擎事件 API 可解析、监听器真的挂上）——
这条在 dev 里跑不到（没引擎），此前只有 javap 静态比对。

**⑤ 顺带发现**：被 Connector 转换过的引擎 jar 有 `fabric.mod.json` 但**没有 `mods.toml`**，
却在清单里声明了 `MixinConfigs` —— 说明它不是走 Forge 的 mod 扫描器，而是 Connector 的定位器。
（Connector 还有个 `SplitPackageMerger`，专门合并分包 —— 和 4.15.4 里我们撞到的 JPMS 分包问题是同一类东西。）

### 4.15.6 证据总表（截至 2026-09-15 23:30）

| 目标 | 代码 | dev 实测 | 整合包实测 |
|---|---|---|---|
| ① 施法扣魔力（服务端权威） | ✅ | 算术边界 5 条自检；注册路径可跑 | ✅ **钩子已注册到 SPELL_CAST**；事件触发后扣魔待进世界 |
| ② 魔力不足的反馈 | ✅ | 拦截分支被真实执行（三级阶梯） | 待进世界 |
| ③ 施法前真拦截（硬拦截） | ✅ | ✅ **Mixin 确实注入 + 阶梯 4/4** | ⏳ 待进世界；**可行性已由 `ysjxteams` 证明** |
| ④ 数值表 + GUI 显示消耗 | ✅ | 数值表自检通过 | 待 GUI 目视 |

**唯一还剩的未知**：进世界后 `SpellHelper` 被加载的那一刻，我们的 mixin 是否真的应用。
一条命令就能读出结论（`tools\check-pack-verdict.ps1`）。

### 4.15.8 ✅✅ 整合包实测通过：硬拦截在 Connector 环境下真的生效了（2026-09-15 23:34）

**这是整个「魔力参与施法」的收官证据。**

```
[23:34:50.137] [Server thread/DEBUG] [mixin/]:
    Mixing SpellHelperManaGateMixin from tnc.mixins.json
    into net.spell_engine.internals.SpellHelper
[23:34:50.168] TN-C magic stone self-test [server start]: 21/21 passed
[23:34:50.202] TN-C mana gate probe [server start]: 4/4 passed (硬拦截生效)
  [ok] Mixin 已注入：判定代码真的被执行到                  : gateChecks +1
  [ok] 魔力充足但未解锁 → 拦住（算在「未解锁」头上）        : blocked +1 · unlearned +1
  [ok] 已解锁但魔力差 1 点 → 拦住（算在「魔力不足」头上）    : 魔力 19 / 20 · blocked +1 · unlearned +0
  [ok] 已解锁且魔力刚好够 → 放行（不再拦）                  : 魔力 20 / 20 · blocked +0
```

加上更早的 `TN-C: mana hook registered on SpellEngine SPELL_CAST`，
四个目标**全部拿到整合包级证据**：引擎事件挂上了、Mixin 注入进去了、
判定确实按"解锁状态 + 魔力多少"区分、界面数值表已就位。

**⚠️ 一个把我自己坑了一次的细节**：`Mixing ... into ...` 是 **DEBUG 级**，只写进 `debug.log`，
**不写进 `latest.log`**。`check-pack-verdict.ps1` 第一版只看 `latest.log`，
于是明明 4/4 通过却报了 `FAIL`。判据要用**两个**来源：
`debug.log` 里 Mixin 自己那行（直接证据）+ 探针第 1 条断言（`gateChecks` 涨了 = 注入的代码被执行到，
这个伪造不了）。**先看探针结论，别只看日志 grep。**

### 4.15.9 证据总表（最终）

| 目标 | 代码 | dev 实测 | 整合包实测 |
|---|---|---|---|
| ① 施法扣魔力（服务端权威） | ✅ | 算术 5 条边界自检 | ✅ 钩子已注册到 `SPELL_CAST` |
| ② 魔力不足的反馈 | ✅ | 拦截分支被真实执行 | ✅ 拦下路径被执行（探针 2/3 步） |
| ③ 施法前真拦截（硬拦截） | ✅ | ✅ 桩引擎注入 + 阶梯 4/4 | ✅ **Mixin 真的注入 + 阶梯 4/4** |
| ④ 数值表 + GUI 显示消耗 | ✅ | 数值表自检通过 | ✅ 自检 21/21 |

**还没做过的事（都不是阻塞项）**：
- 在游戏里真放一次法术，肉眼看动作栏的 `魔力 -20 → …`（服务端扣魔已由注册+算术覆盖）
- 装含 `ManaCharge` 重构的新版 jar（自检 21 → 26 条）：`tools\install-to-pack.ps1 -Wait`
- GUI 里目视确认每个法术的消耗数字

---

### 4.15.11 🔎 `performSpell` 的内部结构（读字节码得到的，很关键）

用户反馈"施法后魔力没扣"。读引擎 `performSpell` 的字节码，得到两件重要事实：

```
performSpell(Level, Player, ResourceLocation, List<Entity>, Action, float):
  33: Player.getMainHandItem()
  42: invokestatic attemptCasting(Player,ItemStack,ResourceLocation)   ← 引擎自己又判一次
  49: Attempt.isSuccess()
  52: ifne 56
  55: return                        ← 不成功就直接不放，事件也不会触发
  ...（扣弹药 / 算命中 / 粒子音效 / 上冷却）...
 902: getstatic CombatEvents.SPELL_CAST
 905: Event.isListened()
 908: ifeq 941                      ← 没人监听就跳过
 938: Event.invoke(Consumer)        ← 事件在**所有效果处理完之后**才播报
 941: return
```

**① 硬拦截其实有"两道"**：`performSpell` 内部会再调一次 `attemptCasting`。
所以我们拦 `attemptCasting` 等于同时守住了"起手判定"和"真正放出去之前"两道门 —— 比预想的更稳。

**② 被拦下时事件根本不会触发**（55 行提前 return），所以"拦住了"和"没扣魔力"是一致的、不是 bug。

**③ 那"魔力没扣"最可能是什么**：法术 id 不是 `tnc:*`。
作者的雷系法术书池子里一共 **10 个法术**（作者的 5 个 + 我们的 5 个），
随手按到槽位 1 很可能放的是**作者的法术**；而 TN-C 故意只对自己的法术扣魔力（`entry == null` 就 return）。

**为了不再靠猜**，这一轮加了：
- `SpellEngineManaHook` 里记 `seen / ours / otherMod` 三个计数 + `lastSeen` 一行，
  `/tnc engine` 直接显示"施法事件 N 次 · TN-C 法术 M 次 · 别家法术 K 次"，并在 `seen>0 && ours==0` 时明确提示
- 每次施法在日志里打一行 `TN-C: SPELL_CAST spell=... action=... caster=... ours=true/false`
- **`/tnc cast <法术id>`**：直接调 `performSpell` 放指定法术，不看法术书、不管键位槽位。
  走的是和真实施法完全相同的路径（含那道 `attemptCasting`），所以一条命令能同时验"硬拦截"和"扣魔力"

### 4.15.12 法术书的发放逻辑（bug 修复）

用户反馈"每解锁一个法术就给一本新书"。原因是 `SpellEngineBridge` 原来的写法：
遍历背包，**只往"池子认这个法术"的书里写**（`getPool(container).spellIds().contains(spell)`），
只要 `containerFromItemStack` 对作者那本书返回 null、或它自带池子的名字/内容和我们以为的不一样，
这一轮就被判成"没有合适的书" → 走 `giveNewBook` → 每解锁一次发一本。

**改法**：按**物品**匹配 —— 身上有 `ysjx_weapons:lightning_spell_book` 就往里写
（没有 container 就补一个）；池子只用来决定"新书默认认哪些法术"，不再当"能不能写"的门槛。
玩家背包 `getContainerSize()` 是 41（36+4+1），所以副手那本也能找到。

---

### 4.16 🔄 设计改向：抛弃法术书，改成「法杖 + 魔法石」（2026-09-15 用户拍板）

**用户原话**：「抛弃魔法书的概念吧，你既然都有魔法石了，只要你有法杖，你就可以按数字释放魔法，
不然的话你这样魔法书逻辑太难弄了，你可以把这个逻辑绑到魔法石上面去。」

这个判断是对的。法术书那套是我引入的多余一层，而且**那本书是作者的**（池子里有他自己的 5 个法术），
玩家随手按到别人的法术 → TN-C 不扣魔力 → 看起来像坏了。更要命的是书会丢、内容会少、槽位和"第几个已解锁法术"对不上。

**新模型**：

```
魔法石（权威，隐藏数据）  ──单向同步──▶  法杖（派生道具，SpellContainer）
        │                                      │
        ├─ 已解锁哪些法术 ─────────────────────┘
        └─ 你手上得拿着法杖才能放（requireWandToCast）
```

- 登录 / 解锁 / `/tnc wand` → 把"已解锁集合"同步进法杖；**没有法杖但解锁过法术就补发一根**
- 法杖认**我们自己的法术池** `tnc:tnc_lightning`（`data/tnc/spell_pools/tnc_lightning.json`，只含 5 个 tnc 雷法）
  → 槽位顺序 = 目录里等级的顺序，可预期
- 顺手把作者的 `ysjx_weapons:lightning` 池**改回只有他原来的 5 个**（我们的法术以后只从法杖出）
- 新增判定 `Decision.NO_WAND`（手上没法杖就拦住）+ 计数器 `noWandCount`

### 🔴🔴 4.16.1 ~~关键发现：引擎的快捷栏是按 `instanceof SpellBookItem` 认物品的~~ —— **这条结论是错的，见下方更正**

> ⚠️ **2026-09-16 更正**：下面这段的结论**完全反了**，而且它直接把法杖做坏了。
> `ifne` 是"成立就跳走"，不是"成立就走这里"。保留原文是为了留下教训：
> **读到 `ifne`/`ifeq` 一定要确认跳转目标的含义，光看"某个 instanceof 出现在字节码里"就下结论会南辕北辙。**
> 正确结论见 §4.16.6。

**（以下是当初的错误推理，勿信）**：我最初的实现是"一个普通 `Item` + 往它 NBT 里写 `SpellContainer`"，
理由是 `SpellContainerHelper.containerFromItemStack()` 读 NBT、与物品 Java 类型无关。
读引擎 `client.input.SpellHotbar` 的字节码时，我看到：

```
 77: instanceof net/spell_engine/api/item/trinket/SpellBookItem
 90: getfield SpellContainer.spell_ids
```

于是**错误地**推断"物品必须是 SpellBookItem 才会被取用"，
并把法杖改成继承 `SpellBookVanillaItem`。**结果正好相反 —— 这一改恰恰让快捷栏彻底不认它。**

**但这就带来一个加载顺序问题**：这个类 `extends` 引擎类，dev 环境（没引擎）一加载就 `NoClassDefFoundError`，
连物品注册都过不去。解法还是工程里那个老套路 —— **把 `new` 关在嵌套类里**：

```java
public static Item createWandItem() {
    if (!enginePresent()) return new Item(...);   // 没引擎：退化，保证注册不炸
    return EngineWand.create();                  // 有引擎：才加载 MagicWandItem
}
private static final class EngineWand { static Item create() { return new MagicWandItem(); } }
```

嵌套类只在第一次被用到时加载，而那时已经确认引擎存在了。javap 核对过：
`SpellEngineBridge$EngineWand` 只引用 `MagicWandItem`，不直接引用任何引擎类。

**玩家角度这只是"一根法杖"** —— `SpellBookItem` 是引擎内部实现的名字，不暴露。

> ⚠️ 以上整段（`MagicWandItem extends SpellBookVanillaItem` + 嵌套类隔离）**已在 §4.16.6 被整体删除** ——
> 它建立在一个读反了的字节码判断上。现在 `createWandItem()` 就是一句 `new Item(...)`，
> 既不需要嵌套类隔离，也没有引擎依赖。

### 4.16.6 ✅✅ 真正的结论：法杖必须是**普通 Item**（不是 `SpellBookItem`）

**用户反馈"还是没有数字键"之后，重新逐条读 `SpellHotbar.update()` 的完整字节码，看到了跳转目标：**

```
74: aload 5 ; invokevirtual ItemStack.getItem()
77: instanceof net/spell_engine/api/item/trinket/SpellBookItem
80: ifne 516          ← ★ 是 SpellBookItem 就"跳到 516"，跳过下面整段
83: aload 6
85: ifnull 516        ← 没有容器也跳过
88: getfield SpellContainer.spell_ids        ← 只有"不是 SpellBookItem 但有容器"才读这里
95: ... stream().map(→SpellInfo).filter(...).toList()
```

**`ifne 516` = "成立就跳走"。** 所以快捷栏是**两条互斥的路**：

| 主手物品 | 快捷栏从哪取法术 |
|---|---|
| **是** `SpellBookItem` | 另一条分支（偏移 516 之后；要靠池 / `SpellBooks` 那一套登记） |
| **不是** `SpellBookItem`，但有 `SpellContainer` | **直接读容器的 `spell_ids`** |

作者的杖 `extends StaffItem`（**不是** `SpellBookItem`）+ 自带 `spell_assignments` → 走第二条，所以一直好用。
我把法杖做成 `SpellBookItem`，正好被 `ifne` 跳开 → **数字键完全没有**。

**最终实现（三处配合，缺一不可）**：

1. **法杖 = 普通 `new Item(...)`**（`createWandItem()` 一行，无引擎依赖、dev 也能注册）
2. **`data/tnc/spell_assignments/magic_wand.json`**：让引擎（含客户端）知道这个物品是法术载体；
   内容照抄作者那根能用的杖的形状（`is_proxy: true` + 5 个雷法），作为**兜底**
3. **NBT 容器**（`is_proxy: false`，内容 = 已解锁法术按等级排序）→ 覆盖兜底，实现"魔法石驱动槽位"
   - 写完必须 **`player.inventoryMenu.broadcastChanges()`** 强制同步：
     **快捷栏跑在客户端**，就地改服务端 NBT 不保证推过去

**实测确认（2026-09-16 00:39）**：用户连续施放 10 次，日志逐条：

```
TN-C: SPELL_CAST spell=tnc:spark            action=RELEASE caster=Cockr0Ach ours=true
TN-C: SPELL_CAST spell=tnc:lightning_field  action=RELEASE caster=Cockr0Ach ours=true
TN-C: SPELL_CAST spell=tnc:lightning_strike action=RELEASE caster=Cockr0Ach ours=true
self-test 26/26 · mana gate probe 6/6
```

**法杖被引擎认了、数字键能放了、钩子每次施法都收到 → 魔力在扣。**

**这一串坑的教训（比结论更值钱）**：
1. **`ifne`/`ifeq` 要看跳转目标** —— 光看到"字节码里有个 instanceof"就下结论，会得出完全相反的答案。
2. **引擎的契约不全在 Java 签名里**：`spell_assignments`（物品↔容器的数据文件）在 API 上完全看不见，
   少一个文件就静默失效。
3. **要抄就抄一个已经能工作的真实 mod**：作者那三根杖的数据目录（`spell_assignments`）和物品基类
   （`StaffItem`）就是最好的规格说明书。
4. **客户端/服务端要分清楚**：快捷栏、HUD、按键都在客户端；服务端改完 NBT 不主动推，客户端就看不到。

### 4.16.2 顺带修好的：`requireWandToCast` 会把启动探针测坏

探针用的是**假玩家**，而假玩家手上没有法杖 —— 加了法杖要求之后，
"魔力够就该放行"那一步会被"没法杖"这个原因拦掉，看起来像魔力判定坏了。

所以探针改成先测一条新断言「**没拿法杖 → 拦住**」，再给假玩家塞一根法杖，
之后才测解锁/魔力那三级。这样每一条失败都能指到正确的原因上。

---

### 🔴🔴 4.16.4 法杖不被引擎认（没有数字键）—— 缺的是 `spell_assignments` 数据文件

**现象**：手里拿着 TN-C 的法杖，**不出现法术快捷栏、按数字键没反应**；换成作者的法杖就好了。

**根因**（读引擎 `SpellRegistry` 字节码找到的）：

```java
public static void loadContainers(ResourceManager rm) {
    String folder = "spell_assignments";                     // ← 物品↔法术容器的关联
    Map<ResourceLocation, Resource> files = rm.listResources(folder, ...);
    // 文件名 = 物品 id，内容 = 一个 SpellContainer（JSON）
    containers.put(itemId, container);
}
public static final Map<ResourceLocation, SpellContainer> containers;
public static SpellContainer containerForItem(ResourceLocation itemId);
```

也就是说"这个物品能装法术"这件事，引擎是**从 `data/<命名空间>/spell_assignments/<物品名>.json` 读的**，
而且这份数据会**同步到客户端**。我们的法杖只有 NBT，没有这个文件 →
**客户端根本不知道它是法术载体 → 不给快捷栏 → 按键全无反应，而且完全是静默的。**

作者的杖有这个文件，所以我们用他的杖时一切正常：

```
data/ysjx_weapons/spell_assignments/lightning_staff.json
{ "is_proxy": true, "spell_ids": [ "ysjxspells:leiji_basic" ] }
```

**修法**：新增 `src/main/resources/data/tnc/spell_assignments/magic_wand.json`，
内容照抄那个已经被证明能用的形状（`is_proxy: true` + 我们的 5 个雷法），
并把 `writeContainer` 里的 `is_proxy` 也从 false 改成 true（`SpellContainer.isValid()` 对 proxy 直接返回 true）。

**教训**：这类"缺一个数据文件就静默失效"的机制，光读 Java API 是看不出来的 ——
**要去翻一个已经能工作的真实 mod 的数据目录，照着它的形状抄。**
（这条和 4.16.1 的 `instanceof SpellBookItem` 是同一类问题：引擎的契约不全在 Java 签名里。）

**顺带确认（这一轮日志）**：法杖方案的其他部分其实都通了 ——
`self-test 26/26`、`mana gate probe 6/6`（含新增的「没拿法杖→拦住」）、
`gave a new magic wand (2 spells)`、`synced 1 magic wand(s) ... 3 spell(s)`，
而且**用户真的放出了 `tnc:lightning_field`，钩子收到了 `SPELL_CAST ... ours=true`** ——
说明目标①那条"引擎会播报我们的法术"已经实测成立。

---

### 4.16.5 工具表（法杖阶段新增/修好的）

| 工具 | 作用 |
|---|---|
| `tools\check-pack-verdict.ps1` | 一条命令读出整合包判定。处理四个坑：日志是 **GBK**、游戏运行时日志被**独占锁**（要用 `FileShare.ReadWrite`）、**区分"被杀"还是"崩溃"**、以及 Mixin 那行在 **debug.log 而非 latest.log** |
| `tools\install-to-pack.ps1` | 安全安装 jar：**有 java 在跑就拒绝**（`-Wait` 可等），装完自动 verify |
| `tools\kill-dev-java.ps1` | 已修：原来用 `BootstrapLauncher` 当"游戏标志"，可 **Forge dev 服务器也用它**，导致残留 dev JVM 被误判放过（→ 世界存档被锁）。现在按 `--launchTarget` 判定 |

---

### 4.17 魔力条 HUD 版面：按**包内实际坐标**重排（2026-09-16）

用户反馈：「魔力条把血条完全挡住了」「聊天框的暗底把紫色那条占了」
「中心的魔法石被遮住了」。查下来是三件独立的事：
**坐标算错了**（血条位置）、**一个多余的 return**（开界面就消失）、
**深度缓冲**（聊天框之后画的东西直接被丢掉）—— 见 4.17.1 / 4.17.3 / 4.17.5。

#### 4.17.1 这个包的状态条不在原版位置（实测）

包里的血条/饱食度被 `whisperingstatusbar-1.3.jar` 换成了 11 像素高的贴图条，
于是整条往上长了一行。**拿用户截图逐像素扫出来的**（截图 856x512 = GUI 428x256，
GUI 缩放 2，所以 GUI y = 物理 y / 2）：

| GUI y | 画的是什么 | 是谁画的 |
|---|---|---|
| `高-61 .. 高-51` | 护甲条（**左半边**，右半边空着） | 包（whisperingstatusbar） |
| `高-50 .. 高-40` | 血条（左）/ 饱食度（右） | 包 |
| `高-49 .. 高-40` | **聊天框最下面一行的暗底** | 原版 `ChatComponent` |
| `高-39` | 原版血条锚点（这个包里没人用） | —— |
| `高-22` | 快捷栏 | 原版 |

**教训**：原版血条在 `高-39`，但这个包把它画到了 `高-50`。
我们原来按原版算，把魔力条画在 `高-49` —— 正好横在血条身上，**全盖住了**。
所以现在摆在 `高-61`（饱食度正上方一行 = 护甲条那一行的右半边，那里本来就是空的），
和**饱食度**同宽（80）同右边界（`宽/2 + 91`）。

#### 4.17.2 聊天框暗底是"一条横带"，正好压在血条那一行

从 `ChatComponent.render` 读出来的（不是猜的）：

```java
int l  = guiHeight();                        // 256
int i1 = Mth.floor((l - 40) / scale);        // = 216
// 每行消息（j2 = 0 是最新那条）：
int i4 = i1 - j2 * lineHeight;               // 最新一行 i4 = 216，lineHeight = 9
pose.translate(4, 0, 0);                     // 整块往右挪 4
fill(-4, i4 - 9, getWidth() + 8, i4, k3 << 24);   // => GUI x 0..332, y 207..216
```

所以**最新那条消息的暗底 = GUI `x 0..332`、`y 207..216`，而且是全屏宽度**
（`getWidth()` 默认 320，`chatWidth` 选项拉满时更宽）。
血条/饱食度（`206..216`）正好长在带子里 —— 这就是为什么一有聊天消息它们会变暗；
而护甲条（`195..205`）在带子**外面**，所以它一直不暗。

魔力条挪到 `高-61`（= `195..205`）以后，**整条都在带子外面**（带宽最多到约 `宽/2+12`），
所以条子本身安全了。但**图标在屏幕正中间，躲不开**（带宽 `0..332` 正好盖住中线），
它的下场见 4.17.5 —— 那才是"被遮住"的真身。

> 预览图：`docs\previews\mana_bar_layout.png`（拿用户截图合成的前后对比）

#### 4.17.3 🔴 真正的 bug：`if (minecraft.screen != null) return;`

用户说"血条、饱食度一直看得见、就是半透明，只有魔力条整条没了"。
原因就是我们自己写的那个判断。**原版开着界面时照样画 HUD**：

```java
// GameRenderer.render
if (!this.minecraft.options.hideGui || this.minecraft.screen != null) {
    this.minecraft.gui.render(guigraphics, p_109094_);   // ← HUD（含我们的条、含聊天框）
}
...
} else if (this.minecraft.screen != null) {
    ForgeHooksClient.drawScreen(this.minecraft.screen, ...);   // ← 界面在这之后才画上去
}
```

顺序是：`Gui.render`（我们的 `RenderGuiEvent.Post` 在这里面，**在聊天框之后** ——
`chat.render` 是 `Gui.render` 的倒数第二件事）→ 然后才画界面。
所以：

- 按 T 开聊天框时，血条/饱食度被"聊天框自己那张暗底"压暗，但**一直在**；
- 我们写了 `screen != null` 就 return，于是**全场只有魔力条消失**，
  玩家一眼就看出是 bug。

**改法**：删掉 `screen != null` 判断，只留 `hideGui`（F1）。
界面本来就会盖在我们上面，不用我们操心。

#### 4.17.4 顺便记住的两条

1. **HUD 是画在界面底下的，不是不画**（`hideGui || screen != null` 都要画）。
   `RenderGuiEvent.Post` 画在界面之下 —— 想挂 `ScreenEvent.Render.Post` 让条子
   盖住聊天界面是错的，那会把聊天文字一起挡住。（但"盖过聊天框**暗底**"是另一回事，
   能做到也应该做，见 4.17.5。）
2. 改条子位置只需动 `MagicStoneHud` 顶部那几个常量：
   `PACK_BAR_ROW_HEIGHT`（步长 11）、`HUNGER_ROW_TOP_MARGIN`、`MANA_BAR_TOP_MARGIN`、
   `MANA_BAR_X_OFFSET`、`TRACK_U/V/W/H`（轨道在贴图里的位置）。

---

#### 4.17.5 🔴🔴 真凶：聊天框暗底把我们的像素**从深度缓冲里丢掉**了（2026-09-16）

用户第二张截图（1920x1080，GUI 缩放 3 → GUI 640x360）：条子在右边好好的，
**中间的魔法石整个不见**。

先排除"变暗"这个可能：逐像素扫魔法石该在的位置
（GUI `312..328, 296..312` → 物理 `936..984, 888..936`），
再把该位置反推的几种颜色全图搜一遍 —— 宝石描边 `0xB9A7FF`、填充 `0x9C86F5`、
高光 `0xF0E8FF`，以及它们被压暗 50% / 60% / 75% 后的样子：

| 颜色 | 全图命中 |
|---|---|
| `0x9C86F5`（宝石填充） | **0** |
| `0xF0E8FF`（宝石高光） | **0** |
| `0xB9A7FF`（描边） | 1469，**全在魔力条矩形里**（物理 996..1232, 897..926） |
| 上面各色 ×50%/40%/25% | **全是 0** |

**结论：不是被压暗，是根本没画上（被丢了）。** 而且用户上一轮说的
"魔法石被遮到了一半、就少了一半"，少的正好是落进暗底矩形的那一半 —— 完全吻合。

原因（读 `RenderType` / `RenderStateShard` 源码确认，不是猜）：

```java
// RenderType.java L144
private static final RenderType GUI = create("gui", POSITION_COLOR, QUADS, 256,
    CompositeState.builder()
        .setShaderState(RENDERTYPE_GUI_SHADER)
        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
        .setDepthTestState(LEQUAL_DEPTH_TEST)      // ← 深度测试
        .createCompositeState(false));
// RenderStateShard.java L567 / L574 —— CompositeState 的两个默认值
private DepthTestStateShard depthTestState = LEQUAL_DEPTH_TEST;
private WriteMaskStateShard writeMaskState = COLOR_DEPTH_WRITE;   // ← 还写深度
```

而 `ChatComponent.render` 里，暗底画在 `z = +50`、文字画在 `z = +100`
（连着两次 `pose().translate(0, 0, 50)`）。于是：

| 谁 | 什么时候画 | z | 结果 |
|---|---|---|---|
| 包内血条/饱食度 | 聊天框**之前** | 0 | 被暗底蒙一层 → 变暗但看得见 |
| 聊天框暗底 | —— | **+50** | 写深度 |
| 聊天文字 | —— | **+100** | 写深度 |
| **我们的条/宝石** | 聊天框**之后** | 0 | 落在暗底矩形里的像素 **LEQUAL 失败 → 丢弃（全无）** |

注意 `Gui.java:323` 的 `chat.render(...)` 就在 `Gui.render` 结尾附近，
而 Forge 的 `RenderGuiEvent.Post` 在那之后 —— 所以我们比聊天框**晚**画，
这就是"晚画反而被吃掉"的原因。

**修法（一行）**：把我们的绘制整体抬到 `z = +75`
（`graphics.pose().translate(0, 0, HUD_Z)`）：

- **75 > 50** → 压过聊天框暗底，条子和宝石永远看得见；
- **75 < 100** → 仍在聊天文字之下，长消息的文字会盖在我们上面，绝不挡字；
- 位置一个都不用动。

> ⚠️ 别再犯的误判：上一轮把这个现象当成"配色不够亮、被暗底吃掉了"，
> 于是去提亮颜色 —— 方向完全错了，颜色再亮也会被丢掉。
> **教训：HUD 元素"整块不见"时，第一反应应该是查 z / 深度，而不是查颜色。**

---

### 4.18 魔力条换成贴图条「thundermagicbar」（2026-09-16）

用户自己拿来的两张 168x26 贴图（`thundermagicbar_empty.png` / `thundermagicbar_fill.png`）：
左边画好了一颗魔法石、中间一条轨道、右边一道闪电，外面套着和血条同款的金框。
所以代码里只剩两件事：**按魔力比例把中间那段轨道填起来**、**把数字压上去**。

#### 4.18.1 贴图规格（逐像素量出来的，改图要照着来）

| 参数 | 值 | 说明 |
|---|---|---|
| 画布 | **168 x 26** | 和包内 `health_*.png` / `food_*.png` **完全同规格** |
| 游戏里画的尺寸 | **84 x 13** | 包内血条/饱食度也是按 **0.5 倍**画的（量过：饱食度那条在屏幕上 84 宽 13 高） |
| 轨道（会涨的那段） | **u 42..148, v 14..19** | 左边 0..41 是宝石+金框、右边 149+ 是闪电，按比例裁剪时不能碰 |
| empty vs fill | 只差轨道颜色 | fill 的轨道是紫的（约 `v14..19` 那条带），empty 是深棕 |

#### 4.18.2 三个必须用对的地方

1. **裁剪 + 缩放要用带 `uWidth/vHeight` 的 blit 重载**
   `blit(loc, x, y, 宽, 高, u, v, uWidth, vHeight, texW, texH)` ——
   它允许"目标尺寸"和"取样尺寸"不同，所以一刀就能**既裁轨道、又维持 0.5 倍**。
   （用上一版那个"目标宽 = 取样宽"的重载，一裁剪会把左边的宝石和右边的闪电一起切掉。）
2. **填充只在 `u 42..148` 里按比例长**：`filled` 按屏幕像素取整（= 半个贴图像素），
   取样宽取 `filled * 2` —— 正好 0.5 倍，而且永远画不出轨道之外。
3. **位置**：左边界 `宽/2 + 11`、框高 13，紧贴饱食度贴图框正上方 →
   `高-63 .. 高-50`，恰好落在包内**护甲条那一行的右半边**（那里本来就是空的）。

> 预览图：`docs\previews\mana_bar_thunder.png`
> 上一版自己画的 `mana_empty.png` / `mana_fill.png` 和生成脚本 `tools\mana_bar_texture_gen.ps1`
> 都留着了，想改回代码画的那版只需把 `MANA_BAR_EMPTY/FILL` 两个路径换回去。

#### 4.18.3 顺带去掉的东西

- **居中的魔法石图标去掉了**（原来 HUD 正中还有一颗，按 V 的提示）。
  现在条子贴图左边自己就画着一颗，再画一颗是重复的 —— 用户确认后删掉了
  `onRenderGui` 里那行 `drawStone(...)`。石头画法本身留着给物品栏界面的入口按钮用
  （`MagicStoneButton` → `MagicStoneHud.drawStone`），HUD 打开界面仍然靠快捷键 V。
- 授权：这两张贴图是**用户自己画的**（已确认），没有第三方素材问题。

---

### 4.19 「魔力条不跟着魔力变」的真相：链路没坏，是数值太小（2026-09-16）

用户报：「魔力条没有随着魔力减少而实时显示，条子的长度也没变化」。
加了两行 `[diag]` 日志实测（打完就删了），结论是**整条链路一点问题都没有**：

| 时间 | 日志 | 含义 |
|---|---|---|
| 21:52:10.041 | `SPELL_CAST spell=tnc:lightning_strike action=RELEASE ours=true` | 引擎事件来了 |
| 21:52:10.044 | `[diag] sync -> Cockr0Ach mana=560/620` | 服务端扣了 60 |
| 21:52:10.055 | `[diag] client mana 620/620 -> 560/620` | 客户端收到了（14 毫秒） |
| 21:52:11.722 | `[diag] sync -> mana=592/620` | 1.7 秒后回魔 +32 |
| 21:52:13.717 | `[diag] sync -> mana=620/620` | 3 秒就回满了 |

两个原因叠加，玩家在条子上什么都看不见：

1. **消耗相对上限太小**：上限 = 亲和力总和 × 10 + 原版等级 × 10（41 级 → 620），
   而消耗表是写死的 `[20, 40, 60, 100, 160]` → 一级法术只花 **3.2%**。
2. **回魔太快**：每 2 秒回「1 + 上限的 5%」= 32 点 → 20 点的消耗**一个周期就回满**。

条子轨道只有 59 像素宽，20/620 的差 = **1.9 像素**，而且 2 秒后就被回魔抹平。

#### 4.19.1 修法（全是配置项，可以随时改回去）

| 配置 | 默认 | 作用 |
|---|---|---|
| `manaCostScalesWithMaxMana` | `true` | 消耗随上限等比放大：`实际 = 表里的值 × 上限 ÷ 基准上限` |
| `manaCostBaselineMaxMana` | `210` | 消耗表对应的基准上限（设计时假定的 0 级上限）。调小 = 整体变贵 |
| `manaRegenDelayAfterCastTicks` | `60`（3 秒） | 施法后暂停回魔，让玩家看得见刚扣掉的那一截 |

改完之后**任何上限下**一级法术都吃掉条子的 9.5%（≈5.6 像素）、神级 76%（45 像素）：

| 上限 | 一级 | 二级 | 三级 | 四级 | 神级 |
|---|---|---|---|---|---|
| 210（0 级） | 20 | 40 | 60 | 100 | 160 |
| 620（41 级） | 59 | 118 | 177 | 295 | 472 |

判定、扣费、提示、界面**全部走同一个口径**（`SpellCatalog.Entry.manaCostFor(maxMana)`），
所以不会再出现"提示说 20、实际扣 59"。自检加了一条
`mana cost scales with max mana`，会把两个上限下的消耗打出来。

> ⚠️ 教训：HUD「看起来没反应」时，先算**变化量占满格的比例**和**变化能维持多久**，
> 再去怀疑代码。这次两个答案分别是 3% 和 2 秒 —— 都不用看代码就知道看不见。

---

### 4.20 雷系三条链（10 个新法术，2026-09-16）

用户定的两条新链路，加上原有主链 —— 雷系现在是 **3 条链 x 5 级 = 15 个法术**：

| 链 | 1 级 | 2 级 | 3 级 | 4 级 | 5 级 |
|---|---|---|---|---|---|
| **主链 CORE** | 小闪电 | 雷场 | 雷击 | 雷暴 | 天打五雷轰 |
| **雷球 ORB** | 雷球 | 大雷球 | 环绕雷球 | 爆炸雷球 | 天降超级无敌大雷球 |
| **雷速 SPEED** | 雷速 | 闪电移位 | 极速雷风 | 闪电降低冷却 | 闪电登神 |

#### 4.20.1 两条规则（用户拍板）

1. **每条链独立进度**。以前是"每元素一个进度"，三条链共用会把整个元素卡死在 1 级
   （学了雷球 1 级后，`progress+1` 规则让三条链的 2 级全都学不了）。
   现在存的是 `progress[元素][链]`。
2. **高阶替换低阶**：法杖上每条链<b>只挂已经解锁的最高那一级</b>。
   学了「大雷球」以后法杖上就没有「雷球」了。
   见 `SpellCatalog.effectiveIds(data)`，四个同步点（登录 / learn 命令 / wand 命令 / 界面解锁包）都喂它。

> 这条规则顺带解决了引擎的**热键栏硬上限 9 格、不能翻页**的问题
> （`Keybindings.spell_hotbar_1..9`，第 10 个法术格子照画但按键是 null）：
> 三链满级时法杖上只有 3 个法术。

存档兼容：`Progress` 字段继续写（只写主链，老版本/老存档都能读），
新格式 `ChainProgress` 按**链名**做 key（加链、改枚举顺序都不会串位）。
老存档读进来主链进度原样保留、新链从 0 开始。

#### 4.20.2 每条法术怎么落地的（引擎能力边界实测）

反编译 `spell_engine-0.15.12` + 抽了 519 个真实法术 JSON 之后确定的边界：

| 想要的效果 | 落地方式 |
|---|---|
| 雷球 / 大雷球 / 爆炸雷球（飞行 + 命中/范围爆炸） | 纯 JSON：`release.target.type=PROJECTILE` + 顶层 `area_impact` |
| 天降超级无敌大雷球 | 纯 JSON：`target.type=METEOR`（`launch_height/launch_radius/extra_launch_count`）+ 大 `area_impact.radius` |
| 闪电移位 | 纯 JSON：`TELEPORT{mode:"FORWARD", forward{distance}}` |
| 雷速 +25% / 极速雷风 +70% | **自定义效果**：原版速度效果每级固定 +20%，拿不到 25%/70% |
| 环绕雷球（绕圈 + 电击） | **Java**：引擎没有"绕玩家旋转"的字段，`SpellCloud` 也不会跟随 |
| 极速雷风（雷电伤害拖尾） | **Java**：没有"沿路留伤害"的字段 |
| 闪电降低冷却（回一半蓝） | **Java**：引擎压根没有 mana 池，我们才是 mana 的所有者 |
| 闪电登神（无冷却） | **Java**：`cooldown_duration` 只能靠 haste 做除法，**永远到不了 0**，只能代码清 |

#### 4.20.3 代码结构

- `SpellCatalog`：加了 `Chain` 枚举（CORE/ORB/SPEED）+ 15 条记录；
  `chainsOf` / `of(element, chain)` / `topLearned` / `effective` / `effectiveIds`。
- `TNEffects`：4 个自定义效果（`tnc:lightning_haste` +25%、`tnc:lightning_wind` +70%、
  `tnc:orbiting_thunder_orb` 标记、`tnc:lightning_ascension` 增伤 30%）。
  用 `addAttributeModifier` 给精确数值；`MobEffectInstance` 会按 `(amplifier+1)` 放大，
  所以**雷速一个效果同时覆盖 +25%（amp0）和 +50%（amp1）**。
  ⚠️ 1.20.1 的 `addAttributeModifier` 收的是 **UUID 字符串**，不是 UUID 对象。
- `TnSpellMechanics`：光环 / 拖尾 / 回蓝 / 清冷却。
  粒子一律用原版 `ELECTRIC_SPARK`（不依赖任何 mod 的粒子注册，少一个会崩的点）；
  清冷却走 `SpellCasterEntity.getCooldownManager()`，而且必须用 `remove()`
  —— 它会把清除**同步给客户端**，不然客户端那边还在冷却、法术根本按不出去。
  引擎类全包在 try/catch 里（软依赖）。
- 界面：魔法石面板加宽到 520，法术目录**按链分三列**（15 个挤一列会跑出面板）。

#### 4.20.4 文件都放哪（改东西时看这张表）

| 内容 | 位置 |
|---|---|
| 法术本体（数值/伤害/冷却/粒子） | 整合包 `kubejs/data/tnc/spells/*.json`（**唯一事实来源**，直接改） |
| 法术图标 16x16 / 效果图标 18x18 / 投射物模型与贴图 | 整合包 `config/openloader/resources/TN-C/assets/tnc/...` |
| 法术池 + 法杖 assignment（15 个 id） | mod 的 `src/main/resources/data/tnc/...` |
| 效果数值（+25%/+70%/增伤） | `TNEffects` |
| 光环/拖尾的半径、间隔、伤害 | `TnSpellMechanics` 顶部的常量 |
| 生成脚本（一次性，纯 ASCII） | `tools/gen_tnc_spells.ps1`、`tools/gen_tnc_spell_assets.ps1` |

> ⚠️ 又一次踩到 UTF-8/GBK 的坑：`tools/*.ps1` 里写中文注释，
> Windows PowerShell 按 GBK 读会把多字节序列末尾的换行一起吃掉，
> 于是"注释行"和下一行合并 —— 表现是 here-string 没打开、整段 JSON 被当代码解析。
> **工具脚本保持纯 ASCII 是有原因的，别再犯**（两个生成脚本现在都是 0 个非 ASCII 字节）。

---

#### 4.20.5 离线验证：dev 服务器自检（2026-09-16 22:30）

在 dev 环境（有 Forge、**没装 SpellEngine**）跑 `gradlew runServer`，启动自检 **28/28 全过**，
其中新链路的四条直接证明了模型是对的：

```
[ok] catalog = 3 chains x 5 tiers : chains=3 total=15 [主链=5 雷球=5 雷速=5]
[ok] chains are independent : CORE=1 ORB=0 直接学 大雷球 -> OUT_OF_ORDER
[ok] high tier replaces low (wand content) : 已学 ORB 1+2 级 -> 法杖内容=[great_thunder_orb]
[ok] mana cost scales with max mana : 上限 210 → 20，上限 620 → 59（占上限 9.5%）
TN-C effects registered: [tnc:lightning_haste, tnc:lightning_wind, tnc:orbiting_thunder_orb, tnc:lightning_ascension]
```

- 第三条是这次的核心规则（**高阶替换低阶**）：学了雷球 1+2 级，法杖内容只剩大雷球。
- 最后一行是 mod 启动时主动打的（`TNMod.commonSetup`）—— 把"效果漏注册/改名"
  这种静默失败变成日志里一眼能看到的一行。
- 另外还做过一次**字段名对表**：把 10 个法术 JSON 里用到的每个键
  （`release.target.projectile.launch_properties.velocity`、`meteor.launch_height`、
  `area_impact.extra_radius.power_coefficient`、`action.teleport.forward.distance`、
  `cost.cooldown_duration`、粒子批次的 7 个键……）逐个和 `javap` 出来的
  `Spell$*` 内部类真实字段核对 —— 全部对得上。
  **Gson 遇到不认识的键是静默丢弃的**，写错一个字母 = 数值用默认值且毫无报错。

> ⚠️ dev 环境没有 SpellEngine，所以**硬拦截探针会跳过**，
> 投射物 / 光环 / 拖尾 / 清冷却这些依赖引擎的行为也测不到 —— 那部分只能在整合包里实测。

---

### 4.21 火系三条链（15 个法术，2026-09-18）

火系和雷系同构：**3 条链 x 5 级 = 15 个法术**，每链独立进度、高阶替换低阶、亲和力门槛。

| 链 | 1 级 | 2 级 | 3 级 | 4 级 | 5 级 |
|---|---|---|---|---|---|
| **RAY 火射线** | 火射线（穿透） | 粗火射线 | 三条火射线（三向） | 爆炸射线 | 巨大爆炸射线 |
| **BALL 火球** | 火球术 | 大火球术 | 熔岩火球（留熔岩地） | 熔岳天倾（头顶法阵） | 炎葬（脚下法阵） |
| **BURN 燃烧** | 燎肤（火伤+10% / 受伤+10%） | 灼脉（+25% / +20%） | 焚元（+75% / +35%） | 烬魂（+150% / +55%） | 殉命（+200% / +80%） |

用户拍板的细节（**2026-10-11 整条链重做成「以伤换伤」**）：**火系法术伤害提高，代价是自身受到的一切伤害提高**，
两半都随档位递增。旧的「燃血 / 原地复活 / 完全燃烧血锁+无敌」已按作者要求**全部移除** ——
新描述里 t4 写「稍有不慎便魂飞魄散」、t5 写「自身受到伤害极大幅提高」，与无敌/复活直接冲突。
自爆 已于 2026-10-05 随火球链重做**删除**（那一档换成「熔岳天倾」）。

#### 4.21.1 落地方式（哪些纯数据、哪些必须写代码）

| 法术 | 方式 |
|---|---|
| 火射线 / 粗火射线 / 爆炸射线 / 巨大爆炸射线 | 纯 JSON：**高速穿透投射物**（`perks.pierce`），爆炸靠 `area_impact` |
| 三条火射线 | `extra_launch_count: 2` + `divergence: 45` → 扇形三束 |
| 火球术 / 大火球术 / 熔岩火球 | **Java 自有实体**（`magic/fire/TNFireBoltEntity`）：命中要精确知道"是哪个法术、打了多少"才能挂焚身；引擎的 `PROJECTILE` 在伤害事件里不带法术 id ✗ |
| 熔岩火球 / 熔岳天倾 的落点 | **Java**：留一块**熔岩地**（`TNLavaFieldEntity`，与焚身不冲突、可同时跳）；熔岳天倾的火球还会爆炸 |
| 熔岳天倾 / 炎葬 | **Java 自有实体**（`TNSkyfallEntity` / `TNBurialEntity`）：跟着施法者走的 10 秒法阵（头顶 / 脚下）|
| 燃烧线 5 个伤害加成 | **Java 注册的 5 个效果**（只挂 `spell_power:fire`） |
| 燃烧线的「挨打更疼」 | **Java**：`TNFireMechanics` 挂 `LivingHurtEvent` 直接乘最终伤害（1.20.1 没有"受到伤害"这个属性，挂不了修饰符） |

> **射线没用引擎的 `BEAM`**：`BEAM` 要自带一张光束贴图（`texture_id`）、穿透语义也没验证过；
> 投射物这条路雷系已经实测跑通。以后想要真光束再换。

#### 4.21.2 数值（都在 `TNFireMechanics` 顶部两张表）

| 档 | 法术 | 效果 id | 火系法术伤害 | 自身受到伤害 | 结束后反噬（最大生命） |
|---|---|---|---|---|---|
| 1 | 燎肤 | `fire_aspect` | +10% | +10% | 20% |
| 2 | 灼脉 | `ember_burn` | +25% | +20% | 40% |
| 3 | 焚元 | `blaze_burn` | +75% | +35% | 60% |
| 4 | 烬魂 | `inferno_burn` | +150% | +55% | 80% |
| 5 | 殉命 | `total_burn` | +200% | +80% | 100% |

- **火伤**走属性 `spell_power:fire`（`AttributeModifier.Operation.MULTIPLY_BASE`），**只加火系**
- **受伤**走 `LivingHurtEvent`：`amount` 已经是过完护甲/抗性的最终伤害 ⇒ 轻甲重甲都是同一个百分比
- **结束反噬**：效果**自然结束**那一刻扣"最大生命 × 比例"，**永不致死**（保底留 1 点），
  随后给 **抗性提升 V 3 秒**（= 100% 减伤，即无敌）。
  判定靠每 tick 比对"上一 tick 还挂着哪一档"，不用 `MobEffectEvent.Expired/Remove`
  （1.20.1 两者会重复触发 ⇒ 会结算两次）；观察者/创造模式跳过。
  扣血走 `hurt()` 而非 `setHealth()`（后者绕过受伤窗口，连打会被静默丢弃）
- 三半都**取身上最高的那一档、不叠加**（`apply_mode: SET` 也保证不会两档同时挂着）
- **效果 id 一个没改**（KubeJS 那 5 个法术 json 按 id 引用效果），改的只有**显示名**与**机制**
- **（2026-10-11 移除）** 燃血 / 原地复活 / 完全燃烧血锁无敌 —— 理由见上；
  一并删掉断言"火系复活优先于倒地"的 GameTest `fireRevivalTakesPrecedenceInRealEventBusOrder`
- **（2026-10-05 移除）自爆**：原为扣最大生命 10%。火球链重做后那一档换成「熔岳天倾」，机制移到 `magic/fire/TNSkyfallEntity`

#### 4.21.3 踩过的坑：写死元素名的地方一共四处

加火系时暴露出**同一个坑的四张脸**，现在全部改成通用（以后加水/风/土只加数据）：

| 位置 | 症状 |
|---|---|
| `SpellCatalog` 的记录 | 加记录就行，本来就通用 |
| **`effective()`（法杖内容）** | 写死 `LIGHTNING` 循环 → **火法术永远进不了法杖** |
| **`MagicStoneSelfTest`** | 写死"3 链 x 5 级、总数 15" → 加火系后**自检误报失败** |
| **`MagicStoneScreen`** | 写死 `LIGHTNING` → 界面看不到火系 → 现在底部有"雷/火"切换按钮 |

#### 4.21.4 待办

- **三条火射线的精确"前/左/右各 90°"**：JSON 的 `divergence` 只能随机散射，
  没有固定偏移角。要精确 90° 得在 Java 里再发两束，入口是引擎自己的：
  `SpellHelper.shootProjectile(Level, LivingEntity, Entity, SpellInfo, ImpactContext, int)`。
  前置改动：把 `SpellInfo`/`ImpactContext` 从 `SPELL_CAST` 钩子透传到 `TnSpellMechanics`，
  发射左右两束时临时改施法者朝向再还原（同一 tick 内）。
  **在没法进游戏验证前不盲写**（`ImpactContext` 构造错会在命中时 NPE）。
- **游戏内实测**：火系 15 个法术 / 5 个效果 / 4 套机制 / 界面，全部尚未在真实引擎里跑过。

> 离线验证到什么程度：dev 服务器自检 **28/28**（dev 没装 SpellEngine，会少一条引擎相关检查），
> 整合包里是 **29/29**；火系 15 个 JSON 的结构、效果 id、投射物模型文件都有 verify 断言覆盖。

---

# 五、核心技术知识库

> 这一节是花了一整晚用 `javap` 读编译类、拆 jar、试错换来的。别重复踩。

## 5.1 SpellEngine（法术引擎）

**法术是纯数据**：`data/<namespace>/spells/<name>.json`

**顶层字段**（来自 `javap net.spell_engine.api.spell.Spell`）：
```
school (SpellSchool)  range (float)  group (String)  learn
mode  cast  item_use  arrow_perks  release  impact[]  area_impact  cost
```

**动作类型只有 6 种**（`Spell$Impact$Action$Type`）：
```
DAMAGE  HEAL  STATUS_EFFECT  FIRE  SPAWN  TELEPORT
```

**TELEPORT 的完整字段**（`javap Spell$Impact$Action$Teleport`）：
```
mode                     FORWARD | BEHIND_TARGET
forward.distance         float      ← 写死的浮点数，不支持系数/属性缩放！
behind_target.distance   float
required_clearance_block_y  int
intent                   HARMFUL | BENEFICIAL | HELPFUL
depart_particles / arrive_particles
```

**🔴 关键限制**：`distance` 不支持缩放 → **"等级越高越强"必须靠"一个等级一个法术文件"实现**。作者就是这么干的（`airslash` / `airslash_2` / `airslash_3`）。

**学派**（12 个，来自 `spell_power`，数据驱动）：
```
AIR ARCANE ARMOR BERSERKER_MELEE EARTH FIRE FROST
HEALING LIGHTNING PHYSICAL_MELEE PHYSICAL_RANGED SOUL WATER
```

**可用素材统计**（已排除闭源 `ysjx*`）：
| 素材 | 数量 |
|---|---|
| 纯数据法术（可照抄）| **183 个** |
| 施法动画 | 68 个 |
| 状态效果 | 60 种 |
| 法术音效 | 254 个 |
| 学派 | 12 个 |
| 动作类型 | 6 个 |

**法术图标**：SpellEngine 会在 `assets/<namespace>/textures/spell/<法术名>.png` 找图标（我们还没做，日志里有 FileNotFoundException）

## 5.2 SpellAnvil（法术注册台）

- mod：`spellanvil`（`org.hiedacamellia.spellanvil`）
- 方块 ID：**`spellanvil:anvil`**
- **用物品标签识别内容**：
  - `data/spellanvil/tags/items/scroll.json`（493 条，在 jar 里）
  - `data/spellanvil/tags/items/book.json`（18 条）
- **往标签里加自己的物品**：在 `kubejs/data/spellanvil/tags/items/scroll.json` 写 `{"replace": false, "values": [...]}`

**KubeJS 事件**（`SpellAnvilEvents`）：
```javascript
SpellAnvilEvents.getItemStackSpells(e => { if (e.itemStack == 'x') e.addSpell('ns:spell') })
SpellAnvilEvents.validateSpellPool(e => { if (e.itemStack == 'x') e.setPool('ns:pool') })
SpellAnvilEvents.getSpellCost(...)
SpellAnvilEvents.spellRemove(...)
```

## 5.3 数据包覆写 mod 数据（**超强能力**）

**同路径文件放在 `kubejs/data/<mod命名空间>/...` 即可覆盖 mod jar 里的版本。**

作者自己就这么干 —— 他覆写了 **132 个 mod 法术**：
```
spellbladenext 78 / elemental_wizards_rpg 18 / wizards 15 / paladins 9 / rogues 8 / archers 4
```

**推论**：连闭源 `ysjxspells` 的 385 个法术，**外壳也能覆写**（学派/冷却/耗材/动画/音效/粒子/目标）。
只有 `custom_impact: true` 的实际效果在 Java 里改不了 —— 但你可以**覆写时去掉 `custom_impact`，换成纯数据效果**，那个法术就完全归你了。

## 5.4 KubeJS

- **自动加载目录下所有 `.js`** → 新增自己的文件，不用改作者的
- 目录：`startup_scripts/`（注册物品）`server_scripts/`（逻辑/配方/掉落）`client_scripts/`（tooltip）
- `kubejs/data/` **会被当数据包加载**（namespace = 子文件夹名）
- 日志验证：`Loaded 25/25 KubeJS startup scripts with 0 errors and 0 warnings`

**卷轴命名约定**（作者的 `Scroll.js`）：
```javascript
function getScrollItemId(spellId) {
    const [namespace, path] = spellId.split(':');
    return `${path}_scroll`;     // 'tnc:blink_1' → 'blink_1_scroll'
}
```

## 5.5 车万女仆 TLM 1.5.0（模型包）

**两套格式并存**：
| 格式 | 标志 | 骨骼 |
|---|---|---|
| 旧格式 | 无 | 简单（`head`/`armLeft`）|
| **新 GeckoLib 格式** | `"is_gecko": true` | **93 根**（含手指/眼皮/头发/裙子）|

**模型包结构**：
```
tnc_pet/                                  ← 资源包根
├── pack.mcmeta
└── assets/tnc_pet/
    ├── maid_model.json                   ← 清单
    ├── models/entity/songzhikun.json
    └── textures/entity/songzhikun.png    ← 按模型文件名约定
```

**清单格式**：
```json
{
  "pack_name": "...", "author": ["..."], "version": "1.0.0",
  "model_list": [{
    "model_id": "tnc_pet:songzhikun",
    "is_gecko": true,
    "animation": ["touhou_little_maid:animation/maid.animation.json"],
    "render_entity_scale": 0.65
  }]
}
```

**游戏内换模型**：空手右键女仆 → **"Change Skin"** 按钮（`gui.touhou_little_maid.button.skin`）
**模型切换器方块**（`touhou_little_maid:model_switcher`）是**服务器展示用**的，自己玩不需要。

**TLM 依赖**：只要求 Forge≥46 + MC 1.20~1.20.2，**不内嵌 GeckoLib**，需要外部安装。

## 5.6 Blockbench

- **Java Block/Item** = 方块/物品；**Bedrock Entity** = 生物（GeckoLib/TLM）
- 新建时要选对**格式**和**MC 版本**
- **box UV** 是十字展开，占面积很大（头 9.2³ 需要 36.8×18.4）→ 手动摆极易重叠，**要算**
- 导出会**重置贴图命名空间**（写成 `"texture"`）→ 每次导出后要检查
- **`.bbmodel` 是源文件，`.json` 是产物** —— Blockbench 有自动备份（`%APPDATA%\Blockbench\backups\`，`<lz>` 压缩格式）
- 模型改动要**重新构建**才生效（F3+T 只重载构建输出）

## 5.7 用 javap 读编译类

**不要猜 API，直接读**：
```powershell
& "C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\javap.exe" `
  -p -classpath "<jar>" 'net.spell_engine.api.spell.Spell$Impact$Action$Teleport'
```
反混淆的 Forge jar（**2026-09-15 起用 47.4.22**，与工程/整合包一致；旧路径把版本号换成 47.4.13 也能用）：
`C:\Users\FDCX\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.22_mapped_official_1.20.1\forge-1.20.1-47.4.22_mapped_official_1.20.1.jar`

**写 mod 代码时的两个自查工具**（都在 `D:\ModTest\tools\`，纯 ASCII 脚本、自动识别路径）：
- `verify_mod_jar.ps1` —— 构建后跑：检查 jar 里类是否齐全、
  **`@Mod.EventBusSubscriber` 是否在**（漏了会"编译通过但静默失效"）、字节码版本、是否已装进游戏包
- `verify_spells.ps1` —— 写法术数据前后跑：字段名/枚举值是否被包里既有法术证明过 + 交付链路是否完整

---

# 六、踩过的坑与教训

| 坑 | 教训 |
|---|---|
| **沙箱写不了 `D:\blockbench`** | 现已改为 `danger-full-access`，但历史上要提权 |
| **改了资源但游戏没变化** | 游戏读 `build/resources/main`，不是 `src/`。要么重新构建，要么直接同步过去 |
| **`sync push` 覆盖了游戏改写的 4167 个 config** | 已改成时间戳感知（只推更新的）|
| **`.ps1` 里的中文乱码** | 系统是 GBK 代码页，PS 5.1 读 `.ps1` 需 **UTF-8 BOM** |
| **`pwsh` 不存在** | 底层是 Windows PowerShell 5.1 |
| **Blockbench 导出重置贴图命名空间** | 每次导出后检查 `textures` 字段 |
| **box UV 重叠导致全身花掉** | UV 要用程序算，不能手摆 |
| **模型浮空** | 脚底必须 y=0；参考模型尺度 ~39 单位高 |
| **模型和原版剑"垂直"** | 平躺 XZ 平面的模型不能用 `parent: item/handheld` |
| **KubeJS 脚本不生效** | startup 脚本要重启游戏；server 脚本可 `/reload` |
| **`api.foojay.io` 连不上** | 用本地已有 JDK，不靠 Gradle 自动下载 |
| **PCL 认不出解压出来的实例** | PCL 要求 **文件夹名 = JSON 名 = jar 名**，且 JSON 里的 `id` 也要同名；别人打包的实例常常 JSON 还是旧名（本次就是），必须改名 + 改 `id` |
| **开游戏时内存被别的程序吃掉** | PCL「自动分配内存」按剩余内存算 → 只剩 3.1 GB 就只给 3174 MB，新世界生成到 57% 必 `OutOfMemoryError`、卡死十几分钟（日志里几万条 OOM）。**PCL 内存要设固定值** |
| **怀疑 NPC/村民被 mod 换掉** | 先 `javap` 看实体类的父类，别猜：作者的 NPC 全是 `PathfinderMob`（MCA 只换 `Villager`，换不到它们）；真正被影响的是**依赖原版村民**的内容 |
| **想看存档里到底有什么实体** | `.mca` 是自定义区域格式（表头在尾部），但可以直接**暴力找 `78 9C` zlib 流并解压**，再在区块 NBT 里搜实体 id。注意 PS 5.1 的 `New-Object X($a,$b,$c)` 传参不可靠，要用 `[X]::new(...)` |

---

# 七、当前状态与待办

## 进行中
- [x] **移除 `simplyskills`** —— 2026-09-15 已在新包里完成并核实（jar / config / 技能树全没了）
- [ ] **新实例首次启动测试**（MCA Reborn + Bountiful 只装未测）——重点看：
      能不能正常进游戏、村庄有没有 MCA 村民、ELA 原本的剧情 NPC 还在不在、TPS 有没有明显下降
      （⚠️ 启动前先把 PCL 内存设成固定 6–7 GB，见 8.3）
- [ ] **魔法石（数据层 + GUI）游戏内验证**：按 E → 点玩家模型右边那颗紫色石头 →
      期望亲和力全 3 / 魔力 210/210 / 点数 1 可用 / 「小闪电」可解锁（见 4.11、4.12）
- [ ] **雷系 5 法游戏内验证**（临时入口，用完即删）：重启 → `/give @s kubejs:tnc_spark_scroll` →
      法术注册台绑定 → 施放（数据 / 卷轴 / 图标 / 标签都已 push 进游戏包，见 4.10）
- [ ] 魔法系统设计待确认数值（见设计文档第十三节；雷系数值已按第十五节落地，先试手感再调）

## 待办
- [x] 把 `tlm_custom_pack\tnc_pet`（女仆模型包）与 `vaultpatcher\`（汉化）从旧实例搬进新实例 ✅ 2026-09-15
- [x] 把归档的闪现 7 个文件重放回新包 ✅ 2026-09-15（工作区已 pull 同步）
- [x] 把 `touhoulittlemaid-1.5.2` 装回新实例（魔改版删掉了它，`tnc_pet` 模型包需要它）✅ 2026-09-15，mods 278→279
- [ ] 视需要再搬 `hotai` / `immersive_furniture` / `local` / `data`（本次没搬）
- [x] TN-C 工程 Forge 版本从 47.4.13 对齐到整合包的 **47.4.22** ✅ 2026-09-15（联网补下 mapped jar，重建通过）
- [ ] 补上闪现的法术图标 `assets/tnc/textures/spell/blink_*.png`
- [ ] `mod_authors` 占位符待填
- [ ] 剑的贴图还是测试色块（未画真贴图）
- [ ] songzhikun 贴图未画（现在用的是 UV 验证图）

## 下一步（按设计文档的阶段计划）
```
阶段 0  清理（摘 simplyskills）← ✅ 已完成（魔改版里已删干净）
阶段 1  MagicStoneData Java 数据类（亲和力/魔力/点数/进度/已学）← ✅ 已完成（2026-09-15，见 4.11）
阶段 2  雷系 5 级法术 ← ✅ 首批 5 个数据已完成（2026-09-15，见 4.10）
        └ 还差：雷系领域魔法
阶段 3  魔法石 GUI + 「花魔法点数解锁法术」← 🚧 **第一版已完成**（2026-09-15，见 4.12）
        └ 还差：石头美术、列表滚动、解锁后的施放方式
阶段 4  HUD（重绘血条 + 新增魔力条）
阶段 5  施法（法杖 + 长按数字键）← 注意：本包 client.json5 已配好「数字键施法」，可能省一半活
阶段 6  领域魔法 + 融合
```

---

# 八、重要提醒

## 8.1 三个高难度模块需要 Java
GUI、HUD（血条/魔力条）、长按数字键施法 —— 这三个**超出 KubeJS 能力**，必须写 Java + Mixin。
**所以 TN-C 那个 Forge 工程是主力，不是配角。**

## 8.2 授权
- `车万女仆` = MIT / CC BY-NC-SA 4.0（素材非商用、需署名）
- 我们的 songzhikun 骨架源自 TLM 模型 → **也是 CC BY-NC-SA**
- 东方 Project 二次创作规约：必须标注是同人作品
- 整合包里各 mod 授权各异，**分发前要逐个确认**

## 8.3 性能
- 机器 **13.9 GB 内存**，跑 278 mods 的包很紧张
- **新世界生成**会造成 `Can't keep up!`（服务器线程落后几十秒），不是 mod 冲突
- 启动游戏前关掉 IntelliJ / QQ / 浏览器

### 🔴 PCL 的「自动分配内存」是个陷阱（2026-09-15 实测）

PCL2 默认按**开游戏那一刻的系统剩余内存**来算堆大小，于是：

| 启动时间 | PCL 分配的内存 | 结果 |
|---|---|---|
| 09-13 13:45 | 7270 MB | 正常 |
| 09-13 18:01 | 6246 MB | 正常 |
| 09-14 01:27 | 6758 MB | 正常 |
| **09-15 21:05（新实例首次）** | **3174 MB**（`-Xmx3174m`）| **世界生成 OOM，卡死** |

那天开游戏前 QQ×2 / Edge×2 / msedgewebview2 / 飞书 / node 一起吃掉了约 9 GB，
PCL 日志写着「当前剩余内存：3.1G」→ 就只给了 **3.1 GB**。
278 mods 生成新世界在 3 GB 堆里必然爆。

**结论：在 PCL 里把内存设成固定值（6–7 GB），不要用「自动分配」。**
另外开游戏前把 QQ / Edge / 飞书 / IntelliJ 都关掉。

## 8.4 工作流
```
1. IntelliJ 改 D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\...
2. cd D:\ModTest\modpack && .\sync.cmd push
3. PCL2 启动 元素觉醒1.4.3-魔改版-20260915 测试
```
**IntelliJ 不能运行整合包** —— 整合包只能由 PCL2 启动。

---

# 九、版本控制与协作（2026-09-16 建立）

远端：`https://github.com/Cockr00Ach/TN-C_MC-magic-s-world`（公开）
本地：`D:\ModTest`，分支 `main`。**仓库只有 1.55 MB / 309 个文件**，clone 秒级。

## 9.1 核心原则：**只跟踪我们自己写的东西**

`.gitignore` 是这个工程的命脉。整合包里绝大多数是第三方内容，进版本库有三个问题：
**版权（公开仓库二次分发 mod 会被投诉）、体积（GB 级）、而且我们从不改它们。**

| 进仓库 | 不进仓库 |
|---|---|
| `src/` `design/` `tools/` `model-source/` | `modpack/*/mods/`（280 个 jar） |
| `modpack/*/kubejs/`（211 个文件，含作者脚本） | `modpack/*/config/`（45.9 MB，只保留我们的 openloader 覆盖层 6 个文件） |
| `modpack/*/config/openloader/resources/TN-C/` | `modpack/*/tlm_custom_pack/`（37.4 MB 女仆模型包） |
| 根目录配置 / README / 本文档 | `build/` `run/` `.gradle/` `libs/*.jar` `*.zip` |

> ⚠️ **`config` 那段不能直接写 `modpack/*/config/`** —— 一旦整个目录被排除，
> git 就不进去看，后面用 `!` 放行的规则**全部失效**。必须"排除子项、再放行子路径"（见 .gitignore 注释）。

## 9.2 朋友怎么拿到完整环境

```
基础整合包 zip（1.5 GB，网盘发）      ← 279 个 mod + config + 女仆模型包
        ＋
git clone（1.55 MB）                  ← 我们的代码 + kubejs + 法术图标
        ＋
额外两个 jar（见下）                   ← 基础包缺 / 构建产物
        ＝ 完整可运行环境
```

**⚠️ 基础包里缺 `touhoulittlemaid-1.5.2-forge+mc1.20.1.jar`**（作者没写进说明，我们当初也是手工补的）。
对比过：zip 里 **279** 个 jar，好的实例里是 **280** 个。朋友用同一个包会一模一样地缺，必须单独发。
`tnc-1.0.0.jar` 他自己 `gradlew build` 就有，不必发。

朋友的步骤（已在 `README.md` 里）：
```powershell
git clone https://github.com/Cockr00Ach/TN-C_MC-magic-s-world.git
cd TN-C_MC-magic-s-world
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1   # 取回编译用 jar
.\gradlew.bat build
powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-to-pack.ps1 -Wait
```

## 9.3 踩过的三个坑

**① 首次提交时 `.git` 悄悄涨到 1545 MB。**
仓库根目录放着整合包原包 `元素觉醒1.4.3-魔改版-20260915.zip`（1547 MB），
`git add -A` 把它收了进去。发现得早（只有一个提交、还没配远端）→ **删掉 `.git` 重建**最干净。
现在 `.gitignore` 挡了 `*.zip / *.7z / *.rar`。

> **铁律：`git add -A` 之后一定看一眼文件数。** 健康值 ~309 个文件 / ~1.6 MB；
> 哪天变成几十 MB 或几百个文件，就是有大件混进来了。

**② GitHub 直连被墙。**
`github.com:443` TCP 四次全失败（DNS 解析正常）。但机器上跑着 Clash（内核 `ninja-mihomo`）
在 `127.0.0.1:6789`，系统代理也是开的 —— **而 git 默认不用 Windows 系统代理**。
所以只给 github 配了代理（不影响别的站点）：
```
git config --global http.https://github.com.proxy http://127.0.0.1:6789
```
⚠️ **Clash 没开时 git 连 GitHub 会报 `Connection was reset`** —— 那是代理没开，不是仓库坏了。

**③ 工具脚本里一堆硬编码绝对路径。**
`verify_mod_jar.ps1` 等 6 个脚本写死了 `D:\ModTest\`、`E:\download\...`，
甚至 `C:\Users\FDCX\...\javap.exe`；`modpack\sync.ps1` 写死了
`E:\download\正式版 2.12.6.1\.minecraft\versions`。
**朋友克隆到别的路径就全废。** 已全部改成自动推断：
- `tools\_common.ps1`：统一的仓库根 / 整合包 / 实时实例 / java 进程分类 / GBK 日志读取
- `Find-TncJdkTool`：javap/javac/jar 从 `JAVA_HOME` → 启动器自带 runtime → PATH 里找
- `sync.ps1` 支持 `-LiveRoot <路径>` 或环境变量 `TNC_LIVE_ROOT`，否则自动搜索

## 9.4 日常协作流程

```powershell
git pull                       # 动手前先拉
git checkout -b 你的分支名       # 一人一分支，别都往 main 直接写
# ...改代码... 至少保证 .\gradlew.bat build 能过
git add -A ; git status        # ★ 确认文件数还是 ~309
git commit -m "做了什么"
git push -u origin 你的分支名
# 再到 GitHub 发 Pull Request，另一个人看过再合进 main
```

**最容易冲突的三个文件**，改之前互相说一声：
`PROJECT-STATE.md`（两人都在记）、`kubejs/data/tnc/spells/*.json`（同一批数值）、
`Config.java` / `SpellCatalog.java`（数值表与法术目录）。

## 9.5 ★ 提交守卫：机械拦截 mod / 大文件（2026-09-16）

**9.3 的坑①不能靠记性防**（`.gitignore` 只在"有人记得写规则"时有用），所以做了自动化：

```
tools/git-hooks/pre-commit   ← git 的 pre-commit 钩子壳
tools/check-staged.ps1       ← 真正的检查（也可手动跑）
```

**每个 clone 要开一次**（`core.hooksPath` 是 per-clone 的）：
```
git config core.hooksPath tools/git-hooks
```
`tools\fetch-libs.ps1` 结尾会检查并提示这一条 —— 但**不会擅自改你的 git 配置**。

**拒绝规则**：`mods/*.jar`、`*.zip|7z|rar`、`libs/*.jar`、`tlm_custom_pack/`、`.connector/`、
`build/` `run/` `.gradle/`、`*.class|dll|so`、以及**任何 > 2 MB 的文件**（兜底）。
被拒时退出码 1，并打印原因 + 撤销方法（`git reset HEAD <file>`）。

**实测**（不是写完就交）：塞入 3 MB 文件 + 用 `git add -f` 强行暂存一个 `mods/*.jar`
→ 提交被拒，两类问题都列出（`[forbidden]` / `[too big]`）；清理后正常提交放行，
钩子自己打印 `check-staged: 4 file(s), 0.02 MB - ok`。

**踩到的一个坑（值得记）**：钩子输出里原本每行都被包成
`System.Management.Automation.RemoteException`，看着像钩子坏了。
根因是 **empty-string 输出**（`Write-Host ''` / `Write-Output ''`）在
"MSYS sh → powershell" 这条路上会被转成异常对象。
**用 `Write-Output ' '`（一个空格）代替空行就干净了。**
（注意：在 pwsh 里直接跑复现不出来，只在 git 钩子这条路上出现。）

> **健康基线：约 311 个文件 / 约 1.6 MB。** 钩子是兜底，习惯仍是第一道：
> `git add -A` 之后扫一眼 `git status` 的文件数。



---

# 六、引擎能力补充（2026-09-18 铺六系时挖到的）

> 这一节是"查一次省十次"的发现，写代码前先看这里，别重复挖。

## 6.1 三种法术模板（铺骨架时反复用到）

| 模板 | 结构 | 用在哪 | 特点 |
|---|---|---|---|
| **投射物** | `release.target.type=PROJECTILE` + `launch_properties` + `projectile.client_data` + 顶层 `area_impact` | 火球/水球/土弹/暗夜之手/火射线/风刃 | 飞出去 + 命中爆炸 |
| **持续场** | `release.target.type=CLOUD` + `cloud.volume.radius` + `time_to_live_seconds` + **`impact_tick_interval`** | 土泥/黑雾/雨滴/土动 | **留在场上，每 N tick 重新施加 impact** |
| **自身增益** | `release.target.type=SELF` + `impact[].action.type=STATUS_EFFECT` | 燃烧/风速/以伤换伤/土环/风球 | 纯 buff，零 Java |

**判据**：文档里写"持续 / 在其中 / 区域内"→ 用 **CLOUD** ✗ 别用 AREA（AREA 只打一发 ✗）。

## 6.2 `CLOUD` 的字段（javap：`Spell$Release$Target$Cloud`）

```java
String entity_type_id;              // 可选，用实体承载
AreaImpact volume;                  // ★ 半径在这里（volume.radius + extra_radius）
float time_to_live_seconds;         // 存在多久
int impact_tick_interval;           // ★ 每 N tick 重新施加 impact 列表
int delay_ticks;                    // 生成延迟
EntityPlacement placement;          // 放哪
Sound presence_sound;               // 存在时的循环音
ClientData client_data;             // { light_level, particles[], model }
Spawn spawn;                        // { sound, particles[] }（生成瞬间）
```

真实样例：`archers` 的 `entangling_roots.json`（radius 3.5 + interval 15 + 状态效果）。

## 6.3 `AREA` 目标：半径是**顶层 `range`**

`Spell$Release$Target$Area` 里**没有 radius** —— 半径读的是法术顶层的 `range` ✓
（对照 `elemental_wizards_rpg` 的 `aqua_springwater`：`range: 6` + `type: AREA`）。

## 6.4 `BEAM` 没被采用（但字段已知）

`Spell$Release$Target$Beam`：`texture_id`（要自带光束贴图！）、`color_rgba`、`width`、`flow`、
`block_hit_particles[]`。当时因为"要自备贴图 + 穿透语义没验证"改用了穿透投射物；
以后要做真光束可以回来用。

## 6.5 投射物模型的朝向：**"射线像火球"的真正原因**

`Spell$ProjectileModel`：`model_id` / `scale` / `light_emission` / `rotate_degrees_per_tick` /
`rotate_degrees_offset` / `use_held_item` / **`orientation`**。

`Orientation` 三个值：`TOWARDS_CAMERA`、`TOWARDS_MOTION`、`ALONG_MOTION`。

**细长模型（射线/长矛）必须指定 orientation** ✗ 否则长轴不对齐弹道 → 横着糊成一坨 →
看起来"像火球" ✗。真实样例（`spellbladenext` 的 `rebuke` 长矛）用 `TOWARDS_MOTION` ✓。

## 6.6 原版效果优先（省掉一堆自定义效果类）

实测可用的原版效果：`absorption`（护盾）、`resistance`（减伤）、`regeneration`（回血）、
`blindness`（致盲）、`slowness`（减速）、`speed`（加速）、`strength`（增伤）、`wither`（自伤）、
`hunger`、`fire_resistance`、`night_vision`、`glowing`、`levitation`、`weakness`。

**只有"精确数值的自定义增益"才必须写效果类** ✓（例如风 +300% 速度、火 +200% 火伤，
原版效果都是固定 20%/级，拿不到这些值）。

## 6.7 学派名对照（写错就静默不生效 ✗）

| 我们的元素 | 引擎 school | 属性 |
|---|---|---|
| 雷 | `LIGHTNING` | `spell_power:lightning` |
| 火 | `FIRE` | `spell_power:fire` |
| **风** | **`AIR`**（不是 WIND ✗）| `spell_power:air` |
| 水 | `WATER` | `spell_power:water` |
| 土 | `EARTH` | `spell_power:earth` |
| 光 | `HEALING` | `spell_power:healing` |
| **暗** | **`SOUL`**（不是 DARK ✗）| `spell_power:soul` |

## 6.8 音效 id（写错就是静音 ✗）

引擎自带一套 `spell_engine:generic_<school>_casting` / `_release`，
但 **风系只有 `generic_wind_charging`**（没有 casting/release ✗）。
其余可直接用原版音效（`entity.wither.shoot`、`entity.general.splash`、
`block.stone.break`、`weather.rain.above` 等）。

---

## 七、这几轮补充的方法论（都踩过）

### 7.1 通用机制可以借，元素专属的数值加成不能借

| 借用 | 对不对 | 理由 |
|---|---|---|
| 水缚 / 土泥 借风系的 `gale_slow` | ✅ 对 | 减速是通用机制，和元素无关 |
| 暗系借火系的"燃血"效果 | ✅ 对 | 自伤换力量是通用机制 |
| **暗系借火系的"火伤加成"** | ❌ **错** | 加的是 `spell_power:fire`，暗系法术吃的是 `soul` ✗ —— 等于白扣血 |
| **暗系借原版 `strength`** | ❌ **错** | 只加近战，法术伤害走 `spell_power` ✗ |

**判据**：借之前先问"这个效果加的是什么属性/机制" ✓ —— **元素专属的数值加成必须自己写** ✓（于是有了 `dark_power`）。

### 7.2 加枚举值前必须先全项目搜它的 switch

`MagicStoneLearning.Result` 加一个值，我以为只有 2 处 switch，**实际全项目 4 处** ✗：
`MagicStoneScreen.shortState` / `MagicStoneScreen.stateText` / `MagicStoneLearning.describe` / `MagicStoneCommand.stateText`。
漏一处就是"枚举不穷尽"编译失败 ✗（我真的提交过一个编译不过的版本 ✗）。

**正确流程**：`grep` 出所有 `switch (该枚举)` → 一起改 → 才有资格提交 ✓。
**更保险的做法**：先给各处 switch 加 `default` 兜底（一次改动、两个提交），再加枚举值 ✓ —— 把"必须一次改对 4 处"拆成两步低风险改动 ✓。

### 7.3 生成出来的文件，要改就改生成器

直接手改 JSON：只改到 1/5 个文件 ✗、拼出多余的空效果项 ✗、下次重跑生成器还会被覆盖 ✗。
改生成器再重跑：5 个文件全对 ✓、格式统一 ✓、以后改一处就能全部重生成 ✓。

### 7.4 命令里必须带"编译失败就不提交 / 自动回滚"

这轮之前，我有过**编译不过还提交**的事故 ✗。现在的命令模板：

```powershell
$out = & .\gradlew.bat build --console=plain 2>&1
$bad = ($out | Select-String -Pattern 'BUILD FAILED|error:').Count
if ($bad -gt 0) { git checkout -- <改动的文件>; exit 1 }   # 回滚，保住可编译状态
git commit ...
```

加上之后**再没有过坏提交** ✓，而且它当场拦住了 4 次编译失败 ✓。

### 7.5 "观感不对"先查字段，别急着重画美术

"射线像火球"折腾了几轮美术 ✗，真正原因是投射物模型**缺 `orientation` 字段** ✗ ——
长轴没对齐弹道，横着糊成一坨。加一行 `"orientation": "TOWARDS_MOTION"` 就解决了 ✓。

**顺序**：先问"是不是缺字段 / 值不对 / 资源没生效"，再考虑美术 ✓。

### 7.6 不要用文件名/印象去数东西

- 用文件名正则数"水系有几个法术"→ 得出 14 个（真实 15 个）✗ —— 要**解析 JSON** ✓
- 凭记忆数 switch / 调用点 → 漏改、编译不过 ✗ —— 要 **grep** ✓
