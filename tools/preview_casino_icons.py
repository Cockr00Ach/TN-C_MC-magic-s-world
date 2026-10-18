#!/usr/bin/env python3
"""把赌场物品图标打成 ASCII 预览（不出图，直接看形状对不对）。

用途：生成器画出来的 32x32 图标，在终端里先确认"看得出是台机器"，
不用开图片查看器 ✓。像素 → 字符按亮度映射 ✓。

用法：
    python tools/preview_casino_icons.py
"""

import glob
import os
import struct
import sys
import zlib

RAMP = " .:-=+*#%@"


def read_png(path):
    d = open(path, "rb").read()
    w, h = struct.unpack(">II", d[16:24])
    i, idata = 8, b""
    while i < len(d):
        ln = struct.unpack(">I", d[i:i + 4])[0]
        tag = d[i + 4:i + 8]
        if tag == b"IDAT":
            idata += d[i + 8:i + 8 + ln]
        i += 12 + ln
    raw = zlib.decompress(idata)
    stride = w * 4
    rows = []
    for y in range(h):
        base = y * (stride + 1) + 1
        rows.append([tuple(raw[base + x * 4: base + x * 4 + 4]) for x in range(w)])
    return w, h, rows


def show(path):
    w, h, rows = read_png(path)
    print("\n%s  (%dx%d)" % (os.path.basename(path), w, h))
    for y in range(h):
        line = ""
        for x in range(w):
            r, g, b, a = rows[y][x]
            if a == 0:
                line += " "
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            line += RAMP[min(len(RAMP) - 1, int(lum * len(RAMP)))]
        print("  |" + line + "|")


def main():
    files = sorted(glob.glob(os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "src", "main", "resources", "assets", "tnc", "textures", "item", "casino_*.png")))
    if not files:
        print("no icons found")
        return 1
    for f in files:
        show(f)
    print("\nramp: '%s' (dark -> light), space = transparent" % RAMP)
    return 0


if __name__ == "__main__":
    sys.exit(main())
