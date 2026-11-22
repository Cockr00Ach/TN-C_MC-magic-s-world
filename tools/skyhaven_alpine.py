"""Fit a surveyed ReTerraForged mountain and its drainage to the authored sky realm.

Heights are in island-local coordinates. The saved survey is build input, never a
runtime world generator. The pack's overworld and pre-existing realms keep their saves.
"""
from pathlib import Path
import json
import numpy as np
from scipy import ndimage as nd
from skyhaven_sculpt import outline, X0, Z0, WIDTH, DEPTH
from skyhaven_natural_sites import sites, trail_constraints, capture_roots
from skyhaven_alpine_growth import plant
from skyhaven_rock_features import dress
import skyhaven_meadow_falls as meadow_falls
import skyhaven_summit as summit
import skyhaven_western_divide as western
import skyhaven_arch as arch
import skyhaven_crags as crags

HERE = Path(__file__).resolve().parent
SURVEY = HERE / "skyhaven_rtf_relief.npz"


def smoothstep(t):
    t = np.clip(t, 0, 1)
    return t*t*(3-2*t)


def fit_survey(source, target=SURVEY):
    """Choose a single continuous cut and orientation; no tiling or mirrored peaks."""
    with np.load(source, allow_pickle=False) as raw:
        h = raw["height"].astype(float)
        if not np.isfinite(h).all() or "valid" in raw and not raw["valid"].all():
            raise ValueError("Survey must be a contiguous rectangle of actual RTF terrain")
        water = raw["water"].astype(float) if "water" in raw else np.full(h.shape,-32768.)
        if "water" not in raw and "river" in raw:
            water[raw["river"].astype(bool)]=63.
        # Keep the original projection of every established district when extending
        # west. The expanded source overlaps every sample of the first survey.
        if h.shape==(1249,1281) and int(raw["origin_x"])==5260 and int(raw["origin_z"])==-1880:
            z,x=np.mgrid[Z0:Z0+DEPTH,X0:X0+WIDTH]
            scale=1.4566145092460883
            coordinates=[216+(x+96)*scale,116+(z+80)*scale]
            if min(a.min() for a in coordinates)<0 or any(a.max()>=h.shape[i] for i,a in enumerate(coordinates)):
                raise ValueError("Island extends past the native RTF survey")
            meta=json.loads((HERE/"skyhaven_rtf/manifest.json").read_text("utf8"))
            meta.update(scale=scale,quarter_turns=3,mirror=True,original_overlap_samples=1181825,
                        source_shape=list(h.shape),reference_range=[64.20230938,258.29589587])
            np.savez_compressed(target,height8=np.rint(nd.map_coordinates(h,coordinates,order=1)*8).astype(np.int16),
                                water=nd.map_coordinates(water,coordinates,order=0).astype(np.int16),meta=json.dumps(meta))
            return meta
        meta = {k: int(raw[k]) for k in ("x0","z0","seed","step") if k in raw}
        sidecar=Path(source).with_name("manifest.json")
        if sidecar.exists():
            origin=json.loads(sidecar.read_text("utf8"))
            meta.update({k:origin[k] for k in ("source","preset","x0","z0","spacing","jarSHA256","fieldSHA256","scope") if k in origin})
        if "meta" in raw or "metadata" in raw:
            provenance=json.loads(str(raw["meta" if "meta" in raw else "metadata"]))
            for k in ("engine","generator","preset","preset_name","seed","origin","step","jar_sha256"):
                if k in provenance:meta[k]=provenance[k]
    z,x = np.mgrid[Z0:Z0+DEPTH,X0:X0+WIDTH]
    mask = outline()
    edge = nd.distance_transform_edt(mask)
    candidates = (edge>20) & (((x<126)&(z<210))|((x>379)&(z<203))|((x>313)&(z>399)))
    northeast = candidates & (x>379) & (z<203)
    west = candidates & (x<126)
    town = (x>150)&(x<365)&(z>130)&(z<315)
    clip = np.percentile(h,[2,99.9])
    best = None
    # Evaluate the view at a coarse spacing; resample only the chosen cut at block scale.
    for turns in range(4):
        land=np.rot90(h, turns)
        for flip in (False,True):
            raster=np.fliplr(land) if flip else land
            for zoom in (.71,.86,1.0):
                span=min((raster.shape[0]-1)/(DEPTH-1),(raster.shape[1]-1)/(WIDTH-1))*zoom
                for ax in (0.,.5,1.):
                    for az in (0.,.5,1.):
                        offx=ax*(raster.shape[1]-1-(WIDTH-1)*span)
                        offz=az*(raster.shape[0]-1-(DEPTH-1)*span)
                        pos=np.array([(z[::5,::5]-Z0)*span+offz,(x[::5,::5]-X0)*span+offx])
                        sample=nd.map_coordinates(raster,pos,order=1,mode="nearest")
                        q=lambda region,n:np.percentile(sample[region[::5,::5]],n)
                        score=(.47*q(candidates,99.5)+.27*q(west,98)+.17*q(northeast,98)
                               +.12*(q(candidates,90)-q(candidates,15))-.23*q(town,85))
                        # Keep the branching valley in an outer district; a cropped
                        # ridge without its valley loses the scale visible in the survey.
                        mapped_water=water if turns==0 else np.rot90(water,turns)
                        if flip:mapped_water=np.fliplr(mapped_water)
                        openwater=nd.map_coordinates(mapped_water,pos,order=0,mode="nearest")>0
                        exterior=(mask & ~town & (edge>8))[::5,::5]
                        score+=min(1.,np.mean(openwater[exterior])/.035)*15+zoom*7
                        if best is None or score>best[0]:
                            best=(score,turns,flip,span,offx,offz)
    _,turns,flip,span,offx,offz=best
    def project(data,order):
        data=np.rot90(data,turns)
        if flip:data=np.fliplr(data)
        return nd.map_coordinates(data,[(z-Z0)*span+offz,(x-X0)*span+offx],order=order,mode="nearest")
    height=project(h,1)
    # Store 1/8-block precision; all columns remain traceable to the source survey.
    meta.update(quarter_turns=turns,mirror=flip,scale=float(span),offset_x=float(offx),offset_z=float(offz),
                source_shape=list(h.shape),source_height_range=[float(clip[0]),float(clip[1])])
    np.savez_compressed(target, height8=np.rint(height*8).astype(np.int16),
                        water=project(water,0).astype(np.int16),meta=json.dumps(meta))
    return meta


def route_fields(x,z,routes):
    best=np.full(x.shape,np.inf)
    grade=np.zeros_like(x,dtype=float)
    for route in routes:
        for (ax,ay,az),(bx,by,bz) in zip(route,route[1:]):
            dx,dz=bx-ax,bz-az
            t=np.clip(((x-ax)*dx+(z-az)*dz)/max(1,dx*dx+dz*dz),0,1)
            dist=np.hypot(x-ax-dx*t,z-az-dz*t)
            closer=dist<best
            grade[closer]=ay+t[closer]*(by-ay)
            np.minimum(best,dist,out=best)
    return best,grade


def surface_under_sites(v,protect):
    # The contact elevation is soil, a lane, or native stone under a building.
    # Never use the roof or a tree canopy as the surrounding hillside's height.
    names=[str(s["Name"]).split(":")[-1] for s in v.palette]
    soil=np.array([s in {"grass_block","dirt","coarse_dirt","podzol","dirt_path","farmland","gravel","moss_block","rooted_dirt"} for s in names])
    old=np.zeros(protect.shape,dtype=np.int16)
    for y in range(55,214):
        m=protect & soil[v.blocks[y]]
        old[m]=y
    support=protect&(old>0)
    neighbors=nd.distance_transform_edt(~support,return_distances=False,return_indices=True)
    return old[tuple(neighbors)].astype(float)


def natural_lakes(h,land,protected,paths,original_water,vertical_scale,base):
    """Inundate only complete connected basins, without cutting across roads or scenery."""
    levels=np.full(h.shape,-1,np.int16)
    forbidden=protected|(paths<3)
    # First retain suitable water bodies from the source generator itself.
    for y in np.unique(original_water[original_water>0]):
        plane=round(float(y)*vertical_scale+base)
        labels,count=nd.label((original_water==y)&land&(h<plane))
        for i,sl in enumerate(nd.find_objects(labels),1):
            if sl is None: continue
            patch=labels[sl]==i
            if patch.sum()<20 or np.any(forbidden[sl]&patch):continue
            levels[sl][patch]=plane
    # A rock basin, sometimes open onto the cliff, carries the foreground water.
    # Score contiguous lowland contours on the *final* heightfield. Their shoreline
    # and any natural spillway therefore agree with the transplanted relief.
    choices=[]
    lip=land & ~nd.binary_erosion(land)
    for y in range(int(np.percentile(h[land],14)),int(np.percentile(h[land],66)),3):
        labels,_=nd.label(land & (h<y))
        for i,sl in enumerate(nd.find_objects(labels),1):
            if sl is None:continue
            patch=labels[sl]==i
            area=patch.sum()
            if not 260<area<4400 or np.any(patch&forbidden[sl]):continue
            depth=y-h[sl][patch]
            if np.max(depth)>21 or np.mean(depth)<1.8:continue
            outlets=np.sum(lip[sl]&patch)
            if outlets>6:continue
            score=np.log(area)*3+np.percentile(depth,80)*.25-outlets*.2
            choices.append((score,y,sl,patch))
    for _,level,sl,patch in sorted(choices,key=lambda a:-a[0]):
        if np.any(levels[sl][patch]>=0):continue
        levels[sl][patch]=level
        if np.sum(levels>=0)>2000:break
    return levels


def rock_beds(v,x,z,relief):
    """Lithological contacts continue through the block sides and the island underside.

    The tilted limestone shelf, schist ridge and southern granite intrusion have
    different strikes. Only a few of their contacts reach the surface on any slope.
    """
    names=("eroded_gneiss","gray_limestone","tan_limestone","dark_limestone",
           "blue_schist","green_schist","shale","brown_granite","gneiss","wet_slate")
    eroded,light,tan,dark,blue,green,shale,granite,gneiss,slate=[v.id("conquest:"+n) for n in names]
    limestone=v.id("create:limestone")
    cool=v.id("create:asurine")
    ochre=v.id("create:ochrum")
    chalk=v.id("conquest:layered_chalk")
    rose=v.id("conquest:faded_dark_red_sandstone")
    iron=v.id("minecraft:dripstone_block")
    sweep=np.sin(x*.018+z*.021)*13+np.cos(z*.031-x*.008)*5
    bedding=.14*(x-130)-.12*(z-170)+sweep+.32*relief
    east=x>300+34*np.sin(z*.017)+.22*(z-170)
    south=(z>277+.18*(x-310)+17*np.cos(x*.027))&(x>180+15*np.sin(z*.043))
    seam=.23*(z-360)-.12*(x-300)+sweep*.68
    fold=np.sin(x*.041-z*.032)+.52*np.sin(z*.065+x*.022)
    pale_lens=np.cos(x*.021+z*.026)+.67*np.sin(z*.036-x*.012)
    western_beds=crags.beds(v,x,z)

    def at(y):
        depth=y-bedding
        limestone_face=np.where(depth<92,eroded,np.where(depth<133,tan,light))
        limestone_face=np.where((depth>111)&(depth<121+3*fold)&(pale_lens>-.32),dark,limestone_face)
        limestone_face=np.where((depth>146)&(depth<165+7*fold)&(pale_lens>-.12),limestone,limestone_face)
        limestone_face=np.where((depth>156+6*fold)&(depth<186+7*fold)&(pale_lens<-.48),cool,limestone_face)
        limestone_face=np.where((depth>179+6*fold)&(pale_lens>.5),chalk,limestone_face)
        schist_face=np.where(y<110+seam,green,np.where(fold<.50,cool,blue))
        schist_face=np.where((y>133+.35*bedding)&(y<150+.27*bedding+6*fold),gneiss,schist_face)
        schist_face=np.where((fold>.75)&(y<142+seam),shale,schist_face)
        southern=np.where(y>145+seam,light,np.where(y<97+seam,eroded,np.where(fold<.35,rose,granite)))
        southern=np.where((y>110+seam)&(y<123+seam+3*fold),tan,southern)
        southern=np.where((y>123+seam)&(y<132+seam)&(pale_lens>.2),np.where(fold>.5,iron,ochre),southern)
        out=np.where(south,southern,np.where(east,schist_face,limestone_face))
        return western_beds(np.where(y<54+seam*.3,slate,out),y)
    return at


def write_terrain(v,jar):
    with np.load(SURVEY,allow_pickle=False) as sample:
        raw=sample["height8"].astype(float)/8
        source_water=sample["water"]
        metadata=json.loads(str(sample["meta"]))
    z,x=np.mgrid[Z0:Z0+DEPTH,X0:X0+WIDTH]
    protect=sites(v,x,z)
    roots,root_states=capture_roots(v,x,z)
    raw=nd.gaussian_filter(raw,.72)
    normlo,normhi=metadata.get("reference_range") or np.percentile(raw[outline()],[4,99.7])
    scale=min(2.45,153/max(1.,normhi-normlo))
    base=62.-scale*normlo
    relief=western.divide(x,z,raw*scale+base,raw)
    longwave=nd.gaussian_filter(raw,8)-nd.gaussian_filter(raw,31)
    body=outline()
    signed=nd.distance_transform_edt(body)-nd.distance_transform_edt(~body)
    land=(signed+np.clip(longwave*.95,-12,12)>0)|protect
    coast=nd.distance_transform_edt(land)
    dist=nd.distance_transform_edt(~protect)
    contact=surface_under_sites(v,protect)
    # Grade the original destination, its functional gate and the tutorial path
    # using their surveyed ground levels. The summit's southern viewing axis stays open.
    contact_weight=smoothstep(dist/40)
    crest=relief*(.8+.2*smoothstep(coast/26))
    h=contact+(crest-contact)*contact_weight
    gate_dist=np.maximum.reduce([np.abs(x-158)-18,np.abs(z-448)-18,np.zeros(x.shape)])
    h=88+(h-88)*smoothstep(gate_dist/29)
    path,path_y=route_fields(x,z,trail_constraints())
    shoulder=smoothstep((path-1.55)/19)
    h=np.where(protect,h,path_y+(h-path_y)*shoulder)
    h,arch_exclude=arch.grade(x,z,h,raw,protect,land)
    h,summit_exclude=summit.grade(x,z,h,raw,protect,land)
    h,meadow,falls,fall_water,bank_path=meadow_falls.grade(x,z,h,protect,land)
    unreserved = land & ~protect & ~meadow & ~falls & ~summit_exclude
    h=crags.fracture(x,z,h,unreserved & ~arch_exclude & (path>4) & (gate_dist>5))
    h=np.clip(np.rint(h),18,298).astype(np.int16)
    protected_dist=dist.copy()
    wet_level=natural_lakes(h,land,protect|(gate_dist<3)|summit_exclude|arch_exclude|falls|meadow,path,source_water,scale,base)
    wet_level[falls]=fall_water[falls]
    wet=wet_level>h
    gy,gx=np.gradient(nd.gaussian_filter(h.astype(float),1.55))
    slope=np.hypot(gy,gx)
    n1=(np.sin(.083*x+.05*z)+.6*np.sin(.14*z-.028*x)+.28*np.cos(.20*x-.13*z))
    n2=np.cos(.043*x+.064*z)+.55*np.sin(.081*x-.079*z)
    # Concave gullies collect snow and the windward ribs remain visible even
    # high on the massif. A moderate bedrock incline can also carry soil ledges.
    hollow=nd.gaussian_filter(h.astype(float),6)-h
    northness=gy/np.maximum(slope,.1)
    snow=(h+6*northness+2.6*hollow+n2*3>166)&(slope<1.27+.28*n1+.30*northness)
    cliff=slope>.91+n1*.14
    scree=(slope>.51+n1*.10)&(h>94+n2*6)
    def block(name): return v.id("minecraft:"+name)
    gravel=block("gravel")
    grass,dirt,moss,podzol,snowblock,clay=[block(s) for s in ("grass_block[snowy=false]","dirt","moss_block","podzol[snowy=false]","snow_block","clay")]
    top=np.full(h.shape,grass,np.uint16)
    beds=rock_beds(v,x,z,longwave)
    rock=beds(h)
    top[(h<118)&(n2>.93)&(n1<-.45)]=moss
    top[(h<127)&(n2<-.75)&(n1>.57)]=podzol
    top[scree|cliff]=rock[scree|cliff]
    talus=scree&~cliff&(hollow>0.35)&(nd.maximum_filter(slope,size=15)>1.12)
    loose=np.where(n2>.3,v.id("conquest:dark_gray_limestone_gravel"),gravel)
    top[talus]=loose[talus]
    ledge=scree&~cliff&~talus&(slope<.84)&(h<149+5*n2)&(n1+northness*.8>.10)
    top[ledge]=np.where(northness[ledge]>.38,moss,grass)
    top[snow]=snowblock
    heath=crags.heath(x,z,h,slope,northness,hollow,unreserved & ~arch_exclude & ~wet)
    top[heath]=np.where(n1[heath]+.4*n2[heath]>.05,moss,grass)
    shore=nd.distance_transform_edt(~wet)
    damp=(shore<4.5+.9*n1)&~wet&(slope<.94)
    top[damp]=np.where(n1[damp]>.18,v.id("conquest:moss_covered_limestone"),v.id("conquest:wet_slate"))
    top[wet]=np.where(n2[wet]>.35,clay,v.id("conquest:dark_gray_limestone_gravel"))
    walking=(path<1.45)&(protected_dist>0)&(gate_dist>0)
    top[walking]=np.where(n1[walking]>.8,gravel,block("dirt_path"))
    top[meadow]=grass
    top[(bank_path<1.35)&(~protect)&(~wet)]=np.where(n1[(bank_path<1.35)&(~protect)&(~wet)]>.55,gravel,block("dirt_path"))
    # The island underside thins out at the sea promontories. The folded beds
    # are continuous on adjoining faces, including the exposed walls below snow.
    mantle=22+4.5*np.sqrt(coast)+.29*np.maximum(h-75,0)
    floor=np.maximum(3,np.rint(h-mantle-10*n2-4*n1)).astype(int)
    # Merge the new rock into the actual undersides of the retained core. This
    # prevents the foundations of individual houses becoming rectangular piers.
    old_floor=np.full(h.shape,-1,np.int16)
    for y in range(150):
        hit=protect&(old_floor<0)&(v.blocks[y]!=0)
        old_floor[hit]=y
    near=nd.distance_transform_edt(old_floor<0,return_distances=False,return_indices=True)
    ledge=smoothstep(dist/24)
    floor=np.minimum(h-4,np.rint(old_floor[tuple(near)]*(1-ledge)+floor*ledge)).astype(int)
    edit=~protect
    active=land&edit
    qz,qx=np.where(active)
    H,F=h[qz,qx],floor[qz,qx]
    shallow=(np.isin(top,[grass,moss,podzol,block("dirt_path")]))[qz,qx]
    skin=top[qz,qx]
    substrate=rock_beds(v,x[qz,qx],z[qz,qx],longwave[qz,qx])
    levels=wet_level[qz,qx]
    water_id=block("water[level=0]")
    for y in range(max(219,int(H.max()+2),int(levels.max()+2))):
        a=v.blocks[y]
        a[edit]=0
        d=H-y
        mineral=substrate(y)
        mineral[(d<3)&(d>0)&shallow]=dirt
        mineral[d==0]=skin[d==0]
        mineral[(y<F)|(d<0)]=0
        mineral[(d<0)&(y<=levels)]=water_id
        a[qz,qx]=mineral
    v.blocks[roots]=root_states
    decoration=active & ~meadow & ~falls & ~summit_exclude & ~arch_exclude
    dress(v,x,z,h,decoration&(dist>4)&(path>3)&(gate_dist>4)&(coast>3),wet,slope,northness,rock)
    plantable=decoration&(dist>3)&(path>2)&(gate_dist>4)&(coast>3)&(~wet)&(~cliff)&(~scree)&(~snow)
    plantable |= heath & (dist>4) & (path>4) & (gate_dist>4) & (coast>3)
    plant(v,x,z,h,plantable,wet,jar)
    metadata["coastal_recess_blocks"]=western.shelf_notches(v,x,z,h,land,arch_exclude)
    metadata["western_arch"]=arch.carve(v,x,z,h)
    metadata["western_arch"]["heath"]=crags.gate_groves(v,x,z,h,slope,~unreserved | wet | (dist<=4) | (path<=4),jar)
    arch.validate(v,x,z)
    summit.finish(v,x,z,h)
    metadata["waterfall"]=meadow_falls.finish(v,x,z,h)
    metadata["meadow"]={"center_world":[64,177,88],"open_columns":int(meadow.sum())}
    from skyhaven_meadow_fringe import plant_edge
    metadata["meadow"]["fringe_plants"] = plant_edge(v,x,z,h,meadow,path,protect)
    # RTF supplies the landform. Mark a few useful vantage points for QA.
    high=np.argmax(np.where(active,h,-999))
    iz,ix=np.unravel_index(high,h.shape)
    print(f"RTF relief: {active.sum()} rebuilt columns, peak {(int(x[iz,ix]),int(h[iz,ix]),int(z[iz,ix]))}, {wet.sum()} wet columns.",flush=True)
    return land,metadata
