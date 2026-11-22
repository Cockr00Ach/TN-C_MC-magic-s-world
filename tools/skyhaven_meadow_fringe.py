"""A wind-swept, walkable fringe around the south town's building meadow."""
import numpy as np
from scipy.ndimage import distance_transform_edt


def plant_edge(v, x, z, surface, meadow, path, protect):
    # A player can lay a foundation anywhere in the whole 60 x 77 core.
    # Keep a little apron beyond it, then ease into knee-high flowers and grass.
    iz, ix = np.where(meadow & ~protect & (path > 3))
    wx, wz = x[iz, ix] - 260, z[iz, ix] - 260
    apron = np.hypot(np.maximum.reduce([26-wx, wx-85, np.zeros_like(wx)]),
                      np.maximum.reduce([50-wz, wz-126, np.zeros_like(wz)]))
    y = surface[iz, ix]
    soil = v.id("minecraft:grass_block[snowy=false]")
    available = (v.blocks[y, iz, ix] == soil) & (v.blocks[y+1, iz, ix] == 0)
    pockets = (.53*np.sin(.10*wx+.061*wz+1.8*np.sin(.047*wz))
               + .30*np.cos(.061*wx-.15*wz) + .17*np.sin(.33*wx+.19*wz))
    apron = np.clip((apron-1.5)/10., 0, 1)
    outer = np.clip(distance_transform_edt(~protect)[iz, ix]/5, 0, 1)
    rng = np.random.default_rng(312617)
    planted = available & (rng.random(wx.shape) < apron*outer*(.18+.52*np.maximum(0, pockets)))
    blooms = (pockets>.12) & (rng.random(wx.shape)<.36)
    # Each group follows a swale along the meadow margin; leave gaps between groups.
    colors = np.where(np.sin(wz*.053+.8*np.sin(wx*.08)) > .28,
                      v.id("minecraft:cornflower"), v.id("minecraft:oxeye_daisy"))
    sprigs = np.where(blooms, colors, v.id("minecraft:grass"))
    v.blocks[y[planted]+1, iz[planted], ix[planted]] = sprigs[planted]
    return int(planted.sum())
