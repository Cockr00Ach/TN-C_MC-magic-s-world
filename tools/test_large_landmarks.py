from pathlib import Path
import json
import unittest
import numpy as np
import nbtlib
from export_large_landmarks import fill_holes
from survey_world_buildings import section_array

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/data/tnc'

class LandmarkTests(unittest.TestCase):
    def test_fill_holes_preserves_wings_and_enclosed_courtyard(self):
        mask=np.zeros((9,12),bool);mask[2:7,2]=True;mask[2:7,7]=True;mask[2,2:8]=True;mask[6,2:8]=True
        mask[4,8:11]=True
        got=fill_holes(mask)
        self.assertTrue(got[4,4]);self.assertTrue(got[4,10]);self.assertFalse(got[0,0])
    def test_unsigned_anvil_long_decode(self):
        class Chunk:
            def _section(self,sy): return list(range(16)),[(1<<64)-1]*256,4
        a,p=section_array(Chunk(),0);self.assertEqual(a.shape,(16,16,16));self.assertTrue((a==15).all())
    def test_every_piece_matches_manifest_and_has_safe_nbt(self):
        for asset in ('end_pvp_island','heroskand_complex','gothic_cathedral','elden_coastal_castle'):
            m=json.loads((ROOT/'buildings'/f'{asset}.json').read_text(encoding='utf8'))
            seen=set(); total=0; dims=m['dimensions']
            for p in m['pieces']:
                offset=tuple(p['offset']);self.assertNotIn(offset,seen);seen.add(offset)
                n=nbtlib.load(ROOT/'structures'/(p['resource'].split(':')[1]+'.nbt'))
                self.assertEqual(list(map(int,n['size'])),p['size'])
                self.assertTrue(all(1<=s<=16 and o>=0 and o+s<=d for o,s,d in zip(offset,p['size'],dims)))
                self.assertEqual(len(n['blocks']),p['blocks']);self.assertFalse(n['entities'])
                for state in n['palette']:
                    self.assertNotIn(str(state['Name']),('minecraft:command_block','minecraft:repeating_command_block','minecraft:jigsaw','minecraft:structure_block'))
                self.assertTrue(all('nbt' not in b for b in n['blocks']))
                total+=len(n['blocks'])
            self.assertEqual(total,m['block_count']);self.assertEqual(len(seen),m['piece_count'])
            if asset=='end_pvp_island':
                self.assertEqual(dims,[408,165,585]);self.assertEqual(total,5805193)
            if asset=='heroskand_complex':
                # Whole palace and side wings; the northern village is deliberately not imported.
                self.assertEqual(m['source_bounds'],[2064,128,880,2415,319,1167])
                self.assertGreater(dims[0],272);self.assertGreater(dims[2],272)
            if asset=='gothic_cathedral': self.assertEqual(dims[1],358);self.assertTrue(m['sunken'])

if __name__=='__main__': unittest.main()
