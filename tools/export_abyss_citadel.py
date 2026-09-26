"""Export the full, unscaled Distortion Citadel into 16-cube checkpoints."""
from pathlib import Path
import argparse
import hashlib
import numpy as np
from building_assets import read_blueprint, AIR
from export_town_structures import write_piece
from import_buildings import write_json

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,default=Path(r'D:\mc建组\the-distortion-citadel.schem'))
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'src/main/resources/data/tnc')
args=parser.parse_args()
source,root=args.source,args.output
blocks, palette, version, entities = read_blueprint(source)
assert blocks.shape == (384,141,244), blocks.shape
solid = np.array([s.split('[')[0] not in AIR for s in palette])
# This source contains only 1.20.1 blocks and no block entities.
assert not entities
entries = []
for y in range(0,384,16):
    for z in range(0,141,16):
        for x in range(0,244,16):
            tile = blocks[y:y+16,z:z+16,x:x+16]
            records = [(int(lx),int(ly),int(lz),palette[int(tile[ly,lz,lx])],None)
                       for ly,lz,lx in np.argwhere(solid[tile])]
            if not records: continue
            name = f'p_{x}_{y}_{z}'
            size = [tile.shape[2],tile.shape[0],tile.shape[1]]
            count,_ = write_piece(root/'structures/abyss_citadel'/(name+'.nbt'),tuple(size),records,3465)
            entries.append(dict(resource='tnc:abyss_citadel/'+name,offset=[x,y,z],size=size,blocks=count))
manifest = dict(dimensions=[244,384,141],source=source.name,source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                source_data_version=version,source_blocks=int(solid[blocks].sum()),pieces=entries,piece_count=len(entries))
assert sum(p['blocks'] for p in entries) == manifest['source_blocks']
write_json(root/'buildings/abyss_citadel.json',manifest)
write_piece(root/'structures/building_test_empty.nbt',(5,5,5),[(0,0,0,'minecraft:air',None)],3465)
print(f"Unscaled citadel: {len(entries)} pieces, {manifest['source_blocks']} blocks, y=-64..319")
