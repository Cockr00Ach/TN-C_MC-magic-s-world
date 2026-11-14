#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""法术平衡表：施法时间（瞬发 / 快招 / 蓄力 / 大招读条）与冷却时间。

作者 2026-11-07：「然后我现在希望你开始调平衡吧数值冷却时间这块，什么技能该瞬发，
什么技能该蓄力，你考虑一下」。

设计原则（详细理由见 docs/spells/法术平衡-20261107.md）
------------------------------------------------------------
1. **瞬发（cast 0）只给"救命/位移"**：闪现三档、闪电移位、光线的 t1~t3（指哪打哪的射线）。
   别的一律 ≥0.35 秒 —— 有读条才有"要不要打断"的博弈。
2. **同一条链必须逐级变重**：t1 快、t5 慢（读条 0.45 → 1.6 秒这个坡度是雷锲/雷球/火球三条链
   早就验证过的，用它当基准）。旧的数据里火/暗/水/地/风是**一档到底**（t1 和 t5 都是 0.6~0.8）✗。
3. **冷却按"这一招值多少"给**：基础攻击 0.5 秒（蓝耗才是闸门）；档位越高越久；
   召唤/大招 20~100 秒；光系是"一锤定音"节奏，长冷却不动。
4. **蓝耗只走魔法石那一套**（Config.manaCostForTier）⇒ 引擎的 `cost.exhaust` 全部清零，
   避免"两套系统同时扣费"和莫名其妙的饥饿感。

用法
----
    python tools/spell_balance.py --check            # 只报告差异
    python tools/spell_balance.py --apply            # 改文件（三个目录都改）
    python tools/spell_balance.py --report           # 打印整张表
"""
import glob
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

# ---- 读条曲线（秒，t1..t5）------------------------------------------------
CAST = {
    "SNAP":   [0.00, 0.00, 0.00, 0.00, 0.00],   # 瞬发：逃命/位移/光线
    "SELF":   [0.35, 0.40, 0.45, 0.50, 0.55],   # 自增益 / 姿态
    "SHOT":   [0.45, 0.65, 0.85, 1.15, 1.60],   # 攻击：弹/射线/爆破
    "ZONE":   [0.50, 0.70, 0.90, 1.15, 1.50],   # 场：雾/泥/环/雨/锁
    "SUMMON": [1.00, 1.30, 1.60, 1.90, 2.20],   # 召唤
    "ULT":    [1.50, 1.80, 2.10, 2.40, 2.80],   # 大招 / 仪式
}

# ---- 冷却曲线（秒，t1..t5）------------------------------------------------
CD = {
    "SNAP":   [4, 6, 9, 12, 12],
    "SELF":   [8, 12, 16, 20, 30],
    "SHOT":   [1.5, 3, 5, 8, 12],
    "ZONE":   [4, 6, 10, 15, 22],
    "SUMMON": [20, 30, 45, 70, 100],
    "ULT":    [20, 30, 45, 60, 90],
}

# ---- 每条链用哪条曲线 -----------------------------------------------------
#   cast / cd 可以分开指定；None = 这两项不动（那条链本来就是对的）
GROUP = {
    # 主链（基础雷法）：读条已经是对的了，要补的是**冷却**（原来全是 0.5 ✗）
    "primary":          {"cast": None,                        "cd": [0.5, 2, 3.5, 6, 10]},

    # 数据包那批"一档到底"的链 —— 这里是把坡度补上
    "gale":             {"cast": [0.45, 0.60, 0.75, 0.95, 1.15], "cd": [3, 5, 7, 10, 14]},
    "earth_shot":       {"cast": [0.45, 0.60, 0.75, 0.90, 1.10], "cd": None},
    "earth_move":       {"cast": [0.50, 0.65, 0.80, 1.00, 1.20], "cd": None},
    "earth_mud":        {"cast": [0.50, 0.65, 0.80, 1.00, 1.20], "cd": None},
    "earth_ring":       {"cast": [0.50, 0.65, 0.80, 1.00, 1.20], "cd": None},
    "water_ball":       {"cast": [0.45, 0.60, 0.75, 0.95, 1.20], "cd": None},
    "water_wave":       {"cast": [0.45, 0.60, 0.75, 0.95, 1.20], "cd": None},
    "water_bind":       {"cast": [0.50, 0.65, 0.80, 1.00, 1.20], "cd": None},
    "water_rain":       {"cast": [0.55, 0.70, 0.85, 1.05, 1.30], "cd": None},
    "dark_fog":         {"cast": [0.55, 0.70, 0.85, 1.05, 1.25], "cd": None},
    "dark_hand":        {"cast": [0.45, 0.60, 0.75, 0.95, 1.15], "cd": None},
    "dark_sacrifice":   {"cast": [0.50, 0.55, 0.60, 0.65, 0.70], "cd": None},
    "dark_summon":      {"cast": [1.00, 1.30, 1.60, 1.90, 2.20], "cd": None},

    # 光系：治疗/祝福、召唤天使的读条太长（3.8 秒 / 3.2 秒）⇒ 收到 2.4 秒以内
    "light_grace":      {"cast": [1.20, 1.50, 1.80, 2.10, 2.40], "cd": None},
    "light_summon":     {"cast": [1.20, 1.50, 1.80, 2.10, 2.40], "cd": None},

    # 下面这些**刻意不动**（各自的节奏是设计出来的）：
    #   orb / ball / ray / thunder_seal / wind_ball / wind_weapon —— 坡度已经有了
    #   light_beam —— t1~t3 瞬发射线 + t4/t5 读条，冷却 16/36/60/100/160 是"一锤定音"
    #   light_mirror —— 五档同参数（兑换的是效果不是节奏）
    #   blink / flight / burn / speed / concealment / chaos_magic —— 位移与姿态
}

# ---- 个别法术的例外（id -> (cast, cd)，None = 不改这一项）------------------
OVERRIDE = {
    # 殉命：以命换命，读条短、冷却极长 —— 不动
    "total_burn": (None, None),
}

# 三个目录：jar 内置资源、仓库里的数据包镜像、实机数据包
DIRS = [
    r"D:\ModTest\src\main\resources\data\tnc\spells",
    r"D:\ModTest\modpack\元素觉醒1.4.3-魔改版-20260915\kubejs\data\tnc\spells",
    r"E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915\kubejs\data\tnc\spells",
]

NUM = re.compile(r"-?\d+(?:\.\d+)?")


def obj_span(text, key):
    """找到 "key": { ... } 的对象正文区间（花括号配平，跳过字符串）。"""
    m = re.search(r'"%s"\s*:\s*\{' % key, text)
    if not m:
        return None
    start = text.index("{", m.start())
    depth = 0
    i = start
    while i < len(text):
        c = text[i]
        if c == '"':
            i += 1
            while i < len(text):
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    break
                i += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return (start, i)
        i += 1
    return None


def set_in_obj(text, parent, child, value):
    """只改 parent 对象里的 child 数值，其余一个字符都不动（保留原格式）。"""
    span = obj_span(text, parent)
    if span is None:
        raise SystemExit("找不到对象 %r" % parent)
    body = text[span[0]:span[1] + 1]
    pat = re.compile(r'("%s"\s*:\s*)(-?\d+(?:\.\d+)?)' % child)
    hits = pat.findall(body)
    if len(hits) != 1:
        raise SystemExit("%s.%s 出现了 %d 次（应为 1）" % (parent, child, len(hits)))
    new_body = pat.sub(lambda m: m.group(1) + fmt(value), body, count=1)
    return text[:span[0]] + new_body + text[span[1] + 1:]


def fmt(v):
    if isinstance(v, float) and v.is_integer() and abs(v) < 1e6:
        return str(int(v))
    return ("%.2f" % v).rstrip("0").rstrip(".")


def plan(school_group, tier, sid):
    """返回 (目标 cast, 目标 cd, 目标 exhaust)；None = 不改。"""
    cast = cd = None
    pol = GROUP.get(school_group)
    if pol:
        if pol.get("cast"):
            cast = pol["cast"][tier - 1]
        if pol.get("cd"):
            cd = pol["cd"][tier - 1]
    if sid in OVERRIDE:
        o_cast, o_cd = OVERRIDE[sid]
        if o_cast is not None:
            cast = o_cast
        if o_cd is not None:
            cd = o_cd
    return cast, cd, 0.0   # exhaust 一律清零


def walk(apply):
    rows = []
    for d in DIRS:
        if not os.path.isdir(d):
            print("!! 目录不存在:", d)
            continue
        for p in sorted(glob.glob(os.path.join(d, "*.json"))):
            sid = os.path.basename(p)[:-5]
            # newline="" => 换行符原样保留（这些文件 CRLF / LF 混杂，不能统一换行）
            text = open(p, encoding="utf-8", newline="").read()
            data = json.loads(text)
            group = data.get("group")
            tier = ((data.get("learn") or {}).get("tier")) or 1
            old_cast = (data.get("cast") or {}).get("duration")
            old_cd = (data.get("cost") or {}).get("cooldown_duration")
            old_ex = (data.get("cost") or {}).get("exhaust")
            cast, cd, ex = plan(group, tier, sid)
            new_text = text
            changed = []
            if cast is not None and abs(float(old_cast or 0) - float(cast)) > 1e-9:
                new_text = set_in_obj(new_text, "cast", "duration", cast)
                changed.append("cast %s->%s" % (fmt(old_cast or 0), fmt(cast)))
            if cd is not None and abs(float(old_cd or 0) - float(cd)) > 1e-9:
                new_text = set_in_obj(new_text, "cost", "cooldown_duration", cd)
                changed.append("cd %s->%s" % (fmt(old_cd or 0), fmt(cd)))
            if ex is not None and float(old_ex or 0) != float(ex):
                new_text = set_in_obj(new_text, "cost", "exhaust", ex)
                changed.append("exh %s->0" % fmt(old_ex or 0))
            if changed:
                json.loads(new_text)          # 改完必须还是合法 JSON ✓
                rows.append((sid, group, tier, "; ".join(changed), p))
                if apply:
                    open(p, "w", encoding="utf-8", newline="").write(new_text)
    return rows


def report():
    print("%-28s %-16s %s" % ("id", "group", "cast t1..t5 / cd t1..t5"))
    for d in DIRS[:1] + DIRS[2:3]:
        if not os.path.isdir(d):
            continue
        print("\n#", d)
        bys = {}
        for p in sorted(glob.glob(os.path.join(d, "*.json"))):
            data = json.load(open(p, encoding="utf-8"))
            g = data.get("group")
            t = ((data.get("learn") or {}).get("tier")) or 1
            bys.setdefault(g, []).append((t, os.path.basename(p)[:-5],
                                          (data.get("cast") or {}).get("duration"),
                                          (data.get("cost") or {}).get("cooldown_duration")))
        for g, lst in sorted(bys.items(), key=lambda kv: str(kv[0])):
            lst.sort()
            casts = " ".join(fmt(x[2] or 0) for x in lst)
            cds = " ".join(fmt(x[3] or 0) for x in lst)
            print("%-28s %-16s cast[%s]  cd[%s]" % (lst[0][1], g, casts, cds))


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
    if mode == "--report":
        report()
        sys.exit(0)
    apply = mode == "--apply"
    rows = walk(apply)
    print(("APPLY" if apply else "CHECK") + ": %d 处改动" % len(rows))
    for sid, g, t, ch, p in rows:
        print("  %-24s t%d %-16s %s" % (sid, t, g, ch))
