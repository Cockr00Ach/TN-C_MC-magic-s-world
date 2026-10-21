# 土系与风系 Boss 音乐

本轮使用用户提供的两份本地音频，转为双声道、44100 Hz OGG/Vorbis：

| Boss | 曲目 | 时长 | 声音事件 |
| --- | --- | --- | --- |
| 磐岳·岩枪古像 `stone_lancer` | We Are The Dawn | 3:11 | `tnc:music.boss.earth_dawn` |
| 御风将军 `wind_general`、翠岚武尊 `wind_warden`、苍岚战将 `canglan_warlord` | GUNDAM／菲尼克斯 | 3:50 | `tnc:music.boss.wind_phoenix` |

衍的 Crywolf 与现有旅馆、赌场和旅行音乐继续保留。Boss 曲目使用游戏音乐音量、
流式读取，在附近 56 格内检测活着的对应实体时播放，结束后自动重播。
Boss 死亡或离开检测范围时停止；战斗阶段改变不重启音乐。
同时存在多个 Boss 时，优先保持当前曲目；当前 Boss 离开后选择最近的其他 Boss。
攻击实体、已死亡 Boss 和普通小怪不会触发这些曲目。

安装以当前正式包为底包，只替换音乐控制器的 `tickBossMusic` 方法，加入
`BossBattleMusicSelector`，合并两个声音事件并加入两份 OGG。控制器其他方法、
衍的音频及无关 JAR 条目逐字节保留。工具为 `prepare_element_boss_music.py`
和 `WireBossMusicMethod.java`，生成的清单由现有安装器备份、校验后部署。

两段音频均通过完整解码检查。音频沿用已有本地音乐管理方式，不上传 Git；
实际游戏内音量与听感仍待试玩。
