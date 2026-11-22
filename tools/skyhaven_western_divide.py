"""A single folded alpine divide and coastal benches on the larger RTF survey."""
import numpy as np
from scipy import ndimage as nd
from scipy.interpolate import CubicSpline, PchipInterpolator
from scipy.spatial import cKDTree

# World coordinates and elevation, moving along the spine from the old snowcap
# through the arch's two pillars to the low shoulders over the southwest lake.
SPINE = np.array([
    (-139,-207,312),(-171,-220,301),(-201,-246,322),(-242,-256,338),
    (-279,-246,313),(-311,-217,324),(-322,-180,302),(-329,-145,309),
    (-334,-102,318),(-342,-61,296),(-357,-25,308),(-370,8,289),
    (-377,46,302),(-365,83,285),(-339,113,268),(-308,138,285),
    (-275,155,255),(-241,165,224),(-202,155,185)
], dtype=float)


def ease(t):
    t=np.clip(t,0,1)
    return t*t*(3-2*t)


def divide(x,z,relief,raw):
    wx,wz=x-260,z-260
    smooth=nd.gaussian_filter(raw,11)
    # Shave short isolated horns far from the primary divide. Retain the
    # original grade near the occupied city, the mountain spring and town lake.
    lowpass=nd.gaussian_filter(relief,11)
    quiet=ease((wx-75)/105)+ease((wz-145)/95)
    quiet=np.clip(quiet,0,.84)
    relief=relief+(lowpass-relief)*quiet - np.maximum(0,lowpass-139)*quiet*.35
    length=np.r_[0,np.cumsum(np.hypot(*np.diff(SPINE[:,:2],axis=0).T))]
    t=np.arange(0,length[-1],.82)
    q=CubicSpline(length,SPINE[:,:2],axis=0)(t)
    distance,index=cKDTree(q).query(np.stack([wx,wz],axis=-1))
    s=t[index]
    height=PchipInterpolator(length,SPINE[:,2]-90)(s)
    uplift_mask=1-ease((distance-35)/112)
    shoulder=74+.14*(smooth-92)+5*np.sin((wx+wz)*.026)
    # The broad crest is cut by the surveyed incisions. These displace both
    # its ridge line and gullies without separating it into incidental spikes.
    width=47+7*np.sin(s*.018)
    ribs=(raw-nd.gaussian_filter(raw,6))*.64 + (nd.gaussian_filter(raw,4)-smooth)*.58
    flank=shoulder+(height-shoulder)*np.exp(-(distance/width)**1.54)
    flank+=ribs+3*np.sin(s*.056-distance*.079)*ease(distance/18)
    # Softer foothills/valleys extend to the city side of the divide; grass
    # and mixed woodland fill the foreground when looking towards the arch.
    valley=np.hypot((wx+229)/62,(wz+41)/132)
    old_ground=relief+(shoulder-6+0.25*(raw-smooth)-relief)*(1-ease((valley-.37)/.86))
    target=old_ground+(flank-old_ground)*uplift_mask
    keep_lake=ease((np.hypot((wx+178)/61,(wz-140)/49)-.78)/.43)
    keep_falls=ease((np.hypot((wx+165)/40,(wz+120)/48)-.84)/.45)
    return relief+(target-relief)*keep_lake*keep_falls


def shelf_notches(v,x,z,h,land,arch_exclude):
    wx,wz=x-260,z-260
    coast=nd.distance_transform_edt(land)
    # Cut back two exposed strata under the outer headlands. Stone remains
    # continuous behind each recess and carries the cap. Ribs at either end
    # let the view alternate between hanging ledge and open sea.
    exposure=(1-ease((wx+365)/32)) * ease((wz+325)/47)
    pockets=(.5+.5*np.cos(.039*wz+.013*wx)) * exposure
    eligible=land & ~arch_exclude & (coast<37) & (pockets>.15)
    iz,ix=np.where(eligible)
    if not len(iz): return 0
    p=pockets[iz,ix]; d=coast[iz,ix]
    cut=0
    for offset in range(10,51):
        first=np.maximum(0,1-((offset-21)/12)**2)*p*27
        second=np.maximum(0,1-((offset-40)/9)**2)*p*19
        mask=d < np.maximum(first,second)
        y=h[iz[mask],ix[mask]].astype(int)-offset
        keep=y>4
        coords=(y[keep],iz[mask][keep],ix[mask][keep])
        cut+=int(np.count_nonzero(v.blocks[coords]))
        v.blocks[coords]=0
    return cut
