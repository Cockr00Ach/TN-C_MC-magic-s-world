"""用参考图直接做血条：抠掉内框里的熔岩、保留岩框与火山，熔岩由代码按血量填充。

输入：build/ref_bossbar.png（用户给的参考图）
输出：
  src/main/resources/assets/tnc/textures/gui/bossbar/art.png   完整条（含火山，内框熔岩已透明）
  build/bossbar_art_meta.txt                                  贴图内框位置/尺寸，供 Java 侧对齐
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "build/ref_bossbar.png"
OUT = ROOT / "src/main/resources/assets/tnc/textures/gui/bossbar"

# 目标内框（GUI 像素 * 2，贴图按 2 倍超采样）
INNER_W_TEX = 364
INNER_H_TEX = 28
MARGIN = 4  # 画布四周留白，避免边缘采样发虚


def is_lava(px):
    r, g, b, a = px
    if a == 0:
        return False
    # 亮熔岩：红占主导且不够暗；深色岩块/火山体保留
    return r > 120 and (r - b) > 45 and g < 175


def main():
    img = Image.open(SRC).convert("RGBA")
    # 参考图是白底，先把近白背景转透明
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if r > 238 and g > 238 and b > 238:
                px[x, y] = (r, g, b, 0)

    bbox = img.getbbox()
    art = img.crop(bbox)
    aw, ah = art.size

    # 找内框：按“每行熔岩像素数量”确定纵向范围（火山把内框切成左右两段，
    # 所以用计数而不是连续段长度），再按分位数取左右边界。
    ap = art.load()
    counts = []
    for y in range(ah):
        c = 0
        for x in range(aw):
            if is_lava(ap[x, y]):
                c += 1
        counts.append(c)
    peak = max(counts)
    band = [y for y, c in enumerate(counts) if c > peak * 0.55]
    top, bottom = min(band), max(band)

    xs = []
    for y in range(top, bottom + 1):
        for x in range(aw):
            if is_lava(ap[x, y]):
                xs.append(x)
                break
        for x in range(aw - 1, -1, -1):
            if is_lava(ap[x, y]):
                xs.append(x)
                break
    xs.sort()
    left, right = xs[int(len(xs) * 0.02)], xs[int(len(xs) * 0.98) - 1]

    # 抠掉内框里的熔岩（保留深色岩块与火山体）
    cut = 0
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            if is_lava(ap[x, y]):
                ap[x, y] = (0, 0, 0, 0)
                cut += 1

    # 按内框尺寸重采样整张图（横向/纵向各自对齐到 2 倍 GUI 像素）
    sx = INNER_W_TEX / float(right - left + 1)
    sy = INNER_H_TEX / float(bottom - top + 1)
    new_w = int(round(aw * sx))
    new_h = int(round(ah * sy))
    art = art.resize((new_w, new_h), Image.LANCZOS)

    inner_left = int(round(left * sx))
    inner_top = int(round(top * sy))
    canvas = Image.new("RGBA", (new_w + MARGIN * 2, new_h + MARGIN * 2), (0, 0, 0, 0))
    canvas.alpha_composite(art, (MARGIN, MARGIN))
    canvas.save(OUT / "art.png")

    meta = (
        f"art_w={canvas.width}\nart_h={canvas.height}\n"
        f"inner_left={inner_left + MARGIN}\ninner_top={inner_top + MARGIN}\n"
        f"inner_w={INNER_W_TEX}\ninner_h={INNER_H_TEX}\n"
        f"cut_px={cut}\n"
    )
    (ROOT / "build/bossbar_art_meta.txt").write_text(meta, encoding="utf-8")
    print(meta)
    print("art", canvas.size)


if __name__ == "__main__":
    main()
