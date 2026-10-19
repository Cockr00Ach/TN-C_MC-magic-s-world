"""Build the general entirely from layered, slightly angled cuboids.

No sculpted/chamfered mesh and no image-based face or armor relief.
"""


def build(bone, cube):
    bone('root',(0,0,0),None)
    pelvis=bone('pelvis',(0,18,0),'root')
    cube(pelvis,(0,19,0),(7.2,4.8,4),7)
    body=bone('torso',(0,22,0),'pelvis')
    cube(body,(0,27,0),(7.7,9.7,4.2),7)
    # The silver breastplate is several overlapping plates with a raised cloud crest.
    for s in (-1,1):
        cube(body,(s*2.0,28,-2.25),(4.1,6.2,1),0,(0,s*5,s*5))
        cube(body,(s*3.5,30.6,-1),(2.4,2.1,4.4),2,(0,s*5,s*-12))
        for j in range(3):
            cube(body,(s*(1.3+j*.65),29.9-j*.55,-2.94),(1.25,.55,.3),3,(0,0,s*22))
        cube(body,(s*1.6,27.6,-3.02),(1.7,.45,.3),8,(0,0,s*-18))
        for row in range(3):
            for col in range(3):
                cube(body,(s*(.75+col*1.05),25.3-row*.95,-2.62),(1.2,1.4,.6),1 if row%2 else 0,(0,s*3,s*3))
    cube(body,(0,22,-2.5),(8.3,1.15,.85),8)
    cube(body,(0,22,-3.1),(1.8,1.45,.65),13,(0,0,45))
    cube(body,(0,28,2.2),(7.8,7.2,.9),1)
    neck=bone('neck',(0,32,0))
    cube(neck,(0,33,0),(2.8,2.5,2.8),11)
    for s in (-1,1):cube(neck,(s*1,32.2,-1.8),(2,1.3,1.2),4,(0,0,s*15))

    head=bone('head',(0,33.4,0),'neck')
    cube(head,(0,36.9,.35),(5.5,5.4,4.6),11)
    cube(head,(0,34.65,-.1),(4.7,1.7,3.8),11)
    cube(head,(0,38.5,-2),(4.7,1.5,.8),16)
    cube(head,(0,34.25,-2),(2.4,.75,.85),16)
    for s in (-1,1):
        cube(head,(s*2.2,37.7,-1.7),(1.1,1.8,1.1),11,(0,s*7,0))
        cube(head,(s*1.85,36.15,-2.05),(1.6,1.2,1.15),16,(0,s*8,s*4))
        cube(head,(s*2.05,35.1,-1.65),(1.1,1.4,1.55),11,(0,s*8,s*-9))
        cube(head,(s*2.75,36.8,.1),(.55,1.25,1.1),11)
        # Each eye and eyebrow is a set of shallow little cuboids.
        cube(head,(s*1.15,37.35,-2.42),(1.65,.65,.28),17)
        cube(head,(s*1.15,37.35,-2.62),(1.4,.43,.16),18)
        cube(head,(s*.99,37.35,-2.75),(.4,.43,.14),19)
        cube(head,(s*.93,37.43,-2.85),(.12,.12,.08),18)
        cube(head,(s*1.2,37.0,-2.48),(1.5,.2,.3),16)
        cube(head,(s*1.2,37.88,-2.4),(1.8,.42,.65),11,(0,0,s*5))
        for j in range(2):cube(head,(s*(.78+j*.65),38.06+j*.06,-2.78),(.8,.18,.2),21,(0,0,s*5))
        cube(head,(s*.38,36.1,-2.65),(.35,.3,.5),17)
    cube(head,(0,37.0,-2.4),(.6,1.5,.65),16)
    cube(head,(0,36.25,-2.65),(.7,.5,.7),16)
    cube(head,(0,36.05,-2.88),(.5,.28,.5),11)
    cube(head,(0,35.45,-2.35),(1.4,.16,.2),20)
    cube(head,(0,35.22,-2.31),(1.2,.22,.3),16)
    # Hair is a cap plus individual angled locks, not a painted human photograph.
    cube(head,(0,39.35,.3),(5.8,1.2,5),9)
    for j in range(5):cube(head,((j-2)*1.05,37.6-abs(j-2)*.2,2.65),(1.15,4.5,.9),9,(0,0,(j-2)*3))
    for s in (-1,1):
        cube(head,(s*2.65,37.8,.25),(.75,3.7,3.7),9,(0,0,s*4))
        cube(head,(s*1.7,39.1,-2.18),(1.5,1.3,.6),9,(0,0,s*-12))

    helmet=bone('helmet',(0,39.3,0),'head')
    cube(helmet,(0,40,.3),(6.4,1.65,5.8),0)
    for col in range(5):
        cube(helmet,((col-2)*1.2,39.5,-2.8),(1.25,.65,.7),8)
        cube(helmet,((col-2)*1.1,40.65,-1.8),(1.2,.55,3.2),2,(0,(col-2)*4,0))
    cube(helmet,(0,40.1,-3.15),(1.25,1.7,.5),13,(0,0,45))
    for s in (-1,1):
        for j in range(2):cube(helmet,(s*3.02,38.5-j*1.15,.1),(.8,1.6,3.6-j*.4),0,(0,0,s*(-7+j*7)))
        cube(helmet,(s*3.0,37,-1.65),(.65,.65,.65),8)
    for j in range(1,4):
        parent='helmet' if j==1 else 'plume_'+str(j-1)
        top=40.8+(j-1)*3.2;w=1.3-j*.22
        plume=bone('plume_'+str(j),(0,top,1),parent)
        cube(plume,(0,top+1.6,1),(w,3.25,w),14)
        for s in (-1,1):cube(plume,(s*w*.38,top+1.6,1),(.3,3.15,w*.9),4)
    hair=bone('hair_tail',(0,38,3),'head')
    for s in (-1,0,1):cube(hair,(s*.6,35.8,3.7),(.8,5.5,1.1),9,(10,0,s*4))

    for s in (-1,1):
        tag='left' if s==1 else 'right'
        leg=bone('leg_'+tag,(s*2,18,0),'pelvis',(0,0,s*-3))
        cube(leg,(s*2,13.9,0),(3.2,8.1,3.6),5)
        for row in range(3):
            for col in (-1,1):cube(leg,(s*2+col*.8,15.2-row*1.5,-1.8),(1.7,1.8,.7),1,(0,0,col*3))
        shin=bone('shin_'+tag,(s*2,10,0),'leg_'+tag)
        cube(shin,(s*2,6.2,0),(2.8,7.6,3.2),10)
        for j in range(3):cube(shin,(s*2,8.8-j*1.9,-1.65),(3-j*.15,2.25,.7),3,(0,0,s*2))
        cube(shin,(s*2,10,-1.8),(3.2,1.8,.95),0,(0,0,45))
        for y in (4,9):cube(shin,(s*2,y,-2.1),(2.8,.4,.35),8)
        cube(shin,(s*2,1.4,-.65),(3.2,2.6,4.7),10)
        cube(shin,(s*2,2.0,-2.5),(3.2,1,1.6),0,(-8,0,0))
        skirt=bone('skirt_'+tag,(s*3.2,22,0),'pelvis',(0,0,s*-7))
        cube(skirt,(s*3.8,18.5,.25),(3.5,7,3.3),7)
        for row in range(3):
            for col in (-1,1):cube(skirt,(s*3.8+col*.85,20.7-row*1.7,-1.65),(1.8,2.1,.7),1 if row%2 else 0,(0,s*5,s*-3))
        cube(skirt,(s*3.8,15.3,-1.7),(3.6,.4,.7),8)
        arm=bone('arm_'+tag,(s*5.2,31,0),'torso',(0,0,s*-9))
        cube(arm,(s*6.3,27.8,0),(3.1,6.3,3.7),4)
        # Three shoulder tiers are physically built, with individually raised edges.
        for row in range(3):
            cube(arm,(s*(5.7+row*.45),31.8-row*1.15,.1),(4.2-row*.15,1.65,5.0-row*.2),0 if row==0 else 1,(0,0,s*-12))
            for col in (-1,1):cube(arm,(s*(5.7+row*.45)+col*.95,31.6-row*1.15,-2.4),(1.65,1.3,.55),2,(0,0,s*-9))
        fore=bone('fore_'+tag,(s*7,25,-.1),'arm_'+tag,(-8,0,s*4))
        cube(fore,(s*7.4,22.6,-.25),(2.7,5.5,3.0),10)
        for j in range(3):cube(fore,(s*7.4,24.4-j*1.55,-1.8),(2.9,1.8,.75),3,(0,0,s*2))
        for y in (20.2,25):cube(fore,(s*7.4,y,-2.0),(3,.35,.4),8)
        hand=bone('hand_'+tag,(s*7.8,19.7,-.4),'fore_'+tag)
        cube(hand,(s*7.8,19.1,-.4),(2.45,2.7,2.6),10)
        for j in range(3):cube(hand,(s*7.8+(j-1)*.6,18.9,-1.75),(.55,1.6,.4),3)
        cube(hand,(s*6.5,19.1,-.7),(.65,1.8,1.2),10,(0,0,s*-18))

    sash=bone('sash',(0,28,-3.2))
    for j in range(5):cube(sash,(-2.6+j*1.2,31-j*1.6,-3.35),(1.25,2.4,.35),4,(0,0,-30))
    for s in (-1,1):
        parent='torso'
        for j in range(3):
            top=32.5-j*7.5;y=top-3.75;z=3.5
            name=f'cape_{s}_{j}';cape=bone(name,(s*2.4,top,z),parent,(10+j*3,0,s*3))
            for col in range(3):cube(cape,(s*(.9+col*1.6),y,z+col*.12),(1.8,7.5-col*.1,.45),4 if col!=2 else 5,(0,s*(col-1)*4,0))
            cube(cape,(s*5,y,z+.2),(.35,7.55,.5),6)
            parent=name
        parent='pelvis'
        for j in range(3):
            top=22-j*5
            name=f'ribbon_{s}_{j}';ribbon=bone(name,(s*4.5,top,2.7),parent,(15,0,s*(8+j*5)))
            cube(ribbon,(s*4.5,top-2.5,2.7),(1.4,5.1,.35),6 if j%2==0 else 4)
            parent=name
    spear=bone('spear',(-9.2,19.5,-2.2),'hand_right',(0,0,4))
    cube(spear,(-9.2,27.5,-2.2),(.65,50,.65),7)
    for y in (4,16,22,45,50):cube(spear,(-9.2,y,-2.2),(.95,.7,.95),8)
    cube(spear,(-9.2,52.2,-2.2),(1.8,1.6,1.2),0,(0,0,45))
    blade=bone('spear_point_up',(-9.2,53,-2.2),'spear')
    # A stepped three-dimensional spear blade assembled out of cuboids.
    for j,(y,w,h) in enumerate(((54,2,2),(56,1.5,2.6),(58,.95,2.2),(59.5,.4,1.3))):cube(blade,(-9.2,y,-2.2),(w,h,.7-j*.13),15)
    heel=bone('spear_point_down',(-9.2,2.6,-2.2),'spear')
    for j in range(2):cube(heel,(-9.2,2-j,-2.2),(1-j*.45,1.3,.7-j*.2),15)
    for s in (-1,1):
        tassel=bone('tassel_'+str(s),(-9.2+s*.7,51,-2.2),'spear')
        for j in range(2):cube(tassel,(-9.2+s*(1+j*.3),49-j*2.4,-2.2),(.65-j*.15,3,.45),4,(-5,0,s*9))
