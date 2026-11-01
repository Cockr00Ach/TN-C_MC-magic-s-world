# 水墨三相战斗音乐与施法啸叫

## 曲目与响度

| 阶段 | 完整曲目 | 时长 | 转换后综合响度 |
| --- | --- | --- | --- |
| 鹤 | 大漠行 | 98.41 秒 | −18.02 LUFS |
| 翼虎 | 火焰帝皇以骸 BOSS战 | 299.03 秒 | −18.09 LUFS |
| 墨龙 | 骤雨狂风 · 基塔布鲁 BOSS战 | 217.88 秒 | −18.12 LUFS |

三首 AAC 来源是作者提供的本地文件。两遍 EBU R128 测量和响度处理，目标 −18 LUFS、真峰值上限 −2 dBTP，Vorbis 编码后再次测量。保留完整曲目及内部强弱变化，流式读取并循环；三首之间的实测响度跨度为 0.10 LU。原始音频保持原样，派生音乐目录加入 Git 忽略，不自动发布作者的本地歌曲。

## 播放机制

服务器每秒同步附近 Boss 的当前阶段，晚加入、未追踪到龙实体的玩家同样能收到音乐状态。完整三相试炼的转场等待期保留上一阶段音乐，新形态现身时切换；也支持原有命令单独召唤的三个 Boss。附近无 Boss、玩家死亡、离开维度或退出时停止。阶段切换使用约 2 秒交叉淡入淡出，歌曲不会每次施法重新开始。

战斗音乐使用「音乐」滑块并遵循主音量；播放时阻止旅行、原版等其他背景音乐叠加。静音时隐藏曲名并停止该曲，恢复音量后重新播放当前阶段；资源重载后重新读取。音乐的服务器心跳超过 4 秒未收到时自动释放音乐通道。

左上角显示“正在播放”及实际曲名，包括出场特写期间；曲目切换同步更新，停止播放和静音时隐藏，F1 隐藏 HUD 时也隐藏。

## 施法音效来源

- 鸟啸：[Peacock scream](https://opengameart.org/content/peacock-scream)，popthebubbles4 / qubodup，CC0；降调、滤波和淡出，作为风格化鹤啸，不冒称真实鹤的野外录音。
- 虎啸：[Tiger Roar](https://freesound.org/people/lauramellis/sounds/263115/)，lauramellis，CC0；使用公开 HQ 预览，截取、滤波、淡出。
- 龙啸：[CC0 Deep Monster Roar](https://opengameart.org/content/cc0-deep-monster-roar)，trazzz123，CC0；低沉怪物啸叫处理成幻想龙啸。

三段音效均为单声道，可定位，随施法起手和阶段出场触发，采用怪物音量分类和 96 格声音范围。每只 Boss 至少隔 30 tick 再次啸叫。音效目标约 −20 LUFS，短叫声采用较宽的门控响度容差，同时校验编码后真峰值，避免过尖或削波。完整来源和处理记录随资源写入 `assets/tnc/ink_audio_credits.json`。

## 生成与验证

导入命令：`python tools/import_ink_boss_audio.py --ffmpeg <完整版ffmpeg.exe> --inbox <作者AAC目录> --roars work/ink-audio`。

响度与文件校验报告：`work/ink-audio/normalization-report.json`、`assets/tnc/ink_boss_audio.json`。资源测试检查完整歌曲时长、响度、真峰值、OGG 文件、流式事件及啸叫许可记录。原有三相独立客户端审查现增加每个出场镜头中实际 SoundManager 播放状态与左上角标题截图，战斗结束后检查音乐通道已释放。
