"""Relocate the original authored gate while preserving its connected block states."""
from pathlib import Path
import nbtlib as nbt
from skyhaven_sculpt import X0,Z0

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "src/main/resources/data/tnc/structures/sky_island/portal/ritual_gate.nbt"


def capture(v):
    template = nbt.load(GATE)
    palette = []
    for state in template["palette"]:
        name = str(state["Name"])
        props = state.get("Properties", {})
        if props:
            name += "[" + ",".join(f"{k}={val}" for k, val in sorted(props.items())) + "]"
        palette.append(v.id(name))
    result = []
    for block in template["blocks"]:
        x,y,z = map(int, block["pos"])
        expected = palette[int(block["state"])]
        original = int(v.blocks[89+y, 407+z-Z0, 192+x-X0])
        # Connection properties on glass/walls change after world placement.
        belongs = str(v.palette[original]["Name"]) == str(v.palette[expected]["Name"])
        result.append((x,y,z,original if belongs else expected,belongs))
    return result


def remove_origin(v, captured):
    for x,y,z,_,belongs in captured:
        if belongs:
            v.set(192+x,89+y,407+z,0)


def place(v, captured):
    paving=v.id("minecraft:polished_andesite")
    stone=v.id("minecraft:stone")
    for z in range(431,466):
        for x in range(141,176):
            for y in range(85,88):
                v.set(x,y,z,stone)
            v.set(x,88,z,paving)
    for x,y,z,block,_ in captured:
        v.set(141+x,89+y,431+z,block)
