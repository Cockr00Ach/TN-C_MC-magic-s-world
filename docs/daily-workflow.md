# 日常命令速查

> 打印出来贴在屏幕上就行。所有命令都可以直接复制粘贴。
> 目录约定：仓库在 `D:\ModTest`（你朋友克隆到哪都行，脚本会自动识别）。

---

## 0. 一次性设置（每台机器只做一次）

```powershell
# 装 Git：https://git-scm.com/download/win  或  winget install --id Git.Git -e

git clone https://github.com/Cockr00Ach/TN-C_MC-magic-s-world.git
cd TN-C_MC-magic-s-world

# ★ 开启「提交守卫」（防止把 mod / 大文件提交进去）—— 每个 clone 必须做一次
git config core.hooksPath tools/git-hooks

# 取回编译用的两个第三方 jar（它们不在 git 里，从本地整合包取）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1

# 编译一次，确认环境没问题
.\gradlew.bat build
```

还需要（git 管不到的）：

- **基础整合包**：解压到任意位置（1.5 GB，走网盘，不走 git）
- **`touhoulittlemaid-1.5.2-forge+mc1.20.1.jar`**：放进整合包的 `mods\`（基础包漏了这个）

**验证一切正常**：
```powershell
git status          # 应该是 "nothing to commit, working tree clean"
```

---

## 1. 每天开始（你和朋友的操作完全一样）

```powershell
cd D:\ModTest
git pull                              # ★ 先拉，别在旧版本上改
git status                            # 确认干净
```

要开新功能就切分支（**推荐**，两个人的改动不容易打架）：

```powershell
git checkout -b feat/今天做什么
```

> 只是小改（改个数值、加一句提示）也可以直接在 `main` 上做，
> 但**改之前跟对方说一声**，尤其是这三个文件：
> `PROJECT-STATE.md`、`kubejs/data/tnc/spells/*.json`、`Config.java` / `SpellCatalog.java`

---

## 2. 改了 **Java 代码**（src\ 下的东西）→ 怎么进游戏

```powershell
cd D:\ModTest
.\gradlew.bat build                                   # 编译

powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify_mod_jar.ps1
#   ↑ 会检查一堆"编译通过但运行时静默失效"的坑（mixin 描述符、@Mod 注解、spell_assignments…）
#     看到 "mod jar verification passed." 再往下走

powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-to-pack.ps1 -Wait
#   ↑ 把新 jar 装进整合包。游戏开着会拒绝；-Wait 会等它关掉再装

# 然后启动游戏 → 进一个世界（自动探针在世界加载时才跑）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-pack-verdict.ps1
#   ↑ 期望: PASS  mana gate probe 6/6
```

---

## 3. 改了 **kubejs / config**（法术 json、脚本、配置）→ 怎么进游戏

**这条和上面不是一回事** —— kubejs 数据不走 jar，走 `sync.cmd`：

```powershell
cd D:\ModTest\modpack
.\sync.cmd diff      # 先看哪些文件有差异（只读，安全）
.\sync.cmd push      # 把工作区里"较新"的文件推到整合包
#                    （时间戳感知：不会用旧文件覆盖游戏刚写的新文件）

# 然后**重启游戏**（kubejs 数据在启动/加载世界时读，改完必须重启）
```

> 记不住的话：**Java 改动 → `install-to-pack.ps1`；数据改动 → `sync.cmd push`。**
> 两个都改了 → 两个都跑。顺序无所谓。

---

## 4. 提交并推送

```powershell
cd D:\ModTest
git add -A
git status                            # ★★★ 看一眼文件数！
#                                     健康基线：约 311 个文件 / 约 1.6 MB
#                                     数量级不对 = 有大件混进来了，先查再提交

git commit -m "一句话说清改了什么"
git push                              # 第一次推分支用 git push -u origin 分支名
```

提交时**守卫会自动跑**：如果里面有 `mods/*.jar`、`*.zip`、`libs/*.jar`、
`build/`、或任何 > 2 MB 的文件，**提交会被拒**，并告诉你原因。
处理办法：`git reset HEAD <那个文件>`（文件还在磁盘上），然后在 `.gitignore` 加规则。

推完到 GitHub 开 **Pull Request**，对方看过再合进 `main`。

---

## 5. 把对方的改动拿到手

```powershell
cd D:\ModTest
git checkout main
git pull
# 他的改动如果动了 Java 或 kubejs，记得按第 2 / 第 3 节重新装一遍
```

---

## 6. 游戏内调试命令（需要作弊/OP）

```
/tnc magic                 查看魔法石数据（亲和力/魔力/点数/已学）
/tnc points add 10         ★ 送 10 点魔法点数（测试用）
/tnc learn "tnc:spark"     解锁法术（会自动同步进法杖）
/tnc spells                法术目录和状态
/tnc wand                  法杖状态：同步 / 缺了就补发
/tnc engine                ★ 引擎接线 + 施法事件账本（排查"魔力没扣"看这个）
/tnc gatetest              ★ 硬拦截实测（三级阶梯，4~6 条断言）
/tnc cast "tnc:spark"      ★ 直接放一个法术（不依赖法杖和键位）
/tnc selftest              数值/门槛/NBT 自检
```

**施法**：手拿**魔法法杖** → 槽位 1 = 右键、槽位 2–5 = 数字键 → **按住约 0.5 秒**。
三条硬性条件（缺一会红字提示）：**拿着法杖** + **已解锁** + **魔力够**。

---

## 7. 出问题对照表

| 现象 | 原因 | 怎么办 |
|---|---|---|
| `git` 不是可识别的命令 | 装完 Git 没开新终端 | 关掉终端重开一个 |
| `Connection was reset` / 连不上 GitHub | **Clash 没开** | 打开 Clash，再试 |
| 提交被拒（COMMIT REJECTED） | 暂存区里有大文件/mod | `git reset HEAD <file>`，然后加 `.gitignore` 规则 |
| `push` 报 `rejected - non-fast-forward` | 对方先推了 | `git pull --rebase` 再 `git push` |
| 编译报找不到 `net.spell_engine` | `libs\` 里没 jar | `tools\fetch-libs.ps1` |
| 装 jar 被拒绝 | 游戏开着 | 关游戏，或用 `-Wait` |
| 游戏里数字键没反应 | 没拿法杖 / 没进世界重新加载 | 拿法杖；`/tnc engine` 看施法事件账本 |
| 改了 kubejs 没生效 | 没同步 / 没重启 | `sync.cmd push` 然后重启游戏 |
| `sync.ps1` 找不到整合包 | 路径不常见 | `sync.cmd diff -LiveRoot "X:\...\.minecraft\versions"` |
| 想手动查暂存区 | — | `powershell -File tools\check-staged.ps1` |

---

## 附：常用工具一览

| 脚本 | 干什么 |
|---|---|
| `tools\fetch-libs.ps1` | 从整合包取回编译用第三方 jar（克隆后跑一次） |
| `tools\verify_mod_jar.ps1` | 提交/安装前校验产物（查静默失效的坑） |
| `tools\install-to-pack.ps1` | 把 jar 装进整合包（有 java 在跑就拒绝） |
| `tools\check-pack-verdict.ps1` | 一条命令读出整合包实测结论（PASS/FAIL） |
| `tools\check-staged.ps1` | 手动检查暂存区有没有大文件 |
| `tools\kill-dev-java.ps1` | 清 dev 残留 JVM（**绝不碰游戏进程**） |
| `modpack\sync.cmd` | 工作区 ↔ 整合包 同步 kubejs/config |
