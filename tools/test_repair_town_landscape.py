import importlib.util,unittest
from pathlib import Path
import nbtlib as n,numpy as np
spec=importlib.util.spec_from_file_location('repair',Path(__file__).with_name('repair-town-landscape.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def state(name):return n.Compound({'Name':n.String('minecraft:'+name)})
class RepairTests(unittest.TestCase):
    def test_modern_padded_words_round_trip(self):
        for size in [1,2,16,17,33,257]:
            pal=[state('test_'+str(i)) for i in range(size)];arr=(np.arange(4096)%size).astype(np.uint16);np.testing.assert_array_equal(m.decode(m.encode(pal,arr)),arr)
    def test_player_blocks_and_container_guards_stay_strict(self):
        self.assertFalse(m.compatible(state('diamond_block'),state('air'),state('stone')));self.assertFalse(m.compatible(state('chest'),state('air'),state('air')))
    def test_covered_grass_can_finish_raised_terrain(self):
        self.assertTrue(m.compatible(state('dirt'),state('grass_block'),state('stone')));self.assertFalse(m.compatible(state('dirt'),state('grass_block'),state('air')))
    def test_region_repack_preserves_unchanged_record_bytes(self):
        chunk=n.File({'xPos':n.Int(-1),'zPos':n.Int(3),'sentinel':n.String('unrelated container data')});records={5:m.serialize(chunk),800:m.serialize(chunk)};raw=m.pack_region(bytes(8192),records);self.assertEqual(m.region_chunks(raw),records);self.assertEqual(str(m.read_chunk(m.region_chunks(raw)[800])['sentinel']),'unrelated container data')
if __name__=='__main__':unittest.main()
