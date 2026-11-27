"""Rebuild all rooted plant surfaces, including open, closed and wet states.
Use --projects DIR to also export the mature geometry as editable Blockbench meshes.
"""
import argparse
from collections import Counter
import json
from math import sin, cos, pi, tau
from pathlib import Path
import re

from botanical_climbing_segments import join_root, generate as make_segments
from botanical_surface import Mesh, atlas, plus, minus, mul, radial, mix, qround, write_bbmodel
from rebuild_magic_plant_world_models import ASSETS, ROOT, FAMILIES, model_refs, dump

SPECIES = tuple(FAMILIES)+('steam_lotus',)
BASE=(8,.1,8)
DETAILS=ROOT/'model-source/botanical/ecology-polish'

def shoots(m,tip,thick=.40,bend=(0,0)):
    mid=mix(BASE,tip,.53)
    m.stem([BASE,plus(mid,(bend[0],.25,bend[1])),tip],[thick,thick*.78,thick*.45])

def basal(m,n,reach,width,lift,phase=.4,arch=1.1):
    m.part='叶丛'
    m.crown((8,.4,8),n,reach,width,lift,arch,1,phase,.11)

def bead(m,p,r,mat=4,h=None,glow=True):
    m.spindle(p,r,h or 1.7*r,mat,sides=7,glow=glow)
def crescent(m,p,r,mat=3,arc=4.5,spin=.1,vertical=False,thick=.13):
    pts=[]
    for j in range(12):
        a=spin+arc*j/11
        pts.append(plus(p,(cos(a)*r,sin(a)*r if vertical else .15*sin(2*a),0 if vertical else sin(a)*r)))
    m.stem(pts,[thick*(.15+.85*sin(pi*j/11)) for j in range(12)],mat,5)

def commission(m,p):
    file=DETAILS/(m.species+'.json')
    if not file.is_file():raise FileNotFoundError(f'Missing Blockbench flower centre {file}')
    authored=json.loads(file.read_text('utf-8-sig'))
    m.part='Blockbench · 专属花心'
    for f in authored:
        m.faces.append((m.part,[plus(p,v) for v in f['vertices']],f['uv'],f.get('glow',False)))

def flowers(m):
    s=m.species
    if s=='homeward_flower':
        p=(8.7,12.5,8.1)
        basal(m,5,4.8,2.4,3.1);m.part='花茎';shoots(m,p,.43,(-1.1,.55))
        m.blade((7.8,7.2,8.3),2.4,4,2,2,.7)
        m.part='向归花冠'
        if m.age<2:
            m.crown(p,5,1.2,2.55,4.1,1.2,2,0,.40)
            return
        m.crown(p,5,4.2,2,1,1.1,3,.12,.29)
        m.crown(plus(p,(0,.23,0)),5,3.25,2.3,2.05,-.43,2,.65,-.12)
        # One elongated pale petal mirrors the compass-like anther.
        m.blade(p,-.8,5.25,1.82,1.7,1.35,3,.14)
        commission(m,(8.7,12.9,8.1))
    elif s in ('mirror_lotus','steam_lotus'):
        steam=s=='steam_lotus';p=(8,3.45 if steam else 2.0,8)
        m.part='浮叶'
        for i in range(4 if steam else 3):
            a=.4+i*2.43
            q=radial(BASE,a,3.25,.55+i*.12)
            m.stem([BASE,radial(BASE,a,2,.22),q],[.23,.16,.12])
            # An eccentric slit stays open between the two edges of each pad.
            for j in range(10):
                b=a+.30+j*5.65/10;c=a+.30+(j+1)*5.65/10
                def petal(ang,r):return radial(q,ang,r,(r/2.80)**2*(.25+.16*cos(ang*3)))
                m.face([q,petal(b,2.80),petal(c,2.80),q],1,[(.5,.5),(.5+.48*cos(b),.5+.48*sin(b)),(.5+.48*cos(c),.5+.48*sin(c)),(.5,.5)],True)
            for ray in range(5):
                b=a+.6+ray*1.15
                m.stem([plus(q,(0,.035,0)),petal(b,1.1),petal(b+.08,2.49)],[.081,.068,.030],3 if steam else 1,4)
        m.part='重瓣莲冠' if not steam else '蒸莲双色焰瓣'
        m.stem([BASE,p],[.55,.36]);m.crown(p,9 if not steam else 6,5.5 if not steam else 4.6,2.75,1.8 if not steam else 3.7,-.7,2,0,.16)
        m.crown(plus(p,(0,.35,0)),7 if not steam else 5,3.65,2.25,2.5 if not steam else 4.5,-.56,3,.45,-.13)
        if steam:
            m.crown(plus(p,(0,-.18,0)),7,3.8,1.85,1.65,.80,3,.73,.23)
            m.part='泉眼';m.spindle((8,1.0,8),1.35,4.2,0,[(0,.62),(.43,1),(.75,.7),(1,.61)],8)
        if m.age>=2:commission(m,(8,5.1 if steam else 3.55,8))
    elif s=='dawn_disk':
        basal(m,4,4.4,2.5,2.1);p=(8.7,10.6,8);m.part='向日盘';shoots(m,p,.48,(-.6,.45))
        # The disk and petals form a near-vertical sun visible from a player's eye height.
        start=len(m.faces);origin=(0,0,0)
        m.crown(origin,13,4.9,1.55,.35,.55,2,0,.10)
        m.crown((0,.13,0),9,3.7,1.48,.55,-.3,3,.21,-.15)
        m.spindle((0,-.3,0),1.80,1.30,5,[(0,.65),(.4,1),(.8,.77),(1,.30)],12)
        if m.age>=2:
            for j in range(13):
                a=j*2.4;r=.33+.075*j;bead(m,radial((0,.72,0),a,r),.13,4)
        # Lean forward with the petal fan facing south and slightly upward.
        for i in range(start,len(m.faces)):
            part,pts,uv,glow=m.faces[i]
            m.faces[i]=(part,[plus(p,(x,.32*y+.947*z,.947*y-.32*z)) for x,y,z in pts],uv,glow)
            # This basis preserves handedness when passing from y to z.
            pp=m.faces[i];m.faces[i]=(pp[0],pp[1][::-1],pp[2][::-1],pp[3])
    elif s=='shadow_cut':
        basal(m,4,5,1.8,2.7);p=(7.1,11.1,8.35);m.part='蝶形花冠';shoots(m,p,.36,(1.65,-.7))
        for sign in (-1,1):
            a=.1 if sign==1 else pi-.1
            m.blade(p,a,4.85,3.35,3.65,-.2,2,twist=sign*.30)
            m.blade(p,a,3.65,1.6,-2.3,.6,3,twist=-sign*.36)
        m.blade(p,1.65,2.4,2.25,-3.1,.30,3)
        m.spindle(p,.73,1.65,4,sides=6,glow=True)
    elif s=='star_rest':
        basal(m,6,5.6,2.15,2.3)
        p=(8,8.6,8);m.part='星座花托';shoots(m,p,.48)
        m.crown(p,5,5.4,1.55,3.4,-.75,2,.14,.04)
        m.crown(plus(p,(0,.5,0)),5,3.45,1.5,3.1,-.65,3,.14+pi/5,-.12)
        for i in range(3):
            a=.45+i*tau/3;tip=radial(p,a,1.45,4+.42*(i%2))
            m.stem([p,radial(p,a,1.2,1.6),tip],[.22,.13,.09],3,5);bead(m,tip,.39)
    elif s=='wish_puff':
        basal(m,5,4.45,1.12,2.1);p=(8.2,10.35,8.3);m.part='放射灯种';shoots(m,p,.33,(-.64,.29))
        n=17 if m.age>=2 else 9
        for i in range(n):
            h=(i+.5)/n*1.8-.72;a=i*2.399;r=(1-min(1,h*h))**.5
            tip=radial(p,a,4.25*r,4.05*h)
            m.stem([p,mix(p,tip,.64),tip],[.11,.09,.06],3,4)
            for j in range(3):m.blade(tip,a+j*tau/3,.70,.48,.55,.10,3,steps=2)
            if i%3==0:bead(m,tip,.20)
    elif s in ('frost_chime','dance_bell'):
        frost=s=='frost_chime';basal(m,5,4,1.3 if frost else 2.1,2.1)
        neck=(7.8,8.0,8);m.part='霜索垂铃' if frost else '回旋铃梗';shoots(m,neck,.37)
        for i in range(3 if frost else 5):
            a=.1+i*(tau/3 if frost else 2.399)
            tip=radial(neck,a,3.2 if frost else 2.3+i*.25,3.0+i*.74)
            m.stem([neck,radial(neck,a,2.0,3.3+i*.70),tip],[.26,.16,.1],0,5)
            base=plus(tip,(0,-3.0 if frost else -2.4,0))
            if frost:
                m.spindle(base,1.20,3.0,2,[(0,1),(.13,.78),(.69,.48),(1,.12)],6)
                for j in range(5):m.blade(base,a+j*tau/5,.56,.65,-1.0,.15,3,steps=2)
            else:
                m.spindle(base,1.12,2.4,2,[(0,1),(.18,.74),(.55,.73),(.85,.50),(1,.15)],8,pleat=.10)
                m.crown(base,4,.85,.57,-.46,.12,3,a,.35)
            bead(m,plus(base,(0,-.59,0)),.33,4)
    elif s in ('prism_crown','storm_crown'):
        storm=s=='storm_crown';basal(m,6,5.2,1.85,2.2);p=(8,7.5 if storm else 8.7,8)
        m.part='引雷叉冠' if storm else '虹色晶冠';shoots(m,p,.52,(.75,.55))
        for i in range(5 if storm else 7):
            a=i*tau/(5 if storm else 7)+.11;r=3.2 if storm else 2.9
            q=radial(p,a,r,1.5)
            m.stem([p,radial(p,a,r*.70,.06),q],[.45,.28,.23],0,6)
            if storm:
                elbow=radial(q,a+1.5,.9,2.0);point=radial(q,a,1.25,4.8+i%2)
                m.stem([q,elbow,radial(q,a-.7,.15,2.55),point],[.30,.25,.20,.035],3,5)
                m.wire([mix(q,elbow,.62),elbow,radial(q,a-.7,.15,2.55)],.085,4,True)
            else:
                m.spindle(q,.79,3.15+.8*(i%3),2 if i%3 else 3,[(0,.48),(.18,.92),(.69,1),(1,.015)],5,lean=(cos(a)*.85,sin(a)*.85))
                bead(m,radial(q,a,-.48,.48),.20)
        m.spindle(p,1.05 if storm else .95,3.4,4,[(0,.08),(.5,1),(1,.04)],6,glow=True)
    elif s=='shadow_hour_orchid':
        basal(m,4,5,1.70,1.85);p=(8,6.25,8);m.part='日影漏斗';shoots(m,p,.31)
        m.spindle(p,2.2,6.25,2,[(0,.92),(.12,1),(.4,.29),(.53,.20),(.86,.78),(1,.94)],10)
        m.crown(plus(p,(0,5.9,0)),6,2.25,1.85,1.13,-.46,3,.16,.24)
        for j in range(3):
            a=j*tau/3;m.stem([radial(p,a,1.92,.25),radial(p,a,2.6,2.05),radial(p,a,1.98,5.79)],[.10,.13,.08],3,5)
        bead(m,plus(p,(0,2.6,0)),.58,4)


def undergrowth(m,closed=False):
    s=m.species
    if s=='warning_moss':
        # A creeping holdfast underlies every leaf and spore cup; this whole mat rotates onto the wall.
        m.part='贴壁苔络'
        for branch in range(5 if m.age>0 else 3):
            a=.2+branch*2.399;q=radial(BASE,a,1.8,.15)
            pts=[BASE,q,radial(q,a+.57,2.4,.07),radial(q,a+.16,4.6,.20)]
            m.stem(pts,[.42,.32,.23,.10],0,6)
            for j in range(1,4):
                p=mix(pts[1],pts[3],j*.25)
                m.blade(p,a+(-1)**j*.99,2.5,1.75,.80,.47,1,.18)
                m.blade(p,a-(-1)**j*.9,1.75,1.12,.47,.24,1)
            if m.age>=2:
                rim=plus(pts[2],(0,.40,0))
                m.spindle(rim,.79,1.05,2,[(0,.21),(.75,1),(1,.84)],7)
                bead(m,plus(rim,(0,.94,0)),.43,4)
    elif s=='ore_sleep_moss':
        m.part='矿鳞苔床'
        for j in range(9 if m.age>=2 else 5):
            a=j*2.399;q=radial(BASE,a,(j**.5)*1.60,.02)
            m.spindle(q,2.05-.07*j,1.35+.15*(j%3),1,[(0,.87),(.55,1),(1,.36)],7,pleat=.1)
            if j%2==0:
                h=2.0+.42*(j%3);p=plus(q,(0,.83,0))
                m.spindle(p,.85,h,2 if j%4 else 3,[(0,.85),(.63,1),(1,.02)],5,lean=(.4*cos(a),.4*sin(a)))
                m.wire([plus(q,(0,1.28,0)),radial(q,a+.5,1.2,.98),radial(q,a+.3,1.71,.6)],.070,4,True)
    elif s=='hushcap_mushroom':
        m.part='缄声伞房'
        for i,(x,z,h,r) in enumerate([(7.1,8.1,8.2,4.35),(11.4,9.35,4.85,2.25)]):
            p=(x,h,z);m.stem([(x-.42,.085,z),(x+.38,h*.46,z-.3),p],[.87,.65,.44])
            rad=r*(.58 if closed else 1);cap_h=3.75 if closed else 2.15
            m.stem([p,plus(p,(0,cap_h*.88,0))],[.46,.30],3,6)
            collar=plus(p,(0,-.78,0))
            m.spindle(collar,.83,.71,3,[(0,1),(.26,.89),(1,.50)],9)
            m.crown((x,.16,z),5,1.38,.52,.50,.24,0,.32)
            if not closed:
                for j in range(11):
                    a=j*tau/11
                    m.stem([plus(p,(0,cap_h*.87,0)),radial(p,a,.65*rad,.42*cap_h),radial(p,a,.97*rad,-.06)],
                           [.105,.095,.056],3,4)
            for j in range(13):
                a=j*tau/13;b=(j+1)*tau/13
                def cap(t,angle):
                    return radial(p,angle,rad*t,cap_h*(1-t*t)-.17*cos(6*angle)*t)
                for lo,hi in [(0,.36),(.36,.73),(.73,1)]:
                    m.face([cap(lo,a),cap(lo,b),cap(hi,b),cap(hi,a)],2 if i==0 else 3,[(j/13,lo),((j+1)/13,lo),((j+1)/13,hi),(j/13,hi)],True)
                m.blade(plus(p,(0,-.1,0)),a,rad*.96,.32,-.15,.50,3,steps=3)
                if j%3==0:bead(m,cap(.73,a),.17)
    elif s=='salt_ink':
        m.part='钟罩墨伞'
        for i,(x,z,h,r) in enumerate([(7.5,8.6,9.8,3.5),(10.7,6.0,5.6,2.05)]):
            m.stem([(x,.10,z),(x+.50,h*.53,z-.50),(x,h,z)],[.49,.42,.33])
            bot=(x,h-2.25,z)
            m.spindle(bot,r,5.4 if not i else 3.6,2,[(0,.98),(.16,1),(.54,.85),(.87,.4),(1,.10)],13,pleat=.06)
            for j in range(9):
                a=tau*j/9;q=radial(bot,a,r*.98,.04);end=radial(q,a,.08,-(.50+.46*(j%3)))
                m.stem([q,end],[.21,.10],0,5)
                if j%2==0:bead(m,plus(end,(0,-.42,0)),.21,3,.49,False)
            if i==0:crescent(m,plus(bot,(0,.66,0)),r,3,5.0,.15,thick=.09)
    elif s=='dream_compost':
        m.part='发酵菌床'
        m.spindle(BASE,3.9,2,0,[(0,.9),(.45,1),(1,.40)],9,pleat=.08)
        for i in range(7 if m.age>=2 else 4):
            a=i*2.399;q=radial(BASE,a,.4+i*.24,.40+i*.43)
            m.stem([(8,.5,8),q],[.58,.38],0)
            for j in range(8):
                start=a-1.56+j*3.10/8;end=start+3.10/8
                def shelf(t,ang):return radial(q,ang,t*(3.1+.4*cos(ang*3)),.52+sin(pi*t)*.55-t*.33)
                for lo,hi,material in [(0,.65,2),(.65,.86,3),(.86,1,2)]:
                    m.face([shelf(lo,start),shelf(lo,end),shelf(hi,end),shelf(hi,start)],material,back=True)
            if i%2==0:bead(m,radial(q,a,2.25,.60),.28,4)
    elif s=='mana_root':
        m.part='蓄魔根室'
        m.spindle(BASE,2.85,5.3,5,[(0,.66),(.22,1),(.64,.88),(1,.30)],9,pleat=.16)
        for j in range(6):
            a=j*tau/6
            m.stem([radial(BASE,a,1.42,2.3),radial(BASE,a+.2,3.65,.90),radial(BASE,a-.11,5.4,.14)],[.48,.28,.06],0,5)
            m.wire([radial(BASE,a,1.30,4.5),radial(BASE,a,2.4,3.1),radial(BASE,a,2.68,2.1)],.075,4,True)
            m.blade((8,4.5,8),a+.18,4.25,2.3,4.9+.66*(j%2),1.15,1)
    elif s=='verdant_vein':
        m.part='脉晶树杈';shoots(m,(8.65,11,8.25),.70,(-.54,.33))
        for i in range(5):
            a=i*2.399;p=(8,3.3+i*1.44,8);q=radial(p,a,3.23,2.15)
            m.stem([p,radial(p,a+.15,1.7,.55),q],[.38,.27,.17])
            for side in [-1,1]:
                m.blade(q,a+.74*side,2.7,1.10,1.67,.60,1)
            m.spindle(q,.75,2.40,2,[(0,.30),(.4,1),(1,.02)],5,lean=(.5*cos(a),.5*sin(a)))
            m.wire([p,radial(p,a+.15,1.7,.62),q],.070,4,True)
    elif s in ('sky_vine','ladder_vine','star_dew','paper_tree'):
        paper=s=='paper_tree';sky=s=='sky_vine';star=s=='star_dew'
        m.part='纸皮树干' if paper else '缠绕活藤'
        n=1 if paper or star else 2
        for k in range(n):
            base_angle=k*pi+.4
            radius=1.48 if sky else 1.70
            loop=1.12 if sky else .8 if star else .36
            pts=[radial((8,.1+14*j/8,8),base_angle+j/8*tau*loop,
                        radius*(.65+.23*sin(pi*j/8)) if not paper else .60) for j in range(9)]
            m.stem(pts,[.60-j*.042 for j in range(9)],0,7)
            for j in range(2,9,2):
                a=base_angle+j*1.12;q=pts[j];tip=radial(q,a,2.2,1.25 if not star else -.75)
                m.stem([q,mix(q,tip,.45),tip],[.24,.17,.09],0,5)
                if paper:
                    q2=radial(tip,a-1.10,1.15,.51)
                    m.stem([tip,q2],[.17,.1],0,5)
                    m.blade(q2,a-.94,1.86,1.11,.43,.42,1,.13)
                    # Paired thin sheets hang from a crooked branch with a folded upper corner.
                    m.blade(tip,a-.66,1.7,2.72,-2.30,.68,3,.31)
                    m.blade(tip,a+.78,2.35,1.85,-1.55,.34,2,-.32)
                    m.blade(pts[j-1],a,1.10,1.4,-1.67,.52,2,.44)
                elif star:
                    m.blade(q,a+1.14,2.33,1.55,.78,.92,1,-.21)
                    m.blade(q,a-.76,3.25,1.70,1.14,.39)
                    m.blade(tip,a+.65,1.9,1.2,-.22,.35)
                    bead(m,plus(tip,(0,-1.03,0)),.58,3,1.22,False)
                    bead(m,plus(tip,(.11,-.91,.22)),.21,4,.40)
                else:
                    m.blade(tip,a+.4,2.6 if sky else 1.95,2.0 if sky else 1.1,1.05,.65)
                    if sky:
                        m.blade(tip,a-.7,2.4,1.60,.7,.4)
                        m.blade(q,a-1.25,3.35,1.10,1.75,.76,1,.27)
                    elif j in (4,6):
                        m.blade(q,a-1.05,2.30,1.40,1.2,.66,1)
                        m.wire([tip,radial(tip,a,1.15,.53),radial(tip,a+.48,1.58,.84),radial(tip,a+.66,1.20,1.11)],.075,3)
                    if k==0 and not sky:
                        end=radial((8,pts[j][1],8),base_angle+pi+j/8*tau*loop,radius*.8)
                        m.stem([pts[j],mix(pts[j],end,.5),end],[.19,.20,.19],3,5)
            if sky:
                q=pts[-1];m.crown(q,3,1.55,.95,.94,.30,3,.4)


def foliage(m):
    s=m.species
    if s=='stone_fern':
        m.part='石鳞羽片'
        for i in range(5):
            a=i*2.4;pts=[radial(BASE,a+.05*j,j*1.18,.15+2.17*j-.19*j*j) for j in range(6)]
            m.stem(pts,[.36-j*.05 for j in range(6)],0,5)
            for j,p in enumerate(pts[1:-1]):
                for side in [-1,1]:
                    ang=a+side*.95;l=2.65-.4*j
                    m.blade(p,ang,l,1.36,.64,.42,1 if i%2 else 3,.06,ridge=.28,steps=3)
            m.spindle(pts[-1],.38,1.25,2,[(0,.5),(.6,1),(1,.02)],5)
    elif s=='turnpage_fern':
        m.part='卷页叶轴'
        pts=[(8,.12,8),(7.35,3,8.3),(7.55,6,8.25),(8.2,9.4,8.2),(9.4,12.7,8.18),(9.95,13.85,8.15)]
        m.stem(pts,[.45,.36,.31,.25,.20,.14])
        for i,p in enumerate(pts[1:]):
            for side in [-1,1]:
                a=.17+i*.12+(pi if side<0 else 0)
                # A creased folio opens around the centre stem; older leaves spread wider.
                m.blade(p,a,4.9-i*.50,2.65-i*.22,1.32+i*.20,.95,2 if side==1 else 3,
                        twist=side*.24,ridge=.29,steps=4)
        tip=pts[-1];m.wire([tip,plus(tip,(.38,.29,0)),plus(tip,(.50,.14,0))],.10,4,True)
    elif s=='wind_sail':
        m.part='顺风膜翼'
        for i in range(3):
            a=.60+(i-1)*1.02
            p=radial(BASE,a,1,.35);bend=radial(p,a,1.51,4.20);tip=radial(p,a,5.45,11.0-i*.86)
            m.stem([p,bend,tip],[.33,.27,.10])
            shoulder=radial(bend,a+1.36,3.25,4.15)
            bottom=radial(bend,a+.30,1.1,.65)
            m.face([bottom,shoulder,tip,mix(tip,bend,.42)],2 if i==1 else 1,back=True)
            m.face([bottom,mix(tip,bend,.42),tip,bend],3,back=True)
            m.wire([bottom,shoulder,tip],.10,3)
            m.blade(p,a-1.25,3.5,1.10,2.65,.72)
    elif s=='thermal_foldleaf':
        m.part='冷热褶叶'
        for i in range(3):
            p=(8,1.3+i*1.34,8);m.stem([BASE,p],[.30,.25])
            for side in [-1,1]:
                a=.05+i*.66+(0 if side==1 else pi)
                q=radial(p,a,1.2,1.1);m.stem([p,q],[.28,.17])
                # Accordion is a solid fold across its width, with opposite thermal pigments.
                def at(t,k):
                    ang=a+.13*t;w=(.035+sin(pi*t))*2.25*k
                    return plus(radial(q,ang,4.1*t,4.8*t+1.6*sin(pi*t)),(-sin(ang)*w,(.36 if int((k+1)*2)%2 else -.32)*sin(pi*t),cos(ang)*w))
                for j in range(4):
                    for k in range(4):
                        u,v=j/4,(j+1)/4;l,r=-1+k*.5,-.5+k*.5
                        m.face([at(u,l),at(u,r),at(v,r),at(v,l)],2 if side==1 else 3,[((l+1)/2,u),((r+1)/2,u),((r+1)/2,v),((l+1)/2,v)],True)
    else: # sleep_clock
        m.part='钟眠卷叶'
        for i in range(4):
            a=i*tau/4+.25;r=2.65-i*.24;q=radial(BASE,a,1.8,1.3)
            sep=radial(q,a,2.7,.2);m.stem([BASE,q,sep],[.31,.26,.21])
            pts=[]
            for j in range(14):
                t=j/13;ang=-pi*.5+t*tau*1.12;rr=r*(1-.70*t)
                pts.append(radial(sep,a,cos(ang)*rr,3.0+sin(ang)*rr-.43))
            m.stem([sep]+pts,[.28]+[.24-.14*j/13 for j in range(14)],1,6)
            bead(m,pts[-1],.28,4)
            m.blade(BASE,a+.75,3.22,1.4,2.1,.45,3)
        m.spindle((8,.24,8),1.38,1.24,2,[(0,.70),(.35,1),(1,.28)],10)

def grasses(m):
    s=m.species
    if s=='road_bell_crop':
        m.part='麦芒与铃颖'
        for i in range(4):
            a=i*2.4;foot=radial(BASE,a,1.45);neck=radial(foot,a,1.45,7.1+.70*i);tip=radial(neck,a,.8,3.75)
            m.stem([foot,radial(foot,a,.3,3.75),neck,tip],[.23,.20,.15,.075])
            m.blade(radial(foot,a,.15,2.05),a-.43,4.5,1.03,2.9,.90)
            for j in range(6 if m.age>0 else 3):
                p=mix(neck,tip,j/6);b=a+(.95 if j%2 else -.95)
                m.blade(p,b,1.20,.69,1.39,.14,2 if i%2 else 3,steps=3)
                m.wire([radial(p,b,.68,.8),radial(p,b,1.55,2.12)],.052,3)
            if m.age>=2:bead(m,plus(tip,(0,-.85,0)),.22,4)
    elif s=='tide_reed_crop':
        m.part='潮音笛管'
        for i in range(5):
            a=i*2.4;foot=radial(BASE,a,2.03);h=8.2+i*1.22
            tip=radial(foot,a,1.15,h);m.stem([foot,mix(foot,tip,.6),tip],[.43,.40,.34],1,7)
            for j in (1,2,3):
                p=mix(foot,tip,.17*j+.18);m.spindle(p,.47,.22,3,[(0,1),(1,1)],7)
                if j==1:m.blade(p,a-.4,3.5,1.05,3.4,.60)
            m.spindle(tip,.82,1.65,2,[(0,.48),(.64,1),(1,.85)],8)
            bead(m,plus(tip,(0,1.53,0)),.37,4,.19)
    elif s=='daynight_sandgrain':
        m.part='昼夜双穗'
        for i in range(2):
            a=.16+pi*i;foot=radial(BASE,a,1.1);neck=radial(foot,a,1.30,7.6);tip=radial(neck,a,.40,5.5)
            m.stem([foot,neck,tip],[.34,.25,.13])
            for j in range(8):
                b=j*2.399+i;p=mix(neck,tip,j/8)
                q=radial(p,b,.84,.15);m.stem([p,q],[.15,.10],0,4)
                m.spindle(q,.56,1.38,2 if not i else 3,[(0,.15),(.52,1),(1,.09)],6)
            m.blade(mix(foot,neck,.47),a+.80,3.60,1.2,2.65,1,1)
        crescent(m,(8,8.35,8),2.02,3,4.85,.52,True,.14)
    else: # hanging_dewgrass
        m.part='垂珠丝弓'
        for i in range(5):
            a=i*2.399;root=radial(BASE,a,.76)
            pts=[root,radial(root,a,1.10,5.1),radial(root,a,2.84,9.1+i%2),radial(root,a,4.14,9.35),radial(root,a,5.6,6.2-.5*(i%2))]
            m.stem(pts,[.24,.22,.18,.12,.06],1,5)
            m.blade(root,a+.51,3.3,.70,3.5,1.55)
            for j in range(2):
                q=mix(pts[2],pts[-1],.44+j*.5);end=plus(q,(0,-(1.4+.71*j),0))
                m.wire([q,end],.061,3)
                m.spindle(plus(end,(0,-.8,0)),.35,.9,4,[(0,.32),(.32,1),(1,.09)],6,glow=True)


def fruits(m,wet=False):
    s=m.species
    if s=='night_gourd_crop':
        m.part='夜瓠卷须'
        path=[BASE,(5.2,2.4,8.6),(4.4,5.8,8.1),(5.65,9,8),(8.5,10.4,7.8),(11,9.2,8.3)]
        m.stem(path,[.43,.39,.34,.30,.22,.15])
        for i,p in enumerate(path[1:]):
            m.blade(p,1.7+i*2.4,3.65,2.85,1.27,.68,1,.20)
        m.part='月瓠'
        for i,p in enumerate((path[-1],path[3])):
            k=.84 if i else 1.0
            if m.age>=2:
                q=plus(p,(.25,-4.5*k,0))
                m.spindle(q,1.63*k,4.55*k,5,[(0,.26),(.13,.78),(.35,1),(.55,.46),(.75,.62),(1,.15)],10,pleat=.065)
                crescent(m,plus(q,(0,1.26*k,0)),1.58*k,3,3.65,.20,False,.09)
            else:bead(m,plus(p,(0,-.70,0)),.38,3,.82,False)
        tip=path[-1];m.wire([tip,plus(tip,(.74,.60,.22)),plus(tip,(1.43,.86,.18)),plus(tip,(1.59,.36,.18)),plus(tip,(1.09,.27,.15))],.09,3)
    elif s=='rainletter_bush':
        m.part='雨纹枝笺'
        for i in range(3):
            a=i*2.399;tip=radial(BASE,a,3.70,7.2+i*1.51);shoots(m,tip,.40,(.45,.14))
            for j in (1,2,3):
                p=mix(BASE,tip,.24+j*.2);b=a+(-1)**j*1.2
                m.blade(p,b,2.94,2.27,.83,.72,1,.24,ridge=.26)
                if j==2 and m.age>=2:
                    q=radial(p,b,1.6,-.28);m.stem([p,q],[.18,.08])
                    for k in range(3):bead(m,radial(q,b+k*2.4,.47,-.65-.24*k),.45,5,.91,False)
                if wet:
                    leaf_tip=radial(p,b+.24,2.94,.83)
                    m.spindle(plus(leaf_tip,(0,-.94,0)),.36,.95,4,[(0,.29),(.30,1),(1,.09)],6,glow=True)
    elif s=='hearth_pepper':
        m.part='炉椒与炭萼'
        for i in range(3):
            a=i*2.399;tip=radial(BASE,a,3.15,8.5+i*1.35);shoots(m,tip,.38,(.60,.26))
            for j in (1,2):
                p=mix(BASE,tip,.26+j*.22);m.blade(p,a+(-1)**j,3.2,1.43,1.15,.8,1)
            q=radial(tip,a,1.17,-.85);m.stem([tip,radial(tip,a,1.06,.2),q],[.18,.13,.12])
            m.crown(q,5,.82,.58,-.55,.22,3,a)
            if m.age>=2:
                ps=[q,radial(q,a,.10,-1),radial(q,a,.38,-2.26),radial(q,a+.30,1.15,-3.20),radial(q,a+.48,1.65,-3.50)]
                m.stem(ps,[.38,1.06,.79,.44,.025],5,8)
                m.wire([plus(ps[1],(.5,.1,.61)),plus(ps[2],(.45,0,.58)),plus(ps[3],(.17,0,.28))],.076,4,True)
    elif s=='mist_cotton':
        m.part='雾絮花球'
        for i in range(4):
            a=.5+i*2.399;p=radial(BASE,a,1.20,3.6+i*.50);q=radial(p,a,2.55,4.0+i*.78)
            m.stem([BASE,p,q],[.38,.28,.16]);m.blade(p,a-.80,2.66,1.80,1.83,.42,1)
            m.crown(q,5,1.72,1.10,.90,.34,1,a)
            if m.age>=2:
                for k in range(4):
                    b=a+k*2.399;v=radial(q,b,.63,.40+(k%2)*.40)
                    m.spindle(v,1.12,1.6,3 if k%2 else 2,sides=8)
                m.blade(plus(q,(0,1.45,0)),a-.60,1.6,.67,2.22,.43,3,twist=.74)
    elif s=='echo_bean':
        m.part='回声豆荚'
        stalk=[BASE,(7.3,3.55,8),(8.65,7.25,8.2),(7.55,10.7,8.55),(8.80,13.1,8.1)]
        m.stem(stalk,[.39,.32,.26,.18,.11])
        for i,p in enumerate(stalk[1:]):
            a=i*2.4-.44;q=radial(p,a,2.50,.55)
            m.stem([p,q],[.23,.14],0,5);m.blade(q,a-.70,2.4,1.65,1.76,.23)
            if m.age<2:continue
            pts=[q,radial(q,a,.18,-.64),radial(q,a+.20,.55,-1.33),radial(q,a+.26,.79,-2),radial(q,a+.68,1.2,-2.61),radial(q,a+1.00,1.3,-2.91)]
            m.stem(pts,[.18,.72,.48,.75,.48,.09],5,7)
            for j in (1,3):bead(m,plus(pts[j],(.18,-.07,.56)),.19,4)
    elif s=='flight_pod':
        m.part='羽翅种荚'
        p=(8,8.6,8);shoots(m,p,.35,(-.55,.33));basal(m,4,3.7,1.78,3.1)
        for i in range(3):
            a=.31+i*tau/3;tip=radial(p,a,2.6,2.25+i*.52);m.stem([p,tip],[.24,.16],0,5)
            if m.age<2:
                m.blade(tip,a,1.50,.72,1.36,.35,3);continue
            bead(m,plus(tip,(0,-1.26,0)),.64,5,1.27,False)
            for side in [-1,1]:
                m.blade(tip,a+side*.92,2.51,1.28,2.14,.71,3 if side==1 else 2,twist=side*.45)
    else: # honey_cluster
        m.part='蜂巢花序'
        for i in range(3):
            a=i*2.399;p=radial(BASE,a,1.20,3.2+i);q=radial(p,a,1.75,3.0+.82*i)
            m.stem([BASE,p,q],[.38,.24,.20]);m.blade(p,a+.80,3.3,1.52,1.9,.94)
            for j in range(4 if m.age>=2 else 2):
                b=j*tau/3;v=radial(q,b,1.12 if j<3 else 0,.54 if j==3 else 0)
                m.spindle(v,.75,1.55,2,[(0,.55),(.7,1),(1,.86)],6)
                bead(m,plus(v,(0,1.34,0)),.42,4,.60)
            m.crown(q,4,1.85,1.22,.45,.4,1,a)

FLOWERS={'homeward_flower','mirror_lotus','steam_lotus','dawn_disk','shadow_cut','star_rest','wish_puff','frost_chime','dance_bell','prism_crown','storm_crown','shadow_hour_orchid'}
UNDERGROWTH={'warning_moss','ore_sleep_moss','hushcap_mushroom','salt_ink','dream_compost','mana_root','verdant_vein','sky_vine','ladder_vine','star_dew','paper_tree'}
FOLIAGE={'stone_fern','turnpage_fern','wind_sail','sleep_clock','thermal_foldleaf'}
GRASSES={'road_bell_crop','tide_reed_crop','daynight_sandgrain','hanging_dewgrass'}
FRUITS={'night_gourd_crop','rainletter_bush','hearth_pepper','mist_cotton','echo_bean','flight_pod','honey_cluster'}
assert FLOWERS|UNDERGROWTH|FOLIAGE|GRASSES|FRUITS==set(SPECIES)

def make(species,age,wet=False,closed=False):
    m=Mesh(species,age)
    if species in FLOWERS:flowers(m)
    elif species in UNDERGROWTH:undergrowth(m,closed)
    elif species in FOLIAGE:foliage(m)
    elif species in GRASSES:grasses(m)
    else:fruits(m,wet)
    # Physical stage progression: small, furled organs gradually fan outward and gain height.
    width=(.29,.57,.81,1.0)[age];height=(.24,.52,.78,1.0)[age]
    out=[]
    for organ,pts,uv,lit in m.faces:
        points=[]
        for x,y,z in pts:
            x,z=8+(x-8)*width,8+(z-8)*width
            y=max(.04,.08+(y-.08)*height)
            if species=='warning_moss':y,z=z,16-y
            points.append((qround(x),qround(y),qround(z)))
        out.append((organ,points,uv,lit))
    m.faces=out
    # Roots for the two multi-block climbers reach the next real stem; no organ clips through the cell above.
    max_y=max(p[1] for _,pts,_,_ in out for p in pts)
    if max_y>15.98:
        m.faces=[(part,[(x,qround(y*15.98/max_y),z) for x,y,z in pts],uv,glow) for part,pts,uv,glow in out]
    join_root(m)
    return m

def main(projects=None):
    lang=json.loads((ASSETS/'lang/zh_cn.json').read_text('utf-8-sig'))
    report=[]
    for species in SPECIES:
        tx=atlas(species)
        # Respect existing state aliases (including shared wild forms) and rendering flags.
        states=json.loads((ASSETS/'blockstates'/f'{species}.json').read_text('utf-8-sig'))['variants']
        refs={name for val in states.values() for name in model_refs(val)}
        geometry=set()
        for name in refs:
            wrapper=json.loads((ASSETS/'models'/(name.split(':')[1]+'.json')).read_text('utf-8-sig'))
            geometry.add(wrapper['geometry'].split(':')[1])
        for rel in sorted(geometry):
            path=ASSETS/rel
            match=re.fullmatch(re.escape(species)+r'_([0-3])(_wet|_closed)?',path.stem)
            if not match:raise ValueError(path)
            age=int(match[1]);state=match[2]
            m=make(species,age,wet=state=='_wet',closed=state=='_closed')
            assert m.faces,(species,age,state)
            path.write_text(json.dumps(m.export_faces(),separators=(',',':'))+'\n',encoding='utf-8')
            if age==3 and state is None:
                if projects:write_bbmodel(m,lang['block.tnc.'+species],tx,projects/(species+'.bbmodel'))
                mature=m
        report.append({'species':species,'family':FAMILIES.get(species,species),'models':len(refs),
                       'stage_meshes':len(geometry),'mature_surfaces':len(mature.faces),
                       'emissive_surfaces':sum(lit for _,_,_,lit in mature.faces)})
    make_segments(projects)
    dump(ROOT/'model-source/botanical/sculpture-manifest.json',report)
    print(f'{len(report)} species, {sum(x["stage_meshes"] for x in report)} stage meshes, {sum(x["models"] for x in report)} model bindings.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--projects',type=Path)
    main(parser.parse_args().projects)

