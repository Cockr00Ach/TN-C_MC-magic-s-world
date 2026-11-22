"""Bedrock, fractured ribs and pockets of mountain heath on the western divide."""
import numpy as np
from scipy import ndimage as nd

from skyhaven_alpine_growth import tree_library


def ramp(value):
    v = np.clip(value, 0, 1)
    return v*v*(3-2*v)


def beds(v, x, z):
    """The west gate exposes a dark schist basement, tilted quartzite and iron lenses.

    Index only this outcrop while filling the vertical volume. Contacts cross the
    actual ridge interior, so the tunnel walls show the same beds as its shoulders.
    """
    wx, wz = x-260, z-260
    contact = -201+21*np.sin(wz/58)+9*np.cos(wz/24)
    mask = wx < contact
    a, b = wx[mask]+334, wz[mask]+102
    fold = 9*np.sin((b+.27*a)/27)+5*np.sin(a/18-b/43)
    lens = np.sin(a/26+b/33) + .58*np.sin(b/12-a/21)
    intrusion = .65*np.cos(a/35-b/26)+.45*np.sin(a/11+b/59)
    datum = 154 + .18*a-.34*b+fold
    cross = .32*a+.63*b+8*np.sin((a-b)/28)
    ids = {n:v.id("conquest:"+n) for n in ("blue_schist","green_schist","gneiss",
            "gray_quartzite","light_gray_granite","blue_schist_cobble","gray_limestone",
            "brown_granite","dark_brown_granite","slate")}
    asurine,veridium = v.id("create:asurine"),v.id("create:veridium")

    def apply(rock, y):
        elevation = y if np.ndim(y)==0 else y[mask]
        stratum = elevation-datum
        stone = np.where(stratum < -49+6*lens, ids["slate"], ids["gneiss"])
        stone = np.where(stratum > -18+5*lens, ids["green_schist"], stone)
        stone = np.where((stratum > -14+3*lens)&(intrusion<-.15),veridium,stone)
        stone = np.where(stratum > 3+4*intrusion, ids["blue_schist"], stone)
        stone = np.where((stratum>14+7*lens)&(stratum<78+15*lens)&(intrusion<.19),asurine,stone)
        pale = (stratum>25+6*lens) & (stratum<49+11*lens) & (lens>-.72)
        stone = np.where(pale, np.where(intrusion>.22,ids["gray_quartzite"],ids["light_gray_granite"]),stone)
        stone = np.where((stratum>57+6*lens)&(stratum<72+9*lens),ids["gray_limestone"],stone)
        warm = np.abs(cross-.33*(elevation-147)) < np.maximum(0,8+11*intrusion)
        stone = np.where(warm, np.where(lens>-.1,ids["brown_granite"],ids["dark_brown_granite"]),stone)
        # Fault breccia breaks up where the narrower intrusive branch pinches out.
        joint = np.abs(cross+.49*(elevation-170)+17*np.sin(b/27))
        stone = np.where((joint<2.6)&(lens>.1),ids["blue_schist_cobble"],stone)
        rock[mask] = stone
        return rock
    return apply


def fracture(x,z,h,allowed):
    """Wear open narrow clefts and offset the heads of rock slabs on steeper faces."""
    wx, wz = x-260, z-260
    rise = np.hypot(*np.gradient(nd.gaussian_filter(h,1.2)))
    grain = wx*.58+wz*.86+8*np.sin(wz/23)+5*np.sin(wx/33+wz/41)
    cross = wx*.95-wz*.26+4*np.cos(wz/22)
    depth = 3+4*ramp((h-100)/62)
    first = np.abs(np.sin(grain*.055))
    second = np.abs(np.sin((cross+.1*h)*.083))
    cleft = np.maximum(ramp((.40-first)/.40), .6*ramp((.28-second)/.28))
    ribs = np.sin(grain*.145) + .65*np.sin(cross*.107-wz*.037)
    weight = ramp((rise-.42)/.90)*ramp((-wx-102)/70)*ramp((h-78)/35)
    # A two-block buffer next to the road/archbed keeps the old grade under foot.
    fade = ramp(nd.distance_transform_edt(allowed)/5)
    return h+weight*fade*(1.7*ribs-cleft*depth)


def heath(x,z,h,slope,northness,hollow,available):
    wx, wz=x-260,z-260
    rough=np.sin(wx*.058+wz*.032)+.72*np.cos(wz*.072-wx*.027)+.38*np.sin(wz*.125+wx*.093)
    # Pronounced green shelves below the timberline; higher on the divide
    # their shorter north-facing pockets slip between the exposed crags.
    shelter=rough+.7*northness+.4*np.clip(hollow,-2,3)
    margin = 174+14*rough+6*northness-h
    return (available & (wx< -105) & (slope<.8+.24*ramp(shelter)) & (margin>0)
            & (shelter>.13+(h-115)*.012))


def gate_groves(v,x,z,h,slope,protected,jar):
    """Sheltered groves frame both canyon mouths and give the span a human scale."""
    wx,wz=x-260,z-260
    dx,dz=wx+334,wz+102
    along=.97815*dx+.20791*dz
    side=-.20791*dx+.97815*dz
    # Keep the clear walk and the line of sight down the canyon. On the
    # windward saddle there are only small bent pines, mostly on one flank.
    stands=np.zeros(x.shape,float)
    for cx,cz,l,w in ((53,-20,25,11),(87,19,26,14),(67,48,20,9),
                      (-61,23,27,13),(-85,-28,14,11),(-55,-48,22,9)):
        stands=np.maximum(stands,1-((along-cx)/l)**2-((side-cz)/w)**2)
    margin=stands+.14*np.sin(wz*.23)+.13*np.cos(wx*.31+wz*.13)
    pocket=(stands>0)&(margin>.11)&(np.abs(side)>12)&(slope<1.04)&~protected
    iz,ix=np.where(pocket)
    # The carving pass may have exposed a new floor below the input heightfield.
    # Only colonize actual exposed ground. Avoid overhangs and any existing foliage.
    hg=h[iz,ix]
    ground=v.blocks[hg,iz,ix]
    skip=np.array([str(st["Name"]).startswith("minecraft:") and str(st["Name"]).split(":")[1]
                   not in {"gravel","andesite","tuff","dirt","grass_block","podzol","moss_block","coarse_dirt"}
                   for st in v.palette])
    clear=(ground!=0)&~skip[ground]&(v.blocks[hg+1,iz,ix]==0)
    iz,ix,hg=iz[clear],ix[clear],hg[clear]
    carpet=margin[iz,ix]+.4*np.cos(wx[iz,ix]*.13+wz[iz,ix]*.17)
    moss=v.id("minecraft:moss_block")
    grass=v.id("minecraft:grass_block[snowy=false]")
    heath=v.id("conquest:lichen_blue_schist")
    v.blocks[hg,iz,ix]=np.where(carpet>.76,moss,np.where(carpet>.30,grass,heath))
    habitat=np.zeros_like(pocket)
    habitat[iz,ix]=carpet>.32
    trees=tree_library(v,jar)
    rng=np.random.default_rng(537619)
    positions=np.argwhere(habitat & (rng.random(x.shape)<.12))
    rng.shuffle(positions)
    planted=[]
    edge=nd.distance_transform_edt(~protected & (np.abs(side)>11))
    for zz,xx in positions:
        if any((zz-z0)**2+(xx-x0)**2 < radius**2 for z0,x0,radius in planted):
            continue
        group=rng.choice(["spruce","pine","bush"],p=[.34,.24,.42])
        options=trees[group]
        tree=options[rng.integers(len(options))]
        if edge[zz,xx]<=tree.radius:continue
        (ax,ay,az),states=tree.variants[rng.integers(4)]
        tx,ty,tz=xx+ax,h[zz,xx]+1+ay,zz+az
        if np.any(v.blocks[ty,tz,tx]!=0):continue
        v.blocks[ty,tz,tx]=states
        planted.append((zz,xx,tree.radius+4))
    above=hg+1
    ferns=(v.blocks[above,iz,ix]==0)&(carpet>.36)&(rng.random(len(hg))<.17)
    v.blocks[above[ferns],iz[ferns],ix[ferns]]=v.id("minecraft:fern")
    return {"sheltered_ground":len(hg),"native_trees_and_shrubs":len(planted),"ferns":int(ferns.sum())}
