"""Build small, grounded ridge outcrops, talus fans and snow tongues on the RTF relief."""
import numpy as np
from scipy import ndimage as nd


def _centres(rng, suitable, radius):
    noise = rng.random(suitable.shape)
    noise[~suitable] = -1
    centres = np.argwhere(suitable & (noise == nd.maximum_filter(noise, 2*radius+1)))
    rng.shuffle(centres)
    return centres


def dress(v, x, z, h, eligible, wet, slope, northness, rocktype):
    """Add height to real ground. All ranges and gradients are in block coordinates [z,x]."""
    rng = np.random.default_rng(718310)
    height = np.asarray(h, dtype=np.int32)
    row, col = np.indices(h.shape)
    available = eligible & ~wet & (height > 0) & (height < v.blocks.shape[0]-9)
    safe_h = height.clip(0, v.blocks.shape[0]-2)
    available &= (v.blocks[safe_h, row, col] != 0) & (v.blocks[safe_h+1, row, col] == 0)
    # Keep any existing beam, canopy or root crossing above the ground intact.
    for clearance in range(2, 8):
        available &= v.blocks[(safe_h+clearance).clip(0, v.blocks.shape[0]-1), row, col] == 0
    edge = nd.distance_transform_edt(available)
    smooth = nd.gaussian_filter(height.astype(np.float32), 3)
    grad_z, grad_x = np.gradient(smooth)
    basin = nd.gaussian_filter(smooth, 7) - smooth
    steep_near = nd.maximum_filter(slope, 25)
    stones = np.asarray(rocktype, dtype=np.uint16).copy()
    gravel = v.id("minecraft:gravel")
    solid_snow = v.id("minecraft:snow_block")
    snow_layers = np.array([v.id(f"minecraft:snow[layers={i}]") for i in range(1, 8)], dtype=np.uint16)
    # Store a column profile first, so intersecting lumps share a footing and single-block
    # overlays like grass or a strip of snow never support a free-floating upper block.
    rise = np.zeros(h.shape, dtype=np.uint8)
    cap = np.zeros(h.shape, dtype=np.uint16)
    dust = np.zeros(h.shape, dtype=bool)
    counts = {"outcrop_groups": 0, "talus_fans": 0, "snow_tongues": 0}

    def patch(cx, cz, extent):
        x0, x1 = max(0, int(cx-extent)), min(h.shape[1], int(cx+extent+2))
        z0, z1 = max(0, int(cz-extent)), min(h.shape[0], int(cz+extent+2))
        return np.s_[z0:z1, x0:x1], np.ogrid[z0:z1, x0:x1]

    def lobe(cx, cz, theta, length, width, amplitude, skew, stone):
        sl, (zz, xx) = patch(cx, cz, length+width)
        dx, dz = xx-cx, zz-cz
        along = (dx*np.cos(theta) + dz*np.sin(theta))/length
        cross = (-dx*np.sin(theta) + dz*np.cos(theta))/width + skew * along
        profile = 1 - np.abs(along)**1.65 - np.abs(cross+.13*np.sin(along*3))**1.55
        levels = np.rint(amplitude*np.maximum(0, profile)**.66).astype(np.uint8)
        footprint = levels > 0
        if footprint.sum() < 4 or not np.all(available[sl][footprint]):
            return False
        update = levels > rise[sl]
        rise[sl][update] = levels[update]
        stones[sl][update] = stone
        return True

    outcrop = available & (edge > 4) & (height > 88) & (slope > .64) & (slope < 2.6)
    for iz, ix in _centres(rng, outcrop, 11):
        # Long exposed edges strike across the hillside. Adjacent offset lenses mimic
        # one body breaking through a bed, with a shorter ledge descending beside it.
        descent = np.arctan2(-grad_z[iz,ix], -grad_x[iz,ix])
        theta = descent + np.pi/2 + rng.normal(0,.22)
        length, width = rng.uniform(5,10), rng.uniform(1.9,3.35)
        amp = rng.uniform(2.8,5.9)
        stone = stones[iz,ix]
        if lobe(ix,iz,theta,length,width,amp,rng.uniform(-.31,.31),stone):
            counts["outcrop_groups"] += 1
            r = rng.uniform(2,4)
            lobe(ix+np.cos(descent)*r+np.cos(theta)*length*.22,
                 iz+np.sin(descent)*r+np.sin(theta)*length*.22,
                 theta+rng.normal(0,.14),length*.64,width*.83,amp*.58,-.14,stone)

    toe = available & (edge > 5) & (height > 69) & (slope > .14) & (slope < .94) & (steep_near > 1.1)
    for iz, ix in _centres(rng, toe, 12):
        descent = np.arctan2(-grad_z[iz,ix], -grad_x[iz,ix])
        if basin[iz,ix] < -2.4:
            continue
        count = 0
        for step in sorted(rng.uniform(-2,11,rng.integers(5,11))):
            cross = rng.normal(0,1.2+max(0,step)*.22)
            cx = ix+step*np.cos(descent)-cross*np.sin(descent)
            cz = iz+step*np.sin(descent)+cross*np.cos(descent)
            length = rng.uniform(1.65,3.4)*(1.15-max(0,step)*.029)
            count += lobe(cx,cz,descent+rng.uniform(-1.0,1.0),length,max(1.28,length*.68),
                          1.9+max(0,8-step)*.11,rng.uniform(-.25,.25),stones[iz,ix])
        if count:
            counts["talus_fans"] += 1
            sl, (zz, xx) = patch(ix, iz, 15)
            dx, dz = xx-ix, zz-iz
            along = dx*np.cos(descent)+dz*np.sin(descent)
            across = np.abs(-dx*np.sin(descent)+dz*np.cos(descent))
            margin = (1.6+np.clip(along,0,12)*.24)-across
            scattered = rng.random(margin.shape) < np.clip(.18+margin*.18,0,.76)
            dust[sl] |= available[sl] & (edge[sl]>1.4) & (along>-2) & (along<13) & (margin>0) & scattered

    lee = available & (edge > 3) & (height > 144) & (northness > .12) & (slope > .33) & (slope < 1.85) & (basin > -.75)
    for iz, ix in _centres(rng, lee, 11):
        # At the crest of a high gully the snow is broad, thinning and curving to a
        # point down the fall line. Layer thickness changes across the same patch.
        descent = np.arctan2(-grad_z[iz,ix], -grad_x[iz,ix])
        length = rng.uniform(9,19)
        sl, (zz, xx) = patch(ix,iz,length+5)
        dx, dz = xx-ix, zz-iz
        along = (dx*np.cos(descent)+dz*np.sin(descent))/length
        cross = -dx*np.sin(descent)+dz*np.cos(descent)-rng.uniform(-2.1,2.1)*np.sin(np.clip(along,0,1)*np.pi)
        thickness = np.maximum(0,1-np.abs((along-.30)/.72)**1.6 - (cross/(2.8-1.39*np.clip(along,0,1)))**2)
        keep = (thickness>.16) & (edge[sl]>1.5) & available[sl] & (height[sl]>=height[iz,ix]-22) & (slope[sl]<2.05)
        if np.count_nonzero(keep)<9:
            continue
        counts["snow_tongues"] += 1
        fill = keep & (thickness>.76) & (rise[sl]==0)
        rise[sl][fill] = 1
        stones[sl][fill] = solid_snow
        amount = np.clip(np.rint(thickness*6),1,7).astype(np.int32)
        cap[sl][keep] = snow_layers[amount[keep]-1]

    for dz in range(1,int(rise.max())+1):
        zz, xx = np.where(rise >= dz)
        v.blocks[height[zz,xx]+dz,zz,xx] = stones[zz,xx]
    zz, xx = np.where(dust & (rise==0))
    v.blocks[height[zz,xx],zz,xx] = gravel
    zz, xx = np.where(cap!=0)
    v.blocks[height[zz,xx]+rise[zz,xx]+1,zz,xx] = cap[zz,xx]
    counts["raised_columns"] = int(np.count_nonzero(rise))
    counts["talus_gravel"] = int(dust.sum())
    counts["snow_columns"] = len(xx)
    print(f"Alpine rock faces and lee slopes: {counts}",flush=True)
    return counts
