"""Tiny production-format fixture for headless placement/recovery tests only."""
from pathlib import Path
from export_town_structures import write_piece
from import_buildings import write_json

root=Path(__file__).resolve().parents[1]/'src/main/resources/data/tnc'
pieces=[]
for x in (0,16):
    name=f'landmark_fixture_{x}'
    records=[(lx,0,z,'minecraft:stone_bricks',None) for lx in range(16) for z in range(16)]
    records.append((2,1,2,'minecraft:diamond_block',None))
    write_piece(root/'structures'/(name+'.nbt'),(16,3,16),records,3465)
    pieces.append(dict(resource='tnc:'+name,offset=[x,0,0],size=[16,3,16],blocks=len(records)))
write_json(root/'buildings/landmark_fixture.json',dict(dimensions=[32,3,16],anchor_local=[16,0,15],
           mask_rows=[[[0,31]] for _ in range(16)],terrain_mask_rows=[[[0,31]] for _ in range(16)],
           pieces=pieces,piece_count=2,block_count=514))

# Same small templates, but the first half of the floating island has a raised underside.
floating=[dict(p,offset=[p['offset'][0],16 if p['offset'][0]==0 else 0,0]) for p in pieces]
write_json(root/'buildings/landmark_floating_fixture.json',dict(dimensions=[32,19,16],anchor_local=[16,0,15],floating=True,
           mask_rows=[[[0,31]] for _ in range(16)],terrain_mask_rows=[[[0,31]] for _ in range(16)],
           pieces=floating,piece_count=2,block_count=514))
