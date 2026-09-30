import unittest
import numpy as np
from export_large_landmarks import landscape_mask, ground_profile, height_runs


class LandscapeProfileTests(unittest.TestCase):
    def test_polygon_preserves_open_courtyard_without_roof_and_excludes_far_bank(self):
        mask = landscape_mask([100, 0, 200, 109, 15, 209],
                              [[101, 201], [107, 201], [107, 207], [101, 207]])
        self.assertTrue(mask[4, 4])
        self.assertFalse(mask[4, 9])
        self.assertFalse(mask[0, 0])

    def test_soil_profile_ignores_roofs_and_has_reference_height_fallback(self):
        blocks = np.zeros((8, 2, 2), dtype=np.uint16)
        palette = ['minecraft:air', 'minecraft:grass_block', 'minecraft:stone_bricks']
        blocks[2, 0, 0] = 1
        blocks[6, 0, 0] = 2
        profile = ground_profile(blocks, palette, 4)
        self.assertEqual(int(profile[0, 0]), 2)
        self.assertEqual(int(profile[1, 1]), 4)

    def test_height_runs_cover_only_core_and_keep_varying_heights(self):
        mask = np.array([[False, True, True, True, False]])
        heights = np.array([[0, 3, 3, 5, 0]], dtype=np.int16)
        self.assertEqual(height_runs(mask, heights), [[[1, 2, 3], [3, 3, 5]]])


if __name__ == '__main__':
    unittest.main()
