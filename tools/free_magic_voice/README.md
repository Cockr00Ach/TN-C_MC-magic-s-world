# 自由魔法离线语音

这个目录负责把按键期间的本地麦克风音频转成中文文本。客户端按住吟唱键时打开默认输入设备，松开后关闭录音并取得整句；切换世界、取消或出错会丢弃本次结果。辅助进程从标准输入读取 PCM，从标准输出返回逐步转写和最终文本。按住吟唱最长 20 秒。运行时不联网、不保存录音。首次吟唱启动识别进程，之后的吟唱复用已加载模型。停止使用 45 秒、切换世界或取消时清理相应进程；等待期间麦克风关闭。

只需要说话的玩家安装语音运行时。联机字幕由 TN-C 同步，观看字幕的玩家可以没有语音运行时。

## 安装

已验证 Windows x64、Python 3.13 和 Forge 1.20.1。需要 Python 3.11 以上以及所选平台的 sherpa-onnx wheel。

```powershell
python tools/free_magic_voice/install.py --target '<实际 Minecraft gameDir>/free-magic-voice'
```

安装器使用独立虚拟环境，安装固定版本的依赖并下载约 21 MB 的普通话模型。它校验模型包，安装模型前用包内真实中文语音测试流式结果。离线准备好官方模型包时可以传 `--model-archive <包路径>`，省去模型下载。安装目录之外也可通过 JVM 参数 `-Dtnc.freeMagicVoiceRuntime=<绝对路径>` 指定运行时。

客户端使用 Java Sound，尝试 16、48、44.1 kHz 单声道输入。没有输入设备、系统关闭麦克风权限或语音运行时不可用时，会在游戏内提示。第一次按键到字幕出现还包含本地模型加载和模型分段等待；延迟及术语识别效果受设备、语速和背景噪声影响。

引擎与模型来自 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 及其 [普通话流式 Zipformer CTC 模型](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/zipformer-ctc-models.html#sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01-chinese)。

吟唱时会在整句转写里查找形态词和近音词，无需加「释放」等前缀。例如「我最喜欢的睡人啊」中的「睡人」会识别为「水刃」。同音且无法区分的形态仍需要说出明确的关键词。

整句字幕由中文流式模型提供。安装器还会校验并安装约 5 MB 的 WenetSpeech 关键词模型。当整句没有辨认出水形时，游戏可使用它听到的单一形态。水龙卷、水泡囚笼优先于各自的短词。关键词模型文件缺失或校验失败时，整句语音转写仍可用。水剑和水箭同音，语音兜底使用「水长剑」和「水弓箭」区分。 [关键词模型](https://www.modelscope.cn/models/pkufool/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01)

已有模型文件时，可传 `--keyword-model-dir <目录>` 使用本地副本。文件名、上游版本和 SHA256 在 `keyword_model.json`，安装后位于 `free-magic-voice/model/keywords`。

## 维护水形语音词表

`src/main/resources/data/tnc/free_magic/water_speech_words.tsv` 保存水形关键词、普通话拼音和精确别名。新增水形读音后，用以下命令更新随 JAR 发布的读音表和许可证。

```powershell
python tools/free_magic_voice/generate_water_speech_pinyin.py
```

生成器校验固定版本的上游文件，`--check` 可以验证输出。读音来自 [pinyin-data v0.16.0](https://github.com/mozillazg/pinyin-data/tree/fe29ad32bdf352b31a02b9936e537f575432e9a6)，按 MIT 许可随读音表发布。
