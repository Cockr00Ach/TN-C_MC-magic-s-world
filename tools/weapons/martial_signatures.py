"""Distinct authored routes: stance, wind-up, lateral release, recoil. Spear is untouched."""
# arm XYZ, root yaw, torso pitch; these are game exaggerations of basic methods.
DESIGNS = {
 'yue_bronze_jian': [((-85,-98,25),-38,-5),((-72,65,-18),32,6),((-98,0,0),8,12)],
 'han_iron_jian': [((-140,-72,60),-28,-8),((-115,85,-48),48,0),((-90,8,-8),12,14)],
 'willow_leaf_dao': [((-165,-95,85),-55,-8),((-170,95,-75),55,-4),((-65,115,-30),78,16)],
 'ring_pommel_dao': [((-110,-105,22),-48,-4),((-45,92,-40),58,15),((-125,-85,38),-35,-2),((-48,108,-28),65,12)],
 'crescent_guandao': [((-150,-112,35),-60,-15),((-82,-135,12),-72,4),((-70,130,-30),90,22),((-32,85,-12),50,18)],
 'fangtian_ji': [((-90,112,-28),60,4),((-65,58,35),28,-8),((-60,-105,48),-55,5),((-85,125,-35),78,14)],
 'iron_jian_mace': [((-105,-98,35),-42,-8),((-48,105,-32),48,20),((-135,65,-45),35,-10),((-40,-112,30),-50,16)],
 'monk_spade': [((-75,125,-15),68,4),((-50,-115,40),-62,-8),((-135,-85,70),-40,-4),((-70,120,-25),78,18)],
 'qimei_staff': [((-155,-115,70),-55,-8),((-28,112,-55),60,14),((-150,100,-65),55,-6),((-45,-125,55),-65,14)],
}


def signature(id,slot,twohand):
    route=DESIGNS[id]
    # Different ready positions help distinguish silhouettes before the attack too.
    ready={'yue_bronze_jian':(-58,-12,18),'han_iron_jian':(-76,18,-12),
           'willow_leaf_dao':(-65,30,-25),'ring_pommel_dao':(-48,-25,30),
           'crescent_guandao':(-68,-38,20),'fangtian_ji':(-82,35,-24),
           'iron_jian_mace':(-55,-20,34),'monk_spade':(-72,42,-20),
           'qimei_staff':(-85,-35,28)}[id]
    if slot==1:
        arm=route[0][0]
        frames=[(0,ready,0,0),(.35,arm,route[0][1]*.35,2),(1,arm,route[0][1]*.35,2)]
    else:
        if slot==0:
            # Draw across the body, release across screen, then finish on the point for swords.
            stages=route[:2]+[((-95,0,0),8,14)] if id in ('yue_bronze_jian','han_iron_jian') else [route[0],route[-1]]
            phases=[.22+i*.48/max(1,len(stages)-1) for i in range(len(stages))]
        elif slot==2:
            stages=[route[0],((-72,route[1][0][1]*.65,route[1][0][2]*.5),route[1][1]*.5,18)]
            phases=[.28,.52]
        elif slot==4:
            # Withdraw to the outside, then cut back across; no whole-body rewind/spin.
            stages=[route[-1],route[0],route[1]];phases=[.2,.4,.72]
        elif slot==5:
            stages=list(reversed(route));phases=[.18+i*.62/max(1,len(stages)-1) for i in range(len(stages))]
        else:
            stages=route;phases=[.2+i*.58/max(1,len(stages)-1) for i in range(len(stages))]
        frames=[(0,ready,0,0)]+[(t,*s) for t,s in zip(phases,stages)]+[(1,ready,0,0)]
    tracks={k:{'rotation':{}} for k in ('bone','head','armRight','armLeft','legRight','legLeft')}
    for t,arm,yaw,pitch in frames:
        if twohand:
            # Both arms stay active, with a narrower front grip rather than identical mirroring.
            other=[arm[0]+14,-arm[1]*.45,-arm[2]*.55]
        elif id=='iron_jian_mace':other=[-72,-yaw*.3,35]  # guarding hand during short strikes
        else:other=[-32-pitch*.5,-yaw*.35,42+abs(yaw)*.15]
        vals={'bone':[pitch,yaw,0],'head':[-pitch*.45,-yaw*.12,0],
              'armRight':arm,'armLeft':other,'legRight':[-pitch*1.3,0,-6],
              'legLeft':[pitch*.9,0,8]}
        for bone,vec in vals.items():tracks[bone]['rotation'][str(round(t,5))]=[round(v,3) for v in vec]
    return {'animation_length':1,'bones':tracks}
