"""Small connected fittings and restrained two-sided engraving for existing weapon silhouettes."""
import math


def refine(w,parts):
    out=list(parts)
    def add(name,b,role='guard'):
        b=tuple(round(v,4) for v in b)
        if all(round(b[i],2)<round(b[i+3],2) for i in range(3)):
            out.append((f'craft_{name}_{len(out)}',b,role,'block'))
    def pin(x,z,y0,y1):
        for y in (y0-.12,y1):
            add('pin_seat',(x-.18,y,z-.18,x+.18,y+.1,z+.18))
            add('pin_head',(x-.08,y-.03,z-.08,x+.08,y+.13,z+.08),'edge')
    id=w['id'];profile=w.get('profile')
    if profile:
        # Quiet paired lines follow the real authored blade, leaving open metal between motifs.
        for i in range(math.ceil((profile[-1][0]-profile[0][0])/.5)):
            z=profile[0][0]+i*.5
            if z>profile[-1][0]-1:continue
            for a,b in zip(profile,profile[1:]):
                if a[0]<=z<=b[0]:
                    t=(z-a[0])/(b[0]-a[0]);left=a[1]+(b[1]-a[1])*t;right=a[2]+(b[2]-a[2])*t;break
            else:continue
            if right-left<1.5:continue
            for x in (left+.38,right-.52):
                # The trim seats in the existing bevel; thickness is the actual authored blade thickness.
                for y in (-.035,w.get('thick',1.2)-.06):
                    add('blade_inlay',(x,y,z,x+.12,y+.095,z+.30),'blade' if i%6 else 'guard')
    # Keep new fittings at the existing guard/socket, never on an arbitrary floating plane.
    guards=[p for p in parts if any(n in p[0] for n in ('sword_guard','jade_guard','disc_guard','blade_throat','socket_plate','guard_'))]
    if not guards:guards=[p for p in parts if p[2]=='guard' and 3<p[1][2]<6]
    if guards:
        b=guards[0][1];x=(b[0]+b[3])/2;z=(b[2]+b[5])/2
        for side in (-1,1):pin(x+side*min(.8,(b[3]-b[0])*.28),z,b[1],b[4])
    if id=='qimei_staff':
        for z in (-1.5,0,1.5,5.5,7,8.5):
            for y in (-.10,1.24):
                for j in range(5):
                    x=6.38+j*.248
                    add('hand_wrap',(x,y,z+j*.07,x+.248,y+.10,z+j*.07+.20),'grip')
    if id in ('yue_bronze_jian','han_iron_jian'):
        # Double-edged swords have matching bright bevels, unlike single-edged dao.
        out=[(name,b,'edge' if name.startswith('forged_blade') and profile and
                  abs(b[0]-min(p[1] for p in profile))<.26 else role,mode,*extra)
             for name,b,role,mode,*extra in out]
    if id in ('crescent_guandao','monk_spade'):
        for sign in (-1,1):
            y=-.18 if sign<0 else 1.3
            for i in range(5):
                z=12+i*.22;half=.8-abs(i-2)*.12
                add('cast_cheek',(7-half,y,z,7+half,y+.18,z+.22))
            pin(7,12.5,y,y+.18)
    if id=='fangtian_ji':
        for z in (13.1,16.35):
            for x in (4.4,7,9.6):pin(x,z,.2,.85)
    if id=='red_tassel_spear':
        # Preserve the approved tassel and silhouette; add only a stepped knot ferrule.
        for i in range(3):
            d=.08*i
            add('tassel_ferrule',(6.25+d,-.05,11.92+i*.13,7.75-d,1.1,12.05+i*.13))
    if id=='iron_jian_mace':
        for z in (4.35,15.35):
            for side in (-1,1):pin(7+side*.48,z,0,1.2)
    if id=='ring_pommel_dao':
        for t in range(16):
            a=t*math.pi/8;x=7+math.cos(a)*1.0;z=-1.25+math.sin(a)*1.0
            for y in (-.08,1.05):add('pommel_rim',(x-.10,y,z-.10,x+.10,y+.08,z+.10),'guard')
    return out
