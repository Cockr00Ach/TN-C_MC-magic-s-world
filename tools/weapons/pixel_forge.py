"""Stepped, relief-carved weapon geometry and a deliberately limited pixel atlas.

All blocks are actual item elements. No smooth normals, procedural noise or gradients.
The existing weapon definitions remain the silhouette / colour / grip authority.
"""
import math
from PIL import Image

GRID = .25


def parts(weapon, lerp):
    result = []
    def add(name, box, style='guard'):
        box=tuple(round(v, 4) for v in box)
        if all(round(box[i+3],2) > round(box[i],2) for i in range(3)):
            result.append((f'{name}_{len(result)}', box, style, 'block'))

    # Fittings get actual stepped shoulders, individual wrapped grip segments and
    # raised fastening studs. Rotated horns are rasterized in their original frame.
    for name, box, style, mode, *rotation in weapon['parts']:
        x0,y0,z0,x1,y1,z1 = box
        step = .25 if style != 'grip' else .5
        angle = math.radians(rotation[0][1]) if rotation else 0
        origin = rotation[0][2] if rotation else (0,0,0)
        corners = []
        for x in (x0,x1):
            for z in (z0,z1):
                dx,dz = x-origin[0],z-origin[2]
                corners.append((origin[0]+dx*math.cos(angle)+dz*math.sin(angle),
                                origin[2]-dx*math.sin(angle)+dz*math.cos(angle)))
        loX,hiX = min(p[0] for p in corners),max(p[0] for p in corners)
        loZ,hiZ = min(p[1] for p in corners),max(p[1] for p in corners)
        for iz in range(math.floor(loZ/step),math.ceil(hiZ/step)):
            za,zb = max(loZ,iz*step),min(hiZ,(iz+1)*step)
            for ix in range(math.floor(loX/GRID),math.ceil(hiX/GRID)):
                xa,xb = max(loX,ix*GRID),min(hiX,(ix+1)*GRID)
                xc,zc=(xa+xb)/2,(za+zb)/2
                dx,dz=xc-origin[0],zc-origin[2]
                localX=origin[0]+dx*math.cos(angle)-dz*math.sin(angle)
                localZ=origin[2]+dx*math.sin(angle)+dz*math.cos(angle)
                if not (x0 <= localX <= x1 and z0 <= localZ <= z1): continue
                d=min(localX-x0,x1-localX,localZ-z0,z1-localZ)
                bevel=min(.125,(y1-y0)*.18) if d<.20 else 0
                wrap=.0625 if style=='grip' and (iz+ix//2)%3==0 else 0
                add(name,(xa,y0+bevel-wrap,za,xb,y1-bevel+wrap,zb),style)
        if style in ('guard','gem') and x1-x0>.8 and z1-z0>.6:
            # Raised metal rim and central inlaid stone, on both faces.
            for sign in (-1,1):
                y=y1 if sign>0 else y0-.125
                for x in (x0+.125,x1-.375):
                    for z in (z0+.125,z1-.375):
                        add('rivet',(x,y,z,x+.25,y+.125,z+.25),'edge')
        if style=='grip':
            # Individual interlocking leather strips rise from the handle, with
            # a stepped diagonal course on the reverse rather than a flat stripe.
            for band in range(math.ceil((z1-z0)/.5)):
                z=z0+band*.5
                for ix in range(max(1,math.ceil((x1-x0)/.25))):
                    x=x0+ix*.25;zz=z+(ix%3)*.0625
                    for y in (y0-.0625,y1):
                        add('woven_grip',(x,y,zz,min(x+.25,x1),y+.0625,min(zz+.1875,z1)),'grip')

    prof=weapon.get('profile')
    if prof:
        # Square pixels define the outline, while bevel depth follows the distance
        # from the cutting edge. Longitudinal ribs remain tangible from side views.
        for iz in range(round(prof[0][0]/GRID),math.ceil(prof[-1][0]/GRID)):
            z=iz*GRID
            xl,xr=lerp(prof,z+GRID/2,0),lerp(prof,z+GRID/2,1)
            left,right=round(xl/GRID),max(round(xl/GRID)+1,round(xr/GRID))
            for ix in range(left,right):
                edge=min(ix-left,right-1-ix)
                thickness=weapon['thick']
                inset=min(thickness*.36,max(0,2-edge)*.125)
                top,bottom=thickness-inset,inset
                style='edge' if ix==right-1 else 'blade'
                add('forged_blade',(ix*GRID,bottom,z,(ix+1)*GRID,top,z+GRID),style)
                # A stepped longitudinal back ridge and fine metal inlay are
                # actual volume on either face, visible edge-on as well.
                if edge>0 and ix==left+1:
                    for a,b in ((top,top+.0625),(bottom-.0625,bottom)):
                        add('forged_ridge',(ix*GRID,a,z,(ix+1)*GRID,b,z+GRID),'blade')
                # Geometry motifs use spaced coherent strokes, leaving calm metal
                # between them. Both sides receive the same craftsmanship.
                mid=(left+right-1)//2
                identity=weapon['palette']
                relief=False; material='guard'
                if identity=='dragon':
                    relief=(abs(ix-mid)==(iz%8)//2 and iz%8<6)
                    material='gem' if iz%16==0 else 'guard'
                elif identity=='frost':
                    relief=(ix==left+1 and iz%12<8) or (iz%16 in (4,5) and ix==mid)
                    material='edge'
                elif identity=='star':
                    relief=ix==mid and iz%12<6
                    material='guard'
                elif identity=='crimson':
                    relief=(ix==left+1 and iz%12<10) or (abs(ix-mid)==1 and iz%12 in (2,3,4)) or (ix==mid and iz%12 in (1,5))
                elif identity=='doom':
                    relief=(iz%8 in (0,1) and left+1<=ix<right-1)
                elif identity=='sky':
                    relief=(ix==right-3 or (iz%12 in (0,1) and abs(ix-mid)<=1))
                    material='guard'
                if relief and edge>0:
                    for a,b in ((top,top+.125),(bottom-.125,bottom)):
                        add('carved_'+identity,(ix*GRID,a,z,(ix+1)*GRID,b,z+GRID),material)

    # A hammer is constructed around a reinforced head, with recessed joints,
    # staggered armour plates, brass bolts and concentric stepped striking faces.
    if weapon['palette']=='earth':
        for sign in (-1,1):
            for ix in range(10):
                for iz in range(7):
                    x,z=5.25+ix*.5,11.3+iz*.5
                    y=3.2 if sign>0 else .05
                    add('head_armour',(x,y,z,x+.4375,y+.1875,z+.4375),'blade')
                    if ix%3==1 and iz%3==1:
                        yy=y+.1875 if sign>0 else y-.125
                        add('head_bolt',(x+.125,yy,z+.125,x+.3125,yy+.125,z+.3125),'guard')
        for sign in (-1,1):
            for layer in range(3):
                x=7.6+sign*(3.7+layer*.125)
                add('strike_step',(x-.125,.65+layer*.25,11.8+layer*.25,
                                   x+.125,2.8-layer*.25,14.3-layer*.25),'guard' if layer==0 else 'edge')
    return result


def texture(weapon, expanded, palettes, mix, dark, light):
    """Independent role tiles: fittings cannot overwrite a blade's UVs anymore.

    Eight variants per material, each an 8px tile: hard three-tone clusters,
    corner chips, straight engravings and restrained specular pixels.
    """
    image=Image.new('RGBA',(128,128),(0,0,0,0))
    px=image.load();pal=palettes[weapon['palette']]
    roles=('blade','edge','guard','grip','gem')
    for roleIndex,role in enumerate(roles):
        base=pal[role]
        hi=pal.get({'blade':'blade_hi','edge':'edge','guard':'guard_hi','grip':'wrap','gem':'gem_hi'}[role])
        colors=(dark(base,.30),base,mix(base,hi,.55),hi)
        for variant in range(8):
            u,v=(roleIndex*8+variant)%16*8,(roleIndex*8+variant)//16*8
            for y in range(8):
                for x in range(8):
                    tone=2 if x>=5 else 1
                    if x==0: tone=0
                    if y==0 and x>1: tone=3
                    if role=='grip': tone=2 if (y+x//3)%6<2 else 1
                    if role=='gem': tone=3 if 1<x<5 and 1<y<4 else (0 if x>5 or y>5 else 2)
                    if role=='blade' and variant in (2,5) and y in (5,6) and x in (2,3): tone=2
                    if role=='guard' and variant%3==0 and x in (2,5) and 2<=y<=5: tone=0
                    c=colors[tone]
                    # Only a few deliberate clustered patina values. No random
                    # speckles, soft gradients or baked photographic highlights.
                    if variant==1:c=dark(c,.06)
                    elif variant==5:c=mix(c,hi,.08)
                    px[u+x,v+y]=c
    rects={}
    for index,part in enumerate(expanded):
        roleIndex=roles.index(part[2]);box=part[1]
        # Stable location-based assignment, without random grain/noise.
        variant=(round(box[0]*4)//3+round(box[2]*4)//6)%8
        tile=roleIndex*8+variant
        # One model pixel samples one atlas pixel, rather than shrinking an entire
        # outlined tile onto every tiny cube (which makes a noisy woven surface).
        x,y=3,4
        if part[2]=='blade':
            if weapon.get('profile'):
                z=(box[2]+box[5])/2;profile=weapon['profile']
                from bisect import bisect_right
                i=max(0,min(len(profile)-2,bisect_right([p[0] for p in profile],z)-1))
                a,b=profile[i],profile[i+1];t=max(0,min(1,(z-a[0])/(b[0]-a[0])))
                left=a[1]+(b[1]-a[1])*t;right=a[2]+(b[2]-a[2])*t
                f=((box[0]+box[3])/2-left)/max(.25,right-left)
                x=0 if f<.16 else (6 if f>.65 else 3)
            else: x=6 if round(box[2]*4)%8<3 else 3
        elif part[2]=='edge': x,y=6,0
        elif part[2]=='grip':
            x=3;y=(round(box[2]*4)+round(box[0]*4)//3)%6
        elif part[2]=='guard': x,y=6,4
        elif part[2]=='gem': x,y=3,2
        rects[part[0]]=(tile%16*8+x,tile//16*8+y,1,1)
    return image,rects


def hidden_faces(expanded):
    """Remove exact matching internal quads; never use world-block cullface on items."""
    boundaries={};hidden={}
    for name,box,*_ in expanded:
        for axis,negative,positive in ((0,'west','east'),(1,'down','up'),(2,'north','south')):
            others=[i for i in range(3) if i!=axis]
            span=tuple(round(box[i+j],4) for i in others for j in (0,3))
            for side,face in ((0,negative),(3,positive)):
                key=(axis,round(box[axis+side],4),span)
                opposite=positive if side==0 else negative
                mate=boundaries.get((key,opposite))
                if mate is not None:
                    hidden.setdefault(name,set()).add(face)
                    hidden.setdefault(mate,set()).add(opposite)
                boundaries[(key,face)]=name
    return hidden


def bow_parts(palette, pull=0):
    """Rigid carved riser, articulated stepped limbs, a moving V string and nocked arrow."""
    out=[]
    def add(name,b,style):out.append((f'{name}_{len(out)}',b,style,'block'))
    def tile(name,x,y,z,w,h,d,style):
        for ix in range(max(1,math.ceil(w/.25))):
            for iy in range(max(1,math.ceil(h/.25))):
                xa,ya=x+ix*.25,y+iy*.25
                add(name,(xa,ya,z,min(x+w,xa+.25),min(y+h,ya+.25),z+d),style)
    # Hand sits exactly in the middle of the reinforced riser.
    tile('riser',7.5,5.5,7.4,1,5,1.2,'grip')
    for s in (-1,1):
        for i in range(25):
            y=8+s*(1.8+i*.45)
            # The limb tips bend inward as tension increases, without moving the grip.
            x=7.6-2.5*math.sin(i/24*math.pi)+i/24*2.6+pull*(i/24)**2*.55
            width=.8 if i<19 else .55
            if palette=='doom':x+=.35*math.sin(i*.6)
            if palette=='crimson':width+=.3 if i%5<3 else 0
            tile('laminated_limb',round(x*4)/4,round(y*4)/4,7.65,width,.55,.7,'blade')
            if i%3==0:
                tile('limb_socket',round(x*4)/4-.125,round(y*4)/4,7.4,width+.25,.25,1.2,'guard')
            # Crystal fletches on the outer shoulder, independent of the string.
            if i in (5,9,13):
                for j in range(4):
                    tile('limb_carving',x-.25-j*.25,y+s*j*.15,7.55,.25,.5-j*.08,.9,'gem' if j==3 else 'guard')
        endpoint=8+s*(1.8+24*.45)
        endX=10.2+pull*.55
        # Pixel raster of a tensioned string: upper/lower halves meet at the nock.
        steps=50
        for j in range(steps):
            f=j/steps;f2=(j+1)/steps
            x=endX+(11+pull*3.5-endX)*f;y=endpoint+(8-endpoint)*f
            x2=endX+(11+pull*3.5-endX)*f2;y2=endpoint+(8-endpoint)*f2
            add('string',(min(x,x2)-.045,min(y,y2)-.045,7.955,max(x,x2)+.045,max(y,y2)+.045,8.045),'edge')
    for sign in (-1,1):
        for i in range(5):
            tile('riser_cheek',7.0+i*.4,5.4 if sign<0 else 10.35,7.25,.35,.3,1.5,'guard')
    tile('arrow_rest',7.1,7.65,7,1.6,.35,2,'guard')
    tile('riser_jewel',7.75,7.6,8.65,.5,.8,.25,'gem')
    if pull>0:
        tail=11+pull*3.5
        tile('nocked_shaft',tail-12,7.92,7.92,12,.16,.16,'grip')
        for i in range(5):tile('nocked_tip',tail-13+i*.25,8-.1*i,8-.1*i,.25,.2*i+.2,.2*i+.2,'edge')
        for s in (-1,1):tile('nocked_feather',tail-2.5,8+s*.25,7.8,1.75,.25,.4,'gem')
    return out


def arrow_parts(palette):
    out=[]
    def add(b,style):out.append((f'arrow_{len(out)}',b,style,'block'))
    for i in range(56):
        z=-5+i*.25
        add((7.875,7.875,z,8.125,8.125,z+.25),'grip')
        if i%8==0:add((7.8125,7.8125,z,8.1875,8.1875,z+.125),'guard')
    # Four intersecting stepped vanes, with independently modelled spines.
    for side in (-1,1):
        for i in range(12):
            z=-4.5+i*.25;span=.25+(.75 if 2<=i<=8 else .25)
            for j in range(round(span/.25)):
                offset=side*(j+1)*.25
                add((7.9+offset,7.92,z,8.1+offset,8.08,z+.25),'gem' if i%4==0 else 'blade')
                add((7.92,7.9+offset,z,8.08,8.1+offset,z+.25),'gem' if i%4==0 else 'blade')
    # A socket and hollow-looking cruciform broadhead, not a flat arrow sprite.
    for i in range(12):
        z=8+i*.25;width=max(.125,(12-i)*.10)
        add((8-width,7.8,z,8+width,8.2,z+.25),'edge')
        add((7.8,8-width,z,8.2,8+width,z+.25),'blade')
        if palette=='crimson' and i%3==0:
            add((7.7-width,7.85,z-.2,8.3+width,8.15,z+.15),'guard')
    add((7.6,7.6,7.5,8.4,8.4,8.2),'gem')
    add((7.7,7.7,-5.3,8.3,8.3,-5),'guard')
    return out
