"""Authored buildings and walkable grades for the natural Skyhaven terrain bake.

Coordinates are island-local. Trail heights are the supporting block, one
below a standing player's feet. Call sites and capture_roots on the original
template after applying peak_v2, before changing its surface.
"""
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import binary_closing, binary_dilation, binary_fill_holes, distance_transform_edt, label


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/tnc"


def _disk(radius):
    z, x = np.ogrid[-radius:radius + 1, -radius:radius + 1]
    return x * x + z * z <= radius * radius


def _architecture(name):
    # Bare stone, grass, crops, leaves, logs and gravel occur across the old
    # circular island. None of those alone identifies an authored building.
    return (not name.startswith("minecraft:") or
            any(part in name for part in ("planks", "bricks", "stairs", "slab",
                "glass", "copper", "terracotta", "wool", "concrete", "quartz",
                "lantern", "bookshelf", "door", "barrel", "chest", "carpet",
                "furnace", "smooth_stone", "cobblestone_wall", "crafting_table")))


def sites(v, x, z):
    """Return protected columns, aligned to v.blocks[Y,Z,X] and the local grids."""
    x, z = np.broadcast_arrays(x, z)
    if x.shape != v.blocks.shape[1:]:
        raise ValueError("Skyhaven site mask and terrain dimensions differ")

    def bounds(left, north, right, south):
        return (x >= left) & (x <= right) & (z >= north) & (z <= south)

    buildings = json.loads((ROOT / "docs/art/rouchnao-building-index.json").read_text("utf8"))
    # Use the surveyed centres AND working entrances for irregular, multi-door
    # buildings. Bare ground around a pin is still fully editable.
    centers = np.zeros(x.shape, bool)
    x0, z0 = int(x[0, 0]), int(z[0, 0])
    for building in buildings:
        if int(building["number"]) == 41:
            continue  # The east tavern has its own upgrade volume below.
        px, _, pz = building["entry"]
        centers[pz-z0, px-x0] = True
        centers[round(building["z"])-z0, round(building["x"])-x0] = True
    near_door = distance_transform_edt(~centers)
    town = bounds(120, 94, 410, 405) & (near_door <= 34)
    tavern = bounds(408, 263, 493, 334)
    casino = bounds(58, 225, 130, 314)
    window = town | tavern | casino
    iz, ix = np.nonzero(window)
    sub = np.s_[iz.min():iz.max()+1, ix.min():ix.max()+1]
    built = np.zeros(x.shape, bool)
    palette = np.array([_architecture(str(t["Name"])) for t in v.palette], bool)
    for plane in v.blocks[80:200]:
        built[sub] |= palette[plane[sub]]

    # The old detached garden wall is intentionally outside the civic mask.
    # Cut only its narrow traced footprint; the townhouses just south survive.
    wall = [(294,111),(310,107),(329,105),(347,99),(360,101),(378,103),(398,115),(400,126)]
    curtain = np.zeros(x.shape,bool)
    for (ax,az),(bx,bz) in zip(wall,wall[1:]):
        dx,dz=bx-ax,bz-az
        t=np.clip(((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz),0,1)
        curtain |= (x-ax-t*dx)**2+(z-az-t*dz)**2 < 7**2
    footprint = built & window & ~curtain
    # Bridge minor roof slots and rooms, keep the space between independent
    # buildings open for regrading. The two-block shoulder supports doorsteps.
    footprint = binary_fill_holes(binary_closing(footprint, structure=_disk(2)))
    # A lone street lamp on an open slope must not lock a little island of old
    # landscaping into the new hills. Attached porches and stairs stay connected.
    groups, _ = label(footprint, structure=np.ones((3,3),bool))
    sizes = np.bincount(groups.ravel())
    footprint &= sizes[groups] >= 10
    keep = binary_dilation(footprint, structure=_disk(2)) & ~curtain
    # Large attached floors, the upgrade compare-and-set volumes and licensed
    # home airspace need exact block states even where there is no roof above.
    keep |= bounds(63,231,125,308)           # casino rebuild and facade
    keep |= bounds(415,269,487,328)          # tavern furniture, film and seat
    keep |= bounds(436,206,496,266)          # upstream east arena preflight
    keep |= bounds(463,266,469,273)          # five scaling steps + landing
    for p in json.loads((DATA/"housing/catalog.json").read_text("utf8"))["plots"]:
        if p["kind"] == "HOME":
            a, _, b = p["min"]; c, _, d = p["max"]
            keep |= bounds(a-1,b-1,c+1,d+1)
    # Tutorial components use exact relative offsets in AcademyStations plus
    # the NPC standing tiles. This leaves surrounding fields and workshops free.
    for left,north,right,south in [
            (77,174,99,182), (85,183,91,193),   # three runes and the guide
            (88,308,99,314), (91,314,95,321),   # working farm and mentor
            (413,363,424,369), (418,369,422,371)]:   # circuit and mentor
        keep |= bounds(left,north,right,south)
    return keep


def capture_roots(v, x, z):
    """Return (indices, states) for the original ten hanging roots, including dirt.

    Restore with v.blocks[indices] = states after sculpting. Unlike protecting
    columns, this leaves the terrain above every root free for the new relief.
    """
    from generate_sky_island_structures import build_root_blocks
    groups, _ = build_root_blocks()
    px, py, pz = np.array([p for group in groups.values() for p in group],np.int32).T
    index = py, pz-int(np.min(z)), px-int(np.min(x))
    states = v.blocks[index]
    # Authored buildings/landscape overlays can already have replaced portions
    # of buried roots. Restore only original root tissue still in the template.
    keep_names = {"minecraft:dark_oak_log","minecraft:amethyst_block","minecraft:budding_amethyst",
                  "minecraft:rooted_dirt","minecraft:sea_lantern"}
    original = np.array([str(p["Name"]) in keep_names for p in v.palette])[states]
    return tuple(a[original] for a in index), states[original].copy()


def trail_constraints():
    """Natural approaches only: modest curves between the unchanged doors.

    Branches share exact junctions. The high route joins the observatory's
    switchbacks at X323/Z76 on the eastern shoulder.
    """
    return [
        # North lip of the relocated departure court through the old south street.
        [(158,88,432),(159,88,426),(171,87,425),(183,88,421),(191,89,415),
         (201,89,411),(207,89,403),(205,90,392),(201,90,383),(205,90,376),(215,89,365)],
        # West farm, casino access and the original west-town street.
        [(93,91,320),(100,92,319),(110,93,321),(119,91,323),(130,91,324),
         (140,92,325),(151,97,325),(161,98,328),(172,97,325),(182,98,322)],
        [(119,91,323),(126,91,312),(132,93,301),(131,97,289),(125,98,283)],
        # Wind guide to west ridge and central buildings.
        [(88,106,190),(90,106,201),(96,106,211),(106,108,217),(117,104,219),
         (126,104,217),(134,108,218),(144,108,219),(153,108,216)],
        # Eastern town lane to the tavern, existing arena, and working circuit.
        [(382,88,268),(393,86,271),(401,85,279),(407,86,288),(414,89,292),(419,89,291)],
        [(419,89,291),(427,89,284),(438,90,276),(450,90,274),(458,90,271),(466,90,272)],
        [(407,86,288),(404,86,302),(405,85,316),(409,86,332),(410,89,344),
         (413,91,356),(416,92,364),(420,92,369)],
        # Outer town road and the old academy's summit trailhead.
        [(393,86,271),(402,86,259),(410,86,249),(417,88,232),(420,92,216),
         (423,94,203),(425,94,188)],
        [(425,94,188),(433,95,176),(440,99,158),(436,103,145),(427,107,130),
         # Follow the inside rim of the northern inlet. This narrow natural
         # saddle reaches Y127; a straight Y107 crossing here would need a bridge.
         (419,109,116),(409,111,111),(399,117,111),(389,123,110),(384,126,110),
         (381,127,109),(377,127,108),(373,126,107),(370,125,106),(367,124,104),
         (365,124,100),(361,125,94),(355,127,87),(345,132,81),(335,137,83),(323,142,76)],
    ]
