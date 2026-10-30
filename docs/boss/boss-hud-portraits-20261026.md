# Boss 血条头像

响应用户对线稿徽记过于简陋的反馈，把火山巨龟之外的十二枚图标替换为有明暗、材质和厚度的头像/浮雕贴图。血条主体、右端纹样、数字、阶段规则和全部战斗逻辑沿用上版。

## 图像来源与绘制

十一枚头像从正式已安装模组的对应头部几何与纹理离线烘焙，保留模型本身的头盔、虎纹、獠牙、冠羽和龙角龙须。使用逐三角形深度缓冲与逐纹素 UV 采样，避免把贴图平均成单一色块。龙头使用正式自由曲面网格中的 head、jaw、mane、beard、whiskers 五组，共 67408 个源三角形。水墨头像调整了明暗曲线，增加很窄的轮廓光，保持黑白灰。

阿波罗原祭司纹理含诊断色块，因此图标单独制作金色太阳浮雕：环形金属台阶、长短交错的光芒、嵌珠与太阳面具，离线栅格化为同一套静态图集。没有改动 Boss 本身的旧模型或贴图。

制作脚本：`tools/bake_boss_hud_portraits.py`。源数据使用的正式模组 SHA-256：`239411A9FE2413BBF91AF66BD3B5E5DC9D34D9524C1C556D63E970C9E477C096`，对应本次安装备份里的旧模组。该脚本重新运行时须继续使用匹配的源模组，避免静默混用后续模型版本。

十二枚头像保存在 `art/boss-hud-portraits-20261026/`，来源明细在该目录 `sources.json`。游戏图集 `assets/tnc/textures/gui/boss_hud/portraits_v1.png` 为 1024×768 RGBA，约 666 KiB，附线性过滤元数据；每枚图标的游戏尺寸为 36 GUI 像素。一帧只画静态图片，不在 HUD 渲染实体、生成粒子或重新解码模型。

## 检查

- 原生客户端 `failures=[]`，覆盖十三位 Boss 的图库、小分辨率与实际两个 Boss 同屏；实际绘制 1876 次。外部普通血条未被拦截，实体离场后缓存清零。
- 四张截图逐张查看，确认头像、血条和数字互不遮挡。`work/boss-hud-portraits-client-20261026/verified.json` 是验证记录。
- 火山巨龟一整行与上版逐像素相同。其余血条主体与右侧纹样逐像素相同。缩放文字的栅格边缘有极少量采样差异（两页分别 22、33 像素，最大通道差 28），没有文字位置或内容变化。
- 图集十二格均非空、具透明边缘且各不相同。未出现图集缺失警告。
- 合并包 96 个相关类、1131 个方法通过 ASM 校验。

头像预览：`docs/previews/boss_hud_portraits_20261026.png`。实际游戏截图：`work/boss-hud-portraits-client-20261026/screenshots/`。

## 安装

已安装到正式整合包，仅替换 `BossBarEngraving.class`，新增 `BossHudPortraits.class` 与图集/元数据两项资源；17085 个其他条目按字节保留。没有修改存档、战斗、网络、Boss 最大血量或光影设置。

安装清单：`work/boss-hud-portraits-install-20261026/manifest.json`。

备份：`E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915/backups/tnc-boss-hud-portraits-20261026-004942`。

已安装模组 SHA-256：`6F5DE85B06322A7D09B7EB601DBD4DFB90432DAC0CFC34D675ED8B5AC05EBCE6`。重启游戏生效。
