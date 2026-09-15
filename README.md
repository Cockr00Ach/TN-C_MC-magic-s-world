# TN-C（元素觉醒 · 魔法系统改造）

Minecraft 1.20.1 / Forge 47.4.22 的魔改工程。给整合包 **元素觉醒 1.4.3** 加一套自研的
**魔法石 + 法杖** 体系，取代原来"卷轴 + 法术注册台"那一套。

当前阶段：**雷系 5 个法术已可用**（小闪电 / 雷场 / 雷击 / 雷暴 / 天打五雷轰），
魔力值真正参与施法（施法前硬拦截，不是事后补扣）。

---

## 一、这个仓库里有什么 / 没有什么

| 在仓库里（我们的成果） | 不在仓库里（第三方/产物） |
|---|---|
| `src/` Java mod 源码 | `modpack/*/mods/` —— 280 个第三方 mod jar |
| `design/` 设计文档 | `modpack/*/config/` 绝大部分（45 MB，整合包自带） |
| `tools/` 工具脚本 | `modpack/*/tlm_custom_pack/`（37 MB 女仆模型包） |
| `PROJECT-STATE.md` 交接文档 | `build/`、`.gradle/`、`run/` 构建与运行产物 |
| `modpack/*/kubejs/data/tnc/` 我们写的法术 | `libs/*.jar`（第三方 remap jar，用脚本取） |
| `modpack/*/config/openloader/resources/TN-C/` 法术图标 | |

**所以：仓库 + 你自己那份整合包 = 完整环境。** 不需要互相传 mod 文件。

> ⚠️ **绝对不要**把 `mods/` 里的 jar 提交进来 —— 那是别人的版权内容，而且会让仓库暴涨到 GB 级。
> `.gitignore` 已经挡住了，但 `git add -f` 能强行绕过，别那么干。

---

## 二、第一次跑起来

前置：**JDK 17**、一份整合包（放在任何位置）、PowerShell。

```powershell
git clone <仓库地址>            # 克隆到任意路径都行，脚本不再假设 D:\ModTest
cd <克隆目录>

# 1) 从本地整合包里取回编译所需的两个第三方 jar（它们是 compileOnly，不在 git 里）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1
#    如果脚本找不到整合包，就显式指路：
#    ... -File tools\fetch-libs.ps1 -PackDir "X:\...\<整合包>\mods\.connector"

# 2) 编译
.\gradlew.bat build

# 3) 校验产物（会检查一堆"编译通过但运行时静默失效"的坑）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify_mod_jar.ps1
```

### 装进游戏并验证

```powershell
# 游戏必须完全关闭（脚本会拒绝在 java 运行时覆盖 jar）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-to-pack.ps1 -Wait

# 启动游戏 → 进一个世界（探针在世界加载时才跑）→ 然后：
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-pack-verdict.ps1
```

期望看到 `PASS  mana gate probe 6/6`。

---

## 三、怎么玩（游戏内）

需要作弊/OP（命令权限等级 2）。

```
/tnc points add 10              赠送魔法点数（调试用）
/tnc learn "tnc:spark"          解锁法术（会同步进法杖）
/tnc magic                      查看魔法石数据
/tnc spells                     看法术目录
/tnc wand                       同步/补发法杖
/tnc engine                     引擎接线状态 + 施法事件账本
/tnc gatetest                   硬拦截实测（三级阶梯）
/tnc cast "tnc:spark"           直接放一个法术（不依赖法杖/键位）
/tnc selftest                   数值/门槛/NBT 自检
```

**施法**：手拿**魔法法杖**，槽位 1 = 右键，槽位 2–5 = 数字键 2–5，**按住约 0.5 秒**。

三条硬性条件（缺一放不出来，会有红字提示）：**拿着法杖** + **已在魔法石解锁** + **魔力够**。

---

## 四、几个人一起改代码的约定

```powershell
git pull                     # 动手前先拉，别在旧版本上写
git checkout -b 你的分支名    # 一人一个分支，别都往 main 上直接写
# ...改代码...
.\gradlew.bat build          # 至少保证能编译
git add -A ; git commit -m "做了什么"
git push -u origin 你的分支名
# 然后在 Gitee 上发起合并请求（Pull Request），另一个人看过再合进 main
```

**最容易被抢改的文件，改之前先说一声**：

| 文件 | 为什么 |
|---|---|
| `PROJECT-STATE.md` | 两个人都在往里记东西，最容易冲突 |
| `modpack/*/kubejs/data/tnc/spells/*.json` | 同一批法术数值 |
| `magic/Config.java` / `SpellCatalog.java` | 数值表与法术目录 |

真冲突了不要慌：`git status` 会列出 `both modified` 的文件，打开会看到
`<<<<<<<` / `=======` / `>>>>>>>` 三段，留下你要的、删掉标记，再 `git add` 即可。
**拿不准就先 `git stash` 把自己没提交的改动收起来，或者直接问另一个人。**

---

## 五、更详细的背景

`PROJECT-STATE.md` 是这个工程的完整交接文档：**踩过的坑、引擎行为、数值设计、
以及每条结论是怎么验证出来的**。改代码前值得先扫一遍 ——
里面记录了好几个"编译通过但运行时静默失效"的陷阱（mixin / spell_assignments /
客户端-服务端同步），照着它走能省很多时间。
