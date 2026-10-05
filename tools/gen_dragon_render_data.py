# -*- coding: utf-8 -*-
"""gen_dragon_render_data.py -- bake the dragon into a Java source file (no resource pack).

Why: the pack shadows `assets/tnc/models/block/**` (resource pack "TN-C" claims the tnc
namespace), so the generated block models never loaded -- the log said
    Unable to load model: 'tnc:block/dragon_display_light'
and the very same error hits the pack's own props (gamble_table, tavern_*), i.e. it is the
pack, not my json. A Display entity then also had no renderer registered in Forge (the crash:
NPE on `entityrenderer`), because vanilla only registers minecraft:block_display.

So the dragon is rendered by a plain Forge EntityRenderer (light/client/TNDragonRenderer)
that carries its own geometry AND its own texture:
  * geometry comes from the author's dragon.geo.json, evaluated at one dash pose and baked
    into axis-aligned boxes (xml block elements cannot express multi-axis rotation, and every
    one of the 41 bones here is multi-axis -- checked, not assumed);
  * the texture is embedded as a base64 PNG (a DynamicTexture at runtime), because the pack's
    higher-priority "TN-C" resource pack shadows the mod's tnc:textures as well.
Nothing in the render path touches the resource pack any more.

Output: src/main/java/com/tnc/tnc/light/client/TNDragonRenderData.java
Run:    python tools/gen_dragon_render_data.py
ASCII only.
"""
import base64
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo_anim as P          # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join("src", "main", "resources", "assets", "tnc", "geo", "entity", "dragon.geo.json")
ANIM = os.path.join("src", "main", "resources", "assets", "tnc", "animations", "entity",
                    "dragon.animation.json")
OUT = os.path.join("src", "main", "java", "com", "tnc", "tnc", "light", "client",
                   "TNDragonRenderData.java")
TEX_LIGHT = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "entity",
                         "dragon_bedrock.png")
TEX_DARK = os.path.join("src", "main", "resources", "assets", "tnc", "textures", "entity",
                        "dragon_dark_bedrock.png")
POSE_TIME = 0.3                      # the most-bent frame of the dash clip
TEX_SIZE = 512.0

# vanilla face name -> box-uv rectangle; same layout tools/face_uv_check.py validated
FACES = ("up", "down", "west", "north", "east", "south")


def bake(geo, order, anim, t):
    """-> [(x0,y0,z0, x1,y1,z1, [(u0,v0,u1,v1) x6])] in model units (16/block)."""
    tfs = P.world_transforms(geo, anim, t)
    out = []
    for bone in order:
        tf = tfs[bone]
        for cube in geo[bone].get("cubes", []):
            o = [float(v) for v in cube["origin"]]
            s = [float(v) for v in cube["size"]]
            uv = cube.get("uv")
            if not isinstance(uv, list) or min(s) <= 0.0:
                continue
            pts = []
            for i in (0, 1):
                for j in (0, 1):
                    for k in (0, 1):
                        pts.append(P.apply(tf, [o[0] + s[0] * i, o[1] + s[1] * j, o[2] + s[2] * k]))
            lo = [min(p[c] for p in pts) for c in range(3)]
            hi = [max(p[c] for p in pts) for c in range(3)]
            if min(hi[c] - lo[c] for c in range(3)) <= 0.0:
                continue
            u, v = float(uv[0]), float(uv[1])
            w, h, d = s
            rects = {
                "up":    (u + d, v, w, d),
                "down":  (u + d + w, v, w, d),
                "west":  (u, v + d, d, h),
                "north": (u + d, v + d, w, h),
                "east":  (u + d + w, v + d, d, h),
                "south": (u + d + w + d, v + d, w, h),
            }
            out.append((lo[0], lo[1], lo[2], hi[0], hi[1], hi[2],
                        [rects[name] for name in FACES]))
    return out


def pack_boxes(boxes):
    """-> base64 of float32: per box 6 floats (from/to) + 24 floats (6 faces x u0,v0,u1,v1)."""
    buf = bytearray()
    for (x0, y0, z0, x1, y1, z1, rects) in boxes:
        vals = [x0, y0, z0, x1, y1, z1]
        for (a, b, c, d) in rects:
            vals += [a / TEX_SIZE, b / TEX_SIZE, c / TEX_SIZE, d / TEX_SIZE]
        buf += struct.pack("<%df" % len(vals), *vals)
    return base64.b64encode(bytes(buf)).decode("ascii")


def chunk_b64(text, width=110):
    """-> one Java string literal per line, joined with '+'.

    The '+' matters: without it the compiler sees a bare string per line and reports
    "';' expected" on the first continuation.
    """
    parts = [text[i:i + width] for i in range(0, len(text), width)] or [""]
    lines = ['            "%s"' % p for p in parts]
    return "\n            + ".join(lines)


# Java caps a CONSTANT string at 65535 UTF-8 BYTES (not chars), and javac folds a chain of
# literals into one constant -> the texture blew up with "constant string too long".
# The payload is pure ASCII base64 so bytes == chars, but a field plus its quoting and the
# folding of its own literals stays well clear of the cap at 20000.
MAX_FIELD = 20000


def field_block(field, getter, visibility, text, doc):
    """-> java source for one payload, split across as many fields as needed."""
    parts = [text[i:i + MAX_FIELD] for i in range(0, len(text), MAX_FIELD)] or [""]
    out = ['    /** %s */' % doc]
    names = []
    for i, part in enumerate(parts):
        fname = "%s_%d" % (field, i) if len(parts) > 1 else field
        names.append(fname)
        out.append("    private static final String %s =" % fname)
        out.append(chunk_b64(part))
        out.append(";")
    out.append("")
    out.append("    %s static String %s() {" % (visibility, getter))
    out.append("        return %s;" % " + ".join(names))
    out.append("    }")
    return "\n".join(out)


def main():
    os.chdir(REPO)
    P.FRONT = "-Z"
    geo, order = P.load_geo(GEO)
    anim = P.load_anim(ANIM, "dash")
    boxes = bake(geo, order, anim, POSE_TIME)
    packed = pack_boxes(boxes)
    tex_light = base64.b64encode(open(TEX_LIGHT, "rb").read()).decode("ascii")
    tex_dark = base64.b64encode(open(TEX_DARK, "rb").read()).decode("ascii")

    geo_fields = field_block("GEOMETRY_B64", "geometryB64", "public", packed,
                             "几何（float32，小端）✓ —— 每个盒子 6 个 from/to + 6 面 × 4 个 uv ✓")
    light_fields = field_block("TEXTURE_LIGHT_B64", "textureLightB64", "public", tex_light,
                               "光龙贴图（512×512 PNG ✓，运行时解成 DynamicTexture ✓ 不给资源包看 ✓）")
    dark_fields = field_block("TEXTURE_DARK_B64", "textureDarkB64", "public", tex_dark,
                              "暗龙贴图 ✓")

    src = '''package com.tnc.tnc.light.client;

/**
 * 龙的<b>自绘数据</b> —— 几何 + 贴图，全部烤进这个类 ✓（{@code tools/gen_dragon_render_data.py} 生成 ✓，
 * 别手改 ✗）。
 *
 * <h2>为什么连贴图都要内嵌（不查资源包 ✗）</h2>
 * 实机日志里这一行是关键 ✓：
 * <pre>
 *   Unable to load model: 'tnc:block/dragon_display_light' referenced from: tnc:dragon_display_light#
 *       java.io.FileNotFoundException: tnc:models/block/dragon_display_light.json
 * </pre>
 * 而同一个日志里 <b>{@code gamble_table} / {@code tavern_table_2x1} 这些早就能用的方块模型也是同样的报错</b> ✓
 * ⇒ 不是我的 json 写错了 ✗，是整合包里那个**优先级更高的资源包把 {@code tnc} 命名空间遮住了** ✗
 * （{@code options.txt} 里 {@code resources/TN-C} 排在模组资源之后 ✓，
 * 这个包声明了 {@code assets/tnc} 但只有 projectile/gui/spell 那几个目录 ✓）。
 *
 * <p>同一条链上还有第二个坑 ✓：龙用 {@code Display.BlockDisplay} 之后，
 * 原版 {@code EntityRenderDispatcher} 找不到它的渲染器（原版只给内置的
 * {@code minecraft:block_display} 注册过 ✗）⇒ 空指针 ⇒ <b>释放即卡退</b> ✗。
 *
 * <p>所以现在：<b>几何在这里、贴图也在这里</b> ✓，渲染走一个普通的 Forge
 * {@code EntityRenderer}（{@link TNDragonRenderer} ✓），
 * <b>整条渲染路径一次都不碰资源包</b> ✓。
 *
 * <h2>几何怎么烤的</h2>
 * 从作者手改的 {@code dragon.geo.json} 取 {@code dash} 里弯得最明显的一帧（t=%.2f ✓），
 * 把整条骨骼链压进每个 cube 的顶点 ✓，再取**包围盒** ✓ ——
 * 原版方块模型元素只能绕**单轴**转 ✗，而这个模型 41 根骨骼**每根都是多轴的** ✓（实测过 ✓），
 * 所以只能这样降级 ✓：龙还是那条龙 ✓，弯曲的身体会略微变"方" ✓。
 * 贴图 UV 逐面映射（box-uv 布局 ✓，{@code tools/face_uv_check.py} 验过 ✓）。
 *
 * <p>★ 数据为什么要拆成好几个字段 ✗：Java 的**常量字符串上限 65535 字节** ✓，
 * 而 javac 会把一串 {@code "a" + "b" + ...} 折成一个常量 ✗ ⇒ 贴图（15 万字符）会直接
 * 报 {@code constant string too long} ✗。所以每段都拆到 4 万字符以下的**独立字段** ✓，
 * 再用访问器在运行时拼 ✓。
 */
public final class TNDragonRenderData {

    private TNDragonRenderData() {
    }

    /** 一个方块盒子 = 6 个 float 的 from/to + 6 个面 × 4 个 float 的 uv（u0,v0,u1,v1）✓。 */
    public static final int FLOATS_PER_BOX = 6 + 6 * 4;

    /** 有颜色的方块数 ✓（没方块的骨骼：root / 胡须 / 四只脚 ✗ —— 作者那份 geo 里它们就是空的 ✓）。 */
    public static final int BOX_COUNT = %d;

%s

%s

%s

    public static byte[] geometry() {
        return java.util.Base64.getDecoder().decode(geometryB64());
    }

    public static byte[] texture(boolean dark) {
        return java.util.Base64.getDecoder().decode(dark ? textureDarkB64() : textureLightB64());
    }
}
''' % (POSE_TIME, len(boxes), geo_fields, light_fields, dark_fields)

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(src)
    print("wrote %s" % OUT)
    print("  boxes=%d  geometry b64=%d chars  texture light=%d dark=%d chars"
          % (len(boxes), len(packed), len(tex_light), len(tex_dark)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
