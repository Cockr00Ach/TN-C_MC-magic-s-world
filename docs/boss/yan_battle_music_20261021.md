# 公孙衍战斗音乐

公孙衍（迷失，`tnc:yan_dark`）的战斗 BGM 换为用户提供的 Crywolf 全阶段音频。
原音频完整保留，转成双声道、44100 Hz 的 OGG/Vorbis，约 9 分 17 秒、9.7 MiB。
声音事件为 `tnc:music.boss.yan_crywolf`，以流式方式读取，由游戏的“音乐”音量控制。

沿用现有 Boss 音乐通道：附近 56 格范围内检测到衍时停止旅行音乐并开始播放；
战斗持续时播完重播，离开 Boss、Boss 移除或退出世界时停止。
第一、二阶段播放同一完整曲目，换阶段不会重启。旅馆和赌场原有音乐优先级保持原样。

`tools/prepare_yan_boss_music.py` 以最新正式包为底包，仅修改音乐控制器的曲目常量、
合并 `sounds.json` 的单个事件并加入音频。控制器其他方法及无关 JAR 条目逐字节保留。
`tools/WireYanBossMusic.java` 用于对正式包做同等常量更新，保留并行修复。

本地音频沿用旅行音乐的管理方式，保留在本机与装机包中，不提交 Git。
重新导入时运行准备工具，传入 `--audio <源音频> --ffmpeg <支持Vorbis的ffmpeg>`、
`--live <正式实例目录> --output <准备输出目录>`；准备后的 `manifest.json`
可交给 `tools/install_dungeon_mobs.ps1 -ManifestPath <清单>` 安装并备份、校验。

验证：整段 OGG 完整解码通过；音频事件使用 `stream: true`；正式包修改仅限上述三项。
尚未在游戏里试听，不将解码检查作为实际混音或听感验收。
