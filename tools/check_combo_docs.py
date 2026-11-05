#!/usr/bin/env python3
"""检查 docs/warrior 里写的连招按键串，和代码里的真连招是否一致。

为什么需要它：连招表在 `WarriorCombos` 里、说明在 markdown 里，两边各写一遍。
一旦有人调了某一招的按键（2026-11-01 就把旋转斩击从 QEZZZZ 改成了 QEQZZZ），
文档不会报错、游戏却按不出来，玩家只会觉得"我按对了但没反应"。

用法（先导出真值表）：
    java ... com.tnc.tnc.warrior.DumpCombos > work/combos-truth.txt
    python tools/check_combo_docs.py work/combos-truth.txt

真值表每行 `id|名字|序列|链|冷却`。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

SEQ = re.compile(r"(?<![A-Za-z])([QZE]{2,6})(?![A-Za-z])")
DOC_DIR = Path("docs/warrior")


def read_text(path: Path) -> str:
    """Windows 上 `java ... > file` 默认写成 UTF-16，`Out-File -Encoding utf8` 又带 BOM；
    两种都认，省得为了跑一次检查先去关心重定向的编码。"""
    data = path.read_bytes()
    for encoding in ("utf-8-sig", "utf-16", "gbk"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", "replace")


def load_truth(path: Path) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    for raw in read_text(path).splitlines():
        parts = raw.strip().split("|")
        if len(parts) < 3:
            continue
        combo_id, name, sequence = parts[0], parts[1], parts[2]
        if not sequence or not re.fullmatch(r"[QZE]+", sequence):
            continue
        rows.append((combo_id, name, sequence))
    return rows


def normalize(cell: str) -> str:
    for markup in ("**", "`", "「", "」", "《", "》"):
        cell = cell.replace(markup, "")
    return cell.strip()


def scan_row(line: str, truth: list[tuple[str, str, str]]) -> list[tuple[str, str]]:
    """只检查表格行：一行里「一格是连招名、另一格只有按键」时，按键必须等于代码里的序列。

    为什么只认表格：正文里既有 `振刀 EQE` 也有 `长剑EQ长E → 振刀`（后者是动画名），
    靠猜名字和按键的距离去配会一直误报；而玩家真正照着按的是表格，把表格钉死就够用了。
    """
    stripped = line.strip()
    if not stripped.startswith("|"):
        return []
    cells = [normalize(cell) for cell in stripped.strip("|").split("|")]
    names = [(index, name, sequence) for index, cell in enumerate(cells)
             for _, name, sequence in truth if cell == name]
    tokens = [(index, cell) for index, cell in enumerate(cells) if SEQ.fullmatch(cell)]
    if len(names) != 1 or not tokens:
        return []
    name_index, name, sequence = names[0]
    tokens.sort(key=lambda pair: abs(pair[0] - name_index))
    return [(name, tokens[0][1])]


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("usage: check_combo_docs.py <truth.txt> [doc dir]")
        return 2
    truth = load_truth(Path(argv[1]))
    if not truth:
        print("FAIL: 真值表是空的（先跑 DumpCombos）")
        return 1
    doc_dir = Path(argv[2]) if len(argv) > 2 else DOC_DIR
    docs = sorted(doc_dir.glob("*.md"))
    if not docs:
        print(f"FAIL: {doc_dir} 下没有 markdown")
        return 1

    by_name = {name: sequence for _, name, sequence in truth}
    checked = 0
    problems: list[str] = []
    for doc in docs:
        for number, line in enumerate(read_text(doc).splitlines(), 1):
            for name, token in scan_row(line, truth):
                checked += 1
                sequence = by_name[name]
                if token != sequence:
                    problems.append(f"{doc.name}:{number} 「{name}」写成 {token}，代码里是 {sequence}")
    if problems:
        print(f"FAIL: {len(problems)} 处连招按键与代码不一致")
        for problem in problems:
            print("  " + problem)
        return 1
    print(f"ok: {len(docs)} 篇文档里 {checked} 处连招按键与代码一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
