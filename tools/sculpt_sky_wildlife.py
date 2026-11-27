"""Build the atmospheric wildlife mesh, bind poses, texture atlas and editable Blockbench rigs."""
import argparse
import json
import math
from pathlib import Path
import numpy as np
from sculpt_pasture_fauna import Surface, PALETTES, GLOW, shelf, paint, packed_surface, export_bb, uid

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
SW,ST,EA,BA,CR='reedwake_swallow','leafscript_starling','sunwheel_eagle','gloamveil_bat','windchime_crane'
# Length, chest radius, head radius, inner span, outer span, tail.
SHAPES={SW:(6.1,1.65,1.55,4.1,6.4,6),ST:(7.4,2.1,1.8,4.2,5.9,4),
        EA:(15,3.45,3.2,10,14,10),BA:(7.4,2.2,1.85,5.8,9.8,4),CR:(14,2.55,1.75,9.1,12,7.4)}
COLORS={ # midtone, shadow, highlight, luminous sparkle
 'river_feather':('136977','163449','54b6bc','b6fff4'),'river_silk':('e3c79b','946653','fff6ce','fff5c0'),
 'ink_feather':('294d5e','14263b','53869c','9bdef6'),'leaf_feather':('437958','172e38','a1bd69','e6e5a1'),
 'prism_feather':('41556d','252737','7798b0','d3e4df'),'gilt_feather':('ab7a34','513d2c','e8c26b','ffe8a2'),
 'coal_feather':('584537','282830','908071','d7be8b'),'cinnamon_feather':('a5693f','54403b','db9e5c','ffe0a5'),
 'sable_fur':('56475b','292840','a28aa6','eedbf4'),'veil_sail':('52476f','25263f','9182b2','c7b9e0'),
 'dusk_bone':('a59aad','4b405e','d9c9d4','f5d5ef'),'cloud_feather':('c7cdd0','647587','f7f2e2','fff6d3'),
 'slate_feather':('526573','303a4b','98a9af','cfdbdb'),'carmine':('a34c46','57343c','ee8874','ffe6bf'),
 'bill':('867658','3c3940','d5b878','ffe2ad'),'glass_eye':('1f2732','080f1b','606875','abbfcc'),
 'water_glint':('6dd8d6','227d91','c5fff1','f4fffb'),'script_glint':('abcf79','488776','e5ff9d','f4ffd3'),
 'sun_glint':('fad379','b7792c','fff0b9','fff8d7'),'moon_glint':('cdb0e5','766fba','f3e3ff','fff6ff'),
 'wind_glint':('bde5dd','719ea7','edfff4','fffffb')}
PALETTES.update(COLORS)
GLOW.update(['water_glint','script_glint','sun_glint','moon_glint','wind_glint'])
COATS={SW:('river_feather','ink_feather','river_silk','water_glint'),ST:('leaf_feather','prism_feather','gilt_feather','script_glint'),
       EA:('coal_feather','cinnamon_feather','gilt_feather','sun_glint'),BA:('sable_fur','veil_sail','dusk_bone','moon_glint'),
       CR:('cloud_feather','slate_feather','carmine','wind_glint')}

def rod(g,mat,start,end,r1,r2,sides=5):
    a,b=np.array(start,dtype=float),np.array(end,dtype=float)
    d=b-a;d/=np.linalg.norm(d)
    n=np.cross(d,[0,1,0] if abs(d[1])<.8 else [1,0,0]);n/=np.linalg.norm(n)
    q=np.cross(d,n);ang=np.arange(sides+1)*np.pi*2/sides
    rings=[[p+r*(np.cos(t)*n+np.sin(t)*q) for t in ang] for p,r in [(a,r1),(b,r2)]]
    for i in range(sides):
        g.face(mat,[rings[0][i],rings[0][i+1],rings[1][i+1],rings[1][i]],[(0,0),(1,0),(1,1),(0,1)])

def membrane(g,mat,v):
    uv=[(0,0),(.48,.13),(1,1),(.1,.8)][:len(v)]
    g.face(mat,v,uv);g.face(mat,v[::-1],uv[::-1])

def anatomy(sid):
    length,chest,head,inner,outer,tail=SHAPES[sid]
    coat,dark,accent,light=COATS[sid]
    parts=[];skin={}
    def joint(name,parent,xyz):
        parts.append(dict(name=name,parent=parent,pivot=list(xyz),rotation=[0,0,0]))
        g=skin[name]=Surface()
        return g
    core=joint('body','root',(0,19,0))
    core.loft(coat,'z',[(-length*.5,0,-.25,chest*.45,chest*.65),(-length*.32,0,.05,chest*.93,chest*1.02),
                          (-length*.035,0,.08,chest*1.05,chest),(length*.31,0,-.06,chest*.83,chest*.74),
                          (length*.53,0,.07,chest*.31,chest*.32)],10)
    core.blade(accent if sid in (SW,EA) else coat,(0,chest*.78,-length*.35),(0,chest*.55,length*.4),chest*.81,(0,1,0),chest*.24)
    neck=joint('neck','body',(0,-chest*.19,-length*.38))
    necklen=length*(.47 if sid==CR else .20)
    neck.loft(coat,'z',[(-necklen,0,-chest*.43,head*.64,head*.63),(-necklen*.45,0,-chest*.22,head*.81,head*.68),
                         (.6,0,.02,chest*.72,chest*.73)],8)
    skull=joint('head','neck',(0,-chest*.46,-necklen))
    skull.loft(coat,'z',[(-head,0,.14,head*.47,head*.49),(-head*.50,0,-.02,head*.93,head*.86),
                             (head*.23,0,-.06,head,head),(head*.83,0,0,head*.53,head*.73)],10)
    if sid==CR:
        skull.blade(accent,(0,-head*.83,-head*.48),(0,-head*.92,head*.48),head*.54,ridge=.17)
    bill=(head*(2.6 if sid==CR else 1.02 if sid==EA else .9 if sid==ST else .57))
    if sid==BA:
        skull.rounded('dusk_bone',(-head*.28,.05,-head*1.12),(head*.56,.47,.53),'z','soft')
    else:
        skull.loft('bill','z',[(-head*.78-bill,0,head*(.38 if sid==EA else .12),.045,.065),
            (-head*.78-bill*.73,0,head*(-.08 if sid==EA else .08),head*.18,head*(.26 if sid==EA else .09)),
            (-head*.83,0,head*.10,head*(.5 if sid==EA else .34),head*(.40 if sid==EA else .21))],8)
    for side,s in [('l',-1),('r',1)]:
        sx=s*head*.9
        skull.rounded('glass_eye',(sx-head*.13,-head*.32,-head*.63),(head*.26,head*.43,head*.44),'z','soft')
        skull.blade(light,(s*head*1.003,-head*.18,-head*.51),(s*head*1.035,-head*.23,-head*.34),head*.05,(s,0,0),.045)
        if sid==BA:
            skull.blade(coat,(s*head*.58,-head*.58,.1),(s*head*1.28,-head*2.32,.61),head*.62,(0,0,-1),.21)
            skull.blade(accent,(s*head*.63,-head*.87,-.08),(s*head*1.16,-head*2.06,.36),head*.36,(0,0,-1),.13)
        else:
            skull.blade(accent if sid!=CR else dark,(s*head*.66,-head*.54,-head*.72),
                       (s*head*.80,-head*.40,head*.72),head*.22,(s,0,0),.13)
            if sid in (EA,ST):
                for i in range(3):
                    skull.blade(accent,(s*head*(.47+i*.16),-head*(.76-i*.15),head*.18),
                           (s*head*(.60+i*.17),-head*(.53-i*.33),head*(1.12-i*.1)),head*.22,(s,-.3,0),.16)
        wing=joint('wing_'+side,'body',(s*chest*.78,-chest*.23,-length*.22))
        wrist=(s*inner,-inner*.11,-inner*.22)
        hand=joint('outer_'+side,'wing_'+side,wrist)
        if sid!=BA:
            # Hackle feathers follow the chest, and a continuous row of overlapping
            # lesser coverts carries the shoulder into the outer handwing.
            for j in range(4 if sid==EA else 3):
                u=j/4
                core.blade(accent if sid in (EA,ST) else coat,(s*chest*(.37+.15*u),-chest*(.86-.28*u),-length*(.32-.1*u)),
                    (s*chest*(.68+.30*u),-chest*(.63-.33*u),length*(.0+.12*u)),chest*.18,(s*.48,-1,0),.13)
            for j in range(5):
                u=j/5
                wing.blade(accent if sid==EA and j==1 else coat,
                    (s*inner*(.03+.96*u),-chest*(.47-.18*u),-inner*(.125+.18*u)),
                    (s*inner*(.22+.93*u),-chest*(.50-.22*u),length*(.38-.17*u)),
                    inner*(.18 if sid==SW else .195),ridge=.16)
            hand.blade(coat,(-s*.40,-.12,-.25),(s*outer*.81,.01,outer*(.29 if sid==SW else .18)),
                       outer*.149,ridge=.22)
        if sid==BA:
            elbow=(s*inner*.40,.3,inner*.23)
            rod(wing,coat,(0,0,0),elbow,.53,.38);rod(wing,accent,elbow,wrist,.38,.25)
            membrane(wing,dark,[(0,0,0),wrist,(s*inner,.52,inner*.42),(0,.57,inner*.85)])
            tips=[(s*outer,-.23,outer*.26),(s*outer*.77,.67,outer*.67),(s*outer*.47,1.13,outer*.87),(0,.8,inner*.65)]
            for i,(p,q) in enumerate(zip(tips,tips[1:])):
                d=(np.array(p)*.45+np.array(q)*.55);d[2]-=outer*(.13 if i==0 else .085)
                ridge=(np.array(p)+np.array(q))*.19;ridge[1]+=.60
                # Triangle panels meet at one convex bulge; a concave quad here
                # would drop a fan sector when triangulated by a shader/mesh editor.
                for edgeA,edgeB in [((0,0,0),p),(p,d),(d,q),(q,(0,0,0))]:
                    membrane(hand,dark,[edgeA,edgeB,ridge])
                rod(hand,accent,(0,0,0),p,.20 if i else .25,.05)
                hand.blade(light,np.array(p)*.54,np.array(p)*.77,.09,ridge=.045)
            rod(hand,accent,(0,0,0),(s*.38,-.62,-.95),.23,.04)
        else:
            leading=length*(.46 if sid in (EA,CR) else .40)
            wing.loft(coat,'x',[(0,0,leading*.36,chest*.54,leading*.70),
               (s*inner*.45,-inner*.085,leading*.28,chest*.41,leading*.80),
               (s*inner,-inner*.11,-inner*.22+leading*.21,chest*.23,leading*.55)],8)
            for i in range(5):
                t=i/5
                wing.blade(dark if i in (0,3) else coat,(s*inner*t,-.15,-inner*.20*t),
                    (s*inner*(t+.20),.18,leading*(1.01-.18*t)),leading*.24,ridge=.13)
            fingers=7 if sid in (EA,CR) else 5
            for i in range(fingers):
                t=i/(fingers-1)
                span=(1-.44*t) if sid==EA else (1-.7*t)
                sweep=(.44+.50*t) if sid==SW else (.05+.88*t) if sid==EA else (.16+.70*t)
                tip=(s*outer*span,outer*(.008+.040*t),outer*sweep)
                hand.blade(dark if i%3!=1 else coat,(s*outer*(.02+.09*(1-t)),.08*t,leading*(t*.77-.15)),tip,
                           outer*(.11 if sid==EA else .12),ridge=outer*.024)
                if i in (1,3,5):
                    hand.blade(accent if sid!=CR else 'cloud_feather',(tip[0]*.43,-.15,tip[2]*.49),
                              (tip[0]*.73,-.10,tip[2]*.72),outer*.050,ridge=.085)
            wing.blade(light,(s*inner*.17,-chest*.41,leading*.035),(s*inner*.74,-chest*.34,-inner*.09),inner*.040,ridge=.08)
        rectrix=joint('tail_'+side,'body',(s*chest*.34,-.04,length*.40))
        for i in range(2 if sid==BA else 3):
            t=i/2; end=(s*(.12+t*(tail*(.42 if sid==SW else .33))),tail*.035*t,
                         tail*((.38+.77*t) if sid==SW else (1-.21*t)))
            if sid==BA:
                if i==0:
                    membrane(rectrix,dark,[(0,0,0),(s*1.3,.22,-.12),(s*2,.36,2.4),(0,.48,3.7)])
            else:
                rectrix.blade(dark if i!=1 else coat,(s*i*.11,-.04*i,0),end,
                             tail*(.10 if sid==SW else .17),ridge=.15)
        leg=joint('leg_'+side,'body',(s*chest*.50,chest*.56,length*.20))
        z=length*(.77 if sid==CR else .24); knee=(s*.14,chest*(.91 if sid==CR else .36),z*.32)
        foot=(s*.12,chest*(1.16 if sid==CR else .52),z)
        rod(leg,accent if sid==CR else 'bill',(0,0,0),knee,chest*.16,chest*.11)
        rod(leg,'bill',knee,foot,chest*.11,chest*.08)
        for j in (-1,0,1):
            rod(leg,'bill',foot,(foot[0]+j*chest*.19,foot[1]+chest*.1,foot[2]+chest*(.66-.15*abs(j))),chest*.07,.025)
    return parts,skin

FLIGHT={SW:(.76,.68,.02,.49),ST:(.69,.76,.07,.56),EA:(.208,.76,.12,.26),BA:(.62,.82,-.04,.72),CR:(.24,.73,.085,.35)}

def sample_flight(sid,phase,glide=False):
    """An editable sample of each runtime wingbeat at full stroke or gliding amplitude."""
    rate,amp,dihedral,lag=FLIGHT[sid]
    if glide: amp*=.14 if sid not in (BA,ST) else .45
    wave=math.sin(phase)
    out={'body':{'position':(0,-math.cos(phase)*amp*.32,0)}}
    neck=.09*math.sin(phase-.8)*amp
    def rot(name,x,y,z):
        out[name]={'rotation':tuple(math.degrees(q) for q in (-x,y,-z))}
    rot('neck',neck,0,0);rot('head',-neck*.67,0,0)
    for side,s in [('l',1),('r',-1)]:
        rot('wing_'+side,0,s*amp*.16*math.cos(phase),s*(dihedral+amp*wave))
        rot('outer_'+side,0,s*amp*.24*(1+math.cos(phase-.22)),s*(.06+lag*amp*math.sin(phase-.90)))
        rot('tail_'+side,amp*.067*math.sin(phase-.8),0,0)
        rot('leg_'+side,.06*math.sin(phase-1.12)*amp,0,0)
    return out

def clips(sid):
    result=[]
    for clip,glide in [('powered_flight',False),('glide',True)]:
        period=math.tau/(FLIGHT[sid][0]*20)
        animation=dict(name=clip,loop='loop',override=False,length=round(period,4),snapping=60,selected=False,
                       anim_time_update='',blend_weight='',start_delay='',loop_delay='',animators={})
        for i in range(13):
            for name,channels in sample_flight(sid,i/12*math.tau,glide).items():
                bone=uid(sid,'joint.'+name)
                track=animation['animators'].setdefault(bone,dict(name=name,type='bone',keyframes=[]))
                for channel,vals in channels.items():
                    track['keyframes'].append(dict(channel=channel,time=round(period*i/12,4),interpolation='linear',
                          data_points=[dict(zip('xyz',[round(q,4) for q in vals]))],color=-1,
                          uuid=uid(sid,f'{clip}.{name}.{channel}.{i}')))
        result.append(animation)
    return result

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf8')

def build(export):
    for sid in SHAPES:
        parts,skin=anatomy(sid); tiles=shelf(skin); color,glow=paint(tiles)
        data={bone:packed_surface(g,tiles) for bone,g in skin.items()}
        write(ASSETS/'pasture_meshes/skywild'/f'{sid}.json',data)
        write(ASSETS/'skywild/binds'/f'{sid}.json',parts)
        tex=ASSETS/'textures/entity/skywild';tex.mkdir(parents=True,exist_ok=True)
        color.save(tex/f'{sid}.png');glow.save(tex/f'{sid}_glow.png')
        if export:
            bb=export_bb(sid,parts,data,color);bb['animations']=clips(sid)
            write(export/f'{sid}.bbmodel',bb)
        print(sid,sum(len(v['faces']) for v in data.values()))
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--export',type=Path)
    build(parser.parse_args().export)

