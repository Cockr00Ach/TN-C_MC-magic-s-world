"""Wind-cut gate through the western limestone divide, in world coordinates."""
import numpy as np

CX, CZ = -334, -102
ANGLE = np.deg2rad(12)
NX, NZ = np.cos(ANGLE), np.sin(ANGLE)


def _ease(t):
    t = np.clip(t, 0, 1)
    return t*t*(3-2*t)


def _geometry(x, z):
    # Traverse the ridge along s. Follow the canyon through it along r.
    dx, dz = x-260-CX, z-260-CZ
    r, s = NX*dx+NZ*dz, -NZ*dx+NX*dz
    spine = 4.9*np.sin(s/23) + .12*s - 1.7*np.cos(s/12)
    normal = r-spine
    center = 2 + 3.1*np.sin((r-4)/18) + .054*r
    across = s-center
    width = 33.5 + 3.3*np.sin((r+5)/13) + np.minimum(13,np.abs(r)*.14)
    # The gorge bed meanders and collects talus against the outside wall.
    floor = (238.7 + .022*r + 1.45*np.sin(r/22) + .8*np.sin((s+.2*r)/19)
             + 6*_ease((np.abs(across)-11)/28) + 2.2*_ease((across-20)/18))
    lean = np.where(across<0, width*1.11, width*.93)
    profile = np.maximum(0, 1-np.abs(across/lean)**1.66)
    # A broad fractured northern crown meets a steep, undercut southern pier.
    aperture = (55 + 3.4*np.sin((r-8)/13) - 1.6*np.sin(r/6.4))*profile**.57
    aperture += profile*(3*np.sin((s+6)/12)+1.2*np.sin(.28*s+.20*r))
    return r,s,normal,across,floor,aperture


def _window(x,z):
    xx,zz = x[0]-260, z[:,0]-260
    return np.s_[np.searchsorted(zz,CZ-151):np.searchsorted(zz,CZ+152),
                 np.searchsorted(xx,CX-154):np.searchsorted(xx,CX+155)]


def grade(x,z,h,raw,protect,land):
    w = _window(x,z)
    r,s,n,across,bed,hole = _geometry(x[w],z[w])
    # The arch shares its flanks with the RTF divide for well over a hundred
    # blocks. Its long inclined back and split north buttress are visible in
    # silhouette even when the opening is seen edge-on.
    weight = (1-_ease((np.abs(s)-51)/47))*(1-_ease((np.abs(n)-25)/56))
    variation = np.clip((raw[w]-np.median(raw[w]))*.10,-6,6)
    crown = 80 + 15*np.exp(-((s+32)/17)**2) - 10*np.exp(-((s-28)/17)**2)
    crown += 3.5*np.sin((s+5)/13)+2.1*np.cos((s+n*.4)/7.3)+variation
    margin = 24 + 4*np.sin((s-8)/19) + 3*np.sin(s/8.5)
    back = np.where(n<0, margin*1.30,margin*.85)
    crest = bed+crown*np.exp(-np.abs(n/back)**1.55)
    # A second high ravine cuts behind the short spur. From the eastern basin
    # this leaves daylight outside the slender south abutment as well as inside
    # the arch, while the long north side still shoulders into the main massif.
    ravine = (s-57-.14*n-2*np.sin(n/11))/9.5
    crest -= 53*np.exp(-ravine*ravine-(n/40)**2)*_ease((s-47)/9)
    buttress = 14*np.exp(-((n-22)/18)**2) * np.exp(-((s+37)/20)**2)
    local = h[w]+90
    local = local+(crest+buttress-local)*weight
    # At both mouths, open a tapering notch into the surrounding catchment.
    # Beyond the west outlet the lower bench leaves a line of sky through the gate.
    width = 19+np.minimum(28,np.abs(r)*.27)
    valley = (1-_ease((np.abs(across)-width)/20))*_ease((np.abs(n)-27)/16)
    valley *= 1-_ease((np.abs(r)-79)/45)
    ledges = 7*_ease((np.abs(across)-12)/18) + 14*_ease((np.abs(across)-34)/15)
    local -= np.maximum(0,local-(bed+ledges))*valley
    # Keep a continuous walkable stream-bed at the mouths while the mountain
    # above it remains solid for the subtractive pass through its interior.
    centerline = (1-_ease((np.abs(across)-6)/17))*(1-_ease((np.abs(r)-74)/20))
    local += np.maximum(0,bed-local)*centerline
    writable = ~protect[w]&land[w]
    np.copyto(h[w],local-90,where=writable)
    exclude = np.zeros_like(land)
    exclude[w] = writable & (np.abs(r)<67) & (hole>0) & (np.abs(across)<42)
    return h,exclude


def carve(v,x,z,h):
    w = _window(x,z)
    wx,wz = x[w]-260,z[w]-260
    r,s,n,across,bed,hole = _geometry(x[w],z[w])
    foot = np.rint(bed).astype(int)-90
    soffit = np.floor(bed+hole).astype(int)-90
    # The gate must connect front to back without creating a blind cut in the
    # valley wall. Only the ridge and its immediate mouths are excavated.
    opening = (np.abs(r)<79) & (hole>1)
    iz,ix = np.nonzero(opening)
    vz,vx = iz+w[0].start,ix+w[1].start
    f,t = foot[iz,ix],soffit[iz,ix]
    original = h[w][iz,ix].astype(int)
    intersect = original >= f
    for y in range(f.min()+1,t.max()+1):
        take = intersect & (f<y)&(y<=t)
        v.blocks[y,vz[take],vx[take]]=0
    # A seam of metamorphic blue rock follows the underside of the bed. Wet
    # slate appears on one wall at the melt channel; the lighter broken lenses
    # follow the same inclined strike through both sides of the opening.
    rz,sz = r[iz,ix],s[iz,ix]
    seam = 3.5*np.sin(sz*.117+rz*.044) + .22*sz + 1.6*np.cos(.20*sz-.10*rz)
    vein = (t+90-283-seam)
    ceiling = intersect & (original>=t+3) & (v.blocks[t+1,vz,vx]!=0)
    blue = v.id("conquest:blue_schist")
    pale = v.id("conquest:layered_chalk")
    schist = ceiling & (vein>-5.4)&(vein<2.7)
    chalk = ceiling & (vein>=2.7)&(vein<5.8)&(sz<11)
    v.blocks[t[schist]+1,vz[schist],vx[schist]]=blue
    v.blocks[t[chalk]+1,vz[chalk],vx[chalk]]=pale
    gravel = v.id("conquest:dark_gray_limestone_gravel")
    moss = v.id("conquest:moss_covered_limestone")
    slate = v.id("conquest:wet_slate")
    # Gravel deposits follow shallow bends, changing into subdued moss towards
    # daylight at the eastern mouth. Leave native strata on the remaining bed.
    rivulet = sz-4-3.7*np.sin(rz*.075)
    bedrock = intersect & (v.blocks[f,vz,vx]!=0)
    wet = bedrock & (np.abs(rivulet)<2.0+.6*np.cos(rz*.15))
    shingle = bedrock & (rivulet>2)&(rivulet<7+2*np.sin(rz*.14))
    green = bedrock & (rz>18)&(rz<44)&(rivulet<-4)&(rivulet>-12+2*np.cos(rz*.26))
    v.blocks[f[wet],vz[wet],vx[wet]]=slate
    v.blocks[f[shingle],vz[shingle],vx[shingle]]=gravel
    v.blocks[f[green],vz[green],vx[green]]=moss
    # Sparse ceiling colonies grow on damp underside faces only.
    light = v.id("minecraft:glow_lichen[down=false,east=false,north=false,south=false,up=true,waterlogged=false,west=false]")
    lichens = ceiling & (vein<-3) & (np.sin(.43*sz+.14*rz)+np.cos(.22*sz-.49*rz)>1.87)
    v.blocks[t[lichens],vz[lichens],vx[lichens]]=light
    return validate(v,x,z)


def validate(v,x,z):
    """Probe real body blocks along the canyon, including both lower abutments."""
    xmin,zmin = int(x.min())-260,int(z.min())-260
    def samples(normal,along):
        return (np.rint(CZ+NZ*normal+NX*along-zmin).astype(int),
                np.rint(CX+NX*normal-NZ*along-xmin).astype(int))
    along = np.arange(-28,29)
    normal = np.arange(-70,71)
    n,s=np.meshgrid(normal,along)
    iz,ix=samples(n,s)
    permitted=[]
    for y in range(249,295):
        permitted.append(np.all(v.blocks[y-90,iz,ix]==0,axis=1))
    permitted=np.asarray(permitted)
    clear_area=int(permitted.sum())
    if clear_area < 1100 or not permitted[274-249,len(along)//2]:
        raise ValueError(f"Western gate lacks a clear daylight opening ({clear_area} clear rays)")
    wz,wx=samples(normal,np.zeros_like(normal))
    path_floor=[]
    for iz,ix in zip(wz,wx):
        support=np.flatnonzero(v.blocks[135:164,iz,ix])
        if not len(support):
            raise ValueError("Unsupported western gorge floor")
        ground=int(support[-1])+135
        if np.any(v.blocks[ground+1:ground+4,iz,ix]):
            raise ValueError("Obstructed western gorge floor")
        path_floor.append(ground)
    if np.max(np.abs(np.diff(path_floor)))>1:
        raise ValueError("Broken grade through the western gorge")
    crown_z,crown_x=samples(0,0)
    thickness=int(np.count_nonzero(v.blocks[205:260,crown_z,crown_x]))
    if thickness<10:
        raise ValueError(f"Western gate crown too thin: {thickness}")
    for side in (-46,46):
        pz,px=samples(0,side)
        if np.count_nonzero(v.blocks[163:202,pz,px])<32:
            raise ValueError("Detached arch buttress")
    result = {"center_world":[CX,269,CZ],"view_normal":[round(float(NX),3),round(float(NZ),3)],
              "clear_parallel_rays":clear_area,"throat_width_y267":int(permitted[267-249].sum()),
              "walk_length":len(normal),"crown_thickness":thickness}
    print("Western erosion gate:",result,flush=True)
    return result
