# -*- coding: utf-8 -*-
"""
stage_and_push.py -- stage the work, filter out scratch/junk, commit, push via the
local proxy.

Why a script: the working tree has 200+ changed/untracked paths, and a plain
`git add -A` dragged in a pile of desktop-cleanup scratch (adware_cleanup.ps1,
oldscan*.log, startup-backup/*.reg, ...) that has nothing to do with the mod.
Staging the PROJECT folders explicitly is safer than excluding junk one pattern at
a time.

Usage: python tools/stage_and_push.py [--dry-run]
"""
import os
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_PATHS = ["src", "tools", "docs", "questbook", "design", "modpack", "剧情",
                 "archive", "model-source", "magic_modelandtexture", "剧情总纲.md"]

# scratch left over from a desktop malware clean-up / ad-hoc probing: never part
# of this mod, never committed (see the junk list in the session notes)
JUNK = (
    "adscan.ps1", "adware_", "cleanup_e.ps1", "final_pass", "final_run",
    "oldscan", "oldscan2", "quark_reinstall.log", "shellext_cleanup.log",
    "build_log.txt", "elevated_probe.txt", "old_dirs.csv", "stop_result.txt",
    "startup-backup/", ".dsh-tmp/", "logs/",
)


def run(args, check=True):
    r = subprocess.run(["git"] + args, cwd=REPO, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if check and r.returncode != 0:
        print("git %s failed (%d):\n%s\n%s" % (" ".join(args), r.returncode, r.stdout, r.stderr))
        sys.exit(1)
    return r.stdout


def main():
    dry = "--dry-run" in sys.argv
    existing = [p for p in PROJECT_PATHS if os.path.exists(os.path.join(REPO, p))]
    print("staging %d path(s): %s" % (len(existing), ", ".join(existing)))
    run(["add", "-A", "--"] + existing)

    staged = [ln for ln in run(["diff", "--cached", "--name-only"]).splitlines() if ln.strip()]
    keep, drop = [], []
    for path in staged:
        norm = path.replace("\\", "/")
        if any(j in norm for j in JUNK):
            drop.append(path)
        else:
            keep.append(path)
    if drop:
        print("un-staging %d scratch path(s):" % len(drop))
        for p in drop[:12]:
            print("   -", p)
        run(["reset", "-q", "--"] + drop)

    print("staged %d file(s)" % len(keep))

    # the commit guard hook refuses any file over 2 MB: show the biggest staged ones
    big = []
    for path in keep:
        full = os.path.join(REPO, path)
        if os.path.isfile(full) and os.path.getsize(full) > 1024 * 1024:
            big.append((os.path.getsize(full), path))
    for size, path in sorted(big, reverse=True)[:8]:
        print("   %.1f MB  %s" % (size / 1048576.0, path))

    if dry:
        print("dry run: nothing committed")
        return 0

    message = os.path.join(REPO, ".git", "TNC_COMMIT_MSG.txt")
    with open(message, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(COMMIT_MESSAGE)
    r = subprocess.run(["git", "commit", "-F", message], cwd=REPO,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(r.stdout.strip())
    if r.returncode != 0:
        print("COMMIT FAILED:\n%s" % r.stderr.strip())
        return 1

    # the repo's git config points http.proxy at a clash instance that is often down;
    # the working recipe (README) is to pass the proxy explicitly on the command
    push = subprocess.run(
        ["git", "-c", "http.proxy=http://127.0.0.1:6789", "push", "origin", "main"],
        cwd=REPO, capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(push.stdout.strip())
    print(push.stderr.strip())
    return push.returncode


COMMIT_MESSAGE = """feat(暗系): 黑雾链 + 以伤换伤链重做; 黑夜之手体积; 作者版三枚币; 修 verify 脚本

黑雾链（暗系第 4 条，作者："我不满意现在这样的" -> "你全做吧"）
- 四层真·黑雾粒子取代单组原版 smoke：贴地墨雾 + 中段雾 + 魂火 + 鬼火
- 新增 3D 暗穹顶 tnc:projectile/dark_fog（走引擎 CLOUD 的 client_data.model 槽，
  此前全项目 0 用例；生成器 tools/gen_dark_fog_model.py）
- 新增实体 tnc:fog（DarkFogCloudEntity extends SpellCloud）：t3 起跟着施法者走、
  t5 环绕；托管一张跟着走的暗色边界法阵（新样式 STYLE_DARK）
- 新增减益 tnc:dark_veil，替换借来的风系 tnc:gale_slow
- TnSpellMechanics/DarkFogMechanics 挂在引擎自己的 CombatEvents.SPELL_CAST 上
  （不是 Forge 事件，挂 @SubscribeEvent 会静默不触发）

以伤换伤链（暗系第 2 条，作者："你还可以再优化一下以伤换伤"）
- 原来挂的是火系燃烧线那五个效果（加 spell_power:fire）=> 暗系收益为 0，
  且 t1 是火系"不燃血"档、t5 是纯白嫖；现改为暗系自己的五个
  tnc:blood_mark/burn/sacrifice/possess/god（加 spell_power:soul）
- 新增 magic/TNDarkSacrificeMechanics：燃血（永不致死，保底 1 点）/ 原地复活 / 我为神
- 补上这条链原来完全没有的回血（五档 HEAL），"以伤换伤"才成立
- 燃血改用 hurt() 并清零受伤窗口（setHealth 绕开 invulnerableTime 会静默丢伤害）

黑夜之手体积：t2 x2 / t3 x5 / t4 x10 / t5 x20 => scale 0.85/1.07/1.45/1.83/2.31

金银铜币：用作者给的三份模型（coin_gold.json 仅改名为 gold_coin.json）
- 原来模型带 -90 度旋转 => 原版只接受 22.5 的整数倍，整个模型不加载 => 紫黑方块；
  我中途自己烘焙几何时转错了方向、把形状改坏（已作废，记录见 docs/）

闸门与工具
- 新增 SpellParticleSafetyTest：法术里出现"不是 ParticleType 的粒子 id"就构建失败
  （block_factorys_bosses 的 ink_fog 等是 Bedrock 粒子，用了会让客户端闪退）
- verify_mod_jar.ps1：school 白名单 DARK->SOUL（暗系此前从未被检查）、PS 5.1 深层
  JSON（ConvertFrom-Json 默认只展开两层，CLOUD 模型检查静默失效）、
  每个 models/item/*.json 必须能被原版加载器读（角度/范围/uv）、三枚币进物品白名单、
  新增 tnc:dark_power 与献祭链效果白名单、"献祭链不许再挂 tnc:fire_*"守卫
- 新增生成器 gen_dark_fog_model/spells/icons、gen_tnc_dark_sacrifice_spells、
  gen_dark_sacrifice_icons、gen_dark_hand_scales、gen_coin_models、preview_dark_fog

同时包含另一个会话尚未提交的工作（一并推送）：
- 龙改用原版方块模型 + Display 渲染（TNDragonRenderer / TNDragonRenderData / dragon_block.json）
- 巨兽人领主 NPC（TNNpcs / TNNpcClientEvents）与 MagicStoneData 配装归一化
- 作者的新雷球模型 thunder_ball.json 等资源

未实机验收（开发环境看不到画面）：判断依据为引擎字节码 + 构建期测试 + CPU 预览图。
"""


if __name__ == "__main__":
    sys.exit(main())
