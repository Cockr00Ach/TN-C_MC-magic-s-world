# 墨龙舒展飞行与可见性修订（2026-10-19）

本记录取代 v2 的盘曲模型飞行动作。三倍本体比例不变，两招和下颌动作保留。

## 查明和修订

- 正式日志中未见墨龙 mesh 或着色器缺失报错；用户渲染距离为 32，模拟距离为 6。
- v2 召唤锚点在前方 90 格、上方 30 格，加上盘曲模型龙头高度，龙头约高出玩家 78 格，容易不在正常平视画面内。这是代码中可确认的可见性问题，不等于证明正式包不存在其他性能瓶颈。
- 原飞行只是盘曲精模在移动，并未舒展；已按原中心线弧长重新映射全身网格，近景仍保留全部 140,414 面及龙角、鳞片、龙须、五趾四肢。原始盘龙工程 SHA256 不变。
- 新姿态横向长身、龙头前伸，GPU 波动从颈部传向尾梢；上下及侧向波动，不是整体刚性摆动。躯干弧长约 66.11 模型单位，本体仍乘 3。
- 新运行时网格静态范围约 X[-11.24,63.33]、Y[2.02,9.55]；动画裁剪边界按完整长身及朝向计算，不只按头部碰撞箱。
- 新召唤前方 72 格、锚点高 4 格；平常离地巡游目标为地形上方 8 格。龙头正常高度大幅降低，不再需要抬头近四十度才发现。
- `/tnc ink dragon_find` 给出已加载区域内最近墨龙的坐标和距离。
- 在进入世界时预加载共享 mesh/VBO，避免第一次看到墨龙才准备资源；可选兼容 EntityCulling 的公开可见性 API，只处理墨龙，不关闭全局实体裁剪或修改玩家配置。
- 近/中/远三角面为 140,414 / 67,794 / 32,506；运行时网格约 11.55 MB，先前约 17.45 MB。远景不再使用约 9.7 万面。
- 避障先用区块最高非空 section 排除纯空气，再检测可能有障碍的局部区域，不再每 tick 扫描巨大空体积；头部只在已加载区块移动，视觉长尾跨出加载边缘不会强制加载新区块或卡住头部。

## 验证与文件

- 330 项单元测试通过；包含舒展姿态几何边界、全朝向裁剪、召唤视角及可选裁剪接口兼容。
- 24 项实际服务器 GameTests 通过；日志 `work/ink-dragon-extended-flight-final-tests.log`。
- 原生 GPU 渲染两种相位、进入世界预加载验证：`work/ink-dragon-extended-flight-native-report.json`。
- 客户端及生产打包日志：`work/ink-dragon-extended-flight-native-final.log`。
- 可编辑的新飞行姿态：`model-source/ink-celestial-dragon/ink_celestial_dragon_flight.bbmodel`；旧盘龙工程另存保留，不覆盖。
- 原生截图：`model-source/ink-celestial-dragon/preview_extended_flight_v3.png`。

这不是完整整合包 FPS 基准；第三方裁剪兼容用本机 JAR 的公开接口检查和独立接口测试验证，开发客户端没有加载整套正式包。

## 安装

已安装至正式包 `E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/mods/tnc-1.0.0.jar`，旧 JAR 完整备份，安装后再次读取哈希校验通过。

- 新版 SHA256：`5EC2A3E9A09FAA5AADEEC446204353B36C1F00B4056FB0BEBB3FDF74E669364F`。
- 备份：`D:/ModTest/work/install-backups/ink-dragon-flight-v3-20261019-204439/tnc-1.0.0.jar`。
- 备份 SHA256：`FC878CB736526EE0D6B469ECA1B84B8F4AA549FF7089FAB2AC15B62927D2E0EA`。
- 合并报告：`work/ink-dragon-flight-v3-merge-report.json`，更新 90 项水墨内容，16,671 项无关条目逐字节保留。
- 原生报告：`preloadedBeforeFirstDragon=true`，全部 3 级 LOD 已准备，两种动画相位可见，渲染失败列表为空。

安装时正式游戏已退出，没有改动正式玩家存档。重启加载新版即可，已有墨龙也会使用新飞行网格；`/tnc ink dragon_find` 定位已加载区域内旧实体。
