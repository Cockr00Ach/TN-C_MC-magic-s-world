"""TN-C 战士武器：模型 / 贴图 / 预览图生成器。

为什么用脚本而不是手写 JSON：
  * 一把武器 = 若干长方体 + 每块的颜色角色（刃、护手、柄、宝石……），
    模型和贴图必须**同时**定 UV 才不会错位，手写 7 份必然出事；
  * 贴图是像素画，配色/描边/高光是规则化的，脚本一次写好、后面改色只改调色板；
  * 预览图也吃同一份几何：拿模型数据做正交投影，所见即所得。

坐标口径：X = 宽，Y = 厚（0..1），Z = 长（0 在握把处，向 +Z 伸出）。
显示变换（第三人称怎么握）直接写在脚本里，不依赖任何已有武器的 json。
"""

import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "models", "item")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "tnc", "textures", "item")
PREVIEW = os.path.join(ROOT, "docs", "previews")
FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"

TEX_SIZE = 128
TEXEL = 4

# 握在手里的姿态：所有新武器共用（握把在原点、刃沿 +Z，所以这组旋转平移对它们都成立）。
DISPLAY = {
    "gui": {"rotation": [-90, 0, 0]},
    "thirdperson_righthand": {"rotation": [-79, 0, 89],
                              "translation": [-7, 6.25, 1.25],
                              "scale": [1.45, 1.5, 1.5]},
    "thirdperson_lefthand": {"rotation": [-83, 0, -91],
                             "translation": [7.75, 5.5, 2],
                             "scale": [1.45, 1.5, 1.5]},
    "firstperson_righthand": {"rotation": [40, -180, 94],
                              "translation": [6, 4.75, 0]},
    "firstperson_lefthand": {"rotation": [-133, 0, 39],
                             "translation": [-9.25, 3.75, 0]},
    "ground": {"translation": [0, 4.5, 0], "scale": [.5, .5, .5]},
    "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -9.5], "scale": [.65, .65, .65]},
}


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def dark(c, t=0.45):
    return mix(c, (0, 0, 0, 255), t)


def light(c, t=0.35):
    return mix(c, (255, 255, 255, 255), t)


PALETTES = {
    "dragon": dict(
        blade=hx("#7a1a12"), blade_hi=hx("#d8543a"), edge=hx("#ffd08a"),
        guard=hx("#2e0c0c"), guard_hi=hx("#b83a22"), grip=hx("#1d0f10"),
        wrap=hx("#6b2418"), gem=hx("#ffb340"), gem_hi=hx("#fff3cc"),
        accent=hx("#ff8a3a"),
    ),
    "frost": dict(
        blade=hx("#5b7f99"), blade_hi=hx("#cfe6f2"), edge=hx("#f4fdff"),
        guard=hx("#1f3648"), guard_hi=hx("#79b0d0"), grip=hx("#16232e"),
        wrap=hx("#35536b"), gem=hx("#9fe8ff"), gem_hi=hx("#ffffff"),
        accent=hx("#d9f6ff"),
    ),
    "star": dict(
        blade=hx("#97a3ad"), blade_hi=hx("#e6eef4"), edge=hx("#ffffff"),
        guard=hx("#5c4a28"), guard_hi=hx("#e0be6c"), grip=hx("#2b2318"),
        wrap=hx("#7d6238"), gem=hx("#ffe9a0"), gem_hi=hx("#ffffff"),
        accent=hx("#ffe9a0"),
    ),
    "crimson": dict(
        blade=hx("#8a2415"), blade_hi=hx("#e0603a"), edge=hx("#ffe0a8"),
        guard=hx("#41150f"), guard_hi=hx("#bf4c26"), grip=hx("#241310"),
        wrap=hx("#70301c"), gem=hx("#ff8a3c"), gem_hi=hx("#ffe6bb"),
        accent=hx("#ffae66"),
    ),
    "doom": dict(
        blade=hx("#523c6b"), blade_hi=hx("#b897de"), edge=hx("#f0e2ff"),
        guard=hx("#241a30"), guard_hi=hx("#8468a8"), grip=hx("#1e1728"),
        wrap=hx("#53406e"), gem=hx("#c98bff"), gem_hi=hx("#f4e8ff"),
        accent=hx("#dcb8ff"),
    ),
    "earth": dict(
        blade=hx("#6b4d2c"), blade_hi=hx("#cfa76d"), edge=hx("#f6dfae"),
        guard=hx("#33261a"), guard_hi=hx("#9a7548"), grip=hx("#231a12"),
        wrap=hx("#5c4229"), gem=hx("#ffcf6a"), gem_hi=hx("#fff0c8"),
        accent=hx("#ffd98a"),
    ),
    "sky": dict(
        blade=hx("#356076"), blade_hi=hx("#a6dbef"), edge=hx("#f2fbff"),
        guard=hx("#1d3742"), guard_hi=hx("#79b6cc"), grip=hx("#17262d"),
        wrap=hx("#35525f"), gem=hx("#7fe3ff"), gem_hi=hx("#ffffff"),
        accent=hx("#c2f1ff"),
    ),
}


# ----------------------------------------------------------------------
#  武器定义
#      parts: (name, box, style, mode[, rotation])
#      style -> blade / edge / guard / grip / gem
#      mode  -> 'slab' 薄的刀身杆子（侧面取边缘列）；'block' 方块头（每面都用整块）
#      rotation -> ('y', -12.0, (x, y, z)) 绕轴旋转，用来做弯刀/镰刀/戟
# ----------------------------------------------------------------------
WEAPONS = [
    dict(
        id="dragon_sword", palette="dragon", zh="龙之剑", en="Dragonfang",
        family="longsword",
        parts=[
            ("pommel", (6.2, 0.0, -1.6, 7.8, 1.0, 0.2), "gem", "block"),
            ("grip", (6.5, 0.0, 0.2, 7.5, 1.0, 3.9), "grip", "slab"),
            ("guard", (5.0, 0.0, 3.9, 9.0, 1.0, 5.5), "guard", "block"),
            ("horn_l", (4.0, 0.0, 3.2, 5.2, 1.0, 5.8), "guard", "block", ("y", -26.0, (5.2, 0.5, 5.0))),
            ("horn_r", (8.8, 0.0, 3.2, 10.0, 1.0, 5.8), "guard", "block", ("y", 26.0, (8.8, 0.5, 5.0))),
        ],
        thick=1.3, step=1.0, edge_from=0.84,
        profile=[(5.5, 5.0, 9.0), (7.0, 5.1, 8.9), (9.0, 5.25, 8.75),
                 (11.5, 5.5, 8.5), (13.5, 5.7, 8.3), (15.2, 5.95, 8.05),
                 (16.4, 6.2, 7.8), (17.0, 6.6, 7.4)],
    ),
    dict(
        id="frost_katana", palette="frost", zh="霜月太刀", en="Frostmoon Katana",
        family="longsword",
        parts=[
            ("pommel", (6.5, 0.0, -1.1, 7.9, 1.0, 0.1), "guard", "block"),
            ("grip", (6.7, 0.0, 0.1, 7.6, 1.0, 4.3), "grip", "slab"),
            ("tsuba", (4.9, 0.0, 4.3, 9.5, 1.0, 5.2), "guard", "block"),
        ],
        thick=1.2, step=1.0, edge_from=0.84,
        profile=[(5.2, 6.72, 8.32), (9.4, 6.62, 8.22), (12.8, 6.50, 8.10),
                 (15.4, 6.42, 8.02), (16.5, 6.85, 7.60)],
    ),
    dict(
        id="star_rapier", palette="star", zh="晨星细剑", en="Morningstar Rapier",
        family="longsword",
        parts=[
            ("pommel", (6.6, 0.0, -1.2, 7.8, 1.0, 0.1), "gem", "block"),
            ("grip", (6.85, 0.0, 0.1, 7.55, 1.0, 3.5), "grip", "slab"),
            ("cup", (5.2, 0.0, 3.5, 9.2, 1.0, 4.5), "guard", "block"),
            ("bow", (5.0, 0.0, 4.5, 5.8, 1.0, 6.8), "guard", "slab"),
        ],
        thick=1.1, step=1.0, edge_from=0.70,
        profile=[(4.5, 6.98, 7.58), (10.5, 7.05, 7.51), (14.5, 7.12, 7.44),
                 (16.0, 7.20, 7.40), (16.9, 7.28, 7.34)],
    ),
    dict(
        id="crimson_warblade", palette="crimson", zh="赤霄斩马刀", en="Crimson Warblade",
        family="greatsword",
        parts=[
            ("pommel", (6.4, 0.0, -1.3, 8.0, 1.0, 0.1), "guard", "block"),
            ("grip", (6.6, 0.0, 0.1, 7.8, 1.0, 5.0), "grip", "slab"),
            ("guard", (5.1, 0.0, 5.0, 9.3, 1.0, 6.1), "guard", "block"),
        ],
        thick=1.4, step=1.0, edge_from=0.82,
        profile=[(6.0, 4.75, 8.45), (10.0, 4.45, 8.55), (13.4, 4.25, 8.40),
                 (15.6, 4.15, 7.85), (17.0, 4.45, 7.10)],
    ),
    dict(
        id="doom_scythe", palette="doom", zh="收割巨镰", en="Doom Scythe",
        family="greatsword",
        parts=[
            ("butt", (6.9, 0.0, -1.2, 8.1, 1.0, 0.2), "guard", "block"),
            ("shaft", (7.1, 0.0, 0.2, 7.9, 1.0, 12.4), "grip", "slab"),
            ("collar", (6.4, 0.0, 12.4, 8.6, 1.0, 13.2), "guard", "block"),
            ("blade_1", (6.2, 0.0, 13.2, 8.2, 1.2, 15.8), "blade", "slab", ("y", 10.0, (7.4, 0.6, 14.5))),
            ("blade_2", (3.4, 0.0, 14.0, 6.6, 1.2, 15.4), "blade", "slab", ("y", 22.0, (6.6, 0.6, 14.7))),
            ("blade_3", (1.2, 0.0, 13.0, 3.6, 1.2, 15.2), "edge", "slab", ("y", 44.0, (3.5, 0.6, 14.1))),
        ],
    ),
    dict(
        id="earthshaker_hammer", palette="earth", zh="裂地战锤", en="Earthshaker Maul",
        family="hammer",
        parts=[
            ("butt", (6.9, 0.0, -1.1, 8.1, 1.0, 0.2), "guard", "block"),
            ("shaft", (7.15, 0.0, 0.2, 7.85, 1.0, 10.6), "grip", "slab"),
            ("collar", (6.6, 0.0, 10.2, 8.4, 1.0, 11.0), "guard", "block"),
            ("head", (4.9, 0.3, 11.0, 10.3, 3.2, 15.2), "blade", "block"),
            ("band_l", (4.7, 0.1, 11.0, 5.5, 3.4, 15.2), "guard", "block"),
            ("band_r", (9.7, 0.1, 11.0, 10.5, 3.4, 15.2), "guard", "block"),
            ("spike_l", (3.6, 0.9, 12.0, 4.9, 2.6, 14.4), "edge", "block"),
            ("spike_r", (10.3, 0.9, 12.0, 11.6, 2.6, 14.4), "edge", "block"),
        ],
    ),
    dict(
        id="sky_halberd", palette="sky", zh="破天戟", en="Skybreaker Halberd",
        family="spear",
        parts=[
            ("butt", (6.9, 0.0, -1.3, 8.1, 1.0, 0.2), "guard", "block"),
            ("shaft", (7.15, 0.0, 0.2, 7.85, 1.0, 15.2), "grip", "slab"),
            ("collar", (6.6, 0.0, 10.4, 8.4, 1.0, 11.2), "guard", "block"),
            ("axe", (3.2, 0.0, 11.2, 7.2, 1.2, 15.4), "blade", "slab", ("y", -16.0, (7.2, 0.6, 13.2))),
            ("hook", (7.8, 0.0, 11.8, 10.0, 1.2, 12.9), "edge", "slab"),
            ("spike", (7.1, 0.0, 15.2, 7.9, 1.2, 18.8), "edge", "slab"),
        ],
    ),
]


# ----------------------------------------------------------------------
#  贴图：按零件的模型坐标铺进 128x128，共享连续的刃面纹理
# ----------------------------------------------------------------------
def refine_definitions():
    """Distinct silhouettes, actual blade points and layered fittings; keep all gameplay IDs."""
    by_id = {w['id']: w for w in WEAPONS}
    def part(name, box, style='guard', mode='slab', rotation=None):
        return (name, box, style, mode) + ((rotation,) if rotation else ())
    def fit(w, name, box, style='guard', mode='slab', rotation=None):
        w['parts'].append(part(name, box, style, mode, rotation))
    for w in WEAPONS:
        w['step'] = .25
        w['edge_from'] = 1.1  # The cutting edge runs lengthwise, never as repeating end caps.
        for i, p in enumerate(w['parts']):
            if len(p) > 4:
                axis, angle, origin = p[4]
                angle = min((-45, -22.5, 0, 22.5, 45), key=lambda v: abs(v-angle))
                w['parts'][i] = p[:4] + ((axis, angle, origin),)
    w = by_id['dragon_sword']
    w['thick'] = .85
    w['profile'] = [(5.5,5.65,8.35),(8,5.5,8.5),(13,5.8,8.2),(17.5,6.3,7.7),(20.5,6.98,7.02)]
    fit(w,'fuller',(6.83,.84,6.5,7.17,.94,16.8),'grip')
    fit(w,'crest',(6.25,.94,5.2,7.75,1.25,6.6),'gem','block')
    for sign in (-1,1):
        x = 7 + sign*1.6
        fit(w,'wing'+str(sign),(x-.35,-.18,4.4,x+.35,1.15,6.2),'guard','block',('y',sign*45,(x,.5,5)))
    w = by_id['frost_katana']; w['thick'] = .6
    w['parts'] = [p for p in w['parts'] if p[0] != 'tsuba']
    fit(w,'tsuba',(5.8,-.15,4.3,8.8,1.1,4.8),'guard','block')
    fit(w,'habaki',(6.65,-.05,4.8,8.0,.7,5.65),'gem','block')
    w['profile'] = [(5.2,6.8,8.05),(10,6.9,8.15),(15,7.3,8.5),(19,7.9,9.05),(21.5,9.15,9.2)]
    w = by_id['star_rapier']; w['thick'] = .48
    w['profile'] = [(4.5,6.93,7.62),(12,7.05,7.5),(19,7.18,7.38),(22,7.27,7.29)]
    w['parts'] = [p for p in w['parts'] if p[0] not in ('bow','cup')]
    fit(w,'cup',(5.9,-.15,3.8,8.65,1.1,4.5),'guard','block')
    fit(w,'quillon_l',(4.8,.15,4.1,6.1,.75,4.55),'guard')
    fit(w,'quillon_r',(8.5,.15,4.1,9.8,.75,4.55),'guard')
    for n,box,angle in [('lower',(5.6,.15,.65,5.95,.8,2.0),-22.5),('middle',(5.1,.15,1.7,5.45,.8,3.35),0),('upper',(5.45,.15,3.0,5.8,.8,4.3),22.5)]:
        fit(w,'basket_'+n,box,'guard','slab',('y',angle,((box[0]+box[3])/2,.5,(box[2]+box[5])/2)))
    w = by_id['crimson_warblade']; w['thick'] = 1.0
    w['profile'] = [(6,5.65,8.05),(11,5.35,8.65),(17,5.05,8.9),(20.2,5.3,8.5),(22,7.8,7.85)]
    fit(w,'spine',(5.55,1.0,6.4,5.85,1.16,15.8),'guard')
    fit(w,'seal',(6.35,1.02,7,7.55,1.28,8.2),'gem','block')
    w = by_id['doom_scythe']
    w['parts'] = [p for p in w['parts'] if not p[0].startswith('blade_')]
    # The blade curves across X and down towards the shaft: unmistakably a scythe, not a pickaxe.
    w['thick'] = .75
    w['profile'] = [(11.5,-1.8,-1.74),(12.5,-1.75,-.4),(14,-1.35,1.4),(15.5,-.2,3.9),(16.6,1.2,6.2),(17.4,3.5,8.4),(18,6.4,8.2)]
    fit(w,'socket',(6.35,-.35,12.4,8.65,1.55,14.3),'guard','block')
    fit(w,'neck',(7.15,-.05,14.1,7.85,.95,17.7),'guard','block')
    fit(w,'soul',(6.8,1.55,12.8,8.1,1.85,14.1),'gem','block')
    for z in (3.2,7.8,11.6): fit(w,'ferrule_'+str(z),(6.94,-.12,z,8.06,1.12,z+.35),'guard','block')
    w = by_id['earthshaker_hammer']
    w['parts'] = [p for p in w['parts'] if p[0] not in ('head','spike_l','spike_r')]
    fit(w,'core',(5.25,.3,11.3,10,3.15,14.8),'grip','block')
    fit(w,'crown',(5.7,3.15,11.8,9.5,3.5,14.3),'blade','block')
    fit(w,'rune',(6.9,3.5,12.1,8.3,3.7,13.9),'gem','block')
    for sign in (-1,1):
        x = 7.6+sign*3.05
        fit(w,'face_'+str(sign),(x-.45,.5,11.7,x+.45,2.9,14.4),'blade','block')
        fit(w,'ridge_'+str(sign),(x-.65,.95,12.15,x+.65,2.45,13.95),'edge','block')
    for z in (3,7.7): fit(w,'binding_'+str(z),(6.94,-.12,z,8.06,1.12,z+.35),'guard','block')
    w = by_id['sky_halberd']
    w['parts'] = [p for p in w['parts'] if p[0] not in ('axe','spike','hook')]
    w['thick'] = .7
    w['profile'] = [(10.9,5.9,7.5),(12.4,3.0,7.5),(14.1,2.35,7.5),(15.8,3.8,7.5),(17,6.9,7.5),(20.8,7.2,7.25)]
    fit(w,'reverse_hook',(7.85,.1,12.2,9.6,.85,13.1),'guard','slab',('y',-22.5,(8,.5,12.5)))
    fit(w,'star',(6.7,.78,14.4,7.9,1.1,15.6),'gem','block')
    for z in (3,8,11): fit(w,'ring_'+str(z),(6.95,-.1,z,8.05,1.1,z+.35),'guard','block')
    PALETTES['dragon'].update(blade=hx('#48272e'),blade_hi=hx('#9f5261'),edge=hx('#ddc9a2'),guard_hi=hx('#b78648'))
    PALETTES['crimson'].update(blade=hx('#39363d'),blade_hi=hx('#7b626b'),edge=hx('#d6b89b'),guard_hi=hx('#ad5740'))
    PALETTES['earth'].update(blade=hx('#515258'),blade_hi=hx('#99969a'),edge=hx('#d4c2a0'),guard_hi=hx('#ad874f'))


refine_definitions()


def _noise(c, i, j, amt=0.05):
    n = ((i * 73 + j * 151) % 11) / 11.0 - 0.5
    return mix(c, (255, 255, 255, 255) if n > 0 else (0, 0, 0, 255), abs(n) * amt)


def _tx(v, off):
    return int(round((v + off) * TEXEL))


def sprite_offsets(weapon):
    """模型坐标 (x, z) -> 贴图像素 (u, v)，居中。"""
    xs, zs = [], []
    for part in expand_profile(weapon):
        x1, y1, z1, x2, y2, z2 = part[1]
        xs += [x1, x2]
        zs += [z1, z2]
    ox = (TEX_SIZE / TEXEL - (max(xs) - min(xs))) / 2 - min(xs)
    oz = (TEX_SIZE / TEXEL - (max(zs) - min(zs))) / 2 - min(zs)
    return ox, oz


def part_rect(part, ox, oz):
    x1, y1, z1, x2, y2, z2 = part[1]
    u1, u2 = _tx(x1, ox), _tx(x2, ox)
    v1, v2 = _tx(z1, oz), _tx(z2, oz)
    return (u1, v1, max(u2, u1 + 1), max(v2, v1 + 1))


def paint_span(px, v, cols, style, pal, tint, vfrac):
    """像素画不考虑渐变：每行按位置分 5 档明暗，靠边一定描边。"""
    n = len(cols)
    base, hi, edge = pal["blade"], pal["blade_hi"], pal["edge"]
    if style in ("blade", "edge"):
        tones = [dark(base, 0.62), dark(base, 0.28), base, mix(base, hi, 0.72), hi, mix(hi, edge, 0.60)]
        for k, u in enumerate(cols):
            f = (k + 0.5) / n
            idx = 2 + int(round(f * 2.0))
            if k == 0:
                idx = 0
            elif k == 1:
                idx = min(idx, 2)
            elif k == n - 1:
                idx = 5
            c = tones[max(0, min(5, idx))]
            if style == "edge":
                c = mix(c, edge, 0.45)
            px[u, v] = dark(c, tint) if tint > 0 else c
    elif style == "guard":
        tones = [dark(pal["guard"], 0.35), pal["guard"], mix(pal["guard"], pal["guard_hi"], 0.55),
                 pal["guard_hi"], light(pal["guard_hi"], 0.25)]
        idx = 4 if vfrac < 0.18 else (3 if vfrac < 0.45 else (1 if vfrac > 0.85 else 2))
        for k, u in enumerate(cols):
            c = tones[idx]
            if k == 0 or k == n - 1:
                c = dark(c, 0.35)
            px[u, v] = c
    elif style == "grip":
        for k, u in enumerate(cols):
            band = ((v + k // 2) // 4) % 2 == 0
            c0 = mix(pal["grip"], pal["wrap"], 0.70) if band else pal["grip"]
            c = c0 if 0 < k < n - 1 else dark(c0, 0.25)
            px[u, v] = c
    else:  # gem
        gems = [dark(pal["gem"], 0.30), pal["gem"], pal["gem_hi"]]
        for k, u in enumerate(cols):
            f = (k + 0.5) / n
            idx = 2 if 0.18 < f < 0.72 else (1 if f < 0.18 else 0)
            px[u, v] = gems[idx]


def build_texture(weapon):
    pal = PALETTES[weapon["palette"]]
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    ox, oz = sprite_offsets(weapon)
    parts = expand_profile(weapon)

    rects, style_at, owner_at = {}, {}, {}
    for part in parts:
        rect = part_rect(part, ox, oz)
        rects[part[0]] = (rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1])
        for v in range(rect[1], rect[3]):
            for u in range(rect[0], rect[2]):
                style_at[(u, v)] = part[2]
                owner_at[(u, v)] = part[0]

    zs = [p[1][2] for p in parts] + [p[1][5] for p in parts]
    zlo, zhi = min(zs), max(zs)
    px = img.load()
    accent = pal.get("accent")

    for v in range(TEX_SIZE):
        cols = sorted(u for (u, vv) in style_at if vv == v)
        if not cols:
            continue
        runs = []
        for u in cols:
            if runs and u == runs[-1][-1] + 1:
                runs[-1].append(u)
            else:
                runs.append([u])
        z = (v + 0.5) / TEXEL
        t = min(1.0, max(0.0, (z - zlo) / max(1e-6, zhi - zlo)))
        ls = 0.66 + 0.40 * math.sin(math.pi * (0.14 + 0.80 * t))
        if t > 0.94:
            ls *= 0.80
        for run in runs:
            style = style_at[(run[0], v)]
            owner = owner_at[(run[0], v)]
            r1, r2 = rects[owner][1], rects[owner][1] + rects[owner][3]
            vfrac = (v - r1 + 0.5) / max(1, r2 - r1)
            tint = 0.0 if ls > 0.66 else 0.18
            paint_span(px, v, run, style, pal, tint, vfrac)

    # 刀身餐纹：沿沿刃中线的点状碎光
    if accent:
        for part in parts:
            if part[2] not in ("blade", "edge"):
                continue
            u1, v1, u2, v2 = rects[part[0]]
            w, h = u2 - u1, v2 - v1
            if w < 6 or h < 3:
                continue
            col = u1 + max(2, int(w * 0.40))
            for i in range(v1 + 1, v2 - 1, 3):
                px[col, i] = mix(px[col, i], accent, 0.65)

    # 内描边：左/上/下压暗，右侧提亮
    src = img.copy().load()
    for v in range(TEX_SIZE):
        for u in range(TEX_SIZE):
            if src[u, v][3] == 0:
                continue
            left = src[u - 1, v][3] if u > 0 else 0
            right = src[u + 1, v][3] if u + 1 < TEX_SIZE else 0
            up = src[u, v - 1][3] if v > 0 else 0
            down = src[u, v + 1][3] if v + 1 < TEX_SIZE else 0
            c = src[u, v]
            if right == 0:
                continue  # 右缘的亮边已经在 paint_span 里上了，不重复加
            if left == 0 or up == 0 or down == 0:
                px[u, v] = dark(c, 0.62)
    # Sparse engraving on broad blades. Leave the edge clean and the metal readable.
    if weapon.get('profile'):
        prof = weapon['profile']; lo, hi = prof[0][0], prof[-1][0]
        for z in np.arange(lo + 1.0, min(hi - 2, lo + 7), 1.35):
            mid = (_lerp_profile(prof, z, 0) + _lerp_profile(prof, z, 1)) / 2
            u, v = _tx(mid, ox), _tx(z, oz)
            if 1 <= u < TEX_SIZE-1 and 1 <= v < TEX_SIZE-1 and px[u,v][3]:
                col = mix(pal['blade'], pal['gem'], .45)
                for du,dv in ((0,0),(-1,1),(1,1),(0,2)):
                    if px[u+du,v+dv][3]: px[u+du,v+dv] = col
    return img, rects


# ----------------------------------------------------------------------
#  模型 JSON
# ----------------------------------------------------------------------
def face_uvs(rect, box, mode):
    u, v, w, h = rect
    x1, y1, z1, x2, y2, z2 = box
    thick = max(1, int(math.ceil((y2 - y1) * TEXEL)))
    if mode == "block":
        side = [u, v, u + w, v + h]
        end = [u, v, u + w, v + h]
    else:
        vs = v + max(0, (h - thick) // 2)   # 分段接缝取中间色，否则每段之间会露出一道亮线
        side = [u, vs, u + w, vs + thick]
        um = u + max(0, (w - 1) // 2)       # 侧面也取中间列，避开亮边
        end = [um, v, min(um + 1, u + w), v + h]
    return {
        "up": {"uv": [u, v, u + w, v + h]},
        "down": {"uv": [u + w, v, u, v + h]},
        "north": {"uv": side},
        "south": {"uv": side},
        "east": {"uv": end},
        "west": {"uv": end},
    }


def build_model(weapon, rects):
    elements = []
    for part in expand_profile(weapon):
        box = part[1]
        mode = part[3]
        rot = part[4] if len(part) > 4 else None
        element = {
            "from": [round(v, 2) for v in box[:3]],
            "to": [round(v, 2) for v in box[3:]],
            "faces": face_uvs(rects[part[0]], box, mode),
        }
        # Minecraft item UVs are always 0..16, regardless of PNG resolution.
        for face in element['faces'].values():
            face['uv'] = [round(value * 16 / TEX_SIZE, 5) for value in face['uv']]
            face['texture'] = '#0'
        if rot:
            axis, angle, origin = rot
            element["rotation"] = {"angle": angle, "axis": axis,
                                   "origin": [round(o, 2) for o in origin]}
        elements.append(element)
    grip = next((p for p in weapon['parts'] if p[2] == 'grip'), None)
    grip_box = grip[1] if grip else (6.5,0,0,7.5,1,4)
    grip_at = [(grip_box[0]+grip_box[3])/2, (grip_box[1]+grip_box[4])/2,
               min(2.05, (grip_box[2]+grip_box[5])/2)]
    return {
        "credit": "TN-C 战士武器（tools/weapons 生成，勿手改）",
        "texture_size": [TEX_SIZE, TEX_SIZE],
        "tnc_grip": grip_at,
        "textures": {"0": "tnc:item/" + weapon["id"], "particle": "tnc:item/" + weapon["id"]},
        "display": calibrated_display(grip_at),
        "elements": elements,
    }


def transform_point(point, display, left=False):
    """Vanilla ItemTransform: translation * rotationXYZ * scale * (model - .5)."""
    rotation = list(display.get('rotation', [0,0,0]))
    translation = list(display.get('translation', [0,0,0]))
    if left:
        rotation[1] *= -1; rotation[2] *= -1; translation[0] *= -1
    p = tuple((point[i]/16-.5)*display.get('scale',[1,1,1])[i] for i in range(3))
    for axis, angle in reversed(list(zip('xyz', rotation))):
        p = AXIS_ROT[axis](math.radians(angle), p)
    return tuple(p[i]+translation[i]/16 for i in range(3))


def calibrated_display(grip):
    """Pin every custom grip to the same hand-space position as a vanilla held sword."""
    import copy
    display = copy.deepcopy(DISPLAY)
    right = display['thirdperson_righthand']
    display['thirdperson_lefthand'] = copy.deepcopy(right)
    display['thirdperson_lefthand']['rotation'][2] *= -1
    for left in (False, True):
        reference = {'rotation':[0,90 if left else -90,-55 if left else 55],
                     'translation':[0,4,.5], 'scale':[.85]*3}
        target = transform_point([3,3,8], reference, left)
        slot = display['thirdperson_lefthand' if left else 'thirdperson_righthand']
        slot['translation'] = [0,0,0]
        current = transform_point(grip, slot, left)
        slot['translation'] = [round((target[i]-current[i])*16*(-1 if left and i==0 else 1),5) for i in range(3)]
    # Long blades are fitted in inventory rather than cropped at their tips.
    display['gui']['scale'] = [.60,.60,.60]
    display['gui']['translation'] = [0,-.8,0]
    return display


# ----------------------------------------------------------------------
#  预览：正交投影 + 仿射贴图采样
# ----------------------------------------------------------------------
AXIS_ROT = {
    "x": lambda a, p: (p[0], p[1] * math.cos(a) - p[2] * math.sin(a), p[1] * math.sin(a) + p[2] * math.cos(a)),
    "y": lambda a, p: (p[0] * math.cos(a) + p[2] * math.sin(a), p[1], -p[0] * math.sin(a) + p[2] * math.cos(a)),
    "z": lambda a, p: (p[0] * math.cos(a) - p[1] * math.sin(a), p[0] * math.sin(a) + p[1] * math.cos(a), p[2]),
}

FACES = [
    ("up", [0b001, 0b011, 0b111, 0b101]),
    ("down", [0b000, 0b100, 0b110, 0b010]),
    ("north", [0b000, 0b001, 0b101, 0b100]),
    ("south", [0b110, 0b111, 0b011, 0b010]),
    ("west", [0b000, 0b010, 0b011, 0b001]),
    ("east", [0b100, 0b101, 0b111, 0b110]),
]


def element_corners(part):
    box = part[1]
    rot = part[4] if len(part) > 4 else None
    x1, y1, z1, x2, y2, z2 = box
    pts = []
    for x in (x1, x2):
        for y in (y1, y2):
            for z in (z1, z2):
                p = (x, y, z)
                if rot:
                    axis, angle, origin = rot
                    q = (p[0] - origin[0], p[1] - origin[1], p[2] - origin[2])
                    q = AXIS_ROT[axis](math.radians(angle), q)
                    p = (q[0] + origin[0], q[1] + origin[1], q[2] + origin[2])
                pts.append(p)
    return pts


def project(p, yaw, pitch):
    x, y, z = p
    cy, sy = math.cos(yaw), math.sin(yaw)
    x, z = x * cy - z * sy, x * sy + z * cy
    cp, sp = math.cos(pitch), math.sin(pitch)
    y, z = y * cp - z * sp, y * sp + z * cp
    return np.array([x, y, z])


def expand_profile(weapon):
    """刀身用控制点描述：(z, 左边, 右边)，每 0.5 单位生成一行方块。"""
    prof = weapon.get("profile")
    if not prof:
        return weapon["parts"]
    thick = weapon.get("thick", 1.2)
    step = weapon.get("step", 0.5)
    edge_from = weapon.get("edge_from", 0.76)
    zs = [p[0] for p in prof]
    z0, z1 = zs[0], zs[-1]
    rows = []
    k = 0
    z = z0
    while z < z1 - 1e-6:
        z2 = min(z + step, z1)
        xl = _lerp_profile(prof, z, 0)
        xr = _lerp_profile(prof, z, 1)
        f = (z - z0) / (z1 - z0)
        style = "edge" if f >= edge_from else "blade"
        rows.append(("blade_%02d" % k, (round(xl, 3), 0.0, round(z, 3), round(xr, 3), thick, round(z2, 3)), style, "slab"))
        k += 1
        z = z2
    return weapon["parts"] + rows


def _lerp_profile(prof, z, idx):
    for i in range(len(prof) - 1):
        a, b = prof[i], prof[i + 1]
        if a[0] <= z <= b[0]:
            f = 0.0 if b[0] == a[0] else (z - a[0]) / (b[0] - a[0])
            return a[1 + idx] + (b[1 + idx] - a[1 + idx]) * f
    return prof[-1][1 + idx]


def render_preview(weapon, tex_img, size=520, yaw_deg=34, pitch_deg=-52):
    tex = np.asarray(tex_img.convert("RGBA"), dtype=np.uint8)
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)

    center = np.array([7.9, 0.7, 8.0])
    faces = []
    for part in expand_profile(weapon):
        proj = [project(np.array(c) - center, yaw, pitch) for c in element_corners(part)]
        for fname, idx in FACES:
            sel = [proj[i] for i in idx]
            a = sel[1] - sel[0]
            b = sel[2] - sel[0]
            n = np.cross(a, b)
            length = np.linalg.norm(n)
            if length < 1e-9:
                continue
            n = n / length
            if n[2] <= 0.02:
                continue
            faces.append((float(np.mean([p[2] for p in sel])), part, fname, sel, n))

    xs = [p[0] for _, _, _, sel, _ in faces for p in sel]
    ys = [p[1] for _, _, _, sel, _ in faces for p in sel]
    span = max(max(xs) - min(xs), max(ys) - min(ys)) or 1.0
    scale = size * 0.80 / span
    ox = size / 2 - (min(xs) + max(xs)) / 2 * scale
    oy = size / 2 + (min(ys) + max(ys)) / 2 * scale

    buf = np.zeros((size, size, 3), dtype=np.float64)
    zbuf = np.full((size, size), -1e9)
    faces.sort(key=lambda t: t[0])

    for depth, part, fname, sel, n in faces:
        pts = [(p[0] * scale + ox, -p[1] * scale + oy) for p in sel]
        x0 = max(0, int(math.floor(min(p[0] for p in pts))))
        x1 = min(size - 1, int(math.ceil(max(p[0] for p in pts))))
        y0 = max(0, int(math.floor(min(p[1] for p in pts))))
        y1 = min(size - 1, int(math.ceil(max(p[1] for p in pts))))
        if x1 <= x0 or y1 <= y0:
            continue
        O = np.array(pts[0])
        E1 = np.array(pts[1]) - O
        E2 = np.array(pts[3]) - O
        det = E1[0] * E2[1] - E1[1] * E2[0]
        if abs(det) < 1e-9:
            continue
        gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        dx = gx - O[0]
        dy = gy - O[1]
        a = (dx * E2[1] - dy * E2[0]) / det
        b = (-dx * E1[1] + dy * E1[0]) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        u, v, w, h = weapon["_rects"][part[0]]
        if fname in ("up", "down"):
            su = u + np.clip(a, 0, 0.999) * w
            sv = v + np.clip(b if fname == "up" else 1 - b, 0, 0.999) * h
        elif fname in ("north", "south"):
            su = u + np.clip(a, 0, 0.999) * w
            sv = np.full_like(a, v + max(0, (h - max(1, 2)) // 2) + 0.5)
        else:
            su = np.full_like(b, u + max(0, (w - 1) // 2) + 0.5)
            sv = v + np.clip(b, 0, 0.999) * h
        tu = np.clip(su.astype(int), 0, TEX_SIZE - 1)
        tv = np.clip(sv.astype(int), 0, TEX_SIZE - 1)
        col = tex[tv, tu].astype(np.float64)
        if fname == "up":
            shade = 1.00
        elif fname == "down":
            shade = 0.55
        elif fname in ("north", "south"):
            shade = 0.86
        else:
            shade = 0.72
        shade *= 0.92 + 0.16 * abs(float(n[1]))
        target = buf[y0:y1 + 1, x0:x1 + 1]
        zt = zbuf[y0:y1 + 1, x0:x1 + 1]
        pixel_depth = sel[0][2] + a * (sel[1][2] - sel[0][2]) + b * (sel[3][2] - sel[0][2])
        write = inside & (pixel_depth >= zt - 1e-6)
        if not write.any():
            continue
        for c in range(3):
            target[:, :, c] = np.where(write, col[:, :, c] * shade, target[:, :, c])
        zt[...] = np.where(write, pixel_depth, zt)

    clipped = np.clip(buf, 0, 255)
    out = np.zeros((size, size, 4), dtype=np.uint8)
    out[:, :, :3] = clipped.astype(np.uint8)
    out[:, :, 3] = np.where(clipped.sum(axis=2) > 0, 255, 0)
    return Image.fromarray(out, "RGBA")


def atlas_sheet(weapon, img, rects, scale=8):
    """UV 对照图：把贴图放大，标出每块零件占的矩形。手绘贴图时照这个对齐。"""
    pad = 8
    big = img.convert("RGBA").resize((img.width * scale, img.height * scale), Image.NEAREST)
    bg = Image.new("RGB", big.size, (26, 27, 33))
    bg.paste(big, (0, 0), big)
    draw = ImageDraw.Draw(bg)
    try:
        font = ImageFont.truetype(FONT, 13)
    except OSError:
        font = ImageFont.load_default()
    for name, (u, v, w, h) in rects.items():
        draw.rectangle([u * scale, v * scale, (u + w) * scale - 1, (v + h) * scale - 1],
                       outline=(255, 210, 90))
        draw.text((u * scale + 2, v * scale + 1), name, font=font, fill=(255, 236, 170))
    out = os.path.join(PREVIEW, "atlas-" + weapon["id"] + ".png")
    bg.save(out)


def contact_sheet(rows, path, cols=2, cell=470, title="TN-C 战士武器"):
    label_h = 46
    nrows = (len(rows) + cols - 1) // cols
    w = cols * cell + 40
    h = 96 + nrows * (cell + label_h) + 24
    sheet = Image.new("RGB", (w, h), (17, 17, 21))
    draw = ImageDraw.Draw(sheet)
    try:
        font_t = ImageFont.truetype(FONT_B, 34)
        font_n = ImageFont.truetype(FONT_B, 23)
        font_s = ImageFont.truetype(FONT, 17)
    except OSError:
        font_t = font_n = font_s = ImageFont.load_default()
    draw.text((24, 22), title, font=font_t, fill=(240, 236, 228))
    for i, (img, name, sub) in enumerate(rows):
        cx = 20 + (i % cols) * cell
        cy = 92 + (i // cols) * (cell + label_h)
        panel = Image.new("RGB", (cell - 30, cell - 30), (26, 27, 33))
        pd = ImageDraw.Draw(panel)
        for k in range(24):
            c = int(30 + k * 1.2)
            pd.rectangle([0, (cell - 30) * k // 24, cell - 30, (cell - 30) * (k + 1) // 24],
                         fill=(c, c, c + 4))
        icon = img.resize((cell - 90, cell - 90), Image.LANCZOS)
        panel.paste(icon, ((cell - 30 - icon.width) // 2, (cell - 30 - icon.height) // 2), icon)
        sheet.paste(panel, (cx + 15, cy))
        draw.text((cx + 18, cy + cell - 28), name, font=font_n, fill=(236, 231, 221))
        draw.text((cx + 18, cy + cell - 2), sub, font=font_s, fill=(146, 144, 142))
    sheet.save(path)


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    rows = []
    for weapon in WEAPONS:
        img, rects = build_texture(weapon)
        weapon["_rects"] = rects
        model = build_model(weapon, rects)
        with open(os.path.join(MODELS, weapon["id"] + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2, ensure_ascii=False)
            f.write("\n")
        img.save(os.path.join(TEX, weapon["id"] + ".png"))
        atlas_sheet(weapon, img, rects)
        prev = render_preview(weapon, img)
        prev.save(os.path.join(PREVIEW, "weapon-" + weapon["id"] + ".png"))
        rows.append((prev, weapon["zh"] + "  ·  " + weapon["en"],
                     weapon["id"] + "  (" + weapon["family"] + ")"))
        print("built", weapon["id"])
    contact_sheet(rows, os.path.join(PREVIEW, "weapons-20261103.png"))


if __name__ == "__main__":
    main()
