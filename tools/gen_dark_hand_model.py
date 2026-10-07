# -*- coding: utf-8 -*-
"""
gen_dark_hand_model.py -- the "HAND OF NIGHT" projectile model (dark magic chain 1).

================================================================================
!!! 2026-10-10 晚起：**本脚本不再是产物来源，别再跑它** !!!
================================================================================
作者拿 Blockbench 自己改了模型（方块 31 -> 42、手指重新摆位）并重画了贴图
（原话：「我现在更新了一下，你依照我的更新更新吧」）
=> 事实来源变成 `magic_modelandtexture/dark_hand/dark_hand.bbmodel`；
   把它导回发货文件的是 `tools/import_dark_hand_bbmodel.py`（bbmodel -> 模型 JSON + PNG，带加载器硬约束校验）。

**跑本脚本会**：按下面这套程序化调色板重画整张贴图（覆盖作者手绘的 ✗），
并按老坐标重建 31 个方块（丢掉作者加的 11 个方块 ✗）。历史/备用保留，正常运行别碰 ✓。
需要重新程序化生成时（比如要重做一版全新造型），先跟作者确认 ✓。

WHAT IT WRITES
    1. src/main/resources/assets/tnc/models/projectile/dark_hand.json
       vanilla Java block model (same format as lightingball.json / fireball.json)
    2. src/main/resources/assets/tnc/textures/spell_projectile/dark_hand.png   (32x32)
    3. docs/previews/dark_hand_preview.png     a 4x "what it looks like" board
       (front / side / top / 3-4 view, rendered by this script -- not a screenshot)

MODEL ORIENTATION (the part that is easy to get wrong)
    The projectile flies along +Z in model space. The hand is therefore built with its
    FINGERS pointing +Z (forward, at the victim), the wrist stub at -Z (trailing), and
    the palm facing down (-Y). Author 2026-10-10 confirmed the intent: "手腕对着别人
    不是手指" was wrong, and the hand should read as an open grasp with "掌心对着别人".

    ★★ THIS ORIENTATION ONLY HOLDS IF THE SPELL SETS `model.orientation`.
    With the field absent, the engine TUMBLES the model about the entity position, and
    then whichever end faces the victim is arbitrary. That is why the five night-hand
    spells now carry `"orientation": "TOWARDS_CAMERA"` -- the only variant that yaws
    the model without pitching or rolling it (see tools/gen_dark_hand_projectiles.py).
    (Same trap as the `flash` lightning on 2026-09-27: "怎么雷电是躺着的".)

SHAPE (2026-10-10 rework: open grasp, not a fist)
    Compact wrist collar + broad flat palm + four splayed, nearly-straight fingers with
    forward-hooking claws + a thumb angled across, i.e. the "about to seize" silhouette
    the author asked for ("有点下界铁掌这个boss的形状").

SIZE
    (printed by main(); the model is recentred on its own bounding box, so the printed
     bbox is what the engine sees.) Scale knobs per tier live in the spell JSONs --
     do not resize the model to change how big a tier looks.

TEXTURE
    32x32, drawn procedurally: bone-white knuckle plates, purple-black shadow between
    the fingers, hot magenta claw tips. Every face of every cube maps into this atlas
    in whole pixels (no fractional UV), which is what keeps it from looking striped.

ASCII only (PowerShell 5.1 reads non-ASCII .ps1 as ANSI; this is .py but the project
rule is the same -- keep tooling ASCII and put text data in json).
"""
import json
import math
import os
import sys

from PIL import Image

MODID = "tnc"
MODEL_NAME = "dark_hand"

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                          "models", "projectile", MODEL_NAME + ".json")
TEXTURE_PATH = os.path.join(REPO, "src", "main", "resources", "assets", MODID,
                            "textures", "spell_projectile", MODEL_NAME + ".png")
PREVIEW_PATH = os.path.join(REPO, "docs", "previews", MODEL_NAME + "_preview.png")

TEX = 32   # texture is 32x32; every UV below is in whole pixels of this atlas

# ----------------------------------------------------------------------------
#  atlas: one rectangle per "material", tiled by the boxes below
# ----------------------------------------------------------------------------
#  Keep every material a solid-ish patch with its own shading so a mis-UVed face is
#  visible at a glance instead of blending in.
#
#  ★★ 2026-10-10 调色重做（作者："我想改改贴图，感觉**太花了**，想改的**更暗更黑更邪恶**一点"）:
#    旧配色是「紫黑肉质 + 亮紫指节 + **洋红爪尖 + 近白高光**」✗ —— 爪子那两个数是全场最亮的，
#    远看整只手被爪尖和指节的亮紫抢掉，所以"花" ✓。
#    新配色只有一个思路：**整只手是焦黑的碳，只用冷紫的极窄描边把块面分开，
#    唯一的暖色留给爪尖的干血暗红** ✓ —— 黑、脏、安静，但手指仍然数得清。
#    ⚠️ 别把 edge 也压到 0 ✗：手是 31 个方块拼的，块面之间没有一丝明度差 ⇒
#      远看会糊成**一坨黑**（旧文档当初选亮配色就是为了防这个 ✗）。edge 只比 base 高一点点。
#  ★★ 2026-10-10 第三刀：**整张图集重排**（作者："blockbench 模型没变化" ✓ 他是对的 ✗）
#
#  查出来的两个根问题（都不是"配色不够暗"，是排布本身错了 ✗）：
#   1) **矩形互相重叠** ✗：旧排布里 `palm`(8..20,0..12) 与 `claw`(12..22,8..18)、
#      与 `digit_0`(0..12,8..14) 都压在一起 ⇒ ① 同一批像素被两块材质共用，
#      **作者画爪子会同时改到手背** ✗；② 实测亮度被爪尖那条高光带拉回去
#      （手背块 max 46.7 比掌心 45.6 还亮 ✗）⇒ 怎么调 base 都"看不出区别" ✓。
#   2) **掌心的贴图区比例不对** ✗：掌板那个大面是 **13 单位宽 × 12 高**，
#      而我第一版劈出来的两块只有 6×12 ⇒ 画上去会被**横向拉伸 2.2 倍** ✗✗。
#  ⇒ 现在：掌/背各给一块 **13x12**（正好等于那个面 ✓ 1:1 不拉伸），其余材质按需要缩小后
#    重新铺满 32x32，**任何两块都不重叠** ✓（下面有断言，重叠会直接报错 ✓）。
ATLAS = {
    # name          (x, y, w, h)      base           edge           accent
    "palm":         ((0, 0, 13, 12),  (24, 18, 27),  (40, 30, 45),  (11, 8, 14)),
    "palm_back":    ((13, 0, 13, 12), (5, 4, 8),     (8, 7, 12),    (2, 2, 3)),
    "claw":         ((0, 12, 8, 8),   (54, 11, 14),  (92, 20, 24),  (18, 4, 6)),
    "aura":         ((8, 12, 6, 6),   (28, 17, 40),  (50, 30, 70),  (11, 6, 17)),
    "wrist":        ((14, 12, 8, 8),  (13, 10, 16),  (22, 17, 27),  (6, 4, 8)),
    "back":         ((22, 12, 8, 8),  (12, 9, 15),   (20, 15, 24),  (5, 4, 7)),
    "digit_0":      ((0, 20, 6, 4),   (21, 15, 25),  (34, 24, 40),  (8, 6, 11)),
    "digit_1":      ((6, 20, 6, 4),   (18, 13, 22),  (29, 20, 35),  (7, 5, 9)),
    "digit_2":      ((12, 20, 6, 4),  (15, 11, 19),  (25, 17, 29),  (6, 4, 8)),
    "knuckle":      ((18, 20, 6, 4),  (24, 17, 28),  (38, 27, 43),  (9, 6, 12)),
    # ★ tether：全图最暗的一档（作者 2026-10-10："手腕跟身体可以有一条黑色的粗的线连接"）。
    #   ⚠️ 现在**没有方块用它**（那条黑带已删，改由粒子黑索承担 ✓），留着当备用区域 ✓
    "tether":       ((24, 20, 8, 8),  (10, 8, 14),   (16, 13, 22),  (5, 4, 8)),
}


def _assert_no_overlap():
    """★ 图集里任何两块矩形都不许重叠 ✓ —— 重叠会让两块材质共用像素
    （作者画 A 会同时改到 B ✗），而且会像 2026-10-10 那次一样把亮度统计彻底带偏 ✗。

    这条断言是**故意留的**：以后往 ATLAS 里加材质、或者挪某一块的坐标，
    一旦压到别人身上，生成器会**当场报错**，而不是安静地画出一张互相污染的图 ✓。
    """
    names = list(ATLAS)
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            ax, ay, aw, ah = ATLAS[names[i]][0]
            bx, by, bw, bh = ATLAS[names[j]][0]
            if ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah:
                raise AssertionError(
                    "atlas overlap: %s %s vs %s %s" % (names[i], ATLAS[names[i]][0],
                                                       names[j], ATLAS[names[j]][0]))


_assert_no_overlap()

# where the next free patch is, for any material not pre-declared (auto-tiled)
_auto_cursor = [0, 16]

# ★★ 整体明度旋钮（2026-10-10 加）：
#   1.00 = 就用上面表里那套"焦黑 + 干血红"；
#   想让**整只手**再暗一档写 0.85、想亮回来一点写 1.15 ✓ —— 只改这一个数，其它都不用动。
#   ⚠️ 别低于 ~0.7 ✗：手是 31 个方块拼的，全部压到接近 0 之后块面之间就没有明度差了，
#      远看会糊成**一坨黑**（旧配色当初选亮色就是为了防这个 ✗）。
PALETTE_GAIN = 1.00


def atlas_rect(name):
    if name in ATLAS:
        return ATLAS[name][0]
    # auto-tile unknown materials into the bottom half, 8x8 each
    x, y = _auto_cursor
    if x + 8 > TEX:
        x, y = 0, y + 8
    _auto_cursor[0] = x + 8
    ATLAS[name] = ((x, y, 8, 8), (40, 40, 40), (70, 70, 70), (20, 20, 20))
    return ATLAS[name][0]


# ----------------------------------------------------------------------------
#  geometry helpers
# ----------------------------------------------------------------------------
def box(frm, to, mat, uv_face=None, north_mat=None):
    """One axis-aligned cube. frm/to are [x,y,z] in model units (1 unit = 1/16 block).

    north_mat: 可选的**手背面专用材质** ✓（2026-10-10 加）。
      模型的 `north` 面就是 -Z 那一面 —— 对掌板来说它是**手背**，而 `south`（+Z）是**掌心** ✓。
      默认六个面共用同一块材质 ⇒ 手心和手背一模一样 ✗（作者："哪边是掌心"）。
      传了 north_mat 之后，只有 north 那一个面改用另一块贴图区域 ⇒ 两面可以画得完全不同 ✓。
    """
    x0, y0, z0 = frm
    x1, y1, z1 = to
    assert x1 > x0 and y1 > y0 and z1 > z0, "box must have positive size: %r -> %r" % (frm, to)
    ax, ay, aw, ah = atlas_rect(mat)
    # every face gets a same-sized slice of the material patch, picking different
    # corners so faces are distinguishable while looking at the model
    fx, fy = uv_face if uv_face else (0, 0)
    # face-region sizes, CLAMPED twice:
    #   1) to the material patch (so a face never samples a neighbouring material), and
    #   2) so that (start + size) still fits inside the atlas. The fanned hands are
    #      ROTATED, so their spans are fractional and can exceed the patch -- without
    #      this clamp the generator now aborts with "UV out of atlas" (it did, on the
    #      first run of the fan version).
    def span(available, start, extent):
        size = max(1, min(available, int(math.floor(extent))))
        if start + size > TEX:
            size = max(1, TEX - start)
        return size

    w = span(aw, ax + fx, x1 - x0)
    h = span(ah, ay + fy, y1 - y0)
    d = span(aw, ax + fx, z1 - z0)
    eh = h

    def face(u, v, uw, vh):
        # keep the rect inside the atlas on both axes, then guarantee it is non-degenerate
        u = max(0, min(u, TEX - 1))
        v = max(0, min(v, TEX - 1))
        uw = max(1, min(uw, TEX - u))
        vh = max(1, min(vh, TEX - v))
        return {"uv": [u, v, u + uw, v + vh], "texture": "#0"}

    # ★ 手背面单独用另一块材质（见 north_mat 的说明）✓
    if north_mat:
        nx, ny, nw, nh = atlas_rect(north_mat)
        north_face = face(nx, ny, span(nw, nx, x1 - x0), span(nh, ny, y1 - y0))
    else:
        north_face = face(ax + fx, ay + fy, w, h)

    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "rotation": {"angle": 0, "axis": "y", "origin": [x0, y0, z0]},
        "faces": {
            # north/south are the XY faces (size w x h)
            "north": north_face,
            "south": face(ax + (aw - w - fx) % max(1, aw - w + 1), ay + fy, w, h),
            # east/west are the ZY faces (size d x h)
            "east": face(ax + fx, ay + ((fy + h) % max(1, ah - h + 1)), d, h),
            "west": face(ax + ((fx + w) % max(1, aw - d + 1)), ay + fy, d, h),
            # up/down are the XZ faces (size w x d)
            "up": face(ax + fx, ay + ((fy + h) % max(1, ah - d + 1)), w, d),
            "down": face(ax + fx, ay + ((fy + h + 1) % max(1, ah - eh + 1)), w, d),
        },
        "_mat": mat,
    }


def build_boxes():
    """Several clawed hands fanning out on a thick black tether.

    Author 2026-10-10: "我希望手腕跟身体可以有一条黑色的粗的线连接，看起来像是从我的背后
    伸出来的好几只手去抓别人".

    So the shape is now:
        * a THICK black band running back along -Z from each wrist (the "tether" -- what
          visually connects the hand to the caster's body while it flies), built from the
          darkest atlas material so it reads as a solid black line, not a gradient;
        * THREE complete open hands fanned around the travel axis (-Z... +Z), sharing the
          same origin, so the silhouette is "several hands reaching out", not one hand.

    WHY THREE HANDS INSIDE ONE MODEL (and not three projectiles):
        the engine's `launch_properties` can fire extra copies (`extra_launch_count`), but
        they share one trajectory and ONE divergence -- they stack into a single blob and
        do not read as separate hands. Modelling the fan is what actually produces the
        look, and it keeps homing/impact semantics exactly as they are (one projectile).
        (Checked with javap: LaunchProperties has only velocity / extra_launch_count /
         extra_launch_delay -- there is no per-copy spread to exploit.)

    Each hand keeps the same anatomy as before (palm, four splayed fingers with
    forward-hooking claws, thumb), just smaller and rotated about the Z axis.
    """
    b = []

    # ★★ 2026-10-10 第三版：**一只手，掌心朝 +Z（飞行方向）**
    #
    # 作者反馈（这一版就是照它改的）：
    #   1. "手发射出去怎么一直在旋转，掌心直直出去就好"
    #      ⇒ 朝向改成 ALONG_MOTION（见 gen_dark_hand_projectiles.py），
    #        而且模型**掌心必须朝着 +Z** —— 之前掌心朝下、手指朝前，
    #        配 TOWARDS_CAMERA 会随视角偏航，看起来就在转 ✗。
    #   2. "三个手掌黏在一起很丑"
    #      ⇒ 模型里**只放一只手** ✓。"好几只手"改由引擎**多发几发**实现
    #        （extra_launch_count + extra_launch_delay）⇒ 三只手**沿飞行路径错开**，
    #        而不是在同一处互相穿插 ✓。
    #   3. "黑带很怪…可以做一个黑线连接着手腕跟后背"
    #      ⇒ 模型里那条粗黑带**删掉** ✓。连到施法者的线**不能烤进模型** ——
    #        手会飞离施法者，静态模型永远够不到他 ✗。改由客户端渲染一条**黑索**
    #        （见 client/TNDarkCordRenderer.java）✓。
    #
    # 几何（模型空间）：
    #   +Z = 前方（朝着敌人）⇒ **手掌平板的面朝 +Z** ✓
    #   +Y = 上 ⇒ 手腕在下、手指朝上（像"伸手按住"的姿势）
    #   +X = 右
    # 正面看就是一只张开的手掌按过来，手腕沿 -Z 直直拖在后面 ✓。
    b = []

    # 手腕：沿 -Z 直直往后（短，不是前臂）
    b.append(box([-3, -3, -7], [3, 3, -1], "wrist"))
    # 手掌平板：它的 +Z 面就是掌心
    # ★ 2026-10-10：**手背（north = -Z）改用 palm_back** ✓ —— 原来两面共用一块 UV，
    #   游戏里手心和手背一模一样，作者认不出哪面是掌心 ✗（见 ATLAS 里那两块的说明）
    b.append(box([-6.5, -6, -1.5], [6.5, 6, 1.5], "palm", north_mat="palm_back"))
    # 掌根（靠手腕那侧稍厚一点）
    b.append(box([-5, -7, -2.5], [5, -3, 1.5], "back"))
    # 掌指关节横条
    b.append(box([-6.5, 6, -1], [6.5, 8, 1], "knuckle"))
    b.append(box([-6, 6.5, 1], [6, 8, 2], "back"))

    # 四指朝上伸、向外张开：(x0, x1, 长度, 每段外张量)
    fingers = [
        (-6.0, -4.0, 15, -0.55),
        (-2.5, -0.5, 18, -0.15),
        (0.5, 2.5, 18, 0.15),
        (4.0, 6.0, 15, 0.55),
    ]
    for i, (xa, xb, length, splay) in enumerate(fingers):
        mat = "digit_%d" % min(2, i if i < 3 else 2)
        seg = length // 3
        y = 8.0
        z = -1.0
        for s in range(3):
            h = 2.5
            dx = splay * (s + 1)
            dz = -0.15 * s                      # 极轻微向前收，读起来像"要抓"
            b.append(box([xa + dx, y, z + dz], [xb + dx, y + h, z + dz + 2], mat))
            y += h
        # 指尖爪：底座 ＋ 向前钩的尖
        cx0, cx1 = xa + splay * 3, xb + splay * 3
        b.append(box([cx0 + 0.25, y, z - 0.5], [cx1 - 0.25, y + 1.5, z + 1.5], "claw"))
        b.append(box([cx0 + 0.75, y + 1.5, z - 1.0], [cx1 - 0.75, y + 3.5, z + 0.5], "claw"))

    # 拇指：从左侧伸出，斜着往上
    b.append(box([-10, -1, -1], [-6.5, 2, 1], "digit_2"))
    b.append(box([-12.5, 1, -1], [-10, 4, 1], "digit_1"))
    b.append(box([-13.5, 4, -1], [-11.5, 6.5, 0.5], "claw"))

    # 几片灵光碎片，免得看起来像一张纸片
    b.append(box([-8.5, -5, -1], [-7, -3, 1], "aura"))
    b.append(box([7, -5, -1], [8.5, -3, 1], "aura"))
    b.append(box([-1.5, 15, -1], [1.5, 17, 1], "aura"))

    return b


def recenter(boxes):
    """Shift the whole model so its bounding-box centre sits on the origin.

    Why this matters for a PROJECTILE: the engine tumbles the model about the entity
    position. If the geometry hangs forward of the origin (this hand was +4 units on Z),
    the hand swings around a point in front of itself and looks like it is orbiting
    its own wrist. Centring the bbox makes it spin in place.

    ★★ AND THEN CLAMP INTO THE LOADER'S WINDOW (2026-10-10).
    Vanilla's `BlockElement.Deserializer` rejects any `from`/`to` outside **-16 .. 32**
    and drops the ENTIRE model if one cube violates it. Centring a model longer than
    ~32 units therefore guarantees a failure -- which is exactly what happened to the
    first three-hand version (tether at z=-30 => recentred to +-29.75 => the client
    logged "Failed to load model tnc:models/projectile/dark_hand.json").
    This function now refuses to produce an out-of-window model: if the recentred box
    would not fit, it slides the model so the box fits inside `-15.9 .. 31.9` instead.
    A slightly off-centre pivot is a cosmetic wobble; an unloaded model is invisible.
    """
    xs0 = min(b["from"][0] for b in boxes)
    xs1 = max(b["to"][0] for b in boxes)
    ys0 = min(b["from"][1] for b in boxes)
    ys1 = max(b["to"][1] for b in boxes)
    zs0 = min(b["from"][2] for b in boxes)
    zs1 = max(b["to"][2] for b in boxes)
    dx = -round((xs0 + xs1) / 2.0, 2)
    dy = -round((ys0 + ys1) / 2.0, 2)
    dz = -round((zs0 + zs1) / 2.0, 2)

    # the vanilla extents window, with a hair of margin
    LO, HI = -15.9, 31.9

    def fix(lo, hi, shift):
        """Center if it fits; otherwise sit flush against the window edge.

        Centring keeps the projectile's pivot in the middle of the model (so it spins in
        place instead of orbiting its own wrist). But the extents window is NOT symmetric
        about the origin (-16 .. 32), so a model that cannot fit centred still has room on
        the + side: sliding it flush to LO uses the whole 47.8-unit window instead of the
        ~31.8 units available when centred. Both branches are deterministic, so re-running
        the generator does not jitter the geometry.
        """
        if (hi - lo) > (HI - LO):
            raise AssertionError(
                "model is %.2f units long in one axis but the vanilla loader only allows "
                "%.2f (-16..32); shrink the model and let the spell JSON `scale` make it big"
                % (hi - lo, HI - LO))
        if lo + shift < LO:
            return round(LO - lo, 2)
        if hi + shift > HI:
            return round(HI - hi, 2)
        return shift

    dx = fix(xs0, xs1, dx)
    dy = fix(ys0, ys1, dy)
    dz = fix(zs0, zs1, dz)

    if dx == dy == dz == 0:
        return boxes
    for b in boxes:
        b["from"] = [round(b["from"][0] + dx, 2), round(b["from"][1] + dy, 2), round(b["from"][2] + dz, 2)]
        b["to"] = [round(b["to"][0] + dx, 2), round(b["to"][1] + dy, 2), round(b["to"][2] + dz, 2)]
    return boxes


def to_model(boxes):
    elements = []
    for bx in boxes:
        el = {k: v for k, v in bx.items() if not k.startswith("_")}
        # round the half-units the claws use, so the json stays readable
        el["from"] = [round(v, 2) for v in el["from"]]
        el["to"] = [round(v, 2) for v in el["to"]]
        elements.append(el)
    return {
        "format_version": "1.9.0",
        "credit": "Generated by tools/gen_dark_hand_model.py",
        "textures": {
            "0": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
            "particle": "%s:spell_projectile/%s" % (MODID, MODEL_NAME),
        },
        "elements": elements,
    }


# ----------------------------------------------------------------------------
#  texture
# ----------------------------------------------------------------------------
def build_texture():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = img.load()

    def paint(name):
        (x, y, w, h), base, edge, accent = ATLAS[name]
        # ★ 整体明度旋钮（PALETTE_GAIN）在这里统一生效 ✓
        base = tuple(min(255, int(v * PALETTE_GAIN)) for v in base)
        edge = tuple(min(255, int(v * PALETTE_GAIN)) for v in edge)
        accent = tuple(min(255, int(v * PALETTE_GAIN)) for v in accent)
        for i in range(w):
            for j in range(h):
                # soft vertical shade + a 1px darker frame
                t = j / max(1, h - 1)
                c = tuple(int(base[k] * (1 - 0.25 * t) + accent[k] * (0.25 * t)) for k in range(3))
                if i == 0 or j == 0 or i == w - 1 or j == h - 1:
                    c = edge
                # ★ 2026-10-10：散点从 +28 压到 +10 —— 原来的亮点太多，也是"花"的一部分 ✗
                #   （只需要"块面不完全是平的"，不需要看得见的噪点）
                if (i * 7 + j * 13) % 17 == 0:
                    c = tuple(min(255, v + 10) for v in c)
                px[x + i, y + j] = (c[0], c[1], c[2], 255)

    for name in ATLAS:
        paint(name)

    # claw patch: ★ 2026-10-10 那条**近白高光**换成暗红细线 ✗
    #   （旧值 (255,235,255) 是整张图最亮的一笔 —— 远看爪尖一片白，就是"太花"的主犯 ✓；
    #    现在改成干血的暗红，爪子仍然是整只手上唯一能一眼看到的细节，但不再是白点 ✓）
    (x, y, w, h), base, edge, accent = ATLAS["claw"]
    ridge = tuple(min(255, int(v * PALETTE_GAIN)) for v in (122, 26, 30))
    for j in range(h):
        px[x + w // 2, y + j] = (ridge[0], ridge[1], ridge[2], 255)

    # ------------------------------------------------------------------
    #  ★★ 2026-10-10 第四刀：**在掌心上画一个显眼的记号** ✓
    #
    #  作者的原话是「这个没变啊你在干嘛」✗ —— 而文件其实**已经是新的**了
    #  （掌/背两块 UV 确实分开了、亮度差 4.4 倍），问题在于：
    #    · 形状**故意没变**（一个方块都没加 ✗）⇒ 3D 视图里轮廓完全一样；
    #    · 两块又都是近黑色，差别只是"亮度 23 vs 5" ✗ —— 掌心那块只有 13x12 像素、
    #      在 Blockbench 默认视角下就指甲盖大 ⇒ **看不出来是正常的** ✓。
    #  ⇒ 光靠调色永远说不清"哪面是掌心"，干脆**画一个记号**：
    #      掌心 = 一枚血红的"封印"（一个环 ＋ 三道掌纹），**手背留空** ✓
    #    这样在 Blockbench 里转一圈、或者在游戏里放一次，都是**一眼**的事 ✓。
    #    ⚠️ 这是**临时记号**，不是最终美术 ✗ —— 作者随后会把自己的画整块覆盖上去 ✓；
    #      而且它用的颜色（血红 0xB02226）就是整张图最亮的一档 ⇒ 无论世界多黑都看得见 ✓。
    # ------------------------------------------------------------------
    from PIL import ImageDraw
    (px0, py0, pw, ph), *_ = ATLAS["palm"]
    seal = (214, 46, 50)
    draw = ImageDraw.Draw(img)
    cx = px0 + pw // 2
    cy = py0 + ph // 2
    draw.ellipse([px0 + 2, py0 + 2, px0 + pw - 3, py0 + ph - 3], outline=seal)
    for dx in (-3, 0, 3):                      # 三道"掌纹"
        draw.line([cx + dx, cy - 3, cx + dx, cy + 2], fill=seal)
    draw.point((cx, py0 + 1), fill=seal)       # 顶端一点，方向感 ✓

    return img


# ----------------------------------------------------------------------------
#  preview: render the boxes ourselves (front / side / top / three-quarter)
# ----------------------------------------------------------------------------
def shade(color, factor):
    r, g, b = color[0], color[1], color[2]
    return (max(0, min(255, int(r * factor))),
            max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))))


def render_view(elements, tex, yaw, pitch, size, zoom):
    """Dead-simple painter's-algorithm box renderer -- just enough to sanity-check the model.

    For every visible face we rasterise the projected quad and pick the texture sample with
    an inverse-bilinear UV lookup, so a wrong UV shows up here exactly as it would in game.
    """
    import math

    FACES = {
        "north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
        "south": ([(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], (0, 0, 1)),
        "west":  ([(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], (-1, 0, 0)),
        "east":  ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
        "up":    ([(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)], (0, 1, 0)),
        "down":  ([(0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)], (0, -1, 0)),
    }
    LIGHT = {"north": 0.80, "south": 1.05, "west": 0.85, "east": 0.62, "up": 1.20, "down": 0.48}

    # model centre, so the preview is centred regardless of how the bbox moves
    xs0 = min(e["from"][0] for e in elements)
    xs1 = max(e["to"][0] for e in elements)
    ys0 = min(e["from"][1] for e in elements)
    ys1 = max(e["to"][1] for e in elements)
    zs0 = min(e["from"][2] for e in elements)
    zs1 = max(e["to"][2] for e in elements)
    mid = ((xs0 + xs1) / 2.0, (ys0 + ys1) / 2.0, (zs0 + zs1) / 2.0)

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[0] - mid[0], p[1] - mid[1], p[2] - mid[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x * zoom, y * zoom, z * zoom)

    def rotate_dir(n):
        x, y, z = n
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)

    quads = []
    for el in elements:
        f, t = el["from"], el["to"]
        for key, (idx, normal) in FACES.items():
            if rotate_dir(normal)[2] <= 0.0001:
                continue                      # face points away from the camera
            pts = []
            for (ix, iy, iz) in idx:
                pts.append(project((f[0] + (t[0] - f[0]) * ix,
                                    f[1] + (t[1] - f[1]) * iy,
                                    f[2] + (t[2] - f[2]) * iz)))
            depth = sum(p[2] for p in pts) / 4.0
            face = el["faces"].get(key)
            if not face:
                continue
            quads.append((depth, [project_pt(p, size) for p in pts], key, face["uv"]))
    quads.sort(key=lambda r: -r[0])

    out = Image.new("RGBA", (size, size), (14, 11, 20, 255))
    op = out.load()
    step = max(4, size // 10)
    for i in range(0, size, step):
        for j in range(size):
            op[i, j] = (30, 23, 40, 255)
            op[j, i] = (30, 23, 40, 255)

    for depth, pts, key, uvs in quads:
        u0, v0, u1, v1 = uvs
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for yy in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
            for xx in range(max(0, int(min(xs))), min(size, int(max(xs)) + 1)):
                uv = quad_uv(pts, (xx + 0.5, yy + 0.5))
                if uv is None:
                    continue
                su, sv = uv
                tu = u0 + (u1 - u0) * su
                tv = v0 + (v1 - v0) * sv
                col = tex.getpixel((int(min(TEX - 1, max(0, tu))), int(min(TEX - 1, max(0, tv)))))
                op[xx, yy] = shade(col, LIGHT[key]) + (255,)
    return out


def project_pt(p, size):
    return (size / 2.0 + p[0], size / 2.0 - p[1])


def quad_uv(pts, sp):
    """Inverse-bilinear lookup: screen point -> (u,v) in [0,1]^2 inside the quad."""
    # corners in order: p0 p1 p2 p3 = (0,0) (1,0) (1,1) (0,1)
    p0, p1, p2, p3 = pts
    # solve p = p0 + u*(p1-p0) + v*(p3-p0) + u*v*(p0-p1+p2-p3) by a few Newton steps
    u, v = 0.5, 0.5
    ex = p1[0] - p0[0]
    ey = p1[1] - p0[1]
    fx = p3[0] - p0[0]
    fy = p3[1] - p0[1]
    gx = p0[0] - p1[0] + p2[0] - p3[0]
    gy = p0[1] - p1[1] + p2[1] - p3[1]
    for _ in range(6):
        dx = p0[0] + ex * u + fx * v + gx * u * v - sp[0]
        dy = p0[1] + ey * u + fy * v + gy * u * v - sp[1]
        if abs(dx) < 0.05 and abs(dy) < 0.05:
            break
        a11 = ex + gx * v
        a12 = fx + gx * u
        a21 = ey + gy * v
        a22 = fy + gy * u
        det = a11 * a22 - a12 * a21
        if abs(det) < 1e-9:
            return None
        du = (dx * a22 - dy * a12) / det
        dv = (a11 * dy - a21 * dx) / det
        u -= du
        v -= dv
    if -0.01 <= u <= 1.01 and -0.01 <= v <= 1.01:
        return (min(1.0, max(0.0, u)), min(1.0, max(0.0, v)))
    return None


def build_preview(elements, tex):
    zoom = 12.0
    size = 240
    views = [
        ("front", 0, -12),
        ("side", 90, -12),
        ("top", 0, -78),
        ("3/4", 40, -32),
    ]
    try:
        from PIL import ImageDraw
        draw_labels = True
    except Exception:
        draw_labels = False

    board = Image.new("RGBA", (size * len(views), size), (12, 10, 18, 255))
    for i, (label, yaw, pitch) in enumerate(views):
        cell = render_view(elements, tex, yaw, pitch, size, zoom)
        board.paste(cell, (i * size, 0))
        if draw_labels:
            d = ImageDraw.Draw(board)
            d.text((i * size + 6, 6), label, fill=(180, 165, 210, 255))
    return board


# ----------------------------------------------------------------------------
def main():
    boxes = recenter(build_boxes())
    model = to_model(boxes)
    tex = build_texture()

    # sanity: every UV inside the atlas
    for el in model["elements"]:
        for key, face in el["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert 0 <= u0 <= u1 <= TEX and 0 <= v0 <= v1 <= TEX, \
                "UV out of atlas: %s %s" % (key, face["uv"])
            assert u1 > u0 and v1 > v0, "degenerate UV: %s %s" % (key, face["uv"])

    # bbox report (the spell json needs this to pick a scale)
    xs0 = min(e["from"][0] for e in model["elements"])
    xs1 = max(e["to"][0] for e in model["elements"])
    ys0 = min(e["from"][1] for e in model["elements"])
    ys1 = max(e["to"][1] for e in model["elements"])
    zs0 = min(e["from"][2] for e in model["elements"])
    zs1 = max(e["to"][2] for e in model["elements"])

    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(TEXTURE_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW_PATH), exist_ok=True)

    with open(MODEL_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")
    tex.save(TEXTURE_PATH)
    build_preview(model["elements"], tex).save(PREVIEW_PATH)

    print("wrote %s" % os.path.relpath(MODEL_PATH, REPO))
    print("wrote %s" % os.path.relpath(TEXTURE_PATH, REPO))
    print("wrote %s" % os.path.relpath(PREVIEW_PATH, REPO))
    print("elements : %d" % len(model["elements"]))
    print("bbox     : x %d..%d   y %d..%d   z %d..%d  (units, 16 = 1 block)"
          % (xs0, xs1, ys0, ys1, zs0, zs1))
    print("bbox blk : %.2f x %.2f x %.2f blocks"
          % ((xs1 - xs0) / 16.0, (ys1 - ys0) / 16.0, (zs1 - zs0) / 16.0))
    print("center   : %.2f, %.2f, %.2f (units) -- put this in the render translate if needed"
          % ((xs0 + xs1) / 2.0, (ys0 + ys1) / 2.0, (zs0 + zs1) / 2.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
