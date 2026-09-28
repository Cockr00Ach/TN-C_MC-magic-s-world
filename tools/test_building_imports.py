from pathlib import Path
import json
import unittest
import numpy as np
import nbtlib
from building_assets import read_blueprint, state_text, AIR, varints
from import_buildings import compatible

ROOT=Path(__file__).resolve().parents[1]

class BuildingImportTests(unittest.TestCase):
    def test_newer_stairs_keep_orientation(self):
        self.assertEqual(compatible('minecraft:tuff_brick_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]'),
                         'minecraft:stone_brick_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]')
    def test_commands_never_imported(self):
        self.assertEqual(compatible('minecraft:command_block[conditional=false,facing=north]'),'minecraft:chiseled_stone_bricks')
    def test_varint_rejects_extra_and_truncated_data(self):
        with self.assertRaises(ValueError): varints([1,2],1)
        with self.assertRaises(ValueError): varints([-128],1)
    def test_citadel_preserves_every_source_block(self):
        source=Path(r'D:\mc建组\the-distortion-citadel.schem')
        if not source.exists(): self.skipTest('Original source only on asset workstation')
        blocks,palette,_,_=read_blueprint(source)
        data=ROOT/'src/main/resources/data/tnc'
        manifest=json.loads((data/'buildings/abyss_citadel.json').read_text(encoding='utf8'))
        seen=np.zeros(blocks.shape,dtype=bool)
        total=0
        for piece in manifest['pieces']:
            path=data/'structures'/(piece['resource'].split(':')[1]+'.nbt')
            nbt=nbtlib.load(path)
            actual=[state_text(p) for p in nbt['palette']]
            # State property order is canonicalized in state_text.
            ox,oy,oz=piece['offset']
            self.assertEqual(list(map(int,nbt['size'])),piece['size'])
            for b in nbt['blocks']:
                x,y,z=map(int,b['pos']);x+=ox;y+=oy;z+=oz
                self.assertFalse(seen[y,z,x]);seen[y,z,x]=True
                self.assertEqual(actual[int(b['state'])],palette[int(blocks[y,z,x])])
                total+=1
        self.assertEqual(total,200395)
        np.testing.assert_array_equal(seen,np.array([s.split('[')[0] not in AIR for s in palette])[blocks])

if __name__=='__main__': unittest.main()
