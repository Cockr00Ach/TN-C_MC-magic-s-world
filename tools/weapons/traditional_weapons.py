"""Traditional Chinese silhouettes in the existing quarter-unit pixel forge.

Only writes this collection. Museum references and artistic departures are in
docs/warrior/中国传统兵器-20261105.md. Run from any directory.
"""
import json
import math
from pathlib import Path
import weapon_gen as g
import pixel_forge

ROOT = Path(g.ROOT)
IDS = ['yue_bronze_jian', 'willow_leaf_dao', 'crescent_guandao', 'fangtian_ji', 'red_tassel_spear']
IDS += ['ring_pommel_dao', 'han_iron_jian', 'iron_jian_mace', 'monk_spade', 'qimei_staff']


def palette(blade, high, edge, grip, wrap, bronze, jade, silk):
    return dict(blade=g.hx(blade), blade_hi=g.hx(high), edge=g.hx(edge),
                guard=g.hx(bronze), guard_hi=g.hx('e5c17b'), grip=g.hx(grip),
                wrap=g.hx(wrap), gem=g.hx(silk), gem_hi=g.hx(jade), accent=g.hx(jade))


g.PALETTES.update({
    'yue': palette('928245', 'c1af65', 'e9d78e', '302d24', '756543', '706039', '76b8a2', '287a70'),
    'willow': palette('687982', 'aebdc2', 'eaf1ee', '262c29', '53664b', '9e7c42', 'a4bdaa', '587f63'),
    'guan': palette('697e7c', 'b4c8bc', 'ecf1d8', '282c26', '465747', 'a17c3a', '92bda1', '8f3028'),
    'fang': palette('75828b', 'bac9d0', 'eff5ed', '302623', '694336', '9d753c', 'e7c482', '963a30'),
    'hong': palette('6c7a80', 'bdc8cb', 'eff5ed', '392b20', '725239', '94713a', 'e2ad7d', 'b53025'),
})
g.PALETTES['hong']['gem_hi'] = g.hx('f65a42')
g.PALETTES['han'] = palette('6b7d88','bccbd0','e3eeee','28232b','5b3b43','987544','83b9aa','477c71')
g.PALETTES['ring'] = palette('626e77','b3c0c3','e6ece9','302923','77583d','97723e','bd9d6b','754c35')
g.PALETTES['mace'] = palette('555b62','929ba0','bfc6c4','2b2420','735741','88683e','c6a26a','87613d')
g.PALETTES['spade'] = palette('586b6f','a5b7b7','d6e5dd','302b26','665345','9e7c43','c2b38b','8c4d36')
g.PALETTES['wood'] = palette('765239','ae8355','c2975e','483023','93683e','66503b','b09b71','82633f')


def part(name, box, style='guard'):
    return (name, box, style, 'block')


def fittings(long=False):
    if long:
        return [part('shaft', (6.6, .12, -8, 7.4, .92, 12), 'grip'),
                part('butt_shoe', (6.5, 0, -8.8, 7.5, 1.04, -7.5)),
                part('socket', (6.4, -.08, 10.8, 7.6, 1.12, 12.3)),
                part('hand_binding', (6.48, 0, .5, 7.52, 1.04, 3.5), 'grip')]
    return [part('pommel', (6.25, -.1, -1, 7.75, 1.1, 0)),
            part('grip', (6.55, .05, 0, 7.45, .95, 3.5), 'grip'),
            part('ferrule', (6.4, -.05, 3.2, 7.6, 1.05, 3.8))]


def definitions():
    return [
        dict(id=IDS[0], zh='越式青铜剑', en='Yue Bronze Jian', palette='yue', family='longsword',
             parts=fittings()+[part('sword_guard', (5.25, -.1, 3.65, 8.75, 1.1, 4.3))],
             thick=1.05, profile=[(4.25,5.9,8.1),(6,5.8,8.2),(11.5,5.95,8.05),(14,6.15,7.85),(16.5,6.9,7.1)]),
        dict(id=IDS[1], zh='柳叶腰刀', en='Willow-leaf Dao', palette='willow', family='longsword',
             parts=fittings()+[part('disc_guard', (5.5, -.3, 3.65, 8.5, 1.3, 4.2))],
             thick=1.05, profile=[(4.2,6.25,7.8),(8,6.35,8),(11,6.65,8.5),(14,7.3,9.1),(16,8.1,9.3),(17.5,9.4,9.65)]),
        dict(id=IDS[2], zh='偃月刀', en='Crescent Guandao', palette='guan', family='greatsword',
             parts=fittings(True)+[part('blade_throat', (6.1,-.1,11.6,7.9,1.2,13))],
             thick=1.3, profile=[(12.25,6.5,8.25),(14,6.4,9.6),(16,6.5,10.8),(18,7,11.3),(20,8.1,11.1),(22.5,10.8,11.3)]),
        dict(id=IDS[3], zh='方天画戟', en='Fangtian Ji', palette='fang', family='spear',
             parts=fittings(True), thick=1.15,
             profile=[(12,6.3,7.7),(14,5.9,8.1),(16,6.25,7.75),(20.5,6.9,7.1)]),
        dict(id=IDS[4], zh='红缨枪', en='Red-tassel Spear', palette='hong', family='spear',
             parts=fittings(True), thick=1.05,
             profile=[(12,6.65,7.35),(13.5,5.95,8.05),(15,6.1,7.9),(18.25,6.9,7.1)]),
        dict(id=IDS[5], zh='环首刀', en='Ring-pommel Dao', palette='ring', family='longsword',
             parts=[p for p in fittings() if p[0]!='pommel'],thick=1.1,
             profile=[(3.8,6.4,7.8),(12,6.4,7.8),(16,6.5,7.9),(18,7.75,8)]),
        dict(id=IDS[6], zh='汉式铁剑', en='Han Iron Jian', palette='han', family='longsword',
             parts=fittings()+[part('jade_guard',(5.5,-.12,3.6,8.5,1.15,4.25)),
                               part('jade_pommel',(6.2,-.2,-1,7.8,1.2,-.6),'gem')],
             thick=1.1,profile=[(4.2,6.15,7.85),(12,6.25,7.75),(17,6.5,7.5),(19.5,6.9,7.1)]),
        dict(id=IDS[7], zh='四棱铁锏', en='Four-ridge Iron Jian', palette='mace', family='hammer',
             parts=fittings()+[part('disc_guard',(5.5,-.3,3.5,8.5,1.3,4.2)),
                               part('blunt_rod',(6.4,0,4.2,7.6,1.2,15.5),'blade')],thick=1.2),
        dict(id=IDS[8], zh='月牙铲', en='Monk Crescent Spade', palette='spade', family='greatsword',
             parts=fittings(True)+[part('spade_neck',(6.3,0,11.8,7.7,1.1,14))],thick=1.2,
             profile=[(13,5.75,8.25),(14.5,4.5,9.5),(17.5,3.75,10.25),(18.5,4.5,9.5)]),
        dict(id=IDS[9], zh='齐眉棍', en='Qimei Staff', palette='wood', family='spear',
             parts=[part('wood_staff',(6.38,0,-9,7.62,1.24,17),'grip')],thick=1.24),
    ]


def decorate(w, detail=True):
    # Fine geometry is physical on both faces; ghost keeps only the silhouette.
    out = pixel_forge.parts(w, g._lerp_profile) if detail else g.expand_profile_legacy(w)
    out = list(out)
    def add(name, b, style='guard'):
        b=tuple(round(x,4) for x in b)
        if all(round(b[i],2)<round(b[i+3],2) for i in range(3)):
            out.append((name+'_'+str(len(out)), b, style, 'block'))
    def relief(x,z,style='guard',width=.2):
        for ya,yb in ((-.10,0),(w['thick'],w['thick']+.1)):
            add('inlay', (x,ya,z,x+width,yb,z+.22),style)
    def plate(name, profile, style='blade'):
        step=.25 if detail else .75
        z=profile[0][0]
        while z<profile[-1][0]-.001:
            z1=min(z+step,profile[-1][0]); zz=(z+z1)/2
            a,b=(g._lerp_profile(profile,zz,i) for i in (0,1))
            if detail:
                x=a
                while x<b-.001:
                    end=min(b,x+.25); inset=.15 if x==a or end==b else 0
                    add(name,(x,inset,z,end,1.05-inset,z1),'edge' if x==a else style);x=end
            else:add(name,(a,0,z,b,1.05,z1),style)
            z=z1
    id=w['id']
    if id==IDS[0]:
        if detail:
            for i in range(46):
                z=4.5+i*.25
                relief(6.9,z,'blade',.2)  # central spine
                for side in (-1,1):
                    # Quiet, repeating lozenges inspired by Yue bronze decoration.
                    offset=abs((i%12)-6)*.125
                    if offset<.72:relief(6.9+side*offset,z,'guard',.14)
            for x in (5.5,6,7.75,8.25):relief(x,3.85,'gem')
        # Three stepped concentric discs read as a round sword pommel, not a gem.
        for layer in range(3):
            r=.85-layer*.18
            add('pommel_disc',(7-r,-.2-layer*.08,-.95,7+r,1.2+layer*.08,-.7+layer*.16))
    elif id==IDS[1]:
        if detail:
            for i in range(44):
                z=4.5+i*.25;x=g._lerp_profile(w['profile'],z,0)+.3
                relief(x,z,'blade',.16)  # narrow fuller follows the actual curve
            for x in (5.6,8.2):
                for z in (3.7,3.95):relief(x,z,'guard')
    elif id==IDS[2]:
        plate('back_hook',[(13.2,5.2,6.6),(14.5,4.8,6.5),(15.5,5.5,6.6)])
        if detail:
            # Interlocking raised cloud-scrolls, socket cheeks and three rivets.
            for i in range(25):
                z=13+i*.25;x=7.25+(.25 if i%8<4 else .75)
                relief(x,z,'guard',.25)
                if i%8 in (0,3):relief(x+.25,z,'guard',.25)
            for z in (12.1,12.6,13.1):relief(7,z,'guard')
    elif id==IDS[3]:
        # Double crescent wings with open air between the wings and spearhead.
        for side in (-1,1):
            profile=[(11.8,9.2,9.4),(13,9.3,10.1),(15,9.5,10.6),(17,9.1,10.1),(18.5,8.8,9)]
            if side<0:profile=[(z,14-b,14-a) for z,a,b in profile]
            plate('crescent_wing',profile)
            for z in (13,16.25):
                a,b=sorted((7,7+side*2.7))
                add('wing_bridge',(a,.2,z,b,.85,z+.35))
        if detail:
            for i in range(24):relief(6.9,12+i*.25,'guard',.2)
    if id in (IDS[2],IDS[3],IDS[4],IDS[8]):
        # Segmented metal collars sit on the wooden haft, not floating around it.
        for z in (-7.5,-3.5,4.5,8.5,10.4):
            add('haft_collar',(6.42,-.04,z,7.58,1.08,z+.28))
        plate('butt_spike',[(-10,6.9,7.1),(-8.75,6.5,7.5)])
    if id==IDS[4]:
        # Individual silk locks fan backwards below the spear socket.
        for i in range(13 if detail else 7):
            t=i/(12 if detail else 6);spread=(t-.5)*3.6
            for k in range(7 if detail else 3):
                f=k/(6 if detail else 2)
                x=7+spread*(.25+.75*f);z=11.9-f*(2.4+.7*math.cos(t*math.pi))
                add('silk_lock',(x-.09,-.22,z-.4,x+.09,.12,z+.1),'gem')
        add('tassel_knot',(6.3,-.28,11.4,7.7,1.25,11.9),'gem')
    if id==IDS[5]:
        # Open annular pommel; the hole is actual empty space.
        step=.25 if detail else .5
        for ix in range(round(2.5/step)):
            for iz in range(round(2.5/step)):
                x,z=5.75+ix*step,-2.5+iz*step
                r=math.hypot(x+step/2-7,z+step/2+1.25)
                if .72<r<1.24:add('ring_pommel',(x,0,z,x+step,1.05,z+step),'blade')
    if id==IDS[6] and detail:
        for i in range(56):
            relief(6.92,4.5+i*.25,'blade',.16)
            if i%8<3:relief(6.65,4.5+i*.25,'guard',.16)
    if id==IDS[7]:
        for i in range(40 if detail else 8):
            z=4.3+i*(.275 if detail else 1.375)
            d=.275 if detail else 1.375
            add('central_rib',(6.8,-.25,z,7.2,1.45,z+d),'blade')
            for side in (-1,1):
                x=7+side*.7
                add('side_rib',(x-.15,.4,z,x+.15,.8,z+d),'blade')
        add('blunt_tip',(6.55,.15,15.4,7.45,1.05,15.8),'blade')
    if id==IDS[8]:
        # Transverse crescent at the rear, with an open concave centre.
        step=.25 if detail else .5
        for i in range(round(6/step)):
            x=4+i*step;t=(x+step/2-7)/3
            z=-9+1.8*(t*t)
            add('rear_crescent',(x,0,z-.55,x+step,1.1,z+.15),'edge')
        if detail:
            for i in range(16):
                relief(6.1,14+i*.25,'guard',.2);relief(7.7,14+i*.25,'guard',.2)
    if id==IDS[9]:
        for z in (-8.8,-5,0,4,8,12,16.4):
            # Wooden end collars / dark grain rings rather than a metal spear socket.
            add('wood_node',(6.26,-.12,z,7.74,1.36,z+.22),'blade')
        if detail:
            for i in range(52):
                z=-9+i*.5
                add('grain_ridge',(6.48,-.08,z,6.66,0,z+.32),'blade')
                add('grain_ridge',(7.34,1.24,z,7.52,1.32,z+.32),'blade')
    return out


def make_model(w, detailed=True):
    w=dict(w);w['_pixel_parts']=decorate(w,detailed)
    tex,rects=g.build_texture(w);w['_rects']=rects
    model=g.build_model(w,rects)
    bounds=[p[1] for p in w['_pixel_parts']]
    lo=[min(b[i] for b in bounds) for i in range(3)]
    hi=[max(b[i+3] for b in bounds) for i in range(3)]
    scale=min(.68,13.5/max(hi[0]-lo[0],hi[2]-lo[2]))
    model['display']['gui'].update(scale=[scale]*3,
        translation=[round((8-(lo[0]+hi[0])/2)*scale,4),round((8-(lo[2]+hi[2])/2)*scale,4),0])
    return w,tex,model


def main():
    rows=[];manifest=[]
    for weapon in definitions():
        w,tex,model=make_model(weapon)
        g.write_model(w['id'],model);tex.save(Path(g.TEX)/(w['id']+'.png'))
        preview=g.render_preview(w,tex,size=660,yaw_deg=20,pitch_deg=-65)
        preview.save(Path(g.PREVIEW)/('weapon-'+w['id']+'.png'))
        rows.append((preview,w['zh']+' · '+w['en'],w['id']+' / '+w['family']))
        _,_,ghost=make_model(weapon,False)
        path=Path(g.MODELS)/'warrior_ghost'/(w['id']+'.json')
        path.write_text(json.dumps(ghost,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        # A second angle exposes sockets, blade bevels and raised carving depth.
        side=g.render_preview(w,tex,size=500,yaw_deg=64,pitch_deg=-28)
        side.save(Path(g.PREVIEW)/('weapon-'+w['id']+'-detail.png'))
        manifest.append(dict(id=w['id'],blocks=len(model['elements']),
                             quads=sum(len(e['faces']) for e in model['elements']),ghost_blocks=len(ghost['elements'])))
        print(manifest[-1])
    g.contact_sheet(rows,str(Path(g.PREVIEW)/'traditional-weapons-20261105.png'),cols=3,cell=530,title='TN-C · 中国传统兵器 / 实际模型预览')
    path=ROOT/'model-source/traditional-weapons';path.mkdir(exist_ok=True)
    (path/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')


if __name__=='__main__':main()
