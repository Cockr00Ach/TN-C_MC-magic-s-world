import unittest
import numpy as np
import sky_landscape as landscape

class LandscapeTests(unittest.TestCase):
    def test_generated_caps_under_logs_are_dirt_not_decaying_grass(self):
        for name in ['minecraft:oak_log[axis=y]','minecraft:dark_oak_log[axis=x]','minecraft:spruce_planks','minecraft:dirt']:
            self.assertTrue(landscape.opaque_cover(name))
        for name in ['minecraft:air','minecraft:oak_leaves[distance=1]','minecraft:grass']:
            self.assertFalse(landscape.opaque_cover(name))
    def test_town_interior_untouched(self):
        heights=np.full((520,520),90)
        for x,z in [(120,92),(400,428),(240,260)]:
            self.assertEqual(landscape.blend_height(x,z,84,heights),84)

    def test_tall_rectangle_has_continuous_outer_slope(self):
        heights=np.full((520,520),84);heights[92:429,120:401]=140
        ys=[landscape.blend_height(x,180,84,heights) for x in range(401,501)]
        self.assertGreaterEqual(ys[0],139)
        self.assertEqual(ys[-1],84)
        self.assertLessEqual(max(abs(a-b) for a,b in zip(ys,ys[1:])),3)

    def test_low_ground_is_not_raised_to_artificial_plateau(self):
        heights=np.full((520,520),84)
        self.assertEqual(landscape.blend_height(410,290,86,heights),86)

    def test_tavern_is_inside_east_margin_and_clear_of_town(self):
        x,y,z=landscape.TAVERN_ORIGIN
        self.assertGreater(x,400+10)
        for xx,zz in [(x,z),(x+70,z+57),(x+70,z),(x,z+57)]:
            radius=landscape.boundary_radius(np.arctan2(zz-260,xx-260))
            self.assertGreater(radius-np.hypot(xx-260,zz-260),15)
        self.assertEqual(landscape.TAVERN_DOOR,(x+3,y+1,z+21))

if __name__=='__main__':unittest.main()
